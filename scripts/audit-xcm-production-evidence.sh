#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${XCM_PRODUCTION_EVIDENCE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
EVIDENCE_FILE="$ROOT_DIR/scripts/xcm-production-evidence.json"
REQUIRED_ROUTE_FILE="$ROOT_DIR/scripts/xcm-required-routes.tsv"
APPROVED_ROUTE_FILE="$ROOT_DIR/runtime/src/main/assets/approved_xcm_routes.tsv"
DISCOVERY_GAP_FILE="$ROOT_DIR/scripts/xcm-discovery-only-routes.tsv"
EFFECTIVE_REGISTRY_REPORT="$ROOT_DIR/build/reports/xcm-effective-registry-report.json"
REQUIRE_READY=false

usage() {
  cat <<'USAGE'
Usage: scripts/audit-xcm-production-evidence.sh [--evidence <path>] [--required-route-file <path>] [--approved-route-file <path>] [--discovery-gap-file <path>] [--effective-registry-report <path>] [--require-ready]

Validates the Android XCM production evidence manifest. The default audit allows
the current blocked state, but rejects any release-enabled or ready claim unless
all required route evidence is present and no discovery-only XCM gaps remain.
Ready evidence must also record androidCommit for every route and match it to
XCM_PRODUCTION_EXPECTED_COMMIT when set, otherwise the local Android git HEAD.
Each route must include independently attested finalized origin and destination
proof, success outcomes, a positive destination balance delta, and public HTTPS
verification links. This audit validates the evidence contract and attestation;
release reviewers must still verify those public proofs against canonical RPCs.
When evidence contains valid timestamps, lastReviewed is required and the UTC
calendar date of every timestamp and verifiedAt must be on or before it.

--require-ready additionally fails unless the manifest is marked ready for broad
production XCM release and the referenced effective-registry report is a complete
canonical live-discovery report whose approved/effective routes and local input
content identities match this audit's route manifests and bundled registry.
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
    --approved-route-file)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --approved-route-file requires a path" >&2; exit 2; }
      APPROVED_ROUTE_FILE="$2"
      shift 2
      ;;
    --discovery-gap-file)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --discovery-gap-file requires a path" >&2; exit 2; }
      DISCOVERY_GAP_FILE="$2"
      shift 2
      ;;
    --effective-registry-report)
      [[ $# -ge 2 ]] || { echo "[xcm-production-evidence][error] --effective-registry-report requires a path" >&2; exit 2; }
      EFFECTIVE_REGISTRY_REPORT="$2"
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

node - "$ROOT_DIR" "$EVIDENCE_FILE" "$REQUIRED_ROUTE_FILE" "$APPROVED_ROUTE_FILE" "$DISCOVERY_GAP_FILE" "$EFFECTIVE_REGISTRY_REPORT" "$REQUIRE_READY" <<'NODE'
const childProcess = require('child_process');
const crypto = require('crypto');
const fs = require('fs');
const net = require('net');
const path = require('path');

const [rootDir, evidenceFile, requiredRouteFile, approvedRouteFile, discoveryGapFile, effectiveRegistryReportFile, requireReadyRaw] = process.argv.slice(2);
const requireReady = requireReadyRaw === 'true';
const errors = [];
const MAX_CLOCK_SKEW_MS = 5 * 60 * 1000;
const EXPECTED_ANDROID_COMMIT_ENV = 'XCM_PRODUCTION_EXPECTED_COMMIT';
const PRODUCTION_DISCOVERY_URL = 'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json';
const BUNDLED_REGISTRY_FILE = path.join(rootDir, 'runtime/src/main/assets/local_chains.json');
const EFFECTIVE_REGISTRY_INPUT_SOURCES = {
  approvedRoutes: 'runtime/src/main/assets/approved_xcm_routes.tsv',
  requiredRoutes: 'scripts/xcm-required-routes.tsv',
  bundledRegistry: 'runtime/src/main/assets/local_chains.json'
};
const EFFECTIVE_REGISTRY_POLICY = {
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
  releaseDiscoveryUrl: PRODUCTION_DISCOVERY_URL
};
const SECRET_VALUE_PATTERN =
  /(?:AKIA[0-9A-Z]{16}|gh[pousr]_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,})/u;

const REQUIRED_BLOCKERS = [
  'e2e-transfer-evidence-missing',
  'independent-on-chain-verification-missing',
  'all-routes-executable-gate-not-green',
  'discovery-only-routes-remain'
];

const REQUIRED_EVIDENCE_FIELDS = [
  'originChainId',
  'destinationChainId',
  'assetSymbol',
  'extrinsicHash',
  'originBlockHash',
  'originBlockNumber',
  'originFinalized',
  'originExtrinsicSucceeded',
  'sender',
  'recipient',
  'amount',
  'timestamp',
  'destinationBlockHash',
  'destinationBlockNumber',
  'destinationEventSucceeded',
  'destinationBalanceDelta',
  'originVerificationUrl',
  'destinationVerificationUrl',
  'verificationMethod',
  'verifiedAt',
  'independentVerifier',
  'environment',
  'operator',
  'androidCommit'
];

const BOOLEAN_EVIDENCE_FIELDS = new Set([
  'originFinalized',
  'originExtrinsicSucceeded',
  'destinationEventSucceeded'
]);

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
  'approvedExecutableRoutes',
  'discoveryOnlyRoutes',
  'gapReport',
  'effectiveRegistryReport'
];

