#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const root = path.resolve(__dirname, '..');
const writerSource = path.join(
  root,
  'scripts',
  'write-google-play-internal-app-sharing-handoff.js',
);
const temporaryRoot = fs.mkdtempSync(
  path.join(os.tmpdir(), 'fearless-google-play-ias-writer.'),
);
const uploadToken = 'A'.repeat(11);
const installToken = 'B'.repeat(90);
const validUrl = [
  'https://play.google.com',
  'apps',
  'test',
  uploadToken,
  installToken,
].join('/');
const validDigest = crypto
  .createHash('sha256')
  .update(validUrl, 'utf8')
  .digest('hex');
const expectedPositiveCases = 2;
const expectedNegativeCases = 17;
let positiveCases = 0;
let negativeCases = 0;

function makeFixture(label, configure = () => {}) {
  const fixture = fs.mkdtempSync(path.join(temporaryRoot, `${label}.`));
  const scripts = path.join(fixture, 'scripts');
  const config = path.join(fixture, 'config');
  fs.mkdirSync(scripts);
  fs.mkdirSync(config);
  fs.copyFileSync(
    writerSource,
    path.join(
      scripts,
      'write-google-play-internal-app-sharing-handoff.js',
    ),
  );
  const manifest = {
    publication: {
      shareUrlSha256: validDigest,
      observedUploadTokenLength: uploadToken.length,
      observedInstallTokenLength: installToken.length,
    },
    googleConsoleObservation: {
      normalizedExpiryLocal: '2026-09-24T10:49',
    },
  };
  configure({ fixture, manifest });
  if (!fs.existsSync(path.join(config, 'google-play-internal-app-sharing-publication.json'))) {
    fs.writeFileSync(
      path.join(config, 'google-play-internal-app-sharing-publication.json'),
      `${JSON.stringify(manifest, null, 2)}\n`,
    );
  }
  return fixture;
}

function invoke(fixture, input) {
  return spawnSync(
    process.execPath,
    [
      path.join(
        fixture,
        'scripts',
        'write-google-play-internal-app-sharing-handoff.js',
      ),
    ],
    {
      cwd: fixture,
      input,
      encoding: 'utf8',
      timeout: 5000,
    },
  );
}

function expectSuccess(label, configure = () => {}) {
  const fixture = makeFixture(label, configure);
  const result = invoke(fixture, `${validUrl}\n`);
  assert.equal(result.status, 0, `${label}: ${result.stderr}`);
  assert.equal(result.stdout.includes(validUrl), false, `${label}: leaked URL`);
  const output = path.join(
    fixture,
    'build',
    'reports',
    'google-play-internal-app-sharing',
    'public-link.json',
  );
  const stat = fs.lstatSync(output);
  assert.equal(stat.isFile(), true, `${label}: output is not a file`);
  assert.equal(stat.isSymbolicLink(), false, `${label}: output is a symlink`);
  assert.equal(stat.mode & 0o777, 0o600, `${label}: output mode drifted`);
  const handoff = JSON.parse(fs.readFileSync(output, 'utf8'));
  assert.deepEqual(handoff, {
    schemaVersion: 1,
    shareUrl: validUrl,
    shareUrlSha256: validDigest,
    expiresDisplayLocal: '2026-09-24T10:49',
    timezone: 'not-displayed-by-google',
  });
  positiveCases += 1;
  process.stdout.write(`[ias-handoff-writer-test] PASS: ${label}\n`);
  return { fixture, output };
}

function expectFailure(
  label,
  input,
  expectedMessage,
  configure = () => {},
) {
  const fixture = makeFixture(label, configure);
  const result = invoke(fixture, input);
  assert.notEqual(result.status, 0, `${label}: unexpectedly passed`);
  assert.match(result.stderr, expectedMessage, `${label}: wrong diagnostic`);
  assert.equal(result.stdout.includes(validUrl), false, `${label}: leaked URL`);
  negativeCases += 1;
  process.stdout.write(`[ias-handoff-writer-test] PASS (rejected): ${label}\n`);
}

