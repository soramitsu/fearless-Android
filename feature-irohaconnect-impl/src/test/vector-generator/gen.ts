// Generates the Android IrohaConnect cross-check vectors from the browser wallet's implementation
// (fearless-wallet-web feature/iroha-connect-current) and the upstream iroha-js 0.0.3 app SDK and codec.
// Run through generate.sh, which extracts the web sources at the pinned commit next to this script.
import { writeFileSync } from 'node:fs';

import { NetworkId } from '@iroha/iroha-js/browser';
import { createConnectAppSession, createConnectSessionPreview } from '@iroha/iroha-js/connect-browser';
import { buildBrowserExecutableBatchPayload, buildBrowserTransferPayload } from '@iroha/iroha-js/transaction-codec';
import { blake3 } from '@noble/hashes/blake3';
import { ed25519, x25519 } from '@noble/curves/ed25519';

import {
  IROHA_CONNECT_PROFILES,
  IrohaConnectError,
  buildIrohaConnectApprovalPreimage,
  buildIrohaConnectWebSocket,
  crc64Xz,
  decodeIrohaConnectFrame,
  deriveIrohaConnectDirectionKeys,
  deriveIrohaConnectSessionId,
  encodeIrohaConnectApproveFrame,
  encodeIrohaConnectPingFrame,
  encodeIrohaConnectPongFrame,
  encodeIrohaConnectRejectFrame,
  irohaConnectRelayAuthHash,
  parseIrohaConnectUri,
  sealIrohaConnectEnvelope,
  toBase64Url,
} from '@extension-base/services/iroha-connect-service/wire';
import { IrohaConnectTransactionError, reviewIrohaConnectTransaction } from '@extension-base/services/iroha-connect-service/transactionReview';
import { IrohaConnectService } from '@extension-base/services/iroha-connect-service';
import { UNIVERSAL_WALLET_IROHA_NETWORKS } from '@/consts/universalWallet';
import { encodeIrohaI105Address } from '@/util/iroha';
import { deriveIrohaSigningKey } from '@/util/irohaKeyring';

const hex = (bytes: Uint8Array): string => Buffer.from(bytes).toString('hex');
const unhex = (value: string): Uint8Array => Uint8Array.from(Buffer.from(value, 'hex'));
const fill = (length: number, value: number) => new Uint8Array(length).fill(value);
const TAIRA = IROHA_CONNECT_PROFILES.taira;
const NETWORK = NetworkId.parse(TAIRA.networkId!);
const XOR = UNIVERSAL_WALLET_IROHA_NETWORKS.taira.nativeAsset.id;
const MNEMONIC = 'abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about';
const derived = deriveIrohaSigningKey({ mnemonic: MNEMONIC });
const ACCOUNT = encodeIrohaI105Address(derived.publicKeyHex, 'taira');
const keyAccount = (seedByte: number) => encodeIrohaI105Address(hex(ed25519.getPublicKey(fill(32, seedByte))), 'taira');
const OTHER = keyAccount(0x0b);
const SPONSOR = keyAccount(0x0c);
const CONTRACT = 'irohac1qyqqqqqqqqqqqq95fes93ygegsv5enq9mqsz6x4lv4vp9gg4yxgjw';
const TOKEN_APP = toBase64Url(fill(32, 0x51));
const TOKEN_WALLET = toBase64Url(fill(32, 0x52));
const TOKEN_RELAY = toBase64Url(fill(32, 0x53));

const out: Record<string, unknown> = {
  description:
    'Iroha Connect v1 vectors produced by the browser wallet implementation (fearless-wallet-web PR #1122, ' +
    'feature/iroha-connect-current) and the upstream iroha-js 0.0.3 app SDK and transaction codec.',
  webCommit: '6f1fc6b5658d2d371f9ade08a28eeaeaf0cda22c',
  irohaJs: '0.0.3',
  mnemonic: MNEMONIC,
  account: { address: ACCOUNT, publicKeyHex: derived.publicKeyHex },
  otherAccount: OTHER,
  sponsorAccount: SPONSOR,
  taira: { networkId: TAIRA.networkId, networkIdHex: hex(NETWORK.toBytes()), toriiBaseUrl: TAIRA.toriiBaseUrl, networkPrefix: TAIRA.networkPrefix },
};

const errorCode = (action: () => unknown): string | null => {
  try {
    action();
    return null;
  } catch (error) {
    if (error instanceof IrohaConnectError) return error.code;
    if (error instanceof IrohaConnectTransactionError) return error.code;
    return `other:${(error as Error).message}`;
  }
};

// ---------------------------------------------------------------------------------------------------------------
// CRC-64/XZ and NetworkId literals

out.crc64 = ['', '123456789', 'NRT0', 'iroha-connect'].map((text) => ({ inputHex: hex(new TextEncoder().encode(text)), crcHex: crc64Xz(new TextEncoder().encode(text)).toString(16).padStart(16, '0') }));
const otherNetwork = NetworkId.fromBytes(Uint8Array.from({ length: 32 }, (_, index) => (index === 31 ? 1 : 7)));
out.networkIds = [
  { literal: TAIRA.networkId, bytesHex: hex(NETWORK.toBytes()) },
  { literal: otherNetwork.literal, bytesHex: hex(otherNetwork.toBytes()) },
];

