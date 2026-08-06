# 净读 TXT 执行状态与恢复入口

## Goal Closure

- goal_statement：在既有 W0 证据上，把净读 TXT Android 推进为可交付测试用户的 MVP，并进一步关闭正式上架所需的产品化、质量、安全、合规和发布 Gate；Harmony 保持后续共享核心目标。
- completion_claim：核心导入/Big5 强证据自动候选与 GB18030 歧义保守回退/自动与手动编码覆盖/源身份与内容 revision 分离、损坏解码完整计数与前 128 个位置有界保留/profile v4 兼容读取 v1-v3、分级章节置信度/原文锚点手工目录、全文件净读、v1 兼容的 v2 分块压缩投影与候选索引、可恢复搜索索引/有界章节目录/锚点、Unicode 安全阅读选区/派生到原文范围映射/有界精确读取、逐处与全局序号范围选择、完整命中分页、规则包、多规则原子存储、带 CRC 的多本书架与独立隐藏状态、按书隔离的原文锚点书签、向后兼容的确定性阅读外观 v2/主题对比度/方向策略、连续/分页与音量键导航设置契约、跨刷新率自动滚动速度及上下文绑定恢复契约、安全删除恢复日志、安全容量回收、外部导出恢复日志、伴读睡眠定时、分段 TTS 队列、词级进度扩展句级高亮/无范围回调段落降级/独立语音跟随锚点，以及系统音色/语速/音调严格持久化契约已形成证据；Android 单规则闭环运行通过，最终 Debug/Release 已在 API 36 模拟器通过首屏冷启动，最新高风险能力的完整矩阵仍需真机复验；W0 整体仍在进行。
- required_evidence：`PRODUCT_PLAN.md`、`ANDROID_MVP_RELEASE_REQUIREMENTS.md`、`prototype/core/`、`android-prototype/`、`docs/txt-reader/w0/`、`docs/txt-reader/release/`、MVP/Store 分层门禁、真机 Beta 与 Play Console 证据。
- claimant：当前 W0 实现阶段。
- verifier：完成前验证阶段必须重跑 `w0Check` 并核对 APK、权限、运行证据和开放项。
- open_items：Harmony 工具链/设备、Android 真机、大文件设备基准、索引/投影/候选索引/外部导出低空间与进程杀死、最新 APK 长按选词/拖动扩选/跨行高亮/复制与系统剪贴板/选区全文搜索/派生选区映射原文及创建规则对话框/TalkBack/选择手柄增强、触摸连续滚动/分页吸附与步长/音量键正反向及系统音量回退/TalkBack 门禁/跨窗口手动与自动续载/旋转锚点、章节置信度/目录选择/改名/拆分/合并/重启/净读映射/重解码与删书清理、Big5/GB18030 真实歧义语料/自动置信度/手动覆盖/编码告警跨重启与损坏降级/前后乱码导航及 128 处边界/原文和净读映射/TalkBack/同源同结果复用/同源差异确认与取消/重解码强杀/删书 profile 清理、其他编码选择、多书切换/重复导入/进度强杀恢复、书签跨净读 revision 跳转及移出/恢复/删除中断运行复验、自动滚动 60/90/120Hz 实机速度/触摸暂停小于 100ms/倒计时再次触摸与跳转取消/常亮系统行为/文末停止/30 分钟耐久及设置重启恢复、配额真机数据、历史生成资产逐书归属清理、TTS 真实音色枚举/系统默认切换/音色卸载/离线与联网标记/语速音调听感和重启持久化、range/无 range 引擎、句段配色、分页/连续页面跟随、长段性能、暂停恢复、跨窗口/后台/进程死亡/来电与焦点/蓝牙与有线耳机/耐久及 TalkBack 共存、字体实际字形/段距视觉/方向锁定重建/阅读外观持久化/大字体/夜间观感运行复验、自定义字体、规则拖拽与正式视觉、早期用户验证。

## Current State

| 项目 | 状态 |
|---|---|
| 需求与定位 | complete |
| v1.1 产品方案 | complete |
| 方案评审 | conditional-pass |
| W0 技术验证 | core host-pass；Android runtime partial-pass/device-pending；Harmony toolchain/device pending |
| Android W0 原型 | complete-as-prototype：不等于产品完成 |
| Android MVP code/signing-ready | complete：`com.junchen.jingdu`、签名 APK/AAB、压缩与分层门禁通过 |
| Android MVP device-ready | partial-pass：API 36 Release 的 SAF、七编码错误恢复、全量乱码夹具、自动滚动、生命周期恢复、平台 TTS 已通过；仍需 API 26–35 与真机矩阵复验 |
| Android Production / Store-ready | pending：需要 Play Console、真机、商店截图与合规材料 |
| Android UI/UX 产品化 | active：正文优先外壳、图标底栏、品牌空状态、封面式快速书架、目录/多结果搜索底部面板、伴读迷你条、净读安全入口和简中/繁中/英文切换已通过 API 36 阶段烟测；200% 字体使用图标优先自适应并通过阅读页/书架截图门禁；繁中人工语言审校、TalkBack、窄屏和完整净读差异面板待补 |
| Harmony W0 原型 | blocked-by-environment |
| 正式开发 | Android MVP active；Production 受 Store Gate 限制 |
| 封闭测试 | pending |

## Planning Artifacts

- `docs/txt-reader/PRODUCT_PLAN.md`：产品、功能、架构、验收和路线图 SSOT。
- `docs/txt-reader/PLAN_REVIEW.md`：评审证据、评分、Major Gate 和决策。
- `docs/txt-reader/EXECUTION_STATE.md`：当前状态、恢复入口和防卡死规则。

## Checkpoints

### CP0：方案更新与评审

- owner：方案编制者；
- status：complete；
- done criteria：v1.1 纳入伴读模式、净读规则、技术架构、验收、阶段门禁和风险台账；完成评审；
- evidence：`PRODUCT_PLAN.md`、`PLAN_REVIEW.md`；
- next：确认 W0 资源并启动技术验证。

### CP1：W0 技术验证

