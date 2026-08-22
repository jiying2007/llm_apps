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

## Android product UI

Android is Compose-first and has two top-level states: Library and Reader. CI compiles a Compose instrumentation-test source set in addition to Debug/Release lint/build.

Minimum UI smoke coverage:

- empty Library exposes product positioning and the primary import action;
- Reader exposes back, search, chapters, progress and TTS semantics;
- icon-only controls have content descriptions;
- the old programmatic Views `MainActivity.java` is absent;
- adaptive grid / bounded wide-reader measure / bottom sheets are part of the architecture contract.

Device/UI validation additionally covers:

- edge-to-edge system bars and predictive back;
- 200% font scale and large accessibility text;
- TalkBack reading order and minimum touch targets;
- phone portrait/landscape, split screen, tablet and foldable-size windows;
- theme/type/line-height/margin changes without losing position;
- progress slider and sequential previous-page behavior;
- ACTION_VIEW and ACTION_SEND import;
- re-decode without reopening the external source picker;
- revision-safe bookmarks and Clean offset isolation;
- TTS/audio focus, auto page and sleep timer.

UI tests should prefer semantics/user-observable behavior over implementation-node structure so Compose refactors do not create brittle tests.

## Android performance

10/100/300 MiB device runs verify that import, open/index, search, chapter scan, Clean generation, re-decode and export never intentionally execute on the main thread. Library rendering must not open/index every book. Reader page navigation must remain bounded regardless of full document size.

## HarmonyOS

Hosted CI verifies source/bridge/storage contracts. The `harmony-device.yml` workflow is the authoritative HAP build gate on a self-hosted runner with the official SDK. Device validation covers DocumentViewPicker, repeated import, TaskPool long operations, process/lifecycle recovery, Core Speech Kit and ArkUI accessibility.

## Cross-platform parity

Use the same golden source files on both platforms. For each file compare source SHA, normalized SHA, encoding selection, character count, fixed window reads, search offsets, chapter offsets, repair revision and clean-output SHA. A mismatch is a release blocker before declaring the two-platform product jointly production-ready.
