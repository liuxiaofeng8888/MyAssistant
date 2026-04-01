package com.myassistant.android;

/**
 * 极简 VAD：基于 PCM16 单声道帧的 RMS 能量 + 滞回。
 * 适合 MVP 做“有声才发 / 静音自动停”，不追求在强噪声环境下的最优效果。
 */
public final class SimpleVad {
  private final double startRms;
  private final double endRms;

  public SimpleVad(double startRms, double endRms) {
    if (endRms > startRms) {
      throw new IllegalArgumentException("endRms must be <= startRms");
    }
    this.startRms = startRms;
    this.endRms = endRms;
  }

  /**
   * @param pcm16Bytes little-endian PCM16 mono
   * @param len number of bytes
   */
  public boolean isSpeech(byte[] pcm16Bytes, int len, boolean currentlySpeaking) {
    double rms = rms(pcm16Bytes, len);
    if (currentlySpeaking) {
      return rms >= endRms;
    }
    return rms >= startRms;
  }

  /**
   * Normalize RMS to 0..1 relative to 16-bit range.
   */
  public static double rms(byte[] pcm16Bytes, int len) {
    if (pcm16Bytes == null || len <= 1) return 0.0;
    int samples = len / 2;
    if (samples <= 0) return 0.0;

    long sumSq = 0;
    int off = 0;
    for (int i = 0; i < samples; i++) {
      int lo = pcm16Bytes[off++] & 0xff;
      int hi = pcm16Bytes[off++];
      short s = (short) ((hi << 8) | lo);
      int v = s;
      sumSq += (long) v * (long) v;
    }
    double meanSq = (double) sumSq / (double) samples;
    double rms = Math.sqrt(meanSq);
    return rms / 32768.0;
  }
}

