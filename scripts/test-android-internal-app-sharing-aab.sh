#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFY="$ROOT_DIR/scripts/verify-android-internal-app-sharing-aab.sh"
fixture="${IAS_AAB_FIXTURE:-}"
expected_commit="${EXPECTED_IAS_SOURCE_COMMIT:-}"
configured_keystore="${ANDROID_IAS_DEBUG_KEYSTORE_PATH:-}"
configured_certificate="${ANDROID_IAS_DEBUG_CERTIFICATE_PATH:-}"
signer_mode=""
temporary_dir=""
hook_pid=""
hook_dir=""
hook_log=""

cleanup() {
  if [[ -n "$hook_pid" ]] && kill -0 "$hook_pid" 2>/dev/null; then
    kill -TERM "$hook_pid" 2>/dev/null || true
    wait "$hook_pid" 2>/dev/null || true
  fi
  if [[ -n "$temporary_dir" && -d "$temporary_dir" && ! -L "$temporary_dir" ]]; then
    rm -rf "$temporary_dir"
  fi
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

fail() {
  echo "[android-ias-aab-test][error] $*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

for command_name in awk cp find git grep jarsigner keytool kill mktemp mv openssl python3 sleep uname; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
if ! command -v sha256sum >/dev/null 2>&1 &&
  ! command -v shasum >/dev/null 2>&1; then
  fail "sha256sum or the macOS-compatible shasum command is required."
fi
[[ -x "$VERIFY" ]] || fail "IAS AAB verifier is missing."
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "EXPECTED_IAS_SOURCE_COMMIT must be an exact lowercase Git commit."
[[ -n "${BUNDLETOOL_JAR:-}" ]] || fail "BUNDLETOOL_JAR is required."
[[ -z "$configured_keystore" || -z "$configured_certificate" ]] ||
  fail "provide either the IAS debug keystore or its public certificate, never both."
if [[ -n "$configured_keystore" ]]; then
  [[ ! -L "$configured_keystore" && -f "$configured_keystore" &&
    -s "$configured_keystore" ]] ||
    fail "ANDROID_IAS_DEBUG_KEYSTORE_PATH must be a safe non-empty file."
  signer_mode=keystore
elif [[ -n "$configured_certificate" ]]; then
  [[ ! -L "$configured_certificate" && -f "$configured_certificate" &&
    -s "$configured_certificate" ]] ||
    fail "ANDROID_IAS_DEBUG_CERTIFICATE_PATH must be a safe non-empty file."
  signer_mode=certificate
else
  fail "ANDROID_IAS_DEBUG_KEYSTORE_PATH or ANDROID_IAS_DEBUG_CERTIFICATE_PATH is required."
fi
[[ "${EXPECTED_IAS_DEBUG_CERT_SHA256:-}" =~ ^[0-9A-F]{64}$ ]] ||
  fail "EXPECTED_IAS_DEBUG_CERT_SHA256 is required."
[[ ! -L "$fixture" && -f "$fixture" && -s "$fixture" ]] ||
  fail "IAS_AAB_FIXTURE must be a non-empty regular, non-symlink AAB."

temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-ias-aab-test.XXXXXX")"
[[ -d "$temporary_dir" && ! -L "$temporary_dir" ]] ||
  fail "could not create safe artifact-test workspace."
chmod 700 "$temporary_dir"
temporary_dir="$(cd "$temporary_dir" && pwd -P)"
public_certificate="$temporary_dir/run-bound-public-certificate.pem"
if [[ "$signer_mode" == "keystore" ]]; then
  keytool -exportcert -rfc \
    -keystore "$configured_keystore" -storepass android \
    -alias androiddebugkey >"$public_certificate" 2>/dev/null ||
    fail "could not export the run-bound public certificate."
else
  keytool -printcert -rfc -file "$configured_certificate" \
    >"$public_certificate" 2>/dev/null ||
    fail "could not normalize the run-bound public certificate."
fi
chmod 600 "$public_certificate"
public_certificate_der="$temporary_dir/run-bound-public-certificate.der"
openssl x509 -in "$public_certificate" -outform DER \
  -out "$public_certificate_der" 2>/dev/null ||
  fail "could not encode the run-bound public certificate as DER."
chmod 600 "$public_certificate_der"

positive_count=0
negative_count=0
linux_resource_negative_count=0

expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$temporary_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly passed."
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label did not emit expected diagnostic: $diagnostic"
  }
  negative_count=$((negative_count + 1))
}

start_hooked_verifier() {
  local stage="$1"
  shift
  hook_dir="$temporary_dir/hook-$negative_count-$stage"
  hook_log="$temporary_dir/hook-$negative_count-$stage.log"
  mkdir -m 700 "$hook_dir"
  env \
    IAS_AAB_VERIFIER_TEST_MODE=true \
    IAS_AAB_VERIFIER_TEST_HOOK="$stage" \
    IAS_AAB_VERIFIER_TEST_HOOK_DIR="$hook_dir" \
    "$@" >"$hook_log" 2>&1 &
  hook_pid=$!

  local attempt
  for ((attempt = 0; attempt < 1200; attempt++)); do
    [[ ! -f "$hook_dir/$stage.ready" ]] || return 0
    if ! kill -0 "$hook_pid" 2>/dev/null; then
      wait "$hook_pid" 2>/dev/null || true
      sed -n '1,200p' "$hook_log" >&2
      hook_pid=""
      fail "hooked verifier exited before synchronization point: $stage"
    fi
    sleep 0.05
  done
  fail "timed out waiting for hooked verifier synchronization point: $stage"
}

resume_hooked_verifier() {
  local stage="$1"
  local resume_tmp="$hook_dir/$stage.continue.tmp"
  printf 'continue\n' >"$resume_tmp"
  chmod 600 "$resume_tmp"
  mv "$resume_tmp" "$hook_dir/$stage.continue"
}

finish_hooked_failure() {
  local label="$1" diagnostic="$2"
  if wait "$hook_pid"; then
    hook_pid=""
    fail "$label unexpectedly passed."
  fi
  hook_pid=""
  grep -Fq "$diagnostic" "$hook_log" || {
    sed -n '1,220p' "$hook_log" >&2
    fail "$label did not emit expected diagnostic: $diagnostic"
  }
  negative_count=$((negative_count + 1))
}

assert_no_publication_residue() {
  local parent="$1" destination="$2" label="$3"
  [[ ! -e "$destination" && ! -L "$destination" ]] ||
    fail "$label left a final verified output."
  if find "$parent" -mindepth 1 -maxdepth 1 \
    -name ".${destination##*/}.partial.*" -print -quit | grep -q .; then
    fail "$label left a partial verified output."
  fi
}

resign_with_debug_key() {
  local artifact="$1"
  jarsigner \
    -J-Duser.language=en \
    -J-Duser.country=US \
    -keystore "$ANDROID_IAS_DEBUG_KEYSTORE_PATH" \
    -storepass android \
    -keypass android \
    "$artifact" androiddebugkey >/dev/null
}