// ---------------------------------------------------------------------------------------------------------------
// Links

const APP_PRIVATE = fill(32, 0x21);
const APP_PUBLIC = x25519.getPublicKey(APP_PRIVATE);
const NONCE = fill(16, 0x31);
const SID = deriveIrohaConnectSessionId(NETWORK.toBytes(), APP_PUBLIC, NONCE);
const linkFields = (overrides: Record<string, string | null> = {}) => {
  const fields: Record<string, string | null> = {
    sid: toBase64Url(SID),
    network_id: TAIRA.networkId,
    app_pk: toBase64Url(APP_PUBLIC),
    nonce: toBase64Url(NONCE),
    node: TAIRA.toriiBaseUrl,
    v: '1',
    role: 'wallet',
    token: TOKEN_WALLET,
    relay: TOKEN_RELAY,
    ...overrides,
  };
  const params = new URLSearchParams();
  for (const [name, value] of Object.entries(fields)) if (value !== null) params.append(name, value);
  return params.toString();
};
const link = (overrides: Record<string, string | null> = {}, scheme = 'iroha') => `${scheme}://connect?${linkFields(overrides)}`;
const linkInputs: string[] = [
  link(),
  link({}, 'irohaconnect'),
  // A link whose query is not percent-encoded beyond what the NetworkId needs.
  `iroha://connect?sid=${toBase64Url(SID)}&network_id=${encodeURIComponent(TAIRA.networkId!)}&app_pk=${toBase64Url(APP_PUBLIC)}&nonce=${toBase64Url(NONCE)}&node=https://taira.sora.org&v=1&role=wallet&token=${TOKEN_WALLET}&relay=${TOKEN_RELAY}`,
  `iroha://connect?sid=${toBase64Url(fill(32, 1))}&chain_id=x&node=${TAIRA.toriiBaseUrl}&v=1&role=wallet&token=${TOKEN_WALLET}`,
  link({ node: 'https://evil.example' }),
  link({ node: 'https://taira.sora.org/' }),
  link({ network_id: otherNetwork.literal }),
  link({ network_id: otherNetwork.literal, node: 'https://minamoto.sora.org' }),
  link({ network_id: otherNetwork.literal, node: 'https://minamoto.sora.org/' }),
  link({ role: 'app' }),
  link({ v: '2' }),
  link({ relay: null }),
  link({ extra: 'x' }),
  link({ token: `${TOKEN_WALLET}=` }),
  link({ token: toBase64Url(fill(31, 0x52)) }),
  link({ token: toBase64Url(fill(32, 0)) }),
  link({ network_id: TAIRA.networkId!.toLowerCase() }),
  link({ network_id: TAIRA.networkId!.replace('#6774', '#6775') }),
  link({ sid: toBase64Url(fill(32, 9)) }),
  link({ app_pk: toBase64Url(fill(32, 0x22)) }),
  link({ nonce: toBase64Url(fill(16, 0x32)) }),
  `${link()}&sid=${toBase64Url(fill(32, 9))}`,
  link().replace('connect?', 'connect/path?'),
  ` ${link()}`,
  `${link()}#fragment`,
  link({}, 'https'),
  link({ v: '' }),
  'iroha://connect?',
  'iroha://connect',
];
out.links = linkInputs.map((uri) => {
  try {
    const parsed = parseIrohaConnectUri(uri);
    const socket = buildIrohaConnectWebSocket(parsed);
    return {
      uri,
      scheme: parsed.scheme,
      sidHex: hex(parsed.sidBytes),
      appPublicKeyHex: hex(parsed.appPublicKey),
      nonceHex: hex(parsed.nonce),
      networkIdHex: hex(parsed.networkIdBytes),
      token: parsed.token,
      relayToken: parsed.relayToken,
      webSocketUrl: socket.url,
      protocol: socket.protocols[0],
    };
  } catch (error) {
    return { uri, error: error instanceof IrohaConnectError ? error.code : `other:${(error as Error).message}` };
  }
});

// ---------------------------------------------------------------------------------------------------------------
// Plain frames, approval preimages, keys and envelopes

