#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PREFLIGHT="$ROOT_DIR/scripts/verify-android-distribution-preflight.sh"
if [[ -d /private/tmp && ! -L /private/tmp ]]; then
  tmp_dir="$(mktemp -d /private/tmp/android-distribution-preflight.XXXXXX)"
else
  tmp_dir="$(mktemp -d)"
fi
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[android-distribution-preflight-test][error] $*" >&2
  exit 1
}

negative_count=0
expect_failure() {
  local label="$1"
  shift
  negative_count=$((negative_count + 1))
  if "$@" >/dev/null 2>&1; then
    fail "$label was accepted"
  fi
}

fake_bin="$tmp_dir/bin"
mkdir -p "$fake_bin"
cat > "$fake_bin/keytool" <<'KEYTOOL'
#!/usr/bin/env bash
set -euo pipefail
case "${FAKE_KEYTOOL_MODE:-valid}" in
  valid)
    owner='CN=Fearless Upload, O=Fearless, C=JP'
    fingerprint='11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00'
    ;;
  debug)
    owner='CN=Android Debug, O=Android, C=US'
    fingerprint='11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00'
    ;;
  malformed-fingerprint)
    owner='CN=Fearless Upload, O=Fearless, C=JP'
    fingerprint='not-a-fingerprint'
    ;;
  failure)
    exit 1
    ;;
  *) exit 2 ;;
esac
cat <<OUTPUT
Alias name: upload
Owner: $owner
Certificate fingerprints:
         SHA256: $fingerprint
OUTPUT
KEYTOOL
chmod 700 "$fake_bin/keytool"

keystore="$tmp_dir/upload.jks"
printf '%s' 'fixture-keystore' > "$keystore"
chmod 600 "$keystore"

service_account="$tmp_dir/play-service-account.json"
cat > "$service_account" <<'JSON'
{
  "type": "service_account",
  "project_id": "fearless-release",
  "private_key_id": "0123456789abcdef",
  "private_key": "-----BEGIN PRIVATE KEY-----\nfixture\n-----END PRIVATE KEY-----\n",
  "client_email": "publisher@fearless-release.iam.gserviceaccount.com",
  "client_id": "123456789012345678901",
  "token_uri": "https://oauth2.googleapis.com/token"
}
JSON
chmod 600 "$service_account"

artifact_dir="$tmp_dir/release-artifact"
mkdir -p "$artifact_dir"
staged_bundle="$artifact_dir/Fearless-v4.2.0.aab"
printf '%s' 'signed-aab-fixture' > "$staged_bundle"
if command -v sha256sum >/dev/null 2>&1; then
  artifact_sha256="$(sha256sum "$staged_bundle" | awk '{print $1}')"
else
  artifact_sha256="$(shasum -a 256 "$staged_bundle" | awk '{print $1}')"
fi
printf '%s  %s\n' "$artifact_sha256" "$staged_bundle" > "$staged_bundle.sha256"
cat > "$artifact_dir/provenance.json" <<JSON
{
  "releaseTag": "v4.2.0",
  "releaseCommit": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
  "versionName": "4.2.0",
  "versionCode": "230",
  "versionPropertiesSha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
  "artifactSha256": "$artifact_sha256",
  "signingCertificateSha256": "11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00"
}
JSON

base_env=(
  "PATH=$fake_bin:/usr/bin:/bin"
  "CI_KEYSTORE_PATH=$keystore"
  "CI_KEYSTORE_PASS=store-password"
  "CI_KEYSTORE_KEY_ALIAS=upload"
  "CI_KEYSTORE_KEY_PASS=key-password"
  "CI_PLAY_KEY=$service_account"
  "PLAY_TRACK=internal"
  "PLAY_RELEASE_STATUS=draft"
  "PLAY_ARTIFACT_DIR=$artifact_dir"
  "RELEASE_TAG=v4.2.0"
  "RELEASE_COMMIT=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
  "RELEASE_VERSION_NAME=4.2.0"
  "RELEASE_VERSION_CODE=230"
  "VERSION_PROPERTIES_SHA=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  "RAMP_TOKEN_RELEASE=ramp-live-fixture"
  "COINBASE_APP_ID=coinbase-live-fixture"
  "MOONPAY_PRODUCTION_PUBLIC_KEY=pk_live_fixture"
  "X1_ENDPOINT_URL_RELEASE=https://x1.example.test/widget"
  "X1_WIDGET_ID_RELEASE=x1-live-fixture"
  "WEB_CLIENT_ID_RELEASE=1234567890-fixture.apps.googleusercontent.com"
  "FL_BLAST_API_ETHEREUM_KEY=blast-ethereum-fixture"
  "FL_BLAST_API_BSC_KEY=blast-bsc-fixture"
  "FL_BLAST_API_SEPOLIA_KEY=blast-sepolia-fixture"
  "FL_BLAST_API_GOERLI_KEY=blast-goerli-fixture"
  "FL_BLAST_API_POLYGON_KEY=blast-polygon-fixture"
  "FL_ANDROID_ETHERSCAN_API_KEY=etherscan-fixture"
  "FL_ANDROID_BSCSCAN_API_KEY=bscscan-fixture"
  "FL_ANDROID_POLYGONSCAN_API_KEY=polygonscan-fixture"
  "FL_ANDROID_OKLINK_API_KEY=oklink-fixture"
  "FL_ANDROID_OPMAINNET_API_KEY=opmainnet-fixture"
  "FL_WALLET_CONNECT_PROJECT_ID=wallet-connect-fixture"
  "FL_DWELLIR_API_KEY=dwellir-fixture"
  "FL_ANDROID_TON_API_KEY=ton-fixture"
  "FL_ANDROID_ALCHEMY_API_ETHEREUM_KEY=alchemy-fixture"
)

