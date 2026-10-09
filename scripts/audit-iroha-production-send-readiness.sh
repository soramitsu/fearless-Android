#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${IROHA_SEND_AUDIT_ROOT:-$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "$0")/.." && pwd))}"
MANIFEST="$ROOT_DIR/config/iroha-production-send-readiness.json"
DOC="$ROOT_DIR/docs/iroha-production-send-readiness.md"
UNIVERSAL_DOC="$ROOT_DIR/docs/universal-wallet-v2.md"
TRANSFER_SERVICE="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/TransferService.kt"
TRANSFER_TEST="$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/BitcoinTransferServiceProviderTest.kt"
TORII_CLIENT="$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiClient.kt"
TORII_MODELS="$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiModels.kt"
TORII_ROUTES="$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/data/network/iroha/IrohaToriiRoutes.kt"
TORII_TEST="$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaToriiClientTest.kt"
TORII_ROUTES_TEST="$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaToriiRoutesTest.kt"
TORII_MODEL_TEST="$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaAssetDefinitionSpecJsonTest.kt"
HISTORY_SOURCE="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySource.kt"
HISTORY_TEST="$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/historySource/IrohaHistorySourceTest.kt"
BALANCE_SOURCE="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoader.kt"
BALANCE_TEST="$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/IrohaBalanceLoaderTest.kt"
BALANCE_PROVIDER="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/network/blockchain/balance/BalanceLoaderProvider.kt"
IROHA_IDENTITY="$ROOT_DIR/runtime/src/main/java/jp/co/soramitsu/runtime/ext/UniversalWalletIrohaExt.kt"
IROHA_ROUTING_TEST="$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/UniversalWalletIrohaRoutingTest.kt"
META_ACCOUNT="$ROOT_DIR/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/MetaAccount.kt"
ACCOUNT_TEST="$ROOT_DIR/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/LightMetaAccountUniversalWalletTest.kt"
MIGRATION_SOURCE="$ROOT_DIR/feature-account-api/src/main/java/jp/co/soramitsu/account/api/domain/model/AndroidUniversalWalletMigrationSnapshotBuilder.kt"
MIGRATION_TEST="$ROOT_DIR/feature-account-impl/src/test/java/jp/co/soramitsu/account/impl/data/mappers/AndroidUniversalWalletMigrationSnapshotBuilderTest.kt"
NETWORK_MODULE="$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/di/modules/NetworkModule.kt"
NETWORK_TEST="$ROOT_DIR/common/src/test/java/jp/co/soramitsu/common/wallet/IrohaNetworkModuleTest.kt"
METADATA_SOURCE="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadata.kt"
METADATA_TEST="$ROOT_DIR/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser/IrohaTransferMetadataTest.kt"
DI_MODULE="$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
REGISTRY="$ROOT_DIR/common/src/main/java/jp/co/soramitsu/common/model/UniversalWalletRegistry.kt"
SETTINGS="$ROOT_DIR/settings.gradle"
ROOT_BUILD="$ROOT_DIR/build.gradle"
CI_WORKFLOW="$ROOT_DIR/.github/workflows/android-ci.yml"
LOCAL_VALIDATOR="$ROOT_DIR/scripts/validate-local.sh"
BRIDGE_BUILD="$ROOT_DIR/iroha-sdk-bridge/build.gradle"
BRIDGE_LOCK="$ROOT_DIR/iroha-sdk-bridge/gradle.lockfile"
BRIDGE_SOURCE="$ROOT_DIR/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridge.java"
BRIDGE_REQUEST="$ROOT_DIR/iroha-sdk-bridge/src/main/java/jp/co/soramitsu/iroha/bridge/IrohaTransferRequest.java"
BRIDGE_TEST="$ROOT_DIR/iroha-sdk-bridge/src/test/java/jp/co/soramitsu/iroha/bridge/IrohaTransferBridgeTest.java"
HASH_VECTOR="$ROOT_DIR/iroha-sdk-bridge/src/test/resources/iroha-compact-hash-vector.properties"
SMOKE_BUILD="$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/build.gradle"
SMOKE_LOCK="$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/gradle.lockfile"
SMOKE_TEST="$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/src/test/kotlin/jp/co/soramitsu/iroha/bridge/IrohaBridgeKotlinIsolationTest.kt"
MATERIALIZER="$ROOT_DIR/scripts/materialize-iroha-core-jvm.sh"
MATERIALIZER_TEST="$ROOT_DIR/scripts/test-iroha-core-jvm-materializer.sh"
BRIDGE_GATE="$ROOT_DIR/scripts/verify-staged-iroha-core-bridge.sh"
HASH_VECTOR_VERIFIER="$ROOT_DIR/scripts/verify-iroha-compact-hash-vector.py"
EXPECTED_MANIFEST_SHA256="921e5a7bb864a69ad518be23a210f2209eabbdd156e96884ebbd8aba54062fde"
EXPECTED_VECTOR_SHA256="63e75a1183fe42763353c950d53ab159a9f8d37a67b7b86177e71bcc6c6e0c20"
TMP_MATCHES="$(mktemp "${TMPDIR:-/tmp}/fearless-android-iroha-send-audit.XXXXXX")"
trap 'rm -f "$TMP_MATCHES"' EXIT

