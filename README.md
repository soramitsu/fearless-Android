### Fearless Wallet Android
[![Google Play](https://img.shields.io/badge/Google%20Play-Android-green?logo=google%20play)](https://play.google.com/store/apps/details?id=jp.co.soramitsu.fearless) [![Android CI](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml/badge.svg)](https://github.com/soramitsu/fearless-Android/actions/workflows/android-ci.yml)

![logo](/docs/fearlesswallet_promo.png)

## About
Fearless Wallet is a mobile wallet designed for the decentralized future on the Kusama network, with support on iOS and Android platforms. The best user experience, fast performance, and secure storage for your accounts. Development of Fearless Wallet is supported by Kusama Treasury grant.

[![](https://img.shields.io/twitter/follow/FearlessWallet?label=Follow&style=social)](https://twitter.com/FearlessWallet)

## Roadmap
Fearless Wallet roadmap is available for everyone: [roadmap link](https://soramitsucoltd.aha.io/shared/97bc3006ee3c1baa0598863615cf8d14)

## Dev Status
Track features development: [board link](https://soramitsucoltd.aha.io/shared/343e5db57d53398e3f26d0048158c4a2)

## Architecture & Current State
- Architecture overview: see `docs/ARCHITECTURE.md` for module layout, layers, and flows.
- Module map: see `docs/MODULES.md` for a quick feature-by-feature guide.
- Current state: see `docs/CURRENT_STATE.md` for supported ecosystems, integrations, and TODO hotspots.
 - Status snapshot: see `docs/status.md` for health, risks, and what’s incomplete.
 - Roadmap: see `docs/roadmap.md` for prioritized, actionable tasks.
 - Release process: see `docs/releases/PROCESS.md` for beta → stable steps and checklists.

## How to build

To build Fearless Wallet Android project, you need to provide several keys either in environment variables or in `local.properties` file:

### Moonpay properties
```
MOONPAY_TEST_PUBLIC_KEY=stub
MOONPAY_PRODUCTION_PUBLIC_KEY=stub
```

Note, that with stub keys buy via moonpay will not work correctly. However, other parts of the application will not be affected.
The Android artifact accepts only MoonPay publishable keys. It intentionally
does not prefill `walletAddress`, because MoonPay requires that parameter to be
signed by a backend-held API secret. Never add a MoonPay secret or client-side
URL signing to the app. See MoonPay's
[widget signing guidance](https://dev.moonpay.com/docs/off-ramp-web-sdk#signing).

### Buy provider partner properties
```
RAMP_TOKEN_DEBUG=stub
RAMP_TOKEN_RELEASE=stub
COINBASE_APP_ID=stub
```

Partner identifiers are loaded from `local.properties` or environment variables
so public builds do not require committed release config.

### Firebase properties

The checked-in `app/src/*/google-services.json` files are public placeholders
with real package names but no live Firebase project IDs, API keys, OAuth
clients, or certificate hashes. Governed production release and Play-track
distribution builds must replace them from the private CI overlay before
publishing. The IAS smoke lane below is the explicit exception: it accepts only
the public placeholder and must not receive the private overlay.

### Play testing releases

CI does not mutate Google Play. The `Android Signed Release Artifact` workflow
produces the only approved upload artifact through three fresh-runner jobs:

1. credential-free `release-controls` validates the signed tag, exact
   `master` source tree, prior CI, governance, and adversarial release suites;
2. `release-build` (`android-release-build`) restores only Firebase, runs the
   exact CI-only/source-bound unsigned `:app:bundleRelease`, and uploads an
   exact, individually attested four-file unsigned artifact: AAB, checksum,
   provenance, and the bounded verified Gradle/R8 build log;
3. `release-signing` (`android-release-signing`) downloads and revalidates
   those four files, their digests, source, provenance, and attestations before
   restoring the upload key. It signs with the certificate-pinned standalone
   signer, verifies identity, native payloads, and permissions from the signed
   bytes, and uploads a separately attested three-file final artifact.

Release dispatch remains fail-closed until the sentinel in
`.github/release/android-tag-signer-fingerprints.txt` is replaced by the exact
reviewed OpenSSH Ed25519 `SHA256:...` fingerprint and the matching one-line
public key is stored, base64 encoded, in the
`ANDROID_RELEASE_TAG_SIGNER_PUBLIC_KEY_B64` repository variable.

The build and signing environments require disjoint, exact User reviewer
allowlists, so the artifact crosses two distinct environment approvals. Each
allowlist must contain at least one User; two or more provides additional
reviewer redundancy. No Play service-account credential, Android Publisher API
client, or Gradle Play publishing task belongs in this workflow. An authorized
operator downloads the final workflow artifact and uploads its AAB manually to
the existing `beta`/Open Testing track, preserving the existing country and
tester configuration. See `docs/releases/PROCESS.md` for the evidence and Play
Console checklist.

### Internal App Sharing smoke builds

> [!WARNING]
> The Internal App Sharing (IAS) bundle is Android-debug-signed and Google
> re-signs IAS uploads. It cannot upgrade a wallet installed from Google Play.
> Never uninstall a funded wallet to install an IAS build. A fresh IAS install
> does **not** validate a production database migration or production release
> signing.

IAS is a file-only, pre-release smoke-test lane. Its Gradle variant deliberately
preserves the release shrinker, resources, package name, version code, and
manifest semantics, adds only the `-ias` version-name suffix, and emits an
unsigned quarantine AAB. A separate fresh-runner qualifier externally signs
those exact bytes with a run-bound ephemeral JKS whose certificate has the
Android Debug identity. It is not an alternative production release path, and
its signer is not a production trust anchor.

Only CI may create the artifact, from a clean checkout whose `HEAD` exactly
matches the lowercase 40-character `RELEASE_COMMIT`. Pull-request runs are
non-distributable validation only: they build and inspect the candidate on the
runner but create no handoff and upload no artifact. The sole eligible handoff
comes from a manual run in the first-party `soramitsu/fearless-Android`
repository after the reviewed change is merged to the protected `develop`
branch and the checked-out commit is still the remote `develop` head.

The protected invocation is exactly
`:app:bundleInternalAppSharing --no-build-cache --rerun-tasks`; combining it
with another Gradle task, using an unqualified/alternate task, supplying
release-signing inputs, or supplying Play/Firebase distribution credentials
fails closed. CI also binds the verified `fearless-utils-Android` commit and
effective source tree.

The `processInternalAppSharingGoogleServices` task has its typed JSON input
finalized directly to the checksum-pinned, reviewed public placeholder at
`app/src/release/google-services.json`. The build validates that exact
single-link, non-symlink file and its public-only Firebase identity. It never
creates, copies into, owns, or cleans an `app/src/internalAppSharing` tree; that
path must remain absent. Real Firebase configuration is forbidden in this lane.
Consequently, IAS can qualify install/fresh launch, cold start, onboarding,
navigation, local-only disposable-wallet flows, and screens that do not depend
on the disabled services. It cannot qualify live Firebase behavior, Google
OAuth/sign-in, Google Drive or passkey backup/restore, Play upgrade, production
database migration, or production signing. Empty or fake placeholder values
must never be interpreted as successful coverage of those integrations.

Pull-request validation is also deliberately WalletConnect-secretless and
non-distributable. Only an eligible protected-`develop` manual dispatch binds
`secrets.FL_WALLET_CONNECT_PROJECT_ID`, and only to the exact non-cached bundle
step; that step fails closed unless it is 32 hexadecimal characters. The
project ID is never printed. Trusted tester acceptance must scan a valid `wc:`
pairing URI in addition to the cold-start/navigation soak.

Each eligible manual run transfers the exact unsigned producer artifact by
GitHub artifact ID and digest into a fresh qualifier. Disposable operating
system users independently validate the unsigned bytes, create a mode-`0600`
JKS, externally sign the candidate, export only its public certificate, and are
terminated at each trust-boundary transition. The private key is erased before
post-sign verification; it is never stored in the repository, transferred to
the handoff, or reused between runs.

Qualification must run `scripts/test-android-internal-app-sharing.sh`, build the
AAB, and then run `scripts/verify-android-internal-app-sharing-aab.sh` plus
`scripts/test-android-internal-app-sharing-aab.sh` against those exact bytes.
The artifact verifier checks the debug certificate and complete JAR signature,
embedded `RELEASE_COMMIT`, package/version/SDK identity, component and
permission surface, native payloads, and compiled public Firebase placeholders.
The supported verifier invocation is workflow-only under the workflow's
`CI=true` and source-bound `RELEASE_COMMIT`. Unsigned mode accepts no signer
input. Signed and `--signed-from` modes bind the checksum-pinned
`BUNDLETOOL_JAR`, exact uppercase
`EXPECTED_IAS_DEBUG_CERT_SHA256`, exactly one of the private
`ANDROID_IAS_DEBUG_KEYSTORE_PATH` or public-only
`ANDROID_IAS_DEBUG_CERTIFICATE_PATH`, and the exact `FEARLESS_UTILS_COMMIT`,
`FEARLESS_UTILS_EFFECTIVE_TREE`, and `FEARLESS_UTILS_PATH`. Every mode may
write its private snapshot to one new absolute output path.

The identity harness also runs the exact extracted R8 fixture synthesizer
through 1 positive and 15 bounded negative/adversarial cases. The cases cover
source/output bytes, ZIP entry count, per-entry and aggregate expansion,
encrypted or unsupported entries, duplicate canonical metadata, symlinks, and
exclusive output creation.

The frozen suites must report dependency provenance 2 positive / 65
adversarial, bounded Gradle coverage cleanup 4 positive / 12
negative/adversarial, IAS Gradle 8 positive / 39 behavioral negative / 645
static adversarial assertions, and the workflow's Linux public-certificate IAS
AAB suite 7 positive / 78 adversarial artifacts. The AAB suite additionally
expects 7/76 in macOS certificate mode, 7/84 in macOS keystore mode, and 7/86
in Linux keystore mode. A changed total is a review event, not an automatic
pass.

Only the eligible first-party, post-merge `develop` manual run may create a
handoff. The qualifier first uploads it with an explicitly pending/untrusted
name, downloads that exact archive back by immutable artifact ID and digest,
revalidates its bounded ZIP entries under another disposable user, and records
completion. A separate finalizer retains it for seven days only when every
qualification result and the current protected `develop` head still match;
otherwise it deletes the artifact. A scheduled janitor removes canceled,
failed, stale, or ambiguous pending artifacts. Never use PR runner output.
After a successful run, an authorized operator may manually upload the exact
AAB in Play Console's Internal App Sharing page. The workflow has no Gradle
Play Publisher integration, uploads nothing to Google or Play, changes no Play
track, and consumes no Play service-account credential.

The handoff filename and verifier currently pin `4.2.0-ias` / version code
`230`. Any change to either value in `versioning/version.properties` must update
and review every IAS hardcode in the same commit before a new handoff is
eligible; never relabel old bytes with a new filename.

### Android testing distributions

For a fast manual Google Play Internal App Sharing build:

```
./gradlew :app:bundleInternalAppSharing
```

Upload
`app/build/outputs/bundle/internalAppSharing/app-internalAppSharing.aab` in Play
Console and share the generated IAS link. This explicit variant uses release
optimization/resources with the Android debug signer and a `-ias` version-name
suffix. It cannot be published by a normal Gradle Play Publisher task and is
not evidence for production signing, Play Integrity, certificate-bound app
links/passkeys, or production upgrade behavior.

Production-equivalent Google Play testing is handled by the tagged `Android
Release` workflow. It accepts only `internal`, `alpha`, or `beta`, defaults to
no Play publication and `draft`, and publishes the exact staged and attested
production-signed AAB. An Open Testing link additionally depends on Play Console
beta-track configuration and Google review. See
`docs/releases/PROCESS.md` for the required secrets, gates, and operator steps.

### X1 plugin

X1 is a plugin which is embedded into webView. It requires url and id for launching.

````
X1_ENDPOINT_URL_RELEASE
X1_WIDGET_ID_RELEASE

X1_ENDPOINT_URL_DEBUG
X1_WIDGET_ID_DEBUG
````

### Ethereum properties

Set of params required to deliver Ethereum connection

````
// Ethereum blast api nodes keys
FL_BLAST_API_ETHEREUM_KEY
FL_BLAST_API_BSC_KEY
FL_BLAST_API_SEPOLIA_KEY
FL_BLAST_API_GOERLI_KEY
FL_BLAST_API_POLYGON_KEY

// Ethereum history providers api keys
FL_ANDROID_ETHERSCAN_API_KEY
FL_ANDROID_BSCSCAN_API_KEY
FL_ANDROID_POLYGONSCAN_API_KEY
````

## Local Validation

Run static analysis, unit tests, lint, and set up Android SDK packages:

```
bash scripts/validate-local.sh
```

Manual equivalents if you prefer:

```
./gradlew detektAll
./gradlew runTest
./gradlew :app:lint
```

Prerequisites: JDK 21 (Temurin/Adoptium) and Android SDK with API 36 + build-tools 36.0.0. The script will try to locate `ANDROID_SDK_ROOT` and install missing packages if `sdkmanager` is available.

### Use fearless-utils-Android

Public builds use a checked-out copy of `fearless-utils-Android` as a composite build. CI pins that checkout to `7500809f33243ee47ecb2ec8563fc284ac4de0d6`. Locally, clone the repo next to this checkout or set `FEARLESS_UTILS_PATH`:

```
git clone https://github.com/soramitsu/fearless-utils-Android.git ../fearless-utils-Android
git -C ../fearless-utils-Android checkout 7500809f33243ee47ecb2ec8563fc284ac4de0d6
export FEARLESS_UTILS_PATH=../fearless-utils-Android
export FEARLESS_UTILS_COMMIT=7500809f33243ee47ecb2ec8563fc284ac4de0d6
export FEARLESS_UTILS_REPOSITORY=soramitsu/fearless-utils-Android
export FEARLESS_UTILS_LIBRARY_ONLY=true
bash ./scripts/test-fearless-utils-derived-tree.sh
./scripts/ensure-fearless-utils.sh
./gradlew :app:assembleDebug
```

Gradle includes the local project via a composite build through Gradle 9 and substitutes `jp.co.soramitsu.fearless-utils:fearless-utils` automatically.
Run the self-test and guard shown above before building. The guard verifies the
exact GitHub origin and pinned commit, requires an unstaged index, and compares
the active checkout with a temporary-index model of either pristine `HEAD` or
`HEAD` plus the committed library-only patch. Extra tracked or untracked source,
partial overlays, modified patch-touched files, and dirty submodules fail closed;
Git-ignored build output is outside the source-tree comparison. A pristine tree
is overlaid and reverified, while a dirty tree is never rewritten. The Gradle
`USE_REMOTE_UTILS=true` source-control fallback remains experimental and is not
the public CI contract.
Prereqs for building the utils from source: NDK r28 (android-ndk-r28 / 28.0.x) and a Rust toolchain on `PATH` (`rustup`, `cargo`).

### Rebuild libsodium with 16 KB alignment

We vendor libsodium sources under `third_party/libsodium` and ship aligned binaries under `app/src/main/jniLibs`.  
If you need to refresh them (e.g., after pulling upstream changes), run:

```
ANDROID_NDK_HOME=/Users/<you>/Library/Android/sdk/ndk/28.0.12674087 \
./scripts/build-libsodium.sh
```

The script rebuilds `libsodium.so` for arm64-v8a, armeabi-v7a, x86, and x86_64 with the Google Play-required `-Wl,-z,common-page-size=4096 -Wl,-z,max-page-size=16384` flags and copies them into `app/src/main/jniLibs`.

Tracked native/vendor binary provenance is documented in
`docs/binary-provenance.md`.
Public dependency provenance and current Soramitsu artifact blockers are tracked
in `docs/public-dependency-audit.md`.
Validate both governance documents before release with:

```
bash ./scripts/test-public-dependency-upstream-delta-export.sh
bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta
bash ./scripts/test-public-artifact-provenance-audit.sh
./scripts/audit-public-artifacts.sh --strict-provenance
```

## Contributing

- Contributor Guide: see [AGENTS.md](AGENTS.md) for project layout, commands, and conventions.
- Process & community details: see [CONTRIBUTING.md](CONTRIBUTING.md).

## License
Fearless Wallet Android is available under the Apache 2.0 license. See the LICENSE file for more info.
