#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AUDIT="$ROOT_DIR/scripts/audit-release-workflow.sh"
tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[release-workflow-audit-test][error] $*" >&2
  exit 1
}

reset_fixtures() {
  cp "$ROOT_DIR/.github/workflows/android-release.yml" "$tmp_dir/android-release.yml"
  cp "$ROOT_DIR/.github/workflows/android-ci.yml" "$tmp_dir/android-ci.yml"
  cp "$ROOT_DIR/.github/workflows/branch-flow.yml" "$tmp_dir/branch-flow.yml"
  cp "$ROOT_DIR/build.gradle" "$tmp_dir/build.gradle"
  cp "$ROOT_DIR/app/build.gradle" "$tmp_dir/app-build.gradle"
  cp "$ROOT_DIR/public-shared-features-backup/build.gradle" "$tmp_dir/backup-build.gradle"
  cp "$ROOT_DIR/feature-wallet-impl/build.gradle" "$tmp_dir/wallet-build.gradle"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProvider.kt" "$tmp_dir/MoonPayProvider.kt"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProviderTest.kt" "$tmp_dir/MoonPayProviderTest.kt"
  cp "$ROOT_DIR/scripts/verify-android-distribution-preflight.sh" "$tmp_dir/distribution-preflight.sh"
  cp "$ROOT_DIR/scripts/test-android-distribution-preflight.sh" "$tmp_dir/distribution-preflight-test.sh"
  cp "$ROOT_DIR/scripts/test-android-gradle-distribution-guards.sh" "$tmp_dir/gradle-distribution-test.sh"
  cp "$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py" "$tmp_dir/aab-alignment.py"
  cp "$ROOT_DIR/scripts/test-android-aab-native-page-alignment.sh" "$tmp_dir/aab-alignment-test.sh"
  chmod +x "$tmp_dir/aab-alignment.py" "$tmp_dir/aab-alignment-test.sh"
  cp "$ROOT_DIR/scripts/restore-release-overlays.sh" "$tmp_dir/restore-release-overlays.sh"
  cp "$ROOT_DIR/scripts/test-restore-release-overlays.sh" "$tmp_dir/restore-release-overlays-test.sh"
  cp "$ROOT_DIR/scripts/cleanup-release-overlays.sh" "$tmp_dir/cleanup-release-overlays.sh"
  cp "$ROOT_DIR/scripts/test-cleanup-release-overlays.sh" "$tmp_dir/cleanup-release-overlays-test.sh"
  cp "$ROOT_DIR/app/.gitignore" "$tmp_dir/app.gitignore"
}

