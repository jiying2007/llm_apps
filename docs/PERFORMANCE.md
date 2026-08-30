# Performance Contract

Large local TXT files are first-class inputs. Correctness is required at all supported sizes. Hosted emulator measurements are deterministic regression evidence; physical-device measurements are the product Release qualification authority.

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
- frame/jank behavior during page turn, continuous scroll and hot-panel interactions.

## Responsiveness rule

Every user action that can trigger work proportional to file size gives immediate UI feedback and performs the work outside the Android main thread / Harmony ArkUI thread. This includes import/normalization, native open/index construction, search, chapter scan, Clean generation, re-decode and export copy.

Android serializes session-affecting work on one worker executor and uses dedicated bounded workers for progress/TOC work. Compose state is published on the main thread only after the relevant generation/state checks. HarmonyOS uses TaskPool `@Concurrent` tasks.

## Persistent sparse-index rule

The shared Core owns a disposable sparse-index sidecar named `<immutable-document-path>.jdx`. It is a performance cache, never product identity or user data.

- First open validates UTF-8 and builds the sparse character/byte index, then best-effort publishes the sidecar.
- Repeat open of the same immutable revision validates document size/mtime/stride and loads the sidecar instead of rescanning the whole file.
- A missing, stale, truncated or malformed sidecar transparently falls back to source scanning and repairs the cache.
- Cache publication is temporary-file + rename and serialized in-process.
- If the document directory is read-only, failure to persist the cache must not make the document unreadable.
- Platform revision pruning removes orphan `.jdx` / `.jdx.tmp` files after the associated immutable document is no longer retained.

Because normalized/Clean documents are content-addressed immutable revisions, cache validation never relies on supporting in-place mutation beneath an active reader.

## Reader and memory rule

The managed UI never receives the whole document. Android asks the Core for bounded read-ahead; pagination/continuous rendering operate on bounded windows and source/Core offsets remain authoritative. Typography changes can change page boundaries without changing document identity or materializing the whole book.

The product must not concatenate the full book into one managed-language string. Core indexing/search and platform normalization are streaming/bounded. Any future implementation that scales memory linearly with whole-file text requires explicit review.

A tablet/foldable window caps paragraph line length. Extra width is not used to render arbitrarily long text lines because that degrades reading speed and visual tracking.

## UI performance targets

For the Android release device matrix:

- busy feedback appears in the next rendered UI state after starting a long operation;
- page next/previous, hot-panel open/close and theme/typography controls remain interactive without waiting for whole-file work;
- no StrictMode/main-thread disk operation is intentionally added to import/search/chapter/Clean/export flows;
- library renders metadata only and does not open/index every book just to show the grid;
- progress persistence is bounded metadata work and must not trigger whole-file reads;
- opening Search/Chapters within an active session must not rebuild the native document index;
- reopening an unchanged 100/300 MiB revision should use the validated sparse-index sidecar.

## Hosted Android regression gate

Pull requests run Android 35 on a pinned Ubuntu 22.04 GitHub-hosted runner with a Pixel 6 AVD. The emulator graphics mode remains `JINGDU_EMULATOR_GPU_MODE:-auto`; CI does not select a faster backend merely to pass. This environment is regression evidence only and is never reported as physical-device performance.

The frame-producing journeys are fixed and must satisfy their sample floors:

| Journey | Real input | Minimum `frameDurationCpuMs` samples |
| --- | --- | ---: |
| `pageTurn10MiB` | six right-side Reader tap-zone page turns | 20 |
| `continuousScroll10MiB` | six real UiDevice swipes | 500 |
| `chaptersAndSettings10MiB` | two Chapters → Back → Aa → Back cycles | 50 |

The Hosted gate uses `scripts/reader-v3-hosted-emulator-baseline.json`, frozen from the first exact-head run where all three journeys completed with authoritative sample floors: PR #25 head `fa22d088df7456330244ac4dc2c00a82da888656`, workflow run `33294378785`, job `99212107479`, artifact `9727262417`.

For each journey and percentile, the effective Hosted limit is:

`min(checked-in baseline × 1.15, absolute hosted ceiling)`

The absolute hosted ceilings are **P95 <= 160 ms** and **P99 <= 220 ms**. They are anti-drift ceilings, not product SLOs. With the frozen baseline, the 15% relative limit is currently tighter than the absolute ceiling for every journey. Missing/truncated frame evidence fails the gate before percentile comparison.

Hosted page-turn uses the real Reader tap zone because the Android 35 hosted emulator can consume injected hardware volume keys before the foreground Activity. This does not remove or weaken product volume-key paging; hardware-volume delivery is qualified separately on a physical device.

On failure CI preserves benchmark JSON and device-side Perfetto traces. The artifact is the diagnostic authority for deciding whether the cost is application composition/layout, text layout, native work, rendering or hosted infrastructure.

## Physical Android Release frame gate

Physical-device Release qualification is separate and deliberately stricter. `.github/workflows/android-physical-release-performance.yml` runs only by manual dispatch on a self-hosted runner labeled `[self-hosted, android, physical]`. `scripts/run-android-physical-release-performance.sh` refuses QEMU/generic emulator devices before measurement.

The physical run installs the same release-derived minified Benchmark target and runs the real Macrobenchmark journeys, but page-turn is forced to `jingdu.pageTurnInput=physical-volume`. Six `KEYCODE_VOLUME_DOWN` inputs must each advance the authoritative Reader source position.

The product frame SLO remains unchanged:

- **P95 <= 40 ms**
- **P99 <= 80 ms**
- sample floors remain **20 / 500 / 50** for page-turn / continuous-scroll / chapters-settings.

No Hosted baseline or Hosted absolute ceiling is accepted by this gate. A release/tag/store submission is not performance-qualified until required physical-device evidence is green and retained.

## Baseline and Startup Profile contract

Reader V3 profiles are product assets generated from real critical-user journeys, not handwritten broad keep rules.

- The Startup Profile contains only app launch and opening the first readable Reader page.
- Page turn, Quick Settings and Chapters are profiled in the paged Reader session; continuous scrolling is then profiled independently.
- Hosted page-turn profile collection uses the same real Reader tap zone as the Hosted frame gate and does not depend on emulator hardware-volume delivery.
- Macrobenchmark measurement runs first with `CompilationMode.Partial(BaselineProfileMode.Require, warmupIterations = 0)`. Profile collection runs only after the Hosted result has been captured, so newly generated rules cannot feed the same measurement.
- A red Hosted regression still preserves performance evidence; generated profile evidence remains a separate freshness contract.
- Physical Release qualification is independent of profile generation and remains the authority for the 40/80 product frame SLO.

## Current automated host stress gate

Native CI generates bounded fixtures and validates cross-buffer search, random reads, concurrent readers, malformed UTF-8 rejection and repeated handle lifecycle. Core tests also validate sparse-index cache creation, corrupt-cache fallback and repair, JDX2 chapter-cache authority, and the near-1GiB RSS gate (`JINGDU_PERF_FIXTURE_MIB=960`, RSS below 640 MiB).

Android CI compiles unit tests, AndroidTest, lint, R8/release artifacts, Macrobenchmark tests, hosted regression contracts and Reader V3 profile contracts. Device 10/100/300 MiB first-open/reopen timing, jank and memory results remain Release/device evidence and are recorded in `DEVICE_MATRIX.md`.
