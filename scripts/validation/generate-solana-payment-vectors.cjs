// Independent complete message fixtures from pinned Solana SDKs; public deterministic accounts only.
// Usage: node scripts/validation/generate-solana-payment-vectors.cjs <dependency-root>
const { createRequire } = require('node:module');
const { resolve, dirname } = require('node:path');
const { readFileSync } = require('node:fs');
const fixtureRequire = createRequire(resolve(process.argv[2], 'package.json'));
const { Keypair, PublicKey, TransactionMessage, VersionedTransaction, ComputeBudgetProgram, SystemProgram } = fixtureRequire('@solana/web3.js');
const { createTransferCheckedInstruction, createAssociatedTokenAccountIdempotentInstruction, getAssociatedTokenAddressSync } = fixtureRequire('@solana/spl-token');
const owner = Keypair.fromSeed(Buffer.alloc(32, 1)).publicKey;
const recipient = Keypair.fromSeed(Buffer.alloc(32, 2)).publicKey;
const mint = Keypair.fromSeed(Buffer.alloc(32, 3)).publicKey;
const source = getAssociatedTokenAddressSync(mint, owner);
const secondSource = Keypair.fromSeed(Buffer.alloc(32, 4)).publicKey;
const destination = getAssociatedTokenAddressSync(mint, recipient);
const blockhash = Keypair.fromSeed(Buffer.alloc(32, 5)).publicKey.toBase58();
const vectors = [];
function record(name, fields, instructions, limit) {
  const message = new TransactionMessage({payerKey: owner, recentBlockhash: blockhash, instructions: [
    ComputeBudgetProgram.setComputeUnitLimit({units: limit}), ComputeBudgetProgram.setComputeUnitPrice({microLamports: 1250n}), ...instructions
  ]}).compileToLegacyMessage();
  vectors.push({name, sender: owner.toBase58(), recipient: recipient.toBase58(), mint: mint.toBase58(), blockhash,
    microLamports: 1250, limit, ...fields, messageBase64: Buffer.from(message.serialize()).toString('base64'),
    transactionBase64: Buffer.from(new VersionedTransaction(message).serialize()).toString('base64')});
}
record('native', {amount: '2500000'}, [SystemProgram.transfer({fromPubkey: owner, toPubkey: recipient, lamports: 2500000})], 10000);
for (const create of [false, true]) {
  const debits = [{account: source.toBase58(), amount: '9223372036854775808'}, {account: secondSource.toBase58(), amount: '9223372036854775807'}];
  record(create ? 'spl-create-full-u64' : 'spl-existing-full-u64', {decimals: 6, create, debits}, [
    ...(create ? [createAssociatedTokenAccountIdempotentInstruction(owner, destination, recipient, mint)] : []),
    ...debits.map(debit => createTransferCheckedInstruction(new PublicKey(debit.account), mint, destination, owner, BigInt(debit.amount), 6))
  ], 200000);
}
const web3 = fixtureRequire('@solana/web3.js/package.json').version;
const splToken = JSON.parse(readFileSync(resolve(dirname(fixtureRequire.resolve('@solana/spl-token')), '../../package.json'), 'utf8')).version;
if (web3 !== '1.98.4' || splToken !== '0.4.14') throw new Error('Install the pinned fixture SDK versions');
process.stdout.write(JSON.stringify({web3, splToken, vectors}, null, 2) + '\n');