fail() {
  echo "[iroha-send-readiness][android][error] $*" >&2
  exit 1
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_file() {
  local path="$1"
  [[ -f "$path" && ! -L "$path" ]] || fail "required regular file is missing or is a symlink: $path"
}

require_fixed() {
  local path="$1"
  local marker="$2"
  local label="$3"
  grep -Fq -- "$marker" "$path" || fail "$label is missing from ${path#"$ROOT_DIR/"}"
}

for required in \
  "$MANIFEST" "$DOC" "$UNIVERSAL_DOC" "$TRANSFER_SERVICE" "$TRANSFER_TEST" \
  "$TORII_CLIENT" "$TORII_MODELS" "$TORII_ROUTES" "$TORII_TEST" "$TORII_ROUTES_TEST" "$TORII_MODEL_TEST" \
  "$HISTORY_SOURCE" "$HISTORY_TEST" "$BALANCE_SOURCE" "$BALANCE_TEST" "$BALANCE_PROVIDER" \
  "$IROHA_IDENTITY" "$IROHA_ROUTING_TEST" "$META_ACCOUNT" "$ACCOUNT_TEST" \
  "$MIGRATION_SOURCE" "$MIGRATION_TEST" \
  "$NETWORK_MODULE" "$NETWORK_TEST" \
  "$METADATA_SOURCE" "$METADATA_TEST" \
  "$DI_MODULE" "$REGISTRY" "$SETTINGS" "$ROOT_BUILD" "$CI_WORKFLOW" \
  "$LOCAL_VALIDATOR" "$BRIDGE_BUILD" "$BRIDGE_LOCK" "$BRIDGE_SOURCE" "$BRIDGE_REQUEST" \
  "$BRIDGE_TEST" "$HASH_VECTOR" "$SMOKE_BUILD" "$SMOKE_LOCK" "$SMOKE_TEST" \
  "$MATERIALIZER" "$MATERIALIZER_TEST" "$BRIDGE_GATE" "$HASH_VECTOR_VERIFIER"; do
  require_file "$required"
done

[[ "$(wc -c < "$MANIFEST" | tr -d '[:space:]')" -le 32768 ]] || fail "readiness manifest exceeds 32 KiB"
actual_manifest_sha256="$(sha256_file "$MANIFEST")"
[[ "$actual_manifest_sha256" == "$EXPECTED_MANIFEST_SHA256" ]] ||
  fail "readiness manifest digest mismatch: expected $EXPECTED_MANIFEST_SHA256, got $actual_manifest_sha256"
actual_vector_sha256="$(sha256_file "$HASH_VECTOR")"
[[ "$actual_vector_sha256" == "$EXPECTED_VECTOR_SHA256" ]] ||
  fail "compact hash vector digest mismatch: expected $EXPECTED_VECTOR_SHA256, got $actual_vector_sha256"

node - "$MANIFEST" <<'NODE'
const fs = require('node:fs');

const path = process.argv[2];
let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(path, 'utf8'));
} catch (error) {
  console.error(`[iroha-send-readiness][android][error] invalid readiness JSON: ${error.message}`);
  process.exit(1);
}

function assert(condition, message) {
  if (!condition) {
    console.error(`[iroha-send-readiness][android][error] ${message}`);
    process.exit(1);
  }
}

assert(manifest.schemaVersion === 2, 'schemaVersion must be 2');
assert(manifest.platform === 'android', 'platform must be android');
assert(manifest.assessedAt === '2026-08-23', 'assessment date drifted');
assert(manifest.status === 'blocked', 'status must remain blocked');
assert(manifest.releaseEnabled === false, 'releaseEnabled must remain false');
assert(manifest.nexusEnabledByDefault === false, 'Nexus must remain disabled by default');
assert(manifest.upstream?.repository === 'hyperledger/iroha', 'unexpected upstream repository');
assert(manifest.upstream?.tag === 'v2.0.0-rc.2.1-fearless-mobile-sdk.3', 'unexpected upstream tag');
assert(manifest.artifact?.name === 'iroha-mobile-sdk-android-v2.0.0-rc.2.1-fearless-mobile-sdk.3.zip', 'unexpected Android artifact');
assert(manifest.artifact?.sha256 === '50e37369e3b08e4435f15ae31d1baca28dd55eae82686000df8f39b85feee7e6', 'unexpected Android artifact digest');
assert(manifest.artifact?.bytes === 116231129, 'unexpected Android artifact size');
assert(manifest.artifact?.reviewThresholdBytes === 100000000, 'unexpected Android review threshold');
assert(manifest.artifact.bytes > manifest.artifact.reviewThresholdBytes, 'artifact must remain above the recorded review threshold');

const staged = manifest.stagedIntegration;
assert(staged?.archiveReview === 'passed', 'archive review must remain passed');
assert(staged?.materializedCoordinate === 'org.hyperledger.iroha.sdk:core-jvm:2.0.0-rc.2.1-fearless-mobile-sdk.3', 'unexpected staged coordinate');
assert(staged?.coreJarSha256 === '33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce', 'unexpected core JAR digest');
assert(staged?.materializationOutput === 'ignored-build-directory', 'materialization must stay in ignored build output');
assert(staged?.ordinaryBuildFetch === false, 'ordinary builds must not fetch the SDK');
assert(staged?.applicationRuntimeDependency === false, 'staged bridge must not be an application dependency');
assert(staged?.nativeArtifactsAllowed === false, 'native artifacts must remain forbidden');
assert(staged?.bridge === 'java-only-test-stage', 'bridge must remain Java-only and staged');
assert(staged?.supportedNetwork === 'taira', 'only Taira may be staged');
assert(staged?.protocolChainId === 'fc56984b-2be7-431d-840e-21514d1883f0', 'canonical Taira chain ID drifted');
assert(staged?.canonicalAssetDefinitionRequired === true, 'canonical asset definitions must be required');
assert(staged?.canonicalTairaNativeXorDefinitionId === '6TEAJqbb8oEPmLncoNiMRbLEK6tw', 'canonical Taira XOR ID drifted');
assert(staged?.canonicalTairaNativeXorScale === 9, 'canonical Taira XOR scale drifted');
assert(staged?.canonicalTairaNativeXorSymbol === 'XOR', 'canonical Taira XOR symbol drifted');
assert(staged?.canonicalTairaNativeXorDecimals === 9, 'canonical Taira XOR decimals drifted');
assert(staged?.liveTairaObservedNativeXorScale === null, 'pre-rollout live Taira XOR scale observation drifted');
assert(staged?.productionAssetMappingStatus === 'exact-base58-with-torii-definition-validation', 'canonical asset mapping policy drifted');
assert(staged?.fanoutReadPolicy === 'require-six-complete-fanout-headers-reject-partial-or-malformed', 'fanout read policy drifted');
assert(staged?.submissionHashPolicy === 'require-local-top-level-transaction-receipt-final-hash-equality-and-applied-finality-via-mcp-submit-and-wait', 'submission hash/finality policy drifted');
assert(staged?.canonicalHashFormat === '[0-9a-f]{63}[13579bdf]', 'canonical Iroha hash format drifted');
assert(staged?.allSubmissionHashesEqualityRequired === true, 'all submit-and-wait hashes must match locally');
assert(staged?.appliedTerminalRequired === true, 'Applied terminal status must remain mandatory');
assert(JSON.stringify(staged?.liveTairaObservation) === JSON.stringify({
  observedAt: '2026-08-23',
  buildCommit: '7efcc118eb50e3369d004d092f9b9d0b4d31ac52',
  blocks: 6,
  peers: 2,
  chainProgressStalenessApproxMinutes: 41,
  chainIdReported: null,
  statusExposesAbsoluteManifestPath: true,
  canonicalXorFanoutAttempted: 5,
  canonicalXorFanoutSucceeded: 1,
  canonicalXorScale: null,
  alternateScaleNineDefinitionPrefixObserved: '61Ct',
  committedValidatorDnsTotal: 4,
  committedValidatorDnsResolved: 0,
  deploymentAuthority: 'workspace-root-live-gate'
}), 'live Taira degraded observation drifted');
assert(staged?.sdkReleaseTagCommit === '4f8cfbdd17aa6a3b049e619f23ec02501e5297b6', 'SDK release-tag commit drifted');
assert(staged?.sdkDeployedNodeCompatibility === 'not-proven', 'SDK/deployed-node compatibility must remain blocked');
assert(staged?.feePolicy === 'not-recorded', 'live fee policy must remain blocked');
assert(staged?.feeEstimateBehavior === 'fail-closed-typed-error-authoritative-policy-unavailable', 'fee estimation must remain fail closed');
assert(staged?.hashParity?.canonicalCompactHash === '2332d0004eb24d97fd965fe68f6f31b0e51339764b4dd80f3ea50a3b6f7e5003', 'canonical compact hash drifted');
assert(staged?.hashParity?.releaseTagRustFramingInspection === 'passed', 'release-tag Rust framing inspection must remain recorded');
assert(staged?.hashParity?.currentLocalNativeHostDiagnostic === 'passed-unproven-build-provenance', 'local native diagnostic must not be promoted to provenance');
assert(staged?.hashParity?.pinnedSdkHasherStatus === 'known-defective-fixed-u64-length', 'known SDK hasher defect must remain explicit');
assert(staged?.hashParity?.pinnedSdkDefectiveHash === '2b5e69a0a3d333756f4ac2a54bf7eaff88a2c87484d89e6e7da98abea659662d', 'defective SDK hash vector drifted');
assert(staged?.hashParity?.liveTairaReceiptParity === 'not-recorded', 'live Taira receipt parity must remain blocked');

