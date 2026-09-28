#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
AUDIT_SCRIPT="$SCRIPT_DIR/audit-xcm-production-evidence.sh"
ANDROID_COMMIT="0123456789abcdef0123456789abcdef01234567"
STALE_ANDROID_COMMIT="89abcdef89abcdef89abcdef89abcdef89abcdef"
TEST_COUNT=0

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
    "independent-on-chain-verification-missing",
    "all-routes-executable-gate-not-green",
    "discovery-only-routes-remain"
  ],
  "routeManifests": {
    "requiredExecutableRoutes": "scripts/xcm-required-routes.tsv",
    "approvedExecutableRoutes": "runtime/src/main/assets/approved_xcm_routes.tsv",
    "discoveryOnlyRoutes": "scripts/xcm-discovery-only-routes.tsv",
    "gapReport": "build/reports/xcm-registry-gap-report.json",
    "effectiveRegistryReport": "build/reports/xcm-effective-registry-report.json"
  },
  "readyVerificationCommands": [
    "bash ./scripts/test-xcm-production-evidence-template.sh",
    "bash ./scripts/test-xcm-production-evidence-audit.sh",
    "bash ./scripts/test-xcm-effective-registry-audit.sh",
    "bash ./scripts/audit-xcm-effective-registry.sh --write-report build/reports/xcm-effective-registry-report.json",
    "bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready",
    "bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv",
    "./gradlew :public-shared-features-xcm:testDebugUnitTest --tests 'jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistryTest'",
    "./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'"
  ],
  "requiredEvidenceFields": [
    "originChainId",
    "destinationChainId",
    "assetSymbol",
    "extrinsicHash",
    "originBlockHash",
    "originBlockNumber",
    "originFinalized",
    "originExtrinsicSucceeded",
    "sender",
    "recipient",
    "amount",
    "timestamp",
    "destinationBlockHash",
    "destinationBlockNumber",
    "destinationEventSucceeded",
    "destinationBalanceDelta",
    "originVerificationUrl",
    "destinationVerificationUrl",
    "verificationMethod",
    "verifiedAt",
    "independentVerifier",
    "environment",
    "operator",
    "androidCommit"
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
  "lastReviewed": "2026-06-26",
  "currentState": {
    "requiredExecutableRouteCount": 2,
    "discoveryOnlyRouteCount": 0
  },
  "blockers": [],
  "routeManifests": {
    "requiredExecutableRoutes": "scripts/xcm-required-routes.tsv",
    "approvedExecutableRoutes": "runtime/src/main/assets/approved_xcm_routes.tsv",
    "discoveryOnlyRoutes": "scripts/xcm-discovery-only-routes.tsv",
    "gapReport": "build/reports/xcm-registry-gap-report.json",
    "effectiveRegistryReport": "build/reports/xcm-effective-registry-report.json"
  },
  "readyVerificationCommands": [
    "bash ./scripts/test-xcm-production-evidence-template.sh",
    "bash ./scripts/test-xcm-production-evidence-audit.sh",
    "bash ./scripts/test-xcm-effective-registry-audit.sh",
    "bash ./scripts/audit-xcm-effective-registry.sh --write-report build/reports/xcm-effective-registry-report.json",
    "bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready",
    "bash ./scripts/audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable --require-route-file scripts/xcm-required-routes.tsv --require-gap-file scripts/xcm-discovery-only-routes.tsv",
    "./gradlew :public-shared-features-xcm:testDebugUnitTest --tests 'jp.co.soramitsu.xcm.domain.ApprovedXcmRouteRegistryTest'",
    "./gradlew :runtime:testDebugUnitTest --tests 'jp.co.soramitsu.runtime.multiNetwork.chain.remote.XcmLocalChainsRegistryTest'"
  ],
  "requiredEvidenceFields": [
    "originChainId",
    "destinationChainId",
    "assetSymbol",
    "extrinsicHash",
    "originBlockHash",
    "originBlockNumber",
    "originFinalized",
    "originExtrinsicSucceeded",
    "sender",
    "recipient",
    "amount",
    "timestamp",
    "destinationBlockHash",
    "destinationBlockNumber",
    "destinationEventSucceeded",
    "destinationBalanceDelta",
    "originVerificationUrl",
    "destinationVerificationUrl",
    "verificationMethod",
    "verifiedAt",
    "independentVerifier",
    "environment",
    "operator",
    "androidCommit"
  ],
  "evidence": [
    {
      "originChainId": "origin",
      "destinationChainId": "destination",
      "assetSymbol": "DOT",
      "extrinsicHash": "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
      "originBlockHash": "0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
      "originBlockNumber": "100",
      "originFinalized": true,
      "originExtrinsicSucceeded": true,
      "sender": "sender-origin",
      "recipient": "recipient-destination",
      "amount": "1",
      "timestamp": "2026-06-26T00:00:00Z",
      "destinationBlockHash": "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
      "destinationBlockNumber": "101",
      "destinationEventSucceeded": true,
      "destinationBalanceDelta": "1",
      "originVerificationUrl": "https://polkadot.subscan.io/extrinsic/0x0123456789abcdef",
      "destinationVerificationUrl": "https://assethub-polkadot.subscan.io/block/101",
      "verificationMethod": "canonical-rpc-and-explorer",
      "verifiedAt": "2026-06-26T00:05:00Z",
      "independentVerifier": "security-reviewer",
      "environment": "mainnet",
      "operator": "release",
      "androidCommit": "0123456789abcdef0123456789abcdef01234567"
    },
    {
      "originChainId": "destination",
      "destinationChainId": "origin",
      "assetSymbol": "DOT",
      "extrinsicHash": "0xfedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
      "originBlockHash": "0x89abcdef0123456789abcdef0123456789abcdef0123456789abcdef01234567",
      "originBlockNumber": "200",
      "originFinalized": true,
      "originExtrinsicSucceeded": true,
      "sender": "sender-destination",
      "recipient": "recipient-origin",
      "amount": "1",
      "timestamp": "2026-06-26T00:00:00Z",
      "destinationBlockHash": "0x76543210fedcba9876543210fedcba9876543210fedcba9876543210fedcba98",
      "destinationBlockNumber": "201",
      "destinationEventSucceeded": true,
      "destinationBalanceDelta": "1",
      "originVerificationUrl": "https://kusama.subscan.io/extrinsic/0xfedcba9876543210",
      "destinationVerificationUrl": "https://assethub-kusama.subscan.io/block/201",
      "verificationMethod": "canonical-rpc-and-explorer",
      "verifiedAt": "2026-06-26T00:05:00Z",
      "independentVerifier": "independent-reviewer",
      "environment": "mainnet",
      "operator": "release",
      "androidCommit": "0123456789abcdef0123456789abcdef01234567"
    }
  ]
}
JSON
}

