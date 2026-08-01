#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -P -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd -P -- "$SCRIPT_DIR/.." && pwd)"
AUDIT="$ROOT_DIR/scripts/audit-google-play-internal-app-sharing-publication.sh"
TMP_DIR_RAW="$(mktemp -d "${TMPDIR:-/tmp}/fearless-android-google-play-ias.XXXXXX")"
TMP_DIR="$(cd -P -- "$TMP_DIR_RAW" && pwd)"
BASE_FIXTURE="$TMP_DIR/base"
EXPECTED_POSITIVE_CASES=6
EXPECTED_NEGATIVE_CASES=184
POSITIVE_CASES=0
NEGATIVE_CASES=0
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "[google-play-ias-publication-test][android][error] $*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

prepare_base_fixture() {
  mkdir -p \
    "$BASE_FIXTURE/.github/workflows" \
    "$BASE_FIXTURE/app/build/outputs/bundle/internalAppSharing" \
    "$BASE_FIXTURE/build/reports/google-play-internal-app-sharing" \
    "$BASE_FIXTURE/config" \
    "$BASE_FIXTURE/docs/releases" \
    "$BASE_FIXTURE/scripts"
  cp "$ROOT_DIR/.gitignore" "$BASE_FIXTURE/.gitignore"
  cp "$ROOT_DIR/.github/workflows/android-ci.yml" "$BASE_FIXTURE/.github/workflows/android-ci.yml"
  cp "$ROOT_DIR/.github/workflows/android-release.yml" "$BASE_FIXTURE/.github/workflows/android-release.yml"
  cp "$ROOT_DIR/config/google-play-internal-app-sharing-publication.json" "$BASE_FIXTURE/config/google-play-internal-app-sharing-publication.json"
  cp "$ROOT_DIR/docs/google-play-internal-app-sharing-publication.md" "$BASE_FIXTURE/docs/google-play-internal-app-sharing-publication.md"
  cp "$ROOT_DIR/docs/release-checklist.md" "$BASE_FIXTURE/docs/release-checklist.md"
  cp "$ROOT_DIR/docs/releases/PROCESS.md" "$BASE_FIXTURE/docs/releases/PROCESS.md"
  cp "$ROOT_DIR/scripts/audit-google-play-internal-app-sharing-publication.sh" "$BASE_FIXTURE/scripts/audit-google-play-internal-app-sharing-publication.sh"
  cp "$ROOT_DIR/scripts/test-google-play-internal-app-sharing-publication-audit.sh" "$BASE_FIXTURE/scripts/test-google-play-internal-app-sharing-publication-audit.sh"
  cp "$ROOT_DIR/scripts/write-google-play-internal-app-sharing-handoff.js" "$BASE_FIXTURE/scripts/write-google-play-internal-app-sharing-handoff.js"
  cp "$ROOT_DIR/scripts/test-google-play-internal-app-sharing-handoff-writer.js" "$BASE_FIXTURE/scripts/test-google-play-internal-app-sharing-handoff-writer.js"
  cp "$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py" "$BASE_FIXTURE/scripts/verify-android-aab-native-page-alignment.py"
  cp "$ROOT_DIR/scripts/validate-local.sh" "$BASE_FIXTURE/scripts/validate-local.sh"
  node - \
    "$BASE_FIXTURE/build/reports/google-play-internal-app-sharing/public-link.json" \
    "$BASE_FIXTURE/config/google-play-internal-app-sharing-publication.json" \
    "$BASE_FIXTURE/scripts/audit-google-play-internal-app-sharing-publication.sh" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');

const handoffPath = process.argv[2];
const manifestPath = process.argv[3];
const auditPath = process.argv[4];
const shareUrl = ['https://play.google.com', 'apps', 'test', 'A'.repeat(11), 'B'.repeat(90)].join('/');
const digest = crypto.createHash('sha256').update(shareUrl, 'utf8').digest('hex');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
const originalDigest = manifest.publication.shareUrlSha256;
manifest.publication.shareUrlSha256 = digest;
manifest.testerPageObservation.requestedShareUrlSha256 = digest;
manifest.testerPageObservation.privateSafariObservation.continueUrlSha256 = digest;
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);
fs.writeFileSync(handoffPath, `${JSON.stringify({
  schemaVersion: 1,
  shareUrl,
  shareUrlSha256: digest,
  expiresDisplayLocal: '2026-09-24T10:49',
  timezone: 'not-displayed-by-google',
}, null, 2)}\n`);
const audit = fs.readFileSync(auditPath, 'utf8').split(originalDigest).join(digest);
fs.writeFileSync(auditPath, audit);
NODE
  chmod 700 \
    "$BASE_FIXTURE/scripts/audit-google-play-internal-app-sharing-publication.sh" \
    "$BASE_FIXTURE/scripts/test-google-play-internal-app-sharing-publication-audit.sh" \
    "$BASE_FIXTURE/scripts/write-google-play-internal-app-sharing-handoff.js" \
    "$BASE_FIXTURE/scripts/test-google-play-internal-app-sharing-handoff-writer.js" \
    "$BASE_FIXTURE/scripts/verify-android-aab-native-page-alignment.py"
  chmod 600 "$BASE_FIXTURE/build/reports/google-play-internal-app-sharing/public-link.json"
  git -C "$BASE_FIXTURE" init -q
  repin_manifest "$BASE_FIXTURE"
}

