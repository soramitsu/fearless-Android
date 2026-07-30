#!/usr/bin/env python3
"""Adversarial tests for portable Android migration evidence staging."""

from __future__ import annotations

import hashlib
import importlib.util
import json
import os
import socket
import stat
import tempfile
import time
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("prepare-android-migration-evidence.py")
SPEC = importlib.util.spec_from_file_location("migration_evidence", SCRIPT)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError(f"cannot import {SCRIPT}")
evidence = importlib.util.module_from_spec(SPEC)
os.sys.modules[SPEC.name] = evidence
SPEC.loader.exec_module(evidence)


class PortableComponentTest(unittest.TestCase):
    def test_exact_arrow_regression_is_encoded(self) -> None:
        self.assertEqual(
            evidence.portable_component("released schema 26 -> 77"),
            "released schema 26 -~3E 77",
        )

    def test_all_artifact_forbidden_characters_are_encoded(self) -> None:
        encoded = evidence.portable_component('a"b/c:d<e>f|g*h?i\\j')
        self.assertEqual(encoded, "a~22b~2Fc~3Ad~3Ce~3Ef~7Cg~2Ah~3Fi~5Cj")
        self.assertFalse(set(encoded) & evidence.WINDOWS_FORBIDDEN)

    def test_controls_del_and_tilde_are_encoded(self) -> None:
        encoded = evidence.portable_component("a\x00\x01\r\n\x7f~z")
        self.assertEqual(encoded, "a~00~01~0D~0A~7F~7Ez")

    def test_escape_encoding_is_injective(self) -> None:
        self.assertNotEqual(
            evidence.portable_component("a>b"),
            evidence.portable_component("a~3Eb"),
        )
        self.assertEqual(evidence.portable_component("a>b"), "a~3Eb")
        self.assertEqual(evidence.portable_component("a~3Eb"), "a~7E3Eb")

    def test_trailing_periods_and_spaces_are_encoded(self) -> None:
        self.assertEqual(evidence.portable_component("report. "), "report~2E~20")

    def test_windows_device_names_are_disarmed_case_insensitively(self) -> None:
        for name in (
            "CON",
            "con.txt",
            "CON .txt",
            "PrN",
            "AUX.json",
            "nul",
            "COM1",
            "lpt9.log",
            "CONIN$",
            "conout$.txt",
            "COM¹",
            "com².log",
            "LPT³",
        ):
            with self.subTest(name=name):
                encoded = evidence.portable_component(name)
                self.assertTrue(encoded.startswith("~"))
                self.assertNotIn(
                    encoded.split(".", 1)[0].upper(),
                    evidence.WINDOWS_RESERVED,
                )

    def test_non_reserved_near_misses_remain_readable(self) -> None:
        for name in ("CONSOLE", "COM0", "COM10", "LPT0", "LPT10"):
            with self.subTest(name=name):
                self.assertEqual(evidence.portable_component(name), name)

    def test_unicode_is_preserved(self) -> None:
        self.assertEqual(
            evidence.portable_component("移行テスト-кошелёк.txt"),
            "移行テスト-кошелёк.txt",
        )

    def test_leading_dot_is_encoded_to_keep_artifact_visible(self) -> None:
        self.assertEqual(evidence.portable_component(".hidden.xml"), "~2Ehidden.xml")
        self.assertEqual(evidence.portable_component("..nested"), "~2E.nested")

    def test_long_components_are_bounded_and_content_addressed(self) -> None:
        first = evidence.portable_component("a" * 240 + "x")
        second = evidence.portable_component("a" * 240 + "y")
        self.assertLessEqual(len(first.encode("utf-8")), evidence.MAX_COMPONENT_BYTES)
        self.assertTrue("~H" in first)
        self.assertNotEqual(first, second)
        self.assertEqual(first, evidence.portable_component("a" * 240 + "x"))


class EvidenceStagingTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.project = Path(self.temporary.name) / "project"
        self.project.mkdir()
        self.output = self.project / evidence.OUTPUT_RELATIVE

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def _write(self, relative: str, payload: bytes = b"evidence") -> Path:
        path = self.project / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(payload)
        return path

    def _single_source(self, source: Path, prefix: str = "fixture"):
        return [evidence.SourceRoot(source=source, destination_prefix=(prefix,))]

    def test_exact_ci_regression_stages_with_safe_name_and_manifest(self) -> None:
        filename = (
            "logcat-jp.co.soramitsu.coredb.migrations."
            "ReleasedSchemaUpgradeMatrixTest-"
            "exactReleasedSchemaUpgradesTo77WithoutLosingWalletState"
            "[released schema 26 -> 77].txt"
        )
        source_file = self._write(
            "build/reports/android-migration-compatibility/"
            "api-30/core-db/emulator-5554 - 11/"
            + filename,
            b"all 39 tests passed\n",
        )
        source_hash = hashlib.sha256(source_file.read_bytes()).hexdigest()
        manifest = evidence.stage_evidence(self.project, self.output)
        self.assertEqual(manifest["file_count"], 1)
        entry = manifest["files"][0]
        self.assertNotIn(">", entry["staged"])
        self.assertLessEqual(
            len(entry["staged"].encode("utf-8")),
            evidence.MAX_RELATIVE_PATH_BYTES,
        )
        staged = self.output / entry["staged"]
        self.assertEqual(staged.read_bytes(), source_file.read_bytes())
        self.assertEqual(entry["sha256"], source_hash)
        self.assertEqual(source_file.name, filename)

    def test_default_discovery_covers_every_uploaded_evidence_family(self) -> None:
        expected = {
            "common/build/outputs/androidTest-results/connected/result.xml",
            "core-db/build/reports/androidTests/connected/report.html",
            "build/reports/android-migration-compatibility/api-30/log.txt",
            "build/reports/android-emulator-lifecycle/api-34/status.env",
        }
        for relative in expected:
            self._write(relative, relative.encode())
        manifest = evidence.stage_evidence(self.project, self.output)
        self.assertEqual(
            {entry["source"] for entry in manifest["files"]},
            expected,
        )
        staged_roots = {
            Path(entry["staged"]).parts[0] for entry in manifest["files"]
        }
        self.assertEqual(
            staged_roots,
            {
                "android-test-results",
                "android-test-reports",
                "compatibility",
                "emulator-lifecycle",
            },
        )

    def test_recursive_discovery_preserves_root_and_nested_module_evidence(self) -> None:
        expected = {
            "build/outputs/androidTest-results/connected/root.xml",
            (
                "nested/group/module/build/outputs/"
                "androidTest-results/connected/nested.xml"
            ),
        }
        for relative in expected:
            self._write(relative, relative.encode())
        manifest = evidence.stage_evidence(self.project, self.output)
        self.assertEqual(
            {entry["source"] for entry in manifest["files"]},
            expected,
        )
        staged = {entry["source"]: entry["staged"] for entry in manifest["files"]}
        self.assertEqual(
            staged["build/outputs/androidTest-results/connected/root.xml"],
            "android-test-results/root.xml",
        )
        self.assertEqual(
            staged[
                "nested/group/module/build/outputs/"
                "androidTest-results/connected/nested.xml"
            ],
            "android-test-results/nested/group/module/nested.xml",
        )

    def test_hidden_files_and_directories_are_encoded_not_omitted(self) -> None:
        source_root = self.project / "source"
        self._write("source/.hidden.xml", b"hidden file")
        self._write("source/.hidden-directory/result.xml", b"hidden directory")
        manifest = evidence.stage_evidence(
            self.project,
            self.output,
            self._single_source(source_root),
        )
        self.assertEqual(manifest["file_count"], 2)
        for entry in manifest["files"]:
            self.assertTrue((self.output / entry["staged"]).is_file())
            self.assertTrue(
                all(not part.startswith(".") for part in Path(entry["staged"]).parts)
            )
        self.assertEqual(
            {entry["source"] for entry in manifest["files"]},
            {
                "source/.hidden.xml",
                "source/.hidden-directory/result.xml",
            },
        )

    def test_overlong_relative_paths_fail_closed(self) -> None:
        source_root = self.project / "source"
        deep = source_root
        for index in range(9):
            deep /= f"directory-{index}-" + ("x" * 24)
        deep.mkdir(parents=True)
        (deep / "result.xml").write_bytes(b"too deep for portable extraction")
        with self.assertRaisesRegex(evidence.EvidenceError, "exceeds 220 bytes"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )
        self.assertFalse(self.output.exists())

    def test_hostile_real_filenames_stage_uniquely_without_source_mutation(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        hostile_names = (
            'quote"colon:less<than>pipe|star*question?.txt',
            "line\rbreak\n.txt",
            "a>b",
            "a~3Eb",
            "CON.txt",
            "trailing. ",
            "unicode-移行-кошелёк.txt",
        )
        original_payloads: dict[str, bytes] = {}
        for index, name in enumerate(hostile_names):
            payload = f"payload-{index}".encode()
            (source_root / name).write_bytes(payload)
            original_payloads[name] = payload
        nested = source_root / "bad?directory"
        nested.mkdir()
        (nested / "result>.xml").write_bytes(b"nested")

        manifest = evidence.stage_evidence(
            self.project,
            self.output,
            self._single_source(source_root),
        )
        staged_names = [entry["staged"] for entry in manifest["files"]]
        self.assertEqual(len(staged_names), len(set(staged_names)))
        self.assertEqual(len(staged_names), len(hostile_names) + 1)
        for staged_name in staged_names:
            for component in Path(staged_name).parts:
                evidence._validate_portable_component(component)
        for name, payload in original_payloads.items():
            self.assertEqual((source_root / name).read_bytes(), payload)
        self.assertTrue((nested / "result>.xml").is_file())

    def test_manifest_and_tree_are_byte_deterministic(self) -> None:
        source = self._write("source/z.txt", b"z")
        source_root = source.parent
        sources = self._single_source(source_root)
        evidence.stage_evidence(self.project, self.output, sources)
        first_manifest = (self.output / evidence.MANIFEST_NAME).read_bytes()
        first_sha = (self.output / evidence.MANIFEST_SHA256_NAME).read_bytes()
        first_files = sorted(
            (
                path.relative_to(self.output).as_posix(),
                path.read_bytes(),
            )
            for path in self.output.rglob("*")
            if path.is_file()
        )
        evidence.stage_evidence(self.project, self.output, sources)
        self.assertEqual(
            (self.output / evidence.MANIFEST_NAME).read_bytes(),
            first_manifest,
        )
        self.assertEqual(
            (self.output / evidence.MANIFEST_SHA256_NAME).read_bytes(),
            first_sha,
        )
        second_files = sorted(
            (
                path.relative_to(self.output).as_posix(),
                path.read_bytes(),
            )
            for path in self.output.rglob("*")
            if path.is_file()
        )
        self.assertEqual(second_files, first_files)

    def test_manifest_checksum_is_exact(self) -> None:
        source = self._write("source/result.xml", b"<testsuite/>")
        evidence.stage_evidence(
            self.project,
            self.output,
            self._single_source(source.parent),
        )
        manifest_bytes = (self.output / evidence.MANIFEST_NAME).read_bytes()
        expected = hashlib.sha256(manifest_bytes).hexdigest()
        checksum = (
            self.output / evidence.MANIFEST_SHA256_NAME
        ).read_text(encoding="ascii")
        self.assertEqual(checksum, f"{expected}  {evidence.MANIFEST_NAME}\n")
        parsed = json.loads(manifest_bytes)
        self.assertEqual(parsed["file_count"], 1)

    def test_staged_files_are_read_only_and_directories_are_private(self) -> None:
        source = self._write("source/result.xml")
        manifest = evidence.stage_evidence(
            self.project,
            self.output,
            self._single_source(source.parent),
        )
        staged = self.output / manifest["files"][0]["staged"]
        self.assertEqual(stat.S_IMODE(staged.stat().st_mode), 0o400)
        self.assertEqual(stat.S_IMODE(self.output.stat().st_mode), 0o700)

    def test_duplicate_destination_contract_fails_closed(self) -> None:
        source = self._write("source/result.xml")
        contract = evidence.SourceRoot(source=source.parent, destination_prefix=("same",))
        with self.assertRaisesRegex(evidence.EvidenceError, "duplicate evidence source"):
            evidence.stage_evidence(
                self.project,
                self.output,
                [contract, contract],
            )

    def test_casefold_equivalent_destinations_fail_closed(self) -> None:
        source = self._write("source/result.xml")
        with self.assertRaisesRegex(evidence.EvidenceError, "case/Unicode-normalized"):
            evidence.stage_evidence(
                self.project,
                self.output,
                [
                    evidence.SourceRoot(source.parent, ("Case",)),
                    evidence.SourceRoot(source.parent, ("case",)),
                ],
            )
        self.assertFalse(self.output.exists())

    def test_unicode_normalization_equivalent_destinations_fail_closed(self) -> None:
        source = self._write("source/result.xml")
        with self.assertRaisesRegex(evidence.EvidenceError, "case/Unicode-normalized"):
            evidence.stage_evidence(
                self.project,
                self.output,
                [
                    evidence.SourceRoot(source.parent, ("\u00e9",)),
                    evidence.SourceRoot(source.parent, ("e\u0301",)),
                ],
            )
        self.assertFalse(self.output.exists())

    def test_file_directory_prefix_collision_fails_closed(self) -> None:
        root = evidence.CanonicalPathNode()
        evidence._register_portable_path(root, Path("same"), "first")
        with self.assertRaisesRegex(evidence.EvidenceError, "file/directory prefix"):
            evidence._register_portable_path(root, Path("same/child"), "second")

    def test_generated_manifest_paths_are_reserved_case_insensitively(self) -> None:
        source = self._write("source/result.xml")
        with self.assertRaises(evidence.EvidenceError):
            evidence.stage_evidence(
                self.project,
                self.output,
                [
                    evidence.SourceRoot(
                        source.parent,
                        ("MANIFEST.JSON",),
                    )
                ],
            )
        self.assertFalse(self.output.exists())

    def test_symlinked_file_is_rejected(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        target = self._write("target.txt", b"secret")
        (source_root / "result.xml").symlink_to(target)
        with self.assertRaisesRegex(evidence.EvidenceError, "must not be symlinks"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )

    def test_symlinked_directory_is_rejected(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        target = self.project / "target"
        target.mkdir()
        (source_root / "nested").symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(evidence.EvidenceError, "must not be symlinks"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )

    @unittest.skipUnless(hasattr(os, "mkfifo"), "FIFO requires POSIX")
    def test_fifo_is_rejected(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        os.mkfifo(source_root / "blocked")
        with self.assertRaisesRegex(evidence.EvidenceError, "must be regular files"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )

    @unittest.skipUnless(hasattr(socket, "AF_UNIX"), "Unix socket requires POSIX")
    def test_socket_is_rejected(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        socket_path = source_root / "blocked.sock"
        server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            server.bind(os.fspath(socket_path))
            with self.assertRaisesRegex(evidence.EvidenceError, "regular files"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source_root),
                )
        finally:
            server.close()

    def test_traversal_error_cannot_silently_omit_nested_evidence(self) -> None:
        source_root = self.project / "source"
        nested = source_root / "blocked"
        nested.mkdir(parents=True)
        (source_root / "visible.txt").write_bytes(b"visible")
        (nested / "hidden.txt").write_bytes(b"must not be omitted")
        blocked_inode = nested.stat().st_ino
        original_listdir = evidence.os.listdir

        def fail_for_blocked(descriptor):
            if os.fstat(descriptor).st_ino == blocked_inode:
                raise PermissionError("adversarial traversal denial")
            return original_listdir(descriptor)

        evidence.os.listdir = fail_for_blocked
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "failed to enumerate"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source_root),
                )
        finally:
            evidence.os.listdir = original_listdir
        self.assertFalse(self.output.exists())

    def test_discovery_error_cannot_silently_omit_evidence_root(self) -> None:
        self._write(
            "blocked/nested/module/build/outputs/"
            "androidTest-results/connected/must-upload.xml",
            b"must not be omitted",
        )
        self._write(
            "build/reports/android-emulator-lifecycle/status.env",
            b"visible fallback must not mask discovery failure",
        )
        blocked_inode = (self.project / "blocked").stat().st_ino
        original_listdir = evidence.os.listdir

        def fail_discovery(descriptor):
            if os.fstat(descriptor).st_ino == blocked_inode:
                raise PermissionError("adversarial discovery denial")
            return original_listdir(descriptor)

        evidence.os.listdir = fail_discovery
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "failed to discover"):
                evidence.stage_evidence(self.project, self.output)
        finally:
            evidence.os.listdir = original_listdir
        self.assertFalse(self.output.exists())

    def test_empty_sources_fail_closed(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        with self.assertRaisesRegex(evidence.EvidenceError, "zero files"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )

    def test_missing_default_sources_fail_closed(self) -> None:
        with self.assertRaisesRegex(evidence.EvidenceError, "no Android migration"):
            evidence.stage_evidence(self.project, self.output)

    def test_source_outside_project_is_rejected(self) -> None:
        outside = Path(self.temporary.name) / "outside"
        outside.mkdir()
        (outside / "result.xml").write_text("outside", encoding="utf-8")
        with self.assertRaisesRegex(evidence.EvidenceError, "escapes project root"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(outside),
            )

    def test_output_outside_project_is_rejected(self) -> None:
        source = self._write("source/result.xml")
        outside = Path(self.temporary.name) / "upload"
        with self.assertRaisesRegex(evidence.EvidenceError, "escapes project root"):
            evidence.stage_evidence(
                self.project,
                outside,
                self._single_source(source.parent),
            )

    def test_output_overlapping_source_is_rejected(self) -> None:
        source = self._write("source/result.xml")
        with self.assertRaisesRegex(evidence.EvidenceError, "overlaps evidence source"):
            evidence.stage_evidence(
                self.project,
                source.parent / "upload",
                self._single_source(source.parent),
            )

    def test_source_nested_in_output_is_rejected(self) -> None:
        source = self._write(
            f"{evidence.OUTPUT_RELATIVE}/nested/result.xml",
            b"recursive evidence",
        )
        with self.assertRaisesRegex(evidence.EvidenceError, "overlaps evidence source"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source.parent),
            )

    def test_existing_output_symlink_is_rejected_without_touching_target(self) -> None:
        source = self._write("source/result.xml")
        target = self.project / "target-output"
        target.mkdir()
        sentinel = target / "sentinel"
        sentinel.write_text("keep", encoding="utf-8")
        self.output.parent.mkdir(parents=True)
        self.output.symlink_to(target, target_is_directory=True)
        with self.assertRaisesRegex(evidence.EvidenceError, "symlink"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source.parent),
            )
        self.assertEqual(sentinel.read_text(encoding="utf-8"), "keep")
        self.assertFalse(self.output.exists())

    def test_non_regular_source_root_is_rejected(self) -> None:
        source_file = self._write("source-file", b"not a directory")
        with self.assertRaisesRegex(evidence.EvidenceError, "must be a directory"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_file),
            )

    def test_failed_second_stage_removes_stale_successful_output(self) -> None:
        source_root = self.project / "source"
        source_root.mkdir()
        (source_root / "old.xml").write_bytes(b"old successful evidence")
        evidence.stage_evidence(
            self.project,
            self.output,
            self._single_source(source_root),
        )
        self.assertTrue(self.output.exists())
        outside = self.project / "outside"
        outside.mkdir()
        (outside / "secret").write_bytes(b"outside")
        (source_root / "blocked").symlink_to(outside, target_is_directory=True)
        with self.assertRaisesRegex(evidence.EvidenceError, "symlinks"):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source_root),
            )
        self.assertFalse(self.output.exists())

    def test_source_ancestor_swap_cannot_escape_held_directory(self) -> None:
        source_root = self.project / "source"
        nested = source_root / "dir"
        nested.mkdir(parents=True)
        (nested / "result.txt").write_bytes(b"EXPECTED-EVIDENCE")
        outside = self.project.parent / "outside-source"
        outside.mkdir()
        (outside / "result.txt").write_bytes(b"OUTSIDE-SECRET")
        original_copy = evidence._copy_and_hash
        swapped = False

        def swap_before_copy(item, staging_descriptor):
            nonlocal swapped
            if not swapped:
                nested.rename(source_root / "dir-original")
                nested.symlink_to(outside, target_is_directory=True)
                swapped = True
            return original_copy(item, staging_descriptor)

        evidence._copy_and_hash = swap_before_copy
        try:
            with self.assertRaises(evidence.EvidenceError):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source_root),
                )
        finally:
            evidence._copy_and_hash = original_copy
        self.assertTrue(swapped)
        self.assertFalse(self.output.exists())
        self.assertEqual((outside / "result.txt").read_bytes(), b"OUTSIDE-SECRET")

    def test_same_size_mtime_restored_mutation_is_rejected_by_ctime(self) -> None:
        source = self._write("source/result.txt", b"ORIGINAL")
        original_copy = evidence._copy_and_hash
        mutated = False

        def mutate_before_copy(item, staging_descriptor):
            nonlocal mutated
            if not mutated:
                before = source.stat()
                time.sleep(0.01)
                source.write_bytes(b"TAMPERED")
                os.utime(
                    source,
                    ns=(before.st_atime_ns, before.st_mtime_ns),
                )
                mutated = True
            return original_copy(item, staging_descriptor)

        evidence._copy_and_hash = mutate_before_copy
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "changed after planning"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence._copy_and_hash = original_copy
        self.assertTrue(mutated)
        self.assertFalse(self.output.exists())

    def test_source_tree_addition_during_copy_is_rejected(self) -> None:
        source = self._write("source/result.txt", b"stable")
        original_copy = evidence._copy_and_hash
        added = False

        def add_after_copy(item, staging_descriptor):
            nonlocal added
            result = original_copy(item, staging_descriptor)
            if not added:
                (source.parent / "late.txt").write_bytes(b"late")
                added = True
            return result

        evidence._copy_and_hash = add_after_copy
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "source changed|tree changed"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence._copy_and_hash = original_copy
        self.assertTrue(added)
        self.assertFalse(self.output.exists())

    def test_default_root_addition_during_copy_is_rejected(self) -> None:
        self._write(
            "build/reports/android-emulator-lifecycle/status.env",
            b"stable",
        )
        original_copy = evidence._copy_and_hash
        added = False

        def add_default_root_after_copy(item, staging_descriptor):
            nonlocal added
            result = original_copy(item, staging_descriptor)
            if not added:
                self._write(
                    "late/group/module/build/outputs/"
                    "androidTest-results/connected/late.xml",
                    b"late",
                )
                added = True
            return result

        evidence._copy_and_hash = add_default_root_after_copy
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "root set changed"):
                evidence.stage_evidence(self.project, self.output)
        finally:
            evidence._copy_and_hash = original_copy
        self.assertTrue(added)
        self.assertFalse(self.output.exists())

    def test_destination_parent_swap_cannot_delete_or_write_outside(self) -> None:
        source = self._write("source/result.txt", b"evidence")
        output_parent = self.output.parent
        output_parent.mkdir(parents=True)
        outside = self.project / "outside-output"
        outside.mkdir()
        sentinel = outside / "sentinel"
        sentinel.write_bytes(b"keep")
        original_create = evidence._create_temporary_directory
        swapped = False

        def swap_parent(parent_descriptor):
            nonlocal swapped
            if not swapped:
                output_parent.rename(self.project / "reports-original")
                output_parent.symlink_to(outside, target_is_directory=True)
                swapped = True
            return original_create(parent_descriptor)

        evidence._create_temporary_directory = swap_parent
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "path binding"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence._create_temporary_directory = original_create
        self.assertTrue(swapped)
        self.assertEqual(sentinel.read_bytes(), b"keep")
        self.assertFalse((outside / self.output.name).exists())

    def test_post_rename_parent_swap_cannot_report_success(self) -> None:
        source = self._write("source/result.txt", b"evidence")
        output_parent = self.output.parent
        output_parent.mkdir(parents=True)
        outside = self.project / "outside-after-rename"
        outside.mkdir()
        sentinel = outside / "sentinel"
        sentinel.write_bytes(b"keep")
        moved_parent = self.project / "reports-after-rename"
        original_rename = evidence.os.rename
        swapped = False

        def rename_then_swap(source_name, destination_name, **kwargs):
            nonlocal swapped
            result = original_rename(source_name, destination_name, **kwargs)
            if not swapped:
                original_rename(output_parent, moved_parent)
                output_parent.symlink_to(outside, target_is_directory=True)
                swapped = True
            return result

        evidence.os.rename = rename_then_swap
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "path binding"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence.os.rename = original_rename
        self.assertTrue(swapped)
        self.assertEqual(sentinel.read_bytes(), b"keep")
        self.assertFalse((outside / self.output.name).exists())
        self.assertFalse((moved_parent / self.output.name).exists())

    def test_post_rename_project_root_swap_cannot_report_success(self) -> None:
        source = self._write("source/result.txt", b"evidence")
        replacement = self.project.parent / "replacement-after-rename"
        replacement.mkdir()
        moved_project = self.project.parent / "project-after-rename"
        original_rename = evidence.os.rename
        swapped = False

        def rename_then_swap_project(source_name, destination_name, **kwargs):
            nonlocal swapped
            result = original_rename(source_name, destination_name, **kwargs)
            if not swapped:
                original_rename(self.project, moved_project)
                original_rename(replacement, self.project)
                swapped = True
            return result

        evidence.os.rename = rename_then_swap_project
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "project root"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence.os.rename = original_rename
        self.assertTrue(swapped)
        self.assertFalse((moved_project / evidence.OUTPUT_RELATIVE).exists())
        self.assertFalse((self.project / evidence.OUTPUT_RELATIVE).exists())

    def test_project_root_swap_before_first_open_is_rejected(self) -> None:
        source = self._write("source/result.txt", b"EXPECTED-EVIDENCE")
        replacement = self.project.parent / "replacement-project"
        replacement.mkdir()
        (replacement / "source").mkdir()
        (replacement / "source/result.txt").write_bytes(b"OUTSIDE-SECRET")
        original_open = evidence._open_absolute_directory_no_follow
        original_project = self.project.parent / "project-original"
        swapped = False

        def swap_before_open(path):
            nonlocal swapped
            if not swapped:
                self.project.rename(original_project)
                replacement.rename(self.project)
                swapped = True
            return original_open(path)

        evidence._open_absolute_directory_no_follow = swap_before_open
        try:
            with self.assertRaisesRegex(evidence.EvidenceError, "changed before descriptor"):
                evidence.stage_evidence(
                    self.project,
                    self.output,
                    self._single_source(source.parent),
                )
        finally:
            evidence._open_absolute_directory_no_follow = original_open
        self.assertTrue(swapped)
        self.assertFalse((self.project / evidence.OUTPUT_RELATIVE).exists())
        self.assertEqual(
            (self.project / "source/result.txt").read_bytes(),
            b"OUTSIDE-SECRET",
        )

    def test_project_root_symlink_is_rejected(self) -> None:
        source = self._write("source/result.txt", b"evidence")
        alias = self.project.parent / "project-alias"
        alias.symlink_to(self.project, target_is_directory=True)
        with self.assertRaisesRegex(evidence.EvidenceError, "project root must not be a symlink"):
            evidence.stage_evidence(
                alias,
                alias / evidence.OUTPUT_RELATIVE,
                [
                    evidence.SourceRoot(
                        alias / source.relative_to(self.project),
                        ("fixture",),
                    )
                ],
            )

    def test_output_parent_symlink_is_rejected_without_touching_target(self) -> None:
        source = self._write("source/result.txt", b"evidence")
        outside = self.project / "outside-parent"
        outside.mkdir()
        sentinel = outside / "sentinel"
        sentinel.write_bytes(b"keep")
        build = self.project / "build"
        build.symlink_to(outside, target_is_directory=True)
        with self.assertRaises(evidence.EvidenceError):
            evidence.stage_evidence(
                self.project,
                self.output,
                self._single_source(source.parent),
            )
        self.assertEqual(sentinel.read_bytes(), b"keep")


if __name__ == "__main__":
    unittest.main(verbosity=2)
