## 服务端 API（MVP）

### 1. 约定
- **基础域名**：`https://api.example.com`（占位）
- **鉴权**：`Authorization: Bearer <token>`（MVP 可先用设备匿名 token）
- **内容类型**：HTTP 使用 JSON；流式使用 WebSocket（JSON 文本帧 + 音频帧）
- **时间格式**：ISO 8601（如 `2026-03-31T19:30:00+08:00`）
- **trace_id**：服务端每次会话/请求生成，用于排查

### 2. WebSocket：语音对话（推荐）
#### 2.1 连接
- **URL**：`ws://<host>:8080/v1/voice/ws`（本地示例）
- **Headers**：可选 `Authorization`（当前默认关闭鉴权）

连接成功后，服务端会立即回一条 `ready`：

```json
{
  "type": "ready",
  "conversation_id": "conv-uuid",
  "trace_id": "t-uuid"
}
```

#### 2.2 客户端 → 服务端（JSON 文本帧）
##### a) 开始一次发言
```json
{
  "type": "start",
  "client_msg_id": "c-uuid"
}
```

> 说明：`client_msg_id` 由客户端生成，用于把同一轮对话的多条返回消息关联起来。

##### b) 结束一次发言
```json
{ "type": "stop" }
```

##### c) 取消（打断）
```json
{ "type": "cancel", "client_msg_id": "c-uuid" }
```

##### d) 心跳
```json
{ "type": "ping" }
```

#### 2.3 客户端 → 服务端（音频帧）
- 在 `start` 与 `stop` 之间发送音频分片。
- 支持两种方式（可选其一）：
  - **Binary 音频帧**：直接发送音频 bytes（推荐）
  - **JSON Base64 音频帧**：把音频 bytes 做 Base64 放进 `data_b64` 字段

若采用“JSON Base64”，示例：
```json
{
  "type": "audio",
  "client_msg_id": "c-uuid",
  "seq": 12,
  "data_b64": "..."
}
```

音频格式约束（当前服务端实现）：
- 推荐：**WAV(PCM16/单声道)**，采样率 8k 或 16k（服务端会自动解包 WAV）
- 或：**裸 PCM16/单声道**（默认按 16k 处理；若需严格按 8k/16k 切换，建议统一上传 WAV）

#### 2.4 服务端 → 客户端（JSON 文本帧）
##### a) ASR 中间结果（可选）
```json
{
  "type": "asr_partial",
  "client_msg_id": "c-uuid",
  "text": "提醒我三十分钟后",
  "is_final": false
}
```

##### b) ASR 最终结果
```json
{
  "type": "asr_final",
  "client_msg_id": "c-uuid",
  "text": "提醒我三十分钟后喝水"
}
```

##### c) 助手回复（流式）
```json
{
  "type": "assistant_delta",
  "client_msg_id": "c-uuid",
  "text": "好的，"
}
```

##### d) 工具调用（服务端执行型，客户端仅展示）
```json
{
  "type": "tool_call",
  "client_msg_id": "c-uuid",
  "name": "reminder.create",
  "args": { "title": "喝水", "when": "in_30m" }
}
```

##### e) 工具结果（用于展示/调试）
```json
{
  "type": "tool_result",
  "client_msg_id": "c-uuid",
  "name": "reminder.create",
  "ok": true,
  "result": { "reminder_id": "r-uuid", "fire_time": "2026-03-31T20:00:00+08:00" }
}
```

##### f) 助手回复完成
```json
{
  "type": "assistant_final",
  "client_msg_id": "c-uuid",
  "text": "好的，我会在 30 分钟后提醒你喝水。"
}
```

##### g) 错误
```json
{
  "type": "error",
  "client_msg_id": "c-uuid",
  "trace_id": "t-uuid",
  "code": "ASR_TIMEOUT",
  "message": "语音识别超时，请重试。",
  "retryable": true
}
```

### 3. HTTP：会话/历史（MVP 可选，若历史仅本地可不做）
#### 3.1 获取会话列表
- `GET /v1/conversations`

#### 3.2 获取会话消息
- `GET /v1/conversations/{conversation_id}/messages`

### 4. 错误码（建议）
- `UNAUTHORIZED`：token 无效
- `RATE_LIMITED`：限流
- `ASR_UNAVAILABLE` / `ASR_TIMEOUT`
- `LLM_UNAVAILABLE` / `LLM_TIMEOUT`
- `TOOL_FAILED`：工具执行失败
- `BAD_REQUEST`：参数错误

### 5. 工具/技能命名规范（建议）
- `reminder.create` / `reminder.list` / `reminder.cancel`
- `todo.add` / `todo.list` / `todo.complete`
- `weather.query`
- `timer.start` / `timer.stop` / `timer.status`

