# Architecture

## One product, one core, two native shells

```
Android UI/platform (Java) -- JNI -----+
                                        +-- C ABI -- C++17 Jingdu Core
HarmonyOS UI/platform (ArkTS) -- NAPI --+
```

The core has no Android, JNI, HarmonyOS, Node-API or UI dependencies. Platform shells own only capabilities that necessarily differ by OS: user-file picker, legacy-byte decoding, private-file creation, system TTS/audio focus, lifecycle and store signing.

The normalization boundary is deliberate: selected source bytes are copied into the app sandbox and decoded to UTF-8 by each operating system's maintained charset implementation. From that point forward every document operation is shared native code.

## Core contract

ABI v1 owns UTF-8 validation, sparse character/byte indexing, bounded window reads, full-text literal search, chapter discovery, sentence-bounded speech chunks and atomic literal-rule export. Handles are process-local and must be closed by the platform bridge.

## Non-negotiable repository rules

There is no compatibility core, prototype core, migration adapter or old package-data migration. Old private application data is intentionally not part of this 2.0 hard cut. Source trees may not contain release binaries, private keys, competitor APKs or archived transition implementation.

Android and HarmonyOS must change together when a core ABI or product behavior changes. A platform-specific reimplementation of a core operation is a defect.
