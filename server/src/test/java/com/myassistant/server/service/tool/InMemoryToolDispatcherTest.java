package com.myassistant.server.service.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryToolDispatcherTest {

  private InMemoryToolDispatcher dispatcher;

  @BeforeEach
  void setUp() {
    dispatcher = new InMemoryToolDispatcher();
  }

  // ==================== reminder.create ====================

  @Nested
  @DisplayName("reminder.create")
  class ReminderCreateTests {

    @Test
    @DisplayName("带 fire_time → 返回 ok + reminder_id + fire_time")
    void withFireTime() {
      ToolResult r = dispatcher.dispatch("reminder.create", Map.of(
          "fire_time", "2026-08-11T08:00:00+08:00",
          "title", "开会"
      ));
      assertTrue(r.ok);
      assertNotNull(r.result.get("reminder_id"));
      assertTrue(((String) r.result.get("reminder_id")).startsWith("r-"));
      assertEquals("2026-08-11T08:00+08:00", r.result.get("fire_time"));
    }

    @Test
    @DisplayName("无 fire_time → fallback 为 now+30min")
    void noFireTime() {
      ToolResult r = dispatcher.dispatch("reminder.create", Map.of(
          "title", "喝水"
      ));
      assertTrue(r.ok);
      assertNotNull(r.result.get("fire_time"));
    }

    @Test
    @DisplayName("带 after_minutes → 正确计算")
    void afterMinutes() {
      ToolResult r = dispatcher.dispatch("reminder.create", Map.of(
          "title", "喝水",
          "after_minutes", 10
      ));
      assertTrue(r.ok);
      assertNotNull(r.result.get("fire_time"));
    }

    @Test
    @DisplayName("空参数 → 仍正常返回")
    void emptyArgs() {
      ToolResult r = dispatcher.dispatch("reminder.create", Map.of());
      assertTrue(r.ok);
      assertNotNull(r.result.get("reminder_id"));
      assertNotNull(r.result.get("fire_time"));
    }
  }

  // ==================== 未知工具 ====================

  @Nested
  @DisplayName("未知工具")
  class UnknownToolTests {

    @Test
    @DisplayName("未注册的 toolName → TOOL_NOT_FOUND")
    void unknownTool() {
      ToolResult r = dispatcher.dispatch("weather.get", Map.of());
      assertFalse(r.ok);
      assertEquals("TOOL_NOT_FOUND", r.errorCode);
    }
  }

  // ==================== ToolResult 静态工厂 ====================

  @Nested
  @DisplayName("ToolResult 工厂方法")
  class FactoryMethodTests {

    @Test
    @DisplayName("ok() → ok=true")
    void okFactory() {
      ToolResult r = ToolResult.ok(Map.of("key", "value"));
      assertTrue(r.ok);
      assertEquals("value", r.result.get("key"));
    }

    @Test
    @DisplayName("fail() → ok=false + code + message")
    void failFactory() {
      ToolResult r = ToolResult.fail("ERR_001", "something went wrong");
      assertFalse(r.ok);
      assertEquals("ERR_001", r.errorCode);
      assertEquals("something went wrong", r.errorMessage);
    }
  }
}
