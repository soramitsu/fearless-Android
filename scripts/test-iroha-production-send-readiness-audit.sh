#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${IROHA_SEND_AUDIT_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "$0")/.." && pwd))}"
AUDIT="$ROOT_DIR/scripts/audit-iroha-production-send-readiness.sh"
TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/fearless-android-iroha-send.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

fail() {
  echo "[iroha-send-readiness-test][android][error] $*" >&2
  exit 1
}

new_fixture() {
  local name="$1"
  local fixture="$TMP_DIR/$name"
  mkdir -p \
    "$fixture/.github/workflows" \
    "$fixture/config" \
    "$fixture/docs" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/model" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/di/modules" \
    "$fixture/common/src/test/java/jp/co/soramitsu/common/wallet" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/historySource" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser" \
    "$fixture/runtime/src/main/java/jp/co/soramitsu/runtime/ext" \
    "$fixture/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model" \
    "$fixture/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers" \
    "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge" \
    "$fixture/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge" \
    "$fixture/iroha-sdk-bridge/src/test/resources" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke/src/test/kotlin/jp/co/soramitsu/iroha/bridge" \
    "$fixture/scripts"
  cp "$ROOT_DIR/config/iroha-production-send-readiness.json" "$fixture/config/"
  cp "$ROOT_DIR/docs/iroha-production-send-readiness.md" "$fixture/docs/"
  cp "$ROOT_DIR/docs/universal-wallet-v2.md" "$fixture/docs/"
  cp "$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/model/UniversalWalletRegistry.kt" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/model/"
  cp "$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/"
  cp "$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/"
  cp "$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiRoutes.kt" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/"
  cp "$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/di/modules/NetworkModule.kt" \
    "$fixture/common/src/main/java/jp/co/soramitsu/common/di/modules/"
  cp "$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaToriiClientTest.kt" \
    "$fixture/common/src/test/java/jp/co/soramitsu/common/wallet/"
  cp "$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaToriiRoutesTest.kt" \
    "$fixture/common/src/test/java/jp/co/soramitsu/common/wallet/"
  cp "$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaAssetDefinitionSpecJsonTest.kt" \
    "$fixture/common/src/test/java/jp/co/soramitsu/common/wallet/"
  cp "$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaNetworkModuleTest.kt" \
    "$fixture/common/src/test/java/jp/co/soramitsu/common/wallet/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySourceTest.kt" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/historySource/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoader.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoaderTest.kt" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/BalanceLoaderProvider.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/"
  cp "$ROOT_DIR/runtime/src/main/java/jp/co/soramitsu/runtime/ext/UniversalWalletIrohaExt.kt" \
    "$fixture/runtime/src/main/java/jp/co/soramitsu/runtime/ext/"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/UniversalWalletIrohaRoutingTest.kt" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/"
  cp "$ROOT_DIR/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/MetaAccount.kt" \
    "$fixture/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/"
  cp "$ROOT_DIR/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/AndroidUniversalWalletMigrationSnapshotBuilder.kt" \
    "$fixture/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/"
  cp "$ROOT_DIR/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/LightMetaAccountUniversalWalletTest.kt" \
    "$fixture/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/"
  cp "$ROOT_DIR/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/AndroidUniversalWalletMigrationSnapshotBuilderTest.kt" \
    "$fixture/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/"
  cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/"
  cp "$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadataTest.kt" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/"
  cp "$ROOT_DIR/settings.gradle" "$fixture/"
  cp "$ROOT_DIR/build.gradle" "$fixture/"
  cp "$ROOT_DIR/.github/workflows/android-ci.yml" "$fixture/.github/workflows/"
  cp "$ROOT_DIR/iroha-sdk-bridge/build.gradle" "$fixture/iroha-sdk-bridge/"
  cp "$ROOT_DIR/iroha-sdk-bridge/gradle.lockfile" "$fixture/iroha-sdk-bridge/"
  cp "$ROOT_DIR/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java" \
    "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/"
  cp "$ROOT_DIR/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferRequest.java" \
    "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/"
  cp "$ROOT_DIR/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridgeTest.java" \
    "$fixture/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge/"
  cp "$ROOT_DIR/iroha-sdk-bridge/src/test/resources/iroha-compact-hash-vector.properties" \
    "$fixture/iroha-sdk-bridge/src/test/resources/"
  cp "$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/build.gradle" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke/"
  cp "$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/gradle.lockfile" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke/"
  cp "$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/src/test/kotlin/jp/co/soramitsu/iroha/bridge/IrohaBridgeKotlinIsolationTest.kt" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke/src/test/kotlin/jp/co/soramitsu/iroha/bridge/"
  cp "$ROOT_DIR/scripts/validate-local.sh" "$fixture/scripts/"
  cp "$ROOT_DIR/scripts/materialize-iroha-core-jvm.sh" "$fixture/scripts/"
  cp "$ROOT_DIR/scripts/test-iroha-core-jvm-materializer.sh" "$fixture/scripts/"
  cp "$ROOT_DIR/scripts/verify-staged-iroha-core-bridge.sh" "$fixture/scripts/"
  cp "$ROOT_DIR/scripts/verify-iroha-compact-hash-vector.py" "$fixture/scripts/"
  (
    cd "$fixture"
    git init -q
    git add .
  )
  printf '%s' "$fixture"
}

