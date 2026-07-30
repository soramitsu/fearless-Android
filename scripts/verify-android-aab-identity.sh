#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
EXPECTED_ANDROID_GRADLE_PLUGIN_VERSION="8.10.1"
EXPECTED_R8_VERSION="8.10.24"
EXPECTED_MANIFEST_COMPONENTS_SHA256="4609a48938cd2a48d687f95a1937f777676b4e969934964437346247595dc6ad"
temporary_files=()

cleanup() {
  local file_path
  for file_path in "${temporary_files[@]-}"; do
    [[ -n "$file_path" ]] || continue
    rm -f "$file_path"
  done
}
trap cleanup EXIT
trap 'cleanup; exit 130' HUP INT TERM

fail() {
  echo "[android-aab-identity][error] $*" >&2
  exit 1
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  BUNDLETOOL_JAR=/path/to/pinned-bundletool.jar \
    scripts/verify-android-aab-identity.sh \
      <bundle.aab> <package-name> <version-name> <version-code> <source-commit>

This verifier derives the application identity from the AAB manifest and
requires the exact source commit, production-safe application/SDK policy,
exact reviewed application component/intent/authority surface, merged
permission set, and complete allowlisted Fearless native payload for every
supported ABI.
The caller remains responsible for verifying the bundletool binary and the AAB
signature before treating the artifact as releasable.
USAGE
}

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_regular_file() {
  local file_path="$1"
  local label="$2"
  local maximum_bytes="$3"
  local bytes

  [[ "$file_path" != *$'\n'* && "$file_path" != *$'\r'* ]] ||
    fail "$label path is malformed."
  [[ ! -L "$file_path" && -f "$file_path" && -s "$file_path" ]] ||
    fail "$label must be a non-empty regular, non-symlink file."
  bytes="$(wc -c < "$file_path" | tr -d '[:space:]')"
  [[ "$bytes" =~ ^[1-9][0-9]*$ ]] || fail "$label size is malformed."
  (( bytes <= maximum_bytes )) || fail "$label exceeds its maximum size."
}

expected_native_sha256() {
  case "$1" in
    base/lib/armeabi-v7a/libandroidx.graphics.path.so)
      echo "41399eba6fc2a60f6f14642375c1824f3cf25eb8fec7397d753730a3ceda3e2b"
      ;;
    base/lib/armeabi-v7a/libsodium.so)
      echo "8bcd304a813f3d814793c8553cd6a8814b64b2c188d66e8462f82053d7855343"
      ;;
    base/lib/armeabi-v7a/libsqlcipher.so)
      echo "4790ca6bbf562e6f5e477ed4ca73c0db60a0d0413a702d02884a35a67e6a7980"
      ;;
    base/lib/armeabi-v7a/libsr25519java.so)
      echo "d217b2511bf2c7f90ea29879d5ecad86c62d95a3ee2dd412fbb0f6d804e5f3de"
      ;;
    base/lib/arm64-v8a/libandroidx.graphics.path.so)
      echo "41e9a793c43a0f4fddb19e33f346bace464f30f888ba7b9eaf96294ea115bfb6"
      ;;
    base/lib/arm64-v8a/libsodium.so)
      echo "939ba2865a93abe39c4d6a29802419b36b1bfd0b320396eeaccd4d660468a664"
      ;;
    base/lib/arm64-v8a/libsqlcipher.so)
      echo "d63a8d6bc0bb1bb1575e201f5c4d6bbcda7b9464bb309fb2f88daa6e1ee4a57b"
      ;;
    base/lib/arm64-v8a/libsr25519java.so)
      echo "a37f031de78b841dab6a8c69900a64fe427d53d2a4f77d8f3c059caab5849c12"
      ;;
    base/lib/x86/libandroidx.graphics.path.so)
      echo "eb0570b41fd3bff25d8204a967c03bd7550719e768b791f680cc40cbe35f29af"
      ;;
    base/lib/x86/libsodium.so)
      echo "a6cd7f654ff400d080b16b3e794971b2c2b2cbc12bf0cee444f7e2ea950839ae"
      ;;
    base/lib/x86/libsqlcipher.so)
      echo "7799bfcdc38be283f02221d4d25acf8c8b3b13e12b95181791919fc315d7ed7b"
      ;;
    base/lib/x86/libsr25519java.so)
      echo "56f26fe8e7e55026cf36be6c22a62037389d4f59cc8ee35d6bb295b5dc5441aa"
      ;;
    base/lib/x86_64/libandroidx.graphics.path.so)
      echo "4e56c996f13670e70082658de7880c4020eabf4f25e43387f88ed78a713fc9f0"
      ;;
    base/lib/x86_64/libsodium.so)
      echo "a8b52ce229d18338b9f0b0f4d2396078582c9742d771367394b8e9b3cbabb81c"
      ;;
    base/lib/x86_64/libsqlcipher.so)
      echo "85dcbc655a1d1a34d0d6eb4d56d6563bf9aeceba8a6921c6c0d2252f474a4d6b"
      ;;
    base/lib/x86_64/libsr25519java.so)
      echo "b024dcc2ebf236f21d329e3d637a6fdd39746ad1dc0e0ccc99143279617946dc"
      ;;
    *)
      return 1
      ;;
  esac
}

source_native_path() {
  local entry="$1"
  echo "$ROOT_DIR/app/src/main/jniLibs/${entry#base/lib/}"
}

bundletool_manifest_value() {
  local artifact="$1"
  local xpath="$2"
  local label="$3"
  local value

  if ! value="$(
    java -jar "$BUNDLETOOL_JAR" dump manifest \
      --bundle "$artifact" \
      --xpath "$xpath"
  )"; then
    fail "bundletool could not read $label from the AAB manifest."
  fi
  [[ -n "$value" && "$value" != *$'\n'* && "$value" != *$'\r'* ]] ||
    fail "The AAB manifest contains a malformed $label."
  printf '%s' "$value"
}

build_config_integer() {
  local config_file="$1"
  local property_name="$2"
  local value

  if ! value="$(python3 - "$config_file" "$property_name" <<'PY'
import re
import sys

config_path, property_name = sys.argv[1:]
pattern = re.compile(
    rf"^[ \t]*{re.escape(property_name)}[ \t]*=[ \t]*"
    r"([1-9][0-9]*)[ \t]*(?://.*)?$"
)

try:
    with open(config_path, "r", encoding="utf-8") as config:
        matches = [
            match.group(1)
            for line in config
            if (match := pattern.fullmatch(line.rstrip("\r\n")))
        ]
except (OSError, UnicodeError) as error:
    print(
        "[android-aab-identity][error] "
        f"Unable to read authoritative build configuration: {error}.",
        file=sys.stderr,
    )
    sys.exit(1)

if len(matches) != 1:
    print(
        "[android-aab-identity][error] "
        f"Authoritative build configuration must define exactly one "
        f"literal {property_name} value; found {len(matches)}.",
        file=sys.stderr,
    )
    sys.exit(1)

print(matches[0], end="")
PY
  )"; then
    exit 1
  fi

  [[ "$value" =~ ^[1-9][0-9]*$ ]] ||
    fail "Authoritative $property_name is malformed."
  printf '%s' "$value"
}

[[ "$#" -eq 5 ]] || {
  usage
  exit 2
}

