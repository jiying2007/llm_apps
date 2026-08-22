# 净读 TXT Android

Android 正式应用工程。业务与算法唯一来源是仓库根目录 `core/`；本目录仅包含 Android UI、生命周期、SAF、TTS、音频焦点、无障碍和发布适配。

当前 Android 仍通过 `apps/android/core` Java module 消费 `core/src` 行为基线。长期迁移目标是 `core/native` C++17，通过 JNI façade 接入；每个子域必须先完成 Java/C++ 黄金向量差分，再删除 Java 生产调用，禁止长期双实现。

## 构建门禁

```bash
./gradlew --no-daemon androidMvpCheck
```

Store 候选：

```bash
./gradlew --no-daemon --no-configuration-cache \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=1 \
  -PjingduVersionName=1.0.0 \
  writeAndroidReleaseChecksums
```

产物写入 `dist/android/<version>/`，该目录被 Git 忽略；正式分发必须进入 Release/制品存储，不回写源码树。

## 当前事实

- API 36 / minSdk 26；
- 不申请网络或广泛存储权限；
- Release 开启 R8/资源压缩；
- Debug/Release 分包可共存；
- 现有 API 36 模拟器证据仍有效，但不替代 API 26–36、多 OEM、TalkBack、TTS、低空间和长稳真机矩阵。

跨端终态与 Gate 见 `../../docs/architecture/ANDROID_HARMONY_TERMINAL.md`。
