> Historical qualification receipts named in this document are retained in the local consolidation recovery archive, outside the public source tree. They are not current release approval. See `docs/public-source-qualification.md` for the published source checks and remaining release gates.

# K6 master-key recovery

This lane implements the explicit fresh secure-storage slot and D-1 reversal. It is locally qualified and awaits integration with the separately qualified PIN recovery UI and archive lane. No release is authorized by these local tests.

## Storage contract

- Ordinary key creation keeps `PreferenceAesKeyPolicy.mayCreateNewKey` unchanged. A missing key beside retained wallet data is never recreated automatically.
- Explicit retry of the previous slot must fail permanently at least twice across increasing Android OS boot counts. Unknown boot counts and temporary provider failures never grant consent. Consent expires after five minutes and binds the exact typed old wrapped-key fingerprint and current boot. The user also confirms possession of recovery backups.
- Activation rechecks permanent failure before creating or reusing the additive `key_alias_v2` alias. One synchronous, verified preference commit retains the old wrapped value under `secret_key_lost_<epoch>` with its original type, records whether it existed, and installs `secret_key_v2`, its own GCM sentinel, and `active_slot=2`. The original wrapped value, alias, wallet sentinel, PIN, wallet ciphertexts, and database stay untouched. An ambiguous commit latches the existing process-restart requirement.
- Every access tries to authenticate both available slots independently. A recovered previous key becomes preferred again; GCM data written by the fresh slot remains readable as a fallback. CBC data is never interpreted under an unrelated fresh key. Provider failures remain global and retryable; they never become per-wallet corruption.
- The existing startup revalidation releases a matching old-slot quarantine when its key returns. Explicit recovery invalidates the session ready flag and runs the complete database, journal and revalidation gate again.
- `EncryptionUtil.assessPinHealth()` is the effective PIN health boundary. An absent PIN after fresh activation means recovery is still required. A valid PIN under either authenticated slot is healthy. PIN ciphertext over 4096 characters is unreadable without decoding and remains exact. `EncryptedPreferencesImpl.getPinHealth()` delegates to this boundary. Integration must also use it in the proof-bound PIN-reset write preflight.

## Recovery interface

`WalletKeyLossRecoveryActivity` is not exported. It offers retry before fresh storage, shows the requirement to restart the device, and requires explicit backup consent before activation. A configured device lock adds device-credential confirmation. Closing or backgrounding hides cached wallet data and drops in-memory consent outside an active credential prompt. No path here grants signing access.

Before accepting fresh storage, users with a device lock can authenticate to view cached public records. `WalletPublicSnapshotReader` opens SQLite read-only, never opens Room, never migrates and never reads secret columns. It bounds the database size, query deadline, row count and projected value size. Cached values are labelled as cached; unreadable or omitted records are disclosed. Emergency exact key access stays reachable through the existing authenticated screen.

After integration, Recovery Center must retain a route to `WalletKeyLossRecoveryActivity.intent(context)` so the previous key can be retried explicitly after activation. The parent PIN lane owns that routing and the proof-bound PIN-reset flow.

## Historical evidence and validation

App instrumentation includes byte-identical copies of the frozen 2.0.0/version-30 and 3.7.4/version-71 public database fixtures, with their original release-derived manifests. The frozen assets contain schema DDL extracted from the official binaries. The reader test adds synthetic public-only wallet rows to disposable copies, then checks that wallet identities remain visible and each populated copy stays byte-identical across the read. No fixture is a user backup.

Final evidence is in `k6-master-key-validation.json`: 1,197 JVM cases (1,193 pass, four existing core-db network skips), 48 common native tests and five app native tests pass. Debug app and instrumentation APKs assemble. `detektAll` passes, but its existing exclusions include app/common/core-db/account and do not qualify the changed files with static analysis. Source hashes bind all 34 changed source/test/resource files. All 22 strings and format placeholders match in the eight shipped locales; `git diff --check` passes.

Three 320dp/font-scale-2 captures were inspected: home, initial consent and scrolled/checked consent. Native tests exposed delayed OnShow gating; Continue is now disabled immediately and the handler rechecks the checkbox. An explicit opaque dialog theme, bounded scroll body and high-contrast controls fix the visual findings. Earlier failed native runs and development logs are retained in `build/reports/k6-keyloss/` with their limitations recorded; they are not reported as passing final evidence.

Combined integration must use effective health in proof-reset persistence and must not allocate an old-PIN archive for an absent captured PIN. Test that case through the public reset APIs. The persistent Recovery Center retry route and combined qualification remain parent integration gates. Other Android runtime versions, physical credential/biometric acceptance and protected release qualification remain open.