const WALLET_PRIVATE = fill(32, 0x41);
const WALLET_PUBLIC = x25519.getPublicKey(WALLET_PRIVATE);
const permissionsNone = Uint8Array.of(0);
const signature = ed25519.sign(fill(8, 1), unhex(hex(fill(32, 7))));
out.frames = {
  sidHex: hex(SID),
  walletPublicKeyHex: hex(WALLET_PUBLIC),
  relayAuthHex: hex(irohaConnectRelayAuthHash(SID, TOKEN_RELAY)),
  relayToken: TOKEN_RELAY,
  preimageNoPermissionsHex: hex(
    buildIrohaConnectApprovalPreimage({ sidBytes: SID, networkIdBytes: NETWORK.toBytes(), appPublicKey: APP_PUBLIC, walletPublicKey: WALLET_PUBLIC, accountId: ACCOUNT, permissionsField: permissionsNone, relayToken: TOKEN_RELAY })
  ),
  approveNoPermissionsHex: hex(
    encodeIrohaConnectApproveFrame({ sidBytes: SID, sequence: 1, walletPublicKey: WALLET_PUBLIC, accountId: ACCOUNT, permissionsField: permissionsNone, signature })
  ),
  signatureHex: hex(signature),
  rejectHex: hex(encodeIrohaConnectRejectFrame({ sidBytes: SID, sequence: 2, code: 4001, codeId: 'user_rejected', reason: 'The wallet user rejected the connection.' })),
  pingHex: hex(encodeIrohaConnectPingFrame({ sidBytes: SID, sequence: 3, nonce: 0xffff_ffff_ffff_fffen })),
  pongHex: hex(encodeIrohaConnectPongFrame({ sidBytes: SID, sequence: 4, nonce: 5n })),
};
const keys = deriveIrohaConnectDirectionKeys(WALLET_PRIVATE.slice(), APP_PUBLIC, SID);
out.keys = { walletPrivateHex: hex(WALLET_PRIVATE), appPublicHex: hex(APP_PUBLIC), sidHex: hex(SID), appKeyHex: hex(keys.appKey), walletKeyHex: hex(keys.walletKey) };
out.envelopes = [
  { sequence: 2, payload: { type: 'sign_result_ok', signatureHex: hex(signature) } },
  { sequence: 3, payload: { type: 'sign_result_err', code: 'USER_REJECTED', message: 'The wallet user rejected the transaction.' } },
  { sequence: 4, payload: { type: 'control_close', code: 1000, reason: 'The wallet disconnected.' } },
].map(({ sequence, payload }) => {
  const walletPayload =
    payload.type === 'sign_result_ok'
      ? { type: 'sign_result_ok' as const, signature: unhex(payload.signatureHex!) }
      : payload.type === 'sign_result_err'
        ? { type: 'sign_result_err' as const, code: payload.code!, message: payload.message! }
        : { type: 'control_close' as const, code: payload.code!, reason: payload.reason! };
  return { sequence, payload, frameHex: hex(sealIrohaConnectEnvelope({ walletKey: keys.walletKey, sidBytes: SID, sequence, payload: walletPayload })) };
});

// ---------------------------------------------------------------------------------------------------------------
// Transaction payloads: SDK-built ones, plus hand-encoded variants the SDK builders would refuse

const enc = {
  compact(value: number): Uint8Array {
    const bytes: number[] = [];
    let remaining = value;
    do {
      let byte = remaining & 0x7f;
      remaining = Math.floor(remaining / 128);
      if (remaining !== 0) byte |= 0x80;
      bytes.push(byte);
    } while (remaining !== 0);
    return Uint8Array.from(bytes);
  },
  cat: (...chunks: Uint8Array[]) => Uint8Array.from(Buffer.concat(chunks.map((chunk) => Buffer.from(chunk)))),
  u32: (value: number) => { const b = Buffer.alloc(4); b.writeUInt32LE(value); return Uint8Array.from(b); },
  u64: (value: bigint | number) => { const b = Buffer.alloc(8); b.writeBigUInt64LE(BigInt(value)); return Uint8Array.from(b); },
  field: (payload: Uint8Array) => enc.cat(enc.compact(payload.length), payload),
  struct: (fields: Uint8Array[]) => enc.cat(...fields.map(enc.field)),
  str: (value: string) => enc.field(new TextEncoder().encode(value)),
  option: (value: Uint8Array | null) => (value === null ? Uint8Array.of(0) : enc.cat(Uint8Array.of(1), enc.field(value))),
  vector: (values: Uint8Array[]) => enc.cat(enc.u64(values.length), ...values.map(enc.field)),
  account: (literal: string, controllerTag = 0, algorithm = 0) => {
    const pk = literal === ACCOUNT ? unhex(derived.publicKeyHex) : literal === OTHER ? ed25519.getPublicKey(fill(32, 0x0b)) : ed25519.getPublicKey(fill(32, 0x0c));
    const controller = Uint8Array.from([algorithm, ...pk]);
    return enc.cat(enc.u32(controllerTag), enc.field(enc.cat(enc.u64(controller.length), ...Array.from(controller, (byte) => enc.field(Uint8Array.of(byte))))));
  },
  definition: (literal: string) => {
    const alphabet = '123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz';
    let value = 0n;
    for (const character of literal) value = value * 58n + BigInt(alphabet.indexOf(character));
    const bytes: number[] = [];
    while (value > 0n) { bytes.unshift(Number(value & 0xffn)); value >>= 8n; }
    return enc.cat(...bytes.slice(1, 17).map((byte) => enc.field(Uint8Array.of(byte))));
  },
  numeric: (mantissaBytes: Uint8Array, scale: number) => enc.struct([enc.cat(enc.u32(mantissaBytes.length), mantissaBytes), enc.u32(scale)]),
  norito: (payload: Uint8Array, schema: Uint8Array, flags = 2, crcOverride?: bigint) =>
    enc.cat(new TextEncoder().encode('NRT0'), Uint8Array.of(0, 0), schema, Uint8Array.of(0), enc.u64(payload.length), enc.u64(crcOverride ?? crc64Xz(payload)), Uint8Array.of(flags), payload),
};
const TRANSFER_SCHEMA = unhex('a4174c78d6341f8f98fc2adae8ed67b9');
const mantissa = (value: bigint) => { const bytes: number[] = []; let r = value; while (r > 0n) { bytes.push(Number(r & 0xffn)); r >>= 8n; } if (bytes.length && bytes[bytes.length - 1] & 0x80) bytes.push(0); return Uint8Array.from(bytes); };

