# Product — 净读 TXT

## Positioning

**净读 TXT is a privacy-first, offline reader for local TXT files, optimized for Chinese long-form and web-novel reading.**

It is not a general document suite. Its product promise is narrower and stronger:

> Open messy TXT correctly, clean distracting text locally, and keep reading comfortably for hours.

## Primary users

1. Readers who keep local TXT novels or long-form archives on Android/HarmonyOS.
2. Users who frequently meet GB18030/GBK/Big5/UTF-16 encoding problems.
3. Long-session readers who care about typography, progress recovery, TTS and low distraction.
4. Privacy-conscious users who do not want accounts, cloud upload, ads or analytics in the reading path.

## Core jobs

### 1. Open it correctly
- One-tap local TXT import with AUTO encoding detection.
- Manual encoding override remains available when AUTO is wrong.
- Source bytes are never modified.
- Large files must not block the UI thread.

### 2. Make it clean
- Literal clean rules are first-class product functionality, not an advanced settings afterthought.
- Rules are editable per book.
- Clean preview is isolated from original progress/bookmark offsets.
- Clean output can be exported as a new TXT without touching the source.

### 3. Keep me in the text
- Library resumes the most recent book quickly.
- Search, chapters and bookmarks are one interaction away from the reader.
- Reading chrome is quiet and predictable.
- Typography, theme and page spacing are adjustable without leaving the book.
- Volume keys, auto paging, TTS and sleep timer support long sessions.

## Product principles

1. **Offline by default and by architecture** — no account, network permission, ads, telemetry or runtime third-party SDK in the reading path.
2. **TXT depth over format breadth** — do not add EPUB/PDF merely to match feature lists. Add a format only when its quality can match the TXT path.
3. **Correctness before decoration** — encoding, offset domains, immutable revisions and crash recovery are product features.
4. **Progressive disclosure** — library and reading are simple; advanced tools live in sheets/menus rather than permanent toolbars.
5. **Calm reading surface** — content owns the screen; controls should not look like an engineering console.
6. **Large-file credibility** — 10/100/300 MiB are normal verification sizes, not edge cases.

## Information architecture

### Library
- Product identity and privacy promise.
- Recent/all books with progress, encoding, size and last-read state.
- One primary action: Import TXT.
- Book overflow: open, re-decode, delete.

### Reader
- Top: back, book title, search, chapters, more.
- Center: typography-first page surface.
- Bottom: previous, position slider, next, TTS.
- More: bookmarks, Clean, encoding, reading settings, delete.

### Reading tools
- Search: query + contextual results.
- Chapters: generated chapter list.
- Bookmarks: original-view positions only.
- Clean: rule list, add/remove, preview/original switch, export.
- Settings: page tone, font, size, line height, margins, TTS, auto page, sleep timer.

## Non-goals for the 2.x line

- Online bookstore or OPDS catalog.
- Social/community features.
- Mandatory cloud sync.
- In-reader advertising.
- AI features that require uploading private book text.
- A compatibility layer for pre-2.x experimental private metadata.

## Success measures

No analytics are required to collect these. They are release/test objectives:

- Import a typical TXT to readable first page with no user configuration.
- Reopen an already imported book without UI-thread stalls.
- Recover the last original-view reading position after process death.
- Search/chapter/clean operations never freeze the main thread on the 300 MiB corpus.
- Reader controls remain usable at 200% font scale and TalkBack touch targets meet 48dp minimum.
- Compact, tablet/foldable and landscape windows remain readable without stretched line lengths.
- Android release retains zero network permission and zero runtime advertising/analytics SDKs.
