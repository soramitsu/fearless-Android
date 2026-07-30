#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PRODUCTION_UPLOAD_CERTIFICATE_SHA256="40391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
PINNED_PUBLIC_FIREBASE_SHA256="3085af682c8c8a166142f7ef867e0c3fa06a6f87436f3a15497f8ae7dac93d2f"
PINNED_BUNDLETOOL_SHA256="a099cfa1543f55593bc2ed16a70a7c67fe54b1747bb7301f37fdfd6d91028e29"
temporary_dir=""
verified_output=""
verified_output_parent=""
verified_output_parent_identity=""
verified_output_identity=""
verified_output_created=false
verified_output_success=false
test_hook="${IAS_AAB_VERIFIER_TEST_HOOK:-}"
test_hook_dir="${IAS_AAB_VERIFIER_TEST_HOOK_DIR:-}"

cleanup() {
  if [[ "$verified_output_created" == "true" &&
    "$verified_output_success" != "true" &&
    -n "$verified_output_parent" &&
    -n "$verified_output_parent_identity" &&
    -n "$verified_output_identity" ]]; then
    python3 - \
      "$verified_output_parent" \
      "${verified_output##*/}" \
      "$verified_output_parent_identity" \
      "$verified_output_identity" <<'PY' || true
import os
import stat
import sys

parent, name, expected_parent, expected_output = sys.argv[1:]
flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
try:
    fd = os.open(os.path.sep, flags)
    try:
        for part in [item for item in parent.split(os.path.sep) if item]:
            child = os.open(part, flags, dir_fd=fd)
            os.close(fd)
            fd = child
        metadata = os.fstat(fd)
        if f"{metadata.st_dev}:{metadata.st_ino}" != expected_parent:
            raise OSError("parent identity changed")
        output = os.stat(name, dir_fd=fd, follow_symlinks=False)
        identity = f"{output.st_dev}:{output.st_ino}:{output.st_size}"
        if stat.S_ISREG(output.st_mode) and identity == expected_output:
            os.unlink(name, dir_fd=fd)
            os.fsync(fd)
    finally:
        os.close(fd)
except OSError:
    pass
PY
  fi
  [[ -z "$temporary_dir" || ! -d "$temporary_dir" || -L "$temporary_dir" ]] ||
    rm -rf "$temporary_dir"
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

fail() {
  echo "[android-ias-aab][error] $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  FEARLESS_UTILS_COMMIT=<exact-utils-commit> \
  FEARLESS_UTILS_EFFECTIVE_TREE=<exact-utils-effective-tree> \
  BUNDLETOOL_JAR=/path/to/pinned-bundletool.jar \
    scripts/verify-android-internal-app-sharing-aab.sh --unsigned \
      <unsigned-ias.aab> <source-commit> [verified-unsigned-output.aab]

  FEARLESS_UTILS_COMMIT=<exact-utils-commit> \
  FEARLESS_UTILS_EFFECTIVE_TREE=<exact-utils-effective-tree> \
  BUNDLETOOL_JAR=/path/to/pinned-bundletool.jar \
  EXPECTED_IAS_DEBUG_CERT_SHA256=<uppercase-fingerprint> \
  ANDROID_IAS_DEBUG_KEYSTORE_PATH=/path/to/run-bound.jks \
    scripts/verify-android-internal-app-sharing-aab.sh \
      <app-internalAppSharing.aab> <source-commit> [verified-output.aab]

  FEARLESS_UTILS_COMMIT=<exact-utils-commit> \
  FEARLESS_UTILS_EFFECTIVE_TREE=<exact-utils-effective-tree> \
  BUNDLETOOL_JAR=/path/to/pinned-bundletool.jar \
  EXPECTED_IAS_DEBUG_CERT_SHA256=<uppercase-fingerprint> \
  ANDROID_IAS_DEBUG_KEYSTORE_PATH=/path/to/qualifier-only.jks \
    scripts/verify-android-internal-app-sharing-aab.sh --signed-from \
      <unsigned-ias.aab> <signed-ias.aab> <source-commit> [verified-output.aab]

For fresh-job qualification, replace ANDROID_IAS_DEBUG_KEYSTORE_PATH with
ANDROID_IAS_DEBUG_CERTIFICATE_PATH=/path/to/public-certificate.pem-or-der.
The two signer inputs are mutually exclusive; the public mode never needs or
accepts the producer's private key.

Unsigned mode forbids every signer input. Signed-from mode additionally proves
that external signing changed no non-signature ZIP entry. Every mode verifies a
private snapshot, then optionally writes those exact verified bytes to a new
private handoff path. This script never uploads to Google and does not establish
production signing or migration coverage.

SHA-256 checks use sha256sum when available and the macOS-compatible
"shasum -a 256" fallback otherwise.
USAGE
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

extract_single_rfc_certificate() {
  local source_path="$1" destination_path="$2" label="$3"
  python3 - "$source_path" "$destination_path" "$label" <<'PY'
import os
import re
import stat
import sys

source_path, destination_path, label = sys.argv[1:]
maximum_bytes = 1_048_576
flags = (
    os.O_RDONLY
    | getattr(os, "O_NOFOLLOW", 0)
    | getattr(os, "O_CLOEXEC", 0)
)
try:
    source_fd = os.open(source_path, flags)
except OSError as error:
    raise SystemExit(
        f"[android-ias-aab][error] {label} could not be opened safely: {error}"
    )
try:
    before = os.fstat(source_fd)
    if (
        not stat.S_ISREG(before.st_mode)
        or before.st_nlink != 1
        or before.st_size <= 0
        or before.st_size > maximum_bytes
    ):
        raise ValueError("unsafe keytool output")
    chunks = bytearray()
    while len(chunks) <= maximum_bytes:
        chunk = os.read(source_fd, min(65_536, maximum_bytes + 1 - len(chunks)))
        if not chunk:
            break
        chunks.extend(chunk)
    payload = bytes(chunks)
    after = os.fstat(source_fd)
    identity_fields = (
        "st_dev",
        "st_ino",
        "st_size",
        "st_mtime_ns",
        "st_ctime_ns",
        "st_nlink",
    )
    if (
        len(payload) > maximum_bytes
        or len(payload) != before.st_size
        or any(
            getattr(before, field) != getattr(after, field)
            for field in identity_fields
        )
    ):
        raise ValueError("keytool output changed while it was read")
except (OSError, ValueError) as error:
    raise SystemExit(
        f"[android-ias-aab][error] {label} could not be read safely: {error}"
    )
finally:
    os.close(source_fd)

begin = b"-----BEGIN CERTIFICATE-----"
end = b"-----END CERTIFICATE-----"
if payload.count(begin) != 1 or payload.count(end) != 1:
    raise SystemExit(
        f"[android-ias-aab][error] {label} must expose exactly one X.509 certificate."
    )
start = payload.index(begin)
finish = payload.index(end, start) + len(end)
certificate = payload[start:finish]
if not re.fullmatch(
    rb"-----BEGIN CERTIFICATE-----\r?\n"
    rb"(?:[A-Za-z0-9+/]{1,76}={0,2}\r?\n)+"
    rb"-----END CERTIFICATE-----",
    certificate,
):
    raise SystemExit(
        f"[android-ias-aab][error] {label} contains malformed RFC certificate data."
    )
certificate = certificate.replace(b"\r\n", b"\n") + b"\n"
try:
    with open(destination_path, "xb") as destination:
        destination.write(certificate)
        destination.flush()
        os.fsync(destination.fileno())
except OSError as error:
    raise SystemExit(
        f"[android-ias-aab][error] {label} could not be isolated safely: {error}"
    )
PY
}

file_mode() {
  if stat -f '%Lp' "$1" 2>/dev/null; then
    return
  fi
  stat -c '%a' "$1" 2>/dev/null || fail "file mode could not be derived."
}

require_regular_file() {
  local path="$1" label="$2" maximum_bytes="$3"
  local bytes links
  [[ "$path" != *$'\n'* && "$path" != *$'\r'* ]] ||
    fail "$label path is malformed."
  [[ ! -L "$path" && -f "$path" && -s "$path" ]] ||
    fail "$label must be a non-empty regular, non-symlink file."
  bytes="$(wc -c <"$path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size is malformed."
  ((bytes <= maximum_bytes)) || fail "$label exceeds its maximum size."
  if links="$(stat -f '%l' "$path" 2>/dev/null)"; then
    :
  elif links="$(stat -c '%h' "$path" 2>/dev/null)"; then
    :
  else
    fail "$label filesystem link count could not be derived."
  fi
  [[ "$links" == "1" ]] || fail "$label must have exactly one filesystem link."
}

snapshot_file() {
  local source="$1" destination="$2" maximum="$3" identity="$4" label="$5"
  python3 - "$source" "$destination" "$maximum" "$identity" "$label" <<'PY'
import hashlib, json, os, stat, sys
source, destination, maximum, identity_path, label = sys.argv[1:]
flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
try:
    source_fd = os.open(source, flags)
except OSError as error:
    raise SystemExit(f"[android-ias-aab][error] could not securely open {label}: {error}")
try:
    before = os.fstat(source_fd)
    if not stat.S_ISREG(before.st_mode) or before.st_nlink != 1:
        raise SystemExit(f"[android-ias-aab][error] {label} changed before snapshot.")
    if before.st_size <= 0 or before.st_size > int(maximum):
        raise SystemExit(f"[android-ias-aab][error] {label} snapshot size is unsafe.")
    output_fd = os.open(
        destination,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )
    digest = hashlib.sha256()
    try:
        while True:
            chunk = os.read(source_fd, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
            view = memoryview(chunk)
            while view:
                view = view[os.write(output_fd, view):]
        os.fsync(output_fd)
    finally:
        os.close(output_fd)
    after = os.fstat(source_fd)
    fields = ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_ctime_ns", "st_nlink")
    if any(getattr(before, field) != getattr(after, field) for field in fields):
        raise SystemExit(f"[android-ias-aab][error] {label} changed during snapshot.")
    metadata = {field: getattr(before, field) for field in fields}
    metadata["sha256"] = digest.hexdigest()
    with open(identity_path, "x", encoding="utf-8") as output:
        json.dump(metadata, output, sort_keys=True)
        output.write("\n")
    print(metadata["sha256"])
finally:
    os.close(source_fd)
PY
}

preflight_aab_archive() {
  local artifact_path="$1" label="$2"
  python3 - "$artifact_path" "$label" <<'PY'
import os
import stat
import struct
import sys
import unicodedata

path, label = sys.argv[1:]
MAX_ARCHIVE_BYTES = 262_144_000
MAX_ENTRIES = 50_000
MAX_NAME_BYTES = 1_024
MAX_COMPONENT_BYTES = 255
MAX_EXTRA_BYTES = 16_384
MAX_COMMENT_BYTES = 1_024
MAX_ENTRY_COMPRESSED_BYTES = 262_144_000
MAX_ENTRY_UNCOMPRESSED_BYTES = 536_870_912
MAX_TOTAL_COMPRESSED_BYTES = 262_144_000
MAX_TOTAL_UNCOMPRESSED_BYTES = 1_073_741_824
MAX_ENTRY_RATIO = 200
MAX_TOTAL_RATIO = 50
LOCAL_HEADER = b"PK\x03\x04"
CENTRAL_HEADER = b"PK\x01\x02"
DATA_DESCRIPTOR = b"PK\x07\x08"
END_OF_CENTRAL_DIRECTORY = b"PK\x05\x06"
SUPPORTED_METHODS = {0, 8}
SUPPORTED_FLAGS = 0x080E
UNSUPPORTED_EXTRA_FIELDS = {
    0x0001: "ZIP64",
    0x0017: "strong-encryption",
    0x6375: "Unicode-comment",
    0x7075: "Unicode-path",
    0x9901: "AES-encryption",
}


class ArchiveError(Exception):
    pass


def reject(message):
    raise ArchiveError(message)


def decode_utf8(raw, field):
    if len(raw) > MAX_NAME_BYTES:
        reject(f"{field} exceeds the UTF-8 byte-length cap")
    try:
        return raw.decode("utf-8", "strict")
    except UnicodeDecodeError as error:
        reject(f"{field} is not strict UTF-8: {error}")


def validate_extra(raw, field):
    if len(raw) > MAX_EXTRA_BYTES:
        reject(f"{field} exceeds the ZIP extra-field cap")
    cursor = 0
    identifiers = set()
    while cursor < len(raw):
        if len(raw) - cursor < 4:
            reject(f"{field} has truncated ZIP extra metadata")
        identifier, length = struct.unpack_from("<HH", raw, cursor)
        cursor += 4
        if cursor + length > len(raw):
            reject(f"{field} has malformed ZIP extra metadata")
        if identifier in identifiers:
            reject(f"{field} repeats ZIP extra metadata identifier 0x{identifier:04x}")
        identifiers.add(identifier)
        if identifier in UNSUPPORTED_EXTRA_FIELDS:
            reject(
                f"{field} uses unsupported {UNSUPPORTED_EXTRA_FIELDS[identifier]} metadata"
            )
        cursor += length


def validate_name(raw, flags, field):
    name = decode_utf8(raw, field)
    if not raw or not name:
        reject(f"{field} is empty")
    if not flags & 0x0800 and any(byte >= 0x80 for byte in raw):
        reject(f"{field} contains non-ASCII UTF-8 without the ZIP UTF-8 flag")
    if "\\" in name or name.startswith("/") or name.startswith("~"):
        reject(f"{field} is not a safe relative POSIX path")
    if len(name) >= 2 and name[0].isalpha() and name[1] == ":":
        reject(f"{field} contains an absolute drive path")
    if name != unicodedata.normalize("NFC", name):
        reject(f"{field} is not Unicode NFC-normalized")
    for character in name:
        category = unicodedata.category(character)
        if ord(character) == 127 or category in {"Cc", "Cf", "Cs", "Co", "Cn"}:
            reject(f"{field} contains a control or unsafe Unicode code point")
    components = name.split("/")
    if components[-1] == "":
        components = components[:-1]
    if not components or any(component in {"", ".", ".."} for component in components):
        reject(f"{field} contains an empty or traversal path component")
    if any(len(component.encode("utf-8")) > MAX_COMPONENT_BYTES for component in components):
        reject(f"{field} contains an overlong path component")
    return name


def validate_flags(flags, method, field):
    if flags & 0x0001 or flags & 0x0040:
        reject(f"{field} is encrypted")
    if flags & ~SUPPORTED_FLAGS:
        reject(f"{field} uses unsupported general-purpose flags 0x{flags:04x}")
    if method == 0 and flags & 0x0006:
        reject(f"{field} applies DEFLATE flags to a stored entry")


def read_exact(stream, offset, length, field):
    stream.seek(offset)
    payload = stream.read(length)
    if len(payload) != length:
        reject(f"{field} is truncated")
    return payload


flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
try:
    descriptor = os.open(path, flags)
except OSError as error:
    raise SystemExit(
        f"[android-ias-aab][error] archive preflight could not open {label}: {error}"
    )

try:
    metadata = os.fstat(descriptor)
    if (
        not stat.S_ISREG(metadata.st_mode)
        or metadata.st_nlink != 1
        or metadata.st_uid != os.geteuid()
        or stat.S_IMODE(metadata.st_mode) != 0o600
        or metadata.st_size <= 0
        or metadata.st_size > MAX_ARCHIVE_BYTES
    ):
        reject("private snapshot identity or size is unsafe")
    with os.fdopen(descriptor, "rb", closefd=False) as archive:
        tail_length = min(metadata.st_size, 22 + 65_535)
        tail_offset = metadata.st_size - tail_length
        tail = read_exact(archive, tail_offset, tail_length, "ZIP end metadata")
        candidates = []
        search_end = len(tail)
        while True:
            position = tail.rfind(END_OF_CENTRAL_DIRECTORY, 0, search_end)
            if position < 0:
                break
            if position + 22 <= len(tail):
                comment_length = struct.unpack_from("<H", tail, position + 20)[0]
                if position + 22 + comment_length == len(tail):
                    candidates.append(position)
            search_end = position
        if len(candidates) != 1:
            reject("ZIP end-of-central-directory metadata is missing or ambiguous")
        eocd_offset = tail_offset + candidates[0]
        eocd = read_exact(archive, eocd_offset, 22, "ZIP end metadata")
        (
            signature,
            disk_number,
            central_disk,
            disk_entries,
            total_entries,
            central_size,
            central_offset,
            archive_comment_length,
        ) = struct.unpack("<4s4H2IH", eocd)
        if signature != END_OF_CENTRAL_DIRECTORY:
            reject("ZIP end signature is malformed")
        if disk_number != 0 or central_disk != 0 or disk_entries != total_entries:
            reject("multi-disk ZIP metadata is unsupported")
        if (
            total_entries in {0, 0xFFFF}
            or central_size == 0xFFFFFFFF
            or central_offset == 0xFFFFFFFF
        ):
            reject("empty or ZIP64 AAB metadata is unsupported")
        if total_entries > MAX_ENTRIES:
            reject("ZIP entry count exceeds the archive preflight cap")
        if archive_comment_length > MAX_COMMENT_BYTES:
            reject("ZIP archive comment exceeds the UTF-8 byte-length cap")
        archive_comment = read_exact(
            archive, eocd_offset + 22, archive_comment_length, "ZIP archive comment"
        )
        decode_utf8(archive_comment, "ZIP archive comment")
        if central_offset + central_size != eocd_offset:
            reject("central-directory bounds are malformed or contain trailing records")
        if central_offset <= 0 or central_offset > metadata.st_size:
            reject("central-directory offset is outside the archive")

        cursor = central_offset
        entries = []
        exact_names = set()
        folded_names = set()
        local_offsets = set()
        total_compressed = 0
        total_uncompressed = 0
        for index in range(total_entries):
            fixed = read_exact(archive, cursor, 46, f"central entry {index}")
            fields = struct.unpack("<4s6H3I5H2I", fixed)
            if fields[0] != CENTRAL_HEADER:
                reject(f"central entry {index} has a malformed header")
            (
                _,
                version_made,
                version_needed,
                entry_flags,
                method,
                _modified_time,
                _modified_date,
                crc32,
                compressed_size,
                uncompressed_size,
                name_length,
                extra_length,
                comment_length,
                entry_disk,
                _internal_attributes,
                external_attributes,
                local_offset,
            ) = fields
            if version_needed > 63 or (version_made & 0xFF) > 63:
                reject(f"central entry {index} requires an unsupported ZIP version")
            if entry_disk != 0:
                reject(f"central entry {index} references an unsupported ZIP disk")
            if method not in SUPPORTED_METHODS:
                reject(f"central entry {index} uses unsupported compression method {method}")
            validate_flags(entry_flags, method, f"central entry {index}")
            if extra_length > MAX_EXTRA_BYTES or comment_length > MAX_COMMENT_BYTES:
                reject(f"central entry {index} has oversized metadata")
            variable = read_exact(
                archive,
                cursor + 46,
                name_length + extra_length + comment_length,
                f"central entry {index} metadata",
            )
            raw_name = variable[:name_length]
            raw_extra = variable[name_length:name_length + extra_length]
            raw_comment = variable[name_length + extra_length:]
            name = validate_name(raw_name, entry_flags, f"central entry {index} name")
            validate_extra(raw_extra, f"central entry {index}")
            decode_utf8(raw_comment, f"central entry {index} comment")
            collision_key = unicodedata.normalize("NFC", name).casefold()
            if name in exact_names:
                reject(f"duplicate ZIP entry name: {name}")
            if collision_key in folded_names:
                reject(f"case-insensitive ZIP entry-name collision: {name}")
            exact_names.add(name)
            folded_names.add(collision_key)
            if local_offset in local_offsets:
                reject(f"central entry {index} reuses a local-header offset")
            local_offsets.add(local_offset)
            if compressed_size > MAX_ENTRY_COMPRESSED_BYTES:
                reject(f"central entry {index} exceeds the per-entry compressed-size cap")
            if uncompressed_size > MAX_ENTRY_UNCOMPRESSED_BYTES:
                reject(f"central entry {index} exceeds the per-entry uncompressed-size cap")
            if uncompressed_size and compressed_size == 0:
                reject(f"central entry {index} has an impossible zero compressed size")
            if (
                uncompressed_size > 1_048_576
                and uncompressed_size > compressed_size * MAX_ENTRY_RATIO
            ):
                reject(f"central entry {index} exceeds the per-entry compression-ratio cap")
            if name.endswith("/") and (compressed_size != 0 or uncompressed_size != 0):
                reject(f"central directory entry {index} has a non-empty directory payload")
            unix_mode = external_attributes >> 16
            file_type = stat.S_IFMT(unix_mode)
            if file_type not in {0, stat.S_IFREG, stat.S_IFDIR}:
                reject(f"central entry {index} represents an unsafe special file")
            if file_type == stat.S_IFDIR and not name.endswith("/"):
                reject(f"central entry {index} has inconsistent directory metadata")
            total_compressed += compressed_size
            total_uncompressed += uncompressed_size
            if total_compressed > MAX_TOTAL_COMPRESSED_BYTES:
                reject("ZIP payload exceeds the total compressed-size cap")
            if total_uncompressed > MAX_TOTAL_UNCOMPRESSED_BYTES:
                reject("ZIP payload exceeds the total uncompressed-size cap")
            entries.append(
                (
                    index,
                    raw_name,
                    entry_flags,
                    method,
                    crc32,
                    compressed_size,
                    uncompressed_size,
                    local_offset,
                )
            )
            cursor += 46 + name_length + extra_length + comment_length
        if cursor != central_offset + central_size:
            reject("central-directory size does not match its exact entry set")
        if (
            total_uncompressed > 8_388_608
            and total_uncompressed > max(total_compressed, 1) * MAX_TOTAL_RATIO
        ):
            reject("ZIP payload exceeds the total compression-ratio cap")

        spans = []
        for (
            index,
            raw_name,
            entry_flags,
            method,
            crc32,
            compressed_size,
            uncompressed_size,
            local_offset,
        ) in entries:
            fixed = read_exact(archive, local_offset, 30, f"local entry {index}")
            fields = struct.unpack("<4s5H3I2H", fixed)
            if fields[0] != LOCAL_HEADER:
                reject(f"local entry {index} has a malformed header")
            (
                _,
                version_needed,
                local_flags,
                local_method,
                _modified_time,
                _modified_date,
                local_crc32,
                local_compressed_size,
                local_uncompressed_size,
                local_name_length,
                local_extra_length,
            ) = fields
            if version_needed > 63:
                reject(f"local entry {index} requires an unsupported ZIP version")
            if local_flags != entry_flags or local_method != method:
                reject(f"local entry {index} disagrees with central compression metadata")
            validate_flags(local_flags, local_method, f"local entry {index}")
            if local_extra_length > MAX_EXTRA_BYTES:
                reject(f"local entry {index} has oversized extra metadata")
            variable = read_exact(
                archive,
                local_offset + 30,
                local_name_length + local_extra_length,
                f"local entry {index} metadata",
            )
            local_name = variable[:local_name_length]
            local_extra = variable[local_name_length:]
            if local_name != raw_name:
                reject(f"local entry {index} name disagrees with the central directory")
            validate_name(local_name, local_flags, f"local entry {index} name")
            validate_extra(local_extra, f"local entry {index}")
            if entry_flags & 0x0008:
                if (
                    local_crc32 not in {0, crc32}
                    or local_compressed_size not in {0, compressed_size}
                    or local_uncompressed_size not in {0, uncompressed_size}
                ):
                    reject(f"local entry {index} has inconsistent deferred sizes")
            elif (
                local_crc32 != crc32
                or local_compressed_size != compressed_size
                or local_uncompressed_size != uncompressed_size
            ):
                reject(f"local entry {index} size or CRC disagrees with the central directory")
            data_offset = local_offset + 30 + local_name_length + local_extra_length
            data_end = data_offset + compressed_size
            if data_end > central_offset:
                reject(f"local entry {index} compressed payload exceeds archive bounds")
            span_end = data_end
            if entry_flags & 0x0008:
                descriptor_prefix = read_exact(
                    archive, data_end, 4, f"entry {index} data descriptor"
                )
                if descriptor_prefix == DATA_DESCRIPTOR:
                    descriptor_data = read_exact(
                        archive, data_end + 4, 12, f"entry {index} data descriptor"
                    )
                    span_end += 16
                else:
                    descriptor_data = descriptor_prefix + read_exact(
                        archive, data_end + 4, 8, f"entry {index} data descriptor"
                    )
                    span_end += 12
                if struct.unpack("<III", descriptor_data) != (
                    crc32,
                    compressed_size,
                    uncompressed_size,
                ):
                    reject(f"entry {index} data descriptor disagrees with central metadata")
            if span_end > central_offset:
                reject(f"local entry {index} metadata overlaps the central directory")
            spans.append((local_offset, span_end, index))

        spans.sort()
        expected_offset = 0
        for start, end, index in spans:
            if start != expected_offset:
                reject(f"local entry {index} is overlapping or separated by hidden data")
            if end <= start:
                reject(f"local entry {index} has invalid byte bounds")
            expected_offset = end
        if expected_offset != central_offset:
            reject("local ZIP records do not exactly cover the pre-central payload")
except ArchiveError as error:
    raise SystemExit(
        f"[android-ias-aab][error] archive preflight rejected {label}: {error}"
    )
except (OSError, OverflowError, struct.error) as error:
    raise SystemExit(
        f"[android-ias-aab][error] archive preflight rejected {label}: malformed ZIP metadata: {error}"
    )
finally:
    os.close(descriptor)
PY
}

run_limited_command() {
  local label="$1" stdout_path="$2" stderr_path="$3"
  shift 3
  python3 - "$label" "$stdout_path" "$stderr_path" "$@" <<'PY'
import ctypes
import os
import platform
import resource
import signal
import subprocess
import sys
import time

label, stdout_path, stderr_path, *command = sys.argv[1:]
CPU_SECONDS = 180
WALL_SECONDS = 300
MEMORY_BYTES = 4_294_967_296
TOTAL_RSS_BYTES = 3_221_225_472
FILE_BYTES = 536_870_912
OPEN_FILES = 256
PROCESSES = 256
system = platform.system()


def fail(message):
    raise SystemExit(f"[android-ias-aab][error] {label} resource isolation failed: {message}")


if not command:
    fail("no command was supplied")
required = ["RLIMIT_CPU", "RLIMIT_FSIZE", "RLIMIT_NOFILE"]
if system == "Linux":
    required.extend(["RLIMIT_AS", "RLIMIT_NPROC"])
for name in required:
    if not hasattr(resource, name):
        fail(f"required {name} support is unavailable on {system}")


def cap_limit(kind, requested):
    soft, hard = resource.getrlimit(kind)
    unlimited = resource.RLIM_INFINITY
    new_hard = requested if hard == unlimited else min(hard, requested)
    new_soft = new_hard if soft == unlimited else min(soft, new_hard)
    if new_soft <= 0 or new_hard <= 0:
        raise RuntimeError("an inherited resource limit is unusable")
    resource.setrlimit(kind, (new_soft, new_hard))


def restrict_child():
    cap_limit(resource.RLIMIT_CPU, CPU_SECONDS)
    cap_limit(resource.RLIMIT_FSIZE, FILE_BYTES)
    cap_limit(resource.RLIMIT_NOFILE, OPEN_FILES)
    if system == "Linux":
        cap_limit(resource.RLIMIT_AS, MEMORY_BYTES)
        cap_limit(resource.RLIMIT_NPROC, PROCESSES)
    # macOS rejects useful RLIMIT_RSS values and documents it as advisory.
    # Bundletool's explicit heap/direct/metaspace caps retain practical local
    # memory containment; Linux CI additionally has hard AS and aggregate RSS.
    if hasattr(resource, "RLIMIT_CORE"):
        resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def open_output(path):
    if path == os.devnull:
        return os.open(os.devnull, os.O_WRONLY)
    return os.open(
        path,
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | getattr(os, "O_NOFOLLOW", 0),
        0o600,
    )


def enable_linux_subreaper():
    if system != "Linux":
        return
    if not os.path.isdir("/proc/self"):
        fail("required Linux /proc process accounting is unavailable")
    libc = ctypes.CDLL(None, use_errno=True)
    if not hasattr(libc, "prctl"):
        fail("required Linux prctl subreaper support is unavailable")
    if libc.prctl(36, 1, 0, 0, 0) != 0:  # PR_SET_CHILD_SUBREAPER
        fail(f"could not enable the Linux child subreaper: errno {ctypes.get_errno()}")


def linux_process_table():
    table = {}
    try:
        process_names = os.listdir("/proc")
    except OSError as error:
        fail(f"could not enumerate Linux processes: {error}")
    for process_name in process_names:
        if not process_name.isdigit():
            continue
        try:
            with open(f"/proc/{process_name}/stat", encoding="ascii") as stream:
                process_stat = stream.read()
        except FileNotFoundError:
            continue
        except (OSError, UnicodeError) as error:
            fail(f"could not inspect Linux process {process_name}: {error}")
        closing_parenthesis = process_stat.rfind(")")
        fields = process_stat[closing_parenthesis + 2:].split()
        if closing_parenthesis < 0 or len(fields) < 20:
            fail(f"Linux process metadata is malformed for pid {process_name}")
        table[int(process_name)] = int(fields[1])
    return table


def linux_tree_members():
    if system != "Linux" or process is None:
        return {process.pid} if process is not None and process.poll() is None else set()
    table = linux_process_table()
    roots = {os.getpid()}
    members = set()
    changed = True
    while changed:
        changed = False
        for process_id, parent_id in table.items():
            if process_id not in members and parent_id in roots | members:
                members.add(process_id)
                changed = True
    return members


def linux_total_rss(process_ids):
    if system != "Linux":
        return 0
    total = 0
    for process_id in process_ids:
        try:
            with open(f"/proc/{process_id}/status", encoding="ascii") as stream:
                for line in stream:
                    if line.startswith("VmRSS:"):
                        fields = line.split()
                        if len(fields) != 3 or fields[2] != "kB":
                            fail(f"Linux RSS metadata is malformed for pid {process_id}")
                        total += int(fields[1]) * 1_024
                        break
        except FileNotFoundError:
            continue
        except (OSError, UnicodeError, ValueError) as error:
            fail(f"could not inspect Linux RSS for pid {process_id}: {error}")
    return total


enable_linux_subreaper()


stdout_fd = None
stderr_fd = None
process = None


def signal_tree(signal_number):
    if process is None:
        return
    try:
        os.killpg(process.pid, signal_number)
    except ProcessLookupError:
        pass
    if system == "Linux":
        for process_id in sorted(linux_tree_members(), reverse=True):
            try:
                os.kill(process_id, signal_number)
            except ProcessLookupError:
                pass


def reap_orphans():
    if system != "Linux":
        return
    while True:
        try:
            waited, _status = os.waitpid(-1, os.WNOHANG)
        except ChildProcessError:
            break
        if waited == 0:
            break


def terminate_tree():
    if process is None:
        return
    signal_tree(signal.SIGTERM)
    try:
        process.wait(timeout=5)
    except subprocess.TimeoutExpired:
        signal_tree(signal.SIGKILL)
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
    if system == "Linux":
        deadline = time.monotonic() + 5
        while True:
            reap_orphans()
            remaining = linux_tree_members()
            if not remaining:
                break
            for process_id in remaining:
                try:
                    os.kill(process_id, signal.SIGKILL)
                except ProcessLookupError:
                    pass
            if time.monotonic() >= deadline:
                break
            time.sleep(0.02)
        reap_orphans()


def interrupted(_signal_number, _frame):
    terminate_tree()
    raise SystemExit(130)


try:
    stdout_fd = open_output(stdout_path)
    if stderr_path == stdout_path:
        stderr_fd = os.dup(stdout_fd)
    else:
        stderr_fd = open_output(stderr_path)
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=stdout_fd,
            stderr=stderr_fd,
            close_fds=True,
            start_new_session=True,
            preexec_fn=restrict_child,
        )
    except (OSError, RuntimeError, subprocess.SubprocessError) as error:
        fail(f"could not start the bounded process: {error}")
    for signal_number in (signal.SIGHUP, signal.SIGINT, signal.SIGTERM):
        signal.signal(signal_number, interrupted)
    deadline = time.monotonic() + WALL_SECONDS
    while process.poll() is None:
        members = linux_tree_members()
        if system == "Linux" and linux_total_rss(members) > TOTAL_RSS_BYTES:
            terminate_tree()
            os.write(
                stderr_fd,
                f"{label} exceeded the aggregate {TOTAL_RSS_BYTES}-byte RSS cap\n".encode(),
            )
            raise SystemExit(125)
        if time.monotonic() >= deadline:
            terminate_tree()
            os.write(stderr_fd, f"{label} exceeded the {WALL_SECONDS}s wall-time cap\n".encode())
            raise SystemExit(124)
        time.sleep(0.05)
    reap_orphans()
    lingering = linux_tree_members()
    if lingering:
        terminate_tree()
        os.write(
            stderr_fd,
            f"{label} left {len(lingering)} descendant process(es); terminated\n".encode(),
        )
        raise SystemExit(125)
    if process.returncode < 0:
        os.write(
            stderr_fd,
            f"{label} terminated by signal {-process.returncode} under resource limits\n".encode(),
        )
        raise SystemExit(128 - process.returncode)
    raise SystemExit(process.returncode)
finally:
    if process is not None and process.poll() is None:
        try:
            terminate_tree()
        except BaseException:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
    for descriptor in (stdout_fd, stderr_fd):
        if descriptor is not None:
            try:
                os.close(descriptor)
            except OSError:
                pass
PY
}

verify_original_unchanged() {
  local source="$1" identity="$2" label="$3"
  python3 - "$source" "$identity" "$label" <<'PY'
import hashlib, json, os, stat, sys
source, identity_path, label = sys.argv[1:]
with open(identity_path, encoding="utf-8") as stream:
    expected = json.load(stream)
try:
    fd = os.open(source, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
except OSError as error:
    raise SystemExit(f"[android-ias-aab][error] {label} changed after snapshot: {error}")
try:
    actual = os.fstat(fd)
    if not stat.S_ISREG(actual.st_mode):
        raise SystemExit(f"[android-ias-aab][error] {label} is no longer regular.")
    fields = ("st_dev", "st_ino", "st_size", "st_mtime_ns", "st_ctime_ns", "st_nlink")
    if any(getattr(actual, field) != expected[field] for field in fields):
        raise SystemExit(f"[android-ias-aab][error] {label} identity changed after snapshot.")
    digest = hashlib.sha256()
    while True:
        chunk = os.read(fd, 1024 * 1024)
        if not chunk:
            break
        digest.update(chunk)
    if digest.hexdigest() != expected["sha256"]:
        raise SystemExit(f"[android-ias-aab][error] {label} bytes changed after snapshot.")
finally:
    os.close(fd)
PY
}

validate_test_hook_configuration() {
  local mode="${IAS_AAB_VERIFIER_TEST_MODE:-}"
  if [[ -z "$mode" && -z "$test_hook" && -z "$test_hook_dir" ]]; then
    return
  fi
  [[ "$mode" == "true" ]] ||
    fail "IAS verifier test hooks require IAS_AAB_VERIFIER_TEST_MODE=true."
  [[ -n "$test_hook" && -n "$test_hook_dir" ]] ||
    fail "IAS verifier test hooks require one hook and one private hook directory."
  case "$test_hook" in
    after-primary-snapshots|after-signer-snapshot|before-publication|\
    publication-parent-open|publication-partial-ready|publication-linked)
      ;;
    *)
      fail "IAS verifier test hook is not an approved synchronization point."
      ;;
  esac
  python3 - "$ROOT_DIR" "$test_hook_dir" <<'PY'
import os
import stat
import sys

root, path = sys.argv[1:]
if (
    not os.path.isabs(path)
    or "\n" in path
    or "\r" in path
    or os.path.realpath(path) == os.path.realpath(root)
    or os.path.commonpath((os.path.realpath(root), os.path.realpath(path)))
        == os.path.realpath(root)
):
    raise SystemExit(
        "[android-ias-aab][error] test hook directory must be an absolute path "
        "outside the source checkout."
    )
flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
fd = os.open(os.path.sep, flags)
try:
    for part in [item for item in path.split(os.path.sep) if item]:
        child = os.open(part, flags, dir_fd=fd)
        os.close(fd)
        fd = child
    metadata = os.fstat(fd)
    if metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o700:
        raise SystemExit(
            "[android-ias-aab][error] test hook directory must be owner-controlled "
            "mode 0700."
        )
    if os.listdir(fd):
        raise SystemExit(
            "[android-ias-aab][error] test hook directory must start empty."
        )
finally:
    os.close(fd)
PY
}

run_test_hook() {
  local stage="$1"
  [[ "$test_hook" == "$stage" ]] || return 0
  python3 - "$test_hook_dir" "$stage" <<'PY'
import os
import stat
import sys
import time

path, stage = sys.argv[1:]
flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
directory_fd = os.open(os.path.sep, flags)
try:
    for part in [item for item in path.split(os.path.sep) if item]:
        child = os.open(part, flags, dir_fd=directory_fd)
        os.close(directory_fd)
        directory_fd = child
    metadata = os.fstat(directory_fd)
    if metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o700:
        raise OSError("hook directory ownership or mode changed")
    marker = f"{stage}.ready"
    resume = f"{stage}.continue"
    marker_fd = os.open(
        marker,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0),
        0o600,
        dir_fd=directory_fd,
    )
    try:
        os.write(marker_fd, f"{os.getpid()}\n".encode("ascii"))
        os.fsync(marker_fd)
    finally:
        os.close(marker_fd)
    os.fsync(directory_fd)
    deadline = time.monotonic() + 60
    while True:
        try:
            resume_fd = os.open(
                resume,
                os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=directory_fd,
            )
        except FileNotFoundError:
            if time.monotonic() >= deadline:
                raise TimeoutError("timed out waiting for test continuation")
            time.sleep(0.02)
            continue
        try:
            resume_metadata = os.fstat(resume_fd)
            payload = os.read(resume_fd, 64)
            if (
                not stat.S_ISREG(resume_metadata.st_mode)
                or resume_metadata.st_uid != os.geteuid()
                or stat.S_IMODE(resume_metadata.st_mode) != 0o600
                or resume_metadata.st_nlink != 1
                or payload != b"continue\n"
            ):
                raise OSError("test continuation file is unsafe")
        finally:
            os.close(resume_fd)
        os.unlink(resume, dir_fd=directory_fd)
        os.unlink(marker, dir_fd=directory_fd)
        os.fsync(directory_fd)
        break
except (OSError, TimeoutError) as error:
    raise SystemExit(f"[android-ias-aab][error] IAS verifier test hook failed closed: {error}")
finally:
    os.close(directory_fd)
PY
}

