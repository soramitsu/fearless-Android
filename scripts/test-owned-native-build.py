#!/usr/bin/env python3
"""Test scripts/build-sr25519.py with fake Rust, cargo-ndk and NDK tools.

The fakes record what the build script runs, so these tests pin the build
contract (four ABIs, API 26, 16 KB page flags, path remapping, a cleaned
environment, toolchain pins) and every fail-closed check (SHA-256, LOAD
alignment, JNI exports, NDK revision, output location) without compiling.
"""
from pathlib import Path
import hashlib
import json
import os
import platform
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
TARGETS = {
    "arm64-v8a": "aarch64-linux-android",
    "armeabi-v7a": "armv7-linux-androideabi",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}
RUSTC = "rustc 1.99.1 (58cf8f9c3 2026-10-13)"
EXPORTS = ["Java_jp_co_soramitsu_fearless_1utils_encrypt_Sr25519_sign",
           "Java_jp_co_soramitsu_shared_1utils_encrypt_Sr25519_sign"]
HOST_TAG = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64"}.get(platform.system(), "linux-x86_64")

FAKE_CARGO = r'''#!/usr/bin/env python3
import json, os, sys
from pathlib import Path
log = Path(os.environ["TEST_ROOT"]) / "invocations.jsonl"
with log.open("a") as handle:
    keys = ("CARGO_ENCODED_RUSTFLAGS", "SOURCE_DATE_EPOCH", "ANDROID_NDK_HOME", "CARGO_TARGET_DIR",
            "RUSTFLAGS", "RUSTC_WRAPPER", "CARGO_PROFILE_RELEASE_OPT_LEVEL", "CARGO_INCREMENTAL")
    handle.write(json.dumps({"argv": sys.argv[1:], "cwd": os.getcwd(),
                             "env": {k: os.environ.get(k) for k in keys}}) + "\n")
args = sys.argv[1:]
if args[:2] == ["ndk", "--version"]:
    print(os.environ.get("FAKE_CARGO_NDK_VERSION", "cargo-ndk 4.1.2"))
    sys.exit(0)
if os.environ.get("FAKE_COMPILE_FAIL"):
    sys.exit(1)
if args[:1] == ["ndk"]:
    staging = Path(args[args.index("-o") + 1])
    for abi in ("arm64-v8a", "armeabi-v7a", "x86", "x86_64"):
        if os.environ.get("FAKE_OMIT_ABI") == abi:
            continue
        (staging / abi).mkdir(parents=True, exist_ok=True)
        content = "built " + abi + os.environ.get("FAKE_CONTENT_SUFFIX", "")
        counter = Path(os.environ["TEST_ROOT"]) / "build-count"
        count = int(counter.read_text()) if counter.exists() else 0
        if os.environ.get("FAKE_NONDETERMINISTIC") and count:
            content += " run " + str(count)
        (staging / abi / "libsr25519java.so").write_text(content)
    counter = Path(os.environ["TEST_ROOT"]) / "build-count"
    counter.write_text(str((int(counter.read_text()) if counter.exists() else 0) + 1))
    sys.exit(0)
if args[:1] == ["build"]:
    target = Path(os.environ["CARGO_TARGET_DIR"]) / "release"
    target.mkdir(parents=True, exist_ok=True)
    for name in ("libsr25519java.dylib", "libsr25519java.so"):
        (target / name).write_text("host library")
    sys.exit(0)
sys.exit(2)
'''

FAKE_RUSTC = r'''#!/usr/bin/env python3
import os, sys
if sys.argv[1:] == ["--version"]:
    print(os.environ.get("FAKE_RUSTC_VERSION", "RUSTC_PLACEHOLDER"))
elif sys.argv[1:] == ["--print", "sysroot"]:
    print(os.path.join(os.environ["TEST_ROOT"], "rust-toolchain"))
else:
    sys.exit(2)
'''

