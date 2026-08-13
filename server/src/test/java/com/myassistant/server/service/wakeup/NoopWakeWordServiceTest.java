package com.myassistant.server.service.wakeup;

import com.myassistant.server.config.MyAssistantProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NoopWakeWordServiceTest {

  // ==================== 始终唤醒 ====================

  @Nested
  @DisplayName("Noop 始终唤醒")
  class AlwaysAwakenedTests {

    @Test
    @DisplayName("普通文本 → 始终唤醒 + 返回修剪后文本")
    void plainText() {
      NoopWakeWordService noop = new NoopWakeWordService(createProps("嗨 小奇"));
      WakeWordResult r = noop.detect("打开空调");
      assertTrue(r.awakened);
      assertEquals("打开空调", r.remainingText);
    }

    @Test
    @DisplayName("空文本 → 唤醒 + 空剩余文本")
    void emptyText() {
      NoopWakeWordService noop = new NoopWakeWordService(createProps("嗨 小奇"));
      WakeWordResult r = noop.detect("");
      assertTrue(r.awakened);
      assertEquals("", r.remainingText);
    }

    @Test
    @DisplayName("null → 唤醒 + 空剩余文本")
    void nullText() {
      NoopWakeWordService noop = new NoopWakeWordService(createProps("嗨 小奇"));
      WakeWordResult r = noop.detect(null);
      assertTrue(r.awakened);
      assertEquals("", r.remainingText);
    }

    @Test
    @DisplayName("带空白文本 → 唤醒 + 修剪后为空")
    void blankText() {
      NoopWakeWordService noop = new NoopWakeWordService(createProps("嗨 小奇"));
      WakeWordResult r = noop.detect("   ");
      assertTrue(r.awakened);
      assertEquals("", r.remainingText);
    }
  }

  // ==================== 自定义唤醒词 ====================

  @Nested
  @DisplayName("自定义唤醒词")
  class CustomWakeWordTests {

    @Test
    @DisplayName("自定义唤醒词 → wakeWord 字段正确")
    void customWakeWord() {
      NoopWakeWordService noop = new NoopWakeWordService(createProps("你好助手"));
      WakeWordResult r = noop.detect("测试");
      assertTrue(r.awakened);
      assertEquals("你好助手", r.wakeWord);
    }

    @Test
    @DisplayName("空白唤醒词 → 使用默认「嗨小奇」")
    void blankWakeWordFallsBack() {
      MyAssistantProperties props = new MyAssistantProperties();
      props.getWakeup().setWakeWord("   ");
      NoopWakeWordService noop = new NoopWakeWordService(props);
      WakeWordResult r = noop.detect("测试");
      assertEquals("嗨小奇", r.wakeWord);
    }

    @Test
    @DisplayName("null 唤醒词 → 使用默认「嗨小奇」")
    void nullWakeWordFallsBack() {
      MyAssistantProperties props = new MyAssistantProperties();
      props.getWakeup().setWakeWord(null);
      NoopWakeWordService noop = new NoopWakeWordService(props);
      WakeWordResult r = noop.detect("测试");
      assertEquals("嗨小奇", r.wakeWord);
    }
  }

  private static MyAssistantProperties createProps(String wakeWord) {
    MyAssistantProperties props = new MyAssistantProperties();
    props.getWakeup().setWakeWord(wakeWord);
    return props;
  }
}
