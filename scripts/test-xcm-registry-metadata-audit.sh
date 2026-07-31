#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-xcm-registry-metadata.sh"

fail() {
  echo "[xcm-registry-test][error] $*" >&2
  exit 1
}

write_registry() {
  local file="$1"
  local asset_fragment="$2"
  local destination_fragment="${3:-}"

  cat >"$file" <<JSON
[
  {
    "chainId": "origin",
    "name": "Origin",
    "ecosystem": "substrate",
    "options": [],
    "xcm": {
      "xcmVersion": "v3",
      "availableAssets": [
        { "id": "dot", "symbol": "DOT" }
      ],
      "availableDestinations": [
        {
          "chainId": "destination",
          "assets": [
            { "id": "dot", "symbol": "DOT", "minAmount": "1"$asset_fragment }
          ]$destination_fragment
        }
      ]
    }
  },
  {
    "chainId": "destination",
    "name": "Destination",
    "ecosystem": "substrate",
    "options": []
  }
]
JSON
}

execution_json() {
  cat <<'JSON'
,
          "execution": {
            "palletName": "PolkadotXcm",
            "callName": "limitedReserveTransferAssets",
            "transferType": "limitedReserveTransferAssets",
            "destinationLocation": { "parents": 1, "interior": "X1(Parachain(2000))" },
            "assetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "beneficiaryLocation": { "parents": 0, "interior": "X1(AccountId32({network: Any, id: <account>}))" },
            "feeAssetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Included", "assetSymbol": "DOT" }
          }
JSON
}

bridge_execution_json() {
  cat <<'JSON'
,
          "execution": {
            "palletName": "PolkadotXcm",
            "callName": "limitedReserveTransferAssets",
            "transferType": "limitedReserveTransferAssets",
            "destinationLocation": { "parents": 1, "interior": "X1(Parachain(2000))" },
            "assetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "beneficiaryLocation": { "parents": 0, "interior": "X1(AccountId32({network: Any, id: <account>}))" },
            "feeAssetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Included", "assetSymbol": "DOT" },
            "bridge": {
              "parachainId": "actual",
              "feeAssetLocation": { "parents": 1, "interior": "Here" },
              "feeAssetItem": 0
            }
          }
JSON
}

bridge_destination_json() {
  printf '%s\n' ', "bridgeParachainId": "expected"'
}

xtokens_execution_json() {
  cat <<'JSON'
,
          "execution": {
            "palletName": "XTokens",
            "callName": "transferMultiasset",
            "transferType": "xTokensTransferMultiasset",
            "argumentShape": "xTokensTransferMultiasset",
            "destinationLocation": { "parents": 1, "interior": "X1(Parachain(2000))" },
            "assetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "beneficiaryLocation": { "parents": 0, "interior": "X1(AccountId32({network: Any, id: <account>}))" },
            "feeAssetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Included", "assetSymbol": "DOT" }
          }
JSON
}

run_audit() {
  local file="$1"
  shift
  bash "$AUDIT_SCRIPT" --registry "$file" "$@"
}

mutate_registry() {
  local file="$1"
  local mutation="$2"
  node - "$file" "$mutation" <<'NODE'
const fs = require('fs');
const [file, mutation] = process.argv.slice(2);
const registry = JSON.parse(fs.readFileSync(file, 'utf8'));
const destination = registry[0].xcm.availableDestinations[0];
const assets = destination.assets;
eval(mutation);
fs.writeFileSync(file, `${JSON.stringify(registry, null, 2)}\n`);
NODE
}

