# Roadmap & Technical Debt

Actionable, prioritized tasks phrased as clear prompts for developers. Each task includes goal, acceptance criteria, and suggested steps.

Priority: P0 (must-do), P1 (should-do), P2 (nice-to-have)

## Production qualification — 2026-09-22

- 2026-09-24 portable cohort projection: a pure Android `FPWMSM01`/`FPWCAI01` projection now pins allocated local IDs to logical wallet/chain/favorite/custody/metadata and exact V1/V2/V3/source-sidecar intents, with cross-wallet public-identity collision rejection. P0: define the reviewed Room position and backup-state migration policy for unsigned source positions and absent backup fields, derive and prove custody identity digests, map watch and original-source storage without loss, and build a cohort-wide transaction with reserved IDs and crash-safe replay. Prove installed original-key signing/export and iOS↔Android restoration before removing the installer blocker; this projection neither writes storage nor enables recovery.

- 2026-09-24 Kaia history candidate: the bundled mainnet/Kairos history configuration and legacy `KLAYTN` route now use chain-bound KaiaScan OAPI native/token endpoints, Bearer credentials, exact decimals and fail-closed pagination; token fees are not invented. P0: provision the Android KaiaScan key, prove native/token amount, fee and terminal receipt parity, fee delegation, multi-page freshness and retry/explorer recovery on both networks, and repeat on exact final signed builds. See [the provider gate](kaia-history-production-gate-20260924.md). The `9180bc535` unsigned AAB predates this source and cannot qualify it.

- 2026-09-24 history provider hardening: OKLink and legacy Klaytn no longer turn provider or transport failures into empty successful history, and the bundled X Layer explorer templates match their networks. Focused tests pass 7/7. P0: qualify the final-source history providers with operator-provisioned credentials, representative native/token transfers, pagination and freshness on X Layer and Kaia; replace any retired provider path through a reviewed contract. Keep provider failures visible and preserve retry/explorer recovery. The source-bound unsigned AAB at `bbd9f34f4fc137444ce01defe45d5ba57e5b766c` passed a 16-library/16 KiB structural audit, but final-head rebuild, actual 16 KiB device checks and Play-signed upgrade acceptance remain required.

- 2026-09-24 V2 chain proof: an unwired read-only verifier can prove exact Android V2 original SCALE bytes, the semantic chain key and recovery fields, and a local signature only under an explicit canonical genesis/identity-kind policy. P0: supply a reviewed immutable production genesis policy, qualify supported iOS chain originals, integrate this proof with all other wallet/source proofs and the transactional installer, and verify actual installed-key signing/export and cross-device restoration. The receive blocker and backup-completion denial remain in place.

- 2026-09-24 migration verifier repair: align the seven required released-schema identities and stale production-open instrumentation expectations with database version 78. Local verifier and evidence-packaging fixtures pass; P0: rerun protected hosted CI and confirm the API 34 full suite, all four API migration/restart profiles, and their source-bound evidence before candidate release.

- 2026-09-24 cohort journal staging: the internal versioned `FPWCJ001` encrypted preference record now retains and revalidates the exact multiwallet after-image across restart, rejects known occupied target namespaces and races at staging, and never publishes a partial wallet. P0: implement a complete installer whose durable transaction covers V1/V2/V3 and source sidecars, Room wallet/chain/favorite/metadata/selection rows and ID reservation; bind every installed key to its public identity and prove original-key signing/export. Add crash-point, rollback and exact replacement-device tests before removing the permanent installer blocker or enabling recovery.

- 2026-09-24 cohort after-image: the pure versioned `FPWCAI01` schema binds ordered local IDs to exact canonical portable bytes, names candidate V3/V2/V1 destinations, retains metadata/originals/selection and every blocker, and rejects duplicate or unrepresentable keys. Focused tests pass 6/6 with scoped Detekt. P0: complete cross-store mutation replay and installation using the verified after-image, with exact V1/V2/V3/sidecar namespace and Room image coverage, crash/rollback tests and original-key/export proof. The after-image alone has no storage call and cannot enable recovery.

- 2026-09-24 receiving-install plan: an unwired pure Android plan now retains exact `FPWMSM01` cohorts and marks V1/V2/auxiliary/watch/metadata and incomplete root proof as blockers; it never writes wallet storage. P0: add a replayable cohort-wide transaction journal covering all root, chain and original-source secrets plus Room wallet, chain, favorite, custody and selection rows; prove namespace collisions, readback, restart recovery and original-key signing/export for every slot before enabling installation. The existing single-wallet V3-root CREATE journal cannot serve as that installer.