version_property() {
  local property="$1" value
  value="$({
    awk -F= -v property="$property" '
      $1 == property { count++; value=$2 }
      END { if (count == 1) print value; else exit 1 }
    ' "$ROOT_DIR/versioning/version.properties"
  })" || fail "versioning/version.properties must define exactly one $property."
  [[ -n "$value" && "$value" != *[[:space:]]* ]] ||
    fail "$property is malformed."
  printf '%s' "$value"
}

verify_source_and_utils() {
  local phase="$1" actual_commit actual_tree utils_output
  local source_args
  actual_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
  actual_tree="$(git -C "$ROOT_DIR" rev-parse 'HEAD^{tree}')"
  [[ "$actual_commit" == "$expected_commit" ]] ||
    fail "$phase checkout HEAD does not match the requested IAS commit."
  [[ "$actual_tree" =~ ^[0-9a-f]{40}$ ]] ||
    fail "$phase checkout tree is malformed."
  source_args=("$expected_commit" "$actual_tree")
  if [[ -e "$ROOT_DIR/fearless-utils-Android" ]]; then
    [[ ! -L "$ROOT_DIR/fearless-utils-Android" &&
      -d "$ROOT_DIR/fearless-utils-Android/.git" ]] ||
      fail "$phase nested fearless-utils checkout is unsafe."
    if [[ -n "${FEARLESS_UTILS_PATH:-}" ]]; then
      configured_utils="$(cd "$FEARLESS_UTILS_PATH" && pwd -P)"
      nested_utils="$(cd "$ROOT_DIR/fearless-utils-Android" && pwd -P)"
      [[ "$configured_utils" == "$nested_utils" ]] ||
        fail "$phase configured fearless-utils path differs from the nested checkout."
    fi
    source_args+=(--allow-fearless-utils)
  fi
  CI=true "$ROOT_DIR/scripts/verify-android-release-source-tree.sh" \
    "${source_args[@]}" >/dev/null ||
    fail "$phase source-tree verification failed."

  [[ "${FEARLESS_UTILS_COMMIT:-}" =~ ^[0-9a-f]{40}$ &&
    "${FEARLESS_UTILS_EFFECTIVE_TREE:-}" =~ ^[0-9a-f]{40}$ ]] ||
    fail "$phase requires exact fearless-utils commit/effective-tree bindings."
  utils_output="$("$ROOT_DIR/scripts/ensure-fearless-utils.sh")" ||
    fail "$phase fearless-utils verification failed."
  [[ "$(grep -Ec "^\[fearless-utils\] Using .+ at ${FEARLESS_UTILS_COMMIT}$" <<<"$utils_output")" == "1" &&
    "$(grep -Fxc "[fearless-utils] Effective source tree ${FEARLESS_UTILS_EFFECTIVE_TREE}" <<<"$utils_output")" == "1" ]] ||
    fail "$phase fearless-utils binding does not match the verified checkout."
}

