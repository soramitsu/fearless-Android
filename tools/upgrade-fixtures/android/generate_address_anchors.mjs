// Independent SS58 and EIP-55 anchors for HistoricalCryptoGoldenVectorTest.
//
// Encodes AL-4 family 05 public keys with polkadot-js (@polkadot/util-crypto),
// an implementation unrelated to fearless-utils and shared-features, so the
// RC's SS58 codec and Ethereum address derivation are checked against a third
// party rather than against themselves.
//
// Usage: node generate_address_anchors.mjs <fearless-wallet-web checkout> <05-keypairs.json>
import { createRequire } from 'node:module';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

const [webRepo, keypairsPath] = process.argv.slice(2);
if (!webRepo || !keypairsPath) {
  console.error('usage: node generate_address_anchors.mjs <fearless-wallet-web checkout> <05-keypairs.json>');
  process.exit(2);
}
const require = createRequire(join(webRepo, 'package.json'));
const { encodeAddress, decodeAddress, ethereumEncode, blake2AsU8a, cryptoWaitReady } = require('@polkadot/util-crypto');
const { u8aToHex, hexToU8a } = require('@polkadot/util');
const version = JSON.parse(
  readFileSync(join(webRepo, 'node_modules/@polkadot/util-crypto/package.json'), 'utf8')
).version;
await cryptoWaitReady();

const vectors = JSON.parse(readFileSync(keypairsPath, 'utf8')).vectors;
const PER_CURVE = { SR25519: 3, ED25519: 3, ECDSA: 2 };
const publicKeys = [];
for (const vector of vectors) {
  if (vector.kind !== 'substrate') continue;
  const taken = publicKeys.filter((key) => key.cryptoType === vector.cryptoType).length;
  if (taken >= PER_CURVE[vector.cryptoType]) continue;
  if (publicKeys.some((key) => key.publicKey === vector.expected.publicKey)) continue;
  publicKeys.push({ cryptoType: vector.cryptoType, publicKey: vector.expected.publicKey });
}

const ss58 = [];
for (const { publicKey } of publicKeys) {
  const bytes = hexToU8a(publicKey);
  const accountId = bytes.length > 32 ? blake2AsU8a(bytes, 256) : bytes;
  for (const prefix of [0, 2, 42, 69]) {
    const address = encodeAddress(accountId, prefix);
    if (u8aToHex(decodeAddress(address)) !== u8aToHex(accountId)) {
      throw new Error(`SS58 round trip failed for ${publicKey}/${prefix}`);
    }
    ss58.push({ publicKey, accountId: u8aToHex(accountId), prefix, address });
  }
}

const ethereum = [];
for (const vector of vectors) {
  if (ethereum.length >= 8) break;
  if (!['ethereum', 'ethereum-private-key'].includes(vector.kind) || !vector.expected?.publicKey) continue;
  ethereum.push({ publicKey: vector.expected.publicKey, eip55: ethereumEncode(hexToU8a(vector.expected.publicKey)) });
}

console.log(JSON.stringify({ library: `@polkadot/util-crypto ${version}`, ss58, ethereum }, null, 1));
