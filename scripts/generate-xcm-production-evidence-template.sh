#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${XCM_PRODUCTION_EVIDENCE_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
REQUIRED_ROUTE_FILE="$ROOT_DIR/scripts/xcm-required-routes.tsv"
OUTPUT_FILE=""

usage() {
  cat <<'USAGE'
Usage: scripts/generate-xcm-production-evidence-template.sh [--required-route-file <path>] [--output <path>]

Generates a JSON template for XCM production E2E evidence from the required-route
manifest. The generated evidence records intentionally contain TODO placeholders
and must be completed before copying them into scripts/xcm-production-evidence.json.
USAGE
}

while (($#)); do
  case "$1" in
    --required-route-file)
      [[ $# -ge 2 ]] || { echo "[xcm-evidence-template][error] --required-route-file requires a path" >&2; exit 2; }
      REQUIRED_ROUTE_FILE="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { echo "[xcm-evidence-template][error] --output requires a path" >&2; exit 2; }
      OUTPUT_FILE="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[xcm-evidence-template][error] Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v node >/dev/null 2>&1; then
  echo "[xcm-evidence-template][error] node is required for structured JSON generation" >&2
  exit 1
fi

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

node - "$REQUIRED_ROUTE_FILE" >"$tmp_file" <<'NODE'
const fs = require('fs');

const [requiredRouteFile] = process.argv.slice(2);
const errors = [];

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
  'operator',
  'androidCommit'
];

function fail(message) {
  errors.push(message);
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

function routeKey(route) {
  return `${route.originChainId}|${route.destinationChainId}|${route.assetSymbol}`;
}

if (!fs.existsSync(requiredRouteFile)) {
  fail(`required route file missing: ${requiredRouteFile}`);
} else {
  const routes = [];
  const seenRoutes = new Set();
  const text = fs.readFileSync(requiredRouteFile, 'utf8');

  text.split(/\r?\n/).forEach((line, index) => {
    const trimmed = line.replace(/\s+#.*$/, '').trim();
    if (!trimmed || trimmed.startsWith('#')) {
      return;
    }

    const parts = trimmed.split(/\s+/);
    if (parts.length !== 3 || parts.some((part) => !nonEmptyString(part))) {
      fail(`Invalid required route file line ${index + 1}: expected origin destination asset`);
      return;
    }

    const route = {
      originChainId: parts[0],
      destinationChainId: parts[1],
      assetSymbol: normalizeAssetSymbol(parts[2])
    };
    const key = routeKey(route);
    if (seenRoutes.has(key)) {
      fail(`duplicate required route: ${route.originChainId} -> ${route.destinationChainId} ${route.assetSymbol}`);
      return;
    }
    seenRoutes.add(key);
    routes.push(route);
  });

  if (routes.length === 0) {
    fail('required route file must contain at least one route');
  }

  if (errors.length === 0) {
    const template = {
      schemaVersion: 1,
      scope: 'android-xcm-production-evidence-template',
      sourceRequiredRouteFile: requiredRouteFile,
      requiredRouteCount: routes.length,
      instructions: [
        'Copy the evidence array into scripts/xcm-production-evidence.json only after replacing every TODO value.',
        'Do not include private keys, mnemonics, seeds, passwords, credentials, or authorization headers in public evidence.',
        'Set androidCommit to the Android release commit under validation; for tagged release validation you may set XCM_PRODUCTION_EXPECTED_COMMIT when running the audit.',
        'After every route has evidence and no discovery-only gaps remain, set status to ready, releaseEnabled to true, clear blockers, and run bash ./scripts/audit-xcm-production-evidence.sh --require-ready.'
      ],
      requiredEvidenceFields: REQUIRED_EVIDENCE_FIELDS,
      evidence: routes.map((route) => ({
        originChainId: route.originChainId,
        destinationChainId: route.destinationChainId,
        assetSymbol: route.assetSymbol,
        extrinsicHash: 'TODO_0x_prefixed_32_byte_hash',
        sender: 'TODO_sender_public_address',
        recipient: 'TODO_recipient_public_address',
        amount: 'TODO_positive_decimal_amount',
        timestamp: 'TODO_YYYY-MM-DDTHH:MM:SSZ',
        environment: 'mainnet',
        operator: 'TODO_operator_or_runbook_id',
        androidCommit: 'TODO_android_release_commit'
      }))
    };

    process.stdout.write(`${JSON.stringify(template, null, 2)}\n`);
  }
}

if (errors.length > 0) {
  for (const error of errors) {
    console.error(`[xcm-evidence-template][error] ${error}`);
  }
  process.exit(1);
}
NODE

if [[ -n "$OUTPUT_FILE" ]]; then
  mkdir -p "$(dirname "$OUTPUT_FILE")"
  mv "$tmp_file" "$OUTPUT_FILE"
  trap - EXIT
else
  cat "$tmp_file"
fi
