#!/usr/bin/env bash
set -euo pipefail

echo "[post-merge] Starting validation..."

UNIT_ONLY=false
OFFLINE=false
for arg in "$@"; do
  case "$arg" in
    --unit-only)
      UNIT_ONLY=true
      ;;
    --offline)
      OFFLINE=true
      ;;
  esac
done

run() {
  echo "> $*"
  "$@"
}

# 1) Environment & Gradle
GRADLE_FLAGS=(--no-daemon --console=plain)
if [ "$OFFLINE" = true ] || [ "${GRADLE_OFFLINE:-}" = "true" ]; then
  echo "[post-merge] Using Gradle --offline"
  GRADLE_FLAGS+=(--offline)
fi

run ./gradlew -version "${GRADLE_FLAGS[@]}" || true

# 2) Polkadot SDK alignment print
echo "[post-merge] Checking Polkadot SDK alignment output..."
ALIGN_OUT=$(./gradlew printPolkadotSdkAlignment "${GRADLE_FLAGS[@]}" --no-parallel | tee /dev/stderr)
echo "$ALIGN_OUT" | grep -q "Polkadot SDK alignment (effective):" || {
  echo "Alignment header not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "TYPES_URL:" || { echo "TYPES_URL not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "DEFAULT_V13_TYPES_URL:" || { echo "DEFAULT_V13_TYPES_URL not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "CHAINS_URL (debug):" || { echo "CHAINS_URL (debug) not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "CHAINS_URL (release):" || { echo "CHAINS_URL (release) not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "SHARED_FEATURES_VERSION_OVERRIDE:" || { echo "SHARED_FEATURES_VERSION_OVERRIDE not printed"; exit 1; }

# 3) Verify fearless-utils source mapping toggle messaging
echo "[post-merge] Verifying fearless-utils source mapping toggle..."
UTILS_OFF=$(./gradlew -q help --no-parallel "${GRADLE_FLAGS[@]}" 2>&1 | tee /dev/stderr || true)
echo "$UTILS_OFF" | grep -q "USE_REMOTE_UTILS not set; using published artifacts for fearless-utils" || {
  echo "Expected message for USE_REMOTE_UTILS=false not found"; exit 1; }

UTILS_ON=$(USE_REMOTE_UTILS=true ./gradlew -q help --no-parallel "${GRADLE_FLAGS[@]}" 2>&1 | tee /dev/stderr || true)
echo "$UTILS_ON" | grep -q "Including remote fearless-utils from GitHub via sourceControl" || {
  echo "Expected message for USE_REMOTE_UTILS=true not found"; exit 1; }

# 4) Static analysis + tests, then assemble, then lint (order matters)
echo "[post-merge] Running detekt + unit tests..."
run ./gradlew runTest "${GRADLE_FLAGS[@]}" --stacktrace --info --no-parallel

if [ "$UNIT_ONLY" = true ]; then
  echo "[post-merge] Skipping assemble/lint (unit-only mode)"
  echo "[post-merge] Done."
  exit 0
fi

echo "[post-merge] Assembling debug (to produce classes/AARs for lint)..."
run ./gradlew :app:assembleDebug "${GRADLE_FLAGS[@]}" --stacktrace --info --no-parallel

echo "[post-merge] Running Android Lint (app)..."
run ./gradlew :app:lint "${GRADLE_FLAGS[@]}" --stacktrace --info --no-parallel

echo "[post-merge] All checks passed."
