#!/usr/bin/env bash
set -euo pipefail

# Exits 0 when the release build compiles XCM submissions on and 1 when it is off. Only exactly one
# ENABLE_PRODUCTION_XCM_TRANSFERS field holding the literal "false" in the release block of
# feature-wallet-impl/build.gradle counts as off; a missing, duplicated or other value counts as on, so the
# caller then runs the full production readiness audit.

ROOT_DIR="${XCM_RELEASE_FLAG_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}"
BUILD_FILE="$ROOT_DIR/feature-wallet-impl/build.gradle"

if ! command -v node >/dev/null 2>&1; then
  echo "[xcm-release-flag][error] node is required" >&2
  exit 2
fi
[[ -f "$BUILD_FILE" && ! -L "$BUILD_FILE" ]] || { echo "[xcm-release-flag] $BUILD_FILE is missing: treated as on" >&2; exit 0; }

node - "$BUILD_FILE" <<'NODE'
const fs = require('fs');
const text = fs.readFileSync(process.argv[2], 'utf8').replace(/\/\*[\s\S]*?\*\//gu, '').replace(/^\s*\/\/.*$/gmu, '');

function block(source, name) {
  const match = new RegExp(`\\b${name}\\s*\\{`, 'u').exec(source);
  if (!match) return null;
  let depth = 0;
  let quote = null;
  for (let index = source.indexOf('{', match.index); index < source.length; index += 1) {
    const char = source[index];
    if (quote !== null) {
      if (char === '\\') index += 1;
      else if (char === quote) quote = null;
    } else if (char === '"' || char === "'") {
      quote = char;
    } else if (char === '{') {
      depth += 1;
    } else if (char === '}') {
      depth -= 1;
      if (depth === 0) return source.slice(match.index, index + 1);
    }
  }
  return null;
}

const release = block(text, 'release');
const fields = release === null ? [] : [
  ...release.matchAll(/buildConfigField\s+["']boolean["']\s*,\s*["']ENABLE_PRODUCTION_XCM_TRANSFERS["']\s*,\s*([^\r\n]+)/gu)
];
const off = fields.length === 1 && /^\s*["']false["']\s*$/u.test(fields[0][1]);
console.log(`[xcm-release-flag] release XCM submissions: ${off ? 'off' : 'on'}`);
process.exit(off ? 1 : 0);
NODE
