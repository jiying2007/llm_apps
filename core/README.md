# 净读 TXT Core

`core/` 是 Android 与 Harmony 的唯一业务语义来源。

## 当前迁移状态

`src/main/java` 与 `src/test/java` 是已经通过大量主机/Android 证据验证的 Java 行为基线；它不再属于 prototype。`native/` 是长期共享实现，采用 C++17 与稳定 C ABI。

迁移期间必须保持：

- Java baseline 的既有测试持续通过；
- C++ 子域使用相同黄金输入/输出；
- Android 只在某子域 C++ parity + JNI 真机通过后切换该子域；
- Harmony 只通过 Node-API 调用同一个 C ABI；
- 子域双端通过后删除 Java 生产实现，不能长期维护两套业务逻辑。

最终状态是 `core/native` 为唯一跨端业务/算法 Core；Java baseline 只可作为历史迁移证据归档或删除。

完整边界、ABI 规则与 G0–G4 Gate 见 `../docs/architecture/ANDROID_HARMONY_TERMINAL.md`。
