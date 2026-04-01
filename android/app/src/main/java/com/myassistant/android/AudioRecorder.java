package com.myassistant.android;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioRecorder {
  public interface Callback {
    void onAudioChunk(byte[] chunk, int len);
    void onError(Exception e);
    void onStopped();
  }

  private final int sampleRate = 16000;
  private final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
  private final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;

  private AudioRecord record;
  private Thread thread;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public int getSampleRate() {
    return sampleRate;
  }

  public boolean isRunning() {
    return running.get();
  }

  public void start(int chunkSizeBytes, Callback cb) {
    if (running.get()) return;
    running.set(true);

    int minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
    int bufSize = Math.max(minBuf, chunkSizeBytes * 4);

    record = new AudioRecord(
        MediaRecorder.AudioSource.VOICE_RECOGNITION,
        sampleRate,
        channelConfig,
        audioFormat,
        bufSize
    );

    if (record.getState() != AudioRecord.STATE_INITIALIZED) {
      running.set(false);
      cb.onError(new IllegalStateException("AudioRecord 初始化失败"));
      return;
    }

    record.startRecording();

    thread = new Thread(() -> {
      Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
      byte[] buf = new byte[chunkSizeBytes];
      try {
        while (running.get()) {
          int n = record.read(buf, 0, buf.length);
          if (n > 0) {
            cb.onAudioChunk(buf, n);
          } else {
            cb.onError(new RuntimeException("AudioRecord read failed: " + n));
            break;
          }
        }
      } catch (Exception e) {
        cb.onError(e);
      } finally {
        stopInternal();
        cb.onStopped();
      }
    }, "AudioRecorder");

    thread.start();
  }

  public void stop() {
    running.set(false);
  }

  private void stopInternal() {
    try {
      if (record != null) {
        try {
          record.stop();
        } catch (Exception ignored) {}
        record.release();
      }
    } finally {
      record = null;
      thread = null;
    }
  }
}

