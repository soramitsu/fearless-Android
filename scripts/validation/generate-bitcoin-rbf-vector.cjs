// Independent public BIP39 vector, generated with bitcoinjs-lib 6.1.7.
// Usage: node scripts/validation/generate-bitcoin-rbf-vector.cjs <dependency-root>
// This never connects to a node or broadcasts a transaction.
const { createRequire } = require('node:module');
const { resolve } = require('node:path');
const requireFixture = createRequire(resolve(process.argv[2], 'package.json'));
const bitcoin = requireFixture('bitcoinjs-lib');
const ecc = requireFixture('tiny-secp256k1');
const { HDKey } = requireFixture('@scure/bip32');
const { mnemonicToSeedSync } = requireFixture('bip39');
const mnemonic = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
const key = HDKey.fromMasterSeed(mnemonicToSeedSync(mnemonic)).derive("m/84'/0'/0'/0/0");
const payment = bitcoin.payments.p2wpkh({ pubkey: Buffer.from(key.publicKey) });
const transaction = new bitcoin.Psbt();
transaction.setVersion(2);
transaction.addInput({ hash: '11'.repeat(32), index: 1, sequence: 0xfffffffd,
  witnessUtxo: { script: payment.output, value: 100000 } });
transaction.addOutput({ address: 'bc1qslk39wvggqa0vl8nd6jckaz54dw3vk45c5w60m', value: 50000 });
transaction.addOutput({ address: payment.address, value: 49718 });
transaction.signInput(0, { publicKey: Buffer.from(key.publicKey), sign: hash => Buffer.from(ecc.sign(hash, key.privateKey)) });
if (!transaction.validateSignaturesOfAllInputs((publicKey, hash, signature) => ecc.verify(hash, publicKey, signature))) {
  throw new Error('Independent signature verification failed');
}
transaction.finalizeAllInputs();
const signed = transaction.extractTransaction();
process.stdout.write(JSON.stringify({ library: requireFixture('bitcoinjs-lib/package.json').version,
  sequence: signed.ins[0].sequence, txid: signed.getId(), txHex: signed.toHex(), vsize: signed.virtualSize(), feeSats: transaction.getFee() }, null, 2) + '\n');
