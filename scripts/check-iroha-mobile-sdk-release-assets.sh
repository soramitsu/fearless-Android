#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'USAGE'
Usage:
  scripts/check-iroha-mobile-sdk-release-assets.sh --release-dir <dir> [--version <version>]
  scripts/check-iroha-mobile-sdk-release-assets.sh --download --tag <tag> [--repo <owner/repo>]
  scripts/check-iroha-mobile-sdk-release-assets.sh --self-test

Validates the Android Iroha mobile SDK release assets produced by ../iroha:
  - iroha-mobile-sdk-android-<version>.zip
  - SHA256SUMS-android-<version>.txt or SHA256SUMS-all-<version>.txt
  - mobile-sdk-android-<version>.artifacts.json or mobile-sdk-all-<version>.artifacts.json

The Android zip must contain raw core/client/offline artifacts, native bridge
libraries for arm64-v8a and x86_64, and a versioned Maven repository tree under
maven/org/hyperledger/iroha/sdk.
USAGE
}

RELEASE_DIR=""
VERSION=""
DOWNLOAD=0
SELF_TEST=0
REPO="${IROHA_MOBILE_SDK_RELEASE_REPO:-hyperledger-iroha/iroha}"
TAG="${IROHA_MOBILE_SDK_RELEASE_TAG:-}"
SELF_TEST_TMP=""
VALIDATION_TMP_DIR=""
DOWNLOAD_TMP_DIR=""

cleanup_runtime() {
  local directory
  for directory in \
    "$SELF_TEST_TMP" \
    "$VALIDATION_TMP_DIR" \
    "$DOWNLOAD_TMP_DIR"; do
    if [[ -n "$directory" && -d "$directory" ]]; then
      rm -rf -- "$directory"
    fi
  done
}

trap cleanup_runtime EXIT
trap 'cleanup_runtime; exit 130' HUP INT TERM

while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-dir)
      shift
      RELEASE_DIR="${1:-}"
      ;;
    --release-dir=*)
      RELEASE_DIR="${1#*=}"
      ;;
    --version)
      shift
      VERSION="${1:-}"
      ;;
    --version=*)
      VERSION="${1#*=}"
      ;;
    --download)
      DOWNLOAD=1
      ;;
    --tag)
      shift
      TAG="${1:-}"
      ;;
    --tag=*)
      TAG="${1#*=}"
      ;;
    --repo)
      shift
      REPO="${1:-}"
      ;;
    --repo=*)
      REPO="${1#*=}"
      ;;
    --self-test)
      SELF_TEST=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[iroha-sdk-assets] ERROR: unexpected argument: $1" >&2
      usage >&2
      exit 64
      ;;
  esac
  shift
done

fail() {
  echo "[iroha-sdk-assets] ERROR: $*" >&2
  exit 1
}

hash_file() {
  local path="$1"
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 "$path" | awk '{print $1}'
  elif command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$path" | awk '{print $1}'
  else
    fail "shasum or sha256sum is required"
  fi
}

require_tool() {
  command -v "$1" >/dev/null 2>&1 || fail "$1 is required"
}

zip_entries() {
  unzip -Z1 "$1" 2>/dev/null || fail "not a readable zip archive: $1"
}

find_zip_entry() {
  local archive="$1"
  local pattern="$2"
  local label="$3"
  local entries entry
  entries="$(zip_entries "$archive")"
  entry="$(grep -E "$pattern" <<<"$entries" | head -n1 || true)"
  [[ -n "$entry" ]] || fail "missing $label in $(basename "$archive")"
  printf '%s' "$entry"
}

require_zip_entry() {
  find_zip_entry "$1" "$2" "$3" >/dev/null
}

validate_client_aar() {
  local android_zip="$1"
  local tmp_dir="$2"
  local client_aar_entry="$3"
  local client_aar="$tmp_dir/client-android-release.aar"

  unzip -p "$android_zip" "$client_aar_entry" > "$client_aar" || fail "unable to extract $client_aar_entry"
  require_zip_entry "$client_aar" '^AndroidManifest\.xml$' "client AAR manifest"
  require_zip_entry "$client_aar" '^classes\.jar$' "client AAR classes.jar"
  require_zip_entry "$client_aar" '^jni/arm64-v8a/libconnect_norito_bridge\.so$' "client AAR arm64 native bridge"
  require_zip_entry "$client_aar" '^jni/x86_64/libconnect_norito_bridge\.so$' "client AAR x86_64 native bridge"
}

