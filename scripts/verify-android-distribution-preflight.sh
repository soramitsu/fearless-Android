#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage: verify-android-distribution-preflight.sh --release-signing|--play-testing

Reads signing and Play credentials only from the established CI_* environment
variables. It never prints credentials or accepts them on the command line.
USAGE
}

fail() {
  echo "[android-distribution-preflight][error] $*" >&2
  exit 1
}

[[ "$#" -eq 1 ]] || {
  usage
  exit 2
}

mode="$1"
case "$mode" in
  --release-signing|--play-testing) ;;
  *)
    usage
    exit 2
    ;;
esac

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

require_bounded_release_value() {
  local name="$1"
  local maximum_bytes="$2"
  local value="${!name:-}"
  local byte_count
  local normalized
  local without_controls

  [[ -n "$value" ]] || fail "$name is required for a production-equivalent release artifact."
  byte_count="$(printf '%s' "$value" | wc -c | tr -d '[:space:]')"
  [[ "$byte_count" =~ ^[1-9][0-9]*$ ]] || fail "$name byte length is malformed."
  (( byte_count <= maximum_bytes )) || fail "$name exceeds the $maximum_bytes-byte limit."
  without_controls="$(printf '%s' "$value" | LC_ALL=C tr -d '[:cntrl:]')"
  [[ "$without_controls" == "$value" ]] || fail "$name must not contain control characters."
  [[ "$value" != *' '* ]] || fail "$name must not contain whitespace."

  normalized="$(printf '%s' "$value" | tr '[:upper:]' '[:lower:]')"
  case "$normalized" in
    test|todo|*stub*|*changeme*|*placeholder*|*'<your_'*|*'<replace_'*)
      fail "$name contains a placeholder value."
      ;;
  esac
}

