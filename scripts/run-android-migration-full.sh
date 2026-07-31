#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
RESULTS_MARKER_RELATIVE="build/reports/android-migration-results/full-start.marker"

cd "$ROOT_DIR"

rm -rf common/build/outputs/androidTest-results/connected
rm -rf core-db/build/outputs/androidTest-results/connected
rm -rf app/build/outputs/androidTest-results/connected
rm -rf feature-account-impl/build/outputs/androidTest-results/connected

mkdir -p "$(dirname "$RESULTS_MARKER_RELATIVE")"
if [[ -e "$RESULTS_MARKER_RELATIVE" || -L "$RESULTS_MARKER_RELATIVE" ]]; then
  rm -f -- "$RESULTS_MARKER_RELATIVE"
fi
(umask 077; : >"$RESULTS_MARKER_RELATIVE")

./gradlew \
  :common:connectedDebugAndroidTest \
  :core-db:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  :feature-account-impl:connectedDebugAndroidTest \
  --continue \
  --no-daemon \
  --console=plain \
  --stacktrace
