package com.myassistant.server.service.llm;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RuleBasedNluService implements NluService {
  private static final ZoneId ZONE = ZoneId.systemDefault();

  private static final String[] JOKES = new String[] {
      "有一天我问电脑：你会下棋吗？电脑说：会啊。我说那你怎么还天天被我“关机”？",
      "我对闹钟说：你能不能别这么准时？闹钟说：不行，我怕你准时迟到。",
      "朋友问我：你怎么总这么冷静？我说：因为我已经把情绪放到回收站了。",
      "我问导航：还有多久到？导航说：取决于你有多想现在就到。",
      "我对镜子说：你能不能瘦一点？镜子说：我只是反映现实，不负责美化。"
  };

  // reminder utterances (口语覆盖面尽量大一些)
  private static final Pattern P_REMINDER_PREFIX =
      Pattern.compile("^(帮我|给我|请|麻烦)?(设置|创建|加一个|加个)?(提醒|闹钟)(一下|一个|个)?");

  // e.g. "30分钟后", "1小时后", "2个小时后", "10秒后"
  private static final Pattern P_AFTER =
      Pattern.compile("(?:(?<n>\\d{1,4})\\s*(?<unit>秒|分钟|分|小时|时))\\s*(后|之后)");

  // e.g. "明天8点", "今天 18:30", "后天 9点半", "8点提醒我..."
  private static final Pattern P_DAY_TIME =
      Pattern.compile("(?:(?<day>今天|明天|后天)\\s*)?(?<h>\\d{1,2})(?:[:：](?<m>\\d{1,2}))?\\s*(点|时)(?<half>半)?");

  // ISO-like direct time (advanced clients may send it)
  private static final Pattern P_ISO_ZONED =
      Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}(:\\d{2})?(?:\\.\\d+)?(?:Z|[+-]\\d{2}:\\d{2})");

  @Override
  public NluResult parse(String userText) {
    String t = normalize(userText);
    if (t.isEmpty()) {
      return NluResult.chat("我没听清，你可以再说一遍吗？");
    }

    // Intent: tell a joke
    if (looksLikeJokeRequest(t)) {
      return NluResult.chat(pickJoke());
    }

    // Intent: reminder.create
    if (looksLikeReminder(t)) {
      Map<String, Object> args = new HashMap<>();

      // 1) extract time (prefer explicit "xx后" over "x点")
      ZonedDateTime fireAt = parseFireTime(t);
      if (fireAt != null) {
        args.put("fire_time", fireAt.toOffsetDateTime().toString());
      }

      // 2) extract title (strip trigger words + time phrase)
      String title = extractReminderTitle(t);
      if (title.isBlank()) {
        title = "提醒";
      }
      args.put("title", title);

      // 若没解析出时间，也允许创建（工具层可兜底/返回错误）
      return NluResult.tool("reminder.create", args, "好的，我来帮你设置提醒。");
    }

    return NluResult.chat("我收到啦：" + t);
  }

  private static boolean looksLikeJokeRequest(String t) {
    // Vosk 可能会识别成 “给我讲个笑话/讲个笑话/来个笑话/说个笑话”
    if (!t.contains("笑话")) return false;
    return t.contains("讲") || t.contains("来") || t.contains("说") || t.contains("给我");
  }

  private static String pickJoke() {
    int idx = ThreadLocalRandom.current().nextInt(JOKES.length);
    return JOKES[idx];
  }

  private static boolean looksLikeReminder(String t) {
    if (t.contains("提醒") || t.contains("闹钟")) return true;
    return P_REMINDER_PREFIX.matcher(t).find();
  }

  private static ZonedDateTime parseFireTime(String t) {
    // A) already has ISO timestamp
    Matcher iso = P_ISO_ZONED.matcher(t);
    if (iso.find()) {
      String s = iso.group();
      try {
        return ZonedDateTime.parse(s);
      } catch (DateTimeParseException ignored) {
        // fallthrough
      }
    }

    // B) relative time: "30分钟后"
    Matcher after = P_AFTER.matcher(t);
    if (after.find()) {
      long n = Long.parseLong(after.group("n"));
      String unit = after.group("unit");
      ZonedDateTime now = ZonedDateTime.now(ZONE);
      if ("秒".equals(unit)) {
        return now.plusSeconds(n);
      }
      if ("分钟".equals(unit) || "分".equals(unit)) {
        return now.plusMinutes(n);
      }
      if ("小时".equals(unit) || "时".equals(unit)) {
        return now.plusHours(n);
      }
      return null;
    }

    // C) absolute time: "明天8点半"
    Matcher dt = P_DAY_TIME.matcher(t);
    if (dt.find()) {
      String day = dt.group("day");
      int h = Integer.parseInt(dt.group("h"));
      String mRaw = dt.group("m");
      boolean half = dt.group("half") != null;
      int m = 0;
      if (mRaw != null && !mRaw.isBlank()) {
        m = Integer.parseInt(mRaw);
      } else if (half) {
        m = 30;
      }

      LocalDate base = LocalDate.now(ZONE);
      if ("明天".equals(day)) base = base.plusDays(1);
      else if ("后天".equals(day)) base = base.plusDays(2);

      // 如果没说今天/明天/后天，且时间已过，则默认是“明天”
      LocalDateTime candidate = LocalDateTime.of(base, LocalTime.of(clamp(h, 0, 23), clamp(m, 0, 59)));
      ZonedDateTime zdt = candidate.atZone(ZONE);
      if (day == null || day.isBlank()) {
        ZonedDateTime now = ZonedDateTime.now(ZONE);
        if (zdt.isBefore(now.minusSeconds(10))) {
          zdt = zdt.plusDays(1);
        }
      }
      return zdt;
    }

    return null;
  }

  private static String extractReminderTitle(String t) {
    String s = t;
    // remove common reminder prefixes
    s = P_REMINDER_PREFIX.matcher(s).replaceFirst("");
    s = s.replace("提醒我", "").replace("提醒", "").replace("闹钟", "");

    // remove time phrases to keep title clean
    s = P_AFTER.matcher(s).replaceAll("");
    s = P_DAY_TIME.matcher(s).replaceAll("");

    // cleanup fillers
    s = s.replace("一下", "").replace("吧", "").replace("好吗", "").replace("谢谢", "");
    return s.trim();
  }

  private static int clamp(int v, int lo, int hi) {
    return Math.max(lo, Math.min(hi, v));
  }

  /**
   * Vosk 常见输出：无标点、带空格、中文数字口语（“三十分钟后”）。
   * 这里做最小但高收益的归一化：去空格 + 常见中文数字转阿拉伯数字（仅覆盖 0-99 / 千以内组合）。
   */
  private static String normalize(String userText) {
    String t = userText == null ? "" : userText.trim();
    if (t.isEmpty()) return "";
    // Vosk 有时会插入空格
    t = t.replace(" ", "");
    // 全角冒号
    t = t.replace("：", ":");
    return zhNumberToDigits(t);
  }

  private static String zhNumberToDigits(String s) {
    // 只转换“明显是数字短语”的片段，避免误伤普通词（非常简化版）
    // e.g. 三十分钟后 -> 30分钟后；两小时后 -> 2小时后；十点半 -> 10点半
    Pattern p = Pattern.compile("[零〇一二两三四五六七八九十百千]+");
    Matcher m = p.matcher(s);
    StringBuffer out = new StringBuffer();
    while (m.find()) {
      String token = m.group();
      Integer v = parseZhNumber(token);
      if (v == null) {
        m.appendReplacement(out, token);
      } else {
        m.appendReplacement(out, String.valueOf(v));
      }
    }
    m.appendTail(out);
    return out.toString();
  }

  private static Integer parseZhNumber(String token) {
    // supports 0-9999-ish (零/十/百/千)；不支持“万/亿”
    if (token == null || token.isBlank()) return null;
    int total = 0;
    int current = 0;
    int lastUnit = 1;
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      Integer d = zhDigit(c);
      if (d != null) {
        current = d;
        continue;
      }
      int unit;
      if (c == '十') unit = 10;
      else if (c == '百') unit = 100;
      else if (c == '千') unit = 1000;
      else unit = -1;
      if (unit == -1) return null;

      if (current == 0 && (c == '十')) {
        // "十" => 10, "十二" => 12
        current = 1;
      }
      total += current * unit;
      current = 0;
      lastUnit = unit;
    }
    total += current;
    // avoid converting single "零" to 0 in non-number contexts? It's fine.
    // prevent weirdly large conversions for plain words; keep conservative:
    if (total < 0 || total > 100000) return null;
    return total;
  }

  private static Integer zhDigit(char c) {
    switch (c) {
      case '零':
      case '〇':
        return 0;
      case '一':
        return 1;
      case '二':
      case '两':
        return 2;
      case '三':
        return 3;
      case '四':
        return 4;
      case '五':
        return 5;
      case '六':
        return 6;
      case '七':
        return 7;
      case '八':
        return 8;
      case '九':
        return 9;
      default:
        return null;
    }
  }
}

