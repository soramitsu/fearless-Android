#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${XCM_EFFECTIVE_REGISTRY_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
BUNDLED_REGISTRY="$ROOT_DIR/runtime/src/main/assets/local_chains.json"
APPROVED_ROUTES="$ROOT_DIR/runtime/src/main/assets/approved_xcm_routes.tsv"
REQUIRED_ROUTES="$ROOT_DIR/scripts/xcm-required-routes.tsv"
DISCOVERY_KIND="none"
DISCOVERY_SOURCE=""
REQUIRE_ALL_APPROVED=false
REPORT_FILE=""

usage() {
  cat <<'USAGE'
Usage: scripts/audit-xcm-effective-registry.sh [options]

Fail-closed audit for the Android XCM effective route registry. Without a
discovery input, the audit requires exact set equality between the approved
route manifest, scripts/xcm-required-routes.tsv, and the executable routes in
the bundled local_chains.json. It also verifies the runtime loader, fetcher,
DI, and release BuildConfig fail-closed wiring.

The report's effective count is the compatible APK-approved candidate set, not
an enabled production route count. summary.productionExecutable remains zero
while the audited release flag and approved-registry provider are disabled.

Options:
  --bundled-registry <path>  Bundled local_chains.json override.
  --approved-routes <path>   Approved three-column route manifest override.
  --required-routes <path>   Required three-column route manifest override.
  --discovery-registry <path>
                             Compare a local discovery registry with approved
                             routes. Remote execution fields are ignored.
  --discovery-url <url>      Fetch the bounded production discovery registry.
                             Only the canonical raw.githubusercontent.com
                             soramitsu/shared-features-utils chains URL is
                             accepted; credentials, query, fragment, redirects,
                             non-default ports, oversized bodies, and slow
                             responses are rejected.
  --require-all-approved     Fail unless every approved route is compatible
                             with the explicit discovery registry.
  --write-report <path>      Write deterministic schema-v1 JSON. A structurally
                             valid discovery audit writes the report before a
                             --require-all-approved failure.
  -h, --help                 Show this help.
USAGE
}

while (($#)); do
  case "$1" in
    --bundled-registry)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --bundled-registry requires a path" >&2; exit 2; }
      BUNDLED_REGISTRY="$2"
      shift 2
      ;;
    --approved-routes)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --approved-routes requires a path" >&2; exit 2; }
      APPROVED_ROUTES="$2"
      shift 2
      ;;
    --required-routes)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --required-routes requires a path" >&2; exit 2; }
      REQUIRED_ROUTES="$2"
      shift 2
      ;;
    --discovery-registry)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --discovery-registry requires a path" >&2; exit 2; }
      [[ "$DISCOVERY_KIND" == "none" ]] || { echo "[xcm-effective-registry][error] discovery inputs are mutually exclusive" >&2; exit 2; }
      DISCOVERY_KIND="file"
      DISCOVERY_SOURCE="$2"
      shift 2
      ;;
    --discovery-url)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --discovery-url requires a URL" >&2; exit 2; }
      [[ "$DISCOVERY_KIND" == "none" ]] || { echo "[xcm-effective-registry][error] discovery inputs are mutually exclusive" >&2; exit 2; }
      DISCOVERY_KIND="url"
      DISCOVERY_SOURCE="$2"
      shift 2
      ;;
    --require-all-approved)
      REQUIRE_ALL_APPROVED=true
      shift
      ;;
    --write-report)
      [[ $# -ge 2 ]] || { echo "[xcm-effective-registry][error] --write-report requires a path" >&2; exit 2; }
      REPORT_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[xcm-effective-registry][error] Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ "$REQUIRE_ALL_APPROVED" == "true" && "$DISCOVERY_KIND" == "none" ]]; then
  echo "[xcm-effective-registry][error] --require-all-approved requires an explicit discovery input" >&2
  exit 2
fi

if ! command -v node >/dev/null 2>&1; then
  echo "[xcm-effective-registry][error] node is required for structured validation" >&2
  exit 1
fi

node - "$ROOT_DIR" "$BUNDLED_REGISTRY" "$APPROVED_ROUTES" "$REQUIRED_ROUTES" \
  "$DISCOVERY_KIND" "$DISCOVERY_SOURCE" "$REQUIRE_ALL_APPROVED" "$REPORT_FILE" <<'NODE'
'use strict';

const fs = require('fs');
const https = require('https');
const path = require('path');
const crypto = require('crypto');
const { isDeepStrictEqual } = require('util');

const [
  rootDirRaw,
  bundledRegistryFile,
  approvedRoutesFile,
  requiredRoutesFile,
  discoveryKind,
  discoverySource,
  requireAllApprovedRaw,
  reportFile
] = process.argv.slice(2);

const rootDirInput = path.resolve(rootDirRaw);
const rootDir = fs.realpathSync(rootDirInput);
const requireAllApproved = requireAllApprovedRaw === 'true';
const MAX_REGISTRY_BYTES = 8 * 1024 * 1024;
const MAX_TEXT_BYTES = 1024 * 1024;
const HTTPS_TIMEOUT_MS = 15_000;
const CHAIN_ID_PATTERN = /^[0-9a-f]{64}$/u;
const SYMBOL_PATTERN = /^[A-Z0-9][A-Z0-9._-]{0,31}$/u;
const PRODUCTION_DISCOVERY_HOST = 'raw.githubusercontent.com';
const PRODUCTION_DISCOVERY_URL =
  'https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json';
const PRODUCTION_DISCOVERY_PATH = '/soramitsu/shared-features-utils/master/chains/v13/chains.json';

class AuditFailure extends Error {
  constructor(messages) {
    super(Array.isArray(messages) ? messages.join('\n') : String(messages));
    this.messages = Array.isArray(messages) ? messages : [String(messages)];
  }
}

function fail(message) {
  throw new AuditFailure(message);
}

