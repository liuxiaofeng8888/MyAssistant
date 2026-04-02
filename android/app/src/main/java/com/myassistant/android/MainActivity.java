package com.myassistant.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import com.konovalov.vad.webrtc.Vad;
import com.konovalov.vad.webrtc.VadWebRTC;
import com.konovalov.vad.webrtc.config.FrameSize;
import com.konovalov.vad.webrtc.config.Mode;
import com.konovalov.vad.webrtc.config.SampleRate;
import com.myassistant.android.kws.VoskKwsService;
import com.myassistant.android.ui.ResultOverlayDialog;
import com.myassistant.android.ui.TypewriterTextView;
import com.myassistant.android.ui.VpaOrbView;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
  private static final int REQ_AUDIO = 1001;
  // WebRTC VAD 支持 16k 下 160/320/480 samples（10/20/30ms）
  // AudioRecorder.read 是按字节读 PCM16，因此 320 samples = 640 bytes
  private static final int CHUNK_SIZE = 640;
  private static final long VAD_SILENCE_STOP_MS = 900; // 连续静音这么久就自动 stop

  private final Handler ui = new Handler(Looper.getMainLooper());

  private TextView logView;
  private ScrollView scroll;
  private Button btnConnect;
  private Button btnWake;
  private Button btnClearLog;
  private EditText serverUrl;
  private FrameLayout vpaContainer;
  private VpaOrbView vpaOrb;
  private TextView asrTitle;
  private TypewriterTextView asrText;

  private VoiceWsClient wsClient;
  private final AudioRecorder recorder = new AudioRecorder();

  private volatile boolean recording = false;
  private VadWebRTC vad;
  private volatile long lastSpeechAtMs = 0L;
  private volatile boolean turnStarted = false;

  private TextToSpeech tts;
  private volatile boolean ttsReady = false;
  private final Map<String, Runnable> onTtsDone = new ConcurrentHashMap<>();
  private AudioManager audioManager;
  private AudioManager.OnAudioFocusChangeListener audioFocusListener;
  private AudioAttributes ttsAudioAttrs;
  private ToneGenerator tone;

  private VoskKwsService kws;
  private volatile boolean kwsReady = false;
  private volatile boolean kwsInitFallbackTried = false;

  private enum UiState {
    IDLE,
    AWAKE,
    LISTENING,
    PROCESSING
  }

  private volatile UiState uiState = UiState.IDLE;
  private volatile String lastAsr = "";

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
    audioFocusListener = focusChange -> {
      // no-op: 仅用于请求/释放焦点
    };
    ttsAudioAttrs = new AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build();
    try {
      tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 80);
    } catch (Exception ignored) {
      tone = null;
    }

    logView = findViewById(R.id.logView);
    scroll = findViewById(R.id.scroll);
    btnConnect = findViewById(R.id.btnConnect);
    btnWake = findViewById(R.id.btnWake);
    btnClearLog = findViewById(R.id.btnClearLog);
    serverUrl = findViewById(R.id.serverUrl);
    vpaContainer = findViewById(R.id.vpaContainer);
    vpaOrb = findViewById(R.id.vpaOrb);
    asrTitle = findViewById(R.id.asrTitle);
    asrText = findViewById(R.id.asrText);

    initTts();
    initVad();
    initKws();

    // 启动后尽早申请录音权限，让本地 KWS 能够常驻监听
    ensureAudioPermission();

    wsClient = new VoiceWsClient(new VoiceWsClient.Listener() {
      @Override
      public void onLog(String line) {
        appendLog(line);
      }

      @Override
      public void onMessage(JSONObject json) {
        appendLog("<< " + json.toString());
        handleServerMessage(json);
      }

      @Override
      public void onState(boolean connected) {
        ui.post(() -> {
          btnConnect.setText(connected ? "断开" : "连接");
          btnWake.setEnabled(connected);
        });
      }
    });

    btnConnect.setOnClickListener(v -> {
      if (wsClient.isConnected()) {
        wsClient.disconnect();
        stopRecordingIfNeeded();
      } else {
        String url = serverUrl.getText().toString().trim();
        appendLog("connect to " + url);
        wsClient.connect(url);
      }
    });

    btnWake.setOnClickListener(v -> {
      if (!ensureAudioPermission()) return;

      if (recording) {
        // 作为“取消/退出唤醒”的快捷入口
        cancelRecording();
        setUiState(UiState.IDLE);
        return;
      }

      // 手动唤醒（KWS 也会走同一套流程）
      onWakeTriggered("manual");
    });

    btnClearLog.setOnClickListener(v -> {
      logView.setText("");
      scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_UP));
    });

    setUiState(UiState.IDLE);
  }

  private void initKws() {
    kws = new VoskKwsService(this, new VoskKwsService.Listener() {
      @Override
      public void onLog(@NonNull String line) {
        appendLog(line);
      }

      @Override
      public void onWakeWord(@NonNull String wakeWord) {
        appendLog("KWS hit: " + wakeWord);
        onWakeTriggered(wakeWord);
      }

      @Override
      public void onModelReady() {
        kwsReady = true;
        appendLog("KWS ready");
        // 如果用户已经授权录音，则直接开始常驻监听
        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED) {
          kws.start();
        }
      }

      @Override
      public void onError(@NonNull String message, Throwable t) {
        kwsReady = false;
        appendLog(message + (t != null ? (": " + t.getMessage()) : ""));

        // 兜底：有些人把模型放成 assets/model/vosk-model-xxx 的形式（多一层目录）
        // 代码默认尝试 assets/model 作为模型根；失败后自动改为尝试嵌套目录。
        if (!kwsInitFallbackTried
            && message.contains("did you add assets/model")
            && !message.contains("model/vosk-model-small-cn-0.22")) {
          kwsInitFallbackTried = true;
          appendLog("KWS: fallback to assets/model/vosk-model-small-cn-0.22");
          try {
            // 异步解包：成功后会触发 onModelReady
            kws.initModelFromAssets("model/vosk-model-small-cn-0.22");
          } catch (Exception ignored) {}
        }
      }
    });

    // 默认：assets/model （你需要把 Vosk 模型文件夹放到 android/app/src/main/assets/model）
    // 兼容常见口音/说法差异：同时加入有/无空格版本，避免“嗨 小布”匹配不到
    kws.setWakeWords(new String[]{
        "你好助手",
        "小助手",
        "你好小助手",
        "嗨助手",
        "嗨 小布",
        "嗨小布",
        "小布"
    });
    kws.setCooldownMs(1800);
    kws.initModelFromAssets("model");
  }

  private void onWakeTriggered(@NonNull String source) {
    if (!ensureAudioPermission()) return;
    if (!wsClient.isConnected()) {
      appendLog("wake ignored (not connected): " + source);
      return;
    }
    if (recording) return;

    lastAsr = "";
    setUiState(UiState.AWAKE);
    speak("你好主人", () -> {
      if (!wsClient.isConnected()) return;
      startRecording();
    });
  }

  private void setUiState(UiState s) {
    uiState = s;
    ui.post(() -> {
      switch (s) {
        case IDLE -> {
          btnWake.setText("点击唤醒");
          scroll.setVisibility(View.VISIBLE);
          vpaContainer.setVisibility(View.GONE);
          vpaOrb.stop();
          asrTitle.setVisibility(View.GONE);
          asrText.setVisibility(View.GONE);
          asrText.stopAll();
        }
        case AWAKE -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.GONE);
          vpaContainer.setVisibility(View.VISIBLE);
          vpaOrb.start();
          asrTitle.setVisibility(View.GONE);
          asrText.setVisibility(View.GONE);
          asrText.stopAll();
        }
        case LISTENING -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.GONE);
          vpaContainer.setVisibility(View.VISIBLE);
          vpaOrb.start();
          asrTitle.setVisibility(View.VISIBLE);
          asrTitle.setText("识别中");
          asrText.setVisibility(View.VISIBLE);
          if (lastAsr == null || lastAsr.isEmpty()) {
            asrText.startDots(220);
          } else {
            asrText.setTextImmediate(lastAsr);
          }
        }
        case PROCESSING -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.GONE);
          vpaContainer.setVisibility(View.VISIBLE);
          vpaOrb.start();
          asrTitle.setVisibility(View.VISIBLE);
          asrTitle.setText("思考中");
          asrText.setVisibility(View.VISIBLE);
          asrText.startDots(260);
        }
      }
    });
  }

  private void initVad() {
    try {
      // 推荐参数：16k + 320 frame + VERY_AGGRESSIVE + (speech 50ms / silence 300ms)
      vad = Vad.builder()
          .setSampleRate(SampleRate.SAMPLE_RATE_16K)
          .setFrameSize(FrameSize.FRAME_SIZE_320)
          .setMode(Mode.VERY_AGGRESSIVE)
          .setSpeechDurationMs(50)
          .setSilenceDurationMs(300)
          .build();
      appendLog("WebRTC VAD ready");
    } catch (Exception e) {
      // 如果 native 库加载失败等，让应用仍可录音（降级为无 VAD 门控）
      vad = null;
      appendLog("WebRTC VAD init failed: " + e.getMessage());
    }
  }

  private boolean ensureAudioPermission() {
    int granted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
    if (granted == PackageManager.PERMISSION_GRANTED) return true;
    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
    return false;
  }

  private void startRecording() {
    if (!wsClient.isConnected()) {
      appendLog("not connected");
      return;
    }
    if (recording) return;
    recording = true;
    turnStarted = false;
    lastSpeechAtMs = 0L;

    stopTts();
    setUiState(UiState.LISTENING);
    recorder.start(CHUNK_SIZE, new AudioRecorder.Callback() {
      @Override
      public void onAudioChunk(byte[] chunk, int len) {
        long now = SystemClock.elapsedRealtime();
        boolean isSpeech;
        if (vad != null) {
          // VadWebRTC 要求 ByteArray 长度为 2 * frameSize（即 640 bytes）
          if (len != CHUNK_SIZE) {
            // 尽量不崩：长度不符合就按“非语音”处理
            isSpeech = false;
          } else {
            isSpeech = vad.isSpeech(chunk);
          }
        } else {
          // VAD 初始化失败时，直接当作“有声”，保持原功能可用
          isSpeech = true;
        }

        if (isSpeech) {
          lastSpeechAtMs = now;
          if (!turnStarted) {
            wsClient.startTurn();
            turnStarted = true;
            appendLog("VAD: speech start -> startTurn()");
          }
          wsClient.sendAudio(chunk, len);
          return;
        }

        // 静音：如果已经开始过 turn，则静音超时后自动 stop 并结束录音
        if (turnStarted && lastSpeechAtMs > 0 && (now - lastSpeechAtMs) >= VAD_SILENCE_STOP_MS) {
          appendLog("VAD: silence timeout -> stopTurn()");
          ui.post(() -> {
            // 复用现有停止逻辑，保证 UI 状态一致
            stopRecording();
          });
        }
      }

      @Override
      public void onError(Exception e) {
        appendLog("recorder error: " + e.getMessage());
      }

      @Override
      public void onStopped() {
        // no-op
      }
    });

    appendLog("recording... (VAD enabled, speech-gated)");
  }

  private void stopRecording() {
    if (!recording) return;
    recording = false;
    recorder.stop();
    wsClient.stopTurn();
    appendLog("stopped");
    setUiState(UiState.PROCESSING);
  }

  private void cancelRecording() {
    if (!recording) return;
    recording = false;
    recorder.stop();
    wsClient.cancelTurn();
    appendLog("cancelled");
    setUiState(UiState.IDLE);
  }

  private void stopRecordingIfNeeded() {
    if (recording) {
      stopRecording();
    }
  }

  private void appendLog(String s) {
    ui.post(() -> {
      logView.append(s + "\n");
      scroll.post(() -> scroll.fullScroll(ScrollView.FOCUS_DOWN));
    });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    try {
      recorder.stop();
    } catch (Exception ignored) {}
    try {
      if (kws != null) kws.stop();
    } catch (Exception ignored) {}
    try {
      wsClient.disconnect();
    } catch (Exception ignored) {}
    try {
      if (vad != null) vad.close();
    } catch (Exception ignored) {}
    try {
      if (tone != null) tone.release();
    } catch (Exception ignored) {}
    try {
      if (tts != null) {
        tts.stop();
        tts.shutdown();
      }
    } catch (Exception ignored) {}
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    if (requestCode == REQ_AUDIO) {
      if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
        appendLog("RECORD_AUDIO granted");
        try {
          if (kws != null && kwsReady) kws.start();
        } catch (Exception ignored) {}
      } else {
        appendLog("RECORD_AUDIO denied");
      }
    }
  }

  private void initTts() {
    tts = new TextToSpeech(this, status -> {
      if (status == TextToSpeech.SUCCESS) {
        int r = tts.setLanguage(Locale.SIMPLIFIED_CHINESE);
        ttsReady = (r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED);
        if (ttsReady) {
          appendLog("TTS ready");
          try {
            tts.setAudioAttributes(ttsAudioAttrs);
          } catch (Exception ignored) {}
        } else {
          appendLog(r == TextToSpeech.LANG_MISSING_DATA ? "TTS missing data (need install)" : "TTS language not supported");
          // 尝试引导安装/更新 TTS 语言数据（部分 ROM 需要）
          try {
            Intent i = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
            startActivity(i);
          } catch (Exception ignored) {}
        }
        try {
          tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {}

            @Override
            public void onDone(String utteranceId) {
              Runnable r = onTtsDone.remove(utteranceId);
              if (r != null) ui.post(r);
            }

            @Override
            public void onError(String utteranceId) {
              Runnable r = onTtsDone.remove(utteranceId);
              if (r != null) ui.post(r);
            }
          });
        } catch (Exception ignored) {}
      } else {
        ttsReady = false;
        appendLog("TTS init failed: " + status);
        // 某些车机 ROM 默认绑定的 TTS Service 不存在，跳系统 TTS 设置让用户切换引擎
        try {
          // 兼容做法：部分系统没有公开常量，使用 settings action 字符串
          Intent i = new Intent("com.android.settings.TTS_SETTINGS");
          startActivity(i);
        } catch (Exception ignored) {}
      }
    });
  }

  private void playWakeBeep() {
    try {
      if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 160);
    } catch (Exception ignored) {}
  }

  private void speak(String text) {
    speak(text, null);
  }

  private void speak(String text, Runnable afterDone) {
    if (!ttsReady || tts == null) {
      appendLog("speak skipped (tts not ready)");
      playWakeBeep();
      if (afterDone != null) ui.post(afterDone);
      return;
    }
    if (text == null) {
      if (afterDone != null) ui.post(afterDone);
      return;
    }
    String t = text.trim();
    if (t.isEmpty()) {
      if (afterDone != null) ui.post(afterDone);
      return;
    }

    // 请求短暂音频焦点，避免被其它音频/录音链路压制
    try {
      if (audioManager != null) {
        audioManager.requestAudioFocus(audioFocusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
      }
    } catch (Exception ignored) {}

    String utteranceId = "utt-" + UUID.randomUUID();
    if (afterDone != null) onTtsDone.put(utteranceId, afterDone);
    tts.speak(t, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
  }

  private void stopTts() {
    try {
      if (tts != null) tts.stop();
    } catch (Exception ignored) {}
  }

  private void handleServerMessage(JSONObject json) {
    try {
      String type = json.optString("type", "");
      if ("asr_partial".equals(type) || "asr_interim".equals(type)) {
        String text = json.optString("text", "");
        lastAsr = text;
        if (uiState == UiState.LISTENING) {
          ui.post(() -> asrText.setTextImmediate(text));
        }
        return;
      }
      if ("asr_final".equals(type)) {
        String text = json.optString("text", "");
        lastAsr = text;
        if (uiState == UiState.LISTENING || uiState == UiState.PROCESSING) {
          ui.post(() -> {
            asrTitle.setVisibility(TextView.VISIBLE);
            asrTitle.setText("你说的是");
            asrText.setVisibility(TextView.VISIBLE);
            asrText.typeTo(text, 18, 0);
          });
        }
        return;
      }
      if ("assistant_final".equals(type)) {
        String text = json.optString("text", "");
        ui.post(() -> {
          try {
            new ResultOverlayDialog(this, text == null ? "" : text).show();
          } catch (Exception e) {
            appendLog("show result dialog failed: " + e.getMessage());
          }
          setUiState(UiState.IDLE);
        });
        speak(text);
      }
    } catch (Exception ignored) {}
  }
}

