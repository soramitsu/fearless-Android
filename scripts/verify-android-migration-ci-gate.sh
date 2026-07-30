#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${MIGRATION_CI_ROOT:-$(cd "$(dirname "$0")/.." && pwd -P)}"
WORKFLOW="$ROOT_DIR/.github/workflows/android-ci.yml"
COMPATIBILITY_RUNNER="$ROOT_DIR/scripts/run-android-migration-compatibility.sh"
FULL_RUNNER="$ROOT_DIR/scripts/run-android-migration-full.sh"

fail() {
  echo "[android-migration-ci][error] $*" >&2
  exit 1
}

[[ -f "$WORKFLOW" && ! -L "$WORKFLOW" ]] ||
  fail "Android CI workflow must be a regular non-symlink file"
[[ "$(wc -c < "$WORKFLOW" | tr -d '[:space:]')" -le 131072 ]] ||
  fail "Android CI workflow exceeds the 128 KiB review limit"
[[ -f "$COMPATIBILITY_RUNNER" && ! -L "$COMPATIBILITY_RUNNER" ]] ||
  fail "Android compatibility runner must be a regular non-symlink file"
[[ "$(wc -c < "$COMPATIBILITY_RUNNER" | tr -d '[:space:]')" -le 32768 ]] ||
  fail "Android compatibility runner exceeds the 32 KiB review limit"
[[ -f "$FULL_RUNNER" && ! -L "$FULL_RUNNER" ]] ||
  fail "Android full migration runner must be a regular non-symlink file"
[[ "$(wc -c < "$FULL_RUNNER" | tr -d '[:space:]')" -le 32768 ]] ||
  fail "Android full migration runner exceeds the 32 KiB review limit"

require_workflow_line() {
  local line="$1"
  local label="$2"
  [[ "$(grep -Fxc -- "$line" "$WORKFLOW" || true)" == "1" ]] ||
    fail "$label must appear exactly once"
}

require_block_line() {
  local block="$1"
  local line="$2"
  local label="$3"
  [[ "$(grep -Fxc -- "$line" <<<"$block" || true)" == "1" ]] ||
    fail "$label must appear exactly once in the required build-and-test job"
}

extract_step() {
  local block="$1"
  local name="$2"
  awk -v target="      - name: $name" '
    $0 == target {
      found++
      capture = 1
    }
    capture && /^      - name: / && $0 != target {
      exit
    }
    capture {
      print
    }
    END {
      if (found != 1) exit 42
    }
  ' <<<"$block"
}

require_workflow_line "  pull_request:" "pull_request trigger"
require_workflow_line "  push:" "push trigger"
require_workflow_line "  build-and-test:" "required build-and-test job"
if grep -Eq '^    paths(-ignore)?:' "$WORKFLOW"; then
  fail "Android CI must not path-filter migration-sensitive pull requests"
fi

build_job="$({
  awk '
    $0 == "  build-and-test:" {
      found++
      capture = 1
    }
    capture && /^  [A-Za-z0-9_-]+:$/ && $0 != "  build-and-test:" {
      exit
    }
    capture {
      print
    }
    END {
      if (found != 1) exit 42
    }
  ' "$WORKFLOW"
} 2>/dev/null)" || fail "required build-and-test job is malformed or duplicated"

require_block_line "$build_job" \
  "    runs-on: ubuntu-24.04" \
  "pinned Ubuntu 24.04 runner"
require_block_line "$build_job" \
  "    timeout-minutes: 90" \
  "90-minute instrumentation-capable timeout"
require_block_line "$build_job" \
  "          bash ./scripts/test-android-migration-ci-gate.sh" \
  "migration CI adversarial guard invocation"
require_block_line "$build_job" \
  "          bash ./scripts/test-android-migration-instrumentation-results.sh" \
  "migration result-parser adversarial guard invocation"