const REQUIRED_READY_COMMAND_MARKERS = [
  'test-xcm-production-evidence-template.sh',
  'test-xcm-production-evidence-audit.sh',
  'test-xcm-effective-registry-audit.sh',
  'audit-xcm-effective-registry.sh --write-report build/reports/xcm-effective-registry-report.json',
  'audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready',
  'audit-xcm-registry-metadata.sh --require-executable --require-all-routes-executable',
  '--require-route-file scripts/xcm-required-routes.tsv',
  '--require-gap-file scripts/xcm-discovery-only-routes.tsv',
  'public-shared-features-xcm:testDebugUnitTest',
  'ApprovedXcmRouteRegistryTest',
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

function isGitCommit(value) {
  return /^[0-9a-fA-F]{40}$/.test(String(value || '').trim());
}

function isPlaceholderText(value) {
  const normalized = String(value || '').trim().toLowerCase();
  return /^(todo|tbd|placeholder|example|sample|dummy|unknown|n\/a)(?:$|[_\-\s:])/u.test(normalized);
}

function validatePublicOperator(value, path) {
  if (typeof value !== 'string') return;
  if (/[\u0000-\u001f\u007f]/u.test(value)) {
    fail(`${path}: operator must be a single-line public value`);
  }
  if (SECRET_VALUE_PATTERN.test(value)) {
    fail(`${path}: operator must not contain secret-like token`);
  }
  if (isPlaceholderText(value)) {
    fail(`${path}: operator must not be a placeholder operator`);
  }
}

function validatePublicEvidenceUrl(value, path) {
  if (typeof value !== 'string') return null;
  let parsed;
  try {
    parsed = new URL(value);
  } catch (_error) {
    fail(`${path} must be a valid public HTTPS URL`);
    return null;
  }
  if (parsed.protocol !== 'https:') fail(`${path} must use HTTPS`);
  if (parsed.username || parsed.password) fail(`${path} must not contain credentials`);
  if (parsed.port) fail(`${path} must use the default HTTPS port`);
  if (parsed.search || parsed.hash) fail(`${path} must not contain a query or fragment`);
  const hostname = parsed.hostname.toLowerCase().replace(/^\[|\]$/gu, '');
  if (
    hostname.endsWith('.') ||
    net.isIP(hostname) !== 0 ||
    !hostname.includes('.') ||
    hostname === 'localhost' ||
    hostname.endsWith('.localhost') ||
    hostname.endsWith('.local') ||
    hostname.endsWith('.internal') ||
    hostname.endsWith('.onion') ||
    hostname.endsWith('.invalid') ||
    hostname.endsWith('.test') ||
    hostname === 'example.com' ||
    hostname.endsWith('.example.com')
  ) {
    fail(`${path} must use a public non-placeholder DNS host`);
  }
  if (parsed.pathname === '/' || parsed.pathname.length < 2) {
    fail(`${path} must link to a specific public proof`);
  }
  return parsed;
}

function validateBlockHash(value, path) {
  if (!/^0x[0-9a-fA-F]{64}$/.test(String(value || ''))) {
    fail(`${path} must be a 0x-prefixed 32-byte hash`);
  } else if (isRepeatedHexPlaceholder(value)) {
    fail(`${path} must not be a placeholder block hash`);
  }
}

function validatePositiveIntegerString(value, path) {
  if (!/^[1-9][0-9]*$/.test(String(value || ''))) {
    fail(`${path} must be a canonical positive integer string`);
  }
}

function resolveExpectedAndroidCommit(readyClaimed) {
  const override = process.env[EXPECTED_ANDROID_COMMIT_ENV];
  if (override !== undefined && override.trim().length > 0) {
    if (!isGitCommit(override)) {
      fail(`${EXPECTED_ANDROID_COMMIT_ENV} must be a 40-character git commit`);
      return null;
    }
    return override.trim().toLowerCase();
  }

  try {
    const commit = childProcess.execFileSync('git', ['-C', rootDir, 'rev-parse', 'HEAD'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore']
    }).trim();
    if (!isGitCommit(commit)) {
      fail('git -C root rev-parse HEAD did not return a 40-character Android release commit');
      return null;
    }
    return commit.toLowerCase();
  } catch (error) {
    if (readyClaimed) {
      fail('ready XCM production evidence requires XCM_PRODUCTION_EXPECTED_COMMIT or a local Android git HEAD source');
    }
    return null;
  }
}

function isIsoUtcSecond(value) {
  const text = String(value || '');
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(text)) return false;
  const millis = Date.parse(text);
  return Number.isFinite(millis) && new Date(millis).toISOString() === text.replace(/Z$/u, '.000Z');
}

