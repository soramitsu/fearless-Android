#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-aab-identity.sh"
SIGNATURE_TEST="$ROOT_DIR/scripts/test-android-aab-jar-signature.sh"
PRODUCTION_AAB_PACKAGE="jp.co.soramitsu.fearless"
component_manifest_modes=(
  empty-application
  wrong-application-name
  application-disabled
  application-permission
  missing-startup
  duplicate-startup
  renamed-startup
  missing-root
  missing-wallet-root
  unexpected-activity
  unexpected-activity-alias
  startup-exported-false
  root-exported-true
  scanner-exported-true
  startup-disabled
  root-disabled
  duplicate-launcher-filter
  second-launcher-filter
  launcher-on-root
  launcher-missing-main
  launcher-missing-category
  launcher-extra-action
  launcher-extra-category
  launcher-extra-data
  missing-ton-filter
  mutated-ton-host
  ton-autoverify-false
  missing-service
  unexpected-service
  service-exported
  revocation-permission-missing
  revocation-permission-mutated
  missing-receiver
  unexpected-receiver
  firebase-receiver-permission-missing
  firebase-receiver-exported-false
  profile-permission-missing
  receiver-intent-mutated
  missing-provider
  unexpected-provider
  file-provider-exported-true
  authority-mutated
  authority-duplicated
  authority-repeated
  authority-placeholder
  grant-uri-disabled
  provider-read-permission
  provider-write-permission
  provider-path-permission
  duplicate-component
  malformed-component-name
  duplicate-intent-action
  empty-intent-data
  unexpected-intent-attribute
  unexpected-intent-child
)
test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-aab-identity.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT

fail() {
  echo "[android-aab-identity-test][error] $*" >&2
  exit 1
}

for name in \
  AAB_IDENTITY_FIXTURE \
  BUNDLETOOL_JAR \
  EXPECTED_AAB_PACKAGE \
  EXPECTED_AAB_VERSION_NAME \
  EXPECTED_AAB_VERSION_CODE \
  EXPECTED_AAB_SOURCE_COMMIT; do
  [[ -n "${!name:-}" ]] || fail "$name is required."
done

requested_r8_policy="${AAB_IDENTITY_R8_POLICY:-required}"
case "$requested_r8_policy" in
  required|absent) ;;
  *) fail "AAB_IDENTITY_R8_POLICY must be exactly required or absent." ;;
esac
active_r8_policy="$requested_r8_policy"

for command_name in cp grep java mktemp python3 sed unzip; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

real_java="$(command -v java)"

[[ ! -L "$AAB_IDENTITY_FIXTURE" &&
  -f "$AAB_IDENTITY_FIXTURE" &&
  -s "$AAB_IDENTITY_FIXTURE" ]] ||
  fail "AAB_IDENTITY_FIXTURE must be a non-empty regular, non-symlink AAB."
[[ ! -L "$BUNDLETOOL_JAR" && -f "$BUNDLETOOL_JAR" && -s "$BUNDLETOOL_JAR" ]] ||
  fail "BUNDLETOOL_JAR must be a non-empty regular, non-symlink file."
[[ ! -L "$SIGNATURE_TEST" && -x "$SIGNATURE_TEST" ]] ||
  fail "the AAB JAR-signature adversarial suite is missing or unsafe"

"$SIGNATURE_TEST"

original_identity_fixture="$AAB_IDENTITY_FIXTURE"
fixture_with_r8="$tmp_dir/fixture-with-r8.aab"
fixture_without_r8="$tmp_dir/fixture-without-r8.aab"
python3 -I - \
  "$original_identity_fixture" \
  "$fixture_with_r8" \
  "$fixture_without_r8" <<'PY'
# R8_FIXTURE_SYNTHESIZER_SOURCE_BEGIN
import copy
import json
import os
import stat
import sys
import zipfile

source, with_r8, without_r8 = sys.argv[1:]
r8_path = "BUNDLE-METADATA/com.android.tools/r8.json"
maximum_aab_bytes = 262_144_000
maximum_entry_count = 100_000
maximum_entry_bytes = 134_217_728
maximum_total_uncompressed_bytes = 536_870_912
copy_chunk_bytes = 1024 * 1024
source_flags = (
    os.O_RDONLY
    | getattr(os, "O_NOFOLLOW", 0)
    | getattr(os, "O_CLOEXEC", 0)
)


def source_identity(metadata):
    return tuple(
        getattr(metadata, field)
        for field in (
            "st_dev",
            "st_ino",
            "st_size",
            "st_mtime_ns",
            "st_ctime_ns",
            "st_nlink",
        )
    )


def write_fixture(archive, entries, destination, include_r8, has_r8):
    try:
        output_archive = zipfile.ZipFile(
            destination,
            "x",
            allowZip64=True,
        )
    except FileExistsError as error:
        raise ValueError(
            f"synthesis destination already exists: {destination}"
        ) from error
    with output_archive as output:
        for entry in entries:
            if entry.filename == r8_path and not include_r8:
                continue
            destination_entry = copy.copy(entry)
            with archive.open(entry, "r") as reader:
                with output.open(
                    destination_entry,
                    "w",
                    force_zip64=True,
                ) as writer:
                    copied = 0
                    while True:
                        chunk = reader.read(copy_chunk_bytes)
                        if not chunk:
                            break
                        copied += len(chunk)
                        if copied > entry.file_size:
                            raise ValueError(
                                f"ZIP entry expanded past its declared size: "
                                f"{entry.filename!r}"
                            )
                        writer.write(chunk)
                    if copied != entry.file_size:
                        raise ValueError(
                            f"ZIP entry size mismatch: {entry.filename!r}"
                        )
        if include_r8 and not has_r8:
            output.writestr(
                r8_path,
                json.dumps(
                    {"version": "8.10.24"},
                    separators=(",", ":"),
                ).encode("utf-8"),
            )
    output_size = os.path.getsize(destination)
    if output_size <= 0 or output_size > maximum_aab_bytes:
        raise ValueError(
            f"synthesized fixture has unsafe size: {output_size} bytes"
        )


try:
    source_fd = os.open(source, source_flags)
except OSError as error:
    raise ValueError(
        "source fixture could not be opened without following links"
    ) from error
try:
    before = os.fstat(source_fd)
    if (
        not stat.S_ISREG(before.st_mode)
        or before.st_size <= 0
        or before.st_size > maximum_aab_bytes
    ):
        raise ValueError("source fixture has an unsafe size or file type")
    with os.fdopen(source_fd, "rb", closefd=False) as source_file:
        with zipfile.ZipFile(source_file, "r") as archive:
            entries = archive.infolist()
            if not entries or len(entries) > maximum_entry_count:
                raise ValueError(
                    f"source fixture has an unsafe ZIP entry count: "
                    f"{len(entries)}"
                )
            total_uncompressed_bytes = 0
            for entry in entries:
                if (
                    entry.file_size < 0
                    or entry.file_size > maximum_entry_bytes
                    or entry.compress_size < 0
                    or entry.compress_size > before.st_size
                    or entry.flag_bits & 0x1
                    or entry.compress_type
                    not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}
                ):
                    raise ValueError(
                        f"source fixture contains an unsafe ZIP entry: "
                        f"{entry.filename!r}"
                    )
                total_uncompressed_bytes += entry.file_size
                if total_uncompressed_bytes > maximum_total_uncompressed_bytes:
                    raise ValueError(
                        "source fixture exceeds the aggregate uncompressed "
                        "size limit"
                    )
            r8_entries = [
                entry for entry in entries if entry.filename == r8_path
            ]
            if len(r8_entries) > 1:
                raise ValueError(
                    "source fixture contains duplicate canonical R8 metadata"
                )
            has_r8 = bool(r8_entries)
            write_fixture(
                archive,
                entries,
                without_r8,
                include_r8=False,
                has_r8=has_r8,
            )
            write_fixture(
                archive,
                entries,
                with_r8,
                include_r8=True,
                has_r8=has_r8,
            )
    after = os.fstat(source_fd)
    if source_identity(before) != source_identity(after):
        raise ValueError("source fixture changed while it was synthesized")
finally:
    os.close(source_fd)
# R8_FIXTURE_SYNTHESIZER_SOURCE_END
PY

r8_synthesis_boundary_dir="$tmp_dir/r8-synthesis-boundaries"
r8_synthesis_input_dir="$r8_synthesis_boundary_dir/input"
mkdir -p "$r8_synthesis_input_dir"
r8_synthesizer_source="$r8_synthesis_boundary_dir/synthesizer.py"
sed -n \
  '/^# R8_FIXTURE_SYNTHESIZER_SOURCE_BEGIN$/,/^# R8_FIXTURE_SYNTHESIZER_SOURCE_END$/p' \
  "$0" >"$r8_synthesizer_source"
[[ "$(grep -Fxc '# R8_FIXTURE_SYNTHESIZER_SOURCE_BEGIN' \
  "$r8_synthesizer_source")" == "1" &&
  "$(grep -Fxc '# R8_FIXTURE_SYNTHESIZER_SOURCE_END' \
    "$r8_synthesizer_source")" == "1" ]] ||
  fail "could not extract the exact R8 fixture synthesizer for boundary tests"

python3 -I - \
  "$r8_synthesis_input_dir" \
  "$r8_synthesizer_source" <<'PY'
import ast
import copy
import json
import os
import struct
import sys
import warnings
import zipfile

output_dir, synthesizer_source = sys.argv[1:]
reviewed_bound_names = {
    "maximum_aab_bytes",
    "maximum_entry_count",
    "maximum_entry_bytes",
    "maximum_total_uncompressed_bytes",
}
with open(synthesizer_source, encoding="utf-8") as source_file:
    synthesizer_tree = ast.parse(source_file.read(), synthesizer_source)
bound_values = {}
for node in ast.walk(synthesizer_tree):
    if (
        isinstance(node, ast.Assign)
        and len(node.targets) == 1
        and isinstance(node.targets[0], ast.Name)
        and node.targets[0].id in reviewed_bound_names
        and isinstance(node.value, ast.Constant)
        and type(node.value.value) is int
    ):
        name = node.targets[0].id
        if name in bound_values:
            raise SystemExit(f"duplicate synthesizer bound assignment: {name}")
        bound_values[name] = node.value.value
