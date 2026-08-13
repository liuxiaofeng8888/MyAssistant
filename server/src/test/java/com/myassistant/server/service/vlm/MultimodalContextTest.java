package com.myassistant.server.service.vlm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultimodalContextTest {

  // ==================== isEmpty ====================

  @Nested
  @DisplayName("isEmpty 判空")
  class IsEmptyTests {

    @Test
    @DisplayName("新建上下文 → isEmpty = true")
    void newContextIsEmpty() {
      MultimodalContext ctx = new MultimodalContext();
      assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("仅设置空白文本 → isEmpty = true")
    void blankTextOnly() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText("   ");
      assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("仅设置 null 文本 → isEmpty = true")
    void nullTextOnly() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText(null);
      assertTrue(ctx.isEmpty());
    }

    @Test
    @DisplayName("有文本 → isEmpty = false")
    void withText() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText("这是什么");
      assertFalse(ctx.isEmpty());
    }

    @Test
    @DisplayName("有图片 → isEmpty = false")
    void withImage() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("abc123", "image/jpeg"));
      assertFalse(ctx.isEmpty());
    }

    @Test
    @DisplayName("文本 + 图片 → isEmpty = false")
    void withTextAndImage() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText("描述一下");
      ctx.addImage(new MultimodalContext.ImageInput("abc123", "image/png"));
      assertFalse(ctx.isEmpty());
    }
  }

  // ==================== Mode ====================

  @Nested
  @DisplayName("对话模式")
  class ModeTests {

    @Test
    @DisplayName("默认模式 → QA")
    void defaultMode() {
      MultimodalContext ctx = new MultimodalContext();
      assertEquals(MultimodalContext.Mode.QA, ctx.getMode());
    }

    @Test
    @DisplayName("切换为 DESCRIBE")
    void switchToDescribe() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setMode(MultimodalContext.Mode.DESCRIBE);
      assertEquals(MultimodalContext.Mode.DESCRIBE, ctx.getMode());
    }

    @Test
    @DisplayName("切换为 TOOL")
    void switchToTool() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setMode(MultimodalContext.Mode.TOOL);
      assertEquals(MultimodalContext.Mode.TOOL, ctx.getMode());
    }
  }

  // ==================== 图片管理 ====================

  @Nested
  @DisplayName("图片管理")
  class ImageManagementTests {

    @Test
    @DisplayName("添加多张图片 → 列表正确累积")
    void addMultipleImages() {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("img1", "image/jpeg"));
      ctx.addImage(new MultimodalContext.ImageInput("img2", "image/png"));
      assertEquals(2, ctx.getImages().size());
    }

    @Test
    @DisplayName("图片列表初始为空")
    void initialImagesEmpty() {
      MultimodalContext ctx = new MultimodalContext();
      assertTrue(ctx.getImages().isEmpty());
    }
  }

  // ==================== ImageInput ====================

  @Nested
  @DisplayName("ImageInput 内嵌类")
  class ImageInputTests {

    @Test
    @DisplayName("无参构造 → mimeType 默认 image/jpeg")
    void defaultMimeType() {
      MultimodalContext.ImageInput img = new MultimodalContext.ImageInput();
      assertEquals("image/jpeg", img.getMimeType());
    }

    @Test
    @DisplayName("带参构造 → 正确赋值")
    void parameterizedConstructor() {
      MultimodalContext.ImageInput img = new MultimodalContext.ImageInput("base64data", "image/png");
      assertEquals("base64data", img.getDataB64());
      assertEquals("image/png", img.getMimeType());
    }

    @Test
    @DisplayName("toDataUrl → 返回正确 data URL")
    void toDataUrl() {
      MultimodalContext.ImageInput img = new MultimodalContext.ImageInput("abc123", "image/jpeg");
      assertEquals("data:image/jpeg;base64,abc123", img.toDataUrl());
    }

    @Test
    @DisplayName("toDataUrl → png 类型")
    void toDataUrlPng() {
      MultimodalContext.ImageInput img = new MultimodalContext.ImageInput("xyz", "image/png");
      assertEquals("data:image/png;base64,xyz", img.toDataUrl());
    }

    @Test
    @DisplayName("setter 修改后 toDataUrl 反映新值")
    void setterReflectInDataUrl() {
      MultimodalContext.ImageInput img = new MultimodalContext.ImageInput();
      img.setDataB64("newdata");
      img.setMimeType("image/webp");
      assertEquals("data:image/webp;base64,newdata", img.toDataUrl());
    }
  }
}
