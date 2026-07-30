#!/usr/bin/env bash
set -euo pipefail
umask 077

fail() {
  echo "[release-overlays][error] $*" >&2
  exit 1
}

cd "$(dirname "$0")/.."

mode="${RELEASE_OVERLAY_MODE:-}"
case "$mode" in
  firebase|signing) ;;
  *) fail "RELEASE_OVERLAY_MODE must be exactly firebase or signing." ;;
esac

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
release_google_services="${GOOGLE_SERVICES_RELEASE_PATH:-app/src/release/google-services.json}"
release_keystore="$overlay_dir/fearless-upload.jks"
release_backup="$overlay_dir/release-google-services.backup"
release_sentinel="$overlay_dir/.release-overlay-active"
signing_sentinel="$overlay_dir/.signing-overlay-active"
secret_temporary_dir="$overlay_dir/secret-temporary"
test_checkpoint_marker="$overlay_dir/.overlay-test-checkpoint"
temporary_files=()
owns_firebase_transaction=false
owns_signing_transaction=false

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

file_size() {
  wc -c < "$1" | tr -d '[:space:]'
}

file_sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  elif command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$1" | awk '{print $1}'
  else
    fail "sha256sum or shasum is required to validate release backups."
  fi
}

file_mode() {
  local mode

  if mode="$(stat -c '%a' "$1" 2>/dev/null)"; then
    printf '%s\n' "$mode"
  elif mode="$(stat -f '%Lp' "$1" 2>/dev/null)"; then
    printf '%s\n' "$mode"
  else
    fail "Unable to inspect release backup permissions."
  fi
}

