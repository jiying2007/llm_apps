# Product Requirements — Android v2.1

## Objective

Turn Android from a functional engineering MVP into a focused daily-use TXT reader while preserving the hard-cut native architecture and offline/privacy guarantees of v2.0.

## P0 requirements

### Library
- Normal launch lands on a real library rather than auto-opening or showing an engineering toolbar.
- Imported books show title, encoding, file size, last activity and useful reading progress.
- Empty state explains the product and has one obvious import action.
- Removing a book clearly states that the external source is untouched.

### Import and encoding
- AUTO remains the default.
- Encoding override is available after import without requiring the source picker again.
- Re-decoding produces a new immutable normalized revision.
- Progress and revision-bound bookmarks do not leak across a changed decode revision.

### Reader
- Content has priority over controls.
- Next/previous page span is derived from the actual laid-out viewport instead of a fixed character count.
- Search, chapters and progress seeking are direct actions.
- Bookmarks, Clean, encoding and settings are contextual actions.
- Back follows platform navigation semantics and is compatible with predictive back.

### Clean
- Existing rules are visible and removable.
- Adding an empty replacement means deletion.
- Original/Clean state is explicit.
- Export is one action from Clean tools.
- Original progress/bookmarks remain isolated from Clean offsets.

### Reading comfort
- Paper, light and night page tones.
- System and serif type choices.
- Font size, line height and horizontal margin controls.
- Wide windows cap line length rather than stretching paragraphs.
- Preferences survive process restart.

### Long-session tools
- TTS starts/pauses from the reader and respects audio focus.
- TTS rate/pitch are persistent preferences.
- Auto page interval is adjustable.
- Sleep timer supports off/15/30/60 minutes.
- Volume key previous/next remains supported.

### Accessibility
- All icon-only controls have meaningful content descriptions.
- Material touch targets satisfy 48dp minimum.
- 200% system font scaling must keep primary navigation usable.
- Busy state is both visual and textual.
- Destructive actions require explicit confirmation.

## P1 requirements

- Search sheet keeps the query and result context visible.
- Chapter scan shows empty-state feedback rather than a silent blank list.
- Library layout adapts from one column to an adaptive grid.
- Android can accept both `ACTION_VIEW` and `ACTION_SEND` text input.
- Launcher supports themed monochrome icon on Android 13+.

## Performance requirements

- Import, normalization, open/index, search, chapter scan, Clean generation, re-decode and export remain off the main thread.
- The reader never materializes the entire document in a managed-language string.
- Reader page fetch is bounded to read-ahead; Compose renders only the current page region.
- 10/100/300 MiB device corpus remains the performance qualification matrix.
- No UI action should intentionally block waiting for a whole-file operation.

## Privacy requirements

- No INTERNET permission.
- No account requirement.
- No analytics/advertising runtime SDK.
- No private TXT content in logs/crash reports by design.
- Source TXT is never modified or deleted.

## Acceptance

Android source gate must compile Debug/Release, lint both variants, compile JNI ABIs, assemble AndroidTest, pass repository/product contracts, and keep the existing Native Core stress gate green. Device UX/performance evidence is recorded before the next Play production rollout.
