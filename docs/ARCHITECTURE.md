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
  -> app-private normalized UTF-8
  -> normalizedSha256
  -> shared core index/read/search/chapter/speech/repair
  -> optional derived clean view keyed by repairRevision
```

The external source is never modified.

## Concurrency

Operations proportional to file size or match count do not run on a UI thread. Android uses a bounded ExecutorService. HarmonyOS uses TaskPool `@Concurrent` tasks with only transferable path/string/number parameters. Bounded page reads and bounded TTS chunks may run synchronously.

## Persistence

Android and HarmonyOS may use different platform persistence APIs but must persist the same logical model defined in `DATA_MODEL.md`. Platform timestamps are metadata, not identity.

## Repository rules

There is no compatibility core, prototype core, old ABI bridge, migration adapter, archived implementation tree or committed release binary. A platform-specific implementation of shared document behavior is a defect.

See `CORE_CONTRACT.md`, `ENCODING.md`, `PERFORMANCE.md`, `TESTING.md` and `DEVICE_MATRIX.md` for normative contracts.
