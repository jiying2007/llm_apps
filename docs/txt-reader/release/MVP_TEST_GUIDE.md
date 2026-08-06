# 净读 TXT Android MVP 测试指南

状态：签名测试包已生成，等待 Android 真机验收。

## 测试版本边界

- 产品版本：`1.0.0`；
- Release 包名：`com.junchen.jingdu`；Debug 包名：`com.junchen.jingdu.debug`；
- 旧 `com.jingdu.txt.w0` 不属于同包升级链，不会自动迁移其私有书架数据；
- 当前测试包不包含账号、云同步、广告、统计 SDK 或自动反馈上传；
- TXT、阅读进度、书签和净读规则保存在应用私有目录；
- 系统 TTS 由设备安装的语音引擎提供，联网音色可能把朗读文本交给引擎提供方处理；
- 卸载会删除应用私有副本和阅读数据，原始 TXT 不受影响。

## 分发要求

优先使用 Google Play Internal testing / Internal app sharing；否则使用 `dist/android/1.0.0/jingdu-1.0.0-release.apk` 受控分发。Debug 包只用于日志定位，不得标记为正式测试发行版。每次分发必须同时提供版本号、`SHA256SUMS`、已知问题和回退版本。

正式签名门禁使用 `rtk ./gradlew --no-daemon --no-configuration-cache -PjingduApplicationId=<最终包名> -PjingduVersionCode=<正整数> -PjingduVersionName=<正式版本> androidStoreCheck`。真实 `keystore.properties` 和上传密钥不得进入源码仓库；每次发布需单独归档 AAB、SHA-256 和 R8 `mapping.txt`。

## 必测主流程

1. 通过系统文件选择器导入 UTF-8 和一种旧编码 TXT；
2. 阅读、搜索、目录跳转、添加书签，强制停止后恢复；
3. 创建净读规则，预览、应用、重启恢复并撤销，核对原始 TXT 未改变；
4. 启动自动滚动，调速、触摸暂停、倒计时恢复和停止；
5. 使用系统默认及一个离线 TTS 音色，验证暂停/继续、前后段和音频中断；
6. 使用乱码夹具验证完整计数、前后导航、128 处边界和净读映射；
7. 切换日间/护眼/夜间、大字体和横竖屏，核对顶栏、正文、键盘和系统栏无遮挡；
8. 开启 TalkBack，完成导入、收起/展开面板、隐私说明、朗读和停止；
9. 导入 100MiB TXT，记录首屏、索引、搜索、内存和异常；
10. 移出/恢复书架并删除私有副本，核对隐私清理语义。

## 反馈模板

```text
测试版本：
设备品牌/型号：
Android 版本：
系统 TTS 引擎/音色：
操作步骤：
实际结果：
期望结果：
是否可稳定复现：
是否涉及原始 TXT 或隐私风险：
可附截图/录屏（请先遮挡私人正文）：
```

不要提交原始书籍、完整应用私有目录或包含私人正文的日志。若问题只能由特定文本触发，优先提供自行构造的最小脱敏样例。

## 放行标准

- blocker 0、major 0；
- 核心阅读闭环在至少两台不同厂商设备通过；
- API 35/36 edge-to-edge、软键盘、刘海/横屏和 TalkBack 无阻断；
- release 变体通过 R8、lint、签名、权限和升级验证；
- 已知问题不包含源文件误写、数据挂错书、无法停止 TTS、崩溃循环或不可恢复数据损坏。
