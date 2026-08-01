#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
GUARD="$ROOT_DIR/scripts/ensure-fearless-utils.sh"
tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/fearless-utils-derived-tree-test.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM

fixture="$tmp_dir/fixture"
wallet="$fixture/fearless-Android"
utils="$fixture/fearless-utils-Android"
expected_commit=""

fail() {
  echo "[fearless-utils-derived-tree-test][error] $*" >&2
  if [[ -s "$fixture/guard.log" ]]; then
    sed 's/^/[guard] /' "$fixture/guard.log" >&2
  fi
  exit 1
}

git_identity() {
  git -C "$1" config user.name "Derived Tree Test"
  git -C "$1" config user.email "derived-tree-test@example.invalid"
}

create_fixture() {
  rm -rf "$fixture"
  mkdir -p "$utils/library" "$wallet/scripts"

  git -C "$utils" init -q
  git_identity "$utils"
  git -C "$utils" config core.filemode true
  printf '%s\n' 'base source' > "$utils/library/base.txt"
  printf '%s\n' 'ignored/' > "$utils/.gitignore"
  git -C "$utils" add .
  git -C "$utils" commit -qm "pinned source"
  git -C "$utils" remote add origin https://github.com/soramitsu/fearless-utils-Android.git
  expected_commit="$(git -C "$utils" rev-parse HEAD)"

  printf '%s\n' 'base source with library overlay' > "$utils/library/base.txt"
  printf '%s\n' 'overlay source' > "$utils/library/overlay.txt"
  git -C "$utils" add -N library/overlay.txt
  git -C "$utils" diff --binary > "$wallet/scripts/fearless-utils-library-only.patch"
  git -C "$utils" reset --hard -q HEAD
  git -C "$utils" clean -fdq

  cp "$GUARD" "$wallet/scripts/ensure-fearless-utils.sh"
  chmod +x "$wallet/scripts/ensure-fearless-utils.sh"
  git -C "$wallet" init -q
  git_identity "$wallet"
  git -C "$wallet" add scripts
  git -C "$wallet" commit -qm "commit derived-tree contract"
}

run_guard() {
  FEARLESS_UTILS_PATH="$utils" \
    FEARLESS_UTILS_COMMIT="$expected_commit" \
    FEARLESS_UTILS_REPOSITORY="soramitsu/fearless-utils-Android" \
    FEARLESS_UTILS_LIBRARY_ONLY="${1:-true}" \
    "$wallet/scripts/ensure-fearless-utils.sh" >"$fixture/guard.log" 2>&1
}

expected_fixture_tree() {
  local index_file="$tmp_dir/expected-fixture.index"

  rm -f "$index_file"
  GIT_INDEX_FILE="$index_file" git -C "$utils" read-tree "$expected_commit"
  GIT_INDEX_FILE="$index_file" git -C "$utils" apply \
    --cached --unidiff-zero "$wallet/scripts/fearless-utils-library-only.patch"
  GIT_INDEX_FILE="$index_file" git -C "$utils" write-tree
}

assert_effective_tree_marker() {
  local label="$1"
  local expected_tree="$2"
  local marker="[fearless-utils] Effective source tree $expected_tree"
  local canonical_utils
  local source_marker

  canonical_utils="$(cd "$utils" && pwd -P)"
  source_marker="[fearless-utils] Using $canonical_utils at $expected_commit"

  [[ "$(grep -Fxc "$source_marker" "$fixture/guard.log")" == "1" ]] ||
    fail "$label did not emit exactly one verified source-commit marker"
  [[ "$(grep -Fxc "$marker" "$fixture/guard.log")" == "1" ]] ||
    fail "$label did not emit exactly one verified effective-tree marker"
}

expect_guard_failure() {
  local label="$1"
  local mode="${2:-true}"
  if run_guard "$mode"; then
    fail "$label was accepted"
  fi
}

