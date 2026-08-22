# Performance Contract

Large local TXT files are first-class inputs. Correctness is required at all supported sizes; the targets below are release gates rather than guarantees for every device.

## Reference sizes

Use deterministic 10 MiB, 100 MiB and 300 MiB UTF-8 fixtures, plus representative GB18030/Big5 imports.

Measure on release builds:

- source copy + normalization time;
- time to first readable window;
- peak process memory during import;
- random 6,000-code-point window read latency;
- full-text search latency;
- chapter discovery latency;
- clean-view generation latency;
- TTS next-chunk latency.

## Threading rule

Operations proportional to file size or number of matches must not run on the UI thread. This includes import/normalization, full search, chapter scan, clean-view generation and export copy. Android uses a bounded ExecutorService; HarmonyOS uses TaskPool `@Concurrent` tasks. UI code may perform only bounded page reads and bounded speech-chunk reads.

## Memory rule

The product must not concatenate the full book into one managed-language string. Core indexing/search and platform normalization are streaming/bounded. Any future implementation that scales memory linearly with whole-file text requires explicit review.

## Current automated host stress gate

CI generates a 32 MiB UTF-8 fixture and validates cross-buffer search, random reads, eight concurrent readers, malformed UTF-8 rejection and repeated handle lifecycle. Device 100/300 MiB results remain a release/device gate and are recorded in `DEVICE_MATRIX.md`.
