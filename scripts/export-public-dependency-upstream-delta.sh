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
  /FEARLESS_UTILS_LIBRARY_ONLY/,
  'library-only overlay mode'
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

1. Review handoff-manifest.json for source hashes and compatibility modules.
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
  requiredReviewCommands: [
    'FEARLESS_UTILS_PATH=../fearless-utils-Android ./scripts/ensure-fearless-utils.sh',
    'bash ./scripts/test-public-dependency-upstream-delta-export.sh',
    'bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta',
    './scripts/audit-public-artifacts.sh',
  ],
  files: handoffFiles.sort((left, right) => left.path.localeCompare(right.path)),
};

fs.writeFileSync(outputPath('handoff-manifest.json'), `${JSON.stringify(manifest, null, 2)}\n`);

console.log(`[public-dependency-handoff] wrote ${outputPath('handoff-manifest.json')}`);
console.log(`[public-dependency-handoff] modules=${modules.length} patchPaths=${patchTouchedPaths.length}`);
NODE
