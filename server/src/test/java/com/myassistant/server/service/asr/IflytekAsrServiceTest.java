package com.myassistant.server.service.asr;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IflytekAsrServiceTest {

  // ==================== buildAuthUrl ====================

  @Nested
  @DisplayName("buildAuthUrl 鉴权 URL 构建")
  class BuildAuthUrlTests {

    @Test
    @DisplayName("正常参数 → 返回带鉴权参数的 URL")
    void normalUrl() throws Exception {
      String url = IflytekAsrService.buildAuthUrl(
          "wss://iat.cn-huabei-1.xf-yun.com/v1",
          "test-api-key",
          "test-api-secret"
      );
      assertNotNull(url);
      assertTrue(url.startsWith("wss://iat.cn-huabei-1.xf-yun.com/v1?"));
      assertTrue(url.contains("authorization="));
      assertTrue(url.contains("date="));
      assertTrue(url.contains("host="));
    }

    @Test
    @DisplayName("无路径的 URL → 默认 /")
    void noPath() throws Exception {
      String url = IflytekAsrService.buildAuthUrl(
          "wss://example.com",
          "key",
          "secret"
      );
      assertTrue(url.startsWith("wss://example.com/?"));
    }

    @Test
    @DisplayName("空 wsUrl → 抛出异常")
    void blankUrl() {
      assertThrows(IllegalStateException.class, () ->
          IflytekAsrService.buildAuthUrl("", "key", "secret")
      );
    }

    @Test
    @DisplayName("空 apiKey → 抛出异常")
    void blankApiKey() {
      assertThrows(IllegalStateException.class, () ->
          IflytekAsrService.buildAuthUrl("wss://example.com/v1", "", "secret")
      );
    }

    @Test
    @DisplayName("空 apiSecret → 抛出异常")
    void blankApiSecret() {
      assertThrows(IllegalStateException.class, () ->
          IflytekAsrService.buildAuthUrl("wss://example.com/v1", "key", "")
      );
    }

    @Test
    @DisplayName("null wsUrl → 抛出异常")
    void nullUrl() {
      assertThrows(IllegalStateException.class, () ->
          IflytekAsrService.buildAuthUrl(null, "key", "secret")
      );
    }

    @Test
    @DisplayName("URL 编码正确处理特殊字符")
    void urlEncoding() throws Exception {
      String url = IflytekAsrService.buildAuthUrl(
          "wss://iat.xf-yun.com/v1",
          "api-key+special",
          "secret/special"
      );
      assertFalse(url.contains(" ")); // 不应有未编码空格
      assertTrue(url.contains("authorization="));
    }
  }
}