write_effective_registry_report() {
  local file="$1"
  local routes_file="$2"
  local approved_file="$3"
  local bundled_registry_file="$4"
  node - "$file" "$routes_file" "$approved_file" "$bundled_registry_file" <<'NODE'
const crypto = require('crypto');
const fs = require('fs');
const [file, routesFile, approvedFile, bundledRegistryFile] = process.argv.slice(2);
const identity = (source, sourceFile) => {
  const content = fs.readFileSync(sourceFile);
  return {
    source,
    byteLength: content.length,
    sha256: crypto.createHash('sha256').update(content).digest('hex')
  };
};
const routes = fs.readFileSync(routesFile, 'utf8')
  .split(/\r?\n/u)
  .map((line) => line.replace(/\s+#.*$/u, '').trim())
  .filter((line) => line && !line.startsWith('#'))
  .map((line) => {
    const [originChainId, destinationChainId, rawAssetSymbol] = line.split(/\s+/u);
    const assetSymbol = rawAssetSymbol.replace(/^xc/iu, '').toUpperCase();
    return { originChainId, destinationChainId, assetSymbol, effective: true, productionExecutable: false, reasons: [] };
  });
const report = {
  schemaVersion: 1,
  mode: 'discovery',
  status: 'complete',
  policy: {
    transactionAuthority: 'apk-approved-intersection',
    effectiveRouteMeaning: 'compatible-approved-candidate',
    remoteExecutionTrusted: false,
    productionTransfersEnabled: false,
    unapprovedDiscoveryRoutesExecutable: false,
    runtimeDiscoveryRole: 'narrowing-advisory-only',
    runtimeDiscoveryStorage: 'current-process-successful-sync-snapshot',
    runtimeDiscoveryRequiresSuccessfulProcessSync: true,
    runtimeDiscoverySnapshotBoundToReport: false,
    runtimeDiscoveryFreshnessEnforced: false,
    releaseDiscoveryUrl: 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json'
  },
  inputs: {
    approvedRoutes: identity('runtime/src/main/assets/approved_xcm_routes.tsv', approvedFile),
    requiredRoutes: identity('scripts/xcm-required-routes.tsv', routesFile),
    bundledRegistry: identity('runtime/src/main/assets/local_chains.json', bundledRegistryFile),
    discoveryRegistry: {
      kind: 'https',
      source: 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json',
      byteLength: 128,
      sha256: '0123456789abcdef'.repeat(4)
    }
  },
  summary: {
    approved: routes.length,
    required: routes.length,
    bundledExecutable: routes.length,
    discovered: routes.length,
    effective: routes.length,
    productionExecutable: 0,
    missing: 0,
    extra: 0
  },
  routes,
  missing: [],
  extra: []
};
fs.writeFileSync(file, `${JSON.stringify(report, null, 2)}\n`);
NODE
}

run_audit() {
  local manifest="$1"
  local routes="$2"
  local gaps="$3"
  shift 3
  run_audit_with_approved "$manifest" "$routes" "$approved" "$gaps" "$@"
}

run_audit_with_approved() {
  local manifest="$1"
  local routes="$2"
  local approved_routes="$3"
  local gaps="$4"
  shift 4
  run_audit_with_effective_report "$manifest" "$routes" "$approved_routes" "$gaps" "$effective_report" "$@"
}

run_audit_with_effective_report() {
  local manifest="$1"
  local routes="$2"
  local approved_routes="$3"
  local gaps="$4"
  local report="$5"
  shift 5
  XCM_PRODUCTION_EXPECTED_COMMIT="${XCM_PRODUCTION_EXPECTED_COMMIT:-$ANDROID_COMMIT}" \
    bash "$AUDIT_SCRIPT" --evidence "$manifest" --required-route-file "$routes" --approved-route-file "$approved_routes" --discovery-gap-file "$gaps" --effective-registry-report "$report" "$@"
}

expect_failure() {
  local name="$1"
  local expected="$2"
  shift 2
  TEST_COUNT=$((TEST_COUNT + 1))
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

expect_failure_without() {
  local name="$1"
  local expected="$2"
  local forbidden="$3"
  shift 3
  TEST_COUNT=$((TEST_COUNT + 1))
  local output

  set +e
  output="$("$@" 2>&1)"
  local status=$?
  set -e

  if [[ "$status" -eq 0 || "$output" != *"$expected"* || "$output" == *"$forbidden"* ]]; then
    echo "$output" >&2
    fail "$name did not fail safely with: $expected"
  fi
}

set_evidence_field() {
  local file="$1"
  local index="$2"
  local field="$3"
  local json_value="$4"
  node - "$file" "$index" "$field" "$json_value" <<'NODE'
const fs = require('fs');
const [file, index, field, jsonValue] = process.argv.slice(2);
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[Number(index)][field] = JSON.parse(jsonValue);
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
}

delete_evidence_field() {
  local file="$1"
  local index="$2"
  local field="$3"
  node - "$file" "$index" "$field" <<'NODE'
const fs = require('fs');
const [file, index, field] = process.argv.slice(2);
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
delete manifest.evidence[Number(index)][field];
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
}

mutate_effective_report() {
  local output="$1"
  local mutation="$2"
  cp "$effective_report" "$output"
  node - "$output" "$mutation" <<'NODE'
const fs = require('fs');
const [file, mutation] = process.argv.slice(2);
const data = JSON.parse(fs.readFileSync(file, 'utf8'));
Function('data', `"use strict"; ${mutation}`)(data);
fs.writeFileSync(file, `${JSON.stringify(data, null, 2)}\n`);
NODE
}

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

routes="$tmp_dir/routes.tsv"
approved="$tmp_dir/approved.tsv"
gaps="$tmp_dir/gaps.tsv"
blocked="$tmp_dir/blocked.json"
ready="$tmp_dir/ready.json"
empty_gaps="$tmp_dir/empty-gaps.tsv"
effective_report="$tmp_dir/effective-registry-report.json"
bundled_registry="$SCRIPT_DIR/../runtime/src/main/assets/local_chains.json"

write_routes "$routes"
cp "$routes" "$approved"
write_gaps "$gaps"
write_blocked_manifest "$blocked"
write_ready_manifest "$ready"
printf '# none\n' >"$empty_gaps"
write_effective_registry_report "$effective_report" "$routes" "$approved" "$bundled_registry"

run_audit "$blocked" "$routes" "$gaps" >/dev/null
run_audit "$ready" "$routes" "$empty_gaps" --require-ready >/dev/null
TEST_COUNT=$((TEST_COUNT + 2))

same_day_last_reviewed_boundary="$tmp_dir/same-day-last-reviewed-boundary.json"
cp "$ready" "$same_day_last_reviewed_boundary"
set_evidence_field "$same_day_last_reviewed_boundary" 0 timestamp '"2026-06-26T23:59:58Z"'
set_evidence_field "$same_day_last_reviewed_boundary" 0 verifiedAt '"2026-06-26T23:59:59Z"'
run_audit "$same_day_last_reviewed_boundary" "$routes" "$empty_gaps" --require-ready >/dev/null
TEST_COUNT=$((TEST_COUNT + 1))

expect_failure "ready evidence missing effective registry report" "effective registry report missing" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$tmp_dir/missing-effective-report.json" --require-ready

effective_report_target="$tmp_dir/effective-report-target.json"
cp "$effective_report" "$effective_report_target"
effective_report_symlink="$tmp_dir/effective-report-symlink.json"
ln -s "$effective_report_target" "$effective_report_symlink"
expect_failure "ready evidence symlinked effective registry report" "effective registry report must be a regular non-symlink file" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$effective_report_symlink" --require-ready

invalid_effective_report="$tmp_dir/invalid-effective-report.json"
printf '{\n' >"$invalid_effective_report"
expect_failure "ready evidence invalid effective registry JSON" "effective registry report must be valid JSON" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$invalid_effective_report" --require-ready

bad_effective_schema="$tmp_dir/bad-effective-schema.json"
mutate_effective_report "$bad_effective_schema" "data.schemaVersion = 2"
expect_failure "ready evidence bad effective registry schema" "effective registry report schemaVersion must be 1" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$bad_effective_schema" --require-ready

bundled_effective_report="$tmp_dir/bundled-effective-report.json"
mutate_effective_report "$bundled_effective_report" "data.mode = 'bundled'; data.inputs.discoveryRegistry = null"
expect_failure "ready evidence bundled effective registry report" "ready effective registry report mode must be discovery" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$bundled_effective_report" --require-ready

incomplete_effective_report="$tmp_dir/incomplete-effective-report.json"
mutate_effective_report "$incomplete_effective_report" "data.status = 'incomplete'"
expect_failure "ready evidence incomplete effective registry report" "ready effective registry report status must be complete" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$incomplete_effective_report" --require-ready

trusted_remote_effective_report="$tmp_dir/trusted-remote-effective-report.json"
mutate_effective_report "$trusted_remote_effective_report" "data.policy.remoteExecutionTrusted = true"
expect_failure "ready evidence trusted remote execution report" "effective registry report policy.remoteExecutionTrusted must be false" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$trusted_remote_effective_report" --require-ready

unbound_process_sync_report="$tmp_dir/unbound-process-sync-report.json"
mutate_effective_report "$unbound_process_sync_report" "data.policy.runtimeDiscoveryRequiresSuccessfulProcessSync = false"
expect_failure "ready evidence process sync bypass report" "effective registry report policy.runtimeDiscoveryRequiresSuccessfulProcessSync must be true" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$unbound_process_sync_report" --require-ready

file_discovery_report="$tmp_dir/file-discovery-report.json"
mutate_effective_report "$file_discovery_report" "data.inputs.discoveryRegistry.kind = 'file'"
expect_failure "ready evidence file discovery report" "ready effective registry report discovery kind must be https" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$file_discovery_report" --require-ready

attacker_discovery_report="$tmp_dir/attacker-discovery-report.json"
mutate_effective_report "$attacker_discovery_report" "data.inputs.discoveryRegistry.source = 'https://attacker.invalid/chains.json'"
expect_failure "ready evidence attacker discovery report" "ready effective registry report discovery source must be" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$attacker_discovery_report" --require-ready

malformed_discovery_hash_report="$tmp_dir/malformed-discovery-hash-report.json"
mutate_effective_report "$malformed_discovery_hash_report" "data.inputs.discoveryRegistry.sha256 = 'invalid'"
expect_failure "ready evidence malformed discovery hash report" "ready effective registry report discovery sha256 must be a lowercase SHA-256" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$malformed_discovery_hash_report" --require-ready

stale_approved_hash_report="$tmp_dir/stale-approved-hash-report.json"
mutate_effective_report "$stale_approved_hash_report" "data.inputs.approvedRoutes.sha256 = '0'.repeat(64)"
expect_failure "ready evidence stale approved hash report" "effective registry report inputs.approvedRoutes.sha256 must match current source bytes" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$stale_approved_hash_report" --require-ready

stale_required_length_report="$tmp_dir/stale-required-length-report.json"
mutate_effective_report "$stale_required_length_report" "data.inputs.requiredRoutes.byteLength += 1"
expect_failure "ready evidence stale required length report" "effective registry report inputs.requiredRoutes.byteLength must match current source bytes" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$stale_required_length_report" --require-ready

stale_bundled_hash_report="$tmp_dir/stale-bundled-hash-report.json"
mutate_effective_report "$stale_bundled_hash_report" "data.inputs.bundledRegistry.sha256 = '0'.repeat(64)"
expect_failure "ready evidence stale bundled hash report" "effective registry report inputs.bundledRegistry.sha256 must match current source bytes" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$stale_bundled_hash_report" --require-ready

effective_count_drift_report="$tmp_dir/effective-count-drift-report.json"
mutate_effective_report "$effective_count_drift_report" "data.summary.effective -= 1"
expect_failure "ready evidence effective count drift report" "ready effective registry report summary.effective must match every approved/required route" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$effective_count_drift_report" --require-ready

production_executable_report="$tmp_dir/production-executable-report.json"
mutate_effective_report "$production_executable_report" "data.summary.productionExecutable = 1; data.routes[0].productionExecutable = true"
expect_failure "ready evidence production executable report" "productionExecutable must remain false while the release flag is disabled" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$production_executable_report" --require-ready

missing_approved_effective_route_report="$tmp_dir/missing-approved-effective-route-report.json"
mutate_effective_report "$missing_approved_effective_route_report" "data.routes.pop(); data.summary.effective -= 1; data.summary.discovered -= 1"
expect_failure "ready evidence effective route omitted report" "ready effective registry report missing approved route" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$missing_approved_effective_route_report" --require-ready

ineffective_route_report="$tmp_dir/ineffective-route-report.json"
mutate_effective_report "$ineffective_route_report" "data.routes[0].effective = false; data.routes[0].reasons = ['not-discovered']"
expect_failure "ready evidence ineffective approved route report" "ready effective registry report route must be effective" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$ineffective_route_report" --require-ready

missing_routes_report="$tmp_dir/missing-routes-report.json"
mutate_effective_report "$missing_routes_report" "data.missing = [{originChainId: 'origin', destinationChainId: 'destination', assetSymbol: 'DOT', reasons: ['not-discovered']}]; data.summary.missing = 1"
expect_failure "ready evidence report carries missing routes" "ready effective registry report missing routes must be empty" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$missing_routes_report" --require-ready

discovered_count_drift_report="$tmp_dir/discovered-count-drift-report.json"
mutate_effective_report "$discovered_count_drift_report" "data.summary.discovered += 1"
expect_failure "ready evidence discovered count drift report" "ready effective registry report summary.discovered must equal effective plus extra routes" \
  run_audit_with_effective_report "$ready" "$routes" "$approved" "$empty_gaps" "$discovered_count_drift_report" --require-ready

expect_failure "missing approved route manifest" "route file missing" \
  run_audit_with_approved "$blocked" "$routes" "$tmp_dir/missing-approved.tsv" "$gaps"

approved_missing="$tmp_dir/approved-missing.tsv"
head -n 2 "$approved" >"$approved_missing"
expect_failure "approved manifest missing required route" "approved route manifest missing required route" \
  run_audit_with_approved "$blocked" "$routes" "$approved_missing" "$gaps"

approved_extra="$tmp_dir/approved-extra.tsv"
cp "$approved" "$approved_extra"
printf '%s\n' 'third fourth DOT' >>"$approved_extra"
expect_failure "approved manifest route outside required" "required route manifest missing approved route" \
  run_audit_with_approved "$blocked" "$routes" "$approved_extra" "$gaps"

approved_duplicate="$tmp_dir/approved-duplicate.tsv"
cp "$approved" "$approved_duplicate"
printf '%s\n' 'origin destination xcDOT' >>"$approved_duplicate"
expect_failure "approved manifest duplicate normalized route" "approved route manifest contains duplicate normalized routes" \
  run_audit_with_approved "$blocked" "$routes" "$approved_duplicate" "$gaps"

expect_failure "missing production evidence manifest" "XCM production evidence manifest missing" run_audit "$tmp_dir/missing.json" "$routes" "$gaps"

bad_json="$tmp_dir/bad-json.json"
printf '{' >"$bad_json"
expect_failure "invalid production evidence JSON" "must be valid JSON" run_audit "$bad_json" "$routes" "$gaps"

bad_schema="$tmp_dir/bad-schema.json"
cp "$blocked" "$bad_schema"
perl -0pi -e 's/"schemaVersion": 1/"schemaVersion": 2/' "$bad_schema"
expect_failure "bad schema" "schemaVersion must be 1" run_audit "$bad_schema" "$routes" "$gaps"

bad_last_reviewed="$tmp_dir/bad-last-reviewed.json"
cp "$blocked" "$bad_last_reviewed"
perl -0pi -e 's/"releaseEnabled": false,/"releaseEnabled": false,\n  "lastReviewed": "today",/' "$bad_last_reviewed"
expect_failure "bad lastReviewed date" "lastReviewed must be YYYY-MM-DD" run_audit "$bad_last_reviewed" "$routes" "$gaps"

invalid_calendar_last_reviewed="$tmp_dir/invalid-calendar-last-reviewed.json"
cp "$blocked" "$invalid_calendar_last_reviewed"
perl -0pi -e 's/"releaseEnabled": false,/"releaseEnabled": false,\n  "lastReviewed": "2026-02-31",/' "$invalid_calendar_last_reviewed"
expect_failure "invalid calendar lastReviewed date" "lastReviewed must be a valid YYYY-MM-DD date" run_audit "$invalid_calendar_last_reviewed" "$routes" "$gaps"

future_last_reviewed="$tmp_dir/future-last-reviewed.json"
cp "$blocked" "$future_last_reviewed"
perl -0pi -e 's/"releaseEnabled": false,/"releaseEnabled": false,\n  "lastReviewed": "2999-01-01",/' "$future_last_reviewed"
expect_failure "future lastReviewed date" "lastReviewed must not be in the future" run_audit "$future_last_reviewed" "$routes" "$gaps"

missing_last_reviewed_with_evidence="$tmp_dir/missing-last-reviewed-with-evidence.json"
cp "$ready" "$missing_last_reviewed_with_evidence"
perl -0pi -e 's/  "lastReviewed": "2026-06-26",\n//' "$missing_last_reviewed_with_evidence"
expect_failure "evidence missing lastReviewed" "lastReviewed is required when evidence contains valid timestamps" run_audit "$missing_last_reviewed_with_evidence" "$routes" "$empty_gaps" --require-ready

first_record_after_last_reviewed="$tmp_dir/first-record-after-last-reviewed.json"
cp "$ready" "$first_record_after_last_reviewed"
perl -0pi -e 's/"lastReviewed": "2026-06-26"/"lastReviewed": "2026-06-25"/' "$first_record_after_last_reviewed"
expect_failure "first evidence record after lastReviewed" "evidence[0].timestamp UTC date must be on or before lastReviewed" run_audit "$first_record_after_last_reviewed" "$routes" "$empty_gaps" --require-ready

later_record_after_last_reviewed="$tmp_dir/later-record-after-last-reviewed.json"
cp "$ready" "$later_record_after_last_reviewed"
set_evidence_field "$later_record_after_last_reviewed" 1 timestamp '"2026-06-27T00:00:00Z"'
set_evidence_field "$later_record_after_last_reviewed" 1 verifiedAt '"2026-06-27T00:00:01Z"'
expect_failure "later evidence record after lastReviewed" "evidence[1].timestamp UTC date must be on or before lastReviewed" run_audit "$later_record_after_last_reviewed" "$routes" "$empty_gaps" --require-ready

verification_after_last_reviewed="$tmp_dir/verification-after-last-reviewed.json"
cp "$ready" "$verification_after_last_reviewed"
set_evidence_field "$verification_after_last_reviewed" 0 verifiedAt '"2026-06-27T00:00:00Z"'
expect_failure "verification after lastReviewed" "evidence[0].verifiedAt UTC date must be on or before lastReviewed" run_audit "$verification_after_last_reviewed" "$routes" "$empty_gaps" --require-ready

release_enabled_blocked="$tmp_dir/release-enabled-blocked.json"
cp "$blocked" "$release_enabled_blocked"
perl -0pi -e 's/"releaseEnabled": false/"releaseEnabled": true/' "$release_enabled_blocked"
expect_failure "release enabled while blocked" "releaseEnabled must remain false while status is blocked" run_audit "$release_enabled_blocked" "$routes" "$gaps"

missing_e2e_blocker="$tmp_dir/missing-e2e-blocker.json"
cp "$blocked" "$missing_e2e_blocker"
perl -0pi -e 's/"e2e-transfer-evidence-missing",\n    //' "$missing_e2e_blocker"
expect_failure "blocked evidence missing E2E blocker" "blocked evidence missing blocker e2e-transfer-evidence-missing" run_audit "$missing_e2e_blocker" "$routes" "$gaps"

missing_independent_blocker="$tmp_dir/missing-independent-blocker.json"
cp "$blocked" "$missing_independent_blocker"
perl -0pi -e 's/    "independent-on-chain-verification-missing",\n//' "$missing_independent_blocker"
expect_failure "blocked evidence missing independent verification blocker" "blocked evidence missing blocker independent-on-chain-verification-missing" run_audit "$missing_independent_blocker" "$routes" "$gaps"

missing_all_routes_command="$tmp_dir/missing-all-routes-command.json"
cp "$blocked" "$missing_all_routes_command"
perl -0pi -e 's/ --require-all-routes-executable//' "$missing_all_routes_command"
expect_failure "missing all-routes executable command" "readyVerificationCommands missing audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable" run_audit "$missing_all_routes_command" "$routes" "$gaps"

missing_template_command="$tmp_dir/missing-template-command.json"
cp "$blocked" "$missing_template_command"
perl -0pi -e 's/    "bash \.\/scripts\/test-xcm-production-evidence-template\.sh",\n//' "$missing_template_command"
expect_failure "missing evidence template self-test command" "readyVerificationCommands missing test-xcm-production-evidence-template.sh" run_audit "$missing_template_command" "$routes" "$gaps"

missing_effective_test_command="$tmp_dir/missing-effective-test-command.json"
cp "$blocked" "$missing_effective_test_command"
perl -0pi -e 's/    "bash \.\/scripts\/test-xcm-effective-registry-audit\.sh",\n//' "$missing_effective_test_command"
expect_failure "missing effective registry self-test command" "readyVerificationCommands missing test-xcm-effective-registry-audit.sh" run_audit "$missing_effective_test_command" "$routes" "$gaps"

missing_effective_audit_command="$tmp_dir/missing-effective-audit-command.json"
cp "$blocked" "$missing_effective_audit_command"
perl -0pi -e 's/    "bash \.\/scripts\/audit-xcm-effective-registry\.sh --write-report[^\n]*\n//' "$missing_effective_audit_command"
expect_failure "missing effective registry offline audit command" "readyVerificationCommands missing audit-xcm-effective-registry.sh --write-report" run_audit "$missing_effective_audit_command" "$routes" "$gaps"

missing_effective_live_command="$tmp_dir/missing-effective-live-command.json"
cp "$blocked" "$missing_effective_live_command"
perl -0pi -e 's/    "bash \.\/scripts\/audit-xcm-effective-registry\.sh --discovery-url[^\n]*\n//' "$missing_effective_live_command"
expect_failure "missing effective registry live audit command" "readyVerificationCommands missing audit-xcm-effective-registry.sh --discovery-url" run_audit "$missing_effective_live_command" "$routes" "$gaps"

missing_effective_report_argument="$tmp_dir/missing-effective-report-argument.json"
cp "$blocked" "$missing_effective_report_argument"
perl -0pi -e 's/ --effective-registry-report build\/reports\/xcm-effective-registry-report\.json//' "$missing_effective_report_argument"
expect_failure "missing effective registry report argument" "readyVerificationCommands missing audit-xcm-effective-registry.sh --discovery-url" run_audit "$missing_effective_report_argument" "$routes" "$gaps"

missing_approved_registry_test_command="$tmp_dir/missing-approved-registry-test-command.json"
cp "$blocked" "$missing_approved_registry_test_command"
perl -0pi -e 's/ApprovedXcmRouteRegistryTest/RemovedApprovedRegistryTest/' "$missing_approved_registry_test_command"
expect_failure "missing approved registry unit-test command" "readyVerificationCommands missing ApprovedXcmRouteRegistryTest" run_audit "$missing_approved_registry_test_command" "$routes" "$gaps"

duplicate_verification_command="$tmp_dir/duplicate-verification-command.json"
cp "$blocked" "$duplicate_verification_command"
node - "$duplicate_verification_command" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.readyVerificationCommands.push(
  manifest.readyVerificationCommands.find((command) => command.includes('public-shared-features-xcm:testDebugUnitTest'))
);
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "duplicate XCM production evidence verification command" "duplicate XCM production evidence verification command" run_audit "$duplicate_verification_command" "$routes" "$gaps"

missing_required_field="$tmp_dir/missing-required-field.json"
cp "$blocked" "$missing_required_field"
perl -0pi -e 's/"extrinsicHash",\n    //' "$missing_required_field"
expect_failure "missing required evidence field" "requiredEvidenceFields missing extrinsicHash" run_audit "$missing_required_field" "$routes" "$gaps"

missing_android_commit_field="$tmp_dir/missing-android-commit-field.json"
cp "$blocked" "$missing_android_commit_field"
perl -0pi -e 's/,\n    "androidCommit"//' "$missing_android_commit_field"
expect_failure "missing Android commit evidence field" "requiredEvidenceFields missing androidCommit" run_audit "$missing_android_commit_field" "$routes" "$gaps"

duplicate_required_field="$tmp_dir/duplicate-required-field.json"
cp "$blocked" "$duplicate_required_field"
node - "$duplicate_required_field" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.requiredEvidenceFields.push('operator');
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "duplicate XCM production evidence required field" "duplicate XCM production evidence required field" run_audit "$duplicate_required_field" "$routes" "$gaps"

unsupported_top_level_field="$tmp_dir/unsupported-top-level-field.json"
cp "$blocked" "$unsupported_top_level_field"
perl -0pi -e 's/"releaseEnabled": false,/"releaseEnabled": false,\n  "unsafeComment": "must fail",/' "$unsupported_top_level_field"
expect_failure "unsupported top-level XCM production evidence field" "unsupported XCM production evidence manifest field" run_audit "$unsupported_top_level_field" "$routes" "$gaps"

unsupported_current_state_field="$tmp_dir/unsupported-current-state-field.json"
cp "$blocked" "$unsupported_current_state_field"
perl -0pi -e 's/"discoveryOnlyRouteCount": 1/"discoveryOnlyRouteCount": 1,\n    "generatedRouteCount": 2/' "$unsupported_current_state_field"
expect_failure "unsupported currentState XCM production evidence field" "unsupported XCM currentState field" run_audit "$unsupported_current_state_field" "$routes" "$gaps"

unsupported_route_manifest_field="$tmp_dir/unsupported-route-manifest-field.json"
cp "$blocked" "$unsupported_route_manifest_field"
perl -0pi -e 's/"gapReport": "build\/reports\/xcm-registry-gap-report.json"/"gapReport": "build\/reports\/xcm-registry-gap-report.json",\n    "dashboardUrl": "https:\/\/example.invalid"/' "$unsupported_route_manifest_field"
expect_failure "unsupported routeManifests XCM production evidence field" "unsupported XCM routeManifests field" run_audit "$unsupported_route_manifest_field" "$routes" "$gaps"

missing_approved_route_manifest_field="$tmp_dir/missing-approved-route-manifest-field.json"
cp "$blocked" "$missing_approved_route_manifest_field"
perl -0pi -e 's/    "approvedExecutableRoutes"[^\n]*\n//' "$missing_approved_route_manifest_field"
expect_failure "missing approved route manifest binding" "routeManifests.approvedExecutableRoutes must be" run_audit "$missing_approved_route_manifest_field" "$routes" "$gaps"

missing_effective_report_field="$tmp_dir/missing-effective-report-field.json"
cp "$blocked" "$missing_effective_report_field"
perl -0pi -e 's/,\n    "effectiveRegistryReport"[^\n]*//' "$missing_effective_report_field"
expect_failure "missing effective registry report binding" "routeManifests.effectiveRegistryReport must be" run_audit "$missing_effective_report_field" "$routes" "$gaps"

unsupported_required_evidence_field="$tmp_dir/unsupported-required-evidence-field.json"
cp "$blocked" "$unsupported_required_evidence_field"
perl -0pi -e 's/"androidCommit"\n  \]/"androidCommit",\n    "receiptUrl"\n  \]/' "$unsupported_required_evidence_field"
expect_failure "unsupported required XCM production evidence field" "unsupported required XCM production evidence field" run_audit "$unsupported_required_evidence_field" "$routes" "$gaps"

unsupported_blocker="$tmp_dir/unsupported-blocker.json"
cp "$blocked" "$unsupported_blocker"
perl -0pi -e 's/"discovery-only-routes-remain"/"discovery-only-routes-remain",\n    "manual-approval-pending"/' "$unsupported_blocker"
expect_failure "unsupported XCM production evidence blocker" "unsupported XCM production evidence blocker" run_audit "$unsupported_blocker" "$routes" "$gaps"

duplicate_blocker="$tmp_dir/duplicate-blocker.json"
cp "$blocked" "$duplicate_blocker"
perl -0pi -e 's/"discovery-only-routes-remain"/"discovery-only-routes-remain",\n    "discovery-only-routes-remain"/' "$duplicate_blocker"
expect_failure "duplicate XCM production evidence blocker" "duplicate XCM production evidence blocker" run_audit "$duplicate_blocker" "$routes" "$gaps"

ready_with_gaps="$tmp_dir/ready-with-gaps.json"
write_ready_manifest "$ready_with_gaps"
perl -0pi -e 's/"discoveryOnlyRouteCount": 0/"discoveryOnlyRouteCount": 1/' "$ready_with_gaps"
expect_failure "ready evidence with discovery-only routes" "ready evidence cannot have discovery-only routes remaining" run_audit "$ready_with_gaps" "$routes" "$gaps"

ready_with_blocker="$tmp_dir/ready-with-blocker.json"
cp "$ready" "$ready_with_blocker"
perl -0pi -e 's/"blockers": \[\]/"blockers": ["e2e-transfer-evidence-missing"]/' "$ready_with_blocker"
expect_failure "ready evidence carries blockers" "blockers must be empty when XCM production evidence is ready" run_audit "$ready_with_blocker" "$routes" "$empty_gaps" --require-ready

ready_missing_route="$tmp_dir/ready-missing-route.json"
cp "$ready" "$ready_missing_route"
perl -0pi -e 's/,\n    \{\n      "originChainId": "destination".*?\n    \}\n  \]/\n  \]/s' "$ready_missing_route"
expect_failure "ready evidence without all required routes" "ready evidence missing E2E transfer evidence for route destination -> origin DOT" run_audit "$ready_missing_route" "$routes" "$empty_gaps" --require-ready

effective_without_evidence_routes="$tmp_dir/effective-without-evidence-routes.tsv"
effective_without_evidence_approved="$tmp_dir/effective-without-evidence-approved.tsv"
cp "$routes" "$effective_without_evidence_routes"
cp "$approved" "$effective_without_evidence_approved"
printf '%s\n' 'third fourth DOT' >>"$effective_without_evidence_routes"
printf '%s\n' 'third fourth DOT' >>"$effective_without_evidence_approved"
effective_without_evidence="$tmp_dir/effective-without-evidence.json"
cp "$ready" "$effective_without_evidence"
perl -0pi -e 's/"requiredExecutableRouteCount": 2/"requiredExecutableRouteCount": 3/' "$effective_without_evidence"
expect_failure "effective approved route without E2E evidence" "ready evidence missing E2E transfer evidence for route third -> fourth DOT" \
  run_audit_with_approved "$effective_without_evidence" "$effective_without_evidence_routes" "$effective_without_evidence_approved" "$empty_gaps" --require-ready

missing_origin_proof="$tmp_dir/missing-origin-proof.json"
cp "$ready" "$missing_origin_proof"
delete_evidence_field "$missing_origin_proof" 0 originBlockHash
expect_failure "missing origin block proof" "originBlockHash must not be blank" run_audit "$missing_origin_proof" "$routes" "$empty_gaps" --require-ready

origin_not_final="$tmp_dir/origin-not-final.json"
cp "$ready" "$origin_not_final"
set_evidence_field "$origin_not_final" 0 originFinalized false
expect_failure "origin proof not finalized" "originFinalized must be true" run_audit "$origin_not_final" "$routes" "$empty_gaps" --require-ready

origin_finality_malformed="$tmp_dir/origin-finality-malformed.json"
cp "$ready" "$origin_finality_malformed"
set_evidence_field "$origin_finality_malformed" 0 originFinalized '"true"'
expect_failure "origin finality malformed boolean" "originFinalized must be a boolean" run_audit "$origin_finality_malformed" "$routes" "$empty_gaps" --require-ready

origin_extrinsic_failed="$tmp_dir/origin-extrinsic-failed.json"
cp "$ready" "$origin_extrinsic_failed"
set_evidence_field "$origin_extrinsic_failed" 0 originExtrinsicSucceeded false
expect_failure "origin extrinsic failed" "originExtrinsicSucceeded must be true" run_audit "$origin_extrinsic_failed" "$routes" "$empty_gaps" --require-ready

destination_event_failed="$tmp_dir/destination-event-failed.json"
cp "$ready" "$destination_event_failed"
set_evidence_field "$destination_event_failed" 0 destinationEventSucceeded false
expect_failure "destination event failed" "destinationEventSucceeded must be true" run_audit "$destination_event_failed" "$routes" "$empty_gaps" --require-ready

bad_origin_block_hash="$tmp_dir/bad-origin-block-hash.json"
cp "$ready" "$bad_origin_block_hash"
set_evidence_field "$bad_origin_block_hash" 0 originBlockHash '"0x1234"'
expect_failure "malformed origin block hash" "originBlockHash must be a 0x-prefixed 32-byte hash" run_audit "$bad_origin_block_hash" "$routes" "$empty_gaps" --require-ready

placeholder_origin_block_hash="$tmp_dir/placeholder-origin-block-hash.json"
cp "$ready" "$placeholder_origin_block_hash"
set_evidence_field "$placeholder_origin_block_hash" 0 originBlockHash '"0x1111111111111111111111111111111111111111111111111111111111111111"'
expect_failure "placeholder origin block hash" "originBlockHash must not be a placeholder block hash" run_audit "$placeholder_origin_block_hash" "$routes" "$empty_gaps" --require-ready

origin_hash_is_extrinsic="$tmp_dir/origin-hash-is-extrinsic.json"
cp "$ready" "$origin_hash_is_extrinsic"
set_evidence_field "$origin_hash_is_extrinsic" 0 originBlockHash '"0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"'
expect_failure "origin block hash reused as extrinsic hash" "originBlockHash must differ from extrinsicHash" run_audit "$origin_hash_is_extrinsic" "$routes" "$empty_gaps" --require-ready

same_chain_block_hash="$tmp_dir/same-chain-block-hash.json"
cp "$ready" "$same_chain_block_hash"
set_evidence_field "$same_chain_block_hash" 0 destinationBlockHash '"0xabcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"'
expect_failure "origin and destination block hash reused" "origin and destination block hashes must differ" run_audit "$same_chain_block_hash" "$routes" "$empty_gaps" --require-ready

bad_origin_block_number="$tmp_dir/bad-origin-block-number.json"
cp "$ready" "$bad_origin_block_number"
set_evidence_field "$bad_origin_block_number" 0 originBlockNumber '"0"'
expect_failure "zero origin block number" "originBlockNumber must be a canonical positive integer string" run_audit "$bad_origin_block_number" "$routes" "$empty_gaps" --require-ready

bad_destination_block_number="$tmp_dir/bad-destination-block-number.json"
cp "$ready" "$bad_destination_block_number"
set_evidence_field "$bad_destination_block_number" 0 destinationBlockNumber '"01"'
expect_failure "noncanonical destination block number" "destinationBlockNumber must be a canonical positive integer string" run_audit "$bad_destination_block_number" "$routes" "$empty_gaps" --require-ready

zero_destination_delta="$tmp_dir/zero-destination-delta.json"
cp "$ready" "$zero_destination_delta"
set_evidence_field "$zero_destination_delta" 0 destinationBalanceDelta '"0"'
expect_failure "zero destination balance delta" "destinationBalanceDelta must be a positive decimal string" run_audit "$zero_destination_delta" "$routes" "$empty_gaps" --require-ready

bad_destination_delta="$tmp_dir/bad-destination-delta.json"
cp "$ready" "$bad_destination_delta"
set_evidence_field "$bad_destination_delta" 0 destinationBalanceDelta '"one"'
expect_failure "malformed destination balance delta" "destinationBalanceDelta must be a positive decimal string" run_audit "$bad_destination_delta" "$routes" "$empty_gaps" --require-ready

http_origin_proof="$tmp_dir/http-origin-proof.json"
cp "$ready" "$http_origin_proof"
set_evidence_field "$http_origin_proof" 0 originVerificationUrl '"http://polkadot.subscan.io/extrinsic/proof"'
expect_failure "HTTP origin proof URL" "originVerificationUrl must use HTTPS" run_audit "$http_origin_proof" "$routes" "$empty_gaps" --require-ready

credential_origin_proof="$tmp_dir/credential-origin-proof.json"
cp "$ready" "$credential_origin_proof"
set_evidence_field "$credential_origin_proof" 0 originVerificationUrl '"https://user:super-secret@polkadot.subscan.io/extrinsic/proof"'
expect_failure_without "credentialed proof URL without secret leak" "originVerificationUrl must not contain credentials" "super-secret" \
  run_audit "$credential_origin_proof" "$routes" "$empty_gaps" --require-ready

private_destination_proof="$tmp_dir/private-destination-proof.json"
cp "$ready" "$private_destination_proof"
set_evidence_field "$private_destination_proof" 0 destinationVerificationUrl '"https://127.0.0.1/proof"'
expect_failure "private destination proof URL" "must use a public non-placeholder DNS host" run_audit "$private_destination_proof" "$routes" "$empty_gaps" --require-ready

private_ipv6_destination_proof="$tmp_dir/private-ipv6-destination-proof.json"
cp "$ready" "$private_ipv6_destination_proof"
set_evidence_field "$private_ipv6_destination_proof" 0 destinationVerificationUrl '"https://[::1]/proof"'
expect_failure "private IPv6 destination proof URL" "must use a public non-placeholder DNS host" run_audit "$private_ipv6_destination_proof" "$routes" "$empty_gaps" --require-ready

trailing_dot_localhost_proof="$tmp_dir/trailing-dot-localhost-proof.json"
cp "$ready" "$trailing_dot_localhost_proof"
set_evidence_field "$trailing_dot_localhost_proof" 0 destinationVerificationUrl '"https://localhost./proof"'
expect_failure "trailing-dot localhost proof URL" "must use a public non-placeholder DNS host" run_audit "$trailing_dot_localhost_proof" "$routes" "$empty_gaps" --require-ready

trailing_dot_localhost_subdomain_proof="$tmp_dir/trailing-dot-localhost-subdomain-proof.json"
cp "$ready" "$trailing_dot_localhost_subdomain_proof"
set_evidence_field "$trailing_dot_localhost_subdomain_proof" 0 destinationVerificationUrl '"https://foo.localhost./proof"'
expect_failure "trailing-dot localhost subdomain proof URL" "must use a public non-placeholder DNS host" run_audit "$trailing_dot_localhost_subdomain_proof" "$routes" "$empty_gaps" --require-ready

trailing_dot_public_name_proof="$tmp_dir/trailing-dot-public-name-proof.json"
cp "$ready" "$trailing_dot_public_name_proof"
set_evidence_field "$trailing_dot_public_name_proof" 0 destinationVerificationUrl '"https://example.com./proof"'
expect_failure "trailing-dot public-name proof URL" "must use a public non-placeholder DNS host" run_audit "$trailing_dot_public_name_proof" "$routes" "$empty_gaps" --require-ready

placeholder_destination_proof="$tmp_dir/placeholder-destination-proof.json"
cp "$ready" "$placeholder_destination_proof"
set_evidence_field "$placeholder_destination_proof" 0 destinationVerificationUrl '"https://example.com/proof"'
expect_failure "placeholder destination proof URL" "must use a public non-placeholder DNS host" run_audit "$placeholder_destination_proof" "$routes" "$empty_gaps" --require-ready

query_origin_proof="$tmp_dir/query-origin-proof.json"
cp "$ready" "$query_origin_proof"
set_evidence_field "$query_origin_proof" 0 originVerificationUrl '"https://polkadot.subscan.io/proof?token=public"'
expect_failure "proof URL query rejected" "must not contain a query or fragment" run_audit "$query_origin_proof" "$routes" "$empty_gaps" --require-ready

same_proof_urls="$tmp_dir/same-proof-urls.json"
cp "$ready" "$same_proof_urls"
set_evidence_field "$same_proof_urls" 0 destinationVerificationUrl '"https://polkadot.subscan.io/extrinsic/0x0123456789abcdef"'
expect_failure "origin and destination proof URLs reused" "origin and destination verification URLs must differ" run_audit "$same_proof_urls" "$routes" "$empty_gaps" --require-ready

wrong_verification_method="$tmp_dir/wrong-verification-method.json"
cp "$ready" "$wrong_verification_method"
set_evidence_field "$wrong_verification_method" 0 verificationMethod '"explorer-only"'
expect_failure "explorer-only verification method" "verificationMethod must be canonical-rpc-and-explorer" run_audit "$wrong_verification_method" "$routes" "$empty_gaps" --require-ready

verification_precedes_transfer="$tmp_dir/verification-precedes-transfer.json"
cp "$ready" "$verification_precedes_transfer"
set_evidence_field "$verification_precedes_transfer" 0 verifiedAt '"2026-06-25T23:59:59Z"'
expect_failure "verification timestamp precedes transfer" "verifiedAt must not precede the transfer timestamp" run_audit "$verification_precedes_transfer" "$routes" "$empty_gaps" --require-ready

verification_future="$tmp_dir/verification-future.json"
cp "$ready" "$verification_future"
set_evidence_field "$verification_future" 0 verifiedAt '"2999-01-01T00:00:00Z"'
expect_failure "verification timestamp in future" "verifiedAt must not be in the future" run_audit "$verification_future" "$routes" "$empty_gaps" --require-ready

verification_invalid_calendar="$tmp_dir/verification-invalid-calendar.json"
cp "$ready" "$verification_invalid_calendar"
set_evidence_field "$verification_invalid_calendar" 0 verifiedAt '"2026-02-31T00:00:00Z"'
expect_failure "verification timestamp invalid calendar" "verifiedAt must be an ISO-8601 UTC second timestamp" run_audit "$verification_invalid_calendar" "$routes" "$empty_gaps" --require-ready

same_verifier_operator="$tmp_dir/same-verifier-operator.json"
cp "$ready" "$same_verifier_operator"
set_evidence_field "$same_verifier_operator" 0 independentVerifier '"release"'
expect_failure "independent verifier same as operator" "independentVerifier must differ from operator" run_audit "$same_verifier_operator" "$routes" "$empty_gaps" --require-ready

placeholder_verifier="$tmp_dir/placeholder-verifier.json"
cp "$ready" "$placeholder_verifier"
set_evidence_field "$placeholder_verifier" 0 independentVerifier '"TODO_reviewer"'
expect_failure "placeholder independent verifier" "must not be a placeholder operator" run_audit "$placeholder_verifier" "$routes" "$empty_gaps" --require-ready

secret_like_verifier="$tmp_dir/secret-like-verifier.json"
cp "$ready" "$secret_like_verifier"
set_evidence_field "$secret_like_verifier" 0 independentVerifier '"reviewer-ghp_12345678901234567890"'
expect_failure "secret-like independent verifier" "independentVerifier must not contain secret-like token" run_audit "$secret_like_verifier" "$routes" "$empty_gaps" --require-ready

ready_bad_hash="$tmp_dir/ready-bad-hash.json"
cp "$ready" "$ready_bad_hash"
perl -0pi -e 's/0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/0x1234/' "$ready_bad_hash"
expect_failure "ready evidence malformed extrinsic hash" "extrinsicHash must be a 0x-prefixed 32-byte hash" run_audit "$ready_bad_hash" "$routes" "$empty_gaps" --require-ready

ready_placeholder_hash="$tmp_dir/ready-placeholder-hash.json"
cp "$ready" "$ready_placeholder_hash"
perl -0pi -e 's/0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/0x1111111111111111111111111111111111111111111111111111111111111111/' "$ready_placeholder_hash"
expect_failure "ready evidence placeholder extrinsic hash" "extrinsicHash must not be a placeholder extrinsic hash" run_audit "$ready_placeholder_hash" "$routes" "$empty_gaps" --require-ready

ready_bad_amount="$tmp_dir/ready-bad-amount.json"
cp "$ready" "$ready_bad_amount"
perl -0pi -e 's/"amount": "1"/"amount": "0"/' "$ready_bad_amount"
expect_failure "ready evidence zero amount" "amount must be greater than zero" run_audit "$ready_bad_amount" "$routes" "$empty_gaps" --require-ready

ready_bad_environment="$tmp_dir/ready-bad-environment.json"
cp "$ready" "$ready_bad_environment"
perl -0pi -e 's/"environment": "mainnet"/"environment": "staging"/' "$ready_bad_environment"
expect_failure "ready evidence bad environment" "environment must be mainnet or testnet" run_audit "$ready_bad_environment" "$routes" "$empty_gaps" --require-ready

ready_testnet_environment="$tmp_dir/ready-testnet-environment.json"
cp "$ready" "$ready_testnet_environment"
perl -0pi -e 's/"environment": "mainnet"/"environment": "testnet"/' "$ready_testnet_environment"
expect_failure "ready evidence testnet environment" "environment must be mainnet when XCM production evidence is ready" run_audit "$ready_testnet_environment" "$routes" "$empty_gaps" --require-ready

ready_placeholder_sender="$tmp_dir/ready-placeholder-sender.json"
cp "$ready" "$ready_placeholder_sender"
perl -0pi -e 's/"sender": "sender-origin"/"sender": "TODO_sender_public_address"/' "$ready_placeholder_sender"
expect_failure "ready evidence placeholder sender" "sender must not be a placeholder public address" run_audit "$ready_placeholder_sender" "$routes" "$empty_gaps" --require-ready

ready_placeholder_recipient="$tmp_dir/ready-placeholder-recipient.json"
cp "$ready" "$ready_placeholder_recipient"
perl -0pi -e 's/"recipient": "recipient-destination"/"recipient": "TODO_recipient_public_address"/' "$ready_placeholder_recipient"
expect_failure "ready evidence placeholder recipient" "recipient must not be a placeholder public address" run_audit "$ready_placeholder_recipient" "$routes" "$empty_gaps" --require-ready

ready_same_parties="$tmp_dir/ready-same-parties.json"
cp "$ready" "$ready_same_parties"
perl -0pi -e 's/"recipient": "recipient-destination"/"recipient": "sender-origin"/' "$ready_same_parties"
expect_failure "ready evidence same sender and recipient" "sender and recipient must differ" run_audit "$ready_same_parties" "$routes" "$empty_gaps" --require-ready

ready_placeholder_operator="$tmp_dir/ready-placeholder-operator.json"
cp "$ready" "$ready_placeholder_operator"
perl -0pi -e 's/"operator": "release"/"operator": "TODO_operator_or_runbook_id"/' "$ready_placeholder_operator"
expect_failure "ready evidence placeholder operator" "operator must not be a placeholder operator" run_audit "$ready_placeholder_operator" "$routes" "$empty_gaps" --require-ready

ready_multiline_operator="$tmp_dir/ready-multiline-operator.json"
cp "$ready" "$ready_multiline_operator"
node - "$ready_multiline_operator" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].operator = 'release\noncall';
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "ready evidence multiline operator" "operator must be a single-line public value" run_audit "$ready_multiline_operator" "$routes" "$empty_gaps" --require-ready

ready_secret_like_operator="$tmp_dir/ready-secret-like-operator.json"
cp "$ready" "$ready_secret_like_operator"
node - "$ready_secret_like_operator" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].operator = 'release-ghp_12345678901234567890';
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "ready evidence secret-like operator" "operator must not contain secret-like token" run_audit "$ready_secret_like_operator" "$routes" "$empty_gaps" --require-ready

ready_unsupported_evidence_field="$tmp_dir/ready-unsupported-evidence-field.json"
cp "$ready" "$ready_unsupported_evidence_field"
perl -0pi -e 's/"operator": "release"/"operator": "release",\n      "receiptUrl": "https:\/\/example.invalid\/evidence"/' "$ready_unsupported_evidence_field"
expect_failure "unsupported XCM production evidence record field" "unsupported XCM production evidence[0] field" run_audit "$ready_unsupported_evidence_field" "$routes" "$empty_gaps" --require-ready

ready_unknown_route="$tmp_dir/ready-unknown-route.json"
cp "$ready" "$ready_unknown_route"
perl -0pi -e 's/"destinationChainId": "destination"/"destinationChainId": "unknown"/' "$ready_unknown_route"
expect_failure "ready evidence for untracked route" "route is not declared in required route manifest" run_audit "$ready_unknown_route" "$routes" "$empty_gaps" --require-ready

ready_duplicate_hash="$tmp_dir/ready-duplicate-hash.json"
cp "$ready" "$ready_duplicate_hash"
perl -0pi -e 's/0xfedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210/0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef/' "$ready_duplicate_hash"
expect_failure "ready evidence duplicate extrinsic hash" "duplicate E2E transfer extrinsicHash" run_audit "$ready_duplicate_hash" "$routes" "$empty_gaps" --require-ready

ready_malformed_android_commit="$tmp_dir/ready-malformed-android-commit.json"
cp "$ready" "$ready_malformed_android_commit"
node - "$ready_malformed_android_commit" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].androidCommit = 'not-a-commit';
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "ready evidence malformed Android commit" "androidCommit must be a 40-character git commit" run_audit "$ready_malformed_android_commit" "$routes" "$empty_gaps" --require-ready

