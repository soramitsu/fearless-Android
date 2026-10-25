#!/usr/bin/env python3
"""Exercise same-checkout source ownership and fail-closed file verification."""
import hashlib
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("runtime_sources", ROOT / "scripts/verify-android-runtime-sources.py")
VERIFY = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(VERIFY)


class RuntimeSourcesTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "config").mkdir()
        (self.root / "gradle").mkdir()
        self.manifest = {"schemaVersion": 2}
        for name, module in VERIFY.MODULES.items():
            (self.root / module).mkdir()
            (self.root / module / "source.kt").write_text("reviewed source\n")
            self.manifest[name] = {
                "repository": "soramitsu/fixture", "commit": "1" * 40, "tree": "2" * 40,
                "module": module, "files": {"source.kt": hashlib.sha256(b"reviewed source\n").hexdigest()},
            }
        for path in ("settings.gradle", "build.gradle", "gradle/libs.versions.toml"):
            (self.root / path).write_text("// app-owned modules\n")
        self.save()

    def tearDown(self):
        self.tmp.cleanup()

    def save(self):
        (self.root / "config/android-runtime-source-pins.json").write_text(json.dumps(self.manifest))

    def rejected(self):
        with self.assertRaises((ValueError, OSError)):
            VERIFY.verify(self.root)

    def test_exact_owned_sources(self):
        self.assertEqual(self.manifest, VERIFY.verify(self.root))

    def test_modified_source(self):
        (self.root / "runtime-utils/source.kt").write_text("changed")
        self.rejected()

    def test_unlisted_source(self):
        (self.root / "runtime-websocket/extra.java").write_text("extra")
        self.rejected()

    def test_missing_source(self):
        (self.root / "runtime-websocket/source.kt").unlink()
        self.rejected()

    def test_symlinked_source(self):
        source = self.root / "runtime-websocket/source.kt"
        source.unlink()
        source.symlink_to(self.root / "runtime-utils/source.kt")
        self.rejected()

    def test_symlinked_module(self):
        module = self.root / "runtime-websocket"
        (module / "source.kt").unlink()
        module.rmdir()
        module.symlink_to(self.root / "runtime-utils", target_is_directory=True)
        self.rejected()

    def test_nested_checkout(self):
        (self.root / "runtime-utils/.git").write_text("gitdir: elsewhere")
        self.rejected()

    def test_unknown_manifest_fields(self):
        self.manifest["utils"]["fallback"] = True
        self.save()
        self.rejected()

    def test_path_escape(self):
        self.manifest["utils"]["module"] = "../runtime-utils"
        self.save()
        self.rejected()

    def test_invalid_digest(self):
        self.manifest["utils"]["files"]["source.kt"] = "a" * 40
        self.save()
        self.rejected()

    def test_external_build_and_override_rejected(self):
        for value in VERIFY.FORBIDDEN:
            with self.subTest(value=value):
                (self.root / "settings.gradle").write_text(value)
                self.rejected()

    def test_generated_outputs_allowed(self):
        output = self.root / "runtime-utils/build/generated/compiled.class"
        output.parent.mkdir(parents=True)
        output.write_bytes(b"compiled")
        self.assertEqual(self.manifest, VERIFY.verify(self.root))

    def test_generated_symlink_rejected(self):
        (self.root / "runtime-utils/build").symlink_to(self.root / "runtime-websocket", target_is_directory=True)
        self.rejected()

    def test_checked_in_runtime_manifest(self):
        VERIFY.verify(ROOT)
    def test_module_published_dependency(self):
        module_build = self.root / 'runtime-utils/build.gradle'
        source = 'implementation "jp.co.soramitsu.fearless-utils:fearless-utils:1"'
        module_build.write_text(source)
        self.manifest['utils']['files']['build.gradle'] = hashlib.sha256(source.encode()).hexdigest()
        self.save()
        self.rejected()



if __name__ == "__main__":
    unittest.main()
