## What changed

<!-- Describe the product/architecture change and why it belongs in the terminal design. -->

## Contract impact

- [ ] No shared-core behavior is reimplemented in a platform shell.
- [ ] If the C ABI changed, `jd_abi_version`, both bridges, tests and `docs/CORE_CONTRACT.md` changed together.
- [ ] If persisted semantics changed, `docs/DATA_MODEL.md` was updated.
- [ ] Long-running work remains off the Android/Harmony UI thread.

## Verification

- [ ] `./scripts/check-native.sh`
- [ ] Android `./gradlew --no-daemon androidCheck`
- [ ] `./scripts/verify-terminal.sh`
- [ ] Harmony HAP build/device evidence when Harmony/shared ABI is touched
- [ ] Relevant 10/100/300 MiB or parity evidence when performance/data semantics are touched

## Repository hygiene

- [ ] No generated APK/AAB/HAP, signing key, credentials, extracted third-party app, compatibility tree or archived implementation added.
- [ ] Documentation states current facts only; no completed claim without evidence.