ready_placeholder_android_commit="$tmp_dir/ready-placeholder-android-commit.json"
cp "$ready" "$ready_placeholder_android_commit"
node - "$ready_placeholder_android_commit" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].androidCommit = 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa';
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "ready evidence placeholder Android commit" "androidCommit must not be a placeholder Android release commit" run_audit "$ready_placeholder_android_commit" "$routes" "$empty_gaps" --require-ready

ready_stale_android_commit="$tmp_dir/ready-stale-android-commit.json"
cp "$ready" "$ready_stale_android_commit"
node - "$ready_stale_android_commit" "$STALE_ANDROID_COMMIT" <<'NODE'
const fs = require('fs');
const [file, staleCommit] = process.argv.slice(2);
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].androidCommit = staleCommit;
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "ready evidence stale Android commit" "androidCommit must match expected Android release commit $ANDROID_COMMIT" run_audit "$ready_stale_android_commit" "$routes" "$empty_gaps" --require-ready

expect_failure "malformed expected Android commit override" "XCM_PRODUCTION_EXPECTED_COMMIT must be a 40-character git commit" \
  env XCM_PRODUCTION_EXPECTED_COMMIT=not-a-commit bash "$AUDIT_SCRIPT" --evidence "$ready" --required-route-file "$routes" --approved-route-file "$approved" --discovery-gap-file "$empty_gaps" --require-ready

