#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(
  mktemp -d "$ROOT_DIR/build/test-tmp/android-unsigned-release-build.XXXXXX"
)"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-unsigned-release-build-test][error] $*" >&2
  exit 1
}

require_text() {
  local path="$1"
  local text="$2"
  grep -Fq "$text" "$path" ||
    fail "$path is missing required unsigned-release contract text: $text"
}

expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"

  if "$@" > "$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$label omitted its fail-closed diagnostic"
  }
  negative_count=$((negative_count + 1))
}

cd "$ROOT_DIR"

require_text app/build.gradle \
  'def unsignedReleaseBuildValue = System.getenv("ANDROID_UNSIGNED_RELEASE_BUILD")'
require_text app/build.gradle \
  "ANDROID_UNSIGNED_RELEASE_BUILD=true permits only an exact, "
require_text app/build.gradle \
  "Unsigned release bundling forbids every release keystore and password input."
require_text app/build.gradle \
  "Unsigned release bundling forbids injected Android signing properties."
require_text app/build.gradle \
  "Unsigned release AAB verified as an intermediate only"
require_text app/build.gradle \
  "external certificate-pinned signing is still required."
require_text app/build.gradle \
  'requestedTasks != [":app:bundleRelease"]'
require_text app/build.gradle \
  'android.buildTypes.getByName("release").signingConfig != null'
require_text app/build.gradle \
  "RELEASE_COMMIT is required whenever the resolved task graph can "
require_text app/build.gradle \
  "if (!releaseArtifactTasks.isEmpty() && !unsignedReleaseBuild)"

source_commit=1111111111111111111111111111111111111111
unsigned_environment=(
  env
  -u CI_KEYSTORE_PATH
  -u CI_KEYSTORE_PASS
  -u CI_KEYSTORE_KEY_ALIAS
  -u CI_KEYSTORE_KEY_PASS
  CI=true
  ANDROID_UNSIGNED_RELEASE_BUILD=true
  RELEASE_COMMIT="$source_commit"
)

positive_count=0
negative_count=0

env \
  -u ANDROID_UNSIGNED_RELEASE_BUILD \
  -u RELEASE_COMMIT \
  ./gradlew :app:help --no-daemon --console=plain >/dev/null
positive_count=$((positive_count + 1))

"${unsigned_environment[@]}" \
  ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain >/dev/null
positive_count=$((positive_count + 1))

for invalid_value in false TRUE 1 ' true '; do
  expect_failure \
    "invalid unsigned flag $invalid_value" \
    "ANDROID_UNSIGNED_RELEASE_BUILD must be unset or exactly true" \
    env \
      ANDROID_UNSIGNED_RELEASE_BUILD="$invalid_value" \
      ./gradlew :app:help --no-daemon --console=plain
done

expect_failure \
  "unsigned release without explicit CI" \
  "restricted to an explicit CI environment" \
  env \
    -u CI \
    -u GITHUB_ACTIONS \
    ANDROID_UNSIGNED_RELEASE_BUILD=true \
    RELEASE_COMMIT="$source_commit" \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain
expect_failure \
  "GitHub marker without explicit CI" \
  "restricted to an explicit CI environment" \
  env \
    -u CI \
    GITHUB_ACTIONS=true \
    ANDROID_UNSIGNED_RELEASE_BUILD=true \
    RELEASE_COMMIT="$source_commit" \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain
expect_failure \
  "unsigned release without source commit" \
  "RELEASE_COMMIT is required whenever the resolved task graph can" \
  env \
    -u RELEASE_COMMIT \
    CI=true \
    ANDROID_UNSIGNED_RELEASE_BUILD=true \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain
expect_failure \
  "unsigned release with malformed source commit" \
  "RELEASE_COMMIT must be an exact lowercase 40-character Git commit" \
  env \
    CI=true \
    ANDROID_UNSIGNED_RELEASE_BUILD=true \
    RELEASE_COMMIT=ABC \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain

for task in \
  :app:help \
  :app:bunRel \
  :app:assembleRelease \
  :app:bundle \
  :app:signReleaseBundle; do
  expect_failure \
    "unsigned release alternate task $task" \
    "permits only an exact, source-bound :app:bundleRelease invocation" \
    "${unsigned_environment[@]}" \
    ./gradlew "$task" --dry-run --no-daemon --console=plain
done
expect_failure \
  "removed Gradle Play Publisher task" \
  "task 'publishReleaseBundle' not found in project ':app'" \
  env \
    -u ANDROID_UNSIGNED_RELEASE_BUILD \
    -u RELEASE_COMMIT \
    ./gradlew :app:publishReleaseBundle --dry-run --no-daemon --console=plain
expect_failure \
  "unsigned release with an extra task" \
  "permits only an exact, source-bound :app:bundleRelease invocation" \
  "${unsigned_environment[@]}" \
  ./gradlew :app:bundleRelease :app:help --dry-run --no-daemon --console=plain
expect_failure \
  "unsigned release with a non-qualified requested task" \
  "permits only an exact, source-bound :app:bundleRelease invocation" \
  "${unsigned_environment[@]}" \
  ./gradlew bundleRelease --dry-run --no-daemon --console=plain

for signing_assignment in \
  "CI_KEYSTORE_PATH=$tmp_dir/missing.jks" \
  "CI_KEYSTORE_PASS=store-password" \
  "CI_KEYSTORE_KEY_ALIAS=upload" \
  "CI_KEYSTORE_KEY_PASS=key-password"; do
  expect_failure \
    "unsigned release with signing input ${signing_assignment%%=*}" \
    "forbids every release keystore and password input" \
    "${unsigned_environment[@]}" \
    "$signing_assignment" \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain
done

for injected_signing_property in \
  android.injected.signing.store.file \
  android.injected.signing.store.password \
  android.injected.signing.key.alias \
  android.injected.signing.key.password; do
  expect_failure \
    "unsigned release with injected signing property $injected_signing_property" \
    "forbids injected Android signing properties" \
    "${unsigned_environment[@]}" \
    ./gradlew \
      "-P$injected_signing_property=test" \
      :app:bundleRelease \
      --dry-run --no-daemon --console=plain
done

expect_failure \
  "normal signed release without signing inputs" \
  "Release signing is incomplete" \
  env \
    -u ANDROID_UNSIGNED_RELEASE_BUILD \
    -u CI_KEYSTORE_PATH \
    -u CI_KEYSTORE_PASS \
    -u CI_KEYSTORE_KEY_ALIAS \
    -u CI_KEYSTORE_KEY_PASS \
    RELEASE_COMMIT="$source_commit" \
    ./gradlew :app:bundleRelease --dry-run --no-daemon --console=plain

[[ "$positive_count" == "2" ]] ||
  fail "expected 2 positive cases; got $positive_count"
[[ "$negative_count" == "25" ]] ||
  fail "expected 25 negative/adversarial cases; got $negative_count"

echo \
  "[android-unsigned-release-build-test] $positive_count positive + $negative_count negative/adversarial task-graph cases passed"
