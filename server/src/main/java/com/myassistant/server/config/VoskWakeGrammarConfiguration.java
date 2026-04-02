package com.myassistant.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.myassistant.server.service.asr.VoskWakeGrammarRecognizer;
import com.myassistant.server.service.wakeup.WakeGrammarRecognizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.vosk.Model;

@Configuration
@ConditionalOnProperty(prefix = "myassistant.asr", name = "provider", havingValue = "vosk")
public class VoskWakeGrammarConfiguration {

  @Bean
  @ConditionalOnProperty(prefix = "myassistant.wakeup", name = "dedicated-path", havingValue = "true", matchIfMissing = true)
  public WakeGrammarRecognizer voskWakeGrammarRecognizer(
      Model model, MyAssistantProperties props, ObjectMapper om) {
    return new VoskWakeGrammarRecognizer(model, props, om);
  }
}
