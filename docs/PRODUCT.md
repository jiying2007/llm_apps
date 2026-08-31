# Product — 净读 TXT

## Positioning

**净读 is the offline cleaner-reader for Chinese long-form TXT.** It deliberately competes on TXT depth rather than format count.

> **Long TXT · Smart Clean · Fully local**  
> **长篇 TXT · 智能净化 · 完全本地**

The terminal promise is:

> Open messy TXT correctly, become readable quickly, diagnose what is wrong, repair structure/noise safely, keep very long books responsive, and preserve long-session reading without sending book text anywhere.

Store names remain localized for discovery: `净读 - TXT 小说阅读器`, `淨讀 - TXT 小說閱讀器`, `Jingdu - Offline TXT Reader`.

## Current product generation

- Shared Core ABI: **v2**.
- Smart Clean: **generation 4**.
- Built-in deterministic clean signature pack: **v3**.
- Chinese display conversion: OpenCC-compatible **OpenccJava 1.4.2**.
- Android product line/source release: **2.3.x / Reader**.
- Portable local-user backup schema: **v4** (`schema=4`, Reader; schema 3 remains importable for pre-production testers).
- First-class Android UI locales: **zh-Hans / zh-Hant / en-US**.

Detailed intelligence architecture lives in `SMART_CLEAN_ARCHITECTURE.md` and `COMPETITIVE_MOAT.md`.

## Primary users

1. Readers with local Chinese TXT novels/archives who want a calm daily reader.
2. Users who encounter GB18030/GBK/GB2312/Big5/UTF-16 mojibake.
3. Web-novel readers whose TXT contains ads, watermarks, URLs, repeated tails, malformed fragments or noisy chapter structure.
4. Long-session readers who care about 20/100/200 MiB responsiveness, stable progress/bookmarks and background TTS.
5. Readers with many TXT files who benefit from explicit folder libraries and batch automation.
6. Privacy-conscious users who do not want accounts, ads, analytics, cloud upload or remote AI in the reading path.

## Product loop

```text
TXT / user-selected TXT folder
  -> bounded first-readable preview
  -> immutable private source + normalized revision/index
  -> TXT Doctor
  -> Smart TOC
  -> Smart Clean 4
  -> reading / OpenCC display / background TTS
  -> reusable local rules, corrections, feedback and optional Pro batch automation
```

## Open it correctly and quickly

- Single TXT import, multi-select import and user-selected SAF folder roots.
- AUTO encoding detection plus manual re-decode from the retained private source copy.
- Source bytes are never modified or deleted.
- New single-book import can render a disposable bounded preview from at most 512 KiB / 12,000 code points before full canonical private import/normalization/indexing completes.
- The preview is never treated as the authoritative document revision.
- Folder roots use persisted read-only SAF URI permission; no broad storage permission is requested.
- Folder synchronization uses document identity + size + last-modified metadata to skip unchanged files. Providers that do not supply reliable metadata are conservatively re-imported rather than silently missing changes.
- Content-addressed immutable normalized revisions and `.jdx` sparse-index caches remain the canonical large-file architecture.

## TXT Doctor

TXT Doctor turns technical subsystems into one user-understandable health report:

- encoding/text integrity;
- malformed/replacement-character samples;
- chapter/TOC quality;
- Smart Clean candidate count/cleanliness;
- combined TXT health score.

Diagnosis uses bounded Core windows and stores scores/counts only, never sampled book text.

## Smart TOC

- Core source code-point offsets remain authoritative.
- Base chapter detection is augmented with verified Chinese special headings such as 序章、楔子、番外、后记/後記、尾声/尾聲 and 大结局/大結局.
- TOC quality reports duplicate titles, nearby numeric gaps and suspicious overlong/prose-like headings.
- Users may hide an incorrectly detected heading or add a chapter at the current source offset.
- These repairs are local metadata overlays; they never rewrite TXT or change the persisted reading coordinate system.

## Smart Clean 4

Smart Clean is precision-first and explainable.

### Detection stack

1. deterministic versioned built-in signatures;
2. shared native URL/promotion/repetition statistics;
3. bounded streaming inline-fragment/malformed-line refinement;
4. candidate-only tiny local semantic classifier;
5. local KEEP / DELETE / PROTECT feedback memory.

