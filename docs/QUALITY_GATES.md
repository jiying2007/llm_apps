# Quality Gates

A change is mergeable only when every applicable source gate passes. `main` protection remains intentionally disabled; this document is still the definition of done.

## Hosted source gates

1. **Native core** — Release CMake, `-Wall -Wextra -Wpedantic -Werror`, CTest and clang-tidy. Coverage includes encoding/SHA/revision, malformed UTF-8, large-file/concurrency, `.jdx` recovery, Smart Clean candidates and whole-line wildcard golden behavior.
2. **Android product** — Kotlin/Compose Debug/Release compile, Debug/Release lint, Debug APK, Release AAB, AndroidTest assembly and supported JNI ABIs.
3. **Android commercial UX** — Free reader stays complete; Smart Clean candidate content is visible before paywall; Pro actions are contextual; Billing uses `jingdu_pro_lifetime`; backup/offline voice/global-rule UI exists; no first-launch paywall/review prompt.
4. **Play store contract** — metadata length/policy checks, default zh-CN title, four Custom Listing specs, Billing/Review dependency versions and fixed lifetime product id.
5. **Large-file path** — immutable revisions, validated `.jdx`, active-session Search/Chapters, bounded/streaming Smart Clean and safe fallback/pruning.
6. **Harmony source contract** — Stage/Node-API/TaskPool/storage/source contracts remain valid; real HAP/device qualification is a separate Harmony release gate.
7. **Repository contract** — required product/growth/store docs exist; no legacy roots, compatibility core, floating Actions tags, committed packages/signing material or direct Android `INTERNET` permission.

## Android merge acceptance

Before Ready/merge:
- exact PR head passes all five Hosted jobs: `native-core`, `android`, `play-store-contract`, `harmony-contract`, `terminal-contract`;
- no unresolved PR review thread/comment remains;
- Product/Requirements/UX/Growth/Core Contract/Testing/Release/Play setup docs agree with implementation;
- Free/Pro boundary does not lock basic reader functionality;
- Smart Clean and wildcard rule tests prove deterministic local behavior;
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
- localized price is configured (initial experiment may compare US$4.99/$6.99/$8.99 equivalents);
- license tester validates purchase, cancel, pending, acknowledge, restore after reinstall and offline verified ownership;
- purchase UI displays Play `formattedPrice`;
- no subscription is configured for v2.2.

### Store discovery
- default zh-CN/en-US metadata uploaded from repository assets;
- screenshot/feature graphic follows `store/play/SCREENSHOT_BRIEF.zh-CN.md`;
- Custom Listings are created for relevant search keyword clusters when Play traffic supports them;
- listing claims avoid unsupported superlatives/performance promises;
- privacy/data-safety declarations match no direct INTERNET permission, no ads/analytics SDK and no text upload.

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

HarmonyOS remains source-complete/pre-release until official toolchain HAP build and device matrix execute on `self-hosted,harmonyos`. A queued workflow is not success, but does not block Android-only v2.2 source merge/release.

## Device gates

Android device qualification covers 10/100/300 MiB, first-open/reopen/cache recovery, lifecycle, Smart Clean, purchase/restore, backup, offline voice, accessibility and adaptive windows. Cross-platform golden parity blocks only a claim that both Android and HarmonyOS are jointly production-ready.