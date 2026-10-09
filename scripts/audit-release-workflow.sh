#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW_FILE="${ANDROID_RELEASE_AUDIT_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/android-release.yml}"
CI_WORKFLOW_FILE="${ANDROID_RELEASE_AUDIT_CI_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/android-ci.yml}"
BRANCH_WORKFLOW_FILE="${ANDROID_RELEASE_AUDIT_BRANCH_WORKFLOW_FILE:-$ROOT_DIR/.github/workflows/branch-flow.yml}"
BUILD_FILE="${ANDROID_RELEASE_AUDIT_BUILD_FILE:-$ROOT_DIR/build.gradle}"
APP_BUILD_FILE="${ANDROID_RELEASE_AUDIT_APP_BUILD_FILE:-$ROOT_DIR/app/build.gradle}"
BACKUP_BUILD_FILE="${ANDROID_RELEASE_AUDIT_BACKUP_BUILD_FILE:-$ROOT_DIR/public-shared-features-backup/build.gradle}"
WALLET_BUILD_FILE="${ANDROID_RELEASE_AUDIT_WALLET_BUILD_FILE:-$ROOT_DIR/feature-wallet-impl/build.gradle}"
MOONPAY_PROVIDER_FILE="${ANDROID_RELEASE_AUDIT_MOONPAY_PROVIDER_FILE:-$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProvider.kt}"
MOONPAY_TEST_FILE="${ANDROID_RELEASE_AUDIT_MOONPAY_TEST_FILE:-$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProviderTest.kt}"
DISTRIBUTION_PREFLIGHT_FILE="${ANDROID_RELEASE_AUDIT_DISTRIBUTION_PREFLIGHT_FILE:-$ROOT_DIR/scripts/verify-android-distribution-preflight.sh}"
DISTRIBUTION_PREFLIGHT_TEST_FILE="${ANDROID_RELEASE_AUDIT_DISTRIBUTION_PREFLIGHT_TEST_FILE:-$ROOT_DIR/scripts/test-android-distribution-preflight.sh}"
GRADLE_DISTRIBUTION_TEST_FILE="${ANDROID_RELEASE_AUDIT_GRADLE_DISTRIBUTION_TEST_FILE:-$ROOT_DIR/scripts/test-android-gradle-distribution-guards.sh}"
AAB_ALIGNMENT_FILE="${ANDROID_RELEASE_AUDIT_AAB_ALIGNMENT_FILE:-$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py}"
AAB_ALIGNMENT_TEST_FILE="${ANDROID_RELEASE_AUDIT_AAB_ALIGNMENT_TEST_FILE:-$ROOT_DIR/scripts/test-android-aab-native-page-alignment.sh}"
RELEASE_OVERLAY_FILE="${ANDROID_RELEASE_AUDIT_RELEASE_OVERLAY_FILE:-$ROOT_DIR/scripts/restore-release-overlays.sh}"
RELEASE_OVERLAY_TEST_FILE="${ANDROID_RELEASE_AUDIT_RELEASE_OVERLAY_TEST_FILE:-$ROOT_DIR/scripts/test-restore-release-overlays.sh}"
RELEASE_CLEANUP_FILE="${ANDROID_RELEASE_AUDIT_RELEASE_CLEANUP_FILE:-$ROOT_DIR/scripts/cleanup-release-overlays.sh}"
RELEASE_CLEANUP_TEST_FILE="${ANDROID_RELEASE_AUDIT_RELEASE_CLEANUP_TEST_FILE:-$ROOT_DIR/scripts/test-cleanup-release-overlays.sh}"
APP_GITIGNORE_FILE="${ANDROID_RELEASE_AUDIT_APP_GITIGNORE_FILE:-$ROOT_DIR/app/.gitignore}"
EXPECTED_SEMVER_PATTERN='^v(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(-((0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*)(\.(0|[1-9][0-9]*|[0-9]*[A-Za-z-][0-9A-Za-z-]*))*))?(\+[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$'

fail() {
  echo "[release-workflow-audit][error] $*" >&2
  exit 1
}

require_text() {
  local value="$1"
  local label="$2"
  grep -F -- "$value" "$WORKFLOW_FILE" |
    grep -Eq '^[[:space:]]*[^#[:space:]]' || fail "$label"
}