for api_level in 30 31 36; do
  case "$api_level" in
    30) step_name="Android 11 migration and restart compatibility" ;;
    31) step_name="Android 12 migration and restart compatibility" ;;
    36) step_name="Android 16 migration and restart compatibility" ;;
  esac
  compatibility_step="$({
    extract_step "$build_job" "$step_name"
  } 2>/dev/null)" || fail "API $api_level compatibility step is missing or duplicated"
  required_compatibility_lines=(
    "        uses: ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2.38.0"
    "          api-level: $api_level"
    "          target: google_atd"
    "          arch: x86_64"
    "          profile: pixel_6"
    "          cores: 2"
    "          ram-size: 2048M"
    "          heap-size: 512M"
    "          emulator-boot-timeout: 600"
    "          disable-animations: true"
    "          disable-spellchecker: true"
    "          script: MIGRATION_COMPAT_API=$api_level bash ./scripts/run-android-migration-compatibility.sh"
  )
  if grep -Eq '^[[:space:]]+(if|continue-on-error):' <<<"$compatibility_step"; then
    fail "API $api_level compatibility step must be unconditional and fail closed"
  fi
  for required_line in "${required_compatibility_lines[@]}"; do
    require_block_line \
      "$compatibility_step" \
      "$required_line" \
      "API $api_level compatibility contract line '$required_line'"
  done
done

required_compatibility_runner_lines=(
  '  30|31|36) ;;'
  'RESULTS_MARKER_RELATIVE="build/reports/android-migration-results/compatibility-start.marker"'
  'rm -rf common/build/outputs/androidTest-results/connected'
  'rm -rf core-db/build/outputs/androidTest-results/connected'
  'rm -rf app/build/outputs/androidTest-results/connected'
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
  "./gradlew :common:connectedDebugAndroidTest \\"
  "  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilAndroidKeyStoreTest \\"
  "./gradlew :core-db:connectedDebugAndroidTest \\"
  "  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.coredb.migrations.ReleasedSchemaUpgradeMatrixTest,jp.co.soramitsu.coredb.migrations.ReleasedVersion27FailClosedMigrationTest \\"
  "./gradlew :app:connectedDebugAndroidTest \\"
  "  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.app.root.presentation.WalletGateActivityLifecycleTest,jp.co.soramitsu.app.root.presentation.WalletSecureStorageRestartActivityLifecycleTest,jp.co.soramitsu.app.root.presentation.SecurityWarningRestorationTest \\"
  "MIGRATION_RESULTS_PROFILE=compatibility \\"
  '  bash ./scripts/verify-android-migration-instrumentation-results.sh'
  "evidence=\"build/reports/android-migration-compatibility/api-\$API_LEVEL\""
)
if grep -Eq '(^|[[:space:]])(-x|--exclude-task|--tests)([=[:space:]]|$)|\|\|[[:space:]]*true|set[[:space:]]+\+e' \
  "$COMPATIBILITY_RUNNER"; then
  fail "Android compatibility runner must not filter tests or suppress failures"
fi
for required_line in "${required_compatibility_runner_lines[@]}"; do
  [[ "$(grep -Fxc -- "$required_line" "$COMPATIBILITY_RUNNER" || true)" == "1" ]] ||
    fail "Android compatibility runner line '$required_line' must appear exactly once"
done

compat_marker_line="$(grep -Fnx -- \
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
  "$COMPATIBILITY_RUNNER" | cut -d: -f1 || true)"
[[ "$compat_marker_line" =~ ^[0-9]+$ ]] ||
  fail "Android compatibility cleanup/marker/Gradle order is malformed"
for module in common core-db app; do
  cleanup_line="$(grep -Fnx -- \
    "rm -rf $module/build/outputs/androidTest-results/connected" \
    "$COMPATIBILITY_RUNNER" | cut -d: -f1 || true)"
  [[ "$cleanup_line" =~ ^[0-9]+$ ]] ||
    fail "Android compatibility cleanup/marker/Gradle order is malformed"
  ((cleanup_line < compat_marker_line)) ||
    fail "Android compatibility marker must be created after every cleanup and before Gradle"
