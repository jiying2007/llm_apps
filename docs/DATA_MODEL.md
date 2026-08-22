# Data Model and Persistence

## Book identity

The product uses content identity, not import time or platform-local identifiers.

- `sourceSha256`: lowercase SHA-256 of the private copy of the selected source bytes.
- `id`: exactly equal to `sourceSha256` on Android and HarmonyOS.
- `normalizedSha256`: lowercase SHA-256 of the normalized UTF-8 document.
- `encoding`: source decoding selected by AUTO detection or explicit user override.
- `progress`: Unicode scalar/code-point offset in the currently selected source-derived text view.
- `touchedAt`: platform-local recency metadata only; never part of identity.

The selected external source file is never modified. Import first creates an app-private source copy and all subsequent work uses private files.

## Normalized document

Every source is decoded by the platform charset implementation to UTF-8 in the app sandbox. The native core then validates and operates only on normalized UTF-8. A source hash and normalized hash are intentionally separate: identical decoded text from different source bytes does not collapse source identity.

## Repair rules

The current production rule pack is an ordered list of literal find/replacement pairs serialized with record separator `U+001E` and field separator `U+001F`. Those separators are forbidden inside rule fields.

A repair artifact is identified by:

`repairRevision = SHA256("jingdu-repair-v1\n" + normalizedSha256 + "\n" + packedRules)`

A cached clean view must be regenerated when that revision changes.

## Book-local persisted state

Each platform persists the same logical state:

- book metadata listed above;
- reading progress;
- bookmarks as code-point offsets;
- ordered repair-rule pack;
- clean artifact revision.

Storage format is platform-native and is not shared across operating systems. Semantics are shared.

## Hard-cut policy

There is no migration from the earlier experimental private-data schema. Metadata missing the v2 identity fields is rejected/ignored. Reintroducing migration compatibility requires an explicit future product decision and is not part of this repository state.
