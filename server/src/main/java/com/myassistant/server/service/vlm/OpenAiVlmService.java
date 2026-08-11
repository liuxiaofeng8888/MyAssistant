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
 * OpenAI GPT-4V / 兼容接口（如 Azure OpenAI、本地 vLLM 等）实现。
 *
 * <p>激活条件：{@code myassistant.vlm.provider=openai}
 */
@Service
@ConditionalOnProperty(prefix = "myassistant.vlm", name = "provider", havingValue = "openai")
public class OpenAiVlmService implements VlmService {

  private final MyAssistantProperties props;
  private final ObjectMapper om;
  private final OkHttpClient client;

  public OpenAiVlmService(MyAssistantProperties props, ObjectMapper om) {
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

    // 构建 OpenAI Chat Completions 请求体
    ObjectNode body = om.createObjectNode();
    body.put("model", resolveModel(model));
    body.put("max_tokens", vlmCfg.getMaxTokens());
    body.put("temperature", vlmCfg.getTemperature());

    // messages: system + user(multimodal)
    ArrayNode messages = om.createArrayNode();
    messages.add(buildSystemMessage());
    messages.add(buildUserMessage(ctx));
    body.set("messages", messages);

    // 可选：要求返回 JSON 工具调用
    if (ctx.getMode() == MultimodalContext.Mode.TOOL) {
      body.set("tools", buildToolsDefinition());
      body.put("tool_choice", "auto");
    }

    RequestBody requestBody = RequestBody.create(
        om.writeValueAsString(body),
        MediaType.parse("application/json"));

    Request request = new Request.Builder()
        .url(apiUrl)
        .header("Authorization", "Bearer " + apiKey)
        .post(requestBody)
        .build();

    try (Response response = client.newCall(request).execute()) {
      if (!response.isSuccessful()) {
        String errBody = response.body() != null ? response.body().string() : "";
        throw new RuntimeException("OpenAI VLM 调用失败 HTTP " + response.code() + ": " + errBody);
      }

      String respBody = response.body().string();
      return parseResponse(respBody);
    }
  }

  // ---- 消息构建 ----

  private ObjectNode buildSystemMessage() {
    ObjectNode msg = om.createObjectNode();
    msg.put("role", "system");
    msg.put("content",
        "你是一个语音助手的视觉模块。根据用户提供的图片和文字，"
            + "简洁地回答问题或描述图片内容。回答控制在150字以内。");
    return msg;
  }

  private ObjectNode buildUserMessage(MultimodalContext ctx) {
    ObjectNode msg = om.createObjectNode();
    msg.put("role", "user");

    ArrayNode content = om.createArrayNode();

    // 文本部分
    if (ctx.getUserText() != null && !ctx.getUserText().isBlank()) {
      ObjectNode textPart = om.createObjectNode();
      textPart.put("type", "text");
      textPart.put("text", ctx.getUserText());
      content.add(textPart);
    }

    // 图片部分
    for (MultimodalContext.ImageInput img : ctx.getImages()) {
      ObjectNode imagePart = om.createObjectNode();
      imagePart.put("type", "image_url");
      ObjectNode imageUrl = om.createObjectNode();
      imageUrl.put("url", img.toDataUrl());
      imagePart.set("image_url", imageUrl);
      content.add(imagePart);
    }

    msg.set("content", content);
    return msg;
  }

  private ArrayNode buildToolsDefinition() {
    // 预留：传递可用工具 schema 给 VLM，实现图片→工具调用闭环
    ArrayNode tools = om.createArrayNode();
    ObjectNode tool = om.createObjectNode();
    tool.put("type", "function");
    ObjectNode func = om.createObjectNode();
    func.put("name", "describe_image");
    func.put("description", "描述图片内容");
    ObjectNode params = om.createObjectNode();
    params.put("type", "object");
    ObjectNode propsNode = om.createObjectNode();
    ObjectNode detailProp = om.createObjectNode();
    detailProp.put("type", "string");
    detailProp.put("description", "详细描述图片中看到的内容");
    propsNode.set("detail", detailProp);
    params.set("properties", propsNode);
    func.set("parameters", params);
    tool.set("function", func);
    tools.add(tool);
    return tools;
  }

  // ---- 响应解析 ----

  private VlmResult parseResponse(String respBody) throws Exception {
    JsonNode root = om.readTree(respBody);

    JsonNode choices = root.path("choices");
    if (!choices.isArray() || choices.size() == 0) {
      return VlmResult.chat("VLM 未返回有效内容。");
    }

    JsonNode first = choices.get(0);
    JsonNode message = first.path("message");

    // 优先检查工具调用
    JsonNode toolCalls = message.path("tool_calls");
    if (toolCalls.isArray() && toolCalls.size() > 0) {
      JsonNode tc = toolCalls.get(0);
      JsonNode func = tc.path("function");
      String name = func.path("name").asText("");
      String argsStr = func.path("arguments").asText("{}");
      @SuppressWarnings("unchecked")
      java.util.Map<String, Object> args = om.readValue(argsStr, java.util.Map.class);
      return VlmResult.tool(name, args, "好的，我来处理这张图片。");
    }

    // 纯文本回复
    String content = message.path("content").asText("").trim();
    if (content.isEmpty()) {
      content = "我看到了图片，但无法理解具体内容。";
    }
    return VlmResult.chat(content);
  }

  private static String resolveModel(String model) {
    return (model == null || model.isBlank()) ? "gpt-4-vision-preview" : model;
  }
}