rewrite_unsigned() {
  local source="$1"
  local destination="$2"
  local entry_name="${3:-}"
  local old_value="${4:-}"
  local new_value="${5:-}"
  local anchor="${6:-}"
  python3 - \
    "$source" "$destination" "$entry_name" "$old_value" "$new_value" \
    "$anchor" <<'PY'
import re
import sys
import zipfile

source, destination, target, old, new, anchor = sys.argv[1:]
signature = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.I)
changed = target == ""
with zipfile.ZipFile(source, "r") as source_zip, zipfile.ZipFile(destination, "w") as output_zip:
    for info in source_zip.infolist():
        if signature.fullmatch(info.filename):
            continue
        payload = source_zip.read(info)
        if info.filename == target:
            old_bytes = old.encode("utf-8")
            new_bytes = new.encode("utf-8")
            if len(old_bytes) != len(new_bytes):
                raise SystemExit(
                    f"mutation precondition failed for {target}: "
                    f"lengths {len(old_bytes)}/{len(new_bytes)}"
                )
            if anchor:
                anchor_bytes = anchor.encode("utf-8")
                if payload.count(anchor_bytes) != 1:
                    raise SystemExit(
                        f"mutation anchor precondition failed for {target}: "
                        f"{anchor!r} occurrences {payload.count(anchor_bytes)}"
                    )
                anchor_position = payload.index(anchor_bytes)
                positions = [
                    position
                    for position in range(
                        anchor_position + len(anchor_bytes),
                        min(len(payload), anchor_position + 256),
                    )
                    if payload.startswith(old_bytes, position)
                ]
                if len(positions) != 1:
                    raise SystemExit(
                        f"anchored mutation precondition failed for {target}: "
                        f"{old!r} nearby occurrences {len(positions)}"
                    )
                position = positions[0]
                payload = (
                    payload[:position]
                    + new_bytes
                    + payload[position + len(old_bytes):]
                )
            else:
                if payload.count(old_bytes) != 1:
                    raise SystemExit(
                        f"mutation precondition failed for {target}: "
                        f"{old!r} occurrences {payload.count(old_bytes)}"
                    )
                payload = payload.replace(old_bytes, new_bytes)
            changed = True
        output_zip.writestr(info, payload)
if not changed:
    raise SystemExit(f"mutation target not found: {target}")
PY
}

create_archive_fixture() {
  local mode="$1" destination="$2"
  python3 - "$mode" "$destination" <<'PY'
import os
import io
import stat
import struct
import sys
import warnings
import zipfile

mode, destination = sys.argv[1:]


def write(entries, compression=zipfile.ZIP_STORED):
    with zipfile.ZipFile(
        destination,
        "w",
        compression=compression,
        compresslevel=9 if compression == zipfile.ZIP_DEFLATED else None,
    ) as archive:
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            for name, payload in entries:
                archive.writestr(name, payload)


def find_eocd(payload):
    offset = payload.rfind(b"PK\x05\x06")
    if offset < 0:
        raise SystemExit("fixture EOCD missing")
    return offset


def patch_single(*, flags=None, method=None, compressed=None, uncompressed=None):
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    central = payload.find(b"PK\x01\x02")
    if local < 0 or central < 0:
        raise SystemExit("fixture ZIP headers missing")
    if flags is not None:
        struct.pack_into("<H", payload, local + 6, flags)
        struct.pack_into("<H", payload, central + 8, flags)
    if method is not None:
        struct.pack_into("<H", payload, local + 8, method)
        struct.pack_into("<H", payload, central + 10, method)
    if compressed is not None:
        struct.pack_into("<I", payload, local + 18, compressed)
        struct.pack_into("<I", payload, central + 20, compressed)
    if uncompressed is not None:
        struct.pack_into("<I", payload, local + 22, uncompressed)
        struct.pack_into("<I", payload, central + 24, uncompressed)
    with open(destination, "wb") as output:
        output.write(payload)


if mode == "zip-bomb":
    one_mebibyte = b"\0" * 1_048_576
    write(
        ((f"base/assets/bomb-{index:04d}.bin", one_mebibyte) for index in range(1_025)),
        zipfile.ZIP_DEFLATED,
    )
elif mode == "extreme-ratio":
    write([("base/assets/extreme-ratio.bin", b"\0" * 2_097_152)], zipfile.ZIP_DEFLATED)
elif mode == "too-many-entries":
    write(((f"base/assets/entry-{index:05d}", b"") for index in range(50_001)))
elif mode == "oversized-name":
    write([("a" * 1_025, b"x")])
elif mode == "duplicate-name":
    write([("base/assets/same", b"one"), ("base/assets/same", b"two")])
elif mode == "case-collision":
    write([("base/assets/Same", b"one"), ("base/assets/same", b"two")])
elif mode == "traversal-name":
    write([("base/../escape", b"x")])
elif mode == "encrypted":
    write([("base/assets/encrypted", b"x")])
    patch_single(flags=0x0001)
elif mode == "unsupported-compression":
    write([("base/assets/unsupported", b"x")])
    patch_single(method=99)
elif mode == "huge-entry-size":
    write([("base/assets/declared-huge", b"x")])
    patch_single(compressed=10_000_000, uncompressed=600_000_000)
elif mode == "huge-compressed-size":
    write([("base/assets/declared-compressed", b"x")])
    patch_single(compressed=262_144_001, uncompressed=262_144_001)
elif mode == "huge-total-size":
    write([(f"base/assets/declared-{index}", b"x") for index in range(3)])
    payload = bytearray(open(destination, "rb").read())
    eocd = find_eocd(payload)
    cursor = struct.unpack_from("<I", payload, eocd + 16)[0]
    for _ in range(3):
        if payload[cursor:cursor + 4] != b"PK\x01\x02":
            raise SystemExit("fixture central header missing")
        local = struct.unpack_from("<I", payload, cursor + 42)[0]
        struct.pack_into("<I", payload, cursor + 20, 10_000_000)
        struct.pack_into("<I", payload, cursor + 24, 400_000_000)
        struct.pack_into("<I", payload, local + 18, 10_000_000)
        struct.pack_into("<I", payload, local + 22, 400_000_000)
        name_length, extra_length, comment_length = struct.unpack_from(
            "<HHH", payload, cursor + 28
        )
        cursor += 46 + name_length + extra_length + comment_length
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "invalid-utf8-name":
    write([("base/assets/name", b"x")])
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    central = payload.find(b"PK\x01\x02")
    payload[local + 30] = 0xFF
    payload[central + 46] = 0xFF
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "malformed-extra":
    info = zipfile.ZipInfo("base/assets/malformed-extra")
    info.extra = b"\x55\x54\x04\x00x"
    with zipfile.ZipFile(destination, "w") as archive:
        archive.writestr(info, b"x")
elif mode == "zip64-extra":
    info = zipfile.ZipInfo("base/assets/zip64-extra")
    info.extra = b"\x01\x00\x00\x00"
    with zipfile.ZipFile(destination, "w") as archive:
        archive.writestr(info, b"x")
elif mode == "reused-local-offset":
    write([("base/assets/first", b"one"), ("base/assets/second", b"two")])
    payload = bytearray(open(destination, "rb").read())
    eocd = find_eocd(payload)
    first_central = struct.unpack_from("<I", payload, eocd + 16)[0]
    name_length, extra_length, comment_length = struct.unpack_from(
        "<HHH", payload, first_central + 28
    )
    second_central = first_central + 46 + name_length + extra_length + comment_length
    first_local = struct.unpack_from("<I", payload, first_central + 42)[0]
    struct.pack_into("<I", payload, second_central + 42, first_local)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "local-central-mismatch":
    write([("base/assets/mismatch", b"x")])
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    struct.pack_into("<H", payload, local + 8, 8)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "local-name-mismatch":
    write([("base/assets/central", b"x")])
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    payload[local + 30] = ord("c")
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "local-flags-mismatch":
    write([("base/assets/flags", b"x")])
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    struct.pack_into("<H", payload, local + 6, 0x0800)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "local-crc-mismatch":
    write([("base/assets/crc", b"x")])
    payload = bytearray(open(destination, "rb").read())
    local = payload.find(b"PK\x03\x04")
    original_crc = struct.unpack_from("<I", payload, local + 14)[0]
    struct.pack_into("<I", payload, local + 14, original_crc ^ 0xFFFFFFFF)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "malformed-data-descriptor":
    class NonSeekable(io.BytesIO):
        def seekable(self):
            return False

        def seek(self, *_arguments, **_keywords):
            raise io.UnsupportedOperation("fixture deliberately is not seekable")

    buffer = NonSeekable()
    with zipfile.ZipFile(buffer, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("base/assets/descriptor", b"descriptor payload")
    payload = bytearray(buffer.getvalue())
    descriptor = payload.find(b"PK\x07\x08")
    if descriptor < 0:
        raise SystemExit("fixture data descriptor missing")
    descriptor_crc = struct.unpack_from("<I", payload, descriptor + 4)[0]
    struct.pack_into("<I", payload, descriptor + 4, descriptor_crc ^ 0xFFFFFFFF)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "special-entry":
    info = zipfile.ZipInfo("base/assets/symlink")
    info.create_system = 3
    info.external_attr = (stat.S_IFLNK | 0o777) << 16
    with zipfile.ZipFile(destination, "w") as archive:
        archive.writestr(info, b"../../escape")
elif mode == "trailing-central-data":
    write([("base/assets/trailing", b"x")])
    payload = bytearray(open(destination, "rb").read())
    eocd = find_eocd(payload)
    payload[eocd:eocd] = b"JUNK"
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "hidden-prefix-data":
    write([("base/assets/prefix", b"x")])
    payload = bytearray(open(destination, "rb").read())
    eocd = find_eocd(payload)
    central = struct.unpack_from("<I", payload, eocd + 16)[0]
    payload[:0] = b"X"
    eocd += 1
    central += 1
    struct.pack_into("<I", payload, central + 42, 1)
    struct.pack_into("<I", payload, eocd + 16, central)
    with open(destination, "wb") as output:
        output.write(payload)
elif mode == "multi-disk":
    write([("base/assets/multi-disk", b"x")])
    payload = bytearray(open(destination, "rb").read())
    eocd = find_eocd(payload)
    struct.pack_into("<H", payload, eocd + 4, 1)
    with open(destination, "wb") as output:
        output.write(payload)
else:
    raise SystemExit(f"unknown archive fixture mode: {mode}")
os.chmod(destination, 0o600)
PY
}

unsigned_fixture="$temporary_dir/unsigned-fixture.aab"
rewrite_unsigned "$fixture" "$unsigned_fixture"

real_keytool="$(command -v keytool)"
keytool_wrapper_dir="$temporary_dir/keytool-wrapper"
mkdir -m 700 "$keytool_wrapper_dir"
cat >"$keytool_wrapper_dir/keytool" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

has_rfc=false
has_jarfile=false
for argument in "$@"; do
  [[ "$argument" != "-rfc" ]] || has_rfc=true
  [[ "$argument" != "-jarfile" ]] || has_jarfile=true
done

if [[ "$has_rfc" == "true" && "$has_jarfile" == "true" ]]; then
  case "${IAS_KEYTOOL_TEST_MODE:-}" in
    prefixed)
      printf '%s\n' 'localized signer metadata before certificate'
      "$REAL_KEYTOOL_BIN" "$@"
      status=$?
      printf '%s\n' 'localized signer metadata after certificate'
      exit "$status"
      ;;
    duplicate)
      output="$("$REAL_KEYTOOL_BIN" "$@")"
      printf '%s\n%s\n' "$output" "$output"
      exit 0
      ;;
  esac