try {
  expectSuccess('canonical private handoff');
  const replacement = expectSuccess(
    'atomic regular-file replacement',
    ({ fixture }) => {
      const directory = path.join(
        fixture,
        'build',
        'reports',
        'google-play-internal-app-sharing',
      );
      fs.mkdirSync(directory, { recursive: true });
      fs.writeFileSync(path.join(directory, 'public-link.json'), '{}\n', {
        mode: 0o600,
      });
    },
  );
  assert.equal(fs.readFileSync(replacement.output, 'utf8').includes(validUrl), true);

  expectFailure('empty input', '', /empty, oversized, or contains whitespace/);
  expectFailure('oversized input', 'A'.repeat(1025), /exceeds 1 KiB/);
  expectFailure(
    'HTTP origin',
    validUrl.replace('https://', 'http://'),
    /canonical Google Play HTTPS origin/,
  );
  expectFailure(
    'wrong host',
    validUrl.replace('play.google.com', 'evil.example'),
    /canonical Google Play HTTPS origin/,
  );
  expectFailure(
    'explicit port',
    validUrl.replace('play.google.com', 'play.google.com:443'),
    /canonical Google Play HTTPS origin/,
  );
  expectFailure(
    'userinfo',
    validUrl.replace('https://', 'https://attacker@'),
    /canonical Google Play HTTPS origin/,
  );
  expectFailure('query', `${validUrl}?x=1`, /canonical Google Play HTTPS origin/);
  expectFailure('fragment', `${validUrl}#x`, /canonical Google Play HTTPS origin/);
  expectFailure('embedded whitespace', `${validUrl} x`, /contains whitespace/);
  expectFailure(
    'short upload token',
    validUrl.replace(uploadToken, 'short'),
    /path or token bounds are invalid/,
  );
  expectFailure(
    'short install token',
    validUrl.replace(installToken, 'C'.repeat(42)),
    /path or token bounds are invalid/,
  );
  expectFailure(
    'invalid token alphabet',
    validUrl.replace(uploadToken, `${'A'.repeat(10)}+`),
    /path or token bounds are invalid/,
  );
  expectFailure(
    'digest mismatch',
    validUrl.replace(installToken, 'C'.repeat(90)),
    /digest does not match/,
  );
  expectFailure(
    'manifest token-length mismatch',
    validUrl,
    /token lengths do not match/,
    ({ manifest }) => {
      manifest.publication.observedUploadTokenLength = 12;
    },
  );
  expectFailure(
    'malformed expiry',
    validUrl,
    /expiry is missing or malformed/,
    ({ manifest }) => {
      manifest.googleConsoleObservation.normalizedExpiryLocal = 'tomorrow';
    },
  );
  expectFailure(
    'symlinked output directory',
    validUrl,
    /must not be a symbolic link/,
    ({ fixture }) => {
      fs.mkdirSync(path.join(fixture, 'build', 'reports'), { recursive: true });
      fs.symlinkSync(
        temporaryRoot,
        path.join(fixture, 'build', 'reports', 'google-play-internal-app-sharing'),
      );
    },
  );
  expectFailure(
    'symlinked output file',
    validUrl,
    /must not be a symbolic link/,
    ({ fixture }) => {
      const directory = path.join(
        fixture,
        'build',
        'reports',
        'google-play-internal-app-sharing',
      );
      fs.mkdirSync(directory, { recursive: true });
      fs.symlinkSync(
        path.join(fixture, 'config', 'target'),
        path.join(directory, 'public-link.json'),
      );
    },
  );

  assert.equal(positiveCases, expectedPositiveCases);
  assert.equal(negativeCases, expectedNegativeCases);
  process.stdout.write(
    `[ias-handoff-writer-test] PASS: ${positiveCases} positive + ` +
      `${negativeCases} negative/adversarial cases\n`,
  );
} finally {
  fs.rmSync(temporaryRoot, { recursive: true, force: true });
}
