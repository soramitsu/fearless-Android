#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PRODUCTION_SIGNER="$ROOT_DIR/scripts/sign-android-release-aab.sh"
PRODUCTION_VERIFIER="$ROOT_DIR/scripts/verify-android-aab-jar-signature.sh"
PRODUCTION_FINGERPRINT="40391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-$ROOT_DIR/build/test-tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-release-aab-signer.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-release-signer-test][error] $*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

for command_name in \
  awk \
  cp \
  grep \
  jarsigner \
  keytool \
  mktemp \
  python3 \
  sed; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

keystore_store_password="test-store-password"
keystore_key_password="test-key-password"
approved_keystore="$tmp_dir/approved.jks"
wrong_keystore="$tmp_dir/wrong.jks"

generate_keystore() {
  local keystore="$1"
  local alias_name="$2"
  local common_name="$3"

  keytool \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -genkeypair \
    -noprompt \
    -alias "$alias_name" \
    -keyalg RSA \
    -keysize 2048 \
    -sigalg SHA256withRSA \
    -validity 3650 \
    -dname "CN=$common_name,O=Fearless Test,C=US" \
    -keystore "$keystore" \
    -storetype JKS \
    -storepass "$keystore_store_password" \
    -keypass "$keystore_key_password" >/dev/null 2>&1
}

keystore_fingerprint() {
  local keystore="$1"
  local alias_name="$2"

  keytool \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -list \
    -v \
    -keystore "$keystore" \
    -storepass "$keystore_store_password" \
    -alias "$alias_name" 2>/dev/null |
    awk -F'SHA256:' '/SHA256:/ { print $2; exit }' |
    tr -d '[:space:]:' |
    tr '[:lower:]' '[:upper:]'
}

sign_fixture() {
  local artifact="$1"
  local keystore="$2"
  local alias_name="$3"
  local signature_name="$4"

  LC_ALL=C jarsigner \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -keystore "$keystore" \
    -storepass "$keystore_store_password" \
    -keypass "$keystore_key_password" \
    -digestalg SHA-256 \
    -sigfile "$signature_name" \
    "$artifact" \
    "$alias_name" >/dev/null 2>&1
}

generate_keystore "$approved_keystore" approved "Approved Upload"
generate_keystore "$wrong_keystore" wrong "Wrong Upload"
approved_fingerprint="$(keystore_fingerprint "$approved_keystore" approved)"

harness_root="$tmp_dir/harness"
mkdir -p "$harness_root/scripts"
sed \
  "s/$PRODUCTION_FINGERPRINT/$approved_fingerprint/g" \
  "$PRODUCTION_SIGNER" > "$harness_root/scripts/sign-android-release-aab.sh"
cp "$PRODUCTION_VERIFIER" \
  "$harness_root/scripts/verify-android-aab-jar-signature.sh"
chmod 700 \
  "$harness_root/scripts/sign-android-release-aab.sh" \
  "$harness_root/scripts/verify-android-aab-jar-signature.sh"
TEST_SIGNER="$harness_root/scripts/sign-android-release-aab.sh"
TEST_VERIFIER="$harness_root/scripts/verify-android-aab-jar-signature.sh"
[[ "$(grep -Foc "$approved_fingerprint" "$TEST_SIGNER")" == "1" &&
  "$(grep -Foc "$PRODUCTION_FINGERPRINT" "$PRODUCTION_SIGNER")" == "1" ]] ||
  fail "test harness did not replace exactly one certificate pin"

unsigned_aab="$tmp_dir/unsigned.aab"
python3 - "$unsigned_aab" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"manifest fixture\n")
    archive.writestr("base/dex/classes.dex", b"dex fixture\n")
    archive.writestr("base/assets/config.json", b'{"safe":true}\n')
PY
unsigned_sha256="$(sha256_file "$unsigned_aab")"

