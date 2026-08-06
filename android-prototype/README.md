# 净读 TXT Android MVP 工程

工程已统一为 `com.junchen.jingdu`，当前为 `code-and-signing-ready / device-pending / console-pending`；已产出可安装 Debug 和签名 Release，但不能在缺少真机运行证据时宣称已可上架：

- API 36 / minSdk 26；
- `ACTION_OPEN_DOCUMENT`，不申请存储或网络权限；
- 文件提供方 URI 复制到应用私有目录；
- 多本私有书架按最近阅读排序，保存文件名、编码、大小、活动 revision 和全局字符锚点；支持显式切换、重复导入复用、旧单本状态迁移，以及分别确认的移出书架与删除副本；
- 复用 `prototype/core` 单遍流式导入和编码探测；导入前可选自动、UTF-8、GB18030、GBK、GB2312、Big5、UTF-16LE/BE，独立状态行持续显示自动置信度或手动来源；Big5 启发式、Big5/GB18030 歧义和损坏字节使用结构化告警，无法解码内容显示完整替换事件数，并有界保留前 128 个源字节/规范化字符位置供“上一处/下一处乱码”导航；净读派生视图会先映射原文字符锚点，再按完整索引载入目标窗口；重复导入与重解码确认也保留本次告警；编码诊断以 revision 绑定、CRC 原子 sidecar v4 跨重启保存，兼容读取 v1/v2/v3/缺失旧目录，损坏不阻断正文，删除副本时一并清除；
- 同源同内容重复导入直接复用；同源但解码内容变化时明确确认后才替换私有副本、重置位置并清除旧书签，源 TXT 始终不修改；
- 原生 `ReaderSurfaceView` 支持触摸拖动连续滚动、按视口吸附分页、可选正反向音量键翻屏和基于帧时间的自动滚动；提供 8–120 dp/秒无级调节、即时预览、速度记忆、异常帧跳跃保护、松手后关闭/3/5/10 秒恢复、跨有界窗口续滚和滚动时可选屏幕常亮；
- 正文支持长按选词、拖动扩选、跨行高亮及复制/全文搜索/创建净读规则；派生选区先经投影映射回原文并有界读取，规则继续使用现有预览与原子保存链；
- 系统 `TextToSpeech` 适配及范围回调入口；
- 自动滚动与 TTS 互斥，并共用关闭/15/30/60 分钟/本章结束睡眠定时；定时绑定阅读 revision，配置重建可恢复；
- TTS 按句段在有界窗口内连续续播，支持暂停后从范围锚点恢复、上一段/下一段；窗口耗尽后经完整磁盘索引读取下一窗口；平台 range 回调扩展为当前句高亮，无回调时降级为当前段落高亮，页面按独立语音锚点跟随；
- TTS 支持系统默认/具体系统音色、50%–200% 语速和音调同步持久化；音色列表标注 locale 与离线/需联网属性，已保存音色缺失、引擎拒绝参数或输入超限时安全停止并给出可操作提示；
- 前台朗读按会话申请/释放音频焦点；焦点丢失、可 duck 或 `ACTION_AUDIO_BECOMING_NOISY` 时暂停并保留锚点，用户继续前重新申请焦点；
- 首屏先使用窗口内索引，后台构建绑定视图 revision 的完整磁盘索引；
- 章节目录显示自动识别置信度或人工确认状态，支持选择跳转、改名、当前位置拆分和与下一章合并；手工目录锚定规范化原文并随净读投影映射，正文不被修改；
- 全文命中和章节跳转会从磁盘分段回读约 128K 字符窗口，并保持全局锚点；
- 多条字面净读规则支持新增、保存、启停、上移、下移、删除、当前书/全部书作用域、备注和逐规则候选/应用计数；
- 所有命中支持每页 20 处浏览、逐处/本页/自定义起止序号范围应用或跳过；翻页复用候选 revision；
- 深页优先使用 v1 兼容的 v2 分块压缩候选索引随机读取，索引不可用时回退顺序扫描；
- 章节目录最多 20,000 项并显式提示截断，完整正文搜索不受影响；
- 支持规则包导入导出和当前清洗视图 TXT 导出；外部导出先写应用私有恢复日志，关闭目标后回读校验字节数与 SHA-256，成功才清除日志；
- 原文保持不变，候选失效会清理；投影与候选索引均使用分块压缩格式，生成资产支持可配置配额、主动清理和活动资产超额保护；
- 移出书架只隐藏条目并保留私有副本、进度和规则，支持恢复最近移出；删除副本先发布带 CRC 的恢复日志，再移除目录条目、当前书规则、已知私有文件及未被其他书引用的活动索引，启动时幂等续删；
- 每书书签使用独立 CRC 原子 profile，最多 200 条；保存标准化原文字符锚点，跳转时映射到当前净读 revision，移出书架时保留、删除副本时精确清除；
- 阅读正文支持日间/护眼/夜间主题、系统无衬线/衬线/等宽字体、16–32sp 字号、120%–200% 行高、0–12dp 段距、8–32dp 左右边距，以及跟随系统/锁定竖屏/锁定横屏；设置同步持久化并在重排/尺寸变化后按字符锚点恢复，控制区可滚动且正文始终保留独立空间；
- 只渲染当前有界窗口并按需从磁盘续载；跨设备固定页码、历史生成资产逐书归属清理、后台 Service 和通知栏控制不在当前 W0 实现范围。

