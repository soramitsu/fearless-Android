#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
RESTORE="$ROOT_DIR/scripts/restore-release-overlays.sh"
CLEANUP="$ROOT_DIR/scripts/cleanup-release-overlays.sh"
mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(mktemp -d "$ROOT_DIR/build/test-tmp/release-overlay-interruption.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[release-overlay-interruption-test][error] $*" >&2
  exit 1
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

base64_one_line() {
  base64 < "$1" | tr -d '\n'
}

# Firebase is not part of the app since 4.3.2: the only overlay left is the signing keystore.
[[ -z "$(find "$ROOT_DIR/app" -maxdepth 3 -name google-services.json -print 2>/dev/null)" ]] ||
  fail "no google-services.json may exist under app/"

dummy_keystore="$tmp_dir/fearless-upload.jks"
printf 'test-only-keystore-material\n' > "$dummy_keystore"
dummy_keystore_b64="$(base64_one_line "$dummy_keystore")"
dummy_keystore_sha="$(hash_file "$dummy_keystore")"
legacy_release_json_b64="$(printf '%s' '{"project_info":{"project_id":"fearless-release-test"}}' | base64 | tr -d '\n')"

unsigned_aab="$tmp_dir/app-release-unsigned.aab"
signed_metadata_aab="$tmp_dir/app-release-already-signed.aab"
python3 - "$unsigned_aab" "$signed_metadata_aab" <<'PY'
import shutil
import sys
import zipfile

unsigned_path, signed_path = sys.argv[1:]
with zipfile.ZipFile(unsigned_path, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"manifest fixture\n")
    archive.writestr("base/dex/classes.dex", b"dex fixture\n")
shutil.copyfile(unsigned_path, signed_path)
with zipfile.ZipFile(signed_path, "a", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\n")
    archive.writestr("META-INF/FEARLESS.SF", b"Signature-Version: 1.0\n")
PY

case_count=0
cleanup_interruption_count=0
legacy_cleanup_count=0
split_positive_count=0
split_negative_count=0

assert_overlay_empty() {
  local overlay_dir="$1"
  local label="$2"
  local leftovers

  for forbidden_path in \
    "$overlay_dir/fearless-upload.jks" \
    "$overlay_dir/play-service-account.json" \
    "$overlay_dir/release-google-services.backup" \
    "$overlay_dir/.release-overlay-active" \
    "$overlay_dir/.signing-overlay-active" \
    "$overlay_dir/.play-overlay-active" \
    "$overlay_dir/.overlay-test-checkpoint" \
    "$overlay_dir/secret-temporary"; do
    [[ ! -e "$forbidden_path" && ! -L "$forbidden_path" ]] ||
      fail "$label left $forbidden_path behind"
  done
  shopt -s nullglob
  leftovers=("$overlay_dir"/.release-google-services.backup.tmp.*)
  shopt -u nullglob
  (( ${#leftovers[@]} == 0 )) || fail "$label left a legacy partial backup behind"
}

run_cleanup() {
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$1" \
    "$CLEANUP"
}

wait_for_checkpoint() {
  local pid="$1"
  local marker="$2"
  local checkpoint="$3"
  local output="$4"
  local label="$5"

  for _ in $(seq 1 200); do
    if [[ -f "$marker" ]] && [[ "$(tr -d '\r\n' < "$marker")" == "$checkpoint" ]]; then
      return 0
    fi
    kill -0 "$pid" 2>/dev/null || break
    sleep 0.05
  done
  kill -KILL "$pid" 2>/dev/null || true
  wait "$pid" 2>/dev/null || true
  sed -n '1,160p' "$output" >&2
  fail "$label did not reach the deterministic interruption point"
}

run_interruption_case() {
  local checkpoint="$1"
  local signal_name="$2"
  local case_dir="$tmp_dir/signing-${checkpoint}-${signal_name}"
  local overlay_dir="$case_dir/overlay"
  local output="$case_dir/restore.log"
  local restore_pid

  mkdir -p "$case_dir"
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    RELEASE_OVERLAY_TEST_MODE=true \
    RELEASE_OVERLAY_TEST_CHECKPOINT="$checkpoint" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE" > "$output" 2>&1 &
  restore_pid="$!"
  wait_for_checkpoint "$restore_pid" "$overlay_dir/.overlay-test-checkpoint" "$checkpoint" "$output" "signing/$checkpoint"

  kill "-$signal_name" "$restore_pid"
  wait "$restore_pid" 2>/dev/null || true

  run_cleanup "$overlay_dir" >/dev/null
  assert_overlay_empty "$overlay_dir" "signing/$checkpoint/$signal_name"
  case_count=$((case_count + 1))
}

run_cleanup_interruption_case() {
  local checkpoint="$1"
  local signal_name="$2"
  local case_dir="$tmp_dir/cleanup-${checkpoint}-${signal_name}"
  local overlay_dir="$case_dir/overlay"
  local output="$case_dir/cleanup.log"
  local cleanup_pid

  mkdir -p "$overlay_dir"
  chmod 700 "$overlay_dir"
  printf 'active\n' > "$overlay_dir/.signing-overlay-active"
  printf 'test-only-keystore-material\n' > "$overlay_dir/fearless-upload.jks"
  chmod 600 "$overlay_dir/.signing-overlay-active" "$overlay_dir/fearless-upload.jks"

  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    RELEASE_OVERLAY_CLEANUP_TEST_MODE=true \
    RELEASE_OVERLAY_CLEANUP_TEST_CHECKPOINT="$checkpoint" \
    "$CLEANUP" > "$output" 2>&1 &
  cleanup_pid="$!"
  wait_for_checkpoint "$cleanup_pid" "$overlay_dir/.overlay-test-checkpoint" "$checkpoint" "$output" "cleanup/$checkpoint"

  kill "-$signal_name" "$cleanup_pid"
  wait "$cleanup_pid" 2>/dev/null || true

  run_cleanup "$overlay_dir" >/dev/null
  assert_overlay_empty "$overlay_dir" "cleanup/$checkpoint/$signal_name"
  cleanup_interruption_count=$((cleanup_interruption_count + 1))
}

# An overlay directory a pre-4.3.2 run left behind: its Firebase backup, sentinel and partial
# copies are deleted, and nothing is written back into the source tree.
run_legacy_cleanup_case() {
  local variant="$1"
  local case_dir="$tmp_dir/legacy-$variant"
  local overlay_dir="$case_dir/overlay"
  local output="$case_dir/cleanup.log"

  mkdir -p "$overlay_dir"
  chmod 700 "$overlay_dir"
  case "$variant" in
    active)
      printf '{"project_info":{"project_id":"fearless-public"}}\n' > "$overlay_dir/release-google-services.backup"
      printf 'active\n' > "$overlay_dir/.release-overlay-active"
      ;;
    prepared-with-partial)
      printf 'prepared\n' > "$overlay_dir/.release-overlay-active"
      printf '{"proj' > "$overlay_dir/.release-google-services.backup.tmp.AbC123"
      ;;
    backup-only)
      printf '{"project_info":{}}\n' > "$overlay_dir/release-google-services.backup"
      ;;
    *) fail "unsupported legacy cleanup variant: $variant" ;;
  esac

  run_cleanup "$overlay_dir" > "$output" 2>&1 || {
    sed -n '1,80p' "$output" >&2
    fail "legacy/$variant cleanup failed"
  }
  assert_overlay_empty "$overlay_dir" "legacy/$variant"
  [[ -z "$(find "$ROOT_DIR/app" -maxdepth 3 -name google-services.json -print 2>/dev/null)" ]] ||
    fail "legacy/$variant cleanup wrote a Google Services configuration into app/"
  legacy_cleanup_count=$((legacy_cleanup_count + 1))
}

expect_split_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$tmp_dir/split-failure-$split_negative_count.log"

  if "$@" > "$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label omitted its fail-closed diagnostic"
  }
  split_negative_count=$((split_negative_count + 1))
}

