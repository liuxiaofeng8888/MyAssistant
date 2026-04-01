package com.myassistant.server.service.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.config.MyAssistantProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

@Service
@ConditionalOnProperty(prefix = "myassistant.asr", name = "provider", havingValue = "vosk")
public class VoskAsrService implements AsrService {
  private final ObjectMapper om;
  private final Model model;

  public VoskAsrService(MyAssistantProperties props, ObjectMapper om) {
    this.om = om;
    String modelPath = normalizePath(props.getAsr().getVoskModelPath());
    if (modelPath == null || modelPath.isBlank()) {
      throw new IllegalStateException("未配置 myassistant.asr.vosk-model-path");
    }
    Path p = resolveModelPath(modelPath);
    if (!Files.exists(p) || !Files.isDirectory(p)) {
      throw new IllegalStateException("Vosk 模型目录不存在: " + modelPath);
    }
    try {
      this.model = new Model(p.toString());
    } catch (IOException e) {
      throw new IllegalStateException("加载 Vosk 模型失败: " + modelPath, e);
    }
  }

  @Override
  public String transcribe(byte[] audioBytes, String audioFormat, int sampleRate) throws Exception {
    if (audioBytes == null || audioBytes.length == 0) {
      return "";
    }
    // 当前链路只会传 pcm16（或 wav 解包后的 pcm16）
    if (audioFormat != null && !"pcm16".equalsIgnoreCase(audioFormat) && !"raw".equalsIgnoreCase(audioFormat)) {
      throw new IllegalArgumentException("Vosk 当前仅支持 PCM16(raw) 输入，audioFormat=" + audioFormat);
    }
    if (sampleRate <= 0) {
      throw new IllegalArgumentException("sampleRate 非法: " + sampleRate);
    }

    try (Recognizer rec = new Recognizer(model, sampleRate)) {
      rec.setWords(true);
      rec.acceptWaveForm(audioBytes, audioBytes.length);
      String json = rec.getFinalResult();
      JsonNode root = om.readTree(json);
      return root.path("text").asText("").trim();
    }
  }

  private static String normalizePath(String s) {
    if (s == null) return null;
    String t = s.trim();
    if (t.length() >= 2) {
      if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
        return t.substring(1, t.length() - 1).trim();
      }
    }
    return t;
  }

  private static Path resolveModelPath(String modelPath) {
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

