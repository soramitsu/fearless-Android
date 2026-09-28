#!/usr/bin/env bash
set -euo pipefail

TEST_ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "$0")/.." && pwd))"
readonly TEST_ROOT_DIR
readonly MATERIALIZER="$TEST_ROOT_DIR/scripts/materialize-iroha-core-jvm.sh"

# shellcheck source=scripts/materialize-iroha-core-jvm.sh
source "$MATERIALIZER"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/iroha-core-materializer-test.XXXXXX")"
CUSTOM_TMP_PARENT="$TEST_ROOT_DIR/../build"
mkdir -p "$CUSTOM_TMP_PARENT"
CUSTOM_TMP_DIR="$(mktemp -d "$CUSTOM_TMP_PARENT/iroha-core-custom-tmp.XXXXXX")"
ROOT_ANCHOR_ESCAPE_DIR="$(mktemp -d "$CUSTOM_TMP_PARENT/iroha-root-anchor-escape.XXXXXX")"
trap 'rm -rf "$TMP_DIR" "$CUSTOM_TMP_DIR" "$ROOT_ANCHOR_ESCAPE_DIR"' EXIT
NEGATIVE_SCENARIOS=0

test_fail() {
  echo "[iroha-core-materializer-test] ERROR: $*" >&2
  exit 1
}

