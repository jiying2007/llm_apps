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
- Real device Macrobenchmark baselines for open/page/scroll/auto-scroll/chapter/settings/conversion; benchmark assembly alone is not considered a performance gate.

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
- Long-run soak contracts for 10MiB/100MiB/1GiB TXT, auto-scroll and TTS.

## Merge gate

The branch must not merge until:

1. Android product build, unit tests, AndroidTest compile, lint, R8 and release AAB pass.
2. Native/Harmony/Play/Terminal contracts pass.
3. Reader V3 correctness/performance contracts pass.
4. Final diff/review audit has no blocker and no temporary workflow/patcher residue.
5. The final exact head is green before squash merge.