run_audit() {
  IROHA_SEND_AUDIT_ROOT="$1" bash "$AUDIT"
}

expect_failure() {
  local label="$1"
  local fixture="$2"
  local expected="$3"
  local output status
  set +e
  output="$(run_audit "$fixture" 2>&1)"
  status=$?
  set -e
  if [[ "$status" -eq 0 ]]; then
    fail "$label unexpectedly passed"
  fi
  [[ "$output" == *"$expected"* ]] || {
    printf '%s\n' "$output" >&2
    fail "$label did not report expected marker: $expected"
  }
}

valid="$(new_fixture valid)"
run_audit "$valid" >/dev/null

fixture="$(new_fixture manifest-tamper)"
sed -i.bak 's/"status": "blocked"/"status": "ready"/' "$fixture/config/iroha-production-send-readiness.json"
rm -f "$fixture/config/iroha-production-send-readiness.json.bak"
expect_failure "manifest tamper" "$fixture" "manifest digest mismatch"

fixture="$(new_fixture manifest-symlink)"
rm "$fixture/config/iroha-production-send-readiness.json"
ln -s ../docs/iroha-production-send-readiness.md "$fixture/config/iroha-production-send-readiness.json"
expect_failure "manifest symlink" "$fixture" "missing or is a symlink"

fixture="$(new_fixture provider-bypass)"
sed -i.bak 's/IrohaTransferSigner = UnavailableIrohaTransferSigner/IrohaTransferSigner = ReviewedSigner/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "provider bypass" "$fixture" "fail-closed provider default"

fixture="$(new_fixture production-di-injection)"
perl -0pi -e \
  's/            solanaBalanceSync,\n            irohaToriiClient\n        \)/            solanaBalanceSync,\n            irohaToriiClient,\n            ReviewedIrohaTransferSigner\n        )/g' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "production DI signer injection" "$fixture" "production DI construction must use the audited unavailable-signer default"

fixture="$(new_fixture signer-no-longer-fails)"
sed -i.bak 's/Iroha transfer signing codec is unavailable/temporary fallback/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "signer no longer fails explicitly" "$fixture" "unavailable signer exception"

fixture="$(new_fixture fee-estimation-fail-open)"
sed -i.bak 's/throw IrohaTransferFeeException(/throw IllegalStateException(/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "fee estimation fail open" "$fixture" "typed fee-unavailable fail-closed behavior"

