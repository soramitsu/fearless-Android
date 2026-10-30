> Historical qualification receipts named in this document are retained in the local consolidation recovery archive, outside the public source tree. They are not current release approval. See `docs/public-source-qualification.md` for the published source checks and remaining release gates.

# Status Summary

## Production release 4.3.1 (233): Asset Hub staking — 30 October 2026

4.3.1 brings back Polkadot and Kusama staking, which 4.3.0 could not offer because
the Asset Hub migration moved it off the relays. It is release 4.3.0 plus the
Asset Hub staking line (Codex `codex/android-validator-search-repair-20261029`,
selected commits, not merged wholesale), versionCode 233, database 84.

- Staking and nomination pools for the native DOT of Polkadot Asset Hub and KSM
  of Kusama Asset Hub: bond (no controller), nominate, bond more, unbond,
  rebond, withdraw, reward destination, paged payouts (`payout_stakers_by_page`,
  discovered on chain from `ErasStakersPaged` and `ClaimedRewards`), slash
  warnings from `ValidatorSlashInEra` and `UnappliedSlashes`, and the era clock
  from the relay (`Babe`, `Session`) with `BondedEras`. The unbonding period is
  read per account (`NominatorFastUnbondDuration` for nominators the runtime does
  not slash); when it cannot be read, the full `BondingDuration` is shown.
  Rewards are estimated from completed-era budgets, not relay inflation, and an
  unreadable history gives a zero estimate. Validator identities come from the
  People chains. The validator choice screens offer a retry when the validator
  list cannot be loaded, and Redeem waits for a successful ledger refresh.
- Activation: the catalogue marks those two assets with `"staking": "assetHub"`.
  4.3.0 maps that value to unsupported (shown with 4.3.0's own compiled mapping),
  so older builds keep staking off; removing it turns 4.3.1's Hub staking off.
  The relays never offer staking, pools or crowdloans again.
- A DOT or KSM staking choice saved before the upgrade opens the matching Hub;
  without the switch it opens the chooser.
- Signing: each signed extension is checked against the runtime's portable
  metadata (from fearless-utils `fc9719e`/`05d20f2`), and Hub staking calls
  require the complete signing context.
- Also ported: the validator search catalogue and cache ownership repair, and
  the JSON export reading the wallet once.

Not included: reward history on the Hubs (no indexer answers with Hub rewards)
and a Westend Asset Hub (not in the catalogue). Release record:
`docs/releases/4.3.1.md`.

## Production release 4.3.0 (232) — 29 October 2026

Master plus #1265–#1269 becomes the 4.3.0 production candidate (versionCode 232,
database 84). Networks and features that no longer work are removed:

- 40 enabled production networks that stopped producing blocks or have no
  reachable node, and every disabled entry; production keeps 50 of 116. Wallet
  rows for a removed network stay on the device.
- Relay staking, nomination pools and crowdloans on Polkadot, Kusama and
  Westend: their relays hold no staking state since the Asset Hub migration.
- The unreachable crowdloan contribution flow and its partner integrations;
  legacy crowdloan recovery remains.
- History indexers, staking indexers and nodes that cannot answer.

The release review also fixed: sends bound to the reviewed account and never
reported failed after the network accepted them; EIP-1559 tips and amount-aware
fee estimates; Google backups with the confirmed password and reviewed paths; an
added account no longer marking the wallet backed up; dApp-signing pause
rechecks; the stuck staking screen; exact payouts; and Guava 33.7.2.

Release signing pins the replacement upload certificate
`AD:6C:1D:07:…:57:C1:BD:E8`. Evidence and the release record:
`docs/releases/4.3.0.md`. Not included: Asset Hub staking, so Polkadot and
Kusama staking is unavailable in this version.

## Taira balance paging and build verification — 29 October 2026

Torii account, account-asset and definition lists now send `limit` and opaque
`cursor` tokens. The balance loader follows `next_cursor`, including empty
continuation pages, and rejects cursor cycles, missing items and scans exceeding
1,000 pages before publishing any balance updates. Saved balances remain intact
when any page fails. Unsupported offset/count-mode and asset/scope query options
were removed from the client contract; history continues using Torii REST.

JUnit BOM 5.11.0-M2 and 5.11.2 Gradle module metadata now have strict SHA-256 pins.
Their bytes match Maven Central's published hashes and its second endpoint;
strict local dependency verification accepts both. No verification rule was relaxed.

Local cursor and malformed-response regressions and the existing Iroha/Bitcoin
wallet tests are the validation gates for this follow-up. Live read-only Taira
replay accepted the new list requests and rejected the old query parameters.
Nexus remains covered by unit tests; no device or release artifact is qualified here.
The stale unbond confirmation regression also awaits its background fee projection
before asserting the rejected state; its no-sign/no-submit assertions are unchanged.

## Build 231 security port — 29 October 2026

The security and fund-safety fixes of the 4.2.0 build 231 Play testing line that
master did not have are ported; the WalletConnect part came with the entry below.

- Signing: the wallet's own EVM transfers are always signed with the chain id
  (EIP-155 also for legacy transactions). TON Connect `sendTransaction` requests are
  checked before they are shown and again when signed (mainnet only, `from` is the
  connection's wallet, unexpired, one to four well-formed messages), the signed
  message expires within five minutes, every message is signed exactly as sent,
  bridge requests reach the wallet at most once, a new connection is bound to the
  wallet whose key the reply gave, and the in-app bridge answers only the dApp's own
  main-frame page. The sign screen shows a decoded summary of what the request does
  and stays disabled until it exists.
- Startup and secrets: the exported launcher passes only the allowlisted VIEW data
  into the wallet; phrase, seed, keystore, import, PIN and recovery screens hold
  FLAG_SECURE through SecureScreenGuard; Android 12+ device transfer and cloud backup
  exclude all app data; keystore exports are shared from internal storage only;
  wrong-PIN delays escalate to five minutes on the since-boot clock and the PIN
  screen shows the wait; pre-installed wallet import is compiled off in release;
  Nomis credentials come from build secrets.
- Data APIs: the history client does not follow redirects, OKLink accepts only its
  HTTPS endpoint, GraphQL cursors and Reef reward pagination are bounded, and Nomis
  calls follow the scoring preference.
- Dependencies: Beacon (integration and SDK) is removed; Java-WebSocket 1.6.0
  (TLS hostname verification), bcprov 1.85, lz4-java 1.11.4 (maintained fork),
  commons-beanutils 1.11.0, Jackson 2.18.11, Guava 33.x, commons-lang3 3.18.0 and the
  Drive/Google API/OAuth 2.x clients, with locks and strict checksums.
- Solana and Bitcoin sends: the send screen accepts their recipients; Solana reads
  fail over between ordered endpoints and pace the rate-limited public node by host;
  Token-2022 sends are refused; broadcasts are classified before any release, a
  pending payment is released only on a proven expiry, and the history row closes as
  failed only when two distinct hosts proved it (see `docs/solana-send.md`).
- Iroha: transfer history comes from Torii REST (`/v1/accounts/{id}/history`, cursor
  paging, transfer filter); the app no longer calls the MCP endpoint, and the
  send-readiness audit fails on `/v1/mcp` or `tools/call` in main sources. Torii scans
  about 500 blocks per request, so after ten empty pages the screen shows what was
  found and keeps the cursor instead of an error, as the web wallet does.

Unit tests (debug, before -> after): app 189 -> 206, common 768 -> 846,
account 915 -> 953 and its API 106, TON Connect 96 -> 168 and its API 13 -> 23,
wallet 718 -> 752 and its API 130, WalletConnect 161, runtime 396 -> 407, staking
1,315 -> 1,318, core-backup 372 -> 378, core-db 521, core-api 133, runtime-utils
432, runtime-websocket 114; no failures, skips unchanged. Detekt is clean, but its
configuration excludes common, core modules, account, staking, wallet, runtime and app,
so of the changed modules it checks only TON Connect and core-backup.

Open: the replaced Play upload certificate is not pinned on master (owner decision);
the Nomis key that was in the source should be rotated; no device or emulator run
covers this port. The Taira balance paging defect is fixed in the follow-up above.

## dApp signing and cross-chain hardening — 29 October 2026

WalletConnect signs only with the account the session approved on the request's
network, refuses malformed params and dApps Reown Verify flags, binds the preview
and the signature to the request shown, and signs `polkadot_signMessage` wrapped in
`<Bytes>`. `polkadot_signTransaction` reads every field strictly, decodes the call
with the network's runtime and lays out the signed bytes itself, signed extensions
included (CheckMetadataHash mode and hash, ChargeAssetTxPayment), bound to the
metadata's runtime version. Ordinary polkadot.js requests now produce valid
signatures: the Polkadot, Kusama, Statemint and Westend parity payloads are rebuilt
byte for byte. Immortal eras, other genesis hashes or runtimes, unknown signed
extensions and calls that do not decode exactly are refused before any key is read;
a regression test reproduces crafted fields that spelled an XCM teleport payload.
Previews decode Polkadot calls (also inside batches, proxies and multisig), EVM
token calls and EIP-712 permits, show every cross-chain call with destination,
beneficiary and amount, mark XCM programs high risk, and refuse destinations that
no longer deliver: SORA bridge burns toward its halted parachains and Liberland,
relay reserve transfers of the relay token, and XCM to Moonbeam, Parallel,
Moonriver and the SORA parachains.

The production chain catalog and app configs are owned by the security team, and a
snapshot test pins the production XCM route membership. Type registry overrides are
debug-only, release builds drop plaintext nodes from the remote catalog, and a
release bridge flag other than false fails Gradle configuration. The retired CBDC
QR payment flow is removed. A cross-chain transfer proven included stays on record
until Done, so a confirm screen restored after another process death never resends
it; sending it again takes an explicit choice.

Unit tests: walletconnect 161, common 768, runtime 396 (10 unchanged skips), wallet
API 130, wallet 718 debug and 726 release, app 189, no failures; Detekt clean. Open:
there is no remote veto for dApp signing; the wallet does not compute RFC-78
metadata hashes itself. (The TON Connect and Beacon items are done; see the entry above.)

## dApp signing kill switches — 29 October 2026

Closes the open "no remote veto for dApp signing" item of the entry above. The
`feature_toggle` file has one veto per dApp family: `polkadot_dapp_signing_enabled`
(WalletConnect `polkadot_*`), `evm_dapp_signing_enabled` (WalletConnect `eth_*` and
`personal_sign`) and `ton_dapp_signing_enabled` (TON Connect `sendTransaction` and
`ton_proof`). They follow the send switches' rules: on by default, an explicit
`false` is remembered across restarts, offline launches and fetches without the key,
and only an explicit `true` lifts it. A paused request is shown with the reason and
Sign disabled; the wallet refuses it right before signing and the dApp gets an
error, also for an `eth_sendTransaction` signed before the pause. The owned-config
verifier refuses switch values that are not JSON booleans. See
`docs/remote-feature-switches.md`.

## Substrate node failover — 29 October 2026

Substrate networks again leave a catalog node that keeps failing while Auto
select is on, the default. Each network connected only to its selected node,
and only the node list screen could change it, so the dead first nodes left by
the Blast and Dwellir shutdowns kept Polkadot, Kusama and the Asset Hubs offline.
`ChainConnection` counts failed connection attempts per logical binding from
socket transitions. After three in a row, `ConnectionPool` admits
`ChainDao.selectNextCatalogNode` with `admitObserved` from the settled read
behind the failing binding. The DAO moves the selection by rowid to the next
catalog node, wrapping to the first. Custom nodes are never replaced or chosen,
Auto select is rechecked inside the writer, and the switch never retires a
pending explicit selection or catalog replacement; one admitted later wins.

Host tests for core-api, core-db and runtime pass: 1,063 with 14 inherited skips
(baseline 1,041), including 22 new failure-count, writer and pool cases. A
schema-84 SQLite replay of the new queries passes. No device run.


## Local app instrumentation profiles — 26 October 2026

The full local runner now distinguishes ordinary portable tests, mandatory
host-driven journal process transitions, and a separate v1 presentation artifact.
Historical installed-device and private-validator qualifications remain explicitly
inventoried and unqualified by the migration run. Safety admission, zero-skip
verification, required identities and count floors remain enforced. See
`docs/android-app-instrumentation-profiles.md` for invocation and evidence scope.

Current-schema fixture expectations use schema 84 and preserve the address-only
Bitcoin testnet catalog row. Disposable fixtures bind the real emulator AVD and
UID; system effects tests disconnect and restore emulator battery reporting.
The focused 36 app regressions pass with zero failures/errors/skips. Final
release validation additionally requires the complete local wrapper, the five
v1 UI checks and seven actual process transitions with exact source/APK digests.

## Iroha read response validation and CI ownership — 25 October 2026

Iroha MCP decoding now checks explicit wire version and string request identity
before Gson can supply defaults or coerce numeric IDs. Responses require exactly
one object result/error; MCP null success is deliberately rejected. History
propagates transport, cancellation, server and malformed-payload failures while
preserving valid empty pages. Base URLs reject missing hosts, userinfo, query or
fragment and allow plaintext only for local HTTP. Pagination, row mapping,
network/asset profiles and production signing admission are unchanged.

Six baseline regressions reproduced the prior behavior; actual Gson decoding
also reproduced missing-version and numeric-ID acceptance. All 24 focused
read-path tests now pass, including five raw JSON adapter tests. Aggregate
`runTest` and Detekt pass: task XML records 6,460 passed, no failures/errors and
18 unchanged skips (runtime-sync/runtime-fetch fixtures, unsupported JVM sr25519
cases and opt-in live RPC checks). The 765 common and 713 wallet tests have no skips.
Unchanged Gradle tasks may reuse validated results. Receipts and failed baseline
runs remain in the local consolidation archive.

The staged-Iroha CI verifier now runs app-owned runtime source/digest checks and
their adversarial suite instead of removed external-utils preparation. Complete
staged bridge checks pass online then offline, including 70 materializer cases,
15 source-ownership cases, 29 compact-vector negatives and 11 boundary cases;
readiness and task-membership audits pass. Production Iroha deployment, live
provider and signed-device qualification remain separate open gates.

## CI fixture scheduling — 25 October 2026

The send-screen fixture now drives a serial Main test dispatcher while awaiting
real background work. Recovery cursor tests await the previous reconciliation
job before issuing the next tap, and the create-pool fixture uses the existing
MockK version for suspended Kotlin `Result` answers. Cleanup restores dispatcher
and exception-handler globals even when an assertion fails. These changes repair
observed fixture races and a boxed-result failure without changing runtime code
or dropping assertions. All 1,315 staking and 710 wallet tests pass with no skips;
Detekt and the aggregate `runTest` pass. Fresh hosted CI remains required.

## Pool setup test cleanup — 25 October 2026

The pool fee test fixture now awaits its ViewModel's background cancellation
before resetting the Main dispatcher and uncaught-exception handler. A controlled
Default-thread cleanup regression fails against cancel-only teardown and passes
with the completion wait. All six fixture tests and the full 1,315-test staking
module pass with no failures or skips; Detekt passes. This repairs a verified
source of asynchronous test leakage without changing staking runtime behavior.
Earlier intermittent failures remain recorded; the next CI run is still required.

## Avatar source regeneration — 25 October 2026

The Android-owned `scripts/generate-avatar-brand.py` preserves the reviewed
PR154 badge-to-avatar generator with only its output path adapted to
`runtime-utils`. Regeneration from the pinned approved badge produces a
byte-identical `WingedSunClip.kt`; runtime code, artwork and source digests stay
unchanged. Source ownership verification still passes.

## Canonical Ed25519 verification — 25 October 2026

The app-owned runtime preserves Utils PR154's missing verifier guard and five
RFC8032 regressions from `db86fdf14ba0d32a28df70ef97406f7317150b06`.
Verification rejects malformed lengths and noncanonical S scalars before the
existing provider; deterministic signing bytes remain unchanged. This guards
callers of this verifier without replacing EdDSA or changing native artifacts.
All ten focused canonical/signing/prepared-signature tests pass, with no skips;
owned-source verification and all fifteen adversarial manifest tests pass.
The original import provenance is retained, with current owned digests updated.

## Universal recovery integration — 25 October 2026

Public recovery work from `8b38ca5ab88e605cd51cdbcf450bfe822080c4d7` is adapted
onto platform-owned Android sources. Verified wallet material can enroll missing
Bitcoin testnet and TON accounts; existing roots, independent aliases and current
startup/journal/catalog safeguards are preserved. Alias comparisons are case
insensitive without rewriting stored IDs. Address-only testnet catalog rows
preserve keys, and SORA Nexus details remain local with no runtime activation.
BIP39 TON recovery stays separate from signing validity and preserves native TON.

Validation: the reconciled focused host suites contain 3,478 tests, with 3,464
passed, 14 inherited opt-in skips and no failures. The final account rerun passes
all 915 tests. Debug APK assembly, repository Detekt, AndroidTest compilation,
source/config ownership, dependency/binary provenance and XCM audits pass.
Initial stale fixtures and the corrected replay fault-position failure are
retained in evidence. The two mnemonic restore and two adapted portfolio
migration device tests compiled but were not run. Existing device, signed/store
release and funded/live-service qualification gates remain open; XCM stays off.

## Android platform ownership — 25 October 2026

Runtime and guarded WebSocket source, native rebuild inputs, core/UI/networking,
XCM, backup, remote config and artwork now belong to this Android repository.
Internal published artifacts, composite checkouts and private Nexus are removed.
Source manifests, release/IAS guards and CI bind the complete app tree. Current
config uses public master; historical migration bytes and legacy host availability
remain preserved. Consolidation does not enable XCM, rebuild native libraries or
qualify a signed distribution. Validation passes: all 6,429 host tests (6,411
passed, 18 inherited/opt-in skips, no failures), Debug APK assembly, app Debug lint
(no errors/fatal findings; 87 warnings), Detekt, and all eight release XCM
configuration/catalog tests. Source/config ownership, native provenance and
release/IAS policy regression checks pass. Failed intermediate attempts and one
inherited staking-test timing failure remain in the consolidation receipts; no
claim is made that the intermittent test defect or device/store gates are closed.

## Runtime dependency pins — 25 October 2026

The release candidate now pins the published runtime sources required by its socket configuration API: fearless-utils `3d22dad159ca5304d199acd08c5a622dea3c1577` (tree `165380bed84bb1dd69f868660e57f9ebca899932`) and guarded WebSocket `18a94e2db0f67a8387414bc7db53aac48ded18d7` (tree `e5bd65d129bd50463eeeb41d8239b28ed2a3dc1f`). The Utils tree is identical to the source used for the prior 4,592-test XCM release verification. The runtime manifest, source guard, workflow jobs and current build instructions now agree. Historical validation snapshots retain the revisions they actually tested.

Validation passes: all 14 runtime pin tests; pristine-source verification before and after Gradle; deterministic/adversarial source checks; the dependency handoff export and its 25 negative scenarios; all XCM audits and self-tests; and the actual release-variant XCM configuration/catalog contracts (8 tests, no failures or skips, `CI=true`, 288 tasks executed). XCM and bridge submissions remain compiled off. The prior 4,592-test debug result uses the identical Utils source tree; this pin correction does not claim a new full-suite, device, or distribution qualification.

## Native migration fixture correction — 24 October 2026

Full-v10 passes 6,161 unit tests with zero failures and 26 inherited skips; eleven lint scopes and Detekt are clean. The subsequent API36 core run executes all 459 methods: 455 pass and four fixture failures remain, with exact wallet bytes, 55 prior proofs, APK identities and clean process settlement preserved.

Three current-Room fixtures still expected version83 or registered only the82→83 edge. They now use the current database version and the actual83→84 migration while retaining endpoint ownership, exact DDL and two-open checks. The legacy-node test now separately checks a real retained-parent staging-budget fallback and acceptance of SQL-only cache bytes under a small TON copy budget. It preserves archive bytes, selected/custom node tuples, parent rows, transaction and temporary-object checks. Production code and schemas are unchanged. Fresh compilation and native rerun remain required; the historical35 and caller14 suites and broader readiness gates remain open.

## Alert test job-capture correction — 24 October 2026

Full-v8 at 23f7eafd74533876e34ceba0df62bde37b21f4eb is preserved with 6,159 passes, one failure and 26 inherited skips across all 21 unit modules. The only failed method is the healthy current redeem alert fixture: its child lookup ran after scheduler settlement, when the actual job could already have completed and left its owner. All eleven lint scopes have no errors or fatal findings, detekt is clean, compilation reports no errors, and all four APKs are fresh. Root and independent reviews retain the failure, exact clean source and settled processes; full-build acceptance is withheld.

The test-only correction captures the real queued child before advancing the StandardTestDispatcher. All 24 existing test bodies and assertions remain unchanged. One regression verifies the shared capture helper, completion, removal from the owner, one validation and one navigation. No fake job, retry, product change or weakened assertion is introduced. The next full build reruns the existing clean-first unit/lint/detekt/generated-graph/coverage/APK checks with the same serial resource controls and a separate per-run daemon registry.

The 459-method Room84 native suite and fourteen caller lifetime methods still await successful current-build acceptance. The caller replacement-dialog failure, relay call-recipe semantics and broader action/quote authority, historical/runtime matrix, installed recovery, rendered signing, iOS/extension, physical/design and publication remain open. The separate 35-case released-schema cohort is not included in the 459-method selection. The full audit remains incomplete.

## Final test fixture formatting correction — 24 October 2026

The second full build at f1414cc41bf536267f25c9e37b59d901d154e543 is preserved with 6,160 unit passes, 0 unit failures and 26 inherited skips. Its only failed task is detektAll, with five function-signature formatting findings in two TON test helpers. The prior Room84 Android-test compilation errors are resolved. Root and independent preservation verify exact clean sources, 3,725 controls and settled owned processes; full-build acceptance remains withheld until the static-analysis check passes.

The correction only changes whitespace around the meta and dapp fixture expression bodies. All non-whitespace source bytes, thirteen test identities and assertions remain exact. The next full run keeps the clean-first task order, forced compilation, unit/lint/detekt/coverage/generated-graph/APK scope and serial resource controls. The 459-method Room84 native suite and fourteen caller lifetime methods are prepared against the preserved API36/API34 profiles and await a verified current build. Relay minimum-bond fee semantics, broader action/quote authority, historical/runtime matrix, recovery, rendered signing, iOS/extension, physical/design and publication remain open. The full audit is incomplete.

## Full build findings and reviewed corrections — 24 October 2026

The complete run at ad380d2f7e3162a561e22d56e2093325b85ddc39 finished with 6,160 unit tests passing, no unit failures and 26 inherited skips across 701 fresh XML reports. The only failed tasks were detektAll (174 findings across nine files) and core-db Android-test Kotlin compilation (four invalid AppDatabase.use receivers, producing eight diagnostics). Root and independent reviews preserve the failures, exact clean sources, 2,977 controls, retained outputs and settled processes. The retained core database test APK is stale and cannot qualify the current migration tests.

The static-analysis correction replaces wildcard imports, applies formatting and removes an unused TON request AccountRepository constructor dependency and its sole fixture argument. All 41 affected test method identities and assertions remain. The four Room84 tests now close their AppDatabase instances in explicit try/finally blocks, preserving every assertion, rollback/retry operation and all eight production reopens. These ten source changes require fresh compilation and checks; no suppressions, exclusions or production database schema changes were introduced.

The next full rerun explicitly orders the existing clean task first, because runTest's unordered clean dependency deleted the first run's generated detekt report after analysis. That report was separately archived before deletion and remains bound to the preserved failure. The new run must retain fresh static-analysis, lint, unit, generated graph, coverage and APK evidence before the 459-method Room84 native selection can execute. Installed dialog lifetime, broader quote/action authority including relay minimum-bond rereads, historical/runtime matrix, recovery, rendered signing, iOS/extension, physical/design and publication remain open. The full audit is incomplete.

## Green unit checks and Room84 native coverage — 24 October 2026

The eighth integrated run at 5df13103b9e79a8274b04fd56578c12d0d19bd20 passes 1,502 methods with zero failures or skips: staking 1,313 and app 189. All 84 focused unbond and seven balance-publication methods pass, and app Android-test Kotlin compilation succeeds. Root and independent result reviews verify exact clean sources, 108 fresh reports, 2,810 controls, unchanged Room84 and settled owned processes.

Four new core database instrumentation methods now cover direct83→84, actual production82→84 and76→84, and transaction failure after the real ALTER and user_version change. They require exact legacy price bytes/storage types, NULL fiat identity, protected SQL rows and controlled preferences, rollback and same-file retry, exported-schema validation and stable production reopens. Existing tests remain unchanged. The explicitly mapped native selection contains 459 methods across34 classes:449 current successors of the old443 cohort, six existing preference DAO methods and four new currency methods. These additions require current APK compilation and native execution; host fixture checks are only preparation.

Next is the complete unit/lint/detekt/generated-graph/coverage/APK build and exact-source native verification. Current production and unit-test sources remain the green V8 versions. Broader quote/action authority including relay minimum-bond rereads, installed dialog lifetime and recovery, historical/runtime matrix, rendered signing, iOS/extension, physical/design and publication remain open. The full audit is incomplete.

## Unbond warning amount, exact input and test cleanup — 24 October 2026

The seventh integrated run at 2e1b756054cb0ecc2b2921d0bfb65dff04ea347e is preserved at 1,486 passes, six failures and zero skips. All 21 parachain quote methods pass, including all seven earlier failures. App passes 189 and Android-test compilation succeeds with no compiler diagnostics. Root and independent preservation verify 107 fresh reports, 2,654 controls, exact sources/Room84, clean repositories and settled owned processes.

Three actual warning-callback failures confirm that amount auto-fix could route or submit ten units using the completed one-unit fee. Two input failures confirm that fractional token units and invalid precision could acquire review authority. The correction now restarts changed input under the same authority, selection and wallet locks, clears the old fee and requires a fresh completed quote plus another explicit action. Confirmation returns to the originating amount screen and cannot create a review for a different amount. Bounded exact conversion rejects fractional planks, malformed precision and excessive decimal work; fiat display uses the same parser.

The original 11 authority and four warning baseline methods remain unchanged. Nine added methods cover stale and concurrent warning input changes, numeric equality, safe parsing, actual Select and Confirm fresh-fee recovery, and invalid-input recovery. The unbond focused set now contains 84 methods awaiting this rerun. The sixth failure is a MissingMain completion-handler error in StakingBalancePublicationTest teardown; its fixture now waits for all owned view-model jobs before resetting Main. All six original test bodies remain, with one added delayed Default-to-Main cleanup regression. Production balance code is unchanged.

Next is the staking/app unit and Android-test compilation rerun, then final full build/lint/generated graph/APK verification. Full-build tools and the current singleton-reader graph checker are prepared; ten structural graph controls pass against frozen fixtures, which is not fresh-build acceptance. Relay minimum-nominator-bond rereads and broader action/quote authority, native Room84 migrations/writers and dialog lifetime, historical/runtime matrix, recovery, rendered signing, iOS/extension, physical/design and publication remain open. The audit is incomplete.

## Parachain unbond quote context and remaining input/fee baselines — 24 October 2026

The sixth integrated module run at 66876c686cf6612e6fbd236bca93f4b20b4d5c8f settled at 1,466 passes, seven quote failures and no skips or compiler diagnostics. All twelve original unbond failures are resolved; 49 focused caller/builder/authority/actual-service methods pass. App passes 189 and Android-test Kotlin compilation succeeds. Root and independent reviews retain 106 fresh XML reports, 2,499 controls, exact sources/Room84, clean repositories and settled processes.

The seven actual quote failures concern captured chain/wallet/account/state identity, wallet/selection ABA, overcommitted amounts and lost cancellation identity. The reviewed correction binds reads to the captured context, rejects invalid values, preserves latest-state combination and forwards each source's original failure, including actual job self-cancellation before its peer emits. It retains the eight original test bodies and adds thirteen regressions, including token precision, malformed candidates, delayed updates and cancellation. No current-round read is needed to subtract both ready and pending reservations. A null candidate retains the existing explicit no-selection zero contract; this does not assert a wallet balance.

Four actual warning-callback tests and two amount-precision tests establish separate unchanged-production baselines. Warning auto-fix must not submit or open confirmation with a fee for a different amount. Unbond capture must reject fractional token units and malformed precision instead of silently truncating. Three warning and two precision failures are expected but have not yet executed. The original nine authority test bodies remain unchanged.

The next module attempt verifies the quote correction and these baselines. Minimum-nominator-bond rereads between quote and signing remain an unqualified source follow-up. Full build/lint/generated graph, native dialog lifetime and Room84 migration/writers, broader action/quote authority, historical/runtime matrix, recovery, rendered signing, iOS/extension, physical/design and publication remain open. The audit is incomplete.

## Unbond review and final submission authority — 24 October 2026

The fifth integrated run at 8d1732fa7b53efcdb27125beb8feb0d177dddcaf is preserved at 1,995 passes, twelve unbond failures and zero skips. All seventeen actual FeeLoader methods pass, and app Android-test compilation succeeds. Root and independent checks retain 180 fresh reports, 2,198 controls, exact sources and Room84, clean repositories and settled processes.

The composed correction captures source/input/wallet/selection authority across fee estimation, validation and confirmation. Unavailable relay balances cannot become zero; parachain requests retain their collator. Confirmation carries the original in-process review and rejects mismatched or restored unbound payloads. Fee, fiat, balance and origin presentation revoke when that review changes. Quote and quick-choice values clear before cancellation cleanup and reject late completions. The return-to-review explanation covers all eight shipped language resources.

The interactor uses wallet-bound public fee preparation and the existing authorized key/sign/send route. Final primitive guards serialize with source/input/selection changes, bind the exact transaction intent and admit at most one physical submission. Blocking output runs after admission outside selection locks. Fifteen original baseline bodies remain unchanged; fourteen caller, nine authority, ten actual-service and eight actual parachain quote methods compose a 56-method focused set. Execution is pending; the quote implementation is intentionally unchanged for its eight-method baseline and six negative failures are expected.

A separate review found that existing CrossExistential warning auto-fix may change the amount and encoded call while retaining the prior fee. Its actual warning-callback baseline is next. Full build/lint/generated graph, native dialog lifetime and Room84 migrations/writers, broader quote/action coverage, historical/runtime matrix, recovery, rendered UI/signing, iOS/extension, physical/design and publication remain open. No full production-readiness claim is made.

## Fee request lifetime correction and unbond compile repair — 24 October 2026

The fourth integrated run at 76784b530e44b9b260488097f80333fccced359d passes 309 methods and exposes five FeeLoader defects. All 189 app methods pass, including MainHub unknown/empty-cache funding; Android-test compilation succeeds. The new unbond fixture fails compilation because its BaseViewModel else branch does not narrow to SelectUnbondViewModel. No unbond runtime result is claimed. Root and independent reviews retain the failed attempt, verify 2,039 controls and 35 fresh reports, exclude stale staking reports, and confirm exact sources/Room84, clean repositories and settled processes.

The fee correction ties each publication and retry/cancel/completion callback to its current request, revokes the previous request before cancellation and handles synchronous LiveData observer replacement. Token-read failures become an explicit Error, cancellation keeps its original reason, known zero fees remain available and negative fees are rejected. A new invalidate operation clears a fee immediately while the owner prepares a replacement. Twelve actual provider methods include all seven unchanged baseline methods and five added regressions. Existing moved-to-Asset-Hub/error/retry behavior also requires rerun. Caller selection changes still require explicit invalidation; this does not qualify every owner.

The unbond fixture now narrows its Select branch explicitly and implements the new fee invalidation contract as a controlled no-op. All fifteen baseline methods and assertions remain intact, with production unbond code unchanged. Next is actual module execution to establish those remaining findings and verify the fee fixes.

Full production-readiness remains open, including broader quote/action authority, full build/lint/generated graph, native migration/writers and dialog lifetime, historical/runtime matrix, recovery, rendered signing, iOS/extension, physical/design and publication gates.

## MainHub availability and fee/unbond baselines — 24 October 2026

The third integrated run at f4b46462eedc2656ccc8a4f7f283e137d0fac5dc settles with 2,170 passing methods and one newly observed MainHub failure. Core-api passes 120, wallet 634, staking 1,228 and app 188 with one failure. All six earlier failures now pass, and app Android-test Kotlin compilation succeeds. Root and independent preservation verify all 1,772 controls, 200 fresh XML reports, exact tracked sources and Room84, clean repositories and settled owned processes.

MainHub now distinguishes unavailable funding (including an empty cache) from explicitly known zero funding. Unknown funding remains blocked with the localized unavailable reason and Unavailable capability; known zero remains SetupRequired, and known positive funding retains existing routing. The original two regression methods are retained with explicit availability assertions and empty-cache coverage. The correction awaits execution.

Twenty-two actual FeeLoader and unbond baseline methods are integrated against unchanged target production code. They cover overlapping requests, read failures, cancellation, delayed validation/builders, amount/account/balance changes, relay availability and parachain collator/context retention. They include bounded cleanup for held work. This is test preparation, not executed acceptance; failures must be retained before correction.

The full audit remains incomplete. Full build/lint/generated graph, native dialog lifetime and Room84 migration/writers, broader quote/action ownership, historical/runtime coverage, recovery, rendered/signing flows, iOS/extension, physical/design and publication remain open.

## Quote RPC errors and actual module rerun — 24 October 2026

The second integrated run at 07f0cb640296c0eef33f892264a5f638cf48c14b finished in 2m8s / 696 tasks: 2,589 passes, six failures and four inherited skips. Core database tests pass 516 with four skips; swap passes 30; app compiles and passes 187. All 18 earlier staking failures are resolved, including the unchanged FreshTap method, and 1,227 staking methods pass. Wallet passes 629; four newly reached quote fixtures fail from nested Mockito stubbing and one header fixture lacks a plural resource. App Android-test compilation exposes one nullable known-total fixture. Root and independent reviews preserve exact source, all 1,468 controls, 252 fresh XML reports, unchanged Room84 and settled processes.

The remaining SORA failure is a production RPC issue: liquidityProxyQuote discarded response.error. A response mapper now checks the server error before mapping its result, including a response containing both an error and an apparent quote. Existing request parameters, delivery mode, absent-result behavior and explicit zero results stay unchanged; the conversion caller separately rejects a nonpositive rate. Three actual RPC regressions supplement the unchanged failed SORA assertion. Wallet fixture corrections preserve their assertions, and the rendered portfolio fixture now explicitly requires its known total. The next run covers the changed core RPC module, wallet/staking failures, app instrumentation compilation and two new actual MainHub baseline methods. The MainHub baseline retains zero/positive routing controls and expects an unavailable holding to be described as unavailable; its production source is unchanged and a failure is expected until observed and corrected.

This checkpoint is not production readiness. Full build/lint/native, unbond and MainHub availability/context, Room84 historical migration/writers and broader recovery/design/signing/publication gates remain open.


## Integrated unit failures and estimate availability — 24 October 2026

The first actual twelve-module run at 7c5bdaf5c2f79f31773785b79bb9e4376822d70a produced 3,975 passes, 43 failures and 14 skips in 4m7s / 744 tasks, plus one MainHub nullable-amount compilation error. Root and independent reviews preserve 396 fresh XML reports, all 976 controls, unchanged tracked sources and Room84, clean dependencies and settled compiler processes. The failed attempt is retained and does not establish readiness.

The correction updates the prepared 231 database ledger to Room84 and requires known positive transferability for MainHub funding eligibility. Test fixtures now supply required identities, resources, icons and LiveData; a concrete staking state replaces an unmockable sealed base, nested stubbing is separated, and the JUnit method returns Unit. Exact cancellation assertions retain marker-bearing injected reasons. Search uses the current catalog configuration, and swap validation asserts both the title and explanation. Existing test names and staking assertions remain intact; the FreshTap test is unchanged because its pre-entry errors trace to incomplete controller construction.

SORA estimates now reject missing or nonpositive conversion quotes, absent/invalid payout samples, invalid statistics and nonfinite arithmetic instead of reporting zero earnings. Known zero payouts, zero requested amounts/periods and the existing valid formulas and averaging policies remain supported; 26 actual socket/factory/calculator/provider tests accompany the change. Setup staking rejects a missing existing bonded amount before minimum lookup. Controller fee checks retain the strict known-balance boundary, preserve unavailable reads and reject negative fees; six regressions cover availability and cancellation. All new work awaits the targeted rerun.

The accepted historical baseline remains 5,398 passes / 26 skips and the focused currency subset remains 372 unique methods / 582 executions. Native v5 remains 8 passes, 1 failure and 5 unrun. Unbond fee/submission authority, full lint/build/generated graph, Room84 native migration and historical writers, broader historical/device coverage, recovery, rendered/signing flows, iOS/extension, design and publication remain open.



## Integrated test and remaining display corrections — 24 October 2026

The fourth module compile corrects the prior wallet/staking production callers and reaches further integration scopes. It still fails: 236 diagnostics across five files, primarily absent signing-test dependencies, two retired BalanceList fixture constructors, one typed fixture copy, one known-zero staking fixture and two liquidity display nullable calls. The failed run and unchanged source/Room84/settled-process evidence are preserved.

The successor supplies existing shared test dependencies, migrates fixtures without deleting their expectations, preserves unknown quantities in liquidity removal and asset management/selection, and adds actual presenter/ViewModel regressions. Manage assets uses canonical qualified fiat; absent or foreign quotes never invent zero. A final asset-picker adapter also needed to preserve unknown through its Compose amount label. Staking reward estimates now clear before cancellation cleanup, reject late calculation results and recover on a new known balance. Staking balance read/factory errors publish unavailable values and revoke actions while allowing explicit refresh. Seventeen actual provider/ViewModel regressions cover these paths. Next is actual module unit-test execution, followed by full lint/build/native checks. No new runtime qualification is claimed.

## Remaining integrated compiler callers — 24 October 2026

The third compiler pass at 52959ff80 failed with 23 diagnostics in eight files after 2m30s / 602 tasks. Shared-reader/account dependency compilation and swap/market production compilation now pass. All 923 control pins and tracked sources remained exact, Room84 is unchanged, and owned compiler processes settled; both independent and root reviews preserve the result. No new unit/native acceptance is claimed.

The successor corrects legacy nullable balance displays and hidden-asset ordering, clears dependent fiat for unavailable detail amounts, and updates backup fixtures to the captured Ready portfolio API. The TON fee path and controller confirmation receive explicit availability guards and cancellation handling. Existing successful fixture assertions remain in place, with regressions for unknown values and recovery. The full module/unit/lint/native checks and all broader audit gates remain open.

## Nullable balance callers and shared reader boundary — 24 October 2026

The second integrated compile at e6760f3ff failed with 46 diagnostics after 2m4s and 578 tasks. The source, Room84 schema and 904 build-control pins remained exact, and the owned compiler processes settled. This result is retained separately from the earlier failure. No unit or native success is inferred.

The shared account asset reader, quote selector and cached domain mapper now live in wallet-api, with byte-identical source and tests, so account implementation can consume them without a dependency cycle. Recording DAOs implement all abstract reads and reject unintended numeric access. The wallet interactor delivers empty revocations before replacement cleanup and preserves independent current-producer cancellation, including self-cancellation; 13 prepared regressions cover both public observers.

Nullable quantities now propagate through staking displays, amount models, quick inputs, swap and market guards, and transfer validation. Unknown origin or fee balances have an explicit non-dismissible validation result. Fee failures cannot create unreserved quick-fill amounts, and cancellation remains cancellation. Send confirmation clears its fee display and disables actions on lost balance availability, recovers after a known balance returns, and checks again before creating a transfer. Claim and receive callers preserve unavailable balances. Original successful submission fixtures now explicitly represent funded balances rather than an empty, unknown row; their original test bodies remain unchanged.

The composed source and added regression tests require a fresh integrated compilation and execution. Accepted baseline evidence remains 5,398 JVM passes with 26 inherited skips and the focused currency cohort of 372 unique methods / 582 accepted executions. Native replacement-window diagnosis, Room84 migration and historical preference writers, remaining action/quote lifetime checks and legacy displays, the broader historical/runtime matrix, recovery, rendered signing flows, iOS/extension, physical/design and publication gates remain open. This checkpoint does not establish production readiness.

## Integrated currency caller corrections — 24 October 2026

The first integrated906950419 module compile failed after1m54s (536tasks); no unit/native success is claimed. Its source/dependency checks and settled compiler cleanup are independently verified. Generated Room84 changes only nullable token_price.fiatCurrencyId plus version/identity metadata; all24 other tables are unchanged. The preceding full catalog build remains5,398passes/26inherited skips, and the accepted currency host cohort remains372unique methods/582accepted executions.

The correction replaces cache read/rebuild writes with field-specific fresh provider updates, preserves metadata-only writes, supplies account-api test dependencies, and adapts original DAO/aggregate fixtures. WalletRepository now consumes captured wallet/catalog/currency snapshots, clears delivered loading/deletion/failure values, preserves exact damaged-row precedence, and uses one captured pair of known transfer balances. Fee-read failures cannot become zero. Canonical mappers retain quote identity. Default amount/fee actions reject unknown balances; portfolio rows and token groups retain unknown quantity, cannot collapse it as automatic zero, and display unavailable rather than a partial total. Regression tests accompany these changes. Receive-only missing rows remain unvalued and cannot prove a zero holding. Review also closes negative load-failure filtering and same-ID catalog replacement: failed quantities remain rows, and a changed precision/price configuration revokes previously captured amounts until refresh.

Next: compile the actual composed source and tests, correct remaining callers, then execute module tests, lint/generated graph checks and native Room/caller qualification. The native replacement-window failure and its diagnostics remain open. Broader historical/runtime matrix, action authority, recovery, rendered/signing, iOS/extension, physical/design and publication gates remain open; this checkpoint is not production ready.


## Currency identity and availability integration — 24 October 2026

The catalog baseline da93900de passes 5,398 JVM cases with 26 inherited skips and eleven scoped lint checks. Its native successor passes eight methods, fails replacement-dialog authorization before focus, and leaves five methods unrun. The failure and protected-data checks remain retained; its exact authority predicate still needs diagnosis. The native fixture now records individual request, epoch, selection, screen and owner predicates while retaining every existing assertion and timeout.

Currency integration is in progress. Room version84 adds nullable fiatCurrencyId without rewriting legacy quotes, registers the contiguous83-to84 migration, and includes that SQL in rollback previews before historical preference writers. Existing quote bytes remain preserved; absent identity cannot prove a quote belongs to the selected currency. The migration host tests and four strengthened native failure-boundary cases are now in this checkout. The composed reader/domain/model changes, captured portfolio callers, shared components, eight locale resources and their focused/module tests are now applied from an exact141-file map. This includes all12 existing total/asset-balance ViewModel consumers. One-wallet ordinary read failures are isolated before the detail producer; the corrected17-case roster run is in progress. Generated Room84, full module tests, real native openings and the remaining currency readers, writers and callers still require validation. No new production-readiness claim is made.

## XCM review follow-up — 24 and 25 October 2026

The remaining wallet-side and engine findings of the XCM review are fixed on top of the route retirement below. XCM execution stays compiled off in release builds and no route is approved, so none of this changes what a release build can send today; it removes the ways a requalified route could lose funds.

- Cross-chain validation no longer takes an unknown value as zero. When the destination copy of the asset is missing or ambiguous, the recipient's balance cannot be read, or a minimum (origin, destination or fee asset) is unknown, setup and confirmation stop with a localized error. Setup and confirmation find the destination asset with the same symbol normaliser (`normalizedAssetSymbol`, now the only one in the XCM code), so an `xc`-prefixed destination asset is no longer skipped at confirmation. Confirmation counts only what arrives after the destination fee, so an amount that covers the minimum only together with the fee is rejected. A transfer on one network checks the asset that is sent instead of the first asset with the same symbol. `CrossChainTransferValidationTest` covers each case.
- Authorized mutations (XCM, the reviewed bridge, pool join and create, Polkamarkt) are never signed with an immortal era. `MortalityConstructor.construct` still falls back to an immortal era when the chain state cannot be read, because fee estimates and legacy signing use it; `ExtrinsicBuilderFactory.createForAuthorizedSubmit` now refuses that fallback, so the change is scoped to the authorized submit path. The mortal era's birth block and period are reported to the submission tracking as the window of blocks the transaction can be included in.
- The cross-chain confirm screen shows the wallet that signs ("From") and binds it: the draft carries the wallet whose balances setup validated, the Confirm button stays disabled while another wallet is selected, and the screen, the XCM interactor and the bridge interactor refuse to submit for a different wallet.
- Cross-chain and bridge transfers keep an unsettled-transfer record (`CrossChainSubmissionJournal`): one per wallet, origin network and sender account, written durably with the exact signed bytes before anything is sent and kept after a send, a node acknowledgement or an ambiguous failure. While it exists no new cross-chain transfer is signed for that scope, and the production XCM submitter refuses untracked submissions. The confirm screen shows "may have been sent" instead of an error, offers Check status, and checks automatically when it opens. `CrossChainSubmissionReconciler` settles a record only on proof from finalized blocks of the origin network: the exact bytes after the anchor (included; the screen says this does not confirm delivery and will not resubmit the same transfer), or the whole mortal validity window finalized without them (expired, not executed; the transfer may be sent again). A miss alone never settles it, and a record without a validity window can only be settled by finding it.
- The cross-chain setup screen no longer crashes when the route leaves the discovery snapshot (every chain sync clears it) between the capability check and the minimum lookup; it shows that no route is available. Origin fee quotes register their quote-only key provider themselves instead of racing the screen's registration, and that provider answers only the selected wallet's public crypto type: the quote signs a placeholder for an all-zero account, which the wallet's key repository could not resolve, so quotes with a real provider always failed.
- Cross-chain recipients: a mixed-case EVM address must match its EIP-55 checksum (`isEvmChecksumValid`, checked at setup and again by `XcmService` before submission), and setup refuses an address with surrounding whitespace instead of letting it fail after confirmation. The shared address check used by other send flows is unchanged.
- A fee-included XCM route (the destination deducts its execution fee from the amount) no longer shows "Destination network fee 0 DOT" on setup or confirmation; it says the fee is deducted from the amount at the destination. Bridge routes keep their reviewed fee.
- The reviewed Polkaswap bridge executor has a compiled gate like XCM: `ENABLE_PRODUCTION_BRIDGE_TRANSFERS` is a hardcoded `false` in release builds and comes from the separate `ENABLE_DEBUG_BRIDGE_TRANSFERS` input (default `false`) in debug builds. The bridge authorization, the bridge mutation switch and the bridge route providers all require it, and the effective-registry audit enforces this wiring (five new scenarios). Release bridge submissions were already denied because production carries no operator trust; enabling them now also takes a reviewed build change.
- The release XCM flag is bound to the evidence manifest. Configuring `feature-wallet-impl` fails when the release `ENABLE_PRODUCTION_XCM_TRANSFERS` value is anything but `false` while `scripts/xcm-production-evidence.json` is not `ready` and release-enabled (checked by flipping it locally). CI and the release workflow run `scripts/xcm-release-submissions-enabled.sh` (7 scenarios) and, when it reports the flag on or unreadable, the live effective-registry audit and `audit-xcm-production-evidence.sh --require-ready`. Ready evidence expires after 30 days, counted from `lastReviewed` and from each transfer (3 new scenarios). The release-variant tests (`XcmReleaseConfigurationTest`, `XcmBridgeCatalogReleaseContractTest`) never ran anywhere; CI now runs `:feature-wallet-impl:testReleaseUnitTest`, and the unit test membership audit requires a CI step for every module with release-variant test sources. Locally the release task stops at the runtime source pin check (the pins name fearless-utils `fbbc2802`, while the code needs `SocketTransportPolicy` from a later commit), so the release tests were run as temporary debug copies: 8 pass. `:feature-wallet-api` unit tests, which existed but were not part of `runTest`, now are. After the merge into `release/fearless-safe-upgrade`, `:feature-account-api` (106 tests added on that line) joins `runTest` too, so the unit test membership audit passes.
- The executable bridge route pins its call index: Liberland to SORA `SoraBridgeApp.burn` is pallet 81, call 3, read from the live Liberland runtime metadata (spec 34) on 2026-10-24. The catalog refuses an executable route without a pin, the runtime resolver checks the pin against the connected runtime's metadata, and the submitter checks it again on the exact runtime that signs. `ReviewedBridgeLiberlandRuntimeTest` resolves the route against a checked-in snapshot of that metadata. Before signing a tracked XCM or bridge submission, the submitter verifies that the connected node's genesis hash is the transfer's network. The XCM module's detekt findings (ten, from earlier commits) are cleared and `detektAll` passes.
- The Liberland to SORA registration check failed closed on live data, so the only executable bridge direction could never be quoted: SORA stores the Liberland id as `GenericAssetId::Liberland(LLD)` / `Liberland(Asset(id))` (read from SORA mainnet on 2026-10-24), while the authority compared the inner value directly. It now requires the Liberland variant and the exact inner asset. `ReviewedPolkaswapBridgeRuntimeAuthorityTest` decodes the live keys and values of LLD, LLM and XOR (asset kind, sidechain precision, sidechain asset id) with SORA's runtime metadata, checks that the keys built from the reviewed routes are the live ones, and covers a foreign variant, another Liberland asset, a changed kind or precision and a missing record.
- One malformed entry of the remote chain catalog no longer stops the chain sync. `ChainSyncService` used to map every entry in one pass, so a single entry without a name or address prefix threw, the whole refresh fell back to the offline catalog and the current process had no XCM discovery data. It now skips and logs that entry, keeps its cached catalog row as it was, and syncs the rest. A chain without an address prefix is refused instead of being given 0 (the Polkadot prefix), and an asset without a precision is dropped instead of being given 10. The live catalog (116 chains, 91 enabled, read on 2026-10-24) and the bundled `local_chains.json` (120 chains) carry a name, prefix, ecosystem and asset precision for every entry, so neither change drops anything today. An entry without an ecosystem is still read as Substrate, as before. `ChainSyncServiceTest`, `UnsupportedEcosystemCatalogTest` and `RemoteChainMappingTest` cover the skip, the kept cached row, the quarantine of unsupported ids next to a malformed entry, and both strict fields.
- An AccountId32 recipient must be written in the destination network's own address format. The account id arrives whatever the format, but an address of another network usually means the wrong destination (an exchange deposit address, say), so setup refuses it with the wrong-network message and `XcmService` refuses it again before quoting or submitting. Origin fee quotes are scaled by the origin network's utility asset, which `XcmService` now takes from the origin chain itself: the service defaulted to the transferred asset and the interactor fell back to it when a chain had no utility asset, which misstates the fee by the precision difference.
- At registry load the reviewed destination location must be the path from the origin to the destination chain of the catalog: `Parachain(id)` with parents 0 from a relay chain to its parachain, `Here` with parents 1 from a parachain to its relay chain, `Parachain(id)` with parents 1 between siblings, always with the destination's own parachain id. Any other pair of chains, or a parachain without a canonical id, is refused. An xTokens spec's beneficiary must be that destination location followed by the recipient account (xTokens takes one destination that already contains the recipient), checked at load and again when the call is built. The registry metadata audit mirrors both checks (three new scenarios). A bundled registry that fails to load is now logged instead of silently leaving the build without XCM routes, and the transfer engine keeps its key providers and metadata in concurrent maps, because screens register them while quotes and transfers read them on other threads.
- Tests that could not fail are replaced. The route registry selection test asserted non-null on a non-null result; it now checks for the shared unavailable registry and the logged failure. The route inventory test scanned the screen's source text; it now checks which minimum text each inventory row gets (`routeInventoryMinimumText`, extracted for this). Engine and bridge call tests compared `toString()` fragments; they now compare the exact runtime values. The XCM service and route fetcher fixtures fell back to an empty registry whenever they failed to load, so their rejection tests passed whatever the reason; fixtures must now load, and the tests about refused specs check that the registry refuses them. New tests cover the remaining cross-chain validation paths (waiting for the fee, sending to oneself, a balance that cannot cover amount and fee, the origin minimum warning and its confirmation) and the TON and Equilibrium minimum sources.
- The route retirement below rewrote `local_chains.json`, which left the receiving chain policy's source digest (`PortableWalletReceivingChainPolicy.SOURCE_SHA256`) pointing at the previous file and failed its test. The digest now names the current file; the policy's Substrate identities (87, 17 of them disabled) and its own digest are unchanged, which the test checks.

## XCM route retirement and engine encoding — 24 October 2026

A live review of the bundled XCM routes found that none of them could execute. Relay-chain reserve transfers of DOT and KSM are rejected by the Polkadot and Kusama runtimes since the Asset Hub Migration (`Filtered`, `InvalidAssetUnknownReserve`); Moonbeam, Moonriver and Parallel are no longer registered on their relay chains; and the transfer engine could not encode any reviewed call against a real runtime (bare string recipient junction, V3 asset id wrapped like a versioned location, V3 `X1` as an array, camelCase call names against snake_case metadata). Release builds were unaffected because XCM execution is compiled and policy disabled.

Every execution spec was retired. `approved_xcm_routes.tsv` and `scripts/xcm-required-routes.tsv` now hold only a comment, `local_chains.json` keeps its 27 discovery-only destinations without the three dead chains or any execution block, and the production evidence manifest records two blockers. The route registry accepts an empty approved set, the effective-registry audit accepts it only with an explanatory comment, and the evidence audit refuses "ready" with nothing to enable. The audit scenarios that need executable routes run against frozen fixtures under `scripts/fixtures/xcm-effective-registry`, which no production code reads. The Moonbeam USDt inspector and its test were deleted with the route.

The engine now emits structured junctions, the V3 and V4+ asset id and `X1` shapes, and resolves call names under both spellings. `SubstrateXcmTransferEngineRealMetadataTest` encodes V3, V4 and V5 teleports, reserve transfers and an XTokens transfer against real Polkadot Asset Hub and Acala metadata, decodes the extrinsic bytes and checks the account, amount and weight. `BundledMutationRouteManifestTest` pins the route manifest digests to the bundled files (the committed `local_chains.json` digest was already stale). The cross-chain confirm screen submits at most once per tap sequence.

The Polkaswap bridge catalog was reviewed the same way. Both SORA parachains stopped producing blocks: read from the relay chains on 2026-10-24, the Polkadot one (para 2025) has been at block 4,850,969 since 2025-12-13 and the Kusama one (para 2011) at block 6,535,527 since 2025-11-11, while Acala, Astar and Asset Hub advance. SORA's bridge records agree (no inbound transfer completing after those dates; the 5 DOT and 9 KSM burns made toward relay accounts since then are still pending), and SORA and Astar have no HRMP channel. SORA to Liberland has three transfers pending since May and June 2026 after 502 completed ones, while Liberland to SORA still completes (latest 2026-10-21). Eleven of the fourteen reviewed directions moved from `executableRoutes` to `retiredRoutes`, are published as unavailable with their reason, and are rejected by the executor; only Liberland to SORA (LLD, LLM, XOR) stays executable. The retired definitions remain as code so their call builders keep their tests.

Open: no XCM or bridge route can be enabled until it has a reserve decision, a live dry run and funded evidence, and the SORA directions also need SORA to confirm that its parachains produce blocks again. Remote chain configuration still advertises the dead and halted directions to installed app versions and needs an owner-side update (patch prepared).

## Shared catalog observations with fresh read boundaries — 23 October 2026

The preceding 497b192ff source has an accepted full build with 5,377 passing JVM cases, zero failures and 26 inherited skips. Its unchanged native caller run has eight passes, one cold-start management timeout and five delegation methods not run. The timeout occurs before the first synthetic publication or Home action. Protected data, device identity and settings remain preserved. No native success is claimed for this correction.

Actual repository and DAO tests reproduce excess work: six overlapping list/map subscribers perform six catalog reads, and one invalidation performs separate reads and mapping for each observer. The generated dependency graph also creates independent repository instances. The provider is now scoped as a singleton, and overlapping observations share one catalog read and mapped snapshot. Synchronous subscriber retirement prevents a just-replaced observer generation from replaying stale data. Errors reach collectors before any retained snapshot, cancellation remains cancellation, and finite sources retain completion semantics. One-shot getters, including getChain, still read fresh local state.

Twenty-one focused runtime tests cover duplicate reads, invalidation, withdrawal, cancellation, restart, finite completion and late-subscriber failure. Three original repository failures and two failed sharing drafts remain preserved; the final candidate passes independent replay. These checks establish the duplicate-read correction, not the cause or resolution of the native timeout. The full-build target is now 5,398 passes with the same 26 inherited skips. Fresh generated Hilt scope, module compilation, lint, coverage, APK checks and unchanged native caller checks remain required.

Numeric availability, currency binding, repository/UI/action propagation, migration and historical writer coverage, production recovery wiring, rendered UI/signing, iOS/extension, physical/design and shipping gates remain open. The production-readiness audit stays active.

## Catalog read cost and pending pool publication — 22 October 2026

The preceding 67d227c source passed 5,350 fresh JVM cases with zero failures and the same 26 inherited skips. Its native management checks still timed out. An independently reviewed observational run preserved all five original failures, assertions and timeouts: four methods reached a real pool selection and scenario without publishing the terminal body, while one had no observed selection by its deadline. Protected records and settings remained exact. These observations narrow the diagnosis but do not establish one cause for every timeout.

Catalog snapshots now scan assets and explorers once, retain parent and child ordering, and read small node text inline with strict UTF-8 and a 4,096-byte bound. Larger node fields keep the existing bounded chunk reader. Catalog SQL excludes missing parents before projecting raw fields; the separate all-row asset reader retains its old semantics. The host fixture drops from 1,275 queries to at most 225 for 106 chains. Sixty-one host tests and 21 SQL controls pass, including oversized orphan rows, malformed text, endpoint selection and existing preservation cases. These are host results, not device latency measurements.

The pool interactor now emits unavailable before waiting for chain and wallet metadata. Twelve new tests exercise pending prerequisites, cancellation, healthy completion and actual pool/root view models. The original source fails seven cases and passes five controls; the candidate passes all twelve plus 49 retained cases. A missing scenario still waits and cannot be invented by this change.

The combined full-build target is 5,377 passing cases with the same 26 inherited skips. Fresh generated Room, expanded lint, coverage and APK validation, followed by unchanged native caller checks, remain required. The candidate does not close numeric cache/domain/action work, migration and historical-writer gaps, production recovery wiring, rendered UI/signing, iOS/extension, physical/design or shipping gates. The production-readiness audit remains active.

## Offline staking catalog selection — 22 October 2026

The preceding `7210b03d` source has an independently accepted full build: 5,344 passing unit cases, zero failures, the same 26 inherited skips, expanded lint and APK checks. Its subsequent offline native caller run failed: all four adapter methods passed, all five management methods timed out waiting for an actual terminal staking body, and the five delegation methods were not run. The failure, receipts and cleanup remain retained. Seven protected records, two wallet rows and four account rows remain exact; settings were restored and the crash buffer was unchanged.

Selection was observing `ChainRegistry.currentChains`, which publishes only after runtime setup jobs finish. The correction observes the local database catalog through `ChainsRepository.chainsFlow()`, preserving stored choices, exact wallet/account matching, current token configuration, null revocation and action revisions. It does not change registry convergence or authorize a network operation before that operation's own checks.

Six new source-compiled tests use the actual registry, repository and local mapper with controlled DAO rows and a held runtime configurator. The preceding source reproduces four failures and passes two controls. The correction passes all six plus the 43 existing availability/revision/shared-state cases. Cached pool selection, local removal and recovery, metadata refresh and unavailable explicit choices work while the registry has never published; absence of a database snapshot still waits. The existing three test fixtures change only their injected catalog source and keep their assertions. The initial host compile preparation failure is separately retained.

Fresh integrated checks must retain all 5,344 prior passes and add six, for a target of 5,350 passes with the same 26 skips. The full build and unchanged fourteen-method native caller regression remain pending for this correction. Host tests do not qualify generated Room, installed UI, rendered parachain/signing behavior or physical devices. Mixed numeric cache reads and their write/domain/action separation remain external work; broader migration, historical writers, iOS/extension, physical/design and shipping gates remain open. The full production-readiness audit is active.

## Nullable staking events and alert callback identity — 22 October 2026

The expanded d030 staking build completed all 5,344 fresh unit cases with zero failures and the same 26 inherited skips, but failed the newly included staking lint scope. Three revocable event streams now explicitly admit null; reward and status observers use the existing null-safe EventObserver, while management retains its null-triggered dialog dismissal. The alert diff keeps reference identity so an equal-text replacement installs fresh callback authority, with a local documented suppression for that intentional behavior. No revocation, observer lifecycle, test assertion or lint scope is removed.

The failed build and its exact test roster, six lint findings and class inputs remain retained. Existing event retirement and nullable-observer regressions plus the fourteen native staking caller methods remain applicable. A fresh full build, expanded lint and native caller qualification are required before accepting this correction. Broader startup/migration, numeric cache availability, historical writers, iOS/extension, physical/design and shipping gates remain open; the full audit is active.

## Staking catalog authority and chooser identity — 22 October 2026

Staking selection now follows the observed wallet, exact account bytes, current catalog and stored chain/asset/scenario choice. Missing or malformed explicit choices remain stored and unavailable; only a missing preference derives an ephemeral default. A catalog or account change revokes action authority before a repository read begins. Restoring an identical choice receives a new revision. Rows for another wallet or account cannot authorize the current selection, and current catalog token configuration replaces stale configuration while retaining matching balances. The chooser also matches both chain ID and asset ID, so networks sharing an asset identifier cannot borrow each other's display or balance.

The external actual-class qualification passes 43 availability/revision/shared-state tests and three selector identity tests. Four deliberate availability regressions are detected; the selector baseline reproduces two failures and its correction passes all three cases. Twenty-eight methods are new, and the ten modified shared-state tests retain their original identities and assertions. The expected integrated cohort is all 5,316 prior passing cases plus these 28, with the same 26 inherited skips; 5,344 passes is an acceptance target, not a completed result.

The pre-integration 5a6 source has accepted full-build evidence, 57 retained APP-process tests across APIs 26/34/36 and 21 fresh-profile emergency Activity/reader tests. Its separate 70-case CORE packet on each API must also be accepted before this change is applied. These results qualify their recorded pre-integration source and scope. Fresh integrated Gradle, scoped lint, coverage and applicable native/caller checks remain required. Existing non-null rendered UI replay, wallet deletion/empty-wallet flows, full signing, broader migration/startup bounds, installed historical writers, physical/design checks and shipping gates remain open. No production-readiness claim is made.

## Paged recovery and active selected-asset observers — 22 October 2026

The preceding Android 5dea source retains its accepted 5,263 fresh JVM passes, 26 inherited skips and three-API APP preservation qualification. This change keeps large recognized recovery inventories reachable through bounded metadata pages and decrypts only an explicitly selected key after PIN authentication. A per-screen session revokes on stop/recreation and prevents late operations from returning secrets. The screen distinguishes stale lists, read failures, actual empty storage and unavailable authentication, identifies each stored key, and routes copying through the sensitive clipboard action. Host qualification independently passes 41 reader/session cases, including 259 synthetic vectors and 991 exact exported fields. The new seven-method actual Android Activity/reader packet remains unexecuted until the final build is accepted.

Selected token and balance consumers now cancel work when the exact saved asset becomes unavailable. Balance observation follows the current wallet and account ID, including same-wallet TON key changes that domain equality omits. All 34 focused host cases pass independent review; the original 10 observer failures and the review-found three TON failures remain recorded. Fifty-three new JVM methods are included in the expected 5,316-pass cohort, with the wallet-api module explicitly added to unit and lint checks. Full Gradle/APK and retained-device CORE/APP regression plus fresh-profile recovery qualification remain pending. These changes do not clear every existing non-null rendered UI state or qualify signing. Separate staking catalog/revision failures, broader migration/startup bounds, installed historical writers, physical/design checks and shipping gates remain open; no production-readiness claim is made.

## Complete registry fixture isolation — 22 October 2026

The second integrated run resolves the preceding four failures and retains 5,262 passes, one failure and 26 inherited skips. The remaining failure is an uncaught Android logging exception reported by a later test; all real-registry fixtures are now inventoried. Two Unconfined fixtures scope their logging mock and join cancellation before restoring it. The native-node fixture keeps Dispatchers.IO, uses a worker-visible logging mock, and joins every registered scope in teardown before restoring logging. All existing test bodies and production files remain unchanged. Forty focused source-compiled checks pass in independent and root replay with assertions and the throwing Android Log stub, including worker-thread logging/restoration and a subsequent runTest control. Complete runtime/common module tests, a fresh full run and retained-device native qualification remain pending; the full audit stays open.

## Full-check test fixture correction — 22 October 2026

The first integrated exact-choice run is retained as a failed attempt: 5,259 passes, four failures and 26 inherited skips. Three new asynchronous exception assertions required reference identity despite coroutine debug stacktrace recovery; they now accept only the original throwable or a copy with the same concrete class/message and original direct cause. The selection fixture now scopes its Android logging mock and joins registry cancellation before closing it, preventing completion-handler exceptions from leaking into a later test. Production code and all test IDs remain unchanged. All 28 focused cases pass with and without Java assertions. A fresh full integrated run and retained-device native qualification remain pending; the full audit is open.

## Single-asset selection and preference observation — 22 October 2026

The reviewed correction preserves an explicit stored chain/asset choice while its catalog entry is unavailable; it never substitutes a different chain or overwrites that choice. Empty or unsupported catalogs wait without initialization exceptions, returning healthy options separately. A missing preference derives a permitted default without persisting it. Asset IDs retain their complete colon-containing suffix, and selected metadata follows catalog updates.

String observation without an initializer now registers before reading, serializes snapshot publication with callbacks, performs no writes, and always unregisters. The producer-present branch keeps its existing behavior. Twenty-eight focused actual-class tests pass in independent replay. The original implementation fails thirteen of the initial twenty-six; the first observer correction separately reproduces two burst/clear failures. The final observer coalesces queued updates to retain the latest value and re-reads on Android R+ null-key clear callbacks. Full integrated Gradle and native checks are pending. Existing collectors retain their last non-null value while unavailable; this change does not revoke their child subscriptions or qualify transaction signing. The full production-readiness audit remains open.

## Unsupported chain families and initial catalog availability — 22 October 2026

The preceding pagination source af0f05d5046b171f47da7d500acac316a965c9f9 is qualified in its recorded scope: 5,192 fresh JVM passes with 26 inherited skips, eight scoped lint reports, four APKs and independently matched coverage. Actual-app catalog qualification passes on API26/34/36, followed by 210 endpoint/DAO regression passes. All prior wallet/database/authentication files are preserved. Separate API34/36 reviews correct a verifier that required the unchanged AndroidX profile cache to refresh after package replacement; no changed prior file is admitted and original failures remain retained.

The new correction excludes explicitly unsupported ecosystem values from operational chain collections and connections while retaining their stored catalog, identities, balances, visibility choices and selected custom endpoints. A known-to-unsupported remote transition updates only the exact raw ecosystem cell inside the existing catalog transaction; supported metadata can restore the chain later. Duplicate remote identities reject before choosing a winner. XCM parsing catches only expected parse failures, and explicit unknown asset types remain unknown. The chain registry now distinguishes an unobserved catalog from a known empty snapshot, publishing initial empty catalogs without a readiness/data race.

The composed candidates pass 80 focused host checks. Forty-three added JVM methods bring the expected full-build cohort to 5,235 passing cases plus the same 26 inherited skips; integrated Gradle validation is pending. Two new native suites cover the actual offline ChainSyncService caller and generated Room quarantine, restoration and late rollback. Together with the preceding catalog suite, the pending cohort has 19 methods,102 synthetic current-schema fixtures and420 measured actions per API; every stored SQL value outside32 exactly named changes remains protected. Native sources and the full SQL oracle have independent review, including44 replayed acceptance/rejection controls. No device execution of this new source is claimed yet.

Next: run the full source-bound unit/static/lint/APK checks, then native qualification on the retained owned API26/34/36 profiles. Saved asset-selection availability, mixed/protected migration limits, historical matrix, secret recovery, physical/design and exact shipping gates remain open. Full production readiness is not established. Composition plan SHA256: `b7b8c475e83537f9328017d107f7e2b02ef4cfa7a9de7bc9306db4f2890e4ece`.

## Runtime catalog pagination — 22 October 2026

The preceding runtime catalog correction has an independently accepted full build: 5,192 fresh passes, zero failures and 26 inherited skips, eight scoped lint reports without errors, four APKs and verified five-module coverage. Detekt retains its inherited exclusions and crowdloan coverage remains zero. Actual-app API26 qualification now independently passes all 12 methods, 68 synthetic fixtures and 234 measured actions. Full SQL preservation, the sixteen exact allowed cell updates, prior wallet data and authentication proofs are verified. API34/36 qualification is pending.

Those measurements exposed a performance regression: complete large catalog reads took 18–35 seconds and offline preservation about 65 seconds. A nullable pagination predicate caused each page to rescan earlier rows. The three runtime catalog queries now add a redundant signed-rowid lower bound so SQLite can seek directly to the next page, retaining the original exclusive cursor test and every valid row. No stored data, schema, migration or optional-field policy changes.

Independent host SQLite checks compare 396 literal-query samples, 168 signed-boundary samples and 36 complete traversals, including 262,146 rows over 4,098 pages. The three query plans use rowid range searches; the minimum signed rowid remains included on the first page and the maximum terminates safely. The existing two healthy native fixtures now seed both signed extremes before their retained before-images. Actual Android performance and the revised boundary fixtures remain pending fresh execution.

Next: full source-bound runTest, static analysis, lint and APK checks, then the 12-method actual-app suite and 70 existing endpoint/DAO cases on retained API26/34/36 profiles. Native caller wiring, unknown ecosystem, mixed/protected migration limits, historical matrix, physical/design, secret recovery and exact shipping gates remain open. The full audit is active; production readiness is not established. Pagination plan SHA256: `8bf3835214c88ade079d0f8a3b2facf48cb4e1a746172c36800d435c485aacb7`.

## Runtime catalog availability and preservation — 22 October 2026

The preceding optional JSON correction has an accepted full build: 5,155 fresh unit passes, 26 inherited skips, eight fresh scoped lint reports without errors, four APKs and five verified coverage reports. Detekt passes its inherited filtered scope; runtime remains excluded and crowdloan implementation coverage remains zero. Earlier migration/startup and rollback acceptance retains its original scope.

Actual native baseline reads reproduced CursorWindow failures for four oversized optional catalog fields. The core-only API26 test process also exhausted its 48 MiB heap on a large relation; the actual wallet application process, using its existing 512 MiB manifest heap allowance, subsequently passed all ten large-catalog reads with every row retained. This establishes the tested app baseline, not a global device-performance guarantee.

The integrated correction pages runtime catalog rows and reads optional JSON as bounded UTF-8 chunks. Values through 4 MiB remain available; oversized, invalid UTF-8 or non-text optional metadata becomes unavailable in the operational view while stored bytes remain intact. Catalog and funded observers retain same-count invalidations. Offline refresh explicitly preserves cached catalog rows without a lossy domain round trip; complete online values, including explicit clears, still update storage. Unknown cached staking/remote asset-source values and incomplete price-provider objects now follow narrow operational fallbacks. Existing identities, endpoints and user choices remain protected; no schema or migration version changes.

The reviewed candidate passes 50 focused host cases plus 24 mapper cases and independent SQLite controls. The new actual-app regression contains 12 methods and 68 synthetic current-schema fixtures per API, including complete large relations, boundary/wide metadata, selected custom endpoint records, explicit clears, absent-parent rejection and rollback after parent and asset writes. Sixteen writer fixtures allow only one named catalog-cell update; all others require exact SQL preservation.

Next: full source-bound Gradle/unit/static/lint/artifact checks, followed by actual-app execution on retained API26/34/36 profiles and independent retention review. These integrated checks are pending. Native ChainSyncService wiring, unknown ecosystem handling, broader mixed/protected migration limits, historical matrix, physical/design, secret recovery and exact shipping gates remain open. The full audit remains active; production readiness is not established. Combined plan SHA256: `cdca2902dbbc1fd96c9d4e2f5d7b622568b8a72b766f8584ff768217f9c2a3b3`.

## Optional cache JSON recovery — 22 October 2026

The preceding cache migration correction now has an independently accepted full build: 5,143 fresh unit passes, 26 inherited skips, zero errors in eight fresh scoped lint reports, four APK targets and five verified coverage reports. Detekt passes its inherited filtered scope; runtime is excluded by that configuration and crowdloan implementation coverage remains zero. All 312 native cases pass on API 26/34/36. Retained SQL, wallet data and authentication proofs are verified. API36 has an exact reviewed Firebase SDK-label ordering change; the original retention failure and read-only completion remain preserved. Native cross-review SHA256: `1a55457d26c23fd999eb5b1a836470ac0136d827bd017bb92c60f73ac2a5dbc9`.

Actual compiled mapper/repository reproductions found that malformed optional purchase-provider, price-provider or explorer JSON prevents healthy peer chains from being returned. The narrow correction treats Gson parse failures as absent optional metadata and preserves every other mapped field and database row. Cancellation, fatal errors and unrelated failures still propagate. Twelve external regression cases pass, including all four repository paths in both peer orders and a valid 1,048,578-byte list. Candidate SHA256: `62c916c8507282e452a41b6dc075329a55f438c7f36089ea0af2da634ed7f361`.

Next: rerun the required full unit/static/lint/build checks with exact source binding. The prior 312 migration/startup executions qualify their unchanged source only; they do not qualify this mapper on Android. Native wide-cell and large-relation reads, semantic cache validation, mixed/protected migration limits, historical matrix, physical/design and exact shipping gates remain open. The full audit remains active and production readiness is not established.

## Cache migration availability correction — 22 October 2026

The physical-cohort transaction correction is now independently accepted: all six cohort and three transport-policy cases pass on each API 26/34/36 profile (27 fresh passes). The other 67 historical cases per API have explicit reuse lineage. The accepted app database, real-PIN proofs and original failed fixtures remain preserved. The earlier rollback-open-mode correction remains closed.

Separate real-SQLite reproductions found that healthy copied cache data could block startup at the 1 MiB cell or 262,144-row limits. This correction excludes four SQL-only copied cache tables from payload-byte accounting and permits known configured cache foreign-key children to bypass row-count cutoffs. Exact schema, foreign-key definitions, actual foreign-key validation, protected identity and mixed user-data limits remain enforced. Saved endpoints and user choices are preserved; token prices retain their existing migration rebuild behavior.

The reviewed candidate passes 44 external host checks, including boundary, invalid-foreign-key, user-data, complete rollback and policy controls. Core and app native sources compile. The new shipped-wallet startup case covers 262,145 cache rows and a 1,048,578-byte cache cell through the actual Room/startup gate, Android Keystore and two opens. These are source/host results; full Gradle and native acceptance are pending.

Next: full runTest, 19 forced unit tasks, eight fresh scoped lint reports and four APKs, then 103 core migration cases and the new actual-startup case on API 26/34/36. Broader mixed-cache availability, runtime cache consumers, historical matrix, physical/design and exact shipping gates remain open. Plan SHA256: `9b7f3f66ed0b645672da4a5f74c6d6dba06302151aa3f4af74234f26735fc9c6`. The full audit remains active and production readiness is not established.

## Historical cohort transaction correction — 22 October 2026

The actual-PIN Solana selection journey is independently accepted on API 26/34/36, including 18 phase proofs per API, exact SQL/preferences and reauthentication after lifecycle changes. Six locale/component methods pass per API; 96 captures have source/receipt/PNG verification and scoped visual review across all eight locales. The API36 pre-install offline-ADB failure and same-profile continuation are retained.

Historical native qualification passed 69 of 73 cases on each API. Four positive physical-schema cohort cases invoked the 76-to-77 migration after ending the fixture transaction; the production rollback-preview guard correctly rejected this. The fixture now runs each positive chain in one outer transaction, preserving the original source version and foreign-key mode. All frozen cohorts, identity assertions, negative cases and preference no-write guards remain intact. All failed evidence and 222/223/223 core files are retained, and the accepted app database and 18 proofs per API remain exact. The Android transport-policy cases were not reached after the cohort failures.

Next: rebuild and lint the corrected core test fixture, then rerun its six cohort cases and three platform-policy cases per API on the same owned profiles. The other 67 native cases per API, 5,139 unit passes/26 inherited skips, scoped coverage and unchanged app/common APKs have explicit reuse lineage; they are not new executions. Broader cache/startup availability, historical matrix, physical/design and exact shipping gates remain open.

## Coverage report path correction — 22 October 2026

The full historical/native follow-up build completed with 5,139 fresh unit passes, zero failures and 26 inherited skips across 18 Android modules and utils. All eight scoped lint reports contain zero errors or fatal findings (five fresh, three exact reused); all four APKs assembled and schemas 82/83 remain unchanged. These results are bound to source `4fd48653a701aa48d4ed8db45d171e8b7b8a5453`.

Coverage instrumentation produced five fresh execution files, but the reports contained no classes because DirectoryProperty string interpolation yielded nonexistent paths. The report script now resolves class-directory providers, supplies Java/Kotlin source roots, and fails when no compiled classes are found. Qualification will rerun only the five report tasks using the exact frozen compiled classes and execution data; production/test sources, APKs, instrumentation scope and inherited report filters are unchanged. Coverage remains pending until nonempty XML/HTML and input lineage are verified.

Next: finish report qualification, then actual-PIN selection, historical/native policy and locale journeys on the allocated API 26/34/36 profiles. Broader startup availability, full historical, physical/design and exact shipping gates remain open.

## Historical repair, native journeys and coverage qualification — 22 October 2026

The preceding endpoint observer correction passes 428 fresh core unit cases with four inherited skips, zero core lint findings and all 70 device cases on each retained API 26/34/36 profile (210 executions). The earlier 3,357-pass C3 build remains separately recorded. Retained failed-baseline files remain intact except the narrowly proven AndroidX package-update timestamp field. This acceptance covers endpoint database and observer behavior; actual-PIN, locale, policy and historical paths remain separate gates.

This follow-up prepares shipped version 71 cache layouts before strict migration checks, archives endpoints before repair, and accepts exact shipped foreign keys and compatible earlier archive captures. User-row copying now uses strict insertion so conflicting contacts cannot silently disappear. External host checks pass 23 cases and preserve 33 retained public-wallet fixtures, including complete rollback for both reproduced contact collisions. Broader startup availability for conflicting/unknown user layouts remains open.

Native additions cover authenticated Solana selection, three real transport-policy checks and explicit locale binding in four Compose fixtures. Seven locales gain 112 selection/status strings. The corrected default/custom activation oracle matches retained evidence; older non-English fixture claims await rerun. This commit also enables application-class coverage on Java21 using pinned JaCoCo0.8.12, replacing the inherited exclusion of every class. Existing five-module report scope and report filters remain explicit; actual Gradle coverage still needs qualification.

Next: full 18-module runTest/static/coverage checks, scoped lint and APK builds, then actual-PIN, historical/native policy, locale journeys and visual review. Full historical, physical/design and exact shipping gates remain open. Combined candidate plan SHA256: `bc4c33a7d6d7f55c49c2b09642c2f1f07e0730cef050cba93d804ef6b8c64289`.

## Endpoint observer correction under qualification — 22 October 2026

The C3 build passed 3,357 fresh unit cases with 26 inherited skips and zero lint errors. The subsequent retained-device baseline produced 163 passes and 11 failure executions across API 26/34/36, representing four distinct methods. The closed-handle postcondition now checks the reopened database. The malformed-inactive-default oracle preserves a healthy active endpoint and adds explicit malformed-active/default and unknown-active rejection controls. The edit fixture uses the real custom-node DAO.

A direct JVM regression independently reproduced stale node/catalog observers when Room invalidations remain idle after a raw-store mutation. Both flows now also observe mutation-lane completion. The funded send-network query uses catalog metadata and bounded node reads, with the same wake-ups, avoiding its legacy unbounded endpoint relation. New native cases exercise actual raw selection through all three observers and a 3 MiB malformed endpoint beside a healthy peer. Four focused JVM cases and 70 native cases per API (60 endpoint cases plus ten existing DAO cases) are the next qualification target; no corrected-pass claim is made yet.

Original failed runs and retained fixtures remain preserved. PIN, locale, historical cache, broader startup availability, physical/design and exact shipping gates remain open. Correction manifest SHA256: `b22bcd5051b0aeeb7c8ed6a82779f9d2fa03a3679e16118a52cc84e01d73ce7c`.

## Endpoint selection Gradle fixture correction checkpoint - 22 October 2026

The first integrated C3 build retained eight runtime/account/wallet failures plus a separate core API test dependency error. The dependency rerun now passes all 117 core API tests with clean lint. This follow-up initializes the real DAO mutation lane in mocks, waits for fixture startup and explicit runtime acknowledgment while preserving healthy-peer identity checks, tests exact URL bytes/rejections, and scopes the expected Android logging stub. The deleted-row regression now requires the intended exception, closing an accidental passing assertion.

The reviewed external candidate passes 45 targeted tests and 200 scheduler repeats against real Android stubs, plus one helper control. Style-only cleanup retains native case bodies and explicitly maps seven renamed cases; 16 residual style findings remain recorded without suppression. Manifest SHA256: `95dd516e8977ef0ac4832de59975dc15071fdfdca3c91e6aa9a25eaaedaccccf`. Actual Gradle rerun, native/PIN selection, historical caches and broader release gates remain open.

## Endpoint Gradle corrections — 22 October 2026

The first integrated C3 Gradle run completed with four failed tasks: core API test compilation lacked its explicit guarded WebSocket test dependency, and eight tests failed across runtime, account and wallet modules. Its source snapshots remained exact; logs, XML reports and generated APKs are retained. These failures remain open pending correction and rerun.

Core API now declares the existing pinned WebSocket library for tests only. Production dependency scope, version and local source substitution are unchanged. The remaining failures and attributable formatting follow-up are under review; no clean Gradle or full readiness claim is made.

## Endpoint selection integration under qualification — 21 October 2026

The combined C3 candidate now integrates ordered endpoint selection, catalog replacement, runtime requalification, Solana preference handling and the Saved endpoints selection UI. Selection authority remains tied to the rendered observation and current authentication lease; UI status distinguishes durable selection from runtime connection acknowledgment. The socket dependency carries matching configuration and worker-lifetime changes.

The external combined checkpoint has 602 distinct host passes, compilation of 68 production files across eight modules, 55 unit/shared sources across seven modules, and eight native/shared sources. Its 58 native test methods compile; they have not yet run on Android for this candidate. Root review SHA256 is `aacd0037bd6c86e917fa43cd801996ed90115ab8677cf1440d078e054da67047`. The earlier missing-resource/dependency host failures remain recorded with their passing class-specific reruns.

Actual Gradle dependency resolution, Room/KSP/Hilt generation, resource/APK builds, scoped units/lint, native transaction/policy checks and actual-PIN selection journeys remain required. Scoped static analysis still has 674 new-file findings and 496 conservative changed-line findings, in addition to 2,076 matched existing findings; cleanup is underway and no clean-style claim is made. Historical cache repair remains an external candidate. The full audit and release gates remain open.

## Startup checksum correction qualified — 21 October 2026

The fixture-only correction at `dcc47d3be` passed its narrow build and all three app cases on API 26, 34 and 36. Root raw-data review and an independent cross-review accept all nine fresh passes, including the original complete-file SHA assertions. Prior failing/diagnostic fixtures remain unchanged on the same profiles and UIDs. API 26 crash evidence uses the original same-boot empty baseline plus fresh during/after buffers; API 34/36 have fresh before/after captures.

Production, core-test and common-test APKs and schemas 82/83 are byte-identical to the previously qualified build. The 496 core/common native cases per API and 1,960 unit passes with four inherited skips remain exact-source reuse. App lint reports zero errors and 100 warnings. The accepted packet is `android-startup-availability-followup-v1/api26-reopen-diagnostic-v1/correction-v1`; cross-review SHA256 is `89c2554dda031eb75a2fd70fcafe7e39552d9d0ee62072f47ab6f7444686d5aa`.

This closes the scoped test correction. Endpoint integration, historical cache recovery, broader startup availability, physical-device and shipping qualification remain open. Earlier entries below retain the investigation history.

## API 26 fixture open-mode correction — 21 October 2026

The retained diagnostic reproduced the original checksum failure and found only the paired SQLite change counters advancing from 46 to 48. Every other byte and all 38 typed SQL table images were unchanged. The scoped test Context enabled WAL after opening the database; Android 8 ContextImpl instead supplies WAL in the initial open flags. Both fixture overloads now follow that platform behavior, including the localized-collator flag.

The original whole-file checksum assertion remains unchanged. Temporary before/after diagnostic copies are removed from this corrected fixture, while their committed source, failed run, raw files and analysis remain retained in the evidence. Production code is unchanged. The corrected fixture still needs its narrow build and three-case native reruns on API 26/34/36; this is not yet a passing qualification or full audit closure.

## API 26 database reopen diagnostic — 21 October 2026

Startup candidate `14d6d5589` passed 1,960 fresh unit cases with four existing skips and four fresh zero-error lint reports. API 34 and API 36 each passed all 499 native cases with independently checked raw database/preferences evidence. API 26 passed 467 core and 29 common cases, but all three app cases failed only the final closed-database raw checksum after the second Room opening; preceding wallet/private-key/public-identity/typed-preference assertions passed. The byte change remains unclassified.

The instrumentation fixture now retains closed before/after database copies around the original, unchanged checksum assertion. This is diagnostic evidence capture, not a fix or a weakened assertion. Production sources are unchanged. The next check runs the small malformed-wallet-journal case on the same owned offline API 26 profile while preserving every earlier fixture. Full startup/C3/historical and release qualification remain open.

## Startup availability qualification in progress — 21 October 2026

Startup now opens Room and reconciles the TON and wallet journals without a whole-file size or integrity scan prerequisite. The read-only public recovery view also accepts a large database while retaining bounded row/value queries. Recovery revalidation visits each bounded key name and reads only its owner, so an unrelated malformed identity or a large inventory does not suppress healthy items. Exact compare-and-set and secure-storage failure handling are retained.

This candidate combines the independently reviewed 56-case per-key host checkpoint and 41-case startup/read-only host checkpoint. Three new Android fixture methods use public seeds replayed through the shipped 3.7.4 writer; they compile and cover current83 cache growth plus malformed reached journals after a small shipped71 upgrade. Native source and compiler/public-writer provenance have independent review; full Gradle and device qualification are pending. A successful startup does not certify untouched database pages. Historical inputs already containing large caches, broader cache repair and the full U0/release gates remain open. No production-readiness or native-pass claim is made here.

## Inactive endpoint attachment and Restore accepted, 2026-10-21

C2 source e4c45589ab2b535fdd6fbdc7c3fa54073384b61c is integrated with all
4,553 non-document files byte-exact to the independently accepted build.
Explicit Add and Restore verify the chosen network and establish inactive
ownership; neither operation selects an endpoint. The Room schema guard also
checks the exact temporary invalidation objects used by Room 2.7.2.

Validation: 1,966 fresh unit passes and four inherited skips; 1,458 fresh native
passes across API 26/34/36 (443 core, 41 components, one actual-PIN attachment and
one actual-PIN Restore test per API), plus 87 common native passes reused on the
identical archived APK and unchanged common source. All four scoped lint reports
have zero errors; core/account/common reports are reused, with 0/67/288 warnings,
and the fresh app report has 100 warnings. Earlier failures remain in the evidence.

The qualification and exact-source verification are recorded in
`endpoint-attachment-qualification.json` and
`endpoint-attachment-integration-verification.json`. This acceptance covers the
retained public fixtures and emulator journeys. C3 explicit selection/runtime
acknowledgment, U0 startup availability/cache/history, OS process death, physical
biometrics/accessibility/design and exact shipping gates remain open. The full
production readiness audit is active.

## Authenticated offline endpoint editor accepted, 2026-10-21

The editor at source bb2c140997c4308fa22c7bea26d93f21a1ce2d65 is integrated
with every non-document source file exact to qualification. Checks cover 1,823
unit passes/four inherited skips (explicit prior reuse), 1,242 core native passes,
105 rendered component passes and accepted retained public-fixture PIN/editor
journeys on API26/34/36. API26/34 qualify actual82-to83 startup and five saves;
API36 combines preserved partial-pilot upgrade/saves with the successful final
no-resave Home/recreation continuation. Failed runs and narrow SQLite sidecar
metadata dispositions remain in the evidence. Production/core APK bytes are
unchanged by the final fixture corrections; fresh app lint reports zero errors.
This closes the authenticated offline PENDING-intent editor checkpoint. Explicit
network verification/inactive attachment, separate selection, broader startup
and historical, physical/design and final release gates remain open. See
endpoint-decision-editor-qualification.json and its integration verification.

## Offline endpoint decisions — backend qualified, 2026-10-20

Source `405d2a2f5f0106e4303cfb2f1554cdd0a27447a4` adds bounded offline
PENDING intent, exact source/target/revision checks, a one-use commit grant and
PIN-lease publication fencing. Schema 83 adds the decision table; schema 82 and
the oldest supported version 9 remain unchanged. The corrected typed-source
fixture uses genuine production captures and retains corruption rejection.

Qualification: 1,139 fresh unit passes, four inherited skips; 1,215 native
passes (405 on each API 26/34/36), with no failures or skips. Lint aggregates
reran; reports were reused byte-exact from the accepted baseline: zero errors,
core/account/app warning counts 0/67/100. Retained app DB/preferences and five
PIN proofs per device remain exact against both prior successful authentication
and failed native baselines. Installed app identities, UIDs and controlled
environments are unchanged. The retained application remains on schema 82.

Evidence: `android-endpoint-offline-decisions-v1/qualified-backend-v1.json`,
SHA256 `9e8b25e0b39bbdf982e4d608f29d266fa905cf0a1fd5245883515a00a1b5852d`.
Every non-document file is exact to the qualified source. This integration
records source continuity, with no additional build or device execution claim.
The verified build used utils `fbbc2802` and WebSocket transport `e8903f4c`.

Authenticated editing forms and the actual schema 83 app upgrade/PIN journey,
explicit genesis verification/atomic inactive attachment, separate acknowledged
selection, and broader startup/historical/physical/design/release gates remain
open. Earlier failures and qualifications remain preserved below.

## Saved endpoints and rollback preservation — qualified source integrated, 2026-10-20

Source `2dffe5e6597e2076ab4498633d25b6df1b3d866a` integrates the qualified
historical/current endpoint preservation, bounded global inventory, authenticated
recovery UI and API 26 compatibility corrections. Every non-document file is
byte-exact to that source; earlier integration records remain preserved.

Rollback/current ownership has 1,035 native passes with no failures or skips.
The global checkpoint has 201 component passes: 67 fresh API 26 outcomes and
134 explicitly reused API 34/36 outcomes. Its API 26 PIN journey is fresh at
this source; API 34/36 retain verified `279cd4dc` fixture provenance against the
identical application APK. Each verifies five sources, 16 byte chunks and four
PIN lifetimes. Full protected data comparisons admit only the exact native
startup bootstrap and declared owned revision increment. All failed attempts
and profiles are retained.

The current build has fresh app lint with zero errors and 100 warnings;
1,945 unit passes and 14 inherited skips are reused from unchanged sources.
Integration itself makes no new build or runtime claim. See
`global-saved-endpoints-qualification.json` and
`global-saved-endpoints-integration-verification.json` for source and evidence.

Offline decision persistence, verified attachment, separate acknowledged
selection, broader startup/historical coverage, physical/design and signed/store
release gates remain open. These synthetic fresh-schema-82 fixtures do not prove
retained 81-to-82 upgrades or operating-system process death. The full audit
remains active; no production-readiness or publication claim is made.

## Historical harness and API 26 migration compatibility — independently accepted, 2026-10-19

Qualified source `5327c6a5d4636ff63d222394002f302ff85b7e57` fixes the API 26 SQLite upgrade blocker:
the final global foreign-key check uses raw PRAGMA while retaining every ordered
schema, relationship, parent-key and row guard. Only exact declared child/parent
pairs are tolerated. Malformed or unexpected violations fail closed and the
cursor closes on exit. The shipped migration manifest now has a separate name,
so strict historical schema discovery works without changing fixture bytes.

The final instrumentation APK passes 558 selected cases: 186 on each of API 26,
34 and 36 (55 preflight, 43 historical and 88 caller regressions). No native
failures or skips remain. The same 96 unit outcomes remain: 92 passes and four
inherited skips, reused unchanged by the final fixture-only build. Core-db lint
reports no issues; configured Detekt passes but excludes core-db. Seventy
packaged historical fixture files and two shipped migration assets are exact.
Prior provenance checks verified all 68 pinned historical APKs and regenerated
35 schema fixtures; ten extractor tests passed. Test helpers retain their original
assertions; three unsupported column-renaming fixtures now preserve rows and
constraints through portable table reconstruction.

Candidate `46a0c350a12223d86fa1b0477632a89c57941d3fa957cf822975ac56e0fb52d3` and independent review `e1773d46ca553c11a712bfc10109582018311472d4d37dd94a994c35b0afc48f` bind 4,453 app files,
377 utility files, 85 transport files and 467 evidence files. Original failing
runs are retained. Source-wide release checks, full schema-scoped U8, installed
writers, process interruption/relaunch, native UI, physical/biometric and
signed/store gates remain open. In particular, pre-40 custom-node loss requires
separate reproduction and correction. These passes do not complete the
3,014-cell release inventory or authorize publication. See
`docs/historical-fk-portability-validation.json`.

## Delegation options native window ownership — independently accepted, 2026-10-19

Source `4d365b5ab6edf0ac27fcff9c1820decb701233dc` retains the exact delegation options dialog in its StakingFragment and dismisses it when the screen pauses, its view is destroyed or navigation changes its owner. Admission checks the captured screen before and after presentation; dismissal clears only the matching owned reference. Normal row callbacks and ViewModel preparation remain byte-exact. Dismissing a row-selected sheet does not cancel admitted asynchronous collator details.

The four original native baseline cases have two genuine window-lifetime failures after Home/navigation and two healthy Back/recreation controls. All original bodies/deadlines remain exact; the final five-case suite adds rejection of a retired real screen entry and fresh-current recovery. Same final APK pair passes14 native methods:5 options,4 unchanged adapter and5 unchanged management cases, with203 JSON phases (63/14/126). Management retains8 terminal qualification proofs and11 exact single synthetic injections. Seven protected XML values, two wallet/four chain rows, settings, readiness and crash buffers are exact across all three suites; six comparisons use scratch DB/WAL copies only.

Independent final review `2dc2e8a712e842c93e6c49441fac6a81f1109c4e5d82ec24ebcfe4dbfd42ee41` verifies4,352app/376utilities/84transport files against working trees and Git, two changed source/test files and1,175 evidence pins. Broad3,332 outcomes=3,322passes/ten unchanged inherited skips, all prior3,332 outcomes and144 focused cases exact. App/common/staking tests executed; six unchanged module tasks reused up-to-date outputs. Debug APKs, lint zero errors/100 unchanged warnings and configured Detekt pass; staking remains excluded from Detekt. Candidate `b9041f858708a881db61f81ba1f998e12f5799e6c605fa349208815d869697dd` retains the genuine baseline failures.

This proves bounded native window lifetime with a public presentation-only collator model. Actual rendered-parachain provenance, row-route navigation, double-tap/stacking, visual/physical acceptance, shared-account/registry provenance, historical upgrades and signed/store release gates remain open. No production-readiness or publication claim. See `docs/parachain-options-window-validation.json`.

## Parachain body and details action ownership — independently accepted, 2026-10-19

Source `3db78e1e8bb94bc5c4b31335fa2923b4a01116be` carries each canonical role observation and source lease into published delegator bodies. Root callbacks require the current publication, role, wallet/account/chain/selection and screen lifetime. Details preparation checks authority around identity and APY waits; only the owning root routes the prepared result. Source metadata exceptions deny action admission while cancellation propagates. Seven original failing methods across related action boundaries and one separately reproduced metadata exception now pass; these are not eight distinct vulnerabilities.

Independent final review `ac781a08d5bad069d3d63677b283d2029b97dbb4421c8a7b84be690d3633d891` verifies all4,351 app/376 utilities/84 transport source files against working trees and Git, four changed source/test files and1,100 evidence pins. Broad results are3,332 named outcomes:3,322 passes/ten unchanged inherited skips; all3,310 prior outcomes exact and144 focused cases pass. Original12 baseline method bodies and122 earlier cases remain exact. App/common/staking tests executed; six unchanged module tasks reused up-to-date outputs. Debug APKs, lint zero errors/100 unchanged warnings and configured Detekt pass; affected staking code remains excluded from Detekt. The final-source APK pair passes four unchanged native adapter and five management-window methods with139 JSON phases (14adapter/125management). Compared with the prior140 phases, one incidental startup-awaiting-recognized-focus event is absent; all8 required terminal qualifications and11 exact synthetic injections remain. Seven protected values, two wallet/four chain rows, settings and crash buffers remain exact; capture readiness is verified. Database comparison uses disposable DB/WAL copies only.

Candidate `bcaf4f5ca19d19d5f1855cc42663292342b1515591ee9f2861291fb7c6a8fc0e` preserves compile, virtual-time fixture and metadata-exception diagnostics. No native actual-parachain options-sheet lifetime/dismissal/stacking or downstream destination authority is claimed. Same-role automatic retry, shared-account wallet and registry provenance, numeric/visual design and historical/physical/signed/store gates remain open. No publication or production-readiness claim. See `docs/parachain-body-action-validation.json`.

## Parachain alert role retirement — independently accepted, 2026-10-19

Source `12f3460ca72a83525cfded473078d5bed09530cb` retires each earlier role observation before downstream alert calculation can suspend. The shared stream carries that exact observation into each callback, which also requires the original wallet/account/chain/selection lease. Four reproduced stale-collator callback methods now pass; these exercise one authority boundary, not four distinct vulnerabilities. All original nine baseline method bodies and111 prior focused cases remain exact, with two additional cancellation/round-trip controls.

Independent final review `05e9fa0870833d88eaabadb00c7b77db4c095c7233ef1334ae752820dad2dff8` verifies all4,350 app/376 utilities/84 transport source files against working trees and Git, two changed source/test files and1,045 evidence pins. Broad results are3,310 named outcomes:3,300 passes/ten unchanged inherited skips; all3,299 prior outcomes exact and122 focused cases pass. App/common/staking tests executed; six unchanged module tasks reused up-to-date outputs. Debug APKs, lint zero errors/100 unchanged warnings and configured Detekt pass; affected staking code remains excluded from Detekt. The same final-source APK pair passes four unchanged native adapter and five management-window methods with140 JSON phases. Pre/post capture readiness is verified; seven protected values, two wallet/four chain rows, settings and crash buffers remain exact. Database comparison uses disposable DB/WAL copies only.

Candidate `e354929ff51a8008eeff1b9849cba246d22c474f44fd629fc7af590c12b817bd` preserves baseline/intermediate diagnostics and previous native timeout/recovery lineage. Actual-root ReadyForUnlocking is covered; ChangeCollators/CollatorLeaving, native parachain source provenance, broader body/options callbacks, post-await details routing, numeric/visual design and historical/physical/signed/store gates remain open. No publication or production-readiness claim. See `docs/parachain-alert-role-validation.json`.

## Root staking alerts and adapter — independently accepted, 2026-10-18

Production `135ff7244` resolves 14 reproduced root callback authority failures and two native adapter failures. Source `d34b84f0a` adds the independently reviewed synchronized management-window fixture; production and JVM test bytes are unchanged and its application APK is byte-exact. Broad results remain at135ff: 3,299 named outcomes, 3,289 passes and ten unchanged inherited skips; all3,275 prior outcomes exact,111focused pass. Four native adapter cases pass at135ff and five native management-window cases pass atd34, with140 raw JSON phases. All4,349 app/376 utilities/84 transport files and1,431 evidence pins are independently verified. Seven protected values, two wallet/four chain rows and settings remain exact. SQLite review used disposable DB/WAL copies. Debug APKs, lint0errors/100unchanged warnings and configured Detekt pass; affected staking code is excluded from Detekt.

The earlier management4/5 timeout remains unclassified. Diagnostic5/5 exposed competing real publication; the final helper waits for an exact current terminal qualification without relay authority, injects once and rejects competing publication without increasing deadlines or retrying. An interrupted preferences write was restored by normal unchanged-app launch; the original public PIN still opens the portfolio and all protected data remain exact. No manual preference rewrite. No screenshots/readability, actual root source provenance/action-row route, parachain retirement or production-readiness claim. Those gates, other native/UI/authority work and migration/physical/signed/store gates remain open.

Candidate `1a75e8d8bb02176c5483918b3ff6ef208b9a4bd171b05dbff45f71901aa02477`; independent final review `414627e1b5c9e3f37e4cd27278a5edacb109f4a1bb416e8e42065f60f464c0fe`. Complete raw evidence and source lineage are linked in the saved validation.


## Relay staking scenario and summary — independently accepted, 2026-10-18

Source `7b6a31246` (production `8138ac692`, focused `3d1fb9a8c`) resolves 17 reproduced stale relay-body failures. The lower summary carries exact public wallet/account/chain/asset denomination/type/bonded input, withdraws pending recalculation and cancels obsolete work; the scenario rejects independently lagging asset/summary combinations before rendering.

Independent review `9b02cb78a9b3764cac00050f77d0ca305e565bafb6ec2d5551485c0a8c0df652` verifies seven changed source files, 4,345 app/376 utilities/84 transport files against Git and current source, all 933 evidence pins and both APKs. Raw broad results: 3,236 named methods, 3,226 passes and ten inherited RuntimeSyncService skips; all 3,210 prior outcomes unchanged, all 26 focused methods pass. Lint has zero errors/100 warnings and is byte-identical to the prior report. Configured Detekt passes but excludes affected staking code. Test-only fixture corrections and the one coherent-input setup adaptation are retained; original assertions remain unchanged.

This checkpoint qualifies the bounded JVM/build behavior. Actual top-level rendered model/action authority, debounce, scenario scope lifetime, detached reward synchronization, native/device continuity, migration and signed/store gates remain open. No native screen, public transaction or production-readiness claim. Candidate SHA `00c03138fe2c05e0a10e52f10131512fea81b1b48785705ba0cfe619b5fb2b97`.


## Staking management presentation lifetime — independently accepted, 2026-10-18

Qualified source `292e998af` (production `e2b9f1f16`) resolves eleven reproduced unit failures and three actual native-window lifetime failures. Exact presentation identities, synchronous revocation signals and identity-owned Fragment dismissal prevent obsolete management callbacks or windows from surviving context/departure/replacement changes.

All 199 focused methods pass. The broad invocation records 3,210 named methods: 3,200 passes and ten inherited runtime skips. Debug APK assembly, lint zero errors/100 unchanged warnings and configured Detekt pass; affected modules remain excluded from Detekt. The same instrumentation APK gives three failures/two healthy controls on the prior app and five passes on the corrected app. All five method bodies/deadlines are exact. Seven protected records/two wallets/four accounts remain exact; settings restored and crash buffers unchanged.

Candidate-v2 SHA256 `868caf2d1cc678256ac13891441fddcc3d0c81ec3738591e9ea4f5d46845a84b`; independent final review `92e9c96d5520d58ef7ec1fa681793568ebbf000e615dba72ac97ebbf9e013a7a`. Earlier fixture and reviewer-precondition failures remain retained and explicitly separated. Synthetic rendered input qualifies window lifetime only. Rendered-model freshness, eligibility/action rows, other dialogs, amount/session and downstream authorization remain open. Black emulator captures do not qualify visual design. See the saved qualification and final-review JSON; source integration verification follows.


## Pool member compatibility integrated acceptance — 2026-10-14

Source `e77e5b2f4` is independently accepted for a single runtime-compatible pool unbond/withdraw attempt. Review SHA `7d682720c2e6c34626f631952a99206fc7927481db51b5ad1a149b27c415996d` verifies2,303 named unit cases/zero failures or skips,22 new methods, six reproduced baseline defects, both Debug APKs and lint0errors98warnings. Configured Detekt exclusions and up-to-date unit tasks remain explicit. Integration verification `820954039ae84d51dec175e38aa08e8e05988cb0de139192dc75e155b2051a9b` proves4 changed non-document files and the whole non-document tree match source5bc32ac8e, with4,304app/376utils/84transport and483 evidence pins exact. Earlier candidate pending-review language below records its freeze and is superseded here. Two documentation-only conflict pairs preserved prior accepted sections.

This closes automatic compatibility retry after ambiguous/cancelled output at the API boundary. It does not close mutable wallet/selection/lifecycle authorization, manual retries, durable outcome recovery, complete live-chain/native migration or release qualification.

## Pool unbond/withdraw single-attempt compatibility — in progress, 2026-10-14

The corrected actual API/service/SCALE baseline reproduces six extra-request failures with ten healthy cases: unbond and withdrawal each retry a signed transaction after ambiguous output or cancellation, and both retry a failed fee RPC. A runtime format change between attempts lets both original and replacement envelopes encode. The first mocked-service attempt had Kotlin Result boxing and copied-exception identity fixture failures; those are preserved and excluded from product counts. All tests use public local keys and mocked RPC, with no real network.

The candidate resolves member_account against the exact ExtrinsicBuilder runtime before adding either call, validating MultiAddress or raw account SCALE locally, then issues only one submission or fee query. Network exceptions and failed Results cannot trigger another encoding/signing attempt. Both old and new argument formats remain required regression controls. Expanded unit-v3 passes325 staking cases with zero failures/skips/source drift, including22 new methods. Both operations cover32-byte Id/raw,20-byte EthereumBased Address20/raw, aliases and unsupported/unresolved/wrong-length metadata with zero RPC output. Earlier unit-v2 preserves two fixture failures from inconsistent Substrate ecosystem with a hex address; those occurred before the encoding path and are excluded from product failures. Full build and final independent review are pending; broader wallet/lifecycle binding and durable outcome recovery for these operations remain open.

Final candidate `5bc32ac8e` passes2,303 unit cases across eight modules with zero failures/skips, Debug app/test assembly, lint0errors98 inherited warnings and configured Detekt (affected modules excluded). All4,304app/376utils/84transport snapshot/Git pins match with no drift. Manifest `docs/android-pool-member-compatibility-validation.json`, SHA `909dc4dbc293dc25e6b1757b87adbc592c6f7e01b9e474f968ae92cee6a7dd5d`, binds6 changed files/483 evidence files. Independent source reviewv3 `8f5019697e706d1dd5e8163f3e2696ffe6f666116f0344a5bc663b5050766377` accepts the bounded source and325-case focused run; final evidence review/integration remain pending. No new installed/native presentation or live-chain claim is made for this data-path correction.

## Pool recovery integrated acceptance — 2026-10-14

Recovery source `db3639a7a` is independently accepted within the bounded read-only scope. Review SHA `b79a8d80b2eea4c9e68aba52c38b5c815aaedad32aeacdbe71a97493cc1e70ed` verifies the candidate, all 2,701 evidence pins, named unit/native results, actual installed Back/reopen and protected-wallet continuity. Integration verification SHA `2cfe9244c1f0a59b806890eb08b43f820aaf42358b1e29a595ed2bcec3a90209` proves all 52 changed non-document files and the complete non-document tree equal qualified source `6abb8f91d`, with 4,302 app/376 utilities/84 transport source and Git pins exact. Two documentation conflict pairs preserved all earlier accepted sections. The final candidate and its detailed limits follow below; their pending-review wording records the earlier freeze, now superseded by this acceptance.

Other operations' retry/wallet/lifecycle binding, unanchored historical pending outcomes, configured-RPC trust limits, broader native/physical/migration and signed release gates remain open. This is not production-readiness or publication approval.

## Pool recovery final candidate — evidence review pending, 2026-10-14

Final source `6abb8f91d` provides independent recovery from pool staking, preserves the original wallet/network/account/payment, and performs bounded read-only finalized-block checks. The installed fixed app now permits system Back while idle and checking, including after a system text-size change. The previous non-hideable Material sheet trapped system Back; both the corrected native regression and actual installed public-wallet baseline reproduce that defect before the scoped hideability fix.

The final full check passes 2,281 unit cases with zero failures/skips, Debug app/test APK assembly, lint zero errors/98 inherited warnings and configured Detekt. Some unit tasks are up to date; this is not a claim of 2,281 fresh executions. Configured Detekt excludes affected modules. All 4,302 app/376 utilities/84 transport source pins match Git and the build snapshot without drift. App SHA `d88b2a114fce6bdf72eceb9dc05a32a16cc80d942a9fa03bc3cf9d1573b8209b` is the installed final artifact.

Native final Back policy passes one method on initial and recreated hosts. Three opt-in, offline fixture methods prepare an impossible never-submitted public transaction, verify it in a fresh process and remove only that exact fixture. Original PIN unlock reaches pool staking with zero DOT and offline networking; the actual 37 DOT/pool8/account/hash record survives idle Back, doubled text plus in-flight-check Back, and both reopens exactly. Toolbar Back also returns to the origin. Secure-window PNGs are black by design; XML/actions qualify navigation and retained state, not visual rendering. All two wallets/four accounts/seven protected records remain exact and SQLite quick-check passes; original network and font settings are restored.

Unchanged component coverage is bound separately: six actual-preference methods plus one process-kill phase at `669beb8bd`, and five native graph/Compose methods with 128 recovery layouts/16 entry layouts/272 raw PNGs at `297381704`. The final change after those layouts affects only Fragment Back policy and two native fixtures; the installed final APK covers that policy. Earlier 96-layout/192-PNG Confirm content coverage remains historical and is not counted as final whole-APK qualification.

Candidate SHA `9b43e39490334a4410fbab1914b55de08cf09b92ec36ea18f5c00c1c969d89dc` binds 55 changed files and 2,701 evidence files in `docs/android-pool-recovery-validation.json`. Independent final evidence review and integration remain pending. Failed baselines, fixture corrections and compile/setup failures are preserved. Earlier progress entries below describe their own checkpoints and are superseded by this status.

Configured RPC trust, absent block-body trie/consensus proof, inclusion-versus-dispatch distinction, unresolved unanchored legacy records, other operation callers, broader native/physical/predictive-gesture checks and full migration/signed-release gates remain explicit. No public transaction, signature, rebroadcast or expiry-based clearance is performed; XCM remains paused.

## Pool join read-only reconciliation — in progress, 2026-10-14

Added a bounded RPC reader that binds exact original bytes to canonical finalized block identity and recomputes SCALE/Blake2b-256 header hashes. It only issues chain reads. Misses, pruning, malformed replies, cancellation and network/finality drift cannot establish non-execution. Under the existing configured-RPC trust model this is not consensus or extrinsics-trie verification, and inclusion does not claim dispatch success.

The journal can atomically archive the exact original transaction and public inclusion before clearing only its pending scope. The reconciler retains exact attempt ownership and invokes synchronous review completion only after durable archive acknowledgement/readback. Focused reader/storage and prior actual pool caller tests pass; a same-height wrong-header regression was reproduced and corrected. Compiler/fixture diagnostics remain preserved. API/UI wiring, historical discovery availability, native qualification, full checks and independent acceptance are still in progress. No new payment is authorized by an RPC acknowledgement or a failed lookup.


The first independent review reproduced two safety gaps: an unanchored old identical inclusion could clear a fresh attempt, and a failed SharedPreferences commit could mutate memory and escape a per-instance fence. New joins capture and retain a canonical finalized anchor before signing; v1 attempts stay readable but unresolved. Storage identity now shares a scope-specific failure fence across recreated PreferencesImpl wrappers, including archived reads. The frozen anchor/fence run passes797 tests (620common/92core/85selected staking), zero failures/skips/source drift. Independent review `9b5541537842fc9dc381d92df3a359439c6942a9533bba0a46018adfd1bb9453` checks11 source pins and the two genuine failing baselines, resolving both findings at this unit scope.

The current unqualified presentation adds original amount/pool/account/hash, eight-locale pending/legacy/error/inclusion messages, read-only recent and older block checks, and a separate return to pool after inclusion. Recent checks start again at the latest finalized head. Actual caller recovery, cancellation and stale-review tests are running in `android-pool-recovery-presentation-build-v1`; native layout/actions, process archive durability, full final checks and independent acceptance remain pending. It never presents inclusion as successful staking or offers replacement signing for an unresolved attempt.


Presentation v2 passes980 tests (620common/92core/268staking), with zero failures/skips/source drift. The corrected race baseline executes62 actual caller cases with57 passes and five genuine failures: held validators prevent recovery publication; three departures during awaited pending lookup still display stale errors; cancellation immediately after durable archive loses its usable return state. Those are fixed by separate validator-flow loading, post-await ownership checks and synchronous-receipt recovery in finally. Earlier Flow access, spy setup and matcher/state-emission fixture diagnostics remain preserved and do not count as product regressions. Source review remains pending on the fixes.

A separate independent entry-path review (`11fb0f626f0a9f27687b9e1fc54afb7730864679cfeb5336ff059a8d214c93ac`) confirms that recovery solely inside Confirm is insufficient: fresh amount/fee/pool-list validation and changed pool membership can prevent entry after restart. A direct read-only route independent of these gates is still release-blocking. This checkpoint is not complete recovery acceptance. Native all-locale presentation and actual archive/restart fixtures are prepared but not yet executed; broader checks are next.

## Pool join uncertain output — independently accepted bounded retention, 2026-10-14

Source `c236bcfe2` retains exact signed bytes, local transaction hash, prepared intent and amount/pool in the original wallet/network/account namespace before RPC. The attempt gate seals late callbacks before an exactly owned, positively unsent record can be removed. Lost/cancelled/wrong-hash responses retain the original record. Matching acknowledgement consumes known success before status persistence; a failed status write does not authorize replacement. Both unresolved and acknowledged records prevent another pool signature. Storage and output hold no review/wallet/attempt monitor.

The baseline reproduces two real duplicate-payment failures with32 healthy cases. Final full qualification passes2,039 unit tests/zero skips (619common71core568account427wallet228staking9Polkamarkt117XCM);78 focused cases pass. Separate136-case expanded run also passes. Debug app/test APKs, lint0errors98 inherited warnings and configured Detekt pass with4,284app+376utils+84transport source/Git pins unchanged. Configured Detekt excludes affected modules and supplies no changed-module coverage claim. Earlier compile-only fixture failures and the known-success-before-status-write timing fixture failure remain preserved and excluded from product regression counts.

Actual PreferencesImpl/SharedPreferences native qualification passes three methods plus one intentionally terminated writer phase. The pending record survives host force-stop during admitted local output (PID23212→23337); the acknowledged record survives restart (23409→23483). Both exact serialized record digests match. Fake public bytes only, no valid signed transaction or fixture network request; this is process termination, not physical power-loss or real RPC qualification. Existing2wallets/4accounts/7protected records remain exact, SQLite quick-check ok; no new PIN UI journey is claimed. Device Wi-Fi/mobile settings were restored.

Candidate SHA `c64a813a42b1583fc726ff6df51b451f289b6ba8e32e7567840df5764c3d5231` binds15 changed files and450 evidence files. Independent final review SHA `1360f29322b51fca9ec4b39533765870ef25103655dde9c3a9b51798052d4a69` accepts this bounded retain/no-rebroadcast stage. Integration source `069104379` and manifest `224d7aacc` exactly match the qualified source outside documentation; integration verification SHA `50c4b889bbdedacdf040e9f2831e149b04f51cb3748d0bef56043aaba0941487` rechecks all13 changed non-document files,4,284app+376utils+84transport source/Git and450 evidence pins. Prior accepted documentation sections were preserved. See `docs/android-pool-retention-validation.json`.

There is no automatic rebroadcast or expiry/nonce-based clearance. Localized pending/recovery UI, proven chain reconciliation and safe future join availability remain required; retained acknowledgement is node acceptance, not inclusion/finality. Account reaping can invalidate same-byte replay assumptions. Other pool operations/unbound callers, full migration/design/physical/signed distribution gates remain open. No public transaction is performed; XCM remains paused.

## Exact wallet signing namespace — independently accepted, 2026-10-14

Pool setup, quick-input estimates, confirmation fees and join submission now carry the original reviewed asset.metaId through the interactor/API to wallet-bound extrinsic entry points. Public crypto metadata and root/per-chain key lookup resolve that exact wallet and verify account ownership; equal accounts in earlier wallets cannot substitute their metadata, keys or quarantine state. Fee construction remains public-only with inert signatures; guarded submission retains the prior revocable key/sign/final-writer admission boundaries. Other unbound callers remain separate work.

Final source `55071ba25` (production identical to `17e495b08`) passes 2,013 module tests without failures/skips:619 common,68 core,568 account,427 wallet,205 staking,9 Polkamarkt and117 XCM. All145 focused cases pass, including the four original repository failures and wrong fee-envelope regression, actual guarded secret-store decoders over in-memory preferences, actual pool caller/SCALE and retained setup/quick-input wallet42→99 controls. Debug assembly and lint0errors98 inherited warnings pass; configured Detekt excludes the affected modules. All4,278 app+376 utilities+84 transport source pins match the build snapshots and Git with no drift.

Candidate SHA `65568cc46d6a1d52d05bafd90cde3ed33b3e3d8ad16becd31d88accfb9e6d69f` binds17 changed files and796 evidence files. Independent source review SHA `8134bb5f97008540b01a0369b69b82899eacbd52b126b4a08c1d43b2d63757a4` accepts the production source; final independent evidence acceptance is complete. Earlier JUnit/Mockito/in-memory provider fixture failures remain retained and excluded from product regression counts; fullv1 stopped at a null fixture asset id, fixed by the test-only final delta. No failed or incomplete run is counted as a full passing gate.

No real encrypted wallet, database/device upgrade, public transaction, process durability or release qualification is claimed. Public deterministic fixture keys only; XCM remains paused. Ambiguous output reconciliation, broader unbound callers, other pool operations and the full migration/design/physical/signed distribution gates remain open.

Independent review SHA `f94995e6cfd26aef9aa0fda4781ab95d260fe5a58ca12e65756c75b867870d67` accepts the bounded signing and fee namespace fix. Integrated through `09839fc3e` (production `405b11c6c`, final test correction `430bfeaa7`); verification SHA `e1ffdbc505e3ef0f47585f1f971a819735b757de136753687e8d9961358b8f69` confirms all4,278 app+376 utilities+84 transport current/Git files,15 changed non-document files and796 evidence pins. No source mismatch; accepted prior sections survive the two documentation conflicts.

## Pool execution revocation — independently accepted, 2026-10-14

Pool join carries local review authority through the guarded extrinsic service. Prepared intent, original main-state revision, exact join claim, wallet-selection epoch and attempt lifetime are checked at key access, signing and final writer admission. Main/join publication and Back/dismissal revocation share one lock; local secret/signing primitives serialize with wallet selection. The final writer callback atomically admits at most one output operation, then releases navigation and wallet-selection locks before potentially blocking write/flush. An already-admitted operation may finish after revocation; admission is neither acknowledgement nor permission to retry. No remote capability/configuration gate is added to healthy wallet access. Known submitted success still consumes only the original claim before cancellable API dispatch-back.

Baselines reproduce ten distinct failures: eight stale transport handoffs and two Back/selection stalls found during independent review despite1,299 earlier passing tests. Final source `95239e3d9` passes79 focused cases and1,438 module tests without failures, skips or source drift (619common/64core/427wallet/202staking/9Polkamarkt/117XCM). Debug assembly and lint pass with zero errors/98 inherited warnings; configured Detekt excludes the affected modules and establishes no analysis coverage for them. The79 focused cases comprise31 actual caller cases, seven authority/concurrency cases,35 retained submission cases, four reviewed-chain/known-success cases and two RPC-admission/default-policy cases.

Held-output tests use the actual source-pinned GuardedWriteHandle with a local OutputStream held at write or flush, exact frozen bytes, rejected admitted cancellation, duplicate-write rejection and healthy acknowledgement. Three final cases invoke actual AndroidX ViewModelStore.clear during preparation, queued transport and admitted output, exercising the real clearing callback. These are JVM fixtures, not real socket, installed-wallet or Activity/Fragment lifecycle qualification. Baseline v1's fixture argument compile failure and candidate v3's missing test-classpath dependency are retained; copied stale XML is excluded from executed-test counts.

Candidate SHA `af46c9abb4cdfa69c0ff2b973c8508331d256a7c275213248a81153160a3d472` binds18 changed files,4,277 app/376 library/84 transport current and Git source pins, and839 evidence files. See `docs/android-pool-execution-validation.json`. Independent source review resolves EXEC-OUTPUT-1; final evidence acceptance remains pending. Exact wallet namespace during key lookup, ambiguous-broadcast retry (including cancellation before the API observes acknowledgement), native lifecycle/configuration, process durability, other pool operations and the full upgrade/physical/signed release gates remain open. Public local test keys and mocked RPC only; no public transaction, device installation, wallet-continuity or release claim. XCM remains paused.

Independent review accepts source `95239e3d9`, integrated through `2acf21762`. Review SHA `f8a71ccd73c1cc7ece144a04536da3ae3711939ba927d789901c0b2b85a2c86b`; integration verification SHA `55efdf2ae7dc4cebe6ca3dd5a9ed780a6b3b28c8e11fc91aadbb62a81156b619` confirms all 4,277 app, 376 utilities and 84 transport files against current and committed source, plus 839 evidence pins. The two documentation conflicts preserve prior accepted sections. Exact wallet namespace and ambiguous output reconciliation remain open.

## Fee estimation without private keys — independently accepted, 2026-10-14

Account-specific extrinsic fee construction now uses public crypto metadata, account, nonce and runtime information without reading a private key. Both fee overloads encode an inert signature placeholder with the runtime's correct representation and width. Submission paths remain unchanged. This closes the eager pool-fee key-access lead and applies the same correction to other users of the shared fee service.

Ten failed regression cases were reproduced across the core service and actual pool API. Source `d9b1bf91e` passes 18 focused checks and all 653 affected-module tests (62 core/427 wallet/164 staking), with zero failures/skips and source drift. The checks include all three crypto types, both fee overloads, held nonce/runtime lookups, failed public RPC, 20-byte Ethereum accounts, structured ECDSA signatures, batch/batch_all order, and equal legacy encoded length with byte-exact non-signature fields. Debug assembly and lint pass with zero errors/98 inherited warnings. Configured Detekt excludes changed modules and does not establish their analysis coverage.

Candidate SHA `f40c8ae7e14a5c022355487147061307abf6a2c942a1f6578b970176ffda93aa` binds three changed files, 4,273 app/376 library source/Git pins and 164 evidence files. Independent source and evidence review accepts this bounded checkpoint (review SHA `1e7e457c1512bf453a3f4058224463a13acf301f6ea5fa653e8c6709bd8e3ca4`). Integration at `ffdaa2e21` matches every source pin; verification SHA `b6536403468242fba88ae9109a20db289777f7929cbd2d1339c6a28991965fe1` records only documentation differences. See `docs/android-fee-placeholder-validation.json`. All dependencies used for runtime interaction are local test doubles. Only public local keys are used in the legacy encoding comparison fixture; no real wallet secret, public transaction, device installation or release operation occurs. Downstream submission authority, exact wallet namespace during key lookup, ambiguous-broadcast retry, full runtime/migration coverage and physical/signed release gates remain open.

## Pool wallet context and preparation — independently accepted, 2026-10-14

Join drafts now require their original prepared main-state snapshot and wallet-selection epoch. Welcome, setup, selection and confirmation reject replaced contexts and wallet A-to-B-to-A changes while preserving valid edits and consuming known successful submissions independently of stale presentation. Preparation captures wallet and synchronous staking-selection intent before launch; shared ownership and conditional publication prevent old, queued or reentrant preparations from replacing a newer flow. Both join and management entry points use this protection.

Corrected baselines reproduce 24 distinct failed cases, including seven preparation cases found during independent review. Source `20820b778` passes 101 focused cases and all 585 affected-module tests (427 wallet/158 staking), with zero failures/skips and no source drift. Debug assembly and lint pass with zero errors/98 inherited warnings. The staking API test task has no sources; its selection revision is exercised by the implementation tests. Configured Detekt passes UP-TO-DATE but excludes the changed staking modules. Candidate SHA `1442d334684447a89f94be857a11938bd4cd975915a6192ea172fda90560874e` binds 17 changed files, 4,272 app/376 library source/Git pins and 267 evidence files. Independent source and evidence review resolves PC-PREP-1 and accepts this bounded checkpoint (review SHA `0de1e062c64b30f1ba7130cd2ef6d1a6fd2d0a5965812f7e6760967a7b05832c`). Integration at `690e168ef` matches all source pins; verification SHA `5cbc6039a8fde5672d7498dc7243bf06ddc90f24087c1e0853d3dcd054a62336` records documentation-only differences. The earlier Mockito fixture errors and missed second-caller compilation failure remain separately recorded in `docs/android-pool-context-validation.json`; stale copied XML is excluded from executed-test counts.

This qualifies callers and preparation. Downstream key access, signing, submission and ambiguous broadcast remain open: the pool API still uses legacy extrinsic submission, and account-specific fee construction also reaches the key provider before confirmation. Native dismissal/configuration, protected-wallet continuity, process durability, other pool operations, the complete upgrade matrix and physical/signed release checks remain separate gates. These runs use local fixtures and perform no signing, RPC, public transaction or device installation.

## Pool join lifecycle — qualified candidate, 2026-10-14

A shared submission claim now spans confirmation instances. A known API success consumes only its owned draft before cancellable dispatch-back; reentrant observers cannot redirect the completion receipt to a newer draft. Back/dismissal revokes queued admission and presentation, repeated Back is idempotent, and old success or failure cannot clear or navigate over another flow. Setup and selection bind their original flow lineage, permit legitimate back-editing, reject unrelated replacements and explicitly exit consumed drafts. A separate edit generation invalidates older validation as soon as submission starts, including after a failed submission releases its claim.

Corrected baseline runs reproduce 24 distinct failed regression cases, with healthy controls. Source `1d7cb371a` passes all 55 focused cases and 539 affected-module tests (427 wallet/112 staking), with zero failures/skips and no source drift. Debug assembly and lint pass with zero errors/98 inherited warnings. Configured Detekt passes UP-TO-DATE but excludes staking and other affected modules; it supplies no static-analysis coverage for these files. Candidate SHA `590bdc4e967fffca914c4f1911960d19b14903681b926eb60e92359ae1d01057` binds 13 changed files, 4,267 app/376 library source/Git pins and 256 evidence files. Independent final review SHA `254fb00bafe9c428c89bc1f9c01cba51ba2a2b90de173861aa656e1ac43d6784` rehashes every source/evidence pin and verifies raw focused/full/baseline counts, with no further bounded blocker. Earlier fixture compilation, suspend-Result boxing and obsolete-control expectation failures remain preserved and separately identified in `docs/android-pool-lifecycle-validation.json`.

Fragment cancel/dismiss wiring is source-reviewed; actual native dialog/system dismissal and configuration journeys remain to be qualified. Wallet selection/authorization, main-state context, downstream cancellation or ambiguous broadcast before API-observed success, crash durability and other pool operations remain separate gates. These checks use local mocked dependencies only: no signing, RPC, public transaction, device installation or new protected-wallet continuity claim. Full upgrade, design and physical/signed release gates remain open.

## Pool review and chain context — independently accepted, 2026-10-14

Pool confirmation now captures a revision of the shared join draft, checks that revision before claiming and again before queued work starts, and only clears/navigates when the successful review still owns the draft. Publishing an equal value again does not revive an old confirmation. Setup and confirmation pass their displayed chain explicitly through the interactor and API for join fees and submission, so later global selection cannot redirect the operation.

Four actual ViewModel failures were reproduced for replaced/restored/queued reviews and late success. Two further failures were reproduced through the actual ViewModel, real interactor and real API with a local extrinsic recorder. Final source `0e8297de7` passes 15 focused cases and all 499 affected-module tests (427 wallet/72 staking), without skips; Debug assembly and lint pass with zero errors/98 inherited warnings. Configured Detekt passes from cache but excludes the changed staking module. Candidate SHA `42b8048170cbcdbc5021fbaf8e6a00e9afe07a40dfa4517c673ef944757a63fc` binds eight changed files, 4,265 app/376 library snapshot pins and 162 evidence files. Initial compile/matcher fixture failures and stale XML are retained and excluded from executed-product-failure counts. Independent review SHA `a2bdc118fedbd75ca377dbae92387e04dbdda53880c02569884a7e538650343e` verifies every pin and result. Source-exact integration `8cd7cb3b5` is verified by SHA `5b60bea24668de125c7eb27f243147fb5a35f3b51dd31616548283f0d9594b8c`; accepted integration documentation is preserved.

This checkpoint does not reserve submissions across separate ViewModels or revoke work after Back, dismissals or an already-started request. Stale error presentation, retained setup/select draft handlers, wallet authorization epochs, other pool operations and downstream ambiguous/cancelled broadcasts remain open follow-ups. Only public synthetic data and local test dependencies were used; there is no signing, RPC, device-installation, new visual/continuity or release claim. See `docs/android-pool-review-context-validation.json`.

## Confirmation submission ownership — qualified candidate, 2026-10-14

The actual pool and Send confirmation ViewModels now claim a submission before suspension, retain the claim after success even if navigation fails, and release refused/failed/cancelled attempts with loading cleanup. Send validation and transfer execute in one owned coroutine; validation errors are shown, warnings still require explicit acknowledgement, and an already successful review stays disabled. These changes protect caller entry points independently of loading-container input interception.

Corrected baselines reproduce five pool failures (one healthy control) and five Send failures (five healthy controls). The candidates pass seven pool and ten Send tests with no skips using only local held dependencies; no signing or network submission occurs. Retained initial diagnostics include Mockito suspend-Result boxing and a test that selected the wrong successful-navigation overload; corrected fixtures reproduce genuine failures before remediation. The first broader run passes 488 wallet/staking unit tests, Debug assembly, configured Detekt and lint (zero errors/98 inherited warnings). Independent review then reproduces four additional failures: known success lost during cancellation at the Send dispatch boundary, and Result-valued cancellation shown as an error in Send validation, Send transfer and pool submission. Known success is now claimed inside the submitting coroutine before dispatch-back, and cancellation is rethrown before presentation. All 21 focused tests pass (13 Send/eight pool); final source `99e246d37` also passes all 492 affected-module tests (427 wallet/65 staking), Debug assembly and lint with zero errors/98 inherited warnings, with 4,264 app/376 library pins unchanged. Configured Detekt passes from cache but excludes the changed wallet/staking modules; it does not provide analysis coverage of these files. Manifest v2 SHA `3f3f4f7432ccf97b61fe90b6364d8f8121f8a0cf83e6eb12a3148eec1144f8ca` binds four changed source files and 276 evidence pins. Independent final evidence acceptance remains pending. These are per-ViewModel claims, not durable transaction deduplication across process death or ambiguous broadcasts.

## Loading input — independently accepted, 2026-10-14

Source `53876ee49` passes 761 unit tests without skips (618 common/143 app), lint with zero errors/98 inherited warnings, configured Detekt and Debug assemblies. All seven actual-container interaction methods pass on APIs 30/31/34/36; each separately compiled global/surface paint rollback passes the same seven plus four paint/motion methods and four flag unit tests. The candidate also passes four paint/motion and ten existing wallet/navigation/mnemonic methods: 64 native executions total. Every one of the 321 comparison PNGs is byte-identical to the accepted presentation checkpoint. The original PIN opens the public wallet and both wallets appear in the picker; two wallets, four chain accounts and seven protected records remain exact with SQLite quick-check ok.

The preserved expanded baseline fails six of seven input tests. Persistent ancestor pointer interception now cancels covered and complete-cycle gestures; covered focus/keyboard activation and semantic actions are suppressed only while busy, and the original content composition and caller Back behavior survive. Paint rollback retains this input safety correction. Manifest SHA `531eea1f37ac488ee8c4ba94fa5f6fa63971b2e381839d7e1d5508082f92ca9d` binds four changed source files, 4,262 app/376 library source files and 676 evidence files. Independent review SHA `3a6937309d52defbd0d25a8c9f356736946da523bf4d7f86a59714545bc522f8` rehashes every source/evidence pin and independently reparses native/unit results, all 321 unchanged images and protected wallet state. Integration matches all 4,262 app/376 library pins and the complete non-documentation source tree. Physical assistive technology and caller-side submission reentry are separate gates; no transaction or release claim is made. See `docs/android-loading-input-validation.json` for failure provenance and setup limits.

## Loading appearance — independently accepted, 2026-10-14

Final source `8fc0cd8cb` passes 756 unit tests with zero skips (613 common/143 app), lint with zero errors/98 inherited warnings, configured Detekt and Debug assemblies. API 30/31/34/36 each pass four native methods, producing 320 locale/size/effect text layouts and 428 PNGs in total. The exact rendered text/surface/arc roles pass in every layout; text contrast is 15.794 and arc contrast is 11.884. Both compiled rollback modes pass four unit and four native methods each, restore all 24 original frames exactly, and match each other in all 107 PNGs. Ten required existing wallet/navigation/mnemonic UI methods also pass with 37 captures. The two installed public wallets, four chain-account records and seven protected records remain exact; the original PIN opens the wallet and both wallets appear in the native picker.

The default-off Loading flag under the global appearance boundary selects still skeletons and a static arc when Reduce Motion is enabled, a quieter 1,600ms ordinary shimmer, localized progress copy in all eight shipped locales, and an opaque progress surface over a blur-free busy backdrop. Actual line-bound checks prove complete labels fit the existing 320×120dp host at normal and double-size text. Manifest SHA `3b1cfc0c4435c94b95cfd7df0879c54fc8500ce54ac3d95fae12a8704543a92a` binds 20 changed source files, 4,259 app/376 library snapshot files and 969 evidence files. See `docs/android-loading-appearance-validation.json` for checks, failed baselines and setup limitations. Independent acceptance SHA `b28d73677d60c54ffdcc3a81bb28e82e86c10bdfccfe04533e4ba6f22213bb02` verifies the complete source/snapshot/evidence inventory and independently repeats pixels, frame equality, unit counts and public-wallet continuity. Integration matches all 4,259 app/376 library snapshot files and the complete non-documentation source tree.

The initial frame harness was built but not run because its sampling ended before the pinned library's 1,500ms initial delay; corrected sampling reproduced the motion failure before remediation. API 30 extraction required post-test debug root on the fresh public-fixture emulator after scoped-storage denial; denied airplane-mode setup and the absent API 31 phone service are retained without an offline-device claim. These checks qualify guarded loading presentation. FullScreenLoading input interception/modal semantics, physical assistive technology, remaining screens, the complete upgrade matrix and production release gates remain open.

## Raw colour guard — independently accepted, 2026-10-14

Source8d3767434 is integrated with exact equality across4,255 app and376 library files and no non-documentation differences. Independent review verifies all4 changed-source and586 evidence pins. All611 common tests pass with zero skips; configured Detekt passes. A real cross-module source injection first exposed a Gradle UP-TO-DATE false green; declaring repository source, baseline and Git/base inputs fixes it. The same injected literal then fails the intended test (610 pass/1 fail), and removing it restores611 passes. The96 occurrence baseline only shrinks against the actual PR/push base. This accepts the native literal guard and cache invalidation only; no new UI/rendering or production-build claim is made. Full colour migration, broader screen, upgrade and release gates remain open. See docs/android-raw-colour-independent-review.json and docs/android-raw-colour-integration-verification.json.

## Recovery appearance adoption — independently accepted, 2026-10-14

Final bounded candidate source ba6fe5799 (production f49821c6e) is independently accepted and integrated through f12022058. Review SHA 3bc15251ea35699e787ddf22c05b52ee17a670a12ef27eb528b1cb520cf1e51a verifies the evidence and independently replays all rendering gates; 4,253 app and 376 library source hashes match and every non-documentation file equals the qualified lane. Manifest SHA441e8e38825fe95fbc5fe68c6a6830e10f303c23a39c96625cb91f9c2b4cfb30 pins14 changed source files,4,253 app/376 library snapshot files and3,572 evidence files. Production checks pass1,298 unit tests/0skips, lint0errors/98 inherited warnings, configured Detekt and Debug assemblies. Native candidate17 methods produces480 recovery and45 prior captures;333 exact comparisons,48 rendered-text groups and340 pixel-role checks pass. Separate3-method focus/press preflight passes40 captures; adversarial native text-fit controls accept the complete short line and reject ellipsis/height clipping. Both compiled rollback configurations pass4 unit methods and17 native methods each, with125 byte/pixel-identical reference images each. All320 legacy effects comparisons are exact. Original PIN opens the installed public wallet, both public wallets appear in the picker,2wallet/4chain/7protected records remain exact. Four representative double-text captures are reviewed; Russian long title wraps mid-word. See docs/android-recovery-appearance-validation.json for full failure provenance, scope and limits. Full R1–R10 recovery, other screens, global A3 and full upgrade/device/release gates remain open. Subsequent paragraphs preserve the earlier qualification history; the accepted final disposition above supersedes their pending-state wording.

A native adversarial control now exercises the corrected line gate against the exact complete-short-line counterexample, an ellipsized narrow label and a height-clipped label. It must distinguish all three before the full candidate can be accepted; no production source changes.

Native diagnostics establish the first overflow flag as paragraph-box versus measured-node width:616px paragraph,480px node and complete479.957px line, with56px height and no ellipsis/truncation. The harness now checks every actual line against the measured integer bounds (half-pixel rounding tolerance), plus complete final character, no ellipsis, no exceeded max-lines and no height overflow. Raw Compose overflow flags remain recorded. This replaces an invalid width-box inference, not a production text change; the full matrix still must pass.

The frozen cross-version Banner replay exposed shifted resource integers after adding the localized Back string. Action lookup now uses the installed app's stable resource names; original mismatch is retained with aapt2 table evidence. The final candidate matrix independently stopped at an English1x title overflow assertion despite a visually complete title; exact paragraph/line diagnostics are now captured without relaxing that assertion. Final focus/press native3-method run passes40 captures; global and surface rollback builds both pass4 compiled-control tests with zero source drift. Complete matrix and visual acceptance remain pending.

Caller review aligns the Banner locale fixture with WalletScreen's16dp horizontal gutters: the320dp host now gives the banner288dp. The frozen old app will replay80 banner captures before the final comparison. Prior fullwidth banner captures are preserved as standalone evidence; corrected Center160 and original Pin160 remain applicable. This test-only change does not alter production sourcef49821c6e, whose buildv5 passed with zero drift.

The focus-state source correction34d79459c passes Debug assemblies, focused module suites, lint and configured Detekt with zero source drift. Runtime replay remains pending. Actual prior secondary-button pixels expose a separate Material-indication overlay: eight standard pressed captures have only2.687–2.691 inner-boundary contrast. The painted secondary now uses native clickable Button semantics with its explicit press/focus paint and no duplicate Material overlay; the unchanged action/geometry checks gain exact focused/pressed fill assertions. The v1 Back label now has translations in all eight shipped locales; rollback retains the old label. Final candidate build/native/rollback checks are pending.

The keyboard-mode negative replay now reproduces missing rings on both frozen pre-correction controls. Candidate preflight passes Back and secondary focus/press across all ten size/effects combinations, but the recreated Lite banner reports native focus while its outside ring is absent. Failed pixels/logs are retained. The correction reads actual focus-node state through onFocusChanged rather than relying on a collected interaction stream; the unchanged native regression must pass before acceptance. Full rendering and compiled rollback qualification remain pending.

The first focus regression run is invalid as a missing-ring reproduction: both nodes stayed in touch-input mode and failed before pixel sampling. The harness now requests actual keyboard input mode. Visual review also rejected the original Center color reference: its standalone Box omitted production BaseComposeFragment's Scaffold content-color provider. Center now uses Material Scaffold and will replay on the frozen pre-restyle APK. The160 affected captures remain; this is a fixture correction, not a claim that the installed app rendered black text. The17 prior native passes/45 unchanged old UX captures remain valid within their stated scope. Optional explicit screen/effects subsets support focused replay, with unknown/empty filters rejected.

Independent source review identified missing explicit keyboard focus on the banner and Back control. Both now share the outside ring/gap treatment, with two actual-control pixel probes prepared against the frozen pre-correction app. The first candidate app assembled, but its test compilation stopped on an invalid test-only onAllNodes import; corrected without changing the intended assertions. Failed build and pre-correction APK are retained. Final checks remain pending.

The scoped recovery banner, recovery center and PIN recovery now consume the approved semantic palette, locale-aware type and opaque Calm presentation when the compiled Recovery flag and v1 palette are both enabled. Secondary actions retain their original callbacks/targets, have full-width wrapping labels, 48dp minimum height, a declared control rim and an outside keyboard-focus ring. The banner uses a warning glyph/leading bar and an opaque status composite. Legacy branches remain the default; no wallet, PIN, key, import or persistence code changes.

The frozen pre-change APK successfully rebuilt after regenerating a missing dependency Dex input, with zero source drift; its native baseline is still running. The new candidate adds rendered-text overflow and actual focus/press checks to the public-fixture locale/effects matrix. Candidate build, native qualification and both compiled rollback comparisons are pending. This is a presentation slice, not completion of the full guided R1–R10 flow, global A3 palette mapping, archives, physical assistive-technology or release gates.

## Recovery presentation baseline — in qualification, 2026-10-14

A public-fixture native harness captures the existing scoped-recovery banner, recovery center and PIN recovery before appearance changes. It exercises8 locales,1x/2x text and Full/Lite/Reduced/high-contrast/reduced-motion inputs at320×640dp. Each callback records its original public target/import mode, including separate biometric/view-only actions, retained-data routes and backup. No wallet/key/persistence operation is invoked. The baseline is building; no new screen appearance is accepted. Generated palette mapping, full guided recovery details, archives and broader screen/device gates remain open.

## Build-time appearance rollback controls — independently accepted, 2026-10-14

`FW_PALETTE=legacy|v1` and thirteen `FW_RESTYLE_<SURFACE>` booleans are compiled into common BuildConfig from Gradle properties. Invalid values and unknown surface flags fail configuration. `FwRestyle.isEnabled` requires both v1 and the requested surface; legacy overrides all individual flags. Defaults remain legacy/false while screen migration proceeds. No production screen consumes these controls yet; this adds the rollback boundary, not the A3 palette swap or completed screen rollback. Wallet state, preferences and remote configuration do not participate.

Qualification inspects17 valid generated configurations, including all13 individual rollbacks, and rejects9 malformed/unknown configurations with the intended diagnostics. Four focused compiled configurations pass4 tests each; final default passes603 common tests without failures/skips and configured Detekt (which excludes common/app). All4,250 app/376 library source pins remain stable. See `docs/android-appearance-validation.json`; independent final acceptance is recorded in `docs/android-appearance-independent-review.json` (SHA `847ee24c885dbd910d7f2bc0efd73647fc31e132811cb59f5c93f48da4c01843`). Full non-documentation source equality with tested `e92aaa18b` is verified. Individual non-Recovery configurations are generated-field checks, not separate JVM/device runs. Full appearance adoption, screenshot rollback proof and release gates remain open.

## Shared paint helpers — independently accepted, 2026-10-14

Static backdrop, glass, Phase0 no-blur chrome, gated glow and raised/inset controls consume the approved palette and inherited effects. Drawing preserves child actions and bounds; decorative layers stay beneath content. Calm, Reduced and high contrast use opaque surfaces without glow/shadows. Focus/error rims remain, with error taking precedence. Repeated controls can disable raised shadows. No production screen adopts these helpers yet.

Final v3 passes143 app and599 common tests, Debug assemblies, lint0errors/98 inherited warnings and configured Detekt; all4,245 lane/376 library source pins remain stable. The configured Detekt task excludes common/app, so this is not a scoped Detekt-clean claim. Final API34 pair passes11 baseline/15 candidate methods with91 paint captures. All45 existing UX/theme PNGs remain pixel-identical. Seven child actions keep exact bounds at1x/2x text across Full/Lite/Reduced/Calm/HC. Native tests verify child pixels, surface composites, focus/error precedence and square-circle/outline glow gating.

Rendered analysis rejected the first generic/chamfer border: standard1.61/HC2.71 contrast versus required3. Direct clipped inside strokes fix the alpha loss. The new native regression fails on the frozen old app and passes on the final app for generic raised/inset/glass standard/HC. Final35 sampled surface strips contain6,300 background pixels;340 foreground-role checks and25 control rims pass (minimum text4.629, boundary3.516). This measures actual fixture backgrounds against declared native colours, not glyph raster or every screen. The first compile import error,99-warning intermediate build and failed pixel/reproduction results remain preserved.

Original PIN and both wallets remain available through fresh UI XML;2 wallet/4 chain rows and7 protected records are exact. See `docs/android-paint-foundation-validation.json`; independent final acceptance is recorded in `docs/android-paint-independent-review.json` (review SHA `dd6c34816608a553230d1909ac7f90dc08e033b6070c1c6b571b144ddcc82950`). Full non-documentation source equality with tested `7c29a12cb` is verified. Remaining A1/A3/A5 work includes build-time appearance rollback, screen adoption, older API26–27 shadow qualification, all-locale/physical accessibility and final release gates. No wallet, cryptographic or transaction code changes.



## Full shared contrast contract — independently accepted, 2026-10-14

The byte-pinned shared contract now covers 33 pair groups (172 permitted foreground/background combinations), composite backdrop recipes, standard/high contrast, and price-colour CVD floors. The native JUnit port evaluates actual generated Compose semantic colours and actual status-container alpha, with rounded sRGB composition and every rounded primary-fill interpolation colour. Pure white under chrome, strict unrounded text/control ratios, required inventory/hash and a deliberately failing control-opacity regression are checked. Final qualification passes all599 common tests (five new contract methods), configured Detekt and a stable4,243 lane/376 library source snapshot. The first native reference assertion incorrectly expected unquantized alpha; its failed log/XML are retained, and the correction checks actual8-bit values. Independent review of the shared engine found an exact gradient midpoint omission for opposing channels; both engines now include the midpoint and a regression. See `docs/android-contrast-contract-validation.json`; independent final acceptance is recorded in `docs/android-contrast-contract-independent-review.json`:2 source/Git,342 evidence and4,243 app/376 library snapshot pins match. Integration retains full non-documentation equality. This test-only checkpoint does not change production paint or replace pixel/accessibility checks; iOS/web ports, per-screen use and broader release gates remain open.


## Semantic typography and offline font licences — independently accepted, 2026-10-14

The approved type scale now has explicit body/number/title/mono/HUD/recovery-word roles. Sora titles are limited to supported Latin locales; Russian/Vietnamese use Inter and Japanese/Chinese use the system body family. Amount roles use body tabular figures. Two exact JetBrains Mono 2.304 fonts and original Sora/Inter/JetBrains OFL notices are bundled with source pins. About has a localized offline licence entry and a scrollable dialog with a fixed Close action. Existing Material typography is unchanged.

Six new JVM tests cover role/locale/weight choices, exact bundled bytes and notice reading. The final v5 build passes 1,289 unit tests with zero failures/skips, Debug app/test assembly, configured Detekt and lint with 0 errors/98 warnings; all 4,241 lane and 376 library source pins remain stable. Final paired native runs pass 11 baseline/14 candidate methods, and all 45 pre-existing UX/theme captures are pixel-identical. The three typography methods exercise eight locales at normal/double text with 64 new captures, actual localized title/Close and rendered font-scale/overflow assertions. Visual inspection verifies the corrected Japanese, Russian and Turkish double-size dialog captures. Original-PIN unlock and both wallets are observed in fresh UI XML; all 2 wallet/4 chain rows and 7 protected records remain exact. The initial native v1 pass was rejected because its separate dialog window reset caller locale/density; corrected providers preserve those values. The subsequent title-width failure is fixed with full-width heading constraints, retaining strict overflow assertions. Failed compile/runtime attempts and rejected captures remain evidence. See `docs/android-typography-foundation-validation.json`; independent final acceptance is recorded in `docs/android-typography-independent-review.json`. Integration source `6cbda37f7` matches all 22 changed source pins and every non-documentation lane file. Independent review rechecks 1,220 evidence pins and all 4,241 lane/376 library build-source pins.

This is partial A1/A4. Full legacy Sora restrictions, small-text retirement, per-screen adoption, populated About/assistive-service coverage, Bold Text, older APIs, physical devices and final release gates remain open. No wallet, cryptographic or transaction code changes.

## Live effects and theme providers — qualified candidate, 2026-10-14

Both reusable theme hosts now provide the approved Fw palette and effects state while retaining existing Material colors and typography. High contrast wins over Reduced/Lite/Automatic choices; battery saving and low-memory/older devices select Lite. System contrast, power, animation scale, ordinary appearance preferences and resume refresh update live. A nested Calm wrapper retains contrast/motion and disables decorative paint. Phase0 live blur remains disabled.

Final qualification passes143 app and588 common unit tests with no failures/skips, Debug app/test assembly, configured Detekt and lint0errors/98warnings. All4229 lane and376 library source pins remain stable. The final API34 suite passes13 native methods: both hosts exercise actual system contrast thresholds, power-saving transitions, preferences, animation scale and nested Calm. The original settings and raw preference values are restored. The empty debug test host handles uiMode so battery saver cannot recreate and discard injected content; activity-recreation behavior is not claimed by that test.

The final paired baseline/candidate runs pass11/13 methods and all45 in-process captures are pixel-identical at narrow width and normal/double text. Earlier comparison across changed system window geometry is retained; it is superseded by the final same-session pair. Original-PIN unlock and both-wallet availability are verified through fresh UIAutomator XML/actions, not black full-app PNGs. All2 wallet/4 chain rows and7 protected records remain exact. The hidden-setting permission failure and empty-test-host recreation failure are retained diagnostics. Independent final review `docs/android-effects-foundation-independent-review.json` verifies all7 source/Git and808 evidence pins,731 unit passes,11/13 native methods,45 exact paired captures and2 wallet/4 chain/7 protected records. The integration source exactly matches the qualified candidate.

This is partial A1 plus A2 theme plumbing. Appearance UI (including acknowledged failed-save behavior and off-main-thread writes), modifiers, typography, palette flags, actual paint consumption, session-jank Auto-Lite and full OS/locale/physical-device/release gates remain open. No wallet, cryptographic or transaction code changes.

## Generated token foundation independently accepted — 2026-10-14

Source `cb222fc51` is integrated at `61fa4aaba` with exact tracked non-documentation equality. Independent review verifies12 source/Git,247 evidence,3 shared-generator/Git and4600 build-snapshot pins. All584 common tests,8 shared/3 legacy generator checks,11 native methods per artifact and45 pixel-identical paired fixture captures pass; lint has0 errors/98 warnings. Original-PIN and both-wallet availability use fresh UIAutomator XML/action evidence: full-app adb PNGs are black and are not visual proof. In-process fixture captures are nonempty. All2 wallet/4 chain/7 protected records remain exact. `docs/android-token-foundation-independent-review.json` records the bounded acceptance. Effects/providers/type, rollback flags, full contrast/CVD/guardrails, visible palette and screen migration, historical/physical and signed-release gates remain open.

## Generated token and legacy palette foundation — qualified candidate, 2026-10-14

The approved shared token source now generates Compose and XML colors. Existing Compose constants, night aliases and XML names resolve through a generated frozen legacy palette without a value swap. Independent source review verifies58 Compose values/aliases, one ordered four-color gradient,23 night aliases and42 XML colors; existing helper bodies are unchanged. Eight shared-generator and three legacy-generator checks pass. The first actual Android build exposed an invalid generated XML comment; the shared generator correction and a real XML-parser regression are retained with the failed baseline.

Final qualification passes584 common tests with zero skips/failures, Debug app/test assembly, configured Detekt and lint0errors/98warnings. All4224 lane and376 library source pins remain stable. Baseline and candidate each pass11 native UI methods; all45 paired captures (37 UX plus8 theme) are pixel-identical within the same emulator session. The installed candidate accepts the original PIN and displays both wallets;2 wallet rows,4 chain accounts and7 protected records remain exact. See `docs/android-token-foundation-validation.json`. Independent final acceptance is pending. This is partial A1 only: effects/modifiers/type, Fw providers, rollback flags, full contrast/CVD/raw-color guardrails, palette swap and screen migration remain open.

## Dark-only theme ownership — candidate, 2026-10-14

The two reusable Compose themes select a light palette when given a light configuration, contrary to their dark-only contract. Main/startup/emergency activities already force dark mode; this is a reproduced theme-boundary leak, not an observed light Portfolio. The baseline captures the real amount component under both hosts at normal/double text and fails exactly the four light-configuration cases. The fix makes the common theme entry select the existing dark palette and removes obsolete preview overrides. No palette values or storage behavior change. Final qualification passes 143 app and 581 common tests, Debug assemblies, configured Detekt and lint with zero errors/98 warnings. Eleven native UI methods pass with 37 fresh UX captures. Same-session baseline/candidate theme rendering preserves all four dark renders byte-for-byte; all four light configurations now match. The installed app unlocks with its original PIN and shows both wallets; two wallet/four account/seven protected records remain exact. `docs/android-theme-boundary-validation.json` pins the source and evidence. Independent acceptance verifies all five source/Git and243 evidence pins, exact paired PNGs and protected records; integration `d9dbbf9e4` matches the qualified source. Broader generated-token/effects and screen migration remain open.

## Approved wallet avatar — qualified candidate, 2026-10-14

The shared library replaces only the legacy bird cutout with the checksum-pinned solid winged-sun badge. Address-derived palettes, circle geometry and Ethereum avatars remain source-identical. All 12 native AndroidSVG cases retain exact colors and geometry; each renders the new shape distinctly. Debug app/test assembly, 581 common tests and three native asset/avatar methods pass. The installed app unlocks with the original PIN and displays both wallets; all two wallet rows, four chain rows and seven protected records remain exact. `docs/android-avatar-brand-validation.json` binds the source and evidence. The runtime dependency guard now pins shared-library commit `fbbc2802c8ef5a7e3ab5e8d565fc897c70a75331` and its exact tree. Independent acceptance verifies all seven source/Git pins and 166 evidence pins; integration is `5ca40bda2`, shared library `fbbc2802c8ef5a7e3ab5e8d565fc897c70a75331`. The integration source matches the qualified candidate. Full theme migration, physical-device and release gates remain open.

## Android brand assets independently accepted — 2026-10-14

Source `e8f4a2e50` is integrated at `f6da94035`, with candidate manifest `docs/android-brand-assets-validation.json`. All51 current/Git source and542 evidence pins match independent review `35eb0ed7aa1d88c89fb64d32a9cb1f23fc50ef305d5abbf9839db7d6ec747190`. Final APK passes1,441 unit tests/10 recorded skips,2 native asset/Coil tests, and the pixel-corrected320dp normal/double-text screen method. Actual PIN/Welcome/About and2 wallet/4 native account/7 protected-record preservation pass. Final lint has0 errors/98 warnings; two long-vector performance warnings remain explicit. Earlier failed renders and test diagnostics are retained. The documentation merge preserves both earlier startup acceptance and branding history. Avatar/general-art, broader design and production release gates remain open.


## Startup U0 independently accepted — 2026-10-14

Lane `a440e8312` is integrated with exact non-documentation equality. Independent review verifies15 changed sources,3313 compiled-source files,404 evidence pins and240 accepted captures with zero mismatch. All724 unit tests and4 device method executions pass. Actual offline unsupported-policy update retains original-PIN access and exact2 wallet/4 account/7 protected records. Warning control tests separately prove Continue/Retry/Update at both text sizes in8 locales. `docs/startup-u0-independent-review.json` records scoped acceptance; full installed-cohort, physical and release gates remain open.

## Approved sun-disc artwork — candidate, 2026-10-14

Visual thesis: the approved winged sun disc anchors app entry on calm dark surfaces. Content plan: line-art launcher badge, colour Welcome/About lockup, compact solid marks, and neutral missing-artwork placeholders. Interaction plan: existing navigation remains; artwork is static and the Android12 system splash has no forced wait or decorative animation. The approved SVG bytes are checksum-pinned under `brand/sun-disc`; `scripts/generate-brand-assets.py` derives native vectors, adaptive foreground/background and density rasters. Both Debug and Release overlays use those assets. Android12 launch attributes use the system SplashScreen styling. Bitcoin/Solana use offline two-letter monograms across canonical and retained catalog aliases. Qualification is pending; generated assets alone are not visual or release acceptance. Legacy wallet-avatar clipping lives in the shared library and remains a separate follow-up.


## Startup U0 — remote policy and optional refresh follow-up, 2026-10-14

Three held-response regressions reproduce local services waiting for remote startup data, optional refreshes delaying completion and caller cancellation losing ownership. Unsupported-version behavior is also covered; its initial relaxed remote-config fixture was corrected to exercise actual cached and fetched unsupported policies. Local services now start before those awaits, optional refresh jobs are independent and guarded against duplicate active work, and policy/version warnings allow Continue. Opening the update store no longer closes the wallet. Policy-save failures remain visible; cached feature vetoes and key authentication are unchanged. The baseline records four failures, including that corrected policy fixture; the first corrected run has15 passes and that one fixture failure. Final qualification passes143 app and581 common tests (724 total, no failures/skips), Debug app/test assembly, lint0errors/98warnings and configured Detekt. All3313 compiled-source hashes remained unchanged. Frozen APK SHA256 `6f230509e0e3bf7c4c95be70eae7cfde0e3961350c302726238f7e24e8d4788c` passes both warning-control methods at actual font1.0/2.0 on320dp, across all8 locales;240 exact fresh captures are pinned. An in-place update of the preserved owned wallet under offline unsupported policy reaches the original PIN and both wallets. All2 wallet rows,4 native account rows and7 protected entries are unchanged. The policy and connectivity test overrides were restored. Actual warning Continue/Update taps are qualified in the component tests; the full startup capture proves wallet access, not an observed Continue tap. See `docs/startup-u0-validation.json`. Physical-device, final upgrade and release gates remain open.

## Native network and cache-startup integration — 2026-10-14

Imported-wallet catalog/enrollment, host theme, durable node selection, per-request Solana genesis and original-payment network binding are integrated from lane `237b5c6c8`; every tracked non-documentation file is identical. The combined behavior source passes 1,939 unit tests with14 recorded skips, lint0errors/98warnings, two private-chain scenarios and13 UI tests at each normal/double system text size. The final localized-notice delta passes581 common tests and7 recovery/portfolio tests at each size. After its in-place install,2 wallet records,4 chain accounts and7 protected records remain exact. `docs/native-startup-network-integration-review.json` records pins and capture limits. Remaining U0 work covers app config, unsupported versions and awaited ancillary refreshes; branding, historical upgrades, physical devices and source-bound release gates remain open.

## Retained-payment notice — final source follow-up, 2026-10-14

The combined candidate passes1,939 unit tests with14 recorded skips, lint0errors/98warnings,2 private-chain scenarios and13 real UI tests at each actual font1.0/2.0 on320dp. Raw review then found that the unavailable-network dialog reused copy asking the user to review a new payment, even though an unbound saved payment cannot safely be replaced. A specific notice in all8 shipped locales now says the saved payment is retained and status/retry are unavailable. Only this notice and its UI assertion/capture change. Final debug APK SHA256 `43e6dc742282ebf1559af313924cd06db5dbe8e1624f0fcc57435d7af8895407` passes 581 common tests, debug/test assembly and configured Detekt, plus seven native recovery/portfolio tests at each actual system font1.0/2.0 on320dp (14 executions). Raw normal/double notice and scrolled Close captures were reviewed. All2 wallet records,4 chain accounts and7 protected records remain exact after the in-place install and tests. `docs/retained-network-notice-validation.json` pins this scoped follow-up; full startup U0 and release gates remain open.

## Startup cache, grouping controls and native navigation — candidate qualification, 2026-10-14

Remote registry/types failures now start local wallet services and offer Retry or Continue. Retries use the
failed step and do not duplicate local services or connection observers. App-config cancellation is
propagated; an offline request can use a supported cached policy, but a failed policy write cannot silently
fall back to an older policy. Unsupported-version enforcement and awaited ancillary refreshes remain
explicit startup follow-ups; this is not a claim that every startup failure is nonblocking.

Actual imported-wallet testing found two more defects: the portfolio grouping selector collapsed to zero
height inside the list, and Solana Send exposed an unsupported cross-chain route with no destination.
The selector now measures normally and changes both lenses through actual taps. BTC/SOL Send hides the
cross-chain action and rejects stale/unsupported callbacks; eligible legacy routes remain covered.

Settled320dp/double-text captures reproduced inaccessible startup alert actions. The shared alert now
opens expanded and scrolls long content. Two instrumentation methods pass at each actual system font
scale1.0/2.0, exercising Continue and Retry through injected taps/swipes for8 locales and both cache failure
steps;130 final captures are pinned. The same installed APK opens the preserved wallet with its original
PIN, shows both grouping choices, switches lenses and opens native Solana Send without the XCM entry.

Qualification:136 app tests pass after the config-persistence/header fixes;579 common tests pass after
the alert change. The preceding broader candidate passes1,923 tests with14 recorded skips across app,
wallet, account, runtime, core-db and common, with lint0errors/98warnings and configured Detekt. Actual
generated Room DAO tests pass4/4. Portfolio4 and native Send/Recovery6 component tests pass at actual
font2.0 on320dp. These source-scoped checkpoints are not a final combined-source acceptance. Exact logs,
failed baselines, APK/source pins and captures are in the parent `build/safe-upgrade/evidence/codex-20261012`.

The latest alert APK SHA256 is `2b403724f87ed2d52862eaa15d5a302643110fb711b3864271c82783557b97b1`;
all3182 tracked/untracked compiled-source hashes checked at build start remained unchanged at completion.
Solana durable network identity is being corrected in a separate slice after two failing alias-replacement
recovery regressions. Final historical upgrades, independent integration and release gates remain open.
No public-network signing or broadcast was performed.

The same installed artifact also passed actual Solana Add Node, selection and rename, with the active URL disabled. The custom choice and name survived a process restart and original-PIN unlock. Comparison with the prior two-wallet snapshot preserves all2 meta-account rows,4 native chain-account rows and7 protected preference/key entries byte-for-byte. The failed devnet Add Node attempt displayed a generic connection error; that UI attempt alone does not prove genesis rejection.


## Solana cluster binding — candidate checkpoint, 2026-10-14

Five real RPC regressions failed before correction. The adapter now pins the selected registry genesis hash, rechecks the exact endpoint before every read/broadcast, preserves that pin across provider changes, and rejects wrong clusters before wallet RPC. Balance, reviewed transfer, retained-payment recovery, and Add/Edit Node select the same network identity. Private-validator tests inject and record that owned run's actual genesis; no check is bypassed.

The combined candidate passes579 common tests and24 node tests, with397 wallet passes and one preserved test-cleanup timeout. Corrected bounded Main-dispatcher cleanup passes the18-test wallet-list suite. Both private Bitcoin/Solana scenarios pass, including native/token fee/rent/Max and exact-byte ambiguous retry. Debug/test assembly and configured detekt pass; Android lint reports0 errors/98 warnings after the debug-only network resource correction. Exact logs, failed baselines, APK hashes and source patches are under `android-solana-cluster-*` in the parent `build/safe-upgrade/evidence/codex-20261012` directory. Final combined module/Room/UI checks and independent acceptance remain pending.

New actual-navigation evidence shows unsupported Solana Send can enter a paused XCM page with an empty destination picker. Three startup regressions also reproduce blocking registry/type refresh and swallowed cancellation. These follow-ups, final historical upgrades, physical-device and signed release gates remain open; this checkpoint is not production approval.

## Imported-wallet send theme — correction in qualification, 2026-10-14

The real imported-wallet Solana Send route crashed because `BaseComposeBottomSheetDialogFragment` uses legacy `FearlessTheme`, while the amount field consumes ui-core typography. The legacy theme now also installs `FearlessAppTheme` providers and retains its existing Material colors/typography. The native Send component regressions use the actual legacy host theme; their previous modern-only wrapper missed this failure. The captured process crash remains preserved. Rebuilt APK navigation and regression execution are pending.

The first aggregate check produced 2,585 passes and 14 inherited skips in 17 reported modules, with some unchanged tasks up-to-date. It failed on a stale staking `Result<BigInteger>` mock and three debug network-security lint errors. The fixture now returns the real API shape and cancels its view-model scope. Debug loopback trust uses a distinct named resource selected by the debug manifest, with explicit subdomain policy; main/release trust is unchanged. Rechecks are pending. Native node/alias correctness is being corrected in a separate slice.


## Native network discovery — remediation in qualification, 2026-10-14

An actual launcher-to-public-mnemonic import reached Portfolio but exposed no native Bitcoin or Solana definitions. Runtime discovery now merges app-owned mainnet definitions, keeps recognized historical ids and exact chain-account identities, preserves the existing cache during a registry outage, and confines XCM discovery to a successful fetched snapshot. Native HTTP services bypass Substrate/EVM runtime setup; Bitcoin subscriptions do not require a websocket node. A wallet/catalog observer expands newly imported wallets without a process restart. No secret or existing public key is rewritten.

Thirty-nine focused JVM tests pass with no failures/skips. Preserved baselines reproduce the missing catalog/subscription, wrong alias address and unrelated Substrate identity in native asset placeholders. Native placeholders now require their exact chain account, and both full/light address models prefer the requested stored id. Full checks, rebuilt imported-wallet navigation, independent review and final upgrade/release gates remain pending. No public-network transaction was sent.


## WP-9 independent integration review — 2026-10-12

Bitcoin/Solana send, exact-payment recovery and native review changes are locally integrated at `2c1196c84`, from qualified lane `f70d15cdd`. All 93 final source hashes and 230 frozen evidence hashes match. Every tracked file outside documentation is identical to that lane; documentation conflicts retained the earlier recovery and portfolio integration records.

Independent review confirms 964 passing JVM tests and 12 native executions at system font scales 1 and 2, with nine representative captures inspected. Exact decimal amounts, fees, full addresses, separate Solana rent and recovery controls remain readable. Earlier delayed-Max, original-payment recovery and Bitcoin no-change findings are resolved. Private-chain production-service checks remain separately pinned at `2c54f65a7`. See `docs/wp9-integration-review.json` for the review and its limits.

Imported-wallet navigation, physical-device and all-locale acceptance, historical signed upgrades and final source-bound release qualification remain open. No public-chain transfer or publication is claimed. Older checkpoint sections below retain their historical status.

## WP-9 native payment review — local lane qualified, 2026-10-12

BTC/SOL confirmation now preserves every reviewed decimal and full address, with Network first, separate Solana account-creation rent and Bitcoin sat/vB. Native Send stays disabled until the network, sender, recipient and amount are present; the exact amount is published with the ready fee. Warning actions remain reachable. Setup gives large-text network, amount and fee fields the width they need; the fee caption includes account creation where applicable. The new formatter covers a single lamport, nine-decimal fees and full-u64 SPL values without abbreviation or rounding.

Final source passes 964 JVM tests (571 common and 393 wallet), debug app/test APK assembly and configured detekt (legacy-module exclusions unchanged). Six API 34 Compose cases pass at each actual system font scale, 1.0 and 2.0, on a 320 dp screen: 12 executions with zero failures/skips. They verify exact amount/recipient/network/fee/rent text geometry, warning actions, amount editing, Max/Continue, incomplete-review gating and retained-payment recovery. Thirty captures and exact source/APK/XML/log hashes are frozen in `docs/wp9-native-ui-validation.json`. Prior clipped-display attempts, compile failures and the unchanged balance-list teardown timeout are retained; the final source passes without suppressing tests.

These are production screen components driven by public fixture states and callbacks. The separate private-chain checkpoint qualifies real signing, storage and transport at `2c54f65a7`; those paths are unchanged in this UI checkpoint. Independent final integration review, integrated historical/device acceptance, broader locale/device checks and governed release gates remain open. No funded public-network canary, imported-wallet end-to-end navigation, signed release or public transaction is claimed. Older WP-9 entries below describe their individual checkpoints; this entry supersedes their earlier open-gate lists.

## WP-9 private-chain sends — native checkpoint qualified, 2026-10-12

Production Android signing, RPC/indexer transport and durable pending storage now pass two real-chain instrumentation flows on disposable Bitcoin regtest and Solana validators (API 34, 4.636 seconds). Bitcoin proves the reviewed amount/fee, RBF, owned change, exact-byte restart retry without a duplicate payment, mined confirmation and a one-output Max spending its change. Solana proves native amount/fee, exact retry after a lost response, SPL with new and existing ATA rent, and native Max.

The first run found a real Bitcoin bug: the ownership check required a change address even for a no-change plan. The fix checks ownership only for an actual change output. The affected wallet suite passes 392 JVM cases with no failures/skips, and debug app/test APKs assemble. `docs/wp9-private-chain-validation.json` binds source, frozen test XML, APKs, logs and helper hashes. Test-only loopback cleartext is confined to the debug resource. The runner removed its validators, temporary data and reverse forwards; no public-network transaction was sent.

Full native Setup/Confirm exact display, compact/double-font captures, independent integration and broader release gates remain open. In particular, the shared abbreviated/eight-decimal formatter is being replaced for native reviewed values before that UI gate is closed.

## WP-9 exact-payment recovery — checkpoint qualified, 2026-10-12

A retained BTC/SOL payment now has a dedicated status dialog with the original network, amount, recipient, network fee and account-creation rent. After a restart it checks status or explicitly retries the same signed bytes; a new Max quote cannot authorize a previous payment. Unknown or expired Solana outcomes remain retained and cannot silently replace their blockhash. Asset details expose the recent payment and native explorer/history fallback. Submission shows neutral payment-status copy instead of claiming confirmation.

Max requests now reject intervening amount/recipient edits, newer Max requests, wallet changes and exact-asset A→B→A changes, including the delayed input branch. Fee requests survive a zero starting amount and later provider errors. Drafts and explorer/history callbacks retain wallet revisions across suspended work. Remote-pause and view-only PIN errors keep actionable localized copy; all 25 review/recovery/error labels are present in the eight shipped locales.

Final source passes 962 JVM tests (571 common and 391 wallet), with no failures or skips, plus debug app/test APK assembly and configured detekt (legacy-module exclusions unchanged). Four API 34 recovery-dialog tests pass at actual system font scales 1.0 and 2.0 on a 320 dp screen; six captures show the original review, expired outcome and reachable actions. `docs/wp9-recovery-validation.json` binds source, immutable XML, APKs, logs and captures. Earlier failed builds and the superseded LocalDensity-only font experiment are preserved separately.

Remaining WP-9 gates: real Android production send execution against private Bitcoin/Solana validators; full Setup/Confirm native captures and exact-amount acceptance; independent final integration review and broader release/device gates. No public-network transaction or release acceptance is claimed.

## WP-9 reviewed sends — source checkpoint qualified, 2026-10-12

The production BTC/SOL paths now retain immutable reviewed transactions and durable signed bytes before submission. Bitcoin spends discovered receive/change UTXOs with owned internal change and an exact sweep; Solana uses RPC-only native/multi-account SPL messages with exact fee, ATA rent, priority, simulation and expiry checks. Confirmation uses its displayed quote/fee, exposes BTC sat/vB and separate account creation, and connects native Max. Selection revisions reject A→B→A work; canceled requests cannot publish late quotes. View-only PIN recovery cannot submit or retry a retained payment.

Qualification: 570 common + 372 wallet + 92 core-db JVM passes (1,034 total), four existing core-db network skips, zero failures, plus debug source compilation and configured detekt (legacy-module exclusions unchanged). Frozen XML and 57 source/test/fixture/helper/resource hashes are recorded in `docs/wp9-reviewed-sends-validation.json`. The delayed quote tests cover selection ABA and canceled amount requests, and a DAO default-method test exercises the actual selection revision bracket. A full-run test lifecycle leak was fixed by canceling and joining test view models before resetting Main; previous failed logs are preserved.

WP-9 remains incomplete: dedicated persistent pending controls, history/explorer fallback, full localized errors/recipient guidance, all eight review locales, native private-chain execution, APK/UI qualification and independent integration review remain open. No public-network transaction or final release acceptance is claimed.

## WP-9 RPC balance and account checks — checkpoint qualified, 2026-10-12

Production Solana balances now use the selected cluster RPC instead of the indexer, with exact SPL/Token-2022 inventory identity, full u64 balances, frozen-token spendability and preservation on incomplete data. New RPC account/status/height/priority methods and pure wallet/mint/token/rent preflight guards pass 912 JVM tests (549 common, 363 wallet), zero failures or skips. Bitcoin explicit/custom endpoints stay isolated from public fallback, and configured relay floors cannot undercut the selector minimum. `docs/wp9-rpc-validation.json` pins the 29 changed Kotlin files and immutable XML copies. These are building blocks: immutable send quotes, durable ambiguous-broadcast handling, send UI, all-address BTC spending and private-chain qualification remain in progress. No APK/native gate or public-network transaction is claimed here.

## WP-9 Bitcoin/Solana correctness — foundation qualified, 2026-10-12

Implemented signed Bitcoin mempool accounting, relay fee floor, strict supported-address validation, RBF sequence, bounded dust fallback and sweep primitive, plus sticky provider failover. Corrected an additional Solana ATA derivation defect: subgroup signature validation had been used instead of Solana's compressed-point decompression contract. The corrected result matches 523 independent Dalek cases and 64 web3.js owner/mint/program vectors. The final foundation passes 895 JVM tests (533 common, 362 wallet), no failures or skips, plus the configured detekt task, whose existing exclusions include these legacy modules. `docs/wp9-foundation-validation.json` binds 39 source/test/fixture/helper files and evidence hashes. See `docs/wp9-send-correctness.md` for source contracts and fixture reproduction.

WP-9 remains in progress: RPC balance/account validation, all-address spendability/change, immutable quotes and durable ambiguous-broadcast protection, user-facing fee/rent/pending handling, and private-chain native execution are still required. No public-network transaction has been sent, and release gates remain open.

## WP-5/6/7 portfolio and exact-network actions — reviewed and integrated, 2026-10-12

By token is now the per-wallet default, with approved exact-member grouping, a By network alternative, persisted expansion, visible-total accounting and zero/hidden summaries. Singleton surviving groups become single-network rows. Manage follows the wallet's lens, keeps Hidden reachable, labels Auto/Shown/Hidden and applies group changes to every member. Its balances, preferences and writes stay bound to the same wallet across account changes.

Send/Receive choose exact approved members, include available unfunded networks, reject ticker/native fallbacks and recheck the wallet after suspended lookups. Changed chain/asset identity clears the amount before loading; confirmation shows the full canonical Network first. The paused Send-to-Receive callback also retains the original wallet. Receive safety copy, sticky vetoes and obsolete-payment cleanup remain intact.

Final source passes 487 JVM tests (361 wallet, 126 app), no failures or skips, debug and instrumentation assembly, and the configured detekt task (which excludes these legacy modules). Three API 34 Compose cases pass at each system font scale, six executions total; 12 final captures were inspected at actual 320 dp width. Pixel review repaired Network ellipsis, Manage scrolling and checkbox contrast. Twenty strings are present in all eight shipped locales. `docs/wp567-portfolio-validation.json` binds all 48 changed source/test/resource files, APKs, logs and captures; `docs/wp567-portfolio.md` explains behavior and contract precedence. Independent review and integration are complete at `b2c5af689`: all 48 source pins and 97 artifact/evidence digests match the lane; source outside documentation is identical to `9718a1d5e`. Four large-text portfolio, Manage, Receive-picker and confirmation captures were independently reviewed. WP-9 protocol and private-chain qualification, broader device/locale acceptance and release gates remain open. The optional asset-detail “Also on” card is deferred.

## K4–K6 combined recovery — locally qualified, 2026-10-12

Integrated PIN ownership recovery, authenticated retained-wallet restore/purge and explicit dual-slot master-key recovery at source `8751f03f1`. Reset persistence now uses effective key-slot health and only archives a previous PIN that actually exists. Recovery Center keeps “Try previous secure storage” available after activation. The public-API native regression proves an absent old PIN can be recovered, while exact old wrapped storage remains retained and a reopened process reads the new PIN.

Combined qualification passes 1,586 JVM tests plus four existing core-db network skips (1,590 cases), 51 native common tests, three native account tests (including 104 archive keys and 102 rebind vectors), and 12 native recovery/UI/SQLite/reversal tests. Debug and instrumentation APKs assemble; configured detekt passes with its existing exclusions. All eight captured compact/large-text surfaces were reviewed. `docs/k6-integrated-recovery-validation.json` binds source, APKs, logs and images. The first combined run's three stale health-mock failures remain recorded.

The independent PIN review and local K4–K6 integration gate are complete. Physical authentication and supported-runtime acceptance, signed historical upgrade/distribution evidence, remaining portfolio/send work and final release approval remain open. This local qualification does not authorize production publication.


## K6 PIN ownership recovery — lane qualified, integration pending, 2026-10-12

Unreadable PINs now route to a separate recovery screen. Previously enabled strong biometrics or a real matching wallet backup grants ten minutes to replace the exact original PIN snapshot; public identity is checked again at reset. The old typed PIN is archived without conversion. Device credential unlock discards any pending private action and opens a process-local view-only session, with signing/key export guarded until PIN recovery. The proof route remains on the PIN controller, and neither PIN confirmation nor backup input enters saved state. All eight shipped locales have the new recovery copy.

Lane qualification passes 1,463 JVM tests (501 common, 510 account, 331 wallet and 121 app), 41 API 34 encryption/Keystore tests, five recovery Compose tests, and one input-privacy test, all without skips or test failures. Debug app and instrumentation APKs assemble; detektAll passes with its existing module exclusions. Recovery actions are reachable at actual 320 dp with 2x text. Source/artifact/log hashes and preserved failed attempts are in `docs/k6-pin-recovery-validation.json`. Independent review, master-key/archive integration and combined verification, physical authentication lifecycle, all-locale visual acceptance and final signed distribution gates remain open.

## K6 explicit master-key recovery — locally qualified, 2026-10-12

- Added an explicit fresh storage slot only after two permanent previous-key failures across increasing Android device boot counts, a short-lived snapshot-bound consent, backup confirmation, and a configured device credential. Ordinary automatic key creation remains unchanged. The old wrapped value retains its exact SharedPreferences type, and all original aliases, ciphertexts, PIN values and database data remain in place.
- Fresh activation atomically commits the new wrapped key, its independent sentinel and active-slot marker. Every access authenticates the available slots independently; a returning original key becomes preferred while new-slot writes remain readable. Existing quarantine revalidation runs through the full startup gate again, with the session ready flag cleared while it runs.
- Added a non-exported recovery screen with retry, authenticated read-only cached public wallet data, emergency access and a deliberate backup-confirmation flow. All eight shipped locales have the new copy. The SQLite preview is bounded, does not migrate, and discloses incomplete records. Effective PIN health treats an absent old PIN after fresh activation as unreadable and bounds PIN ciphertext before decrypting.
- Final qualification passes: 1,197 JVM cases (1,193 pass and four existing core-db network skips), 48 common native tests and five app native tests on API 34, debug and instrumentation assembly, and the configured detekt task (which excludes the changed modules). Three 320dp/font-scale-2 captures passed visual inspection after fixing immediate consent-button gating and dialog contrast. `docs/k6-master-key-validation.json` binds 34 source hashes, APKs, logs and captures. Integration must reconcile effective PIN health in the proof-reset preflight, omit old-PIN archival when the captured PIN is absent, and keep a Recovery Center route for retrying the previous storage slot. Those combined checks and final release/device acceptance remain open.

## K6 PIN and master-key recovery — in progress, 2026-10-12

- The candidate separates PIN readability from master-key authentication. A valid sentinel or independently authenticated wallet payload can admit an unreadable PIN; unreadable active or archived PIN values cannot authenticate a key on their own. Provider outages remain retryable, and failed master-key authentication never publishes a healthy PIN status.
- PIN replacement now has a typed, exact-value retention operation: malformed string, integer, long, float, Boolean or string-set values are retained without conversion, alongside one durable new PIN write and read-back. Changed source values and full retention slots reject the replacement. Ordinary healthy PIN changes do not create unnecessary archives.
- Foundation validation passes: 496 common and 477 account JVM tests, plus 39 API 34 secure-storage/Android Keystore tests, all without skips or failures. `docs/k6-pin-foundation-validation.json` binds source files, logs and the instrumentation APK. This lane is not integrated. Guided strong-biometric/backup verification and device-credential view-only UI, mutation blocking in view-only mode, and explicitly consented fresh secure storage still require implementation and qualification.

## K5 retained archive recovery — 2026-10-12

- Recovery Center now opens a PIN-authenticated retained-record inventory. It previews proved Substrate, Ethereum and TON addresses from root, network, legacy ACCESS, V1 and V0.4 signing records. Each selected key becomes a separate wallet through the cross-store mutation coordinator; exact original archives, unselected sibling keys and unknown fields stay retained. Restored wallets are not marked backed up.
- A distinct `RESTORE_ARCHIVE` journal operation preserves historical stored-key (T1) proof through crash replay without weakening ordinary `CREATE` recovery-material (T2) checks. Restore rechecks the exact preview snapshot and proves the key again under the mutation mutex. Cancellation before staging and changed-record races make no new wallet write. Permanent purge needs two explicit confirmations and removes only the exact confirmed archive.
- Unknown/malformed readable records keep a complete original-string JSON export, including representation details. Undecryptable values and oversized-ciphertext fingerprint tokens are never mislabeled as plaintext exports. Malformed archive names remain visible. The screen locks and clears its in-memory data on stop, uses screenshot protection, and reauthenticates before reopening. Sixteen archive strings are present in all eight shipped locales.
- Validation: 494 common, 488 account, 331 wallet and 120 app JVM tests pass (1,433 total; no skips, failures or errors). The final contrast/theme change was followed by affected account/app checks and fresh debug/instrumentation APK assembly. API 34 passes 2 native tests in 1.088 seconds: all 104 frozen retained SR25519 payloads, including JSON, plus the existing 102-vector rebind regression. Together with 155 non-SR JVM fixture cases, all 259 frozen root/network payloads prove their original identity. Additional cases cover both legacy ACCESS layouts, V1/V0.4 conversion, all 24 historical TON spellings, stale previews, interrupted restore, two delete confirmations, background locking and exact export. Two native archive UI tests pass at an actual 320 dp width and 2x system font scale; the opaque dialog fix addresses the legacy transparent Material surface. Evidence, source/APK digests and final screenshot hashes are in `docs/k5-archive-validation.json`; raw files are under `build/reports/k5-archive/`.
- Independent review found and resolved oversized-token export and cancellation-before-staging issues. `detektAll` passes with its existing common/account exclusions; it is not full static coverage of these modules. Resource XML parity and `git diff --check` pass. First-run query-bound and JSON-runtime test failures, earlier candidate builds and ATD drawing-disabled screenshots remain separate evidence. K6 PIN/master-key recovery, physical-device lifecycle checks and signed upgrade/distribution qualification remain separate gates. See `docs/k5-archive-recovery.md` and `docs/UX-MANUAL-ACCEPTANCE.md`.


## K5 guided key repair foundation — 2026-10-12

- The wallet recovery banner and Settings security entry open a shared Recovery Center. It lists each affected wallet/root or network key and backup advisories, keeps view-only navigation available, and retains authenticated exact-data export. Network backup advisories route to that network key’s authenticated JSON export.
- Phrase, raw-seed/private-key and JSON recovery use the existing PIN check, then verify the backup against the durable account identity and rebind only that key. Wrong backups remain inline errors; ordinary import/create/add-account paths are bypassed. A malformed recorded identity offers a separate corrected-wallet import.
- Recovery fields are excluded recursively from Android hierarchy saving, autofill and personalized IME learning, and the screen uses FLAG_SECURE. Leaving it clears every mode and cancels pending derivation/file reads. JSON reads are bounded and close their stream; recovery uses the target’s signing type so historical Substrate ECDSA files are not rejected as EVM backups. Empty JSON passwords remain usable in recovery.
- Added 24 recovery strings and updated banner copy in all eight shipped locales. XML parity and `git diff --check` pass. The acceptance protocol is in `docs/UX-MANUAL-ACCEPTANCE.md`.
- Validation at source `a84e65b81`: 486 common, 474 account, 331 wallet and 120 app JVM tests pass without failures/skips (1,411 total). Debug app and both instrumentation APKs assemble. API 34 passes both recovery Compose UI tests (9.304 seconds: banner navigation, compact viewport and large text, all repair actions, view-only, corrected import and backup routing) and the direct Android hierarchy/input privacy test (0.018 seconds). Independent guided-flow review found no actionable correctness blocker. Evidence and APK digests are recorded in `docs/k5-recovery-validation.json`; this foundation is qualified for integration. The first full test attempt found that the archive key-name query exceeded the preference API bounds; the query now uses the supported 1,024-byte key / 1 MiB total limits. Earlier compile attempts were interrupted by edits during resource/Kotlin task ordering and are retained as failed evidence. Retained-archive restore/purge, K6 PIN recovery, final device acceptance and release qualification remain open.

## K4 recovery foundation — 2026-10-12

- Added identity-bound recovery derivation for Substrate, Ethereum, TON and independent network accounts. Mnemonic, raw-seed/private-key and JSON inputs are checked against the durable public identity and sign/verify validation before they can become a replacement. The historical empty Substrate path is handled without calling the path decoder, matching the shipped importer; historical TON spelling and spacing are retained when they derive the recorded key.
- One key can be rebound in a single snapshot-bound preference commit. Invalid active ciphertext and quarantine ciphertext are retained exactly, a healthy active key is preserved, pending mutations and stale identities are rejected, and unrelated keys and legacy shared payloads remain intact. The existing archive move API now supports atomic source replacement with an exact expected source snapshot.
- Ordinary wallet removal archives wallet material while clearing active slots, recovery markers and connection credentials. Archive capacity is checked before staging the deletion, and archived numeric wallet IDs remain reserved so a later create cannot silently reuse them. Archive conflicts keep original material and the deletion journal for reconciliation.
- Validation: 486 common and 454 account JVM tests pass, with zero skips/failures/errors. The instrumentation APK assembles, and API 34's real SR25519 provider successfully restores and validates all 102 frozen root/network payloads in one native test (1.526 seconds), including soft paths. `detektAll` passes, but its existing configuration excludes common/account sources; it is not a static-analysis qualification of those modules. `git diff --check` passes. Logs are in the parent workspace at `build/safe-upgrade/evidence/codex-20261012/android-k4-domain-5.log` and `android-k4-sr25519-device.log`. Earlier failed runs are retained; the first historical-vector run led to the empty-path fix. Independent K4 review found no actionable correctness blocker, and Android integration advanced to `8f71f949b`. Guided recovery UI, authenticated archive restore/purge, PIN recovery and final release gates remain open.

## A1 integrated validation — 2026-10-12 (Codex continuation)

- Preserved the interrupted historical phrase/soft-path acceptance updates at `43864ebe0`. Integrated validation exposed a stale fee-failure test mock after the existential-deposit API began returning `Result<BigInteger>`; the mock now returns a successful result.
- `:core-db:testDebugUnitTest`, `:common:testDebugUnitTest`, `:feature-account-impl:testDebugUnitTest`, `:feature-wallet-impl:testDebugUnitTest`, `:app:testDebugUnitTest` and `:app:assembleDebug` pass together. XML reports contain 1,464 tests, four skipped, zero failures/errors (95 core-db, 482 common, 437 account, 331 wallet, 119 app). Uses local utils `de30456c18d4839b0dc6c960eb8705478bd5facd`, Java 21, two Gradle workers.
- Evidence: parent workspace `build/safe-upgrade/evidence/codex-20261012/android-a1-validation-2.log`; the failed first compilation log is retained beside it. Device migration tests, independent A1 review, later recovery/portfolio packages and final release qualification are still pending. This debug build is not a production release.

## Recovery scoped to one key, the app always opens, emergency key access (K3) — 2026-10-12

- One quarantined or flagged secret used to close the whole app: the startup gate returned `RecoveryRequired("WALLET_SECRET_RECOVERY")` whenever any quarantine or public-identity marker existed for any wallet, the sheet offered only a support link and came back when dismissed, and inside the app a flagged Ethereum key, TON key or per-network key blocked Substrate signing, export and backup of the whole wallet; a Bitcoin row (33-byte account id) alone made a wallet recovery-required (audit AND-S4, AND-D6, AND-V3; android-keys A6, A7, N1; addendum U0.1-U0.4).
- Recovery is now scoped to the one key it is about (`WalletSecretScope`: Substrate, Ethereum, TON, or one per-network key). `WalletSecretAccessGuard.isRecoveryRequired(metaId, scope)` implements android-keys §3.2: a legacy `ACCESS_SECRETS` quarantine or marker blocks an ecosystem only while that ecosystem has no converted key; an account id that is not 20 or 32 bytes has no key of its own and is never recovery-required. `KeyPairRepository` checks only the key it signs with, `AccountRepositoryImpl` gates `getSubstrateSecrets`/`getEthereumSecrets`/`getTonSecrets`, per-network secrets, restore JSON and backup eligibility per scope (a blocked key contributes nothing and never hides the others), and Polkaswap, SORA DeFi and the main hub use `isSigningRecoveryRequired(metaId, chain)` (critique G8).
- New status API on `AccountRepository`: `walletRecoveryStatus(metaId)` and its flow (items with scope, kind `QUARANTINED`/`IDENTITY_MISMATCH`/`MISSING_KEY` and the key name; a missing Ethereum or TON key is reported but never blocking, and never for watch-only wallets), `isSigningRecoveryRequired(metaId, scope | chain)`, and `recoveryAssessment(metaId, ecosystem)`. `isWalletRecoveryRequired` keeps meaning "any blocking item" for the banner and portable export. New-network accounts are still derived only from verified recovery material (tested end to end with drifted entropy). Taira auto-provisioning stays (decisions.md: additive but hidden), so critique G7 is not applied.
- The startup gate always returns `Ready` once the database and secure storage are usable, whatever per-wallet recovery state remains, and publishes `WalletStartupRecoverySummary` (flagged and archived key counts, null if the inventory is out of bounds) through `WalletStartupRecoverySummaryHolder` for the Recovery Center (K5). `WalletRecoveryRequiredException` is no longer a startup diagnostic code. Global failures (master key loss, journal, identity-table schema, database integrity) still stop the app, but no longer at a support-only sheet.
- Every start re-evaluates the public-identity markers of existing wallets with this build's validators, including markers internal builds 230/231 wrote (`WalletQuarantineRevalidation`, markers before quarantines): a marker whose identity and stored key now pass is removed (the quarantine behind it is then released, an active legacy payload it held back is converted), a marker with nothing stored for it is removed, and a marker that still applies stays and blocks only its own key. Markers are non-secret; no stored value is touched.
- Emergency key access (U0.4): `WalletEmergencyExportActivity` asks for the wallet PIN (`WalletEmergencyAuthenticator`, same lockout as the PIN screen through the shared `PinAttemptLimiter`) and then lists every stored wallet key read straight from encrypted preferences by `WalletEmergencyRecoveryReader`: active, set aside (quarantined) and archived, in the 0.4, 1.x, 2.x/3.x (six- and seven-field), per-network and 4.x formats, with the recovery phrase, the TON phrase exactly as typed, raw seed, paths and private keys; a value it cannot decode is shown as stored, an undecryptable one is listed as such. It needs no wallet database, never writes, uses `FLAG_SECURE`, hides the keys when the screen leaves the foreground, and marks copies as sensitive. It is the primary action of the startup recovery sheet (support is secondary), a secondary action of the "wallet data could not be opened" sheet, and the action of the wallet recovery banner, whose copy now says one key needs attention and the rest keeps working. New and changed copy ships in en, in, ja, pt, ru, tr, vi and zh (`WalletRecoveryStringsLocaleTest`).
- Tests: common `WalletSecretAccessGuardTest` (scope matrix for every root key, quarantine and marker, with and without converted keys; per-network and Bitcoin ids; inventory; counts), `WalletSecretQuarantineTest`, `WalletEmergencyRecoveryReaderTest` (all 259 production-parity golden vectors exactly as stored, the BIP39 entropy vectors, set-aside and archived values, undecryptable and unknown values, unrelated keys), `WalletEmergencyAuthenticatorTest`; core-db `WalletQuarantineRevalidationTest` (five marker cases, one inverted K2 case) and `WalletEmergencyRecoveryShippedTest` (the verbatim 3.7.4 raw-seed writer's payload active, set aside and archived); account `AccountRepositoryImplRecoveryStatusTest` (an Ethereum key quarantined as 230 did leaves Substrate and TON signing, export and backup working; missing key; unverified material offers JSON only; unverified entropy derives no Bitcoin, Solana or Iroha account; a Bitcoin row never flags the wallet) and `KeyPairRepositoryTest` (Ethereum or TON quarantine or marker never blocks Substrate signing; another network's quarantine never blocks root signing; a Bitcoin row never blocks); app `WalletDatabaseStartupGateTest` (per-wallet recovery state, including real quarantine and marker keys, opens with a summary); Polkaswap and SORA DeFi guard tests on the scoped API; `WalletRecoveryStringsLocaleTest`. Device: `WalletEmergencyAccessDeviceTest` (the 259 golden vectors written through Android Keystore-backed encrypted preferences, the PIN, a set-aside key and ciphertext the device cannot decrypt; storage unchanged) and `WalletQuarantineRevalidationDeviceTest.markersInternalBuildsWroteAreReevaluatedAndScopedToTheirKey` (real SQLite, shipped SR25519 library). Run on 2026-10-12: JVM common 464, core-db 95 (4 network skips), account 430, app 50 (gate and hub), Polkaswap 22, liquidity pools 19, wallet balance list 5; API 31 emulator: common 1/1, core-db 2/2, app `WalletGateActivityLifecycleTest` 20/20, and a manual launch of the emergency screen (PIN step; no PIN and no keys shows "No wallet keys are stored"). Not run: API 26/30/34/36, the full device suites, hosted CI. The recovery UI beyond emergency access (per-scope copy, guided re-import, Recovery Center) is K4/K5; master-key loss and PIN reset are K6.

## Database preflight never locks out users upgraded by 4.x (K10) — 2026-10-12

- Every 4.0.1-4.2.0 release upgraded 3.x databases with a TonMigration (71 -> 72) that dropped the chain registry with foreign keys off and recreated it in its own column order (`b5e22d49d` `TonMigration.kt:99-135`, the same blob in every 4.x tag). The 76 -> 77 wallet-secret preflight accepted only the layouts this release's rewritten migration produces, then rejected the asset settings, favourites, nodes, explorers and per-chain accounts left pointing at networks 4.x dropped (Parallel, InvArch Tinker). Every user who installed 3.x and updated in place would have seen "Wallet recovery required" on every launch (audit `android-lockout-recheck.txt` schema-allowlist-1/-2, foreign-key-preflight-1). Keys were intact but unreachable.
- The chains contract now includes the layout the shipped SQL produced, read back from a replay of that SQL (not from Room's export). `scripts/extract-shipped-ton-migration-sql.py` freezes the shipped 71 -> 75 statements per release (4.0.1/4.0.2 stop at 73, 4.1.1 and the v4.2.0 tag at 74 with a `sora_card` table, Play 4.2.0 229 at 75) into `core-db/src/androidTest/assets/migrations/4.x-shipped/` with a hash manifest; `--check` verifies it against the release refs.
- `RebuildableTableRepair` runs inside the 76 -> 77 migration before the preflight and before any preference write. The wallet identity tables (`meta_accounts`, `chain_accounts`, `ton_connection`, `room_master_table`, `legacy_users_recovery`) keep their exact checks. Every other version 76 table is a cache or a user setting: a missing table is created, a layout or foreign key no release is known to have written is rebuilt from Room's version 76 DDL with rows copied by column name (asset visibility, order, favourites and contacts keep their rows; a required setting the old table left empty gets 0), and other explicit indexes are replaced by Room's. A table that already matches is never written. Node, explorer, chain-asset, pool and Nomis cache rows whose parent row is gone are deleted.
- The preflight's foreign-key check accepts only asset settings, favourites and per-chain accounts that point at a chain the registry no longer has; they are the user's and stay (every reader joins them to a chain, and they return if the network does). Any other violation, such as a per-chain account without its wallet row, still stops the upgrade with nothing changed (K3 owns what the user then sees). With foreign-key enforcement on (never during Room's upgrade) the repair leaves layouts to the strict checks.
- Tests start from shipped data. `ShippedVersion4UpgradeReplayTest` (device, blocking) installs the frozen 3.7.4 database, fills it as 3.7.4 ran, replays the frozen shipped SQL for four cohorts (4.0.1 to 73, 4.1.1 to 74, 229 to 75, and 4.0.1 then 4.1.1 then 229), moves secrets with a verbatim replica of the shipped 71 -> 72 secret separation (`ShippedSecretWriters.separateSecretsTon420`), re-syncs the registry without Parallel and Tinker, adds a TON wallet from the golden vectors, and opens the production builder twice: version 81, every wallet, per-chain account, asset setting and favourite row identical, chains still in the shipped layout, dangling caches gone, every secret kept (or its exact stored value archived), no quarantine or marker. Without this change all four cohorts fail with "chains has incompatible ordered column definitions"; with only the layout accepted they fail with "The database contains a foreign-key violation". `RebuildableTableRepairTest` covers an unknown cache layout, an unknown asset-settings layout, a missing cache table, dangling caches, a per-chain account on a dropped network, and that an unknown identity layout or a per-chain account without its wallet still fails with the schema and every row unchanged. `ReleasedSchemaPhysicalCohortTest` gains the shipped chains cohort; `RebuildableTableContractsTest` (JVM) pins the rebuild DDL, indexes and foreign keys to Room's 76 and 81 exports. Two device tests that encoded the old fail-closed behaviour were inverted (a per-chain account on a dropped network, a missing address-book index). JVM core-db 90 (4 network skips); device core-db 334 on an API 34 emulator.
- Not covered here: the 71 -> 72 preflight of this release (3.x straight to 4.2.0) still checks every table exactly; the audit found no shipped layout it rejects.

## Legacy conversion per ecosystem, secret archive and startup revalidation — 2026-10-11

- Raw-seed imports made on 2.0.2-3.7.4 with the Ethereum step declined stored the default Ethereum path with no Ethereum keypair. The legacy `ACCESS_SECRETS` conversion rejected that shape and quarantined the wallet's only key, on the 71 -> 72 route (users still on 3.x, whose wallet signs today) and on the 76 -> 77 route (users on 4.x, where 4.x had skipped it) (audit AND-V2). The conversion now judges the Substrate and Ethereum keys separately (`LegacyRootOutcome`): a path without a keypair is dropped, an Ethereum identity 4.x added in its own V3 entry while the legacy payload has no Ethereum keypair converts the Substrate key only (A1b), a legacy Ethereum keypair with no identity stays in the archived payload, and a keypair that fails signing integrity is corrupt for its own ecosystem only. When only the Ethereum key fails, the payload moves to the Ethereum quarantine and the converted Substrate key signs; an existing V3 entry that proves the identity is never overwritten and wins over the legacy copy.
- Legacy payloads are no longer removed after conversion. They move by exact ciphertext to `wallet_secret_archive:v1:<key>[:n]` (`WalletSecretArchive`), which nothing reads for signing, which is never recovery state, and which blocks master-key regeneration like any wallet payload. `WalletMasterKeyAttestation` validates an archived legacy CBC payload with the schema of the key it came from.
- 4.0.1-4.2.0 wallet deletion cleared only V2 keys, so every user who deleted a wallet (including the silently created TON wallet) carries its V3 secrets. The orphan inventory (76 -> 77 and every start) now archives them instead of quarantining them and writes no recovery marker; it no longer throws on unrecognised lookalike keys (they stay untouched and are counted) or on a TON connection row left by a deleted wallet. If no wallet row owns any root secret, nothing moves (critique D-2). The startup gate no longer blocks on the orphan report (audit AND-S1).
- New `WalletQuarantineRevalidation` runs on every start before the orphan inventory (addendum U0.3, audit AND-S4): quarantines and markers internal builds wrote for deleted wallets are archived and cleared; a quarantined key of an existing wallet moves back when it now proves its identity (SR25519 soft path, TON as typed, drifted recovery material); a quarantined legacy payload is converted as the upgrade converts it; a quarantine next to an active key proving the same identity is archived; an archived key whose wallet exists and whose active entry is missing moves back. Every change is an exact compare-and-set; an item that fails is left as it was for the next start, and only secure-storage failures propagate.
- V1 -> V2 (28 -> 29) migrates a V1 wallet whose keypair proves the account even when its mnemonic or seed no longer re-derives it: only material that does is carried into V2, no Ethereum account is derived from an unverified mnemonic, and the V1 source stays where it was.
- Nothing the upgrade or the start does with leftovers can lock the app. The startup gate skips a quarantine revalidation or orphan inventory that fails (bounds, a concurrent change, a malformed `ton_connection` row): both only release or archive in one atomic write, so a skipped pass leaves every value where it was and the next start retries; only secure-storage failures still propagate. The 76 -> 77 migration likewise skips orphan archiving it cannot prepare or commit instead of rolling the whole upgrade back. A legacy payload next to a V3 entry that proves nothing no longer fails the upgrade with a conflict: that V3 entry is never overwritten, the legacy payload is quarantined for its ecosystem, the 76 -> 77 pass quarantines the failing entry, and the startup revalidation converts the payload on the same start and archives both quarantines (an unreadable stale quarantine next to a proving key is archived too).
- The 76 -> 77 pass still rewrites a V3 or chain-account payload to drop a path that binds nothing (decision D3 not applied, because `PortableWalletV3OriginalSourceProof` requires the stored V3 payload to be canonical), but it no longer discards what was stored: the exact stored plaintext goes to `wallet_secret_archive:v1:<key>[:n]` in the same atomic write (`planArchiveCopy`), and with no free archive slot the payload is left as it is. A conflicting V3 entry no longer fails closed (see above). Scoped access, the app-wide `hasAnyRecoveryState` gate and the recovery UI are K3-K5; V0.4 and DB31 conversions are unchanged.
- Tests start from what shipped builds stored: `ShippedSecretWriters` (core-db `sharedTest`) replicates the 3.7.4 `importFromSeed` writer and the 4.2.0 `importAdditionalAccount` writer verbatim with file hashes. JVM: `WalletSecretIntegrityValidatorTest`, `LegacySecretConversionPlanTest`, `WalletOrphanSecretInventoryTest`, `WalletQuarantineRevalidationTest` (a fake bounded-query database), `WalletSecretArchiveAttestationTest`, `WalletDatabaseStartupGateTest`. Device: the 3.7.4 raw-seed wallet converts and signs with the shipped SR25519 library on the 71 -> 72 route (`TonMigrationSafetyTest`) and from a database-75 profile (`WalletSecretIntegrityMigrationTest`), with and without an Ethereum account added on 4.2.0; orphan, scoped-quarantine and V1 cases in `WalletChainAccountEcosystemMigrationTest`, `TonMigrationSafetyTest` and `V2MigrationTest`; the 259 production-parity golden vectors (`ReleasedWalletSecretMigrationAcceptanceDeviceTest`) keep every stored value, in place or, for the unbound-path rewrite, in the archive; a full archive never fails the upgrade (`orphanThatCannotBeArchivedNeverFailsTheUpgrade`); a conflicting V3 target is kept and converted on the next start on both routes (`concurrentlyCreatedConflictingTargetIsNeverOverwritten`, `preexistingConflictingLegacyTargetIsKeptAndNeverBlocksTheUpgrade`); internal-build recovery state is released on start (`WalletQuarantineRevalidationDeviceTest`). Run on 2026-10-11: JVM core-db 83 (4 network skips), common 76, app gate 44; API 34 emulator core-db 321/321. Not run: API 26/30/31/36 and hosted CI.

## Two-tier wallet secret validation — 2026-10-10

- Every runtime read of a root (Substrate, Ethereum, TON) or chain-account secret re-derived the keypair from its entropy, seed, mnemonic and path (PBKDF2; 100,000 iterations per TON read) and quarantined the secret on any mismatch. Wallets whose keypair signs correctly but whose recovery material does not re-derive it were therefore locked: every TON phrase 4.2.0 stored with a trailing, leading or double space or capitals (4.2.0 derived the key from the text exactly as typed), V1 seeds that are not 32 bytes, unparsable V1 mnemonics, Ethereum seeds that differ from the private key, and malformed or oversized paths.
- Validation now has two tiers. Signing integrity (payload decodes after normalizing the hex representation, keypair shape, keypair equals the durable identity, sign/verify or ECDSA derivation proves ownership) is the only failure that quarantines, by exact ciphertext as before. Recovery binding returns a `RecoveryAssessment` (`None`, `Verified`, `VerifiedAsTyped` for TON, or `Unverified` with the issues) and never throws. `validate*AndSanitize`, `SecretStoreV2.getChainAccountSecrets` and the V3 store `get` reads are signing integrity only and run no recovery derivation; new `validate*`, `getWithAssessment` and `getWithVerifiedRecovery` calls add the assessment. TON phrases are tried as typed, then normalized only to recognise the key; the stored text is never rewritten. The BIP39/TON checksum requirement is dropped. `WalletSecretScalePreflight.normalizePersistedHex` accepts a missing or uppercase `0x`, uppercase digits and surrounding ASCII whitespace, and still rejects odd lengths and non-hex text.
- Recovery material that does not re-derive the key is never handed out. `AccountRepository.getSubstrateSecrets`, `getEthereumSecrets` and `getChainAccountSecrets` keep the keypair but withhold drifted entropy, seed and path. `getTonSecrets` returns the stored TON phrase byte for byte, spaces and capitals included, whenever it re-derives the key (`Verified` or `VerifiedAsTyped`): 4.2.0 derived the key from that exact text, so it is the only phrase that restores the account (decisions invariant 6, critique C13); only a phrase that re-derives nothing is withheld. The "type it exactly" warning on the export screen belongs to K4. Phrase, seed and JSON export, Google backup eligibility (JSON only for such wallets) and Bitcoin/Solana/Iroha derivation all read through these. Signing reads through `KeyPairRepository` and is unaffected.
- Migrations quarantine only on signing integrity. The 76 -> 77 integrity pass (active V3 roots and chain accounts) and the legacy `ACCESS_SECRETS` -> V3 conversion (71 -> 72 and 76 -> 77) use the signing-integrity validators, so a key that proves its identity converts and stays active whatever its phrase, seed or path does; that material is kept verbatim and judged by its assessment at export time. This frees the SR25519 soft-path and as-typed TON wallets 4.2.0 stored, and pre-4.0 wallets whose seed, entropy or path drifted (audit AND-S2, AND-S3, AND-S7, AND-D7). V2Migration and the DB31 Ethereum migration still require verified recovery where they derive a new identity from it, and the portable-backup proofs stay strict; their partial outcomes, the Ethereum-path-without-keypair rule (AND-V2), archiving and revalidation of earlier quarantines are K2.
- Tests: the root, chain, store, preflight and quarantine JVM suites cover each tier, every issue, provider failures, the strict form and that runtime reads make no recovery call. `MnemonicCompatibilityVectorsTest` checks the re-implemented BIP39 against 4.2.0's library for 12 to 24 words. `ReleasedWalletSecretAcceptanceTest` (JVM, 155 vectors) and `ReleasedWalletSecretAcceptanceDeviceTest` (API 34 device with the shipped SR25519 library, all 259 vectors) run every secret exactly as 4.2.0 or 3.7.x stored it: all sign, and every phrase or seed 4.2.0 could export stays exportable. The pre-K1 validator rejected 71 of them. The same vectors go through the migrations: `ReleasedWalletSecretMigrationAcceptanceTest` (core-db JVM, the 155 non-SR25519 secrets through the migration validators) and `ReleasedWalletSecretMigrationAcceptanceDeviceTest` (a database-75 profile holding all 259 as wallets and chain accounts, opened twice through the production builder) quarantine and mark nothing and keep every ciphertext. Seven `TonMigrationSafetyTest` and two `ChainAccountSecretIntegrityMigrationTest` cases now expect drifted entropy, seed or path to convert or stay active with the material kept; signing-integrity failures still quarantine. Restoring the strict migrations fails all 14 new or inverted tests. `AccountRepositoryVerifiedRecoveryTest` proves drifted material is withheld, the as-typed TON phrase is handed out byte for byte and backup falls back to JSON. Two stale account device tests were aligned: the V1 portable proof no longer expects a different SR25519 nonce to be refused (AL-5), and the real-Room mutation test expected database 80. JVM: common 440, core-db 47 (4 skipped network checks), account 422. Instrumentation on an API 34 emulator: core-db 315/315, common 35/35 and account 17/17. Scoped detekt over every changed file reports no finding that the integration branch lacks. Not run: API 26/30/31/36 and hosted CI.

## Search and asset pickers use the portfolio rule — 2026-10-11

- Search, the Send / Receive / Swap / cross-chain asset pickers and the Send network field required an explicit Shown row (`enabled = 1`). Sync stores every new row as Auto, so on a new wallet Search answered "No assets were found", both pickers were empty and the network field listed nothing; after the upgrade nothing received since showed there (audit AND-G-03). They now ask `PortfolioVisibility`: Search lists what the portfolio shows, then (for a query) explicitly hidden assets and up to 50 reviewed registry assets the wallet does not hold yet, matching symbol, name or network name; spending pickers list any sendable balance with hidden ones in a collapsed section; Receive and swap "to" list every reviewed asset of the network. Entries read "SYMBOL · Network" and selection and exclusion compare (network, asset id). Testnets stay behind the Testnets switch.
- A Search result opens the exact asset detail like a portfolio row; the old intermediate screen (which also needed `enabled = 1`, AND-V-02) is deprecated and no longer opened.
- Tests: `SearchAssetsViewModelTest`, `AssetSelectViewModelTest`, new `PortfolioVisibilityTest` cases, `ChainDaoTest` (funded Auto rows in the network field) and a `ReleasedPortfolioUpgradeTest` case that starts from the frozen 3.7.4 database with rows in the states released builds wrote.

## fearless-utils pin moved to the 4.2.0 parity fixes — 2026-10-10

- Every pin (`scripts/ensure-fearless-utils.sh`, `config/android-runtime-source-pins.json` commit and tree, the CI, Internal App Sharing and release workflow jobs, README and release docs) moves from `1c80a2bf` to `de30456c18d4839b0dc6c960eb8705478bd5facd` (tree `4791f88b3529a8722916d48f6d2e0c0959ca21cd`), the head of the Utils `release/fearless-safe-upgrade` branch. It encodes the `CheckMetadataHash` (disabled) and `CheckAppId` signed extensions, keeps the fee asset native in `ChargeAssetTxPayment`, encodes top-bit unsigned integers, looks calls and events up by runtime index, and refuses relay-chain calls that moved to Asset Hub (Staking, NominationPools, FastUnstake, VoterList, Vesting, Claims, most Crowdloan calls) before anything is signed. With the old pin Polkadot, Kusama and Asset Hub rejected every extrinsic the app built. `sr25519-java` is unchanged (tree `f9e9aeba`), so the native artifact provenance still holds.
- The Utils commit is not on GitHub yet. Push the Utils branch before any CI, IAS or release run: every workflow checks Utils out by that exact commit, and the release checklist now confirms it with `git ls-remote`.
- `scripts/test-android-runtime-source-pins.py` now fails when the guard default, any workflow job, or a documented command names a different commit than the artifact pin, and when the pinned tree is not that commit's tree. `ExtrinsicServiceRuntimeCompatibilityTest` drives the app's `ExtrinsicService` and does not compile against the old pin: relay extrinsics carry `CheckMetadataHash` in the extras and the signed payload, Avail's app id lands in `CheckAppId`, relay staking is refused before it is signed or sent (the authorized path reads no key and never asks for authorization), and Asset Hub still builds staking calls.
- Relay-chain staking on Polkadot and Kusama now stops at fee estimation with `CallMovedToAssetHubException`, and the app says why. Every fee path that can meet the refusal shows "Moved to Asset Hub" (all eight locales): staking, pools, vesting and crowdloan contributions are handled on Polkadot or Kusama Asset Hub, this version can't manage them there, the relay chain would charge the fee and reject the action so nothing was signed or sent, and the Asset Hub account has the same address and is shown in the wallet. That dialog offers no Retry, because the call would be refused again; every other fee failure keeps the network error and its Retry.
  - Fee loader screens (relay staking setup and confirmation, bond more, unbond, redeem, rebond, reward destination, crowdloan contributions) show it in their retry dialog. The payout confirmation, which shows no retry dialog, reports the failure when the user confirms instead of "fee not loaded yet".
  - Pool screens (join, create, bond more, unstake, claim, redeem, nominate, edit and their confirmations) estimated the fee eagerly in `viewModelScope`, so the refusal, like any failed fee query there, crashed the app as soon as the screen opened. They now keep the failure, explain it when the user continues and start a failed estimation again from there unless the call moved to Asset Hub. Joining checks the fee before reading the pool limits, which the relay chain no longer stores.
  - Claim (vesting) is no longer offered on Polkadot and Kusama, which still list `Vesting.vest` but filter it at dispatch; Westend still dispatches it and keeps Claim. The claim screen's fee and locked-amount lookups no longer crash on failure: they explain it and offer Retry (or only Close for a moved call).
  - Not a regression against 4.2.0: 4.2.0 builds these extrinsics with `CheckMetadataHash`, so since the 2025 migration the relay chain would include them, charge the fee and filter the call, and the app would report the submission as sent. This release refuses them before signing. Managing stake on Asset Hub (the registry lists no staking there) is not part of 4.2.0 and needs product sign-off.
- Against a pristine Utils checkout at the new pin: `ensure-fearless-utils.sh` (library-only) and `verify-android-runtime-sources.sh` pass; the derived-tree, source-integrity (4 positive + 7 adversarial), composite-default, runtime-pin (13) and upstream-delta (1 + 25 negative) self-tests pass, and the real handoff export reports the new revision. `./gradlew runTest` passes 1,990 JVM tests in 19 modules (0 failures, 14 existing `@Ignore`d manual tests skipped) with `detektAll`, and `:app:assembleDebug` succeeds. Instrumented suites were not rerun: no androidTest uses the changed Utils extrinsic or type code.
- With the "Moved to Asset Hub" explanation on top of the integration branch (2026-10-11): `./gradlew runTest` passes 2,037 JVM tests in 18 modules (0 failures, the same 14 `@Ignore`d tests skipped) with `detektAll`, and `:app:assembleDebug` succeeds; the runtime-pin self-test passes 13/13 with the Utils checkout present, the upstream-delta self-test 1 + 25 negative, and `ensure-fearless-utils.sh` (library-only) verifies a pristine local clone at the pin. New coverage: `FeeEstimationTest`, `FeeFailureViewModelTest`, `FeeLoaderProviderTest`, `SetupStakingPoolFeeFailureTest`, `CreatePoolSetupFeeFailureTest`, `ClaimRewardsCallTest` (captured Polkadot, Kusama and Westend metadata) and `ClaimRewardsViewModelTest`. Not run: instrumented tests and a device check of the dialogs; no funded staking action was attempted on any network.

## TON provider: TI only when it proves the network (AL-11) — 2026-10-11

- The RC sent TON mainnet reads, fees, seqno and broadcasts to `https://ti.soramitsu.io` first and accepted any 200. TI reports `"network":"testnet"`, has no service-info route and no TonAPI routes (read-only checks, 2026-10-11 14:29Z; captured in `runtime/src/test/resources/ton/ti-deployed-20261011.json`), so TON holders would have seen a testnet balance and history on mainnet. Audit AND-R4.
- `TonRemoteSource` now asks `TonProviderSelector` for a verified endpoint per network. TI is used only after its service-info says schema 1, `ti.soramitsu.io`, ecosystem `ton`, `chainId` `ton:<network>`, `network`, its own `publicBaseUrl`, `readOnly` and the account capabilities; every balances, state and history answer must then carry the requested `network`, or it is rejected (`TonNetworkMismatchException`), never mapped or shown, and TI is passed over for 2 minutes. Otherwise the registry's TON node answers: its first TonAPI node for the network (`keeper.tonapi.io` in the registry 4.2.0 shipped; `tonapi.io` / `testnet.tonapi.io` when the registry lists none, as this build's bundled file lists only TI), used only after its masterchain head carries global id -239 (-3 on testnet). Each choice of the registry node is logged with the reason. With neither proven, requests fail with `TonProviderUnavailableException`: the balance loader writes nothing and marks the scan failed; nothing is broadcast. Checks are kept 5 minutes (30 s when they fail), so the wallet moves to TI shortly after TI proves mainnet.
- Broadcast goes through TI only when its verified service-info says `readOnly: false`; otherwise through the verified registry node. A failed TI broadcast resends the same signed message through the verified node (its seqno lets it be accepted once).
- `FL_ANDROID_TON_REGISTRY_NODE_FALLBACK=false` (build config `TON_REGISTRY_NODE_FALLBACK`, default true) removes the fallback once TI serves TON mainnet. TON mainnet always uses TI's public URL; `FL_ANDROID_TON_INDEXER_URL` still only serves testnet.
- TI parsing fixes found on the way: TI's state is read in the snake case TI sends (`account_state`, `last_seen_utime`); its `last_confirmed_seqno` is the masterchain seqno and is no longer used as the wallet seqno (the wallet's `seqno` get-method is). A TI answer without a valid `ton_raw` fails instead of reading zero. TI jetton lists are marked partial, so `TonBalanceLoader` no longer zeroes a held jetton TI does not list, and TI jetton entries without a wallet address, decimals or amount are left out.
- Tests: `TonProviderSelectionTest` (runtime, 20; a fake TI and TonAPI at the HTTP layer through the production Retrofit/Gson stack, TI answering as deployed, as mainnet, with 404s and with garbage; chains from the TON entries of the registry 4.2.0 shipped), `TonRemoteSourceTest` (21, updated), `TonBalanceLoaderTest` (3). common 396 and runtime unit suites green; `detektAll` clean.

## RPC helpers restored, unknown network minimums no longer zero (AL-10) — 2026-10-11

- `core-api` `RpcCallExtensions.kt` threw "unavailable in the public compatibility layer" for `getExistentialDeposit`, `getBlock`, `executeRuntimeCall` and `liquidityProxyQuote`. They now send what shared-features core 1.1.1.44-FLW (`7508bc83`, shipped in 4.2.0) sent: `tokens_queryExistentialDeposit` with the 4.2.0 currency argument, `chain_getBlock`, `state_call` delivered at most once, and `liquidityProxy_quote` with the same parameter order. An RPC error or empty result is a failure, never a default. Pool pending rewards decode as a SCALE u128 again (the RC read the bytes big-endian).
- `ExistentialDepositUseCase` returns a `Result`. 4.2.0 and the RC answered zero whenever the lookup failed, so send validation checked nothing: no warning that the sender's account would be removed, no dead-recipient error. Zero now comes only from the network, or for EVM and TON assets. Send shows a confirmable "Network minimum not checked" warning before the minimum checks (all 8 locales); the staking pool confirm screens stop with a retryable message; cross-chain validation (paused) stops. "Send all" and the cross-chain max input stay off without a known minimum.
- `pallet-assets` minimums (Polkadot Asset Hub USDT, USDC, DED, PINK, …) are read from `Assets.Asset.min_balance`. 4.2.0 read `Assets.Account` with the asset id alone, a prefix key that never has a value, and used zero.
- Block reconciliation (`SubstrateBalanceLoader.fetchTransfers`) reads blocks again and recognizes `Balances.transfer_allow_death`. Runtimes on polkadot-sdk 1.x have no `Balances.transfer`; looking it up threw, so 4.2.0 found no transfer on them either.
- Minimums that stay unknown today (read-only checks, 2026-10-11): the Bifrost Polkadot and Acala nodes answer `Method not found` to `tokens_queryExistentialDeposit`, and Interlay has no `Balances` pallet for its `ormlChain` assets. Their tokens without a registry minimum show the warning; the user can continue.
- Searched every `public-*` module and `core-api`. Remaining throwing stubs are deliberate: XCM (paused), passkey backup below Android 9 and its unused lifecycle methods, and the Drive "not configured" guard (AL-1). `core-networking` still returns empty results: SORA history, the SORA staking rewarded total and the SORA payout validator list (`BlockExplorerRepositoryImpl.getApy` has no caller). Not restored here; see the roadmap.
- Tests: `RpcCallExtensionsTest` (core-api, 13), `ExistentialDepositUseCaseImplTest` (12, on the AL-9 runtime snapshots and the bundled registry), `UnknownExistentialDepositValidationTest` (7) and `TransfersInBlockTest` (3, on 4.2.0-built Asset Hub extrinsics). core-api 50, common 396, feature-staking-impl 54 and feature-wallet-impl unit suites green; `:app:compileDebugKotlin` builds.

## SORA and ORML asset currency restored (AL-9) — 2026-10-11

- The RC's `core-api` returned `currencyId.toBigIntegerOrNull() ?: currencyId` as `Asset.currency` and the raw registry type as `typeExtra`. No SORA token balance key could be built, XOR took the `Tokens.Accounts` path although SORA keeps XOR in `System.Account`, SORA sends could not be encoded, and ORML tokens (`Token`, `VToken`, `VSToken`, `Stable`, `ForeignAsset`, …) passed bare strings. `Asset.currency` and `typeExtra` now follow 4.2.0 (`shared-features-android@7508bc83` `Asset.kt`) for every type, including `SoraUtilityAsset` for the utility asset of SORA mainnet and testnet; `currencyId` stays the registry string. An unknown type still refuses to encode instead of falling back to a native-token transfer.
- `WssSubstrateSource.getAccountInfo` now reads by `typeExtra`, like the balance loader. It read by registry type, so spendable and total XOR came back zero (a read-only check of 300 SORA mainnet accounts found 13 holding XOR in `System.Account` and none with a `Tokens.Accounts` XOR entry). The Polkaswap and Demeter authorizer checks that XOR balance before signing.
- SORA asset ids reach fearless-utils as unsigned byte values (`soraAssetIdCode`). 4.2.0 passed signed bytes and its writer kept the low byte; fearless-utils refuses negative `u8` since AL-6, which broke balance keys, sends, swaps and pool calls for every SORA asset with a byte of 0x80 or more (DEO, CERES, USDT, …). The bytes are 4.2.0's. This covers the transfer currency, the xorless transfer, Polkaswap swaps and reserve keys, and pool calls and keys.
- Evidence: golden vectors written by verbatim 4.2.0 code (380 registry and synthetic currency cases; balance keys, call bytes and signed extrinsics for 70 assets on read-only snapshots of SORA, Acala, Bifrost Kusama and Polkadot, Interlay, HydraDX, Pendulum and Polkadot Asset Hub). `AssetCurrencyParityTest` (core-api, 8) and `AssetCurrencyRuntimeParityTest` (wallet-impl, 5, through the real `SubstrateBalanceLoader` and `WssSubstrateSource`) find every balance key and call equal to 4.2.0, and the full signed extrinsic equal on SORA and Interlay. The Polkaswap and pools encoding tests build a swap, a deposit and a reserves key with DEO and USDT on the SORA runtime. T-2: 31 RC-built transfers (27 SORA assets including XOR, VAL and PSWAP, and 4 Asset Hub `Assets.transfer`) answer `Invalid(Payment)` on the live runtimes.
- Unchanged, and the same in 4.2.0: the Bifrost Kusama and Polkadot runtimes no longer have `Currencies.transfer`, so ORML token sends there fail in both (their balances load). The bokolo-cash xorless transfer has no 4.2.0 vector.

## Rollback safety guards — 2026-10-10

- `master` (database 73), `develop` (76) and the 4.2.0 production source (75) still call `fallbackToDestructiveMigration()`, and the rollback checklist told release owners to hotfix from `master`. A higher-version-code build from any of them would install over this release and wipe every table. The checklist now bases hotfixes on the latest shipped release tag, treats every rollback as a forward fix with a higher version code, and records the read-only branch findings: `master` has no functional change missing from the release branch.
- `core-db/released-database-versions.txt` records the database each distributed build leaves on devices: the oldest release v0.4.0 = 9, 4.2.0 (229) = 75 (confirmed from the official v4.2.0 APK: versionCode 229, Room identity of `75.json`), the 230 testing builds = 80, and this release 4.2.0 (231) = 81. `AppDatabaseVersionFloorTest` keeps `APP_DATABASE_VERSION` at or above every line up to the current version code, requires a line for that version code, rejects a later build with a lower database, rejects an exported schema newer than the current version, and keeps `EARLIEST_SUPPORTED_DATABASE_VERSION` at or below the oldest line so old migrations cannot be dropped. The 231 line counts once `versioning/version.properties` reaches 231, so the version bump and the 80 to 81 migration must land together.
- `NoDestructiveMigrationSourceTest` scans every module's shipped Kotlin and Java source sets for `fallbackToDestructiveMigration*`, `clearAllTables(` and `deleteDatabase(`. `scripts/audit-no-destructive-migration.sh` applies the same rules to any ref read-only, so a release owner can vet a hotfix base; CI runs its 14-case self-test and audits `HEAD`. Local `develop` fails it until it is fast-forwarded to the release at the end.
- Mutation checks: a destructive call in app or release sources, a raised floor, a newer schema export, a version code without a line, a mismatched version name, a lower later database and `EARLIEST_SUPPORTED_DATABASE_VERSION` raised to 10 each fail the matching test; test-only uses pass.

## Deterministic plaintext delivery tests — 2026-10-10

- CI run 1260 failed the cancelled-return and successful-delivery tests in `PortableWalletMaterialPreflightTest` with `IllegalStateException` after their 10 s wait. This was a race in the tests, not a product defect. The trivial capture could finish on the IO thread before the caller reached the suspension check in `withContext`. When that happened `withContext` returned directly and never dispatched back to the recording caller dispatcher the tests were waiting on.
- Both tests now hold the capture on a latch until the `UNDISPATCHED` caller has suspended (asserted), so completion always dispatches back. A third test forces the other ordering, with the IO block completing inline before suspension, and checks that the same buffer reaches the caller intact with no dispatch. `withPortablePlaintextOnIo` is unchanged and handles both orderings.
- In a single-JVM loop on a loaded machine, the previous tests failed 14 of 4,000 runs with the CI signature. The new tests passed 36,000 of 36,000, including with `kotlinx.coroutines.scheduler.core.pool.size=1` on one CPU. Removing the erase on cancellation fails the cancellation test 20 of 20 times. Erasing on successful delivery fails both delivery tests 20 of 20 times. The account suite passes 353/353, and 50 forced Gradle reruns of the class all pass. A forced Detekt analysis of the file reports the same seven existing findings as before; none are new.

## Legacy auto-hide normalization, database 81 — 2026-10-10

- Room 80 -> 81 `LegacyAutoHideNormalizationMigration` returns asset rows that 3.5.x-4.2.0 hid automatically to the nullable Auto state. It matches only `enabled = 0` rows that are not marked as not needed and whose seven balance columns are all NULL or exactly `'0'`. Hidden rows with any positive, negative (load error), non-canonical or unparsable balance stay Hidden, because they may be deliberate hides of funded assets. The single `UPDATE` changes only `enabled`; it deletes nothing, reads no secrets, leaves the schema unchanged (`81.json` keeps 80's identity hash) and matches nothing when repeated. The unused `hideEmptyAssetsIfThereAreAtLeastOnePositiveBalance` DAO query is removed.
- The released-schema matrix test is now `exactReleasedSchemaUpgradesToCurrentWithoutLosingWalletState` (`released schema N -> current`). Its DB 73-76 fixtures carry a funded 4.2.0-shaped wallet with shown, automatically hidden and one deliberately hidden funded row. The instrumentation verifier binds `APP_DATABASE_VERSION = 81` and the new identities, so later version bumps no longer rename the critical tests.
- `:core-db:testDebugUnitTest` passes 36 tests (4 pre-existing network skips), including the migration SQL shape and the production graph check. The full core-db instrumentation suite passes 306/306 on an API 34 emulator, including the 21-row real-SQLite case table with an idempotent re-run, a frozen 3.7.4 (DB 71) database opened through the production builder, and all seven released-schema matrix cases. Removing the reserved-balance guard makes the case table fail. Forced scoped Detekt passes for the new files. API 30/31/36 and hosted CI remain to be run.

## Google Drive web client release gate — 2026-10-10

- Every official release from 2.4.1 through 4.2.0 compiled the same Google OAuth web client (`WEB_CLIENT_ID_RELEASE`, project 621183184145) into `BackupService.create`; 2.3.1 compiled that project's Android client. Drive keeps backups per Cloud project, so 4.2.0 users' backups stay reachable only through it. It is not the `client_type: 3` client in 4.2.0's `google-services.json`. Its SHA-256 is pinned in `.github/release/android-google-drive-web-client-sha256.txt`; the id itself stays in the secret.
- The release workflow now checks the unsigned and signed AAB with `scripts/verify-android-google-drive-web-client.py`: their dex files must hold exactly one Google OAuth client id, matching the pin. The build-log verifier fails when Gradle reports `WEB_CLIENT_ID_RELEASE` undefined or blank, and `scripts/secrets.gradle` now warns on blank secrets, since GitHub passes an unset secret as an empty variable. The new suite (3 positive, 27 adversarial) runs in Android CI and the release controls; the build-log suite grows to 3 positive and 30 adversarial cases. Against the official APKs, the verifier accepts 2.4.1 through 4.2.0 and rejects 2.3.1 (other client) and 2.2.4 (no client).
- `docs/release-checklist.md` gains a Google Drive Backup section covering the secret, the signing-certificate SHA-1 registration (4.2.0 registers `B6:EE:8D:19:…`, not the upload certificate), why IAS and sideloaded builds cannot qualify Drive, debug-build use, and the manual Drive acceptance protocol, which still has to be run on a Play testing-track candidate.
- `lintVitalRelease` failed on the Drive engine's `google-http-client` 1.43.3 (the RC makes `TrustAllX509TrustManager` fatal, and the jar ships `SslUtils.trustAllTrustManager()` for `doNotValidateCertificate()`, which the engine never calls), so no release, IAS or CI lint could pass. `app/lint.xml` ignores the issue for that exact jar only; a temporary app class with an empty trust manager still fails `lintVitalRelease`. A local unsigned `:app:bundleRelease` (CI mode, `RELEASE_COMMIT` = lane head, the 4.2.0 client in the environment) then succeeds: the build-log verifier passes, and the AAB carries the pinned client once, in `base/dex/classes3.dex`. The same Gradle log with the secret blank or unset is rejected.
- Browser extension backups open with the password set in the extension, not in the app, yet the Drive import list showed them like app backups. They now say "Backed up from the browser extension" in the list and on the password screen (all eight locales). The manual Drive acceptance protocol checks that label and the empty-account path ("No backups in this Google account", "Use another Google account", "Import another way"), and its address checks cover the extension backups too. A local CI-mode unsigned `:app:bundleRelease` of that commit (fearless-utils at the pinned `1c80a2b`, the 4.2.0 client in the environment) passes `lintVitalRelease`, the build-log verifier and the AAB check, which finds the pinned client once, in `base/dex/classes3.dex`. Gradle's blank and unset warnings for `WEB_CLIENT_ID_RELEASE` added to that log are both rejected.

## Google Drive backup callers: classified errors, retries, no dead ends — 2026-10-10

- Every Drive backup and import screen classifies failures through `GoogleBackupIssue` (`feature-account-api`): wrong password, wallet already on this device, unreadable or newer backup, Ethereum-only extension entry, backup gone from Drive, no connection, sign-in or Drive consent needed, Google sign-in unavailable in this installation, Drive full, phrase or seed not verified, and unknown. Each is shown with a working action (try again, sign in, refresh, open wallet, or import or back up another way) and copy translated into all eight Android locales. The developer strings ("GoogleLoginError …", "DeleteGoogleBackup error") and the blanket "check your password" message are gone.
- Import from Drive no longer crashes when the extension list fails: the list shows a retryable error with "Import another way", Drive consent is requested for either list, Substrate-only extension backups (which all have an empty address) are listed and imported by Drive file id, and an orphan Ethereum keystore or Ethereum-only seed is refused instead of being passed as Substrate material. Downloaded phrases and seeds are checked against the address the backup is filed under (`BackupService.restoringSecretTypes`): a phrase an earlier release uploaded unchecked that restores another account is passed over for the verified seed or the wallet's keystore, and is only used, as before, when the backup holds nothing else.
- Creating a backup keeps the created wallet id across retries and a recreated screen, so a failed upload retries only the upload and never creates the wallet twice. "Back up later" and back hand a created wallet to the caller without marking it backed up, so the not-backed-up prompt stays; a failed creation is no longer reported as success. A phrase or seed the engine refuses is left out and the backup uploaded with the remaining verified types (critique D-7/T-7: JSON stays available), followed by an explanation. Drive consent and an expired sign-in are handled on the password screen.
- The sign-in result is classified with `GoogleSignInResult` on every screen that launches it. A build without a web client id explains this on Welcome and opens phrase or JSON import directly; backup screens hide the Google option instead. The backup wallet screen keeps "Backup to Google" visible while signed out so a cancelled sign-in can be retried, and its sign-in request is no longer dropped by `tryEmit`. Deleting a backup that is already gone counts as deleted; other delete failures are reported with Try again instead of being swallowed.
- New JVM tests cover the classification, the upload fallback, the importer against every production-written fixture, and the import, password, backup-phrase, Welcome and delete paths. The backup suite passes 368/368, the account suite 408/408 and the onboarding suite 11/11. The release web-client gate and the manual Drive protocol (AL-3) remain open.
- The Drive wallet list shows a progress indicator while it loads instead of a blank sheet. A Google account with no backups no longer ends at "Sumimasen! No import wallets were found": it says no backups were found in this Google account and offers "Use another Google account" (sign out, then pick an account) and "Import another way", next to "Create new account". The new strings are translated into all eight Android locales.
- Adding an account to an existing wallet no longer offers Google Drive or pre-installed import. Both restore whole wallets, and the add-account import screen has no source for either, so choosing one silently opened the recovery phrase import.

## Legacy Google Drive backup engine restored — 2026-10-10

- `:core-backup` again runs the shared_features 1.1.1.44 Google Drive engine that 4.2.0 shipped, replacing the unavailable stub. The Drive layout (`backupFolder` in `appDataFolder`, `<address>.json`, Chrome-extension files in the root), per-field XSalsa20/scrypt encryption and JSON settings are unchanged. Golden files written by the production 1.1.1.44 code, and by 1.0.1 (app 2.3.1, unreadable since 2.4.1), decrypt with this port, and the production decryptor reads and re-encodes byte for byte what the port writes.
- The extension backup list keeps only `.json` files from the `appDataFolder` root, the names every extension version writes, so passkey backup files stored there (`fearless-passkey-*.bin`) are never offered as wallets.
- Uploads are checked first: a PASSPHRASE or SEED that does not derive the wallet's backup address is refused before anything reaches Drive (`BackupSecretNotVerifiedException` names the type), undeclared material is dropped, and a Polkadot JSON export of a different chain account is left out beside verified types and refused on its own.
- Deltas from production: listings follow every page and read `modifiedTime`; several copies of one wallet are tried newest first; extension entries are addressed by Drive file id; the file index is in memory and the 4.2.0 `backup_preferences` DataStore is never read, written or deleted; `deleteAllAccounts` is not ported; a build without a web client id reports `isAvailable = false` instead of crashing; quota, folder-permission and consent failures map to typed exceptions that extend `Exception`. The Drive v3-rev197-1.25.0, google-api-client-android 1.26.0 and google-http-client-gson 1.43.3 dependencies are the 4.2.0 set; verification metadata and release locks are regenerated.
- The backup JVM suite passes 362/362 and the account suite 352/352, with no failures, errors or skips; `detektAll` passes; R8 over the new release dependencies succeeds with the module's consumer rules (the unused Apache transport is `-dontwarn`). Caller error handling (AL-2), the release web-client gate and the manual Drive protocol (AL-3) remain open; IAS builds cannot qualify Drive sign-in.

## Recovery coroutine cancellation cleanup — 2026-09-28

- Removed redundant cancellation checks before the inherited-job `withContext(Dispatchers.IO)` boundaries in generation creation, reconciliation and promotion. The context switch already checks cancellation before the journal block. Explicit checks around synchronous work and injected suspend callbacks remain where those operations do not guarantee cooperative cancellation. Journal admission, account/owner checks and recovery enablement are unchanged.
- The draft and semantic plaintext exporters now retain cleanup ownership across the IO context switch. If cancellation discards the completed result before delivery, the retained buffer is erased; a successful return transfers the original buffer to the caller. No `NonCancellable` context or replacement job is introduced.
- The final account suite passes 352/352, including deterministic cancelled-return and successful-delivery regressions; the backup suite passes 292/292. Both have zero failures/errors/skips. Default Detekt passes; forced analysis of the five changed Kotlin files reports the same 32 existing findings as the prior-head comparison, with none introduced.

## Android receiving inventory bound to staged cohorts — 2026-09-25

- New `FPWCAI01` v2 records bind the frozen Substrate receiving inventory by SHA-256. The candidate inventory contains the exact 87 raw genesis IDs from the bundled registry, including 17 disabled historical chains for public-identity preservation; it grants no network or signing permission. Staging, encrypted journal readback, restart and storage projection now use that same policy, so approved chain watches can be retained without a caller-supplied allowlist. A substituted or unsupported policy digest is rejected while the stored journal remains intact. Historical v1 after-images preserve their exact bytes, commitments and prior no-chain-watch interpretation, including during original-source-sidecar upgrade.
- The final account JVM suite passes 350/350 with no failures, errors or skips; default Detekt passes. Forced analysis of all nine changed Kotlin files reports four existing structural findings, matching the same four classes/functions in the prior-head source comparison; no new finding remains. Independent inventory review, actual cohort installation, installed original-key signing/export, provider/device recovery and delivered-build acceptance remain required. All receive-plan blockers remain and portable recovery stays disabled.

## Android incoming Substrate watch-chain policy candidate — 2026-09-25

- Chain-specific watch slots in the read-only receiving plan require an exact raw, 64-character lowercase genesis ID and a matching `SUBSTRATE` entry in the caller's reviewed `ApprovedGenesis` policy. Android's bundled `local_chains.json` uses raw IDs while the existing V2 signed-chain policy uses one `0x` prefix; the receive proof adds that prefix only for comparison and rejects prefixed, uppercase, unknown and wrong-kind watch IDs. Local watch export is unchanged. This earlier checkpoint required an explicit policy for a blocked read-only plan; the v2 after-image above now binds the compiled candidate inventory through staging and replay. Transactional installation remains a release gate.
- The full account JVM suite passes 347/347 with no failures, errors or skips, including default-policy stage rejection and a forged encrypted journal replay check. Default and forced four-file scoped Detekt pass. Hosted Android CI and IAS must qualify this exact successor separately from preceding `da0331fcb` checks. Independent review and enabled recovery acceptance remain open.

## Android iOS TON watch-address compatibility candidate — 2026-09-25

- Signed TON roots and incoming watch slots now share the same strict Wallet V4R2 address verifier. The receiving plan accepts the existing iOS TonSwift JSON `{workchain, hash}` shape only when a present 32-byte public key derives its workchain-zero hash, and it retains the original JSON bytes in the cohort after-image. Duplicate/unknown JSON fields, noncanonical Base64, wrong hash or workchain, missing public key and unqualified named chains still fail before staging. This public check does not prove address ownership or install a wallet.
- The full account JVM suite passes 345/345 with no failures, errors or skips, including valid and malformed iOS TON watch cases and the existing signed-root JSON cases. Forced scoped Detekt passes over the four touched Kotlin files. The preceding `1093b133d` source passed exact-head Android CI and IAS; this follow-up still requires its own hosted checks and independent review. Portable recovery remains compiled off.

## Android incoming watch-identity guard candidate — 2026-09-25

- The read-only receiving plan independently verifies every incoming watch slot before it can become a cohort after-image or fresh-install journal. Valid Substrate and supported chain public keys must derive their recorded account IDs; compressed EVM keys must derive 20-byte addresses, while address-only EVM watches remain valid. TON V4R2 watches require a matching public key. Duplicate watch ecosystems/chain IDs and unqualified iOS TON or named universal chain identities fail closed. This check also covers watch slots attached to signed source material without treating them as signing ownership.
- The full account JVM suite passes 343/343 with no failures, errors or skips; focused staging coverage confirms an unqualified incoming watch identity writes no journal or reservation. Default `detektAll` and forced scoped Detekt over all five touched Kotlin files pass against pristine pinned Utils `1c80a2bf` and WebSocket `9714b30b`. The installer, public-identity digest, original-key readback, iOS TON/chain interpretation and replacement-device acceptance remain open. Compiled portable recovery is still disabled.

## Android watch-wallet identity proof candidate — 2026-09-25

- The read-only Android semantic export proof now independently checks watch-wallet public identities after canonical decoding. Present Substrate and chain public keys must derive their recorded account IDs; a present compressed EVM public key must derive its recorded address; a native TON public key must derive the exact raw workchain-zero Wallet V4R2 address. Address-only EVM watch wallets remain supported. Duplicate watch ecosystems or chain IDs within one wallet fail closed. This proof does not establish ownership of a watch address, interpret foreign TON JSON or named universal chains, install a wallet, or enable recovery.
- The full account JVM suite passes 341/341 with zero failures, errors or skips, including four new watch-proof cases. Default `detektAll` and a forced scoped Detekt pass for all three touched Kotlin files; the default task still excludes `feature-account-impl`. The previous head's hosted Android CI passed, while the new head requires fresh exact-source CI and independent review. Original-key installation, signed upgrades and real cross-platform replacement-device recovery remain open.

## Android application-owned semantic exporter candidate — 2026-09-25

- The account preflight now has a separate read-only `captureVerifiedSemanticPlaintext` entry point. It captures fresh caller-owned `FPWMSM01` bytes from current Room wallet, chain and favorite rows plus guarded V1/V2/V3 encrypted secret stores under the cross-store mutation lock. It keeps selected wallet/order, watch custody and standalone EVM/native TON slots, then requires the combined source/signing proof and an exact caller-supplied approved-genesis policy before returning plaintext. A bounded global encrypted-key-name scan rejects V2/V3 secret namespaces whose Room wallet IDs no longer exist, all quarantine/recovery-marker aliases, and missing or unsupported namespaces under current wallet IDs. Ordinary numeric-prefixed nonsecret preferences are ignored. Known pre-V1 private/seed aliases and quarantined legacy sources also block capture. It resamples public identities, V1 ownership, custody, recovery state and global source-key names after proof. Failure clears the encoded buffer; the verified entry point never inserts custody markers, writes a backup state or uploads bytes.
- The two existing Android wallet display preferences have bounded shared-format metadata IDs: `10` for `wallet_selected_chain_id<id>` and `11` for `chain_select_filter_applied_<id>`. Each optional TLV contains the exact UTF-8 value, including a zero-length value when a preference is explicitly present but empty. ID `12` now carries a bounded, canonical list of explicit Room asset-row presentation state: exact chain/asset/account identity (including generic empty account IDs), nullable enabled and account name, signed sort index and marked-not-needed. Its Room projection verifies SQLite storage classes and raw UTF-8 bytes, and all three metadata sources are re-read under the cross-store lock after source/signing proof. Malformed rows, duplicate or unordered keys, metadata overflow and changed presentation fail closed. The cross-platform installer/readback, canonical identity digests, live reviewed genesis policy, device validation and backup-complete wiring remain open. Recovery remains disabled.
- The earlier account JVM checkpoint passed 337/337 with no failures or skips, including the 70-byte IDs 10/11 vector and the 126-byte ID 12 cross-platform vector (SHA-256 `842124d8aa738dc490b5f1366470f9e3183158514236b3c6ba4758bb927a66ab`); the current 341/341 result is above. One focused real Room query test passed on an API 34 emulator, and the AndroidTest source compiles. Default `detektAll`, forced scoped Detekt over the six new/relevant nonlegacy Kotlin files, and the exact pinned Utils/WebSocket source-tree verifier pass. The broad existing `AssetDao` file retains pre-existing formatting findings outside changed lines; default Detekt excludes that module and does not qualify a full-module static gate. API 30/31/36 migrations, cross-platform replacement recovery and delivered-build acceptance remain open.

## Android mixed-cohort original-source proof candidate — 2026-09-25

- A new unwired, read-only verifier checks a whole canonical Android semantic capture for source coverage: every signed wallet must have V1, V2 or V3 material, each V3 root and approved V2 chain must have exactly one matching original Android SCALE source, and every V1 typed source must pass the production legacy validator. It composes the existing signing/source proofs over one stable local copy, rejects orphan, foreign and unsupported original sources, mixed watch/signed custody, and metadata without a source mapping. Public watch identities and favorites receive structural checks only. Focused tests exercise V1/V2/V3 in one wallet and a separate signed/watch cohort. The final account JVM suite passes 316/316 with no failures or skips; default and forced four-file scoped Detekt pass. The exact pinned Utils and WebSocket source-tree verifier passes.
- The verifier by itself does not establish a public-identity digest, installed-key signing/export, complete metadata preservation, transactional installation, or actual replacement-device recovery. It is not a backup-complete authority; the compiled recovery gate remains false.

## Android historical V1 source proof candidate — 2026-09-25

- An unwired, read-only V1 verifier now decodes canonical semantic legacy slots, binds the stored SS58 address to the public account ID, checks mnemonic-to-entropy identity, reconstructs the original Create, Seed, Json, Mnemonic or Unspecified source type, and runs the production legacy private-key and recovery-material validator. The earlier mixed V1/V2 test now includes an exact V3 EVM source under the separate cohort proof above. The prior account JVM suite passed 314/314, forced scoped Detekt passed, and the SR25519 Android instrumentation APK compiled against pinned Utils `1c80a2bf`; device execution remains pending. V1 preference serialization is not preserved as opaque bytes in the shared semantic format; the typed original fields are retained. This component cannot prove installed-key export or cross-store restoration. Portable recovery remains disabled.

## Android V3 original-source proof candidate — 2026-09-25

- An unwired, read-only verifier now requires every Android V3 Substrate, EVM and native TON semantic root to have one exact original SCALE source. It validates each original against the public identity and recorded recovery material, compares every parsed key, nonce, phrase, seed and path to its semantic slot, and invokes the existing local signing proof. Its original strict entry point still rejects other slot families; a separate mixed-cohort entry point above accounts for V1/V2/watch/favorites. Focused JVM cases previously passed 4/4, including standalone EVM and native TON; the prior account suite passed 314/314 with forced scoped Detekt against pinned Utils `1c80a2bf`. iOS source shapes, complete metadata, installed-key export and transactional restore proof remain open. The compiled portable-recovery gate remains false.

## Android first-owner bootstrap candidate — 2026-09-25

- A disabled, unwired client now requires application-owned local authorization and original-key identity/signing/export evidence before requesting a new owner challenge. It builds the owner authority's exact v1 positional WebAuthn registration commitment and length-prefixed wallet message, requires a native discoverable registration with local PRF availability, obtains a Play Integrity Standard token bound to the signed proof, and sends only sanitized public credential/proof/attestation fields in a one-shot completion request. The returned session must match the random owner and namespace in the challenge; a fresh authenticated head read must be empty and use the same verified Google subject. No PRF output, Drive token or wallet secret enters the owner request.
- The original-wallet authorizer has no production implementation. It must re-derive every historical wallet identity and prove original-key signing/export, including after native UI returns; the module rejects a signing key or declared wallet identity that differs from the authorized original. The module cannot independently derive that public key from the source wallet, so the application-owned authorizer remains a critical trust boundary. An unknown completion is never retried inside the client: use discoverable owner authentication to determine whether an owner was created. This candidate discards and clears registration PRF output, creates no generation/wrapper, installs no wallet and never marks backup complete. The owner service is still not production admitted, and the compiled recovery gate remains false. The pinned-source backup module passes 287/287 JVM tests with zero failures/errors/skips; `detektAll` and diff checks pass. These tests use synthetic Credential Manager, Google account, server and Play Integrity responses rather than a real provider verdict.
- A separate disabled initial-backup method now uses a discoverable owner assertion with a local PRF evaluation. It checks the exact selected credential and server-issued owner session, reads a fresh authenticated empty head, then exposes PRF output only within a zeroizing local callback. Wrong credential/owner, an advanced head, and public-response serialization are covered by focused tests. It still does not create a wrapped key, encrypted generation, verified Drive upload or backup-complete state; the first-generation builder and real provider interoperability remain open.

## Android verified backup-head promotion candidate — 2026-09-25

- A disabled, unwired coordinator now ties the immutable Drive upload journal to exact downloaded bytes, local PRF unwrap/decryption/original-key evidence, owner grant/commit/status, and a fresh authenticated owner head. It repeats exact Drive byte readback after CAS without consuming PRF twice. A private fsynced commit-attempt marker blocks a second CAS after an unknown response or restart; even a torn marker allows only read-only status/proof reconciliation against the same journaled operation. Pending outcomes cannot authorize another upload or commit, and prior Drive generations are retained.
- The existing-head candidate verifier consumes native PRF output once, only after a server-verified credential-directed assertion. Synthetic tests cover lost responses/restart, missing media, proof failure, changed head/account, marker crashes/corruption, and credential proof ordering. The compiled recovery gate is false. Empty-head enrollment still lacks the verified candidate assertion API; production original-key signing/export callback, service deployment, real device/provider restoration, and signed distribution gates remain open.

## Android owner generation metadata HTTP candidate — 2026-09-25

- A disabled, unwired `PasskeyBackupOwnerGenerationHttpClient` now matches the owner authority's exact generation grant, commit and operation-status routes. The prepared request binds a canonical operation ID, generation context, expected authenticated head, bundle digest, key epoch and Drive file ID. Commit uses a short-lived grant bound locally and server-side to that exact request and owner session; status reconciles only the same operation and rejects a substituted descriptor. Responses use bounded, closed JSON parsing with duplicate-key, type and expiry rejection.
- Each call derives the storage-account binding from the verified selected Google subject through `GoogleDriveAccessTokenProvider`, then rechecks account, session expiry and cancellation after transport. Only public metadata and the appropriate owner/grant token reach the authority; Drive tokens, PRF output and wallet plaintext do not. The client itself does not upload, download, decrypt, prove original keys, install a wallet or mark backup complete. The full backup-module JVM suite passes 257/257 with no failures/errors/skips, and `detektAll` passes against clean pinned Utils `1c80a2bf`. Production owner-service deployment, verified local backup evidence, durable grant/head coordination, physical-device cross-platform recovery and signed distribution remain open; the compiled recovery gate stays false.

## Android authenticated owner-head read candidate — 2026-09-25

- The disabled, unwired `PasskeyBackupOwnerHeadHttpClient` sends only a versioned metadata query with a fresh owner session. It derives the expected storage-account binding from the verified subject returned by the same `GoogleDriveAccessTokenProvider` used for Drive, then rechecks the selected subject after the request. It does not send the Drive token or accept a caller-supplied binding digest.
- Strict response parsing and the authenticated-head model require the exact owner, backup namespace, current/previous generation chain, digest and Drive file identities. The client cannot read a backup, unwrap a key or install a wallet. Local backup-module JVM tests pass 251/251 and `detektAll` passes. Production owner-session integration, deployed authority and real selected-account/Drive behavior, physical-device cross-platform recovery, original-key proof and signed distribution acceptance remain open; the compiled recovery gate stays false.

## Android owner passkey authentication candidate — 2026-09-25

- The backup module now has a disabled, unwired Android client for an existing owner's discoverable passkey. It sends the owner authority's exact `schemaVersion: 1` / `platform: android` challenge request, rejects duplicate or unexpected response fields, wrong RP/platform/kind, malformed identifiers and stale or overlong challenge lifetimes, then asks Credential Manager for a user-verified discoverable assertion without PRF. A challenge that expires while native UI is open cannot be completed.
- Completion contains only the native sanitizer's allowlisted public WebAuthn assertion fields. Unexpected native PRF output is rejected and cleared locally; it is never placed in the server request. The returned owner session is shape-, platform-, token- and expiry-checked and redacted from diagnostics. The authority HTTP candidate itself is not deployed or production admitted; this client does not perform bootstrap, backup readback or wallet installation. The compiled passkey backup release gate remains false, and no recovery UI is connected.
- Local backup-module JVM tests pass 245/245, including seven owner-authentication tests, and `detektAll` passes with clean pinned Utils `1c80a2bf`. These are synthetic transport/native-gateway checks; real Credential Manager and production authority interoperability remain open.

## Android portable original-source sidecar candidate — 2026-09-25

- The disabled cohort stager now writes a version-2 encrypted journal and a separate, bounded encrypted original-source sidecar in the same durable preference compare-and-swap as the exact ID markers. The sidecar retains canonical opaque `AUXILIARY_SOURCE` fields plus the ordered local/portable wallet IDs, source positions, per-wallet semantic commitments, operation ID and after-image digest. These opaque fields can contain wallet secrets; normalized V1/V2/V3 private-root slots remain in the still-pending cohort journal. A preexisting version-1 journal can be upgraded by an exact-token compare-and-swap during replay; missing, altered or rogue sidecar keys quarantine version-2 replay and abandonment.
- The full account-module JVM suite passed 305/305 with no failures or skips, including the 49 focused cohort cases. API 34 real Room restart tests passed 2/2; `compileDebugKotlin`, `detektAll` and diff checks passed against clean pinned Utils `1c80a2bf`. This is staging and retention groundwork: it neither writes target secret stores or wallet rows nor authorizes journal retirement, wallet signing/export or portable recovery. A future installer must verify every target store and original identity before retaining the sidecar and retiring the cohort journal.

## Android portable receive origin reservation — 2026-09-25

- Candidate Room schema 80 records the exact 16-byte portable wallet ID and historical source position beside each reserved local wallet ID. New two-wallet cohorts bind those origins inside the Room reservation transaction. Replay compares them with the encrypted `FPWCAI01` after-image, rejects swaps or altered positions, and upgrades a v79 pending row only when that exact journal is still present. Abandoned destination IDs stay fenced while a later attempt can reserve fresh local IDs for the same source wallets.
- Focused stager JVM tests passed 18/18, migration-policy tests 6/6, the API 34 core-db compatibility shard 10/10 (including seven released-schema→80 upgrades and the v79→80 migration), and real Room two-wallet restart plus v79 pending-origin replay tests 2/2 against clean pinned Utils `1c80a2b`. The release gate now requires all 41 compatibility identities and a full-profile floor of 343 with both real Room tests; its result-parser fixtures passed 2 positive/40 negative, CI-gate fixtures 1 positive/107 negative, and evidence packaging 49/49. These are component checks. The cohort still has no installed V1/V2/V3 or original-source secrets, wallet rows, cross-store commit/replay, signing/export proof or enabled recovery. Final-source API 30/31/36 migration, signed upgrade and replacement-device acceptance remain open.

## Android PRF consumption boundary — 2026-09-24

- The disabled generation readback now checks the public credential identity and the presence of a native PRF result, then consumes the server-verified assertion and confirms the unchanged owner head **before** exposing PRF bytes to local unwrap/decryption. A native ceremony result releases its PRF output at most once, including when a callback fails or is cancelled; the callback's byte array is cleared on exit. Server rejection therefore cannot reach the wallet verifier, and a second local unwrap cannot reuse that result.
- The complete backup-module JVM suite passes 238/238 with zero failures, errors or skips under the clean pinned Utils source; `detektAll` passes. The focused native/readback suites pass 19/19, including a server-rejection ordering case. These are local synthetic tests. A production owner authority, original-wallet verifier, real Google Password Manager PRF interoperability, replacement-device recovery and signed distribution acceptance remain open. The recovery flag remains disabled.

## Android first-owner Play Integrity request candidate — 2026-09-24

- The disabled Android backup module now has a native Play Integrity Standard gateway, using Google's `com.google.android.play:integrity:1.6.0` from checksum-pinned Google Maven artifacts. It prepares a provider only with an explicitly supplied Cloud project number and requests a token with the exact canonical base64url SHA-256 nonce derived from the already signed first-owner wallet-proof message, normalized public key, scheme and signature. The nonce matches the owner-authority v1 domain/length-prefix contract. The opaque result exposes only a redacted typed token and a `kind`/`token` server-attestation shape; it never handles PRF output or wallet plaintext. The current production factory remains compile-disabled and the requester is not wired into owner bootstrap or UI.
- Google's current Standard API supports the existing Android minimum (API 26); its library requires API 23+. The Android tests consume the owner authority's identical public synthetic fixture for real server-verified Ed25519 and secp256k1 wallet proofs and assert the exact request hashes after secp256k1 key normalization. Release qualification still needs the audited Cloud project number, exact Play-distributed signing certificate and allowed version codes, owner HTTP/session integration, live Google token/decode verdicts, first-owner wallet signing and replacement-device tests. Local fake-gateway tests cannot establish a production verdict or enable recovery. See [Google Standard requests](https://developer.android.com/google/play/integrity/standard) and [library release notes](https://developer.android.com/google/play/integrity/reference/com/google/android/play/core/release-notes).

## Authenticated Android generation readback candidate — 2026-09-24

- A disabled, unwired readback coordinator now obtains a head through an authenticated Fearless owner-session interface, downloads its exact Drive ID/digest/context, takes the matching wrapper's PRF salt, runs a new credential-directed native assertion, and requires a one-use server-verified assertion result for the same credential, owner head and wallet identity before local unwrap/decryption and original-key signing/export checks. It rechecks the selected Google subject and the live owner head after asynchronous verification. Native PRF output remains local and its asynchronous callback copy is cleared even on failure. It returns only redacted local evidence; no wallet is installed, head promoted or backup declared complete.
- The authority interface has no production HTTP adapter or default, and the plaintext-wallet verifier in tests uses synthetic material; a real owner bootstrap/session, provisioned Google passkey/Drive consent, application-owned proof of every historical key and cross-platform device recovery remain mandatory. The compiled recovery flag remains false. Strict pinned-source backup-module JVM tests pass 229/229 with zero failures, errors or skips, including wrong head/account/credential, missing PRF and tampered Drive bytes. The complete local `runTest` passes 1,683 JVM cases with zero failures/errors and 14 existing skips; `detektAll` and the pinned Utils/WebSocket source verifier pass. Hosted exact-head CI and native device acceptance are still required.

## Android portable receive abandonment tombstones — 2026-09-24

- Candidate schema 79 now stores a state and SHA-256 commitment to each cohort's sorted destination IDs in every Room reservation row. Abandonment first changes the exact pending set to permanent `ABANDONED` tombstones in a Room transaction, then compare-and-swap removes the exact encrypted cohort journal and preference markers. A failed Room commit leaves the pending journal replayable; a failed or uncertain preference commit leaves fenced tombstones that replay can finish. Unrecognized pending rows and mismatched commitments remain quarantined. The SQLite insert trigger and signed-wallet ID allocator treat both states as occupied, so an abandoned ID is never reused.
- Tombstones are deliberately retained to close the preference/Room crash window, with a hard 8,192-row staging quota; exhausting that quota requires a separately reviewed maintenance protocol, never a silent deletion. The process-wide mutex does not authorize arbitrary cross-process preference writers. No wallet row, target secret, installer or recovery flag is added by this change. The revised migration trigger passed 1/1 on an API 30 emulator, including direct insert and wallet-ID update fences. The API 30 compatibility profile passed 40/40 before that final update-trigger addition; its trigger was then retested. Final-source full and API 31/36 compatibility runs, signed upgrades and distribution evidence remain required.
- Local final-source account JVM tests passed 299/299; core-db JVM tests passed 29 with four existing skips. Scoped Detekt and both Android-test APK builds passed. The migration-result verifier passed 2 positive and 36 adversarial cases, the CI-gate verifier passed 1 positive and 106 adversarial cases, and evidence packaging passed 49 tests. These checks establish the reservation safety component, not portable wallet recovery or production release acceptance.

## Android portable receive Room reservation — 2026-09-24

- The preceding schema-79 checkpoint added `portable_wallet_reservations` and a database insert trigger that rejects a visible `meta_accounts` row using a reserved receive ID. The internal stager committed exact Room reservations with the encrypted `FPWCJ001` journal. Its original abandon sequence could strand an unrecognized Room reservation if preference deletion succeeded before Room commit; the tombstone transition above replaces that sequence. New wallet rows, V1/V2/V3 target secrets and feature enablement remain untouched.
- The preceding v78→79 migration checkpoint had an exported Room schema and an instrumentation test for preservation and explicit/automatic ID collision rejection. Local account JVM tests passed 295/295; core-db JVM tests reported 29 passed, four existing skips; both Android-test APKs compiled. Default and forced scoped Detekt passed. Its release migration-result verifier fixtures passed 2 positive/34 adversarial cases. These are historical component checks, not revised-head or distribution evidence.

## Portable cohort fresh-install reservation prerequisite — 2026-09-24

- A new internal fresh-install stager checks that Room has no wallets, allocates distinct positive local IDs under the existing cross-store wallet mutex, and durably stores the exact `FPWCAI01` cohort journal with encrypted preference-side ID markers in one compare-and-swap. The normal signed-wallet allocator skips marked IDs; the watch-only allocator rejects a marked inserted ID and rolls its Room transaction back. An active cohort journal blocks both creators even if a marker disappears. A restart can reload the exact journal and its markers; current abandonment retains Room tombstones after removing preference markers. Orphan or changed markers fail closed and need explicit reconciliation.
- No Room wallet row or target signing secret is written by staging. The earlier preference-only markers protected the two current in-process allocators; schema 79 additionally fences direct Room inserts at the reserved ID. A future installer must recheck Room and all namespaces, preserve and verify every V1/V2/V3/standalone EVM/native TON original, resolve missing backup-state and custody fields, and implement complete cohort-wide write/replay before wallet publication. The staged after-image retains receive blockers, and portable recovery stays disabled.
- Focused JVM coverage exercises both allocator paths, multiwallet and source-byte preservation, occupied IDs, before/after-commit failures, replay, marker tampering, CAS races and exact-token abandonment. The full account module passes 291/291 with no failures or skips against pinned Utils `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`; default `detektAll` passes but excludes this module. Forced scoped Detekt passes on the six focused cohort/watch files; the large existing wallet-coordinator source and test retain previously reported formatting debt outside this change. These local component checks do not qualify installation, signing/export or replacement-device recovery.

## Portable cohort storage projection candidate — 2026-09-24

- An unwired, pure projection now independently revalidates an allocated-ID `FPWCAI01` after-image and names each `FPWMSM01` wallet, selected position, public root, V2 chain, favorite, custody classification, metadata, exact V1/V2/V3 secret candidate and opaque original-source sidecar. It preserves standalone EVM and native TON material byte-for-byte, rejects repeated public wallet or chain identities, and keeps every receive-plan blocker. No Room or encrypted-preference mutation calls it.
- Fresh-install Room positions are now projected as distinct compact `Int` values in the authoritative FPWMSM01 wallet-list order, while exact historical unsigned source positions remain available separately. Equal or reversed source positions never wrap, reorder or collide; invalid uint32 values and oversized cohorts fail closed. This policy does not merge with existing local wallets.
- This is a logical intent, not an installable Room image. Backup flags and Google backup address are absent; custody needs a verified public-identity digest; watch and original-source storage/signing mappings remain unproven. Schema 79 reserves Room IDs but does not install any wallet row. Cross-store atomicity, original-key signing/export, replacement-device evidence and final-source CI remain open. Portable recovery stays disabled.
- At the earlier projection checkpoint, focused after-image/projection JVM cases passed 11/11 and the full account module passed 277/277 with no failures or skips against pinned Utils `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`. Forced scoped Detekt covered both changed Kotlin files; these local checks are component evidence only. The current module total is recorded above.

## Android KaiaScan history source candidate — 2026-09-24

- Bundled Kaia mainnet and Kairos history now route through the exact chain-bound KaiaScan OAPI hosts with a separate Bearer key. The old mainnet Scope URL can only redirect internally to the approved host. Native and fungible-token responses have separate mappings, exact decimal precision, bounded pages and fail-closed malformed/provider errors; token fees remain unknown because the token response has no fee field. Wallet and chain identities are unchanged. [The provider gate](kaia-history-production-gate-20260924.md) records the official contract and live acceptance still required.
- Focused Kaia JVM tests pass 9/9; the complete wallet module passes 208/208 with no failures or skips. Runtime tests report 58 cases, 48 passed and 10 pre-existing skips. `detektAll` passes but does not cover the wallet module. A provisioned live key, independent receipt/freshness/pagination comparison, final-source signed artifacts and store-upgrade evidence remain open. The clean `9180bc535a55ad93f79c16e53b1f17a16364c82b` unsigned AAB (`90fdb72e05b4db15192ac474983a535f90e0834d08f7e899278db4b5cfe5d745`) passed 16/16 native 16 KiB alignment but predates this change and is supporting evidence only.

## Android history failure handling and intermediate AAB — 2026-09-24

- OKLink and legacy Klaytn history providers now propagate transport, provider and malformed-page errors so a failed fetch cannot clear or advance the local history cursor as a successful empty page. OKLink binds native rows to the native symbol, token rows to the exact contract, converts amounts without silently truncating excess precision and reports token fees in native units when the utility asset is known. Bundled X Layer explorer links now point to the matching mainnet and testnet explorers; the historical testnet chain identity remains unchanged.
- Seven focused JVM history-source tests pass (7/7, no skips), and the full wallet module passes 202/202 with no failures or skips against pinned Utils `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`. `detektAll` passes but excludes this module. The source is not yet qualified with provisioned live history-provider credentials or production freshness and pagination evidence.
- The clean pre-change Android head `bbd9f34f4fc137444ce01defe45d5ba57e5b766c` built an **unsigned intermediate** release AAB with the pinned Utils and WebSocket source checkouts, Android SDK 36 and JDK 21. Its SHA-256 is `f958b9c6da5c55228db7ca107b736f1006ddf668b30206a8f8810f6091c183bc`; the native verifier passed all 16 libraries across arm64-v8a, armeabi-v7a, x86 and x86_64 at 16 KiB alignment. This artifact predates the history fix and cannot qualify the final source, Play signing or a distribution upgrade.

## Android schema-78 instrumentation assertions — 2026-09-24

- [PR #1260's API 34 CI run](https://github.com/soramitsu/fearless-Android/actions/runs/35951988657/job/107482448391) failed 60 `core-db` and 5 account instrumentation tests because their production-open assertions still expected schema 77 after the database advanced to 78. The `core-db` result verifier correctly rejected the failures. The database tests now use `APP_DATABASE_VERSION`, and the account test target is 78. The hosted migration matrix must run again from the updated source head before any qualification claim.
- Clean pinned dependency verification and changed instrumentation-source compilation pass. On an isolated local API 34 emulator, `core-db` passed 301/301 and `feature-account-impl` passed 12/12 with no failures, errors, or skips. The corrected current-schema orphan-inventory fixture also passed its separate 1/1 rerun.

## Android V2 chain-account proof candidate — 2026-09-24

- An unwired, read-only verifier now requires an explicit caller-supplied list of canonical chain genesis IDs and Substrate/Ethereum identity kinds before it will examine V2 chain-account slots. For each slot it requires one matching Android V2 original SCALE source, rejects any lossy canonicalization, checks the original source's public key, account ID, crypto type, private key and optional recovery material against the semantic slot, and signs and verifies a wallet- and chain-bound local challenge. Unknown chains, wrong identity kinds, missing or duplicate originals and unsupported source shapes fail closed.
- Focused JVM tests pass 6/6 against clean pinned Utils `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`; forced scoped Detekt on both new Kotlin files and diff checks pass. This is a component proof only. No reviewed production genesis policy calls it, and the receiving plan still marks every V2 chain slot and the transactional installer unavailable. SR25519/native-provider behavior, iOS-specific chain originals, actual installed-key export and replacement-device restoration still need qualification. Backup completion and portable recovery remain disabled.

## Android migration evidence identity repair — 2026-09-24

- Hosted Android PR CI reached the API 30 migration verifier after the connected test task succeeded, then failed because it required seven schema-77 testcase names while the checked-in migration matrix and database version are 78. The verifier and its fixtures now require the seven exact schema-78 identities and assert that the source version, method name and parameter label agree. Local verifier fixtures pass 2 positive plus 34 adversarial cases; evidence-packaging tests pass 49/49. The hosted API 30/31/34/36 matrix must run again from the new source head before this is qualified.

## Portable cohort journal staging candidate — 2026-09-24

- The internal `FPWCJ001` journal now durably stages one exact, encrypted `FPWCAI01` multiwallet after-image with ordered local IDs and all canonical semantic source bytes. On restart it checks the version, length, digest, canonical encoding, V1/V2/V3 candidate keys, other active wallet journals, wallet-scoped secret namespaces and exact ID markers. Staging and exact-token abandonment compare-and-swap the journal, preference-side ID markers, TON Connect and legacy mutation journals, and known target keys; uncertain writes require read-only reconciliation. Prefix-based namespace inspection and Room occupancy cannot join that exact-key CAS, so replay rejects later collisions and the journal does not authorize installation.
- At the earlier journal checkpoint, its focused JVM suite passed 14/14 against pinned Utils `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`; forced scoped Detekt passed on both new Kotlin files. A supplemental read-only review found an abandonment race, corrected before publication with a targeted regression. The current journal and module results are recorded above; hosted CI must still qualify the final head.
- The journal does **not** install wallet secrets or Room rows, reserve Room IDs, map all sidecar destinations, prove every original key, or mark backup/recovery complete. Every replayed after-image retains the installer and source blockers. The existing single-wallet journal remains the only active wallet mutation path, and portable recovery remains disabled.

## Portable cohort after-image candidate — 2026-09-24

- An unwired, versioned `FPWCAI01` after-image now binds ordered positive local wallet IDs to the exact canonical `FPWMSM01` cohort. It retains source positions, selection, every wallet/metadata/slot byte and all receiving blockers, and derives candidate V3/V2/V1 secret namespaces without writing any store. Malformed wire, duplicate local IDs or candidate keys, unsupported V2 account IDs and mismatched V1 public addresses fail closed. Record and destination string rendering is redacted.
- Focused JVM tests pass 6/6 against pinned Utils; forced scoped Detekt on the three touched Kotlin files and diff checks pass. This is a schema prerequisite only: the active mutation journal still handles one wallet and up to three V3 roots, with no cohort-wide *mutation* replay, Room/secret-store installation, original-source mapping or replacement-device proof. Portable recovery remains disabled.

## Portable receiving plan candidate — 2026-09-24

- An unwired Android planner now decodes and independently owns every byte of a canonical `FPWMSM01` cohort. It retains wallet IDs, source positions, selection, presentation metadata, signed roots, V1 legacy sources, V2 chain accounts, favorites, auxiliary original bytes and watch identities as logical receiving intents. The plan can reproduce the exact semantic payload, erases its private byte arrays and cannot return an install-ready result. Every unproven source or role has an explicit blocker; no DAO, Keychain/secret-store, Drive or backup-completion path calls it.
- Focused planner JVM tests pass 5/5 against the clean pinned Utils checkout; forced scoped Detekt on the two new files and diff checks pass. Current root-signing proofs remain narrower than a complete wallet. A durable cohort-wide cross-store journal, per-chain/V1/original-source signing and export proof, collision checks, replacement-device acceptance and final-source CI are still required before installing any wallet.

## Android local V1 material capture — 2026-09-24

- The internal, bounded Android-local draft now retains a validated V1-only Substrate source in its own versioned slot. It records source type, exact original address and keypair/nonce, optional seed and derivation path, mnemonic and its derived entropy. Bounded V1 key-name discovery rejects malformed aliases, active addresses without a durable wallet owner, duplicate aliases and inventory changes; repository reads retain the existing recovery guard and cryptographic identity check. Unowned keys remain untouched for reconciliation. V3 Substrate, independent EVM and native TON roots, V2 chain keys, wallet selection/order and favorites remain separate and unchanged. Watch-only material still fails closed.
- Focused draft/preflight JVM tests pass 26/26 and the full account module passes 194/194 with no failures or skips against the clean pinned Utils checkout at `1c80a2bf3fa1f996cf1328873e09f282ee29b69e`. The default adjacent Utils checkout is at a different commit. The default `detektAll` task succeeds but explicitly excludes this module, while a temporary scoped run finds formatting/complexity debt in the draft and its tests. Strict source-pinned CI and module-inclusive Detekt must run on the final source. This draft has no Drive/upload or installer call site and is **not** the shared iOS/Android plaintext contract or backup-completion gate. Cross-platform format review, atomic restore, original-key signing/export proof and replacement-device qualification remain required; passkey recovery stays disabled.

## Legacy Google backup EVM-root preservation — 2026-09-23

- The legacy backup importer now uses the original backed-up EVM private key when a Substrate mnemonic is also present. Both roots enter one durable wallet creation; malformed keys fail before that mutation and import `Result` failures no longer advance the success state. Matching mnemonic-derived keys retain their original entropy/export metadata.
- [The wallet-material inventory](portable-wallet-material-inventory.md) records the still-missing TON, V2 chain-key, multi-wallet and original-key verification work. The public backup service remains an unavailable compatibility stub, and portable passkey recovery remains disabled.

## Legacy Drive backup readback verification — 2026-09-23

- The disabled single-file passkey path now refuses to report save success until it downloads the same storage key, checks every record field and the exact encrypted bytes, then authenticates the downloaded envelope with the local recoverable key. Both registration paths compensate by revoking only the newly registered credential if the readback is missing, changed or undecryptable; the coordinator also fails closed. Existing single-file saves still refuse overwrites.
- Validation: 215/215 backup-module JVM tests pass with zero failures/errors/skips, including missing/mismatched readback and decryption/compensation cases; `detektAll` passes against clean pinned Utils source. This does not qualify immutable-generation owner-head promotion or portable passkey recovery. Recovery remains disabled.

## Read-only Drive generation reconciliation — 2026-09-23

- The disabled candidate now reconciles an admitted generation under independently supplied owner, account and wallet identities, downloads the exact journaled Drive ID and FPBKGEN1 bytes, requires a no-default local wallet verifier, and rechecks the durable journal and selected Google subject before returning local evidence. A 404 or failed verification cannot authorize another POST or mark backup complete.
- Validation: 205/205 backup-module JVM tests and `detektAll` pass. The verifier is a test-only fixture; native PRF unwrap/decrypt/sign/export, owner/grant/head integration and real replacement-device proof remain release gates. No recovery flag is enabled.

## Durable local generation journal candidate — 2026-09-23

- The disabled candidate now persists exact FPBKGEN1 ciphertext, context, digest and preallocated Drive ID in app-private backup-excluded storage. A separate immutable create-attempt marker is synchronized before admission, is required by the public create API, prevents repeated admission across restarts, and retains malformed/partial writes for fail-closed reconciliation.
- Strict reload checks enforce canonical bounded records, independent owner/account scope, private file permissions, no symlinks/hardlinks, a 64-entry cap and cross-process locking. Reusing a Drive ID or generation under another operation is rejected.
- Validation: 200/200 backup-module JVM tests, `detektAll`, test APK build and 2/2 native API 36 emulator cases pass. Pinned Utils and nv-WebSocket source identities remain unchanged. Physical-device durability and recovery/coordinator acceptance remain open; see `docs/passkey-generation-journal.md`. No owner head is promoted, no generation is deleted and no recovery flag is enabled.

## Immutable Drive generation primitive — 2026-09-23

- The disabled FPBKGEN1 candidate preserves original FPBKAEAD metadata/ciphertext and FPBKWRP1 wrappers, binds owner/namespace/parent/epoch/storage subject, and uses canonical bounded bytes. Its independent Node vector is 785 bytes, SHA-256 `1c92b544dc25c687c202317d0e5747b5690a1056cf72e61d1dfab84c07c057a4`.
- A separate subject-bound Drive store preallocates appData file IDs, creates one-shot immutable candidates, and downloads exact ID/digest/context. It has no PATCH/delete/automatic retry or head update. Lost/409/malformed create outcomes require reconciliation; an upload acknowledgment or parsed download is not decryption proof.
- All 181 backup-module JVM tests pass with zero failures/errors/skips, including 17 generation tests and a full 256 KiB legacy envelope with 32 wrappers under the separate 512 KiB generation bound. `detektAll` passes under strict offline dependency verification; all 28 captured module source/resource hashes remained unchanged during validation.
- Recovery stays disabled. Native decryption/identity acceptance, owner-head/grant HTTP integration and transactional lifecycle coordination, real Google/provider and replacement-device qualification remain incomplete. See `docs/passkey-generation-v1.md`.

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
- A 2026-09-24 read-only source inventory for Polkadot Asset Hub → Moonbeam USDt records the bundled registry, frozen gap/approval manifests, and blocked evidence file digests. The bundled source advertises an Asset Hub USDt route and a possible Moonbeam `xcusdt` wallet asset, but no execution spec. Canonical on-chain asset mapping, runtime call/location/fee review, and funded two-chain evidence remain open. The inspector script and its test were removed on 2026-10-24 together with the route, because Moonbeam is no longer registered on Polkadot.
- XCM quote preparation defers wallet-secret access until a permitted transfer requests a key. Origin-fee queries use the existing SCALE encoders with an invalid placeholder signature of the correct variant and width, preserving nonce, era, zero tip and call data while avoiding wallet signing. Runtime-specific fee parity still requires live qualification.
- Validation: all 114 XCM module JVM tests and 5 focused wallet debug tests pass with strict dependency/repository policy; the 2 wallet release tests did not run at that time (no task executed them; CI runs them since 24 October 2026). Release tests verify all 15 reviewed routes remain discoverable with fresh compatible chain data while direct submission is blocked. Effective-registry adversarial checks pass 135 scenarios; production-evidence checks pass 117 scenarios; native-page verifier fixtures pass 1 valid and 20 negative scenarios. Focused Gradle and final audit logs are retained under `build/android-production-phase1-*.log`.
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
  Play production is version code 229 and internal builds used 230; version
  code 231 is reserved for the migration-safe 4.2.0 testing candidate.
- Release signing has no debug-key fallback. The guarded workflow deliberately
  produces an unsigned intermediate without any keystore input, then the
  standalone signer fails if a signing input is absent, the certificate is
  invalid, or its SHA-256 is not the registered Google Play upload certificate
  `40:39:10:92:F5:B9:7E:78:2C:65:28:CC:57:1A:DF:5D:BE:DF:E2:D0:50:23:BA:BC:7C:4E:33:9E:58:4A:4A:9A`.
- The release source and every merged release manifest are gated against
  `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and broad legacy external-storage
  permissions. The legacy open-test artifacts (version codes 221 and 109)
  request broad media access; Play must be given the clean version-code-231
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
  version-code-231 source must merge and be tagged as
  `v4.2.0+android.231`. An authorized Play operator must then review Publishing
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

The workflow handoff name and verifier currently pin `4.2.0-ias` / `231`.
Changing either source-controlled version requires all IAS hardcodes to change
in the same reviewed commit; an older artifact must never be relabeled.

## Migration-Safe 4.2.0 Candidate

- `versionName=4.2.0` / `versionCode=231` is the migration-safe testing
  candidate (230 was used by internal builds). Its initial destination is the
  existing `beta`/Open Testing track; Production promotion is a separate
  reviewed decision.
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
- TON data path (superseded 2026-10-11 by AL-11, see the top of this file): `ton-indexer` is used only after its service-info proves the requested network and every answer names it; otherwise the verified registry TonAPI node answers.
- TON transfer send/fee/time flows use ton-indexer JSON-RPC (`/jsonRPC`: `sendBoc`, `estimateFee`, `getAddressInformation`) only on a verified indexer, and `sendBoc` only when its service-info says it is not read-only (AL-11).
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

- Branding device v2 exposed AAPT STRING_TOO_LARGE in the 48,552-character line-badge path despite the passing JVM/build checks. The generator now converts that single compound polygon to exact Decimal relative coordinates, preserves all 136 closed contours and its even-odd fill, and enforces the 32,767-byte limit. An independent token parser verifies every vertex; device rerender remains required. Adaptive icons use the base anydpi directory (minimum API26), with API33 monochrome overrides. Earlier failing APKs and logs are retained.

- Corrected branding candidate: build v3 passes1,441 JVM tests/10 recorded skips, Debug app/test assembly and configured Detekt. Native API34 resource/Coil tests pass2/2 with12 raw renders; actual original-PIN unlock, both-wallet list, Welcome and About navigation succeed. Pixel-rounded layout v4 passes the existing320dp normal/double-text method with20 captures; its diagnostic47.999985dp failure is retained. All2 wallet rows,4 native chain accounts and7 protected entries remain exact. Source review validates all136 polygon contours and31,139-byte maximum path; final source/evidence manifest and integration acceptance are pending. Shared deterministic wallet-avatar rebranding, general remote-art fallback, broader native design and signed/physical release gates remain open.


## Recovery checkpoint qualification — 2026-10-14

The frozen production checkpoint `669beb8bd` passes2,101 module tests/zero failures/skips, Debug app/test APK builds, lint0errors/98 inherited warnings and configured Detekt (changed modules excluded). Native storage passes six methods plus one intentional process-kill phase: unchanged historical v1 survives in-place upgrade; anchored pending and acknowledged records survive distinct reader processes; archive acknowledgement survives restart with exact original bytes/inclusion. This uses fake public bytes/local callbacks, not a real transaction or physical power loss. Corrected UI test v2 passes two methods,96 layouts/192 PNGs (eight locales × normal/double text × six states) and three positive/negative clipping controls. The first UI attempt falsely flagged a complete centered button because paragraph coordinates were compared against its smaller measured node; its raw failure remains preserved. Only the test geometry was corrected. Root inspected Russian finalized, Japanese pending and Portuguese older-search captures at double text. All2wallets/4accounts/7protected records remain exact with SQLite quick-check ok; network settings restored. Source review v3 `2011d3888d2acaa0b399028b5dc2234769354a9c615c3d663c1a43dfb63158a9` resolves the five caller races at unit scope. Direct recovery entry after restart remains release-blocking and is next; these bounded results do not establish complete recovery or audit readiness.


## Direct pool recovery — in qualification, 2026-10-14

A separate read-only recovery entry is now available in the pool staking view before network/membership content. It prepares only local public wallet/account/asset context and does not wait for balance eligibility, a fresh amount, fees, validators or the pool list. Its dedicated dialog reads the exact retained scope without creating/claiming/consuming a join draft. Empty local state is distinct from finalized inclusion; missing restored context and corrupt/legacy records remain explicit. Recent/older checks reuse the bounded reconciler; archive completion survives cancellation, and explicit Return dismisses back to staking. No operation-success route or signing action exists here.

Unit-v6 passes303 staking tests/zero failures/skips/source drift, including17 direct VM cases and33 actual main-VM preparation cases. Earlier v1/v2/v3/v5 runs preserve missing coroutine/navigation imports, asset/reset/void/BigDecimal fixture diagnostics; they are not product regressions. A separate valid departure baseline executes26 tests with25 passes/one genuine failure: held local context opens recovery after the wallet picker, despite no wallet change. Independent source review `17face6a7634788bfef425f7593d5eaa61185c017aa965c2854d7da31471dadc` identified missing visible-screen authority. Recovery now owns a cancellable job/generation, revokes on pause and synchronously before opening the wallet picker, and accepts only its exact staking back-stack entry and view captured before the click callback. It rechecks before/after publication and error display. Fresh taps after resume remain usable. Broader module/app checks, installed navigation/layout qualification and independent final acceptance remain pending; no full recovery or release approval.


## Direct recovery build and presentation fixture — 2026-10-14

Production source `f0b0910a7` passes2,281 tests across eight modules (app145/common620/core92/account568/wallet427/staking303/Polkamarkt9/XCM117), zero failures/skips, Debug app/test assembly, lint0errors/98 inherited warnings and configured Detekt. All4,300 app/376 library/84 websocket source pins are stable;374 of1,705 Gradle tasks executed and1,331 were up-to-date. Configured Detekt excludes the changed main modules, so no changed-module Detekt coverage is claimed. Actual production graph/router native tests pass two methods with test destinations. Presentation v1 preserves one passing clipping-control method and two failing methods: a copied minimum-ten-text-node assertion wrongly rejects the smaller loading and entry screens. The test now requires each exact expected heading/message/public identifier (or entry label), retaining measured glyph bounds, ellipsis, exact actions and minimum targets. Corrected presentation v2 remains running; actual Fragment/lifecycle and overall release gates remain open.


## Installed pending recovery system Back baseline — 2026-10-14

Installed empty recovery and Return survive offline zero-balance entry and system font configuration1→2; protected identities/records remain exact. An opt-in guarded known-public-wallet fixture retains impossible0x01020304 bytes in the actual pending scope, reads exactly in a new process and removes only that exact never-submitted attempt afterward; all three native fixture methods pass. Actual original-PIN→Earn→Staking→recovery shows exact37DOT/pool8/account/network/hash after restart. Both idle pending and checking ignore Android system Back; the visible toolbar Back works and reopening retains the exact original. This genuine installed baseline is preserved in `android-pool-direct-recovery-system-back-baseline-v1.json`; no final acceptance. The fixture test initial build failed on hidden Settings.Global.MOBILE_DATA; the corrected literal uses the actual global setting and test build v2 passes with no source drift. All2wallets/4accounts/7protected records remain exact with SQLite ok, original network/font settings restored. Secure-window screenshots are black; only native hierarchy/actions are claimed. A narrow recovery-only hideable-behavior remedy is next; broader inherited sheets/configuration remain open.


## Recovery system Back remedy — in qualification, 2026-10-14

The native Material-dialog regression now reproduces the installed problem: baseline v2 runs one method with one genuine `OS Back must cancel recovery; recreated=false` failure, using the actual inherited recovery behavior. Its initial v1 fixture checked draggable state before the asynchronous onShow policy callback; that diagnostic is preserved and not counted as a product failure. The corrected test waits for the actual callback and sends a real OS Back key, covering initial and recreated hosts. Standalone recovery now permits Material hiding/cancellation while retaining the inherited expanded, non-draggable policy. Existing onCancel/onDismiss still revoke review ownership; no shared base behavior changes. The public pending fixture accepts a validated evidence label so baseline and fixed native records remain separate. Full app/module and installed idle/checking Back checks are pending.


## Shared confirmation attempt and completion ownership — 14 October 2026

BaseConfirmViewModel now admits one attempt synchronously, releases loading and that exact attempt in finally, and permanently consumes an observed successful result before any navigation callback. It rechecks cancellation after awaited validation/output, propagates cancellation returned inside Result, and contains ordinary preparation/navigation exceptions without recursively invoking a failing error presenter. Fatal errors are not caught. This applies to the seven pool screens using StakingConfirmViewModel.

The actual-controller regression baselines preserve five ownership/loading failures (8 cases), two Result-valued cancellation alert failures (13 cases), and three uncaught preparation/navigation failures (16 cases), each with healthy controls. Final affected wallet module checks pass443 cases/zero failures/errors/skips and no source drift, including16 new cases. Tests use public local callbacks, actual ViewModelStore clearing and callbacks that ignore cancellation; no keys or RPC. Evidence: `build/safe-upgrade/evidence/codex-20261012/android-base-confirm-ownership-unit-v3-*` in the parent workspace. Full build/static checks and final independent acceptance remain pending.

This is per-ViewModel admission and observed-success protection. It does not yet bind those seven callers to an exact wallet/chain/form/lifetime, persist an unknown output outcome, prevent later manual retry after ambiguity, or qualify native dialog lifecycle. Production readiness and publication remain open.

The first broader build at b559f9144 stopped at one existing recovery fixture: `replaced draft during reconciliation is preserved` read the archive after waiting for submission ownership to be released. Draft replacement releases that claim before the reconciliation job completes, so the assertion raced durable archiving. The raw failed build/XML remain under `android-base-confirm-ownership-full-build-v1-*`; no full-build pass is claimed. The fixture now captures and joins the actual held check job before asserting exact archived bytes, replacement preservation and no stale navigation. Production recovery is unchanged; corrected full qualification follows.

Final shared-confirmation candidate `cb08571a7` passes2,319 exact named unit cases/zero failures or skips, Debug app/test APK assembly, lint0errors/98 inherited warnings and configured Detekt (affected modules remain excluded). Source comparison has no drift:4,305app/376utils/84websocket pins. Candidate `android-base-confirm-ownership-candidate-v1.json` SHA-256 `e3c3119926dbc365a51466599b4247079a119804e805650dcdf77247e271c88d` binds976 evidence files; source review v3 `f15b65b9c3f0c2f62a1d53a07b9ac8c740d750f15dc8c1ad3dcf6d6263d43e57` accepts the boundary fix and actual-job fixture correction. Final evidence review/integration remain pending. Default minimum-balance validation and three pool overrides can still transform a returned cancellation into an unknown-minimum error; this is an explicit follow-up, separate from the tested isValid boundary. No native or whole-release acceptance is claimed.

Shared confirmation ownership is independently accepted: final review `97406b4bf0f0671941826f1b21fb3f704fee76c9081df1c53c277c72eaf87073` verifies all2,319 named cases,16focused methods,10distinct reproduced defects,976 evidence pins and final artifacts. Integrated production `d562416ff` plus corrected fixture `a3998148c` match all3 changed non-document files and the complete qualified source tree. Integration verification `7d7b4a8ed073dba0a2db56d7773820d537b3a463f9aa9e6af3923833419c92bf` rehashes4,305app/376utils/84transport pins; three clean cherry-picks preserved accepted documentation. Default/upstream minimum lookup cancellation, exact caller wallet/chain/lifecycle authorization and durable ambiguous outcomes remain open; no broad native or release claim.

## Preserve minimum-balance lookup cancellation — 15 October 2026

The existential-deposit use case propagates direct coroutine cancellation before wrapping lookup failures. The default confirmation validator and actual claim/redeem/unbond/pool-setup validators also propagate cancellation returned inside Result before converting ordinary failures into an unknown-minimum warning. Unknown minimums still block these pool paths with their existing explanation, and known-zero minimums still permit healthy progression; wallet opening is unaffected.

Corrected baseline-v4 runs30 named cases with7 genuine failures/23 healthy controls: two actual use-case runtime/RPC paths and five actual validators. Final wallet/staking modules pass785 cases (448wallet/337staking), zero failures/skips/source drift, including17 new methods/executions. Baseline-v1 compile diagnostics are excluded. Baseline-v2 has three genuine wallet failures and three missing Mockito icon-URL fixture failures; baseline-v3 corrects that fixture and reproduces six defects before setup is added. All raw runs are retained under `build/safe-upgrade/evidence/codex-20261012/android-minimum-cancellation-*` in the parent workspace. Checks use local public fixtures and mocked RPC; no signing or broadcast.

This is direct cancellation propagation through the named service/converters, not a claim about every minimum-balance consumer or nested exception causes. Broader full build, independent acceptance and integration are pending; exact wallet/chain/form/lifecycle authority, uncertain output recovery and release gates remain open.

Final minimum-cancellation source `29b37660e` passes2,336 exact named unit cases/zero failures or skips, Debug app/test APK builds, lint0errors98 inherited warnings and configured Detekt (affected modules excluded). All4,307app/376utils/84transport source pins remain unchanged. Candidate `android-minimum-cancellation-candidate-v1.json` SHA-256 `44af71e0edbd70d77bf7d5f10293b5779995da1596ecc42b8ab9e22eccb2d26f` binds13 changed files/634 evidence files,30 focused names and all7 corrected-baseline defects now passing. Source review `eb43f3b8d43e82c2ba942c392db96768342233d7b2d7ca3a82847f1f2f526b72` accepts direct cancellation propagation at this scope. Independent final review/integration remain pending; no broader consumer/native/release acceptance.


Minimum cancellation is independently accepted and integrated from qualified29b37660e at5a87ec60a. Final review `f1e402bfa88a92b49d824a1b193c945dedea2b08148f8a941a6c29a1f4a92ca6` verifies2,336 named unit cases/zero failures or skips,30 focused cases,7 true baseline failures now passing,634 evidence pins, Debug APK pair and lint0errors98 inherited warnings. Integration verification `5ebfb142404986e4b38d0a5a13a302e0b736e452b84f86adf3e1370e21fa7ffd` confirms11 changed non-document files and whole non-document tree equal to the qualified source, with4,307app/376utils/84transport current and Git pins exact. Two documentation-only conflicts retain earlier acceptance sections. Configured Detekt excludes affected modules; no native, all-consumer, nested-cause or release-readiness claim. Pool creation draft/context/lifecycle authority is the next actual-controller follow-up.


## Bind pool creation to its original review — 15 October 2026

Create confirmation now captures immutable fields from a draft bound to its original main-context snapshot and wallet-selection epoch. Display, fee and operation callbacks use those original fields. Shared publication claims prevent two confirmation instances from executing the same draft simultaneously, and observed success consumes only the original claim even after departure. Back/dismissal/clearing revoke presentation; stale success/error callbacks cannot navigate or alert over another context. Claim observers are rechecked after synchronous publication, and conditional wallet admission skips stale presentation without swallowing admitted exceptions. These are caller boundaries; downstream key/sign/send authorization is still separate.

Create setup conditionally publishes only its current editable draft under the original context. The welcome action carries that context and rechecks it after the limit lookup. Baseline-v3 executes21 cases with16 genuine failures/5 healthy controls. A reentrant baseline reproduces2 additional failures, and the origin baseline reproduces3 further failures while retaining those2; all21 distinct reproduced cases now pass. Expanded unit-v4 passes1,447 cases/zero failures or skips (621common/448wallet/378staking), including46 focused cases and42 new test executions. Source snapshots have no drift; full Debug/lint/configured Detekt and final independent evidence review are pending.

All original failures are preserved under `build/safe-upgrade/evidence/codex-20261012/android-create-pool-context-*` in the parent workspace. Unit-v1 has two test-only matcher failures after reaching previously failing assertions (the router has a fourth default argument). Unit-v3 exposes queued fixture failures: previous minimum-validator mocks passed null chain/asset IDs to a non-null assetFlow stub, and background fixture flows could outlive Main reset. Explicit fixture IDs plus cancellation-and-join before reset correct these without relaxing assertions. The prior minimum gate's2,336 named result is historical; this qualification supersedes its affected fixtures and records their uncaught-flow weakness.

The confirmation unit scope does not establish full create setup/welcome lifecycle: late setup errors and non-atomic welcome publication remain explicit follow-ups. Underlying create API current-chain/wallet resolution, physical execution revocation, durable ambiguous outputs and known-success loss inside downstream dispatch remain open, as do actual Fragment system Back/configuration and global historical/physical/store/release gates. Tests use local public fixtures with mocked callbacks; no keys, signing or broadcast.


The real success-navigation sequence adds two reproduced failures: returnToMain can synchronously clear or dismiss the confirmation ViewModel, preventing the owned continuation to validator selection/success. A once-only synchronous success presentation scope now retains its exact completion receipt across that owning pop; every subsequent step still checks main/draft/wallet context. Five additional tests cover actual ViewModelStore clearing, dismissal, reentrant confirmation, non-escaping/once-only scope and wallet change during pop. All31 confirmation cases pass in unit-v5; the broader1,452-case run has one retained recovery-fixture failure because joining its check job does not join the separate combine/stateIn presentation collector. Archive and consumed-draft assertions pass. Unit-v6 waits for the finalized view-state emission before retaining the original assertion; recovery production is unchanged. Full candidate acceptance remains pending, with all failed XML retained.

Corrected unit-v6 passes all1,452 named module cases (621common/448wallet/383staking), zero failures/skips/source drift, including51 focused/47 new create-context executions. All23 distinct reproduced create-context/navigation failures now pass. The recovery archive fixture synchronization preserves its original assertions. Final full build and independent acceptance remain pending.

Final source `0c9e64970` passes2,383 named unit cases/zero failures or skips, Debug app/test APK assembly, lint0errors98 inherited warnings and configured Detekt (affected modules excluded). All4,310app/376utils/84transport source pins are unchanged. Candidate `android-create-pool-context-candidate-v2.json` SHA-256 `7c72b2eff3deaf2d47d64acffbaa8b8bb1aa229781a80e5ff1e5a6bf0f9ad012` binds17 changed files/1,765 evidence files,51 focused cases/47 new executions and23 distinct reproduced defects now passing. Source review-v4 `492b64fbad2cc1b0f4b55ed3aa9e827d630d9f3ab5d40f4cd9c98beb303cead9` verifies all15 changed non-document Git blobs and corrected1,452-case module results. Earlier full-v1 is an intermediate result before owned-success-pop correction; unit-v5 retains its separate recovery view-state synchronization failure. Independent final evidence acceptance and integration remain pending.


Create confirmation context and owning success navigation are independently accepted and integrated at source `790adc21d` + `8fc4ea316` from qualified `0c9e64970`. Final review `ece6b3a25ae4efe313714714dd14f70de5552603704b72e433c0bb80611ce5ed` verifies2,383 exact named unit passes/zero skips,51 focused cases and all23 distinct reproduced failures now passing. Integration verification `b4b871fb17a7033339a27d4655b51cef4846ed4c88c08110cf5e2ea61c78145d` confirms15 changed non-document files, entire non-document tree equality and4,310app/376utils/84transport current+Git pins, with1,765 evidence pins unchanged. Two documentation-only conflicts retain earlier acceptance sections. Full setup/welcome lifecycle, exact downstream execution/durable outcomes, actual Fragment lifecycle and all broader release gates remain open. The next setup/welcome baseline is running; no new native claim.


## Preserve create setup and welcome lifetime — 15 October 2026

The actual-controller baseline executes24 cases with7 genuine failures and17 healthy controls: retained setup and held validation survive Back; a late insufficient-balance error reaches a replaced context; validation publishes an old amount with a newer name; an ordinary minimum lookup exception escapes; welcome continues after Back or after a reentrant draft replacement. Raw baseline and exact source snapshot remain under `android-create-setup-lifecycle-baseline-v1-*` in the parent workspace evidence directory.

The candidate shares local screen lifetime with the pool publication lock and wallet epoch, captures original form fields plus an edit generation before validation, contains ordinary lookup failures only for the current request, and conditionally publishes welcome setup against the exact original draft receipt. Back, dismissal and clearing revoke pending presentation; configuration-change dismissal retains the existing ViewModel authority. Ten additional controls cover dismissal/clearing, idempotent Back, form A→B→A, late errors, replacement while the limit is held and ordinary limit failure. Qualification and independent review are pending. This does not establish downstream key/sign/send authority, durable output recovery, installed Fragment behavior or all eager initialization/fee-readiness paths.

Independent source review identified two additional lifetime/order gaps. Expanded selector/welcome baseline-v1 runs41 cases with7 genuine failures/34 healthy controls: both role selectors accept older resolutions, current and obsolete selection exceptions escape, revoked welcome still opens Join/Instructions, and reentrant join-draft replacement still navigates. The correction gives each selector its own pending request token, blocks confirmation until resolutions finish, and guards all welcome routes with exact publication receipts. Two additional controls retain both concurrent selector choices and verify pending-address gating. Corrected unit-v2 passes43 cases/zero failures or skips/source drift; all14 distinct reproduced failures now pass. Full build and independent final review are pending. Eager initialization, fee readiness, selector-origin routing, downstream execution and actual native lifecycle remain explicit limits.

Intermediate source4a1d05c69 passes2,409 full-build named unit cases/zero failures or skips, Debug APK pair and checks with no source drift. Final UI feedback now disables Create while either selector is pending and starts the button disabled until its inputs load. The existing actual ViewModel unresolved-selection control observes enabled→disabled→enabled emissions alongside its unchanged old-address rejection and final exact-address assertions. All43 focused unit-v3 methods pass without failures/skips/drift. Final full-source qualification and independent evidence acceptance follow; shared button rendering is unchanged.


Final setup/welcome source `f1c2d2553` is independently accepted at this bounded scope. Full qualification passes2,409 named unit cases/zero failures or skips,43 focused cases/26 new executions, Debug app/test APKs, lint0errors98 inherited warnings and configured Detekt (affected modules excluded). All14 distinct reproduced baseline failures now pass;4,311app/376utils/84transport current/Git source pins and780 evidence pins match. Candidate SHA `84fdbc89727e658f86e7b336c45eb00d8bbe4ed75d7fc242c97f2bef67bb7481`; independent final review SHA `98299ecd45003f90a848bedb670c33c106d07bf7a195b6ded5352f8e1d167771`. Pending address selection visibly disables Create in actual ViewModel state; no new installed native claim. Downstream exact chain/wallet/key/sign/output and durable unknown outcomes, eager fee/initialization, selector-origin routing, Fragment lifecycle and global migration/physical/store/release gates remain open.


Setup/welcome/selector ownership is integrated at source `285e5c1b2` + `9f2d1b255`. Verification SHA `2e0ddc40df04a821906d163b4c814163b06ff4d35309c47652e5b15294a86ec8` confirms all8 changed non-document files, whole non-document tree equality to f1c2d2553,4,311app/376utils/84transport current+Git pins and780 evidence pins. Two documentation-only conflicts preserved prior create-confirmation acceptance. Independent final review SHA `98299ecd45003f90a848bedb670c33c106d07bf7a195b6ded5352f8e1d167771` accepts the bounded2,409-case qualification. Downstream execution/durability and broader readiness gates remain open.


## Carry create-pool review through physical execution — 15 October 2026

The actual caller/API/SCALE baseline reproduces10 failures in13 cases: mutable global chain, unbound fee/signing wallet namespace and stale output after departure/context changes during preparation or queued transport. Three healthy controls retain atomic create/set_metadata call order, exact addresses and public-only fees. Baseline-v1 stopped at a test import error and is excluded. The correction passes original chain/wallet through confirmation, setup and quick-input fees to the API, then supplies the active review/draft claim/job/wallet epoch guard to physical key access, signing and final output. Admission is once-only and releases navigation/selection locks before blocking output.

Focused unit-v2 passes75 named cases/zero failures or skips/source drift, including19 actual pipeline controls,31existing create-context and25setup cases. Added controls check legacy state_toggler encoding, original fee chain, ViewModel clearing, changed intent, actual local writer Back/wallet responsiveness and duplicate refusal. Production and test source are being frozen for full qualification and independent review. Fixed public fixture keys with mocked RPC/local OutputStream only; no live transaction. Durable ambiguity, matching acknowledgement and acknowledgement loss inside downstream dispatch remain explicit separate work, alongside other pool callers and global gates.


Physical create authority is independently accepted at source8e1572b67:2,428 named unit cases/zero failures or skips,75 focused/19 new,all10 genuine baseline regressions now passing, Debug APK pair, lint0errors98 inherited warnings and configured Detekt (affected modules excluded). Candidate SHAfde1bb546c77ee13f0f3e6391c6d31c5ba05a92e0ec15d051237e09ad3ec0185; final review SHAc0d8d15ed71443749e81d6f364225347ae09c8731c9f091ee6fadf2a5c18ebd1 verifies4,312app/376utils/84transport Git/current source and433 evidence pins. Baseline-v1 never executed tests and its stale copied XML is excluded. Only local fixed public keys and mocked RPC/actual local writer were used; no new installed-device claim. Durable ambiguous output, matching acknowledgement/dispatch-loss and other caller/release gates remain open.


Create chain/wallet and physical key/sign/output authority is integrated at8bbd33add. Verification SHA1eb9aa1857913039ad750f64dc4082d28be73547cdeb7e3ed4c4c7c189d95b35 confirms all8 changed non-document files, entire non-document tree equality to8e1572b67,4,312app/376utils/84transport current+Git pins and433 evidence pins. Two documentation-only conflicts preserved accepted setup lifetime sections. The bounded2,428-case result is accepted; durable unknown output and acknowledgement gates remain active.


2026-10-15: independently accepted bounded durable pool creation recovery from qualified source 464582acaf7aeb1220387c5c402a704dd69fa3b8. Version3 retains exact original create name/roles/bytes/hash/intent/anchor alongside shared join/create scope; old v1/v2 records remain readable and unknown records preserved. Matching acknowledgement synchronously consumes only the original claim and opens read-only recovery without claiming successful creation. Final checks:2,460 named unit cases/zero skips, Debug app/test APK, lint zero errors98 inherited warnings, configured Detekt (affected modules excluded), installed v2 upgrade/read and five v3 persistence methods plus intentional admitted-output process kill, six UI/Back methods with176 layouts352 captures. Seven protected records and exact2wallet4account rows remain unchanged. Candidate SHA 94c2cd9fb72eedb3c0f513a78970b766deac872d88bb4d4c9f2242f41e6a35cf; independent review SHA 7c644e3ae4036f2118027c431017158f2fd644f5632a8e02ec07318575d2b264. Whole non-document tree verification follows this integration. Actual end-to-end create navigation, other pool callers, RPC trust/proof, physical/migration/store/global design and release gates remain open; no production-readiness or publication claim.


2026-10-15: independently accepted bounded member-confirmation lifetime correction fromb8fa36cce6549d83d9ebb8f8650905a59926a7c2. Frozen original main/manage/wallet context, shared claims, exact consume with readable unclaimable fields, guarded late callbacks/synchronous success-pop, and four Fragment dismissal/Back policies. Final full build passes2,549 named unit cases/zero failures or skips,98 focused/89new, app/test APKs, lint0errors98inherited warnings and configured Detekt (affected modules excluded). Installed native tests pass six actual Material OS Back methods (four new member) on initial/recreated host. Seven protected records and exact2wallet4account rows unchanged; settings restored/app stopped. CandidateSHA6fd2c17772dbbfde79f3d6e7268645d9efd4653f6ac2db2cbb272753aa5fa4d3; independent reviewSHA3e98496f839facb0129134aa988ce6784940ef9c79aef6247ec613099dd10c1c. Complete non-document tree verification follows. Upstream preparation, downstream chain/wallet/physical/durability, actual full Fragment navigation, migration/physical/store/design and release gates remain open.


2026-10-15: independently accepted bounded member amount/preparation correction from91f8f64d0b7420044836c2a0ca5d2190a77cfc2d. Original main/draft/wallet authority, exact conditional edits, separate read/edit permission, scoped Back/minimum errors/alert results and numeric input revocation protect claim/redeem/unbond/bond-more. Full build passes2,647 named cases/zero failures or skips,187focused/98new, all53distinct genuine baseline failures resolved, app/test APKs, lint0errors98inherited warnings and configured Detekt (affected modules excluded). Installed native tests pass10Material OS Back methods/four new amount policies on initial/recreated host. Seven protected records and exact2wallet4account rows unchanged; settings restored/app stopped. CandidateSHA20a0f338d9ba4cad7090745e23fd202b66d88d89f87ed8e4268f93d75d2fee5d; independent reviewSHA5d1e57ef407a4e95ca3ffea8e2a6b256cd8babd488cfecca5fa34dc0c493601a. Whole non-document verification follows. Complete Fragment navigation, downstream physical/durable execution, manage/validator/edit actions, generic fee readiness and quick-input faults, migration/physical/store/design and global release gates remain open.


2026-10-15: independently accepted management action and live-value correction from a36c8aa8911fc565b3e738469fce5ef2063e314d. Original main/wallet/lifetime and exact pool own one action, destination publication, navigation and errors. Pause withdraws pending work and stops hidden draft publication; actual return allows a new action. Pool value equality/hash preserve same-ID amounts, roles and metadata. Full build passes 2,798 named cases with zero failures/errors/skips; 338 focused /151 new and all 80 distinct genuine baseline failures resolved. Three queued observer fixture failures remain diagnostic; explicit inline rerun established the genuine cache failure. Debug APK pair, lint zero errors/98 inherited warnings, configured Detekt (affected modules excluded), and eleven installed Material Back methods on initial/recreated host pass. Seven protected records and exact two-wallet/four-account rows unchanged; settings restored/app stopped. Candidate SHA 6d37b9869634b9b4289b9a88f583da71bb9f0820b1a98b4e07d208024530e442; independent review SHA 606ee7b8df3062b5eac1937540cdce8dad90c16b00770e2fc1f33e9bdc698ea7. Complete Fragment/back-stack lifecycle, eager source errors/cancellation, other pool controllers/downstream physical/durable execution, migration/store/design and global readiness remain open.


2026-10-18: independently accepted pool-source lifetime and body context correction from 0c3066eb05dbf1890db4d5b8a31f4d4b6a135c77. Chain/wallet changes cancel old observations and emit unavailable state; missing account or source failure permits later healthy selection. Pool body requires matching chain/public account/supported asset, cancels old quote work, and keeps healthy nonmember input available without a failed optional estimate. Reward dialog retains its previous supported-asset admission filter. Full build passes 2,829 named cases with zero failures/errors/skips; 369 focused /31 new and all24 distinct genuine baseline failures resolved. Twelve Mockito setup failures remain diagnostic. Debug APK pair, lint zero errors/98 inherited warnings, configured Detekt (affected modules excluded), and eleven inherited installed Material Back methods on initial/recreated host pass. Seven protected records and exact two-wallet/four-account rows unchanged; settings restored/app stopped. Candidate SHA 1ef6e35e9538763a1be892d9f0323805c07390dfb534ce279838445cceb40898; independent review SHA d45c9f282fc1a7c8c00b89dd98627fb42d184e4135892ec729abc00b006cec44. Lower observation semantics including missing bonded-pool data, root screen generation/complete Fragment lifetime, network-info/dialog ownership, other controllers/downstream durability and global release gates remain open.


2026-10-18: independently accepted lower pool-observation and acknowledged-storage correction from 0c3791b3358c40dddcacc4a09bdbe26634cd1eb2. Explicit unavailable state withdraws stale membership, body and management actions; confirmed nonmembership requires acknowledged null. Latest observation cancels obsolete metadata/reward/era work and later healthy values recover. Single-key status-bearing RPC queries validate raw block/key/tuple/value before decoding and preserve cancellation; unrelated legacy APIs remain unchanged. Final full build has3,058 named cases:3,048pass/10 inherited runtime skips/zero failures or errors. All428focused and54new methods pass;15 genuine baseline failures resolved, fixture diagnostics preserved. Debug APK pair, lint0errors/100warnings (net two additional availability notices on unchanged dependency catalog; no code-warning delta) and configured Detekt pass (affected modules excluded). Eleven inherited installed Material Back methods pass on initial/recreated host;7 protected records and2wallet4account rows remain exact, settings restored/app stopped. Candidate SHA 16f75d5b9e88b604d1cc580e6204601aa5e56d083f1c948ff11a6d5205979ac5; independent final review SHA c36c483318e1c4708eed44eedddd8e2c54591474b1674cfc6e7bb8dae93c7fe0. Cross-storage snapshot/freshness/provider trust, terminal-source retry/disconnect, full Fragment/root generation/network-info/reward-dialog and downstream signing/output/durable authority remain open, along with global migration/physical/signed/store/design gates.


2026-10-18: independently accepted pool network-information lifetime correction from c65249351ee318162333631ebcfbdd74e7cd8365. Old values reset on each asset; obsolete lookups cancel, unsupported/failed lookup stays unavailable and later healthy observation recovers. Full3,066named=3,056pass/10 inherited runtime skips,35focused/zero skips; six genuine failures fixed, original eight methods exact. Debug APK pair, lint0errors/100warnings exact to previous issue multiplicity, configured Detekt pass with staking excluded. No new native execution or device changes. Candidate db236d28c5360828abd7e793b5434687e413dd9a3cd1cf81bd8da9e8ebf6e4f2; final review 24243df4a857432acde1e6538ae15bc6f88c16a6d8414bbcaf891475189f07d5. Root/Fragment/presentation/reward-dialog, terminal source retry and downstream durable authority/global release gates remain open.


2026-10-18: independently accepted root-action lifetime correction from 1c4344bda8fbce499036ddbbdeacf94f275bfcdf. Screen/wallet/selection ownership rejects obsolete rewards and pool navigation, Fragment callbacks carry original owner, and rejected departures restore fresh visible actions. Nullable shared EventObserver fixes actual installed Wallet-tab crash. Broad3,116named=3,106pass/10 inherited skips;83focused/zero skips,50new and35genuine failures now pass, final47root+3common test files exact. Debug APK pair, lint0errors/100warnings exact prior multiplicity, configured Detekt pass (affected common/staking excluded). Actual offline PIN/startup/recovery/OSBack/Wallet/Earn/background-resume survives same process;7protected/2wallet4account records exact. Screenshots black, no visual claim; offline reward/start/manage controls absent. Candidate 08d8cdd17f84d8273945f695e8470e855bd4308f78d8a94f8f9c19c6d1e1ed34; final review d3eea78114f2bb49eec3e89168e222cdb1e549cbf20426a6a9d30ca614181ca7. Replacement qualification explicitly excludes one lost reviewer-mutated query-copy SHM index; original device DB/WAL and identities reverified on scratch copies. Complete scenario/presentation/dialog ownership, downstream asynchronous validation/durable authority and global migration/physical/store/design gates remain open.


2026-10-18: independently accepted caller-owned nonpool preparation and guarded validation from 489492b5770bdaf0e0d4802ec1f7ef44605c4be2. Root screen/wallet/selection and actual ViewModel parent lifetime guard relay/parachain setup publication, including synchronous observers and retained callbacks. Current-owner cancellation clears progress then propagates. Broad3,154named=3,144pass/ten inherited runtime skips;118focused/zero skips;38new methods and14distinct genuine baseline failures now pass. Final48root/23nonpool/14common files exactly match5a959998a; six prior root mock calls explicitly adapted for the suspend API. Debug APK pair, lint0errors/100warnings exact prior issue multiplicity, configured Detekt pass with affected common/staking excluded. Installed16 hierarchy captures show original-PIN/recovery/OSBack/Wallet/Earn/background-resume continuity in PID7918 without new crash;7protected/2wallet4account records exact and settings restored. SQLite queries ran only on disposable DB/WAL clones. Sample06 black: no visual qualification. Offline reward/start/manage absent; ERROR-level retained callback controls do not establish a native warning button. Candidate 9f91a7efe1e4852ce4a0d2af5522e43e79d78c8c4ff2ae9148d5378459da9d05; final review c8b91bc9c764c9f5c5520aa0caaca2eac385fff196779cf37ebd5f07469e29cb. Fresh-tap scenario/asset/amount freshness, full presentation/dialog and downstream fee/key/sign/output ownership plus global migration/physical/store/design gates remain open.


2026-10-18: independently accepted fresh staking action binding from 9c614ea44152dc59a5b98f443a469c6bf0b74910. Root actions bind scenario type, selection revision, captured asset, current wallet/account and authoritative staking capabilities. Actual relay validation uses captured chain; parachain maximum refuses another replayed chain and bound average recomputes for the requested chain. Broad3,186named=3,176pass/ten inherited runtime skips;172focused/zero skips,32newmethods and18distinct genuine failures now pass. Final21fresh and3actual-calculator assertion bodies exact12dcb4db3; calculator comment corrected, older root/nonpool fixture API changes and one old root contract mismatch explicit. Debug APK pair, lint0errors100warnings exact prior multiplicity and configured Detekt pass (affected modules excluded). Installed original-PIN/recovery/OSBack/Wallet/Earn/background-resume regression preserves7protected/2wallet4account records and settings; details and screenshot limits in native report. Candidate bf48dd008db67f5a51935dac819e6a48c267b135c86de5521637b3f5ad6ae990; final review 2a7e05f3b94ad29e8cb1124b31c002667921233e447c11c46d9fa3b45e94e91e. Entered amount/setup-session, full presented-dialog lifetime, legacy unbound quote/error semantics, downstream authority and migration/physical/store/design gates remain open.


2026-10-18: management baseline9046cc191 executes15 methods:3healthy passes,11genuine stale publication/navigation failures and1fixture null-observer exception on intentional LiveData clearing. Only the helper observer nullability is corrected; all15 method bodies remain unchanged for baseline-v2 before production changes.


2026-10-18: Management presentation/window lifetime qualifies at292e998af (productione2b9f1f16):199focused/zero skips,3,210named=3,200passes/ten inherited runtime skips, both Debug APKs, lint0errors/100unchanged warnings and configured Detekt with affected-module exclusions. Same instrumentation APK reproduces3genuine old-app native-window failures plus2healthy controls; corrected app passesall5. Exact7protected/2wallet/4account rows, settings restoration and unchanged crash buffers confirmed. Earlier startup/PIN fixture and runner/reviewer precondition failures remain separately retained; all5native bodies/deadlines unchanged. Candidate-v2 and paired-window review are frozen; independent final review/integration pending. Synthetic rendered input qualifies window lifetime only; actual action rows, eligibility, rendered-model freshness, other dialogs and downstream authority remain open.

Final independent review accepts this bounded checkpoint; see the current section above. Remaining global production gates stay open.


## 2026-10-18 — Bounded rendered relay action authority accepted

Integrated executable source `72487016f8bb31828cfbce68ebb5f60b35854895` (production `f2c4f5e5737dfd62b8cb3a4d4a7783b96bec17c9`). Each relay body retains its immutable source; root publication validates the original wallet, account, selection and denomination before granting action authority. Management and status callbacks require the exact current published object. Resume requalifies the source and issues a fresh publication.

Independent review verifies 3,258 named JVM outcomes: 3,248 pass and ten unchanged inherited runtime skips, with all 3,236 prior outcomes exact. All 70 focused cases pass. Eleven reproduced failures comprise eight original action failures and three late-adoption defects in the first correction; six further controls are qualification only. Both Debug APK hashes, 4,346 application/376 utility/84 transport source pins and 923 evidence pins are exact. Lint has zero errors/100 warnings, byte-identical to the prior report; configured Detekt excludes the affected staking module.

The same five native management-window methods pass with final APKs. All seven protected records and two wallet/four account rows remain exact; settings are restored and crash buffers unchanged. Review queries used disposable DB/WAL copies, never frozen DBs or SHM. Forty-one JSON observations provide window-lifetime evidence; no current images or visual-readability claim. Synthetic native input does not prove actual root provenance or action-row clicks. The second Back proves window absence only, not navigation retention.

Candidate SHA-256: `595b4ed1a66eb40304583781b25c4bb67f3e33a76b6f3448aaf3d4c45276868b`. Independent final review: `5967ea25af426911b72d3b429c5deea95a14a65ef0b5b3906eaf9889f3974bae`. Continuous independent registry/selector identity, other actions/dialogs, child-scope lifetime, amount/session and downstream signing/durability, migration, physical-device, design and store gates remain open. This is not production release approval.

## Scenario owner, reward refresh and root story acceptance checkpoint

Executable source `911d70b5df4ad1542e141b2083a833146dc46b82` is independently accepted within this bounded scope. Permanent root observers remain in the ViewModel scope; every distinct observed staking selection receives a fresh cancellable scenario owner. Relay reward refresh belongs to its collected body and checks cancellation before publishing an error. Stories switch with the scenario and clear before the next source, including an empty pool source.

The original test-only baseline `730b91d00` preserves 78 named results: 73 pass and five genuine failing methods covering inactive lower-stream work, uncancelled reward work and stale errors after scenario/role replacement. These assertions overlap consequences and are not five independent vulnerabilities. Expanded baseline `bfa4adf67` preserves 87 cases: 85 pass, one genuine finite-source pool-story replay failure and one synthetic hot-source qualification failure. Actual story datasources are finite; the hot-stream probe is not counted as a production defect. The original eight method bodies, all 70 prior tests and the valid expanded 17-case fixture remain exact. Retained diagnostics include one nested Mockito fixture failure fixed without assertion changes and one missing-import compile failure before tests.

Final focused checks pass all 87 methods with zero failures, errors, skips or source drift. The nine-module broad run has 3,275 named tests: 3,265 pass and the same ten inherited RuntimeSyncServiceTest skips. All 3,258 prior outcomes are exact. Both Debug APKs build; Android Lint has zero errors and the same 100 warnings with exact issue/location multiplicity. Configured Detekt passes but excludes feature-staking-impl. Source verification covers 4,347 application, 376 utils and 84 transport files against current bytes and Git blobs. Same-type relay chain replacement, relay/pool/relay body-network-input recovery, delayed NonCancellable returns after release, current errors and cancellation-result controls pass.

The final APK pair passes the unchanged five native management-window cases on owned offline emulator 5710. Its 41 phase JSONs and zero images qualify actual startup/PIN, Fragment and management-window regression with synthetic presentation input, not actual root source provenance, row routing, eligible staking or visual readability. Seven protected records, two wallet rows and four chain-account rows remain exact; scratch-copy database checks pass, settings restore exactly and crash buffers remain unchanged. The app is force-stopped after cleanup. The existing second-Back navigation and black-image/readability limits remain explicit.

Candidate-v2 SHA `0667a85ceb7c48f840cfb75d8a0ad0ad8406ded290f170a36b50898554c65fe8` binds 944 evidence files. Independent final review SHA `cc70a760bfb0b2a36d9d564a0bf0ce34c876969c3de4748668a2bdc062b4d435` accepts this checkpoint; see `docs/android-staking-scenario-owner-validation.json` and `docs/android-staking-scenario-owner-independent-final-review.json`. Integrated at `d9a40d065`; exact-tree verification is recorded below.

Selection ownership follows actual selectionItem emissions, not every wallet/registry/preference intent; prior relay publication admission remains separate. Non-cooperative work can delay progress until it returns. New actual-root recovery tests cover relay and pool, not a fresh parachain round trip. Retained root-alert callbacks, equal-text alert adapter rebinding and action-to-info holder reuse are next source-based candidates requiring controlled baselines. The optional payout listener is unchanged; current-selection reads alone do not establish wrong-identity writes. Automatic root-alert/registry freshness, full native/design, migration, physical-device, signing/store and release gates remain open.

Integration verified at `d9a40d065fb091f2a42c91aa410a755d08f51a29`: whole4,347-file executable tree,376utils/84transport and944evidence pins exact; verification `18147baf25141426aa423e777dcf2ae504e18291322e039729c8b5021bab738b`. No additional build or native run is claimed.

## Unbond confirmation regression scheduling — 29 October 2026

The stale-source confirmation test now waits for the background fee projection
as well as the immediate error dialog. The retained failing result observed a
null fee before that projection completed. The no-builder, no-submission and
error-fee assertions remain in place; production signing code is unchanged.
Local focused verification is recorded in the impact-assessment takeover receipt.
