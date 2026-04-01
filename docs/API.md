## 服务端 API（MVP）

### 1. 约定
- **基础域名**：`https://api.example.com`（占位）
- **鉴权**：`Authorization: Bearer <token>`（MVP 可先用设备匿名 token）
- **内容类型**：HTTP 使用 JSON；流式使用 WebSocket JSON 帧 + 二进制音频帧（或全部 Base64 JSON，MVP 可选其一）
- **时间格式**：ISO 8601（如 `2026-03-31T19:30:00+08:00`）
- **trace_id**：服务端每次会话/请求生成，用于排查

### 2. WebSocket：语音对话（推荐）
#### 2.1 连接
- **URL**：`wss://api.example.com/v1/voice/ws`
- **Query**：
  - `conversation_id`：可选；不传则服务端新建
  - `audio_format`：`opus` | `pcm16`（建议 `opus`）
  - `sample_rate`：`16000`（pcm 时必填；opus 可选）
- **Headers**：`Authorization`

#### 2.2 客户端 → 服务端（JSON 文本帧）
##### a) 开始一次发言
```json
{
  "type": "start",
  "client_msg_id": "c-uuid",
  "mode": "voice",
  "lang": "zh-CN",
  "vad": { "enable": true }
}
```

##### b) 结束一次发言
```json
{ "type": "stop", "client_msg_id": "c-uuid" }
```

##### c) 取消（打断）
```json
{ "type": "cancel", "client_msg_id": "c-uuid" }
```

#### 2.3 客户端 → 服务端（音频帧）
- 在 `start` 与 `stop` 之间发送音频分片（建议 20ms–60ms 一包）。
- 若采用“二进制帧”，帧内容为音频字节；若采用“JSON Base64”，则：
```json
{ "type": "audio", "client_msg_id": "c-uuid", "seq": 12, "data_b64": "..." }
```

#### 2.4 服务端 → 客户端（JSON 文本帧）
##### a) 会话建立/确认
```json
{
  "type": "ready",
  "conversation_id": "conv-uuid",
  "trace_id": "t-uuid"
}
```

##### b) ASR 中间结果（可选）
```json
{
  "type": "asr_partial",
  "client_msg_id": "c-uuid",
  "text": "提醒我三十分钟后",
  "is_final": false
}
```

##### c) ASR 最终结果
```json
{
  "type": "asr_final",
  "client_msg_id": "c-uuid",
  "text": "提醒我三十分钟后喝水"
}
```

##### d) 助手回复（流式）
```json
{
  "type": "assistant_delta",
  "client_msg_id": "c-uuid",
  "text": "好的，"
}
```

##### e) 工具调用（服务端执行型，客户端仅展示）
```json
{
  "type": "tool_call",
  "client_msg_id": "c-uuid",
  "name": "reminder.create",
  "args": { "title": "喝水", "when": "in_30m" }
}
```

##### f) 工具结果（用于展示/调试）
```json
{
  "type": "tool_result",
  "client_msg_id": "c-uuid",
  "name": "reminder.create",
  "ok": true,
  "result": { "reminder_id": "r-uuid", "fire_time": "2026-03-31T20:00:00+08:00" }
}
```

##### g) 助手回复完成
```json
{
  "type": "assistant_final",
  "client_msg_id": "c-uuid",
  "text": "好的，我会在 30 分钟后提醒你喝水。"
}
```

##### h) 错误
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

