#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_DIR="$ROOT_DIR/build/reports/public-dependency-upstream-delta"

usage() {
  cat <<'USAGE'
Usage: scripts/export-public-dependency-upstream-delta.sh [--root <repo>] [--output <dir>]

Exports a deterministic handoff bundle for Android public dependency deltas:
the pinned fearless-utils overlay, compatibility-module source hashes, and
release-review docs needed to upstream or retire the carried public shims.
USAGE
}

while (($# > 0)); do
  case "$1" in
    --root)
      [[ $# -ge 2 ]] || { echo "[public-dependency-handoff][error] --root requires a path" >&2; exit 2; }
      ROOT_DIR="$2"
      shift 2
      ;;
    --output)
      [[ $# -ge 2 ]] || { echo "[public-dependency-handoff][error] --output requires a path" >&2; exit 2; }
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[public-dependency-handoff][error] Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if ! command -v node >/dev/null 2>&1; then
  echo "[public-dependency-handoff][error] node is required for deterministic manifest generation" >&2
  exit 1
fi

ROOT_DIR="$(cd "$ROOT_DIR" && pwd)"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

ROOT_DIR="$ROOT_DIR" OUTPUT_DIR="$OUTPUT_DIR" node <<'NODE'
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const childProcess = require('child_process');

const rootDir = process.env.ROOT_DIR;
const outputDir = process.env.OUTPUT_DIR;

const compatibilityModules = [
  'public-android-foundation',
  'public-ui-core',
  'public-xnetworking',
  'public-shared-features-core',
  'public-shared-features-xcm',
  'public-shared-features-backup',
];

const copiedFiles = [
  {
    source: 'scripts/fearless-utils-library-only.patch',
    output: 'fearless-utils/fearless-utils-library-only.patch',
    label: 'fearless-utils library-only overlay patch',
  },
  {
    source: 'scripts/ensure-fearless-utils.sh',
    output: 'fearless-utils/ensure-fearless-utils.sh',
    label: 'fearless-utils source guard',
  },
  {
    source: 'scripts/test-fearless-utils-derived-tree.sh',
    output: 'fearless-utils/test-fearless-utils-derived-tree.sh',
    label: 'fearless-utils derived-tree adversarial self-test',
  },
  {
    source: 'docs/public-dependency-audit.md',
    output: 'docs/public-dependency-audit.md',
    label: 'public dependency audit docs',
  },
  {
    source: 'docs/release-checklist.md',
    output: 'docs/release-checklist.md',
    label: 'Android release checklist',
  },
  {
    source: 'docs/releases/PROCESS.md',
    output: 'docs/release-process.md',
    label: 'Android release process',
  },
  {
    source: 'docs/binary-provenance.md',
    output: 'docs/binary-provenance.md',
    label: 'binary provenance governance',
  },
  {
    source: 'README.md',
    output: 'docs/root-README.md',
    label: 'public build instructions',
  },
  {
    source: 'scripts/audit-public-artifacts.sh',
    output: 'governance/audit-public-artifacts.sh',
    label: 'strict public artifact audit',
  },
  {
    source: 'scripts/test-public-artifact-provenance-audit.sh',
    output: 'governance/test-public-artifact-provenance-audit.sh',
    label: 'public artifact provenance self-test',
  },
  {
    source: 'scripts/xcm-required-routes.tsv',
    output: 'governance/xcm-required-routes.tsv',
    label: 'required XCM route manifest',
  },
  {
    source: 'scripts/xcm-discovery-only-routes.tsv',
    output: 'governance/xcm-discovery-only-routes.tsv',
    label: 'discovery-only XCM route manifest',
  },
  {
    source: 'runtime/src/main/assets/approved_xcm_routes.tsv',
    output: 'governance/approved_xcm_routes.tsv',
    label: 'APK-approved XCM route manifest',
  },
  {
    source: 'build.gradle',
    output: 'gradle/build.gradle',
    label: 'Gradle public dependency substitutions',
  },
  {
    source: 'settings.gradle',
    output: 'gradle/settings.gradle',
    label: 'Gradle dependency substitution settings',
  },
  {
    source: '.github/workflows/android-ci.yml',
    output: 'ci/android-ci.yml',
    label: 'Android CI workflow',
  },
  {
    source: '.github/workflows/android-release.yml',
    output: 'ci/android-release.yml',
    label: 'Android release workflow',
  },
];

function fail(message) {
  console.error(`[public-dependency-handoff][error] ${message}`);
  process.exit(1);
}

function repoPath(relativePath) {
  return path.join(rootDir, relativePath);
}

function outputPath(relativePath) {
  return path.join(outputDir, relativePath);
}

function readFile(relativePath, label = relativePath) {
  const fullPath = repoPath(relativePath);
  if (!fs.existsSync(fullPath) || !fs.statSync(fullPath).isFile()) {
    fail(`${label} missing: ${relativePath}`);
  }
  return fs.readFileSync(fullPath);
}

function sha256Buffer(buffer) {
  return crypto.createHash('sha256').update(buffer).digest('hex');
}

function sha256File(relativePath) {
  return sha256Buffer(readFile(relativePath));
}

function copyFile(source, destination) {
  const sourcePath = repoPath(source);
  const destinationPath = outputPath(destination);
  fs.mkdirSync(path.dirname(destinationPath), { recursive: true });
  fs.copyFileSync(sourcePath, destinationPath);
}

function listFiles(dir) {
  const files = [];
  function walk(current) {
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      if (entry.name === 'build' || entry.name === '.gradle') {
        continue;
      }
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.isFile()) {
        files.push(path.relative(rootDir, fullPath).split(path.sep).join('/'));
      }
    }
  }
  walk(dir);
  return files.sort();
}

function directoryDigest(files) {
  const hash = crypto.createHash('sha256');
  for (const relativePath of files) {
    hash.update(relativePath);
    hash.update('\0');
    hash.update(sha256File(relativePath));
    hash.update('\n');
  }
  return hash.digest('hex');
}

function gitRevision() {
  try {
    return childProcess.execFileSync('git', ['-C', rootDir, 'rev-parse', 'HEAD'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'ignore'],
    }).trim();
  } catch (_) {
    return null;
  }
}

function assertContains(relativePath, pattern, description) {
  const content = readFile(relativePath).toString('utf8');
  if (!pattern.test(content)) {
    fail(`${description} missing in ${relativePath}`);
  }
  return content;
}

function normalizeGovernanceText(content) {
  return content
    .replace(/\\\s*\n\s*/gu, ' ')
    .replace(/\s+/gu, ' ')
    .trim();
}

function assertCommand(relativePath, command, description) {
  const normalized = normalizeGovernanceText(readFile(relativePath).toString('utf8'));
  if (!normalized.includes(command)) {
    fail(`${description} missing in ${relativePath}: ${command}`);
  }
}

function activeLines(relativePath, commentPrefixes = ['#']) {
  return readFile(relativePath).toString('utf8')
    .split(/\r?\n/u)
    .filter((line) => {
      const trimmed = line.trim();
      return trimmed !== '' && !commentPrefixes.some((prefix) => trimmed.startsWith(prefix));
    });
}

function assertActiveText(relativePath, value, description, commentPrefixes = ['#']) {
  if (!activeLines(relativePath, commentPrefixes).some((line) => line.includes(value))) {
    fail(`${description} missing as active text in ${relativePath}: ${value}`);
  }
}

function manifestStats(relativePath) {
  const rows = readFile(relativePath).toString('utf8')
    .split(/\r?\n/u)
    .map((line) => line.trim())
    .filter((line) => line !== '' && !line.startsWith('#'));
  let assetCount = 0;
  let multiAssetRowCount = 0;
  let multiAssetCount = 0;
  for (const row of rows) {
    const fields = row.split(/\s+/u);
    if (fields.length < 3) fail(`${relativePath} contains malformed row: ${row}`);
    const assets = fields[2].split(',').filter(Boolean);
    if (assets.length === 0) fail(`${relativePath} contains an empty asset list: ${row}`);
    assetCount += assets.length;
    if (assets.length > 1) {
      multiAssetRowCount += 1;
      multiAssetCount += assets.length;
    }
  }
  return { rows, rowCount: rows.length, assetCount, multiAssetRowCount, multiAssetCount };
}

for (const file of copiedFiles) {
  readFile(file.source, file.label);
}

const ensureScript = assertContains(
  'scripts/ensure-fearless-utils.sh',
  /EXPECTED_COMMIT="\$\{FEARLESS_UTILS_COMMIT:-[0-9a-f]{40}\}"/,
  'pinned fearless-utils commit'
);
const expectedCommit = ensureScript.match(/EXPECTED_COMMIT="\$\{FEARLESS_UTILS_COMMIT:-([0-9a-f]{40})\}"/)?.[1];
if (!expectedCommit) {
  fail('Unable to parse pinned fearless-utils commit from scripts/ensure-fearless-utils.sh');
}
assertContains(
  'scripts/ensure-fearless-utils.sh',
  /EXPECTED_REPOSITORY="\$\{FEARLESS_UTILS_REPOSITORY:-soramitsu\/fearless-utils-Android\}"/,
  'pinned fearless-utils repository'
);

assertContains(
  'scripts/ensure-fearless-utils.sh',
  /FEARLESS_UTILS_LIBRARY_ONLY/,
  'library-only overlay mode'
);
assertContains(
  'scripts/test-fearless-utils-derived-tree.sh',
  /all deterministic and adversarial fixtures passed/,
  'fearless-utils derived-tree adversarial fixtures'
);
assertContains(
  'settings.gradle',
  /jp\.co\.soramitsu\.fearless-utils:fearless-utils/,
  'fearless-utils Gradle module substitution'
);
assertContains(
  'docs/public-dependency-audit.md',
  /soramitsu\/fearless-utils-Android/,
  'fearless-utils upstream source docs'
);
assertContains(
  'docs/release-checklist.md',
  /export-public-dependency-upstream-delta\.sh/,
  'release checklist handoff export command'
);

const patch = readFile('scripts/fearless-utils-library-only.patch', 'fearless-utils library-only overlay patch').toString('utf8');
const patchTouchedPaths = [...patch.matchAll(/^diff --git a\/(.+?) b\/(.+)$/gm)]
  .map((match) => match[2])
  .sort();
if (patchTouchedPaths.length === 0) {
  fail('fearless-utils library-only overlay patch must contain at least one diff');
}
if (!patchTouchedPaths.every((patchedPath) => patchedPath.startsWith('fearless-utils/') || patchedPath === 'settings.gradle' || patchedPath === 'build.gradle')) {
  fail('fearless-utils library-only overlay patch touches paths outside fearless-utils/root Gradle files');
}

const modules = compatibilityModules.map((modulePath) => {
  const fullPath = repoPath(modulePath);
  if (!fs.existsSync(fullPath) || !fs.statSync(fullPath).isDirectory()) {
    fail(`public compatibility module missing: ${modulePath}`);
  }
  if (!fs.existsSync(path.join(fullPath, 'build.gradle'))) {
    fail(`public compatibility module build.gradle missing: ${modulePath}`);
  }
  const files = listFiles(fullPath);
  if (files.length === 0) {
    fail(`public compatibility module has no source files: ${modulePath}`);
  }
  return {
    name: modulePath.replace(/^public-/, ''),
    path: modulePath,
    fileCount: files.length,
    sha256: directoryDigest(files),
  };
});

const canonicalCommands = {
  utilsGuard: `FEARLESS_UTILS_PATH=../fearless-utils-Android FEARLESS_UTILS_COMMIT=${expectedCommit} FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android FEARLESS_UTILS_LIBRARY_ONLY=true ./scripts/ensure-fearless-utils.sh`,
  handoffTest: 'bash ./scripts/test-public-dependency-upstream-delta-export.sh',
  handoffExport: 'bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta',
  provenanceTest: 'bash ./scripts/test-public-artifact-provenance-audit.sh',
  provenanceAudit: './scripts/audit-public-artifacts.sh --strict-provenance',
  unsignedReleaseProvenanceAudit: './scripts/audit-public-artifacts.sh --unsigned-release --strict-provenance',
  releaseProvenanceAudit: './scripts/audit-public-artifacts.sh --release --strict-provenance',
};

for (const document of ['docs/public-dependency-audit.md', 'docs/release-checklist.md', 'docs/releases/PROCESS.md']) {
  assertCommand(document, canonicalCommands.utilsGuard, 'canonical fearless-utils guard command');
}
for (const document of ['docs/public-dependency-audit.md', 'docs/release-checklist.md', 'docs/releases/PROCESS.md', 'README.md']) {
  assertCommand(document, canonicalCommands.handoffTest, 'public dependency handoff self-test command');
  assertCommand(document, canonicalCommands.handoffExport, 'public dependency handoff export command');
  assertCommand(document, canonicalCommands.provenanceTest, 'strict provenance self-test command');
  assertCommand(document, canonicalCommands.provenanceAudit, 'strict provenance audit command');
}
for (const document of ['docs/release-checklist.md', 'docs/releases/PROCESS.md', 'docs/binary-provenance.md']) {
  assertCommand(document, canonicalCommands.releaseProvenanceAudit, 'strict release provenance command');
}
for (const document of ['docs/release-checklist.md', 'docs/releases/PROCESS.md']) {
  assertCommand(document, canonicalCommands.unsignedReleaseProvenanceAudit, 'strict unsigned-release provenance command');
}

const buildFile = activeLines('build.gradle', ['//', '#']).join('\n');
const substitutionModules = [...buildFile.matchAll(/substitute module\([^)]*\) using project\(['"]:(public-[^'"]+)['"]\)/gu)]
  .map((match) => match[1]);