run_audit() {
  ANDROID_RELEASE_AUDIT_WORKFLOW_FILE="$tmp_dir/android-release.yml" \
    ANDROID_RELEASE_AUDIT_CI_WORKFLOW_FILE="$tmp_dir/android-ci.yml" \
    ANDROID_RELEASE_AUDIT_BRANCH_WORKFLOW_FILE="$tmp_dir/branch-flow.yml" \
    ANDROID_RELEASE_AUDIT_BUILD_FILE="$tmp_dir/build.gradle" \
    ANDROID_RELEASE_AUDIT_APP_BUILD_FILE="$tmp_dir/app-build.gradle" \
    ANDROID_RELEASE_AUDIT_BACKUP_BUILD_FILE="$tmp_dir/backup-build.gradle" \
    ANDROID_RELEASE_AUDIT_WALLET_BUILD_FILE="$tmp_dir/wallet-build.gradle" \
    ANDROID_RELEASE_AUDIT_MOONPAY_PROVIDER_FILE="$tmp_dir/MoonPayProvider.kt" \
    ANDROID_RELEASE_AUDIT_MOONPAY_TEST_FILE="$tmp_dir/MoonPayProviderTest.kt" \
    ANDROID_RELEASE_AUDIT_DISTRIBUTION_PREFLIGHT_FILE="$tmp_dir/distribution-preflight.sh" \
    ANDROID_RELEASE_AUDIT_DISTRIBUTION_PREFLIGHT_TEST_FILE="$tmp_dir/distribution-preflight-test.sh" \
    ANDROID_RELEASE_AUDIT_GRADLE_DISTRIBUTION_TEST_FILE="$tmp_dir/gradle-distribution-test.sh" \
    ANDROID_RELEASE_AUDIT_AAB_ALIGNMENT_FILE="$tmp_dir/aab-alignment.py" \
    ANDROID_RELEASE_AUDIT_AAB_ALIGNMENT_TEST_FILE="$tmp_dir/aab-alignment-test.sh" \
    ANDROID_RELEASE_AUDIT_RELEASE_OVERLAY_FILE="$tmp_dir/restore-release-overlays.sh" \
    ANDROID_RELEASE_AUDIT_RELEASE_OVERLAY_TEST_FILE="$tmp_dir/restore-release-overlays-test.sh" \
    ANDROID_RELEASE_AUDIT_RELEASE_CLEANUP_FILE="$tmp_dir/cleanup-release-overlays.sh" \
    ANDROID_RELEASE_AUDIT_RELEASE_CLEANUP_TEST_FILE="$tmp_dir/cleanup-release-overlays-test.sh" \
    ANDROID_RELEASE_AUDIT_APP_GITIGNORE_FILE="$tmp_dir/app.gitignore" \
    "$AUDIT" >/dev/null 2>&1
}

expect_failure() {
  local label="$1"
  if run_audit; then
    fail "$label was accepted"
  fi
}

remove_workflow_line() {
  local needle="$1"
  awk -v needle="$needle" 'index($0, needle) == 0 { print }' \
    "$tmp_dir/android-release.yml" > "$tmp_dir/android-release.next"
  mv "$tmp_dir/android-release.next" "$tmp_dir/android-release.yml"
}

remove_ci_workflow_line() {
  local needle="$1"
  awk -v needle="$needle" 'index($0, needle) == 0 { print }' \
    "$tmp_dir/android-ci.yml" > "$tmp_dir/android-ci.next"
  mv "$tmp_dir/android-ci.next" "$tmp_dir/android-ci.yml"
}

comment_workflow_line() {
  local needle="$1"
  awk -v needle="$needle" '
    index($0, needle) != 0 { print "# " $0; next }
    { print }
  ' "$tmp_dir/android-release.yml" > "$tmp_dir/android-release.next"
  mv "$tmp_dir/android-release.next" "$tmp_dir/android-release.yml"
}

reset_fixtures
run_audit || fail "valid release workflow was rejected"

reset_fixtures
remove_workflow_line 'refs/heads/master'
expect_failure "release without master guard"

reset_fixtures
remove_workflow_line "SEMVER_PATTERN='^v"
expect_failure "release without SemVer validation"

reset_fixtures
remove_workflow_line 'git merge-base --is-ancestor'
expect_failure "release tag outside master"

reset_fixtures
comment_workflow_line 'git merge-base --is-ancestor'
expect_failure "commented-out master ancestry guard"

reset_fixtures
remove_workflow_line '/actions/workflows/android-ci.yml/runs?'
expect_failure "release without authoritative prior CI workflow query"

reset_fixtures
remove_workflow_line '.head_sha == $commit'
expect_failure "release accepting CI from another commit"

reset_fixtures
remove_workflow_line '.head_branch == "master"'
expect_failure "release accepting CI from another branch"

reset_fixtures
remove_workflow_line '.event == "push"'
expect_failure "release accepting pre-merge pull request CI"

reset_fixtures
remove_workflow_line '.status == "completed"'
expect_failure "release accepting in-progress CI"

reset_fixtures
remove_workflow_line 'actions: read'
expect_failure "release without workflow-run read permission"

reset_fixtures
remove_workflow_line 'SKIP_AUTO_VERSION_BUMP: "true"'
expect_failure "release with automatic version mutation enabled"