artifact="$1"
expected_package="$2"
expected_version_name="$3"
expected_version_code="$4"
expected_source_commit="$5"

[[ -n "${BUNDLETOOL_JAR:-}" ]] || fail "BUNDLETOOL_JAR is required."
command -v java >/dev/null 2>&1 || fail "java is required."
command -v unzip >/dev/null 2>&1 || fail "unzip is required."
command -v cmp >/dev/null 2>&1 || fail "cmp is required."
command -v python3 >/dev/null 2>&1 || fail "python3 is required."
command -v sort >/dev/null 2>&1 || fail "sort is required."

require_regular_file "$BUNDLETOOL_JAR" "BUNDLETOOL_JAR" 41943040
require_regular_file "$artifact" "AAB" 262144000
unzip -t "$artifact" >/dev/null || fail "The AAB is not a valid ZIP archive."

if ! android_toolchain_evidence="$(
  python3 - \
    "$artifact" \
    "$EXPECTED_ANDROID_GRADLE_PLUGIN_VERSION" \
    "$EXPECTED_R8_VERSION" <<'PY'
import json
import stat
import sys
import zipfile

artifact_path, expected_agp, expected_r8 = sys.argv[1:]
agp_paths = (
    "BUNDLE-METADATA/"
    "com.android.tools.build.gradle/app-metadata.properties",
    "base/root/META-INF/com/android/build/gradle/app-metadata.properties",
)
r8_path = "BUNDLE-METADATA/com.android.tools/r8.json"
expected_paths = {*agp_paths, r8_path}
expected_basenames = {
    path.rsplit("/", 1)[-1].casefold() for path in expected_paths
}
forbidden_dangling_services = {
    "base/root/META-INF/services/org.w3c.dom.DOMImplementationSourceList",
    "base/root/META-INF/services/org.xml.sax.driver",
}


def fail(message):
    print(f"[android-aab-identity][error] {message}", file=sys.stderr)
    raise SystemExit(1)


def reject_duplicate_keys(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            fail(f"R8 metadata contains duplicate JSON key {key!r}.")
        value[key] = item
    return value


def read_exact_metadata(archive, label, expected_path, maximum_size):
    matches = [
        info for info in archive.infolist() if info.filename == expected_path
    ]
    if len(matches) != 1:
        fail(
            f"The AAB must contain exactly one {label} entry at "
            f"{expected_path}; found {len(matches)}."
        )

    entry = matches[0]
    if entry.flag_bits & 0x1:
        fail(f"The embedded {label} entry must not be encrypted.")
    if entry.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}:
        fail(f"The embedded {label} entry uses unsupported compression.")
    unix_mode = (entry.external_attr >> 16) & 0xFFFF
    file_type = stat.S_IFMT(unix_mode)
    if file_type not in {0, stat.S_IFREG}:
        fail(f"The embedded {label} entry must be a regular non-symlink file.")
    if not 0 < entry.file_size <= maximum_size:
        fail(f"The embedded {label} entry has an unsafe size.")
    try:
        payload = archive.read(entry)
    except (OSError, RuntimeError, zipfile.BadZipFile) as error:
        fail(f"Unable to read embedded {label}: {error}.")
    if len(payload) != entry.file_size:
        fail(f"The embedded {label} size changed while it was read.")
    return payload


try:
    with zipfile.ZipFile(artifact_path, "r") as archive:
        for entry in archive.infolist():
            portable_name = entry.filename.replace("\\", "/")
            if entry.filename in forbidden_dangling_services:
                fail(
                    "The AAB contains a dangling R8 ServiceLoader descriptor: "
                    f"{entry.filename}."
                )
            basename = portable_name.rsplit("/", 1)[-1].casefold()
            if basename not in expected_basenames:
                continue
            if entry.filename not in expected_paths:
                fail(
                    "The AAB contains Android toolchain metadata at a "
                    f"non-canonical path: {entry.filename!r}."
                )
        agp_payloads = [
            read_exact_metadata(
                archive,
                "Android Gradle plugin metadata",
                path,
                16 * 1024,
            )
            for path in agp_paths
        ]
        if agp_payloads[0] != agp_payloads[1]:
            fail(
                "The two embedded Android Gradle plugin metadata entries "
                "must be byte-identical."
            )
        agp_payload = agp_payloads[0]
        r8_payload = read_exact_metadata(
            archive,
            "R8 metadata",
            r8_path,
            2 * 1024 * 1024,
        )
except (OSError, RuntimeError, zipfile.BadZipFile) as error:
    print(
        "[android-aab-identity][error] "
        f"Unable to inspect embedded Android toolchain metadata: {error}.",
        file=sys.stderr,
    )
    sys.exit(1)

try:
    agp_text = agp_payload.decode("utf-8")
except UnicodeDecodeError as error:
    fail(f"Android Gradle plugin metadata is not valid UTF-8: {error}.")
if (
    agp_text.startswith("\ufeff")
    or "\x00" in agp_text
    or "\r" in agp_text
):
    fail("Android Gradle plugin metadata contains unsafe text encoding.")

properties = {}
for line in agp_text.splitlines():
    if not line or "=" not in line:
        fail("Android Gradle plugin metadata contains a malformed property.")
    key, value = line.split("=", 1)
    if key in properties:
        fail(f"Android Gradle plugin metadata duplicates property {key!r}.")
    if not key or not value or key.strip() != key or value.strip() != value:
        fail("Android Gradle plugin metadata contains a malformed property.")
    properties[key] = value

expected_property_keys = {
    "androidGradlePluginVersion",
    "appMetadataVersion",
}
if set(properties) != expected_property_keys:
    fail(
        "Android Gradle plugin metadata must contain exactly the reviewed "
        "application metadata keys."
    )
if properties["appMetadataVersion"] != "1.1":
    fail("AAB application metadata schema must be exactly 1.1.")
actual_agp = properties["androidGradlePluginVersion"]
if actual_agp != expected_agp:
    fail(
        "AAB Android Gradle plugin mismatch: "
        f"expected {expected_agp}, got {actual_agp}."
    )
expected_agp_payload = (
    "appMetadataVersion=1.1\n"
    f"androidGradlePluginVersion={expected_agp}\n"
).encode("utf-8")
if agp_payload != expected_agp_payload:
    fail(
        "Android Gradle plugin metadata bytes differ from the exact reviewed "
        "two-line content."
    )

try:
    r8_text = r8_payload.decode("utf-8")
except UnicodeDecodeError as error:
    fail(f"R8 metadata is not valid UTF-8: {error}.")
if r8_text.startswith("\ufeff") or "\x00" in r8_text:
    fail("R8 metadata contains unsafe text encoding.")
try:
    r8_metadata = json.loads(
        r8_text,
        object_pairs_hook=reject_duplicate_keys,
        parse_constant=lambda value: fail(
            f"R8 metadata contains non-finite JSON value {value}."
        ),
    )
except (json.JSONDecodeError, UnicodeError) as error:
    fail(f"R8 metadata is not safe parseable JSON: {error}.")
if not isinstance(r8_metadata, dict):
    fail("R8 metadata must be a JSON object.")
actual_r8 = r8_metadata.get("version")
if actual_r8 != expected_r8:
    fail(f"AAB R8 mismatch: expected {expected_r8}, got {actual_r8!r}.")

print(f"agp={actual_agp} r8={actual_r8}", end="")
PY
)"; then
  exit 1