if set(bound_values) != reviewed_bound_names:
    raise SystemExit(
        "synthesizer bound inventory differs from the reviewed source"
    )
if any(value <= 0 for value in bound_values.values()):
    raise SystemExit("synthesizer bounds must all be positive")

maximum_aab_bytes = bound_values["maximum_aab_bytes"]
maximum_entry_count = bound_values["maximum_entry_count"]
maximum_entry_bytes = bound_values["maximum_entry_bytes"]
maximum_total_uncompressed_bytes = bound_values[
    "maximum_total_uncompressed_bytes"
]
r8_path = "BUNDLE-METADATA/com.android.tools/r8.json"


def archive_path(name):
    return os.path.join(output_dir, name)


def write_empty_entries(path, names):
    with zipfile.ZipFile(path, "x", allowZip64=True) as archive:
        for name in names:
            archive.writestr(name, b"")


def central_offsets(data):
    offsets = []
    cursor = 0
    marker = b"PK\x01\x02"
    while True:
        cursor = data.find(marker, cursor)
        if cursor < 0:
            return offsets
        offsets.append(cursor)
        cursor += len(marker)


def patch_central(source, destination, field_offset, field_format, values):
    with open(source, "rb") as fixture:
        data = bytearray(fixture.read())
    offsets = central_offsets(data)
    if len(offsets) != len(values):
        raise SystemExit(
            f"central-directory inventory mismatch: {len(offsets)} != "
            f"{len(values)}"
        )
    for offset, value in zip(offsets, values):
        struct.pack_into(field_format, data, offset + field_offset, value)
    with open(destination, "xb") as fixture:
        fixture.write(data)


with zipfile.ZipFile(
    archive_path("valid-small.aab"),
    "x",
    zipfile.ZIP_DEFLATED,
) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"manifest\n")

with open(archive_path("zero-size.aab"), "xb"):
    pass

with open(archive_path("source-too-large.aab"), "xb") as fixture:
    fixture.truncate(maximum_aab_bytes + 1)

os.mkdir(archive_path("source-directory.aab"))
os.symlink(
    archive_path("valid-small.aab"),
    archive_path("source-symlink.aab"),
)

with zipfile.ZipFile(archive_path("empty-archive.aab"), "x"):
    pass

with zipfile.ZipFile(
    archive_path("too-many-entries.aab"),
    "x",
    compression=zipfile.ZIP_STORED,
    allowZip64=True,
) as archive:
    for index in range(maximum_entry_count + 1):
        archive.writestr(f"e/{index:06d}", b"")

write_empty_entries(
    archive_path("entry-template.aab"),
    ["oversized-entry.bin"],
)
patch_central(
    archive_path("entry-template.aab"),
    archive_path("oversized-entry.aab"),
    24,
    "<I",
    [maximum_entry_bytes + 1],
)

aggregate_names = [f"aggregate-{index}.bin" for index in range(5)]
aggregate_total_bytes = maximum_total_uncompressed_bytes + 1
aggregate_base_bytes, aggregate_remainder = divmod(
    aggregate_total_bytes,
    len(aggregate_names),
)
aggregate_entry_sizes = [
    aggregate_base_bytes + (1 if index < aggregate_remainder else 0)
    for index in range(len(aggregate_names))
]
if (
    sum(aggregate_entry_sizes) != aggregate_total_bytes
    or max(aggregate_entry_sizes) > maximum_entry_bytes
):
    raise SystemExit(
        "aggregate test cannot isolate the aggregate bound from the per-entry "
        "bound"
    )
write_empty_entries(
    archive_path("aggregate-template.aab"),
    aggregate_names,
)
patch_central(
    archive_path("aggregate-template.aab"),
    archive_path("aggregate-over-limit.aab"),
    24,
    "<I",
    aggregate_entry_sizes,
)

write_empty_entries(
    archive_path("metadata-template.aab"),
    ["metadata.bin"],
)
metadata_template = archive_path("metadata-template.aab")
patch_central(
    metadata_template,
    archive_path("compressed-size-over-limit.aab"),
    20,
    "<I",
    [0xFFFFFFFF],
)
patch_central(
    metadata_template,
    archive_path("encrypted-entry.aab"),
    8,
    "<H",
    [0x1],
)
patch_central(
    metadata_template,
    archive_path("unsupported-compression.aab"),
    10,
    "<H",
    [zipfile.ZIP_BZIP2],
)

with warnings.catch_warnings():
    warnings.simplefilter("ignore", UserWarning)
    with zipfile.ZipFile(
        archive_path("duplicate-r8.aab"),
        "x",
        zipfile.ZIP_DEFLATED,
    ) as archive:
        archive.writestr(r8_path, b'{"version":"8.10.24"}')
        archive.writestr(r8_path, b'{"version":"8.10.24"}')

output_payload_names = [f"payload-{index}.bin" for index in range(2)]
output_overhead_source = archive_path("output-overhead-source.aab")
output_overhead_destination = archive_path("output-overhead-with-r8.aab")
with zipfile.ZipFile(
    output_overhead_source,
    "x",
    compression=zipfile.ZIP_STORED,
    allowZip64=True,
) as archive:
    for name in output_payload_names:
        with archive.open(name, "w", force_zip64=True):
            pass
with zipfile.ZipFile(output_overhead_source, "r") as source_archive:
    with zipfile.ZipFile(
        output_overhead_destination,
        "x",
        allowZip64=True,
    ) as output_archive:
        for entry in source_archive.infolist():
            with source_archive.open(entry, "r") as reader:
                with output_archive.open(
                    copy.copy(entry),
                    "w",
                    force_zip64=True,
                ) as writer:
                    writer.write(reader.read())
        output_archive.writestr(
            r8_path,
            json.dumps(
                {"version": "8.10.24"},
                separators=(",", ":"),
            ).encode("utf-8"),
        )
output_overhead_bytes = os.path.getsize(output_overhead_destination)
os.unlink(output_overhead_destination)
os.unlink(output_overhead_source)
output_payload_total_bytes = maximum_aab_bytes + 1 - output_overhead_bytes
output_payload_base_bytes, output_payload_remainder = divmod(
    output_payload_total_bytes,
    len(output_payload_names),
)
output_payload_sizes = [
    output_payload_base_bytes + (1 if index < output_payload_remainder else 0)
    for index in range(len(output_payload_names))
]
if (
    output_payload_total_bytes <= 0
    or sum(output_payload_sizes) != output_payload_total_bytes
    or max(output_payload_sizes) > maximum_entry_bytes
):
    raise SystemExit(
        "output-size test cannot isolate the output bound from the per-entry "
        "bound"
    )
with zipfile.ZipFile(
    archive_path("output-over-limit.aab"),
    "x",
    compression=zipfile.ZIP_STORED,
    allowZip64=True,
) as archive:
    zero_chunk = b"\0" * (8 * 1024 * 1024)
    for name, entry_size in zip(output_payload_names, output_payload_sizes):
        with archive.open(
            name,
            "w",
            force_zip64=True,
        ) as writer:
            remaining = entry_size
            while remaining:
                chunk = zero_chunk[: min(remaining, len(zero_chunk))]
                writer.write(chunk)
                remaining -= len(chunk)

output_over_limit_size = os.path.getsize(
    archive_path("output-over-limit.aab")
)
if not 0 < output_over_limit_size <= maximum_aab_bytes:
    raise SystemExit(
        f"output-limit source fixture has unsafe size: "
        f"{output_over_limit_size}"
    )
PY

r8_synthesis_positive_count=0
r8_synthesis_negative_count=0

run_r8_synthesizer() {
  local source="$1"
  local output_dir="$2"
  mkdir -p "$output_dir"
  python3 -I \
    "$r8_synthesizer_source" \
    "$source" \
    "$output_dir/with-r8.aab" \
    "$output_dir/without-r8.aab"
}

positive_synthesis_dir="$r8_synthesis_boundary_dir/positive"
run_r8_synthesizer \
  "$r8_synthesis_input_dir/valid-small.aab" \
  "$positive_synthesis_dir"
python3 -I - \
  "$positive_synthesis_dir/with-r8.aab" \
  "$positive_synthesis_dir/without-r8.aab" <<'PY'
import sys
import zipfile

with_r8, without_r8 = sys.argv[1:]
r8_path = "BUNDLE-METADATA/com.android.tools/r8.json"
with zipfile.ZipFile(with_r8) as archive:
    if archive.namelist().count(r8_path) != 1:
        raise SystemExit("positive synthesis did not add exactly one R8 entry")
with zipfile.ZipFile(without_r8) as archive:
    if archive.namelist().count(r8_path) != 0:
        raise SystemExit("positive synthesis did not remove the R8 entry")
PY
r8_synthesis_positive_count=$((r8_synthesis_positive_count + 1))

expect_r8_synthesis_failure() {
  local label="$1"
  local expected_diagnostic="$2"
  local source="$3"
  local mode="${4:-ordinary}"
  local case_dir="$r8_synthesis_boundary_dir/failure-$r8_synthesis_negative_count"
  local output="$case_dir/output.log"
  mkdir -p "$case_dir"
  case "$mode" in
    ordinary|exact-output-over-limit) ;;
    preexisting-with-r8)
      : >"$case_dir/with-r8.aab"
      ;;
    preexisting-without-r8)
      : >"$case_dir/without-r8.aab"
      ;;
    *)
      fail "$label has an unsupported test mode: $mode"
      ;;
  esac
  if python3 -I \
    "$r8_synthesizer_source" \
    "$source" \
    "$case_dir/with-r8.aab" \
    "$case_dir/without-r8.aab" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$expected_diagnostic" "$output" || {
    sed -n '1,120p' "$output" >&2
    fail "$label did not emit the expected diagnostic: $expected_diagnostic"
  }
  if [[ "$mode" == "exact-output-over-limit" ]]; then
    python3 -I - \
      "$r8_synthesizer_source" \
      "$case_dir/with-r8.aab" \
      "$case_dir/without-r8.aab" \
      "$source" <<'PY'
import ast
import os
import sys

synthesizer_source, with_r8, without_r8, source = sys.argv[1:]
with open(synthesizer_source, encoding="utf-8") as source_file:
    synthesizer_tree = ast.parse(source_file.read(), synthesizer_source)
