#!/usr/bin/env bash
set -euo pipefail

EXPECTED_SOURCE_COMMIT="7500809f33243ee47ecb2ec8563fc284ac4de0d6"
UTILS_PATH="${FEARLESS_UTILS_ANDROID_PATH:-$(cd "$(dirname "$0")/../.." && pwd)/fearless-utils-Android}"

log() { echo "[build-sr25519] $*"; }
fail() {
  echo "[build-sr25519][error] $*" >&2
  exit 1
}

cd "$(dirname "$0")/.."

[[ -d "$UTILS_PATH/.git" ]] || fail "fearless-utils-Android checkout not found at $UTILS_PATH"

source_commit="$(git -C "$UTILS_PATH" rev-parse HEAD)"
if [[ "$source_commit" != "$EXPECTED_SOURCE_COMMIT" && "${ALLOW_SR25519_SOURCE_DRIFT:-}" != "true" ]]; then
  fail "fearless-utils-Android must be at $EXPECTED_SOURCE_COMMIT, found $source_commit. Set ALLOW_SR25519_SOURCE_DRIFT=true only for an intentional source bump."
fi

log "Building sr25519java from $UTILS_PATH@$source_commit"
(
  cd "$UTILS_PATH"
  ./gradlew :fearless-utils:cargoBuild --no-daemon --console=plain
)

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

find "$UTILS_PATH" -type f -name 'libsr25519java.so' -print > "$tmp_dir/libs.txt"

resolve_source() {
  local abi="$1"
  local pattern="$2"
  local source
  source="$(grep -E "$pattern" "$tmp_dir/libs.txt" | head -n 1 || true)"
  [[ -n "$source" ]] || fail "Built libsr25519java.so for $abi was not found."
  printf '%s' "$source"
}

copy_lib() {
  local abi="$1"
  local pattern="$2"
  local source
  source="$(resolve_source "$abi" "$pattern")"
  mkdir -p "app/src/main/jniLibs/$abi"
  cp "$source" "app/src/main/jniLibs/$abi/libsr25519java.so"
  log "Copied $abi from $source"
}

copy_lib "arm64-v8a" 'aarch64-linux-android|arm64-v8a'
copy_lib "armeabi-v7a" 'armv7-linux-androideabi|armeabi-v7a'
copy_lib "x86" 'i686-linux-android|/x86/'
copy_lib "x86_64" 'x86_64-linux-android|x86_64'

log "Updated checksums:"
if command -v sha256sum >/dev/null 2>&1; then
  sha256sum app/src/main/jniLibs/*/libsr25519java.so
else
  shasum -a 256 app/src/main/jniLibs/*/libsr25519java.so
fi
