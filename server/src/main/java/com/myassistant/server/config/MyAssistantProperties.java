package com.myassistant.server.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "myassistant")
public class MyAssistantProperties {
  private final Auth auth = new Auth();
  private final Asr asr = new Asr();
  private final Iflytek iflytek = new Iflytek();
  private final Wakeup wakeup = new Wakeup();

  public Auth getAuth() {
    return auth;
  }

  public Asr getAsr() {
    return asr;
  }

  public Iflytek getIflytek() {
    return iflytek;
  }

  public Wakeup getWakeup() {
    return wakeup;
  }

  public static class Asr {
    /**
     * mock | iflytek | vosk
     */
    private String provider = "mock";
    /**
     * Vosk 模型目录路径（例如：/opt/models/vosk-model-cn-0.22）
     */
    private String voskModelPath = "";

    public String getProvider() {
      return provider;
    }

    public void setProvider(String provider) {
      this.provider = provider;
    }

    public String getVoskModelPath() {
      return voskModelPath;
    }

    public void setVoskModelPath(String voskModelPath) {
      this.voskModelPath = voskModelPath;
    }
  }

  public static class Auth {
    private boolean enabled = false;
    private String staticBearerToken = "dev-token";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getStaticBearerToken() {
      return staticBearerToken;
    }

    public void setStaticBearerToken(String staticBearerToken) {
      this.staticBearerToken = staticBearerToken;
    }
  }

  public static class Iflytek {
    private boolean enabled = false;
    private String appId = "";
    private String apiKey = "";
    private String apiSecret = "";
    private String asrWsUrl = "";
    private String sparkWsUrl = "";

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getAppId() {
      return appId;
    }

    public void setAppId(String appId) {
      this.appId = appId;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String getApiSecret() {
      return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
      this.apiSecret = apiSecret;
    }

    public String getAsrWsUrl() {
      return asrWsUrl;
    }

    public void setAsrWsUrl(String asrWsUrl) {
      this.asrWsUrl = asrWsUrl;
    }

    public String getSparkWsUrl() {
      return sparkWsUrl;
    }

    public void setSparkWsUrl(String sparkWsUrl) {
      this.sparkWsUrl = sparkWsUrl;
    }
  }

  public static class Wakeup {
    /**
     * 是否启用唤醒词。默认启用。
     */
    private boolean enabled = true;
    /**
     * 唤醒词，默认：嗨 小奇（匹配时会忽略空格/常见标点）。
     */
    private String wakeWord = "嗨 小奇";
    /**
     * ASR 常把唤醒词误识成别的短语；此处列出的别名仅在识别结果<strong>以该别名开头</strong>时视为已唤醒（降低误触发）。
     */
    private List<String> wakeAliases = new ArrayList<>();
    /**
     * 是否在 Vosk 下启用「grammar 唤醒专用链路」（与开放域 transcribe 并行）。关闭后仅走文本规则匹配。
     */
    private boolean dedicatedPath = true;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getWakeWord() {
      return wakeWord;
    }

    public void setWakeWord(String wakeWord) {
      this.wakeWord = wakeWord;
    }

    public List<String> getWakeAliases() {
      return wakeAliases;
    }

    public void setWakeAliases(List<String> wakeAliases) {
      this.wakeAliases = wakeAliases == null ? new ArrayList<>() : wakeAliases;
    }

    public boolean isDedicatedPath() {
      return dedicatedPath;
    }

    public void setDedicatedPath(boolean dedicatedPath) {
      this.dedicatedPath = dedicatedPath;
    }
  }
}

