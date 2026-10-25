#!/usr/bin/env bash
set -euo pipefail

# Historical source of the unchanged checked-in binaries; rebuilds use app-owned source.
EXPECTED_SOURCE_COMMIT="7500809f33243ee47ecb2ec8563fc284ac4de0d6"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE="$ROOT/runtime-utils/native/sr25519"
OUTPUT="$ROOT/build/native/sr25519"

bash "$ROOT/scripts/verify-android-runtime-sources.sh"
command -v cargo-ndk >/dev/null || { echo "cargo-ndk is required" >&2; exit 1; }
[[ -n "${ANDROID_NDK_HOME:-}" && -d "$ANDROID_NDK_HOME" ]] || { echo "Set ANDROID_NDK_HOME to the reviewed Android NDK" >&2; exit 1; }
mkdir -p "$OUTPUT"
cd "$SOURCE"
# Retain 16 KB page alignment from the imported .cargo/config.toml.
CARGO_TARGET_DIR="$OUTPUT/target" cargo ndk -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 \
  --platform 21 -o "$OUTPUT/jniLibs" build --release --locked
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  [[ -f "$OUTPUT/jniLibs/$abi/libsr25519java.so" && ! -L "$OUTPUT/jniLibs/$abi/libsr25519java.so" ]] || { echo "Missing built library for $abi" >&2; exit 1; }
done
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  cp "$OUTPUT/jniLibs/$abi/libsr25519java.so" "$ROOT/app/src/main/jniLibs/$abi/libsr25519java.so"
done
printf '%s\n' "Rebuilt app-owned sr25519 source. Update binary provenance and qualify all four ABIs before release."
shasum -a 256 "$ROOT"/app/src/main/jniLibs/*/libsr25519java.so
