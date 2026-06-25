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

# 2) Verify branch-flow guards and staging branch deprecation readiness
echo "[post-merge] Auditing branch flow..."
run bash ./scripts/audit-branch-flow.sh
run bash ./scripts/test-branch-flow-audit.sh

# 3) Polkadot SDK alignment print
echo "[post-merge] Checking Polkadot SDK alignment output..."
ALIGN_OUT=$(./gradlew printPolkadotSdkAlignment "${GRADLE_FLAGS[@]}" --no-parallel | tee /dev/stderr)
echo "$ALIGN_OUT" | grep -q "Polkadot SDK alignment (effective):" || {
  echo "Alignment header not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "TYPES_URL:" || { echo "TYPES_URL not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "DEFAULT_V13_TYPES_URL:" || { echo "DEFAULT_V13_TYPES_URL not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "CHAINS_URL (debug):" || { echo "CHAINS_URL (debug) not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "CHAINS_URL (release):" || { echo "CHAINS_URL (release) not printed"; exit 1; }
echo "$ALIGN_OUT" | grep -q "SHARED_FEATURES_VERSION_OVERRIDE:" || { echo "SHARED_FEATURES_VERSION_OVERRIDE not printed"; exit 1; }

# 4) Verify pinned fearless-utils source checkout
echo "[post-merge] Verifying fearless-utils source checkout..."
export FORCE_LOCAL_UTILS="${FORCE_LOCAL_UTILS:-true}"
export FEARLESS_UTILS_LIBRARY_ONLY="${FEARLESS_UTILS_LIBRARY_ONLY:-true}"
run ./scripts/ensure-fearless-utils.sh

# 5) Verify release overlay boundary guard behavior
echo "[post-merge] Testing private overlay boundary guard..."
run bash ./scripts/test-private-overlay-boundary.sh

# 6) Static analysis + tests, then assemble, then lint (order matters)
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