base_signer_environment=(
  env
  CI=true
  CI_KEYSTORE_PATH="$approved_keystore"
  CI_KEYSTORE_PASS="$keystore_store_password"
  CI_KEYSTORE_KEY_ALIAS=approved
  CI_KEYSTORE_KEY_PASS="$keystore_key_password"
  ANDROID_UNSIGNED_AAB_SHA256="$unsigned_sha256"
)

positive_count=0
negative_count=0
interruption_count=0

signed_aab="$tmp_dir/signed.aab"
signer_output="$tmp_dir/signer-positive.log"
"${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$unsigned_aab" "$signed_aab" >"$signer_output"
"$TEST_VERIFIER" "$signed_aab" "$approved_fingerprint" >/dev/null
[[ "$(sha256_file "$unsigned_aab")" == "$unsigned_sha256" ]] ||
  fail "positive signing changed the unsigned input"
emitted_signed_sha256="$(
  sed -n 's/^\[android-release-signer\] signed-aab-sha256=//p' \
    "$signer_output"
)"
[[ "$emitted_signed_sha256" =~ ^[0-9a-f]{64}$ &&
  "$(grep -c '^\[android-release-signer\] signed-aab-sha256=' \
    "$signer_output")" == "1" &&
  "$emitted_signed_sha256" == "$(sha256_file "$signed_aab")" ]] ||
  fail "signer did not emit exactly one trusted digest for its published bytes"
positive_count=$((positive_count + 1))

expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"

  if "$@" > "$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label omitted its fail-closed diagnostic"
  }
  negative_count=$((negative_count + 1))
}

expect_failure \
  "signing outside CI" \
  "restricted to an explicit CI environment" \
  "${base_signer_environment[@]}" CI= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/no-ci.aab"
expect_failure \
  "missing unsigned digest" \
  "ANDROID_UNSIGNED_AAB_SHA256 is required" \
  "${base_signer_environment[@]}" ANDROID_UNSIGNED_AAB_SHA256= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-digest.aab"
expect_failure \
  "malformed unsigned digest" \
  "must be an exact lowercase SHA-256 digest" \
  "${base_signer_environment[@]}" ANDROID_UNSIGNED_AAB_SHA256=ABC \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/malformed-digest.aab"
expect_failure \
  "wrong unsigned digest" \
  "does not match ANDROID_UNSIGNED_AAB_SHA256" \
  "${base_signer_environment[@]}" \
  ANDROID_UNSIGNED_AAB_SHA256=ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/wrong-digest.aab"
expect_failure \
  "missing unsigned AAB" \
  "must be a non-empty regular, non-symlink file" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$tmp_dir/missing.aab" "$tmp_dir/missing-input.aab"
ln -s "$unsigned_aab" "$tmp_dir/symlink-input.aab"
expect_failure \
  "symlink unsigned AAB" \
  "paths must not traverse symlinks" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$tmp_dir/symlink-input.aab" "$tmp_dir/symlink-input-output.aab"
expect_failure \
  "same input and output" \
  "paths must be different" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$unsigned_aab" "$unsigned_aab"
printf 'existing output\n' > "$tmp_dir/existing-output.aab"
expect_failure \
  "preexisting signed output" \
  "must not already exist or be a symlink" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/existing-output.aab"
ln -s "$tmp_dir/nonexistent-target" "$tmp_dir/symlink-output.aab"
expect_failure \
  "symlink signed output" \
  "paths must not traverse symlinks" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/symlink-output.aab"
expect_failure \
  "missing output parent" \
  "parent directory is missing or unsafe" \
  "${base_signer_environment[@]}" \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-parent/output.aab"

signed_input="$tmp_dir/already-signed.aab"
cp "$unsigned_aab" "$signed_input"
sign_fixture "$signed_input" "$approved_keystore" approved FIRST
expect_failure \
  "already-signed input" \
  "already signed or contains JAR signature metadata" \
  "${base_signer_environment[@]}" \
  ANDROID_UNSIGNED_AAB_SHA256="$(sha256_file "$signed_input")" \
  "$TEST_SIGNER" "$signed_input" "$tmp_dir/resigned.aab"

