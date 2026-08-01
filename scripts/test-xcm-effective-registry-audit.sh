#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
AUDIT_SCRIPT="$ROOT_DIR/scripts/audit-xcm-effective-registry.sh"
TMP_DIR="$(mktemp -d)"
BASE_ROOT="$TMP_DIR/base"
CASE_ROOT=""
TEST_COUNT=0

cleanup() {
  rm -rf "$TMP_DIR"
}
trap cleanup EXIT

mkdir -p \
  "$BASE_ROOT/runtime/src/main/assets" \
  "$BASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain" \
  "$BASE_ROOT/runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain" \
  "$BASE_ROOT/scripts" \
  "$BASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain" \
  "$BASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di"

cp "$ROOT_DIR/runtime/src/main/assets/local_chains.json" "$BASE_ROOT/runtime/src/main/assets/local_chains.json"
cp "$ROOT_DIR/runtime/src/main/assets/approved_xcm_routes.tsv" "$BASE_ROOT/runtime/src/main/assets/approved_xcm_routes.tsv"
cp "$ROOT_DIR/runtime/build.gradle" "$BASE_ROOT/runtime/build.gradle"
cp "$ROOT_DIR/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/XcmDiscoverySnapshotProvider.kt" \
  "$BASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/XcmDiscoverySnapshotProvider.kt"
cp "$ROOT_DIR/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt" \
  "$BASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt"
cp "$ROOT_DIR/runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncServiceTest.kt" \
  "$BASE_ROOT/runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncServiceTest.kt"
cp "$ROOT_DIR/scripts/xcm-required-routes.tsv" "$BASE_ROOT/scripts/xcm-required-routes.tsv"
cp "$ROOT_DIR/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt" \
  "$BASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
cp "$ROOT_DIR/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmExecutionSpec.kt" \
  "$BASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmExecutionSpec.kt"
cp "$ROOT_DIR/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmEntitiesFetcher.kt" \
  "$BASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmEntitiesFetcher.kt"
cp "$ROOT_DIR/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt" \
  "$BASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
cp "$ROOT_DIR/feature-wallet-impl/build.gradle" "$BASE_ROOT/feature-wallet-impl/build.gradle"

new_case() {
  local name="$1"
  CASE_ROOT="$TMP_DIR/case-$name"
  mkdir -p "$CASE_ROOT"
  cp -R "$BASE_ROOT/." "$CASE_ROOT"
}

run_audit() {
  local root="$1"
  shift
  XCM_EFFECTIVE_REGISTRY_ROOT="$root" bash "$AUDIT_SCRIPT" "$@"
}

expect_pass() {
  local label="$1"
  local root="$2"
  shift 2
  TEST_COUNT=$((TEST_COUNT + 1))
  local output
  if ! output="$(run_audit "$root" "$@" 2>&1)"; then
    echo "[xcm-effective-registry-test][error] expected success: $label" >&2
    echo "$output" >&2
    exit 1
  fi
}

expect_failure() {
  local label="$1"
  local expected="$2"
  local root="$3"
  shift 3
  TEST_COUNT=$((TEST_COUNT + 1))
  local output
  if output="$(run_audit "$root" "$@" 2>&1)"; then
    echo "[xcm-effective-registry-test][error] expected failure: $label" >&2
    echo "$output" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* ]]; then
    echo "[xcm-effective-registry-test][error] wrong failure for $label; expected: $expected" >&2
    echo "$output" >&2
    exit 1
  fi
}

expect_failure_without() {
  local label="$1"
  local expected="$2"
  local forbidden="$3"
  local root="$4"
  shift 4
  TEST_COUNT=$((TEST_COUNT + 1))
  local output
  if output="$(run_audit "$root" "$@" 2>&1)"; then
    echo "[xcm-effective-registry-test][error] expected failure: $label" >&2
    exit 1
  fi
  if [[ "$output" != *"$expected"* || "$output" == *"$forbidden"* ]]; then
    echo "[xcm-effective-registry-test][error] unsafe failure output for $label" >&2
    echo "$output" >&2
    exit 1
  fi
}

assert_equal() {
  local label="$1"
  local expected="$2"
  local actual="$3"
  TEST_COUNT=$((TEST_COUNT + 1))
  if [[ "$actual" != "$expected" ]]; then
    echo "[xcm-effective-registry-test][error] $label: expected '$expected', got '$actual'" >&2
    exit 1
  fi
}

file_sha256() {
  node -e 'const fs=require("fs"),crypto=require("crypto"); process.stdout.write(crypto.createHash("sha256").update(fs.readFileSync(process.argv[1])).digest("hex"))' "$1"
}

