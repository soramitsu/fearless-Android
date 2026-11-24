// Live IrohaConnect pairing against https://taira.sora.org: the upstream iroha-js app SDK registers a session and
// plays the dApp; the Android wallet on the emulator joins through the same-device irohaconnect:// link (its intent
// filter), and a person approves on the device. Every signature is verified here. Nothing is submitted to the chain.
// Run through live.sh: it sets up the same harness as generate.sh, then runs this script.
import { execFileSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';

import { NetworkId } from '@iroha/iroha-js/browser';
import { createConnectAppSession, createConnectSessionPreview, registerConnectSession, rewriteConnectUriProtocol } from '@iroha/iroha-js/connect-browser';
import { browserTransactionPayloadHashHex, buildBrowserExecutableBatchPayload, buildBrowserTransferPayload } from '@iroha/iroha-js/transaction-codec';
import { ed25519 } from '@noble/curves/ed25519';

import { IROHA_CONNECT_PROFILES } from '@extension-base/services/iroha-connect-service/wire';
import { UNIVERSAL_WALLET_IROHA_NETWORKS } from '@/consts/universalWallet';
import { encodeIrohaI105Address, parseIrohaI105Address } from '@/util/iroha';

const [evidencePath, serial] = process.argv.slice(2);
const TAIRA = IROHA_CONNECT_PROFILES.taira;
const NETWORK = NetworkId.parse(TAIRA.networkId!);
const XOR = UNIVERSAL_WALLET_IROHA_NETWORKS.taira.nativeAsset.id;
const CONTRACT = 'irohac1qyqqqqqqqqqqqq95fes93ygegsv5enq9mqsz6x4lv4vp9gg4yxgjw';
const DESTINATION = encodeIrohaI105Address(Buffer.from(ed25519.getPublicKey(new Uint8Array(32).fill(0x0b))).toString('hex'), 'taira');
const started = Date.now();
const events: string[] = [];
const log = (line: string) => {
  const entry = `${((Date.now() - started) / 1000).toFixed(1)}s ${line}`;
  events.push(entry);
  console.log(entry);
};
const evidence: Record<string, unknown> = { torii: TAIRA.toriiBaseUrl, networkId: TAIRA.networkId, startedAt: new Date().toISOString(), events };
const save = () => writeFileSync(evidencePath, `${JSON.stringify(evidence, null, 2)}\n`);
const timeout = <T>(promise: Promise<T>, ms: number, label: string) =>
  Promise.race([promise, new Promise<never>((_, reject) => setTimeout(() => reject(new Error(`timed out waiting for ${label}`)), ms))]);

class LoggedWebSocket extends WebSocket {
  constructor(url: string, protocols?: string | string[]) {
    super(url, protocols);
    this.addEventListener('message', (event) => log(`app socket received ${(event.data as ArrayBuffer).byteLength ?? '?'} bytes`));
    this.addEventListener('close', (event) => log(`app socket closed ${(event as CloseEvent).code}`));
  }
}

const preview = createConnectSessionPreview({ networkId: NETWORK, node: TAIRA.toriiBaseUrl });
const session = await registerConnectSession(TAIRA.toriiBaseUrl, preview, { node: TAIRA.toriiBaseUrl });
evidence.sid = session.sid;
const launchUri = rewriteConnectUriProtocol(session.wallet_uri, 'irohaconnect');
evidence.walletUriScheme = session.wallet_uri.split(':')[0];
log(`registered session ${session.sid}; Torii wallet link scheme ${evidence.walletUriScheme}`);
const app = createConnectAppSession({
  baseUrl: TAIRA.toriiBaseUrl,
  preview,
  session,
  webSocketImpl: LoggedWebSocket,
  appMeta: { name: 'Fearless Android live check', url: 'https://fearlesswallet.io' },
  permissions: { methods: ['sign_transaction', 'sign_raw'], events: [], resources: ['iroha:torii:canonical-request:v1'] },
} as never);
const approval = app.waitForApproval();
approval.catch(() => undefined);

try {
  // The widget's same-device "Open wallet" button: an irohaconnect:// VIEW intent the wallet's filter receives.
  execFileSync(process.env.ADB ?? 'adb', ['-s', serial, 'shell', 'am', 'start', '-a', 'android.intent.action.VIEW', '-d', `'${launchUri}'`], { stdio: 'inherit' });
  log('STEP approve-session: launched irohaconnect:// on the device; approve the connection there');
  const approved = await timeout(approval, 600_000, 'session approval');
  const accountId = approved.accountId as string;
  const publicKeyHex = parseIrohaI105Address(accountId).publicKeyHex;
  evidence.approvedAccount = accountId;
  log(`approved by ${accountId}`);

  const transfer = new Uint8Array(buildBrowserTransferPayload({
    networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: accountId, sourceAssetHoldingId: `${XOR}#${accountId}`,
    quantity: '0.001', destinationAccountId: DESTINATION,
    feePayment: { payer: 'authority', chargeLimits: [{ kind: 'nexus', assetDefinitionId: XOR, maxAmount: '0.1' }], gasLimit: null },
    metadata: { memo: 'fearless android live check' }, ttlMs: 100_000,
  } as never));
  const transferHash = browserTransactionPayloadHashHex(transfer, TAIRA.networkPrefix);
  evidence.transferPayloadHash = transferHash;
  log(`STEP sign-transfer: review 0.001 XOR to ${DESTINATION}, payload hash ${transferHash}; tap Sign`);
  const transferSignature = await timeout(app.signTransaction(transfer), 600_000, 'the transfer signature');
  const transferOk = ed25519.verify(transferSignature, Buffer.from(transferHash, 'hex'), publicKeyHex);
  evidence.transferSignatureVerifies = transferOk;
  log(`transfer signature verifies: ${transferOk}`);
  if (!transferOk) throw new Error('transfer signature does not verify');

  log('STEP keepalive: waiting 45 s (two wallet keepalive intervals)');
  let closedEarly = false;
  app.on?.('close', () => { closedEarly = true; });
  await new Promise((resolve) => setTimeout(resolve, 45_000));
  evidence.sessionSurvivedKeepalives = !closedEarly;
  log(`session alive after 45 s: ${!closedEarly}`);

  const call = new Uint8Array(buildBrowserExecutableBatchPayload({
    networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: accountId,
    entries: [{ kind: 'contractCall', contractAddress: CONTRACT, expectedCodeHash: `${'ab'.repeat(31)}a1`, entrypoint: 'place_order', arguments: Uint8Array.of(1, 2, 3) }],
    feePayment: { payer: 'authority', chargeLimits: [{ kind: 'nexus', assetDefinitionId: XOR, maxAmount: '0.1' }], gasLimit: '50000' }, ttlMs: 100_000,
  } as never));
  const callHash = browserTransactionPayloadHashHex(call, TAIRA.networkPrefix);
  evidence.contractCallPayloadHash = callHash;
  log(`STEP sign-contract-call: review place_order (3 argument bytes), payload hash ${callHash}; tap Sign`);
  const callSignature = await timeout(app.signTransaction(call), 600_000, 'the contract-call signature');
  const callOk = ed25519.verify(callSignature, Buffer.from(callHash, 'hex'), publicKeyHex);
  evidence.contractCallSignatureVerifies = callOk;
  log(`contract-call signature verifies: ${callOk}`);
  if (!callOk) throw new Error('contract-call signature does not verify');

  log('STEP raw: asking for a raw signature; the wallet must refuse on its own');
  const rawCode = await timeout(app.signRaw('iroha:torii:canonical-request:v1', Uint8Array.of(1, 2, 3)).then(() => 'SIGNED', (error: any) => error?.code ?? String(error)), 60_000, 'the raw answer');
  evidence.rawRequestAnswer = rawCode;
  log(`raw request answer: ${rawCode}`);
  if (rawCode !== 'SIGNING_KEY_REQUIRED') throw new Error(`raw request was answered with ${rawCode}`);

  log('STEP close: the dApp ends the session; the wallet should show that the dApp ended it');
  app.close('live check done');
  await new Promise((resolve) => setTimeout(resolve, 3_000));
  evidence.result = 'passed';
} catch (error) {
  evidence.result = 'failed';
  evidence.error = (error as Error).message;
  log(`FAILED ${(error as Error).message}`);
  process.exitCode = 1;
} finally {
  evidence.finishedAt = new Date().toISOString();
  try { app.close('live check cleanup'); } catch {}
  save();
  log(`evidence written to ${evidencePath}`);
  setTimeout(() => process.exit(), 1_000);
}
