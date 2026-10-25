#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-google-drive-web-client.py"
PIN="$ROOT_DIR/.github/release/android-google-drive-web-client-sha256.txt"

fail() {
  echo "[android-google-drive-web-client-test][error] $*" >&2
  exit 1
}

[[ -f "$VERIFY" && ! -L "$VERIFY" ]] ||
  fail "Google Drive web client verifier is missing or unsafe"
[[ -f "$PIN" && ! -L "$PIN" ]] ||
  fail "Google Drive web client pin is missing or unsafe"
for command_name in grep mktemp python3; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-google-drive-web-client.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

# Synthetic ids only; the production id is never needed by this suite.
pinned_client="123456789012-abcdefghijklmnopqrstuvwxyz012345.apps.googleusercontent.com"
other_client="123456789012-zyxwvutsrqponmlkjihgfedcba543210.apps.googleusercontent.com"
pinned_digest="$(printf '%s' "$pinned_client" | python3 -c 'import hashlib, sys; print(hashlib.sha256(sys.stdin.buffer.read()).hexdigest())')"

# A copy of the verifier reads the pin next to it, like the checked-in layout.
fixture_repo="$tmp_dir/repo"
mkdir -p "$fixture_repo/scripts" "$fixture_repo/.github/release"
cp "$VERIFY" "$fixture_repo/scripts/verify-android-google-drive-web-client.py"
fixture_verify="$fixture_repo/scripts/verify-android-google-drive-web-client.py"
fixture_pin="$fixture_repo/.github/release/android-google-drive-web-client-sha256.txt"
write_pin() {
  printf '# fixture pin\n%s\n' "$1" >"$fixture_pin"
}
write_pin "$pinned_digest"

positive_count=0
negative_count=0

expect_success() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/success-$positive_count.log"
  "$@" >"$output" 2>&1 || {
    sed -n '1,40p' "$output" >&2
    fail "$label was rejected"
  }
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,40p' "$output" >&2
    fail "$label did not report: $expected"
  }
  if grep -Fq -- ".apps.googleusercontent.com" "$output" &&
    grep -Eq -- '[0-9]+-[0-9a-z]+\.apps\.googleusercontent\.com' "$output"; then
    fail "$label printed a client id"
  fi
  positive_count=$((positive_count + 1))
}

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,40p' "$output" >&2
    fail "$label did not fail with expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

# make_artifact <output> <mode> [entry=string,string ...]
make_artifact() {
  python3 - "$@" <<'PY'
import struct
import sys
import warnings
import zipfile

output, mode, *specs = sys.argv[1:]


def dex(strings, *, magic=b"dex\n038\x00", size_delta=0, header_size=0x70,
        string_offset_override=None, unterminated=False):
    string_ids_offset = 0x70
    data_offset = string_ids_offset + 4 * len(strings)
    ids = b""
    payload = b""
    for value in strings:
        encoded = value.encode("utf-8")
        ids += struct.pack("<I", data_offset + len(payload))
        payload += bytes([len(encoded)]) + encoded + (b"" if unterminated else b"\x00")
    if string_offset_override is not None:
        ids = struct.pack("<I", string_offset_override) + ids[4:]
    size = 0x70 + len(ids) + len(payload)
    header = bytearray(0x70)
    header[0:8] = magic
    struct.pack_into("<III", header, 0x20, size + size_delta, header_size, 0x12345678)
    struct.pack_into("<II", header, 0x38, len(strings), string_ids_offset)
    return bytes(header) + ids + payload


if mode == "not-zip":
    with open(output, "wb") as artifact:
        artifact.write(b"not an android artifact")
    raise SystemExit

options = {
    "bad-magic": {"magic": b"zip\n038\x00"},
    "unsupported-version": {"magic": b"dex\n041\x00"},
    "size-mismatch": {"size_delta": 4},
    "bad-header": {"header_size": 0x78},
    "string-out-of-bounds": {"string_offset_override": 0x7FFFFFFF},
    "unterminated": {"unterminated": True},
}.get(mode, {})

with warnings.catch_warnings():
    warnings.simplefilter("ignore", UserWarning)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("BundleConfig.pb" if mode != "apk" else "AndroidManifest.xml", b"fixture")
        for spec in specs:
            entry, _, joined = spec.partition("=")
            strings = joined.split(",") if joined else []
            archive.writestr(entry, dex(strings, **options))
            if mode == "duplicate-entry":
                archive.writestr(entry, dex(strings))
        if mode == "unsafe-path":
            archive.writestr("../escape", b"unsafe")
PY
}

