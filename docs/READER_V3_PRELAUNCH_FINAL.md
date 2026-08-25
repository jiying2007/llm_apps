# Reader V3 pre-launch final contract

Reader V3 is the final hard cut before the first store launch. No reader-schema compatibility layer is retained.

## Invariants

- TXT only; no EPUB/PDF/general document expansion.
- Offline/privacy first: no INTERNET permission, accounts, ads or analytics.
- Source/Core offsets are the only authoritative persisted reading position domain.
- Source TXT is never modified. Display transformations and annotations are local presentation metadata only.
- All reading/rendering paths use bounded windows; whole-document Compose/ML paths are forbidden.

## P0 correctness and architecture

- Exact source<->display projection for blank-line compression, Chinese conversion and custom replacements.
- Annotation anchors survive re-decode/re-normalization using contextual anchors; proportional remap is fallback only.
- One ReaderTypographySpec feeds Compose rendering and page measurement/cache.
- Real paragraph spacing, first-line indent, weight, font, alignment and columns participate in measurement.
- Native-feeling range selection with handles and edge-assisted scrolling; source ranges remain authoritative.
- Chapter-aware skim with preview, chapter ticks, return-to-origin history and commit-on-release.
- Proto DataStore for typed reader settings; no Preferences DataStore compatibility path.
- Room persistence for annotations, reading sessions and reading pace; no JSON full-file rewrite stores.
- ReaderViewModel + StateFlow/UDF owns screen state/events; Activity remains host/integration boundary.
- Hosted Emulator Macrobenchmark is a required regression gate for open/page/scroll/chapter/settings journeys; benchmark assembly alone is not a performance gate. Release-device Macrobenchmark remains the authority for user-facing performance qualification.

## P1 product quality

- Media3 MediaSessionService is the single TTS media authority.
- Previous/next TTS navigation is sentence/paragraph semantic navigation, not fixed-character subtraction.
- Visual Reading Map with chapter/reading/annotation density.
- Named custom reading themes and a Low Vision preset.
- Chapter progress and chapter/full-book remaining-time surfaces.
- Explicit gesture arbitration and transient HUDs for brightness/font/auto-scroll speed.
- Advanced reading settings use categorized full-screen navigation rather than one oversized sheet.
- Annotation search/filter and Markdown export.
- Reader-only extra dim.

## P2 completeness

- Two-stage long-range/cross-page selection.
- Local reading history/calendar heatmap.
- Local dictionary / ACTION_PROCESS_TEXT integration without network dependency.
- Advanced gesture customization while preserving simple defaults.
- Deterministic long-run contracts include 10MiB/100MiB Android journeys, a 960MiB near-1GiB native RSS gate, randomized TextProjection soak, semantic TTS navigation soak and accelerated Auto Scroll / Auto Page / TTS state soak.
- The Reader V3 Baseline Profile critical-user journey is executed in hosted CI and its generated profile output is retained with benchmark evidence.

## Merge gate

The branch must not merge until:

1. Android product build, Kotlin/Room KSP, unit tests, AndroidTest compile, all three native ABIs, lint, R8 and release AAB pass on the exact head.
2. Native/Harmony/Play/Terminal contracts pass.
3. Hosted Macrobenchmark actually runs, returns machine-readable benchmark results, passes P95/P99 frame SLO thresholds and completes the Baseline Profile journey.
4. Reader V3 correctness/soak/performance post-contracts pass, including the 960MiB native RSS gate.
5. Final diff/review audit has no blocker and no temporary workflow/patcher residue.
6. The final exact head is green before squash merge.
