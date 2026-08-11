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
  /** 图片 MIME 类型（image/jpeg, image/png 等） */
  public String mime_type;
  /** 多模态查询模式：describe / qa / tool */
  public String vlm_mode;

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

  public static VoiceMessage wakeupDetected(String clientMsgId, String wakeWord) {
    VoiceMessage m = new VoiceMessage();
    m.type = "wakeup_detected";
    m.client_msg_id = clientMsgId;
    m.text = wakeWord;
    return m;
  }

  // ==================== 多模态相关 ====================

  public static VoiceMessage vlmPartial(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "vlm_partial";
    m.client_msg_id = clientMsgId;
    m.text = text;
    m.is_final = false;
    return m;
  }

  public static VoiceMessage vlmFinal(String clientMsgId, String text) {
    VoiceMessage m = new VoiceMessage();
    m.type = "vlm_final";
    m.client_msg_id = clientMsgId;
    m.text = text;
    m.is_final = true;
    return m;
  }

  /**
   * 请求客户端补传图片。
   * 语音输入后若未带图片但意图疑似需要视觉理解，服务端可用此消息请求补充。
   */
  public static VoiceMessage requestImage(String clientMsgId, String prompt) {
    VoiceMessage m = new VoiceMessage();
    m.type = "request_image";
    m.client_msg_id = clientMsgId;
    m.text = prompt;
    return m;
  }
}

