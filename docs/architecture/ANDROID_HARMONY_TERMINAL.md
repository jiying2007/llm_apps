# Android + Harmony 长期终态架构

## 决策

长期终态采用：

- Android：原生 Kotlin/Java UI + Android 平台适配器；
- HarmonyOS：原生 ArkUI/ArkTS UI + Harmony 平台适配器；
- 共享业务与算法：C++17 `core/native`；
- Android 通过 JNI 调用共享 Core；
- Harmony 通过 Node-API 调用共享 Core；
- UI、生命周期、文件选择、TTS、音频焦点、无障碍、系统权限均保持平台原生。

不采用共享 UI 框架作为产品主路径；不把 Harmony 长期能力绑定到第三方 Kotlin 编译器分支。

## 为什么是 C++ Core

Android NDK/CMake/JNI 与 Harmony Native/Node-API 都是平台长期支持路径。共享层只暴露稳定 C ABI，避免 C++ ABI、Kotlin/ArkTS 对象模型或平台生命周期泄漏到边界。

## Core 边界

最终必须进入共享 Core：

1. 编码探测与流式解码；
2. 文本规范化、源/派生 revision；
3. 索引、窗口读取、章节与全文搜索；
4. 原文/派生投影与锚点映射；
5. 净读规则、候选、预览、应用与规则包；
6. CRC/版本化 profile codec；
7. 删除/导出恢复日志状态机；
8. 容量回收策略；
9. 自动滚动、睡眠定时和 TTS 队列的纯状态机部分。

禁止进入共享 Core：

- Android `Context`、URI、Room、SharedPreferences；
- Harmony `UIAbility`、ArkUI、Preferences；
- Android TTS / Harmony Speech Kit 实例；
- 音频焦点、耳机、通知、窗口、无障碍实现；
- 平台线程/Looper/TaskPool 类型。

## 迁移策略

现有 `core/src` Java 实现作为行为基线，不再称 prototype。迁移按“黄金向量 + 双实现差分”进行：

1. 固化 Java Core 测试向量与文件夹具；
2. 每迁移一个子域，在 C++ 实现同一输入/输出契约；
3. CI 同时执行 Java baseline 和 C++ native；
4. Android JNI 接入该子域后，删除 Android 对对应 Java 实现的生产调用；
5. Harmony Node-API 接入同一 C ABI；
6. 双端真机行为一致后，删除对应 Java baseline；
7. 所有子域完成后移除 `core/src/main/java`，仅保留迁移审计证据。

禁止长期保留两套可写业务实现。

## 平台工程

### Android

`apps/android` 继续保持 minSdk 26 / targetSdk 36。Activity/ReaderSurface 只承担 UI 与平台桥接，业务状态逐步拆入 presentation 层，并通过 JNI façade 调用 Core。

### Harmony

`apps/harmony` 使用 Stage 模型、ArkUI/ArkTS。Native module 使用 CMake 链接 `core/native`，通过 Node-API 暴露最小函数集。文件访问、TTS、后台行为和系统能力均在 ArkTS 侧适配。

## ABI 规则

- 对外仅 C ABI；
- 所有 struct 明确版本和长度；
- 不跨 ABI 传 STL、异常、裸对象指针所有权；
- 字符串统一 UTF-8 + pointer/length；
- 大对象使用 opaque handle；
- 每个 handle 必须提供 destroy；
- 错误使用稳定 error code + 可选诊断文本；
- ABI major 不兼容变更必须显式升级。

## Gate

### G0 Architecture

- 无 `prototype/` 生产目录；
- Android 生产代码只消费正式 `core/`；
- C++ Core 能在 host CI 编译并 smoke；
- Harmony 工程边界和 bridge 契约固定。

### G1 Core parity

- Java baseline 与 C++ Core 的黄金向量 100% 一致；
- 10/100/300MiB 基准无数量级退化；
- fuzz/损坏输入不得崩溃或越界。

### G2 Android native-core

- Android 生产链不再依赖 Java baseline；
- Debug/Release lint、R8、APK/AAB、JNI instrumentation 通过；
- API 26–36 和至少两家 OEM 真机通过。

### G3 Harmony native-core

- HAP 编译、签名与真机安装通过；
- 文件导入、阅读、索引、净读、TTS、恢复主链通过；
- 至少两种 Harmony 设备/系统版本矩阵通过。

### G4 Cross-platform release

- 同一黄金语料的核心结果一致；
- 双端发布身份、签名、隐私、商店材料与回滚闭环；
- 生产分支禁止绕过 required checks。

只有 G0–G4 全部关闭才允许声明 Android + Harmony Production Done。
