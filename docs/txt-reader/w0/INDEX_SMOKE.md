# W0 章节、搜索与分段锚点验证

日期：2026-08-02

## 实现边界

- `DocumentIndex` 对当前 `CharSequence` 建立 Unicode code point bigram 倒排索引；
- 搜索返回 UTF-16 字符偏移和最多前后 24 个字符的上下文；
- 章节识别覆盖“第 N 章/节/回/卷/部/篇/集”、序章、楔子、前言、后记和番外，当前置信度固定为 90%；
- 搜索索引绑定 `viewRevision`，调用方请求不同 revision 时拒绝发布旧结果；
- `SegmentedText` 在不拼接全文的情况下实现 `CharSequence`，现有段落哈希锚点可跨分段创建和恢复；
- Android 首屏使用窗口内索引作为降级路径；后台完成磁盘索引后，搜索和章节跳转覆盖完整文件并按需回读阅读窗口。

## 核心验证

命令：

```bash
rtk ./gradlew --no-daemon --rerun-tasks w0Check
```

最新统一门禁结果：`BUILD SUCCESSFUL`，47 个任务执行；`CorePrototypeTest` 25 个场景通过。本文 Android 运行数据仍对应上一可运行 APK。

新增场景：

1. 章节标题、数量和字符偏移；
2. 中文与 supplementary code point（emoji）bigram 搜索和命中上下文；
3. 单 code point 查询；
4. 视图 revision 不匹配时拒绝旧索引，规则视图重建后命中新文本；
5. 段落横跨三个文本分段且前方插入内容后，锚点仍恢复到同一字符。

## Android API 36 Smoke

- 通过真实 SAF 重新选择 `jingdu-sample.txt`；
- 磁盘完整索引状态显示 10 章、1 分段、73.4ms；
- 连续点击“下一章”两次，状态显示“第 2/10 项：第二章 继续阅读”，`ReaderSurface` 可见锚点从 0 变为 44；
- 输入 `TXT` 并搜索，状态显示命中字符位置 13 和上下文，可见锚点从 44 回到命中所在行 8；
- 全过程 `auto-scroll:false`，跳转不会意外启动自动滚动；
- 最近 300 行 `AndroidRuntime:E` 日志为空。

## 构建与安全

- Android lint：0 errors，1 个 Gradle 版本提示；
- `aapt dump permissions`：仅包名，无权限声明；
- APK v2 签名验证通过；
- 本节运行态 APK SHA-256：`4d87e2c4e967fb8df7444f687ef5add3e43749a96e72f6a4d5e05635da5967c9`；最新构建另见 `ANDROID_SMOKE.md`，不可混用运行证据。

## 尚未关闭

- 磁盘型索引、checkpoint 续建和原子 revision 已有核心与模拟器证据，仍需真机进程杀死与低空间验证；
- 100/300MB 与 2 万章节已有宿主机证据，仍需双端真机 release 复测；
- 中文大小写/繁简/正则/模糊搜索不在本轮范围；
- 章节规则仍需真实语料评估误报率和置信度校准；
- 双端真机一致性仍未验证。

详细磁盘格式、失败恢复和性能证据见 `docs/txt-reader/w0/DISK_INDEX_BENCHMARK.md`。

结论：M4 核心正确性与 Android 完整文件索引集成达到 `partial-pass`；设备端异常恢复和双端一致性尚未完成。