new_fixture() {
  local label="$1"
  local fixture
  fixture="$(mktemp -d "$TMP_DIR/fixture-$label.XXXXXX")"
  rmdir "$fixture"
  cp -R "$BASE_FIXTURE" "$fixture"
  printf '%s\n' "$fixture"
}

repin_manifest() {
  local fixture="$1"
  local digest
  digest="$(sha256_file "$fixture/config/google-play-internal-app-sharing-publication.json")"
  perl -0pi -e \
    "s/EXPECTED_MANIFEST_SHA256=\"[0-9a-zA-Z_]+\"/EXPECTED_MANIFEST_SHA256=\"$digest\"/" \
    "$fixture/scripts/audit-google-play-internal-app-sharing-publication.sh"
}

repin_doc() {
  local fixture="$1"
  local digest
  digest="$(sha256_file "$fixture/docs/google-play-internal-app-sharing-publication.md")"
  perl -0pi -e \
    "s/EXPECTED_DOC_SHA256=\"[0-9a-zA-Z_]+\"/EXPECTED_DOC_SHA256=\"$digest\"/" \
    "$fixture/scripts/audit-google-play-internal-app-sharing-publication.sh"
}

mutate_json() {
  local file="$1"
  local program="$2"
  node - "$file" "$program" <<'NODE'
const fs = require('node:fs');
const path = process.argv[2];
const program = process.argv[3];
const value = JSON.parse(fs.readFileSync(path, 'utf8'));
Function('value', `'use strict'; ${program}`)(value);
fs.writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
NODE
}

run_fixture() {
  local fixture="$1"
  shift
  IAS_PUBLICATION_AUDIT_ROOT="$fixture" \
    "$fixture/scripts/audit-google-play-internal-app-sharing-publication.sh" "$@"
}

expect_success() {
  local label="$1"
  shift
  local output
  if ! output="$("$@" 2>&1)"; then
    fail "$label was rejected: $output"
  fi
  POSITIVE_CASES=$((POSITIVE_CASES + 1))
  echo "[google-play-ias-publication-test][android] PASS positive: $label"
}

expect_failure() {
  local label="$1"
  local expected="$2"
  shift 2
  local output
  if output="$("$@" 2>&1)"; then
    fail "$label was accepted: $output"
  fi
  [[ "$output" == *"$expected"* ]] ||
    fail "$label failed without expected diagnostic '$expected': $output"
  NEGATIVE_CASES=$((NEGATIVE_CASES + 1))
  echo "[google-play-ias-publication-test][android] PASS negative: $label"
}

manifest_case() {
  local label="$1"
  local program="$2"
  local expected="$3"
  local fixture
  fixture="$(new_fixture "manifest")"
  mutate_json "$fixture/config/google-play-internal-app-sharing-publication.json" "$program"
  repin_manifest "$fixture"
  expect_failure "$label" "$expected" run_fixture "$fixture" --manifest-only
}

handoff_case() {
  local label="$1"
  local program="$2"
  local expected="$3"
  local fixture
  fixture="$(new_fixture "handoff")"
  mutate_json "$fixture/build/reports/google-play-internal-app-sharing/public-link.json" "$program"
  chmod 600 "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
  expect_failure "$label" "$expected" run_fixture "$fixture" --handoff-only
}

remove_line() {
  local file="$1"
  local needle="$2"
  awk -v needle="$needle" 'index($0, needle) == 0 { print }' "$file" > "$file.next"
  mv "$file.next" "$file"
}

replace_fixed() {
  local file="$1"
  local before="$2"
  local after="$3"
  node - "$file" "$before" "$after" <<'NODE'
const fs = require('node:fs');
const [file, before, after] = process.argv.slice(2);
const source = fs.readFileSync(file, 'utf8');
if (!source.includes(before)) {
  throw new Error(`missing fixed replacement source: ${before}`);
}
fs.writeFileSync(file, source.replace(before, after));
NODE
}

prepare_base_fixture

fixture="$(new_fixture positive-baseline)"
expect_success "tracked publication contract" "$AUDIT" --manifest-only
expect_success "synthetic fixture tracked publication contract" run_fixture "$fixture" --manifest-only
expect_success "synthetic ignored link handoff" run_fixture "$fixture" --handoff-only
expect_success "exact upload minute is within recorded window" "$AUDIT" --manifest-only \
  --require-within-recorded-expiry-window --as-of-displayed-local 2026-07-26T10:49
expect_success "minute before expiry is within recorded window" "$AUDIT" --manifest-only \
  --require-within-recorded-expiry-window --as-of-displayed-local 2026-09-24T10:48
expect_success "strictly interior date is within recorded window" "$AUDIT" --manifest-only \
  --require-within-recorded-expiry-window --as-of-displayed-local 2026-08-01

