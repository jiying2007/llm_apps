# Quality Gates

A change is mergeable only when every applicable source gate passes. `main` protection remains intentionally disabled; this document is still the definition of done.

## Hosted source gates

1. **Native core** — Release CMake, `-Wall -Wextra -Wpedantic -Werror`, CTest and clang-tidy. Coverage includes encoding/SHA/revision, malformed UTF-8, large-file/concurrency, `.jdx` recovery, Simplified/Traditional Smart Clean candidates and whole-line wildcard golden behavior.
2. **Android product** — Kotlin/Compose Debug/Release compile, Debug/Release lint, Debug APK, Release AAB, AndroidTest assembly and supported JNI ABIs.
3. **Android performance** — hosted Macrobenchmark executes open/page/scroll/chapter/settings journeys, enforces checked-in frame P95/P99 SLOs and completes the Reader V3 Baseline Profile journey with retained evidence.
4. **Android localization** — `en-US / zh-Hans / zh-Hant` resource keys and format placeholders match; English is the unqualified fallback; generated LocaleConfig remains enabled; manifest/app/accessibility/runtime messages are resource-backed; major presentation/controller files cannot reintroduce hard-coded CJK UI copy; AndroidTest resolves expected UI text from the active locale.
5. **Android commercial UX** — Free reader stays complete; Smart Clean candidate content is visible before paywall; Pro actions are contextual; Billing uses `jingdu_pro_lifetime`; backup/offline voice/global-rule UI exists; no first-launch paywall/review prompt.
6. **Play store contract** — metadata length/policy checks for `zh-CN / zh-TW / zh-HK / en-US`, localized Custom Listing/screenshot production specs, Billing/Review dependency versions and fixed lifetime product id.
7. **Large-file path** — immutable revisions, validated `.jdx`, active-session Search/Chapters, bounded/streaming Smart Clean and safe fallback/pruning.
8. **Harmony source contract** — Stage/Node-API/TaskPool/storage/source contracts remain valid; real HAP/device qualification is a separate Harmony release gate.
9. **Repository contract** — required product/growth/store/localization docs exist; no legacy roots, compatibility core, floating Actions tags, committed packages/signing material or direct Android `INTERNET` permission.

## Android merge acceptance

Before Ready/merge:
- exact PR head passes all six Hosted jobs: `native-core`, `android`, `android-performance`, `play-store-contract`, `harmony-contract`, `terminal-contract`;
- no unresolved PR review thread/comment remains;
- Product/Requirements/UX/Growth/Localization/Core Contract/Testing/Device Matrix/Release/Play setup docs agree with implementation;
- Free/Pro boundary does not lock basic reader functionality;
- Smart Clean and wildcard rule tests prove deterministic local behavior;
- Simplified and Traditional document behavior is independent of UI locale;
- cross-script search fallback uses curated one-to-one variants and never silently rewrites document text;
- Billing/Review failures remain non-blocking to Free reading;
- user backup contains no book正文;
- whole-file work remains off Android main thread.

## Android v2.2 commercial release gate

Source merge is not the same as Play production readiness. Before v2.2 staged rollout:

### Build/signing
- exact `main` candidate source gates green;
- `androidStoreCheck` green with production package/version;
- signed APK/AAB reuse the retained v2.0 upload key;
- APK/AAB signing verification, R8 mapping, SHA-256 manifest and certificate fingerprint archived;
- immutable v2.2 tag/release provenance.

### Google Play commerce
- one-time INAPP product `jingdu_pro_lifetime` exists and is active;
- product title/description are localized for `zh-CN / zh-TW / zh-HK / en-US`;
- localized price is configured (initial experiment may compare US$4.99/$6.99/$8.99 equivalents);
- license tester validates purchase, cancel, pending, acknowledge, restore after reinstall and offline verified ownership;
- purchase UI displays Play `formattedPrice`;
- no subscription is configured for v2.2.

### Store discovery
- default `zh-CN / zh-TW / zh-HK / en-US` metadata uploaded from repository assets;
- screenshot/feature graphic follows the matching locale brief under `store/play/`;
- Custom Listings are created for relevant search keyword clusters when Play traffic supports them;
- listing claims avoid unsupported superlatives/performance promises;
- English listing does not imply EPUB/cloud catalog/English-first content scope;
- privacy/data-safety declarations match no direct INTERNET permission, no ads/analytics SDK and no text upload.

### Locale/device qualification
- launch/navigate Library, Reader, Clean and Settings under `zh-CN`, `zh-TW`, `zh-HK`, `en-US`;
- unsupported system locale falls back to English;
- per-app language changes do not change document identity, progress, bookmarks, rules or pinned TTS voice;
- 200% font scale and TalkBack remain usable in Simplified Chinese, Traditional Chinese and English;
- TTS auto content-language selection and explicit offline voice override are verified against installed engine behavior.

### Rollout
- internal/closed test first;
- staged production rollout with Play Vitals/crash/ANR/refund monitoring;
- rollback artifact/version plan exists;
- listing and price experiments change one major variable at a time.

## Review/privacy guardrails

- Play In-App Review only after meaningful local milestones and local cooldown.
- No sentiment pre-screen or fabricated rating prompt.
- Google Play Billing/Review receives no private TXT content.
- No advertising or runtime analytics SDK is introduced for growth.

## Harmony production gate

HarmonyOS remains source-complete/pre-release until official toolchain HAP build and device matrix execute on `self-hosted,harmonyos`. A queued workflow is not success, but does not block Android-only v2.x source merge/release. When Harmony localization is activated it must use platform-native resources and the same locale-neutral shared-Core contracts defined in `LOCALIZATION.md`.

## Device gates

Android device qualification covers 10/100/300 MiB, first-open/reopen/cache recovery, lifecycle, all supported app locales, cross-script search, Simplified/Traditional Smart Clean, purchase/restore, backup, offline voice/TTS locale behavior, accessibility and adaptive windows. Cross-platform golden parity blocks only a claim that both Android and HarmonyOS are jointly production-ready.
