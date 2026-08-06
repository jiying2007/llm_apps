# W0 平台与工具链能力矩阵

更新日期：2026-08-02

## 当前环境

| 项目 | 结果 | 证据/影响 |
|---|---|---|
| Java/Javac | OpenJDK 17.0.15，已安装 | 满足 AGP 9.0.1；Java 8 核心原型也已验证 |
| Gradle | Wrapper 9.1.0，已校验 SHA-256 | 匹配 AGP 9.0.1 官方最低/默认版本 |
| Kotlin compiler | 没有独立安装 | 当前 Android W0 使用 Java；KMP/Kuikly 仍未验证 |
| Android SDK | Command-line Tools 22.0、API 36、Build Tools 36.0.0、Platform Tools 37.0.1 | 可构建 targetSdk 36 APK |
| Android 模拟器 | Emulator 37.1.11、API 36 Google APIs x86_64 | APK 安装、SAF、ReaderSurface、自动滚动、TTS smoke 已通过 |
| Android 真机 | 未提供 | 性能、功耗、系统 TTS 差异仍需真机矩阵 |
| Harmony `ohpm` | 不可用 | 无法解析 Harmony 依赖 |
| Harmony `hvigor` | 不可用 | 无法构建 HAP |
| Harmony 真机/模拟器 | 未发现 | Reader Kit/Core Speech Kit 无法实测 |
| Node.js | v22.23.1，可用 | 不能替代 Harmony SDK 与 hvigor |

## Major Gate 状态

| Gate | 状态 | 本轮可交付证据 | 仍需条件 |
|---|---|---|---|
| M1 ReaderSurface/Kuikly | partial-pass | Android 原生 `ReaderSurfaceView` 构建并在 API 36 模拟器运行；外观 v2 提供三主题、三种系统字体、字号/行高/0–12dp 段距/边距、旧契约迁移、系统字体缩放、真实行顶段距、重排锚点恢复、三种方向策略、Unicode 安全换行、可滚动控制区与有界 TalkBack 描述；自动滚动调速/恢复/常亮核心契约通过 | 最新外观/伴读 UI、字体实际字形/段距视觉、大字体/方向重建/TalkBack、真实刷新率/触摸暂停/倒计时与常亮/耐久运行复验；Kuikly/KMP、Harmony ReaderSurface、双端真机 |
| M2 大文件与净读性能 | host-pass/device-pending | 导入 10/100/300MB；磁盘索引 100MB 6.54s/RSS 146.7MiB；v2 压缩投影/候选索引下净读 100MiB 全选/排除 20 处 4.46s/4.45s、300MiB 11.89s；投影 2.9/8.6MiB、候选 2.8/8.5MiB，`-Xmx128m` 无 OOM；深页 9.1/12.3ms | Android/Harmony release 真机复测；配额设备数据 |
| M3 TTS 与后台行为 | android-partial-pass | Android 系统 TTS 启动、范围推进、停止均通过；自动滚动/TTS 互斥、共享睡眠定时、有界分段连续队列、暂停锚点恢复、前后段、AudioFocusRequest 与 noisy 输出暂停已构建；分段/陈旧回调/Unicode/时间与章节边界通过核心测试 | 最新分段 TTS/睡眠定时 UI、跨窗口续播、来电/焦点/蓝牙/有线耳机运行复验；后台/进程死亡/30 分钟耐久；Harmony Core Speech Kit；真机矩阵 |
| M4 锚点与索引正确性 | core-and-android-partial-pass | 完整磁盘索引与双向映射通过；单规则 Android 闭环运行通过；选择一致性、校验候选索引、完整命中分页、范围边界、规则包/导出恢复、多本书架/独立隐藏状态/按书书签/阅读外观/删除日志 CRC 与边界门禁通过核心测试；乱码导航夹具固定 130 个异常、前 128 个位置和跨三个阅读窗口的预期清单；书签及乱码原文锚点可映射到派生 revision；显示重排与尺寸变化按字符锚点恢复已构建 | 最新 APK 乱码前后导航/128 边界/净读映射/TalkBack、多书/强杀/书签跨 revision/显示重排与旋转锚点/移出恢复/删除中断运行复验、DocumentsProvider 部分写入/回读兼容、设备进程杀死/低空间、双端真机 |
| M5 用户验证 | pending | 无 | 访谈和封闭测试 |

## 工具链恢复路径

1. 配置 DevEco Studio/HarmonyOS NEXT SDK、`ohpm` 与 `hvigor`；
2. 提供至少一台中端 Android 和一台 HarmonyOS NEXT 8GB 真机；
3. 核对 Reader Kit、Core Speech Kit 和 Kuikly 的版本/账号条件；
4. 在 Harmony 工具链与设备就绪后继续 M1、M3，并在双端复跑 M2、M4；
5. 消除主机 `/usr/bin/adb` 1.0.39 与 Android SDK ADB 1.0.41 的版本冲突。

Android 工具链安装均经过用户授权并使用官方来源、官方校验值。Harmony 官方下载端点在当前非登录环境返回 HTTP 403；未使用第三方镜像替代，也未伪造 Harmony 构建证据。