manifest_case "schema version drift" 'value.schemaVersion = 2;' "schemaVersion must be 1"
manifest_case "platform drift" 'value.platform = "ios";' "platform must be android"
manifest_case "evidence kind drift" 'value.evidenceKind = "release";' "evidenceKind drifted"
manifest_case "service drift" 'value.publication.service = "other";' "publication service"
manifest_case "channel drift" 'value.publication.channel = "open-testing";' "publication channel"
manifest_case "observed status promoted" 'value.publication.statusAtObservation = "production";' "recorded as published"
manifest_case "raw URL classified committed" 'value.publication.shareUrlCommitted = true;' "raw share URL must not be committed"
manifest_case "unlisted URL classified public" 'value.publication.publiclyDisclosed = true;' "must not be classified as publicly disclosed"
manifest_case "share URL digest drift" 'value.publication.shareUrlSha256 = "0".repeat(64);' "share URL digest drifted"
manifest_case "canonical origin drift" 'value.publication.canonicalOrigin = "https://evil.example";' "origin must remain canonical"
manifest_case "path shape drift" 'value.publication.pathShape = "/apps/test/{token}";' "path shape drifted"
manifest_case "token alphabet drift" 'value.publication.tokenAlphabet = "arbitrary";' "token alphabet drifted"
manifest_case "upload token minimum weakened" 'value.publication.uploadTokenValidatorLengthBounds.minimum = 1;' "upload token validator minimum drifted"
manifest_case "upload token maximum weakened" 'value.publication.uploadTokenValidatorLengthBounds.maximum = 512;' "upload token validator maximum drifted"
manifest_case "install token minimum weakened" 'value.publication.installTokenValidatorLengthBounds.minimum = 1;' "install token validator minimum drifted"
manifest_case "install token maximum weakened" 'value.publication.installTokenValidatorLengthBounds.maximum = 1024;' "install token validator maximum drifted"
manifest_case "token bounds presented as Google protocol" 'value.publication.tokenValidatorBoundsBasis = "google-protocol";' "token validator bounds basis drifted"
manifest_case "observed upload token length drift" 'value.publication.observedUploadTokenLength = 12;' "observed upload token length drifted"
manifest_case "observed install token length drift" 'value.publication.observedInstallTokenLength = 89;' "observed install token length drifted"
manifest_case "downloader ceiling overstated" 'value.publication.maximumDistinctDownloaders = 101;' "downloader ceiling drifted"
manifest_case "link lifetime overstated" 'value.publication.lifetimeDays = 61;' "lifetime drifted"
manifest_case "policy source drift" 'value.publication.policySourceUrl = "https://example.com";' "policy source drifted"
manifest_case "Google re-signing hidden" 'value.publication.googleReSignsDeliveredApks = false;' "re-signing policy"

manifest_case "local artifact path drift" 'value.localSelectedArtifact.repositoryRelativePath = "app.aab";' "local artifact path drifted"
manifest_case "local artifact name drift" 'value.localSelectedArtifact.fileName = "other.aab";' "local artifact name drifted"
manifest_case "local artifact digest drift" 'value.localSelectedArtifact.sha256 = "0".repeat(64);' "local artifact digest drifted"
manifest_case "uppercase artifact digest" 'value.localSelectedArtifact.sha256 = value.localSelectedArtifact.sha256.toUpperCase();' "local artifact digest drifted"
manifest_case "local artifact byte drift" 'value.localSelectedArtifact.bytes = 41813904;' "byte count drifted"
manifest_case "local artifact package drift" 'value.localSelectedArtifact.packageName = "evil.package";' "package drifted"
manifest_case "local artifact version name drift" 'value.localSelectedArtifact.versionName = "4.2.0";' "versionName drifted"
manifest_case "local artifact version code drift" 'value.localSelectedArtifact.versionCode = 231;' "versionCode drifted"
manifest_case "debug signing hidden" 'value.localSelectedArtifact.signingClassification = "release-signed";' "explicitly debug-signed"
manifest_case "placeholder Firebase hidden" 'value.localSelectedArtifact.firebaseConfiguration = "production";' "public-placeholder Firebase"

manifest_case "Google-displayed package drift" 'value.googleDisplayedArtifact.packageName = "other";' "Google-displayed package"
manifest_case "Google-displayed version drift" 'value.googleDisplayedArtifact.versionName = "other";' "Google-displayed versionName"
manifest_case "undisplayed version code invented" 'value.googleDisplayedArtifact.versionCode = 230;' "did not display a version code"
manifest_case "undisplayed server digest invented" 'value.googleDisplayedArtifact.serverArtifactSha256 = "0".repeat(64);' "did not display a server artifact digest"
manifest_case "undisplayed server bytes invented" 'value.googleDisplayedArtifact.serverArtifactBytes = 41813903;' "did not display a server artifact byte count"
manifest_case "undisplayed server certificate invented" 'value.googleDisplayedArtifact.serverSigningCertificateSha256 = "0".repeat(64);' "did not display a server signing certificate"
manifest_case "server cryptographic binding invented" 'value.googleDisplayedArtifact.serverCryptographicBindingObserved = true;' "must not be claimed"
manifest_case "binding strength overstated" 'value.googleDisplayedArtifact.bindingStrength = "server-cryptographic";' "binding strength was overstated"

