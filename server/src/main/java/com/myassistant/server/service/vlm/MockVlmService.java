package com.myassistant.server.service.vlm;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * MVP 占位实现：不调用真实 VLM，固定返回描述文本。
 * 用于跑通多模态链路后再替换为 OpenAI/Qwen 等真实实现。
 */
@Service
@ConditionalOnProperty(prefix = "myassistant.vlm", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockVlmService implements VlmService {

  @Override
  public VlmResult understand(MultimodalContext ctx) throws Exception {
    if (ctx == null || ctx.isEmpty()) {
      return VlmResult.chat("没有收到图片或文字，请再说一次。");
    }

    int imgCount = ctx.getImages().size();
    String text = ctx.getUserText();

    StringBuilder sb = new StringBuilder();
    if (imgCount > 0) {
      sb.append("我看到了 ").append(imgCount).append(" 张图片");
    }
    if (text != null && !text.isBlank()) {
      if (sb.length() > 0) sb.append("，");
      sb.append("你说：").append(text);
    }
    sb.append("。（VLM Mock 回复，请接入真实模型后替换）");

    return VlmResult.chat(sb.toString());
  }
}
