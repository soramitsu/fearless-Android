#!/usr/bin/env bash
set -euo pipefail

fail() {
  echo "[release-overlay-cleanup][error] $*" >&2
  exit 1
}

[[ "${CI:-}" == "true" || "${GITHUB_ACTIONS:-}" == "true" ]] ||
  fail "Cleanup is restricted to an explicit CI environment."

cd "$(dirname "$0")/.."

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
# The Firebase overlay was removed in 4.3.2. An overlay directory an older run left behind may
# still hold its backup, sentinel or partial copies; they are deleted, never restored into app/.
legacy_firebase_backup="$overlay_dir/release-google-services.backup"
legacy_firebase_sentinel="$overlay_dir/.release-overlay-active"
signing_sentinel="$overlay_dir/.signing-overlay-active"
play_sentinel="$overlay_dir/.play-overlay-active"
release_keystore="$overlay_dir/fearless-upload.jks"
play_credential="$overlay_dir/play-service-account.json"
secret_temporary_dir="$overlay_dir/secret-temporary"
test_checkpoint_marker="$overlay_dir/.overlay-test-checkpoint"
legacy_firebase_backup_temporary_prefix=".release-google-services.backup.tmp."

cleanup_test_checkpoint() {
  local stage="$1"

  [[ -z "${RELEASE_OVERLAY_CLEANUP_TEST_CHECKPOINT:-}" ||
    "$RELEASE_OVERLAY_CLEANUP_TEST_CHECKPOINT" != "$stage" ]] && return 0
  [[ "${RELEASE_OVERLAY_CLEANUP_TEST_MODE:-}" == "true" &&
    "${CI:-}" == "true" ]] ||
    fail "Cleanup checkpoints are restricted to explicit CI tests."
  printf '%s\n' "$stage" > "$test_checkpoint_marker"
  chmod 600 "$test_checkpoint_marker"
  while true; do
    sleep 1
  done
}

assert_regular_or_absent() {
  local file_path="$1"
  if [[ -e "$file_path" || -L "$file_path" ]]; then
    [[ -f "$file_path" && ! -L "$file_path" ]] ||
      fail "Unsafe cleanup path: $file_path"
  fi
}

assert_no_symlink_components() {
  local file_path="$1"
  local current

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "Cleanup paths must not contain control characters."
  if [[ "$file_path" == /* ]]; then
    current="$file_path"
  else
    current="$PWD/$file_path"
  fi
  while [[ "$current" != "/" && "$current" != "." ]]; do
    [[ ! -L "$current" ]] || fail "Cleanup paths must not traverse symlinks."
    current="$(dirname "$current")"
  done
}

assert_no_symlink_components "$overlay_dir"
if [[ -e "$overlay_dir" || -L "$overlay_dir" ]]; then
  [[ -d "$overlay_dir" && ! -L "$overlay_dir" ]] ||
    fail "Release overlay directory is unsafe."
fi

for known_path in \
  "$legacy_firebase_sentinel" \
  "$signing_sentinel" \
  "$play_sentinel" \
  "$legacy_firebase_backup" \
  "$release_keystore" \
  "$play_credential" \
  "$test_checkpoint_marker"; do
  assert_regular_or_absent "$known_path"
done

if [[ -d "$overlay_dir" ]]; then
  shopt -s nullglob
  for backup_temporary in \
    "$overlay_dir"/"$legacy_firebase_backup_temporary_prefix"*; do
    [[ -f "$backup_temporary" && ! -L "$backup_temporary" ]] ||
      fail "Unsafe temporary release backup path: $backup_temporary"
    rm -f "$backup_temporary"
  done
  shopt -u nullglob
fi

if [[ -e "$secret_temporary_dir" || -L "$secret_temporary_dir" ]]; then
  [[ -d "$secret_temporary_dir" && ! -L "$secret_temporary_dir" ]] ||
    fail "Secret temporary directory is unsafe."
  shopt -s nullglob
  for temporary_file in "$secret_temporary_dir"/decoded.*; do
    [[ -f "$temporary_file" && ! -L "$temporary_file" ]] ||
      fail "Unsafe decoded temporary credential path: $temporary_file"
    rm -f "$temporary_file"
  done
  shopt -u nullglob
  if find "$secret_temporary_dir" -mindepth 1 -maxdepth 1 -print -quit |
    grep -q .; then
    fail "Secret temporary directory contains an unexpected entry."
  fi
  rmdir "$secret_temporary_dir"
fi

if [[ -f "$signing_sentinel" || -f "$release_keystore" ]]; then
  rm -f "$signing_sentinel"
  cleanup_test_checkpoint after-signing-sentinel-removal-before-keystore
  rm -f "$release_keystore"
  cleanup_test_checkpoint after-signing-keystore-removal
fi

rm -f "$play_credential" "$play_sentinel"

if [[ -f "$legacy_firebase_backup" || -f "$legacy_firebase_sentinel" ]]; then
  echo "[release-overlay-cleanup] removing a legacy Firebase overlay left by an earlier version"
fi
rm -f "$legacy_firebase_sentinel" "$legacy_firebase_backup"

rm -f \
  "$release_keystore" \
  "$signing_sentinel" \
  "$legacy_firebase_sentinel" \
  "$play_credential" \
  "$play_sentinel" \
  "$test_checkpoint_marker"

echo "[release-overlay-cleanup] ephemeral credentials removed"
