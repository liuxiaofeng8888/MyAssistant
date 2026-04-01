package com.myassistant.server.service.wakeup;

public interface WakeWordService {
  WakeWordResult detect(String text);
}

