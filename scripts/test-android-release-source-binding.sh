#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(mktemp -d "$ROOT_DIR/build/test-tmp/android-release-source-binding.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-release-source-binding-test][error] $*" >&2
  exit 1
}

positive_count=0
negative_count=0
source_error="RELEASE_COMMIT is required whenever the resolved task graph can produce, sign, install, or publish a release artifact."

run_failure() {
  local name="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/$name.log"

  if "$@" > "$output" 2>&1; then
    fail "$name unexpectedly succeeded"
  fi
  grep -Fq "$expected" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$name omitted its fail-closed diagnostic"
  }
  negative_count=$((negative_count + 1))
}

cd "$ROOT_DIR"

env -u RELEASE_COMMIT \
  ./gradlew :app:help --no-daemon --console=plain >/dev/null
positive_count=$((positive_count + 1))

for task in \
  :app:bunRel \
  :app:signReleaseBundle \
  :app:packageReleaseBundle \
  :app:packageRelease \
  :app:packageReleaseUniversalApk \
  :app:assembleRelease \
  :app:bundle \
  :app:assemble \
  :app:build; do
  case_name="$(
    tr -cd '[:alnum:]' <<< "$task" |
      tr '[:upper:]' '[:lower:]'
  )"
  run_failure \
    "$case_name-without-source" \
    "$source_error" \
    env -u RELEASE_COMMIT \
      ./gradlew "$task" --dry-run --no-daemon --console=plain
done

run_failure \
  malformed-source \
  "RELEASE_COMMIT must be an exact lowercase 40-character Git commit." \
  env RELEASE_COMMIT=ABC \
    ./gradlew :app:help --no-daemon --console=plain

run_failure \
  valid-source-advances-to-signing \
  "CI_KEYSTORE_PATH must identify a regular, non-symlink release keystore." \
  env \
    RELEASE_COMMIT=1111111111111111111111111111111111111111 \
    CI_KEYSTORE_PATH="$tmp_dir/missing-upload-keystore.jks" \
    CI_KEYSTORE_PASS=test \
    CI_KEYSTORE_KEY_ALIAS=test \
    CI_KEYSTORE_KEY_PASS=test \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain
positive_count=$((positive_count + 1))
negative_count=$((negative_count - 1))

[[ "$positive_count" == "2" ]] ||
  fail "expected 2 positive cases; got $positive_count"
[[ "$negative_count" == "10" ]] ||
  fail "expected 10 negative cases; got $negative_count"

echo \
  "[android-release-source-binding-test] $positive_count positive + $negative_count negative/adversarial cases passed"
