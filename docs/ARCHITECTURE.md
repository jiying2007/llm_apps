# Architecture

## One product, one core, two native shells

```text
Android UI/platform (Java) -- JNI -----+
                                        +-- C ABI v2 -- C++17 Jingdu Core
HarmonyOS UI/platform (ArkTS) -- NAPI --+
```

`core/native` is the single source of truth for cross-platform document semantics. The platform shells own only operating-system capabilities: document picker/export, charset decoding into app-private UTF-8, UI/lifecycle, concurrency adapters, TTS/audio, preferences and store signing.

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

The external source is never modified.

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

## Concurrency

Operations proportional to file size or match count do not run on a UI thread. Android uses a single bounded worker executor for session-affecting long work so reader transitions serialize. HarmonyOS uses TaskPool `@Concurrent` tasks with only transferable path/string/number parameters. Bounded page reads and bounded TTS chunks may run synchronously.

## Persistence

Android and HarmonyOS may use different platform persistence APIs but must persist the same logical model defined in `DATA_MODEL.md`. Platform timestamps are metadata, not identity. Progress/bookmarks belong only to the normalized source revision; derived clean offsets are not persisted into that domain.

## Repository rules

There is no compatibility core, prototype core, old ABI bridge, migration adapter, archived implementation tree or committed release binary. A platform-specific implementation of shared document behavior is a defect.

See `CORE_CONTRACT.md`, `DATA_MODEL.md`, `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md` and `DEVICE_MATRIX.md` for normative contracts.