FAKE_READELF = r'''#!/usr/bin/env python3
import os, sys
path = sys.argv[-1]
if "--program-headers" in sys.argv:
    alignment = os.environ.get("FAKE_ALIGNMENT", "0x4000")
    offset = os.environ.get("FAKE_OFFSET", "0x004000")
    print("Program Headers:")
    print("  Type           Offset   VirtAddr           PhysAddr           FileSiz  MemSiz   Flg Align")
    print("  LOAD           0x000000 0x0000000000000000 0x0000000000000000 0x001000 0x001000 R E " + alignment)
    print("  LOAD           " + offset + " 0x0000000000004000 0x0000000000004000 0x000100 0x000100 RW  " + alignment)
elif "--dyn-syms" in sys.argv:
    print("Symbol table '.dynsym' contains 4 entries:")
    print("   Num:    Value          Size Type    Bind   Vis       Ndx Name")
    print("     0: 0000000000000000     0 NOTYPE  LOCAL  DEFAULT   UND ")
    print("     1: 0000000000000000     0 FUNC    GLOBAL DEFAULT   UND memcpy@LIBC (2)")
    names = os.environ["FAKE_EXPORTS"].split(",")
    for index, name in enumerate(names, start=2):
        print("     %d: 0000000000001000    10 FUNC    GLOBAL DEFAULT    14 %s" % (index, name))
else:
    sys.exit(2)
'''


