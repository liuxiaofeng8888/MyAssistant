package com.myassistant.android;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
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
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
  private static final int REQ_AUDIO = 1001;
  private static final int CHUNK_SIZE = 1280; // 与服务端/讯飞建议一致

  private final Handler ui = new Handler(Looper.getMainLooper());

  private TextView logView;
  private ScrollView scroll;
  private Button btnConnect;
  private Button btnRecord;
  private EditText serverUrl;

  private VoiceWsClient wsClient;
  private final AudioRecorder recorder = new AudioRecorder();

  private volatile boolean recording = false;

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
    btnRecord.setText("松开结束");

    stopTts();
    wsClient.startTurn();
    recorder.start(CHUNK_SIZE, new AudioRecorder.Callback() {
      @Override
      public void onAudioChunk(byte[] chunk, int len) {
        wsClient.sendAudio(chunk, len);
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

    appendLog("recording... (press and hold)");
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

