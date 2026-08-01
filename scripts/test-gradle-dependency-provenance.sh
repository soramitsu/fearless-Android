#!/bin/bash -p
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd -P)"
VERIFY="$ROOT_DIR/scripts/verify-gradle-dependency-provenance.sh"
EXPECTED_WRAPPER_SHA256='8fad3d78296ca518113f3d29016617c7f9367dc005f932bd9d93bf45ba46072b'
EXPECTED_POSITIVE_COUNT=2
EXPECTED_NEGATIVE_COUNT=65

test_tmp_root="${RUNNER_TEMP:-${TMPDIR:-/tmp}}"
mkdir -p "$test_tmp_root"
test_tmp_root="$(cd "$test_tmp_root" && pwd -P)"
tmp_dir="$(mktemp -d "$test_tmp_root/gradle-provenance.XXXXXX")"
trap 'rm -rf "$tmp_dir"' EXIT

fail() {
  echo "[gradle-provenance-test][error] $*" >&2
  exit 1
}

hash_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

make_fixture() {
  local name="$1"
  local fixture="$tmp_dir/$name"
  mkdir -p \
    "$fixture/gradle/wrapper" \
    "$fixture/app" \
    "$fixture/iroha-sdk-bridge" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke" \
    "$fixture/.github/workflows"
  cp "$ROOT_DIR/build.gradle" "$fixture/build.gradle"
  cp \
    "$ROOT_DIR/buildscript-gradle.lockfile" \
    "$fixture/buildscript-gradle.lockfile"
  cp "$ROOT_DIR/settings.gradle" "$fixture/settings.gradle"
  cp "$ROOT_DIR/gradle.properties" "$fixture/gradle.properties"
  cp \
    "$ROOT_DIR/gradle/libs.versions.toml" \
    "$fixture/gradle/libs.versions.toml"
  cp \
    "$ROOT_DIR/gradle/wrapper/gradle-wrapper.properties" \
    "$fixture/gradle/wrapper/gradle-wrapper.properties"
  cp \
    "$ROOT_DIR/gradle/verification-metadata.xml" \
    "$fixture/gradle/verification-metadata.xml"
  cp \
    "$ROOT_DIR/gradle/verification-metadata.sha256" \
    "$fixture/gradle/verification-metadata.sha256"
  cp "$ROOT_DIR/app/gradle.lockfile" "$fixture/app/gradle.lockfile"
  cp \
    "$ROOT_DIR/iroha-sdk-bridge/build.gradle" \
    "$fixture/iroha-sdk-bridge/build.gradle"
  cp \
    "$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/build.gradle" \
    "$fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle"
  cp \
    "$ROOT_DIR/.github/workflows/android-ci.yml" \
    "$fixture/.github/workflows/android-ci.yml"
  cp \
    "$ROOT_DIR/.github/workflows/android-release.yml" \
    "$fixture/.github/workflows/android-release.yml"
  cp \
    "$ROOT_DIR/.github/workflows/android-internal-app-sharing.yml" \
    "$fixture/.github/workflows/android-internal-app-sharing.yml"
  printf '%s\n' "$fixture"
}

replace_once() {
  local path="$1"
  local old="$2"
  local new="$3"
  python3 - "$path" "$old" "$new" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
old = sys.argv[2]
new = sys.argv[3]
text = path.read_text()
if text.count(old) != 1:
    raise SystemExit(f"expected exactly one occurrence in {path}: {old!r}")
path.write_text(text.replace(old, new, 1))
PY
}

remove_lock_configuration() {
  local path="$1"
  local configuration="$2"
  python3 - "$path" "$configuration" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
configuration = sys.argv[2]
changed = False
output = []
for line in path.read_text(encoding="utf-8").splitlines():
    if line.startswith("#") or "=" not in line:
        output.append(line)
        continue
    coordinate, values = line.rsplit("=", 1)
    configurations = values.split(",")
    filtered = [item for item in configurations if item != configuration]
    if filtered != configurations:
        changed = True
    output.append(f"{coordinate}={','.join(filtered)}")
if not changed:
    raise SystemExit(f"lock fixture omitted {configuration}")
path.write_text("\n".join(output) + "\n", encoding="utf-8")
PY
}