assert_pristine_source() {
  [[ "$(<"$utils/library/base.txt")" == "base source" ]] ||
    fail "$1 modified the pinned source file"
  [[ ! -e "$utils/library/overlay.txt" ]] ||
    fail "$1 added the overlay to a rejected dirty checkout"
}

write_kotlin_diagnostic() {
  local timestamp="${1:-1785000000000}"
  local path="$utils/.kotlin/errors/errors-$timestamp.log"

  mkdir -p "$utils/.kotlin/errors"
  printf '%s\n' \
    'kotlin version: 2.1.10' \
    'error message: Daemon compilation failed: fixture' \
    'java.lang.Exception' \
    $'\tat org.jetbrains.kotlin.compilerRunner.Fixture.run(Fixture.kt:1)' > "$path"
}

write_kotlin_session_marker() {
  local session_id="${1:-1785000000000000000}"
  local path="$utils/.kotlin/sessions/kotlin-compiler-$session_id.salive"

  mkdir -p "$utils/.kotlin/sessions"
  : > "$path"
}

create_fixture
run_guard false || fail "pristine pinned tree was rejected"
assert_effective_tree_marker \
  "pristine pinned tree" \
  "$(git -C "$utils" rev-parse "$expected_commit^{tree}")"

create_fixture
derived_tree="$(expected_fixture_tree)"
run_guard true || fail "pristine tree could not be converted to the expected derived tree"
assert_effective_tree_marker "newly applied derived tree" "$derived_tree"
[[ "$(<"$utils/library/base.txt")" == "base source with library overlay" ]] ||
  fail "library-only overlay did not update its tracked source"
[[ "$(<"$utils/library/overlay.txt")" == "overlay source" ]] ||
  fail "library-only overlay did not add its expected source"
run_guard true || fail "an exact, already-applied derived tree was rejected"
assert_effective_tree_marker "already-applied derived tree" "$derived_tree"

create_fixture
git -C "$utils" remote set-url origin git@github.com:soramitsu/fearless-utils-Android.git
run_guard false || fail "canonical scp-style GitHub origin was rejected"

create_fixture
git -C "$utils" remote set-url origin ssh://git@github.com/soramitsu/fearless-utils-Android.git
run_guard false || fail "canonical SSH GitHub origin was rejected"

create_fixture
git -C "$utils" remote set-url origin https://github.com/soramitsu/fearless-utils-Android
run_guard false || fail "canonical extensionless HTTPS GitHub origin was rejected"

create_fixture
printf '%s\n' 'local drift' >> "$utils/library/base.txt"
tracked_drift_before="$(<"$utils/library/base.txt")"
expect_guard_failure "tracked pre-overlay drift"
[[ "$(<"$utils/library/base.txt")" == "$tracked_drift_before" ]] ||
  fail "tracked-drift rejection rewrote the dirty source file"
[[ ! -e "$utils/library/overlay.txt" ]] ||
  fail "tracked-drift rejection applied part of the overlay"

create_fixture
git -C "$utils" update-index --assume-unchanged library/base.txt
printf '%s\n' 'assume-unchanged drift' >> "$utils/library/base.txt"
expect_guard_failure "assume-unchanged tracked drift" false

create_fixture
git -C "$utils" update-index --skip-worktree library/base.txt
printf '%s\n' 'skip-worktree drift' >> "$utils/library/base.txt"
expect_guard_failure "skip-worktree tracked drift" false

create_fixture
printf '%s\n' 'library/base.txt filter=adversarial-mask' > "$utils/.gitattributes"
git -C "$utils" add .gitattributes
git -C "$utils" commit -qm "configure tracked source attributes"
expected_commit="$(git -C "$utils" rev-parse HEAD)"
git -C "$utils" config filter.adversarial-mask.clean "sed 's/^tampered source$/base source/'"
git -C "$utils" config filter.adversarial-mask.smudge cat
git -C "$utils" config filter.adversarial-mask.required true
printf '%s\n' 'tampered source' > "$utils/library/base.txt"
git -C "$utils" diff --quiet -- library/base.txt ||
  fail "clean-filter adversarial fixture did not mask the ordinary Git diff"
