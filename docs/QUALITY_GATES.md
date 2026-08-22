# Quality Gates

A change is mergeable only when every applicable gate below passes. `main` branch protection is intentionally not enabled at the moment; these gates are still the project definition of done.

## Source gates

1. **Native core** — Release CMake build, `-Wall -Wextra -Wpedantic -Werror`, CTest including encoding/SHA/revision/large-file/concurrency/malformed-input coverage, plus clang-tidy analyzer/bugprone/performance/portability checks.
2. **Android** — Debug/Release lint, Debug APK, Release AAB, JNI compilation for supported ABIs, single-worker long-work isolation, immutable `document-<sha>` / `clean-<revision>` artifacts and candidate-session-before-prune checks.
3. **Harmony source contract** — Stage/HAP files, Node-API bridge, TaskPool long-work/open path, immutable `document-<sha>` / `clean-<revision>` artifacts, candidate-session-before-prune, revision-safe persistence, DocumentViewPicker and Core Speech Kit wiring are present and reference the single shared core.
4. **Repository contract** — no legacy roots, Java shared core, compatibility/transition markers, mutable fixed clean/document persistence API, floating GitHub Actions tags, committed APK/AAB/HAP/keystore or extracted third-party executable assets.

## Android production release gate

Android may be released independently while HarmonyOS remains source-complete but pre-release. An Android production release requires:

- all source gates above green for the exact candidate tree;
- signed release APK and AAB using the retained Android upload key;
- R8 mapping, SHA-256 manifest and signing-certificate fingerprint archived with the release;
- production package id/version validated by `androidStoreCheck`;
- Android release notes and immutable tag provenance.

Harmony HAP or Harmony real-device evidence does **not** block an Android-only release or publication of the shared source tree to `main`. Such a release must not claim HarmonyOS production readiness.

## Harmony production gate

Before any HarmonyOS production release, changes touching `apps/harmony` or the shared ABI require the official HarmonyOS/DevEco Hvigor build workflow on a `self-hosted,harmonyos` runner. The workflow must produce a HAP artifact; a static source check does not substitute for it. Runner installation and verification are defined in `HARMONY_RUNNER.md`.

A workflow that remains queued because no matching runner is online is an unmet **Harmony release** gate, not a failure of the Android release gate.

## Device gates

Android device/store publication follows the Android portion of `DEVICE_MATRIX.md`. Harmony device evidence is deferred until Harmony release qualification. Cross-platform golden parity becomes a blocker before declaring the two-platform product jointly production-ready.

## Store gate

Final application identity/version/signing, privacy/listing declarations, package checksums/symbols and rollback artifacts are verified in release infrastructure for the platform being released.