function failMany(messages) {
  if (messages.length > 0) {
    throw new AuditFailure(messages);
  }
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function displayPath(file) {
  const absolute = canonicalTarget(file);
  const relative = path.relative(rootDir, absolute);
  return relative && !relative.startsWith(`..${path.sep}`) && relative !== '..'
    ? relative.split(path.sep).join('/')
    : absolute;
}

function canonicalTarget(file) {
  const requested = path.resolve(file);
  const relativeToInputRoot = path.relative(rootDirInput, requested);
  if (relativeToInputRoot === '' || (!relativeToInputRoot.startsWith(`..${path.sep}`) && relativeToInputRoot !== '..' && !path.isAbsolute(relativeToInputRoot))) {
    return path.resolve(rootDir, relativeToInputRoot);
  }
  return requested;
}

function readRegularFile(file, description, maxBytes) {
  let stat;
  try {
    stat = fs.lstatSync(file);
  } catch (_error) {
    fail(`${description} missing: ${displayPath(file)}`);
  }
  if (stat.isSymbolicLink() || !stat.isFile()) {
    fail(`${description} must be a regular non-symlink file: ${displayPath(file)}`);
  }
  if (stat.size > maxBytes) {
    fail(`${description} exceeds ${maxBytes} bytes`);
  }
  try {
    return fs.readFileSync(file, 'utf8');
  } catch (error) {
    fail(`${description} could not be read: ${error.message}`);
  }
}

function parseJsonText(text, description) {
  try {
    return JSON.parse(text);
  } catch (error) {
    fail(`${description} must be valid JSON: ${error.message}`);
  }
}

function normalizeSymbol(value) {
  return String(value || '').trim().replace(/^xc/iu, '').toUpperCase();
}

function normalizeOptionalString(value, field, context, errors) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || value.trim() !== value || value.length === 0 || /[\u0000-\u001f\u007f]/u.test(value)) {
    errors.push(`${context}: ${field} must be null or a non-blank trimmed string`);
    return null;
  }
  return value;
}

function normalizeMinAmount(value, context, errors) {
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || !/^(0|[1-9][0-9]*)$/u.test(value)) {
    errors.push(`${context}: minAmount must be a canonical non-negative integer string when present`);
    return null;
  }
  return value;
}

function routeKey(route) {
  return `${route.originChainId}|${route.destinationChainId}|${route.assetSymbol}`;
}

function compareRoute(a, b) {
  return routeKey(a).localeCompare(routeKey(b));
}

function routeLabel(route) {
  return `${route.originChainId} -> ${route.destinationChainId} ${route.assetSymbol}`;
}

function routeIdentity(route) {
  return {
    originChainId: route.originChainId,
    destinationChainId: route.destinationChainId,
    assetSymbol: route.assetSymbol
  };
}

