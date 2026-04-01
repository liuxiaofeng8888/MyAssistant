把 Vosk 模型目录放到这里，然后把目录名改成 `model`。

例如（中文小模型，示例）：
- android/app/src/main/assets/model/am/final.mdl
- android/app/src/main/assets/model/conf/model.conf
- android/app/src/main/assets/model/graph/...
- android/app/src/main/assets/model/ivector/...

应用启动后会自动解包到内部存储（目录名 `vosk-model`），并开始 KWS 监听。

默认唤醒词在 `MainActivity.initKws()`：
- 你好助手
- 小助手
- 你好小助手
- 嗨助手

