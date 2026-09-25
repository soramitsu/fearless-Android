#!/usr/bin/env bash
set -euo pipefail
umask 077

EXPECTED_UPLOAD_CERT_SHA256="40391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
SIGNATURE_VERIFIER="$ROOT_DIR/scripts/verify-android-aab-jar-signature.sh"
temporary_aab=""

cleanup() {
  if [[ -n "$temporary_aab" &&
    -f "$temporary_aab" &&
    ! -L "$temporary_aab" ]]; then
    rm -f "$temporary_aab"
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

fail() {
  echo "[android-release-signer][error] $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  CI=true \
  CI_KEYSTORE_PATH=/path/to/upload.jks \
  CI_KEYSTORE_PASS=... \
  CI_KEYSTORE_KEY_ALIAS=... \
  CI_KEYSTORE_KEY_PASS=... \
  ANDROID_UNSIGNED_AAB_SHA256=<lowercase-sha256> \
    scripts/sign-android-release-aab.sh <unsigned.aab> <signed.aab>

The input must be a source-bound, unsigned release AAB produced by the guarded
CI-only :app:bundleRelease path. Passwords are read only from environment
variables. The signer snapshots the exact expected digest, signs privately,
proves every non-signature ZIP entry retained identical bytes, verifies the
pinned Play upload certificate and complete JAR coverage, and atomically
publishes a previously absent output. On success it emits the SHA-256 digest
of the private verified signed snapshot as `signed-aab-sha256=<digest>`.
USAGE
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "$name is required."
}

normalize_fingerprint() {
  tr -d '[:space:]:' | tr '[:lower:]' '[:upper:]'
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_regular_file() {
  local file_path="$1"
  local label="$2"
  local maximum_bytes="$3"
  local bytes

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "$label path is malformed."
  [[ -f "$file_path" && ! -L "$file_path" && -s "$file_path" ]] ||
    fail "$label must be a non-empty regular, non-symlink file."
  bytes="$(wc -c < "$file_path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size is malformed."
  (( bytes <= maximum_bytes )) || fail "$label exceeds its maximum size."
}

assert_no_symlink_components() {
  local file_path="$1"
  local current

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "Signing paths must not contain control characters."
  if [[ "$file_path" == /* ]]; then
    current="$file_path"
  else
    current="$PWD/$file_path"
  fi
  while [[ "$current" != "/" && "$current" != "." ]]; do
    [[ ! -L "$current" ]] || fail "Signing paths must not traverse symlinks."
    current="$(dirname "$current")"
  done
}

test_checkpoint() {
  local stage="$1"
  local marker="${ANDROID_AAB_SIGNER_TEST_MARKER:-}"
  local resume="${ANDROID_AAB_SIGNER_TEST_RESUME:-}"

  [[ -z "${ANDROID_AAB_SIGNER_TEST_CHECKPOINT:-}" ||
    "$ANDROID_AAB_SIGNER_TEST_CHECKPOINT" != "$stage" ]] && return 0
  [[ "${ANDROID_AAB_SIGNER_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
    fail "Signer test checkpoints are restricted to explicit CI tests."
  [[ -n "$marker" ]] || fail "ANDROID_AAB_SIGNER_TEST_MARKER is required."
  printf '%s\n' "$stage" > "$marker"
  chmod 600 "$marker"
  if [[ -n "$resume" ]]; then
    [[ "$resume" != *$'\n'* && "$resume" != *$'\r'* ]] ||
      fail "ANDROID_AAB_SIGNER_TEST_RESUME is malformed."
    while [[ ! -e "$resume" && ! -L "$resume" ]]; do
      sleep 0.05
    done
    return 0
  fi
  while true; do
    sleep 1
  done
}

apply_test_mutation() {
  local mutation="${ANDROID_AAB_SIGNER_TEST_MUTATION:-}"

  [[ -n "$mutation" ]] || return 0
  [[ "${ANDROID_AAB_SIGNER_TEST_MODE:-}" == "true" && "${CI:-}" == "true" ]] ||
    fail "Signer test mutations are restricted to explicit CI tests."
  python3 - "$temporary_aab" "$mutation" <<'PY'
import os
import sys
import warnings
import zipfile

artifact, mutation = sys.argv[1:]

if mutation in {"tamper-entry", "remove-entry"}:
    rewritten = artifact + ".mutation"
    with zipfile.ZipFile(artifact, "r") as source:
        entries = [(info, source.read(info)) for info in source.infolist()]
    target = "base/assets/config.json"
    with zipfile.ZipFile(rewritten, "w") as destination:
        for info, data in entries:
            if mutation == "remove-entry" and info.filename == target:
                continue
            if mutation == "tamper-entry" and info.filename == target:
                data = b'{"tampered":true}\n'
            destination.writestr(info, data)
    os.replace(rewritten, artifact)
elif mutation == "append-entry":
    with zipfile.ZipFile(artifact, "a", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("base/assets/unsigned-after-signing.txt", b"unsigned\n")
elif mutation == "duplicate-signature-block":
    with zipfile.ZipFile(artifact, "r") as archive:
        block = next(
            (info for info in archive.infolist()
             if info.filename.upper().startswith("META-INF/")
             and info.filename.upper().endswith((".RSA", ".DSA", ".EC"))),
            None,
        )
        if block is None:
            raise SystemExit("signed fixture has no signature block")
        block_bytes = archive.read(block)
    with zipfile.ZipFile(artifact, "a", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("META-INF/SECOND.RSA", block_bytes)
else:
    print(
        f"[android-release-signer][error] Unsupported signer test mutation: {mutation}",
        file=sys.stderr,
    )
    sys.exit(1)
PY
}

[[ "$#" -eq 2 ]] || {
  usage
  exit 2
}

unsigned_aab="$1"
signed_aab="$2"

[[ "${CI:-}" == "true" ]] ||
  fail "Android release signing is restricted to an explicit CI environment."
for name in \
  CI_KEYSTORE_PATH \
  CI_KEYSTORE_PASS \
  CI_KEYSTORE_KEY_ALIAS \
  CI_KEYSTORE_KEY_PASS \
  ANDROID_UNSIGNED_AAB_SHA256; do
  require_env "$name"
done
[[ "$ANDROID_UNSIGNED_AAB_SHA256" =~ ^[0-9a-f]{64}$ ]] ||
  fail "ANDROID_UNSIGNED_AAB_SHA256 must be an exact lowercase SHA-256 digest."
[[ "$CI_KEYSTORE_KEY_ALIAS" != *$'\n'* &&
  "$CI_KEYSTORE_KEY_ALIAS" != *$'\r'* ]] ||
  fail "CI_KEYSTORE_KEY_ALIAS is malformed."

for command_name in \
  awk \
  chmod \
  cp \
  dirname \
  grep \
  jarsigner \
  keytool \
  ln \
  mktemp \
  python3 \
  shasum \
  tr \
  unzip \
  wc; do
  if [[ "$command_name" == "shasum" ]] &&
    command -v sha256sum >/dev/null 2>&1; then
    continue
  fi
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

[[ -x "$SIGNATURE_VERIFIER" && ! -L "$SIGNATURE_VERIFIER" ]] ||
  fail "The pinned AAB signature verifier is missing or unsafe."
assert_no_symlink_components "$unsigned_aab"
assert_no_symlink_components "$signed_aab"
assert_no_symlink_components "$CI_KEYSTORE_PATH"
require_regular_file "$unsigned_aab" "unsigned release AAB" 262144000
require_regular_file "$CI_KEYSTORE_PATH" "CI_KEYSTORE_PATH" 16777216

unsigned_parent="$(cd "$(dirname "$unsigned_aab")" && pwd -P)"
signed_parent="$(cd "$(dirname "$signed_aab")" && pwd -P)" ||
  fail "The signed AAB parent directory is missing or unsafe."
[[ -d "$signed_parent" && ! -L "$signed_parent" ]] ||
  fail "The signed AAB parent directory is missing or unsafe."
unsigned_canonical="$unsigned_parent/$(basename "$unsigned_aab")"
signed_canonical="$signed_parent/$(basename "$signed_aab")"
[[ "$unsigned_canonical" != "$signed_canonical" ]] ||
  fail "Unsigned input and signed output paths must be different."
[[ ! -e "$signed_aab" && ! -L "$signed_aab" ]] ||
  fail "The signed AAB output must not already exist or be a symlink."

unzip -t "$unsigned_aab" >/dev/null ||
  fail "The unsigned release AAB is not a valid ZIP archive."
[[ "$(sha256_file "$unsigned_aab")" == "$ANDROID_UNSIGNED_AAB_SHA256" ]] ||
  fail "The unsigned release AAB does not match ANDROID_UNSIGNED_AAB_SHA256."

if ! python3 - "$unsigned_aab" <<'PY'
import sys
import zipfile

artifact = sys.argv[1]

def is_signature_metadata(name):
    upper = name.upper()
    if upper == "META-INF/MANIFEST.MF":
        return True
    if not upper.startswith("META-INF/") or "/" in upper[len("META-INF/"):]:
        return False
    return upper.endswith((".SF", ".RSA", ".DSA", ".EC"))

with zipfile.ZipFile(artifact, "r") as archive:
    names = [entry.filename for entry in archive.infolist()]
    if len(names) != len(set(names)):
        print(
            "[android-release-signer][error] "
            "The unsigned release AAB contains duplicate ZIP entries.",
            file=sys.stderr,
        )
        sys.exit(1)
    signature_entries = [name for name in names if is_signature_metadata(name)]
    if signature_entries:
        print(
            "[android-release-signer][error] "
            "The input AAB is already signed or contains JAR signature metadata.",
            file=sys.stderr,
        )
        sys.exit(1)
PY
then
  exit 1
fi
test_checkpoint after-input-validation

keystore_details=""
if ! keystore_details="$(keytool \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -list \
  -v \
  -keystore "$CI_KEYSTORE_PATH" \
  -storepass:env CI_KEYSTORE_PASS \
  -alias "$CI_KEYSTORE_KEY_ALIAS" 2>&1)"; then
  fail "The configured release keystore or alias could not be opened."
fi
if grep -Eiq 'Owner:.*CN=Android Debug' <<< "$keystore_details"; then
  fail "The Android Debug certificate cannot sign a Google Play release."
fi
actual_keystore_fingerprint="$(
  awk -F'SHA256:' '/SHA256:/ { print $2; exit }' <<< "$keystore_details" |
    normalize_fingerprint
)"
keystore_details=""
[[ "$actual_keystore_fingerprint" =~ ^[0-9A-F]{64}$ ]] ||
  fail "The release keystore certificate fingerprint could not be derived."
[[ "$actual_keystore_fingerprint" == "$EXPECTED_UPLOAD_CERT_SHA256" ]] ||
  fail "The release keystore does not match the registered Google Play upload certificate."
test_checkpoint after-keystore-validation

signed_basename="$(basename "$signed_aab")"
temporary_aab="$(mktemp "$signed_parent/.${signed_basename}.signing.XXXXXX")"
chmod 600 "$temporary_aab"
cp "$unsigned_aab" "$temporary_aab"
chmod 600 "$temporary_aab"
[[ "$(sha256_file "$temporary_aab")" == "$ANDROID_UNSIGNED_AAB_SHA256" &&
  "$(sha256_file "$unsigned_aab")" == "$ANDROID_UNSIGNED_AAB_SHA256" ]] ||
  fail "The unsigned release AAB changed while its private snapshot was created."
test_checkpoint after-snapshot-copy

if ! LC_ALL=C jarsigner \
  -J-Duser.language=en \
  -J-Duser.country=US \
  -keystore "$CI_KEYSTORE_PATH" \
  -storepass:env CI_KEYSTORE_PASS \
  -keypass:env CI_KEYSTORE_KEY_PASS \
  -digestalg SHA-256 \
  -sigfile FEARLESS \
  "$temporary_aab" \
  "$CI_KEYSTORE_KEY_ALIAS" >/dev/null 2>&1; then
  fail "jarsigner could not sign the private AAB snapshot."
fi
test_checkpoint after-jarsigner
apply_test_mutation

if ! python3 - "$unsigned_aab" "$temporary_aab" <<'PY'
import hashlib
import sys
import zipfile

unsigned_path, signed_path = sys.argv[1:]

def is_signature_metadata(name):
    upper = name.upper()
    if upper == "META-INF/MANIFEST.MF":
        return True
    if not upper.startswith("META-INF/") or "/" in upper[len("META-INF/"):]:
        return False
    return upper.endswith((".SF", ".RSA", ".DSA", ".EC"))

def payload_inventory(path):
    inventory = {}
    with zipfile.ZipFile(path, "r") as archive:
        names = [entry.filename for entry in archive.infolist()]
        if len(names) != len(set(names)):
            raise ValueError("duplicate ZIP entries")
        for entry in archive.infolist():
            if is_signature_metadata(entry.filename):
                continue
            digest = hashlib.sha256()
            size = 0
            with archive.open(entry, "r") as payload:
                while True:
                    chunk = payload.read(1024 * 1024)
                    if not chunk:
                        break
                    digest.update(chunk)
                    size += len(chunk)
            inventory[entry.filename] = (size, digest.hexdigest())
    return inventory

try:
    before = payload_inventory(unsigned_path)
    after = payload_inventory(signed_path)
except (OSError, RuntimeError, ValueError, zipfile.BadZipFile) as error:
    print(
        "[android-release-signer][error] "
        f"Unable to prove AAB entry preservation: {error}",
        file=sys.stderr,
    )
    sys.exit(1)

if before != after:
    print(
        "[android-release-signer][error] "
        "Non-signature ZIP entry payloads changed while signing.",
        file=sys.stderr,
    )
    sys.exit(1)
PY
then
  exit 1
fi
[[ "$(sha256_file "$unsigned_aab")" == "$ANDROID_UNSIGNED_AAB_SHA256" ]] ||
  fail "The unsigned release AAB changed before signed output verification."
test_checkpoint after-preservation-verification

signed_sha256="$(sha256_file "$temporary_aab")"
[[ "$signed_sha256" =~ ^[0-9a-f]{64}$ ]] ||
  fail "The signed AAB digest is malformed before signature verification."
"$SIGNATURE_VERIFIER" \
  "$temporary_aab" \
  "$EXPECTED_UPLOAD_CERT_SHA256" >/dev/null
[[ "$(sha256_file "$temporary_aab")" == "$signed_sha256" ]] ||
  fail "The signed AAB changed during signature verification."
test_checkpoint after-signature-verification
[[ "$(sha256_file "$temporary_aab")" == "$signed_sha256" ]] ||
  fail "The verified signed AAB changed before atomic publication."

[[ ! -e "$signed_aab" && ! -L "$signed_aab" ]] ||
  fail "The signed AAB output appeared before atomic publication."
chmod 600 "$temporary_aab"
ln "$temporary_aab" "$signed_aab" ||
  fail "Unable to atomically publish the verified signed AAB."
[[ "$(sha256_file "$temporary_aab")" == "$signed_sha256" &&
  "$(sha256_file "$signed_aab")" == "$signed_sha256" ]] ||
  fail "The signed AAB changed during atomic publication."
test_checkpoint after-atomic-publish
rm -f "$temporary_aab"
temporary_aab=""

echo \
  "[android-release-signer] exact unsigned AAB signed, byte-preserved, certificate-pinned, and atomically published"
echo "[android-release-signer] signed-aab-sha256=$signed_sha256"
