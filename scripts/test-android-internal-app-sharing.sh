#!/usr/bin/env bash
set -euo pipefail
umask 077

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
PUBLIC_FIREBASE="$ROOT_DIR/app/src/release/google-services.json"
FORBIDDEN_IAS_SOURCE="$ROOT_DIR/app/src/internalAppSharing"
mkdir -p "$ROOT_DIR/build/test-tmp"
temporary_dir="$(mktemp -d "$ROOT_DIR/build/test-tmp/android-ias-guards.XXXXXX")"

cleanup() {
  local exit_code=$?
  local cleanup_status=0
  trap - EXIT
  set +e
  rm -rf "$temporary_dir"
  cleanup_status=$?
  if [[ "$exit_code" -eq 0 && "$cleanup_status" -ne 0 ]]; then
    echo "[android-ias-gradle-test][error] unable to remove temporary directory." >&2
    exit "$cleanup_status"
  fi
  exit "$exit_code"
}
trap cleanup EXIT
trap 'exit 130' HUP INT TERM

fail() {
  echo "[android-ias-gradle-test][error] $*" >&2
  exit 1
}

for command_name in awk cmp cp find git grep ln mktemp python3 sort; do
  command -v "$command_name" >/dev/null 2>&1 ||
    fail "required command is unavailable: $command_name"
done
[[ -x "$GRADLEW" ]] || fail "Gradle wrapper is missing."
[[ ! -L "$PUBLIC_FIREBASE" && -f "$PUBLIC_FIREBASE" && -s "$PUBLIC_FIREBASE" ]] ||
  fail "public Firebase fixture is missing or unsafe."
[[ ! -e "$FORBIDDEN_IAS_SOURCE" && ! -L "$FORBIDDEN_IAS_SOURCE" ]] ||
  fail "zero-copy IAS forbids app/src/internalAppSharing."

sha256_file() {
  if command -v sha256sum >/dev/null 2>&1; then
    sha256sum "$1" | awk '{print $1}'
  else
    shasum -a 256 "$1" | awk '{print $1}'
  fi
}

snapshot_release_outputs() {
  local output="$1"
  : >"$output"
  [[ -d "$ROOT_DIR/app/build/outputs/bundle/release" ]] || return 0
  find "$ROOT_DIR/app/build/outputs/bundle/release" -type f -print0 |
    LC_ALL=C sort -z |
    while IFS= read -r -d '' file; do
      printf '%s  %s\n' "$(sha256_file "$file")" "${file#"$ROOT_DIR/"}"
    done >"$output"
}

snapshot_source() {
  local prefix="$1"
  git -C "$ROOT_DIR" status --porcelain=v1 -z --untracked-files=all >"$prefix.status"
  git -C "$ROOT_DIR" diff --binary HEAD >"$prefix.diff"
  sha256_file "$PUBLIC_FIREBASE" >"$prefix.firebase"
}

assert_source_unchanged() {
  local prefix="$1"
  local label="$2"
  local current="$temporary_dir/current-source"
  snapshot_source "$current"
  cmp -s "$prefix.status" "$current.status" || fail "$label changed Git status."
  cmp -s "$prefix.diff" "$current.diff" || fail "$label changed tracked source."
  cmp -s "$prefix.firebase" "$current.firebase" || fail "$label changed public Firebase bytes."
  [[ ! -e "$FORBIDDEN_IAS_SOURCE" && ! -L "$FORBIDDEN_IAS_SOURCE" ]] ||
    fail "$label created a forbidden IAS source tree."
}

base_environment=(
  SKIP_AUTO_VERSION_BUMP=true
  FORCE_LOCAL_UTILS=true
  USE_REMOTE_UTILS=false
  FEARLESS_UTILS_LIBRARY_ONLY=true
)

run_gradle_with_env() {
  local default_ci=(CI=false)
  if [[ "${1:-}" == "__UNSET_CI__" ]]; then
    default_ci=()
    shift
  fi
  local environment=()
  while [[ "$#" -gt 0 && "$1" != "--" ]]; do
    environment+=("$1")
    shift
  done
  [[ "$#" -gt 0 ]] || fail "run_gradle_with_env requires -- before Gradle arguments."
  shift
  env \
    -u ANDROID_IAS_DEBUG_KEYSTORE_PATH \
    -u ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
    -u ANDROID_IAS_DEBUG_CERT_SHA256 \
    -u EXPECTED_IAS_DEBUG_CERT_SHA256 \
    -u ANDROID_UNSIGNED_RELEASE_BUILD \
    -u CI_KEYSTORE_PATH \
    -u CI_KEYSTORE_PASS \
    -u CI_KEYSTORE_KEY_ALIAS \
    -u CI_KEYSTORE_KEY_PASS \
    -u ANDROID_RELEASE_KEYSTORE_B64 \
    -u PLAY_SERVICE_ACCOUNT_JSON_B64 \
    -u PLAY_SERVICE_ACCOUNT \
    -u CI_PLAY_KEY \
    -u PLAY_TRACK \
    -u PLAY_RELEASE_STATUS \
    -u PLAY_ARTIFACT_DIR \
    -u ALLOW_PLAY_PRODUCTION_UPLOAD \
    -u GOOGLE_SERVICES_RELEASE_PATH \
    -u GOOGLE_SERVICES_RELEASE_JSON_B64 \
    -u ANDROID_RELEASE_FIREBASE_JSON_SHA256 \
    -u FIREBASE_TOKEN \
    -u GOOGLE_APPLICATION_CREDENTIALS \
    -u CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE \
    -u GIT_DIR \
    -u GIT_WORK_TREE \
    -u GIT_INDEX_FILE \
    -u GIT_OBJECT_DIRECTORY \
    -u GIT_ALTERNATE_OBJECT_DIRECTORIES \
    -u CI \
    "${base_environment[@]}" \
    ${default_ci[@]+"${default_ci[@]}"} \
    ${environment[@]+"${environment[@]}"} \
    "$GRADLEW" "$@" --no-daemon --console=plain
}

negative_count=0
expect_failure() {
  local label="$1"
  local diagnostic="$2"
  shift 2
  local output="$temporary_dir/failure-$negative_count.log"
  if "$@" >"$output" 2>&1; then
    fail "$label unexpectedly passed."
  fi
  grep -Fq "$diagnostic" "$output" || {
    sed -n '1,180p' "$output" >&2
    fail "$label did not emit expected diagnostic: $diagnostic"
  }
  assert_source_unchanged "$source_before" "$label"
  negative_count=$((negative_count + 1))
}

source_before="$temporary_dir/source-before"
snapshot_source "$source_before"
release_outputs_before="$temporary_dir/release-outputs-before"
snapshot_release_outputs "$release_outputs_before"

static_count_file="$temporary_dir/static-contract-count.txt"
if ! python3 - \
    "$ROOT_DIR/app/build.gradle" \
    "$ROOT_DIR/build.gradle" \
    "$ROOT_DIR/app/gradle.lockfile" \
    "$ROOT_DIR/app/.gitignore" \
    "$ROOT_DIR/.github/workflows/android-internal-app-sharing.yml" \
    "$ROOT_DIR/scripts/atomic-noreplace-move.py" \
    "$ROOT_DIR/scripts/verify-android-internal-app-sharing-aab.sh" \
    "$ROOT_DIR/scripts/test-android-internal-app-sharing.sh" <<'PY' \
    >"$static_count_file" \
    2>"$temporary_dir/static-contract-errors.log"; then
import pathlib
import re
import sys

app = pathlib.Path(sys.argv[1]).read_text(encoding="utf-8")
root = pathlib.Path(sys.argv[2]).read_text(encoding="utf-8")
locks = pathlib.Path(sys.argv[3]).read_text(encoding="utf-8")
ignore = pathlib.Path(sys.argv[4]).read_text(encoding="utf-8")
workflow_path = pathlib.Path(sys.argv[5])
obsolete_helper = pathlib.Path(sys.argv[6])
verifier = pathlib.Path(sys.argv[7]).read_text(encoding="utf-8")
self_test = pathlib.Path(sys.argv[8]).read_text(encoding="utf-8")

required_app = [
    'requestedTaskNames == [":app:bundleInternalAppSharing"]',
    'requestedTaskNames == [":app:verifyInternalAppSharingConfiguration"]',
    'System.getenv("CI") != "true"',
    '--no-build-cache and --rerun-tasks',
    'Internal App Sharing forbids every release keystore and password input.',
    'Internal App Sharing forbids injected Android signing properties.',
    'Internal App Sharing forbids excluded Gradle tasks.',
    '"FIREBASE_TOKEN"',
    '"GOOGLE_APPLICATION_CREDENTIALS"',
    '"CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE"',
    'FEARLESS_UTILS_EFFECTIVE_TREE',
    'verify-android-release-source-tree.sh',
    'ANDROID_IAS_DEBUG_KEYSTORE_PATH',
    'ANDROID_IAS_DEBUG_CERTIFICATE_PATH',
    'ANDROID_IAS_DEBUG_CERT_SHA256',
    'EXPECTED_IAS_DEBUG_CERT_SHA256',
    '3085af682c8c8a166142f7ef867e0c3fa06a6f87436f3a15497f8ae7dac93d2f',
    'https://fearless-public.firebaseio.com',
    'fearless-public.appspot.com',
    'GoogleServicesTask',
    'processInternalAppSharingGoogleServices',
    'gradle.projectsEvaluated {',
    'boundIasGoogleServicesTask.googleServicesJsonFiles.set(',
    'boundIasGoogleServicesTask.googleServicesJsonFiles.finalizeValue()',
    'Collections.singletonList(releaseGoogleServicesFile)',
    'IAS Google Services direct input',
    'res.srcDir "src/release/res"',
    'outputs.cacheIf("IAS release-candidate output must never come from build cache")',
    'Resolved Internal App Sharing tasks require one exact',
    'must have exactly one filesystem link.',
    'initWith release',
    'signingConfig null',
    'final one-run signer is created only after this Gradle job',
    'iasBuildType.signingConfig != null',
    'unsigned quarantine intermediate',
    "versionNameSuffix '-ias'",
    'tasks.register("verifyInternalAppSharingConfiguration")',
    'beforeVariants(selector().withBuildType("internalAppSharing"))',
    'variantBuilder.enable = false',
    'verifyInternalAppSharingDependencyLocks',
]
forbidden_app = [
    'generatedIas',
    'iasBridge',
    'atomicNoReplaceMove',
    'src/internalAppSharing',
    'configuredIasDebugKeystore',
    'requestedIasDebugKeystore',
    'iasDebugKeyStore',
    "apply plugin: 'com.github.triplet.play'",
    'play {',
]
required_lock_configs = [
    'hiltAnnotationProcessorInternalAppSharing',
    'kotlinCompilerPluginClasspathInternalAppSharing',
    'kspInternalAppSharingKotlinProcessorClasspath',
    'internalAppSharingAnnotationProcessorClasspath',
    'internalAppSharingCompileClasspath',
    'internalAppSharingRuntimeClasspath',
]
required_workflow = [
    'workflow_dispatch:',
    'pull_request:',
    'schedule:',
    'IAS_CANDIDATE_SHA',
    'permissions:',
    'contents: read',
    'bundleInternalAppSharing',
    'Build exact non-cached unsigned IAS AAB',
    'Prepare root-owned read-only pre-sign boundary',
    'Validate downloaded unsigned quarantine independently',
    'Terminate pre-sign UID and transfer exact unsigned bytes',
    'Create step-local signer, externally sign, export public cert, and erase key',
    'Prepare public-certificate-only post-sign boundary',
    'Run signed-from verifier and adversarial suite with public cert only',
    'Kill post-sign UID before runner-owned handoff',
    '--signed-from',
    '--no-build-cache',
    '--rerun-tasks',
    'verify-android-internal-app-sharing-aab.sh',
    'test-android-internal-app-sharing-aab.sh',
    'actions/upload-artifact',
    'Download back exact immutable uploaded archive by ID and digest',
    'Revalidate actual uploaded archive contents under disposable UID',
    'Terminate upload-audit UID and complete qualification',
    'Delete every unqualified or stale pending IAS artifact',
    'Sweep canceled, failed, stale, or ambiguous pending IAS artifacts',
    'Remove qualifier candidates and local handoff',
    'if: always()',
]
required_verifier = [
    'verification_mode="unsigned"',
    '--signed-from',
    'Unsigned IAS verification forbids every signer input',
    'verify_unsigned_signature_state',
    'verify_external_signing_transform',
    'external signing changed non-signature AAB entries',
    '"$unsigned_source_input" "$unsigned_source_identity" "unsigned IAS source AAB"',
]
required_self_test = [
    "local exit_code=$?",
    'local cleanup_status=0\n  trap - EXIT\n  set +e\n  rm -rf "$temporary_dir"\n  cleanup_status=$?',
    'if [[ "$exit_code" -eq 0 && "$cleanup_status" -ne 0 ]]; then\n'
    '    echo "[android-ias-gradle-test][error] unable to remove temporary directory." >&2\n'
    '    exit "$cleanup_status"\n'
    '  fi\n'
    '  exit "$exit_code"',
    "trap 'exit 130' HUP INT TERM",
    '${default_ci[@]+"${default_ci[@]}"}',
    '${environment[@]+"${environment[@]}"}',
]

def step_block(text, name):
    pattern = re.compile(
        rf"(?ms)^      - name: {re.escape(name)}\n"
        r"(?P<body>.*?)(?=^      - name: |^  [a-zA-Z0-9_-]+:\n|\Z)"
    )
    matches = list(pattern.finditer(text))
    if len(matches) != 1:
        return None
    return matches[0].group(0)


def job_block(text, name):
    if "jobs:\n" not in text:
        return None
    jobs = text.split("jobs:\n", 1)[1]
    pattern = re.compile(
        rf"(?ms)^  {re.escape(name)}:\n"
        r".*?(?=^  [a-zA-Z0-9_-]+:\n|\Z)"
    )
    matches = list(pattern.finditer(jobs))
    if len(matches) != 1:
        return None
    return matches[0].group(0)


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise ValueError(f"mutation precondition expected one occurrence: {old!r}")
    return text.replace(old, new, 1)