expect_guard_failure "tracked drift masked by a configured clean filter" false

create_fixture
printf '%s\n' 'untracked drift' > "$utils/untracked.txt"
expect_guard_failure "untracked pre-overlay drift"
assert_pristine_source "untracked-drift rejection"

create_fixture
write_kotlin_diagnostic
run_guard false || fail "bounded Kotlin compiler diagnostic was rejected from the pristine tree"
run_guard true || fail "bounded Kotlin compiler diagnostic blocked the exact library-only overlay"
run_guard true || fail "bounded Kotlin compiler diagnostic blocked the already-applied exact overlay"

create_fixture
write_kotlin_session_marker
run_guard false || fail "bounded live Kotlin compiler session marker was rejected from the pristine tree"
run_guard true || fail "bounded live Kotlin compiler session marker blocked the exact library-only overlay"
run_guard true || fail "bounded live Kotlin compiler session marker blocked the already-applied exact overlay"

create_fixture
write_kotlin_session_marker
printf '%s\n' 'source drift beside session marker' > "$utils/.kotlin/sessions/Injected.kt"
expect_guard_failure "source drift beside an allowed Kotlin compiler session marker" false

create_fixture
mkdir -p "$utils/.kotlin/sessions"
: > "$utils/.kotlin/sessions/kotlin-compiler-adversarial.salive"
expect_guard_failure "nonnumeric Kotlin compiler session marker" false

create_fixture
write_kotlin_session_marker 0000000000000000000
expect_guard_failure "zero-prefixed Kotlin compiler session marker" false

create_fixture
write_kotlin_session_marker 18446744073709551616
expect_guard_failure "out-of-range Kotlin compiler session marker" false

create_fixture
write_kotlin_session_marker 17604385351475217490
run_guard false || fail "observed unsigned 64-bit Kotlin compiler session marker was rejected"

create_fixture
write_kotlin_session_marker 1
write_kotlin_session_marker 18446744073709551615
run_guard false || fail "canonical minimum/maximum uint64 Kotlin compiler session markers were rejected"

create_fixture
mkdir -p "$utils/.kotlin/sessions"
: > "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.kt"
expect_guard_failure "Kotlin compiler session marker with a source extension" false

create_fixture
write_kotlin_session_marker
printf '%s\n' 'nonempty session marker' > "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
expect_guard_failure "nonempty Kotlin compiler session marker" false

create_fixture
write_kotlin_session_marker
chmod +x "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
expect_guard_failure "executable Kotlin compiler session marker" false

create_fixture
mkdir -p "$utils/.kotlin/sessions"
ln -s "$utils/library/base.txt" "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
expect_guard_failure "symlink Kotlin compiler session marker" false

create_fixture
mkdir -p "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
printf '%s\n' 'source drift' > \
  "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive/Injected.kt"
expect_guard_failure "directory disguised as a Kotlin compiler session marker" false

create_fixture
mkdir -p "$utils/.kotlin/sessions"
: > "$fixture/hard-link-target"
ln "$fixture/hard-link-target" "$utils/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
expect_guard_failure "hard-linked Kotlin compiler session marker" false

create_fixture
mkdir -p "$utils/library/.kotlin/sessions"
: > "$utils/library/.kotlin/sessions/kotlin-compiler-1785000000000000000.salive"
expect_guard_failure "nested source path disguised as a Kotlin compiler session marker" false

create_fixture
write_kotlin_session_marker
printf '%s\n' 'tracked drift beside session marker' >> "$utils/library/base.txt"
expect_guard_failure "tracked source drift masked by an allowed Kotlin compiler session marker" false

create_fixture
write_kotlin_diagnostic
printf '%s\n' 'source drift beside diagnostic' > "$utils/.kotlin/errors/Injected.kt"
expect_guard_failure "source drift beside an allowed Kotlin diagnostic" false

