# Product Maturity — Android Reader

This document tracks the transition from a feature-complete GitHub release to a mature, supportable Google Play product. It does **not** change the current repository-governance decision: `main` protection remains optional for the current GitHub/debug-signed stage and is not enabled by this work.

## Maturity principle

Jingdu is no longer optimized for feature count. The product maturity priority is:

1. core reading promises almost never fail;
2. failures preserve the last valid user state and are diagnosable without collecting book text;
3. production artifacts are traceable, symbolicated and compatible with current Android platform requirements;
4. real-device and Play evidence is recorded against the exact immutable source candidate;
5. Free remains a complete reader and Pro remains lifetime automation/reusable local assets.

Format breadth, cloud accounts, online AI, advertising and subscription remain non-goals unless a future product decision changes the positioning contract.

## Repository-enforced maturity gates

The following are source/CI evidence and may be enforced automatically:

- Android uses pinned stable NDK `29.0.14206865`.
- Release builds generate `FULL` native debug symbols for Play/native crash symbolication.
- `verify-android-16k-page-size.sh` builds release APK/AAB and verifies 16 KiB ZIP alignment plus every packaged native ELF `LOAD` alignment.
- Hosted `android-functional` installs the Android 15 `google_apis_ps16k` x86_64 system image, refuses to proceed unless `adb shell getconf PAGE_SIZE` is exactly `16384`, then executes the full Android instrumentation suite. `NativePageSizeSmokeTest` loads JNI/Core, hashes and opens/reads a UTF-8 file on that same 16 KiB runtime.
- AndroidTest compilation alone is no longer acceptance; Compose UI, paging regression, portable-user-assets and diagnostics tests execute in hosted CI.
- Existing hosted Macrobenchmark thresholds/baselines remain unchanged and are still an independent release gate on its pinned standard emulator environment.
- Reader hardware-key routing supports previous/next via arrows/PageUp/PageDown and search via Ctrl+F while an active panel keeps normal text input and only intercepts Escape.
- Physical performance workflow requires an explicit source tag/SHA and records checked-out SHA plus OEM/model/API/fingerprint in the evidence artifact.
- Billing purchase-state and authoritative/offline entitlement behavior is isolated in a pure tested policy.
- Immutable private-file publication is isolated and tested for existing-target and failed-publication recovery.
- Bounded TTS sentence/paragraph navigation has host tests for Unicode/code-point and paragraph-boundary behavior; real TTS engines/routes remain physical evidence.
- The existing user-triggered privacy-audit export contains bounded support diagnostics (device/build/storage class + stable error codes) without paths, URIs, search queries, purchase tokens or book text.
- Smart Clean held-out evaluation combines the manually curated v1 corpus with a checked-in v2 adversarial matrix and must stay above the minimum production-scale row counts while retaining zero auto-AD hard-negative false positives.

`scripts/verify-product-maturity.sh` guards these source contracts against silent regression.

## External P0 evidence — must be real, never fabricated by CI

These rows remain fail-closed until actual device/Play evidence exists for the exact candidate:

### Physical Android qualification

- [ ] API 26 physical device.
- [ ] API 36 physical device.
- [ ] At least two OEM families.
- [ ] `zh-CN`, `zh-TW`, `zh-HK`, `en-US` and unsupported-locale English fallback.
- [ ] 200% font scale and TalkBack on Library/Reader/Clean/Settings.
- [ ] 10 / 100 / 300 MiB import/read/search/chapter/Clean journeys.
- [ ] UTF-8, UTF-16, GB18030/GBK, Big5, malformed and truncated samples.
- [ ] process death / reopen / background-foreground recovery.
- [ ] low-storage/write-failure recovery without replacing the last valid private source/revision.
- [ ] wired and Bluetooth TTS route + transient/permanent audio-focus behavior.
- [ ] hardware keyboard navigation on a suitable large-screen/desktop-class Android target.
- [ ] real volume-key paging advances authoritative source position.
- [ ] physical Reader performance meets the release SLO (`P95 <= 40 ms`, `P99 <= 80 ms`).

Every physical artifact must contain `provenance.txt` binding `source_ref` and `source_sha` to device metadata.

### Google Play commerce

Using license testers and `com.junchen.jingdu`:

- [ ] `jingdu_pro_lifetime` active and localized in zh-CN / zh-TW / zh-HK / en-US.
- [ ] UI price is Play `formattedPrice`.
- [ ] PURCHASED unlocks and is acknowledged.
- [ ] PENDING does not unlock.
- [ ] cancellation does not unlock.
- [ ] reinstall/clear-data restore succeeds for the same account.
- [ ] last Play-verified ownership remains usable offline.
- [ ] successful authoritative no-ownership refresh revokes stale cached entitlement.
- [ ] Billing unavailable/product unavailable leaves every Free Reader path usable.

The current no-backend lifetime model intentionally accepts some piracy risk in exchange for zero account/server dependency. A minimal purchase-token verification service should be considered only when revenue justifies that tradeoff; it must never receive book/reading data.

### Production artifact provenance

- [ ] production/upload-signed AAB generated from the immutable source tag.
- [ ] AAB SHA-256 recorded.
- [ ] versionCode/versionName recorded.
- [ ] upload/production signing certificate fingerprint recorded.
- [ ] R8 `mapping.txt` archived.
- [ ] native debug symbols archived/included for Play.
- [ ] successful 16 KiB package/ELF compatibility evidence attached to the exact source candidate.
- [ ] Play app-bundle/pre-launch validation succeeds for the exact AAB.
- [ ] each staged rollout expansion records tag/commit/AAB checksum and timestamp.

## Rollout health targets

Play production rollout should expand only while the exact installed build has acceptable Android vitals, store feedback and commerce behavior. Google Play bad-behavior thresholds are external platform limits, not internal product goals; Jingdu should target materially lower crash/ANR rates before increasing rollout percentage.

No runtime analytics/advertising SDK is required. Support diagnostics remain user-triggered local export, while Android vitals provides store-level production stability evidence.

## Maintainability direction

Large presentation/orchestration files should be reduced incrementally, never by a high-risk architecture rewrite. New maturity work should preferentially extract testable policy/coordinator components at existing seams. This maturity pass begins that direction with billing entitlement policy, immutable file publication and privacy-safe error logging; further MainActivity/Reader presentation extraction should continue only in behavior-preserving PRs with canonical performance evidence.

## Product-growth restraint

Before the external P0 evidence above is complete, do not prioritize:

- EPUB/PDF/MOBI breadth;
- cloud sync/accounts;
- bookstore/community;
- remote AI over private book text;
- subscription without a recurring service;
- advertising or runtime growth analytics.

The mature-product goal is not more features. It is a local TXT reader whose import, reading, repair, TTS, privacy, purchase and recovery paths are boringly reliable.
