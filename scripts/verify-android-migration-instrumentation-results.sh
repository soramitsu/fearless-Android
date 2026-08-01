#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${MIGRATION_RESULTS_ROOT:-$(cd "$(dirname "$0")/.." && pwd -P)}"

python3 - "$ROOT_DIR" <<'PY'
from __future__ import annotations

import os
import stat
import sys
import time
import xml.etree.ElementTree as ET
from collections import Counter
from pathlib import Path


class VerificationError(RuntimeError):
    pass


root = Path(sys.argv[1]).resolve()
profile = os.environ.get("MIGRATION_RESULTS_PROFILE", "full")
matrix_class = (
    "jp.co.soramitsu.coredb.migrations.ReleasedSchemaUpgradeMatrixTest"
)
orphan_class = (
    "jp.co.soramitsu.coredb.migrations.ReleasedVersion27FailClosedMigrationTest"
)
keystore_class = (
    "jp.co.soramitsu.common.data.storage.encrypt."
    "EncryptionUtilAndroidKeyStoreTest"
)
gate_class = (
    "jp.co.soramitsu.app.root.presentation.WalletGateActivityLifecycleTest"
)
restart_class = (
    "jp.co.soramitsu.app.root.presentation."
    "WalletSecureStorageRestartActivityLifecycleTest"
)
security_warning_class = (
    "jp.co.soramitsu.app.root.presentation."
    "SecurityWarningRestorationTest"
)
matrix_identities = {
    (
        matrix_class,
        "exactReleasedSchemaUpgradesTo77WithoutLosingWalletState"
        f"[released schema {version} -> 77]",
    )
    for version in (26, 27, 28, 73, 74, 75, 76)
}
orphan_identity = (
    orphan_class,
    "orphanChainAccountRejectsFullUpgradeWithoutWipeOrSecretMutation",
)
full_contracts = {
    "common": {
        "minimum": 5,
        "required": {
            (
                keystore_class,
                "transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree",
            ),
        },
    },
    "core-db": {
        # The API-34 full profile is deliberately a single connected shard.
        # Requiring the historical full-suite floor prevents a filtered
        # migration subset from satisfying the critical-identity checks.
        "minimum": 290,
        "required": {
            *matrix_identities,
            orphan_identity,
        },
    },
    "app": {
        "minimum": 41,
        "required": {
            (
                gate_class,
                "legacyGateRelaunchDiscardsNonRestorableFragmentState",
            ),
            (
                gate_class,
                "permanentRecoveryFailureShowsDiagnosticInsteadOfRetryOnlyRoute",
            ),
            (
                restart_class,
                "latchWhileStoppedDefersRedirectUntilForeground",
            ),
            (
                security_warning_class,
                "restoredCancelButtonEmitsOnceAndExitsThroughResultListener",
            ),
            (
                security_warning_class,
                "restoredSystemCancelEmitsOnceAndExitsThroughResultListener",
            ),
            (
                security_warning_class,
                "restoredSystemBackEmitsOnceAndExitsThroughResultListener",
            ),
        },
    },
    "feature-account-impl": {
        "minimum": 4,
        "required": {
            (
                "jp.co.soramitsu.account.api.domain.interfaces."
                "SignWithAccountCryptoRoutingTest",
                "substrateEcdsaChildSignsWithBoundChildKeyWithoutReadingRootSecrets",
            ),
        },
    },
}

compatibility_contracts = {
    "common": {
        "exact": 5,
        "class_counts": {keystore_class: 5},
        "required": {
            (keystore_class, "missingWrappedKeyNeverCreatesReplacementWhileProtectedDataRemains"),
            (keystore_class, "freshInstallPersistsRecoversAndAuthenticatesExactAesKey"),
            (keystore_class, "missingKeystoreAliasNeverCreatesReplacementForExistingWrappedKey"),
            (keystore_class, "hostileWrappedKeyRepresentationsFailPermanentlyWithoutMutation"),
            (keystore_class, "transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree"),
        },
    },
    "core-db": {
        "exact": 8,
        "class_counts": {matrix_class: 7, orphan_class: 1},
        "required": {*matrix_identities, orphan_identity},
    },
    "app": {
        "exact": 26,
        "class_counts": {
            gate_class: 20,
            restart_class: 3,
            security_warning_class: 3,
        },
        "required": {
            (gate_class, "legacyGateRelaunchDiscardsNonRestorableFragmentState"),
            (gate_class, "permanentRecoveryFailureShowsDiagnosticInsteadOfRetryOnlyRoute"),
            (restart_class, "latchWhileStoppedDefersRedirectUntilForeground"),
            (
                security_warning_class,
                "restoredCancelButtonEmitsOnceAndExitsThroughResultListener",
            ),
            (
                security_warning_class,
                "restoredSystemCancelEmitsOnceAndExitsThroughResultListener",
            ),
            (
                security_warning_class,
                "restoredSystemBackEmitsOnceAndExitsThroughResultListener",
            ),
        },
    },
}


def fail(message: str) -> None:
    raise VerificationError(message)


def exact_nonnegative_int(element: ET.Element, attribute: str) -> int:
    value = element.attrib.get(attribute)
    if value is None or not value.isascii() or not value.isdigit():
        fail(f"testsuite has invalid {attribute!r} count")
    return int(value)


