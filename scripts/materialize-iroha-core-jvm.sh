#!/usr/bin/env bash
set -euo pipefail

IROHA_CORE_ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
readonly IROHA_CORE_ROOT_DIR
readonly IROHA_CORE_TAG="v2.0.0-rc.2.1-fearless-mobile-sdk.3"
readonly IROHA_CORE_VERSION="2.0.0-rc.2.1-fearless-mobile-sdk.3"
readonly IROHA_CORE_ARCHIVE_NAME="iroha-mobile-sdk-android-${IROHA_CORE_TAG}.zip"
readonly IROHA_CORE_ARCHIVE_SHA256="24bb47552977cc2610512f59b8bccfd0b047b179302ed972600a66ddc1a01de6"
readonly IROHA_CORE_ARCHIVE_BYTES="116992564"
readonly IROHA_CORE_JAR_SHA256="33f449700948641c73eeaa4e4a8ab9e39d3d6a642a50b6b7d8c30a9f6aff19ce"
readonly IROHA_CORE_JAR_BYTES="4843020"
readonly IROHA_CORE_RELEASE_URL="https://github.com/hyperledger-iroha/iroha/releases/download/${IROHA_CORE_TAG}/${IROHA_CORE_ARCHIVE_NAME}"
readonly IROHA_CORE_DEFAULT_OUTPUT="$IROHA_CORE_ROOT_DIR/build/iroha-mobile-sdk/maven"
IROHA_CORE_TEMP_DOWNLOAD_DIR=""

iroha_core_usage() {
  cat <<USAGE
Usage:
  scripts/materialize-iroha-core-jvm.sh --download [--output-dir <dir>]
  scripts/materialize-iroha-core-jvm.sh --release-dir <dir> [--output-dir <dir>]
  scripts/materialize-iroha-core-jvm.sh --archive <file> [--output-dir <dir>]

Materializes only this checksum-pinned Maven coordinate:
  org.hyperledger.iroha.sdk:core-jvm:${IROHA_CORE_VERSION}

Pinned release:
  tag:    ${IROHA_CORE_TAG}
  file:   ${IROHA_CORE_ARCHIVE_NAME}
  bytes:  ${IROHA_CORE_ARCHIVE_BYTES}
  sha256: ${IROHA_CORE_ARCHIVE_SHA256}

The default generated Maven repository is:
  ${IROHA_CORE_DEFAULT_OUTPUT}

The source modes are mutually exclusive. --download always uses the pinned
HTTPS GitHub release URL; neither the release tag nor checksums are overridable.
USAGE
}

iroha_core_fail() {
  echo "[iroha-core-materializer] ERROR: $*" >&2
  return 1
}

iroha_core_require_tool() {
  command -v "$1" >/dev/null 2>&1 || iroha_core_fail "$1 is required"
}

iroha_core_cleanup_download() {
  local directory="${IROHA_CORE_TEMP_DOWNLOAD_DIR:-}"
  if [[ -n "$directory" && -d "$directory" ]]; then
    rm -rf -- "$directory"
  fi
  IROHA_CORE_TEMP_DOWNLOAD_DIR=""
}

