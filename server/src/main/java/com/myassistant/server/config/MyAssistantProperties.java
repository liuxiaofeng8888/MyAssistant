package com.myassistant.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "myassistant")
public class MyAssistantProperties {
  private final Auth auth = new Auth();
  private final Iflytek iflytek = new Iflytek();

  public Auth getAuth() {
    return auth;
  }

  public Iflytek getIflytek() {
    return iflytek;
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
}