require_release_runtime_config() {
  local name
  for name in \
    RAMP_TOKEN_RELEASE \
    COINBASE_APP_ID \
    MOONPAY_PRODUCTION_PUBLIC_KEY \
    X1_ENDPOINT_URL_RELEASE \
    X1_WIDGET_ID_RELEASE \
    WEB_CLIENT_ID_RELEASE \
    FL_BLAST_API_ETHEREUM_KEY \
    FL_BLAST_API_BSC_KEY \
    FL_BLAST_API_SEPOLIA_KEY \
    FL_BLAST_API_GOERLI_KEY \
    FL_BLAST_API_POLYGON_KEY \
    FL_ANDROID_ETHERSCAN_API_KEY \
    FL_ANDROID_BSCSCAN_API_KEY \
    FL_ANDROID_POLYGONSCAN_API_KEY \
    FL_ANDROID_OKLINK_API_KEY \
    FL_ANDROID_OPMAINNET_API_KEY \
    FL_WALLET_CONNECT_PROJECT_ID \
    FL_DWELLIR_API_KEY \
    FL_ANDROID_TON_API_KEY \
    FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY; do
    require_bounded_release_value "$name" 8192
  done

  [[ "$X1_ENDPOINT_URL_RELEASE" == https://* ]] ||
    fail "X1_ENDPOINT_URL_RELEASE must use HTTPS."
  [[ "$WEB_CLIENT_ID_RELEASE" =~ ^[A-Za-z0-9._-]+\.apps\.googleusercontent\.com$ ]] ||
    fail "WEB_CLIENT_ID_RELEASE must be a Google OAuth web client ID."
  if [[ -n "${FL_ANDROID_TON_INDEXER_URL:-}" ]]; then
    require_bounded_release_value FL_ANDROID_TON_INDEXER_URL 2048
    [[ "$FL_ANDROID_TON_INDEXER_URL" == https://* ]] ||
      fail "FL_ANDROID_TON_INDEXER_URL must use HTTPS when set."
  fi
}

file_mode() {
  local path="$1"
  stat -c '%a' "$path" 2>/dev/null || stat -f '%Lp' "$path" 2>/dev/null
}

require_no_symlink_components() {
  local path="$1"
  local label="$2"
  local current

  if [[ "$path" == /* ]]; then
    current="$path"
  else
    current="$PWD/$path"
  fi
  while [[ "$current" != "/" && "$current" != "." ]]; do
    [[ ! -L "$current" ]] || fail "$label must not traverse a symlink component."
    current="$(dirname "$current")"
  done
}

require_private_regular_file() {
  local path="$1"
  local label="$2"
  local maximum_bytes="$3"

  require_no_symlink_components "$path" "$label"
  [[ ! -L "$path" ]] || fail "$label must not be a symlink."
  [[ -f "$path" && -s "$path" ]] || fail "$label must be a non-empty regular file."

  local bytes
  bytes="$(wc -c < "$path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size could not be determined."
  (( bytes <= maximum_bytes )) || fail "$label exceeds the $maximum_bytes-byte limit."

  local permissions
  permissions="$(file_mode "$path")" || fail "$label permissions could not be determined."
  [[ "$permissions" =~ ^[0-7]{3,4}$ ]] || fail "$label permissions are malformed."
  (( (8#$permissions & 077) == 0 )) || fail "$label must not be group- or world-readable."
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

for name in \
  CI_KEYSTORE_PATH \
  CI_KEYSTORE_PASS \
  CI_KEYSTORE_KEY_ALIAS \
  CI_KEYSTORE_KEY_PASS; do
  require_env "$name"
done

require_release_runtime_config

require_private_regular_file "$CI_KEYSTORE_PATH" "CI_KEYSTORE_PATH" 16777216
command -v keytool >/dev/null 2>&1 || fail "keytool is required."

keytool_output="$(mktemp)"
trap 'rm -f "$keytool_output"' EXIT
chmod 600 "$keytool_output"

if ! keytool \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -list -v \
  -keystore "$CI_KEYSTORE_PATH" \
  -storepass:env CI_KEYSTORE_PASS \
  -alias "$CI_KEYSTORE_KEY_ALIAS" >"$keytool_output" 2>&1; then
  fail "The configured keystore or alias could not be opened."
fi

certificate_owner="$(awk -F'Owner: ' '/^[[:space:]]*Owner: / { print $2; exit }' "$keytool_output")"
certificate_sha256="$(
  awk -F'SHA256:' '/SHA256:/ { value=$2; gsub(/[[:space:]]/, "", value); print toupper(value); exit }' \
    "$keytool_output"
)"
[[ -n "$certificate_owner" ]] || fail "The signing certificate owner is missing."
[[ "$certificate_sha256" =~ ^([0-9A-F]{2}:){31}[0-9A-F]{2}$ ]] ||
  fail "The signing certificate SHA-256 fingerprint is malformed."
certificate_owner_upper="$(printf '%s' "$certificate_owner" | tr '[:lower:]' '[:upper:]')"
[[ "$certificate_owner_upper" != *"CN=ANDROID DEBUG"* ]] ||
  fail "An Android Debug certificate cannot sign a Play release."

if [[ "$mode" == "--play-testing" ]]; then
  require_env PLAY_TRACK
  require_env PLAY_RELEASE_STATUS
  require_env CI_PLAY_KEY
  require_env PLAY_ARTIFACT_DIR
  require_env RELEASE_TAG
  require_env RELEASE_COMMIT
  require_env RELEASE_VERSION_NAME
  require_env RELEASE_VERSION_CODE
  require_env VERSION_PROPERTIES_SHA

  case "$PLAY_TRACK" in
    internal|alpha|beta) ;;
    *) fail "PLAY_TRACK must be exactly internal, alpha, or beta for testing." ;;
  esac
  case "$PLAY_RELEASE_STATUS" in
    draft|completed) ;;
    *) fail "PLAY_RELEASE_STATUS must be exactly draft or completed." ;;
  esac

  require_private_regular_file "$CI_PLAY_KEY" "CI_PLAY_KEY" 65536
  command -v jq >/dev/null 2>&1 || fail "jq is required."
  jq -e '
    type == "object" and
    .type == "service_account" and
    (.project_id | type == "string" and test("^[a-z][a-z0-9-]{4,61}[a-z0-9]$")) and
    (.private_key_id | type == "string" and test("^[A-Za-z0-9_-]{8,256}$")) and
    (.private_key | type == "string" and startswith("-----BEGIN PRIVATE KEY-----\n") and endswith("\n-----END PRIVATE KEY-----\n")) and
    (.client_email | type == "string" and test("^[A-Za-z0-9._%+-]+@[a-z0-9.-]+\\.iam\\.gserviceaccount\\.com$")) and
    (.client_id | type == "string" and test("^[0-9]{6,32}$")) and
    .token_uri == "https://oauth2.googleapis.com/token"
  ' "$CI_PLAY_KEY" >/dev/null 2>&1 ||
    fail "CI_PLAY_KEY is not a bounded Google service-account credential document."

  require_no_symlink_components "$PLAY_ARTIFACT_DIR" "PLAY_ARTIFACT_DIR"
  [[ -d "$PLAY_ARTIFACT_DIR" ]] || fail "PLAY_ARTIFACT_DIR must be a directory."
  bundles=()
  while IFS= read -r -d '' bundle; do
    bundles+=("$bundle")
  done < <(find "$PLAY_ARTIFACT_DIR" -maxdepth 1 -type f -name '*.aab' -print0)
  [[ "${#bundles[@]}" -eq 1 ]] ||
    fail "PLAY_ARTIFACT_DIR must contain exactly one regular AAB."
  staged_bundle="${bundles[0]}"
  require_no_symlink_components "$staged_bundle" "staged AAB"
  [[ ! -L "$staged_bundle" && -f "$staged_bundle" && -s "$staged_bundle" ]] ||
    fail "The staged AAB must be a non-empty regular, non-symlink file."
  staged_bundle_bytes="$(wc -c < "$staged_bundle" | tr -d '[:space:]')"
  [[ "$staged_bundle_bytes" =~ ^[1-9][0-9]*$ ]] || fail "The staged AAB size is malformed."
  (( staged_bundle_bytes <= 262144000 )) || fail "The staged AAB exceeds 250 MiB."

  staged_checksum="$staged_bundle.sha256"
  staged_provenance="$PLAY_ARTIFACT_DIR/provenance.json"
  [[ ! -L "$staged_checksum" && -f "$staged_checksum" && -s "$staged_checksum" ]] ||
    fail "The staged AAB checksum sidecar is missing or unsafe."
  [[ ! -L "$staged_provenance" && -f "$staged_provenance" && -s "$staged_provenance" ]] ||
    fail "The staged AAB provenance manifest is missing or unsafe."
  require_no_symlink_components "$staged_checksum" "staged AAB checksum"
  require_no_symlink_components "$staged_provenance" "staged AAB provenance"
  (( $(wc -c < "$staged_checksum") <= 1024 )) || fail "The staged checksum sidecar is oversized."
  (( $(wc -c < "$staged_provenance") <= 16384 )) || fail "The staged provenance manifest is oversized."

  actual_artifact_sha256="$(sha256_file "$staged_bundle")"
  sidecar_artifact_sha256="$(awk 'NR == 1 { print $1 }' "$staged_checksum")"
  [[ "$sidecar_artifact_sha256" =~ ^[0-9a-f]{64}$ &&
      "$sidecar_artifact_sha256" == "$actual_artifact_sha256" ]] ||
    fail "The staged AAB checksum sidecar does not match the artifact."

  [[ "$RELEASE_TAG" =~ ^v[0-9]+\.[0-9]+\.[0-9]+([+-][0-9A-Za-z.-]+)?$ ]] ||
    fail "RELEASE_TAG is malformed."
  [[ "$RELEASE_COMMIT" =~ ^[0-9a-f]{40}$ ]] || fail "RELEASE_COMMIT is malformed."
  [[ "$RELEASE_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] || fail "RELEASE_VERSION_CODE is malformed."
  [[ "$VERSION_PROPERTIES_SHA" =~ ^[0-9a-f]{64}$ ]] || fail "VERSION_PROPERTIES_SHA is malformed."
  jq -e \
    --arg releaseTag "$RELEASE_TAG" \
    --arg releaseCommit "$RELEASE_COMMIT" \
    --arg versionName "$RELEASE_VERSION_NAME" \
    --arg versionCode "$RELEASE_VERSION_CODE" \
    --arg versionPropertiesSha256 "$VERSION_PROPERTIES_SHA" \
    --arg artifactSha256 "$actual_artifact_sha256" \
    --arg signingCertificateSha256 "$certificate_sha256" '
      keys == [
        "artifactSha256",
        "releaseCommit",
        "releaseTag",
        "signingCertificateSha256",
        "versionCode",
        "versionName",
        "versionPropertiesSha256"
      ] and
      .releaseTag == $releaseTag and
      .releaseCommit == $releaseCommit and
      .versionName == $versionName and
      .versionCode == $versionCode and
      .versionPropertiesSha256 == $versionPropertiesSha256 and
      .artifactSha256 == $artifactSha256 and
      .signingCertificateSha256 == $signingCertificateSha256
    ' "$staged_provenance" >/dev/null 2>&1 ||
    fail "The staged AAB provenance manifest does not match this release invocation."

  artifact_keytool_output="$(mktemp)"
  chmod 600 "$artifact_keytool_output"
  trap 'rm -f "$keytool_output" "$artifact_keytool_output"' EXIT
  if ! keytool \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -printcert -jarfile "$staged_bundle" >"$artifact_keytool_output" 2>&1; then
    fail "The staged AAB signing certificate could not be read."
  fi
  artifact_certificate_sha256="$(
    awk -F'SHA256:' '/SHA256:/ { value=$2; gsub(/[[:space:]]/, "", value); print toupper(value); exit }' \
      "$artifact_keytool_output"
  )"
  [[ "$artifact_certificate_sha256" == "$certificate_sha256" ]] ||
    fail "The staged AAB signer does not match the configured upload key."
fi

echo "[android-distribution-preflight] ${mode#--} passed"