expect_failure() {
  local name="$1"
  local file="$2"
  local expected="$3"
  shift 3
  local output

  set +e
  output="$(run_audit "$file" "$@" 2>&1)"
  local status=$?
  set -e

  if [[ "$status" -eq 0 ]]; then
    echo "$output" >&2
    fail "$name unexpectedly passed"
  fi

  if [[ "$output" != *"$expected"* ]]; then
    echo "$output" >&2
    fail "$name did not report expected text: $expected"
  fi
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

passing="$tmp_dir/passing.json"
write_registry "$passing" "$(execution_json)"
run_audit "$passing" --require-executable >/dev/null
run_audit "$passing" --require-route origin destination DOT >/dev/null
xtokens_passing="$tmp_dir/xtokens-passing.json"
write_registry "$xtokens_passing" "$(xtokens_execution_json)"
run_audit "$xtokens_passing" --require-executable >/dev/null
run_audit "$xtokens_passing" --require-route origin destination DOT >/dev/null
required_route_file="$tmp_dir/required-routes.tsv"
cat >"$required_route_file" <<'ROUTES'
# origin destination asset
origin destination DOT
ROUTES
run_audit "$passing" --require-route-file "$required_route_file" >/dev/null

missing_required_route="$tmp_dir/missing-required-route.json"
write_registry "$missing_required_route" "$(execution_json)"
expect_failure \
  "missing required executable route" \
  "$missing_required_route" \
  "Required executable XCM route missing: origin -> other DOT" \
  --require-route origin other DOT
expect_failure \
  "missing required executable route from file" \
  "$missing_required_route" \
  "Required executable XCM route missing: origin -> other DOT" \
  --require-route-file <(printf 'origin other DOT\n')
missing_required_route_file="$tmp_dir/missing-required-routes.tsv"
expect_failure \
  "missing required route file" \
  "$missing_required_route" \
  "Required route file missing" \
  --require-route-file "$missing_required_route_file"
bad_required_route_file="$tmp_dir/bad-required-routes.tsv"
cat >"$bad_required_route_file" <<'ROUTES'
origin destination DOT extra-column
ROUTES
expect_failure \
  "malformed required route file" \
  "$missing_required_route" \
  "Invalid required route file line 1" \
  --require-route-file "$bad_required_route_file"

missing_execution="$tmp_dir/missing-execution.json"
write_registry "$missing_execution" ""
run_audit "$missing_execution" >/dev/null
gap_report="$tmp_dir/xcm-gap-report.json"
run_audit "$missing_execution" --write-gap-report "$gap_report" >/dev/null
required_gap_file="$tmp_dir/required-gaps.tsv"
cat >"$required_gap_file" <<'GAPS'
# origin destination assets reason bridge
origin destination DOT cross-parachain-native -
GAPS
run_audit "$missing_execution" --require-gap-file "$required_gap_file" >/dev/null
node - "$gap_report" <<'NODE'
const fs = require('fs');
const report = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(report.schemaVersion === 1, 'schemaVersion must be 1');
assert(report.summary.remainingDiscoveryOnlyDestinations === 1, 'remaining destination count must be reported');
assert(report.summary.remainingDiscoveryOnlyRouteAssets === 1, 'remaining route asset count must be reported');
assert(report.summary.executableDestinations === 0, 'executable destination count must be reported');
assert(Array.isArray(report.missingExecutableDestinations), 'missing destination list must be an array');
assert(report.missingExecutableDestinations.length === 1, 'missing destination list must contain one route');

const route = report.missingExecutableDestinations[0];
assert(route.originChainId === 'origin', 'origin chain id must be reported');
assert(route.originName === 'Origin', 'origin name must be reported');
assert(route.destinationChainId === 'destination', 'destination chain id must be reported');
assert(route.destinationName === 'Destination', 'destination name must be reported');
assert(route.reason === 'missingExecutionSpec', 'missing route reason must be explicit');
assert(route.bridgeParachainId === null, 'missing bridge parachain id must be null');
assert(route.assetSymbols.length === 1 && route.assetSymbols[0] === 'DOT', 'asset symbols must be normalized');
NODE
missing_required_gap_file="$tmp_dir/missing-required-gaps.tsv"
expect_failure \
  "missing required gap file" \
  "$missing_execution" \
  "Required gap file missing" \
  --require-gap-file "$missing_required_gap_file"
bad_required_gap_file="$tmp_dir/bad-required-gaps.tsv"
cat >"$bad_required_gap_file" <<'GAPS'
origin destination DOT
GAPS
expect_failure \
  "malformed required gap file" \
  "$missing_execution" \
  "Invalid required gap file line 1" \
  --require-gap-file "$bad_required_gap_file"
empty_gap_file="$tmp_dir/empty-required-gaps.tsv"
printf '# no gaps tracked\n' >"$empty_gap_file"
expect_failure \
  "untracked discovery-only gap" \
  "$missing_execution" \
  "Untracked discovery-only XCM gap: origin -> destination DOT" \
  --require-gap-file "$empty_gap_file"
stale_gap_file="$tmp_dir/stale-required-gaps.tsv"
printf 'origin other DOT cross-parachain-native -\n' >"$stale_gap_file"
expect_failure \
  "stale discovery-only gap" \
  "$missing_execution" \
  "Required discovery-only XCM gap is stale or executable: origin -> other DOT" \
  --require-gap-file "$stale_gap_file"
wrong_gap_reason_file="$tmp_dir/wrong-reason-required-gaps.tsv"
printf 'origin destination DOT bridge-sora -\n' >"$wrong_gap_reason_file"
expect_failure \
  "wrong discovery-only gap reason" \
  "$missing_execution" \
  "Required discovery-only XCM gap reason mismatch" \
  --require-gap-file "$wrong_gap_reason_file"
wrong_gap_bridge_file="$tmp_dir/wrong-bridge-required-gaps.tsv"
printf 'origin destination DOT cross-parachain-native unexpected-bridge\n' >"$wrong_gap_bridge_file"
expect_failure \
  "wrong discovery-only gap bridge" \
  "$missing_execution" \
  "Required discovery-only XCM gap bridge mismatch" \
  --require-gap-file "$wrong_gap_bridge_file"
expect_failure "missing executable route" "$missing_execution" "No executable XCM route metadata found" --require-executable
expect_failure \
  "required route without execution" \
  "$missing_execution" \
  "Required executable XCM route missing: origin -> destination DOT" \
  --require-route origin destination DOT

blocked_gap_parent="$tmp_dir/blocked-gap-parent"
printf locked >"$blocked_gap_parent"
expect_failure \
  "unwritable gap report" \
  "$missing_execution" \
  "Failed to write XCM gap report" \
  --write-gap-report "$blocked_gap_parent/report.json"

all_routes_required="$tmp_dir/all-routes-required.json"
write_registry "$all_routes_required" ""
expect_failure "missing all-route execution" "$all_routes_required" "executable route metadata is required" --require-all-routes-executable
all_routes_gap_report="$tmp_dir/all-routes-gap-report.json"
expect_failure \
  "missing all-route execution with gap report" \
  "$all_routes_required" \
  "executable route metadata is required" \
  --write-gap-report "$all_routes_gap_report" \
  --require-all-routes-executable
node - "$all_routes_gap_report" <<'NODE'
const fs = require('fs');
const report = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
}

