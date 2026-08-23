# Performance SLO — Long-form TXT

Jingdu treats large local TXT as a first-class product workload. These SLOs turn the existing performance contract into measurable release criteria without pretending hosted CI is a phone.

## Qualification corpus

Every release is qualified with deterministic or representative files at four sizes:

| Size | Purpose |
| --- | --- |
| 1–5 MiB | ordinary novel / cold-start path |
| 20 MiB | long novel |
| 100 MiB | very long novel |
| 200 MiB | stress / memory / recovery |

The corpus includes UTF-8, GB18030/GBK and Big5 source imports where device evidence is required. Source files are never rewritten in place.

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

## Host Core regression gate

`jingdu_core_performance_gate_test` is a deterministic hosted-CI guardrail. It defaults to a 64 MiB UTF-8 fixture and checks:

- first open/index completes within a generous hosted-runner ceiling;
- validated `.jdx` reopen is bounded;
- end-of-file full-text search remains bounded;
- 1000 random bounded reads remain responsive;
- peak RSS remains below a coarse anti-regression ceiling;
- no whole-document managed-language materialization is introduced.

Run the 200 MiB host stress form before a major reader/Core release:

```bash
JINGDU_PERF_FIXTURE_MIB=200 ctest --test-dir build/native \
  -R jingdu_core_performance_gate_test --output-on-failure
```

Hosted wall-clock ceilings are intentionally loose. Real experience is measured on Android release devices.

## Android benchmark evidence

Android performance evidence follows the platform-recommended Macrobenchmark/Baseline Profile workflow. Measure at minimum:

1. cold launch to Library;
2. reopen an unchanged imported book;
3. open Search/Chapters from an active reader session;
4. scroll/page a long book;
5. import/open 20/100/200 MiB fixtures;
6. Smart Clean scan on 20/100 MiB fixtures.

Record startup timing, frame timing/jank, memory and trace links in `DEVICE_MATRIX.md`. Use release-like compilation and a stable thermal/device state.

## Architectural performance invariants

1. The managed UI never owns the whole document as a single `String`.
2. Reader rendering uses bounded windows from the shared Core.
3. Immutable normalized/Clean revisions are content addressed; unchanged content reuses index/cache work.
4. Import, normalization, search, chapters, Clean, re-decode and export never perform file-size-proportional work on the UI thread.
5. First readable page and background indexing are separate product milestones; full-book secondary work must not unnecessarily block reading.
6. Library rendering uses metadata only and never opens every book to draw the grid.
7. A performance cache can always be discarded/rebuilt without changing product identity or user data.
