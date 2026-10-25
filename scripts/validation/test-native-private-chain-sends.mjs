#!/usr/bin/env node
// Owns disposable private validators and runs Android production send instrumentation.
// No public peers, faucets, mainnet/testnet transactions, or user-wallet preferences.
// Requires prebuilt debug APKs and the reviewed web local-chain helpers in a sibling checkout.
import { randomBytes, createHash } from 'node:crypto';
import fs from 'node:fs/promises';
import net from 'node:net';
import http from 'node:http';
import os from 'node:os';
import path from 'node:path';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
const exec = promisify(execFile);
const rootDir = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const webDir = path.resolve(process.env.FEARLESS_WEB_CHECKOUT || path.join(rootDir, '../fearless-wallet-web'));
const helperDir = path.join(webDir, 'scripts/local-chains');
const { createBitcoindRpc, waitForBitcoind } = await import(pathToFileURL(path.join(helperDir, 'bitcoind-rpc.mjs')));
const { startEsploraShim, toRegtestAddress, toTestnetAddress } = await import(pathToFileURL(path.join(helperDir, 'esplora-regtest-shim.mjs')));
const { writeSolanaGenesisAccounts } = await import(pathToFileURL(path.join(helperDir, 'solana-genesis-accounts.mjs')));
const { spawnLocalProcess } = await import(pathToFileURL(path.join(helperDir, 'process-lifecycle.mjs')));
const walletSolanaAddress = 'HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk'; // public BIP39 abandon/about vector
const serial = process.env.ANDROID_SERIAL || 'emulator-5688';
if (!/^emulator-\d+$/.test(serial)) throw new Error('Private-chain tests require an explicitly owned emulator');
const adb = process.env.ADB || path.join(process.env.ANDROID_HOME || path.join(os.homedir(), 'Library/Android/sdk'), 'platform-tools/adb');
const adbCall = (...args) => exec(adb, ['-s', serial, ...args], { timeout: 600_000, maxBuffer: 8 * 1024 * 1024 });
const evidenceDir = path.join(rootDir, 'build/reports/wp9/private-chains');
const freePort = () =>
  new Promise((resolve, reject) => {
    const server = net.createServer();

    server.once('error', reject);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();

      server.close(() => resolve(port));
    });
  });

async function freePortRange(size) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const start = 20_000 + Math.floor(Math.random() * 30_000);
    const ports = Array.from({ length: size }, (_, index) => start + index);
    const results = await Promise.all(
      ports.map(
        (port) =>
          new Promise((resolve) => {
            const server = net.createServer();

            server.once('error', () => resolve(false));
            server.listen(port, '127.0.0.1', () => server.close(() => resolve(true)));
          })
      )
    );

    if (results.every(Boolean)) return start;
  }

  throw new Error('no free port range');
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function startBitcoind(dataDir) {
  await fs.mkdir(dataDir, { recursive: true });

  const rpcPort = await freePort();
  const p2pPort = await freePort();
  const user = 'fearless';
  const password = randomBytes(16).toString('hex');
  const owned = spawnOwned(
    process.env.FEARLESS_BITCOIND || 'bitcoind',
    [
      '-regtest',
      `-datadir=${dataDir}`,
      `-rpcport=${rpcPort}`,
      `-port=${p2pPort}`,
      '-rpcbind=127.0.0.1',
      '-rpcallowip=127.0.0.1',
      `-rpcuser=${user}`,
      `-rpcpassword=${password}`,
      '-listen=0',
      '-dnsseed=0',
      '-fixedseeds=0',
      '-txindex=1',
      '-fallbackfee=0.0002',
      '-printtoconsole=0',
    ],
    { stdio: ['ignore', 'ignore', 'pipe'] },
    cleanup
  );

  const rpcUrl = `http://127.0.0.1:${rpcPort}`;
  const rpc = createBitcoindRpc({ password, url: rpcUrl, user });

  await waitForBitcoind(rpc, { checkRunning: owned.assertRunning });

  return { password, rpc, rpcUrl, user };
}

