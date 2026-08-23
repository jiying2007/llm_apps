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

GitHub source provenance is published by the `publish-source-release` job in `.github/workflows/ci.yml`. There is one release authority; no separate release workflow is retained.

A source release is requested through a same-repository pull request whose head branch is `release/source-vX.Y.Z`, base is `main`, and only changed file is the permanent manifest `releases/source/vX.Y.Z.md`. The manifest must declare:

```text
version: vX.Y.Z
kind: source-release
google_play_production: false
```

The release request PR goes through ordinary hosted CI like any other change. After it is squash-merged, the normal `push` CI for `main` reruns all standard gates. Only after `native-core`, `android`, `harmony-contract`, `play-store-contract`, and `terminal-contract` succeed may `publish-source-release` run.

`publish-source-release` has job-scoped `contents: write` and `pull-requests: read`; the rest of CI remains `contents: read`. It resolves the source-declared Android version, requires the matching permanent manifest, and publishes only when that version has no existing Git tag. If the tag already exists, later ordinary main pushes are idempotent no-ops for source publication.

On first successful publication the job:

1. creates Git tag `vX.Y.Z` at the exact fully-gated `main` commit;
2. creates the corresponding GitHub Source Release and cites `releases/source/vX.Y.Z.md`;
3. explicitly states that the Source Release is not a signed APK/AAB and is not Google Play production evidence;
4. prunes merged same-repository temporary development branches (`feat/`, `fix/`, `chore/`, `ci/`, `refactor/`, `docs/`, `test/`, `perf/`) that are not heads of open PRs;
5. prunes the merged `release/source-vX.Y.Z` branch.

This model deliberately uses the already-required main CI path rather than ref-only events, actor identity, cross-workflow event propagation, polling windows, or a second release workflow. The permanent manifest remains the auditable release request record in `main`.

The source-publication job has no Android signing key and cannot activate Play products or publish Play Console listings. A GitHub Source Release is provenance evidence, not production-store evidence.

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