create_fixture
write_kotlin_diagnostic
printf '%s\n' 'source drift elsewhere in cache root' > "$utils/.kotlin/source.kt"
expect_guard_failure "source drift elsewhere under .kotlin" false

create_fixture
mkdir -p "$utils/library/.kotlin/errors"
printf '%s\n' \
  'kotlin version: 2.1.10' \
  'error message: similarly named nested source path' \
  'source drift' > "$utils/library/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "similarly named diagnostic path below source" false

create_fixture
mkdir -p "$utils/.kotlin-errors/errors"
printf '%s\n' \
  'kotlin version: 2.1.10' \
  'error message: similarly named cache root' \
  'source drift' > "$utils/.kotlin-errors/errors/errors-1785000000000.log"
expect_guard_failure "similarly named Kotlin cache root" false

create_fixture
mkdir -p "$utils/.kotlin/errors"
printf '%s\n' \
  'kotlin version: 2.1.10' \
  'error message: wrong extension' \
  'source drift' > "$utils/.kotlin/errors/errors-1785000000000.kt"
expect_guard_failure "Kotlin diagnostic basename with a source extension" false

create_fixture
mkdir -p "$utils/.kotlin/errors"
printf '%s\n' \
  'kotlin version: 2.1.10' \
  'error message: nonnumeric filename' \
  'source drift' > "$utils/.kotlin/errors/errors-adversarial.log"
expect_guard_failure "nonnumeric Kotlin diagnostic filename" false

create_fixture
write_kotlin_diagnostic 178500000000
expect_guard_failure "short Kotlin diagnostic timestamp" false

create_fixture
write_kotlin_diagnostic 17850000000000
expect_guard_failure "long Kotlin diagnostic timestamp" false

create_fixture
write_kotlin_diagnostic 0000000000000
expect_guard_failure "zero Kotlin diagnostic timestamp" false

create_fixture
mkdir -p "$utils/.kotlin/errors"
printf '%s\n' \
  'attacker-controlled first line' \
  'error message: forged diagnostic' \
  'source drift' > "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "malformed Kotlin diagnostic header" false

create_fixture
mkdir -p "$utils/.kotlin/errors"
printf '%s\n' \
  'kotlin version: 2.1.10' \
  'attacker-controlled second line' \
  'source drift' > "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "malformed Kotlin diagnostic error header" false

create_fixture
write_kotlin_diagnostic
chmod +x "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "executable Kotlin diagnostic" false

create_fixture
mkdir -p "$utils/.kotlin/errors"
ln -s "$utils/library/base.txt" "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "symlink Kotlin diagnostic" false

create_fixture
mkdir -p "$utils/.kotlin/errors/errors-1785000000000.log"
printf '%s\n' 'source drift' > "$utils/.kotlin/errors/errors-1785000000000.log/Injected.kt"
expect_guard_failure "directory disguised as a Kotlin diagnostic" false

create_fixture
write_kotlin_diagnostic
LC_ALL=C dd if=/dev/zero bs=1048576 count=1 2>/dev/null |
  LC_ALL=C tr '\000' x >> "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "oversized Kotlin diagnostic" false

create_fixture
for ((diagnostic_index = 1; diagnostic_index <= 129; diagnostic_index++)); do
  write_kotlin_diagnostic "$((1785000000000 + diagnostic_index))"
done
expect_guard_failure "excessive Kotlin diagnostic file count" false

create_fixture
for ((diagnostic_index = 1; diagnostic_index <= 17; diagnostic_index++)); do
  diagnostic_timestamp="$((1785000000000 + diagnostic_index))"
  write_kotlin_diagnostic "$diagnostic_timestamp"
  LC_ALL=C dd if=/dev/zero bs=1000000 count=1 2>/dev/null |
    LC_ALL=C tr '\000' x >> "$utils/.kotlin/errors/errors-$diagnostic_timestamp.log"
done
expect_guard_failure "excessive aggregate Kotlin diagnostic bytes" false