expect_failure() {
  local label="$1"
  local expected="$2"
  local fixture="$3"
  local output="$tmp_dir/failure-$negative_count.log"
  if "$VERIFY" --root "$fixture" >"$output" 2>&1; then
    fail "$label unexpectedly succeeded"
  fi
  grep -Fq -- "$expected" "$output" || {
    sed -n '1,160p' "$output" >&2
    fail "$label did not fail with expected diagnostic: $expected"
  }
  negative_count=$((negative_count + 1))
}

"$VERIFY"
positive_count=1
negative_count=0

python3 - <<'PY'
def policy(
    *,
    ci=None,
    github_actions=None,
    explicit_release=True,
    write_metadata=False,
    write_locks=False,
    update_locks=False,
    resolved_release=True,
):
    ci_environment = github_actions is not None or str(ci or "").strip().lower() in {
        "1",
        "true",
        "yes",
    }
    local_bootstrap = (
        not ci_environment
        and write_metadata
        and not write_locks
        and not update_locks
    )
    provenance_required = ci_environment or (
        explicit_release and not local_bootstrap
    )
    return {
        "bootstrap": local_bootstrap,
        "provenance": provenance_required,
        "metadata_rejected": provenance_required and write_metadata,
        "locks_rejected": provenance_required and (write_locks or update_locks),
        "graph_guard": resolved_release and not local_bootstrap,
    }


assert policy(write_metadata=True) == {
    "bootstrap": True,
    "provenance": False,
    "metadata_rejected": False,
    "locks_rejected": False,
    "graph_guard": False,
}
for environment in ({"ci": "true"}, {"github_actions": "false"}):
    result = policy(write_metadata=True, **environment)
    assert not result["bootstrap"]
    assert result["provenance"]
    assert result["metadata_rejected"]
    assert result["graph_guard"]
for rewrite in ({"write_locks": True}, {"update_locks": True}):
    result = policy(**rewrite)
    assert not result["bootstrap"]
    assert result["provenance"]
    assert result["locks_rejected"]
    assert result["graph_guard"]
combined = policy(write_metadata=True, write_locks=True)
assert not combined["bootstrap"]
assert combined["metadata_rejected"] and combined["locks_rejected"]
PY
positive_count=$((positive_count + 1))

fixture="$(make_fixture missing-metadata)"
rm "$fixture/gradle/verification-metadata.xml"
expect_failure \
  "missing verification metadata" \
  "Gradle dependency verification metadata is missing" \
  "$fixture"

fixture="$(make_fixture mutated-metadata)"
python3 - "$fixture/gradle/verification-metadata.xml" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text()
match = re.search(r'(<sha256 value=")([0-9a-f])', text)
if match is None:
    raise SystemExit("fixture has no SHA-256 entry")
replacement = "0" if match.group(2) != "0" else "1"
path.write_text(
    text[:match.start(2)] + replacement + text[match.end(2):]
)
PY
expect_failure \
  "mutated verification metadata" \
  "dependency verification metadata digest does not match" \
  "$fixture"

fixture="$(make_fixture coordinated-invalid-metadata)"
python3 - "$fixture/gradle/verification-metadata.xml" <<'PY'
import pathlib
import re
import sys

path = pathlib.Path(sys.argv[1])
text = path.read_text()
text, count = re.subn(
    r'<sha256 value="[0-9a-f]{64}"',
    '<sha256 value="0"',
    text,
    count=1,
)
if count != 1:
    raise SystemExit("fixture has no SHA-256 entry")
path.write_text(text)
PY
printf '%s  verification-metadata.xml\n' \
  "$(hash_file "$fixture/gradle/verification-metadata.xml")" \
  > "$fixture/gradle/verification-metadata.sha256"
expect_failure \
  "coordinated malformed verification metadata" \
  "dependency verification metadata structure is unsafe" \
  "$fixture"

fixture="$(make_fixture missing-wrapper-sha)"
python3 - "$fixture/gradle/wrapper/gradle-wrapper.properties" <<'PY'
import pathlib
import sys

path = pathlib.Path(sys.argv[1])
lines = [
    line for line in path.read_text().splitlines()
    if not line.startswith("distributionSha256Sum=")
]
path.write_text("\n".join(lines) + "\n")
PY
expect_failure \
  "missing Gradle wrapper SHA-256" \
  "approved Gradle wrapper distribution SHA-256 must appear exactly once" \
  "$fixture"

