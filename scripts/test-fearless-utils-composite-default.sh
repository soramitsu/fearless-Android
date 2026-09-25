#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
GRADLEW="$ROOT_DIR/gradlew"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-utils-composite-default.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

fail() {
  echo "[fearless-utils-composite-default-test][error] $*" >&2
  exit 1
}

run_composite() {
  env \
    -u FEARLESS_UTILS_LIBRARY_ONLY \
    -u FEARLESS_UTILS_SKIP_RUST_PLUGIN \
    CI=true \
    SKIP_AUTO_VERSION_BUMP=true \
    FORCE_LOCAL_UTILS=true \
    USE_REMOTE_UTILS=false \
    "$GRADLEW" :runtime:assembleDebugAndroidTest --dry-run --no-daemon --console=plain
}

run_standalone_override() {
  env \
    FEARLESS_UTILS_LIBRARY_ONLY=false \
    FEARLESS_UTILS_SKIP_RUST_PLUGIN=true \
    CI=true \
    SKIP_AUTO_VERSION_BUMP=true \
    FORCE_LOCAL_UTILS=true \
    USE_REMOTE_UTILS=false \
    "$GRADLEW" :runtime:assembleDebugAndroidTest --dry-run --no-daemon --console=plain
}

composite_log="$tmp_dir/composite.log"
run_composite >"$composite_log" 2>&1 ||
  fail "default composite mode did not configure the runtime instrumentation build"
grep -Fq '> Configure project :fearless-utils-Android:fearless-utils' "$composite_log" ||
  fail "default composite mode did not configure the substituted fearless-utils library"
if grep -Fq '> Configure project :fearless-utils-Android:app' "$composite_log"; then
  fail "default composite mode configured the unconsumed fearless-utils sample app"
fi

override_log="$tmp_dir/standalone-override.log"
run_standalone_override >"$override_log" 2>&1 || true
grep -Fq '> Configure project :fearless-utils-Android:app' "$override_log" ||
  fail "an explicit false override no longer restores the standalone sample app"

echo "[fearless-utils-composite-default-test] passed"
