# Roadmap & Technical Debt

Actionable, prioritized tasks phrased as clear prompts for developers. Each task includes goal, acceptance criteria, and suggested steps.

Priority: P0 (must-do), P1 (should-do), P2 (nice-to-have)

## Recent Updates
- 2026-07-27: Disabled the legacy MoonPay provider and removed all MoonPay
  signing-secret BuildConfig fields and release requirements. Re-enabling
  MoonPay now requires backend signing or a supported public mobile flow; the
  secret exposed by previously released clients must be revoked/rotated. The
  policy gate now scans tracked Android sources across every module plus all
  build scripts, release scripts, and workflows, with alternate-module
  adversarial fixtures.
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
     - `CHAINS_URL_OVERRIDE=https://<your>/chains.json` (points to chain list validated against stable2503)
  2) Utils alignment (pinned source checkout): The build includes `soramitsu/fearless-utils-Android` as a composite source dependency.
     - Ensure NDK r28 (android-ndk-r28 / 28.0.x) and Rust toolchain with Android targets are installed (see README and CI config).
     - CI checks out `7500809f33243ee47ecb2ec8563fc284ac4de0d6`; local builds should clone that repo next to this checkout or set `FEARLESS_UTILS_PATH`.
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

10) Tests for DB migrations & runtime flows
- Why: Critical to stability across releases.
- Files: `core-db/.../migrations/*`, `runtime/...`
- Acceptance:
  - Migration tests for latest versions; smoke tests for ChainRegistry start/stop.
- Prompt:
  - Add Room migration tests for recent migrations; create lightweight tests for `ChainRegistry.syncUp()` using fakes.

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
