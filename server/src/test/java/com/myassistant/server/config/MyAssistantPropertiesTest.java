package com.myassistant.server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MyAssistantPropertiesTest {

  // ==================== 顶层默认值 ====================

  @Nested
  @DisplayName("顶层对象默认初始化")
  class TopLevelDefaultsTests {

    @Test
    @DisplayName("各子配置对象不为 null")
    void subObjectsNotNull() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertNotNull(props.getAuth());
      assertNotNull(props.getAsr());
      assertNotNull(props.getIflytek());
      assertNotNull(props.getWakeup());
      assertNotNull(props.getVlm());
    }
  }

  // ==================== Auth 默认值 ====================

  @Nested
  @DisplayName("Auth 配置默认值")
  class AuthDefaultsTests {

    @Test
    @DisplayName("enabled 默认 false")
    void authDisabledByDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertFalse(props.getAuth().isEnabled());
    }

    @Test
    @DisplayName("staticBearerToken 默认 dev-token")
    void defaultToken() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("dev-token", props.getAuth().getStaticBearerToken());
    }

    @Test
    @DisplayName("setter 生效")
    void authSetters() {
      MyAssistantProperties props = new MyAssistantProperties();
      props.getAuth().setEnabled(true);
      props.getAuth().setStaticBearerToken("my-token");
      assertTrue(props.getAuth().isEnabled());
      assertEquals("my-token", props.getAuth().getStaticBearerToken());
    }
  }

  // ==================== Asr 默认值 ====================

  @Nested
  @DisplayName("Asr 配置默认值")
  class AsrDefaultsTests {

    @Test
    @DisplayName("provider 默认 mock")
    void asrProviderDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("mock", props.getAsr().getProvider());
    }

    @Test
    @DisplayName("voskModelPath 默认空字符串")
    void voskModelPathDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("", props.getAsr().getVoskModelPath());
    }
  }

  // ==================== Iflytek 默认值 ====================

  @Nested
  @DisplayName("Iflytek 配置默认值")
  class IflytekDefaultsTests {

    @Test
    @DisplayName("enabled 默认 false")
    void iflytekDisabled() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertFalse(props.getIflytek().isEnabled());
    }

    @Test
    @DisplayName("appId / apiKey / apiSecret 默认空")
    void iflytekCredentialsEmpty() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("", props.getIflytek().getAppId());
      assertEquals("", props.getIflytek().getApiKey());
      assertEquals("", props.getIflytek().getApiSecret());
    }

    @Test
    @DisplayName("URL 默认空")
    void iflytekUrlsEmpty() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("", props.getIflytek().getAsrWsUrl());
      assertEquals("", props.getIflytek().getSparkWsUrl());
    }
  }

  // ==================== Wakeup 默认值 ====================

  @Nested
  @DisplayName("Wakeup 配置默认值")
  class WakeupDefaultsTests {

    @Test
    @DisplayName("enabled 默认 true")
    void wakeupEnabled() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertTrue(props.getWakeup().isEnabled());
    }

    @Test
    @DisplayName("wakeWord 默认「嗨 小奇」")
    void defaultWakeWord() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("嗨 小奇", props.getWakeup().getWakeWord());
    }

    @Test
    @DisplayName("wakeAliases 默认空列表")
    void defaultAliases() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertTrue(props.getWakeup().getWakeAliases().isEmpty());
    }

    @Test
    @DisplayName("dedicatedPath 默认 true")
    void defaultDedicatedPath() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertTrue(props.getWakeup().isDedicatedPath());
    }

    @Test
    @DisplayName("setAliases(null) → 空列表而非 null")
    void nullAliasesesBecomesEmptyList() {
      MyAssistantProperties props = new MyAssistantProperties();
      props.getWakeup().setWakeAliases(null);
      assertNotNull(props.getWakeup().getWakeAliases());
      assertTrue(props.getWakeup().getWakeAliases().isEmpty());
    }
  }

  // ==================== Vlm 默认值 ====================

  @Nested
  @DisplayName("Vlm 配置默认值")
  class VlmDefaultsTests {

    @Test
    @DisplayName("provider 默认 mock")
    void vlmProviderDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("mock", props.getVlm().getProvider());
    }

    @Test
    @DisplayName("apiUrl / apiKey 默认空")
    void vlmApiDefaults() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("", props.getVlm().getApiUrl());
      assertEquals("", props.getVlm().getApiKey());
    }

    @Test
    @DisplayName("model 默认空")
    void vlmModelDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals("", props.getVlm().getModel());
    }

    @Test
    @DisplayName("maxTokens 默认 1024")
    void vlmMaxTokensDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals(1024, props.getVlm().getMaxTokens());
    }

    @Test
    @DisplayName("temperature 默认 0.7")
    void vlmTemperatureDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals(0.7, props.getVlm().getTemperature(), 0.001);
    }

    @Test
    @DisplayName("maxImageSize 默认 2048")
    void vlmMaxImageSizeDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals(2048, props.getVlm().getMaxImageSize());
    }

    @Test
    @DisplayName("maxImages 默认 5")
    void vlmMaxImagesDefault() {
      MyAssistantProperties props = new MyAssistantProperties();
      assertEquals(5, props.getVlm().getMaxImages());
    }

    @Test
    @DisplayName("setter 生效")
    void vlmSetters() {
      MyAssistantProperties props = new MyAssistantProperties();
      props.getVlm().setProvider("openai");
      props.getVlm().setApiUrl("https://api.example.com");
      props.getVlm().setApiKey("sk-xxx");
      props.getVlm().setModel("gpt-4v");
      props.getVlm().setMaxTokens(2048);
      props.getVlm().setTemperature(0.5);
      props.getVlm().setMaxImageSize(1024);
      props.getVlm().setMaxImages(3);

      assertEquals("openai", props.getVlm().getProvider());
      assertEquals("https://api.example.com", props.getVlm().getApiUrl());
      assertEquals("sk-xxx", props.getVlm().getApiKey());
      assertEquals("gpt-4v", props.getVlm().getModel());
      assertEquals(2048, props.getVlm().getMaxTokens());
      assertEquals(0.5, props.getVlm().getTemperature(), 0.001);
      assertEquals(1024, props.getVlm().getMaxImageSize());
      assertEquals(3, props.getVlm().getMaxImages());
    }
  }
}