- owner：开发 Owner（待确认）；
- status：blocked-by-environment；
- latest evidence：2026-08-02，核心 42 场景、Big5 强证据自动候选/短歧义 GB18030 保守回退/手动覆盖/64KiB 多字节采样截断与真实 EOF 门禁，单遍全文件错误恢复、完整替换计数/前 128 个严格递增源字节与规范化字符位置/BOM/CRLF/跨缓冲区边界，以及固定 130 个异常、跨三个阅读窗口、含 SHA-256 与全偏移清单的设备验收夹具；按 `bookId + baseRevision` 隔离、可读 v1/v2/v3 的 v4 CRC 原子编码诊断 profile、Android 前后乱码导航/原文→派生映射/有界窗口载入，Big5 启发式/旧编码歧义/损坏字节结构化告警、Unicode 安全选区/派生范围到原文范围映射/有界磁盘精确读取、严格连续/分页及关闭/正反向音量键导航设置契约、严格编码选择契约、自动/手动来源、源 SHA 与规范化输出 SHA 分离、分级章节置信度、20,000 项原文锚点 CRC 手工目录及活动 revision 映射、v1 兼容的 v2 分块压缩投影/候选索引、可恢复搜索索引、完整命中分页/逐处与序号范围选择、规则包、多规则存储、保持 v1 元数据兼容的独立书架隐藏状态、按书隔离的 CRC 书签 profile 与原文↔派生锚点映射、三主题/三字体/字号/行高/段距/边距的兼容迁移契约、4.5:1 对比度门禁与方向策略、8–120 dp/秒自动滚动契约、60/90/120Hz 积分一致性及 revision/锚点绑定触摸恢复状态机、删除 CRC 日志与共享资产保护、受保护资产零配额门禁、原子外部导出恢复日志、伴读睡眠定时与分段 TTS 队列、词级 range 扩展句级高亮、无 range 段落降级、独立语音跟随锚点、系统音色/语速/音调严格持久化与结构化能力契约、Unicode 安全窗口、100/300MiB 净读与随机深页基准；Android 最新 Release 已在 API 36 模拟器通过七编码 instrumentation、全量损坏 UTF-8 SAF 导入与正文完整性、自动滚动/触摸暂停、生命周期恢复及基础系统 TTS，复杂真机矩阵仍为 device-pending；
- done criteria：关闭或处置评审 M1–M4，输出 Go/No-Go；
- evidence：`prototype/core/`、`android-prototype/`、`docs/txt-reader/w0/HOST_BENCHMARK.md`、`DISK_INDEX_BENCHMARK.md`、`REPAIR_RULE_SMOKE.md`、`ANDROID_SMOKE.md`、`INDEX_SMOKE.md`、`CAPABILITY_MATRIX.md`；
- next：补 Harmony 官方工具链和双端真机；完成后输出最终 Go/No-Go，失败则 split/replan。

### CP2：早期产品验证

- owner：产品 Owner（待确认）；
- status：pending；
- done criteria：至少 15 次早期访谈，形成问题频率和切换理由证据；
- evidence：脱敏访谈摘要，不保存正文和原始会话；
- next：决定是否保持当前定位和商业模型。

### CP3：Android MVP 产品化

- owner：Android 开发；发布身份与签名由 Owner 确认；
- status：`code-and-signing-ready / device-pending / console-pending`；
- code-ready evidence：2026-08-02 强制执行 `androidMvpCheck` 的 92 项任务，核心 42 场景、Debug/Release 编译、双变体 lint、R8 压缩、Debug APK 与 Release AAB 全部通过；
- package evidence：Debug `com.junchen.jingdu.debug` APK SHA-256 `9682e5ac90f5c4e8f38b467c38c416af52dc2493c2f15a5de9631dc99257e667`；签名 Release `com.junchen.jingdu` APK SHA-256 `072e78c5341968d30c57885045551aec3060b1a6928082da7090f523b3c005a6`；签名 AAB SHA-256 `f7e6099ab7eafcc415662da163d18757c1a49a6349a972ed3cc29a724241fe6a`；target API 36，无权限声明；
- crash repair：先修复 `readerAppearance` 尚未加载时更新系统栏图标的空指针；随后在 API 36 模拟器复现第二处崩溃，堆栈定位为 `setContentView()` 前访问尚未创建的 `DecorView`/`WindowInsetsController`。系统栏图标更新已移动到内容视图创建后；最终交付 Debug 连续三次冷启动、Debug/Release 各一次交付包冷启动均进程存活且无 `AndroidRuntime` 错误；
- negative evidence：密钥生成首试被 Gradle 配置缓存拒绝，未产生密钥；改为强制 `--no-configuration-cache` 后生成。Debug 名称分离首试因 AGP 9 默认禁用 `resValues` 而失败，显式开启后通过；
- review：已修复 edge-to-edge 夜间导航栏图标对比度、刘海/键盘安全区、大字体顶栏溢出、版本号误判与签名密码配置缓存五个 Major，复审 blocker=0、major=0；
- done criteria：在真机安装新 Debug/Release，证明启动不闪退并通过 P0 矩阵；上传密钥离线备份并注册 Play App Signing；
- open items：真机复验、开发者主体/账号类型、支持邮箱、HTTPS 隐私政策、上架市场、真实截图、测试用户及 Play Console 材料。

#### CP3 Evidence Index

UI/UX checkpoint（2026-08-02）：API 36 已验证简中、繁中、英文应用语言切换；三套资源各 431 项且键集合一致；繁中资源完成 ICU 全量转换与核心台湾用语区域化，但正式商店发布前仍需母语人工审校。封面式快速书架、19 条全文搜索结果滚动浏览与位置 116 跳转、目录底部面板、净读安全入口、TTS 迷你条、正文沉浸切换均通过运行烟测。200% 系统字体首轮暴露顶栏截断、底栏拥挤和系统取消按钮裁切，改为书名省略、工具栏字号封顶、底栏图标优先及底部面板动作按钮封顶后，阅读页与书架复验通过。证据截图为 `w0/jingdu-ui-reader-zh-tw.png`、`w0/jingdu-ui-bookshelf-zh-tw.png`、`w0/jingdu-ui-search-results-multi.png`、`w0/jingdu-ui-font-200-reader.png`、`w0/jingdu-ui-font-200-bookshelf.png`。

