#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PRODUCTION_VERIFIER="$ROOT_DIR/scripts/verify-android-play-release.sh"
PRODUCTION_BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-$ROOT_DIR/build/test-tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(
  mktemp -d "$test_tmp_root/android-play-verifier-snapshot.XXXXXX"
)"
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

fail() {
  echo "[android-play-verifier-snapshot-test][error] $*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

for command_name in awk chmod cmp cp grep mktemp mv python3 sed seq sleep tr; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
[[ -f "$PRODUCTION_VERIFIER" && ! -L "$PRODUCTION_VERIFIER" ]] ||
  fail "production Play release verifier is missing or unsafe"

bundletool_fixture="$tmp_dir/bundletool-fixture.jar"
printf 'deterministic bundletool fixture\n' >"$bundletool_fixture"
bundletool_fixture_sha256="$(sha256_file "$bundletool_fixture")"

harness_root="$tmp_dir/harness"
mkdir -p "$harness_root/scripts"
sed \
  "s/$PRODUCTION_BUNDLETOOL_SHA256/$bundletool_fixture_sha256/g" \
  "$PRODUCTION_VERIFIER" >"$harness_root/scripts/verify-android-play-release.sh"

cat >"$harness_root/scripts/verify-android-aab-jar-signature.sh" <<'FAKE_SIGNATURE'
#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
[[ "$artifact" != "$FAKE_SOURCE_AAB" ]] || {
  echo "signature verifier received the mutable source path" >&2
  exit 1
}
if command -v sha256sum >/dev/null 2>&1; then
  digest="$(sha256sum "$artifact" | awk '{print $1}')"
else
  digest="$(shasum -a 256 "$artifact" | awk '{print $1}')"
fi
[[ "$digest" == "$FAKE_EXPECTED_SNAPSHOT_SHA256" ]] || {
  echo "signature verifier received unexpected snapshot bytes" >&2
  exit 1
}
printf '%s\t%s\n' "$artifact" "$digest" >"$FAKE_SIGNATURE_LOG"
FAKE_SIGNATURE

cat >"$harness_root/scripts/verify-android-aab-identity.sh" <<'FAKE_IDENTITY'
#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
[[ "$artifact" != "$FAKE_SOURCE_AAB" ]] || {
  echo "identity verifier received the mutable source path" >&2
  exit 1
}
if command -v sha256sum >/dev/null 2>&1; then
  digest="$(sha256sum "$artifact" | awk '{print $1}')"
else
  digest="$(shasum -a 256 "$artifact" | awk '{print $1}')"
fi
[[ "$digest" == "$FAKE_EXPECTED_SNAPSHOT_SHA256" ]] || {
  echo "identity verifier received unexpected snapshot bytes" >&2
  exit 1
}
printf '%s\t%s\n' "$artifact" "$digest" >"$FAKE_IDENTITY_LOG"
echo "[android-aab-identity] private snapshot fixture verified"
FAKE_IDENTITY

chmod 700 \
  "$harness_root/scripts/verify-android-play-release.sh" \
  "$harness_root/scripts/verify-android-aab-jar-signature.sh" \
  "$harness_root/scripts/verify-android-aab-identity.sh"
TEST_VERIFIER="$harness_root/scripts/verify-android-play-release.sh"

approved_aab="$tmp_dir/approved.aab"
replacement_aab="$tmp_dir/replacement.aab"
python3 - "$approved_aab" "$replacement_aab" <<'PY'
import sys
import zipfile

approved, replacement = sys.argv[1:]
with zipfile.ZipFile(approved, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"approved manifest\n")
    archive.writestr("base/dex/classes.dex", b"approved dex\n")
with zipfile.ZipFile(replacement, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"replacement manifest\n")
    archive.writestr("base/dex/classes.dex", b"replacement dex\n")
PY
approved_sha256="$(sha256_file "$approved_aab")"
replacement_sha256="$(sha256_file "$replacement_aab")"
[[ "$approved_sha256" != "$replacement_sha256" ]] ||
  fail "approved and replacement fixtures unexpectedly have the same digest"

positive_count=0
negative_count=0
swap_count=0
snapshot_tamper_count=0

base_environment=(
  env
  CI=true
  BUNDLETOOL_JAR="$bundletool_fixture"
  RELEASE_VERSION_NAME=4.2.0
  RELEASE_VERSION_CODE=230
  RELEASE_COMMIT=0123456789abcdef0123456789abcdef01234567
  FAKE_EXPECTED_SNAPSHOT_SHA256="$approved_sha256"
)

expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"

  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$label omitted its fail-closed diagnostic: $diagnostic"
  }
  negative_count=$((negative_count + 1))
}

