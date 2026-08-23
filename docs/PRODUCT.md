# Product — 净读 TXT

## Positioning

**净读 is a privacy-first, offline TXT novel reader for Chinese long-form reading.** It does not compete on format count. Its promise is narrower and stronger:

> Open messy TXT correctly, find distracting text locally, clean it safely, and keep reading comfortably for hours.

The public store name is localized for discovery (`净读 - TXT 小说阅读器`, `淨讀 - TXT 小說閱讀器`, `Jingdu - Offline TXT Reader`), while the in-app brand follows the active app locale. English UI/store support makes the product usable on non-Chinese system locales; it does not expand 2.x into a generic multi-format or English-first ebook reader.

## Primary users

1. Readers who keep local TXT novels/archives and want a calm daily reader.
2. Users who regularly meet GB18030/GBK/Big5/UTF-16 mojibake.
3. Web-novel readers whose TXT contains repeated ads, site tails, URLs or watermarks in Simplified or Traditional Chinese.
4. Long-session readers who care about large-file responsiveness, TTS and position recovery.
5. Privacy-conscious users who do not want accounts, cloud upload, ads or analytics in the reading path.
6. Chinese-text readers whose device/app UI language may be Simplified Chinese, Traditional Chinese or English.

## Core jobs

### Open it correctly
- Single or multi-select local TXT import with AUTO encoding detection.
- Manual re-decode from the retained private source copy without picking the external source again.
- Source bytes are never modified/deleted.
- Immutable normalized revisions and reusable `.jdx` sparse-index cache keep large-file reopen credible.

### Make it clean
- Free exact per-book find/replace/delete rules.
- Free local Smart Clean scan and preview for repeated lines, URLs/domains and common Simplified/Traditional promotional/watermark patterns.
- Shared Core returns stable locale-neutral reason codes; platform shells own localization.
- Pro applies Smart Clean suggestions in one action and unlocks safe whole-line `*` wildcard rules.
- Pro global rules are reusable user-owned assets; recommended Simplified and Traditional Chinese web-novel patterns can seed the library.
- Clean output is an immutable derived revision and can be exported without touching the source.
- Original progress/bookmarks never receive Clean offsets.

### Find it across Chinese scripts
- Exact search remains the primary operation.
- Android additionally tries curated, safe one-to-one Simplified/Traditional character variants and merges hits by document offset.
- This is a search convenience layer, not whole-document conversion; ambiguous character conversion is deliberately not guessed.

### Keep me in the text
- Library exposes recent books, progress, encoding and size.
- Search, chapters, bookmarks and progress seeking are direct reading tools.
- Paper/Light/Night, typography, margins, TTS, auto paging, sleep timer and volume-key paging support long sessions.
- Reader intent can recover after configuration/process recreation using stable identity/revision/offset, never native handles.
- When no explicit offline TTS voice is selected, Android infers a suitable `zh-CN`, `zh-TW`, `zh-HK` or English locale from visible document text; an explicit user voice always wins.

## Localization model

### Application UI
- Android first-class UI languages: `zh-Hans`, `zh-Hant`, `en-US`.
- `en-US` is the unqualified fallback for unsupported system languages.
- Android uses the platform per-app/system language mechanism and generated LocaleConfig; Jingdu does not maintain a competing language preference.
- UI locale never determines document encoding, Smart Clean behavior, search variants or TTS content-language detection.

### Google Play
- Default listings: `zh-CN`, `zh-TW`, `zh-HK`, `en-US`.
- Custom Listing and screenshot production specs exist for Simplified Chinese, Traditional Chinese and English.
- Taiwan/Hong Kong may share general Traditional copy until measured regional wording differences justify separate content.
- `jingdu_pro_lifetime` product title/description must be localized in the same four Play locales.

See `LOCALIZATION.md` for the terminal localization contract and CI rules.

## Free / Pro business model

### Free is a complete reader
Free includes import/re-decode, large-file reading, search, chapters, bookmarks, reading settings, base system TTS, auto paging, sleep timer, exact per-book Clean rules, Smart Clean scanning and candidate preview.

### Pro Lifetime sells automation and reusable local assets
One-time Google Play product: `jingdu_pro_lifetime`.

Pro includes:
- apply selected Smart Clean candidates;
- safe whole-line wildcard rules;
- reusable global rule library and recommended Simplified/Traditional patterns;
- global rule JSON import/export;
- offline TTS voice selection when the installed engine provides offline voices;
- local settings/global-rule backup and restore.

Do not move search, chapters, bookmarks, basic themes or basic TTS behind Pro. A subscription is not part of the current model because the current product has no recurring cloud/server service.

## Product principles

1. **Offline/privacy by architecture** — no account, advertising SDK, analytics SDK or book-text upload. Google Play Billing/Review are platform services, not reading telemetry.
2. **TXT depth over format breadth** — do not add EPUB/PDF merely to match feature lists.
3. **Chinese-content depth, multilingual shell** — Simplified and Traditional content get equal product attention; English UI improves accessibility/discovery without changing the content-first focus.
4. **Free sells trust; Pro sells saved work** — pay after value is visible, not at first launch.
5. **Correctness before decoration** — encoding, offset domains, immutable revisions, localization boundaries, crash recovery and cache validation are product features.
6. **Progressive disclosure** — Library/Reader stay calm; advanced tools live in contextual sheets.
7. **Large-file credibility** — 10/100/300 MiB are normal qualification sizes.
8. **User-owned assets** — rules/settings can be exported locally; backups deliberately exclude book正文.

## Information architecture

### Library
- Localized brand/privacy promise.
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
- Free Smart Clean scan first; candidates show localized reason/count/confidence/text before purchase.
- Pro CTA appears only when the user tries to apply selected suggestions or use Pro rule assets.
- Per-book rules and Pro global rule library stay visible and editable.

### Settings
- Reading appearance and base TTS remain free.
- Pro offline voice selector exposes only voices that the Android TTS engine marks as not requiring network.
- Pro local backup exports settings/global rules only.

## Growth position

Default Chinese ASO targets the high-intent category phrase `TXT 小说阅读器` / `TXT 小說閱讀器`; English discovery communicates offline/local TXT and Chinese encoding/cleanup depth. Custom Store Listings split discovery by intent: TXT reader, encoding rescue, Smart Clean/noise removal and local/private novel reading. Marketing copy must describe implemented behavior; keyword stuffing or unsupported performance claims are prohibited.

## Non-goals for 2.x

- Online bookstore/OPDS/social/community.
- Mandatory sync/account.
- In-reader advertising.
- AI features that upload private book text.
- Subscription without a real recurring service.
- Arbitrary regex execution on whole books.
- Generic EPUB/PDF/multi-format breadth caused solely by English UI support.
- Runtime whole-document Simplified/Traditional conversion.
- Compatibility layers for pre-2.x experimental private state.

## Success measures

Release/test objectives, without adding an analytics SDK:
- typical TXT imports to a readable page without configuration;
- valid large revisions reopen from cache without UI-thread stalls;
- Smart Clean finds real Simplified/Traditional repeated promotional noise while ordinary content remains untouched until explicit apply;
- Free users can evaluate Smart Clean value before any paywall;
- Play purchase/restore survives reinstall and cached verified Pro remains usable offline;
- Reader controls remain usable at 200% font scale and adaptive widths;
- Android resources remain complete for `en-US / zh-Hans / zh-Hant`, format placeholders stay aligned, and Compose AndroidTest resolves expectations from the active locale;
- store listing conversion is tested by Play experiments/custom listings rather than keyword stuffing;
- Android retains no direct `INTERNET` permission and no advertising/analytics runtime SDK.