common_strings="Ljp/co/soramitsu/backup/BackupService;,context,create"

valid_aab="$tmp_dir/valid.aab"
make_artifact "$valid_aab" aab "base/dex/classes.dex=$common_strings,$pinned_client"
expect_success \
  "App Bundle with the pinned client" \
  "sha256 $pinned_digest) in base/dex/classes.dex" \
  python3 "$fixture_verify" "$valid_aab"

# Strings that only mention a client id (URLs, messages) are not the compiled literal.
valid_apk="$tmp_dir/valid.apk"
make_artifact "$valid_apk" apk \
  "classes.dex=$common_strings,https://accounts.example/?client_id=$other_client" \
  "classes2.dex=$pinned_client"
expect_success \
  "APK with the pinned client in a secondary dex" \
  "sha256 $pinned_digest) in classes2.dex" \
  python3 "$fixture_verify" "$valid_apk"

repeated_aab="$tmp_dir/repeated.aab"
make_artifact "$repeated_aab" aab \
  "base/dex/classes.dex=$pinned_client" \
  "base/dex/classes3.dex=$common_strings,$pinned_client"
expect_success \
  "App Bundle repeating the pinned client across dex files" \
  "in base/dex/classes.dex, base/dex/classes3.dex" \
  python3 "$fixture_verify" "$repeated_aab"

expect_failure \
  "missing argument" \
  "Usage:" \
  python3 "$fixture_verify"
expect_failure \
  "extra argument" \
  "Usage:" \
  python3 "$fixture_verify" "$valid_aab" extra

blank_aab="$tmp_dir/blank.aab"
make_artifact "$blank_aab" aab "base/dex/classes.dex=$common_strings,"
expect_failure \
  "blank web client" \
  "No Google OAuth web client id is compiled" \
  python3 "$fixture_verify" "$blank_aab"

padded_aab="$tmp_dir/padded.aab"
make_artifact "$padded_aab" aab "base/dex/classes.dex=$common_strings,$pinned_client "
expect_failure \
  "whitespace-padded web client" \
  "1 other string(s) mention .apps.googleusercontent.com" \
  python3 "$fixture_verify" "$padded_aab"

other_aab="$tmp_dir/other.aab"
make_artifact "$other_aab" aab "base/dex/classes.dex=$common_strings,$other_client"
expect_failure \
  "different web client" \
  "is not the pinned client" \
  python3 "$fixture_verify" "$other_aab"

two_aab="$tmp_dir/two.aab"
make_artifact "$two_aab" aab \
  "base/dex/classes.dex=$pinned_client" \
  "base/dex/classes2.dex=$other_client"
expect_failure \
  "two distinct client ids" \
  "2 distinct Google OAuth client ids" \
  python3 "$fixture_verify" "$two_aab"

# The checked-in pin parses and is not the synthetic fixture id.
expect_failure \
  "checked-in pin against a synthetic client" \
  "is not the pinned client" \
  python3 "$VERIFY" "$valid_aab"

no_dex="$tmp_dir/no-dex.aab"
make_artifact "$no_dex" aab
expect_failure \
  "artifact without dex" \
  "Artifact contains no dex files" \
  python3 "$fixture_verify" "$no_dex"

