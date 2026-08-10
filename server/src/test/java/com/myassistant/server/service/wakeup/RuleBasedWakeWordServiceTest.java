package com.myassistant.server.service.wakeup;

import com.myassistant.server.config.MyAssistantProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedWakeWordServiceTest {

  private RuleBasedWakeWordService wakeup;

  @BeforeEach
  void setUp() {
    MyAssistantProperties props = new MyAssistantProperties();
    props.getWakeup().setWakeWord("嗨 小奇");
    props.getWakeup().setWakeAliases(List.of("嗨小齐", "小奇"));
    wakeup = new RuleBasedWakeWordService(props);
  }

  // ==================== 精确唤醒词匹配 ====================

  @Nested
  @DisplayName("精确唤醒词匹配")
  class ExactMatchTests {

    @Test
    @DisplayName("「嗨 小奇」→ 唤醒 + 无剩余文本")
    void exactWakeWord() {
      WakeWordResult r = wakeup.detect("嗨 小奇");
      assertTrue(r.awakened);
      assertEquals("嗨 小奇", r.wakeWord);
      assertTrue(r.remainingText.isEmpty());
    }

    @Test
    @DisplayName("「嗨小奇」→ 归一化后唤醒（去空格）")
    void noSpaces() {
      WakeWordResult r = wakeup.detect("嗨小奇");
      assertTrue(r.awakened);
    }

    @Test
    @DisplayName("「嗨小奇打开空调」→ 唤醒 + 剩余指令")
    void wakeWordWithCommand() {
      WakeWordResult r = wakeup.detect("嗨小奇打开空调");
      assertTrue(r.awakened);
      assertEquals("打开空调", r.remainingText);
    }
  }

  // ==================== 别名匹配 ====================

  @Nested
  @DisplayName("别名匹配")
  class AliasMatchTests {

    @Test
    @DisplayName("「嗨小齐」→ 别名命中唤醒")
    void aliasHiXiaoQi() {
      WakeWordResult r = wakeup.detect("嗨小齐");
      assertTrue(r.awakened);
      assertEquals("嗨 小奇", r.wakeWord);
    }

    @Test
    @DisplayName("「小奇」→ 别名命中唤醒")
    void aliasXiaoQi() {
      WakeWordResult r = wakeup.detect("小奇");
      assertTrue(r.awakened);
    }

    @Test
    @DisplayName("「小奇帮我查天气」→ 别名 + 指令剥离")
    void aliasWithCommand() {
      WakeWordResult r = wakeup.detect("小奇帮我查天气");
      assertTrue(r.awakened);
      assertEquals("帮我查天气", r.remainingText);
    }

    @Test
    @DisplayName("「小猫」→ 不触发（「小奇」前缀匹配要求严格起始）")
    void notAliasPrefix() {
      WakeWordResult r = wakeup.detect("小猫");
      assertFalse(r.awakened);
    }
  }

  // ==================== 未唤醒 ====================

  @Nested
  @DisplayName("未唤醒")
  class NotAwakenedTests {

    @Test
    @DisplayName("普通文本 → 未唤醒")
    void plainText() {
      WakeWordResult r = wakeup.detect("今天天气怎么样");
      assertFalse(r.awakened);
      assertEquals("今天天气怎么样", r.remainingText);
    }

    @Test
    @DisplayName("空文本 → 未唤醒")
    void emptyText() {
      WakeWordResult r = wakeup.detect("");
      assertFalse(r.awakened);
    }

    @Test
    @DisplayName("null → 未唤醒")
    void nullText() {
      WakeWordResult r = wakeup.detect(null);
      assertFalse(r.awakened);
    }
  }

  // ==================== Grammar 命中后处理 ====================

  @Nested
  @DisplayName("Grammar 命中后的 resolveAfterGrammarHit")
  class GrammarHitTests {

    @Test
    @DisplayName("「嗨 小奇 打开空调」→ 剥离唤醒词")
    void stripAfterGrammar() {
      WakeWordResult r = wakeup.resolveAfterGrammarHit("嗨 小奇 打开空调");
      assertTrue(r.awakened);
      assertEquals("打开空调", r.remainingText);
    }

    @Test
    @DisplayName("「嗨 小奇」→ 仅唤醒词")
    void grammarOnlyWakeWord() {
      WakeWordResult r = wakeup.resolveAfterGrammarHit("嗨 小奇");
      assertTrue(r.awakened);
      assertTrue(r.remainingText.isEmpty());
    }

    @Test
    @DisplayName("Grammar 命中但 ASR 未识别出唤醒词 → 仍算唤醒")
    void grammarHitButNoWakeInAsr() {
      // grammar 已确认唤醒，即使 ASR 文本不包含唤醒词也通过
      WakeWordResult r = wakeup.resolveAfterGrammarHit("打开空调");
      assertTrue(r.awakened);
      assertEquals("打开空调", r.remainingText);
    }
  }

  // ==================== 带标点符号 ====================

  @Nested
  @DisplayName("标点容错")
  class PunctuationTests {

    @Test
    @DisplayName("「嗨，小奇！」→ 标点不影响匹配")
    void commasAndExclamation() {
      WakeWordResult r = wakeup.detect("嗨，小奇！");
      assertTrue(r.awakened);
    }

    @Test
    @DisplayName("「嗨小奇。打开空调」→ 句号分隔")
    void periodSeparated() {
      WakeWordResult r = wakeup.detect("嗨小奇。打开空调");
      assertTrue(r.awakened);
    }
  }
}
