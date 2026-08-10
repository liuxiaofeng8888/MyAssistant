# MyAssistant 代码逻辑流程文档

## 版本记录

| 版本 | 日期 | 作者 | 变更说明 |
|------|------|------|----------|
| v1.0.0 | 2026-08-10 | AI Assistant | 初始版本：梳理服务端与 Android 端完整逻辑流程，标注可优化点 |

---

## 1. 项目概述

**MyAssistant** 是一个语音助手 APP，实现「语音输入 → ASR → 对话/意图识别 → 工具调用 → TTS 播报」的可扩展闭环。

- **Android 客户端**：Java 原生开发，集成 Vosk 离线 KWS（关键词唤醒）+ WebRTC VAD（语音活动检测）+ 系统 TTS 播报
- **服务端**：Spring Boot，WebSocket 通信，可插拔 ASR（Vosk/讯飞/Mock），规则 NLU + 工具分发

---

## 2. 整体架构图

```
┌─────────────────────────────────────────────────────────┐
│  Android 客户端                                           │
│                                                           │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐             │
│  │ Vosk KWS │   │AudioRecor│   │Android   │             │
│  │ (常驻唤醒) │   │ der      │   │ TTS      │             │
│  └────┬─────┘   └────┬─────┘   └────▲─────┘             │
│       │唤醒           │录音         │播报                  │
│       ▼               ▼              │                     │
│  ┌────────────────────────┐         │                     │
│  │    MainActivity        │─────────┘                     │
│  │  (状态机 + VAD + UI)   │                               │
│  └───────────┬────────────┘                               │
│              │ WebSocket                                   │
│  ┌───────────▼────────────┐                               │
│  │   VoiceWsClient        │                               │
│  │  (start/audio/stop/    │                               │
│  │   cancel/ping)         │                               │
│  └───────────┬────────────┘                               │
└──────────────┼────────────────────────────────────────────┘
               │  ws://host:8080/v1/voice/ws
┌──────────────▼────────────────────────────────────────────┐
│  服务端 (Spring Boot)                                       │
│                                                             │
│  ┌──────────────────────────────────┐                      │
│  │  VoiceWebSocketHandler           │← 核心编排器           │
│  │  (WebSocket 消息分发 + 链路编排)   │                      │
│  └──┬───────┬────────┬─────────┬───┘                      │
│     │       │        │         │                            │
│     ▼       ▼        ▼         ▼                            │
│  ┌────┐ ┌────┐  ┌──────┐ ┌──────┐                         │
│  │ASR │ │Wake│  │ NLU  │ │Tool  │                         │
│  │Svc │ │Word│  │ Svc  │ │Disp  │                         │
│  └────┘ └────┘  └──────┘ └──────┘                         │
│                                                             │
│  ASR 实现: MockAsrService / VoskAsrService / IflytekAsr    │
│  Wake: RuleBasedWakeWordService / NoopWakeWordService      │
│  NLU: RuleBasedNluService (规则匹配)                        │
│  Tool: InMemoryToolDispatcher (reminder.create)            │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 服务端逻辑流程

### 3.1 入口与配置

**应用入口**：`MyAssistantServerApplication` → Spring Boot 自动装配

**WebSocket 端点**：`/v1/voice/ws`（`VoiceWebSocketConfig` 注册）  
**HTTP 拦截器**：`AuthInterceptor` 对所有非 `/healthz`、`/actuator/**` 路径做 Bearer Token 鉴权（默认关闭）  
**健康检查**：`GET /healthz` → `{"ok":true, "ts":"..."}`

### 3.2 WebSocket 连接生命周期

```
客户端连接
    │
    ▼
afterConnectionEstablished()
    │ 创建 VoiceSessionState (conversationId, traceId)
    │ states.put(sessionId, state)
    │
    ▼
发送 VoiceMessage.ready(conversationId, traceId)
    │
    ▼
┌─── 循环处理消息 ──────────────────────────────┐
│                                                │
│  handleTextMessage / handleBinaryMessage       │
│                                                │
│  「start」→ 重置轮次状态                        │
│  「audio」→ 追加音频 Base64 到 buffer          │
│  「stop」 → ★ 核心处理链路（见 3.3）           │
│  「cancel」→ 取消并重置                         │
│  「ping」 → 回复 pong                          │
│                                                │
└────────────────────────────────────────────────┘
    │
    ▼
afterConnectionClosed()
    │ states.remove(sessionId)
```

### 3.3 核心处理链路（`type: "stop"`）

```
客户端发送 stop
    │
    ▼
① 音频解码: decodeWavIfNeeded(rawAudioBytes)
    ├─ 检测是否为 WAV 头（RIFF...WAVE），是则解包提取 PCM16 + sampleRate
    └─ 否则按裸 PCM16@16kHz 处理
    │
    ▼
② 开放域 ASR: asr.transcribe(audioBytes, format, sampleRate)
    │ 返回识别文本 userText
    │ 失败 → 返回 ASR_FAILED 错误
    │
    ▼
③ 唤醒词检测: resolveWake(userText, audioInput)
    ├─ Vosk grammar 专用链路（如果可用且 dedicated-path=true）
    │    └─ VoskWakeGrammarRecognizer 用受限 grammar 识别唤醒词
    │        命中 → wakeup.resolveAfterGrammarHit(userText)
    │        未命中/异常 → 回落到文本规则
    └─ 文本规则匹配（RuleBasedWakeWordService.detect）
    │
    ▼
④ 唤醒结果判断:
    ├─ 未唤醒 → 返回 "请先说'XX'唤醒我"
    ├─ 仅唤醒词无指令 → 返回 wakeup_detected + "我在，你说"
    └─ 唤醒词+指令 → 剥离唤醒词，继续
    │
    ▼
⑤ NLU 解析: nlu.parse(userText)
    │ RuleBasedNluService 规则匹配
    │ 返回 NluResult { kind: CHAT | TOOL_CALL, ... }
    │
    ▼
⑥ 结果处理:
    ├─ CHAT → 发送 assistant_delta + assistant_final
    └─ TOOL_CALL → 
         ├─ 发送 tool_call 消息
         ├─ tools.dispatch(toolName, toolArgs)
         ├─ 发送 tool_result 消息
         └─ 根据结果生成 assistant_final
```

### 3.4 ASR 服务 (策略模式)

**接口**：`AsrService.transcribe(byte[] audioBytes, String format, int sampleRate)`

| 实现 | 激活条件 | 说明 |
|------|----------|------|
| `MockAsrService` | `asr.provider=mock`（默认） | MVP 占位，固定返回 "提醒我30分钟后喝水" |
| `VoskAsrService` | `asr.provider=vosk` | 离线 ASR，本地 Vosk 模型，仅支持 PCM16 |
| `IflytekAsrService` | `asr.provider=iflytek` | 讯飞云 ASR，WebSocket 流式调用，需鉴权签名 |

**Vosk 模型加载**：`VoskModelConfiguration` → 从 `myassistant.asr.vosk-model-path` 路径加载 Model Bean（`@ConditionalOnProperty` 保障只在 vosk 模式下加载）

**讯飞 ASR 实现细节**（`IflytekAsrService`）：
1. 通过 HMAC-SHA256 签名构建鉴权 URL
2. 通过 OkHttp WebSocket 连接讯飞 ASR 接口
3. 将音频按 1280 字节分帧，逐帧发送（每帧间隔 40ms 模拟实时流）
4. 解析返回的 JSON 结果，拼接 `ws[].cw[].w` 为最终文本
5. 30 秒超时保护

### 3.5 唤醒词服务

**接口**：`WakeWordService`
- `detect(text)` — 检测文本中是否包含唤醒词
- `resolveAfterGrammarHit(fullAsrText)` — grammar 命中后的解析（比 detect 更宽松）

| 实现 | 激活条件 | 说明 |
|------|----------|------|
| `RuleBasedWakeWordService` | `wakeup.enabled=true`（默认） | 文本归一化匹配，支持别名，支持 grammar 后处理 |
| `NoopWakeWordService` | `wakeup.enabled=false` | 始终视为已唤醒，跳过唤醒检测 |

**匹配规则**（`RuleBasedWakeWordService`）：
1. 文本归一化：去空格、标点、转小写
2. 精确匹配唤醒词：`normalized.indexOf(wakeWordNormalized) >= 0`
3. 前缀匹配别名：`normalized.startsWith(alias)`（仅别名做前缀匹配，避免误触发）
4. 剥离唤醒词后返回 `remainingText`

**专用 grammar 唤醒**（`VoskWakeGrammarRecognizer`）：
- 构建 Vosk grammar JSON（唤醒词 + 别名 + 去空格变体）
- 用 `rec.setGrammar()` 限制解码空间
- 与开放域 ASR 分离，提高唤醒准确率

### 3.6 NLU 服务

**`RuleBasedNluService`** — 基于正则规则的意图识别：

```
输入文本
    │
    ▼
normalize(): 去空格 + 全角转半角 + 中文数字→阿拉伯数字
    │
    ▼
意图识别:
    ├─ looksLikeJokeRequest() → CHAT(随机笑话)
    ├─ looksLikeReminder()  → TOOL_CALL(reminder.create)
    │     ├─ parseFireTime(): 解析时间
    │     │   ├─ ISO 时间戳直接解析
    │     │   ├─ "X分钟后" 相对时间
    │     │   └─ "明天8点半" 绝对时间（含智能补全：时间已过→默认明天）
    │     └─ extractReminderTitle(): 提取提醒标题
    └─ 默认 → CHAT("我收到啦："+原文)
```

**中文数字归一化**：支持「零一二两三四五六七八九十百千」→ 阿拉伯数字，覆盖 0-9999 范围。

### 3.7 工具分发

**`InMemoryToolDispatcher`** — 当前仅支持 `reminder.create`：
1. `fire_time` 解析优先级：`args.fire_time` > `after_minutes` > `after_seconds` > fallback(now+30min)
2. 返回 `reminder_id` + `fire_time`

---

## 4. Android 客户端逻辑流程

### 4.1 初始化阶段

```
onCreate()
    │
    ├─ 初始化 UI 组件 (logView, asrView, btnConnect, btnWake, serverUrl)
    ├─ 初始化 VpaOrbView (浮动动画球)
    ├─ initTts(): 初始化系统 TTS (中文)，注册 UtteranceProgressListener
    ├─ initVad(): 初始化 WebRTC VAD (16k, 320 frame, VERY_AGGRESSIVE)
    ├─ initKws(): 初始化 VoskKwsService
    │     ├─ 设置唤醒词数组 + cooldown(1800ms)
    │     └─ 从 assets 加载模型 → 就绪后自动启动常驻监听
    ├─ ensureAudioPermission(): 请求录音权限
    │
    └─ 创建 VoiceWsClient + 注册回调
```

### 4.2 KWS 常驻唤醒流程

```
┌──── 后台常驻循环 ────────────────────────────────────┐
│                                                       │
│  VoskKwsService.loop()                                │
│    │                                                  │
│    ├─ 创建 AudioRecord (16k, mono, VOICE_RECOGNITION) │
│    ├─ 创建 Vosk Recognizer + grammar 限制词表          │
│    │                                                  │
│    ├─ 循环读取麦克风数据                                │
│    │   ├─ acceptWaveForm → getResult/getPartialResult │
│    │   └─ extractText(json)                           │
│    │                                                  │
│    ├─ 两段式容错：                                     │
│    │   "嗨" + "小奇" (1300ms 窗口内) → 触发唤醒        │
│    │                                                  │
│    ├─ 单词匹配：                                       │
│    │   matchWakeWord(text, wakeWords)                 │
│    │   支持等值/前缀/反向前缀匹配                       │
│    │                                                  │
│    └─ 命中 → cooldown 检查 → listener.onWakeWord()    │
│                                                       │
└───────────────────────────────────────────────────────┘
```

**KWS 与录音互斥管理**：
- 唤醒后立即 `kws.stop()` → 开始录音
- 录音结束后 `kws.start()` → 恢复常驻监听
- 部分设备不支持两个 AudioRecord 并发，必须互斥

### 4.3 UI 状态机

```
                    ┌──────────┐
        cancel/     │   IDLE   │  assistant_final
      disconnect◄───┤  空闲    ├────────────────┐
                    └────┬─────┘                │
                         │ 唤醒(KWS/手动)        │
                         ▼                      │
                    ┌──────────┐                │
                    │  AWAKE   │                │
                    │  已唤醒   │                │
                    └────┬─────┘                │
                         │ TTS "你好主人" done   │
                         ▼                      │
                    ┌──────────┐                │
                    │LISTENING │                │
                    │  录音中   │                │
                    └────┬─────┘                │
                         │ 静音超时/VAD stop     │
                         ▼                      │
                    ┌──────────┐                │
                    │PROCESSING│                │
                    │  处理中   ├────────────────┘
                    └──────────┘
```

### 4.4 录音 + VAD 流程

```
startRecording()
    │
    ├─ 停止 TTS 播报
    ├─ 暂停 KWS
    ├─ 设置状态 → LISTENING
    │
    └─ AudioRecorder.start(chunkSize=640 bytes)
         │  640 bytes = 320 samples @ 16bit mono = 20ms 帧
         │
         ▼
    每帧回调 onAudioChunk(chunk, len):
         │
         ├─ VAD 检测: vad.isSpeech(chunk)
         │
         ├─ 有语音 → 
         │    ├─ 首个语音帧: startTurn() + 补发 pre-roll 帧
         │    └─ wsClient.sendAudio(chunk)
         │
         ├─ 无语音 + 未开始 → 积累 pre-roll (最多 12 帧 ≈ 240ms)
         │
         └─ 无语音 + 已开始 + 静音超时(900ms) → stopRecording()
              │
              ├─ recorder.stop()
              ├─ wsClient.stopTurn()
              └─ 状态 → PROCESSING
```

### 4.5 WebSocket 客户端

**`VoiceWsClient`** — OkHttp WebSocket 封装：

| 方法 | 发送消息 | 说明 |
|------|----------|------|
| `startTurn()` | `{"type":"start","client_msg_id":"..."}` | 开始新轮次 |
| `sendAudio(chunk)` | `{"type":"audio","seq":N,"data_b64":"..."}` | 发送 Base64 音频帧 |
| `stopTurn()` | `{"type":"stop","client_msg_id":"..."}` | 结束录音 |
| `cancelTurn()` | `{"type":"cancel","client_msg_id":"..."}` | 取消当前轮次 |

### 4.6 服务端消息处理

`MainActivity.handleServerMessage()`:

| type | 处理方式 |
|------|----------|
| `asr_partial` / `asr_interim` | 更新 ASR 显示文本 |
| `asr_final` | 更新最终识别文本 |
| `tool_call` | 日志记录工具调用 |
| `tool_result` | 日志记录工具结果 |
| `wakeup_detected` | 日志记录 |
| `assistant_final` | 日志记录 + 状态 → IDLE |

---

## 5. 消息协议

### 5.1 客户端 → 服务端

| type | 字段 | 说明 |
|------|------|------|
| `start` | `client_msg_id`, `mode`, `lang` | 开始语音轮次 |
| `audio` | `client_msg_id`, `seq`, `data_b64` | Base64 编码 PCM16 音频帧 |
| `stop` | `client_msg_id` | 结束并触发 ASR→NLU→Tool |
| `cancel` | `client_msg_id` | 取消当前轮次 |
| `ping` | — | 心跳 |

### 5.2 服务端 → 客户端

| type | 字段 | 说明 |
|------|------|------|
| `ready` | `conversation_id`, `trace_id` | 连接就绪 |
| `asr_partial` | `client_msg_id`, `text`, `is_final=false` | ASR 中间结果 |
| `asr_final` | `client_msg_id`, `text`, `is_final=true` | ASR 最终结果 |
| `wakeup_detected` | `client_msg_id`, `text` | 检测到唤醒词 |
| `assistant_delta` | `client_msg_id`, `text` | 助手流式回复片段 |
| `assistant_final` | `client_msg_id`, `text` | 助手最终回复 |
| `tool_call` | `client_msg_id`, `name`, `args` | 工具调用请求 |
| `tool_result` | `client_msg_id`, `name`, `ok`, `result` | 工具调用结果 |
| `error` | `client_msg_id`, `trace_id`, `code`, `message`, `retryable` | 错误信息 |
| `pong` | — | 心跳响应 |

---

## 6. 配置项说明

### 6.1 服务端配置（`application.yml` / 环境变量）

| 配置路径 | 环境变量 | 默认值 | 说明 |
|----------|----------|--------|------|
| `myassistant.asr.provider` | `MYASSISTANT_ASR_PROVIDER` | `vosk` | ASR 提供商：mock/iflytek/vosk |
| `myassistant.asr.vosk-model-path` | `MYASSISTANT_VOSK_MODEL_PATH` | `models/vosk-model-small-cn-0.22` | Vosk 模型目录 |
| `myassistant.wakeup.enabled` | `MYASSISTANT_WAKEUP_ENABLED` | `true` | 是否启用唤醒词 |
| `myassistant.wakeup.wake-word` | `MYASSISTANT_WAKE_WORD` | `嗨 小奇` | 唤醒词 |
| `myassistant.wakeup.wake-aliases` | — | `[嗨小齐, 小齐]` | 唤醒别名列表 |
| `myassistant.wakeup.dedicated-path` | `MYASSISTANT_WAKEUP_DEDICATED_PATH` | `true` | 是否启用 Vosk grammar 专用唤醒链路 |
| `myassistant.auth.enabled` | — | `false` | 是否启用 Bearer Token 鉴权 |
| `myassistant.auth.static-bearer-token` | — | `dev-token` | 静态 token |
| `myassistant.iflytek.app-id` | `MYASSISTANT_IFLYTEK_APP_ID` | — | 讯飞应用 ID |
| `myassistant.iflytek.api-key` | `MYASSISTANT_IFLYTEK_API_KEY` | — | 讯飞 API Key |
| `myassistant.iflytek.api-secret` | `MYASSISTANT_IFLYTEK_API_SECRET` | — | 讯飞 API Secret |
| `myassistant.iflytek.asr-ws-url` | `MYASSISTANT_IFLYTEK_ASR_WS_URL` | `wss://iat.cn-huabei-1.xf-yun.com/v1` | 讯飞 ASR WebSocket 地址 |

### 6.2 客户端常量

| 常量 | 值 | 说明 |
|------|-----|------|
| `CHUNK_SIZE` | 640 bytes | 录音帧大小（320 samples × 2 bytes） |
| `VAD_SILENCE_STOP_MS` | 900ms | 静音超时自动停止 |
| `PREROLL_FRAMES` | 12 帧 (≈240ms) | Pre-roll 缓冲帧数 |
| KWS cooldown | 1800ms | 唤醒冷却时间 |
| `PENDING_HI_WINDOW_MS` | 1300ms | 两段式唤醒容错窗口 |

---

## 7. 现有问题与可优化点

> **重要**：以下优化点已按优先级排序，建议迭代实现。

### 7.1 服务端

| # | 分类 | 问题描述 | 建议 |
|---|------|----------|------|
| 1 | **架构** | `VoiceWebSocketHandler.handleTextMessage()` 方法过长（约 200 行），`stop` case 分支承担了 ASR、唤醒、NLU、工具分发所有逻辑 | 拆分为独立 Pipeline 处理类，例如 `VoicePipeline`，让 handler 只负责消息路由 |
| 2 | **架构** | `decodeWavIfNeeded` / `decodeWav` 等 WAV 解析逻辑放在 WebSocket Handler 中违反单一职责 | 抽取为 `AudioDecoder` 工具类 |
| 3 | **可靠** | `VoiceSessionState.audioBuffer` 使用 `ByteArrayOutputStream` 无大小上限，恶意或异常客户端可导致 OOM | 增加最大 buffer 大小限制（如 5MB），超限返回错误 |
| 4 | **可靠** | `handleBinaryMessage` 中 catch 块完全吞掉异常（`catch (Exception ignored) {}`），问题不可追踪 | 至少增加 debug 级别日志 |
| 5 | **功能** | NLU 仅有规则匹配 + 一个工具（reminder.create），缺乏扩展性 | 设计插件化的意图注册机制，支持动态添加 skill |
| 6 | **功能** | 服务端只做一次性 ASR 转写（`transcribe`），不支持流式 partial 返回 | 实现流式 ASR 接口，逐帧返回 partial 结果（Vosk/讯飞均支持 partial） |
| 7 | **可靠** | `IflytekAsrService` 利用 `CountDownLatch` + `ws.cancel()` 控制超时，但 `ws.cancel()` 后 OkHttp WebSocket 可能未立即释放连接 | 增加显式 close 逻辑和连接池监控 |
| 8 | **可靠** | `InMemoryToolDispatcher` 只做内存存储，提醒数据不持久化，重启丢失 | 引入简单持久化（如 SQLite/H2 文件存储） |
| 9 | **功能** | `RuleBasedNluService` 中文数字解析仅支持 0-9999，无法处理"一万"等 | 扩展数字解析范围或引入专业 NLP 分词 |

### 7.2 Android 客户端

| # | 分类 | 问题描述 | 建议 |
|---|------|----------|------|
| 1 | **架构** | `MainActivity` 过于庞大（697 行），承担了 UI、状态机、VAD、TTS、KWS、WebSocket 回调等所有职责 | 按职责拆分为多个 Manager 类（`RecordingManager`、`KwsManager`、`TtsManager`、`UiStateManager`） |
| 2 | **可靠** | `VoskKwsService` 和 `AudioRecorder` 共享麦克风，当前通过手动 stop/start 互斥，缺乏统一调度 | 引入 `AudioFocusManager` 统一管理麦克风占用，支持优先级和队列 |
| 3 | **可靠** | `AudioRecorder.read()` 返回负值（如 `ERROR_INVALID_OPERATION`）时直接 `break`，不尝试恢复 | 增加重试/重连机制，区分可恢复错误和致命错误 |
| 4 | **功能** | TTS 播报结果未与 UI 状态联动，`assistant_final` 仅记录日志不做播报 | 实现 TTS 播报 + UI 状态退出联动 |
| 5 | **体验** | `floatOrb` 使用 `windowManager.addView` 需要 `SYSTEM_ALERT_WINDOW` 权限才能全局显示 | 改用 `TYPE_APPLICATION_PANEL` 仅应用内显示，或提示用户授权悬浮窗 |
| 6 | **可靠** | `VoskKwsService.matchWakeWord()` 允许前缀匹配和反向前缀匹配（partial 至少 2 字即触发），可能增加误唤醒率 | 增加置信度阈值或仅对 final 结果做精确匹配 |
| 7 | **体验** | VAD 初始化失败时回退为 `isSpeech = true`（始终有声），导致无 VAD 门控、静音不会自动停止 | 提示用户 VAD 初始化失败并提供手动停止按钮 |
| 8 | **功能** | ASR 显示的 UI 刷新直接在 `handleServerMessage` 中做字符串拼接，未考虑流式 partial 的逐字更新体验 | 实现 Typewriter 打字机效果展示 partial 文本 |

### 7.3 通用

| # | 分类 | 问题描述 | 建议 |
|---|------|----------|------|
| 1 | **工程** | 无 CI/CD 配置、无自动化测试 | 添加 GitHub Actions + 基础单元测试 |
| 2 | **工程** | 服务端使用传统 `javax.servlet`（Spring Boot 2.x），已停止维护 | 迁移至 `jakarta.servlet`（Spring Boot 3.x） |
| 3 | **文档** | 缺少各模块的 Javadoc / KDoc 注释 | 为核心接口和关键方法补充文档注释 |

---

## 8. 文件索引

### 服务端（`server/src/main/java/com/myassistant/server/`）

| 文件 | 职责 |
|------|------|
| `MyAssistantServerApplication.java` | Spring Boot 入口 |
| `config/MyAssistantProperties.java` | 配置属性映射（`@ConfigurationProperties`） |
| `config/VoskModelConfiguration.java` | Vosk 模型 Bean 加载 |
| `config/VoskWakeGrammarConfiguration.java` | Vosk Grammar 唤醒 Bean 配置 |
| `config/WebMvcConfig.java` | 拦截器注册 |
| `config/AuthInterceptor.java` | Bearer Token 鉴权拦截器 |
| `http/HealthController.java` | 健康检查端点 |
| `ws/VoiceWebSocketHandler.java` | **核心**：WebSocket 消息处理 + 链路编排 |
| `ws/VoiceWebSocketConfig.java` | WebSocket 端点注册 |
| `ws/VoiceMessage.java` | 消息体 DTO + 工厂方法 |
| `ws/VoiceSessionState.java` | 会话状态（音频 buffer + 轮次状态） |
| `service/asr/AsrService.java` | ASR 服务接口 |
| `service/asr/MockAsrService.java` | Mock ASR 实现 |
| `service/asr/VoskAsrService.java` | Vosk 离线 ASR 实现 |
| `service/asr/IflytekAsrService.java` | 讯飞云 ASR 实现（含签名鉴权） |
| `service/asr/VoskWakeGrammarRecognizer.java` | Vosk grammar 唤醒专用识别 |
| `service/wakeup/WakeWordService.java` | 唤醒词服务接口 |
| `service/wakeup/RuleBasedWakeWordService.java` | 规则匹配唤醒词 |
| `service/wakeup/NoopWakeWordService.java` | 禁用唤醒（始终通过） |
| `service/wakeup/WakeWordResult.java` | 唤醒检测结果 DTO |
| `service/wakeup/WakeGrammarRecognizer.java` | Grammar 唤醒接口 |
| `service/llm/NluService.java` | NLU 服务接口 |
| `service/llm/NluResult.java` | NLU 结果 DTO |
| `service/llm/RuleBasedNluService.java` | 规则 NLU（笑话/提醒/闲聊） |
| `service/tool/ToolDispatcher.java` | 工具分发接口 |
| `service/tool/InMemoryToolDispatcher.java` | 内存工具分发（reminder.create） |
| `service/tool/ToolResult.java` | 工具执行结果 DTO |

### Android 客户端（`android/app/src/main/java/com/myassistant/android/`）

| 文件 | 职责 |
|------|------|
| `MainActivity.java` | **核心**：Activity 入口 + 状态机 + 回调编排 |
| `VoiceWsClient.java` | WebSocket 客户端封装 |
| `AudioRecorder.java` | PCM16 录音器 |
| `SimpleVad.java` | 基于 RMS 的简单 VAD（备用方案） |
| `kws/VoskKwsService.java` | Vosk 关键词唤醒服务 |
| `ui/VpaOrbView.java` | 浮动动画球（唤醒态视觉反馈） |
| `ui/ResultOverlayDialog.java` | 结果展示弹窗 |
| `ui/TypewriterTextView.java` | 打字机效果 TextView |

---

> **本文档将持续迭代更新，每次重大变更请在上方版本记录中追加条目。**
