#!/usr/bin/env python3
"""Owned-emulator app profiles; historical device qualifications are explicitly separate."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MANIFEST = ROOT / "config/android-app-instrumentation-profiles.json"
EVIDENCE = ROOT / "build/reports/android-app-profiles"
RESULTS = ROOT / "app/build/outputs/androidTest-results/connected/debug"
PACKAGE = "jp.co.soramitsu.fearless.debug"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"


def checked(command, *, timeout=600, capture=False):
    return subprocess.run(command, cwd=ROOT, check=True, timeout=timeout,
                          text=True, stdout=subprocess.PIPE if capture else None,
                          stderr=subprocess.PIPE if capture else None).stdout


def adb(*arguments):
    serial = os.environ.get("ANDROID_SERIAL", "")
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise ValueError("An explicitly selected disposable emulator is required")
    executable = Path(os.environ.get("ANDROID_HOME", os.environ.get("ANDROID_SDK_ROOT", ""))) / "platform-tools/adb"
    return [str(executable), "-s", serial, *arguments]


def shell(*arguments):
    return checked(adb("shell", *arguments), capture=True).strip()


def manifest():
    value = json.loads(MANIFEST.read_text())
    if value["version"] != 1:
        raise ValueError("Unknown app profile contract")
    expected_process = {"jp.co.soramitsu.app.root.domain.staking.PoolJoinJournalProcessInstrumentedTest",
                        "jp.co.soramitsu.app.root.domain.staking.PoolCreateJournalProcessInstrumentedTest"}
    if set(value["processClasses"]) != expected_process or len(value["processClasses"]) != 2:
        raise ValueError("Both journal process regressions are mandatory")
    if len(value["manualQualification"]) != 12 or len(value["externalValidatorQualification"]) != 1:
        raise ValueError("Reviewed historical/external qualification inventory changed")
    for group in ("manualQualification", "externalValidatorQualification"):
        if not value[group] or any(not row["reason"] for row in value[group]):
            raise ValueError("Every non-CI qualification needs an explicit reason")
    return value


def exclusions(value):
    identities = [row["class"] for group in ("manualQualification", "externalValidatorQualification") for row in value[group]]
    identities += value["processClasses"] + value["v1Only"]
    if len(identities) != len(set(identities)) or any(not re.fullmatch(r"jp\.co\.soramitsu\.app\.[A-Za-z0-9_.]+(?:#[A-Za-z0-9_]+)?", x) for x in identities):
        raise ValueError("Invalid or duplicate explicit qualification identity")
    return identities


def owned_device():
    if os.environ.get("ANDROID_APP_OWNED_DEVICE") != "exclusively-owned-disposable-emulator":
        raise ValueError("The lifecycle owner must explicitly admit its disposable emulator")
    serial = os.environ["ANDROID_SERIAL"]
    avd = shell("getprop", "ro.boot.qemu.avd_name")
    if shell("getprop", "ro.build.version.sdk") != "34" or shell("getprop", "ro.hardware") not in ("ranchu", "goldfish"):
        raise ValueError("Full app profiles require the owned API34 emulator")
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", avd):
        raise ValueError("Unsafe or absent actual AVD identity")
    return {"serial": serial, "avd": avd, "bootId": shell("cat", "/proc/sys/kernel/random/boot_id")}


def verify_device(expected, observed, *, require_uid=False):
    keys = ("serial", "avd", "bootId", "uid") if require_uid else ("serial", "avd", "bootId")
    if any(not expected.get(key) or expected[key] != observed.get(key) for key in keys):
        raise ValueError("The admitted disposable device identity changed")


def device_identity():
    identity = owned_device()
    uid = shell("run-as", PACKAGE, "id", "-u")
    if not uid.isascii() or not uid.isdigit():
        raise ValueError("Installed debug app UID could not be bound")
    return {**identity, "uid": uid}


def source_identity():
    paths = checked(["git", "ls-files", "--cached", "--others", "--exclude-standard", "-z"], capture=True).split("\0")
    digest = hashlib.sha256()
    count = 0
    for name in sorted(set(paths) - {""}):
        path = ROOT / name
        data = os.readlink(path).encode() if path.is_symlink() else path.read_bytes() if path.is_file() else b"<deleted>"
        digest.update(name.encode() + b"\0" + hashlib.sha256(data).digest())
        count += 1
    return {"commit": checked(["git", "rev-parse", "HEAD"], capture=True).strip(),
            "contentSha256": digest.hexdigest(), "sourceFiles": count,
            "dirty": bool(checked(["git", "status", "--porcelain"], capture=True).strip())}


def apk_identity():
    paths = ("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
    return {path: hashlib.sha256((ROOT / path).read_bytes()).hexdigest() for path in paths}


def require_same_source(expected, observed):
    if expected != observed:
        raise ValueError("Source identity changed between app qualification phases")


def require_active_uid():
    expected = json.loads((EVIDENCE / "process/identity.json").read_text())
    verify_device(expected, device_identity(), require_uid=True)


def gradle(*arguments):
    checked(["./gradlew", *arguments, "--no-daemon", "--console=plain", "--stacktrace"], timeout=1800)


def prepare():
    value = manifest()
    exclusions(value)
    admitted = owned_device()
    source_before = source_identity()
    if EVIDENCE.exists():
        shutil.rmtree(EVIDENCE)
    EVIDENCE.mkdir(parents=True)
    gradle(":app:installDebug", ":app:installDebugAndroidTest")
    identity = device_identity()
    verify_device(admitted, identity)
    require_same_source(source_before, source_identity())
    (EVIDENCE / "legacy-artifact.json").write_text(json.dumps({"source": source_before, "apks": apk_identity()}, indent=2) + "\n")
    nonce = secrets.token_hex(16)
    source = checked(["git", "rev-parse", "HEAD"], capture=True).strip()
    arguments = {
        "notClass": ",".join(exclusions(value)),
        "loadingExpectStill": "false",  # The production legacy rollback artifact animates.
        "fearlessArchiveGateOwnedUid": "exclusively-owned-disposable-app-uid",
        "fearlessArchiveGateOwnedSerial": identity["serial"],
        "fearlessArchiveGateOwnedAvd": identity["avd"],
        "fearlessArchiveGateUid": identity["uid"],
        "fearlessLargeCacheGateOwnedUid": "exclusively-owned-disposable-app-uid",
        "fearlessLargeCacheGateOwnedSerial": identity["serial"],
        "fearlessRuntimeCacheOwnedUid": "exclusively-owned-disposable-app-uid",
        "fearlessRuntimeCacheOwnedSerial": identity["serial"],
        "fearlessRuntimeCacheRunNonce": nonce,
        "fearlessEmergencyPagingOwnedUid": "exclusively-owned-fresh-synthetic-app-uid",
        "fearlessEmergencyPagingSerial": identity["serial"],
        "fearlessEmergencyPagingAvd": identity["avd"],
        "fearlessEmergencyPagingUid": identity["uid"],
        "fearlessEmergencyPagingNonce": nonce,
        "fearlessEmergencyPagingSource": source,
        "allowPublicAlertAdapter": "audit-public-offline-v1",
        "alertAdapterEvidence": nonce,
    }
    # This emulator is created by the CI lifecycle owner. Adapter tests require no active network.
    identity["wifi"] = shell("settings", "get", "global", "wifi_on")
    identity["mobileData"] = shell("settings", "get", "global", "mobile_data")
    (EVIDENCE / "identity.json").write_text(json.dumps(identity, indent=2) + "\n")
    shell("svc", "wifi", "disable")
    shell("svc", "data", "disable")
    (EVIDENCE / "arguments.txt").write_text("".join(f"-Pandroid.testInstrumentationRunnerArguments.{k}={v}\n" for k, v in arguments.items()))
    (EVIDENCE / "qualification-limits.json").write_text(json.dumps(value, indent=2) + "\n")


def require_one_test(output, classname, method):
    if not re.search(r"OK \(1 test\)", output) or "INSTRUMENTATION_CODE: -1" not in output:
        raise ValueError(f"Instrumentation did not pass exactly one test: {classname}#{method}")
    identity_lines = (f"INSTRUMENTATION_STATUS: class={classname}", f"INSTRUMENTATION_STATUS: test={method}")
    if any(line not in output.splitlines() for line in identity_lines) or "FAILURES!!!" in output or "INSTRUMENTATION_FAILED" in output:
        raise ValueError("Instrumentation identity/failure mismatch")


def instrument(classname, method):
    require_active_uid()
    shell("am", "force-stop", PACKAGE)
    output = checked(adb("shell", "am", "instrument", "-w", "-r", "-e", "class", f"{classname}#{method}", RUNNER), timeout=180, capture=True)
    (EVIDENCE / "process" / f"{classname.rsplit('.', 1)[-1]}-{method}.txt").write_text(output)
    require_one_test(output, classname, method)


def read_proof(directory, phase):
    output = checked(adb("exec-out", "run-as", PACKAGE, "cat", f"cache/{directory}/{phase}.json"), capture=True)
    proof = json.loads(output)
    if proof["phase"] != phase or proof.get("fixtureOnly") is not True or proof.get("networkRequests") != 0:
        raise ValueError("Unexpected process proof source")
    return proof


def verify_pair(before, after, *, archived=False):
    if any(type(value[key]) is not int or value[key] <= 0 for value in (before, after) for key in ("pid", "writerPid")):
        raise ValueError("Invalid process identity")
    if before["pid"] != before["writerPid"] or after["writerPid"] != before["pid"] or after["pid"] == before["pid"]:
        raise ValueError("Fresh reader process was not established")
    for key in ("recordSha256", "transactionHash"):
        if not re.fullmatch(r"(?:0x)?[0-9a-f]{64}", before[key]) or before[key] != after[key]:
            raise ValueError("Journal bytes changed across process restart")
    if archived and (not before["pendingAbsent"] or not after["pendingAbsent"] or before["attemptId"] != after["attemptId"]):
        raise ValueError("Finalized journal ownership was not preserved")


def process_profiles(value):
    active = device_identity()
    verify_device(json.loads((EVIDENCE / "identity.json").read_text()), active)
    (EVIDENCE / "process").mkdir()
    (EVIDENCE / "process/identity.json").write_text(json.dumps(active, indent=2) + "\n")
    pairs = []
    for classname in value["processClasses"]:
        require_active_uid()
        kind = "join" if "PoolJoin" in classname else "create"
        directory = f"pool-{kind}-process-evidence-v1"
        # Only these test-owned output directories are replaced. Wallet preferences are never cleared.
        shell("run-as", PACKAGE, "rm", "-rf", f"cache/{directory}")
        method = "holdAfterAdmittedLocalOutputForHostProcessKill"
        shell("am", "force-stop", PACKAGE)
        log_path = EVIDENCE / "process" / f"{kind}-held-writer.txt"
        with log_path.open("w") as output:
            process = subprocess.Popen(adb("shell", "am", "instrument", "-w", "-r", "-e", "class", f"{classname}#{method}", RUNNER), cwd=ROOT, stdout=output, stderr=subprocess.STDOUT, text=True)
            try:
                deadline = time.monotonic() + 60
                before = None
                while time.monotonic() < deadline and process.poll() is None:
                    try:
                        before = read_proof(directory, "admitted-before-kill")
                        break
                    except (subprocess.CalledProcessError, json.JSONDecodeError):
                        time.sleep(0.2)
                if before is None or str(before["pid"]) not in shell("pidof", PACKAGE).split():
                    raise ValueError("Held writer did not produce live admission evidence")
                require_active_uid()
                shell("run-as", PACKAGE, "kill", "-9", str(before["pid"]))
                process.wait(timeout=30)
            finally:
                if process.poll() is None:
                    shell("am", "force-stop", PACKAGE)
                    process.wait(timeout=30)
        if "Host did not kill" in log_path.read_text():
            raise ValueError("Writer timed out instead of being killed")
        instrument(classname, "readUncertainPaymentAfterProcessKill")
        after = read_proof(directory, "pending-after-process-kill")
        verify_pair(before, after)
        pairs.append({"class": classname, "transition": "killed", "before": before, "after": after})
        transitions = [
            ("prepareAcknowledgedLocalPayment", "readAcknowledgedPaymentAfterRestart", "acknowledged-before-restart", "acknowledged-after-restart", False),
            ("archiveExactOriginalAndReleasePendingScope", "readArchivedOriginalAfterRestartAndKeepItDuringNextAttempt", "finalized-before-restart", "finalized-after-restart", True),
        ]
        if kind == "join":
            transitions.append(("prepareHistoricalUnanchoredAcknowledgement", "readHistoricalUnanchoredAcknowledgementAfterUpgrade", "historical-unanchored-before-restart", "historical-unanchored-after-upgrade", False))
        for writer, reader, before_phase, after_phase, archived in transitions:
            instrument(classname, writer)
            before = read_proof(directory, before_phase)
            instrument(classname, reader)
            after = read_proof(directory, after_phase)
            verify_pair(before, after, archived=archived)
            pairs.append({"class": classname, "transition": before_phase, "before": before, "after": after})
    (EVIDENCE / "process/transitions.json").write_text(json.dumps(pairs, indent=2) + "\n")
    if len(pairs) != 7:
        raise ValueError("All seven process transitions are mandatory")


def verify_v1(directory):
    files = list(directory.glob("TEST-*.xml"))
    if len(files) != 1:
        raise ValueError("Exactly one fresh v1 XML required")
    suite = ET.parse(files[0]).getroot()
    if suite.tag != "testsuite" or suite.get("tests") != "5" or any(suite.get(k) != "0" for k in ("failures", "errors", "skipped")):
        raise ValueError("All five v1 UI regressions must pass")
    expected = {
        ("WalletRecoveryFocusInstrumentedTest", "bannerHasVisibleKeyboardFocusWithOutsideGap"),
        ("WalletRecoveryFocusInstrumentedTest", "backHasVisibleKeyboardFocusWithOutsideGap"),
        ("WalletRecoveryAppearanceInstrumentedTest", "recoveryButtonsKeepFocusPressAndActionWithoutDecorativeEffects"),
        ("WalletLoadingMotionInstrumentedTest", "reducedMotionKeepsBothSkeletonShapesAndProgressStill"),
        ("WalletLoadingMotionInstrumentedTest", "ordinaryMotionKeepsActualLoadingAnimation"),
    }
    cases = suite.findall("testcase")
    expected = {("jp.co.soramitsu.app.root.presentation.main." + c, m) for c, m in expected}
    actual = {(c.attrib["classname"], c.attrib["name"]) for c in cases}
    if actual != expected or len(cases) != 5 or any(suite.findall(".//" + t) for t in ("failure", "error", "skipped")):
        raise ValueError("v1 test identities or outcomes changed")


def qualify():
    value = manifest()
    original = json.loads((EVIDENCE / "identity.json").read_text())
    verify_device(original, owned_device())
    source_before = source_identity()
    legacy = json.loads((EVIDENCE / "legacy-artifact.json").read_text())
    require_same_source(legacy["source"], source_before)
    ordinary = EVIDENCE / "ordinary"
    shutil.copytree(RESULTS, ordinary)
    v1_started = False
    try:
        gradle(":app:installDebug", ":app:installDebugAndroidTest")
        current = device_identity()
        verify_device(original, current)
        require_same_source(source_before, source_identity())
        if apk_identity() != legacy["apks"]:
            raise ValueError("Legacy APK bytes changed between ordinary and process profiles")
        (EVIDENCE / "process-artifact.json").write_text(json.dumps({"source": source_before, "apks": apk_identity()}, indent=2) + "\n")
        process_profiles(value)
        shutil.rmtree(RESULTS)
        v1_started = True
        gradle(":app:connectedDebugAndroidTest", "-PFW_PALETTE=v1", "-PFW_RESTYLE_RECOVERY=true", "-PFW_RESTYLE_LOADING=true",
               "-Pandroid.testInstrumentationRunnerArguments.class=" + ",".join(value["v1Qualification"]))
        shutil.copytree(RESULTS, EVIDENCE / "v1")
        verify_v1(EVIDENCE / "v1")
        require_same_source(source_before, source_identity())
        (EVIDENCE / "v1-artifact.json").write_text(json.dumps({"source": source_before, "apks": apk_identity()}, indent=2) + "\n")
        (EVIDENCE / "complete.json").write_text(json.dumps({"version": 1, "sourceCommit": source_before["commit"], "source": source_before,
            "manifestSha256": hashlib.sha256(MANIFEST.read_bytes()).hexdigest(), "v1Tests": 5, "processTransitions": 7,
            "manualQualificationClaimed": False, "externalValidatorQualificationClaimed": False}, indent=2) + "\n")
    finally:
        if v1_started and RESULTS.exists() and not (EVIDENCE / "v1").exists():
            shutil.copytree(RESULTS, EVIDENCE / "v1")
        if RESULTS.exists():
            shutil.rmtree(RESULTS)
        shutil.copytree(ordinary, RESULTS)


def restore():
    current = owned_device()
    identity_file = EVIDENCE / "identity.json"
    if identity_file.exists():
        identity = json.loads(identity_file.read_text())
        verify_device(identity, current)
        for key, service in (("wifi", "wifi"), ("mobileData", "data")):
            shell("svc", service, "enable" if identity[key] == "1" else "disable")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("phase", choices=("prepare", "qualify", "restore"))
    phase = parser.parse_args().phase
    {"prepare": prepare, "qualify": qualify, "restore": restore}[phase]()