class BuildSr25519Test(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name).resolve()
        (self.root / "scripts").mkdir()
        shutil.copyfile(ROOT / "scripts/build-sr25519.py", self.root / "scripts/build-sr25519.py")
        self.crate = self.root / "runtime-utils/native/sr25519"
        self.crate.mkdir(parents=True)
        self.manifest = {
            "schemaVersion": 1,
            "crate": "runtime-utils/native/sr25519",
            "library": "libsr25519java.so",
            "rustc": RUSTC,
            "cargoNdk": "cargo-ndk 4.1.2",
            "ndkVersion": "30.0.16248370",
            "androidApiLevel": 26,
            "sourceDateEpoch": 1794009600,
            "jniExports": EXPORTS,
            "abis": {abi: {"rustTarget": TARGETS[abi],
                           "sha256": hashlib.sha256(("built " + abi).encode()).hexdigest()} for abi in ABIS},
        }
        self.save_manifest()
        self.ndk = self.root / "sdk/ndk/30.0.16248370"
        readelf = self.ndk / "toolchains/llvm/prebuilt" / HOST_TAG / "bin/llvm-readelf"
        readelf.parent.mkdir(parents=True)
        self.write_tool(readelf, FAKE_READELF)
        (self.ndk / "source.properties").write_text("Pkg.Desc = Android NDK\nPkg.Revision = 30.0.16248370\n")
        (self.root / "rust-toolchain").mkdir()
        bin_dir = self.root / "bin"
        bin_dir.mkdir()
        self.write_tool(bin_dir / "cargo", FAKE_CARGO)
        self.write_tool(bin_dir / "rustc", FAKE_RUSTC.replace("RUSTC_PLACEHOLDER", RUSTC))
        self.write_tool(bin_dir / "cargo-ndk", "#!/bin/sh\nexit 0\n")
        self.env = dict(
            os.environ,
            TEST_ROOT=str(self.root),
            PATH=str(bin_dir) + os.pathsep + os.environ["PATH"],
            CARGO_HOME=str(self.root / "cargo-home"),
            ANDROID_HOME=str(self.root / "sdk"),
            FAKE_EXPORTS=",".join(EXPORTS),
        )
        self.output = self.root / "build/native/sr25519/jniLibs"

    def tearDown(self):
        self.tmp.cleanup()

    @staticmethod
    def write_tool(path, source):
        path.write_text(source)
        path.chmod(0o755)

    def save_manifest(self):
        (self.crate / "reproducible-build.json").write_text(json.dumps(self.manifest, indent=2) + "\n")

    def run_build(self, *arguments, **environment):
        command = ["python3", "-I", str(self.root / "scripts/build-sr25519.py"), *arguments]
        return subprocess.run(command, env=dict(self.env, **environment), capture_output=True, text=True)

    def invocations(self):
        log = self.root / "invocations.jsonl"
        if not log.exists():
            return []
        return [json.loads(line) for line in log.read_text().splitlines()]

    def published(self):
        return {abi: (self.output / abi / "libsr25519java.so").read_text()
                for abi in ABIS if (self.output / abi / "libsr25519java.so").exists()}

    def test_builds_four_abis_at_api_26_with_page_flags_and_remapping(self):
        result = self.run_build("--output", str(self.output), RUSTFLAGS="-Copt-level=0",
                                RUSTC_WRAPPER="sccache", CARGO_PROFILE_RELEASE_OPT_LEVEL="0")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual({abi: "built " + abi for abi in ABIS}, self.published())
        build = [entry for entry in self.invocations() if entry["argv"][:2] == ["ndk", "-t"]]
        self.assertEqual(1, len(build))
        argv = build[0]["argv"]
        self.assertEqual(["ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86", "-t", "x86_64",
                          "--platform", "26", "-o"], argv[:12])
        self.assertEqual(["build", "--release", "--locked"], argv[13:])
        self.assertEqual(str(self.crate), build[0]["cwd"])
        environment = build[0]["env"]
        flags = environment["CARGO_ENCODED_RUSTFLAGS"].split("\x1f")
        self.assertIn("-Clink-arg=-Wl,-z,common-page-size=4096", flags)
        self.assertIn("-Clink-arg=-Wl,-z,max-page-size=16384", flags)
        self.assertIn("-Ccodegen-units=1", flags)
        self.assertIn("--remap-path-prefix=" + str(self.root / "cargo-home") + "=/cargo-home", flags)
        self.assertIn("--remap-path-prefix=" + str(self.root / "rust-toolchain") + "=/rust-toolchain", flags)
        self.assertIn("--remap-path-prefix=" + str(self.crate) + "=/sr25519", flags)
        self.assertEqual("1794009600", environment["SOURCE_DATE_EPOCH"])
        self.assertEqual(str(self.ndk), environment["ANDROID_NDK_HOME"])
        self.assertEqual("0", environment["CARGO_INCREMENTAL"])
        self.assertIsNone(environment["RUSTFLAGS"])
        self.assertIsNone(environment["RUSTC_WRAPPER"])
        self.assertIsNone(environment["CARGO_PROFILE_RELEASE_OPT_LEVEL"])

    def test_hash_mismatch_fails_and_publishes_nothing(self):
        result = self.run_build("--output", str(self.output), FAKE_CONTENT_SUFFIX=" tampered")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("differ from runtime-utils/native/sr25519/reproducible-build.json", result.stderr)
        self.assertEqual({}, self.published())

    def test_allow_hash_mismatch_publishes_with_a_warning(self):
        result = self.run_build("--output", str(self.output), "--allow-hash-mismatch", FAKE_CONTENT_SUFFIX=" dev")
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("WARNING", result.stdout)
        self.assertEqual({abi: "built " + abi + " dev" for abi in ABIS}, self.published())

    def test_record_writes_the_new_hashes(self):
        result = self.run_build("--output", str(self.output), "--record", FAKE_CONTENT_SUFFIX=" v2")
        self.assertEqual(0, result.returncode, result.stderr)
        manifest = json.loads((self.crate / "reproducible-build.json").read_text())
        for abi in ABIS:
            expected = hashlib.sha256(("built " + abi + " v2").encode()).hexdigest()
            self.assertEqual(expected, manifest["abis"][abi]["sha256"])

    def test_wrong_rustc_stops_before_compiling(self):
        result = self.run_build("--output", str(self.output), FAKE_RUSTC_VERSION="rustc 1.89.0 (29483883e 2025-08-04)")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("rust-toolchain.toml", result.stderr)
        self.assertFalse(any(entry["argv"][:2] == ["ndk", "-t"] for entry in self.invocations()))

    def test_wrong_cargo_ndk_stops_before_compiling(self):
        result = self.run_build("--output", str(self.output), FAKE_CARGO_NDK_VERSION="cargo-ndk 3.5.4")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cargo install cargo-ndk --version 4.1.2 --locked", result.stderr)
        self.assertFalse(any(entry["argv"][:2] == ["ndk", "-t"] for entry in self.invocations()))

    def test_unpinned_or_preview_ndk_is_refused(self):
        (self.ndk / "source.properties").write_text("Pkg.Revision = 30.0.16248370-beta1\n")
        result = self.run_build("--output", str(self.output))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("preview releases are refused", result.stderr)
        shutil.rmtree(self.ndk)
        result = self.run_build("--output", str(self.output))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("is not installed", result.stderr)

    def test_misaligned_load_segments_fail(self):
        result = self.run_build("--output", str(self.output), FAKE_ALIGNMENT="0x1000", FAKE_OFFSET="0x001000")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("aligned to 0x1000", result.stderr)
        self.assertEqual({}, self.published())

    def test_unexpected_or_missing_exports_fail(self):
        result = self.run_build("--output", str(self.output),
                                FAKE_EXPORTS=",".join(EXPORTS + ["rust_eh_personality"]))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("rust_eh_personality", result.stderr)
        result = self.run_build("--output", str(self.output), FAKE_EXPORTS=EXPORTS[0])
        self.assertNotEqual(0, result.returncode)
        self.assertIn(EXPORTS[1], result.stderr)

    def test_missing_abi_fails(self):
        result = self.run_build("--output", str(self.output), FAKE_OMIT_ABI="x86")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("no libsr25519java.so for x86", result.stderr)
        self.assertEqual({}, self.published())

    def test_compile_failure_fails(self):
        self.assertNotEqual(0, self.run_build("--output", str(self.output), FAKE_COMPILE_FAIL="1").returncode)
        self.assertEqual({}, self.published())

    def test_refuses_to_write_into_a_source_tree(self):
        target = self.root / "app/src/main/jniLibs"
        result = self.run_build("--output", str(target))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("source tree", result.stderr)
        self.assertFalse(target.exists())

    def test_reproducibility_check_requires_identical_builds(self):
        result = self.run_build("--reproducibility-check", "--target-dir", str(self.root / "repro"))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn("byte-identical", result.stdout)
        self.assertEqual(2, sum(1 for entry in self.invocations() if entry["argv"][:2] == ["ndk", "-t"]))
        targets = {entry["env"]["CARGO_TARGET_DIR"] for entry in self.invocations() if entry["argv"][:2] == ["ndk", "-t"]}
        self.assertEqual(2, len(targets), "each build uses a fresh target directory")

    def test_reproducibility_check_rejects_differing_builds(self):
        result = self.run_build("--reproducibility-check", "--target-dir", str(self.root / "repro"),
                                FAKE_NONDETERMINISTIC="1")
        self.assertNotEqual(0, result.returncode)
        self.assertIn("differ", result.stderr)

    def test_manifest_schema_is_strict(self):
        self.manifest["androidApiLevel"] = 21
        self.save_manifest()
        result = self.run_build("--output", str(self.output))
        self.assertNotEqual(0, result.returncode)
        self.assertIn("API 26", result.stderr)

    def test_host_library_for_jvm_tests(self):
        host = self.root / "build/sr25519/host"
        result = self.run_build("--host-output", str(host), "--target-dir", str(self.root / "host-target"))
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertTrue(any(child.name.startswith("libsr25519java") for child in host.iterdir()))
        build = [entry for entry in self.invocations() if entry["argv"][:1] == ["build"]]
        self.assertEqual(["build", "--release", "--locked"], build[0]["argv"])


if __name__ == "__main__":
    unittest.main()
