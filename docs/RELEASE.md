# Release

Source control contains no signed packages or signing material.

Android release:
```bash
cd apps/android
./gradlew --no-daemon --no-configuration-cache \
  -PjingduApplicationId=com.junchen.jingdu \
  -PjingduVersionCode=<monotonic-code> \
  -PjingduVersionName=<semver> \
  writeAndroidReleaseChecksums
```

HarmonyOS release is built from `apps/harmony` with the installed DevEco/HarmonyOS SDK and the publisher's signing profile. Use the official Hvigor command-line tool to assemble the release HAP, then archive package checksum/symbols in the release system rather than the Git tree.

Release acceptance requires the device matrix in `QUALITY_GATES.md`, the applicable app-store console checks, and immutable version/tag provenance. Package identity is intentionally not backward-compatible with any earlier experimental package/data tree beyond the final `com.junchen.jingdu` identity.
