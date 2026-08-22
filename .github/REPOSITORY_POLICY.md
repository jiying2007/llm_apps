# Repository Policy

This repository intentionally uses a hard-cut Android + HarmonyOS architecture with one C++ shared core.

Do not reintroduce:

- compatibility or migration implementations for the removed experimental product line;
- prototype production roots or a second Java/Kotlin/ArkTS document core;
- platform-specific search/chapter/repair/identity semantics that belong in `core/native`;
- committed build artifacts, signing material or extracted third-party application packages.

Shared ABI/data behavior changes must update both platform bridges, automated native tests and the corresponding SSOT documents in the same change.

`main` branch protection is intentionally not required while development is single-owner; quality gates remain defined by CI/PR practice and `docs/QUALITY_GATES.md`.
