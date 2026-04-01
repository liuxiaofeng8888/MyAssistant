package com.myassistant.server.service.asr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "myassistant.asr", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockAsrService implements AsrService {
  @Override
  public String transcribe(byte[] audioBytes, String audioFormat, int sampleRate) {
    // MVP: 先跑通链路。后续切换为讯飞 ASR（流式）实现。
    if (audioBytes == null || audioBytes.length == 0) {
      return "";
    }
    return "提醒我30分钟后喝水";
  }
}

