package com.myassistant.server.ws;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class VoiceMessage {
  public String type;

  // client -> server fields
  public String client_msg_id;
  public Integer seq;
  public String data_b64;

  // server -> client fields
  public String conversation_id;
  public String trace_id;
  public String text;
  public Boolean is_final;

  public String name;
  public Map<String, Object> args;
  public Boolean ok;
  public Map<String, Object> result;

  public String code;
  public String message;
  public Boolean retryable;

  public static VoiceMessage ready(String conversationId, String traceId) {
    VoiceMessage m = new VoiceMessage();
    m.type = "ready";
    m.conversation_id = conversationId;
    m.trace_id = traceId;
    return m;
  }

  public static VoiceMessage asrPartial(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "asr_partial";
    m.client_msg_id = clientMsgId;
    m.text = text;
    m.is_final = false;
    return m;
  }

  public static VoiceMessage asrFinal(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "asr_final";
    m.client_msg_id = clientMsgId;
    m.text = text;
    m.is_final = true;
    return m;
  }

  public static VoiceMessage assistantDelta(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "assistant_delta";
    m.client_msg_id = clientMsgId;
    m.text = text;
    return m;
  }

  public static VoiceMessage assistantFinal(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "assistant_final";
    m.client_msg_id = clientMsgId;
    m.text = text;
    return m;
  }

  public static VoiceMessage toolCall(String clientMsgId, String name, Map<String, Object> args) {
    VoiceMessage m = new VoiceMessage();
    m.type = "tool_call";
    m.client_msg_id = clientMsgId;
    m.name = name;
    m.args = args;
    return m;
  }

  public static VoiceMessage toolResult(String clientMsgId, String name, boolean ok, Map<String, Object> result) {
    VoiceMessage m = new VoiceMessage();
    m.type = "tool_result";
    m.client_msg_id = clientMsgId;
    m.name = name;
    m.ok = ok;
    m.result = result;
    return m;
  }

  public static VoiceMessage error(String clientMsgId, String traceId, String code, String message, boolean retryable) {
    VoiceMessage m = new VoiceMessage();
    m.type = "error";
    m.client_msg_id = clientMsgId;
    m.trace_id = traceId;
    m.code = code;
    m.message = message;
    m.retryable = retryable;
    return m;
  }
}

