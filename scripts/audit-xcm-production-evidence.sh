#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${XCM_PRODUCTION_EVIDENCE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
EVIDENCE_FILE="$ROOT_DIR/scripts/xcm-production-evidence.json"
REQUIRED_ROUTE_FILE="$ROOT_DIR/scripts/xcm-required-routes.tsv"
DISCOVERY_GAP_FILE="$ROOT_DIR/scripts/xcm-discovery-only-routes.tsv"
REQUIRE_READY=false

usage() {
  cat <<'USAGE'
Usage: scripts/audit-xcm-production-evidence.sh [--evidence <path>] [--required-route-file <path>] [--discovery-gap-file <path>] [--require-ready]

Validates the Android XCM production evidence manifest. The default audit allows
the current blocked state, but rejects any release-enabled or ready claim unless
all required route evidence is present and no discovery-only XCM gaps remain.

--require-ready additionally fails unless the manifest is marked ready for broad
production XCM release.
USAGE
}

while (($#)); do
  case "$1" in
    --evidence)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --evidence requires a path" >&2; exit 2; }
      EVIDENCE_FILE="$2"
      shift 2
      ;;
    --required-route-file)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --required-route-file requires a path" >&2; exit 2; }
      REQUIRED_ROUTE_FILE="$2"
      shift 2
      ;;
    --discovery-gap-file)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --discovery-gap-file requires a path" >&2; exit 2; }
      DISCOVERY_GAP_FILE="$2"
      shift 2
      ;;
    --require-ready)
      REQUIRE_READY=true
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[xcm-production-evidence][error] Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v node >/dev/null 2>&1; then
  echo "[xcm-production-evidence][error] node is required for structured JSON validation" >&2
  exit 1
fi

node - "$EVIDENCE_FILE" "$REQUIRED_ROUTE_FILE" "$DISCOVERY_GAP_FILE" "$REQUIRE_READY" <<'NODE'
const fs = require('fs');

const [evidenceFile, requiredRouteFile, discoveryGapFile, requireReadyRaw] = process.argv.slice(2);
const requireReady = requireReadyRaw === 'true';
const errors = [];

const REQUIRED_BLOCKERS = [
  'e2e-transfer-evidence-missing',
  'all-routes-executable-gate-not-green',
  'discovery-only-routes-remain'
];

const REQUIRED_EVIDENCE_FIELDS = [
  'originChainId',
  'destinationChainId',
  'assetSymbol',
  'extrinsicHash',
  'sender',
  'recipient',
  'amount',
  'timestamp',
  'environment',
  'operator'
];

const ALLOWED_MANIFEST_FIELDS = [
  'schemaVersion',
  'scope',
  'status',
  'releaseEnabled',
  'lastReviewed',
  'currentState',
  'blockers',
  'routeManifests',
  'readyVerificationCommands',
  'requiredEvidenceFields',
  'evidence'
];

const ALLOWED_CURRENT_STATE_FIELDS = [
  'requiredExecutableRouteCount',
  'discoveryOnlyRouteCount'
];

const ALLOWED_ROUTE_MANIFEST_FIELDS = [
  'requiredExecutableRoutes',
  'discoveryOnlyRoutes',
  'gapReport'
];

const REQUIRED_READY_COMMAND_MARKERS = [
  'test-xcm-production-evidence-template.sh',
  'test-xcm-production-evidence-audit.sh',
  'audit-xcm-production-evidence.sh --require-ready',
  'audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable',
  '--require-route-file scripts/xcm-required-routes.tsv',
  '--require-gap-file scripts/xcm-discovery-only-routes.tsv',
  'public-shared-features-xcm:testDebugUnitTest',
  'XcmLocalChainsRegistryTest'
];

function fail(message) {
  errors.push(message);
}

function readText(file, description) {
  if (!fs.existsSync(file)) {
    fail(`${description} missing: ${file}`);
    return null;
  }

  try {
    return fs.readFileSync(file, 'utf8');
  } catch (error) {
    fail(`${description} could not be read: ${error.message}`);
    return null;
  }
}

function parseJson(file, description) {
  const text = readText(file, description);
  if (text === null) {
    return null;
  }

  try {
    return JSON.parse(text);
  } catch (error) {
    fail(`${description} must be valid JSON: ${error.message}`);
    return null;
  }
}

function nonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function normalizeAssetSymbol(value) {
  return String(value || '')
    .trim()
    .replace(/^xc/i, '')
    .toUpperCase();
}

function isRepeatedHexPlaceholder(value) {
  const normalized = String(value || '')
    .trim()
    .toLowerCase()
    .replace(/^0x/, '');
  return /^[0-9a-f]{8,}$/.test(normalized) && new Set(normalized).size === 1;
}

function secretLikeKeyReason(value, path = '$') {
  if (!value || typeof value !== 'object') {
    return null;
  }

  if (Array.isArray(value)) {
    for (let index = 0; index < value.length; index += 1) {
      const reason = secretLikeKeyReason(value[index], `${path}[${index}]`);
      if (reason) {
        return reason;
      }
    }
    return null;
  }

  for (const [key, nested] of Object.entries(value)) {
    const nestedPath = `${path}.${key}`;
    if (/(private[-_]?key|mnemonic|seed|secret|password|authorization|credential|clientDataJSON)/iu.test(key)) {
      return `${nestedPath} must not be included in public XCM production evidence`;
    }
    const reason = secretLikeKeyReason(nested, nestedPath);
    if (reason) {
      return reason;
    }
  }

  return null;
}

function parseRequiredRoutes(file) {
  const text = readText(file, 'required route file');
  if (text === null) {
    return [];
  }

  const routes = [];
  text.split(/\r?\n/).forEach((line, index) => {
    const trimmed = line.replace(/\s+#.*$/, '').trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return;
    }

    const parts = trimmed.split(/\s+/);
    if (parts.length !== 3 || parts.some((part) => !nonEmptyString(part))) {
      fail(`Invalid required route file line ${index + 1} in ${file}: expected origin destination asset`);
      return;
    }

    routes.push({
      originChainId: parts[0],
      destinationChainId: parts[1],
      assetSymbol: normalizeAssetSymbol(parts[2])
    });
  });
  return routes;
}

function parseDiscoveryGaps(file) {
  const text = readText(file, 'discovery-only gap file');
  if (text === null) {
    return [];
  }

  const gaps = [];
  text.split(/\r?\n/).forEach((line, index) => {
    const trimmed = line.replace(/\s+#.*$/, '').trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return;
    }

    const parts = trimmed.split(/\s+/);
    if (parts.length !== 5 || parts.some((part) => !nonEmptyString(part))) {
      fail(`Invalid discovery-only gap file line ${index + 1} in ${file}: expected origin destination assets reason bridgeParachainId`);
      return;
    }

    gaps.push({
      originChainId: parts[0],
      destinationChainId: parts[1],
      assetSymbols: parts[2].split(',').map(normalizeAssetSymbol).filter(Boolean),
      reason: parts[3],
      bridgeParachainId: parts[4]
    });
  });
  return gaps;
}

function routeKey(route) {
  return `${route.originChainId}|${route.destinationChainId}|${normalizeAssetSymbol(route.assetSymbol)}`;
}

function requireArray(value, name) {
  if (!Array.isArray(value)) {
    fail(`${name} must be an array`);
    return [];
  }

  return value;
}

