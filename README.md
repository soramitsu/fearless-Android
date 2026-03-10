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
MOONPAY_TEST_SECRET=stub
MOONPAY_PRODUCTION_SECRET=stub
```

Note, that with stub keys buy via moonpay will not work correctly. However, other parts of the application will not be affected.

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

Prerequisites: JDK 21 (Temurin/Adoptium) and Android SDK with API 35 + build-tools 35.0.0. The script will try to locate `ANDROID_SDK_ROOT` and install missing packages if `sdkmanager` is available.

### Use fearless-utils-Android (remote source dependency)

The build maps the GitHub repository as a source dependency and builds the module from source.

```
# Requires network access during Gradle configuration
./gradlew :app:assembleDebug
```

Gradle will fetch https://github.com/soramitsu/fearless-utils-Android and build module `jp.co.soramitsu.fearless-utils:fearless-utils` from source.

Prereqs for building the utils from source: NDK (25.2.9519653) and Rust toolchain available on PATH (`rustup`, `cargo`).

## Contributing

- Contributor Guide: see [AGENTS.md](AGENTS.md) for project layout, commands, and conventions.
- Process & community details: see [CONTRIBUTING.md](CONTRIBUTING.md).

## License
Fearless Wallet Android is available under the Apache 2.0 license. See the LICENSE file for more info.
