## MyAssistant Server（Spring Boot）

### 运行
在 `server/` 目录执行：

```bash
mvn spring-boot:run
```

启动后：
- 健康检查：`GET http://localhost:8080/healthz`
- WebSocket：`ws://localhost:8080/v1/voice/ws`

### ASR（语音识别）配置
当前 ASR 通过 `myassistant.asr.provider` 选择：
- `mock`：固定返回（用于联调）
- `vosk`：离线开源 ASR（CPU 可跑，推荐）
- `iflytek`：讯飞 ASR（需要配置密钥）

推荐通过环境变量启动（避免把密钥写进文件）：

#### 1) 使用 Vosk（离线、CPU）
已支持中文模型目录，默认路径为 `server/models/vosk-model-small-cn-0.22`。

```bash
MYASSISTANT_ASR_PROVIDER=vosk mvn spring-boot:run
```

如模型不在默认目录，可覆盖：

```bash
MYASSISTANT_ASR_PROVIDER=vosk \
MYASSISTANT_VOSK_MODEL_PATH=/abs/path/to/vosk-model \
mvn spring-boot:run
```

#### 2) 使用讯飞（在线）

```bash
MYASSISTANT_ASR_PROVIDER=iflytek \
MYASSISTANT_IFLYTEK_APP_ID=xxx \
MYASSISTANT_IFLYTEK_API_KEY=yyy \
MYASSISTANT_IFLYTEK_API_SECRET=zzz \
mvn spring-boot:run
```

### WebSocket 协议（MVP）
当前实现支持两种音频上行方式（可混用）：
- **Binary 音频帧**：直接发送音频 bytes（推荐，客户端更自然）
- **JSON + Base64**：发送 `{type:"audio", data_b64:"..."}`（便于快速联调）

服务端在 `stop` 时会自动识别输入是否为 **WAV(PCM16/单声道)**，若是则自动解包出 PCM；否则默认按 **裸 PCM16** 处理。

协议详情见根目录 `docs/API.md`（以服务端代码为准）。

最小联调顺序：
1. 连接 WS，收到 `ready`
2. 发 `start`（带 `client_msg_id`）
3. 连续发音频分片（binary 或 `audio.data_b64`）
4. 发 `stop`
5. 收到 `asr_final` + `assistant_final`（当前 NLU 为规则）

### NLU（理解/工具调用）
MVP 目前使用 `RuleBasedNluService`（规则解析 + 工具调用），后续可替换为任意 LLM（讯飞星火/本地大模型）实现函数/工具调用。