env "${base_env[@]}" "$PREFLIGHT" --release-signing >/dev/null ||
  fail "valid release-signing inputs were rejected"
env "${base_env[@]}" "$PREFLIGHT" --play-testing >/dev/null ||
  fail "valid draft/internal Play testing inputs were rejected"
env "${base_env[@]}" PLAY_TRACK=beta PLAY_RELEASE_STATUS=completed \
  "$PREFLIGHT" --play-testing >/dev/null ||
  fail "valid completed/beta Play testing inputs were rejected"

expect_failure "missing mode" env "${base_env[@]}" "$PREFLIGHT"
expect_failure "unknown mode" env "${base_env[@]}" "$PREFLIGHT" --production
expect_failure "extra argument" env "${base_env[@]}" "$PREFLIGHT" --release-signing extra

for variable in CI_KEYSTORE_PATH CI_KEYSTORE_PASS CI_KEYSTORE_KEY_ALIAS CI_KEYSTORE_KEY_PASS; do
  expect_failure "missing $variable" env "${base_env[@]}" "$variable=" \
    "$PREFLIGHT" --release-signing
done

for variable in \
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
  expect_failure "missing release runtime value $variable" env "${base_env[@]}" "$variable=" \
    "$PREFLIGHT" --release-signing
done

expect_failure "placeholder release runtime value" env "${base_env[@]}" COINBASE_APP_ID=stub \
  "$PREFLIGHT" --release-signing
expect_failure "embedded placeholder release runtime value" env "${base_env[@]}" \
  COINBASE_APP_ID=pk-live-stub-value "$PREFLIGHT" --release-signing
expect_failure "control-bearing release runtime value" env "${base_env[@]}" \
  RAMP_TOKEN_RELEASE=$'ramp\nvalue' "$PREFLIGHT" --release-signing
expect_failure "whitespace-bearing release runtime value" env "${base_env[@]}" \
  RAMP_TOKEN_RELEASE='ramp value' "$PREFLIGHT" --release-signing
oversized_runtime_value="$(awk 'BEGIN { for (i = 0; i < 8193; i++) printf "a" }')"
expect_failure "oversized release runtime value" env "${base_env[@]}" \
  FL_ANDROID_TON_API_KEY="$oversized_runtime_value" "$PREFLIGHT" --release-signing
expect_failure "non-HTTPS X1 endpoint" env "${base_env[@]}" \
  X1_ENDPOINT_URL_RELEASE=http://x1.example.test/widget "$PREFLIGHT" --release-signing
expect_failure "malformed Google OAuth client" env "${base_env[@]}" \
  WEB_CLIENT_ID_RELEASE=attacker.example.test "$PREFLIGHT" --release-signing
expect_failure "non-HTTPS optional TON indexer" env "${base_env[@]}" \
  FL_ANDROID_TON_INDEXER_URL=http://ti.example.test "$PREFLIGHT" --release-signing

empty_keystore="$tmp_dir/empty.jks"
: > "$empty_keystore"
chmod 600 "$empty_keystore"
expect_failure "empty keystore" env "${base_env[@]}" CI_KEYSTORE_PATH="$empty_keystore" \
  "$PREFLIGHT" --release-signing

keystore_link="$tmp_dir/upload-link.jks"
ln -s "$keystore" "$keystore_link"
expect_failure "symlink keystore" env "${base_env[@]}" CI_KEYSTORE_PATH="$keystore_link" \
  "$PREFLIGHT" --release-signing

chmod 644 "$keystore"
expect_failure "world-readable keystore" env "${base_env[@]}" "$PREFLIGHT" --release-signing
chmod 600 "$keystore"

expect_failure "debug signing certificate" env "${base_env[@]}" FAKE_KEYTOOL_MODE=debug \
  "$PREFLIGHT" --release-signing
expect_failure "malformed signing fingerprint" env "${base_env[@]}" FAKE_KEYTOOL_MODE=malformed-fingerprint \
  "$PREFLIGHT" --release-signing
expect_failure "unreadable signing alias" env "${base_env[@]}" FAKE_KEYTOOL_MODE=failure \
  "$PREFLIGHT" --release-signing

