# Full support for Polkadot SDK release: polkadot-stable2503

Why: Align the wallet with the latest stable Polkadot SDK, ensuring type/metadata compatibility and correct decoding/encoding across chains.

Scope: Substrate runtime alignment across Polkadot/Kusama/Westend/AssetHub and major parachains used by the app (per `chains.json`).

Acceptance criteria:
- App runs without SCALE decode errors on target chains.
- Balances, transfers, fees, and staking screens load and execute extrinsics successfully on Polkadot and Kusama.
- Chain sync (ChainRegistry) stable: connections establish, runtime providers load, subscriptions update on version bumps.
- No regressions in unit tests; detekt/lint green.
- If APIs changed (e.g., extrinsic names/signatures), code updated or guarded by capability checks; createPool/rename items validated.

Suggested steps:
1) Registry overrides: In `local.properties`, set
   - `TYPES_URL_OVERRIDE=https://<your>/all_chains_types_android.json` (stable2503-aligned)
   - `DEFAULT_V13_TYPES_URL_OVERRIDE=https://<your>/default_v13_types.json`
   - `CHAINS_URL_OVERRIDE=https://<your>/chains.json` (validated against stable2503)
2) fearless-utils alignment: If needed, use a checkout/tag that supports stable2503
   - `export FEARLESS_UTILS_PATH=/abs/path/to/fearless-utils-Android`
   - Rebuild to use composite substitution.
3) Build + checks:
   - `./gradlew detektAll runTest :app:lint`
   - `./gradlew :app:assembleDebug`
4) Runtime smoke tests (manual):
   - Verify ChainRegistry establishes connections and loads metadata (logcat).
   - Wallet → Balances shows assets and fiat values.
   - Send: compute fee and submit a small transfer on Westend/Kusama dev if available.
   - Staking: validators/nominators decode, no crashes.
5) Address API deltas:
   - Update runtime-extrinsic assumptions and storage paths; add capability checks as needed.
6) Update defaults (optional): If stable2503 becomes default, update `runtime/build.gradle` and docs with new registry URLs.
7) Document: Add the exact registry URLs used to `docs/status.md` and a short note on verification results.

Verification matrix (execute manually or script):
- Polkadot: balances load, transfer fee computed, send succeeds on test account.
- Kusama: same as above; staking validator list loads.
- AssetHub: asset enumeration works; transfers to another account OK.
- Westend: basic transfer path for low-risk checks.

—

More roadmap items (P0/P1/P2), including technical debt and follow-ups, are maintained in `docs/roadmap.md`.

