#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CLEANUP="$ROOT_DIR/scripts/cleanup-release-overlays.sh"
if [[ -d /private/tmp && ! -L /private/tmp ]]; then
  tmp_dir="$(mktemp -d /private/tmp/release-overlay-cleanup-test.XXXXXX)"
else
  tmp_dir="$(mktemp -d)"
fi
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[release-overlay-cleanup-test][error] $*" >&2
  exit 1
}

expect_failure() {
  local label="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    fail "$label was accepted"
  fi
}

fixture="$tmp_dir/fixture"
overlay="$fixture/overlay"
google="$fixture/app/release/google-services.json"
mkdir -p "$overlay" "$(dirname "$google")"
printf '%s' 'release-firebase-secret' > "$google"
printf '%s' 'release-keystore-secret' > "$overlay/fearless-upload.jks"
printf '%s' 'play-service-secret' > "$overlay/play-service-account.json"
: > "$overlay/.release-overlay-active"
: > "$overlay/.play-overlay-active"
chmod 600 "$google" "$overlay"/* "$overlay"/.release-overlay-active "$overlay"/.play-overlay-active

CI=true \
RELEASE_OVERLAY_DIR="$overlay" \
GOOGLE_SERVICES_RELEASE_PATH="$google" \
"$CLEANUP" >/dev/null || fail "valid CI cleanup was rejected"
for path in \
  "$google" \
  "$overlay/fearless-upload.jks" \
  "$overlay/play-service-account.json" \
  "$overlay/.release-overlay-active" \
  "$overlay/.play-overlay-active"; do
  [[ ! -e "$path" && ! -L "$path" ]] || fail "cleanup left $path"
done

no_sentinel="$tmp_dir/no-sentinel"
mkdir -p "$no_sentinel/overlay" "$no_sentinel/app/release"
printf '%s' 'public-placeholder' > "$no_sentinel/app/release/google-services.json"
CI=true \
RELEASE_OVERLAY_DIR="$no_sentinel/overlay" \
GOOGLE_SERVICES_RELEASE_PATH="$no_sentinel/app/release/google-services.json" \
"$CLEANUP" >/dev/null || fail "no-op cleanup was rejected"
[[ "$(<"$no_sentinel/app/release/google-services.json")" == 'public-placeholder' ]] ||
  fail "cleanup removed Firebase config without an active overlay sentinel"

expect_failure "cleanup outside CI" env \
  -u CI -u GITHUB_ACTIONS \
  RELEASE_OVERLAY_DIR="$overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$google" \
  "$CLEANUP"

symlink_parent="$tmp_dir/symlink-parent"
mkdir -p "$symlink_parent/real/overlay" "$symlink_parent/real/app/release"
printf '%s' 'preserve' > "$symlink_parent/real/app/release/google-services.json"
: > "$symlink_parent/real/overlay/.release-overlay-active"
ln -s "$symlink_parent/real" "$symlink_parent/linked"
expect_failure "cleanup through parent symlink" env \
  CI=true \
  RELEASE_OVERLAY_DIR="$symlink_parent/linked/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$symlink_parent/linked/app/release/google-services.json" \
  "$CLEANUP"
[[ "$(<"$symlink_parent/real/app/release/google-services.json")" == 'preserve' ]] ||
  fail "parent-symlink cleanup removed the target"

symlink_sentinel="$tmp_dir/symlink-sentinel"
mkdir -p "$symlink_sentinel/overlay" "$symlink_sentinel/app/release"
printf '%s' 'preserve-google' > "$symlink_sentinel/app/release/google-services.json"
printf '%s' 'preserve-keystore' > "$symlink_sentinel/overlay/fearless-upload.jks"
printf '%s' 'sentinel-target' > "$symlink_sentinel/sentinel-target"
ln -s "$symlink_sentinel/sentinel-target" "$symlink_sentinel/overlay/.release-overlay-active"
expect_failure "release sentinel symlink" env \
  CI=true \
  RELEASE_OVERLAY_DIR="$symlink_sentinel/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$symlink_sentinel/app/release/google-services.json" \
  "$CLEANUP"
[[ "$(<"$symlink_sentinel/app/release/google-services.json")" == 'preserve-google' ]] ||
  fail "malformed release sentinel cleanup removed Firebase credentials"
[[ "$(<"$symlink_sentinel/overlay/fearless-upload.jks")" == 'preserve-keystore' ]] ||
  fail "malformed release sentinel cleanup removed the keystore"
[[ "$(<"$symlink_sentinel/sentinel-target")" == 'sentinel-target' ]] ||
  fail "malformed release sentinel cleanup modified the symlink target"

directory_sentinel="$tmp_dir/directory-sentinel"
mkdir -p \
  "$directory_sentinel/overlay/.play-overlay-active" \
  "$directory_sentinel/app/release"
printf '%s' 'preserve-play' > "$directory_sentinel/overlay/play-service-account.json"
expect_failure "Play sentinel directory" env \
  CI=true \
  RELEASE_OVERLAY_DIR="$directory_sentinel/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$directory_sentinel/app/release/google-services.json" \
  "$CLEANUP"
[[ "$(<"$directory_sentinel/overlay/play-service-account.json")" == 'preserve-play' ]] ||
  fail "malformed Play sentinel cleanup removed the service-account credential"

echo "[release-overlay-cleanup-test] passed (2 valid contracts plus 4 negative scenarios)"