assert(manifest.blocker?.code === 'android_staged_bridge_production_gates_required', 'unexpected blocker code');
assert(manifest.blocker?.defaultSigner === 'UnavailableIrohaTransferSigner', 'unexpected default signer');
const expectedExitCriteria = [
  'publish-and-review-upstream-core-sources-licenses-sbom-and-provenance',
  'record-and-test-the-authoritative-taira-fee-policy',
  'accept-or-remove-bouncy-castle-non-destroyable-private-key-copy-risk',
  'wire-reviewed-bridge-through-production-di-and-pass-android-device-and-r8-release-tests',
  'record-live-taira-native-xor-scale-9-after-rollout',
  'publish-fixed-reviewed-sdk-and-prove-sdk-deployed-node-and-live-receipt-hash-compatibility',
  'record-funded-taira-and-nexus-live-broadcast-evidence-before-enablement',
];
assert(JSON.stringify(manifest.exitCriteria) === JSON.stringify(expectedExitCriteria), 'production exit criteria drifted');
NODE

require_fixed "$TRANSFER_SERVICE" \
  'private val irohaTransferSigner: IrohaTransferSigner = UnavailableIrohaTransferSigner' \
  "fail-closed provider default"
require_fixed "$TRANSFER_SERVICE" \
  'object UnavailableIrohaTransferSigner : IrohaTransferSigner' \
  "unavailable signer implementation"
require_fixed "$TRANSFER_SERVICE" \
  'throw IllegalStateException("Iroha transfer signing codec is unavailable")' \
  "unavailable signer exception"
require_fixed "$TRANSFER_SERVICE" \
  'throw IrohaTransferFeeException(' \
  "typed fee-unavailable fail-closed behavior"
require_fixed "$TRANSFER_SERVICE" \
  'IrohaTransferFeeErrorCode.AUTHORITATIVE_POLICY_UNAVAILABLE' \
  "authoritative fee-policy error code"
require_fixed "$TRANSFER_TEST" \
  'fun `provider routes iroha chains to fail closed iroha transfer service`()' \
  "default signer fail-closed test"
require_fixed "$TRANSFER_TEST" \
  'fun `iroha fee estimation fails closed without authoritative policy`()' \
  "fee-unavailable fail-closed test"
require_fixed "$TRANSFER_TEST" \
  'fun `iroha transfer rejects mnemonic mismatch before signer or torii calls`()' \
  "mnemonic/key mismatch adversarial test"
require_fixed "$TRANSFER_TEST" \
  'fun `iroha transfer rejects noncanonical hash spellings without normalization`()' \
  "canonical Iroha transfer hash adversarial test"
for marker in \
  'signedTransfer.transactionHashHex.canonicalIrohaTransactionHash()' \
  'return this?.takeIf(IROHA_TRANSACTION_HASH_PATTERN::matches)' \
  'toriiClient.submitTransactionAndWait(' \
  'expectedHash = localHash' \
  'outcome.hash != localHash' \
  'outcome.transactionHash != localHash' \
  'outcome.receiptHash != localHash' \
  'outcome.finalHash != localHash' \
  'outcome.terminalKind != IrohaPipelineTransactionStatusKind.Applied' \
  'IrohaSubmitAndWaitErrorCode.REJECTED' \
  'IrohaSubmitAndWaitErrorCode.EXPIRED' \
  'IrohaSubmitAndWaitErrorCode.TIMEOUT' \
  'Regex("^[0-9a-f]{63}[13579bdf]$")'; do
  require_fixed "$TRANSFER_SERVICE" "$marker" "MCP submit-and-wait invariant '$marker'"
done
require_fixed "$HISTORY_SOURCE" \
  'val outgoing = sourceAccount == accountAddress' \
  "exact Iroha history account binding"
require_fixed "$HISTORY_SOURCE" \
  'if (transactionStatus != "Committed")' \
  "exact committed Iroha history status"
require_fixed "$HISTORY_SOURCE" \
  'Torii MCP history returned a non-canonical field alias' \
  "canonical Iroha history field names"