require_active_text_in_file() {
  local file="$1"
  local value="$2"
  local label="$3"
  grep -F -- "$value" "$file" |
    grep -Eq '^[[:space:]]*[^#[:space:]]' || fail "$label"
}

audit_pinned_actions() {
  local file="$1"
  local unpinned_actions
  unpinned_actions="$(grep -E '^[[:space:]]*uses:[[:space:]]+' "$file" | grep -Ev '@[0-9a-f]{40}([[:space:]]|$)' || true)"
  if [[ -n "$unpinned_actions" ]]; then
    fail "Every GitHub action must be pinned to a full commit SHA in $file: $unpinned_actions"
  fi
}

[[ -f "$WORKFLOW_FILE" ]] || fail "Missing release workflow: $WORKFLOW_FILE"
[[ -f "$CI_WORKFLOW_FILE" ]] || fail "Missing Android CI workflow: $CI_WORKFLOW_FILE"
[[ -f "$BRANCH_WORKFLOW_FILE" ]] || fail "Missing branch-flow workflow: $BRANCH_WORKFLOW_FILE"
[[ -f "$BUILD_FILE" ]] || fail "Missing root build file: $BUILD_FILE"
[[ -f "$APP_BUILD_FILE" ]] || fail "Missing app build file: $APP_BUILD_FILE"
[[ -f "$BACKUP_BUILD_FILE" ]] || fail "Missing passkey backup build file: $BACKUP_BUILD_FILE"
[[ -f "$WALLET_BUILD_FILE" ]] || fail "Missing wallet implementation build file: $WALLET_BUILD_FILE"
[[ -f "$MOONPAY_PROVIDER_FILE" ]] || fail "Missing MoonPay provider: $MOONPAY_PROVIDER_FILE"
[[ -f "$MOONPAY_TEST_FILE" ]] || fail "Missing MoonPay provider tests: $MOONPAY_TEST_FILE"
[[ -x "$DISTRIBUTION_PREFLIGHT_FILE" ]] || fail "Missing executable Android distribution preflight: $DISTRIBUTION_PREFLIGHT_FILE"
[[ -x "$DISTRIBUTION_PREFLIGHT_TEST_FILE" ]] || fail "Missing executable Android distribution preflight tests: $DISTRIBUTION_PREFLIGHT_TEST_FILE"
[[ -x "$GRADLE_DISTRIBUTION_TEST_FILE" ]] || fail "Missing executable Android Gradle distribution tests: $GRADLE_DISTRIBUTION_TEST_FILE"
[[ -x "$AAB_ALIGNMENT_FILE" ]] || fail "Missing executable AAB native-alignment verifier: $AAB_ALIGNMENT_FILE"
[[ -x "$AAB_ALIGNMENT_TEST_FILE" ]] || fail "Missing executable AAB native-alignment tests: $AAB_ALIGNMENT_TEST_FILE"
[[ -x "$RELEASE_OVERLAY_FILE" ]] || fail "Missing executable release overlay restore script: $RELEASE_OVERLAY_FILE"
[[ -x "$RELEASE_OVERLAY_TEST_FILE" ]] || fail "Missing executable release overlay restore tests: $RELEASE_OVERLAY_TEST_FILE"
[[ -x "$RELEASE_CLEANUP_FILE" ]] || fail "Missing executable release overlay cleanup script: $RELEASE_CLEANUP_FILE"
[[ -x "$RELEASE_CLEANUP_TEST_FILE" ]] || fail "Missing executable release overlay cleanup tests: $RELEASE_CLEANUP_TEST_FILE"
[[ -f "$APP_GITIGNORE_FILE" ]] || fail "Missing app .gitignore: $APP_GITIGNORE_FILE"