async function startSolanaValidator(dataDir) {
  const rpcPort = await freePort();
  const faucetPort = await freePort();
  const gossipPort = await freePort();
  const dynamicStart = await freePortRange(30);
  const genesis = await writeSolanaGenesisAccounts({
    directory: path.join(dataDir, 'accounts'),
    tokenAmount: 1_000_000_000n,
    wallet: walletSolanaAddress,
    walletLamports: 20_000_000_000,
  });
  const owned = spawnOwned(
    process.env.FEARLESS_SOLANA_TEST_VALIDATOR || 'solana-test-validator',
    [
      '--ledger',
      path.join(dataDir, 'ledger'),
      '--reset',
      '--quiet',
      '--bind-address',
      '127.0.0.1',
      '--rpc-port',
      String(rpcPort),
      '--faucet-port',
      String(faucetPort),
      '--gossip-port',
      String(gossipPort),
      '--dynamic-port-range',
      `${dynamicStart}-${dynamicStart + 29}`,
      ...genesis.args,
    ],
    { stdio: ['ignore', 'ignore', 'pipe'] },
    cleanup
  );

  const rpcUrl = `http://127.0.0.1:${rpcPort}`;
  const deadline = Date.now() + 90_000;

  while (Date.now() < deadline) {
    owned.assertRunning();

    try {
      const response = await fetch(rpcUrl, {
        body: JSON.stringify({ id: 1, jsonrpc: '2.0', method: 'getBalance', params: [walletSolanaAddress] }),
        headers: { 'content-type': 'application/json' },
        method: 'POST',
        signal: AbortSignal.timeout(2000),
      });
      const body = await response.json();

      if (body.result?.value > 0) {
        const identity = await fetch(rpcUrl, {
          body: JSON.stringify({ id: 2, jsonrpc: '2.0', method: 'getGenesisHash', params: [] }),
          headers: { 'content-type': 'application/json' }, method: 'POST', signal: AbortSignal.timeout(2000),
        }).then(response => response.json());
        if (identity.jsonrpc !== '2.0' || identity.id !== 2 || typeof identity.result !== 'string' ||
          !/^[1-9A-HJ-NP-Za-km-z]{32,44}$/.test(identity.result)) throw new Error('Invalid private validator identity');
        return { mint: genesis.mint, rpcUrl, genesisHash: identity.result, walletTokenAccount: genesis.walletTokenAccount };
      }
    } catch {
      // still starting
    }
    await wait(500);
  }

  throw new Error(`solana-test-validator did not become ready: ${owned.diagnostic()}`);
}


