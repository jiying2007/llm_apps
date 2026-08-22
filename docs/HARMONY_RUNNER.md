# HarmonyOS Runner Contract

The real HarmonyOS build gate runs on a self-hosted GitHub Actions runner labeled `self-hosted,harmonyos`. This is deliberate: the repository only accepts Huawei's official DevEco/Command Line Tools toolchain and does not download SDK/tool binaries from third-party mirrors.

## Supported toolchain

Use a current Huawei HarmonyOS Command Line Tools release that supports this project's SDK/model version. Command Line Tools includes `hvigorw`, `ohpm`, code-linter tooling and the HarmonyOS SDK. Obtain it from the Huawei Developer download center and verify the package checksum published there before installation.

The runner must provide:

- official HarmonyOS Command Line Tools / SDK;
- `hvigorw` executable;
- Node.js 22 or a newer version supported by that toolchain;
- Git and standard Unix shell utilities used by GitHub Actions;
- enough free disk for a clean native + ArkTS build and HAP/APP artifacts.

Do not cache or mirror Huawei SDK binaries inside this Git repository.

## Runner labels

Register the repository/organization runner with the additional label:

```text
harmonyos
```

The workflow intentionally selects:

```yaml
runs-on: [self-hosted, harmonyos]
```

A generic self-hosted runner without the HarmonyOS toolchain must not carry this label.

## Tool discovery

`scripts/check-harmony.sh` resolves Hvigor in this order:

1. `HARMONY_HVIGORW` — absolute path to the official `hvigorw` executable;
2. `hvigorw` on `PATH`;
3. `${DEVECO_TOOLS_HOME}/bin/hvigorw`.

Configure one of those routes in the runner service environment. No repository secret is required merely to build an unsigned/debug source gate.

Before adding the `harmonyos` label, validate interactively:

```bash
hvigorw --version
node --version
git --version
```

Then, from a clean checkout, run:

```bash
./scripts/check-harmony.sh
```

The command must exit zero and produce at least one `.hap` under `apps/harmony`.

## CI evidence

`.github/workflows/harmony-device.yml` is the canonical HAP source gate. It:

1. checks out the exact PR commit;
2. runs `scripts/check-harmony.sh`;
3. requires a generated HAP;
4. collects HAP/APP/native `.so` outputs;
5. uploads the artifacts under the commit SHA.

A queued workflow means no matching `self-hosted,harmonyos` runner is online. It is not build success and must never be reported as such.

## Device evidence

A successful HAP build is still not the device gate. Before release, install the built package on the HarmonyOS device matrix in `DEVICE_MATRIX.md` and record the required import/encoding, reader, search/chapter, repair/export, lifecycle, TTS and 10/100/300 MiB results.

## Upgrade rule

When upgrading DevEco/Command Line Tools or the HarmonyOS SDK:

- use only official Huawei packages and published integrity data;
- run the complete HAP workflow before accepting the upgrade;
- update `build-profile.json5`, Hvigor model settings and this document in the same PR when their contract changes;
- never add compatibility scripts for multiple historical toolchains. The repository tracks one supported terminal toolchain contract at a time.
