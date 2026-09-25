#!/usr/bin/env bash
set -euo pipefail

RAW_SCRIPT_PATH="${BASH_SOURCE[0]}"
if [[ "$RAW_SCRIPT_PATH" == /* ]]; then
  SCRIPT_PATH="$RAW_SCRIPT_PATH"
else
  SCRIPT_PATH="$PWD/${RAW_SCRIPT_PATH#./}"
fi
SCRIPT_DIR="$(cd -P -- "$(dirname -- "$SCRIPT_PATH")" && pwd)"
DEFAULT_ROOT_DIR="$(cd -P -- "$SCRIPT_DIR/.." && pwd)"
ROOT_DIR="${IAS_PUBLICATION_AUDIT_ROOT:-$DEFAULT_ROOT_DIR}"
MANIFEST="$ROOT_DIR/config/google-play-internal-app-sharing-publication.json"
DOC="$ROOT_DIR/docs/google-play-internal-app-sharing-publication.md"
SELF_TEST="$ROOT_DIR/scripts/test-google-play-internal-app-sharing-publication-audit.sh"
HANDOFF_WRITER="$ROOT_DIR/scripts/write-google-play-internal-app-sharing-handoff.js"
HANDOFF_WRITER_TEST="$ROOT_DIR/scripts/test-google-play-internal-app-sharing-handoff-writer.js"
AAB_ALIGNMENT_VERIFIER="$ROOT_DIR/scripts/verify-android-aab-native-page-alignment.py"
CI_WORKFLOW="$ROOT_DIR/.github/workflows/android-ci.yml"
RELEASE_WORKFLOW="$ROOT_DIR/.github/workflows/android-release.yml"
RELEASE_PROCESS="$ROOT_DIR/docs/releases/PROCESS.md"
RELEASE_CHECKLIST="$ROOT_DIR/docs/release-checklist.md"
LOCAL_VALIDATOR="$ROOT_DIR/scripts/validate-local.sh"
DEFAULT_ARTIFACT="$ROOT_DIR/app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab"
DEFAULT_HANDOFF="$ROOT_DIR/build/reports/google-play-internal-app-sharing/public-link.json"
EXPECTED_MANIFEST_SHA256="69d6fbb2b838b76a541d2e8d15fe691abe5bbf722ad75ba11753cbafca01c878"
EXPECTED_DOC_SHA256="fcbcf9c327c4920b8565f3ea9a04c3d0bce5acda78557fccef8ab17f033455fd"
EXPECTED_ARTIFACT_SHA256="e963c993afb013451b3c241a6a40f9f267dbb0e9740ae1b79ad2e808b6650dad"
EXPECTED_ARTIFACT_BYTES=42215593
EXPECTED_SHARE_URL_SHA256="3f04ae9bf0efbd03d43b346945f07a5311b988de8500dec68f1a0263ebe1b59c"

MODE="full"
ARTIFACT="$DEFAULT_ARTIFACT"
HANDOFF="$DEFAULT_HANDOFF"
ARTIFACT_OVERRIDDEN=false
HANDOFF_OVERRIDDEN=false
REQUIRE_WITHIN_EXPIRY=false
AS_OF_DISPLAYED_LOCAL=""

fail() {
  echo "[google-play-ias-publication][android][error] $*" >&2
  exit 1
}

usage() {
  cat <<'EOF'
Usage: audit-google-play-internal-app-sharing-publication.sh [options]

Options:
  --manifest-only   Verify only the tracked evidence contract (for clean CI checkouts).
  --handoff-only    Verify the tracked contract and ignored link handoff, not the AAB.
  --artifact PATH   Verify PATH instead of the default ignored AAB (full mode only).
  --handoff PATH    Verify PATH instead of the default ignored link handoff.
  --require-within-recorded-expiry-window
                    Require an injected displayed-local clock inside the recorded window.
                    This proves temporal non-expiry only, never current Google availability.
  --as-of-displayed-local VALUE
                    Inject YYYY-MM-DD or YYYY-MM-DDTHH:MM for the expiry-window check.
  -h, --help        Show this help.
EOF
}

while (($#)); do
  case "$1" in
    --manifest-only)
      [[ "$MODE" == "full" ]] || fail "publication audit mode may be selected only once"
      MODE="manifest-only"
      shift
      ;;
    --handoff-only)
      [[ "$MODE" == "full" ]] || fail "publication audit mode may be selected only once"
      MODE="handoff-only"
      shift
      ;;
    --artifact)
      [[ $# -ge 2 && -n "$2" ]] || fail "--artifact requires a non-empty path"
      ARTIFACT="$2"
      ARTIFACT_OVERRIDDEN=true
      shift 2
      ;;
    --handoff)
      [[ $# -ge 2 && -n "$2" ]] || fail "--handoff requires a non-empty path"
      HANDOFF="$2"
      HANDOFF_OVERRIDDEN=true
      shift 2
      ;;
    --require-within-recorded-expiry-window)
      [[ "$REQUIRE_WITHIN_EXPIRY" == false ]] || fail "expiry-window mode may be selected only once"
      REQUIRE_WITHIN_EXPIRY=true
      shift
      ;;
    --as-of-displayed-local)
      [[ $# -ge 2 && -n "$2" ]] || fail "--as-of-displayed-local requires a non-empty value"
      [[ -z "$AS_OF_DISPLAYED_LOCAL" ]] || fail "displayed-local clock may be injected only once"
      AS_OF_DISPLAYED_LOCAL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      fail "unknown argument: $1"
      ;;
  esac
done

[[ "$MODE" == "full" || "$ARTIFACT_OVERRIDDEN" == false ]] ||
  fail "--artifact is valid only in full mode"
[[ "$MODE" != "manifest-only" || "$HANDOFF_OVERRIDDEN" == false ]] ||
  fail "--handoff is not valid in manifest-only mode"
[[ "$REQUIRE_WITHIN_EXPIRY" == true || -z "$AS_OF_DISPLAYED_LOCAL" ]] ||
  fail "--as-of-displayed-local requires --require-within-recorded-expiry-window"
[[ "$REQUIRE_WITHIN_EXPIRY" == false || -n "$AS_OF_DISPLAYED_LOCAL" ]] ||
  fail "--require-within-recorded-expiry-window requires --as-of-displayed-local"
[[ "$ROOT_DIR" == /* ]] || fail "IAS_PUBLICATION_AUDIT_ROOT must be absolute"

if [[ "$ARTIFACT" != /* ]]; then
  ARTIFACT="$ROOT_DIR/$ARTIFACT"
fi
if [[ "$HANDOFF" != /* ]]; then
  HANDOFF="$ROOT_DIR/$HANDOFF"
fi
[[ "$ARTIFACT" == "$ROOT_DIR/"* ]] || fail "artifact path must remain inside the Android repository"
[[ "$HANDOFF" == "$ROOT_DIR/"* ]] || fail "handoff path must remain inside the Android repository"

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

file_bytes() {
  if stat -f '%z' "$1" >/dev/null 2>&1; then
    stat -f '%z' "$1"
  else
    stat -c '%s' "$1"
  fi
}

file_mode() {
  if stat -f '%Lp' "$1" >/dev/null 2>&1; then
    stat -f '%Lp' "$1"
  else
    stat -c '%a' "$1"
  fi
}

assert_no_symlink_components() {
  local path="$1"
  local label="$2"
  local current=""
  local component
  local -a components=()

  [[ "$path" == /* ]] || fail "$label path must be absolute"
  [[ "/$path/" != *"/../"* ]] || fail "$label path must not contain parent traversal"
  IFS='/' read -r -a components <<< "$path"
  for component in "${components[@]}"; do
    [[ -n "$component" && "$component" != "." ]] || continue
    current="$current/$component"
    [[ ! -L "$current" ]] || fail "$label path traverses a symlink: $current"
  done
}

require_regular_file() {
  local path="$1"
  local label="$2"
  assert_no_symlink_components "$path" "$label"
  [[ -f "$path" && ! -L "$path" ]] || fail "$label must be a regular, non-symlink file: $path"
}

require_executable_file() {
  local path="$1"
  local label="$2"
  require_regular_file "$path" "$label"
  [[ -x "$path" ]] || fail "$label must be executable: $path"
}

require_marker() {
  local path="$1"
  local marker="$2"
  local label="$3"
  grep -Fq -- "$marker" "$path" || fail "$label"
}

require_active_marker() {
  local path="$1"
  local marker="$2"
  local label="$3"
  grep -F -- "$marker" "$path" |
    grep -Eq '^[[:space:]]*[^#[:space:]]' || fail "$label"
}

require_executable_file "$SCRIPT_PATH" "publication audit"
for tracked in \
  "$MANIFEST" "$DOC" "$SELF_TEST" "$HANDOFF_WRITER" \
  "$HANDOFF_WRITER_TEST" "$AAB_ALIGNMENT_VERIFIER" \
  "$CI_WORKFLOW" "$RELEASE_WORKFLOW" "$RELEASE_PROCESS" \
  "$RELEASE_CHECKLIST" "$LOCAL_VALIDATOR"; do
  require_regular_file "$tracked" "tracked publication-evidence dependency"
done
require_executable_file "$SELF_TEST" "publication audit self-test"
require_executable_file "$HANDOFF_WRITER" "private handoff writer"
require_executable_file "$HANDOFF_WRITER_TEST" "private handoff writer self-test"
require_executable_file "$AAB_ALIGNMENT_VERIFIER" "AAB native-alignment verifier"
for marker in \
  'EXPECTED_POSITIVE_CASES=6' \
  'EXPECTED_NEGATIVE_CASES=184' \
  'executed $POSITIVE_CASES positive fixtures; expected exactly $EXPECTED_POSITIVE_CASES' \
  'executed $NEGATIVE_CASES negative fixtures; expected exactly $EXPECTED_NEGATIVE_CASES' \
  'all $POSITIVE_CASES positive and $NEGATIVE_CASES adversarial fixtures passed.'; do
  require_marker "$SELF_TEST" "$marker" "publication self-test exact case-count contract is missing: $marker"
done
for marker in \
  'const expectedPositiveCases = 2;' \
  'const expectedNegativeCases = 17;' \
  "'oversized input'" \
  'symlinked output directory' \
  'symlinked output file'; do
  require_marker "$HANDOFF_WRITER_TEST" "$marker" \
    "private handoff writer self-test contract is missing: $marker"
done
for marker in \
  "fs.readFileSync(0, 'utf8')" \
  'share URL digest does not match the publication manifest' \
  'fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY' \
  'fs.fsyncSync(descriptor)' \
  'fs.chmodSync(outputPath, 0o600)'; do
  require_marker "$HANDOFF_WRITER" "$marker" \
    "private handoff writer fail-closed contract is missing: $marker"
done
require_marker "$DOC" \
  'pbpaste | node ./scripts/write-google-play-internal-app-sharing-handoff.js' \
  "publication documentation must show the private standard-input handoff flow"
require_marker "$DOC" \
  'node ./scripts/test-google-play-internal-app-sharing-handoff-writer.js' \
  "publication documentation must show the handoff writer self-test"

manifest_bytes="$(file_bytes "$MANIFEST")"
doc_bytes="$(file_bytes "$DOC")"
[[ "$manifest_bytes" =~ ^[0-9]+$ && "$manifest_bytes" -le 32768 ]] ||
  fail "publication manifest exceeds 32 KiB"
[[ "$doc_bytes" =~ ^[0-9]+$ && "$doc_bytes" -le 65536 ]] ||
  fail "publication evidence document exceeds 64 KiB"

actual_manifest_sha256="$(sha256_file "$MANIFEST")"
[[ "$actual_manifest_sha256" == "$EXPECTED_MANIFEST_SHA256" ]] ||
  fail "publication manifest digest mismatch: expected $EXPECTED_MANIFEST_SHA256, got $actual_manifest_sha256"
actual_doc_sha256="$(sha256_file "$DOC")"
[[ "$actual_doc_sha256" == "$EXPECTED_DOC_SHA256" ]] ||
  fail "publication evidence document digest mismatch: expected $EXPECTED_DOC_SHA256, got $actual_doc_sha256"

if grep -Eiq -- '-----BEGIN (RSA |EC |OPENSSH )?PRIVATE KEY-----|"(private_key|client_secret|refresh_token|access_token|password|keystorePassword)"[[:space:]]*:|AIza[0-9A-Za-z_-]{25,}|gh[pousr]_[0-9A-Za-z]{20,}|sk_(live|test)_[0-9A-Za-z]{12,}' "$MANIFEST"; then
  fail "publication manifest contains credential-like material"
fi
if grep -ERIq \
  --include='*.sh' --include='*.json' --include='*.md' --include='*.yml' --include='*.yaml' \
  -- 'https://play\.google\.com/apps/test/[0-9A-Za-z_-]+/[0-9A-Za-z_-]+' \
  "$ROOT_DIR/config" "$ROOT_DIR/docs" "$ROOT_DIR/scripts" "$ROOT_DIR/.github"; then
  fail "tracked publication evidence must not contain the raw unlisted share URL"
fi

node - "$MANIFEST" <<'NODE'
const fs = require('node:fs');

const path = process.argv[2];
const raw = fs.readFileSync(path, 'utf8');
let manifest;
try {
  manifest = JSON.parse(raw);
} catch (error) {
  console.error(`[google-play-ias-publication][android][error] invalid publication JSON: ${error.message}`);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) {
    console.error(`[google-play-ias-publication][android][error] ${message}`);
    process.exit(1);
  }
}

function exactObject(value, keys, pathLabel) {
  assert(value !== null && typeof value === 'object' && !Array.isArray(value), `${pathLabel} must be an object`);
  const actual = Object.keys(value).sort();
  const expected = [...keys].sort();
  assert(JSON.stringify(actual) === JSON.stringify(expected), `${pathLabel} has unexpected or missing keys`);
}

function assertExactArray(actual, expected, pathLabel) {
  assert(Array.isArray(actual), `${pathLabel} must be an array`);
  assert(JSON.stringify(actual) === JSON.stringify(expected), `${pathLabel} drifted`);
}

function parseLocalWallClock(value, pathLabel) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  assert(match, `${pathLabel} must use YYYY-MM-DDTHH:MM`);
  const [, year, month, day, hour, minute] = match.map((part, index) => index === 0 ? part : Number(part));
  const millis = Date.UTC(year, month - 1, day, hour, minute);
  const date = new Date(millis);
  assert(
    date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 &&
      date.getUTCDate() === day && date.getUTCHours() === hour &&
      date.getUTCMinutes() === minute,
    `${pathLabel} is not a real displayed wall-clock time`,
  );
  return millis;
}

function rejectControls(value, pathLabel = 'manifest') {
  if (typeof value === 'string') {
    assert(!/[\u0000-\u001f\u007f]/.test(value), `${pathLabel} contains a control character`);
  } else if (Array.isArray(value)) {
    value.forEach((entry, index) => rejectControls(entry, `${pathLabel}[${index}]`));
  } else if (value && typeof value === 'object') {
    for (const [key, entry] of Object.entries(value)) {
      assert(!/[\u0000-\u001f\u007f]/.test(key), `${pathLabel} contains a control-bearing key`);
      rejectControls(entry, `${pathLabel}.${key}`);
    }
  }
}

assert(raw === `${JSON.stringify(manifest, null, 2)}\n`, 'publication manifest must be canonical two-space JSON with one trailing newline');
rejectControls(manifest);

exactObject(manifest, [
  'schemaVersion', 'platform', 'evidenceKind', 'publication', 'localSelectedArtifact',
  'googleDisplayedArtifact', 'googleConsoleObservation', 'testerPageObservation',
  'sourceProvenance', 'classification', 'evidenceLimitations',
], 'manifest');
assert(manifest.schemaVersion === 1, 'schemaVersion must be 1');
assert(manifest.platform === 'android', 'platform must be android');
assert(manifest.evidenceKind === 'google-play-internal-app-sharing-publication', 'evidenceKind drifted');

const publication = manifest.publication;
exactObject(publication, [
  'service', 'channel', 'statusAtObservation', 'shareUrlCommitted', 'publiclyDisclosed',
  'shareUrlSha256', 'canonicalOrigin', 'pathShape', 'tokenAlphabet',
  'uploadTokenValidatorLengthBounds', 'installTokenValidatorLengthBounds',
  'tokenValidatorBoundsBasis', 'observedUploadTokenLength', 'observedInstallTokenLength',
  'maximumDistinctDownloaders',
  'lifetimeDays', 'policySourceUrl', 'googleReSignsDeliveredApks',
], 'publication');
assert(publication.service === 'google-play', 'publication service must be Google Play');
assert(publication.channel === 'internal-app-sharing', 'publication channel must be Internal App Sharing');
assert(publication.statusAtObservation === 'published', 'publication must remain recorded as published at observation');
assert(publication.shareUrlCommitted === false, 'raw share URL must not be committed');
assert(publication.publiclyDisclosed === false, 'unlisted share URL must not be classified as publicly disclosed');
assert(publication.shareUrlSha256 === '3f04ae9bf0efbd03d43b346945f07a5311b988de8500dec68f1a0263ebe1b59c', 'share URL digest drifted');
assert(publication.canonicalOrigin === 'https://play.google.com', 'share URL origin must remain canonical');
assert(publication.pathShape === '/apps/test/{upload-token}/{install-token}', 'share URL path shape drifted');
assert(publication.tokenAlphabet === 'base64url-unpadded', 'share URL token alphabet drifted');
exactObject(publication.uploadTokenValidatorLengthBounds, ['minimum', 'maximum'], 'publication.uploadTokenValidatorLengthBounds');
assert(publication.uploadTokenValidatorLengthBounds.minimum === 10, 'upload token validator minimum drifted');
assert(publication.uploadTokenValidatorLengthBounds.maximum === 64, 'upload token validator maximum drifted');
exactObject(publication.installTokenValidatorLengthBounds, ['minimum', 'maximum'], 'publication.installTokenValidatorLengthBounds');
assert(publication.installTokenValidatorLengthBounds.minimum === 43, 'install token validator minimum drifted');
assert(publication.installTokenValidatorLengthBounds.maximum === 256, 'install token validator maximum drifted');
assert(publication.tokenValidatorBoundsBasis === 'local-defensive-parser-not-google-protocol', 'token validator bounds basis drifted');
assert(publication.observedUploadTokenLength === 11, 'observed upload token length drifted');
assert(publication.observedInstallTokenLength === 90, 'observed install token length drifted');
assert(publication.maximumDistinctDownloaders === 100, 'Internal App Sharing link downloader ceiling drifted');
assert(publication.lifetimeDays === 60, 'Internal App Sharing lifetime drifted');
assert(publication.policySourceUrl === 'https://support.google.com/googleplay/android-developer/answer/9844679', 'Internal App Sharing policy source drifted');
assert(publication.googleReSignsDeliveredApks === true, 'Internal App Sharing re-signing policy must remain explicit');

const local = manifest.localSelectedArtifact;
exactObject(local, [
  'repositoryRelativePath', 'fileName', 'sha256', 'bytes', 'packageName',
  'versionName', 'versionCode', 'signingClassification', 'firebaseConfiguration',
], 'localSelectedArtifact');
assert(local.repositoryRelativePath === 'app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab', 'local artifact path drifted');
assert(local.fileName === 'app-internalAppSharing.aab', 'local artifact name drifted');
assert(local.sha256 === 'e963c993afb013451b3c241a6a40f9f267dbb0e9740ae1b79ad2e808b6650dad', 'local artifact digest drifted');
assert(/^[0-9a-f]{64}$/.test(local.sha256), 'local artifact digest must be lowercase SHA-256');
assert(local.bytes === 42215593, 'local artifact byte count drifted');
assert(local.packageName === 'jp.co.soramitsu.fearless', 'local artifact package drifted');
assert(local.versionName === '4.2.0-ias', 'local artifact versionName drifted');
assert(local.versionCode === 230, 'local artifact versionCode drifted');
assert(local.signingClassification === 'debug-signed', 'IAS artifact must remain explicitly debug-signed');
assert(local.firebaseConfiguration === 'public-placeholder', 'IAS artifact must retain public-placeholder Firebase classification');

const google = manifest.googleDisplayedArtifact;
exactObject(google, [
  'packageName', 'versionName', 'versionCode', 'serverArtifactSha256',
  'serverArtifactBytes', 'serverSigningCertificateSha256',
  'serverCryptographicBindingObserved', 'bindingStrength',
], 'googleDisplayedArtifact');
assert(google.packageName === local.packageName, 'Google-displayed package must match the selected artifact record');
assert(google.versionName === local.versionName, 'Google-displayed versionName must match the selected artifact record');
assert(google.versionCode === null, 'Google did not display a version code');
assert(google.serverArtifactSha256 === null, 'Google did not display a server artifact digest');
assert(google.serverArtifactBytes === null, 'Google did not display a server artifact byte count');
assert(google.serverSigningCertificateSha256 === null, 'Google did not display a server signing certificate');
assert(google.serverCryptographicBindingObserved === false, 'Google-side cryptographic binding must not be claimed');
assert(google.bindingStrength === 'operator-observed-file-selection-plus-package-version-name-and-time-not-server-cryptographic', 'artifact binding strength was overstated or drifted');

const consoleObservation = manifest.googleConsoleObservation;
exactObject(consoleObservation, [
  'browser', 'locale', 'uploadedAsDisplayed', 'normalizedUploadLocal',
  'normalizedUploadYearInference', 'expiresAsDisplayed', 'normalizedExpiryLocal',
  'timezone', 'intervalCalculation', 'displayedIntervalSeconds', 'accessControl',
], 'googleConsoleObservation');
assert(consoleObservation.browser === 'Safari', 'publication browser observation must remain Safari');
assert(consoleObservation.locale === 'ja-JP', 'Google Console observation locale drifted');
exactObject(consoleObservation.uploadedAsDisplayed, ['rawText', 'monthDay', 'time', 'yearDisplayed'], 'googleConsoleObservation.uploadedAsDisplayed');
assert(consoleObservation.uploadedAsDisplayed.rawText === '7月26日 10:49にアップロードしました', 'raw displayed upload text drifted');
assert(consoleObservation.uploadedAsDisplayed.monthDay === '7月26日', 'displayed upload month/day drifted');
assert(consoleObservation.uploadedAsDisplayed.time === '10:49', 'displayed upload time drifted');
assert(consoleObservation.uploadedAsDisplayed.yearDisplayed === false, 'upload year must remain explicitly inferred, not displayed');
assert(consoleObservation.normalizedUploadLocal === '2026-07-26T10:49', 'normalized upload wall-clock drifted');
assert(consoleObservation.normalizedUploadYearInference === 'expiry-and-observation-year-2026', 'upload year inference basis drifted');
exactObject(consoleObservation.expiresAsDisplayed, ['rawDate', 'rawTime', 'date', 'time'], 'googleConsoleObservation.expiresAsDisplayed');
assert(consoleObservation.expiresAsDisplayed.rawDate === '2026年9月24日', 'raw displayed expiry date drifted');
assert(consoleObservation.expiresAsDisplayed.rawTime === '10:49', 'raw displayed expiry time drifted');
assert(consoleObservation.expiresAsDisplayed.date === '2026-09-24', 'displayed expiry date drifted');
assert(consoleObservation.expiresAsDisplayed.time === '10:49', 'displayed expiry time drifted');
assert(consoleObservation.normalizedExpiryLocal === '2026-09-24T10:49', 'normalized expiry wall-clock drifted');
assert(consoleObservation.timezone === 'not-displayed-by-google', 'timestamp must not invent a timezone');
assert(consoleObservation.intervalCalculation === 'displayed-wall-clock-without-timezone-conversion', 'timestamp interval basis drifted');
assert(consoleObservation.displayedIntervalSeconds === 5184000, 'displayed interval must remain exactly 60 days');
const uploadMillis = parseLocalWallClock(consoleObservation.normalizedUploadLocal, 'normalizedUploadLocal');
const expiryMillis = parseLocalWallClock(consoleObservation.normalizedExpiryLocal, 'normalizedExpiryLocal');
assert(expiryMillis > uploadMillis, 'displayed expiry must be after upload');
assert(expiryMillis - uploadMillis === consoleObservation.displayedIntervalSeconds * 1000, 'displayed wall-clock interval is inconsistent');
assert(expiryMillis - uploadMillis === publication.lifetimeDays * 24 * 60 * 60 * 1000, 'displayed wall-clock interval must be 60 days');

const access = consoleObservation.accessControl;
exactObject(access, [
  'selectedRadioRawLabel', 'unselectedMailingListRawLabel', 'selectedRadioSemantic',
  'anyoneWithSharedLinkCanDownload', 'mailingListRestrictionEnabled', 'saveButtonState',
  'authority', 'unauthenticatedAccessVerified',
], 'googleConsoleObservation.accessControl');
assert(access.selectedRadioRawLabel === 'リンクを共有したユーザーはダウンロードできます', 'selected anyone-with-link raw label drifted');
assert(access.unselectedMailingListRawLabel === 'メーリング リストへのアクセスの制限', 'unselected mailing-list raw label drifted');
assert(access.selectedRadioSemantic === 'users-shared-link-can-download', 'anyone-with-link semantic observation drifted');
assert(access.anyoneWithSharedLinkCanDownload === true, 'anyone-with-link console setting must remain recorded');
assert(access.mailingListRestrictionEnabled === false, 'mailing-list restriction must remain off');
assert(access.saveButtonState === 'disabled', 'Save control observation drifted');
assert(access.authority === 'operator-visible-console-state', 'access setting must not be promoted beyond operator-visible UI evidence');
assert(access.unauthenticatedAccessVerified === false, 'anonymous access must not be claimed');

const tester = manifest.testerPageObservation;
exactObject(tester, [
  'signedInGoogleSession', 'appName', 'developerName', 'instruction', 'pageHost',
  'requestedShareUrlSha256',
  'deviceInstallVerified', 'independentAccountInstallVerified', 'testersObserved',
  'requirements', 'privateSafariObservation',
], 'testerPageObservation');
assert(tester.signedInGoogleSession === true, 'tester-page observation occurred in a signed-in Google session');
assert(tester.appName === 'Fearless Wallet: DeFi Wallet', 'tester-page app identity drifted');
assert(tester.developerName === 'Soramitsu', 'tester-page developer identity drifted');
assert(tester.instruction === 'open-link-on-android-device', 'tester-page Android instruction drifted');
assert(tester.pageHost === 'play.google.com', 'tester-page host drifted');
assert(tester.requestedShareUrlSha256 === publication.shareUrlSha256, 'signed-in tester page must bind to the recorded share URL digest');
assert(tester.deviceInstallVerified === false, 'device installation has not been verified');
assert(tester.independentAccountInstallVerified === false, 'independent-account installation has not been verified');
assert(tester.testersObserved === 0, 'no tester installs were observed at capture time');
exactObject(tester.requirements, [
  'googleAccountRequired', 'androidDeviceRequired', 'googlePlayStoreRequired',
  'internalAppSharingOptInRequired', 'playStoreListingEligibilityRequired',
], 'testerPageObservation.requirements');
for (const [name, required] of Object.entries(tester.requirements)) {
  assert(required === true, `${name} must remain an explicit tester prerequisite`);
}
exactObject(tester.privateSafariObservation, [
  'result', 'continueUrlSha256', 'unauthenticatedTesterPageObserved',
], 'testerPageObservation.privateSafariObservation');
assert(tester.privateSafariObservation.result === 'google-sign-in-required', 'private Safari result drifted');
assert(tester.privateSafariObservation.continueUrlSha256 === publication.shareUrlSha256, 'private Safari continue URL must bind to the recorded share URL digest');
assert(tester.privateSafariObservation.unauthenticatedTesterPageObserved === false, 'unauthenticated tester page must not be claimed');

const source = manifest.sourceProvenance;
exactObject(source, [
  'sourceCommit', 'sourceCommitBound', 'sourceReproducibleFromCommit',
  'treeStateAtEvidenceReview',
], 'sourceProvenance');
assert(source.sourceCommit === null, 'dirty IAS artifact must not claim a source commit');
assert(source.sourceCommitBound === false, 'IAS artifact must not claim a source-commit binding');
assert(source.sourceReproducibleFromCommit === false, 'dirty IAS artifact must not claim commit reproducibility');
assert(source.treeStateAtEvidenceReview === 'dirty-uncommitted', 'dirty evidence-review source state must remain explicit');

const classification = manifest.classification;
exactObject(classification, [
  'testOnly', 'productionEquivalent', 'productionReady', 'productionSigned',
  'playAppSigningValidated', 'playIntegrityValidated',
  'appLinkCertificateAssociationValidated', 'productionFirebaseValidated',
  'productionUpgradePathValidated', 'normalPlayTestingTrackValidated',
  'allowedUse', 'prohibitedClaims',
], 'classification');
assert(classification.testOnly === true, 'IAS artifact must remain test-only');
for (const field of [
  'productionEquivalent', 'productionReady', 'productionSigned',
  'playAppSigningValidated', 'playIntegrityValidated',
  'appLinkCertificateAssociationValidated', 'productionFirebaseValidated',
  'productionUpgradePathValidated', 'normalPlayTestingTrackValidated',
]) {
  assert(classification[field] === false, `${field} must remain false`);
}
assertExactArray(classification.allowedUse, ['ui-testing', 'installation-testing'], 'classification.allowedUse');
assertExactArray(classification.prohibitedClaims, [
  'production-release-evidence',
  'production-signing-evidence',
  'play-app-signing-evidence',
  'play-integrity-evidence',
  'app-link-certificate-evidence',
  'production-firebase-evidence',
  'production-upgrade-evidence',
  'normal-play-testing-track-evidence',
  'device-install-evidence',
  'independent-tester-evidence',
], 'classification.prohibitedClaims');

const limitations = manifest.evidenceLimitations;
exactObject(limitations, [
  'observationMethod', 'supportingScreenshotCommitted', 'trackedManifestContainsShareUrl',
  'trackedManifestContainsSecrets', 'localLinkHandoff',
  'currentGoogleAvailabilityRecheckedByAudit',
], 'evidenceLimitations');
assert(limitations.observationMethod === 'manual-safari', 'observation method drifted');
assert(limitations.supportingScreenshotCommitted === false, 'uncommitted screenshot evidence must not be claimed');
assert(limitations.trackedManifestContainsShareUrl === false, 'tracked manifest must not claim to contain the share URL');
assert(limitations.trackedManifestContainsSecrets === false, 'tracked manifest must remain secret-free');
assert(limitations.localLinkHandoff === 'ignored-mode-0600-build-report', 'local link handoff classification drifted');
assert(limitations.currentGoogleAvailabilityRecheckedByAudit === false, 'offline audit must not claim live Google availability');
NODE

EXPIRY_WINDOW_RESULT="not-requested"
if [[ "$REQUIRE_WITHIN_EXPIRY" == true ]]; then
  node - "$MANIFEST" "$AS_OF_DISPLAYED_LOCAL" <<'NODE'
const fs = require('node:fs');

function fail(message) {
  console.error(`[google-play-ias-publication][android][error] ${message}`);
  process.exit(1);
}
function strictDate(value, label) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value);
  if (!match) fail(`${label} must use YYYY-MM-DD`);
  const [year, month, day] = match.slice(1).map(Number);
  const millis = Date.UTC(year, month - 1, day);
  const parsed = new Date(millis);
  if (parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1 || parsed.getUTCDate() !== day) {
    fail(`${label} is not a real calendar date`);
  }
  return millis;
}
function strictMinute(value, label) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(value);
  if (!match) fail(`${label} must use YYYY-MM-DDTHH:MM`);
  const [year, month, day, hour, minute] = match.slice(1).map(Number);
  const millis = Date.UTC(year, month - 1, day, hour, minute);
  const parsed = new Date(millis);
  if (
    parsed.getUTCFullYear() !== year || parsed.getUTCMonth() !== month - 1 ||
    parsed.getUTCDate() !== day || parsed.getUTCHours() !== hour ||
    parsed.getUTCMinutes() !== minute
  ) {
    fail(`${label} is not a real displayed-local wall-clock minute`);
  }
  return millis;
}

const manifest = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const asOf = process.argv[3];
const upload = manifest.googleConsoleObservation.normalizedUploadLocal;
const expiry = manifest.googleConsoleObservation.normalizedExpiryLocal;
const uploadDate = upload.slice(0, 10);
const expiryDate = expiry.slice(0, 10);

if (/^\d{4}-\d{2}-\d{2}$/.test(asOf)) {
  const asOfMillis = strictDate(asOf, 'injected displayed-local date');
  const uploadDateMillis = strictDate(uploadDate, 'recorded upload date');
  const expiryDateMillis = strictDate(expiryDate, 'recorded expiry date');
  if (!(asOfMillis > uploadDateMillis && asOfMillis < expiryDateMillis)) {
    fail('date-only expiry-window check fails closed on/before upload date and on/after expiry date');
  }
} else {
  const asOfMillis = strictMinute(asOf, 'injected displayed-local minute');
  const uploadMillis = strictMinute(upload, 'recorded upload minute');
  const expiryMillis = strictMinute(expiry, 'recorded expiry minute');
  if (!(asOfMillis >= uploadMillis && asOfMillis < expiryMillis)) {
    fail('injected displayed-local minute is outside the recorded temporal window');
  }
}
NODE
  EXPIRY_WINDOW_RESULT="within-recorded-window-injected-as-of=$AS_OF_DISPLAYED_LOCAL"
fi

for marker in \
  'Google did not display a server-side AAB digest, byte count, version code, or' \
  'not a Google-provided cryptographic binding' \
  'debug-signed and uses the public placeholder Firebase' \
  'production-equivalent' \
  'unauthenticated download' \
  'device install' \
  '100-distinct-downloader ceiling' \
  'mode-0600 handoff file' \
  'availability must be checked separately' \
  'private Safari request for the same link led to Google sign-in' \
  'offline comparison cannot prove'; do
  require_marker "$DOC" "$marker" "publication evidence documentation is missing limitation marker: $marker"
done

for workflow in "$CI_WORKFLOW" "$RELEASE_WORKFLOW"; do
  require_active_marker "$workflow" \
    'bash ./scripts/test-google-play-internal-app-sharing-publication-audit.sh' \
    "Android workflow must run the IAS publication adversarial contract: $workflow"
  require_active_marker "$workflow" \
    'bash ./scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only' \
    "Android workflow must audit the tracked IAS publication evidence: $workflow"
done
require_active_marker "$LOCAL_VALIDATOR" \
  'bash ./scripts/test-google-play-internal-app-sharing-publication-audit.sh' \
  "local validation must run the IAS publication adversarial contract"
require_active_marker "$LOCAL_VALIDATOR" \
  'bash ./scripts/audit-google-play-internal-app-sharing-publication.sh --manifest-only' \
  "local validation must audit the tracked IAS publication evidence"
require_marker "$RELEASE_PROCESS" \
  'audit-google-play-internal-app-sharing-publication.sh --manifest-only' \
  "release process must document the tracked IAS publication audit"
require_marker "$RELEASE_CHECKLIST" \
  'test-google-play-internal-app-sharing-publication-audit.sh' \
  "release checklist must require the IAS publication adversarial contract"

if [[ "$MODE" != "manifest-only" ]]; then
  require_regular_file "$HANDOFF" "ignored share-link handoff"
  handoff_bytes="$(file_bytes "$HANDOFF")"
  [[ "$handoff_bytes" =~ ^[0-9]+$ && "$handoff_bytes" -le 4096 ]] ||
    fail "share-link handoff exceeds 4 KiB"
  [[ "$(file_mode "$HANDOFF")" == "600" ]] ||
    fail "share-link handoff permissions must be exactly 600"
  git_root="$(git -C "$ROOT_DIR" rev-parse --show-toplevel 2>/dev/null)" ||
    fail "share-link handoff verification requires an Android Git worktree"
  [[ "$(cd -P -- "$git_root" && pwd)" == "$(cd -P -- "$ROOT_DIR" && pwd)" ]] ||
    fail "share-link handoff must belong to the Android repository root"
  handoff_relative="${HANDOFF#"$ROOT_DIR/"}"
  if git -C "$ROOT_DIR" ls-files --error-unmatch -- "$handoff_relative" >/dev/null 2>&1; then
    fail "share-link handoff must never be tracked by Git"
  fi
  git -C "$ROOT_DIR" check-ignore -q -- "$handoff_relative" ||
    fail "share-link handoff must remain ignored by Git"

  node - "$HANDOFF" "$MANIFEST" <<'NODE'
const crypto = require('node:crypto');
const fs = require('node:fs');

function fail(message) {
  console.error(`[google-play-ias-publication][android][error] ${message}`);
  process.exit(1);
}
function assert(condition, message) {
  if (!condition) fail(message);
}
function exactObject(value, keys, pathLabel) {
  assert(value !== null && typeof value === 'object' && !Array.isArray(value), `${pathLabel} must be an object`);
  assert(
    JSON.stringify(Object.keys(value).sort()) === JSON.stringify([...keys].sort()),
    `${pathLabel} has unexpected or missing keys`,
  );
}

const handoffRaw = fs.readFileSync(process.argv[2], 'utf8');
let handoff;
try {
  handoff = JSON.parse(handoffRaw);
} catch (error) {
  fail(`invalid share-link handoff JSON: ${error.message}`);
}
assert(handoffRaw === `${JSON.stringify(handoff, null, 2)}\n`, 'share-link handoff must be canonical two-space JSON');
exactObject(handoff, ['schemaVersion', 'shareUrl', 'shareUrlSha256', 'expiresDisplayLocal', 'timezone'], 'share-link handoff');
assert(handoff.schemaVersion === 1, 'share-link handoff schemaVersion must be 1');
assert(typeof handoff.shareUrl === 'string' && handoff.shareUrl.length >= 80 && handoff.shareUrl.length <= 512, 'share URL length is outside bounds');
assert(!/[\u0000-\u0020\u007f]/.test(handoff.shareUrl), 'share URL contains whitespace or a control character');

let parsed;
try {
  parsed = new URL(handoff.shareUrl);
} catch (error) {
  fail(`share URL is invalid: ${error.message}`);
}
assert(parsed.protocol === 'https:', 'share URL must use HTTPS');
assert(parsed.hostname === 'play.google.com', 'share URL host must be exactly play.google.com');
assert(parsed.port === '', 'share URL must not specify a port');
assert(parsed.username === '' && parsed.password === '', 'share URL must not contain userinfo');
assert(parsed.search === '', 'share URL must not contain a query');
assert(parsed.hash === '', 'share URL must not contain a fragment');
assert(parsed.origin === 'https://play.google.com', 'share URL origin drifted');
assert(parsed.href === handoff.shareUrl, 'share URL must use canonical serialization');

const manifest = JSON.parse(fs.readFileSync(process.argv[3], 'utf8'));
const match = /^\/apps\/test\/([0-9A-Za-z_-]+)\/([0-9A-Za-z_-]+)$/.exec(parsed.pathname);
assert(match, 'share URL path must have exactly two unpadded base64url tokens');
const uploadToken = match[1];
const installToken = match[2];
const uploadBounds = manifest.publication.uploadTokenValidatorLengthBounds;
const installBounds = manifest.publication.installTokenValidatorLengthBounds;
assert(uploadToken.length >= uploadBounds.minimum && uploadToken.length <= uploadBounds.maximum, 'share URL upload token length is outside bounds');
assert(installToken.length >= installBounds.minimum && installToken.length <= installBounds.maximum, 'share URL install token length is outside bounds');
assert(uploadToken.length === manifest.publication.observedUploadTokenLength, 'share URL upload token length does not match the observed link');
assert(installToken.length === manifest.publication.observedInstallTokenLength, 'share URL install token length does not match the observed link');

const digest = crypto.createHash('sha256').update(handoff.shareUrl, 'utf8').digest('hex');
assert(digest === handoff.shareUrlSha256, 'share-link handoff digest does not match its URL');
assert(digest === manifest.publication.shareUrlSha256, 'share-link handoff digest does not match the tracked manifest');
assert(handoff.shareUrlSha256 === '3f04ae9bf0efbd03d43b346945f07a5311b988de8500dec68f1a0263ebe1b59c', 'share URL digest drifted');
assert(handoff.expiresDisplayLocal === manifest.googleConsoleObservation.normalizedExpiryLocal, 'handoff expiry does not match the tracked manifest');
assert(handoff.timezone === 'not-displayed-by-google', 'handoff must not invent a timestamp timezone');
NODE
fi

if [[ "$MODE" == "full" ]]; then
  require_regular_file "$ARTIFACT" "selected Internal App Sharing AAB"
  artifact_bytes="$(file_bytes "$ARTIFACT")"
  [[ "$artifact_bytes" =~ ^[0-9]+$ && "$artifact_bytes" -le 536870912 ]] ||
    fail "selected Internal App Sharing AAB exceeds the 512 MiB audit bound"
  [[ "$artifact_bytes" -eq "$EXPECTED_ARTIFACT_BYTES" ]] ||
    fail "selected Internal App Sharing AAB byte count mismatch: expected $EXPECTED_ARTIFACT_BYTES, got $artifact_bytes"
  artifact_sha256="$(sha256_file "$ARTIFACT")"
  [[ "$artifact_sha256" == "$EXPECTED_ARTIFACT_SHA256" ]] ||
    fail "selected Internal App Sharing AAB digest mismatch: expected $EXPECTED_ARTIFACT_SHA256, got $artifact_sha256"
  python3 "$AAB_ALIGNMENT_VERIFIER" "$ARTIFACT" >/dev/null
fi

echo "[google-play-ias-publication][android] verified mode=$MODE expiry=$EXPIRY_WINDOW_RESULT manifest=$actual_manifest_sha256 url=$EXPECTED_SHARE_URL_SHA256 artifact=$EXPECTED_ARTIFACT_SHA256"