fi

exec "$REAL_KEYTOOL_BIN" "$@"
SH
chmod 700 "$keytool_wrapper_dir/keytool"

positive_log="$temporary_dir/positive.log"
env \
  PATH="$keytool_wrapper_dir:$PATH" \
  REAL_KEYTOOL_BIN="$real_keytool" \
  IAS_KEYTOOL_TEST_MODE=prefixed \
  CI=true \
  "$VERIFY" "$fixture" "$expected_commit" >"$positive_log" 2>&1 || {
  sed -n '1,200p' "$positive_log" >&2
  fail "valid IAS artifact with localized keytool metadata did not pass the complete verifier."
}
grep -Fq 'exact IAS identity, public Firebase resources, native payload, bundletool validity, and debug signature verified' \
  "$positive_log" || fail "positive IAS verifier omitted its evidence summary."
grep -Fq 'cannot upgrade a Play-installed wallet' "$positive_log" ||
  fail "positive IAS verifier omitted the mandatory upgrade warning."
positive_count=$((positive_count + 1))

public_certificate_log="$temporary_dir/public-certificate-positive.log"
env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$public_certificate" \
  CI=true \
  "$VERIFY" "$fixture" "$expected_commit" \
  >"$public_certificate_log" 2>&1 || {
  sed -n '1,200p' "$public_certificate_log" >&2
  fail "valid IAS artifact did not pass with only its public signer certificate."
}
grep -Fq 'exact IAS identity, public Firebase resources, native payload, bundletool validity, and debug signature verified' \
  "$public_certificate_log" ||
  fail "public-certificate IAS verification omitted its evidence summary."
positive_count=$((positive_count + 1))

env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$public_certificate_der" \
  CI=true \
  "$VERIFY" "$fixture" "$expected_commit" \
  >"$temporary_dir/public-certificate-der-positive.log" 2>&1 ||
  fail "valid IAS artifact did not pass with its DER public certificate."
positive_count=$((positive_count + 1))

verified_output_dir="$temporary_dir/verified-output"
mkdir -m 700 "$verified_output_dir"
verified_output="$verified_output_dir/fearless-ias.aab"
CI=true "$VERIFY" "$fixture" "$expected_commit" "$verified_output" \
  >"$temporary_dir/verified-output.log" 2>&1 ||
  fail "valid IAS artifact did not produce the exact verified output."
[[ ! -L "$verified_output" && -f "$verified_output" &&
  "$(sha256_file "$verified_output")" == "$(sha256_file "$fixture")" ]] ||
  fail "verified output does not match the exact candidate bytes."
positive_count=$((positive_count + 1))

unsigned_positive_log="$temporary_dir/unsigned-positive.log"
env \
  -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
  -u ANDROID_IAS_DEBUG_CERT_SHA256 \
  -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
  CI=true \
  "$VERIFY" --unsigned "$unsigned_fixture" "$expected_commit" \
  >"$unsigned_positive_log" 2>&1 || {
  sed -n '1,200p' "$unsigned_positive_log" >&2
  fail "valid unsigned IAS artifact did not pass the unsigned verifier."
}
grep -Fq 'exact unsigned IAS identity, public Firebase resources, native payload, and bundletool validity verified' \
  "$unsigned_positive_log" ||
  fail "unsigned IAS verification omitted its evidence summary."
positive_count=$((positive_count + 1))

unsigned_verified_output="$temporary_dir/verified-unsigned.aab"
env \
  -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
  -u ANDROID_IAS_DEBUG_CERT_SHA256 \
  -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
  CI=true \
  "$VERIFY" --unsigned "$unsigned_fixture" "$expected_commit" \
  "$unsigned_verified_output" >"$temporary_dir/unsigned-output.log" 2>&1 ||
  fail "valid unsigned IAS artifact did not produce an exact quarantine output."
[[ ! -L "$unsigned_verified_output" && -f "$unsigned_verified_output" &&
  "$(sha256_file "$unsigned_verified_output")" == \
    "$(sha256_file "$unsigned_fixture")" ]] ||
  fail "verified unsigned output does not match the exact candidate bytes."
positive_count=$((positive_count + 1))

signed_from_log="$temporary_dir/signed-from-positive.log"
CI=true "$VERIFY" --signed-from \
  "$unsigned_fixture" "$fixture" "$expected_commit" \
  >"$signed_from_log" 2>&1 || {
  sed -n '1,200p' "$signed_from_log" >&2
  fail "valid external signing transform did not pass verification."
}
grep -Fq "IAS_UNSIGNED_SOURCE_SHA256=$(sha256_file "$unsigned_fixture")" \
  "$signed_from_log" || fail "signed-from verification omitted unsigned provenance."
positive_count=$((positive_count + 1))