for track in '' none production Internal 'beta;production'; do
  expect_failure "invalid testing track '$track'" env "${base_env[@]}" PLAY_TRACK="$track" \
    "$PREFLIGHT" --play-testing
done
for status in '' inProgress halted COMPLETED 'completed;draft'; do
  expect_failure "invalid testing status '$status'" env "${base_env[@]}" PLAY_RELEASE_STATUS="$status" \
    "$PREFLIGHT" --play-testing
done

empty_service_account="$tmp_dir/empty-service-account.json"
: > "$empty_service_account"
chmod 600 "$empty_service_account"
expect_failure "empty service account" env "${base_env[@]}" CI_PLAY_KEY="$empty_service_account" \
  "$PREFLIGHT" --play-testing

service_account_link="$tmp_dir/play-link.json"
ln -s "$service_account" "$service_account_link"
expect_failure "symlink service account" env "${base_env[@]}" CI_PLAY_KEY="$service_account_link" \
  "$PREFLIGHT" --play-testing

chmod 640 "$service_account"
expect_failure "group-readable service account" env "${base_env[@]}" "$PREFLIGHT" --play-testing
chmod 600 "$service_account"

for field in type project_id private_key_id private_key client_email client_id token_uri; do
  invalid_json="$tmp_dir/invalid-$field.json"
  jq --arg field "$field" 'del(.[$field])' "$service_account" > "$invalid_json"
  chmod 600 "$invalid_json"
  expect_failure "service account missing $field" env "${base_env[@]}" CI_PLAY_KEY="$invalid_json" \
    "$PREFLIGHT" --play-testing
done

wrong_token="$tmp_dir/wrong-token.json"
jq '.token_uri = "https://attacker.invalid/token"' "$service_account" > "$wrong_token"
chmod 600 "$wrong_token"
expect_failure "attacker token endpoint" env "${base_env[@]}" CI_PLAY_KEY="$wrong_token" \
  "$PREFLIGHT" --play-testing

wrong_email="$tmp_dir/wrong-email.json"
jq '.client_email = "publisher@example.com"' "$service_account" > "$wrong_email"
chmod 600 "$wrong_email"
expect_failure "non-service-account email" env "${base_env[@]}" CI_PLAY_KEY="$wrong_email" \
  "$PREFLIGHT" --play-testing

oversized_service_account="$tmp_dir/oversized.json"
dd if=/dev/zero of="$oversized_service_account" bs=65537 count=1 2>/dev/null
chmod 600 "$oversized_service_account"
expect_failure "oversized service account" env "${base_env[@]}" CI_PLAY_KEY="$oversized_service_account" \
  "$PREFLIGHT" --play-testing

expect_failure "missing staged artifact directory" env "${base_env[@]}" PLAY_ARTIFACT_DIR= \
  "$PREFLIGHT" --play-testing

multiple_artifacts="$tmp_dir/multiple-artifacts"
mkdir -p "$multiple_artifacts"
cp "$staged_bundle" "$multiple_artifacts/one.aab"
cp "$staged_bundle" "$multiple_artifacts/two.aab"
expect_failure "ambiguous staged artifacts" env "${base_env[@]}" PLAY_ARTIFACT_DIR="$multiple_artifacts" \
  "$PREFLIGHT" --play-testing

tampered_artifact_dir="$tmp_dir/tampered-artifact"
cp -R "$artifact_dir" "$tampered_artifact_dir"
printf '%s' 'tamper' >> "$tampered_artifact_dir/Fearless-v4.2.0.aab"
expect_failure "staged artifact digest drift" env "${base_env[@]}" PLAY_ARTIFACT_DIR="$tampered_artifact_dir" \
  "$PREFLIGHT" --play-testing

tampered_provenance_dir="$tmp_dir/tampered-provenance"
cp -R "$artifact_dir" "$tampered_provenance_dir"
jq '.signingCertificateSha256 = "00:00"' "$artifact_dir/provenance.json" > "$tampered_provenance_dir/provenance.json"
expect_failure "staged provenance signer drift" env "${base_env[@]}" PLAY_ARTIFACT_DIR="$tampered_provenance_dir" \
  "$PREFLIGHT" --play-testing

expect_failure "release tag/provenance mismatch" env "${base_env[@]}" RELEASE_TAG=v4.2.1 \
  "$PREFLIGHT" --play-testing

artifact_symlink_dir="$tmp_dir/artifact-symlink"
mkdir -p "$artifact_symlink_dir"
ln -s "$staged_bundle" "$artifact_symlink_dir/Fearless-v4.2.0.aab"
cp "$staged_bundle.sha256" "$artifact_symlink_dir/Fearless-v4.2.0.aab.sha256"
cp "$artifact_dir/provenance.json" "$artifact_symlink_dir/provenance.json"
expect_failure "symlink staged artifact" env "${base_env[@]}" PLAY_ARTIFACT_DIR="$artifact_symlink_dir" \
  "$PREFLIGHT" --play-testing

echo "[android-distribution-preflight-test] passed (3 valid contracts plus $negative_count negative scenarios)"
