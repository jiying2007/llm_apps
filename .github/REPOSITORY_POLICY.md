# Repository Policy

This repository intentionally uses a hard-cut Android + HarmonyOS architecture with one C++ shared core.

Do not reintroduce:

- compatibility or migration implementations for the removed experimental product line;
- prototype production roots or a second Java/Kotlin/ArkTS document core;
- platform-specific search/chapter/repair/identity semantics that belong in `core/native`;
- committed build artifacts, signing material or extracted third-party application packages.

Shared ABI/data behavior changes must update both platform bridges, automated native tests and the corresponding SSOT documents in the same change.

## Production governance

Source CI/PR practice is necessary but is not sufficient once Android production rollout begins. Before the first Google Play production staged rollout, repository administration must enable platform-enforced protection for `main` (branch protection or an equivalent GitHub ruleset) with the required hosted checks defined by `docs/QUALITY_GATES.md`, prevent force-push/deletion, and require pull-request based changes except for explicitly documented release automation.

Release tags are historical provenance and must never be moved or deleted. New source releases are created as annotated tag objects whose message binds the exact fully-gated `main` commit to the checked-in source-manifest SHA-256. Repository tag rules should additionally block update/deletion of `v*` tags when GitHub administration is configured.

The source publisher is intentionally fail-closed: it may complete an interrupted Release for an existing tag only when that tag already resolves to the exact gated `main` SHA; it never rewrites an existing tag.

Repository-administration state is external release evidence. CI must not claim branch/ruleset protection is active merely because this policy exists; the production checklist in `docs/PRODUCTION_READINESS.md` requires the actual GitHub settings evidence.