multiple_signer_input="$tmp_dir/multiple-signers.aab"
cp "$signed_input" "$multiple_signer_input"
sign_fixture "$multiple_signer_input" "$wrong_keystore" wrong SECOND
expect_failure \
  "multiple-signer input" \
  "already signed or contains JAR signature metadata" \
  "${base_signer_environment[@]}" \
  ANDROID_UNSIGNED_AAB_SHA256="$(sha256_file "$multiple_signer_input")" \
  "$TEST_SIGNER" "$multiple_signer_input" "$tmp_dir/multiple-resigned.aab"

duplicate_input="$tmp_dir/duplicate-entry.aab"
cp "$unsigned_aab" "$duplicate_input"
python3 - "$duplicate_input" <<'PY'
import sys
import warnings
import zipfile

with zipfile.ZipFile(sys.argv[1], "a") as archive:
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        archive.writestr("base/assets/config.json", b'{"duplicate":true}\n')
PY
expect_failure \
  "duplicate unsigned ZIP entry" \
  "contains duplicate ZIP entries" \
  "${base_signer_environment[@]}" \
  ANDROID_UNSIGNED_AAB_SHA256="$(sha256_file "$duplicate_input")" \
  "$TEST_SIGNER" "$duplicate_input" "$tmp_dir/duplicate-signed.aab"

expect_failure \
  "wrong upload certificate" \
  "does not match the registered Google Play upload certificate" \
  "${base_signer_environment[@]}" \
  CI_KEYSTORE_PATH="$wrong_keystore" \
  CI_KEYSTORE_KEY_ALIAS=wrong \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/wrong-cert.aab"
expect_failure \
  "missing keystore path" \
  "CI_KEYSTORE_PATH is required" \
  "${base_signer_environment[@]}" CI_KEYSTORE_PATH= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-keystore.aab"
ln -s "$approved_keystore" "$tmp_dir/symlink-keystore.jks"
expect_failure \
  "symlink keystore" \
  "paths must not traverse symlinks" \
  "${base_signer_environment[@]}" CI_KEYSTORE_PATH="$tmp_dir/symlink-keystore.jks" \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/symlink-keystore.aab"
expect_failure \
  "missing store password" \
  "CI_KEYSTORE_PASS is required" \
  "${base_signer_environment[@]}" CI_KEYSTORE_PASS= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-storepass.aab"
expect_failure \
  "missing key alias" \
  "CI_KEYSTORE_KEY_ALIAS is required" \
  "${base_signer_environment[@]}" CI_KEYSTORE_KEY_ALIAS= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-alias.aab"
expect_failure \
  "missing key password" \
  "CI_KEYSTORE_KEY_PASS is required" \
  "${base_signer_environment[@]}" CI_KEYSTORE_KEY_PASS= \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/missing-keypass.aab"
expect_failure \
  "wrong store password" \
  "keystore or alias could not be opened" \
  "${base_signer_environment[@]}" CI_KEYSTORE_PASS=wrong-password \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/wrong-storepass.aab"
expect_failure \
  "wrong key password" \
  "jarsigner could not sign" \
  "${base_signer_environment[@]}" CI_KEYSTORE_KEY_PASS=wrong-password \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/wrong-keypass.aab"
expect_failure \
  "wrong alias" \
  "keystore or alias could not be opened" \
  "${base_signer_environment[@]}" CI_KEYSTORE_KEY_ALIAS=missing \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/wrong-alias.aab"
expect_failure \
  "malformed alias" \
  "CI_KEYSTORE_KEY_ALIAS is malformed" \
  "${base_signer_environment[@]}" CI_KEYSTORE_KEY_ALIAS=$'approved\ninjected' \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/malformed-alias.aab"

for mutation in \
  tamper-entry \
  append-entry \
  remove-entry; do
  expect_failure \
    "$mutation after private signing" \
    "Non-signature ZIP entry payloads changed while signing" \
    "${base_signer_environment[@]}" \
    ANDROID_AAB_SIGNER_TEST_MODE=true \
    ANDROID_AAB_SIGNER_TEST_MUTATION="$mutation" \
    "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/$mutation.aab"