require_text 'release_tag:' 'Release workflow must require an explicit release tag.'
require_text 'DISPATCH_REF: ${{ github.ref }}' 'Release workflow must inspect the dispatch ref.'
require_text 'refs/heads/master' 'Release workflow must reject dispatches outside master.'
require_text "SEMVER_PATTERN='$EXPECTED_SEMVER_PATTERN'" 'Release workflow must use the strict v-prefixed SemVer validator.'
require_text 'refs/tags/$RELEASE_TAG_INPUT' 'Release workflow must resolve an actual Git tag.'
require_text 'git merge-base --is-ancestor "$release_commit" "origin/master"' 'Release tag must be on master.'
require_text 'actions: read' 'Release workflow must grant read-only access to authoritative workflow runs.'
require_text '/actions/workflows/android-ci.yml/runs?branch=master&head_sha=$RELEASE_COMMIT&event=push&status=completed&per_page=100' 'Release workflow must query the Android CI workflow for the tagged commit on master.'
require_text '.head_sha == $commit' 'Release workflow must bind prior CI to the exact tagged commit.'
require_text '.head_branch == "master"' 'Release workflow must bind prior CI to master.'
require_text '.event == "push"' 'Release workflow must require a post-merge push CI run.'
require_text '.status == "completed"' 'Release workflow must require completed prior CI.'
require_text 'latest_conclusion="$(jq -r' 'Release workflow must parse the authoritative workflow conclusion.'
require_text '"$latest_conclusion" != "success"' 'Release workflow must reject any non-successful prior CI conclusion.'
require_text 'SKIP_AUTO_VERSION_BUMP: "true"' 'CI release builds must disable automatic version mutation.'
require_text 'play_testing_track:' 'Release workflow must expose an explicit optional Play testing track.'
require_text 'play_release_status:' 'Release workflow must expose an explicit Play release status.'
require_text 'default: none' 'Play testing publication must default to disabled.'
require_text 'default: draft' 'Play testing publication must default to draft.'
require_text 'internal|alpha|beta)' 'Play testing workflow must use the exact non-production track allowlist.'
require_text './scripts/verify-android-distribution-preflight.sh --release-signing' 'Release build must reject debug or malformed signing inputs before Gradle.'
require_text './scripts/verify-android-distribution-preflight.sh --play-testing' 'Play testing publication must run its credential/track preflight.'
require_text 'bash ./scripts/test-android-distribution-preflight.sh' 'Tagged release source must exercise distribution preflight adversarial tests.'
require_text 'bash ./scripts/test-android-aab-native-page-alignment.sh' 'Tagged release source must exercise AAB native-alignment adversarial tests.'
require_active_text_in_file "$CI_WORKFLOW_FILE" 'bash ./scripts/test-android-aab-native-page-alignment.sh' 'Android CI must exercise AAB native-alignment adversarial tests.'
require_text 'bash ./scripts/test-restore-release-overlays.sh' 'Tagged release source must exercise atomic overlay restoration adversarial tests.'
require_text 'bash ./scripts/test-cleanup-release-overlays.sh' 'Tagged release source must exercise release-overlay cleanup adversarial tests.'
require_text './scripts/test-android-gradle-distribution-guards.sh' 'Release workflow must exercise real Gradle task-graph distribution guards.'
require_text './gradlew :app:publishReleaseBundle' 'Play testing publication must use the production-signed release bundle variant.'
require_text 'PLAY_TRACK: ${{ inputs.play_testing_track }}' 'Play testing publication must bind the exact reviewed track input.'
require_text 'PLAY_RELEASE_STATUS: ${{ inputs.play_release_status }}' 'Play testing publication must bind the exact reviewed release status input.'
require_text 'PLAY_ARTIFACT_DIR: ${{ github.workspace }}/build/release-artifact' 'Play publication must bind GPP to the exact staged and attested artifact directory.'
require_text 'RELEASE_OVERLAY_MODE=play ./scripts/restore-release-overlays.sh' 'Play credential must be restored only in the conditional Play publication path.'
require_text './scripts/cleanup-release-overlays.sh' 'Release workflow must remove ephemeral release credentials.'
require_text 'PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}' 'Play publication must explicitly scope its service-account secret.'
require_text 'tag_version_name="${RELEASE_TAG#v}"' 'Release workflow must derive versionName from the SemVer tag.'
require_text '"$version_name" != "$tag_version_name"' 'Release workflow must reject tag/versionName mismatches.'
require_text '"$version_code" =~ ^[1-9][0-9]*$' 'Release workflow must require a positive versionCode.'
require_text 'VERSION_PROPERTIES_SHA' 'Release workflow must attest immutable version.properties content.'
require_text 'git diff --quiet -- versioning/version.properties' 'Release workflow must reject version file changes.'
require_text 'bash ./scripts/audit-release-workflow.sh' 'Tagged release source must audit its own release controls.'
require_text 'bash ./scripts/test-public-artifact-provenance-audit.sh' 'Tagged release source must run provenance adversarial tests.'
require_text 'bash ./scripts/test-fearless-utils-derived-tree.sh' 'Release workflow must exercise the fearless-utils derived-tree adversarial contract.'
require_text './scripts/ensure-fearless-utils.sh' 'Release workflow must verify the pinned fearless-utils derived tree.'
require_text './scripts/audit-public-artifacts.sh --release --strict-provenance' 'Release workflow must run strict provenance.'
require_text './gradlew :app:bundleRelease' 'Release workflow must use the fixed release bundle task.'
require_text 'Verify release AAB 16 KB native compatibility' 'Release workflow must verify the built AAB native alignment.'
require_text 'python3 ./scripts/verify-android-aab-native-page-alignment.py "${bundles[0]}"' 'Release workflow must verify the exact selected release AAB native alignment.'
require_text 'Expected exactly one release AAB' 'Release workflow must reject ambiguous/missing artifacts.'
require_text "grep -Ei '^META-INF/[^/]+\\.(RSA|DSA|EC)\$'" 'Release workflow must reject unsigned AABs.'
require_text 'jarsigner -verify -verbose' 'Release workflow must verify the staged bundle signature.'
require_text "grep -Fq 'jar verified.'" 'Release workflow must require positive jarsigner verification.'
require_text 'keytool -list -v' 'Release workflow must inspect the configured release certificate.'
require_text '-storepass:env CI_KEYSTORE_PASS' 'Release certificate inspection must not expose its password on the command line.'
require_text 'keytool -printcert -jarfile' 'Release workflow must inspect the artifact signing certificate.'
require_text '"$artifact_cert_sha256" != "$expected_cert_sha256"' 'Artifact signer must match the configured release keystore.'
require_text 'signingCertificateSha256: $signingCertificateSha256' 'Release manifest must attest the signing certificate.'
require_text 'actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02' 'Artifact upload action must be pinned.'
require_text 'actions/attest-build-provenance@e8998f949152b193b063cb0ec769d69d929409be' 'Build provenance action must be pinned.'
require_text 'subject-path: build/release-artifact/Fearless-${{ inputs.release_tag }}.aab' 'Attestation must target the staged release bundle.'
require_text 'id-token: write' 'Release attestation requires id-token: write.'
require_text 'attestations: write' 'Release attestation requires attestations: write.'

