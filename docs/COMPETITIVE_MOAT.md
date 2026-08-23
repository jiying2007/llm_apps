# Competitive Moat — TXT Doctor / Smart Clean 4 / Smart TOC / Local Automation

## Product boundary

Jingdu wins by going deeper on difficult Chinese long-form TXT, not by matching broad-format readers feature-for-feature.

The terminal product loop is:

```text
select TXT / folder
  -> first-readable bounded preview
  -> private immutable import + normalization/index
  -> TXT Doctor health diagnosis
  -> Smart TOC structure intelligence
  -> Smart Clean 4 explainable cleanup
  -> long-session reading / background TTS
  -> reusable local feedback, rules and batch automation
```

The source TXT is never modified or deleted. Book text is never uploaded. Android retains no INTERNET permission.

## P0 — immediate differentiated value

### TXT Doctor

TXT Doctor combines bounded text-integrity sampling with encoding, TOC and Smart Clean results. The report stores only scores/counts/metadata, never sampled book text.

### Smart TOC

Core source code-point offsets remain authoritative. Smart TOC augments Core chapter headings with carefully verified special Chinese headings and reports duplicates, nearby numeric gaps and suspicious titles. User hide/add corrections are metadata overlays; they do not rewrite source text or offsets.

### First-readable import

New single-book import reads at most 512 KiB and renders at most 12,000 code points before the full private copy/normalization/indexing completes. The bounded preview is disposable and is not treated as the canonical document revision.

### Professional TTS

Android can move read-aloud into an exported=false foreground mediaPlayback service with MediaSession controls. Lock-screen/headset controls operate on source offsets. Current reading-window highlight is presentation-only.

## P1 — retention and paid automation

### SAF folder library

Folder roots are selected explicitly through the Storage Access Framework. Jingdu persists read-only URI permission and uses documentId + size + lastModified signatures to skip unchanged TXT files. It requests no broad storage permission.

### Pro batch automation

Batch automation scans at most 100 library books, supports dry-run before apply, and only auto-applies precision-first safe candidates. KEEP/PROTECT and semantic BODY decisions block automatic deletion. Batch output may create local repair-rule metadata and immutable derived Clean revisions; source TXT is unchanged.

Reports contain book identifiers/names, scores and counts only and declare `containsBookText=false`.

### Verifiable privacy

The installed app can report whether INTERNET permission is absent and whether runtime analytics/ads/book-upload capabilities are present. Exported privacy audits contain configuration and counts only.

## P2 — Smart Clean 4 data/model moat

### Feedback memory

`KEEP`, `DELETE` and `PROTECT` decisions are stored as SHA-256 candidate fingerprints plus reason/decision metadata. The store does not persist candidate text. Book-specific decisions override generic learned deltas.

### Candidate-only tiny model

The semantic classifier accepts one bounded candidate string (maximum 512 characters). It never receives a file, ReaderController or whole document. The runtime model is a deterministic 64-bucket hashed character-bigram linear classifier with signed-int8-style weights and auditable structural features.

The wide middle score range is `UNCERTAIN`; chapter-like headings receive a strong BODY protection bias. The model may strengthen or protect a pre-filtered candidate but is not allowed to scan a complete book.

### Reproducible quality assets

- `quality/smartclean/train-v1.tsv` is the local labeled training corpus.
- `quality/smartclean/eval-v1.tsv` is a held-out hard-negative-heavy evaluation set.
- `scripts/train-smartclean-model.py --verify-source ...` proves runtime weights are reproducible from the training corpus.
- `scripts/verify-smartclean-model.py` enforces zero held-out BODY false positives among auto-AD decisions, auto-AD precision >= 99.5%, minimum non-trivial AD recall, chapter-heading protection and candidate-only source constraints.

Precision is deliberately more important than recall: missing one ad is annoying; deleting prose is unacceptable.

## Long-form invariants

- Source/Core offsets remain the only persisted reading coordinate system.
- No whole-document managed copy is introduced.
- No arbitrary whole-book regex is introduced.
- No generic EPUB/PDF/MOBI expansion is introduced.
- Smart Clean remains explainable and reversible.
- Folder sync, batch automation, TTS and model inference remain local.
- Real-device 20/100/200 MiB SLO evidence remains a release/device qualification gate and is not fabricated by hosted CI.
