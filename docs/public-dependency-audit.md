# Public Dependency Audit

Fearless Android must build from public source and public artifacts without
private Maven repositories or private GitHub repositories. This file tracks
nonstandard Soramitsu dependencies that affect that requirement.

## Resolved Locally

### `jp.co.soramitsu:android-foundation:0.0.4`

The app only used small utility APIs from this artifact:

- `CoroutineManager`
- `MainCoroutineRule`
- `StringPair`
- BigDecimal and hex formatting helpers

These APIs now live in the first-party `:public-android-foundation` module.
Gradle substitutes the old Maven coordinate with the local module.

### `jp.co.soramitsu:ui-core:0.2.39`

The app only used a narrow Compose UI surface:

- design-system theme containers and typography locals
- `BasicNumberInput`
- `Modifier.applyIf`
- `Dimens`
- the `Size.ExtraSmall` token

These APIs now live in the first-party `:public-ui-core` module. Gradle
substitutes the old Maven coordinate with the local module.

### `jp.co.soramitsu.shared_features:{core,xcm,backup}:1.1.1.44-FLW`

The app used a broad mix of model, runtime, XCM, and backup contracts from these
artifacts. The imported API surface now lives in first-party compatibility
modules:

- `:public-shared-features-core`
- `:public-shared-features-xcm`
- `:public-shared-features-backup`

Gradle substitutes the old Maven coordinates with those local modules.

### `jp.co.soramitsu.xnetworking:lib-android:1.0.13`

The app used history, chain-config, block-explorer, and REST client adapters from
this artifact. The required API surface now lives in the first-party
`:public-xnetworking` compatibility module. Gradle substitutes the old Maven
coordinate with the local module.

### `jp.co.soramitsu.fearless-utils:fearless-utils:1.0.121`

The app resolves `fearless-utils` from a public source checkout via Gradle
composite build. CI and release workflows check out
`soramitsu/fearless-utils-Android` at
`7500809f33243ee47ecb2ec8563fc284ac4de0d6`, set `FEARLESS_UTILS_PATH`, force
the local include with `FORCE_LOCAL_UTILS=true`, and run
`scripts/ensure-fearless-utils.sh` before Gradle resolution.

Local developers can use the same contract by cloning the repo next to
`fearless-Android` or setting `FEARLESS_UTILS_PATH` explicitly. The Gradle
`USE_REMOTE_UTILS=true` source-control fallback is not used by public CI because
it does not currently resolve the requested published module version.

The carried upstream delta is exported with:

```
bash ./scripts/test-public-dependency-upstream-delta-export.sh
bash ./scripts/export-public-dependency-upstream-delta.sh --output build/reports/public-dependency-upstream-delta
```

The generated `handoff-manifest.json` records the pinned
`fearless-utils-Android` revision, the library-only overlay patch checksum and
touched paths, and SHA-256 digests for all public compatibility modules. Release
reviewers should attach or archive this bundle whenever the pinned dependency
surface changes.

## Remaining Release Risks

### Compatibility-layer limits

The replacement modules are enough to compile and run the current test suite,
but they are not all production-complete feature implementations.

Known limits:

- `ExtrinsicService` in the public core compatibility layer now supports
  runtime-version lookup, nonce lookup, payment-info fee estimation, extrinsic
  submission, and submit-and-watch status handling through public Substrate RPCs.
- The public extrinsic implementation signs a five-minute mortal era from local
  block state and `chain_getBlockHash`, falling back to immortal only when chain
  state or RPC data is unusable.
- Fee estimation has an account-aware public overload that uses the caller's
  real account, nonce, crypto type, signer, tip, app id, and mortal era. Android
  production call sites now avoid the anonymous dummy estimator; accountless UI
  placeholders return zero instead of building dummy signed fee payloads.
- The public XCM compatibility surface now preserves route metadata from chain
  config through Room and exposes origin, destination, asset, and min-amount
  discovery with malformed-route filtering. Public builds still keep XCM
  transfer execution disabled and fee/submission calls fail explicitly until an
  open-source XCM extrinsic engine and end-to-end coverage are added.
- The private Soramitsu Nexus Maven repository remains opt-in only through
  `INCLUDE_SORAMITSU_NEXUS=1`; public CI should not require it.

## Verification

The pinned public `fearless-utils` checkout guard passes:

```
FEARLESS_UTILS_PATH=../fearless-utils-Android \
./scripts/ensure-fearless-utils.sh
```

The public replacement modules and affected app modules compile:

```
JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/opt/homebrew/share/android-commandlinetools \
ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools \
FORCE_LOCAL_UTILS=true \
FEARLESS_UTILS_LIBRARY_ONLY=true \
./gradlew \
  :public-xnetworking:compileDebugKotlin \
  :public-shared-features-core:compileDebugKotlin \
  :public-shared-features-xcm:compileDebugKotlin \
  :public-shared-features-backup:compileDebugKotlin \
  :runtime:compileDebugKotlin \
  :feature-account-api:compileDebugKotlin \
  :feature-wallet-api:compileDebugKotlin \
  :feature-staking-api:compileDebugKotlin \
  :feature-polkaswap-api:compileDebugKotlin \
  :feature-account-impl:compileDebugKotlin \
  :feature-wallet-impl:compileDebugKotlin \
  :app:compileDebugKotlin \
  --no-daemon --console=plain --stacktrace
```

The public Substrate extrinsic implementation and affected callers also pass:

```
./gradlew \
  :core-api:testDebugUnitTest \
  :feature-crowdloan-impl:compileDebugKotlin \
  --no-daemon --console=plain --stacktrace

./gradlew \
  :feature-wallet-impl:compileDebugKotlin \
  :feature-staking-impl:compileDebugKotlin \
  :feature-polkaswap-impl:compileDebugKotlin \
  :app:compileDebugKotlin \
  --no-daemon --console=plain --stacktrace
```

The public XCM route catalog and unsupported-path guards also pass:

```
./gradlew \
  :public-shared-features-xcm:testDebugUnitTest \
  :runtime:testDebugUnitTest \
  :core-db:compileDebugKotlin \
  :feature-wallet-impl:compileDebugKotlin \
  --no-daemon --console=plain --stacktrace
```

The wallet and staking anonymous fee-preview regression guards also pass:

```
./gradlew \
  :feature-wallet-impl:testDebugUnitTest \
  :feature-staking-impl:testDebugUnitTest \
  --no-daemon --console=plain --stacktrace
```

The broader debug build and unit suites also pass locally with the same
environment:

```
./gradlew :app:assembleDebug testDebugUnitTest --no-daemon --console=plain --stacktrace
```

The local `fearless-utils` extrinsic tests pass in library-only mode:

```
FEARLESS_UTILS_SKIP_RUST_PLUGIN=true \
FEARLESS_UTILS_LIBRARY_ONLY=true \
./gradlew \
  :fearless-utils:testDebugUnitTest \
  --tests 'jp.co.soramitsu.fearless_utils.runtime.extrinsic.*' \
  --no-daemon --console=plain --stacktrace
```