require_fixed "$HISTORY_TEST" \
  'rejects asset sources that only contain the wallet address as a substring' \
  "exact Iroha history account-binding adversarial test"
require_fixed "$HISTORY_TEST" \
  'rejects case mutated noncanonical i105 source accounts' \
  "canonical I105 history account-binding adversarial test"
require_fixed "$HISTORY_TEST" \
  'rejects every noncanonical committed transaction status' \
  "exact committed Iroha history status adversarial test"
require_fixed "$HISTORY_TEST" \
  'rejects legacy and ambiguous top level history field aliases' \
  "canonical Iroha history field-name adversarial test"
for marker in \
  'definitions.hasMore != false' \
  'definitions.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue' \
  '!body.hasExactKeys(HISTORY_PAGE_KEYS)' \
  '!pagination.hasExactKeys(PAGINATION_KEYS)' \
  'page != requestedPage.toLong()' \
  'totalPages != calculatedTotalPages' \
  'val box = this["box"] as? Map<*, *>' \
  'mode.canonicalString("mode") != "Atomic"' \
  'mode["value"] != null' \
  'it.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_LEG_ID_LENGTH' \
  'BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)' \
  'Torii MCP history returned duplicate operation identities'; do
  require_fixed "$HISTORY_SOURCE" "$marker" "exact Iroha history wire invariant '$marker'"
done
for marker in \
  'requires explicit complete asset definition pagination' \
  'requires exact atomic batch mode and stable canonical identities' \
  'rejects noncanonical numeric spellings and values outside the iroha domain' \
  'rejects malformed canonical dto fields and rust raw identifier aliases' \
  'requires coherent exact pagination and snapshot bound cursors'; do
  require_fixed "$HISTORY_TEST" "$marker" "Iroha history wire adversarial test '$marker'"
done
for marker in \
  'CanonicalIrohaScaleAdapter' \
  'reader.peek() != JsonToken.NUMBER' \
  'Regex("^(0|[1-9][0-9]*)$")'; do
  require_fixed "$TORII_MODELS" "$marker" "canonical Iroha scale decoder '$marker'"
done
require_fixed "$TORII_MODEL_TEST" \
  'decodes only canonical integer scale tokens' \
  "canonical Iroha scale decoder adversarial test"
for marker in \
  'limit = IrohaToriiRoutes.MAX_LIMIT' \
  'offset = 0' \
  'countMode = IrohaToriiRoutes.CountMode.Bounded' \
  'definitions.hasMore != false' \
  'definitions.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue' \
  'response.hasMore != false' \
  'response.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue' \
  'if (item.accountId != address)' \
  'if (item.assetId != null)' \
  'IrohaToriiRoutes.normalizeAssetDefinitionId(presentItemAsset)' \
  'IrohaToriiRoutes.normalizeAccountAssetScope(itemScope)' \
  'seenAccountAssetScopes.add(canonicalItemAsset to canonicalScope)' \
  'val maxBalanceInPlanks = MAX_QUANTITY.multiply(BigInteger.TEN.pow(precision))' \
  'value > maxBalanceInPlanks.subtract(total)'; do
  require_fixed "$BALANCE_SOURCE" "$marker" "strict Iroha balance invariant '$marker'"
done
for marker in \
  'requires explicit complete pagination for definitions and account assets' \
  'requires bounded count mode and exact canonical account asset fields' \
  'accepts canonical numeric maximum at wallet precision' \
  'rejects duplicate asset scope rows' \
  'rejects cross-scope balance sums above the precision adjusted numeric maximum' \
  'rejects noncanonical iroha chain identities'; do
  require_fixed "$BALANCE_TEST" "$marker" "Iroha balance adversarial test '$marker'"
done
for marker in \
  'return id == UniversalWalletRegistry.taira.chainId ||' \
  'id == UniversalWalletRegistry.nexus.chainId' \
  'fun Chain.hasNonCanonicalUniversalWalletIrohaIdentity()' \
  'UniversalWalletRegistry.taira.id' \
  'externalApi?.history?.type == Chain.ExternalApi.Section.Type.IROHA' \
  'UniversalWalletRegistry.taira.chainId -> UniversalWalletRegistry.taira' \
  'UniversalWalletRegistry.nexus.chainId -> UniversalWalletRegistry.nexus'; do
  require_fixed "$IROHA_IDENTITY" "$marker" "canonical Iroha identity invariant '$marker'"
done
require_fixed "$BALANCE_PROVIDER" \
  'chain.hasNonCanonicalUniversalWalletIrohaIdentity() -> throw IllegalArgumentException(' \
  "noncanonical Iroha balance-route refusal"
require_fixed "$TRANSFER_SERVICE" \
  '} else if (chain.hasNonCanonicalUniversalWalletIrohaIdentity()) {' \
  "noncanonical Iroha transfer-route refusal"
for marker in \
  'private val TAIRA_CHAIN_IDS = setOf(' \
  'UniversalWalletRegistry.taira.chainId' \
  'private val NEXUS_CHAIN_IDS = setOf(' \
  'UniversalWalletRegistry.nexus.chainId' \
  'chain.hasNonCanonicalUniversalWalletIrohaIdentity() -> null'; do
  require_fixed "$META_ACCOUNT" "$marker" "canonical stored Iroha account invariant '$marker'"
done
for source in "$META_ACCOUNT" "$MIGRATION_SOURCE"; do
  if grep -Fq -- 'UniversalWalletRegistry.taira.id' "$source" || \
     grep -Fq -- 'UniversalWalletRegistry.nexus.id' "$source"; then
    fail "Iroha registry aliases must not be stored-account or migration identities in ${source#"$ROOT_DIR/"}"
  fi
done
for marker in \
  'private val TAIRA_CHAIN_IDS = setOf(' \
  'UniversalWalletRegistry.taira.chainId' \
  'private val NEXUS_CHAIN_IDS = setOf(' \
  'UniversalWalletRegistry.nexus.chainId'; do
  require_fixed "$MIGRATION_SOURCE" "$marker" "canonical Iroha migration invariant '$marker'"
done
require_fixed "$IROHA_ROUTING_TEST" \
  'iroha routing rejects registry aliases case mutations and unknown identities' \
  "canonical Iroha routing adversarial test"
require_fixed "$TRANSFER_TEST" \
  'provider rejects noncanonical iroha profile identities' \
  "noncanonical Iroha transfer-route adversarial test"