| Command | Exit Code | Result Summary | Evidence Path | Layer | Related Artifact |
|---|---:|---|---|---|---|
| `./gradlew --no-daemon --rerun-tasks androidMvpCheck` | 0 | 92/92 任务执行；核心 42 场景、双变体 lint、R8、APK/AAB 通过 | `android-prototype/app/build/` | Workflow | Android MVP |
| `./gradlew --no-daemon --no-configuration-cache -PjingduApplicationId=com.example.jingdu -PjingduVersionCode=1 -PjingduVersionName=1.0.0 androidStoreCheck` | 1 (expected) | 正确拒绝未配置上传签名的“正式”构建 | `app/build.gradle` | Negative Gate | Store release |
| `androidStoreCheck` / `writeAndroidReleaseChecksums` | 0 | RSA 4096 上传签名 APK/AAB、R8、lint、核心 42 场景与交付目录通过 | `dist/android/1.0.0/` | Workflow | Release APK/AAB |
| `aapt dump badging/permissions` + `apksigner verify` | 0 | Debug APK 为 API 36、无权限声明、v2 调试签名 | `app-debug.apk` | Security/Package | MVP debug artifact |
| API 36 AVD + final Debug/Release cold start | 0 | Debug 连续三次冷启动；交付 Debug/Release 各一次冷启动，页面可见、进程存活、无 AndroidRuntime 错误 | `jingdu_api36` / `dist/android/1.0.0/` | Android Runtime | Device-ready partial evidence |
| API 36 AVD + Release malformed fixture | 0 | 七编码 instrumentation 通过；真实 SAF 导入统计 130 处、首偏移 2091，正文和私有副本保留合法 `<`，外部源 SHA 不变 | `jingdu_api36` / `core/build/device-smoke/` | Android Runtime/Data Integrity | Decoder compatibility |
| API 36 AVD + Release companion smoke | 0 | 自动滚动推进、触摸暂停、后台后强停恢复、Google TTS 合成均通过，无 `AndroidRuntime` 崩溃 | `jingdu_api36` | Android Runtime | MVP companion reading |

## Anti-stall

- retry_budget：同一技术假设最多 2 次实质不同的尝试；
- staleness_threshold：平台/政策 14 天，性能证据 7 天或构建变化后失效；
- heartbeat：W0 每日，开发阶段每周；
- stop_condition：仅允许 `pass`、`replan`、`split`、`blocked`、`abort`；
- escalation：计划偏差 >30%、连续 2 个检查点无新证据或相同验证连续失败 2 次时，停止执行并重审。

### Current Blocking Audit（2026-08-02）

- Android：通过 `sg kvm` 使当前会话获得已配置的 KVM 组权限，`jingdu_api36` 已可运行；最终 Debug/Release 启动 Gate 通过。API 26–35、Android 真机、OEM/TTS/功耗与大文件 Gate 仍待执行；
- Harmony：`ohpm` 与 `hvigor` 均不存在，HarmonyOS NEXT 设备/模拟器未提供；官方非登录下载路径此前返回 HTTP 403，未使用不可审计的第三方镜像；
- 产品验证：M5 需要 Owner 组织至少 15 次真实用户访谈，当前没有参与者或脱敏访谈材料；
- 范围审计：后台 Service 和通知栏仍非 MVP 必需；用户已批准 Android MVP 产品化，但真机和 Store Gate 未通过前不扩张新功能；
- 重复性：同一设备/Harmony 外部条件已在至少三个连续推进检查点保持不变，且两条模拟器实现路径均已有负证据；本轮停止条件为 `blocked`，恢复触发条件是 Android 设备出现、Harmony 官方工具链/设备就绪，或 Owner 提供早期用户验证输入。

## Next Actions

1. Owner 确认最终 applicationId、开发者主体/账号、目标市场、支持邮箱和隐私政策 HTTPS 地址；
2. Owner 在本机安全提供上传密钥配置（不在聊天或仓库中传递密码），生成非 debug 签名 MVP；
3. 提供一台中端 Android 真机，复跑 SAF、TTS、自动滚动、edge-to-edge 与 100/300MB；
4. 组织测试用户按 `release/MVP_TEST_GUIDE.md` 完成封闭测试，整理脱敏 P0/P1 结果；
5. 签名、真机与合规材料齐备后运行 `androidStoreCheck`，上传 Play 内部测试并核对 Data safety；
6. Owner 提供 HarmonyOS 官方 Command Line Tools/DevEco 环境、开发者账号和 HarmonyOS NEXT 设备；
7. 建立 Harmony ReaderSurface、Reader Kit/Core Speech Kit 原型；
8. 在 Android 真机执行 100/300MB 磁盘索引、进程杀死续建和低空间回退；
9. 复验多规则、候选索引分页/回退、批选、规则包、导出恢复与容量回收；
10. 在真机注入外部导出进程终止、Provider 不可回读和低空间，依据结果调整恢复文案与默认配额；
11. 输出 W0 Go/No-Go，并据实更新技术选型和排期。

## Resume Prompt

