package com.myassistant.android.multimodal;

import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.myassistant.android.VoiceWsClient;
import org.json.JSONObject;

/**
 * 多模态 WebSocket 客户端扩展。
 * 在已有 {@link VoiceWsClient} 基础上，增加图片发送和多模态查询能力。
 *
 * <p>用法：
 * <pre>{@code
 *   MultimodalWsClient multi = new MultimodalWsClient(voiceWsClient);
 *   // 发送一张图片
 *   multi.sendImage(b64, "image/jpeg");
 *   // 发起查询（文本可选）
 *   multi.sendImageQuery("这张图片里有什么？");
 *   // 或者描述模式
 *   multi.sendImageQuery(null, "describe");
 * }</pre>
 */
public final class MultimodalWsClient {

  private final VoiceWsClient voiceWsClient;

  public MultimodalWsClient(@NonNull VoiceWsClient voiceWsClient) {
    this.voiceWsClient = voiceWsClient;
  }

  /**
   * 发送一张 Base64 编码的图片到服务端。
   * 可多次调用以累积多张图片。
   */
  public void sendImage(@NonNull String dataB64, @NonNull String mimeType) {
    JSONObject j = new JSONObject();
    try {
      j.put("type", "image");
      j.put("data_b64", dataB64);
      j.put("mime_type", mimeType);
    } catch (Exception ignored) {}
    sendRaw(j.toString());
  }

  /**
   * 发起多模态查询。
   *
   * @param userText 用户指令文本（可为 null，仅图片描述）
   * @param mode     模式：describe / qa / tool（可为 null，默认 qa）
   */
  public void sendImageQuery(@Nullable String userText, @Nullable String mode) {
    JSONObject j = new JSONObject();
    try {
      j.put("type", "image_query");
      if (userText != null && !userText.isEmpty()) {
        j.put("text", userText);
      }
      if (mode != null && !mode.isEmpty()) {
        j.put("vlm_mode", mode);
      }
    } catch (Exception ignored) {}
    sendRaw(j.toString());
  }

  /** 便捷方法：QA 模式查询 */
  public void sendImageQuery(@Nullable String userText) {
    sendImageQuery(userText, "qa");
  }

  /** 便捷方法：仅描述图片 */
  public void sendDescribeImage() {
    sendImageQuery(null, "describe");
  }

  /**
   * 一步完成：发送单张图片 + 立即发起查询。
   */
  public void sendImageAndQuery(@NonNull String dataB64, @NonNull String mimeType,
                                @Nullable String userText) {
    sendImage(dataB64, mimeType);
    sendImageQuery(userText);
  }

  // ---- 内部 ----

  /**
   * 通过底层 VoiceWsClient 的 WebSocket 直接发送原始 JSON。
   * 注意：需要 VoiceWsClient 暴露 sendRaw 方法，或在此处持有 WebSocket 引用。
   * 框架模式：预留接口，实际集成时需在 VoiceWsClient 中增加 sendRaw(String) 方法。
   *
   * <p>TODO: 在 VoiceWsClient 中添加：
   * <pre>{@code
   *   public void sendRaw(String json) {
   *     if (ws != null) ws.send(json);
   *   }
   * }</pre>
   */
  private void sendRaw(String json) {
    // 委托 VoiceWsClient 发送
    // 实际集成需要 VoiceWsClient 暴露 sendRaw 或直接操作内部 WebSocket
    voiceWsClient.sendRaw(json);
  }
}
