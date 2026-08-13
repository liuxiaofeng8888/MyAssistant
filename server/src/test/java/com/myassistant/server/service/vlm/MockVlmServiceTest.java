package com.myassistant.server.service.vlm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MockVlmServiceTest {

  private MockVlmService service;

  @BeforeEach
  void setUp() {
    service = new MockVlmService();
  }

  // ==================== 空输入 ====================

  @Nested
  @DisplayName("空输入处理")
  class EmptyInputTests {

    @Test
    @DisplayName("null 上下文 → 提示未收到图片")
    void nullContext() throws Exception {
      VlmResult r = service.understand(null);
      assertEquals(VlmResult.Kind.CHAT, r.getKind());
      assertTrue(r.getAssistantText().contains("没有收到"));
    }

    @Test
    @DisplayName("空上下文（无文本无图片）→ 提示未收到图片")
    void emptyContext() throws Exception {
      VlmResult r = service.understand(new MultimodalContext());
      assertEquals(VlmResult.Kind.CHAT, r.getKind());
      assertTrue(r.getAssistantText().contains("没有收到"));
    }

    @Test
    @DisplayName("仅空白文本 → 提示未收到图片")
    void blankTextOnly() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText("   ");
      VlmResult r = service.understand(ctx);
      assertTrue(r.getAssistantText().contains("没有收到"));
    }
  }

  // ==================== 有图片输入 ====================

  @Nested
  @DisplayName("图片输入处理")
  class ImageInputTests {

    @Test
    @DisplayName("1 张图片 → 回复包含「1 张图片」")
    void singleImage() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("base64data", "image/jpeg"));
      VlmResult r = service.understand(ctx);
      assertEquals(VlmResult.Kind.CHAT, r.getKind());
      assertTrue(r.getAssistantText().contains("1 张图片"));
    }

    @Test
    @DisplayName("3 张图片 → 回复包含「3 张图片」")
    void multipleImages() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("img1", "image/jpeg"));
      ctx.addImage(new MultimodalContext.ImageInput("img2", "image/png"));
      ctx.addImage(new MultimodalContext.ImageInput("img3", "image/webp"));
      VlmResult r = service.understand(ctx);
      assertTrue(r.getAssistantText().contains("3 张图片"));
    }
  }

  // ==================== 图片 + 文本 ====================

  @Nested
  @DisplayName("图片 + 文本输入")
  class ImageAndTextTests {

    @Test
    @DisplayName("图片 + 文本 → 回复同时包含图片数和文本")
    void imageWithText() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("data", "image/jpeg"));
      ctx.setUserText("这是什么");
      VlmResult r = service.understand(ctx);
      assertTrue(r.getAssistantText().contains("1 张图片"));
      assertTrue(r.getAssistantText().contains("这是什么"));
    }

    @Test
    @DisplayName("仅文本（无图片）→ 回复包含文本但不含图片数")
    void textOnly() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.setUserText("你好");
      VlmResult r = service.understand(ctx);
      assertTrue(r.getAssistantText().contains("你好"));
      assertFalse(r.getAssistantText().contains("张图片"));
    }
  }

  // ==================== Mock 标记 ====================

  @Nested
  @DisplayName("Mock 标记")
  class MockMarkerTests {

    @Test
    @DisplayName("回复包含 Mock 标记文案")
    void containsMockMarker() throws Exception {
      MultimodalContext ctx = new MultimodalContext();
      ctx.addImage(new MultimodalContext.ImageInput("data", "image/jpeg"));
      VlmResult r = service.understand(ctx);
      assertTrue(r.getAssistantText().contains("VLM Mock 回复"));
    }
  }
}
