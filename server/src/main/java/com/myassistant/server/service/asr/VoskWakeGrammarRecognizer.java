package com.myassistant.server.service.asr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.config.MyAssistantProperties;
import com.myassistant.server.service.wakeup.WakeGrammarRecognizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.vosk.Model;
import org.vosk.Recognizer;

public class VoskWakeGrammarRecognizer implements WakeGrammarRecognizer {
  private final Model model;
  private final ObjectMapper om;
  private final String grammarJson;

  public VoskWakeGrammarRecognizer(Model model, MyAssistantProperties props, ObjectMapper om) {
    this.model = model;
    this.om = om;
    try {
      this.grammarJson = buildGrammarJson(props, om);
    } catch (Exception e) {
      throw new IllegalStateException("构建唤醒 grammar 失败", e);
    }
  }

  private static String buildGrammarJson(MyAssistantProperties props, ObjectMapper om) throws Exception {
    Set<String> phrases = new LinkedHashSet<>();
    String w = props.getWakeup().getWakeWord();
    if (w != null && !w.isBlank()) {
      addPhraseVariants(phrases, w.trim());
    }
    List<String> aliases = props.getWakeup().getWakeAliases();
    if (aliases != null) {
      for (String a : aliases) {
        if (a != null && !a.isBlank()) {
          addPhraseVariants(phrases, a.trim());
        }
      }
    }
    if (phrases.isEmpty()) {
      phrases.add("嗨小奇");
    }
    List<String> list = new ArrayList<>(phrases);
    return om.writeValueAsString(list);
  }

  private static void addPhraseVariants(Set<String> phrases, String phrase) {
    phrases.add(phrase);
    String compact = phrase.replace(" ", "").replace("　", "");
    if (!compact.isEmpty() && !compact.equals(phrase)) {
      phrases.add(compact);
    }
  }

  @Override
  public Optional<String> recognizeWake(byte[] audioBytes, String audioFormat, int sampleRate) throws Exception {
    if (audioBytes == null || audioBytes.length == 0) {
      return Optional.empty();
    }
    if (audioFormat != null && !"pcm16".equalsIgnoreCase(audioFormat) && !"raw".equalsIgnoreCase(audioFormat)) {
      throw new IllegalArgumentException("Vosk 唤醒链路仅支持 PCM16，audioFormat=" + audioFormat);
    }
    if (sampleRate <= 0) {
      throw new IllegalArgumentException("sampleRate 非法: " + sampleRate);
    }

    try (Recognizer rec = new Recognizer(model, sampleRate)) {
      rec.setGrammar(grammarJson);
      rec.acceptWaveForm(audioBytes, audioBytes.length);
      String json = rec.getFinalResult();
      String text = om.readTree(json).path("text").asText("").trim();
      if (text.isEmpty()) {
        return Optional.empty();
      }
      return Optional.of(text);
    }
  }
}