reset_fixtures
remove_workflow_line 'bash ./scripts/test-fearless-utils-derived-tree.sh'
expect_failure "release without fearless-utils derived-tree adversarial contract"

reset_fixtures
remove_workflow_line './scripts/ensure-fearless-utils.sh'
expect_failure "release without fearless-utils derived-tree verification"

reset_fixtures
remove_ci_workflow_line 'bash ./scripts/test-fearless-utils-derived-tree.sh'
expect_failure "Android CI without fearless-utils derived-tree adversarial contract"

reset_fixtures
remove_ci_workflow_line './scripts/ensure-fearless-utils.sh'
expect_failure "Android CI without fearless-utils derived-tree verification"

reset_fixtures
remove_workflow_line '"$version_name" != "$tag_version_name"'
expect_failure "release with a tag/versionName mismatch"

reset_fixtures
remove_workflow_line '--release --strict-provenance'
expect_failure "release without strict provenance"

reset_fixtures
remove_workflow_line 'git diff --quiet -- versioning/version.properties'
expect_failure "release without immutable version verification"

reset_fixtures
remove_workflow_line 'Expected exactly one release AAB'
expect_failure "release accepting ambiguous artifacts"

reset_fixtures
remove_workflow_line 'python3 ./scripts/verify-android-aab-native-page-alignment.py "${bundles[0]}"'
expect_failure "release without exact AAB native-alignment verification"

reset_fixtures
sed -i.bak '/misaligned-address/d' "$tmp_dir/aab-alignment-test.sh"
expect_failure "AAB alignment tests without address-congruence coverage"

reset_fixtures
remove_workflow_line 'META-INF/[^/]+'
expect_failure "release accepting an unsigned AAB"

reset_fixtures
remove_workflow_line '"$artifact_cert_sha256" != "$expected_cert_sha256"'
expect_failure "release accepting the wrong signing certificate"

reset_fixtures
sed -i.bak 's/signingConfig signingConfigs.playRelease/signingConfig signingConfigs.debug/' "$tmp_dir/app-build.gradle"
expect_failure "release build type using the Android debug signer"

reset_fixtures
printf '%s\n' 'storePassword releaseSigningInputs.CI_KEYSTORE_PASS ?: signingConfigs.debug.storePassword' >> "$tmp_dir/app-build.gradle"
expect_failure "release signing with a debug-password fallback"

reset_fixtures
sed -i.bak 's/initWith release/initWith debug/' "$tmp_dir/app-build.gradle"
expect_failure "IAS variant not inheriting release semantics"

reset_fixtures
sed -i.bak '/StandardCopyOption.REPLACE_EXISTING/d' "$tmp_dir/app-build.gradle"
expect_failure "IAS Firebase config without exact release-source generation"

reset_fixtures
sed -i.bak '/Files.deleteIfExists(generatedIasPath)/d' "$tmp_dir/app-build.gradle"
expect_failure "IAS generated Firebase config not cleaned"

reset_fixtures
sed -i.bak '/internalAppSharing\/google-services.json/d' "$tmp_dir/app.gitignore"
expect_failure "IAS generated Firebase config not ignored"

reset_fixtures
sed -i.bak '/gradle.taskGraph.whenReady/d' "$tmp_dir/app-build.gradle"
expect_failure "release signing not bound to the selected task graph"

reset_fixtures
sed -i.bak 's/if (!releaseArtifactTasks.isEmpty()) {/if (false \&\& !releaseArtifactTasks.isEmpty()) {/' "$tmp_dir/app-build.gradle"
expect_failure "disabled release-artifact signing task-graph guard"

reset_fixtures
sed -i.bak 's/if (!forbiddenIasPublisherTasks.isEmpty()) {/if (false \&\& !forbiddenIasPublisherTasks.isEmpty()) {/' "$tmp_dir/app-build.gradle"
expect_failure "disabled IAS Play-publisher rejection"