const uniqueSubstitutionModules = [...new Set(substitutionModules)].sort();
const expectedModules = [...compatibilityModules].sort();
if (JSON.stringify(uniqueSubstitutionModules) !== JSON.stringify(expectedModules)) {
  fail(`Gradle public dependency substitutions must be exactly ${expectedModules.join(', ')}; found ${uniqueSubstitutionModules.join(', ')}`);
}

const settingsFile = activeLines('settings.gradle', ['//', '#']).join('\n');
const includedPublicModules = [...settingsFile.matchAll(/include\s+['"]:(public-[^'"]+)['"]/gu)]
  .map((match) => match[1]);
const uniqueIncludedPublicModules = [...new Set(includedPublicModules)].sort();
if (JSON.stringify(uniqueIncludedPublicModules) !== JSON.stringify(expectedModules)) {
  fail(`settings.gradle public modules must be exactly ${expectedModules.join(', ')}; found ${uniqueIncludedPublicModules.join(', ')}`);
}

const approvedRoutes = manifestStats('runtime/src/main/assets/approved_xcm_routes.tsv');
const requiredRoutes = manifestStats('scripts/xcm-required-routes.tsv');
const discoveryOnlyRoutes = manifestStats('scripts/xcm-discovery-only-routes.tsv');
if (approvedRoutes.rowCount !== requiredRoutes.rowCount) {
  fail(`approved/required XCM route counts differ: ${approvedRoutes.rowCount}/${requiredRoutes.rowCount}`);
}
if (JSON.stringify([...approvedRoutes.rows].sort()) !== JSON.stringify([...requiredRoutes.rows].sort())) {
  fail('approved and required XCM route manifests must contain the same route rows');
}

