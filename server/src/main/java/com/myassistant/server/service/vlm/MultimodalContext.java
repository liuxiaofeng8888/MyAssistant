package com.myassistant.server.service.vlm;

import java.util.ArrayList;
import java.util.List;

/**
 * 多模态输入上下文：文本 + 若干图片。
 * 一条请求可以包含 0..N 张图片，以及可选的文本指令。
 */
public class MultimodalContext {
  /** 用户文本指令（可能为空，仅发图片） */
  private String userText;

  /** Base64 编码的图片列表 */
  private final List<ImageInput> images = new ArrayList<>();

  /** 对话模式：描述图片 / 问答 / 工具调用（预留） */
  private Mode mode = Mode.QA;

  public enum Mode {
    /** 描述/理解图片内容 */
    DESCRIBE,
    /** 基于图片回答问题 */
    QA,
    /** 图片 + 语音指令 → 工具调用（如「识别这张发票并记一笔账」） */
    TOOL
  }

  public String getUserText() {
    return userText;
  }

  public void setUserText(String userText) {
    this.userText = userText;
  }

  public List<ImageInput> getImages() {
    return images;
  }

  public void addImage(ImageInput image) {
    this.images.add(image);
  }

  public Mode getMode() {
    return mode;
  }

  public void setMode(Mode mode) {
    this.mode = mode;
  }

  public boolean isEmpty() {
    return (userText == null || userText.isBlank()) && images.isEmpty();
  }

  // ---- 内嵌类 ----

  /**
   * 单张图片输入。
   */
  public static class ImageInput {
    /** Base64 编码的图片数据 */
    private String dataB64;
    /** MIME 类型，默认 image/jpeg */
    private String mimeType = "image/jpeg";

    public ImageInput() {}

    public ImageInput(String dataB64, String mimeType) {
      this.dataB64 = dataB64;
      this.mimeType = mimeType;
    }

    public String getDataB64() {
      return dataB64;
    }

    public void setDataB64(String dataB64) {
      this.dataB64 = dataB64;
    }

    public String getMimeType() {
      return mimeType;
    }

    public void setMimeType(String mimeType) {
      this.mimeType = mimeType;
    }

    /** @return data URL 形式，如 data:image/jpeg;base64,xxx */
    public String toDataUrl() {
      return "data:" + mimeType + ";base64," + dataB64;
    }
  }
}
