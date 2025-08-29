# AGENTS Guide: app

Purpose
- Hosts the Android application entry point, wiring DI and navigation.
- Depends on feature `-api` and `-impl` modules and ties them together.

Key Entry Points
- `jp.co.soramitsu.app.App` — Hilt bootstrap, locale, OptionsProvider, WalletConnect v2 init.
- `jp.co.soramitsu.app.root.presentation.RootActivity` — NavHost container, app-level navigation.
- `app/src/main/res/navigation/*.xml` — `main_nav_graph.xml`, `root_nav_graph.xml`, `onboarding_nav_graph.xml`, `bottom_nav_graph.xml`.
- Root ViewModels & routers under `app/src/main/java/jp/co/soramitsu/app/root/presentation/*`.

Build Types
- `debug`, `release`, `staging`, `develop`, `pr`.
- R8/shrinker toggled for remote builds; Firebase App Distribution configured on CI branches.

Configs & Secrets (see README for details)
- WalletConnect: `WALLET_CONNECT_PROJECT_ID` in `BuildConfig`.
- Moonpay: `MOONPAY_TEST_SECRET`, `MOONPAY_PRODUCTION_SECRET`.
- EVM/API keys: `FL_BLAST_API_*`, `FL_ANDROID_*SCAN_API_KEY`.
- SoraCard & X1: multiple Gradle properties required (test/prod creds).

Common Tasks
- Add a new feature screen:
  1) Add destination to an appropriate nav graph.
  2) Inject feature router (from `feature-*-api`) into `RootActivity`/host fragment.
  3) Wire ViewModel with Hilt in the feature `-impl` module.
- Add a feature module dependency: Update `app/build.gradle` to include `:feature-xyz-api` and `:feature-xyz-impl`.
- Adjust app metadata for WalletConnect: Update the AppMetaData fields in `App.setupWalletConnect()`.

Run, Lint, Test
- Build debug: `./gradlew :app:assembleDebug`
- Lint: `./gradlew :app:lint`
- Full checks: `./gradlew detektAll runTest`

Troubleshooting
- WalletConnect init errors: verify `WALLET_CONNECT_PROJECT_ID` and network reachability.
- Missing features at runtime: ensure `matchingFallbacks` and proper build type are used.
- Local utils: set `FEARLESS_UTILS_PATH` to include local `fearless-utils-Android` in the composite build.

