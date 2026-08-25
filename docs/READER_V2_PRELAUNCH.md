# Jingdu Reader V2 — Pre-launch hard cut

This is the first-store-version terminal reader contract. There is intentionally no compatibility layer for legacy ReaderSettings, SharedPreferences reader storage, or split foreground/background TTS ownership.

## P0 — launch blockers

- Reader Architecture V2: ReaderRoute / ReaderViewModel / ReaderSession / ReaderMotionController / ReaderViewportEngine / ReaderChrome / ReaderQuickSettings.
- Exactly one persisted reading coordinate: Core/source offset. Viewport pixel/line offsets are transient only.
- Reader settings move to Jetpack DataStore; legacy reader SharedPreferences storage is removed without migration.
- TTS playback has one service authority and range-aware highlight state; UI observes playback and never owns a second speech engine.
- Paged viewport pre-layout/cache and bounded continuous viewport with source/display mapping cache and anchor-preserving rebases.
- Adaptive window-size decisions, large-screen/foldable-safe layout, quick Aa controls, chapter-aware skim/progress preview.
- Live auto-scroll controls, adaptive word/character-paced auto-page, reader brightness gesture and screen-on while automatic reading/TTS is active.
- Source-range highlight/note/copy/share without source TXT mutation.
- TalkBack actions, 48dp targets, 200% font-scale coverage and touch-exploration gesture fallback.
- Reader Macrobenchmark journeys plus baseline profile coverage for open book, page turn, continuous scroll, TOC/search/settings.

## P1 — daily-reader completeness

- Left/right/balanced tap-zone templates, pinch-to-font-size and optional double-tap bookmark.
- Rich reading status: chapter/book progress and remaining time, optional clock/battery.
- Typography: paragraph spacing, blank-line normalization for presentation, letter spacing, title treatment, custom local font import.
- Auto-scroll speed presets and live fine adjustment; Auto Page supports learned/adaptive and fixed timing modes.
- Reading annotations browser and source-offset-safe export/backup.
- Haptic feedback for page/annotation/slider milestones and a dismissible first-run gesture coach.

## P2 — advanced but launch-ready

- Reading Map: chapter boundaries, bookmarks, highlights/notes, read-position marker and jump-back support.
- Local-only reading-session statistics useful to the reader (not analytics): pace, session duration and completion estimate.
- Synthetic long-form qualification for 10 MiB / 100 MiB / 1 GiB boundaries and bounded-memory contracts.
- Soak harness for repeated page/continuous/auto-scroll/TTS state transitions; no network or telemetry.

## Invariants

- TXT-only. No EPUB/PDF/general-format expansion.
- No INTERNET permission, account, cloud sync, analytics or ads.
- Never modify/delete the source TXT.
- No whole-document Compose text, no whole-document conversion artifact, no whole-document ML.
- Search, TOC, bookmarks, annotations, TTS and progress all use Core/source offsets.
- Runtime motion is exclusive: IDLE / AUTO_SCROLL / AUTO_PAGE / TTS.
- Temporary viewport state may be high-frequency, but persisted progress commits are throttled and source-offset authoritative.

## Release gate

Do not merge until Android/Native/Harmony/Play/Terminal hosted gates are green on the exact head, reader performance/long-form contracts are present, temporary bootstrap workflows are gone, final diff/review audit is clean, and main publisher cleanup returns the repository to `main` only.
