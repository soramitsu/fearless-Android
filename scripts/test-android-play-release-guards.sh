#!/usr/bin/env bash
# shellcheck disable=SC2016 # Static-contract literals must not expand here.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
SELF="$ROOT_DIR/scripts/test-android-play-release-guards.sh"
VERIFY="$ROOT_DIR/scripts/verify-android-play-release.sh"
RESTORE="$ROOT_DIR/scripts/restore-release-overlays.sh"
CLEANUP="$ROOT_DIR/scripts/cleanup-release-overlays.sh"
SNAPSHOT="$ROOT_DIR/scripts/emit-verified-release-snapshot.sh"
EXPECTED_UPLOAD_CERT_SHA256="40391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
EXPECTED_POSITIVE_COUNT=4
EXPECTED_NEGATIVE_COUNT=123

fail() {
  echo "[android-play-release-test][error] $*" >&2
  exit 1
}

static_fail() {
  echo "[android-play-release-static][error] $*" >&2
  exit 1
}

require_regular_file() {
  local path="$1"
  local label="$2"
  [[ -f "$path" && ! -L "$path" ]] ||
    static_fail "$label is missing or unsafe: $path"
}

require_absent_path() {
  local path="$1"
  local diagnostic="$2"
  [[ ! -e "$path" && ! -L "$path" ]] || static_fail "$diagnostic"
}

require_text() {
  local path="$1"
  local text="$2"
  local diagnostic="$3"
  grep -Fq -- "$text" "$path" || static_fail "$diagnostic"
}

require_block_text() {
  local block="$1"
  local text="$2"
  local diagnostic="$3"
  grep -Fq -- "$text" <<<"$block" || static_fail "$diagnostic"
}

require_file_sequence() {
  local path="$1"
  local sequence="$2"
  local diagnostic="$3"
  local contents
  contents="$(<"$path")"
  [[ "$contents" == *"$sequence"* ]] || static_fail "$diagnostic"
}

require_block_sequence() {
  local block="$1"
  local sequence="$2"
  local diagnostic="$3"
  [[ "$block" == *"$sequence"* ]] || static_fail "$diagnostic"
}

require_block_exact_count() {
  local block="$1"
  local text="$2"
  local expected="$3"
  local diagnostic="$4"
  local actual
  actual="$(grep -Foc -- "$text" <<<"$block" || true)"
  [[ "$actual" == "$expected" ]] ||
    static_fail "$diagnostic (expected $expected, found $actual)"
}

require_exact_count() {
  local path="$1"
  local text="$2"
  local expected="$3"
  local diagnostic="$4"
  local actual
  actual="$(grep -Foc -- "$text" "$path" || true)"
  [[ "$actual" == "$expected" ]] ||
    static_fail "$diagnostic (expected $expected, found $actual)"
}

require_exact_line_count() {
  local path="$1"
  local text="$2"
  local expected="$3"
  local diagnostic="$4"
  local actual
  actual="$(grep -Fxc -- "$text" "$path" || true)"
  [[ "$actual" == "$expected" ]] ||
    static_fail "$diagnostic (expected $expected, found $actual)"
}

forbid_text() {
  local path="$1"
  local text="$2"
  local diagnostic="$3"
  if grep -Fq -- "$text" "$path"; then
    static_fail "$diagnostic"
  fi
}

forbid_block_text() {
  local block="$1"
  local text="$2"
  local diagnostic="$3"
  if grep -Fq -- "$text" <<<"$block"; then
    static_fail "$diagnostic"
  fi
}

line_of() {
  local path="$1"
  local text="$2"
  local diagnostic="$3"
  local line
  line="$(grep -Fn -m1 -- "$text" "$path" | cut -d: -f1 || true)"
  [[ -n "$line" ]] || static_fail "$diagnostic"
  printf '%s\n' "$line"
}

require_order() {
  local first="$1"
  local second="$2"
  local diagnostic="$3"
  (( first < second )) || static_fail "$diagnostic"
}

require_pinned_action() {
  local workflow="$1"
  local action="$2"
  local pinned="$3"
  local expected="$4"
  local label="$5"
  local total
  local exact

  total="$(grep -Ec "uses: ${action}@" "$workflow" || true)"
  exact="$(grep -Foc "uses: ${action}@${pinned}" "$workflow" || true)"
  [[ "$total" == "$expected" && "$exact" == "$expected" ]] ||
    static_fail "$label must be commit-pinned exactly $expected time(s)"
}