maximum_aab_values = [
    node.value.value
    for node in ast.walk(synthesizer_tree)
    if (
        isinstance(node, ast.Assign)
        and len(node.targets) == 1
        and isinstance(node.targets[0], ast.Name)
        and node.targets[0].id == "maximum_aab_bytes"
        and isinstance(node.value, ast.Constant)
        and type(node.value.value) is int
    )
]
if len(maximum_aab_values) != 1:
    raise SystemExit("could not recover the unique synthesized AAB byte bound")
actual_size = os.path.getsize(with_r8)
expected_size = maximum_aab_values[0] + 1
if actual_size != expected_size:
    raise SystemExit(
        f"output-limit fixture missed the first rejected byte: "
        f"{actual_size} != {expected_size}"
    )
for path in (with_r8, without_r8, source):
    if os.path.lexists(path):
        os.unlink(path)
PY
  fi
  r8_synthesis_negative_count=$((r8_synthesis_negative_count + 1))
}

expect_r8_synthesis_failure \
  "zero-byte source fixture" \
  "source fixture has an unsafe size or file type" \
  "$r8_synthesis_input_dir/zero-size.aab"
expect_r8_synthesis_failure \
  "source fixture over the exact AAB byte limit" \
  "source fixture has an unsafe size or file type" \
  "$r8_synthesis_input_dir/source-too-large.aab"
expect_r8_synthesis_failure \
  "non-regular source fixture" \
  "source fixture has an unsafe size or file type" \
  "$r8_synthesis_input_dir/source-directory.aab"
expect_r8_synthesis_failure \
  "symlink source fixture" \
  "source fixture could not be opened without following links" \
  "$r8_synthesis_input_dir/source-symlink.aab"
expect_r8_synthesis_failure \
  "empty ZIP source fixture" \
  "source fixture has an unsafe ZIP entry count: 0" \
  "$r8_synthesis_input_dir/empty-archive.aab"
expect_r8_synthesis_failure \
  "ZIP source fixture over the exact entry-count limit" \
  "source fixture has an unsafe ZIP entry count: 100001" \
  "$r8_synthesis_input_dir/too-many-entries.aab"
expect_r8_synthesis_failure \
  "ZIP entry over the exact uncompressed byte limit" \
  "source fixture contains an unsafe ZIP entry: 'oversized-entry.bin'" \
  "$r8_synthesis_input_dir/oversized-entry.aab"
expect_r8_synthesis_failure \
  "ZIP source fixture over the exact aggregate uncompressed byte limit" \
  "source fixture exceeds the aggregate uncompressed size limit" \
  "$r8_synthesis_input_dir/aggregate-over-limit.aab"
expect_r8_synthesis_failure \
  "ZIP entry with declared compressed size larger than its source" \
  "source fixture contains an unsafe ZIP entry: 'metadata.bin'" \
  "$r8_synthesis_input_dir/compressed-size-over-limit.aab"
expect_r8_synthesis_failure \
  "encrypted ZIP entry" \
  "source fixture contains an unsafe ZIP entry: 'metadata.bin'" \
  "$r8_synthesis_input_dir/encrypted-entry.aab"
expect_r8_synthesis_failure \
  "unsupported ZIP compression" \
  "source fixture contains an unsafe ZIP entry: 'metadata.bin'" \
  "$r8_synthesis_input_dir/unsupported-compression.aab"
expect_r8_synthesis_failure \
  "duplicate canonical R8 metadata" \
  "source fixture contains duplicate canonical R8 metadata" \
  "$r8_synthesis_input_dir/duplicate-r8.aab"
expect_r8_synthesis_failure \
  "synthesized fixture over the exact output byte limit" \
  "synthesized fixture has unsafe size" \
  "$r8_synthesis_input_dir/output-over-limit.aab" \
  exact-output-over-limit
expect_r8_synthesis_failure \
  "preexisting exclusive without-R8 synthesis destination" \
  "synthesis destination already exists" \
  "$r8_synthesis_input_dir/valid-small.aab" \
  preexisting-without-r8
expect_r8_synthesis_failure \
  "preexisting exclusive with-R8 synthesis destination" \
  "synthesis destination already exists" \
  "$r8_synthesis_input_dir/valid-small.aab" \
  preexisting-with-r8

[[ "$r8_synthesis_positive_count" == "1" ]] ||
  fail \
    "expected 1 R8-synthesis boundary positive case; got " \
    "$r8_synthesis_positive_count"
[[ "$r8_synthesis_negative_count" == "15" ]] ||
  fail \
    "expected 15 R8-synthesis boundary negative/adversarial cases; got " \
    "$r8_synthesis_negative_count"
printf '%s\n' \
  "[android-aab-r8-synthesis-boundary-test] $r8_synthesis_positive_count positive + $r8_synthesis_negative_count negative/adversarial cases passed"

run_verifier() {
  local artifact="$1"
  local expected_package="${2:-$EXPECTED_AAB_PACKAGE}"
  local expected_version_name="${3:-$EXPECTED_AAB_VERSION_NAME}"
  local expected_version_code="${4:-$EXPECTED_AAB_VERSION_CODE}"
  local expected_source_commit="${5:-$EXPECTED_AAB_SOURCE_COMMIT}"
  local expected_r8_policy="$active_r8_policy"
  if [[ "$#" -ge 6 ]]; then
    expected_r8_policy="$6"
  fi

  BUNDLETOOL_JAR="$BUNDLETOOL_JAR" "$VERIFY" \
    "$artifact" \
    "$expected_package" \
    "$expected_version_name" \
    "$expected_version_code" \
    "$expected_source_commit" \
    "$expected_r8_policy"
}

fake_java_dir="$tmp_dir/fake-java-bin"
mkdir -p "$fake_java_dir"
cat > "$fake_java_dir/java" <<'FAKEJAVA'
#!/usr/bin/env bash
set -euo pipefail

if [[ -z "${FAKE_BUNDLETOOL_MANIFEST_FILE:-}" ]]; then
  exec "$REAL_JAVA" "$@"
fi

xpath=""
while [[ "$#" -gt 0 ]]; do
  if [[ "$1" == "--xpath" ]]; then
    [[ "$#" -ge 2 ]] || exit 2
    xpath="$2"
    break
  fi
  shift
done

case "$xpath" in
  "")
    cat "$FAKE_BUNDLETOOL_MANIFEST_FILE"
    ;;
  "/manifest/@package")
    printf '%s' "$FAKE_AAB_PACKAGE"
    ;;
  "/manifest/@android:versionCode")
    printf '%s' "$FAKE_AAB_VERSION_CODE"
    ;;
  "/manifest/@android:versionName")
    printf '%s' "$FAKE_AAB_VERSION_NAME"
    ;;
  "/manifest/application/meta-data[@android:name='jp.co.soramitsu.fearless.RELEASE_COMMIT']/@android:value")
    printf '%s' "$FAKE_AAB_SOURCE_COMMIT"
    ;;
  *)
    echo "unsupported fake bundletool xpath: $xpath" >&2
    exit 2
    ;;
esac
FAKEJAVA
chmod 700 "$fake_java_dir/java"

