#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
API_LEVEL="${MIGRATION_COMPAT_API:-}"
RESULTS_MARKER_RELATIVE="build/reports/android-migration-results/compatibility-start.marker"

case "$API_LEVEL" in
  30|31|36) ;;
  *)
    echo "[android-migration-compat][error] unsupported API level: $API_LEVEL" >&2
    exit 1
    ;;
esac

cd "$ROOT_DIR"

rm -rf common/build/outputs/androidTest-results/connected
rm -rf core-db/build/outputs/androidTest-results/connected
rm -rf app/build/outputs/androidTest-results/connected

mkdir -p "$(dirname "$RESULTS_MARKER_RELATIVE")"
if [[ -e "$RESULTS_MARKER_RELATIVE" || -L "$RESULTS_MARKER_RELATIVE" ]]; then
  rm -f -- "$RESULTS_MARKER_RELATIVE"
fi
(umask 077; : >"$RESULTS_MARKER_RELATIVE")

./gradlew :common:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.common.data.storage.encrypt.EncryptionUtilAndroidKeyStoreTest \
  --no-daemon --console=plain --stacktrace

./gradlew :core-db:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.coredb.migrations.ReleasedSchemaUpgradeMatrixTest,jp.co.soramitsu.coredb.migrations.ReleasedVersion27FailClosedMigrationTest \
  --no-daemon --console=plain --stacktrace

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=jp.co.soramitsu.app.root.presentation.WalletGateActivityLifecycleTest,jp.co.soramitsu.app.root.presentation.WalletSecureStorageRestartActivityLifecycleTest,jp.co.soramitsu.app.root.presentation.SecurityWarningRestorationTest \
  --no-daemon --console=plain --stacktrace

MIGRATION_RESULTS_PROFILE=compatibility \
  bash ./scripts/verify-android-migration-instrumentation-results.sh

evidence="build/reports/android-migration-compatibility/api-$API_LEVEL"
for module in common core-db app; do
  mkdir -p "$evidence/$module"
  cp -a "$module/build/outputs/androidTest-results/connected/debug/." \
    "$evidence/$module/"
done

echo \
  "[android-migration-compat] API $API_LEVEL: 39 critical tests passed and archived"