positive_signature_log="$tmp_dir/positive-signature.log"
positive_identity_log="$tmp_dir/positive-identity.log"
positive_verifier_log="$tmp_dir/positive-verifier.log"
"${base_environment[@]}" \
  FAKE_SOURCE_AAB="$approved_aab" \
  FAKE_SIGNATURE_LOG="$positive_signature_log" \
  FAKE_IDENTITY_LOG="$positive_identity_log" \
  "$TEST_VERIFIER" --artifact "$approved_aab" "$approved_sha256" \
  >"$positive_verifier_log"
cmp "$positive_signature_log" "$positive_identity_log" >/dev/null ||
  fail "signature and identity verification did not consume one snapshot"
snapshot_path="$(awk -F '\t' 'NR == 1 { print $1 }' "$positive_signature_log")"
[[ -n "$snapshot_path" && "$snapshot_path" != "$approved_aab" &&
  ! -e "$snapshot_path" && ! -L "$snapshot_path" ]] ||
  fail "private verification snapshot was exposed or not cleaned up"
snapshot_digest="$(awk -F '\t' 'NR == 1 { print $2 }' "$positive_signature_log")"
[[ "$snapshot_digest" == "$approved_sha256" ]] ||
  fail "private verification snapshot had the wrong digest"
[[ "$(grep -Foc \
  "[android-play-release] trusted-signer-sha256=$approved_sha256" \
  "$positive_verifier_log")" == "1" ]] ||
  fail "successful verification did not emit exactly one trusted digest"
positive_count=$((positive_count + 1))

expect_failure \
  "malformed trusted signer digest" \
  "trusted signer SHA-256 must be exactly 64 lowercase" \
  "${base_environment[@]}" \
  FAKE_SOURCE_AAB="$approved_aab" \
  FAKE_SIGNATURE_LOG="$tmp_dir/malformed-signature.log" \
  FAKE_IDENTITY_LOG="$tmp_dir/malformed-identity.log" \
  "$TEST_VERIFIER" --artifact "$approved_aab" ABC

expect_failure \
  "wrong trusted signer digest" \
  "changed while its private verification snapshot was created" \
  "${base_environment[@]}" \
  FAKE_SOURCE_AAB="$approved_aab" \
  FAKE_SIGNATURE_LOG="$tmp_dir/wrong-signature.log" \
  FAKE_IDENTITY_LOG="$tmp_dir/wrong-identity.log" \
  "$TEST_VERIFIER" --artifact "$approved_aab" "$replacement_sha256"

expect_failure \
  "unguarded verifier checkpoint" \
  "checkpoints are restricted to explicit CI tests" \
  "${base_environment[@]}" \
  ANDROID_PLAY_VERIFIER_TEST_CHECKPOINT=after-input-validation \
  ANDROID_PLAY_VERIFIER_TEST_MARKER="$tmp_dir/unguarded-marker" \
  ANDROID_PLAY_VERIFIER_TEST_RESUME="$tmp_dir/unguarded-resume" \
  FAKE_SOURCE_AAB="$approved_aab" \
  FAKE_SIGNATURE_LOG="$tmp_dir/unguarded-signature.log" \
  FAKE_IDENTITY_LOG="$tmp_dir/unguarded-identity.log" \
  "$TEST_VERIFIER" --artifact "$approved_aab" "$approved_sha256"