require_fixed "$ACCOUNT_TEST" \
  'iroha registry aliases never match stored universal wallet accounts' \
  "canonical stored Iroha account adversarial test"
require_fixed "$MIGRATION_TEST" \
  'noncanonical iroha identities never complete migration' \
  "canonical Iroha migration adversarial test"
[[ "$(grep -Fc 'val hasMore: Boolean?' "$TORII_MODELS")" -eq 2 ]] ||
  fail "nullable Iroha list completeness must cover account assets and asset definitions exactly"
for marker in \
  '(parsed.protocol != "https" && !(parsed.protocol == "http" && isLocal))' \
  'parsed.userInfo != null' \
  'parsed.query != null' \
  'parsed.ref != null'; do
  require_fixed "$TORII_ROUTES" "$marker" "safe Iroha base URL invariant '$marker'"
done
for marker in \
  'preserves missing and null list completeness as unavailable' \
  'ftp://localhost'; do
  require_fixed "$TORII_ROUTES_TEST" "$marker" "Iroha wire/URL adversarial test '$marker'"
done
for marker in \
  '.followRedirects(false)' \
  '.followSslRedirects(false)' \
  'irohaNoRedirectHttpClient(okHttpClient)'; do
  require_fixed "$NETWORK_MODULE" "$marker" "Iroha no-redirect transport invariant '$marker'"
done
require_fixed "$NETWORK_TEST" \
  'iroha transport disables http and https redirects' \
  "Iroha no-redirect transport test"
for marker in \
  'unavailable > failed - denied' \
  'notFound > failed - denied - unavailable'; do
  require_fixed "$TORII_MODELS" "$marker" "overflow-safe fanout invariant '$marker'"
done
require_fixed "$TORII_MODELS" \
  'val jsonrpc: String? = null' \
  "missing JSON-RPC version must remain observable"
require_fixed "$TORII_ROUTES" \
  'if (!HASH_256.matches(hash))' \
  "canonical transaction-status hash routing"
for marker in \
  'const val SUBMIT_AND_WAIT_TOOL = "iroha.transactions.submit_and_wait"' \
  '"body_base64" to Base64.getEncoder().encodeToString(noritoBytes)' \
  '"hash" to canonicalExpectedHash' \
  '"terminal_statuses" to listOf(APPLIED_STATUS)' \
  'listOf(hash, transactionHash, receiptHash, finalHash).any { it != expectedHash }' \
  'terminalKind != IrohaPipelineTransactionStatusKind.Applied' \
  'finalKind != IrohaPipelineTransactionStatusKind.Applied' \
  'const val FANOUT_ATTEMPTED_HEADER = "x-iroha-fanout-routes-attempted"' \
  'const val FANOUT_SUCCEEDED_HEADER = "x-iroha-fanout-routes-succeeded"' \
  'const val FANOUT_FAILED_HEADER = "x-iroha-fanout-routes-failed"' \
  'const val FANOUT_DENIED_HEADER = "x-iroha-fanout-routes-denied"' \
  'const val FANOUT_UNAVAILABLE_HEADER = "x-iroha-fanout-routes-unavailable"' \
  'const val FANOUT_NOT_FOUND_HEADER = "x-iroha-fanout-routes-not-found"' \
  'return completeRoutedBody(' \
  'return completeMcpBody(' \
  'validateFanoutHeaders { response.headers().values(it).singleOrNull() }' \
  'requireJsonContentType(response)' \
  'headers.entries.forEach { entry ->' \
  'duplicate case-insensitive headers' \
  'val contentType = (route["content_type"] as? String)' \
  'value?.takeIf(FANOUT_COUNT::matches)?.toIntOrNull()' \
  'val hasExactlyOnePayload = (response.result == null) != (response.error == null)' \
  'response.jsonrpc != "2.0"' \
  'response.id != request.id' \
  'return value?.takeIf(CANONICAL_TRANSACTION_HASH::matches)' \
  'Regex("^[0-9a-f]{63}[13579bdf]$")'; do
  require_fixed "$TORII_CLIENT" "$marker" "Torii strict read/write invariant '$marker'"
done
for marker in \
  'rejects degraded nested submit and wait route metadata' \
  'rejects incomplete six-header fanout contract' \
  'rejects successful routed reads without fanout evidence' \
  'rejects overflowing fanout counters as malformed metadata' \
  'accepts mcp transport without outer fanout when nested routes are complete' \
  'rejects non json and ambiguous outer successful responses' \
  'rejects non json and case colliding nested route metadata' \
  'rejects an outer mcp redirect before accepting an attacker final body' \
  'rejects any submit and wait hash that differs from local expected hash' \
  'rejects json rpc responses that are not exactly bound to the request' \
  'rejects non canonical transaction hash spellings without normalization' \
  'rejects matching but non applied terminal and final status'; do
  require_fixed "$TORII_TEST" "$marker" "Torii adversarial test '$marker'"
done
require_fixed "$TORII_ROUTES_TEST" \
  'listOf("0x$HASH", HASH.uppercase(), " $HASH")' \
  "non-canonical transaction-status hash adversarial test"
require_fixed "$TRANSFER_SERVICE" \
  'val transactionMetadata: IrohaTransferMetadata = IrohaTransferMetadata.empty()' \
  "normal-transfer empty metadata default"
for marker in \
  'suspend fun transferWalletSmokeEvidence' \
  'val validatedMetadata = IrohaTransferMetadata.walletSmoke(untrustedMetadata)' \
  'val evidenceNetwork = chain.universalWalletIrohaNetwork()' \
  'evidenceNetwork != UniversalWalletRegistry.nexus' \
  'val evidenceToriiBaseUrl = chain.externalApi?.history' \
  'evidenceToriiBaseUrl != canonicalMinamoto' \
  'context.signingRequest.network != IrohaTransferMetadata.NEXUS_NETWORK' \
  'context.signingRequest.chainId != IrohaTransferMetadata.NEXUS_CHAIN_ID' \
  'val canonicalMinamoto = UniversalWalletRegistry.nexus.toriiBaseUrl' \
  'context.toriiBaseUrl != canonicalMinamoto' \
  'withValidatedWalletSmokeMetadata(' \
  'private suspend fun signAndSubmit'; do
  require_fixed "$TRANSFER_SERVICE" "$marker" "Nexus wallet-smoke service invariant '$marker'"
