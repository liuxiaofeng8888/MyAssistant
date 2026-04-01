package com.myassistant.server.service.llm;

import java.util.Map;

public class NluResult {
  public enum Kind { CHAT, TOOL_CALL }

  public Kind kind;
  public String assistantText;

  public String toolName;
  public Map<String, Object> toolArgs;

  public static NluResult chat(String text) {
    NluResult r = new NluResult();
    r.kind = Kind.CHAT;
    r.assistantText = text;
    return r;
  }

  public static NluResult tool(String name, Map<String, Object> args, String prefaceText) {
    NluResult r = new NluResult();
    r.kind = Kind.TOOL_CALL;
    r.toolName = name;
    r.toolArgs = args;
    r.assistantText = prefaceText;
    return r;
  }
}

