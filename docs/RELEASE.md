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
- Harmony HAP build for the release commit;
- Android/Harmony device matrix evidence;
- golden cross-platform parity evidence;
- final store signing/privacy/listing checks;
- immutable version/tag provenance and rollback package metadata.

Version `2.x` is a hard-cut product line. Earlier experimental private metadata/ABI is not an upgrade-compatibility contract.
