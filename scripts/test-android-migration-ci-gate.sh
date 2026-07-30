#!/usr/bin/env bash
# shellcheck disable=SC1003,SC2016
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-migration-ci-gate.sh"
EXPECTED_POSITIVE_COUNT=1
EXPECTED_NEGATIVE_COUNT=82

tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$tmp_root"
tmp_dir="$(mktemp -d "$tmp_root/android-migration-ci-test.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-migration-ci-test][error] $*" >&2
  exit 1
}

positive_count=0
negative_count=0

make_fixture() {
  local name="$1"
  local fixture="$tmp_dir/$name"
  mkdir -p "$fixture/.github/workflows"
  mkdir -p "$fixture/scripts"
  cp "$ROOT_DIR/.github/workflows/android-ci.yml" \
    "$fixture/.github/workflows/android-ci.yml"
  cp "$ROOT_DIR/scripts/run-android-emulator-ci.sh" \
    "$fixture/scripts/run-android-emulator-ci.sh"
  cp "$ROOT_DIR/scripts/run-android-migration-compatibility.sh" \
    "$fixture/scripts/run-android-migration-compatibility.sh"
  cp "$ROOT_DIR/scripts/run-android-migration-full.sh" \
    "$fixture/scripts/run-android-migration-full.sh"
  printf '%s\n' "$fixture"
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

replace_last_once() {
  local path="$1"
  local old="$2"
  local new="$3"
  python3 - "$path" "$old" "$new" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
old = sys.argv[2]
new = sys.argv[3]
text = path.read_text(encoding="utf-8")
position = text.rfind(old)
if position < 0:
    raise SystemExit(42)
path.write_text(
    text[:position] + new + text[position + len(old):],
    encoding="utf-8",
)
PY
}

expect_failure() {
  local label="$1"
  local expected="$2"
  local fixture="$3"
  local output="$tmp_dir/${label//[^A-Za-z0-9]/-}.log"
  if MIGRATION_CI_ROOT="$fixture" "$VERIFY" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$expected" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label omitted expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

MIGRATION_CI_ROOT="$ROOT_DIR" "$VERIFY" >/dev/null
positive_count=$((positive_count + 1))

fixture="$(make_fixture missing-pull-request)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "  pull_request:" "  pull_request_disabled:"
expect_failure "missing pull request trigger" "pull_request trigger" "$fixture"

fixture="$(make_fixture renamed-required-job)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "  build-and-test:" "  build-and-test-disabled:"
expect_failure "renamed required job" "required build-and-test job" "$fixture"

fixture="$(make_fixture mutable-runner)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "    runs-on: ubuntu-24.04" "    runs-on: ubuntu-latest"
expect_failure "mutable runner" "pinned Ubuntu 24.04 runner" "$fixture"

fixture="$(make_fixture shortened-timeout)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "    timeout-minutes: 90" "    timeout-minutes: 45"
expect_failure "shortened timeout" "90-minute instrumentation-capable timeout" "$fixture"

fixture="$(make_fixture missing-self-test)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "          bash ./scripts/test-android-migration-ci-gate.sh" \
  "          bash ./scripts/test-android-migration-ci-gate-disabled.sh"
expect_failure "missing self test" "migration CI adversarial guard invocation" "$fixture"

fixture="$(make_fixture missing-result-parser-self-test)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "          bash ./scripts/test-android-migration-instrumentation-results.sh" \
  "          bash ./scripts/test-android-migration-instrumentation-results-disabled.sh"
expect_failure \
  "missing result parser self test" \
  "migration result-parser adversarial guard invocation" \
  "$fixture"

fixture="$(make_fixture policy-blocked-emulator-action)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "actions/setup-java@c1e323688fd81a25caa38c78aa6df2d33d3e20d9" \
  "ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d"
expect_failure \
  "repository-policy-blocked emulator action" \
  "outside the repository-policy allowlist" \
  "$fixture"

for field_mutation in \
  'readonly SYSTEM_IMAGE_TARGET="google_atd"|readonly SYSTEM_IMAGE_TARGET="default"' \
  'readonly SYSTEM_IMAGE_ARCH="x86_64"|readonly SYSTEM_IMAGE_ARCH="x86"' \
  '  -memory 2048 \|  -memory 4096 \' \
  '  -cores 2 \|  -cores 1 \' \
  '  -gpu swiftshader \|  -gpu host \'; do
  old="${field_mutation%%|*}"
  new="${field_mutation#*|}"
  fixture="$(make_fixture "mutated-${old%%:*}")"
  replace_once "$fixture/scripts/run-android-emulator-ci.sh" "$old" "$new"
  expect_failure \
    "mutated emulator field $old" \
    "Android emulator lifecycle runner line '$old'" \
    "$fixture"
done

fixture="$(make_fixture missing-sdk-emulator-package)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  '            "emulator" \' \
  '            "emulator-disabled" \'
expect_failure \
  "missing SDK emulator package" \
  "Android SDK setup contract line" \
  "$fixture"

fixture="$(make_fixture suppressed-sdk-license-failure)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  '            "$sdkmanager_bin" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null' \
  '            "$sdkmanager_bin" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null || true'
expect_failure \
  "suppressed SDK license failure" \
  "must not suppress license or package installation failures" \
  "$fixture"

fixture="$(make_fixture missing-command-tool-export)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  '            echo "SDKMANAGER_BIN=$sdkmanager_bin"' \
  '            echo "SDKMANAGER_BIN_DISABLED=$sdkmanager_bin"'
expect_failure \
  "missing command-line tool export" \
  "Android SDK setup contract line" \
  "$fixture"

fixture="$(make_fixture unbound-command-tool-path)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '[[ -n "${SDKMANAGER_BIN:-}" && "$SDKMANAGER_BIN" == "$ANDROID_SDK_ROOT"/* ]] ||' \
  '[[ -n "${SDKMANAGER_BIN:-}" ]] ||'
expect_failure \
  "unbound command-line tool path" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture unsupported-api-accepted)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  "  30:compatibility|31:compatibility|34:full|36:compatibility) ;;" \
  "  30:compatibility|31:compatibility|34:full|35:full|36:compatibility) ;;"
expect_failure \
  "unsupported API accepted" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture missing-kvm-device-check)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '[[ -c /dev/kvm ]] || fail "/dev/kvm is not a character device"' \
  'echo "/dev/kvm check disabled"'
