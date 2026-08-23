# Product — 净读 TXT

## Positioning

**净读 is a privacy-first, offline TXT novel reader for Chinese long-form reading.** It does not compete on format count. Its promise is narrower and stronger:

> Open messy TXT correctly, find distracting text locally, clean it safely, and keep reading comfortably for hours.

The public store name for Simplified Chinese discovery is `净读 - TXT 小说阅读器`; the in-app brand remains `净读`.

## Primary users

1. Readers who keep local TXT novels/archives and want a calm daily reader.
2. Users who regularly meet GB18030/GBK/Big5/UTF-16 mojibake.
3. Web-novel readers whose TXT contains repeated ads, site tails, URLs or watermarks.
4. Long-session readers who care about large-file responsiveness, TTS and position recovery.
5. Privacy-conscious users who do not want accounts, cloud upload, ads or analytics in the reading path.

## Core jobs

### Open it correctly
- Single or multi-select local TXT import with AUTO encoding detection.
- Manual re-decode from the retained private source copy without picking the external source again.
- Source bytes are never modified/deleted.
- Immutable normalized revisions and reusable `.jdx` sparse-index cache keep large-file reopen credible.

### Make it clean
- Free exact per-book find/replace/delete rules.
- Free local Smart Clean scan and preview for repeated lines, URLs/domains and common promotional/watermark patterns.
- Pro applies Smart Clean suggestions in one action and unlocks safe whole-line `*` wildcard rules.
- Pro global rules are reusable user-owned assets; recommended Chinese web-novel patterns can seed the library.
- Clean output is an immutable derived revision and can be exported without touching the source.
- Original progress/bookmarks never receive Clean offsets.

### Keep me in the text
- Library exposes recent books, progress, encoding and size.
- Search, chapters, bookmarks and progress seeking are direct reading tools.
- Paper/Light/Night, typography, margins, TTS, auto paging, sleep timer and volume-key paging support long sessions.
- Reader intent can recover after configuration/process recreation using stable identity/revision/offset, never native handles.

## Free / Pro business model

### Free is a complete reader
Free includes import/re-decode, large-file reading, search, chapters, bookmarks, reading settings, base system TTS, auto paging, sleep timer, exact per-book Clean rules, Smart Clean scanning and candidate preview.

### Pro Lifetime sells automation and reusable local assets
One-time Google Play product: `jingdu_pro_lifetime`.

Pro includes:
- apply selected Smart Clean candidates;
- safe whole-line wildcard rules;
- reusable global rule library and recommended patterns;
- global rule JSON import/export;
- offline TTS voice selection when the installed engine provides offline voices;
- local settings/global-rule backup and restore.

Do not move search, chapters, bookmarks, basic themes or basic TTS behind Pro. A subscription is not part of the current model because the current product has no recurring cloud/server service.

## Product principles

1. **Offline/privacy by architecture** — no account, advertising SDK, analytics SDK or book-text upload. Google Play Billing/Review are platform services, not reading telemetry.
2. **TXT depth over format breadth** — do not add EPUB/PDF merely to match feature lists.
3. **Free sells trust; Pro sells saved work** — pay after value is visible, not at first launch.
4. **Correctness before decoration** — encoding, offset domains, immutable revisions, crash recovery and cache validation are product features.
5. **Progressive disclosure** — Library/Reader stay calm; advanced tools live in contextual sheets.
6. **Large-file credibility** — 10/100/300 MiB are normal qualification sizes.
7. **User-owned assets** — rules/settings can be exported locally; backups deliberately exclude book正文.

## Information architecture

### Library
- Brand/privacy promise.
- Recent/all books with progress, encoding, size and last-read state.
- Primary import plus multi-select batch import.
- Card tap continues reading; destructive action removes only the private copy.

### Reader
- Top: back, title, search, chapters, more.
- Center: typography-first page surface.
- Bottom: previous, position slider, next, TTS.
- More: bookmarks, Clean, encoding/re-decode, reading settings, delete.

### Clean
- Original/Clean switch and export.
- Free Smart Clean scan first; candidates show reason/count/confidence/text before purchase.
- Pro CTA appears only when the user tries to apply selected suggestions or use Pro rule assets.
- Per-book rules and Pro global rule library stay visible and editable.

### Settings
- Reading appearance and base TTS remain free.
- Pro offline voice selector exposes only voices that the Android TTS engine marks as not requiring network.
- Pro local backup exports settings/global rules only.

## Growth position

Default ASO targets the high-intent category phrase `TXT 小说阅读器`, while Custom Store Listings split discovery by intent: TXT reader, encoding rescue, Smart Clean/noise removal and local/private novel reading. Marketing copy must describe implemented behavior; keyword stuffing or unsupported performance claims are prohibited.

## Non-goals for 2.x

- Online bookstore/OPDS/social/community.
- Mandatory sync/account.
- In-reader advertising.
- AI features that upload private book text.
- Subscription without a real recurring service.
- Arbitrary regex execution on whole books.
- Compatibility layers for pre-2.x experimental private state.

## Success measures

Release/test objectives, without adding an analytics SDK:
- typical TXT imports to a readable page without configuration;
- valid large revisions reopen from cache without UI-thread stalls;
- Smart Clean finds real repeated promotional noise while ordinary content remains untouched until explicit apply;
- Free users can evaluate Smart Clean value before any paywall;
- Play purchase/restore survives reinstall and cached verified Pro remains usable offline;
- Reader controls remain usable at 200% font scale and adaptive widths;
- store listing conversion is tested by Play experiments/custom listings rather than keyword stuffing;
- Android retains no direct `INTERNET` permission and no advertising/analytics runtime SDK.