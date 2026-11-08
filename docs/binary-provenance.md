# Binary Provenance

This repository should not rely on unexplained generated or vendored binaries.
Run this audit before release hardening changes:

```
git ls-files '*.jar' '*.aar' '*.so' '*.a' '*.dylib' '*.framework/**' '*.xcframework/**' '*.wasm' '*.keystore' '*.jks' '*.mobileprovision' '*.p12' '*.p8' '*.pem' '*.provisionprofile'
```

CI and local validation run the adversarial self-test followed by the strict
tracked-document gate:

```
bash ./scripts/test-public-artifact-provenance-audit.sh
./scripts/audit-public-artifacts.sh --strict-provenance
```

Release builds must restore private overlays from CI secrets and then run the
strict release audit:

```
./scripts/restore-release-overlays.sh
./scripts/audit-public-artifacts.sh --release --strict-provenance
```

## Current Findings

### Built From Source During Every Build

`libsr25519java.so` holds every sr25519 key operation of the app (Polkadot,
Kusama and SORA accounts). No prebuilt copy is committed. The Gradle task
`:runtime-utils:buildSr25519AndroidLibraries` builds it for all four ABIs from
the app-owned Rust crate `runtime-utils/native/sr25519` (imported from
`soramitsu/fearless-utils-Android`, path `sr25519-java`) by running
`scripts/build-sr25519.py`, and every variant packages that output.

- crate: schnorrkel 0.11.5 (curve25519-dalek 4.1.3), jni 0.22.4, zeroize 1.9.1,
  with every transitive crate locked in `Cargo.lock` and built with `--locked`
- toolchain: `rustc 1.99.1 (58cf8f9c3 2026-10-13)`, pinned by
  `runtime-utils/native/sr25519/rust-toolchain.toml`
- `cargo-ndk 4.1.2`, Android NDK `30.0.16248370` (r30, pinned as `ndkVersion` in
  `runtime-utils/build.gradle`), Android API 26
- linker flags `-Wl,-z,common-page-size=4096` and `-Wl,-z,max-page-size=16384`
  for 16 KB LOAD alignment on every ABI
- reproducibility: one codegen unit, fat LTO, stripped symbols, a cleaned build
  environment, `--remap-path-prefix` for the cargo home, the Rust toolchain, the
  crate and the cargo target directory, and a fixed `SOURCE_DATE_EPOCH`
- the same bytes on every build machine: cargo's `-C metadata` hashes the
  `host:` line of `rustc -vV`, so macOS and Linux builds differed. The rustc
  wrapper `runtime-utils/native/sr25519/stable-crate-metadata.py` (set by
  `scripts/build-sr25519.py`) replaces each value with a hash of the package,
  version, crate name and types, edition, cfgs, target and package-relative
  source path; arm64 and x86_64 macOS hosts build byte-identical libraries

`runtime-utils/native/sr25519/reproducible-build.json` records the reviewed
SHA-256 of each ABI and the exact JNI export list. Every build checks each
library's 16 KB LOAD alignment and exports with the NDK's `llvm-readelf` and
fails on any SHA-256 mismatch (`-Psr25519.allowHashMismatch=true` downgrades
that to a warning for local debug builds only; release graphs refuse it).
`scripts/verify-android-aab-identity.sh` requires the same values inside the
AAB.

```
./gradlew :runtime-utils:buildSr25519AndroidLibraries
./gradlew :runtime-utils:verifySr25519ReproducibleBuild   # two clean builds, byte-identical
python3 scripts/build-sr25519.py --reproducibility-check   # the same without Gradle
./gradlew :runtime-utils:recordSr25519NativeHashes         # after an intended source change
```

Reviewed checksums:

| ABI | SHA-256 |
| --- | --- |
| arm64-v8a | `453b2acb2f8372145cddd9783338a1cf600499dbe5cea148cb53849f14ed07da` |
| armeabi-v7a | `8aff40c7748cadd375a9a44381746e37d9c97d589b8868a9d9fa14d300edae14` |
| x86 | `e929646e3021c2b36bd8ed13144024411711b3b8d953859afa43e9eb3eed3f9c` |
| x86_64 | `14376c77935b4bc0e6c8bae755bc47caaa29d3f6acb65259e36e0827b540ecea` |

Host builds of the same crate back the JVM unit tests
(`Sr25519NativeTest`, `HostSr25519KeypairDerivationTest`). The library shipped
up to Android 4.3.1 was a prebuilt schnorrkel 0.9.1 binary (source commit
`7500809f33243ee47ecb2ec8563fc284ac4de0d6`, curve25519-dalek 2.1.3 with
RUSTSEC-2024-0344); `scripts/verify-sr25519-legacy-compatibility.sh` rebuilds
it from Git history and checks that both produce identical keys and derivations
and accept each other's signatures.

### Removed

`libsodium.so` (four ABIs), its vendored libsodium 1.0.19 source under
`third_party/libsodium` and `scripts/build-libsodium.sh` were removed in 4.3.2:
no code, manifest or dependency loaded the library.

### Standard Build Wrapper

`gradle/wrapper/gradle-wrapper.jar` is the standard Gradle 8.13 wrapper
bootstrap jar, verified against Gradle's published wrapper checksum. It
downloads the Gradle 9.0 binary distribution, whose published SHA-256 is pinned
as `distributionSha256Sum` in
`gradle/wrapper/gradle-wrapper.properties`.

Current checksum:

| File | SHA-256 |
| --- | --- |
| `gradle/wrapper/gradle-wrapper.jar` | `81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f` |

The pinned Gradle 9.0 binary distribution SHA-256 is
`8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b`.

### Replaced With Source

`feature-wallet-impl/libs/pushpayment-core-sdk-2.0.6.jar` has been removed.
CBDC QR parsing now lives in
`feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/domain/qr/CbdcQrParser.kt`
with unit coverage under
`feature-wallet-impl/src/test/java/jp/co/soramitsu/wallet/impl/domain/qr/CbdcQrParserTest.kt`.

`runtime-utils/libs/polkaj-scale-0.2.3.jar` (SHA-256
`bea0f3904fef83497b9e5802f36408663a4aba462bf63ded38b89a5f22befa4d`) has been
removed in 4.3.2. The SCALE codec it held is built from source in
`runtime-utils/src/main/java/io/emeraldpay/polkaj/scale`: upstream polkaj v0.2.3
(Apache-2.0, commit `3227a8ef605cd65b29b1cb2648a02e3a2a9c2419`) plus the Java 8
changes and the Short, Long and String codecs the jar carried (see `NOTICE`).
Before the jar was deleted, `PolkajScaleJarDifferentialTest` ran every reader and
writer of both over the same random input; `PolkajScaleGoldenTest` now replays
the jar's recorded transcript.

`debug-keystore.jks` has been removed from source control. Use the Android
default generated debug keystore for local builds and CI-provided keystores for
release signing.

No `google-services.json` may be tracked or present under `app/`: Firebase and
Google Play services are not part of the app's own code since 4.3.2, and
`scripts/audit-public-artifacts.sh` refuses such a file (in release mode even an
untracked one).
