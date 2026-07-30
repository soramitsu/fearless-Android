#!/usr/bin/env bash
# shellcheck disable=SC2329
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
readonly ROOT_DIR
readonly BOOT_TIMEOUT_SECONDS=600
readonly COMMAND_TIMEOUT_SECONDS=15
readonly SDK_INSTALL_TIMEOUT_SECONDS=900
readonly SDK_UNINSTALL_TIMEOUT_SECONDS=180
readonly SYSTEM_IMAGE_TARGET="google_atd"
readonly SYSTEM_IMAGE_ARCH="x86_64"
readonly EMULATOR_PORT=5554
readonly EMULATOR_SERIAL="emulator-5554"

fail() {
  echo "[android-emulator-ci][error] $*" >&2
  if [[ -n "${EVIDENCE_DIR:-}" && -d "$EVIDENCE_DIR" && ! -L "$EVIDENCE_DIR" ]]; then
    printf '%s\n' "$*" >"$EVIDENCE_DIR/failure.txt"
  fi
  exit 1
}

[[ "$#" == "2" ]] ||
  fail "usage: run-android-emulator-ci.sh <api-level> <compatibility|full>"
readonly API_LEVEL="$1"
readonly TEST_MODE="$2"
case "$API_LEVEL:$TEST_MODE" in
  30:compatibility|31:compatibility|34:full|36:compatibility) ;;
  *) fail "unsupported API/test-mode binding: $API_LEVEL:$TEST_MODE" ;;
esac

[[ "$(uname -s)" == "Linux" ]] ||
  fail "the CI emulator lifecycle is supported only on Linux"
[[ "${CI:-}" == "true" ]] ||
  fail "the CI emulator lifecycle requires CI=true"
