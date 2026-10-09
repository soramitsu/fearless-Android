#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RESTORE="$ROOT_DIR/scripts/restore-release-overlays.sh"
if [[ -d /private/tmp && ! -L /private/tmp ]]; then
  tmp_dir="$(mktemp -d /private/tmp/release-overlay-test.XXXXXX)"
else
  tmp_dir="$(mktemp -d)"
fi
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[release-overlay-test][error] $*" >&2
  exit 1
}

negative_count=0
expect_failure() {
  local label="$1"
  shift
  negative_count=$((negative_count + 1))
  if "$@" >/dev/null 2>&1; then
    fail "$label was accepted"
  fi
}

encode() {
  printf '%s' "$1" | base64 | tr -d '\r\n'
}

mode_of() {
  stat -c '%a' "$1" 2>/dev/null || stat -f '%Lp' "$1" 2>/dev/null
}

google_b64="$(encode '{"project_info":{"project_id":"fearless-release"}}')"
keystore_b64="$(encode 'release-keystore')"
play_b64="$(encode '{"type":"service_account"}')"

valid_root="$tmp_dir/valid"
mkdir -p "$valid_root/app/release"
printf '%s' 'old-google-config' > "$valid_root/app/release/google-services.json"
chmod 644 "$valid_root/app/release/google-services.json"
github_env="$valid_root/github-env"
: > "$github_env"
chmod 600 "$github_env"

env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$valid_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$valid_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  GITHUB_ENV="$github_env" \
  "$RESTORE" >/dev/null || fail "valid release overlay was rejected"
[[ "$(<"$valid_root/app/release/google-services.json")" == '{"project_info":{"project_id":"fearless-release"}}' ]] ||
  fail "release Firebase bytes changed"
[[ "$(<"$valid_root/overlay/fearless-upload.jks")" == 'release-keystore' ]] ||
  fail "release keystore bytes changed"
[[ "$(mode_of "$valid_root/app/release/google-services.json")" == 600 ]] || fail "Firebase mode is not 0600"
[[ "$(mode_of "$valid_root/overlay/fearless-upload.jks")" == 600 ]] || fail "keystore mode is not 0600"
grep -Fxq "CI_KEYSTORE_PATH=$valid_root/overlay/fearless-upload.jks" "$github_env" ||
  fail "release overlay did not export CI_KEYSTORE_PATH"
if grep -Fq 'CI_PLAY_KEY=' "$github_env"; then
  fail "release-only overlay unexpectedly exported a Play credential"
fi

play_root="$tmp_dir/play"
mkdir -p "$play_root"
play_env="$play_root/github-env"
: > "$play_env"
chmod 600 "$play_env"
env \
  RELEASE_OVERLAY_MODE=play \
  RELEASE_OVERLAY_DIR="$play_root/overlay" \
  PLAY_SERVICE_ACCOUNT_JSON_B64="$play_b64" \
  GITHUB_ENV="$play_env" \
  "$RESTORE" >/dev/null || fail "valid Play-only overlay was rejected"
[[ "$(<"$play_root/overlay/play-service-account.json")" == '{"type":"service_account"}' ]] ||
  fail "Play credential bytes changed"
[[ "$(mode_of "$play_root/overlay/play-service-account.json")" == 600 ]] ||
  fail "Play credential mode is not 0600"
grep -Fxq "CI_PLAY_KEY=$play_root/overlay/play-service-account.json" "$play_env" ||
  fail "Play overlay did not export CI_PLAY_KEY"
[[ ! -e "$play_root/overlay/fearless-upload.jks" ]] ||
  fail "Play-only overlay unexpectedly restored a release keystore"

invalid_root="$tmp_dir/invalid-base64"
mkdir -p "$invalid_root/app/release"
printf '%s' 'preserve-me' > "$invalid_root/app/release/google-services.json"
chmod 600 "$invalid_root/app/release/google-services.json"
expect_failure "partially decodable Firebase base64" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$invalid_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$invalid_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64='dmFsaWQ=garbage' \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"
[[ "$(<"$invalid_root/app/release/google-services.json")" == 'preserve-me' ]] ||
  fail "invalid base64 partially overwrote the existing Firebase config"

invalid_keystore_root="$tmp_dir/invalid-keystore-base64"
mkdir -p "$invalid_keystore_root/app/release"
printf '%s' 'preserve-google-before-second-payload' > "$invalid_keystore_root/app/release/google-services.json"
chmod 600 "$invalid_keystore_root/app/release/google-services.json"
expect_failure "partially decodable keystore base64" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$invalid_keystore_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$invalid_keystore_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64='dmFsaWQ=garbage' \
  "$RESTORE"
