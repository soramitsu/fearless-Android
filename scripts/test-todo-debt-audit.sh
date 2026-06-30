#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-todo-debt.sh"

fail() {
  echo "[todo-audit-test][error] $*" >&2
  exit 1
}

make_fixture_root() {
  local root="$1"
  mkdir -p "$root/app/src/main/java" "$root/config"
}

run_audit() {
  local root="$1"
  TODO_AUDIT_ROOT="$root" TODO_AUDIT_BASELINE="$root/config/todo-debt-baseline.tsv" bash "$AUDIT_SCRIPT"
}

expect_failure() {
  local name="$1"
  local root="$2"
  local expected="$3"
  local output

  set +e
  output="$(run_audit "$root" 2>&1)"
  local status=$?
  set -e

  if [[ "$status" -eq 0 ]]; then
    echo "$output" >&2
    fail "$name unexpectedly passed"
  fi

  if [[ "$output" != *"$expected"* ]]; then
    echo "$output" >&2
    fail "$name did not report expected text: $expected"
  fi
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

pass_root="$tmp_dir/pass"
make_fixture_root "$pass_root"
printf '%s\n' 'class ExistingDebt { // TODO existing baseline marker' '}' > "$pass_root/app/src/main/java/ExistingDebt.kt"
mkdir -p "$pass_root/fearless-utils-Android/fearless-utils/src/main/java"
printf '%s\n' 'class ExternalCheckoutDebt { // TODO external dependency marker ignored' '    fun crash(): Nothing = TODO("external")' '}' > "$pass_root/fearless-utils-Android/fearless-utils/src/main/java/ExternalCheckoutDebt.kt"
printf '%s\t%s\n' 'app/src/main/java/ExistingDebt.kt' 'class ExistingDebt { // TODO existing baseline marker' > "$pass_root/config/todo-debt-baseline.tsv"
run_audit "$pass_root" >/dev/null
PATH="/usr/bin:/bin:/usr/sbin:/sbin" run_audit "$pass_root" >/dev/null

new_root="$tmp_dir/new-marker"
make_fixture_root "$new_root"
printf '%s\n' 'class ExistingDebt { // TODO existing baseline marker' '}' > "$new_root/app/src/main/java/ExistingDebt.kt"
printf '%s\n' 'class NewDebt { // FIXME new marker must fail' '}' > "$new_root/app/src/main/java/NewDebt.kt"
printf '%s\t%s\n' 'app/src/main/java/ExistingDebt.kt' 'class ExistingDebt { // TODO existing baseline marker' > "$new_root/config/todo-debt-baseline.tsv"
expect_failure "new marker fixture" "$new_root" "New TODO/FIXME/STOPSHIP markers"

stale_root="$tmp_dir/stale-baseline"
make_fixture_root "$stale_root"
printf '%s\n' 'class CleanSource' > "$stale_root/app/src/main/java/CleanSource.kt"
printf '%s\t%s\n' 'app/src/main/java/OldDebt.kt' 'class OldDebt { // TODO deleted marker' > "$stale_root/config/todo-debt-baseline.tsv"
expect_failure "stale baseline fixture" "$stale_root" "Baseline entries no longer exist"

duplicate_root="$tmp_dir/duplicate-baseline"
make_fixture_root "$duplicate_root"
printf '%s\n' 'class DuplicateDebt { // TODO duplicate baseline marker' 'class DuplicateDebt { // TODO duplicate baseline marker' > "$duplicate_root/app/src/main/java/DuplicateDebt.kt"
printf '%s\t%s\n%s\t%s\n' \
  'app/src/main/java/DuplicateDebt.kt' 'class DuplicateDebt { // TODO duplicate baseline marker' \
  'app/src/main/java/DuplicateDebt.kt' 'class DuplicateDebt { // TODO duplicate baseline marker' \
  > "$duplicate_root/config/todo-debt-baseline.tsv"
expect_failure "duplicate baseline fixture" "$duplicate_root" "Duplicate TODO debt baseline entries are forbidden"

executable_root="$tmp_dir/executable"
make_fixture_root "$executable_root"
printf '%s\n' 'class Crashy {' '    fun crash(): Nothing = TODO("boom")' '}' > "$executable_root/app/src/main/java/Crashy.kt"
printf '%s\t%s\n' 'app/src/main/java/Crashy.kt' 'fun crash(): Nothing = TODO("boom")' > "$executable_root/config/todo-debt-baseline.tsv"
expect_failure "executable TODO fixture" "$executable_root" "Executable TODO calls are forbidden"

echo "[todo-audit-test] all tests passed"
