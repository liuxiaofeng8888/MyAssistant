package com.myassistant.server.service.vlm;

/**
 * VLM（视觉语言模型）服务接口 — 策略模式，与 {@code AsrService} 对齐。
 *
 * <p>支持多模态输入（文本 + 图片），输出结构化理解结果。
 * 可插拔不同提供商：Mock / OpenAI GPT-4V / 通义千问 VL / 本地模型等。
 */
public interface VlmService {

  /**
   * 对多模态输入进行理解，返回结构化结果。
   *
   * @param ctx 包含用户文本 + 图片列表的多模态上下文
   * @return VLM 理解结果（CHAT 或 TOOL_CALL）
   */
  VlmResult understand(MultimodalContext ctx) throws Exception;
}