run_swap_case() {
  local checkpoint="$1"
  local case_dir="$tmp_dir/swap-$checkpoint"
  local source_aab="$case_dir/source.aab"
  local swapped_aab="$case_dir/source.aab.replacement"
  local marker="$case_dir/checkpoint"
  local resume="$case_dir/resume"
  local signature_log="$case_dir/signature.log"
  local identity_log="$case_dir/identity.log"
  local output="$case_dir/verifier.log"
  local verifier_pid
  local observed=false

  mkdir -p "$case_dir"
  cp "$approved_aab" "$source_aab"
  "${base_environment[@]}" \
    TMPDIR="$case_dir" \
    ANDROID_PLAY_VERIFIER_TEST_MODE=true \
    ANDROID_PLAY_VERIFIER_TEST_CHECKPOINT="$checkpoint" \
    ANDROID_PLAY_VERIFIER_TEST_MARKER="$marker" \
    ANDROID_PLAY_VERIFIER_TEST_RESUME="$resume" \
    FAKE_SOURCE_AAB="$source_aab" \
    FAKE_SIGNATURE_LOG="$signature_log" \
    FAKE_IDENTITY_LOG="$identity_log" \
    "$TEST_VERIFIER" --artifact "$source_aab" "$approved_sha256" \
    >"$output" 2>&1 &
  verifier_pid="$!"

  for _ in $(seq 1 300); do
    if [[ -f "$marker" ]] &&
      [[ "$(tr -d '\r\n' <"$marker")" == "$checkpoint" ]]; then
      observed=true
      break
    fi
    if ! kill -0 "$verifier_pid" 2>/dev/null; then
      break
    fi
    sleep 0.05
  done
  if [[ "$observed" != "true" ]]; then
    kill -KILL "$verifier_pid" 2>/dev/null || true
    wait "$verifier_pid" 2>/dev/null || true
    sed -n '1,180p' "$output" >&2
    fail "$checkpoint did not reach its deterministic verifier checkpoint"
  fi

  cp "$replacement_aab" "$swapped_aab"
  mv "$swapped_aab" "$source_aab"
  printf 'resume\n' >"$resume"
  if wait "$verifier_pid"; then
    fail "$checkpoint accepted bytes swapped after trusted verification began"
  fi
  grep -Fq "release AAB changed" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$checkpoint omitted its source-swap diagnostic"
  }
  [[ "$(sha256_file "$source_aab")" == "$replacement_sha256" ]] ||
    fail "$checkpoint did not retain the deterministic replacement fixture"

  case "$checkpoint" in
    after-input-validation)
      [[ ! -e "$signature_log" && ! -e "$identity_log" ]] ||
        fail "$checkpoint verified bytes before rejecting the swapped input"
      ;;
    after-snapshot-copy)
      cmp "$signature_log" "$identity_log" >/dev/null ||
        fail "$checkpoint split signature and identity across different bytes"
      ;;
    after-signature-verification)
      [[ -s "$signature_log" && -s "$identity_log" ]] ||
        fail "$checkpoint did not complete both checks on the private snapshot"
      cmp "$signature_log" "$identity_log" >/dev/null ||
        fail "$checkpoint split signature and identity across different bytes"
      ;;
    after-identity-verification)
      cmp "$signature_log" "$identity_log" >/dev/null ||
        fail "$checkpoint split signature and identity across different bytes"
      ;;
    *)
      fail "unsupported verifier checkpoint: $checkpoint"
      ;;
  esac

  negative_count=$((negative_count + 1))
  swap_count=$((swap_count + 1))
}

for checkpoint in \
  after-input-validation \
  after-snapshot-copy \
  after-signature-verification \
  after-identity-verification; do
  run_swap_case "$checkpoint"
done