no_git_root="$tmp_dir/no-git-root"
mkdir "$no_git_root"
expect_failure "missing expected Android commit source" "ready XCM production evidence requires XCM_PRODUCTION_EXPECTED_COMMIT or a local Android git HEAD source" \
  env -u XCM_PRODUCTION_EXPECTED_COMMIT XCM_PRODUCTION_EVIDENCE_ROOT="$no_git_root" bash "$AUDIT_SCRIPT" --evidence "$ready" --required-route-file "$routes" --approved-route-file "$approved" --discovery-gap-file "$empty_gaps" --require-ready

ready_future_timestamp="$tmp_dir/ready-future-timestamp.json"
cp "$ready" "$ready_future_timestamp"
perl -0pi -e 's/2026-06-26T00:00:00Z/2999-01-01T00:00:00Z/g' "$ready_future_timestamp"
expect_failure "ready evidence future timestamp" "timestamp must not be in the future" run_audit "$ready_future_timestamp" "$routes" "$empty_gaps" --require-ready

ready_secret_key="$tmp_dir/ready-secret-key.json"
cp "$ready" "$ready_secret_key"
perl -0pi -e 's/"operator": "release"/"operator": "release",\n      "privateKey": "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"/' "$ready_secret_key"
expect_failure "secret-like XCM production evidence key" "must not be included in public XCM production evidence" run_audit "$ready_secret_key" "$routes" "$empty_gaps" --require-ready

