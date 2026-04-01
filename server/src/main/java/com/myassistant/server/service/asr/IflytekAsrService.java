package com.myassistant.server.service.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.config.MyAssistantProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "myassistant.iflytek", name = "enabled", havingValue = "true")
public class IflytekAsrService implements AsrService {
  private final MyAssistantProperties props;
  private final ObjectMapper om;
  private final OkHttpClient client;

  public IflytekAsrService(MyAssistantProperties props, ObjectMapper om) {
    this.props = props;
    this.om = om;
    this.client = new OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .build();
  }

  @Override
  public String transcribe(byte[] audioBytes, String audioFormat, int sampleRate) throws Exception {
    if (audioBytes == null || audioBytes.length == 0) {
      return "";
    }
    if (sampleRate != 16000 && sampleRate != 8000) {
      throw new IllegalArgumentException("讯飞 ASR 仅支持 16000/8000 采样率，当前: " + sampleRate);
    }
    // 当前实现按文档使用 raw(PCM)；若后续上传 mp3，则改 encoding=lame
    if (audioFormat != null && !"pcm16".equalsIgnoreCase(audioFormat) && !"raw".equalsIgnoreCase(audioFormat)) {
      throw new IllegalArgumentException("当前仅实现 PCM(raw) 上传，audioFormat: " + audioFormat);
    }

    String url = buildAuthUrl(props.getIflytek().getAsrWsUrl(),
        props.getIflytek().getApiKey(),
        props.getIflytek().getApiSecret());

    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<String> finalText = new AtomicReference<>("");
    AtomicReference<Exception> error = new AtomicReference<>(null);

    Request req = new Request.Builder().url(url).build();
    WebSocket ws = client.newWebSocket(req, new WebSocketListener() {
      private final StringBuilder sb = new StringBuilder();
      private volatile boolean closed = false;

      @Override
      public void onOpen(WebSocket webSocket, Response response) {
        try {
          sendAudioFrames(webSocket, audioBytes, sampleRate);
        } catch (Exception e) {
          error.set(e);
          if (!closed) {
            closed = true;
            webSocket.close(1000, "client_error");
          }
          done.countDown();
        }
      }

      @Override
      public void onMessage(WebSocket webSocket, String text) {
        try {
          JsonNode root = om.readTree(text);
          JsonNode header = root.path("header");
          int code = header.path("code").asInt(0);
          int status = header.path("status").asInt(-1);
          if (code != 0) {
            String msg = header.path("message").asText("iflytek_error");
            error.set(new RuntimeException("讯飞ASR错误 code=" + code + " message=" + msg));
            if (!closed) {
              closed = true;
              webSocket.close(1000, "server_error");
            }
            done.countDown();
            return;
          }

          JsonNode payload = root.path("payload");
          JsonNode result = payload.path("result");
          String resultTextB64 = result.path("text").asText(null);
          if (resultTextB64 != null && !resultTextB64.isBlank()) {
            String decoded = new String(Base64.getDecoder().decode(resultTextB64), StandardCharsets.UTF_8);
            appendWsWords(decoded, sb);
          }

          if (status == 2) {
            finalText.set(sb.toString().trim());
            if (!closed) {
              closed = true;
              webSocket.close(1000, "done");
            }
            done.countDown();
          }
        } catch (Exception e) {
          error.set(e);
          if (!closed) {
            closed = true;
            webSocket.close(1000, "parse_error");
          }
          done.countDown();
        }
      }

      @Override
      public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        error.set(new RuntimeException(t));
        done.countDown();
      }

      @Override
      public void onClosed(WebSocket webSocket, int code, String reason) {
        done.countDown();
      }
    });

    boolean ok = done.await(30, TimeUnit.SECONDS);
    // 避免资源悬挂
    ws.cancel();

