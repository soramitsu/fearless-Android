#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DETECTOR="$SCRIPT_DIR/xcm-release-submissions-enabled.sh"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
TEST_COUNT=0

fail() {
  echo "[xcm-release-flag-test][error] $*" >&2
  exit 1
}

write_build() {
  mkdir -p "$TMP_DIR/root/feature-wallet-impl"
  cp "$SCRIPT_DIR/../feature-wallet-impl/build.gradle" "$TMP_DIR/root/feature-wallet-impl/build.gradle"
}

expect() {
  local label="$1"
  local expected="$2"
  TEST_COUNT=$((TEST_COUNT + 1))
  local status=0
  XCM_RELEASE_FLAG_ROOT="$TMP_DIR/root" bash "$DETECTOR" >/dev/null 2>&1 || status=$?
  [[ "$status" == "$expected" ]] || fail "$label: expected exit $expected, got $status"
}

edit() {
  perl -0pi -e "$1" "$TMP_DIR/root/feature-wallet-impl/build.gradle"
}

write_build
expect "the committed release build compiles XCM submissions off" 1

write_build
edit 's/(release \{.*?ENABLE_PRODUCTION_XCM_TRANSFERS"\s*,\s*)"false"/${1}"true"/s'
expect "a release flag set to true is on" 0

write_build
edit 's/(release \{.*?ENABLE_PRODUCTION_XCM_TRANSFERS"\s*,\s*)"false"/${1}releaseXcmTransfers/s'
expect "a release flag taken from a variable is treated as on" 0

write_build
edit 's/(release \{.*?)\n\s*buildConfigField "boolean", "ENABLE_PRODUCTION_XCM_TRANSFERS", "false"/$1/s'
expect "a missing release flag is treated as on" 0

write_build
edit 's/(release \{.*?)(buildConfigField "boolean", "ENABLE_PRODUCTION_XCM_TRANSFERS", "false")/$1$2\n            $2/s'
expect "a duplicated release flag is treated as on" 0

write_build
edit 's/(release \{.*?)(buildConfigField "boolean", "ENABLE_PRODUCTION_XCM_TRANSFERS", "false")/$1\/\/ $2\n            buildConfigField "boolean", "ENABLE_PRODUCTION_XCM_TRANSFERS", "true"/s'
expect "a commented-out false next to a true flag is on" 0

rm -rf "$TMP_DIR/root"
expect "a missing build file is treated as on" 0

echo "[xcm-release-flag-test] all tests passed ($TEST_COUNT scenarios)"
