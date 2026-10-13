#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(git rev-parse --show-toplevel 2>/dev/null || (cd "$(dirname "$0")/.." && pwd))"
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
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di" \
    "$fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser" \
    "$fixture/feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/data/repository/tranfser" \
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

fixture="$(new_fixture settings-repository-lockdown-removed)"
sed -i.bak \
  's/repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)/repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)/' \
  "$fixture/settings.gradle"
rm -f "$fixture/settings.gradle.bak"
expect_failure "settings repository lockdown removed" "$fixture" \
  "CI/release project repository lockdown"

fixture="$(new_fixture settings-iroha-filter-widened)"
sed -i.bak \
  "s/includeGroup 'org.hyperledger.iroha.sdk'/includeGroupByRegex '.*'/" \
  "$fixture/settings.gradle"
rm -f "$fixture/settings.gradle.bak"
expect_failure "settings staged Iroha filter widened" "$fixture" \
  "central staged Iroha repository filter"

fixture="$(new_fixture materializer-count-weakened)"
sed -i.bak \
  's/EXPECTED_NEGATIVE_SCENARIOS=68/EXPECTED_NEGATIVE_SCENARIOS=67/' \
  "$fixture/scripts/test-iroha-core-jvm-materializer.sh"
rm -f "$fixture/scripts/test-iroha-core-jvm-materializer.sh.bak"
expect_failure "materializer adversarial count weakened" "$fixture" \
  "cross-platform materializer adversarial count"

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
sed -i.bak 's/116992564/unknown/g' "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "release evidence redacted" "$fixture" "pinned blocker evidence marker"

fixture="$(new_fixture live-registry-evidence-redacted)"
sed -i.bak 's/6TEAJqbb8oEPmLncoNiMRbLEK6tw/live-id-redacted/g' \
  "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "live registry evidence redacted" "$fixture" "pinned blocker evidence marker"

fixture="$(new_fixture hardcoded-production-id-claim)"
sed -i.bak 's/Neither identifier may be hard-coded/Either identifier may be hard-coded/' \
  "$fixture/docs/iroha-production-send-readiness.md"
rm -f "$fixture/docs/iroha-production-send-readiness.md.bak"
expect_failure "hard-coded production ID claim" "$fixture" "pinned blocker evidence marker"

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

echo "[iroha-send-readiness-test][android] all adversarial fixtures passed."