snapshot_case="$tmp_dir/private-snapshot-tamper"
snapshot_source_aab="$snapshot_case/source.aab"
snapshot_marker="$snapshot_case/checkpoint"
snapshot_resume="$snapshot_case/resume"
snapshot_signature_log="$snapshot_case/signature.log"
snapshot_identity_log="$snapshot_case/identity.log"
snapshot_output="$snapshot_case/verifier.log"
mkdir -p "$snapshot_case"
cp "$approved_aab" "$snapshot_source_aab"
"${base_environment[@]}" \
  TMPDIR="$snapshot_case" \
  ANDROID_PLAY_VERIFIER_TEST_MODE=true \
  ANDROID_PLAY_VERIFIER_TEST_CHECKPOINT=after-identity-verification \
  ANDROID_PLAY_VERIFIER_TEST_MARKER="$snapshot_marker" \
  ANDROID_PLAY_VERIFIER_TEST_RESUME="$snapshot_resume" \
  FAKE_SOURCE_AAB="$snapshot_source_aab" \
  FAKE_SIGNATURE_LOG="$snapshot_signature_log" \
  FAKE_IDENTITY_LOG="$snapshot_identity_log" \
  "$TEST_VERIFIER" --artifact "$snapshot_source_aab" "$approved_sha256" \
  >"$snapshot_output" 2>&1 &
snapshot_pid="$!"
snapshot_observed=false
for _ in $(seq 1 300); do
  if [[ -f "$snapshot_marker" ]] &&
    [[ "$(tr -d '\r\n' <"$snapshot_marker")" == "after-identity-verification" ]]; then
    snapshot_observed=true
    break
  fi
  if ! kill -0 "$snapshot_pid" 2>/dev/null; then
    break
  fi
  sleep 0.05
done
if [[ "$snapshot_observed" != "true" ]]; then
  kill -KILL "$snapshot_pid" 2>/dev/null || true
  wait "$snapshot_pid" 2>/dev/null || true
  sed -n '1,180p' "$snapshot_output" >&2
  fail "private snapshot tamper did not reach its deterministic checkpoint"
fi
cmp "$snapshot_signature_log" "$snapshot_identity_log" >/dev/null ||
  fail "private snapshot tamper did not verify one initial snapshot"
private_snapshot_path="$(
  awk -F '\t' 'NR == 1 { print $1 }' "$snapshot_signature_log"
)"
[[ -f "$private_snapshot_path" && ! -L "$private_snapshot_path" &&
  "$private_snapshot_path" != "$snapshot_source_aab" ]] ||
  fail "private snapshot tamper could not identify the verifier snapshot"
chmod 600 "$private_snapshot_path"
printf '\nprivate-snapshot-tamper\n' >>"$private_snapshot_path"
printf 'resume\n' >"$snapshot_resume"
if wait "$snapshot_pid"; then
  fail "private snapshot tamper unexpectedly succeeded"
fi
grep -Fq "private release AAB snapshot changed during verification" \
  "$snapshot_output" || {
    sed -n '1,180p' "$snapshot_output" >&2
    fail "private snapshot tamper omitted its fail-closed diagnostic"
  }
[[ "$(sha256_file "$snapshot_source_aab")" == "$approved_sha256" ]] ||
  fail "private snapshot tamper changed the trusted source fixture"
negative_count=$((negative_count + 1))
snapshot_tamper_count=$((snapshot_tamper_count + 1))

[[ "$positive_count" == "1" ]] ||
  fail "expected 1 positive case; got $positive_count"
[[ "$negative_count" == "8" ]] ||
  fail "expected 8 negative/adversarial cases; got $negative_count"
[[ "$swap_count" == "4" ]] ||
  fail "expected 4 deterministic source-swap cases; got $swap_count"
[[ "$snapshot_tamper_count" == "1" ]] ||
  fail "expected 1 deterministic private-snapshot tamper; got $snapshot_tamper_count"

echo \
  "[android-play-verifier-snapshot-test] $positive_count positive + $negative_count negative/adversarial including $swap_count source swaps + $snapshot_tamper_count private-snapshot tamper passed"
