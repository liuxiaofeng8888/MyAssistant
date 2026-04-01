package com.myassistant.server.service.wakeup;

import com.myassistant.server.config.MyAssistantProperties;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "myassistant.wakeup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleBasedWakeWordService implements WakeWordService {
  private final String wakeWordNormalized;
  private final String wakeWordDisplay;

  public RuleBasedWakeWordService(MyAssistantProperties props) {
    String w = props.getWakeup().getWakeWord();
    if (w == null || w.isBlank()) {
      w = "嗨小布";
    }
    this.wakeWordDisplay = w.trim();
    this.wakeWordNormalized = normalizeForMatch(wakeWordDisplay);
  }

  @Override
  public WakeWordResult detect(String text) {
    String original = text == null ? "" : text.trim();
    if (original.isEmpty()) {
      return WakeWordResult.notAwakened(wakeWordDisplay, "");
    }

    String normalized = normalizeForMatch(original);
    int idx = normalized.indexOf(wakeWordNormalized);
    if (idx < 0) {
      return WakeWordResult.notAwakened(wakeWordDisplay, original);
    }

    // 仅做最小“剥离”：如果唤醒词出现在开头附近（允许前面有极少噪声），就把它从原文中删掉
    String remaining = stripWakeWordFromOriginal(original);
    return WakeWordResult.awakened(wakeWordDisplay, remaining);
  }

  private String stripWakeWordFromOriginal(String original) {
    String o = original;
    // 常见情况：Vosk/讯飞会输出带空格或逗号
    String n = normalizeForMatch(o);
    if (!n.startsWith(wakeWordNormalized)) {
      // 如果不是开头，先不做激进删除，避免误删正文
      return original.trim();
    }

    // 将原文前缀中可能出现的空格/标点一起去掉
    // 做法：从原文头开始扫描，累计“匹配字符”直到覆盖 wakeWordNormalized
    int consumed = 0;
    int matched = 0;
    while (consumed < o.length() && matched < wakeWordNormalized.length()) {
      char c = o.charAt(consumed);
      String s = String.valueOf(c);
      String nn = normalizeForMatch(s);
      if (!nn.isEmpty()) {
        // nn 可能包含多字符（例如全角标点被清空，不会走到这里）
        for (int i = 0; i < nn.length() && matched < wakeWordNormalized.length(); i++) {
          char mc = nn.charAt(i);
          if (mc == wakeWordNormalized.charAt(matched)) {
            matched++;
          } else {
            // 不严格对齐：遇到不一致就停止剥离
            return original.trim();
          }
        }
      }
      consumed++;
    }

    if (matched < wakeWordNormalized.length()) {
      return original.trim();
    }

    // 继续吃掉紧随其后的分隔符（空格/逗号/顿号/句号等）
    while (consumed < o.length()) {
      char c = o.charAt(consumed);
      if (isSeparator(c)) {
        consumed++;
        continue;
      }
      break;
    }
    return o.substring(consumed).trim();
  }

  private static boolean isSeparator(char c) {
    return Character.isWhitespace(c)
        || c == ',' || c == '，'
        || c == '.' || c == '。'
        || c == '、'
        || c == '!' || c == '！'
        || c == '?' || c == '？'
        || c == ':' || c == '：'
        || c == ';' || c == '；';
  }

  private static String normalizeForMatch(String s) {
    if (s == null) return "";
    String t = s.trim()
        .toLowerCase(Locale.ROOT)
        .replace(" ", "");
    // 去掉常见中英文标点，提升匹配鲁棒性
    t = t.replace(",", "").replace("，", "")
        .replace(".", "").replace("。", "")
        .replace("、", "")
        .replace("!", "").replace("！", "")
        .replace("?", "").replace("？", "")
        .replace(":", "").replace("：", "")
        .replace(";", "").replace("；", "");
    return t;
  }
}

