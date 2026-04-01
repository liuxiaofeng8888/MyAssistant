## MyAssistant Server（Spring Boot）

### 运行
在 `server/` 目录执行：

```bash
mvn spring-boot:run
```

启动后：
- 健康检查：`GET http://localhost:8080/healthz`
- WebSocket：`ws://localhost:8080/v1/voice/ws`

### WebSocket 协议（MVP）
当前实现先走 **JSON + Base64 音频**（便于 Android 端快速联调），消息类型见 `docs/API.md`。

最小联调顺序：
1. 连接 WS，收到 `ready`
2. 发 `start`（带 `client_msg_id`）
3. 连续发多条 `audio`（`data_b64` 为音频分片）
4. 发 `stop`
5. 收到 `asr_final` + `assistant_final`（当前 ASR/LLM 为 mock/规则）

### 接入讯飞（下一步）
已预留：
- 配置：`src/main/resources/application.yml` 的 `myassistant.iflytek.*`
- ASR：已实现 `IflytekAsrService`（WebSocket 鉴权 + 分片上传 + 结果解析）。开启方式：
  - `myassistant.iflytek.enabled=true`
  - 配置 `app-id/api-key/api-secret`
- 大模型：替换 `RuleBasedNluService` 为 `SparkNluService`（建议工具/函数调用）

