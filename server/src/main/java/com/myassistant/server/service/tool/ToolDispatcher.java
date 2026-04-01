package com.myassistant.server.service.tool;

import java.util.Map;

public interface ToolDispatcher {
  ToolResult dispatch(String toolName, Map<String, Object> args) throws Exception;
}

