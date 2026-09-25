#!/usr/bin/env bash
set -euo pipefail

MAX_BUILD_LOG_BYTES=134217728

fail() {
  echo "[android-release-build-log][error] $*" >&2
  exit 1
}

usage() {
  echo "Usage: scripts/verify-android-release-build-log.sh <bundle-release.log>" >&2
}

[[ "$#" -eq 1 ]] || {
  usage
  exit 2
}

build_log="$1"
[[ "$build_log" != *$'\n'* && "$build_log" != *$'\r'* ]] ||
  fail "Build-log path is malformed."
[[ -f "$build_log" && ! -L "$build_log" && -s "$build_log" ]] ||
  fail "Build log must be a non-empty regular, non-symlink file."
build_log_bytes="$(wc -c <"$build_log" | tr -d '[:space:]')"
[[ "$build_log_bytes" =~ ^[1-9][0-9]*$ ]] ||
  fail "Build-log size is malformed."
(( build_log_bytes <= MAX_BUILD_LOG_BYTES )) ||
  fail "Build log exceeds its maximum size."
command -v python3 >/dev/null 2>&1 || fail "python3 is required."

python3 - "$build_log" <<'PY'
import hashlib
import os
import re
import stat
import sys

path = sys.argv[1]


def fail(message):
    print(f"[android-release-build-log][error] {message}", file=sys.stderr)
    raise SystemExit(1)


flags = os.O_RDONLY
if hasattr(os, "O_NOFOLLOW"):
    flags |= os.O_NOFOLLOW
try:
    descriptor = os.open(path, flags)
except OSError as error:
    fail(f"Unable to read build log: {error}.")
try:
    before = os.fstat(descriptor)
    if not stat.S_ISREG(before.st_mode) or before.st_size <= 0:
        fail("Build log must remain a non-empty regular file.")
    if before.st_size > 134217728:
        fail("Build log exceeds its maximum size.")
    with os.fdopen(descriptor, "rb", closefd=False) as source:
        payload = source.read(134217729)
    after = os.fstat(descriptor)
finally:
    os.close(descriptor)
if len(payload) > 134217728:
    fail("Build log exceeds its maximum size.")
if (
    len(payload) != before.st_size
    or before.st_dev != after.st_dev
    or before.st_ino != after.st_ino
    or before.st_size != after.st_size
    or before.st_mtime_ns != after.st_mtime_ns
):
    fail("Build log changed while its verification snapshot was captured.")
if b"\x00" in payload:
    fail("Build log contains a NUL byte.")
try:
    text = payload.decode("utf-8")
except UnicodeDecodeError as error:
    fail(f"Build log is not valid UTF-8: {error}.")

# Remove complete terminal color/title sequences before evaluating diagnostics;
# this prevents ANSI escapes from hiding a forbidden warning phrase.
ansi_pattern = re.compile(
    r"\x1b(?:"
    r"\[[0-?]*[ -/]*[@-~]"
    r"|\][^\x07\x1b]*(?:\x07|\x1b\\)"
    r")"
)
normalized = ansi_pattern.sub("", text).replace("\r", "\n")
if "\x1b" in normalized:
    fail("Build log contains an incomplete terminal escape sequence.")

kotlin_metadata_warning = re.compile(
    r"an\s+error\s+occurred\s+when\s+parsing\s+kotlin\s+metadata",
    flags=re.IGNORECASE,
)
if kotlin_metadata_warning.search(normalized):
    fail("R8 Kotlin metadata parse warning detected.")
missing_service_warning = re.compile(
    r"unexpected\s+reference\s+to\s+missing\s+service\s+class",
    flags=re.IGNORECASE,
)
if missing_service_warning.search(normalized):
    fail("R8 missing ServiceLoader class warning detected.")
if re.search(r"(?m)^\s*BUILD FAILED(?:\s|$)", normalized):
    fail("Gradle reported a failed production build.")
if not re.search(r"(?m)^\s*BUILD SUCCESSFUL(?:\s|$)", normalized):
    fail("Build log does not contain a Gradle success marker.")

r8_task_pattern = re.compile(
    r"^\s*>\s*Task\s+:app:minifyReleaseWithR8"
    r"(?:\s+(FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE))?\s*$",
    flags=re.IGNORECASE,
)
r8_task_outcomes = [
    match.group(1).upper() if match.group(1) else None
    for line in normalized.splitlines()
    if (match := r8_task_pattern.fullmatch(line))
]
if not r8_task_outcomes:
    fail("Build log does not prove execution of :app:minifyReleaseWithR8.")
nonexecuted_outcomes = sorted(
    outcome for outcome in r8_task_outcomes if outcome is not None
)
if nonexecuted_outcomes:
    fail(
        "The release R8 task was cached, up-to-date, skipped, or had no source: "
        + ", ".join(nonexecuted_outcomes)
        + "."
    )
if len(r8_task_outcomes) != 1:
    fail("Build log must contain exactly one executed release R8 task marker.")

bundle_task_pattern = re.compile(
    r"^\s*>\s*Task\s+:app:bundleRelease"
    r"(?:\s+(FROM-CACHE|UP-TO-DATE|SKIPPED|NO-SOURCE))?\s*$",
    flags=re.IGNORECASE,
)
bundle_task_outcomes = [
    match.group(1).upper() if match.group(1) else None
    for line in normalized.splitlines()
    if (match := bundle_task_pattern.fullmatch(line))
]
if len(bundle_task_outcomes) != 1 or bundle_task_outcomes[0] is not None:
    fail("Build log does not prove one executed :app:bundleRelease task.")

print(
    "[android-release-build-log] release R8 task completed without forbidden "
    "Kotlin metadata or ServiceLoader warnings; sha256="
    + hashlib.sha256(payload).hexdigest()
)
PY
