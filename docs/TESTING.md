# Testing Strategy

## Shared native core

Every `core/native` change passes Release build with `-Wall -Wextra -Wpedantic -Werror`, CTest and clang-tidy analyzer/bugprone/performance/portability gates.

Required automated coverage:
- SHA/file hashing;
- UTF-8/UTF-16/GB18030/Big5 detection and truncated sample boundaries;
- malformed UTF-8 rejection;
- character/byte indexing, bounded reads, search, chapters and speech segmentation;
- deterministic repair revisions;
- literal repair export;
- Smart Clean candidate detection with exact occurrence counts;
- safe whole-line wildcard export (`@g` packed rule) removing matching promotional lines while preserving ordinary正文;
- persistent `.jdx` creation, corrupt/stale fallback and repair;
- large-file stress, concurrent reads and handle lifecycle.

Tests never rely on C/C++ `assert` because Release builds define `NDEBUG`.

## Smart Clean correctness

Smart Clean is advisory until explicit apply. Tests verify:
- high-confidence URL/promotion/repeated lines are discoverable;
- ordinary low-frequency正文 is not automatically converted into a candidate solely because it exists;
- candidate results contain score/count/reason/text and bounded result count;
- whole-line `*` rules match the trimmed whole line, not arbitrary regex;
- literal rules retain existing semantics;
- invalid/oversized packed fields are ignored/rejected safely;
- applying rules still writes an immutable derived file and never mutates normalized/source input;
- portable correction-memory backup contains only `bookId + fingerprint + decision`, declares `containsBookText=false`, and round-trips KEEP/DELETE/PROTECT without candidate text.

## Android Free / Pro contract

Automated source/UI contracts verify:
- Free retains import/re-decode, search, chapters, bookmarks, reading settings, base TTS, exact per-book rules and Smart Clean scan/preview;
- `jingdu_pro_lifetime` is the only Android 2.3.x Pro product id;
- Billing Library stays on the approved pinned version in product contract;
- `PURCHASED` is required for unlock and completed purchases are acknowledged;
- restore uses `queryPurchasesAsync` and Billing failure does not block Free UI;
- Smart Clean candidate text is visible before Pro CTA;
- whole-line wildcard/global rule/portable-backup/offline voice actions require Pro;
- price text comes from Play product details rather than a hard-coded currency value.

License-tester device validation additionally covers fresh purchase, cancellation, pending purchase, restore after reinstall, offline launch after verified ownership and authoritative no-ownership refresh.

## Portable local-user asset contract

Reader V3 backup schema 4 is bounded, local and text-free. Automated/instrumented tests verify:

- Reader settings use the typed Reader V3 settings import validation;
- global-rule JSON retains its versioned bounded schema and field/count limits;
- annotations remain bounded and keep source/context anchors;
- favorites/tags are portable by source identity;
- progress is staged with the source id and exact `normalizedSha256` and is consumed only by that revision;
- a mismatched normalized revision cannot consume staged progress;
- reading session/pace backup contains identifiers/timestamps/counts only;
- Smart Clean feedback backup contains one-way fingerprints/decisions only;
- backup root declares `containsBookText=false`;
- schema 3 Reader V3 settings/rules/annotation backups remain importable for pre-production testers;
- SAF folder URI grants and unavailable imported font binaries are re-selected rather than represented as portable credentials.

`PortableUserAssetsTest` covers revision-bound staged progress, Smart Clean text-free feedback round-trip and bounded reading-stat restore on AndroidTest.

## User asset safety

- Local backup never contains source/normalized/clean book text or book files.
- Backup import validates version/types/ranges before applying bounded state.
- Selected TTS voice is persisted only by system voice name; unavailable voices fall back without breaking base TTS.
- Only voices reporting `isNetworkConnectionRequired == false` are offered as Pro offline voices.
- Batch import is SAF multi-select, bounded to the configured per-operation maximum and reports partial failures.

## Review UX

Play In-App Review source/device tests verify:
- no first-launch request;
- no sentiment pre-screen;
- only meaningful local milestones increase eligibility;
- local cooldown prevents repeated requests;
- review API failure is non-blocking.

## Store / ASO contract

`./scripts/verify-play-store.sh` is a Hosted CI gate. It verifies:
- default zh-CN/en-US metadata exists;
- title/short/full descriptions stay within Play limits;
- zh-CN title is `净读 - TXT 小说阅读器`;
- prohibited title promotion/superlative patterns are absent;
- four search-intent Custom Listing specs exist;
- Billing/Review dependency versions and `jingdu_pro_lifetime` remain pinned/declared.

Store listing experiments and Custom Listing conversion are Play Console evidence, not simulated by source CI.

## Storage/session contract

Both platform shells keep:
- immutable `document-<normalizedSha256>.txt` and `clean-<repairRevision>.txt`;
- `.jdx` as disposable non-identity cache;
- source identity stable across identical bytes;
- decode revision changes isolated from old progress/bookmarks;
- candidate session publication before old-session close/prune;
- Clean offsets isolated from normalized progress/bookmarks.

Portable progress restore adds one further invariant: staged progress never crosses a normalized-revision boundary.

## Android UI / performance

Hosted Android gate compiles Debug/Release, lint, Debug APK, Release AAB, AndroidTest and JNI ABIs.

Device review covers:
- edge-to-edge/predictive back;
- 200% font scale/TalkBack/48dp targets;
- compact/landscape/split/tablet/foldable windows;
- viewport paging and slider commit-on-release;
- configuration/process restoration;
- ACTION_VIEW/ACTION_SEND and multi-select import;
- Smart Clean/Pro/settings/portable-backup surfaces;
- TTS/audio focus/voice selection, auto page and sleep timer.

10/100/300 MiB qualification verifies no file-size-proportional work intentionally runs on the main thread, Search/Chapters reuse active session, repeat open uses valid `.jdx`, corrupt cache rebuilds and Clean/Smart Clean remain bounded-memory streaming operations.

Hosted Macrobenchmark/Baseline Profile is a regression gate, not physical-device product evidence. `PRODUCTION_READINESS.md` requires the real-device matrix and release SLO evidence before production rollout.

## Repository/source provenance

Source-release tests/contracts verify:
- Android source/staging versions match the permanent release manifest;
- publication runs only after all six hosted jobs;
- existing source tags are never moved;
- new source tags are annotated objects resolving to the exact gated `main` commit;
- the annotated tag message binds the checked-in source-manifest SHA-256;
- interrupted publication may complete only when an orphan tag already resolves to the exact gated SHA;
- source publication never claims signed artifact, physical-device or Play production evidence.

GitHub branch/tag protection itself is repository-administration evidence and therefore remains a required external P0 row in `PRODUCTION_READINESS.md`; CI must not fabricate that setting.

## HarmonyOS

Hosted CI verifies source/bridge/storage contracts. Real HAP/device validation remains on the official `self-hosted,harmonyos` runner and is not an Android-only 2.3.x source-merge blocker.

## Cross-platform parity

Before declaring both platforms jointly production-ready, compare golden corpus source SHA, normalized SHA, encoding, character count, reads, search/chapter offsets, repair revision and clean-output SHA. Smart Clean candidate parity should also be included once Harmony exposes the matching UI/API. `.jdx` is excluded from semantic identity.
