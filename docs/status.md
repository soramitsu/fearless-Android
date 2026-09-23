# Status Summary

## Immutable Drive generation primitive — 2026-09-23

- The disabled FPBKGEN1 candidate preserves original FPBKAEAD metadata/ciphertext and FPBKWRP1 wrappers, binds owner/namespace/parent/epoch/storage subject, and uses canonical bounded bytes. Its independent Node vector is 785 bytes, SHA-256 `1c92b544dc25c687c202317d0e5747b5690a1056cf72e61d1dfab84c07c057a4`.
- A separate subject-bound Drive store preallocates appData file IDs, creates one-shot immutable candidates, and downloads exact ID/digest/context. It has no PATCH/delete/automatic retry or head update. Lost/409/malformed create outcomes require reconciliation; an upload acknowledgment or parsed download is not decryption proof.
- All 181 backup-module JVM tests pass with zero failures/errors/skips, including 17 generation tests and a full 256 KiB legacy envelope with 32 wrappers under the separate 512 KiB generation bound. `detektAll` passes under strict offline dependency verification; all 28 captured module source/resource hashes remained unchanged during validation.
- Recovery stays disabled. Durable upload journal, native decryption/identity acceptance, owner-head/grant HTTP integration and transactional lifecycle coordination, real Google/provider and replacement-device qualification remain incomplete. See `docs/passkey-generation-v1.md`.

## Passkey Drive subject binding — 2026-09-23

- The disabled Android Drive candidate verifies the exact bearer through Google OIDC UserInfo (`openid email` plus appData consent), pins stable `sub`, requires initially matching verified email and rejects account/token substitution before every Drive request. Email renames retain the original authenticated envelope metadata and ciphertext.
- Strict identity parsing and dedicated bounded/no-redirect transport fail closed. The token backing field is excluded from Gson serialization; Google identity does not replace owner authorization.
- Validation: all 164 backup-module JVM tests pass (52 Drive/account/identity tests), with zero failures/skips; `detektAll` and exact runtime source verification pass under strict dependency verification. Native consent/UI, shared Google Cloud configuration, provider/device interoperability, owner lifecycle and generation acceptance remain blocked; recovery stays disabled. See `docs/passkey-google-drive-subject.md`.

## Release checkout guard inventory — 2026-09-23

- The release architecture guard now requires all seven commit-pinned, credential-free checkouts: three app checkouts and the Utils/WebSocket source checkouts in both controls and build. Each checkout has independent mutable-action and persisted-credential negative coverage.
- App debt and unit-test membership audits now treat the exact top-level pinned WebSocket checkout as an independent dependency, matching the existing Utils boundary. Fixtures retain checks for similarly named and nested first-party sources in both debt scanners.
- `runTest` now includes liquidity-pools, Polkamarkt and Polkaswap JVM suites; their new mutation-boundary tests were missing from the aggregate. Membership fixtures reject dropping either a required task or its task and source together. All three module suites pass locally with strict dependency verification.
- The debt baseline also removes the single stale TotalBalanceUseCaseImpl marker whose source comment was removed by the consolidated candidate; new or executable markers remain forbidden.
- Validation: the release-architecture suite passes 4 positive and 136 deterministic negative/adversarial cases.
- This corrects the stale five-checkout expectation that stopped PR CI before Gradle and emulator execution. API 30/31/36 migration runs, result-start markers and portable evidence remain required; passing the static guard alone does not qualify those runs or a release artifact.

## Guarded physical boundaries and immutable sources — 2026-09-22

- New-feature leases now reach physical RSA unwrap, wallet-key attestation and payload decryption after locks, preferences and cipher setup. Signing uses a prepared single-use primitive after hashing/provider initialization. Durable authority clock checks resample after persistence; observed expiry cannot be revived by rollback/refetch.
- Utils carries the exact intent lease into the nv-websocket writer after serialization, listeners and preceding-output flush. Guarded compression is rejected before compressor state changes. The final action writes directly to underlying output, reports uncertain outcomes after commitment, and never replays. Legacy read/RPC/compression/signing paths remain independent.
- The complete Utils source candidate is PR153, based on the original runtime-compatible7500809 source; transport PR1 is based on exact upstream2.14 commit3168d4e. `config/android-runtime-source-pins.json` binds both candidates and trees. CI/release verifies raw committed sources before resolution and after builds; active workflows and release manifests use source-pin digests instead of applying dependency overlays. Native SR25519 blob provenance remains tied to its original build source.
- Transport: 90 strict local tests pass, including two actual loopback WebSocket connections; its published CI passes. SDK tests include Ed25519/Substrate-ECDSA/Ethereum signing parity and single-use/revocation failures. Android key, queue, authorization and app Hilt validation logs are retained under `build/android-production-phase4-*.log`; the final handoff report records exact counts and hashes.
- These candidates remain subject to review/merge, full merged-tree release CI, SR25519/native device and TLS qualification, operator trust provisioning, funded per-route evidence, and authentic Play-signed upgrades. Missing trust stays denied and executable XCM routes remain zero. The older phase2/phase3 entries below describe their historical partial checkpoints.

## Production qualification baseline — 2026-09-22

