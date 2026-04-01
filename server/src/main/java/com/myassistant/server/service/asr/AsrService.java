package com.myassistant.server.service.asr;

public interface AsrService {
  /**
   * MVP: 直接把一段音频 bytes 转成最终文本（先用 mock）。
   * 后续接入讯飞流式 ASR 时，可改成“分片输入 + partial 回调”模型。
   */
  String transcribe(byte[] audioBytes, String audioFormat, int sampleRate) throws Exception;
}