expect_split_failure \
  "missing explicit overlay mode" \
  "must be exactly signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true "$RESTORE"
expect_split_failure \
  "legacy combined release mode" \
  "must be exactly signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true RELEASE_OVERLAY_MODE=release "$RESTORE"
expect_split_failure \
  "removed Play credential overlay mode" \
  "must be exactly signing" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=play \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    "$RESTORE"
expect_split_failure \
  "removed Firebase overlay mode" \
  "RELEASE_OVERLAY_MODE=firebase was removed" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$tmp_dir/firebase-overlay" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$legacy_release_json_b64" \
    "$RESTORE"
[[ ! -e "$tmp_dir/firebase-overlay" ]] ||
  fail "the removed Firebase mode created an overlay directory"

split_case="$tmp_dir/split"
split_overlay="$split_case/overlay"
mkdir -p "$split_case"
expect_split_failure \
  "signing overlay outside CI" \
  "restricted to an explicit CI environment" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with Firebase material" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$legacy_release_json_b64" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with a Firebase digest" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$dummy_keystore_sha" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with Play credential material" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay without completed AAB" \
  "ANDROID_UNSIGNED_AAB_PATH is required" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with already-signed AAB" \
  "already contains JAR signature metadata" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$signed_metadata_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
ln -s "$unsigned_aab" "$split_case/symlink-unsigned.aab"
expect_split_failure \
  "signing overlay with symlink AAB" \
  "paths must not traverse symlinks" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$split_case/symlink-unsigned.aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