fi
[[ "$android_toolchain_evidence" == \
  "agp=$EXPECTED_ANDROID_GRADLE_PLUGIN_VERSION r8=$EXPECTED_R8_VERSION" ]] ||
  fail "Embedded Android toolchain evidence is malformed."

build_config_file="$ROOT_DIR/build.gradle"
require_regular_file \
  "$build_config_file" \
  "authoritative Android build configuration" \
  2097152
expected_min_sdk="$(build_config_integer "$build_config_file" minSdkVersion)"
expected_target_sdk="$(build_config_integer "$build_config_file" targetSdkVersion)"
(( expected_min_sdk <= expected_target_sdk )) ||
  fail "Authoritative minSdkVersion exceeds targetSdkVersion."

[[ "$expected_package" =~ ^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$ ]] ||
  fail "The expected package name is malformed."
[[ "$expected_version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][0-9A-Za-z.-]+)?$ ]] ||
  fail "The expected versionName is malformed."
[[ "$expected_version_code" =~ ^[1-9][0-9]*$ ]] ||
  fail "The expected versionCode is malformed."
[[ "$expected_source_commit" == "development" ||
  "$expected_source_commit" =~ ^[0-9a-f]{40}$ ]] ||
  fail "The expected source commit is malformed."

actual_package="$(
  bundletool_manifest_value "$artifact" "/manifest/@package" "package name"
)"
actual_version_code="$(
  bundletool_manifest_value \
    "$artifact" \
    "/manifest/@android:versionCode" \
    "versionCode"
)"
actual_version_name="$(
  bundletool_manifest_value \
    "$artifact" \
    "/manifest/@android:versionName" \
    "versionName"
)"
actual_source_commit="$(
  bundletool_manifest_value \
    "$artifact" \
    "/manifest/application/meta-data[@android:name='jp.co.soramitsu.fearless.RELEASE_COMMIT']/@android:value" \
    "source commit"
)"

[[ "$actual_package" == "$expected_package" ]] ||
  fail "AAB package mismatch: expected $expected_package, got $actual_package."
[[ "$actual_version_name" == "$expected_version_name" ]] ||
  fail "AAB versionName mismatch: expected $expected_version_name, got $actual_version_name."
[[ "$actual_version_code" == "$expected_version_code" ]] ||
  fail "AAB versionCode mismatch: expected $expected_version_code, got $actual_version_code."
[[ "$actual_source_commit" == "$expected_source_commit" ]] ||
  fail "AAB source commit mismatch: expected $expected_source_commit, got $actual_source_commit."

manifest_file="$(mktemp)"
permissions_file="$(mktemp)"
components_file="$(mktemp)"
temporary_files+=("$manifest_file" "$permissions_file" "$components_file")
if ! java -jar "$BUNDLETOOL_JAR" dump manifest \
  --bundle "$artifact" > "$manifest_file"; then
  fail "bundletool could not dump the merged AAB manifest."
fi
require_regular_file "$manifest_file" "merged AAB manifest" 8388608

if ! python3 - \
  "$manifest_file" \
  "$permissions_file" \
  "$components_file" \
  "$expected_package" \
  "$expected_min_sdk" \
  "$expected_target_sdk" \
  "$EXPECTED_MANIFEST_COMPONENTS_SHA256" <<'PY'
import hashlib
import json
import re
import sys
import xml.etree.ElementTree as ElementTree

(
    manifest_path,
    permissions_path,
    components_path,
    expected_package,
    expected_min_sdk,
    expected_target_sdk,
    expected_components_sha256,
) = sys.argv[1:]
android_namespace = "{http://schemas.android.com/apk/res/android}"
permission_tags = {
    "uses-permission",
    "uses-permission-sdk-23",
    "uses-permission-sdk-m",
}
forbidden_permissions = {
    "android.permission.ACCESS_MEDIA_LOCATION",
    "android.permission.MANAGE_EXTERNAL_STORAGE",
    "android.permission.READ_EXTERNAL_STORAGE",
    "android.permission.READ_MEDIA_AUDIO",
    "android.permission.READ_MEDIA_IMAGES",
    "android.permission.READ_MEDIA_VIDEO",
    "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
    "android.permission.WRITE_EXTERNAL_STORAGE",
}
expected_permissions = {
    ("uses-permission", "android.permission.ACCESS_NETWORK_STATE", ""),
    ("uses-permission", "android.permission.CAMERA", ""),
    ("uses-permission", "android.permission.INTERNET", ""),
    ("uses-permission", "android.permission.POST_NOTIFICATIONS", ""),
    ("uses-permission", "android.permission.USE_BIOMETRIC", ""),
    ("uses-permission", "android.permission.USE_FINGERPRINT", ""),
    ("uses-permission", "android.permission.VIBRATE", ""),
    ("uses-permission", "android.permission.WAKE_LOCK", ""),
    ("uses-permission", "com.google.android.c2dm.permission.RECEIVE", ""),
    (
        "uses-permission",
        f"{expected_package}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
        "",
    ),
}

production_package = "jp.co.soramitsu.fearless"
application_class = "jp.co.soramitsu.app.App"
startup_activity = (
    "jp.co.soramitsu.app.root.presentation.WalletStartupActivity"
)
root_activity = "jp.co.soramitsu.app.root.presentation.RootActivity"
wallet_root_activity = (
    "jp.co.soramitsu.app.root.presentation.WalletRootActivity"
)
component_tags = {"activity", "activity-alias", "service", "receiver", "provider"}
data_attribute_names = (
    "scheme",
    "host",
    "port",
    "path",
    "pathPrefix",
    "pathPattern",
    "pathAdvancedPattern",
    "mimeType",
)
class_name_pattern = re.compile(
    r"^[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)+$"
)
authority_pattern = re.compile(
    r"^[a-z][a-z0-9]*(?:[._-][a-z0-9]+)*$"
)