- 2026-09-24 wallet-material capture: the Android candidate has an internal bounded draft serializer for validated V3 Substrate/EVM/TON roots, V2 chain keys and guarded V1-only sources across multiple wallets, with public identity, selection/order and favorites. V1 source type, original address, exact keypair, optional seed/path and mnemonic-derived entropy use a distinct slot; malformed or unowned active aliases, duplicate aliases and changing inventories fail closed without deleting the source. Watch-only cohorts still fail closed. P0: agree one semantic and binary iOS/Android plaintext contract, prove all historical cohorts, implement atomic replacement-device installation and verify original-key signing/export. This Android-only draft is not wired to Drive or backup completion and cannot make passkey recovery eligible by itself.

- 2026-09-23 wallet-material inventory: legacy mnemonic backup restoration now preserves a separately backed-up EVM private key in the same durable wallet creation. P0: agree the portable iOS/Android plaintext format and implement wallet-owned serialization/verification for all V3 Substrate/EVM/TON roots, V2 chain keys, historical V1 material and multiple wallet identities; prove locked/interrupted migration and original-key signing/export. See [the source inventory](portable-wallet-material-inventory.md). The public legacy remote-backup compatibility stub and disabled passkey generation code are not release acceptance.

- 2026-09-23 immutable-generation candidate: FPBKGEN1 canonical bytes and a separate append-only Drive store pass local vector/max-envelope/unknown-outcome tests. A bounded local journal candidate now records exact ciphertext and requires a durable single create-attempt marker through the public upload API. The disabled legacy single-file save path verifies exact Drive readback and local decryption before success, with credential compensation on registration failure. P0: qualify physical-device filesystem behavior, integrate downloaded DEK/envelope and wallet-identity verification into owner-authorized head CAS/status, reconcile competing writers and revocation, retain the last usable generation, and qualify Android↔iOS recovery on real devices before enabling. No automatic deletion or final-credential retirement is implemented.

- 2026-09-23 Drive identity candidate: retain stable Google UserInfo `sub` across email rename, bind every request to the verified selected subject, and preserve original envelope AAD/ciphertext. P0: qualify consent/UI and the same Google Cloud appData project on both platforms, migrate the deprecated token-fetch consent path to AuthorizationClient, integrate owner/lifecycle and generation checks, and collect real replacement-device evidence before enabling recovery.

- 2026-09-23 CI repair: retain the seven-checkout release architecture contract and per-checkout pin/credential negatives, require the liquidity-pools/Polkamarkt/Polkaswap mutation suites in `runTest`, keep the debt baseline equal to the current source markers, and preserve exact first-party audit boundaries around independently verified dependency checkouts. Complete the full Android CI run at the repaired source head, including required API 30/31/36 migration and restart evidence, before release qualification.

- Completed baseline: target API 36; retain reviewed XCM discovery/quotes with a hardcoded false release submission flag; reject direct engine transfer before entering signing/submission even when the remote switch enables mutations. Keep the 15-route approval set, 34 discovery-only routes and blocked production-evidence requirements intact.
- Completed fee isolation: preparing quotes does not read wallet secrets; origin-fee RPC uses a correctly typed invalid signature placeholder without invoking the wallet signer. The 121 scoped JVM tests include private-key exclusion and Substrate/Ethereum encoding fixtures. Verify fee/weight parity against each approved live runtime before release enablement.
- Completed authorization core: closed canonical Ed25519 FWMA1 verification, production package/version and policy/route-manifest binding, 900-second maximum lifetime, five-minute refresh, durable monotonic revision/digest and clock high-water, startup-denied state, and intent-bound process leases. Missing production trust and immutable all-false policy keep new Polkamarkt/XCM/bridge mutations denied; legacy behavior and read-only discovery are independent.
- Completed source integration: freeze SCALE bytes and bind chain/account/payload intent before key acquisition; check the same lease inside physical key reads, signing, and the Utils WebSocket handoff. New-feature discovery/quotes use public metadata or key-presence checks; guarded SocketService requests terminate without reconnect replay.
- Completed guarded source candidate: pass intent leases through physical unwrap/attestation/decryption, prepared signing primitives, Utils queues and the nv-websocket underlying output. Reject guarded compression before compressor state, finish owned blocking work before the final sample, and never replay uncertain mutations. Utils PR153 and transport PR1 are published source candidates; Android CI/release verifies their exact commits/trees without checkout mutation.
- P0: Review and merge those exact source candidates, pass complete Android CI/release checks from the final merged app tree, and qualify SR25519/native providers and real-device TLS before treating source boundary tests as artifact evidence.
- P0: Provision operator-reviewed production verification keys, exact artifact version/policy and full route/catalog manifest coverage only after release evidence is complete. Do not install fixture keys or relax compiled approval/per-route gates.
- P0: Complete the existing funded per-route XCM evidence and final reconciliation gates before changing compiled submission permission. Quote/discovery availability does not count as an executable-route qualification.
- P0: For the frozen Polkadot Asset Hub → Moonbeam USDt discovery gap, run
  `bash scripts/test-xcm-assethub-moonbeam-usdt-discovery.sh` and
  `node scripts/inspect-xcm-assethub-moonbeam-usdt.js --output build/reports/xcm-assethub-moonbeam-usdt-discovery.json`.
  Verify the source-derived asset identities against canonical chain state,
  then review the exact runtime call, XCM locations, beneficiary, weight and
  fee semantics. Keep the route discovery-only until funded origin/destination
  success evidence and separate approval are complete.
