# Google Play Custom Store Listings — zh-CN

> Play Console supports Search keywords targeting. Select only keyword bundles actually offered/known to bring traffic in Play Console; this file is the copy/asset SSOT, not a claim that the listings are already published.

## Default

- **Name:** 净读 - TXT 小说阅读器
- **Intent:** broad branded + category discovery
- **Short:** 专为中文长篇 TXT 打造：自动识别乱码编码、智能净读广告水印、大文件阅读、目录搜索与离线朗读
- **Hero promise:** 乱码能打开，脏文本能净化，长篇能舒服读。
- **Screenshot order:** encoding → Smart Clean → reading → navigation → TTS → privacy

## Listing A — TXT 阅读器

- **Console key:** `txt-reader`
- **Search intents to select when available:** TXT阅读器、TXT小说阅读器、txt reader、中文TXT、本地TXT
- **Name:** 净读 - TXT 小说阅读器
- **Short:** 为本地长篇 TXT 深度优化：大文件快速重开、目录搜索、书签、舒适排版与离线朗读
- **Screenshot 1:** 本地 TXT，打开就读
- **Screenshot 2:** 长篇 TXT，也能保持流畅
- **Screenshot 3:** 目录、搜索、书签，一步到达

## Listing B — 乱码 / 中文编码

- **Console key:** `txt-encoding`
- **Search intents to select when available:** TXT乱码、乱码阅读器、GBK阅读器、GB18030、Big5阅读器、中文乱码
- **Name:** 净读 - 中文 TXT 阅读器
- **Short:** TXT 乱码不用重找文件：自动识别常见中文编码，也可从私有源副本随时重新解码
- **Screenshot 1:** TXT 乱码？直接正确打开
- **Screenshot 2:** UTF-8 / GBK / GB18030 / Big5 / UTF-16
- **Screenshot 3:** 自动不准？一键重新解码

## Listing C — 净读 / 去干扰

- **Console key:** `smart-clean`
- **Search intents to select when available:** TXT清理、小说去广告、小说净化、文本清理、广告水印、TXT去广告
- **Name:** 净读 - TXT 智能净读
- **Short:** 本地发现小说里的重复广告、水印、网址和站点尾巴，先预览再净读，源 TXT 永不修改
- **Screenshot 1:** 广告、水印、网站尾巴，一眼发现
- **Screenshot 2:** 出现 326 次？净读先告诉你
- **Screenshot 3:** 先预览，再一键应用
- **Screenshot 4:** 你的全局规则，用在每一本书

## Listing D — 本地小说 / 隐私

- **Console key:** `local-novel`
- **Search intents to select when available:** 本地小说阅读器、离线小说阅读器、离线阅读器、本地阅读器、无网络阅读器
- **Name:** 净读 - 本地小说阅读器
- **Short:** 本地导入、离线阅读、系统 TTS；无账号、无广告 SDK、无分析追踪，TXT 内容不上传
- **Screenshot 1:** 你的小说，只留在你的设备
- **Screenshot 2:** 无账号 · 无上传 · 本地处理
- **Screenshot 3:** 离线朗读、自动翻页、睡眠定时

## Experiment order

Run one major variable at a time:

1. icon;
2. first screenshot / hero promise;
3. short description;
4. screenshot order;
5. only then test secondary copy details.

Primary metrics: store-listing visitor → install conversion, retained installer quality, ratings/reviews and Pro purchase conversion. Do not optimize only for raw installs if a variant brings low-quality traffic.