zip_bomb="$temporary_dir/archive-zip-bomb.aab"
extreme_ratio="$temporary_dir/archive-extreme-ratio.aab"
too_many_entries="$temporary_dir/archive-too-many-entries.aab"
oversized_name="$temporary_dir/archive-oversized-name.aab"
duplicate_name="$temporary_dir/archive-duplicate-name.aab"
case_collision="$temporary_dir/archive-case-collision.aab"
traversal_name="$temporary_dir/archive-traversal-name.aab"
encrypted_archive="$temporary_dir/archive-encrypted.aab"
unsupported_compression="$temporary_dir/archive-unsupported-compression.aab"
huge_entry_size="$temporary_dir/archive-huge-entry-size.aab"
huge_compressed_size="$temporary_dir/archive-huge-compressed-size.aab"
huge_total_size="$temporary_dir/archive-huge-total-size.aab"
invalid_utf8_name="$temporary_dir/archive-invalid-utf8-name.aab"
malformed_extra="$temporary_dir/archive-malformed-extra.aab"
zip64_extra="$temporary_dir/archive-zip64-extra.aab"
reused_local_offset="$temporary_dir/archive-reused-local-offset.aab"
local_central_mismatch="$temporary_dir/archive-local-central-mismatch.aab"
local_name_mismatch="$temporary_dir/archive-local-name-mismatch.aab"
local_flags_mismatch="$temporary_dir/archive-local-flags-mismatch.aab"
local_crc_mismatch="$temporary_dir/archive-local-crc-mismatch.aab"
malformed_data_descriptor="$temporary_dir/archive-malformed-data-descriptor.aab"
special_entry="$temporary_dir/archive-special-entry.aab"
trailing_central_data="$temporary_dir/archive-trailing-central-data.aab"
hidden_prefix_data="$temporary_dir/archive-hidden-prefix-data.aab"
multi_disk="$temporary_dir/archive-multi-disk.aab"
create_archive_fixture zip-bomb "$zip_bomb"
create_archive_fixture extreme-ratio "$extreme_ratio"
create_archive_fixture too-many-entries "$too_many_entries"
create_archive_fixture oversized-name "$oversized_name"
create_archive_fixture duplicate-name "$duplicate_name"
create_archive_fixture case-collision "$case_collision"
create_archive_fixture traversal-name "$traversal_name"
create_archive_fixture encrypted "$encrypted_archive"
create_archive_fixture unsupported-compression "$unsupported_compression"
create_archive_fixture huge-entry-size "$huge_entry_size"
create_archive_fixture huge-compressed-size "$huge_compressed_size"
create_archive_fixture huge-total-size "$huge_total_size"
create_archive_fixture invalid-utf8-name "$invalid_utf8_name"
create_archive_fixture malformed-extra "$malformed_extra"
create_archive_fixture zip64-extra "$zip64_extra"
create_archive_fixture reused-local-offset "$reused_local_offset"
create_archive_fixture local-central-mismatch "$local_central_mismatch"
create_archive_fixture local-name-mismatch "$local_name_mismatch"
create_archive_fixture local-flags-mismatch "$local_flags_mismatch"
create_archive_fixture local-crc-mismatch "$local_crc_mismatch"
create_archive_fixture malformed-data-descriptor "$malformed_data_descriptor"
create_archive_fixture special-entry "$special_entry"
create_archive_fixture trailing-central-data "$trailing_central_data"
create_archive_fixture hidden-prefix-data "$hidden_prefix_data"
create_archive_fixture multi-disk "$multi_disk"

expect_failure \
  "unsigned ZIP bomb" \
  "ZIP payload exceeds the total uncompressed-size cap" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$zip_bomb" "$expected_commit"
expect_failure \
  "signed extreme compression ratio" \
  "exceeds the per-entry compression-ratio cap" \
  "$VERIFY" "$extreme_ratio" "$expected_commit"
expect_failure \
  "signed archive entry-count exhaustion" \
  "ZIP entry count exceeds the archive preflight cap" \
  "$VERIFY" "$too_many_entries" "$expected_commit"
expect_failure \
  "signed-from oversized unsigned-source name" \
  "name exceeds the UTF-8 byte-length cap" \
  "$VERIFY" --signed-from "$oversized_name" "$fixture" "$expected_commit"
expect_failure \
  "signed duplicate entry name" \
  "duplicate ZIP entry name" \
  "$VERIFY" "$duplicate_name" "$expected_commit"
expect_failure \
  "unsigned case-colliding entry name" \
  "case-insensitive ZIP entry-name collision" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$case_collision" "$expected_commit"
expect_failure \
  "signed-from traversal in unsigned source" \
  "contains an empty or traversal path component" \
  "$VERIFY" --signed-from "$traversal_name" "$fixture" "$expected_commit"
expect_failure \
  "signed encrypted archive" \
  "central entry 0 is encrypted" \
  "$VERIFY" "$encrypted_archive" "$expected_commit"
expect_failure \
  "unsigned encrypted archive" \
  "central entry 0 is encrypted" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$encrypted_archive" "$expected_commit"
expect_failure \
  "signed unsupported compression" \
  "uses unsupported compression method 99" \
  "$VERIFY" "$unsupported_compression" "$expected_commit"
expect_failure \
  "unsigned unsupported compression" \
  "uses unsupported compression method 99" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$unsupported_compression" "$expected_commit"
expect_failure \
  "signed huge declared uncompressed entry" \
  "exceeds the per-entry uncompressed-size cap" \
  "$VERIFY" "$huge_entry_size" "$expected_commit"
expect_failure \
  "unsigned huge declared compressed entry" \
  "exceeds the per-entry compressed-size cap" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$huge_compressed_size" "$expected_commit"
expect_failure \
  "signed huge declared total payload" \
  "ZIP payload exceeds the total uncompressed-size cap" \
  "$VERIFY" "$huge_total_size" "$expected_commit"
expect_failure \
  "unsigned invalid UTF-8 entry name" \
  "name is not strict UTF-8" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$invalid_utf8_name" "$expected_commit"
expect_failure \
  "signed malformed ZIP extra metadata" \
  "has malformed ZIP extra metadata" \
  "$VERIFY" "$malformed_extra" "$expected_commit"
expect_failure \
  "unsigned ZIP64 extra metadata" \
  "uses unsupported ZIP64 metadata" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$zip64_extra" "$expected_commit"
expect_failure \
  "signed reused local-header offset" \
  "reuses a local-header offset" \
  "$VERIFY" "$reused_local_offset" "$expected_commit"
expect_failure \
  "unsigned local-central compression mismatch" \
  "disagrees with central compression metadata" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$local_central_mismatch" "$expected_commit"
expect_failure \
  "signed local-central name mismatch" \
  "name disagrees with the central directory" \
  "$VERIFY" "$local_name_mismatch" "$expected_commit"
expect_failure \
  "unsigned local-central flags mismatch" \
  "disagrees with central compression metadata" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$local_flags_mismatch" "$expected_commit"
expect_failure \
  "signed local-central CRC mismatch" \
  "size or CRC disagrees with the central directory" \
  "$VERIFY" "$local_crc_mismatch" "$expected_commit"
expect_failure \
  "unsigned malformed data descriptor" \
  "data descriptor disagrees with central metadata" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$malformed_data_descriptor" "$expected_commit"
expect_failure \
  "signed symlink-like special ZIP entry" \
  "represents an unsafe special file" \
  "$VERIFY" "$special_entry" "$expected_commit"
expect_failure \
  "unsigned trailing central-directory data" \
  "central-directory bounds are malformed or contain trailing records" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$trailing_central_data" "$expected_commit"
expect_failure \
  "signed hidden pre-local ZIP data" \
  "overlapping or separated by hidden data" \
  "$VERIFY" "$hidden_prefix_data" "$expected_commit"
expect_failure \
  "unsigned multi-disk ZIP metadata" \
  "multi-disk ZIP metadata is unsupported" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$multi_disk" "$expected_commit"

if [[ "$(uname -s)" == "Linux" ]]; then
  resource_tool_dir="$temporary_dir/resource-tool-bin"
  mkdir -m 700 "$resource_tool_dir"
  python3 - "$resource_tool_dir/unzip" <<'PY'
import os
import stat
import sys