- P0: Qualify the exact source-bound target-36 release AAB, native 16 KiB alignment and actual 16 KiB-device startup/crypto behavior. Complete configured signer trust, immutable dependency/source publication, and the real Play-signed upgrade matrix on API 30, 31 and 36 from the final merged tree; preserve all legacy wallet identities, signing, backup and export behavior.

## Portfolio network headers — 2026-09-07

- Healthy headings now show the network name and available fiat subtotal. Routine scan coverage/timestamps, repeated account/ecosystem metadata, asset counts and missing-price placeholders are hidden; failed, outdated and unloaded balances retain short localized status messages. Collapse and detected-asset review are unchanged.
- Validation: five focused JVM status tests and the native 320dp/200% text header fixture pass; debug app/test APKs build successfully. Screenshot and exact artifact hashes are in `build/ux-evidence/20260907-portfolio-header/`.

## Completed TalkBack and large-text acceptance — 2026-09-06

- Official-source TalkBack and audible RHVoice output now support the isolated 5560 checks. The scoped real-wallet and synthetic production-view matrix passes; service availability is no longer an Android blocker.
- Preserve the verified raised center artwork/single accessible Polkaswap action, opaque bar and matching content inset, responsive wallet states, task-first setup/Back behavior, full options text and reusable dialog ownership. Targeted validation totals 41 JVM / 15 native passes; final app/test artifacts and reviewed screenshots/audio are in `build/ux-evidence/20260906-talkback/`.
- The evidence README records exact case/artifact scope and diagnostic history. Normal text size and disabled TalkBack are restored after acceptance, with the official service and four synthetic wallets retained for owner review. No release or account/crypto behavior was changed for accessibility tooling.

## Legacy upgrade acceptance — 2026-09-06

- Preserve all legacy keys/addresses and existing wallet access while adding supported network accounts; implementation and targeted checks are recorded in [legacy-upgrade-audit-20260906.md](legacy-upgrade-audit-20260906.md).
- Repeat the existing source-bound migration compatibility/release gate from the final merged release tree on API 30, 31 and 36 before distribution. The present working tree contains unrelated development changes and is not a releasable artifact.
- Seed-only, raw-key, watch-only and other wallets without a recoverable mnemonic retain their legacy accounts. Adding a mnemonic-based network requires explicit user import/create; automatic upgrade never invents or silently replaces their recovery phrase.

