# Repository Guidelines

<!-- wallet-context:start -->
> **About this codebase**  
> This repository contains the codebase for a cryptocurrency wallet compatible with the **Polkadot**, **EVM**, and **TON** ecosystems.  
> It owns its runtime and configuration sources in this repository and has an iOS equivalent in [soramitsu/fearless-iOS](https://github.com/soramitsu/fearless-iOS).
<!-- wallet-context:end -->


[![Android CI](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml)

## Project Structure & Modules
- `app/`: Android app entry; build types
  `debug/release/internalAppSharing/staging/develop/pr`.
  `internalAppSharing` is a CI-only smoke variant signed by a run-bound
  ephemeral Android Debug JKS; it is not a production distribution path. Its
  Google Services task reads the checksum-pinned release placeholder directly,
  and `app/src/internalAppSharing` must never be created.
- `feature-*/`: Split by domain with `-api` and `-impl` modules (e.g., `feature-wallet-api`, `feature-wallet-impl`).
- `core-api/`, `core-db/`, `runtime/`, `runtime-permission/`, `common/`: Shared foundations.
- `test-shared/`: Test-only utilities reused across modules.
- `buildSrc/`, `detekt/`, `scripts/`, `docs/`: Gradle plugins/config, static analysis, helper scripts, documentation.
- Per-module sources: `src/main/java|kotlin`, resources `src/main/res`, unit tests `src/test`, instrumentation tests `src/androidTest`.

## Build, Test, and Dev Commands
- Build a local debug APK: `./gradlew :app:assembleDebug`. Release artifacts
  must follow the protected, source-bound procedure in
  `docs/releases/PROCESS.md`; do not invoke a local release task for distribution.
- Full build + checks: `./gradlew clean build`.
- Static analysis: `./gradlew detektAll` (auto-fix formatting: `./gradlew detektFormat`).
- Unit tests (aggregated): `./gradlew runTest` (runs detekt, unit tests, JaCoCo report).
- Per-module unit tests: `./gradlew :core-db:testDebugUnitTest` (or `testDevelopDebugUnitTest` if present).
- Android Lint: `./gradlew :app:lint`.
- Coverage report: `./gradlew jacocoTestReport` (outputs under each module’s `build/reports/jacoco`).
- Post-merge validation: `./gradlew postMergeCheck` or `bash scripts/post_merge_check.sh`.
- App version: `./gradlew :app:printVersion`.
- Local validation helper: `bash scripts/validate-local.sh`.

## Coding Style & Naming
- Language: Kotlin 2.1, Java 21 target; Compose enabled where applicable.
- Formatting: Detekt + detekt-formatting; 4-space indent; max line length per `detekt/detekt.yml`.
- Files: one public class per file; filename matches class (`PascalCase`).
- Packages: lowercase dot-separated; avoid acronyms.
- Resources: layouts `activity_*.xml`, `fragment_*.xml`; ids `btnX`, `tvX`; strings `feature_action_label`.
- KDoc for public APIs; prefer immutable data and explicit visibility.

## Testing Guidelines
- Frameworks: JUnit4, AndroidX Test, Room testing, Coroutines test; Mockito/MockK available.
- Structure: unit tests in `src/test`, instrumentation in `src/androidTest`.
- Naming: mirror class under test, e.g., `AccountRepositoryTest.kt`; methods `fun shouldDoX_whenY()`.
- Run: module `testDebugUnitTest` (or `testDevelopDebugUnitTest`), or root `runTest`.
- Coverage: maintain/raise JaCoCo coverage for changed code.
- New code policy: every time you add a function, create at least one unit test for it (minimum), placed in the corresponding module under `src/test`.
- Integration (DOT): add instrumentation/integration tests that hit a reachable Polkadot node and log key responses (e.g., runtimeVersion, balances, fees). Guard with assumptions so tests skip when network is unavailable.
- Logs: when testing DOT, log the raw RPC response and parsed model to aid debugging; never log secrets.

## Commit & Pull Requests
- Commits: imperative, concise subject; reference issues (`#123`). Prefer Conventional Commits (`feat:`, `fix:`, `refactor:`) when possible.
- Before PR: ensure `./gradlew runTest` passes and `detektAll` is clean.
- PR checklist: clear description, linked issue, screenshots/video for UI, steps to test, notes on config/secrets.
 - Use the PR template; CI must be green (Android CI badge above).

## Security & Configuration
- Secrets are read via `scripts/secrets.gradle`; set in env vars or `local.properties` (see `README.md`).
- Do not commit keys, keystores, provisioning files, or `local.properties`.
- No closed Google SDK (Play services, Play Core/Integrity, Firebase, `googleid`, Google API
  client libraries) and no `google-services.json`. Google Drive backup uses the in-repo OAuth 2.0
  + PKCE browser sign-in (`core-backup/.../oauth`, `feature-account-impl/.../google_signin`) and
  Drive v3 REST client (`core-backup/.../data/GoogleDriveApi.kt`); keep the backup file layout
  (`backupFolder`, `<address>.json`, `text/json`) byte-compatible with iOS and the extension.
  `GOOGLE_DRIVE_OAUTH_CLIENT_ID` (scripts/secrets.gradle) must stay a client of project
  621183184145; changing it also changes the release pin and the AAB identity redirect scheme.
- Internal App Sharing must run only through the dedicated
  `android-internal-app-sharing.yml` CI path. Generate a new mode-`0600` JKS and
  bind its certificate SHA-256 for that run; never commit or reuse the private
  key. PR runs are non-distributable validation only and must not upload an
  artifact. Only a manual run of the first-party workflow from the protected,
  merged, current `master` head may retain a seven-day verified GitHub Actions
  handoff. The workflow must never upload to Google/Play or mutate a Play track.
  IAS builds carry no Firebase configuration; do not claim IAS qualifies
  passkey backup, Play upgrade, production migration or production signing.
  When versioning changes, update the workflow/verifier hardcodes for
  `4.3.2-ias` / `234` in the same reviewed commit.
- Polkadot runtime sources: to align debug builds with a specific Polkadot SDK release (e.g., `polkadot-stable2503`), you can override chain/type registries without code changes:
  - `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_DEBUG_OVERRIDE`
  - Release chain discovery is intentionally pinned to the canonical production URL and does not accept a chain-registry override.
  - Example in `local.properties`:
    - `TYPES_URL_OVERRIDE=https://.../all_chains_types_android.json`
    - `CHAINS_URL_DEBUG_OVERRIDE=https://.../chains.json`
  - After updating, run `./gradlew detektAll runTest :app:lint`.
## Local Properties (private)
- Create a root-level `local.properties` with the required secrets and service credentials. Do NOT commit this file.
- See `docs/samples/local.properties.example` and create a private `local.properties` at the repo root; replace placeholders with your real values.
- Typical keys include: MoonPay publishable keys, X1 plugin, Google Web Client IDs, Ethereum providers (Blast, Etherscan/BscScan/PolygonScan/OKLink), WalletConnect, Alchemy, Dwellir, TON API. Never put a MoonPay server secret in Android configuration.
- Formats: use `key=value` per line; avoid trailing spaces. Strings may be unquoted; if values contain special characters or spaces, wrap in double quotes. Set `sdk.dir=/absolute/path/to/Android/sdk` to avoid SDK lookup errors.
- Runtime overrides (mirrors recommended for first run):
  - `TYPES_URL_OVERRIDE=https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/chains/all_chains_types_android.json`
  - `DEFAULT_V13_TYPES_URL_OVERRIDE=https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/chains/default_v13_types.json`
  - `CHAINS_URL_DEBUG_OVERRIDE=https://raw.githubusercontent.com/soramitsu/fearless-Android/master/remote-config/production/chains/v13/chains.json`
- Verify config: `./gradlew printPolkadotSdkAlignment` prints effective URLs before you run the app/tests.

## Runtime ownership
- Edit `runtime-utils`, `runtime-websocket` and `core-*` modules directly. Preserve package/API compatibility and upstream license attribution.
- Update owned file digests in `config/android-runtime-source-pins.json` with reviewed changes and run its verifier and adversarial tests. No external runtime checkout or published internal library override is supported.
- `remote-config` is owned by Android; update its manifest and URL-closure tests with data changes. Publish on public `master` before shipping consumer URLs. Keep the legacy host available to installed clients.

## Native crypto (sr25519)
- `libsr25519java.so` (all sr25519 keys: Polkadot, Kusama, SORA) is built from `runtime-utils/native/sr25519` during every Gradle build by `:runtime-utils:buildSr25519AndroidLibraries` (`scripts/build-sr25519.py`, cargo-ndk, API 26, 16 KB pages). No prebuilt `.so` is committed; the repository ships no other native code of its own.
- Builds need rustup (the toolchain in `runtime-utils/native/sr25519/rust-toolchain.toml` installs itself), `cargo install cargo-ndk --version 4.1.2 --locked`, and NDK `30.0.16248370`.
- Every build checks each ABI against `runtime-utils/native/sr25519/reproducible-build.json`. After an intended Rust, `Cargo.lock` or toolchain change, run `:runtime-utils:recordSr25519NativeHashes` and `:runtime-utils:verifySr25519ReproducibleBuild`, then commit the manifest and the runtime source pins. `-Psr25519.allowHashMismatch=true` is for local debug builds only.
- Keep the JNI ABI (function names, byte layouts, exception messages) and the derivation behaviour unchanged; `scripts/verify-sr25519-legacy-compatibility.sh` compares the library with the one Android 4.3.1 shipped.

## Documentation
- Status: see `docs/status.md` for current health, coverage, and risks.
- Roadmap: see `docs/roadmap.md` for prioritized tasks, acceptance criteria, and prompts.
- Sync policy: update `docs/status.md` and `docs/roadmap.md` whenever code changes (in the same PR) so they stay in sync with the repository’s current state.