manifest_case "browser observation drift" 'value.googleConsoleObservation.browser = "Chrome";' "must remain Safari"
manifest_case "locale observation drift" 'value.googleConsoleObservation.locale = "en-US";' "locale drifted"
manifest_case "raw upload text translated" 'value.googleConsoleObservation.uploadedAsDisplayed.rawText = "Jul 16";' "raw displayed upload text drifted"
manifest_case "upload month/day translated" 'value.googleConsoleObservation.uploadedAsDisplayed.monthDay = "Jul 16";' "upload month/day drifted"
manifest_case "upload display time drift" 'value.googleConsoleObservation.uploadedAsDisplayed.time = "8:52";' "upload time drifted"
manifest_case "upload inferred year promoted to displayed" 'value.googleConsoleObservation.uploadedAsDisplayed.yearDisplayed = true;' "explicitly inferred"
manifest_case "normalized upload impossible" 'value.googleConsoleObservation.normalizedUploadLocal = "2026-02-30T08:51";' "normalized upload wall-clock drifted"
manifest_case "upload year inference removed" 'value.googleConsoleObservation.normalizedUploadYearInference = "displayed";' "inference basis drifted"
manifest_case "raw expiry date drift" 'value.googleConsoleObservation.expiresAsDisplayed.rawDate = "2026-09-15";' "raw displayed expiry date drifted"
manifest_case "raw expiry time drift" 'value.googleConsoleObservation.expiresAsDisplayed.rawTime = "8:52";' "raw displayed expiry time drifted"
manifest_case "normalized expiry precedes upload" 'value.googleConsoleObservation.normalizedExpiryLocal = "2026-07-15T08:51";' "expiry wall-clock drifted"
manifest_case "timestamp timezone invented" 'value.googleConsoleObservation.timezone = "UTC";' "must not invent a timezone"
manifest_case "interval basis promoted to UTC" 'value.googleConsoleObservation.intervalCalculation = "UTC";' "interval basis drifted"
manifest_case "displayed interval drift" 'value.googleConsoleObservation.displayedIntervalSeconds = 1;' "exactly 60 days"
manifest_case "selected radio raw label drift" 'value.googleConsoleObservation.accessControl.selectedRadioRawLabel = "Anyone";' "raw label drifted"
manifest_case "mailing-list raw label drift" 'value.googleConsoleObservation.accessControl.unselectedMailingListRawLabel = "Mailing list";' "raw label drifted"
manifest_case "selected radio semantic drift" 'value.googleConsoleObservation.accessControl.selectedRadioSemantic = "mailing-list";' "semantic observation drifted"
manifest_case "anyone-link selection hidden" 'value.googleConsoleObservation.accessControl.anyoneWithSharedLinkCanDownload = false;' "console setting"
manifest_case "mailing-list restriction invented" 'value.googleConsoleObservation.accessControl.mailingListRestrictionEnabled = true;' "must remain off"
manifest_case "Save state drift" 'value.googleConsoleObservation.accessControl.saveButtonState = "enabled";' "Save control observation drifted"
manifest_case "operator UI promoted to server proof" 'value.googleConsoleObservation.accessControl.authority = "server-proof";' "must not be promoted"
manifest_case "anonymous access invented" 'value.googleConsoleObservation.accessControl.unauthenticatedAccessVerified = true;' "anonymous access must not be claimed"

manifest_case "signed-in tester session hidden" 'value.testerPageObservation.signedInGoogleSession = false;' "signed-in Google session"
manifest_case "tester app identity drift" 'value.testerPageObservation.appName = "Fearless Wallet";' "app identity drifted"
manifest_case "tester developer identity drift" 'value.testerPageObservation.developerName = "Other";' "developer identity drifted"
manifest_case "tester instruction drift" 'value.testerPageObservation.instruction = "install-complete";' "Android instruction drifted"
manifest_case "tester host drift" 'value.testerPageObservation.pageHost = "evil.example";' "tester-page host drifted"
manifest_case "tester requested URL binding drift" 'value.testerPageObservation.requestedShareUrlSha256 = "0".repeat(64);' "tester page must bind"
manifest_case "device install invented" 'value.testerPageObservation.deviceInstallVerified = true;' "installation has not been verified"
manifest_case "independent tester invented" 'value.testerPageObservation.independentAccountInstallVerified = true;' "independent-account installation"
manifest_case "tester count invented" 'value.testerPageObservation.testersObserved = 1;' "no tester installs were observed"
manifest_case "Google account prerequisite removed" 'value.testerPageObservation.requirements.googleAccountRequired = false;' "googleAccountRequired"
manifest_case "Android device prerequisite removed" 'value.testerPageObservation.requirements.androidDeviceRequired = false;' "androidDeviceRequired"
manifest_case "Play Store prerequisite removed" 'value.testerPageObservation.requirements.googlePlayStoreRequired = false;' "googlePlayStoreRequired"
manifest_case "IAS opt-in prerequisite removed" 'value.testerPageObservation.requirements.internalAppSharingOptInRequired = false;' "internalAppSharingOptInRequired"
manifest_case "listing eligibility prerequisite removed" 'value.testerPageObservation.requirements.playStoreListingEligibilityRequired = false;' "playStoreListingEligibilityRequired"
manifest_case "private Safari result promoted" 'value.testerPageObservation.privateSafariObservation.result = "tester-page";' "private Safari result drifted"
manifest_case "private Safari continue binding drift" 'value.testerPageObservation.privateSafariObservation.continueUrlSha256 = "0".repeat(64);' "continue URL must bind"
manifest_case "unauthenticated tester page invented" 'value.testerPageObservation.privateSafariObservation.unauthenticatedTesterPageObserved = true;' "must not be claimed"