expect_failure \
  "missing KVM device check" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture missing-kvm-access-remediation)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  sudo setfacl -m "u:$(id -un):rw" /dev/kvm' \
  '  echo "KVM ACL disabled"'
expect_failure \
  "missing KVM access remediation" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture missing-acceleration-check)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  "$EMULATOR" -accel-check >"$EVIDENCE_DIR/accel-check.txt" 2>&1; then' \
  '  false; then'
expect_failure \
  "missing emulator acceleration check" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture acceleration-disabled)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  -accel on \' \
  '  -accel off \'
expect_failure \
  "emulator acceleration disabled" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture reused-emulator-user-data)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  -wipe-data \' \
  '  -no-wipe-data \'
expect_failure \
  "reused emulator user data" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture snapshots-enabled)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  -no-snapshot \' \
  '  -snapshot default \'
expect_failure \
  "emulator snapshots enabled" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture deprecated-gpu-mode)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '  -gpu swiftshader \' \
  '  -gpu swiftshader_indirect \'
expect_failure \
  "deprecated emulator GPU mode" \
  "Android emulator lifecycle runner line" \
  "$fixture"

for additive_mutation in \
  '  -gpu swiftshader \|  -gpu swiftshader \\n  -gpu host \' \
  '  -memory 2048 \|  -memory 2048 \\n  -memory 4096 \' \
  '  -accel on \|  -accel on \\n  -accel off \' \
  '  -no-snapshot \|  -no-snapshot \\n  -snapshot default \'; do
  old="${additive_mutation%%|*}"
  new="${additive_mutation#*|}"
  new="${new//\\n/$'\n'}"
  fixture="$(make_fixture "additive-${old//[^A-Za-z0-9]/-}")"
  replace_once "$fixture/scripts/run-android-emulator-ci.sh" "$old" "$new"
  expect_failure \
    "additive emulator option override $old" \
    "must contain exactly one" \
    "$fixture"
done

fixture="$(make_fixture unbounded-boot-deadline)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'readonly BOOT_TIMEOUT_SECONDS=600' \
  'readonly BOOT_TIMEOUT_SECONDS=0'
expect_failure \
  "unbounded emulator boot deadline" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture ignored-emulator-process-death)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '    fail "emulator exited before completing boot"' \
  '    echo "emulator process death ignored"'
