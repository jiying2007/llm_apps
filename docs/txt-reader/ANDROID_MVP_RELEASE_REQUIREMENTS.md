# 净读 TXT Android MVP 与正式发布需求基线

更新日期：2026-08-02

## 1. 目标与边界

### MVP Beta

面向受控测试用户交付可安装版本，用户可以在不接触“W0/技术原型”概念的情况下完成：导入本地 TXT、阅读与恢复、搜索/目录/书签、乱码诊断、净读规则、自动滚动和系统 TTS。测试版本必须有明确版本、隐私说明、反馈入口说明、升级/回退策略和已知限制。

### Production

在 MVP Beta 的真实设备与用户证据基础上，形成可上传 Google Play 的签名 Android App Bundle，并关闭应用身份、签名托管、数据安全、隐私政策、内容分级、商店素材、测试轨道、开发者验证、崩溃/ANR、可访问性和发布回滚 Gate。

### 非目标

- 当前阶段不同时承诺 Harmony 正式版；Android 达到发布 Gate 后再复用共享核心推进 Harmony；
- 不增加内容源、账号、云同步、广告、分析 SDK 或应用内支付；任何一项都会使隐私与数据安全声明重新评审；
- 不在 W0 Gate 未关闭时引入后台常驻朗读、通知栏控制、自定义字体商店或多格式阅读；
- 不自动创建、上传或保管生产签名密钥，不自动登录 Play Console，不自动发布测试或正式轨道。

## 2. 需求包与验收

| ID | 优先级 | 需求包 | MVP Beta 验收 | Production 验收 |
|---|---|---|---|---|
| A1 | P0 | 应用身份 | 去除 W0 用户文案；显示产品名和语义版本 | Owner 固定唯一 `applicationId`、开发者展示名；2026-09-30 前完成身份与包名注册 |
| A2 | P0 | 可安装工件 | 受控签名 APK/AAB，可覆盖升级且保留书架数据 | 使用独立上传密钥签名 AAB并加入 Play App Signing；密钥不入库 |
| A3 | P0 | 核心闭环 | 中端手机完成导入→阅读→退出→恢复→净读→撤销 | release 变体在目标手机和平板通过回归，无阻断崩溃/ANR |
| A4 | P0 | 隐私与安全 | 应用内可访问隐私说明；无网络/存储敏感权限 | 托管公开隐私政策；Data safety 与代码/SDK一致；每次依赖变化复审 |
| A5 | P0 | Android 兼容 | API 26–36 构建；API 35/36 edge-to-edge 不遮挡核心操作 | target API 满足提交日政策；手机/平板、深浅主题、旋转和大字体通过 |
| A6 | P1 | 可用性 | 阅读区可一键扩展，复杂控制面板可收起；TalkBack 可识别主要动作 | 无阻断可访问性问题，商店截图与真实 UI 一致 |
| A7 | P1 | 质量门禁 | 核心、lint、debug APK、release AAB 自动门禁 | 签名、包名、版本递增、R8、权限、AAB、release 设备回归全部通过 |
| A8 | P1 | 测试运营 | 测试说明、反馈模板、已知问题、回退版本齐全 | 若账号适用，至少 12 名测试者连续加入封闭测试 14 天并申请生产权限 |
| A9 | P1 | 商店材料 | 草拟中文标题、短描述、长描述和截图清单 | 图标 512×512、特性图 1024×500、至少 2 张合规截图、内容分级和目标受众完成 |
| A10 | P1 | 数据兼容 | 同一包名升级保留现有书架/profile；失败可回退 | 正式包名首次发布后不可变；格式升级必须继续读取已发布版本数据 |

## 3. 当前事实

