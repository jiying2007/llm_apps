# Architecture

## One product, one core, two native shells

```text
Android Compose / Kotlin platform shell -- JNI -----+
                                                     +-- C ABI v2 -- C++17 Jingdu Core
HarmonyOS ArkUI / ArkTS platform shell ----- NAPI --+
```

`core/native` is the single source of truth for cross-platform document semantics. Platform shells own only operating-system capabilities: document picker/export, charset decoding into app-private UTF-8, UI/lifecycle, concurrency adapters, TTS/audio, preferences and store signing.

Android is Compose-first. Its UI state, responsive layout and reading interaction are implemented in Kotlin/Jetpack Compose Material 3; JNI remains a thin adapter to the same native Core. There is no View-based fallback screen or Java business Core.

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
  -> optional immutable clean-<repairRevision>.txt
```

The external source is never modified. Android keeps the private `source.bin`, so encoding can be changed later without asking the user to choose the source file again.

## Reader session publication

A reader never follows a mutable pathname. Normalized and clean artifacts are content-addressed immutable files. Re-importing a source with a different decoding or regenerating a clean view creates a new path rather than replacing the file beneath an active reader.

Both platform shells follow the same publication rule:

1. finish writing/fsyncing the candidate artifact;
2. compute and validate its content/revision identity;
3. open/build a candidate native reader session against that immutable path off the UI thread;
4. atomically publish the candidate session;
5. close the previous session;
6. only then prune obsolete revisions.

A failed import/open leaves the previous published session usable. This ordering is a correctness contract, not an implementation preference.

## Android reading surface

The native Core exposes bounded read-ahead. Compose measures the actual laid-out visible text and reports the visible code-point span back to the activity. Sequential next-page navigation advances by that measured span rather than a fixed character constant; an in-session history stack makes the immediate previous page exact.

Seeking/search/chapter jumps clear sequential history because they establish a new navigation anchor. Persisted progress remains a Core character offset in the normalized original-view domain.

## Concurrency

Operations proportional to file size or match count do not run on a UI thread. Android uses one serialized worker executor for session-affecting long work so reader transitions cannot race. HarmonyOS uses TaskPool `@Concurrent` tasks with only transferable path/string/number parameters. Bounded page reads and bounded TTS chunks may run synchronously.

Android long work includes import/normalization, native open/index construction, search, chapter scan, clean generation, re-decode and export. Compose state is published only on the main thread after generation-token validation.

## Adaptive Android layout

Compose owns edge-to-edge insets and responsive layout. Library cards use an adaptive grid; reader paragraphs cap their text measure on expanded windows instead of stretching to the full tablet/foldable width. Controls preserve Android minimum touch targets and icon-only controls expose content descriptions.

## Persistence

Android and HarmonyOS may use different platform persistence APIs but must persist the same logical model defined in `DATA_MODEL.md`. Platform timestamps are metadata, not identity. Progress/bookmarks belong only to the normalized source revision; derived clean offsets are not persisted into that domain.

Android presentation preferences (palette, font family/size, line height, margins, TTS rate/pitch and auto-page interval) are product preferences, not shared document semantics.

## Repository rules

There is no compatibility core, prototype core, old ABI bridge, migration adapter, archived implementation tree, View-based Android fallback or committed release binary. A platform-specific implementation of shared document behavior is a defect.

See `PRODUCT.md`, `UX.md`, `CORE_CONTRACT.md`, `DATA_MODEL.md`, `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md` and `DEVICE_MATRIX.md` for normative contracts.