ready_secret_value="$tmp_dir/ready-secret-value.json"
cp "$ready" "$ready_secret_value"
node - "$ready_secret_value" <<'NODE'
const fs = require('fs');
const file = process.argv[2];
const manifest = JSON.parse(fs.readFileSync(file, 'utf8'));
manifest.evidence[0].sender = 'sender-ghp_12345678901234567890';
fs.writeFileSync(file, `${JSON.stringify(manifest, null, 2)}\n`);
NODE
expect_failure "secret-like XCM production evidence value" "sender must not contain secret-like token" run_audit "$ready_secret_value" "$routes" "$empty_gaps" --require-ready

blocked_stale_counts="$tmp_dir/blocked-stale-counts.json"
cp "$blocked" "$blocked_stale_counts"
perl -0pi -e 's/"requiredExecutableRouteCount": 2/"requiredExecutableRouteCount": 1/' "$blocked_stale_counts"
expect_failure "stale production evidence counts" "currentState.requiredExecutableRouteCount must match required route manifest count 2" run_audit "$blocked_stale_counts" "$routes" "$gaps"

blocked_stale_discovery="$tmp_dir/blocked-stale-discovery.json"
cp "$ready" "$blocked_stale_discovery"
perl -0pi -e 's/"status": "ready"/"status": "blocked"/' "$blocked_stale_discovery"
perl -0pi -e 's/"releaseEnabled": true/"releaseEnabled": false/' "$blocked_stale_discovery"
perl -0pi -e 's/"blockers": \[\]/"blockers": ["discovery-only-routes-remain"]/' "$blocked_stale_discovery"
expect_failure "blocked evidence stale discovery blocker" "blocked evidence has stale blocker discovery-only-routes-remain" run_audit "$blocked_stale_discovery" "$routes" "$empty_gaps"

echo "[xcm-production-evidence-test] all tests passed ($TEST_COUNT scenarios: $((TEST_COUNT - 3)) negative, 3 valid-state)"