Every normal candidate remains inspectable with reason, risk, count, approximate impact and sample text before apply. Inline/garbled and semantic-BODY evidence stays conservative.

### Tiny local model

- accepts one pre-filtered candidate only, maximum 512 characters;
- 64 hashed character-bigram buckets with signed-int8-style weights and auditable structural features;
- outputs BODY / AD / UNCERTAIN;
- wide UNCERTAIN range and strong chapter-heading protection;
- never opens a file, receives a whole document or uses a network/runtime ML service.

Training and held-out hard-negative evaluation are checked into the repository and CI verifies exact reproducibility plus precision-first safety gates.

### User correction memory

- `KEEP`: do not auto-delete this candidate for the book and contributes a keep signal.
- `DELETE`: explicit user-owned delete signal.
- `PROTECT`: strongest protection signal for batch automation.
- Only SHA-256-derived candidate fingerprints + decision metadata are stored; candidate/book text is not retained in the feedback store.
- Portable backup stores only `bookId + fingerprint + decision`; it never stores candidate text.

### Apply / undo

Selected cleanup becomes explicit local rules and an immutable derived Clean revision. The source TXT stays untouched. The immediately previous rule set is retained as a rule-only one-step undo snapshot; history does not copy book text.

## Chinese display conversion

OpenCC-compatible conversion is a display layer, not a source rewrite.

Modes:
- Original;
- Simplified (`t2s`);
- Traditional (`s2t`);
- Taiwan (`s2tw`);
- Taiwan phrases (`s2twp`);
- Hong Kong (`s2hk`).

Conversion applies only to bounded reader/search/chapter/TTS strings. Source TXT, normalized revision, bookmarks, search offsets, TOC offsets and progress remain in normalized source code-point coordinates. Up to 200 local `source => target` phrase overrides take priority over OpenCC and participate in local backup.

## Keep me in the text

- Library: recent/progress, favorite, local tags, unread/reading/finished filters and recent/name/progress sort.
- Search, Smart TOC, bookmarks and direct progress seeking.
- Source-identity bookmarks survive encoding rescue.
- Progress persistence is throttled and force-flushed at lifecycle/navigation boundaries.
- Paper/Light/Night, typography, margins, auto paging and sleep timer.
- Reader intent restores from stable book/revision/offset identity, never native handles.
- Portable backup stages progress against `sourceSha256 + normalizedSha256`; stale progress is never applied to a different normalized revision.

## Professional local TTS

- Basic system TTS remains local/free.
- Android can host long read-aloud in an `exported=false` foreground `mediaPlayback` service.
- Framework `MediaSession` provides lock-screen and headset play/pause/next/previous/stop controls.
- Read-aloud source offsets are broadcast only inside the app package and synchronize the reader position/current-window highlight.
- Existing audio-focus behavior pauses/resumes transient interruptions without skipping the interrupted chunk; permanent focus loss stops.
- Installed offline voice selection remains a Pro convenience when the system TTS engine exposes offline voices.

## Folder library and Pro batch automation

Folder libraries are available through explicit user-selected SAF roots and remain a local organization feature.

Pro sells saved repetitive work:

- batch dry-run across up to 100 library books;
- combined Smart Clean + TOC health report;
- explicit batch apply of precision-first safe cleanup candidates;
- reusable global rules/recommended rule packs;
- global-rule import/export;
- offline TTS voice selection;
- portable local-user backup/restore of text-free Reader assets.

Batch apply excludes KEEP/PROTECT, semantic BODY, inline fragment and garbled-line candidates unless an explicit DELETE decision makes the user intent authoritative. Batch reports contain identifiers/names/scores/counts only and declare `containsBookText=false`.

## Portable local-user assets

Reader schema-4 backup intentionally excludes book/source/normalized/Clean payloads but preserves the user-owned state that can safely travel:

- Reader settings and custom presentation preferences;
- global Clean rules;
- bookmarks, highlights and notes with source/context anchors;
- favorites and local tags;
- progress staged against exact source + normalized revision identity;
- local reading sessions and pace;
- Smart Clean KEEP/DELETE/PROTECT fingerprint memory.

SAF URI grants are device/install capabilities and therefore must be re-selected on a destination installation. Imported font binaries are likewise re-selected when the referenced local font is not available. The backup declares `containsBookText=false`.