- Android now targets API 36 (compile SDK remains 36). The final signed AAB, target-36 device behavior and a real 16 KiB-page device still require qualification; changing the source target is not store-release evidence.
- Reviewed XCM discovery and fee quotes remain available while the release submission flag is hardcoded false. The transfer engine checks compiled permission before the remote mutation switch and before entering the signing/submission delegate, including callers that bypass the UI. Existing per-route approvals and the blocked production-evidence manifest are unchanged: 15 compatible reviewed candidates, zero production-executable routes, and 34 discovery-only routes.
- XCM quote preparation defers wallet-secret access until a permitted transfer requests a key. Origin-fee queries use the existing SCALE encoders with an invalid placeholder signature of the correct variant and width, preserving nonce, era, zero tip and call data while avoiding wallet signing. Runtime-specific fee parity still requires live qualification.
- Validation: all 114 XCM module JVM tests, 5 focused wallet debug tests and 2 wallet release tests pass with strict dependency/repository policy. Release tests verify all 15 reviewed routes remain discoverable with fresh compatible chain data while direct submission is blocked. Effective-registry adversarial checks pass 135 scenarios; production-evidence checks pass 117 scenarios; native-page verifier fixtures pass 1 valid and 20 negative scenarios. Focused Gradle and final audit logs are retained under `build/android-production-phase1-*.log`.
- Remaining gates include qualification of the guarded nv-websocket source candidate; funded and reconciled per-route XCM evidence; configured release signer trust; immutable publication of the final source/dependencies; and Play-signed in-place upgrades that preserve every legacy wallet and recovery/export path. This source checkpoint does not qualify a production artifact or enable XCM submissions.

## Intent-bound key/signing and Utils queue checkpoint — 2026-09-22

- New XCM, reviewed bridge and Polkamarkt submissions freeze SCALE call, account, nonce, mortality, tip and runtime identity before acquiring an intent-bound lease. The canonical key repository checks the lease at physical secret reads, including after the chain-secret dispatcher switch. Signing uses the frozen bytes and the same lease; ordinary wallet signing remains unchanged.
- Utils guards recheck after RPC serialization/logging before WebSocket.sendText. Connecting/paused/executor queues recheck authorization; connection failure, pause, switch and stop terminate guarded requests without automatic replay. Cancellation, reentrant failure, exactly-once response/error handling and legacy reconnect behavior have focused regressions. RequestExecutor now safely invalidates a canceled queue while actions complete concurrently.
- This is a partial transport checkpoint: nv-websocket-client 2.14 still queues frames internally and writes buffered output after sendText returns. Authorization must also guard that writer after compression/serialization and immediately around final output write/flush. No capability is enabled and no production network boundary is claimed complete.
- Focused tests and dependency/source evidence are recorded in `build/reports/android-production-phase3-20260922.json`; the final exact-checkpoint build log is `build/android-production-phase3-final-validation.log`. The broad `detektAll` run reported 285 weighted issues in the working tree; it is not a clean static-analysis gate. Effective-registry fixtures pass 135 cases, dependency-provenance fixtures pass 2 positive/65 negative cases, and executable XCM routes remain zero.

## Signed mutation authority core — 2026-09-22

- New Polkamarkt, XCM and Polkaswap bridge permissions now require a verified FWMA1 Ed25519 authorization instead of persisted remote booleans. The closed canonical payload binds the production package, inclusive installed version-code bounds, immutable policy and route manifest digests, revision and capability set. Lifetime is at most 900 seconds. Application startup begins denied and refreshes every five minutes; missing, invalid, expired or unavailable authority denies these new mutations.
- Only replay/clock high-water metadata persists, synchronously before permission is granted. Lower revisions, same-revision payload substitution, corrupt state and ambiguous durable-write failure deny. Within a process, retrying the same token cannot extend its elapsed-time deadline; failed refresh invalidates operation leases. Legacy Polkaswap and historical Demeter rollout controls, navigation, route discovery and read-only quotes retain their existing behavior.
- Production composition verifies three immutable assets and the exact installed package/version. The trust asset intentionally contains no production key or accepted digests, and the policy approves no capabilities. The route manifest hashes the actual bundled approved XCM routes and chain definitions. Provisioning requires reviewed operator trust and final artifact policy; expanded bridge/catalog coverage must be approved before those capabilities can be enabled.
- This checkpoint implements verifier/state/refresh and an intent-bound lease API. Final key access, signing and actual socket-send integration remains unfinished. The Utils transport queues asynchronously, including while connecting, so an Android-only check before RPC enqueue would not close the broadcast race. No new feature is production-enabled by this work.
- Validation: 21 focused JVM tests pass (14 authorization/state, 3 refresh, 4 toggle integration), including all 9 shared wire vectors, clock rollback after an expired fresh fetch, corrupt/ambiguous durable state, exact intent mismatch, compiled approvals and unsigned cached-toggle bypass attempts. App Kotlin integration compilation and Hilt component compilation pass. The final clock-state rerun passes all 21 cases in 56 seconds.
- Shared contract and test fixtures: `docs/security/mutation-authorization-v1.md` and `common/src/test/resources/mutation-authorization-v1-vectors.json`; local handoff copies are under `build/reports/mutation-authorization-v1/`. Focused validation is recorded in `build/android-production-phase2-authority-tests.log`, `build/android-production-phase2-final-tests.log` and `build/android-production-phase2-clock-final-tests.log`.

## Portfolio network headers — 2026-09-07

- Healthy headings now show the network name and available fiat subtotal. Routine scan coverage/timestamps, repeated account/ecosystem metadata, asset counts and missing-price placeholders are hidden; failed, outdated and unloaded balances retain short localized status messages. Collapse and detected-asset review are unchanged.
- Validation: five focused JVM status tests and the native 320dp/200% text header fixture pass; debug app/test APKs build successfully. Screenshot and exact artifact hashes are in `build/ux-evidence/20260907-portfolio-header/`.

## Actual TalkBack and large-text UX — verified 2026-09-06