verify_unsigned_signature_state() {
  local artifact_path="$1" artifact_label="$2"
  python3 - "$artifact_path" "$artifact_label" <<'PY'
import re
import sys
import zipfile

path, label = sys.argv[1:]
signature = re.compile(
    r"META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))\Z",
    re.IGNORECASE,
)
try:
    with zipfile.ZipFile(path) as archive:
        names = [entry.filename for entry in archive.infolist()]
except (OSError, zipfile.BadZipFile) as error:
    raise SystemExit(f"[android-ias-aab][error] {label} is unreadable: {error}")
folded = [name.casefold() for name in names]
if len(names) != len(set(names)) or len(folded) != len(set(folded)):
    raise SystemExit(
        f"[android-ias-aab][error] {label} contains duplicate ZIP entry names."
    )
found = sorted(name for name in names if signature.fullmatch(name))
if found:
    raise SystemExit(
        f"[android-ias-aab][error] {label} contains JAR signature metadata."
    )
PY
}

verify_external_signing_transform() {
  local unsigned_path="$1" signed_path="$2"
  python3 - "$unsigned_path" "$signed_path" <<'PY'
import hashlib
import re
import sys
import zipfile

unsigned_path, signed_path = sys.argv[1:]
signature = re.compile(
    r"META-INF/(MANIFEST\.MF|([^/]+)\.(SF|RSA|DSA|EC))\Z",
    re.IGNORECASE,
)


def load(path):
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
        names = [entry.filename for entry in infos]
        folded = [name.casefold() for name in names]
        if len(names) != len(set(names)) or len(folded) != len(set(folded)):
            raise ValueError("duplicate ZIP entry names")
        total_size = 0
        payload = {}
        for entry in infos:
            if entry.is_dir():
                continue
            if entry.file_size < 0 or entry.file_size > 536_870_912:
                raise ValueError("ZIP entry exceeds the signing-transform size cap")
            total_size += entry.file_size
            if total_size > 1_073_741_824:
                raise ValueError("ZIP payload exceeds the signing-transform size cap")
            digest = hashlib.sha256()
            with archive.open(entry) as stream:
                while chunk := stream.read(1024 * 1024):
                    digest.update(chunk)
            payload[entry.filename] = digest.hexdigest()
    return names, payload


try:
    unsigned_names, unsigned_payload = load(unsigned_path)
    signed_names, signed_payload = load(signed_path)
except (OSError, ValueError, zipfile.BadZipFile) as error:
    raise SystemExit(
        f"[android-ias-aab][error] external signing transform is unreadable: {error}"
    )

if any(signature.fullmatch(name) for name in unsigned_names):
    raise SystemExit(
        "[android-ias-aab][error] signed-from source contains JAR signature metadata."
    )

signature_names = [name for name in signed_names if signature.fullmatch(name)]
manifest = [name for name in signature_names if name.casefold() == "meta-inf/manifest.mf"]
signature_files = [
    match for name in signature_names
    if (match := signature.fullmatch(name)) and (match.group(3) or "").upper() == "SF"
]
signature_blocks = [
    match for name in signature_names
    if (match := signature.fullmatch(name))
    and (match.group(3) or "").upper() in {"RSA", "DSA", "EC"}
]
if (
    len(manifest) != 1
    or len(signature_files) != 1
    or len(signature_blocks) != 1
    or signature_files[0].group(2).casefold()
        != signature_blocks[0].group(2).casefold()
):
    raise SystemExit(
        "[android-ias-aab][error] external signing did not add one exact JAR signature set."
    )

for name in signature_names:
    signed_payload.pop(name, None)
if unsigned_payload != signed_payload:
    raise SystemExit(
        "[android-ias-aab][error] external signing changed non-signature AAB entries."
    )

unsigned_structural = {
    name for name in unsigned_names if name.endswith("/")
}
signed_structural = {
    name for name in signed_names
    if name.endswith("/") and name.casefold() != "meta-inf/"
}
if unsigned_structural != signed_structural:
    raise SystemExit(
        "[android-ias-aab][error] external signing changed non-signature ZIP structure."
    )
PY
}

