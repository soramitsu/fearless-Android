#!/usr/bin/env bash
set -euo pipefail
umask 077

log() { echo "[release-overlays] $*"; }
fail() {
  echo "[release-overlays][error] $*" >&2
  exit 1
}

cd "$(dirname "$0")/.."

temporary_files=()
cleanup() {
  local path
  for path in "${temporary_files[@]}"; do
    [[ -n "$path" ]] && rm -f "$path"
  done
}
trap cleanup EXIT HUP INT TERM

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

assert_no_symlink_components() {
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

validate_output_destination() {
  local output="$1"
  local label="$2"
  local parent

  [[ "$output" != *$'\n'* && "$output" != *$'\r'* ]] || fail "$label output path is malformed."
  parent="$(dirname "$output")"
  assert_no_symlink_components "$parent" "$label output parent"
  mkdir -p "$parent"
  assert_no_symlink_components "$parent" "$label output parent"
  [[ -d "$parent" ]] || fail "$label output parent must be a directory."
  if [[ -e "$output" || -L "$output" ]]; then
    [[ -f "$output" && ! -L "$output" ]] || fail "$label output must be a regular, non-symlink file."
  fi
}

validate_base64_payload() {
  local env_name="$1"
  local maximum_bytes="$2"
  local validation_file
  local decoded_bytes

  require_env "$env_name"
  validation_file="$(mktemp "$overlay_dir/.fearless-release-validation.XXXXXX")"
  temporary_files+=("$validation_file")
  chmod 600 "$validation_file"
  if base64 --help 2>&1 | grep -q -- '-d'; then
    if ! printf '%s' "${!env_name}" | base64 -d > "$validation_file"; then
      fail "$env_name is not valid base64."
    fi
  else
    if ! printf '%s' "${!env_name}" | base64 -D > "$validation_file"; then
      fail "$env_name is not valid base64."
    fi
  fi
  decoded_bytes="$(wc -c < "$validation_file" | tr -d '[:space:]')"
  [[ "$decoded_bytes" =~ ^[1-9][0-9]*$ ]] || fail "$env_name decoded to an empty or malformed file."
  (( decoded_bytes <= maximum_bytes )) || fail "$env_name exceeds the $maximum_bytes-byte limit."
  rm -f "$validation_file"
}

decode_base64_to_file() {
  local env_name="$1"
  local output="$2"
  local maximum_bytes="$3"
  local parent
  local temporary_output
  local decoded_bytes
  local permissions

  require_env "$env_name"
  validate_output_destination "$output" "$env_name"
  parent="$(dirname "$output")"

  temporary_output="$(mktemp "$parent/.fearless-release-overlay.XXXXXX")"
  temporary_files+=("$temporary_output")
  chmod 600 "$temporary_output"

  if base64 --help 2>&1 | grep -q -- '-d'; then
    if ! printf '%s' "${!env_name}" | base64 -d > "$temporary_output"; then
      fail "$env_name is not valid base64."
    fi
  else
    if ! printf '%s' "${!env_name}" | base64 -D > "$temporary_output"; then
      fail "$env_name is not valid base64."
    fi
  fi

  decoded_bytes="$(wc -c < "$temporary_output" | tr -d '[:space:]')"
  [[ "$decoded_bytes" =~ ^[1-9][0-9]*$ ]] || fail "$env_name decoded to an empty or malformed file."
  (( decoded_bytes <= maximum_bytes )) || fail "$env_name exceeds the $maximum_bytes-byte limit."
  chmod 600 "$temporary_output"
  mv -f "$temporary_output" "$output"
  temporary_output=""

  assert_no_symlink_components "$output" "$env_name output"
  [[ -f "$output" && ! -L "$output" && -s "$output" ]] || fail "$output was not restored safely."
  permissions="$(stat -c '%a' "$output" 2>/dev/null || stat -f '%Lp' "$output" 2>/dev/null)" ||
    fail "$output permissions could not be determined."
  [[ "$permissions" == "600" ]] || fail "$output must have mode 0600."
}

create_active_sentinel() {
  local sentinel="$1"
  local label="$2"
  local parent
  local temporary_sentinel
  local permissions

  validate_output_destination "$sentinel" "$label"
  [[ ! -e "$sentinel" && ! -L "$sentinel" ]] ||
    fail "$label already exists; run the guarded release-overlay cleanup before restoring credentials."
  parent="$(dirname "$sentinel")"
  temporary_sentinel="$(mktemp "$parent/.fearless-release-sentinel.XXXXXX")"
  temporary_files+=("$temporary_sentinel")
  chmod 600 "$temporary_sentinel"
  if ! ln "$temporary_sentinel" "$sentinel"; then
    fail "$label could not be created without replacing an existing path."
  fi
  rm -f "$temporary_sentinel"

  assert_no_symlink_components "$sentinel" "$label"
  [[ -f "$sentinel" && ! -L "$sentinel" ]] || fail "$label was not created safely."
  permissions="$(stat -c '%a' "$sentinel" 2>/dev/null || stat -f '%Lp' "$sentinel" 2>/dev/null)" ||
    fail "$label permissions could not be determined."
  [[ "$permissions" == "600" ]] || fail "$label must have mode 0600."
}

restore_mode="${RELEASE_OVERLAY_MODE:-release}"
case "$restore_mode" in
  release|play) ;;
  *) fail "RELEASE_OVERLAY_MODE must be exactly release or play." ;;