path = sys.argv[1]
program = r'''#!/usr/bin/env python3
import mmap
import os
import subprocess
import sys
import time

mode = os.environ.get("IAS_AAB_RESOURCE_NEGATIVE_MODE")
if mode == "memory":
    try:
        allocation = mmap.mmap(-1, 5 * 1024 * 1024 * 1024, access=mmap.ACCESS_WRITE)
    except (BufferError, MemoryError, OSError, OverflowError):
        print("memory cap enforced by RLIMIT_AS", file=sys.stderr)
        raise SystemExit(73)
    allocation.close()
    print("memory cap was not enforced", file=sys.stderr)
    raise SystemExit(0)
if mode == "process-escape":
    child = subprocess.Popen(
        [sys.executable, "-c", "import time; time.sleep(600)"],
        start_new_session=True,
    )
    with open(os.environ["IAS_AAB_RESOURCE_ESCAPE_PID_FILE"], "x", encoding="ascii") as output:
        output.write(f"{child.pid}\n")
    raise SystemExit(0)
print("unknown IAS resource negative mode", file=sys.stderr)
raise SystemExit(74)
'''
descriptor = os.open(
    path,
    os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
    0o700,
)
try:
    os.write(descriptor, program.encode("utf-8"))
    os.fsync(descriptor)
finally:
    os.close(descriptor)
if stat.S_IMODE(os.stat(path, follow_symlinks=False).st_mode) != 0o700:
    raise SystemExit("fake unzip mode is unsafe")
PY

  expect_failure \
    "bounded unzip memory exhaustion" \
    "memory cap enforced by RLIMIT_AS" \
    env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
      -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
      -u ANDROID_IAS_DEBUG_CERT_SHA256 \
      -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
      PATH="$resource_tool_dir:$PATH" \
      IAS_AAB_RESOURCE_NEGATIVE_MODE=memory \
      "$VERIFY" --unsigned "$unsigned_fixture" "$expected_commit"

  escaped_pid_file="$temporary_dir/escaped-process.pid"
  expect_failure \
    "bounded unzip process-group escape" \
    "descendant process(es); terminated" \
    env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
      -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
      -u ANDROID_IAS_DEBUG_CERT_SHA256 \
      -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
      PATH="$resource_tool_dir:$PATH" \
      IAS_AAB_RESOURCE_NEGATIVE_MODE=process-escape \
      IAS_AAB_RESOURCE_ESCAPE_PID_FILE="$escaped_pid_file" \
      "$VERIFY" --unsigned "$unsigned_fixture" "$expected_commit"
  [[ ! -L "$escaped_pid_file" && -f "$escaped_pid_file" ]] ||
    fail "process-group escape negative did not record its child pid."
  read -r escaped_pid <"$escaped_pid_file"
  [[ "$escaped_pid" =~ ^[1-9][0-9]*$ ]] ||
    fail "process-group escape negative recorded a malformed child pid."
  if kill -0 "$escaped_pid" 2>/dev/null; then
    fail "resource supervisor left an escaped descendant running."
  fi
  linux_resource_negative_count=2
fi

usage_log="$temporary_dir/missing-arguments-usage.log"
if "$VERIFY" >"$usage_log" 2>&1; then
  fail "missing arguments unexpectedly passed."
fi
for usage_evidence in \
  'Usage:' \
  'FEARLESS_UTILS_COMMIT=<exact-utils-commit>' \
  'FEARLESS_UTILS_EFFECTIVE_TREE=<exact-utils-effective-tree>' \
  'macOS-compatible'; do
  grep -Fq "$usage_evidence" "$usage_log" ||
    fail "missing-arguments usage omitted required evidence: $usage_evidence"
done
negative_count=$((negative_count + 1))
expect_failure \
  "malformed source commit" \
  "Source commit must be an exact lowercase 40-character Git commit." \
  "$VERIFY" "$fixture" BAD
expect_failure \
  "missing signer fingerprint binding" \
  "EXPECTED_IAS_DEBUG_CERT_SHA256 must be an exact uppercase SHA-256 fingerprint." \
  env -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" "$fixture" "$expected_commit"
expect_failure \
  "wrong source commit" \
  "pre-verification checkout HEAD does not match the requested IAS commit." \
  "$VERIFY" "$fixture" 0000000000000000000000000000000000000000

expect_failure \
  "signer exposure in unsigned verification" \
  "Unsigned IAS verification forbids every signer input" \
  env ANDROID_IAS_DEBUG_KEYSTORE_PATH="$temporary_dir/attacker.jks" \
    "$VERIFY" --unsigned "$unsigned_fixture" "$expected_commit"
expect_failure \
  "signed artifact presented as unsigned" \
  "unsigned IAS AAB contains JAR signature metadata" \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    "$VERIFY" --unsigned "$fixture" "$expected_commit"
expect_failure \
  "signed signed-from source" \
  "signed-from source contains JAR signature metadata" \
  "$VERIFY" --signed-from "$fixture" "$fixture" "$expected_commit"

transform_mismatch_unsigned="$temporary_dir/transform-mismatch-unsigned.aab"
cp "$unsigned_fixture" "$transform_mismatch_unsigned"
python3 - "$transform_mismatch_unsigned" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "a") as archive:
    archive.writestr("base/assets/adversarial-transform-entry.txt", b"attacker")
PY
expect_failure \
  "external signing payload mismatch" \
  "external signing changed non-signature AAB entries" \
  "$VERIFY" --signed-from \
    "$transform_mismatch_unsigned" "$fixture" "$expected_commit"

hook_control_dir="$temporary_dir/hook-control"
mkdir -m 700 "$hook_control_dir"
expect_failure \
  "test mode without a hook" \
  "IAS verifier test hooks require one hook and one private hook directory." \
  env IAS_AAB_VERIFIER_TEST_MODE=true \
    "$VERIFY" "$fixture" "$expected_commit"
expect_failure \
  "test hook without explicit mode" \
  "IAS verifier test hooks require IAS_AAB_VERIFIER_TEST_MODE=true." \
  env IAS_AAB_VERIFIER_TEST_HOOK=after-primary-snapshots \
    IAS_AAB_VERIFIER_TEST_HOOK_DIR="$hook_control_dir" \
    "$VERIFY" "$fixture" "$expected_commit"
expect_failure \
  "unknown test hook" \
  "IAS verifier test hook is not an approved synchronization point." \
  env IAS_AAB_VERIFIER_TEST_MODE=true \
    IAS_AAB_VERIFIER_TEST_HOOK=skip-verification \
    IAS_AAB_VERIFIER_TEST_HOOK_DIR="$hook_control_dir" \
    "$VERIFY" "$fixture" "$expected_commit"
chmod 755 "$hook_control_dir"
expect_failure \
  "unsafe test-hook directory" \
  "test hook directory must be owner-controlled mode 0700." \
  env IAS_AAB_VERIFIER_TEST_MODE=true \
    IAS_AAB_VERIFIER_TEST_HOOK=after-primary-snapshots \
    IAS_AAB_VERIFIER_TEST_HOOK_DIR="$hook_control_dir" \
    "$VERIFY" "$fixture" "$expected_commit"
chmod 700 "$hook_control_dir"

race_aab="$temporary_dir/race-original.aab"
cp "$fixture" "$race_aab"
start_hooked_verifier \
  after-primary-snapshots \
  env CI=true "$VERIFY" "$race_aab" "$expected_commit"
printf 'mutated-after-snapshot\n' >>"$race_aab"
resume_hooked_verifier after-primary-snapshots
finish_hooked_failure \
  "original AAB mutation during verification" \
  "IAS AAB identity changed after snapshot."

race_bundletool="$temporary_dir/race-bundletool.jar"
cp "$BUNDLETOOL_JAR" "$race_bundletool"
start_hooked_verifier \
  after-primary-snapshots \
  env CI=true BUNDLETOOL_JAR="$race_bundletool" \
    "$VERIFY" "$fixture" "$expected_commit"
printf 'mutated-after-snapshot\n' >>"$race_bundletool"
resume_hooked_verifier after-primary-snapshots
finish_hooked_failure \
  "bundletool mutation during verification" \
  "BUNDLETOOL_JAR identity changed after snapshot."

race_unsigned_source="$temporary_dir/race-unsigned-source.aab"
cp "$unsigned_fixture" "$race_unsigned_source"
start_hooked_verifier \
  after-primary-snapshots \
  env CI=true "$VERIFY" --signed-from \
    "$race_unsigned_source" "$fixture" "$expected_commit"
