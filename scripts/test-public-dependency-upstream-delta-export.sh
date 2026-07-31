#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
EXPORTER="$SCRIPT_DIR/export-public-dependency-upstream-delta.sh"

fail() {
  echo "[public-dependency-handoff-test][error] $*" >&2
  exit 1
}

NEGATIVE_SCENARIO_COUNT=0

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
  NEGATIVE_SCENARIO_COUNT=$((NEGATIVE_SCENARIO_COUNT + 1))
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
expected_commit="7500809f33243ee47ecb2ec8563fc284ac4de0d6"
utils_guard_command="FEARLESS_UTILS_PATH=../fearless-utils-Android FEARLESS_UTILS_COMMIT=$expected_commit FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android FEARLESS_UTILS_LIBRARY_ONLY=true ./scripts/ensure-fearless-utils.sh"
handoff_test_command="bash ./scripts/test-public-dependency-upstream-delta-export.sh"
handoff_export_command="bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta"
provenance_test_command="bash ./scripts/test-public-artifact-provenance-audit.sh"
provenance_audit_command="./scripts/audit-public-artifacts.sh --strict-provenance"
unsigned_release_provenance_audit_command="./scripts/audit-public-artifacts.sh --unsigned-release --strict-provenance"
release_provenance_audit_command="./scripts/audit-public-artifacts.sh --release --strict-provenance"

