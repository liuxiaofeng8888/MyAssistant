package com.myassistant.server.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vosk.Model;

@Configuration
@ConditionalOnProperty(prefix = "myassistant.asr", name = "provider", havingValue = "vosk")
public class VoskModelConfiguration {

  @Bean(destroyMethod = "close")
  public Model voskModel(MyAssistantProperties props) throws IOException {
    String modelPath = normalizePath(props.getAsr().getVoskModelPath());
    if (modelPath == null || modelPath.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.asr.vosk-model-path");
    }
    Path p = resolveModelPath(modelPath);
    if (!Files.exists(p) || !Files.isDirectory(p)) {
      throw new IllegalStateException("Vosk 模型目录不存在: " + modelPath);
    }
    return new Model(p.toString());
  }

  static String normalizePath(String s) {
    if (s == null) return null;
    String t = s.trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        return t.substring(1, t.length() - 1).trim();
      }
    }
    return t;
  }

  static Path resolveModelPath(String modelPath) {
    Path p = Path.of(modelPath);
    if (p.isAbsolute()) {
      return p.normalize();
    }
    String userDir = System.getProperty("user.dir", "");
    if (!userDir.isBlank()) {
      return Path.of(userDir).resolve(p).normalize();
    }
    return p.normalize();
  }
}