assert(report.summary.remainingDiscoveryOnlyDestinations === 1, 'failing all-routes audit must still write the gap report');
assert(report.summary.remainingDiscoveryOnlyRouteAssets === 1, 'failing all-routes audit must preserve the route asset count');
assert(report.missingExecutableDestinations.length === 1, 'failing all-routes audit must preserve missing routes');
assert(report.missingExecutableDestinations[0].reason === 'missingExecutionSpec', 'gap reason must stay explicit');
NODE

bad_argument_shape="$tmp_dir/bad-argument-shape.json"
write_registry "$bad_argument_shape" "$(execution_json)"
perl -0pi -e 's/"transferType": "limitedReserveTransferAssets"/"transferType": "limitedReserveTransferAssets",\n            "argumentShape": "operatorAlias"/' "$bad_argument_shape"
expect_failure "unsupported argument shape" "$bad_argument_shape" "argumentShape is unsupported" --require-executable

bad_polkadot_call_shape="$tmp_dir/bad-polkadot-call-shape.json"
write_registry "$bad_polkadot_call_shape" "$(execution_json)"
perl -0pi -e 's/"callName": "limitedReserveTransferAssets"/"callName": "transferMultiasset"/' "$bad_polkadot_call_shape"
expect_failure "mismatched PolkadotXcm shape" "$bad_polkadot_call_shape" "PolkadotXcm transfer-assets argumentShape callName must match transferType" --require-executable

bad_xtokens_shape="$tmp_dir/bad-xtokens-shape.json"
write_registry "$bad_xtokens_shape" "$(execution_json)"
perl -0pi -e 's/"callName": "limitedReserveTransferAssets"/"callName": "transferMultiasset"/' "$bad_xtokens_shape"
perl -0pi -e 's/"transferType": "limitedReserveTransferAssets"/"transferType": "xTokensTransferMultiasset",\n            "argumentShape": "xTokensTransferMultiasset"/' "$bad_xtokens_shape"
expect_failure "mismatched XTokens shape" "$bad_xtokens_shape" "XTokens transferMultiasset argumentShape requires palletName XTokens" --require-executable