for runtime_secret in \
  RAMP_TOKEN_RELEASE \
  COINBASE_APP_ID \
  MOONPAY_PRODUCTION_PUBLIC_KEY \
  X1_ENDPOINT_URL_RELEASE \
  X1_WIDGET_ID_RELEASE \
  WEB_CLIENT_ID_RELEASE \
  FL_BLAST_API_ETHEREUM_KEY \
  FL_BLAST_API_BSC_KEY \
  FL_BLAST_API_SEPOLIA_KEY \
  FL_BLAST_API_GOERLI_KEY \
  FL_BLAST_API_POLYGON_KEY \
  FL_ANDROID_ETHERSCAN_API_KEY \
  FL_ANDROID_BSCSCAN_API_KEY \
  FL_ANDROID_POLYGONSCAN_API_KEY \
  FL_ANDROID_OKLINK_API_KEY \
  FL_ANDROID_OPMAINNET_API_KEY \
  FL_WALLET_CONNECT_PROJECT_ID \
  FL_DWELLIR_API_KEY \
  FL_ANDROID_TON_API_KEY \
  FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY; do
  require_text \
    "$runtime_secret: \${{ secrets.$runtime_secret }}" \
    "Android release workflow must provide $runtime_secret."
  require_active_text_in_file \
    "$DISTRIBUTION_PREFLIGHT_FILE" \
    "$runtime_secret" \
    "Android distribution preflight must require $runtime_secret."
  require_active_text_in_file \
    "$ROOT_DIR/scripts/audit-public-artifacts.sh" \
    "$runtime_secret" \
    "Release artifact audit must require $runtime_secret."
done

if grep -Eq '^[[:space:]]*-[[:space:]]*production[[:space:]]*$' "$WORKFLOW_FILE"; then
  fail "The Play testing workflow must never expose production as a selectable track."
