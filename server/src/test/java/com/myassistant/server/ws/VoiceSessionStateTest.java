package com.myassistant.server.ws;

import com.myassistant.server.service.vlm.MultimodalContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VoiceSessionStateTest {

  private VoiceSessionState state;

  @BeforeEach
  void setUp() {
    state = new VoiceSessionState();
  }

  // ==================== 初始状态 ====================

  @Nested
  @DisplayName("初始状态")
  class InitialStateTests {

    @Test
    @DisplayName("started 初始为 false")
    void initialNotStarted() {
      assertFalse(state.started);
    }

    @Test
    @DisplayName("audioBuffer 初始为空")
    void initialAudioBufferEmpty() {
      assertEquals(0, state.audioBuffer.size());
    }

    @Test
    @DisplayName("multimodalCtx 初始已创建")
    void multimodalCtxNotNull() {
      assertNotNull(state.multimodalCtx);
    }

    @Test
    @DisplayName("conversationId / traceId 初始为 null")
    void initialIdsNull() {
      assertNull(state.conversationId);
      assertNull(state.traceId);
    }
  }

  // ==================== resetTurn ====================

  @Nested
  @DisplayName("resetTurn 轮次重置")
  class ResetTurnTests {

    @Test
    @DisplayName("resetTurn → started=true + clientMsgId 设置 + audioBuffer 清空")
    void resetTurnBasic() {
      state.audioBuffer.write(new byte[]{1, 2, 3}, 0, 3);
      state.resetTurn("msg-001");
      assertTrue(state.started);
      assertEquals("msg-001", state.currentClientMsgId);
      assertEquals(0, state.audioBuffer.size());
    }

    @Test
    @DisplayName("多次 resetTurn → 仅保留最后一次 msgId")
    void multipleResetTurns() {
      state.resetTurn("msg-1");
      state.resetTurn("msg-2");
      assertEquals("msg-2", state.currentClientMsgId);
    }
  }

  // ==================== resetMultimodal ====================

  @Nested
  @DisplayName("resetMultimodal 多模态重置")
  class ResetMultimodalTests {

    @Test
    @DisplayName("resetMultimodal → 图片清空 + 文本置 null + 模式恢复 QA")
    void resetMultimodalBasic() {
      state.multimodalCtx.addImage(new MultimodalContext.ImageInput("data", "image/jpeg"));
      state.multimodalCtx.setUserText("描述一下");
      state.multimodalCtx.setMode(MultimodalContext.Mode.TOOL);

      state.resetMultimodal();

      assertTrue(state.multimodalCtx.getImages().isEmpty());
      assertNull(state.multimodalCtx.getUserText());
      assertEquals(MultimodalContext.Mode.QA, state.multimodalCtx.getMode());
    }

    @Test
    @DisplayName("resetMultimodal 幂等 → 多次调用不报错")
    void resetMultimodalIdempotent() {
      state.resetMultimodal();
      state.resetMultimodal();
      assertTrue(state.multimodalCtx.getImages().isEmpty());
    }
  }
}
