#!/usr/bin/env bash
set -euo pipefail

EXPECTED_GRADLE_URL='distributionUrl=https\://services.gradle.org/distributions/gradle-9.0-bin.zip'
EXPECTED_GRADLE_SHA256='8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b'

fail() {
  echo "[gradle-provenance][error] $*" >&2
  exit 1
}

usage() {
  echo "Usage: scripts/verify-gradle-dependency-provenance.sh [--root <project-root>]" >&2
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

require_regular_file() {
  local path="$1"
  local label="$2"
  [[ -f "$path" && ! -L "$path" ]] ||
    fail "$label is missing or is not a regular non-symlink file: $path"
}

require_exact_line() {
  local path="$1"
  local expected="$2"
  local label="$3"
  local count
  count="$(grep -Fxc -- "$expected" "$path" || true)"
  [[ "$count" == "1" ]] ||
    fail "$label must appear exactly once in $path"
}

require_text() {
  local path="$1"
  local expected="$2"
  local label="$3"
  grep -Fq -- "$expected" "$path" ||
    fail "$label is missing from $path"
}

forbid_pattern() {
  local path="$1"
  local pattern="$2"
  local label="$3"
  if grep -Eiq -- "$pattern" "$path"; then
    fail "$label is forbidden in $path"
  fi
}

root_dir="$(cd "$(dirname "$0")/.." && pwd -P)"
if (( $# > 0 )); then
  [[ "$1" == "--root" && $# == 2 ]] || {
    usage
    exit 2
  }
  root_dir="$(cd "$2" && pwd -P)"
fi

wrapper="$root_dir/gradle/wrapper/gradle-wrapper.properties"
metadata="$root_dir/gradle/verification-metadata.xml"
metadata_digest="$root_dir/gradle/verification-metadata.sha256"
gradle_properties="$root_dir/gradle.properties"
settings="$root_dir/settings.gradle"
root_build="$root_dir/build.gradle"
buildscript_lock="$root_dir/buildscript-gradle.lockfile"
release_lock="$root_dir/app/gradle.lockfile"
version_catalog="$root_dir/gradle/libs.versions.toml"

require_regular_file "$wrapper" "Gradle wrapper properties"
require_exact_line \
  "$wrapper" \
  "$EXPECTED_GRADLE_URL" \
  "approved Gradle wrapper distribution URL"
require_exact_line \
  "$wrapper" \
  "distributionSha256Sum=$EXPECTED_GRADLE_SHA256" \
  "approved Gradle wrapper distribution SHA-256"

require_regular_file "$metadata" "Gradle dependency verification metadata"
require_regular_file \
  "$metadata_digest" \
  "Gradle dependency verification metadata digest"
mapfile_supported=false
if [[ -n "${BASH_VERSION:-}" ]] && (( BASH_VERSINFO[0] >= 4 )); then
  mapfile_supported=true
fi
if [[ "$mapfile_supported" == "true" ]]; then
  mapfile -t digest_lines < "$metadata_digest"
else
  digest_lines=()
  while IFS= read -r line || [[ -n "$line" ]]; do
    digest_lines+=("$line")
  done < "$metadata_digest"
fi
[[ "${#digest_lines[@]}" == "1" ]] ||
  fail "dependency verification metadata digest must contain exactly one line"
[[ "${digest_lines[0]}" =~ ^([0-9a-f]{64})\ \ verification-metadata\.xml$ ]] ||
  fail "dependency verification metadata digest is malformed"
expected_metadata_digest="${BASH_REMATCH[1]}"
actual_metadata_digest="$(hash_file "$metadata")"
[[ "$actual_metadata_digest" == "$expected_metadata_digest" ]] ||
  fail "dependency verification metadata digest does not match"

command -v python3 >/dev/null 2>&1 ||
  fail "python3 is required to validate dependency verification XML"
if ! python3 - "$metadata" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

path = sys.argv[1]
try:
    root = ET.parse(path).getroot()
except (ET.ParseError, OSError) as error:
    raise SystemExit(f"dependency verification metadata XML is invalid: {error}")

def local_name(element):
    return element.tag.rsplit("}", 1)[-1]

if local_name(root) != "verification-metadata":
    raise SystemExit("dependency verification metadata has the wrong root element")

configuration = next(
    (child for child in root if local_name(child) == "configuration"),
    None,
)
if configuration is None:
    raise SystemExit("dependency verification configuration is missing")
settings = {
    local_name(child): (child.text or "").strip()
    for child in configuration
}
if settings.get("verify-metadata") != "true":
    raise SystemExit("dependency metadata verification must be enabled")
if settings.get("verify-signatures") != "false":
    raise SystemExit("dependency signature mode must be explicitly disabled")

forbidden_elements = {
    "ignored-key",
    "ignored-keys",
    "md5",
    "sha1",
    "trusted-artifact",
    "trusted-artifacts",
    "trusted-key",
    "trusted-keys",
}
present_forbidden = sorted(
    {
        local_name(element)
        for element in root.iter()
        if local_name(element) in forbidden_elements
    }
)
if present_forbidden:
    raise SystemExit(
        "dependency verification metadata contains forbidden trust bypasses: "
        + ", ".join(present_forbidden)
    )

components_parent = next(
    (child for child in root if local_name(child) == "components"),
    None,
)
if components_parent is None:
    raise SystemExit("dependency verification components are missing")
components = [
    child for child in components_parent if local_name(child) == "component"
]
if not components:
    raise SystemExit("dependency verification metadata contains no components")

sha256_pattern = re.compile(r"^[0-9a-f]{64}$")
artifact_count = 0
for component in components:
    for attribute in ("group", "name", "version"):
        if not component.attrib.get(attribute):
            raise SystemExit(
                f"dependency component is missing its {attribute} attribute"
            )
    artifacts = [
        child for child in component if local_name(child) == "artifact"
    ]
    if not artifacts:
        raise SystemExit("dependency component contains no verified artifacts")
    for artifact in artifacts:
        artifact_count += 1
        if not artifact.attrib.get("name"):
            raise SystemExit("dependency artifact is missing its name")
        hashes = [
            child for child in artifact if local_name(child) == "sha256"
        ]
        if not hashes:
            raise SystemExit(
                f"dependency artifact {artifact.attrib['name']} lacks SHA-256"
            )
        if any(
            not sha256_pattern.fullmatch(item.attrib.get("value", ""))
            for item in hashes
        ):
            raise SystemExit(
                f"dependency artifact {artifact.attrib['name']} has malformed SHA-256"
            )

if artifact_count < len(components):
    raise SystemExit("dependency verification artifact coverage is incomplete")
PY
then
  fail "dependency verification metadata structure is unsafe"
fi

require_regular_file "$gradle_properties" "Gradle properties"
require_exact_line \
  "$gradle_properties" \
  "org.gradle.dependency.verification=strict" \
  "strict Gradle dependency verification mode"
require_exact_line \
  "$gradle_properties" \
  "org.gradle.dependency.verification.console=verbose" \
  "verbose Gradle dependency verification diagnostics"

require_regular_file "$settings" "Gradle settings"
require_text \
  "$settings" \
  "DependencyVerificationMode.STRICT" \
  "strict verification runtime guard"
require_text \
  "$settings" \
  'System.getenv("GITHUB_ACTIONS") != null' \
  "GitHub Actions provenance guard"
require_text \
  "$settings" \
  "validateDependencyProvenance()" \
  "dependency provenance validation hook"
require_text \
  "$settings" \
  "resolvedReleaseGraph" \
  "resolved release task-graph guard"
require_text \
  "$settings" \
  "rejectLocalMavenRepository" \
  "local Maven runtime rejection guard"
require_text \
  "$settings" \
  "CI and release builds cannot rewrite dependency verification metadata." \
  "CI metadata rewrite rejection guard"
require_text \
  "$settings" \
  "CI and release builds cannot rewrite production dependency locks." \
  "CI dependency lock rewrite rejection guard"
if ! python3 - "$settings" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
try:
    text = path.read_text(encoding="utf-8")
except (OSError, UnicodeError) as error:
    raise SystemExit(f"unable to read Gradle bootstrap policy: {error}")

required_blocks = {
    "local metadata bootstrap predicate": """\
def localVerificationBootstrapRequested =
        !ciEnvironment &&
        !gradle.startParameter.writeDependencyVerifications.isEmpty() &&
        !gradle.startParameter.writeDependencyLocks &&
        gradle.startParameter.lockedDependenciesToUpdate.isEmpty()""",
    "explicit release provenance predicate": """\
def dependencyProvenanceRequired =
        ciEnvironment ||
        (
                explicitReleaseTaskRequested &&
                !localVerificationBootstrapRequested
        )""",
    "resolved release graph predicate": """\
if (resolvedReleaseGraph && !localVerificationBootstrapRequested) {
        validateDependencyProvenance()
        auditConfiguredRepositories()""",
    "metadata rewrite rejection": """\
if (!gradle.startParameter.writeDependencyVerifications.isEmpty()) {
        throw new GradleException(
                "CI and release builds cannot rewrite dependency verification metadata."
        )
    }""",
    "dependency lock rewrite rejection": """\
if (gradle.startParameter.writeDependencyLocks ||
            !gradle.startParameter.lockedDependenciesToUpdate.isEmpty()) {
        throw new GradleException(
                "CI and release builds cannot rewrite production dependency locks."
        )
    }""",
}
for label, block in required_blocks.items():
    count = text.count(block)
    if count != 1:
        raise SystemExit(
            f"{label} must appear exactly once; found {count}"
        )
PY
then
  fail "local dependency verification bootstrap contract is unsafe"
fi

require_regular_file "$root_build" "root Gradle build"
require_exact_line \
  "$root_build" \
  "        lockMode = LockMode.STRICT" \
  "strict buildscript dependency lock mode"
require_exact_line \
  "$root_build" \
  "        resolutionStrategy.activateDependencyLocking()" \
  "buildscript classpath dependency locking activation"
require_exact_line \
  "$root_build" \
  "        if (gradle.ext.fearlessMavenLocalAllowed) {" \
  "non-release mavenLocal guard"
require_exact_line \
  "$root_build" \
  "            mavenLocal {" \
  "single guarded mavenLocal declaration"
require_text \
  "$root_build" \
  'includeGroupByRegex "jp[.]co[.]soramitsu([.].*)?"' \
  "mavenLocal Soramitsu-only content filter"
require_text \
  "$root_build" \
  "exclusiveContent {" \
  "exclusive JitPack content filter"
require_text \
  "$root_build" \
  "LockMode.STRICT" \
  "strict production release dependency lock mode"
require_text \
  "$root_build" \
  "activateDependencyLocking()" \
  "production release dependency locking activation"

require_regular_file \
  "$buildscript_lock" \
  "root buildscript dependency lock"
[[ -s "$buildscript_lock" ]] ||
  fail "root buildscript dependency lock must be non-empty"
if ! python3 - "$buildscript_lock" <<'PY'
import re
import sys

path = sys.argv[1]
try:
    with open(path, encoding="utf-8") as source:
        text = source.read()
except (OSError, UnicodeError) as error:
    raise SystemExit(f"unable to read root buildscript lock: {error}")

if "\x00" in text or "\r" in text:
    raise SystemExit("root buildscript lock contains forbidden bytes")
if not text.endswith("\n"):
    raise SystemExit("root buildscript lock must end with one newline")
lines = text.splitlines()
expected_header = [
    "# This is a Gradle generated file for dependency locking.",
    "# Manual edits can break the build and are not advised.",
    "# This file is expected to be part of source control.",
]
if lines[:3] != expected_header:
    raise SystemExit("root buildscript lock has a non-generated header")
if lines[-1:] != ["empty="] or lines.count("empty=") != 1:
    raise SystemExit("root buildscript lock has an invalid terminal state")

entries = lines[3:-1]
if len(entries) < 8:
    raise SystemExit("root buildscript lock contains no real classpath graph")
entry_pattern = re.compile(
    r"^[A-Za-z0-9_.-]+:[A-Za-z0-9_.-]+:[^=,\s]+="
    r"classpath$"
)
if any(not entry_pattern.fullmatch(entry) for entry in entries):
    raise SystemExit("root buildscript lock contains a malformed lock state")
if entries != sorted(entries):
    raise SystemExit("root buildscript lock entries are not canonical")
if len(entries) != len(set(entries)):
    raise SystemExit("root buildscript lock contains duplicate entries")

required_entries = {
    "com.android.tools.build:gradle:8.10.1=classpath",
    "com.google.firebase:firebase-appdistribution-gradle:4.2.0=classpath",
    "com.google.gms:google-services:4.4.1=classpath",
    "org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.10=classpath",
    "org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.10=classpath",
    "org.jetbrains.kotlin:kotlin-serialization:2.1.10=classpath",
    "org.jacoco:org.jacoco.core:0.8.12=classpath",
    "org.sonarsource.scanner.gradle:sonarqube-gradle-plugin:3.3=classpath",
}
missing = sorted(required_entries.difference(entries))
if missing:
    raise SystemExit(
        "root buildscript lock omits reviewed classpath roots: "
        + ", ".join(missing)
    )
PY
then
  fail "root buildscript dependency lock is unsafe"
fi

require_regular_file "$release_lock" "production release dependency lock"
require_regular_file "$version_catalog" "Gradle version catalog"
if ! python3 - "$version_catalog" "$root_build" "$release_lock" <<'PY'
import re
import sys

catalog_path, build_path, lock_path = sys.argv[1:]


def read(path):
    try:
        with open(path, encoding="utf-8") as source:
            return source.read()
    except OSError as error:
        raise SystemExit(f"unable to read Android toolchain input: {error}")


catalog = read(catalog_path)
build = read(build_path)
lock = read(lock_path)

agp_matches = re.findall(
    r'^android_plugin\s*=\s*"([0-9]+(?:\.[0-9]+){2})"\s*$',
    catalog,
    flags=re.MULTILINE,
)
if len(agp_matches) != 1:
    raise SystemExit("Android Gradle plugin version must be declared exactly once")
if agp_matches[0] != "8.10.1":
    raise SystemExit(
        "Android Gradle plugin must be exactly 8.10.1 with its reviewed "
        "R8 8.10.24 toolchain"
    )

compile_sdk_matches = re.findall(
    r'^\s*compileSdkVersion\s*=\s*([0-9]+)\s*$',
    build,
    flags=re.MULTILINE,
)
if len(compile_sdk_matches) != 1:
    raise SystemExit("compile SDK version must be declared exactly once")

runtime_stdlib_versions = []
for line in lock.splitlines():
    match = re.fullmatch(
        r'org\.jetbrains\.kotlin:kotlin-stdlib:([^=]+)=(.+)',
        line,
    )
    if match is None:
        continue
    configurations = set(match.group(2).split(","))
    if "releaseRuntimeClasspath" in configurations:
        runtime_stdlib_versions.append(match.group(1))
if len(runtime_stdlib_versions) != 1:
    raise SystemExit(
        "release runtime must resolve exactly one Kotlin standard library"
    )


def numeric_version(value, label):
    if not re.fullmatch(r"[0-9]+(?:\.[0-9]+){1,2}", value):
        raise SystemExit(f"{label} is not a reviewed numeric version")
    parts = tuple(int(part) for part in value.split("."))
    return parts + (0,) * (3 - len(parts))


compile_sdk = int(compile_sdk_matches[0])
kotlin_runtime = numeric_version(
    runtime_stdlib_versions[0],
    "release Kotlin standard library version",
)

# AGP 8.10.1 embeds the reviewed R8 8.10.24 toolchain, which exceeds the
# minimum R8 8.10.21 required for Kotlin 2.2 metadata. Keep other AGP/R8 and
# Kotlin metadata lines fail-closed until their exact combination is reviewed.
if kotlin_runtime[:2] != (2, 2):
    raise SystemExit(
        "release Kotlin metadata version must be explicitly reviewed before use"
    )
if compile_sdk != 36:
    raise SystemExit(
        "compile SDK compatibility must be explicitly reviewed before use"
    )
PY
then
  fail "Android release AGP/R8/Kotlin compatibility contract is unsafe"
fi
locked_entry_count="$(
  grep -Evc '^(#|$|empty=)' "$release_lock" || true
)"
(( locked_entry_count >= 1 )) ||
  fail "production release dependency lock contains no module entries"
required_lock_configurations=(
  hiltAnnotationProcessorRelease
  hiltAnnotationProcessorInternalAppSharing
  kotlinCompilerPluginClasspathRelease
  kotlinCompilerPluginClasspathInternalAppSharing
  kspReleaseKotlinProcessorClasspath
  kspInternalAppSharingKotlinProcessorClasspath
  releaseAnnotationProcessorClasspath
  releaseCompileClasspath
  releaseRuntimeClasspath
  internalAppSharingAnnotationProcessorClasspath
  internalAppSharingCompileClasspath
  internalAppSharingRuntimeClasspath
)
for configuration in "${required_lock_configurations[@]}"; do
  grep -Fq "\"$configuration\"" "$root_build" ||
    fail "strict production/IAS dependency locking omits $configuration"
  grep -Eq "(=|,)${configuration}(,|$)" "$release_lock" ||
    fail "production release dependency lock lacks $configuration"
done

for workflow in \
  "$root_dir/.github/workflows/android-ci.yml" \
  "$root_dir/.github/workflows/android-release.yml" \
  "$root_dir/.github/workflows/android-internal-app-sharing.yml"; do
  require_regular_file "$workflow" "Android CI/release workflow"
  require_text "$workflow" "CI: true" "explicit CI environment marker"
  forbid_pattern \
    "$workflow" \
    'ALLOW[_-]?MAVEN[_-]?LOCAL|MAVEN[_-]?LOCAL[_-]?REPOSITORY|mavenLocal' \
    "CI mavenLocal bypass"
  forbid_pattern \
    "$workflow" \
    '(--dependency-verification([=[:space:]]+)(off|lenient)|org[.]gradle[.]dependency[.]verification([=:[:space:]]+)(off|lenient))' \
    "CI dependency verification bypass"
  forbid_pattern \
    "$workflow" \
    '(--write-verification-metadata|--write-locks|--update-locks)' \
    "CI dependency provenance rewrite"
done

echo \
  "[gradle-provenance] passed: wrapper SHA-256, strict metadata, release locks, AGP/R8/Kotlin compatibility, and CI repository policy"