const artifactAudit = readFile('scripts/audit-public-artifacts.sh').toString('utf8');
const auditBinaryRows = [...artifactAudit.matchAll(/^\s+([A-Za-z0-9_./-]+)\) echo "([0-9a-f]{64})" ;;/gmu)]
  .map((match) => ({ path: match[1], sha256: match[2] }));
if (auditBinaryRows.length === 0) fail('strict artifact audit has no allowlisted binary checksum rows');
const provenanceDocument = readFile('docs/binary-provenance.md').toString('utf8');
const documentedBinaryRows = [...provenanceDocument.matchAll(/^\| `([^`]+)` \| `([0-9a-f]{64})` \|$/gmu)]
  .map((match) => ({ path: match[1], sha256: match[2] }));
const normalizeBinaryRows = (rows) => rows
  .map((entry) => `${entry.path}\t${entry.sha256}`)
  .sort();
if (JSON.stringify(normalizeBinaryRows(documentedBinaryRows)) !== JSON.stringify(normalizeBinaryRows(auditBinaryRows))) {
  fail('docs/binary-provenance.md rows must exactly match scripts/audit-public-artifacts.sh allowlisted checksums');
}

for (const workflow of ['.github/workflows/android-ci.yml', '.github/workflows/android-release.yml']) {
  assertActiveText(workflow, `FEARLESS_UTILS_COMMIT: ${expectedCommit}`, 'workflow fearless-utils revision');
  assertActiveText(workflow, 'repository: soramitsu/fearless-utils-Android', 'workflow fearless-utils repository');
  assertActiveText(workflow, 'FEARLESS_UTILS_LIBRARY_ONLY: "true"', 'workflow library-only mode');
  assertActiveText(workflow, 'FORCE_LOCAL_UTILS: "true"', 'workflow forced local source dependency');
}
for (const document of ['README.md', 'docs/binary-provenance.md']) {
  assertContains(document, new RegExp(expectedCommit, 'u'), 'documented fearless-utils revision');
  assertContains(document, /soramitsu\/fearless-utils-Android/u, 'documented fearless-utils repository');
}
for (const command of [canonicalCommands.handoffTest, canonicalCommands.handoffExport, canonicalCommands.provenanceTest, canonicalCommands.provenanceAudit]) {
  assertActiveText('.github/workflows/android-ci.yml', command, 'Android CI governance command');
}
assertActiveText('.github/workflows/android-release.yml', canonicalCommands.provenanceTest, 'Android release provenance self-test');
assertActiveText('.github/workflows/android-release.yml', canonicalCommands.provenanceAudit, 'Android tagged-source provenance audit');
assertActiveText('.github/workflows/android-release.yml', canonicalCommands.unsignedReleaseProvenanceAudit, 'Android unsigned release artifact provenance audit');

const publicDependencyDocument = normalizeGovernanceText(readFile('docs/public-dependency-audit.md').toString('utf8'));
const governedSnapshot = [
  `Pinned \`fearless-utils-Android\` revision: \`${expectedCommit}\`.`,
  `Public compatibility-module substitutions: \`${modules.length}\`.`,
  `Approved/required executable XCM route rows: \`${approvedRoutes.rowCount}\` / \`${requiredRoutes.rowCount}\`.`,
  `Discovery-only XCM destinations/assets: \`${discoveryOnlyRoutes.rowCount}\` / \`${discoveryOnlyRoutes.assetCount}\`; \`${discoveryOnlyRoutes.multiAssetRowCount}\` of those destinations carry \`${discoveryOnlyRoutes.multiAssetCount}\` multi-asset route entries.`,
  `Strict-provenance allowlisted binary artifacts: \`${auditBinaryRows.length}\`.`,
];
for (const snapshotLine of governedSnapshot) {
  if (!publicDependencyDocument.includes(snapshotLine)) {
    fail(`public dependency governed snapshot is stale or missing: ${snapshotLine}`);
  }
}
if (publicDependencyDocument.includes('until an open-source XCM extrinsic engine')) {
  fail('public dependency docs still claim the open-source XCM engine is missing');
}
for (const currentXcmMarker of ['Substrate fee and submission engine', 'ENABLE_PRODUCTION_XCM_TRANSFERS=false']) {
  if (!publicDependencyDocument.includes(currentXcmMarker)) {
    fail(`public dependency XCM status is missing current marker: ${currentXcmMarker}`);
  }
}