bad_weight="$tmp_dir/bad-weight.json"
write_registry "$bad_weight" "$(execution_json | sed 's/, "proofSize": "65536"//')"
bad_weight_gap_report="$tmp_dir/bad-weight-gap-report.json"
expect_failure "bad limited weight" "$bad_weight" "limited weight requires refTime and proofSize" --require-executable
expect_failure "bad limited weight with gap report" "$bad_weight" "limited weight requires refTime and proofSize" --write-gap-report "$bad_weight_gap_report" --require-executable
[[ ! -e "$bad_weight_gap_report" ]] || fail "validation failure unexpectedly wrote a gap report"

bad_unlimited="$tmp_dir/bad-unlimited.json"
write_registry "$bad_unlimited" "$(execution_json | sed 's/"type": "Limited"/"type": "Unlimited"/')"
expect_failure "bad unlimited weight" "$bad_unlimited" "unlimited weight must not include refTime or proofSize" --require-executable

bad_destination_location="$tmp_dir/bad-destination-location.json"
write_registry "$bad_destination_location" "$(execution_json)"
perl -0pi -e 's/"destinationLocation": \{ "parents": 1, "interior": "X1\(Parachain\(2000\)\)" \},//' "$bad_destination_location"
expect_failure "missing destination location" "$bad_destination_location" "destinationLocation is required" --require-executable

bad_fixed_fee="$tmp_dir/bad-fixed-fee.json"
write_registry "$bad_fixed_fee" "$(execution_json | sed 's/"mode": "Included"/"mode": "Fixed"/')"
expect_failure "bad fixed fee" "$bad_fixed_fee" "fixed destination fee requires amount" --require-executable

bad_estimated_fee="$tmp_dir/bad-estimated-fee.json"
write_registry "$bad_estimated_fee" "$(execution_json | sed 's/"mode": "Included"/"mode": "Estimated"/')"
expect_failure "unsupported estimated fee" "$bad_estimated_fee" "destinationFee: mode is unsupported" --require-executable

bad_xcm_version="$tmp_dir/bad-xcm-version.json"
write_registry "$bad_xcm_version" "$(execution_json)"
perl -0pi -e 's/"xcmVersion": "v3"/"xcmVersion": "three"/' "$bad_xcm_version"
expect_failure "noncanonical XCM version" "$bad_xcm_version" "xcmVersion must use canonical v<number> syntax" --require-executable

padded_xcm_version="$tmp_dir/padded-xcm-version.json"
write_registry "$padded_xcm_version" "$(execution_json)"
perl -0pi -e 's/"xcmVersion": "v3"/"xcmVersion": " v3 "/' "$padded_xcm_version"
expect_failure "whitespace-padded XCM version" "$padded_xcm_version" "xcmVersion must not contain surrounding whitespace" --require-executable

bad_fee_asset_item="$tmp_dir/bad-fee-asset-item.json"
write_registry "$bad_fee_asset_item" "$(execution_json | sed 's/"feeAssetItem": 0/"feeAssetItem": 1/')"
expect_failure "nonzero single-asset fee item" "$bad_fee_asset_item" "single-asset feeAssetItem must be zero" --require-executable

bad_fee_asset_location="$tmp_dir/bad-fee-asset-location.json"
write_registry "$bad_fee_asset_location" "$(execution_json | sed 's/"feeAssetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" }/"feeAssetLocation": { "parents": 1, "interior": "Here" }/')"
expect_failure "mismatched single-asset fee location" "$bad_fee_asset_location" "single-asset feeAssetLocation must equal assetLocation" --require-executable

bad_fee_asset="$tmp_dir/bad-fee-asset.json"
write_registry "$bad_fee_asset" "$(execution_json | sed 's/"assetSymbol": "DOT"/"assetSymbol": "KSM"/')"
expect_failure "destination fee asset mismatch" "$bad_fee_asset" "destinationFee.assetSymbol must match the exact route asset DOT" --require-executable

