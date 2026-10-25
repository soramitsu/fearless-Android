#!/usr/bin/env python3
"""Test native rebuild orchestration with a fake compiler, never replacing real binaries."""
from pathlib import Path
import os
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
ABIS = ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')


class NativeBuildTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        for name in ['scripts', 'runtime-utils/native/sr25519', 'ndk', 'bin']:
            (self.root / name).mkdir(parents=True)
        shutil.copyfile(ROOT / 'scripts/build-sr25519.sh', self.root / 'scripts/build-sr25519.sh')
        (self.root / 'scripts/verify-android-runtime-sources.sh').write_text('exit "${GUARD_EXIT:-0}"\n')
        for abi in ABIS:
            path = self.root / 'app/src/main/jniLibs' / abi
            path.mkdir(parents=True)
            (path / 'libsr25519java.so').write_text('original')
        fake = self.root / 'bin/cargo'
        fake.write_text('''#!/bin/bash
set -eu
printf '%s\\n' "$@" > "$TEST_ROOT/arguments"
[[ "$PWD" == "$TEST_ROOT/runtime-utils/native/sr25519" ]]
[[ "$CARGO_TARGET_DIR" == "$TEST_ROOT/build/native/sr25519/target" ]]
[[ "${COMPILE_FAIL:-0}" == 0 ]] || exit 1
for abi in arm64-v8a armeabi-v7a x86 x86_64; do
  [[ "${OMIT_ABI:-}" != "$abi" ]] || continue
  mkdir -p "$TEST_ROOT/build/native/sr25519/jniLibs/$abi"
  printf built > "$TEST_ROOT/build/native/sr25519/jniLibs/$abi/libsr25519java.so"
done
''')
        fake.chmod(0o755)
        (self.root / 'bin/cargo-ndk').symlink_to(fake)
        self.env = dict(os.environ, TEST_ROOT=str(self.root), ANDROID_NDK_HOME=str(self.root / 'ndk'), PATH=str(self.root / 'bin') + ':' + os.environ['PATH'])

    def tearDown(self):
        self.tmp.cleanup()

    def run_build(self, **updates):
        result = subprocess.run(['bash', str(self.root / 'scripts/build-sr25519.sh')], env=dict(self.env, **updates), capture_output=True, text=True)
        return result.returncode

    def originals_preserved(self):
        for abi in ABIS:
            self.assertEqual('original', (self.root / 'app/src/main/jniLibs' / abi / 'libsr25519java.so').read_text())

    def test_builds_locked_owned_source_all_abis(self):
        self.assertEqual(0, self.run_build())
        args = (self.root / 'arguments').read_text().splitlines()
        self.assertEqual(['ndk', '-t', 'arm64-v8a', '-t', 'armeabi-v7a', '-t', 'x86', '-t', 'x86_64', '--platform', '21', '-o', str(self.root / 'build/native/sr25519/jniLibs'), 'build', '--release', '--locked'], args)
        for abi in ABIS:
            self.assertEqual('built', (self.root / 'app/src/main/jniLibs' / abi / 'libsr25519java.so').read_text())

    def test_source_drift_stops_before_compiler(self):
        self.assertNotEqual(0, self.run_build(GUARD_EXIT='1'))
        self.assertFalse((self.root / 'arguments').exists())
        self.originals_preserved()

    def test_compile_failure_preserves_binaries(self):
        self.assertNotEqual(0, self.run_build(COMPILE_FAIL='1'))
        self.originals_preserved()

    def test_missing_abi_preserves_all_binaries(self):
        self.assertNotEqual(0, self.run_build(OMIT_ABI='x86'))
        self.originals_preserved()


if __name__ == '__main__':
    unittest.main()