[[ -n "${RUNNER_TEMP:-}" && "$RUNNER_TEMP" == /* ]] ||
  fail "RUNNER_TEMP must be an absolute path"
[[ -d "$RUNNER_TEMP" && ! -L "$RUNNER_TEMP" ]] ||
  fail "RUNNER_TEMP must be a regular directory, not a symlink"
[[ -n "${ANDROID_SDK_ROOT:-}" && "$ANDROID_SDK_ROOT" == /* ]] ||
  fail "ANDROID_SDK_ROOT must be an absolute path"
[[ -d "$ANDROID_SDK_ROOT" && ! -L "$ANDROID_SDK_ROOT" ]] ||
  fail "ANDROID_SDK_ROOT must be a regular directory, not a symlink"

readonly SYSTEM_IMAGE_PACKAGE="system-images;android-$API_LEVEL;$SYSTEM_IMAGE_TARGET;$SYSTEM_IMAGE_ARCH"
readonly SYSTEM_IMAGE_DIR="$ANDROID_SDK_ROOT/system-images/android-$API_LEVEL/$SYSTEM_IMAGE_TARGET/$SYSTEM_IMAGE_ARCH"
readonly EVIDENCE_DIR="$ROOT_DIR/build/reports/android-emulator-lifecycle/api-$API_LEVEL-$TEST_MODE"
rm -rf "$EVIDENCE_DIR"
install -d -m 0700 "$EVIDENCE_DIR"
(umask 077; : >"$EVIDENCE_DIR/lifecycle-start.marker")
printf '%s\n' \
  "requested_api=$API_LEVEL" \
  "requested_mode=$TEST_MODE" \
  "requested_target=$SYSTEM_IMAGE_TARGET" \
  "requested_arch=$SYSTEM_IMAGE_ARCH" \
  "requested_serial=$EMULATOR_SERIAL" \
  "requested_port=$EMULATOR_PORT" \
  >"$EVIDENCE_DIR/requested.env"

[[ -n "${SDKMANAGER_BIN:-}" && "$SDKMANAGER_BIN" == "$ANDROID_SDK_ROOT"/* ]] ||
  fail "SDKMANAGER_BIN must be an absolute executable under ANDROID_SDK_ROOT"
[[ -n "${AVDMANAGER_BIN:-}" && "$AVDMANAGER_BIN" == "$ANDROID_SDK_ROOT"/* ]] ||
  fail "AVDMANAGER_BIN must be an absolute executable under ANDROID_SDK_ROOT"
readonly SDKMANAGER="$SDKMANAGER_BIN"
readonly AVDMANAGER="$AVDMANAGER_BIN"
readonly EMULATOR="$ANDROID_SDK_ROOT/emulator/emulator"
readonly QEMU_SYSTEM="$ANDROID_SDK_ROOT/emulator/qemu/linux-x86_64/qemu-system-x86_64"
readonly ADB="$ANDROID_SDK_ROOT/platform-tools/adb"

work_dir="$(mktemp -d "$RUNNER_TEMP/fearless-android-emulator-$API_LEVEL.XXXXXX")"
[[ "$work_dir" == "$RUNNER_TEMP"/fearless-android-emulator-"$API_LEVEL".* ]] ||
  fail "private emulator work directory escaped RUNNER_TEMP"
chmod 0700 "$work_dir"
readonly WORK_DIR="$work_dir"
readonly ANDROID_AVD_HOME="$WORK_DIR/avd"
readonly ANDROID_EMULATOR_HOME="$WORK_DIR/emulator-home"
readonly AVD_NAME="fearless-api-$API_LEVEL-$TEST_MODE"
readonly EMULATOR_LOG="$EVIDENCE_DIR/emulator.log"
readonly EMULATOR_PID_FILE="$EVIDENCE_DIR/emulator.pid"
export ANDROID_AVD_HOME ANDROID_EMULATOR_HOME

emulator_pid=""
emulator_pgid=""
image_install_attempted=0
cleanup_started=0

record_adb_diagnostics() {
  local diagnostic_status=0
  if [[ -n "$emulator_pid" ]] && kill -0 "$emulator_pid" 2>/dev/null; then
    if ! timeout "$COMMAND_TIMEOUT_SECONDS" \
      "$ADB" -s "$EMULATOR_SERIAL" shell getprop \
      >"$EVIDENCE_DIR/device-properties.txt" 2>"$EVIDENCE_DIR/device-properties.stderr"; then
      diagnostic_status=1
    fi
    if ! timeout "$COMMAND_TIMEOUT_SECONDS" \
      "$ADB" -s "$EMULATOR_SERIAL" logcat -d -t 4000 \
      >"$EVIDENCE_DIR/logcat.txt" 2>"$EVIDENCE_DIR/logcat.stderr"; then
      diagnostic_status=1
    fi
  fi
  return "$diagnostic_status"
}

stop_emulator() {
  local stop_status=0
  if [[ -n "$emulator_pid" ]] && kill -0 "$emulator_pid" 2>/dev/null; then
    if ! timeout "$COMMAND_TIMEOUT_SECONDS" \
      "$ADB" -s "$EMULATOR_SERIAL" emu kill \
      >"$EVIDENCE_DIR/emulator-kill.stdout" \
      2>"$EVIDENCE_DIR/emulator-kill.stderr"; then
      stop_status=1
    fi

    local deadline=$((SECONDS + 30))
    while kill -0 "$emulator_pid" 2>/dev/null && ((SECONDS < deadline)); do
      sleep 1
    done
    if kill -0 "$emulator_pid" 2>/dev/null; then
      if ! kill -TERM -- "-$emulator_pgid" 2>/dev/null; then
        stop_status=1
      fi
      deadline=$((SECONDS + 10))
      while kill -0 "$emulator_pid" 2>/dev/null && ((SECONDS < deadline)); do
        sleep 1
      done
    fi
    if kill -0 "$emulator_pid" 2>/dev/null; then
      if ! kill -KILL -- "-$emulator_pgid" 2>/dev/null; then
        stop_status=1
      fi
    fi
    if wait "$emulator_pid" 2>/dev/null; then
      :
    else
      printf '%s\n' "emulator_wait_status=$?" \
        >"$EVIDENCE_DIR/emulator-wait.env"
    fi
    local transport_deadline=$((SECONDS + 15))
    local transport_present=1
    while ((SECONDS < transport_deadline)); do
      local inventory=""
      if inventory="$(timeout "$COMMAND_TIMEOUT_SECONDS" "$ADB" devices)"; then
        if ! awk -v serial="$EMULATOR_SERIAL" \
          '$1 == serial { found = 1 } END { exit(found ? 0 : 1) }' \
          <<<"$inventory"; then
          transport_present=0
          break
        fi
      else
        stop_status=1
        break
      fi
      sleep 1
    done
    if ((transport_present)); then
      stop_status=1
    fi
  fi
  return "$stop_status"
}

cleanup() {
  local original_status="$?"
  local cleanup_status=0
  if ((cleanup_started)); then
    exit "$original_status"
  fi
  cleanup_started=1
  trap - EXIT HUP INT TERM

  if ! record_adb_diagnostics; then
    cleanup_status=1
  fi
  if ! stop_emulator; then
    cleanup_status=1
  fi
  if ((image_install_attempted)); then
    if ! timeout "$SDK_UNINSTALL_TIMEOUT_SECONDS" "$SDKMANAGER" \
      --sdk_root="$ANDROID_SDK_ROOT" \
      --uninstall "$SYSTEM_IMAGE_PACKAGE" \
      >"$EVIDENCE_DIR/system-image-uninstall.stdout" \
      2>"$EVIDENCE_DIR/system-image-uninstall.stderr"; then
      cleanup_status=1
    fi
  fi
  if [[ -d "$WORK_DIR" && ! -L "$WORK_DIR" && "$WORK_DIR" == "$RUNNER_TEMP"/fearless-android-emulator-"$API_LEVEL".* ]]; then
    rm -rf "$WORK_DIR"
  else
    cleanup_status=1
  fi

  printf '%s\n' \
    "test_exit=$original_status" \
    "cleanup_exit=$cleanup_status" \
    >"$EVIDENCE_DIR/final-status.env"
  if ((original_status == 0 && cleanup_status != 0)); then
    exit "$cleanup_status"
  fi
  exit "$original_status"
}

trap cleanup EXIT
trap 'exit 129' HUP
trap 'exit 130' INT
trap 'exit 143' TERM

for executable in "$SDKMANAGER" "$AVDMANAGER" "$EMULATOR" "$QEMU_SYSTEM" "$ADB"; do
  [[ -x "$executable" && ! -L "$executable" ]] ||
    fail "required Android SDK executable is missing or symlinked: $executable"
done
for executable in timeout setsid awk sed ps ldd; do
  command -v "$executable" >/dev/null ||
    fail "required host command is missing: $executable"
done
[[ -c /dev/kvm ]] || fail "/dev/kvm is not a character device"
if [[ ! -r /dev/kvm || ! -w /dev/kvm ]]; then
  command -v setfacl >/dev/null ||
    fail "/dev/kvm is inaccessible and setfacl is unavailable"
  sudo setfacl -m "u:$(id -un):rw" /dev/kvm
fi
[[ -r /dev/kvm && -w /dev/kvm ]] ||
  fail "current CI user cannot read and write /dev/kvm"

if ! {
  timeout "$COMMAND_TIMEOUT_SECONDS" ldd "$EMULATOR"
  timeout "$COMMAND_TIMEOUT_SECONDS" ldd "$QEMU_SYSTEM"
} >"$EVIDENCE_DIR/emulator-shared-libraries.txt" 2>&1; then
  fail "Android emulator shared-library inspection failed"
fi
if grep -Fq "not found" "$EVIDENCE_DIR/emulator-shared-libraries.txt"; then
  fail "Android emulator has unresolved shared-library dependencies"
fi

timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$EMULATOR" -version >"$EVIDENCE_DIR/emulator-version.txt" 2>&1
if ! timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$EMULATOR" -accel-check >"$EVIDENCE_DIR/accel-check.txt" 2>&1; then
  fail "Android emulator hardware acceleration check failed"
fi

image_install_attempted=1
timeout "$SDK_INSTALL_TIMEOUT_SECONDS" "$SDKMANAGER" \
  --sdk_root="$ANDROID_SDK_ROOT" \
  --install "$SYSTEM_IMAGE_PACKAGE" \
  >"$EVIDENCE_DIR/system-image-install.stdout" \
  2>"$EVIDENCE_DIR/system-image-install.stderr"
[[ -d "$SYSTEM_IMAGE_DIR" && ! -L "$SYSTEM_IMAGE_DIR" ]] ||
  fail "installed system image directory is missing or symlinked"
[[ -f "$SYSTEM_IMAGE_DIR/source.properties" && ! -L "$SYSTEM_IMAGE_DIR/source.properties" ]] ||
  fail "installed system image metadata is missing or symlinked"
cp "$SYSTEM_IMAGE_DIR/source.properties" "$EVIDENCE_DIR/system-image-source.properties"
grep -Fxq "AndroidVersion.ApiLevel=$API_LEVEL" \
  "$EVIDENCE_DIR/system-image-source.properties" ||
  fail "installed system image API metadata does not match API $API_LEVEL"
grep -Fxq "SystemImage.TagId=$SYSTEM_IMAGE_TARGET" \
  "$EVIDENCE_DIR/system-image-source.properties" ||
  fail "installed system image target metadata does not match $SYSTEM_IMAGE_TARGET"
grep -Fxq "SystemImage.Abi=$SYSTEM_IMAGE_ARCH" \
  "$EVIDENCE_DIR/system-image-source.properties" ||
  fail "installed system image ABI metadata does not match $SYSTEM_IMAGE_ARCH"

install -d -m 0700 "$ANDROID_AVD_HOME" "$ANDROID_EMULATOR_HOME"
printf 'no\n' | timeout "$COMMAND_TIMEOUT_SECONDS" "$AVDMANAGER" \
  create avd \
  --force \
  --name "$AVD_NAME" \
  --package "$SYSTEM_IMAGE_PACKAGE" \
  --device pixel_6 \
  >"$EVIDENCE_DIR/avd-create.stdout" \
  2>"$EVIDENCE_DIR/avd-create.stderr"

readonly AVD_CONFIG="$ANDROID_AVD_HOME/$AVD_NAME.avd/config.ini"
[[ -f "$AVD_CONFIG" && ! -L "$AVD_CONFIG" ]] ||
  fail "AVD config was not created as a regular non-symlink file"
{
  printf '%s\n' \
    "hw.cpu.ncore=2" \
    "hw.ramSize=2048" \
    "vm.heapSize=512"
} >>"$AVD_CONFIG"
cp "$AVD_CONFIG" "$EVIDENCE_DIR/avd-config.ini"
grep -Fxq "image.sysdir.1=system-images/android-$API_LEVEL/$SYSTEM_IMAGE_TARGET/$SYSTEM_IMAGE_ARCH/" \
  "$AVD_CONFIG" ||
  fail "AVD config is not bound to the requested system image"

prelaunch_inventory="$(timeout "$COMMAND_TIMEOUT_SECONDS" "$ADB" devices)"
preexisting_transports="$(awk \
  'NR > 1 && NF >= 2 { count++ } END { print count + 0 }' \
  <<<"$prelaunch_inventory")"
[[ "$preexisting_transports" == "0" ]] ||
  fail "an Android transport is already attached before emulator startup"

setsid "$EMULATOR" \
  "@$AVD_NAME" \
  -port "$EMULATOR_PORT" \
  -accel on \
  -wipe-data \
  -no-snapshot \
  -no-window \
  -noaudio \
  -no-boot-anim \
  -camera-back none \
  -gpu swiftshader \
  -cores 2 \
  -memory 2048 \
  >"$EMULATOR_LOG" 2>&1 &
emulator_pid="$!"
emulator_pgid="$emulator_pid"
printf '%s\n' "$emulator_pid" >"$EMULATOR_PID_FILE"
observed_pgid="$(ps -o pgid= -p "$emulator_pid" | tr -d '[:space:]')"
[[ "$observed_pgid" =~ ^[0-9]+$ && "$observed_pgid" == "$emulator_pgid" ]] ||
  fail "emulator did not start as the captured process-group leader"

readonly BOOT_DEADLINE=$((SECONDS + BOOT_TIMEOUT_SECONDS))
boot_complete=0
while ((SECONDS < BOOT_DEADLINE)); do
  kill -0 "$emulator_pid" 2>/dev/null ||
    fail "emulator exited before completing boot"
  state=""
  if state="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
    "$ADB" -s "$EMULATOR_SERIAL" get-state 2>/dev/null)"; then
    if [[ "$state" == "device" ]]; then
      sys_boot="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
        "$ADB" -s "$EMULATOR_SERIAL" shell getprop sys.boot_completed |
        tr -d '\r')"
      boot_animation="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
        "$ADB" -s "$EMULATOR_SERIAL" shell getprop init.svc.bootanim |
        tr -d '\r')"
      package_path=""
      if [[ "$sys_boot" == "1" ]] &&
        package_path="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
          "$ADB" -s "$EMULATOR_SERIAL" shell pm path android 2>/dev/null)" &&
        [[ "$package_path" == package:* ]]; then
        boot_complete=1
        printf '%s\n' \
          "sys_boot_completed=$sys_boot" \
          "boot_animation=$boot_animation" \
          "package_path=$package_path" \
          >"$EVIDENCE_DIR/boot-observed.env"
        break
      fi
    fi
  fi
  sleep 2
done
((boot_complete == 1)) ||
  fail "emulator did not complete boot within $BOOT_TIMEOUT_SECONDS seconds"

device_inventory="$(timeout "$COMMAND_TIMEOUT_SECONDS" "$ADB" devices)"
transport_count="$(awk \
  'NR > 1 && NF >= 2 { count++ } END { print count + 0 }' \
  <<<"$device_inventory")"
device_count="$(awk \
  'NR > 1 && $2 == "device" { count++ } END { print count + 0 }' \
  <<<"$device_inventory")"
[[ "$transport_count" == "1" ]] ||
  fail "expected exactly one Android transport; found $transport_count"
[[ "$device_count" == "1" ]] ||
  fail "expected exactly one attached Android device; found $device_count"
awk -v serial="$EMULATOR_SERIAL" '
  $1 == serial && $2 == "device" { found++ }
  END { exit(found == 1 ? 0 : 1) }
' <<<"$device_inventory" ||
  fail "the sole attached Android device is not $EMULATOR_SERIAL"

observed_api="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$ADB" -s "$EMULATOR_SERIAL" shell getprop ro.build.version.sdk | tr -d '\r')"
observed_abi="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$ADB" -s "$EMULATOR_SERIAL" shell getprop ro.product.cpu.abi | tr -d '\r')"
observed_qemu="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$ADB" -s "$EMULATOR_SERIAL" shell getprop ro.kernel.qemu | tr -d '\r')"
[[ "$observed_api" == "$API_LEVEL" ]] ||
  fail "booted API $observed_api instead of API $API_LEVEL"
[[ "$observed_abi" == "$SYSTEM_IMAGE_ARCH" ]] ||
  fail "booted ABI $observed_abi instead of $SYSTEM_IMAGE_ARCH"
[[ "$observed_qemu" == "1" ]] ||
  fail "booted device is not an Android emulator"
printf '%s\n' \
  "observed_api=$observed_api" \
  "observed_abi=$observed_abi" \
  "observed_qemu=$observed_qemu" \
  "observed_serial=$EMULATOR_SERIAL" \
  >"$EVIDENCE_DIR/observed.env"

for scale in window_animation_scale transition_animation_scale animator_duration_scale; do
  timeout "$COMMAND_TIMEOUT_SECONDS" \
    "$ADB" -s "$EMULATOR_SERIAL" shell settings put global "$scale" 0
  observed_scale="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
    "$ADB" -s "$EMULATOR_SERIAL" shell settings get global "$scale" |
    tr -d '\r')"
  [[ "$observed_scale" == "0" || "$observed_scale" == "0.0" ]] ||
    fail "failed to disable $scale"
done
timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$ADB" -s "$EMULATOR_SERIAL" shell settings put secure spell_checker_enabled 0
observed_spellchecker="$(timeout "$COMMAND_TIMEOUT_SECONDS" \
  "$ADB" -s "$EMULATOR_SERIAL" shell settings get secure spell_checker_enabled |
  tr -d '\r')"
[[ "$observed_spellchecker" == "0" ]] ||
  fail "failed to disable the Android spell checker"

export ANDROID_SERIAL="$EMULATOR_SERIAL"
test_status=0
if [[ "$TEST_MODE" == "compatibility" ]]; then
  if MIGRATION_COMPAT_API="$API_LEVEL" \
    bash "$ROOT_DIR/scripts/run-android-migration-compatibility.sh"; then
    test_status=0
  else
    test_status="$?"
  fi
else
  if bash "$ROOT_DIR/scripts/run-android-migration-full.sh"; then
    test_status=0
  else
    test_status="$?"
  fi
fi
printf '%s\n' "test_exit=$test_status" >"$EVIDENCE_DIR/test-status.env"
exit "$test_status"