manifest_case "source commit invented" 'value.sourceProvenance.sourceCommit = "0".repeat(40);' "must not claim a source commit"
manifest_case "source commit binding invented" 'value.sourceProvenance.sourceCommitBound = true;' "must not claim a source-commit binding"
manifest_case "commit reproducibility invented" 'value.sourceProvenance.sourceReproducibleFromCommit = true;' "must not claim commit reproducibility"
manifest_case "dirty tree hidden" 'value.sourceProvenance.treeStateAtEvidenceReview = "clean";' "dirty evidence-review source state"

manifest_case "test-only classification removed" 'value.classification.testOnly = false;' "must remain test-only"
manifest_case "production equivalence invented" 'value.classification.productionEquivalent = true;' "productionEquivalent must remain false"
manifest_case "production readiness invented" 'value.classification.productionReady = true;' "productionReady must remain false"
manifest_case "production signing invented" 'value.classification.productionSigned = true;' "productionSigned must remain false"
manifest_case "Play App Signing invented" 'value.classification.playAppSigningValidated = true;' "playAppSigningValidated must remain false"
manifest_case "Play Integrity invented" 'value.classification.playIntegrityValidated = true;' "playIntegrityValidated must remain false"
manifest_case "app-link certificate evidence invented" 'value.classification.appLinkCertificateAssociationValidated = true;' "appLinkCertificateAssociationValidated must remain false"
manifest_case "production Firebase invented" 'value.classification.productionFirebaseValidated = true;' "productionFirebaseValidated must remain false"
manifest_case "production upgrade invented" 'value.classification.productionUpgradePathValidated = true;' "productionUpgradePathValidated must remain false"
manifest_case "normal Play track invented" 'value.classification.normalPlayTestingTrackValidated = true;' "normalPlayTestingTrackValidated must remain false"
manifest_case "allowed scope widened" 'value.classification.allowedUse.push("funded-transfer-testing");' "classification.allowedUse drifted"
manifest_case "prohibited claim removed" 'value.classification.prohibitedClaims.pop();' "classification.prohibitedClaims drifted"

manifest_case "screenshot evidence invented" 'value.evidenceLimitations.supportingScreenshotCommitted = true;' "screenshot evidence must not be claimed"
manifest_case "tracked URL leakage hidden" 'value.evidenceLimitations.trackedManifestContainsShareUrl = true;' "must not claim to contain the share URL"
manifest_case "tracked secret leakage hidden" 'value.evidenceLimitations.trackedManifestContainsSecrets = true;' "must remain secret-free"
manifest_case "handoff permission classification drift" 'value.evidenceLimitations.localLinkHandoff = "tracked";' "handoff classification drifted"
manifest_case "live Google recheck invented" 'value.evidenceLimitations.currentGoogleAvailabilityRecheckedByAudit = true;' "must not claim live Google availability"
manifest_case "observation method drift" 'value.evidenceLimitations.observationMethod = "api";' "observation method drifted"
manifest_case "unexpected top-level key" 'value.unexpected = true;' "unexpected or missing keys"
manifest_case "control-bearing value" 'value.testerPageObservation.appName = "Fearless\nWallet";' "contains a control character"

fixture="$(new_fixture duplicate-json-key)"
perl -0pi -e 's/"schemaVersion": 1,/"schemaVersion": 1,\n  "schemaVersion": 1,/' \
  "$fixture/config/google-play-internal-app-sharing-publication.json"
repin_manifest "$fixture"
expect_failure "duplicate JSON key" "canonical two-space JSON" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture noncanonical-json)"
perl -0pi -e 's/^  "platform"/    "platform"/m' "$fixture/config/google-play-internal-app-sharing-publication.json"
repin_manifest "$fixture"
expect_failure "noncanonical JSON indentation" "canonical two-space JSON" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture invalid-json)"
printf '%s\n' '{"schemaVersion":' > "$fixture/config/google-play-internal-app-sharing-publication.json"
repin_manifest "$fixture"
expect_failure "invalid JSON" "invalid publication JSON" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture oversized-manifest)"
dd if=/dev/zero bs=33000 count=1 2>/dev/null | tr '\0' ' ' >> "$fixture/config/google-play-internal-app-sharing-publication.json"
expect_failure "oversized publication manifest" "exceeds 32 KiB" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture raw-url-leak)"
synthetic_upload_token="$(printf 'A%.0s' {1..11})"
synthetic_install_token="$(printf 'B%.0s' {1..90})"
printf '\n%s/%s/%s\n' 'https://play.google.com/apps/test' "$synthetic_upload_token" "$synthetic_install_token" >> \
  "$fixture/docs/google-play-internal-app-sharing-publication.md"