## UX validation follow-up
- 2026-09-06 Receive follow-up: the SORA Request share-asset mismatch and actual Request typography crash are fixed. Current display/QR/share snapshot, cancellation/stale-result handling, share retry and selector reopening have six passing regressions; Copy address/Share address use localized labels. Scoped totals are 37 JVM and 10 native passes; both APKs build successfully. Actual Receive passes 4/4 cases across two SORA wallets, TON and ETH/Ethereum on the recorded `c493...` APK. Request at 200% text, amount/shared-QR equality and actual CERES→DEO switching pass. The fixture-only host-sizing/full-bounds correction builds and its native rerun passes 3/3 in 5.706s; corrected captures were inspected and the earlier clipped images remain diagnostics. The final scoped crash buffer is empty. Existing Manage assets toggles and Search provide zero-balance Receive entry without changing the tested Portfolio policy. That checkpoint predates the completed official TalkBack checks above.
- Confirmation follow-up: returned/thrown create and backup errors now permit retry, cancellation releases the guard, and retry after partial completion reuses the saved wallet. Seven new JVM cases pass (31 scoped JVM total). Both debug APKs build successfully. The selected/source word panels share bounded scrolling, empty selection no longer preallocates the full phrase height, word targets are at least 48dp, and the exercised confirmation/export toolbar actions have named 48dp bounds. The new `MnemonicConfirmationLayoutInstrumentedTest` passes (9 scoped native total). The actual 24-word/200% text walkthrough and persisted backup status after restart also pass on the exact confirmation APK. Build-specific evidence is in `build/ux-evidence/20260905-real-onboarding/README.md`.
- 2026-09-05: Retain the raised central Polkaswap artwork and existing destination. The ordinary center menu item is hidden from accessibility so the labeled FAB is the single accessible action. Future contextual Polkaswap/Soraswap selection remains deferred; this change does not add a provider router.
- Center restoration: debug APK/test APK builds, 6 navigation JVM checks and 7 API 34 native layout/back-stack/screen checks pass. Production navigation fixtures at 320dp and 100%/200% text are saved under `build/ux-evidence/20260905-polkaswap/`; captions wrap without truncation. Production onboarding/Receive/Swap fixtures at the same widths/scales are saved under `build/ux-evidence/20260905-screens/ux-evidence/`; full text fit, scroll reachability and disclosure behavior pass. The later official TalkBack checks above complete that follow-up.
- 2026-09-05: Implemented task-first onboarding, persistent wallet protection, explicit receive-network instructions, accessible toolbar/swap controls, transaction-focused swap details and consistent task labels. Existing backend capability checks and authenticated navigation graph IDs are preserved.
- Actual Substrate/EVM and TON Create/Restore, invalid-phrase retry, Back, PIN unlock and saved-wallet switching have been exercised on an isolated API 34 emulator. Follow-up runtime testing found and fixed banner typography context, the TON header network identity and a TON initialization ordering deadlock; the final debug APK and three repeated TON cold starts pass; evidence is recorded in `build/ux-evidence/20260905-real-onboarding/README.md`. Spoken TalkBack remains required and unavailable in the QA image; cloud recovery is additional validation. The later full generated-word confirmation passed at 200% text. The generated TON empty-state copy and same-chain wallet address refresh are fixed, with four empty-state tests, three address-flow tests and real model/display switch checks. Final scoped totals: 24 JVM and 8 native passes.
- A future global Activity destination needs a real multi-network operation feed and pagination contract; the existing AddressHistory screen selects recipients and cannot serve that role.
- 6 focused onboarding/icon tests and common/onboarding/wallet/Polkaswap/staking debug compilation passed with the existing runtime-compatible fearless-utils checkout. App integration compilation passed. Finish device acceptance; see docs/status.md for exact current results. No production build is generated for this UX change.

## Recent Updates
- 2026-08-01: Added a URL-redacted, digest-bound contract for the observed
  Google Play Internal App Sharing publication, including a mode-`0600`
  private handoff writer and 6-positive / 184-adversarial audit. The evidence
  remains test-only and records its missing device, independent-tester,
  production-signing, and current-availability proof. Added a bounded AAB ELF
  verifier with 1 positive / 20 negative fixtures and wired exact CI, IAS,
  unsigned-release, and signed-release artifacts through its 16 KiB PT_LOAD
  alignment checks.
- 2026-07-30: Removed Reown delegate calls from `WCDelegate` static
  initialization after a delayed release-like startup crash. Delegate
  registration is now synchronized, idempotent, refreshes persisted sessions
  when initialization succeeds, and safely retries partial readiness.
  Recoverable synchronous SDK failures across pairing and session actions now
  reach UI error callbacks; cancellation and fatal errors still propagate.
  Stale proposal/session/request destinations route back without dereferencing
  missing process-memory state. Pull-request IAS builds stay
  WalletConnect-secretless, while a protected-`develop` manual dispatch binds
  a required non-logged 32-hex project ID only to the exact bundle step.
- 2026-07-27: Retired the legacy MoonPay secret/HMAC flow and removed every
  signing-secret BuildConfig field and release requirement. The replacement
  uses only publishable keys, an exact hosted-checkout allowlist, and manual
  wallet entry; prefilled-wallet URLs still require a backend signer. The
  formerly exposed secret must be revoked/rotated. The policy gate scans every
  tracked Android source set, build input, release script, and workflow, with
  alternate-module adversarial fixtures.
- 2026-07-27: Made release Firebase overlay backup creation crash-safe by
  validating size, SHA-256, and `0600` permissions on a same-directory
  temporary before atomic rename. Cleanup discards partial temporaries. The
  finalized split-overlay suite covers 20 restore and 8 cleanup TERM/SIGKILL
  cases, 2 independent positive phases, 20 phase/credential-isolation
  negatives, and separate corruption and permission-negative cases.
