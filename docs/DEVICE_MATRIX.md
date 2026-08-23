# Device Matrix

This file is the release evidence checklist. Do not mark an item passed without recording device/OS/build evidence in the release system or PR.

## Android

Minimum source compatibility: API 26. Target API: 36. Compile SDK: 37.

Required release matrix:

| Area | Minimum evidence |
| --- | --- |
| API levels | API 26 plus current target API 36 |
| OEM diversity | at least two OEM families |
| App locales | launch and navigate Library/Reader/Clean/Settings under `zh-CN`, `zh-TW`, `zh-HK`, `en-US`; unsupported system locale falls back to English; per-app language change must not change book identity/progress/rules |
| Locale layout | 200% font scale in Simplified Chinese, Traditional Chinese and English; no clipped primary actions/content descriptions on phone and expanded-width layouts |
| Files | import/export through system document provider; selected external source remains unchanged |
| Sizes | 10/100/300 MiB |
| Encodings | UTF-8, UTF-16 with/without BOM, GB18030, Big5, malformed legacy bytes, 64 KiB sample ending inside a multibyte UTF-8 sequence |
| Identity/reimport | same source bytes import to the same `sourceSha256`; same normalized revision preserves progress; manual encoding change that changes `normalizedSha256` resets progress |
| Publish recovery | interrupt/retry import around private-file publication; no half-written source/normalized file may replace the last valid private copy |
| Lifecycle | rotation/configuration, background/foreground, process death/reopen; stale background open/search results must not replace the active reader |
| Reader | paging, exact search, Simplified↔Traditional search fallback, chapters, source bookmarks, repair, clean export |
| Smart Clean | Simplified and Traditional promotional/watermark samples return equivalent locale-neutral reason codes and localized UI reasons; explicit Apply remains required |
| Offset domain | clean preview starts in its derived view and must not overwrite normalized-source progress/bookmarks; returning to source restores the source-domain position |
| TTS | start/pause/end, content-language auto selection for `zh-CN`/`zh-TW`/`zh-HK`/English when no voice is pinned, explicit offline voice override, permanent/transient/duck audio-focus interruption, wired/Bluetooth route |
| Accessibility | TalkBack, localized content descriptions and large font |
| Failure | low storage / write failure without external-source or last-valid-private-copy corruption |

Hosted CI assembles Android/AndroidTest and statically verifies locale resource parity/format placeholders. The device rows above remain release evidence because hosted source CI does not substitute for real Android locale, TTS-engine or OEM behavior.

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

HarmonyOS localization remains source-contract work until the Harmony release milestone. When activated, it must use platform-native resources and preserve the same UI-locale/document-language separation defined in `LOCALIZATION.md`; no translated presentation strings belong in the shared C++ Core.

## Cross-platform parity

For the golden corpus record and compare:

- source SHA-256;
- normalized SHA-256;
- AUTO encoding result;
- native character count;
- representative window contents and offsets;
- search/chapter offsets;
- Smart Clean reason code and candidate semantics for equivalent Simplified/Traditional samples;
- repair revision;
- clean-output SHA-256;
- progress retention/reset decision for same-source same/different normalized revisions.

All values and semantic decisions except platform presentation metadata must match.
