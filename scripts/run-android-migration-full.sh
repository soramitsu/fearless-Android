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

trap 'python3 scripts/android-app-instrumentation.py restore' EXIT
python3 scripts/android-app-instrumentation.py prepare
APP_TEST_ARGUMENTS=()
while IFS= read -r argument; do
  [[ "$argument" == -Pandroid.testInstrumentationRunnerArguments.*=* ]] || exit 1
  APP_TEST_ARGUMENTS+=("$argument")
done < build/reports/android-app-profiles/arguments.txt

./gradlew \
  :common:connectedDebugAndroidTest \
  :core-db:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  :feature-account-impl:connectedDebugAndroidTest \
  "${APP_TEST_ARGUMENTS[@]}" \
  --continue \
  --no-daemon \
  --console=plain \
  --stacktrace

python3 scripts/android-app-instrumentation.py qualify