- 2026-07-27: Reserved Google Play version code 230 and replaced mutable
  release versioning/debug-signing fallback with a fail-closed Play testing
  artifact pipeline. A credential-free first job validates the signed tag,
  prior CI, governance, adversarial controls, and exact source tree. The
  protected build runner then creates only an exact CI/source-bound unsigned
  Gradle AAB with no signing material and transfers its exact, individually
  attested four-file evidence, including the bounded verified Gradle/R8 build
  log, to a separate protected signing runner. That
  runner revalidates every file digest, exact provenance, all attestations,
  tag, `master`, and source tree before restoring the upload key and invoking
  the standalone signer pinned to the registered upload certificate. It
  derives identity and permissions from the signed bytes and separately
  attests the exact final three-file artifact. CI performs no Play mutation
  and receives no Play service-account credential; the final AAB is uploaded
  manually to the existing Open Testing track after evidence review.
- 2026-07-27: Pinned release dependency provenance with the Gradle 9.0
  distribution SHA-256, strict verification metadata plus a tracked digest,
  the strict root buildscript-classpath lock, and the production dependency
  lock. CI/release graphs reject
  `mavenLocal()`, verification-off flags, and metadata/lock rewrite attempts.
- 2026-07-30: Advanced the release toolchain from AGP 8.9.1 to exact AGP
  8.10.1 / R8 8.10.24 so Reown's Kotlin 2.2 metadata is parsed by a supported
  shrinker; clean release logs and embedded AAB metadata are fail-closed.
- 2026-07-13: Added the production-satisfiable Android Iroha wallet-smoke
  metadata seam without enabling send. A closed immutable wallet model and the
  Nexus-only request factory enforce the exact four-key/all-string schema,
  canonical route hash and wallet commit, Android platform/role binding, and
  defensive snapshots. The operator seam also requires the exact Nexus global
  chain and canonical Minamoto endpoint. The staged Java codec remains
  Taira-only and rejects every non-empty metadata map before signing. Focused
  adversarial tests cover malformed keys, types, case, controls, confusables,
  sentinels, mutable aliasing, wrong network/chain, and endpoint substitution;
  ordinary transfers still use empty metadata and production DI still injects
  `UnavailableIrohaTransferSigner`.
- 2026-07-11: Added a fail-closed, non-production Iroha staging lane. The
  bounded materializer extracts only the pinned `core-jvm` coordinate; a
  Java-only Taira bridge and Kotlin 2.1 smoke module run behind an explicit
  CI/local gate; app debug/release graphs are proven free of the SDK. A custom
  compact-length transaction hasher works around the pinned SDK's confirmed
  fixed-`u64` defect and matches the inspected Rust/current-native diagnostic
  vector. Production DI, live Torii/funded evidence, exact binary provenance,
  device/R8 proof, authoritative live registry/precision/fee mapping,
  deployed-node compatibility, and private-key residue acceptance remain
  blockers. The tag fixture and current live Taira expose different canonical
  XOR definition IDs, so neither is a valid hard-coded production mapping.
- 2026-07-10: Hardened Android production release provenance: strict tag/master
  and exact prior-CI binding, immutable committed versioning, AAB signer
  verification, pinned build attestation/actions, and destructive release-gate
  tests. `runTest` now dynamically covers all 14 source-backed test modules,
  including the previously omitted `core-api`, backup, and XCM modules.
- 2026-07-10: Removed MoonPay server-secret/HMAC signing from the APK and kept
  the hosted checkout in publishable-key/manual-wallet mode with an exact host
  allowlist and adversarial query/control/size tests. Credential Manager
  transport and trust-all TLS lint checks are now fatal, and unused legacy
  Spongy Castle PKIX/PGP artifacts no longer ship at runtime.
