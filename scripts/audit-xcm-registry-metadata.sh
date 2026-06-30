#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${XCM_REGISTRY_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
REGISTRY_FILE="$ROOT_DIR/runtime/src/main/assets/local_chains.json"
REQUIRE_EXECUTABLE=false
REQUIRE_ALL_ROUTES_EXECUTABLE=false
GAP_REPORT_FILE=""
REQUIRED_ROUTES=()
REQUIRED_ROUTE_FILES=()
REQUIRED_GAP_FILES=()

usage() {
  cat <<'USAGE'
Usage: scripts/audit-xcm-registry-metadata.sh [--registry <path>] [--require-executable] [--require-all-routes-executable] [--write-gap-report <path>] [--require-route <origin-chain-id> <destination-chain-id> <asset-symbol>] [--require-route-file <path>] [--require-gap-file <path>]

Validates Android local_chains.json XCM route metadata and any committed
execution specs. By default the audit validates metadata shape and fails on any
malformed execution spec. Release hardening can add --require-executable and
--require-all-routes-executable to fail until real executable route specs are
committed for the public XCM backend.

--require-route can be repeated to require a specific executable route asset.
--require-route-file reads required executable routes from a whitespace-separated
file with origin chain id, destination chain id, and asset symbol columns.
--require-gap-file reads expected discovery-only routes from a
whitespace-separated file with origin chain id, destination chain id,
comma-separated asset symbols, reason category, and bridge parachain id columns.
--write-gap-report writes a deterministic JSON report listing advertised XCM
routes that are still discovery-only because they do not have execution specs.
USAGE
}