esac

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
assert_no_symlink_components "$overlay_dir" "release overlay directory"
mkdir -p "$overlay_dir"
assert_no_symlink_components "$overlay_dir" "release overlay directory"

release_google_services="${GOOGLE_SERVICES_RELEASE_PATH:-app/src/release/google-services.json}"
keystore_path="$overlay_dir/fearless-upload.jks"
play_key_path="$overlay_dir/play-service-account.json"
release_sentinel="$overlay_dir/.release-overlay-active"
play_sentinel="$overlay_dir/.play-overlay-active"

if [[ "$restore_mode" == "release" ]]; then
  require_env GOOGLE_SERVICES_RELEASE_JSON_B64
  require_env ANDROID_RELEASE_KEYSTORE_B64
  validate_output_destination "$release_google_services" GOOGLE_SERVICES_RELEASE_JSON_B64
  validate_output_destination "$keystore_path" ANDROID_RELEASE_KEYSTORE_B64
  validate_output_destination "$release_sentinel" "release overlay sentinel"
  validate_base64_payload GOOGLE_SERVICES_RELEASE_JSON_B64 262144
  validate_base64_payload ANDROID_RELEASE_KEYSTORE_B64 16777216
  create_active_sentinel "$release_sentinel" "release overlay sentinel"
  decode_base64_to_file GOOGLE_SERVICES_RELEASE_JSON_B64 "$release_google_services" 262144
  decode_base64_to_file ANDROID_RELEASE_KEYSTORE_B64 "$keystore_path" 16777216
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "CI_KEYSTORE_PATH=$keystore_path" >> "$GITHUB_ENV"
  else
    log "Set CI_KEYSTORE_PATH=$keystore_path"
  fi
else
  require_env PLAY_SERVICE_ACCOUNT_JSON_B64
  validate_output_destination "$play_key_path" PLAY_SERVICE_ACCOUNT_JSON_B64
  validate_output_destination "$play_sentinel" "Play overlay sentinel"
  validate_base64_payload PLAY_SERVICE_ACCOUNT_JSON_B64 65536
  create_active_sentinel "$play_sentinel" "Play overlay sentinel"
  decode_base64_to_file PLAY_SERVICE_ACCOUNT_JSON_B64 "$play_key_path" 65536
  if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "CI_PLAY_KEY=$play_key_path" >> "$GITHUB_ENV"
  else
    log "Set CI_PLAY_KEY=$play_key_path"
  fi
fi

log "$restore_mode overlay restored."