- Official Google TalkBack 16.2 was built unchanged and installed only on isolated emulator 5560 with official RHVoice speech output. Actual focus, keyboard activation and output audio cover the single raised Polkaswap action at 100%/200%, Receive address/copy/share, task-first Create/Restore and TON/Back, wallet footer reachability, and the repaired options/backup route. Production-view fixtures with synthetic data additionally verify spoken swap amounts/minimum/fees, execution disclosure and recovery/unavailable actions.
- Real 200% findings are fixed: flexible toolbar/network/score layout, opaque navigation with a measured content inset, scrollable balance/backup/asset states, stacked Currencies/NFTs, wallet names that reserve their options action, and complete options-sheet action text. Existing-wallet setup Back now returns to Wallets. Reusable wallet dialogs share the selector's authenticated shell, fixing an actual options activation exception while preserving tab and Back behavior.
- Final scoped validation: **41 JVM / 15 native checks pass**. Final app/test build passes in 1m54s; the final 14-test native rerun passes in 15.169s. App SHA-256 `1d08d9bd248cf999999fa3c042819296e222dfce26df00da4d26b809e30768ea`; test SHA-256 `40a010368dcf186023cf75b0aa60b0d36fd081aa02579c05a3c0694122f81128`.
- Evidence: `build/ux-evidence/20260906-talkback/README.md`, per-case focus/utterance/PNG/WAV records, exact APKs, logs, source snapshots and manifest. Real-wallet and synthetic-view scope, intermediate artifact hashes and failed diagnostics are explicit. No recovery words, signing or transactions are recorded. Earlier TalkBack-unavailable statements below describe superseded checkpoints.

## Legacy upgrade safety audit — 2026-09-06

- Older wallets retain normal access when the six-ecosystem identity is incomplete, including chain-only/watch-only wallets. Legacy chain-specific address routing now takes precedence in both full and light account models.
- DB31 → 32 preserves existing EVM public/private keys and addresses. Historical recovery metadata that no longer reproduces that key is converted to the existing direct-key EVM representation, retaining validated Substrate mnemonic/seed/path. Interrupted cross-store conversion is retryable without changing the original wallet or its asset address.
- Optional missing EVM derivation failure leaves the existing Substrate wallet intact. Enabled canonical Bitcoin mainnet, Solana mainnet and Taira public accounts are added from the existing validated mnemonic outside awaited startup. Insert-only writes, the wallet mutation mutex, per-wallet/network error isolation and no completion marker preserve existing accounts and permit retry.
- Validation: 74 JVM tests passed with 4 pre-existing manual-only skips, plus 43/43 API34 instrumentation tests. Initial app integration compilation passed; a later redundant app rebuild hit a reported shared-output KSP collision and requires isolated revalidation. No audit Gradle/device operations remain active.
- Current audit evidence and limitations: [legacy-upgrade-audit-20260906.md](legacy-upgrade-audit-20260906.md). These changes are source validation, not a newly qualified Play release artifact.

Last updated: 2026-09-05

This snapshot summarizes the current health, feature coverage, and key risks of the Fearless Wallet Android codebase.

## UX updates — 2026-09-05

- Onboarding starts with Create/Restore, then explains Ethereum/Polkadot and TON using asset names. The selected intent survives recreation through SavedStateHandle; existing account creation, import and signing paths are retained.
- Wallet protection is persistent above assets. Optional network discovery sits below the asset list without automatic paging.
- Receive displays the asset, network, network-specific instruction and full selectable address. QR data and clipboard operations retain their existing implementations.
- Toolbars, wallet selectors and swap controls expose localized action labels and larger touch targets. The send-maximum switch names its consequence and uses the entire row as its accessible target.
- Swap keeps minimum receive and network fee visible, moves route/exchange-rate details behind a disclosure, and removes liquidity promotion from the transaction form.
- Navigation uses Wallet / Earn / Polkaswap / Transfer / Settings with unchanged graph IDs. At the user’s request, Polkaswap keeps the distinctive raised center artwork and curved navigation background, with one accessible center action and the existing repeated-tap behavior. At larger font scales, the surrounding captions wrap and the bar grows vertically; the center artwork has no overlapping caption. Send links to the existing network-transfer flow using the selected asset. An Activity tab is intentionally not claimed: AddressHistory is a recipient picker requiring a chain payload, not a global activity screen.
- Pool staking labels annual rewards as estimates and explains variability. Pool creation is disclosed under operator options.
- Validation: common, onboarding, wallet, Polkaswap and staking Kotlin compilation passed. All 6 focused regression tests passed (2 toolbar-action labels, 4 onboarding intent/routing cases; zero failures, errors or skips). Resource XML parsing, UX string-reference checks and scoped `git diff --check` pass. Staking compilation caught two missing labels in fully qualified PoolInfo toolbar calls; both are fixed and staking retry passed. App integration compilation passed in the previous verification run. The final debug APK and test APK builds pass, along with 6 navigation JVM tests and 7 API 34 instrumentation tests. The additional native Compose case covers production Create/Restore, Receive and Swap views at 320dp and 100%/200% text: action labels fit, consent/full addresses/copy/share remain scroll-reachable, and execution details expand to the route. Visual checks caught and fixed clipped onboarding buttons and the oversized swap market header; the current market now lives in execution details while the settings action keeps a 48dp target. The native tests cover exact button accessibility naming, one accessible center action, minimum 48dp targets, selection/reselection, existing back stacks, and untruncated captions at 320dp with 100%/200% text. Captured production-layout fixtures are in `build/ux-evidence/20260905-polkaswap/`. Additional screen captures with synthetic balances, a synthetic address and an encoded fixture QR are in `build/ux-evidence/20260905-screens/ux-evidence/`. These are attached production-view fixtures, not a live wallet or transaction run. A later isolated real-wallet walkthrough is recorded below. At that checkpoint TalkBack was not yet installed; the completed official-service checks above supersede it. The default local utils checkout fails strict AGP 8.9.1 dependency verification; local validation instead selects the existing AGP 8.10.1 runtime-compatible checkout with verification still enabled. No release gates, signing inputs, dependency metadata or production artifacts were changed.


### Real onboarding follow-up

