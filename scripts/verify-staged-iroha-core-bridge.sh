#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
MATERIALIZER="$ROOT_DIR/scripts/materialize-iroha-core-jvm.sh"
MATERIALIZER_TEST="$ROOT_DIR/scripts/test-iroha-core-jvm-materializer.sh"
HASH_VECTOR="$ROOT_DIR/iroha-sdk-bridge/src/test/resources/iroha-compact-hash-vector.properties"
HASH_VECTOR_VERIFIER="$ROOT_DIR/scripts/verify-iroha-compact-hash-vector.py"
NATIVE_SOURCE_TEST="$ROOT_DIR/scripts/test-android-runtime-source-pins.py"
NATIVE_SOURCE_GUARD="$ROOT_DIR/scripts/verify-android-runtime-sources.py"

fail() {
  echo "[iroha-core-bridge][error] $*" >&2
  exit 1
}

[[ -x "$ROOT_DIR/gradlew" ]] || fail "Gradle wrapper is missing or is not executable"
[[ -f "$MATERIALIZER" && ! -L "$MATERIALIZER" ]] || fail "materializer is missing or is a symlink"
[[ -f "$MATERIALIZER_TEST" && ! -L "$MATERIALIZER_TEST" ]] ||
  fail "materializer adversarial test is missing or is a symlink"
[[ -f "$HASH_VECTOR" && ! -L "$HASH_VECTOR" ]] ||
  fail "compact hash vector is missing or is a symlink"
[[ -f "$HASH_VECTOR_VERIFIER" && ! -L "$HASH_VECTOR_VERIFIER" ]] ||
  fail "compact hash vector verifier is missing or is a symlink"
[[ -f "$NATIVE_SOURCE_TEST" && ! -L "$NATIVE_SOURCE_TEST" ]] ||
  fail "native source ownership test is missing or is a symlink"
[[ -f "$NATIVE_SOURCE_GUARD" && ! -L "$NATIVE_SOURCE_GUARD" ]] ||
  fail "native source ownership guard is missing or is a symlink"
command -v python3 >/dev/null 2>&1 || fail "python3 is required for independent hash-vector verification"

source_args=("$@")
if [[ "${#source_args[@]}" -eq 0 ]]; then
  source_args=(--download)
fi

case "${source_args[0]}" in
  --download)
    [[ "${#source_args[@]}" -eq 1 ]] || fail "--download accepts no additional arguments"
    ;;
  --archive|--release-dir)
    [[ "${#source_args[@]}" -eq 2 ]] || fail "${source_args[0]} requires exactly one path"
    ;;
  *) fail "first argument must be --download, --archive, or --release-dir" ;;
esac

cd "$ROOT_DIR"

echo "[iroha-core-bridge] verifying the independent Rust/native compact-hash diagnostic vector"
python3 "$HASH_VECTOR_VERIFIER" --self-test "$HASH_VECTOR"

echo "[iroha-core-bridge] exercising bounded materializer adversarial suite"
bash "$MATERIALIZER_TEST"

echo "[iroha-core-bridge] materializing the checksum-pinned core-jvm coordinate"
bash "$MATERIALIZER" "${source_args[@]}"

echo "[iroha-core-bridge] verifying app-owned native runtime sources"
python3 "$NATIVE_SOURCE_TEST"
python3 "$NATIVE_SOURCE_GUARD"

gradle_tasks=(
  :iroha-sdk-bridge:check
  :iroha-sdk-bridge-kotlin-smoke:check
)

echo "[iroha-core-bridge] resolving, hashing, compiling, and testing the staged modules"
# The staging-boundary task inspects every project's configuration graph. Keep
# project tasks serial so another task cannot add a configuration mid-audit.
./gradlew "${gradle_tasks[@]}" --no-parallel --no-daemon --console=plain --stacktrace

echo "[iroha-core-bridge] repeating the staged module checks offline"
./gradlew "${gradle_tasks[@]}" --offline --no-parallel --no-daemon --console=plain --stacktrace

echo "[iroha-core-bridge] staged, fail-closed bridge verification passed"
