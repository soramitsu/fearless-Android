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
The target manifest lastReviewed date must cover every evidence timestamp and
verifiedAt UTC calendar date.
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
        'Independently verify finalized origin inclusion/success and destination execution/balance delta against canonical RPCs plus public proof links; the offline audit checks the attestation shape, not chain truth.',
        'Set every success/finality boolean to true only after verification, and use an independentVerifier that differs from operator.',
        'Set lastReviewed in scripts/xcm-production-evidence.json to a valid UTC YYYY-MM-DD date on or after the UTC calendar date of every timestamp and verifiedAt value; same-day values through 23:59:59Z are valid.',
        'Set androidCommit to the Android release commit under validation; for tagged release validation you may set XCM_PRODUCTION_EXPECTED_COMMIT when running the audit.',
        'After every route has evidence and no discovery-only gaps remain, set status to ready, releaseEnabled to true, clear blockers, then regenerate the canonical live report and validate it with the evidence in one command: bash ./scripts/audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready.'
      ],
      requiredEvidenceFields: REQUIRED_EVIDENCE_FIELDS,
      evidence: routes.map((route) => ({
        originChainId: route.originChainId,
        destinationChainId: route.destinationChainId,
        assetSymbol: route.assetSymbol,
        extrinsicHash: 'TODO_0x_prefixed_32_byte_hash',
        originBlockHash: 'TODO_origin_0x_prefixed_32_byte_block_hash',
        originBlockNumber: 'TODO_origin_positive_block_number',
        originFinalized: false,
        originExtrinsicSucceeded: false,
        sender: 'TODO_sender_public_address',
        recipient: 'TODO_recipient_public_address',
        amount: 'TODO_positive_decimal_amount',
        timestamp: 'TODO_YYYY-MM-DDTHH:MM:SSZ',
        destinationBlockHash: 'TODO_destination_0x_prefixed_32_byte_block_hash',
        destinationBlockNumber: 'TODO_destination_positive_block_number',
        destinationEventSucceeded: false,
        destinationBalanceDelta: 'TODO_positive_destination_balance_delta',
        originVerificationUrl: 'TODO_public_https_origin_proof_url',
        destinationVerificationUrl: 'TODO_public_https_destination_proof_url',
        verificationMethod: 'canonical-rpc-and-explorer',
        verifiedAt: 'TODO_YYYY-MM-DDTHH:MM:SSZ',
        independentVerifier: 'TODO_independent_verifier_or_runbook_id',
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