- 2026-03-12: `scripts/build-libsodium.sh` now detects the correct host-specific NDK toolchain directory (darwin/linux/windows) instead of hardcoding macOS paths, so rebuilding libsodium works on Linux and CI hosts.
- 2026-03-12: Local validation script now installs Android platform/build-tools 36 so fresh environments match the Gradle compileSdk configuration before running tasks.
- 2026-03-12: Added Gradle compatibility shims inside `settings.gradle` (`jcenter()` repository + `JavaExec.main`) so the pinned `fearless-utils-Android` composite checkout remains buildable on the current Gradle stack until the upstream repository upgrades.
- 2026-03-12: Updated WalletConnect/Reown dependencies to BOM 1.6.9 and initially bumped AGP (8.9.1) / compileSdk (36) so upstream UniFFI native libraries ship with 16 KB page-size support.
- 2026-03-12: Temporarily excluded the WalletConnect Pay dependency (and its `yttrium-wcpay` natives) until Reown publishes 16 KB–aligned builds.
- 2026-03-05: Completed Google Play 16 KB page-size compliance for all bundled native libs (sr25519, TonConnect helpers, toolChecker) by rebuilding with NDK r28, verifying `readelf -l` alignment in CI, and clearing the Play Console warning.
- 2026-03-05: Completed security remediation batch for TON and account flows (TonConnect origin validation, TON network client hardening, WebView restrictions, encrypted preferences migration to AES-GCM, PIN lockout throttling, and internal-cache JSON export hygiene).
- 2026-03-05: Expanded TON indexer-first integration to include account transaction history ingestion with automatic TonAPI fallback, plus additional runtime tests for indexer/fallback behavior.
- 2026-03-05: Added TON history cursor normalization (`lt[:hash]`) so indexer requests can use `cursor_lt/cursor_hash` directly when available.
- 2026-03-05: Tightened TonConnect `tonapi.fetch` URL validation (HTTPS + `tonapi.io` allowlist + default port), fixed TON API header host matching for `*.tonapi.io`, and added regression tests.
- 2026-03-05: Hardened TonConnect dApp WebView navigation policy from host-only to strict same-origin checks to block cross-port and cross-origin hops.
- 2026-03-05: Re-enabled TonConnect `tonapi.fetch` through the hardened TON API client with strict `GET`-only execution on IO dispatcher.
- 2026-03-05: Fixed TON indexer fallback cancellation handling so `CancellationException` is not swallowed when indexer requests are interrupted.
- 2026-03-05: Added TonConnect URL hardening against localhost bypass aliases (`*.localhost`, decimal/short IPv4 forms like `2130706433` and `127.1`) and made WebView connection restore parsing resilient to malformed URLs.
- 2026-03-05: Pinned TON fallback reads to `https://tonapi.io` when indexer fails, even if chain nodes do not include TonAPI hosts, to keep ton-indexer primary and TonAPI fallback-only.
- 2026-03-05: Added ton-indexer JSON-RPC client support in Android (`sendBoc`, `estimateFee`, `getAddressInformation`) and switched TON transfer send/fee/time paths to indexer-first with TonAPI fallback.
- 2026-03-05: Added ton-indexer jetton transfer payload API integration for TonConnect signing (`/api/indexer/v1/jettons/{jetton}/transfer/{owner}/payload`) with TonAPI fallback.

## P0 — High Priority

1) Full support for Polkadot SDK release: polkadot-stable2503 (TOP PRIORITY)
- Why: Aligns the wallet with the latest stable Polkadot SDK, ensuring type/metadata compatibility and correct decoding/encoding across chains.
- Scope: Substrate runtime alignment across Polkadot/Kusama/Westend/AssetHub and major parachains used by the app (per `chains.json`).
- Acceptance:
  - App runs without SCALE decode errors on target chains.
  - Balances, transfers, fees, and staking screens load and execute extrinsics successfully on Polkadot and Kusama.
  - Chain sync (ChainRegistry) stable: connections establish, runtime providers load, subscriptions update on version bumps.
  - No regressions in unit tests; detekt/lint green.
  - If APIs changed (e.g., extrinsic names/signatures), code updated or guarded by capability checks; createPool/rename items validated.
- Prompt (steps):
  1) Registry overrides: In `local.properties`, set
     - `TYPES_URL_OVERRIDE=https://<your>/all_chains_types_android.json` (stable2503-aligned)
     - `DEFAULT_V13_TYPES_URL_OVERRIDE=https://<your>/default_v13_types.json`
     - `CHAINS_URL_DEBUG_OVERRIDE=https://<your>/chains.json` (debug-only chain list validated against stable2503; release remains pinned)
  2) Utils alignment (pinned source checkout): The build includes `soramitsu/fearless-utils-Android` as a composite source dependency.
     - Ensure NDK r28 (android-ndk-r28 / 28.0.x) and Rust toolchain with Android targets are installed (see README and CI config).
     - CI checks out the exact Utils and guarded transport commits from `config/android-runtime-source-pins.json`; local builds should clone that repo next to this checkout or set `FEARLESS_UTILS_PATH`.
  3) Library version pinning (shared_features): If required, pin via `SHARED_FEATURES_VERSION_OVERRIDE=1.x.y` in `local.properties` or env.
  4) Build + quick checks:
     - `./gradlew detektAll runTest :app:lint`
     - `./gradlew :app:assembleDebug`
  5) Runtime smoke tests: Run app against Polkadot and Kusama
     - Verify ChainRegistry establishes connections and loads metadata (logcat).
     - Open Wallet → Balances; verify assets and fiat values present.
     - Open Send; compute fee; submit a small transfer on Westend/Kusama dev if available.
     - Open Staking screens; ensure validators/nominators decode, no crashes.
  6) Address API deltas:
     - Search for runtime-extrinsic assumptions (e.g., staking pool create/rename) and update code or add capability checks.
     - Validate storage keys/paths used in wallet/staking; update binding code where schema changed.
  7) Update defaults (optional): If stable2503 becomes default, update `runtime/build.gradle` defaults and docs with new registry URLs.
  8) Document: Add the exact registry URLs used to `docs/status.md` and a short note on verification results.