infer_version() {
  local dir="$1"
  local files=()
  local file base
  while IFS= read -r file; do
    files+=("$file")
  done < <(find "$dir" -maxdepth 1 -type f -name 'iroha-mobile-sdk-android-*.zip' | sort)

  [[ ${#files[@]} -eq 1 ]] || fail "expected exactly one iroha-mobile-sdk-android-*.zip in $dir, found ${#files[@]}"
  base="$(basename "${files[0]}")"
  base="${base#iroha-mobile-sdk-android-}"
  printf '%s' "${base%.zip}"
}

download_release_assets() {
  [[ -n "$TAG" ]] || fail "--download requires --tag or IROHA_MOBILE_SDK_RELEASE_TAG"
  require_tool gh
  RELEASE_DIR="$(mktemp -d "${TMPDIR:-/tmp}/iroha-android-sdk-assets.XXXXXX")"
  DOWNLOAD_TMP_DIR="$RELEASE_DIR"
  VERSION="$TAG"
  gh release download "$TAG" \
    --repo "$REPO" \
    --dir "$RELEASE_DIR" \
    --pattern "iroha-mobile-sdk-android-${TAG}.zip" \
    --pattern "SHA256SUMS-android-${TAG}.txt" \
    --pattern "mobile-sdk-android-${TAG}.artifacts.json"
}

validate_release_dir() {
  require_tool unzip

  [[ -n "$RELEASE_DIR" ]] || fail "--release-dir is required unless --download is used"
  [[ -d "$RELEASE_DIR" ]] || fail "release dir does not exist: $RELEASE_DIR"

  if [[ -z "$VERSION" ]]; then
    VERSION="$(infer_version "$RELEASE_DIR")"
  fi

  local android_zip="$RELEASE_DIR/iroha-mobile-sdk-android-${VERSION}.zip"
  local checksums="$RELEASE_DIR/SHA256SUMS-android-${VERSION}.txt"
  local manifest="$RELEASE_DIR/mobile-sdk-android-${VERSION}.artifacts.json"
  local tmp_dir sha client_aar_entry

  [[ -f "$android_zip" ]] || fail "missing Android SDK zip: $android_zip"
  if [[ ! -f "$checksums" ]]; then
    checksums="$RELEASE_DIR/SHA256SUMS-all-${VERSION}.txt"
  fi
  [[ -f "$checksums" ]] || fail "missing checksum file for version $VERSION"
  if [[ ! -f "$manifest" ]]; then
    manifest="$RELEASE_DIR/mobile-sdk-all-${VERSION}.artifacts.json"
  fi
  [[ -f "$manifest" ]] || fail "missing artifact manifest for version $VERSION"

  sha="$(hash_file "$android_zip")"
  grep -F "$(basename "$android_zip")" "$checksums" | grep -Fq "$sha" ||
    fail "checksum file does not match $(basename "$android_zip")"
  grep -Fq "\"version\": \"$VERSION\"" "$manifest" || fail "manifest version mismatch"
  grep -Fq "$(basename "$android_zip")" "$manifest" || fail "manifest does not list Android SDK zip"
  grep -Eq '"sha256"[[:space:]]*:[[:space:]]*"[[:xdigit:]]{64}"' "$manifest" ||
    fail "manifest does not contain SHA-256 artifact hashes"

  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/core-jvm/core-jvm-.+\\.jar$" "raw core-jvm jar"
  client_aar_entry="$(find_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/client-android/client-android-release\\.aar$" "raw client Android AAR")"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/offline-wallet-android/offline-wallet-android-release\\.aar$" "raw offline-wallet Android AAR"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/native/arm64-v8a/libconnect_norito_bridge\\.so$" "raw arm64 native bridge"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/native/x86_64/libconnect_norito_bridge\\.so$" "raw x86_64 native bridge"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/maven/org/hyperledger/iroha/sdk/core-jvm/[^/]+/core-jvm-.+\\.pom$" "Android Maven core-jvm POM"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/maven/org/hyperledger/iroha/sdk/client-android/[^/]+/client-android-.+\\.aar$" "Android Maven client AAR"
  require_zip_entry "$android_zip" "^iroha-mobile-sdk-android-${VERSION}/maven/org/hyperledger/iroha/sdk/offline-wallet-android/[^/]+/offline-wallet-android-.+\\.aar$" "Android Maven offline-wallet AAR"

  tmp_dir="$(mktemp -d "${TMPDIR:-/tmp}/iroha-android-aar.XXXXXX")"
  VALIDATION_TMP_DIR="$tmp_dir"
  validate_client_aar "$android_zip" "$tmp_dir" "$client_aar_entry"
  rm -rf -- "$tmp_dir"
  VALIDATION_TMP_DIR=""

  echo "[iroha-sdk-assets] Android Iroha SDK assets validated for version $VERSION"
}

make_aar() {
  local archive="$1"
  local omit_x86="${2:-0}"
  local stage
  stage="$(mktemp -d "${TMPDIR:-/tmp}/iroha-aar-fixture.XXXXXX")"
  mkdir -p "$stage/jni/arm64-v8a" "$stage/jni/x86_64"
  printf '<manifest />\n' > "$stage/AndroidManifest.xml"
  printf 'classes\n' > "$stage/classes.jar"
  printf 'so\n' > "$stage/jni/arm64-v8a/libconnect_norito_bridge.so"
  if [[ "$omit_x86" != "1" ]]; then
    printf 'so\n' > "$stage/jni/x86_64/libconnect_norito_bridge.so"
  fi
  (cd "$stage" && zip -qr "$archive" .)
  rm -rf "$stage"
}

make_fixture() {
  local dir="$1"
  local version="$2"
  local omit_maven="${3:-0}"
  local omit_x86="${4:-0}"
  local stage="$dir/iroha-mobile-sdk-android-${version}"
  local zip_path="$dir/iroha-mobile-sdk-android-${version}.zip"
  local sha

  mkdir -p "$stage/core-jvm" "$stage/client-android" "$stage/offline-wallet-android" "$stage/native/arm64-v8a" "$stage/native/x86_64"
  printf 'jar\n' > "$stage/core-jvm/core-jvm-${version#v}.jar"
  make_aar "$stage/client-android/client-android-release.aar" "$omit_x86"
  printf 'aar\n' > "$stage/offline-wallet-android/offline-wallet-android-release.aar"
  printf 'so\n' > "$stage/native/arm64-v8a/libconnect_norito_bridge.so"
  printf 'so\n' > "$stage/native/x86_64/libconnect_norito_bridge.so"

  if [[ "$omit_maven" != "1" ]]; then
    mkdir -p \
      "$stage/maven/org/hyperledger/iroha/sdk/core-jvm/${version#v}" \
      "$stage/maven/org/hyperledger/iroha/sdk/client-android/${version#v}" \
      "$stage/maven/org/hyperledger/iroha/sdk/offline-wallet-android/${version#v}"
    printf '<pom />\n' > "$stage/maven/org/hyperledger/iroha/sdk/core-jvm/${version#v}/core-jvm-${version#v}.pom"
    cp "$stage/client-android/client-android-release.aar" "$stage/maven/org/hyperledger/iroha/sdk/client-android/${version#v}/client-android-${version#v}.aar"
    printf 'aar\n' > "$stage/maven/org/hyperledger/iroha/sdk/offline-wallet-android/${version#v}/offline-wallet-android-${version#v}.aar"
  fi

  (cd "$dir" && zip -qr "$(basename "$zip_path")" "$(basename "$stage")")
  sha="$(hash_file "$zip_path")"
  printf '%s  %s\n' "$sha" "$zip_path" > "$dir/SHA256SUMS-android-${version}.txt"
  cat > "$dir/mobile-sdk-android-${version}.artifacts.json" <<JSON
{
  "version": "$version",
  "mode": "android",
  "artifacts": [
    {"kind":"android-sdk","name":"$(basename "$zip_path")","path":"$zip_path","sha256":"$sha","bytes":1}
  ]
}
JSON
  rm -rf "$stage"
}

run_self_test() {
  require_tool zip
  require_tool unzip
  local tmp valid missing_maven bad_aar missing_checksums
  tmp="$(mktemp -d "${TMPDIR:-/tmp}/iroha-android-assets-test.XXXXXX")"
  SELF_TEST_TMP="$tmp"

  valid="$tmp/valid"
  mkdir -p "$valid"
  make_fixture "$valid" "v0.1.0"
  bash "$0" --release-dir "$valid" --version "v0.1.0" >/dev/null

  missing_maven="$tmp/missing-maven"
  mkdir -p "$missing_maven"
  make_fixture "$missing_maven" "v0.1.0" 1
  if bash "$0" --release-dir "$missing_maven" --version "v0.1.0" >/dev/null 2>&1; then
    fail "self-test expected missing Maven repository validation to fail"
  fi

  bad_aar="$tmp/bad-aar"
  mkdir -p "$bad_aar"
  make_fixture "$bad_aar" "v0.1.0" 0 1
  if bash "$0" --release-dir "$bad_aar" --version "v0.1.0" >/dev/null 2>&1; then
    fail "self-test expected missing x86_64 native AAR entry to fail"
  fi

  missing_checksums="$tmp/missing-checksums"
  mkdir -p "$missing_checksums"
  make_fixture "$missing_checksums" "v0.1.0"
  rm -f "$missing_checksums/SHA256SUMS-android-v0.1.0.txt"
  if bash "$0" --release-dir "$missing_checksums" --version "v0.1.0" >/dev/null 2>&1; then
    fail "self-test expected missing checksum validation to fail"
  fi

  echo "[iroha-sdk-assets-test] Android release asset checks passed"
}

if [[ "$SELF_TEST" == "1" ]]; then
  run_self_test
  exit 0
fi

if [[ "$DOWNLOAD" == "1" ]]; then
  download_release_assets
fi

validate_release_dir
