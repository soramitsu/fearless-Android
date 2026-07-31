#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUDIT="$ROOT_DIR/scripts/audit-unit-test-task-membership.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[unit-test-task-audit-test][error] $*" >&2
  exit 1
}

write_valid_build() {
  rm -rf "$tmp_dir/project"
  mkdir -p "$tmp_dir/project"
  cat > "$tmp_dir/build.gradle" <<'GRADLE'
def unitTestTaskPaths = [
  ':app:testDebugUnitTest',
  ':common:testDebugUnitTest',
  ':core-api:testDebugUnitTest',
  ':core-db:testDebugUnitTest',
  ':feature-account-impl:testDebugUnitTest',
  ':feature-crowdloan-impl:testDebugUnitTest',
  ':feature-onboarding-impl:testDebugUnitTest',
  ':feature-staking-impl:testDebugUnitTest',
  ':feature-tonconnect-api:testDebugUnitTest',
  ':feature-wallet-impl:testDebugUnitTest',
  ':feature-walletconnect-impl:testDebugUnitTest',
  ':public-shared-features-backup:testDebugUnitTest',
  ':public-shared-features-xcm:testDebugUnitTest',
  ':runtime:testDebugUnitTest'
]
def stagedIrohaTestTaskPaths = [
  ':iroha-sdk-bridge:test',
  ':iroha-sdk-bridge-kotlin-smoke:test'
]
GRADLE

  local task module
  while IFS= read -r task; do
    module="${task#:}"
    module="${module%:testDebugUnitTest}"
    module="${module//:/\/}"
    mkdir -p "$tmp_dir/project/$module/src/test/java/fixture"
    : > "$tmp_dir/project/$module/build.gradle"
    : > "$tmp_dir/project/$module/src/test/java/fixture/SmokeTest.kt"
  done < <(sed -n "s/^[[:space:]]*'\([^']*:testDebugUnitTest\)',\{0,1\}[[:space:]]*$/\1/p" "$tmp_dir/build.gradle")

  for module in iroha-sdk-bridge iroha-sdk-bridge-kotlin-smoke; do
    mkdir -p "$tmp_dir/project/$module/src/test/java/fixture"
    : > "$tmp_dir/project/$module/build.gradle"
    : > "$tmp_dir/project/$module/src/test/java/fixture/SmokeTest.java"
  done
}

write_valid_workflow() {
  cat > "$tmp_dir/android-ci.yml" <<'YAML'
steps:
  - run: |
      bash ./scripts/test-unit-test-task-membership-audit.sh
      bash ./scripts/audit-unit-test-task-membership.sh
      bash ./scripts/verify-staged-iroha-core-bridge.sh
  - run: ./gradlew runTest --no-daemon
YAML
}

run_audit() {
  ANDROID_TEST_TASK_AUDIT_BUILD_FILE="$tmp_dir/build.gradle" \
    ANDROID_TEST_TASK_AUDIT_WORKFLOW_FILE="$tmp_dir/android-ci.yml" \
    ANDROID_TEST_TASK_AUDIT_PROJECT_ROOT="$tmp_dir/project" \
    "$AUDIT" >/dev/null 2>&1
}

comment_workflow_line() {
  local needle="$1"
  awk -v needle="$needle" '
    index($0, needle) != 0 { print "# " $0; next }
    { print }
  ' "$tmp_dir/android-ci.yml" > "$tmp_dir/android-ci.next"
  mv "$tmp_dir/android-ci.next" "$tmp_dir/android-ci.yml"
}

expect_failure() {
  local label="$1"
  if run_audit; then
    fail "$label was accepted"
  fi
}

write_valid_build
write_valid_workflow
if ! run_audit; then
  ANDROID_TEST_TASK_AUDIT_BUILD_FILE="$tmp_dir/build.gradle" \
    ANDROID_TEST_TASK_AUDIT_WORKFLOW_FILE="$tmp_dir/android-ci.yml" \
    ANDROID_TEST_TASK_AUDIT_PROJECT_ROOT="$tmp_dir/project" \
    "$AUDIT" || true
  fail "valid fixture was rejected"
