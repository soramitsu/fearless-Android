#!/usr/bin/env bash
set -euo pipefail

log() { echo "[release-overlays] $*"; }
fail() {
  echo "[release-overlays][error] $*" >&2
  exit 1
}

cd "$(dirname "$0")/.."

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

decode_base64_to_file() {
  local env_name="$1"
  local output="$2"

  require_env "$env_name"
  mkdir -p "$(dirname "$output")"

  if base64 --help 2>&1 | grep -q -- '-d'; then
    printf '%s' "${!env_name}" | base64 -d > "$output"
  else
    printf '%s' "${!env_name}" | base64 -D > "$output"
  fi

  [[ -s "$output" ]] || fail "$output was not restored."
}

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
mkdir -p "$overlay_dir"

release_google_services="app/src/release/google-services.json"
keystore_path="$overlay_dir/fearless-upload.jks"
play_key_path="$overlay_dir/play-service-account.json"

decode_base64_to_file GOOGLE_SERVICES_RELEASE_JSON_B64 "$release_google_services"
decode_base64_to_file ANDROID_RELEASE_KEYSTORE_B64 "$keystore_path"
decode_base64_to_file PLAY_SERVICE_ACCOUNT_JSON_B64 "$play_key_path"

if [[ -n "${GITHUB_ENV:-}" ]]; then
  {
    echo "CI_KEYSTORE_PATH=$keystore_path"
    echo "CI_PLAY_KEY=$play_key_path"
  } >> "$GITHUB_ENV"
else
  log "Set CI_KEYSTORE_PATH=$keystore_path"
  log "Set CI_PLAY_KEY=$play_key_path"
fi

log "Release overlays restored."