fixture="$(make_fixture mutated-wrapper-sha)"
replace_once \
  "$fixture/gradle/wrapper/gradle-wrapper.properties" \
  "$EXPECTED_WRAPPER_SHA256" \
  "0${EXPECTED_WRAPPER_SHA256:1}"
expect_failure \
  "mutated Gradle wrapper SHA-256" \
  "approved Gradle wrapper distribution SHA-256 must appear exactly once" \
  "$fixture"

fixture="$(make_fixture strict-mode-disabled)"
replace_once \
  "$fixture/gradle.properties" \
  "org.gradle.dependency.verification=strict" \
  "org.gradle.dependency.verification=off"
expect_failure \
  "disabled strict verification mode" \
  "strict Gradle dependency verification mode must appear exactly once" \
  "$fixture"

fixture="$(make_fixture local-bootstrap-ci-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '        !ciEnvironment &&' \
  '        true &&'
expect_failure \
  "local metadata bootstrap CI bypass" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture local-bootstrap-lock-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  $'        !gradle.startParameter.writeDependencyVerifications.isEmpty() &&\n        !gradle.startParameter.writeDependencyLocks &&\n        gradle.startParameter.lockedDependenciesToUpdate.isEmpty()' \
  '        !gradle.startParameter.writeDependencyVerifications.isEmpty()'
expect_failure \
  "local metadata bootstrap dependency-lock bypass" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture local-bootstrap-release-disabled)"
replace_once \
  "$fixture/settings.gradle" \
  '                !localVerificationBootstrapRequested' \
  '                true'
expect_failure \
  "explicit release ignores local metadata bootstrap" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture task-graph-bootstrap-disabled)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (resolvedReleaseGraph && !localVerificationBootstrapRequested) {' \
  '    if (resolvedReleaseGraph) {'
expect_failure \
  "resolved release graph ignores local metadata bootstrap" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture github-actions-marker-removed)"
replace_once \
  "$fixture/settings.gradle" \
  'System.getenv("GITHUB_ACTIONS") != null' \
  'System.getenv("UNTRUSTED_GITHUB_ACTIONS") != null'
expect_failure \
  "GitHub Actions does not activate provenance" \
  "GitHub Actions provenance guard is missing" \
  "$fixture"

fixture="$(make_fixture project-repository-lockdown-weakened)"
replace_once \
  "$fixture/settings.gradle" \
  'repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)' \
  'repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)'
expect_failure \
  "CI/release project repository lockdown weakened" \
  "CI/release project repository lockdown must appear exactly once" \
  "$fixture"

fixture="$(make_fixture staged-iroha-repository-name-widened)"
replace_once \
  "$fixture/settings.gradle" \
  "name = 'materializedIrohaMobileSdk'" \
  "name = 'mavenLocal'"
expect_failure \
  "staged Iroha local repository name widened" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-repository-path-widened)"
replace_once \
  "$fixture/settings.gradle" \
  ".resolve('build/iroha-mobile-sdk/maven')" \
  ".resolve(System.getProperty('user.home'))"
expect_failure \
  "staged Iroha local repository path widened" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-local-repository-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  '    if (false) {'
expect_failure \
  "staged Iroha local repository rejection bypassed" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-rejection-commented-out)"
replace_once \
  "$fixture/settings.gradle" \
  $'    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  $'    /*\n    if (repository.url?.scheme?.equalsIgnoreCase("file")) {'
replace_once \
  "$fixture/settings.gradle" \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n}' \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n    */\n}'
expect_failure \
  "staged Iroha rejection hidden in a block comment" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-rejection-string-decoy)"
replace_once \
  "$fixture/settings.gradle" \
  $'    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  $'    \"\"\"\n    if (repository.url?.scheme?.equalsIgnoreCase("file")) {'
replace_once \
  "$fixture/settings.gradle" \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n}' \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n    \"\"\"\n}'
expect_failure \
  "staged Iroha rejection hidden in a string decoy" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-rejection-slashy-string-decoy)"
replace_once \
  "$fixture/settings.gradle" \
  $'    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  $'    return /\n    if (repository.url?.scheme?.equalsIgnoreCase("file")) {'
replace_once \
  "$fixture/settings.gradle" \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n}' \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n    /\n}'
expect_failure \
  "staged Iroha rejection hidden in a multiline slashy string" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-rejection-dollar-slashy-decoy)"
