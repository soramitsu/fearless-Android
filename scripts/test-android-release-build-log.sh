#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-android-release-build-log.sh"

fail() {
  echo "[android-release-build-log-test][error] $*" >&2
  exit 1
}

[[ -f "$VERIFY" && ! -L "$VERIFY" ]] ||
  fail "build-log verifier is missing or unsafe"
for command_name in grep mktemp python3; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/android-release-build-log.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

positive_count=0
negative_count=0

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label did not fail with expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

valid="$tmp_dir/valid.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  '> Task :app:bundleRelease' \
  'BUILD SUCCESSFUL in 7m 12s' >"$valid"
"$VERIFY" "$valid" >/dev/null
positive_count=$((positive_count + 1))

valid_ansi="$tmp_dir/valid-ansi.log"
printf '\033[32m> Task :app:minifyReleaseWithR8\033[0m\r\n> Task :app:bundleRelease\nBUILD SUCCESSFUL in 1m\n' \
  >"$valid_ansi"
"$VERIFY" "$valid_ansi" >/dev/null
positive_count=$((positive_count + 1))

expect_failure \
  "missing log" \
  "Build log must be a non-empty regular, non-symlink file" \
  "$VERIFY" "$tmp_dir/missing.log"

symlink_log="$tmp_dir/symlink.log"
ln -s "$valid" "$symlink_log"
expect_failure \
  "symlink log" \
  "Build log must be a non-empty regular, non-symlink file" \
  "$VERIFY" "$symlink_log"

directory_log="$tmp_dir/directory.log"
mkdir "$directory_log"
expect_failure \
  "directory log" \
  "Build log must be a non-empty regular, non-symlink file" \
  "$VERIFY" "$directory_log"

empty_log="$tmp_dir/empty.log"
: >"$empty_log"
expect_failure \
  "empty log" \
  "Build log must be a non-empty regular, non-symlink file" \
  "$VERIFY" "$empty_log"

oversized_log="$tmp_dir/oversized.log"
python3 - "$oversized_log" <<'PY'
import sys
with open(sys.argv[1], "wb") as output:
    output.seek(134217728)
    output.write(b"x")
PY
expect_failure \
  "oversized log" \
  "Build log exceeds its maximum size" \
  "$VERIFY" "$oversized_log"

canonical_warning="$tmp_dir/canonical-warning.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  'WARNING: R8: An error occurred when parsing kotlin metadata. This normally happens when using a newer version of kotlin.' \
  'BUILD SUCCESSFUL in 7m' >"$canonical_warning"
expect_failure \
  "canonical Kotlin metadata warning" \
  "R8 Kotlin metadata parse warning detected" \
  "$VERIFY" "$canonical_warning"

whitespace_warning="$tmp_dir/whitespace-warning.log"
printf '> Task :app:minifyReleaseWithR8\nwarning: r8: an\terror\n occurred when parsing   Kotlin metadata\nBUILD SUCCESSFUL in 7m\n' \
  >"$whitespace_warning"
expect_failure \
  "whitespace-obfuscated Kotlin metadata warning" \
  "R8 Kotlin metadata parse warning detected" \
  "$VERIFY" "$whitespace_warning"

ansi_warning="$tmp_dir/ansi-warning.log"
printf '> Task :app:minifyReleaseWithR8\nWARNING: R8: An error occurred when \033[31mparsing\033[0m kotlin metadata.\nBUILD SUCCESSFUL in 7m\n' \
  >"$ansi_warning"
expect_failure \
  "ANSI-obfuscated Kotlin metadata warning" \
  "R8 Kotlin metadata parse warning detected" \
  "$VERIFY" "$ansi_warning"

missing_service_warning="$tmp_dir/missing-service-warning.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  'WARNING: R8: Unexpected reference to missing service class: org.apache.xerces.parsers.SAXParser.' \
  'BUILD SUCCESSFUL in 7m' >"$missing_service_warning"
expect_failure \
  "canonical missing ServiceLoader class warning" \
  "R8 missing ServiceLoader class warning detected" \
  "$VERIFY" "$missing_service_warning"

whitespace_service_warning="$tmp_dir/whitespace-service-warning.log"
printf '> Task :app:minifyReleaseWithR8\nwarning: r8: unexpected\treference\n to missing   SERVICE class: attacker.Parser\nBUILD SUCCESSFUL in 7m\n' \
  >"$whitespace_service_warning"
expect_failure \
  "whitespace-obfuscated missing ServiceLoader class warning" \
  "R8 missing ServiceLoader class warning detected" \
  "$VERIFY" "$whitespace_service_warning"

ansi_service_warning="$tmp_dir/ansi-service-warning.log"
printf '> Task :app:minifyReleaseWithR8\nWARNING: R8: Unexpected reference to \033[31mmissing\033[0m service class: attacker.Parser\nBUILD SUCCESSFUL in 7m\n' \
  >"$ansi_service_warning"