```text
继续净读 TXT 项目。先完整阅读 docs/txt-reader/PRODUCT_PLAN.md、
docs/txt-reader/PLAN_REVIEW.md 和 docs/txt-reader/EXECUTION_STATE.md。
当前结论是方案 conditional-pass，只批准进入 W0。
核心 42 场景、Android API 36 的 SAF/Big5 编码选择/ReaderSurface/长按选择/复制/搜索/原文映射创建规则/触摸连续滚动/分页吸附/可选音量键翻屏/跨窗口续载/自动滚动/TTS/磁盘索引，
以及单规则净读预览—应用—重启恢复—撤销已通过；校验候选索引、完整命中分页/页级批选、规则包、
编码选择/自动置信度/同源差异确认重解码、多规则管理/双向排序、原子规则存储、持久书架/重复导入复用/锚点 pending 合并/按书书签/移出恢复/副本删除续作、日间/护眼/夜间与字号/行高/边距持久设置、伴读互斥/睡眠定时/分段 TTS/音频中断、外部导出 pending/回读校验和可配置生成资产治理已构建；源 SHA 保持书籍身份，规范化输出 SHA 独立作为阅读 revision；书签固定保存原文锚点并映射到当前净读 revision；显示重排与尺寸变化按当前字符锚点恢复；`library.bin` 保持 v1 可读，隐藏状态独立保存；v2 投影/候选均保持 v1 读取兼容，100/300MiB
投影为 2.9/8.6MiB，候选为 2.8/8.5MiB，管线约 4.46s/11.89s；最后命中页读取为 9.1/12.3ms。
章节目录超过 20,000 项时显式截断，全文搜索保持完整。
下一步使用 Android 真机，安装 SHA-256 `9682e5ac…` 的 Debug APK 复验启动，再安装 SHA-256 `072e78c5…` 的 Release APK，
复验超过 128 处损坏字节的完整计数/截断提示、上一处/下一处边界、跨窗口与净读映射、重启恢复和 TalkBack，Big5 强证据自动识别、短歧义回退提示、手动 Big5 与同源重解码，系统默认/具体音色切换、离线与联网标记、音色卸载降级、50%–200% 语速音调和重启持久化，真实 TTS range 句级高亮、无 range 段落降级、分页/连续语音跟随、暂停保留/停止清除、长段性能与 TalkBack 共存，长按中英文/emoji 选词、拖动扩选与跨行高亮、精确复制、选区全文搜索、原文与派生视图创建规则/映射提示/重启持久化、TalkBack 浮动菜单，触摸连续滚动、分页步长与锚点、音量键正反向及系统音量回退、TalkBack 门禁、跨窗口手动/自动续载与停止后不幽灵重启，章节置信度显示、目录打开/改名/拆分/合并/重启、净读映射和重解码/删书清理，自动/手动编码显示、同源同结果复用、同源差异确认/取消/强杀及书签清除，两本导入/切换/重复导入/进度强杀恢复、原文与净读版本间书签添加/跳转/删除、三主题与字号/行高/边距保存重启、32sp+系统大字体、旋转锚点、控制区滚动、TalkBack 焦点与正文描述、移出/恢复后的书签保留、删除确认/中途强杀续作/重新导入、两条默认规则命中、启停/排序持久化、索引随机翻页/顺序回退、页级与自定义范围批选、规则包往返、清洗 TXT 导出/回读校验/中断恢复、分段 TTS 暂停恢复/前后段/跨窗口、睡眠定时、配额持久化/跨书活动资产保护与主动清理、
派生重启恢复、撤销与回收状态；之后执行
Android 100/300MiB、低空间/进程杀死/TTS 后台，并建立 Harmony ReaderSurface 与 Speech 原型。
不得在缺少双端真机证据时宣称架构、设备性能或 W0 整体通过。
```

## Archive Decision

本轮删除语义与降级兼容决策仍缺设备中断证据，先保留在项目 SSOT，不写长期知识候选；W0 形成双端 Go/No-Go 后再归档稳定结论。

## CP0 Historical Verification Evidence

以下内容仅记录方案阶段的验证快照，时间早于 W0 代码实现，不代表当前工作区范围。

### Scope Summary

- 本次只新增产品方案、评审报告和执行状态三个 Markdown 工件；
- 未创建应用代码、未修改参考 APK、未改变运行配置；
- 因尚无应用工程，不存在可运行的 lint、test 或 build 命令；验证范围是文档结构、需求覆盖、评审门禁和声明—证据一致性。

### Completion Claim Audit

- claimant 声明：v1.1 方案和方案评审已经完成；
- verifier 核对：三个工件存在，方案包含目标、非目标、功能、架构、验收、阶段、风险和停止条件；评审包含证据缺口、Major Gate 和限制性结论；
- 缺失证据：真机原型、性能、TTS、锚点和用户验证；这些已经列为 W0/W8 open items，没有用于支持“技术或市场已通过”的声明。

### Evidence Index

| Command | Exit Code | Result Summary | Evidence Path | Layer | Related Artifact |
|---|---:|---|---|---|---|
| `rtk wc -l docs/txt-reader/PRODUCT_PLAN.md docs/txt-reader/PLAN_REVIEW.md docs/txt-reader/EXECUTION_STATE.md` | 0 | 三个工件均存在，共 824 行（写入本节前） | `docs/txt-reader/` | Workflow | 全部工件 |
| `rtk rg -n '^## (1\. 结论\|2\. 目标与边界\|5\. 产品范围与优先级\|7\. 技术架构\|8\. 非功能验收\|9\. 交付阶段与完成标准\|12\. 风险台账\|13\. 防卡死与停止条件)' docs/txt-reader/PRODUCT_PLAN.md` | 0 | 找到目标、范围、架构、验收、阶段、风险和防卡死章节 | `PRODUCT_PLAN.md` | Workflow | 产品方案 |
| `rtk rg -n '自动滚动\|平台 TTS\|净读规则\|原文件逐字节不变\|音频焦点\|睡眠定时' docs/txt-reader/PRODUCT_PLAN.md` | 0 | 三项新增能力及关键验收均已进入方案 | `PRODUCT_PLAN.md` | Workflow | 产品方案 |
| `rtk rg -n '^### M[1-5]\.\|Conditional Pass\|conditional-pass\|不支持的声明' docs/txt-reader/PLAN_REVIEW.md` | 0 | 找到 5 个 Major Gate 和 conditional-pass 结论 | `PLAN_REVIEW.md` | Workflow | 评审报告 |
| `rtk rg -n 'retry_budget\|staleness_threshold\|heartbeat\|stop_condition\|Resume Prompt' docs/txt-reader/EXECUTION_STATE.md` | 0 | 找到重试、时效、心跳、停止条件和恢复入口 | `EXECUTION_STATE.md` | Workflow | 执行状态 |
| `rtk rg -n '100MB/300MB 性能已达标\|产品已有 PMF\|8–10 周一定可以发布\|Kuikly 为最终架构.*是' docs/txt-reader/PRODUCT_PLAN.md` | 1 | 负向检查无匹配：方案正文未宣称性能、PMF 或最终架构已通过 | `PRODUCT_PLAN.md` | Workflow | 完成声明核验 |

### Review Status

- Blocker：0；
- Major Gate：5，均有 Owner 阶段、验证要求、通过条件和失败回退；
- Minor：4，不阻断 W0；
- breaking change：无，当前工作区此前没有产品方案或实现；
- rollback：如不采纳方案，可删除 `docs/txt-reader/`，不影响参考 APK。

### Final Gate

- 请求的“更新方案并进行方案评审”：`pass`；
- 进入 W0 技术验证：`conditional-pass`；
- 直接进入完整开发或发布承诺：`needs-fix`，必须先关闭 M1–M4，并在产品阶段处理 M5。