multi_asset_execution="$tmp_dir/multi-asset-execution.json"
write_registry "$multi_asset_execution" "$(execution_json)"
mutate_registry "$multi_asset_execution" 'assets.push({id:"usdt",symbol:"USDT",minAmount:"1"})'
run_audit "$multi_asset_execution" --require-executable --require-route origin destination DOT >/dev/null
expect_failure "multi-asset missing exact execution" "$multi_asset_execution" "executable route metadata is required for every asset" --require-all-routes-executable

wrong_multi_asset_execution="$tmp_dir/wrong-multi-asset-execution.json"
cp "$multi_asset_execution" "$wrong_multi_asset_execution"
mutate_registry "$wrong_multi_asset_execution" 'assets[1].execution=JSON.parse(JSON.stringify(assets[0].execution))'
expect_failure "multi-asset cannot borrow wrong execution" "$wrong_multi_asset_execution" "destinationFee.assetSymbol must match the exact route asset USDT" --require-executable

duplicate_normalized_asset="$tmp_dir/duplicate-normalized-asset.json"
cp "$multi_asset_execution" "$duplicate_normalized_asset"
mutate_registry "$duplicate_normalized_asset" 'assets.push({id:"xcdot",symbol:"xcDOT",minAmount:"1"})'
expect_failure "normalized duplicate asset ambiguity" "$duplicate_normalized_asset" "duplicate or ambiguous normalized route asset DOT" --require-executable

legacy_destination_execution="$tmp_dir/legacy-destination-execution.json"
write_registry "$legacy_destination_execution" "$(execution_json)"
mutate_registry "$legacy_destination_execution" 'destination.execution=assets[0].execution; delete assets[0].execution'
expect_failure "legacy destination execution" "$legacy_destination_execution" "legacy destination-scoped execution is forbidden" --require-executable

mixed_destination_asset_execution="$tmp_dir/mixed-destination-asset-execution.json"
write_registry "$mixed_destination_asset_execution" "$(execution_json)"
mutate_registry "$mixed_destination_asset_execution" 'destination.execution=JSON.parse(JSON.stringify(assets[0].execution))'
expect_failure "mixed destination and per-asset execution" "$mixed_destination_asset_execution" "legacy destination-scoped execution is forbidden" --require-executable

bad_bridge="$tmp_dir/bad-bridge.json"
write_registry "$bad_bridge" "$(bridge_execution_json)" "$(bridge_destination_json)"
expect_failure "bridge mismatch" "$bad_bridge" "bridge.parachainId must match destination.bridgeParachainId" --require-executable

missing_bridge_execution="$tmp_dir/missing-bridge-execution.json"
write_registry "$missing_bridge_execution" "$(execution_json)"
perl -0pi -e 's/"chainId": "destination",/"chainId": "destination",\n          "bridgeParachainId": "expected",/' "$missing_bridge_execution"
expect_failure "missing bridge execution" "$missing_bridge_execution" "destination.bridgeParachainId requires execution.bridge" --require-executable

unexpected_bridge_execution="$tmp_dir/unexpected-bridge-execution.json"
write_registry "$unexpected_bridge_execution" "$(bridge_execution_json)"
expect_failure "unexpected bridge execution" "$unexpected_bridge_execution" "execution.bridge requires destination.bridgeParachainId" --require-executable

unsupported_bridge_execution="$tmp_dir/unsupported-bridge-execution.json"
write_registry "$unsupported_bridge_execution" "$(bridge_execution_json | sed 's/"parachainId": "actual"/"parachainId": "expected"/')" "$(bridge_destination_json)"
expect_failure "unsupported bridge execution" "$unsupported_bridge_execution" "bridge execution is unsupported until the production transfer engine consumes bridge fee semantics" --require-executable

empty_asset_execution="$tmp_dir/empty-asset-execution.json"
write_registry "$empty_asset_execution" "$(execution_json)"
mutate_registry "$empty_asset_execution" 'destination.assets=[]'
expect_failure "empty destination has no executable asset" "$empty_asset_execution" "No executable XCM route metadata found" --require-executable

bad_min_amount="$tmp_dir/bad-min-amount.json"
write_registry "$bad_min_amount" "$(execution_json)"
perl -0pi -e 's/"minAmount": "1"/"minAmount": "-1"/' "$bad_min_amount"
expect_failure "negative min amount" "$bad_min_amount" "minAmount must be a non-negative integer string" --require-executable

