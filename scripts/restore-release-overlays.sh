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
  signing) ;;
  firebase)
    fail "RELEASE_OVERLAY_MODE=firebase was removed: the app carries no Firebase or Google Services configuration."
    ;;
  *) fail "RELEASE_OVERLAY_MODE must be exactly signing." ;;
esac

overlay_dir="${RELEASE_OVERLAY_DIR:-${RUNNER_TEMP:-$PWD/.release-overlays}}"
release_keystore="$overlay_dir/fearless-upload.jks"
signing_sentinel="$overlay_dir/.signing-overlay-active"
secret_temporary_dir="$overlay_dir/secret-temporary"
test_checkpoint_marker="$overlay_dir/.overlay-test-checkpoint"
temporary_files=()
owns_signing_transaction=false

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

file_size() {
  wc -c < "$1" | tr -d '[:space:]'
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

rollback_signing() {
  rm -f "$release_keystore" "$signing_sentinel"
}

on_exit() {
  local status="$?"
  trap - EXIT HUP INT TERM
  if (( status != 0 )); then
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

if [[ "$mode" == "signing" ]]; then
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
