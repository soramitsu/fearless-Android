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

To build Fearless Wallet Android project, you need to provide several keys either in environment variables or in `local.properties` file.

MoonPay is intentionally disabled. Its widget URL requires a server-side
signature, so a MoonPay signing secret must never be supplied to or compiled
into the Android client. Re-enable it only through a backend-signed or
MoonPay-supported public mobile integration.

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
clients, or certificate hashes. Release and distribution builds must replace
them from the private CI overlay before publishing.

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
export FEARLESS_UTILS_PATH=/absolute/path/to/fearless-utils-Android
export FEARLESS_UTILS_LIBRARY_ONLY=true
./scripts/ensure-fearless-utils.sh
./gradlew :app:assembleDebug
```

Gradle includes the local project via a composite build through Gradle 9 and substitutes `jp.co.soramitsu.fearless-utils:fearless-utils` automatically.
Run `./scripts/ensure-fearless-utils.sh` to verify the checkout, pinned commit, and library-only overlay before building. The Gradle `USE_REMOTE_UTILS=true` source-control fallback remains experimental and is not the public CI contract.
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

## Contributing

- Contributor Guide: see [AGENTS.md](AGENTS.md) for project layout, commands, and conventions.
- Process & community details: see [CONTRIBUTING.md](CONTRIBUTING.md).

## License
Fearless Wallet Android is available under the Apache 2.0 license. See the LICENSE file for more info.