make_fixture() {
  local fixture_dir="$1"
  local profile="$2"
  local archive="$fixture_dir/$IROHA_CORE_ARCHIVE_NAME"
  mkdir -p "$fixture_dir"

  python3 - "$archive" "$profile" "$IROHA_CORE_TAG" "$IROHA_CORE_VERSION" <<'PY'
import hashlib
import io
import json
from pathlib import Path
import stat
import struct
import warnings
import zipfile
import sys


archive = Path(sys.argv[1])
profile = sys.argv[2]
tag = sys.argv[3]
version = sys.argv[4]
prefix = f"iroha-mobile-sdk-android-{tag}/"
artifact_root = f"{prefix}maven/org/hyperledger/iroha/sdk/core-jvm/"
coordinate = f"{artifact_root}{version}/"
stem = f"core-jvm-{version}"


def patch_entry(path_or_bytes, target, *, crc=False, encrypted=False):
    is_bytes = isinstance(path_or_bytes, (bytes, bytearray))
    data = bytearray(path_or_bytes if is_bytes else Path(path_or_bytes).read_bytes())
    central = data.find(b"PK\x01\x02")
    found = False
    while central >= 0:
        name_length = struct.unpack_from("<H", data, central + 28)[0]
        extra_length = struct.unpack_from("<H", data, central + 30)[0]
        comment_length = struct.unpack_from("<H", data, central + 32)[0]
        name = bytes(data[central + 46:central + 46 + name_length]).decode("utf-8")
        if name == target:
            local = struct.unpack_from("<I", data, central + 42)[0]
            if crc:
                old_crc = struct.unpack_from("<I", data, central + 16)[0]
                new_crc = old_crc ^ 1
                struct.pack_into("<I", data, central + 16, new_crc)
                struct.pack_into("<I", data, local + 14, new_crc)
            if encrypted:
                central_flags = struct.unpack_from("<H", data, central + 8)[0] | 1
                local_flags = struct.unpack_from("<H", data, local + 6)[0] | 1
                struct.pack_into("<H", data, central + 8, central_flags)
                struct.pack_into("<H", data, local + 6, local_flags)
            found = True
            break
        central = data.find(b"PK\x01\x02", central + 46 + name_length + extra_length + comment_length)
    if not found:
        raise RuntimeError(f"unable to patch ZIP member {target}")
    if is_bytes:
        return bytes(data)
    Path(path_or_bytes).write_bytes(data)


def make_nested_jar():
    stream = io.BytesIO()
    with zipfile.ZipFile(stream, "w", compression=zipfile.ZIP_DEFLATED) as jar:
        jar.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n")
        jar.writestr("sample.txt", "safe core payload\n")
        if profile == "nested-artifact":
            jar.writestr("lib/embedded.jar", b"PK\x03\x04nested")
        elif profile == "nested-symlink":
            link = zipfile.ZipInfo("linked-class")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            jar.writestr(link, "sample.txt")
    result = stream.getvalue()
    if profile == "nested-crc":
        result = patch_entry(result, "sample.txt", crc=True)
    return result


jar_bytes = make_nested_jar()
jar_sha256 = hashlib.sha256(jar_bytes).hexdigest()
pom = f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>org.hyperledger.iroha.sdk</groupId>
  <artifactId>core-jvm</artifactId>
  <version>{"wrong-version" if profile == "pom-mismatch" else version}</version>
</project>
'''.encode()
module = {
    "formatVersion": "1.1",
    "component": {
        "group": "org.hyperledger.iroha.sdk",
        "module": "wrong-artifact" if profile == "module-mismatch" else "core-jvm",
        "version": version,
    },
    "variants": [
        {
            "name": "runtimeElements",
            "files": [
                {
                    "name": f"{stem}.jar",
                    "url": f"{stem}.jar",
                    "size": len(jar_bytes),
                    "sha256": jar_sha256,
                }
            ],
        }
    ],
}
module_bytes = json.dumps(module, sort_keys=True).encode()

files = {
    f"{prefix}core-jvm/{stem}.jar": b"different raw jar" if profile == "raw-mismatch" else jar_bytes,
    f"{coordinate}{stem}.jar": jar_bytes,
    f"{coordinate}{stem}.pom": pom,
    f"{coordinate}{stem}.module": module_bytes,
}
for extension, payload in (("jar", jar_bytes), ("pom", pom), ("module", module_bytes)):
    for algorithm in ("md5", "sha1", "sha256", "sha512"):
        files[f"{coordinate}{stem}.{extension}.{algorithm}"] = hashlib.new(algorithm, payload).hexdigest().encode() + b"\n"

if profile.startswith("bad-sidecar-"):
    algorithm = profile.removeprefix("bad-sidecar-")
    files[f"{coordinate}{stem}.jar.{algorithm}"] = b"0" * (hashlib.new(algorithm).digest_size * 2) + b"\n"
elif profile == "missing-sidecar":
    del files[f"{coordinate}{stem}.jar.sha256"]
elif profile == "unexpected-coordinate-artifact":
    files[f"{coordinate}{stem}.aar"] = b"forbidden aar"
elif profile == "nested-coordinate-entry":
    files[f"{coordinate}nested/evil.txt"] = b"nested"
elif profile == "extra-core-version":
    files[f"{artifact_root}other-version/core-jvm-other-version.jar"] = b"other"
elif profile == "traversal":
    files[f"{prefix}../escape"] = b"escape"
elif profile == "absolute":
    files["/absolute-escape"] = b"escape"
elif profile == "backslash":
    files[f"{prefix}bad\\escape"] = b"escape"
elif profile == "drive":
    files["C:/drive-escape"] = b"escape"
elif profile == "portable-collision":
    files[f"{prefix}Collision"] = b"one"
    files[f"{prefix}collision"] = b"two"
elif profile == "compression-bomb":
    files[f"{prefix}bomb.bin"] = b"0" * (2 * 1024 * 1024)
elif profile == "entry-too-large":
    files[f"{prefix}large.bin"] = bytes(range(256)) * 512
elif profile == "total-too-large":
    files[f"{prefix}total-a.bin"] = bytes(range(256)) * 256
    files[f"{prefix}total-b.bin"] = bytes(range(255, -1, -1)) * 256
elif profile == "too-many-entries":
    for index in range(32):
        files[f"{prefix}count-{index:02d}"] = b""

manifest_name = f"{prefix}SHA256SUMS.txt"
if profile != "missing-internal-manifest":
    manifest_lines = [
        f"{hashlib.sha256(payload).hexdigest()}  {name[len(prefix):]}"
        for name, payload in sorted(files.items())
        if name.startswith(prefix)
    ]
    if profile == "bad-internal-checksum":
        manifest_lines[0] = "0" * 64 + manifest_lines[0][64:]
    elif profile == "duplicate-internal-path":
        manifest_lines.append(manifest_lines[0])
    elif profile == "malformed-internal-manifest":
        manifest_lines.append("not a checksum record")
    files[manifest_name] = ("\n".join(manifest_lines) + "\n").encode()
    if profile == "unlisted-internal-file":
        files[f"{prefix}unlisted.txt"] = b"not covered"

compression_overrides = {}
if profile == "compression-bomb":
    compression_overrides[f"{prefix}bomb.bin"] = zipfile.ZIP_DEFLATED
elif profile in ("entry-too-large", "total-too-large"):
    for name in files:
        if name.endswith(".bin"):
            compression_overrides[name] = zipfile.ZIP_STORED
elif profile == "unsupported-method":
    unsupported_name = f"{prefix}bzip2-entry"
    files[unsupported_name] = b"unsupported"
    compression_overrides[unsupported_name] = zipfile.ZIP_BZIP2

with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED) as outer:
    for name, payload in files.items():
        if profile == "symlink" and name == manifest_name:
            link = zipfile.ZipInfo(f"{prefix}malicious-link")
            link.create_system = 3
            link.external_attr = (stat.S_IFLNK | 0o777) << 16
            outer.writestr(link, "target")
        if profile == "special-file" and name == manifest_name:
            fifo = zipfile.ZipInfo(f"{prefix}malicious-fifo")
            fifo.create_system = 3
            fifo.external_attr = (stat.S_IFIFO | 0o600) << 16
            outer.writestr(fifo, b"")
        outer.writestr(name, payload, compress_type=compression_overrides.get(name, zipfile.ZIP_DEFLATED))

if profile == "duplicate-entry":
    target = f"{prefix}core-jvm/{stem}.jar"
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(archive, "a", compression=zipfile.ZIP_DEFLATED) as outer:
            outer.writestr(target, jar_bytes)
elif profile == "encrypted-entry":
    patch_entry(archive, manifest_name, encrypted=True)
elif profile == "outer-crc":
    patch_entry(archive, manifest_name, crc=True)
PY
}

fixture_pins() {
  local archive="$1"
  python3 - "$archive" "$IROHA_CORE_TAG" "$IROHA_CORE_VERSION" <<'PY'
import hashlib
from pathlib import Path
import sys
import zipfile

archive = Path(sys.argv[1])
tag = sys.argv[2]
version = sys.argv[3]
jar_name = (
    f"iroha-mobile-sdk-android-{tag}/maven/org/hyperledger/iroha/sdk/"
    f"core-jvm/{version}/core-jvm-{version}.jar"
)
archive_bytes = archive.read_bytes()
with zipfile.ZipFile(archive) as zf:
    jar_info = zf.getinfo(jar_name)
    with zf.open(jar_info) as stream:
        jar = stream.read()
print(hashlib.sha256(archive_bytes).hexdigest(), len(archive_bytes), hashlib.sha256(jar).hexdigest(), len(jar))
PY
}

run_fixture() {
  local fixture_dir="$1"
  local profile="$2"
  local output_dir="$3"
  local max_entries="${4:-256}"
  local max_total="${5:-268435456}"
  local max_entry="${6:-134217728}"
  local max_ratio="${7:-100}"
  local archive archive_sha archive_bytes jar_sha jar_bytes

  archive="$fixture_dir/$IROHA_CORE_ARCHIVE_NAME"
  make_fixture "$fixture_dir" "$profile"
  read -r archive_sha archive_bytes jar_sha jar_bytes <<<"$(fixture_pins "$archive")"
  iroha_core_materialize_validated_archive \
    "$archive" \
    "$archive_sha" \
    "$archive_bytes" \
    "$output_dir" \
    "$jar_sha" \
    "$jar_bytes" \
    "$max_entries" \
    "$max_total" \
    "$max_entry" \
    "$max_ratio"
}

expect_failure() {
  local label="$1"
  local profile="$2"
  local expected="$3"
  local max_entries="${4:-256}"
  local max_total="${5:-268435456}"
  local max_entry="${6:-134217728}"
  local max_ratio="${7:-100}"
  local fixture_dir="$TMP_DIR/failure-${label// /-}"
  local output status

  set +e
  output="$(run_fixture "$fixture_dir" "$profile" "$fixture_dir/output" "$max_entries" "$max_total" "$max_entry" "$max_ratio" 2>&1)"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    test_fail "$label unexpectedly passed"
  fi
  if [[ "$output" != *"$expected"* ]]; then
    printf '%s\n' "$output" >&2
    test_fail "$label did not report expected marker: $expected"
  fi
  [[ ! -e "$fixture_dir/output" ]] || test_fail "$label wrote output before validation completed"
  NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
  echo "[iroha-core-materializer-test] PASS: $label"
}

expect_command_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output status
  set +e
  output="$("$@" 2>&1)"
  status=$?
  set -e
  [[ "$status" -ne 0 ]] || test_fail "$label unexpectedly passed"
  if [[ "$output" != *"$expected"* ]]; then
    printf '%s\n' "$output" >&2
    test_fail "$label did not report expected marker: $expected"
  fi
  NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
  echo "[iroha-core-materializer-test] PASS: $label"
}

run_fake_download_case() {
  local label="$1"
  local mode="$2"
  local expected_status="$3"
  local case_dir="$TMP_DIR/fake-curl-$mode"
  local fake_bin="$case_dir/bin"
  local args_log="$case_dir/args.log"
  local temp_log="$case_dir/temp.log"
  local output status first_arg download_temp
  mkdir -p "$fake_bin" "$case_dir/tmp"

  python3 - "$fake_bin/curl" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
path.write_text(r'''#!/usr/bin/env bash
set -euo pipefail
printf '%s\n' "$@" > "$FAKE_CURL_ARGS_LOG"
[[ "${1:-}" == "--disable" ]] || exit 97
output=""
max_filesize=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "--output" ]]; then
    shift
    output="${1:-}"
  elif [[ "$1" == "--max-filesize" ]]; then
    shift
    max_filesize="${1:-}"
  fi
  shift || true
