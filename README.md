# llm_apps / 净读 TXT

Production repository for the same offline TXT reader on Android and HarmonyOS.

## Terminal architecture

- `core/native/`: single C++17 business/algorithm core with stable C ABI.
- `apps/android/`: Android native app; Java platform/UI layer + JNI bridge only.
- `apps/harmony/`: HarmonyOS Stage/ArkUI app; ArkTS platform/UI layer + Node-API bridge only.
- legacy encodings are decoded by the platform import adapters into a private normalized UTF-8 copy; the selected source file is never modified.
- generated packages, signing keys, extracted competitor APKs, old prototypes, compatibility migrations and transition trees are not source assets and are rejected by CI.

## Local gates

```bash
./scripts/check-native.sh
cd apps/android && ./gradlew --no-daemon androidCheck
./scripts/verify-terminal.sh
```

HarmonyOS requires DevEco Studio / HarmonyOS SDK 6.0+ and builds from `apps/harmony` with the official Hvigor toolchain. The Harmony app imports files through DocumentViewPicker, uses the same shared native core through Node-API, and uses Core Speech Kit for system TTS.

## Release rule

`main` is releasable source only. APK/AAB/HAP, mapping files and signatures are build/release artifacts, never committed. A release is valid only when source CI passes and the corresponding Android/Harmony signed packages pass their device/store gates.
