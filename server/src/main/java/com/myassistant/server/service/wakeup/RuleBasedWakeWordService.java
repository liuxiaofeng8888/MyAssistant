package com.myassistant.server.service.wakeup;

import com.myassistant.server.config.MyAssistantProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "myassistant.wakeup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RuleBasedWakeWordService implements WakeWordService {
  private final String wakeWordNormalized;
  private final String wakeWordDisplay;
  private final List<String> wakeAliasNormalized;

  public RuleBasedWakeWordService(MyAssistantProperties props) {
    String w = props.getWakeup().getWakeWord();
    if (w == null || w.isBlank()) {
      w = "嗨小布";
    }
    this.wakeWordDisplay = w.trim();
    this.wakeWordNormalized = normalizeForMatch(wakeWordDisplay);

    List<String> norm = new ArrayList<>();
    List<String> aliases = props.getWakeup().getWakeAliases();
    if (aliases != null) {
      for (String a : aliases) {
        if (a == null || a.isBlank()) {
          continue;
        }
        String an = normalizeForMatch(a);
        if (an.isEmpty() || an.equals(wakeWordNormalized) || norm.contains(an)) {
          continue;
        }
        norm.add(an);
      }
    }
    this.wakeAliasNormalized = Collections.unmodifiableList(norm);
  }

  @Override
  public WakeWordResult detect(String text) {
    String original = text == null ? "" : text.trim();
    if (original.isEmpty()) {
      return WakeWordResult.notAwakened(wakeWordDisplay, "");
    }

    String normalized = normalizeForMatch(original);
    int idx = normalized.indexOf(wakeWordNormalized);
    if (idx >= 0) {
      String remaining = stripNormalizedPrefixFromOriginal(original, wakeWordNormalized);
      return WakeWordResult.awakened(wakeWordDisplay, remaining);
    }

    for (String alias : wakeAliasNormalized) {
      if (normalized.startsWith(alias)) {
        String remaining = stripNormalizedPrefixFromOriginal(original, alias);
        return WakeWordResult.awakened(wakeWordDisplay, remaining);
      }
    }

    return WakeWordResult.notAwakened(wakeWordDisplay, original);
  }

  @Override
  public WakeWordResult resolveAfterGrammarHit(String fullAsrText) {
    String original = fullAsrText == null ? "" : fullAsrText.trim();
    if (original.isEmpty()) {
      return WakeWordResult.awakened(wakeWordDisplay, "");
    }
    String normalized = normalizeForMatch(original);
    if (normalized.startsWith(wakeWordNormalized)) {
      return WakeWordResult.awakened(
          wakeWordDisplay, stripNormalizedPrefixFromOriginal(original, wakeWordNormalized));
    }
    for (String alias : wakeAliasNormalized) {
      if (normalized.startsWith(alias)) {
        return WakeWordResult.awakened(
            wakeWordDisplay, stripNormalizedPrefixFromOriginal(original, alias));
      }
    }
    return WakeWordResult.awakened(wakeWordDisplay, original);
  }

  private static String stripNormalizedPrefixFromOriginal(String original, String targetNormalized) {
    String o = original;
    String n = normalizeForMatch(o);
    if (!n.startsWith(targetNormalized)) {
      return original.trim();
    }

    int consumed = 0;
    int matched = 0;
    while (consumed < o.length() && matched < targetNormalized.length()) {
      char c = o.charAt(consumed);
      String s = String.valueOf(c);
      String nn = normalizeForMatch(s);
      if (!nn.isEmpty()) {
        for (int i = 0; i < nn.length() && matched < targetNormalized.length(); i++) {
          char mc = nn.charAt(i);
          if (mc == targetNormalized.charAt(matched)) {
            matched++;
          } else {
            return original.trim();
          }
        }
      }
      consumed++;
    }

    if (matched < targetNormalized.length()) {
      return original.trim();
    }

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
