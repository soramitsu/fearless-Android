#!/usr/bin/env bash
set -euo pipefail

RELEASE_MODE=false
UNSIGNED_RELEASE_MODE=false
STRICT_PROVENANCE=false

for arg in "$@"; do
  case "$arg" in
    --release)
      RELEASE_MODE=true
      ;;
    --unsigned-release)
      RELEASE_MODE=true
      UNSIGNED_RELEASE_MODE=true
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

if [[ "$RELEASE_MODE" == true && "$STRICT_PROVENANCE" != true ]]; then
  fail "Release mode requires --strict-provenance."
fi

cd "$(dirname "$0")/.."

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  fail "Run from a Git checkout."
fi

PROVENANCE_DOC="${PUBLIC_ARTIFACT_PROVENANCE_DOC:-docs/binary-provenance.md}"
DEFAULT_PROVENANCE_DOC="docs/binary-provenance.md"
GRADLE_DISTRIBUTION_SHA256="8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b"
SR25519_SOURCE_COMMIT="7500809f33243ee47ecb2ec8563fc284ac4de0d6"
EXPECTED_DOCUMENTED_BINARY_COUNT=9

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

require_tracked_file() {
  local path="$1"
  [[ -f "$path" ]] || fail "Strict provenance requires $path."
  git ls-files --error-unmatch -- "$path" >/dev/null 2>&1 ||
    fail "Strict provenance requires $path to be tracked."
}

require_provenance_text() {
  local value="$1"
  grep -Fq -- "$value" "$PROVENANCE_DOC" ||
    fail "$PROVENANCE_DOC is missing strict provenance evidence: $value"
}

check_documented_binary() {
  local path="$1"
  local checksum
  local expected_row occurrences
  checksum="$(expected_checksum "$path")"
  [[ -n "$checksum" ]] || fail "$path has no strict provenance checksum."
  expected_row="| \`$path\` | \`$checksum\` |"
  occurrences="$(grep -Fxc -- "$expected_row" "$PROVENANCE_DOC" || true)"
  [[ "$occurrences" -eq 1 ]] ||
    fail "$PROVENANCE_DOC must contain exactly one strict provenance row for $path; found $occurrences."
}

