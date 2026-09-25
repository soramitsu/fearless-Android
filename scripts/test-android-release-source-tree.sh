#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
VERIFY_SOURCE="$ROOT_DIR/scripts/verify-android-release-source-tree.sh"
mkdir -p "$ROOT_DIR/build/test-tmp"
tmp_dir="$(mktemp -d "$ROOT_DIR/build/test-tmp/android-release-source-tree.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-release-source-tree-test][error] $*" >&2
  exit 1
}

for command_name in find git grep mktemp; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
[[ ! -L "$VERIFY_SOURCE" && -f "$VERIFY_SOURCE" ]] ||
  fail "release source-tree verifier is missing or unsafe"

fixture_source="$tmp_dir/source"
mkdir "$fixture_source"
git -C "$fixture_source" init --quiet
git -C "$fixture_source" config user.name "Fearless test"
git -C "$fixture_source" config user.email "fearless-test@example.invalid"
cat >"$fixture_source/.gitignore" <<'IGNORE'
.gradle
.kotlin/
build/
**/build/
local.properties
*.log
*.jks
.release-overlays/
IGNORE
printf 'approved source\n' >"$fixture_source/source.txt"
git -C "$fixture_source" add .gitignore source.txt
git -C "$fixture_source" commit --quiet -m "fixture"

clone_case() {
  local name="$1"
  local destination="$tmp_dir/$name"
  git clone --quiet --no-hardlinks "$fixture_source" "$destination"
  printf '%s' "$destination"
}

run_verifier() {
  local fixture="$1"
  shift
  local commit
  local tree
  commit="$(git -C "$fixture" rev-parse HEAD)"
  tree="$(git -C "$fixture" rev-parse "HEAD^{tree}")"
  env \
    CI=false \
    ANDROID_RELEASE_SOURCE_ROOT="$fixture" \
    "$VERIFY_SOURCE" "$commit" "$tree" "$@"
}

positive_count=0
negative_count=0

clean_fixture="$(clone_case clean)"
run_verifier "$clean_fixture" >/dev/null
positive_count=$((positive_count + 1))

generated_fixture="$(clone_case generated)"
mkdir -p \
  "$generated_fixture/.gradle/cache" \
  "$generated_fixture/.kotlin/errors" \
  "$generated_fixture/build/reports" \
  "$generated_fixture/module/build/generated"
printf 'generated\n' >"$generated_fixture/.gradle/cache/state"
printf 'generated\n' >"$generated_fixture/.kotlin/errors/compiler.log"
printf 'generated\n' >"$generated_fixture/build/reports/report.txt"
printf 'generated\n' >"$generated_fixture/module/build/generated/output.bin"
run_verifier "$generated_fixture" >/dev/null
positive_count=$((positive_count + 1))

utils_fixture="$(clone_case utils)"
mkdir "$utils_fixture/fearless-utils-Android"
git -C "$utils_fixture/fearless-utils-Android" init --quiet
git -C "$utils_fixture/fearless-utils-Android" config \
  user.name "Fearless test"
git -C "$utils_fixture/fearless-utils-Android" config \
  user.email "fearless-test@example.invalid"
printf 'pinned utils\n' >"$utils_fixture/fearless-utils-Android/source.txt"
git -C "$utils_fixture/fearless-utils-Android" add source.txt
git -C "$utils_fixture/fearless-utils-Android" commit --quiet -m "utils"
run_verifier "$utils_fixture" --allow-fearless-utils >/dev/null
positive_count=$((positive_count + 1))

negative_count=0
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

ci_override_fixture="$(clone_case ci-override)"
ci_override_commit="$(git -C "$ci_override_fixture" rev-parse HEAD)"
ci_override_tree="$(git -C "$ci_override_fixture" rev-parse 'HEAD^{tree}')"
expect_failure \
  "source-root override in CI" \
  "ANDROID_RELEASE_SOURCE_ROOT cannot override the checkout in CI" \
  env CI=true ANDROID_RELEASE_SOURCE_ROOT="$ci_override_fixture" \
    "$VERIFY_SOURCE" \
    "$ci_override_commit" \
    "$ci_override_tree"