expect_failure \
  "ANSI-obfuscated missing ServiceLoader class warning" \
  "R8 missing ServiceLoader class warning detected" \
  "$VERIFY" "$ansi_service_warning"

failed_build="$tmp_dir/failed-build.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  'BUILD FAILED in 2m' >"$failed_build"
expect_failure \
  "failed Gradle build" \
  "Gradle reported a failed production build" \
  "$VERIFY" "$failed_build"

missing_success="$tmp_dir/missing-success.log"
printf '%s\n' '> Task :app:minifyReleaseWithR8' >"$missing_success"
expect_failure \
  "missing Gradle success marker" \
  "does not contain a Gradle success marker" \
  "$VERIFY" "$missing_success"

missing_r8_task="$tmp_dir/missing-r8-task.log"
printf '%s\n' '> Task :app:bundleRelease' 'BUILD SUCCESSFUL in 2m' \
  >"$missing_r8_task"
expect_failure \
  "missing release R8 task" \
  "does not prove execution of :app:minifyReleaseWithR8" \
  "$VERIFY" "$missing_r8_task"

for outcome in FROM-CACHE UP-TO-DATE SKIPPED NO-SOURCE; do
  skipped_log="$tmp_dir/r8-$outcome.log"
  printf '%s\n' \
    "> Task :app:minifyReleaseWithR8 $outcome" \
    'BUILD SUCCESSFUL in 2m' >"$skipped_log"
  expect_failure \
    "release R8 task $outcome" \
    "release R8 task was cached, up-to-date, skipped, or had no source" \
    "$VERIFY" "$skipped_log"
done

mixed_cached_log="$tmp_dir/r8-mixed-cached.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  '> Task :app:minifyReleaseWithR8 FROM-CACHE' \
  'BUILD SUCCESSFUL in 2m' >"$mixed_cached_log"
expect_failure \
  "mixed executed and cached R8 task records" \
  "release R8 task was cached, up-to-date, skipped, or had no source" \
  "$VERIFY" "$mixed_cached_log"

r8_decoy_log="$tmp_dir/r8-task-decoy.log"
printf '%s\n' \
  'WARNING: an attacker mentioned :app:minifyReleaseWithR8 in prose' \
  'BUILD SUCCESSFUL in 2m' >"$r8_decoy_log"
expect_failure \
  "R8 task-name prose decoy" \
  "does not prove execution of :app:minifyReleaseWithR8" \
  "$VERIFY" "$r8_decoy_log"

duplicate_executed_r8_log="$tmp_dir/r8-duplicate-executed.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  '> Task :app:minifyReleaseWithR8' \
  '> Task :app:bundleRelease' \
  'BUILD SUCCESSFUL in 2m' >"$duplicate_executed_r8_log"
expect_failure \
  "duplicate executed R8 task records" \
  "exactly one executed release R8 task marker" \
  "$VERIFY" "$duplicate_executed_r8_log"

missing_bundle_log="$tmp_dir/missing-bundle.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  'BUILD SUCCESSFUL in 2m' >"$missing_bundle_log"
expect_failure \
  "missing bundleRelease task" \
  "does not prove one executed :app:bundleRelease task" \
  "$VERIFY" "$missing_bundle_log"

cached_bundle_log="$tmp_dir/cached-bundle.log"
printf '%s\n' \
  '> Task :app:minifyReleaseWithR8' \
  '> Task :app:bundleRelease FROM-CACHE' \
  'BUILD SUCCESSFUL in 2m' >"$cached_bundle_log"
expect_failure \
  "cached bundleRelease task" \
  "does not prove one executed :app:bundleRelease task" \
  "$VERIFY" "$cached_bundle_log"

expect_failure \
  "newline in build-log path" \
  "Build-log path is malformed" \
  "$VERIFY" "$valid"$'\nredirected'

nul_log="$tmp_dir/nul.log"
printf '> Task :app:minifyReleaseWithR8\000\nBUILD SUCCESSFUL in 2m\n' >"$nul_log"
expect_failure \
  "NUL in build log" \
  "Build log contains a NUL byte" \
  "$VERIFY" "$nul_log"

invalid_utf8_log="$tmp_dir/invalid-utf8.log"
printf '> Task :app:minifyReleaseWithR8\n\377\nBUILD SUCCESSFUL in 2m\n' \
  >"$invalid_utf8_log"
expect_failure \
  "invalid UTF-8 build log" \
  "Build log is not valid UTF-8" \
  "$VERIFY" "$invalid_utf8_log"

[[ "$positive_count" == "2" ]] ||
  fail "expected 2 positive cases; got $positive_count"
[[ "$negative_count" == "26" ]] ||
  fail "expected 26 negative cases; got $negative_count"

echo \
  "[android-release-build-log-test] $positive_count positive + $negative_count negative/adversarial cases passed"
