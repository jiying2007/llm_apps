#!/usr/bin/env python3
"""Publish immutable Jingdu source provenance from an already-gated main SHA.

This script is intentionally stdlib-only. It is invoked only by the tail job of the
main CI workflow after all required product gates succeed. New releases use annotated
tag objects whose message binds the exact gated commit to the checked-in manifest hash.
"""

from __future__ import annotations

import hashlib
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

TEMP_PREFIXES = ("feat/", "fix/", "chore/", "ci/", "refactor/", "docs/", "test/", "perf/", "tmp/")
RELEASE_PREFIX = "release/source-v"


def fail(message: str) -> "NoReturn":
    raise SystemExit(message)


def required_env(name: str) -> str:
    value = os.environ.get(name, "").strip()
    if not value:
        fail(f"missing required environment variable: {name}")
    return value


API = required_env("GH_API_URL")
REPO = required_env("GH_REPOSITORY")
TOKEN = required_env("GH_TOKEN")
MAIN_SHA = required_env("MAIN_SHA")


def request(path: str, method: str = "GET", payload: object | None = None, allowed: tuple[int, ...] = ()):
    data = None if payload is None else json.dumps(payload).encode("utf-8")
    req = urllib.request.Request(
        f"{API}/repos/{REPO}{path}",
        data=data,
        method=method,
        headers={
            "Authorization": f"Bearer {TOKEN}",
            "Accept": "application/vnd.github+json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Content-Type": "application/json",
        },
    )
    try:
        with urllib.request.urlopen(req) as response:
            body = response.read()
            return response.status, json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        if error.code in allowed:
            return error.code, None
        detail = error.read().decode("utf-8", errors="replace")
        fail(f"GitHub API {method} {path} failed: {error.code} {detail}")


def paged(path: str) -> list[dict]:
    output: list[dict] = []
    separator = "&" if "?" in path else "?"
    for page in range(1, 11):
        _, values = request(f"{path}{separator}per_page=100&page={page}")
        batch = values or []
        output.extend(batch)
        if len(batch) < 100:
            break
    return output


def declared_version() -> str:
    app = Path("apps/android/app/build.gradle").read_text(encoding="utf-8")
    root = Path("apps/android/build.gradle").read_text(encoding="utf-8")
    app_match = re.search(r'versionNameProperty\.getOrElse\("([^\"]+)"\)', app)
    root_match = re.search(r'jingduVersionName"\)\.getOrElse\("([^\"]+)"\)', root)
    if not app_match or not root_match:
        fail("Android source release version defaults are missing")
    if app_match.group(1) != root_match.group(1):
        fail(f"Android version drift: app={app_match.group(1)} staging={root_match.group(1)}")
    version = app_match.group(1)
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        fail(f"Android source version is not SemVer: {version}")
    return version


def verify_manifest(tag: str) -> Path | None:
    manifest = Path(f"releases/source/{tag}.md")
    if not manifest.is_file():
        return None
    text = manifest.read_text(encoding="utf-8")
    required = (f"version: {tag}", "kind: source-release", "google_play_production: false")
    missing = [item for item in required if item not in text]
    if missing:
        fail(f"source release manifest contract missing: {missing}")
    return manifest


def resolve_object_sha(kind: str, sha: str) -> str:
    current_kind, current_sha = kind, sha
    for _ in range(4):
        if current_kind != "tag":
            return current_sha
        _, tag = request(f"/git/tags/{current_sha}")
        obj = tag.get("object") or {}
        current_kind = obj.get("type", "")
        current_sha = obj.get("sha", "")
        if not current_sha:
            fail("annotated tag object has no target sha")
    fail("tag indirection exceeds supported depth")


def read_tag(tag: str):
    encoded = urllib.parse.quote(tag, safe="")
    status, ref = request(f"/git/ref/tags/{encoded}", allowed=(404,))
    if status == 404:
        return None
    obj = ref.get("object") or {}
    sha = resolve_object_sha(obj.get("type", ""), obj.get("sha", ""))
    return ref, sha


def manifest_sha256(manifest: Path) -> str:
    return hashlib.sha256(manifest.read_bytes()).hexdigest()