verification_mode="signed"
unsigned_source_input=""
case "${1:-}" in
  --unsigned)
    verification_mode="unsigned"
    shift
    ;;
  --signed-from)
    verification_mode="signed-from"
    [[ "$#" -ge 2 ]] || {
      usage
      exit 2
    }
    unsigned_source_input="$2"
    shift 2
    ;;
  --*)
    usage
    exit 2
    ;;
esac

[[ "$#" -eq 2 || "$#" -eq 3 ]] || {
  usage
  exit 2
}
input_artifact="$1"
expected_commit="$2"
verified_output="${3:-}"
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "Source commit must be an exact lowercase 40-character Git commit."
[[ -n "${BUNDLETOOL_JAR:-}" ]] || fail "BUNDLETOOL_JAR is required."
input_bundletool="$BUNDLETOOL_JAR"
expected_debug_fingerprint="${EXPECTED_IAS_DEBUG_CERT_SHA256:-}"
debug_keystore_input="${ANDROID_IAS_DEBUG_KEYSTORE_PATH:-}"
debug_certificate_input="${ANDROID_IAS_DEBUG_CERTIFICATE_PATH:-}"
if [[ "$verification_mode" == "unsigned" ]]; then
  for signer_name in \
    ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    ANDROID_IAS_DEBUG_CERT_SHA256 \
    EXPECTED_IAS_DEBUG_CERT_SHA256; do
    if printenv "$signer_name" >/dev/null 2>&1; then
      fail "Unsigned IAS verification forbids every signer input: $signer_name."
    fi
  done