- A fresh isolated API 34 emulator completed actual Substrate/EVM and TON Create/Restore using synthetic wallets, plus invalid-phrase retry, Back, PIN unlock, saved selection and wallet switching. No transactions, signing, purchases or cloud recovery were performed. Substrate creation used the existing skip-confirmation option to verify persistent backup status; TON creation retains its existing internal generation and immediate wallet entry.
- The walkthrough found a first-wallet crash in optional network discovery: its shared banner read the newer typography context from the legacy wallet host. The banner now provides `FearlessAppTheme`; the added native regression renders both ecosystem banners inside the legacy host (1 pass, bringing the focused native total to 8).
- TON's header now looks up the selected canonical network ID instead of displaying the first registry entry. Four new JVM cases pass, including TON after Polkadot and missing/loading registry data.
- A timing-dependent TON cold-start ANR exposed the pinned BOC parser waiting on Default work while concurrent scans held the whole Default pool in class initialization. App startup now initializes the existing fixed V4R2 code before scanning begins; contract bytes and address derivation are unchanged. The new JVM regression completes 32 concurrent reads against an independent public address fixture. The final debug APK build passes, and three consecutive real TON cold restarts pass (14.16s / 13.85s / 13.59s including PIN and UI waits), preserving wallet selection and the corrected TON identity. Final focused totals are 24 JVM and 8 native checks. Exact evidence is in `build/ux-evidence/20260905-real-onboarding/README.md`.
- Early screenshots from the Google ATD image were black because its drawing was disabled. They are labeled diagnostic failures, not visual evidence. The isolated emulator's software rendering and `debug.hwui.drawing_enabled` were enabled before final capture. Real wallet, backup, network selection, Restore and invalid-input screens were visually inspected. The generated TON empty state now uses the existing neutral text; “hidden all assets” is reserved for a nonempty scope whose visibility flags are all disabled. Four empty-state regressions and the real generated-wallet screenshot pass. The address chip now observes both wallet and chain identity, clears while resolving and cancels late work; three flow regressions and three real same-chain switches match each wallet’s independently derived public model address. Existing 320dp/100%/200% production-view fixtures remain separately documented.

### Confirmation recovery and large text follow-up

- Continued real backup testing found that failed account creation or backup-status persistence left Continue/Skip disabled. Confirmation now releases the submission guard on failure or cancellation and preserves entered words; successful navigation retains duplicate-tap suppression. If wallet creation already succeeded, a later filter/PIN completion failure retries using that saved wallet ID. Confirming words after a failed skipped-wallet completion updates backup status without creating another wallet. Account payloads, derivation and persistence APIs are unchanged.
- Seven new ViewModel regressions pass: returned failure, thrown TON Skip failure, existing-wallet backup failure, duplicate submission, cancellation, post-create completion failure and Skip-to-confirm recovery. Focused JVM coverage is now 31 passing cases. The final debug APK and test APK build pass in 2m1s; the build log and JUnit XML are in `build/ux-evidence/20260905-real-onboarding/confirmation-build-tests.log` and `confirmation-retry-junit.xml`.
- The 320dp/200% real confirmation screen previously let its preallocated selection panel cover the source words and Continue. Both word panels now scroll above the fixed actions; an empty selection uses a small minimum and grows with entered words. Selectable words have 48dp minimum dimensions. Confirmation Back/Reset and the directly exercised mnemonic-export Back action have localized names and 48dp click areas with unchanged 24dp artwork. The new production-layout native regression passes at 320dp/100%/200% text (1 case, 2.709s), bringing the scoped native total to 9. Actual 24/24 generated-word confirmation at 200% text passes on that confirmation APK, and backup status persists after cold restart. Receive checks on the later corrected APK are recorded below.
- The confirmation APK SHA-256 is `8f2aee68fc6356222954fbe3f7db8c5d4c78bf5bfb8f1de8b1a23c1354836c5c`. Earlier three cold-start durations and address-switch screenshots remain evidence for the preceding `e12b6ba...` APK and are not attributed to these later confirmation changes.

### Receive consistency and Request follow-up — 6 September

- Source audit found a mismatch after changing SORA Request tokens: displayed asset/QR changed while share text still described the opening asset. Receive now derives display, address, QR and share from the same current snapshot. A replacement QR clears the old image while keeping the form mounted; Copy/Share are disabled until it is ready. Latest-only generation cancels obsolete work, stale share preparation is discarded, failed sharing can retry, and reopening the token selector keeps the current selection. SORA and plain-address QR formats and account/interactor APIs are unchanged.
- Actual Request mode also crashed when its AmountInput read the newer typography context from the legacy wallet bottom sheet. Receive now supplies the existing FearlessAppTheme locally; shared theme behavior is unchanged. Copy address uses the existing translation and Share address is translated for all eight existing Android languages.
- Six snapshot regressions pass, bringing scoped JVM checks to 37. The three-test native screen class passes, including the new Request-at-200% legacy-host case and two existing cases; scoped unique native checks now total 10. Both APKs build in 3m25s. The exact app SHA-256 is `c493eaaf146f5e1c1f4729ad88b4ad30b22d4df4e6ab57ad6f3fb0de3ab17265`; durable logs, JUnit and source/artifact hashes are in `build/ux-evidence/20260905-real-onboarding/receive-build-results.json`.
- The fixture-only correction uses the actual Activity viewport and asserts complete action bounds; its test APK builds in 31s and the native class rerun passes 3/3 in 5.706s. The 16 corrected captures were inspected; earlier clipped images remain clearly marked diagnostics. Actual final-app Receive comparisons pass for two SORA wallets, a TON wallet and an additional ETH/Ethereum case (4/4): visible address, decoded QR, clipboard and Share preview agree. The ETH shared image also decodes to the displayed address. Actual Request at 200% text has fully visible address/actions; amount 1.25 and CERES→DEO switching preserve the wallet address and give matching current-token displayed/shared-image QR payloads and share text. Existing Manage assets toggles and Search make these zero-balance assets reachable without changing Portfolio policy. The final scoped crash buffer is empty. At that checkpoint no accessibility service was installed; the completed official-service checks above supersede it.

