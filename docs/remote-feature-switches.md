# Remote feature switches (4.2.0)

Builds from this repository read `remote-config/production/appConfigs/feature_toggle` on its
`master` branch (`FEATURE_TOGGLE_URL` in `common/build.gradle`, debug and release alike). For
fund-moving features and dApp signing the remote file is **veto-only**: it can turn a shipped
feature off, and it can never turn on anything the build ships off.

## Semantics

| Key | Shipped default | Remote `false` | Remote `true` | Key absent / fetch fails |
|---|---|---|---|---|
| `polkaswap_mutations_enabled` (swap, liquidity) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `demeter_mutations_enabled` (farming) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `bitcoin_send_enabled` | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `solana_send_enabled` | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `polkadot_dapp_signing_enabled` (WalletConnect `polkadot_signTransaction`, `polkadot_signMessage`) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `evm_dapp_signing_enabled` (WalletConnect `eth_sendTransaction`, `eth_signTransaction`, `eth_sign`, `personal_sign`, `eth_signTypedData`, `eth_signTypedData_v4`) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `ton_dapp_signing_enabled` (TON Connect `sendTransaction` and `ton_proof`, QR connections and the in-app browser) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `xcm_mutations_enabled`, `polkamarkt_mutations_enabled`, `polkaswap_bridge_mutations_enabled` | off | ignored | ignored | ignored |

- A veto is stored only after a successful fetch and survives restarts, offline launches and
  later fetches that omit the key. Only an explicit `true` clears it.
- XCM, Polkamarkt and the Polkaswap bridge additionally need signed `mutation_authorization` and
  the compiled XCM switch, which is off in release builds. XCM stays paused in 4.2.0 with an in-app
  explanation and a same-network send alternative.
- The config is applied at app start and the vetoes are re-read every five minutes
  (`MutationAuthorizationRefresher`), so an emergency veto reaches running apps quickly.
- Gson ignores unknown keys, so these keys are safe for every older Android build. Builds without
  the dApp signing keys keep signing whatever the file says.
- Publish JSON booleans only. Gson reads a string other than `"true"` as `false` (a veto) and any
  other type fails the whole fetch, which leaves every switch at its remembered state;
  `scripts/verify-owned-remote-config.py` refuses a `*_enabled` value that is not `true` or `false`.
- The legacy `dapp_enabled` key in the production file is unrelated: it never pauses dApp signing.
- Store: `common/.../data/network/config/ProductFeatureToggleStore.kt`. Persisted keys:
  `feature.polkaswap_mutations.v2`, `feature.demeter_mutations.v2`, `feature.bitcoin_send.v1`,
  `feature.solana_send.v1`, `feature.polkadot_dapp_signing.v1`, `feature.evm_dapp_signing.v1`,
  `feature.ton_dapp_signing.v1`. The `.v1` Polkaswap/Demeter keys from release candidates stored
  `remote == true` and are ignored, so a candidate build's stored `false` is not read as a veto.

## What users see under a veto

- Swap, liquidity and farming: the Earn/Polkaswap hub explains the pause; the asset Swap action
  opens a "Swaps paused" sheet. Balances and positions stay visible.
- Bitcoin or Solana send: the Send action (asset detail and swipe) opens a "Sending paused" sheet
  with Receive as the next step. The transfer service refuses fees and submission for the vetoed
  network before any indexer, RPC or signing call (`PausedTransferService`). Entry points without
  their own check (QR scan, search, repeating a transfer, switching the asset inside Send) reach
  the Send screen, which shows the same sheet instead of a fee error and closes on either choice.
  Receive and balances are unaffected.
- dApp signing (WalletConnect): requests of a paused family still open, show what they ask, and
  are headed by "Fearless has paused dApp signing on this network for your safety"; Sign stays
  disabled and closing rejects the request. The interactor refuses before any key is read and
  answers the dApp with a JSON-RPC error, also for a request that arrives or is retried after the
  pause: an `eth_sendTransaction` signed before the pause is never broadcast. Session proposals and
  existing connections are unaffected.
- dApp signing (TON Connect): a `sendTransaction` request opens with a "dApp signing paused"
  dialog whose Close declines it, and a connection that asks for `ton_proof` does the same; the
  dApp gets an error. Both screens check again right before signing. A connection without
  `ton_proof` signs nothing and still connects.
- Without a veto none of this copy appears: Bitcoin and Solana sends and dApp signing simply work.

## Payloads

Production payload for 4.2.0 (no change needed; every shipped feature stays on):

```json
{ "pendulum_case_enabled": false, "nft_enabled": false, "dapp_enabled": false }
```

Emergency veto template (publish only the keys you need to turn off; do not publish preemptively):

```json
{ "pendulum_case_enabled": false, "nft_enabled": false, "dapp_enabled": false,
  "bitcoin_send_enabled": false, "solana_send_enabled": false,
  "polkaswap_mutations_enabled": false, "demeter_mutations_enabled": false,
  "polkadot_dapp_signing_enabled": false, "evm_dapp_signing_enabled": false,
  "ton_dapp_signing_enabled": false }
```

To lift a veto, publish the same key with `true`; removing the key keeps the veto on devices that
already saw it. Any edit of the file also updates its `ownedSha256` in
`config/remote-config-imports.json` and adds a `changes` line (`verify-owned-remote-config.py`).
The iOS app reads its own `feature_toggle` in `fearless-iOS` and the extension reads
`web_config.json` `actions.*`; a veto for them is published there.

## Tests

`FeatureContinuityTest` replays upgrade paths (4.2.0, release-candidate testers holding `.v1`
values, offline first launch, veto then failed refreshes) against
`common/src/test/resources/remote-config/feature_toggle.production.json`, a byte-for-byte copy of
the production file. Refresh that copy whenever the production file changes.

```
./gradlew :common:testDebugUnitTest --tests '*ProductFeatureToggleStoreTest*' --tests '*MutationAuthorizationRefresherTest*' --tests '*FeatureContinuityTest*'
./gradlew :feature-walletconnect-impl:testDebugUnitTest --tests '*DappSigningPause*' --tests '*WalletConnectInteractorImplTest*' --tests '*RequestPreviewViewModelTest*' --tests '*WalletConnectSummaryPresenterTest*'
./gradlew :feature-tonconnect-impl:testDebugUnitTest --tests '*TonSignRequestPortfolioAdoptionTest*' --tests '*TonConnectionProofPauseTest*'
python3 scripts/test-owned-remote-config.py
./gradlew :feature-wallet-impl:testDebugUnitTest --tests '*SendPause*' --tests '*SendSetupPausedSend*' --tests '*BalanceDetail*' --tests '*CrossChainPausedReason*'
./gradlew :core-xcm:testDebugUnitTest --tests '*XcmServiceTest*'
```