mutate_registry() {
  local file="$1"
  local mutation="$2"
  node - "$file" "$mutation" <<'NODE'
const fs = require('fs');
const [file, mutation] = process.argv.slice(2);
const parsed = JSON.parse(fs.readFileSync(file, 'utf8'));
const chains = Array.isArray(parsed) ? parsed : parsed.chains;
const executable = [];
const destinations = [];
for (const origin of chains) {
  for (const destination of origin?.xcm?.availableDestinations || []) {
    const destinationItem = {
      origin,
      destination,
      destinationChain: chains.find((chain) => chain.chainId === destination.chainId)
    };
    destinations.push(destinationItem);
    for (const asset of destination.assets || []) {
      if (asset.execution != null) executable.push({...destinationItem, asset});
    }
  }
}
eval(mutation);
fs.writeFileSync(file, `${JSON.stringify(parsed, null, 2)}\n`);
NODE
}

approved_file() {
  printf '%s/runtime/src/main/assets/approved_xcm_routes.tsv' "$1"
}

required_file() {
  printf '%s/scripts/xcm-required-routes.tsv' "$1"
}

registry_file() {
  printf '%s/runtime/src/main/assets/local_chains.json' "$1"
}

new_case baseline
expect_pass "offline baseline" "$CASE_ROOT" --write-report "$CASE_ROOT/report.json"
assert_equal "offline report count" "15:15:0:0" \
  "$(jq -r '[.summary.approved,.summary.effective,.summary.missing,.summary.extra] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "disabled production has zero executable routes" "0:0" \
  "$(jq -r '[.summary.productionExecutable,([.routes[] | select(.productionExecutable == true)] | length)] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "offline trust policy" "apk-approved-intersection:false:false:false" \
  "$(jq -r '[.policy.transactionAuthority,.policy.remoteExecutionTrusted,.policy.productionTransfersEnabled,.policy.unapprovedDiscoveryRoutesExecutable] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "runtime discovery requires a successful current-process sync" "narrowing-advisory-only:current-process-successful-sync-snapshot:true:false:false:0" \
  "$(jq -r '[.policy.runtimeDiscoveryRole,.policy.runtimeDiscoveryStorage,.policy.runtimeDiscoveryRequiresSuccessfulProcessSync,.policy.runtimeDiscoverySnapshotBoundToReport,.policy.runtimeDiscoveryFreshnessEnforced,.summary.productionExecutable] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "offline report SHA shape" "64:64:64" \
  "$(jq -r '[.inputs.approvedRoutes.sha256,.inputs.requiredRoutes.sha256,.inputs.bundledRegistry.sha256] | map(length) | join(":")' "$CASE_ROOT/report.json")"
run_audit "$CASE_ROOT" --write-report "$CASE_ROOT/report-2.json" >/dev/null
assert_equal "deterministic offline report" "identical" \
  "$([[ "$(file_sha256 "$CASE_ROOT/report.json")" == "$(file_sha256 "$CASE_ROOT/report-2.json")" ]] && echo identical || echo different)"

new_case unknown-option
expect_failure "unknown option" "Unknown argument" "$CASE_ROOT" --not-an-option
expect_failure "require-all without discovery" "requires an explicit discovery input" "$CASE_ROOT" --require-all-approved
expect_failure "mutually exclusive discovery" "mutually exclusive" "$CASE_ROOT" \
  --discovery-registry "$(registry_file "$CASE_ROOT")" \
  --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json

new_case approved-missing
rm "$(approved_file "$CASE_ROOT")"
expect_failure "missing approved manifest" "approved route manifest missing" "$CASE_ROOT"

new_case approved-empty
: > "$(approved_file "$CASE_ROOT")"
expect_failure "empty approved manifest" "must contain at least one route" "$CASE_ROOT"

new_case approved-columns
printf '%s\n' 'bad bad' >> "$(approved_file "$CASE_ROOT")"
expect_failure "approved malformed columns" "must contain exactly" "$CASE_ROOT"

new_case approved-origin
perl -0pi -e 's/91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3/not-a-chain/' "$(approved_file "$CASE_ROOT")"
expect_failure "approved malformed origin" "malformed origin chain identity" "$CASE_ROOT"

new_case approved-destination
perl -0pi -e 's/68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f/not-a-chain/' "$(approved_file "$CASE_ROOT")"
expect_failure "approved malformed destination" "malformed destination chain identity" "$CASE_ROOT"

new_case approved-self-route
node - "$(approved_file "$CASE_ROOT")" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
const index = lines.findIndex((line) => line && !line.startsWith('#'));
const parts = lines[index].trim().split(/\s+/);
lines[index] = `${parts[0]} ${parts[0]} ${parts[2]}`;
fs.writeFileSync(file, lines.join('\n'));
NODE
expect_failure "approved self route" "origin and destination chains must differ" "$CASE_ROOT"

new_case approved-symbol
perl -0pi -e 's/ DOT/ dot/' "$(approved_file "$CASE_ROOT")"
expect_failure "approved noncanonical symbol" "asset symbol must be canonical uppercase" "$CASE_ROOT"

new_case approved-duplicate
awk '!/^#/ && NF { print; exit }' "$(approved_file "$CASE_ROOT")" >> "$(approved_file "$CASE_ROOT")"
expect_failure "approved duplicate" "contains duplicate route" "$CASE_ROOT"

new_case approved-missing-route
perl -ni -e 'print unless /68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f DOT$/' "$(approved_file "$CASE_ROOT")"
expect_failure "approved missing required route" "outside approved manifest" "$CASE_ROOT"

new_case approved-extra-route
printf '%s\n' '91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3 9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed DOT' >> "$(approved_file "$CASE_ROOT")"
expect_failure "approved extra route" "required manifest missing approved manifest route" "$CASE_ROOT"

new_case required-missing
rm "$(required_file "$CASE_ROOT")"
expect_failure "missing required manifest" "required route manifest missing" "$CASE_ROOT"

new_case required-duplicate
awk '!/^#/ && NF { print; exit }' "$(required_file "$CASE_ROOT")" >> "$(required_file "$CASE_ROOT")"
expect_failure "required duplicate" "contains duplicate route" "$CASE_ROOT"

new_case required-drift
perl -0pi -e 's/(91b171bb158e2d3848fa23a9f1c25182fb8e20313b2c1eb49219da7a70ce90c3 68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f) DOT/$1 KSM/' "$(required_file "$CASE_ROOT")"
expect_failure "required route drift" "required manifest missing approved manifest route" "$CASE_ROOT"

new_case malformed-json
printf '%s\n' '{' > "$(registry_file "$CASE_ROOT")"
expect_failure "malformed bundled JSON" "must be valid JSON" "$CASE_ROOT"

new_case malformed-root
printf '%s\n' '{"chains":"wrong"}' > "$(registry_file "$CASE_ROOT")"
expect_failure "malformed registry root" "root must be an array" "$CASE_ROOT"

new_case duplicate-chain
mutate_registry "$(registry_file "$CASE_ROOT")" 'chains.push(JSON.parse(JSON.stringify(chains[0])))'
expect_failure "duplicate bundled chain" "duplicate chain identity" "$CASE_ROOT"

new_case missing-execution
mutate_registry "$(registry_file "$CASE_ROOT")" 'delete executable[0].asset.execution'
expect_failure "approved route loses execution" "bundled executable registry missing approved manifest route" "$CASE_ROOT"

new_case extra-execution
mutate_registry "$(registry_file "$CASE_ROOT")" 'const candidate = destinations.flatMap((it) => (it.destination.assets || []).map((asset) => ({...it,asset}))).find((it) => it.asset.execution == null && it.destination.bridgeParachainId == null); candidate.asset.execution = JSON.parse(JSON.stringify(executable[0].asset.execution)); candidate.asset.execution.destinationFee.assetSymbol=candidate.asset.symbol'
expect_failure "unapproved executable route" "outside approved manifest" "$CASE_ROOT"

new_case multi-asset
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets.push({id:"extra-asset",symbol:"BAD"})'
expect_pass "approved exact asset remains valid in multi-asset destination" "$CASE_ROOT" --write-report "$CASE_ROOT/report.json"
assert_equal "multi-asset destination keeps exact approved count" "15" "$(jq -r .summary.approved "$CASE_ROOT/report.json")"

new_case multi-asset-wrong-spec
mutate_registry "$(registry_file "$CASE_ROOT")" 'const extra={id:"extra-asset",symbol:"BAD",execution:JSON.parse(JSON.stringify(executable[0].asset.execution))}; executable[0].destination.assets.push(extra)'
expect_failure "sibling asset cannot borrow approved spec" "destination fee asset must match exact route asset" "$CASE_ROOT"

new_case legacy-destination-execution
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.execution=JSON.parse(JSON.stringify(executable[0].asset.execution))'
expect_failure "legacy destination authority rejected" "legacy destination-scoped execution is forbidden" "$CASE_ROOT"

new_case duplicate-destination
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].origin.xcm.availableDestinations.push(JSON.parse(JSON.stringify(executable[0].destination)))'
expect_failure "duplicate bundled destination" "duplicate destination identity" "$CASE_ROOT"

