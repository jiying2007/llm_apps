# 净读 TXT HarmonyOS

HarmonyOS 端采用原生 ArkUI/ArkTS UI。共享业务与算法通过 `core/native` 的稳定 C ABI 暴露，Harmony Native module 使用 Node-API 向 ArkTS 提供薄封装。

本目录只接受平台实现，不复制共享业务算法。任何在 Android 与 Harmony 都需要保持一致的算法，应先进入 `core/native`。

## 必须实现的第一阶段

1. Stage 模型应用壳与页面导航；
2. Native module + CMake 链接 `../../core/native`；
3. Node-API 暴露 ABI version / import / window / search / repair / profile 等 façade；
4. 系统文件选择器导入；
5. 原生 ReaderSurface；
6. Harmony TTS/音频焦点/生命周期适配；
7. HAP release 真机矩阵。

Harmony SDK/DevEco/签名材料不得提交仓库。正式工程创建后，其生成文件需遵循本仓库 ABI 与 Gate 文档。
