package com.myassistant.server.service.wakeup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WakeWordResultTest {

  // ==================== notAwakened ====================

  @Nested
  @DisplayName("notAwakened 工厂方法")
  class NotAwakenedTests {

    @Test
    @DisplayName("notAwakened → awakened=false + 保留原文")
    void basicNotAwakened() {
      WakeWordResult r = WakeWordResult.notAwakened("嗨 小奇", "今天天气怎么样");
      assertFalse(r.awakened);
      assertEquals("嗨 小奇", r.wakeWord);
      assertEquals("今天天气怎么样", r.remainingText);
    }

    @Test
    @DisplayName("notAwakened(null) → remainingText 为空字符串")
    void nullTextBecomesEmpty() {
      WakeWordResult r = WakeWordResult.notAwakened("嗨 小奇", null);
      assertFalse(r.awakened);
      assertEquals("", r.remainingText);
    }

    @Test
    @DisplayName("notAwakened 空文本 → remainingText 为空")
    void emptyText() {
      WakeWordResult r = WakeWordResult.notAwakened("嗨 小奇", "");
      assertFalse(r.awakened);
      assertEquals("", r.remainingText);
    }
  }

  // ==================== awakened ====================

  @Nested
  @DisplayName("awakened 工厂方法")
  class AwakenedTests {

    @Test
    @DisplayName("awakened → awakened=true + 剩余文本")
    void basicAwakened() {
      WakeWordResult r = WakeWordResult.awakened("嗨 小奇", "打开空调");
      assertTrue(r.awakened);
      assertEquals("嗨 小奇", r.wakeWord);
      assertEquals("打开空调", r.remainingText);
    }

    @Test
    @DisplayName("awakened(null) → remainingText 为空字符串")
    void nullRemainingBecomesEmpty() {
      WakeWordResult r = WakeWordResult.awakened("嗨 小奇", null);
      assertTrue(r.awakened);
      assertEquals("", r.remainingText);
    }

    @Test
    @DisplayName("awakened 空剩余 → remainingText 为空")
    void emptyRemaining() {
      WakeWordResult r = WakeWordResult.awakened("嗨 小奇", "");
      assertTrue(r.awakened);
      assertEquals("", r.remainingText);
    }
  }
}
