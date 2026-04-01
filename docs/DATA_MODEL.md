## 数据模型（MVP）

### 1. 客户端本地数据（建议 Room/SQLite）
#### 1.1 Conversation（会话）
- `conversation_id`：string（uuid）
- `title`：string（可用首条用户发言截断生成）
- `created_at`：datetime
- `updated_at`：datetime

#### 1.2 Message（消息）
- `message_id`：string（uuid）
- `conversation_id`：string
- `role`：`user` | `assistant` | `system`
- `content_type`：`text` | `event`
- `text`：string
- `created_at`：datetime
- `client_msg_id`：string（用于匹配一次发言）
- `trace_id`：string（服务端链路追踪）

#### 1.3 ToolInvocation（工具调用记录，可选）
- `invocation_id`：string（uuid）
- `conversation_id`：string
- `client_msg_id`：string
- `tool_name`：string
- `args_json`：string
- `ok`：boolean
- `result_json`：string
- `created_at`：datetime

#### 1.4 Settings（设置）
- `save_history`：boolean（默认 true）
- `tts_enabled`：boolean（默认 true）
- `tts_rate`：float（默认 1.0）
- `tts_pitch`：float（默认 1.0）
- `privacy_audio_upload`：enum（`always` | `only_when_needed` | `never`，MVP 可先固定为上传但不存储）

### 2. 服务端数据（若需要同步/审计）
> MVP 可以只存“最小化元数据”，避免合规风险；若要存内容，必须有清晰告知与开关。

#### 2.1 Session（会话）
- `conversation_id`、`user_id/device_id`、`created_at`、`last_active_at`

#### 2.2 Audit（审计）
- `trace_id`、`tool_name`、`ok`、`latency_ms`、`timestamp`
- 日志字段需脱敏；默认不保存原始音频

### 3. 技能数据（MVP）
#### 3.1 Reminder（提醒）
- `reminder_id`
- `user_id/device_id`
- `title`
- `fire_time`
- `status`：`scheduled` | `cancelled` | `fired`
- `created_at`

#### 3.2 Todo（待办）
- `todo_id`
- `user_id/device_id`
- `title`
- `due_time`（可选）
- `status`：`open` | `done`
- `created_at`、`updated_at`

