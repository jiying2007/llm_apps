# Performance SLO — Long-form TXT

Jingdu treats large local TXT as a first-class product workload. These SLOs separate deterministic hosted-CI regression guardrails from release-device qualification so emulator numbers are never presented as phone performance.

## Qualification corpus

Every release is qualified with deterministic or representative files at four tiers:

| Size | Purpose |
| --- | --- |
| 1–5 MiB | ordinary novel / cold-start path |
| 10 MiB | hosted Android Reader V3 interaction regression journey |
| 100 MiB | very-long-book Android soak journey |
| 960 MiB | near-1GiB native bounded-memory/RSS qualification |

The release-device corpus additionally includes UTF-8, GB18030/GBK and Big5 source imports where device evidence is required. Source files are never rewritten in place.

## Android release-device SLOs

Measure a minified release build on the device classes listed in `DEVICE_MATRIX.md`. Report median and P95 where the tool supports repeated measurements.

| Metric | Target |
| --- | ---: |
| cold app startup, time to initial display | P95 < 1.0 s |
| unchanged imported book, time to first readable page | P95 < 500 ms |
| new 20 MiB TXT, time to first readable page | < 1.0 s |
| new 100 MiB TXT, time to first readable page | < 2.0 s |
| chapter jump with active index | P95 < 100 ms |
| indexed exact search, ordinary query | P95 < 100 ms |
| page next/previous interaction | no visible main-thread stall |
| Smart Clean scan, 20 MiB | < 1.0 s target |
| Smart Clean scan, 100 MiB | < 3.0 s target |
| TTS next-chunk scheduling | P95 < 150 ms |
| 200 MiB open/search/Clean qualification | no OOM / ANR |

Targets are qualification goals for the release matrix, not guarantees across every Android device. A regression must be investigated and recorded rather than hidden by widening the target without evidence.

## Host Core regression gates

`jingdu_core_performance_gate_test` is the fast deterministic hosted-CI guardrail. It defaults to a 64 MiB UTF-8 fixture and checks:

- first open/index completes within a generous hosted-runner ceiling;
- validated `.jdx` reopen is bounded;
- end-of-file full-text search remains bounded;
- 1000 random bounded reads remain responsive;
- peak RSS remains below a coarse anti-regression ceiling;
- no whole-document managed-language materialization is introduced.

`jingdu_core_near_1gib_rss_gate_test` runs the same bounded-access contract with `JINGDU_PERF_FIXTURE_MIB=960`. Its peak RSS ceiling remains below 640 MiB, independent of the 960 MiB source size. This is specifically intended to catch accidental whole-document materialization.

Hosted wall-clock ceilings are intentionally loose. Real experience is measured on Android release devices.

## Hosted Android regression gate

Pull requests run the Reader V3 Macrobenchmark suite on a hosted Android Emulator after the normal Android build/test/lint/R8/AAB gate passes. The emulator is used only as a stable regression environment; it is not release-device performance evidence.

The required journeys are:

1. open a 10 MiB TXT;
2. open a 100 MiB TXT soak fixture;
3. page-turn repeatedly on the 10 MiB fixture;
4. switch to continuous mode and scroll;
5. exercise chapters and reading settings;
6. execute the Reader V3 Baseline Profile critical-user journey.

The Macrobenchmark result and Perfetto traces are retained as CI artifacts. `scripts/check-android-performance-slo.py` fails the build if `frameDurationCpuMs` percentile evidence is missing or if the hosted regression ceiling is exceeded:

| Hosted regression metric | Default ceiling |
| --- | ---: |
| `frameDurationCpuMs` P95 | 40 ms |
| `frameDurationCpuMs` P99 | 80 ms |

The limits may be tightened through `JINGDU_FRAME_P95_MS` and `JINGDU_FRAME_P99_MS`. They must not be widened simply to make a regression green; investigate the trace or explicitly revise this contract with evidence.

## Baseline Profile contract

`BaselineProfileGenerator.readerV3CriticalJourneys` covers the Reader V3 10 MiB open path, repeated page turns, reading settings, continuous scrolling and chapter navigation. Hosted CI executes profile generation separately from Macrobenchmark measurement and requires profile output to be pulled back to the runner. The generated profile is evidence that the critical-user journey remains executable; release-device qualification remains the authority for user-facing startup and frame SLOs.

## Architectural performance invariants

1. The managed UI never owns the whole document as a single `String`.
2. Reader rendering uses bounded windows from the shared Core.
3. Immutable normalized/Clean revisions are content addressed; unchanged content reuses index/cache work.
4. Import, normalization, search, chapters, Clean, re-decode and export never perform file-size-proportional work on the UI thread.
5. First readable page and background indexing are separate product milestones; full-book secondary work must not unnecessarily block reading.
6. Library rendering uses metadata only and never opens every book to draw the grid.
7. A performance cache can always be discarded/rebuilt without changing product identity or user data.