- 已确认并统一 `applicationId`/namespace 为 `com.junchen.jingdu`；Debug 使用 `.debug` 后缀与 Release 共存；
- targetSdk/compileSdk 已为 36，满足 2026-08-31 起普通手机新应用需 target API 36 的已知政策；
- 当前没有 `INTERNET`、广泛存储或其他敏感权限，应用不集成第三方 SDK；
- 已生成本地 RSA 4096 上传密钥，签名 Release APK/AAB 和 Store 构建门禁通过；密钥尚需 Owner 离线备份并在 Play App Signing 注册；
- 核心与构建证据充分，但最新功能缺 Android 真机运行、长期 TTS、低空间/强杀和 TalkBack 证据；
- Google Play 从 2021-08 起要求新应用使用 AAB；所有发布应用必须填写 Data safety，即使不收集数据也需提供隐私政策；
- 2023-11-13 后创建的个人开发者账号，在申请生产权限前通常需要至少 12 名测试者连续加入封闭测试 14 天；
- 2026-09-30 起 Play 包名需要符合 Android developer verification 注册要求。

## 4. Owner 必须确认的不可逆信息

1. 正式包名候选（建议先做 Play Console/商标冲突检查，发布后不再改变）；
2. 开发者账号类型、创建日期、开发者展示名和法定主体；
3. 首发市场：Google Play 全球/特定地区，是否包含中国大陆其他商店；
4. 支持邮箱、隐私政策公开 HTTPS 地址和删除/反馈联系渠道；
5. 上传密钥责任人、离线备份位置和 Play App Signing 选择；
6. MVP 测试设备、测试用户名单和反馈收集方式；
7. 是否继续坚持零网络、零账号、零分析 SDK；默认答案为“是”。

这些信息缺失不阻断本地签名产物验证，但阻断“真机通过”“已上架”和“正式发布完成”声明。

## 5. 分阶段 Gate

### G1：MVP code-ready

- 产品文案、隐私入口、控制面板收起和系统栏安全区完成；
- 核心测试、debug/release lint、R8、APK/AAB 构建通过；
- 权限负向检查通过，测试说明与已知问题齐备。

### G2：MVP device-ready

- 至少一台 API 35/36 手机完成核心闭环、强杀恢复、TalkBack 和 100MiB；
- 至少一台不同厂商/系统版本设备验证 TTS；
- blocker/major 关闭，形成可发给测试用户的签名工件。

### G3：Store-ready

- 固定并注册包名、上传密钥和 Play App Signing；
- release AAB、商店素材、隐私 URL、Data safety、内容分级、目标受众与账号验证完成；
- 满足适用的封闭测试周期，生产回滚包和发布说明齐备。

## 6. 风险与回退

- 正式包名或签名选错会破坏升级链：在 Owner 书面确认前保持占位身份，不发布生产轨道；
- R8 可能删除运行路径：release 变体必须执行与 debug 等价的设备回归；
- 生产签名构建必须使用 `--no-configuration-cache`，避免签名密码被复制到 Gradle 配置缓存；R8 `mapping.txt` 必须随每个发布版本安全归档；
- 系统 TTS 可能由第三方引擎联网处理正文：隐私说明必须告知用户由所选系统引擎负责，默认优先标识离线音色；
- 没有真机证据时只能声明 `code-ready/device-pending`；
- 若正式视觉与当前功能密度无法通过早期用户测试，回到信息架构重构，不用更多按钮掩盖问题。

## 7. 官方依据

- Target API：https://support.google.com/googleplay/android-developer/answer/11926878
- Android App Bundle 与发布准备：https://developer.android.com/studio/publish/
- Play App Signing：https://support.google.com/googleplay/android-developer/answer/9842756
- Data safety：https://support.google.com/googleplay/android-developer/answer/10787469
- User Data / Privacy Policy：https://support.google.com/googleplay/android-developer/answer/10144311
- 新个人账号测试要求：https://support.google.com/googleplay/android-developer/answer/14151465
- 包名注册与开发者验证：https://support.google.com/googleplay/android-developer/answer/16984799
- 商店预览素材：https://support.google.com/googleplay/android-developer/answer/9866151