fixture="$(new_fixture nexus-enabled)"
node - "$fixture/common/src/main/java/jp/co/soramitsu/common/model/UniversalWalletRegistry.kt" <<'NODE'
const fs = require('node:fs');
const path = process.argv[2];
const text = fs.readFileSync(path, 'utf8');
const start = text.indexOf('val nexus = IrohaNetwork(');
const end = text.indexOf('\n    )', start);
fs.writeFileSync(path, text.slice(0, start) + text.slice(start, end).replace('enabledByDefault = false', 'enabledByDefault = true') + text.slice(end));
NODE
expect_failure "Nexus enabled" "$fixture" "Nexus registry default"

fixture="$(new_fixture sdk-dependency)"
printf 'dependencies { implementation("org.hyperledger.iroha.sdk:client-android:0.1") }\n' > "$fixture/feature-wallet-impl/iroha.gradle"
expect_failure "untracked production SDK dependency" "$fixture" "leaked outside the two audited staging modules"

fixture="$(new_fixture production-project-dependency)"
printf "dependencies { implementation project(':iroha-sdk-bridge') }\n" > \
  "$fixture/feature-wallet-impl/iroha.gradle"
expect_failure "untracked production bridge project dependency" "$fixture" \
  "leaked outside the two audited staging modules"

fixture="$(new_fixture tracked-native)"
mkdir -p "$fixture/vendor"
printf 'native\n' > "$fixture/vendor/libconnect_norito_bridge.so"
git -C "$fixture" add .
expect_failure "tracked native bridge" "$fixture" "SDK/native binary material"

fixture="$(new_fixture defective-hasher-import)"
sed -i.bak '/import org.hyperledger.iroha.sdk.tx.SignedTransaction;/a\
import org.hyperledger.iroha.sdk.tx.SignedTransactionHasher;' \
  "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java"
rm -f "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java.bak"
expect_failure "production bridge imports defective SDK hasher" "$fixture" \
  "must not import the pinned defective SignedTransactionHasher"

fixture="$(new_fixture compact-boundary-test-removed)"
sed -i.bak \
  's/compactLengthEncodingIsMinimalAtEveryBoundaryAndRejectsOverlongInputs/compactLengthSmoke/' \
  "$fixture/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridgeTest.java"
rm -f "$fixture/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridgeTest.java.bak"
expect_failure "compact boundary and overlong test removed" "$fixture" \
  "bridge adversarial test marker"

fixture="$(new_fixture hash-vector-tamper)"
sed -i.bak 's/canonical.hash=2332/canonical.hash=0332/' \
  "$fixture/iroha-sdk-bridge/src/test/resources/iroha-compact-hash-vector.properties"
rm -f "$fixture/iroha-sdk-bridge/src/test/resources/iroha-compact-hash-vector.properties.bak"
expect_failure "compact hash vector tamper" "$fixture" "compact hash vector digest mismatch"

fixture="$(new_fixture hash-verifier-gate-removed)"
sed -i.bak '/verify-iroha-compact-hash-vector.py/d' \
  "$fixture/scripts/verify-staged-iroha-core-bridge.sh"
rm -f "$fixture/scripts/verify-staged-iroha-core-bridge.sh.bak"
expect_failure "independent hash verifier gate removed" "$fixture" \
  "independent compact hash verifier gate"

fixture="$(new_fixture app-runtime-boundary-removed)"
sed -i.bak 's/verifyStagedIrohaSdkExcluded/verifyOnlyDebugMetadata/' \
  "$fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle"
rm -f "$fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle.bak"
expect_failure "app runtime boundary removed" "$fixture" "Kotlin smoke invariant"

fixture="$(new_fixture ci-staged-gate-removed)"
sed -i.bak '/verify-staged-iroha-core-bridge.sh/d' "$fixture/.github/workflows/android-ci.yml"
rm -f "$fixture/.github/workflows/android-ci.yml.bak"
expect_failure "CI staged bridge gate removed" "$fixture" "Android CI staged bridge gate"

fixture="$(new_fixture fail-closed-test-removed)"
sed -i.bak 's/provider routes iroha chains to fail closed iroha transfer service/provider routes iroha/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "fail-closed test removed" "$fixture" "default signer fail-closed test"