verify_static_contract() {
  local root="$1"
  local workflow="$root/.github/workflows/android-release.yml"
  local ci_workflow="$root/.github/workflows/android-ci.yml"
  local app_gradle="$root/app/build.gradle"
  local version_properties="$root/versioning/version.properties"
  local snapshot="$root/scripts/emit-verified-release-snapshot.sh"
  local tag_signature_verifier="$root/scripts/verify-android-release-tag-signature.sh"
  local tag_signature_test="$root/scripts/test-android-release-signed-tag-policy.sh"
  local tag_signer_allowlist="$root/.github/release/android-tag-signer-fingerprints.txt"
  local build_log_verifier="$root/scripts/verify-android-release-build-log.sh"
  local build_log_test="$root/scripts/test-android-release-build-log.sh"
  local restore="$root/scripts/restore-release-overlays.sh"
  local cleanup="$root/scripts/cleanup-release-overlays.sh"
  local signer="$root/scripts/sign-android-release-aab.sh"
  local play_verifier="$root/scripts/verify-android-play-release.sh"
  local identity_verifier="$root/scripts/verify-android-aab-identity.sh"
  local signature_verifier="$root/scripts/verify-android-aab-jar-signature.sh"
  local governance="$root/scripts/verify-android-release-governance.sh"
  local governance_test="$root/scripts/test-android-release-governance.sh"
  local overlay_test="$root/scripts/test-release-overlay-interruption.sh"
  local signer_test="$root/scripts/test-android-release-aab-signer.sh"
  local play_snapshot_test="$root/scripts/test-android-play-release-verifier-snapshot.sh"
  local unsigned_test="$root/scripts/test-android-unsigned-release-build.sh"
  local source_test="$root/scripts/test-android-release-source-binding.sh"
  local source_tree_verifier="$root/scripts/verify-android-release-source-tree.sh"
  local source_tree_test="$root/scripts/test-android-release-source-tree.sh"
  local sdk_lock="$root/scripts/android-release-sdk-linux.lock.json"
  local sdk_tool="$root/scripts/android-release-sdk.py"
  local sdk_wrapper="$root/scripts/install-android-release-sdk.sh"
  local sdk_test="$root/scripts/test-android-release-sdk.sh"
  local dependency_test="$root/scripts/test-gradle-dependency-provenance.sh"
  local buildscript_lock="$root/buildscript-gradle.lockfile"
  local utils_test="$root/scripts/test-fearless-utils-source-integrity.sh"
  local identity_test="$root/scripts/test-android-aab-identity.sh"
  local version_code
  local job_ids
  local input_ids
  local controls_line
  local build_job_line
  local signing_job_line
  local controls_job
  local build_job
  local signing_job
  local signing_secrets
  local expected_signing_secrets
  local controls_suite_line
  local firebase_restore_line
  local unsigned_build_line
  local firebase_cleanup_line
  local unsigned_stage_line
  local unsigned_attest_line
  local unsigned_verify_line
  local unsigned_upload_line
  local unsigned_download_line
  local downloaded_verify_line
  local remote_revalidate_line
  local signing_restore_line
  local signing_line
  local signing_cleanup_line
  local signed_identity_line
  local signed_stage_line
  local signed_attest_line
  local signed_verify_line
  local signed_preupload_line
  local signed_upload_line
  local unsigned_stage_block
  local downloaded_verify_block
  local signing_block
  local signed_identity_block
  local signed_stage_block
  local signed_verify_block
  local signed_preupload_block

  for required in \
    "$workflow" \
    "$ci_workflow" \
    "$app_gradle" \
    "$version_properties" \
    "$snapshot" \
    "$tag_signature_verifier" \
    "$tag_signature_test" \
    "$tag_signer_allowlist" \
    "$build_log_verifier" \
    "$build_log_test" \
    "$restore" \
    "$cleanup" \
    "$signer" \
    "$play_verifier" \
    "$identity_verifier" \
    "$signature_verifier" \
    "$governance" \
    "$governance_test" \
    "$overlay_test" \
    "$signer_test" \
    "$play_snapshot_test" \
    "$unsigned_test" \
    "$source_test" \
    "$source_tree_verifier" \
    "$source_tree_test" \
    "$sdk_lock" \
    "$sdk_tool" \
    "$sdk_wrapper" \
    "$sdk_test" \
    "$dependency_test" \
    "$buildscript_lock" \
    "$utils_test" \
    "$identity_test"; do
    require_regular_file "$required" "release contract input"
  done

  require_absent_path \
    "$root/scripts/publish-android-play-testing.sh" \
    "the removed Play API publisher must not be restored"
  require_absent_path \
    "$root/scripts/test-android-play-publisher.sh" \
    "the removed Play API publisher test must not be restored"

  version_code="$(
    awk -F= '$1 == "versionCode" { print $2 }' "$version_properties"
  )"
  if [[ ! "$version_code" =~ ^[1-9][0-9]*$ ]] ||
    (( version_code < 230 )); then
    static_fail "source-controlled versionCode must be an unused value at least 230"
  fi
  forbid_text \
    "$root/build.gradle" \
    "withOutputStream" \
    "release builds must not mutate versioning/version.properties"
  forbid_text \
    "$root/scripts/versions.gradle" \
    "CI_BUILD_ID" \
    "release versioning must not derive versionCode from CI"

  job_ids="$(
    awk '
      /^jobs:$/ { in_jobs=1; next }
      in_jobs && /^  [A-Za-z0-9_-]+:$/ {
        value=$0
        sub(/^  /, "", value)
        sub(/:$/, "", value)
        print value
      }
    ' "$workflow"
  )"
  [[ "$job_ids" == $'release-controls\nrelease-build\nrelease-signing' ]] ||
    static_fail "workflow must contain exactly release-controls, release-build, and release-signing jobs"

  input_ids="$(
    awk '
      /^    inputs:$/ { in_inputs=1; next }
      in_inputs && /^permissions:$/ { in_inputs=0 }
      in_inputs && /^      [A-Za-z0-9_-]+:$/ {
        value=$0
        sub(/^      /, "", value)
        sub(/:$/, "", value)
        print value
      }
    ' "$workflow"
  )"
  [[ "$input_ids" == "release_tag" ]] ||
    static_fail "release_tag must be the workflow's only dispatch input"

  for forbidden in \
    "play_testing_track" \
    "play_release_status" \
    "publish_to_play" \
    "PLAY_TRACK" \
    "PLAY_RELEASE_STATUS" \
    "PLAY_SERVICE_ACCOUNT" \
    "CI_PLAY_KEY" \
    "RELEASE_OVERLAY_MODE=play" \
    ":app:publishReleaseBundle" \
    "publish-android-play-testing.sh" \
    "test-android-play-publisher.sh" \
    "androidpublisher.googleapis.com" \
    "androidpublisher.v3" \
    "edits.bundles.upload"; do
    forbid_text \
      "$workflow" \
      "$forbidden" \
      "workflow contains a forbidden Google Play mutation path: $forbidden"
  done

  require_text \
    "$workflow" \
    "group: fearless-android-release" \
    "release workflow must serialize Android release artifacts"
  require_exact_count \
    "$workflow" \
    "name: android-release-build" \
    1 \
    "workflow must use the protected build environment exactly once"
  require_exact_count \
    "$workflow" \
    "name: android-release-signing" \
    1 \
    "workflow must use the protected signing environment exactly once"
  require_exact_count \
    "$workflow" \
    "persist-credentials: false" \
    5 \
    "every release checkout must disable persisted GitHub credentials"
  require_exact_count \
    "$workflow" \
    "runs-on: ubuntu-24.04" \
    3 \
    "all three release jobs must use fresh pinned Ubuntu runner images"

  controls_line="$(
    line_of "$workflow" \
      "  release-controls:" \
      "workflow lacks the credential-free release-controls job"
  )"
  build_job_line="$(
    line_of "$workflow" \
      "  release-build:" \
      "workflow lacks the protected release-build job"
  )"
  signing_job_line="$(
    line_of "$workflow" \
      "  release-signing:" \
      "workflow lacks the protected release-signing job"
  )"
  require_order "$controls_line" "$build_job_line" \
    "release controls must run before the protected build"
  require_order "$build_job_line" "$signing_job_line" \
    "the isolated signing runner must follow the build runner"

  controls_job="$(sed -n "${controls_line},$((build_job_line - 1))p" "$workflow")"
  build_job="$(sed -n "${build_job_line},$((signing_job_line - 1))p" "$workflow")"
  signing_job="$(sed -n "${signing_job_line},\$p" "$workflow")"

  require_block_text \
    "$build_job" \
    "needs: release-controls" \
    "release-build must depend on credential-free release-controls"
  require_block_text \
    "$signing_job" \
    "      - release-controls" \
    "release-signing must retain the approved control-job source"
  require_block_text \
    "$signing_job" \
    "      - release-build" \
    "release-signing must depend on the protected unsigned build"
  forbid_block_text \
    "$controls_job" \
    "environment:" \
    "release-controls must not use a secret-bearing environment"
  forbid_block_text \
    "$controls_job" \
    '${{ secrets.' \
    "release-controls must remain credential-free"
  forbid_block_text \
    "$controls_job" \
    "RELEASE_OVERLAY_MODE=" \
    "release-controls must never restore a private overlay"

  for forbidden in \
    ANDROID_RELEASE_KEYSTORE_B64 \
    CI_KEYSTORE_PATH \
    CI_KEYSTORE_PASS \
    CI_KEYSTORE_KEY_ALIAS \
    CI_KEYSTORE_KEY_PASS \
    PLAY_SERVICE_ACCOUNT_JSON_B64 \
    CI_PLAY_KEY; do
    forbid_block_text \
      "$build_job" \
      "$forbidden" \
      "release-build contains forbidden signing/Play material: $forbidden"
  done
  for forbidden in \
    GOOGLE_SERVICES_RELEASE_JSON_B64 \
    RAMP_TOKEN_RELEASE \
    COINBASE_APP_ID \
    X1_ENDPOINT_URL_RELEASE \
    X1_WIDGET_ID_RELEASE \
    WEB_CLIENT_ID_RELEASE \
    FL_BLAST_API_ETHEREUM_KEY \
    FL_WALLET_CONNECT_PROJECT_ID \
    FL_ANDROID_TON_API_KEY \
    PLAY_SERVICE_ACCOUNT_JSON_B64 \
    CI_PLAY_KEY \
    "RELEASE_OVERLAY_MODE=firebase"; do
    forbid_block_text \
      "$signing_job" \
      "$forbidden" \
      "release-signing contains forbidden build/Play material: $forbidden"
  done
  if grep -Eq '(^|[[:space:]])\./gradlew([[:space:]]|$)' <<<"$signing_job"; then
    static_fail "release-signing must never execute Gradle"
  fi

  signing_secrets="$(
    grep -Eo '\$\{\{ secrets\.[A-Z0-9_]+ \}\}' <<<"$signing_job" |
      sort -u
  )"
  expected_signing_secrets="$(
    printf '%s\n' \
      '${{ secrets.ANDROID_RELEASE_KEYSTORE_B64 }}' \
      '${{ secrets.ANDROID_RELEASE_KEYSTORE_PASSWORD }}' \
      '${{ secrets.ANDROID_RELEASE_KEY_PASSWORD }}' |
      sort
  )"
  [[ "$signing_secrets" == "$expected_signing_secrets" ]] ||
    static_fail "release-signing may receive only the three upload-keystore secrets"
  require_exact_count \
    "$workflow" \
    'CI_KEYSTORE_KEY_ALIAS: ${{ vars.ANDROID_RELEASE_KEY_ALIAS }}' \
    1 \
    "signing alias must come from the approved non-secret variable"

  require_exact_count \
    "$workflow" \
    '${{ secrets.GOOGLE_SERVICES_RELEASE_JSON_B64 }}' \
    1 \
    "Firebase secret must be consumed exactly once in release-build"
  require_exact_count \
    "$workflow" \
    '${{ secrets.ANDROID_RELEASE_KEYSTORE_B64 }}' \
    1 \
    "keystore secret must be consumed exactly once in release-signing"
  require_exact_count \
    "$workflow" \
    'EXPECTED_ANDROID_RELEASE_BUILD_REVIEWER_IDS: ${{ vars.ANDROID_RELEASE_BUILD_REVIEWER_IDS }}' \
    3 \
    "all three jobs must use the approved build-reviewer allowlist"
  require_exact_count \
    "$workflow" \
    'EXPECTED_ANDROID_RELEASE_SIGNING_REVIEWER_IDS: ${{ vars.ANDROID_RELEASE_SIGNING_REVIEWER_IDS }}' \
    3 \
    "all three jobs must use the approved signing-reviewer allowlist"
  require_exact_count \
    "$workflow" \
    "bash ./scripts/verify-android-release-governance.sh" \
    3 \
    "every release boundary must revalidate environment governance"

  require_text \
    "$governance" \
    '.prevent_self_review == true' \
    "both protected environments must prevent self-review"
  require_text \
    "$governance" \
    '.can_admins_bypass == false' \
    "both protected environments must deny admin bypass"
  require_text \
    "$governance" \
    '.type == "User"' \
    "environment reviewers must be User identities"
  require_text \
    "$governance" \
    "must be disjoint" \
    "build and signing reviewer allowlists must be disjoint"
  require_text \
    "$governance" \
    "android-release-build" \
    "governance must verify the build environment"
  require_text \
    "$governance" \
    "android-release-signing" \
    "governance must verify the signing environment"
  require_text \
    "$governance_test" \
    '[[ "$positive_count" == "3" ]]' \
    "governance test must fix its three valid configurations"
  require_text \
    "$governance_test" \
    '[[ "$negative_count" == "43" ]]' \
    "governance test must fix its 43 adversarial cases"
  require_text \
    "$governance" \
    '.reviewer.type == "User"' \
    "nested environment reviewer identities must also be Users"

  require_text \
    "$restore" \
    "firebase|signing) ;;" \
    "overlay restore must allow exactly firebase and signing modes"
  require_text \
    "$restore" \
    "RELEASE_OVERLAY_MODE must be exactly firebase or signing." \
    "overlay restore lacks an exact fail-closed mode diagnostic"
  require_text \
    "$restore" \
    "The Firebase-only overlay forbids signing and Play credentials." \
    "Firebase overlay must reject signing and Play material"
  require_text \
    "$restore" \
    "The signing-only overlay forbids Firebase and Play credentials." \
    "signing overlay must reject Firebase and Play material"
  require_text \
    "$restore" \
    "GOOGLE_SERVICES_RELEASE_JSON_B64 does not match the approved Firebase SHA-256 digest." \
    "Firebase overlay must bind the approved digest"
  require_text \
    "$cleanup" \
    'rm -f "$play_credential" "$play_sentinel"' \
    "cleanup must remove any legacy Play credential residue"
  forbid_text \
    "$workflow" \
    "RELEASE_OVERLAY_MODE=release" \
    "workflow still uses the obsolete combined release overlay"
  require_exact_count \
    "$workflow" \
    "RELEASE_OVERLAY_MODE=firebase" \
    1 \
    "workflow must restore the Firebase-only overlay exactly once"
  require_exact_count \
    "$workflow" \
    "RELEASE_OVERLAY_MODE=signing" \
    1 \
    "workflow must restore the signing-only overlay exactly once"

  require_text \
    "$app_gradle" \
    'def unsignedReleaseBuildValue = System.getenv("ANDROID_UNSIGNED_RELEASE_BUILD")' \
    "app build lacks the explicit unsigned-release switch"
  require_text \
    "$app_gradle" \
    "ANDROID_UNSIGNED_RELEASE_BUILD=true is restricted to an explicit CI environment." \
    "unsigned release bundling must be CI-only"
  require_text \
    "$app_gradle" \
    "Unsigned release bundling forbids every release keystore and password input." \
    "unsigned Gradle bundling must reject signing credentials"
  require_text \
    "$app_gradle" \
    'requestedTasks != [":app:bundleRelease"]' \
    "unsigned Gradle bundling must require the exact bundleRelease task"
  require_text \
    "$app_gradle" \
    "external certificate-pinned signing is still required." \
    "unsigned Gradle output must remain an intermediate"
  require_text \
    "$app_gradle" \
    "RELEASE_COMMIT is required whenever the resolved task graph can " \
    "release artifact tasks must remain source-bound"

  require_exact_count \
    "$signer" \
    "$EXPECTED_UPLOAD_CERT_SHA256" \
    1 \
    "standalone signer must pin exactly one registered upload certificate"
  require_text \
    "$signer" \
    '[[ "${CI:-}" == "true" ]]' \
    "standalone signer must be CI-only"
  require_text \
    "$signer" \
    "ANDROID_UNSIGNED_AAB_SHA256 must be an exact lowercase SHA-256 digest." \
    "standalone signer must bind the unsigned AAB digest"
  require_text \
    "$signer" \
    "Non-signature ZIP entry payloads changed while signing." \
    "standalone signer must prove byte-identical payload preservation"
  require_text \
    "$signer" \
    '"$SIGNATURE_VERIFIER"' \
    "standalone signer must invoke the certificate-pinned signature verifier"
  require_text \
    "$signer" \
    'ln "$temporary_aab" "$signed_aab"' \
    "standalone signer must publish only a verified private snapshot atomically"
  require_text \
    "$signer" \
    'signed_sha256="$(sha256_file "$temporary_aab")"' \
    "standalone signer must capture the signed digest before verification"
  require_text \
    "$signer" \
    "The signed AAB changed during signature verification." \
    "standalone signer must detect signature-verifier mutation"
  require_text \
    "$signer" \
    "The verified signed AAB changed before atomic publication." \
    "standalone signer must recheck bytes immediately before publication"
  require_text \
    "$signer" \
    '[android-release-signer] signed-aab-sha256=$signed_sha256' \
    "standalone signer must emit its trusted signed AAB digest"
  require_order \
    "$(line_of "$signer" \
      'signed_sha256="$(sha256_file "$temporary_aab")"' \
      "signer lacks pre-verification digest capture")" \
    "$(line_of "$signer" \
      '"$EXPECTED_UPLOAD_CERT_SHA256" >/dev/null' \
      "signer lacks signature-verifier invocation")" \
    "signer must capture the trusted digest before invoking the signature verifier"
  require_exact_count \
    "$play_verifier" \
    "$EXPECTED_UPLOAD_CERT_SHA256" \
    1 \
    "release verifier must pin exactly one registered upload certificate"
  require_file_sequence \
    "$play_verifier" \
    '"$AAB_SIGNATURE_VERIFIER"' \
    "release verifier must enforce the AAB JAR signature"
  require_file_sequence \
    "$play_verifier" \
    '"$AAB_IDENTITY_VERIFIER"' \
    "release verifier must derive identity from the signed AAB"
  require_text \
    "$play_verifier" \
    '--artifact <release.aab> <trusted-signer-sha256>' \
    "release verifier must require the trusted signer digest"
  require_text \
    "$play_verifier" \
    'artifact_snapshot="$(mktemp ' \
    "release verifier must create a private AAB snapshot"
  require_text \
    "$play_verifier" \
    'cp -- "$artifact" "$artifact_snapshot"' \
    "release verifier must snapshot the trusted signer output"
  require_file_sequence \
    "$play_verifier" \
    $'"$AAB_SIGNATURE_VERIFIER" \\\n    "$artifact_snapshot"' \
    "release verifier signature check must consume the private snapshot"
  require_file_sequence \
    "$play_verifier" \
    $'"$AAB_IDENTITY_VERIFIER" \\\n    "$artifact_snapshot"' \
    "release verifier identity check must consume the same private snapshot"
  require_file_sequence \
    "$play_verifier" \
    $'"$RELEASE_COMMIT" \\\n    required' \
    "release verifier must explicitly require R8 metadata"
  require_exact_count \
    "$workflow" \
    "AAB_IDENTITY_R8_POLICY=required" \
    1 \
    "release workflow must pin exactly one required R8 policy"
  require_exact_count \
    "$ci_workflow" \
    "AAB_IDENTITY_R8_POLICY: absent" \
    1 \
    "unminified debug CI must pin exactly one absent R8 policy"
  require_text \
    "$play_verifier" \
    "The private release AAB snapshot changed during verification." \
    "release verifier must recheck its private snapshot"
  require_text \
    "$play_verifier" \
    "The release AAB changed during verification." \
    "release verifier must recheck the signer output"
  require_text \
    "$play_verifier" \
    '[android-play-release] trusted-signer-sha256=$expected_sha256' \
    "release verifier must emit the digest it actually enforced"
  require_text \
    "$identity_verifier" \
    "forbidden_permissions = {" \
    "signed AAB identity must reject broad media/storage permissions"
  require_text \
    "$identity_verifier" \
    "if actual_set != expected_permissions:" \
    "signed AAB identity must require the exact permission allowlist"
  require_text \
    "$identity_verifier" \
    "exact merged permissions (" \
    "signed AAB identity must emit its merged-permission digest"
  require_text \
    "$identity_verifier" \
    'for attribute_name in ("debuggable", "testOnly"):' \
    "signed AAB identity must reject debug and test-only release flags"
  require_text \
    "$identity_verifier" \
    'uses_cleartext_traffic = application.get(' \
    "signed AAB identity must enforce release cleartext policy"
  require_text \
    "$identity_verifier" \
    'if network_security_config != "@xml/network_security_config":' \
    "signed AAB identity must require the exact network-policy reference"
  require_text \
    "$identity_verifier" \
    'if len(candidates) != 1:' \
    "signed AAB identity must reject missing, duplicate, or qualified network policies"
  require_text \
    "$identity_verifier" \
    'if resource.filename != "base/res/xml/network_security_config.xml":' \
    "signed AAB identity must require the default base-module network policy"
  require_text \
    "$identity_verifier" \
    'if set(children_by_name) != {"base-config", "debug-overrides"}:' \
    "signed AAB identity must enforce the exact network-policy structure"
  require_text \
    "$identity_verifier" \
    'if base_attributes != {"cleartextTrafficPermitted": ("false", False)}:' \
    "signed AAB identity must require a literal compiled cleartext denial"
  require_text \
    "$identity_verifier" \
    'if sorted(certificate_sources) != ["system", "user"]:' \
    "signed AAB identity must limit trust overrides to debug-only system and user anchors"
  require_text \
    "$identity_verifier" \
    'b"network-security-policy-v1\n"' \
    "signed AAB identity must emit canonical semantic network-policy evidence"
  require_text \
    "$identity_verifier" \
    'Compiled network-security evidence is malformed.' \
    "signed AAB identity must validate its network-policy evidence digest"
  require_text \
    "$identity_verifier" \
    'allow_backup != "false"' \
    "signed AAB identity must enforce disabled backups"
  require_text \
    "$identity_verifier" \
    'actual_min_sdk != expected_min_sdk' \
    "signed AAB identity must enforce the source-bound minimum SDK"
  require_text \
    "$identity_verifier" \
    'actual_target_sdk != expected_target_sdk' \
    "signed AAB identity must enforce the source-bound target SDK"
  require_text \
    "$identity_verifier" \
    'build_config_integer "$build_config_file" minSdkVersion' \
    "signed AAB identity must derive its minimum SDK from source"
  require_text \
    "$identity_verifier" \
    'build_config_integer "$build_config_file" targetSdkVersion' \
    "signed AAB identity must derive its target SDK from source"
  require_text \
    "$signature_verifier" \
    "The release AAB must contain exactly one JAR signer and one manifest." \
    "AAB signature verifier must reject missing or multiple signers"

  require_pinned_action \
    "$workflow" \
    "actions/checkout" \
    "34e114876b0b11c390a56381ad16ebd13914f8d5" \
    5 \
    "checkout action"
  require_pinned_action \
    "$workflow" \
    "actions/download-artifact" \
    "d3f86a106a0bac45b974a628896c90dbdf5c8093" \
    1 \
    "artifact download action"
  require_pinned_action \
    "$workflow" \
    "actions/upload-artifact" \
    "ea165f8d65b6e75b540449e92b4886f43607fa02" \
    2 \
    "artifact upload action"
  require_pinned_action \
    "$workflow" \
    "actions/attest-build-provenance" \
    "e8998f949152b193b063cb0ec769d69d929409be" \
    2 \
    "artifact attestation action"
  require_pinned_action \
    "$workflow" \
    "actions/setup-java" \
    "c1e323688fd81a25caa38c78aa6df2d33d3e20d9" \
    2 \
    "Java setup action"
  require_exact_count \
    "$workflow" \
    'TEMURIN_JDK_VERSION: "21.0.12+8"' \
    2 \
    "both protected jobs must pin the exact Temurin patch version"
  require_exact_count \
    "$workflow" \
    "TEMURIN_JDK_URL: https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz" \
    2 \
    "both protected jobs must use the exact Temurin archive URL"
  require_exact_count \
    "$workflow" \
    "TEMURIN_JDK_ARCHIVE_SHA256: e4446ff06a276155697597cc0f1b15da004ff083f4964a35271ecee567177370" \
    2 \
    "both protected jobs must pin the reviewed Temurin archive digest"
  require_exact_count \
    "$workflow" \
    "distribution: jdkfile" \
    2 \
    "both protected jobs must install only the checksum-pinned JDK archive"
  require_exact_count \
    "$workflow" \
    'java-version: "21.0.12+8"' \
    2 \
    "both protected jobs must select the exact reviewed JDK"
  require_exact_count \
    "$workflow" \
    "architecture: x64" \
    2 \
    "both protected jobs must bind the JDK architecture"
  require_exact_count \
    "$workflow" \
    'jdkFile: ${{ runner.temp }}/temurin-jdk-21.0.12+8.tar.gz' \
    2 \
    "both protected jobs must install the independently hashed archive"
  jq -e '
    .schemaVersion == 1 and
    .host == {os: "linux", arch: "x86_64"} and
    .license.id == "android-sdk-license" and
    .license.runtimeAcceptance == "not-performed-direct-archive" and
    (.packages | length) == 2 and
    .packages[0].packageId == "platforms;android-36" and
    .packages[0].revision == "2" and
    .packages[0].archiveUrl ==
      "https://dl.google.com/android/repository/platform-36_r02.zip" and
    .packages[0].archiveSize == 65878410 and
    .packages[0].archiveSha256 ==
      "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e" and
    .packages[0].archiveRoot == "android-36" and
    .packages[0].installPath == "platforms/android-36" and
    .packages[1].packageId == "build-tools;35.0.0" and
    .packages[1].revision == "35.0.0" and
    .packages[1].archiveUrl ==
      "https://dl.google.com/android/repository/build-tools_r35_linux.zip" and
    .packages[1].archiveSize == 61958799 and
    .packages[1].archiveSha256 ==
      "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88" and
    .packages[1].archiveRoot == "android-15" and
    .packages[1].installPath == "build-tools/35.0.0"
  ' "$sdk_lock" >/dev/null ||
    static_fail "Android release SDK lock differs from the reviewed official archives"
  for forbidden in \
    sdkmanager \
    commandlinetools \
    platform-tools \
    'ndk;28.0.12674087' \
    'build-tools;36.0.0'; do
    forbid_text \
      "$workflow" \
      "$forbidden" \
      "release workflow restored forbidden mutable SDK content: $forbidden"
  done
  require_block_text \
    "$build_job" \
    './scripts/install-android-release-sdk.sh install' \
    "release build must install the checksum-locked isolated SDK"
  require_block_text \
    "$build_job" \
    'android_sdk_root="$RUNNER_TEMP/fearless-android-release-sdk"' \
    "release build must use a fresh SDK root under RUNNER_TEMP"
  require_block_exact_count \
    "$build_job" \
    './scripts/install-android-release-sdk.sh verify' \
    4 \
    "release build must verify the isolated SDK before and after Gradle"
  require_block_exact_count \
    "$build_job" \
    '-Pandroid.builder.sdkDownload=false' \
    2 \
    "both release Gradle invocations must disable SDK auto-downloads"
  require_block_text \
    "$build_job" \
    '--no-build-cache' \
    "release bundle construction must force fresh R8 execution"
  require_block_text \
    "$build_job" \
    'ulimit -f 262144' \
    "release bundle construction must bound the captured log at 128 MiB"
  require_block_text \
    "$build_job" \
    'tee -- "$build_log"' \
    "release bundle construction must capture the exact combined Gradle stream"
  require_exact_count \
    "$workflow" \
    './scripts/verify-android-release-tag-signature.sh' \
    4 \
    "every release checkout and pre-sign boundary must verify the pinned tag key"
  require_exact_count \
    "$workflow" \
    'ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64: ${{ vars.ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64 }}' \
    4 \
    "tag-key verification must receive the public key at all four boundaries"
  require_text \
    "$tag_signature_verifier" \
    'gpg.format=ssh' \
    "release tag verifier must cryptographically verify an SSH-signed Git tag"
  require_text \
    "$tag_signature_verifier" \
    'Signer public key does not match the source-controlled fingerprint.' \
    "release tag verifier must bind mutable public-key material to source"
  require_text \
    "$tag_signature_verifier" \
    'gpg.ssh.allowedSignersFile' \
    "release tag verifier must use an isolated exact allowed-signers file"
  require_text \
    "$tag_signature_verifier" \
    'allowlist="$repository_root/.github/release/android-tag-signer-fingerprints.txt"' \
    "release tag verifier must read only the checked-in signer allowlist"
  forbid_text \
    "$tag_signature_verifier" \
    'ANDROID_RELEASE_TAG_SIGNER_ALLOWLIST_PATH' \
    "release tag verifier must not accept a mutable allowlist path"
  require_text \
    "$tag_signature_verifier" \
    '[[ "$actual_fingerprint" == "$allowed_fingerprint" ]]' \
    "release tag verifier must compare the supplied key to the checked-in fingerprint"
  require_text \
    "$tag_signature_test" \
    '[[ "$negative_count" == "13" ]]' \
    "signed-tag policy test must retain all 13 adversarial cases"
  require_text \
    "$sdk_test" \
    'EXPECTED_NEGATIVE_COUNT=53' \
    "isolated SDK test must retain all 53 adversarial cases"

  controls_suite_line="$(
    line_of "$workflow" \
      "- name: Run adversarial release control suite" \
      "workflow lacks the credential-free adversarial control suite"
  )"
  firebase_restore_line="$(
    line_of "$workflow" \
      "- name: Restore digest-pinned release Firebase configuration" \
      "workflow lacks digest-pinned Firebase restoration"
  )"
  unsigned_build_line="$(
    line_of "$workflow" \
      "- name: Build exact unsigned release AAB" \
      "workflow lacks exact unsigned AAB construction"
  )"
  firebase_cleanup_line="$(
    line_of "$workflow" \
      "- name: Remove Firebase overlay after Gradle exits" \
      "workflow lacks immediate Firebase cleanup"
  )"
  unsigned_stage_line="$(
    line_of "$workflow" \
      "- name: Stage exact four-file unsigned artifact" \
      "workflow lacks exact unsigned evidence staging"
  )"
  unsigned_attest_line="$(
    line_of "$workflow" \
      "- name: Attest all four unsigned artifact files" \
      "workflow lacks four-file unsigned attestation"
  )"
  unsigned_verify_line="$(
    line_of "$workflow" \
      "- name: Verify all unsigned attestations against immutable source" \
      "workflow lacks unsigned attestation verification"
  )"
  unsigned_upload_line="$(
    line_of "$workflow" \
      "- name: Upload exact attested unsigned artifact" \
      "workflow lacks exact unsigned artifact upload"
  )"
  unsigned_download_line="$(
    line_of "$workflow" \
      "- name: Download exact unsigned build artifact" \
      "signing job lacks exact unsigned artifact download"
  )"
  downloaded_verify_line="$(
    line_of "$workflow" \
      "- name: Verify unsigned files, provenance, and attestations" \
      "signing job lacks downloaded unsigned verification"
  )"
  remote_revalidate_line="$(
    line_of "$workflow" \
      "- name: Revalidate remote source before keystore restoration" \
      "signing job lacks last-moment remote source validation"
  )"
  signing_restore_line="$(
    line_of "$workflow" \
      "- name: Restore keystore only after unsigned verification" \
      "signing job lacks post-verification keystore restoration"
  )"
  signing_line="$(
    line_of "$workflow" \
      "- name: Sign exact unsigned AAB with upload certificate" \
      "signing job lacks certificate-pinned standalone signing"
  )"
  signing_cleanup_line="$(
    line_of "$workflow" \
      "- name: Remove keystore immediately after signing" \
      "signing job lacks immediate keystore cleanup"
  )"
  signed_identity_line="$(
    line_of "$workflow" \
      "- name: Verify signed AAB identity, signature, and permissions" \
      "signing job lacks signed-byte identity verification"
  )"
  signed_stage_line="$(
    line_of "$workflow" \
      "- name: Stage exact three-file signed artifact" \
      "signing job lacks exact final evidence staging"
  )"
  signed_attest_line="$(
    line_of "$workflow" \
      "- name: Attest all three signed artifact files" \
      "signing job lacks three-file final attestation"
  )"
  signed_verify_line="$(
    line_of "$workflow" \
      "- name: Verify all signed attestations against immutable source" \
      "signing job lacks final attestation verification"
  )"
  signed_preupload_line="$(
    line_of "$workflow" \
      "- name: Revalidate exact signed artifact immediately before upload" \
      "signing job lacks the immediate pre-upload digest gate"
  )"
  signed_upload_line="$(
    line_of "$workflow" \
      "- name: Upload exact attested signed artifact" \
      "signing job lacks exact final artifact upload"
  )"

  require_order "$controls_suite_line" "$build_job_line" \
    "all adversarial release controls must pass before protected build approval"
  require_order "$firebase_restore_line" "$unsigned_build_line" \
    "Firebase restore must immediately precede unsigned construction"
  require_order "$unsigned_build_line" "$firebase_cleanup_line" \
    "Firebase must be removed after Gradle exits"
  require_order "$firebase_cleanup_line" "$unsigned_stage_line" \
    "unsigned evidence must be staged only after Firebase cleanup"
  require_order "$unsigned_stage_line" "$unsigned_attest_line" \
    "unsigned evidence must be staged before attestation"
  require_order "$unsigned_attest_line" "$unsigned_verify_line" \
    "unsigned attestations must be created before verification"
  require_order "$unsigned_verify_line" "$unsigned_upload_line" \
    "all unsigned attestations must pass before artifact upload"
  require_order "$unsigned_upload_line" "$signing_job_line" \
    "signing must run on a fresh job after unsigned upload"
  require_order "$unsigned_download_line" "$downloaded_verify_line" \
    "unsigned download must precede exact evidence validation"
  require_order "$downloaded_verify_line" "$remote_revalidate_line" \
    "downloaded evidence must pass before last-moment source validation"
  require_order "$remote_revalidate_line" "$signing_restore_line" \
    "keystore must remain absent through last-moment remote validation"
  require_order "$signing_restore_line" "$signing_line" \
    "keystore restore must immediately precede standalone signing"
  require_order "$signing_line" "$signing_cleanup_line" \
    "keystore must be removed after standalone signing"
  require_order "$signing_cleanup_line" "$signed_identity_line" \
    "all signed-byte checks must run after keystore removal"
  require_order "$signed_identity_line" "$signed_stage_line" \
    "signed identity evidence must precede final staging"
  require_order "$signed_stage_line" "$signed_attest_line" \
    "final evidence must be staged before attestation"
  require_order "$signed_attest_line" "$signed_verify_line" \
    "final attestations must be created before verification"
  require_order "$signed_verify_line" "$signed_preupload_line" \
    "all final attestations must pass before the pre-upload digest gate"
  require_order "$signed_preupload_line" "$signed_upload_line" \
    "the final digest gate must immediately precede artifact upload"

  require_block_text \
    "$build_job" \
    "ANDROID_UNSIGNED_RELEASE_BUILD=true" \
    "build must enable the exact CI-only unsigned-release gate"
  require_block_text \
    "$build_job" \
    "./gradlew :app:bundleRelease" \
    "build must run the exact source-bound unsigned bundle task"
  require_block_exact_count \
    "$build_job" \
    './scripts/verify-android-release-build-log.sh' \
    4 \
    "build must verify the bounded log before use, staging, and upload"
  require_exact_count \
    "$workflow" \
    './scripts/verify-android-release-build-log.sh' \
    9 \
    "every build-log consumption boundary must revalidate exact bytes"
  require_text \
    "$build_log_verifier" \
    'flags |= os.O_NOFOLLOW' \
    "build-log verifier must refuse symlink traversal at snapshot open"
  require_text \
    "$build_log_verifier" \
    'before = os.fstat(descriptor)' \
    "build-log verifier must bind the opened regular-file identity"
  require_text \
    "$build_log_verifier" \
    'if len(r8_task_outcomes) != 1:' \
    "build-log verifier must require exactly one executed R8 task"
  require_text \
    "$build_log_verifier" \
    'if len(bundle_task_outcomes) != 1 or bundle_task_outcomes[0] is not None:' \
    "build-log verifier must require exactly one executed bundle task"
  require_block_text \
    "$signing_job" \
    "./scripts/sign-android-release-aab.sh" \
    "signing job must use the standalone certificate-pinned signer"
  grep -Fq 'if: ${{ always() }}' \
    <<<"$(sed -n "${signing_cleanup_line},$((signed_identity_line - 1))p" "$workflow")" ||
    static_fail "keystore cleanup must run even after signing failure"

  unsigned_stage_block="$(
    sed -n "${unsigned_stage_line},$((unsigned_attest_line - 1))p" "$workflow"
  )"
  downloaded_verify_block="$(
    sed -n "${downloaded_verify_line},$((remote_revalidate_line - 1))p" "$workflow"
  )"
  signing_block="$(
    sed -n "${signing_line},$((signing_cleanup_line - 1))p" "$workflow"
  )"
  signed_identity_block="$(
    sed -n "${signed_identity_line},$((signed_stage_line - 1))p" "$workflow"
  )"
  signed_stage_block="$(
    sed -n "${signed_stage_line},$((signed_attest_line - 1))p" "$workflow"
  )"
  signed_verify_block="$(
    sed -n "${signed_verify_line},$((signed_preupload_line - 1))p" "$workflow"
  )"
  signed_preupload_block="$(
    sed -n "${signed_preupload_line},$((signed_upload_line - 1))p" "$workflow"
  )"

  require_block_text \
    "$unsigned_stage_block" \
    '[[ "${#entries[@]}" -eq 4 ]]' \
    "unsigned artifact staging must contain exactly four entries"
  require_block_text \
    "$unsigned_stage_block" \
    'artifact_basename="Fearless-$RELEASE_TAG.unsigned.aab"' \
    "unsigned artifact must use its exact source-bound filename"
  require_block_text \
    "$unsigned_stage_block" \
    'provenance="$artifact_dir/unsigned-provenance.json"' \
    "unsigned artifact must include exact provenance"
  require_block_text \
    "$unsigned_stage_block" \
    'schemaVersion: 3,' \
    "unsigned provenance must use the build-log-bound schema"
  require_block_text \
    "$unsigned_stage_block" \
    'buildLog: {' \
    "unsigned provenance must bind the captured build log"
  require_block_text \
    "$unsigned_stage_block" \
    'sha256: $buildLogSha256' \
    "unsigned provenance must bind the captured build-log digest"
  for field in \
    schemaVersion \
    artifactKind \
    artifactFilename \
    artifactSha256 \
    buildLog \
    release \
    source \
    firebase \
    fearlessUtils \
    toolchain; do
    require_block_text \
      "$unsigned_stage_block" \
      "$field" \
      "unsigned provenance omits required field: $field"
  done
  for field in \
    javaArchiveSha256 \
    javaBinarySha256 \
    javacBinarySha256 \
    jarsignerBinarySha256 \
    keytoolBinarySha256; do
    require_block_text \
      "$unsigned_stage_block" \
      "$field" \
      "unsigned provenance omits reviewed Java evidence: $field"
  done
  require_block_text \
    "$unsigned_stage_block" \
    'javaDistribution: "temurin-jdkfile"' \
    "unsigned provenance must identify the exact archive-backed JDK"
  require_block_text \
    "$unsigned_stage_block" \
    'javaVersion: $javaVersion' \
    "unsigned provenance must bind the exact JDK version"
  require_block_text \
    "$unsigned_stage_block" \
    'javaBinarySha256: $javaBinarySha256' \
    "unsigned provenance must bind the reviewed Java executable"
  require_block_text \
    "$unsigned_stage_block" \
    'androidSdkEvidenceSha256: $androidSdkEvidenceSha256' \
    "unsigned provenance must bind exact Android SDK evidence bytes"
  require_block_text \
    "$unsigned_stage_block" \
    'androidSdkLockSha256: $androidSdkLockSha256' \
    "unsigned provenance must bind the checked-in Android SDK lock"

  require_block_text \
    "$downloaded_verify_block" \
    '[[ "${#entries[@]}" -eq 4 ]] || {' \
    "signing must reject extra downloaded entries"
  require_block_text \
    "$downloaded_verify_block" \
    'cmp - "$checksum"' \
    "signing must compare the downloaded checksum sidecar byte-for-byte"
  require_block_text \
    "$downloaded_verify_block" \
    'sha256sum "$unsigned_aab"' \
    "signing must independently hash the downloaded unsigned AAB"
  require_block_text \
    "$downloaded_verify_block" \
    'sha256sum "$provenance"' \
    "signing must independently hash downloaded provenance"
  require_block_text \
    "$downloaded_verify_block" \
    'sha256sum "$build_log"' \
    "signing must independently hash the downloaded build log"
  require_block_text \
    "$downloaded_verify_block" \
    './scripts/verify-android-release-build-log.sh "$build_log"' \
    "signing must semantically revalidate the downloaded build log"
  require_block_text \
    "$downloaded_verify_block" \
    '(keys | sort) == ([' \
    "signing must require the exact unsigned provenance key set"
  require_block_text \
    "$downloaded_verify_block" \
    '.schemaVersion == 3 and' \
    "signing must require the build-log-bound unsigned schema"
  require_block_text \
    "$downloaded_verify_block" \
    '.buildLog == {' \
    "signing must rebind unsigned provenance to the transferred build log"
  require_block_text \
    "$downloaded_verify_block" \
    'exact_android_sdk_evidence' \
    "signing must revalidate exact checksum-locked SDK package evidence"
  require_block_text \
    "$downloaded_verify_block" \
    'validate-evidence' \
    "signing must rehash and structurally revalidate SDK evidence bytes"
  require_block_text \
    "$downloaded_verify_block" \
    '"$build_log"; do' \
    "signing must reverify attestations for every unsigned evidence file"

  require_block_text \
    "$signing_block" \
    's/^\[android-release-signer\] signed-aab-sha256=//p' \
    "signing must parse only the signer's dedicated digest record"
  require_block_text \
    "$signing_block" \
    '[[ "${#signed_sha256_lines[@]}" -eq 1 ]]' \
    "signing must require exactly one signer digest record"
  require_block_text \
    "$signing_block" \
    'SIGNED_RELEASE_AAB_SHA256=$signed_sha256' \
    "signing must propagate the trusted signer digest"
  require_block_text \
    "$signing_block" \
    'sha256sum "$signed_aab"' \
    "signing must bind the published AAB to the emitted digest"

  require_block_sequence \
    "$signed_identity_block" \
    './scripts/verify-android-play-release.sh' \
    "signed-byte verification must use the snapshotting Play verifier"
  require_block_sequence \
    "$signed_identity_block" \
    $'--artifact \\\n              "$SIGNED_RELEASE_AAB" \\\n              "$SIGNED_RELEASE_AAB_SHA256" |' \
    "signed-byte verification must use the exact signer output"
  forbid_block_text \
    "$signed_identity_block" \
    "./scripts/verify-android-aab-identity.sh" \
    "signed-byte evidence must not reopen mutable signer output directly"
  require_block_text \
    "$signed_identity_block" \
    's/^\[android-play-release\] trusted-signer-sha256=//p' \
    "signed-byte verification must capture the enforced signer digest"
  require_block_text \
    "$signed_identity_block" \
    '[[ "${#verified_sha256_lines[@]}" -eq 1 &&' \
    "signed-byte verification must require exactly one enforced digest"
  require_block_text \
    "$signed_identity_block" \
    "AAB_PERMISSIONS_SHA256" \
    "signed-byte verification must capture the merged-permission digest"
  require_block_text \
    "$signed_identity_block" \
    "AAB_IDENTITY_EVIDENCE_SHA256" \
    "signed-byte verification must hash successful identity evidence"

  require_block_text \
    "$signed_stage_block" \
    '[[ "${#entries[@]}" -eq 3 ]]' \
    "signed artifact staging must contain exactly three entries"
  require_block_text \
    "$signed_stage_block" \
    'artifact_basename="Fearless-$RELEASE_TAG.aab"' \
    "signed artifact must use its exact source-bound filename"
  require_block_text \
    "$signed_stage_block" \
    'provenance="$artifact_dir/provenance.json"' \
    "signed artifact must include exact final provenance"
  require_block_text \
    "$signed_stage_block" \
    '--artifact "$artifact" "$SIGNED_RELEASE_AAB_SHA256"' \
    "staged AAB verification must enforce the trusted signer digest"
  require_block_text \
    "$signed_stage_block" \
    'artifact_sha256="$SIGNED_RELEASE_AAB_SHA256"' \
    "final artifact metadata must use the trusted signer digest"
  require_block_text \
    "$signed_stage_block" \
    'cmp -s - "$checksum"' \
    "final staged checksum must exactly match the trusted signer digest"
  require_block_text \
    "$signed_stage_block" \
    '.artifactSha256 == $sha256' \
    "final staged provenance must match the trusted signer digest"
  require_block_exact_count \
    "$signed_stage_block" \
    '(keys | sort) == ([' \
    2 \
    "signed artifact must require the exact final provenance key set"
  require_block_text \
    "$signed_stage_block" \
    $'./scripts/emit-verified-release-snapshot.sh \\\n            "$unsigned_provenance" \\\n            "$UNSIGNED_PROVENANCE_SHA256" |\n          jq -e \\' \
    "final provenance must consume one digest-verified in-memory snapshot"
  require_exact_count \
    "$workflow" \
    '"$UNSIGNED_PROVENANCE_SHA256" |' \
    1 \
    "final provenance must use the trusted unsigned-provenance digest exactly once"
  require_block_text \
    "$signed_stage_block" \
    'def exact_approved_unsigned_provenance:' \
    "final consumption must revalidate the exact unsigned schema"
  require_block_text \
    "$signed_stage_block" \
    'if exact_approved_unsigned_provenance then' \
    "final provenance construction must be gated by unsigned revalidation"
  require_block_text \
    "$signed_stage_block" \
    '.release == {' \
    "final consumption must revalidate immutable release identity"
  require_block_text \
    "$signed_stage_block" \
    '.source == {' \
    "final consumption must revalidate immutable source digests"
  require_block_text \
    "$signed_stage_block" \
    'exact_android_sdk_evidence' \
    "final consumption must revalidate exact checksum-locked SDK evidence"
  require_block_text \
    "$signed_stage_block" \
    'final-android-sdk-evidence.json' \
    "final provenance must rehash the exact embedded SDK evidence bytes"
  require_text \
    "$snapshot" \
    'snapshot="$(' \
    "release snapshot helper must capture bytes once before validation"
  require_text \
    "$snapshot" \
    '[[ "$actual_sha256" == "$expected_sha256" ]]' \
    "release snapshot helper must enforce the trusted digest"
  require_text \
    "$snapshot" \
    'printf '\''%s'\'' "$snapshot"' \
    "release snapshot helper must emit only the verified captured bytes"
  for field in \
    schemaVersion \
    artifactKind \
    artifactFilename \
    artifactSha256 \
    buildLog \
    unsignedArtifact \
    release \
    source \
    firebase \
    fearlessUtils \
    buildToolchain \
    artifactVerification \
    signing; do
    require_block_text \
      "$signed_stage_block" \
      "\"$field\"" \
      "final provenance omits required top-level key: $field"
  done
  require_block_text \
    "$signed_stage_block" \
    "mergedPermissionsSha256" \
    "final provenance must bind the signed merged-permission digest"
  require_block_text \
    "$signed_stage_block" \
    "artifactVerification: {" \
    "final provenance must construct signed artifact-verification evidence"
  require_block_text \
    "$signed_stage_block" \
    'identityEvidenceSha256: $aabIdentityEvidenceSha256' \
    "final provenance must bind the signed identity evidence digest"
  require_block_text \
    "$signed_stage_block" \
    ".signing.certificateSha256 ==" \
    "final provenance must pin the registered upload certificate"
  for field in \
    javaArchiveSha256 \
    javaBinarySha256 \
    javacBinarySha256 \
    jarsignerBinarySha256 \
    keytoolBinarySha256; do
    require_block_text \
      "$signed_stage_block" \
      "$field" \
      "final provenance omits reviewed signing-Java evidence: $field"
  done
  require_block_text \
    "$signed_stage_block" \
    'javaDistribution: "temurin-jdkfile"' \
    "final provenance must identify the exact archive-backed signing JDK"
  require_block_text \
    "$signed_stage_block" \
    'javaBinarySha256: $signingJavaBinarySha256' \
    "final provenance must bind the reviewed signing Java executable"
  require_block_text \
    "$signed_stage_block" \
    'buildLog: $unsigned.buildLog' \
    "final provenance must carry forward the verified unsigned build-log binding"

  require_block_text \
    "$signed_verify_block" \
    '[[ "$digest" == "$SIGNED_RELEASE_AAB_SHA256" ]]' \
    "signed attestation verification must enforce the signer digest"
  require_block_text \
    "$signed_verify_block" \
    'cmp -s - "$checksum"' \
    "signed attestation verification must rebind the checksum sidecar"
  require_block_text \
    "$signed_verify_block" \
    '.artifactSha256 == $sha256' \
    "signed attestation verification must rebind final provenance"
  require_block_text \
    "$signed_verify_block" \
    './scripts/verify-android-release-build-log.sh' \
    "signed attestation boundary must revalidate the original build log"
  require_block_text \
    "$signed_verify_block" \
    '.buildLog == {filename: $filename, sha256: $sha256}' \
    "signed attestation boundary must rebind final provenance to the build log"
  require_block_text \
    "$signed_preupload_block" \
    '[[ "${#entries[@]}" -eq 3 ]]' \
    "pre-upload gate must reject extra signed artifact entries"
  require_block_text \
    "$signed_preupload_block" \
    'sha256sum "$artifact"' \
    "pre-upload gate must independently hash the signed AAB"
  require_block_text \
    "$signed_preupload_block" \
    '"$SIGNED_RELEASE_AAB_SHA256"' \
    "pre-upload gate must enforce the signer digest"
  require_block_text \
    "$signed_preupload_block" \
    'cmp -s - "$checksum"' \
    "pre-upload gate must rebind the checksum sidecar"
  require_block_text \
    "$signed_preupload_block" \
    '.artifactSha256 == $sha256' \
    "pre-upload gate must rebind final provenance"
  require_block_text \
    "$signed_preupload_block" \
    './scripts/verify-android-release-build-log.sh' \
    "pre-upload gate must revalidate the original build log"
  require_block_text \
    "$signed_preupload_block" \
    '.buildLog == {filename: $filename, sha256: $sha256}' \
    "pre-upload gate must rebind final provenance to the build log"

  require_exact_count \
    "$workflow" \
    "gh attestation verify \"\$subject\"" \
    3 \
    "every evidence boundary must independently verify all three attestations"
  require_exact_count \
    "$workflow" \
    '--signer-digest "$RELEASE_COMMIT"' \
    3 \
    "every attestation verification must bind the workflow commit"
  require_exact_count \
    "$workflow" \
    '--source-digest "$RELEASE_COMMIT"' \
    3 \
    "every attestation verification must bind the source commit"
  require_exact_count \
    "$workflow" \
    "--deny-self-hosted-runners" \
    3 \
    "every attestation verification must reject self-hosted builders"
  require_exact_count \
    "$workflow" \
    "name: fearless-android-unsigned-\${{ inputs.release_tag }}" \
    2 \
    "workflow must transfer only the exact unsigned artifact"
  require_exact_count \
    "$workflow" \
    "name: fearless-android-signed-\${{ inputs.release_tag }}" \
    1 \
    "workflow must expose only the exact final signed artifact"

  for control in \
    test-android-release-signed-tag-policy.sh \
    test-android-release-governance.sh \
    test-android-play-release-guards.sh \
    test-android-play-release-verifier-snapshot.sh \
    test-android-release-aab-signer.sh \
    test-android-unsigned-release-build.sh \
    test-android-release-build-log.sh \
    test-android-release-source-binding.sh \
    test-android-release-source-tree.sh \
    test-android-release-sdk.sh \
    test-fearless-utils-source-integrity.sh \
    test-gradle-dependency-provenance.sh \
    test-release-overlay-interruption.sh \
    test-android-release-media-permissions.sh; do
    require_block_text \
      "$controls_job" \
      "$control" \
      "credential-free controls must exercise $control"
  done
  require_text \
    "$ci_workflow" \
    "test-android-play-release-guards.sh" \
    "Android CI must run this release architecture guard"
  require_text \
    "$ci_workflow" \
    "test-android-release-build-log.sh" \
    "Android CI must run the build-log adversarial suite"
  require_exact_count \
    "$workflow" \
    "./scripts/verify-android-release-source-tree.sh" \
    7 \
    "workflow must verify the exact source tree at all seven release boundaries"
  require_exact_count \
    "$workflow" \
    "--allow-fearless-utils" \
    3 \
    "only the three post-utils build/control boundaries may allow the nested checkout"
  require_block_exact_count \
    "$controls_job" \
    "--allow-fearless-utils" \
    1 \
    "controls may allow utils only after its pinned fixture checkout"
  require_block_exact_count \
    "$build_job" \
    "--allow-fearless-utils" \
    2 \
    "build may allow utils only at its two post-checkout source gates"
  require_block_exact_count \
    "$signing_job" \
    "--allow-fearless-utils" \
    0 \
    "signing must never allow an unverified nested utils checkout"
  require_block_exact_count \
    "$controls_job" \
    "./scripts/verify-android-release-source-tree.sh" \
    1 \
    "controls must end with one exact source-tree proof"
  require_block_exact_count \
    "$build_job" \
    "./scripts/verify-android-release-source-tree.sh" \
    3 \
    "build must prove source before utils, before Firebase, and after Gradle"
  require_block_exact_count \
    "$signing_job" \
    "./scripts/verify-android-release-source-tree.sh" \
    3 \
    "signing must prove source before download, before key restore, and before final staging"
  require_text \
    "$source_tree_verifier" \
    "Ignored file is not an approved generated output" \
    "source-tree verifier must reject ignored source-like or credential files"
  require_text \
    "$source_tree_verifier" \
    "Untracked release source is present" \
    "source-tree verifier must reject untracked release source"
  require_text \
    "$source_tree_verifier" \
    "Unexpected nested fearless-utils-Android checkout" \
    "source-tree verifier must reject utils before its explicit checkout boundary"
  require_text \
    "$source_tree_test" \
    '[[ "$positive_count" == "3" ]]' \
    "source-tree test must fix its three valid boundary cases"
  require_text \
    "$source_tree_test" \
    '[[ "$negative_count" == "13" ]]' \
    "source-tree test must fix its 13 adversarial cases"
  require_text \
    "$overlay_test" \
    "expected 20 interruption cases" \
    "overlay test must fix the 20 restore-interruption cases"
  require_text \
    "$overlay_test" \
    "expected 8 cleanup-interruption cases" \
    "overlay test must fix the 8 cleanup-interruption cases"
  require_text \
    "$overlay_test" \
    "expected 20 split-overlay negative cases" \
    "overlay test must fix the 20 split-overlay adversarial cases"
  require_text \
    "$overlay_test" \
    "expected 2 split-overlay positive cases" \
    "overlay test must fix both independent overlay positives"
  require_text \
    "$signer_test" \
    "expected 31 negative/adversarial cases" \
    "signer test must fix its 31 adversarial cases"
  require_text \
    "$signer_test" \
    "expected 14 interruption cases" \
    "signer test must fix its 14 TERM/KILL cases"
  require_text \
    "$play_snapshot_test" \
    "expected 8 negative/adversarial cases" \
    "Play verifier snapshot test must fix its eight adversarial cases"
  require_text \
    "$play_snapshot_test" \
    "expected 4 deterministic source-swap cases" \
    "Play verifier snapshot test must retain all source-swap checkpoints"
  require_text \
    "$play_snapshot_test" \
    "expected 1 deterministic private-snapshot tamper" \
    "Play verifier snapshot test must retain private-snapshot tampering"
  require_text \
    "$unsigned_test" \
    "expected 25 negative/adversarial cases" \
    "unsigned Gradle test must fix its 25 adversarial cases"
  require_text \
    "$source_test" \
    "expected 10 negative cases" \
    "source-binding test must fix its 10 adversarial cases"
  require_text \
    "$dependency_test" \
    'EXPECTED_POSITIVE_COUNT=2' \
    "dependency-provenance test must retain both valid configurations"
  require_text \
    "$dependency_test" \
    'EXPECTED_NEGATIVE_COUNT=40' \
    "dependency-provenance test must retain all 40 adversarial cases"
  require_text \
    "$dependency_test" \
    '[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]]' \
    "dependency-provenance test must enforce its adversarial count"
  require_text \
    "$dependency_test" \
    '"[gradle-provenance-test] $positive_count positive + $negative_count negative cases passed"' \
    "dependency-provenance fixture test is not wired"
  require_text \
    "$utils_test" \
    "expected 7 adversarial cases" \
    "fearless-utils source-integrity test must fix its adversarial count"
  require_text \
    "$identity_test" \
    "expected 135 negative cases" \
    "signed AAB identity test must fix its adversarial count"
  require_exact_line_count \
    "$identity_test" \
    "# R8_FIXTURE_SYNTHESIZER_SOURCE_BEGIN" \
    "1" \
    "R8 synthesis boundary test must extract the exact synthesizer start"
  require_exact_line_count \
    "$identity_test" \
    "# R8_FIXTURE_SYNTHESIZER_SOURCE_END" \
    "1" \
    "R8 synthesis boundary test must extract the exact synthesizer end"
  for synthesis_bound in \
    "maximum_aab_bytes = 262_144_000" \
    "maximum_entry_count = 100_000" \
    "maximum_entry_bytes = 134_217_728" \
    "maximum_total_uncompressed_bytes = 536_870_912"; do
    require_text \
      "$identity_test" \
      "$synthesis_bound" \
      "R8 synthesis boundary test must retain every reviewed exact bound"
  done
  for synthesis_operator in \
    "if output_size <= 0 or output_size > maximum_aab_bytes:" \
    "or before.st_size > maximum_aab_bytes" \
    "if not entries or len(entries) > maximum_entry_count:" \
    "or entry.file_size > maximum_entry_bytes" \
    "if total_uncompressed_bytes > maximum_total_uncompressed_bytes:"; do
    require_text \
      "$identity_test" \
      "$synthesis_operator" \
      "R8 synthesis boundary test must retain every reviewed strict bound"
  done
  require_text \
    "$identity_test" \
    'reviewed_bound_names = {' \
    "R8 synthesis fixtures must derive bounds from the extracted source"
  require_text \
    "$identity_test" \
    'maximum_aab_bytes = bound_values["maximum_aab_bytes"]' \
    "R8 synthesis fixtures must use the extracted AAB byte bound"
  require_text \
    "$identity_test" \
    'maximum_entry_count = bound_values["maximum_entry_count"]' \
    "R8 synthesis fixtures must use the extracted ZIP entry-count bound"
  require_text \
    "$identity_test" \
    'maximum_entry_bytes = bound_values["maximum_entry_bytes"]' \
    "R8 synthesis fixtures must use the extracted per-entry byte bound"
  require_text \
    "$identity_test" \
    'maximum_total_uncompressed_bytes = bound_values[' \
    "R8 synthesis fixtures must use the extracted aggregate byte bound"
  require_text \
    "$identity_test" \
    'aggregate_total_bytes = maximum_total_uncompressed_bytes + 1' \
    "R8 synthesis aggregate fixture must exercise the first rejected byte"
  require_text \
    "$identity_test" \
    'output_payload_total_bytes = maximum_aab_bytes + 1 - output_overhead_bytes' \
    "R8 synthesis output fixture must derive the first rejected output byte"
  require_text \
    "$identity_test" \
    'expected_size = maximum_aab_values[0] + 1' \
    "R8 synthesis output fixture must verify the first rejected output byte"
  require_exact_count \
    "$identity_test" \
    "synthesis destination already exists" \
    "3" \
    "R8 synthesis fixtures must use a stable exclusive-destination diagnostic"
  require_exact_count \
    "$identity_test" \
    "source fixture could not be opened without following links" \
    "2" \
    "R8 synthesis fixtures must use a stable no-follow diagnostic"
  require_text \
    "$identity_test" \
    "expected 1 R8-synthesis boundary positive case" \
    "R8 synthesis boundary test must retain its positive case"
  require_text \
    "$identity_test" \
    "expected 15 R8-synthesis boundary negative/adversarial cases" \
    "R8 synthesis boundary test must fix its adversarial count"
  require_text \
    "$identity_test" \
    "[android-aab-r8-synthesis-boundary-test]" \
    "R8 synthesis boundary test must emit its fixed completion receipt"
  require_text \
    "$build_log_test" \
    '[[ "$positive_count" == "2" ]]' \
    "build-log test must retain both valid execution cases"
  require_text \
    "$build_log_test" \
    '[[ "$negative_count" == "26" ]]' \
    "build-log test must retain all 26 adversarial cases"
}