# This function is intentionally sourceable by the adversarial self-test. The
# public CLI below always passes the immutable production pins and limits.
iroha_core_materialize_validated_archive() {
  local archive="$1"
  local expected_archive_sha256="$2"
  local expected_archive_bytes="$3"
  local output_dir="$4"
  local expected_jar_sha256="$5"
  local expected_jar_bytes="$6"
  local max_outer_entries="${7:-256}"
  local max_outer_total_bytes="${8:-268435456}"
  local max_outer_entry_bytes="${9:-134217728}"
  local max_outer_ratio="${10:-100}"
  local validation_repo_root="${11:-$IROHA_CORE_ROOT_DIR}"

  python3 - \
    "$archive" \
    "$expected_archive_sha256" \
    "$expected_archive_bytes" \
    "$output_dir" \
    "$expected_jar_sha256" \
    "$expected_jar_bytes" \
    "$max_outer_entries" \
    "$max_outer_total_bytes" \
    "$max_outer_entry_bytes" \
    "$max_outer_ratio" \
    "$IROHA_CORE_TAG" \
    "$IROHA_CORE_VERSION" \
    "$validation_repo_root" <<'PY'
import ctypes
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import re
import shutil
import stat
import sys
import tempfile
import unicodedata
import xml.etree.ElementTree as ET
import zipfile


class ValidationError(Exception):
    pass


def fail(message):
    raise ValidationError(message)


def parse_positive_int(label, value):
    try:
        parsed = int(value)
    except ValueError:
        fail(f"{label} must be an integer")
    if parsed <= 0:
        fail(f"{label} must be positive")
    return parsed


def digest_stream(stream, algorithm):
    digest = hashlib.new(algorithm)
    while True:
        chunk = stream.read(1024 * 1024)
        if not chunk:
            return digest.hexdigest()
        digest.update(chunk)


def digest_file(path, algorithm="sha256"):
    with path.open("rb") as stream:
        return digest_stream(stream, algorithm)


def secure_archive_snapshot(path, expected_bytes, expected_sha256):
    flags = os.O_RDONLY | getattr(os, "O_CLOEXEC", 0) | getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        fail(f"unable to securely open archive without following symlinks: {path}: {error}")

    snapshot = tempfile.TemporaryFile(prefix="iroha-core-archive-snapshot.")
    try:
        with os.fdopen(descriptor, "rb", closefd=True) as source:
            source_stat = os.fstat(source.fileno())
            if not stat.S_ISREG(source_stat.st_mode):
                fail(f"archive is not a regular file: {path}")
            if source_stat.st_size != expected_bytes:
                fail(
                    f"archive byte count mismatch: expected {expected_bytes}, "
                    f"got {source_stat.st_size}"
                )
            digest = hashlib.sha256()
            copied = 0
            while True:
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                copied += len(chunk)
                if copied > expected_bytes:
                    fail(f"archive grew beyond the pinned byte count {expected_bytes}")
                digest.update(chunk)
                snapshot.write(chunk)
            if copied != expected_bytes:
                fail(f"archive changed while being snapshotted: expected {expected_bytes}, got {copied}")
            actual_sha256 = digest.hexdigest()
            if actual_sha256 != expected_sha256:
                fail(
                    f"archive SHA-256 mismatch: expected {expected_sha256}, "
                    f"got {actual_sha256}"
                )
        snapshot.flush()
        snapshot.seek(0)
        return snapshot
    except Exception:
        snapshot.close()
        raise


def containment_key(path):
    value = unicodedata.normalize("NFC", os.path.normpath(os.fspath(path)))
    if sys.platform == "darwin":
        value = value.casefold()
    return Path(value)


def same_path(first, second):
    return containment_key(first) == containment_key(second)


def strict_descendant(path, parent):
    try:
        relative = containment_key(path).relative_to(containment_key(parent))
    except ValueError:
        return False
    return bool(relative.parts)


def same_or_parent(candidate, child):
    return same_path(candidate, child) or strict_descendant(child, candidate)


def relative_descendant(path, parent):
    try:
        relative = path.relative_to(parent)
    except ValueError:
        return None
    return relative if relative.parts else None


def validate_stable_output_resolution(output, anchor, resolved_anchor):
    relative = relative_descendant(output, anchor)
    if relative is None:
        fail(f"output directory escaped its approved anchor: {output}")
    expected_resolution = resolved_anchor.joinpath(relative)
    actual_resolution = Path(os.path.realpath(output))
    if not same_path(actual_resolution, expected_resolution):
        fail(f"output path contains a symbolic-link escape: {output}")

    current = anchor
    for component in relative.parts:
        current = current / component
        if os.path.lexists(current) and stat.S_ISLNK(os.lstat(current).st_mode):
            fail(f"output path contains a symbolic link: {current}")


def validate_output_path(raw_output, archive, repo_root):
    if not raw_output or not raw_output.strip():
        fail("output directory must not be empty")

    lexical = Path(os.path.abspath(raw_output))
    if lexical.is_symlink():
        fail(f"output directory is a symlink: {lexical}")
    if os.path.lexists(lexical) and not lexical.is_dir():
        fail(f"output path is not a directory: {lexical}")

    temp_anchor_pairs = []

    def add_temp_anchor(anchor, resolved_anchor):
        pair = (anchor, resolved_anchor)
        if pair not in temp_anchor_pairs:
            temp_anchor_pairs.append(pair)

    for candidate in ("/tmp", "/var/tmp", "/var/folders"):
        if not candidate:
            continue
        candidate_path = Path(os.path.abspath(candidate))
        if candidate_path == candidate_path.parent or not candidate_path.is_dir():
            continue
        resolved_candidate = Path(os.path.realpath(candidate_path))
        if resolved_candidate == resolved_candidate.parent:
            continue
        add_temp_anchor(candidate_path, resolved_candidate)
        if not same_path(candidate_path, resolved_candidate):
            add_temp_anchor(resolved_candidate, resolved_candidate)

    for candidate in (
        tempfile.gettempdir(),
        os.environ.get("TMPDIR", ""),
        os.environ.get("RUNNER_TEMP", ""),
    ):
        if not candidate:
            continue
        candidate_path = Path(os.path.abspath(candidate))
        if candidate_path == candidate_path.parent or not candidate_path.is_dir():
            continue
        resolved_candidate = Path(os.path.realpath(candidate_path))
        if resolved_candidate == resolved_candidate.parent:
            continue
        if same_path(candidate_path, resolved_candidate):
            add_temp_anchor(candidate_path, resolved_candidate)
        else:
            # Custom anchors are trusted only through their canonical spelling.
            # Fixed /tmp and /var aliases above remain available for macOS.
            add_temp_anchor(resolved_candidate, resolved_candidate)
    temp_roots = {resolved for _, resolved in temp_anchor_pairs}

    repo_lexical = Path(os.path.abspath(repo_root))
    repo = Path(os.path.realpath(repo_lexical))
    if not same_path(repo_lexical, repo):
        matching_repo_anchors = [
            (anchor, resolved)
            for anchor, resolved in temp_anchor_pairs
            if relative_descendant(repo_lexical, anchor) is not None
        ]
        if not matching_repo_anchors:
            fail(f"repository root contains a symbolic link: {repo_lexical}")
        repo_temp_anchor, resolved_repo_temp_anchor = max(
            matching_repo_anchors,
            key=lambda item: len(item[0].parts),
        )
        validate_stable_output_resolution(
            repo_lexical,
            repo_temp_anchor,
            resolved_repo_temp_anchor,
        )
    output = Path(os.path.realpath(lexical))
    cwd = Path(os.path.realpath(Path.cwd()))
    archive_path = Path(os.path.realpath(archive))
    filesystem_root = Path(output.anchor)
    repo_build = repo_lexical / "build"

    dangerous_exact_paths = {filesystem_root, repo, cwd, repo_build, *temp_roots}
    if any(same_path(output, dangerous) for dangerous in dangerous_exact_paths):
        fail(f"refusing unsafe output directory: {output}")
    if same_or_parent(output, repo):
        fail(f"output directory must not contain the repository: {output}")
    if same_or_parent(output, archive_path):
        fail(f"output directory must not contain the source archive: {output}")
    lexical_repo_relative = relative_descendant(lexical, repo_lexical)
    canonical_repo_relative = relative_descendant(lexical, repo)
    resolved_repo_relative = relative_descendant(output, repo)
    if (
        lexical_repo_relative is not None
        or canonical_repo_relative is not None
        or resolved_repo_relative is not None
    ):
        if lexical_repo_relative is not None:
            repo_relative = lexical_repo_relative
            output_repo_anchor = repo_lexical
        elif canonical_repo_relative is not None:
            repo_relative = canonical_repo_relative
            output_repo_anchor = repo
        else:
            fail(
                "output path enters the repository through an unapproved "
                f"symbolic link: {lexical}"
            )
        if (
            len(repo_relative.parts) < 2
            or repo_relative.parts[0] != "build"
        ):
            fail(
                "output inside the repository must be below the repository build "
                f"directory: {lexical}"
            )
        validate_stable_output_resolution(lexical, output_repo_anchor, repo)
        return lexical, output_repo_anchor, repo

    matching_temp_anchors = [
        (anchor, resolved)
        for anchor, resolved in temp_anchor_pairs
        if relative_descendant(lexical, anchor) is not None
    ]
    if not matching_temp_anchors:
        fail(
            "output directory must be below the repository build directory or "
            f"the system temporary directory: {lexical}"
        )
    anchor, resolved_anchor = max(
        matching_temp_anchors,
        key=lambda item: len(item[0].parts),
    )
    validate_stable_output_resolution(lexical, anchor, resolved_anchor)
    return lexical, anchor, resolved_anchor


def safe_member_name(name, label):
    if not name:
        fail(f"{label} has an empty name")
    if "\x00" in name:
        fail(f"{label} contains a NUL byte: {name!r}")
    if "\\" in name:
        fail(f"{label} contains a backslash: {name!r}")
    if name.startswith("/"):
        fail(f"{label} is absolute: {name!r}")

    stripped = name[:-1] if name.endswith("/") else name
    if not stripped:
        fail(f"{label} resolves to the archive root")
    parts = stripped.split("/")
    if any(part in ("", ".", "..") for part in parts):
        fail(f"{label} contains an unsafe path component: {name!r}")
    if any(re.match(r"^[A-Za-z]:", part) for part in parts):
        fail(f"{label} contains a drive-qualified component: {name!r}")
    return stripped


def inspect_zip(zf, label, max_entries, max_total, max_entry, max_ratio):
    infos = zf.infolist()
    if len(infos) > max_entries:
        fail(f"{label} entry count {len(infos)} exceeds limit {max_entries}")

    seen_exact = set()
    seen_portable = set()
    total = 0
    allowed_methods = {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}
    by_name = {}

    for info in infos:
        normalized = safe_member_name(info.filename, f"{label} entry")
        if info.filename in seen_exact:
            fail(f"{label} contains duplicate entry: {info.filename!r}")
        seen_exact.add(info.filename)

        portable = unicodedata.normalize("NFC", normalized).casefold()
        if portable in seen_portable:
            fail(f"{label} contains a portable-name collision: {info.filename!r}")
        seen_portable.add(portable)

        if info.flag_bits & 0x1:
            fail(f"{label} contains encrypted entry: {info.filename!r}")
        if info.compress_type not in allowed_methods:
            fail(
                f"{label} uses unsupported compression method "
                f"{info.compress_type}: {info.filename!r}"
            )

        mode = (info.external_attr >> 16) & 0xFFFF
        file_type = stat.S_IFMT(mode)
        if info.create_system == 3 and file_type not in (0, stat.S_IFREG, stat.S_IFDIR):
            if file_type == stat.S_IFLNK:
                kind = "symlink"
            else:
                kind = "special file"
            fail(f"{label} contains {kind}: {info.filename!r}")
        if info.is_dir():
            if file_type not in (0, stat.S_IFDIR):
                fail(f"{label} directory has a non-directory mode: {info.filename!r}")
            if info.file_size != 0:
                fail(f"{label} directory has data: {info.filename!r}")
        elif file_type == stat.S_IFDIR:
            fail(f"{label} file has a directory mode: {info.filename!r}")

        if info.file_size > max_entry:
            fail(
                f"{label} entry size {info.file_size} exceeds limit {max_entry}: "
                f"{info.filename!r}"
            )
        total += info.file_size
        if total > max_total:
            fail(f"{label} total uncompressed size {total} exceeds limit {max_total}")
        if info.file_size:
            if info.compress_size == 0:
                fail(f"{label} has an impossible zero compressed size: {info.filename!r}")
            ratio = info.file_size / info.compress_size
            if ratio > max_ratio:
                fail(
                    f"{label} compression ratio {ratio:.2f} exceeds limit "
                    f"{max_ratio}: {info.filename!r}"
                )
        by_name[info.filename] = info

    try:
        corrupt = zf.testzip()
    except (RuntimeError, zipfile.BadZipFile, OSError) as error:
        fail(f"{label} CRC/decompression validation failed: {error}")
    if corrupt is not None:
        fail(f"{label} CRC validation failed: {corrupt!r}")
    return by_name


def read_member(zf, info, limit):
    if info.file_size > limit:
        fail(f"entry exceeds read limit {limit}: {info.filename!r}")
    with zf.open(info, "r") as stream:
        data = stream.read(limit + 1)
    if len(data) != info.file_size:
        fail(f"entry size changed while reading: {info.filename!r}")
    if len(data) > limit:
        fail(f"entry exceeds read limit {limit}: {info.filename!r}")
    return data


def verify_sidecars(zf, members, coordinate_prefix, stem):
    payloads = {
        f"{stem}.jar": read_member(zf, members[f"{coordinate_prefix}{stem}.jar"], 8 * 1024 * 1024),
        f"{stem}.pom": read_member(zf, members[f"{coordinate_prefix}{stem}.pom"], 1024 * 1024),
        f"{stem}.module": read_member(zf, members[f"{coordinate_prefix}{stem}.module"], 1024 * 1024),
    }
    for filename, payload in payloads.items():
        for algorithm in ("md5", "sha1", "sha256", "sha512"):
            sidecar_name = f"{coordinate_prefix}{filename}.{algorithm}"
            try:
                sidecar = read_member(zf, members[sidecar_name], 256).decode("ascii").strip()
            except UnicodeDecodeError:
                fail(f"non-ASCII {algorithm} sidecar: {sidecar_name!r}")
            expected_length = hashlib.new(algorithm).digest_size * 2
            if not re.fullmatch(rf"[0-9a-fA-F]{{{expected_length}}}", sidecar):
                fail(f"malformed {algorithm} sidecar: {sidecar_name!r}")
            actual = hashlib.new(algorithm, payload).hexdigest()
            if sidecar.lower() != actual:
                fail(f"{algorithm} sidecar mismatch: {sidecar_name!r}")
    return payloads


def verify_internal_manifest(zf, members, archive_prefix, manifest_name):
    manifest_info = members.get(manifest_name)
    if manifest_info is None or manifest_info.is_dir():
        fail("missing internal SHA256SUMS.txt")
    try:
        text = read_member(zf, manifest_info, 128 * 1024).decode("ascii")
    except UnicodeDecodeError:
        fail("internal SHA256SUMS.txt is not ASCII")

    declared = {}
    for line_number, line in enumerate(text.splitlines(), 1):
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            fail(f"malformed internal SHA256SUMS.txt line {line_number}")
        digest, relative_name = match.groups()
        safe_member_name(relative_name, "internal checksum path")
        if relative_name in declared:
            fail(f"duplicate internal checksum path: {relative_name!r}")
        declared[relative_name] = digest

    regular_relative_names = {
        name[len(archive_prefix):]
        for name, info in members.items()
        if name.startswith(archive_prefix) and not info.is_dir() and name != manifest_name
    }
    if set(declared) != regular_relative_names:
        missing = sorted(regular_relative_names - set(declared))
        unexpected = sorted(set(declared) - regular_relative_names)
        fail(
            "internal SHA256SUMS.txt coverage mismatch "
            f"(missing={missing[:3]}, unexpected={unexpected[:3]})"
        )

    for relative_name in sorted(declared):
        full_name = f"{archive_prefix}{relative_name}"
        with zf.open(members[full_name], "r") as stream:
            actual = digest_stream(stream, "sha256")
        if actual != declared[relative_name]:
            fail(f"internal SHA256 mismatch: {relative_name!r}")


def verify_pom(pom_bytes, group, artifact, version):
    upper = pom_bytes.upper()
    if b"<!DOCTYPE" in upper or b"<!ENTITY" in upper:
        fail("core-jvm POM contains a forbidden DTD/entity declaration")
    try:
        root = ET.fromstring(pom_bytes)
    except ET.ParseError as error:
        fail(f"core-jvm POM is not valid XML: {error}")

    def child_text(name):
        child = root.find(f"{{*}}{name}")
        return None if child is None else child.text

    if child_text("groupId") != group:
        fail("core-jvm POM groupId mismatch")
    if child_text("artifactId") != artifact:
        fail("core-jvm POM artifactId mismatch")
    if child_text("version") != version:
        fail("core-jvm POM version mismatch")


def verify_module(module_bytes, group, artifact, version, jar_name, jar_bytes, jar_sha256):
    try:
        model = json.loads(module_bytes)
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        fail(f"core-jvm Gradle module metadata is invalid: {error}")
    component = model.get("component", {})
    expected_component = {"group": group, "module": artifact, "version": version}
    if any(component.get(key) != value for key, value in expected_component.items()):
        fail("core-jvm Gradle module component mismatch")
    variants = model.get("variants")
    if not isinstance(variants, list) or not variants:
        fail("core-jvm Gradle module metadata has no variants")
    file_records = []
    for variant in variants:
        files = variant.get("files", []) if isinstance(variant, dict) else []
        if not isinstance(files, list):
            fail("core-jvm Gradle module metadata has malformed files")
        file_records.extend(files)
    if not file_records:
        fail("core-jvm Gradle module metadata does not declare its JAR")
    for record in file_records:
        if not isinstance(record, dict):
            fail("core-jvm Gradle module metadata has a malformed file record")
        if record.get("name") != jar_name or record.get("url") != jar_name:
            fail("core-jvm Gradle module metadata references an unexpected artifact")
        if record.get("size") != jar_bytes or record.get("sha256") != jar_sha256:
            fail("core-jvm Gradle module metadata JAR checksum/size mismatch")


def verify_nested_jar(jar_bytes, jar_name):
    try:
        with zipfile.ZipFile(io.BytesIO(jar_bytes), "r") as nested:
            members = inspect_zip(
                nested,
                "core-jvm JAR",
                max_entries=4096,
                max_total=64 * 1024 * 1024,
                max_entry=16 * 1024 * 1024,
                max_ratio=100,
            )
            forbidden_suffixes = (".aar", ".jar", ".zip", ".so", ".dll", ".dylib")
            for name, info in members.items():
                if not info.is_dir() and name.casefold().endswith(forbidden_suffixes):
                    fail(f"core-jvm JAR contains nested/native artifact: {name!r}")
    except zipfile.BadZipFile as error:
        fail(f"core-jvm JAR is not a readable ZIP: {jar_name!r}: {error}")


def fsync_tree(root):
    for current_root, directories, files in os.walk(root, topdown=False):
        for filename in files:
            path = Path(current_root, filename)
            with path.open("rb") as stream:
                os.fsync(stream.fileno())
        descriptor = os.open(current_root, os.O_RDONLY)
        try:
            os.fsync(descriptor)
        finally:
            os.close(descriptor)


def atomic_exchange_directories(first, second):
    library = ctypes.CDLL(None, use_errno=True)
    first_bytes = os.fsencode(first)
    second_bytes = os.fsencode(second)
    if sys.platform == "darwin":
        try:
            exchange = library.renameatx_np
        except AttributeError:
            fail("atomic directory exchange is unavailable on this macOS runtime")
        exchange.argtypes = [
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_uint,
        ]
        exchange.restype = ctypes.c_int
        result = exchange(-2, first_bytes, -2, second_bytes, 0x00000002)
    elif sys.platform.startswith("linux"):
        try:
            exchange = library.renameat2
        except AttributeError:
            fail("atomic directory exchange is unavailable on this Linux runtime")
        exchange.argtypes = [
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_int,
            ctypes.c_char_p,
            ctypes.c_uint,
        ]
        exchange.restype = ctypes.c_int
        result = exchange(-100, first_bytes, -100, second_bytes, 0x00000002)
    else:
        fail(f"atomic directory exchange is unsupported on {sys.platform}")
    if result != 0:
        error_number = ctypes.get_errno()
        fail(f"atomic directory exchange failed: {os.strerror(error_number)}")


def replace_repository(stage, output, output_anchor, resolved_output_anchor):
    parent = output.parent
    lock_path = parent / f".{output.name}.materialize.lock"
    with lock_path.open("a+b") as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        validate_stable_output_resolution(
            output,
            output_anchor,
            resolved_output_anchor,
        )
        if output.is_symlink():
            fail(f"output directory is a symlink: {output}")
        if output.exists() and not output.is_dir():
            fail(f"output path is not a directory: {output}")
        if output.exists():
            atomic_exchange_directories(stage, output)
            try:
                shutil.rmtree(stage)
            except OSError as error:
                print(
                    f"[iroha-core-materializer] WARNING: unable to remove prior "
                    f"repository at {stage}: {error}",
                    file=sys.stderr,
                )
        else:
            os.rename(stage, output)
        parent_fd = os.open(parent, os.O_RDONLY)
        try:
            os.fsync(parent_fd)
        finally:
            os.close(parent_fd)


def main():
    if len(sys.argv) != 14:
        fail("internal invocation argument mismatch")
    archive = Path(os.path.abspath(sys.argv[1]))
    expected_archive_sha256 = sys.argv[2]
    expected_archive_bytes = parse_positive_int("expected archive bytes", sys.argv[3])
    raw_output = sys.argv[4]
    expected_jar_sha256 = sys.argv[5]
    expected_jar_bytes = parse_positive_int("expected JAR bytes", sys.argv[6])
    max_outer_entries = parse_positive_int("maximum outer entries", sys.argv[7])
    max_outer_total = parse_positive_int("maximum outer total bytes", sys.argv[8])
    max_outer_entry = parse_positive_int("maximum outer entry bytes", sys.argv[9])
    try:
        max_outer_ratio = float(sys.argv[10])
    except ValueError:
        fail("maximum outer compression ratio must be numeric")
    if max_outer_ratio <= 0:
        fail("maximum outer compression ratio must be positive")
    tag = sys.argv[11]
    version = sys.argv[12]
    repo_root = Path(sys.argv[13])

    if not re.fullmatch(r"[0-9a-f]{64}", expected_archive_sha256):
        fail("expected archive SHA-256 is malformed")
    if not re.fullmatch(r"[0-9a-f]{64}", expected_jar_sha256):
        fail("expected JAR SHA-256 is malformed")
    output, output_anchor, resolved_output_anchor = validate_output_path(
        raw_output,
        archive,
        repo_root,
    )
    archive_snapshot = secure_archive_snapshot(
        archive,
        expected_archive_bytes,
        expected_archive_sha256,
    )

    archive_prefix = f"iroha-mobile-sdk-android-{tag}/"
    group = "org.hyperledger.iroha.sdk"
    artifact = "core-jvm"
    artifact_root = f"{archive_prefix}maven/org/hyperledger/iroha/sdk/{artifact}/"
    coordinate_prefix = f"{artifact_root}{version}/"
    stem = f"{artifact}-{version}"
    expected_coordinate_files = {
        f"{stem}.{extension}{suffix}"
        for extension in ("jar", "pom", "module")
        for suffix in ("", ".md5", ".sha1", ".sha256", ".sha512")
    }
    allowed_metadata_files = {
        f"maven-metadata.xml{suffix}"
        for suffix in ("", ".md5", ".sha1", ".sha256", ".sha512")
    }
    manifest_name = f"{archive_prefix}SHA256SUMS.txt"
    raw_jar_name = f"{archive_prefix}core-jvm/{stem}.jar"

    try:
        with archive_snapshot, zipfile.ZipFile(archive_snapshot, "r") as zf:
            members = inspect_zip(
                zf,
                "release ZIP",
                max_entries=max_outer_entries,
                max_total=max_outer_total,
                max_entry=max_outer_entry,
                max_ratio=max_outer_ratio,
            )
            if manifest_name not in members:
                fail("missing internal SHA256SUMS.txt")
            if raw_jar_name not in members or members[raw_jar_name].is_dir():
                fail("missing raw core-jvm JAR")

            actual_coordinate_files = set()
            for name, info in members.items():
                if not name.startswith(artifact_root) or name == artifact_root:
                    continue
                relative = name[len(artifact_root):]
                if relative.startswith(f"{version}/"):
                    nested = relative[len(version) + 1:]
                    if not nested:
                        continue
                    if "/" in nested or info.is_dir():
                        fail(f"unexpected nested core-jvm coordinate entry: {name!r}")
                    actual_coordinate_files.add(nested)
                elif relative not in allowed_metadata_files:
                    fail(f"unexpected core-jvm repository entry: {name!r}")
            if actual_coordinate_files != expected_coordinate_files:
                missing = sorted(expected_coordinate_files - actual_coordinate_files)
                unexpected = sorted(actual_coordinate_files - expected_coordinate_files)
                fail(
                    "core-jvm coordinate allowlist mismatch "
                    f"(missing={missing}, unexpected={unexpected})"
                )

            verify_internal_manifest(zf, members, archive_prefix, manifest_name)
            payloads = verify_sidecars(zf, members, coordinate_prefix, stem)
            jar_name = f"{stem}.jar"
            jar_bytes = payloads[jar_name]
            actual_jar_sha256 = hashlib.sha256(jar_bytes).hexdigest()
            if len(jar_bytes) != expected_jar_bytes:
                fail(
                    f"core-jvm JAR byte count mismatch: expected {expected_jar_bytes}, "
                    f"got {len(jar_bytes)}"
                )
            if actual_jar_sha256 != expected_jar_sha256:
                fail(
                    f"core-jvm JAR SHA-256 mismatch: expected {expected_jar_sha256}, "
                    f"got {actual_jar_sha256}"
                )
            raw_jar = read_member(zf, members[raw_jar_name], 8 * 1024 * 1024)
            if raw_jar != jar_bytes:
                fail("raw and Maven core-jvm JARs differ")
            verify_nested_jar(jar_bytes, jar_name)
            verify_pom(payloads[f"{stem}.pom"], group, artifact, version)
            verify_module(
                payloads[f"{stem}.module"],
                group,
                artifact,
                version,
                jar_name,
                len(jar_bytes),
                actual_jar_sha256,
            )

            output_parent = output.parent
            if output == output.parent:
                fail("refusing to replace a filesystem root")
            validate_stable_output_resolution(
                output,
                output_anchor,
                resolved_output_anchor,
            )
            output_parent.mkdir(parents=True, exist_ok=True)
            validate_stable_output_resolution(
                output,
                output_anchor,
                resolved_output_anchor,
            )
            if output.is_symlink():
                fail(f"output directory is a symlink: {output}")
            stage = Path(tempfile.mkdtemp(prefix=f".{output.name}.stage.", dir=output_parent))
            try:
                target_coordinate = stage / "org/hyperledger/iroha/sdk/core-jvm" / version
                target_coordinate.mkdir(parents=True, mode=0o755)
                for filename in sorted(expected_coordinate_files):
                    source_info = members[f"{coordinate_prefix}{filename}"]
                    target = target_coordinate / filename
                    with zf.open(source_info, "r") as source, target.open("xb") as destination:
                        shutil.copyfileobj(source, destination, length=1024 * 1024)
                    os.chmod(target, 0o644)

                materialized_files = {
                    path.relative_to(stage).as_posix()
                    for path in stage.rglob("*")
                    if path.is_file()
                }
                expected_materialized = {
                    f"org/hyperledger/iroha/sdk/core-jvm/{version}/{filename}"
                    for filename in expected_coordinate_files
                }
                if materialized_files != expected_materialized:
                    fail("staged repository file allowlist mismatch")
                forbidden_suffixes = (".aar", ".so", ".dll", ".dylib", ".zip")
                if any(name.casefold().endswith(forbidden_suffixes) for name in materialized_files):
                    fail("staged repository contains a forbidden binary/native artifact")
                if digest_file(target_coordinate / jar_name) != expected_jar_sha256:
                    fail("staged core-jvm JAR digest mismatch")
                fsync_tree(stage)
                replace_repository(
                    stage,
                    output,
                    output_anchor,
                    resolved_output_anchor,
                )
                stage = None
            finally:
                if stage is not None:
                    shutil.rmtree(stage, ignore_errors=True)
    except zipfile.BadZipFile as error:
        fail(f"release ZIP is not readable: {error}")

    print(
        "[iroha-core-materializer] Materialized "
        f"{group}:{artifact}:{version} at {output}"
    )


try:
    main()
except ValidationError as error:
    print(f"[iroha-core-materializer] ERROR: {error}", file=sys.stderr)
    sys.exit(1)
except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
    print(f"[iroha-core-materializer] ERROR: validation/materialization failed: {error}", file=sys.stderr)
    sys.exit(1)
PY
}