def canonical_json(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"))


def expected_data(**values):
    return {
        attribute_name: values.get(attribute_name, "")
        for attribute_name in data_attribute_names
    }


def expected_filter(
    actions,
    categories=(),
    data=(),
    *,
    auto_verify=False,
    priority=0,
    order=0,
):
    return {
        "actions": sorted(actions),
        "autoVerify": auto_verify,
        "categories": sorted(categories),
        "data": sorted(data, key=canonical_json),
        "order": order,
        "priority": priority,
    }


def expected_component(
    component_type,
    name,
    *,
    exported=False,
    permission="",
    intent_filters=(),
    authorities=(),
    read_permission="",
    write_permission="",
    grant_uri_permissions=False,
    target_activity="",
):
    return {
        "authorities": sorted(authorities),
        "enabled": True,
        "exported": exported,
        "grantUriPermissions": grant_uri_permissions,
        "intentFilters": sorted(intent_filters, key=canonical_json),
        "name": name,
        "permission": permission,
        "readPermission": read_permission,
        "targetActivity": target_activity,
        "type": component_type,
        "writePermission": write_permission,
    }


view_json_filter = expected_filter(
    ["android.intent.action.VIEW"],
    ["android.intent.category.DEFAULT"],
    [expected_data(mimeType="application/json")],
)
launcher_filter = expected_filter(
    ["android.intent.action.MAIN"],
    ["android.intent.category.LAUNCHER"],
)
buy_success_filter = expected_filter(
    ["android.intent.action.VIEW"],
    [
        "android.intent.category.DEFAULT",
        "android.intent.category.BROWSABLE",
    ],
    [expected_data(scheme="fearless"), expected_data(host="buy-success")],
)
ton_connect_filter = expected_filter(
    ["android.intent.action.VIEW"],
    [
        "android.intent.category.DEFAULT",
        "android.intent.category.BROWSABLE",
    ],
    [
        expected_data(scheme="https"),
        expected_data(host="fearlesswallet.io"),
        expected_data(pathPrefix="/ton-connect/"),
    ],
    auto_verify=True,
)

expected_components = [
    expected_component(
        "activity",
        "androidx.credentials.playservices.controllers.identityauth.HiddenActivity",
    ),
    expected_component(
        "activity",
        "androidx.credentials.playservices.controllers.identitycredentials.IdentityCredentialApiHiddenActivity",
    ),
    expected_component(
        "activity",
        "com.google.android.gms.auth.api.signin.internal.SignInHubActivity",
    ),
    expected_component(
        "activity",
        "com.google.android.gms.common.api.GoogleApiActivity",
    ),
    expected_component(
        "activity",
        "com.journeyapps.barcodescanner.CaptureActivity",
    ),
    expected_component(
        "activity",
        "jp.co.soramitsu.common.qrScanner.QrScannerActivity",
    ),
    expected_component(
        "activity",
        "jp.co.soramitsu.common.scan.ScannerActivity",
    ),
    expected_component("activity", root_activity),
    expected_component(
        "activity",
        startup_activity,
        exported=True,
        intent_filters=[
            view_json_filter,
            launcher_filter,
            buy_success_filter,
            ton_connect_filter,
        ],
    ),
    expected_component("activity", wallet_root_activity),
    expected_component(
        "service",
        "androidx.credentials.playservices.CredentialProviderMetadataHolder",
    ),
    expected_component(
        "service",
        "androidx.room.MultiInstanceInvalidationService",
    ),
    expected_component(
        "service",
        "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
    ),
    expected_component(
        "service",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
        permission="android.permission.BIND_JOB_SERVICE",
    ),
    expected_component(
        "service",
        "com.google.android.gms.auth.api.signin.RevocationBoundService",
        exported=True,
        permission=(
            "com.google.android.gms.auth.api.signin.permission."
            "REVOCATION_NOTIFICATION"
        ),
    ),
    expected_component(
        "service",
        "com.google.firebase.components.ComponentDiscoveryService",
    ),
    expected_component(
        "service",
        "com.google.firebase.messaging.FirebaseMessagingService",
        intent_filters=[
            expected_filter(
                ["com.google.firebase.MESSAGING_EVENT"],
                priority=-500,
            )
        ],
    ),
    expected_component(
        "receiver",
        "androidx.profileinstaller.ProfileInstallReceiver",
        exported=True,
        permission="android.permission.DUMP",
        intent_filters=[
            expected_filter(["androidx.profileinstaller.action.INSTALL_PROFILE"]),
            expected_filter(["androidx.profileinstaller.action.SKIP_FILE"]),
            expected_filter(["androidx.profileinstaller.action.SAVE_PROFILE"]),
            expected_filter(
                ["androidx.profileinstaller.action.BENCHMARK_OPERATION"]
            ),
        ],
    ),
    expected_component(
        "receiver",
        "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
    ),
    expected_component(
        "receiver",
        "com.google.firebase.iid.FirebaseInstanceIdReceiver",
        exported=True,
        permission="com.google.android.c2dm.permission.SEND",
        intent_filters=[
            expected_filter(["com.google.android.c2dm.intent.RECEIVE"])
        ],
    ),
    expected_component(
        "provider",
        "androidx.core.content.FileProvider",
        authorities=[f"{production_package}.provider"],
        grant_uri_permissions=True,
    ),
    expected_component(
        "provider",
        "androidx.startup.InitializationProvider",
        authorities=[f"{production_package}.androidx-startup"],
    ),
    expected_component(
        "provider",
        "com.google.firebase.provider.FirebaseInitProvider",
        authorities=[f"{production_package}.firebaseinitprovider"],
    ),
    expected_component(
        "provider",
        "it.airgap.beaconsdk.core.provider.BeaconInitProvider",
        authorities=[f"{production_package}.beaconinitprovider"],
    ),
]
expected_components.sort(key=lambda component: (component["type"], component["name"]))
reviewed_inventory = {
    "application": {
        "enabled": True,
        "name": application_class,
        "permission": "",
    },
    "components": expected_components,
    "launcherComponent": startup_activity,
    "launcherTarget": startup_activity,
    "package": production_package,
    "schemaVersion": 1,
    "startupChain": [startup_activity, root_activity, wallet_root_activity],
}
reviewed_inventory_payload = (canonical_json(reviewed_inventory) + "\n").encode(
    "utf-8"
)
reviewed_inventory_sha256 = hashlib.sha256(reviewed_inventory_payload).hexdigest()
if reviewed_inventory_sha256 != expected_components_sha256:
    print(
        "[android-aab-identity][error] The reviewed component inventory "
        "digest constant is stale: "
        f"expected {expected_components_sha256}, computed "
        f"{reviewed_inventory_sha256}.",
        file=sys.stderr,
    )
    sys.exit(1)

try:
    root = ElementTree.parse(manifest_path).getroot()
except (ElementTree.ParseError, OSError) as error:
    print(
        "[android-aab-identity][error] "
        f"The merged AAB manifest is not safe parseable XML: {error}.",
        file=sys.stderr,
    )
    sys.exit(1)

if root.tag != "manifest":
    print(
        "[android-aab-identity][error] "
        f"Unexpected merged AAB manifest root: {root.tag!r}.",
        file=sys.stderr,
    )
    sys.exit(1)


def fail_manifest(message):
    print(
        f"[android-aab-identity][error] {message}",
        file=sys.stderr,
    )
    sys.exit(1)


def android_attribute(element, name):
    return element.get(f"{android_namespace}{name}")


def parse_boolean_attribute(element, name, default):
    value = android_attribute(element, name)
    if value is None:
        return default
    if value not in {"true", "false"}:
        fail_manifest(
            f"{element.tag} {android_attribute(element, 'name')!r} has "
            f"non-literal android:{name}={value!r}."
        )
    return value == "true"


def parse_literal_attribute(element, name, *, required=False):
    value = android_attribute(element, name)
    if value is None:
        if required:
            fail_manifest(
                f"{element.tag} is missing required android:{name}."
            )
        return ""
    if (
        not value
        or value != value.strip()
        or any(character in value for character in "\r\n\0")
        or value.startswith(("@", "?"))
    ):
        fail_manifest(
            f"{element.tag} has malformed android:{name}={value!r}."
        )
    return value


def parse_integer_attribute(element, name, default):
    value = android_attribute(element, name)
    if value is None:
        return default
    if not re.fullmatch(r"-?(?:0|[1-9][0-9]*)", value):
        fail_manifest(
            f"intent-filter has non-literal android:{name}={value!r}."
        )
    return int(value)


def assert_only_attributes(element, allowed_android_names):
    allowed = {
        f"{android_namespace}{name}" for name in allowed_android_names
    }
    unexpected = sorted(set(element.attrib) - allowed)
    if unexpected:
        fail_manifest(
            f"{element.tag} contains unexpected attribute(s): {unexpected!r}."
        )


def parse_intent_filter(element):
    assert_only_attributes(element, {"autoVerify", "priority", "order"})
    actions = []
    categories = []
    data_entries = []
    for child in element:
        if child.tag == "action":
            assert_only_attributes(child, {"name"})
            if list(child):
                fail_manifest("action must not contain child elements.")
            actions.append(parse_literal_attribute(child, "name", required=True))
        elif child.tag == "category":
            assert_only_attributes(child, {"name"})
            if list(child):
                fail_manifest("category must not contain child elements.")
            categories.append(
                parse_literal_attribute(child, "name", required=True)
            )
        elif child.tag == "data":
            assert_only_attributes(child, set(data_attribute_names))
            if list(child):
                fail_manifest("data must not contain child elements.")
            data_entry = {
                name: parse_literal_attribute(child, name)
                for name in data_attribute_names
            }
            if not any(data_entry.values()):
                fail_manifest("intent-filter data element must not be empty.")
            data_entries.append(data_entry)
        else:
            fail_manifest(
                f"intent-filter contains unexpected child {child.tag!r}."
            )
    if not actions:
        fail_manifest("intent-filter must declare at least one action.")
    if len(actions) != len(set(actions)):
        fail_manifest("intent-filter contains duplicate action declarations.")
    if len(categories) != len(set(categories)):
        fail_manifest("intent-filter contains duplicate category declarations.")
    data_payloads = [canonical_json(entry) for entry in data_entries]
    if len(data_payloads) != len(set(data_payloads)):
        fail_manifest("intent-filter contains duplicate data declarations.")
    return {
        "actions": sorted(actions),
        "autoVerify": parse_boolean_attribute(element, "autoVerify", False),
        "categories": sorted(categories),
        "data": sorted(data_entries, key=canonical_json),
        "order": parse_integer_attribute(element, "order", 0),
        "priority": parse_integer_attribute(element, "priority", 0),
    }


def parse_component(element):
    name = parse_literal_attribute(element, "name", required=True)
    if not class_name_pattern.fullmatch(name):
        fail_manifest(
            f"Component name must be canonical and fully qualified: {name!r}."
        )

    intent_filters = []
    for child in element:
        if child.tag == "intent-filter":
            intent_filters.append(parse_intent_filter(child))
        elif child.tag in {"meta-data", "property"}:
            continue
        elif child.tag in {"grant-uri-permission", "path-permission"}:
            fail_manifest(
                f"Provider {name!r} contains unreviewed {child.tag!r} policy."
            )
        else:
            fail_manifest(
                f"Component {name!r} contains unexpected child {child.tag!r}."
            )
    filter_payloads = [canonical_json(item) for item in intent_filters]
    if len(filter_payloads) != len(set(filter_payloads)):
        fail_manifest(f"Component {name!r} contains duplicate intent filters.")

    if element.tag == "provider":
        default_exported = False
    else:
        default_exported = bool(intent_filters)
    enabled = parse_boolean_attribute(element, "enabled", True)
    exported = parse_boolean_attribute(element, "exported", default_exported)
    permission = parse_literal_attribute(element, "permission")

    authorities = []
    read_permission = ""
    write_permission = ""
    grant_uri_permissions = False
    if element.tag == "provider":
        authorities_value = parse_literal_attribute(
            element, "authorities", required=True
        )
        authorities = authorities_value.split(";")
        if any(
            not authority_pattern.fullmatch(authority)
            or authority != authority.casefold()
            for authority in authorities
        ):
            fail_manifest(
                f"Provider {name!r} has non-canonical authorities "
                f"{authorities_value!r}."
            )
        if len(authorities) != len(set(authorities)):
            fail_manifest(f"Provider {name!r} repeats an authority.")
        read_permission = parse_literal_attribute(element, "readPermission")
        write_permission = parse_literal_attribute(element, "writePermission")
        grant_uri_permissions = parse_boolean_attribute(
            element, "grantUriPermissions", False
        )

    target_activity = ""
    if element.tag == "activity-alias":
        target_activity = parse_literal_attribute(
            element, "targetActivity", required=True
        )
        if not class_name_pattern.fullmatch(target_activity):
            fail_manifest(
                "Activity-alias target must be canonical and fully qualified: "
                f"{target_activity!r}."
            )

    return {
        "authorities": sorted(authorities),
        "enabled": enabled,
        "exported": exported,
        "grantUriPermissions": grant_uri_permissions,
        "intentFilters": sorted(intent_filters, key=canonical_json),
        "name": name,
        "permission": permission,
        "readPermission": read_permission,
        "targetActivity": target_activity,
        "type": element.tag,
        "writePermission": write_permission,
    }


if expected_package != production_package:
    fail_manifest(
        "The component inventory is sealed to production package "
        f"{production_package}; got {expected_package!r}."
    )
actual_manifest_package = root.get("package")
if actual_manifest_package != expected_package:
    fail_manifest(
        "Merged AAB manifest package mismatch: "
        f"expected {expected_package}, got {actual_manifest_package!r}."
    )


uses_sdk_elements = [element for element in root if element.tag == "uses-sdk"]
if len(uses_sdk_elements) != 1:
    fail_manifest(
        "The merged AAB manifest must contain exactly one direct uses-sdk "
        f"declaration; found {len(uses_sdk_elements)}."
    )

uses_sdk = uses_sdk_elements[0]
actual_min_sdk = uses_sdk.get(f"{android_namespace}minSdkVersion")
actual_target_sdk = uses_sdk.get(f"{android_namespace}targetSdkVersion")
if actual_min_sdk != expected_min_sdk:
    fail_manifest(
        "AAB minSdkVersion mismatch: "
        f"expected {expected_min_sdk}, got {actual_min_sdk!r}."
    )
if actual_target_sdk != expected_target_sdk:
    fail_manifest(
        "AAB targetSdkVersion mismatch: "
        f"expected {expected_target_sdk}, got {actual_target_sdk!r}."
    )
if uses_sdk.get(f"{android_namespace}maxSdkVersion") is not None:
    fail_manifest("The merged AAB manifest must not set maxSdkVersion.")

application_elements = [
    element for element in root if element.tag == "application"
]
if len(application_elements) != 1:
    fail_manifest(
        "The merged AAB manifest must contain exactly one direct application "
        f"declaration; found {len(application_elements)}."
    )

application = application_elements[0]
allow_backup = application.get(f"{android_namespace}allowBackup")
if allow_backup != "false":
    fail_manifest(
        "The merged AAB manifest must explicitly set android:allowBackup=false; "
        f"got {allow_backup!r}."
    )

for attribute_name in ("debuggable", "testOnly"):
    attribute_value = application.get(f"{android_namespace}{attribute_name}")
    if attribute_value not in (None, "false"):
        fail_manifest(
            "The merged AAB manifest must leave "
            f"android:{attribute_name} absent or set it to false; "
            f"got {attribute_value!r}."
        )

uses_cleartext_traffic = application.get(
    f"{android_namespace}usesCleartextTraffic"
)
if uses_cleartext_traffic != "false":
    fail_manifest(
        "The merged AAB manifest must explicitly set "
        "android:usesCleartextTraffic=false; "
        f"got {uses_cleartext_traffic!r}."
    )

network_security_config = application.get(
    f"{android_namespace}networkSecurityConfig"
)
if network_security_config != "@xml/network_security_config":
    fail_manifest(
        "The merged AAB manifest must reference the exact approved "
        "android:networkSecurityConfig=@xml/network_security_config; "
        f"got {network_security_config!r}."
    )

actual_application = {
    "enabled": parse_boolean_attribute(application, "enabled", True),
    "name": parse_literal_attribute(application, "name", required=True),
    "permission": parse_literal_attribute(application, "permission"),
}
if actual_application != reviewed_inventory["application"]:
    fail_manifest(
        "The merged AAB application identity or default security policy "
        "differs from the reviewed allowlist: "
        f"expected {reviewed_inventory['application']!r}, "
        f"got {actual_application!r}."
    )

actual_components = [
    parse_component(element)
    for element in application
    if element.tag in component_tags
]
if not actual_components:
    fail_manifest(
        "The merged AAB application must not have an empty component surface."
    )
component_keys = [
    (component["type"], component["name"])
    for component in actual_components
]
duplicate_component_keys = sorted(
    key for key in set(component_keys) if component_keys.count(key) > 1
)
if duplicate_component_keys:
    fail_manifest(
        "The merged AAB manifest contains duplicate components: "
        f"{duplicate_component_keys!r}."
    )

all_authorities = [
    authority
    for component in actual_components
    for authority in component["authorities"]
]
duplicate_authorities = sorted(
    authority
    for authority in set(all_authorities)
    if all_authorities.count(authority) > 1
)
casefolded_authorities = [authority.casefold() for authority in all_authorities]
casefold_duplicate_authorities = sorted(
    authority
    for authority in set(casefolded_authorities)
    if casefolded_authorities.count(authority) > 1
)
if duplicate_authorities or casefold_duplicate_authorities:
    fail_manifest(
        "Provider authorities must be globally unique: "
        f"duplicates={duplicate_authorities!r}, "
        f"casefoldDuplicates={casefold_duplicate_authorities!r}."
    )

launcher_filters = []
for component in actual_components:
    if component["type"] not in {"activity", "activity-alias"}:
        continue
    for intent_filter in component["intentFilters"]:
        if (
            "android.intent.action.MAIN" in intent_filter["actions"]
            and "android.intent.category.LAUNCHER"
            in intent_filter["categories"]
            and component["enabled"]
            and component["exported"]
        ):
            launcher_filters.append(component)
if len(launcher_filters) != 1:
    fail_manifest(
        "The merged AAB manifest must contain exactly one enabled/exported "
        "MAIN+LAUNCHER activity or activity-alias; found "
        f"{len(launcher_filters)}."
    )
launcher_component = launcher_filters[0]
if launcher_component["type"] == "activity":
    launcher_target = launcher_component["name"]
else:
    launcher_target = launcher_component["targetActivity"]
    target_matches = [
        component
        for component in actual_components
        if component["type"] == "activity"
        and component["name"] == launcher_target
    ]
    if len(target_matches) != 1 or not target_matches[0]["enabled"]:
        fail_manifest(
            "The launcher activity-alias must resolve to exactly one enabled "
            f"activity; target={launcher_target!r}."
        )
if launcher_target != startup_activity:
    fail_manifest(
        "The launcher must resolve to the reviewed wallet startup activity; "
        f"got {launcher_target!r}."
    )

component_by_key = {
    (component["type"], component["name"]): component
    for component in actual_components
}
for chain_activity, required_exported in (
    (startup_activity, True),
    (root_activity, False),
    (wallet_root_activity, False),
):
    chain_component = component_by_key.get(("activity", chain_activity))
    if (
        chain_component is None
        or not chain_component["enabled"]
        or chain_component["exported"] != required_exported
    ):
        fail_manifest(
            "The wallet startup activity chain is missing or has unsafe "
            f"enabled/exported state at {chain_activity!r}."
        )

for component in actual_components:
    if (
        component["exported"]
        and component["name"] != launcher_component["name"]
        and not component["permission"]
    ):
        fail_manifest(
            "An exported non-launcher component lacks a required permission: "
            f"{component['type']} {component['name']}."
        )

actual_components.sort(
    key=lambda component: (component["type"], component["name"])
)
actual_by_key = {
    (component["type"], component["name"]): component
    for component in actual_components
}
expected_by_key = {
    (component["type"], component["name"]): component
    for component in expected_components
}
if actual_by_key != expected_by_key:
    actual_keys = set(actual_by_key)
    expected_keys = set(expected_by_key)
    missing = sorted(expected_keys - actual_keys)
    unexpected = sorted(actual_keys - expected_keys)
    changed = sorted(
        key
        for key in actual_keys & expected_keys
        if actual_by_key[key] != expected_by_key[key]
    )
    fail_manifest(
        "The exact manifest component surface differs from the reviewed "
        "allowlist: "
        f"missing={missing!r}, unexpected={unexpected!r}, "
        f"changed={changed!r}."
    )

actual_inventory = {
    "application": actual_application,
    "components": actual_components,
    "launcherComponent": launcher_component["name"],
    "launcherTarget": launcher_target,
    "package": actual_manifest_package,
    "schemaVersion": 1,
    "startupChain": [startup_activity, root_activity, wallet_root_activity],
}
actual_inventory_payload = (canonical_json(actual_inventory) + "\n").encode(
    "utf-8"
)
actual_inventory_sha256 = hashlib.sha256(actual_inventory_payload).hexdigest()
if actual_inventory_sha256 != expected_components_sha256:
    fail_manifest(
        "The exact manifest component inventory digest differs from the "
        "reviewed digest: "
        f"expected {expected_components_sha256}, got "
        f"{actual_inventory_sha256}."
    )
try:
    with open(components_path, "wb") as output:
        output.write(actual_inventory_payload)
except OSError as error:
    fail_manifest(f"Unable to record verified manifest components: {error}.")

actual_permissions = []
for element in root:
    if element.tag not in permission_tags:
        continue
    name = element.get(f"{android_namespace}name")
    maximum_sdk = element.get(f"{android_namespace}maxSdkVersion", "")
    if (
        not name
        or any(character in name for character in "\r\n\0")
        or any(character in maximum_sdk for character in "\r\n\0")
    ):
        print(
            "[android-aab-identity][error] "
            "The merged AAB manifest contains a malformed permission.",
            file=sys.stderr,
        )
        sys.exit(1)
    actual_permissions.append((element.tag, name, maximum_sdk))

duplicates = sorted(
    permission
    for permission in set(actual_permissions)
    if actual_permissions.count(permission) > 1
)
if duplicates:
    print(
        "[android-aab-identity][error] "
        "The merged AAB manifest contains duplicate permission declarations: "
        + ", ".join(permission[1] for permission in duplicates)
        + ".",
        file=sys.stderr,
    )
    sys.exit(1)

forbidden_present = sorted(
    name
    for _, name, _ in actual_permissions
    if name in forbidden_permissions
)
if forbidden_present:
    print(
        "[android-aab-identity][error] "
        "The exact AAB requests forbidden broad media/storage permission(s): "
        + ", ".join(forbidden_present)
        + ".",
        file=sys.stderr,
    )
    sys.exit(1)

actual_set = set(actual_permissions)
if actual_set != expected_permissions:
    missing = sorted(expected_permissions - actual_set)
    unexpected = sorted(actual_set - expected_permissions)
    print(
        "[android-aab-identity][error] "
        "The exact AAB permission set differs from the approved allowlist.",
        file=sys.stderr,
    )
    if missing:
        print(f"missing={missing!r}", file=sys.stderr)
    if unexpected:
        print(f"unexpected={unexpected!r}", file=sys.stderr)
    sys.exit(1)

try:
    with open(permissions_path, "w", encoding="utf-8", newline="\n") as output:
        for tag, name, maximum_sdk in sorted(actual_set):
            output.write(f"{tag}\t{name}\t{maximum_sdk}\n")
except OSError as error:
    print(
        "[android-aab-identity][error] "
        f"Unable to record the verified AAB permissions: {error}.",
        file=sys.stderr,
    )
    sys.exit(1)
PY
then
  exit 1
fi
require_regular_file "$permissions_file" "verified AAB permissions" 65536
permissions_sha256="$(sha256_file "$permissions_file")"
require_regular_file \
  "$components_file" \
  "verified AAB component inventory" \
  1048576
components_sha256="$(sha256_file "$components_file")"
[[ "$components_sha256" == "$EXPECTED_MANIFEST_COMPONENTS_SHA256" ]] ||
  fail "Verified AAB component inventory digest changed after validation."

if ! network_security_evidence="$(python3 - "$artifact" <<'PY'
import hashlib
import re
import sys
import zipfile

artifact_path = sys.argv[1]


class PolicyError(Exception):
    pass


def fail(message):
    raise PolicyError(message)


def read_varint(payload, offset):
    value = 0
    shift = 0
    while True:
        if offset >= len(payload) or shift >= 70:
            fail("The compiled network policy contains a malformed varint.")
        current = payload[offset]
        offset += 1
        value |= (current & 0x7F) << shift
        if current < 0x80:
            return value, offset
        shift += 7


def parse_message(payload):
    fields = []
    offset = 0
    while offset < len(payload):
        key, offset = read_varint(payload, offset)
        field_number = key >> 3
        wire_type = key & 0x07
        if field_number == 0:
            fail("The compiled network policy contains field zero.")
        if wire_type == 0:
            value, offset = read_varint(payload, offset)
        elif wire_type == 1:
            end = offset + 8
            if end > len(payload):
                fail("The compiled network policy is truncated.")
            value = payload[offset:end]
            offset = end
        elif wire_type == 2:
            length, offset = read_varint(payload, offset)
            end = offset + length
            if end > len(payload):
                fail("The compiled network policy is truncated.")
            value = payload[offset:end]
            offset = end
        elif wire_type == 5:
            end = offset + 4
            if end > len(payload):
                fail("The compiled network policy is truncated.")
            value = payload[offset:end]
            offset = end
        else:
            fail(
                "The compiled network policy uses an unsupported protobuf "
                f"wire type: {wire_type}."
            )
        fields.append((field_number, wire_type, value))
    return fields


def field_values(fields, field_number, wire_type):
    values = []
    for actual_number, actual_wire, value in fields:
        if actual_number != field_number:
            continue
        if actual_wire != wire_type:
            fail(
                "The compiled network policy encodes a known field with "
                "the wrong protobuf wire type."
            )
        values.append(value)
    return values


def require_allowed_fields(fields, allowed, description):
    unexpected = sorted({number for number, _, _ in fields} - set(allowed))
    if unexpected:
        fail(
            f"The compiled {description} contains unexpected field(s): "
            + ", ".join(str(number) for number in unexpected)
            + "."
        )


def decode_utf8(value, description):
    try:
        decoded = value.decode("utf-8")
    except UnicodeDecodeError as error:
        fail(f"The compiled {description} is not valid UTF-8: {error}.")
    if not decoded or any(character in decoded for character in "\x00\r\n"):
        fail(f"The compiled {description} is malformed.")
    return decoded


def decode_compiled_boolean(value):
    item = parse_message(value)
    require_allowed_fields(item, {7}, "network-policy item")
    primitives = field_values(item, 7, 2)
    if len(primitives) != 1:
        fail("The cleartext policy must compile to exactly one primitive.")
    primitive = parse_message(primitives[0])
    require_allowed_fields(primitive, {8}, "network-policy primitive")
    booleans = field_values(primitive, 8, 0)
    if len(booleans) != 1 or booleans[0] not in (0, 1):
        fail("The cleartext policy does not contain one compiled boolean.")
    return booleans[0] == 1


def decode_attribute(payload):
    fields = parse_message(payload)
    require_allowed_fields(fields, {2, 3, 4, 6}, "network-policy attribute")
    names = field_values(fields, 2, 2)
    values = field_values(fields, 3, 2)
    if len(names) != 1 or len(values) != 1:
        fail("Every network-policy attribute must have one literal name and value.")
    compiled_items = field_values(fields, 6, 2)
    if len(compiled_items) > 1:
        fail("A network-policy attribute has multiple compiled values.")
    compiled_boolean = (
        decode_compiled_boolean(compiled_items[0])
        if compiled_items
        else None
    )
    return (
        decode_utf8(names[0], "network-policy attribute name"),
        decode_utf8(values[0], "network-policy attribute value"),
        compiled_boolean,
    )


def decode_element(payload):
    fields = parse_message(payload)
    require_allowed_fields(fields, {3, 4, 5}, "network-policy element")
    names = field_values(fields, 3, 2)
    if len(names) != 1:
        fail("Every network-policy element must have exactly one name.")
    attributes = {}
    for encoded_attribute in field_values(fields, 4, 2):
        name, value, compiled_boolean = decode_attribute(encoded_attribute)
        if name in attributes:
            fail(f"The network policy duplicates attribute {name!r}.")
        attributes[name] = (value, compiled_boolean)
    children = [
        decode_node(encoded_child)
        for encoded_child in field_values(fields, 5, 2)
    ]
    return decode_utf8(names[0], "network-policy element name"), attributes, children


def decode_node(payload):
    fields = parse_message(payload)
    require_allowed_fields(fields, {1, 3}, "network-policy XML node")
    elements = field_values(fields, 1, 2)
    if len(elements) != 1:
        fail("Every approved network-policy node must contain one element.")
    return decode_element(elements[0])


def require_empty_attributes(attributes, element_name):
    if attributes:
        fail(f"The approved {element_name} element must not have attributes.")


def require_no_children(children, element_name):
    if children:
        fail(f"The approved {element_name} element must not have children.")


def require_exact_policy(compiled_xml):
    root_name, root_attributes, root_children = decode_node(compiled_xml)
    if root_name != "network-security-config":
        fail("The compiled network policy has the wrong root element.")
    require_empty_attributes(root_attributes, root_name)

    children_by_name = {}
    for child in root_children:
        if child[0] in children_by_name:
            fail(f"The network policy duplicates element {child[0]!r}.")
        children_by_name[child[0]] = child
    if set(children_by_name) != {"base-config", "debug-overrides"}:
        fail(
            "The network policy must contain only the approved base-config "
            "and debug-overrides elements."
        )

    _, base_attributes, base_children = children_by_name["base-config"]
    if base_attributes != {"cleartextTrafficPermitted": ("false", False)}:
        fail(
            "The release network base-config must compile to literal "
            "cleartextTrafficPermitted=false."
        )
    require_no_children(base_children, "base-config")

    _, debug_attributes, debug_children = children_by_name["debug-overrides"]
    require_empty_attributes(debug_attributes, "debug-overrides")
    if len(debug_children) != 1 or debug_children[0][0] != "trust-anchors":
        fail("debug-overrides must contain exactly one trust-anchors element.")

    _, trust_attributes, trust_children = debug_children[0]
    require_empty_attributes(trust_attributes, "trust-anchors")
    certificate_sources = []
    for certificate in trust_children:
        name, attributes, children = certificate
        if name != "certificates":
            fail("debug trust-anchors contains an unexpected element.")
        if attributes.keys() != {"src"}:
            fail("Every debug certificates element must have only src.")
        source, compiled_boolean = attributes["src"]
        if compiled_boolean is not None:
            fail("A debug certificate source must remain a literal string.")
        require_no_children(children, "certificates")
        certificate_sources.append(source)
    if sorted(certificate_sources) != ["system", "user"]:
        fail("Debug trust anchors must be exactly system and user certificates.")


try:
    with zipfile.ZipFile(artifact_path, "r") as archive:
        pattern = re.compile(
            r"(?:^|.*/)res/xml(?:-[^/]*)?/network_security_config\.xml$"
        )
        candidates = [
            info
            for info in archive.infolist()
            if pattern.fullmatch(info.filename)
        ]
        if len(candidates) != 1:
            fail(
                "The AAB must contain exactly one network-security resource; "
                f"found {len(candidates)}."
            )
        resource = candidates[0]
        if resource.filename != "base/res/xml/network_security_config.xml":
            fail(
                "The AAB network-security resource must use the exact default "
                "base-module path."
            )
        if not 0 < resource.file_size <= 65536:
            fail("The compiled network-security resource has an unsafe size.")
        compiled_xml = archive.read(resource)
except PolicyError as error:
    print(f"[android-aab-identity][error] {error}", file=sys.stderr)
    sys.exit(1)
except (OSError, RuntimeError, zipfile.BadZipFile) as error:
    print(
        "[android-aab-identity][error] "
        f"Unable to inspect the compiled network-security resource: {error}.",
        file=sys.stderr,
    )
    sys.exit(1)

try:
    require_exact_policy(compiled_xml)
except PolicyError as error:
    print(f"[android-aab-identity][error] {error}", file=sys.stderr)
    sys.exit(1)

canonical_policy = (
    b"network-security-policy-v1\n"
    b"base.cleartextTrafficPermitted=false\n"
    b"debug.trustAnchors=system,user\n"
)
print(
    "semantic=" + hashlib.sha256(canonical_policy).hexdigest()
    + " compiled=" + hashlib.sha256(compiled_xml).hexdigest(),
    end="",
)
PY
)"; then
  exit 1
