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

file_size() {
  wc -c < "$1" | tr -d '[:space:]'
}

file_mode() {
  local mode

  if mode="$(stat -c '%a' "$1" 2>/dev/null)"; then
    printf '%s\n' "$mode"
  else
    stat -f '%Lp' "$1"
  fi
}

base64_one_line() {
  base64 < "$1" | tr -d '\n'
}

release_json="$tmp_dir/release-google-services.json"
printf '%s' \
  '{"project_info":{"project_id":"fearless-release-test"},"client":[{"client_info":{"android_client_info":{"package_name":"jp.co.soramitsu.fearless"}}}]}' \
  > "$release_json"
release_json_b64="$(base64_one_line "$release_json")"
release_json_sha256="$(hash_file "$release_json")"
dummy_keystore="$tmp_dir/fearless-upload.jks"
printf 'test-only-keystore-material\n' > "$dummy_keystore"
dummy_keystore_b64="$(base64_one_line "$dummy_keystore")"

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
backup_rejection_count=0
cleanup_interruption_count=0
split_positive_count=0
split_negative_count=0

run_interruption_case() {
  local mode="$1"
  local checkpoint="$2"
  local signal_name="$3"
  local case_dir="$tmp_dir/${mode}-${checkpoint}-${signal_name}"
  local overlay_dir="$case_dir/overlay"
  local firebase_fixture="$case_dir/google-services.json"
  local marker="$overlay_dir/.overlay-test-checkpoint"
  local output="$case_dir/restore.log"
  local before_sha
  local restore_pid
  local observed=false
  local backup_size
  local source_size
  local backup_temporaries

  mkdir -p "$case_dir"
  cp "$ROOT_DIR/app/src/release/google-services.json" "$firebase_fixture"
  before_sha="$(hash_file "$firebase_fixture")"

  if [[ "$mode" == "firebase" ]]; then
    env -i \
      PATH="$PATH" \
      HOME="$HOME" \
      CI=true \
      RELEASE_OVERLAY_MODE=firebase \
      RELEASE_OVERLAY_DIR="$overlay_dir" \
      GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
      RELEASE_OVERLAY_TEST_MODE=true \
      RELEASE_OVERLAY_TEST_CHECKPOINT="$checkpoint" \
      GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
      ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
      "$RESTORE" > "$output" 2>&1 &
  elif [[ "$mode" == "signing" ]]; then
    env -i \
      PATH="$PATH" \
      HOME="$HOME" \
      CI=true \
      RELEASE_OVERLAY_MODE=signing \
      RELEASE_OVERLAY_DIR="$overlay_dir" \
      GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
      RELEASE_OVERLAY_TEST_MODE=true \
      RELEASE_OVERLAY_TEST_CHECKPOINT="$checkpoint" \
      ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
      ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
      "$RESTORE" > "$output" 2>&1 &
  else
    fail "unsupported test mode: $mode"
  fi
  restore_pid="$!"

  for _ in $(seq 1 200); do
    if [[ -f "$marker" ]] &&
      [[ "$(tr -d '\r\n' < "$marker")" == "$checkpoint" ]]; then
      observed=true
      break
    fi
    if ! kill -0 "$restore_pid" 2>/dev/null; then
      break
    fi
    sleep 0.05
  done
  if [[ "$observed" != "true" ]]; then
    kill -KILL "$restore_pid" 2>/dev/null || true
    wait "$restore_pid" 2>/dev/null || true
    sed -n '1,160p' "$output" >&2
    fail "$mode/$checkpoint did not reach the deterministic interruption point"
  fi

  if [[ "$checkpoint" == "during-release-backup-copy" ]]; then
    shopt -s nullglob
    backup_temporaries=(
      "$overlay_dir"/.release-google-services.backup.tmp.*
    )
    shopt -u nullglob
    (( ${#backup_temporaries[@]} == 1 )) ||
      fail "$mode/$checkpoint did not expose exactly one partial backup"
    backup_size="$(file_size "${backup_temporaries[0]}")"
    source_size="$(file_size "$firebase_fixture")"
    (( backup_size > 0 && backup_size < source_size )) ||
      fail "$mode/$checkpoint did not stop during a partial copy"
    [[ ! -e "$overlay_dir/release-google-services.backup" ]] ||
      fail "$mode/$checkpoint published a partial canonical backup"
  elif [[ "$checkpoint" == "after-release-backup-copy" ||
    "$checkpoint" == "after-release-backup-validation" ]]; then
    shopt -s nullglob
    backup_temporaries=(
      "$overlay_dir"/.release-google-services.backup.tmp.*
    )
    shopt -u nullglob
    (( ${#backup_temporaries[@]} == 1 )) ||
      fail "$mode/$checkpoint did not retain exactly one temporary backup"
    [[ "$(hash_file "${backup_temporaries[0]}")" == "$before_sha" ]] ||
      fail "$mode/$checkpoint temporary backup did not match its source"
    [[ ! -e "$overlay_dir/release-google-services.backup" ]] ||
      fail "$mode/$checkpoint published the backup before validation completed"
  elif [[ "$checkpoint" == "after-release-backup" ]]; then
    [[ "$(hash_file "$overlay_dir/release-google-services.backup")" == "$before_sha" ]] ||
      fail "$mode/$checkpoint canonical backup did not match its source"
    [[ "$(file_mode "$overlay_dir/release-google-services.backup")" == "600" ]] ||
      fail "$mode/$checkpoint canonical backup mode was not 0600"
  fi

  kill "-$signal_name" "$restore_pid"
  wait "$restore_pid" 2>/dev/null || true

  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    "$CLEANUP" >/dev/null

  [[ "$(hash_file "$firebase_fixture")" == "$before_sha" ]] ||
    fail "$mode/$checkpoint/$signal_name did not restore the Firebase placeholder"

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
      fail "$mode/$checkpoint/$signal_name left $forbidden_path behind"
  done
  shopt -s nullglob
  backup_temporaries=(
    "$overlay_dir"/.release-google-services.backup.tmp.*
  )
  shopt -u nullglob
  (( ${#backup_temporaries[@]} == 0 )) ||
    fail "$mode/$checkpoint/$signal_name left a partial backup behind"
  case_count=$((case_count + 1))
}

run_backup_rejection_case() {
  local mutation="$1"
  local case_dir="$tmp_dir/reject-backup-$mutation"
  local overlay_dir="$case_dir/overlay"
  local firebase_fixture="$case_dir/google-services.json"
  local output="$case_dir/restore.log"
  local before_sha
  local backup_temporaries

  mkdir -p "$case_dir"
  cp "$ROOT_DIR/app/src/release/google-services.json" "$firebase_fixture"
  before_sha="$(hash_file "$firebase_fixture")"

  if env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    RELEASE_OVERLAY_TEST_MODE=true \
    RELEASE_OVERLAY_TEST_BACKUP_MUTATION="$mutation" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    "$RESTORE" > "$output" 2>&1; then
    fail "backup $mutation mutation was accepted"
  fi

  if [[ "$mutation" == "content" ]]; then
    grep -q 'SHA-256 integrity check' "$output" ||
      fail "backup content mutation did not fail at the SHA-256 gate"
  else
    grep -q 'permissions must be exactly 0600' "$output" ||
      fail "backup permissions mutation did not fail at the mode gate"
  fi
  [[ "$(hash_file "$firebase_fixture")" == "$before_sha" ]] ||
    fail "backup $mutation rejection changed the Firebase placeholder"
  [[ ! -e "$overlay_dir/release-google-services.backup" ]] ||
    fail "backup $mutation rejection published a canonical backup"
  shopt -s nullglob
  backup_temporaries=(
    "$overlay_dir"/.release-google-services.backup.tmp.*
  )
  shopt -u nullglob
  (( ${#backup_temporaries[@]} == 0 )) ||
    fail "backup $mutation rejection left a temporary backup"

  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    "$CLEANUP" >/dev/null

  echo \
    "[release-overlay-interruption-test] rejected backup $mutation mutation"
  backup_rejection_count=$((backup_rejection_count + 1))
}

run_cleanup_interruption_case() {
  local checkpoint="$1"
  local signal_name="$2"
  local case_dir="$tmp_dir/cleanup-${checkpoint}-${signal_name}"
  local overlay_dir="$case_dir/overlay"
  local firebase_fixture="$case_dir/google-services.json"
  local marker="$overlay_dir/.overlay-test-checkpoint"
  local output="$case_dir/cleanup.log"
  local before_sha
  local cleanup_pid
  local observed=false

  mkdir -p "$overlay_dir"
  chmod 700 "$overlay_dir"
  cp "$ROOT_DIR/app/src/release/google-services.json" "$firebase_fixture"
  before_sha="$(hash_file "$firebase_fixture")"
  cp "$firebase_fixture" "$overlay_dir/release-google-services.backup"
  chmod 600 "$overlay_dir/release-google-services.backup"
  cp "$release_json" "$firebase_fixture"
  printf 'active\n' > "$overlay_dir/.release-overlay-active"
  printf 'active\n' > "$overlay_dir/.signing-overlay-active"
  printf 'test-only-keystore-material\n' > "$overlay_dir/fearless-upload.jks"
  chmod 600 \
    "$overlay_dir/.release-overlay-active" \
    "$overlay_dir/.signing-overlay-active" \
    "$overlay_dir/fearless-upload.jks"

  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    RELEASE_OVERLAY_CLEANUP_TEST_MODE=true \
    RELEASE_OVERLAY_CLEANUP_TEST_CHECKPOINT="$checkpoint" \
    "$CLEANUP" > "$output" 2>&1 &
  cleanup_pid="$!"

  for _ in $(seq 1 200); do
    if [[ -f "$marker" ]] &&
      [[ "$(tr -d '\r\n' < "$marker")" == "$checkpoint" ]]; then
      observed=true
      break
    fi
    if ! kill -0 "$cleanup_pid" 2>/dev/null; then
      break
    fi
    sleep 0.05
  done
  if [[ "$observed" != "true" ]]; then
    kill -KILL "$cleanup_pid" 2>/dev/null || true
    wait "$cleanup_pid" 2>/dev/null || true
    sed -n '1,160p' "$output" >&2
    fail "cleanup/$checkpoint did not reach the interruption point"
  fi

  kill "-$signal_name" "$cleanup_pid"
  wait "$cleanup_pid" 2>/dev/null || true

  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    "$CLEANUP" >/dev/null

  [[ "$(hash_file "$firebase_fixture")" == "$before_sha" ]] ||
    fail "cleanup/$checkpoint/$signal_name did not restore the Firebase placeholder"
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
      fail "cleanup/$checkpoint/$signal_name left $forbidden_path behind"
  done

  cleanup_interruption_count=$((cleanup_interruption_count + 1))
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
  "must be exactly firebase or signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true "$RESTORE"
expect_split_failure \
  "legacy combined release mode" \
  "must be exactly firebase or signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true RELEASE_OVERLAY_MODE=release "$RESTORE"
expect_split_failure \
  "removed Play credential overlay mode" \
  "must be exactly firebase or signing" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=play \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    "$RESTORE"

forbidden_case="$tmp_dir/split-forbidden-secret"
mkdir -p "$forbidden_case"
cp "$ROOT_DIR/app/src/release/google-services.json" "$forbidden_case/google-services.json"
expect_split_failure \
  "Firebase overlay with signing material" \
  "Firebase-only overlay forbids signing and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$forbidden_case/overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$forbidden_case/google-services.json" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "Firebase overlay with Play credential material" \
  "Firebase-only overlay forbids signing and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$forbidden_case/overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$forbidden_case/google-services.json" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    "$RESTORE"

digest_case="$tmp_dir/firebase-digest"
mkdir -p "$digest_case"
cp "$ROOT_DIR/app/src/release/google-services.json" "$digest_case/google-services.json"
digest_before_sha="$(hash_file "$digest_case/google-services.json")"
expect_split_failure \
  "Firebase overlay without approved digest" \
  "ANDROID_RELEASE_FIREBASE_JSON_SHA256 is required" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$digest_case/overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$digest_case/google-services.json" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    "$RESTORE"
expect_split_failure \
  "Firebase overlay with malformed approved digest" \
  "must be an exact lowercase SHA-256 digest" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$digest_case/overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$digest_case/google-services.json" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256=ABC \
    "$RESTORE"
expect_split_failure \
  "Firebase overlay with mismatched approved digest" \
  "does not match the approved Firebase SHA-256 digest" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$digest_case/overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$digest_case/google-services.json" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    "$RESTORE"
env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_DIR="$digest_case/overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$digest_case/google-services.json" \
  "$CLEANUP" >/dev/null
[[ "$(hash_file "$digest_case/google-services.json")" == "$digest_before_sha" ]] ||
  fail "Firebase digest rejection did not preserve the checked-in placeholder"

split_case="$tmp_dir/split-positive"
split_overlay="$split_case/overlay"
split_firebase="$split_case/google-services.json"
mkdir -p "$split_case"
cp "$ROOT_DIR/app/src/release/google-services.json" "$split_firebase"
split_before_sha="$(hash_file "$split_firebase")"
env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_MODE=firebase \
  RELEASE_OVERLAY_DIR="$split_overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
  ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
  "$RESTORE" >/dev/null
[[ -s "$split_overlay/release-google-services.backup" &&
  -s "$split_overlay/.release-overlay-active" ]] ||
  fail "Firebase-only overlay did not become active"
[[ ! -e "$split_overlay/fearless-upload.jks" &&
  ! -e "$split_overlay/.signing-overlay-active" ]] ||
  fail "Firebase-only overlay exposed signing material"
[[ "$(hash_file "$split_firebase")" == "$(hash_file "$release_json")" ]] ||
  fail "Firebase-only overlay did not publish the production fixture"
split_positive_count=$((split_positive_count + 1))

expect_split_failure \
  "duplicate Firebase overlay" \
  "Firebase release overlay is already active" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    "$RESTORE"
[[ -s "$split_overlay/release-google-services.backup" &&
  "$(hash_file "$split_firebase")" == "$(hash_file "$release_json")" ]] ||
  fail "duplicate Firebase restore destroyed the active transaction"

env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_DIR="$split_overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
  "$CLEANUP" >/dev/null
[[ "$(hash_file "$split_firebase")" == "$split_before_sha" &&
  ! -e "$split_overlay/release-google-services.backup" &&
  ! -e "$split_overlay/.release-overlay-active" ]] ||
  fail "Firebase-only cleanup did not restore an independent signing workspace"

expect_split_failure \
  "signing overlay outside explicit CI" \
  "signing overlay is restricted to an explicit CI environment" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with GitHub marker but no explicit CI" \
  "signing overlay is restricted to an explicit CI environment" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    GITHUB_ACTIONS=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_split_failure \
  "signing overlay with Firebase credential material" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i \
    PATH="$PATH" \
    HOME="$HOME" \
    CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$split_overlay" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
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
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
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
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
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
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
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
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
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

env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_MODE=signing \
  RELEASE_OVERLAY_DIR="$split_overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
  ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
  ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
  "$RESTORE" >/dev/null
split_keystore_sha="$(hash_file "$split_overlay/fearless-upload.jks")"
dummy_keystore_sha="$(hash_file "$dummy_keystore")"
signing_sentinel_state="$(
  tr -d '\r\n' < "$split_overlay/.signing-overlay-active"
)"
[[ "$split_keystore_sha" == "$dummy_keystore_sha" &&
  "$signing_sentinel_state" == "active" ]] ||
  fail "signing-only overlay did not publish the exact keystore"
[[ "$(hash_file "$split_firebase")" == "$split_before_sha" &&
  ! -e "$split_overlay/release-google-services.backup" &&
  ! -e "$split_overlay/.release-overlay-active" ]] ||
  fail "signing-only overlay touched the independent Firebase workspace"
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
    GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
[[ "$(hash_file "$split_overlay/fearless-upload.jks")" == "$dummy_keystore_sha" ]] ||
  fail "duplicate signing restore destroyed the active keystore"

env -i \
  PATH="$PATH" \
  HOME="$HOME" \
  CI=true \
  RELEASE_OVERLAY_DIR="$split_overlay" \
  GOOGLE_SERVICES_RELEASE_PATH="$split_firebase" \
  "$CLEANUP" >/dev/null
[[ "$(hash_file "$split_firebase")" == "$split_before_sha" ]] ||
  fail "split overlay cleanup did not restore the Firebase placeholder"

for checkpoint in \
  after-google-decode \
  during-release-backup-copy \
  after-release-backup-copy \
  after-release-backup-validation \
  after-release-backup \
  after-release-sentinel \
  after-google-move; do
  run_interruption_case firebase "$checkpoint" TERM
  run_interruption_case firebase "$checkpoint" KILL
done

for checkpoint in \
  after-keystore-decode \
  after-signing-sentinel \
  after-keystore-move; do
  run_interruption_case signing "$checkpoint" TERM
  run_interruption_case signing "$checkpoint" KILL
done

run_backup_rejection_case content
run_backup_rejection_case permissions

for checkpoint in \
  after-signing-sentinel-removal-before-keystore \
  after-signing-keystore-removal \
  after-release-sentinel-removal-before-restore \
  after-release-backup-restore; do
  run_cleanup_interruption_case "$checkpoint" TERM
  run_cleanup_interruption_case "$checkpoint" KILL
done

[[ "$case_count" == "20" ]] ||
  fail "expected 20 interruption cases; got $case_count"
[[ "$backup_rejection_count" == "2" ]] ||
  fail "expected 2 backup-integrity cases; got $backup_rejection_count"
[[ "$cleanup_interruption_count" == "8" ]] ||
  fail "expected 8 cleanup-interruption cases; got $cleanup_interruption_count"
[[ "$split_positive_count" == "2" ]] ||
  fail "expected 2 split-overlay positive cases; got $split_positive_count"
[[ "$split_negative_count" == "20" ]] ||
  fail "expected 20 split-overlay negative cases; got $split_negative_count"

echo \
  "[release-overlay-interruption-test] $split_positive_count split positive, $split_negative_count split negative, $case_count restore-interruption, $cleanup_interruption_count cleanup-interruption, and $backup_rejection_count backup-integrity adversarial cases passed"