function isFutureTimestamp(value) {
  const millis = Date.parse(value);
  return Number.isFinite(millis) && millis > Date.now() + MAX_CLOCK_SKEW_MS;
}

function parseUtcDate(value) {
  const text = String(value || '');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(text)) {
    return { ok: false, reason: 'format' };
  }

  const millis = Date.parse(`${text}T00:00:00Z`);
  if (!Number.isFinite(millis)) {
    return { ok: false, reason: 'invalid' };
  }

  const canonical = new Date(millis).toISOString().slice(0, 10);
  if (canonical !== text) {
    return { ok: false, reason: 'invalid' };
  }

  return { ok: true, millis };
}

function validateEvidenceUtcDateNotAfterLastReviewed(value, path, lastReviewedMillis) {
  if (!isIsoUtcSecond(value) || lastReviewedMillis === null) {
    return;
  }

  const utcDateMillis = Date.parse(`${value.slice(0, 10)}T00:00:00Z`);
  if (utcDateMillis > lastReviewedMillis) {
    fail(`${path} UTC date must be on or before lastReviewed`);
  }
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

function secretLikeValueReason(value, path = '$') {
  if (typeof value === 'string') {
    if (SECRET_VALUE_PATTERN.test(value)) {
      return `${path} must not contain secret-like token`;
    }
    return null;
  }

  if (!value || typeof value !== 'object') {
    return null;
  }

  if (Array.isArray(value)) {
    for (let index = 0; index < value.length; index += 1) {
      const reason = secretLikeValueReason(value[index], `${path}[${index}]`);
      if (reason) {
        return reason;
      }
    }
    return null;
  }

  for (const [key, nested] of Object.entries(value)) {
    const reason = secretLikeValueReason(nested, `${path}.${key}`);
    if (reason) {
      return reason;
    }
  }

  return null;
}

function parseRequiredRoutes(file, description = 'required route file') {
  const text = readText(file, description);
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
      fail(`Invalid ${description} line ${index + 1} in ${file}: expected origin destination asset`);
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

function readRegularFileBuffer(file, description) {
  if (!fs.existsSync(file)) {
    fail(`${description} missing: ${file}`);
    return null;
  }

  try {
    const stat = fs.lstatSync(file);
    if (stat.isSymbolicLink() || !stat.isFile()) {
      fail(`${description} must be a regular non-symlink file: ${file}`);
      return null;
    }
    return fs.readFileSync(file);
  } catch (error) {
    fail(`${description} could not be read: ${error.message}`);
    return null;
  }
}

function validateEffectiveContentIdentity(value, label, expectedSource, expectedFile) {
  const identity = requireObject(value, label);
  assertAllowedKeys(identity, ['source', 'byteLength', 'sha256'], `${label} has unsupported field`);
  if (identity.source !== expectedSource) {
    fail(`${label}.source must be ${expectedSource}`);
  }
  if (!Number.isInteger(identity.byteLength) || identity.byteLength <= 0) {
    fail(`${label}.byteLength must be a positive integer`);
  }
  if (!/^[a-f0-9]{64}$/.test(String(identity.sha256 || ''))) {
    fail(`${label}.sha256 must be a lowercase SHA-256`);
  }

  const content = readRegularFileBuffer(expectedFile, `${label} source`);
  if (content === null) return;
  const digest = crypto.createHash('sha256').update(content).digest('hex');
  if (identity.byteLength !== content.length) {
    fail(`${label}.byteLength must match current source bytes`);
  }
  if (identity.sha256 !== digest) {
    fail(`${label}.sha256 must match current source bytes`);
  }
}

function validateEffectiveRouteIdentity(route, label, allowedKeys) {
  const value = requireObject(route, label);
  assertAllowedKeys(value, allowedKeys, `${label} has unsupported field`);
  if (!nonEmptyString(value.originChainId)) fail(`${label}.originChainId must not be blank`);
  if (!nonEmptyString(value.destinationChainId)) fail(`${label}.destinationChainId must not be blank`);
  if (!nonEmptyString(value.assetSymbol)) fail(`${label}.assetSymbol must not be blank`);
  if (value.assetSymbol !== normalizeAssetSymbol(value.assetSymbol)) {
    fail(`${label}.assetSymbol must be canonical uppercase without an xc prefix`);
  }
  return value;
}

function validateReadyEffectiveRegistryReport(file, requiredRoutes, approvedRoutes) {
  const reportBuffer = readRegularFileBuffer(file, 'effective registry report');
  if (reportBuffer === null) return;

  let report;
  try {
    report = JSON.parse(reportBuffer.toString('utf8'));
  } catch (error) {
    fail(`effective registry report must be valid JSON: ${error.message}`);
    return;
  }

  const value = requireObject(report, 'effective registry report');
  assertAllowedKeys(
    value,
    ['schemaVersion', 'mode', 'status', 'policy', 'inputs', 'summary', 'routes', 'missing', 'extra'],
    'effective registry report has unsupported field'
  );
  if (value.schemaVersion !== 1) fail('effective registry report schemaVersion must be 1');
  if (value.mode !== 'discovery') fail('ready effective registry report mode must be discovery');
  if (value.status !== 'complete') fail('ready effective registry report status must be complete');

  const policy = requireObject(value.policy, 'effective registry report policy');
  assertAllowedKeys(policy, Object.keys(EFFECTIVE_REGISTRY_POLICY), 'effective registry report policy has unsupported field');
  for (const [key, expected] of Object.entries(EFFECTIVE_REGISTRY_POLICY)) {
    if (policy[key] !== expected) {
      fail(`effective registry report policy.${key} must be ${JSON.stringify(expected)}`);
    }
  }

  const inputs = requireObject(value.inputs, 'effective registry report inputs');
  assertAllowedKeys(
    inputs,
    ['approvedRoutes', 'requiredRoutes', 'bundledRegistry', 'discoveryRegistry'],
    'effective registry report inputs has unsupported field'
  );
  validateEffectiveContentIdentity(
    inputs.approvedRoutes,
    'effective registry report inputs.approvedRoutes',
    EFFECTIVE_REGISTRY_INPUT_SOURCES.approvedRoutes,
    approvedRouteFile
  );
  validateEffectiveContentIdentity(
    inputs.requiredRoutes,
    'effective registry report inputs.requiredRoutes',
    EFFECTIVE_REGISTRY_INPUT_SOURCES.requiredRoutes,
    requiredRouteFile
  );
  validateEffectiveContentIdentity(
    inputs.bundledRegistry,
    'effective registry report inputs.bundledRegistry',
    EFFECTIVE_REGISTRY_INPUT_SOURCES.bundledRegistry,
    BUNDLED_REGISTRY_FILE
  );
  const discovery = requireObject(inputs.discoveryRegistry, 'effective registry report inputs.discoveryRegistry');
  assertAllowedKeys(
    discovery,
    ['kind', 'source', 'byteLength', 'sha256'],
    'effective registry report inputs.discoveryRegistry has unsupported field'
  );
  if (discovery.kind !== 'https') fail('ready effective registry report discovery kind must be https');
  if (discovery.source !== PRODUCTION_DISCOVERY_URL) {
    fail(`ready effective registry report discovery source must be ${PRODUCTION_DISCOVERY_URL}`);
  }
  if (!Number.isInteger(discovery.byteLength) || discovery.byteLength <= 0) {
    fail('ready effective registry report discovery byteLength must be a positive integer');
  }
  if (!/^[a-f0-9]{64}$/.test(String(discovery.sha256 || ''))) {
    fail('ready effective registry report discovery sha256 must be a lowercase SHA-256');
  }

  const summary = requireObject(value.summary, 'effective registry report summary');
  const summaryFields = ['approved', 'required', 'bundledExecutable', 'discovered', 'effective', 'productionExecutable', 'missing', 'extra'];
  assertAllowedKeys(summary, summaryFields, 'effective registry report summary has unsupported field');
  for (const field of summaryFields) {
    if (!Number.isInteger(summary[field]) || summary[field] < 0) {
      fail(`effective registry report summary.${field} must be a non-negative integer`);
    }
  }

  const requiredKeys = new Set(requiredRoutes.map(routeKey));
  const approvedKeys = new Set(approvedRoutes.map(routeKey));
  const routes = requireArray(value.routes, 'effective registry report routes');
  const routeKeys = new Set();
  routes.forEach((entry, index) => {
    const route = validateEffectiveRouteIdentity(
      entry,
      `effective registry report routes[${index}]`,
      ['originChainId', 'destinationChainId', 'assetSymbol', 'effective', 'productionExecutable', 'reasons']
    );
    const key = routeKey(route);
    if (routeKeys.has(key)) fail(`effective registry report contains duplicate route ${key}`);
    routeKeys.add(key);
    if (!requiredKeys.has(key) || !approvedKeys.has(key)) {
      fail(`effective registry report contains route outside approved/required manifests: ${key}`);
    }
    if (route.effective !== true) fail(`ready effective registry report route must be effective: ${key}`);
    if (route.productionExecutable !== false) {
      fail(`ready effective registry report route productionExecutable must remain false while the release flag is disabled: ${key}`);
    }
    if (!Array.isArray(route.reasons) || route.reasons.length !== 0) {
      fail(`ready effective registry report route reasons must be empty: ${key}`);
    }
  });
  for (const key of requiredKeys) {
    if (!routeKeys.has(key)) fail(`ready effective registry report missing approved route: ${key}`);
  }

  const missing = requireArray(value.missing, 'effective registry report missing');
  if (missing.length !== 0) fail('ready effective registry report missing routes must be empty');
  const extra = requireArray(value.extra, 'effective registry report extra');
  const extraKeys = new Set();
  extra.forEach((entry, index) => {
    const route = validateEffectiveRouteIdentity(
      entry,
      `effective registry report extra[${index}]`,
      ['originChainId', 'destinationChainId', 'assetSymbol']
    );
    const key = routeKey(route);
    if (extraKeys.has(key)) fail(`effective registry report contains duplicate extra route ${key}`);
    extraKeys.add(key);
    if (routeKeys.has(key)) fail(`effective registry report extra route must not be approved: ${key}`);
  });

  if (summary.approved !== approvedKeys.size) fail('ready effective registry report summary.approved must match approved route manifest');
  if (summary.required !== requiredKeys.size) fail('ready effective registry report summary.required must match required route manifest');
  if (summary.bundledExecutable !== approvedKeys.size) fail('ready effective registry report summary.bundledExecutable must match approved routes');
  if (summary.effective !== requiredKeys.size || summary.effective !== routeKeys.size) {
    fail('ready effective registry report summary.effective must match every approved/required route');
  }
  if (summary.productionExecutable !== 0) {
    fail('ready effective registry report summary.productionExecutable must remain zero while the release flag is disabled');
  }
  if (summary.missing !== 0) fail('ready effective registry report summary.missing must be zero');
  if (summary.extra !== extra.length) fail('ready effective registry report summary.extra must match extra routes');
  if (summary.discovered !== summary.effective + summary.extra) {
    fail('ready effective registry report summary.discovered must equal effective plus extra routes');
  }
}

const manifest = parseJson(evidenceFile, 'XCM production evidence manifest');
const requiredRoutes = parseRequiredRoutes(requiredRouteFile, 'required route file');
const approvedRoutes = parseRequiredRoutes(approvedRouteFile, 'approved route file');
const discoveryGaps = parseDiscoveryGaps(discoveryGapFile);

const requiredRouteKeysForParity = new Set(requiredRoutes.map(routeKey));
const approvedRouteKeysForParity = new Set(approvedRoutes.map(routeKey));
if (requiredRouteKeysForParity.size !== requiredRoutes.length) {
  fail('required route manifest contains duplicate normalized routes');
}
if (approvedRouteKeysForParity.size !== approvedRoutes.length) {
  fail('approved route manifest contains duplicate normalized routes');
}
for (const route of approvedRoutes) {
  if (!requiredRouteKeysForParity.has(routeKey(route))) {
    fail(`required route manifest missing approved route: ${route.originChainId} -> ${route.destinationChainId} ${route.assetSymbol}`);
  }
}
for (const route of requiredRoutes) {
  if (!approvedRouteKeysForParity.has(routeKey(route))) {
    fail(`approved route manifest missing required route: ${route.originChainId} -> ${route.destinationChainId} ${route.assetSymbol}`);
  }
}

if (manifest) {
  const secretReason = secretLikeKeyReason(manifest);
  if (secretReason) {
    fail(secretReason);
  }
  const secretValueReason = secretLikeValueReason(manifest);
  if (secretValueReason) {
    fail(secretValueReason);
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

  let lastReviewedMillis = null;
  if (manifest.lastReviewed !== undefined) {
    const lastReviewed = parseUtcDate(manifest.lastReviewed);
    if (!lastReviewed.ok) {
      fail(lastReviewed.reason === 'format' ? 'lastReviewed must be YYYY-MM-DD when present' : 'lastReviewed must be a valid YYYY-MM-DD date');
    } else {
      lastReviewedMillis = lastReviewed.millis;
      if (lastReviewed.millis > Date.now() + MAX_CLOCK_SKEW_MS) {
        fail('lastReviewed must not be in the future');
      }
    }
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
  if (routeManifests.approvedExecutableRoutes !== 'runtime/src/main/assets/approved_xcm_routes.tsv') {
    fail('routeManifests.approvedExecutableRoutes must be runtime/src/main/assets/approved_xcm_routes.tsv');
  }
  if (routeManifests.discoveryOnlyRoutes !== 'scripts/xcm-discovery-only-routes.tsv') {
    fail('routeManifests.discoveryOnlyRoutes must be scripts/xcm-discovery-only-routes.tsv');
  }
  if (routeManifests.gapReport !== 'build/reports/xcm-registry-gap-report.json') {
    fail('routeManifests.gapReport must be build/reports/xcm-registry-gap-report.json');
  }
  if (routeManifests.effectiveRegistryReport !== 'build/reports/xcm-effective-registry-report.json') {
    fail('routeManifests.effectiveRegistryReport must be build/reports/xcm-effective-registry-report.json');
  }

  const manifestBlockers = requireArray(manifest.blockers, 'blockers');
  const blockers = new Set(manifestBlockers);
  const commandList = requireArray(manifest.readyVerificationCommands, 'readyVerificationCommands');
  const commands = commandList.join('\n');
  const requiredEvidenceFieldList = requireArray(manifest.requiredEvidenceFields, 'requiredEvidenceFields');
  const requiredEvidenceFields = new Set(requiredEvidenceFieldList);
  const evidence = requireArray(manifest.evidence, 'evidence');

  const evidenceHasValidTimestamp = evidence.some((entry) =>
    entry && typeof entry === 'object' && !Array.isArray(entry) &&
      (isIsoUtcSecond(entry.timestamp) || isIsoUtcSecond(entry.verifiedAt))
  );
  if (manifest.lastReviewed === undefined && evidenceHasValidTimestamp) {
    fail('lastReviewed is required when evidence contains valid timestamps');
  }

  if (blockers.size !== manifestBlockers.length) {
    fail('duplicate XCM production evidence blocker');
  }
  if (new Set(commandList).size !== commandList.length) {
    fail('duplicate XCM production evidence verification command');
  }
  if (requiredEvidenceFields.size !== requiredEvidenceFieldList.length) {
    fail('duplicate XCM production evidence required field');
  }

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
  const expectedAndroidCommit = readyClaimed ? resolveExpectedAndroidCommit(readyClaimed) : null;
  if (readyClaimed) {
    validateReadyEffectiveRegistryReport(effectiveRegistryReportFile, requiredRoutes, approvedRoutes);
  }
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
      if (BOOLEAN_EVIDENCE_FIELDS.has(field)) {
        if (typeof entry[field] !== 'boolean') {
          fail(`evidence[${index}].${field} must be a boolean`);
        }
      } else if (!nonEmptyString(entry[field])) {
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

    validateBlockHash(entry.originBlockHash, `evidence[${index}].originBlockHash`);
    validatePositiveIntegerString(entry.originBlockNumber, `evidence[${index}].originBlockNumber`);
    if (entry.originFinalized !== true) {
      fail(`evidence[${index}].originFinalized must be true`);
    }
    if (entry.originExtrinsicSucceeded !== true) {
      fail(`evidence[${index}].originExtrinsicSucceeded must be true`);
    }
    if (
      /^0x[0-9a-fA-F]{64}$/.test(String(entry.originBlockHash || '')) &&
      String(entry.originBlockHash).toLowerCase() === String(entry.extrinsicHash || '').toLowerCase()
    ) {
      fail(`evidence[${index}].originBlockHash must differ from extrinsicHash`);
    }

    validateBlockHash(entry.destinationBlockHash, `evidence[${index}].destinationBlockHash`);
    validatePositiveIntegerString(entry.destinationBlockNumber, `evidence[${index}].destinationBlockNumber`);
    if (entry.destinationEventSucceeded !== true) {
      fail(`evidence[${index}].destinationEventSucceeded must be true`);
    }
    if (
      /^0x[0-9a-fA-F]{64}$/.test(String(entry.originBlockHash || '')) &&
      String(entry.originBlockHash).toLowerCase() === String(entry.destinationBlockHash || '').toLowerCase()
    ) {
      fail(`evidence[${index}] origin and destination block hashes must differ`);
    }
    if (!/^\d+(\.\d+)?$/.test(String(entry.destinationBalanceDelta || '')) || Number(entry.destinationBalanceDelta) <= 0) {
      fail(`evidence[${index}].destinationBalanceDelta must be a positive decimal string`);
    }

    const originVerificationUrl = validatePublicEvidenceUrl(
      entry.originVerificationUrl,
      `evidence[${index}].originVerificationUrl`
    );
    const destinationVerificationUrl = validatePublicEvidenceUrl(
      entry.destinationVerificationUrl,
      `evidence[${index}].destinationVerificationUrl`
    );
    if (
      originVerificationUrl &&
      destinationVerificationUrl &&
      originVerificationUrl.href === destinationVerificationUrl.href
    ) {
      fail(`evidence[${index}] origin and destination verification URLs must differ`);
    }
    if (entry.verificationMethod !== 'canonical-rpc-and-explorer') {
      fail(`evidence[${index}].verificationMethod must be canonical-rpc-and-explorer`);
    }
    if (!isIsoUtcSecond(entry.verifiedAt)) {
      fail(`evidence[${index}].verifiedAt must be an ISO-8601 UTC second timestamp`);
    } else {
      validateEvidenceUtcDateNotAfterLastReviewed(
        entry.verifiedAt,
        `evidence[${index}].verifiedAt`,
        lastReviewedMillis
      );
      if (isFutureTimestamp(entry.verifiedAt)) {
        fail(`evidence[${index}].verifiedAt must not be in the future`);
      }
      const transferMillis = Date.parse(entry.timestamp);
      const verificationMillis = Date.parse(entry.verifiedAt);
      if (Number.isFinite(transferMillis) && Number.isFinite(verificationMillis) && verificationMillis < transferMillis) {
        fail(`evidence[${index}].verifiedAt must not precede the transfer timestamp`);
      }
    }
    validatePublicOperator(entry.independentVerifier, `evidence[${index}].independentVerifier`);
    if (
      nonEmptyString(entry.independentVerifier) &&
      nonEmptyString(entry.operator) &&
      entry.independentVerifier.trim().toLowerCase() === entry.operator.trim().toLowerCase()
    ) {
      fail(`evidence[${index}].independentVerifier must differ from operator`);
    }

    if (!/^\d+(\.\d+)?$/.test(String(entry.amount || ''))) {
      fail(`evidence[${index}].amount must be a positive decimal string`);
    } else if (Number(entry.amount) <= 0) {
      fail(`evidence[${index}].amount must be greater than zero`);
    }

    if (!isIsoUtcSecond(entry.timestamp)) {
      fail(`evidence[${index}].timestamp must be an ISO-8601 UTC second timestamp`);
    } else {
      validateEvidenceUtcDateNotAfterLastReviewed(
        entry.timestamp,
        `evidence[${index}].timestamp`,
        lastReviewedMillis
      );
      if (isFutureTimestamp(entry.timestamp)) {
        fail(`evidence[${index}].timestamp must not be in the future`);
      }
    }

    if (!['mainnet', 'testnet'].includes(String(entry.environment || ''))) {
      fail(`evidence[${index}].environment must be mainnet or testnet`);
    }
    if (readyClaimed && entry.environment !== 'mainnet') {
      fail(`evidence[${index}].environment must be mainnet when XCM production evidence is ready`);
    }
    if (isPlaceholderText(entry.sender)) {
      fail(`evidence[${index}].sender must not be a placeholder public address`);
    }
    if (isPlaceholderText(entry.recipient)) {
      fail(`evidence[${index}].recipient must not be a placeholder public address`);
    }
    if (String(entry.sender || '').trim() === String(entry.recipient || '').trim()) {
      fail(`evidence[${index}].sender and recipient must differ`);
    }
    validatePublicOperator(entry.operator, `evidence[${index}].operator`);

    if (!isGitCommit(entry.androidCommit)) {
      fail(`evidence[${index}].androidCommit must be a 40-character git commit`);
    } else {
      const normalizedAndroidCommit = String(entry.androidCommit).trim().toLowerCase();
      if (isRepeatedHexPlaceholder(normalizedAndroidCommit)) {
        fail(`evidence[${index}].androidCommit must not be a placeholder Android release commit`);
      }
      if (readyClaimed && expectedAndroidCommit && normalizedAndroidCommit !== expectedAndroidCommit) {
        fail(`evidence[${index}].androidCommit must match expected Android release commit ${expectedAndroidCommit}`);
      }
    }
  });

  const missingEvidenceRoutes = requiredRoutes.filter((route) => !evidenceByRoute.has(routeKey(route)));
  const expectedBlockedReasons = new Map();
  expectedBlockedReasons.set('e2e-transfer-evidence-missing', missingEvidenceRoutes.length > 0);
  expectedBlockedReasons.set('independent-on-chain-verification-missing', missingEvidenceRoutes.length > 0);
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