wrong_commit_fixture="$(clone_case wrong-commit)"
wrong_commit_tree="$(git -C "$wrong_commit_fixture" rev-parse 'HEAD^{tree}')"
expect_failure \
  "wrong commit" \
  "Checkout commit changed" \
  env CI=false ANDROID_RELEASE_SOURCE_ROOT="$wrong_commit_fixture" \
    "$VERIFY_SOURCE" \
    0000000000000000000000000000000000000000 \
    "$wrong_commit_tree"

wrong_tree_fixture="$(clone_case wrong-tree)"
wrong_tree_commit="$(git -C "$wrong_tree_fixture" rev-parse HEAD)"
expect_failure \
  "wrong tree" \
  "Checkout tree changed" \
  env CI=false ANDROID_RELEASE_SOURCE_ROOT="$wrong_tree_fixture" \
    "$VERIFY_SOURCE" \
    "$wrong_tree_commit" \
    0000000000000000000000000000000000000000

tracked_fixture="$(clone_case tracked)"
printf 'mutation\n' >>"$tracked_fixture/source.txt"
expect_failure \
  "tracked source mutation" \
  "working-tree changes" \
  run_verifier "$tracked_fixture"

staged_fixture="$(clone_case staged)"
printf 'mutation\n' >>"$staged_fixture/source.txt"
git -C "$staged_fixture" add source.txt
expect_failure \
  "staged source mutation" \
  "staged changes" \
  run_verifier "$staged_fixture"

untracked_fixture="$(clone_case untracked)"
printf 'mutation\n' >"$untracked_fixture/injected.gradle"
expect_failure \
  "untracked source" \
  "Untracked release source is present" \
  run_verifier "$untracked_fixture"

for ignored_path in \
  local.properties \
  attacker.log \
  release.jks \
  .release-overlays/residual-secret; do
  case_name="${ignored_path//[^A-Za-z0-9]/-}"
  ignored_fixture="$(clone_case "ignored-$case_name")"
  mkdir -p "$(dirname "$ignored_fixture/$ignored_path")"
  printf 'ignored mutation\n' >"$ignored_fixture/$ignored_path"
  expect_failure \
    "ignored source-like file $ignored_path" \
    "Ignored file is not an approved generated output" \
    run_verifier "$ignored_fixture"
done

unexpected_utils_fixture="$(clone_case unexpected-utils)"
mkdir "$unexpected_utils_fixture/fearless-utils-Android"
expect_failure \
  "unexpected utils checkout" \
  "Unexpected nested fearless-utils-Android checkout" \
  run_verifier "$unexpected_utils_fixture"

fake_utils_fixture="$(clone_case fake-utils)"
mkdir "$fake_utils_fixture/fearless-utils-Android"
expect_failure \
  "fake utils checkout" \
  "missing, unsafe, or not a regular checkout" \
  run_verifier "$fake_utils_fixture" --allow-fearless-utils

symlink_utils_fixture="$(clone_case symlink-utils)"
ln -s . "$symlink_utils_fixture/fearless-utils-Android"
expect_failure \
  "symlink utils checkout" \
  "missing, unsafe, or not a regular checkout" \
  run_verifier "$symlink_utils_fixture" --allow-fearless-utils

generated_symlink_fixture="$(clone_case generated-symlink)"
generated_symlink_target="$tmp_dir/generated-symlink-target"
mkdir -p "$generated_symlink_target" "$generated_symlink_fixture/build"
printf 'escaped generated output\n' >"$generated_symlink_target/output.bin"
ln -s \
  "$generated_symlink_target" \
  "$generated_symlink_fixture/build/iroha-mobile-sdk"
expect_failure \
  "generated output symlink escape" \
  "Generated output path cannot be or contain a symlink" \
  run_verifier "$generated_symlink_fixture"

[[ "$positive_count" == "3" ]] ||
  fail "expected 3 positive cases; got $positive_count"
[[ "$negative_count" == "14" ]] ||
  fail "expected 14 adversarial cases; got $negative_count"

echo \
  "[android-release-source-tree-test] $positive_count positive + $negative_count adversarial cases passed"
