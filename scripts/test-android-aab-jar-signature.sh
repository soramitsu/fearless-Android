#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-aab-jar-signature.sh"
test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(
  mktemp -d "$test_tmp_root/android-aab-signature.XXXXXX"
)"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT

fail() {
  echo "[android-aab-signature-test][error] $*" >&2
  exit 1
}

for command_name in awk cp grep jarsigner keytool python3; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

keystore_password="fearless-test-only"
approved_keystore="$tmp_dir/approved.p12"
secondary_keystore="$tmp_dir/secondary.p12"
unsigned_artifact="$tmp_dir/unsigned.aab"
valid_artifact="$tmp_dir/valid-self-signed.aab"

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
    -storetype PKCS12 \
    -storepass "$keystore_password" \
    -keypass "$keystore_password" >/dev/null 2>&1
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
    -storetype PKCS12 \
    -storepass "$keystore_password" \
    -alias "$alias_name" |
    awk -F'SHA256:' '/SHA256:/ { print $2; exit }' |
    tr -d '[:space:]:' |
    tr '[:lower:]' '[:upper:]'
}

sign_artifact() {
  local artifact="$1"
  local keystore="$2"
  local alias_name="$3"
  local signature_name="$4"

  LC_ALL=C jarsigner \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -keystore "$keystore" \
    -storetype PKCS12 \
    -storepass "$keystore_password" \
    -keypass "$keystore_password" \
    -sigalg SHA256withRSA \
    -digestalg SHA-256 \
    -sigfile "$signature_name" \
    "$artifact" \
    "$alias_name" >/dev/null 2>&1
}

python3 - "$unsigned_artifact" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"manifest fixture\n")
    archive.writestr("base/dex/classes.dex", b"dex fixture\n")
    archive.writestr("base/assets/config.json", b'{"safe":true}\n')
PY

generate_keystore "$approved_keystore" approved "Approved Upload"
generate_keystore "$secondary_keystore" secondary "Wrong Upload"
approved_fingerprint="$(keystore_fingerprint "$approved_keystore" approved)"
secondary_fingerprint="$(keystore_fingerprint "$secondary_keystore" secondary)"

cp "$unsigned_artifact" "$valid_artifact"
sign_artifact "$valid_artifact" "$approved_keystore" approved APPROVED

positive_count=0
negative_count=0

"$VERIFY" "$valid_artifact" "$approved_fingerprint" >/dev/null
positive_count=$((positive_count + 1))

expect_failure() {
  local label="$1"
  local expected_diagnostic="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"

  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$expected_diagnostic" "$output" || {
    sed -n '1,120p' "$output" >&2
    fail "$label did not emit the expected diagnostic: $expected_diagnostic"
  }
  negative_count=$((negative_count + 1))
}

expect_failure \
  "unsigned artifact" \
  "exactly one JAR signer" \
  "$VERIFY" "$unsigned_artifact" "$approved_fingerprint"

unsigned_entry_artifact="$tmp_dir/unsigned-entry.aab"
cp "$valid_artifact" "$unsigned_entry_artifact"
python3 - "$unsigned_entry_artifact" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "a", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/assets/unsigned-after-signing.txt", b"unsigned\n")
PY
expect_failure \
  "unsigned entry appended after signing" \
  "JAR signature is invalid or incomplete" \
  "$VERIFY" "$unsigned_entry_artifact" "$approved_fingerprint"

tampered_artifact="$tmp_dir/tampered-entry.aab"
python3 - "$valid_artifact" "$tampered_artifact" <<'PY'
import sys
import zipfile

source, destination = sys.argv[1:]
with zipfile.ZipFile(source, "r") as archive:
    entries = [(info, archive.read(info)) for info in archive.infolist()]

with zipfile.ZipFile(destination, "w") as archive:
    for info, data in entries:
        if info.filename == "base/assets/config.json":
            data = b'{"tampered":true}\n'
        archive.writestr(info, data)
PY
expect_failure \
  "tampered signed entry" \
  "JAR signature is invalid or incomplete" \
  "$VERIFY" "$tampered_artifact" "$approved_fingerprint"

expect_failure \
  "wrong signer certificate" \
  "does not match the approved upload certificate" \
  "$VERIFY" "$valid_artifact" "$secondary_fingerprint"

multiple_signer_artifact="$tmp_dir/multiple-signers.aab"
cp "$valid_artifact" "$multiple_signer_artifact"
sign_artifact \
  "$multiple_signer_artifact" \
  "$secondary_keystore" \
  secondary \
  SECOND
expect_failure \
  "multiple signers" \
  "exactly one JAR signer" \
  "$VERIFY" "$multiple_signer_artifact" "$approved_fingerprint"

duplicate_entry_artifact="$tmp_dir/duplicate-entry.aab"
cp "$valid_artifact" "$duplicate_entry_artifact"
python3 - "$duplicate_entry_artifact" <<'PY'
import sys
import warnings
import zipfile

with zipfile.ZipFile(sys.argv[1], "a") as archive:
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        archive.writestr("base/assets/config.json", b'{"duplicate":true}\n')
PY
expect_failure \
  "duplicate ZIP entry" \
  "duplicate ZIP entries" \
  "$VERIFY" "$duplicate_entry_artifact" "$approved_fingerprint"

[[ "$positive_count" == "1" ]] ||
  fail "expected 1 positive case; got $positive_count"
[[ "$negative_count" == "6" ]] ||
  fail "expected 6 negative cases; got $negative_count"

echo \
  "[android-aab-signature-test] $positive_count positive + $negative_count adversarial cases passed"
