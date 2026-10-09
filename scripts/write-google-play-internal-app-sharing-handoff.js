#!/usr/bin/env node
'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const manifestPath = path.join(
  root,
  'config',
  'google-play-internal-app-sharing-publication.json',
);
const outputDirectory = path.join(
  root,
  'build',
  'reports',
  'google-play-internal-app-sharing',
);
const outputPath = path.join(outputDirectory, 'public-link.json');

function fail(message) {
  process.stderr.write(
    `[google-play-ias-handoff][android][error] ${message}\n`,
  );
  process.exit(1);
}

function requireSafePath(existingPath, label) {
  let stat;
  try {
    stat = fs.lstatSync(existingPath);
  } catch (error) {
    if (error.code === 'ENOENT') {
      return;
    }
    fail(`${label} could not be inspected`);
  }
  if (stat.isSymbolicLink()) {
    fail(`${label} must not be a symbolic link`);
  }
}

const rawInput = fs.readFileSync(0, 'utf8');
if (Buffer.byteLength(rawInput, 'utf8') > 1024) {
  fail('share URL input exceeds 1 KiB');
}
const shareUrl = rawInput.replace(/[\r\n]+$/, '');
if (
  shareUrl.length < 80 ||
  shareUrl.length > 512 ||
  /[\u0000-\u0020\u007f]/.test(shareUrl)
) {
  fail('share URL is empty, oversized, or contains whitespace');
}

let parsed;
try {
  parsed = new URL(shareUrl);
} catch {
  fail('share URL is not valid');
}
if (
  parsed.protocol !== 'https:' ||
  parsed.hostname !== 'play.google.com' ||
  parsed.port !== '' ||
  parsed.username !== '' ||
  parsed.password !== '' ||
  parsed.search !== '' ||
  parsed.hash !== '' ||
  parsed.href !== shareUrl
) {
  fail('share URL does not use the canonical Google Play HTTPS origin');
}
const match = /^\/apps\/test\/([0-9A-Za-z_-]{10,64})\/([0-9A-Za-z_-]{43,256})$/
  .exec(parsed.pathname);
if (!match) {
  fail('share URL path or token bounds are invalid');
}

let manifest;
try {
  manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
} catch {
  fail('publication manifest is unavailable or invalid');
}
const digest = crypto
  .createHash('sha256')
  .update(shareUrl, 'utf8')
  .digest('hex');
if (digest !== manifest.publication?.shareUrlSha256) {
  fail('share URL digest does not match the publication manifest');
}
if (
  match[1].length !== manifest.publication?.observedUploadTokenLength ||
  match[2].length !== manifest.publication?.observedInstallTokenLength
) {
  fail('share URL token lengths do not match the publication manifest');
}
const expiry = manifest.googleConsoleObservation?.normalizedExpiryLocal;
if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(expiry || '')) {
  fail('publication expiry is missing or malformed');
}

for (const component of [
  root,
  path.join(root, 'build'),
  path.join(root, 'build', 'reports'),
  outputDirectory,
  outputPath,
]) {
  requireSafePath(component, path.relative(root, component) || 'repository root');
}
fs.mkdirSync(outputDirectory, { recursive: true, mode: 0o700 });
fs.chmodSync(outputDirectory, 0o700);

const handoff = {
  schemaVersion: 1,
  shareUrl,
  shareUrlSha256: digest,
  expiresDisplayLocal: expiry,
  timezone: 'not-displayed-by-google',
};
const temporaryPath = path.join(
  outputDirectory,
  `.public-link.${process.pid}.${crypto.randomBytes(8).toString('hex')}.tmp`,
);
try {
  const descriptor = fs.openSync(
    temporaryPath,
    fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY,
    0o600,
  );
  try {
    fs.writeFileSync(
      descriptor,
      `${JSON.stringify(handoff, null, 2)}\n`,
      'utf8',
    );
    fs.fsyncSync(descriptor);
  } finally {
    fs.closeSync(descriptor);
  }
  fs.renameSync(temporaryPath, outputPath);
  fs.chmodSync(outputPath, 0o600);
} catch (error) {
  try {
    fs.rmSync(temporaryPath, { force: true });
  } catch {
    // Preserve the original failure.
  }
  fail(`could not write the private handoff: ${error.message}`);
}

process.stdout.write(
  `[google-play-ias-handoff][android] wrote private mode-0600 handoff ` +
    `sha256=${digest} expiry=${expiry}\n`,
);