check_strict_provenance_contract() {
  [[ -s "$PROVENANCE_DOC" ]] || fail "Strict provenance document is missing or empty: $PROVENANCE_DOC"

  if [[ "$PROVENANCE_DOC" == "$DEFAULT_PROVENANCE_DOC" ]]; then
    require_tracked_file "$DEFAULT_PROVENANCE_DOC"
  elif [[ "$RELEASE_MODE" == true ]]; then
    fail "Release mode may not override PUBLIC_ARTIFACT_PROVENANCE_DOC."
  fi

  local documented_binary_count
  documented_binary_count="$(grep -Ec '^\| `[^`]+` \| `[0-9a-f]{64}` \|$' "$PROVENANCE_DOC" || true)"
  [[ "$documented_binary_count" -eq "$EXPECTED_DOCUMENTED_BINARY_COUNT" ]] ||
    fail "$PROVENANCE_DOC must contain exactly $EXPECTED_DOCUMENTED_BINARY_COUNT allowlisted binary rows; found $documented_binary_count."

  local path
  for path in \
    gradle/wrapper/gradle-wrapper.jar \
    app/src/main/jniLibs/arm64-v8a/libsodium.so \
    app/src/main/jniLibs/armeabi-v7a/libsodium.so \
    app/src/main/jniLibs/x86/libsodium.so \
    app/src/main/jniLibs/x86_64/libsodium.so \
    app/src/main/jniLibs/arm64-v8a/libsr25519java.so \
    app/src/main/jniLibs/armeabi-v7a/libsr25519java.so \
    app/src/main/jniLibs/x86/libsr25519java.so \
    app/src/main/jniLibs/x86_64/libsr25519java.so; do
    require_tracked_file "$path"
    check_checksum "$path"
    check_documented_binary "$path"
  done

  for path in \
    scripts/build-libsodium.sh \
    scripts/build-sr25519.sh \
    third_party/libsodium/LICENSE \
    third_party/libsodium/configure.ac \
    third_party/libsodium/autogen.sh \
    gradle/wrapper/gradle-wrapper.properties; do
    require_tracked_file "$path"
  done

  [[ -x scripts/build-libsodium.sh ]] || fail "scripts/build-libsodium.sh must be executable."
  [[ -x scripts/build-sr25519.sh ]] || fail "scripts/build-sr25519.sh must be executable."
  [[ -x third_party/libsodium/autogen.sh ]] || fail "third_party/libsodium/autogen.sh must be executable."
  if git ls-files --error-unmatch -- third_party/libsodium/configure >/dev/null 2>&1; then
    fail "Generated third_party/libsodium/configure must not be tracked; rebuild it from configure.ac."
  fi

  require_provenance_text 'third_party/libsodium'
  require_provenance_text 'https://github.com/jedisct1/libsodium'
  require_provenance_text 'libsodium 1.0.19'
  require_provenance_text 'scripts/build-libsodium.sh'
  require_provenance_text 'scripts/build-sr25519.sh'
  require_provenance_text 'bash ./scripts/test-public-artifact-provenance-audit.sh'
  require_provenance_text './scripts/audit-public-artifacts.sh --strict-provenance'
  require_provenance_text './scripts/audit-public-artifacts.sh --release --strict-provenance'
  require_provenance_text "$SR25519_SOURCE_COMMIT"
  require_provenance_text "$GRADLE_DISTRIBUTION_SHA256"

  grep -Fq "EXPECTED_SOURCE_COMMIT=\"$SR25519_SOURCE_COMMIT\"" scripts/build-sr25519.sh ||
    fail "scripts/build-sr25519.sh must enforce the documented source commit."
  grep -Fq './autogen.sh' scripts/build-libsodium.sh ||
    fail "scripts/build-libsodium.sh must bootstrap a clean vendored source checkout."
  grep -Fq 'AC_INIT([libsodium],[1.0.19]' third_party/libsodium/configure.ac ||
    fail "Vendored libsodium source version is missing or changed."
  grep -Fq "distributionSha256Sum=$GRADLE_DISTRIBUTION_SHA256" gradle/wrapper/gradle-wrapper.properties ||
    fail "Gradle 9.0 distributionSha256Sum is missing or changed."
  grep -Fq "FEARLESS_UTILS_COMMIT: $SR25519_SOURCE_COMMIT" .github/workflows/android-release.yml ||
    fail "Android release workflow must pin the documented fearless-utils source commit."
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
  local required_names=(
    RAMP_TOKEN_RELEASE
    WALLET_CONNECT_PROJECT_ID
  )
  if [[ "$UNSIGNED_RELEASE_MODE" == false ]]; then
    required_names+=(
      CI_KEYSTORE_PATH
      CI_KEYSTORE_PASS
      CI_KEYSTORE_KEY_ALIAS
      CI_KEYSTORE_KEY_PASS
    )
  fi
  for name in "${required_names[@]}"; do
    if [[ -z "${!name:-}" ]]; then
      missing+=("$name")
    fi
  done

  if (( ${#missing[@]} > 0 )); then
    fail "Release mode is missing required environment variables: ${missing[*]}"
  fi

  if [[ "$UNSIGNED_RELEASE_MODE" == true ]]; then
    for name in \
      CI_KEYSTORE_PATH \
      CI_KEYSTORE_PASS \
      CI_KEYSTORE_KEY_ALIAS \
      CI_KEYSTORE_KEY_PASS; do
      [[ -z "${!name:-}" ]] ||
        fail "Unsigned release audit forbids every signing input."
    done
  else
    [[ -s "$CI_KEYSTORE_PATH" ]] ||
      fail "CI_KEYSTORE_PATH does not point to a readable keystore."
  fi
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

if [[ "$STRICT_PROVENANCE" == true ]]; then
  check_strict_provenance_contract
fi

if [[ "$RELEASE_MODE" == true ]]; then
  check_release_google_services
  check_required_release_env
fi

if [[ "$STRICT_PROVENANCE" == true ]]; then
  log "Public artifact audit passed with strict provenance."
else
  log "Public artifact audit passed."
fi
