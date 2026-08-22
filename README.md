# llm_apps

Android + Harmony 长期产品仓库。当前主产品为 **净读 TXT**。

## 终态架构

- `core/`：平台无关业务、文本处理、索引、锚点、恢复与 TTS 会话契约；唯一业务语义 SSOT。
- `apps/android/`：Android 原生 UI 与 Android 平台适配器。
- `apps/harmony/`：HarmonyOS 原生 ArkUI/ArkTS UI 与平台适配器。
- `docs/`：产品、架构、验证和发布证据。

共享范围只包含业务与算法，不共享 UI。Android 与 Harmony 分别使用原生 UI，以保证阅读交互、无障碍、生命周期、性能和平台能力长期可控。

## 当前 Gate

Android 已有签名候选与 API 36 模拟器证据；Harmony 与双端真机仍需 SDK/设备环境验证。任何平台在真机、发布控制台与合规 Gate 未关闭前，不得声明 Production Done。

详见 `docs/architecture/ANDROID_HARMONY_TERMINAL.md`。
