# Changelog

All notable changes to this project will be documented in this file.

## 4.3.1

- Staking: Polkadot and Kusama staking returns on their Asset Hubs, where the
  stake moved: bond, nominate, bond more, unbond, rebond, withdraw, reward
  destination, paged payouts, slash warnings and nomination pools. The
  unbonding period follows the account (fast unbonding for nominators the
  runtime does not slash), and estimated rewards come from the eras the Hub
  actually paid. Payouts are offered from the Manage menu on the Hubs, only
  from validators that are still bonded. A DOT or KSM choice saved before the upgrade opens its Hub when the
  wallet uses the same account on the relay and the Hub.
- Staking: enabled by the catalogue value `"staking": "assetHub"` on the Hub
  native assets, which 4.3.0 ignores; relays never offer staking again.
- Signing: every signed extension is checked against the runtime's portable
  metadata; an unknown one is accepted only when it takes no bytes.
- Validators: the validator search keeps its catalogue and selection bound to
  the network and account it was opened for, and recovers after a failed load.
  The validator choice screens offer a retry when the validator list cannot be
  loaded instead of closing the app, Redeem waits for a successful ledger
  refresh instead of closing the app when it fails, and the staking balance
  screen offers a retry when its unbonding list cannot be read. A Hub active
  era that cannot be read no longer closes the staking screens, and the
  Current Validators list offers a retry when it cannot be read.
- Accounts: a network-account JSON export reads the wallet once, and an
  Ethereum export never carries the Substrate seed.

## 4.3.0

- Networks: remove 40 networks that stopped producing blocks or have no
  reachable node (among them Moonbeam, Moonriver, Polygon zkEVM, Polkadex,
  KILT, Phala, Centrifuge, Interlay and both SORA parachains) and every
  disabled catalog entry. Wallet rows for a removed network stay on the device.
- Staking: remove relay-chain staking, nomination pools and crowdloans from
  Polkadot, Kusama and Westend, which hold no staking state since the Asset
  Hub migration. Legacy crowdloan recovery remains.
- Code: remove the unreachable crowdloan contribution screens and partner
  integrations.
- History: drop indexers that no longer answer; those networks show history
  as unsupported.
- Nodes: drop dead and misconfigured nodes, fail over from an unreachable
  Substrate node while Auto select is on, and follow Dwellir's host move.
- Security: port the build 231 signing, secret-screen, backup-exclusion and
  dependency fixes, harden WalletConnect signing and cross-chain
  configuration, and add remote kill switches for dApp signing.
- Sends: bind a send to the reviewed account before signing, and never report
  a transfer the network accepted as failed, so it cannot be sent twice.
- Ethereum: tip the node's priority fee in EIP-1559 transfers instead of paying
  the whole fee cap, and estimate the fee for the amount being sent.
- Backups: encrypt Google backups with the confirmed password, back up the
  wallet and derivation paths the user reviewed, and keep a wallet marked
  unbacked when only an added account's phrase was confirmed.
- WalletConnect: check the dApp-signing pause again after every wait.
- Staking: show the network chooser when the saved choice is no longer
  offered, and compute payouts in exact integer arithmetic.
- Dependencies: Guava 33.7.2 (CVE-2026-102554).

## 4.2.0

- Wallet safety: replace destructive database fallback with a complete,
  fail-closed Room migration path and bounded recovery for incompatible legacy
  wallet records.
- Startup recovery: distinguish retryable secure-storage failures, permanent
  key loss, database failures, and restart-required state before PIN, export,
  signing, or the wallet UI can open.
- Lifecycle stability: discard incompatible saved fragment state at the wallet
  gate and restore security/export warnings through FragmentManager-safe
  constructors.
- WalletConnect stability: defer Reown delegate registration until both clients
  are ready, retry partial initialization safely, and keep startup, session
  lookups, pairing, and actions nonfatal when the optional SDK is unavailable.
- Release safety: add an isolated, source-bound Internal App Sharing candidate
  lane with zero-copy Firebase input, external one-run signing, and adversarial
  artifact verification.
- Transfers: reject Bitcoin and Solana amounts outside the signed 64-bit range
  before any network or broadcast call.

## 4.2.0‑beta.1

- TON: public features available (send/receive, details, explorers)
- Providers: Coinbase provider added; new price service; remote asset sync service
- WalletConnect: migrate integrations to Reown SDK
- Platform: Android toolchain upgrades to meet Play requirements
- CI/CD: NDK r28; native libs verification; Polkadot alignment printing; Jenkins stability
- Fixes: banner closing (FLW‑5177); Ethereum recipient validation; confirmation/warnings UX; UI tweaks
