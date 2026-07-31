#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-migration-instrumentation-results.sh"
EXPECTED_POSITIVE_COUNT=2
EXPECTED_NEGATIVE_COUNT=33

tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$tmp_root"
tmp_dir="$(mktemp -d "$tmp_root/android-migration-results-test.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-migration-results-test][error] $*" >&2
  exit 1
}

positive_count=0
negative_count=0

make_fixture() {
  local name="$1"
  local fixture="$tmp_dir/$name"
  mkdir -p "$fixture"
  FIXTURE_ROOT="$fixture" python3 <<'PY'
import os
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(os.environ["FIXTURE_ROOT"])
marker = (
    root / "build" / "reports" / "android-migration-results"
    / "full-start.marker"
)
marker.parent.mkdir(parents=True)
marker.touch()
matrix_class = (
    "jp.co.soramitsu.coredb.migrations.ReleasedSchemaUpgradeMatrixTest"
)
contracts = {
    "common": (
        5,
        [(
            "jp.co.soramitsu.common.data.storage.encrypt."
            "EncryptionUtilAndroidKeyStoreTest",
            "transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree",
        )],
    ),
    "core-db": (
        61,
        [
            *[
                (
                    matrix_class,
                    "exactReleasedSchemaUpgradesTo77WithoutLosingWalletState"
                    f"[released schema {version} -> 77]",
                )
                for version in (26, 27, 28, 73, 74, 75, 76)
            ],
            (
                "jp.co.soramitsu.coredb.migrations."
                "ReleasedVersion27FailClosedMigrationTest",
                "orphanChainAccountRejectsFullUpgradeWithoutWipeOrSecretMutation",
            ),
        ],
    ),
    "app": (
        41,
        [
            (
                "jp.co.soramitsu.app.root.presentation."
                "WalletGateActivityLifecycleTest",
                "legacyGateRelaunchDiscardsNonRestorableFragmentState",
            ),
            (
                "jp.co.soramitsu.app.root.presentation."
                "WalletGateActivityLifecycleTest",
                "permanentRecoveryFailureShowsDiagnosticInsteadOfRetryOnlyRoute",
            ),
            (
                "jp.co.soramitsu.app.root.presentation."
                "WalletSecureStorageRestartActivityLifecycleTest",
                "latchWhileStoppedDefersRedirectUntilForeground",
            ),
            (
                "jp.co.soramitsu.app.root.presentation."
                "SecurityWarningRestorationTest",
                "restoredCancelButtonEmitsOnceAndExitsThroughResultListener",
            ),
            (
                "jp.co.soramitsu.app.root.presentation."
                "SecurityWarningRestorationTest",
                "restoredSystemCancelEmitsOnceAndExitsThroughResultListener",
            ),
            (
                "jp.co.soramitsu.app.root.presentation."
                "SecurityWarningRestorationTest",
                "restoredSystemBackEmitsOnceAndExitsThroughResultListener",
            ),
        ],
    ),
    "feature-account-impl": (
        4,
        [(
            "jp.co.soramitsu.account.api.domain.interfaces."
            "SignWithAccountCryptoRoutingTest",
            "substrateEcdsaChildSignsWithBoundChildKeyWithoutReadingRootSecrets",
        )],
    ),
}

for module, (count, required) in contracts.items():
    directory = (
        root / module / "build" / "outputs" / "androidTest-results"
        / "connected" / "debug"
    )
    directory.mkdir(parents=True)
    suite = ET.Element(
        "testsuite",
        name=f"fixture.{module}",
        tests=str(count),
        failures="0",
        errors="0",
        skipped="0",
    )
    identities = list(required)
    identities.extend(
        (f"fixture.{module}.Filler", f"filler-{index}")
        for index in range(count - len(identities))
    )
    for classname, test_name in identities:
        ET.SubElement(suite, "testcase", classname=classname, name=test_name)
    ET.ElementTree(suite).write(
        directory / f"TEST-fixture-{module}.xml",
        encoding="UTF-8",
        xml_declaration=True,
    )
PY
  printf '%s\n' "$fixture"
}