type HandTransfer = {
  domainVariant?: number; network?: Uint8Array; authority?: Uint8Array; creation?: bigint; executable?: Uint8Array; ttl?: Uint8Array; nonce?: Uint8Array;
  fee?: Uint8Array; metadata?: Uint8Array; attachments?: Uint8Array; owner?: Uint8Array; quantity?: Uint8Array; wireId?: string; transferVariant?: number;
  schema?: Uint8Array; crc?: bigint; flags?: number; instructionCount?: number; executableVariant?: number;
};
const defaultFee = (gas: bigint | null = null, limits?: Uint8Array) =>
  enc.cat(enc.u32(0), enc.field(enc.struct([limits ?? enc.vector([enc.struct([enc.u32(0), enc.definition(XOR), enc.numeric(mantissa(25n), 2)])]), enc.option(gas === null ? null : enc.u64(gas))])));
const metadataArchive = (entries: Array<[string, string]>) => enc.vector(entries.map(([key, json]) => enc.struct([enc.str(key), enc.struct([enc.str(json)])])));
const handTransfer = (o: HandTransfer = {}): Uint8Array => {
  const body = enc.struct([
    enc.struct([o.owner ?? enc.account(ACCOUNT), enc.definition(XOR), enc.u32(0)]),
    o.quantity ?? enc.numeric(mantissa(15n), 1),
    enc.account(OTHER),
  ]);
  const inner = enc.cat(enc.u32(o.transferVariant ?? 2), enc.field(body));
  const frame = enc.norito(inner, o.schema ?? TRANSFER_SCHEMA, o.flags ?? 2, o.crc);
  const instruction = enc.cat(enc.field(enc.str(o.wireId ?? 'iroha.transfer')), enc.field(enc.cat(enc.u64(frame.length), frame)));
  const instructions = Array.from({ length: o.instructionCount ?? 1 }, () => instruction);
  const executable = o.executable ?? enc.cat(enc.u32(o.executableVariant ?? 0), enc.field(enc.vector(instructions)));
  return enc.struct([
    enc.cat(enc.u32(o.domainVariant ?? 0), enc.field(o.network ?? NETWORK.toBytes())),
    o.authority ?? enc.account(ACCOUNT),
    enc.u64(o.creation ?? 1_795_000_000_000n),
    executable,
    o.ttl ?? enc.option(enc.u64(100_000n)),
    o.nonce ?? Uint8Array.of(0),
    o.fee ?? defaultFee(),
    o.metadata ?? metadataArchive([['memo', '"hanami"']]),
    o.attachments ?? Uint8Array.of(0),
  ]);
};
const handBatch = (calls: Uint8Array[], fee = defaultFee(50_000n)) =>
  enc.struct([
    enc.cat(enc.u32(0), enc.field(NETWORK.toBytes())),
    enc.account(ACCOUNT),
    enc.u64(1_795_000_000_000n),
    enc.cat(enc.u32(4), enc.field(enc.vector(calls))),
    enc.option(enc.u64(100_000n)),
    Uint8Array.of(0),
    fee,
    enc.vector([]),
    Uint8Array.of(0),
  ]);
const handCall = (address = CONTRACT, codeHash = unhex(`${'ab'.repeat(31)}a1`), entrypoint = 'place_order', args: Uint8Array | null = Uint8Array.of(1, 2, 3), tag = 1) =>
  enc.cat(enc.u32(tag), enc.field(enc.struct([enc.str(address), codeHash, enc.str(entrypoint), enc.option(args === null ? null : enc.cat(enc.u64(args.length), args))])));

const sdkFee = (gasLimit: string | null = null, extra: Record<string, unknown> = {}) => ({
  payer: 'authority', chargeLimits: [{ kind: 'nexus', assetDefinitionId: XOR, maxAmount: '0.25' }], gasLimit, ...extra,
});
const sdkTransfer = (overrides: Record<string, unknown> = {}) =>
  new Uint8Array(buildBrowserTransferPayload({
    networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: ACCOUNT, sourceAssetHoldingId: `${XOR}#${ACCOUNT}`, quantity: '1.5',
    destinationAccountId: OTHER, feePayment: sdkFee(), metadata: { memo: 'hanami' }, creationTimeMs: 1_795_000_000_000, ttlMs: 100_000, ...overrides,
  } as never));
const sdkBatch = (entries: unknown[], overrides: Record<string, unknown> = {}) =>
  new Uint8Array(buildBrowserExecutableBatchPayload({
    networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: ACCOUNT, entries, feePayment: sdkFee('50000'), creationTimeMs: 1_795_000_000_000, ttlMs: 100_000, ...overrides,
  } as never));
