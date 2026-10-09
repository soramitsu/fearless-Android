#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
tmp_dir="$(mktemp -d)"
generated_config="$ROOT_DIR/app/src/internalAppSharing/google-services.json"
created_fixture=false

cleanup() {
  if [[ "$created_fixture" == true ]]; then
    rm -f "$generated_config"
  fi
  rm -rf "$tmp_dir"
}
trap cleanup EXIT HUP INT TERM

fail() {
  echo "[android-gradle-distribution-test][error] $*" >&2
  exit 1
}

[[ -x "$GRADLEW" ]] || fail "Gradle wrapper is missing."
command -v java >/dev/null 2>&1 || fail "Java is required."
[[ ! -e "$generated_config" && ! -L "$generated_config" ]] ||
  fail "Refusing to test over a pre-existing IAS Firebase bridge file."

gradle_env=(
  "CI=true"
  "SKIP_AUTO_VERSION_BUMP=true"
  "FEARLESS_UTILS_LIBRARY_ONLY=true"
  "FORCE_LOCAL_UTILS=true"
  "USE_REMOTE_UTILS=false"
)

run_gradle() {
  env \
    -u CI_KEYSTORE_PATH \
    -u CI_KEYSTORE_PASS \
    -u CI_KEYSTORE_KEY_ALIAS \
    -u CI_KEYSTORE_KEY_PASS \
    -u CI_PLAY_KEY \
    -u PLAY_TRACK \
    -u PLAY_RELEASE_STATUS \
    -u PLAY_ARTIFACT_DIR \
    "${gradle_env[@]}" \
    "$GRADLEW" "$@" --no-daemon --console=plain
}

tasks_log="$tmp_dir/tasks.log"
run_gradle :app:tasks --all >"$tasks_log" 2>&1 || fail "could not enumerate Android app tasks"
for task_name in bundleInternalAppSharing publishInternalAppSharingBundle publishReleaseBundle; do
  grep -Eq "^${task_name}([[:space:]]|$)" "$tasks_log" || fail "missing expected task inventory entry: $task_name"
done

release_guard_log="$tmp_dir/release-guard.log"
if run_gradle :app:bundleRelease >"$release_guard_log" 2>&1; then
  fail "bundleRelease accepted missing production signing inputs"
fi
grep -Fq 'Play release signing is incomplete; missing explicit inputs:' "$release_guard_log" ||
  fail "bundleRelease did not fail through the production signing guard"

ias_publish_log="$tmp_dir/ias-publish.log"
if run_gradle :app:publishInternalAppSharingBundle >"$ias_publish_log" 2>&1; then
  fail "debug-signed IAS variant reached a normal Play publisher task"
fi
grep -Fq 'internalAppSharing variant is IAS-file-only and must never run a Play publisher task' "$ias_publish_log" ||
  fail "IAS normal-Play publication did not fail through the dedicated guard"
[[ ! -e "$generated_config" && ! -L "$generated_config" ]] ||
  fail "IAS publisher rejection left a generated Firebase bridge file"

success_log="$tmp_dir/ias-success.log"
run_gradle :app:verifyInternalAppSharingConfiguration >"$success_log" 2>&1 ||
  fail "valid ephemeral IAS Firebase bridge verification failed"
[[ ! -e "$generated_config" && ! -L "$generated_config" ]] ||
  fail "successful IAS verification left a generated Firebase bridge file"

forced_failure_log="$tmp_dir/ias-forced-failure.log"
if run_gradle :app:verifyInternalAppSharingConfiguration -PiasVerificationForceFailure=true \
  >"$forced_failure_log" 2>&1; then
  fail "forced IAS verification failure unexpectedly passed"
fi
grep -Fq 'Forced IAS verification failure after generated-file validation.' "$forced_failure_log" ||
  fail "forced IAS failure did not run after byte/mode validation"
[[ ! -e "$generated_config" && ! -L "$generated_config" ]] ||
  fail "failed IAS verification left a generated Firebase bridge file"

mkdir -p "$(dirname "$generated_config")"
printf '%s' 'divergent-user-content' > "$generated_config"
chmod 600 "$generated_config"
created_fixture=true
divergent_log="$tmp_dir/ias-divergent.log"
if run_gradle :app:verifyInternalAppSharingConfiguration >"$divergent_log" 2>&1; then
  fail "divergent pre-existing IAS Firebase file was accepted"
fi
grep -Fq 'Refusing to overwrite a divergent pre-existing IAS Firebase configuration.' "$divergent_log" ||
  fail "divergent IAS file did not fail through the no-overwrite guard"
[[ "$(<"$generated_config")" == 'divergent-user-content' ]] ||
  fail "divergent pre-existing IAS file was overwritten or deleted"
rm -f "$generated_config"
created_fixture=false

symlink_target="$tmp_dir/symlink-target"
printf '%s' 'preserve-symlink-target' > "$symlink_target"
ln -s "$symlink_target" "$generated_config"
created_fixture=true
symlink_log="$tmp_dir/ias-symlink.log"
if run_gradle :app:verifyInternalAppSharingConfiguration >"$symlink_log" 2>&1; then
  fail "symlink IAS Firebase file was accepted"
fi
grep -Fq 'Pre-existing IAS Firebase configuration must be a regular, non-symlink file.' "$symlink_log" ||
  fail "IAS symlink did not fail through the no-follow guard"
[[ "$(<"$symlink_target")" == 'preserve-symlink-target' ]] ||
  fail "IAS symlink target was overwritten"
rm -f "$generated_config"
created_fixture=false

release_aab="$ROOT_DIR/app/build/outputs/bundle/release/app-release.aab"
if [[ -f "$release_aab" && -n "${ANDROID_DISTRIBUTION_EXPECTED_RELEASE_AAB_SHA256:-}" ]]; then
  if command -v sha256sum >/dev/null 2>&1; then
    release_sha="$(sha256sum "$release_aab" | awk '{print $1}')"
  else
    release_sha="$(shasum -a 256 "$release_aab" | awk '{print $1}')"
  fi
  [[ "$release_sha" == "$ANDROID_DISTRIBUTION_EXPECTED_RELEASE_AAB_SHA256" ]] ||
    fail "existing release AAB changed during distribution-guard tests"
fi

echo "[android-gradle-distribution-test] passed (task inventory plus 6 behavioral/adversarial guards)"