make_compatibility_fixture() {
  local name="$1"
  local fixture="$tmp_dir/$name"
  mkdir -p "$fixture"
  FIXTURE_ROOT="$fixture" python3 <<'PY'
import os
import xml.etree.ElementTree as ET
from pathlib import Path

root = Path(os.environ["FIXTURE_ROOT"])
marker = (
    root / "build" / "reports" / "android-migration-results"
    / "compatibility-start.marker"
)
marker.parent.mkdir(parents=True)
marker.touch()
keystore = (
    "jp.co.soramitsu.common.data.storage.encrypt."
    "EncryptionUtilAndroidKeyStoreTest"
)
matrix = "jp.co.soramitsu.coredb.migrations.ReleasedSchemaUpgradeMatrixTest"
orphan = (
    "jp.co.soramitsu.coredb.migrations."
    "ReleasedVersion27FailClosedMigrationTest"
)
gate = "jp.co.soramitsu.app.root.presentation.WalletGateActivityLifecycleTest"
restart = (
    "jp.co.soramitsu.app.root.presentation."
    "WalletSecureStorageRestartActivityLifecycleTest"
)
security_warning = (
    "jp.co.soramitsu.app.root.presentation."
    "SecurityWarningRestorationTest"
)
module_identities = {
    "common": [
        (keystore, "missingWrappedKeyNeverCreatesReplacementWhileProtectedDataRemains"),
        (keystore, "freshInstallPersistsRecoversAndAuthenticatesExactAesKey"),
        (keystore, "missingKeystoreAliasNeverCreatesReplacementForExistingWrappedKey"),
        (keystore, "hostileWrappedKeyRepresentationsFailPermanentlyWithoutMutation"),
        (keystore, "transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree"),
    ],
    "core-db": [
        *[
            (
                matrix,
                "exactReleasedSchemaUpgradesTo77WithoutLosingWalletState"
                f"[released schema {version} -> 77]",
            )
            for version in (26, 27, 28, 73, 74, 75, 76)
        ],
        (
            orphan,
            "orphanChainAccountRejectsFullUpgradeWithoutWipeOrSecretMutation",
        ),
    ],
    "app": [
        (gate, "legacyGateRelaunchDiscardsNonRestorableFragmentState"),
        (gate, "permanentRecoveryFailureShowsDiagnosticInsteadOfRetryOnlyRoute"),
        *[(gate, f"gate-compatibility-{index}") for index in range(18)],
        (restart, "latchWhileStoppedDefersRedirectUntilForeground"),
        *[(restart, f"restart-compatibility-{index}") for index in range(2)],
        (
            security_warning,
            "restoredCancelButtonEmitsOnceAndExitsThroughResultListener",
        ),
        (
            security_warning,
            "restoredSystemCancelEmitsOnceAndExitsThroughResultListener",
        ),
        (
            security_warning,
            "restoredSystemBackEmitsOnceAndExitsThroughResultListener",
        ),
    ],
}

for module, identities in module_identities.items():
    directory = (
        root / module / "build" / "outputs" / "androidTest-results"
        / "connected" / "debug"
    )
    directory.mkdir(parents=True)
    suite = ET.Element(
        "testsuite",
        name=f"fixture.{module}",
        tests=str(len(identities)),
        failures="0",
        errors="0",
        skipped="0",
    )
    for classname, test_name in identities:
        ET.SubElement(suite, "testcase", classname=classname, name=test_name)
    ET.ElementTree(suite).write(
        directory / f"TEST-fixture-{module}.xml",
        encoding="UTF-8",
        xml_declaration=True,
    )
PY
  printf '%s\n' "$fixture"
}

result_file() {
  local fixture="$1"
  local module="$2"
  printf '%s/%s/build/outputs/androidTest-results/connected/debug/TEST-fixture-%s.xml\n' \
    "$fixture" "$module" "$module"
}

result_marker() {
  local fixture="$1"
  local profile="${2:-full}"
  printf '%s/build/reports/android-migration-results/%s-start.marker\n' \
    "$fixture" "$profile"
}

replace_once() {
  local path="$1"
  local old="$2"
  local new="$3"
  OLD="$old" NEW="$new" perl -0pi -e '
    $old = $ENV{"OLD"};
    $new = $ENV{"NEW"};
    $count = s/\Q$old\E/$new/;
    END { exit 42 unless $count == 1; }
  ' "$path" || fail "fixture mutation did not replace exactly one value"
}