fi
[[ "$network_security_evidence" =~ ^semantic=[0-9a-f]{64}\ compiled=[0-9a-f]{64}$ ]] ||
  fail "Compiled network-security evidence is malformed."

expected_entries_file="$(mktemp)"
actual_entries_file="$(mktemp)"
temporary_files+=("$expected_entries_file" "$actual_entries_file")

cat > "$expected_entries_file" <<'ENTRIES'
base/lib/arm64-v8a/libandroidx.graphics.path.so
base/lib/arm64-v8a/libsodium.so
base/lib/arm64-v8a/libsqlcipher.so
base/lib/arm64-v8a/libsr25519java.so
base/lib/armeabi-v7a/libandroidx.graphics.path.so
base/lib/armeabi-v7a/libsodium.so
base/lib/armeabi-v7a/libsqlcipher.so
base/lib/armeabi-v7a/libsr25519java.so
base/lib/x86/libandroidx.graphics.path.so
base/lib/x86/libsodium.so
base/lib/x86/libsqlcipher.so
base/lib/x86/libsr25519java.so
base/lib/x86_64/libandroidx.graphics.path.so
base/lib/x86_64/libsodium.so
base/lib/x86_64/libsqlcipher.so
base/lib/x86_64/libsr25519java.so
ENTRIES
LC_ALL=C sort -o "$expected_entries_file" "$expected_entries_file"

