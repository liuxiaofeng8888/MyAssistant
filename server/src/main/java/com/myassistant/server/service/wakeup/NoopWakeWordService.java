package com.myassistant.server.service.wakeup;

import com.myassistant.server.config.MyAssistantProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "myassistant.wakeup", name = "enabled", havingValue = "false")
public class NoopWakeWordService implements WakeWordService {
  private final String wakeWord;

  public NoopWakeWordService(MyAssistantProperties props) {
    String w = props.getWakeup().getWakeWord();
    this.wakeWord = (w == null || w.isBlank()) ? "嗨小布" : w.trim();
  }

  @Override
  public WakeWordResult detect(String text) {
    return WakeWordResult.awakened(wakeWord, text == null ? "" : text.trim());
  }
}