replace_once \
  "$fixture/settings.gradle" \
  $'    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  $'    return $/\n    if (repository.url?.scheme?.equalsIgnoreCase("file")) {'
replace_once \
  "$fixture/settings.gradle" \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n}' \
  $'                        "\'${repository.name}\' in ${owner}."\n        )\n    }\n    /$\n}'
expect_failure \
  "staged Iroha rejection hidden in a multiline dollar-slashy string" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-predicate-reassigned)"
printf '%s\n' \
  'hasNoSymbolicLinkComponents = { candidate -> true }' \
  >>"$fixture/settings.gradle"
expect_failure \
  "staged Iroha approval predicate reassigned" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-rejection-reassigned)"
printf '%s\n' \
  'rejectLocalMavenRepository = { MavenArtifactRepository repository, String owner -> }' \
  >>"$fixture/settings.gradle"
expect_failure \
  "staged Iroha rejection predicate reassigned" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-policy-mutated)"
printf '%s\n' \
  "approvedStagedIrohaRepositoryPath = settingsDir.toPath()" \
  >>"$fixture/settings.gradle"
expect_failure \
  "staged Iroha nested policy mutated" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-exclusive-content-removed)"
replace_once \
  "$fixture/iroha-sdk-bridge/build.gradle" \
  '        exclusiveContent {' \
  '        mavenContent {'
expect_failure \
  "staged Iroha exclusive content removed" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-repository-commented-out)"
replace_once \
  "$fixture/iroha-sdk-bridge/build.gradle" \
  '    repositories {' \
  '    /* repositories { */'
expect_failure \
  "staged Iroha repository hidden in a block comment" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-group-filter-widened)"
replace_once \
  "$fixture/iroha-sdk-bridge/build.gradle" \
  "includeGroup 'org.hyperledger.iroha.sdk'" \
  "includeGroupByRegex '.*'"
expect_failure \
  "staged Iroha repository group filter widened" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-extra-local-repository)"
printf '%s\n' \
  "repositories { flatDir { dirs rootProject.layout.buildDirectory.dir('attacker') } }" \
  >>"$fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle"
expect_failure \
  "staged Iroha extra local repository added" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-division-bracketed-local-repository)"
printf '%s\n' \
  'def harmlessRatio = 1 / 1' \
  "repositories { flatDir { dirs rootProject.layout.buildDirectory.dir('attacker') } }" \
  'def anotherHarmlessRatio = 1 / 1' \
  >>"$fixture/iroha-sdk-bridge/build.gradle"
expect_failure \
  "staged Iroha local repository bracketed by division operators" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture staged-iroha-gstring-local-repository)"
printf '%s\n' \
  "\"\${repositories { flatDir { dirs rootProject.layout.buildDirectory.dir('attacker') } }}\"" \
  >>"$fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle"
expect_failure \
  "staged Iroha local repository executed inside GString interpolation" \
  "local dependency verification bootstrap contract is unsafe" \
  "$fixture"

fixture="$(make_fixture python-path-shadow-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  '    if (false) {'
fake_bin="$fixture/fake-bin"
mkdir -p "$fake_bin"
printf '%s\n' '#!/bin/sh' 'exit 0' >"$fake_bin/python3"
chmod +x "$fake_bin/python3"
output="$tmp_dir/failure-$negative_count.log"
if PATH="$fake_bin:$PATH" "$VERIFY" --root "$fixture" >"$output" 2>&1; then
  fail "PATH-shadowed Python bypass unexpectedly succeeded"
fi
grep -Fq -- "local dependency verification bootstrap contract is unsafe" "$output" || {
  sed -n '1,160p' "$output" >&2
  fail "PATH-shadowed Python bypass did not fail closed"
}
negative_count=$((negative_count + 1))

fixture="$(make_fixture exported-python-function-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  '    if (false) {'
output="$tmp_dir/failure-$negative_count.log"
if (
  function python3() { return 0; }
  export -f python3
  "$VERIFY" --root "$fixture" >"$output" 2>&1
); then
  fail "exported Python function bypass unexpectedly succeeded"
fi
grep -Fq -- "local dependency verification bootstrap contract is unsafe" "$output" || {
  sed -n '1,160p' "$output" >&2
  fail "exported Python function bypass did not fail closed"
}
negative_count=$((negative_count + 1))

