package com.myassistant.server.service.tool;

import java.util.Map;

public class ToolResult {
  public boolean ok;
  public Map<String, Object> result;
  public String errorCode;
  public String errorMessage;

  public static ToolResult ok(Map<String, Object> result) {
    ToolResult r = new ToolResult();
    r.ok = true;
    r.result = result;
    return r;
  }

  public static ToolResult fail(String code, String message) {
    ToolResult r = new ToolResult();
    r.ok = false;
    r.errorCode = code;
    r.errorMessage = message;
    return r;
  }
}