repin_doc "$fixture"
expect_failure "raw share URL leaked into tracked docs" "must not contain the raw unlisted share URL" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture credential-leak)"
mutate_json "$fixture/config/google-play-internal-app-sharing-publication.json" \
  'value.private_key = "-----BEGIN PRIVATE KEY----- attacker";'
repin_manifest "$fixture"
expect_failure "credential-like material in manifest" "credential-like material" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture missing-doc-marker)"
remove_line "$fixture/docs/google-play-internal-app-sharing-publication.md" "offline comparison cannot prove"
repin_doc "$fixture"
expect_failure "publication limitation removed from docs" "documentation is missing limitation marker" run_fixture "$fixture" --manifest-only

fixture="$(new_fixture oversized-doc)"
dd if=/dev/zero bs=66000 count=1 2>/dev/null | tr '\0' ' ' >> "$fixture/docs/google-play-internal-app-sharing-publication.md"
expect_failure "oversized publication document" "exceeds 64 KiB" run_fixture "$fixture" --manifest-only

for workflow_case in \
  'ci-test|.github/workflows/android-ci.yml|test-google-play-internal-app-sharing-publication-audit.sh|adversarial contract' \
  'ci-audit|.github/workflows/android-ci.yml|audit-google-play-internal-app-sharing-publication.sh --manifest-only|tracked IAS publication evidence' \
  'release-test|.github/workflows/android-release.yml|test-google-play-internal-app-sharing-publication-audit.sh|adversarial contract' \
  'release-audit|.github/workflows/android-release.yml|audit-google-play-internal-app-sharing-publication.sh --manifest-only|tracked IAS publication evidence' \
  'local-test|scripts/validate-local.sh|test-google-play-internal-app-sharing-publication-audit.sh|local validation must run' \
  'local-audit|scripts/validate-local.sh|audit-google-play-internal-app-sharing-publication.sh --manifest-only|local validation must audit' \
  'process-doc|docs/releases/PROCESS.md|audit-google-play-internal-app-sharing-publication.sh --manifest-only|release process must document' \
  'checklist-doc|docs/release-checklist.md|test-google-play-internal-app-sharing-publication-audit.sh|release checklist must require'; do
  IFS='|' read -r label file needle diagnostic <<< "$workflow_case"
  fixture="$(new_fixture "$label")"
  remove_line "$fixture/$file" "$needle"
  expect_failure "$label wiring removed" "$diagnostic" run_fixture "$fixture" --manifest-only
done

for symlink_case in manifest doc self-test alignment; do
  fixture="$(new_fixture "$symlink_case-symlink")"
  case "$symlink_case" in
    manifest)
      rm "$fixture/config/google-play-internal-app-sharing-publication.json"
      ln -s "$ROOT_DIR/config/google-play-internal-app-sharing-publication.json" \
        "$fixture/config/google-play-internal-app-sharing-publication.json"
      ;;
    doc)
      rm "$fixture/docs/google-play-internal-app-sharing-publication.md"
      ln -s "$ROOT_DIR/docs/google-play-internal-app-sharing-publication.md" \
        "$fixture/docs/google-play-internal-app-sharing-publication.md"
      ;;
    self-test)
      rm "$fixture/scripts/test-google-play-internal-app-sharing-publication-audit.sh"
      ln -s "$ROOT_DIR/scripts/test-google-play-internal-app-sharing-publication-audit.sh" \
        "$fixture/scripts/test-google-play-internal-app-sharing-publication-audit.sh"
      ;;
    alignment)
      rm "$fixture/scripts/verify-android-aab-native-page-alignment.py"
      ln -s "$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py" \
        "$fixture/scripts/verify-android-aab-native-page-alignment.py"
      ;;
  esac
  expect_failure "$symlink_case symlink substitution" "traverses a symlink" run_fixture "$fixture" --manifest-only
done

fixture="$(new_fixture audit-symlink)"
rm "$fixture/scripts/audit-google-play-internal-app-sharing-publication.sh"
ln -s "$ROOT_DIR/scripts/audit-google-play-internal-app-sharing-publication.sh" \
  "$fixture/scripts/audit-google-play-internal-app-sharing-publication.sh"
expect_failure "audit symlink substitution" "traverses a symlink" run_fixture "$fixture" --manifest-only