create_fixture
write_kotlin_diagnostic
printf '%s\n' 'real tracked source drift' >> "$utils/library/base.txt"
expect_guard_failure "tracked source drift masked by an allowed Kotlin diagnostic" false

create_fixture
write_kotlin_diagnostic
git -C "$utils" add .kotlin/errors/errors-1785000000000.log
git -C "$utils" commit -qm "track diagnostic-shaped fixture"
expected_commit="$(git -C "$utils" rev-parse HEAD)"
printf '%s\n' 'tracked diagnostic-shaped drift' >> "$utils/.kotlin/errors/errors-1785000000000.log"
expect_guard_failure "tracked drift at an otherwise allowed diagnostic path" false

create_fixture
mkdir -p "$utils/untracked-directory"
printf '%s\n' 'nested drift' > "$utils/untracked-directory/nested.txt"
expect_guard_failure "untracked directory drift"
assert_pristine_source "untracked-directory rejection"

create_fixture
printf '%s\n' 'intent to add' > "$utils/intent-to-add.txt"
git -C "$utils" add -N intent-to-add.txt
expect_guard_failure "intent-to-add index drift"
assert_pristine_source "intent-to-add rejection"

create_fixture
printf '%s\n' 'staged drift' >> "$utils/library/base.txt"
git -C "$utils" add library/base.txt
expect_guard_failure "staged tracked drift"

create_fixture
run_guard true || fail "fixture overlay setup failed"
git -C "$utils" add library/base.txt library/overlay.txt
expect_guard_failure "staged overlay content"

create_fixture
run_guard true || fail "fixture overlay setup failed"
printf '%s\n' 'adversarial extra line' >> "$utils/library/base.txt"
expect_guard_failure "extra modification in a patch-touched tracked file"

create_fixture
run_guard true || fail "fixture overlay setup failed"
printf '%s\n' 'adversarial replacement' > "$utils/library/overlay.txt"
expect_guard_failure "extra modification in a patch-added file"

create_fixture
run_guard true || fail "fixture overlay setup failed"
rm "$utils/library/overlay.txt"
expect_guard_failure "deleted patch-added file"

create_fixture
printf '%s\n' 'base source with library overlay' > "$utils/library/base.txt"
expect_guard_failure "partially applied overlay"
[[ ! -e "$utils/library/overlay.txt" ]] ||
  fail "partial-overlay rejection mutated the checkout"

create_fixture
chmod +x "$utils/library/base.txt"
expect_guard_failure "tracked executable-mode drift" false

create_fixture
rm "$utils/library/base.txt"
ln -s ../.gitignore "$utils/library/base.txt"
expect_guard_failure "tracked file replaced by symlink" false

create_fixture
git -C "$utils" remote set-url origin https://github.com/attacker/fearless-utils-Android.git
expect_guard_failure "wrong origin owner" false

create_fixture
git -C "$utils" remote set-url origin https://github.com/soramitsu/other.git
expect_guard_failure "wrong origin repository" false

create_fixture
git -C "$utils" remote set-url origin https://token@github.com/soramitsu/fearless-utils-Android.git
expect_guard_failure "credential-bearing origin" false

create_fixture
git -C "$utils" config --add remote.origin.url git@github.com:soramitsu/fearless-utils-Android.git
expect_guard_failure "multiple origin URLs" false

create_fixture
git -C "$utils" config url.file://"$tmp_dir"/redirect/.insteadOf https://github.com/
expect_guard_failure "effective origin redirected away from GitHub" false

create_fixture
printf '%s\n' 'new commit' > "$utils/new-commit.txt"
git -C "$utils" add new-commit.txt
git -C "$utils" commit -qm "unpinned commit"
if ALLOW_FEARLESS_UTILS_DRIFT=true run_guard false; then
  fail "legacy drift override bypassed the pinned commit"
fi