fixture="$(make_fixture pythonpath-sitecustomize-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  '    if (false) {'
fake_pythonpath="$fixture/fake-pythonpath"
mkdir -p "$fake_pythonpath"
printf '%s\n' 'import os' 'os._exit(0)' >"$fake_pythonpath/sitecustomize.py"
output="$tmp_dir/failure-$negative_count.log"
if PYTHONPATH="$fake_pythonpath" "$VERIFY" --root "$fixture" >"$output" 2>&1; then
  fail "PYTHONPATH sitecustomize bypass unexpectedly succeeded"
fi
grep -Fq -- "local dependency verification bootstrap contract is unsafe" "$output" || {
  sed -n '1,160p' "$output" >&2
  fail "PYTHONPATH sitecustomize bypass did not fail closed"
}
negative_count=$((negative_count + 1))

fixture="$(make_fixture developer-dir-xcrun-bypass)"
replace_once \
  "$fixture/settings.gradle" \
  '    if (repository.url?.scheme?.equalsIgnoreCase("file")) {' \
  '    if (false) {'
fake_developer_dir="$fixture/fake-developer"
mkdir -p "$fake_developer_dir/usr/bin"
metadata_digest_line="$(<"$fixture/gradle/verification-metadata.sha256")"
metadata_digest="${metadata_digest_line%% *}"
printf '%s\n' \
  '#!/bin/sh' \
  "printf '%s\\n' '$metadata_digest'" \
  'exit 0' \
  >"$fake_developer_dir/usr/bin/xcrun"
chmod +x "$fake_developer_dir/usr/bin/xcrun"
output="$tmp_dir/failure-$negative_count.log"
if DEVELOPER_DIR="$fake_developer_dir" \
  "$VERIFY" --root "$fixture" >"$output" 2>&1; then
  fail "DEVELOPER_DIR xcrun Python bypass unexpectedly succeeded"
fi
grep -Fq -- "local dependency verification bootstrap contract is unsafe" "$output" || {
  sed -n '1,160p' "$output" >&2
  fail "DEVELOPER_DIR xcrun Python bypass did not fail closed"
}
negative_count=$((negative_count + 1))

fake_shell_bin="$tmp_dir/fake-shell-bin"
mkdir -p "$fake_shell_bin"
printf '%s\n' '#!/bin/sh' 'exit 0' >"$fake_shell_bin/bash"
chmod +x "$fake_shell_bin/bash"
output="$tmp_dir/failure-$negative_count.log"
if PATH="$fake_shell_bin:$PATH" \
  "$VERIFY" --root "$tmp_dir/definitely-missing-root" >"$output" 2>&1; then
  fail "PATH-shadowed Bash bootstrap bypass unexpectedly succeeded"
fi
negative_count=$((negative_count + 1))

fake_bash_env="$tmp_dir/malicious-bash-env"
printf '%s\n' 'exit 0' >"$fake_bash_env"
output="$tmp_dir/failure-$negative_count.log"
if BASH_ENV="$fake_bash_env" \
  "$VERIFY" --root "$tmp_dir/definitely-missing-root" >"$output" 2>&1; then
  fail "BASH_ENV bootstrap bypass unexpectedly succeeded"
fi
negative_count=$((negative_count + 1))

fixture="$(make_fixture unguarded-maven-local)"
replace_once \
  "$fixture/build.gradle" \
  "            if (gradle.ext.fearlessMavenLocalAllowed) {" \
  "            if (true) {"
expect_failure \
  "unguarded mavenLocal repository" \
  "non-release mavenLocal guard must appear exactly once" \
  "$fixture"

fixture="$(make_fixture ci-maven-local-bypass)"
printf '\n# adversarial fixture\nALLOW_MAVEN_LOCAL: true\n' \
  >> "$fixture/.github/workflows/android-ci.yml"
expect_failure \
  "CI mavenLocal bypass" \
  "CI mavenLocal bypass is forbidden" \
  "$fixture"

fixture="$(make_fixture ci-verification-off-bypass)"
printf '\n# ./gradlew help --dependency-verification=off\n' \
  >> "$fixture/.github/workflows/android-release.yml"
expect_failure \
  "CI strict verification bypass" \
  "CI dependency verification bypass is forbidden" \
  "$fixture"

fixture="$(make_fixture ci-metadata-rewrite-bypass)"
printf '\n# ./gradlew help --write-verification-metadata sha256\n' \
  >> "$fixture/.github/workflows/android-ci.yml"
