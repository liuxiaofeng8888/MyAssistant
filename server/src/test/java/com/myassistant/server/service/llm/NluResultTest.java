package com.myassistant.server.service.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class NluResultTest {

  // ==================== chat 工厂方法 ====================

  @Nested
  @DisplayName("chat 工厂方法")
  class ChatFactoryTests {

    @Test
    @DisplayName("chat() → Kind.CHAT + 正确文本")
    void chatFactory() {
      NluResult r = NluResult.chat("你好");
      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertEquals("你好", r.assistantText);
    }

    @Test
    @DisplayName("chat() → toolName / toolArgs 为 null")
    void chatHasNoToolFields() {
      NluResult r = NluResult.chat("文本");
      assertNull(r.toolName);
      assertNull(r.toolArgs);
    }
  }

  // ==================== tool 工厂方法 ====================

  @Nested
  @DisplayName("tool 工厂方法")
  class ToolFactoryTests {

    @Test
    @DisplayName("tool() → Kind.TOOL_CALL + 正确字段")
    void toolFactory() {
      Map<String, Object> args = Map.of("title", "喝水", "fire_time", "2026-08-13T10:00:00");
      NluResult r = NluResult.tool("reminder.create", args, "好的");
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertEquals("reminder.create", r.toolName);
      assertEquals("喝水", r.toolArgs.get("title"));
      assertEquals("好的", r.assistantText);
    }

    @Test
    @DisplayName("tool() 空参数 → 空 Map")
    void toolWithEmptyArgs() {
      NluResult r = NluResult.tool("test.tool", Map.of(), null);
      assertEquals(NluResult.Kind.TOOL_CALL, r.kind);
      assertTrue(r.toolArgs.isEmpty());
      assertNull(r.assistantText);
    }
  }

  // ==================== 直接字段赋值 ====================

  @Nested
  @DisplayName("直接字段赋值")
  class DirectFieldTests {

    @Test
    @DisplayName("public 字段可直接读写")
    void publicFields() {
      NluResult r = new NluResult();
      r.kind = NluResult.Kind.CHAT;
      r.assistantText = "测试";
      r.toolName = "my.tool";
      r.toolArgs = Map.of("key", "val");

      assertEquals(NluResult.Kind.CHAT, r.kind);
      assertEquals("测试", r.assistantText);
      assertEquals("my.tool", r.toolName);
      assertEquals("val", r.toolArgs.get("key"));
    }
  }
}
