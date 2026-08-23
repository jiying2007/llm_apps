# Google Play Screenshot / Feature Graphic Brief — zh-CN

## Visual rule

Screenshots sell user outcomes, not implementation details. Do not lead with C++, SHA, ABI, JNI, Core or cache terminology. Use real app UI captured from a release-like build; only use measured performance claims when device evidence exists.

## 7-frame default sequence

### 1. TXT 乱码？直接正确打开
- Show a split before/after: mojibake on the left, readable Chinese text in Jingdu on the right.
- Secondary line: 自动识别常见中文编码，也可随时重新解码。
- No exaggerated success-rate claims.

### 2. 广告、水印、网站尾巴，一眼发现
- Show Smart Clean candidates with reason, count and confidence.
- Secondary line: 全部在本地扫描，先预览再决定。
- This is the primary differentiation screenshot.

### 3. 326 次重复干扰，一键净读
- Show selected candidates → Clean preview comparison.
- Secondary line: 源 TXT 永不修改。
- If Pro UI is shown, do not put price/discount copy on the store graphic.

### 4. 长篇 TXT，也要舒服读
- Show Paper / Light / Night and typography controls.
- Secondary line: 字号、行距、留白、衬线字体、宽屏合适行长。
- Only add 10/100/300 MiB timing numbers after DEVICE_MATRIX has measured evidence.

### 5. 找章节、搜人物、留书签
- Show Chapters and Search sheets plus progress/bookmark context.
- Secondary line: 长篇内容也能快速回到目标位置。

### 6. 听书、自动翻页、睡眠定时
- Show TTS state and Settings sheet.
- Secondary line: 适合通勤、休息和长时间阅读。

### 7. 你的 TXT，只留在你的设备
- Show local/offline privacy visual using actual app/privacy copy, not fake certification logos.
- Secondary line: 无账号 · 无广告 SDK · 无分析追踪 · TXT 不上传。

## Keyword-listing hero variants

- `txt-reader`: 本地 TXT，打开就读
- `txt-encoding`: TXT 乱码？直接正确打开
- `smart-clean`: 广告、水印、网站尾巴，一眼发现
- `local-novel`: 你的小说，只留在你的设备

## Device set

Capture at minimum:
- phone portrait;
- phone landscape where useful;
- tablet/expanded window for library + reading line-length proof.

## Production checklist

- no debug package/watermark;
- no personally identifiable filenames or private book content;
- no copyrighted novel passage beyond short synthetic demo content;
- use synthetic TXT fixtures for before/after examples;
- all claims must be directly represented by current release behavior;
- avoid rankings, awards, discounts, temporary pricing or competitor references;
- keep top copy readable at thumbnail/search-result size.