expect_failure \
  "ignored emulator process death" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture ignored-system-boot-property)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '      if [[ "$sys_boot" == "1" ]] &&' \
  '      if [[ "$sys_boot" == "0" ]] &&'
expect_failure \
  "ignored Android system boot property" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture missing-package-manager-readiness)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '          "$ADB" -s "$EMULATOR_SERIAL" shell pm path android 2>/dev/null)" &&' \
  '          printf "package:fake")" &&'
expect_failure \
  "missing Android package-manager readiness" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture missing-android-serial-export)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'export ANDROID_SERIAL="$EMULATOR_SERIAL"' \
  'echo "ANDROID_SERIAL export disabled"'
expect_failure \
  "missing ANDROID_SERIAL export" \
  "Android emulator lifecycle runner line" \
  "$fixture"

fixture="$(make_fixture arbitrary-shell-dispatch)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'export ANDROID_SERIAL="$EMULATOR_SERIAL"' \
  $'export ANDROID_SERIAL="$EMULATOR_SERIAL"\nbash -c true'
expect_failure \
  "arbitrary shell dispatch" \
  "forbidden bypass or broad process operation" \
  "$fixture"

fixture="$(make_fixture broad-process-kill)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'export ANDROID_SERIAL="$EMULATOR_SERIAL"' \
  $'export ANDROID_SERIAL="$EMULATOR_SERIAL"\npkill emulator'
expect_failure \
  "broad emulator process kill" \
  "forbidden bypass or broad process operation" \
  "$fixture"

fixture="$(make_fixture cleanup-trap-after-launch)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'trap cleanup EXIT' \
  '# cleanup trap moved'
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  'setsid "$EMULATOR" \' \
  $'setsid "$EMULATOR" \\\ntrap cleanup EXIT'
expect_failure \
  "cleanup trap after launch" \
  "cleanup trap must be installed before launch" \
  "$fixture"

fixture="$(make_fixture teardown-before-diagnostics)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  $'  if ! record_adb_diagnostics; then\n    cleanup_status=1\n  fi\n  if ! stop_emulator; then\n    cleanup_status=1\n  fi' \
  $'  if ! stop_emulator; then\n    cleanup_status=1\n  fi\n  if ! record_adb_diagnostics; then\n    cleanup_status=1\n  fi'
expect_failure \
  "teardown before diagnostics" \
  "diagnostics must be captured before teardown" \
  "$fixture"