done
for marker in \
  'nexus wallet smoke evidence uses exact immutable metadata and canonical minamoto' \
  'wallet smoke evidence rejects taira and noncanonical minamoto before signer or torii' \
  'wallet smoke evidence rejects malformed and aliasing metadata before signer or torii' \
  'verifyNoInteractions(tairaAccountRepository)' \
  'verifyNoInteractions(nexusAccountRepository)' \
  'verifyNoInteractions(accountRepository)'; do
  require_fixed "$TRANSFER_TEST" "$marker" "Nexus wallet-smoke service test marker '$marker'"
done

node - "$TRANSFER_SERVICE" <<'NODE'
const fs = require('node:fs');
const source = fs.readFileSync(process.argv[2], 'utf8');
const start = source.indexOf('suspend fun transferWalletSmokeEvidence');
const validation = source.indexOf(
  'val validatedMetadata = IrohaTransferMetadata.walletSmoke(untrustedMetadata)',
  start,
);
const preflightNetwork = source.indexOf(
  'val evidenceNetwork = chain.universalWalletIrohaNetwork()',
  start,
);
const preflightRouteGate = source.indexOf(
  'evidenceNetwork != UniversalWalletRegistry.nexus',
  start,
);
const canonicalMinamoto = source.indexOf(
  'val canonicalMinamoto = UniversalWalletRegistry.nexus.toriiBaseUrl',
  start,
);
const preflightMinamotoGate = source.indexOf('evidenceToriiBaseUrl != canonicalMinamoto', start);
const context = source.indexOf('val context = resolveContext(transfer)', start);
const routeGate = source.indexOf(
  'context.signingRequest.network != IrohaTransferMetadata.NEXUS_NETWORK',
  start,
);
const postContextMinamotoGate = source.indexOf(
  'context.toriiBaseUrl != canonicalMinamoto',
  start,
);
const attachment = source.indexOf('withValidatedWalletSmokeMetadata(', start);
const submission = source.indexOf('return signAndSubmit(evidenceContext)', start);
const ordered = [
  start,
  validation,
  preflightNetwork,
  preflightRouteGate,
  canonicalMinamoto,
  preflightMinamotoGate,
  context,
  routeGate,
  postContextMinamotoGate,
  attachment,
  submission,
]
  .every((value, index, values) => value >= 0 && (index === 0 || value > values[index - 1]));

if (!ordered) {
  console.error('[iroha-send-readiness][android][error] wallet-smoke input and route must validate before context, attachment, and submission');
  process.exit(1);
}
NODE

for marker in \
  'class IrohaTransferMetadata private constructor' \
  'const val EVIDENCE_ROLE_KEY = "evidence_role"' \
  'const val ROUTE_GOVERNANCE_ACTION_HASH_KEY = "route_governance_action_hash"' \
  'const val WALLET_PLATFORM_KEY = "wallet_platform"' \
  'const val WALLET_COMMIT_KEY = "wallet_commit"' \
  'const val WALLET_SMOKE_ROLE = "wallet-smoke"' \
  'const val ANDROID_PLATFORM = "android"' \
  'const val NEXUS_NETWORK = "nexus"' \
  'const val NEXUS_CHAIN_ID = "sora:nexus:global"' \
  'Regex("sha256:[0-9a-f]{64}")' \
  'Regex("[0-9a-f]{40}")' \
  'snapshot.keys == WALLET_SMOKE_KEYS' \
  'snapshot.values.all { it is String }' \
  'fun IrohaTransferSigningRequest.withWalletSmokeMetadata' \
  'internal fun IrohaTransferSigningRequest.withValidatedWalletSmokeMetadata' \
  'require(!validatedMetadata.isEmpty())' \
  'requireNexusWalletSmokeContext()' \
  'require(network == IrohaTransferMetadata.NEXUS_NETWORK)' \
  'require(chainId == IrohaTransferMetadata.NEXUS_CHAIN_ID)'; do
  require_fixed "$METADATA_SOURCE" "$marker" "wallet-smoke metadata invariant '$marker'"
done

for marker in \
  'wallet smoke factory emits exact canonical all-string contract' \
  'rejects malformed wallet smoke metadata before signer or torii calls' \
  'metadata snapshots mutable input and returns immutable defensive copies' \
  'untrusted input order is normalized before codec handoff' \
  'wallet smoke metadata is rejected outside exact Nexus global context' \
  'validated wallet smoke attachment cannot downgrade to empty metadata'; do
  require_fixed "$METADATA_TEST" "$marker" "wallet-smoke metadata test marker '$marker'"
done

node - "$DI_MODULE" <<'NODE'
const fs = require('node:fs');
const source = fs.readFileSync(process.argv[2], 'utf8');
const calls = source.match(/\bTransferServiceProvider\s*\(/g) ?? [];
const expected = `return TransferServiceProvider(
            substrateSource,
            ethereumRemoteSource,
            keyPairRepository,
            accountRepository,
            tonRemoteSource,
            assetDao,
            bitcoinIndexerClient,
            solanaRpcClient,
            solanaBalanceSync,
            irohaToriiClient
        )`;

if (calls.length !== 1 || !source.includes(expected)) {
  console.error('[iroha-send-readiness][android][error] production DI construction must use the audited unavailable-signer default');
  process.exit(1);
}
NODE

provider_files="$(
  find "$ROOT_DIR/feature-wallet-impl/src/main" -type f -name '*.kt' \
    -exec grep -Il 'TransferServiceProvider(' {} + | sort
)"
expected_provider_files="$(printf '%s\n%s\n' "$TRANSFER_SERVICE" "$DI_MODULE" | sort)"
[[ "$provider_files" == "$expected_provider_files" ]] || {
  printf '%s\n' "$provider_files" >&2
  fail "TransferServiceProvider construction appeared outside the audited production DI path"
}

