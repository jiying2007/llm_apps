# Smart Clean 3.0 and Chinese Conversion Architecture

## Product intent

Jingdu remains a TXT-only, offline, privacy-first reader optimized for long Chinese novels. Intelligence is used to remove repetitive work without turning the reader into a cloud service or a heavyweight full-document ML pipeline.

## Smart Clean pipeline

### L0 — Encoding guard

The shared Core remains authoritative for encoding detection/normalization. UTF/GB18030/GBK/GB2312/Big5 handling stays deterministic and users retain manual re-decode from the immutable source copy.

### L1 — Versioned deterministic signatures

`BuiltinCleanRules.PACK_VERSION` identifies the in-app signature pack. Rules are reviewed source assets with stable IDs, locale and confidence metadata. Updates ship with app source/releases; there is no remote rule download.

### L2 — Native statistical scan

The shared Core continues to detect URL/promotion/repetition candidates with bounded memory and stable reason codes.

### L3 — Streaming refinement

`SmartCleanRefiner` performs a second streaming pass over normalized UTF-8 and adds only two deliberately conservative candidate classes:

- inline promotional suffix/fragment candidates;
- lines with a very high malformed/replacement/control-character ratio.

The refiner never loads the whole book, caps candidate memory, and never mutates source text. These higher false-positive-risk candidates are never preselected.

### L4 — Candidate-only local semantic seam

`SemanticCandidateClassifier` is the only future ML seam. Its contract accepts one already-filtered candidate string and returns BODY / AD / UNCERTAIN. A model implementation must not receive a complete TXT document. The current implementation is disabled; no placeholder model or remote inference is shipped.

## Apply/undo semantics

Smart Clean still converts selected candidates into explicit local repair rules. Before applying suggestions, Jingdu stores one rule-only snapshot. Undo restores that rules snapshot; book text is never copied into history and the source TXT is never modified.

## Chinese conversion

Chinese conversion is independent from Clean. It changes presentation, not document identity.

Supported modes:

- ORIGINAL
- SIMPLIFIED (`t2s`)
- TRADITIONAL (`s2t`)
- TAIWAN (`s2tw`)
- TAIWAN_PHRASES (`s2twp`)
- HONG_KONG (`s2hk`)

Android uses OpenccJava 1.4.2, a pure-Java OpenCC-compatible engine, to avoid a second JNI/C++ runtime and `libc++_shared.so` conflicts.

### Offset invariant

The shared Core, bookmarks, search hits and persisted progress remain in normalized source code-point offsets. Reader page state stores the original Core window. Compose converts only the displayed window and maps the visible converted length proportionally back to source code points before navigation. Search result contexts, chapter titles and TTS chunks are converted after their source offsets have already been resolved.

No full converted book file is created.

### Local override dictionary

Reader settings may contain up to 200 `source => target` phrase overrides. Overrides are protected before OpenCC conversion and restored afterward, so they have higher priority than the standard dictionary. They are included in the existing local settings backup and never uploaded.

## Performance and privacy constraints

- No Android INTERNET permission is added.
- No runtime analytics/ads/account dependency is added.
- OpenCC is used only on bounded UI/TTS strings; the 6,000-character reader window remains the main display work set.
- Smart Clean refinement streams the normalized document with bounded candidate memory.
- Future semantic inference must be local, candidate-only and separately performance-gated before enabling.