fixture="$(make_fixture missing-lifecycle-evidence-upload)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "            build/reports/android-emulator-lifecycle/**" \
  "            build/reports/android-emulator-disabled/**"
expect_failure \
  "missing emulator lifecycle evidence upload" \
  "android-emulator-lifecycle" \
  "$fixture"

fixture="$(make_fixture late-lifecycle-start-marker)"
replace_once "$fixture/scripts/run-android-emulator-ci.sh" \
  '(umask 077; : >"$EVIDENCE_DIR/lifecycle-start.marker")' \
  'echo "lifecycle start marker disabled"'
expect_failure \
  "missing lifecycle start marker" \
  "lifecycle runner ordering is malformed" \
  "$fixture"

for module in common core-db app feature-account-impl; do
  fixture="$(make_fixture "missing-${module//[^A-Za-z0-9]/-}-tests")"
  replace_once "$fixture/scripts/run-android-migration-full.sh" \
    "  :$module:connectedDebugAndroidTest \\" \
    "  :$module:connectedDebugAndroidTestDisabled \\"
  expect_failure \
    "missing $module connected tests" \
    "full migration runner line '  :$module:connectedDebugAndroidTest" \
    "$fixture"
done

fixture="$(make_fixture full-marker-after-gradle)"
replace_once "$fixture/scripts/run-android-migration-full.sh" \
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
  "./gradlew \\"
replace_last_once "$fixture/scripts/run-android-migration-full.sh" \
  "./gradlew \\" \
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
expect_failure \
  "full marker after Gradle" \
  "full marker must be created after every cleanup and before Gradle" \
  "$fixture"

fixture="$(make_fixture missing-continue)"
replace_once "$fixture/scripts/run-android-migration-full.sh" \
  "  --continue \\" "  --warning-mode=all \\"
expect_failure "missing continue" "full migration runner line '  --continue" "$fixture"

fixture="$(make_fixture excluded-tests)"
replace_once "$fixture/scripts/run-android-migration-full.sh" \
  "  --continue \\" \
  $'  -x :core-db:connectedDebugAndroidTest \\\n  --continue \\'
expect_failure "excluded tests" "must not exclude or filter tests" "$fixture"

fixture="$(make_fixture suppressed-failure)"
replace_once "$fixture/scripts/run-android-migration-full.sh" \
  "  --stacktrace" "  --stacktrace || true"
expect_failure "suppressed failure" "must not suppress command failures" "$fixture"

fixture="$(make_fixture conditional-instrumentation)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Wallet migration and startup instrumentation\n        run:' \
  $'      - name: Wallet migration and startup instrumentation\n        if: false\n        run:'
expect_failure \
  "conditional instrumentation" \
  "must be unconditional and fail closed" \
  "$fixture"

fixture="$(make_fixture continue-on-error)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Wallet migration and startup instrumentation\n        run:' \
  $'      - name: Wallet migration and startup instrumentation\n        continue-on-error: true\n        run:'
expect_failure \
  "continue on instrumentation error" \
  "must be unconditional and fail closed" \
  "$fixture"

fixture="$(make_fixture missing-result-verification-step)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "      - name: Verify migration instrumentation results" \
  "      - name: Verify migration instrumentation results disabled"
expect_failure \
  "missing result verification step" \
  "exact migration result-verification step is missing" \
  "$fixture"

fixture="$(make_fixture conditional-result-verification)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Verify migration instrumentation results\n        if: always()' \
  $'      - name: Verify migration instrumentation results\n        if: success()'
expect_failure \
  "conditional result verification" \
  "contract line '        if: always()'" \
  "$fixture"

fixture="$(make_fixture suppressed-result-verification)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "        run: bash ./scripts/verify-android-migration-instrumentation-results.sh" \
  "        run: bash ./scripts/verify-android-migration-instrumentation-results.sh || true"
expect_failure \
  "suppressed result verification" \
  "must fail closed" \
  "$fixture"

fixture="$(make_fixture result-verification-after-evidence)"
python3 - "$fixture/.github/workflows/android-ci.yml" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text(encoding="utf-8")
result = (
    "      - name: Verify migration instrumentation results\n"
    "        if: always()\n"
    "        run: bash ./scripts/verify-android-migration-instrumentation-results.sh\n\n"
)
if text.count(result) != 1:
    raise SystemExit(42)
text = text.replace(result, "", 1)
marker = "      - name: Gradle/AGP versions\n"
if text.count(marker) != 1:
    raise SystemExit(42)
path.write_text(text.replace(marker, result + marker, 1), encoding="utf-8")
PY
expect_failure \
  "result verification after evidence" \
  "evidence upload must follow result verification" \
  "$fixture"

fixture="$(make_fixture mutable-upload-action)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Upload migration instrumentation evidence\n        if: always()\n        uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2' \
  $'      - name: Upload migration instrumentation evidence\n        if: always()\n        uses: actions/upload-artifact@v4 # v4.6.2'
expect_failure \
  "mutable evidence upload action" \
  "outside the repository-policy allowlist" \
  "$fixture"

fixture="$(make_fixture missing-always-evidence)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Upload migration instrumentation evidence\n        if: always()' \
  $'      - name: Upload migration instrumentation evidence\n        if: success()'
expect_failure "conditional evidence" "contract line '        if: always()'" "$fixture"

fixture="$(make_fixture missing-result-path)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "            **/build/outputs/androidTest-results/connected/**" \
  "            **/build/outputs/androidTest-results/disabled/**"
expect_failure \
  "missing XML result evidence" \
  "androidTest-results/connected" \
  "$fixture"

fixture="$(make_fixture allow-missing-evidence)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "          if-no-files-found: error" \
  "          if-no-files-found: ignore"
expect_failure "allow missing evidence" "if-no-files-found: error" "$fixture"

fixture="$(make_fixture missing-compatibility-runner)"
mv "$fixture/scripts/run-android-migration-compatibility.sh" \
  "$fixture/scripts/run-android-migration-compatibility.sh.disabled"
expect_failure \
  "missing compatibility runner" \
  "compatibility runner must be a regular" \
  "$fixture"

fixture="$(make_fixture missing-emulator-lifecycle-runner)"
mv "$fixture/scripts/run-android-emulator-ci.sh" \
  "$fixture/scripts/run-android-emulator-ci.sh.disabled"
expect_failure \
  "missing emulator lifecycle runner" \
  "emulator lifecycle runner must be a regular" \
  "$fixture"

fixture="$(make_fixture missing-full-runner)"
mv "$fixture/scripts/run-android-migration-full.sh" \
  "$fixture/scripts/run-android-migration-full.sh.disabled"
expect_failure \
  "missing full migration runner" \
  "full migration runner must be a regular" \
  "$fixture"

fixture="$(make_fixture bypassed-full-runner)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 34 full" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 34 compatibility"
expect_failure \
  "bypassed full migration runner" \
  "migration instrumentation contract line" \
  "$fixture"

fixture="$(make_fixture missing-full-result-cleanup)"
replace_once "$fixture/scripts/run-android-migration-full.sh" \
  "rm -rf feature-account-impl/build/outputs/androidTest-results/connected" \
  "echo retained-feature-account-results"
expect_failure \
  "missing full result cleanup" \
  "Android full migration runner line" \
  "$fixture"

for module in common core-db app feature-account-impl; do
  fixture="$(make_fixture "full-marker-before-${module}-cleanup")"
  cleanup="rm -rf $module/build/outputs/androidTest-results/connected"
  replace_once "$fixture/scripts/run-android-migration-full.sh" \
    "$cleanup" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
  replace_last_once "$fixture/scripts/run-android-migration-full.sh" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
    "$cleanup"
  expect_failure \
    "full marker before $module cleanup" \
    "full marker must be created after every cleanup and before Gradle" \
    "$fixture"
done

fixture="$(make_fixture missing-compatibility-result-marker)"
replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
  'echo compatibility-marker-disabled'
expect_failure \
  "missing compatibility result marker" \
  "Android compatibility runner line" \
  "$fixture"

for module in common core-db app; do
  fixture="$(make_fixture "compatibility-marker-before-${module}-cleanup")"
  cleanup="rm -rf $module/build/outputs/androidTest-results/connected"
  replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
    "$cleanup" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
  replace_last_once "$fixture/scripts/run-android-migration-compatibility.sh" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
    "$cleanup"
  expect_failure \
    "compatibility marker before $module cleanup" \
    "compatibility marker must be created after every cleanup and before Gradle" \
    "$fixture"
done

for module in common core-db app; do
  fixture="$(make_fixture "compatibility-marker-after-${module}-gradle")"
  gradle_line="./gradlew :$module:connectedDebugAndroidTest \\"
  replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
    "$gradle_line"
  replace_last_once "$fixture/scripts/run-android-migration-compatibility.sh" \
    "$gradle_line" \
    "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
  expect_failure \
    "compatibility marker after $module Gradle" \
    "compatibility marker must be created after every cleanup and before every Gradle invocation" \
    "$fixture"
done

fixture="$(make_fixture missing-api30-compatibility-step)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "      - name: Android 11 migration and restart compatibility" \
  "      - name: Android 11 migration compatibility disabled"
expect_failure \
  "missing API 30 compatibility step" \
  "API 30 compatibility step is missing" \
  "$fixture"

fixture="$(make_fixture wrong-api31-compatibility-level)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 31 compatibility" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 32 compatibility"
expect_failure \
  "wrong API 31 compatibility level" \
  "API 31 compatibility contract line" \
  "$fixture"

fixture="$(make_fixture bypassed-compatibility-emulator-lifecycle)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "bash ./scripts/run-android-emulator-ci.sh 30 compatibility" \
  "bash ./scripts/run-android-migration-compatibility.sh"
expect_failure \
  "bypassed compatibility emulator lifecycle" \
  "API 30 compatibility contract line" \
  "$fixture"

fixture="$(make_fixture conditional-api36-compatibility-step)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  $'      - name: Android 16 migration and restart compatibility\n        run:' \
  $'      - name: Android 16 migration and restart compatibility\n        if: success()\n        run:'
expect_failure \
  "conditional API 36 compatibility step" \
  "API 36 compatibility step must be unconditional" \
  "$fixture"

fixture="$(make_fixture mismatched-api31-runner-binding)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 31 compatibility" \
  "        run: bash ./scripts/run-android-emulator-ci.sh 30 compatibility"
expect_failure \
  "mismatched API 31 runner binding" \
  "API 31 compatibility contract line" \
  "$fixture"

fixture="$(make_fixture missing-compatibility-keystore-class)"
replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
  "jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilAndroidKeyStoreTest" \
  "jp.co.soramitsu.common.data.storage.encrypt.RemovedKeyStoreTest"
expect_failure \
  "missing compatibility keystore class" \
  "Android compatibility runner line" \
  "$fixture"

fixture="$(make_fixture missing-compatibility-security-warning-class)"
replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
  "jp.co.soramitsu.app.root.presentation.SecurityWarningRestorationTest" \
  "jp.co.soramitsu.app.root.presentation.RemovedSecurityWarningTest"
expect_failure \
  "missing compatibility security-warning class" \
  "Android compatibility runner line" \
  "$fixture"

fixture="$(make_fixture weakened-compatibility-result-profile)"
replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
  "MIGRATION_RESULTS_PROFILE=compatibility" \
  "MIGRATION_RESULTS_PROFILE=full"
expect_failure \
  "weakened compatibility result profile" \
  "Android compatibility runner line" \
  "$fixture"

fixture="$(make_fixture suppressed-compatibility-result-verification)"
replace_once "$fixture/scripts/run-android-migration-compatibility.sh" \
  "  bash ./scripts/verify-android-migration-instrumentation-results.sh" \
  "  bash ./scripts/verify-android-migration-instrumentation-results.sh || true"
expect_failure \
  "suppressed compatibility result verification" \
  "must not filter tests or suppress failures" \
  "$fixture"

[[ "$positive_count" == "$EXPECTED_POSITIVE_COUNT" ]] ||
  fail "expected $EXPECTED_POSITIVE_COUNT positive case; got $positive_count"
[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]] ||
  fail "expected $EXPECTED_NEGATIVE_COUNT negative cases; got $negative_count"

echo \
  "[android-migration-ci-test] $positive_count positive + $negative_count negative/adversarial cases passed"