printf 'mutated-after-snapshot\n' >>"$race_unsigned_source"
resume_hooked_verifier after-primary-snapshots
finish_hooked_failure \
  "unsigned source mutation during signing-transform verification" \
  "unsigned IAS source AAB identity changed after snapshot."

race_signer="$temporary_dir/race-signer-input"
race_signer_environment=(
  env
  -u ANDROID_IAS_DEBUG_KEYSTORE_PATH
  -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH
  CI=true
)
if [[ "$signer_mode" == "keystore" ]]; then
  cp "$configured_keystore" "$race_signer"
  race_signer_environment+=(ANDROID_IAS_DEBUG_KEYSTORE_PATH="$race_signer")
  race_signer_label="Android debug keystore"
else
  cp "$configured_certificate" "$race_signer"
  race_signer_environment+=(ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$race_signer")
  race_signer_label="Android debug certificate"
fi
chmod 600 "$race_signer"
start_hooked_verifier \
  after-signer-snapshot \
  "${race_signer_environment[@]}" \
    "$VERIFY" "$fixture" "$expected_commit"
printf 'mutated-after-snapshot\n' >>"$race_signer"
resume_hooked_verifier after-signer-snapshot
finish_hooked_failure \
  "run-bound signer mutation during verification" \
  "$race_signer_label identity changed after snapshot."

race_public_certificate="$temporary_dir/race-public-certificate.pem"
cp "$public_certificate" "$race_public_certificate"
start_hooked_verifier \
  after-signer-snapshot \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$race_public_certificate" \
    CI=true \
    "$VERIFY" "$fixture" "$expected_commit"
printf 'mutated-after-snapshot\n' >>"$race_public_certificate"
resume_hooked_verifier after-signer-snapshot
finish_hooked_failure \
  "public signer certificate mutation during verification" \
  "Android debug certificate identity changed after snapshot."

source_drift_repo="$temporary_dir/source-drift-repo"
git clone --quiet --no-hardlinks "$ROOT_DIR" "$source_drift_repo"
source_drift_commit="$(git -C "$source_drift_repo" rev-parse HEAD)"
[[ "$source_drift_commit" == "$expected_commit" ]] ||
  fail "source-drift clone did not preserve the exact IAS source commit."
start_hooked_verifier \
  after-primary-snapshots \
  env \
    CI=true \
    FEARLESS_UTILS_PATH="${FEARLESS_UTILS_PATH:-$ROOT_DIR/../fearless-utils-Android}" \
    "$source_drift_repo/scripts/verify-android-internal-app-sharing-aab.sh" \
    "$fixture" "$source_drift_commit"
printf 'tracked drift after pre-verification\n' \
  >>"$source_drift_repo/README.md"
resume_hooked_verifier after-primary-snapshots
finish_hooked_failure \
  "source drift during verification" \
  "post-verification source-tree verification failed."

ln -s "$fixture" "$temporary_dir/symlink.aab"
expect_failure \
  "symlink AAB" \
  "IAS AAB must be a non-empty regular, non-symlink file." \
  "$VERIFY" "$temporary_dir/symlink.aab" "$expected_commit"

ln "$fixture" "$temporary_dir/hardlink.aab"
expect_failure \
  "hard-linked AAB" \
  "IAS AAB must have exactly one filesystem link." \
  "$VERIFY" "$temporary_dir/hardlink.aab" "$expected_commit"
rm -f "$temporary_dir/hardlink.aab"

ln -s "$BUNDLETOOL_JAR" "$temporary_dir/bundletool-symlink.jar"
expect_failure \
  "symlink bundletool" \
  "BUNDLETOOL_JAR must be a non-empty regular, non-symlink file." \
  env BUNDLETOOL_JAR="$temporary_dir/bundletool-symlink.jar" \
    "$VERIFY" "$fixture" "$expected_commit"

printf '%s\n' 'not a jar' >"$temporary_dir/fake-bundletool.jar"
expect_failure \
  "malformed bundletool" \
  "BUNDLETOOL_JAR does not match the exact reviewed checksum." \
  env BUNDLETOOL_JAR="$temporary_dir/fake-bundletool.jar" \
    "$VERIFY" "$fixture" "$expected_commit"

expect_failure \
  "missing CI signer binding" \
  "CI verification requires exactly one run-bound IAS keystore or public certificate input." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH CI=true \
    "$VERIFY" "$fixture" "$expected_commit"

expect_failure \
  "mutually exclusive signer inputs" \
  "Run-bound IAS keystore and public certificate inputs are mutually exclusive." \
  env ANDROID_IAS_DEBUG_KEYSTORE_PATH="$temporary_dir/not-opened.jks" \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$public_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

metadata_public_certificate="$temporary_dir/metadata-public-certificate.pem"
printf '%s\n' 'Signer metadata must not be accepted from a caller.' \
  >"$metadata_public_certificate"
sed -n '1,200p' "$public_certificate" >>"$metadata_public_certificate"
expect_failure \
  "metadata-prefixed public signer certificate" \
  "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$metadata_public_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

malformed_public_certificate="$temporary_dir/malformed-public-certificate.pem"
printf '%s\n' 'not an X.509 certificate' >"$malformed_public_certificate"
expect_failure \
  "malformed public signer certificate" \
  "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$malformed_public_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

duplicate_public_certificate="$temporary_dir/duplicate-public-certificate.pem"
cp "$public_certificate" "$duplicate_public_certificate"
printf '\n' >>"$duplicate_public_certificate"
sed -n '1,200p' "$public_certificate" >>"$duplicate_public_certificate"
expect_failure \
  "duplicate public signer certificate" \
  "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$duplicate_public_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

expect_failure \
  "duplicate signer certificates in keytool output" \
  "The IAS AAB signer certificate must expose exactly one X.509 certificate." \
  env \
    PATH="$keytool_wrapper_dir:$PATH" \
    REAL_KEYTOOL_BIN="$real_keytool" \
    IAS_KEYTOOL_TEST_MODE=duplicate \
    "$VERIFY" "$fixture" "$expected_commit"

private_material_certificate="$temporary_dir/private-material-certificate.pem"
cp "$public_certificate" "$private_material_certificate"
printf '%s\n' \
  '-----BEGIN PRIVATE KEY-----' \
  'YXR0YWNrZXI=' \
  '-----END PRIVATE KEY-----' \
  >>"$private_material_certificate"
expect_failure \
  "private material appended to public signer certificate" \
  "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$private_material_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

trailing_der_certificate="$temporary_dir/trailing-public-certificate.der"
cp "$public_certificate_der" "$trailing_der_certificate"
printf 'trailing' >>"$trailing_der_certificate"
expect_failure \
  "trailing bytes after DER public signer certificate" \
  "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$trailing_der_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

ln -s "$public_certificate" "$temporary_dir/public-certificate-symlink.pem"
expect_failure \
  "symlink public signer certificate" \
  "Android debug certificate must be a non-empty regular, non-symlink file." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$temporary_dir/public-certificate-symlink.pem" \
    "$VERIFY" "$fixture" "$expected_commit"

cp "$public_certificate" "$temporary_dir/public-certificate-hardlink.pem"
ln "$temporary_dir/public-certificate-hardlink.pem" \
  "$temporary_dir/public-certificate-hardlink-second.pem"
expect_failure \
  "hard-linked public signer certificate" \
  "Android debug certificate must have exactly one filesystem link." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$temporary_dir/public-certificate-hardlink.pem" \
    "$VERIFY" "$fixture" "$expected_commit"

wrong_subject_keystore="$temporary_dir/wrong-subject.p12"
keytool -genkeypair -noprompt \
  -alias wrongsubject \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -dname 'CN=Fearless IAS Test,O=Fearless,C=US' \
  -keystore "$wrong_subject_keystore" -storetype PKCS12 \
  -storepass adversarial -keypass adversarial >/dev/null 2>&1
wrong_subject_certificate="$temporary_dir/wrong-subject.pem"
keytool -exportcert -rfc -keystore "$wrong_subject_keystore" \
  -storetype PKCS12 -storepass adversarial -alias wrongsubject \
  >"$wrong_subject_certificate" 2>/dev/null
expect_failure \
  "wrong-subject public signer certificate" \
  "IAS must use the exact Android Debug certificate subject." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$wrong_subject_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

wrong_debug_keystore="$temporary_dir/wrong-debug.p12"
keytool -genkeypair -noprompt \
  -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 3650 \
  -dname 'CN=Android Debug,O=Android,C=US' \
  -keystore "$wrong_debug_keystore" -storetype PKCS12 \
  -storepass android -keypass android >/dev/null 2>&1
wrong_debug_certificate="$temporary_dir/wrong-debug.pem"
keytool -exportcert -rfc -keystore "$wrong_debug_keystore" \
  -storetype PKCS12 -storepass android -alias androiddebugkey \
  >"$wrong_debug_certificate" 2>/dev/null
expect_failure \
  "wrong public signer certificate" \
  "IAS signer does not match the exact run-bound Android debug certificate." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$wrong_debug_certificate" \
    "$VERIFY" "$fixture" "$expected_commit"

expect_failure \
  "public signer fingerprint mismatch" \
  "IAS signer does not match the exact run-bound test certificate fingerprint." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$public_certificate" \
    EXPECTED_IAS_DEBUG_CERT_SHA256=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA \
    "$VERIFY" "$fixture" "$expected_commit"

rewrite_unsigned "$fixture" "$temporary_dir/unsigned.aab"
expect_failure \
  "unsigned artifact" \
  "The IAS AAB signer certificate must expose exactly one X.509 certificate." \
  "$VERIFY" "$temporary_dir/unsigned.aab" "$expected_commit"

python3 - "$fixture" "$temporary_dir/tampered-signed.aab" <<'PY'
import copy
import os
import sys
import zipfile

source, destination = sys.argv[1:]
with zipfile.ZipFile(source, "r") as source_zip, zipfile.ZipFile(
    destination, "x"
) as output_zip:
    for info in source_zip.infolist():
        output_zip.writestr(copy.copy(info), source_zip.read(info))
    output_zip.writestr("base/assets/adversarial-unsigned-entry.txt", b"tampered")
os.chmod(destination, 0o600)
PY
expect_failure \
  "signed artifact with unsigned entry" \
  "JAR signature is invalid or incomplete" \
  "$VERIFY" "$temporary_dir/tampered-signed.aab" "$expected_commit"

rewrite_unsigned "$fixture" "$temporary_dir/wrong-subject.aab"
jarsigner -keystore "$wrong_subject_keystore" -storetype PKCS12 \
  -storepass adversarial -keypass adversarial \
  "$temporary_dir/wrong-subject.aab" wrongsubject >/dev/null
expect_failure \
  "wrong signer subject" \
  "IAS must use the exact Android Debug certificate subject." \
  "$VERIFY" "$temporary_dir/wrong-subject.aab" "$expected_commit"

expect_failure \
  "wrong local debug keystore" \
  "IAS signer does not match the exact run-bound Android debug certificate." \
  env -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    CI=false ANDROID_IAS_DEBUG_KEYSTORE_PATH="$wrong_debug_keystore" \
    "$VERIFY" "$fixture" "$expected_commit"
rewrite_unsigned "$fixture" "$temporary_dir/wrong-debug.aab"
jarsigner -keystore "$wrong_debug_keystore" -storetype PKCS12 \
  -storepass android -keypass android \
  "$temporary_dir/wrong-debug.aab" androiddebugkey >/dev/null
expect_failure \
  "different debug certificate" \
  "IAS signer does not match the exact run-bound Android debug certificate." \
  "$VERIFY" "$temporary_dir/wrong-debug.aab" "$expected_commit"

expired_debug_keystore="$temporary_dir/expired-debug.jks"
keytool -genkeypair -noprompt -storetype JKS \
  -keystore "$expired_debug_keystore" -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 1 \
  -startdate '2020/01/01 00:00:00' \
  -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1
chmod 600 "$expired_debug_keystore"
expired_debug_fingerprint="$(
  keytool -exportcert -rfc -keystore "$expired_debug_keystore" \
    -storepass android -alias androiddebugkey 2>/dev/null |
    openssl x509 -outform DER 2>/dev/null |
    openssl dgst -sha256 -r |
    awk '{print toupper($1)}'
)"
expect_failure \
  "expired debug-subject signer" \
  "The run-bound Android debug certificate is expired or not yet valid." \
  env -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    ANDROID_IAS_DEBUG_KEYSTORE_PATH="$expired_debug_keystore" \
    EXPECTED_IAS_DEBUG_CERT_SHA256="$expired_debug_fingerprint" \
    "$VERIFY" "$fixture" "$expected_commit"