expect_failure() {
  local label="$1"
  local expected="$2"
  local fixture="$3"
  local output="$tmp_dir/${label//[^A-Za-z0-9]/-}.log"
  if MIGRATION_RESULTS_ROOT="$fixture" "$VERIFY" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$expected" "$output" || {
    sed -n '1,120p' "$output" >&2
    fail "$label omitted expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

fixture="$(make_fixture positive)"
MIGRATION_RESULTS_ROOT="$fixture" "$VERIFY" >/dev/null
positive_count=$((positive_count + 1))

fixture="$(make_compatibility_fixture compatibility-positive)"
MIGRATION_RESULTS_ROOT="$fixture" MIGRATION_RESULTS_PROFILE=compatibility \
  "$VERIFY" >/dev/null
positive_count=$((positive_count + 1))

fixture="$(make_fixture missing-result-start-marker)"
mv "$(result_marker "$fixture")" "$(result_marker "$fixture").missing"
expect_failure "missing result-start marker" \
  "result-start marker is missing or unsafe" "$fixture"

fixture="$(make_fixture symlink-result-start-marker)"
marker="$(result_marker "$fixture")"
mv "$marker" "$fixture/result-start-marker"
ln -s "$fixture/result-start-marker" "$marker"
expect_failure "symlink result-start marker" \
  "result-start marker is missing or unsafe" "$fixture"

fixture="$(make_fixture hardlink-result-start-marker)"
marker="$(result_marker "$fixture")"
mv "$marker" "$fixture/result-start-marker"
ln "$fixture/result-start-marker" "$marker"
expect_failure "hardlink result-start marker" \
  "result-start marker is missing or unsafe" "$fixture"

fixture="$(make_fixture nonempty-result-start-marker)"
printf '%s\n' hostile >"$(result_marker "$fixture")"
expect_failure "nonempty result-start marker" \
  "result-start marker is missing or unsafe" "$fixture"

fixture="$(make_fixture stale-result-start-marker)"
python3 - "$(result_marker "$fixture")" <<'PY'
import os
import sys
import time

stale_ns = time.time_ns() - 3 * 60 * 60 * 1_000_000_000
os.utime(sys.argv[1], ns=(stale_ns, stale_ns))
PY
expect_failure "stale result-start marker" \
  "result-start marker is stale" "$fixture"

fixture="$(make_fixture future-result-start-marker)"
python3 - "$(result_marker "$fixture")" <<'PY'
import os
import sys
import time

future_ns = time.time_ns() + 10 * 60 * 1_000_000_000
os.utime(sys.argv[1], ns=(future_ns, future_ns))
PY
expect_failure "future result-start marker" \
  "result-start marker has an unsafe future timestamp" "$fixture"

fixture="$(make_fixture stale-valid-result-xml)"
python3 - "$(result_marker "$fixture")" \
  "$(result_file "$fixture" common)" <<'PY'
import os
import sys

marker_ns = os.stat(sys.argv[1], follow_symlinks=False).st_mtime_ns
os.utime(sys.argv[2], ns=(marker_ns - 1, marker_ns - 1))
PY
expect_failure "stale valid result XML" \
  "connected-result XML predates the current test run" "$fixture"

fixture="$(make_fixture symlink-result-marker-parent)"
marker_parent="$(dirname "$(result_marker "$fixture")")"
mv "$marker_parent" "$fixture/result-marker-parent"
ln -s "$fixture/result-marker-parent" "$marker_parent"
expect_failure "symlink result-marker parent" \
  "result-start marker is missing or unsafe" "$fixture"

fixture="$(make_fixture symlink-module-ancestor)"
mv "$fixture/common" "$fixture/common-real"
ln -s "$fixture/common-real" "$fixture/common"
expect_failure "symlink module ancestor" \
  "connected-result directory is missing or unsafe" "$fixture"

fixture="$(make_fixture missing-module-results)"
mv "$fixture/core-db/build/outputs/androidTest-results/connected/debug" \
  "$fixture/core-db/build/outputs/androidTest-results/connected/debug-missing"
expect_failure "missing module results" "connected-result directory is missing" "$fixture"

fixture="$(make_fixture duplicate-result-xml)"
cp "$(result_file "$fixture" common)" \
  "$fixture/common/build/outputs/androidTest-results/connected/debug/TEST-extra.xml"
expect_failure "duplicate result XML" "exactly one connected-result XML" "$fixture"

fixture="$(make_fixture symlink-result-xml)"
path="$(result_file "$fixture" common)"
mv "$path" "$fixture/common-result.xml"
ln -s "$fixture/common-result.xml" "$path"
expect_failure "symlink result XML" "empty, oversized, or unsafe" "$fixture"

fixture="$(make_fixture hardlink-result-xml)"
path="$(result_file "$fixture" common)"
mv "$path" "$fixture/common-result.xml"
ln "$fixture/common-result.xml" "$path"
expect_failure "hardlink result XML" "empty, oversized, or unsafe" "$fixture"

fixture="$(make_fixture malformed-result-xml)"
printf '%s\n' '<testsuite' >"$(result_file "$fixture" common)"
expect_failure "malformed result XML" "cannot be parsed" "$fixture"

fixture="$(make_fixture nonzero-failure-count)"
replace_once "$(result_file "$fixture" common)" 'failures="0"' 'failures="1"'
expect_failure "nonzero failure count" "results are not fully green" "$fixture"

fixture="$(make_fixture nested-failure-record)"
replace_once "$(result_file "$fixture" common)" \
  '</testsuite>' '<failure message="hidden" /></testsuite>'
expect_failure "nested failure record" "contains failure/error records" "$fixture"

fixture="$(make_fixture skipped-test-count)"
replace_once "$(result_file "$fixture" common)" 'skipped="0"' 'skipped="1"'
expect_failure "skipped test count" "results are not fully green" "$fixture"

fixture="$(make_fixture declared-count-mismatch)"
replace_once "$(result_file "$fixture" common)" 'tests="5"' 'tests="6"'
expect_failure "declared count mismatch" "does not match testcase records" "$fixture"

fixture="$(make_fixture below-module-minimum)"
path="$(result_file "$fixture" common)"
python3 - "$path" <<'PY'
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
tree = ET.parse(path)
root = tree.getroot()
root.remove(list(root)[-1])
root.set("tests", "4")
tree.write(path, encoding="UTF-8", xml_declaration=True)
PY
expect_failure "below module minimum" "minimum is 5" "$fixture"

fixture="$(make_fixture duplicate-test-identity)"
replace_once "$(result_file "$fixture" common)" \
  'classname="fixture.common.Filler" name="filler-0"' \
  'classname="jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilAndroidKeyStoreTest" name="transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree"'
expect_failure "duplicate test identity" "duplicate testcase identities" "$fixture"

fixture="$(make_fixture missing-common-critical-test)"
replace_once "$(result_file "$fixture" common)" \
  'transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree' \
  'transientPinDecryptProviderFailureRemoved'
expect_failure "missing common critical test" "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture missing-core-db-critical-test)"
replace_once "$(result_file "$fixture" core-db)" \
  'exactReleasedSchemaUpgradesTo77WithoutLosingWalletState[released schema 27 -&gt; 77]' \
  'exactReleasedSchemaUpgradeRemoved[released schema 27 -&gt; 77]'
expect_failure "missing core-db critical test" "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture missing-app-critical-test)"
replace_once "$(result_file "$fixture" app)" \
  'legacyGateRelaunchDiscardsNonRestorableFragmentState' \
  'legacyGateRelaunchCoverageRemoved'
expect_failure "missing app critical test" "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture missing-security-warning-critical-test)"
replace_once "$(result_file "$fixture" app)" \
  'restoredCancelButtonEmitsOnceAndExitsThroughResultListener' \
  'restoredCancelButtonCoverageRemoved'
expect_failure "missing security-warning critical test" \
  "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture missing-security-warning-back-critical-test)"
replace_once "$(result_file "$fixture" app)" \
  'restoredSystemBackEmitsOnceAndExitsThroughResultListener' \
  'restoredSystemBackCoverageRemoved'
expect_failure "missing security-warning Back critical test" \
  "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture missing-account-critical-test)"
replace_once "$(result_file "$fixture" feature-account-impl)" \
  'substrateEcdsaChildSignsWithBoundChildKeyWithoutReadingRootSecrets' \
  'childSigningCoverageRemoved'
expect_failure "missing account critical test" "omitted required migration/startup tests" "$fixture"

fixture="$(make_fixture wrong-xml-root)"
replace_once "$(result_file "$fixture" common)" '<testsuite ' '<testsuites '
replace_once "$(result_file "$fixture" common)" '</testsuite>' '</testsuites>'
expect_failure "wrong XML root" "root must be testsuite" "$fixture"

fixture="$(make_fixture testcase-without-class)"
replace_once "$(result_file "$fixture" common)" \
  'classname="fixture.common.Filler" name="filler-0"' \
  'name="filler-0"'
expect_failure "testcase without class" "without class/name identity" "$fixture"

fixture="$(make_fixture nonnumeric-suite-count)"
replace_once "$(result_file "$fixture" common)" 'errors="0"' 'errors="none"'
expect_failure "nonnumeric suite count" "invalid 'errors' count" "$fixture"

fixture="$(make_fixture unsupported-profile)"
output="$tmp_dir/unsupported-profile.log"
if MIGRATION_RESULTS_ROOT="$fixture" MIGRATION_RESULTS_PROFILE=weakened \
  "$VERIFY" >"$output" 2>&1; then
  fail "unsupported profile unexpectedly passed"
fi
grep -Fq "unsupported result profile" "$output" ||
  fail "unsupported profile omitted expected diagnostic"
negative_count=$((negative_count + 1))

fixture="$(make_fixture full-results-as-compatibility)"
cp -p "$(result_marker "$fixture" full)" \
  "$(result_marker "$fixture" compatibility)"
output="$tmp_dir/full-results-as-compatibility.log"
if MIGRATION_RESULTS_ROOT="$fixture" MIGRATION_RESULTS_PROFILE=compatibility \
  "$VERIFY" >"$output" 2>&1; then
  fail "full results unexpectedly passed as compatibility shard"
fi
grep -Fq "compatibility class/test counts changed" "$output" ||
  fail "full-as-compatibility omitted expected diagnostic"
negative_count=$((negative_count + 1))

fixture="$(make_compatibility_fixture compatibility-missing-critical)"
replace_once "$(result_file "$fixture" common)" \
  'transientPinDecryptProviderFailureRemainsRetryableAndSideEffectFree' \
  'transientPinDecryptProviderFailureRemoved'
output="$tmp_dir/compatibility-missing-critical.log"
if MIGRATION_RESULTS_ROOT="$fixture" MIGRATION_RESULTS_PROFILE=compatibility \
  "$VERIFY" >"$output" 2>&1; then
  fail "compatibility shard missing a critical test unexpectedly passed"
fi
grep -Fq "omitted required migration/startup tests" "$output" ||
  fail "compatibility missing-critical omitted expected diagnostic"
negative_count=$((negative_count + 1))

fixture="$(make_compatibility_fixture compatibility-extra-test)"
path="$(result_file "$fixture" app)"
replace_once "$path" 'tests="26"' 'tests="27"'
replace_once "$path" '</testsuite>' \
  '<testcase classname="jp.co.soramitsu.app.root.presentation.WalletGateActivityLifecycleTest" name="unexpected-extra" /></testsuite>'
output="$tmp_dir/compatibility-extra-test.log"
if MIGRATION_RESULTS_ROOT="$fixture" MIGRATION_RESULTS_PROFILE=compatibility \
  "$VERIFY" >"$output" 2>&1; then
  fail "compatibility shard with an extra test unexpectedly passed"
fi
grep -Fq "exact compatibility count is 26" "$output" ||
  fail "compatibility extra-test omitted expected diagnostic"
negative_count=$((negative_count + 1))

[[ "$positive_count" == "$EXPECTED_POSITIVE_COUNT" ]] ||
  fail "expected $EXPECTED_POSITIVE_COUNT positive case; got $positive_count"
[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]] ||
  fail "expected $EXPECTED_NEGATIVE_COUNT negative cases; got $negative_count"

echo \
  "[android-migration-results-test] $positive_count positive + $negative_count negative/adversarial cases passed"
