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
- `debug`, `release`, `internalAppSharing`, `staging`, `develop`, `pr`.
- `internalAppSharing` is CI-only and file-only. It inherits release shrinker
  semantics, uses a run-bound ephemeral JKS with the Android Debug identity and
  the `-ias` version-name suffix, and cannot upgrade a Play-installed app.
- R8/shrinker toggled for remote builds; Firebase App Distribution configured on CI branches.

Configs & Secrets (see README for details)
- WalletConnect: `WALLET_CONNECT_PROJECT_ID` in `BuildConfig`.
- MoonPay is disabled until its URL is signed by a backend or a supported
  public mobile flow; never add a MoonPay signing secret to `BuildConfig`.
- Buy providers: `RAMP_TOKEN_DEBUG`, `RAMP_TOKEN_RELEASE`, `COINBASE_APP_ID`.
- Firebase: checked-in `google-services.json` files are placeholders; real
  Firebase project files must come from private CI or local overlay config for
  governed production release builds. IAS finalizes the typed input of
  `processInternalAppSharingGoogleServices` directly to the checked-in
  `fearless-public` release placeholder. It must never create an
  `app/src/internalAppSharing` tree. Its fake/empty Firebase and OAuth values
  mean IAS cannot qualify live Firebase, Google sign-in, or Drive/passkey
  backup and restore.
- EVM/API keys: `FL_BLAST_API_*`, `FL_ANDROID_*SCAN_API_KEY`.

Release Safety
- Never use the legacy combined `RELEASE_OVERLAY_MODE=release`. The protected
  workflow first runs a credential-free `release-controls` source/governance
  gate, then uses `firebase` in the protected build job and `signing` in a
  separate protected fresh signing job.
- At every boundary, use `scripts/verify-android-release-source-tree.sh` to
  prove the exact commit/tree and reject tracked, staged, untracked, or
  source-like ignored drift. Allow the nested `fearless-utils-Android` checkout
  only after it has been explicitly checked out and independently verified.
- Gradle may produce the release intermediate only through the exact CI-only,
  source-bound unsigned `:app:bundleRelease` path. It must receive neither an
  upload keystore nor a Play service-account credential.
- The signing job must download and revalidate the exact unsigned digest,
  provenance, all four source attestations, tag, `master`, and source tree
  before restoring signing material. The signing alias comes from the approved
  non-secret repository variable; only the keystore and its passwords are
  secrets. Sign with
  `scripts/sign-android-release-aab.sh`, which pins the registered upload
  certificate and proves every non-signature AAB entry is byte-identical, then
  clean the signing overlay immediately.
- CI never mutates Google Play and must not receive a Play service-account
  credential. Upload only the final attested AAB to the existing Open Testing
  track through Play Console after reviewing its exact three-file evidence.
- Internal App Sharing bundling accepts only the exact CI task
  `:app:bundleInternalAppSharing` with `--no-build-cache --rerun-tasks` from
  clean `RELEASE_COMMIT == HEAD` source, forbids release
  signing/Firebase/Play inputs, and produces only a run-bound-debug-signed smoke
  AAB. PR runs are non-distributable validation only. Only a manual first-party
  workflow run from the protected, merged, current `develop` head may upload a
  seven-day verified GitHub Actions handoff. An authorized operator may then
  manually upload those exact bytes to Play Console Internal App Sharing;
  Gradle and CI must never upload to Google/Play or mutate any track. Never
  uninstall a funded wallet for IAS, and never treat IAS as production
  migration or signing evidence. Update all reviewed IAS `4.2.0-ias` / `230`
  hardcodes in the same commit as any source-controlled version change.

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
