#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERIFIER="$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py"
if [[ -d /private/tmp && ! -L /private/tmp ]]; then
  tmp_dir="$(mktemp -d /private/tmp/android-aab-native-alignment.XXXXXX)"
else
  tmp_dir="$(mktemp -d)"
fi
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-aab-native-alignment-test][error] $*" >&2
  exit 1
}

negative_count=0
expect_failure() {
  local label="$1"
  shift
  negative_count=$((negative_count + 1))
  if "$@" >/dev/null 2>&1; then
    fail "$label was accepted"
  fi
}

make_fixture() {
  local mode="$1"
  local output="$2"
  python3 - "$mode" "$output" <<'PY'
import struct
import sys
import warnings
import zipfile

mode, output = sys.argv[1:]
machines = {"armeabi-v7a": 40, "arm64-v8a": 183, "x86": 3, "x86_64": 62}

def elf(abi, *, alignment=16384, virtual_address=0, machine=None, program_type=1):
    machine = machines[abi] if machine is None else machine
    elf_class = 1 if abi in {"armeabi-v7a", "x86"} else 2
    ident = b"\x7fELF" + bytes([elf_class, 1, 1, 0, 0]) + bytes(7)
    if elf_class == 1:
        size = 84
        header = struct.pack("<16sHHIIIIIHHHHHH", ident, 3, machine, 1, 0, 52, 0, 0, 52, 32, 1, 40, 0, 0)
        program = struct.pack("<IIIIIIII", program_type, 0, virtual_address, 0, size, size, 5, alignment)
    else:
        size = 120
        header = struct.pack("<16sHHIQQQIHHHHHH", ident, 3, machine, 1, 0, 64, 0, 0, 64, 56, 1, 64, 0, 0)
        program = struct.pack("<IIQQQQQQ", program_type, 5, 0, virtual_address, 0, size, size, alignment)
    return header + program

if mode == "not-zip":
    with open(output, "wb") as artifact:
        artifact.write(b"not an app bundle")
    raise SystemExit

with warnings.catch_warnings():
    warnings.simplefilter("ignore", UserWarning)
    with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("BundleConfig.pb", b"fixture")
        if mode == "no-native":
            raise SystemExit
        for abi in machines:
            if mode == "missing-abi" and abi == "x86":
                continue
            for library in ("libfixture.so", "libsecond.so"):
                if mode == "inconsistent-libraries" and abi == "x86_64" and library == "libsecond.so":
                    continue
                kwargs = {}
                if mode == "low-alignment" and abi == "arm64-v8a" and library == "libfixture.so":
                    kwargs["alignment"] = 4096
                if mode == "non-power-alignment" and abi == "arm64-v8a" and library == "libfixture.so":
                    kwargs["alignment"] = 24576
                if mode == "misaligned-address" and abi == "arm64-v8a" and library == "libfixture.so":
                    kwargs["virtual_address"] = 4096
                if mode == "wrong-machine" and abi == "arm64-v8a" and library == "libfixture.so":
                    kwargs["machine"] = 62
                if mode == "missing-load" and abi == "arm64-v8a" and library == "libfixture.so":
                    kwargs["program_type"] = 2
                payload = elf(abi, **kwargs)
                if mode == "not-elf" and abi == "arm64-v8a" and library == "libfixture.so":
                    payload = b"not elf"
                if mode == "truncated-elf" and abi == "arm64-v8a" and library == "libfixture.so":
                    payload = payload[:32]
                name = f"base/lib/{abi}/{library}"
                archive.writestr(name, payload)
                if mode == "duplicate-entry" and abi == "arm64-v8a" and library == "libfixture.so":
                    archive.writestr(name, payload)
        if mode == "unsupported-abi":
            archive.writestr("base/lib/riscv64/libfixture.so", elf("x86_64"))
        if mode == "unsafe-path":
            archive.writestr("../escape", b"unsafe")
PY
}

valid="$tmp_dir/valid.aab"
make_fixture valid "$valid"
python3 "$VERIFIER" "$valid" >/dev/null || fail "valid 16 KiB fixture was rejected"

expect_failure "missing argument" python3 "$VERIFIER"
expect_failure "extra argument" python3 "$VERIFIER" "$valid" extra

for mode in \
  not-zip \
  no-native \
  missing-abi \
  inconsistent-libraries \
  low-alignment \
  non-power-alignment \
  misaligned-address \
  wrong-machine \
  missing-load \
  not-elf \
  truncated-elf \
  duplicate-entry \
  unsupported-abi \
  unsafe-path; do
  fixture="$tmp_dir/$mode.aab"
  make_fixture "$mode" "$fixture"
  expect_failure "$mode AAB" python3 "$VERIFIER" "$fixture"
done

symlink_fixture="$tmp_dir/symlink.aab"
ln -s "$valid" "$symlink_fixture"
expect_failure "symlink AAB" python3 "$VERIFIER" "$symlink_fixture"

symlink_parent_target="$tmp_dir/real-parent"
mkdir -p "$symlink_parent_target"
cp "$valid" "$symlink_parent_target/fixture.aab"
ln -s "$symlink_parent_target" "$tmp_dir/symlink-parent"
expect_failure "symlink-parent AAB" python3 "$VERIFIER" "$tmp_dir/symlink-parent/fixture.aab"

directory_fixture="$tmp_dir/directory.aab"
mkdir "$directory_fixture"
expect_failure "directory AAB" python3 "$VERIFIER" "$directory_fixture"

oversized_fixture="$tmp_dir/oversized.aab"
python3 - "$oversized_fixture" <<'PY'
import sys
with open(sys.argv[1], "wb") as artifact:
    artifact.truncate(262144001)
PY
expect_failure "oversized AAB" python3 "$VERIFIER" "$oversized_fixture"

echo "[android-aab-native-alignment-test] passed (1 valid contract plus $negative_count negative scenarios)"
