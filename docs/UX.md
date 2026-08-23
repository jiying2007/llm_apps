# UX — Android v2.2

## Design direction

Jingdu is a calm Material 3 reading product, not an engineering toolbar and not a paywall-first app. Library and Reader are the two product states. Search, chapters, bookmarks, Clean, encoding and settings are contextual sheets.

Commercial UX follows one rule: **show useful local results before asking for money**. Free users must be able to read normally and inspect Smart Clean candidates; Pro appears only when automation/reusable assets are requested.

## Library

- In-app brand stays `净读`; store discovery title may include `TXT 小说阅读器`.
- Supporting promise: `本地 TXT · 无广告 · 不上传`.
- Primary action imports one TXT; batch import is secondary and uses SAF multi-select.
- Cards prioritize title/progress; encoding/size/last-read are secondary.
- Empty state explains mojibake rescue, Clean and privacy before file selection.

## Reader

- Text owns the screen.
- Search and chapters are top-level actions.
- Previous/progress/next/TTS are persistent bottom actions.
- Bookmarks/Clean/encoding/settings/delete remain progressive-disclosure actions.
- System back closes a sheet before Reader → Library; predictive back remains compatible.

## Clean conversion flow

### Smart Clean is not a hidden Pro teaser
1. User opens Clean.
2. `免费扫描干扰文本` runs locally.
3. Results show reason, exact text, count and confidence.
4. User can include/exclude candidates.
5. Only `应用已选建议` asks for Pro when not owned.
6. After purchase, the same selection is applied and Clean preview opens.

No first-launch Pro modal and no artificial blur/hidden candidate text.

### Per-book rules
- Exact literal rules remain Free.
- Empty replacement means delete.
- Safe whole-line wildcard `*` is labeled Pro and explains that it matches a whole line.
- No arbitrary regex editor in v2.2.

### Global rule library
- Clearly labeled Pro/user-owned asset.
- Recommended rules are opt-in and editable.
- Import/export is visible only as an explicit user action.
- Applying rules never mutates source TXT.

## Reading settings

Free groups:
1. page tone;
2. font/font size/line height/margins;
3. base TTS rate/pitch and start/stop;
4. auto paging;
5. sleep timer.

Pro groups:
- offline TTS voice selection, showing only voices the system engine marks as not requiring network;
- local settings/global-rule backup and restore.

Backup copy explicitly says book正文 is excluded and nothing is uploaded.

## Pro purchase surface

- Product is one-time lifetime, never described as subscription.
- CTA uses Google Play `formattedPrice` when available.
- If Billing/product details are unavailable, show retry/restore wording without blocking Free features.
- Existing owners always get a visible `恢复购买` / re-check path.
- `PENDING` purchases do not show Pro unlocked.

## Review prompt

- No review on first launch.
- Request only after meaningful local milestones such as multiple book opens, Smart Clean use or encoding rescue.
- No “Do you like Jingdu?” pre-gate.
- Respect Play’s system UI and local cooldown.

## Reading surface / navigation

- 16–34sp base type, adjustable line height/margins, system sans/serif, Paper/Light/Night.
- Expanded windows cap text measure rather than stretching paragraphs.
- Viewport layout reports visible code-point span for sequential paging.
- Search/chapter jumps reset sequential page history.
- Clean offsets never persist into original progress/bookmarks.

## Adaptive / accessibility

- Material controls retain 48dp targets.
- Icon-only actions have content descriptions.
- 200% font scale keeps primary navigation usable.
- Color is never the sole state signal.
- Busy work has progress indicator + descriptive text.
- Destructive actions require confirmation.
- Pro is never communicated by color alone; use text/icon labels.

## Error language

Errors explain next action rather than native status codes. Examples:
- `无法识别文本编码。可以在“编码”里手动选择后重试。`
- `当前无法连接 Google Play 购买服务，请稍后重试。`
- `系统朗读引擎暂无可选离线 voice。`
- `备份恢复失败：文件格式无效或版本不受支持。`

## Store-to-product continuity

The first-run experience must deliver the same promises shown in Play screenshots: encoding rescue, local Smart Clean, long-form comfort and privacy. Store imagery must not advertise features that are only roadmap items.