## Overview
- Platforms: Android (Kotlin 2.1, Java 21 target). Compose enabled in selective screens.
- Ecosystems: Substrate/Polkadot, EVM (Ethereum-compatible), TON.
- Architecture: Modular feature pairs (`-api`/`-impl`), shared foundations (`common`, `core-api`, `core-db`, `runtime`). Hilt for DI.
- Build types: `debug`, `release`, `internalAppSharing`, `staging`, `develop`,
  `pr`. `internalAppSharing` is a CI-only, unsigned, file-only smoke variant;
  a separate fresh-runner qualifier externally debug-signs only the exact
  verified handoff candidate. It is not a production distribution build.

## Feature Coverage (High Level)
- Wallet: Send/Receive/History/Manage Assets — present and integrated (`feature-wallet-*`).
- Accounts & Onboarding: Present (`feature-account-*`, `feature-onboarding-*`).
- Staking & Crowdloans: Present for Substrate ecosystems (`feature-staking-*`, `feature-crowdloan-*`).
- Swaps & Pools: Polkaswap and liquidity pools present (`feature-polkaswap-*`, `feature-liquiditypools-*`).
- WalletConnect v2: Initialized in `App.setupWalletConnect()` with Reown SDK.
- TON Connect: Present (`feature-tonconnect-*`).
- NFTs: Present; details screen has TODO placeholders.

## Build & CI
- CI Pipeline: `.github/workflows/android-ci.yml` runs detekt, unit tests
  (`runTest`), app lint, and the existing release/debug build checks on push/PR.
- Android Internal App Sharing is implemented as a deliberately separate smoke
  lane, but its distributable handoff status remains **pending** until the full
  frozen suites and a fresh eligible manual workflow run are green. It accepts
  only the exact `:app:bundleInternalAppSharing` CI invocation from a clean
  `RELEASE_COMMIT == HEAD` source with independently bound fearless-utils
  commit/effective-tree values. It inherits release minification, shrinking,
  application ID, version code, and manifest semantics; appends `-ias` to the
  version name; and uses only a run-bound, mode-`0600`, 30-day ephemeral JKS
  with the Android Debug identity only after a separate unsigned producer
  stage. Disposable OS-user boundaries isolate unsigned validation, signer
  creation, post-sign verification, and uploaded-archive audit. The certificate
  fingerprint is derived and bound for that one run; the repository and
  handoff never contain the private key.
- IAS configuration finalizes the typed input of
  `processInternalAppSharingGoogleServices` directly to the checked-in,
  checksum-pinned `fearless-public` release placeholder. It rejects symlinks,
  hardlinks, non-public Firebase values, production signing or Firebase inputs,
  Play credentials/controls, and mixed Gradle task requests. It never creates
  `app/src/internalAppSharing` or any other generated source-tree bridge. The
  fake/empty Firebase and OAuth values mean IAS cannot qualify live Firebase,
  Google OAuth/sign-in, or Google Drive/passkey backup and restore.
- The IAS artifact verifier and adversarial suite enforce complete JAR
  signature coverage by the debug certificate, embedded source commit,
  package/`-ias` version/SDK identity, exact components and permissions,
  reviewed native payloads, and compiled public Firebase values. Pull-request
  runs are non-distributable validation only and upload no artifact. Only a
  manual first-party run from the protected, merged, current `develop` head may
  retain a seven-day GitHub Actions handoff after exact artifact-ID/digest
  download-back, bounded ZIP revalidation under a disposable user, and a
  separate deletion finalizer; a scheduled janitor removes stale pending
  artifacts. An operator may then manually upload its exact verified AAB to
  Play Console Internal App Sharing. CI must never upload IAS bytes to
  Google/Play or mutate a Play track. PR validation receives no WalletConnect
  secret; only the exact protected-`develop` dispatch build step may receive a
  required non-logged 32-hex project ID, and trusted device acceptance includes
  a valid pairing scan. Frozen totals are dependency provenance
  2 positive / 65 adversarial, bounded Gradle coverage cleanup 4 positive / 12
  negative/adversarial, IAS Gradle 8 positive / 39 behavioral negative / 645
  static adversarial, and workflow Linux certificate-mode IAS AAB 7 positive /
  78 adversarial. The shared AAB identity harness separately freezes the R8
  policy at 4 positive / 19 negative/adversarial and its exact fixture
  synthesizer boundaries at 1 positive / 15 negative/adversarial.
- The historical 26 July 2026 Google Play Internal App Sharing observation is
  now represented by a URL-redacted, digest-bound manifest and 6-positive / 184
  adversarial offline contract. It remains explicitly test-only and does not
  claim current link availability, device installation, independent testing,
  production signing, or production readiness. A separate AAB verifier checks
  every native PT_LOAD segment for 16 KiB alignment across all four Android
  ABIs, with 1 positive and 20 negative fixtures, and runs against CI, IAS,
  unsigned-release, and signed-release bundle bytes.