create_fixture
uppercase_commit="$(printf '%s' "$expected_commit" | tr '[:lower:]' '[:upper:]')"
if FEARLESS_UTILS_COMMIT="$uppercase_commit" \
  FEARLESS_UTILS_PATH="$utils" \
  FEARLESS_UTILS_LIBRARY_ONLY=false \
  "$wallet/scripts/ensure-fearless-utils.sh" >/dev/null 2>&1; then
  fail "non-canonical uppercase commit input was accepted"
fi

create_fixture
if FEARLESS_UTILS_PATH="$utils/library" \
  FEARLESS_UTILS_COMMIT="$expected_commit" \
  FEARLESS_UTILS_LIBRARY_ONLY=false \
  "$wallet/scripts/ensure-fearless-utils.sh" >/dev/null 2>&1; then
  fail "nested directory was accepted as the repository root"
fi

create_fixture
replacement_base="$expected_commit"
printf '%s\n' 'replacement tree' > "$utils/library/base.txt"
git -C "$utils" add library/base.txt
git -C "$utils" commit -qm "replacement commit"
replacement_commit="$(git -C "$utils" rev-parse HEAD)"
git -C "$utils" reset --hard -q "$replacement_base"
git -C "$utils" replace "$replacement_base" "$replacement_commit"
expect_guard_failure "Git replacement ref"

create_fixture
printf '%s\n' '# uncommitted patch drift' >> "$wallet/scripts/fearless-utils-library-only.patch"
expect_guard_failure "uncommitted overlay patch drift"

create_fixture
printf '%s\n' '# staged patch drift' >> "$wallet/scripts/fearless-utils-library-only.patch"
git -C "$wallet" add scripts/fearless-utils-library-only.patch
expect_guard_failure "staged overlay patch drift"

create_fixture
printf '%s\n' 'not a patch' > "$wallet/scripts/fearless-utils-library-only.patch"
git -C "$wallet" add scripts/fearless-utils-library-only.patch
git -C "$wallet" commit -qm "commit malformed patch"
expect_guard_failure "committed malformed overlay patch"

create_fixture
rm "$wallet/scripts/fearless-utils-library-only.patch"
ln -s /etc/hosts "$wallet/scripts/fearless-utils-library-only.patch"
expect_guard_failure "symlink overlay patch"

create_fixture
sub_origin="$fixture/submodule-origin"
mkdir -p "$sub_origin"
git -C "$sub_origin" init -q
git_identity "$sub_origin"
printf '%s\n' 'submodule source' > "$sub_origin/source.txt"
git -C "$sub_origin" add source.txt
git -C "$sub_origin" commit -qm "submodule source"
git -C "$utils" -c protocol.file.allow=always submodule add -q "$sub_origin" vendor/submodule
git -C "$utils" commit -qm "pin submodule"
expected_commit="$(git -C "$utils" rev-parse HEAD)"
run_guard true || fail "clean pinned submodule was rejected"
printf '%s\n' 'dirty submodule source' >> "$utils/vendor/submodule/source.txt"
expect_guard_failure "dirty submodule content"

create_fixture
sub_origin="$fixture/submodule-origin"
mkdir -p "$sub_origin"
git -C "$sub_origin" init -q
git_identity "$sub_origin"
printf '%s\n' 'submodule source' > "$sub_origin/source.txt"
git -C "$sub_origin" add source.txt
git -C "$sub_origin" commit -qm "submodule source"
git -C "$utils" -c protocol.file.allow=always submodule add -q "$sub_origin" vendor/submodule
git -C "$utils" commit -qm "pin submodule"
expected_commit="$(git -C "$utils" rev-parse HEAD)"
rm -rf "$utils/vendor/submodule"
expect_guard_failure "uninitialized submodule"

create_fixture
mkdir -p "$utils/ignored"
printf '%s\n' 'build output' > "$utils/ignored/output.bin"
run_guard true || fail "Git-ignored build output incorrectly changed the source-tree contract"

echo "[fearless-utils-derived-tree-test] all deterministic and adversarial fixtures passed"