const contractCall = (overrides: Record<string, unknown> = {}) => ({ kind: 'contractCall', contractAddress: CONTRACT, expectedCodeHash: `${'ab'.repeat(31)}a1`, entrypoint: 'place_order', arguments: Uint8Array.of(1, 2, 3, 4), ...overrides });

const basic = sdkTransfer();
const cases: Array<[string, Uint8Array, string?]> = [
  ['sdk transfer', basic],
  ['sdk transfer scope nonce two limits gas', sdkTransfer({ sourceAssetHoldingId: `${XOR}#${ACCOUNT}#dataspace:7`, nonce: 42, metadata: null, feePayment: { payer: 'authority', chargeLimits: [{ kind: 'nexus', assetDefinitionId: XOR, maxAmount: '0.25' }, { kind: 'pipelineGas', assetDefinitionId: XOR, maxAmount: '3' }], gasLimit: '1000' } })],
  ['sdk transfer sponsor', sdkTransfer({ feePayment: { payer: 'sponsor', programId: `${SPONSOR}/hanami_program`, programRevision: '3', chargeLimits: [], gasLimit: null } })],
  ['sdk transfer nested metadata', sdkTransfer({ metadata: { a: [1, true, null, 'x', -7], b: { c: 'd', '2': 'two' }, '10': 'ten', '9': 'nine', q: 'quote"\\\n\u0001', z: 9007199254740991 } })],
  ['sdk transfer bidi metadata', sdkTransfer({ metadata: { memo: 'pay‮evil ' } })],
  ['sdk transfer large quantity', sdkTransfer({ quantity: '123456789012345678901234567890.000000000000000000000000001' })],
  ['sdk transfer tiny quantity', sdkTransfer({ quantity: '0.0000000000000000000000000001' })],
  ['sdk transfer high-bit mantissa', sdkTransfer({ quantity: '128' })],
  ['sdk transfer long ttl', sdkTransfer({ ttlMs: 1234567 })],
  ['sdk contract calls', sdkBatch([contractCall(), contractCall({ entrypoint: 'cancel', arguments: null, expectedCodeHash: `${'cd'.repeat(31)}cf` })])],
  ['sdk contract call unicode entrypoint', sdkBatch([contractCall({ entrypoint: 'ご注文', arguments: new Uint8Array(0) })])],
  ['hand transfer equals sdk', handTransfer()],
  ['trailing byte', enc.cat(basic, Uint8Array.of(0))],
  ['truncated', basic.subarray(0, basic.length - 3)],
  ['empty', new Uint8Array(0)],
  ['wrong network', sdkTransfer({ networkId: otherNetwork })],
  ['wrong authority', (() => { const p = new Uint8Array(buildBrowserTransferPayload({ networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: OTHER, sourceAssetHoldingId: `${XOR}#${OTHER}`, quantity: '1', destinationAccountId: ACCOUNT, feePayment: sdkFee(), creationTimeMs: 1, ttlMs: 100_000 } as never)); return p; })()],
  ['attachments present', handTransfer({ attachments: Uint8Array.of(1) })],
  ['domain variant 1', handTransfer({ domainVariant: 1 })],
  ['ttl missing', handTransfer({ ttl: Uint8Array.of(0) })],
  ['ttl zero', handTransfer({ ttl: enc.option(enc.u64(0n)) })],
  ['ttl wrong width', handTransfer({ ttl: enc.option(enc.u32(5)) })],
  ['nonce zero', handTransfer({ nonce: enc.option(enc.u32(0)) })],
  ['nonce present', handTransfer({ nonce: enc.option(enc.u32(77)) })],
  ['metadata unsorted', handTransfer({ metadata: metadataArchive([['z', '1'], ['a', '2']]) })],
  ['metadata duplicate', handTransfer({ metadata: metadataArchive([['a', '1'], ['a', '1']]) })],
  ['metadata spaced json', handTransfer({ metadata: metadataArchive([['a', '{ "b": 1 }']]) })],
  ['metadata float', handTransfer({ metadata: metadataArchive([['a', '1.5']]) })],
  ['metadata big integer', handTransfer({ metadata: metadataArchive([['a', '9007199254740993']]) })],
  ['metadata minus zero', handTransfer({ metadata: metadataArchive([['a', '-0']]) })],
  ['metadata invalid json', handTransfer({ metadata: metadataArchive([['a', '{']]) })],
  ['metadata legacy key', handTransfer({ metadata: metadataArchive([['gas_limit', '1']]) })],
  ['metadata bad key', handTransfer({ metadata: metadataArchive([['a b', '1']]) })],
  ['metadata nested bad key', handTransfer({ metadata: metadataArchive([['a', '{"b@c":1}']]) })],
  ['metadata escaped solidus', handTransfer({ metadata: metadataArchive([['a', '"x\\/y"']]) })],
  ['metadata unicode escape', handTransfer({ metadata: metadataArchive([['a', '"\\u00e9"']]) })],
  ['metadata empty vector', handTransfer({ metadata: enc.vector([]) })],
  ['quantity trailing zero', handTransfer({ quantity: enc.numeric(mantissa(150n), 2) })],
  ['quantity zero', handTransfer({ quantity: enc.numeric(Uint8Array.of(0), 0) })],
  ['quantity empty mantissa', handTransfer({ quantity: enc.numeric(new Uint8Array(0), 0) })],
  ['quantity negative', handTransfer({ quantity: enc.numeric(Uint8Array.of(0xff), 0) })],
  ['quantity scale 29', handTransfer({ quantity: enc.numeric(mantissa(1n), 29) })],
  ['quantity non-minimal', handTransfer({ quantity: enc.numeric(Uint8Array.of(15, 0), 1) })],
  ['two instructions', handTransfer({ instructionCount: 2 })],
  ['no instructions', handTransfer({ instructionCount: 0 })],
  ['executable variant 1', handTransfer({ executableVariant: 1 })],
  ['other wire id', handTransfer({ wireId: 'iroha.instruction.v1::mint' })],
  ['transfer variant 1', handTransfer({ transferVariant: 1 })],
  ['owner is not authority', handTransfer({ owner: enc.account(OTHER) })],
  ['wrong schema hash', handTransfer({ schema: new Uint8Array(16).fill(1) })],
  ['bad crc', handTransfer({ crc: 5n })],
  ['flags zero', handTransfer({ flags: 0 })],
  ['controller tag 1', handTransfer({ authority: enc.account(ACCOUNT, 1) })],
  ['algorithm tag 1', handTransfer({ authority: enc.account(ACCOUNT, 0, 1) })],
  ['fee payer 2', handTransfer({ fee: enc.cat(enc.u32(2), enc.field(enc.struct([enc.vector([]), Uint8Array.of(0)]))) })],
  ['fee charge kind 2', handTransfer({ fee: defaultFee(null, enc.vector([enc.struct([enc.u32(2), enc.definition(XOR), enc.numeric(mantissa(25n), 2)])])) })],
  ['fee limits out of order', handTransfer({ fee: defaultFee(null, enc.vector([enc.struct([enc.u32(1), enc.definition(XOR), enc.numeric(mantissa(1n), 0)]), enc.struct([enc.u32(0), enc.definition(XOR), enc.numeric(mantissa(1n), 0)])])) })],
  ['fee three limits', handTransfer({ fee: defaultFee(null, enc.vector([0, 1, 1].map((kind) => enc.struct([enc.u32(kind), enc.definition(XOR), enc.numeric(mantissa(1n), 0)])))) })],
  ['fee gas zero', handTransfer({ fee: defaultFee(0n) })],
  ['fee sponsor revision zero', handTransfer({ fee: enc.cat(enc.u32(1), enc.field(enc.struct([enc.struct([enc.account(SPONSOR), enc.str('prog')]), enc.u64(0n), enc.vector([]), Uint8Array.of(0)]))) })],
  ['fee sponsor bad name', handTransfer({ fee: enc.cat(enc.u32(1), enc.field(enc.struct([enc.struct([enc.account(SPONSOR), enc.str('pro#g')]), enc.u64(1n), enc.vector([]), Uint8Array.of(0)]))) })],
  ['non-canonical length', (() => { const p = handTransfer(); return enc.cat(Uint8Array.of(0x80 | (p[0] & 0x7f), 0x00), p.subarray(1)); })()],
  ['hand batch', handBatch([handCall()])],
  ['batch without gas', handBatch([handCall()], defaultFee(null))],
  ['batch bad contract address', handBatch([handCall('irohac1qyqqqqqqqqqqqq95fes93ygegsv5enq9mqsz6x4lv4vp9gg4yxgjx')])],
  ['batch uppercase contract address', handBatch([handCall(CONTRACT.toUpperCase())])],
  ['batch unmarked code hash', handBatch([handCall(CONTRACT, unhex(`${'ab'.repeat(31)}a0`))])],
  ['batch short code hash', handBatch([handCall(CONTRACT, unhex('ab'.repeat(31)))])],
  ['batch control entrypoint', handBatch([handCall(CONTRACT, undefined, 'place\u0001order')])],
  ['batch padded entrypoint', handBatch([handCall(CONTRACT, undefined, ' place')])],
  ['batch instruction entry', handBatch([handCall(CONTRACT, undefined, 'x', null, 0)])],
  ['batch empty', handBatch([])],
];
out.reviews = cases.map(([name, payload]) => {
  const expectation = { networkId: TAIRA.networkId!, networkPrefix: TAIRA.networkPrefix, authority: ACCOUNT, signingPublicKeyHex: derived.publicKeyHex };
  try {
    const reviewed = reviewIrohaConnectTransaction(payload.slice(), expectation);
    return { name, payloadHex: hex(payload), review: reviewed.review, payloadHashHex: hex(reviewed.payloadHash) };
  } catch (error) {
    return { name, payloadHex: hex(payload), error: error instanceof IrohaConnectTransactionError ? error.code : `other:${(error as Error).message}` };
  }
});
if (hex(handTransfer()) !== hex(basic)) throw new Error('the hand encoder no longer matches the SDK');