expired_debug_certificate="$temporary_dir/expired-debug.pem"
keytool -exportcert -rfc -keystore "$expired_debug_keystore" \
  -storepass android -alias androiddebugkey \
  >"$expired_debug_certificate" 2>/dev/null
expect_failure \
  "expired public debug certificate" \
  "The run-bound Android debug certificate is expired or not yet valid." \
  env -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$expired_debug_certificate" \
    EXPECTED_IAS_DEBUG_CERT_SHA256="$expired_debug_fingerprint" \
    "$VERIFY" "$fixture" "$expected_commit"

if [[ "$signer_mode" == "keystore" ]]; then
rewrite_unsigned \
  "$fixture" "$temporary_dir/wrong-version.aab" \
  base/manifest/AndroidManifest.xml 4.2.0-ias 9.9.9-ias
resign_with_debug_key "$temporary_dir/wrong-version.aab"
expect_failure \
  "mutated embedded version" \
  "AAB versionName mismatch" \
  "$VERIFY" "$temporary_dir/wrong-version.aab" "$expected_commit"

rewrite_unsigned \
  "$fixture" "$temporary_dir/wrong-source.aab" \
  base/manifest/AndroidManifest.xml "$expected_commit" \
  0000000000000000000000000000000000000000
resign_with_debug_key "$temporary_dir/wrong-source.aab"
expect_failure \
  "mutated embedded source" \
  "AAB source commit mismatch" \
  "$VERIFY" "$temporary_dir/wrong-source.aab" "$expected_commit"

rewrite_unsigned \
  "$fixture" "$temporary_dir/wrong-firebase.aab" \
  base/resources.pb fearless-public fearless-attack project_id
resign_with_debug_key "$temporary_dir/wrong-firebase.aab"
expect_failure \
  "mutated compiled Firebase identity" \
  "compiled Firebase resource project_id does not have exactly the approved public-placeholder value." \
  "$VERIFY" "$temporary_dir/wrong-firebase.aab" "$expected_commit"

rewrite_unsigned \
  "$fixture" "$temporary_dir/wrong-database-url.aab" \
  base/resources.pb \
  https://fearless-public.firebaseio.com \
  https://fearless-attack.firebaseio.com
resign_with_debug_key "$temporary_dir/wrong-database-url.aab"
expect_failure \
  "mutated compiled Firebase database URL" \
  "compiled Firebase resource firebase_database_url does not have exactly the approved public-placeholder value." \
  "$VERIFY" "$temporary_dir/wrong-database-url.aab" "$expected_commit"

rewrite_unsigned \
  "$fixture" "$temporary_dir/wrong-storage-bucket.aab" \
  base/resources.pb \
  fearless-public.appspot.com \
  fearless-attack.appspot.com
resign_with_debug_key "$temporary_dir/wrong-storage-bucket.aab"
expect_failure \
  "mutated compiled Firebase storage bucket" \
  "compiled Firebase resource google_storage_bucket does not have exactly the approved public-placeholder value." \
  "$VERIFY" "$temporary_dir/wrong-storage-bucket.aab" "$expected_commit"