## 构建

```bash
rtk ./gradlew --no-daemon w0Check
rtk ./gradlew --no-daemon androidMvpCheck
rtk ./gradlew --no-daemon :core:generateMalformedNavigationFixture
rtk ./gradlew --no-daemon :core:largeFileBenchmark -PsizeMiB=100
rtk ./gradlew --no-daemon :core:repairFileBenchmark -PsizeMiB=100
```

`androidMvpCheck` 同时运行核心测试、Debug/Release lint、Debug APK、R8/资源压缩和未签名 release AAB。首次生成本地上传密钥（仅执行一次）：

```bash
rtk ./gradlew --no-daemon --no-configuration-cache generateUploadKey
```

正式商店门禁与交付目录使用：

```bash
rtk ./gradlew --no-daemon --no-configuration-cache \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=1 \
  -PjingduVersionName=1.0.0 \
  writeAndroidReleaseChecksums
```

交付物位于 `dist/android/1.0.0/`。真实密钥与 `keystore.properties` 权限为 600 且被 Git 忽略；必须立即离线备份两者，不得进入源码仓库、聊天或普通网盘。

SDK 路径只存于被忽略的 `local.properties`。API 36 模拟器 smoke 已通过，包括真实 SAF 导入、130 处损坏 UTF-8 导航、自动滚动/触摸暂停、后台后强停恢复和平台 TTS；但模拟器不能替代 Android/Harmony 真机的性能、功耗、厂商 TTS 和后台行为验证。

已修复首屏系统栏图标在阅读外观加载前解引用，以及 `setContentView()` 前访问尚未创建的 `DecorView`/`WindowInsetsController` 导致的两处确定性启动闪退。最终 Debug 与 Release 已在 API 36 模拟器冷启动通过。新 Debug 包名为 `com.junchen.jingdu.debug`，Release 为 `com.junchen.jingdu`，可共存；旧 `com.jingdu.txt.w0` 不会覆盖升级，其私有数据也不会自动迁移。最新 UI 及既有高风险能力仍缺真机复验。

另已修复 Android ICU 与桌面 JDK 对 `CharsetDecoder` 错误游标语义不同导致的合法字符丢失：运行时以独立探针校准解码器是否已消费错误范围，再统一恢复位置。主机 42 场景和 API 36 上 UTF-8、GB18030、GBK、GB2312、Big5、UTF-16LE/BE 七编码回归通过；Release 全量夹具显示首处源字节 2091，规范化字节为 `=> EF BF BD 3C 3D`，源文件 SHA-256 保持不变。
