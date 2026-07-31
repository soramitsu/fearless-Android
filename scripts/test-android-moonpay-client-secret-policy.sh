#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VERIFY="$ROOT/scripts/verify-android-moonpay-client-secret-policy.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
positive_count=0
negative_count=0

make_fixture() {
  rm -rf "$TMP/fixture"
  mkdir -p \
    "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di" \
    "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken" \
    "$TMP/fixture/common/src/main/java/jp/co/soramitsu/common/utils" \
    "$TMP/fixture/.github/workflows" \
    "$TMP/fixture/app" \
    "$TMP/fixture/docs" \
    "$TMP/fixture/third_party/vendor/src" \
    "$TMP/fixture/scripts"

  printf '%s\n' 'android { buildFeatures { buildConfig = true } }' \
    > "$TMP/fixture/feature-wallet-impl/build.gradle"
  cp \
    "$ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt" \
    "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
  cp \
    "$ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/ExternalProvider.kt" \
    "$ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/RampProvider.kt" \
    "$ROOT/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/CoinbaseProvider.kt" \
    "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/"
  printf '%s\n' 'android { }' > "$TMP/fixture/app/build.gradle"
  printf '%s\n' 'fun safeHash() = Unit' \
    > "$TMP/fixture/common/src/main/java/jp/co/soramitsu/common/utils/CryptoUtils.kt"
  printf '%s\n' 'jobs: {}' > "$TMP/fixture/.github/workflows/android-ci.yml"
  printf '%s\n' 'jobs: {}' > "$TMP/fixture/.github/workflows/android-release.yml"
  printf '%s\n' '#!/usr/bin/env bash' 'exit 0' \
    > "$TMP/fixture/scripts/audit-public-artifacts.sh"
  printf '%s\n' \
    '# Policy implementation contains its own forbidden-token patterns.' \
    'pattern="MOONPAY_PRIVATE_KEY|HmacSHA256"' \
    > "$TMP/fixture/scripts/verify-android-moonpay-client-secret-policy.sh"
  printf '%s\n' \
    '# Adversarial fixture strings belong only in this dedicated shell test.' \
    'fixture="MOONPAY_PRODUCTION_SECRET"' \
    > "$TMP/fixture/scripts/test-android-moonpay-client-secret-policy.sh"
  printf '%s\n' \
    'Historical incident note: MOONPAY_PRODUCTION_SECRET was removed.' \
    > "$TMP/fixture/docs/moonpay-incident.md"
  printf '%s\n' 'void HmacSHA256(void);' \
    > "$TMP/fixture/third_party/vendor/src/crypto.c"

  git -C "$TMP/fixture" init -q
  git -C "$TMP/fixture" add -A
}

expect_rejected() {
  local name="$1"

  git -C "$TMP/fixture" add -A
  if "$VERIFY" "$TMP/fixture" >"$TMP/out" 2>&1; then
    echo "[moonpay-client-policy-test][error] accepted adversarial case: $name" >&2
    exit 1
  fi
  echo "[moonpay-client-policy-test] rejected: $name"
  negative_count=$((negative_count + 1))
}

make_fixture
"$VERIFY" "$TMP/fixture"
echo "[moonpay-client-policy-test] accepted: fail-closed fixture"
positive_count=$((positive_count + 1))

make_fixture
printf '%s\n' \
  'buildConfigField "String", "MOONPAY_PRIVATE_KEY", readSecretInQuotes("value")' \
  >> "$TMP/fixture/feature-wallet-impl/build.gradle"
expect_rejected "private signing key in BuildConfig"

make_fixture
printf '%s\n' \
  'buildConfigField "String", "X", readSecretInQuotes("MOONPAY_PRODUCTION_SECRET")' \
  >> "$TMP/fixture/feature-wallet-impl/build.gradle"
expect_rejected "production signing secret required by Gradle"

make_fixture
printf '%s\n' \
  'class MoonPayProvider {' \
  '  fun create(secret: String) = "moonpay".hmacSHA256(secret) + "&signature="' \
  '}' \
  > "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/MoonPayProvider.kt"
expect_rejected "client-side HMAC provider restored"