done
for module in common core-db app; do
  gradle_line="$(grep -Fnx -- \
    "./gradlew :$module:connectedDebugAndroidTest \\" \
    "$COMPATIBILITY_RUNNER" | cut -d: -f1 || true)"
  [[ "$gradle_line" =~ ^[0-9]+$ ]] ||
    fail "Android compatibility cleanup/marker/Gradle order is malformed"
  ((compat_marker_line < gradle_line)) ||
    fail "Android compatibility marker must be created after every cleanup and before every Gradle invocation"
done

required_full_runner_lines=(
  'RESULTS_MARKER_RELATIVE="build/reports/android-migration-results/full-start.marker"'
  'rm -rf common/build/outputs/androidTest-results/connected'
  'rm -rf core-db/build/outputs/androidTest-results/connected'
  'rm -rf app/build/outputs/androidTest-results/connected'
  'rm -rf feature-account-impl/build/outputs/androidTest-results/connected'
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")"
  "./gradlew \\"
  "  :common:connectedDebugAndroidTest \\"
  "  :core-db:connectedDebugAndroidTest \\"
  "  :app:connectedDebugAndroidTest \\"
  "  :feature-account-impl:connectedDebugAndroidTest \\"
  "  --continue \\"
  "  --no-daemon \\"
  "  --console=plain \\"
  "  --stacktrace"
)
if grep -Eq '(^|[[:space:]])(-x|--exclude-task|--tests)([=[:space:]]|$)' \
  "$FULL_RUNNER"; then
  fail "Android full migration runner must not exclude or filter tests"
fi
if grep -Eq '\|\|[[:space:]]*true|set[[:space:]]+\+e' "$FULL_RUNNER"; then
  fail "Android full migration runner must not suppress command failures"
fi
for required_line in "${required_full_runner_lines[@]}"; do
  [[ "$(grep -Fxc -- "$required_line" "$FULL_RUNNER" || true)" == "1" ]] ||
    fail "Android full migration runner line '$required_line' must appear exactly once"
done

full_marker_line="$(grep -Fnx -- \
  "(umask 077; : >\"\$RESULTS_MARKER_RELATIVE\")" \
  "$FULL_RUNNER" | cut -d: -f1 || true)"
full_gradle_line="$(grep -Fnx -- "./gradlew \\" \
  "$FULL_RUNNER" | cut -d: -f1 || true)"
[[ "$full_marker_line" =~ ^[0-9]+$ && \
  "$full_gradle_line" =~ ^[0-9]+$ ]] ||
  fail "Android full cleanup/marker/Gradle order is malformed"
for module in common core-db app feature-account-impl; do
  cleanup_line="$(grep -Fnx -- \
    "rm -rf $module/build/outputs/androidTest-results/connected" \
    "$FULL_RUNNER" | cut -d: -f1 || true)"
  [[ "$cleanup_line" =~ ^[0-9]+$ ]] ||
    fail "Android full cleanup/marker/Gradle order is malformed"
  ((cleanup_line < full_marker_line)) ||
    fail "Android full marker must be created after every cleanup and before Gradle"
done
((full_marker_line < full_gradle_line)) ||
  fail "Android full marker must be created after every cleanup and before Gradle"

instrumentation_step="$({
  extract_step "$build_job" "Wallet migration and startup instrumentation"
} 2>/dev/null)" || fail "exact migration instrumentation step is missing or duplicated"

required_instrumentation_lines=(
  "        uses: ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2.38.0"
  "          api-level: 34"
  "          target: google_atd"
  "          arch: x86_64"
  "          profile: pixel_6"
  "          cores: 2"
  "          ram-size: 2048M"
  "          heap-size: 512M"
  "          emulator-boot-timeout: 600"
  "          disable-animations: true"
  "          disable-spellchecker: true"
  "          script: bash ./scripts/run-android-migration-full.sh"
)
if grep -Eq '^[[:space:]]+(if|continue-on-error):' <<<"$instrumentation_step"; then
  fail "migration instrumentation step must be unconditional and fail closed"
fi
if grep -Eq '(^|[[:space:]])(-x|--exclude-task|--tests)([=[:space:]]|$)' \
  <<<"$instrumentation_step"; then
  fail "migration instrumentation step must not exclude or filter tests"