try:
    if profile == "full":
        contracts = full_contracts
        required_total = 340
        exact_total = False
    elif profile == "compatibility":
        contracts = compatibility_contracts
        required_total = 39
        exact_total = True
    else:
        fail(f"unsupported result profile: {profile}")

    marker_file = (
        root
        / "build"
        / "reports"
        / "android-migration-results"
        / f"{profile}-start.marker"
    )
    try:
        marker_metadata = marker_file.lstat()
        resolved_marker = marker_file.resolve(strict=True)
    except OSError as error:
        fail(f"{profile} result-start marker is missing or unsafe: {error}")
    if (
        resolved_marker != marker_file
        or not stat.S_ISREG(marker_metadata.st_mode)
        or marker_file.is_symlink()
        or marker_metadata.st_nlink != 1
        or marker_metadata.st_size != 0
    ):
        fail(f"{profile} result-start marker is missing or unsafe")
    now_ns = time.time_ns()
    if marker_metadata.st_mtime_ns > now_ns + 5 * 60 * 1_000_000_000:
        fail(f"{profile} result-start marker has an unsafe future timestamp")
    if now_ns - marker_metadata.st_mtime_ns > 2 * 60 * 60 * 1_000_000_000:
        fail(f"{profile} result-start marker is stale")

    observed_total = 0
    for module, contract in contracts.items():
        result_dir = (
            root
            / module
            / "build"
            / "outputs"
            / "androidTest-results"
            / "connected"
            / "debug"
        )
        if (
            not result_dir.is_dir()
            or result_dir.is_symlink()
            or result_dir.resolve() != result_dir
        ):
            fail(f"{module} connected-result directory is missing or unsafe")
        result_files = sorted(result_dir.glob("TEST-*.xml"))
        if len(result_files) != 1:
            fail(f"{module} must have exactly one connected-result XML")
        result_file = result_files[0]
        metadata = result_file.lstat()
        if (
            not stat.S_ISREG(metadata.st_mode)
            or result_file.is_symlink()
            or result_file.resolve() != result_file
            or metadata.st_nlink != 1
            or metadata.st_size <= 0
            or metadata.st_size > 8 * 1024 * 1024
        ):
            fail(f"{module} connected-result XML is empty, oversized, or unsafe")
        if metadata.st_mtime_ns < marker_metadata.st_mtime_ns:
            fail(f"{module} connected-result XML predates the current test run")
        try:
            suite = ET.parse(result_file).getroot()
        except (ET.ParseError, OSError) as error:
            fail(f"{module} connected-result XML cannot be parsed: {error}")
        if suite.tag != "testsuite":
            fail(f"{module} connected-result root must be testsuite")

        declared_tests = exact_nonnegative_int(suite, "tests")
        failures = exact_nonnegative_int(suite, "failures")
        errors = exact_nonnegative_int(suite, "errors")
        skipped = exact_nonnegative_int(suite, "skipped")
        testcases = list(suite.findall("testcase"))
        if declared_tests != len(testcases):
            fail(f"{module} declared test count does not match testcase records")
        if "exact" in contract and declared_tests != int(contract["exact"]):
            fail(
                f"{module} ran {declared_tests} tests; "
                f"exact compatibility count is {contract['exact']}"
            )
        if "minimum" in contract and declared_tests < int(contract["minimum"]):
            fail(
                f"{module} ran {declared_tests} tests; "
                f"minimum is {contract['minimum']}"
            )
        if failures != 0 or errors != 0 or skipped != 0:
            fail(
                f"{module} results are not fully green "
                f"(failures={failures}, errors={errors}, skipped={skipped})"
            )
        if suite.findall(".//failure") or suite.findall(".//error"):
            fail(f"{module} result XML contains failure/error records")
        if suite.findall(".//skipped"):
            fail(f"{module} result XML contains skipped-test records")

        identities: list[tuple[str, str]] = []
        for testcase in testcases:
            classname = testcase.attrib.get("classname", "")
            name = testcase.attrib.get("name", "")
            if not classname or not name:
                fail(f"{module} contains a testcase without class/name identity")
            identities.append((classname, name))
        identity_set = set(identities)
        if len(identity_set) != len(identities):
            fail(f"{module} contains duplicate testcase identities")
        expected_class_counts = contract.get("class_counts")
        if expected_class_counts is not None:
            observed_class_counts = Counter(class_name for class_name, _ in identities)
            if observed_class_counts != expected_class_counts:
                fail(f"{module} compatibility class/test counts changed")
        missing = sorted(contract["required"] - identity_set)
        if missing:
            rendered = ", ".join(f"{class_name}#{name}" for class_name, name in missing)
            fail(f"{module} omitted required migration/startup tests: {rendered}")
        observed_total += declared_tests

    if exact_total and observed_total != required_total:
        fail(
            f"compatibility migration/startup gate ran {observed_total} tests; "
            f"expected exactly {required_total}"
        )
    if not exact_total and observed_total < required_total:
        fail(f"full migration/startup gate ran only {observed_total} tests")
except VerificationError as error:
    print(f"[android-migration-results][error] {error}", file=sys.stderr)
    raise SystemExit(1)

print(
    "[android-migration-results] "
    f"{profile} profile: {observed_total} tests passed with zero failures/errors/skips; "
    "critical migration/startup identities verified"
)
PY
