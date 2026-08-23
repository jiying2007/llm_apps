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

## Source release automation

GitHub source provenance has one authority: the `publish-source-release` tail job in `.github/workflows/ci.yml`. There is no separate Source Release workflow, ref-only trigger, `workflow_run` relay, PR-close release path, or polling protocol.

A source release is represented by a permanent manifest `releases/source/vX.Y.Z.md`. The source-declared Android version must match `X.Y.Z`, and the manifest must contain:

```text
version: vX.Y.Z
kind: source-release
google_play_production: false
```

A release manifest is reviewed and merged through the normal pull-request process. Every push to `main` executes the same five required hosted gates:

1. shared native Core build/tests/static analysis;
2. Android product build, lint, AndroidTest compilation, release bundle and benchmark assembly;
3. Harmony source/terminal contract;
4. Play metadata and lifetime-Pro contract;
5. terminal source/product/localization/long-form quality contract.

`publish-source-release` has `needs` on all five jobs and runs only for `push` to `main`. Ordinary PR jobs remain read-only. Only the publication tail job receives job-scoped `contents: write` plus `pull-requests: read`.

`scripts/publish-source-release.py` then resolves the Android source version and matching permanent manifest. If no manifest exists for that version, source publication is skipped. If a manifest exists, the publisher uses three explicit states:

1. **tag + GitHub Release already exist** — publication is complete and becomes a permanent no-op on all later `main` pushes for that same version, even after `main` advances; the tag is never moved;
2. **tag exists but Release is missing** — this is treated as an interrupted first publication and may be completed only if the orphan tag already resolves to the current fully-gated `github.sha`;
3. **neither tag nor Release exists** — create both at the exact fully-gated `github.sha`, then verify the resulting tag resolves to that SHA.

A Release without its tag is an inconsistent state and fails hard. Existing published tags are historical provenance: later development on the same version cannot retarget them and cannot cause ordinary CI to fail merely because `main` advanced.

After publication/no-op resolution, the publisher removes closed same-repository temporary PR branches under `feat/`, `fix/`, `chore/`, `ci/`, `refactor/`, `docs/`, `test/`, `perf/`, plus `release/source-v*`, while preserving every currently open PR head. This cleanup intentionally removes both merged and abandoned closed temporary PR branches. Long-lived branch names outside the explicit temporary prefixes are never pruned automatically.

The publisher has no signing key and cannot activate Play products, upload production listings or perform a Google Play rollout. A GitHub Source Release is **source provenance only**. It is not evidence of a signed APK/AAB, Google Play production, or HarmonyOS device qualification.

## Android v2.2 commercial release

v2.2 adds a lifetime Pro product but keeps Free as a complete reader.

### Play Console prerequisites

Before any production rollout, follow `PLAY_CONSOLE_SETUP.md` and verify:
- INAPP one-time product id exactly `jingdu_pro_lifetime`;
- product is active and localized price configured;
- license testers cover successful purchase, cancel, pending, acknowledgement, reinstall restore and offline launch after verified ownership;
- no subscription product is required for v2.2;
- default listing metadata comes from `fastlane/metadata/android`;
- Custom Listing/search keyword specs and screenshot brief under `store/play/` are applied where supported;
- Data safety/privacy declarations match actual app behavior.

Source CI cannot create/activate Play Console products or publish listings in the current environment; repository assets/runbooks make those external actions deterministic.

### Store artifact acceptance

An Android v2.2 production release requires:
- exact candidate Hosted gates green;
- `androidStoreCheck` green with production identity/version/signing;
- signed APK/AAB verified with retained upload key;
- mapping/checksum/certificate evidence archived;
- Play license-test purchase/restore/acknowledge evidence;
- store listing screenshots/text uploaded and reviewed;
- staged rollout rather than immediate 100% production where practical;
- immutable Git tag/GitHub Release provenance.

## Growth release checklist

Before rollout:
1. run `./scripts/verify-play-store.sh`;
2. verify default zh-CN title is `净读 - TXT 小说阅读器`;
3. confirm screenshot claims reflect device-tested behavior;
4. verify Free Smart Clean scan shows full candidate text before paywall;
5. verify Pro CTA displays Play `formattedPrice`;
6. exercise global-rule import/export and settings/rules backup;
7. verify no backup/export contains book正文;
8. verify In-App Review is not shown on first launch;
9. capture Play experiment plan (icon/first screenshot/short description/price one variable at a time).

## HarmonyOS

HarmonyOS remains source-complete/pre-release until official HAP/device gates execute. Android v2.2 commerce/store readiness is not Harmony production evidence.

## Historical hard cut

Android v2.0.0 already established the current terminal source line as a new root and removed ordinary refs to the experimental lineage. v2.2 is a normal forward release on that hard-cut line; do not rewrite history again merely for a product update.

Version 2.x does not promise compatibility with pre-2.x experimental private metadata/ABI.