while (($#)); do
  case "$1" in
    --registry)
      [[ $# -ge 2 ]] || { echo "[xcm-registry][error] --registry requires a path" >&2; exit 2; }
      REGISTRY_FILE="$2"
      shift 2
      ;;
    --require-executable)
      REQUIRE_EXECUTABLE=true
      shift
      ;;
    --require-all-routes-executable)
      REQUIRE_ALL_ROUTES_EXECUTABLE=true
      shift
      ;;
    --write-gap-report)
      [[ $# -ge 2 ]] || { echo "[xcm-registry][error] --write-gap-report requires a path" >&2; exit 2; }
      GAP_REPORT_FILE="$2"
      shift 2
      ;;
    --require-route)
      [[ $# -ge 4 ]] || { echo "[xcm-registry][error] --require-route requires origin, destination, and asset" >&2; exit 2; }
      REQUIRED_ROUTES+=("$2|$3|$4")
      shift 4
      ;;
    --require-route-file)
      [[ $# -ge 2 ]] || { echo "[xcm-registry][error] --require-route-file requires a path" >&2; exit 2; }
      REQUIRED_ROUTE_FILES+=("$2")
      shift 2
      ;;
    --require-gap-file)
      [[ $# -ge 2 ]] || { echo "[xcm-registry][error] --require-gap-file requires a path" >&2; exit 2; }
      REQUIRED_GAP_FILES+=("$2")
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[xcm-registry][error] Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v node >/dev/null 2>&1; then
  echo "[xcm-registry][error] node is required for structured JSON validation" >&2
  exit 1
fi

required_routes_raw=""
if ((${#REQUIRED_ROUTES[@]} > 0)); then
  required_routes_raw="$(printf '%s\n' "${REQUIRED_ROUTES[@]}")"
fi

required_route_files_raw=""
if ((${#REQUIRED_ROUTE_FILES[@]} > 0)); then
  required_route_files_raw="$(printf '%s\n' "${REQUIRED_ROUTE_FILES[@]}")"
fi

required_gap_files_raw=""
if ((${#REQUIRED_GAP_FILES[@]} > 0)); then
  required_gap_files_raw="$(printf '%s\n' "${REQUIRED_GAP_FILES[@]}")"
fi

node - "$REGISTRY_FILE" "$REQUIRE_EXECUTABLE" "$REQUIRE_ALL_ROUTES_EXECUTABLE" "$required_routes_raw" "$GAP_REPORT_FILE" "$required_route_files_raw" "$required_gap_files_raw" <<'NODE'
const fs = require('fs');
const path = require('path');

const [
  registryFile,
  requireExecutableRaw,
  requireAllRoutesExecutableRaw,
  requiredRoutesRaw = '',
  gapReportFile = '',
  requiredRouteFilesRaw = '',
  requiredGapFilesRaw = ''
] = process.argv.slice(2);
const requireExecutable = requireExecutableRaw === 'true';
const requireAllRoutesExecutable = requireAllRoutesExecutableRaw === 'true';
const errors = [];
const executableRouteKeys = new Set();
const missingExecutableDestinations = [];
const requiredGaps = [];
const allowedGapReasons = new Set(['bridge-sora', 'non-native-asset', 'cross-parachain-native']);
const requireGapManifest = requiredGapFilesRaw.split('\n').filter(Boolean).length > 0;

function fail(message) {
  errors.push(message);
}

function label(...parts) {
  return parts.filter(Boolean).join(' ');
}

function nonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function requiredString(value, field, context) {
  if (!nonEmptyString(value)) {
    fail(`${context}: ${field} must not be blank`);
    return null;
  }

  return value.trim();
}

function nonNegativeInteger(value, field, context) {
  if (!Number.isInteger(value) || value < 0) {
    fail(`${context}: ${field} must be a non-negative integer`);
    return null;
  }

  return value;
}

function nonNegativeIntegerString(value, field, context) {
  if (value === undefined || value === null) {
    return null;
  }

  if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/.test(value.trim())) {
    fail(`${context}: ${field} must be a non-negative integer string`);
    return null;
  }

  return value.trim();
}

function normalizedEnum(value) {
  return String(value)
    .trim()
    .replace(/-/g, '_')
    .replace(/\s+/g, '_')
    .replace(/([a-z])([A-Z])/g, '$1_$2')
    .toUpperCase();
}

function normalizeAssetSymbol(value) {
  return String(value)
    .trim()
    .replace(/^xc/i, '')
    .toUpperCase();
}

function normalizeAssetSymbols(values) {
  return values
    .map(normalizeAssetSymbol)
    .filter(Boolean)
    .sort()
    .join(',');
}

const requiredRoutes = requiredRoutesRaw
  .split('\n')
  .filter(Boolean)
  .map((route) => {
    const parts = route.split('|');
    if (parts.length !== 3 || parts.some((part) => !nonEmptyString(part))) {
      fail(`Invalid required route declaration: ${route}`);
      return null;
    }
    return {
      originId: parts[0].trim(),
      destinationId: parts[1].trim(),
      assetSymbol: normalizeAssetSymbol(parts[2])
    };
  })
  .filter(Boolean);

function parseRequiredRouteLine(line, source, lineNumber) {
  const trimmed = line.replace(/\s+#.*$/, '').trim();
  if (!trimmed || trimmed.startsWith('#')) {
    return null;
  }

  const parts = trimmed.split(/\s+/);
  if (parts.length !== 3 || parts.some((part) => !nonEmptyString(part))) {
    fail(`Invalid required route file line ${lineNumber} in ${source}: expected origin destination asset`);
    return null;
  }

  return {
    originId: parts[0].trim(),
    destinationId: parts[1].trim(),
    assetSymbol: normalizeAssetSymbol(parts[2])
  };
}

function parseRequiredGapLine(line, source, lineNumber) {
  const trimmed = line.replace(/\s+#.*$/, '').trim();
  if (!trimmed || trimmed.startsWith('#')) {
    return null;
  }

  const parts = trimmed.split(/\s+/);
  if (parts.length !== 5 || parts.some((part) => !nonEmptyString(part))) {
    fail(`Invalid required gap file line ${lineNumber} in ${source}: expected origin destination assets reason bridgeParachainId`);
    return null;
  }

  const reason = parts[3].trim();
  if (!allowedGapReasons.has(reason)) {
    fail(`Invalid required gap file line ${lineNumber} in ${source}: unsupported reason ${reason}`);
    return null;
  }

  const assetSymbols = parts[2]
    .split(',')
    .map((symbol) => symbol.trim())
    .filter(Boolean);
  if (assetSymbols.length === 0) {
    fail(`Invalid required gap file line ${lineNumber} in ${source}: assets must not be blank`);
    return null;
  }

  const bridgeParachainId = parts[4].trim();
  return {
    originId: parts[0].trim(),
    destinationId: parts[1].trim(),
    assetSymbols: normalizeAssetSymbols(assetSymbols),
    reason,
    bridgeParachainId: bridgeParachainId === '-' ? null : bridgeParachainId
  };
}

for (const routeFile of requiredRouteFilesRaw.split('\n').filter(Boolean)) {
  if (!fs.existsSync(routeFile)) {
    fail(`Required route file missing: ${routeFile}`);
    continue;
  }

  const routeFileContents = fs.readFileSync(routeFile, 'utf8');
  routeFileContents.split(/\r?\n/).forEach((line, index) => {
    const route = parseRequiredRouteLine(line, routeFile, index + 1);
    if (route !== null) {
      requiredRoutes.push(route);
    }
  });
}

for (const gapFile of requiredGapFilesRaw.split('\n').filter(Boolean)) {
  if (!fs.existsSync(gapFile)) {
    fail(`Required gap file missing: ${gapFile}`);
    continue;
  }

  const gapFileContents = fs.readFileSync(gapFile, 'utf8');
  gapFileContents.split(/\r?\n/).forEach((line, index) => {
    const gap = parseRequiredGapLine(line, gapFile, index + 1);
    if (gap !== null) {
      requiredGaps.push(gap);
    }
  });
}

function gapKey(gap) {
  return `${gap.originId}|${gap.destinationId}|${gap.assetSymbols}`;
}

function classifyMissingDestination(destination) {
  if (destination.bridgeParachainId !== null) {
    return 'bridge-sora';
  }

  const assetSymbols = destination.assetSymbols.map(normalizeAssetSymbol);
  if (assetSymbols.some((symbol) => !['DOT', 'KSM'].includes(symbol))) {
    return 'non-native-asset';
  }

  return 'cross-parachain-native';
}

function requiredEnum(value, field, allowed, context) {
  const text = requiredString(value, field, context);
  if (text === null) return null;

  const normalized = normalizedEnum(text);
  if (!allowed.includes(normalized)) {
    fail(`${context}: ${field} is unsupported (${text})`);
    return null;
  }

  return normalized;
}

function optionalEnum(value, field, allowed, defaultValue, context) {
  if (value === undefined || value === null || String(value).trim() === '') {
    return defaultValue;
  }

  const normalized = normalizedEnum(value);
  if (!allowed.includes(normalized)) {
    const fieldMessage = field === 'argumentShape' ? 'argumentShape is unsupported' : `${field} is unsupported`;
    fail(`${context}: ${fieldMessage} (${value})`);
    return null;
  }

  return normalized;
}

function polkadotXcmCallName(transferType) {
  if (transferType === 'RESERVE_TRANSFER_ASSETS') return 'reserveTransferAssets';
  if (transferType === 'LIMITED_RESERVE_TRANSFER_ASSETS') return 'limitedReserveTransferAssets';
  if (transferType === 'TELEPORT_ASSETS') return 'teleportAssets';
  if (transferType === 'LIMITED_TELEPORT_ASSETS') return 'limitedTeleportAssets';
  return null;
}

function validateCallShape(palletName, callName, transferType, argumentShape, context) {
  if ([palletName, callName, transferType, argumentShape].some((value) => value === null)) {
    return;
  }

  if (argumentShape === 'POLKADOT_XCM_TRANSFER_ASSETS') {
    if (palletName !== 'PolkadotXcm') {
      fail(`${context}: PolkadotXcm transfer-assets argumentShape requires palletName PolkadotXcm`);
    }
    if (callName !== polkadotXcmCallName(transferType)) {
      fail(`${context}: PolkadotXcm transfer-assets argumentShape callName must match transferType`);
    }
    return;
  }

  if (argumentShape === 'X_TOKENS_TRANSFER_MULTIASSET') {
    if (palletName !== 'XTokens') {
      fail(`${context}: XTokens transferMultiasset argumentShape requires palletName XTokens`);
    }
    if (callName !== 'transferMultiasset') {
      fail(`${context}: XTokens transferMultiasset argumentShape requires callName transferMultiasset`);
    }
    if (transferType !== 'X_TOKENS_TRANSFER_MULTIASSET') {
      fail(`${context}: XTokens transferMultiasset argumentShape requires transferType xTokensTransferMultiasset`);
    }
  }
}

function requiredMultiLocation(value, field, context) {
  const mlContext = `${context}: ${field}`;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${mlContext} is required`);
    return;
  }

  nonNegativeInteger(value.parents, 'parents', mlContext);
  const interior = requiredString(value.interior, 'interior', mlContext);
  if (interior !== null) {
    validateMultiLocationInterior(interior, `${mlContext}.interior`);
  }
}

function splitTopLevel(value, context) {
  const parts = [];
  let start = 0;
  let parentheses = 0;
  let braces = 0;
  let brackets = 0;

  for (let index = 0; index < value.length; index += 1) {
    const char = value[index];
    if (char === '(') parentheses += 1;
    if (char === ')') parentheses -= 1;
    if (char === '{') braces += 1;
    if (char === '}') braces -= 1;
    if (char === '[') brackets += 1;
    if (char === ']') brackets -= 1;

    if (parentheses < 0 || braces < 0 || brackets < 0) {
      fail(`${context} has unbalanced delimiters`);
      return null;
    }

    if (char === ',' && parentheses === 0 && braces === 0 && brackets === 0) {
      parts.push(value.slice(start, index).trim());
      start = index + 1;
    }
  }

  if (parentheses !== 0 || braces !== 0 || brackets !== 0) {
    fail(`${context} has unbalanced delimiters`);
    return null;
  }

  parts.push(value.slice(start).trim());
  return parts.filter(Boolean);
}

function validateJunction(value, context) {
  const match = /^([A-Za-z][A-Za-z0-9]*)(?:\((.*)\))?$/.exec(value.trim());
  if (!match) {
    fail(`${context} has malformed junction`);
    return;
  }

  const type = normalizedEnum(match[1]);
  const argument = typeof match[2] === 'string' ? match[2].trim() : null;
  const requireArgument = (name) => {
    if (!argument) {
      fail(`${context} ${name} must include a value`);
      return null;
    }
    return argument;
  };
  const requireUnsignedInteger = (name) => {
    const parsed = requireArgument(name);
    if (parsed !== null && !/^(0|[1-9][0-9]*)$/.test(parsed)) {
      fail(`${context} ${name} must be a non-negative integer`);
    }
  };

  if (['PARACHAIN', 'PALLET_INSTANCE', 'GENERAL_INDEX'].includes(type)) {
    requireUnsignedInteger(match[1]);
  } else if (type === 'GENERAL_KEY') {
    requireArgument(match[1]);
  } else if (type === 'ACCOUNT_ID32' || type === 'ACCOUNT_KEY20') {
    const parsed = requireArgument(match[1]);
    if (parsed !== null && !parsed.includes('<account>')) {
      fail(`${context} ${match[1]} must include <account> recipient placeholder`);
    }
  } else {
    fail(`${context} has unsupported junction ${match[1]}`);
  }
}

function validateMultiLocationInterior(value, context) {
  if (value === 'Here') return;

  const match = /^X([1-8])\((.*)\)$/.exec(value);
  if (!match) {
    fail(`${context} must be Here or X1..X8 junctions`);
    return;
  }

  const expectedCount = Number(match[1]);
  const junctions = splitTopLevel(match[2], context);
  if (junctions === null) return;

  if (junctions.length !== expectedCount) {
    fail(`${context} declares X${expectedCount} but contains ${junctions.length} junctions`);
    return;
  }

  junctions.forEach((junction, index) => {
    validateJunction(junction, `${context} junction ${index + 1}`);
  });
}

function validateWeightLimit(value, context) {
  const weightContext = `${context}: weightLimit`;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${weightContext} is required`);
    return;
  }

  const type = requiredEnum(value.type, 'type', ['UNLIMITED', 'LIMITED'], weightContext);
  const refTime = nonNegativeIntegerString(value.refTime, 'refTime', weightContext);
  const proofSize = nonNegativeIntegerString(value.proofSize, 'proofSize', weightContext);

  if (type === 'LIMITED' && (refTime === null || proofSize === null)) {
    fail(`${weightContext}: limited weight requires refTime and proofSize`);
  }

  if (type === 'UNLIMITED' && (refTime !== null || proofSize !== null)) {
    fail(`${weightContext}: unlimited weight must not include refTime or proofSize`);
  }
}

function validateDestinationFee(value, context) {
  const feeContext = `${context}: destinationFee`;
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    fail(`${feeContext} is required`);
    return;
  }

  const mode = requiredEnum(value.mode, 'mode', ['INCLUDED', 'ESTIMATED', 'FIXED'], feeContext);
  requiredString(value.assetSymbol, 'assetSymbol', feeContext);
  const amount = nonNegativeIntegerString(value.amount, 'amount', feeContext);

  if (mode === 'FIXED' && amount === null) {
    fail(`${feeContext}: fixed destination fee requires amount`);
  }

  if (mode !== null && mode !== 'FIXED' && amount !== null) {
    fail(`${feeContext}: amount is only valid for fixed destination fees`);
  }
}

function validateExecutionSpec(execution, destination, xcmVersion, context) {
  if (!execution || typeof execution !== 'object' || Array.isArray(execution)) {
    fail(`${context}: execution must be an object`);
    return;
  }

  requiredString(xcmVersion, 'xcmVersion', context);
  const palletName = requiredString(execution.palletName, 'palletName', context);
  const callName = requiredString(execution.callName, 'callName', context);
  const transferType = requiredEnum(
    execution.transferType,
    'transferType',
    ['RESERVE_TRANSFER_ASSETS', 'LIMITED_RESERVE_TRANSFER_ASSETS', 'TELEPORT_ASSETS', 'LIMITED_TELEPORT_ASSETS', 'X_TOKENS_TRANSFER_MULTIASSET'],
    context
  );
  const argumentShape = optionalEnum(
    execution.argumentShape,
    'argumentShape',
    ['POLKADOT_XCM_TRANSFER_ASSETS', 'X_TOKENS_TRANSFER_MULTIASSET'],
    'POLKADOT_XCM_TRANSFER_ASSETS',
    context
  );
  validateCallShape(palletName, callName, transferType, argumentShape, context);
  requiredMultiLocation(execution.destinationLocation, 'destinationLocation', context);
  requiredMultiLocation(execution.assetLocation, 'assetLocation', context);
  requiredMultiLocation(execution.beneficiaryLocation, 'beneficiaryLocation', context);
  requiredMultiLocation(execution.feeAssetLocation, 'feeAssetLocation', context);
  nonNegativeInteger(execution.feeAssetItem, 'feeAssetItem', context);
  validateWeightLimit(execution.weightLimit, context);
  validateDestinationFee(execution.destinationFee, context);

  if (execution.bridge !== undefined && execution.bridge !== null) {
    const bridgeContext = `${context}: bridge`;
    if (typeof execution.bridge !== 'object' || Array.isArray(execution.bridge)) {
      fail(`${bridgeContext} must be an object`);
    } else {
      const bridgeParachainId = requiredString(execution.bridge.parachainId, 'parachainId', bridgeContext);
      requiredMultiLocation(execution.bridge.feeAssetLocation, 'feeAssetLocation', bridgeContext);
      nonNegativeInteger(execution.bridge.feeAssetItem, 'feeAssetItem', bridgeContext);

      const routeBridgeParachainId = typeof destination.bridgeParachainId === 'string'
        ? destination.bridgeParachainId.trim()
        : '';
      if (routeBridgeParachainId && bridgeParachainId !== null && bridgeParachainId !== routeBridgeParachainId) {
        fail(`${context}: bridge.parachainId must match destination.bridgeParachainId`);
      }
    }
  }
}

function validateRouteAsset(asset, context) {
  if (!asset || typeof asset !== 'object' || Array.isArray(asset)) {
    fail(`${context}: route asset must be an object`);
    return;
  }

  requiredString(asset.symbol, 'symbol', context);
  nonNegativeIntegerString(asset.minAmount, 'minAmount', context);
}

let registry;
try {
  registry = JSON.parse(fs.readFileSync(registryFile, 'utf8'));
} catch (error) {
  console.error(`[xcm-registry][error] Failed to parse ${registryFile}: ${error.message}`);
  process.exit(1);
}

const chains = Array.isArray(registry)
  ? registry
  : Array.isArray(registry.chains)
    ? registry.chains
    : null;

if (!chains) {
  console.error('[xcm-registry][error] Registry root must be an array or contain a chains array');
  process.exit(1);
}

let xcmChainCount = 0;
let destinationCount = 0;
let routeAssetCount = 0;
let executableDestinationCount = 0;
let executableRouteAssetCount = 0;
const chainNamesById = new Map();

for (const chain of Array.isArray(chains) ? chains : []) {
  if (!chain || typeof chain !== 'object' || Array.isArray(chain)) {
    continue;
  }

  const chainId = chain.chainId || chain.id;
  if (nonEmptyString(chainId)) {
    chainNamesById.set(chainId.trim(), nonEmptyString(chain.name) ? chain.name.trim() : null);
  }
}

for (const chain of chains) {
  if (!chain || typeof chain !== 'object' || Array.isArray(chain) || !chain.xcm) {
    continue;
  }

  xcmChainCount += 1;
  const originId = chain.chainId || chain.id || chain.name || '<unknown origin>';
  const xcm = chain.xcm;
  const destinations = Array.isArray(xcm.availableDestinations) ? xcm.availableDestinations : [];

  if (xcm.availableDestinations !== undefined && !Array.isArray(xcm.availableDestinations)) {
    fail(`${originId}: xcm.availableDestinations must be an array`);
  }

  if (xcm.availableAssets !== undefined && !Array.isArray(xcm.availableAssets)) {
    fail(`${originId}: xcm.availableAssets must be an array`);
  }

  for (const asset of Array.isArray(xcm.availableAssets) ? xcm.availableAssets : []) {
    validateRouteAsset(asset, `${originId}: availableAssets`);
  }

  for (const destination of destinations) {
    destinationCount += 1;
    if (!destination || typeof destination !== 'object' || Array.isArray(destination)) {
      fail(`${originId}: destination must be an object`);
      continue;
    }

    const destinationId = requiredString(destination.chainId, 'chainId', `${originId}: destination`) || '<unknown destination>';
    const context = label(originId, '->', destinationId);
    const assets = Array.isArray(destination.assets) ? destination.assets : [];

    if (destination.assets !== undefined && !Array.isArray(destination.assets)) {
      fail(`${context}: destination.assets must be an array`);
    }

    for (const asset of assets) {
      routeAssetCount += 1;
      validateRouteAsset(asset, context);
    }

    if (destination.execution) {
      executableDestinationCount += 1;
      executableRouteAssetCount += assets.length;
      const errorCountBeforeExecutionValidation = errors.length;
      validateExecutionSpec(destination.execution, destination, xcm.xcmVersion, `${context}: execution`);
      if (errors.length === errorCountBeforeExecutionValidation) {
        for (const asset of assets) {
          if (nonEmptyString(asset?.symbol)) {
            executableRouteKeys.add(`${originId}|${destinationId}|${normalizeAssetSymbol(asset.symbol)}`);
          }
        }
      }
    } else if (assets.length > 0) {
      missingExecutableDestinations.push({
        originChainId: String(originId),
        originName: nonEmptyString(chain.name) ? chain.name.trim() : null,
        destinationChainId: String(destinationId),
        destinationName: chainNamesById.get(destinationId) || null,
        assetSymbols: assets
          .map((asset) => nonEmptyString(asset?.symbol) ? normalizeAssetSymbol(asset.symbol) : null)
          .filter(Boolean),
        bridgeParachainId: nonEmptyString(destination.bridgeParachainId) ? destination.bridgeParachainId.trim() : null,
        reason: 'missingExecutionSpec'
      });

      if (requireAllRoutesExecutable) {
        fail(`${context}: executable route metadata is required`);
      }
    }
  }
}

if (requireExecutable && executableDestinationCount === 0) {
  fail('No executable XCM route metadata found');
}

for (const route of requiredRoutes) {
  const key = `${route.originId}|${route.destinationId}|${route.assetSymbol}`;
  if (!executableRouteKeys.has(key)) {
    fail(`Required executable XCM route missing: ${route.originId} -> ${route.destinationId} ${route.assetSymbol}`);
  }
}

if (requireGapManifest) {
  const actualGaps = new Map();
  for (const destination of missingExecutableDestinations) {
    const gap = {
      originId: destination.originChainId,
      destinationId: destination.destinationChainId,
      assetSymbols: normalizeAssetSymbols(destination.assetSymbols),
      bridgeParachainId: destination.bridgeParachainId,
      reason: classifyMissingDestination(destination)
    };
    const key = gapKey(gap);
    if (actualGaps.has(key)) {
      fail(`Duplicate generated discovery-only XCM gap: ${gap.originId} -> ${gap.destinationId} ${gap.assetSymbols}`);
    }
    actualGaps.set(key, gap);
  }

  const expectedGaps = new Map();
  for (const gap of requiredGaps) {
    const key = gapKey(gap);
    if (expectedGaps.has(key)) {
      fail(`Duplicate required discovery-only XCM gap: ${gap.originId} -> ${gap.destinationId} ${gap.assetSymbols}`);
      continue;
    }
    expectedGaps.set(key, gap);

    const actual = actualGaps.get(key);
    if (!actual) {
      fail(`Required discovery-only XCM gap is stale or executable: ${gap.originId} -> ${gap.destinationId} ${gap.assetSymbols}`);
      continue;
    }

    if (gap.reason !== actual.reason) {
      fail(`Required discovery-only XCM gap reason mismatch: ${gap.originId} -> ${gap.destinationId} ${gap.assetSymbols} expected ${gap.reason} actual ${actual.reason}`);
    }

    if (gap.bridgeParachainId !== actual.bridgeParachainId) {
      fail(`Required discovery-only XCM gap bridge mismatch: ${gap.originId} -> ${gap.destinationId} ${gap.assetSymbols}`);
    }
  }

  for (const [key, actual] of actualGaps.entries()) {
    if (!expectedGaps.has(key)) {
      fail(`Untracked discovery-only XCM gap: ${actual.originId} -> ${actual.destinationId} ${actual.assetSymbols}`);
    }
  }
}

if (errors.length > 0) {
  console.error('[xcm-registry][error] XCM registry metadata audit failed:');
  for (const error of errors) {
    console.error(`  - ${error}`);
  }
  process.exit(1);
}

if (gapReportFile) {
  const report = {
    schemaVersion: 1,
    registryFile: path.relative(process.cwd(), registryFile),
    summary: {
      chains: chains.length,
      xcmChains: xcmChainCount,
      destinations: destinationCount,
      routeAssets: routeAssetCount,
      executableDestinations: executableDestinationCount,
      executableRouteAssets: executableRouteAssetCount,
      remainingDiscoveryOnlyDestinations: missingExecutableDestinations.length
    },
    missingExecutableDestinations
  };

  try {
    fs.mkdirSync(path.dirname(gapReportFile), { recursive: true });
    fs.writeFileSync(gapReportFile, `${JSON.stringify(report, null, 2)}\n`);
  } catch (error) {
    console.error(`[xcm-registry][error] Failed to write XCM gap report ${gapReportFile}: ${error.message}`);
    process.exit(1);
  }
}

console.log(
  `[xcm-registry] XCM metadata audit passed: chains=${chains.length}, xcmChains=${xcmChainCount}, ` +
  `destinations=${destinationCount}, routeAssets=${routeAssetCount}, ` +
  `executableDestinations=${executableDestinationCount}, executableRouteAssets=${executableRouteAssetCount}, ` +
  `remainingDiscoveryOnlyDestinations=${missingExecutableDestinations.length}`
);
NODE