bad_symbol="$tmp_dir/bad-symbol.json"
write_registry "$bad_symbol" "$(execution_json)"
perl -0pi -e 's/"symbol": "DOT", "minAmount"/"symbol": " ", "minAmount"/' "$bad_symbol"
expect_failure "blank route symbol" "$bad_symbol" "symbol must not be blank" --require-executable

bad_account_placeholder="$tmp_dir/bad-account-placeholder.json"
write_registry "$bad_account_placeholder" "$(execution_json)"
perl -0pi -e 's/id: <account>/id: 0x1234/' "$bad_account_placeholder"
expect_failure "missing account placeholder" "$bad_account_placeholder" "AccountId32 must use exact {network: Any, id: <account>} recipient authority" --require-executable

duplicate_account_placeholder="$tmp_dir/duplicate-account-placeholder.json"
write_registry "$duplicate_account_placeholder" "$(execution_json)"
perl -0pi -e 's/id: <account>/id: <account><account>/' "$duplicate_account_placeholder"
expect_failure "duplicate account placeholder" "$duplicate_account_placeholder" "AccountId32 must use exact {network: Any, id: <account>} recipient authority" --require-executable

prefixed_account_placeholder="$tmp_dir/prefixed-account-placeholder.json"
write_registry "$prefixed_account_placeholder" "$(execution_json)"
perl -0pi -e 's/id: <account>/id: prefix<account>/' "$prefixed_account_placeholder"
expect_failure "prefixed AccountId32 placeholder" "$prefixed_account_placeholder" "AccountId32 must use exact {network: Any, id: <account>} recipient authority" --require-executable

suffixed_account_placeholder="$tmp_dir/suffixed-account-placeholder.json"
write_registry "$suffixed_account_placeholder" "$(execution_json)"
perl -0pi -e 's/id: <account>/id: <account>suffix/' "$suffixed_account_placeholder"
expect_failure "suffixed AccountId32 placeholder" "$suffixed_account_placeholder" "AccountId32 must use exact {network: Any, id: <account>} recipient authority" --require-executable

prefixed_account_key20_placeholder="$tmp_dir/prefixed-account-key20-placeholder.json"
write_registry "$prefixed_account_key20_placeholder" "$(execution_json)"
mutate_registry "$prefixed_account_key20_placeholder" 'registry[1].ecosystem="ethereumBased"; registry[1].options=["ethereumBased"]; assets[0].execution.beneficiaryLocation.interior="X1(AccountKey20({network: Any, key: prefix<account>}))"'
expect_failure "prefixed AccountKey20 placeholder" "$prefixed_account_key20_placeholder" "AccountKey20 must use exact {network: Any, key: <account>} recipient authority" --require-executable

suffixed_account_key20_placeholder="$tmp_dir/suffixed-account-key20-placeholder.json"
write_registry "$suffixed_account_key20_placeholder" "$(execution_json)"
mutate_registry "$suffixed_account_key20_placeholder" 'registry[1].ecosystem="ethereumBased"; registry[1].options=["ethereumBased"]; assets[0].execution.beneficiaryLocation.interior="X1(AccountKey20({network: Any, key: <account>suffix}))"'
expect_failure "suffixed AccountKey20 placeholder" "$suffixed_account_key20_placeholder" "AccountKey20 must use exact {network: Any, key: <account>} recipient authority" --require-executable

missing_beneficiary_recipient="$tmp_dir/missing-beneficiary-recipient.json"
write_registry "$missing_beneficiary_recipient" "$(execution_json)"
mutate_registry "$missing_beneficiary_recipient" 'assets[0].execution.beneficiaryLocation={parents:0,interior:"Here"}'
expect_failure "missing beneficiary recipient" "$missing_beneficiary_recipient" "beneficiaryLocation must contain exactly one recipient account junction" --require-executable

recipient_controlled_destination="$tmp_dir/recipient-controlled-destination.json"
write_registry "$recipient_controlled_destination" "$(execution_json)"
mutate_registry "$recipient_controlled_destination" 'assets[0].execution.destinationLocation={parents:0,interior:"X1(AccountId32({network: Any, id: <account>}))"}'
expect_failure "recipient-controlled destination" "$recipient_controlled_destination" "destinationLocation must not contain a recipient account junction" --require-executable

