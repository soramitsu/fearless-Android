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
SR25519_CRATE="runtime-utils/native/sr25519"
SR25519_MANIFEST="$SR25519_CRATE/reproducible-build.json"
EXPECTED_DOCUMENTED_BINARY_COUNT=2

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
    runtime-utils/libs/polkaj-scale-0.2.3.jar) echo "bea0f3904fef83497b9e5802f36408663a4aba462bf63ded38b89a5f22befa4d" ;;
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
    runtime-utils/libs/polkaj-scale-0.2.3.jar \
    gradle/wrapper/gradle-wrapper.jar; do
    require_tracked_file "$path"
    check_checksum "$path"
    check_documented_binary "$path"
  done

  for path in \
    scripts/build-sr25519.py \
    "$SR25519_CRATE/Cargo.toml" \
    "$SR25519_CRATE/Cargo.lock" \
    "$SR25519_CRATE/rust-toolchain.toml" \
    "$SR25519_CRATE/.cargo/config.toml" \
    "$SR25519_CRATE/src/lib.rs" \
    "$SR25519_MANIFEST" \
    gradle/wrapper/gradle-wrapper.properties; do
    require_tracked_file "$path"
  done

  [[ -x scripts/build-sr25519.py ]] || fail "scripts/build-sr25519.py must be executable."

  require_provenance_text 'scripts/build-sr25519.py'
  require_provenance_text "$SR25519_MANIFEST"
  require_provenance_text 'bash ./scripts/test-public-artifact-provenance-audit.sh'
  require_provenance_text './scripts/audit-public-artifacts.sh --strict-provenance'
  require_provenance_text './scripts/audit-public-artifacts.sh --release --strict-provenance'
  require_provenance_text "$GRADLE_DISTRIBUTION_SHA256"

  grep -Fq "distributionSha256Sum=$GRADLE_DISTRIBUTION_SHA256" gradle/wrapper/gradle-wrapper.properties ||
    fail "Gradle 9.0 distributionSha256Sum is missing or changed."
  check_sr25519_source_build
}

# libsr25519java.so is built from source by Gradle. The repository must hold the
# crate, the pinned toolchain and the reviewed per-ABI SHA-256 values, and no
# prebuilt copy; the provenance document repeats every reviewed SHA-256.
check_sr25519_source_build() {
  if git ls-files -- '*libsr25519java*' '*libsodium*' | grep -q .; then
    fail "Prebuilt sr25519 or sodium libraries must not be tracked; libsr25519java.so is built from $SR25519_CRATE."
  fi
  python3 -I - "$SR25519_MANIFEST" "$PROVENANCE_DOC" "$SR25519_CRATE/rust-toolchain.toml" \
    runtime-utils/build.gradle <<'PY' || fail "The sr25519 reproducible-build contract is incomplete."
import json
import re
import sys

manifest_path, document_path, toolchain_path, gradle_path = sys.argv[1:]
with open(manifest_path, encoding="utf-8") as handle:
    manifest = json.load(handle)
with open(document_path, encoding="utf-8") as handle:
    document = handle.read()
with open(toolchain_path, encoding="utf-8") as handle:
    toolchain = handle.read()
with open(gradle_path, encoding="utf-8") as handle:
    gradle = handle.read()
errors = []
abis = manifest.get("abis", {})
if sorted(abis) != ["arm64-v8a", "armeabi-v7a", "x86", "x86_64"]:
    errors.append("manifest must list exactly the four Android ABIs")
for abi, entry in sorted(abis.items()):
    digest = entry.get("sha256", "")
    if not re.fullmatch(r"[0-9a-f]{64}", digest):
        errors.append(f"malformed SHA-256 for {abi}")
    elif document.count(f"| {abi} | `{digest}` |") != 1:
        errors.append(f"provenance document must list the {abi} SHA-256 exactly once")
version = re.fullmatch(r"rustc (\d+\.\d+\.\d+) \(.*\)", manifest.get("rustc", ""))
if not version or f'channel = "{version.group(1)}"' not in toolchain:
    errors.append("rust-toolchain.toml must pin the manifest's rustc version")
if f'ndkVersion "{manifest.get("ndkVersion")}"' not in gradle:
    errors.append("runtime-utils/build.gradle must pin the manifest's NDK version")
for field in ("rustc", "cargoNdk", "ndkVersion"):
    if str(manifest.get(field)) not in document:
        errors.append(f"provenance document must name the manifest {field}")
for message in errors:
    print("[artifact-audit][error] " + message, file=sys.stderr)
sys.exit(1 if errors else 0)
PY
}

# Firebase and Google Play services are not part of the app (4.3.2): no Google Services
# configuration may be tracked, and none may be present, tracked or not, when a release is built.
check_no_google_services_configuration() {
  local present
  present="$(find app -maxdepth 3 -name google-services.json -print 2>/dev/null | LC_ALL=C sort)"
  [[ -z "$present" ]] ||
    fail "Google Services configuration is not part of the app; remove: $(printf '%s' "$present" | tr '\n' ' ')"
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
    google-services.json|*/google-services.json)
      fail "$path is a tracked Google Services configuration. Firebase is not part of the app."
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
  'google-services.json' '*/google-services.json')

if [[ "$STRICT_PROVENANCE" == true ]]; then
  check_strict_provenance_contract
fi

if [[ "$RELEASE_MODE" == true ]]; then
  check_no_google_services_configuration
  check_required_release_env
fi

if [[ "$STRICT_PROVENANCE" == true ]]; then
  log "Public artifact audit passed with strict provenance."
else
  log "Public artifact audit passed."
fi