## W0 Implementation Verification Evidence

### Scope Summary

- 新增纯 Java 核心原型：流式导入、Big5/GB18030 歧义探测与编码覆盖、Unicode 安全选区/派生范围回映原文/有界磁盘精确读取、连续/分页及音量键导航设置、分级章节置信度及原文锚点手工目录/映射/CRC 存储、全文件净读投影、双向锚点映射、校验候选索引/完整命中分页/逐处选择、规则包、多规则作用域/启停/顺序/备注原子存储、逐规则双计数、安全容量回收、Unicode bigram、有界章节识别、可恢复磁盘分段与损坏门禁、睡眠定时、分段 TTS 队列和系统音色/语速/音调设置契约；
- 新增 Android API 36 原型：SAF 导入、导入前 Big5 等编码选择、自动置信度/手动来源提示、同源同内容复用与差异确认重解码、章节目录置信度/选择跳转/改名/拆分/合并及净读映射、私有副本、多本持久书架/最近阅读/重复导入复用/进度恢复、原生 `ReaderSurfaceView` 长按选词/拖动扩选/跨行高亮/复制/全文搜索/原文映射创建规则、触摸连续滚动/分页吸附/可选音量键翻屏/有界窗口手动与自动续载、自动滚动、平台 TTS/系统音色/语速/音调同步持久化/有界分段队列/暂停恢复/前后段/磁盘跨窗口续播/音频焦点与 noisy 输出暂停、睡眠定时、完整磁盘索引、全文搜索、有界章节目录、全局锚点窗口回读，以及多规则编辑/持久化/预览/索引分页/页级批选/应用/撤销、规则包与清洗 TXT 导出；
- 建立 Gradle 9.1.0 / AGP 9.0.1 / JDK 17 / compileSdk 36 构建链和统一 `w0Check`；
- HarmonyOS 代码尚未创建：官方 Command Line Tools 下载端点在当前非登录环境返回 HTTP 403，且没有 HarmonyOS NEXT 设备；未使用第三方镜像规避来源验证。

### Evidence Index

