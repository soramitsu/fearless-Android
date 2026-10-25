# Remote feature switches (4.2.0)

The app reads `appConfigs/feature_toggle` from `shared-features-utils` (`FEATURE_TOGGLE_URL`).
For fund-moving features the remote file is **veto-only**: it can turn a shipped feature off, and
it can never turn on anything the build ships off.

## Semantics

| Key | Shipped default | Remote `false` | Remote `true` | Key absent / fetch fails |
|---|---|---|---|---|
| `polkaswap_mutations_enabled` (swap, liquidity) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `demeter_mutations_enabled` (farming) | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `bitcoin_send_enabled` | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `solana_send_enabled` | on | off, remembered | clears a remembered veto | keeps the remembered state |
| `xcm_mutations_enabled`, `polkamarkt_mutations_enabled`, `polkaswap_bridge_mutations_enabled` | off | ignored | ignored | ignored |

- A veto is stored only after a successful fetch and survives restarts, offline launches and
  later fetches that omit the key. Only an explicit `true` clears it.
- XCM, Polkamarkt and the Polkaswap bridge additionally need signed `mutation_authorization` and
  the compiled XCM switch, which is off in release builds. XCM stays paused in 4.2.0 with an in-app
  explanation and a same-network send alternative.
- The config is applied at app start and the vetoes are re-read every five minutes
  (`MutationAuthorizationRefresher`), so an emergency veto reaches running apps quickly.
- Gson ignores unknown keys, so these keys are safe for every older Android build.
- Store: `common/.../data/network/config/ProductFeatureToggleStore.kt`. Persisted keys:
  `feature.polkaswap_mutations.v2`, `feature.demeter_mutations.v2`, `feature.bitcoin_send.v1`,
  `feature.solana_send.v1`. The `.v1` Polkaswap/Demeter keys from release candidates stored
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
- Without a veto none of this copy appears: Bitcoin and Solana sends simply work.

## Payloads

Production payload for 4.2.0 (no change needed; every shipped feature stays on):

```json
{ "pendulum_case_enabled": false, "nft_enabled": false, "dapp_enabled": false }
```

Emergency veto template (publish only the keys you need to turn off; do not publish preemptively):

```json
{ "pendulum_case_enabled": false, "nft_enabled": false, "dapp_enabled": false,
  "bitcoin_send_enabled": false, "solana_send_enabled": false,
  "polkaswap_mutations_enabled": false, "demeter_mutations_enabled": false }
```

To lift a veto, publish the same key with `true`; removing the key keeps the veto on devices that
already saw it. iOS shares this file and these keys, and the extension uses `web_config.json`
`actions.*`; confirm both consumers decode the payload the same way before publishing.

## Tests

`FeatureContinuityTest` replays upgrade paths (4.2.0, release-candidate testers holding `.v1`
values, offline first launch, veto then failed refreshes) against
`common/src/test/resources/remote-config/feature_toggle.production.json`, a byte-for-byte copy of
the production file. Refresh that copy whenever the production file changes.

```
./gradlew :common:testDebugUnitTest --tests '*ProductFeatureToggleStoreTest*' --tests '*MutationAuthorizationRefresherTest*' --tests '*FeatureContinuityTest*'
./gradlew :feature-wallet-impl:testDebugUnitTest --tests '*SendPause*' --tests '*SendSetupPausedSend*' --tests '*BalanceDetail*' --tests '*CrossChainPausedReason*'
./gradlew :core-xcm:testDebugUnitTest --tests '*XcmServiceTest*'
```
