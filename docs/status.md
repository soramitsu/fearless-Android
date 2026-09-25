# Status Summary

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
- A 2026-09-24 read-only source inventory for Polkadot Asset Hub → Moonbeam USDt records the bundled registry, frozen gap/approval manifests, and blocked evidence file digests. The bundled source advertises an Asset Hub USDt route and a possible Moonbeam `xcusdt` wallet asset, but no execution spec. Canonical on-chain asset mapping, runtime call/location/fee review, and funded two-chain evidence remain open. Run `scripts/inspect-xcm-assethub-moonbeam-usdt.js` to regenerate the report.
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