fixture="$(new_fixture mismatch-test-removed)"
sed -i.bak 's/iroha transfer rejects mnemonic mismatch before signer or torii calls/iroha mismatch/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "mismatch test removed" "$fixture" "mnemonic/key mismatch adversarial test"

fixture="$(new_fixture transfer-hash-normalization-restored)"
sed -i.bak \
  's/return this?.takeIf(IROHA_TRANSACTION_HASH_PATTERN::matches)/return this?.trim()?.lowercase()/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "transfer hash normalization restored" "$fixture" "MCP submit-and-wait invariant"

fixture="$(new_fixture canonical-transfer-hash-test-removed)"
sed -i.bak \
  's/iroha transfer rejects noncanonical hash spellings without normalization/iroha hash spellings/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "canonical transfer hash test removed" "$fixture" "canonical Iroha transfer hash adversarial test"

fixture="$(new_fixture wallet-smoke-key-contract-removed)"
sed -i.bak 's/route_governance_action_hash/route_action_hash/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt.bak"
expect_failure "wallet-smoke key contract removed" "$fixture" "wallet-smoke metadata invariant"

fixture="$(new_fixture wallet-smoke-negative-test-removed)"
sed -i.bak \
  's/rejects malformed wallet smoke metadata before signer or torii calls/rejects wallet smoke metadata/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadataTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadataTest.kt.bak"
expect_failure "wallet-smoke negative test removed" "$fixture" "wallet-smoke metadata test marker"

fixture="$(new_fixture taira-nexus-metadata-rejection-removed)"
sed -i.bak \
  's/wallet-smoke transaction metadata is Nexus-only; staged Taira bridge requires empty metadata/non-empty transaction metadata accepted/' \
  "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java"
rm -f "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java.bak"
expect_failure "Taira Nexus metadata rejection removed" "$fixture" "bridge source invariant"

fixture="$(new_fixture wallet-smoke-nexus-context-removed)"
sed -i.bak 's/const val NEXUS_NETWORK = "nexus"/const val NEXUS_NETWORK = "taira"/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt.bak"
expect_failure "wallet-smoke Nexus context removed" "$fixture" "wallet-smoke metadata invariant"

fixture="$(new_fixture wallet-smoke-minamoto-test-removed)"
sed -i.bak \
  's/wallet smoke evidence rejects taira and noncanonical minamoto before signer or torii/wallet smoke evidence endpoint test/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "wallet-smoke Minamoto test removed" "$fixture" "Nexus wallet-smoke service test marker"

fixture="$(new_fixture wallet-smoke-pre-context-proof-removed)"
sed -i.bak \
  's/verifyNoInteractions(accountRepository)/assertEquals(null, signer.lastRequest)/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "wallet-smoke pre-context proof removed" "$fixture" "Nexus wallet-smoke service test marker"

fixture="$(new_fixture wallet-smoke-preflight-network-bypassed)"
sed -i.bak \
  's/evidenceNetwork != UniversalWalletRegistry.nexus/false/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "wallet-smoke preflight network bypassed" "$fixture" "Nexus wallet-smoke service invariant"

fixture="$(new_fixture wallet-smoke-preflight-endpoint-bypassed)"
sed -i.bak \
  's/evidenceToriiBaseUrl != canonicalMinamoto/false/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "wallet-smoke preflight endpoint bypassed" "$fixture" "Nexus wallet-smoke service invariant"

fixture="$(new_fixture wallet-smoke-taira-zero-secret-proof-removed)"
sed -i.bak \
  's/verifyNoInteractions(tairaAccountRepository)/assertEquals(null, tairaSigner.lastRequest)/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "wallet-smoke Taira zero-secret proof removed" "$fixture" "Nexus wallet-smoke service test marker"