handoff_case "handoff schema drift" 'value.schemaVersion = 2;' "schemaVersion must be 1"
handoff_case "HTTP share URL" 'value.shareUrl = value.shareUrl.replace("https://", "http://");' "must use HTTPS"
handoff_case "wrong share URL host" 'value.shareUrl = value.shareUrl.replace("play.google.com", "evil.example");' "host must be exactly"
handoff_case "Google trailing-dot host confusion" 'value.shareUrl = value.shareUrl.replace("play.google.com/", "play.google.com./");' "host must be exactly"
handoff_case "share URL default-port canonicalization" 'value.shareUrl = value.shareUrl.replace("play.google.com", "play.google.com:443");' "canonical serialization"
handoff_case "share URL userinfo" 'value.shareUrl = value.shareUrl.replace("https://", "https://attacker@");' "must not contain userinfo"
handoff_case "share URL query" 'value.shareUrl += "?token=attacker";' "must not contain a query"
handoff_case "share URL fragment" 'value.shareUrl += "#fragment";' "must not contain a fragment"
handoff_case "wrong share URL path prefix" 'value.shareUrl = value.shareUrl.replace("/apps/test/", "/store/test/");' "path must have exactly two"
handoff_case "extra share URL path segment" 'value.shareUrl += "/extra";' "path must have exactly two"
handoff_case "encoded slash in share URL token" 'value.shareUrl = value.shareUrl.replace("/apps/test/", "/apps/test/%2F");' "path must have exactly two"
handoff_case "short upload token" 'const url = new URL(value.shareUrl); const parts = url.pathname.split("/"); parts[3] = "short"; url.pathname = parts.join("/"); value.shareUrl = url.href;' "upload token length is outside bounds"
handoff_case "long upload token" 'const url = new URL(value.shareUrl); const parts = url.pathname.split("/"); parts[3] = "A".repeat(65); url.pathname = parts.join("/"); value.shareUrl = url.href;' "upload token length is outside bounds"
handoff_case "short install token" 'value.shareUrl = value.shareUrl.replace(/\/[^/]+$/, "/" + "A".repeat(42));' "install token length is outside bounds"
handoff_case "long install token" 'value.shareUrl = value.shareUrl.replace(/\/[^/]+$/, "/" + "A".repeat(257));' "install token length is outside bounds"
handoff_case "share URL whitespace" 'value.shareUrl += " ";' "whitespace or a control character"
handoff_case "share URL below total length bound" 'value.shareUrl = ["https://play.google.com", "apps", "test", "a", "b"].join("/");' "share URL length is outside bounds"
handoff_case "handoff digest mismatch" 'value.shareUrlSha256 = "0".repeat(64);' "handoff digest does not match its URL"
handoff_case "handoff expiry drift" 'value.expiresDisplayLocal = "2026-09-25T10:49";' "handoff expiry does not match"
handoff_case "handoff timezone invention" 'value.timezone = "UTC";' "must not invent a timestamp timezone"
handoff_case "handoff unexpected secret field" 'value.private_key = "attacker";' "unexpected or missing keys"

fixture="$(new_fixture invalid-handoff-json)"
printf '%s\n' '{"schemaVersion":' > "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
chmod 600 "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "invalid handoff JSON" "invalid share-link handoff JSON" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture noncanonical-handoff)"
perl -0pi -e 's/^  "shareUrl"/    "shareUrl"/m' "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
chmod 600 "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "noncanonical handoff JSON" "canonical two-space JSON" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture handoff-permissions)"
chmod 644 "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "world-readable handoff" "permissions must be exactly 600" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture missing-handoff)"
rm "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "missing handoff" "must be a regular, non-symlink file" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture symlink-handoff)"
rm "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
ln -s "$ROOT_DIR/build/reports/google-play-internal-app-sharing/public-link.json" \
  "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "symlink handoff" "traverses a symlink" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture parent-symlink-handoff)"
mv "$fixture/build/reports/google-play-internal-app-sharing" "$fixture/build/reports/google-play-internal-app-sharing-real"
ln -s google-play-internal-app-sharing-real "$fixture/build/reports/google-play-internal-app-sharing"
expect_failure "handoff parent symlink" "traverses a symlink" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture tracked-handoff)"
git -C "$fixture" add -f build/reports/google-play-internal-app-sharing/public-link.json
expect_failure "tracked handoff" "must never be tracked" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture unignored-handoff)"
remove_line "$fixture/.gitignore" '/build'
remove_line "$fixture/.gitignore" '**/build/'
expect_failure "unignored handoff" "must remain ignored" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture oversized-handoff)"
dd if=/dev/zero bs=5000 count=1 2>/dev/null | tr '\0' 'A' >> "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
chmod 600 "$fixture/build/reports/google-play-internal-app-sharing/public-link.json"
expect_failure "oversized handoff" "exceeds 4 KiB" run_fixture "$fixture" --handoff-only

fixture="$(new_fixture missing-aab)"
expect_failure "missing AAB" "must be a regular, non-symlink file" run_fixture "$fixture"

fixture="$(new_fixture symlink-aab)"
ln -s "$ROOT_DIR/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab" \
  "$fixture/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab"
expect_failure "symlink AAB" "traverses a symlink" run_fixture "$fixture"

fixture="$(new_fixture wrong-aab-bytes)"
printf '%s' 'not-an-aab' > "$fixture/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab"
expect_failure "wrong AAB bytes" "byte count mismatch" run_fixture "$fixture"

