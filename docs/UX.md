# UX — Android

## Design direction

The Android app follows a calm Material 3 reading experience rather than a permanent command toolbar. Library and Reader are the two product states; search, chapters, bookmarks, Clean, encoding and reading settings are contextual sheets.

The UI is implemented with Jetpack Compose so Android can follow the current Compose-first platform direction, edge-to-edge behavior, predictive back and adaptive layouts without preserving the old Views toolbar architecture.

## Visual hierarchy

### Library
- Large product title: `净读`.
- Supporting line: `本地 TXT · 无广告 · 不上传`.
- One high-emphasis import action.
- Book cards prioritize title and reading progress; technical metadata is secondary.
- Empty state explains the product value before asking the user to choose a file.

### Reader
- The text surface receives the largest visual area and the lowest visual noise.
- Book title is available but visually subordinate to content.
- Search and chapter navigation are top-level reader actions.
- Page navigation and progress are grouped at the bottom.
- Clean/encoding/settings/delete are progressive-disclosure actions.

## Reading surface

Three page tones are supported:

- **Paper** — warm background for long sessions; default.
- **Light** — neutral high-contrast white.
- **Night** — low-luminance surface with warm off-white text.

Reader typography:
- respects Android font scaling;
- adjustable 16–34sp base size;
- adjustable line height;
- system sans and serif choices;
- adjustable horizontal margins;
- long line length is capped on tablets/foldables instead of stretching across the window.

## Navigation

- System back closes the active sheet first, then returns Reader → Library.
- No custom interception of the platform back key; predictive back remains compatible.
- Volume Up/Down keeps previous/next-page support.
- Search/chapter jumps reset sequential page history.
- Clean preview uses an independent offset domain and never persists original-view progress/bookmarks.

## Sheets

### Search
Search field stays visible above results. Results show context and jump directly to the hit.

### Chapters
Chapter titles are a scrollable destination list. Generated chapter scans run off the main thread.

### Bookmarks
Original-view bookmarks show approximate progress. Adding a bookmark is a first-class action. Clean preview explicitly disables bookmark persistence.

### Clean
Rules are visible and individually removable. Users can add a literal `find → replace` rule, preview the current clean revision, return to original text, or export a clean TXT.

### Reading settings
Settings are grouped by intent rather than implementation detail:
1. page tone;
2. typography;
3. TTS;
4. auto page and sleep timer.

## Adaptive layout

- Compact width: one-column library and centered reader.
- Medium width: book cards use an adaptive grid.
- Expanded width: reader keeps a bounded text measure; chrome uses the extra width rather than stretching paragraphs.
- Landscape and split screen must remain usable at compact height.

## Accessibility

- Material controls provide at least 48dp touch targets.
- Every icon-only action has a content description.
- Color is never the only status signal.
- Reader type scales with system accessibility settings.
- Progress is exposed as text as well as a slider.
- Busy state uses a visible progress indicator plus descriptive text.
- Destructive actions require explicit confirmation.

## Motion

Use short state transitions only where they explain hierarchy. Reading itself has no decorative animation. Page movement should feel immediate; no simulated paper-curl animation is planned.

## Error language

Errors state what failed and what the user can do next. Avoid exposing native status codes unless they are included as secondary diagnostic detail.

Examples:
- `无法识别文本编码。可以在“编码”里手动选择后重试。`
- `导出失败，目标位置可能不可写。`
- `系统朗读引擎尚未就绪，请稍后重试。`