fs.rmSync(outputDir, { recursive: true, force: true });
fs.mkdirSync(outputDir, { recursive: true });

for (const file of copiedFiles) {
  copyFile(file.source, file.output);
}

const handoffFiles = copiedFiles.map((file) => ({
  path: file.output,
  source: file.source,
  sha256: sha256Buffer(fs.readFileSync(outputPath(file.output))),
}));

const readme = `# Android Public Dependency Upstream Delta

This bundle captures the public dependency compatibility work that still needs
upstream ownership before Android can remove local checkout mutation and
compatibility shims.

## Source Pin

- fearless-utils repository: https://github.com/soramitsu/fearless-utils-Android
- expected revision: ${expectedCommit}
- overlay patch: fearless-utils/fearless-utils-library-only.patch

## Review

1. Review handoff-manifest.json for source hashes and compatibility modules,
   then run fearless-utils/test-fearless-utils-derived-tree.sh.
2. Apply the fearless-utils overlay to the pinned upstream checkout or port the
   same changes directly upstream.
3. Replace local compatibility modules only after public artifacts expose the
   same source-backed API contracts and Android CI passes without local
   substitutions.
`;

fs.writeFileSync(outputPath('README.md'), readme);
handoffFiles.push({
  path: 'README.md',
  source: 'generated',
  sha256: sha256Buffer(fs.readFileSync(outputPath('README.md'))),
});

