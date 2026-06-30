#!/usr/bin/env bash
set -euo pipefail

RELEASE_MODE=false
STRICT_PROVENANCE=false

for arg in "$@"; do
  case "$arg" in
    --release)
      RELEASE_MODE=true
      ;;
    --strict-provenance)
      STRICT_PROVENANCE=true
      ;;
    *)
      echo "Unknown argument: $arg" >&2
      exit 2
      ;;
  esac
done

log() { echo "[artifact-audit] $*"; }
warn() { echo "[artifact-audit][warn] $*" >&2; }
fail() {
  echo "[artifact-audit][error] $*" >&2
  exit 1
}

cd "$(dirname "$0")/.."

if [[ ! -d .git ]]; then
  fail "Run from a Git checkout."
fi

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

expected_checksum() {
  case "$1" in
    gradle/wrapper/gradle-wrapper.jar) echo "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f" ;;
    app/src/main/jniLibs/arm64-v8a/libsodium.so) echo "939ba2865a93abe39c4d6a29802419b36b1bfd0b320396eeaccd4d660468a664" ;;
    app/src/main/jniLibs/armeabi-v7a/libsodium.so) echo "8bcd304a813f3d814793c8553cd6a8814b64b2c188d66e8462f82053d7855343" ;;
    app/src/main/jniLibs/x86/libsodium.so) echo "a6cd7f654ff400d080b16b3e794971b2c2b2cbc12bf0cee444f7e2ea950839ae" ;;
    app/src/main/jniLibs/x86_64/libsodium.so) echo "a8b52ce229d18338b9f0b0f4d2396078582c9742d771367394b8e9b3cbabb81c" ;;
    app/src/main/jniLibs/arm64-v8a/libsr25519java.so) echo "a37f031de78b841dab6a8c69900a64fe427d53d2a4f77d8f3c059caab5849c12" ;;
    app/src/main/jniLibs/armeabi-v7a/libsr25519java.so) echo "d217b2511bf2c7f90ea29879d5ecad86c62d95a3ee2dd412fbb0f6d804e5f3de" ;;
    app/src/main/jniLibs/x86/libsr25519java.so) echo "56f26fe8e7e55026cf36be6c22a62037389d4f59cc8ee35d6bb295b5dc5441aa" ;;
    app/src/main/jniLibs/x86_64/libsr25519java.so) echo "b024dcc2ebf236f21d329e3d637a6fdd39746ad1dc0e0ccc99143279617946dc" ;;
    *) echo "" ;;
  esac
}

check_checksum() {
  local path="$1"
  local expected
  expected="$(expected_checksum "$path")"
  if [[ -z "$expected" ]]; then
    return 1
  fi

  local actual
  actual="$(sha256 "$path")"
  if [[ "$actual" != "$expected" ]]; then
    fail "$path checksum changed: expected $expected, got $actual"
  fi

  return 0
}

check_public_google_services() {
  local path="$1"

  grep -q '"project_id"[[:space:]]*:[[:space:]]*"fearless-public"' "$path" ||
    fail "$path must use the checked-in fearless-public Firebase placeholder project."

  if grep -Eq '"current_key"[[:space:]]*:[[:space:]]*"[^"]+"' "$path"; then
    fail "$path contains a non-empty Firebase API key. Commit only placeholder Firebase config."
  fi

  if grep -q '"certificate_hash"' "$path"; then
    fail "$path contains OAuth certificate hashes. Commit only placeholder Firebase config."
  fi
}

check_release_google_services() {
  local path="app/src/release/google-services.json"

  [[ -s "$path" ]] || fail "$path is required for release mode."

  if grep -q '"project_id"[[:space:]]*:[[:space:]]*"fearless-public"' "$path"; then
    fail "$path still points at the public placeholder project in release mode."
  fi

  grep -Eq '"current_key"[[:space:]]*:[[:space:]]*"[^"]+"' "$path" ||
    fail "$path must contain release Firebase API keys restored from CI secrets."
}

check_required_release_env() {
  local missing=()
  local name
  for name in \
    CI_KEYSTORE_PATH \
    CI_KEYSTORE_PASS \
    CI_KEYSTORE_KEY_ALIAS \
    CI_KEYSTORE_KEY_PASS \
    CI_PLAY_KEY \
    MOONPAY_PRODUCTION_SECRET \
    RAMP_TOKEN_RELEASE \
    WALLET_CONNECT_PROJECT_ID; do
    if [[ -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    fail "Release mode is missing required environment variables: ${missing[*]}"
  fi

  [[ -s "$CI_KEYSTORE_PATH" ]] || fail "CI_KEYSTORE_PATH does not point to a readable keystore."
  [[ -s "$CI_PLAY_KEY" ]] || fail "CI_PLAY_KEY does not point to a readable Play service-account JSON."
}

while IFS= read -r -d '' path; do
  [[ -e "$path" ]] || continue

  case "$path" in
    *.jks|*.keystore|*.p12|*.p8|*.pem|*.mobileprovision|*.provisionprofile)
      fail "$path is tracked private release material. Keep it in CI/local overlays only."
      ;;
    */google-services.json)
      if [[ "$RELEASE_MODE" == false ]]; then
        check_public_google_services "$path"
      fi
      ;;
    feature-wallet-impl/libs/pushpayment-core-sdk-*.jar)
      fail "$path must not be vendored. The CBDC QR parser is implemented from source."
      ;;
    *.jar|*.aar|*.so|*.a|*.dylib|*.wasm|*.framework/*|*.xcframework/*)
      check_checksum "$path" || fail "$path is a tracked binary without an allowlisted checksum/provenance entry."
      ;;
  esac
done < <(git ls-files -z -- \
  '*.jar' '*.aar' '*.so' '*.a' '*.dylib' '*.framework/**' '*.xcframework/**' '*.wasm' \
  '*.keystore' '*.jks' '*.mobileprovision' '*.p12' '*.p8' '*.pem' '*.provisionprofile' \
  '*/google-services.json')

if [[ "$RELEASE_MODE" == true ]]; then
  check_release_google_services
  check_required_release_env
fi

log "Public artifact audit passed."