// ---------------------------------------------------------------------------------------------------------------
// Session transcripts: the upstream app SDK against the browser wallet, every frame in order

type Event = { type: string; [key: string]: unknown };
const runTranscript = async (name: string, permissions: unknown, script: (h: Harness) => Promise<void>) => {
  const log: Event[] = [];
  const timers: Array<{ id: number; delay: number; callback: () => void; cleared: boolean }> = [];
  let walletSocket: any;
  let appSocket: any;
  const pendingForWallet: Uint8Array[] = [];
  const deliverToWallet = (bytes: Uint8Array) => {
    log.push({ type: 'appFrame', hex: hex(bytes) });
    walletSocket.onmessage?.({ data: bytes.slice().buffer });
  };
  class AppSocket {
    binaryType = 'arraybuffer';
    readyState = 0;
    listeners = new Map<string, Array<(event: unknown) => void>>();
    constructor(readonly url: string, readonly protocols: string[]) {
      appSocket = this;
      queueMicrotask(() => { this.readyState = 1; this.dispatch('open', {}); });
    }
    addEventListener(n: string, l: (event: unknown) => void) { this.listeners.set(n, [...(this.listeners.get(n) ?? []), l]); }
    removeEventListener() {}
    dispatch(n: string, e: unknown) { for (const l of this.listeners.get(n) ?? []) l(e); }
    send(data: Uint8Array) {
      const bytes = new Uint8Array(data);
      if (!walletSocket) pendingForWallet.push(bytes);
      else queueMicrotask(() => deliverToWallet(bytes));
    }
    close() { if (this.readyState === 3) return; this.readyState = 3; this.dispatch('close', {}); }
  }
  const settle = async () => { for (let i = 0; i < 200; i += 1) await Promise.resolve(); await new Promise((r) => setImmediate(r)); for (let i = 0; i < 200; i += 1) await Promise.resolve(); };
  const storedAccount = { address: 'stored', meta: { irohaPublicKeyHex: derived.publicKeyHex, name: 'Sakura', walletEcosystem: 'iroha' } };
  const keyringService = {
    accountSubject: { value: { primary: { json: storedAccount, type: 'ed25519' } } },
    exportMnemonic: () => ({ seed: MNEMONIC }),
    getAccounts: () => [storedAccount],
    keyringIsLocked: false,
  };
  let requestCounter = 0;
  let clock = 1_795_000_000_000;
  const service = new IrohaConnectService({ keyringService } as never, {
    createSocket: (url: string, protocols: string[]) => {
      log.push({ type: 'openSocket', url, protocol: protocols[0] });
      walletSocket = {
        binaryType: '', readyState: 1, onclose: null, onerror: null, onmessage: null, onopen: null,
        close: () => { log.push({ type: 'walletClosedSocket' }); walletSocket.readyState = 3; },
        send: (data: Uint8Array | string) => {
          if (typeof data === 'string') throw new Error('text frame');
          const bytes = new Uint8Array(data);
          log.push({ type: 'walletFrame', hex: hex(bytes) });
          queueMicrotask(() => appSocket.dispatch('message', { data: bytes.slice().buffer }));
        },
      };
      queueMicrotask(() => { for (const frame of pendingForWallet.splice(0)) deliverToWallet(frame); });
      return walletSocket;
    },
    makeRequestId: () => `request-${(requestCounter += 1)}`,
    makeWalletKeyPair: () => ({ privateKey: WALLET_PRIVATE.slice(), publicKey: WALLET_PUBLIC.slice() }),
    now: () => clock,
    setTimer: (callback: () => void, delay: number) => { const timer = { id: timers.length + 1, delay, callback, cleared: false }; timers.push(timer); return timer.id as never; },
    clearTimer: (id: unknown) => { const timer = timers.find((t) => t.id === id); if (timer) timer.cleared = true; },
  });
  const snapshot = () => {
    const s = service.snapshot;
    log.push({ type: 'snapshot', phase: s.phase, notice: s.notice?.code ?? null, error: s.error ?? null, selectedAccountId: s.selectedAccountId ?? null, session: s.session ? { appName: s.session.appName, appUrl: s.session.appUrl ?? null, permissions: s.session.permissions } : null, accounts: s.accounts.map((a) => a.address), review: s.request?.review ?? null, requestId: s.request?.requestId ?? null });
  };
  const preview = createConnectSessionPreview({ networkId: NETWORK, node: TAIRA.toriiBaseUrl, appKeyPair: { privateKey: APP_PRIVATE, publicKey: APP_PUBLIC }, nonce: NONCE });
  const app = createConnectAppSession({
    baseUrl: TAIRA.toriiBaseUrl, preview, session: { sid: preview.sidBase64Url, token_app: TOKEN_APP, token_relay: TOKEN_RELAY },
    webSocketImpl: AppSocket as never, appMeta: { name: 'Hanami Market', url: 'https://hanami.example/market' }, permissions,
  } as never);
  const approval = app.waitForApproval();
  approval.catch(() => undefined);
  const h = {
    app, approval, service, settle, snapshot, log, timers,
    tick: (ms: number) => { clock += ms; },
    action: async (nameOfAction: string, arg: unknown, run: () => unknown) => {
      log.push({ type: 'action', name: nameOfAction, arg });
      try { await run(); } catch (error) { log.push({ type: 'actionError', message: (error as Error).message }); }
      await settle();
      snapshot();
    },
    fireKeepAlive: async () => {
      const timer = [...timers].reverse().find((t) => !t.cleared && t.delay === 20_000);
      if (!timer) throw new Error('no keepalive timer');
      timer.cleared = true;
      log.push({ type: 'keepAlive' });
      timer.callback();
      await settle();
    },
    uri: `iroha://connect?${linkFields()}`,
  };
  await settle();
  await script(h as never);
  return { name, permissions, sidHex: hex(SID), walletPrivateHex: hex(WALLET_PRIVATE), uri: h.uri, events: log };
};
type Harness = Awaited<ReturnType<typeof runTranscript>> extends never ? never : any;