write_manifest_fixture() {
  local mode="$1"
  local output="$2"
  python3 -I - \
    "$output" \
    "$mode" \
    "$PRODUCTION_AAB_PACKAGE" \
    "${component_manifest_modes[@]}" <<'PY'
import copy
import sys
import xml.etree.ElementTree as ElementTree

output, mode, package_name, *component_mode_names = sys.argv[1:]
android_namespace = "http://schemas.android.com/apk/res/android"
ElementTree.register_namespace("android", android_namespace)
attribute_name = f"{{{android_namespace}}}name"
attribute_max_sdk = f"{{{android_namespace}}}maxSdkVersion"
attribute_min_sdk = f"{{{android_namespace}}}minSdkVersion"
attribute_target_sdk = f"{{{android_namespace}}}targetSdkVersion"
attribute_allow_backup = f"{{{android_namespace}}}allowBackup"
attribute_debuggable = f"{{{android_namespace}}}debuggable"
attribute_test_only = f"{{{android_namespace}}}testOnly"
attribute_cleartext = f"{{{android_namespace}}}usesCleartextTraffic"
attribute_network_security = f"{{{android_namespace}}}networkSecurityConfig"
attribute_enabled = f"{{{android_namespace}}}enabled"
attribute_exported = f"{{{android_namespace}}}exported"
attribute_permission = f"{{{android_namespace}}}permission"
attribute_target_activity = f"{{{android_namespace}}}targetActivity"
attribute_authorities = f"{{{android_namespace}}}authorities"
attribute_read_permission = f"{{{android_namespace}}}readPermission"
attribute_write_permission = f"{{{android_namespace}}}writePermission"
attribute_grant_uri_permissions = (
    f"{{{android_namespace}}}grantUriPermissions"
)
attribute_auto_verify = f"{{{android_namespace}}}autoVerify"
attribute_priority = f"{{{android_namespace}}}priority"
attribute_order = f"{{{android_namespace}}}order"
attribute_scheme = f"{{{android_namespace}}}scheme"
attribute_host = f"{{{android_namespace}}}host"
attribute_path_prefix = f"{{{android_namespace}}}pathPrefix"
attribute_mime_type = f"{{{android_namespace}}}mimeType"
startup_activity = "jp.co.soramitsu.app.root.presentation.WalletStartupActivity"
root_activity = "jp.co.soramitsu.app.root.presentation.RootActivity"
wallet_root_activity = (
    "jp.co.soramitsu.app.root.presentation.WalletRootActivity"
)
component_modes = set(component_mode_names)
if len(component_modes) != len(component_mode_names):
    raise SystemExit("component manifest mode inventory contains duplicates")
permissions = [
    ("uses-permission", "android.permission.ACCESS_NETWORK_STATE", None),
    ("uses-permission", "android.permission.CAMERA", None),
    ("uses-permission", "android.permission.INTERNET", None),
    ("uses-permission", "android.permission.POST_NOTIFICATIONS", None),
    ("uses-permission", "android.permission.USE_BIOMETRIC", None),
    ("uses-permission", "android.permission.USE_FINGERPRINT", None),
    ("uses-permission", "android.permission.VIBRATE", None),
    ("uses-permission", "android.permission.WAKE_LOCK", None),
    ("uses-permission", "com.google.android.c2dm.permission.RECEIVE", None),
    (
        "uses-permission",
        f"{package_name}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        None,
    ),
]

if mode == "missing-internet":
    permissions = [
        permission
        for permission in permissions
        if permission[1] != "android.permission.INTERNET"
    ]
elif mode == "broad-media":
    permissions.append(
        ("uses-permission", "android.permission.READ_MEDIA_IMAGES", None)
    )
elif mode == "unexpected-audio":
    permissions.append(
        ("uses-permission", "android.permission.RECORD_AUDIO", None)
    )
elif mode == "duplicate-camera":
    permissions.append(
        ("uses-permission", "android.permission.CAMERA", None)
    )
elif mode == "camera-max-sdk":
    permissions = [
        (tag, name, "34" if name == "android.permission.CAMERA" else maximum)
        for tag, name, maximum in permissions
    ]
elif mode == "sdk23-camera":
    permissions = [
        (
            "uses-permission-sdk-23"
            if name == "android.permission.CAMERA"
            else tag,
            name,
            maximum,
        )
        for tag, name, maximum in permissions
    ]
elif mode == "malformed":
    with open(output, "w", encoding="utf-8", newline="\n") as manifest:
        manifest.write("<manifest><uses-permission")
    sys.exit(0)
elif mode not in ({
    "valid",
    "explicit-safe-flags",
    "debuggable-true",
    "debuggable-resource",
    "debuggable-uppercase-false",
    "test-only-true",
    "test-only-resource",
    "cleartext-true",
    "cleartext-resource",
    "cleartext-missing",
    "network-config-missing",
    "network-config-wrong",
    "allow-backup-true",
    "allow-backup-missing",
    "allow-backup-resource",
    "min-sdk-missing",
    "target-sdk-missing",
    "wrong-min-sdk",
    "wrong-target-sdk",
    "max-sdk-present",
    "duplicate-uses-sdk",
    "duplicate-application",
} | component_modes):
    raise SystemExit(f"unsupported manifest mode: {mode}")

root = ElementTree.Element("manifest", {"package": package_name})
sdk_attributes = {
    attribute_min_sdk: "26",
    attribute_target_sdk: "35",
}
if mode == "min-sdk-missing":
    del sdk_attributes[attribute_min_sdk]
elif mode == "target-sdk-missing":
    del sdk_attributes[attribute_target_sdk]
elif mode == "wrong-min-sdk":
    sdk_attributes[attribute_min_sdk] = "27"
elif mode == "wrong-target-sdk":
    sdk_attributes[attribute_target_sdk] = "34"
elif mode == "max-sdk-present":
    sdk_attributes[attribute_max_sdk] = "35"
ElementTree.SubElement(root, "uses-sdk", sdk_attributes)
if mode == "duplicate-uses-sdk":
    ElementTree.SubElement(root, "uses-sdk", sdk_attributes)

for tag, name, maximum_sdk in permissions:
    attributes = {attribute_name: name}
    if maximum_sdk is not None:
        attributes[attribute_max_sdk] = maximum_sdk
    ElementTree.SubElement(root, tag, attributes)
application_attributes = {
    attribute_name: "jp.co.soramitsu.app.App",
    attribute_allow_backup: "false",
    attribute_cleartext: "false",
    attribute_network_security: "@xml/network_security_config",
}
if mode == "explicit-safe-flags":
    application_attributes.update(
        {
            attribute_debuggable: "false",
            attribute_test_only: "false",
            attribute_cleartext: "false",
        }
    )
elif mode == "debuggable-true":
    application_attributes[attribute_debuggable] = "true"
elif mode == "debuggable-resource":
    application_attributes[attribute_debuggable] = "@bool/release_debuggable"
elif mode == "debuggable-uppercase-false":
    application_attributes[attribute_debuggable] = "False"
elif mode == "test-only-true":
    application_attributes[attribute_test_only] = "true"
elif mode == "test-only-resource":
    application_attributes[attribute_test_only] = "@bool/release_test_only"
elif mode == "cleartext-true":
    application_attributes[attribute_cleartext] = "true"
elif mode == "cleartext-resource":
    application_attributes[attribute_cleartext] = "@bool/release_cleartext"
elif mode == "cleartext-missing":
    del application_attributes[attribute_cleartext]
elif mode == "network-config-missing":
    del application_attributes[attribute_network_security]
elif mode == "network-config-wrong":
    application_attributes[attribute_network_security] = (
        "@xml/unapproved_network_security_config"
    )
elif mode == "allow-backup-true":
    application_attributes[attribute_allow_backup] = "true"
elif mode == "allow-backup-missing":
    del application_attributes[attribute_allow_backup]
elif mode == "allow-backup-resource":
    application_attributes[attribute_allow_backup] = "@bool/release_backup"

application = ElementTree.SubElement(root, "application", application_attributes)


def add_component(tag, name, **attributes):
    values = {attribute_name: name}
    values.update(attributes)
    return ElementTree.SubElement(application, tag, values)


def add_intent_filter(
    parent,
    actions,
    categories=(),
    data=(),
    *,
    auto_verify=None,
    priority=None,
    order=None,
):
    attributes = {}
    if auto_verify is not None:
        attributes[attribute_auto_verify] = "true" if auto_verify else "false"
    if priority is not None:
        attributes[attribute_priority] = str(priority)
    if order is not None:
        attributes[attribute_order] = str(order)
    element = ElementTree.SubElement(parent, "intent-filter", attributes)
    for action in actions:
        ElementTree.SubElement(element, "action", {attribute_name: action})
    for category in categories:
        ElementTree.SubElement(
            element, "category", {attribute_name: category}
        )
    for data_entry in data:
        ElementTree.SubElement(
            element,
            "data",
            {
                f"{{{android_namespace}}}{name}": value
                for name, value in data_entry.items()
            },
        )
    return element


qr_scanner = add_component(
    "activity",
    "jp.co.soramitsu.common.qrScanner.QrScannerActivity",
)
startup = add_component(
    "activity",
    startup_activity,
    **{attribute_exported: "true"},
)
add_intent_filter(
    startup,
    ["android.intent.action.VIEW"],
    ["android.intent.category.DEFAULT"],
    [{"mimeType": "application/json"}],
)
launcher = add_intent_filter(
    startup,
    ["android.intent.action.MAIN"],
    ["android.intent.category.LAUNCHER"],
)
add_intent_filter(
    startup,
    ["android.intent.action.VIEW"],
    ["android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"],
    [{"scheme": "fearless"}, {"host": "buy-success"}],
)
ton_filter = add_intent_filter(
    startup,
    ["android.intent.action.VIEW"],
    ["android.intent.category.DEFAULT", "android.intent.category.BROWSABLE"],
    [
        {"scheme": "https"},
        {"host": "fearlesswallet.io"},
        {"pathPrefix": "/ton-connect/"},
    ],
    auto_verify=True,
)
root_component = add_component(
    "activity", root_activity, **{attribute_exported: "false"}
)
wallet_root_component = add_component(
    "activity", wallet_root_activity, **{attribute_exported: "false"}
)
scanner = add_component(
    "activity",
    "jp.co.soramitsu.common.scan.ScannerActivity",
    **{attribute_exported: "false"},
)
add_component(
    "activity",
    "androidx.credentials.playservices.controllers.identityauth.HiddenActivity",
    **{attribute_enabled: "true", attribute_exported: "false"},
)
add_component(
    "activity",
    "androidx.credentials.playservices.controllers.identitycredentials.IdentityCredentialApiHiddenActivity",
    **{attribute_enabled: "true", attribute_exported: "false"},
)
add_component(
    "activity",
    "com.google.android.gms.auth.api.signin.internal.SignInHubActivity",
    **{attribute_exported: "false"},
)
add_component(
    "activity",
    "com.google.android.gms.common.api.GoogleApiActivity",
    **{attribute_exported: "false"},
)
add_component(
    "activity",
    "com.journeyapps.barcodescanner.CaptureActivity",
)

add_component(
    "service",
    "androidx.credentials.playservices.CredentialProviderMetadataHolder",
    **{attribute_enabled: "true", attribute_exported: "false"},
)
firebase_messaging = add_component(
    "service",
    "com.google.firebase.messaging.FirebaseMessagingService",
    **{attribute_exported: "false"},
)
add_intent_filter(
    firebase_messaging,
    ["com.google.firebase.MESSAGING_EVENT"],
    priority=-500,
)
component_discovery = add_component(
    "service",
    "com.google.firebase.components.ComponentDiscoveryService",
    **{attribute_exported: "false"},
)
revocation_service = add_component(
    "service",
    "com.google.android.gms.auth.api.signin.RevocationBoundService",
    **{
        attribute_exported: "true",
        attribute_permission: (
            "com.google.android.gms.auth.api.signin.permission."
            "REVOCATION_NOTIFICATION"
        ),
    },
)
add_component(
    "service",
    "androidx.room.MultiInstanceInvalidationService",
    **{attribute_exported: "false"},
)
add_component(
    "service",
    "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
    **{attribute_exported: "false"},
)
add_component(
    "service",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
    **{
        attribute_exported: "false",
        attribute_permission: "android.permission.BIND_JOB_SERVICE",
    },
)

firebase_receiver = add_component(
    "receiver",
    "com.google.firebase.iid.FirebaseInstanceIdReceiver",
    **{
        attribute_exported: "true",
        attribute_permission: "com.google.android.c2dm.permission.SEND",
    },
)
firebase_receive_filter = add_intent_filter(
    firebase_receiver,
    ["com.google.android.c2dm.intent.RECEIVE"],
)
profile_receiver = add_component(
    "receiver",
    "androidx.profileinstaller.ProfileInstallReceiver",
    **{
        attribute_enabled: "true",
        attribute_exported: "true",
        attribute_permission: "android.permission.DUMP",
    },
)
for profile_action in (
    "androidx.profileinstaller.action.INSTALL_PROFILE",
    "androidx.profileinstaller.action.SKIP_FILE",
    "androidx.profileinstaller.action.SAVE_PROFILE",
    "androidx.profileinstaller.action.BENCHMARK_OPERATION",
):
    add_intent_filter(profile_receiver, [profile_action])
add_component(
    "receiver",
    "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
    **{attribute_exported: "false"},
)

file_provider = add_component(
    "provider",
    "androidx.core.content.FileProvider",
    **{
        attribute_authorities: f"{package_name}.provider",
        attribute_exported: "false",
        attribute_grant_uri_permissions: "true",
    },
)
beacon_provider = add_component(
    "provider",
    "it.airgap.beaconsdk.core.provider.BeaconInitProvider",
    **{
        attribute_authorities: f"{package_name}.beaconinitprovider",
        attribute_exported: "false",
    },
)
firebase_provider = add_component(
    "provider",
    "com.google.firebase.provider.FirebaseInitProvider",
    **{
        attribute_authorities: f"{package_name}.firebaseinitprovider",
        attribute_exported: "false",
    },
)
startup_provider = add_component(
    "provider",
    "androidx.startup.InitializationProvider",
    **{
        attribute_authorities: f"{package_name}.androidx-startup",
        attribute_exported: "false",
    },
)


def remove_component(component):
    application.remove(component)


if mode == "empty-application":
    for child in list(application):
        application.remove(child)
elif mode == "wrong-application-name":
    application.set(attribute_name, "jp.example.SubstitutedApplication")
elif mode == "application-disabled":
    application.set(attribute_enabled, "false")
elif mode == "application-permission":
    application.set(attribute_permission, "jp.example.APP_PERMISSION")
elif mode == "missing-startup":
    remove_component(startup)
elif mode == "duplicate-startup":
    application.append(copy.deepcopy(startup))
elif mode == "renamed-startup":
    startup.set(attribute_name, f"{startup_activity}Renamed")
elif mode == "missing-root":
    remove_component(root_component)
elif mode == "missing-wallet-root":
    remove_component(wallet_root_component)
elif mode == "unexpected-activity":
    add_component(
        "activity", "jp.example.UnreviewedActivity", **{attribute_exported: "false"}
    )
elif mode == "unexpected-activity-alias":
    add_component(
        "activity-alias",
        "jp.example.UnreviewedAlias",
        **{
            attribute_exported: "false",
            attribute_target_activity: startup_activity,
        },
    )
elif mode == "startup-exported-false":
    startup.set(attribute_exported, "false")
elif mode == "root-exported-true":
    root_component.set(attribute_exported, "true")
elif mode == "scanner-exported-true":
    scanner.set(attribute_exported, "true")
elif mode == "startup-disabled":
    startup.set(attribute_enabled, "false")
elif mode == "root-disabled":
    root_component.set(attribute_enabled, "false")
elif mode == "duplicate-launcher-filter":
    startup.append(copy.deepcopy(launcher))
elif mode == "second-launcher-filter":
    add_intent_filter(
        startup,
        ["android.intent.action.MAIN"],
        [
            "android.intent.category.LAUNCHER",
            "android.intent.category.DEFAULT",
        ],
    )
elif mode == "launcher-on-root":
    startup.remove(launcher)
    root_component.append(copy.deepcopy(launcher))
    root_component.set(attribute_exported, "true")
elif mode == "launcher-missing-main":
    launcher.find("action").set(attribute_name, "android.intent.action.VIEW")
elif mode == "launcher-missing-category":
    launcher.find("category").set(
        attribute_name, "android.intent.category.DEFAULT"
    )
elif mode == "launcher-extra-action":
    ElementTree.SubElement(
        launcher,
        "action",
        {attribute_name: "android.intent.action.VIEW"},
    )
elif mode == "launcher-extra-category":
    ElementTree.SubElement(
        launcher,
        "category",
        {attribute_name: "android.intent.category.DEFAULT"},
    )
elif mode == "launcher-extra-data":
    ElementTree.SubElement(
        launcher,
        "data",
        {attribute_scheme: "fearless-launcher"},
    )
elif mode == "missing-ton-filter":
    startup.remove(ton_filter)
elif mode == "mutated-ton-host":
    next(
        item
        for item in ton_filter.findall("data")
        if item.get(attribute_host) is not None
    ).set(attribute_host, "attacker.example")
elif mode == "ton-autoverify-false":
    ton_filter.set(attribute_auto_verify, "false")
elif mode == "missing-service":
    remove_component(component_discovery)
elif mode == "unexpected-service":
    add_component(
        "service", "jp.example.UnreviewedService", **{attribute_exported: "false"}
    )
elif mode == "service-exported":
    component_discovery.set(attribute_exported, "true")
elif mode == "revocation-permission-missing":
    del revocation_service.attrib[attribute_permission]
elif mode == "revocation-permission-mutated":
    revocation_service.set(attribute_permission, "jp.example.WRONG_PERMISSION")
elif mode == "missing-receiver":
    remove_component(profile_receiver)
elif mode == "unexpected-receiver":
    add_component(
        "receiver", "jp.example.UnreviewedReceiver", **{attribute_exported: "false"}
    )
elif mode == "firebase-receiver-permission-missing":
    del firebase_receiver.attrib[attribute_permission]
elif mode == "firebase-receiver-exported-false":
    firebase_receiver.set(attribute_exported, "false")
elif mode == "profile-permission-missing":
    del profile_receiver.attrib[attribute_permission]
elif mode == "receiver-intent-mutated":
    firebase_receive_filter.find("action").set(
        attribute_name, "com.google.android.c2dm.intent.ATTACK"
    )
elif mode == "missing-provider":
    remove_component(beacon_provider)
elif mode == "unexpected-provider":
    add_component(
        "provider",
        "jp.example.UnreviewedProvider",
        **{
            attribute_authorities: f"{package_name}.unreviewedprovider",
            attribute_exported: "false",
        },
    )
elif mode == "file-provider-exported-true":
    file_provider.set(attribute_exported, "true")
elif mode == "authority-mutated":
    file_provider.set(attribute_authorities, f"{package_name}.mutated")
elif mode == "authority-duplicated":
    beacon_provider.set(
        attribute_authorities, firebase_provider.get(attribute_authorities)
    )
elif mode == "authority-repeated":
    authority = file_provider.get(attribute_authorities)
    file_provider.set(attribute_authorities, f"{authority};{authority}")
elif mode == "authority-placeholder":
    file_provider.set(attribute_authorities, "${applicationId}.provider")
elif mode == "grant-uri-disabled":
    file_provider.set(attribute_grant_uri_permissions, "false")
elif mode == "provider-read-permission":
    file_provider.set(attribute_read_permission, "jp.example.READ_PROVIDER")
elif mode == "provider-write-permission":
    file_provider.set(attribute_write_permission, "jp.example.WRITE_PROVIDER")
elif mode == "provider-path-permission":
    ElementTree.SubElement(
        file_provider,
        "path-permission",
        {
            attribute_path_prefix: "/",
            attribute_read_permission: "jp.example.READ_PROVIDER",
        },
    )
elif mode == "duplicate-component":
    application.append(copy.deepcopy(root_component))
elif mode == "malformed-component-name":
    root_component.set(attribute_name, ".RootActivity")
elif mode == "duplicate-intent-action":
    ElementTree.SubElement(
        launcher,
        "action",
        {attribute_name: "android.intent.action.MAIN"},
    )
elif mode == "empty-intent-data":
    ElementTree.SubElement(launcher, "data")
elif mode == "unexpected-intent-attribute":
    launcher.set(f"{{{android_namespace}}}label", "unreviewed")
elif mode == "unexpected-intent-child":
    ElementTree.SubElement(launcher, "meta-data")

if mode == "duplicate-application":
    ElementTree.SubElement(root, "application", application_attributes)
ElementTree.ElementTree(root).write(
    output,
    encoding="utf-8",
    xml_declaration=True,
)
PY
}

