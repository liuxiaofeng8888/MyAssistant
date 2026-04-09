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
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayDeque;
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
import com.myassistant.android.ui.VpaOrbView;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
  private static final int REQ_AUDIO = 1001;
  // WebRTC VAD 支持 16k 下 160/320/480 samples（10/20/30ms）
  // AudioRecorder.read 是按字节读 PCM16，因此 320 samples = 640 bytes
  private static final int CHUNK_SIZE = 640;
  private static final long VAD_SILENCE_STOP_MS = 900; // 连续静音这么久就自动 stop
  // 预留一点点 pre-roll，降低 VAD 裁掉开头导致的识别不准
  private static final int PREROLL_FRAMES = 12; // 12 * 20ms ≈ 240ms（CHUNK_SIZE=320 samples）

  private final Handler ui = new Handler(Looper.getMainLooper());

  private TextView logView;
  private ScrollView scroll;
  private TextView asrView;
  private Button btnConnect;
  private Button btnWake;
  private Button btnClearLog;
  private EditText serverUrl;
  private @NonNull WindowManager windowManager;
  private @NonNull VpaOrbView floatOrb;
  private volatile boolean floatOrbAttached = false;

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

    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    floatOrb = new VpaOrbView(this);

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
    asrView = findViewById(R.id.asrView);
    btnConnect = findViewById(R.id.btnConnect);
    btnWake = findViewById(R.id.btnWake);
    btnClearLog = findViewById(R.id.btnClearLog);
    serverUrl = findViewById(R.id.serverUrl);

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
        // 兜底：连接状态变化后确保 KWS 处于常驻监听（避免某些异常路径 stop 后未恢复）
        if (connected) {
          ui.post(() -> {
            try {
              if (!recording
                  && kws != null
                  && kwsReady
                  && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                  == PackageManager.PERMISSION_GRANTED
                  && !kws.isRunning()) {
                appendLog("KWS: start on connected");
                kws.start();
              }
            } catch (Exception ignored) {}
          });
        }
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

    // 默认：assets/model/vosk-model-small-cn-0.22
    // （把完整的模型目录放到 android/app/src/main/assets/model/vosk-model-small-cn-0.22）
    // 兼容常见口音/说法差异：同时加入有/无空格版本，避免“嗨 小奇”匹配不到
    kws.setWakeWords(new String[]{
        "你好助手",
        "小助手",
        "你好小助手",
        "嗨助手",
        "嗨 小奇",
        "嗨小奇",
        "小奇"
    });
    kws.setCooldownMs(1800);
    kws.initModelFromAssets("model/vosk-model-small-cn-0.22");
  }

  private void onWakeTriggered(@NonNull String source) {
    if (!ensureAudioPermission()) return;
    if (!wsClient.isConnected()) {
      appendLog("wake ignored (not connected): " + source);
      return;
    }
    if (recording) return;

    // 唤醒后立刻暂停 KWS，避免 TTS 回放/环境噪声导致连续误唤醒
    try {
      if (kws != null && kws.isRunning()) {
        appendLog("KWS: pause on wake");
        kws.stop();
      }
    } catch (Exception ignored) {}

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
          hideFloatOrb();
        }
        case AWAKE -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.VISIBLE);
          showFloatOrb();
        }
        case LISTENING -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.VISIBLE);
          showFloatOrb();
        }
        case PROCESSING -> {
          btnWake.setText("取消");
          scroll.setVisibility(View.VISIBLE);
          showFloatOrb();
        }
      }
    });
  }

  private void showFloatOrb() {
    try {
      if (floatOrbAttached) {
        floatOrb.start();
        return;
      }

      int size = dp(120);
      WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
          size,
          size,
          WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
          WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
              | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
              | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
          android.graphics.PixelFormat.TRANSLUCENT
      );
      lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
      lp.y = dp(18);

      windowManager.addView(floatOrb, lp);
      floatOrbAttached = true;
      floatOrb.start();
    } catch (Exception e) {
      appendLog("showFloatOrb failed: " + e.getMessage());
    }
  }

  private void hideFloatOrb() {
    try {
      floatOrb.stop();
    } catch (Exception ignored) {}
    try {
      if (floatOrbAttached) {
        windowManager.removeViewImmediate(floatOrb);
        floatOrbAttached = false;
      }
    } catch (Exception ignored) {}
  }

  private int dp(int v) {
    float d = getResources().getDisplayMetrics().density;
    return Math.round(v * d);
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

    // 避免与本地 KWS 同时占用麦克风（部分设备不支持两个 AudioRecord 并发）
    try {
      if (kws != null && kws.isRunning()) {
        appendLog("KWS: pause during recording");
        kws.stop();
      }
    } catch (Exception ignored) {}

    recorder.start(CHUNK_SIZE, new AudioRecorder.Callback() {
      private final ArrayDeque<byte[]> preRoll = new ArrayDeque<>();
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
            // 把 pre-roll 帧先补发，减少“吃掉第一个字”的概率
            while (!preRoll.isEmpty()) {
              byte[] f = preRoll.removeFirst();
              wsClient.sendAudio(f, f.length);
            }
          }
          wsClient.sendAudio(chunk, len);
          return;
        }

        // 未开始说话时：积累少量 pre-roll
        if (!turnStarted) {
          if (len == CHUNK_SIZE) {
            // chunk 来自 AudioRecorder 每次新分配的 frame，这里直接缓存引用即可
            preRoll.addLast(chunk);
            while (preRoll.size() > PREROLL_FRAMES) preRoll.removeFirst();
          }
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
        // 录音结束后恢复 KWS 常驻监听
        ui.post(() -> {
          try {
            if (kws != null && kwsReady
                && ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
              if (!kws.isRunning()) {
                appendLog("KWS: resume after recording");
                kws.start();
              }
            }
          } catch (Exception ignored) {}
        });
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
      hideFloatOrb();
    } catch (Exception ignored) {}
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
        updateAsrView(text);
        return;
      }
      if ("asr_final".equals(type)) {
        String text = json.optString("text", "");
        lastAsr = text;
        updateAsrView(text);
        return;
      }
      if ("tool_call".equals(type)) {
        String name = json.optString("name", "");
        appendLog("NLU(tool_call): " + (name == null || name.isEmpty() ? "(unknown)" : name));
        return;
      }
      if ("tool_result".equals(type)) {
        String name = json.optString("name", "");
        boolean ok = json.optBoolean("ok", false);
        appendLog("RESULT(tool_result): " + (name == null || name.isEmpty() ? "(unknown)" : name) + " ok=" + ok);
        return;
      }
      if ("wakeup_detected".equals(type)) {
        String text = json.optString("text", "");
        appendLog("NLU(wakeup_detected): " + text);
        return;
      }
      if ("assistant_final".equals(type)) {
        String text = json.optString("text", "");
        appendLog("RESULT(assistant_final): " + (text == null ? "" : text));
        // UI 仅展示 ASR：不再弹出结果对话框，也不做 TTS 播报
        ui.post(() -> setUiState(UiState.IDLE));
      }
    } catch (Exception ignored) {}
  }

  private void updateAsrView(String text) {
    String t = text == null ? "" : text.trim();
    ui.post(() -> asrView.setText(t.isEmpty() ? "" : ("ASR: " + t)));
  }
}

