#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-xcm-production-evidence.sh"

fail() {
  echo "[xcm-production-evidence-test][error] $*" >&2
  exit 1
}

write_routes() {
  local file="$1"
  cat >"$file" <<'ROUTES'
# origin destination asset
origin destination DOT
destination origin DOT
ROUTES
}

write_gaps() {
  local file="$1"
  cat >"$file" <<'GAPS'
# origin destination assets reason bridge
origin sora DOT bridge-sora bridgehub
GAPS
}

write_blocked_manifest() {
  local file="$1"
  cat >"$file" <<'JSON'
{
  "schemaVersion": 1,
  "scope": "android-xcm-production-readiness",
  "status": "blocked",
  "releaseEnabled": false,
  "currentState": {
    "requiredExecutableRouteCount": 2,
    "discoveryOnlyRouteCount": 1
  },
  "blockers": [
    "e2e-transfer-evidence-missing",
    "all-routes-executable-gate-not-green",
    "discovery-only-routes-remain"
  ],
  "routeManifests": {
    "requiredExecutableRoutes": "scripts/xcm-required-routes.tsv",
    "discoveryOnlyRoutes": "scripts/xcm-discovery-only-routes.tsv",
    "gapReport": "build/reports/xcm-registry-gap-report.json"
  },
  "readyVerificationCommands": [
    "bash ./scripts/test-xcm-production-evidence-audit.sh",
    "bash ./scripts/audit-xcm-production-evidence.sh --require-ready",
    "bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv",
    "./gradlew :public-shared-features-xcm:testDebugUnitTest",
    "./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'"
  ],
  "requiredEvidenceFields": [
    "originChainId",
    "destinationChainId",
    "assetSymbol",
    "extrinsicHash",
    "sender",
    "recipient",
    "amount",
    "timestamp",
    "environment",
    "operator"
  ],
  "evidence": []
}
JSON
}

write_ready_manifest() {
  local file="$1"
  cat >"$file" <<'JSON'
{
  "schemaVersion": 1,
  "scope": "android-xcm-production-readiness",
  "status": "ready",
  "releaseEnabled": true,
  "currentState": {
    "requiredExecutableRouteCount": 2,
    "discoveryOnlyRouteCount": 0
  },
  "blockers": [
    "e2e-transfer-evidence-missing",
    "all-routes-executable-gate-not-green",
    "discovery-only-routes-remain"
  ],
  "routeManifests": {
    "requiredExecutableRoutes": "scripts/xcm-required-routes.tsv",
    "discoveryOnlyRoutes": "scripts/xcm-discovery-only-routes.tsv",
    "gapReport": "build/reports/xcm-registry-gap-report.json"
  },
  "readyVerificationCommands": [
    "bash ./scripts/test-xcm-production-evidence-audit.sh",
    "bash ./scripts/audit-xcm-production-evidence.sh --require-ready",
    "bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv",
    "./gradlew :public-shared-features-xcm:testDebugUnitTest",
    "./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'"
  ],
  "requiredEvidenceFields": [
    "originChainId",
    "destinationChainId",
    "assetSymbol",
    "extrinsicHash",
    "sender",
    "recipient",
    "amount",
    "timestamp",
    "environment",
    "operator"
  ],
  "evidence": [
    {
      "originChainId": "origin",
      "destinationChainId": "destination",
      "assetSymbol": "DOT",
      "extrinsicHash": "0x1111111111111111111111111111111111111111111111111111111111111111",
      "sender": "sender-origin",
      "recipient": "recipient-destination",
      "amount": "1",
      "timestamp": "2026-06-26T00:00:00Z",
      "environment": "mainnet",
      "operator": "release"
    },
    {
      "originChainId": "destination",
      "destinationChainId": "origin",
      "assetSymbol": "DOT",
      "extrinsicHash": "0x2222222222222222222222222222222222222222222222222222222222222222",
      "sender": "sender-destination",
      "recipient": "recipient-origin",
      "amount": "1",
      "timestamp": "2026-06-26T00:00:00Z",
      "environment": "mainnet",
      "operator": "release"
    }
  ]
}
JSON
}

run_audit() {
  local manifest="$1"
  local routes="$2"
  local gaps="$3"
  shift 3
  bash "$AUDIT_SCRIPT" --evidence "$manifest" --required-route-file "$routes" --discovery-gap-file "$gaps" "$@"
}

