package com.myassistant.server.service.tool;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InMemoryToolDispatcher implements ToolDispatcher {
  @Override
  public ToolResult dispatch(String toolName, Map<String, Object> args) {
    if ("reminder.create".equals(toolName)) {
      String id = "r-" + UUID.randomUUID();
      String fireTime = OffsetDateTime.now().plusMinutes(30).toString();
      return ToolResult.ok(Map.of(
          "reminder_id", id,
          "fire_time", fireTime
      ));
    }
    return ToolResult.fail("TOOL_NOT_FOUND", "未支持的工具：" + toolName);
  }
}