- Verification matrix (execute manually or script):
  - Polkadot: balances load, transfer fee computed, send succeeds on test account.
  - Kusama: same as above; staking validator list loads.
  - AssetHub: asset enumeration works; transfers to another account OK.
  - Westend: basic transfer path used for low-risk checks.

2) Fix staking validator oversubscription/slashed logic
- Why: Marked FIXME; incorrect flags can mislead users and affect staking choices.
- Files: `feature-staking-impl/src/main/java/jp/co/soramitsu/staking/impl/presentation/mappers/Validator.kt`
- Acceptance:
  - Correctly reflects oversubscribed and slashed status independent of election state.
  - Unit tests cover typical and edge cases.
- Prompt:
  - Investigate current calculation for `isOversubscribed` and `isSlashed`.
  - Cross-check against chain indexer or on-chain sources for truthiness.
  - Implement corrected logic with clear documentation and tests.

3) Remove hardcoded chainAssetId in balance loader
- Why: Hardcoded fallback can show wrong asset balance on some chains.
- Files: `feature-wallet-impl/.../SubstrateBalanceLoader.kt` (search for "do not hardcode chain asset id")
- Acceptance:
  - Asset ID resolved from chain/asset registry consistently.
  - No direct default to utility asset unless explicitly intended and documented.
- Prompt:
  - Introduce a safe resolver using `ChainRegistry.getAsset(chainId, chainAssetId)`.
  - Add tests for chains with multiple assets and non-utility assets.

4) Adopt meta-account/EVM nullability across features
- Why: Several call sites assume non-null `accountId(chain)` which may be null for EVM chains.
- Files: Examples in `feature-staking-impl/.../StakingRelayChainScenarioInteractor.kt`, `feature-crowdloan-impl/...`, and account use cases.
- Acceptance:
  - No `!!` assumptions for account IDs on EVM chains.
  - Compile-time null safety; graceful user prompts to select/derive appropriate account.
- Prompt:
  - Introduce utilities to safely obtain chain-specific account IDs with null-safe flows.
  - Update call sites; add tests covering Substrate/EVM differences.

5) Replace TODO placeholders in NFT UI
- Why: Visible TODOs degrade UX and block validation of flows.
- Files: `feature-nft-impl/.../DetailsScreen.kt`.
- Acceptance:
  - Replace all `TODO("Not yet implemented")` with minimal functional UI or feature flags hiding incomplete screens.
  - Provide tracking issues for any scoped-down functionality.
- Prompt:
  - Implement minimal views with loaders/empty states and navigation back.
  - If data/API missing, add feature flags and hide from production builds.

6) Resource-based error texts for FearlessException
- Why: Error messages should be localized and consistent.
- Files: `common/.../base/errors/FearlessException.kt`
- Acceptance:
  - No generic empty strings; map kinds to string resources with fallbacks.
  - Unit tests verify mapping for Network/Unexpected/etc.
- Prompt:
  - Create `strings.xml` entries and a small mapper to user-friendly messages.
  - Replace TODOs with resource lookups.

## P1 — Medium Priority

6) Cleanup or implement `SocketSingleRequestExecutor`
- Why: Marked as unused; dead code increases maintenance burden.
- Files: `common/.../data/network/rpc/SocketSingleRequestExecutor.kt`
- Acceptance:
  - Either removed fully or covered by usages/tests.
- Prompt:
  - Run ripgrep for references; if none, delete and run CI. If used, document and add tests.

7) Crowdloan Karura interactor TODO
- Why: TODO indicates incomplete crowdloan integration for Karura.
- Files: `feature-crowdloan-impl/.../karura/KaruraContributeInteractor.kt`
- Acceptance:
  - Implement contribution logic or hide Karura option if not supported.
- Prompt:
  - Confirm current Karura status; implement required extrinsics or guard with feature flag.