else
  [[ "$expected_debug_fingerprint" =~ ^[0-9A-F]{64}$ ]] ||
    fail "EXPECTED_IAS_DEBUG_CERT_SHA256 must be an exact uppercase SHA-256 fingerprint."
fi
unset \
  ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
  ANDROID_IAS_DEBUG_CERT_SHA256 \
  EXPECTED_IAS_DEBUG_CERT_SHA256

for command_name in awk chmod git grep java keytool mkdir mktemp printenv python3 sed stat tr unzip wc; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
validate_test_hook_configuration
require_regular_file "$input_artifact" "IAS AAB" 262144000
require_regular_file "$input_bundletool" "BUNDLETOOL_JAR" 41943040
if [[ "$verification_mode" == "signed-from" ]]; then
  require_regular_file "$unsigned_source_input" "unsigned IAS source AAB" 262144000
fi
[[ "$(sha256_file "$input_bundletool")" == "$PINNED_BUNDLETOOL_SHA256" ]] ||
  fail "BUNDLETOOL_JAR does not match the exact reviewed checksum."

if [[ -n "$verified_output" ]]; then
  [[ "$verified_output" = /* && "$verified_output" != *$'\n'* &&
    "$verified_output" != *$'\r'* ]] ||
    fail "verified output must be an absolute, well-formed path."
  case "$verified_output" in
    "$ROOT_DIR"|"$ROOT_DIR"/*)
      fail "verified output must stay outside the source checkout."
      ;;
  esac
  verified_output_parent="${verified_output%/*}"
  [[ -n "$verified_output_parent" ]] || verified_output_parent="/"
  verified_output_parent_identity="$(
    python3 - "$ROOT_DIR" "$verified_output" <<'PY'
import os
import stat
import sys

raw_root, raw_destination = sys.argv[1:]
root = os.path.abspath(raw_root)
destination = os.path.abspath(raw_destination)
if os.path.normpath(raw_destination) != raw_destination:
    raise SystemExit(
        "[android-ias-aab][error] verified output path must be lexically canonical."
    )
parent = os.path.dirname(destination)
cursor = os.path.sep
for part in [item for item in parent.split(os.path.sep) if item]:
    cursor = os.path.join(cursor, part)
    try:
        metadata = os.lstat(cursor)
    except OSError as error:
        raise SystemExit(
            f"[android-ias-aab][error] verified output parent is unavailable: {error}"
        )
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISDIR(metadata.st_mode):
        raise SystemExit(
            "[android-ias-aab][error] verified output parent must not traverse symlinks."
        )
canonical_root = os.path.realpath(root)
canonical_parent = os.path.realpath(parent)
if os.path.commonpath((canonical_root, canonical_parent)) == canonical_root:
    raise SystemExit(
        "[android-ias-aab][error] verified output must remain outside the source checkout."
    )
parent_metadata = os.lstat(parent)
if parent_metadata.st_uid != os.geteuid() or stat.S_IMODE(parent_metadata.st_mode) != 0o700:
    raise SystemExit(
        "[android-ias-aab][error] verified output parent must be owner-controlled mode 0700."
    )
try:
    os.lstat(destination)
except FileNotFoundError:
    pass
else:
    raise SystemExit(
        "[android-ias-aab][error] verified output already exists; refusing to overwrite it."
    )
print(f"{parent_metadata.st_dev}:{parent_metadata.st_ino}")
PY
  )"
  [[ "$verified_output_parent_identity" =~ ^[0-9]+:[0-9]+$ ]] ||
    fail "verified output parent identity is malformed."
fi

verify_source_and_utils "pre-verification"
temporary_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-ias-aab.XXXXXX")"
[[ -d "$temporary_dir" && ! -L "$temporary_dir" ]] ||
  fail "Unable to create a safe IAS verification directory."
chmod 700 "$temporary_dir"

artifact="$temporary_dir/candidate.aab"
artifact_identity="$temporary_dir/candidate-input-identity.json"
artifact_sha256="$(
  snapshot_file "$input_artifact" "$artifact" 262144000 \
    "$artifact_identity" "IAS AAB"
)"
unsigned_source_sha256=""
unsigned_source_identity=""
unsigned_artifact=""
if [[ "$verification_mode" == "signed-from" ]]; then
  unsigned_artifact="$temporary_dir/unsigned-source.aab"
  unsigned_source_identity="$temporary_dir/unsigned-source-input-identity.json"
  unsigned_source_sha256="$(
    snapshot_file \
      "$unsigned_source_input" "$unsigned_artifact" 262144000 \
      "$unsigned_source_identity" "unsigned IAS source AAB"
  )"
fi
preflight_aab_archive "$artifact" "private IAS AAB snapshot"
if [[ "$verification_mode" == "signed-from" ]]; then
  preflight_aab_archive \
    "$unsigned_artifact" "private unsigned IAS source AAB snapshot"
fi

bundletool_snapshot="$temporary_dir/bundletool.jar"
bundletool_identity="$temporary_dir/bundletool-input-identity.json"
bundletool_sha256="$(
  snapshot_file "$input_bundletool" "$bundletool_snapshot" 41943040 \
    "$bundletool_identity" "BUNDLETOOL_JAR"
)"
[[ "$bundletool_sha256" == "$PINNED_BUNDLETOOL_SHA256" ]] ||
  fail "private bundletool snapshot does not match the exact reviewed checksum."
BUNDLETOOL_JAR="$bundletool_snapshot"
export BUNDLETOOL_JAR
run_test_hook after-primary-snapshots

archive_test_log="$temporary_dir/unzip-test.log"
if ! run_limited_command \
  "unzip archive test" "$archive_test_log" "$archive_test_log" \
  unzip -qq -t "$artifact"; then
  sed -n '1,80p' "$archive_test_log" >&2
  fail "The private IAS AAB snapshot is not a valid ZIP archive."
fi
python3 - "$artifact" <<'PY'
import sys
import zipfile

artifact = sys.argv[1]
with zipfile.ZipFile(artifact) as archive:
    manifests = sorted(
        info.filename
        for info in archive.infolist()
        if info.filename.endswith("/manifest/AndroidManifest.xml")
    )
if manifests != ["base/manifest/AndroidManifest.xml"]:
    raise SystemExit(
        "[android-ias-aab][error] IAS AAB must contain exactly the base application module."
    )
PY
bundletool_tmp="$temporary_dir/bundletool-tmp"
mkdir -m 700 "$bundletool_tmp"
bundletool_java_args=(
  -Xms64m
  -Xmx1536m
  -XX:MaxDirectMemorySize=512m
  -XX:MaxMetaspaceSize=512m
  -XX:+ExitOnOutOfMemoryError
  "-Djava.io.tmpdir=$bundletool_tmp"
)
bundletool_validate_log="$temporary_dir/bundletool-validate.log"
if ! run_limited_command \
  "bundletool validate" "$bundletool_validate_log" "$bundletool_validate_log" \
  java "${bundletool_java_args[@]}" -jar "$BUNDLETOOL_JAR" \
  validate --bundle "$artifact"; then
  sed -n '1,80p' "$bundletool_validate_log" >&2
  fail "bundletool validate rejected the private IAS AAB snapshot."
fi
if [[ "$verification_mode" == "unsigned" ]]; then
  verify_unsigned_signature_state "$artifact" "unsigned IAS AAB"
elif [[ "$verification_mode" == "signed-from" ]]; then
  verify_unsigned_signature_state "$unsigned_artifact" "signed-from source"
  verify_external_signing_transform "$unsigned_artifact" "$artifact"
fi

expected_version_name="$(version_property versionName)-ias"
expected_version_code="$(version_property versionCode)"
[[ "$expected_version_name" == "4.2.0-ias" ]] ||
  fail "IAS versionName must remain exactly 4.2.0-ias for this testing candidate."
[[ "$expected_version_code" == "230" ]] ||
  fail "IAS versionCode must remain exactly 230 for this testing candidate."

public_firebase="$ROOT_DIR/app/src/release/google-services.json"
require_regular_file "$public_firebase" "public Firebase configuration" 65536
[[ "$(sha256_file "$public_firebase")" == "$PINNED_PUBLIC_FIREBASE_SHA256" ]] ||
  fail "public Firebase configuration does not match the exact reviewed checksum."
python3 - "$public_firebase" <<'PY'
import json
import sys

def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result

try:
    with open(sys.argv[1], encoding="utf-8") as source:
        data = json.load(source, object_pairs_hook=unique_object)
except (OSError, UnicodeError, json.JSONDecodeError, ValueError) as error:
    raise SystemExit(f"[android-ias-aab][error] public Firebase JSON is unsafe: {error}")
project = data.get("project_info")
clients = data.get("client")
if not isinstance(project, dict) or not isinstance(clients, list):
    raise SystemExit("[android-ias-aab][error] public Firebase JSON structure is malformed.")
matching = [
    client for client in clients
    if isinstance(client, dict)
    and client.get("client_info", {}).get("android_client_info", {}).get("package_name")
        == "jp.co.soramitsu.fearless"
]
keys = [
    item.get("current_key")
    for client in clients if isinstance(client, dict)
    for item in client.get("api_key", []) if isinstance(item, dict)
]
web = [
    item.get("client_id")
    for item in (matching[0].get("oauth_client", []) if len(matching) == 1 else [])
    if isinstance(item, dict) and item.get("client_type") == 3
]
valid = (
    project.get("project_id") == "fearless-public"
    and project.get("project_number") == "000000000000"
    and project.get("firebase_url") == "https://fearless-public.firebaseio.com"
    and project.get("storage_bucket") == "fearless-public.appspot.com"
    and len(matching) == 1
    and matching[0].get("client_info", {}).get("mobilesdk_app_id")
        == "1:000000000000:android:0000000000000000000002"
    and web == ["000000000000-public-1-1.apps.googleusercontent.com"]
    and bool(keys)
    and all(value == "" for value in keys)
)
if not valid:
    raise SystemExit(
        "[android-ias-aab][error] only the reviewed public-placeholder Firebase JSON is allowed."
    )
PY

certificate_validity_source="$temporary_dir/CheckCertificateValidity.java"
cat >"$certificate_validity_source" <<'JAVA'
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.security.auth.x500.X500Principal;

public class CheckCertificateValidity {
    private static X509Certificate parseStrict(Path path) throws Exception {
        byte[] raw = Files.readAllBytes(path);
        byte[] der = raw;
        String ascii = new String(raw, StandardCharsets.US_ASCII);
        if (ascii.startsWith("-----BEGIN CERTIFICATE-----")) {
            Pattern pem = Pattern.compile(
                "\\A-----BEGIN CERTIFICATE-----\\R"
                + "([A-Za-z0-9+/=\\r\\n]+)"
                + "-----END CERTIFICATE-----\\R?\\z"
            );
            Matcher matcher = pem.matcher(ascii);
            if (!matcher.matches()) {
                throw new IllegalArgumentException(
                    "certificate PEM contains extra or malformed material"
                );
            }
            der = Base64.getMimeDecoder().decode(matcher.group(1));
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(der)) {
            X509Certificate certificate = (X509Certificate)
                CertificateFactory.getInstance("X.509").generateCertificate(input);
            if (input.available() != 0 || !Arrays.equals(der, certificate.getEncoded())) {
                throw new IllegalArgumentException(
                    "certificate input contains trailing or non-canonical material"
                );
            }
            return certificate;
        }
    }

    public static void main(String[] arguments) throws Exception {
        if (
            arguments.length < 2
            || arguments.length > 3
            || !(arguments[1].equals("structure") || arguments[1].equals("validity"))
        ) {
            throw new IllegalArgumentException(
                "certificate path, validation mode, and optional subject are required"
            );
        }
        X509Certificate certificate = parseStrict(Path.of(arguments[0]));
        if (arguments[1].equals("validity")) {
            certificate.checkValidity();
        }
        if (
            arguments.length == 3
            && !certificate.getSubjectX500Principal().equals(
                new X500Principal(arguments[2])
            )
        ) {
            throw new IllegalArgumentException("certificate subject mismatch");
        }
    }
}
JAVA

ias_fingerprint=""
signer_binding_input=""
signer_binding_identity=""
signer_binding_label=""
if [[ "$verification_mode" != "unsigned" ]]; then
  debug_certificate="$temporary_dir/debug-certificate.txt"
  ias_certificate="$temporary_dir/ias-certificate.txt"
  debug_certificate_pem="$temporary_dir/debug-certificate.pem"
  ias_certificate_pem="$temporary_dir/ias-certificate.pem"
  if [[ -n "$debug_keystore_input" && -n "$debug_certificate_input" ]]; then
    fail "Run-bound IAS keystore and public certificate inputs are mutually exclusive."
  fi
  if [[ -z "$debug_keystore_input" && -z "$debug_certificate_input" ]]; then
    if [[ "${CI:-false}" == "true" ]]; then
      fail "CI verification requires exactly one run-bound IAS keystore or public certificate input."
    fi
    debug_keystore_input="$HOME/.android/debug.keystore"
  fi

  if [[ -n "$debug_certificate_input" ]]; then
    signer_binding_input="$debug_certificate_input"
    signer_binding_identity="$temporary_dir/debug-certificate-input-identity.json"
    signer_binding_label="Android debug certificate"
    require_regular_file \
      "$debug_certificate_input" "$signer_binding_label" 65536
    debug_certificate_snapshot="$temporary_dir/debug-certificate-input"
    snapshot_file \
      "$debug_certificate_input" "$debug_certificate_snapshot" 65536 \
      "$signer_binding_identity" "$signer_binding_label" >/dev/null
    run_test_hook after-signer-snapshot
    java "$certificate_validity_source" \
      "$debug_certificate_snapshot" structure >/dev/null 2>&1 ||
      fail "The public Android debug certificate must contain exactly one canonical X.509 certificate and no private material."
    keytool -J-Duser.language=en -J-Duser.country=US -printcert -v \
      -file "$debug_certificate_snapshot" >"$debug_certificate" 2>&1 ||
      fail "The run-bound Android debug certificate could not be read."
    keytool -printcert -rfc -file "$debug_certificate_snapshot" \
      >"$debug_certificate_pem" 2>/dev/null ||
      fail "The run-bound Android debug certificate could not be exported."
  else
    signer_binding_input="$debug_keystore_input"
    signer_binding_identity="$temporary_dir/debug-keystore-input-identity.json"
    signer_binding_label="Android debug keystore"
    require_regular_file "$debug_keystore_input" "$signer_binding_label" 1048576
    [[ "$(file_mode "$debug_keystore_input")" == "600" ]] ||
      fail "Android debug keystore must have mode 0600."
    debug_keystore="$temporary_dir/debug.keystore"
    snapshot_file "$debug_keystore_input" "$debug_keystore" 1048576 \
      "$signer_binding_identity" "$signer_binding_label" >/dev/null
    run_test_hook after-signer-snapshot
    keytool -J-Duser.language=en -J-Duser.country=US -list -v \
      -keystore "$debug_keystore" -storepass android -alias androiddebugkey \
      >"$debug_certificate" 2>&1 ||
      fail "The run-bound Android debug certificate could not be read."
    keytool -exportcert -rfc -keystore "$debug_keystore" -storepass android \
      -alias androiddebugkey >"$debug_certificate_pem" 2>/dev/null ||
      fail "The run-bound Android debug certificate could not be exported."
  fi
  keytool -J-Duser.language=en -J-Duser.country=US \
    -printcert -jarfile "$artifact" >"$ias_certificate" 2>&1 ||
    fail "The IAS AAB signer certificate could not be read."
  ias_certificate_rfc="$temporary_dir/ias-certificate-rfc.txt"
  keytool -J-Duser.language=en -J-Duser.country=US \
    -printcert -rfc -jarfile "$artifact" \
    >"$ias_certificate_rfc" 2>/dev/null ||
    fail "The IAS AAB signer certificate could not be exported."
  extract_single_rfc_certificate \
    "$ias_certificate_rfc" "$ias_certificate_pem" \
    "The IAS AAB signer certificate" ||
    fail "The IAS AAB signer certificate could not be isolated."

java "$certificate_validity_source" \
  "$debug_certificate_pem" validity >/dev/null 2>&1 ||
  fail "The run-bound Android debug certificate is expired or not yet valid."
java "$certificate_validity_source" \
  "$ias_certificate_pem" validity >/dev/null 2>&1 ||
  fail "The IAS AAB signer certificate is expired or not yet valid."
if ! java "$certificate_validity_source" "$debug_certificate_pem" \
  validity 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1 ||
  ! java "$certificate_validity_source" "$ias_certificate_pem" \
    validity 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1; then
  fail "IAS must use the exact Android Debug certificate subject."
fi

  debug_fingerprint="$(
    awk -F'SHA256:' '/SHA256:/ { print $2; exit }' "$debug_certificate" |
      normalize_fingerprint
  )"
  ias_fingerprint="$(
    awk -F'SHA256:' '/SHA256:/ { print $2; exit }' "$ias_certificate" |
      normalize_fingerprint
  )"
  [[ "$debug_fingerprint" =~ ^[0-9A-F]{64}$ &&
    "$ias_fingerprint" =~ ^[0-9A-F]{64}$ ]] ||
    fail "Android debug certificate fingerprint could not be derived."
  [[ "$ias_fingerprint" == "$debug_fingerprint" ]] ||
    fail "IAS signer does not match the exact run-bound Android debug certificate."
  [[ "$ias_fingerprint" == "$expected_debug_fingerprint" ]] ||
    fail "IAS signer does not match the exact run-bound test certificate fingerprint."
  [[ "$ias_fingerprint" != "$PRODUCTION_UPLOAD_CERTIFICATE_SHA256" ]] ||
    fail "IAS must never use the Google Play upload certificate."

  signature_verification_log="$temporary_dir/signature-verification.log"
  if ! run_limited_command \
    "strict JAR signature verification" \
    "$signature_verification_log" "$signature_verification_log" \
    "$ROOT_DIR/scripts/verify-android-aab-jar-signature.sh" \
    "$artifact" "$debug_fingerprint"; then
    sed -n '1,120p' "$signature_verification_log" >&2
    fail "bounded strict JAR signature verification failed."
  fi
  sed -n '1,120p' "$signature_verification_log"
fi
identity_verification_log="$temporary_dir/identity-verification.log"
if ! run_limited_command \
  "AAB identity verification" \
  "$identity_verification_log" "$identity_verification_log" \
  "$ROOT_DIR/scripts/verify-android-aab-identity.sh" \
  "$artifact" jp.co.soramitsu.fearless "$expected_version_name" \
  "$expected_version_code" "$expected_commit"; then
  sed -n '1,160p' "$identity_verification_log" >&2
  fail "bounded AAB identity verification failed."
fi
sed -n '1,160p' "$identity_verification_log"

verify_resource() {
  local name="$1" expected="$2" output="$temporary_dir/resource-$1.txt"
  local error_output="$temporary_dir/resource-$1.error.txt"
  run_limited_command \
    "bundletool resource dump ($name)" "$output" "$error_output" \
    java "${bundletool_java_args[@]}" -jar "$BUNDLETOOL_JAR" \
    dump resources --bundle "$artifact" \
    --resource "string/$name" --values || {
    sed -n '1,80p' "$error_output" >&2
    fail "bundletool could not read IAS Firebase resource $name."
  }
  python3 - "$output" "$name" "$expected" <<'PY'
import re
import sys
path, name, expected = sys.argv[1:]
try:
    text = open(path, encoding="utf-8").read()
except (OSError, UnicodeError) as error:
    raise SystemExit(f"[android-ias-aab][error] could not read compiled resource {name}: {error}")
matches = re.findall(r'\[STR\] "(.*)"\s*$', text, re.MULTILINE)
if matches != [expected]:
    raise SystemExit(
        f"[android-ias-aab][error] compiled Firebase resource {name} "
        "does not have exactly the approved public-placeholder value."
    )
PY
}

verify_resource project_id fearless-public
verify_resource firebase_database_url https://fearless-public.firebaseio.com
verify_resource google_storage_bucket fearless-public.appspot.com
verify_resource google_app_id 1:000000000000:android:0000000000000000000002
verify_resource google_api_key ''
verify_resource google_crash_reporting_api_key ''
verify_resource default_web_client_id 000000000000-public-1-1.apps.googleusercontent.com
verify_resource gcm_defaultSenderId 000000000000

verify_original_unchanged "$input_artifact" "$artifact_identity" "IAS AAB"
verify_original_unchanged "$input_bundletool" "$bundletool_identity" "BUNDLETOOL_JAR"
if [[ "$verification_mode" == "signed-from" ]]; then
  verify_original_unchanged \
    "$unsigned_source_input" "$unsigned_source_identity" "unsigned IAS source AAB"
fi
if [[ "$verification_mode" != "unsigned" ]]; then
  verify_original_unchanged \
    "$signer_binding_input" "$signer_binding_identity" "$signer_binding_label"
fi
verify_source_and_utils "post-verification"

if [[ -n "$verified_output" ]]; then
  run_test_hook before-publication
  verified_output_identity="$(
    python3 - \
      "$artifact" \
      "$verified_output_parent" \
      "${verified_output##*/}" \
      "$artifact_sha256" \
      "$verified_output_parent_identity" \
      "$test_hook" \
      "$test_hook_dir" <<'PY'
import hashlib
import os
import secrets
import signal
import stat
import sys
import time

(
    source,
    parent_path,
    destination_name,
    expected_digest,
    expected_parent_identity,
    configured_hook,
    hook_path,
) = sys.argv[1:]
directory_flags = (
    os.O_RDONLY
    | getattr(os, "O_DIRECTORY", 0)
    | getattr(os, "O_NOFOLLOW", 0)
)
read_flags = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
parent_fd = None
source_fd = None
partial_name = (
    f".{destination_name}.partial.{os.getpid()}.{secrets.token_hex(12)}"
)
owned_identity = None
partial_created = False
destination_created = False
blocked_signals = {signal.SIGHUP, signal.SIGINT, signal.SIGTERM}


class PublicationError(Exception):
    pass


def open_secure_directory(path):
    descriptor = os.open(os.path.sep, directory_flags)
    try:
        for component in [item for item in path.split(os.path.sep) if item]:
            child = os.open(component, directory_flags, dir_fd=descriptor)
            os.close(descriptor)
            descriptor = child
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def parent_identity(descriptor):
    metadata = os.fstat(descriptor)
    return f"{metadata.st_dev}:{metadata.st_ino}"


def require_current_parent():
    try:
        current_fd = open_secure_directory(parent_path)
    except OSError as error:
        raise PublicationError(
            f"verified output parent changed during publication: {error}"
        ) from error
    try:
        if parent_identity(current_fd) != expected_parent_identity:
            raise PublicationError(
                "verified output parent identity changed during publication."
            )
    finally:
        os.close(current_fd)


def unlink_owned(name):
    if parent_fd is None or owned_identity is None:
        return
    try:
        metadata = os.stat(name, dir_fd=parent_fd, follow_symlinks=False)
        if (
            stat.S_ISREG(metadata.st_mode)
            and (metadata.st_dev, metadata.st_ino) == owned_identity
        ):
            os.unlink(name, dir_fd=parent_fd)
    except FileNotFoundError:
        pass
    except OSError:
        pass


def cleanup_publication():
    if destination_created:
        unlink_owned(destination_name)
    if partial_created:
        unlink_owned(partial_name)
    if parent_fd is not None:
        try:
            os.fsync(parent_fd)
        except OSError:
            pass


def interrupted(_signum, _frame):
    cleanup_publication()
    os._exit(130)


def run_publication_hook(stage):
    if configured_hook != stage:
        return
    hook_fd = open_secure_directory(hook_path)
    try:
        metadata = os.fstat(hook_fd)
        if metadata.st_uid != os.geteuid() or stat.S_IMODE(metadata.st_mode) != 0o700:
            raise PublicationError(
                "IAS verifier test hook directory changed during publication."
            )
        marker = f"{stage}.ready"
        resume = f"{stage}.continue"
        marker_fd = os.open(
            marker,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_NOFOLLOW", 0),
            0o600,
            dir_fd=hook_fd,
        )
        try:
            os.write(marker_fd, f"{os.getpid()}\n".encode("ascii"))
            os.fsync(marker_fd)
        finally:
            os.close(marker_fd)
        os.fsync(hook_fd)
        deadline = time.monotonic() + 60
        while True:
            try:
                resume_fd = os.open(resume, read_flags, dir_fd=hook_fd)
            except FileNotFoundError:
                if time.monotonic() >= deadline:
                    raise PublicationError(
                        "IAS verifier test hook timed out during publication."
                    )
                time.sleep(0.02)
                continue
            try:
                resume_metadata = os.fstat(resume_fd)
                payload = os.read(resume_fd, 64)
                if (
                    not stat.S_ISREG(resume_metadata.st_mode)
                    or resume_metadata.st_uid != os.geteuid()
                    or stat.S_IMODE(resume_metadata.st_mode) != 0o600
                    or resume_metadata.st_nlink != 1
                    or payload != b"continue\n"
                ):
                    raise PublicationError(
                        "IAS verifier test continuation changed during publication."
                    )
            finally:
                os.close(resume_fd)
            os.unlink(resume, dir_fd=hook_fd)
            os.unlink(marker, dir_fd=hook_fd)
            os.fsync(hook_fd)
            break
    finally:
        os.close(hook_fd)


for signal_number in (signal.SIGHUP, signal.SIGINT, signal.SIGTERM):
    signal.signal(signal_number, interrupted)

try:
    parent_fd = open_secure_directory(parent_path)
    parent_metadata = os.fstat(parent_fd)
    if (
        parent_identity(parent_fd) != expected_parent_identity
        or parent_metadata.st_uid != os.geteuid()
        or stat.S_IMODE(parent_metadata.st_mode) != 0o700
    ):
        raise PublicationError("verified output parent changed before publication.")
    run_publication_hook("publication-parent-open")
    require_current_parent()
    try:
        os.stat(destination_name, dir_fd=parent_fd, follow_symlinks=False)
    except FileNotFoundError:
        pass
    else:
        raise PublicationError(
            "verified output appeared before publication; refusing to overwrite it."
        )

    source_fd = os.open(source, read_flags)
    source_metadata = os.fstat(source_fd)
    if not stat.S_ISREG(source_metadata.st_mode) or source_metadata.st_nlink != 1:
        raise PublicationError("private IAS snapshot changed before publication.")

    previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, blocked_signals)
    try:
        output_fd = os.open(
            partial_name,
            os.O_WRONLY
            | os.O_CREAT
            | os.O_EXCL
            | getattr(os, "O_NOFOLLOW", 0),
            0o400,
            dir_fd=parent_fd,
        )
        partial_created = True
        partial_metadata = os.fstat(output_fd)
        owned_identity = (partial_metadata.st_dev, partial_metadata.st_ino)
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
    digest = hashlib.sha256()
    try:
        while True:
            chunk = os.read(source_fd, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
            view = memoryview(chunk)
            while view:
                written = os.write(output_fd, view)
                if written <= 0:
                    raise PublicationError("verified output copy made no progress.")
                view = view[written:]
        os.fsync(output_fd)
    finally:
        os.close(output_fd)
    if digest.hexdigest() != expected_digest:
        raise PublicationError("verified output copy changed bytes.")

    run_publication_hook("publication-partial-ready")
    require_current_parent()
    previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, blocked_signals)
    try:
        try:
            os.link(
                partial_name,
                destination_name,
                src_dir_fd=parent_fd,
                dst_dir_fd=parent_fd,
                follow_symlinks=False,
            )
        except FileExistsError as error:
            raise PublicationError(
                "verified output appeared during publication; refusing to overwrite it."
            ) from error
        destination_created = True
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
    os.fsync(parent_fd)
    run_publication_hook("publication-linked")
    require_current_parent()

    partial_metadata = os.stat(
        partial_name, dir_fd=parent_fd, follow_symlinks=False
    )
    destination_metadata = os.stat(
        destination_name, dir_fd=parent_fd, follow_symlinks=False
    )
    if (
        not stat.S_ISREG(partial_metadata.st_mode)
        or not stat.S_ISREG(destination_metadata.st_mode)
        or (partial_metadata.st_dev, partial_metadata.st_ino) != owned_identity
        or (destination_metadata.st_dev, destination_metadata.st_ino)
            != owned_identity
        or partial_metadata.st_nlink != 2
        or destination_metadata.st_nlink != 2
    ):
        raise PublicationError(
            "verified output publication did not preserve the exact snapshot inode."
        )

    previous_mask = signal.pthread_sigmask(signal.SIG_BLOCK, blocked_signals)
    try:
        os.unlink(partial_name, dir_fd=parent_fd)
        partial_created = False
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
    os.fsync(parent_fd)
    destination_metadata = os.stat(
        destination_name, dir_fd=parent_fd, follow_symlinks=False
    )
    if (
        not stat.S_ISREG(destination_metadata.st_mode)
        or destination_metadata.st_uid != os.geteuid()
        or stat.S_IMODE(destination_metadata.st_mode) != 0o400
        or destination_metadata.st_nlink != 1
        or (destination_metadata.st_dev, destination_metadata.st_ino)
            != owned_identity
    ):
        raise PublicationError("verified output metadata changed before handoff.")
    final_fd = os.open(destination_name, read_flags, dir_fd=parent_fd)
    final_digest = hashlib.sha256()
    try:
        while True:
            chunk = os.read(final_fd, 1024 * 1024)
            if not chunk:
                break
            final_digest.update(chunk)
    finally:
        os.close(final_fd)
    if final_digest.hexdigest() != expected_digest:
        raise PublicationError("verified output digest changed before handoff.")
    require_current_parent()
    print(
        f"{destination_metadata.st_dev}:"
        f"{destination_metadata.st_ino}:"
        f"{destination_metadata.st_size}"
    )
