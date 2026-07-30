#!/usr/bin/env bash
set -euo pipefail
umask 077

EXPECTED_UPLOAD_CERT_SHA256="40391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
EXPECTED_BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
EXPECTED_PACKAGE_NAME="jp.co.soramitsu.fearless"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AAB_IDENTITY_VERIFIER="$ROOT_DIR/scripts/verify-android-aab-identity.sh"
AAB_SIGNATURE_VERIFIER="$ROOT_DIR/scripts/verify-android-aab-jar-signature.sh"
temporary_files=()

cleanup() {
  local path
  for path in "${temporary_files[@]-}"; do
    [[ -n "$path" ]] || continue
    rm -f "$path"
  done
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/verify-android-play-release.sh --signing-config
  scripts/verify-android-play-release.sh \
    --artifact <release.aab> <trusted-signer-sha256>

The verifier never prints credential values. --signing-config validates the
release keystore against the registered Google Play upload certificate.
--artifact derives package, version, source commit, ABI set, and the complete
allowlisted native payload from the exact signed AAB. Google Play publication
is a manual Play Console operation and this script never receives Play API
credentials or mutates a Play track.
USAGE
}

fail() {
  echo "[android-play-release][error] $*" >&2
  exit 1
}

test_checkpoint() {
  local stage="$1"
  local marker="${ANDROID_PLAY_VERIFIER_TEST_MARKER:-}"
  local resume="${ANDROID_PLAY_VERIFIER_TEST_RESUME:-}"

  [[ -z "${ANDROID_PLAY_VERIFIER_TEST_CHECKPOINT:-}" ||
    "$ANDROID_PLAY_VERIFIER_TEST_CHECKPOINT" != "$stage" ]] && return 0
  [[ "${ANDROID_PLAY_VERIFIER_TEST_MODE:-}" == "true" &&
    "${CI:-}" == "true" ]] ||
    fail "Verifier test checkpoints are restricted to explicit CI tests."
  [[ -n "$marker" && -n "$resume" ]] ||
    fail "Verifier test checkpoint paths are required."
  [[ "$marker" != *$'\n'* && "$marker" != *$'\r'* &&
    "$resume" != *$'\n'* && "$resume" != *$'\r'* ]] ||
    fail "Verifier test checkpoint paths are malformed."
  [[ ! -e "$marker" && ! -L "$marker" ]] ||
    fail "Verifier test checkpoint marker must not already exist."
  command -v sleep >/dev/null 2>&1 || fail "sleep is required for verifier tests."

  printf '%s\n' "$stage" >"$marker"
  chmod 600 "$marker"
  while [[ ! -e "$resume" && ! -L "$resume" ]]; do
    sleep 0.05
  done
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

normalize_fingerprint() {
  tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

require_regular_file() {
  local path="$1"
  local label="$2"
  local maximum_bytes="$3"

  [[ "$path" != *$'\n'* && "$path" != *$'\r'* ]] || fail "$label path is malformed."
  [[ ! -L "$path" && -f "$path" && -s "$path" ]] ||
    fail "$label must be a non-empty regular, non-symlink file."

  local bytes
  bytes="$(wc -c < "$path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size is malformed."
  (( bytes <= maximum_bytes )) || fail "$label exceeds its maximum size."
}

certificate_fingerprint_from_keystore() {
  local output
  output="$(mktemp)"
  temporary_files+=("$output")
  chmod 600 "$output"

  if ! keytool \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -list -v \
    -keystore "$CI_KEYSTORE_PATH" \
    -storepass:env CI_KEYSTORE_PASS \
    -alias "$CI_KEYSTORE_KEY_ALIAS" >"$output" 2>&1; then
    fail "The configured keystore or alias could not be opened."
  fi

  if grep -Eiq 'Owner:.*CN=Android Debug' "$output"; then
    fail "The Android Debug certificate cannot sign a Google Play release."
  fi

  awk -F'SHA256:' '/SHA256:/ { print $2; exit }' "$output" | normalize_fingerprint
}

verify_signing_config() {
  local name
  for name in \
    CI_KEYSTORE_PATH \
    CI_KEYSTORE_PASS \
    CI_KEYSTORE_KEY_ALIAS \
    CI_KEYSTORE_KEY_PASS; do
    require_env "$name"
  done
  command -v keytool >/dev/null 2>&1 || fail "keytool is required."
  require_regular_file "$CI_KEYSTORE_PATH" "CI_KEYSTORE_PATH" 16777216

  local fingerprint
  fingerprint="$(certificate_fingerprint_from_keystore)"
  [[ "$fingerprint" == "$EXPECTED_UPLOAD_CERT_SHA256" ]] ||
    fail "The release keystore does not match the registered Google Play upload certificate."
}

verify_artifact() {
  local artifact="$1"
  local expected_sha256="$2"
  local artifact_snapshot

  for command_name in chmod cp mktemp tr unzip wc; do
    command -v "$command_name" >/dev/null 2>&1 ||
      fail "$command_name is required."
  done
  [[ -x "$AAB_SIGNATURE_VERIFIER" && ! -L "$AAB_SIGNATURE_VERIFIER" ]] ||
    fail "The AAB JAR-signature verifier is missing or unsafe."
  [[ -x "$AAB_IDENTITY_VERIFIER" && ! -L "$AAB_IDENTITY_VERIFIER" ]] ||
    fail "The AAB identity verifier is missing or unsafe."
  [[ "$expected_sha256" =~ ^[0-9a-f]{64}$ ]] ||
    fail "The trusted signer SHA-256 must be exactly 64 lowercase hexadecimal characters."
  require_regular_file "$artifact" "release AAB" 262144000

  test_checkpoint after-input-validation
  artifact_snapshot="$(mktemp "${RUNNER_TEMP:-${TMPDIR:-/tmp}}/fearless-play-aab.XXXXXX")"
  temporary_files+=("$artifact_snapshot")
  chmod 600 "$artifact_snapshot"
  cp -- "$artifact" "$artifact_snapshot"
  chmod 400 "$artifact_snapshot"
  require_regular_file "$artifact_snapshot" "private release AAB snapshot" 262144000
  [[ "$(sha256_file "$artifact_snapshot")" == "$expected_sha256" &&
    "$(sha256_file "$artifact")" == "$expected_sha256" ]] ||
    fail "The release AAB changed while its private verification snapshot was created."
  unzip -t "$artifact_snapshot" >/dev/null ||
    fail "The release AAB is not a valid ZIP archive."
  require_env BUNDLETOOL_JAR
  require_env RELEASE_VERSION_NAME
  require_env RELEASE_VERSION_CODE
  require_env RELEASE_COMMIT
  require_regular_file "$BUNDLETOOL_JAR" "BUNDLETOOL_JAR" 41943040
  [[ "$(sha256_file "$BUNDLETOOL_JAR")" == "$EXPECTED_BUNDLETOOL_SHA256" ]] ||
    fail "BUNDLETOOL_JAR does not match the pinned bundletool release."
  test_checkpoint after-snapshot-copy

  "$AAB_SIGNATURE_VERIFIER" \
    "$artifact_snapshot" \
    "$EXPECTED_UPLOAD_CERT_SHA256"
  test_checkpoint after-signature-verification

  BUNDLETOOL_JAR="$BUNDLETOOL_JAR" "$AAB_IDENTITY_VERIFIER" \
    "$artifact_snapshot" \
    "$EXPECTED_PACKAGE_NAME" \
    "$RELEASE_VERSION_NAME" \
    "$RELEASE_VERSION_CODE" \
    "$RELEASE_COMMIT"
  test_checkpoint after-identity-verification

  [[ "$(sha256_file "$artifact_snapshot")" == "$expected_sha256" ]] ||
    fail "The private release AAB snapshot changed during verification."
  [[ "$(sha256_file "$artifact")" == "$expected_sha256" ]] ||
    fail "The release AAB changed during verification."
  echo "[android-play-release] trusted-signer-sha256=$expected_sha256"
}

case "${1:-}" in
  --signing-config)
    [[ "$#" -eq 1 ]] || { usage; exit 2; }
    verify_signing_config
    ;;
  --artifact)
    [[ "$#" -eq 3 ]] || { usage; exit 2; }
    verify_artifact "$2" "$3"
    ;;
  *)
    usage
    exit 2
    ;;
esac

echo "[android-play-release] verification passed"