new_case duplicate-asset
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets.push(JSON.parse(JSON.stringify(executable[0].destination.assets[0])))'
expect_failure "duplicate bundled asset" "duplicate normalized asset identity" "$CASE_ROOT"

new_case blank-asset-id
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets[0].id=""'
expect_failure "blank route asset id" "id must be a non-blank" "$CASE_ROOT"

new_case padded-asset-id
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets[0].id=` ${executable[0].destination.assets[0].id}`'
expect_failure "padded route asset id" "id must be a non-blank trimmed public identifier" "$CASE_ROOT"

new_case blank-symbol
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets[0].symbol=""'
expect_failure "blank route asset symbol" "symbol must be a non-blank" "$CASE_ROOT"

new_case malformed-minimum
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets[0].minAmount="01"'
expect_failure "malformed route minimum" "minAmount must be a canonical" "$CASE_ROOT"

new_case padded-minimum
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.assets[0].minAmount=` ${executable[0].destination.assets[0].minAmount}`'
expect_failure "padded route minimum" "minAmount must be a canonical" "$CASE_ROOT"

new_case blank-version
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].origin.xcm.xcmVersion=""'
expect_failure "blank XCM version" "xcmVersion is required" "$CASE_ROOT"

new_case noncanonical-version
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].origin.xcm.xcmVersion="v0"'
expect_failure "noncanonical executable XCM version" "xcmVersion must be canonical v1 or greater" "$CASE_ROOT"