recipient_controlled_asset="$tmp_dir/recipient-controlled-asset.json"
write_registry "$recipient_controlled_asset" "$(execution_json)"
mutate_registry "$recipient_controlled_asset" 'const location={parents:0,interior:"X1(AccountId32({network: Any, id: <account>}))"}; assets[0].execution.assetLocation=location; assets[0].execution.feeAssetLocation=location'
expect_failure "recipient-controlled asset and fee" "$recipient_controlled_asset" "assetLocation must not contain a recipient account junction" --require-executable

evm_beneficiary="$tmp_dir/evm-beneficiary.json"
write_registry "$evm_beneficiary" "$(execution_json)"
mutate_registry "$evm_beneficiary" 'registry[1].ecosystem="ethereumBased"; registry[1].options=["ethereumBased"]; assets[0].execution.beneficiaryLocation.interior="X1(AccountKey20({network: Any, key: <account>}))"'
run_audit "$evm_beneficiary" --require-executable >/dev/null

account_key20_on_substrate="$tmp_dir/account-key20-on-substrate.json"
write_registry "$account_key20_on_substrate" "$(execution_json)"
mutate_registry "$account_key20_on_substrate" 'assets[0].execution.beneficiaryLocation.interior="X1(AccountKey20({network: Any, key: <account>}))"'
expect_failure "AccountKey20 on Substrate destination" "$account_key20_on_substrate" "destination ecosystem requires AccountId32 beneficiary" --require-executable

account_id32_on_evm="$tmp_dir/account-id32-on-evm.json"
write_registry "$account_id32_on_evm" "$(execution_json)"
mutate_registry "$account_id32_on_evm" 'registry[1].ecosystem="ethereumBased"; registry[1].options=["ethereumBased"]'
expect_failure "AccountId32 on EVM destination" "$account_id32_on_evm" "destination ecosystem requires AccountKey20 beneficiary" --require-executable

inconsistent_origin_account_width="$tmp_dir/inconsistent-origin-account-width.json"
write_registry "$inconsistent_origin_account_width" "$(execution_json)"
mutate_registry "$inconsistent_origin_account_width" 'registry[0].options=["ethereumBased"]'
expect_failure "inconsistent origin account width" "$inconsistent_origin_account_width" "origin chain ecosystem and account-width metadata must agree" --require-executable

testnet_destination="$tmp_dir/testnet-destination.json"
write_registry "$testnet_destination" "$(execution_json)"
mutate_registry "$testnet_destination" 'registry[1].options=["testnet"]'
expect_failure "testnet executable destination" "$testnet_destination" "destination chain must not be a testnet" --require-executable

missing_destination_chain="$tmp_dir/missing-destination-chain.json"
write_registry "$missing_destination_chain" "$(execution_json)"
mutate_registry "$missing_destination_chain" 'registry.pop()'
expect_failure "missing executable destination chain" "$missing_destination_chain" "destination chain metadata is missing" --require-executable

duplicate_destination_chain="$tmp_dir/duplicate-destination-chain.json"
write_registry "$duplicate_destination_chain" "$(execution_json)"
mutate_registry "$duplicate_destination_chain" 'registry.push(JSON.parse(JSON.stringify(registry[1])))'
expect_failure "duplicate executable destination chain" "$duplicate_destination_chain" "Registry contains duplicate chain id: destination" --require-executable

bad_junction_count="$tmp_dir/bad-junction-count.json"
write_registry "$bad_junction_count" "$(execution_json)"
perl -0pi -e 's/X2\(Parachain\(1000\), GeneralKey\(dot\)\)/X2(Parachain(1000))/' "$bad_junction_count"
expect_failure "bad junction count" "$bad_junction_count" "declares X2 but contains 1 junctions" --require-executable

bad_unsupported_junction="$tmp_dir/bad-unsupported-junction.json"
write_registry "$bad_unsupported_junction" "$(execution_json)"
perl -0pi -e 's/GeneralKey\(dot\)/Unsupported(1)/' "$bad_unsupported_junction"
expect_failure "unsupported junction" "$bad_unsupported_junction" "has unsupported junction Unsupported" --require-executable

echo "[xcm-registry-test] all tests passed"
