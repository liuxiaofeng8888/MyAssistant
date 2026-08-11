package com.myassistant.server.service.vlm;

/**
 * VLM 理解结果。
 * 与 NluResult 保持结构对齐，方便后续合并或替换 NLU 链路。
 */
public class VlmResult {

  public enum Kind {
    /** 纯文本回复（描述图片、回答闲聊） */
    CHAT,
    /**
     * 工具调用：从图片+文本中解析出的结构化意图。
     * 例如「识别这张发票 → {tool: invoice.parse, args: {amount: 100, ...}}」
     */
    TOOL_CALL
  }

  /** 结果类型 */
  private Kind kind;

  /** 助手的自然语言回复文本 */
  private String assistantText;

  /** 工具名称（仅 TOOL_CALL） */
  private String toolName;

  /** 工具参数（仅 TOOL_CALL） */
  private java.util.Map<String, Object> toolArgs;

  // ---- 工厂方法 ----

  public static VlmResult chat(String text) {
    VlmResult r = new VlmResult();
    r.kind = Kind.CHAT;
    r.assistantText = text;
    return r;
  }

  public static VlmResult tool(String name, java.util.Map<String, Object> args, String prefaceText) {
    VlmResult r = new VlmResult();
    r.kind = Kind.TOOL_CALL;
    r.toolName = name;
    r.toolArgs = args;
    r.assistantText = prefaceText;
    return r;
  }

  // ---- getters / setters ----

  public Kind getKind() { return kind; }
  public void setKind(Kind kind) { this.kind = kind; }

  public String getAssistantText() { return assistantText; }
  public void setAssistantText(String assistantText) { this.assistantText = assistantText; }

  public String getToolName() { return toolName; }
  public void setToolName(String toolName) { this.toolName = toolName; }

  public java.util.Map<String, Object> getToolArgs() { return toolArgs; }
  public void setToolArgs(java.util.Map<String, Object> toolArgs) { this.toolArgs = toolArgs; }
}