new_case padded-version
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].origin.xcm.xcmVersion=`${executable[0].origin.xcm.xcmVersion} `'
expect_failure "padded executable XCM version" "xcmVersion must be null or a non-blank trimmed string" "$CASE_ROOT"

new_case nonzero-fee-asset-index
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].asset.execution.feeAssetItem=1'
expect_failure "single-asset route uses nonzero fee asset index" "feeAssetItem must be 0" "$CASE_ROOT"

new_case fee-location-drift
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].asset.execution.feeAssetLocation={parents:7,interior:"Here"}'
expect_failure "single-asset fee location differs from asset location" "feeAssetLocation must equal assetLocation" "$CASE_ROOT"

new_case malformed-parent
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destinationChain.parentId="not-a-parent"'
expect_failure "malformed approved parent" "parentId must be a canonical chain identity" "$CASE_ROOT"

new_case malformed-para
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destinationChain.paraId="01"'
expect_failure "malformed approved parachain" "paraId must be a canonical" "$CASE_ROOT"

new_case bridge-route
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].destination.bridgeParachainId="e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738"'
expect_failure "unsupported approved bridge" "executable bridge routes are not supported" "$CASE_ROOT"

new_case estimated-fee
mutate_registry "$(registry_file "$CASE_ROOT")" 'executable[0].asset.execution.destinationFee.mode="ESTIMATED"'
expect_failure "unsupported estimated fee" "estimated destination fee is not supported" "$CASE_ROOT"

new_case full-discovery
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
expect_pass "full local discovery intersection" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved --write-report "$CASE_ROOT/report.json"
assert_equal "full discovery effective count" "15:0" \
  "$(jq -r '[.summary.effective,.summary.missing] | join(":")' "$CASE_ROOT/report.json")"
FULL_DISCOVERY_EXTRA="$(jq -r '.summary.extra' "$CASE_ROOT/report.json")"
assert_equal "full discovery has unapproved discovery-only routes" "true" \
  "$([[ "$FULL_DISCOVERY_EXTRA" -gt 0 ]] && echo true || echo false)"

new_case discovery-removal
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'for (const item of executable.slice(0, 2)) { item.origin.xcm.availableDestinations = item.origin.xcm.availableDestinations.filter((candidate) => candidate !== item.destination) }'
expect_failure "require all reports remote removal" "approved route is not effective" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved --write-report "$CASE_ROOT/report.json"
assert_equal "failed require-all still writes deterministic counts" "13:2" \
  "$(jq -r '[.summary.effective,.summary.missing] | join(":")' "$CASE_ROOT/report.json")"
expect_pass "partial discovery allowed without require-all" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --write-report "$CASE_ROOT/report-partial.json"

new_case remote-addition
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'const added=JSON.parse(JSON.stringify(executable[0].destination)); added.chainId="9f28c6a68e0fc9646eff64935684f6eeeece527e37bbe1f213d22caa1d9d6bed"; delete added.execution; executable[0].origin.xcm.availableDestinations.push(added)'
expect_pass "remote addition remains discovery-only" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved --write-report "$CASE_ROOT/report.json"
assert_equal "remote addition does not expand authority" "15:$((FULL_DISCOVERY_EXTRA + 1)):false" \
  "$(jq -r '[.summary.effective,.summary.extra,.policy.unapprovedDiscoveryRoutesExecutable] | join(":")' "$CASE_ROOT/report.json")"

new_case remote-execution-injection
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'for (const item of destinations) { item.destination.execution="attacker-controlled-invalid-execution"; for (const asset of item.destination.assets || []) asset.execution="attacker-controlled-invalid-execution"; }'
expect_pass "remote execution injection ignored" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved --write-report "$CASE_ROOT/report.json"
assert_equal "remote execution stays untrusted" "false:15" \
  "$(jq -r '[.policy.remoteExecutionTrusted,.summary.effective] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "compatible discovery routes remain production-disabled" "0:0" \
  "$(jq -r '[.summary.productionExecutable,([.routes[] | select(.productionExecutable == true)] | length)] | join(":")' "$CASE_ROOT/report.json")"