reset_fixtures
sed -i.bak 's/if (!normalPlayPublishingTasks.isEmpty()) {/if (false \&\& !normalPlayPublishingTasks.isEmpty()) {/' "$tmp_dir/app-build.gradle"
expect_failure "disabled normal Play publication guard"

reset_fixtures
sed -i.bak '/An Android Debug certificate cannot sign a Play release artifact/d' "$tmp_dir/app-build.gradle"
expect_failure "Gradle release guard accepting an Android Debug certificate"

reset_fixtures
awk '{ print; if ($0 == "          - beta") print "          - production" }' \
  "$tmp_dir/android-release.yml" > "$tmp_dir/android-release.next"
mv "$tmp_dir/android-release.next" "$tmp_dir/android-release.yml"
expect_failure "testing workflow exposing the production track"

reset_fixtures
sed -i.bak 's/internal|alpha|beta)/internal|alpha|beta|production)/' "$tmp_dir/android-release.yml"
expect_failure "testing workflow runtime allowing the production track"

reset_fixtures
remove_workflow_line './scripts/verify-android-distribution-preflight.sh --play-testing'
expect_failure "testing workflow without credential and track preflight"

reset_fixtures
remove_workflow_line 'FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY: ${{ secrets.FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY }}'
expect_failure "release workflow without the compiled Alchemy integration credential"

reset_fixtures
sed -i.bak '/FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY/d' "$tmp_dir/distribution-preflight.sh"
expect_failure "release preflight without the Alchemy integration credential gate"

reset_fixtures
remove_workflow_line 'PLAY_ARTIFACT_DIR: ${{ github.workspace }}/build/release-artifact'
expect_failure "Play publisher not bound to the staged attested artifact"

reset_fixtures
remove_workflow_line "if: \${{ inputs.play_testing_track != 'none' }}"
expect_failure "Play service-account restore without testing-track condition"

reset_fixtures
awk '
  $0 == "          PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}" { next }
  { print }
  $0 == "    env:" && !inserted {
    print "      PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}"
    inserted = 1
  }
' "$tmp_dir/android-release.yml" > "$tmp_dir/android-release.next"
mv "$tmp_dir/android-release.next" "$tmp_dir/android-release.yml"
expect_failure "Play service-account secret moved to job-wide environment"

reset_fixtures
sed -i.bak 's/-storepass:env CI_KEYSTORE_PASS/-storepass "$CI_KEYSTORE_PASS"/' "$tmp_dir/android-release.yml"
expect_failure "release workflow exposing the keystore password in process arguments"

reset_fixtures
sed -i.bak '/umask 077/d' "$tmp_dir/restore-release-overlays.sh"
expect_failure "release overlay files created with ambient permissions"

reset_fixtures
sed -i.bak '/validate_base64_payload ANDROID_RELEASE_KEYSTORE_B64 16777216/d' "$tmp_dir/restore-release-overlays.sh"
expect_failure "release overlays committing the first payload before validating the second"

reset_fixtures
sed -i.bak '/if ! ln "$temporary_sentinel" "$sentinel"; then/d' "$tmp_dir/restore-release-overlays.sh"
expect_failure "release overlay sentinel created with a clobbering write"

reset_fixtures
sed -i.bak '/invalid second payload committed the first release overlay/d' "$tmp_dir/restore-release-overlays-test.sh"
expect_failure "release overlay tests missing second-payload transaction coverage"

reset_fixtures
remove_workflow_line 'if: ${{ always() }}'
expect_failure "release credential cleanup not guaranteed after failure"

reset_fixtures
remove_workflow_line './scripts/cleanup-release-overlays.sh'
expect_failure "release workflow without credential cleanup"

reset_fixtures
sed -i.bak '/Cleanup is restricted to an explicit CI environment/d' "$tmp_dir/cleanup-release-overlays.sh"
expect_failure "release cleanup callable outside CI"

