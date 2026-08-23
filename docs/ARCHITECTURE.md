# Architecture

## One product, one core, two native shells

```text
Android Compose / Kotlin platform shell -- JNI -----+
                                                     +-- C ABI v2 -- C++17 Jingdu Core
HarmonyOS ArkUI / ArkTS platform shell ----- NAPI --+
```

`core/native` is the single source of truth for cross-platform document semantics. Platform shells own only operating-system capabilities: document picker/export, charset decoding into app-private UTF-8, UI/lifecycle, concurrency adapters, TTS/audio, preferences and store signing.

Android is Compose-first. Its UI state, responsive layout and reading interaction are implemented in Kotlin/Jetpack Compose Material 3; JNI remains a thin adapter to the same native Core. There is no View-based fallback screen or Java business Core.

Android presentation source is intentionally modular rather than one monolithic Activity/UI file:

```text
MainActivity.kt       lifecycle, platform IO, worker/session orchestration
JingduApp.kt          app theme/root state dispatch
LibraryScreen.kt      library/empty state
ReaderScreen.kt       reading surface and primary navigation
ReaderSheets.kt       Search/Chapters/Bookmarks/Clean/Encoding/Settings
ReaderPreferences.kt  presentation preferences
UiModels.kt           immutable Compose state models
UiComponents.kt       shared Compose helpers
UiFormatters.kt       pure display formatting
```

## Product state model

Android intentionally exposes two top-level product states:

```text
Library
  -> Reader
       -> contextual sheet: Search / Chapters / Bookmarks / Clean / Encoding / Settings
```

The library is the normal launch state. Reader chrome uses progressive disclosure: high-frequency navigation is persistent, while advanced operations are sheets rather than a permanent command toolbar. `docs/PRODUCT.md` and `docs/UX.md` are normative for this product/UI boundary.

## Data flow

```text
user-selected source
  -> app-private byte-for-byte copy
  -> sourceSha256 == book id
  -> shared AUTO encoding decision (or manual override)
  -> platform charset decoder
  -> temporary normalized UTF-8 + fsync
  -> normalizedSha256
  -> immutable document-<normalizedSha256>.txt
  -> shared core index/read/search/chapter/speech/repair
       -> disposable <document-path>.jdx sparse-index cache
  -> optional immutable clean-<repairRevision>.txt
```

The external source is never modified. Android keeps the private `source.bin`, so encoding can be changed later without asking the user to choose the source file again.

The `.jdx` file is a derived Core cache only. It is validated before use, may be deleted at any time, is rebuilt from the immutable UTF-8 document when absent/corrupt, and is never part of BookIdentity/normalizedSha/repairRevision.

## Reader session publication

A reader never follows a mutable pathname. Normalized and clean artifacts are content-addressed immutable files. Re-importing a source with a different decoding or regenerating a clean view creates a new path rather than replacing the file beneath an active reader.

Both platform shells follow the same publication rule:

1. finish writing/fsyncing the candidate artifact;
2. compute and validate its content/revision identity;
3. open/build a candidate native reader session against that immutable path off the UI thread;
4. atomically publish the candidate session;
5. close the previous session;
6. only then prune obsolete revisions and orphan index caches.

A failed import/open leaves the previous published session usable. This ordering is a correctness contract, not an implementation preference.

## Android reading surface

The native Core exposes bounded read-ahead. Compose measures the actual laid-out visible text and reports the visible code-point span back to the activity. Sequential next-page navigation advances by that measured span rather than a fixed character constant; an in-session history stack makes the immediate previous page exact.

Seeking/search/chapter jumps clear sequential history because they establish a new navigation anchor. Persisted progress remains a Core character offset in the normalized original-view domain.

Search and chapter discovery reuse the active immutable native session instead of reopening/reindexing the same file. Chapters are cached in presentation state for the lifetime of that reader session.

## Lifecycle restoration

Configuration recreation and ordinary process restoration preserve product intent without persisting native handles:

- if the saved top-level state was Library, launch remains in Library;
- if it was Reader, the Activity restores book id + normalized revision + source-view position and reopens a fresh native session off the UI thread;
- a saved position is accepted only when the current `normalizedSha256` still matches;
- Clean preview restoration reopens the derived revision and never writes its offset into source progress;
- pending export path may be restored only while its private file still exists;
- TTS/auto-page/audio focus are runtime state and are not blindly resumed after process death.

## Concurrency

Operations proportional to file size or match count do not run on a UI thread. Android uses one serialized worker executor for session-affecting long work so reader transitions cannot race. HarmonyOS uses TaskPool `@Concurrent` tasks with only transferable path/string/number parameters. Bounded page reads and bounded TTS chunks may run synchronously.

Android long work includes import/normalization, first native open/index construction, search, chapter scan, clean generation, re-decode and export. Compose state is published only on the main thread after generation-token validation.

## Adaptive Android layout

Compose owns edge-to-edge insets and responsive layout. Library cards use an adaptive grid; reader paragraphs cap their text measure on expanded windows instead of stretching to the full tablet/foldable width. Controls preserve Android minimum touch targets and icon-only controls expose content descriptions.

## Persistence

Android and HarmonyOS may use different platform persistence APIs but must persist the same logical model defined in `DATA_MODEL.md`. Platform timestamps are metadata, not identity. Progress/bookmarks belong only to the normalized source revision; derived clean offsets are not persisted into that domain.

Android presentation preferences (palette, font family/size, line height, margins, TTS rate/pitch and auto-page interval) are product preferences, not shared document semantics.

## Repository rules

There is no compatibility core, prototype core, old ABI bridge, migration adapter, archived implementation tree, View-based Android fallback or committed release binary. A platform-specific implementation of shared document behavior is a defect.

See `PRODUCT.md`, `PRODUCT_REQUIREMENTS.md`, `UX.md`, `CORE_CONTRACT.md`, `DATA_MODEL.md`, `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md` and `DEVICE_MATRIX.md` for normative contracts.