run_fake_manifest_verifier() {
  local manifest_file="$1"
  local artifact="${2:-$AAB_IDENTITY_FIXTURE}"
  local expected_r8_policy="$active_r8_policy"
  local expected_source_commit="$EXPECTED_AAB_SOURCE_COMMIT"
  if [[ "$#" -ge 3 ]]; then
    expected_r8_policy="$3"
  fi
  if [[ "$#" -ge 4 ]]; then
    expected_source_commit="$4"
  fi
  PATH="$fake_java_dir:$PATH" \
    REAL_JAVA="$real_java" \
    FAKE_BUNDLETOOL_MANIFEST_FILE="$manifest_file" \
    FAKE_AAB_PACKAGE="$PRODUCTION_AAB_PACKAGE" \
    FAKE_AAB_VERSION_CODE="$EXPECTED_AAB_VERSION_CODE" \
    FAKE_AAB_VERSION_NAME="$EXPECTED_AAB_VERSION_NAME" \
    FAKE_AAB_SOURCE_COMMIT="$EXPECTED_AAB_SOURCE_COMMIT" \
    run_verifier \
      "$artifact" \
      "$PRODUCTION_AAB_PACKAGE" \
      "$EXPECTED_AAB_VERSION_NAME" \
      "$EXPECTED_AAB_VERSION_CODE" \
      "$expected_source_commit" \
      "$expected_r8_policy"
}

run_fake_manifest_verifier_default_required() {
  local manifest_file="$1"
  local artifact="$2"
  PATH="$fake_java_dir:$PATH" \
    REAL_JAVA="$real_java" \
    FAKE_BUNDLETOOL_MANIFEST_FILE="$manifest_file" \
    FAKE_AAB_PACKAGE="$PRODUCTION_AAB_PACKAGE" \
    FAKE_AAB_VERSION_CODE="$EXPECTED_AAB_VERSION_CODE" \
    FAKE_AAB_VERSION_NAME="$EXPECTED_AAB_VERSION_NAME" \
    FAKE_AAB_SOURCE_COMMIT="$EXPECTED_AAB_SOURCE_COMMIT" \
    BUNDLETOOL_JAR="$BUNDLETOOL_JAR" \
    "$VERIFY" \
      "$artifact" \
      "$PRODUCTION_AAB_PACKAGE" \
      "$EXPECTED_AAB_VERSION_NAME" \
      "$EXPECTED_AAB_VERSION_CODE" \
      "$EXPECTED_AAB_SOURCE_COMMIT"
}

