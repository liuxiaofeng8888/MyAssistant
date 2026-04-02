package com.myassistant.server.service.wakeup;

public interface WakeWordService {
  WakeWordResult detect(String text);

  /**
   * 语法/专用链路已确认唤醒；用开放域 ASR 全文推导剩余指令（尽量剥掉主唤醒词或句首别名）。
   */
  default WakeWordResult resolveAfterGrammarHit(String fullAsrText) {
    return detect(fullAsrText);
  }
}