expect_failure \
  "CI verification metadata rewrite" \
  "CI dependency provenance rewrite is forbidden" \
  "$fixture"

fixture="$(make_fixture ias-ci-maven-local-bypass)"
printf '\n# adversarial fixture\nmavenLocal\n' \
  >> "$fixture/.github/workflows/android-internal-app-sharing.yml"
expect_failure \
  "IAS CI mavenLocal bypass" \
  "CI mavenLocal bypass is forbidden" \
  "$fixture"

fixture="$(make_fixture ias-ci-verification-off-bypass)"
printf '\n# ./gradlew help --dependency-verification=off\n' \
  >> "$fixture/.github/workflows/android-internal-app-sharing.yml"
expect_failure \
  "IAS CI strict verification bypass" \
  "CI dependency verification bypass is forbidden" \
  "$fixture"

fixture="$(make_fixture ias-ci-lock-rewrite-bypass)"
printf '\n# ./gradlew dependencies --write-locks\n' \
  >> "$fixture/.github/workflows/android-internal-app-sharing.yml"
expect_failure \
  "IAS CI dependency lock rewrite" \
  "CI dependency provenance rewrite is forbidden" \
  "$fixture"

for configuration in \
  hiltAnnotationProcessorInternalAppSharing \
  kotlinCompilerPluginClasspathInternalAppSharing \
  kspInternalAppSharingKotlinProcessorClasspath \
  internalAppSharingAnnotationProcessorClasspath \
  internalAppSharingCompileClasspath \
  internalAppSharingRuntimeClasspath; do
  fixture="$(make_fixture "missing-$configuration-lock")"
  remove_lock_configuration "$fixture/app/gradle.lockfile" "$configuration"
  expect_failure \
    "missing IAS lock coverage for $configuration" \
    "production release dependency lock lacks $configuration" \
    "$fixture"
done

fixture="$(make_fixture missing-ias-strict-activation)"
replace_once \
  "$fixture/build.gradle" \
  '                "internalAppSharingRuntimeClasspath"' \
  '                "internalAppSharingRuntimeClasspathUnlocked"'
expect_failure \
  "missing IAS strict-lock activation" \
  "strict production/IAS dependency locking omits internalAppSharingRuntimeClasspath" \
  "$fixture"

fixture="$(make_fixture missing-release-lock)"
rm "$fixture/app/gradle.lockfile"
expect_failure \
  "missing production release lock" \
  "production release dependency lock is missing" \
  "$fixture"

fixture="$(make_fixture missing-buildscript-lock)"
rm "$fixture/buildscript-gradle.lockfile"
expect_failure \
  "missing root buildscript lock" \
  "root buildscript dependency lock is missing" \
  "$fixture"

fixture="$(make_fixture symlinked-buildscript-lock)"
mv \
  "$fixture/buildscript-gradle.lockfile" \
  "$fixture/buildscript-gradle.lockfile.real"
ln -s \
  "buildscript-gradle.lockfile.real" \
  "$fixture/buildscript-gradle.lockfile"
expect_failure \
  "symlinked root buildscript lock" \
  "root buildscript dependency lock is missing" \
  "$fixture"

fixture="$(make_fixture empty-buildscript-lock)"
: >"$fixture/buildscript-gradle.lockfile"
expect_failure \
  "empty root buildscript lock" \
  "root buildscript dependency lock must be non-empty" \
  "$fixture"

fixture="$(make_fixture placeholder-buildscript-lock)"
printf '%s\n' \
  '# This is a Gradle generated file for dependency locking.' \
  '# Manual edits can break the build and are not advised.' \
  '# This file is expected to be part of source control.' \
  'empty=' >"$fixture/buildscript-gradle.lockfile"
expect_failure \
  "placeholder root buildscript lock" \
  "root buildscript lock contains no real classpath graph" \
  "$fixture"

fixture="$(make_fixture tampered-buildscript-lock-state)"
replace_once \
  "$fixture/buildscript-gradle.lockfile" \
  'com.android.tools.build:gradle:8.10.1=classpath' \
  'com.android.tools.build:gradle:8.10.1=unlocked'
expect_failure \
  "tampered root buildscript lock state" \
  "root buildscript lock contains a malformed lock state" \
  "$fixture"

