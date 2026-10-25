#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUDIT="$ROOT_DIR/scripts/audit-public-artifacts.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[artifact-provenance-test][error] $*" >&2
  exit 1
}

NEGATIVE_SCENARIO_COUNT=0

expect_failure() {
  local label="$1"
  shift
  NEGATIVE_SCENARIO_COUNT=$((NEGATIVE_SCENARIO_COUNT + 1))
  if "$@" >/dev/null 2>&1; then
    fail "$label was accepted"
  fi
}

(
  cd "$ROOT_DIR"
  "$AUDIT" --strict-provenance >/dev/null
) || fail "valid strict provenance contract was rejected"

expect_failure "release without strict provenance" "$AUDIT" --release

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak '/app\/src\/main\/jniLibs\/arm64-v8a\/libsodium.so/d' "$tmp_dir/provenance.md"
expect_failure "undocumented allowlisted binary" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
printf '%s\n' '| `app/src/main/jniLibs/obsolete/libstale.so` | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |' >> "$tmp_dir/provenance.md"
expect_failure "stale extra provenance row" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
grep -F '| `app/src/main/jniLibs/arm64-v8a/libsodium.so` |' "$ROOT_DIR/docs/binary-provenance.md" >> "$tmp_dir/provenance.md"
expect_failure "duplicate provenance row" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' "$tmp_dir/provenance.md"
expect_failure "missing provenance self-test command" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak 's/7500809f33243ee47ecb2ec8563fc284ac4de0d6/0000000000000000000000000000000000000000/g' "$tmp_dir/provenance.md"
expect_failure "missing pinned sr25519 source revision" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak 's/libsodium 1\.0\.19/libsodium unknown/g' "$tmp_dir/provenance.md"
expect_failure "missing vendored libsodium source release" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak 's/8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b/0000000000000000000000000000000000000000000000000000000000000000/g' "$tmp_dir/provenance.md"
expect_failure "missing Gradle distribution checksum" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

expect_failure "release provenance document override" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$ROOT_DIR/docs/binary-provenance.md" \
  "$AUDIT" --release --strict-provenance

echo "[artifact-provenance-test] all adversarial fixtures passed (1 valid contract plus $NEGATIVE_SCENARIO_COUNT negative scenarios)"
