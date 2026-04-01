package com.myassistant.server.service.tool;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InMemoryToolDispatcher implements ToolDispatcher {
  @Override
  public ToolResult dispatch(String toolName, Map<String, Object> args) {
    if ("reminder.create".equals(toolName)) {
      String id = "r-" + UUID.randomUUID();
      OffsetDateTime fireTime = resolveFireTime(args);
      return ToolResult.ok(Map.of(
          "reminder_id", id,
          "fire_time", fireTime.toString()
      ));
    }
    return ToolResult.fail("TOOL_NOT_FOUND", "未支持的工具：" + toolName);
  }

  private static OffsetDateTime resolveFireTime(Map<String, Object> args) {
    // Priority:
    // 1) args.fire_time: ISO_OFFSET_DATE_TIME
    // 2) args.after_minutes / after_seconds
    // 3) fallback: now + 30m
    if (args != null) {
      Object ft = args.get("fire_time");
      if (ft instanceof String) {
        String s = ((String) ft).trim();
        if (s.isBlank()) {
          // ignore
        } else {
        try {
          return OffsetDateTime.parse(s);
        } catch (DateTimeParseException ignored) {
          // try parse as zoned then convert
          try {
            return ZonedDateTime.parse(s).toOffsetDateTime();
          } catch (DateTimeParseException ignored2) {
            // fallthrough
          }
        }
        }
      }

      Object am = args.get("after_minutes");
      if (am instanceof Number) {
        return OffsetDateTime.now().plusMinutes(((Number) am).longValue());
      }
      if (am instanceof String) {
        String s = ((String) am).trim();
        if (s.isBlank()) {
          // ignore
        } else {
        try {
          return OffsetDateTime.now().plusMinutes(Long.parseLong(s));
        } catch (NumberFormatException ignored) {
        }
        }
      }

      Object as = args.get("after_seconds");
      if (as instanceof Number) {
        return OffsetDateTime.now().plusSeconds(((Number) as).longValue());
      }
      if (as instanceof String) {
        String s = ((String) as).trim();
        if (s.isBlank()) {
          // ignore
        } else {
        try {
          return OffsetDateTime.now().plusSeconds(Long.parseLong(s));
        } catch (NumberFormatException ignored) {
        }
        }
      }
    }

    // default behavior for MVP
    return OffsetDateTime.now(ZoneId.systemDefault()).plusMinutes(30);
  }
}