assert_equal "release digest is not misrepresented as the runtime process snapshot" "true:false:false:0" \
  "$(jq -r '[.policy.runtimeDiscoveryRequiresSuccessfulProcessSync,.policy.runtimeDiscoverySnapshotBoundToReport,.policy.runtimeDiscoveryFreshnessEnforced,.summary.productionExecutable] | join(":")' "$CASE_ROOT/report.json")"

new_case remote-multi-asset
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets.push({id:"remote-extra",symbol:"BAD"})'
expect_pass "remote multi-asset route selects exact approved asset" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved --write-report "$CASE_ROOT/report.json"
assert_equal "remote multi-asset retains all approved routes" "15:0" \
  "$(jq -r '[.summary.effective,.summary.missing] | join(":")' "$CASE_ROOT/report.json")"

new_case remote-asset-id
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets[0].id="remote-drift"'
expect_failure "remote asset id drift" "asset-id-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-symbol
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets[0].symbol="BAD"'
expect_failure "remote asset symbol drift" "asset-symbol-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-minimum
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets[0].minAmount="1"'
expect_failure "remote minimum drift" "min-amount-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-padded-minimum
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets[0].minAmount=`${executable[0].destination.assets[0].minAmount} `'
expect_failure "remote padded minimum rejected" "minAmount must be a canonical" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-padded-asset-id
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.assets[0].id=`${executable[0].destination.assets[0].id} `'
expect_failure "remote padded asset identity rejected" "id must be a non-blank trimmed public identifier" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-origin-asset-id
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'const symbol=executable[0].destination.assets[0].symbol.toLowerCase(); const asset=executable[0].origin.assets.find((candidate) => candidate.symbol.toLowerCase().replace(/^xc/,"")===symbol.toLowerCase().replace(/^xc/,"")); asset.id="remote-origin-asset-drift"'
expect_failure "remote origin core asset id drift" "origin-asset-id-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-origin-asset-precision
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'const symbol=executable[0].destination.assets[0].symbol.toLowerCase(); const asset=executable[0].origin.assets.find((candidate) => candidate.symbol.toLowerCase().replace(/^xc/,"")===symbol.toLowerCase().replace(/^xc/,"")); asset.precision += 1'
expect_failure "remote origin core asset precision drift" "origin-asset-precision-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-origin-asset-symbol
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'const symbol=executable[0].destination.assets[0].symbol.toLowerCase(); const asset=executable[0].origin.assets.find((candidate) => candidate.symbol.toLowerCase().replace(/^xc/,"")===symbol.toLowerCase().replace(/^xc/,"")); asset.symbol="BAD"'
expect_failure "remote origin core asset symbol drift" "origin-core-asset-not-single" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-version
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].origin.xcm.xcmVersion="v4"'
expect_failure "remote XCM version drift" "origin-xcm-version-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-padded-version
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].origin.xcm.xcmVersion=` ${executable[0].origin.xcm.xcmVersion}`'
expect_failure "remote padded XCM version rejected" "xcmVersion must be null or a non-blank trimmed string" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-xcm-chain
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].origin.xcm.chainId="b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"'
expect_failure "remote XCM chain drift" "origin-xcm-chain-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-parent
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destinationChain.parentId="b0a8d493285c2df73290dfb7e61f870f17b41801197a149ca93654499ea3dafe"'
expect_failure "remote parent drift" "destination-parent-chain-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-para
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destinationChain.paraId="999"'
expect_failure "remote parachain drift" "destination-parachain-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-bridge
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.bridgeParachainId="e92d165ad41e41e215d09713788173aecfdbe34d3bed29409d33a2ef03980738"'
expect_failure "remote bridge drift" "bridge-parachain-mismatch" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json" --require-all-approved

new_case remote-duplicate-chain
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'chains.push(JSON.parse(JSON.stringify(chains[0])))'
expect_failure "remote duplicate chain identity" "duplicate chain identity" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json"

new_case remote-duplicate-destination
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].origin.xcm.availableDestinations.push(JSON.parse(JSON.stringify(executable[0].destination)))'
expect_failure "remote duplicate destination identity" "duplicate destination identity" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json"

new_case remote-malformed-identity
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
mutate_registry "$CASE_ROOT/discovery.json" 'executable[0].destination.chainId="not-a-chain"'
expect_failure "remote malformed route identity" "malformed destination chain identity" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json"

new_case remote-malformed-json
printf '%s\n' '{' > "$CASE_ROOT/discovery.json"
expect_failure "remote malformed JSON" "must be valid JSON" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json"

