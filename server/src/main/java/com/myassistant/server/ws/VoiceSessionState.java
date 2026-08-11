package com.myassistant.server.ws;

import com.myassistant.server.service.vlm.MultimodalContext;
import java.io.ByteArrayOutputStream;

public class VoiceSessionState {
  public String conversationId;
  public String traceId;

  public String currentClientMsgId;
  public boolean started = false;
  public final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

  /** 多模态上下文（图片累积 + 文本指令） */
  public final MultimodalContext multimodalCtx = new MultimodalContext();

  public void resetTurn(String clientMsgId) {
    this.currentClientMsgId = clientMsgId;
    this.started = true;
    this.audioBuffer.reset();
  }

  /** 重置多模态上下文（新的视觉轮次开始时调用） */
  public void resetMultimodal() {
    this.multimodalCtx.getImages().clear();
    this.multimodalCtx.setUserText(null);
    this.multimodalCtx.setMode(MultimodalContext.Mode.QA);
  }
}

