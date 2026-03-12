#!/usr/bin/env bash
set -euo pipefail

REQUIRED_JAVA=21
REQUIRED_API=35
REQUIRED_BUILD_TOOLS=35.0.0
REQUIRED_NDK=28.0.12674087

log() { echo -e "[validate] $*"; }
warn() { echo -e "[validate][warn] $*" >&2; }
err() { echo -e "[validate][error] $*" >&2; }

detect_java_major() {
  if ! command -v java >/dev/null 2>&1; then
    echo ""; return 0
  fi
  local v
  v=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
  echo "${v%%.*}"
}

ensure_java() {
  local major
  major=$(detect_java_major)
  if [[ -z "$major" ]]; then
    warn "Java not found. Install Temurin/Adoptium JDK ${REQUIRED_JAVA}."
    warn "macOS: brew install --cask temurin@${REQUIRED_JAVA}"
    warn "Linux: https://adoptium.net/ or SDKMAN: sdk install java ${REQUIRED_JAVA}"
    return 1
  fi
  if (( major < REQUIRED_JAVA )); then
    err "Java ${REQUIRED_JAVA}+ required, found ${major}. Update your JDK."
    return 1
  fi
  log "Java OK (version $(java -version 2>&1 | head -n1))."
}

ensure_android_sdk() {
  if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    # Try common locations
    local candidates=("$HOME/Library/Android/sdk" "$HOME/Android/Sdk")
    for d in "${candidates[@]}"; do
      if [[ -d "$d" ]]; then export ANDROID_SDK_ROOT="$d"; break; fi
    done
  fi
  if [[ -z "${ANDROID_SDK_ROOT:-}" || ! -d "$ANDROID_SDK_ROOT" ]]; then
    warn "ANDROID_SDK_ROOT not set. Install Android SDK (commandline-tools) and set ANDROID_SDK_ROOT."
    warn "Docs: https://developer.android.com/studio#command-tools"
    return 1
  fi
  log "Using ANDROID_SDK_ROOT=$ANDROID_SDK_ROOT"
}

find_sdkmanager() {
  if command -v sdkmanager >/dev/null 2>&1; then echo "$(command -v sdkmanager)"; return; fi
  local paths=(
    "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
    "$ANDROID_SDK_ROOT/cmdline-tools/bin/sdkmanager"
    "$ANDROID_SDK_ROOT/tools/bin/sdkmanager"
  )
  for p in "${paths[@]}"; do
    [[ -x "$p" ]] && { echo "$p"; return; }
  done
  echo ""
}

prepare_android_packages() {
  local sm
  sm=$(find_sdkmanager || true)
  if [[ -z "$sm" ]]; then
    warn "sdkmanager not found. Ensure Android commandline-tools are installed under ANDROID_SDK_ROOT."
    return 1
  fi
  log "Accepting licenses (if any)…"
  yes | "$sm" --licenses >/dev/null || true
  log "Ensuring required SDK packages (platforms;android-${REQUIRED_API}, build-tools;${REQUIRED_BUILD_TOOLS}, platform-tools, ndk;${REQUIRED_NDK})…"
  "$sm" --install "platforms;android-${REQUIRED_API}" "build-tools;${REQUIRED_BUILD_TOOLS}" "platform-tools" "ndk;${REQUIRED_NDK}"
}

run_gradle_tasks() {
  if [[ ! -x "./gradlew" ]]; then
    err "Gradle wrapper not found. Run from repo root."; return 1
  fi
  log "Gradle version…"
  ./gradlew --version --no-daemon --console=plain || true
  log "Polkadot SDK alignment (overrides)…"
  ./gradlew printPolkadotSdkAlignment --no-daemon --console=plain || true
  log "Running detektAll…"
  ./gradlew detektAll --no-daemon --console=plain
  log "Running unit tests + coverage (runTest)…"
  ./gradlew runTest --no-daemon --console=plain
  log "Running Android Lint (app)…"
  ./gradlew :app:lint --no-daemon --console=plain
  log "Done. Coverage reports under */build/reports/jacoco and HTML in */build/reports/tests."
}

main() {
  ensure_java || exit 1
  ensure_android_sdk || warn "SDK not fully configured; continuing if tasks do not require it."
  prepare_android_packages || warn "Could not ensure SDK packages; unit tests may still run."
  run_gradle_tasks
}

main "$@"
