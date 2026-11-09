# Changelog

All notable changes to this project will be documented in this file.

## 4.3.3 (235)

- Design: the sun-lit redesign. The approved winged-sun badge is the launcher icon, the
  in-app mark, the default wallet avatar and the QR centre mark; full-page screens (PIN,
  welcome, onboarding, About, launch art) show the wings at about 90% of the page width.
  Cards, buttons, input wells, sheets and the bottom bar are lit from one key light.
- Wallet home: the badge is the sun of a new hero, its ring follows the real asset loading,
  the total rolls when it changes, and Send, Receive, Buy and Swap are round actions; Receive
  and Buy pick an asset first. The whole home scrolls as one page at every text size.
- PIN: the keypad is raised, dots fill from the lit side, a wrong PIN shakes twice, and the
  last keypad row stays reachable at 320 dp and 200% text.
- History rows, asset details, Receive, Send and Settings follow the new surfaces. Signing and
  recovery-phrase sheets animate nothing beyond press feedback.
- Launch: the black flash between the system splash and the wallet is gone (night mode is set
  before any Activity exists), and the launch art no longer hops when the wallet takes over.
- Pages run to the bottom edge behind a transparent navigation bar and fade out above the
  bottom bar instead of ending in a straight line.
- Fixed: money shows its currency symbol again ($0.09, not usd0.09).
- Fixed: Settings > Manage assets crashed when scrolled, and the home showed one token of
  a pair that shares a currency id (Acala's tDOT and GLMR, Karura's RMRK and taiKSM,
  Bifrost's vKSM and vsKSM). Each token now has its own row.
- Fixed: Start staking for a pool crashed on every open.
- Fixed: system Back in the liquidity pools and NFT sheets goes back a page instead of
  sticking on Loading.
- Fixed: closing the success sheet after a WalletConnect signature no longer shows the
  answered request again, and a message signature says "Message signed".
- Fixed: a new wallet no longer says "No assets were found" while balances it counts are
  still unread.
- Wallet avatars are whole and centred, and sheets are opaque with rounded tops.
- Presentation only otherwise: keys, the PIN envelope, signing, storage, networking, remote
  configuration and chain logic are the code of 4.3.2. The database schema stays 84.

## 4.3.2 (234)

- Security: biometric unlock is tied to the PIN. The PIN is sealed to an Android
  Keystore key that only a strong biometric opens and that a newly enrolled
  fingerprint or face destroys; exports, recovery and PIN changes take the PIN
  itself. After a biometric change, biometric unlock turns off until the next
  PIN entry offers it again.
- Security: the PIN is part of the encryption of every recovery phrase, seed,
  private key and keystore JSON. Existing wallets are sealed after the first PIN
  entry on 4.3.2. "Forgot PIN?" restores access with any one wallet backup.
- Security: a locked wallet signs and approves nothing. WalletConnect and TON
  Connect requests wait for the PIN and open only once the wallet screens are up;
  a request held while the app was closed no longer crashes it at start.
- Security: Polkadot transactions are signed with the network's metadata hash
  where the network checks it, so a node serving false metadata cannot change the
  call that is signed; dApp transactions that name a metadata hash are signed only
  for the network's own hash. Remote configuration is used only when an
  Ed25519-signed manifest lists it.
- Security: copied secrets are marked sensitive and cleared after a minute;
  retained wallet data is no longer shared to other apps as plain text. Secret
  screens ignore touches through other apps' windows, hide their overlays and, on
  Android 14+, are hidden from accessibility services that are not screen
  readers. Derivation paths and import fields stay out of keyboard learning.
- Backup: backing up an existing wallet to Google Drive asks for the PIN, and new
  backup passwords must be strong (12+ characters of three kinds, or 16+). Old
  backups open with their old passwords.
- Open source only: WalletConnect v2 (with Verify), Google Drive sign-in, TON keys
  and the sr25519 library are built in this repository. The Reown SDK, Google Play
  services, Google sign-in, Play Integrity, Firebase, SpongyCastle, EdDSA-Java,
  curve25519-kotlin, libsodium and the prebuilt polkaj jar are gone: the release
  has 308 libraries instead of 441, none older than in 4.3.1.
- Fix: jetton transfers carry a random query_id instead of 0.
- Fix: closing a network connection can no longer close the app when its last
  request finishes at the same moment.
- Fix: opening Export wallet no longer jumps to a Google sign-in in the browser or
  forgets the Drive sign-in; "Backup to Google" signs in when tapped. Bitcoin, Solana
  and Iroha accounts, which the wallet's phrase restores, no longer raise the
  "replaced key pair" warning. Checking a wallet's secrets for these screens no
  longer runs on the main thread, where it could freeze the app.
- Upgrade: keys are unchanged; dApps connected with 4.3.1 stay connected; Google
  Drive needs one new sign-in in the browser.

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
  Current Validators and pool Selected Validators lists offer a retry when
  they cannot be read. A minimum stake that cannot be read yet is shown as
  loading or offers a retry instead of closing the app.
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
