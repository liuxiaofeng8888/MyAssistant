package com.myassistant.server.ws;

import java.io.ByteArrayOutputStream;

public class VoiceSessionState {
  public String conversationId;
  public String traceId;

  public String currentClientMsgId;
  public boolean started = false;
  public final ByteArrayOutputStream audioBuffer = new ByteArrayOutputStream();

  public void resetTurn(String clientMsgId) {
    this.currentClientMsgId = clientMsgId;
    this.started = true;
    this.audioBuffer.reset();
  }
}

