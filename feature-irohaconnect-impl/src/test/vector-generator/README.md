# IrohaConnect cross-check vectors

`src/test/resources/irohaconnect/web-vectors.json` is generated, not hand-written. `generate.sh` runs the browser
wallet's own Iroha Connect code (fearless-wallet-web PR #1122, `feature/iroha-connect-current` @6f1fc6b5:
`wire.ts`, `transactionReview.ts`, `index.ts`) together with the iroha-js 0.0.3 app SDK and transaction codec, and
records:

- links (accepted with every parsed field, or refused with the browser wallet's error code);
- approval preimages, approve/reject/ping/pong frames, direction keys and sealed envelopes;
- 72 transaction payloads (SDK-built, plus hand-encoded variants the SDK builders refuse) with the browser review or
  refusal code;
- four complete sessions between the upstream app SDK and the browser wallet, every frame in order.

The Android tests must agree byte for byte. `stubs/` replaces only the extension plumbing the service imports
(keyring, account listing, build flags); the protocol, review and key derivation code run unmodified.
`hooks.mjs` maps the web's path aliases, strips TypeScript with esbuild, and swaps the SDK's native binding for the
wallet's browser binding exactly as the web build does.

```
WEB=~/dev/fearless/fearless-wallet-web bash generate.sh
```

## Live Taira pairing

`live.sh` runs `live-taira-dapp.ts`: the upstream app SDK registers a session with https://taira.sora.org, launches the
widget's same-device `irohaconnect://connect?…` link on an emulator with the debug build, waits for the person to
approve, asks for a transfer and a contract-call signature (each verified against its payload hash), waits 45 s for
the wallet's Connect pings, asks for a raw signature (expects `SIGNING_KEY_REQUIRED`) and closes the session.

```
ADB=~/Library/Android/sdk/platform-tools/adb WEB=~/dev/fearless/fearless-wallet-web bash live.sh evidence.json emulator-5996
```