if [[ "${1:-}" == "--verify-static" ]]; then
  [[ "$#" -eq 2 ]] || static_fail "Usage: $SELF --verify-static <fixture-root>"
  verify_static_contract "$2"
  echo "[android-play-release-static] release architecture contract verified"
  exit 0
fi
[[ "$#" -eq 0 ]] || fail "Usage: $SELF"

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-$ROOT_DIR/build/test-tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(
  mktemp -d "$test_tmp_root/android-play-release.XXXXXX"
)"
trap 'rm -rf "$tmp_dir"' EXIT HUP INT TERM
positive_count=0
negative_count=0

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

base64_one_line() {
  base64 <"$1" | tr -d '\n'
}

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output="$tmp_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$label did not fail with the expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

fixture_paths=(
  .github/release/android-tag-signer-fingerprints.txt
  .github/workflows/android-ci.yml
  .github/workflows/android-release.yml
  app/build.gradle
  build.gradle
  buildscript-gradle.lockfile
  scripts/android-release-sdk-linux.lock.json
  scripts/android-release-sdk.py
  scripts/cleanup-release-overlays.sh
  scripts/emit-verified-release-snapshot.sh
  scripts/restore-release-overlays.sh
  scripts/sign-android-release-aab.sh
  scripts/install-android-release-sdk.sh
  scripts/test-android-aab-identity.sh
  scripts/test-android-play-release-verifier-snapshot.sh
  scripts/test-android-release-aab-signer.sh
  scripts/test-android-release-build-log.sh
  scripts/test-android-release-signed-tag-policy.sh
  scripts/test-android-release-governance.sh
  scripts/test-android-release-source-binding.sh
  scripts/test-android-release-source-tree.sh
  scripts/test-android-release-sdk.sh
  scripts/test-android-unsigned-release-build.sh
  scripts/test-fearless-utils-source-integrity.sh
  scripts/test-gradle-dependency-provenance.sh
  scripts/test-release-overlay-interruption.sh
  scripts/verify-android-aab-identity.sh
  scripts/verify-android-aab-jar-signature.sh
  scripts/verify-android-play-release.sh
  scripts/verify-android-release-governance.sh
  scripts/verify-android-release-build-log.sh
  scripts/verify-android-release-source-tree.sh
  scripts/verify-android-release-tag-signature.sh
  scripts/versions.gradle
  versioning/version.properties
)