node - "$REGISTRY" <<'NODE'
const fs = require('node:fs');
const source = fs.readFileSync(process.argv[2], 'utf8');
const tairaStart = source.indexOf('val taira = IrohaNetwork(');
const tairaEnd = source.indexOf('\n    )', tairaStart);
const start = source.indexOf('val nexus = IrohaNetwork(');
const end = source.indexOf('\n    )', start);
const taira = source.slice(tairaStart, tairaEnd);
if (tairaStart < 0 || tairaEnd < 0 ||
    !taira.includes('chainId = TAIRA_CHAIN_ID') ||
    !taira.includes('id = TAIRA_XOR_ASSET_DEFINITION_ID') ||
    !taira.includes('symbol = "XOR"') ||
    !taira.includes('decimals = 9')) {
  console.error('[iroha-send-readiness][android][error] canonical Taira native profile drifted');
  process.exit(1);
}
if (start < 0 || end < 0 || !source.slice(start, end).includes('enabledByDefault = false')) {
  console.error('[iroha-send-readiness][android][error] Nexus registry default is not provably disabled');
  process.exit(1);
}
NODE

require_fixed "$SETTINGS" "include ':iroha-sdk-bridge'" "staged bridge settings include"
require_fixed "$SETTINGS" "include ':iroha-sdk-bridge-kotlin-smoke'" "Kotlin smoke settings include"
require_fixed "$ROOT_BUILD" "':iroha-sdk-bridge:test'" "staged Java test task"
require_fixed "$ROOT_BUILD" "':iroha-sdk-bridge-kotlin-smoke:test'" "staged Kotlin test task"
require_fixed "$ROOT_BUILD" "tasks.register('runStagedIrohaBridgeTests')" "explicit staged test runner"

for marker in \
  "apply plugin: 'java-library'" \
  "implementation(\"org.hyperledger.iroha.sdk:core-jvm:\${irohaSdkVersion}\")" \
  "transitive = false" \
  "lockMode = LockMode.STRICT" \
  "33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce" \
  "add5915e6acfc6ab5836e1fd8a5e21c6488536a8c1f21f386eeb3bf280b702d7" \
  "5f2ac1ca8dc8b37a3f4314e716d36969ebf0227a75181d32699d0a8f645b1c21" \
  "dependsOn tasks.named('verifyIrohaBridgeRuntime')"; do
  require_fixed "$BRIDGE_BUILD" "$marker" "bridge build invariant '$marker'"
done

for marker in \
  "apply plugin: 'org.jetbrains.kotlin.jvm'" \
  "implementation project(':iroha-sdk-bridge')" \
  "lockMode = LockMode.STRICT" \
  "verifyKotlinCompileClasspathIsolation" \
  "verifyIrohaStagingBoundary" \
  "verifyStagedIrohaSdkExcluded" \
  "debugRuntimeClasspath" \
  "releaseRuntimeClasspath"; do
  require_fixed "$SMOKE_BUILD" "$marker" "Kotlin smoke invariant '$marker'"
done
require_fixed "$SMOKE_TEST" 'kotlinTwoOneConsumerCompilesAndRunsWithoutSdkTypesInPublicApi' \
  "Kotlin 2.1 isolation test"
require_fixed "$SMOKE_TEST" 'tairaBridgeRejectsNexusWalletSmokeMetadataBeforeSigning' \
  "Kotlin Taira/Nexus metadata isolation test"

for marker in \
  'SUPPORTED_NETWORK = "taira"' \
  'SUPPORTED_CHAIN_ID = "fc56984b-2be7-431d-840e-21514d1883f0"' \
  'SUPPORTED_CHAIN_DISCRIMINANT = 369' \
  'AssetDefinitionIdEncoder.isCanonicalAddress' \
  'IrohaHash.prehash(message)' \
  'SignedTransactionEncoder.encodeVersioned' \
  'canonicalTransactionHashHex(versioned)' \
  'encodeCanonicalCompactLength' \
  'decodeCanonicalCompactLength' \
  'metadata.isEmpty()' \
  'wallet-smoke transaction metadata is Nexus-only; staged Taira bridge requires empty metadata' \
  'Collections.emptyMap()' \
  'Castle retains an internal private-key copy without a destruction API'; do
  require_fixed "$BRIDGE_SOURCE" "$marker" "bridge source invariant '$marker'"
done
if grep -Eq 'JsonValue|java[.]lang[.]reflect|constructor[.]newInstance' "$BRIDGE_SOURCE"; then
  fail "Taira bridge must not encode or reflectively construct Nexus wallet-smoke metadata"
fi
for marker in \
  'private final Map<String, String> transactionMetadata' \
  'this.transactionMetadata = immutableStringMap(transactionMetadata)' \
  'Collections.unmodifiableMap(new LinkedHashMap<>(transactionMetadata))' \
  'transaction metadata keys and values must all be strings'; do
  require_fixed "$BRIDGE_REQUEST" "$marker" "bridge request metadata invariant '$marker'"
done
if grep -Eq '^[[:space:]]*import[[:space:]].*SignedTransactionHasher' "$BRIDGE_SOURCE"; then
  fail "production bridge source must not import the pinned defective SignedTransactionHasher"
fi
if grep -Eq 'SignedTransactionHasher[.]hash|SignedTransactionHasher[.]canonical' "$BRIDGE_SOURCE"; then
  fail "production bridge source must not call the pinned defective SignedTransactionHasher"
fi

for marker in \
  'compactLengthEncodingIsMinimalAtEveryBoundaryAndRejectsOverlongInputs' \
  'PINNED_SDK_DEFECTIVE_HASH' \
  'SignedTransactionHasher.hashHex(signed)' \
  'xor#universal' \
  '6TEAJqbb8oEPmLncoNiMRbLEK6tw' \
  'rejectsCanonicalNexusWalletSmokeMetadataBeforeClockOrSigning' \
  'rejectsMalformedWalletSmokeMetadataBeforeSigning' \
  'transactionMetadataIsDefensivelyCopiedAndImmutable' \
  'everyNonEmptyMetadataMapAbortsBeforeClockSigningOrSubmission' \
  'concurrentSigningIsDeterministicAndDoesNotShareSecretState'; do
  require_fixed "$BRIDGE_TEST" "$marker" "bridge adversarial test marker '$marker'"
done

for marker in \
  'org.hyperledger.iroha.sdk:core-jvm:2.0.0-rc.2.1-fearless-mobile-sdk.3=' \
  'org.bouncycastle:bcprov-jdk18on:1.78.1=' \
  'org.jetbrains.kotlin:kotlin-stdlib:2.1.10='; do
  require_fixed "$BRIDGE_LOCK" "$marker" "bridge dependency lock '$marker'"
  require_fixed "$SMOKE_LOCK" "$marker" "smoke dependency lock '$marker'"