## Verifiable privacy

Privacy is an architectural feature and an in-app verifiable fact:

- Android Manifest has no INTERNET permission;
- no broad storage permission;
- no account;
- no advertising SDK;
- no runtime analytics SDK;
- no book-text upload capability;
- no remote clean rules or semantic inference.

The app can export a privacy audit containing configuration/counts only, never book text. Third-party OpenCC/OpenccJava legal assets are retained in the repository and packaged into Android application assets.

## Performance position

Performance is a product feature. Qualification uses 1–5 / 20 / 100 / 200 MiB workloads, with 300 MiB as an extended stress class.

Architectural invariants:

- managed UI never owns the whole canonical document;
- bounded native reading windows;
- content-addressed immutable revisions;
- reusable validated sparse indexes;
- first-readable preview is separated from full canonical import work;
- Smart Clean whole-document work streams with bounded memory;
- semantic inference is candidate-only.

Release-device targets and hosted Core gates remain defined in `PERFORMANCE_SLO.md`. Hosted CI is not a substitute for physical-device startup/frame/memory evidence.

## Free / Pro business model

### Free is a complete reader

Free includes:
- import/re-decode and folder library;
- large-file reading/index cache;
- TXT Doctor and Smart TOC;
- search, chapters, bookmarks, progress;
- themes/typography;
- OpenCC display conversion and local phrase overrides;
- basic system TTS, auto paging and sleep timer;
- exact per-book Clean rules;
- Smart Clean scan/preview and local correction visibility.

### Pro Lifetime sells automation and reusable local assets

One-time Google Play product: `jingdu_pro_lifetime`.

Pro includes:
- one-action application of selected Smart Clean suggestions and one-step rule undo;
- safe whole-line wildcard rules;
- reusable global rule library / recommended patterns;
- global-rule JSON import/export;
- batch diagnostics + explicit safe batch apply;
- selectable installed offline TTS voices;
- portable local-user backup/restore.

No subscription is justified while there is no recurring cloud/server service. Do not move basic reading, search, TXT Doctor, Smart TOC, bookmarks, themes or base TTS behind Pro.

## Product principles

1. **TXT depth over format breadth.**
2. **Offline/privacy by architecture and verification.**
3. **Precision before deletion.** Missing an ad is preferable to deleting prose.
4. **Explain before apply; preserve undo.**
5. **Source/Core offsets are the permanent coordinate system.**
6. **First readable before background completeness where safely possible.**
7. **Free sells trust; Pro sells saved repetitive work.**
8. **Chinese-content depth, multilingual shell.**
9. **Long-session reliability is a product feature.**
10. **User-owned local assets compound value without an account.**

## Non-goals

- Online bookstore/OPDS/social/community.
- Mandatory account or cloud sync.
- In-reader advertising.
- AI features that upload private book text.
- Subscription without a real recurring service.
- Arbitrary whole-book regex execution.
- Generic EPUB/PDF/MOBI/multi-format breadth.
- Whole-document local LLM/BERT inference for cleanup.
- Whole-book Simplified/Traditional converted copies.
- Compatibility layers for pre-2.x experimental private state.

## Success measures

Without adding runtime analytics SDKs, release/support/store evidence should show:

- typical TXT reaches a readable preview/open state without encoding setup;
- source-identity progress/bookmarks survive encoding rescue;
- Smart TOC finds useful structure without changing source offsets;
- Smart Clean held-out auto-AD decisions maintain precision-first quality gates with no hard-negative BODY false positives;
- KEEP/DELETE/PROTECT memory never stores正文;
- Pro batch dry-run precedes explicit apply and never changes source TXT;
- background TTS survives ordinary Activity lifecycle changes and supports MediaSession controls;
- folder sync skips reliably unchanged documents and conservatively reimports unknown metadata;
- portable backup restores text-free user assets and applies progress only to the exact normalized revision;
- 20/100/200 MiB real-device qualification is recorded against `PERFORMANCE_SLO.md` / `DEVICE_MATRIX.md`;
- Android resources remain complete across en-US / zh-Hans / zh-Hant;
- Android retains no direct INTERNET/broad-storage permission and no ads/analytics runtime SDK;
- Google Play production is declared only after `PRODUCTION_READINESS.md` external evidence is complete.