8) Node switch UI polish
- Why: Button temporarily hidden; affects network management UX.
- Files: `feature-account-impl/.../OptionsSwitchNodeContent.kt`
- Acceptance:
  - Button visibility reflects product decision; if enabled, wiring works end-to-end.
- Prompt:
  - Validate `ChainRegistry.switchNode` flow; unhide button with proper enable/disable states.

9) Track Polkadot runtime upgrade (9390) and rename `createPool`
- Why: API alignment reduces confusion and future merge conflicts.
- Files: `feature-staking-impl/.../ExtrinsicBuilderExt.kt`
- Acceptance:
  - Name aligned post-upgrade; integration tests green.
- Prompt:
  - Add a build-time flag or comment with target version; plan a small PR once utils/runtime upgraded.

10) Expand runtime-flow tests (Room migration coverage delivered)
- Status: API 34 runs the full 61-test database suite, including malformed,
  oversized, interrupted, and no-wipe preservation cases. APIs 30, 31, and 36
  each run the compact 8-test released-schema/fail-closed database profile as
  part of a 39-test compatibility gate.
- Remaining why: Runtime connection lifecycle behavior still needs the same
  deterministic regression depth.
- Files: `runtime/...`
- Acceptance:
  - Smoke tests cover `ChainRegistry` start, stop, reconnection, cancellation,
    and concurrent `syncUp()` calls without live-network timing dependencies.
- Prompt:
  - Build lightweight `ChainRegistry.syncUp()` tests with fakes and adversarial
    cancellation/reconnection cases; keep the existing migration device matrix
    mandatory for every database schema change.

11) Per-module READMEs and entry points
- Why: Speeds onboarding and code navigation.
- Files: All `feature-*/` modules.
- Acceptance:
  - README in each module with purpose, key classes, DI entry, and main screens.
- Prompt:
  - Template a README and populate for wallet, account, staking first.

12) Gradle/AGP update and build hygiene
- Why: Keep toolchain current, reduce deprecations, and ensure reproducible builds.
- Acceptance:
  - Update to latest stable Gradle (e.g., 8.x) and Android Gradle Plugin (e.g., 8.x); no deprecation warnings in `./gradlew help`.
  - Build works with JDK 21; CI green. CI prints Gradle/AGP versions for traceability.
- Prompt:
  - Bump versions in `gradle/libs.versions.toml` and wrapper to the latest stable; fix any DSL changes.
  - Verify `url = uri(...)`, `namespace = '…'`, and packaging excludes for test APKs.
  - Keep a CI step that prints Gradle/AGP versions (android-ci.yml).

13) Utils source checkout contract
- Why: Keep `fearless-utils` open-source and reproducible without relying on private Maven artifacts.
- Acceptance:
  - CI checks out `soramitsu/fearless-utils-Android` at the pinned commit and fails early if it drifts.
  - `settings.gradle` includes the checked-out repo via composite build and substitutes `jp.co.soramitsu.fearless-utils:fearless-utils`.
  - README and validation scripts document the same local workflow.
- Prompt:
  - Update `FEARLESS_UTILS_COMMIT` intentionally when the source dependency changes, then rerun `scripts/ensure-fearless-utils.sh` and the Android build/test suite.

## P2 — Lower Priority

12) Centralize chain/type override docs and checks
- Why: Developers often need to align with Polkadot SDK releases.
- Acceptance:
  - Single doc page with examples; pre-flight Gradle check warns when overrides set.
- Prompt:
  - Expand `ARCHITECTURE.md` or a new doc; optional Gradle task to echo overrides.

13) Compose migration plan (where applicable)
- Why: Mixed View/Compose code; define direction.
- Acceptance:
  - Short plan identifying priority screens and blockers.
- Prompt:
  - Audit major screens; identify shared UI components to port first.

14) Network-state observability improvements
- Why: Better debugging for chain sync failures.
- Acceptance:
  - Structured logs/metrics for `ChainRegistry` sync and node switches.
- Prompt:
  - Add log tags and failure counters; consider emitting events for debug builds.

15) Detekt rule hygiene for TODOs
- Why: Config already forbids TODOs; enforce cleanup instead of accumulating markers.
- Acceptance:
  - Replace TODOs with tracking issues or feature flags; CI stays green.
- Prompt:
  - Sweep TODOs; convert to issues with links in code comments.

---

Team prompts for execution
- Default workflow:
  1. Pick a P0/P1 task, create an issue with scope and acceptance.
  2. Draft a small PR (1–3 files where possible) with tests.
  3. Run `./gradlew detektAll runTest :app:lint` locally; ensure CI green.
  4. Add a brief note in `docs/status.md` if the change affects status/risks.
