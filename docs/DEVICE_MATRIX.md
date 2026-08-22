# Device Matrix

This file is the release evidence checklist. Do not mark an item passed without recording device/OS/build evidence in the release system or PR.

## Android

Minimum source compatibility: API 26. Target/compile: API 36.

Required release matrix:

| Area | Minimum evidence |
| --- | --- |
| API levels | API 26 plus current target API 36 |
| OEM diversity | at least two OEM families |
| Files | import/export through system document provider |
| Sizes | 10/100/300 MiB |
| Encodings | UTF-8, UTF-16, GB18030, Big5, malformed legacy bytes |
| Lifecycle | rotation/configuration, background/foreground, process death/reopen |
| Reader | paging, search, chapters, bookmarks, repair, clean export |
| TTS | start/pause/end, audio focus interruption, wired/Bluetooth route |
| Accessibility | TalkBack and large font |
| Failure | low storage / write failure without source corruption |

## HarmonyOS

Baseline: HarmonyOS/SDK 6.0 product configuration; validate on at least two device/system combinations available to release engineering.

Required areas mirror Android: DocumentViewPicker import/export, 10/100/300 MiB, encoding matrix, TaskPool responsiveness, lifecycle/reopen, reader features, Core Speech Kit TTS, accessibility and low-storage/write failure.

## Cross-platform parity

For the golden corpus record and compare:

- source SHA-256;
- normalized SHA-256;
- AUTO encoding result;
- native character count;
- search/chapter offsets;
- repair revision;
- clean-output SHA-256.

All values except platform presentation metadata must match.