const runDir = await fs.mkdtemp(path.join(os.tmpdir(), 'fearless-android-private-chains-'));
const cleanup = [];
let cleanupPromise;
let shuttingDown = false;
function spawnOwned(command, args, options, owners) {
  if (shuttingDown) throw new Error('Private-chain runner is shutting down');
  return spawnLocalProcess(command, args, options, owners);
}
const cleanUp = () => cleanupPromise ??= (async () => {
  shuttingDown = true;
  for (const step of cleanup.reverse()) await step().catch(error => console.error('Owned cleanup failed:', error.message));
  await fs.rm(runDir, { recursive: true, force: true });
})();
for (const [signal, code] of [['SIGINT', 130], ['SIGTERM', 143]]) process.once(signal, () => { void cleanUp().then(() => process.exit(code)); });
let exitCode = 1;
try {
  await fs.mkdir(evidenceDir, { recursive: true });
  const helperPins = {};
  for (const name of ['bitcoind-rpc.mjs', 'esplora-regtest-shim.mjs', 'solana-genesis-accounts.mjs', 'process-lifecycle.mjs']) {
    helperPins[name] = createHash('sha256').update(await fs.readFile(path.join(helperDir, name))).digest('hex');
  }
  const bitcoin = await startBitcoind(path.join(runDir, 'bitcoin'));
  cleanup.push(() => bitcoin.rpc.call('stop').catch(() => undefined));
  await bitcoin.rpc.call('createwallet', ['wp9-private']);
  const miner = await bitcoin.rpc.call('getnewaddress', ['', 'bech32'], 'wp9-private');
  await bitcoin.rpc.call('generatetoaddress', [101, miner]);
  const shim = await startEsploraShim({ rpc: bitcoin.rpc }); cleanup.push(() => shim.close());
  const solana = await startSolanaValidator(path.join(runDir, 'solana'));
  const token = randomBytes(24).toString('hex');
  const control = http.createServer(async (request, response) => {
    try {
      if (request.headers.authorization !== `Bearer ${token}`) { response.writeHead(403); response.end(); return; }
      let body = ''; for await (const part of request) { body += part; if (body.length > 2048) throw new Error('Request too large'); }
      const args = body ? JSON.parse(body) : {};
      let result;
      if (request.method === 'POST' && request.url === '/bitcoin/fund') {
        if (!/^tb1q[023456789acdefghjklmnpqrstuvwxyz]{38}$/.test(args.address) || !Number.isSafeInteger(args.sats) || args.sats < 1000 || args.sats > 100_000_000) throw new Error('Invalid private funding request');
        result = await bitcoin.rpc.call('sendtoaddress', [toRegtestAddress(args.address), args.sats / 100_000_000], 'wp9-private');
        await bitcoin.rpc.call('generatetoaddress', [1, miner]);
      } else if (request.method === 'POST' && request.url === '/bitcoin/mine') {
        if (!Number.isInteger(args.blocks) || args.blocks < 1 || args.blocks > 6) throw new Error('Invalid block count');
        result = await bitcoin.rpc.call('generatetoaddress', [args.blocks, miner]);
      } else if (request.method === 'GET' && /^\/bitcoin\/tx\/[0-9a-f]{64}$/.test(request.url)) {
        result = await bitcoin.rpc.call('getrawtransaction', [request.url.split('/').at(-1), true]);
        for (const output of result.vout) if (output.scriptPubKey.address?.startsWith('bcrt1')) output.testnetAddress = toTestnetAddress(output.scriptPubKey.address);
      } else if (request.method === 'GET' && request.url === '/bitcoin/mempool') {
        result = await bitcoin.rpc.call('getrawmempool');
      } else { response.writeHead(404); response.end(); return; }
      response.writeHead(200, { 'content-type': 'application/json' }); response.end(JSON.stringify({ result }));
    } catch (error) { response.writeHead(400, { 'content-type': 'application/json' }); response.end(JSON.stringify({ error: error.message })); }
  });
  await new Promise(resolve => control.listen(0, '127.0.0.1', resolve));
  cleanup.push(() => new Promise(resolve => control.close(resolve)));
  const controlUrl = `http://127.0.0.1:${control.address().port}`;
  for (const url of [shim.url, solana.rpcUrl, controlUrl]) {
    const port = new URL(url).port;
    await adbCall('reverse', `tcp:${port}`, `tcp:${port}`);
    cleanup.push(() => adbCall('reverse', '--remove', `tcp:${port}`));
  }
  for (const apk of ['app/build/outputs/apk/debug/app-debug.apk', 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']) {
    await adbCall('install', '-r', '-t', path.join(rootDir, apk));
  }
  console.info('Owned regtest and Solana validators ready; running native production send tests.');
  const started = Date.now();
  const result = await adbCall('shell', 'am', 'instrument', '-w', '-r',
    '-e', 'class', 'jp.co.soramitsu.app.root.domain.send.NativePrivateChainSendInstrumentedTest',
    '-e', 'bitcoinUrl', shim.url, '-e', 'solanaUrl', solana.rpcUrl, '-e', 'solanaMint', solana.mint,
    '-e', 'solanaGenesisHash', solana.genesisHash,
    '-e', 'controlUrl', controlUrl, '-e', 'controlToken', token,
    'jp.co.soramitsu.fearless.debug.test/androidx.test.runner.AndroidJUnitRunner');
  await fs.writeFile(path.join(evidenceDir, 'instrumentation.log'), result.stdout + result.stderr);
  console.info(result.stdout);
  if (!/OK \(2 tests\)/.test(result.stdout) || /FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed|INSTRUMENTATION_STATUS_CODE: -3/.test(result.stdout)) throw new Error('Native private-chain tests did not pass');
  await fs.writeFile(path.join(evidenceDir, 'environment.json'), JSON.stringify({ serial, elapsedMillis: Date.now() - started,
    helperPins, bitcoin: 'regtest, listen=0, dnsseed=0, fixedseeds=0', solana: 'local test validator with public genesis vectors',
    solanaGenesisHash: solana.genesisHash,
    transport: 'adb reverse to allocated 127.0.0.1 ports; native HTTP rejects all other hosts' }, null, 2) + '\n');
  exitCode = 0;
} catch (error) { console.error(error.message); } finally { await cleanUp(); }
process.exitCode = exitCode;
