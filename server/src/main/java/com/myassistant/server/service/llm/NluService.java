package com.myassistant.server.service.llm;

public interface NluService {
  /**
   * 把用户文本解析为：闲聊回复 或 工具调用。
   * MVP 用规则；后续替换为讯飞星火（Spark）/函数调用。
   */
  NluResult parse(String userText) throws Exception;
}

