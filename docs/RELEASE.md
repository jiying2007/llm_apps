# Release

Source control contains releasable source, not signed packages or signing material.

## Android

```bash
cd apps/android
./gradlew --no-daemon --no-configuration-cache \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<monotonic-code> \
  -PjingduVersionName=<semver> \
  writeAndroidReleaseChecksums
```

The command validates final identity/version/signing, builds release APK/AAB, stages version-derived names and writes SHA-256 checksums under Gradle build output. Do not copy those artifacts into Git.

## HarmonyOS

Use `.github/workflows/harmony-device.yml` or `scripts/check-harmony.sh` on the official DevEco/HarmonyOS SDK 6.x toolchain. Release signing uses publisher-owned signing configuration outside Git. Archive the HAP/APP, native symbols and package checksums in release infrastructure.

## Acceptance

A production release requires:

- all source gates in `QUALITY_GATES.md`;
- Harmony HAP build for the exact candidate source tree;
- Android/Harmony device matrix evidence;
- golden cross-platform parity evidence;
- final store signing/privacy/listing checks;
- immutable version/tag provenance and rollback package metadata.

## Final hard-cut history publication

This repository originally committed experimental prototypes, extracted reference APKs and generated Android packages. Deleting those paths from the working tree does not remove their blobs from reachable Git history. The terminal publication therefore uses a deliberate history cut after all physical/toolchain gates above are green.

The release operator must:

1. record the verified candidate tree SHA and HAP/Android/device/store evidence;
2. create a clean temporary clone/fetch of the terminal branch;
3. create an orphan branch containing exactly the verified terminal tree and a single root commit;
4. run `./scripts/check-native.sh`, `./scripts/verify-terminal.sh` and `cd apps/android && ./gradlew --no-daemon --warning-mode all androidCheck` against that orphan commit;
5. confirm the orphan tree SHA is byte-for-byte identical to the verified candidate tree;
6. force-update `main` to the verified orphan root commit;
7. create the terminal version tag/release from that new `main` commit only;
8. delete the migration branch and any old tags/branches that make the experimental history reachable;
9. re-clone from GitHub and verify only the terminal root lineage is reachable from repository refs.

Do not perform this rewrite before the Harmony HAP and device/store gates are complete, because the rewrite is the final publication boundary rather than a development migration mechanism.

After the rewrite, earlier blobs are unreachable from repository refs. Physical deletion of unreachable GitHub server objects depends on GitHub garbage collection and is not controlled by repository code; no branch, tag, release or documented reference may intentionally keep those objects reachable.

Version `2.x` is a hard-cut product line. Earlier experimental private metadata/ABI is not an upgrade-compatibility contract.