def mutate_step(text, name, old, new):
    block = step_block(text, name)
    if block is None:
        raise ValueError(f"mutation step missing: {name}")
    mutated = replace_once(block, old, new)
    return replace_once(text, block, mutated)


def mutate_job(text, name, old, new):
    block = job_block(text, name)
    if block is None:
        raise ValueError(f"mutation job missing: {name}")
    mutated = replace_once(block, old, new)
    return replace_once(text, block, mutated)


# The IAS handoff deliberately treats every checked-out parser as untrusted.
# Keep this validator close to the mutation corpus below: each security boundary
# is both asserted on the real workflow and independently weakened/deleted.
def trust_contract_errors(text, count_assertion=None):
    errors = []

    def require(condition, diagnostic):
        if count_assertion is not None:
            count_assertion()
        if not condition:
            errors.append(diagnostic)

    producer = job_block(text, "validate-candidate")
    qualifier = job_block(text, "qualify-handoff")
    finalizer = job_block(text, "finalize-handoff")
    janitor = job_block(text, "sweep-stale-pending-handoffs")
    require(producer is not None, "producer job must exist exactly once")
    require(qualifier is not None, "qualifier job must exist exactly once")
    require(finalizer is not None, "artifact-deletion finalizer must exist exactly once")
    require(janitor is not None, "scheduled pending-artifact janitor must exist exactly once")
    if producer is None or qualifier is None or finalizer is None or janitor is None:
        return errors

    context = step_block(text, "Require exact first-party workflow context")
    validation_only = step_block(text, "Mark pull request run validation-only")
    require(context is not None and context in producer, "first-party workflow context gate must exist in producer")
    require(validation_only is not None and validation_only in producer, "PR validation-only marker must exist in producer")
    if context is not None:
        require(
            '[[ "${GITHUB_REPOSITORY:-}" == "soramitsu/fearless-Android" ]]' in context
            and '[[ "$PULL_REQUEST_BASE_REF" == "develop" ]]' in context
            and '[[ "$DISPATCH_REF" == "refs/heads/develop" ]]' in context
            and '[[ "$DISPATCH_REF_PROTECTED" == "true" ]]' in context,
            "workflow context must require first-party protected develop",
        )
    if validation_only is not None:
        require(
            "if: github.event_name == 'pull_request'" in validation_only
            and "non-distributable validation only" in validation_only
            and "No handoff or upload-artifact step may run" in validation_only,
            "pull requests must remain explicitly non-distributable validation only",
        )
    require(
        "on:\n  workflow_dispatch:\n  pull_request:\n    branches: [develop]\n  schedule:\n    - cron: \"17,47 * * * *\"" in text,
        "workflow triggers must retain dispatch/PR validation plus scheduled cleanup",
    )
    require(
        "cancel-in-progress: ${{ github.event_name == 'pull_request' }}" in text
        and "'pending-artifact-janitor'" in text
        and "if: ${{ github.event_name == 'workflow_dispatch' || github.event_name == 'pull_request' }}" in producer,
        "dispatch runs must not be canceled after upload and schedule must bypass producer execution",
    )
    require(
        re.search(r"(?m)^permissions:\n  contents: read$", text) is not None,
        "workflow default permissions must remain contents:read",
    )
    require(
        'producer_artifact_id: ${{ steps.upload-producer-quarantine.outputs.artifact-id }}' in producer
        and 'producer_artifact_digest: ${{ steps.upload-producer-quarantine.outputs.artifact-digest }}' in producer,
        "producer must export exact quarantine artifact ID and digest",
    )
    require(
        "jarsigner" not in producer
        and "keytool -genkeypair" not in producer
        and "signer-certificate.pem" not in producer
        and "fearless-android-ias-trusted-develop-" not in producer,
        "producer must never receive a signer or create a trusted handoff",
    )
    exact_producer_gate = (
        "if: ${{ github.event_name == 'workflow_dispatch' && "
        "github.repository == 'soramitsu/fearless-Android' && "
        "github.ref == 'refs/heads/develop' && "
        "github.ref_protected == true }}"
    )
    for producer_step_name in (
        "Create untrusted producer quarantine",
        "Recheck untrusted producer quarantine",
        "Upload untrusted producer quarantine",
        "Post-upload producer quarantine check",
    ):
        producer_step = step_block(text, producer_step_name)
        require(
            producer_step is not None
            and producer_step in producer
            and exact_producer_gate in producer_step,
            f"producer handoff step {producer_step_name!r} must use exact dispatch gate",
        )
    producer_upload = step_block(text, "Upload untrusted producer quarantine")
    require(
        producer_upload is not None
        and "fearless-android-ias-UNTRUSTED-producer-" in producer_upload
        and "retention-days: 1" in producer_upload
        and "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02" in producer_upload,
        "producer may upload only a one-day explicitly untrusted quarantine",
    )
    require(
        'IAS_PRODUCER_ARTIFACT_ID: ${{ needs.validate-candidate.outputs.producer_artifact_id }}' in qualifier
        and 'IAS_PRODUCER_ARTIFACT_DIGEST: ${{ needs.validate-candidate.outputs.producer_artifact_digest }}' in qualifier,
        "qualifier must consume exact producer artifact ID and digest outputs",
    )
    immutable = step_block(text, "Bind exact immutable producer artifact")
    producer_download = step_block(text, "Download untrusted producer quarantine")
    require(
        immutable is not None
        and immutable in qualifier
        and "GH_TOKEN: ${{ github.token }}" in immutable
        and 'artifacts/$IAS_PRODUCER_ARTIFACT_ID' in immutable
        and ".workflow_run.id" in immutable
        and ".workflow_run.head_sha" in immutable
        and ".digest // empty" in immutable,
        "qualifier must bind immutable producer artifact metadata",
    )
    require(
        producer_download is not None
        and producer_download in qualifier
        and "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093" in producer_download
        and 'artifact-ids: ${{ env.IAS_PRODUCER_ARTIFACT_ID }}' in producer_download,
        "qualifier must download producer quarantine only by immutable ID with pinned action",
    )
    require(text.count("actions/upload-artifact@") == 2, "workflow must have exactly untrusted and pending uploaders")
    require(
        not re.search(r"(?i)(firebaseAppDistribution|PLAY_SERVICE_ACCOUNT\s*:|CI_PLAY_KEY\s*:)", text),
        "IAS workflow must never invoke a production distributor",
    )

    exact_qualifier_gate = (
        "if: ${{ needs.validate-candidate.result == 'success' && "
        "github.event_name == 'workflow_dispatch' && "
        "github.repository == 'soramitsu/fearless-Android' && "
        "github.ref == 'refs/heads/develop' && "
        "github.ref_protected == true }}"
    )
    exact_finalizer_gate = (
        "if: ${{ always() && github.event_name == 'workflow_dispatch' && "
        "github.repository == 'soramitsu/fearless-Android' && "
        "github.ref == 'refs/heads/develop' && "
        "github.ref_protected == true }}"
    )
    require(exact_qualifier_gate in qualifier, "qualifier must use the exact protected-develop gate")
    require(exact_finalizer_gate in finalizer, "finalizer must run always under the exact protected-develop gate")
    require(
        "permissions:\n      actions: read\n      contents: read" in qualifier,
        "qualifier permissions must remain actions:read and contents:read",
    )
    require(
        "permissions:\n      actions: write\n      contents: read" in finalizer,
        "fresh finalizer must receive narrowly scoped actions:write",
    )
    require(
        "permissions:\n      actions: write\n      contents: read" in janitor
        and text.count("actions: write") == 2,
        "only artifact cleanup jobs may receive actions:write",
    )
    require("actions: write" not in producer, "producer must not receive actions:write")
    require("actions: write" not in qualifier, "qualifier must not receive actions:write")
    require("./gradlew" not in qualifier, "qualifier must never execute Gradle")
    require(
        'pending_artifact_id: ${{ steps.upload-pending-handoff.outputs.artifact-id }}' in qualifier
        and 'pending_artifact_digest: ${{ steps.upload-pending-handoff.outputs.artifact-digest }}' in qualifier
        and 'qualification_complete: ${{ steps.complete-qualification.outputs.qualified }}' in qualifier,
        "qualifier must export exact pending artifact and completion outputs",
    )

    step_names = [
        "Bind qualifier source with trusted system tools only",
        "Prepare root-owned read-only pre-sign boundary",
        "Validate downloaded unsigned quarantine independently",
        "Terminate pre-sign UID and transfer exact unsigned bytes",
        "Create step-local signer, externally sign, export public cert, and erase key",
        "Prepare public-certificate-only post-sign boundary",
        "Run signed-from verifier and adversarial suite with public cert only",
        "Kill post-sign UID before runner-owned handoff",
        "Recheck exact trusted handoff bytes",
        "Upload pending exact-byte handoff for immutable audit",
        "Download back exact immutable uploaded archive by ID and digest",
        "Revalidate actual uploaded archive contents under disposable UID",
        "Terminate upload-audit UID and complete qualification",
        "Remove qualifier candidates and local handoff",
    ]
    blocks = {name: step_block(text, name) for name in step_names}
    for name, block in blocks.items():
        require(
            block is not None and block in qualifier,
            f"qualifier boundary step {name!r} must exist exactly once",
        )
    if any(block is None for block in blocks.values()):
        return errors

    bind = blocks[step_names[0]]
    pre_prepare = blocks[step_names[1]]
    pre_verify = blocks[step_names[2]]
    pre_kill = blocks[step_names[3]]
    signer = blocks[step_names[4]]
    post_prepare = blocks[step_names[5]]
    post_verify = blocks[step_names[6]]
    post_kill = blocks[step_names[7]]
    recheck = blocks[step_names[8]]
    upload = blocks[step_names[9]]
    download_back = blocks[step_names[10]]
    upload_audit = blocks[step_names[11]]
    complete = blocks[step_names[12]]
    cleanup = blocks[step_names[13]]

    ordered = [blocks[name] for name in step_names]
    require(
        all(qualifier.index(left) < qualifier.index(right) for left, right in zip(ordered, ordered[1:])),
        "qualifier security phases must remain in fail-closed order",
    )
    require(
        "./scripts/" not in qualifier
        and qualifier.count('/bin/bash "$1/scripts/verify-android-internal-app-sharing-aab.sh"') == 3
        and qualifier.count('/bin/bash "$1/scripts/test-android-internal-app-sharing-aab.sh"') == 1,
        "checkout-controlled scripts may execute only through isolated UID wrappers",
    )
    for isolated in (pre_verify, post_verify, upload_audit):
        require(
            "/usr/bin/env -i" in isolated
            and re.search(r'sudo -u "[^"\n]+" /usr/bin/env -i', isolated) is not None
            and "/usr/bin/sudo -n -u root /usr/bin/true" in isolated,
            "each checkout-controlled parser must run under env-i and a no-sudo UID",
        )
        require(
            "runner_command_file" in isolated
            and "GITHUB_ENV" in isolated
            and "GITHUB_OUTPUT" in isolated
            and "GITHUB_PATH" in isolated
            and "GITHUB_STEP_SUMMARY" in isolated
            and 'test -w "$runner_command_file"' in isolated,
            "each untrusted UID must be unable to write runner command files",
        )
        require(
            "umask 077" in isolated
            and "| tee" not in isolated
            and "/usr/bin/head -c 16777217" in isolated
            and re.search(r'>"\$(?:log|audit_log)"; then', isolated) is not None
            and "-le 16777216 ]]" in isolated,
            "untrusted parser output must stay in a private log outside runner command parsing",
        )

    require(
        "./scripts/ensure-fearless-utils.sh" not in bind
        and "./scripts/verify-android-release-source-tree.sh" not in bind
        and "git -C \"$utils_path\" apply" in bind,
        "normal qualifier UID must use only inline trusted source binding before signing",
    )
    require(
        "principal=iaspresign" in pre_prepare
        and "reserved_uid=61001" in pre_prepare
        and '--uid "$reserved_uid"' in pre_prepare
        and "useradd --system --user-group --no-create-home" in pre_prepare
        and "--shell /usr/sbin/nologin" in pre_prepare
        and "grep -Eq '^(sudo|admin)$'" in pre_prepare,
        "pre-sign verifier must use a dedicated no-login no-sudo principal",
    )
    require(
        'sudo install -d -o root -g root -m 0555 "$boundary/input"' in pre_prepare
        and "/var/tmp/fearless-ias-presign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX" in pre_prepare
        and 'sudo chmod 0711 "$boundary"' in pre_prepare
        and 'sudo install -o root -g root -m 0444 "$source_path"' in pre_prepare
        and 'chown -R root:root "$GITHUB_WORKSPACE"' in pre_prepare
        and "-perm /022" in pre_prepare,
        "pre-sign source and inputs must be root-owned and read-only",
    )
    require(
        'runner_command_dir="${GITHUB_ENV%/*}"' in pre_prepare
        and '[[ "$runner_command_dir" == "$RUNNER_TEMP"/* ]]' in pre_prepare
        and 'chmod 0700 "$runner_command_dir"' in pre_prepare
        and 'chmod 0600 {} +' in pre_prepare,
        "runner command files must be private before untrusted UID execution",
    )
    require(
        'install -d -o "$principal_uid" -g "$principal_gid" -m 0700' in pre_prepare
        and '"$boundary/home" "$boundary/tmp" "$boundary/output"' in pre_prepare,
        "pre-sign UID must receive only private writable home/tmp/output state",
    )
    require(
        'sudo -u "$IAS_PRESIGN_PRINCIPAL" /usr/bin/env -i' in pre_verify
        and "GIT_OPTIONAL_LOCKS=0" in pre_verify
        and "--unsigned" in pre_verify
        and 'BUNDLETOOL_JAR="$IAS_PRESIGN_BOUNDARY/tools/bundletool.jar"' in pre_verify
        and '"$2/output/verified-unsigned.aab"' in pre_verify
        and "required=(" in pre_verify
        and 'workflow_ref_protected=true' in pre_verify,
        "unsigned AAB and producer evidence must be verified inside the pre-sign UID",
    )
    for evidence_binding in (
        '"signature_state=unsigned"',
        '"source_commit=$3"',
        '"source_tree=$4"',
        '"fearless_utils_tree=$6"',
        '"workflow_ref_protected=true"',
        '"workflow_run_id=$9"',
    ):
        require(
            evidence_binding in pre_verify,
            f"unsigned AAB and producer evidence must bind {evidence_binding}",
        )
    require(
        "if: always()" in pre_kill
        and 'ISOLATED_OUTCOME: ${{ steps.isolated-unsigned-verification.outcome }}' in pre_kill
        and 'pkill -KILL -u "$IAS_PRESIGN_UID"' in pre_kill
        and pre_kill.count('pgrep -u "$IAS_PRESIGN_UID"') >= 2
        and pre_kill.count('userdel "$IAS_PRESIGN_PRINCIPAL"') == 2
        and pre_kill.count('rm -rf -- "$IAS_PRESIGN_BOUNDARY"') == 2
        and pre_kill.count('groupdel "$IAS_PRESIGN_PRINCIPAL"') == 2
        and 'getent group "$IAS_PRESIGN_PRINCIPAL"' in pre_kill
        and pre_kill.count('find "$RUNNER_TEMP" /tmp /var/tmp /dev/shm -xdev') == 2
        and pre_kill.count('-uid "$IAS_PRESIGN_UID" -depth -delete') == 2
        and '"$ISOLATED_OUTCOME" != "success"' in pre_kill,
        "every pre-sign UID process and writable state must die before signing",
    )
    require(
        '"$unsigned_sha" == ' in pre_kill
        and "candidate-unsigned.aab" in pre_kill
        and 'install -o "$(id -u)" -g "$(id -g)" -m 0400' in pre_kill,
        "only exact verified unsigned bytes may cross into runner ownership",
    )
    require(
        'if sudo test -L "$isolated"; then' in pre_kill
        and 'sudo test -f "$isolated"' in pre_kill
        and '"1:$IAS_PRESIGN_UID"' in pre_kill
        and '"$isolated_size" -le 262144000' in pre_kill
        and 'chown root:root "$isolated"' not in pre_kill
        and 'chmod 0400 "$isolated"' not in pre_kill
        and 0 <= pre_kill.find('if pgrep -u "$IAS_PRESIGN_UID"')
        < pre_kill.find('isolated="$IAS_PRESIGN_BOUNDARY/output/verified-unsigned.aab"')
        < pre_kill.find('sudo install -o "$(id -u)"'),
        "hostile pre-sign output must be process-quiesced and reject symlink, nonregular, hardlink, or oversize state before privileged copy",
    )

    require(
        "ephemeral-final-signer.jks" in signer
        and "keytool -genkeypair" in signer
        and "jarsigner" in signer
        and "cleanup_signer" in signer
        and "trap cleanup_signer EXIT" in signer
        and "trap 'cleanup_signer; exit 130' HUP INT TERM" in signer,
        "final signer must be generated and used only inside one trap-protected step",
    )
    require(
        signer.count('rm -f -- "$signer"') == 2
        and 'rm -f -- "$signer"\n          signer=""' in signer
        and "-name '*.jks'" in signer
        and "trap - EXIT HUP INT TERM" in signer,
        "final keystore must be erased and absence-proved before leaving signer step",
    )
    require(
        "keytool -exportcert -rfc" in signer
        and "IAS_PUBLIC_CERTIFICATE=" in signer
        and "IAS_PUBLIC_CERTIFICATE_SHA256=" in signer,
        "signer step may export only its public certificate and fingerprint",
    )
    require(
        not re.search(
            r'echo\s+"[^"\n]*(?:KEYSTORE|JKS|ephemeral-final-signer)[^"\n]*"\s*>>"\$GITHUB_ENV"',
            signer,
            re.IGNORECASE,
        )
        and "ANDROID_IAS_DEBUG_KEYSTORE_PATH=" not in qualifier,
        "final private-key path must never reach GITHUB_ENV or an isolated parser",
    )
    require("/scripts/" not in signer, "no checkout-controlled code may execute while final JKS exists")

    require(
        "principal=iaspostsign" in post_prepare
        and "reserved_uid=61002" in post_prepare
        and '--uid "$reserved_uid"' in post_prepare
        and 'sudo install -o root -g root -m 0444 "$IAS_PUBLIC_CERTIFICATE"' in post_prepare
        and '"$boundary/input/final-signer-public-certificate.pem"' in post_prepare
        and "candidate-signed.aab" in post_prepare
        and "-name '*.jks'" in post_prepare
        and "-perm /022" in post_prepare,
        "post-sign UID boundary must contain read-only bytes and public certificate only",
    )
    require(
        "/var/tmp/fearless-ias-postsign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX" in post_prepare
        and 'sudo chmod 0711 "$boundary"' in post_prepare,
        "post-sign boundary must be a fresh root-owned traversable enclave",
    )
    require(
        'sudo -u "$IAS_POSTSIGN_PRINCIPAL" /usr/bin/env -i' in post_verify
        and "ANDROID_IAS_DEBUG_CERTIFICATE_PATH=" in post_verify
        and 'BUNDLETOOL_JAR="$IAS_POSTSIGN_BOUNDARY/tools/bundletool.jar"' in post_verify
        and '[[ -z "${ANDROID_IAS_DEBUG_KEYSTORE_PATH:-}" ]]' in post_verify
        and "--signed-from" in post_verify
        and "test-android-internal-app-sharing-aab.sh" in post_verify,
        "signed verifier and adversarial suite must receive only the public certificate",
    )
    require(
        qualifier.index(signer) < qualifier.index(post_verify)
        and qualifier.index(signer) < qualifier.index(post_prepare)
        and qualifier.index(pre_kill) < qualifier.index(signer),
        "key erasure must precede all checkout-controlled post-sign code",
    )
    require(
        "if: always()" in post_kill
        and 'pkill -KILL -u "$IAS_POSTSIGN_UID"' in post_kill
        and post_kill.count('pgrep -u "$IAS_POSTSIGN_UID"') >= 2
        and post_kill.count('userdel "$IAS_POSTSIGN_PRINCIPAL"') == 2
        and post_kill.count('groupdel "$IAS_POSTSIGN_PRINCIPAL"') == 2
        and post_kill.count('find "$RUNNER_TEMP" /tmp /var/tmp /dev/shm -xdev') == 2
        and post_kill.count('-uid "$IAS_POSTSIGN_UID" -depth -delete') == 2
        and '"$ISOLATED_OUTCOME" != "success"' in post_kill,
        "post-sign UID must be killed and removed before runner handoff",
    )
    require(
        'if sudo test -L "$isolated"; then' in post_kill
        and 'sudo test -f "$isolated"' in post_kill
        and '"1:$IAS_POSTSIGN_UID"' in post_kill
        and '"$isolated_size" -le 262144000' in post_kill
        and 'chown root:root "$isolated"' not in post_kill
        and 'chmod 0400 "$isolated"' not in post_kill
        and 0 <= post_kill.find('if pgrep -u "$IAS_POSTSIGN_UID"')
        < post_kill.find('isolated="$IAS_POSTSIGN_BOUNDARY/output/verified-signed.aab"')
        < post_kill.find('sudo install -o "$(id -u)"'),
        "hostile post-sign output must be process-quiesced and reject symlink, nonregular, hardlink, or oversize state before privileged copy",
    )
    require(
        qualifier.index(post_kill) < qualifier.index(recheck) < qualifier.index(upload),
        "runner-owned handoff and upload must occur only after post-sign UID death",
    )

    pending_name = "fearless-android-ias-PENDING-UNTRUSTED-until-download-back-succeeds-"
    require(
        pending_name in upload
        and "retention-days: 7" in upload
        and "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02" in upload,
        "uploaded handoff must remain explicitly pending until download-back audit succeeds",
    )
    require(
        'UPLOADED_ARTIFACT_ID: ${{ steps.upload-pending-handoff.outputs.artifact-id }}' in download_back
        and 'UPLOADED_ARTIFACT_DIGEST: ${{ steps.upload-pending-handoff.outputs.artifact-digest }}' in download_back
        and 'artifacts/$UPLOADED_ARTIFACT_ID/zip' in download_back
        and 'sha256sum "$archive"' in download_back
        and '"$UPLOADED_ARTIFACT_DIGEST"' in download_back,
        "actual immutable uploaded ZIP must be fetched by exact ID and digest-checked",
    )
    require(
        'wc -c <"$archive"' in download_back and '" -le 262225920 ]]' in download_back,
        "downloaded artifact archive must be size-bounded before parsing",
    )
    for metadata_check, metadata_field in (
        ('$(jq -r \'.id\' <<<"$metadata")', ".id"),
        ('$(jq -r \'.name\' <<<"$metadata")', ".name"),
        ('$(jq -r \'.digest // empty\' <<<"$metadata")', ".digest // empty"),
        ('$(jq -r \'.workflow_run.id\' <<<"$metadata")', ".workflow_run.id"),
        ('$(jq -r \'.workflow_run.head_sha\' <<<"$metadata")', ".workflow_run.head_sha"),
    ):
        require(metadata_check in download_back, f"download-back metadata must bind {metadata_field}")
    require(
        "branches/develop" in download_back
        and ".commit.sha" in download_back
        and ".protected" in download_back,
        "download-back must recheck protected develop freshness",
    )
    require(
        "principal=iasuploadaudit" in upload_audit
        and "reserved_uid=61003" in upload_audit
        and '--uid "$reserved_uid"' in upload_audit
        and 'sudo -u "$principal" /usr/bin/env -i' in upload_audit
        and "/usr/bin/sudo -n -u root /usr/bin/true" in upload_audit
        and 'BUNDLETOOL_JAR="$boundary/tools/bundletool.jar"' in upload_audit
        and "ANDROID_IAS_DEBUG_CERTIFICATE_PATH=" in upload_audit
        and '[[ -z "${ANDROID_IAS_DEBUG_KEYSTORE_PATH:-}" ]]' in upload_audit,
        "download-back archive must be audited by a third public-cert-only no-sudo UID",
    )
    require(
        "/var/tmp/fearless-ias-upload-audit-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX" in upload_audit
        and 'sudo chmod 0711 "$boundary"' in upload_audit,
        "upload-audit boundary must be a fresh root-owned traversable enclave",
    )
    for archive_contract in (
        'expected_names = ["evidence.txt", "fearless-wallet-4.2.0-ias-230.aab"]',
        "len(names) != len(set(names))",
        "not stat.S_ISREG(mode)",
        "entry.file_size > maximum",
        "hashlib.sha256(payload).hexdigest() != expected",
        "os.O_CREAT | os.O_EXCL",
        "while view:",
        "--signed-from",
    ):
        require(archive_contract in upload_audit, f"downloaded archive contract missing {archive_contract}")
    require(
        "if: always()" in complete
        and 'AUDIT_OUTCOME: ${{ steps.isolated-upload-audit.outcome }}' in complete
        and 'pkill -KILL -u "$IAS_UPLOAD_AUDIT_UID"' in complete
        and 'groupdel "$IAS_UPLOAD_AUDIT_PRINCIPAL"' in complete
        and '-uid "$IAS_UPLOAD_AUDIT_UID" -depth -delete' in complete
        and '[[ "$AUDIT_OUTCOME" == "success" ]]' in complete
        and 'echo "qualified=true" >>"$GITHUB_OUTPUT"' in complete,
        "qualification may complete only after audit UID termination and success",
    )
    require(
        "branches/develop" in complete
        and ".commit.sha" in complete
        and ".protected" in complete
        and ".digest // empty" in complete,
        "completion must rebind artifact metadata and protected develop",
    )
    require(
        "if: always()" in cleanup
        and "iaspresign iaspostsign iasuploadaudit" in cleanup
        and "fixed_uid=61001" in cleanup
        and "fixed_uid=61002" in cleanup
        and "fixed_uid=61003" in cleanup
        and 'recorded_uid="${IAS_PRESIGN_UID:-}"' in cleanup
        and 'pkill -KILL -u "$fixed_uid"' in cleanup
        and 'groupdel "$principal"' in cleanup
        and '-uid "$fixed_uid" -depth -delete' in cleanup
        and cleanup.count('if getent passwd "$principal"') >= 3
        and cleanup.count('if getent group "$principal"') >= 2
        and '[[ "$managed" == "true" ]]' in cleanup
        and "fearless-ias-presign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.*" in cleanup
        and "fearless-ias-postsign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.*" in cleanup
        and "fearless-ias-upload-audit-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.*" in cleanup
        and 'chown -R "$(id -u):$(id -g)" "$GITHUB_WORKSPACE"' in cleanup,
        "always-cleanup must remove every UID/state, recover partial user/group deletion, and restore checkout ownership",
    )

    require("needs: [validate-candidate, qualify-handoff]" in finalizer, "finalizer must depend on producer and qualifier")
    require(
        'QUALIFIER_RESULT: ${{ needs.qualify-handoff.result }}' in finalizer
        and 'QUALIFICATION_COMPLETE: ${{ needs.qualify-handoff.outputs.qualification_complete }}' in finalizer
        and 'QUALIFIED_ARTIFACT_ID: ${{ needs.qualify-handoff.outputs.pending_artifact_id }}' in finalizer
        and 'QUALIFIED_ARTIFACT_DIGEST: ${{ needs.qualify-handoff.outputs.pending_artifact_digest }}' in finalizer,
        "finalizer must bind exact qualifier result, completion, ID, and digest",
    )
    finalizer_step = step_block(text, "Keep only a fully download-back-qualified current artifact")
    require(finalizer_step is not None and finalizer_step in finalizer, "finalizer enforcement step must exist exactly once")
    if finalizer_step is not None:
        require(pending_name in finalizer_step, "finalizer must select only the exact pending artifact name")
        require(
            '"$QUALIFIER_RESULT" == "success"' in finalizer_step
            and '"$QUALIFICATION_COMPLETE" == "true"' in finalizer_step
            and '"${#matching_ids[@]}" == "1"' in finalizer_step,
            "failure, cancellation, duplicate, or incomplete qualification must enter deletion",
        )
        require(
            ".workflow_run.id" in finalizer_step
            and ".workflow_run.head_sha" in finalizer_step
            and ".commit.sha" in finalizer_step
            and ".protected" in finalizer_step,
            "finalizer must reject stale run, source, and ref metadata",
        )
        require(
            "--request DELETE" in finalizer_step
            and 'actions/artifacts/$artifact_id' in finalizer_step
            and 'remaining="${#matching_ids[@]}"' in finalizer_step
            and 'echo "IAS qualification did not complete; pending artifact deleted." >&2\n            exit 1' in finalizer_step,
            "finalizer must delete and absence-prove every unqualified artifact",
        )
        require(
            'direct_artifact_id="$QUALIFIED_ARTIFACT_ID"' in finalizer_step
            and 'deletion_ids+=("$direct_artifact_id")' in finalizer_step
            and 'actions/artifacts/$direct_artifact_id' in finalizer_step
            and '[[ "$direct_remaining" == "0" ]]' in finalizer_step,
            "unqualified action output ID must be deleted and absence-proved even before list consistency",
        )
        require(
            "list_matching_ids()" in finalizer_step
            and "for page in {1..100}; do" in finalizer_step
            and "artifacts?per_page=100&page=$page" in finalizer_step
            and 'matching_ids+=("${page_ids[@]}")' in finalizer_step
            and finalizer_step.count("list_matching_ids") == 3,
            "finalizer must paginate every artifact-list and absence-proof query",
        )
        require(
            finalizer_step.count("for _ in {1..6}; do") == 2
            and finalizer_step.count("sleep 5") == 2
            and "matching_ids=()" in finalizer_step
            and "remaining=-1" in finalizer_step,
            "finalizer must tolerate artifact-list and deletion consistency delay",
        )

    exact_janitor_gate = (
        "if: ${{ github.event_name == 'schedule' && "
        "github.repository == 'soramitsu/fearless-Android' && "
        "github.ref == 'refs/heads/develop' && "
        "github.ref_protected == true }}"
    )
    require(exact_janitor_gate in janitor, "scheduled janitor must use the exact protected-develop gate")
    require(
        "actions/checkout" not in janitor
        and "uses:" not in janitor
        and "/scripts/" not in janitor
        and "./gradlew" not in janitor
        and janitor.count("      - name:") == 1,
        "scheduled janitor must be a no-checkout trusted inline cleanup job",
    )
    janitor_step = step_block(text, "Retain only exact pending artifacts from successful qualified runs")
    require(janitor_step is not None and janitor_step in janitor, "scheduled janitor enforcement step must exist exactly once")
    if janitor_step is not None:
        require(
            '[[ "$GITHUB_EVENT_NAME" == "schedule" ]]' in janitor_step
            and '[[ "$GITHUB_REF" == "refs/heads/develop" ]]' in janitor_step
            and '[[ "$SCHEDULE_REF_PROTECTED" == "true" ]]' in janitor_step,
            "scheduled janitor must recheck its protected first-party runtime context",
        )
        require(
            "enumerate_pending()" in janitor_step
            and "for page in {1..100}; do" in janitor_step
            and "actions/artifacts?per_page=100&page=$page" in janitor_step
            and janitor_step.count('enumerate_pending "$') == 2,
            "scheduled janitor must fully paginate discovery and absence proof",
        )
        require(
            pending_name in janitor_step
            and '([0-9a-f]{40})-([1-9][0-9]*)-([1-9][0-9]*)$' in janitor_step
            and 'expected_run_id="${BASH_REMATCH[2]}"' in janitor_step
            and 'expected_attempt="${BASH_REMATCH[3]}"' in janitor_step
            and "ambiguous-duplicate-name" in janitor_step,
            "scheduled janitor must anchor name SHA/run/attempt and delete duplicate tuples",
        )
        require(
            '"$run_id" != "$expected_run_id"' in janitor_step
            and '"$run_attempt" != "$expected_attempt"' in janitor_step
            and '"$run_head_sha" != "$expected_sha"' in janitor_step
            and '"$run_event" != "workflow_dispatch"' in janitor_step
            and '"$run_branch" != "develop"' in janitor_step
            and '.github/workflows/android-internal-app-sharing.yml' in janitor_step
            and '"$artifact_run_id" != "$expected_run_id"' in janitor_step
            and '"$artifact_head_sha" != "$expected_sha"' in janitor_step,
            "scheduled janitor must bind artifact and run identity before retention",
        )
        require(
            "completed)" in janitor_step
            and '"$run_conclusion" != "success"' in janitor_step
            and "queued|in_progress|waiting|requested|pending)" in janitor_step
            and "Preserving active pending IAS artifact ID" in janitor_step,
            "scheduled janitor must retain active runs and delete nonsuccessful completed runs",
        )
        require(
            "enumerate_attempt_jobs()" in janitor_step
            and "attempts/$run_attempt/jobs?per_page=100&page=$page" in janitor_step
            and '"$qualifier_total" != "1"' in janitor_step
            and '"$qualifier_success" != "1"' in janitor_step
            and '"$finalizer_total" != "1"' in janitor_step
            and '"$finalizer_success" != "1"' in janitor_step
            and janitor_step.count('.status == "completed" and .conclusion == "success"') == 2,
            "scheduled janitor must retain only attempts whose qualifier and finalizer jobs uniquely succeeded",
        )
        require(
            "--request DELETE" in janitor_step
            and 'actions/artifacts/$artifact_id' in janitor_step
            and '[[ "$delete_http_code" == "204" || "$delete_http_code" == "404" ]]' in janitor_step
            and "--slurpfile deleted" in janitor_step
            and '[[ "$remaining" == "0" ]]' in janitor_step,
            "scheduled janitor must delete exact stale IDs and prove their absence",
        )

    require("IAS_AAB_VERIFIER_TEST_" not in text, "production workflow must not enable verifier test hooks")
    return errors