make_fixture
printf '%s\n' 'fun String.hmacSHA256(secret: String) = HmacSHA256(secret)' \
  > "$TMP/fixture/common/src/main/java/jp/co/soramitsu/common/utils/CryptoUtils.kt"
expect_rejected "generic MoonPay HMAC helper restored"

make_fixture
printf '%s\n' 'MoonPayProvider(privateKey = "embedded")' \
  >> "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_rejected "MoonPay re-registered in dependency injection"

make_fixture
printf '%s\n' 'env:' '  MOONPAY_PRODUCTION_SECRET: secret' \
  > "$TMP/fixture/.github/workflows/android-release.yml"
expect_rejected "release workflow secret restored"

make_fixture
printf '%s\n' 'required=(MOONPAY_TEST_SECRET)' \
  >> "$TMP/fixture/scripts/audit-public-artifacts.sh"
expect_rejected "release audit requires a client signing secret"

make_fixture
mkdir -p "$TMP/fixture/feature-staking-impl"
printf '%s\n' \
  'val moonpayClientSecret = providers.gradleProperty("forbidden")' \
  > "$TMP/fixture/feature-staking-impl/build.gradle.kts"
expect_rejected "alternate module Gradle signing secret"

make_fixture
mkdir -p \
  "$TMP/fixture/feature-tonconnect-impl/src/release/kotlin/example"
printf '%s\n' \
  'fun signMoonPay(value: String, key: String) = value.hmac(key)' \
  > "$TMP/fixture/feature-tonconnect-impl/src/release/kotlin/example/CheckoutSigner.kt"
expect_rejected "alternate module release-source signer"

make_fixture
mkdir -p "$TMP/fixture/buildSrc/src/main/kotlin"
printf '%s\n' \
  'val keyName = "MOONPAY_HMAC_KEY"' \
  > "$TMP/fixture/buildSrc/src/main/kotlin/MobileSecrets.kt"
expect_rejected "buildSrc signing-key configuration"

make_fixture
printf '%s\n' 'jobs:' '  leak:' '    env:' \
  '      MOONPAY_SIGNING_KEY: forbidden' \
  > "$TMP/fixture/.github/workflows/alternate.yaml"
expect_rejected "alternate workflow signing key"

make_fixture
mkdir -p "$TMP/fixture/versioning"
printf '%s\n' 'MOONPAY_API_SECRET=forbidden' \
  > "$TMP/fixture/versioning/release.properties"
expect_rejected "versioning/build property signing secret"

make_fixture
printf '%s\n' \
  'val key = "moon" + "pay" + "Sign" + "ing" + "Key"' \
  > "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/SplitCredential.kt"
expect_rejected "split-token signing identifier"

make_fixture
mkdir -p "$TMP/fixture/versioning"
printf '%s\n' 'encoded.provider=bW9vbnBheQ==' \
  > "$TMP/fixture/versioning/release.properties"
expect_rejected "base64-encoded MoonPay identifier"

make_fixture
printf '\000binary\000MOONPAY_PRIVATE_KEY\000payload\000' \
  > "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/data/buyToken/provider.bin"
expect_rejected "binary resource signing identifier"

make_fixture
perl -0pi -e \
  's/RampProvider\(host = BuildConfig\.RAMP_HOST, apiToken = BuildConfig\.RAMP_TOKEN\),/RampProvider(host = BuildConfig.RAMP_HOST, apiToken = BuildConfig.RAMP_TOKEN),\\n                UnreviewedProvider(),/' \
  "$TMP/fixture/feature-wallet-impl/src/main/java/jp/co/soramitsu/wallet/impl/di/WalletFeatureModule.kt"
expect_rejected "unreviewed provider registry entry"

[[ "$positive_count" == "1" ]] ||
  {
    echo "[moonpay-client-policy-test][error] expected 1 positive case; got $positive_count" >&2
    exit 1
  }
[[ "$negative_count" == "16" ]] ||
  {
    echo "[moonpay-client-policy-test][error] expected 16 negative cases; got $negative_count" >&2
    exit 1
  }

echo \
  "[moonpay-client-policy-test] PASS: $positive_count positive and $negative_count negative/adversarial cases"