function requireObject(value, name) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${name} must be an object`);
    return {};
  }

  return value;
}

function assertAllowedKeys(value, allowedKeys, unsupportedFieldPrefix) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return;
  }

  const allowed = new Set(allowedKeys);
  for (const key of Object.keys(value)) {
    if (!allowed.has(key)) {
      fail(`${unsupportedFieldPrefix}: ${key}`);
    }
  }
}

const manifest = parseJson(evidenceFile, 'XCM production evidence manifest');
const requiredRoutes = parseRequiredRoutes(requiredRouteFile);
const discoveryGaps = parseDiscoveryGaps(discoveryGapFile);

if (manifest) {
  const secretReason = secretLikeKeyReason(manifest);
  if (secretReason) {
    fail(secretReason);
  }

  assertAllowedKeys(manifest, ALLOWED_MANIFEST_FIELDS, 'unsupported XCM production evidence manifest field');

  if (manifest.schemaVersion !== 1) {
    fail('schemaVersion must be 1');
  }

  if (manifest.scope !== 'android-xcm-production-readiness') {
    fail('scope must be android-xcm-production-readiness');
  }

  if (!['blocked', 'ready'].includes(manifest.status)) {
    fail('status must be blocked or ready');
  }

  if (typeof manifest.releaseEnabled !== 'boolean') {
    fail('releaseEnabled must be a boolean');
  }

  if (manifest.lastReviewed !== undefined && !/^\d{4}-\d{2}-\d{2}$/.test(String(manifest.lastReviewed))) {
    fail('lastReviewed must be YYYY-MM-DD when present');
  }

  const currentState = requireObject(manifest.currentState, 'currentState');
  assertAllowedKeys(currentState, ALLOWED_CURRENT_STATE_FIELDS, 'unsupported XCM currentState field');
  if (currentState.requiredExecutableRouteCount !== requiredRoutes.length) {
    fail(`currentState.requiredExecutableRouteCount must match required route manifest count ${requiredRoutes.length}`);
  }
  if (currentState.discoveryOnlyRouteCount !== discoveryGaps.length) {
    fail(`currentState.discoveryOnlyRouteCount must match discovery-only manifest count ${discoveryGaps.length}`);
  }

  const routeManifests = requireObject(manifest.routeManifests, 'routeManifests');
  assertAllowedKeys(routeManifests, ALLOWED_ROUTE_MANIFEST_FIELDS, 'unsupported XCM routeManifests field');
  if (routeManifests.requiredExecutableRoutes !== 'scripts/xcm-required-routes.tsv') {
    fail('routeManifests.requiredExecutableRoutes must be scripts/xcm-required-routes.tsv');
  }
  if (routeManifests.discoveryOnlyRoutes !== 'scripts/xcm-discovery-only-routes.tsv') {
    fail('routeManifests.discoveryOnlyRoutes must be scripts/xcm-discovery-only-routes.tsv');
  }
  if (routeManifests.gapReport !== 'build/reports/xcm-registry-gap-report.json') {
    fail('routeManifests.gapReport must be build/reports/xcm-registry-gap-report.json');
  }

  const blockers = new Set(requireArray(manifest.blockers, 'blockers'));
  const commands = requireArray(manifest.readyVerificationCommands, 'readyVerificationCommands').join('\n');
  const requiredEvidenceFields = new Set(requireArray(manifest.requiredEvidenceFields, 'requiredEvidenceFields'));
  const evidence = requireArray(manifest.evidence, 'evidence');

  for (const blocker of blockers) {
    if (!REQUIRED_BLOCKERS.includes(blocker)) {
      fail(`unsupported XCM production evidence blocker: ${blocker}`);
    }
  }

  for (const marker of REQUIRED_READY_COMMAND_MARKERS) {
    if (!commands.includes(marker)) {
      fail(`readyVerificationCommands missing ${marker}`);
    }
  }

  for (const field of requiredEvidenceFields) {
    if (!REQUIRED_EVIDENCE_FIELDS.includes(field)) {
      fail(`unsupported required XCM production evidence field: ${field}`);
    }
  }

  for (const field of REQUIRED_EVIDENCE_FIELDS) {
    if (!requiredEvidenceFields.has(field)) {
      fail(`requiredEvidenceFields missing ${field}`);
    }
  }

  if (manifest.status === 'blocked' && manifest.releaseEnabled) {
    fail('releaseEnabled must remain false while status is blocked');
  }

  if (requireReady && manifest.status !== 'ready') {
    fail('status must be ready when --require-ready is used');
  }

  const readyClaimed = manifest.status === 'ready' || manifest.releaseEnabled || requireReady;
  const evidenceByRoute = new Map();
  const requiredRouteKeys = new Set(requiredRoutes.map(routeKey));
  const extrinsicHashes = new Set();

  evidence.forEach((entry, index) => {
    if (!entry || typeof entry !== 'object' || Array.isArray(entry)) {
      fail(`evidence[${index}] must be an object`);
      return;
    }

    assertAllowedKeys(entry, REQUIRED_EVIDENCE_FIELDS, `unsupported XCM production evidence[${index}] field`);

    for (const field of REQUIRED_EVIDENCE_FIELDS) {
      if (!nonEmptyString(entry[field])) {
        fail(`evidence[${index}].${field} must not be blank`);
      }
    }

    const key = routeKey(entry);
    if (!requiredRouteKeys.has(key)) {
      fail(`evidence[${index}] route is not declared in required route manifest: ${entry.originChainId} -> ${entry.destinationChainId} ${normalizeAssetSymbol(entry.assetSymbol)}`);
    }

    if (evidenceByRoute.has(key)) {
      fail(`duplicate E2E transfer evidence for route ${entry.originChainId} -> ${entry.destinationChainId} ${normalizeAssetSymbol(entry.assetSymbol)}`);
    } else {
      evidenceByRoute.set(key, entry);
    }

    if (!/^0x[0-9a-fA-F]{64}$/.test(String(entry.extrinsicHash || ''))) {
      fail(`evidence[${index}].extrinsicHash must be a 0x-prefixed 32-byte hash`);
    } else {
      const normalizedExtrinsicHash = String(entry.extrinsicHash).toLowerCase();
      if (isRepeatedHexPlaceholder(normalizedExtrinsicHash)) {
        fail(`evidence[${index}].extrinsicHash must not be a placeholder extrinsic hash`);
      }
      if (extrinsicHashes.has(normalizedExtrinsicHash)) {
        fail(`duplicate E2E transfer extrinsicHash: ${normalizedExtrinsicHash}`);
      } else {
        extrinsicHashes.add(normalizedExtrinsicHash);
      }
    }

    if (!/^\d+(\.\d+)?$/.test(String(entry.amount || ''))) {
      fail(`evidence[${index}].amount must be a positive decimal string`);
    } else if (Number(entry.amount) <= 0) {
      fail(`evidence[${index}].amount must be greater than zero`);
    }

    if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(String(entry.timestamp || ''))) {
      fail(`evidence[${index}].timestamp must be an ISO-8601 UTC second timestamp`);
    }

    if (!['mainnet', 'testnet'].includes(String(entry.environment || ''))) {
      fail(`evidence[${index}].environment must be mainnet or testnet`);
    }
  });

  const missingEvidenceRoutes = requiredRoutes.filter((route) => !evidenceByRoute.has(routeKey(route)));
  const expectedBlockedReasons = new Map();
  expectedBlockedReasons.set('e2e-transfer-evidence-missing', missingEvidenceRoutes.length > 0);
  expectedBlockedReasons.set('all-routes-executable-gate-not-green', discoveryGaps.length > 0);
  expectedBlockedReasons.set('discovery-only-routes-remain', discoveryGaps.length > 0);

  if (manifest.status === 'blocked') {
    for (const [blocker, active] of expectedBlockedReasons.entries()) {
      if (active && !blockers.has(blocker)) {
        fail(`blocked evidence missing blocker ${blocker}`);
      }
      if (!active && blockers.has(blocker)) {
        fail(`blocked evidence has stale blocker ${blocker}`);
      }
    }
  }

  if (readyClaimed) {
    if (!manifest.releaseEnabled) {
      fail('releaseEnabled must be true when status is ready or --require-ready is used');
    }
    if (Array.isArray(manifest.blockers) && manifest.blockers.length > 0) {
      fail('blockers must be empty when XCM production evidence is ready');
    }

    if (discoveryGaps.length > 0) {
      fail(`ready evidence cannot have discovery-only routes remaining: ${discoveryGaps.length}`);
    }

    for (const route of missingEvidenceRoutes) {
        fail(`ready evidence missing E2E transfer evidence for route ${route.originChainId} -> ${route.destinationChainId} ${route.assetSymbol}`);
    }
  }
}

if (errors.length > 0) {
  for (const error of errors) {
    console.error(`[xcm-production-evidence][error] ${error}`);
  }
  process.exit(1);
}

const status = manifest ? manifest.status : 'unknown';
const releaseEnabled = manifest ? manifest.releaseEnabled : false;
console.log(`[xcm-production-evidence] status=${status} releaseEnabled=${releaseEnabled} requiredRoutes=${requiredRoutes.length} discoveryOnlyRoutes=${discoveryGaps.length}`);
NODE
