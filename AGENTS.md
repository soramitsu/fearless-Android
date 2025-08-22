# Repository Guidelines

<!-- wallet-context:start -->
> **About this codebase**  
> This repository contains the codebase for a cryptocurrency wallet compatible with the **Polkadot**, **EVM**, and **TON** ecosystems.  
> It uses dependencies available in the [soramitsu/fearless-utils-Android](https://github.com/soramitsu/fearless-utils-Android) repository and has an iOS equivalent in [soramitsu/fearless-iOS](https://github.com/soramitsu/fearless-iOS).
<!-- wallet-context:end -->


[![Android CI](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml)

## Project Structure & Modules
- `app/`: Android app entry; build types `debug/release/staging/develop/pr`.
- `feature-*/`: Split by domain with `-api` and `-impl` modules (e.g., `feature-wallet-api`, `feature-wallet-impl`).
- `core-api/`, `core-db/`, `runtime/`, `runtime-permission/`, `common/`: Shared foundations.
- `test-shared/`: Test-only utilities reused across modules.
- `buildSrc/`, `detekt/`, `scripts/`, `docs/`: Gradle plugins/config, static analysis, helper scripts, documentation.
- Per-module sources: `src/main/java|kotlin`, resources `src/main/res`, unit tests `src/test`, instrumentation tests `src/androidTest`.

## Build, Test, and Dev Commands
- Build app (APK): `./gradlew :app:assembleDebug` (use `assembleRelease` for release).
- Full build + checks: `./gradlew clean build`.
- Static analysis: `./gradlew detektAll` (auto-fix formatting: `./gradlew detektFormat`).
- Unit tests (aggregated): `./gradlew runTest` (runs detekt, unit tests, JaCoCo report).
- Per-module unit tests: `./gradlew :core-db:testDebugUnitTest` (or `testDevelopDebugUnitTest` if present).
- Android Lint: `./gradlew :app:lint`.
- Coverage report: `./gradlew jacocoTestReport` (outputs under each module’s `build/reports/jacoco`).
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

## Commit & Pull Requests
- Commits: imperative, concise subject; reference issues (`#123`). Prefer Conventional Commits (`feat:`, `fix:`, `refactor:`) when possible.
- Before PR: ensure `./gradlew runTest` passes and `detektAll` is clean.
- PR checklist: clear description, linked issue, screenshots/video for UI, steps to test, notes on config/secrets.
 - Use the PR template; CI must be green (Android CI badge above).

## Security & Configuration
- Secrets are read via `scripts/secrets.gradle`; set in env vars or `local.properties` (see `README.md`).
- Do not commit keys or `local.properties`. Use the provided debug keystore only for local builds.
- Polkadot runtime sources: to align with a specific Polkadot SDK release (e.g., `polkadot-stable2503`), you can override chain/type registries without code changes:
  - `TYPES_URL_OVERRIDE`, `DEFAULT_V13_TYPES_URL_OVERRIDE`, `CHAINS_URL_OVERRIDE`
  - Example in `local.properties`:
    - `TYPES_URL_OVERRIDE=https://.../all_chains_types_android.json`
    - `CHAINS_URL_OVERRIDE=https://.../chains.json`
  - After updating, run `./gradlew detektAll runTest :app:lint`.
- Library version pinning: to use a specific `shared_features` version compatible with a Polkadot SDK release, set
  - `SHARED_FEATURES_VERSION_OVERRIDE=1.x.y`
  - Works via `local.properties` or environment variable.

## Utils Integration
- Gradle maps the GitHub repo `soramitsu/fearless-utils-Android` as a source dependency and builds `jp.co.soramitsu.fearless-utils:fearless-utils` from source (requires network).
- Building from source requires NDK and Rust toolchain installed (see README for versions).
- This enables rapid testing of utils updates needed for specific Polkadot SDK releases.

## Documentation
- Status: see `docs/status.md` for current health, coverage, and risks.
- Roadmap: see `docs/roadmap.md` for prioritized tasks, acceptance criteria, and prompts.
- Sync policy: update `docs/status.md` and `docs/roadmap.md` whenever code changes (in the same PR) so they stay in sync with the repository’s current state.
