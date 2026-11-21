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
sed -i.bak '/^| `gradle\/wrapper\/gradle-wrapper.jar` |/d' "$tmp_dir/provenance.md"
expect_failure "undocumented allowlisted binary" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
printf '%s\n' '| `app/src/main/jniLibs/obsolete/libstale.so` | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |' >> "$tmp_dir/provenance.md"
expect_failure "stale extra provenance row" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
printf '%s\n' '| `runtime-utils/libs/polkaj-scale-0.2.3.jar` | `bea0f3904fef83497b9e5802f36408663a4aba462bf63ded38b89a5f22befa4d` |' >> "$tmp_dir/provenance.md"
expect_failure "retired polkaj-scale jar row" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
grep -F '| `gradle/wrapper/gradle-wrapper.jar` |' "$ROOT_DIR/docs/binary-provenance.md" >> "$tmp_dir/provenance.md"
expect_failure "duplicate provenance row" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' "$tmp_dir/provenance.md"
expect_failure "missing provenance self-test command" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

sr25519_arm64_sha256="$(
  python3 -I -c 'import json, sys; print(json.load(open(sys.argv[1]))["abis"]["arm64-v8a"]["sha256"])' \
    "$ROOT_DIR/runtime-utils/native/sr25519/reproducible-build.json"
)"
cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak "s/$sr25519_arm64_sha256/$(printf '0%.0s' {1..64})/g" "$tmp_dir/provenance.md"
expect_failure "sr25519 reproducible-build checksum out of sync" env \
  PUBLIC_ARTIFACT_PROVENANCE_DOC="$tmp_dir/provenance.md" \
  "$AUDIT" --strict-provenance

cp "$ROOT_DIR/docs/binary-provenance.md" "$tmp_dir/provenance.md"
sed -i.bak 's/cargo-ndk 4\.1\.2/cargo-ndk unknown/g' "$tmp_dir/provenance.md"
expect_failure "missing pinned sr25519 build toolchain" env \
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

# Firebase is not part of the app: an unsigned release audit passes without a Google Services
# configuration and fails as soon as one appears, tracked or not.
release_env=(env -u CI_KEYSTORE_PATH -u CI_KEYSTORE_PASS -u CI_KEYSTORE_KEY_ALIAS -u CI_KEYSTORE_KEY_PASS
  RAMP_TOKEN_RELEASE=synthetic WALLET_CONNECT_PROJECT_ID=00000000000000000000000000000000)
stray_google_services="$ROOT_DIR/app/src/release/google-services.json"
[[ ! -e "$stray_google_services" ]] || fail "a Google Services configuration already exists"
"${release_env[@]}" "$AUDIT" --unsigned-release --strict-provenance >/dev/null ||
  fail "unsigned release audit without Google Services configuration was rejected"
trap 'rm -rf "$tmp_dir"; rm -f "$stray_google_services"' EXIT
printf '{"project_info":{"project_id":"fearless-public"}}\n' >"$stray_google_services"
expect_failure "release with a Google Services configuration" \
  "${release_env[@]}" "$AUDIT" --unsigned-release --strict-provenance
rm -f "$stray_google_services"

echo "[artifact-provenance-test] all adversarial fixtures passed (2 valid contracts plus $NEGATIVE_SCENARIO_COUNT negative scenarios)"
