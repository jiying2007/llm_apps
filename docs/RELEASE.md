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

GitHub source provenance is automated by `.github/workflows/source-release.yml`. It is deliberately separate from Google Play production rollout.

To request an immutable source release, create or fast-forward a temporary branch named `release/source-vX.Y.Z` to the current `main` HEAD. The release workflow is self-contained and does not depend on another workflow's timing, actor identity, `workflow_run` delivery, or a polling timeout.

The workflow first refuses any request branch that does not point exactly at current `main`, refuses a requested version that disagrees with both Android version defaults, and refuses to move an existing tag to another commit. It then independently runs the release-candidate gates on that exact immutable main SHA:

1. shared native Core build/tests/static analysis via `scripts/check-native.sh`;
2. Android `androidCheck` plus the Android i18n contract;
3. terminal/source/product/commercial contract and Play metadata/lifetime-Pro contract.

Only after all release-candidate jobs pass does the publish job run. This intentionally duplicates the critical release verification at release time rather than coupling source publication to the duration or event propagation of ordinary CI.

On success the workflow:

1. creates or verifies Git tag `vX.Y.Z` at the exact verified `main` commit;
2. creates an idempotent GitHub Source Release with explicit notice that no signed APK/AAB or Play rollout evidence is implied;
3. prunes only temporary development branches (`feat/`, `fix/`, `chore/`, `ci/`, `refactor/`, `docs/`, `test/`, `perf/`) that belong to merged same-repository PRs and are not used by an open PR;
4. removes the temporary `release/source-vX.Y.Z` request branch.

Long-lived branch names outside those explicit temporary prefixes are never pruned automatically merely because they once appeared as a merged PR head. A failed source-release request remains as a branch and can be safely replayed after a normal workflow-fix PR by fast-forwarding it to the new exact `main`.

The workflow has no signing key and cannot activate Play products or publish Play Console listings. A GitHub Source Release is provenance evidence, not production-store evidence.

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
