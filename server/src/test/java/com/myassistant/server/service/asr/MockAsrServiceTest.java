package com.myassistant.server.service.asr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockAsrServiceTest {

  private MockAsrService service;

  @BeforeEach
  void setUp() {
    service = new MockAsrService();
  }

  // ==================== transcribe ====================

  @Nested
  @DisplayName("transcribe 语音识别")
  class TranscribeTests {

    @Test
    @DisplayName("null 音频 → 返回空字符串")
    void nullAudio() throws Exception {
      String result = service.transcribe(null, "pcm16", 16000);
      assertEquals("", result);
    }

    @Test
    @DisplayName("空数组 → 返回空字符串")
    void emptyAudio() throws Exception {
      String result = service.transcribe(new byte[0], "pcm16", 16000);
      assertEquals("", result);
    }

    @Test
    @DisplayName("有效音频 → 返回固定 Mock 文本")
    void validAudio() throws Exception {
      byte[] audio = new byte[]{1, 2, 3, 4};
      String result = service.transcribe(audio, "pcm16", 16000);
      assertEquals("提醒我30分钟后喝水", result);
    }

    @Test
    @DisplayName("不同 audioFormat 不影响 Mock 返回")
    void anyFormat() throws Exception {
      byte[] audio = new byte[]{1, 2, 3};
      String result = service.transcribe(audio, "wav", 16000);
      assertEquals("提醒我30分钟后喝水", result);
    }

    @Test
    @DisplayName("null audioFormat → 正常返回")
    void nullFormat() throws Exception {
      byte[] audio = new byte[]{1, 2};
      String result = service.transcribe(audio, null, 16000);
      assertEquals("提醒我30分钟后喝水", result);
    }
  }
}