def create_annotated_tag(tag: str, manifest: Path) -> None:
    manifest_hash = manifest_sha256(manifest)
    _, tag_object = request(
        "/git/tags",
        method="POST",
        payload={
            "tag": tag,
            "message": (
                f"Jingdu {tag} source provenance\n\n"
                f"gated-main-sha: {MAIN_SHA}\n"
                f"manifest: {manifest.as_posix()}\n"
                f"manifest-sha256: {manifest_hash}\n"
                "google-play-production: false"
            ),
            "object": MAIN_SHA,
            "type": "commit",
        },
    )
    tag_object_sha = (tag_object or {}).get("sha", "")
    if not tag_object_sha:
        fail("GitHub did not return annotated tag object sha")
    request(
        "/git/refs",
        method="POST",
        payload={"ref": f"refs/tags/{tag}", "sha": tag_object_sha},
    )
    final = read_tag(tag)
    if final is None or final[1] != MAIN_SHA:
        fail(f"annotated tag {tag} does not resolve to gated main {MAIN_SHA}")


def publish(tag: str, manifest: Path) -> None:
    existing = read_tag(tag)
    encoded = urllib.parse.quote(tag, safe="")
    release_status, release = request(f"/releases/tags/{encoded}", allowed=(404,))

    # A completed tag + Release is immutable historical provenance by publisher contract. Later
    # main commits may retain the same version until the next bump; this script never moves a tag.
    if existing is not None and release_status != 404:
        _, target_sha = existing
        print(
            f"source release already published at immutable {tag} -> {target_sha}: "
            f"{release.get('html_url')}"
        )
        return

    # An orphan tag can be completed only when it already resolves to this exact gated main SHA.
    if existing is not None:
        _, target_sha = existing
        if target_sha != MAIN_SHA:
            fail(f"orphan immutable tag {tag} points to {target_sha}, expected gated main {MAIN_SHA}")
        _, release = request(
            "/releases",
            method="POST",
            payload={
                "tag_name": tag,
                "target_commitish": target_sha,
                "name": f"Jingdu {tag}",
                "body": release_body(tag, manifest),
                "draft": False,
                "prerelease": False,
                "make_latest": "true",
            },
        )
        print(f"completed release for existing immutable tag: {release.get('html_url')}")
        return

    if release_status != 404:
        fail(f"release exists for missing tag {tag}")

    create_annotated_tag(tag, manifest)
    _, release = request(
        "/releases",
        method="POST",
        payload={
            "tag_name": tag,
            "target_commitish": MAIN_SHA,
            "name": f"Jingdu {tag}",
            "body": release_body(tag, manifest),
            "draft": False,
            "prerelease": False,
            "make_latest": "true",
            "generate_release_notes": True,
        },
    )
    final = read_tag(tag)
    if final is None or final[1] != MAIN_SHA:
        fail(f"created release tag {tag} does not resolve to gated main {MAIN_SHA}")
    print(
        f"created annotated source release: {release.get('html_url')} "
        f"(manifest sha256 {manifest_sha256(manifest)})"
    )


def release_body(tag: str, manifest: Path) -> str:
    return (
        f"Jingdu {tag} source release.\n\n"
        f"Immutable source manifest: `{manifest.as_posix()}`.\n"
        f"Manifest SHA-256: `{manifest_sha256(manifest)}`.\n\n"
        "This records source provenance only. It is not evidence of a signed APK/AAB or "
        "Google Play production rollout. Android production still requires the retained upload key, "
        "Play Console product/listing/license-test evidence and staged rollout documented in `docs/RELEASE.md`."
    )


def cleanup_closed_temporary_branches() -> None:
    open_pulls = paged("/pulls?state=open")
    open_heads = {
        pr.get("head", {}).get("ref")
        for pr in open_pulls
        if pr.get("head", {}).get("repo", {}).get("full_name") == REPO
    }

    closed_pulls = paged("/pulls?state=closed&base=main&sort=updated&direction=desc")
    candidates: list[str] = []
    for pr in closed_pulls:
        head = pr.get("head") or {}
        head_repo = head.get("repo") or {}
        ref = head.get("ref")
        if head_repo.get("full_name") != REPO or not ref or ref == "main" or ref in open_heads:
            continue
        if ref.startswith(RELEASE_PREFIX) or ref.startswith(TEMP_PREFIXES):
            if ref not in candidates:
                candidates.append(ref)

    for ref in candidates:
        encoded = urllib.parse.quote(ref, safe="/")
        status, _ = request(f"/git/refs/heads/{encoded}", method="DELETE", allowed=(404, 422))
        if status in (204, 404, 422):
            print(f"pruned closed temporary branch: {ref} ({status})")


def main() -> int:
    version = declared_version()
    tag = f"v{version}"
    manifest = verify_manifest(tag)
    if manifest is None:
        print(f"no source manifest for {tag}; publication skipped")
        cleanup_closed_temporary_branches()
        return 0

    publish(tag, manifest)
    cleanup_closed_temporary_branches()
    return 0


if __name__ == "__main__":
    sys.exit(main())