except BaseException as error:
    cleanup_publication()
    if isinstance(error, SystemExit):
        raise
    raise SystemExit(
        f"[android-ias-aab][error] verified output publication failed closed: {error}"
    )
finally:
    if source_fd is not None:
        os.close(source_fd)
    if parent_fd is not None:
        os.close(parent_fd)
PY
  )"
  [[ "$verified_output_identity" =~ ^[0-9]+:[0-9]+:[1-9][0-9]*$ ]] ||
    fail "verified output publication identity is malformed."
  verified_output_created=true
  verify_original_unchanged "$input_artifact" "$artifact_identity" "IAS AAB"
  verify_original_unchanged "$input_bundletool" "$bundletool_identity" "BUNDLETOOL_JAR"
  if [[ "$verification_mode" == "signed-from" ]]; then
    verify_original_unchanged \
      "$unsigned_source_input" "$unsigned_source_identity" "unsigned IAS source AAB"
  fi
  if [[ "$verification_mode" != "unsigned" ]]; then
    verify_original_unchanged \
      "$signer_binding_input" "$signer_binding_identity" "$signer_binding_label"
  fi
  verify_source_and_utils "post-publication"
  python3 - \
    "$verified_output_parent" \
    "${verified_output##*/}" \
    "$verified_output_parent_identity" \
    "$verified_output_identity" \
    "$artifact_sha256" <<'PY'
