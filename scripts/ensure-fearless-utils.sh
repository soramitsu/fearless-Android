#!/usr/bin/env bash
set -euo pipefail

EXPECTED_COMMIT="${FEARLESS_UTILS_COMMIT:-7500809f33243ee47ecb2ec8563fc284ac4de0d6}"
ALLOW_DRIFT="${ALLOW_FEARLESS_UTILS_DRIFT:-false}"

repo_root="$(cd "$(dirname "$0")/.." && pwd)"
temporary_dir=""

cleanup() {
  [[ -z "$temporary_dir" ]] || rm -rf "$temporary_dir"
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

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
  [[ ! -L "$patch_file" && -f "$patch_file" && -s "$patch_file" ]] ||
    fail "library-only overlay patch is missing or unsafe at $patch_file"

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

is_generated_output_path() {
  case "$1" in
    .gradle/*|.kotlin/*|build/*|*/build/*|target/*|*/target/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

validate_ignored_files() {
  local ignored_path
  while IFS= read -r -d '' ignored_path; do
    is_generated_output_path "$ignored_path" ||
      fail "ignored file outside approved generated-output directories: $ignored_path"
  done < <(
    git -C "$utils_path" \
      ls-files --others --ignored --exclude-standard -z
  )
}

expected_effective_tree() {
  local index_file="$temporary_dir/expected.index"
  rm -f "$index_file"
  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" read-tree "$actual_commit^{tree}"

  if [[ "${FEARLESS_UTILS_LIBRARY_ONLY:-false}" == "true" ]]; then
    GIT_INDEX_FILE="$index_file" \
      git -C "$utils_path" apply \
        --cached \
        "$repo_root/scripts/fearless-utils-library-only.patch"
  fi

  GIT_INDEX_FILE="$index_file" git -C "$utils_path" write-tree
}

actual_effective_tree() {
  local index_file="$temporary_dir/actual.index"
  rm -f "$index_file"
  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" read-tree "$actual_commit^{tree}"
  GIT_INDEX_FILE="$index_file" \
    git -C "$utils_path" add -A -- \
      . \
      ':(exclude,glob).gradle' \
      ':(exclude,glob).gradle/**' \
      ':(exclude,glob).kotlin' \
      ':(exclude,glob).kotlin/**' \
      ':(exclude,glob)build' \
      ':(exclude,glob)build/**' \
      ':(exclude,glob)**/build' \
      ':(exclude,glob)**/build/**' \
      ':(exclude,glob)target' \
      ':(exclude,glob)target/**' \
      ':(exclude,glob)**/target' \
      ':(exclude,glob)**/target/**'
  GIT_INDEX_FILE="$index_file" git -C "$utils_path" write-tree
}

[[ ! -L "$utils_path" && -d "$utils_path/.git" ]] ||
  fail "fearless-utils-Android checkout not found or unsafe at $utils_path. Clone https://github.com/soramitsu/fearless-utils-Android.git or set FEARLESS_UTILS_PATH."

actual_commit="$(git -C "$utils_path" rev-parse HEAD)"
[[ "$actual_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "fearless-utils-Android HEAD is malformed."
if [[ "$actual_commit" != "$EXPECTED_COMMIT" && "$ALLOW_DRIFT" != "true" ]]; then
  fail "fearless-utils-Android must be at $EXPECTED_COMMIT, found $actual_commit. Set ALLOW_FEARLESS_UTILS_DRIFT=true only for an intentional source bump."
fi

echo "[fearless-utils] Using $utils_path at $actual_commit"
temporary_dir="$(mktemp -d)"
chmod 700 "$temporary_dir"
apply_library_only_overlay
validate_ignored_files

expected_tree="$(expected_effective_tree)"
actual_tree="$(actual_effective_tree)"
if [[ "$actual_tree" != "$expected_tree" ]]; then
  git -C "$utils_path" status \
    --short \
    --untracked-files=all \
    --ignore-submodules=none >&2 || true
  fail "fearless-utils-Android effective source tree differs from the exact pinned commit plus approved library-only overlay (expected $expected_tree, got $actual_tree)."
fi

echo "[fearless-utils] Effective source tree $actual_tree"
