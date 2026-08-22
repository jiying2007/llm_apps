# Quality Gates

A change is mergeable only when every applicable gate below passes. `main` branch protection is intentionally not enabled at the moment; these gates are still the project definition of done.

## Source gates

1. **Native core** — Release CMake build, `-Wall -Wextra -Wpedantic -Werror`, CTest including encoding/SHA/revision/large-file/concurrency/malformed-input coverage, plus clang-tidy analyzer/bugprone/performance/portability checks.
2. **Android** — Debug/Release lint, Debug APK, Release AAB, JNI compilation for supported ABIs, single-worker long-work isolation, immutable `document-<sha>` / `clean-<revision>` artifacts and candidate-session-before-prune checks.
3. **Harmony source contract** — Stage/HAP files, Node-API bridge, TaskPool long-work/open path, immutable `document-<sha>` / `clean-<revision>` artifacts, candidate-session-before-prune, revision-safe persistence, DocumentViewPicker and Core Speech Kit wiring are present and reference the single shared core.
4. **Repository contract** — no legacy roots, Java shared core, compatibility/transition markers, mutable fixed clean/document persistence API, floating GitHub Actions tags, committed APK/AAB/HAP/keystore or extracted third-party executable assets.

## Toolchain gate

Changes touching `apps/harmony` or the shared ABI require the official HarmonyOS/DevEco Hvigor build workflow on a `self-hosted,harmonyos` runner. The workflow must produce a HAP artifact; a static source check does not substitute for it. Runner installation and verification are defined in `HARMONY_RUNNER.md`.

A workflow that remains queued because no matching runner is online is an unmet toolchain gate, not success.

## Device gate

Before store release, both platforms execute the matrix in `DEVICE_MATRIX.md`: import/encoding, repeated import, changed decode revision, interrupted candidate publication, reopen/progress, paging, search, chapters, bookmarks, repair/export, auto reading, TTS, lifecycle, accessibility, low-storage/write failures and 10/100/300 MiB performance.

## Cross-platform gate

Golden files must match on source SHA, normalized SHA, AUTO encoding, character counts, representative windows, search/chapter offsets, repair revision and clean-output SHA.

## Store gate

Final application identity/version/signing, privacy/listing declarations, package checksums/symbols and rollback artifacts are verified in release infrastructure.

A green hosted CI does not replace Harmony HAP/device/store evidence; device evidence does not permit bypassing source gates.
