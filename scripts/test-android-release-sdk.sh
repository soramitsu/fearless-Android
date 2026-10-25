#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
TOOL="$ROOT_DIR/scripts/android-release-sdk.py"
LOCK="$ROOT_DIR/scripts/android-release-sdk-linux.lock.json"
WRAPPER="$ROOT_DIR/scripts/install-android-release-sdk.sh"
EXPECTED_POSITIVE_COUNT=3
EXPECTED_NEGATIVE_COUNT=53

fail() {
  echo "[android-release-sdk-test][error] $*" >&2
  exit 1
}

for required in "$TOOL" "$LOCK" "$WRAPPER"; do
  [[ -f "$required" && ! -L "$required" ]] ||
    fail "required release SDK input is missing or unsafe: $required"
done

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-release-sdk.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM
positive_count=0
negative_count=0

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$label did not fail with expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

assert_no_sdk_temporary_trees() {
  local root="$1"
  local residue
  residue="$({
    find "$root" \
      \( -name '.fearless-sdk-stage-*' -o \
      -name '.fearless-sdk-evidence-stage-*' \) \
      -print -quit
  } 2>/dev/null || true)"
  [[ -z "$residue" ]] ||
    fail "SDK installation left a private staging tree behind: $residue"
}

create_fixture() {
  local destination="$1"
  local scenario="$2"
  mkdir -p "$destination/archives"
  python3 - "$destination" "$scenario" <<'PY'
import hashlib
import json
import os
import pathlib
import platform
import stat
import struct
import sys
import warnings
import zipfile

root = pathlib.Path(sys.argv[1])
scenario = sys.argv[2]


def host():
    machine = platform.machine().lower()
    machine = {"amd64": "x86_64", "aarch64": "arm64"}.get(machine, machine)
    return {"os": platform.system().lower(), "arch": machine}


def zip_info(name, mode=stat.S_IFREG | 0o644, compression=zipfile.ZIP_DEFLATED):
    info = zipfile.ZipInfo(name, (2008, 1, 1, 0, 0, 0))
    info.create_system = 3
    info.external_attr = mode << 16
    info.compress_type = compression
    return info


platform_source = b"Pkg.Revision=1\nPkg.UserSrc=false\nAndroidVersion.ApiLevel=99\n"
tools_source = b"Pkg.Revision=1.0.0\nPkg.UserSrc=false\n"
platform_entries = [
    ("platform-root/android.jar", b"android fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("platform-root/core-for-system-modules.jar", b"modules fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("platform-root/framework.aidl", b"framework fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_STORED),
    ("platform-root/source.properties", platform_source, stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
]
tools_entries = [
    ("tools-root/aapt2", b"aapt2 fixture\n", stat.S_IFREG | 0o755, zipfile.ZIP_DEFLATED),
    ("tools-root/aidl", b"aidl fixture\n", stat.S_IFREG | 0o755, zipfile.ZIP_DEFLATED),
    ("tools-root/apksigner", b"apksigner fixture\n", stat.S_IFREG | 0o755, zipfile.ZIP_DEFLATED),
    ("tools-root/core-lambda-stubs.jar", b"lambda fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("tools-root/lib/apksigner.jar", b"apksigner jar fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("tools-root/lib/d8.jar", b"d8 fixture\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("tools-root/source.properties", tools_source, stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED),
    ("tools-root/zipalign", b"zipalign fixture\n", stat.S_IFREG | 0o755, zipfile.ZIP_DEFLATED),
]

if scenario == "wrong-root":
    platform_entries[0] = ("wrong-root/android.jar",) + platform_entries[0][1:]
elif scenario == "extra-root":
    platform_entries.append(("other-root/file", b"other\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED))
elif scenario == "traversal-entry":
    platform_entries.append(("platform-root/../escape", b"escape\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED))
elif scenario == "absolute-entry":
    platform_entries.append(("/platform-root/escape", b"escape\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED))
elif scenario == "backslash-entry":
    platform_entries.append(("platform-root\\escape", b"escape\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED))
elif scenario == "duplicate-entry":
    platform_entries.append(platform_entries[0])
elif scenario == "case-collision":
    platform_entries.append(("platform-root/ANDROID.JAR", b"collision\n", stat.S_IFREG | 0o644, zipfile.ZIP_DEFLATED))
elif scenario == "symlink-entry":
    platform_entries.append(("platform-root/link", b"android.jar", stat.S_IFLNK | 0o777, zipfile.ZIP_STORED))
elif scenario == "fifo-entry":
    platform_entries.append(("platform-root/fifo", b"fifo", stat.S_IFIFO | 0o644, zipfile.ZIP_STORED))
elif scenario == "unsafe-mode":
    platform_entries[0] = (platform_entries[0][0], platform_entries[0][1], stat.S_IFREG | 0o600, platform_entries[0][3])
elif scenario == "unsupported-compression":
    platform_entries[0] = platform_entries[0][:3] + (zipfile.ZIP_BZIP2,)
elif scenario == "missing-source-properties":
    platform_entries = [entry for entry in platform_entries if not entry[0].endswith("source.properties")]
elif scenario == "malformed-source-properties":
    platform_source = b"Pkg.Revision=1\nmalformed-line\n"
    platform_entries[-1] = (platform_entries[-1][0], platform_source, platform_entries[-1][2], platform_entries[-1][3])
elif scenario == "metadata-mismatch":
    platform_source = b"Pkg.Revision=2\nPkg.UserSrc=false\nAndroidVersion.ApiLevel=99\n"
    platform_entries[-1] = (platform_entries[-1][0], platform_source, platform_entries[-1][2], platform_entries[-1][3])
elif scenario == "missing-required-file":
    platform_entries = [entry for entry in platform_entries if not entry[0].endswith("android.jar")]
elif scenario == "empty-required-file":
    platform_entries[0] = (platform_entries[0][0], b"", platform_entries[0][2], platform_entries[0][3])


def write_archive(filename, entries, comment=False):
    path = root / "archives" / filename
    with warnings.catch_warnings():
        warnings.simplefilter("ignore", UserWarning)
        with zipfile.ZipFile(path, "w") as archive:
            for name, data, mode, compression in entries:
                archive.writestr(zip_info(name, mode, compression), data)
            if comment:
                archive.comment = b"forbidden-comment"
    return path


platform_archive = write_archive(
    "fixture-platform.zip", platform_entries, scenario == "zip-comment"
)
tools_archive = write_archive("fixture-tools.zip", tools_entries)

if scenario == "encrypted-entry":
    data = bytearray(platform_archive.read_bytes())
    local = data.find(b"PK\x03\x04")
    central = data.find(b"PK\x01\x02")
    if local < 0 or central < 0:
        raise SystemExit("fixture ZIP signatures missing")
    local_flags = struct.unpack_from("<H", data, local + 6)[0] | 1
    central_flags = struct.unpack_from("<H", data, central + 8)[0] | 1
    struct.pack_into("<H", data, local + 6, local_flags)
    struct.pack_into("<H", data, central + 8, central_flags)
    platform_archive.write_bytes(data)


def archive_record(path, package_type, package_id, revision, archive_root, install_path, source, source_properties, required):
    data = path.read_bytes()
    with zipfile.ZipFile(path) as archive:
        infos = archive.infolist()
    return {
        "packageType": package_type,
        "packageId": package_id,
        "revision": revision,
        "archiveFilename": path.name,
        "archiveUrl": f"https://dl.google.com/android/repository/{path.name}",
        "archiveSize": len(data),
        "archiveSha1": hashlib.sha1(data).hexdigest(),
        "archiveSha256": hashlib.sha256(data).hexdigest(),
        "archiveEntryCount": len(infos),
        "archiveUncompressedSize": sum(info.file_size for info in infos),
        "archiveRoot": archive_root,
        "installPath": install_path,
        "sourcePropertiesSha256": hashlib.sha256(source).hexdigest(),
        "sourceProperties": source_properties,
        "requiredFiles": required,
    }


lock = {
    "schemaVersion": 1,
    "host": host(),
    "license": {
        "id": "android-sdk-license",
        "textSha256": "1" * 64,
        "runtimeAcceptance": "not-performed-direct-archive",
    },
    "packages": [
        archive_record(
            platform_archive,
            "platform",
            "platforms;android-99",
            "1",
            "platform-root",
            "platforms/android-99",
            platform_source,
            {
                "Pkg.Revision": "1",
                "Pkg.UserSrc": "false",
                "AndroidVersion.ApiLevel": "99",
            },
            ["android.jar", "core-for-system-modules.jar", "framework.aidl", "source.properties"],
        ),
        archive_record(
            tools_archive,
            "build-tools",
            "build-tools;1.0.0",
            "1.0.0",
            "tools-root",
            "build-tools/1.0.0",
            tools_source,
            {"Pkg.Revision": "1.0.0", "Pkg.UserSrc": "false"},
            [
                "aapt2",
                "aidl",
                "apksigner",
                "core-lambda-stubs.jar",
                "lib/apksigner.jar",
                "lib/d8.jar",
                "source.properties",
                "zipalign",
            ],
        ),
    ],
}

if scenario == "schema-version":
    lock["schemaVersion"] = 2
elif scenario == "extra-lock-key":
    lock["unexpected"] = True
elif scenario == "license-id":
    lock["license"]["id"] = "android-sdk-preview-license"
elif scenario == "url-scheme":
    lock["packages"][0]["archiveUrl"] = "http://dl.google.com/android/repository/fixture-platform.zip"
elif scenario == "url-host":
    lock["packages"][0]["archiveUrl"] = "https://example.invalid/android/repository/fixture-platform.zip"
elif scenario == "url-query":
    lock["packages"][0]["archiveUrl"] += "?mutable=true"
elif scenario == "install-traversal":
    lock["packages"][0]["installPath"] = "platforms/../escape"
elif scenario == "duplicate-package-type":
    lock["packages"][1]["packageType"] = "platform"
elif scenario == "host-mismatch":
    lock["host"]["arch"] = "x86_64" if lock["host"]["arch"] == "arm64" else "arm64"
elif scenario == "archive-size":
    lock["packages"][0]["archiveSize"] += 1
elif scenario == "archive-sha1":
    lock["packages"][0]["archiveSha1"] = "0" * 40
elif scenario == "archive-sha256":
    lock["packages"][0]["archiveSha256"] = "0" * 64
elif scenario == "entry-count":
    lock["packages"][0]["archiveEntryCount"] += 1
elif scenario == "uncompressed-size":
    lock["packages"][0]["archiveUncompressedSize"] += 1
elif scenario == "source-properties-sha256":
    lock["packages"][0]["sourcePropertiesSha256"] = "0" * 64

(root / "lock.json").write_text(json.dumps(lock, indent=2, sort_keys=True) + "\n")
PY
}

install_fixture() {
  local fixture="$1"
  python3 "$TOOL" install \
    "$fixture/lock.json" \
    "$fixture/sdk" \
    "$fixture/archives" \
    "$fixture/evidence"
}

install_fixture_with_fault() {
  local fixture="$1"
  local fault="$2"
  python3 - "$TOOL" "$fixture" "$fault" <<'PY'
import importlib.util
import os
from pathlib import Path
import signal
import sys

tool = Path(sys.argv[1])
fixture = Path(sys.argv[2])
fault = sys.argv[3]
spec = importlib.util.spec_from_file_location("fearless_android_release_sdk", tool)
if spec is None or spec.loader is None:
    raise SystemExit("unable to load Android release SDK module")
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

original_make_read_only = module.make_read_only


def inject_fault(root):
    original_make_read_only(root)
    if fault == "rename-parent":
        fixture.rename(fixture.with_name(fixture.name + "-moved"))
        return
    if fault.startswith("signal:"):
        signal_name = fault.removeprefix("signal:")
        os.kill(os.getpid(), getattr(signal, signal_name))
        return
    raise AssertionError(f"unknown injected fault: {fault}")


module.make_read_only = inject_fault
try:
    module.command_install(
        fixture / "lock.json",
        fixture / "sdk",
        fixture / "archives",
        fixture / "evidence",
    )
except module.ReleaseSdkError as error:
    print(f"[android-release-sdk][error] {error}", file=sys.stderr)
    raise SystemExit(1) from None
raise SystemExit("fault-injected SDK installation unexpectedly succeeded")
PY
}

verify_fixture() {
  local fixture="$1"
  python3 "$TOOL" verify \
    "$fixture/lock.json" \
    "$fixture/sdk" \
    "$fixture/evidence"
}

# Production constants and absence of package-manager escape hatches are part of
# this executable test, in addition to the release workflow's shared meta-test.
jq -e '
  .schemaVersion == 1 and
  .host == {os: "linux", arch: "x86_64"} and
  .license.id == "android-sdk-license" and
  (.packages | length) == 2 and
  .packages[0].packageId == "platforms;android-36" and
  .packages[0].revision == "2" and
  .packages[0].archiveUrl ==
    "https://dl.google.com/android/repository/platform-36_r02.zip" and
  .packages[0].archiveSize == 65878410 and
  .packages[0].archiveSha256 ==
    "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e" and
  .packages[0].archiveRoot == "android-36" and
  .packages[0].installPath == "platforms/android-36" and
  .packages[1].packageId == "build-tools;35.0.0" and
  .packages[1].revision == "35.0.0" and
  .packages[1].archiveUrl ==
    "https://dl.google.com/android/repository/build-tools_r35_linux.zip" and
  .packages[1].archiveSize == 61958799 and
  .packages[1].archiveSha256 ==
    "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88" and
  .packages[1].archiveRoot == "android-15" and
  .packages[1].installPath == "build-tools/35.0.0"
' "$LOCK" >/dev/null || fail "production Android SDK lock differs from reviewed archives"
python3 "$TOOL" validate-lock "$LOCK" >/dev/null
for forbidden in sdkmanager commandlinetools platform-tools 'ndk;28.0.12674087'; do
  if grep -Fq -- "$forbidden" "$WRAPPER" "$LOCK"; then
    fail "release SDK path restored forbidden package-manager content: $forbidden"
  fi
done
positive_count=$((positive_count + 1))

valid="$tmp_dir/valid"
create_fixture "$valid" valid
install_fixture "$valid"
verify_fixture "$valid"
python3 "$TOOL" validate-evidence "$valid/lock.json" "$valid/evidence/evidence.json"
assert_no_sdk_temporary_trees "$valid"
positive_count=$((positive_count + 1))

valid_second="$tmp_dir/valid-second"
create_fixture "$valid_second" valid
install_fixture "$valid_second"
cmp "$valid/evidence/evidence.json" "$valid_second/evidence/evidence.json" >/dev/null ||
  fail "identical SDK inputs did not produce deterministic evidence"
cmp "$valid/evidence/installed-tree.json" "$valid_second/evidence/installed-tree.json" >/dev/null ||
  fail "identical SDK inputs did not produce a deterministic tree manifest"
[[ "$(python3 "$TOOL" download-plan "$valid/lock.json" | wc -l | tr -d '[:space:]')" == "2" ]] ||
  fail "valid SDK lock did not produce exactly two downloads"
assert_no_sdk_temporary_trees "$valid_second"
positive_count=$((positive_count + 1))

rename_parent="$tmp_dir/case-$negative_count-rename-parent"
create_fixture "$rename_parent" valid
expect_failure \
  "SDK parent rename during installation" \
  "Installed SDK package is missing or unsafe" \
  install_fixture_with_fault "$rename_parent" rename-parent
[[ ! -e "$rename_parent" && -d "$rename_parent-moved" ]] ||
  fail "parent-rename fixture did not preserve the adversarial rename"
assert_no_sdk_temporary_trees "$rename_parent-moved"
[[ ! -e "$rename_parent-moved/sdk" && ! -e "$rename_parent-moved/evidence" ]] ||
  fail "parent-rename failure published a partial SDK or evidence tree"

for signal_name in SIGHUP SIGINT SIGTERM; do
  interrupted="$tmp_dir/case-$negative_count-$signal_name"
  create_fixture "$interrupted" valid
  expect_failure \
    "$signal_name interruption cleanup" \
    "SDK installation interrupted by $signal_name" \
    install_fixture_with_fault "$interrupted" "signal:$signal_name"
  assert_no_sdk_temporary_trees "$interrupted"
  [[ ! -e "$interrupted/sdk" && ! -e "$interrupted/evidence" ]] ||
    fail "$signal_name interruption published a partial SDK or evidence tree"
done

run_install_failure() {
  local scenario="$1"
  local expected="$2"
  local fixture="$tmp_dir/case-$negative_count-$scenario"
  create_fixture "$fixture" "$scenario"
  expect_failure "$scenario" "$expected" install_fixture "$fixture"
  assert_no_sdk_temporary_trees "$fixture"
}

run_install_failure schema-version "schemaVersion must be exactly 1"
run_install_failure extra-lock-key "must contain exactly"
run_install_failure license-id "standard android-sdk-license"
run_install_failure url-scheme "exact official Google HTTPS path"
run_install_failure url-host "exact official Google HTTPS path"
run_install_failure url-query "exact official Google HTTPS path"
run_install_failure install-traversal "unsafe path component"
run_install_failure duplicate-package-type "one platform and one build-tools package"
run_install_failure host-mismatch "SDK lock host mismatch"

missing_archive="$tmp_dir/case-$negative_count-missing-archive"
create_fixture "$missing_archive" valid
rm "$missing_archive/archives/fixture-platform.zip"
expect_failure "missing archive" "is missing" install_fixture "$missing_archive"

symlink_archive="$tmp_dir/case-$negative_count-symlink-archive"
create_fixture "$symlink_archive" valid
mv "$symlink_archive/archives/fixture-platform.zip" "$symlink_archive/platform-target.zip"
ln -s "$symlink_archive/platform-target.zip" "$symlink_archive/archives/fixture-platform.zip"
expect_failure "symlink archive" "regular, non-symlink file" install_fixture "$symlink_archive"

run_install_failure archive-size "size mismatch"
run_install_failure archive-sha1 "SHA-1 mismatch"
run_install_failure archive-sha256 "SHA-256 mismatch"
run_install_failure entry-count "entry count mismatch"
run_install_failure uncompressed-size "uncompressed size mismatch"
run_install_failure wrong-root "differs from root"
run_install_failure extra-root "differs from root"
run_install_failure traversal-entry "escapes or differs from root"
run_install_failure absolute-entry "forbidden entry path"
run_install_failure backslash-entry "forbidden entry path"
run_install_failure duplicate-entry "duplicate entry"
run_install_failure case-collision "case-colliding entry"
run_install_failure encrypted-entry "encrypted entry"
run_install_failure symlink-entry "non-regular or unsafe-mode entry"
run_install_failure fifo-entry "non-regular or unsafe-mode entry"
run_install_failure unsafe-mode "non-regular or unsafe-mode entry"
run_install_failure unsupported-compression "unsupported compression method"
run_install_failure zip-comment "must not contain a ZIP comment"
run_install_failure missing-source-properties "source.properties is missing"
run_install_failure malformed-source-properties "contains a malformed line"
run_install_failure metadata-mismatch "source.properties identity mismatch"
run_install_failure source-properties-sha256 "source.properties digest mismatch"
run_install_failure missing-required-file "missing required file"
run_install_failure empty-required-file "required file is empty or unsafe"

preexisting="$tmp_dir/case-$negative_count-preexisting-root"
create_fixture "$preexisting" valid
mkdir "$preexisting/sdk"
expect_failure "preexisting SDK root" "must not exist before isolated installation" install_fixture "$preexisting"

clone_valid() {
  local fixture="$1"
  mkdir -p "$fixture"
  cp "$valid/lock.json" "$fixture/lock.json"
  cp -R "$valid/sdk" "$fixture/sdk"
  cp -R "$valid/evidence" "$fixture/evidence"
}

mutated="$tmp_dir/case-$negative_count-content-mutation"
clone_valid "$mutated"
chmod u+w "$mutated/sdk/platforms/android-99/android.jar"
printf 'mutation\n' >>"$mutated/sdk/platforms/android-99/android.jar"
expect_failure "installed content mutation" "tree differs from its captured manifest" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-mode-mutation"
clone_valid "$mutated"
chmod 0444 "$mutated/sdk/build-tools/1.0.0/aapt2"
expect_failure "installed mode mutation" "tree differs from its captured manifest" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-extra-file"
clone_valid "$mutated"
chmod u+w "$mutated/sdk"
printf 'unexpected\n' >"$mutated/sdk/unexpected"
expect_failure "installed extra file" "tree differs from its captured manifest" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-deleted-file"
clone_valid "$mutated"
chmod u+w "$mutated/sdk/platforms/android-99"
rm "$mutated/sdk/platforms/android-99/framework.aidl"
expect_failure "installed file deletion" "missing required file" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-installed-symlink"
clone_valid "$mutated"
chmod u+w "$mutated/sdk/platforms/android-99"
rm "$mutated/sdk/platforms/android-99/android.jar"
ln -s framework.aidl "$mutated/sdk/platforms/android-99/android.jar"
expect_failure "installed symlink" "required file is empty or unsafe" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-root-mode"
clone_valid "$mutated"
chmod 0755 "$mutated/sdk"
expect_failure "SDK root mode mutation" "tree differs from its captured manifest" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-evidence-extra-key"
clone_valid "$mutated"
chmod u+w "$mutated/evidence/evidence.json"
python3 - "$mutated/evidence/evidence.json" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
value = json.loads(path.read_text())
value["unexpected"] = True
path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
PY
expect_failure "evidence extra key" "must contain exactly" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-evidence-lock-digest"
clone_valid "$mutated"
chmod u+w "$mutated/evidence/evidence.json"
python3 - "$mutated/evidence/evidence.json" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
value = json.loads(path.read_text())
value["lockSha256"] = "0" * 64
path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
PY
expect_failure "evidence lock digest mutation" "not bound to the checked-in lock" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-evidence-package"
clone_valid "$mutated"
chmod u+w "$mutated/evidence/evidence.json"
python3 - "$mutated/evidence/evidence.json" <<'PY'
import json, pathlib, sys
path = pathlib.Path(sys.argv[1])
value = json.loads(path.read_text())
value["packages"][0]["archiveUrl"] = "https://dl.google.com/android/repository/attacker.zip"
path.write_text(json.dumps(value, indent=2, sort_keys=True) + "\n")
PY
expect_failure "evidence package mutation" "differs from the lock" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-manifest-mutation"
clone_valid "$mutated"
chmod u+w "$mutated/evidence/installed-tree.json"
printf '\n' >>"$mutated/evidence/installed-tree.json"
expect_failure "tree manifest mutation" "tree-manifest digest differs from evidence" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-symlink-evidence-root"
clone_valid "$mutated"
mv "$mutated/evidence" "$mutated/evidence-target"
ln -s "$mutated/evidence-target" "$mutated/evidence"
expect_failure "symlink evidence root" "existing non-symlink directory" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-empty-evidence"
clone_valid "$mutated"
chmod u+w "$mutated/evidence/evidence.json"
: >"$mutated/evidence/evidence.json"
expect_failure "empty evidence" "has an unsafe size" verify_fixture "$mutated"

mutated="$tmp_dir/case-$negative_count-source-property-content"
clone_valid "$mutated"
chmod u+w "$mutated/sdk/platforms/android-99/source.properties"
printf 'Extra=true\n' >>"$mutated/sdk/platforms/android-99/source.properties"
expect_failure "installed package identity mutation" "source.properties digest mismatch" verify_fixture "$mutated"

assert_no_sdk_temporary_trees "$tmp_dir"

[[ "$positive_count" == "$EXPECTED_POSITIVE_COUNT" ]] ||
  fail "expected $EXPECTED_POSITIVE_COUNT positive cases; got $positive_count"
[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]] ||
  fail "expected $EXPECTED_NEGATIVE_COUNT negative cases; got $negative_count"

echo "[android-release-sdk-test] $positive_count positive + $negative_count negative/adversarial cases passed"