mixed_layout="$tmp_dir/mixed.aab"
make_artifact "$mixed_layout" aab \
  "base/dex/classes.dex=$pinned_client" \
  "classes.dex=$pinned_client"
expect_failure \
  "mixed APK and bundle dex layout" \
  "mixes APK and App Bundle dex layouts" \
  python3 "$fixture_verify" "$mixed_layout"

for case in \
  "bad-magic|is not a dex file" \
  "unsupported-version|unsupported dex version 041" \
  "size-mismatch|declared size does not match" \
  "bad-header|unsupported dex header" \
  "string-out-of-bounds|string 0 is out of bounds" \
  "unterminated|is unterminated" \
  "duplicate-entry|duplicate archive entry names" \
  "unsafe-path|unsafe archive path"; do
  mode="${case%%|*}"
  expected="${case#*|}"
  fixture="$tmp_dir/$mode.aab"
  make_artifact "$fixture" "$mode" "base/dex/classes.dex=$common_strings,$pinned_client"
  expect_failure "$mode artifact" "$expected" python3 "$fixture_verify" "$fixture"
done

not_zip="$tmp_dir/not-zip.aab"
make_artifact "$not_zip" not-zip
expect_failure \
  "non-zip artifact" \
  "not a valid zip archive" \
  python3 "$fixture_verify" "$not_zip"

expect_failure \
  "missing artifact" \
  "Artifact could not be opened" \
  python3 "$fixture_verify" "$tmp_dir/missing.aab"

symlink_artifact="$tmp_dir/symlink.aab"
ln -s "$valid_aab" "$symlink_artifact"
expect_failure \
  "symlink artifact" \
  "must not traverse a symlink component" \
  python3 "$fixture_verify" "$symlink_artifact"

directory_artifact="$tmp_dir/directory.aab"
mkdir "$directory_artifact"
expect_failure \
  "directory artifact" \
  "Artifact must be a non-empty regular file" \
  python3 "$fixture_verify" "$directory_artifact"

oversized_artifact="$tmp_dir/oversized.aab"
python3 - "$oversized_artifact" <<'PY'
import sys
with open(sys.argv[1], "wb") as artifact:
    artifact.truncate(262144001)
PY
expect_failure \
  "oversized artifact" \
  "Artifact exceeds the 262144000-byte limit" \
  python3 "$fixture_verify" "$oversized_artifact"

printf '# fixture pin\nUNCONFIGURED_GOOGLE_DRIVE_WEB_CLIENT\n' >"$fixture_pin"
expect_failure \
  "placeholder pin" \
  "pin is malformed" \
  python3 "$fixture_verify" "$valid_aab"

printf '%s\n%s\n' "$pinned_digest" "$pinned_digest" >"$fixture_pin"
expect_failure \
  "pin with two digests" \
  "pin is malformed" \
  python3 "$fixture_verify" "$valid_aab"

printf '%s\n' "$(tr '[:lower:]' '[:upper:]' <<<"$pinned_digest")" >"$fixture_pin"
expect_failure \
  "uppercase pin" \
  "pin is malformed" \
  python3 "$fixture_verify" "$valid_aab"

rm -f "$fixture_pin"
expect_failure \
  "missing pin" \
  "pin file could not be opened" \
  python3 "$fixture_verify" "$valid_aab"

write_pin "$pinned_digest"
mv "$fixture_pin" "$fixture_pin.real"
ln -s "$fixture_pin.real" "$fixture_pin"
expect_failure \
  "symlink pin" \
  "must not traverse a symlink component" \
  python3 "$fixture_verify" "$valid_aab"

[[ "$positive_count" == "3" ]] ||
  fail "expected 3 positive cases; got $positive_count"
[[ "$negative_count" == "27" ]] ||
  fail "expected 27 negative cases; got $negative_count"

echo \
  "[android-google-drive-web-client-test] $positive_count positive + $negative_count negative/adversarial cases passed"