unzip -Z1 "$artifact" |
  grep -E '(^|/)lib/[^/]+/[^/]+\.so$' |
  LC_ALL=C sort > "$actual_entries_file" || true

if ! cmp -s "$expected_entries_file" "$actual_entries_file"; then
  echo "[android-aab-identity][error] Native payload entries differ from the exact allowlist." >&2
  diff -u "$expected_entries_file" "$actual_entries_file" >&2 || true
  exit 1
fi

if ! python3 - "$artifact" "$expected_entries_file" <<'PY'
import sys
import zipfile

artifact_path, allowlist_path = sys.argv[1:]
with open(allowlist_path, "r", encoding="utf-8") as allowlist:
    approved_elf_entries = {
        line.rstrip("\n")
        for line in allowlist
        if line.rstrip("\n")
    }

try:
    with zipfile.ZipFile(artifact_path, "r") as archive:
        for entry in archive.infolist():
            try:
                with archive.open(entry, "r") as payload:
                    magic = payload.read(4)
            except (OSError, RuntimeError, zipfile.BadZipFile) as error:
                print(
                    "[android-aab-identity][error] "
                    f"Unable to inspect ZIP entry {entry.filename!r}: {error}",
                    file=sys.stderr,
                )
                sys.exit(1)

            if magic == b"\x7fELF" and entry.filename not in approved_elf_entries:
                print(
                    "[android-aab-identity][error] "
                    "ELF payload is outside exact native allowlist: "
                    f"{entry.filename!r}.",
                    file=sys.stderr,
                )
                sys.exit(1)