- Signed Android release artifact pipeline:
  `.github/workflows/android-release.yml` builds
  only an immutable tagged `master` source after exact-head CI passes. CI has
  no Play track/status inputs and performs no Play mutation.
  Credential-free `release-controls` validates the signed tag, prior CI,
  disjoint environment governance, all adversarial release suites, and the
  exact source commit/tree before either protected stage.
  `release-build`, protected by `android-release-build`, restores only
  Firebase, runs the exact CI-only/source-bound unsigned Gradle bundle, and
  stages exactly the unsigned AAB, checksum, provenance, and bounded verified
  Gradle/R8 build log. It individually attests and verifies all four files
  before uploading the artifact.
  `release-signing`, protected by the separately reviewed
  `android-release-signing` environment, runs on a fresh runner. It downloads
  exactly those four files and revalidates the digest, provenance, source
  attestation, tag, and current `master` before restoring the upload key.
  The standalone signer preserves every non-signature entry byte-for-byte and
  atomically writes the certificate-pinned AAB. The job verifies signature,
  package/version/source/native identity, and exact merged media permissions
  from the signed bytes, records the identity/permission evidence and digest in
  final provenance, then individually attests the final AAB, checksum, and
  provenance.
  The release AAB embeds the exact tagged commit and the verifier derives it
  from the manifest, checks the exact complete 16-library native inventory
  (including SQLCipher and AndroidX), and rejects missing, duplicate, extra, or
  substituted native payloads. Provenance also binds the pinned
  `fearless-utils` commit and library-only patch checksum.
  The unsigned transfer contains exactly four entries; the final signed
  transfer contains exactly three. Both require exact checksum/provenance key
  sets. Every job repeatedly rejects tracked, staged,
  untracked, and source-like ignored drift from the approved source tree. The
  protected runners use the independently checksum-pinned Temurin
  `21.0.12+8` archive and bind its archive plus Java-tool binary digests into
  provenance. CI never receives a Play service-account credential. An
  authorized operator reviews the final evidence and uploads the AAB manually
  to the existing Open Testing track in Play Console.
- Gradle dependency provenance is fail-closed for CI/release graphs: the Gradle
  9.0 distribution SHA-256, strict dependency verification metadata and its
  tracked digest, the strict root buildscript lockfile, and the production
  release lockfile are pinned. CI/release builds reject `mavenLocal()`,
  verification-off flags, and metadata/lock rewrites.
- Android versioning is source-controlled and immutable during builds. Google
  Play production is version code 229; version code 230 is reserved for the
  migration-safe 4.2.0 testing candidate.
- Release signing has no debug-key fallback. The guarded workflow deliberately
  produces an unsigned intermediate without any keystore input, then the
  standalone signer fails if a signing input is absent, the certificate is
  invalid, or its SHA-256 is not the registered Google Play upload certificate
  `40:39:10:92:F5:B9:7E:78:2C:65:28:CC:57:1A:DF:5D:BE:DF:E2:D0:50:23:BA:BC:7C:4E:33:9E:58:4A:4A:9A`.
- The release source and every merged release manifest are gated against
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and broad legacy external-storage
  permissions. The legacy open-test artifacts (version codes 221 and 109)
  request broad media access; Play must be given the clean version-code-230
  bundle with the track resume instead of using its “continue anyway” override.
- MoonPay's legacy client-side HMAC secret and signing fields are absent. The
  provider uses only publishable keys, an exact hosted-checkout allowlist, and
  manual wallet entry; prefilled-wallet URLs still require a backend signer.
  The secret exposed by already released Android artifacts must be
  revoked/rotated with MoonPay before the next release. The fail-closed audit
  covers every tracked Android source set, build input, release script, and
  workflow, with alternate-module/buildSrc/workflow adversarial fixtures.
- Release Firebase backup construction is interruption-safe: it validates a
  restricted temporary copy's size, SHA-256, and exact mode before an atomic
  rename. Cleanup deletes incomplete temporaries. The split-overlay suite
  covers 2 independent positives, 20 phase/credential-isolation negatives, 20
  restore and 8 cleanup TERM/SIGKILL cases, and 2 backup-integrity negatives;
  the checked-in placeholder survives every covered boundary and no
  signing/Play credential remains.
- Secrets in CI: Stubbed publishable MoonPay keys, EVM providers, and history
  providers keep public CI stable. MoonPay server secrets are prohibited from
  Android builds; prefilled-wallet URLs require a backend signer.
- Test aggregation: `runTest` now covers every module with Kotlin/Java sources
  under `src/test`, including `core-api`, passkey backup, and XCM. A dynamic
  membership audit and adversarial self-test prevent omitted, stale,
  commented-out, duplicated, or shadow task entries.
- Iroha send remains production-disabled. An explicit CI/local gate now
  materializes only the checksum-pinned `core-jvm` artifact into ignored build
  output, tests a Java-only Taira bridge plus Kotlin 2.1 isolation, independently
  verifies compact transaction hashing against a Rust/native diagnostic vector,
  and proves the app runtime has no staged SDK dependency. A closed immutable
  request seam now validates the exact Nexus Android wallet-smoke transaction
  metadata contract and gates its operator path to the canonical Minamoto
  endpoint. The Taira-only bridge rejects every non-empty metadata map; normal
  transfers retain empty metadata and production DI remains fail closed. A
  reviewed Nexus metadata codec, live Torii receipt,
  funded-network, provenance, Android device/R8, authoritative live registry and
  fee mapping, deployed-node compatibility, and key-residue gates remain blocked
  in `config/iroha-production-send-readiness.json`.
- Release integrity: the manual release workflow builds an explicit strict
  `v`-prefixed SemVer tag on `master`, requires a completed successful Android
  CI push run for the exact tagged commit, prohibits CI version mutation,
  verifies the single AAB and signing certificate, and emits pinned GitHub
  build provenance. All workflow actions are full-SHA pinned.
- Security lint: missing Credential Manager transport and trust-all TLS code
  are fatal. The passkey module exports the Play Services transport, while
  unused legacy Spongy Castle PKIX/PGP artifacts are replaced by only the
  provider primitives required by `fearless-utils`.
- Local validation: `scripts/validate-local.sh` runs the same checks and now installs Android platform/build-tools 36 to match the compile SDK, along with NDK r28 and platform-tools.
- WalletConnect/Reown SDK: BOM 1.6.9 resolves Kotlin 2.2 runtime metadata, so
  release builds use exact AGP 8.10.1 / R8 8.10.24 with compileSdk 36; the
  bundled UniFFI native libraries retain 16 KB page alignment.