const manifest = {
  schemaVersion: 1,
  scope: 'android-public-dependency-upstream-delta',
  walletRevision: gitRevision(),
  fearlessUtils: {
    repository: 'https://github.com/soramitsu/fearless-utils-Android',
    expectedRevision: expectedCommit,
    libraryOnlyPatch: 'fearless-utils/fearless-utils-library-only.patch',
    patchSha256: sha256Buffer(Buffer.from(patch)),
    patchTouchedPathCount: patchTouchedPaths.length,
    patchTouchedPaths,
  },
  publicCompatibilityModules: modules,
  governance: {
    compatibilityModuleCount: modules.length,
    approvedXcmRouteCount: approvedRoutes.rowCount,
    requiredXcmRouteCount: requiredRoutes.rowCount,
    discoveryOnlyDestinationCount: discoveryOnlyRoutes.rowCount,
    discoveryOnlyAssetCount: discoveryOnlyRoutes.assetCount,
    discoveryOnlyMultiAssetDestinationCount: discoveryOnlyRoutes.multiAssetRowCount,
    discoveryOnlyMultiAssetCount: discoveryOnlyRoutes.multiAssetCount,
    allowlistedBinaryArtifactCount: auditBinaryRows.length,
  },
  requiredReviewCommands: [
    'bash ./scripts/test-fearless-utils-derived-tree.sh',
    canonicalCommands.utilsGuard,
    canonicalCommands.handoffTest,
    canonicalCommands.handoffExport,
    canonicalCommands.provenanceTest,
    canonicalCommands.provenanceAudit,
    canonicalCommands.unsignedReleaseProvenanceAudit,
    canonicalCommands.releaseProvenanceAudit,
  ],
  files: handoffFiles.sort((left, right) => left.path.localeCompare(right.path)),
};

fs.writeFileSync(outputPath('handoff-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

console.log(`[public-dependency-handoff] wrote ${outputPath('handoff-manifest.json')}`);
console.log(`[public-dependency-handoff] modules=${modules.length} patchPaths=${patchTouchedPaths.length}`);
NODE
