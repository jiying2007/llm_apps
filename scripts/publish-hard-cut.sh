#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'EOF'
Usage: HARD_CUT_CONFIRM=REWRITE_MAIN ./scripts/publish-hard-cut.sh <40-hex-candidate-sha>

Destructive final publication tool. Run only after the Harmony HAP, both device
matrices, cross-platform parity and store/signing gates for the exact candidate
SHA are recorded as passed.
EOF
  exit 2
}

[[ $# -eq 1 ]] || usage
candidate="$1"
[[ "$candidate" =~ ^[0-9a-f]{40}$ ]] || usage
[[ "${HARD_CUT_CONFIRM:-}" == "REWRITE_MAIN" ]] || {
  echo 'Refusing destructive history cut: set HARD_CUT_CONFIRM=REWRITE_MAIN.' >&2
  exit 2
}

repo_root="$(git rev-parse --show-toplevel)"
cd "$repo_root"

remote="${HARD_CUT_REMOTE:-origin}"
migration_branch="${HARD_CUT_MIGRATION_BRANCH:-feat/android-harmony-terminal}"
commit_message="${HARD_CUT_COMMIT_MESSAGE:-release: establish Android Harmony terminal root}"

git diff --quiet
git diff --cached --quiet
git rev-parse --verify "${candidate}^{commit}" >/dev/null

# The cut is intentionally defined only for a two-branch repository. Refuse to
# silently strand another branch/tag that could keep the experimental lineage reachable.
mapfile -t remote_heads < <(git ls-remote --heads "$remote" | awk '{sub("refs/heads/", "", $2); print $2}' | sort)
expected_heads="$(printf '%s\n' main "$migration_branch" | sort)"
actual_heads="$(printf '%s\n' "${remote_heads[@]}")"
[[ "$actual_heads" == "$expected_heads" ]] || {
  echo 'Unexpected remote branches exist; remove or account for them before the hard cut:' >&2
  printf '  %s\n' "${remote_heads[@]}" >&2
  exit 1
}

if git ls-remote --refs --tags "$remote" | grep -q .; then
  echo 'Existing remote tags would keep old history reachable. Remove/archive them before the hard cut.' >&2
  git ls-remote --refs --tags "$remote" >&2
  exit 1
fi

git fetch --prune "$remote" \
  "+refs/heads/main:refs/remotes/${remote}/main" \
  "+refs/heads/${migration_branch}:refs/remotes/${remote}/${migration_branch}"
remote_main="$(git rev-parse "refs/remotes/${remote}/main")"
remote_candidate="$(git rev-parse "refs/remotes/${remote}/${migration_branch}")"
[[ "$remote_candidate" == "$candidate" ]] || {
  echo "Candidate $candidate is not the current ${remote}/${migration_branch} ($remote_candidate)." >&2
  exit 1
}

candidate_tree="$(git rev-parse "${candidate}^{tree}")"
root_commit="$(printf '%s\n' "$commit_message" | git commit-tree "$candidate_tree")"
root_tree="$(git rev-parse "${root_commit}^{tree}")"
[[ "$root_tree" == "$candidate_tree" ]] || {
  echo 'Root commit tree differs from the verified candidate tree.' >&2
  exit 1
}

workdir="$(mktemp -d "${TMPDIR:-/tmp}/jingdu-hard-cut.XXXXXX")"
cleanup() {
  git worktree remove --force "$workdir" >/dev/null 2>&1 || true
  rm -rf "$workdir"
}
trap cleanup EXIT

git worktree add --detach "$workdir" "$root_commit" >/dev/null
(
  cd "$workdir"
  ./scripts/check-native.sh
  ./scripts/verify-terminal.sh
  cd apps/android
  ./gradlew --no-daemon --warning-mode all androidCheck
)

# Protect against any concurrent change to main after the fetch above.
git push "$remote" "${root_commit}:refs/heads/main" \
  --force-with-lease="refs/heads/main:${remote_main}"

# The migration branch is the last ordinary ref to the experimental lineage.
git push "$remote" --delete "$migration_branch"

mapfile -t final_heads < <(git ls-remote --heads "$remote" | awk '{sub("refs/heads/", "", $2); print $2}' | sort)
[[ "$(printf '%s\n' "${final_heads[@]}")" == 'main' ]] || {
  echo 'Unexpected branches remain after hard cut.' >&2
  printf '  %s\n' "${final_heads[@]}" >&2
  exit 1
}

published_main="$(git ls-remote --heads "$remote" refs/heads/main | awk '{print $1}')"
[[ "$published_main" == "$root_commit" ]] || {
  echo 'Remote main does not point to the generated terminal root.' >&2
  exit 1
}

printf 'Published terminal root commit: %s\n' "$root_commit"
printf 'Published tree SHA: %s\n' "$root_tree"
printf 'Previous main: %s\n' "$remote_main"
printf '%s\n' 'Re-clone the repository, verify reachable refs, then create the release tag from main.'
