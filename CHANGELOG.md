# Changelog

All notable changes to this project will be documented in this file.

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
