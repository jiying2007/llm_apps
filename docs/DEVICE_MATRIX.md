# Device Matrix

This file is the release evidence checklist. Do not mark an item passed without recording device/OS/build evidence in the release system or PR.

## Android

Minimum source compatibility: API 26. Target/compile: API 36.

Required release matrix:

| Area | Minimum evidence |
| --- | --- |
| API levels | API 26 plus current target API 36 |
| OEM diversity | at least two OEM families |
| Files | import/export through system document provider; selected external source remains unchanged |
| Sizes | 10/100/300 MiB |
| Encodings | UTF-8, UTF-16 with/without BOM, GB18030, Big5, malformed legacy bytes, 64 KiB sample ending inside a multibyte UTF-8 sequence |
| Identity/reimport | same source bytes import to the same `sourceSha256`; same normalized revision preserves progress; manual encoding change that changes `normalizedSha256` resets progress |
| Publish recovery | interrupt/retry import around private-file publication; no half-written source/normalized file may replace the last valid private copy |
| Lifecycle | rotation/configuration, background/foreground, process death/reopen; stale background open/search results must not replace the active reader |
| Reader | paging, search, chapters, source bookmarks, repair, clean export |
| Offset domain | clean preview starts in its derived view and must not overwrite normalized-source progress/bookmarks; returning to source restores the source-domain position |
| TTS | start/pause/end, permanent/transient/duck audio-focus interruption, wired/Bluetooth route |
| Accessibility | TalkBack and large font |
| Failure | low storage / write failure without external-source or last-valid-private-copy corruption |

## HarmonyOS

Baseline: HarmonyOS/SDK 6.0 product configuration; validate on at least two device/system combinations available to release engineering.

Run the same semantic matrix as Android with Harmony-native capabilities:

| Area | Minimum evidence |
| --- | --- |
| Devices | at least two device/system combinations |
| Files | DocumentViewPicker import/export; selected external source remains unchanged |
| Sizes | 10/100/300 MiB |
| Encodings | UTF-8, UTF-16 with/without BOM, GB18030, Big5, malformed legacy bytes, truncated 64 KiB UTF-8 sample boundary |
| Identity/reimport | same `sourceSha256` identity; normalized-revision-safe progress; explicit encoding override resets progress when normalized SHA changes |
| Publish recovery | repeat/interrupted import must preserve or recover the last valid private source/normalized copy; no half-published file is accepted |
| Responsiveness | TaskPool import/open/search/chapter/clean/export paths keep ArkUI responsive; stale async handle/result is closed/ignored |
| Lifecycle | background/foreground, process death/reopen and reader state recovery |
| Reader | paging, search, chapters, source bookmarks, repair and clean export |
| Offset domain | clean-view offsets never persist into normalized-source progress/bookmarks |
| TTS | Core Speech Kit start/stop/end and applicable audio interruption/route scenarios |
| Accessibility | system screen reader/large-font behavior |
| Failure | low-storage/write failure without external-source or last-valid-private-copy corruption |

## Cross-platform parity

For the golden corpus record and compare:

- source SHA-256;
- normalized SHA-256;
- AUTO encoding result;
- native character count;
- representative window contents and offsets;
- search/chapter offsets;
- repair revision;
- clean-output SHA-256;
- progress retention/reset decision for same-source same/different normalized revisions.

All values and semantic decisions except platform presentation metadata must match.