malformed_aab="$split_case/malformed.aab"
printf 'not a zip\n' > "$malformed_aab"
expect_split_failure \
  "signing overlay with malformed AAB" \
  "not a valid ZIP archive" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$malformed_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay without keystore material" \
  "ANDROID_RELEASE_KEYSTORE_B64 is required" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with malformed keystore base64" \
  "ANDROID_RELEASE_KEYSTORE_B64 is not valid base64" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64='***not-base64***' \
    "$RESTORE"
run_cleanup "$split_overlay" >/dev/null

env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_MODE=signing \
  RELEASE_OVERLAY_DIR="$split_overlay" \
  ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
  ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
  "$RESTORE" >/dev/null
[[ "$(hash_file "$split_overlay/fearless-upload.jks")" == "$dummy_keystore_sha" &&
  "$(tr -d '\r\n' < "$split_overlay/.signing-overlay-active")" == "active" ]] ||
  fail "signing-only overlay did not publish the exact keystore"
[[ ! -e "$split_overlay/release-google-services.backup" &&
  ! -e "$split_overlay/.release-overlay-active" ]] ||
  fail "signing-only overlay created Firebase overlay state"
split_positive_count=$((split_positive_count + 1))

expect_split_failure \
  "duplicate signing overlay" \
  "signing-only overlay is already active" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
[[ "$(hash_file "$split_overlay/fearless-upload.jks")" == "$dummy_keystore_sha" ]] ||
  fail "duplicate signing restore destroyed the active keystore"

run_cleanup "$split_overlay" >/dev/null
assert_overlay_empty "$split_overlay" "split overlay cleanup"
split_positive_count=$((split_positive_count + 1))

for checkpoint in \
  after-keystore-decode \
  after-signing-sentinel \
  after-keystore-move; do
  run_interruption_case "$checkpoint" TERM
  run_interruption_case "$checkpoint" KILL
done

for checkpoint in \
  after-signing-sentinel-removal-before-keystore \
  after-signing-keystore-removal; do
  run_cleanup_interruption_case "$checkpoint" TERM
  run_cleanup_interruption_case "$checkpoint" KILL
done

for variant in active prepared-with-partial backup-only; do
  run_legacy_cleanup_case "$variant"
done

[[ "$case_count" == "6" ]] ||
  fail "expected 6 interruption cases; got $case_count"
[[ "$cleanup_interruption_count" == "4" ]] ||
  fail "expected 4 cleanup-interruption cases; got $cleanup_interruption_count"
[[ "$legacy_cleanup_count" == "3" ]] ||
  fail "expected 3 legacy Firebase cleanup cases; got $legacy_cleanup_count"
[[ "$split_positive_count" == "2" ]] ||
  fail "expected 2 split-overlay positive cases; got $split_positive_count"
[[ "$split_negative_count" == "15" ]] ||
  fail "expected 15 split-overlay negative cases; got $split_negative_count"

echo \
  "[release-overlay-interruption-test] $split_positive_count split positive, $split_negative_count split negative, $case_count restore-interruption, $cleanup_interruption_count cleanup-interruption, and $legacy_cleanup_count legacy-Firebase cleanup adversarial cases passed"
