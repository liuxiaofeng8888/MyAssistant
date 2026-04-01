package com.myassistant.server.service.wakeup;

public final class WakeWordResult {
  public final boolean awakened;
  public final String wakeWord;
  public final String remainingText;

  private WakeWordResult(boolean awakened, String wakeWord, String remainingText) {
    this.awakened = awakened;
    this.wakeWord = wakeWord;
    this.remainingText = remainingText;
  }

  public static WakeWordResult notAwakened(String configuredWakeWord, String originalText) {
    return new WakeWordResult(false, configuredWakeWord, originalText == null ? "" : originalText);
  }

  public static WakeWordResult awakened(String configuredWakeWord, String remainingText) {
    return new WakeWordResult(true, configuredWakeWord, remainingText == null ? "" : remainingText);
  }
}

