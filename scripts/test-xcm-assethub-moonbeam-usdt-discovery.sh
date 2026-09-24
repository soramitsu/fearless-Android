#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
INSPECTOR="$ROOT_DIR/scripts/inspect-xcm-assethub-moonbeam-usdt.js"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

fail() {
  echo "[xcm-discovery-test][error] $*" >&2
  exit 1
}

reset_fixture() {
  mkdir -p "$TEMP_DIR/runtime/src/main/assets" "$TEMP_DIR/scripts"
  cp "$ROOT_DIR/runtime/src/main/assets/local_chains.json" "$TEMP_DIR/runtime/src/main/assets/local_chains.json"
  cp "$ROOT_DIR/runtime/src/main/assets/approved_xcm_routes.tsv" "$TEMP_DIR/runtime/src/main/assets/approved_xcm_routes.tsv"
  cp "$ROOT_DIR/scripts/xcm-required-routes.tsv" "$TEMP_DIR/scripts/xcm-required-routes.tsv"
  cp "$ROOT_DIR/scripts/xcm-discovery-only-routes.tsv" "$TEMP_DIR/scripts/xcm-discovery-only-routes.tsv"
  cp "$ROOT_DIR/scripts/xcm-production-evidence.json" "$TEMP_DIR/scripts/xcm-production-evidence.json"
}

expect_failure() {
  local label="$1"
  local message="$2"
  if XCM_DISCOVERY_ROOT="$TEMP_DIR" node "$INSPECTOR" >"$TEMP_DIR/stdout" 2>"$TEMP_DIR/stderr"; then
    fail "$label unexpectedly passed"
  fi
  if ! rg -Fq "$message" "$TEMP_DIR/stderr"; then
    fail "$label failed for the wrong reason: $(cat "$TEMP_DIR/stderr")"
  fi
}

reset_fixture
XCM_DISCOVERY_ROOT="$TEMP_DIR" node "$INSPECTOR" --output "$TEMP_DIR/report.json"
node - "$TEMP_DIR/report.json" "$TEMP_DIR" <<'NODE'
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const report = JSON.parse(fs.readFileSync(process.argv[2], 'utf8'));
const root = process.argv[3];
assert.equal(report.discovery.origin.name, 'Polkadot AssetHub');
assert.equal(report.discovery.destination.name, 'Moonbeam');
assert.equal(report.discovery.origin.paraId, '1000');
assert.equal(report.discovery.destination.paraId, '2004');
assert.equal(report.discovery.advertisedRouteAsset.id, '1c9cea4c-f369-4bfa-a8c4-3d3264d3d7c2');
assert.equal(report.discovery.originWalletAsset.currencyId, '1984');
assert.equal(report.discovery.originWalletAsset.precision, 6);
assert.equal(report.discovery.possibleDestinationWalletAsset.symbol, 'xcusdt');
assert.equal(report.discovery.possibleDestinationWalletAsset.precision, 6);
assert.equal(report.qualification.discoveryOnly, true);
assert.equal(report.qualification.approvedForExecution, false);
assert.equal(report.qualification.productionEvidenceReleaseEnabled, false);
for (const source of Object.values(report.sources)) {
    const bytes = fs.readFileSync(path.join(root, source.path));
    assert.equal(source.byteLength, bytes.length);
    assert.equal(source.sha256, crypto.createHash('sha256').update(bytes).digest('hex'));
}
NODE

reset_fixture
node - "$TEMP_DIR/runtime/src/main/assets/local_chains.json" <<'NODE'
const fs = require('node:fs');
const file = process.argv[2];
const chains = JSON.parse(fs.readFileSync(file, 'utf8'));
const origin = chains.find(chain => chain.name === 'Polkadot AssetHub');
origin.xcm.availableDestinations.find(route => route.chainId.startsWith('fe58ea77')).assets[0].execution = {};
fs.writeFileSync(file, JSON.stringify(chains));
NODE
expect_failure "execution promotion" "route now has execution metadata"

reset_fixture
cat >>"$TEMP_DIR/runtime/src/main/assets/approved_xcm_routes.tsv" <<'TSV'
68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d USDT
TSV
expect_failure "approval promotion" "route unexpectedly appears in approved execution manifest"

reset_fixture
node - "$TEMP_DIR/scripts/xcm-discovery-only-routes.tsv" <<'NODE'
const fs = require('node:fs');
const file = process.argv[2];
fs.writeFileSync(file, fs.readFileSync(file, 'utf8').split('\n')
    .filter(line => !line.startsWith('68d56f15f85d3136970ec16946040bc1752654e906147f7e43e9d539d7c3de2f fe58ea77779b7abda7da4ec526d14db9b1e9cd40a217c34892af80a9b332b76d '))
    .join('\n'));
NODE
expect_failure "missing frozen gap" "discovery-only route: expected one, found 0"

reset_fixture
node - "$TEMP_DIR/runtime/src/main/assets/local_chains.json" <<'NODE'
const fs = require('node:fs');
const file = process.argv[2];
const chains = JSON.parse(fs.readFileSync(file, 'utf8'));
const moonbeam = chains.find(chain => chain.name === 'Moonbeam');
moonbeam.assets = moonbeam.assets.filter(asset => asset.symbol !== 'xcusdt');
fs.writeFileSync(file, JSON.stringify(chains));
NODE
expect_failure "destination candidate removed" "possible destination wallet asset: expected one, found 0"

reset_fixture
node - "$TEMP_DIR/scripts/xcm-production-evidence.json" <<'NODE'
const fs = require('node:fs');
const file = process.argv[2];
const evidence = JSON.parse(fs.readFileSync(file, 'utf8'));
evidence.releaseEnabled = true;
fs.writeFileSync(file, JSON.stringify(evidence));
NODE
expect_failure "release flag enabled" "production XCM release gate is no longer blocked"

echo '[xcm-discovery-test] Asset Hub -> Moonbeam USDt source inventory and fail-closed drift cases passed'
