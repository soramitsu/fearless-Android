#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(
  cd "${ANDROID_RELEASE_SOURCE_ROOT:-"$(dirname "$0")/.."}"
  pwd
)"

fail() {
  echo "[android-release-source-tree][error] $*" >&2
  exit 1
}

if [[ "${CI:-false}" == "true" && -n "${ANDROID_RELEASE_SOURCE_ROOT:-}" ]]; then
  fail "ANDROID_RELEASE_SOURCE_ROOT cannot override the checkout in CI."
fi

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/verify-android-release-source-tree.sh \
    <expected-commit> <expected-tree> [--allow-fearless-utils]

Requires the checkout to remain at the exact commit/tree with no tracked,
staged, or source-like untracked/ignored drift. Only generated Gradle, Kotlin,
and build output directories are ignored. The optional nested
fearless-utils-Android checkout must be independently verified by
ensure-fearless-utils.sh.
USAGE
}

is_approved_generated_path() {
  case "$1" in
    .gradle|.gradle/*|*/.gradle|*/.gradle/*|\
    .kotlin|.kotlin/*|*/.kotlin|*/.kotlin/*|\
    build|build/*|*/build|*/build/*)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

[[ "$#" -eq 2 || "$#" -eq 3 ]] || {
  usage
  exit 2
}

expected_commit="$1"
expected_tree="$2"
allow_fearless_utils=false
if [[ "$#" -eq 3 ]]; then
  [[ "$3" == "--allow-fearless-utils" ]] || {
    usage
    exit 2
  }
  allow_fearless_utils=true
fi

[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "Expected commit must be an exact lowercase 40-character Git object."
[[ "$expected_tree" =~ ^[0-9a-f]{40}$ ]] ||
  fail "Expected tree must be an exact lowercase 40-character Git object."
[[ ! -L "$ROOT_DIR" ]] || fail "Repository root cannot be a symlink."
git -C "$ROOT_DIR" rev-parse --is-inside-work-tree >/dev/null 2>&1 ||
  fail "Repository root is not a Git working tree."

actual_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
actual_tree="$(git -C "$ROOT_DIR" rev-parse "HEAD^{tree}")"
[[ "$actual_commit" == "$expected_commit" ]] ||
  fail "Checkout commit changed: expected $expected_commit, got $actual_commit."
[[ "$actual_tree" == "$expected_tree" ]] ||
  fail "Checkout tree changed: expected $expected_tree, got $actual_tree."
git -C "$ROOT_DIR" diff --quiet ||
  fail "Tracked release source has working-tree changes."
git -C "$ROOT_DIR" diff --cached --quiet ||
  fail "Tracked release source has staged changes."

if [[ "$allow_fearless_utils" == "true" ]]; then
  utils_path="$ROOT_DIR/fearless-utils-Android"
  [[ ! -L "$utils_path" && -d "$utils_path/.git" ]] ||
    fail "Allowed fearless-utils-Android path is missing, unsafe, or not a regular checkout."
  utils_commit="$(git -C "$utils_path" rev-parse HEAD)"
  [[ "$utils_commit" =~ ^[0-9a-f]{40}$ ]] ||
    fail "Nested fearless-utils-Android checkout has a malformed HEAD."
elif [[ -e "$ROOT_DIR/fearless-utils-Android" ]]; then
  fail "Unexpected nested fearless-utils-Android checkout is present."
fi

status_file="$(mktemp)"
cleanup() {
  rm -f "$status_file"
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

git -C "$ROOT_DIR" status \
  --porcelain=v1 \
  -z \
  --untracked-files=all \
  --ignored=matching \
  --ignore-submodules=none >"$status_file"

while IFS= read -r -d '' status_record; do
  [[ "${#status_record}" -ge 4 ]] ||
    fail "Git returned a malformed source-tree status record."
  status="${status_record:0:2}"
  path="${status_record:3}"
  case "$status" in
    "!!")
      is_approved_generated_path "$path" ||
        fail "Ignored file is not an approved generated output: $(printf '%q' "$path")"
      ;;
    "??")
      if [[
        "$allow_fearless_utils" == "true" &&
        "$path" == "fearless-utils-Android/"
      ]]; then
        continue
      fi
      fail "Untracked release source is present: $(printf '%q' "$path")"
      ;;
    *)
      fail "Release checkout is not clean at $(printf '%q' "$path") (status $status)."
      ;;
  esac
done <"$status_file"

echo \
  "[android-release-source-tree] exact commit/tree and bounded generated-output allowlist verified"