const fullScript = async (h: any) => {
  await h.action('connect', h.uri, () => h.service.connect(h.uri));
  await h.action('approveSession', ACCOUNT, () => h.service.approveSession(ACCOUNT));
  const approved = await h.approval;
  h.log.push({ type: 'appApproved', accountId: approved.accountId });

  const transfer = h.app.signTransaction(sdkTransfer());
  await h.settle();
  h.snapshot();
  h.tick(1000);
  await h.action('approveRequest', 'request-1', () => h.service.approveRequest('request-1'));
  h.log.push({ type: 'appSignature', hex: hex(await transfer) });

  await h.fireKeepAlive();
  h.snapshot();

  const call = h.app.signTransaction(sdkBatch([contractCall()]));
  call.catch(() => undefined);
  await h.settle();
  h.snapshot();
  await h.action('rejectRequest', 'request-2', () => h.service.rejectRequest('request-2'));
  h.log.push({ type: 'appRejected', code: await call.then(() => null, (error: any) => error?.code ?? String(error)) });

  const raw = h.app.signRaw('iroha:torii:canonical-request:v1', Uint8Array.of(1, 2, 3));
  raw.catch(() => undefined);
  await h.settle();
  h.snapshot();
  h.log.push({ type: 'appRejected', code: await raw.then(() => null, (error: any) => error?.code ?? String(error)) });

  const foreign = h.app.signTransaction(new Uint8Array(buildBrowserTransferPayload({ networkId: NETWORK, networkPrefix: TAIRA.networkPrefix, authority: OTHER, sourceAssetHoldingId: `${XOR}#${OTHER}`, quantity: '1', destinationAccountId: ACCOUNT, feePayment: sdkFee(), creationTimeMs: 1, ttlMs: 100_000 } as never)));
  foreign.catch(() => undefined);
  await h.settle();
  h.snapshot();
  h.log.push({ type: 'appRejected', code: await foreign.then(() => null, (error: any) => error?.code ?? String(error)) });

  const unsupported = h.app.signTransaction(handTransfer({ wireId: 'iroha.instruction.v1::mint' }));
  unsupported.catch(() => undefined);
  await h.settle();
  h.snapshot();
  h.log.push({ type: 'appRejected', code: await unsupported.then(() => null, (error: any) => error?.code ?? String(error)) });

  h.log.push({ type: 'appClose' });
  h.app.close('transcript done');
  await h.settle();
  h.snapshot();
};

