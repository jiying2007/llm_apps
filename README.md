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
- persisted progress/bookmarks belong only to the normalized source offset domain; clean-view offsets are not written back without a shared-core projection;
- long file operations and document opening/index construction run off the UI thread;
- no network permission/runtime third-party SDK is part of the current product path;
- no compatibility core, old ABI bridge, prototype production root or committed release package is allowed.

## Local/source gates

```bash
./scripts/check-native.sh
cd apps/android && ./gradlew --no-daemon androidCheck
cd ../..
./scripts/verify-terminal.sh
```

Hosted CI also enforces Android/Harmony architecture contracts. These source gates do not substitute for a real HarmonyOS HAP build.

HarmonyOS real HAP build uses Huawei's official Command Line Tools/HarmonyOS SDK on a `self-hosted,harmonyos` runner through `.github/workflows/harmony-device.yml`. See `docs/HARMONY_RUNNER.md` for the single supported runner/toolchain contract. A queued Harmony workflow is an unmet gate, not a successful build.

## Documentation

Start with `docs/ARCHITECTURE.md`. `CORE_CONTRACT.md` and `DATA_MODEL.md` define cross-platform semantics; `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md`, `DEVICE_MATRIX.md`, `QUALITY_GATES.md`, `HARMONY_RUNNER.md` and `RELEASE.md` define operational gates.

`main` is intended to remain releasable source. APK/AAB/HAP, mapping/symbol packages and signing material belong to build/release infrastructure rather than Git.