import hashlib
import os
import stat
import sys

parent, name, expected_parent, expected_output, expected_digest = sys.argv[1:]
flags = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
fd = os.open(os.path.sep, flags)
try:
    for part in [item for item in parent.split(os.path.sep) if item]:
        child = os.open(part, flags, dir_fd=fd)
        os.close(fd)
        fd = child
    parent_metadata = os.fstat(fd)
    if f"{parent_metadata.st_dev}:{parent_metadata.st_ino}" != expected_parent:
        raise OSError("verified output parent identity changed after publication")
    metadata = os.stat(name, dir_fd=fd, follow_symlinks=False)
    actual_identity = f"{metadata.st_dev}:{metadata.st_ino}:{metadata.st_size}"
    if (
        not stat.S_ISREG(metadata.st_mode)
        or metadata.st_uid != os.geteuid()
        or stat.S_IMODE(metadata.st_mode) != 0o400
        or metadata.st_nlink != 1
        or actual_identity != expected_output
    ):
        raise OSError("verified output identity changed after publication")
    output_fd = os.open(
        name, os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0), dir_fd=fd
    )
    digest = hashlib.sha256()
    try:
        while True:
            chunk = os.read(output_fd, 1024 * 1024)
            if not chunk:
                break
            digest.update(chunk)
    finally:
        os.close(output_fd)
    if digest.hexdigest() != expected_digest:
        raise OSError("verified output bytes changed after publication")
