# W0 主机流式导入基准

日期：2026-08-01

## 环境与范围

- 原型：`prototype/core`；
- 测试文本：运行时生成的原创 UTF-8/CRLF 合成文本；
- 过程：编码探测、流式解码、CRLF 标准化、UTF-8 输出、SHA-256；
- Java：基准运行时为 OpenJDK 8u442；
- 计时：原型内部 `System.nanoTime`，外层 `/usr/bin/time -v`；
- 结论只适用于主机算法，不代替 Android/Harmony release 真机结果。

## 定向测试

命令：

```bash
rtk javac -encoding UTF-8 -d <temp-dir> @prototype/core/sources.txt
rtk java -cp <temp-dir> com.jingdu.txt.core.CorePrototypeTest
```

结果：`PASS CorePrototypeTest: 5 scenarios`。

覆盖：

1. GB18030 探测、UTF-8 转码和 CRLF/CR 标准化；
2. UTF-16LE BOM 探测与转码；
3. 目标文件已存在时拒绝覆盖；
4. 净读规则、修改预览、派生映射和锚点恢复；
5. 冲突规则告警与稳定优先级。

## 大文件结果

| 输入 | 首个 64K 可读窗口 | 完整导入 | 最大 RSS | 结果 |
|---:|---:|---:|---:|---|
| 10MiB | 39.83ms | 329.07ms | 59,120KB | pass |
| 100MiB | 37.43ms | 2,354.62ms | 135,400KB | pass |
| 300MiB | 41.17ms | 7,813.99ms | 186,956KB | pass，无 OOM |

外层 wall time 包含测试文件生成和清理，分别约 0.74s、4.51s、13.94s，不应与导入内部计时混用。

2026-08-02 增量复验：导入器改为单遍 `REPORT` 恢复解码并统计替换事件/首偏移后，Gradle Java 17 运行 100MiB 合成语料，首窗口 47.91ms、完整导入 2,251.68ms、堆后 32.50MiB；与上表环境不同，不直接作同比结论，但继续满足 `<5s` 和内存目标，且没有为诊断增加第二遍文件扫描。

## 结论

- 分块读取和固定大小缓冲区方向成立；
- 100MiB 主机峰值 RSS 低于方案的 180MB 目标；
- 300MiB 完成且无 OOM；
- 首个窗口回调不是 Android 真正首帧，仍需把临时分段文件接入平台 ReaderSurface 后测量“用户看到文字”的时间；
- 当前原型仍会先把 URI 复制为私有源副本，再执行第二遍标准化，后续需评估单遍导入状态机。