fi
play_secret_occurrences="$(grep -Fc 'PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}' "$WORKFLOW_FILE" || true)"
[[ "$play_secret_occurrences" -eq 1 ]] ||
  fail "Play service-account secret must occur exactly once in the conditional restore step."
play_restore_block="$(
  awk '
    $0 == "      - name: Restore least-privilege Play publishing credential" { capture = 1 }
    capture { print }
    capture && $0 == "        run: RELEASE_OVERLAY_MODE=play ./scripts/restore-release-overlays.sh" { exit }
  ' "$WORKFLOW_FILE"
)"
expected_play_restore_block='      - name: Restore least-privilege Play publishing credential
        if: ${{ inputs.play_testing_track != '\''none'\'' }}
        env:
          PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}
        run: RELEASE_OVERLAY_MODE=play ./scripts/restore-release-overlays.sh'
[[ "$play_restore_block" == "$expected_play_restore_block" ]] ||
  fail "Play service-account secret must be scoped to the exact conditional restore step."
cleanup_block="$(
  awk '
    $0 == "      - name: Remove ephemeral release credentials" { capture = 1 }
    capture { print }
    capture && $0 == "        run: ./scripts/cleanup-release-overlays.sh" { exit }
  ' "$WORKFLOW_FILE"
)"
expected_cleanup_block='      - name: Remove ephemeral release credentials
        if: ${{ always() }}
        run: ./scripts/cleanup-release-overlays.sh'
[[ "$cleanup_block" == "$expected_cleanup_block" ]] ||
  fail "Ephemeral release credentials must be removed by the exact always-running cleanup step."
if grep -Fq -- '-storepass "$CI_KEYSTORE_PASS"' "$WORKFLOW_FILE"; then
  fail "Release workflow must pass the keystore password through keytool's environment option."
fi

if grep -Eq '^[[:space:]]+(build_task|strict_provenance):' "$WORKFLOW_FILE"; then
  fail "Release safety controls may not be workflow inputs."
fi

audit_pinned_actions "$WORKFLOW_FILE"
audit_pinned_actions "$CI_WORKFLOW_FILE"
audit_pinned_actions "$BRANCH_WORKFLOW_FILE"

require_active_text_in_file "$CI_WORKFLOW_FILE" \
  'bash ./scripts/test-fearless-utils-derived-tree.sh' \
  'Android CI must exercise the fearless-utils derived-tree adversarial contract.'
require_active_text_in_file "$CI_WORKFLOW_FILE" \
  './scripts/ensure-fearless-utils.sh' \
  'Android CI must verify the pinned fearless-utils derived tree.'

grep -Fq "def ciEnvironmentMarkers = ['CI', 'GITHUB_ACTIONS', 'JENKINS_URL', 'BUILD_ID', 'CI_BUILD_ID']" "$BUILD_FILE" ||
  fail "Root build must detect supported GitHub, Jenkins, generic, and versioned CI environments before auto-bumping."
grep -Fq 'boolean isCiEnvironment = ciEnvironmentMarkers.any' "$BUILD_FILE" ||
  fail "Root build must evaluate every CI environment marker before auto-bumping."
grep -Fq "!value.equalsIgnoreCase('false') && value != '0'" "$BUILD_FILE" ||
  fail "Root build must distinguish explicit false CI markers from active CI environments."
grep -Fq 'boolean autoBumpAllowed = !skipAutoBump && !isCiEnvironment' "$BUILD_FILE" ||
  fail "Root build may not mutate version.properties in any CI environment."
grep -Fq "details.requested.group == 'com.madgag.spongycastle'" "$BUILD_FILE" ||
  fail "Root build must intercept legacy Spongy Castle PKIX/PGP artifacts."
grep -Fq "details.requested.name in ['bcpkix-jdk15on', 'bcpg-jdk15on']" "$BUILD_FILE" ||
  fail "Root build must exclude both unused Spongy Castle PKIX and PGP artifacts."
grep -Fq "details.useTarget('com.madgag.spongycastle:prov:1.58.0.0')" "$BUILD_FILE" ||
  fail "Root build must retain only the Spongy Castle provider required by fearless-utils."
