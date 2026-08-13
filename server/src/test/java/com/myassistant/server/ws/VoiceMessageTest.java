package com.myassistant.server.ws;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoiceMessageTest {

  // ==================== ready ====================

  @Nested
  @DisplayName("ready 消息")
  class ReadyTests {

    @Test
    @DisplayName("ready() → type=ready + conversation_id + trace_id")
    void readyMessage() {
      VoiceMessage m = VoiceMessage.ready("conv-1", "trace-1");
      assertEquals("ready", m.type);
      assertEquals("conv-1", m.conversation_id);
      assertEquals("trace-1", m.trace_id);
    }
  }

  // ==================== asr_partial / asr_final ====================

  @Nested
  @DisplayName("ASR 消息")
  class AsrMessageTests {

    @Test
    @DisplayName("asrPartial → type=asr_partial + is_final=false")
    void asrPartial() {
      VoiceMessage m = VoiceMessage.asrPartial("msg-1", "你好");
      assertEquals("asr_partial", m.type);
      assertEquals("msg-1", m.client_msg_id);
      assertEquals("你好", m.text);
      assertFalse(m.is_final);
    }

    @Test
    @DisplayName("asrFinal → type=asr_final + is_final=true")
    void asrFinal() {
      VoiceMessage m = VoiceMessage.asrFinal("msg-2", "你好世界");
      assertEquals("asr_final", m.type);
      assertEquals("msg-2", m.client_msg_id);
      assertEquals("你好世界", m.text);
      assertTrue(m.is_final);
    }
  }

  // ==================== assistant_delta / assistant_final ====================

  @Nested
  @DisplayName("助手回复消息")
  class AssistantMessageTests {

    @Test
    @DisplayName("assistantDelta → type=assistant_delta")
    void delta() {
      VoiceMessage m = VoiceMessage.assistantDelta("msg-3", "思考中");
      assertEquals("assistant_delta", m.type);
      assertEquals("msg-3", m.client_msg_id);
      assertEquals("思考中", m.text);
    }

    @Test
    @DisplayName("assistantFinal → type=assistant_final")
    void finalMsg() {
      VoiceMessage m = VoiceMessage.assistantFinal("msg-4", "好的，已为你处理");
      assertEquals("assistant_final", m.type);
      assertEquals("好的，已为你处理", m.text);
    }
  }

  // ==================== tool_call / tool_result ====================

  @Nested
  @DisplayName("工具消息")
  class ToolMessageTests {

    @Test
    @DisplayName("toolCall → type=tool_call + name + args")
    void toolCall() {
      Map<String, Object> args = Map.of("title", "喝水");
      VoiceMessage m = VoiceMessage.toolCall("msg-5", "reminder.create", args);
      assertEquals("tool_call", m.type);
      assertEquals("msg-5", m.client_msg_id);
      assertEquals("reminder.create", m.name);
      assertEquals("喝水", m.args.get("title"));
    }

    @Test
    @DisplayName("toolResult(ok) → ok=true + result")
    void toolResultOk() {
      Map<String, Object> result = Map.of("reminder_id", "r-123");
      VoiceMessage m = VoiceMessage.toolResult("msg-6", "reminder.create", true, result);
      assertEquals("tool_result", m.type);
      assertTrue(m.ok);
      assertEquals("r-123", m.result.get("reminder_id"));
    }

    @Test
    @DisplayName("toolResult(fail) → ok=false")
    void toolResultFail() {
      VoiceMessage m = VoiceMessage.toolResult("msg-7", "unknown", false, Map.of());
      assertFalse(m.ok);
    }
  }

  // ==================== error ====================

  @Nested
  @DisplayName("错误消息")
  class ErrorMessageTests {

    @Test
    @DisplayName("error → type=error + 所有字段")
    void errorMessage() {
      VoiceMessage m = VoiceMessage.error("msg-8", "trace-8", "ASR_FAIL", "识别失败", true);
      assertEquals("error", m.type);
      assertEquals("msg-8", m.client_msg_id);
      assertEquals("trace-8", m.trace_id);
      assertEquals("ASR_FAIL", m.code);
      assertEquals("识别失败", m.message);
      assertTrue(m.retryable);
    }

    @Test
    @DisplayName("error(retryable=false)")
    void nonRetryableError() {
      VoiceMessage m = VoiceMessage.error("msg-9", "trace-9", "AUTH_ERR", "鉴权失败", false);
      assertFalse(m.retryable);
    }
  }

  // ==================== wakeup_detected ====================

  @Nested
  @DisplayName("唤醒消息")
  class WakeupMessageTests {

    @Test
    @DisplayName("wakeupDetected → type=wakeup_detected + text=唤醒词")
    void wakeupDetected() {
      VoiceMessage m = VoiceMessage.wakeupDetected("msg-10", "嗨 小奇");
      assertEquals("wakeup_detected", m.type);
      assertEquals("嗨 小奇", m.text);
    }
  }

  // ==================== VLM 多模态消息 ====================

  @Nested
  @DisplayName("VLM 多模态消息")
  class VlmMessageTests {

    @Test
    @DisplayName("vlmPartial → type=vlm_partial + is_final=false")
    void vlmPartial() {
      VoiceMessage m = VoiceMessage.vlmPartial("msg-11", "正在分析图片");
      assertEquals("vlm_partial", m.type);
      assertEquals("msg-11", m.client_msg_id);
      assertEquals("正在分析图片", m.text);
      assertFalse(m.is_final);
    }

    @Test
    @DisplayName("vlmFinal → type=vlm_final + is_final=true")
    void vlmFinal() {
      VoiceMessage m = VoiceMessage.vlmFinal("msg-12", "图片中是一只猫");
      assertEquals("vlm_final", m.type);
      assertEquals("msg-12", m.client_msg_id);
      assertEquals("图片中是一只猫", m.text);
      assertTrue(m.is_final);
    }

    @Test
    @DisplayName("requestImage → type=request_image + prompt")
    void requestImage() {
      VoiceMessage m = VoiceMessage.requestImage("msg-13", "请拍照上传发票");
      assertEquals("request_image", m.type);
      assertEquals("msg-13", m.client_msg_id);
      assertEquals("请拍照上传发票", m.text);
    }
  }
}
