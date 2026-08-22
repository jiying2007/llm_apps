# llm_apps / 净读 TXT

Production source for one offline TXT reader implemented as two native applications over one C++17 document core.

## Layout

- `core/native/` — the only cross-platform document/algorithm implementation; stable C ABI v2.
- `apps/android/` — Android UI/platform shell and JNI bridge.
- `apps/harmony/` — HarmonyOS Stage/ArkUI shell and Node-API bridge.
- `docs/` — normative architecture, ABI, data, encoding, performance, test, device and release contracts.
- `scripts/` — local/CI verification entry points.

## Product invariants

- selected external TXT files are never modified;
- import creates an app-private source copy;
- `book id == SHA256(source bytes)` on both platforms;
- source bytes are decoded to app-private normalized UTF-8;
- all post-normalization search/chapter/read/repair/speech semantics use the same native core;
- long file operations run off the UI thread;
- no network permission/runtime third-party SDK is part of the current product path;
- no compatibility core, old ABI bridge, prototype production root or committed release package is allowed.

## Local gates

```bash
./scripts/check-native.sh
cd apps/android && ./gradlew --no-daemon androidCheck
cd ../..
./scripts/verify-terminal.sh
```

HarmonyOS real HAP build requires the official DevEco/HarmonyOS SDK 6.x environment and is automated in `.github/workflows/harmony-device.yml` for a `self-hosted,harmonyos` runner.

## Documentation

Start with `docs/ARCHITECTURE.md`. `CORE_CONTRACT.md` and `DATA_MODEL.md` define cross-platform semantics; `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md`, `DEVICE_MATRIX.md`, `QUALITY_GATES.md` and `RELEASE.md` define operational gates.

`main` is intended to remain releasable source. APK/AAB/HAP, mapping/symbol packages and signing material belong to build/release infrastructure rather than Git.