- WalletConnect Pay: dependency excluded (until Reown publishes 16 KB-native builds) to avoid packaging the `yttrium-wcpay` 4 KB libraries.
- Google Play 16 KB page-size compliance: Native bundles rebuilt with NDK r28, `readelf -l` verification runs in CI on sr25519/toolChecker libraries, and the Play Console warning is cleared.
- Native crypto rebuild tooling: `scripts/build-libsodium.sh` now auto-detects the host-specific `toolchains/llvm/prebuilt` directory (darwin/linux/windows) so libsodium can be rebuilt on non-macOS hosts without manual tweaks.
- Utils composite build: public CI checks out `soramitsu/fearless-utils-Android`
  at `7500809f33243ee47ecb2ec8563fc284ac4de0d6`, exercises the adversarial
  derived-tree self-test, and verifies the exact origin, commit, index, and
  worktree against pinned `HEAD` plus the committed library-only patch with
  `scripts/ensure-fearless-utils.sh`. The guard rejects staged, tracked,
  untracked, partial-overlay, patch-overlap, and submodule drift without
  resetting a dirty checkout. CI forces the local composite include;
  `settings.gradle` retains the upstream Gradle 9 compatibility shims.
- The reviewed utils overlay treats the nullable `runtime_id` field as optional
  during Kotlin serialization. Legacy type registries that omit it now reach
  Android's existing live runtime-version fallback; null and present values are
  covered, and malformed object values remain fail-closed.
- External Play blockers: release/upload credentials are not stored locally;
  the required Firebase/upload-key GitHub Actions secrets must be configured.
  `android-release-build` and `android-release-signing` must each prevent
  self-review/admin bypass, use protected branches, and have exact, sorted,
  nonempty User-only reviewer allowlists with no reviewer ID shared between
  them. This creates two distinct environment approvals; two or more Users per
  list is optional availability redundancy. The release tagger also needs a
  locally verified OpenSSH Ed25519 signing key pinned by the exact fingerprint
  in `.github/release/android-tag-signer-fingerprints.txt`; the matching public
  key must be set in `ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64`. A mutable
  tagger-email variable is not sufficient. The exact
  version-code-230 source must merge and be tagged as
  `v4.2.0+android.230`. An authorized Play operator must then review Publishing
  overview, upload the final attested AAB to the existing beta/Open Testing
  release without changing countries or testers, submit it as installable, and
  smoke-test the public opt-in link after Google processes the update.

## Internal App Sharing Test Boundary

> [!WARNING]
> The IAS AAB is Android-debug-signed and Google re-signs IAS uploads. It cannot
> upgrade a Play-installed wallet. Never uninstall a funded wallet to install
> IAS, and never report a fresh IAS install as validation of production
> database migration or production release signing.

The IAS link is suitable for disposable-device feature and cold-start smoke
testing only: install/fresh launch, cold start, onboarding/navigation,
local-only flows using disposable test material, and screens independent of
the disabled services. Live Firebase, Google OAuth/sign-in, Google Drive or
passkey backup/restore, Play upgrade, production migration, and production
signing are unsupported in IAS. Production migration evidence requires
upgrading an unfunded test wallet that was installed from Google Play with a
newer Play-signed candidate delivered through the intended testing track,
while preserving its existing app data. Production signing evidence remains
exclusively the protected, certificate-pinned signed-release-artifact pipeline.

The workflow handoff name and verifier currently pin `4.2.0-ias` / `230`.
Changing either source-controlled version requires all IAS hardcodes to change
in the same reviewed commit; an older artifact must never be relabeled.

## Migration-Safe 4.2.0 Candidate

- `versionName=4.2.0` / `versionCode=230` is the first migration-safe testing
  candidate. Its initial destination is the existing `beta`/Open Testing track;
  Production promotion is a separate reviewed decision.
- Room accepts only the complete adjacent migration chain from database version
  9 through 77. Missing, duplicate, non-adjacent, or out-of-range edges fail
  before the user database opens, and no destructive migration fallback is
  configured.
- Released-schema and SQL preflights validate table/index/foreign-key shape,
  bounded row/cursor sizes, whole-database integrity, and wallet public/secret
  bindings before mutations. Unsafe input aborts startup instead of partially
  upgrading or wiping data.
- Legacy wallet rows that cannot be represented safely are retained in a
  recovery ledger. Unreadable or mismatched encrypted values are moved only by
  exact-ciphertext, durable quarantine operations; recovery state blocks PIN,
  export, signing, and ordinary wallet use rather than deleting evidence.
- Android Keystore-backed master-key attestation runs before the main wallet
  opens. Retryable secure-storage failure, permanent key loss, database-open
  failure, and process-restart-required state are classified separately. A
  latched storage failure invalidates the process-wide ready state.
- Account create/add-EVM/delete and TON Connect changes use bounded durable
  cross-store journals. Startup serially reconciles TON and account journals,
  inventories active/quarantined orphan namespaces, and reaches the heavy root
  activity only after database, storage, and recovery checks are coherent.
- The process-owned startup session serializes checks across recreation and
  competing launcher/deep-link intents, retains the newest ready intent, and
  prevents an Activity lifecycle cancellation from abandoning the shared
  startup result.
- Current local qualification evidence includes 676 root JVM tests (14
  explicit assumption skips), 310 included-utils JVM tests (10 explicit
  skips), and 111/111 connected tests on a clean disposable API 34 device (41
  app, 61 database, 5 common, 4 account). The migration compatibility profile
  also passes 39/39 on each of API 30, 31, and 36. A fresh source-bound release
  build and all artifact verifiers must still be repeated from the final
  merged/tagged commit; a locally built or pre-commit AAB is not an upload
  candidate.

## Runtime & Chains
- Default types/chains live under `runtime/src/main/assets`. Override types and debug chain discovery via `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_DEBUG_OVERRIDE`; release chain discovery is immutable.
- ChainRegistry coordinates runtime providers and connections. EVM handled via `EthereumEnvironmentConfigurator` and `EthereumConnectionPool`.