make_fixture() {
  local name="$1"
  local fixture="$tmp_dir/static-$name"
  local relative
  for relative in "${fixture_paths[@]}"; do
    mkdir -p "$fixture/$(dirname "$relative")"
    cp "$ROOT_DIR/$relative" "$fixture/$relative"
  done
  printf '%s\n' "$fixture"
}

replace_nth() {
  local path="$1"
  local old="$2"
  local new="$3"
  local occurrence="$4"
  python3 - "$path" "$old" "$new" "$occurrence" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
old, new = sys.argv[2:4]
occurrence = int(sys.argv[4])
text = path.read_text()
count = text.count(old)
if occurrence < 1 or count < occurrence:
    raise SystemExit(
        f"expected occurrence {occurrence} of {old!r} in {path}; found {count}"
    )
parts = text.split(old)
text = old.join(parts[:occurrence]) + new + old.join(parts[occurrence:])
path.write_text(text)
PY
}

replace_once() {
  python3 - "$1" "$2" "$3" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
old, new = sys.argv[2:]
text = path.read_text()
count = text.count(old)
if count != 1:
    raise SystemExit(
        f"expected exactly one occurrence in {path}: {old!r}; found {count}"
    )
path.write_text(text.replace(old, new, 1))
PY
}

swap_once() {
  local path="$1"
  local first="$2"
  local second="$3"
  local marker="__ANDROID_RELEASE_GUARD_SWAP_${negative_count}__"
  replace_once "$path" "$first" "$marker"
  replace_once "$path" "$second" "$first"
  replace_once "$path" "$marker" "$second"
}