finally:
    os.close(fd)
PY
fi

verified_output_success=true

echo "[android-ias-aab] IAS_AAB_SHA256=$artifact_sha256"
if [[ "$verification_mode" == "unsigned" ]]; then
  echo "[android-ias-aab] IAS_SIGNATURE_STATE=UNSIGNED"
elif [[ "$verification_mode" == "signed-from" ]]; then
  echo "[android-ias-aab] IAS_SIGNER_SHA256=$ias_fingerprint"
  echo "[android-ias-aab] IAS_UNSIGNED_SOURCE_SHA256=$unsigned_source_sha256"
else
  echo "[android-ias-aab] IAS_SIGNER_SHA256=$ias_fingerprint"
fi
echo "[android-ias-aab] BUNDLETOOL_SHA256=$bundletool_sha256"
if [[ -n "$verified_output" ]]; then
  echo "[android-ias-aab] IAS_VERIFIED_AAB_PATH=$verified_output"
fi
if [[ "$verification_mode" == "unsigned" ]]; then
  cat <<'NOTICE'
[android-ias-aab] exact unsigned IAS identity, public Firebase resources, native payload, and bundletool validity verified
[android-ias-aab] UNSIGNED QUARANTINE ONLY: this artifact is not installable and must be signed only in the fresh qualifier.
NOTICE
else
  cat <<'NOTICE'
[android-ias-aab] exact IAS identity, public Firebase resources, native payload, bundletool validity, and debug signature verified
[android-ias-aab] TEST ONLY: Google IAS re-signing/debug signing cannot upgrade a Play-installed wallet.
[android-ias-aab] Never uninstall a funded wallet; this artifact does not prove production migration coverage.
NOTICE
fi
