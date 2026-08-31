# Production Readiness — Android

This document is the final external-evidence gate between a fully-gated **source release** and a **Google Play production rollout**. It is intentionally fail-closed: an unchecked item is not production evidence, and hosted source CI must never mark an external item complete on its own.

## Current boundary

- Source line: Reader / Android 2.3.x.
- GitHub source release: provenance only.
- Signed APK/AAB, physical-device qualification, Play product/listing state and rollout state are external release evidence.
- HarmonyOS has a separate HAP/device qualification chain and is not covered by an Android production declaration.

## P0 — repository governance

Before the first production staged rollout, capture actual GitHub administration evidence that:

- [ ] `main` is protected by branch protection or an equivalent ruleset;
- [ ] force-push and branch deletion are blocked;
- [ ] pull-request based changes are required for ordinary source changes;
- [ ] required status checks match the hosted source gates in `QUALITY_GATES.md`;
- [ ] `v*` tag update/deletion is blocked by repository rules where available;
- [ ] the candidate source tag resolves to the exact fully-gated `main` commit;
- [ ] new source tags are annotated provenance objects containing the source-manifest SHA-256.

The repository policy and publisher enforce intent, but only the actual GitHub settings prove platform enforcement.

## P0 — signed Android artifact

Run with the retained upload key and explicit monotonically increasing version values:

```bash
cd apps/android
./gradlew --no-daemon --no-configuration-cache --warning-mode all \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<production-code> \
  -PjingduVersionName=<source-semver> \
  androidStoreCheck writeAndroidReleaseChecksums
```

Archive and record:

- [ ] signed release AAB;
- [ ] optional signed release APK used for direct device qualification;
- [ ] R8 `mapping.txt`;
- [ ] `SHA256SUMS`;
- [ ] upload/signing certificate fingerprint;
- [ ] exact source commit/tag used to build the artifacts;
- [ ] successful Play pre-launch/app-bundle validation for the exact AAB.

Do not commit any signing key or signed binary to this repository.

## P0 — physical Android qualification

Execute `DEVICE_MATRIX.md` and `PERFORMANCE_SLO.md` on real release-derived builds. Minimum evidence:

- [ ] API 26 device plus current target-API device;
- [ ] at least two OEM families;
- [ ] `zh-CN`, `zh-TW`, `zh-HK`, `en-US` plus unsupported-locale English fallback;
- [ ] 200% font scale and TalkBack on the primary Library/Reader/Clean/Settings paths;
- [ ] 10 / 100 / 300 MiB TXT import/read/search/chapter/Clean journeys;
- [ ] UTF-8, UTF-16, GB18030/GBK, Big5 and malformed/truncated samples;
- [ ] process death / reopen / background-foreground recovery;
- [ ] low-storage/write-failure recovery without source/private-copy corruption;
- [ ] wired/Bluetooth TTS route and audio-focus interruption behavior;
- [ ] physical Reader Macrobenchmark meets the release SLOs in `PERFORMANCE_SLO.md`;
- [ ] physical volume-key paging advances the authoritative Reader source position.

Hosted emulator numbers are regression evidence only and cannot satisfy these rows.

## P0 — Google Play commerce

Using license testers and the production application id `com.junchen.jingdu`:

- [ ] create and activate one-time product `jingdu_pro_lifetime`;
- [ ] localize product title/description for `zh-CN`, `zh-TW`, `zh-HK`, `en-US`;
- [ ] verify displayed price comes from Play `formattedPrice`;
- [ ] successful purchase unlocks Pro and is acknowledged;
- [ ] cancellation does not unlock;
- [ ] pending purchase does not unlock;
- [ ] reinstall/clear-data restore succeeds on the same Play account;
- [ ] verified ownership remains usable offline;
- [ ] authoritative no-ownership refresh removes stale entitlement;
- [ ] product unavailable / Billing unavailable leaves all Free Reader paths functional.

## P0 — Play listing and policy

- [ ] default listings uploaded from `fastlane/metadata/android` for all four supported Play locales;
- [ ] screenshots captured from the actual release UI using synthetic/public-domain TXT;
- [ ] Custom Listings are applied only where Play supplies the matching keyword targeting capability;
- [ ] Data safety / privacy declarations match the no-book-upload / no-ads / no-runtime-analytics architecture;
- [ ] store contact/category/content declarations are complete;
- [ ] no screenshot or description contains unverified performance/ranking claims;
- [ ] In-App Review remains milestone/cooldown driven and is not a first-launch gate.

## P0 — rollout

- [ ] upload the exact qualified AAB to the intended Play track;
- [ ] complete internal/closed testing on Play-installed builds;
- [ ] verify purchase/restore on a Play-installed candidate;
- [ ] start a staged production rollout instead of immediate 100% exposure after the Reader + commerce hardening changes;
- [ ] record initial staged percentage and start timestamp;
- [ ] inspect Android vitals / crash / ANR / store feedback before each rollout expansion;
- [ ] record the commit/tag/AAB checksum associated with each rollout expansion;
- [ ] complete 100% rollout only after the staged evidence is acceptable.

## Portable local-user backup acceptance

The Reader portable backup is text-free by contract. Before production rollout verify on a device:

- [ ] export/import Reader settings and named/custom reading preferences;
- [ ] export/import global Clean rules;
- [ ] export/import bookmarks/highlights/notes;
- [ ] export/import favorites/tags;
- [ ] same source + same normalized revision restores staged progress;
- [ ] different normalized revision does **not** restore stale progress;
- [ ] reading sessions/pace restore without book text;
- [ ] Smart Clean KEEP/DELETE/PROTECT memory restores from fingerprints only;
- [ ] backup JSON declares `containsBookText=false` and contains no source/normalized/Clean book payload;
- [ ] SAF folder roots are re-selected rather than pretending URI grants are portable across installs/devices;
- [ ] imported font binaries are re-selected if unavailable on the destination device.

## Release declaration

A release may be called **Google Play production-qualified** only when all applicable P0 rows above have concrete evidence attached to the release system/PR and the exact production AAB is traceable to the fully-gated source tag.

Until then, the correct wording remains **source-complete / launch candidate / source provenance release**.
