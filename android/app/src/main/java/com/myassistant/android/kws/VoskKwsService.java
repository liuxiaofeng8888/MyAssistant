package com.myassistant.android.kws;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.StorageService;

/**
 * 基于 Vosk 的本地 KWS（关键词唤醒）。
 *
 * 约定：
 * - 需要在 assets 下提供 Vosk 模型目录（默认：assets/model）
 * - 唤醒词用 grammar（JSON array）限制，降低误唤醒和算力开销
 */
public final class VoskKwsService {
  public interface Listener {
    void onLog(@NonNull String line);
    void onWakeWord(@NonNull String wakeWord);
    void onModelReady();
    void onError(@NonNull String message, @Nullable Throwable t);
  }

  private static final int SAMPLE_RATE = 16000;
  private static final int CHANNEL = AudioFormat.CHANNEL_IN_MONO;
  private static final int FORMAT = AudioFormat.ENCODING_PCM_16BIT;

  private final Context app;
  private final Listener listener;
  private final Handler ui = new Handler(Looper.getMainLooper());
  private final AtomicBoolean running = new AtomicBoolean(false);

  private @Nullable Model model;
  private @Nullable Thread thread;

  private volatile long lastWakeAtMs = 0L;
  private volatile long cooldownMs = 1800L;
  private volatile String[] wakeWords = new String[]{"你好助手", "小助手", "你好小助手", "嗨助手"};

  public VoskKwsService(@NonNull Context context, @NonNull Listener listener) {
    this.app = context.getApplicationContext();
    this.listener = listener;
  }

  /** assets 里的模型目录名，默认 "model" */
  public void initModelFromAssets(@NonNull String assetDirName) {
    log("Vosk: unpack model from assets/" + assetDirName);
    StorageService.unpack(
        app,
        assetDirName,
        "vosk-model",
        (Model m) -> {
          model = m;
          log("Vosk: model ready");
          ui.post(listener::onModelReady);
        },
        (IOException e) -> error("Vosk: unpack failed (did you add assets/" + assetDirName + " model?)", e)
    );
  }

  public boolean isReady() {
    return model != null;
  }

  public boolean isRunning() {
    return running.get();
  }

  public void setWakeWords(@NonNull String[] words) {
    if (words.length == 0) return;
    this.wakeWords = words;
  }

  public void setCooldownMs(long ms) {
    this.cooldownMs = Math.max(200, ms);
  }

  public void start() {
    if (running.get()) return;
    if (model == null) {
      log("Vosk: start ignored (model not ready)");
      return;
    }
    running.set(true);
    thread = new Thread(this::loop, "VoskKws");
    thread.start();
  }

  public void stop() {
    running.set(false);
  }

  private void loop() {
    AudioRecord record = null;
    Recognizer recognizer = null;
    try {
      String grammar = grammarJson(wakeWords);
      recognizer = new Recognizer(model, SAMPLE_RATE, grammar);

      int minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT);
      int bufSize = Math.max(minBuf, SAMPLE_RATE * 2); // ~1s pcm16
      record = new AudioRecord(
          MediaRecorder.AudioSource.VOICE_RECOGNITION,
          SAMPLE_RATE,
          CHANNEL,
          FORMAT,
          bufSize
      );

      if (record.getState() != AudioRecord.STATE_INITIALIZED) {
        throw new IllegalStateException("AudioRecord init failed");
      }

      byte[] buf = new byte[Math.min(bufSize, 4096)];
      record.startRecording();
      log("Vosk: kws started, grammar=" + grammar);

      while (running.get()) {
        int n = record.read(buf, 0, buf.length);
        if (n <= 0) continue;

        boolean isFinal = recognizer.acceptWaveForm(buf, n);
        String j = isFinal ? recognizer.getResult() : recognizer.getPartialResult();
        String text = extractText(j);
        if (text == null || text.isEmpty()) continue;

        // grammar 限制下，text 一般就是命中的词（也可能带空格/大小写差异）
        String hit = matchWakeWord(text, wakeWords);
        if (hit == null) continue;

        long now = android.os.SystemClock.elapsedRealtime();
        if (now - lastWakeAtMs < cooldownMs) continue;
        lastWakeAtMs = now;

        String finalHit = hit;
        ui.post(() -> listener.onWakeWord(finalHit));
      }
    } catch (Exception e) {
      error("Vosk: kws loop failed", e);
    } finally {
      try {
        if (record != null) {
          try { record.stop(); } catch (Exception ignored) {}
          record.release();
        }
      } catch (Exception ignored) {}
      try {
        if (recognizer != null) recognizer.close();
      } catch (Exception ignored) {}
      running.set(false);
      log("Vosk: kws stopped");
    }
  }

  private static @NonNull String grammarJson(@NonNull String[] words) {
    // Vosk grammar: JSON array of phrases
    StringBuilder sb = new StringBuilder();
    sb.append('[');
    for (int i = 0; i < words.length; i++) {
      if (i > 0) sb.append(',');
      sb.append('"').append(escape(words[i])).append('"');
    }
    sb.append(']');
    return sb.toString();
  }

  private static @NonNull String escape(@NonNull String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static @Nullable String extractText(@NonNull String json) {
    try {
      JSONObject j = new JSONObject(json);
      String t = j.optString("text", "");
      if (!t.isEmpty()) return t.trim();
      String p = j.optString("partial", "");
      return p.trim();
    } catch (Exception ignored) {
      return null;
    }
  }

  private static @Nullable String matchWakeWord(@NonNull String text, @NonNull String[] words) {
    // 识别结果可能包含/不包含空格差异（例如“嗨小布” vs “嗨 小布”），这里做空白归一化
    String norm = text.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    for (String w : words) {
      if (w == null) continue;
      String wn = w.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
      if (wn.isEmpty()) continue;
      if (norm.equals(wn)) return w.trim();
      if (norm.contains(wn)) return w.trim();
    }
    return null;
  }

  private void log(@NonNull String s) {
    ui.post(() -> listener.onLog(s));
  }

  private void error(@NonNull String message, @Nullable Throwable t) {
    ui.post(() -> listener.onError(message, t));
  }
}

