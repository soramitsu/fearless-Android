#!/usr/bin/env bash
# Builds a host copy of the sr25519 library that Android 4.3.1 and earlier
# shipped (schnorrkel 0.9.1, from Git history) and runs
# Sr25519LegacyCompatibilityTest, which loads it next to the current library
# and requires identical keys, derivations and cross-verifying signatures.
#
# Usage: scripts/verify-sr25519-legacy-compatibility.sh [--build-only] [legacy-revision]
#   --build-only     build the legacy host library and print its path; run no tests
#   GRADLE_LAUNCHER  optional command prefix for ./gradlew (for example a build-slot helper)
#   LEGACY_TOOLCHAIN Rust toolchain for the legacy crate (default 1.89.0, the one that built 4.3.1)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd -P)"
BUILD_ONLY=false
if [[ "${1:-}" == "--build-only" ]]; then
  BUILD_ONLY=true
  shift
fi
# release/android-4.3.0 head; its crate is the one the 4.3.1 binaries were built from.
REVISION="${1:-c9d18c43555613d8e90cb8bab82d95212aa71a64}"
TOOLCHAIN="${LEGACY_TOOLCHAIN:-1.89.0}"
WORK="$ROOT/build/native/sr25519/legacy"
CRATE_PATH="runtime-utils/native/sr25519"

fail() {
  echo "[sr25519-legacy][error] $*" >&2
  exit 1
}

expected_sha256() {
  case "$1" in
    Cargo.toml) echo "0521af5004cb2e7a53bf33c9d0502f7c0a8877cc5ca98fa53100b036b1442e04" ;;
    Cargo.lock) echo "c125ec4753fa0b3e8fda33699214f795ea39135a91ae580dc253de5716e6bf53" ;;
    .cargo/config.toml) echo "d043bb1229e635510968215d259c3e3b43da6511dbe499757094c3d0bd229e5e" ;;
    src/lib.rs) echo "3b9447faa07961ddc7cc1f97f723f719a0fbc28ff6db6f572831f71e232740e2" ;;
    *) return 1 ;;
  esac
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

export PATH="$PATH:${CARGO_HOME:-$HOME/.cargo}/bin"
command -v cargo >/dev/null || fail "cargo is required (https://rustup.rs)"
rustup run "$TOOLCHAIN" rustc --version >/dev/null 2>&1 ||
  fail "Rust $TOOLCHAIN is not installed; run: rustup toolchain install $TOOLCHAIN --profile minimal"

rm -rf "$WORK"
mkdir -p "$WORK/source"
git -C "$ROOT" archive "$REVISION" "$CRATE_PATH" | tar -x -C "$WORK/source"
LEGACY="$WORK/source/$CRATE_PATH"
for file in Cargo.toml Cargo.lock .cargo/config.toml src/lib.rs; do
  [[ -f "$LEGACY/$file" && ! -L "$LEGACY/$file" ]] || fail "legacy $file is missing at $REVISION"
  [[ "$(sha256_file "$LEGACY/$file")" == "$(expected_sha256 "$file")" ]] ||
    fail "legacy $file at $REVISION is not the reviewed 4.3.1 source"
done
[[ "$(head -n 1 "$LEGACY/src/lib.rs")" == '#![cfg(target_os = "android")]' ]] ||
  fail "unexpected first line in the legacy lib.rs"
# The legacy crate compiles only for Android; drop that gate for the host build.
tail -n +2 "$LEGACY/src/lib.rs" > "$LEGACY/src/lib.rs.host"
mv "$LEGACY/src/lib.rs.host" "$LEGACY/src/lib.rs"

(
  cd "$LEGACY"
  env -u RUSTFLAGS -u CARGO_ENCODED_RUSTFLAGS -u RUSTUP_TOOLCHAIN \
    CARGO_TARGET_DIR="$WORK/target" \
    cargo "+$TOOLCHAIN" build --release --locked --quiet
)
case "$(uname -s)" in
  Darwin) library_name="libsr25519java.dylib" ;;
  Linux) library_name="libsr25519java.so" ;;
  *) fail "unsupported host $(uname -s)" ;;
esac
mkdir -p "$WORK/host"
cp "$WORK/target/release/$library_name" "$WORK/host/$library_name"
echo "[sr25519-legacy] legacy host library: $WORK/host/$library_name"
[[ "$BUILD_ONLY" == false ]] || exit 0

cd "$ROOT"
# shellcheck disable=SC2086 # GRADLE_LAUNCHER is an intentional command prefix.
${GRADLE_LAUNCHER:-} ./gradlew :runtime-utils:testDebugUnitTest \
  --tests 'jp.co.soramitsu.fearless_utils.encrypt.Sr25519LegacyCompatibilityTest' \
  -Psr25519.legacyLibrary="$WORK/host/$library_name" \
  --console=plain
