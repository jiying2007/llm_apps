# Jingdu Immutable GitHub Release Flow

Jingdu GitHub releases use GitHub Immutable Releases. New releases therefore follow a strict draft-first sequence:

1. Exact-main CI passes every product, functional, native-compatibility, and hosted-performance gate.
2. `publish-source-release.py` creates the annotated source tag and a **draft** GitHub Release.
3. The Android release tail job builds the stable-debug-key APK from that source tag and uploads the APK, `SHA256SUMS.txt`, and `SIGNING-CERT-SHA256.txt` while the release is still a draft.
4. The separate `Finalize Immutable Release` workflow runs only after the complete push-to-main CI concludes successfully. It verifies the draft tag resolves to that exact gated main SHA and verifies all three required assets are present.
5. Only then is the draft published. GitHub immutability locks the release assets and associated tag after publication.

Already-published versions are treated as immutable provenance. Later main commits at the same version may verify that release and its required assets, but never move the tag, replace assets, or republish the version.

The GitHub Android artifact remains the repository-stable debug-key-signed APK for the current release stage. Google Play production signing and rollout remain a separate future stage.
