#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROJECT_ROOT="${ANDROID_TEST_TASK_AUDIT_PROJECT_ROOT:-$ROOT_DIR}"
BUILD_FILE="${ANDROID_TEST_TASK_AUDIT_BUILD_FILE:-$ROOT_DIR/build.gradle}"
WORKFLOW_FILE="${ANDROID_TEST_TASK_AUDIT_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/android-ci.yml}"

fail() {
  echo "[unit-test-task-audit][error] $*" >&2
  exit 1
}

require_active_workflow_text() {
  local value="$1"
  local message="$2"
  grep -F -- "$value" "$WORKFLOW_FILE" |
    grep -Eq '^[[:space:]]*[^#[:space:]]' || fail "$message"
}

[[ -f "$BUILD_FILE" ]] || fail "Missing Gradle build file: $BUILD_FILE"
[[ -f "$WORKFLOW_FILE" ]] || fail "Missing Android CI workflow: $WORKFLOW_FILE"
[[ -d "$PROJECT_ROOT" ]] || fail "Missing Android project root: $PROJECT_ROOT"

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT
task_file="$tmp_dir/unit-test-tasks.txt"
staged_task_file="$tmp_dir/staged-iroha-unit-test-tasks.txt"
all_task_file="$tmp_dir/all-unit-test-tasks.txt"
source_task_file="$tmp_dir/source-unit-test-tasks.txt"

parse_task_list() {
  local list_name="$1"
  local output="$2"
  awk -v target="$list_name" '
  $0 ~ "^[[:space:]]*def[[:space:]]+" target "[[:space:]]*=[[:space:]]*\\[" {
    if (seen) exit 42
    seen = 1
    in_list = 1
    next
  }
  in_list && /^[[:space:]]*\]/ {
    closed = 1
    in_list = 0
    next
  }
  in_list {
    line = $0
    sub(/[[:space:]]*\/\/.*$/, "", line)
    if (line ~ /^[[:space:]]*$/) next
    if (line !~ /^[[:space:]]*\047[^\047]+\047[[:space:]]*,?[[:space:]]*$/) exit 44
    sub(/^[[:space:]]*\047/, "", line)
    sub(/\047[[:space:]]*,?[[:space:]]*$/, "", line)
    print line
  }
  END {
    if (!seen || !closed) exit 43
  }
' "$BUILD_FILE" > "$output" || fail "Could not parse one closed $list_name list from $BUILD_FILE"
}

parse_task_list unitTestTaskPaths "$task_file"
parse_task_list stagedIrohaTestTaskPaths "$staged_task_file"

[[ -s "$task_file" ]] || fail "unitTestTaskPaths is empty"
[[ -s "$staged_task_file" ]] || fail "stagedIrohaTestTaskPaths is empty"

cat "$task_file" "$staged_task_file" | sort > "$all_task_file"

duplicates="$(uniq -d "$all_task_file" || true)"
[[ -z "$duplicates" ]] || fail "Duplicate unit-test task(s): $(printf '%s' "$duplicates" | tr '\n' ' ')"

while IFS= read -r task; do
  [[ "$task" =~ ^:([A-Za-z0-9_.-]+:)*[A-Za-z0-9_.-]+:testDebugUnitTest$ ]] ||
    fail "Unsupported unit-test task path: $task"
done < "$task_file"

expected_staged_tasks=(
  ':iroha-sdk-bridge:test'
  ':iroha-sdk-bridge-kotlin-smoke:test'
)
while IFS= read -r task; do
  [[ "$task" =~ ^:iroha-sdk-bridge(-kotlin-smoke)?:test$ ]] ||
    fail "Unsupported staged Iroha unit-test task path: $task"
done < "$staged_task_file"
for task in "${expected_staged_tasks[@]}"; do
  count="$(grep -Fxc -- "$task" "$staged_task_file" || true)"
  [[ "$count" == "1" ]] || fail "Staged Iroha task must appear exactly once: $task (found $count)"
done
[[ "$(wc -l < "$staged_task_file" | tr -d '[:space:]')" == "${#expected_staged_tasks[@]}" ]] ||
  fail "stagedIrohaTestTaskPaths must contain only the two audited bridge tasks"