python3 - "$fixture" "$temporary_dir/extra-module.aab" <<'PY'
import re
import sys
import zipfile

source, destination = sys.argv[1:]
signature = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.I)
with zipfile.ZipFile(source) as source_zip, zipfile.ZipFile(destination, "w") as output_zip:
    base_manifest = source_zip.read("base/manifest/AndroidManifest.xml")
    for info in source_zip.infolist():
        if not signature.fullmatch(info.filename):
            output_zip.writestr(info, source_zip.read(info))
    output_zip.writestr("evil/manifest/AndroidManifest.xml", base_manifest)
PY
resign_with_debug_key "$temporary_dir/extra-module.aab"
expect_failure \
  "unexpected extra application module" \
  "IAS AAB must contain exactly the base application module." \
  "$VERIFY" "$temporary_dir/extra-module.aab" "$expected_commit"

python3 - "$fixture" "$temporary_dir/malformed-structure.aab" <<'PY'
import re
import sys
import zipfile
source, destination = sys.argv[1:]
signature = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.I)
removed = False
with zipfile.ZipFile(source) as source_zip, zipfile.ZipFile(destination, "w") as output_zip:
    for info in source_zip.infolist():
        if signature.fullmatch(info.filename):
            continue
        if info.filename == "base/manifest/AndroidManifest.xml":
            removed = True
            continue
        output_zip.writestr(info, source_zip.read(info))
if not removed:
    raise SystemExit("manifest mutation target missing")
PY
resign_with_debug_key "$temporary_dir/malformed-structure.aab"
expect_failure \
  "bundletool-invalid artifact structure" \
  "IAS AAB must contain exactly the base application module." \
  "$VERIFY" "$temporary_dir/malformed-structure.aab" "$expected_commit"
fi

preexisting_output="$temporary_dir/preexisting-output.aab"
printf 'attacker\n' >"$preexisting_output"
expect_failure \
  "pre-existing verified output" \
  "verified output already exists; refusing to overwrite it." \
  "$VERIFY" "$fixture" "$expected_commit" "$preexisting_output"

unsafe_output_dir="$temporary_dir/unsafe-output"
mkdir -m 755 "$unsafe_output_dir"
expect_failure \
  "unsafe verified-output directory mode" \
  "verified output parent must be owner-controlled mode 0700." \
  "$VERIFY" "$fixture" "$expected_commit" "$unsafe_output_dir/candidate.aab"

real_output_dir="$temporary_dir/real-output"
mkdir -m 700 "$real_output_dir"
ln -s "$real_output_dir" "$temporary_dir/symlink-output"
expect_failure \
  "symlinked verified-output ancestor" \
  "verified output parent must not traverse symlinks." \
  "$VERIFY" "$fixture" "$expected_commit" \
    "$temporary_dir/symlink-output/candidate.aab"

mkdir -p "$ROOT_DIR/build/ias-verifier-forbidden-output"
chmod 700 "$ROOT_DIR/build/ias-verifier-forbidden-output"
expect_failure \
  "verified output inside checkout" \
  "verified output must stay outside the source checkout." \
  "$VERIFY" "$fixture" "$expected_commit" \
    "$ROOT_DIR/build/ias-verifier-forbidden-output/candidate.aab"
rmdir "$ROOT_DIR/build/ias-verifier-forbidden-output"

destination_swap_dir="$temporary_dir/destination-swap-output"
mkdir -m 700 "$destination_swap_dir"
destination_swap_output="$destination_swap_dir/candidate.aab"
destination_swap_target="$temporary_dir/destination-swap-target"
printf 'attacker-owned-target\n' >"$destination_swap_target"
destination_swap_target_sha="$(
  sha256_file "$destination_swap_target"
)"
start_hooked_verifier \
  before-publication \
  env CI=true "$VERIFY" "$fixture" "$expected_commit" "$destination_swap_output"
ln -s "$destination_swap_target" "$destination_swap_output"
resume_hooked_verifier before-publication
finish_hooked_failure \
  "destination symlink swap before publication" \
  "verified output appeared before publication; refusing to overwrite it."
[[ -L "$destination_swap_output" &&
  "$(sha256_file "$destination_swap_target")" == \
    "$destination_swap_target_sha" ]] ||
  fail "destination swap modified or removed the attacker-controlled target."
rm -f "$destination_swap_output"
assert_no_publication_residue \
  "$destination_swap_dir" "$destination_swap_output" \
  "destination symlink swap"

parent_swap_dir="$temporary_dir/parent-swap-output"
parent_swap_held="$temporary_dir/parent-swap-held"
mkdir -m 700 "$parent_swap_dir"
parent_swap_output="$parent_swap_dir/candidate.aab"
start_hooked_verifier \
  publication-partial-ready \
  env CI=true "$VERIFY" "$fixture" "$expected_commit" "$parent_swap_output"
mv "$parent_swap_dir" "$parent_swap_held"
mkdir -m 700 "$parent_swap_dir"
resume_hooked_verifier publication-partial-ready
finish_hooked_failure \
  "verified-output parent replacement during publication" \
  "verified output parent identity changed during publication."
assert_no_publication_residue \
  "$parent_swap_held" "$parent_swap_held/candidate.aab" \
  "verified-output parent replacement (original parent)"
assert_no_publication_residue \
  "$parent_swap_dir" "$parent_swap_output" \
  "verified-output parent replacement (replacement parent)"

term_output_dir="$temporary_dir/term-output"
mkdir -m 700 "$term_output_dir"
term_output="$term_output_dir/candidate.aab"
start_hooked_verifier \
  publication-linked \
  env CI=true "$VERIFY" "$fixture" "$expected_commit" "$term_output"
read -r publication_pid <"$hook_dir/publication-linked.ready"
[[ "$publication_pid" =~ ^[1-9][0-9]*$ ]] ||
  fail "publication interruption hook returned a malformed process id."
kill -TERM "$publication_pid"
term_status=0
if wait "$hook_pid"; then
  hook_pid=""
  fail "TERM during publication unexpectedly succeeded."
else
  term_status=$?
fi
hook_pid=""
[[ "$term_status" == "130" ]] ||
  fail "TERM during publication returned $term_status instead of 130."
assert_no_publication_residue \
  "$term_output_dir" "$term_output" \
  "TERM during publication"
negative_count=$((negative_count + 1))

if [[ "$signer_mode" == "keystore" ]]; then
python3 - "$fixture" "$temporary_dir/wrong-native.aab" <<'PY'
import re
import sys
import zipfile

source, destination = sys.argv[1:]
signature = re.compile(r"^META-INF/(MANIFEST\.MF|[^/]+\.(SF|RSA|DSA|EC))$", re.I)
target = "base/lib/arm64-v8a/libsodium.so"
changed = False
with zipfile.ZipFile(source, "r") as source_zip, zipfile.ZipFile(destination, "w") as output_zip:
    for info in source_zip.infolist():
        if signature.fullmatch(info.filename):
            continue
        payload = source_zip.read(info)
        if info.filename == target:
            payload = payload[:-1] + bytes([payload[-1] ^ 1])
            changed = True
        output_zip.writestr(info, payload)
if not changed:
    raise SystemExit(f"native mutation target missing: {target}")
PY
resign_with_debug_key "$temporary_dir/wrong-native.aab"
expect_failure \
  "mutated native payload" \
  "Embedded native library checksum mismatch" \
  "$VERIFY" "$temporary_dir/wrong-native.aab" "$expected_commit"
fi

[[ "$positive_count" == "7" ]] ||
  fail "expected 7 positive artifacts; got $positive_count."
if [[ "$signer_mode" == "keystore" ]]; then
  expected_negative_count=$((84 + linux_resource_negative_count))
else
  expected_negative_count=$((76 + linux_resource_negative_count))
fi
[[ "$negative_count" == "$expected_negative_count" ]] ||
  fail "expected $expected_negative_count $signer_mode-mode adversarial artifacts; got $negative_count."

echo "[android-ias-aab-test] $positive_count positive + $negative_count $signer_mode-mode adversarial artifacts passed"
