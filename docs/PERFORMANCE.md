# Performance Contract

Large local TXT files are first-class inputs. Correctness is required at all supported sizes; the targets below are release gates rather than guarantees for every device.

## Reference sizes

Use deterministic 10 MiB, 100 MiB and 300 MiB UTF-8 fixtures, plus representative GB18030/Big5 imports.

Measure on release builds:

- source copy + normalization time;
- time from user action to visible busy feedback;
- first open/index time and repeat-open time for the same immutable revision;
- time to first readable page after import/open;
- peak process memory during import/open/search/Clean;
- random bounded window read latency;
- full-text search latency;
- chapter discovery latency;
- clean-view generation latency;
- TTS next-chunk latency;
- frame/jank behavior while progress/reading settings sheets are open.

## Responsiveness rule

Every user action that can trigger work proportional to file size gives immediate UI feedback and performs the work outside the Android main thread / Harmony ArkUI thread. This includes import/normalization, native open/index construction, search, chapter scan, Clean generation, re-decode and export copy.

Android serializes session-affecting work on one worker executor. Compose state is published on the main thread only after a generation-token check. HarmonyOS uses TaskPool `@Concurrent` tasks.

## Persistent sparse-index rule

The shared Core owns a disposable sparse-index sidecar named `<immutable-document-path>.jdx`. It is a performance cache, never product identity or user data.

- First open validates UTF-8 and builds the sparse character/byte index, then best-effort publishes the sidecar.
- Repeat open of the same immutable revision validates document size/mtime/stride and loads the sidecar instead of rescanning the whole file.
- A missing, stale, truncated or malformed sidecar must transparently fall back to source scanning and repair the cache.
- Cache publication is temporary-file + rename and is serialized in-process.
- If the document directory is read-only, failure to persist the cache must not make the document unreadable.
- Platform revision pruning removes orphan `.jdx` / `.jdx.tmp` files after the associated immutable document is no longer retained.

Because normalized/Clean documents are content-addressed immutable revisions, cache validation never relies on supporting in-place mutation beneath an active reader.

## Reader page rule

The managed UI never receives the whole document. Android asks the Core for bounded read-ahead and Compose measures the laid-out visible range; sequential navigation advances by the visible code-point span. Typography changes can therefore change page boundaries without changing document offsets or loading the entire book.

A tablet/foldable window caps paragraph line length. Extra width is not used to render arbitrarily long text lines because that degrades reading speed and visual tracking.

## Memory rule

The product must not concatenate the full book into one managed-language string. Core indexing/search and platform normalization are streaming/bounded. Any future implementation that scales memory linearly with whole-file text requires explicit review.

## UI performance targets

For the Android release device matrix:

- busy feedback appears in the next rendered UI state after starting a long operation;
- page next/previous, sheet open/close and theme/typography controls remain interactive without waiting for whole-file work;
- no StrictMode/main-thread disk operation is intentionally added to import/search/chapter/Clean/export flows;
- library renders metadata only and does not open/index every book just to show the grid;
- progress persistence is bounded metadata work and must not trigger whole-file reads;
- opening Search/Chapters within an active session must not rebuild the native document index;
- reopening an unchanged 100/300 MiB revision should use the validated sparse-index sidecar.

## Current automated host stress gate

CI generates a 32 MiB UTF-8 fixture and validates cross-buffer search, random reads, eight concurrent readers, malformed UTF-8 rejection and repeated handle lifecycle. Core tests also validate sparse-index cache creation, corrupt-cache fallback and repair. Android CI compiles the Compose UI and instrumentation tests in addition to Debug/Release lint/build.

Device 10/100/300 MiB first-open/reopen timing, jank and memory results remain release/device evidence and are recorded in `DEVICE_MATRIX.md`.