expect_static_failure() {
  local label="$1"
  local expected="$2"
  local fixture="$3"
  expect_failure "$label" "$expected" "$SELF" --verify-static "$fixture"
}

"$SELF" --verify-static "$ROOT_DIR" >/dev/null
positive_count=$((positive_count + 1))

fixture="$(make_fixture version-code-downgrade)"
replace_once "$fixture/versioning/version.properties" "versionCode=230" "versionCode=229"
expect_static_failure \
  "downgraded Play version code" \
  "versionCode must be an unused value at least 230" \
  "$fixture"

fixture="$(make_fixture unexpected-workflow-job)"
printf '\n  publish-testing:\n    runs-on: ubuntu-24.04\n' \
  >>"$fixture/.github/workflows/android-release.yml"
expect_static_failure \
  "unexpected workflow job" \
  "exactly release-controls, release-build, and release-signing jobs" \
  "$fixture"

fixture="$(make_fixture extra-dispatch-input)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      release_tag:" \
  $'      play_testing_track:\n        type: string\n      release_tag:'
expect_static_failure \
  "extra dispatch input" \
  "release_tag must be the workflow's only dispatch input" \
  "$fixture"

fixture="$(make_fixture build-controls-dependency-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "    needs: release-controls" \
  "    needs: []"
expect_static_failure \
  "build detached from controls" \
  "release-build must depend on credential-free release-controls" \
  "$fixture"

