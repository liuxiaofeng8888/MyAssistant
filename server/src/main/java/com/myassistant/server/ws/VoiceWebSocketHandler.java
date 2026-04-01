package com.myassistant.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.service.asr.AsrService;
import com.myassistant.server.service.llm.NluResult;
import com.myassistant.server.service.llm.NluService;
import com.myassistant.server.service.tool.ToolDispatcher;
import com.myassistant.server.service.tool.ToolResult;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper om;
  private final AsrService asr;
  private final NluService nlu;
  private final ToolDispatcher tools;

  private final ConcurrentHashMap<String, VoiceSessionState> states = new ConcurrentHashMap<>();

  public VoiceWebSocketHandler(ObjectMapper om, AsrService asr, NluService nlu, ToolDispatcher tools) {
    this.om = om;
    this.asr = asr;
    this.nlu = nlu;
    this.tools = tools;
  }

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    VoiceSessionState st = new VoiceSessionState();
    st.conversationId = "conv-" + UUID.randomUUID();
    st.traceId = "t-" + UUID.randomUUID();
    states.put(session.getId(), st);
    send(session, VoiceMessage.ready(st.conversationId, st.traceId));
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    VoiceSessionState st = states.get(session.getId());
    if (st == null) {
      return;
    }

    VoiceMessage req;
    try {
      req = om.readValue(message.getPayload(), VoiceMessage.class);
    } catch (Exception e) {
      send(session, VoiceMessage.error(null, st.traceId, "BAD_REQUEST", "消息格式错误", false));
      return;
    }

    if (req.type == null) {
      send(session, VoiceMessage.error(req.client_msg_id, st.traceId, "BAD_REQUEST", "缺少 type", false));
      return;
    }

    switch (req.type) {
      case "start": {
        if (req.client_msg_id == null || req.client_msg_id.isBlank()) {
          send(session, VoiceMessage.error(null, st.traceId, "BAD_REQUEST", "缺少 client_msg_id", false));
          return;
        }
        st.resetTurn(req.client_msg_id);
        send(session, VoiceMessage.asrPartial(req.client_msg_id, ""));
        break;
      }
      case "audio": {
        if (!st.started) {
          send(session, VoiceMessage.error(req.client_msg_id, st.traceId, "BAD_REQUEST", "未 start 就发送 audio", false));
          return;
        }
        if (req.data_b64 == null || req.data_b64.isBlank()) {
          return;
        }
        byte[] chunk = Base64.getDecoder().decode(req.data_b64);
        st.audioBuffer.write(chunk);
        break;
      }
      case "stop": {
        if (!st.started) {
          return;
        }
        String clientMsgId = st.currentClientMsgId;
        st.started = false;

        byte[] audioBytes = st.audioBuffer.toByteArray();
        if (audioBytes.length == 0) {
          send(session, VoiceMessage.error(clientMsgId, st.traceId, "EMPTY_AUDIO", "没有收到音频数据", true));
          return;
        }

        // ASR：根据配置使用 Mock 或 讯飞
        String userText;
        try {
          userText = asr.transcribe(audioBytes, "pcm16", 16000);
        } catch (Exception e) {
          send(session, VoiceMessage.error(clientMsgId, st.traceId, "ASR_FAILED", "语音识别失败: " + e.getMessage(), true));
          return;
        }
        send(session, VoiceMessage.asrFinal(clientMsgId, userText));

        // MVP：规则 NLU（后续替换为讯飞星火工具调用）
        NluResult parsed;
        try {
          parsed = nlu.parse(userText);
        } catch (Exception e) {
          send(session, VoiceMessage.error(clientMsgId, st.traceId, "LLM_FAILED", "理解失败", true));
          return;
        }

        if (parsed.assistantText != null && !parsed.assistantText.isBlank()) {
          send(session, VoiceMessage.assistantDelta(clientMsgId, parsed.assistantText));
        }

        if (parsed.kind == NluResult.Kind.TOOL_CALL) {
          send(session, VoiceMessage.toolCall(clientMsgId, parsed.toolName, parsed.toolArgs));
          ToolResult tr;
          try {
            tr = tools.dispatch(parsed.toolName, parsed.toolArgs);
          } catch (Exception e) {
            send(session, VoiceMessage.toolResult(clientMsgId, parsed.toolName, false, Map.of("error", "exception")));
            send(session, VoiceMessage.assistantFinal(clientMsgId, "执行任务时出错了，请稍后重试。"));
            return;
          }

          send(session, VoiceMessage.toolResult(clientMsgId, parsed.toolName, tr.ok,
              tr.ok ? tr.result : Map.of("code", tr.errorCode, "message", tr.errorMessage)));

          if (tr.ok && "reminder.create".equals(parsed.toolName)) {
            Object fireTime = tr.result.get("fire_time");
            send(session, VoiceMessage.assistantFinal(clientMsgId, "好的，已创建提醒，时间是 " + fireTime + "。"));
          } else if (!tr.ok) {
            send(session, VoiceMessage.assistantFinal(clientMsgId, "没能完成任务：" + tr.errorMessage));
          } else {
            send(session, VoiceMessage.assistantFinal(clientMsgId, "任务已完成。"));
          }
        } else {
          send(session, VoiceMessage.assistantFinal(clientMsgId, parsed.assistantText));
        }
        break;
      }
      case "cancel": {
        st.started = false;
        st.audioBuffer.reset();
        if (req.client_msg_id != null) {
          send(session, VoiceMessage.assistantFinal(req.client_msg_id, "好的，已取消。"));
        }
        break;
      }
      case "ping": {
        session.sendMessage(new TextMessage("{\"type\":\"pong\"}", true));
        break;
      }
      default:
        send(session, VoiceMessage.error(req.client_msg_id, st.traceId, "BAD_REQUEST", "未知 type: " + req.type, false));
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    states.remove(session.getId());
  }

  private void send(WebSocketSession session, VoiceMessage msg) throws Exception {
    byte[] json = om.writeValueAsBytes(msg);
    session.sendMessage(new TextMessage(new String(json, StandardCharsets.UTF_8), true));
  }
}

