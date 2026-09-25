#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
GENERATOR="$SCRIPT_DIR/generate-xcm-production-evidence-template.sh"

fail() {
  echo "[xcm-evidence-template-test][error] $*" >&2
  exit 1
}

write_routes() {
  local file="$1"
  cat >"$file" <<'ROUTES'
# origin destination asset
origin destination DOT
destination origin xcKSM
ROUTES
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
stdout_template="$tmp_dir/template-stdout.json"
output_template="$tmp_dir/template-output.json"
write_routes "$routes"

bash "$GENERATOR" --required-route-file "$routes" >"$stdout_template"
bash "$GENERATOR" --required-route-file "$routes" --output "$output_template"
cmp "$stdout_template" "$output_template" >/dev/null ||
  fail "output file matches stdout"

node - "$stdout_template" "$routes" <<'NODE'
const assert = require('node:assert/strict');
const fs = require('node:fs');

const [file, routesFile] = process.argv.slice(2);
const template = JSON.parse(fs.readFileSync(file, 'utf8'));

assert.equal(template.schemaVersion, 1);
assert.equal(template.scope, 'android-xcm-production-evidence-template');
assert.equal(template.sourceRequiredRouteFile, routesFile);
assert.equal(template.requiredRouteCount, 2);
assert.ok(template.instructions.includes(
  'Set lastReviewed in scripts/xcm-production-evidence.json to a valid UTC YYYY-MM-DD date on or after the UTC calendar date of every timestamp and verifiedAt value; same-day values through 23:59:59Z are valid.'
));
assert.ok(template.instructions.some((instruction) => instruction.includes(
  'audit-xcm-effective-registry.sh --discovery-url https://raw.githubusercontent.com/soramitsu/shared-features-utils/master/chains/v13/chains.json --require-all-approved --write-report build/reports/xcm-effective-registry-report.json && bash ./scripts/audit-xcm-production-evidence.sh --effective-registry-report build/reports/xcm-effective-registry-report.json --require-ready'
)));
assert.deepEqual(template.requiredEvidenceFields, [
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
]);
assert.equal(template.evidence.length, 2);
assert.deepEqual(template.evidence[0], {
  originChainId: 'origin',
  destinationChainId: 'destination',
  assetSymbol: 'DOT',
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
});
assert.equal(template.evidence[1].assetSymbol, 'KSM');
assert.equal(template.evidence[1].androidCommit, 'TODO_android_release_commit');

function assertNoSecretLikeKeys(value, path = '$') {
  if (!value || typeof value !== 'object') {
    return;
  }
  if (Array.isArray(value)) {
    value.forEach((item, index) => assertNoSecretLikeKeys(item, `${path}[${index}]`));
    return;
  }
  for (const [key, nested] of Object.entries(value)) {
    assert.doesNotMatch(key, /(private[-_]?key|mnemonic|seed|secret|password|authorization|credential|clientDataJSON)/iu, `${path}.${key}`);
    assertNoSecretLikeKeys(nested, `${path}.${key}`);
  }
}

assertNoSecretLikeKeys(template);
NODE

expect_failure "missing required route file" "required route file missing" \
  bash "$GENERATOR" --required-route-file "$tmp_dir/missing.tsv"

malformed="$tmp_dir/malformed.tsv"
printf 'origin destination DOT extra\n' >"$malformed"
expect_failure "malformed required route file" "Invalid required route file line" \
  bash "$GENERATOR" --required-route-file "$malformed"

empty="$tmp_dir/empty.tsv"
printf '# no routes\n' >"$empty"
expect_failure "empty required route file" "required route file must contain at least one route" \
  bash "$GENERATOR" --required-route-file "$empty"

duplicate="$tmp_dir/duplicate.tsv"
cat >"$duplicate" <<'ROUTES'
origin destination DOT
origin destination xcDOT
ROUTES
expect_failure "duplicate required route" "duplicate required route" \
  bash "$GENERATOR" --required-route-file "$duplicate"

expect_failure "unknown argument" "Unknown argument" \
  bash "$GENERATOR" --bogus

echo "[xcm-evidence-template-test] all assertions passed"
