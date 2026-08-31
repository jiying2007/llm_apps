# Release

Source control contains releasable source and store metadata, not signed packages or signing material.

## Android v2.x build

```bash
cd apps/android
./gradlew --no-daemon --no-configuration-cache --warning-mode all \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<monotonic-code> \
  -PjingduVersionName=<semver> \
  androidStoreCheck writeAndroidReleaseChecksums
```

Release infrastructure archives signed APK/AAB, R8 mapping, SHA-256 manifest and signing-certificate fingerprint. Future releases reuse the retained Android upload key created for v2.0.0; never generate a replacement key for routine releases.

The external production evidence gate is `PRODUCTION_READINESS.md`. Hosted source CI must never convert an unchecked Play/device/repository-administration row into claimed production evidence.

## Source release automation

GitHub source provenance has one authority: the `publish-source-release` tail job in `.github/workflows/ci.yml`. There is no separate Source Release workflow, ref-only trigger, `workflow_run` relay, PR-close release path, or polling protocol.

A source release is represented by a permanent manifest `releases/source/vX.Y.Z.md`. The source-declared Android version must match `X.Y.Z`, and the manifest must contain:

```text
version: vX.Y.Z
kind: source-release
google_play_production: false
```

A release manifest is reviewed and merged through the normal pull-request process. Every push to `main` executes the same six required hosted gates:

1. shared native Core build/tests/static analysis;
2. Android product build, lint, AndroidTest compilation, release bundle and benchmark assembly;
3. hosted Android Macrobenchmark/frame SLO and Baseline Profile qualification;
4. Harmony source/terminal contract;
5. Play metadata and lifetime-Pro contract;
6. terminal source/product/localization/long-form quality contract.

`publish-source-release` has `needs` on all six jobs and runs only for `push` to `main`. Ordinary PR jobs remain read-only. Only the publication tail job receives job-scoped `contents: write` plus `pull-requests: read`.

`scripts/publish-source-release.py` resolves the Android source version and matching permanent manifest. If no manifest exists for that version, source publication is skipped. If a manifest exists, the publisher uses three explicit states:

1. **tag + GitHub Release already exist** — publication is complete and becomes a permanent no-op on all later `main` pushes for that same version, even after `main` advances; the tag is never moved;
2. **tag exists but Release is missing** — this is treated as an interrupted first publication and may be completed only if the orphan tag already resolves to the current fully-gated `github.sha`;
3. **neither tag nor Release exists** — create an annotated tag object that binds the exact fully-gated `github.sha` to the checked-in source-manifest SHA-256, create its GitHub Release, then verify the tag resolves to that SHA.

A Release without its tag is an inconsistent state and fails hard. Existing published tags are historical provenance: later development on the same version cannot retarget them and cannot cause ordinary CI to fail merely because `main` advanced. The publisher itself never moves/deletes a release tag. GitHub repository tag rules remain a separate administration control required by `PRODUCTION_READINESS.md` before production rollout.

After publication/no-op resolution, the publisher removes closed same-repository temporary PR branches under `feat/`, `fix/`, `chore/`, `ci/`, `refactor/`, `docs/`, `test/`, `perf/`, plus `release/source-v*`, while preserving every currently open PR head. This cleanup intentionally removes both merged and abandoned closed temporary PR branches. Long-lived branch names outside the explicit temporary prefixes are never pruned automatically.

The publisher has no signing key and cannot activate Play products, upload production listings or perform a Google Play rollout. A GitHub Source Release is **source provenance only**. It is not evidence of a signed APK/AAB, Google Play production, or HarmonyOS device qualification.

## Android 2.3.x commercial / Reader release

Android 2.3.x carries the Reader product line and lifetime Pro model while keeping Free as a complete reader.

### Play Console prerequisites

Before any production rollout, follow `PLAY_CONSOLE_SETUP.md` and `PRODUCTION_READINESS.md` and verify:
- INAPP one-time product id exactly `jingdu_pro_lifetime`;
- product is active and localized price configured;
- license testers cover successful purchase, cancel, pending, acknowledgement, reinstall restore and offline launch after verified ownership;
- no subscription product is required while there is no recurring server service;
- default listing metadata comes from `fastlane/metadata/android`;
- Custom Listing/search keyword specs and screenshot brief under `store/play/` are applied where supported;
- Data safety/privacy declarations match actual app behavior;
- platform-enforced `main` / `v*` repository protection evidence is captured before production rollout.

Source CI cannot create/activate Play Console products, publish listings, qualify physical devices or change GitHub administration settings in the current environment; repository assets/runbooks make those external actions deterministic and auditable.

### Store artifact acceptance

An Android 2.3.x production release requires:
- exact candidate Hosted gates green;
- `androidStoreCheck` green with production identity/version/signing;
- signed APK/AAB verified with retained upload key;
- mapping/checksum/certificate evidence archived;
- physical-device matrix and release performance SLO evidence;
- Play license-test purchase/restore/acknowledge evidence;
- store listing screenshots/text uploaded and reviewed;
- internal/closed Play-installed candidate testing;
- staged rollout rather than immediate 100% production where practical;
- immutable source tag/GitHub Release provenance plus repository protection evidence.

## Portable local-user backup

Reader schema 4 backs up portable, text-free user assets:

- Reader settings;
- global Clean rules;
- bookmarks/highlights/notes;
- favorites/tags;
- progress staged against `sourceSha256 + normalizedSha256` and consumed only for the exact normalized revision;
- local reading sessions and pace;
- Smart Clean KEEP/DELETE/PROTECT memory as one-way candidate fingerprints and decisions.

The backup declares `containsBookText=false` and does not contain source, normalized or Clean book payloads. Schema 3 settings/rules/annotation backups remain importable for pre-production testers. SAF folder URI grants and imported font binaries are not falsely treated as portable credentials/assets; destination devices must re-select them when unavailable.

## Growth release checklist

Before rollout:
1. run `./scripts/verify-play-store.sh`;
2. verify default zh-CN title is `净读 - TXT 小说阅读器`;
3. confirm screenshot claims reflect device-tested behavior;
4. verify Free Smart Clean scan shows full candidate text before paywall;
5. verify Pro CTA displays Play `formattedPrice`;
6. exercise global-rule import/export and schema-4 local-user backup/restore;
7. verify no backup/export contains book正文;
8. verify staged progress restores only for the exact normalized revision;
9. verify Smart Clean feedback backup contains fingerprints/decisions only;
10. verify In-App Review is not shown on first launch;
11. capture Play experiment plan (icon/first screenshot/short description/price one variable at a time).

## HarmonyOS

HarmonyOS remains source-complete/pre-release until official HAP/device gates execute. Android 2.3.x commerce/store readiness is not Harmony production evidence.

## Historical hard cut

Android v2.0.0 established the current terminal source line as a new root and removed ordinary refs to the experimental lineage. Reader / Android 2.3.x is a normal forward release on that hard-cut line; do not rewrite history again merely for a product update.

Version 2.x does not promise compatibility with pre-2.x experimental private metadata/ABI.
