# Security Policy

## Scope

Security support follows the currently released production version. Experimental/private branch artifacts are not a separate supported product line.

## Reporting

Report suspected vulnerabilities privately to the repository owner rather than opening a public issue. Include affected version/commit, reproduction conditions, impact and any relevant logs with secrets/user content removed.

## Sensitive material

Never commit or paste into issues/PRs/CI logs:

- Android or Harmony signing keys/passwords/profiles;
- store credentials, tokens or access keys;
- private TXT contents or exported user libraries;
- device identifiers or unpublished user data.

Signing material is local/release-infrastructure state and is excluded by repository policy.

## Product security posture

- the application is offline-first and has no network permission by design unless an explicit reviewed product change says otherwise;
- selected external TXT files are never modified; processing uses app-private copies;
- normalized files and derived clean views stay in app-private storage until the user explicitly exports them;
- invalid normalized UTF-8 is rejected by the shared core rather than silently reinterpreted;
- native handles/buffers have explicit lifetime rules documented in `docs/CORE_CONTRACT.md`.

## Dependencies and provenance

Avoid runtime third-party SDKs unless their necessity, license, privacy behavior and update policy are reviewed. Build/test dependencies must be pinned by the platform lock/wrapper mechanisms where available. Generated packages and third-party executable reference apps are not source assets.

## Release integrity

Release packages are built from a reviewed commit/tag with CI evidence. Store signing, package checksums/symbols and rollback artifacts live in release infrastructure, not the Git tree.
