#!/usr/bin/env python3
"""Adversarial checks for profile boundaries and actual process/UI evidence."""
import copy
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("profiles", Path(__file__).with_name("android-app-instrumentation.py"))
profiles = importlib.util.module_from_spec(spec)
spec.loader.exec_module(profiles)


class ProfileTests(unittest.TestCase):
    def test_cross_phase_source_drift_is_rejected_before_build_or_result_mutation(self):
        original = {"commit": "a" * 40, "contentSha256": "b" * 64, "sourceFiles": 5, "dirty": False}
        device = {"serial": "emulator-5750", "avd": "owned", "bootId": "one-boot", "uid": "10042"}
        with tempfile.TemporaryDirectory() as directory, patch.object(profiles, "EVIDENCE", Path(directory)):
            evidence = Path(directory)
            (evidence / "identity.json").write_text(json.dumps(device))
            (evidence / "legacy-artifact.json").write_text(json.dumps({"source": original, "apks": {}}))
            for field, value in (("commit", "c" * 40), ("contentSha256", "d" * 64), ("dirty", True)):
                with patch.object(profiles, "owned_device", return_value=device), \
                     patch.object(profiles, "source_identity", return_value={**original, field: value}), \
                     patch.object(profiles, "gradle") as build:
                    with self.assertRaises(ValueError):
                        profiles.qualify()
                    build.assert_not_called()
                    self.assertFalse((evidence / "ordinary").exists())

    def test_ownership_is_required_before_device_commands(self):
        with patch.dict(profiles.os.environ, {}, clear=True), patch.object(profiles, "shell") as shell:
            for operation in (profiles.owned_device, profiles.restore):
                with self.assertRaises(ValueError):
                    operation()
            shell.assert_not_called()

    def test_device_and_fresh_uid_bindings_cannot_drift(self):
        original = {"serial": "emulator-5750", "avd": "owned", "bootId": "one-boot", "uid": "10042"}
        profiles.verify_device(original, original, require_uid=True)
        # AGP may reinstall the debug app; a new phase binds its fresh UID after installation.
        profiles.verify_device(original, {**original, "uid": "10043"})
        for key in original:
            with self.assertRaises(ValueError):
                profiles.verify_device(original, {**original, key: "changed"}, require_uid=True)

    def test_source_identity_covers_untracked_source_and_exact_content(self):
        with tempfile.TemporaryDirectory() as directory, patch.object(profiles, "ROOT", Path(directory)):
            (Path(directory) / "tracked.kt").write_text("one")
            (Path(directory) / "untracked.py").write_text("two")
            def git(command, **kwargs):
                if command[1] == "ls-files":
                    self.assertIn("--others", command)
                    return "tracked.kt\0untracked.py\0"
                return "1" * 40 if command[1] == "rev-parse" else "?? untracked.py\n"
            with patch.object(profiles, "checked", side_effect=git):
                before = profiles.source_identity()
                (Path(directory) / "untracked.py").write_text("changed")
                after = profiles.source_identity()
                self.assertTrue(before["dirty"])
                self.assertEqual(2, before["sourceFiles"])
                self.assertNotEqual(before["contentSha256"], after["contentSha256"])

    def test_reviewed_inventory_keeps_manual_and_external_qualification_distinct(self):
        value = profiles.manifest()
        self.assertEqual(12, len(value["manualQualification"]))
        self.assertEqual(["jp.co.soramitsu.app.root.domain.send.NativePrivateChainSendInstrumentedTest"],
                         [row["class"] for row in value["externalValidatorQualification"]])
        excluded = profiles.exclusions(value)
        self.assertEqual(17, len(excluded))
        for identity in excluded:
            classname, _, method = identity.partition("#")
            source = profiles.ROOT / "app/src/androidTest/java" / (classname.replace(".", "/") + ".kt")
            self.assertTrue(source.is_file(), classname)
            if method:
                self.assertIn("fun " + method + "(", source.read_text())
        self.assertNotIn("jp.co.soramitsu.app.root.domain.WalletRuntimeCatalogInstrumentedTest", excluded)
        self.assertNotIn("jp.co.soramitsu.app.root.presentation.main.StakingAlertAdapterAuthorityInstrumentedTest", excluded)

    def test_exclusions_reject_injection_and_duplicate_identity(self):
        for invalid in ("x; rm -rf /", "jp.co.soramitsu.app.Other#method\n-e package x"):
            value = profiles.manifest()
            value["manualQualification"][0]["class"] = invalid
            with self.assertRaises(ValueError):
                profiles.exclusions(value)
        value = profiles.manifest()
        value["manualQualification"][0]["class"] = value["manualQualification"][1]["class"]
        with self.assertRaises(ValueError):
            profiles.exclusions(value)

    def test_instrumentation_requires_exact_success_identity_and_one_test(self):
        good = "INSTRUMENTATION_STATUS: class=a.b.Test\nINSTRUMENTATION_STATUS: test=read\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n"
        profiles.require_one_test(good, "a.b.Test", "read")
        for invalid in (good.replace("1 test", "2 tests"), good.replace("class=a.b.Test", "class=other.Test"),
                        good.replace("class=a.b.Test", "class=a.b.TestOther"), good.replace("test=read", "test=readOther"),
                        good.replace("test=read", "test=write"), good.replace("CODE: -1", "CODE: 0"), good + "FAILURES!!!"):
            with self.assertRaises(ValueError):
                profiles.require_one_test(invalid, "a.b.Test", "read")

    def test_process_pair_requires_fresh_reader_and_identical_retained_bytes(self):
        before = {"pid": 100, "writerPid": 100, "recordSha256": "a" * 64, "transactionHash": "0x" + "b" * 64,
                  "pendingAbsent": True, "attemptId": "public-fixture"}
        after = {**before, "pid": 101}
        profiles.verify_pair(before, after, archived=True)
        for change in ({"pid": 100}, {"pid": 0}, {"pid": True}, {"writerPid": 101}, {"recordSha256": "c" * 64},
                       {"transactionHash": "0x" + "c" * 64}, {"pendingAbsent": False}, {"attemptId": "different"}):
            with self.assertRaises(ValueError):
                profiles.verify_pair(before, {**after, **change}, archived=True)

    def test_v1_requires_five_exact_green_identities(self):
        names = {
            "WalletRecoveryFocusInstrumentedTest": ["bannerHasVisibleKeyboardFocusWithOutsideGap", "backHasVisibleKeyboardFocusWithOutsideGap"],
            "WalletRecoveryAppearanceInstrumentedTest": ["recoveryButtonsKeepFocusPressAndActionWithoutDecorativeEffects"],
            "WalletLoadingMotionInstrumentedTest": ["reducedMotionKeepsBothSkeletonShapesAndProgressStill", "ordinaryMotionKeepsActualLoadingAnimation"],
        }
        suite = ET.Element("testsuite", tests="5", failures="0", errors="0", skipped="0")
        for classname, methods in names.items():
            for method in methods:
                ET.SubElement(suite, "testcase", classname="jp.co.soramitsu.app.root.presentation.main." + classname, name=method)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "TEST-v1.xml"
            ET.ElementTree(suite).write(path)
            profiles.verify_v1(Path(directory))
            for mutation in ("skip", "failure", "wrong-package", "duplicate", "missing"):
                changed = copy.deepcopy(suite)
                if mutation == "skip":
                    ET.SubElement(changed[0], "skipped")
                elif mutation == "failure":
                    ET.SubElement(changed[0], "failure")
                elif mutation == "wrong-package":
                    changed[0].set("classname", "other." + names.keys().__iter__().__next__())
                elif mutation == "duplicate":
                    changed[1].attrib.update(changed[0].attrib)
                else:
                    changed.remove(changed[0])
                ET.ElementTree(changed).write(path)
                with self.assertRaises(ValueError, msg=mutation):
                    profiles.verify_v1(Path(directory))


if __name__ == "__main__":
    unittest.main()
