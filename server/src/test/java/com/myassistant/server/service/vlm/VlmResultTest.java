package com.myassistant.server.service.vlm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VlmResultTest {

  // ==================== chat 工厂方法 ====================

  @Nested
  @DisplayName("chat 工厂方法")
  class ChatFactoryTests {

    @Test
    @DisplayName("chat() → Kind.CHAT + 正确文本")
    void chatFactory() {
      VlmResult r = VlmResult.chat("这是一张图片");
      assertEquals(VlmResult.Kind.CHAT, r.getKind());
      assertEquals("这是一张图片", r.getAssistantText());
    }

    @Test
    @DisplayName("chat() → toolName 为 null")
    void chatHasNoToolName() {
      VlmResult r = VlmResult.chat("描述");
      assertNull(r.getToolName());
      assertNull(r.getToolArgs());
    }

    @Test
    @DisplayName("chat(null) → assistantText 为 null")
    void chatWithNull() {
      VlmResult r = VlmResult.chat(null);
      assertEquals(VlmResult.Kind.CHAT, r.getKind());
      assertNull(r.getAssistantText());
    }
  }

  // ==================== tool 工厂方法 ====================

  @Nested
  @DisplayName("tool 工厂方法")
  class ToolFactoryTests {

    @Test
    @DisplayName("tool() → Kind.TOOL_CALL + 正确字段")
    void toolFactory() {
      Map<String, Object> args = Map.of("amount", 100);
      VlmResult r = VlmResult.tool("invoice.parse", args, "好的，我来处理");
      assertEquals(VlmResult.Kind.TOOL_CALL, r.getKind());
      assertEquals("invoice.parse", r.getToolName());
      assertEquals(100, r.getToolArgs().get("amount"));
      assertEquals("好的，我来处理", r.getAssistantText());
    }

    @Test
    @DisplayName("tool() 空参数 → 字段为空 Map")
    void toolWithEmptyArgs() {
      VlmResult r = VlmResult.tool("test.tool", Map.of(), null);
      assertEquals(VlmResult.Kind.TOOL_CALL, r.getKind());
      assertTrue(r.getToolArgs().isEmpty());
      assertNull(r.getAssistantText());
    }
  }

  // ==================== setters / getters ====================

  @Nested
  @DisplayName("setter/getter")
  class SetterGetterTests {

    @Test
    @DisplayName("setKind 可切换类型")
    void setKind() {
      VlmResult r = VlmResult.chat("text");
      r.setKind(VlmResult.Kind.TOOL_CALL);
      assertEquals(VlmResult.Kind.TOOL_CALL, r.getKind());
    }

    @Test
    @DisplayName("setAssistantText 覆盖文本")
    void setAssistantText() {
      VlmResult r = VlmResult.chat("old");
      r.setAssistantText("new");
      assertEquals("new", r.getAssistantText());
    }

    @Test
    @DisplayName("setToolName / setToolArgs")
    void setToolFields() {
      VlmResult r = new VlmResult();
      r.setToolName("my.tool");
      r.setToolArgs(Map.of("key", "val"));
      assertEquals("my.tool", r.getToolName());
      assertEquals("val", r.getToolArgs().get("key"));
    }
  }
}