failures = []
for needle in required_app:
    if needle not in app:
        failures.append(f"missing app contract: {needle}")
for needle in forbidden_app:
    if needle in app:
        failures.append(f"forbidden app contract: {needle}")
for needle in required_lock_configs:
    if needle not in root or needle not in locks:
        failures.append(f"missing strict IAS lock coverage: {needle}")
for needle in required_self_test:
    if needle not in self_test:
        failures.append(f"missing portable IAS self-test contract: {needle}")
if 'internalAppSharing' in ignore:
    failures.append("app/.gitignore still hides a mutable IAS source tree")
if obsolete_helper.exists():
    failures.append("obsolete atomic bridge helper still exists")
if not workflow_path.is_file():
    failures.append("dedicated IAS workflow is missing")
else:
    workflow = workflow_path.read_text(encoding="utf-8")
    for needle in required_workflow:
        if needle not in workflow:
            failures.append(f"missing IAS workflow contract: {needle}")
    for needle in required_verifier:
        if needle not in verifier:
            failures.append(f"missing signer-isolated IAS verifier contract: {needle}")
    signer_unset = verifier.find(
        "unset \\\n"
        "  ANDROID_IAS_DEBUG_KEYSTORE_PATH \\\n"
        "  ANDROID_IAS_DEBUG_CERTIFICATE_PATH \\\n"
        "  ANDROID_IAS_DEBUG_CERT_SHA256 \\\n"
        "  EXPECTED_IAS_DEBUG_CERT_SHA256"
    )
    first_bundletool = verifier.find('validate --bundle "$artifact"')
    if signer_unset < 0 or first_bundletool < 0 or signer_unset >= first_bundletool:
        failures.append(
            "IAS verifier must erase signer environment variables before bundletool runs"
        )
    forbidden_distributor_patterns = [
        (r"(?m)^\s+PLAY_SERVICE_ACCOUNT\s*:", "PLAY_SERVICE_ACCOUNT assignment"),
        (r"(?m)^\s+CI_PLAY_KEY\s*:", "CI_PLAY_KEY assignment"),
        (r"firebaseAppDistribution(?:Upload)?", "Firebase App Distribution task"),
    ]
    for pattern, label in forbidden_distributor_patterns:
        if re.search(pattern, workflow):
            failures.append(f"forbidden distributor in IAS workflow: {label}")
    trust_assertion_count = [0]

    def count_trust_assertion():
        trust_assertion_count[0] += 1

    failures.extend(trust_contract_errors(workflow, count_trust_assertion))

    mutations = []

    def add_step_contract(label, step_name, old, changed, expected):
        for operation, replacement in (("delete", ""), ("change", changed)):
            try:
                mutated = mutate_step(workflow, step_name, old, replacement)
            except ValueError as error:
                failures.append(f"mutation precondition failed ({operation} {label}): {error}")
                continue
            mutations.append((f"{operation} {label}", mutated, expected))

    def add_job_contract(label, job_name, old, changed, expected):
        for operation, replacement in (("delete", ""), ("change", changed)):
            try:
                mutated = mutate_job(workflow, job_name, old, replacement)
            except ValueError as error:
                failures.append(f"mutation precondition failed ({operation} {label}): {error}")
                continue
            mutations.append((f"{operation} {label}", mutated, expected))

    def add_step_change_only(label, step_name, old, changed, expected):
        try:
            mutated = mutate_step(workflow, step_name, old, changed)
        except ValueError as error:
            failures.append(f"mutation precondition failed (change {label}): {error}")
            return
        mutations.append((f"change {label}", mutated, expected))

    def add_workflow_contract(label, old, changed, expected):
        for operation, replacement in (("delete", ""), ("change", changed)):
            try:
                mutated = replace_once(workflow, old, replacement)
            except ValueError as error:
                failures.append(f"mutation precondition failed ({operation} {label}): {error}")
                continue
            mutations.append((f"{operation} {label}", mutated, expected))

    pre_prepare_name = "Prepare root-owned read-only pre-sign boundary"
    pre_verify_name = "Validate downloaded unsigned quarantine independently"
    pre_kill_name = "Terminate pre-sign UID and transfer exact unsigned bytes"
    signer_name = "Create step-local signer, externally sign, export public cert, and erase key"
    post_prepare_name = "Prepare public-certificate-only post-sign boundary"
    post_verify_name = "Run signed-from verifier and adversarial suite with public cert only"
    post_kill_name = "Kill post-sign UID before runner-owned handoff"
    upload_name = "Upload pending exact-byte handoff for immutable audit"
    download_name = "Download back exact immutable uploaded archive by ID and digest"
    audit_name = "Revalidate actual uploaded archive contents under disposable UID"
    complete_name = "Terminate upload-audit UID and complete qualification"
    cleanup_name = "Remove qualifier candidates and local handoff"
    finalizer_name = "Keep only a fully download-back-qualified current artifact"
    janitor_name = "Retain only exact pending artifacts from successful qualified runs"

    for label, old, changed, expected in (
        (
            "first-party repository gate",
            '[[ "${GITHUB_REPOSITORY:-}" == "soramitsu/fearless-Android" ]]',
            '[[ "${GITHUB_REPOSITORY:-}" == "attacker/fearless-Android" ]]',
            "first-party protected develop",
        ),
        (
            "pull-request develop base gate",
            '[[ "$PULL_REQUEST_BASE_REF" == "develop" ]]',
            '[[ "$PULL_REQUEST_BASE_REF" == "attacker" ]]',
            "first-party protected develop",
        ),
        (
            "dispatch develop ref gate",
            '[[ "$DISPATCH_REF" == "refs/heads/develop" ]]',
            '[[ "$DISPATCH_REF" == "refs/heads/attacker" ]]',
            "first-party protected develop",
        ),
        (
            "dispatch protected-ref gate",
            '[[ "$DISPATCH_REF_PROTECTED" == "true" ]]',
            '[[ "$DISPATCH_REF_PROTECTED" == "false" ]]',
            "first-party protected develop",
        ),
    ):
        add_step_contract(
            label,
            "Require exact first-party workflow context",
            old,
            changed,
            expected,
        )
    add_step_contract(
        "PR-only validation marker",
        "Mark pull request run validation-only",
        "        if: github.event_name == 'pull_request'\n",
        "        if: github.event_name == 'workflow_dispatch'\n",
        "non-distributable validation only",
    )
    add_step_contract(
        "PR no-handoff warning",
        "Mark pull request run validation-only",
        "No handoff or upload-artifact step may run",
        "A handoff may run",
        "non-distributable validation only",
    )
    add_workflow_contract(
        "scheduled janitor trigger",
        '  schedule:\n    - cron: "17,47 * * * *"\n',
        '  schedule:\n    - cron: "0 0 31 2 *"\n',
        "dispatch/PR validation plus scheduled cleanup",
    )
    add_workflow_contract(
        "non-canceling dispatch concurrency",
        "  cancel-in-progress: ${{ github.event_name == 'pull_request' }}\n",
        "  cancel-in-progress: true\n",
        "dispatch runs must not be canceled after upload",
    )
    add_workflow_contract(
        "schedule-safe concurrency group",
        "'pending-artifact-janitor'",
        "github.event.pull_request.head.sha",
        "dispatch runs must not be canceled after upload",
    )
    add_job_contract(
        "producer schedule exclusion",
        "validate-candidate",
        "    if: ${{ github.event_name == 'workflow_dispatch' || github.event_name == 'pull_request' }}\n",
        "    if: ${{ always() }}\n",
        "schedule must bypass producer execution",
    )
    for label, line in (
        ("producer artifact ID output", "      producer_artifact_id: ${{ steps.upload-producer-quarantine.outputs.artifact-id }}\n"),
        ("producer artifact digest output", "      producer_artifact_digest: ${{ steps.upload-producer-quarantine.outputs.artifact-digest }}\n"),
    ):
        add_job_contract(
            label,
            "validate-candidate",
            line,
            line.replace("outputs.artifact-", "outputs.attacker-"),
            "export exact quarantine artifact ID and digest",
        )
    producer_gate_line = (
        "        if: ${{ github.event_name == 'workflow_dispatch' && "
        "github.repository == 'soramitsu/fearless-Android' && "
        "github.ref == 'refs/heads/develop' && "
        "github.ref_protected == true }}\n"
    )
    for guarded_name in (
        "Create untrusted producer quarantine",
        "Recheck untrusted producer quarantine",
        "Upload untrusted producer quarantine",
        "Post-upload producer quarantine check",
    ):
        add_step_contract(
            f"exact producer gate on {guarded_name}",
            guarded_name,
            producer_gate_line,
            producer_gate_line.replace("github.ref_protected == true", "github.ref_protected == false"),
            f"producer handoff step {guarded_name!r} must use exact dispatch gate",
        )
    add_step_contract(
        "producer untrusted artifact name",
        "Upload untrusted producer quarantine",
        "fearless-android-ias-UNTRUSTED-producer-",
        "fearless-android-ias-trusted-develop-",
        "one-day explicitly untrusted quarantine",
    )
    add_step_contract(
        "producer one-day retention",
        "Upload untrusted producer quarantine",
        "retention-days: 1",
        "retention-days: 7",
        "one-day explicitly untrusted quarantine",
    )
    for label, line in (
        ("qualifier producer artifact ID input", "      IAS_PRODUCER_ARTIFACT_ID: ${{ needs.validate-candidate.outputs.producer_artifact_id }}\n"),
        ("qualifier producer artifact digest input", "      IAS_PRODUCER_ARTIFACT_DIGEST: ${{ needs.validate-candidate.outputs.producer_artifact_digest }}\n"),
    ):
        add_job_contract(
            label,
            "qualify-handoff",
            line,
            line.replace("outputs.producer_", "outputs.attacker_"),
            "consume exact producer artifact ID and digest",
        )
    add_step_contract(
        "immutable producer artifact ID lookup",
        "Bind exact immutable producer artifact",
        'artifacts/$IAS_PRODUCER_ARTIFACT_ID',
        'artifacts/latest',
        "bind immutable producer artifact metadata",
    )
    add_step_contract(
        "immutable producer workflow head",
        "Bind exact immutable producer artifact",
        "$(jq -r '.workflow_run.head_sha' <<<\"$metadata\")",
        "$(jq -r '.name' <<<\"$metadata\")",
        "bind immutable producer artifact metadata",
    )
    add_step_contract(
        "pinned producer artifact downloader",
        "Download untrusted producer quarantine",
        "actions/download-artifact@d3f86a106a0bac45b974a628896c90dbdf5c8093",
        "actions/download-artifact@v4",
        "download producer quarantine only by immutable ID",
    )
    add_step_contract(
        "producer immutable-ID download",
        "Download untrusted producer quarantine",
        'artifact-ids: ${{ env.IAS_PRODUCER_ARTIFACT_ID }}',
        "name: fearless-android-ias-latest",
        "download producer quarantine only by immutable ID",
    )

    add_job_contract(
        "qualifier actions-read permission",
        "qualify-handoff",
        "      actions: read\n",
        "      actions: write\n",
        "qualifier permissions must remain actions:read",
    )
    add_job_contract(
        "qualifier contents-read permission",
        "qualify-handoff",
        "      contents: read\n",
        "      contents: write\n",
        "qualifier permissions must remain actions:read",
    )
    add_job_contract(
        "qualifier exact protected-develop gate",
        "qualify-handoff",
        "github.ref_protected == true }}\n",
        "github.ref_protected == false }}\n",
        "qualifier must use the exact protected-develop gate",
    )
    for output_label, output_line in (
        ("pending artifact ID output", "      pending_artifact_id: ${{ steps.upload-pending-handoff.outputs.artifact-id }}\n"),
        ("pending artifact digest output", "      pending_artifact_digest: ${{ steps.upload-pending-handoff.outputs.artifact-digest }}\n"),
        ("qualification completion output", "      qualification_complete: ${{ steps.complete-qualification.outputs.qualified }}\n"),
    ):
        add_job_contract(
            output_label,
            "qualify-handoff",
            output_line,
            output_line.replace("steps.", "steps.attacker-"),
            "qualifier must export exact pending artifact",
        )

    add_step_contract(
        "dedicated pre-sign principal",
        pre_prepare_name,
        "          principal=iaspresign\n",
        "          principal=runner\n",
        "dedicated no-login no-sudo principal",
    )
    add_step_contract(
        "non-reused pre-sign numeric UID",
        pre_prepare_name,
        "          reserved_uid=61001\n",
        "          reserved_uid=61002\n",
        "dedicated no-login no-sudo principal",
    )
    add_step_contract(
        "pre-sign system user creation",
        pre_prepare_name,
        "          sudo useradd --system --user-group --no-create-home \\\n",
        "          sudo useradd --create-home \\\n",
        "dedicated no-login no-sudo principal",
    )
    add_step_contract(
        "pre-sign nologin shell",
        pre_prepare_name,
        "            --shell /usr/sbin/nologin \"$principal\"\n",
        "            --shell /bin/bash \"$principal\"\n",
        "dedicated no-login no-sudo principal",
    )
    add_step_contract(
        "pre-sign sudo-group rejection",
        pre_prepare_name,
        "          if id -nG \"$principal\" | tr ' ' '\\n' | grep -Eq '^(sudo|admin)$'; then\n            exit 1\n          fi\n",
        "          id -nG \"$principal\" >/dev/null\n",
        "dedicated no-login no-sudo principal",
    )
    add_step_contract(
        "root-owned read-only input directory",
        pre_prepare_name,
        "          sudo install -d -o root -g root -m 0555 \"$boundary/input\"\n",
        "          sudo install -d -m 0777 \"$boundary/input\"\n",
        "root-owned and read-only",
    )
    add_step_contract(
        "fresh random pre-sign enclave",
        pre_prepare_name,
        "/var/tmp/fearless-ias-presign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX",
        "/var/tmp/fearless-ias-presign-static",
        "root-owned and read-only",
    )
    add_step_contract(
        "root-owned read-only input files",
        pre_prepare_name,
        "            sudo install -o root -g root -m 0444 \"$source_path\" \\\n",
        "            install -m 0600 \"$source_path\" \\\n",
        "root-owned and read-only",
    )
    add_step_contract(
        "root-owned source checkout",
        pre_prepare_name,
        "          sudo chown -R root:root \"$GITHUB_WORKSPACE\"\n",
        "          chown -R \"$(id -u):$(id -g)\" \"$GITHUB_WORKSPACE\"\n",
        "root-owned and read-only",
    )
    add_step_contract(
        "source write-bit absence proof",
        pre_prepare_name,
        "            \\( -type d -o -type f \\) -perm /022 -print -quit | grep -q .; then\n",
        "            -type f -print -quit >/dev/null; then\n",
        "root-owned and read-only",
    )
    add_step_contract(
        "private UID-owned pre-sign state",
        pre_prepare_name,
        "          sudo install -d -o \"$principal_uid\" -g \"$principal_gid\" -m 0700 \\\n",
        "          sudo install -d -o root -g root -m 0755 \\\n",
        "private writable home/tmp/output",
    )
    add_step_contract(
        "private runner command directory",
        pre_prepare_name,
        '          chmod 0700 "$runner_command_dir"\n',
        '          chmod 0777 "$runner_command_dir"\n',
        "runner command files must be private",
    )

    for label, old, changed in (
        ("pre-sign sudo UID switch", '          if ! sudo -u "$IAS_PRESIGN_PRINCIPAL" /usr/bin/env -i \\\n', '          if ! /usr/bin/env -i \\\n'),
        ("pre-sign empty environment", "/usr/bin/env -i \\\n", "/usr/bin/env \\\n"),
        ("pre-sign no-sudo runtime proof", "              if /usr/bin/sudo -n -u root /usr/bin/true 2>/dev/null; then\n", "              if false; then\n"),
    ):
        add_step_contract(label, pre_verify_name, old, changed, "env-i and a no-sudo UID")
    add_step_contract(
        "pre-sign runner-command write rejection",
        pre_verify_name,
        '            if sudo -u "$IAS_PRESIGN_PRINCIPAL" test -w "$runner_command_file"; then\n',
        '            if false; then\n',
        "unable to write runner command files",
    )
    add_step_contract(
        "pre-sign private output capture",
        pre_verify_name,
        '            "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" 2>&1 | \\\n            /usr/bin/head -c 16777217 >"$log"; then\n',
        '            "$GITHUB_RUN_ID" "$GITHUB_RUN_ATTEMPT" 2>&1 | \\\n            /usr/bin/tee "$log"; then\n',
        "output must stay in a private log",
    )
    add_step_contract(
        "pre-sign output size bound",
        pre_verify_name,
        '          [[ "$(wc -c <"$log" | tr -d \'[:space:]\')" -le 16777216 ]]\n',
        '          [[ -s "$log" ]]\n',
        "output must stay in a private log",
    )
    add_step_contract(
        "pre-sign optional-lock suppression",
        pre_verify_name,
        "            GIT_OPTIONAL_LOCKS=0 \\\n",
        "            GIT_OPTIONAL_LOCKS=1 \\\n",
        "unsigned AAB and producer evidence",
    )
    add_step_contract(
        "pre-sign unsigned verifier mode",
        pre_verify_name,
        "                  --unsigned \"$unsigned\" \"$3\" \"$2/output/verified-unsigned.aab\"\n",
        "                  --signed-from \"$unsigned\" \"$3\" \"$2/output/verified-unsigned.aab\"\n",
        "unsigned AAB and producer evidence",
    )
    for evidence_line in (
        '                "signature_state=unsigned"\n',
        '                "source_commit=$3"\n',
        '                "source_tree=$4"\n',
        '                "fearless_utils_tree=$6"\n',
        '                "workflow_ref_protected=true"\n',
        '                "workflow_run_id=$9"\n',
    ):
        add_step_contract(
            f"pre-sign evidence binding {evidence_line.strip()}",
            pre_verify_name,
            evidence_line,
            evidence_line.replace("=", "=attacker-", 1),
            "unsigned AAB and producer evidence",
        )

    add_step_contract(
        "pre-sign always termination",
        pre_kill_name,
        "        if: always()\n",
        "        if: success()\n",
        "every pre-sign UID process",
    )
    for label, old, changed in (
        ("pre-sign process kill", '          sudo pkill -KILL -u "$IAS_PRESIGN_UID" 2>/dev/null || true\n', "          true\n"),
        (
            "pre-sign no-process assertion",
            '          if pgrep -u "$IAS_PRESIGN_UID" >/dev/null 2>&1; then\n            exit 1\n          fi\n',
            "          true\n",
        ),
        ("pre-sign user deletion", '          sudo userdel "$IAS_PRESIGN_PRINCIPAL"\n', "          true\n"),
        ("pre-sign group deletion", '            sudo groupdel "$IAS_PRESIGN_PRINCIPAL"\n', "            true\n"),
        (
            "pre-sign world-writable state sweep",
            '            -uid "$IAS_PRESIGN_UID" -depth -delete\n          sudo userdel "$IAS_PRESIGN_PRINCIPAL"\n',
            '            -uid nobody -depth -delete\n          sudo userdel "$IAS_PRESIGN_PRINCIPAL"\n',
        ),
        (
            "pre-sign state deletion",
            '            "$isolated" "$signing_input/verified-unsigned.aab"\n          sudo rm -rf -- "$IAS_PRESIGN_BOUNDARY"\n',
            '            "$isolated" "$signing_input/verified-unsigned.aab"\n          true\n',
        ),
        ("pre-sign outcome failure close", '          if [[ "$ISOLATED_OUTCOME" != "success" ]]; then\n', "          if false; then\n"),
    ):
        add_step_contract(label, pre_kill_name, old, changed, "every pre-sign UID process")
    add_step_contract(
        "exact verified unsigned digest transfer",
        pre_kill_name,
        '          [[ "$unsigned_sha" == \\\n',
        '          [[ -n "$unsigned_sha" ]] || \\\n',
        "only exact verified unsigned bytes",
    )
    add_step_contract(
        "read-only runner unsigned transfer",
        pre_kill_name,
        '          sudo install -o "$(id -u)" -g "$(id -g)" -m 0400 \\\n',
        '          install -m 0666 \\\n',
        "only exact verified unsigned bytes",
    )
    for label, old, changed in (
        (
            "pre-sign hostile-output symlink rejection",
            '          if sudo test -L "$isolated"; then\n            exit 1\n          fi\n',
            "          if false; then exit 1; fi\n",
        ),
        ("pre-sign hostile-output regular-file check", '          sudo test -f "$isolated"\n', "          true\n"),
        ("pre-sign hostile-output single-link owner check", '            "1:$IAS_PRESIGN_UID" ]]\n', '            "2:$IAS_PRESIGN_UID" ]]\n'),
        ("pre-sign hostile-output size bound", '            "$isolated_size" -le 262144000 ]]\n', '            "$isolated_size" -ge 0 ]]\n'),
    ):
        add_step_contract(
            label,
            pre_kill_name,
            old,
            changed,
            "hostile pre-sign output must be process-quiesced",
        )
    add_step_change_only(
        "pre-sign privileged hostile-path chown",
        pre_kill_name,
        '          unsigned_sha="$(sudo sha256sum "$isolated" | awk \'{print $1}\')"\n',
        '          sudo chown root:root "$isolated"\n          unsigned_sha="$(sudo sha256sum "$isolated" | awk \'{print $1}\')"\n',
        "hostile pre-sign output must be process-quiesced",
    )

    for label, old, changed, expected in (
        ("step-local JKS", "          signer=\"$signing_dir/ephemeral-final-signer.jks\"\n", "          signer=/tmp/shared.jks\n", "generated and used only inside one"),
        ("signer generation", "          keytool -genkeypair -noprompt -storetype JKS \\\n", "          keytool -list \\\n", "generated and used only inside one"),
        ("external jarsigner", "          jarsigner \\\n", "          ./gradlew signer \\\n", "generated and used only inside one"),
        ("signer cleanup trap", "          trap cleanup_signer EXIT\n", "          trap - EXIT\n", "trap-protected step"),
        ("signer cancellation cleanup", "          trap 'cleanup_signer; exit 130' HUP INT TERM\n", "          trap - HUP INT TERM\n", "trap-protected step"),
        (
            "immediate JKS deletion",
            '          rm -f -- "$signer"\n          signer=""\n',
            '          true\n          signer=""\n',
            "erased and absence-proved",
        ),
        ("cleared signer variable", '          signer=""\n', '          signer="$signer"\n', "erased and absence-proved"),
        ("JKS absence proof", "            \\( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' \\) \\\n", "            -name '*.txt' \\\n", "erased and absence-proved"),
        ("public cert export", "          keytool -exportcert -rfc -keystore \"$signer\" -storepass android \\\n", "          keytool -list -keystore \"$signer\" \\\n", "export only its public certificate"),
    ):
        add_step_contract(label, signer_name, old, changed, expected)
    signer_block = step_block(workflow, signer_name)
    pre_kill_block = step_block(workflow, pre_kill_name)
    if signer_block is not None and pre_kill_block is not None:
        without_signer = replace_once(workflow, signer_block, "")
        mutations.append((
            "move signer before pre-sign UID termination",
            replace_once(without_signer, pre_kill_block, signer_block + pre_kill_block),
            "security phases must remain in fail-closed order",
        ))
    add_step_change_only(
        "private key path injected into GITHUB_ENV",
        signer_name,
        "          } >>\"$GITHUB_ENV\"\n",
        "            echo \"ANDROID_IAS_DEBUG_KEYSTORE_PATH=$signer\"\n          } >>\"$GITHUB_ENV\"\n",
        "private-key path must never reach GITHUB_ENV",
        )
    add_step_change_only(
        "checkout script while JKS exists",
        signer_name,
        "          jarsigner \\\n",
        "          /bin/bash \"$GITHUB_WORKSPACE/scripts/verify-android-internal-app-sharing-aab.sh\" --help\n          jarsigner \\\n",
        "no checkout-controlled code may execute while final JKS exists",
    )

    add_step_contract(
        "post-sign dedicated principal",
        post_prepare_name,
        "          principal=iaspostsign\n",
        "          principal=runner\n",
        "public certificate only",
    )
    add_step_contract(
        "fresh random post-sign enclave",
        post_prepare_name,
        "/var/tmp/fearless-ias-postsign-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX",
        "/var/tmp/fearless-ias-postsign-static",
        "fresh root-owned traversable enclave",
    )
    add_step_contract(
        "non-reused post-sign numeric UID",
        post_prepare_name,
        "          reserved_uid=61002\n",
        "          reserved_uid=61001\n",
        "public certificate only",
    )
    add_step_contract(
        "post-sign public certificate copy",
        post_prepare_name,
        '          sudo install -o root -g root -m 0444 "$IAS_PUBLIC_CERTIFICATE" \\\n',
        '          sudo install -o root -g root -m 0444 "$IAS_SIGNED_CANDIDATE" \\\n',
        "public certificate only",
    )
    add_step_contract(
        "post-sign JKS exclusion",
        post_prepare_name,
        "            \\( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' \\) \\\n",
        "            -name '*.txt' \\\n",
        "public certificate only",
    )
    for label, old, changed in (
        ("post-sign sudo UID switch", '          if ! sudo -u "$IAS_POSTSIGN_PRINCIPAL" /usr/bin/env -i \\\n', '          if ! /usr/bin/env -i \\\n'),
        ("post-sign empty environment", "/usr/bin/env -i \\\n", "/usr/bin/env \\\n"),
        ("post-sign public cert environment", '            ANDROID_IAS_DEBUG_CERTIFICATE_PATH="$IAS_POSTSIGN_BOUNDARY/input/final-signer-public-certificate.pem" \\\n', '            ANDROID_IAS_DEBUG_KEYSTORE_PATH="$IAS_SIGNED_CANDIDATE" \\\n'),
        ("post-sign private key absence", '              [[ -z "${ANDROID_IAS_DEBUG_KEYSTORE_PATH:-}" ]]\n', "              true\n"),
        ("post-sign signed-from mode", "                  --signed-from \"$input/verified-unsigned.aab\" \\\n", "                  \"$input/verified-unsigned.aab\" \\\n"),
        ("post-sign adversarial suite", '/bin/bash "$1/scripts/test-android-internal-app-sharing-aab.sh"\n', '/bin/bash "$1/scripts/attacker.sh"\n'),
    ):
        add_step_contract(label, post_verify_name, old, changed, "public certificate")
    add_step_contract(
        "post-sign runner-command write rejection",
        post_verify_name,
        '            if sudo -u "$IAS_POSTSIGN_PRINCIPAL" test -w "$runner_command_file"; then\n',
        '            if false; then\n',
        "unable to write runner command files",
    )
    add_step_contract(
        "post-sign private output capture",
        post_verify_name,
        '            "$IAS_CANDIDATE_SHA" 2>&1 | \\\n            /usr/bin/head -c 16777217 >"$log"; then\n',
        '            "$IAS_CANDIDATE_SHA" 2>&1 | \\\n            /usr/bin/tee "$log"; then\n',
        "output must stay in a private log",
    )
    add_step_contract(
        "post-sign output size bound",
        post_verify_name,
        '          [[ "$(wc -c <"$log" | tr -d \'[:space:]\')" -le 16777216 ]]\n',
        '          [[ -s "$log" ]]\n',
        "output must stay in a private log",
    )
    for label, old, changed in (
        ("post-sign always termination", "        if: always()\n", "        if: success()\n"),
        ("post-sign process kill", '          sudo pkill -KILL -u "$IAS_POSTSIGN_UID" 2>/dev/null || true\n', "          true\n"),
        (
            "post-sign no-process assertion",
            '          if pgrep -u "$IAS_POSTSIGN_UID" >/dev/null 2>&1; then\n            exit 1\n          fi\n',
            "          true\n",
        ),
        ("post-sign user deletion", '          sudo userdel "$IAS_POSTSIGN_PRINCIPAL"\n', "          true\n"),
        ("post-sign group deletion", '            sudo groupdel "$IAS_POSTSIGN_PRINCIPAL"\n', "            true\n"),
        (
            "post-sign world-writable state sweep",
            '            -uid "$IAS_POSTSIGN_UID" -depth -delete\n          sudo userdel "$IAS_POSTSIGN_PRINCIPAL"\n',
            '            -uid nobody -depth -delete\n          sudo userdel "$IAS_POSTSIGN_PRINCIPAL"\n',
        ),
        ("post-sign outcome failure close", '          if [[ "$ISOLATED_OUTCOME" != "success" ]]; then\n', "          if false; then\n"),
    ):
        add_step_contract(label, post_kill_name, old, changed, "post-sign UID must be killed")
    for label, old, changed in (
        (
            "post-sign hostile-output symlink rejection",
            '          if sudo test -L "$isolated"; then\n            exit 1\n          fi\n',
            "          if false; then exit 1; fi\n",
        ),
        ("post-sign hostile-output regular-file check", '          sudo test -f "$isolated"\n', "          true\n"),
        ("post-sign hostile-output single-link owner check", '            "1:$IAS_POSTSIGN_UID" ]]\n', '            "2:$IAS_POSTSIGN_UID" ]]\n'),
        ("post-sign hostile-output size bound", '            "$isolated_size" -le 262144000 ]]\n', '            "$isolated_size" -ge 0 ]]\n'),
    ):
        add_step_contract(
            label,
            post_kill_name,
            old,
            changed,
            "hostile post-sign output must be process-quiesced",
        )
    add_step_change_only(
        "post-sign privileged hostile-path chown",
        post_kill_name,
        '          aab_sha="$(sudo sha256sum "$isolated" | awk \'{print $1}\')"\n',
        '          sudo chown root:root "$isolated"\n          aab_sha="$(sudo sha256sum "$isolated" | awk \'{print $1}\')"\n',
        "hostile post-sign output must be process-quiesced",
    )

    add_step_contract(
        "pending-untrusted artifact name",
        upload_name,
        "fearless-android-ias-PENDING-UNTRUSTED-until-download-back-succeeds-",
        "fearless-android-ias-trusted-develop-",
        "explicitly pending until download-back",
    )
    add_step_contract(
        "commit-pinned pending uploader",
        upload_name,
        "actions/upload-artifact@ea165f8d65b6e75b540449e92b4886f43607fa02",
        "actions/upload-artifact@v4",
        "explicitly pending until download-back",
    )
    for label, old, changed, expected in (
        ("uploaded artifact ID output binding", '          UPLOADED_ARTIFACT_ID: ${{ steps.upload-pending-handoff.outputs.artifact-id }}\n', '          UPLOADED_ARTIFACT_ID: attacker\n', "actual immutable uploaded ZIP"),
        ("uploaded artifact digest output binding", '          UPLOADED_ARTIFACT_DIGEST: ${{ steps.upload-pending-handoff.outputs.artifact-digest }}\n', '          UPLOADED_ARTIFACT_DIGEST: attacker\n', "actual immutable uploaded ZIP"),
        ("uploaded ZIP exact-ID endpoint", 'artifacts/$UPLOADED_ARTIFACT_ID/zip', 'artifacts/latest/zip', "actual immutable uploaded ZIP"),
        ("downloaded ZIP digest equality", '          [[ "$(sha256sum "$archive" | awk \'{print $1}\')" == \\\n', '          [[ -n "$archive" ]] || \\\n', "actual immutable uploaded ZIP"),
        ("downloaded ZIP size bound", '          [[ "$(wc -c <"$archive" | tr -d \'[:space:]\')" -le 262225920 ]]\n', '          [[ -s "$archive" ]]\n', "size-bounded before parsing"),
        ("download-back ID metadata", "$(jq -r '.id' <<<\"$metadata\")", "$(jq -r '.name' <<<\"$metadata\")", "metadata must bind .id"),
        ("download-back workflow-run metadata", "$(jq -r '.workflow_run.id' <<<\"$metadata\")", "$(jq -r '.id' <<<\"$metadata\")", "metadata must bind .workflow_run.id"),
        ("download-back head-SHA metadata", "$(jq -r '.workflow_run.head_sha' <<<\"$metadata\")", "$(jq -r '.name' <<<\"$metadata\")", "metadata must bind .workflow_run.head_sha"),
        ("download-back protected ref", "$(jq -r '.protected' <<<\"$branch\")", "$(jq -r '.name' <<<\"$branch\")", "protected develop freshness"),
    ):
        add_step_contract(label, download_name, old, changed, expected)

    for label, old, changed, expected in (
        ("upload-audit disposable principal", "          principal=iasuploadaudit\n", "          principal=runner\n", "third public-cert-only no-sudo UID"),
        ("upload-audit fresh random enclave", "/var/tmp/fearless-ias-upload-audit-$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT.XXXXXX", "/var/tmp/fearless-ias-upload-audit-static", "fresh root-owned traversable enclave"),
        ("upload-audit non-reused numeric UID", "          reserved_uid=61003\n", "          reserved_uid=61001\n", "third public-cert-only no-sudo UID"),
        ("upload-audit empty environment", '/usr/bin/env -i \\\n', '/usr/bin/env \\\n', "third public-cert-only no-sudo UID"),
        ("upload-audit no-sudo proof", "              if /usr/bin/sudo -n -u root /usr/bin/true 2>/dev/null; then exit 1; fi\n", "              if false; then true; fi\n", "third public-cert-only no-sudo UID"),
        ("upload-audit exact entries", 'expected_names = ["evidence.txt", "fearless-wallet-4.2.0-ias-230.aab"]', 'expected_names = ["fearless-wallet-4.2.0-ias-230.aab"]', "downloaded archive contract missing expected_names"),
        ("upload-audit duplicate names", "len(names) != len(set(names))", "False", "downloaded archive contract missing len(names)"),
        ("upload-audit regular entries", "not stat.S_ISREG(mode)", "False", "downloaded archive contract missing not stat.S_ISREG"),
        ("upload-audit entry size bound", "entry.file_size > maximum", "False", "downloaded archive contract missing entry.file_size"),
        ("upload-audit exact entry digests", "hashlib.sha256(payload).hexdigest() != expected", "False", "downloaded archive contract missing hashlib.sha256"),
        ("upload-audit exclusive extraction", "os.O_CREAT | os.O_EXCL", "os.O_CREAT", "downloaded archive contract missing os.O_CREAT"),
        ("upload-audit signed-from AAB verifier", "                  --signed-from \"$2/input/verified-unsigned.aab\" \\\n", "                  \"$2/input/verified-unsigned.aab\" \\\n", "downloaded archive contract missing --signed-from"),
    ):
        add_step_contract(label, audit_name, old, changed, expected)
    add_step_contract(
        "upload-audit runner-command write rejection",
        audit_name,
        '            if sudo -u "$principal" test -w "$runner_command_file"; then\n',
        '            if false; then\n',
        "unable to write runner command files",
    )
    add_step_contract(
        "upload-audit private output capture",
        audit_name,
        '            "$IAS_CANDIDATE_SHA" 2>&1 | \\\n            /usr/bin/head -c 16777217 >"$audit_log"; then\n',
        '            "$IAS_CANDIDATE_SHA" 2>&1 | \\\n            /usr/bin/tee "$audit_log"; then\n',
        "output must stay in a private log",
    )
    add_step_contract(
        "upload-audit output size bound",
        audit_name,
        '          [[ "$(wc -c <"$audit_log" | tr -d \'[:space:]\')" -le 16777216 ]]\n',
        '          [[ -s "$audit_log" ]]\n',
        "output must stay in a private log",
    )
    for label, old, changed, expected in (
        ("upload-audit always termination", "        if: always()\n", "        if: success()\n", "qualification may complete only after audit UID termination"),
        ("upload-audit process kill", '            sudo pkill -KILL -u "$IAS_UPLOAD_AUDIT_UID" 2>/dev/null || true\n', "            true\n", "qualification may complete only after audit UID termination"),
        ("upload-audit group deletion", '            sudo groupdel "$IAS_UPLOAD_AUDIT_PRINCIPAL" 2>/dev/null || true\n', "            true\n", "qualification may complete only after audit UID termination"),
        ("upload-audit world-writable state sweep", '              -uid "$IAS_UPLOAD_AUDIT_UID" -depth -delete\n', "              -uid nobody -depth -delete\n", "qualification may complete only after audit UID termination"),
        ("upload-audit outcome gate", '          [[ "$AUDIT_OUTCOME" == "success" ]]\n', "          true\n", "qualification may complete only after audit UID termination"),
        ("completion protected branch", "$(jq -r '.protected' <<<\"$branch\")", "$(jq -r '.name' <<<\"$branch\")", "completion must rebind artifact metadata"),
        ("completion artifact digest", "$(jq -r '.digest // empty' <<<\"$metadata\")", "$(jq -r '.name' <<<\"$metadata\")", "completion must rebind artifact metadata"),
        ("qualification completion output", '          echo "qualified=true" >>"$GITHUB_OUTPUT"\n', '          echo "qualified=true" >>"$GITHUB_ENV"\n', "qualification may complete only after audit UID termination"),
    ):
        add_step_contract(label, complete_name, old, changed, expected)

    add_step_contract(
        "always cleanup",
        cleanup_name,
        "        if: always()\n",
        "        if: success()\n",
        "always-cleanup must remove every UID",
    )
    add_step_contract(
        "cleanup all disposable principals",
        cleanup_name,
        "          for principal in iaspresign iaspostsign iasuploadaudit; do\n",
        "          for principal in iaspresign; do\n",
        "always-cleanup must remove every UID",
    )
    add_step_contract(
        "cleanup recorded UID recovery",
        cleanup_name,
        '                recorded_uid="${IAS_PRESIGN_UID:-}"\n',
        '                recorded_uid=""\n',
        "recover partial user/group deletion",
    )
    add_step_contract(
        "cleanup independent orphan-group removal",
        cleanup_name,
        '            if getent group "$principal" >/dev/null; then\n              [[ "$managed" == "true" ]]\n',
        '            if false; then\n              [[ "$managed" == "true" ]]\n',
        "recover partial user/group deletion",
    )
    add_step_contract(
        "cleanup fixed-UID sweep",
        cleanup_name,
        '                -uid "$fixed_uid" -depth -delete\n',
        '                -uid "$principal_uid" -depth -delete\n',
        "recover partial user/group deletion",
    )

    add_job_contract(
        "finalizer always gate",
        "finalize-handoff",
        "    if: ${{ always() && github.event_name == 'workflow_dispatch' && ",
        "    if: ${{ success() && github.event_name == 'workflow_dispatch' && ",
        "finalizer must run always",
    )
    add_job_contract(
        "finalizer actions-write permission",
        "finalize-handoff",
        "      actions: write\n",
        "      actions: read\n",
        "fresh finalizer must receive narrowly scoped actions:write",
    )
    for label, old, changed, expected in (
        ("failure/cancel qualifier result gate", '"$QUALIFIER_RESULT" == "success"', '"$QUALIFIER_RESULT" != ""', "failure, cancellation, duplicate"),
        ("qualification-complete gate", '"$QUALIFICATION_COMPLETE" == "true"', '"$QUALIFICATION_COMPLETE" != ""', "failure, cancellation, duplicate"),
        ("unique artifact gate", '"${#matching_ids[@]}" == "1"', '"${#matching_ids[@]}" -ge "1"', "failure, cancellation, duplicate"),
        ("finalizer pending name", "fearless-android-ias-PENDING-UNTRUSTED-until-download-back-succeeds-", "fearless-android-ias-trusted-develop-", "exact pending artifact name"),
        ("finalizer workflow run binding", "$(jq -r '.workflow_run.id' <<<\"$metadata\")", "$(jq -r '.id' <<<\"$metadata\")", "reject stale run"),
        ("finalizer head SHA binding", "$(jq -r '.workflow_run.head_sha' <<<\"$metadata\")", "$(jq -r '.name' <<<\"$metadata\")", "reject stale run"),
        ("finalizer current branch SHA", "$(jq -r '.commit.sha' <<<\"$branch\")", "$(jq -r '.name' <<<\"$branch\")", "reject stale run"),
        ("finalizer protected branch", "$(jq -r '.protected' <<<\"$branch\")", "$(jq -r '.name' <<<\"$branch\")", "reject stale run"),
        ("artifact DELETE request", "                  --request DELETE --output /dev/null --write-out '%{http_code}' \\\n", "                  --output /dev/null --write-out '%{http_code}' \\\n", "delete and absence-prove"),
        ("exact artifact DELETE endpoint", '"$api/actions/artifacts/$artifact_id"\n', '"$api/actions/artifacts/latest"\n', "delete and absence-prove"),
        ("post-delete absence proof", '              remaining="${#matching_ids[@]}"\n', '              remaining=0\n', "delete and absence-prove"),
        ("failed finalizer outcome", '            echo "IAS qualification did not complete; pending artifact deleted." >&2\n            exit 1\n', '            echo "IAS qualification did not complete; pending artifact deleted." >&2\n            exit 0\n', "delete and absence-prove"),
    ):
        add_step_contract(label, finalizer_name, old, changed, expected)
    for label, old, changed in (
        ("direct output artifact ID capture", '              direct_artifact_id="$QUALIFIED_ARTIFACT_ID"\n', '              direct_artifact_id=""\n'),
        ("direct output artifact deletion", '              deletion_ids+=("$direct_artifact_id")\n', "              true\n"),
        ("direct output artifact absence query", '                    "${headers[@]}" "$api/actions/artifacts/$direct_artifact_id"\n', '                    "${headers[@]}" "$api/actions/artifacts/latest"\n'),
        ("direct output artifact absence proof", '            [[ "$direct_remaining" == "0" ]]\n', "            true\n"),
    ):
        add_step_contract(
            label,
            finalizer_name,
            old,
            changed,
            "unqualified action output ID must be deleted",
        )
    add_step_contract(
        "artifact-list consistency retry",
        finalizer_name,
        "          matching_ids=()\n          for _ in {1..6}; do\n",
        "          matching_ids=()\n          for _ in {1..1}; do\n",
        "tolerate artifact-list and deletion consistency delay",
    )
    add_step_contract(
        "post-delete consistency retry",
        finalizer_name,
        "            remaining=-1\n            direct_remaining=0\n            [[ -z \"$direct_artifact_id\" ]] || direct_remaining=1\n            for _ in {1..6}; do\n",
        "            remaining=-1\n            direct_remaining=0\n            [[ -z \"$direct_artifact_id\" ]] || direct_remaining=1\n            for _ in {1..1}; do\n",
        "tolerate artifact-list and deletion consistency delay",
    )
    add_step_contract(
        "artifact-list pagination bound",
        finalizer_name,
        "            for page in {1..100}; do\n",
        "            for page in {1..1}; do\n",
        "paginate every artifact-list and absence-proof query",
    )
    add_step_contract(
        "artifact-list page query",
        finalizer_name,
        "artifacts?per_page=100&page=$page",
        "artifacts?per_page=100",
        "paginate every artifact-list and absence-proof query",
    )
    add_step_contract(
        "artifact-list page accumulation",
        finalizer_name,
        '              matching_ids+=("${page_ids[@]}")\n',
        '              matching_ids=("${page_ids[@]}")\n',
        "paginate every artifact-list and absence-proof query",
    )

    add_job_contract(
        "scheduled janitor protected-develop gate",
        "sweep-stale-pending-handoffs",
        "    if: ${{ github.event_name == 'schedule' && github.repository == 'soramitsu/fearless-Android' && github.ref == 'refs/heads/develop' && github.ref_protected == true }}\n",
        "    if: ${{ always() }}\n",
        "scheduled janitor must use the exact protected-develop gate",
    )
    add_job_contract(
        "scheduled janitor actions-write permission",
        "sweep-stale-pending-handoffs",
        "      actions: write\n",
        "      actions: read\n",
        "only artifact cleanup jobs may receive actions:write",
    )
    add_step_change_only(
        "scheduled janitor checkout injection",
        janitor_name,
        "          set -euo pipefail\n",
        "          uses: actions/checkout@v4\n          set -euo pipefail\n",
        "no-checkout trusted inline cleanup job",
    )
    for label, old, changed, expected in (
        ("janitor schedule runtime gate", '          [[ "$GITHUB_EVENT_NAME" == "schedule" ]]\n', '          [[ "$GITHUB_EVENT_NAME" == "push" ]]\n', "recheck its protected first-party runtime context"),
        ("janitor protected runtime gate", '          [[ "$SCHEDULE_REF_PROTECTED" == "true" ]]\n', '          [[ "$SCHEDULE_REF_PROTECTED" == "false" ]]\n', "recheck its protected first-party runtime context"),
        ("janitor discovery pagination", "            for page in {1..100}; do\n", "            for page in {1..1}; do\n", "fully paginate discovery and absence proof"),
        ("janitor discovery page query", "actions/artifacts?per_page=100&page=$page", "actions/artifacts?per_page=100", "fully paginate discovery and absence proof"),
        ("janitor anchored SHA/run/attempt name", '([0-9a-f]{40})-([1-9][0-9]*)-([1-9][0-9]*)$ ', '([0-9a-f]+)-([0-9]+)-([0-9]+) ', "anchor name SHA/run/attempt"),
        ("janitor attempt capture", '              expected_attempt="${BASH_REMATCH[3]}"\n', '              expected_attempt=1\n', "anchor name SHA/run/attempt"),
        ("janitor duplicate rejection", '              reason=ambiguous-duplicate-name\n', "              true\n", "delete duplicate tuples"),
        ("janitor run ID binding", '              if [[ "$run_id" != "$expected_run_id" || \\\n', '              if [[ -z "$run_id" || \\\n', "bind artifact and run identity"),
        ("janitor run attempt binding", '                "$run_attempt" != "$expected_attempt" || \\\n', '                -z "$run_attempt" || \\\n', "bind artifact and run identity"),
        ("janitor run head binding", '                "$run_head_sha" != "$expected_sha" || \\\n', '                -z "$run_head_sha" || \\\n', "bind artifact and run identity"),
        ("janitor dispatch binding", '                "$run_event" != "workflow_dispatch" || \\\n', '                -z "$run_event" || \\\n', "bind artifact and run identity"),
        ("janitor artifact run binding", '              if [[ "$artifact_run_id" != "$expected_run_id" || \\\n', '              if [[ -z "$artifact_run_id" || \\\n', "bind artifact and run identity"),
        ("janitor completed-success gate", '                  if [[ "$run_conclusion" != "success" ]]; then\n', '                  if [[ -n "$run_conclusion" ]]; then\n', "delete nonsuccessful completed runs"),
        ("janitor active-run preservation", '                    echo "Preserving active pending IAS artifact ID $artifact_id."\n', '                    reason=delete-active-run\n', "retain active runs"),
        ("janitor exact-attempt jobs endpoint", "attempts/$run_attempt/jobs?per_page=100&page=$page", "jobs?per_page=100&page=$page", "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor qualifier singleton", '"$qualifier_total" != "1"', '"$qualifier_total" -lt "1"', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor qualifier success", '"$qualifier_success" != "1"', '"$qualifier_success" -lt "1"', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor finalizer singleton", '"$finalizer_total" != "1"', '"$finalizer_total" -lt "1"', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor finalizer success", '"$finalizer_success" != "1"', '"$finalizer_success" -lt "1"', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor qualifier completed-success predicate", '                        .name == "Qualify IAS handoff across disposable UID boundaries" and\n                        .status == "completed" and .conclusion == "success"\n', '                        .name == "Qualify IAS handoff across disposable UID boundaries"\n', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor finalizer completed-success predicate", '                        .name == "Delete every unqualified or stale pending IAS artifact" and\n                        .status == "completed" and .conclusion == "success"\n', '                        .name == "Delete every unqualified or stale pending IAS artifact"\n', "qualifier and finalizer jobs uniquely succeeded"),
        ("janitor exact-ID deletion", '                  "$api/actions/artifacts/$artifact_id"\n', '                  "$api/actions/artifacts/latest"\n', "delete exact stale IDs"),
        ("janitor deletion success code", '              [[ "$delete_http_code" == "204" || "$delete_http_code" == "404" ]]\n', "              true\n", "delete exact stale IDs"),
        ("janitor deleted-ID absence proof", '            [[ "$remaining" == "0" ]]\n', "            true\n", "prove their absence"),
    ):
        add_step_contract(label, janitor_name, old, changed, expected)

    for label, mutated_workflow, expected_error in mutations:
        mutation_errors = trust_contract_errors(mutated_workflow)
        if not any(expected_error in error for error in mutation_errors):
            failures.append(
                f"workflow mutation was not rejected ({label}): "
                f"expected {expected_error!r}, got {mutation_errors!r}"
            )
    if failures:
        print("\n".join(failures), file=sys.stderr)
        raise SystemExit(1)
    base_count = (
        len(required_app)
        + len(forbidden_app)
        + len(required_lock_configs)
        + len(required_workflow)
        + len(required_verifier)
        + len(required_self_test)
        + 8
    )
    print(base_count + trust_assertion_count[0] + len(mutations))
    raise SystemExit(0)
PY
  cat "$temporary_dir/static-contract-errors.log" >&2
  fail "IAS static contract failed."
fi
static_count="$(<"$static_count_file")"
[[ "$static_count" == "621" ]] ||
  fail "expected 621 static adversarial assertions; got $static_count."

if [[ "${IAS_GRADLE_CONTRACT_ONLY:-false}" == "true" ]]; then
  assert_source_unchanged "$source_before" "static IAS contract"
  echo "[android-ias-gradle-test] 1 positive + $static_count static adversarial assertions passed"
  exit 0
fi

command -v keytool >/dev/null 2>&1 ||
  fail "required behavioral-test command is unavailable: keytool"

positive_count=1

tasks_log="$temporary_dir/tasks.log"
run_gradle_with_env -- :app:tasks --all >"$tasks_log" 2>&1 ||
  fail "could not enumerate app tasks."
if grep -Eq '^(assemble|bundle|install).*InternalAppSharing([[:space:]]|$)' "$tasks_log"; then
  fail "the test-only IAS variant leaked into a normal task inventory."
fi
grep -Eq '^verifyInternalAppSharingConfiguration([[:space:]]|$)' "$tasks_log" ||
  fail "IAS configuration verifier task is missing."
if grep -Eq '^(publish|promote).*InternalAppSharing([[:space:]]|$)' "$tasks_log"; then
  fail "a publisher task exists for Internal App Sharing."
fi
positive_count=$((positive_count + 1))

for normal_request in 'clean --dry-run' ':app:help' ':app:installDebug --dry-run'; do
  read -r -a normal_args <<<"$normal_request"
  normal_log="$temporary_dir/normal-$positive_count.log"
  run_gradle_with_env -- "${normal_args[@]}" >"$normal_log" 2>&1 || {
    sed -n '1,160p' "$normal_log" >&2
    fail "normal request failed after IAS variant isolation: $normal_request"
  }
  ! grep -Fq 'Internal App Sharing' "$normal_log" ||
    fail "normal request entered IAS controls: $normal_request"
  positive_count=$((positive_count + 1))
done

aggregate_log="$temporary_dir/normal-aggregate.log"
run_gradle_with_env -- clean build --dry-run >"$aggregate_log" 2>&1 || true
if grep -Eq 'Internal App Sharing|internalAppSharing' "$aggregate_log"; then
  sed -n '1,160p' "$aggregate_log" >&2
  fail "normal aggregate build entered the isolated IAS variant."
fi
positive_count=$((positive_count + 1))

success_log="$temporary_dir/verify-success.log"
run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration >"$success_log" 2>&1 || {
  sed -n '1,180p' "$success_log" >&2
  fail "zero-copy IAS verification failed."
}
grep -Fq 'IAS configuration verified with zero-copy public Firebase input' "$success_log" ||
  fail "IAS verifier did not report zero-copy success."
assert_source_unchanged "$source_before" "successful IAS verification"
positive_count=$((positive_count + 1))

expect_failure \
  "malformed forced-failure property" \
  "iasVerificationForceFailure must be unset or exactly true." \
  run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration \
    -PiasVerificationForceFailure=TRUE
expect_failure \
  "forced verifier failure" \
  "Forced IAS verification failure after direct-input validation." \
  run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration \
    -PiasVerificationForceFailure=true
expect_failure \
  "excluded IAS dependency verification" \
  "Internal App Sharing forbids excluded Gradle tasks." \
  run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration \
    -x :app:verifyInternalAppSharingDependencyLocks

for request in \
  'bundleInternalAppSharing' \
  ':app:assembleInternalAppSharing' \
  ':app:bundleInternalAppSharing :app:tasks' \
  ':app:publishInternalAppSharing' \
  ':app:firebaseAppDistributionUploadInternalAppSharing'; do
  read -r -a request_args <<<"$request"
  expect_failure \
    "non-exact IAS request $request" \
    "Internal App Sharing permits only an exact" \
    run_gradle_with_env -- "${request_args[@]}"
done

expect_failure \
  "ambiguous abbreviated IAS request :app:bIAS" \
  "task 'bIAS' is ambiguous in project ':app'" \
  run_gradle_with_env -- :app:bIAS
expect_failure \
  "unmatched abbreviated IAS request :app:bundleIAppSharing" \
  "task 'bundleIAppSharing' not found in project ':app'" \
  run_gradle_with_env -- :app:bundleIAppSharing

property_attack_init="$temporary_dir/property-attack.init.gradle"
cat >"$property_attack_init" <<'GRADLE'
gradle.afterProject { candidate, state ->
    if (candidate.path == ":app") {
        candidate.gradle.taskGraph.whenReady {
            candidate.tasks.matching { task ->
                task.name == "processInternalAppSharingGoogleServices"
            }.configureEach { task ->
                task.googleServicesJsonFiles.set(
                    [new File(candidate.projectDir, "attacker.json")]
                )
            }
        }
    }
}
GRADLE
expect_failure \
  "finalized Google Services task input injection" \
  "is final and cannot be changed any further" \
  run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration \
    --init-script "$property_attack_init"

expect_failure \
  "unsigned IAS request" \
  "Internal App Sharing forbids ANDROID_UNSIGNED_RELEASE_BUILD." \
  run_gradle_with_env CI=true ANDROID_UNSIGNED_RELEASE_BUILD=true -- \
    :app:verifyInternalAppSharingConfiguration
expect_failure \
  "injected signer IAS request" \
  "Internal App Sharing forbids injected Android signing properties." \
  run_gradle_with_env -- :app:verifyInternalAppSharingConfiguration \
    -Pandroid.injected.signing.store.file="$temporary_dir/attacker.jks"

for release_variable in \
  CI_KEYSTORE_PATH CI_KEYSTORE_PASS CI_KEYSTORE_KEY_ALIAS CI_KEYSTORE_KEY_PASS; do
  expect_failure \
    "release signing input $release_variable" \
    "Internal App Sharing forbids every release keystore and password input." \
    run_gradle_with_env "$release_variable=attacker" -- \
      :app:verifyInternalAppSharingConfiguration
done

for forbidden_variable in \
  CI_PLAY_KEY \
  GIT_INDEX_FILE \
  FIREBASE_TOKEN \
  GOOGLE_APPLICATION_CREDENTIALS \
  CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE; do
  expect_failure \
    "forbidden production input $forbidden_variable" \
    "Internal App Sharing forbids final-signer, production-distribution, or Firebase inputs" \
    run_gradle_with_env "$forbidden_variable=attacker" -- \
      :app:verifyInternalAppSharingConfiguration
done

firebase_clone="$temporary_dir/firebase-adversarial-clone"
git clone --quiet --no-hardlinks "$ROOT_DIR" "$firebase_clone"
clone_firebase="$firebase_clone/app/src/release/google-services.json"
firebase_backup="$temporary_dir/public-google-services.json"
cp "$clone_firebase" "$firebase_backup"

restore_clone_firebase() {
  rm -f -- "$clone_firebase"
  cp "$firebase_backup" "$clone_firebase"
}

run_clone_gradle() {
  env \
    -u ANDROID_UNSIGNED_RELEASE_BUILD \
    -u CI_KEYSTORE_PATH \
    -u CI_KEYSTORE_PASS \
    -u CI_KEYSTORE_KEY_ALIAS \
    -u CI_KEYSTORE_KEY_PASS \
    -u ANDROID_RELEASE_KEYSTORE_B64 \
    -u PLAY_SERVICE_ACCOUNT_JSON_B64 \
    -u PLAY_SERVICE_ACCOUNT \
    -u CI_PLAY_KEY \
    -u FIREBASE_TOKEN \
    -u GOOGLE_APPLICATION_CREDENTIALS \
    -u CLOUDSDK_AUTH_CREDENTIAL_FILE_OVERRIDE \
    -u GIT_DIR \
    -u GIT_WORK_TREE \
    -u GIT_INDEX_FILE \
    CI=false \
    SKIP_AUTO_VERSION_BUMP=true \
    FORCE_LOCAL_UTILS=true \
    USE_REMOTE_UTILS=false \
    FEARLESS_UTILS_LIBRARY_ONLY=true \
    FEARLESS_UTILS_PATH="${FEARLESS_UTILS_PATH:-$ROOT_DIR/../fearless-utils-Android}" \
    "$firebase_clone/gradlew" -p "$firebase_clone" \
      :app:verifyInternalAppSharingConfiguration \
      --no-daemon --console=plain
}

rm -f "$clone_firebase"
ln -s "$firebase_backup" "$clone_firebase"
expect_failure \
  "symlink public Firebase input" \
  "Release Firebase configuration must not traverse a symlink." \
  run_clone_gradle

restore_clone_firebase
ln "$clone_firebase" "$temporary_dir/public-google-services-hardlink.json"
expect_failure \
  "hard-linked public Firebase input" \
  "Release Firebase configuration must have exactly one filesystem link." \
  run_clone_gradle
rm -f "$temporary_dir/public-google-services-hardlink.json"

restore_clone_firebase
printf '\n' >>"$clone_firebase"
expect_failure \
  "checksum-mutated public Firebase input" \
  "IAS requires the exact checksum-pinned reviewed public Firebase file." \
  run_clone_gradle

restore_clone_firebase
printf '{ malformed json\n' >"$clone_firebase"
expect_failure \
  "malformed public Firebase JSON" \
  "IAS requires the exact checksum-pinned reviewed public Firebase file." \
  run_clone_gradle

restore_clone_firebase
python3 - "$clone_firebase" <<'PY'
import json
import pathlib
import sys
path = pathlib.Path(sys.argv[1])
data = json.loads(path.read_text(encoding="utf-8"))
data["client"][0]["api_key"][0]["current_key"] = "attacker-secret"
path.write_text(json.dumps(data, separators=(",", ":")) + "\n", encoding="utf-8")
PY
expect_failure \
  "secret-bearing public Firebase JSON" \
  "IAS requires the exact checksum-pinned reviewed public Firebase file." \
  run_clone_gradle
restore_clone_firebase

head_commit="$(git -C "$ROOT_DIR" rev-parse HEAD)"
utils_commit="${FEARLESS_UTILS_COMMIT:-}"
utils_tree="${FEARLESS_UTILS_EFFECTIVE_TREE:-}"
[[ "$utils_commit" =~ ^[0-9a-f]{40}$ && "$utils_tree" =~ ^[0-9a-f]{40}$ ]] ||
  fail "full IAS tests require exact FEARLESS_UTILS_COMMIT and FEARLESS_UTILS_EFFECTIVE_TREE."

keystore="$temporary_dir/ias-run.jks"
keytool -genkeypair -noprompt -storetype JKS \
  -keystore "$keystore" -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 2 \
  -dname 'CN=Android Debug,O=Android,C=US' >/dev/null 2>&1
chmod 600 "$keystore"

bundle_common=(
  CI=true
  RELEASE_COMMIT="$head_commit"
  FEARLESS_UTILS_COMMIT="$utils_commit"
  FEARLESS_UTILS_EFFECTIVE_TREE="$utils_tree"
)
bundle_args=(
  :app:bundleInternalAppSharing
  --no-build-cache
  --rerun-tasks
  --dry-run
)

unsigned_bundle_log="$temporary_dir/unsigned-bundle-dry-run.log"
run_gradle_with_env "${bundle_common[@]}" -- "${bundle_args[@]}" \
  >"$unsigned_bundle_log" 2>&1 || {
  sed -n '1,180p' "$unsigned_bundle_log" >&2
  fail "exact unsigned IAS bundle request failed during isolated dry run."
}
grep -Fq ':app:bundleInternalAppSharing' "$unsigned_bundle_log" ||
  fail "exact unsigned IAS dry run omitted its bundle task."
positive_count=$((positive_count + 1))

expect_failure \
  "IAS CI unset" \
  "Internal App Sharing bundling requires CI to be exactly lowercase true." \
  run_gradle_with_env __UNSET_CI__ \
    "${bundle_common[@]:1}" -- "${bundle_args[@]}"
expect_failure \
  "IAS CI false" \
  "Internal App Sharing bundling requires CI to be exactly lowercase true." \
  run_gradle_with_env "${bundle_common[@]}" CI=false -- \
    "${bundle_args[@]}"
expect_failure \
  "IAS CI uppercase" \
  "Internal App Sharing bundling requires CI to be exactly lowercase true." \
  run_gradle_with_env "${bundle_common[@]}" CI=TRUE -- \
    "${bundle_args[@]}"
expect_failure \
  "IAS missing no-cache/rerun flags" \
  "Internal App Sharing bundling requires --no-build-cache and --rerun-tasks." \
  run_gradle_with_env "${bundle_common[@]}" -- \
    :app:bundleInternalAppSharing --dry-run
expect_failure \
  "IAS missing source commit" \
  "RELEASE_COMMIT is required for Internal App Sharing bundling." \
  run_gradle_with_env \
    CI=true FEARLESS_UTILS_COMMIT="$utils_commit" \
    FEARLESS_UTILS_EFFECTIVE_TREE="$utils_tree" -- \
    "${bundle_args[@]}"
expect_failure \
  "IAS malformed source commit" \
  "RELEASE_COMMIT must be an exact lowercase 40-character Git commit." \
  run_gradle_with_env "${bundle_common[@]}" \
    RELEASE_COMMIT=ABC -- "${bundle_args[@]}"
expect_failure \
  "IAS wrong source commit" \
  "Internal App Sharing RELEASE_COMMIT must equal the checked-out HEAD." \
  run_gradle_with_env "${bundle_common[@]}" \
    RELEASE_COMMIT=0000000000000000000000000000000000000000 -- "${bundle_args[@]}"

for signer_variable in \
  ANDROID_IAS_DEBUG_KEYSTORE_PATH \
  ANDROID_IAS_DEBUG_CERTIFICATE_PATH \
  ANDROID_IAS_DEBUG_CERT_SHA256 \
  EXPECTED_IAS_DEBUG_CERT_SHA256; do
  expect_failure \
    "final signer exposure to Gradle through $signer_variable" \
    "Internal App Sharing forbids final-signer, production-distribution, or Firebase inputs" \
    run_gradle_with_env "${bundle_common[@]}" "$signer_variable=attacker" -- \
      "${bundle_args[@]}"
done

expect_failure \
  "production release signed with IAS key" \
  "The release certificate does not match the registered Google Play upload certificate." \
  run_gradle_with_env \
    CI=true RELEASE_COMMIT="$head_commit" \
    CI_KEYSTORE_PATH="$keystore" CI_KEYSTORE_PASS=android \
    CI_KEYSTORE_KEY_ALIAS=androiddebugkey CI_KEYSTORE_KEY_PASS=android -- \
    :app:bundleRelease --no-build-cache --rerun-tasks --dry-run

release_outputs_after="$temporary_dir/release-outputs-after"
snapshot_release_outputs "$release_outputs_after"
cmp -s "$release_outputs_before" "$release_outputs_after" ||
  fail "IAS guard tests changed production release outputs."
assert_source_unchanged "$source_before" "complete IAS guard suite"

[[ "$positive_count" == "8" ]] ||
  fail "expected 8 positive IAS Gradle cases; got $positive_count."
[[ "$negative_count" == "39" ]] ||
  fail "expected 39 behavioral negative IAS Gradle cases; got $negative_count."

echo \
  "[android-ias-gradle-test] $positive_count positive + $negative_count behavioral negative + $static_count static adversarial assertions passed"