fixture="$(new_fixture wallet-smoke-endpoint-zero-secret-proof-removed)"
sed -i.bak \
  's/verifyNoInteractions(nexusAccountRepository)/assertEquals(null, nexusSigner.lastRequest)/' \
  "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
rm -f "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt.bak"
expect_failure "wallet-smoke endpoint zero-secret proof removed" "$fixture" "Nexus wallet-smoke service test marker"

fixture="$(new_fixture codec-metadata-defensive-copy-removed)"
sed -i.bak \
  's/Collections.unmodifiableMap(new LinkedHashMap<>(transactionMetadata))/transactionMetadata/' \
  "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferRequest.java"
rm -f "$fixture/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferRequest.java.bak"
expect_failure "codec metadata defensive copy removed" "$fixture" "bridge request metadata invariant"

fixture="$(new_fixture evidence-redacted)"
sed -i.bak 's/116231129/unknown/g' "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "release evidence redacted" "$fixture" "pinned blocker evidence marker"

fixture="$(new_fixture live-registry-evidence-redacted)"
sed -i.bak 's/6TEAJqbb8oEPmLncoNiMRbLEK6tw/live-id-redacted/g' \
  "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "live registry evidence redacted" "$fixture" "pinned blocker evidence marker"

fixture="$(new_fixture legacy-alias-accepted-claim)"
sed -i.bak 's/accept no `name#domain`/accept `name#domain`/' \
  "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "legacy alias accepted claim" "$fixture" "pinned blocker evidence marker"

fixture="$(new_fixture fee-estimate-restored-to-zero)"
sed -i.bak \
  's/throw IrohaTransferFeeException(/return BigDecimal.ZERO.also {/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt.bak"
expect_failure "fee estimate restored to zero" "$fixture" "typed fee-unavailable fail-closed behavior"

fixture="$(new_fixture fanout-denied-header-removed)"
sed -i.bak 's/x-iroha-fanout-routes-denied/x-iroha-fanout-routes-denied-disabled/g' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "denied fanout header removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture submit-hash-match-removed)"
sed -i.bak \
  's/listOf(hash, transactionHash, receiptHash, finalHash).any { it != expectedHash }/false/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "submit-and-wait hash match removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture json-rpc-request-binding-removed)"
sed -i.bak 's/response.id != request.id/false/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "JSON-RPC request binding removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture missing-json-rpc-version-hidden)"
sed -i.bak 's/val jsonrpc: String? = null/val jsonrpc: String = "2.0"/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt.bak"
expect_failure "missing JSON-RPC version hidden" "$fixture" "missing JSON-RPC version must remain observable"

fixture="$(new_fixture submit-hash-normalization-restored)"
sed -i.bak 's/return value?.takeIf(CANONICAL_TRANSACTION_HASH::matches)/return value?.trim()?.lowercase()/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "submit hash normalization restored" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture status-hash-normalization-restored)"
sed -i.bak 's/if (!HASH_256.matches(hash))/if (!HASH_256.matches(hash.lowercase()))/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiRoutes.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiRoutes.kt.bak"
expect_failure "status hash normalization restored" "$fixture" "canonical transaction-status hash routing"

fixture="$(new_fixture submit-applied-finality-removed)"
sed -i.bak \
  's/finalKind != IrohaPipelineTransactionStatusKind.Applied/false/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "submit-and-wait Applied finality removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture sdk-node-compatibility-promoted)"
sed -i.bak 's/"sdkDeployedNodeCompatibility": "not-proven"/"sdkDeployedNodeCompatibility": "proven"/' \
  "$fixture/config/iroha-production-send-readiness.json"
rm -f "$fixture/config/iroha-production-send-readiness.json.bak"
expect_failure "SDK deployed-node compatibility promoted" "$fixture" "manifest digest mismatch"

fixture="$(new_fixture stale-pre-review-doc)"
printf '\nLegacy blocker: android_sdk_archive_review_required (not materialized).\n' >> \
  "$fixture/docs/iroha-production-send-readiness.md"