fixture="$(new_fixture wrong-aab-digest)"
truncate -s 42215593 "$fixture/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab"
expect_failure "exact-size wrong AAB digest" "AAB digest mismatch" run_fixture "$fixture"

fixture="$(new_fixture oversized-aab)"
truncate -s 536870913 "$fixture/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab"
expect_failure "oversized AAB" "exceeds the 512 MiB audit bound" run_fixture "$fixture"

fixture="$(new_fixture parent-symlink-aab)"
mv "$fixture/app/build/outputs/bundle/internalAppSharing" "$fixture/app/build/outputs/bundle/internalAppSharing-real"
ln -s internalAppSharing-real "$fixture/app/build/outputs/bundle/internalAppSharing"
expect_failure "AAB parent symlink" "traverses a symlink" run_fixture "$fixture"

fixture="$(new_fixture missing-handoff-writer)"
rm "$fixture/scripts/write-google-play-internal-app-sharing-handoff.js"
expect_failure "missing private handoff writer" \
  "tracked publication-evidence dependency must be a regular" \
  run_fixture "$fixture" --manifest-only

fixture="$(new_fixture nonexecutable-handoff-writer)"
chmod 600 "$fixture/scripts/write-google-play-internal-app-sharing-handoff.js"
expect_failure "nonexecutable private handoff writer" \
  "private handoff writer must be executable" \
  run_fixture "$fixture" --manifest-only

fixture="$(new_fixture missing-handoff-writer-test)"
rm "$fixture/scripts/test-google-play-internal-app-sharing-handoff-writer.js"
expect_failure "missing private handoff writer self-test" \
  "tracked publication-evidence dependency must be a regular" \
  run_fixture "$fixture" --manifest-only

fixture="$(new_fixture handoff-writer-test-count-drift)"
replace_fixed "$fixture/scripts/test-google-play-internal-app-sharing-handoff-writer.js" \
  'const expectedNegativeCases = 17;' 'const expectedNegativeCases = 16;'
expect_failure "private handoff writer test count drift" \
  "private handoff writer self-test contract is missing" \
  run_fixture "$fixture" --manifest-only

expect_failure "unknown argument" "unknown argument" "$AUDIT" --unknown
expect_failure "handoff override ignored by manifest-only" "--handoff is not valid" "$AUDIT" --manifest-only --handoff build/reports/google-play-internal-app-sharing/public-link.json
expect_failure "artifact override ignored by handoff-only" "--artifact is valid only in full mode" "$AUDIT" --handoff-only --artifact app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab
expect_failure "clock injected without expiry-window mode" "requires --require-within-recorded-expiry-window" "$AUDIT" --manifest-only --as-of-displayed-local 2026-08-01
expect_failure "expiry-window mode without clock" "requires --as-of-displayed-local" "$AUDIT" --manifest-only --require-within-recorded-expiry-window
expect_failure "duplicate expiry-window mode" "may be selected only once" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --require-within-recorded-expiry-window --as-of-displayed-local 2026-08-01
expect_failure "duplicate injected clock" "may be injected only once" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-08-01 --as-of-displayed-local 2026-08-02
expect_failure "malformed injected clock" "must use YYYY-MM-DDTHH:MM" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local tomorrow
expect_failure "impossible injected date" "is not a real calendar date" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-02-30
expect_failure "date-only upload boundary" "fails closed on/before upload date" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-07-26
expect_failure "date-only expiry boundary" "fails closed on/before upload date" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-09-24
expect_failure "minute before upload" "outside the recorded temporal window" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-07-26T10:48
expect_failure "exact expiry minute" "outside the recorded temporal window" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-09-24T10:49
expect_failure "minute after expiry" "outside the recorded temporal window" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-09-24T10:50
expect_failure "date before upload" "fails closed on/before upload date" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-07-25
expect_failure "date after expiry" "fails closed on/before upload date" "$AUDIT" --manifest-only --require-within-recorded-expiry-window --as-of-displayed-local 2026-09-25
expect_failure "artifact outside repository" "must remain inside the Android repository" "$AUDIT" --artifact /tmp/outside.aab
expect_failure "handoff outside repository" "must remain inside the Android repository" "$AUDIT" --handoff-only --handoff /tmp/outside.json

fixture="$(new_fixture root-symlink)"
ln -s "$fixture" "$TMP_DIR/root-alias"
expect_failure "repository root symlink" "traverses a symlink" env \
  IAS_PUBLICATION_AUDIT_ROOT="$TMP_DIR/root-alias" "$AUDIT" --manifest-only

[[ "$POSITIVE_CASES" -eq "$EXPECTED_POSITIVE_CASES" ]] ||
  fail "executed $POSITIVE_CASES positive fixtures; expected exactly $EXPECTED_POSITIVE_CASES"
[[ "$NEGATIVE_CASES" -eq "$EXPECTED_NEGATIVE_CASES" ]] ||
  fail "executed $NEGATIVE_CASES negative fixtures; expected exactly $EXPECTED_NEGATIVE_CASES"

echo "[google-play-ias-publication-test][android] all $POSITIVE_CASES positive and $NEGATIVE_CASES adversarial fixtures passed."