mutate_artifact() {
  local mode="$1"
  local output="$2"
  local source="${3:-$AAB_IDENTITY_FIXTURE}"
  python3 -I - "$source" "$output" "$mode" <<'PY'
import sys
import json
import stat
import warnings
import zipfile

source, destination, mode = sys.argv[1:]
replacement_targets = {
    "replace-sodium": "base/lib/arm64-v8a/libsodium.so",
    "replace-sr25519": "base/lib/x86/libsr25519java.so",
    "replace-sqlcipher": "base/lib/armeabi-v7a/libsqlcipher.so",
    "replace-path": "base/lib/x86_64/libandroidx.graphics.path.so",
}
network_security_entry = "base/res/xml/network_security_config.xml"
agp_bundle_metadata_entry = (
    "BUNDLE-METADATA/"
    "com.android.tools.build.gradle/app-metadata.properties"
)
agp_manifest_metadata_entry = (
    "base/root/META-INF/com/android/build/gradle/app-metadata.properties"
)
agp_metadata_entries = {
    agp_bundle_metadata_entry,
    agp_manifest_metadata_entry,
}
r8_metadata_entry = "BUNDLE-METADATA/com.android.tools/r8.json"

with zipfile.ZipFile(source, "r") as archive:
    entries = [(info, archive.read(info)) for info in archive.infolist()]
agp_metadata = next(
    data for info, data in entries if info.filename == agp_bundle_metadata_entry
)
manifest_agp_metadata = next(
    data for info, data in entries
    if info.filename == agp_manifest_metadata_entry
)
if manifest_agp_metadata != agp_metadata:
    raise SystemExit("AGP metadata fixture copies are not byte-identical")
r8_payloads = [
    data for info, data in entries if info.filename == r8_metadata_entry
]
if len(r8_payloads) > 1:
    raise SystemExit("R8 metadata fixture contains duplicate canonical entries")
r8_metadata = (
    r8_payloads[0]
    if r8_payloads
    else b'{"version":"8.10.24"}'
)

with zipfile.ZipFile(destination, "w") as archive:
    for info, data in entries:
        if mode == "missing-abi" and info.filename.startswith("base/lib/x86_64/"):
            continue
        if mode == "missing-network-config" and info.filename == network_security_entry:
            continue
        if (
            mode == "missing-agp-metadata"
            and info.filename == agp_bundle_metadata_entry
        ):
            continue
        if (
            mode == "missing-manifest-agp-metadata"
            and info.filename == agp_manifest_metadata_entry
        ):
            continue
        if mode == "missing-r8-metadata" and info.filename == r8_metadata_entry:
            continue
        if mode in {
            "alternate-agp-path",
            "casefold-agp-path",
            "traversal-agp-path",
        } and info.filename == agp_bundle_metadata_entry:
            continue
        if mode == "backslash-r8-path" and info.filename == r8_metadata_entry:
            continue
        if info.filename == replacement_targets.get(mode):
            data = f"adversarial replacement: {mode}\n".encode()
        if info.filename in agp_metadata_entries:
            if (
                mode == "mismatched-agp-metadata"
                and info.filename == agp_manifest_metadata_entry
            ):
                data += b"# mismatch\n"
            elif mode == "wrong-agp-version":
                needle = b"androidGradlePluginVersion=8.10.1"
                if data.count(needle) != 1:
                    raise SystemExit("AGP metadata fixture is not 8.10.1")
                data = data.replace(
                    needle,
                    b"androidGradlePluginVersion=8.9.1",
                )
            elif mode == "malformed-agp-metadata":
                data += b"androidGradlePluginVersion=8.10.1\n"
            elif mode == "unexpected-agp-key":
                data += b"unreviewedToolchain=true\n"
            elif mode == "wrong-app-metadata-schema":
                needle = b"appMetadataVersion=1.1"
                if data.count(needle) != 1:
                    raise SystemExit("app metadata schema fixture is not 1.1")
                data = data.replace(needle, b"appMetadataVersion=1.2")
            elif mode == "empty-agp-metadata":
                data = b""
            elif mode == "symlink-agp-metadata":
                info.create_system = 3
                info.external_attr = (stat.S_IFLNK | 0o777) << 16
                data = b"../../attacker.properties"
        if info.filename == r8_metadata_entry:
            if mode == "wrong-r8-version":
                value = json.loads(data)
                value["version"] = "8.9.32"
                data = json.dumps(value, separators=(",", ":")).encode()
            elif mode == "malformed-r8-metadata":
                data = b'{"version":"8.10.24"'
            elif mode == "duplicate-r8-version-key":
                data = b'{"version":"8.10.24","version":"8.10.24"}'
            elif mode == "oversized-r8-metadata":
                data = b" " * (2 * 1024 * 1024 + 1)
            elif mode == "symlink-r8-metadata":
                info.create_system = 3
                info.external_attr = (stat.S_IFLNK | 0o777) << 16
                data = b"../../attacker.json"
        if info.filename == network_security_entry:
            if mode == "network-config-compiled-true":
                needle = b"\x3a\x02\x40\x00"
                if data.count(needle) != 1:
                    raise SystemExit("compiled cleartext boolean fixture is ambiguous")
                data = data.replace(needle, b"\x3a\x02\x40\x01")
            elif mode == "network-config-raw-nonliteral":
                if data.count(b"false") != 1:
                    raise SystemExit("raw cleartext fixture is ambiguous")
                data = data.replace(b"false", b"true ")
            elif mode == "network-config-custom-ca":
                if data.count(b"user") != 1:
                    raise SystemExit("debug certificate fixture is ambiguous")
                data = data.replace(b"user", b"evil")
            elif mode == "network-config-unapproved-element":
                if data.count(b"base-config") != 1:
                    raise SystemExit("base-config fixture is ambiguous")
                data = data.replace(b"base-config", b"domain-conf")
        archive.writestr(info, data)

    if mode == "extra-library":
        archive.writestr("base/lib/arm64-v8a/libevil.so", b"unexpected native\n")
    elif mode == "unexpected-module":
        archive.writestr(
            "dynamic/lib/arm64-v8a/libsodium.so",
            b"unexpected dynamic module native\n",
        )
    elif mode == "asset-elf":
        archive.writestr(
            "base/assets/runtime.blob",
            b"\x7fELF\x02\x01\x01\x00concealed asset payload\n",
        )
    elif mode == "renamed-elf":
        archive.writestr(
            "base/lib/arm64-v8a/libconcealed.bin",
            b"\x7fELF\x02\x01\x01\x00renamed native payload\n",
        )
    elif mode == "duplicate-entry":
        target = "base/lib/arm64-v8a/libsodium.so"
        original = next(data for info, data in entries if info.filename == target)
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr(target, original)
    elif mode == "duplicate-agp-metadata":
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr(agp_bundle_metadata_entry, agp_metadata)
    elif mode == "duplicate-manifest-agp-metadata":
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr(agp_manifest_metadata_entry, agp_metadata)
    elif mode == "duplicate-r8-metadata":
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr(r8_metadata_entry, r8_metadata)
    elif mode == "alternate-agp-path":
        archive.writestr(
            "BUNDLE-METADATA/unreviewed/app-metadata.properties",
            agp_metadata,
        )
    elif mode == "casefold-agp-path":
        archive.writestr(
            "BUNDLE-METADATA/com.android.tools.build.gradle/"
            "App-Metadata.properties",
            agp_metadata,
        )
    elif mode == "traversal-agp-path":
        archive.writestr(
            "BUNDLE-METADATA/com.android.tools.build.gradle/../"
            "com.android.tools.build.gradle/app-metadata.properties",
            agp_metadata,
        )
    elif mode == "backslash-agp-path":
        archive.writestr(
            "base\\root\\META-INF\\com\\android\\build\\gradle\\"
            "app-metadata.properties",
            agp_metadata,
        )
    elif mode == "backslash-r8-path":
        archive.writestr(
            "BUNDLE-METADATA\\com.android.tools\\r8.json",
            r8_metadata,
        )
    elif mode == "casefold-r8-path":
        archive.writestr(
            "BUNDLE-METADATA/com.android.tools/R8.json",
            r8_metadata,
        )
    elif mode == "alternate-r8-path":
        archive.writestr(
            "BUNDLE-METADATA/unreviewed/r8.json",
            r8_metadata,
        )
    elif mode == "traversal-r8-path":
        archive.writestr(
            "BUNDLE-METADATA/com.android.tools/../com.android.tools/r8.json",
            r8_metadata,
        )
    elif mode == "unexpected-r8-metadata":
        archive.writestr(r8_metadata_entry, r8_metadata)
    elif mode == "dangling-dom-service":
        archive.writestr(
            "base/root/META-INF/services/"
            "org.w3c.dom.DOMImplementationSourceList",
            b"org.apache.xerces.dom.DOMXSImplementationSourceImpl\n",
        )
    elif mode == "dangling-sax-driver":
        archive.writestr(
            "base/root/META-INF/services/org.xml.sax.driver",
            b"org.apache.xerces.parsers.SAXParser\n",
        )
    elif mode in {
        "network-config-qualified",
        "network-config-feature",
        "duplicate-network-config-entry",
    }:
        original = next(
            data
            for info, data in entries
            if info.filename == network_security_entry
        )
        if mode == "network-config-qualified":
            target = "base/res/xml-v24/network_security_config.xml"
        elif mode == "network-config-feature":
            target = "feature/res/xml/network_security_config.xml"
        else:
            target = network_security_entry
        with warnings.catch_warnings():
            warnings.simplefilter("ignore", UserWarning)
            archive.writestr(target, original)
PY
}

positive_count=0
negative_count=0
r8_policy_positive_count=0
r8_policy_negative_count=0

valid_manifest="$tmp_dir/manifest-valid.xml"
write_manifest_fixture valid "$valid_manifest"

run_fake_manifest_verifier \
  "$valid_manifest" \
  "$original_identity_fixture" \
  "$requested_r8_policy" >/dev/null
r8_policy_positive_count=$((r8_policy_positive_count + 1))
run_fake_manifest_verifier \
  "$valid_manifest" \
  "$fixture_with_r8" \
  required >/dev/null
r8_policy_positive_count=$((r8_policy_positive_count + 1))
run_fake_manifest_verifier \
  "$valid_manifest" \
  "$fixture_without_r8" \
  absent >/dev/null
r8_policy_positive_count=$((r8_policy_positive_count + 1))
run_fake_manifest_verifier_default_required \
  "$valid_manifest" \
  "$fixture_with_r8" >/dev/null
