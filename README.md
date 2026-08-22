# 净读 TXT

**离线、隐私优先的本地 TXT 长文本阅读器。** 重点不是支持最多格式，而是把乱码文本打开正确、把干扰内容清干净，并让长时间阅读保持舒服、稳定和快速。

> Open messy TXT correctly, clean distracting text locally, and keep reading comfortably for hours.

## Product

- **本地优先**：无账号、无广告、无网络权限，TXT 内容不上传。
- **中文 TXT 深度支持**：AUTO 编码检测覆盖 UTF-8 / UTF-16 / GB18030 / GBK / Big5，并允许基于已保存的私有 source 副本重新解码。
- **净读**：按书保存字面清理规则，原文/净读预览独立，支持导出新的净读 TXT，永不修改源文件。
- **大文件可信**：导入、打开/索引、搜索、目录、净读生成和导出均不在 UI 线程执行；10/100/300 MiB 是正式验证尺寸。
- **长期阅读**：书架/进度、全文搜索、目录、revision-safe 书签、Material 3 排版、纸张/明亮/夜间主题、系统 TTS、自动翻页和睡眠定时。
- **自适应 Android UI**：Kotlin + Jetpack Compose Material 3，edge-to-edge，手机/横屏/平板/折叠屏保持合适行长和信息层级。

产品范围、非目标和成功指标见 [`docs/PRODUCT.md`](docs/PRODUCT.md)，Android 交互/视觉规范见 [`docs/UX.md`](docs/UX.md)。

## Architecture

```text
Android Compose / Kotlin platform shell -- JNI -----+
                                                     +-- C ABI v2 -- C++17 Jingdu Core
HarmonyOS ArkUI / ArkTS platform shell ----- NAPI --+
```

- `core/native/` — 唯一跨平台文本/算法实现，稳定 C ABI v2。
- `apps/android/` — Compose Android 产品壳、平台能力和 JNI bridge。
- `apps/harmony/` — HarmonyOS Stage/ArkUI 产品壳和 Node-API bridge。
- `docs/` — 产品、UX、架构、ABI、数据、编码、性能、测试和发布的规范事实源。
- `scripts/` — 本地/CI 强制门禁。

## Product invariants

- 外部 TXT 永不修改，导入只创建 app-private source copy；
- `book id == SHA256(source bytes)`；
- normalized/clean 文件是不可变 content-addressed revision；
- 所有 post-normalization read/search/chapter/repair/speech 语义只来自同一个 C++ Core；
- 原文 progress/bookmark 不接收净读派生 offset；
- 与文件规模相关的操作不运行在 UI thread；
- 不允许 Java shared core、兼容 Core、旧 ABI bridge、prototype production root 或提交 release binary。

## Gates

```bash
./scripts/check-native.sh
cd apps/android && ./gradlew --no-daemon --warning-mode all androidCheck
cd ../..
./scripts/verify-terminal.sh
```

Hosted CI 还会编译 Compose AndroidTest 并验证 Android/Harmony 架构 contract。HarmonyOS 真 HAP 仍使用官方 HarmonyOS/DevEco 工具链和 `self-hosted,harmonyos` runner；详见 `docs/HARMONY_RUNNER.md`。

## Documentation

建议阅读顺序：

1. `PRODUCT.md` — 产品定位、用户、核心任务、非目标；
2. `UX.md` — Android 信息架构、阅读体验、响应式和可访问性；
3. `ARCHITECTURE.md` / `CORE_CONTRACT.md` / `DATA_MODEL.md` — 技术与跨端事实源；
4. `ENCODING.md` / `PERFORMANCE.md` / `TESTING.md` / `DEVICE_MATRIX.md` — 质量标准；
5. `QUALITY_GATES.md` / `RELEASE.md` — 合并与发布边界。

`main` 保持可发布源码；APK/AAB/HAP、mapping/symbol package 和签名材料只属于构建/发布基础设施。
