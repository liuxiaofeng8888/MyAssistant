package com.myassistant.server.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.config.MyAssistantProperties;
import com.myassistant.server.service.asr.AsrService;
import com.myassistant.server.service.llm.NluResult;
import com.myassistant.server.service.llm.NluService;
import com.myassistant.server.service.tool.ToolDispatcher;
import com.myassistant.server.service.tool.ToolResult;
import com.myassistant.server.service.wakeup.WakeGrammarRecognizer;
import com.myassistant.server.service.wakeup.WakeWordResult;
import com.myassistant.server.service.wakeup.WakeWordService;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class VoiceWebSocketHandler extends TextWebSocketHandler {
  private final ObjectMapper om;
  private final AsrService asr;
  private final NluService nlu;
  private final ToolDispatcher tools;
  private final WakeWordService wakeup;
  private final MyAssistantProperties assistantProps;
  private final WakeGrammarRecognizer wakeGrammar;

  private final ConcurrentHashMap<String, VoiceSessionState> states = new ConcurrentHashMap<>();

  public VoiceWebSocketHandler(
      ObjectMapper om,
      AsrService asr,
      NluService nlu,
      ToolDispatcher tools,
      WakeWordService wakeup,
      MyAssistantProperties assistantProps,
      ObjectProvider<WakeGrammarRecognizer> wakeGrammarProvider) {
    this.om = om;
    this.asr = asr;
    this.nlu = nlu;
    this.tools = tools;
    this.wakeup = wakeup;
    this.assistantProps = assistantProps;
    this.wakeGrammar = wakeGrammarProvider.getIfAvailable();
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

        byte[] rawAudioBytes = st.audioBuffer.toByteArray();
        if (rawAudioBytes.length == 0) {
          send(session, VoiceMessage.error(clientMsgId, st.traceId, "EMPTY_AUDIO", "没有收到音频数据", true));
          return;
        }

        AudioInput in = decodeWavIfNeeded(rawAudioBytes);

        // 开放域 ASR（联调展示与 NLU 主文本）
        String userText;
        try {
          userText = asr.transcribe(in.audioBytes, in.audioFormat, in.sampleRate);
        } catch (Exception e) {
          send(session, VoiceMessage.error(clientMsgId, st.traceId, "ASR_FAILED", "语音识别失败: " + e.getMessage(), true));
          return;
        }
        send(session, VoiceMessage.asrFinal(clientMsgId, userText));

        // 唤醒：Vosk 下可走 grammar 专用链路；否则仅文本规则
        WakeWordResult ww = resolveWake(userText, in);
        if (!ww.awakened) {
          send(session, VoiceMessage.assistantFinal(clientMsgId, "请先说“" + ww.wakeWord + "”唤醒我。"));
          return;
        }
        if (ww.remainingText != null && !ww.remainingText.trim().isEmpty()) {
          // 如果同一句里包含唤醒词+指令，剥离唤醒词后继续理解
          userText = ww.remainingText.trim();
        } else {
          // 只有唤醒词，没有后续指令
          send(session, VoiceMessage.wakeupDetected(clientMsgId, ww.wakeWord));
          send(session, VoiceMessage.assistantFinal(clientMsgId, "我在，你说。"));
          return;
        }

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
  protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
    try {
      VoiceSessionState st = states.get(session.getId());
      if (st == null) {
        return;
      }
      if (!st.started) {
        send(session, VoiceMessage.error(null, st.traceId, "BAD_REQUEST", "未 start 就发送 binary 音频", false));
        return;
      }

      ByteBuffer payload = message.getPayload();
      if (!payload.hasRemaining()) {
        return;
      }

      byte[] chunk = new byte[payload.remaining()];
      payload.get(chunk);
      st.audioBuffer.write(chunk);
    } catch (Exception ignored) {
      // Best-effort: binary 音频帧异常不应打断 WS 主流程
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    states.remove(session.getId());
  }

  private WakeWordResult resolveWake(String userText, AudioInput in) {
    if (wakeGrammar != null && assistantProps.getWakeup().isEnabled()) {
      try {
        Optional<String> g = wakeGrammar.recognizeWake(in.audioBytes, in.audioFormat, in.sampleRate);
        if (g.isPresent() && !g.get().isBlank()) {
          return wakeup.resolveAfterGrammarHit(userText);
        }
      } catch (Exception ignored) {
        // 专用链路异常时回落到文本唤醒规则
      }
    }
    return wakeup.detect(userText);
  }

  private void send(WebSocketSession session, VoiceMessage msg) throws Exception {
    byte[] json = om.writeValueAsBytes(msg);
    session.sendMessage(new TextMessage(new String(json, StandardCharsets.UTF_8), true));
  }

  private static final class AudioInput {
    final byte[] audioBytes;
    final String audioFormat;
    final int sampleRate;

    private AudioInput(byte[] audioBytes, String audioFormat, int sampleRate) {
      this.audioBytes = audioBytes;
      this.audioFormat = audioFormat;
      this.sampleRate = sampleRate;
    }
  }

  /**
   * 兼容常见客户端直接上传 WAV 文件：
   * - 若是 WAV(PCM16/单声道)，自动提取 data chunk，返回 pcm16 + wav 内的 sampleRate
   * - 否则按原逻辑认为是裸 PCM16@16k
   */
  private static AudioInput decodeWavIfNeeded(byte[] bytes) throws IOException {
    if (bytes.length >= 12
        && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
        && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E') {
      return decodeWav(bytes);
    }
    return new AudioInput(bytes, "pcm16", 16000);
  }

  private static AudioInput decodeWav(byte[] wavBytes) throws IOException {
    try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(wavBytes))) {
      // RIFF header
      readFourCC(in); // "RIFF"
      readU32LE(in);  // file size
      readFourCC(in); // "WAVE"

      Integer sampleRate = null;
      Integer bitsPerSample = null;
      Integer channels = null;
      byte[] pcmData = null;

      while (in.available() > 0) {
        String chunkId = readFourCC(in);
        long chunkSize = readU32LE(in);
        if (chunkSize < 0 || chunkSize > Integer.MAX_VALUE) {
          throw new IOException("WAV chunk size invalid: " + chunkSize);
        }

        if ("fmt ".equals(chunkId)) {
          int audioFormat = readU16LE(in);
          int ch = readU16LE(in);
          int sr = (int) readU32LE(in);
          readU32LE(in); // byteRate
          readU16LE(in); // blockAlign
          int bps = readU16LE(in);

          int remaining = (int) chunkSize - 16;
          if (remaining > 0) {
            in.skipBytes(remaining);
          }

          // PCM = 1
          if (audioFormat != 1) {
            throw new IOException("WAV not PCM (audioFormat=" + audioFormat + ")");
          }
          sampleRate = sr;
          bitsPerSample = bps;
          channels = ch;
        } else if ("data".equals(chunkId)) {
          byte[] data = new byte[(int) chunkSize];
          in.readFully(data);
          pcmData = data;
        } else {
          // skip unknown chunk
          in.skipBytes((int) chunkSize);
        }

        // chunks are word-aligned
        if ((chunkSize & 1) == 1) {
          in.skipBytes(1);
        }
      }

      if (sampleRate == null || bitsPerSample == null || channels == null || pcmData == null) {
        throw new IOException("WAV missing required chunks");
      }
      if (channels != 1) {
        throw new IOException("WAV must be mono (channels=" + channels + ")");
      }
      if (bitsPerSample != 16) {
        throw new IOException("WAV must be 16-bit PCM (bitsPerSample=" + bitsPerSample + ")");
      }
      return new AudioInput(pcmData, "pcm16", sampleRate);
    }
  }

  private static String readFourCC(DataInputStream in) throws IOException {
    byte[] b = new byte[4];
    in.readFully(b);
    return new String(b, StandardCharsets.US_ASCII);
  }

  private static long readU32LE(DataInputStream in) throws IOException {
    int b0 = in.readUnsignedByte();
    int b1 = in.readUnsignedByte();
    int b2 = in.readUnsignedByte();
    int b3 = in.readUnsignedByte();
    return ((long) b0) | ((long) b1 << 8) | ((long) b2 << 16) | ((long) b3 << 24);
  }

  private static int readU16LE(DataInputStream in) throws IOException {
    int b0 = in.readUnsignedByte();
    int b1 = in.readUnsignedByte();
    return b0 | (b1 << 8);
  }
}