new_case oversized-discovery
dd if=/dev/zero of="$CASE_ROOT/discovery.json" bs=1048576 count=9 status=none
expect_failure "oversized local discovery" "exceeds 8388608 bytes" "$CASE_ROOT" \
  --discovery-registry "$CASE_ROOT/discovery.json"

new_case digest-change
cp "$(registry_file "$CASE_ROOT")" "$CASE_ROOT/discovery.json"
run_audit "$CASE_ROOT" --discovery-registry "$CASE_ROOT/discovery.json" --write-report "$CASE_ROOT/before.json" >/dev/null
printf '\n' >> "$CASE_ROOT/discovery.json"
run_audit "$CASE_ROOT" --discovery-registry "$CASE_ROOT/discovery.json" --write-report "$CASE_ROOT/after.json" >/dev/null
assert_equal "discovery digest changes with exact bytes" "changed" \
  "$([[ "$(jq -r .inputs.discoveryRegistry.sha256 "$CASE_ROOT/before.json")" != "$(jq -r .inputs.discoveryRegistry.sha256 "$CASE_ROOT/after.json")" ]] && echo changed || echo unchanged)"
assert_equal "discovery byte length attests appended byte" "1" \
  "$(( $(jq -r .inputs.discoveryRegistry.byteLength "$CASE_ROOT/after.json") - $(jq -r .inputs.discoveryRegistry.byteLength "$CASE_ROOT/before.json") ))"
assert_equal "content-only change leaves effective set deterministic" \
  "$(jq -c .summary "$CASE_ROOT/before.json")" "$(jq -c .summary "$CASE_ROOT/after.json")"

new_case url-validation
expect_failure_without "URL credentials rejected without leak" "must not contain credentials" "super-secret" "$CASE_ROOT" \
  --discovery-url 'https://user:super-secret@raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json'
expect_failure "URL scheme rejected" "must use HTTPS" "$CASE_ROOT" \
  --discovery-url 'http://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json'
expect_failure "URL query rejected" "must not contain a query" "$CASE_ROOT" \
  --discovery-url 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json?token=nope'
expect_failure "URL fragment rejected" "must not contain a fragment" "$CASE_ROOT" \
  --discovery-url 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json#fragment'
expect_failure "URL nondefault port rejected" "default HTTPS port" "$CASE_ROOT" \
  --discovery-url 'https://raw.githubusercontent.com:444/soramitsu/shared-features-utils/master/chains/v13/chains.json'
expect_failure "URL host rejected" "outside the bounded" "$CASE_ROOT" \
  --discovery-url 'https://example.com/soramitsu/shared-features-utils/master/chains/v13/chains.json'
expect_failure "URL branch/path rejected" "outside the bounded" "$CASE_ROOT" \
  --discovery-url 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/develop/chains/v13/chains.json'
expect_failure "malformed URL rejected" "discovery URL is malformed" "$CASE_ROOT" \
  --discovery-url 'not a URL'

new_case registry-source-missing
rm "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
expect_failure "missing approved registry source" "approved XCM registry source missing" "$CASE_ROOT"

new_case loader-marker
perl -0pi -e 's/ApprovedXcmRouteRegistryLoader/RemovedRegistryLoader/g' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
expect_failure "approved loader marker removed" "missing ApprovedXcmRouteRegistryLoader" "$CASE_ROOT"

new_case loader-input-marker
perl -0pi -e 's/approvedRoutesTsv/removedRoutesInput/g' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
expect_failure "approved loader input binding removed" "must bind bundledChainsJson and approvedRoutesTsv" "$CASE_ROOT"

new_case execution-validator-source-missing
rm "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmExecutionSpec.kt"
expect_failure "missing approved execution validator source" "approved XCM execution validator source missing" "$CASE_ROOT"

new_case execution-validator-binding
perl -0pi -e 's/asset = asset/asset = removedAsset/' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
expect_failure "approved exact route asset binding removed" "must pass the exact route asset" "$CASE_ROOT"

new_case execution-validator-asset-authority
perl -0pi -e 's/asset\.execution/removedAsset.execution/' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmExecutionSpec.kt"
expect_failure "approved exact route asset authority removed" "must select execution from the exact route asset" "$CASE_ROOT"

new_case legacy-destination-rejection-marker
perl -0pi -e 's/destination\.execution == null/destination.execution != null/' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt"
expect_failure "legacy destination rejection marker removed" "must explicitly reject legacy destination execution" "$CASE_ROOT"

new_case fetcher-registry-marker
perl -0pi -e 's/private val approvedRoutes: ApprovedXcmRouteRegistry/private val approvedRoutes: RemovedRegistry/' \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmEntitiesFetcher.kt"
expect_failure "fetcher approved dependency removed" "missing approved registry field" "$CASE_ROOT"

new_case fetcher-remote-execution
printf '%s\n' 'private val forbiddenRemoteExecution = destination.execution' >> \
  "$CASE_ROOT/public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmEntitiesFetcher.kt"