except (OSError, zipfile.BadZipFile) as error:
    print(
        "[android-aab-identity][error] "
        f"Unable to inspect every AAB ZIP entry for ELF payloads: {error}",
        file=sys.stderr,
    )
    sys.exit(1)
PY
then
  exit 1
fi

while IFS= read -r entry; do
  expected_sha="$(expected_native_sha256 "$entry")" ||
    fail "No approved checksum exists for $entry."
  case "$entry" in
    */libsodium.so|*/libsr25519java.so)
      source_path="$(source_native_path "$entry")"
      require_regular_file "$source_path" "approved source native library" 67108864
      source_sha="$(sha256_file "$source_path")"
      [[ "$source_sha" == "$expected_sha" ]] ||
        fail "Approved source native library checksum changed for $source_path."
      ;;
  esac

  extracted_file="$(mktemp)"
  temporary_files+=("$extracted_file")
  unzip -p "$artifact" "$entry" > "$extracted_file" ||
    fail "Unable to extract $entry from the AAB."
  require_regular_file "$extracted_file" "embedded native library" 67108864
  embedded_sha="$(sha256_file "$extracted_file")"
  [[ "$embedded_sha" == "$expected_sha" ]] ||
    fail "Embedded native library checksum mismatch for $entry."
done < "$expected_entries_file"

echo \
  "[android-aab-identity] package, version, source commit, exact Android toolchain ($android_toolchain_evidence), production-safe manifest (SDK $expected_min_sdk-$expected_target_sdk), exact component surface ($components_sha256), exact network policy ($network_security_evidence), exact merged permissions ($permissions_sha256), ABI set, and complete native/ELF payload verified"
