#!/usr/bin/env bash
set -euo pipefail
umask 077

temporary_dir=""

cleanup() {
  if [[ -n "$temporary_dir" &&
    -d "$temporary_dir" &&
    ! -L "$temporary_dir" ]]; then
    rm -rf "$temporary_dir"
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

fail() {
  echo "[android-aab-signature][error] $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/verify-android-aab-jar-signature.sh \
    <bundle.aab> <expected-signer-certificate-sha256>

The verifier accepts an Android self-signed upload certificate only when its
exact SHA-256 fingerprint matches the caller's pinned value. It trusts only
that extracted certificate in an ephemeral truststore, requires exactly one
JAR signer, and uses locale-pinned strict jarsigner verification so every
non-signature archive entry must be signed and unmodified.
USAGE
}

normalize_fingerprint() {
  tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

require_regular_file() {
  local file_path="$1"
  local label="$2"
  local maximum_bytes="$3"
  local bytes

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "$label path is malformed."
  [[ ! -L "$file_path" && -f "$file_path" && -s "$file_path" ]] ||
    fail "$label must be a non-empty regular, non-symlink file."
  bytes="$(wc -c < "$file_path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size is malformed."
  (( bytes <= maximum_bytes )) || fail "$label exceeds its maximum size."
}

[[ "$#" -eq 2 ]] || {
  usage
  exit 2
}

artifact="$1"
expected_fingerprint="$(
  printf '%s' "$2" |
    normalize_fingerprint
)"

for command_name in awk grep jarsigner keytool mktemp sort tr uniq unzip wc; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

[[ "$expected_fingerprint" =~ ^[0-9A-F]{64}$ ]] ||
  fail "Expected signer certificate SHA-256 is malformed."
require_regular_file "$artifact" "release AAB" 262144000
unzip -t "$artifact" >/dev/null ||
  fail "The release AAB is not a valid ZIP archive."

temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-aab-signature.XXXXXX")"
[[ -d "$temporary_dir" && ! -L "$temporary_dir" ]] ||
  fail "Unable to create a safe temporary signature-verification directory."
chmod 700 "$temporary_dir"

entries_file="$temporary_dir/archive-entries.txt"
duplicate_entries_file="$temporary_dir/duplicate-entries.txt"
signer_certificate="$temporary_dir/signer-certificate.pem"
certificate_details="$temporary_dir/signer-certificate.txt"
truststore="$temporary_dir/exact-signer-truststore.p12"
jarsigner_output="$temporary_dir/jarsigner-output.txt"
truststore_password="fearless-public-certificate-verification"

unzip -Z1 "$artifact" > "$entries_file" ||
  fail "Unable to enumerate AAB entries."
LC_ALL=C sort "$entries_file" |
  uniq -d > "$duplicate_entries_file"
[[ ! -s "$duplicate_entries_file" ]] ||
  fail "The release AAB contains duplicate ZIP entries."

signature_block_count="$(
  LC_ALL=C grep -Eic '^META-INF/[^/]+\.(RSA|DSA|EC)$' "$entries_file" ||
    true
)"
signature_file_count="$(
  LC_ALL=C grep -Eic '^META-INF/[^/]+\.SF$' "$entries_file" ||
    true
)"
manifest_count="$(
  LC_ALL=C grep -Eic '^META-INF/MANIFEST\.MF$' "$entries_file" ||
    true
)"
[[ "$signature_block_count" == "1" &&
  "$signature_file_count" == "1" &&
  "$manifest_count" == "1" ]] ||
  fail "The release AAB must contain exactly one JAR signer and one manifest."

if ! keytool \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -printcert \
  -rfc \
  -jarfile "$artifact" > "$signer_certificate" 2>/dev/null; then
  fail "The release AAB JAR signature is invalid or incomplete."
fi
require_regular_file "$signer_certificate" "AAB signer certificate" 65536

certificate_begin_count="$(
  grep -c '^-----BEGIN CERTIFICATE-----$' "$signer_certificate" ||
    true
)"
certificate_end_count="$(
  grep -c '^-----END CERTIFICATE-----$' "$signer_certificate" ||
    true
)"
[[ "$certificate_begin_count" == "1" && "$certificate_end_count" == "1" ]] ||
  fail "The release AAB must contain exactly one self-signed signer certificate."

if ! keytool \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -printcert \
  -v \
  -file "$signer_certificate" > "$certificate_details" 2>&1; then
  fail "The extracted AAB signer certificate is malformed."
fi
actual_fingerprint="$(
  awk -F'SHA256:' '/SHA256:/ { print $2; exit }' "$certificate_details" |
    normalize_fingerprint
)"
[[ "$actual_fingerprint" =~ ^[0-9A-F]{64}$ ]] ||
  fail "The AAB signer certificate fingerprint could not be derived."
[[ "$actual_fingerprint" == "$expected_fingerprint" ]] ||
  fail "The AAB signer does not match the approved upload certificate."

if ! keytool \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -importcert \
  -noprompt \
  -alias approved-play-upload-certificate \
  -file "$signer_certificate" \
  -keystore "$truststore" \
  -storetype PKCS12 \
  -storepass "$truststore_password" >/dev/null 2>&1; then
  fail "Unable to create the exact ephemeral signer truststore."
fi
require_regular_file "$truststore" "ephemeral signer truststore" 1048576

if ! LC_ALL=C jarsigner \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -verify \
  -strict \
  -verbose \
  -certs \
  -keystore "$truststore" \
  -storetype PKCS12 \
  -storepass "$truststore_password" \
  "$artifact" > "$jarsigner_output" 2>&1; then
  fail "The release AAB JAR signature is invalid or incomplete."
fi
grep -Fq 'jar verified.' "$jarsigner_output" ||
  fail "The strict AAB signature verifier did not report success."

echo \
  "[android-aab-signature] exact self-signed certificate and complete JAR entry coverage verified"