| Command / Activity | Exit Code | Result Summary | Evidence Path | Layer |
|---|---:|---|---|---|
| `rtk ./gradlew --no-daemon --rerun-tasks w0Check` | 0 | 47 个任务强制执行；核心 42 个场景通过，Android lint 通过，debug APK 构建成功 | `android-prototype/` | Build/Test/Lint |
| `rtk ./gradlew --no-daemon :core:generateMalformedNavigationFixture` | 0 | 生成 269,521 字节、130 个异常、前 128 个可导航位置及完整预期清单；源 SHA-256 为 `47a40a53ca8aed70bfa9cb7c9719d7fa800aaf38d9b07f0bb7765db16a495c68` | `android-prototype/core/build/device-smoke/` | Device Test Asset |
| API 36 AVD 冷启动 Debug/Release | 0 | KVM 组上下文启动成功；最终交付包页面可见、进程存活、无 AndroidRuntime 错误 | Android Emulator 37.1.11 / API 36 | Android Runtime |
| `rtk which ohpm` / `rtk which hvigor` | 1 / 1 | Harmony 包管理器与构建器均不可用 | host PATH | External Dependency |
| 首次书架安全增量编译 | 1 / 0 | `removalPublished` 误置于恢复任务作用域，Java 编译失败；移动到移出任务后，定向编译/lint 与全量门禁均通过 | `MainActivity.java` | Negative Test Evidence |
| 首次书签全量门禁 | 1 / 0 | Unicode 安全截断局部变量与摘要窗口变量重名，Java 编译失败；最小改名后重新强制执行 47 个任务并通过 | `MainActivity.java` | Negative Compile Evidence |
| Android 书签评审 | needs-fix / 0 | 发现单一跨书书签文件会扩大损坏爆炸半径并阻断副本隐私删除（Major）；改为按书 ID 隔离 profile、拒绝跨书污染并按 ID 精确删除后复审 blocker=0、major=0 | `BookBookmarkStore.java` / `MainActivity.java` | Review |
| Android 阅读外观评审 | needs-fix / 0 | 发现累积控制项会把正文挤到零高度（blocker）、设置保存/应用可能失配及 TalkBack 会朗读技术 revision（Major）；改为控制区/正文等分、同步回滚事务和人类描述/诊断 tag 分离后复审 blocker=0、major=0 | `ReaderAppearance.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android 自动滚动调速评审 | needs-fix / 0 | 发现仅在停止触摸时持久化会漏掉键盘与部分 TalkBack 调节（Major）；非按压型用户输入改为即时提交，触摸仍在松手提交，失败统一回滚后复审 blocker=0、major=0 | `AutoScrollPolicy.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android 触摸恢复/常亮评审 | needs-fix / 0 | 发现倒计时期间再次触摸不会取消、可能幽灵恢复，以及 250ms 重写 live status 会使 TalkBack 重复播报两个 Major；ReaderSurface 改为上报所有触摸及暂停原因，二次触摸取消，只在秒数变化时更新提示后复审 blocker=0、major=0 | `AutoScrollCompanionSettings.java` / `AutoScrollResumeSession.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android 字体/段距/方向评审 | needs-fix / 0 | 发现设置已提交后方向 API 异常会把内存方向回滚为旧值，但磁盘与排版保持新值（Major）；提交前完成可失败计算，提交后保持新状态，方向 API 独立降级提示，并补字体选择边界门禁后复审 blocker=0、major=0 | `ReaderAppearance.java` / `ReaderDisplayPolicy.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android 编码覆盖/重解码评审 | needs-fix / 0 | 发现同源不同解码仍复用源 SHA revision、提交后失败可能删除目录指向文件、陈旧导入可越过新请求提交，以及删除快照仍假定 `bookId == revision` 四个 Major；分离源身份/输出 revision、前置可失败准备、增加跨线程请求门禁并按删除 revision 判断后复审 blocker=0、major=0 | `ImportEncodingPreference.java` / `ImportResult.java` / `TextImportPipeline.java` / `MainActivity.java` | Review |
| Big5 编码探测评审 | needs-fix / 0 | 发现 Big5/GB18030 字节域高度重叠时激进猜测会误码，以及 64KiB 样本恰好截断多字节字符会被旧解码路径误判为损坏两个 Major；仅在语言合理性显著领先且至少两个常用字命中时选 Big5，歧义保守回退并提示手动覆盖；采样多读 1 字节区分窗口截断与真实 EOF | `EncodingDetector.java` / `ImportEncodingPreference.java` / `CorePrototypeTest.java` | Review |
| Android 编码告警评审 | needs-fix / 0 | 发现内部诊断原因未进入 UI，且重复导入恢复分支会覆盖本次检测告警（Major）；新增平台中立告警枚举和独立持久状态行，覆盖启发式 Big5、旧编码歧义、损坏字节、重复导入及重解码确认，复审 blocker=0、major=0 | `DetectedEncoding.java` / `EncodingDetector.java` / `MainActivity.java` / `strings.xml` | Review |
| Android 编码诊断持久化评审 | needs-fix / 0 | 发现非有限置信度可绕过校验、目录发布失败会遗留/覆盖 profile，以及重复导入清理旧 profile 失败会在主数据已提交后误报失败三个 Major；收紧有限数不变量、保存旧 profile 并随目录失败回滚、旧版本清理改为 best-effort，复审 blocker=0、major=0 | `BookEncodingProfile*.java` / `DetectedEncoding.java` / `MainActivity.java` / `CorePrototypeTest.java` | Review |
| 流式损坏字节诊断评审 | needs-fix / 0 | 初版夹具未覆盖错误序列跨 16KiB 输入缓冲区（Major）；补充跨边界 UTF-8 错误前缀/紧随非法字节夹具，证明事件分组和绝对偏移，BOM、手动编码、无错误及 profile v1→v2 已覆盖，复审 blocker=0、major=0 | `TextImportPipeline.java` / `ImportResult.java` / `BookEncodingProfile*.java` / `CorePrototypeTest.java` | Review |
| Android 首异常跳转评审 | needs-fix / 0 | 发现书架无可恢复项、切换失败、删除/移出非当前书等收口路径解除 busy 后未恢复跳转按钮（Major）；所有 library transition 收口统一刷新按钮状态，并以 request/book/revision/profile/index 五重门禁拒绝陈旧跳转，复审 blocker=0、major=0 | `MainActivity.java` / `strings.xml` / `BookEncodingProfile*.java` | Review |
| Android 乱码前后导航评审 | needs-fix / 0 | 发现 profile 构造为兼容 v2 放宽空列表时，也会接受带字符锚点却无位置的 v4 非一致状态（Major）；仅无字符锚点的旧记录允许空列表，v3 首锚点升级为单一位置，并明确历史记录/128 上限提示，复审 blocker=0、major=0 | `DecodingReplacement.java` / `BookEncodingProfile*.java` / `MainActivity.java` / `CorePrototypeTest.java` | Review |
| 乱码导航设备夹具评审 | pass / 0 | 固定 130 个非法 UTF-8 字节；独立记录字节/字符预期，核心管线逐项核对前 128 个位置；第 64/128 锚点分别越过 128K/256K，blocker=0、major=0 | `MalformedNavigationFixtureGenerator.java` / `CorePrototypeTest.java` / `ANDROID_SMOKE.md` | Review |
| 首次损坏字节诊断定向编译 | 1 / 0 | 新夹具误写不存在的 `Choice.UTF8`，生产代码已编译但测试未运行；核对公开枚举后最小修正为 `Choice.UTF_8`，同一门禁核心 41 场景通过 | `CorePrototypeTest.java` | Negative Compile Evidence |
| Android 手工章节目录评审 | needs-fix / 0 | 发现目录 profile 仅按 `bookId` 命名会在重解码崩溃窗口误挂新原文、映射验证晚于落盘会发布 UI 未接纳状态、文末拆分可创建空章节三个 Major；改为 `bookId + baseRevision` 版本隔离、共享层先映射验证后原子保存，并以原文字符数拒绝文末拆分后复审 blocker=0、major=0 | `ChapterOutline*.java` / `MainActivity.java` | Review |
| Android 阅读导航评审 | needs-fix / 0 | 发现触摸按下即启动恢复倒计时、自动滚动无法跨有界窗口、陈旧续载可在用户停止后幽灵重启、设置提交后异常会造成内存/磁盘分歧，以及分页回摆和 `ACTION_CANCEL` 误翻页/误恢复；改为松手启动倒计时、边界续载携带恢复意图、generation 与 pending 双门禁、提交前计算及提交后不回滚，并按最终位移和取消语义处理后复审 blocker=0、major=0 | `ReaderNavigationSettings.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android 正文选择评审 | needs-fix / 0 | 发现直接把派生选区存为非级联规则可能永不命中（blocker），以及越界静默夹紧、MOVE 全窗口扫描、行尾误选下一行、过渡期陈旧确认和音量键移走选区等 Major；新增派生范围回映原文与有界精确读取，严格端点、局部验证、行尾命中修正、revision/path/request 门禁及选区期间系统音量回退后复审 blocker=0、major=0 | `ReaderTextSelection.java` / `TextOffsetRange.java` / `DiskRepairProjection.java` / `DiskDocumentIndex.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android TTS 高亮评审 | needs-fix / 0 | 发现平台 range 常为词级、无 range 降级只覆盖分片、动态描述会干扰 TalkBack、初次朗读可能从段中开始、高频回调重复扫长窗口，以及长段高亮起点不能代表语音进度等 Major；改为句级扩展、完整段落降级、初次段首规范化、边界缓存、独立跟随锚点和模式化页面跟随后复审 blocker=0、major=0 | `SpeechPlaybackQueue.java` / `ReaderSurface.java` / `ReaderSurfaceView.java` / `MainActivity.java` | Review |
| Android TTS 设置评审 | needs-fix / 0 | 发现同步参数拒绝提示会被“正在朗读”覆盖、系统默认音色可能因 `setVoice` 漂移且网络属性不可见、提交后异常可能造成内存/磁盘分歧，以及错误队列残留会持续拦截音量键等 Major；固定初始化默认音色、结构化离线能力、提交前完成可失败计算、同步错误后门禁状态发布并统一停止后复审 blocker=0、major=0 | `SpeechSettings.java` / `TextToSpeechPort.java` / `AndroidTextToSpeechAdapter.java` / `MainActivity.java` | Review |
| 首次阅读选区核心定向测试 | 1 / 0 | 反向半开区间夹具把 `7→1` 的全文末尾字符漏出期望值；核对实际范围语义后只修正测试期望，生产实现未改，随后核心 36 场景和全量门禁通过 | `CorePrototypeTest.java` | Negative Test Evidence |
| `aapt dump permissions app-debug.apk` | 0 | 仅输出包名，无存储、网络或高风险权限 | `android-prototype/app/build/outputs/apk/debug/app-debug.apk` | Package |
| `apksigner verify --verbose app-debug.apk` | 0 | APK 签名可验证，v2 scheme 为 true | 同上 | Package |
| `rg 'uses-permission\|MANAGE_EXTERNAL_STORAGE\|READ_EXTERNAL_STORAGE\|WRITE_EXTERNAL_STORAGE\|INTERNET' ...` | 1 | 负向检查无匹配，源码 Manifest 与合并 Manifest 均未声明这些权限 | `android-prototype/app/src/` | Security |
| `sha256sum app-debug.apk` | 0 | 当时的前 128 处乱码导航/profile v4 历史构建 `b1898c08699a5a50d7ba8029030791abf1b126f78dd88865bd47c0de76fd7719`，1,128,405 字节；已被 CP3 新构建替代 | 同上 | Package |
| `java ... LargeFileBenchmark 10/100/300` | 0 | 首窗口均小于 42ms；300MiB 导入约 7.81s，最大 RSS 186,956KB，无 OOM | `docs/txt-reader/w0/HOST_BENCHMARK.md` | Host Algorithm |
| API 36 emulator + SAF picker | 0 | 通过真实系统文件选择器导入 UTF-8 文本，状态与预览更新 | `docs/txt-reader/w0/ANDROID_SMOKE.md` | Android Runtime |
| API 36 emulator auto-scroll/touch | 0 | offset 0→26→43，触摸后保持 43，状态为“自动滚动已暂停” | `docs/txt-reader/w0/ANDROID_SMOKE.md` | Android Runtime |
| API 36 emulator platform TTS | 0 | TTS 初始化并产生 range 回调，停止后状态正确，无 AndroidRuntime crash | `docs/txt-reader/w0/ANDROID_SMOKE.md` | Android Runtime |
| API 36 emulator index/search | 0 | 识别 10 章；第二章跳转锚点 44；`TXT` 命中位置 13，可见锚点回到 8；无 AndroidRuntime error | `docs/txt-reader/w0/INDEX_SMOKE.md` | Android Runtime |
| `java -Xms32m -Xmx128m ... DiskIndexBenchmark 100 20000` | 0 | 100MB 构建 6.54s，查询 4.65ms，最大 RSS 146.7MiB | `docs/txt-reader/w0/DISK_INDEX_BENCHMARK.md` | Host Algorithm |
| `java -Xms32m -Xmx128m ... DiskIndexBenchmark 300 20000` | 0 | 300MB 构建 18.61s，查询 62.27ms，最大 RSS 144.7MiB，无 OOM | 同上 | Host Algorithm |
| injected interruption / truncated bucket / revision conflict | expected failure | 未完成 revision 不发布；续建后可查询；截断桶和同 revision 异内容被拒绝 | 同上 | Failure Recovery |
| API 36 force-stop + cold start | 0 | 1.35s 后无需 SAF 即恢复同一 revision、窗口和完整索引；无 resume/AndroidRuntime error | `docs/txt-reader/w0/DISK_INDEX_BENCHMARK.md` | Android Recovery |
| `:core:repairFileBenchmark -PsizeMiB=100/300` | 0 | v2 双压缩下 100MiB 全选 4.46s、排除 20 处 4.45s、300MiB 11.89s；投影 2.9/8.6MiB、候选 2.8/8.5MiB，深页 9.1/12.3ms；无 OOM | `docs/txt-reader/w0/REPAIR_RULE_SMOKE.md` | Host Algorithm |
| 首次候选索引 100MiB 基准 | 1 | 约 95 万伪章节使章节列表 OOM；增加 20,000 项显式上限后同命令通过，全文搜索不截断 | `CorePrototypeTest` / `REPAIR_RULE_SMOKE.md` | Negative Performance |
| 生成资产零配额门禁 | 0 | 删除全部非活动生成组，同时保留原文、活动派生/投影/候选索引和活动搜索索引；报告 protected/retained bytes 与超额状态 | `CorePrototypeTest` | Capacity Safety |
| 规则包篡改/边界与选择一致性测试 | expected failure / 0 | 篡改、重复 ID、超字段/超数量被拒绝；排除不会使低优先级规则接管，候选/应用/投影/revision 一致 | `prototype/core/src/test/java/.../CorePrototypeTest.java` | Core Contract/Security |
| API 36 preview/apply/force-stop/undo | 0 | 命中 1、警告 0；原文 `b4c2dd07…`→派生 `61942ca9…`；冷启动恢复派生；撤销恢复原文 | 同上 | Android Runtime/Recovery |
| KVM emulator / `-accel off` | 1 / offline | 硬件模式被 `/dev/kvm` 权限拒绝；软件模式持续 ADB offline，停止重试 | `docs/txt-reader/w0/ANDROID_SMOKE.md` | Negative Infrastructure |
| 首次多规则完成门禁 | 1 | 夹具未真正构造潜在循环导致警告数断言失败；修正夹具后全门禁通过 | `prototype/core/src/test/java/.../CorePrototypeTest.java` | Negative Test Evidence |
| 首次范围索引断言 | 1 | 数值同为 70，但测试误用 `Integer`/`Long` 对象比较；统一为 long 后核心 21 场景通过，生产实现未改 | `prototype/core/src/test/java/.../CorePrototypeTest.java` | Negative Test Evidence |
| 独立 `javac @sources.txt` 路径审查 | needs-fix / 0 | 新范围类最初未进入手工源清单；本轮再次从全新临时目录冷编译并独立运行核心 42 场景，含设备夹具生成器、完整计数、128 个位置上限、编码 profile v1/v2/v3/v4 与精确损坏字节诊断，手工源清单无遗漏 | `prototype/core/sources.txt` | Build/Recovery |
| `rtk ./gradlew :core:largeFileBenchmark -PsizeMiB=100` | 0 | 单遍恢复解码 100MiB：首窗口 47.91ms、完整导入 2,251.68ms、堆后 32.50MiB，无第二遍扫描 | `docs/txt-reader/w0/HOST_BENCHMARK.md` | Host Performance |
| 首次 TTS 设置独立冷编译命令 | 127 / 0 | 在 `android-prototype/` cwd 错用仓库根相对路径，且假定 `aapt/apksigner` 在 PATH；改为仓库根与 Build Tools 36.0.0 明确路径后，核心 37 场景、签名和权限检查通过 | `prototype/core/sources.txt` / debug APK | Negative Verification Evidence |
| v2 投影数据/索引位翻转注入 | expected failure | 数据块 CRC 与索引 CRC 分别拒绝损坏；跨块、连续删除同锚点和 v1 兼容映射通过 | `CorePrototypeTest` | Format/Recovery |
| 首次候选 v2 编译 | 1 | 管线资源已切换 Writer，但记录仍调用旧静态方法；改为实例写入后核心门禁通过 | `RepairFilePipeline.java` | Negative Compile Evidence |
| 首次候选 v2 深页基准 | 0 / regression | 空间降至 2.8MiB，但逐字段块索引读取使深页达到 110.7ms；缓冲解析后降至 9.1ms | `REPAIR_RULE_SMOKE.md` | Negative Performance |
| v2 候选数据/索引位翻转与 v1 夹具 | expected failure / 0 | 双 CRC 拒绝损坏；跨 256 条块边界、最大长度规则、规则签名和 v1 读取兼容通过 | `CorePrototypeTest` | Format/Recovery |
| 外部导出恢复日志损坏注入 | expected failure / 0 | CRC 位翻转被拒绝；有效 pending 原子往返、用户确认后清除及字段边界通过 | `CorePrototypeTest` / `ExportRecoveryJournal.java` | Recovery/Security |
| 导出恢复首轮代码评审 | needs-fix / 0 | 发现瞬态异常误清日志、旧 pending 被新任务覆盖两个 Major；缩窄清理分支并增加单槽/进行中门禁后复审 blocker=0、major=0 | `MainActivity.java` / `REPAIR_RULE_SMOKE.md` | Review |
| 伴读定时首次 Android 编译 | 1 / 0 | 互斥入口缺少 `isAutoScrolling()` 只读接口；补齐后定向编译/lint 与全量门禁通过 | `ReaderSurfaceView.java` | Negative Compile Evidence |
| 伴读睡眠定时评审 | needs-fix / 0 | 发现章节边界不可用时旧定时仍运行、配置重建静默丢失两个 Major；改为取消旧定时，并保留同进程非配置实例目标/revision 后复审 blocker=0、major=0 | `MainActivity.java` / `CompanionSleepTimer.java` | Review |
| 分段 TTS 队列评审 | needs-fix / 0 | 发现 revision/窗口切换未统一失效旧队列、磁盘窗口可能拆代理对、范围回调可倒退锚点和极端上限整数溢出；统一停止入口、Unicode 安全窗口、单调锚点和 long 算术修复后复审 blocker=0、major=0 | `SpeechPlaybackQueue.java` / `MainActivity.java` / `DiskDocumentIndex.java` | Review |
| Android 音频中断评审 | needs-fix / 0 | 发现异步跨窗口读取期间丢失焦点后仍可能自动出声；改为事务式建队，焦点失效时把新队列保持暂停并等待用户重新申请后复审 blocker=0、major=0 | `AndroidAudioInterruptionAdapter.java` / `MainActivity.java` | Review |
| Android 多本书架评审 | needs-fix / 0 | 发现跨书清理可能误删其他书活动派生（blocker），以及损坏重复项误删新副本、坏最近项阻断其他书、销毁后重任务泄漏、过渡期旧锚点覆盖新 revision 等 Major；增加全书架活动资产保护、可恢复性检查、先展示目录、pending 锚点与过渡门禁后复审 blocker=0、major=0 | `BookLibraryStore.java` / `MainActivity.java` | Review |
| 跨书活动资产保护夹具首次全量门禁 | 1 / 0 | 第二个活动组改为受保护后不再占非活动保留名额，旧索引按“保留 1 组”语义被正确保留，原断言失效；将该清零实验的非活动保留数设为 0 后，两个活动组在零配额下均保留且旧组删除，全量门禁通过 | `CorePrototypeTest.java` / `GeneratedArtifactPruner.java` | Negative Test Evidence |