reset_fixtures
sed -i.bak '/cleanup removed Firebase config without an active overlay sentinel/d' "$tmp_dir/cleanup-release-overlays-test.sh"
expect_failure "release cleanup tests missing public-placeholder preservation coverage"

reset_fixtures
sed -i.bak '/must be a regular, non-symlink file when present/d' "$tmp_dir/cleanup-release-overlays.sh"
expect_failure "release cleanup silently accepting a malformed sentinel"

reset_fixtures
sed -i.bak '/malformed Play sentinel cleanup removed the service-account credential/d' "$tmp_dir/cleanup-release-overlays-test.sh"
expect_failure "release cleanup tests missing malformed Play sentinel coverage"

reset_fixtures
sed -i.bak 's/internal|alpha|beta)/internal|alpha|beta|production)/' "$tmp_dir/distribution-preflight.sh"
expect_failure "testing preflight allowing production"

reset_fixtures
sed -i.bak '/An Android Debug certificate cannot sign a Play release/d' "$tmp_dir/distribution-preflight.sh"
expect_failure "distribution preflight accepting a debug certificate"

reset_fixtures
sed -i.bak '/attacker token endpoint/d' "$tmp_dir/distribution-preflight-test.sh"
expect_failure "distribution preflight tests missing hostile token-endpoint coverage"

reset_fixtures
sed -i.bak 's#actions/attest-build-provenance@[0-9a-f]*#actions/attest-build-provenance@v2#' "$tmp_dir/android-release.yml"
expect_failure "unpinned attestation action"

reset_fixtures
perl -0pi -e 's#actions/checkout@[0-9a-f]{40}#actions/checkout\@v4#' "$tmp_dir/android-ci.yml"
expect_failure "unpinned Android CI checkout action"

reset_fixtures
sed -i.bak 's#actions/checkout@[0-9a-f]*#actions/checkout@v4#' "$tmp_dir/branch-flow.yml"
expect_failure "unpinned branch-flow checkout action"

reset_fixtures
sed -i.bak 's/boolean autoBumpAllowed = !skipAutoBump && !isCiEnvironment/boolean autoBumpAllowed = !skipAutoBump/' "$tmp_dir/build.gradle"
expect_failure "CI auto-bump regression"

reset_fixtures
sed -i.bak "s/'GITHUB_ACTIONS', 'JENKINS_URL', 'BUILD_ID', //" "$tmp_dir/build.gradle"
expect_failure "CI provider marker regression"

reset_fixtures
sed -i.bak "s/ && !value.equalsIgnoreCase('false') && value != '0'//" "$tmp_dir/build.gradle"
expect_failure "false-marker parsing regression"

reset_fixtures
sed -i.bak "s/\['bcpkix-jdk15on', 'bcpg-jdk15on'\]/['bcpkix-jdk15on']/" "$tmp_dir/build.gradle"
expect_failure "trust-all PKIX dependency exclusion regression"

reset_fixtures
sed -i.bak "/fatal 'CredentialDependency', 'TrustAllX509TrustManager'/d" "$tmp_dir/app-build.gradle"
expect_failure "security lint fatality regression"

reset_fixtures
sed -i.bak 's/api libs.credentials.play.services.auth/implementation libs.credentials.play.services.auth/' "$tmp_dir/backup-build.gradle"
expect_failure "Credential Manager transport visibility regression"

reset_fixtures
printf '%s\n' 'buildConfigField "String", "MOONPAY_PRIVATE_KEY", readSecretInQuotes("MOONPAY_PRODUCTION_SECRET")' >> "$tmp_dir/wallet-build.gradle"
expect_failure "MoonPay server secret embedded in Android"

reset_fixtures
sed -i.bak '/encodes public key and currency query injection as data/d' "$tmp_dir/MoonPayProviderTest.kt"
expect_failure "MoonPay query-injection adversarial test removed"

echo "[release-workflow-audit-test] all adversarial fixtures passed"
