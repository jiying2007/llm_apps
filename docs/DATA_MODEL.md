# Data Model and Persistence

## Book identity

The product uses content identity, not import time or platform-local identifiers.

- `sourceSha256`: lowercase SHA-256 of the private copy of the selected source bytes.
- `id`: exactly equal to `sourceSha256` on Android and HarmonyOS.
- `normalizedSha256`: lowercase SHA-256 of the normalized UTF-8 document.
- `encoding`: source decoding selected by AUTO detection or explicit user override.
- `progress`: Unicode scalar/code-point offset in the normalized source view only.
- `touchedAt`: platform-local recency metadata only; never part of identity.

The selected external source file is never modified. Import first creates an app-private source copy and all subsequent work uses private files.

## Immutable artifact layout

A book directory is keyed by `sourceSha256`. Source bytes are immutable because equal source hashes imply equal bytes. Derived files are content addressed and are never replaced underneath an active reader:

```text
books/<sourceSha256>/
  source.bin
  document-<normalizedSha256>.txt
  clean-<repairRevision>.txt
```

Normalization is written to a temporary file, fsynced, hashed and then published under `document-<normalizedSha256>.txt`. An existing target is reused rather than overwritten. Repair output follows the same rule with `clean-<repairRevision>.txt`.

A new reader session is built against the new immutable path first. Only after that session is successfully published may older document/clean revisions for the same book be pruned. A failed import/open therefore leaves the previously published session and files usable.

Legacy fixed names such as `document.txt`, `clean.txt` and `clean.revision` are not part of the production persistence contract. They may only appear in cleanup code that deletes stale experimental artifacts.

## Normalized document and revision domain

Every source is decoded by the platform charset implementation to UTF-8 in the app sandbox. The native core then validates and operates only on normalized UTF-8. A source hash and normalized hash are intentionally separate: identical decoded text from different source bytes does not collapse source identity.

`progress` and bookmarks are valid only in the offset domain identified by `normalizedSha256`. Re-importing the same source bytes with a different decoding preserves progress only when the resulting `normalizedSha256` is unchanged; otherwise progress resets to zero. Bookmarks remain source-view offsets and must not be created or applied from a derived clean view without an explicit source/derived projection.

The current clean preview intentionally does not persist its own position into source progress and starts at offset zero. This is a correctness rule, not a compatibility fallback. A future feature that maps clean-view positions back to source positions must introduce a tested projection contract rather than approximate offsets.

## Repair rules

The current production rule pack is an ordered list of literal find/replacement pairs serialized with record separator `U+001E` and field separator `U+001F`. Those separators are forbidden inside rule fields.

A repair artifact is identified by:

`repairRevision = SHA256("jingdu-repair-v1\n" + normalizedSha256 + "\n" + packedRules)`

The revision is the clean artifact identity itself; no sidecar revision file is required.

## Book-local persisted state

Each platform persists the same logical state:

- book metadata listed above;
- source-view reading progress bound to `normalizedSha256`;
- source-view bookmarks as code-point offsets;
- ordered repair-rule pack.

Storage format is platform-native and is not shared across operating systems. Semantics are shared.

## Hard-cut policy

There is no migration from the earlier experimental private-data schema. Metadata missing the v2 identity fields is rejected/ignored. Reintroducing migration compatibility requires an explicit future product decision and is not part of this repository state.