    if (!ok) {
      throw new RuntimeException("讯飞ASR超时");
    }
    if (error.get() != null) {
      throw error.get();
    }
    return finalText.get();
  }

  private void sendAudioFrames(WebSocket ws, byte[] audioBytes, int sampleRate) throws Exception {
    String appId = props.getIflytek().getAppId();
    if (appId == null || appId.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.iflytek.app-id");
    }

    int frameSize = 1280; // 参考文档建议
    int seq = 1;

    int offset = 0;
    boolean first = true;
    while (offset < audioBytes.length) {
      int remaining = audioBytes.length - offset;
      int len = Math.min(frameSize, remaining);
      byte[] chunk = new byte[len];
      System.arraycopy(audioBytes, offset, chunk, 0, len);
      offset += len;

      int status = offset >= audioBytes.length ? 2 : (first ? 0 : 1);
      String audioB64 = Base64.getEncoder().encodeToString(chunk);

      String json = buildRequestFrame(appId, status, sampleRate, seq, audioB64, first);
      ws.send(json);

      first = false;
      seq++;

      // 模拟实时流式发送间隔，避免过快导致耗时增加（可按需调小/改为动态）
      Thread.sleep(40);
    }
  }

  private String buildRequestFrame(String appId, int status, int sampleRate, int seq, String audioB64, boolean includeParameter)
      throws Exception {
    // 第一帧包含 parameter；中间/末尾帧按文档可省略 parameter
    StringBuilder sb = new StringBuilder();
    sb.append("{\"header\":{")
        .append("\"app_id\":\"").append(escape(appId)).append("\",")
        .append("\"status\":").append(status)
        .append("}");

    if (includeParameter) {
      sb.append(",\"parameter\":{")
          .append("\"iat\":{")
          .append("\"domain\":\"slm\",")
          .append("\"language\":\"mul_cn\",")
          .append("\"accent\":\"mandarin\",")
          .append("\"eos\":6000,")
          .append("\"result\":{")
          .append("\"encoding\":\"utf8\",")
          .append("\"compress\":\"raw\",")
          .append("\"format\":\"json\"")
          .append("}")
          .append("}")
          .append("}");
    }

    sb.append(",\"payload\":{")
        .append("\"audio\":{")
        .append("\"encoding\":\"raw\",")
        .append("\"sample_rate\":").append(sampleRate).append(",")
        .append("\"channels\":1,")
        .append("\"bit_depth\":16,")
        .append("\"seq\":").append(seq).append(",")
        .append("\"status\":").append(status).append(",")
        .append("\"audio\":\"").append(audioB64).append("\"")
        .append("}")
        .append("}")
        .append("}");
    return sb.toString();
  }

  /**
   * 解析 result.text(base64) 解码后的 JSON，并把 ws[].cw[].w 拼接为句子。
   */
  private void appendWsWords(String decodedJson, StringBuilder out) throws Exception {
    JsonNode r = om.readTree(decodedJson);
    JsonNode ws = r.path("ws");
    if (!ws.isArray()) return;
    for (JsonNode wseg : ws) {
      JsonNode cw = wseg.path("cw");
      if (!cw.isArray()) continue;
      // 取第一个候选词
      JsonNode first = cw.size() > 0 ? cw.get(0) : null;
      if (first == null) continue;
      String w = first.path("w").asText("");
      if (!w.isEmpty()) {
        out.append(w);
      }
    }
  }

  /**
   * 通用鉴权：authorization/date/host，拼到 ws url query。
   */
  static String buildAuthUrl(String wsUrl, String apiKey, String apiSecret) throws Exception {
    if (wsUrl == null || wsUrl.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.iflytek.asr-ws-url");
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.iflytek.api-key");
    }
    if (apiSecret == null || apiSecret.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.iflytek.api-secret");
    }

    URI uri = URI.create(wsUrl);
    String host = uri.getHost();
    String path = uri.getPath();
    if (path == null || path.isBlank()) {
      path = "/";
    }

    String date = ZonedDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME);
    String requestLine = "GET " + path + " HTTP/1.1";
    String signatureOrigin = "host: " + host + "\n" + "date: " + date + "\n" + requestLine;

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    byte[] signBytes = mac.doFinal(signatureOrigin.getBytes(StandardCharsets.UTF_8));
    String signature = Base64.getEncoder().encodeToString(signBytes);

    String authorizationOrigin = "api_key=\"" + apiKey + "\", algorithm=\"hmac-sha256\", headers=\"host date request-line\", signature=\"" + signature + "\"";
    String authorization = Base64.getEncoder().encodeToString(authorizationOrigin.getBytes(StandardCharsets.UTF_8));

    String query = "authorization=" + urlEncode(authorization)
        + "&date=" + urlEncode(date)
        + "&host=" + urlEncode(host);

    String base = uri.getScheme() + "://" + host + path;
    return base + "?" + query;
  }

  private static String urlEncode(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String escape(String s) {
    return s.replace("\\", "\\\\").replace("\"", "\\\"");
  }
}

