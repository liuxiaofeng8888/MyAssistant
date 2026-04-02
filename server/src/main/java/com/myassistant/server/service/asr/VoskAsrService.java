package com.myassistant.server.service.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

@Service
@ConditionalOnProperty(prefix = "myassistant.asr", name = "provider", havingValue = "vosk")
public class VoskAsrService implements AsrService {
  private final ObjectMapper om;
  private final Model model;

  public VoskAsrService(Model model, ObjectMapper om) {
    this.model = model;
    this.om = om;
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
}