expect_failure() {
  local name="$1"
  local expected="$2"
  shift 2
  local output

  set +e
  output="$("$@" 2>&1)"
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

routes="$tmp_dir/routes.tsv"
gaps="$tmp_dir/gaps.tsv"
blocked="$tmp_dir/blocked.json"
ready="$tmp_dir/ready.json"
empty_gaps="$tmp_dir/empty-gaps.tsv"

write_routes "$routes"
write_gaps "$gaps"
write_blocked_manifest "$blocked"
write_ready_manifest "$ready"
printf '# none\n' >"$empty_gaps"

run_audit "$blocked" "$routes" "$gaps" >/dev/null
run_audit "$ready" "$routes" "$empty_gaps" --require-ready >/dev/null

expect_failure "missing production evidence manifest" "XCM production evidence manifest missing" run_audit "$tmp_dir/missing.json" "$routes" "$gaps"

bad_json="$tmp_dir/bad-json.json"
printf '{' >"$bad_json"
expect_failure "invalid production evidence JSON" "must be valid JSON" run_audit "$bad_json" "$routes" "$gaps"

bad_schema="$tmp_dir/bad-schema.json"
cp "$blocked" "$bad_schema"
perl -0pi -e 's/"schemaVersion": 1/"schemaVersion": 2/' "$bad_schema"
expect_failure "bad schema" "schemaVersion must be 1" run_audit "$bad_schema" "$routes" "$gaps"

release_enabled_blocked="$tmp_dir/release-enabled-blocked.json"
cp "$blocked" "$release_enabled_blocked"
perl -0pi -e 's/"releaseEnabled": false/"releaseEnabled": true/' "$release_enabled_blocked"
expect_failure "release enabled while blocked" "releaseEnabled must remain false while status is blocked" run_audit "$release_enabled_blocked" "$routes" "$gaps"

missing_e2e_blocker="$tmp_dir/missing-e2e-blocker.json"
cp "$blocked" "$missing_e2e_blocker"
perl -0pi -e 's/"e2e-transfer-evidence-missing",\n    //' "$missing_e2e_blocker"
expect_failure "blocked evidence missing E2E blocker" "blocked evidence missing blocker e2e-transfer-evidence-missing" run_audit "$missing_e2e_blocker" "$routes" "$gaps"

missing_all_routes_command="$tmp_dir/missing-all-routes-command.json"
cp "$blocked" "$missing_all_routes_command"
perl -0pi -e 's/ --require-all-routes-executable//' "$missing_all_routes_command"
expect_failure "missing all-routes executable command" "readyVerificationCommands missing audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable" run_audit "$missing_all_routes_command" "$routes" "$gaps"

missing_required_field="$tmp_dir/missing-required-field.json"
cp "$blocked" "$missing_required_field"
perl -0pi -e 's/"extrinsicHash",\n    //' "$missing_required_field"
expect_failure "missing required evidence field" "requiredEvidenceFields missing extrinsicHash" run_audit "$missing_required_field" "$routes" "$gaps"

ready_with_gaps="$tmp_dir/ready-with-gaps.json"
write_ready_manifest "$ready_with_gaps"
perl -0pi -e 's/"discoveryOnlyRouteCount": 0/"discoveryOnlyRouteCount": 1/' "$ready_with_gaps"
expect_failure "ready evidence with discovery-only routes" "ready evidence cannot have discovery-only routes remaining" run_audit "$ready_with_gaps" "$routes" "$gaps"

ready_missing_route="$tmp_dir/ready-missing-route.json"
cp "$ready" "$ready_missing_route"
perl -0pi -e 's/,\n    \{\n      "originChainId": "destination".*?\n    \}\n  \]/\n  \]/s' "$ready_missing_route"
expect_failure "ready evidence without all required routes" "ready evidence missing E2E transfer evidence for route destination -> origin DOT" run_audit "$ready_missing_route" "$routes" "$empty_gaps" --require-ready

ready_bad_hash="$tmp_dir/ready-bad-hash.json"
cp "$ready" "$ready_bad_hash"
perl -0pi -e 's/0x1111111111111111111111111111111111111111111111111111111111111111/0x1234/' "$ready_bad_hash"
expect_failure "ready evidence malformed extrinsic hash" "extrinsicHash must be a 0x-prefixed 32-byte hash" run_audit "$ready_bad_hash" "$routes" "$empty_gaps" --require-ready

ready_bad_amount="$tmp_dir/ready-bad-amount.json"
cp "$ready" "$ready_bad_amount"
perl -0pi -e 's/"amount": "1"/"amount": "0"/' "$ready_bad_amount"
expect_failure "ready evidence zero amount" "amount must be greater than zero" run_audit "$ready_bad_amount" "$routes" "$empty_gaps" --require-ready

ready_bad_environment="$tmp_dir/ready-bad-environment.json"
cp "$ready" "$ready_bad_environment"
perl -0pi -e 's/"environment": "mainnet"/"environment": "staging"/' "$ready_bad_environment"
expect_failure "ready evidence bad environment" "environment must be mainnet or testnet" run_audit "$ready_bad_environment" "$routes" "$empty_gaps" --require-ready

ready_unknown_route="$tmp_dir/ready-unknown-route.json"
cp "$ready" "$ready_unknown_route"
perl -0pi -e 's/"destinationChainId": "destination"/"destinationChainId": "unknown"/' "$ready_unknown_route"
expect_failure "ready evidence for untracked route" "route is not declared in required route manifest" run_audit "$ready_unknown_route" "$routes" "$empty_gaps" --require-ready

blocked_stale_counts="$tmp_dir/blocked-stale-counts.json"
cp "$blocked" "$blocked_stale_counts"
perl -0pi -e 's/"requiredExecutableRouteCount": 2/"requiredExecutableRouteCount": 1/' "$blocked_stale_counts"
expect_failure "stale production evidence counts" "currentState.requiredExecutableRouteCount must match required route manifest count 2" run_audit "$blocked_stale_counts" "$routes" "$gaps"

echo "[xcm-production-evidence-test] all assertions passed"