expect_failure "fetcher remote execution marker" "must ignore mutable remote destination and asset execution" "$CASE_ROOT"

new_case di-loader
perl -0pi -e 's/ApprovedXcmRouteRegistryLoader\.load/RemovedRegistryLoader.load/' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "DI approved loader removed" "wallet DI must load" "$CASE_ROOT"

new_case di-approved-asset
perl -0pi -e 's/approved_xcm_routes\.tsv/removed_routes.tsv/' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "DI approved asset removed" "missing approved_xcm_routes.tsv" "$CASE_ROOT"

new_case di-approved-provider-guard
perl -0pi -e 's/if \(!BuildConfig\.ENABLE_PRODUCTION_XCM_TRANSFERS\)/if (false)/' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "DI approved registry early guard removed" "must return unavailable before asset loading" "$CASE_ROOT"

new_case discovery-provider-interface
perl -0pi -e 's/interface XcmDiscoverySnapshotProvider/interface RemovedDiscoverySnapshotProvider/' \
  "$CASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/XcmDiscoverySnapshotProvider.kt"
expect_failure "current-process discovery provider removed" "missing XcmDiscoverySnapshotProvider interface" "$CASE_ROOT"

new_case discovery-clear-before-sync
perl -0pi -e 's/currentProcessXcmDiscoveryChains = null/currentProcessXcmDiscoveryChains = emptyList()/' \
  "$CASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt"
expect_failure "discovery snapshot not cleared before sync" "must clear before sync" "$CASE_ROOT"

new_case discovery-snapshot-not-volatile
perl -0pi -e 's/\@Volatile\n//' \
  "$CASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt"
expect_failure "published discovery snapshot loses visibility guarantee" "must be volatile" "$CASE_ROOT"

new_case discovery-failure-swallowed
perl -0pi -e 's/val remoteChains = configChainsSyncUp\(\)/val remoteChains = runCatching { configChainsSyncUp() }.getOrElse { emptyList() }/' \
  "$CASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt"
expect_failure "discovery sync failure swallowed" "must propagate XCM discovery sync failure" "$CASE_ROOT"

new_case discovery-success-not-published
perl -0pi -e 's/currentProcessXcmDiscoveryChains = remoteChains\.toList\(\)/currentProcessXcmDiscoveryChains = null/' \
  "$CASE_ROOT/runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt"
expect_failure "discovery snapshot not published after success" "publish only after full successful sync" "$CASE_ROOT"

new_case discovery-room-fallback-di
perl -0pi -e 's/chainSyncService: ChainSyncService/chainRegistry: ChainRegistry/; s/XcmEntitiesFetcher\(chainSyncService, approvedRoutes\)/XcmEntitiesFetcher(chainRegistry, approvedRoutes)/' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "production discovery falls back to ChainRegistry Room state" "forbid ChainRegistry/Room fallback" "$CASE_ROOT"

new_case discovery-failure-test-removed
perl -0pi -e 's/failed refresh propagates and clears current process XCM discovery snapshot/removed failed refresh test/' \
  "$CASE_ROOT/runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncServiceTest.kt"
expect_failure "failed-refresh clearing test removed" "missing failed-refresh clearing case" "$CASE_ROOT"

new_case discovery-in-flight-test-removed
perl -0pi -e 's/in flight refresh exposes empty XCM snapshot without blocking readers/removed in flight refresh test/' \
  "$CASE_ROOT/runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncServiceTest.kt"
expect_failure "nonblocking in-flight refresh test removed" "missing nonblocking in-flight refresh case" "$CASE_ROOT"

new_case di-flag
perl -0pi -e 's/BuildConfig\.ENABLE_PRODUCTION_XCM_TRANSFERS/true/' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "DI production flag removed" "missing production XCM flag guard" "$CASE_ROOT"

new_case di-fallback
perl -0pi -e 's/UnavailableXcmTransferEngine/RemovedUnavailableEngine/g' \
  "$CASE_ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_failure "DI unavailable fallback removed" "fail closed to UnavailableXcmTransferEngine" "$CASE_ROOT"

new_case release-true
perl -0pi -e 's/(release \{.*?ENABLE_PRODUCTION_XCM_TRANSFERS"\s*,\s*)"false"/${1}"true"/s' \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "release flag forced true while blocked" "hardcoded false" "$CASE_ROOT"

new_case release-malformed
perl -0pi -e 's/(release \{.*?ENABLE_PRODUCTION_XCM_TRANSFERS"\s*,\s*)"false"/${1}"truthy"/s' \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "release malformed flag" "hardcoded false" "$CASE_ROOT"

new_case release-env-bypass
printf '%s\n' 'def bypass = System.getenv("ENABLE_PRODUCTION_XCM_TRANSFERS")' >> \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "release environment bypass" "must not accept an environment/property override" "$CASE_ROOT"