baseline_required_tasks=(
  ':app:testDebugUnitTest'
  ':common:testDebugUnitTest'
  ':core-api:testDebugUnitTest'
  ':core-db:testDebugUnitTest'
  ':feature-account-impl:testDebugUnitTest'
  ':feature-crowdloan-impl:testDebugUnitTest'
  ':feature-onboarding-impl:testDebugUnitTest'
  ':feature-staking-impl:testDebugUnitTest'
  ':feature-tonconnect-api:testDebugUnitTest'
  ':feature-wallet-impl:testDebugUnitTest'
  ':feature-walletconnect-impl:testDebugUnitTest'
  ':public-shared-features-backup:testDebugUnitTest'
  ':public-shared-features-xcm:testDebugUnitTest'
  ':runtime:testDebugUnitTest'
)

for task in "${baseline_required_tasks[@]}"; do
  count="$(grep -Fxc -- "$task" "$task_file" || true)"
  [[ "$count" == "1" ]] || fail "Baseline task must appear exactly once: $task (found $count)"
done

while IFS= read -r -d '' source_file; do
  relative="${source_file#"$PROJECT_ROOT"/}"
  module_path="${relative%%/src/test/*}"
  [[ "$module_path" != "$relative" && -n "$module_path" ]] ||
    fail "Could not resolve Gradle module for unit test source: $source_file"
  [[ -f "$PROJECT_ROOT/$module_path/build.gradle" || -f "$PROJECT_ROOT/$module_path/build.gradle.kts" ]] ||
    fail "Unit test source has no Gradle module build file: $source_file"
  case "$module_path" in
    iroha-sdk-bridge|iroha-sdk-bridge-kotlin-smoke)
      printf ':%s:test\n' "${module_path//\//:}"
      ;;
    *)
      printf ':%s:testDebugUnitTest\n' "${module_path//\//:}"
      ;;
  esac
done < <(
  find -P "$PROJECT_ROOT" -type f \
    \( -name '*.kt' -o -name '*.java' \) \
    -path '*/src/test/*' \
    ! -path "$PROJECT_ROOT/.gradle/*" \
    ! -path '*/build/*' \
    ! -path "$PROJECT_ROOT/fearless-utils-Android/*" \
    -print0
) | sort -u > "$source_task_file"

[[ -s "$source_task_file" ]] || fail "No Android unit-test sources were discovered under $PROJECT_ROOT."

missing_source_tasks="$(comm -23 "$source_task_file" "$all_task_file" || true)"
[[ -z "$missing_source_tasks" ]] ||
  fail "runTest omits module task(s) with unit-test sources: $(printf '%s' "$missing_source_tasks" | tr '\n' ' ')"

stale_declared_tasks="$(comm -13 "$source_task_file" "$all_task_file" || true)"
[[ -z "$stale_declared_tasks" ]] ||
  fail "runTest declares stale task(s) without unit-test sources: $(printf '%s' "$stale_declared_tasks" | tr '\n' ' ')"

require_active_workflow_text \
  'bash ./scripts/test-unit-test-task-membership-audit.sh' \
  'Android CI must run the adversarial unit-test task audit self-test.'
require_active_workflow_text \
  'bash ./scripts/audit-unit-test-task-membership.sh' \
  'Android CI must audit the real Gradle unit-test task membership.'
require_active_workflow_text \
  'bash ./scripts/verify-staged-iroha-core-bridge.sh' \
  'Android CI must run the explicit staged Iroha bridge gate.'
grep -Eq '^[[:space:]]*(-[[:space:]]+)?(run:[[:space:]]*)?\./gradlew[[:space:]]+runTest([[:space:]]|$)' "$WORKFLOW_FILE" ||
  fail "Android CI must execute ./gradlew runTest."

task_count="$(wc -l < "$all_task_file" | tr -d '[:space:]')"
echo "[unit-test-task-audit] passed ($task_count source-backed module tasks, ${#baseline_required_tasks[@]} Android baseline tasks, ${#expected_staged_tasks[@]} staged Iroha tasks)"
