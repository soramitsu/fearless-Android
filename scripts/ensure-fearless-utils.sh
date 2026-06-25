#!/usr/bin/env bash
set -euo pipefail

EXPECTED_COMMIT="${FEARLESS_UTILS_COMMIT:-7500809f33243ee47ecb2ec8563fc284ac4de0d6}"
ALLOW_DRIFT="${ALLOW_FEARLESS_UTILS_DRIFT:-false}"

repo_root="$(cd "$(dirname "$0")/.." && pwd)"

if [[ -n "${FEARLESS_UTILS_PATH:-}" ]]; then
  utils_path="$FEARLESS_UTILS_PATH"
elif [[ -d "$repo_root/fearless-utils-Android/.git" ]]; then
  utils_path="$repo_root/fearless-utils-Android"
else
  utils_path="$(cd "$repo_root/.." && pwd)/fearless-utils-Android"
fi

fail() {
  echo "[fearless-utils][error] $*" >&2
  exit 1
}

apply_library_only_overlay() {
  [[ "${FEARLESS_UTILS_LIBRARY_ONLY:-false}" == "true" ]] || return 0

  local patch_file="$repo_root/scripts/fearless-utils-library-only.patch"
  [[ -f "$patch_file" ]] || fail "library-only overlay patch not found at $patch_file"

  if git -C "$utils_path" apply --reverse --check "$patch_file" >/dev/null 2>&1; then
    echo "[fearless-utils] Library-only overlay already applied"
    return 0
  fi

  if ! git -C "$utils_path" apply --check "$patch_file"; then
    fail "Unable to apply library-only overlay to $utils_path. Reset fearless-utils-Android to $EXPECTED_COMMIT or update $patch_file for the new pinned source."
  fi

  git -C "$utils_path" apply "$patch_file"
  echo "[fearless-utils] Applied library-only overlay for wallet CI"
}

[[ -d "$utils_path/.git" ]] || fail "fearless-utils-Android checkout not found at $utils_path. Clone https://github.com/soramitsu/fearless-utils-Android.git or set FEARLESS_UTILS_PATH."

actual_commit="$(git -C "$utils_path" rev-parse HEAD)"
if [[ "$actual_commit" != "$EXPECTED_COMMIT" && "$ALLOW_DRIFT" != "true" ]]; then
  fail "fearless-utils-Android must be at $EXPECTED_COMMIT, found $actual_commit. Set ALLOW_FEARLESS_UTILS_DRIFT=true only for an intentional source bump."
fi

echo "[fearless-utils] Using $utils_path at $actual_commit"
apply_library_only_overlay