fi
if grep -Fq 'android.testInstrumentationRunnerArguments' \
  <<<"$instrumentation_step"; then
  fail "migration instrumentation step must run the complete module suites"
fi
if grep -Eq '\|\|[[:space:]]*true|set[[:space:]]+\+e' \
  <<<"$instrumentation_step"; then
  fail "migration instrumentation step must not suppress command failures"
fi
for required_line in "${required_instrumentation_lines[@]}"; do
  require_block_line \
    "$instrumentation_step" \
    "$required_line" \
    "migration instrumentation contract line '$required_line'"
done

results_step="$({
  extract_step "$build_job" "Verify migration instrumentation results"
} 2>/dev/null)" || fail "exact migration result-verification step is missing or duplicated"
required_result_lines=(
  "        if: always()"
  "        run: bash ./scripts/verify-android-migration-instrumentation-results.sh"
)
if grep -Eq 'continue-on-error:|\|\|[[:space:]]*true|set[[:space:]]+\+e' \
  <<<"$results_step"; then
  fail "migration result-verification step must fail closed"
fi
for required_line in "${required_result_lines[@]}"; do
  require_block_line \
    "$results_step" \
    "$required_line" \
    "migration result-verification contract line '$required_line'"
done

evidence_step="$({
  extract_step "$build_job" "Upload migration instrumentation evidence"
} 2>/dev/null)" || fail "migration instrumentation evidence step is missing or duplicated"
required_evidence_lines=(
  "        if: always()"
  "        uses: actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02 # v4.6.2"
  "          name: android-migration-instrumentation-\${{ github.sha }}"
  "            **/build/outputs/androidTest-results/connected/**"
  "            **/build/reports/androidTests/connected/**"
  "          if-no-files-found: error"
  "          retention-days: 14"
)
for required_line in "${required_evidence_lines[@]}"; do
  require_block_line \
    "$evidence_step" \
    "$required_line" \
    "migration evidence contract line '$required_line'"
done
if grep -Eq 'continue-on-error:' <<<"$evidence_step"; then
  fail "migration instrumentation evidence upload must fail closed"
fi

instrumentation_line="$(
  grep -nF '      - name: Wallet migration and startup instrumentation' \
    "$WORKFLOW" | cut -d: -f1
)"
api30_line="$(
  grep -nF '      - name: Android 11 migration and restart compatibility' \
    "$WORKFLOW" | cut -d: -f1
)"
api31_line="$(
  grep -nF '      - name: Android 12 migration and restart compatibility' \
    "$WORKFLOW" | cut -d: -f1
)"
api36_line="$(
  grep -nF '      - name: Android 16 migration and restart compatibility' \
    "$WORKFLOW" | cut -d: -f1
)"
results_line="$(
  grep -nF '      - name: Verify migration instrumentation results' \
    "$WORKFLOW" | cut -d: -f1
)"
evidence_line="$(
  grep -nF '      - name: Upload migration instrumentation evidence' \
    "$WORKFLOW" | cut -d: -f1
)"
[[ "$api30_line" =~ ^[0-9]+$ && "$api31_line" =~ ^[0-9]+$ && \
  "$api36_line" =~ ^[0-9]+$ && "$instrumentation_line" =~ ^[0-9]+$ && \
  "$results_line" =~ ^[0-9]+$ && "$evidence_line" =~ ^[0-9]+$ ]] ||
  fail "migration instrumentation step ordering is malformed"
((api30_line < api31_line && api31_line < api36_line && \
  api36_line < instrumentation_line)) ||
  fail "API 30/31/36 compatibility shards must precede the full API 34 gate"
((results_line > instrumentation_line)) ||
  fail "migration result verification must follow instrumentation"
((evidence_line > instrumentation_line)) ||
  fail "migration evidence upload must follow the instrumentation run"
((evidence_line > results_line)) ||
  fail "migration evidence upload must follow result verification"

echo \
  "[android-migration-ci] required full migration/startup instrumentation gate verified"