done
if grep -Eiq 'client-android|offline-wallet|zstd-jni|kotlinx-serialization' "$BRIDGE_LOCK"; then
  fail "forbidden Android/native/serialization dependency entered the Java bridge lock"
fi
if grep -Eiq 'client-android|offline-wallet|zstd-jni|kotlinx-serialization' "$SMOKE_LOCK"; then
  fail "forbidden Android/native/serialization dependency entered the Kotlin smoke lock"
fi

for marker in \
  '50e37369e3b08e4435f15ae31d1baca28dd55eae82686000df8f39b85feee7e6' \
  '33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce' \
  'IROHA_CORE_ARCHIVE_BYTES="116231129"'; do
  require_fixed "$MATERIALIZER" "$marker" "materializer pin '$marker'"
done
require_fixed "$MATERIALIZER_TEST" 'negative/adversarial scenarios passed' \
  "materializer adversarial test summary"
require_fixed "$BRIDGE_GATE" 'verify-iroha-compact-hash-vector.py' \
  "independent compact hash verifier gate"
require_fixed "$BRIDGE_GATE" '--self-test "$HASH_VECTOR"' \
  "compact hash adversarial self-test gate"
require_fixed "$BRIDGE_GATE" 'test-fearless-utils-derived-tree.sh' \
  "fearless-utils derived-tree preparation"
require_fixed "$BRIDGE_GATE" 'ensure-fearless-utils.sh' \
  "fearless-utils guard preparation"
require_fixed "$BRIDGE_GATE" ':iroha-sdk-bridge:check' \
  "Java bridge check gate"
require_fixed "$BRIDGE_GATE" ':iroha-sdk-bridge-kotlin-smoke:check' \
  "Kotlin smoke check gate"
require_fixed "$BRIDGE_GATE" '--offline' "offline repeat gate"
require_fixed "$CI_WORKFLOW" 'bash ./scripts/verify-staged-iroha-core-bridge.sh --download' \
  "Android CI staged bridge gate"
require_fixed "$LOCAL_VALIDATOR" 'verify_staged_iroha_core_bridge' \
  "local staged bridge gate"

command -v python3 >/dev/null 2>&1 || fail "python3 is required for the independent compact hash audit"
python3 "$HASH_VECTOR_VERIFIER" --self-test "$HASH_VECTOR" >/dev/null ||
  fail "independent compact hash vector verification failed"

while IFS= read -r -d '' gradle_file; do
  relative="${gradle_file#"$ROOT_DIR/"}"
  case "$relative" in
    iroha-sdk-bridge/build.gradle|iroha-sdk-bridge-kotlin-smoke/build.gradle) continue ;;
  esac
  if grep -EnI \
    "org[.]hyperledger[.]iroha[.]sdk|project[(][[:space:]]*['\"]:iroha-sdk-bridge" \
    "$gradle_file" >"$TMP_MATCHES"; then
    sed "s#^#$relative:#" "$TMP_MATCHES" >&2
    fail "staged Iroha SDK dependency leaked outside the two audited staging modules"
  fi
done < <(
  find -P "$ROOT_DIR" -type f \( -name '*.gradle' -o -name '*.gradle.kts' \) \
    ! -path "$ROOT_DIR/.git/*" \
    ! -path "$ROOT_DIR/.gradle/*" \
    ! -path '*/build/*' \
    ! -path "$ROOT_DIR/fearless-utils-Android/*" \
    -print0
)
: > "$TMP_MATCHES"

tracked_binary="$(git -C "$ROOT_DIR" ls-files | grep -E '(^|/)(libconnect_norito_bridge[.]so|iroha-mobile-sdk-android-.*[.]zip|client-android-.*[.]aar|offline-wallet-android-.*[.]aar|core-jvm-.*[.]jar)$' || true)"
[[ -z "$tracked_binary" ]] || {
  printf '%s\n' "$tracked_binary" >&2
  fail "Iroha SDK/native binary material must remain generated and untracked"
}

for marker in \
  'BLOCKED / fail closed' \
  'v2.0.0-rc.2.1-fearless-mobile-sdk.3' \
  '50e37369e3b08e4435f15ae31d1baca28dd55eae82686000df8f39b85feee7e6' \
  '116231129' \
  '100000000' \
  'archive is larger' \
  'structural and dependency review is now complete' \
  'android_staged_bridge_production_gates_required' \
  '33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce' \
  '2332d0004eb24d97fd965fe68f6f31b0e51339764b4dd80f3ea50a3b6f7e5003' \
  '2b5e69a0a3d333756f4ac2a54bf7eaff88a2c87484d89e6e7da98abea659662d' \
  'defective' \
  'fc56984b-2be7-431d-840e-21514d1883f0' \
  '6TEAJqbb8oEPmLncoNiMRbLEK6tw' \
  'accept no `name#domain`' \
  'all six fanout completeness headers' \
  '`iroha.transactions.submit_and_wait`' \
  '`entrypoint_hash`' \
  'live `spec.scale` of 9' \
  '7efcc118eb50e3369d004d092f9b9d0b4d31ac52' \
  'workspace-root live gate remains deployment authority' \
  'exact four-string Android wallet-smoke metadata contract' \
  'Nexus-only: construction requires `network=nexus`' \
  '`chainId=sora:nexus:global`' \
  'canonical SORA' \
  'rejects every non-empty transaction metadata map' \
  'The bridge never signs Nexus' \
  'no reflective constructor is used by the Taira bridge' \
  'does not enable production send' \
  'does **not** make Iroha send production-ready'; do
  require_fixed "$DOC" "$marker" "pinned blocker evidence marker '$marker'"
done
for stale in \
  'android_sdk_archive_review_required' \
  'has not been materialized' \
  'not materialized' \
  'unapproved archive'; do
  if grep -Fiq -- "$stale" "$DOC"; then
    fail "stale pre-review statement remains in readiness documentation: $stale"
  fi
done
require_fixed "$UNIVERSAL_DOC" \
  'Iroha `features = ["transfer"]` is capability metadata, not a production-send' \
  "Universal Wallet non-enablement statement"
require_fixed "$UNIVERSAL_DOC" \
  '`config/iroha-production-send-readiness.json`' \
  "Universal Wallet readiness manifest reference"

echo "[iroha-send-readiness][android] reviewed staging is isolated; production send remains explicit and fail closed."
