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
  local destination_fragment="$2"

  cat >"$file" <<JSON
[
  {
    "chainId": "origin",
    "name": "Origin",
    "xcm": {
      "xcmVersion": "v3",
      "availableAssets": [
        { "id": "dot", "symbol": "DOT" }
      ],
      "availableDestinations": [
        {
          "chainId": "destination",
          "assets": [
            { "id": "dot", "symbol": "DOT", "minAmount": "1" }
          ]$destination_fragment
        }
      ]
    }
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
            "feeAssetLocation": { "parents": 1, "interior": "Here" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Estimated", "assetSymbol": "DOT" }
          }
JSON
}

bridge_execution_json() {
  cat <<'JSON'
,
          "bridgeParachainId": "expected",
          "execution": {
            "palletName": "PolkadotXcm",
            "callName": "limitedReserveTransferAssets",
            "transferType": "limitedReserveTransferAssets",
            "destinationLocation": { "parents": 1, "interior": "X1(Parachain(2000))" },
            "assetLocation": { "parents": 1, "interior": "X2(Parachain(1000), GeneralKey(dot))" },
            "beneficiaryLocation": { "parents": 0, "interior": "X1(AccountId32({network: Any, id: <account>}))" },
            "feeAssetLocation": { "parents": 1, "interior": "Here" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Estimated", "assetSymbol": "DOT" },
            "bridge": {
              "parachainId": "actual",
              "feeAssetLocation": { "parents": 1, "interior": "Here" },
              "feeAssetItem": 0
            }
          }
JSON
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
            "feeAssetLocation": { "parents": 1, "interior": "Here" },
            "feeAssetItem": 0,
            "weightLimit": { "type": "Limited", "refTime": "1000000000", "proofSize": "65536" },
            "destinationFee": { "mode": "Estimated", "assetSymbol": "DOT" }
          }
JSON
}

run_audit() {
  local file="$1"
  shift
  bash "$AUDIT_SCRIPT" --registry "$file" "$@"
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
assert(report.summary.executableDestinations === 0, 'executable destination count must be reported');
assert(Array.isArray(report.missingExecutableDestinations), 'missing destination list must be an array');
assert(report.missingExecutableDestinations.length === 1, 'missing destination list must contain one route');

const route = report.missingExecutableDestinations[0];
assert(route.originChainId === 'origin', 'origin chain id must be reported');
assert(route.originName === 'Origin', 'origin name must be reported');
assert(route.destinationChainId === 'destination', 'destination chain id must be reported');
assert(route.destinationName === null, 'unknown destination name must be null');
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
write_registry "$bad_fixed_fee" "$(execution_json | sed 's/"mode": "Estimated"/"mode": "Fixed"/')"
expect_failure "bad fixed fee" "$bad_fixed_fee" "fixed destination fee requires amount" --require-executable

bad_estimated_fee="$tmp_dir/bad-estimated-fee.json"
write_registry "$bad_estimated_fee" "$(execution_json | sed 's/"assetSymbol": "DOT"/"assetSymbol": "DOT", "amount": "1"/')"
expect_failure "bad estimated fee amount" "$bad_estimated_fee" "amount is only valid for fixed destination fees" --require-executable

bad_bridge="$tmp_dir/bad-bridge.json"
write_registry "$bad_bridge" "$(bridge_execution_json)"
expect_failure "bridge mismatch" "$bad_bridge" "bridge.parachainId must match destination.bridgeParachainId" --require-executable

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
expect_failure "missing account placeholder" "$bad_account_placeholder" "AccountId32 must include <account> recipient placeholder" --require-executable

bad_junction_count="$tmp_dir/bad-junction-count.json"
write_registry "$bad_junction_count" "$(execution_json)"
perl -0pi -e 's/X2\(Parachain\(1000\), GeneralKey\(dot\)\)/X2(Parachain(1000))/' "$bad_junction_count"
expect_failure "bad junction count" "$bad_junction_count" "declares X2 but contains 1 junctions" --require-executable

bad_unsupported_junction="$tmp_dir/bad-unsupported-junction.json"
write_registry "$bad_unsupported_junction" "$(execution_json)"
perl -0pi -e 's/GeneralKey\(dot\)/Unsupported(1)/' "$bad_unsupported_junction"
expect_failure "unsupported junction" "$bad_unsupported_junction" "has unsupported junction Unsupported" --require-executable

echo "[xcm-registry-test] all tests passed"