grep -Fq "fatal 'CredentialDependency', 'TrustAllX509TrustManager'" "$APP_BUILD_FILE" ||
  fail "App lint must fail closed on missing Credential Manager transport or trust-all TLS code."
for marker in \
  'def internalAppSharingRequested = gradle.startParameter.taskNames.any' \
  'releaseGoogleServicesFile = file("src/release/google-services.json")' \
  'generatedIasGoogleServicesFile = file("src/internalAppSharing/google-services.json")' \
  'Files.isSymbolicLink(releaseGoogleServicesFile.toPath())' \
  'releaseGoogleServicesFile.length() > 65536' \
  'matchingReleaseClients.size() != 1' \
  'Refusing to overwrite a divergent pre-existing IAS Firebase configuration.' \
  'gradle.buildFinished' \
  'StandardCopyOption.REPLACE_EXISTING' \
  'generatedIasGoogleServicesFile.setReadable(false, false)' \
  'Files.setPosixFilePermissions(generatedIasPath, ownerOnlyPermissions)' \
  'Arrays.equals(releaseGoogleServicesFile.bytes, generatedIasGoogleServicesFile.bytes)' \
  'Files.deleteIfExists(generatedIasPath)' \
  'tasks.register("verifyInternalAppSharingConfiguration")' \
  'iasVerificationForceFailure' \
  'def releaseSigningInputs = [' \
  'playRelease {' \
  'signingConfig signingConfigs.playRelease' \
  'internalAppSharing {' \
  'initWith release' \
  "matchingFallbacks = ['release']" \
  "versionNameSuffix '-ias'" \
  'signingConfig signingConfigs.debug' \
  'def effectivePlayTrack = requestedPlayTrack ?: "internal"' \
  'def effectivePlayReleaseStatus = requestedPlayReleaseStatus ?: "draft"' \
  'gradle.taskGraph.whenReady' \
  'if (!releaseArtifactTasks.isEmpty()) {' \
  'def forbiddenIasPublisherTasks = appTasks.findAll' \
  'if (!forbiddenIasPublisherTasks.isEmpty()) {' \
  'The debug-signed internalAppSharing variant is IAS-file-only and must never run a Play publisher task.' \
  'if (!normalPlayPublishingTasks.isEmpty()) {' \
  'Play release signing is incomplete; missing explicit inputs:' \
  'The Android debug keystore cannot sign a Play release artifact.' \
  'An Android Debug certificate cannot sign a Play release artifact.' \
  'Arrays.equals(releaseCertificate.encoded, debugCertificate.encoded)' \
  'Play publishing requires explicit PLAY_TRACK and PLAY_RELEASE_STATUS values.' \
  'Play publishing requires an explicit PLAY_ARTIFACT_DIR.' \
  'PLAY_ARTIFACT_DIR must be the root build/release-artifact directory.' \
  'artifactDir = configuredPlayArtifactDirectory' \
  'requireNoSymlinkComponents(configuredReleaseKeystore, "CI_KEYSTORE_PATH")' \
  'requireNoSymlinkComponents(serviceAccountFile, "CI_PLAY_KEY")' \
  'Production publishing requires ALLOW_PLAY_PRODUCTION_UPLOAD=true.'; do
  require_active_text_in_file "$APP_BUILD_FILE" "$marker" "Android distribution-mode guard missing: $marker"
done
if grep -Eq 'resolvedStoreFile[[:space:]]*=[[:space:]]*debug|(debugSigningConfig|signingConfigs\.debug)\.(storeFile|storePassword|keyAlias|keyPassword)|signingConfigs\.ci' "$APP_BUILD_FILE"; then
  fail "Release signing must never fall back to the Android debug signing configuration."
fi
require_active_text_in_file "$APP_GITIGNORE_FILE" \
  '/src/internalAppSharing/google-services.json' \
  'Generated Internal App Sharing Firebase configuration must be ignored.'