new_case release-property-bypass
printf '%s\n' 'def bypass = providers.gradleProperty("ENABLE_PRODUCTION_XCM_TRANSFERS")' >> \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "release property bypass" "must not accept an environment/property override" "$CASE_ROOT"

new_case duplicate-build-field
perl -0pi -e 's/(buildConfigField "boolean", "ENABLE_PRODUCTION_XCM_TRANSFERS", "false")/$1\n            $1/' \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "duplicate release BuildConfig field" "exactly one hardcoded false" "$CASE_ROOT"

new_case debug-input
perl -0pi -e 's/ENABLE_DEBUG_XCM_TRANSFERS/REMOVED_DEBUG_XCM_TRANSFERS/g' \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "debug-only input removed" "distinct ENABLE_DEBUG_XCM_TRANSFERS" "$CASE_ROOT"

new_case debug-validation
perl -0pi -e 's/throw new GradleException/throw new RuntimeException/' \
  "$CASE_ROOT/feature-wallet-impl/build.gradle"
expect_failure "debug malformed value rejection removed" "must reject malformed boolean values" "$CASE_ROOT"

new_case release-url-drift
perl -0pi -e 's#shared-features-utils/master/chains/v13/chains\.json#shared-features-utils/develop/chains/v13/chains.json#' \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime release discovery URL drift" "must be exactly one hardcoded canonical production discovery URL" "$CASE_ROOT"

new_case release-url-specific-override
printf '%s\n' 'def releaseUrlBypass = readOptionalSecret("CHAINS_URL_RELEASE_OVERRIDE")' >> \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime release-specific discovery override" "must not accept CHAINS_URL_RELEASE_OVERRIDE" "$CASE_ROOT"

new_case release-url-generic-override
printf '%s\n' 'def releaseUrlBypass = readOptionalSecret("CHAINS_URL_OVERRIDE")' >> \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime generic discovery override outside debug" "generic CHAINS_URL_OVERRIDE is forbidden" "$CASE_ROOT"

new_case release-url-indirect-alias
printf '%s\n' 'def aliasedReleaseRegistry = providers.gradleProperty("UNRELATED_REGISTRY_INPUT")' >> \
  "$CASE_ROOT/runtime/build.gradle"
perl -0pi -e 's#\\"https://raw\.githubusercontent\.com/soramitsu/shared-features-utils/master/chains/v13/chains\.json\\"#\\"\${aliasedReleaseRegistry}\\"#' \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime aliased release discovery input" "must be exactly one hardcoded canonical production discovery URL" "$CASE_ROOT"

new_case release-url-duplicate-field
perl -0pi -e 's#(buildConfigField "String", "CHAINS_URL", "\\"https://raw\.githubusercontent\.com/soramitsu/shared-features-utils/master/chains/v13/chains\.json\\"")#$1\n            $1#' \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime duplicate release discovery field" "must be exactly one hardcoded canonical production discovery URL" "$CASE_ROOT"

new_case release-url-extra-build-type-field
printf '%s\n' 'buildConfigField "String", "CHAINS_URL", "\\"https://example.invalid/chains.json\\""' >> \
  "$CASE_ROOT/runtime/build.gradle"
expect_failure "runtime extra discovery BuildConfig field" "only be defined for explicit debug and release build types" "$CASE_ROOT"

new_case report-symlink-destination
mkdir -p "$CASE_ROOT/output"
touch "$CASE_ROOT/real-report.json"
ln -s "$CASE_ROOT/real-report.json" "$CASE_ROOT/output/report.json"
expect_failure "report symlink destination" "regular non-symlink file" "$CASE_ROOT" \
  --write-report "$CASE_ROOT/output/report.json"
assert_equal "report symlink failure leaves no temp" "0" \
  "$(find "$CASE_ROOT/output" -name 'report.json.tmp-*' | wc -l | tr -d ' ')"

new_case report-symlink-ancestor
mkdir -p "$CASE_ROOT/real-output"
ln -s "$CASE_ROOT/real-output" "$CASE_ROOT/output-link"
expect_failure "report symlink ancestor" "ancestor must be a real directory" "$CASE_ROOT" \
  --write-report "$CASE_ROOT/output-link/report.json"
assert_equal "report ancestor failure leaves no temp" "0" \
  "$(find "$CASE_ROOT/real-output" -name 'report.json.tmp-*' | wc -l | tr -d ' ')"

new_case report-directory-destination
mkdir -p "$CASE_ROOT/output/report.json"
expect_failure "report directory destination" "regular non-symlink file" "$CASE_ROOT" \
  --write-report "$CASE_ROOT/output/report.json"

echo "[xcm-effective-registry-test] all tests passed ($TEST_COUNT adversarial scenarios)"
