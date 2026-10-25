// Public deterministic curve/PDA fixtures, verified independently by @solana/web3.js.
// Usage: node scripts/validation/generate-solana-ata-vectors.cjs <dependency-root>
const { createRequire } = require('node:module');
const { resolve } = require('node:path');
const { pbkdf2Sync, createHmac } = require('node:crypto');
const fixtureRequire = createRequire(resolve(process.argv[2], 'package.json'));
const { Keypair, PublicKey } = fixtureRequire('@solana/web3.js');
const program = new PublicKey('TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA');
const associatedProgram = new PublicKey('ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL');
const mint = Keypair.fromSeed(Buffer.alloc(32, 201)).publicKey;
const vectors = [program, new PublicKey("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")].flatMap(tokenProgram => Array.from({length: 32}, (_, index) => {
  const owner = Keypair.fromSeed(Buffer.alloc(32, index + 1)).publicKey;
  const [address, bump] = PublicKey.findProgramAddressSync([owner.toBuffer(), tokenProgram.toBuffer(), mint.toBuffer()], associatedProgram);
  return {owner: owner.toBase58(), mint: mint.toBase58(), tokenProgram: tokenProgram.toBase58(), address: address.toBase58(), bump};
}));
// Requalify existing public BIP39 tests using SLIP-0010 and the independent SDK.
function legacyAddress(account) {
  const seed = pbkdf2Sync('abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about', 'mnemonic', 2048, 64, 'sha512');
  let material = createHmac('sha512', 'ed25519 seed').update(seed).digest();
  for (const index of [44, 501, account, 0]) {
    const child = Buffer.alloc(4); child.writeUInt32BE(index + 0x80000000);
    material = createHmac('sha512', material.subarray(32)).update(Buffer.concat([Buffer.from([0]), material.subarray(0, 32), child])).digest();
  }
  return Keypair.fromSeed(material.subarray(0, 32)).publicKey;
}
const legacyMint = legacyAddress(3);
const legacy = [6, 7].flatMap(account => [program, new PublicKey('TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb')].map(tokenProgram => {
  const owner = legacyAddress(account);
  const [address, bump] = PublicKey.findProgramAddressSync([owner.toBuffer(), tokenProgram.toBuffer(), legacyMint.toBuffer()], associatedProgram);
  return {account, tokenProgram: tokenProgram.toBase58(), owner: owner.toBase58(), mint: legacyMint.toBase58(), address: address.toBase58(), bump};
}));
process.stdout.write(JSON.stringify({library: fixtureRequire('@solana/web3.js/package.json').version, vectors, legacy}, null, 2) + '\n');