fixture="$(make_fixture signing-build-dependency-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      - release-build" \
  "      - release-controls # build dependency removed"
expect_static_failure \
  "signing detached from build" \
  "release-signing must depend on the protected unsigned build" \
  "$fixture"

fixture="$(make_fixture build-environment-mutated)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      name: android-release-build" \
  "      name: android-release-signing"
expect_static_failure \
  "wrong build environment" \
  "protected build environment exactly once" \
  "$fixture"

fixture="$(make_fixture signing-environment-mutated)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      name: android-release-signing" \
  "      name: android-release-build"
expect_static_failure \
  "wrong signing environment" \
  "protected build environment exactly once" \
  "$fixture"

fixture="$(make_fixture controls-secret-injected)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '      CI: true' \
  $'      CI: true\n      FORBIDDEN: ${{ secrets.ANDROID_RELEASE_KEYSTORE_B64 }}' \
  1
expect_static_failure \
  "secret in credential-free controls" \
  "release-controls must remain credential-free" \
  "$fixture"

fixture="$(make_fixture play-service-account-injected)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '      CI: true' \
  $'      CI: true\n      PLAY_SERVICE_ACCOUNT_JSON_B64: ${{ secrets.PLAY_SERVICE_ACCOUNT_JSON_B64 }}' \
  2
expect_static_failure \
  "Play service account in workflow" \
  "forbidden Google Play mutation path: PLAY_SERVICE_ACCOUNT" \
  "$fixture"

fixture="$(make_fixture publisher-restored)"
printf '#!/usr/bin/env bash\nexit 0\n' \
  >"$fixture/scripts/publish-android-play-testing.sh"
expect_static_failure \
  "Play API publisher restored" \
  "removed Play API publisher must not be restored" \
  "$fixture"

fixture="$(make_fixture play-overlay-restored)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "RELEASE_OVERLAY_MODE=signing" \
  "RELEASE_OVERLAY_MODE=play"
expect_static_failure \
  "Play overlay restored" \
  "forbidden Google Play mutation path: RELEASE_OVERLAY_MODE=play" \
  "$fixture"

fixture="$(make_fixture gradle-play-publisher)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "./scripts/sign-android-release-aab.sh \\" \
  "./gradlew :app:publishReleaseBundle \\"
expect_static_failure \
  "Gradle Play publisher restored" \
  "forbidden Google Play mutation path: :app:publishReleaseBundle" \
  "$fixture"

fixture="$(make_fixture mutable-checkout)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "actions/checkout@34e114876b0b11c390a56381ad16ebd13914f8d5" \
  "actions/checkout@v4" \
  1
expect_static_failure \
  "mutable checkout action" \
  "checkout action must be commit-pinned" \
  "$fixture"

fixture="$(make_fixture mutable-download)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093" \
  "actions/download-artifact@v4"
expect_static_failure \
  "mutable artifact download" \
  "artifact download action must be commit-pinned" \
  "$fixture"

fixture="$(make_fixture temurin-url-mutated)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "TEMURIN_JDK_URL: https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12%2B8/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12_8.tar.gz" \
  "TEMURIN_JDK_URL: https://example.invalid/mutable-jdk.tar.gz" \
  1
expect_static_failure \
  "Temurin archive URL mutated" \
  "exact Temurin archive URL" \
  "$fixture"

fixture="$(make_fixture temurin-digest-mutated)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "TEMURIN_JDK_ARCHIVE_SHA256: e4446ff06a276155697597cc0f1b15da004ff083f4964a35271ecee567177370" \
  "TEMURIN_JDK_ARCHIVE_SHA256: a4446ff06a276155697597cc0f1b15da004ff083f4964a35271ecee567177370" \
  2
expect_static_failure \
  "Temurin archive digest mutated" \
  "reviewed Temurin archive digest" \
  "$fixture"

fixture="$(make_fixture android-platform-digest-mutated)"
replace_once "$fixture/scripts/android-release-sdk-linux.lock.json" \
  "37607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e" \
  "a7607369a28c5b640b3a7998868d45898ebcb777565a0e85f9acf36f29631d2e"
expect_static_failure \
  "Android platform archive digest mutated" \
  "SDK lock differs from the reviewed official archives" \
  "$fixture"

fixture="$(make_fixture android-platform-url-mutated)"
replace_once "$fixture/scripts/android-release-sdk-linux.lock.json" \
  "https://dl.google.com/android/repository/platform-36_r02.zip" \
  "https://example.invalid/platform-36_r02.zip"
expect_static_failure \
  "Android platform archive URL mutated" \
  "SDK lock differs from the reviewed official archives" \
  "$fixture"

fixture="$(make_fixture android-platform-size-mutated)"
replace_once "$fixture/scripts/android-release-sdk-linux.lock.json" \
  '"archiveSize": 65878410' \
  '"archiveSize": 65878411'
expect_static_failure \
  "Android platform archive size mutated" \
  "SDK lock differs from the reviewed official archives" \
  "$fixture"

fixture="$(make_fixture android-build-tools-digest-mutated)"
replace_once "$fixture/scripts/android-release-sdk-linux.lock.json" \
  "bd3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88" \
  "ad3a4966912eb8b30ed0d00b0cda6b6543b949d5ffe00bea54c04c81e1561d88"
expect_static_failure \
  "Android build-tools archive digest mutated" \
  "SDK lock differs from the reviewed official archives" \
  "$fixture"

fixture="$(make_fixture android-build-tools-size-mutated)"
replace_once "$fixture/scripts/android-release-sdk-linux.lock.json" \
  '"archiveSize": 61958799' \
  '"archiveSize": 61958800'
expect_static_failure \
  "Android build-tools archive size mutated" \
  "SDK lock differs from the reviewed official archives" \
  "$fixture"

fixture="$(make_fixture android-sdkmanager-restored)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "./scripts/install-android-release-sdk.sh install" \
  "sdkmanager --install platforms\;android-36"
expect_static_failure \
  "SDK manager restored in release build" \
  "forbidden mutable SDK content: sdkmanager" \
  "$fixture"

fixture="$(make_fixture android-sdk-count-weakened)"
replace_once "$fixture/scripts/test-android-release-sdk.sh" \
  "EXPECTED_NEGATIVE_COUNT=53" \
  "EXPECTED_NEGATIVE_COUNT=52"
expect_static_failure \
  "isolated Android SDK adversarial count weakened" \
  "retain all 53 adversarial cases" \
  "$fixture"

fixture="$(make_fixture release-tag-verification-boundary-removed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  './scripts/verify-android-release-tag-signature.sh' \
  './scripts/disabled-android-release-tag-signature.sh' \
  1
expect_static_failure \
  "release tag verification boundary removed" \
  "every release checkout and pre-sign boundary must verify the pinned tag key" \
  "$fixture"

fixture="$(make_fixture release-tag-public-key-boundary-removed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64: ${{ vars.ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64 }}' \
  'ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64: attacker-controlled' \
  1
expect_static_failure \
  "release tag public-key boundary removed" \
  "tag-key verification must receive the public key at all four boundaries" \
  "$fixture"

fixture="$(make_fixture release-tag-fingerprint-comparison-weakened)"
replace_once "$fixture/scripts/verify-android-release-tag-signature.sh" \
  '[[ "$actual_fingerprint" == "$allowed_fingerprint" ]]' \
  '[[ -n "$actual_fingerprint" ]]'
expect_static_failure \
  "release tag fingerprint comparison weakened" \
  "compare the supplied key to the checked-in fingerprint" \
  "$fixture"

fixture="$(make_fixture release-tag-ssh-format-weakened)"
replace_once "$fixture/scripts/verify-android-release-tag-signature.sh" \
  'gpg.format=ssh' \
  'gpg.format=openpgp'
expect_static_failure \
  "release tag SSH signature verification weakened" \
  "cryptographically verify an SSH-signed Git tag" \
  "$fixture"

fixture="$(make_fixture release-tag-allowed-signers-weakened)"
replace_once "$fixture/scripts/verify-android-release-tag-signature.sh" \
  'gpg.ssh.allowedSignersFile' \
  'gpg.ssh.defaultKeyCommand'
expect_static_failure \
  "release tag isolated allowed-signers verification weakened" \
  "use an isolated exact allowed-signers file" \
  "$fixture"

fixture="$(make_fixture release-tag-policy-count-weakened)"
replace_once "$fixture/scripts/test-android-release-signed-tag-policy.sh" \
  '[[ "$negative_count" == "13" ]]' \
  '[[ "$negative_count" == "12" ]]'
expect_static_failure \
  "release tag policy adversarial count weakened" \
  "retain all 13 adversarial cases" \
  "$fixture"

fixture="$(make_fixture release-build-cache-restored)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  '--no-build-cache' \
  '--build-cache'
expect_static_failure \
  "release build cache restored" \
  "force fresh R8 execution" \
  "$fixture"

fixture="$(make_fixture release-build-log-bound-weakened)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'ulimit -f 262144' \
  'ulimit -f unlimited'
expect_static_failure \
  "release build-log bound weakened" \
  "bound the captured log at 128 MiB" \
  "$fixture"

fixture="$(make_fixture release-build-log-verification-boundary-removed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  './scripts/verify-android-release-build-log.sh' \
  './scripts/disabled-android-release-build-log.sh' \
  1
expect_static_failure \
  "release build-log verification boundary removed" \
  "build must verify the bounded log before use, staging, and upload" \
  "$fixture"

