#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
LOCK="$ROOT_DIR/scripts/android-release-sdk-linux.lock.json"
SDK_TOOL="$ROOT_DIR/scripts/android-release-sdk.py"

fail() {
  echo "[android-release-sdk][error] $*" >&2
  exit 1
}

[[ "$#" -eq 1 && ("$1" == "install" || "$1" == "verify") ]] || {
  echo "Usage: scripts/install-android-release-sdk.sh <install|verify>" >&2
  exit 2
}
[[ "${CI:-}" == "true" ]] || fail "The hermetic release SDK is CI-only."
[[ "$(uname -s)" == "Linux" && "$(uname -m)" == "x86_64" ]] ||
  fail "The release SDK lock supports only Linux x86_64."
[[ -n "${RUNNER_TEMP:-}" && "$RUNNER_TEMP" == /* ]] ||
  fail "RUNNER_TEMP must be an absolute path."
[[ -d "$RUNNER_TEMP" && ! -L "$RUNNER_TEMP" ]] ||
  fail "RUNNER_TEMP must be an existing non-symlink directory."
[[ "$(cd "$RUNNER_TEMP" && pwd -P)" == "$RUNNER_TEMP" ]] ||
  fail "RUNNER_TEMP must be canonical."
[[ -f "$LOCK" && ! -L "$LOCK" && -f "$SDK_TOOL" && ! -L "$SDK_TOOL" ]] ||
  fail "The checked-in SDK lock or installer is missing or unsafe."

sdk_root="$RUNNER_TEMP/fearless-android-release-sdk"
evidence_root="$RUNNER_TEMP/fearless-android-release-sdk-evidence"
archive_root="$RUNNER_TEMP/fearless-android-release-sdk-archives"

if [[ "$1" == "verify" ]]; then
  python3 "$SDK_TOOL" verify "$LOCK" "$sdk_root" "$evidence_root"
  echo "[android-release-sdk] isolated SDK tree verified"
  exit 0
fi

[[ ! -e "$sdk_root" && ! -L "$sdk_root" ]] ||
  fail "The isolated SDK root must not already exist."
[[ ! -e "$evidence_root" && ! -L "$evidence_root" ]] ||
  fail "The SDK evidence root must not already exist."
[[ ! -e "$archive_root" && ! -L "$archive_root" ]] ||
  fail "The private SDK archive root must not already exist."

umask 077
mkdir "$archive_root"
cleanup_archives() {
  if [[ -d "$archive_root" && ! -L "$archive_root" ]]; then
    chmod -R u+rwX -- "$archive_root" 2>/dev/null || true
  fi
  rm -rf -- "$archive_root"
}
handle_signal() {
  local exit_code="$1"
  trap - EXIT HUP INT TERM
  cleanup_archives
  exit "$exit_code"
}
trap cleanup_archives EXIT
trap 'handle_signal 129' HUP
trap 'handle_signal 130' INT
trap 'handle_signal 143' TERM

while IFS=$'\t' read -r filename url expected_size expected_sha1 expected_sha256; do
  [[ -n "$filename" && -n "$url" && -n "$expected_size" &&
    -n "$expected_sha1" && -n "$expected_sha256" ]] ||
    fail "The SDK download plan is malformed."
  partial="$archive_root/$filename.partial"
  archive="$archive_root/$filename"
  curl --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --fail --location --silent --show-error \
    --retry 3 --retry-all-errors \
    --output "$partial" \
    "$url"
  [[ "$(wc -c <"$partial" | tr -d '[:space:]')" == "$expected_size" ]] ||
    fail "$filename size differs from the lock."
  [[ "$(sha1sum "$partial" | awk '{print $1}')" == "$expected_sha1" ]] ||
    fail "$filename SHA-1 differs from the official metadata lock."
  [[ "$(sha256sum "$partial" | awk '{print $1}')" == "$expected_sha256" ]] ||
    fail "$filename SHA-256 differs from the release lock."
  chmod 0444 "$partial"
  mv "$partial" "$archive"
done < <(python3 "$SDK_TOOL" download-plan "$LOCK")

python3 "$SDK_TOOL" install \
  "$LOCK" \
  "$sdk_root" \
  "$archive_root" \
  "$evidence_root"
python3 "$SDK_TOOL" verify "$LOCK" "$sdk_root" "$evidence_root"
echo "[android-release-sdk] installed and verified two-package isolated SDK"