done
[[ -n "$output" ]] || exit 98
[[ "$max_filesize" == "$FAKE_CURL_EXPECTED_BYTES" ]] || exit 99
printf '%s\n' "$(dirname "$output")" > "$FAKE_CURL_TEMP_LOG"
if [[ "${FAKE_CURL_MODE:-success}" == "curl-failure" ]]; then
  printf 'partial download\n' > "$output"
  exit 22
elif [[ "${FAKE_CURL_MODE:-success}" == "oversize" ]]; then
  python3 - "$output" "$FAKE_CURL_EXPECTED_BYTES" <<'PY_FAKE_CURL'
from pathlib import Path
import sys
with Path(sys.argv[1]).open("wb") as stream:
    stream.truncate(int(sys.argv[2]) + 1)
PY_FAKE_CURL
  exit 63
fi
printf 'downloaded fixture\n' > "$output"
''')
path.chmod(0o755)
PY

  set +e
  output="$(
    PATH="$fake_bin:$PATH" \
    TMPDIR="$case_dir/tmp" \
    FAKE_CURL_ARGS_LOG="$args_log" \
    FAKE_CURL_TEMP_LOG="$temp_log" \
    FAKE_CURL_MODE="$mode" \
    FAKE_CURL_EXPECTED_BYTES="$IROHA_CORE_ARCHIVE_BYTES" \
    MATERIALIZER_FOR_TEST="$MATERIALIZER" \
    DOWNLOAD_TEST_MODE="$mode" \
    bash -c '
      source "$MATERIALIZER_FOR_TEST"
      iroha_core_materialize_validated_archive() {
        if [[ "$DOWNLOAD_TEST_MODE" == "validation-failure" ]]; then
          return 23
        fi
        return 0
      }
      iroha_core_main --download --output-dir "$TMPDIR/materialized"
    ' 2>&1
  )"
  status=$?
  set -e

  if [[ "$expected_status" == "success" ]]; then
    [[ "$status" -eq 0 ]] || {
      printf '%s\n' "$output" >&2
      test_fail "$label failed with status $status"
    }
  else
    [[ "$status" -ne 0 ]] || test_fail "$label unexpectedly passed"
    NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
  fi
  [[ -f "$args_log" ]] || test_fail "$label did not invoke fake curl"
  first_arg="$(sed -n '1p' "$args_log")"
  [[ "$first_arg" == "--disable" ]] || test_fail "$label did not put --disable first"
  grep -Fxq -- "$IROHA_CORE_RELEASE_URL" "$args_log" || test_fail "$label changed the pinned release URL"
  grep -Fxq -- "--proto" "$args_log" || test_fail "$label omitted the HTTPS protocol restriction"
  grep -Fxq -- "--proto-redir" "$args_log" || test_fail "$label omitted the redirect protocol restriction"
  grep -Fxq -- "--max-filesize" "$args_log" || test_fail "$label omitted the transfer size cap"
  grep -Fxq -- "$IROHA_CORE_ARCHIVE_BYTES" "$args_log" || test_fail "$label changed the transfer size cap"
  [[ -f "$temp_log" ]] || test_fail "$label did not record its download temp directory"
  download_temp="$(cat "$temp_log")"
  [[ ! -e "$download_temp" ]] || test_fail "$label left its download temp directory behind: $download_temp"
  echo "[iroha-core-materializer-test] PASS: $label"
}

expect_tmpdir_overlap_failure() {
  local label="$1"
  local output_dir="$2"
  local validation_repo_root="$3"
  local tmpdir_root="$4"
  local output status

  set +e
  output="$(
    TMPDIR="$tmpdir_root" iroha_core_materialize_validated_archive \
      "$wrong_pin_archive" \
      "$valid_sha" \
      "$valid_bytes" \
      "$output_dir" \
      "$valid_jar_sha" \
      "$valid_jar_bytes" \
      256 \
      268435456 \
      134217728 \
      100 \
      "$validation_repo_root" 2>&1
  )"
  status=$?
  set -e
  [[ "$status" -ne 0 ]] || test_fail "$label unexpectedly passed"
  if [[ "$output" != *"output inside the repository must be below"* ]]; then
    printf '%s\n' "$output" >&2
    test_fail "$label did not enforce repo-first output containment"
  fi
  NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
  echo "[iroha-core-materializer-test] PASS: $label"
}

expect_symlinked_temp_anchor_failure() {
  local label="$1"
  local variable="$2"
  local anchor="$3"
  local output_dir="$4"
  local expected_marker="${5:-output path contains a symbolic-link escape}"
  local output status

  set +e
  output="$(
    unset TMPDIR RUNNER_TEMP
    export "$variable=$anchor"
    iroha_core_materialize_validated_archive \
      "$wrong_pin_archive" \
      "$valid_sha" \
      "$valid_bytes" \
      "$output_dir" \
      "$valid_jar_sha" \
      "$valid_jar_bytes" \
      2>&1
  )"
  status=$?
  set -e
  [[ "$status" -ne 0 ]] || test_fail "$label unexpectedly passed"
  if [[ "$output" != *"$expected_marker"* ]]; then
    printf '%s\n' "$output" >&2
    test_fail "$label did not reject the symlinked environment anchor"
  fi
  NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
  echo "[iroha-core-materializer-test] PASS: $label"
}

valid_fixture="$TMP_DIR/valid"
valid_output="$TMP_DIR/valid-output"
run_fixture "$valid_fixture" valid "$valid_output" >/dev/null
coordinate="$valid_output/org/hyperledger/iroha/sdk/core-jvm/$IROHA_CORE_VERSION"
[[ -d "$coordinate" ]] || test_fail "valid materialization omitted the exact coordinate"
[[ "$(find "$valid_output" -type f | wc -l | tr -d ' ')" == "15" ]] ||
  test_fail "valid materialization did not contain exactly 15 allowlisted files"
if find "$valid_output" -type f \( -name '*.aar' -o -name '*.so' -o -name '*.dll' -o -name '*.dylib' -o -name '*.zip' \) | grep -q .; then
  test_fail "valid materialization emitted a forbidden Android/native/archive artifact"
fi
echo "[iroha-core-materializer-test] PASS: exact core-only materialization"

valid_archive="$valid_fixture/$IROHA_CORE_ARCHIVE_NAME"
read -r custom_sha custom_bytes custom_jar_sha custom_jar_bytes \
  <<<"$(fixture_pins "$valid_archive")"
custom_output="$CUSTOM_TMP_DIR/custom-output"
TMPDIR="$CUSTOM_TMP_DIR" iroha_core_materialize_validated_archive \
  "$valid_archive" \
  "$custom_sha" \
  "$custom_bytes" \
  "$custom_output" \
  "$custom_jar_sha" \
  "$custom_jar_bytes" \
  >/dev/null
[[ -d "$custom_output/org/hyperledger/iroha/sdk/core-jvm/$IROHA_CORE_VERSION" ]] ||
  test_fail "custom TMPDIR materialization omitted the exact coordinate"
echo "[iroha-core-materializer-test] PASS: canonicalized custom TMPDIR anchor"

expect_failure "traversal path" traversal "unsafe path component"
expect_failure "absolute path" absolute "is absolute"
expect_failure "backslash path" backslash "contains a backslash"
expect_failure "drive-qualified path" drive "drive-qualified component"
expect_failure "duplicate entry" duplicate-entry "duplicate entry"
expect_failure "portable-name collision" portable-collision "portable-name collision"
expect_failure "symlink entry" symlink "contains symlink"
expect_failure "special-file entry" special-file "contains special file"
expect_failure "encrypted entry" encrypted-entry "contains encrypted entry"
expect_failure "unsupported compression" unsupported-method "unsupported compression method"
expect_failure "outer CRC corruption" outer-crc "CRC validation failed"
expect_failure "compression bomb" compression-bomb "compression ratio"
expect_failure "entry-count bound" too-many-entries "entry count" 24
expect_failure "per-entry bound" entry-too-large "entry size" 256 268435456 65536
expect_failure "total-size bound" total-too-large "total uncompressed size" 256 100000 134217728

expect_failure "unexpected coordinate artifact" unexpected-coordinate-artifact "coordinate allowlist mismatch"
expect_failure "nested coordinate entry" nested-coordinate-entry "unexpected nested core-jvm coordinate entry"
expect_failure "extra core version" extra-core-version "unexpected core-jvm repository entry"
expect_failure "nested JAR artifact" nested-artifact "contains nested/native artifact"
expect_failure "nested JAR symlink" nested-symlink "core-jvm JAR contains symlink"
expect_failure "nested JAR CRC" nested-crc "core-jvm JAR CRC validation failed"

expect_failure "missing internal manifest" missing-internal-manifest "missing internal SHA256SUMS.txt"
expect_failure "bad internal checksum" bad-internal-checksum "internal SHA256 mismatch"
expect_failure "duplicate internal path" duplicate-internal-path "duplicate internal checksum path"
expect_failure "malformed internal manifest" malformed-internal-manifest "malformed internal SHA256SUMS.txt"
expect_failure "unlisted internal file" unlisted-internal-file "coverage mismatch"
expect_failure "missing coordinate sidecar" missing-sidecar "coordinate allowlist mismatch"
for algorithm in md5 sha1 sha256 sha512; do
  expect_failure "$algorithm sidecar mismatch" "bad-sidecar-$algorithm" "$algorithm sidecar mismatch"
done
expect_failure "raw/Maven JAR mismatch" raw-mismatch "raw and Maven core-jvm JARs differ"
expect_failure "POM coordinate mismatch" pom-mismatch "POM version mismatch"
expect_failure "Gradle module coordinate mismatch" module-mismatch "Gradle module component mismatch"

wrong_pin_fixture="$TMP_DIR/wrong-pin"
make_fixture "$wrong_pin_fixture" valid
wrong_pin_archive="$wrong_pin_fixture/$IROHA_CORE_ARCHIVE_NAME"
read -r valid_sha valid_bytes valid_jar_sha valid_jar_bytes <<<"$(fixture_pins "$wrong_pin_archive")"
expect_command_failure \
  "archive SHA pin" \
  "archive SHA-256 mismatch" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$(printf '0%.0s' {1..64})" "$valid_bytes" "$wrong_pin_fixture/output-sha" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "archive size pin" \
  "archive byte count mismatch" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$((valid_bytes + 1))" "$wrong_pin_fixture/output-size" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "core JAR SHA pin" \
  "core-jvm JAR SHA-256 mismatch" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$wrong_pin_fixture/output-jar-sha" "$(printf '0%.0s' {1..64})" "$valid_jar_bytes"
expect_command_failure \
  "core JAR size pin" \
  "core-jvm JAR byte count mismatch" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$wrong_pin_fixture/output-jar-size" "$valid_jar_sha" "$((valid_jar_bytes + 1))"

expect_command_failure "missing source mode" "one of --download" bash "$MATERIALIZER"
expect_command_failure \
  "mutually exclusive source modes" \
  "source modes are mutually exclusive" \
  bash "$MATERIALIZER" --download --archive "$wrong_pin_archive"
wrong_name="$TMP_DIR/wrong-name.zip"
cp "$wrong_pin_archive" "$wrong_name"
expect_command_failure \
  "exact archive basename" \
  "archive basename must be exactly" \
  bash "$MATERIALIZER" --archive "$wrong_name"

archive_link_dir="$TMP_DIR/archive-link"
mkdir -p "$archive_link_dir"
ln -s "$wrong_pin_archive" "$archive_link_dir/$IROHA_CORE_ARCHIVE_NAME"
expect_command_failure \
  "archive symlink" \
  "archive must not be a symlink" \
  bash "$MATERIALIZER" --archive "$archive_link_dir/$IROHA_CORE_ARCHIVE_NAME"
release_real="$TMP_DIR/release-real"
release_link="$TMP_DIR/release-link"
mkdir -p "$release_real"
ln -s "$release_real" "$release_link"
expect_command_failure \
  "release-directory symlink" \
  "release directory must not be a symlink" \
  bash "$MATERIALIZER" --release-dir "$release_link"

output_link_target="$TMP_DIR/output-link-target"
output_link="$TMP_DIR/output-link"
mkdir -p "$output_link_target"
ln -s "$output_link_target" "$output_link"
expect_command_failure \
  "output-directory symlink" \
  "output directory is a symlink" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$output_link" "$valid_jar_sha" "$valid_jar_bytes"

expect_symlinked_temp_anchor_failure \
  "symlinked TMPDIR anchor" \
  TMPDIR \
  "$output_link" \
  "$output_link/tmpdir-escaped-output"
expect_symlinked_temp_anchor_failure \
  "symlinked RUNNER_TEMP anchor" \
  RUNNER_TEMP \
  "$output_link" \
  "$output_link/runner-temp-escaped-output"

root_anchor_link="$TMP_DIR/root-anchor-link"
ln -s / "$root_anchor_link"
expect_symlinked_temp_anchor_failure \
  "TMPDIR symlink to filesystem root" \
  TMPDIR \
  "$root_anchor_link" \
  "$ROOT_ANCHOR_ESCAPE_DIR/tmpdir-output" \
  "output directory must be below the repository build directory or the system temporary directory"
expect_symlinked_temp_anchor_failure \
  "RUNNER_TEMP symlink to filesystem root" \
  RUNNER_TEMP \
  "$root_anchor_link" \
  "$ROOT_ANCHOR_ESCAPE_DIR/runner-temp-output" \
  "output directory must be below the repository build directory or the system temporary directory"

intermediate_repo="$TMP_DIR/intermediate-symlink-repo"
intermediate_external="$TMP_DIR/intermediate-symlink-external"
mkdir -p "$intermediate_repo" "$intermediate_external"
ln -s "$intermediate_external" "$intermediate_repo/build"
expect_command_failure \
  "repository intermediate output symlink" \
  "output path contains a symbolic-link escape" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" \
  "$valid_sha" \
  "$valid_bytes" \
  "$intermediate_repo/build/iroha-mobile-sdk/maven" \
  "$valid_jar_sha" \
  "$valid_jar_bytes" \
  256 \
  268435456 \
  134217728 \
  100 \
  "$intermediate_repo"

intermediate_temp_parent="$TMP_DIR/intermediate-temp-parent"
mkdir -p "$intermediate_temp_parent"
ln -s "$intermediate_external" "$intermediate_temp_parent/linked"
expect_command_failure \
  "temporary intermediate output symlink" \
  "output path contains a symbolic-link escape" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" \
  "$valid_sha" \
  "$valid_bytes" \
  "$intermediate_temp_parent/linked/maven" \
  "$valid_jar_sha" \
  "$valid_jar_bytes"

output_file="$TMP_DIR/output-file"
printf 'preserve output file\n' > "$output_file"
expect_command_failure \
  "output non-directory preservation" \
  "output path is not a directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$output_file" "$valid_jar_sha" "$valid_jar_bytes"
[[ "$(cat "$output_file")" == "preserve output file" ]] || test_fail "non-directory output was modified"

expect_command_failure \
  "empty output argument" \
  "--output-dir requires a non-empty value" \
  bash "$MATERIALIZER" --archive "$wrong_pin_archive" --output-dir ""
expect_command_failure \
  "dot output directory" \
  "refusing unsafe output directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "." "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "repository-root output directory" \
  "refusing unsafe output directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$TEST_ROOT_DIR" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "normalized repository-root output directory" \
  "refusing unsafe output directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$TEST_ROOT_DIR/build/nested/../.." "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "repository-build-root output directory" \
  "refusing unsafe output directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$TEST_ROOT_DIR/build" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "filesystem-root output directory" \
  "refusing unsafe output directory" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "/" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "repository-ancestor output directory" \
  "must not contain the repository" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$(dirname "$TEST_ROOT_DIR")" "$valid_jar_sha" "$valid_jar_bytes"
expect_command_failure \
  "archive-parent output directory" \
  "must not contain the source archive" \
  iroha_core_materialize_validated_archive \
  "$wrong_pin_archive" "$valid_sha" "$valid_bytes" "$wrong_pin_fixture" "$valid_jar_sha" "$valid_jar_bytes"

expect_tmpdir_overlap_failure \
  "TMPDIR-overlap scripts rejection" \
  "$TEST_ROOT_DIR/scripts/iroha-materialized" \
  "$TEST_ROOT_DIR" \
  "$TEST_ROOT_DIR"
expect_tmpdir_overlap_failure \
  "TMPDIR-overlap git-directory rejection" \
  "$TEST_ROOT_DIR/.git/iroha-materialized" \
  "$TEST_ROOT_DIR" \
  "$TEST_ROOT_DIR"
expect_tmpdir_overlap_failure \
  "TMPDIR-overlap arbitrary repo-child rejection" \
  "$TEST_ROOT_DIR/docs/iroha-materialized" \
  "$TEST_ROOT_DIR" \
  "$TEST_ROOT_DIR"
simulated_temp_repo="$TMP_DIR/simulated-repo-under-temp"
mkdir -p "$simulated_temp_repo"
expect_tmpdir_overlap_failure \
  "repository-under-temp rejection" \
  "$simulated_temp_repo/scripts/iroha-materialized" \
  "$simulated_temp_repo" \
  "$TMP_DIR"
if [[ "$(uname -s)" == "Darwin" ]]; then
  case_alias_root="${TEST_ROOT_DIR/#\/Users\//\/users\/}"
  expect_tmpdir_overlap_failure \
    "macOS case-alias repo rejection" \
    "$case_alias_root/scripts/iroha-materialized" \
    "$TEST_ROOT_DIR" \
    "$TEST_ROOT_DIR"

  physical_alias_repo="$TMP_DIR/physical-alias-repo"
  mkdir -p "$physical_alias_repo"
  case "$physical_alias_repo" in
    /var/*) physical_alias_output="/private$physical_alias_repo/scripts/iroha-materialized" ;;
    /private/var/*) physical_alias_output="${physical_alias_repo#/private}/scripts/iroha-materialized" ;;
    *) physical_alias_output="" ;;
  esac
  if [[ -n "$physical_alias_output" ]]; then
    expect_tmpdir_overlap_failure \
      "macOS physical temp-alias repo rejection" \
      "$physical_alias_output" \
      "$physical_alias_repo" \
      "$TMP_DIR"
  else
    test_fail "macOS test temp root lacks the expected /var physical alias"
  fi
fi

run_fake_download_case "curl contract and successful cleanup" success success
run_fake_download_case "curl failure cleanup" curl-failure failure
run_fake_download_case "curl oversize cleanup" oversize failure
run_fake_download_case "post-download validation failure cleanup" validation-failure failure

race_dir="$TMP_DIR/archive-swap-race"
race_original_dir="$race_dir/original"
race_replacement_dir="$race_dir/replacement"
make_fixture "$race_original_dir" valid
make_fixture "$race_replacement_dir" pom-mismatch
race_archive="$race_original_dir/$IROHA_CORE_ARCHIVE_NAME"
race_replacement="$race_replacement_dir/$IROHA_CORE_ARCHIVE_NAME"
read -r race_sha race_bytes race_jar_sha race_jar_bytes <<<"$(fixture_pins "$race_archive")"
real_python3="$(command -v python3)"
IROHA_ARCHIVE_SWAP_ARMED=1
python3() {
  if [[ "${IROHA_ARCHIVE_SWAP_ARMED:-0}" == "1" ]]; then
    IROHA_ARCHIVE_SWAP_ARMED=0
    rm -f "$race_archive"
    ln -s "$race_replacement" "$race_archive"
  fi
  "$real_python3" "$@"
}
set +e
race_error="$(
  iroha_core_materialize_validated_archive \
    "$race_archive" "$race_sha" "$race_bytes" "$race_dir/output" "$race_jar_sha" "$race_jar_bytes" 2>&1
)"
race_status=$?
set -e
unset -f python3
[[ "$race_status" -ne 0 ]] || test_fail "archive path-swap race unexpectedly passed"
[[ "$race_error" == *"securely open archive without following symlinks"* ]] || {
  printf '%s\n' "$race_error" >&2
  test_fail "archive path-swap race did not fail at the secure-open boundary"
}
[[ ! -e "$race_dir/output" ]] || test_fail "archive path-swap race published output"
NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
echo "[iroha-core-materializer-test] PASS: archive path-swap race guard"

atomic_output="$TMP_DIR/atomic-output"
run_fixture "$TMP_DIR/atomic-valid" valid "$atomic_output" >/dev/null
printf 'preserve me\n' > "$atomic_output/preexisting-sentinel"
atomic_bad="$TMP_DIR/atomic-bad"
set +e
atomic_error="$(run_fixture "$atomic_bad" bad-sidecar-sha256 "$atomic_output" 2>&1)"
atomic_status=$?
set -e
[[ "$atomic_status" -ne 0 ]] || test_fail "invalid atomic replacement unexpectedly passed"
NEGATIVE_SCENARIOS=$((NEGATIVE_SCENARIOS + 1))
[[ "$atomic_error" == *"sha256 sidecar mismatch"* ]] || test_fail "invalid atomic replacement reported the wrong error"
[[ "$(cat "$atomic_output/preexisting-sentinel")" == "preserve me" ]] ||
  test_fail "failed validation modified the previous repository"
run_fixture "$TMP_DIR/atomic-valid-retry" valid "$atomic_output" >/dev/null
[[ ! -e "$atomic_output/preexisting-sentinel" ]] || test_fail "successful replacement retained stale output"
[[ "$(find "$atomic_output" -type f | wc -l | tr -d ' ')" == "15" ]] ||
  test_fail "successful replacement did not publish the exact file set"
echo "[iroha-core-materializer-test] PASS: transactional replacement and rollback"

EXPECTED_NEGATIVE_SCENARIOS=68
if [[ "$(uname -s)" == "Darwin" ]]; then
  EXPECTED_NEGATIVE_SCENARIOS=70
fi
[[ "$NEGATIVE_SCENARIOS" == "$EXPECTED_NEGATIVE_SCENARIOS" ]] ||
  test_fail \
    "expected $EXPECTED_NEGATIVE_SCENARIOS negative/adversarial scenarios; got $NEGATIVE_SCENARIOS"
echo "[iroha-core-materializer-test] all $NEGATIVE_SCENARIOS negative/adversarial scenarios passed"
