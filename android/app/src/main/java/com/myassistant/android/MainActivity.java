package com.myassistant.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.Locale;
import java.util.UUID;
import com.konovalov.vad.webrtc.Vad;
import com.konovalov.vad.webrtc.VadWebRTC;
import com.konovalov.vad.webrtc.config.FrameSize;
import com.konovalov.vad.webrtc.config.Mode;
import com.konovalov.vad.webrtc.config.SampleRate;
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
  private Button btnRecord;
  private EditText serverUrl;

  private VoiceWsClient wsClient;
  private final AudioRecorder recorder = new AudioRecorder();

  private volatile boolean recording = false;
  private VadWebRTC vad;
  private volatile long lastSpeechAtMs = 0L;
  private volatile boolean turnStarted = false;

  private TextToSpeech tts;
  private volatile boolean ttsReady = false;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    logView = findViewById(R.id.logView);
    scroll = findViewById(R.id.scroll);
    btnConnect = findViewById(R.id.btnConnect);
    btnRecord = findViewById(R.id.btnRecord);
    serverUrl = findViewById(R.id.serverUrl);

    initTts();
    initVad();

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
          btnRecord.setEnabled(connected);
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

    btnRecord.setText("按住说话");
    btnRecord.setOnTouchListener((v, event) -> {
      if (!ensureAudioPermission()) return true;
      if (!wsClient.isConnected()) return true;

      switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN -> {
          startRecording();
          return true;
        }
        case MotionEvent.ACTION_UP -> {
          stopRecording();
          return true;
        }
        case MotionEvent.ACTION_CANCEL -> {
          cancelRecording();
          return true;
        }
      }
      return false;
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
    btnRecord.setText("松开结束");

    stopTts();
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
    btnRecord.setText("按住说话");
    recorder.stop();
    wsClient.stopTurn();
    appendLog("stopped");
  }

  private void cancelRecording() {
    if (!recording) return;
    recording = false;
    btnRecord.setText("按住说话");
    recorder.stop();
    wsClient.cancelTurn();
    appendLog("cancelled");
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
      wsClient.disconnect();
    } catch (Exception ignored) {}
    try {
      if (vad != null) vad.close();
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
        appendLog(ttsReady ? "TTS ready" : "TTS language not supported");
      } else {
        ttsReady = false;
        appendLog("TTS init failed: " + status);
      }
    });
  }

  private void speak(String text) {
    if (!ttsReady || tts == null) return;
    if (text == null) return;
    String t = text.trim();
    if (t.isEmpty()) return;
    String utteranceId = "utt-" + UUID.randomUUID();
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
      if ("assistant_final".equals(type)) {
        String text = json.optString("text", "");
        speak(text);
      }
    } catch (Exception ignored) {}
  }
}