fixture="$(make_fixture missing-buildscript-compose-compiler-root)"
sed -i.bak \
  '/^org[.]jetbrains[.]kotlin:compose-compiler-gradle-plugin:2[.]1[.]10=classpath$/d' \
  "$fixture/buildscript-gradle.lockfile"
rm "$fixture/buildscript-gradle.lockfile.bak"
expect_failure \
  "missing reviewed Compose compiler classpath root" \
  "root buildscript lock omits reviewed classpath roots: org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.10=classpath" \
  "$fixture"

fixture="$(make_fixture missing-buildscript-kotlin-serialization-root)"
sed -i.bak \
  '/^org[.]jetbrains[.]kotlin:kotlin-serialization:2[.]1[.]10=classpath$/d' \
  "$fixture/buildscript-gradle.lockfile"
rm "$fixture/buildscript-gradle.lockfile.bak"
expect_failure \
  "missing reviewed Kotlin serialization classpath root" \
  "root buildscript lock omits reviewed classpath roots: org.jetbrains.kotlin:kotlin-serialization:2.1.10=classpath" \
  "$fixture"

fixture="$(make_fixture buildscript-strict-mode-removed)"
replace_once \
  "$fixture/build.gradle" \
  $'buildscript {\n    dependencyLocking {\n        lockMode = LockMode.STRICT\n    }' \
  $'buildscript {\n    dependencyLocking {\n        lockMode = LockMode.DEFAULT\n    }'
expect_failure \
  "root buildscript strict lock mode removed" \
  "strict buildscript dependency lock mode must appear exactly once" \
  "$fixture"

fixture="$(make_fixture buildscript-lock-activation-removed)"
replace_once \
  "$fixture/build.gradle" \
  $'    configurations.classpath {\n        resolutionStrategy.activateDependencyLocking()\n    }' \
  $'    configurations.classpath {\n        resolutionStrategy.deactivateDependencyLocking()\n    }'
expect_failure \
  "root buildscript lock activation removed" \
  "buildscript classpath dependency locking activation must appear exactly once" \
  "$fixture"

fixture="$(make_fixture missing-version-catalog)"
rm "$fixture/gradle/libs.versions.toml"
expect_failure \
  "missing version catalog" \
  "Gradle version catalog is missing" \
  "$fixture"

fixture="$(make_fixture agp-downgrade)"
replace_once \
  "$fixture/gradle/libs.versions.toml" \
  'android_plugin = "8.10.1"' \
  'android_plugin = "8.9.2"'
expect_failure \
  "Android Gradle plugin downgrade" \
  "Android Gradle plugin must be exactly 8.10.1" \
  "$fixture"

fixture="$(make_fixture adjacent-agp-downgrade)"
replace_once \
  "$fixture/gradle/libs.versions.toml" \
  'android_plugin = "8.10.1"' \
  'android_plugin = "8.10.0"'
expect_failure \
  "adjacent Android Gradle plugin downgrade" \
  "Android Gradle plugin must be exactly 8.10.1" \
  "$fixture"

fixture="$(make_fixture unreviewed-agp-upgrade)"
replace_once \
  "$fixture/gradle/libs.versions.toml" \
  'android_plugin = "8.10.1"' \
  'android_plugin = "8.11.0"'
expect_failure \
  "unreviewed Android Gradle plugin upgrade" \
  "Android Gradle plugin must be exactly 8.10.1" \
  "$fixture"

fixture="$(make_fixture unreviewed-kotlin-metadata)"
replace_once \
  "$fixture/app/gradle.lockfile" \
  'org.jetbrains.kotlin:kotlin-stdlib:2.2.10=internalAppSharingCompileClasspath,internalAppSharingRuntimeClasspath,releaseCompileClasspath,releaseRuntimeClasspath' \
  'org.jetbrains.kotlin:kotlin-stdlib:2.3.10=internalAppSharingCompileClasspath,internalAppSharingRuntimeClasspath,releaseCompileClasspath,releaseRuntimeClasspath'
expect_failure \
  "unreviewed release Kotlin metadata" \
  "release Kotlin metadata version must be explicitly reviewed before use" \
  "$fixture"

gradle_java_home="${JAVA_HOME:-}"
if [[ ! -x "$gradle_java_home/bin/java" ]] &&
  [[ -x /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home/bin/java ]]; then
  gradle_java_home=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [[ -x "$gradle_java_home/bin/java" ]]; then
  lockdown_fixture="$tmp_dir/gradle-project-repository-lockdown"
  mkdir -p "$lockdown_fixture"
  cat >"$lockdown_fixture/settings.gradle" <<'GROOVY'
