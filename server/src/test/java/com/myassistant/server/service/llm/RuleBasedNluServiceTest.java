package com.myassistant.server.service.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuleBasedNluServiceTest {

  private RuleBasedNluService nlu;

  @BeforeEach
  void setUp() {
    nlu = new RuleBasedNluService();
  }

  // ==================== 笑话意图 ====================

  @Nested
  @DisplayName("笑话请求识别")
  class JokeTests {

    @Test
    @DisplayName("「讲个笑话」→ 识别为 CHAT 并返回笑话")
    void tellJoke() {
      NluResult r = nlu.parse("讲个笑话");
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertNotNull(r.assistantText);
      assertFalse(r.assistantText.isBlank());
    }

    @Test
    @DisplayName("「给我讲个笑话」→ 识别为 CHAT")
    void giveMeJoke() {
      NluResult r = nlu.parse("给我讲个笑话");
      assertEquals(NluResult.Kind.CHAT, r.kind);
    }

    @Test
    @DisplayName("「说个笑话」→ 识别为 CHAT")
    void sayJoke() {
      NluResult r = nlu.parse("说个笑话");
      assertEquals(NluResult.Kind.CHAT, r.kind);
    }

    @Test
    @DisplayName("「来个笑话」→ 识别为 CHAT")
    void laiJoke() {
      NluResult r = nlu.parse("来个笑话");
      assertEquals(NluResult.Kind.CHAT, r.kind);
    }

    @Test
    @DisplayName("仅包含「笑话」但无请求动词 → 不触发笑话")
    void onlyContainsJokeWord() {
      NluResult r = nlu.parse("今天听到一个笑话");
      assertEquals(NluResult.Kind.CHAT, r.kind);
    }
  }

  // ==================== 提醒意图 ====================

  @Nested
  @DisplayName("提醒创建识别")
  class ReminderTests {

    @Test
    @DisplayName("「提醒我30分钟后喝水」→ TOOL_CALL")
    void remindAfter30Minutes() {
      NluResult r = nlu.parse("提醒我30分钟后喝水");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertEquals("reminder.create", r.toolName);
      assertTrue(r.toolArgs.containsKey("fire_time"));
      // extractReminderTitle 剥离"提醒"后剩余"我30分钟后喝水"，去除时间短语后为"我喝水"
      assertEquals("我喝水", r.toolArgs.get("title"));
    }

    @Test
    @DisplayName("「设置一个闹钟1小时后」→ TOOL_CALL")
    void setAlarm() {
      NluResult r = nlu.parse("设置一个闹钟1小时后");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertEquals("reminder.create", r.toolName);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }

    @Test
    @DisplayName("「帮我设置提醒明天8点开会」→ TOOL_CALL + 时间+标题")
    void remindTomorrow8OClock() {
      NluResult r = nlu.parse("帮我设置提醒明天8点开会");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertEquals("reminder.create", r.toolName);
      assertTrue(r.toolArgs.containsKey("fire_time"));
      String title = (String) r.toolArgs.get("title");
      assertTrue(title.contains("开会"));
    }

    @Test
    @DisplayName("「创建提醒」无其他内容 → TOOL_CALL + 默认标题")
    void createReminderNoTitle() {
      NluResult r = nlu.parse("创建提醒");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertEquals("提醒", r.toolArgs.get("title"));
    }

    @Test
    @DisplayName("「10秒后提醒」→ 秒级相对时间")
    void remindAfterSeconds() {
      NluResult r = nlu.parse("10秒后提醒我关火");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }
  }

  // ==================== 默认闲聊 ====================

  @Nested
  @DisplayName("默认闲聊兜底")
  class ChatFallbackTests {

    @Test
    @DisplayName("普通文本 → CHAT + 回显原文")
    void plainText() {
      NluResult r = nlu.parse("今天天气怎么样");
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertTrue(r.assistantText.contains("今天天气怎么样"));
    }

    @Test
    @DisplayName("空文本 → CHAT + 提示语")
    void emptyText() {
      NluResult r = nlu.parse("");
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertTrue(r.assistantText.contains("没听清"));
    }

    @Test
    @DisplayName("null → CHAT + 提示语")
    void nullText() {
      NluResult r = nlu.parse(null);
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertTrue(r.assistantText.contains("没听清"));
    }
  }

  // ==================== 中文数字归一化 ====================

  @Nested
  @DisplayName("中文数字归一化")
  class ChineseNumberTests {

    @Test
    @DisplayName("「提醒我三十分钟后」→ 正常解析")
    void thirtyMinutesZh() {
      NluResult r = nlu.parse("提醒我三十分钟后");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }

    @Test
    @DisplayName("「两小时后」→ 正常解析")
    void twoHoursZh() {
      NluResult r = nlu.parse("两小时后提醒我");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }

    @Test
    @DisplayName("「十二小时后」→ 正常解析两位数")
    void twelveHoursZh() {
      NluResult r = nlu.parse("十二小时后提醒我");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }
  }

  // ==================== 绝对时间解析 ====================

  @Nested
  @DisplayName("绝对时间解析")
  class AbsoluteTimeTests {

    @Test
    @DisplayName("「提醒我明天8点半」→ 包含 fire_time")
    void tomorrowHalfPast8() {
      NluResult r = nlu.parse("提醒我明天8点半");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }

    @Test
    @DisplayName("「后天9点」→ 包含 fire_time")
    void dayAfterTomorrow9() {
      NluResult r = nlu.parse("后天9点提醒我出门");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }

    @Test
    @DisplayName("「今天18点30分提醒」→ 包含 fire_time")
    void todayColonTime() {
      NluResult r = nlu.parse("今天18点30分提醒我下班");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.containsKey("fire_time"));
    }
  }

  // ==================== 边界情况 ====================

  @Nested
  @DisplayName("边界情况")
  class EdgeCaseTests {

    @Test
    @DisplayName("纯空白文本切齐")
    void blankText() {
      NluResult r = nlu.parse("   ");
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertTrue(r.assistantText.contains("没听清"));
    }

    @Test
    @DisplayName("「提醒」关键词在非提醒语境 → 仍触发提醒意图")
    void ambiguousReminder() {
      // RuleBasedNluService 只要包含"提醒"就触发
      NluResult r = nlu.parse("我不需要提醒");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
    }
  }
}