const transcripts = [
  await runTranscript('full session', { methods: ['sign_transaction', 'sign_raw'], events: [], resources: ['iroha:torii:canonical-request:v1'] }, fullScript),
  await runTranscript('reject session', { methods: ['sign_transaction'], events: [] }, async (h: any) => {
    await h.action('connect', h.uri, () => h.service.connect(h.uri));
    await h.action('rejectSession', null, () => h.service.rejectSession());
  }),
  await runTranscript('wallet disconnects', null, async (h: any) => {
    await h.action('connect', h.uri, () => h.service.connect(h.uri));
    await h.action('approveSession', ACCOUNT, () => h.service.approveSession(ACCOUNT));
    await h.approval;
    await h.action('disconnect', null, () => h.service.disconnect());
  }),
  await runTranscript('permission denied', { methods: ['sign_raw'], events: [] }, async (h: any) => {
    await h.action('connect', h.uri, () => h.service.connect(h.uri));
    await h.action('approveSession', ACCOUNT, () => h.service.approveSession(ACCOUNT));
    await h.approval;
    const transfer = h.app.signTransaction(sdkTransfer());
    transfer.catch(() => undefined);
    await h.settle();
    h.snapshot();
    h.log.push({ type: 'appRejected', code: await transfer.then(() => null, (error: any) => error?.code ?? String(error)) });
  }),
];
out.transcripts = transcripts;

writeFileSync(process.argv[2], `${JSON.stringify(out, (_, value) => (typeof value === 'bigint' ? value.toString() : value), 2)}\n`);
console.log('wrote', process.argv[2], 'links', (out.links as unknown[]).length, 'reviews', (out.reviews as unknown[]).length, 'transcripts', transcripts.map((t) => `${t.name}:${t.events.length}`).join(', '));