### Negative Paths And Deviations

- 直接由 shell 向 DocumentsProvider URI 授权返回 `SecurityException`；改用真实 SAF 用户选择流程后通过，证明原型依赖正确的系统授权模型；
- 首次 Android 编译暴露 `UtteranceProgressListener.onError(String)` 缺失，补齐抽象回调后通过；
- 首次 lint 暴露 URI flag 常量、字符串资源、图标与备份规则问题，修复后为 0 error；
- 系统旧 `adb` 1.0.39 与 SDK `adb` 1.0.41 会抢占 server，现阶段固定使用 SDK 路径；正式自动化前应移除双版本歧义；
- 模拟器 smoke 不能替代真机音质、音频焦点、后台、耗电和 100/300MB 设备基准。
- SAF DocumentsProvider 无法提供跨提供方原子重命名；应用私有 `pending`、回读校验和显式重试已构建，但外部导出进程中断仍可能留下部分目标文件，保留真机异常验证。

### W0 Gate

- 核心算法：`partial-pass`，主路径、全文件净读、双向映射、可恢复磁盘索引、宿主机大文件与跨分段锚点已有证据，仍缺设备端异常/低空间基准；
- Android：`partial-pass`，最新 Release 已在 API 36 模拟器通过 SAF、七编码错误恢复、130 处损坏 UTF-8 统计与首处导航、自动滚动/触摸暂停、后台后强停恢复、基础系统 TTS；Big5 真实歧义语料、音色与参数、选择与导航手势/TalkBack、目录编辑与映射、多书和复杂中断路径仍为 `device-pending`，并缺 API 26–35、不同 OEM 真机与大文件设备复测；
- HarmonyOS：`blocked-by-environment`，等待官方工具链、账号与设备；
- W0 总结论：`in-progress`，不得据此宣称双端架构、性能或发布就绪。
