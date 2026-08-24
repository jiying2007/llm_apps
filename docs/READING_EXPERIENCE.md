# Reading Experience

Jingdu's reading surface is a first-class product capability, not a thin view over TXT processing. The reader must remain comfortable for long daily sessions even when TXT Doctor, Smart Clean, Pro automation and other differentiated features are ignored.

## Product contract

The Android reader supports two explicit reading modes:

- **Paged** — bounded page windows with side tap zones, horizontal swipe paging, optional slide transition, progress seeking and previous/next controls.
- **Continuous** — a bounded rolling window around the authoritative source offset. The whole TXT is never loaded into Compose.

Both modes share the same `ReaderController` source-offset domain used by progress, bookmarks, search, Smart TOC, TXT Doctor, TTS and re-decode recovery. A UI mode must never create a second persisted position model.

## Core interactions

Default touch behavior is deliberately small and predictable:

- tap the left edge to move backward;
- tap the right edge to move forward;
- tap the center to show or hide reading controls;
- swipe horizontally to turn pages in paged mode;
- long-press remains available to Android text selection;
- optional reversed paging direction applies consistently to swipe and tap zones.

Touch zones and swipe paging may be disabled independently. User gestures stop competing motion such as auto-scroll before navigating.

## Immersive reading

Reader controls are overlays rather than layout chrome that permanently consumes text space. When controls auto-hide, system bars hide as well and can still be revealed transiently by the platform system gesture. Center tap restores the controls.

Reader-only brightness uses `WindowManager.LayoutParams.screenBrightness`; it never changes global system brightness. Orientation can follow the system or be locked to portrait/landscape while the reader is active. Both are restored when the reader leaves the composition.

## Auto page versus auto scroll

These are intentionally separate features:

- **Auto page** keeps the existing timed whole-page advance.
- **Auto scroll** advances continuous mode at a user-controlled dp/s rate.

They are mutually exclusive with one another and with TTS. Auto-scroll pauses on user touch, app backgrounding and explicit navigation. Runtime auto-scroll state is not restored after process restart; a reader must opt in again after reopening the app.

## Bounded continuous window

`ContinuousWindowReader` is read-only. It opens the same normalized private revision as the authoritative reader but only calls bounded `readAt` requests. The window includes a small back buffer so upward scrolling is possible without keeping the entire book in memory.

As the viewport approaches a window edge, the UI rebases around the current source offset. Visible display offsets are mapped back to source offsets before calling the existing sync action. Simplified/Traditional presentation conversion remains display-only.

The companion reader must never:

- save progress;
- mutate source TXT;
- own bookmarks or TOC state;
- run whole-document conversion;
- create a second index or persistence format.

## Location history

Search, chapter, bookmark and progress-seek jumps are browser-like navigation operations. Jingdu keeps a bounded in-memory Back/Forward location stack so a reader can inspect a distant location and return to the prior reading point. The stack is session state; the persisted book progress remains the source offset owned by the main reader.

## Typography and display

The terminal baseline includes:

- Standard, Comfort, Large Text and Night presets;
- paper, light, night and OLED-black palettes;
- system or serif face;
- font size, line height, font weight;
- horizontal and vertical margins;
- optional first-line indent;
- start or justified alignment;
- single/two-column wide-screen policy;
- optional 3/5-line focus band;
- optional local reading-status / remaining-time estimate.

A preset is only a convenient bundle of display settings. Editing a visual parameter converts the preset to Custom.

## Volume keys and TTS

Volume-key paging is configurable. The default policy turns pages only when TTS is not active, so volume buttons keep their normal audio meaning during foreground or background read-aloud. Users may explicitly choose always-page or always-system-volume behavior and may reverse the page direction.

## Accessibility and safety

- Primary reader controls retain localized content descriptions.
- The reading surface has a localized semantic label.
- The interaction layer observes pointer events without intentionally consuming long-press text selection.
- Text-size changes remain within the existing supported range and settings continue to use localized strings across English, Simplified Chinese and Traditional Chinese.
- Auto motion always has a visible or direct tap path to pause.

## Non-goals

This reading-experience layer does not add EPUB/PDF support, cloud sync, accounts, analytics, ads, network access or whole-book ML. It does not rewrite Core ABI v2 and does not modify source TXT.