r8_policy_positive_count=$((r8_policy_positive_count + 1))

# The full historical identity suite remains strict release-mode coverage even
# when its input came from the explicitly unminified debug CI lane.
AAB_IDENTITY_FIXTURE="$fixture_with_r8"
active_r8_policy="required"
run_fake_manifest_verifier "$valid_manifest" >/dev/null
positive_count=$((positive_count + 1))

explicit_safe_manifest="$tmp_dir/manifest-explicit-safe-flags.xml"
write_manifest_fixture explicit-safe-flags "$explicit_safe_manifest"
run_fake_manifest_verifier "$explicit_safe_manifest" >/dev/null
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

expect_r8_policy_failure() {
  local label="$1"
  local expected_diagnostic="$2"
  shift 2
  local output="$tmp_dir/r8-policy-failure-$r8_policy_negative_count.log"

  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$expected_diagnostic" "$output" || {
    sed -n '1,120p' "$output" >&2
    fail "$label did not emit the expected diagnostic: $expected_diagnostic"
  }
  r8_policy_negative_count=$((r8_policy_negative_count + 1))
}

expect_r8_policy_failure \
  "required policy with absent R8 metadata" \
  "exactly one R8 metadata entry" \
  run_fake_manifest_verifier "$valid_manifest" "$fixture_without_r8" required
expect_r8_policy_failure \
  "absent policy with canonical R8 metadata" \
  "must not contain R8 metadata" \
  run_fake_manifest_verifier "$valid_manifest" "$fixture_with_r8" absent
expect_r8_policy_failure \
  "default required policy with absent R8 metadata" \
  "exactly one R8 metadata entry" \
  run_fake_manifest_verifier_default_required \
    "$valid_manifest" \
    "$fixture_without_r8"
expect_r8_policy_failure \
  "absent policy with wrong source commit" \
  "AAB source commit mismatch" \
  run_fake_manifest_verifier \
    "$valid_manifest" \
    "$fixture_without_r8" \
    absent \
    0000000000000000000000000000000000000000

for invalid_r8_policy in "" optional Required " absent" "absent "; do
  expect_r8_policy_failure \
    "invalid R8 policy case $r8_policy_negative_count" \
    "must be exactly required or absent" \
    run_fake_manifest_verifier \
      "$valid_manifest" \
      "$fixture_with_r8" \
      "$invalid_r8_policy"
done

for alias_mode in \
  alternate-r8-path \
  backslash-r8-path \
  casefold-r8-path \
  traversal-r8-path; do
  alias_artifact="$tmp_dir/r8-policy-$alias_mode.aab"
  mutate_artifact "$alias_mode" "$alias_artifact" "$fixture_without_r8"
  expect_r8_policy_failure \
    "absent policy with $alias_mode" \
    "non-canonical path" \
    run_fake_manifest_verifier "$valid_manifest" "$alias_artifact" absent
done

duplicate_r8_artifact="$tmp_dir/r8-policy-duplicate.aab"
mutate_artifact \
  duplicate-r8-metadata \
  "$duplicate_r8_artifact" \
  "$fixture_with_r8"
expect_r8_policy_failure \
  "absent policy with duplicate canonical R8 metadata" \
  "must not contain R8 metadata" \
  run_fake_manifest_verifier "$valid_manifest" "$duplicate_r8_artifact" absent

for bypass_mode in \
  missing-agp-metadata \
  duplicate-agp-metadata \
  missing-abi \
  replace-sodium; do
  bypass_artifact="$tmp_dir/r8-policy-bypass-$bypass_mode.aab"
  mutate_artifact "$bypass_mode" "$bypass_artifact" "$fixture_without_r8"
  case "$bypass_mode" in
    missing-agp-metadata|duplicate-agp-metadata)
      expected_diagnostic="exactly one Android Gradle plugin metadata entry"
      ;;
    missing-abi)
      expected_diagnostic="Native payload entries differ from the exact allowlist"
      ;;
    replace-sodium)
      expected_diagnostic="Embedded native library checksum mismatch"
      ;;
  esac
  expect_r8_policy_failure \
    "absent policy bypass attempt $bypass_mode" \
    "$expected_diagnostic" \
    run_fake_manifest_verifier "$valid_manifest" "$bypass_artifact" absent
done

broad_media_manifest="$tmp_dir/r8-policy-broad-media.xml"
write_manifest_fixture broad-media "$broad_media_manifest"
expect_r8_policy_failure \
  "absent policy manifest bypass attempt" \
  "forbidden broad media/storage permission" \
  run_fake_manifest_verifier \
    "$broad_media_manifest" \
    "$fixture_without_r8" \
    absent

write_build_config_fixture() {
  local mode="$1"
  local output="$2"
  python3 -I - "$output" "$mode" <<'PY'
import sys

output, mode = sys.argv[1:]
fixtures = {
    "missing-min-sdk": "targetSdkVersion = 35\n",
    "duplicate-min-sdk": (
        "minSdkVersion = 26\n"
        "minSdkVersion = 27\n"
        "targetSdkVersion = 35\n"
    ),
    "nonliteral-target-sdk": (
        "minSdkVersion = 26\n"
        'targetSdkVersion = providers.gradleProperty("targetSdk")\n'
    ),
    "leading-zero-min-sdk": (
        "minSdkVersion = 026\n"
        "targetSdkVersion = 35\n"
    ),
    "inverted-sdk-range": (
        "minSdkVersion = 36\n"
        "targetSdkVersion = 35\n"
    ),
}

try:
    contents = fixtures[mode]
except KeyError as error:
    raise SystemExit(f"unsupported build config mode: {mode}") from error

with open(output, "w", encoding="utf-8", newline="\n") as config:
    config.write(contents)
PY
}

run_config_fixture_verifier() {
  local fixture_root="$1"
  BUNDLETOOL_JAR="$BUNDLETOOL_JAR" \
    bash "$fixture_root/scripts/verify-android-aab-identity.sh" \
      "$AAB_IDENTITY_FIXTURE" \
      "$EXPECTED_AAB_PACKAGE" \
      "$EXPECTED_AAB_VERSION_NAME" \
      "$EXPECTED_AAB_VERSION_CODE" \
      "$EXPECTED_AAB_SOURCE_COMMIT" \
      "$active_r8_policy"
}

expect_failure \
  "wrong package" \
  "AAB package mismatch" \
  run_verifier "$AAB_IDENTITY_FIXTURE" jp.example.substituted

expect_failure \
  "wrong versionName" \
  "AAB versionName mismatch" \
  run_verifier "$AAB_IDENTITY_FIXTURE" \
    "$EXPECTED_AAB_PACKAGE" 0.0.1

wrong_version_code=1
if [[ "$EXPECTED_AAB_VERSION_CODE" == "1" ]]; then
  wrong_version_code=2
fi
expect_failure \
  "wrong versionCode" \
  "AAB versionCode mismatch" \
  run_verifier "$AAB_IDENTITY_FIXTURE" \
    "$EXPECTED_AAB_PACKAGE" \
    "$EXPECTED_AAB_VERSION_NAME" \
    "$wrong_version_code"

wrong_source_commit=ffffffffffffffffffffffffffffffffffffffff
if [[ "$EXPECTED_AAB_SOURCE_COMMIT" == "$wrong_source_commit" ]]; then
  wrong_source_commit=0000000000000000000000000000000000000000
fi
expect_failure \
  "wrong source commit" \
  "AAB source commit mismatch" \
  run_verifier "$AAB_IDENTITY_FIXTURE" \
    "$EXPECTED_AAB_PACKAGE" \
    "$EXPECTED_AAB_VERSION_NAME" \
    "$EXPECTED_AAB_VERSION_CODE" \
    "$wrong_source_commit"

symlink_artifact="$tmp_dir/symlink-artifact.aab"
ln -s "$AAB_IDENTITY_FIXTURE" "$symlink_artifact"
expect_failure \
  "symlink AAB path" \
  "AAB must be a non-empty regular, non-symlink file" \
  run_verifier "$symlink_artifact"

for config_mode in missing-min-sdk duplicate-min-sdk \
  nonliteral-target-sdk leading-zero-min-sdk inverted-sdk-range; do
  config_fixture_root="$tmp_dir/config-$config_mode"
  mkdir -p "$config_fixture_root/scripts"
  cp "$VERIFY" \
    "$config_fixture_root/scripts/verify-android-aab-identity.sh"
  write_build_config_fixture \
    "$config_mode" \
    "$config_fixture_root/build.gradle"
  case "$config_mode" in
    inverted-sdk-range)
      expected_diagnostic="minSdkVersion exceeds targetSdkVersion"
      ;;
    *)
      expected_diagnostic="must define exactly one literal"
      ;;
  esac
  expect_failure \
    "authoritative build config $config_mode" \
    "$expected_diagnostic" \
    run_config_fixture_verifier "$config_fixture_root"
done

for mutation in \
  missing-agp-metadata \
  missing-manifest-agp-metadata \
  missing-r8-metadata \
  mismatched-agp-metadata \
  wrong-agp-version \
  wrong-r8-version \
  malformed-agp-metadata \
  unexpected-agp-key \
  wrong-app-metadata-schema \
  empty-agp-metadata \
  malformed-r8-metadata \
  duplicate-r8-version-key \
  oversized-r8-metadata \
  duplicate-agp-metadata \
  duplicate-manifest-agp-metadata \
  duplicate-r8-metadata \
  symlink-agp-metadata \
  symlink-r8-metadata \
  alternate-agp-path \
  casefold-agp-path \
  traversal-agp-path \
  backslash-agp-path \
  dangling-dom-service \
  dangling-sax-driver \
  backslash-r8-path; do
  artifact="$tmp_dir/$mutation.aab"
  mutate_artifact "$mutation" "$artifact"
  case "$mutation" in
    missing-agp-metadata|missing-manifest-agp-metadata|duplicate-agp-metadata|duplicate-manifest-agp-metadata)
      expected_diagnostic="exactly one Android Gradle plugin metadata entry"
      ;;
    missing-r8-metadata|duplicate-r8-metadata)
      expected_diagnostic="exactly one R8 metadata entry"
      ;;
    wrong-agp-version)
      expected_diagnostic="AAB Android Gradle plugin mismatch"
      ;;
    mismatched-agp-metadata)
      expected_diagnostic="must be byte-identical"
      ;;
    wrong-r8-version)
      expected_diagnostic="AAB R8 mismatch"
      ;;
    malformed-agp-metadata)
      expected_diagnostic="duplicates property"
      ;;
    unexpected-agp-key)
      expected_diagnostic="exactly the reviewed application metadata keys"
      ;;
    wrong-app-metadata-schema)
      expected_diagnostic="metadata schema must be exactly 1.1"
      ;;
    empty-agp-metadata|oversized-r8-metadata)
      expected_diagnostic="entry has an unsafe size"
      ;;
    malformed-r8-metadata)
      expected_diagnostic="R8 metadata is not safe parseable JSON"
      ;;
    duplicate-r8-version-key)
      expected_diagnostic="duplicate JSON key"
      ;;
    symlink-agp-metadata|symlink-r8-metadata)
      expected_diagnostic="regular non-symlink file"
      ;;
    alternate-agp-path|casefold-agp-path|traversal-agp-path|backslash-agp-path|backslash-r8-path)
      expected_diagnostic="non-canonical path"
      ;;
    dangling-dom-service|dangling-sax-driver)
      expected_diagnostic="dangling R8 ServiceLoader descriptor"
      ;;
  esac
  expect_failure \
    "$mutation" \
    "$expected_diagnostic" \
    run_fake_manifest_verifier "$valid_manifest" "$artifact"