assert_no_symlink_components() {
  local file_path="$1"
  local current

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "Overlay paths must not contain control characters."
  if [[ "$file_path" == /* ]]; then
    current="$file_path"
  else
    current="$PWD/$file_path"
  fi
  while [[ "$current" != "/" && "$current" != "." ]]; do
    [[ ! -L "$current" ]] || fail "Overlay paths must not traverse symlinks."
    current="$(dirname "$current")"
  done
}

prepare_parent() {
  local file_path="$1"
  local parent

  parent="$(dirname "$file_path")"
  assert_no_symlink_components "$parent"
  mkdir -p "$parent"
  assert_no_symlink_components "$parent"
  [[ -d "$parent" ]] || fail "Overlay parent must be a directory."
  if [[ -e "$file_path" || -L "$file_path" ]]; then
    [[ -f "$file_path" && ! -L "$file_path" ]] ||
      fail "Overlay destination must be a regular file."
  fi
}

cleanup_temporary_files() {
  local file_path

  for file_path in "${temporary_files[@]}"; do
    if [[ -e "$file_path" || -L "$file_path" ]]; then
      [[ -f "$file_path" && ! -L "$file_path" ]] ||
        fail "Unsafe decoded temporary credential path: $file_path"
      rm -f "$file_path"
    fi
  done

  if [[ -d "$secret_temporary_dir" && ! -L "$secret_temporary_dir" ]]; then
    shopt -s nullglob
    for file_path in "$secret_temporary_dir"/decoded.*; do
      [[ -f "$file_path" && ! -L "$file_path" ]] ||
        fail "Unsafe decoded temporary credential path: $file_path"
      rm -f "$file_path"
    done
    shopt -u nullglob
  fi
}

rollback_firebase() {
  if [[ -e "$release_backup" || -L "$release_backup" ]]; then
    [[ -f "$release_backup" && ! -L "$release_backup" ]] ||
      fail "Release Firebase backup is unsafe."
    prepare_parent "$release_google_services"
    mv -f "$release_backup" "$release_google_services"
  fi
  rm -f "$release_sentinel"
}

rollback_signing() {
  rm -f "$release_keystore" "$signing_sentinel"
}

on_exit() {
  local status="$?"
  trap - EXIT HUP INT TERM
  if (( status != 0 )); then
    [[ "$owns_firebase_transaction" == "false" ]] || rollback_firebase
    [[ "$owns_signing_transaction" == "false" ]] || rollback_signing
  fi
  cleanup_temporary_files
  exit "$status"
}

trap on_exit EXIT
trap 'exit 130' HUP INT TERM

test_checkpoint() {
  local stage="$1"

  [[ -z "${RELEASE_OVERLAY_TEST_CHECKPOINT:-}" ||
    "$RELEASE_OVERLAY_TEST_CHECKPOINT" != "$stage" ]] && return 0
  [[ "${RELEASE_OVERLAY_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
    fail "Release-overlay test checkpoints are restricted to explicit CI tests."
  printf '%s\n' "$stage" > "$test_checkpoint_marker"
  while true; do
    sleep 1
  done
}

create_release_backup() {
  local source="$1"
  local destination="$2"
  local source_size_before
  local source_size_after
  local source_sha_before
  local source_sha_after
  local backup_size
  local backup_sha
  local backup_mode
  local partial_bytes
  local temporary

  source_size_before="$(file_size "$source")"
  [[ "$source_size_before" =~ ^[1-9][0-9]*$ ]] ||
    fail "The checked-in release Firebase placeholder is empty."
  source_sha_before="$(file_sha256 "$source")"

  temporary="$(mktemp "$overlay_dir/.release-google-services.backup.tmp.XXXXXX")"
  temporary_files+=("$temporary")
  chmod 600 "$temporary"

  if [[ "${RELEASE_OVERLAY_TEST_CHECKPOINT:-}" == "during-release-backup-copy" ]]; then
    [[ "${RELEASE_OVERLAY_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
      fail "Release-overlay test checkpoints are restricted to explicit CI tests."
    partial_bytes=$((source_size_before / 2))
    (( partial_bytes > 0 )) || partial_bytes=1
    dd if="$source" of="$temporary" bs=1 count="$partial_bytes" \
      2>/dev/null ||
      fail "Unable to construct the release Firebase backup fixture."
    chmod 600 "$temporary"
    test_checkpoint during-release-backup-copy
  fi

  cp "$source" "$temporary" ||
    fail "Unable to copy the release Firebase placeholder to a temporary backup."
  chmod 600 "$temporary"
  test_checkpoint after-release-backup-copy

  case "${RELEASE_OVERLAY_TEST_BACKUP_MUTATION:-}" in
    "")
      ;;
    content)
      [[ "${RELEASE_OVERLAY_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
        fail "Release-overlay test mutations are restricted to explicit CI tests."
      printf 'X' | dd of="$temporary" bs=1 seek=0 count=1 conv=notrunc \
        2>/dev/null
      ;;
    permissions)
      [[ "${RELEASE_OVERLAY_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
        fail "Release-overlay test mutations are restricted to explicit CI tests."
      chmod 640 "$temporary"
      ;;
    *)
      fail "Unsupported release-overlay test backup mutation."
      ;;
  esac

  source_size_after="$(file_size "$source")"
  source_sha_after="$(file_sha256 "$source")"
  backup_size="$(file_size "$temporary")"
  backup_sha="$(file_sha256 "$temporary")"
  backup_mode="$(file_mode "$temporary")"

  [[ "$source_size_before" == "$source_size_after" &&
    "$source_size_before" == "$backup_size" ]] ||
    fail "Release Firebase placeholder changed while its backup was constructed."
  [[ "$source_sha_before" == "$source_sha_after" &&
    "$source_sha_before" == "$backup_sha" ]] ||
    fail "Release Firebase backup failed its SHA-256 integrity check."
  [[ "$backup_mode" == "600" ]] ||
    fail "Release Firebase backup permissions must be exactly 0600."
  test_checkpoint after-release-backup-validation

  [[ ! -e "$destination" && ! -L "$destination" ]] ||
    fail "A stale release Firebase backup appeared during preparation."
  mv "$temporary" "$destination" ||
    fail "Unable to atomically publish the validated release Firebase backup."
}

decode_to_temporary_file() {
  local env_name="$1"
  local maximum_bytes="$2"
  local output_variable="$3"
  local temporary
  local bytes

  require_env "$env_name"
  temporary="$(mktemp "$secret_temporary_dir/decoded.XXXXXX")"
  temporary_files+=("$temporary")
  chmod 600 "$temporary"
  if base64 --help 2>&1 | grep -q -- '-d'; then
    printf '%s' "${!env_name}" | base64 -d > "$temporary" ||
      fail "$env_name is not valid base64."
  else
    printf '%s' "${!env_name}" | base64 -D > "$temporary" ||
      fail "$env_name is not valid base64."
  fi
  bytes="$(file_size "$temporary")"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] ||
    fail "$env_name decoded to an empty file."
  (( bytes <= maximum_bytes )) ||
    fail "$env_name exceeds its maximum decoded size."
  printf -v "$output_variable" '%s' "$temporary"
}

assert_no_symlink_components "$overlay_dir"
mkdir -p "$overlay_dir"
chmod 700 "$overlay_dir"
assert_no_symlink_components "$overlay_dir"
[[ -d "$overlay_dir" ]] || fail "Release overlay directory is unsafe."

if [[ -e "$secret_temporary_dir" || -L "$secret_temporary_dir" ]]; then
  [[ -d "$secret_temporary_dir" && ! -L "$secret_temporary_dir" ]] ||
    fail "Secret temporary directory is unsafe."
else
  mkdir "$secret_temporary_dir"
fi
chmod 700 "$secret_temporary_dir"

if [[ "$mode" == "firebase" ]]; then
  command -v jq >/dev/null 2>&1 ||
    fail "jq is required to validate the Firebase release overlay."
  for forbidden_signing_input in \
    ANDROID_RELEASE_KEYSTORE_B64 \
    CI_KEYSTORE_PATH \
    CI_KEYSTORE_PASS \
    CI_KEYSTORE_KEY_ALIAS \
    CI_KEYSTORE_KEY_PASS \
    PLAY_SERVICE_ACCOUNT_JSON_B64 \
    CI_PLAY_KEY; do
    [[ -z "${!forbidden_signing_input:-}" ]] ||
      fail "The Firebase-only overlay forbids signing and Play credentials."
  done
  require_env ANDROID_RELEASE_FIREBASE_JSON_SHA256
  [[ "$ANDROID_RELEASE_FIREBASE_JSON_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
    fail "ANDROID_RELEASE_FIREBASE_JSON_SHA256 must be an exact lowercase SHA-256 digest."
  [[ ! -e "$release_sentinel" && ! -L "$release_sentinel" ]] ||
    fail "A Firebase release overlay is already active."
  [[ ! -e "$release_backup" && ! -L "$release_backup" ]] ||
    fail "A stale release Firebase backup exists."
  [[ -f "$release_google_services" && ! -L "$release_google_services" ]] ||
    fail "The checked-in release Firebase placeholder is missing or unsafe."
  prepare_parent "$release_google_services"
  owns_firebase_transaction=true

  google_temporary=""
  decode_to_temporary_file \
    GOOGLE_SERVICES_RELEASE_JSON_B64 \
    262144 \
    google_temporary
  test_checkpoint after-google-decode

  jq -e '
    type == "object" and
    (.project_info.project_id | type == "string" and . != "fearless-public") and
    ([.client[]?.client_info.android_client_info.package_name] |
      map(select(. == "jp.co.soramitsu.fearless")) | length == 1)
  ' "$google_temporary" >/dev/null 2>&1 ||
    fail "GOOGLE_SERVICES_RELEASE_JSON_B64 is not the production Fearless Firebase configuration."
  [[ "$(file_sha256 "$google_temporary")" == \
    "$ANDROID_RELEASE_FIREBASE_JSON_SHA256" ]] ||
    fail "GOOGLE_SERVICES_RELEASE_JSON_B64 does not match the approved Firebase SHA-256 digest."

  create_release_backup "$release_google_services" "$release_backup"
  test_checkpoint after-release-backup
  printf 'prepared\n' > "$release_sentinel"
  chmod 600 "$release_sentinel"
  test_checkpoint after-release-sentinel

  mv -f "$google_temporary" "$release_google_services"
  google_temporary=""
  test_checkpoint after-google-move
  chmod 600 "$release_google_services"
  printf 'active\n' > "$release_sentinel"
  chmod 600 "$release_sentinel"

elif [[ "$mode" == "signing" ]]; then
  [[ "${CI:-}" == "true" ]] ||
    fail "The signing overlay is restricted to an explicit CI environment."
  for forbidden_build_or_play_input in \
    GOOGLE_SERVICES_RELEASE_JSON_B64 \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256 \
    PLAY_SERVICE_ACCOUNT_JSON_B64 \
    CI_PLAY_KEY; do
    [[ -z "${!forbidden_build_or_play_input:-}" ]] ||
      fail "The signing-only overlay forbids Firebase and Play credentials."
  done
  [[ ! -e "$signing_sentinel" && ! -L "$signing_sentinel" ]] ||
    fail "A signing-only overlay is already active."
  [[ ! -e "$release_keystore" && ! -L "$release_keystore" ]] ||
    fail "A stale release keystore exists."
  require_env ANDROID_UNSIGNED_AAB_PATH
  assert_no_symlink_components "$ANDROID_UNSIGNED_AAB_PATH"
  [[ -f "$ANDROID_UNSIGNED_AAB_PATH" &&
    ! -L "$ANDROID_UNSIGNED_AAB_PATH" &&
    -s "$ANDROID_UNSIGNED_AAB_PATH" ]] ||
    fail "ANDROID_UNSIGNED_AAB_PATH must identify the completed unsigned AAB."
  command -v unzip >/dev/null 2>&1 ||
    fail "unzip is required to validate the completed unsigned AAB."
  unzip -t "$ANDROID_UNSIGNED_AAB_PATH" >/dev/null ||
    fail "ANDROID_UNSIGNED_AAB_PATH is not a valid ZIP archive."
  if unzip -Z1 "$ANDROID_UNSIGNED_AAB_PATH" |
    LC_ALL=C grep -Eiq \
      '^META-INF/[^/]+\.(RSA|DSA|EC|SF)$|^META-INF/MANIFEST\.MF$'; then
    fail "ANDROID_UNSIGNED_AAB_PATH already contains JAR signature metadata."
  fi
  owns_signing_transaction=true

  keystore_temporary=""
  decode_to_temporary_file \
    ANDROID_RELEASE_KEYSTORE_B64 \
    16777216 \
    keystore_temporary
  test_checkpoint after-keystore-decode
  printf 'prepared\n' > "$signing_sentinel"
  chmod 600 "$signing_sentinel"
  test_checkpoint after-signing-sentinel
  mv -f "$keystore_temporary" "$release_keystore"
  keystore_temporary=""
  test_checkpoint after-keystore-move
  chmod 600 "$release_keystore"
  printf 'active\n' > "$signing_sentinel"
  chmod 600 "$signing_sentinel"

  if [[ -n "${GITHUB_ENV:-}" ]]; then
    echo "CI_KEYSTORE_PATH=$release_keystore" >> "$GITHUB_ENV"
  else
    echo "[release-overlays] Set CI_KEYSTORE_PATH=$release_keystore"
  fi
fi

echo "[release-overlays] $mode overlay restored"
