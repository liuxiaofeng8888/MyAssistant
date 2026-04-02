package com.myassistant.server.service.wakeup;

import java.util.Optional;

/**
 * 唤醒专用识别：用有限词表（如 Vosk grammar）约束解码，与开放域 ASR 分离。
 */
public interface WakeGrammarRecognizer {

  /**
   * @return 命中时的识别文本（与词表中某条一致或为其子集）；未命中则 empty
   */
  Optional<String> recognizeWake(byte[] audioBytes, String audioFormat, int sampleRate) throws Exception;
}
