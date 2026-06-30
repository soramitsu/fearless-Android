#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
EXPORTER="$SCRIPT_DIR/export-public-dependency-upstream-delta.sh"

fail() {
  echo "[public-dependency-handoff-test][error] $*" >&2
  exit 1
}

write_file() {
  local file="$1"
  shift
  mkdir -p "$(dirname "$file")"
  printf '%s\n' "$@" > "$file"
}

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output
  output="$(mktemp)"
  if "$@" >"$output" 2>&1; then
    cat "$output" >&2
    fail "expected failure for $label"
  fi
  if ! grep -q "$expected" "$output"; then
    cat "$output" >&2
    fail "$label failed without expected message: $expected"
  fi
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

modules=(
  public-android-foundation
  public-ui-core
  public-xnetworking
  public-shared-features-core
  public-shared-features-xcm
  public-shared-features-backup
)

seed_fixture() {
  local root="$1"
  mkdir -p "$root/scripts" "$root/docs" "$root/.github/workflows"
  write_file "$root/scripts/ensure-fearless-utils.sh" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'EXPECTED_COMMIT="${FEARLESS_UTILS_COMMIT:-7500809f33243ee47ecb2ec8563fc284ac4de0d6}"' \
    'FEARLESS_UTILS_LIBRARY_ONLY="${FEARLESS_UTILS_LIBRARY_ONLY:-false}"' \
    'echo "$EXPECTED_COMMIT $FEARLESS_UTILS_LIBRARY_ONLY"'
  write_file "$root/scripts/fearless-utils-library-only.patch" \
    'diff --git a/fearless-utils/build.gradle b/fearless-utils/build.gradle' \
    '--- a/fearless-utils/build.gradle' \
    '+++ b/fearless-utils/build.gradle' \
    '@@ -1 +1 @@' \
    '-plugins {}' \
    '+plugins { id "com.android.library" }'
  write_file "$root/docs/public-dependency-audit.md" \
    '# Public Dependency Audit' \
    'Uses soramitsu/fearless-utils-Android and scripts/ensure-fearless-utils.sh.'
  write_file "$root/docs/release-checklist.md" \
    '# Release Checklist' \
    'Run bash ./scripts/test-public-dependency-upstream-delta-export.sh.' \
    'Run bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta.'
  write_file "$root/settings.gradle" \
    'includeBuild("../fearless-utils-Android") {' \
    '  dependencySubstitution { substitute module("jp.co.soramitsu.fearless-utils:fearless-utils") using project(":fearless-utils") }' \
    '}'
  write_file "$root/.github/workflows/android-ci.yml" \
    'name: Android CI' \
    'jobs:' \
    '  test:' \
    '    steps:' \
    '      - run: bash ./scripts/test-public-dependency-upstream-delta-export.sh' \
    '      - run: bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta'
  write_file "$root/.github/workflows/android-release.yml" \
    'name: Android Release' \
    'jobs:' \
    '  release:' \
    '    steps:' \
    '      - run: ./scripts/audit-public-artifacts.sh --release --strict-provenance'

  local module
  for module in "${modules[@]}"; do
    write_file "$root/$module/build.gradle" 'plugins { id "com.android.library" }'
    write_file "$root/$module/src/main/java/example/${module//-/_}.kt" "package example" "object ${module//-/_}"
  done
}

fixture="$tmp_dir/repo"
seed_fixture "$fixture"

output_dir="$tmp_dir/out"
bash "$EXPORTER" --root "$fixture" --output "$output_dir" >/dev/null

node - "$output_dir/handoff-manifest.json" <<'NODE'
const fs = require('fs');
const assert = require('assert');
const manifest = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));

assert.equal(manifest.schemaVersion, 1);
assert.equal(manifest.scope, 'android-public-dependency-upstream-delta');
assert.equal(manifest.fearlessUtils.expectedRevision, '7500809f33243ee47ecb2ec8563fc284ac4de0d6');
assert.equal(manifest.fearlessUtils.patchTouchedPathCount, 1);
assert.deepEqual(manifest.fearlessUtils.patchTouchedPaths, ['fearless-utils/build.gradle']);
assert.equal(manifest.publicCompatibilityModules.length, 6);
assert(manifest.publicCompatibilityModules.every((entry) => entry.fileCount === 2));
assert(manifest.requiredReviewCommands.some((command) => command.includes('test-public-dependency-upstream-delta-export.sh')));
assert(manifest.files.some((entry) => entry.path === 'handoff-manifest.json') === false);
assert(manifest.files.some((entry) => entry.path === 'README.md'));
assert(manifest.files.some((entry) => entry.path === 'fearless-utils/fearless-utils-library-only.patch'));
assert(manifest.files.every((entry) => /^[0-9a-f]{64}$/.test(entry.sha256)));
NODE

[[ -s "$output_dir/README.md" ]] || fail "README.md was not generated"
[[ -s "$output_dir/fearless-utils/fearless-utils-library-only.patch" ]] || fail "overlay patch was not copied"

missing_patch="$tmp_dir/missing-patch"
cp -R "$fixture" "$missing_patch"
rm "$missing_patch/scripts/fearless-utils-library-only.patch"
expect_failure "missing overlay patch" "library-only overlay patch missing" \
  bash "$EXPORTER" --root "$missing_patch" --output "$tmp_dir/missing-patch-out"

missing_docs="$tmp_dir/missing-docs"
cp -R "$fixture" "$missing_docs"
rm "$missing_docs/docs/public-dependency-audit.md"
expect_failure "missing public dependency docs" "public dependency audit docs missing" \
  bash "$EXPORTER" --root "$missing_docs" --output "$tmp_dir/missing-docs-out"

missing_module="$tmp_dir/missing-module"
cp -R "$fixture" "$missing_module"
rm -rf "$missing_module/public-xnetworking"
expect_failure "missing compatibility module" "public compatibility module missing: public-xnetworking" \
  bash "$EXPORTER" --root "$missing_module" --output "$tmp_dir/missing-module-out"

bad_commit="$tmp_dir/bad-commit"
cp -R "$fixture" "$bad_commit"
perl -0pi -e 's/7500809f33243ee47ecb2ec8563fc284ac4de0d6/not-a-commit/' "$bad_commit/scripts/ensure-fearless-utils.sh"
expect_failure "unparseable utils pin" "pinned fearless-utils commit missing" \
  bash "$EXPORTER" --root "$bad_commit" --output "$tmp_dir/bad-commit-out"

bad_patch_scope="$tmp_dir/bad-patch-scope"
cp -R "$fixture" "$bad_patch_scope"
perl -0pi -e 's#fearless-utils/build.gradle#app/build.gradle#g' "$bad_patch_scope/scripts/fearless-utils-library-only.patch"
expect_failure "patch outside upstream scope" "touches paths outside fearless-utils/root Gradle files" \
  bash "$EXPORTER" --root "$bad_patch_scope" --output "$tmp_dir/bad-patch-scope-out"

missing_release_marker="$tmp_dir/missing-release-marker"
cp -R "$fixture" "$missing_release_marker"
write_file "$missing_release_marker/docs/release-checklist.md" '# Release Checklist' 'Run release checks.'
expect_failure "missing release handoff command" "release checklist handoff export command missing" \
  bash "$EXPORTER" --root "$missing_release_marker" --output "$tmp_dir/missing-release-marker-out"

echo "[public-dependency-handoff-test] all assertions passed"
