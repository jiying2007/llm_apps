# Testing Strategy

## Shared native core

Every change to `core/native` must pass a Release host build with `-Wall -Wextra -Wpedantic -Werror` and CTest.

Required automated coverage includes:

- known SHA-256 vectors and file hashing;
- encoding BOM/no-BOM detection, Big5/GB18030 and truncated sample boundaries;
- invalid/overlong/truncated/surrogate UTF-8 rejection;
- character/byte indexing and bounded reads;
- cross-buffer literal search;
- chapter extraction;
- speech segmentation;
- deterministic repair revisions and repair export;
- large-file stress, concurrent reads and handle lifecycle.

No test may rely on C/C++ `assert` for correctness because Release builds define `NDEBUG`; test checks must execute in Release configurations.

## Platform storage/session contract

Both platforms must test the same persistence invariants:

- normalized files are named `document-<normalizedSha256>.txt`;
- clean files are named `clean-<repairRevision>.txt`;
- no production reader uses mutable fixed `document.txt`/`clean.txt` paths or a `clean.revision` sidecar;
- re-importing identical source bytes reuses source identity;
- changing the decode so `normalizedSha256` changes creates a new immutable normalized path and resets source-view progress;
- a candidate reader session is opened before the previous session is closed;
- failed/interrupted candidate creation leaves the previous session/files usable;
- obsolete revisions are pruned only after the new session is published;
- clean-view offsets never overwrite normalized-source progress/bookmarks.

The hosted repository contract checks structural invariants; device tests exercise interruption/process-death behavior.

## Android

CI must compile all supported native ABIs and run Debug/Release lint plus Debug APK and Release AAB builds. Platform behavior that can be expressed without a device should be unit-tested. Release/device validation covers DocumentProvider import/export, repeated import, process death, lifecycle, TTS/audio focus and accessibility.

## HarmonyOS

Hosted CI verifies source/bridge/storage contracts. The `harmony-device.yml` workflow is the authoritative HAP build gate on a self-hosted runner with the official SDK. Device validation covers DocumentViewPicker, repeated import, TaskPool long operations, process/lifecycle recovery, Core Speech Kit and ArkUI accessibility.

## Cross-platform parity

Use the same golden source files on both platforms. For each file compare source SHA, normalized SHA, encoding selection, character count, fixed window reads, search offsets, chapter offsets, repair revision and clean-output SHA. A mismatch is a release blocker.