done

for mutation in \
  missing-abi \
  extra-library \
  duplicate-entry \
  unexpected-module; do
  artifact="$tmp_dir/$mutation.aab"
  mutate_artifact "$mutation" "$artifact"
  expect_failure \
    "$mutation" \
    "Native payload entries differ from the exact allowlist" \
    run_fake_manifest_verifier "$valid_manifest" "$artifact"
done

for mutation in \
  replace-sodium \
  replace-sr25519 \
  replace-sqlcipher \
  replace-path; do
  artifact="$tmp_dir/$mutation.aab"
  mutate_artifact "$mutation" "$artifact"
  expect_failure \
    "$mutation" \
    "Embedded native library checksum mismatch" \
    run_fake_manifest_verifier "$valid_manifest" "$artifact"
done

for mutation in asset-elf renamed-elf; do
  artifact="$tmp_dir/$mutation.aab"
  mutate_artifact "$mutation" "$artifact"
  expect_failure \
    "$mutation" \
    "ELF payload is outside exact native allowlist" \
    run_fake_manifest_verifier "$valid_manifest" "$artifact"
done

for mutation in \
  missing-network-config \
  network-config-compiled-true \
  network-config-raw-nonliteral \
  network-config-custom-ca \
  network-config-unapproved-element \
  network-config-qualified \
  network-config-feature \
  duplicate-network-config-entry; do
  artifact="$tmp_dir/$mutation.aab"
  mutate_artifact "$mutation" "$artifact"
  case "$mutation" in
    missing-network-config|network-config-qualified|network-config-feature|duplicate-network-config-entry)
      expected_diagnostic="exactly one network-security resource"
      ;;
    network-config-compiled-true|network-config-raw-nonliteral)
      expected_diagnostic="cleartextTrafficPermitted=false"
      ;;
    network-config-custom-ca)
      expected_diagnostic="exactly system and user certificates"
      ;;
    network-config-unapproved-element)
      expected_diagnostic="only the approved base-config"
      ;;
  esac
  expect_failure \
    "$mutation" \
    "$expected_diagnostic" \
    run_fake_manifest_verifier "$valid_manifest" "$artifact"
done

for manifest_mode in broad-media unexpected-audio missing-internet \
  duplicate-camera camera-max-sdk sdk23-camera malformed \
  debuggable-true debuggable-resource debuggable-uppercase-false \
  test-only-true test-only-resource cleartext-true cleartext-resource \
  cleartext-missing network-config-missing network-config-wrong \
  allow-backup-true allow-backup-missing allow-backup-resource \
  min-sdk-missing target-sdk-missing wrong-min-sdk wrong-target-sdk \
  max-sdk-present duplicate-uses-sdk duplicate-application; do
  manifest_fixture="$tmp_dir/manifest-$manifest_mode.xml"
  write_manifest_fixture "$manifest_mode" "$manifest_fixture"
  case "$manifest_mode" in
    broad-media)
      expected_diagnostic="forbidden broad media/storage permission"
      ;;
    duplicate-camera)
      expected_diagnostic="duplicate permission declarations"
      ;;
    malformed)
      expected_diagnostic="not safe parseable XML"
      ;;
    debuggable-true|debuggable-resource|debuggable-uppercase-false)
      expected_diagnostic="android:debuggable absent or set it to false"
      ;;
    test-only-true|test-only-resource)
      expected_diagnostic="android:testOnly absent or set it to false"
      ;;
    cleartext-true|cleartext-resource|cleartext-missing)
      expected_diagnostic="android:usesCleartextTraffic=false"
      ;;
    network-config-missing|network-config-wrong)
      expected_diagnostic="android:networkSecurityConfig=@xml/network_security_config"
      ;;
    allow-backup-true|allow-backup-missing|allow-backup-resource)
      expected_diagnostic="android:allowBackup=false"
      ;;
    min-sdk-missing|wrong-min-sdk)
      expected_diagnostic="AAB minSdkVersion mismatch"
      ;;
    target-sdk-missing|wrong-target-sdk)
      expected_diagnostic="AAB targetSdkVersion mismatch"
      ;;
    max-sdk-present)
      expected_diagnostic="must not set maxSdkVersion"
      ;;
    duplicate-uses-sdk)
      expected_diagnostic="exactly one direct uses-sdk declaration"
      ;;
    duplicate-application)
      expected_diagnostic="exactly one direct application declaration"
      ;;
    *)
      expected_diagnostic="permission set differs from the approved allowlist"
      ;;
  esac
  expect_failure \
    "merged manifest $manifest_mode" \
    "$expected_diagnostic" \
    run_fake_manifest_verifier "$manifest_fixture"
done

component_manifest_negative_count=0
for manifest_mode in "${component_manifest_modes[@]}"; do
  manifest_fixture="$tmp_dir/manifest-$manifest_mode.xml"
  write_manifest_fixture "$manifest_mode" "$manifest_fixture"
  case "$manifest_mode" in
    empty-application)
      expected_diagnostic="must not have an empty component surface"
      ;;
    wrong-application-name|application-disabled|application-permission)
      expected_diagnostic="application identity or default security policy"
      ;;
    missing-startup|startup-exported-false|startup-disabled|second-launcher-filter|launcher-missing-main|launcher-missing-category)
      expected_diagnostic="exactly one enabled/exported MAIN+LAUNCHER"
      ;;
    duplicate-startup|duplicate-component)
      expected_diagnostic="contains duplicate components"
      ;;
    renamed-startup|launcher-on-root)
      expected_diagnostic="launcher must resolve to the reviewed wallet startup activity"
      ;;
    missing-root|missing-wallet-root|root-exported-true|root-disabled)
      expected_diagnostic="wallet startup activity chain is missing or has unsafe"
      ;;
    scanner-exported-true|service-exported|revocation-permission-missing|firebase-receiver-permission-missing|profile-permission-missing|file-provider-exported-true)
      expected_diagnostic="exported non-launcher component lacks a required permission"
      ;;
    duplicate-launcher-filter)
      expected_diagnostic="contains duplicate intent filters"
      ;;
    unexpected-activity|unexpected-activity-alias|launcher-extra-action|launcher-extra-category|launcher-extra-data|missing-ton-filter|mutated-ton-host|ton-autoverify-false|missing-service|unexpected-service|revocation-permission-mutated|missing-receiver|unexpected-receiver|firebase-receiver-exported-false|receiver-intent-mutated|missing-provider|unexpected-provider|authority-mutated|grant-uri-disabled|provider-read-permission|provider-write-permission)
      expected_diagnostic="exact manifest component surface differs from the reviewed allowlist"
      ;;
    authority-duplicated)
      expected_diagnostic="Provider authorities must be globally unique"
      ;;
    authority-repeated)
      expected_diagnostic="repeats an authority"
      ;;
    authority-placeholder)
      expected_diagnostic="has non-canonical authorities"
      ;;
    provider-path-permission)
      expected_diagnostic="contains unreviewed 'path-permission' policy"
      ;;
    malformed-component-name)
      expected_diagnostic="Component name must be canonical and fully qualified"
      ;;
    duplicate-intent-action)
      expected_diagnostic="contains duplicate action declarations"
      ;;
    empty-intent-data)
      expected_diagnostic="intent-filter data element must not be empty"
      ;;
    unexpected-intent-attribute)
      expected_diagnostic="intent-filter contains unexpected attribute"
      ;;
    unexpected-intent-child)
      expected_diagnostic="intent-filter contains unexpected child"
      ;;
  esac
  expect_failure \
    "component surface $manifest_mode" \
    "$expected_diagnostic" \
    run_fake_manifest_verifier "$manifest_fixture"
  component_manifest_negative_count=$((component_manifest_negative_count + 1))
done

[[ "${#component_manifest_modes[@]}" == "55" ]] ||
  fail \
    "expected 55 declared component mutation modes; got ${#component_manifest_modes[@]}"
[[ "$component_manifest_negative_count" == "${#component_manifest_modes[@]}" ]] ||
  fail \
    "not every declared component mutation mode executed: " \
    "$component_manifest_negative_count/${#component_manifest_modes[@]}"

[[ "$r8_policy_positive_count" == "4" ]] ||
  fail "expected 4 R8-policy positive cases; got $r8_policy_positive_count"
[[ "$r8_policy_negative_count" == "19" ]] ||
  fail \
    "expected 19 R8-policy negative/adversarial cases; got " \
    "$r8_policy_negative_count"
[[ "$positive_count" == "2" ]] ||
  fail "expected 2 positive cases; got $positive_count"
[[ "$negative_count" == "135" ]] ||
  fail "expected 135 negative cases; got $negative_count"

printf '%s\n' \
  "[android-aab-r8-policy-test] $r8_policy_positive_count positive + $r8_policy_negative_count negative/adversarial cases passed"
echo \
  "[android-aab-identity-test] $positive_count positive + $negative_count adversarial cases passed"