fi

sed -i.bak "/core-api:testDebugUnitTest/d" "$tmp_dir/build.gradle"
expect_failure "missing core API module test"

write_valid_build
sed -i.bak "/public-shared-features-backup:testDebugUnitTest/d" "$tmp_dir/build.gradle"
expect_failure "missing backup module test"

write_valid_build
sed -i.bak "/public-shared-features-xcm:testDebugUnitTest/d" "$tmp_dir/build.gradle"
expect_failure "missing XCM module test"

write_valid_build
sed -i.bak "/iroha-sdk-bridge-kotlin-smoke:test/d" "$tmp_dir/build.gradle"
expect_failure "missing staged Iroha Kotlin isolation test"

write_valid_build
sed -i.bak "/':iroha-sdk-bridge:test'/d" "$tmp_dir/build.gradle"
expect_failure "missing staged Iroha Java bridge test"

write_valid_build
sed -i.bak "/:runtime:testDebugUnitTest/a\\
  ':public-shared-features-backup:testDebugUnitTest'," "$tmp_dir/build.gradle"
expect_failure "duplicate module test"

write_valid_build
mkdir -p "$tmp_dir/project/future-module/src/test/java/fixture"
: > "$tmp_dir/project/future-module/build.gradle.kts"
: > "$tmp_dir/project/future-module/src/test/java/fixture/FutureTest.kt"
expect_failure "new source-backed module omitted from runTest"

write_valid_build
sed -i.bak "/feature-walletconnect-impl:testDebugUnitTest/d" "$tmp_dir/build.gradle"
rm -rf "$tmp_dir/project/feature-walletconnect-impl"
expect_failure "baseline test module removed together with its task"

write_valid_build
sed -i.bak "s#  ':core-api:testDebugUnitTest',#  // ':core-api:testDebugUnitTest',#" "$tmp_dir/build.gradle"
expect_failure "commented-out core API test task"

write_valid_build
sed -i.bak "/:runtime:testDebugUnitTest/i\\
  ':core-api:check'," "$tmp_dir/build.gradle"
expect_failure "non-unit-test task injected into runTest list"

write_valid_build
sed -i.bak "/:runtime:testDebugUnitTest/a\\
  ':feature-success-impl:testDebugUnitTest'," "$tmp_dir/build.gradle"
expect_failure "stale declared task without test sources"

write_valid_build
write_valid_workflow
sed -i.bak "/test-unit-test-task-membership-audit.sh/d" "$tmp_dir/android-ci.yml"
expect_failure "CI without adversarial self-test"

write_valid_workflow
sed -i.bak "/audit-unit-test-task-membership.sh/d" "$tmp_dir/android-ci.yml"
expect_failure "CI without real membership audit"

write_valid_workflow
comment_workflow_line 'bash ./scripts/audit-unit-test-task-membership.sh'
expect_failure "CI with commented-out membership audit"

write_valid_workflow
sed -i.bak "/gradlew runTest/d" "$tmp_dir/android-ci.yml"
expect_failure "CI without runTest"

write_valid_workflow
sed -i.bak "/verify-staged-iroha-core-bridge.sh/d" "$tmp_dir/android-ci.yml"
expect_failure "CI without staged Iroha bridge gate"

write_valid_workflow
sed -i.bak 's#      bash ./scripts/audit-unit-test-task-membership.sh#      echo ./gradlew runTest\n      bash ./scripts/audit-unit-test-task-membership.sh#' "$tmp_dir/android-ci.yml"
sed -i.bak '/run: \.\/gradlew runTest/d' "$tmp_dir/android-ci.yml"
expect_failure "CI that only echoes runTest"

write_valid_build
write_valid_workflow
printf "\ndef unitTestTaskPaths = [':app:testDebugUnitTest']\n" >> "$tmp_dir/build.gradle"
expect_failure "second shadow task list"

echo "[unit-test-task-audit-test] all adversarial fixtures passed"