function parseRouteManifest(file, description) {
  const text = readRegularFile(file, description, MAX_TEXT_BYTES);
  const errors = [];
  const routes = [];
  const seen = new Set();

  text.split(/\r?\n/u).forEach((line, index) => {
    const lineNumber = index + 1;
    const trimmed = line.replace(/\s+#.*$/u, '').trim();
    if (!trimmed || trimmed.startsWith('#')) return;
    const parts = trimmed.split(/\s+/u);
    if (parts.length !== 3) {
      errors.push(`${description} line ${lineNumber} must contain exactly origin destination asset columns`);
      return;
    }
    const [originChainId, destinationChainId, rawAssetSymbol] = parts;
    const assetSymbol = normalizeSymbol(rawAssetSymbol);
    if (!CHAIN_ID_PATTERN.test(originChainId)) {
      errors.push(`${description} line ${lineNumber} has malformed origin chain identity`);
    }
    if (!CHAIN_ID_PATTERN.test(destinationChainId)) {
      errors.push(`${description} line ${lineNumber} has malformed destination chain identity`);
    }
    if (originChainId === destinationChainId) {
      errors.push(`${description} line ${lineNumber} origin and destination chains must differ`);
    }
    if (rawAssetSymbol !== assetSymbol || !SYMBOL_PATTERN.test(assetSymbol)) {
      errors.push(`${description} line ${lineNumber} asset symbol must be canonical uppercase`);
    }
    const route = { originChainId, destinationChainId, assetSymbol };
    const key = routeKey(route);
    if (seen.has(key)) {
      errors.push(`${description} contains duplicate route: ${routeLabel(route)}`);
    } else {
      seen.add(key);
      routes.push(route);
    }
  });

  if (routes.length === 0) {
    errors.push(`${description} must contain at least one route`);
  }
  failMany(errors);
  return { routes: routes.sort(compareRoute), text };
}

function contentIdentity(text) {
  const bytes = Buffer.from(text, 'utf8');
  return {
    byteLength: bytes.length,
    sha256: crypto.createHash('sha256').update(bytes).digest('hex')
  };
}

function stripSourceComments(text) {
  let result = '';
  let index = 0;
  let quote = null;
  let escaped = false;
  while (index < text.length) {
    const char = text[index];
    const next = text[index + 1];
    if (quote !== null) {
      result += char;
      if (escaped) escaped = false;
      else if (char === '\\') escaped = true;
      else if (char === quote) quote = null;
      index += 1;
      continue;
    }
    if (char === "'" || char === '"') {
      quote = char;
      result += char;
      index += 1;
      continue;
    }
    if (char === '/' && next === '/') {
      while (index < text.length && text[index] !== '\n') index += 1;
      result += '\n';
      index += 1;
      continue;
    }
    if (char === '/' && next === '*') {
      index += 2;
      while (index < text.length && !(text[index] === '*' && text[index + 1] === '/')) {
        if (text[index] === '\n') result += '\n';
        index += 1;
      }
      index += 2;
      continue;
    }
    result += char;
    index += 1;
  }
  return result;
}

function parseRegistry(text, description, bundled) {
  const parsed = parseJsonText(text, description);
  const chains = Array.isArray(parsed)
    ? parsed
    : isObject(parsed) && Array.isArray(parsed.chains)
      ? parsed.chains
      : null;
  if (!chains) {
    fail(`${description} root must be an array or contain a chains array`);
  }

  const errors = [];
  const chainById = new Map();
  const routeByKey = new Map();
  const executableByKey = new Map();

  chains.forEach((chain, chainIndex) => {
    const context = `${description} chain[${chainIndex}]`;
    if (!isObject(chain)) {
      errors.push(`${context} must be an object`);
      return;
    }
    const chainId = chain.chainId ?? chain.id;
    if (typeof chainId !== 'string' || chainId.trim() !== chainId || chainId.length === 0 || /[\u0000-\u001f\u007f]/u.test(chainId)) {
      errors.push(`${context} identity must be a non-blank trimmed string`);
      return;
    }
    if (chainById.has(chainId)) {
      errors.push(`${description} contains duplicate chain identity`);
      return;
    }
    chainById.set(chainId, chain);
  });

  chains.forEach((chain, chainIndex) => {
    if (!isObject(chain)) return;
    const originChainId = chain.chainId ?? chain.id;
    if (typeof originChainId !== 'string' || !isObject(chain.xcm)) return;
    const context = `${description} XCM chain[${chainIndex}]`;
    if (!CHAIN_ID_PATTERN.test(originChainId)) {
      errors.push(`${context} has malformed origin chain identity`);
    }
    const xcmVersion = normalizeOptionalString(chain.xcm.xcmVersion, 'xcmVersion', context, errors);
    if (xcmVersion === null) {
      errors.push(`${context}: xcmVersion is required`);
    }
    const originXcmChainId = normalizeOptionalString(chain.xcm.chainId, 'chainId', `${context} xcm`, errors);
    if (originXcmChainId !== null && !CHAIN_ID_PATTERN.test(originXcmChainId)) {
      errors.push(`${context}: xcm.chainId must be a canonical chain identity`);
    }
    const destinations = chain.xcm.availableDestinations;
    if (destinations === undefined || destinations === null) return;
    if (!Array.isArray(destinations)) {
      errors.push(`${context}: availableDestinations must be an array`);
      return;
    }

    const destinationIds = new Set();
    destinations.forEach((destination, destinationIndex) => {
      const destinationContext = `${context} destination[${destinationIndex}]`;
      if (!isObject(destination)) {
        errors.push(`${destinationContext} must be an object`);
        return;
      }
      const destinationChainId = destination.chainId;
      if (typeof destinationChainId !== 'string' || !CHAIN_ID_PATTERN.test(destinationChainId)) {
        errors.push(`${destinationContext} has malformed destination chain identity`);
        return;
      }
      if (destinationIds.has(destinationChainId)) {
        errors.push(`${context} contains duplicate destination identity`);
      } else {
        destinationIds.add(destinationChainId);
      }
      const bridgeParachainId = normalizeOptionalString(
        destination.bridgeParachainId,
        'bridgeParachainId',
        destinationContext,
        errors
      );
      if (bridgeParachainId !== null && !CHAIN_ID_PATTERN.test(bridgeParachainId)) {
        errors.push(`${destinationContext}: bridgeParachainId must be a canonical chain identity`);
      }
      if (!Array.isArray(destination.assets)) {
        errors.push(`${destinationContext}: assets must be an array`);
        return;
      }
      if (bundled && Object.prototype.hasOwnProperty.call(destination, 'execution')) {
        errors.push(`${destinationContext}: legacy destination-scoped execution is forbidden`);
      }
      const normalizedAssets = [];
      const assetSymbols = new Set();
      destination.assets.forEach((asset, assetIndex) => {
        const assetContext = `${destinationContext} asset[${assetIndex}]`;
        if (!isObject(asset)) {
          errors.push(`${assetContext} must be an object`);
          return;
        }
        if (typeof asset.id !== 'string' || asset.id.trim() !== asset.id || asset.id.length === 0 || /[\u0000-\u001f\u007f]/u.test(asset.id)) {
          errors.push(`${assetContext}: id must be a non-blank trimmed public identifier`);
        }
        if (typeof asset.symbol !== 'string' || asset.symbol.trim().length === 0 || /[\u0000-\u001f\u007f]/u.test(asset.symbol)) {
          errors.push(`${assetContext}: symbol must be a non-blank public value`);
          return;
        }
        const assetSymbol = normalizeSymbol(asset.symbol);
        if (!SYMBOL_PATTERN.test(assetSymbol)) {
          errors.push(`${assetContext}: symbol has malformed route identity`);
        }
        if (assetSymbols.has(assetSymbol)) {
          errors.push(`${destinationContext} contains duplicate normalized asset identity ${assetSymbol}`);
        } else {
          assetSymbols.add(assetSymbol);
        }
        normalizedAssets.push({
          id: asset.id,
          symbol: assetSymbol,
          minAmount: normalizeMinAmount(asset.minAmount, assetContext, errors),
          raw: asset
        });
      });

      for (const asset of normalizedAssets) {
        const route = {
          originChainId,
          destinationChainId,
          assetSymbol: asset.symbol,
          originChain: chain,
          destinationChain: chainById.get(destinationChainId) || null,
          destination,
          asset,
          xcmVersion,
          originXcmChainId,
          bridgeParachainId
        };
        const key = routeKey(route);
        if (routeByKey.has(key)) {
          errors.push(`${description} contains duplicate route identity: ${routeLabel(route)}`);
        } else {
          routeByKey.set(key, route);
        }
      }

      for (const asset of normalizedAssets) {
        const execution = asset.raw.execution;
        if (execution === undefined || execution === null || !bundled) {
          // Discovery execution is mutable and deliberately neither validated nor copied.
          continue;
        }
        const assetContext = `${destinationContext} asset ${asset.symbol}`;
        if (!isObject(execution) || Object.keys(execution).length === 0) {
          errors.push(`${assetContext}: bundled per-asset execution must be a non-empty object`);
          continue;
        }
        if (!/^v[1-9][0-9]*$/u.test(xcmVersion || '')) {
          errors.push(`${assetContext}: executable route xcmVersion must be canonical v1 or greater`);
        }
        if (execution.feeAssetItem !== 0) {
          errors.push(`${assetContext}: single-asset executable route feeAssetItem must be 0`);
        }
        if (!isObject(execution.feeAssetLocation) ||
            !isObject(execution.assetLocation) ||
            !isDeepStrictEqual(execution.feeAssetLocation, execution.assetLocation)) {
          errors.push(`${assetContext}: single-asset executable route feeAssetLocation must equal assetLocation`);
        }
        if (execution.bridge !== undefined && execution.bridge !== null) {
          errors.push(`${assetContext}: bridge execution is not supported by the approved runtime loader`);
        }
        if (bridgeParachainId !== null) {
          errors.push(`${assetContext}: executable bridge routes are not supported by the approved runtime loader`);
        }
        const feeMode = normalizeSymbol(execution.destinationFee?.mode);
        if (feeMode === 'ESTIMATED') {
          errors.push(`${assetContext}: estimated destination fee is not supported by the approved runtime loader`);
        }
        const feeAssetSymbol = normalizeSymbol(execution.destinationFee?.assetSymbol);
        if (feeAssetSymbol !== asset.symbol) {
          errors.push(`${assetContext}: destination fee asset must match exact route asset`);
        }
        const key = routeKey({ originChainId, destinationChainId, assetSymbol: asset.symbol });
        if (executableByKey.has(key)) {
          errors.push(`${description} contains duplicate executable route identity`);
        } else {
          executableByKey.set(key, routeByKey.get(key));
        }
      }
    });
  });

  failMany(errors);
  return { chains, chainById, routeByKey, executableByKey };
}

function compareSets(expectedRoutes, actualKeys, expectedName, actualName) {
  const expectedKeys = new Set(expectedRoutes.map(routeKey));
  const errors = [];
  for (const route of expectedRoutes) {
    if (!actualKeys.has(routeKey(route))) {
      errors.push(`${actualName} missing ${expectedName} route: ${routeLabel(route)}`);
    }
  }
  for (const key of [...actualKeys].sort()) {
    if (!expectedKeys.has(key)) {
      const [originChainId, destinationChainId, assetSymbol] = key.split('|');
      errors.push(`${actualName} contains route outside ${expectedName}: ${routeLabel({ originChainId, destinationChainId, assetSymbol })}`);
    }
  }
  failMany(errors);
}

function optionalIdentity(chain, field, context, errors) {
  if (!chain) return null;
  const value = chain[field];
  if (value === undefined || value === null) return null;
  if (typeof value !== 'string' || value.trim() !== value || value.length === 0) {
    errors.push(`${context}: ${field} must be null or a canonical string`);
    return null;
  }
  if (field === 'parentId' && !CHAIN_ID_PATTERN.test(value)) {
    errors.push(`${context}: parentId must be a canonical chain identity`);
  }
  if (field === 'paraId' && !/^(0|[1-9][0-9]*)$/u.test(value)) {
    errors.push(`${context}: paraId must be a canonical non-negative integer string`);
  }
  return value;
}

function fingerprint(route, context, requireOriginAsset = false) {
  const errors = [];
  const originAssets = Array.isArray(route.originChain?.assets)
    ? route.originChain.assets.filter((asset) => isObject(asset) && normalizeSymbol(asset.symbol) === route.asset.symbol)
    : [];
  if (requireOriginAsset && originAssets.length !== 1) {
    errors.push(`${context}: approved origin must contain exactly one core asset for ${route.asset.symbol}`);
  }
  const originAsset = originAssets.length === 1 ? originAssets[0] : null;
  const originAssetId = originAsset && typeof originAsset.id === 'string' && originAsset.id.trim() === originAsset.id && originAsset.id.length > 0
    ? originAsset.id
    : null;
  const originAssetPrecision = originAsset && Number.isInteger(originAsset.precision) && originAsset.precision >= 0
    ? originAsset.precision
    : null;
  if (requireOriginAsset && originAsset !== null && originAssetId === null) {
    errors.push(`${context}: approved origin core asset id must be a non-blank trimmed identifier`);
  }
  if (requireOriginAsset && originAsset !== null && originAssetPrecision === null) {
    errors.push(`${context}: approved origin core asset precision must be a non-negative integer`);
  }
  const value = {
    originXcmVersion: route.xcmVersion,
    originXcmChainId: route.originXcmChainId,
    originParentId: optionalIdentity(route.originChain, 'parentId', `${context} origin`, errors),
    originParaId: optionalIdentity(route.originChain, 'paraId', `${context} origin`, errors),
    destinationParentId: optionalIdentity(route.destinationChain, 'parentId', `${context} destination`, errors),
    destinationParaId: optionalIdentity(route.destinationChain, 'paraId', `${context} destination`, errors),
    bridgeParachainId: route.bridgeParachainId,
    assetId: route.asset.id,
    assetSymbol: route.asset.symbol,
    minAmount: route.asset.minAmount,
    originAssetCount: originAssets.length,
    originAssetId,
    originAssetPrecision
  };
  failMany(errors);
  return value;
}

function auditWiring() {
  const errors = [];
  const registrySourceFile = path.join(
    rootDir,
    'public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/ApprovedXcmRouteRegistry.kt'
  );
  const executionSpecSourceFile = path.join(
    rootDir,
    'public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmExecutionSpec.kt'
  );
  const fetcherSourceFile = path.join(
    rootDir,
    'public-shared-features-xcm/src/main/java/jp/co/soramitsu/xcm/domain/XcmEntitiesFetcher.kt'
  );
  const featureModuleFile = path.join(
    rootDir,
    'feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt'
  );
  const featureGradleFile = path.join(rootDir, 'feature-wallet-impl/build.gradle');
  const runtimeGradleFile = path.join(rootDir, 'runtime/build.gradle');
  const discoveryProviderFile = path.join(
    rootDir,
    'runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/XcmDiscoverySnapshotProvider.kt'
  );
  const chainSyncSourceFile = path.join(
    rootDir,
    'runtime/src/main/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncService.kt'
  );
  const chainSyncTestFile = path.join(
    rootDir,
    'runtime/src/test/java/jp/co/soramitsu/runtime/multiNetwork/chain/ChainSyncServiceTest.kt'
  );

  const registrySource = stripSourceComments(readRegularFile(registrySourceFile, 'approved XCM registry source', MAX_TEXT_BYTES));
  const executionSpecSource = stripSourceComments(readRegularFile(executionSpecSourceFile, 'approved XCM execution validator source', MAX_TEXT_BYTES));
  const fetcherSource = stripSourceComments(readRegularFile(fetcherSourceFile, 'XCM entities fetcher source', MAX_TEXT_BYTES));
  const featureModule = stripSourceComments(readRegularFile(featureModuleFile, 'wallet DI source', MAX_TEXT_BYTES));
  const featureGradle = stripSourceComments(readRegularFile(featureGradleFile, 'wallet feature Gradle source', MAX_TEXT_BYTES));
  const runtimeGradle = stripSourceComments(readRegularFile(runtimeGradleFile, 'runtime Gradle source', MAX_TEXT_BYTES));
  const discoveryProvider = stripSourceComments(readRegularFile(discoveryProviderFile, 'XCM discovery snapshot provider source', MAX_TEXT_BYTES));
  const chainSyncSource = stripSourceComments(readRegularFile(chainSyncSourceFile, 'chain sync source', MAX_TEXT_BYTES));
  const chainSyncTest = stripSourceComments(readRegularFile(chainSyncTestFile, 'chain sync test source', MAX_TEXT_BYTES));

  const requireMarker = (text, pattern, message) => {
    if (!pattern.test(text)) errors.push(message);
  };

  requireMarker(registrySource, /\bclass\s+ApprovedXcmRouteRegistry\b/u, 'approved registry source missing ApprovedXcmRouteRegistry');
  requireMarker(registrySource, /\b(?:object|class)\s+ApprovedXcmRouteRegistryLoader\b/u, 'approved registry source missing ApprovedXcmRouteRegistryLoader');
  requireMarker(registrySource, /\bfun\s+load\s*\([^)]*bundledChainsJson[^)]*approvedRoutesTsv[^)]*\)/su, 'approved registry loader must bind bundledChainsJson and approvedRoutesTsv');
  requireMarker(registrySource, /XcmExecutionSpecValidator\s*\.\s*requireValid\s*\([^)]*\basset\s*=\s*asset\b/su, 'approved registry loader must pass the exact route asset to its execution validator');
  requireMarker(executionSpecSource, /\b(?:object|class)\s+XcmExecutionSpecValidator\b/u, 'approved execution source missing XcmExecutionSpecValidator');
  requireMarker(executionSpecSource, /\basset\s*\.\s*execution\b/u, 'approved registry loader must select execution from the exact route asset');
  requireMarker(registrySource, /\bdestination\s*\.\s*execution\s*==\s*null\b/u, 'approved registry loader must explicitly reject legacy destination execution');

  requireMarker(fetcherSource, /\bApprovedXcmRouteRegistry\b/u, 'XcmEntitiesFetcher must depend on ApprovedXcmRouteRegistry');
  requireMarker(fetcherSource, /private\s+val\s+approved\w*\s*:\s*ApprovedXcmRouteRegistry\b/u, 'XcmEntitiesFetcher missing approved registry field');
  requireMarker(fetcherSource, /\bexecutionSpec\b/u, 'XcmEntitiesFetcher must obtain executionSpec from the approved registry');
  requireMarker(fetcherSource, /\bXcmDiscoverySnapshotProvider\b/u, 'XcmEntitiesFetcher must use the current-process XCM discovery snapshot provider');
  requireMarker(fetcherSource, /getCurrentProcessXcmDiscoveryChains\s*\(\s*\)/u, 'XcmEntitiesFetcher must read only the current-process XCM discovery snapshot');
  if (/\b(?:destination|asset)\s*\.\s*execution\b/u.test(fetcherSource)) {
    errors.push('XcmEntitiesFetcher must ignore mutable remote destination and asset execution');
  }

  requireMarker(featureModule, /ApprovedXcmRouteRegistryLoader\s*\.\s*load\s*\(/u, 'wallet DI must load the approved XCM route registry');
  requireMarker(featureModule, /local_chains\.json/u, 'wallet DI missing bundled local_chains.json input');
  requireMarker(featureModule, /approved_xcm_routes\.tsv/u, 'wallet DI missing approved_xcm_routes.tsv input');
  requireMarker(featureModule, /XcmEntitiesFetcher\s*\([^)]*approved/isu, 'wallet DI must inject the approved registry into XcmEntitiesFetcher');
  requireMarker(featureModule, /if\s*\(\s*BuildConfig\.ENABLE_PRODUCTION_XCM_TRANSFERS\s*\)/u, 'wallet DI missing production XCM flag guard');
  requireMarker(featureModule, /SubstrateXcmTransferEngine\s*\(/u, 'wallet DI missing enabled XCM engine branch');
  requireMarker(featureModule, /else\s*\{?\s*UnavailableXcmTransferEngine\b/su, 'wallet DI must fail closed to UnavailableXcmTransferEngine');

  function extractFunctionBlock(text, name) {
    const nameIndex = text.indexOf(name);
    if (nameIndex < 0) return null;
    const open = text.indexOf('{', nameIndex);
    if (open < 0) return null;
    let depth = 0;
    for (let index = open; index < text.length; index += 1) {
      if (text[index] === '{') depth += 1;
      if (text[index] === '}') {
        depth -= 1;
        if (depth === 0) return text.slice(open + 1, index);
      }
    }
    return null;
  }

  const approvedProvider = extractFunctionBlock(featureModule, 'provideApprovedXcmRouteRegistry');
  if (approvedProvider === null ||
      !/if\s*\(\s*!\s*BuildConfig\.ENABLE_PRODUCTION_XCM_TRANSFERS\s*\)\s*\{[^}]*return\s+ApprovedXcmRouteRegistry\.unavailable\s*\(\s*\)/su.test(approvedProvider)) {
    errors.push('approved registry DI provider must return unavailable before asset loading when production XCM is disabled');
  } else if (approvedProvider.indexOf('ApprovedXcmRouteRegistry.unavailable') > approvedProvider.indexOf('context.assets.open')) {
    errors.push('approved registry DI fail-closed guard must run before opening APK assets');
  }

  requireMarker(discoveryProvider, /\binterface\s+XcmDiscoverySnapshotProvider\b/u, 'runtime missing XcmDiscoverySnapshotProvider interface');
  requireMarker(discoveryProvider, /getCurrentProcessXcmDiscoveryChains\s*\(\s*\)/u, 'runtime discovery provider missing current-process snapshot API');
  requireMarker(chainSyncSource, /class\s+ChainSyncService[\s\S]*:\s*XcmDiscoverySnapshotProvider\b/u, 'ChainSyncService must implement XcmDiscoverySnapshotProvider');
  requireMarker(chainSyncSource, /xcmDiscovery\w*Mutex\s*=\s*Mutex\s*\(\s*\)/u, 'ChainSyncService XCM discovery refresh must be mutex-protected');
  requireMarker(chainSyncSource, /@Volatile\s+private\s+var\s+currentProcessXcmDiscoveryChains/u, 'ChainSyncService published XCM discovery snapshot must be volatile');
  const syncUpBlock = extractFunctionBlock(chainSyncSource, 'syncUp');
  const clearBeforeSyncIndex = syncUpBlock?.indexOf('currentProcessXcmDiscoveryChains = null') ?? -1;
  const performSyncIndex = syncUpBlock?.indexOf('configChainsSyncUp()') ?? -1;
  const publishAfterSyncIndex = syncUpBlock?.indexOf('currentProcessXcmDiscoveryChains = remoteChains.toList()') ?? -1;
  if (syncUpBlock === null ||
      clearBeforeSyncIndex < 0 ||
      performSyncIndex <= clearBeforeSyncIndex ||
      publishAfterSyncIndex <= performSyncIndex) {
    errors.push('ChainSyncService must clear before sync and publish only after full successful sync');
  }
  if (syncUpBlock !== null && /(?:runCatching|catch\s*\()/u.test(syncUpBlock)) {
    errors.push('ChainSyncService must propagate XCM discovery sync failure after clearing the snapshot');
  }
  if (!/override\s+suspend\s+fun\s+getCurrentProcessXcmDiscoveryChains\s*\(\s*\)[\s\S]{0,200}currentProcessXcmDiscoveryChains\?\.toList\s*\(\s*\)\.orEmpty\s*\(\s*\)/u.test(chainSyncSource)) {
    errors.push('ChainSyncService current-process discovery getter must return an isolated empty snapshot until successful sync');
  }
  const xcmFetcherProviderMatch = /fun\s+provideXcmEntitiesFetcher\s*\(\s*chainSyncService\s*:\s*ChainSyncService\s*,\s*approvedRoutes\s*:\s*ApprovedXcmRouteRegistry\s*\)[\s\S]{0,300}XcmEntitiesFetcher\s*\(\s*chainSyncService\s*,\s*approvedRoutes\s*\)/u.test(featureModule);
  const xcmFetcherRoomFallback = /fun\s+provideXcmEntitiesFetcher\s*\([^)]*chainRegistry\s*:\s*ChainRegistry/su.test(featureModule);
  if (!xcmFetcherProviderMatch || xcmFetcherRoomFallback) {
    errors.push('production XcmEntitiesFetcher DI must use ChainSyncService snapshot provider and forbid ChainRegistry/Room fallback');
  }
  requireMarker(chainSyncTest, /persisted chains are never exposed before a current process sync/u, 'chain sync tests missing persisted-cache exclusion case');
  requireMarker(chainSyncTest, /failed refresh propagates and clears current process XCM discovery snapshot/u, 'chain sync tests missing failed-refresh clearing case');
  requireMarker(chainSyncTest, /failed database application clears fetched XCM discovery snapshot/u, 'chain sync tests missing failed-application clearing case');
  requireMarker(chainSyncTest, /in flight refresh exposes empty XCM snapshot without blocking readers/u, 'chain sync tests missing nonblocking in-flight refresh case');

  function extractBlock(text, name) {
    const match = new RegExp(`\\b${name}\\s*\\{`, 'u').exec(text);
    if (!match) return null;
    const open = text.indexOf('{', match.index);
    let depth = 0;
    let quote = null;
    let escaped = false;
    for (let index = open; index < text.length; index += 1) {
      const char = text[index];
      if (quote !== null) {
        if (escaped) escaped = false;
        else if (char === '\\') escaped = true;
        else if (char === quote) quote = null;
        continue;
      }
      if (char === "'" || char === '"') {
        quote = char;
      } else if (char === '{') {
        depth += 1;
      } else if (char === '}') {
        depth -= 1;
        if (depth === 0) return text.slice(open + 1, index);
      }
    }
    return null;
  }

  const debugBlock = extractBlock(featureGradle, 'debug');
  const releaseBlock = extractBlock(featureGradle, 'release');
  const productionFieldPattern = /buildConfigField\s+["']boolean["']\s*,\s*["']ENABLE_PRODUCTION_XCM_TRANSFERS["']\s*,\s*([^\r\n]+)/gu;
  const releaseFields = releaseBlock ? [...releaseBlock.matchAll(productionFieldPattern)] : [];
  const debugFields = debugBlock ? [...debugBlock.matchAll(productionFieldPattern)] : [];
  const allFields = [...featureGradle.matchAll(productionFieldPattern)];
  if (releaseFields.length !== 1 || !/^\s*["']false["']\s*(?:\/\/.*)?$/u.test(releaseFields[0]?.[1] || '')) {
    errors.push('release ENABLE_PRODUCTION_XCM_TRANSFERS must be exactly one hardcoded false boolean BuildConfig field');
  }
  if (debugFields.length !== 1 || !/enableDebugXcmTransfers/u.test(debugFields[0]?.[1] || '')) {
    errors.push('debug ENABLE_PRODUCTION_XCM_TRANSFERS must be defined once from the strict debug-only input');
  }
  if (allFields.length !== 2) {
    errors.push('ENABLE_PRODUCTION_XCM_TRANSFERS must only be defined for explicit debug and release build types');
  }
  if (/ENABLE_PRODUCTION_XCM_TRANSFERS[^\r\n]*(?:getenv|environmentVariable|findProperty|gradleProperty|readOptionalSecret)/iu.test(featureGradle) ||
      /(?:getenv|environmentVariable|findProperty|gradleProperty|readOptionalSecret)[^\r\n]*ENABLE_PRODUCTION_XCM_TRANSFERS/iu.test(featureGradle)) {
    errors.push('release ENABLE_PRODUCTION_XCM_TRANSFERS must not accept an environment/property override');
  }
  requireMarker(featureGradle, /ENABLE_DEBUG_XCM_TRANSFERS/u, 'debug XCM enablement must use the distinct ENABLE_DEBUG_XCM_TRANSFERS input');
  requireMarker(featureGradle, /GradleException/u, 'debug XCM flag must reject malformed boolean values');
  requireMarker(featureGradle, /(?:==|in|contains)[^\r\n]*(?:["']true["'])/u, 'debug XCM flag validation missing literal true');
  requireMarker(featureGradle, /(?:==|in|contains)[^\r\n]*(?:["']false["'])/u, 'debug XCM flag validation missing literal false');

  const runtimeReleaseBlock = extractBlock(runtimeGradle, 'release');
  const chainsUrlFieldPattern = /buildConfigField\s+["']String["']\s*,\s*["']CHAINS_URL["']/u;
  const releaseChainsUrlLines = runtimeReleaseBlock
    ? runtimeReleaseBlock.split(/\r?\n/u)
      .map((line) => line.trim())
      .filter((line) => chainsUrlFieldPattern.test(line))
    : [];
  const allChainsUrlFields = [
    ...runtimeGradle.matchAll(/buildConfigField\s+["']String["']\s*,\s*["']CHAINS_URL["']/gu)
  ];
  const expectedReleaseField =
    `buildConfigField "String", "CHAINS_URL", "\\"${PRODUCTION_DISCOVERY_URL}\\""`;
  if (releaseChainsUrlLines.length !== 1 || releaseChainsUrlLines[0] !== expectedReleaseField) {
    errors.push('runtime release CHAINS_URL must be exactly one hardcoded canonical production discovery URL');
  }
  if (allChainsUrlFields.length !== 2) {
    errors.push('runtime CHAINS_URL must only be defined for explicit debug and release build types');
  }
  if (/CHAINS_URL_RELEASE_OVERRIDE/u.test(runtimeGradle)) {
    errors.push('runtime release CHAINS_URL must not accept CHAINS_URL_RELEASE_OVERRIDE');
  }
  if (/\bCHAINS_URL_OVERRIDE\b/u.test(runtimeGradle)) {
    errors.push('runtime generic CHAINS_URL_OVERRIDE is forbidden; use CHAINS_URL_DEBUG_OVERRIDE for debug only');
  }

  failMany(errors);
  return { releaseDiscoveryUrl: PRODUCTION_DISCOVERY_URL };
}

function validateDiscoveryUrl(raw) {
  let url;
  try {
    url = new URL(raw);
  } catch (_error) {
    fail('discovery URL is malformed');
  }
  if (url.protocol !== 'https:') fail('discovery URL must use HTTPS');
  if (url.username || url.password) fail('discovery URL must not contain credentials');
  if (url.search) fail('discovery URL must not contain a query');
  if (url.hash) fail('discovery URL must not contain a fragment');
  if (url.port) fail('discovery URL must use the default HTTPS port');
  if (url.hostname !== PRODUCTION_DISCOVERY_HOST || url.pathname !== PRODUCTION_DISCOVERY_PATH) {
    fail('discovery URL is outside the bounded production registry location');
  }
  return url;
}

function fetchDiscoveryRegistry(rawUrl) {
  const url = validateDiscoveryUrl(rawUrl);
  return new Promise((resolve, reject) => {
    const request = https.get(url, {
      headers: {
        Accept: 'application/json',
        'User-Agent': 'fearless-xcm-effective-registry-audit/1'
      },
      timeout: HTTPS_TIMEOUT_MS
    }, (response) => {
      const status = response.statusCode || 0;
      if (status >= 300 && status < 400) {
        response.resume();
        reject(new AuditFailure('production discovery registry redirects are not allowed'));
        return;
      }
      if (status !== 200) {
        response.resume();
        reject(new AuditFailure(`production discovery registry returned HTTP ${status}`));
        return;
      }
      const contentType = String(response.headers['content-type'] || '').toLowerCase();
      const contentTypeAllowed = /^application\/(?:[a-z0-9.+-]+\+)?json(?:\s*;|$)/u.test(contentType) ||
        /^text\/plain(?:\s*;|$)/u.test(contentType);
      if (!contentTypeAllowed) {
        response.resume();
        reject(new AuditFailure('production discovery registry returned an unsupported Content-Type'));
        return;
      }
      const contentLength = Number(response.headers['content-length'] || 0);
      if (Number.isFinite(contentLength) && contentLength > MAX_REGISTRY_BYTES) {
        response.resume();
        reject(new AuditFailure('production discovery registry exceeds the maximum response size'));
        return;
      }
      const chunks = [];
      let bytes = 0;
      response.on('data', (chunk) => {
        bytes += chunk.length;
        if (bytes > MAX_REGISTRY_BYTES) {
          request.destroy(new AuditFailure('production discovery registry exceeds the maximum response size'));
          return;
        }
        chunks.push(chunk);
      });
      response.on('end', () => resolve(Buffer.concat(chunks).toString('utf8')));
    });
    request.on('timeout', () => request.destroy(new AuditFailure('production discovery registry request timed out')));
    request.on('error', (error) => {
      reject(error instanceof AuditFailure ? error : new AuditFailure(`production discovery registry request failed: ${error.code || 'network error'}`));
    });
  });
}

function writeReport(file, report) {
  const absolute = canonicalTarget(file);
  let temporary = null;
  try {
    const parent = path.dirname(absolute);
    let cursor = parent;
    while (!fs.existsSync(cursor)) {
      const next = path.dirname(cursor);
      if (next === cursor) break;
      cursor = next;
    }
    if (fs.existsSync(cursor)) {
      const existing = fs.lstatSync(cursor);
      if (existing.isSymbolicLink() || !existing.isDirectory()) {
        fail('effective registry report ancestor must be a real directory, not a symlink');
      }
    }
    fs.mkdirSync(parent, { recursive: true });
    let prefix = path.parse(parent).root;
    for (const component of parent.slice(prefix.length).split(path.sep).filter(Boolean)) {
      prefix = path.join(prefix, component);
      const stat = fs.lstatSync(prefix);
      if (stat.isSymbolicLink() || !stat.isDirectory()) {
        fail('effective registry report ancestor must be a real directory, not a symlink');
      }
    }
    if (fs.existsSync(absolute)) {
      const destination = fs.lstatSync(absolute);
      if (destination.isSymbolicLink() || !destination.isFile()) {
        fail('effective registry report destination must be a regular non-symlink file');
      }
    }
    temporary = `${absolute}.tmp-${process.pid}`;
    fs.writeFileSync(temporary, `${JSON.stringify(report, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
    fs.renameSync(temporary, absolute);
    temporary = null;
  } catch (error) {
    if (error instanceof AuditFailure) throw error;
    fail(`effective registry report could not be written: ${error.message}`);
  } finally {
    if (temporary !== null) {
      try {
        fs.rmSync(temporary, { force: true });
      } catch (_error) {
        // Preserve the primary write failure while making a best-effort cleanup.
      }
    }
  }
}

async function main() {
  const wiring = auditWiring();
  const approvedManifest = parseRouteManifest(approvedRoutesFile, 'approved route manifest');
  const requiredManifest = parseRouteManifest(requiredRoutesFile, 'required route manifest');
  const approvedRoutes = approvedManifest.routes;
  const requiredRoutes = requiredManifest.routes;
  compareSets(approvedRoutes, new Set(requiredRoutes.map(routeKey)), 'approved manifest', 'required manifest');

  const bundledText = readRegularFile(bundledRegistryFile, 'bundled XCM registry', MAX_REGISTRY_BYTES);
  const bundled = parseRegistry(bundledText, 'bundled XCM registry', true);
  compareSets(approvedRoutes, new Set(bundled.executableByKey.keys()), 'approved manifest', 'bundled executable registry');

  const bundledApproved = new Map();
  for (const approved of approvedRoutes) {
    const key = routeKey(approved);
    const route = bundled.executableByKey.get(key);
    if (!route) continue;
    if (!route.destinationChain) {
      fail(`approved bundled route destination chain is missing: ${routeLabel(approved)}`);
    }
    bundledApproved.set(key, {
      route,
      fingerprint: fingerprint(route, `bundled ${routeLabel(approved)}`, true)
    });
  }

  let discovery = null;
  let discoveryInput = null;
  let discoveryText = null;
  if (discoveryKind === 'file') {
    discoveryText = readRegularFile(discoverySource, 'discovery XCM registry', MAX_REGISTRY_BYTES);
    discovery = parseRegistry(discoveryText, 'discovery XCM registry', false);
    discoveryInput = { kind: 'file', source: displayPath(discoverySource) };
  } else if (discoveryKind === 'url') {
    const url = validateDiscoveryUrl(discoverySource);
    if (url.href !== wiring.releaseDiscoveryUrl) {
      fail('discovery URL must exactly match the release app CHAINS_URL default');
    }
    discoveryText = await fetchDiscoveryRegistry(url.href);
    discovery = parseRegistry(discoveryText, 'production discovery XCM registry', false);
    discoveryInput = { kind: 'https', source: url.href };
  }

  const approvedKeys = new Set(approvedRoutes.map(routeKey));
  const routeResults = [];
  const extras = [];
  let discoveredCount = bundled.executableByKey.size;

  if (discovery) {
    discoveredCount = discovery.routeByKey.size;
    for (const [key, discoveredRoute] of [...discovery.routeByKey.entries()].sort(([a], [b]) => a.localeCompare(b))) {
      if (!approvedKeys.has(key)) extras.push(routeIdentity(discoveredRoute));
    }
  }

  for (const approved of approvedRoutes) {
    const key = routeKey(approved);
    const reasons = [];
    if (discovery) {
      let remote = discovery.routeByKey.get(key);
      if (!remote) {
        const samePair = [...discovery.routeByKey.values()]
          .filter((candidate) =>
            candidate.originChainId === approved.originChainId &&
            candidate.destinationChainId === approved.destinationChainId
          )
          .sort((a, b) => a.asset.symbol.localeCompare(b.asset.symbol));
        if (samePair.length > 0) remote = samePair[0];
      }
      if (!remote) {
        reasons.push('not-discovered');
      } else {
        const baseline = bundledApproved.get(key);
        const expected = baseline.fingerprint;
        if (!remote.destinationChain) {
          reasons.push('destination-chain-not-discovered');
        }
        const actual = fingerprint(remote, `discovery ${routeLabel(approved)}`);
        if (actual.originAssetCount !== 1) reasons.push('origin-core-asset-not-single');
        const comparisons = [
          ['originXcmVersion', 'origin-xcm-version-mismatch'],
          ['originXcmChainId', 'origin-xcm-chain-mismatch'],
          ['originParentId', 'origin-parent-chain-mismatch'],
          ['originParaId', 'origin-parachain-mismatch'],
          ['destinationParentId', 'destination-parent-chain-mismatch'],
          ['destinationParaId', 'destination-parachain-mismatch'],
          ['bridgeParachainId', 'bridge-parachain-mismatch'],
          ['assetId', 'asset-id-mismatch'],
          ['assetSymbol', 'asset-symbol-mismatch'],
          ['minAmount', 'min-amount-mismatch'],
          ['originAssetId', 'origin-asset-id-mismatch'],
          ['originAssetPrecision', 'origin-asset-precision-mismatch']
        ];
        for (const [field, reason] of comparisons) {
          if (actual[field] !== expected[field]) reasons.push(reason);
        }
      }
    }
    const effective = reasons.length === 0;
    routeResults.push({
      ...routeIdentity(approved),
      effective,
      productionExecutable: false,
      reasons
    });
  }

  routeResults.sort(compareRoute);
  extras.sort(compareRoute);
  const missing = routeResults
    .filter((route) => !route.effective)
    .map((route) => ({ ...routeIdentity(route), reasons: route.reasons }));
  const effectiveCount = routeResults.length - missing.length;
  const productionExecutableCount = 0;
  const report = {
    schemaVersion: 1,
    mode: discovery ? 'discovery' : 'bundled',
    status: missing.length === 0 ? 'complete' : 'incomplete',
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
      releaseDiscoveryUrl: wiring.releaseDiscoveryUrl
    },
    inputs: {
      approvedRoutes: {
        source: displayPath(approvedRoutesFile),
        ...contentIdentity(approvedManifest.text)
      },
      requiredRoutes: {
        source: displayPath(requiredRoutesFile),
        ...contentIdentity(requiredManifest.text)
      },
      bundledRegistry: {
        source: displayPath(bundledRegistryFile),
        ...contentIdentity(bundledText)
      },
      discoveryRegistry: discoveryInput === null
        ? null
        : { ...discoveryInput, ...contentIdentity(discoveryText) }
    },
    summary: {
      approved: approvedRoutes.length,
      required: requiredRoutes.length,
      bundledExecutable: bundled.executableByKey.size,
      discovered: discoveredCount,
      effective: effectiveCount,
      productionExecutable: productionExecutableCount,
      missing: missing.length,
      extra: extras.length
    },
    routes: routeResults,
    missing,
    extra: extras
  };

  if (reportFile) writeReport(reportFile, report);

  if (requireAllApproved && missing.length > 0) {
    throw new AuditFailure(
      missing.map((route) => `approved route is not effective: ${routeLabel(route)} (${route.reasons.join(', ')})`)
    );
  }

  console.log(
    `[xcm-effective-registry] audit passed: mode=${report.mode}, approved=${report.summary.approved}, ` +
      `compatibleCandidates=${report.summary.effective}, productionExecutable=${report.summary.productionExecutable}, ` +
      `missing=${report.summary.missing}, extra=${report.summary.extra}`
  );
}

main().catch((error) => {
  const messages = error instanceof AuditFailure ? error.messages : [`unexpected audit failure: ${error.message}`];
  console.error('[xcm-effective-registry][error] effective registry audit failed:');
  for (const message of messages) console.error(`  - ${message}`);
  process.exitCode = 1;
});
NODE