import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = 'repository-lockdown-fixture'
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}
GROOVY
  cat >"$lockdown_fixture/build.gradle" <<'GROOVY'
def delayedLocalProbe = repositories.maven {
    name = 'delayedLocalProbe'
    url = uri('https://repo.maven.apache.org/maven2')
}
gradle.taskGraph.whenReady {
    delayedLocalProbe.url = uri(layout.buildDirectory.dir('attacker-maven'))
}
GROOVY
  output="$tmp_dir/gradle-project-repository-lockdown.log"
  set +e
  JAVA_HOME="$gradle_java_home" \
    PATH="$gradle_java_home/bin:/usr/bin:/bin" \
    "$ROOT_DIR/gradlew" \
      --project-dir "$lockdown_fixture" \
      --offline \
      --no-daemon \
      help >"$output" 2>&1
  lockdown_status=$?
  set -e
  [[ "$lockdown_status" -ne 0 ]] ||
    fail "late project repository URL mutation unexpectedly succeeded"
  grep -Fq \
    "repository 'delayedLocalProbe' was added by build file" \
    "$output" || {
      sed -n '1,160p' "$output" >&2
      fail "late project repository URL mutation did not fail at repository creation"
    }
  negative_count=$((negative_count + 1))
  echo \
    "[gradle-provenance-test] release project repository lockdown behavior passed"

  symlink_fixture="$tmp_dir/gradle-staged-repository-symlink"
  git clone --quiet --no-hardlinks "$ROOT_DIR" "$symlink_fixture"
  cp "$ROOT_DIR/settings.gradle" "$symlink_fixture/settings.gradle"
  cp "$ROOT_DIR/build.gradle" "$symlink_fixture/build.gradle"
  cp \
    "$ROOT_DIR/gradle/verification-metadata.xml" \
    "$symlink_fixture/gradle/verification-metadata.xml"
  cp \
    "$ROOT_DIR/gradle/verification-metadata.sha256" \
    "$symlink_fixture/gradle/verification-metadata.sha256"
  cp \
    "$ROOT_DIR/iroha-sdk-bridge/build.gradle" \
    "$symlink_fixture/iroha-sdk-bridge/build.gradle"
  cp \
    "$ROOT_DIR/iroha-sdk-bridge-kotlin-smoke/build.gradle" \
    "$symlink_fixture/iroha-sdk-bridge-kotlin-smoke/build.gradle"
  mkdir -p \
    "$symlink_fixture/build" \
    "$tmp_dir/external-staged-repository/maven"
  ln -s \
    "$tmp_dir/external-staged-repository" \
    "$symlink_fixture/build/iroha-mobile-sdk"
  output="$tmp_dir/gradle-staged-repository-symlink.log"
  set +e
  JAVA_HOME="$gradle_java_home" \
    PATH="$gradle_java_home/bin:/usr/bin:/bin" \
    CI=true \
    "$symlink_fixture/gradlew" \
      --project-dir "$symlink_fixture" \
      --offline \
      --no-daemon \
      :iroha-sdk-bridge:dependencies >"$output" 2>&1
  symlink_status=$?
  set -e
  [[ "$symlink_status" -ne 0 ]] ||
    fail "intermediate staged-repository symlink unexpectedly resolved"
  grep -Fq \
    "The staged Iroha repository path contains a symbolic link." \
    "$output" || {
      sed -n '1,160p' "$output" >&2
      fail "intermediate staged-repository symlink did not fail at settings validation"
    }
  negative_count=$((negative_count + 1))
  echo \
    "[gradle-provenance-test] staged-repository intermediate symlink behavior passed"
else
  fail "a Java 21 runtime is required for Gradle repository behavior tests"
fi

[[ "$positive_count" == "$EXPECTED_POSITIVE_COUNT" ]] ||
  fail "expected $EXPECTED_POSITIVE_COUNT positive cases; got $positive_count"
[[ "$negative_count" == "$EXPECTED_NEGATIVE_COUNT" ]] ||
  fail "expected $EXPECTED_NEGATIVE_COUNT negative cases; got $negative_count"

echo \
  "[gradle-provenance-test] $positive_count positive + $negative_count negative cases passed"
