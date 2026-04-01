package com.myassistant.server.service.llm;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedNluService implements NluService {
  @Override
  public NluResult parse(String userText) {
    String t = userText == null ? "" : userText.trim();
    if (t.isEmpty()) {
      return NluResult.chat("我没听清，你可以再说一遍吗？");
    }

    // MVP：非常粗的规则，先跑通 reminder.create
    if (t.contains("提醒") && (t.contains("分钟") || t.contains("点"))) {
      Map<String, Object> args = new HashMap<>();
      args.put("title", t.replace("提醒我", "").replace("提醒", "").trim());
      if (t.contains("30") && t.contains("分钟")) {
        args.put("when", "in_30m");
      }
      return NluResult.tool("reminder.create", args, "好的，我来帮你设置提醒。");
    }

    return NluResult.chat("我收到啦：" + t);
  }
}

