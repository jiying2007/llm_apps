# Repository policy

This repository intentionally uses a hard-cut architecture. Do not reintroduce legacy compatibility paths, old prototype roots, committed build artifacts, extracted third-party application packages, or a second shared business core.

Changes to `core/native` must update both platform bridges when the ABI changes and must keep host tests green.
