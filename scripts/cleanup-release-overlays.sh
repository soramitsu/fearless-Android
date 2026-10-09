#!/usr/bin/env bash
set -euo pipefail

log() { echo "[release-overlay-cleanup] $*"; }
fail() {
  echo "[release-overlay-cleanup][error] $*" >&2
  exit 1
}

[[ "${CI:-}" == "true" || "${GITHUB_ACTIONS:-}" == "true" ]] ||
  fail "Cleanup is restricted to an explicit CI environment."

cd "$(dirname "$0")/.."

assert_no_parent_symlinks() {
  local path="$1"
  local label="$2"
  local current

  if [[ "$path" == /* ]]; then
    current="$(dirname "$path")"
  else
    current="$PWD/$(dirname "$path")"
  fi
  while [[ "$current" != "/" && "$current" != "." ]]; do
    [[ ! -L "$current" ]] || fail "$label parent must not traverse a symlink."
    current="$(dirname "$current")"
  done
}

remove_ephemeral_file() {
  local path="$1"
  local label="$2"

  [[ "$path" != *$'\n'* && "$path" != *$'\r'* ]] || fail "$label path is malformed."
  assert_no_parent_symlinks "$path" "$label"
  if [[ -d "$path" && ! -L "$path" ]]; then
    fail "$label unexpectedly identifies a directory."
  fi
  rm -f "$path"
  [[ ! -e "$path" && ! -L "$path" ]] || fail "$label could not be removed."
}

sentinel_is_active() {
  local path="$1"
  local label="$2"
  local permissions

  [[ "$path" != *$'\n'* && "$path" != *$'\r'* ]] || fail "$label path is malformed."
  assert_no_parent_symlinks "$path" "$label"
  if [[ ! -e "$path" && ! -L "$path" ]]; then
    return 1
  fi
  [[ -f "$path" && ! -L "$path" ]] ||
    fail "$label must be a regular, non-symlink file when present."
  permissions="$(stat -c '%a' "$path" 2>/dev/null || stat -f '%Lp' "$path" 2>/dev/null)" ||
    fail "$label permissions could not be determined."
  [[ "$permissions" == "600" ]] || fail "$label must have mode 0600."
  return 0
}

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
release_google_services="${GOOGLE_SERVICES_RELEASE_PATH:-app/src/release/google-services.json}"
release_sentinel="$overlay_dir/.release-overlay-active"
play_sentinel="$overlay_dir/.play-overlay-active"

release_active=false
play_active=false
if sentinel_is_active "$release_sentinel" "release overlay sentinel"; then
  release_active=true
fi
if sentinel_is_active "$play_sentinel" "Play overlay sentinel"; then
  play_active=true
fi

if [[ "$release_active" == true ]]; then
  remove_ephemeral_file "$release_google_services" "release Firebase overlay"
  remove_ephemeral_file "$overlay_dir/fearless-upload.jks" "release keystore overlay"
  remove_ephemeral_file "$release_sentinel" "release overlay sentinel"
fi
if [[ "$play_active" == true ]]; then
  remove_ephemeral_file "$overlay_dir/play-service-account.json" "Play service-account overlay"
  remove_ephemeral_file "$play_sentinel" "Play overlay sentinel"
fi

log "ephemeral release credentials removed"
