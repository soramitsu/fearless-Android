#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
ENSURE="$ROOT_DIR/scripts/ensure-fearless-utils.sh"
SOURCE_REPOSITORY="${FEARLESS_UTILS_TEST_SOURCE:-$ROOT_DIR/../fearless-utils-Android}"
test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/fearless-utils-integrity.XXXXXX")"
cleanup() {
  chmod -R u+rwX "$tmp_dir" 2>/dev/null || true
  rm -rf -- "$tmp_dir"
}
trap cleanup EXIT

fail() {
  echo "[fearless-utils-integrity-test][error] $*" >&2
  exit 1
}

for command_name in git grep mktemp; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
[[ ! -L "$ENSURE" && -x "$ENSURE" ]] ||
  fail "ensure-fearless-utils.sh is missing or unsafe"
[[ ! -L "$SOURCE_REPOSITORY" && -d "$SOURCE_REPOSITORY/.git" ]] ||
  fail "FEARLESS_UTILS_TEST_SOURCE must be a regular Git checkout"

expected_commit="$(git -C "$SOURCE_REPOSITORY" rev-parse HEAD)"
[[ "$expected_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "fixture source commit is malformed"

clone_case() {
  local name="$1"
  local destination="$tmp_dir/$name"
  git clone --quiet --no-hardlinks "$SOURCE_REPOSITORY" "$destination"
  printf '%s' "$destination"
}

run_ensure() {
  local fixture="$1"
  local library_only="$2"
  local expected="${3:-$expected_commit}"
  FEARLESS_UTILS_PATH="$fixture" \
    FEARLESS_UTILS_COMMIT="$expected" \
    FEARLESS_UTILS_LIBRARY_ONLY="$library_only" \
    "$ENSURE"
}

positive_count=0
negative_count=0

clean_fixture="$(clone_case clean)"
run_ensure "$clean_fixture" false >/dev/null
positive_count=$((positive_count + 1))

overlay_fixture="$(clone_case overlay)"
run_ensure "$overlay_fixture" true >/dev/null
positive_count=$((positive_count + 1))
run_ensure "$overlay_fixture" true >/dev/null
positive_count=$((positive_count + 1))

mkdir -p \
  "$overlay_fixture/.kotlin/errors" \
  "$overlay_fixture/fearless-utils/build/generated" \
  "$overlay_fixture/sr25519-java/target/debug"
printf 'generated\n' > "$overlay_fixture/.kotlin/errors/compiler.log"
printf 'generated\n' > \
  "$overlay_fixture/fearless-utils/build/generated/output.bin"
printf 'generated\n' > \
  "$overlay_fixture/sr25519-java/target/debug/output.bin"
run_ensure "$overlay_fixture" true >/dev/null
positive_count=$((positive_count + 1))

expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"

  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly passed"
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,100p' "$output" >&2
    fail "$label did not emit expected diagnostic: $diagnostic"
  }
  negative_count=$((negative_count + 1))
}

wrong_commit_fixture="$(clone_case wrong-commit)"
expect_failure \
  "wrong pinned commit" \
  "must be at 0000000000000000000000000000000000000000" \
  run_ensure \
    "$wrong_commit_fixture" \
    true \
    0000000000000000000000000000000000000000

untracked_fixture="$(clone_case untracked-source)"
run_ensure "$untracked_fixture" true >/dev/null
printf 'attacker controlled\n' > "$untracked_fixture/untracked-source.gradle"
expect_failure \
  "untracked source" \
  "effective source tree differs" \
  run_ensure "$untracked_fixture" true

tracked_fixture="$(clone_case tracked-source)"
run_ensure "$tracked_fixture" true >/dev/null
printf '\n// attacker controlled\n' >> "$tracked_fixture/settings.gradle"
expect_failure \
  "tracked source mutation" \
  "effective source tree differs" \
  run_ensure "$tracked_fixture" true

deleted_overlay_fixture="$(clone_case deleted-overlay-source)"
run_ensure "$deleted_overlay_fixture" true >/dev/null
rm \
  "$deleted_overlay_fixture/fearless-utils/src/main/java/jp/co/soramitsu/fearless_utils/wsrpc/request/CoroutinesRequestExecutor.kt"
expect_failure \
  "deleted overlay source" \
  "Unable to apply library-only overlay" \
  run_ensure "$deleted_overlay_fixture" true

ignored_source_fixture="$(clone_case ignored-source)"
mkdir -p "$ignored_source_fixture/sr25519-java"
printf 'unexpected dependency lock\n' > \
  "$ignored_source_fixture/sr25519-java/Cargo.lock"
expect_failure \
  "ignored source-like file" \
  "ignored file outside approved generated-output directories" \
  run_ensure "$ignored_source_fixture" true

symlink_fixture="$(clone_case symlink-source)"
run_ensure "$symlink_fixture" true >/dev/null
ln -s settings.gradle "$symlink_fixture/untracked-source.gradle"
expect_failure \
  "untracked source symlink" \
  "effective source tree differs" \
  run_ensure "$symlink_fixture" true

unexpected_overlay_fixture="$(clone_case unexpected-overlay)"
run_ensure "$unexpected_overlay_fixture" true >/dev/null
expect_failure \
  "overlay present when disabled" \
  "effective source tree differs" \
  run_ensure "$unexpected_overlay_fixture" false

[[ "$positive_count" == "4" ]] ||
  fail "expected 4 positive cases; got $positive_count"
[[ "$negative_count" == "7" ]] ||
  fail "expected 7 adversarial cases; got $negative_count"

echo \
  "[fearless-utils-integrity-test] $positive_count positive + $negative_count adversarial cases passed"