[[ "$(<"$invalid_keystore_root/app/release/google-services.json")" == 'preserve-google-before-second-payload' ]] ||
  fail "invalid second payload committed the first release overlay"
[[ ! -e "$invalid_keystore_root/overlay/fearless-upload.jks" ]] ||
  fail "invalid keystore payload left a final keystore file"

symlink_root="$tmp_dir/output-symlink"
mkdir -p "$symlink_root/app/release"
printf '%s' 'symlink-target' > "$symlink_root/target"
ln -s "$symlink_root/target" "$symlink_root/app/release/google-services.json"
expect_failure "Firebase output symlink" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$symlink_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$symlink_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"
[[ "$(<"$symlink_root/target")" == 'symlink-target' ]] || fail "output symlink target was overwritten"

parent_symlink_root="$tmp_dir/parent-symlink"
mkdir -p "$parent_symlink_root/real-parent"
ln -s "$parent_symlink_root/real-parent" "$parent_symlink_root/linked-parent"
expect_failure "Firebase parent symlink" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$parent_symlink_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$parent_symlink_root/linked-parent/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"

keystore_symlink_root="$tmp_dir/keystore-symlink"
mkdir -p "$keystore_symlink_root/overlay" "$keystore_symlink_root/app/release"
printf '%s' 'keystore-target' > "$keystore_symlink_root/target"
ln -s "$keystore_symlink_root/target" "$keystore_symlink_root/overlay/fearless-upload.jks"
expect_failure "keystore output symlink" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$keystore_symlink_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$keystore_symlink_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"
[[ "$(<"$keystore_symlink_root/target")" == 'keystore-target' ]] || fail "keystore symlink target was overwritten"

sentinel_symlink_root="$tmp_dir/sentinel-symlink"
mkdir -p "$sentinel_symlink_root/overlay" "$sentinel_symlink_root/app/release"
printf '%s' 'sentinel-target' > "$sentinel_symlink_root/target"
ln -s "$sentinel_symlink_root/target" "$sentinel_symlink_root/overlay/.release-overlay-active"
expect_failure "release sentinel symlink" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$sentinel_symlink_root/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$sentinel_symlink_root/app/release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$google_b64" \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"
[[ "$(<"$sentinel_symlink_root/target")" == 'sentinel-target' ]] ||
  fail "release sentinel symlink target was truncated"
[[ ! -e "$sentinel_symlink_root/app/release/google-services.json" ]] ||
  fail "release sentinel rejection still committed Firebase credentials"
[[ ! -e "$sentinel_symlink_root/overlay/fearless-upload.jks" ]] ||
  fail "release sentinel rejection still committed a keystore"

oversized_play_b64="$(dd if=/dev/zero bs=65537 count=1 2>/dev/null | base64 | tr -d '\r\n')"
expect_failure "oversized Play credential" env \
  RELEASE_OVERLAY_MODE=play \
  RELEASE_OVERLAY_DIR="$tmp_dir/oversized-play" \
  PLAY_SERVICE_ACCOUNT_JSON_B64="$oversized_play_b64" \
  "$RESTORE"

expect_failure "empty release Firebase payload" env \
  RELEASE_OVERLAY_MODE=release \
  RELEASE_OVERLAY_DIR="$tmp_dir/empty-release/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$tmp_dir/empty-release/google-services.json" \
  GOOGLE_SERVICES_RELEASE_JSON_B64= \
  ANDROID_RELEASE_KEYSTORE_B64="$keystore_b64" \
  "$RESTORE"

expect_failure "missing Play credential" env \
  RELEASE_OVERLAY_MODE=play \
  RELEASE_OVERLAY_DIR="$tmp_dir/missing-play" \
  PLAY_SERVICE_ACCOUNT_JSON_B64= \
  "$RESTORE"

expect_failure "unknown restore mode" env \
  RELEASE_OVERLAY_MODE=all \
  RELEASE_OVERLAY_DIR="$tmp_dir/unknown" \
  "$RESTORE"

if find "$tmp_dir" -name '.fearless-release-overlay.*' -print -quit | grep -q .; then
  fail "a partial release overlay temporary file was left behind"
fi

echo "[release-overlay-test] passed (2 valid contracts plus $negative_count negative scenarios)"