for marker in \
  'umask 077' \
  'RELEASE_OVERLAY_MODE must be exactly release or play.' \
  'validate_output_destination "$release_google_services" GOOGLE_SERVICES_RELEASE_JSON_B64' \
  'validate_output_destination "$keystore_path" ANDROID_RELEASE_KEYSTORE_B64' \
  'validate_output_destination "$release_sentinel" "release overlay sentinel"' \
  'validate_base64_payload GOOGLE_SERVICES_RELEASE_JSON_B64 262144' \
  'validate_base64_payload ANDROID_RELEASE_KEYSTORE_B64 16777216' \
  'create_active_sentinel "$release_sentinel" "release overlay sentinel"' \
  '[[ ! -e "$sentinel" && ! -L "$sentinel" ]]' \
  'if ! ln "$temporary_sentinel" "$sentinel"; then' \
  'temporary_output="$(mktemp "$parent/.fearless-release-overlay.XXXXXX")"' \
  'chmod 600 "$temporary_output"' \
  'mv -f "$temporary_output" "$output"' \
  '[[ "$permissions" == "600" ]]' \
  'decode_base64_to_file GOOGLE_SERVICES_RELEASE_JSON_B64 "$release_google_services" 262144' \
  'decode_base64_to_file ANDROID_RELEASE_KEYSTORE_B64 "$keystore_path" 16777216' \
  'decode_base64_to_file PLAY_SERVICE_ACCOUNT_JSON_B64 "$play_key_path" 65536'; do
  require_active_text_in_file "$RELEASE_OVERLAY_FILE" "$marker" "Release overlay hardening missing: $marker"
done

for marker in \
  'invalid second payload committed the first release overlay' \
  'Firebase parent symlink' \
  'keystore output symlink' \
  'release sentinel symlink target was truncated' \
  'oversized Play credential'; do
  require_active_text_in_file "$RELEASE_OVERLAY_TEST_FILE" "$marker" "Release overlay adversarial coverage missing: $marker"
done

for marker in \
  'Cleanup is restricted to an explicit CI environment.' \
  'sentinel_is_active "$release_sentinel" "release overlay sentinel"' \
  'sentinel_is_active "$play_sentinel" "Play overlay sentinel"' \
  'must be a regular, non-symlink file when present.' \
  '[[ "$permissions" == "600" ]]' \
  'if [[ "$release_active" == true ]]; then' \
  'remove_ephemeral_file "$release_google_services" "release Firebase overlay"' \
  'remove_ephemeral_file "$overlay_dir/fearless-upload.jks" "release keystore overlay"' \
  'remove_ephemeral_file "$overlay_dir/play-service-account.json" "Play service-account overlay"' \
  'assert_no_parent_symlinks "$path" "$label"'; do
  require_active_text_in_file "$RELEASE_CLEANUP_FILE" "$marker" "Release overlay cleanup guard missing: $marker"
done

for marker in \
  'cleanup outside CI' \
  'cleanup removed Firebase config without an active overlay sentinel' \
  'cleanup through parent symlink' \
  'malformed release sentinel cleanup removed Firebase credentials' \
  'malformed Play sentinel cleanup removed the service-account credential'; do
  require_active_text_in_file "$RELEASE_CLEANUP_TEST_FILE" "$marker" "Release overlay cleanup coverage missing: $marker"
done

for marker in \
  'bundleRelease accepted missing production signing inputs' \
  'publishInternalAppSharingBundle' \
  'debug-signed IAS variant reached a normal Play publisher task' \
  'forced IAS verification failure unexpectedly passed' \
  'divergent pre-existing IAS Firebase file was accepted' \
  'symlink IAS Firebase file was accepted'; do
  require_active_text_in_file "$GRADLE_DISTRIBUTION_TEST_FILE" "$marker" "Android Gradle distribution coverage missing: $marker"
done

for marker in \
  'REQUIRED_PAGE_ALIGNMENT = 16 * 1024' \
  'EXPECTED_MACHINES = {' \
  'archive.testzip()' \
  'AAB contains duplicate archive entry names.' \
  'AAB is missing required native ABIs:' \
  'Native library set for' \
  'PT_LOAD alignment' \
  'misaligned PT_LOAD offset/address pair'; do
  require_active_text_in_file "$AAB_ALIGNMENT_FILE" "$marker" "AAB native-alignment verifier missing: $marker"
done
for marker in \
  'low-alignment' \
  'non-power-alignment' \
  'misaligned-address' \
  'wrong-machine' \
  'missing-load' \
  'duplicate-entry' \
  'unsupported-abi' \
  'symlink-parent AAB' \
  'oversized AAB'; do
  require_active_text_in_file "$AAB_ALIGNMENT_TEST_FILE" "$marker" "AAB native-alignment adversarial coverage missing: $marker"