seed_fixture() {
  local root="$1"
  mkdir -p "$root/scripts" "$root/docs/releases" "$root/.github/workflows" "$root/runtime/src/main/assets"
  write_file "$root/scripts/ensure-fearless-utils.sh" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    "EXPECTED_COMMIT=\"\${FEARLESS_UTILS_COMMIT:-$expected_commit}\"" \
    'EXPECTED_REPOSITORY="${FEARLESS_UTILS_REPOSITORY:-soramitsu/fearless-utils-Android}"' \
    'FEARLESS_UTILS_LIBRARY_ONLY="${FEARLESS_UTILS_LIBRARY_ONLY:-false}"' \
    'echo "$EXPECTED_COMMIT $EXPECTED_REPOSITORY $FEARLESS_UTILS_LIBRARY_ONLY"'
  write_file "$root/scripts/test-fearless-utils-derived-tree.sh" \
    '#!/usr/bin/env bash' \
    'set -euo pipefail' \
    'echo "[fearless-utils-derived-tree-test] all deterministic and adversarial fixtures passed"'
  write_file "$root/scripts/fearless-utils-library-only.patch" \
    'diff --git a/fearless-utils/build.gradle b/fearless-utils/build.gradle' \
    '--- a/fearless-utils/build.gradle' \
    '+++ b/fearless-utils/build.gradle' \
    '@@ -1 +1 @@' \
    '-plugins {}' \
    '+plugins { id "com.android.library" }'
  write_file "$root/docs/public-dependency-audit.md" \
    '# Public Dependency Audit' \
    "- Pinned \`fearless-utils-Android\` revision: \`$expected_commit\`." \
    '- Public compatibility-module substitutions: `6`.' \
    '- Approved/required executable XCM route rows: `15` / `15`.' \
    '- Discovery-only XCM destinations/assets: `34` / `59`; `14` of those destinations carry `39` multi-asset route entries.' \
    '- Strict-provenance allowlisted binary artifacts: `9`.' \
    'Uses soramitsu/fearless-utils-Android and scripts/ensure-fearless-utils.sh.' \
    'The public compatibility layer contains a Substrate fee and submission engine.' \
    'Release ENABLE_PRODUCTION_XCM_TRANSFERS=false remains fail closed.' \
    "$utils_guard_command" \
    "$handoff_test_command" \
    "$handoff_export_command" \
    "$provenance_test_command" \
    "$provenance_audit_command"
  write_file "$root/docs/release-checklist.md" \
    '# Release Checklist' \
    "$utils_guard_command" \
    "$handoff_test_command" \
    "$handoff_export_command" \
    "$provenance_test_command" \
    "$provenance_audit_command" \
    "$unsigned_release_provenance_audit_command" \
    "$release_provenance_audit_command"
  write_file "$root/docs/releases/PROCESS.md" \
    '# Android Release Process' \
    "$utils_guard_command" \
    "$handoff_test_command" \
    "$handoff_export_command" \
    "$provenance_test_command" \
    "$provenance_audit_command" \
    "$unsigned_release_provenance_audit_command" \
    "$release_provenance_audit_command"
  write_file "$root/README.md" \
    '# Fearless Android' \
    "FEARLESS_UTILS_COMMIT=$expected_commit" \
    'FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android' \
    "$handoff_test_command" \
    "$handoff_export_command" \
    "$provenance_test_command" \
    "$provenance_audit_command"
  write_file "$root/docs/binary-provenance.md" \
    '# Binary Provenance' \
    "source repository: soramitsu/fearless-utils-Android at $expected_commit" \
    "$provenance_test_command" \
    "$provenance_audit_command" \
    "$release_provenance_audit_command" \
    '| `gradle/wrapper/gradle-wrapper.jar` | `1111111111111111111111111111111111111111111111111111111111111111` |' \
    '| `app/src/main/jniLibs/arm64-v8a/libsodium.so` | `2222222222222222222222222222222222222222222222222222222222222222` |' \
    '| `app/src/main/jniLibs/armeabi-v7a/libsodium.so` | `3333333333333333333333333333333333333333333333333333333333333333` |' \
    '| `app/src/main/jniLibs/x86/libsodium.so` | `4444444444444444444444444444444444444444444444444444444444444444` |' \
    '| `app/src/main/jniLibs/x86_64/libsodium.so` | `5555555555555555555555555555555555555555555555555555555555555555` |' \
    '| `app/src/main/jniLibs/arm64-v8a/libsr25519java.so` | `6666666666666666666666666666666666666666666666666666666666666666` |' \
    '| `app/src/main/jniLibs/armeabi-v7a/libsr25519java.so` | `7777777777777777777777777777777777777777777777777777777777777777` |' \
    '| `app/src/main/jniLibs/x86/libsr25519java.so` | `8888888888888888888888888888888888888888888888888888888888888888` |' \
    '| `app/src/main/jniLibs/x86_64/libsr25519java.so` | `9999999999999999999999999999999999999999999999999999999999999999` |'
  write_file "$root/scripts/audit-public-artifacts.sh" \
    '#!/usr/bin/env bash' \
    'expected_checksum() {' \
    '  case "$1" in' \
    '    gradle/wrapper/gradle-wrapper.jar) echo "1111111111111111111111111111111111111111111111111111111111111111" ;;' \
    '    app/src/main/jniLibs/arm64-v8a/libsodium.so) echo "2222222222222222222222222222222222222222222222222222222222222222" ;;' \
    '    app/src/main/jniLibs/armeabi-v7a/libsodium.so) echo "3333333333333333333333333333333333333333333333333333333333333333" ;;' \
    '    app/src/main/jniLibs/x86/libsodium.so) echo "4444444444444444444444444444444444444444444444444444444444444444" ;;' \
    '    app/src/main/jniLibs/x86_64/libsodium.so) echo "5555555555555555555555555555555555555555555555555555555555555555" ;;' \
    '    app/src/main/jniLibs/arm64-v8a/libsr25519java.so) echo "6666666666666666666666666666666666666666666666666666666666666666" ;;' \
    '    app/src/main/jniLibs/armeabi-v7a/libsr25519java.so) echo "7777777777777777777777777777777777777777777777777777777777777777" ;;' \
    '    app/src/main/jniLibs/x86/libsr25519java.so) echo "8888888888888888888888888888888888888888888888888888888888888888" ;;' \
    '    app/src/main/jniLibs/x86_64/libsr25519java.so) echo "9999999999999999999999999999999999999999999999999999999999999999" ;;' \
    '  esac' \
    '}'
  write_file "$root/scripts/test-public-artifact-provenance-audit.sh" \
    '#!/usr/bin/env bash' \
    'echo "[artifact-provenance-test] all adversarial fixtures passed"'
  write_file "$root/build.gradle" \
    "substitute module('jp.co.soramitsu:android-foundation') using project(':public-android-foundation')" \
    "substitute module('jp.co.soramitsu:ui-core') using project(':public-ui-core')" \
    "substitute module('jp.co.soramitsu.xnetworking:lib-android') using project(':public-xnetworking')" \
    "substitute module('jp.co.soramitsu.shared_features:core') using project(':public-shared-features-core')" \
    "substitute module('jp.co.soramitsu.shared_features:xcm') using project(':public-shared-features-xcm')" \
    "substitute module('jp.co.soramitsu.shared_features:backup') using project(':public-shared-features-backup')"
  write_file "$root/settings.gradle" \
    "include ':public-android-foundation'" \
    "include ':public-ui-core'" \
    "include ':public-xnetworking'" \
    "include ':public-shared-features-core'" \
    "include ':public-shared-features-xcm'" \
    "include ':public-shared-features-backup'" \
    'includeBuild("../fearless-utils-Android") {' \
    '  dependencySubstitution { substitute module("jp.co.soramitsu.fearless-utils:fearless-utils") using project(":fearless-utils") }' \
    '}'
  write_file "$root/.github/workflows/android-ci.yml" \
    'name: Android CI' \
    "FEARLESS_UTILS_COMMIT: $expected_commit" \
    'FEARLESS_UTILS_LIBRARY_ONLY: "true"' \
    'FORCE_LOCAL_UTILS: "true"' \
    'jobs:' \
    '  test:' \
    '    steps:' \
    '      - repository: soramitsu/fearless-utils-Android' \
    "      - run: $handoff_test_command" \
    "      - run: $handoff_export_command" \
    "      - run: $provenance_test_command" \
    "      - run: $provenance_audit_command"
  write_file "$root/.github/workflows/android-release.yml" \
    'name: Android Release' \
    "FEARLESS_UTILS_COMMIT: $expected_commit" \
    'FEARLESS_UTILS_LIBRARY_ONLY: "true"' \
    'FORCE_LOCAL_UTILS: "true"' \
    'jobs:' \
    '  release:' \
    '    steps:' \
    '      - repository: soramitsu/fearless-utils-Android' \
    "      - run: $provenance_test_command" \
    "      - run: $provenance_audit_command" \
    "      - run: $unsigned_release_provenance_audit_command"

  write_file "$root/runtime/src/main/assets/approved_xcm_routes.tsv" '# approved routes'
  write_file "$root/scripts/xcm-required-routes.tsv" '# required routes'
  local index
  for index in $(seq 1 15); do
    printf 'origin%02d destination%02d ASSET%02d\n' "$index" "$index" "$index" >> "$root/runtime/src/main/assets/approved_xcm_routes.tsv"
    printf 'origin%02d destination%02d ASSET%02d\n' "$index" "$index" "$index" >> "$root/scripts/xcm-required-routes.tsv"
  done
  write_file "$root/scripts/xcm-discovery-only-routes.tsv" '# discovery-only routes'
  for index in $(seq 1 20); do
    printf 'gap-origin%02d gap-destination%02d ASSET%02d reason -\n' "$index" "$index" "$index" >> "$root/scripts/xcm-discovery-only-routes.tsv"
  done
  for index in $(seq 21 31); do
    printf 'gap-origin%02d gap-destination%02d ASSET%02dA,ASSET%02dB,ASSET%02dC reason -\n' "$index" "$index" "$index" "$index" "$index" >> "$root/scripts/xcm-discovery-only-routes.tsv"
  done
  for index in $(seq 32 34); do
    printf 'gap-origin%02d gap-destination%02d ASSET%02dA,ASSET%02dB reason -\n' "$index" "$index" "$index" "$index" >> "$root/scripts/xcm-discovery-only-routes.tsv"
  done

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
assert.deepEqual(manifest.governance, {
  compatibilityModuleCount: 6,
  approvedXcmRouteCount: 15,
  requiredXcmRouteCount: 15,
  discoveryOnlyDestinationCount: 34,
  discoveryOnlyAssetCount: 59,
  discoveryOnlyMultiAssetDestinationCount: 14,
  discoveryOnlyMultiAssetCount: 39,
  allowlistedBinaryArtifactCount: 9,
});
assert.equal(manifest.requiredReviewCommands.length, 8);
assert(manifest.requiredReviewCommands.some((command) => command.includes('test-public-dependency-upstream-delta-export.sh')));
assert(manifest.requiredReviewCommands.some((command) => command.includes('test-fearless-utils-derived-tree.sh')));
assert(manifest.requiredReviewCommands.includes('FEARLESS_UTILS_PATH=../fearless-utils-Android FEARLESS_UTILS_COMMIT=7500809f33243ee47ecb2ec8563fc284ac4de0d6 FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android FEARLESS_UTILS_LIBRARY_ONLY=true ./scripts/ensure-fearless-utils.sh'));
assert(manifest.requiredReviewCommands.includes('bash ./scripts/test-public-artifact-provenance-audit.sh'));
assert(manifest.requiredReviewCommands.includes('./scripts/audit-public-artifacts.sh --strict-provenance'));
assert(manifest.requiredReviewCommands.includes('./scripts/audit-public-artifacts.sh --unsigned-release --strict-provenance'));
assert(manifest.requiredReviewCommands.includes('./scripts/audit-public-artifacts.sh --release --strict-provenance'));
assert(manifest.files.some((entry) => entry.path === 'handoff-manifest.json') === false);
assert(manifest.files.some((entry) => entry.path === 'README.md'));
assert(manifest.files.some((entry) => entry.path === 'fearless-utils/fearless-utils-library-only.patch'));
assert(manifest.files.some((entry) => entry.path === 'fearless-utils/test-fearless-utils-derived-tree.sh'));
assert(manifest.files.some((entry) => entry.path === 'docs/binary-provenance.md'));
assert(manifest.files.some((entry) => entry.path === 'docs/release-process.md'));
assert(manifest.files.some((entry) => entry.path === 'governance/approved_xcm_routes.tsv'));
assert(manifest.files.some((entry) => entry.path === 'governance/audit-public-artifacts.sh'));
assert.equal(manifest.files.length, 18);
assert(manifest.files.every((entry) => /^[0-9a-f]{64}$/.test(entry.sha256)));
NODE

[[ -s "$output_dir/README.md" ]] || fail "README.md was not generated"
[[ -s "$output_dir/fearless-utils/fearless-utils-library-only.patch" ]] || fail "overlay patch was not copied"
[[ -s "$output_dir/fearless-utils/test-fearless-utils-derived-tree.sh" ]] || fail "derived-tree self-test was not copied"

missing_patch="$tmp_dir/missing-patch"
cp -R "$fixture" "$missing_patch"
rm "$missing_patch/scripts/fearless-utils-library-only.patch"
expect_failure "missing overlay patch" "library-only overlay patch missing" \
  bash "$EXPORTER" --root "$missing_patch" --output "$tmp_dir/missing-patch-out"

missing_derived_tree_test="$tmp_dir/missing-derived-tree-test"
cp -R "$fixture" "$missing_derived_tree_test"
rm "$missing_derived_tree_test/scripts/test-fearless-utils-derived-tree.sh"
expect_failure "missing derived-tree self-test" "derived-tree adversarial self-test missing" \
  bash "$EXPORTER" --root "$missing_derived_tree_test" --output "$tmp_dir/missing-derived-tree-test-out"

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

bad_repository="$tmp_dir/bad-repository"
cp -R "$fixture" "$bad_repository"
perl -0pi -e 's#soramitsu/fearless-utils-Android#attacker/fearless-utils-Android#' "$bad_repository/scripts/ensure-fearless-utils.sh"
expect_failure "unreviewed utils repository" "pinned fearless-utils repository missing" \
  bash "$EXPORTER" --root "$bad_repository" --output "$tmp_dir/bad-repository-out"

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

stale_snapshot_pin="$tmp_dir/stale-snapshot-pin"
cp -R "$fixture" "$stale_snapshot_pin"
perl -0pi -e 's/7500809f33243ee47ecb2ec8563fc284ac4de0d6/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/' \
  "$stale_snapshot_pin/docs/public-dependency-audit.md"
expect_failure "stale documented utils pin" "public dependency governed snapshot is stale" \
  bash "$EXPORTER" --root "$stale_snapshot_pin" --output "$tmp_dir/stale-snapshot-pin-out"

stale_xcm_status="$tmp_dir/stale-xcm-status"
cp -R "$fixture" "$stale_xcm_status"
printf '%s\n' 'XCM remains blocked until an open-source XCM extrinsic engine is added.' >> \
  "$stale_xcm_status/docs/public-dependency-audit.md"
expect_failure "stale missing-XCM-engine claim" "still claim the open-source XCM engine is missing" \
  bash "$EXPORTER" --root "$stale_xcm_status" --output "$tmp_dir/stale-xcm-status-out"

missing_process_command="$tmp_dir/missing-process-command"
cp -R "$fixture" "$missing_process_command"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' "$missing_process_command/docs/releases/PROCESS.md"
expect_failure "release process missing provenance self-test" "strict provenance self-test command missing in docs/releases/PROCESS.md" \
  bash "$EXPORTER" --root "$missing_process_command" --output "$tmp_dir/missing-process-command-out"

extra_substitution="$tmp_dir/extra-substitution"
cp -R "$fixture" "$extra_substitution"
printf '%s\n' "substitute module('attacker:unreviewed') using project(':public-unreviewed')" >> "$extra_substitution/build.gradle"
expect_failure "extra public dependency substitution" "Gradle public dependency substitutions must be exactly" \
  bash "$EXPORTER" --root "$extra_substitution" --output "$tmp_dir/extra-substitution-out"

missing_substitution="$tmp_dir/missing-substitution"
cp -R "$fixture" "$missing_substitution"
sed -i.bak '/public-ui-core/d' "$missing_substitution/build.gradle"
expect_failure "missing public dependency substitution" "Gradle public dependency substitutions must be exactly" \
  bash "$EXPORTER" --root "$missing_substitution" --output "$tmp_dir/missing-substitution-out"

commented_substitution="$tmp_dir/commented-substitution"
cp -R "$fixture" "$commented_substitution"
sed -i.bak "s#substitute module('jp.co.soramitsu:ui-core')#// substitute module('jp.co.soramitsu:ui-core')#" \
  "$commented_substitution/build.gradle"
expect_failure "commented public dependency substitution" "Gradle public dependency substitutions must be exactly" \
  bash "$EXPORTER" --root "$commented_substitution" --output "$tmp_dir/commented-substitution-out"

missing_settings_module="$tmp_dir/missing-settings-module"
cp -R "$fixture" "$missing_settings_module"
sed -i.bak "/include ':public-xnetworking'/d" "$missing_settings_module/settings.gradle"
expect_failure "missing public module include" "settings.gradle public modules must be exactly" \
  bash "$EXPORTER" --root "$missing_settings_module" --output "$tmp_dir/missing-settings-module-out"

required_route_count_drift="$tmp_dir/required-route-count-drift"
cp -R "$fixture" "$required_route_count_drift"
sed -i.bak '$d' "$required_route_count_drift/scripts/xcm-required-routes.tsv"
expect_failure "required route count drift" "approved/required XCM route counts differ" \
  bash "$EXPORTER" --root "$required_route_count_drift" --output "$tmp_dir/required-route-count-drift-out"

approved_route_identity_drift="$tmp_dir/approved-route-identity-drift"
cp -R "$fixture" "$approved_route_identity_drift"
sed -i.bak 's/ASSET15/DRIFT15/' "$approved_route_identity_drift/runtime/src/main/assets/approved_xcm_routes.tsv"
expect_failure "approved route identity drift" "approved and required XCM route manifests must contain the same route rows" \
  bash "$EXPORTER" --root "$approved_route_identity_drift" --output "$tmp_dir/approved-route-identity-drift-out"

discovery_count_drift="$tmp_dir/discovery-count-drift"
cp -R "$fixture" "$discovery_count_drift"
sed -i.bak '$d' "$discovery_count_drift/scripts/xcm-discovery-only-routes.tsv"
expect_failure "discovery-only count drift" "public dependency governed snapshot is stale" \
  bash "$EXPORTER" --root "$discovery_count_drift" --output "$tmp_dir/discovery-count-drift-out"

extra_binary_row="$tmp_dir/extra-binary-row"
cp -R "$fixture" "$extra_binary_row"
printf '%s\n' '| `app/src/main/jniLibs/obsolete/libstale.so` | `aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa` |' >> \
  "$extra_binary_row/docs/binary-provenance.md"
expect_failure "extra binary provenance row" "rows must exactly match" \
  bash "$EXPORTER" --root "$extra_binary_row" --output "$tmp_dir/extra-binary-row-out"

binary_checksum_drift="$tmp_dir/binary-checksum-drift"
cp -R "$fixture" "$binary_checksum_drift"
sed -i.bak 's/2222222222222222222222222222222222222222222222222222222222222222/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/' \
  "$binary_checksum_drift/docs/binary-provenance.md"
expect_failure "binary provenance checksum drift" "rows must exactly match" \
  bash "$EXPORTER" --root "$binary_checksum_drift" --output "$tmp_dir/binary-checksum-drift-out"

workflow_pin_drift="$tmp_dir/workflow-pin-drift"
cp -R "$fixture" "$workflow_pin_drift"
sed -i.bak "s/FEARLESS_UTILS_COMMIT: $expected_commit/FEARLESS_UTILS_COMMIT: aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/" \
  "$workflow_pin_drift/.github/workflows/android-ci.yml"
expect_failure "workflow utils pin drift" "workflow fearless-utils revision missing" \
  bash "$EXPORTER" --root "$workflow_pin_drift" --output "$tmp_dir/workflow-pin-drift-out"

workflow_command_drift="$tmp_dir/workflow-command-drift"
cp -R "$fixture" "$workflow_command_drift"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' "$workflow_command_drift/.github/workflows/android-ci.yml"
expect_failure "workflow provenance command drift" "Android CI governance command missing" \
  bash "$EXPORTER" --root "$workflow_command_drift" --output "$tmp_dir/workflow-command-drift-out"

commented_workflow_command="$tmp_dir/commented-workflow-command"
cp -R "$fixture" "$commented_workflow_command"
sed -i.bak 's|      - run: bash ./scripts/test-public-artifact-provenance-audit.sh|      # - run: bash ./scripts/test-public-artifact-provenance-audit.sh|' \
  "$commented_workflow_command/.github/workflows/android-ci.yml"
expect_failure "commented workflow provenance command" "Android CI governance command missing as active text" \
  bash "$EXPORTER" --root "$commented_workflow_command" --output "$tmp_dir/commented-workflow-command-out"

missing_release_provenance_self_test="$tmp_dir/missing-release-provenance-self-test"
cp -R "$fixture" "$missing_release_provenance_self_test"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' \
  "$missing_release_provenance_self_test/.github/workflows/android-release.yml"
expect_failure "release workflow missing provenance self-test" "Android release provenance self-test missing" \
  bash "$EXPORTER" --root "$missing_release_provenance_self_test" \
    --output "$tmp_dir/missing-release-provenance-self-test-out"

missing_unsigned_release_provenance="$tmp_dir/missing-unsigned-release-provenance"
cp -R "$fixture" "$missing_unsigned_release_provenance"
sed -i.bak '/audit-public-artifacts.sh --unsigned-release --strict-provenance/d' \
  "$missing_unsigned_release_provenance/.github/workflows/android-release.yml"
expect_failure "release workflow missing unsigned provenance gate" "Android unsigned release artifact provenance audit missing" \
  bash "$EXPORTER" --root "$missing_unsigned_release_provenance" \
    --output "$tmp_dir/missing-unsigned-release-provenance-out"

readme_pin_drift="$tmp_dir/readme-pin-drift"
cp -R "$fixture" "$readme_pin_drift"
sed -i.bak "s/$expected_commit/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa/" "$readme_pin_drift/README.md"
expect_failure "README utils pin drift" "documented fearless-utils revision missing in README.md" \
  bash "$EXPORTER" --root "$readme_pin_drift" --output "$tmp_dir/readme-pin-drift-out"

readme_command_drift="$tmp_dir/readme-command-drift"
cp -R "$fixture" "$readme_command_drift"
sed -i.bak '/test-public-artifact-provenance-audit.sh/d' "$readme_command_drift/README.md"
expect_failure "README provenance command drift" "strict provenance self-test command missing in README.md" \
  bash "$EXPORTER" --root "$readme_command_drift" --output "$tmp_dir/readme-command-drift-out"

echo "[public-dependency-handoff-test] all assertions passed (1 valid manifest plus $NEGATIVE_SCENARIO_COUNT negative scenarios)"