done
expect_failure \
  "second signature block after private signing" \
  "must contain exactly one JAR signer" \
  "${base_signer_environment[@]}" \
  ANDROID_AAB_SIGNER_TEST_MODE=true \
  ANDROID_AAB_SIGNER_TEST_MUTATION=duplicate-signature-block \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/duplicate-signature.aab"
expect_failure \
  "unsupported signer mutation" \
  "Unsupported signer test mutation" \
  "${base_signer_environment[@]}" \
  ANDROID_AAB_SIGNER_TEST_MODE=true \
  ANDROID_AAB_SIGNER_TEST_MUTATION=unsupported \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/unsupported-mutation.aab"
expect_failure \
  "mutation outside guarded test mode" \
  "mutations are restricted to explicit CI tests" \
  "${base_signer_environment[@]}" \
  ANDROID_AAB_SIGNER_TEST_MUTATION=tamper-entry \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/unguarded-mutation.aab"
expect_failure \
  "checkpoint outside guarded test mode" \
  "checkpoints are restricted to explicit CI tests" \
  "${base_signer_environment[@]}" \
  ANDROID_AAB_SIGNER_TEST_CHECKPOINT=after-input-validation \
  ANDROID_AAB_SIGNER_TEST_MARKER="$tmp_dir/unguarded-marker" \
  "$TEST_SIGNER" "$unsigned_aab" "$tmp_dir/unguarded-checkpoint.aab"

post_signature_case="$tmp_dir/post-signature-digest-swap"
post_signature_output="$post_signature_case/signed.aab"
post_signature_marker="$post_signature_case/checkpoint"
post_signature_resume="$post_signature_case/resume"
post_signature_log="$post_signature_case/signer.log"
mkdir -p "$post_signature_case"
"${base_signer_environment[@]}" \
  ANDROID_AAB_SIGNER_TEST_MODE=true \
  ANDROID_AAB_SIGNER_TEST_CHECKPOINT=after-signature-verification \
  ANDROID_AAB_SIGNER_TEST_MARKER="$post_signature_marker" \
  ANDROID_AAB_SIGNER_TEST_RESUME="$post_signature_resume" \
  "$TEST_SIGNER" "$unsigned_aab" "$post_signature_output" \
  >"$post_signature_log" 2>&1 &
post_signature_pid="$!"
post_signature_observed=false
for _ in $(seq 1 300); do
  if [[ -f "$post_signature_marker" ]] &&
    [[ "$(tr -d '\r\n' <"$post_signature_marker")" == "after-signature-verification" ]]; then
    post_signature_observed=true
    break
  fi
  if ! kill -0 "$post_signature_pid" 2>/dev/null; then
    break
  fi
  sleep 0.05
done
if [[ "$post_signature_observed" != "true" ]]; then
  kill -KILL "$post_signature_pid" 2>/dev/null || true
  wait "$post_signature_pid" 2>/dev/null || true
  sed -n '1,160p' "$post_signature_log" >&2
  fail "post-signature digest swap did not reach its deterministic checkpoint"
fi
shopt -s nullglob
post_signature_temporaries=("$post_signature_case"/.signed.aab.signing.*)
shopt -u nullglob
[[ "${#post_signature_temporaries[@]}" == "1" ]] ||
  fail "post-signature digest swap could not identify one private snapshot"
printf '\npost-signature-tamper\n' >>"${post_signature_temporaries[0]}"
printf 'resume\n' >"$post_signature_resume"
if wait "$post_signature_pid"; then
  fail "post-signature digest swap unexpectedly succeeded"
fi
grep -Fq "verified signed AAB changed before atomic publication" \
  "$post_signature_log" || {
    sed -n '1,160p' "$post_signature_log" >&2
    fail "post-signature digest swap omitted its fail-closed diagnostic"
  }
[[ ! -e "$post_signature_output" && ! -L "$post_signature_output" ]] ||
  fail "post-signature digest swap published unverified bytes"