fixture="$(make_fixture unsigned-build-log-artifact-omitted)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '[[ "${#entries[@]}" -eq 4 ]]' \
  '[[ "${#entries[@]}" -eq 3 ]]' \
  1
expect_static_failure \
  "unsigned build log omitted from staging" \
  "unsigned artifact staging must contain exactly four entries" \
  "$fixture"

fixture="$(make_fixture downloaded-build-log-artifact-omitted)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '[[ "${#entries[@]}" -eq 4 ]]' \
  '[[ "${#entries[@]}" -eq 3 ]]' \
  2
expect_static_failure \
  "downloaded build log omitted from exact transfer" \
  "signing must reject extra downloaded entries" \
  "$fixture"

fixture="$(make_fixture downloaded-build-log-digest-bypassed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'sha256sum "$build_log"' \
  'printf %s "$ANDROID_RELEASE_BUILD_LOG_SHA256"' \
  3
expect_static_failure \
  "downloaded build-log digest bypassed" \
  "signing must independently hash the downloaded build log" \
  "$fixture"

fixture="$(make_fixture unsigned-build-log-provenance-binding-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  '              buildLog: {' \
  '              untrustedBuildLog: {'
expect_static_failure \
  "unsigned build-log provenance binding removed" \
  "unsigned provenance must bind the captured build log" \
  "$fixture"

fixture="$(make_fixture controls-build-log-test-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'bash ./scripts/test-android-release-build-log.sh' \
  'true # build-log adversarial test removed'
expect_static_failure \
  "release controls build-log test removed" \
  "credential-free controls must exercise test-android-release-build-log.sh" \
  "$fixture"

fixture="$(make_fixture ci-build-log-test-removed)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  'bash ./scripts/test-android-release-build-log.sh' \
  'true # build-log adversarial test removed'
expect_static_failure \
  "CI build-log test removed" \
  "Android CI must run the build-log adversarial suite" \
  "$fixture"

fixture="$(make_fixture build-log-test-count-weakened)"
replace_once "$fixture/scripts/test-android-release-build-log.sh" \
  '[[ "$negative_count" == "26" ]]' \
  '[[ "$negative_count" == "25" ]]'
expect_static_failure \
  "build-log adversarial count weakened" \
  "retain all 26 adversarial cases" \
  "$fixture"

fixture="$(make_fixture build-log-nofollow-open-removed)"
replace_once "$fixture/scripts/verify-android-release-build-log.sh" \
  '    flags |= os.O_NOFOLLOW' \
  '    flags |= 0'
expect_static_failure \
  "build-log no-follow open removed" \
  "refuse symlink traversal at snapshot open" \
  "$fixture"

fixture="$(make_fixture build-log-bundle-marker-check-weakened)"
replace_once "$fixture/scripts/verify-android-release-build-log.sh" \
  'if len(bundle_task_outcomes) != 1 or bundle_task_outcomes[0] is not None:' \
  'if not bundle_task_outcomes:'
expect_static_failure \
  "build-log exact bundle marker check weakened" \
  "require exactly one executed bundle task" \
  "$fixture"

fixture="$(make_fixture jdkfile-install-bypassed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "distribution: jdkfile" \
  "distribution: temurin" \
  1
expect_static_failure \
  "archive-backed JDK install bypassed" \
  "checksum-pinned JDK archive" \
  "$fixture"

fixture="$(make_fixture unsigned-java-evidence-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'javaBinarySha256: $javaBinarySha256' \
  'javaBinarySha256: "disabled"'
expect_static_failure \
  "unsigned Java evidence removed" \
  "bind the reviewed Java executable" \
  "$fixture"

fixture="$(make_fixture build-reviewer-mapping-mutated)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'EXPECTED_ANDROID_RELEASE_BUILD_REVIEWER_IDS: ${{ vars.ANDROID_RELEASE_BUILD_REVIEWER_IDS }}' \
  "EXPECTED_ANDROID_RELEASE_BUILD_REVIEWER_IDS: invented" \
  1
expect_static_failure \
  "build reviewer mapping mutated" \
  "approved build-reviewer allowlist" \
  "$fixture"

fixture="$(make_fixture signing-reviewer-mapping-mutated)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'EXPECTED_ANDROID_RELEASE_SIGNING_REVIEWER_IDS: ${{ vars.ANDROID_RELEASE_SIGNING_REVIEWER_IDS }}' \
  "EXPECTED_ANDROID_RELEASE_SIGNING_REVIEWER_IDS: invented" \
  3
expect_static_failure \
  "signing reviewer mapping mutated" \
  "approved signing-reviewer allowlist" \
  "$fixture"

fixture="$(make_fixture governance-build-call-removed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "bash ./scripts/verify-android-release-governance.sh" \
  "true # governance removed" \
  2
expect_static_failure \
  "build governance call removed" \
  "every release boundary must revalidate environment governance" \
  "$fixture"

fixture="$(make_fixture governance-self-review-weakened)"
replace_once "$fixture/scripts/verify-android-release-governance.sh" \
  ".prevent_self_review == true" \
  ".prevent_self_review == false"
expect_static_failure \
  "self-review prevention weakened" \
  "protected environments must prevent self-review" \
  "$fixture"

fixture="$(make_fixture governance-disjoint-check-removed)"
replace_once "$fixture/scripts/verify-android-release-governance.sh" \
  'fail "Android release build and signing reviewer allowlists must be disjoint."' \
  'fail "Reviewer overlap accepted."'
expect_static_failure \
  "disjoint reviewer enforcement removed" \
  "reviewer allowlists must be disjoint" \
  "$fixture"

fixture="$(make_fixture governance-count-weakened)"
replace_once "$fixture/scripts/test-android-release-governance.sh" \
  '[[ "$negative_count" == "43" ]]' \
  '[[ "$negative_count" == "42" ]]'
expect_static_failure \
  "governance adversarial count weakened" \
  "43 adversarial cases" \
  "$fixture"

fixture="$(make_fixture firebase-combined-overlay)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "RELEASE_OVERLAY_MODE=firebase" \
  "RELEASE_OVERLAY_MODE=release"
expect_static_failure \
  "combined overlay in build" \
  "obsolete combined release overlay" \
  "$fixture"

fixture="$(make_fixture signing-combined-overlay)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "RELEASE_OVERLAY_MODE=signing" \
  "RELEASE_OVERLAY_MODE=release"
expect_static_failure \
  "combined overlay in signing" \
  "obsolete combined release overlay" \
  "$fixture"

fixture="$(make_fixture firebase-secret-in-signing)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      APPROVED_FIREBASE_JSON_SHA256: \${{ vars.ANDROID_RELEASE_FIREBASE_JSON_SHA256 }}" \
  $'      GOOGLE_SERVICES_RELEASE_JSON_B64: ${{ secrets.GOOGLE_SERVICES_RELEASE_JSON_B64 }}\n      APPROVED_FIREBASE_JSON_SHA256: ${{ vars.ANDROID_RELEASE_FIREBASE_JSON_SHA256 }}'
expect_static_failure \
  "Firebase secret in signing" \
  "release-signing contains forbidden build/Play material: GOOGLE_SERVICES_RELEASE_JSON_B64" \
  "$fixture"

fixture="$(make_fixture keystore-secret-in-build)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "      ANDROID_RELEASE_FIREBASE_JSON_SHA256: \${{ vars.ANDROID_RELEASE_FIREBASE_JSON_SHA256 }}" \
  $'      ANDROID_RELEASE_KEYSTORE_B64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_B64 }}\n      ANDROID_RELEASE_FIREBASE_JSON_SHA256: ${{ vars.ANDROID_RELEASE_FIREBASE_JSON_SHA256 }}'
expect_static_failure \
  "keystore secret in build" \
  "release-build contains forbidden signing/Play material: ANDROID_RELEASE_KEYSTORE_B64" \
  "$fixture"

fixture="$(make_fixture unsigned-flag-disabled)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "ANDROID_UNSIGNED_RELEASE_BUILD=true" \
  "ANDROID_UNSIGNED_RELEASE_BUILD=false"
expect_static_failure \
  "unsigned gate disabled" \
  "exact CI-only unsigned-release gate" \
  "$fixture"

fixture="$(make_fixture unsigned-task-changed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "./gradlew :app:bundleRelease" \
  "./gradlew :app:assembleRelease"
expect_static_failure \
  "unsigned task changed" \
  "exact source-bound unsigned bundle task" \
  "$fixture"

fixture="$(make_fixture gradle-in-signing)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Sign exact unsigned AAB with upload certificate" \
  $'- name: Forbidden Gradle signing task\n        run: ./gradlew :app:help\n\n      - name: Sign exact unsigned AAB with upload certificate'
expect_static_failure \
  "Gradle in signing job" \
  "release-signing must never execute Gradle" \
  "$fixture"

fixture="$(make_fixture standalone-signer-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "./scripts/sign-android-release-aab.sh \\" \
  "jarsigner \\"
expect_static_failure \
  "standalone signer removed" \
  "standalone certificate-pinned signer" \
  "$fixture"

fixture="$(make_fixture upload-certificate-pin-mutated)"
replace_once "$fixture/scripts/sign-android-release-aab.sh" \
  "$EXPECTED_UPLOAD_CERT_SHA256" \
  "50391092F5B97E782C6528CC571ADF5DBEDFE2D05023BABC7C4E339E584A4A9A"
expect_static_failure \
  "upload certificate pin mutated" \
  "pin exactly one registered upload certificate" \
  "$fixture"

fixture="$(make_fixture signer-trusted-digest-output-removed)"
replace_once "$fixture/scripts/sign-android-release-aab.sh" \
  '[android-release-signer] signed-aab-sha256=$signed_sha256' \
  '[android-release-signer] signed-aab-digest-disabled=$signed_sha256'
expect_static_failure \
  "signer trusted digest output removed" \
  "emit its trusted signed AAB digest" \
  "$fixture"

fixture="$(make_fixture verifier-private-snapshot-bypassed)"
replace_once "$fixture/scripts/verify-android-play-release.sh" \
  $'"$AAB_SIGNATURE_VERIFIER" \\\n    "$artifact_snapshot"' \
  $'"$AAB_SIGNATURE_VERIFIER" \\\n    "$artifact"'
expect_static_failure \
  "signature verifier redirected away from private snapshot" \
  "signature check must consume the private snapshot" \
  "$fixture"

fixture="$(make_fixture play-r8-policy-weakened)"
replace_once "$fixture/scripts/verify-android-play-release.sh" \
  $'"$RELEASE_COMMIT" \\\n    required' \
  $'"$RELEASE_COMMIT" \\\n    absent'
expect_static_failure \
  "Play verifier R8 policy weakened" \
  "release verifier must explicitly require R8 metadata" \
  "$fixture"

fixture="$(make_fixture release-r8-policy-weakened)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "AAB_IDENTITY_R8_POLICY=required" \
  "AAB_IDENTITY_R8_POLICY=absent"
expect_static_failure \
  "release workflow R8 policy weakened" \
  "release workflow must pin exactly one required R8 policy" \
  "$fixture"

fixture="$(make_fixture debug-r8-policy-strengthened)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "AAB_IDENTITY_R8_POLICY: absent" \
  "AAB_IDENTITY_R8_POLICY: required"
expect_static_failure \
  "debug workflow R8 policy changed" \
  "unminified debug CI must pin exactly one absent R8 policy" \
  "$fixture"

fixture="$(make_fixture key-cleanup-not-always)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  $'- name: Remove keystore immediately after signing\n        if: ${{ always() }}' \
  $'- name: Remove keystore immediately after signing\n        if: ${{ success() }}'
expect_static_failure \
  "key cleanup only on success" \
  "cleanup must run even after signing failure" \
  "$fixture"

fixture="$(make_fixture identity-before-cleanup)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Remove keystore immediately after signing" \
  "- name: Verify signed AAB identity, signature, and permissions"
expect_static_failure \
  "signed checks before key cleanup" \
  "signed-byte checks must run after keystore removal" \
  "$fixture"

fixture="$(make_fixture signed-artifact-verifier-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  $'--artifact \\\n              "$SIGNED_RELEASE_AAB" \\\n              "$SIGNED_RELEASE_AAB_SHA256" |' \
  $'--artifact \\\n              "$ANDROID_UNSIGNED_AAB_PATH" \\\n              "$SIGNED_RELEASE_AAB_SHA256" |'
expect_static_failure \
  "signed artifact verifier redirected" \
  "signed-byte verification must use the exact signer output" \
  "$fixture"

fixture="$(make_fixture trusted-signer-digest-propagation-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'SIGNED_RELEASE_AAB_SHA256=$signed_sha256' \
  'UNTRUSTED_RELEASE_AAB_SHA256=$signed_sha256'
expect_static_failure \
  "trusted signer digest propagation removed" \
  "propagate the trusted signer digest" \
  "$fixture"

fixture="$(make_fixture permission-proof-removed)"
replace_once "$fixture/scripts/verify-android-aab-identity.sh" \
  "forbidden_permissions = {" \
  "ignored_permissions = {"
expect_static_failure \
  "broad media permission proof removed" \
  "reject broad media/storage permissions" \
  "$fixture"

fixture="$(make_fixture network-policy-reference-check-removed)"
replace_once "$fixture/scripts/verify-android-aab-identity.sh" \
  'if network_security_config != "@xml/network_security_config":' \
  "if False: # exact network-policy reference disabled"
expect_static_failure \
  "network-policy manifest reference check removed" \
  "require the exact network-policy reference" \
  "$fixture"

fixture="$(make_fixture compiled-cleartext-denial-removed)"
replace_once "$fixture/scripts/verify-android-aab-identity.sh" \
  'if base_attributes != {"cleartextTrafficPermitted": ("false", False)}:' \
  "if False: # compiled cleartext denial disabled"
expect_static_failure \
  "compiled network-policy cleartext denial removed" \
  "require a literal compiled cleartext denial" \
  "$fixture"

fixture="$(make_fixture unsigned-stage-extra-entry)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '[[ "${#entries[@]}" -eq 4 ]]' \
  '[[ "${#entries[@]}" -ge 4 ]]' \
  1
expect_static_failure \
  "unsigned stage allows extra entry" \
  "unsigned artifact staging must contain exactly four entries" \
  "$fixture"

fixture="$(make_fixture signed-stage-extra-entry)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '[[ "${#entries[@]}" -eq 3 ]]' \
  '[[ "${#entries[@]}" -ge 3 ]]' \
  1
expect_static_failure \
  "signed stage allows extra entry" \
  "signed artifact staging must contain exactly three entries" \
  "$fixture"

fixture="$(make_fixture download-extra-entry)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '[[ "${#entries[@]}" -eq 4 ]] || {' \
  '[[ "${#entries[@]}" -ge 4 ]] || {' \
  1
expect_static_failure \
  "download allows extra entry" \
  "signing must reject extra downloaded entries" \
  "$fixture"

fixture="$(make_fixture checksum-sidecar-bypassed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'cmp - "$checksum"' \
  "true # checksum comparison bypassed"
expect_static_failure \
  "checksum sidecar bypassed" \
  "compare the downloaded checksum sidecar byte-for-byte" \
  "$fixture"

fixture="$(make_fixture downloaded-hash-bypassed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'sha256sum "$unsigned_aab"' \
  'sha256sum "$checksum"' \
  2
expect_static_failure \
  "independent AAB hash bypassed" \
  "independently hash the downloaded unsigned AAB" \
  "$fixture"

fixture="$(make_fixture unsigned-provenance-keyset-weakened)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "(keys | sort) == ([" \
  "(keys | sort) != ([" \
  1
expect_static_failure \
  "unsigned provenance keyset weakened" \
  "exact unsigned provenance key set" \
  "$fixture"

fixture="$(make_fixture signed-provenance-keyset-weakened)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "(keys | sort) == ([" \
  "(keys | sort) != ([" \
  3
expect_static_failure \
  "signed provenance keyset weakened" \
  "exact final provenance key set" \
  "$fixture"

fixture="$(make_fixture final-provenance-trusted-digest-bypassed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  '            "$UNSIGNED_PROVENANCE_SHA256" |' \
  '            "$(sha256sum "$unsigned_provenance" | awk '\''{print $1}'\'')" |'
expect_static_failure \
  "final provenance trusted digest bypassed" \
  "trusted unsigned-provenance digest" \
  "$fixture"

fixture="$(make_fixture final-artifact-trusted-digest-bypassed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  'artifact_sha256="$SIGNED_RELEASE_AAB_SHA256"' \
  'artifact_sha256="$(sha256sum "$artifact" | awk '\''{print $1}'\'')"'
expect_static_failure \
  "final artifact digest recomputed from mutable staging path" \
  "final artifact metadata must use the trusted signer digest" \
  "$fixture"

fixture="$(make_fixture final-provenance-schema-revalidation-bypassed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "if exact_approved_unsigned_provenance then" \
  "if true then"
expect_static_failure \
  "final provenance schema revalidation bypassed" \
  "gated by unsigned revalidation" \
  "$fixture"

fixture="$(make_fixture unsigned-attestation-before-stage)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Stage exact four-file unsigned artifact" \
  "- name: Attest all four unsigned artifact files"
expect_static_failure \
  "unsigned attestation before stage" \
  "unsigned evidence must be staged before attestation" \
  "$fixture"

fixture="$(make_fixture unsigned-upload-before-verification)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Verify all unsigned attestations against immutable source" \
  "- name: Upload exact attested unsigned artifact"
expect_static_failure \
  "unsigned upload before verification" \
  "unsigned attestations must pass before artifact upload" \
  "$fixture"

fixture="$(make_fixture key-before-download-verification)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Verify unsigned files, provenance, and attestations" \
  "- name: Restore keystore only after unsigned verification"
expect_static_failure \
  "key restored before downloaded verification" \
  "downloaded evidence must pass before last-moment source validation" \
  "$fixture"

fixture="$(make_fixture key-before-remote-revalidation)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Revalidate remote source before keystore restoration" \
  "- name: Restore keystore only after unsigned verification"
expect_static_failure \
  "key restored before remote revalidation" \
  "keystore must remain absent through last-moment remote validation" \
  "$fixture"

fixture="$(make_fixture final-stage-before-identity)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Verify signed AAB identity, signature, and permissions" \
  "- name: Stage exact three-file signed artifact"
expect_static_failure \
  "final stage before identity" \
  "signed identity evidence must precede final staging" \
  "$fixture"

fixture="$(make_fixture final-upload-before-verification)"
swap_once "$fixture/.github/workflows/android-release.yml" \
  "- name: Verify all signed attestations against immutable source" \
  "- name: Upload exact attested signed artifact"
expect_static_failure \
  "final upload before attestation verification" \
  "final attestations must pass before the pre-upload digest gate" \
  "$fixture"

fixture="$(make_fixture preupload-aab-digest-check-bypassed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  'sha256sum "$artifact"' \
  'printf %s "$SIGNED_RELEASE_AAB_SHA256"' \
  6
expect_static_failure \
  "pre-upload AAB digest check bypassed" \
  "pre-upload gate must independently hash the signed AAB" \
  "$fixture"

fixture="$(make_fixture source-digest-unbound)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  '--source-digest "$RELEASE_COMMIT"' \
  '--source-digest "attacker-controlled"' \
  2
expect_static_failure \
  "attestation source digest unbound" \
  "every attestation verification must bind the source commit" \
  "$fixture"

fixture="$(make_fixture self-hosted-attestation-allowed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "--deny-self-hosted-runners" \
  "--allow-self-hosted-runners" \
  3
expect_static_failure \
  "self-hosted attestation allowed" \
  "attestation verification must reject self-hosted builders" \
  "$fixture"

fixture="$(make_fixture unsigned-artifact-name-mutated)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "name: fearless-android-unsigned-\${{ inputs.release_tag }}" \
  "name: mutable-unsigned" \
  1
expect_static_failure \
  "unsigned artifact name mutated" \
  "transfer only the exact unsigned artifact" \
  "$fixture"

fixture="$(make_fixture signed-artifact-name-mutated)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "name: fearless-android-signed-\${{ inputs.release_tag }}" \
  "name: mutable-signed"
expect_static_failure \
  "signed artifact name mutated" \
  "expose only the exact final signed artifact" \
  "$fixture"

fixture="$(make_fixture controls-guard-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "bash ./scripts/test-android-play-release-guards.sh" \
  "true # release architecture guard removed"
expect_static_failure \
  "release controls guard removed" \
  "credential-free controls must exercise test-android-play-release-guards.sh" \
  "$fixture"

fixture="$(make_fixture ci-guard-removed)"
replace_once "$fixture/.github/workflows/android-ci.yml" \
  "bash ./scripts/test-android-play-release-guards.sh" \
  "true # release architecture guard removed"
expect_static_failure \
  "CI release guard removed" \
  "Android CI must run this release architecture guard" \
  "$fixture"

fixture="$(make_fixture overlay-count-weakened)"
replace_once "$fixture/scripts/test-release-overlay-interruption.sh" \
  "expected 20 split-overlay negative cases" \
  "expected 19 split-overlay negative cases"
expect_static_failure \
  "overlay adversarial count weakened" \
  "20 split-overlay adversarial cases" \
  "$fixture"

fixture="$(make_fixture signer-count-weakened)"
replace_once "$fixture/scripts/test-android-release-aab-signer.sh" \
  "expected 31 negative/adversarial cases" \
  "expected 30 negative/adversarial cases"
expect_static_failure \
  "signer adversarial count weakened" \
  "31 adversarial cases" \
  "$fixture"

fixture="$(make_fixture verifier-snapshot-count-weakened)"
replace_once "$fixture/scripts/test-android-play-release-verifier-snapshot.sh" \
  "expected 8 negative/adversarial cases" \
  "expected 7 negative/adversarial cases"
expect_static_failure \
  "Play verifier snapshot adversarial count weakened" \
  "eight adversarial cases" \
  "$fixture"

fixture="$(make_fixture dependency-provenance-count-weakened)"
replace_once "$fixture/scripts/test-gradle-dependency-provenance.sh" \
  "EXPECTED_NEGATIVE_COUNT=40" \
  "EXPECTED_NEGATIVE_COUNT=39"
expect_static_failure \
  "dependency-provenance adversarial count weakened" \
  "retain all 40 adversarial cases" \
  "$fixture"

fixture="$(make_fixture identity-count-weakened)"
replace_once "$fixture/scripts/test-android-aab-identity.sh" \
  "expected 135 negative cases" \
  "expected 134 negative cases"
expect_static_failure \
  "AAB identity adversarial count weakened" \
  "identity test must fix its adversarial count" \
  "$fixture"

fixture="$(make_fixture r8-synthesis-boundary-count-weakened)"
replace_once "$fixture/scripts/test-android-aab-identity.sh" \
  "expected 15 R8-synthesis boundary negative/adversarial cases" \
  "expected 14 R8-synthesis boundary negative/adversarial cases"
expect_static_failure \
  "R8 synthesis boundary adversarial count weakened" \
  "R8 synthesis boundary test must fix its adversarial count" \
  "$fixture"

fixture="$(make_fixture unsigned-count-weakened)"
replace_once "$fixture/scripts/test-android-unsigned-release-build.sh" \
  "expected 25 negative/adversarial cases" \
  "expected 24 negative/adversarial cases"
expect_static_failure \
  "unsigned task-graph count weakened" \
  "25 adversarial cases" \
  "$fixture"

fixture="$(make_fixture source-count-weakened)"
replace_once "$fixture/scripts/test-android-release-source-binding.sh" \
  "expected 10 negative cases" \
  "expected 9 negative cases"
expect_static_failure \
  "source-binding count weakened" \
  "10 adversarial cases" \
  "$fixture"

fixture="$(make_fixture source-tree-gate-removed)"
replace_nth "$fixture/.github/workflows/android-release.yml" \
  "./scripts/verify-android-release-source-tree.sh" \
  "true # source-tree gate removed" \
  6
expect_static_failure \
  "pre-keystore source-tree gate removed" \
  "seven release boundaries" \
  "$fixture"

fixture="$(make_fixture source-tree-count-weakened)"
replace_once "$fixture/scripts/test-android-release-source-tree.sh" \
  '[[ "$negative_count" == "13" ]]' \
  '[[ "$negative_count" == "12" ]]'
expect_static_failure \
  "source-tree adversarial count weakened" \
  "13 adversarial cases" \
  "$fixture"

fixture="$(make_fixture artifact-verification-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "                artifactVerification: {" \
  "                ignoredVerification: {"
expect_static_failure \
  "artifact verification evidence removed" \
  "construct signed artifact-verification evidence" \
  "$fixture"

fixture="$(make_fixture identity-evidence-digest-removed)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  "                  identityEvidenceSha256: \$aabIdentityEvidenceSha256," \
  "                  identityEvidenceSha256: \"disabled\","
expect_static_failure \
  "identity evidence digest removed" \
  "final provenance must bind the signed identity evidence digest" \
  "$fixture"

fixture="$(make_fixture extra-signing-secret)"
replace_once "$fixture/.github/workflows/android-release.yml" \
  '          CI_KEYSTORE_PASS: ${{ secrets.ANDROID_RELEASE_KEYSTORE_PASSWORD }}' \
  $'          FORBIDDEN_TOKEN: ${{ secrets.EXTRA_RELEASE_TOKEN }}\n          CI_KEYSTORE_PASS: ${{ secrets.ANDROID_RELEASE_KEYSTORE_PASSWORD }}'
expect_static_failure \
  "extra signing secret" \
  "only the three upload-keystore secrets" \
  "$fixture"

snapshot_input="$tmp_dir/final-boundary-unsigned-provenance.json"
snapshot_output="$tmp_dir/final-boundary-verified-snapshot.json"
printf '%s\n\n' \
  '{"schemaVersion":1,"artifactKind":"unsigned-android-app-bundle"}' \
  >"$snapshot_input"
snapshot_sha256="$(hash_file "$snapshot_input")"
"$SNAPSHOT" "$snapshot_input" "$snapshot_sha256" >"$snapshot_output"
cmp "$snapshot_input" "$snapshot_output" ||
  fail "verified release snapshot did not preserve the exact trusted bytes"
positive_count=$((positive_count + 1))

printf '%s\n\n' \
  '{"schemaVersion":2,"artifactKind":"unsigned-android-app-bundle"}' \
  >"$snapshot_input"
expect_failure \
  "unsigned provenance mutated at final-consumption boundary" \
  "changed after its trusted digest was recorded" \
  "$SNAPSHOT" "$snapshot_input" "$snapshot_sha256"

expect_failure \
  "malformed trusted provenance digest" \
  "exactly 64 lowercase hexadecimal" \
  "$SNAPSHOT" "$snapshot_input" "$(
    printf '%s' "$snapshot_sha256" | tr '[:lower:]' '[:upper:]'
  )"

ln -s "$snapshot_input" "$tmp_dir/final-boundary-symlink.json"
expect_failure \
  "symlinked final provenance input" \
  "non-empty regular, non-symlink file" \
  "$SNAPSHOT" "$tmp_dir/final-boundary-symlink.json" "$snapshot_sha256"

: >"$tmp_dir/final-boundary-empty.json"
expect_failure \
  "empty final provenance input" \
  "non-empty regular, non-symlink file" \
  "$SNAPSHOT" "$tmp_dir/final-boundary-empty.json" "$snapshot_sha256"

expect_failure "missing verifier mode" "Usage:" "$VERIFY"
expect_failure \
  "missing signing inputs" \
  "CI_KEYSTORE_PATH is required" \
  env -i PATH="$PATH" HOME="$HOME" "$VERIFY" --signing-config

printf 'not-an-aab' >"$tmp_dir/not-an-aab"
malformed_aab_sha256="$(hash_file "$tmp_dir/not-an-aab")"
expect_failure \
  "malformed artifact" \
  "not a valid ZIP archive" \
  "$VERIFY" --artifact "$tmp_dir/not-an-aab" "$malformed_aab_sha256"
ln -s "$tmp_dir/not-an-aab" "$tmp_dir/symlink.aab"
expect_failure \
  "symlink artifact" \
  "must be a non-empty regular, non-symlink file" \
  "$VERIFY" --artifact "$tmp_dir/symlink.aab" "$malformed_aab_sha256"

overlay_dir="$tmp_dir/overlay"
firebase_fixture="$tmp_dir/google-services.json"
cp "$ROOT_DIR/app/src/release/google-services.json" "$firebase_fixture"
firebase_before="$(hash_file "$firebase_fixture")"
release_json="$tmp_dir/release-google-services.json"
printf '%s' \
  '{"project_info":{"project_id":"fearless-release-test"},"client":[{"client_info":{"android_client_info":{"package_name":"jp.co.soramitsu.fearless"}}}]}' \
  >"$release_json"
release_json_b64="$(base64_one_line "$release_json")"
release_json_sha256="$(hash_file "$release_json")"
dummy_keystore="$tmp_dir/fearless-upload.jks"
printf 'test-only-keystore-material\n' >"$dummy_keystore"
dummy_keystore_b64="$(base64_one_line "$dummy_keystore")"
unsigned_aab="$tmp_dir/unsigned.aab"
python3 - "$unsigned_aab" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[1], "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("base/manifest/AndroidManifest.xml", b"manifest fixture\n")
    archive.writestr("base/dex/classes.dex", b"dex fixture\n")
PY

expect_failure \
  "missing explicit overlay mode" \
  "must be exactly firebase or signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    "$RESTORE"
expect_failure \
  "obsolete combined release mode" \
  "must be exactly firebase or signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=release \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    "$RESTORE"
expect_failure \
  "removed Play overlay mode" \
  "must be exactly firebase or signing" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=play \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    "$RESTORE"
expect_failure \
  "Firebase overlay with signing material" \
  "Firebase-only overlay forbids signing and Play credentials" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_failure \
  "Firebase overlay with Play material" \
  "Firebase-only overlay forbids signing and Play credentials" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    "$RESTORE"
expect_failure \
  "Firebase overlay digest mismatch" \
  "does not match the approved Firebase SHA-256 digest" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=firebase \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_RELEASE_FIREBASE_JSON_SHA256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa \
    "$RESTORE"
expect_failure \
  "signing overlay outside explicit CI" \
  "signing overlay is restricted to an explicit CI environment" \
  env -i PATH="$PATH" HOME="$HOME" \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_failure \
  "signing overlay with Firebase material" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"
expect_failure \
  "signing overlay with Play material" \
  "signing-only overlay forbids Firebase and Play credentials" \
  env -i PATH="$PATH" HOME="$HOME" CI=true \
    RELEASE_OVERLAY_MODE=signing \
    RELEASE_OVERLAY_DIR="$overlay_dir" \
    PLAY_SERVICE_ACCOUNT_JSON_B64=dGVzdA== \
    ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
    ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
    "$RESTORE"

env -i PATH="$PATH" HOME="$HOME" CI=true \
  RELEASE_OVERLAY_MODE=firebase \
  RELEASE_OVERLAY_DIR="$overlay_dir" \
  GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
  GOOGLE_SERVICES_RELEASE_JSON_B64="$release_json_b64" \
  ANDROID_RELEASE_FIREBASE_JSON_SHA256="$release_json_sha256" \
  "$RESTORE" >/dev/null
[[ "$(hash_file "$firebase_fixture")" == "$release_json_sha256" &&
  -s "$overlay_dir/.release-overlay-active" &&
  ! -e "$overlay_dir/fearless-upload.jks" ]] ||
  fail "Firebase-only positive case did not remain isolated"
env -i PATH="$PATH" HOME="$HOME" CI=true \
  RELEASE_OVERLAY_DIR="$overlay_dir" \
  GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
  "$CLEANUP" >/dev/null
[[ "$(hash_file "$firebase_fixture")" == "$firebase_before" ]] ||
  fail "Firebase-only cleanup did not restore the placeholder"
positive_count=$((positive_count + 1))

env -i PATH="$PATH" HOME="$HOME" CI=true \
  RELEASE_OVERLAY_MODE=signing \
  RELEASE_OVERLAY_DIR="$overlay_dir" \
  GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
  ANDROID_UNSIGNED_AAB_PATH="$unsigned_aab" \
  ANDROID_RELEASE_KEYSTORE_B64="$dummy_keystore_b64" \
  "$RESTORE" >/dev/null
[[ -s "$overlay_dir/fearless-upload.jks" &&
  -s "$overlay_dir/.signing-overlay-active" &&
  "$(hash_file "$firebase_fixture")" == "$firebase_before" ]] ||
  fail "signing-only positive case did not remain isolated"
env -i PATH="$PATH" HOME="$HOME" CI=true \
  RELEASE_OVERLAY_DIR="$overlay_dir" \
  GOOGLE_SERVICES_RELEASE_PATH="$firebase_fixture" \
  "$CLEANUP" >/dev/null
[[ ! -e "$overlay_dir/fearless-upload.jks" &&
  ! -e "$overlay_dir/.signing-overlay-active" &&
  "$(hash_file "$firebase_fixture")" == "$firebase_before" ]] ||
  fail "signing-only cleanup left credential material"
positive_count=$((positive_count + 1))

[[ "$positive_count" == "$EXPECTED_POSITIVE_COUNT" ]] ||
  fail "expected $EXPECTED_POSITIVE_COUNT positive cases; got $positive_count"
[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]] ||
  fail "expected $EXPECTED_NEGATIVE_COUNT negative cases; got $negative_count"

echo \
  "[android-play-release-test] $positive_count positive + $negative_count deterministic negative/adversarial release-architecture cases passed"
