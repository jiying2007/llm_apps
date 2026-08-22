# Release

Source control contains releasable source, not signed packages or signing material.

## Android v2.x

```bash
cd apps/android
./gradlew --no-daemon --no-configuration-cache --warning-mode all \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<monotonic-code> \
  -PjingduVersionName=<semver> \
  androidStoreCheck writeAndroidReleaseChecksums
```

The command validates final identity/version/signing, builds signed release APK/AAB, stages version-derived names and writes SHA-256 checksums under Gradle build output. Release infrastructure also archives the R8 mapping and signing-certificate fingerprint. Signing keys never enter Git or GitHub Release assets.

For the initial Android v2.0.0 publication, release automation may bootstrap the long-lived upload key exactly once. The private keystore and credentials are stored only as a short-retention private workflow artifact for the release owner to archive securely; future releases must reuse that retained key rather than generate another one.

## HarmonyOS

HarmonyOS remains source-complete but pre-release until its official toolchain/HAP and device gates are executed. Use `.github/workflows/harmony-device.yml` or `scripts/check-harmony.sh` on the official DevEco/HarmonyOS SDK toolchain when Harmony release qualification begins. Android release status must never be presented as Harmony production evidence.

## Android release acceptance

An Android production release requires:

- all hosted source gates in `QUALITY_GATES.md` green for the exact candidate tree;
- `androidStoreCheck` green with production identity/version/signing;
- signed release APK and AAB;
- signing verification for APK/AAB;
- R8 mapping, SHA-256 manifest and certificate fingerprint;
- immutable Git tag/GitHub Release provenance;
- the retained upload key archived outside the repository.

Harmony HAP/real-device evidence is intentionally deferred and does not block an Android-only v2.x release.

## Final hard-cut history publication

This repository originally committed experimental prototypes, extracted reference APKs and generated Android packages. Android v2.0.0 publication therefore establishes the current terminal source tree as a new root `main` commit rather than retaining those blobs in reachable branch/tag history.

The publication workflow must:

1. build and verify signed Android v2.0.0 artifacts from the exact candidate tree;
2. remove only its one-time publisher workflow and obsolete hard-cut helper from the index;
3. create a parentless root commit from that final tree;
4. verify the root tree with Native, Android and terminal source gates;
5. force-update `main` with `--force-with-lease` against the known old main;
6. create tag `v2.0.0` and the Android GitHub Release from that root commit;
7. delete the migration branch and ensure no ordinary branch/tag references the experimental lineage.

Earlier Git objects then become unreachable from normal repository refs. Physical deletion of unreachable GitHub server objects depends on GitHub garbage collection and is not controlled by repository code.

Version `2.x` is a hard-cut product line. Earlier experimental private metadata/ABI is not an upgrade-compatibility contract.