negative_count=$((negative_count + 1))

run_interruption_case() {
  local checkpoint="$1"
  local signal_name="$2"
  local case_dir="$tmp_dir/interruption-$checkpoint-$signal_name"
  local output_aab="$case_dir/signed.aab"
  local marker="$case_dir/checkpoint"
  local log="$case_dir/signer.log"
  local signer_pid
  local observed=false
  local hidden_temporaries

  mkdir -p "$case_dir"
  "${base_signer_environment[@]}" \
    ANDROID_AAB_SIGNER_TEST_MODE=true \
    ANDROID_AAB_SIGNER_TEST_CHECKPOINT="$checkpoint" \
    ANDROID_AAB_SIGNER_TEST_MARKER="$marker" \
    "$TEST_SIGNER" "$unsigned_aab" "$output_aab" > "$log" 2>&1 &
  signer_pid="$!"

  for _ in $(seq 1 300); do
    if [[ -f "$marker" ]] &&
      [[ "$(tr -d '\r\n' < "$marker")" == "$checkpoint" ]]; then
      observed=true
      break
    fi
    if ! kill -0 "$signer_pid" 2>/dev/null; then
      break
    fi
    sleep 0.05
  done
  if [[ "$observed" != "true" ]]; then
    kill -KILL "$signer_pid" 2>/dev/null || true
    wait "$signer_pid" 2>/dev/null || true
    sed -n '1,160p' "$log" >&2
    fail "$checkpoint/$signal_name did not reach its deterministic signer checkpoint"
  fi

  if [[ "$checkpoint" == "after-atomic-publish" ]]; then
    [[ -f "$output_aab" && ! -L "$output_aab" ]] ||
      fail "$checkpoint/$signal_name exposed no complete output"
    "$TEST_VERIFIER" "$output_aab" "$approved_fingerprint" >/dev/null
  else
    [[ ! -e "$output_aab" && ! -L "$output_aab" ]] ||
      fail "$checkpoint/$signal_name exposed a partial signed output"
  fi

  kill "-$signal_name" "$signer_pid"
  wait "$signer_pid" 2>/dev/null || true

  if [[ "$checkpoint" == "after-atomic-publish" ]]; then
    "$TEST_VERIFIER" "$output_aab" "$approved_fingerprint" >/dev/null
  else
    [[ ! -e "$output_aab" && ! -L "$output_aab" ]] ||
      fail "$checkpoint/$signal_name published output after interruption"
    "${base_signer_environment[@]}" \
      "$TEST_SIGNER" "$unsigned_aab" "$output_aab" >/dev/null
    "$TEST_VERIFIER" "$output_aab" "$approved_fingerprint" >/dev/null
  fi

  shopt -s nullglob
  hidden_temporaries=("$case_dir"/.signed.aab.signing.*)
  shopt -u nullglob
  if [[ "$signal_name" == "TERM" ]]; then
    (( ${#hidden_temporaries[@]} == 0 )) ||
      fail "$checkpoint/$signal_name left a private signing temporary"
  else
    (( ${#hidden_temporaries[@]} <= 1 )) ||
      fail "$checkpoint/$signal_name left multiple private signing temporaries"
  fi
  interruption_count=$((interruption_count + 1))
}

for checkpoint in \
  after-input-validation \
  after-keystore-validation \
  after-snapshot-copy \
  after-jarsigner \
  after-preservation-verification \
  after-signature-verification \
  after-atomic-publish; do
  run_interruption_case "$checkpoint" TERM
  run_interruption_case "$checkpoint" KILL
done

[[ "$positive_count" == "1" ]] ||
  fail "expected 1 positive case; got $positive_count"
[[ "$negative_count" == "31" ]] ||
  fail "expected 31 negative/adversarial cases; got $negative_count"
[[ "$interruption_count" == "14" ]] ||
  fail "expected 14 interruption cases; got $interruption_count"

echo \
  "[android-release-signer-test] $positive_count positive + $negative_count negative/adversarial + $interruption_count TERM/KILL cases passed"