iroha_core_main() {
  local source_mode=""
  local release_dir=""
  local archive=""
  local output_dir="$IROHA_CORE_DEFAULT_OUTPUT"

  while [[ $# -gt 0 ]]; do
    case "$1" in
      --download)
        [[ -z "$source_mode" ]] || iroha_core_fail "source modes are mutually exclusive"
        source_mode="download"
        ;;
      --release-dir)
        shift
        [[ $# -gt 0 ]] || iroha_core_fail "--release-dir requires a value"
        [[ -z "$source_mode" ]] || iroha_core_fail "source modes are mutually exclusive"
        source_mode="release-dir"
        release_dir="$1"
        ;;
      --release-dir=*)
        [[ -z "$source_mode" ]] || iroha_core_fail "source modes are mutually exclusive"
        source_mode="release-dir"
        release_dir="${1#*=}"
        [[ -n "$release_dir" ]] || iroha_core_fail "--release-dir requires a value"
        ;;
      --archive)
        shift
        [[ $# -gt 0 ]] || iroha_core_fail "--archive requires a value"
        [[ -z "$source_mode" ]] || iroha_core_fail "source modes are mutually exclusive"
        source_mode="archive"
        archive="$1"
        ;;
      --archive=*)
        [[ -z "$source_mode" ]] || iroha_core_fail "source modes are mutually exclusive"
        source_mode="archive"
        archive="${1#*=}"
        [[ -n "$archive" ]] || iroha_core_fail "--archive requires a value"
        ;;
      --output-dir)
        shift
        [[ $# -gt 0 ]] || iroha_core_fail "--output-dir requires a value"
        output_dir="$1"
        [[ -n "$output_dir" ]] || iroha_core_fail "--output-dir requires a non-empty value"
        ;;
      --output-dir=*)
        output_dir="${1#*=}"
        [[ -n "$output_dir" ]] || iroha_core_fail "--output-dir requires a value"
        ;;
      -h|--help)
        iroha_core_usage
        return 0
        ;;
      *)
        iroha_core_fail "unexpected argument: $1"
        ;;
    esac
    shift
  done

  [[ -n "$source_mode" ]] || {
    iroha_core_usage >&2
    iroha_core_fail "one of --download, --release-dir, or --archive is required"
  }
  iroha_core_require_tool python3

  case "$source_mode" in
    download)
      iroha_core_require_tool curl
      IROHA_CORE_TEMP_DOWNLOAD_DIR="$(mktemp -d "${TMPDIR:-/tmp}/iroha-core-download.XXXXXX")"
      archive="$IROHA_CORE_TEMP_DOWNLOAD_DIR/$IROHA_CORE_ARCHIVE_NAME"
      trap iroha_core_cleanup_download EXIT
      umask 077
      curl \
        --disable \
        --fail \
        --location \
        --silent \
        --show-error \
        --proto '=https' \
        --proto-redir '=https' \
        --connect-timeout 30 \
        --max-time 900 \
        --max-filesize "$IROHA_CORE_ARCHIVE_BYTES" \
        --retry 3 \
        --retry-delay 1 \
        --output "$archive.part" \
        "$IROHA_CORE_RELEASE_URL"
      mv "$archive.part" "$archive"
      ;;
    release-dir)
      [[ -d "$release_dir" ]] || iroha_core_fail "release directory does not exist: $release_dir"
      [[ ! -L "$release_dir" ]] || iroha_core_fail "release directory must not be a symlink: $release_dir"
      archive="${release_dir%/}/$IROHA_CORE_ARCHIVE_NAME"
      ;;
    archive)
      [[ "$(basename "$archive")" == "$IROHA_CORE_ARCHIVE_NAME" ]] ||
        iroha_core_fail "archive basename must be exactly $IROHA_CORE_ARCHIVE_NAME"
      ;;
  esac

  [[ -f "$archive" ]] || iroha_core_fail "archive does not exist: $archive"
  [[ ! -L "$archive" ]] || iroha_core_fail "archive must not be a symlink: $archive"

  iroha_core_materialize_validated_archive \
    "$archive" \
    "$IROHA_CORE_ARCHIVE_SHA256" \
    "$IROHA_CORE_ARCHIVE_BYTES" \
    "$output_dir" \
    "$IROHA_CORE_JAR_SHA256" \
    "$IROHA_CORE_JAR_BYTES"

  iroha_core_cleanup_download
  trap - EXIT
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  iroha_core_main "$@"
fi
