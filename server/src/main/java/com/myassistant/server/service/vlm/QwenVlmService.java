package com.myassistant.server.service.vlm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.myassistant.server.config.MyAssistantProperties;
import okhttp3.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 通义千问 VL（qwen-vl-plus / qwen-vl-max）实现。
 *
 * <p>API 文档：https://help.aliyun.com/zh/dashscope/
 * <p>激活条件：{@code myassistant.vlm.provider=qwen}
 */
@Service
@ConditionalOnProperty(prefix = "myassistant.vlm", name = "provider", havingValue = "qwen")
public class QwenVlmService implements VlmService {

  private final MyAssistantProperties props;
  private final ObjectMapper om;
  private final OkHttpClient client;

  public QwenVlmService(MyAssistantProperties props, ObjectMapper om) {
    this.props = props;
    this.om = om;
    this.client = new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(Duration.ofSeconds(90))
        .build();
  }

  @Override
  public VlmResult understand(MultimodalContext ctx) throws Exception {
    if (ctx == null || ctx.isEmpty()) {
      return VlmResult.chat("没有收到图片或文字，请再说一次。");
    }

    MyAssistantProperties.Vlm vlmCfg = props.getVlm();
    String apiUrl = vlmCfg.getApiUrl();
    String apiKey = vlmCfg.getApiKey();
    String model = vlmCfg.getModel();

    if (apiUrl == null || apiUrl.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.vlm.api-url");
    }
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.vlm.api-key");
    }

    // 通义千问 DashScope API 请求格式
    ObjectNode body = om.createObjectNode();
    body.put("model", resolveModel(model));

    ObjectNode input = om.createObjectNode();
    ArrayNode messages = om.createArrayNode();

    // system prompt
    ObjectNode sysMsg = om.createObjectNode();
    sysMsg.put("role", "system");
    ArrayNode sysContent = om.createArrayNode();
    ObjectNode sysText = om.createObjectNode();
    sysText.put("text", "你是一个语音助手的视觉模块。根据图片和文字简洁回答。回答控制在150字以内。");
    sysContent.add(sysText);
    sysMsg.set("content", sysContent);
    messages.add(sysMsg);

    // user message (multimodal)
    ObjectNode userMsg = om.createObjectNode();
    userMsg.put("role", "user");
    ArrayNode userContent = om.createArrayNode();

    // 图片
    for (MultimodalContext.ImageInput img : ctx.getImages()) {
      ObjectNode imagePart = om.createObjectNode();
      imagePart.put("image", img.toDataUrl());
      userContent.add(imagePart);
    }

    // 文本
    if (ctx.getUserText() != null && !ctx.getUserText().isBlank()) {
      ObjectNode textPart = om.createObjectNode();
      textPart.put("text", ctx.getUserText());
      userContent.add(textPart);
    }

    userMsg.set("content", userContent);
    messages.add(userMsg);

    input.set("messages", messages);
    body.set("input", input);

    ObjectNode params = om.createObjectNode();
    params.put("max_tokens", vlmCfg.getMaxTokens());
    body.set("parameters", params);

    RequestBody requestBody = RequestBody.create(
        om.writeValueAsString(body),
        MediaType.parse("application/json"));

    Request request = new Request.Builder()
        .url(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .post(requestBody)
        .build();

    try (Response response = client.newCall(request).execute()) {
      String respBody = response.body() != null ? response.body().string() : "";
      if (!response.isSuccessful()) {
        throw new RuntimeException("Qwen VLM 调用失败 HTTP " + response.code() + ": " + respBody);
      }
      return parseResponse(respBody);
    }
  }

  private VlmResult parseResponse(String respBody) throws Exception {
    JsonNode root = om.readTree(respBody);

    // DashScope 格式: output.choices[0].message.content[0].text
    JsonNode output = root.path("output");
    JsonNode choices = output.path("choices");
    if (!choices.isArray() || choices.size() == 0) {
      return VlmResult.chat("VLM 未返回有效内容。");
    }

    JsonNode message = choices.get(0).path("message");
    JsonNode content = message.path("content");

    StringBuilder sb = new StringBuilder();
    if (content.isArray()) {
      for (JsonNode part : content) {
        String t = part.path("text").asText("");
        if (!t.isEmpty()) sb.append(t);
      }
    } else {
      sb.append(content.asText(""));
    }

    String text = sb.toString().trim();
    if (text.isEmpty()) {
      text = "我看到了图片，但无法理解具体内容。";
    }
    return VlmResult.chat(text);
  }

  private static String resolveModel(String model) {
    return (model == null || model.isBlank()) ? "qwen-vl-plus" : model;
  }
}