expect_failure "stale pre-review documentation" "$fixture" "stale pre-review statement"

fixture="$(new_fixture capability-claim-regressed)"
sed -i.bak 's/is capability metadata, not a production-send/is production-send/' "$fixture/docs/universal-wallet-v2.md"
rm -f "$fixture/docs/universal-wallet-v2.md.bak"
expect_failure "capability claim regressed" "$fixture" "Universal Wallet non-enablement statement"

fixture="$(new_fixture fanout-overflow-guard-removed)"
sed -i.bak 's/unavailable > failed - denied/unavailable > failed/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt.bak"
expect_failure "fanout overflow guard removed" "$fixture" "overflow-safe fanout invariant"

fixture="$(new_fixture history-account-substring-fallback-restored)"
sed -i.bak \
  's/val outgoing = sourceAccount == accountAddress/val outgoing = source?.contains(accountAddress) == true/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt.bak"
expect_failure "history account substring fallback restored" "$fixture" "exact Iroha history account binding"

fixture="$(new_fixture noncommitted-history-accepted)"
sed -i.bak \
  's/if (transactionStatus != "Committed")/if (transactionStatus == "Rejected")/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt.bak"
expect_failure "noncommitted history accepted" "$fixture" "exact committed Iroha history status"

fixture="$(new_fixture iroha-redirects-restored)"
sed -i.bak 's/\.followRedirects(false)/.followRedirects(true)/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/di/modules/NetworkModule.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/di/modules/NetworkModule.kt.bak"
expect_failure "Iroha redirects restored" "$fixture" "Iroha no-redirect transport invariant"

fixture="$(new_fixture outer-json-media-type-check-removed)"
sed -i.bak 's/requireJsonContentType(response)/acceptAnySuccessfulBody(response)/g' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "outer JSON media-type check removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture nested-header-collision-check-removed)"
sed -i.bak 's/duplicate case-insensitive headers/ignored case-insensitive headers/' \
  "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
rm -f "$fixture/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt.bak"
expect_failure "nested header collision check removed" "$fixture" "Torii strict read/write invariant"

fixture="$(new_fixture duplicate-asset-scope-check-removed)"
sed -i.bak 's/seenAccountAssetScopes.add(canonicalItemAsset to canonicalScope)/true/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoader.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoader.kt.bak"
expect_failure "duplicate account-asset scope check removed" "$fixture" "strict Iroha balance invariant"

fixture="$(new_fixture iroha-registry-alias-routing-restored)"
sed -i.bak 's/return id == UniversalWalletRegistry.taira.chainId ||/return id == UniversalWalletRegistry.taira.id ||/' \
  "$fixture/runtime/src/main/java/jp/co/soramitsu/runtime/ext/UniversalWalletIrohaExt.kt"
rm -f "$fixture/runtime/src/main/java/jp/co/soramitsu/runtime/ext/UniversalWalletIrohaExt.kt.bak"
expect_failure "Iroha registry alias routing restored" "$fixture" "canonical Iroha identity invariant"

fixture="$(new_fixture noncanonical-iroha-balance-fallthrough-restored)"
sed -i.bak 's/chain.hasNonCanonicalUniversalWalletIrohaIdentity() -> throw IllegalArgumentException(/false -> throw IllegalArgumentException(/' \
  "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/BalanceLoaderProvider.kt"
rm -f "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/BalanceLoaderProvider.kt.bak"
expect_failure "noncanonical Iroha balance fallthrough restored" "$fixture" "noncanonical Iroha balance-route refusal"

fixture="$(new_fixture iroha-migration-registry-alias-restored)"
printf '\nval retiredIrohaIdentity = UniversalWalletRegistry.taira.id\n' >> \
  "$fixture/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/AndroidUniversalWalletMigrationSnapshotBuilder.kt"
expect_failure "Iroha migration registry alias restored" "$fixture" "Iroha registry aliases must not be stored-account or migration identities"

echo "[iroha-send-readiness-test][android] all adversarial fixtures passed."
