# 净读 TXT W0 核心原型

本目录验证不依赖 Android/Harmony SDK 的共享语义：

- 流式编码探测、严格有界的自动/手动编码覆盖与 UTF-8/LF 标准化；支持 Big5 强证据候选、GB18030 歧义保守回退及采样窗口多字节截断门禁；
- 源字节 SHA-256 与规范化输出 SHA-256 分离，稳定书籍身份不复用错误阅读 revision；
- 首个可读窗口回调；
- 源文件 SHA-256 与不可覆盖保证；
- 字面净读规则、冲突警告、修改预览和原文—派生视图映射；
- 全文件流式净读、确定性 revision、v2 分块压缩磁盘双向映射和原子发布；v1 投影保持只读兼容；
- 多规则作用域、启停、顺序、备注的原子 catalog，以及逐规则候选/应用命中统计；
- 基于规则 ID 与原文偏移的逐处选择、确定性 revision 和预览/输出/投影一致性；
- 任意候选序号的只读流式预览分页，翻页不重建派生文件；
- 与源 SHA/规则签名绑定、v1 兼容的 v2 分块压缩候选索引，用于毫秒级深页随机预览；
- 1-based 全局候选序号范围校验与最多 10,000 处的索引批量读取；
- 规则包确定性编解码、完整性校验、大小/字段限制和保留/替换冲突策略；
- 带版本、字段边界和 CRC 的原子外部导出恢复日志，损坏记录拒绝读取，校验完成后显式清除；
- 只识别生成文件的容量回收器，保护活动 revision、报告 protected/retained bytes 并忽略未知文件；
- 段落哈希锚点与规则变化后的定位；
- Unicode code point bigram 搜索、命中上下文、章节识别与视图 revision 门禁；
- 分级章节识别置信度，以及锚定规范化原文、支持改名/拆分/合并的 20,000 项 CRC 原子目录 profile；
- 不拼接全文的 `SegmentedText`，用于跨分段锚点恢复；
- 可恢复的磁盘分段索引、原子 revision 发布、旧版本回滚与损坏门禁；
- 最多 20,000 项的有界章节目录和显式截断状态，正文搜索保持完整；
- `ReaderSurface`、`TextToSpeechPort` 平台适配契约；
- 连续/分页模式与关闭/正向/反向音量键翻屏的严格持久化契约；
- Unicode 安全的阅读选区、派生范围到原文范围映射，以及有上限的磁盘精确范围读取；
- 使用调用方单调时钟的伴读睡眠定时状态机，覆盖时间到期、章节边界、取消、单次消费和输入边界；
- 有界窗口 TTS 分段/播放队列：优先句段边界、不拆代理对、锚点单调、暂停恢复、前后段和陈旧 utterance 回调拒绝；支持词级 range 扩展为句级高亮、无 range 段落降级、独立页面跟随锚点和边界缓存；
- TTS 系统默认/具体音色、50%–200% 语速与音调的严格 v1 持久化契约，以及结构化音色 locale/离线能力和防御复制门禁；
- 10/100/300MB 合成文件基准。

该原型使用当前环境已有的 Java 8，只用于关闭 W0 的算法和数据风险。正式共享层仍需在平台工具链到位后迁移到 Kotlin Multiplatform 或按评审回退为双原生实现。

## 编译与测试

```bash
classes_dir="$(mktemp -d)"
rtk javac -encoding UTF-8 -d "$classes_dir" @prototype/core/sources.txt
rtk java -cp "$classes_dir" com.jingdu.txt.core.CorePrototypeTest
rtk java -cp "$classes_dir" com.jingdu.txt.core.LargeFileBenchmark 10
rtk java -cp "$classes_dir" com.jingdu.txt.core.LargeFileBenchmark 100
rtk java -cp "$classes_dir" com.jingdu.txt.core.LargeFileBenchmark 300
rtk java -Xmx128m -cp "$classes_dir" com.jingdu.txt.core.RepairFileBenchmark 100 0
rtk java -Xmx128m -cp "$classes_dir" com.jingdu.txt.core.RepairFileBenchmark 100 20
rtk java -Xmx128m -cp "$classes_dir" com.jingdu.txt.core.RepairFileBenchmark 300 0
```

基准使用原创合成文本并只写入系统临时目录，结束后删除临时文件。首屏时间和进程内存只能作为当前主机的算法证据，不能替代 Android/Harmony release 真机结果。
