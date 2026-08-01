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

### Reproducible From Vendored Source

`libsodium.so` is shipped for Android ABIs under `app/src/main/jniLibs`.
The ISC-licensed libsodium 1.0.19 source from
`https://github.com/jedisct1/libsodium` is vendored under
`third_party/libsodium`, and
`scripts/build-libsodium.sh` rebuilds the checked-in binaries with the Android
16 KB page-size linker flags.

Current checksums:

| File | SHA-256 |
| --- | --- |
| `app/src/main/jniLibs/arm64-v8a/libsodium.so` | `939ba2865a93abe39c4d6a29802419b36b1bfd0b320396eeaccd4d660468a664` |
| `app/src/main/jniLibs/armeabi-v7a/libsodium.so` | `8bcd304a813f3d814793c8553cd6a8814b64b2c188d66e8462f82053d7855343` |
| `app/src/main/jniLibs/x86/libsodium.so` | `a6cd7f654ff400d080b16b3e794971b2c2b2cbc12bf0cee444f7e2ea950839ae` |
| `app/src/main/jniLibs/x86_64/libsodium.so` | `a8b52ce229d18338b9f0b0f4d2396078582c9742d771367394b8e9b3cbabb81c` |

### Reproducible From Open Source

`libsr25519java.so` is built from the open-source
`soramitsu/fearless-utils-Android` repository:

- source repository: `https://github.com/soramitsu/fearless-utils-Android`
- source commit: `7500809f33243ee47ecb2ec8563fc284ac4de0d6`
- source path: `sr25519-java`
- Gradle module: `:fearless-utils:cargoBuild`
- Android NDK: `28.0.12674087`
- linker flags: `-Wl,-z,common-page-size=4096` and
  `-Wl,-z,max-page-size=16384` from the source repo `.cargo/config.toml`

The checked-in binaries expose both legacy `fearless_utils` JNI names and the
`shared_utils` JNI names used by the wallet app. Rebuild and copy them with:

```
FEARLESS_UTILS_ANDROID_PATH=../fearless-utils-Android ./scripts/build-sr25519.sh
```

Current checksums:

| File | SHA-256 |
| --- | --- |
| `app/src/main/jniLibs/arm64-v8a/libsr25519java.so` | `a37f031de78b841dab6a8c69900a64fe427d53d2a4f77d8f3c059caab5849c12` |
| `app/src/main/jniLibs/armeabi-v7a/libsr25519java.so` | `d217b2511bf2c7f90ea29879d5ecad86c62d95a3ee2dd412fbb0f6d804e5f3de` |
| `app/src/main/jniLibs/x86/libsr25519java.so` | `56f26fe8e7e55026cf36be6c22a62037389d4f59cc8ee35d6bb295b5dc5441aa` |
| `app/src/main/jniLibs/x86_64/libsr25519java.so` | `b024dcc2ebf236f21d329e3d637a6fdd39746ad1dc0e0ccc99143279617946dc` |

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

`debug-keystore.jks` has been removed from source control. Use the Android
default generated debug keystore for local builds and CI-provided keystores for
release signing.

Checked-in `google-services.json` files must stay on the `fearless-public`
placeholder project with empty API keys and no OAuth certificate hashes. Release
CI restores the real `app/src/release/google-services.json` from
`GOOGLE_SERVICES_RELEASE_JSON_B64`.