done
if grep -Eq 'base64 (-d|-D)[[:space:]]*>[[:space:]]*"\$output"' "$RELEASE_OVERLAY_FILE"; then
  fail "Release overlays must decode atomically through a private temporary file."
fi

for marker in \
  '$label must not be a symlink.' \
  '-storepass:env CI_KEYSTORE_PASS' \
  'An Android Debug certificate cannot sign a Play release.' \
  'internal|alpha|beta)' \
  'PLAY_RELEASE_STATUS must be exactly draft or completed.' \
  '.type == "service_account"' \
  '.token_uri == "https://oauth2.googleapis.com/token"' \
  'PLAY_ARTIFACT_DIR must contain exactly one regular AAB.' \
  'The staged AAB checksum sidecar does not match the artifact.' \
  'The staged AAB provenance manifest does not match this release invocation.' \
  'The staged AAB signer does not match the configured upload key.'; do
  require_active_text_in_file "$DISTRIBUTION_PREFLIGHT_FILE" "$marker" "Android distribution preflight guard missing: $marker"
done

for marker in \
  'valid completed/beta Play testing inputs were rejected' \
  'debug signing certificate' \
  "invalid testing track '\$track'" \
  'attacker token endpoint' \
  'oversized service account' \
  'missing release runtime value $variable' \
  'placeholder release runtime value' \
  'embedded placeholder release runtime value' \
  'control-bearing release runtime value' \
  'whitespace-bearing release runtime value' \
  'non-HTTPS X1 endpoint' \
  'malformed Google OAuth client' \
  'non-HTTPS optional TON indexer'; do
  require_active_text_in_file "$DISTRIBUTION_PREFLIGHT_TEST_FILE" "$marker" "Android distribution adversarial coverage missing: $marker"
done
grep -Fq 'api libs.credentials.play.services.auth' "$BACKUP_BUILD_FILE" ||
  fail "Passkey backup must expose the Google Play Services Credential Manager transport to the app."

if grep -Eq 'MOONPAY_(TEST|PRODUCTION)_SECRET|MOONPAY_PRIVATE_KEY' \
  "$WORKFLOW_FILE" "$CI_WORKFLOW_FILE" "$WALLET_BUILD_FILE" "$MOONPAY_PROVIDER_FILE"; then
  fail "Android release sources must never accept or embed a MoonPay server secret."
fi
if grep -Eq 'hmacSHA256|["'\'' ]signature["'\'' ]|["'\'']walletAddress["'\''][[:space:]]+to' "$MOONPAY_PROVIDER_FILE"; then
  fail "MoonPay URL signing and wallet-address prefill must remain server-side."
fi
grep -Fq 'setOf("buy-sandbox.moonpay.com", "buy.moonpay.com")' "$MOONPAY_PROVIDER_FILE" ||
  fail "MoonPay hosted checkout must use the exact sandbox/production host allowlist."
grep -Fq '"showWalletAddressForm" to "true"' "$MOONPAY_PROVIDER_FILE" ||
  fail "Unsigned MoonPay checkout must require manual wallet-address entry."
for marker in \
  'supports only the production and sandbox MoonPay hosts' \
  'encodes public key and currency query injection as data' \
  'rejects missing oversized and control-bearing public keys' \
  'rejects malformed currency color and redirect values'; do
  grep -Fq "$marker" "$MOONPAY_TEST_FILE" || fail "MoonPay adversarial test missing: $marker"
done

for valid_tag in v0.0.0 v4.2.0 v1.2.3-rc.1 v1.2.3-alpha-1+build.7; do
  [[ "$valid_tag" =~ $EXPECTED_SEMVER_PATTERN ]] || fail "Internal SemVer validator rejected $valid_tag."
done
for invalid_tag in 1.2.3 v01.2.3 v1.02.3 v1.2.03 v1.2 v1.2.3-01 v1.2.3-rc..1 v1.2.3+; do
  [[ ! "$invalid_tag" =~ $EXPECTED_SEMVER_PATTERN ]] || fail "Internal SemVer validator accepted $invalid_tag."
done

echo "[release-workflow-audit] passed"