## Polkadot SDK Alignment
- Target: polkadot-stable2503 (prepared via override keys).
- How to align debug builds: set `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, and `CHAINS_URL_DEBUG_OVERRIDE` to registries validated against stable2503. See `docs/samples/local.properties.stable2503`.
- Optional: pin `shared_features` via `SHARED_FEATURES_VERSION_OVERRIDE=1.x.y` if required by the SDK combo.
- Utils integration: the build uses a pinned `soramitsu/fearless-utils-Android` checkout as a composite source dependency.
- Debug: run `./gradlew printPolkadotSdkAlignment` to verify effective overrides.

## Health & Risks (Snapshot)
- Security hardening delivered on 2026-03-05: TonConnect manifest origin validation, TonAPI header/logging protections, WebView mixed-content restrictions, AES-GCM encrypted preferences migration path, PIN lockout backoff, and internal-cache JSON export cleanup.
- TonConnect URL controls were tightened further: `tonapi.fetch` now enforces HTTPS + `tonapi.io` host allowlist with default TLS port, and TON API auth header host matching now correctly handles `*.tonapi.io` subdomains.
- TonConnect URL validator now rejects IP-like numeric host aliases (for example `2130706433`, `127.1`, `*.localhost`) to close localhost/private-network bypasses.
- TonConnect in-app browser navigation now enforces strict same-origin policy (scheme + host + port) for loaded dApps instead of host-only checks.
- TonConnect `tonapi.fetch` bridge is now backed by the hardened TON API HTTP client and limited to `GET` requests, executed on IO dispatcher.
- Encrypted preferences key recovery now regenerates the wrapped AES key if Android keystore unwrap returns invalid bytes, avoiding crash loops on corrupted key material.
- TON data path now uses `ton-indexer` first for account state, balances, seqno/public key, and account transaction history, with automatic fallback to TonAPI on indexer errors.
- TonAPI fallback for TON data reads is now pinned to `https://tonapi.io` (including cases where chain node configuration does not include a TonAPI URL), ensuring indexer-first + TonAPI-only fallback behavior.
- TON transfer send/fee/time flows now use ton-indexer JSON-RPC (`/jsonRPC`: `sendBoc`, `estimateFee`, `getAddressInformation`) as primary path, with TonAPI retained strictly as fallback.
- TonConnect jetton transfer payload lookup now uses ton-indexer first (`/api/indexer/v1/jettons/{jetton}/transfer/{owner}/payload`) and falls back to TonAPI only when indexer is unavailable.
- TON history cursor handling now supports `lt:hash` when indexer transaction IDs are available, enabling cursor-based indexer pagination before page-scan fallback.
- TON indexer fallback now preserves coroutine cancellation semantics (`CancellationException` is rethrown), preventing cancellation bypass during indexer outages.
- Code quality: Detekt enforced in CI. Several TODO/FIXME markers remain in features and common utils.
- 2026-07-10 verification: all shell policy/adversarial suites passed; the
  aggregate Android test graph passed 496 tests with 0 failures/errors (14
  intentional skips); app lint and debug assembly passed; the APK passed ZIP
  integrity plus negative MoonPay-secret and legacy trust-all-class scans.
- XCM release status remains fail-closed: 15 required executable routes are
  tracked, 34 advertised routes are discovery-only, and funded production
  evidence is still required before broad XCM release enablement.
- Incomplete UI/logic areas:
  - NFT details screen placeholders.
  - Staking validator oversubscription/slashed logic marked FIXME.
  - Substrate balance loader contains a hardcoded `chainAssetId` fallback.
  - Meta-account/EVM nullability handling flagged in multiple call sites (`accountId(chain)!!`).
  - Error text TODOs in `FearlessException` (needs resource-based messages).
  - Potentially unused network executor (`SocketSingleRequestExecutor`) flagged for deletion.

## Notable TODO/FIXME References
- Account:
  - `feature-account-api/.../AddressDisplayUseCase.kt` — adopt meta-account logic.
  - `feature-account-api/.../domain/model/Account.kt` — `cryptoType` optionality.
- Wallet & Balance:
  - `feature-wallet-impl/.../SubstrateBalanceLoader.kt` — avoid hardcoded `chainAssetId`.
- Staking:
  - `feature-staking-impl/.../Validator.kt` — oversubscribed/slashed logic.
  - `feature-staking-impl/.../StakingRelayChainScenarioInteractor.kt` — EVM nullability.
- Crowdloan:
  - `feature-crowdloan-impl/.../KaruraContributeInteractor.kt` — TODO marker.
- NFTs:
  - `feature-nft-impl/.../DetailsScreen.kt` — TODO placeholder.
- Common:
  - `common/.../FearlessException.kt` — resource texts for common errors.
  - `common/.../PreferencesImpl.kt` — listener GC TODO note.
  - `common/.../SocketSingleRequestExecutor.kt` — unused? consider removal.
- Misc:
  - `feature-account-impl/.../OptionsSwitchNodeContent.kt` — temporarily hidden button.
  - `feature-staking-impl/.../ExtrinsicBuilderExt.kt` — rename `createPool` with runtime 9390.

## Getting Started & Verifications
- Build app: `./gradlew :app:assembleDebug`
- Local checks: `bash scripts/validate-local.sh`
- Config: set MoonPay publishable keys, EVM provider keys, and history provider
  keys in `local.properties` or env vars (see README). Never configure a
  MoonPay server secret in the app.
- WalletConnect: ensure `WALLET_CONNECT_PROJECT_ID` is correctly provided; observe init logs.

Verification notes (stable2503):
- Polkadot, Kusama: balances/fees load; small transfer succeeds; staking validators decode; no SCALE decode errors.
- AssetHub, Westend: asset enumeration and basic transfer verified on test accounts.
