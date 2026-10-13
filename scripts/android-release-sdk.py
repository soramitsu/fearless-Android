#!/usr/bin/env python3
"""Install and verify the checksum-locked Android release SDK."""

from __future__ import annotations

import argparse
import errno
import hashlib
import io
import json
import os
import platform
import re
import secrets
import signal
import stat
import sys
import zipfile
from pathlib import Path, PurePosixPath
from typing import Any
from urllib.parse import urlsplit


class ReleaseSdkError(RuntimeError):
    """A fail-closed Android release SDK validation error."""


LOCK_KEYS = {"schemaVersion", "host", "license", "packages"}
HOST_KEYS = {"os", "arch"}
LICENSE_KEYS = {"id", "textSha256", "runtimeAcceptance"}
PACKAGE_KEYS = {
    "packageType",
    "packageId",
    "revision",
    "archiveFilename",
    "archiveUrl",
    "archiveSize",
    "archiveSha1",
    "archiveSha256",
    "archiveEntryCount",
    "archiveUncompressedSize",
    "archiveRoot",
    "installPath",
    "sourcePropertiesSha256",
    "sourceProperties",
    "requiredFiles",
}
EVIDENCE_KEYS = {
    "schemaVersion",
    "lockSha256",
    "host",
    "license",
    "isolatedRoot",
    "sdkTreeSha256",
    "installedTreeManifestSha256",
    "packages",
}
EVIDENCE_PACKAGE_KEYS = PACKAGE_KEYS | {"installedTreeSha256"}
SAFE_PATH_COMPONENT = re.compile(r"^[A-Za-z0-9._+@=-]+$")
LOWER_SHA1 = re.compile(r"^[0-9a-f]{40}$")
LOWER_SHA256 = re.compile(r"^[0-9a-f]{64}$")
PACKAGE_ID = re.compile(r"^(platforms|build-tools);[A-Za-z0-9._-]+$")
REVISION = re.compile(r"^[0-9]+(?:\.[0-9]+){0,2}$")
MAX_LOCK_BYTES = 1024 * 1024
MAX_EVIDENCE_BYTES = 2 * 1024 * 1024


def fail(message: str) -> None:
    raise ReleaseSdkError(message)


def exact_keys(value: Any, expected: set[str], label: str) -> None:
    if not isinstance(value, dict) or set(value) != expected:
        fail(f"{label} must contain exactly: {', '.join(sorted(expected))}")


def no_duplicate_json_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            fail(f"JSON contains a duplicate key: {key}")
        result[key] = value
    return result


def read_regular_file(path: Path, limit: int, label: str) -> bytes:
    try:
        metadata = path.lstat()
    except FileNotFoundError:
        fail(f"{label} is missing: {path}")
    if not stat.S_ISREG(metadata.st_mode) or path.is_symlink():
        fail(f"{label} must be a regular, non-symlink file: {path}")
    if metadata.st_size <= 0 or metadata.st_size > limit:
        fail(f"{label} has an unsafe size: {metadata.st_size}")
    flags = os.O_RDONLY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    descriptor = os.open(path, flags)
    try:
        opened = os.fstat(descriptor)
        if not stat.S_ISREG(opened.st_mode):
            fail(f"{label} changed type while it was opened")
        chunks: list[bytes] = []
        total = 0
        while True:
            chunk = os.read(descriptor, min(1024 * 1024, limit + 1 - total))
            if not chunk:
                break
            chunks.append(chunk)
            total += len(chunk)
            if total > limit:
                fail(f"{label} exceeds its maximum permitted size")
        data = b"".join(chunks)
        if len(data) != opened.st_size:
            fail(f"{label} changed while its bytes were captured")
        return data
    finally:
        os.close(descriptor)


def parse_json_bytes(data: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            data.decode("utf-8"), object_pairs_hook=no_duplicate_json_keys
        )
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        fail(f"{label} is not valid UTF-8 JSON: {error}")
    if not isinstance(value, dict):
        fail(f"{label} must be a JSON object")
    return value


def validate_relative_path(value: Any, label: str, components: int | None = None) -> str:
    if not isinstance(value, str) or not value or not value.isascii():
        fail(f"{label} must be a non-empty ASCII relative path")
    if "\\" in value or "\x00" in value or value.startswith("/"):
        fail(f"{label} contains a forbidden path form")
    parts = value.split("/")
    if any(part in ("", ".", "..") or not SAFE_PATH_COMPONENT.fullmatch(part) for part in parts):
        fail(f"{label} contains an unsafe path component")
    if components is not None and len(parts) != components:
        fail(f"{label} must contain exactly {components} path component(s)")
    return value


def validate_lock(lock_path: Path) -> tuple[dict[str, Any], bytes, str]:
    raw = read_regular_file(lock_path, MAX_LOCK_BYTES, "SDK lock")
    lock = parse_json_bytes(raw, "SDK lock")
    exact_keys(lock, LOCK_KEYS, "SDK lock")
    if lock["schemaVersion"] != 1:
        fail("SDK lock schemaVersion must be exactly 1")

    exact_keys(lock["host"], HOST_KEYS, "SDK lock host")
    if lock["host"]["os"] not in {"linux", "darwin"}:
        fail("SDK lock host OS is unsupported")
    if lock["host"]["arch"] not in {"x86_64", "arm64"}:
        fail("SDK lock host architecture is unsupported")

    exact_keys(lock["license"], LICENSE_KEYS, "SDK lock license")
    license_record = lock["license"]
    if license_record["id"] != "android-sdk-license":
        fail("SDK lock must identify the standard android-sdk-license")
    if not isinstance(license_record["textSha256"], str) or not LOWER_SHA256.fullmatch(
        license_record["textSha256"]
    ):
        fail("SDK license textSha256 must be lowercase SHA-256")
    if license_record["runtimeAcceptance"] != "not-performed-direct-archive":
        fail("SDK lock must explicitly record direct-archive license handling")

    packages = lock["packages"]
    if not isinstance(packages, list) or len(packages) != 2:
        fail("SDK lock must contain exactly two packages")
    seen_types: set[str] = set()
    seen_ids: set[str] = set()
    seen_archives: set[str] = set()
    seen_installs: set[str] = set()
    for index, package in enumerate(packages):
        label = f"SDK package {index}"
        exact_keys(package, PACKAGE_KEYS, label)
        package_type = package["packageType"]
        if package_type not in {"platform", "build-tools"}:
            fail(f"{label} has an unsupported packageType")
        if package_type in seen_types:
            fail("SDK lock must contain one platform and one build-tools package")
        seen_types.add(package_type)

        package_id = package["packageId"]
        if not isinstance(package_id, str) or not PACKAGE_ID.fullmatch(package_id):
            fail(f"{label} packageId is malformed")
        expected_prefix = "platforms;" if package_type == "platform" else "build-tools;"
        if not package_id.startswith(expected_prefix):
            fail(f"{label} packageId does not match packageType")
        if package_id in seen_ids:
            fail("SDK packageId values must be unique")
        seen_ids.add(package_id)

        revision = package["revision"]
        if not isinstance(revision, str) or not REVISION.fullmatch(revision):
            fail(f"{label} revision is malformed")
        archive_filename = validate_relative_path(
            package["archiveFilename"], f"{label} archiveFilename", 1
        )
        if not archive_filename.endswith(".zip"):
            fail(f"{label} archiveFilename must end in .zip")
        if archive_filename in seen_archives:
            fail("SDK archive filenames must be unique")
        seen_archives.add(archive_filename)

        url = package["archiveUrl"]
        if not isinstance(url, str):
            fail(f"{label} archiveUrl is malformed")
        parsed = urlsplit(url)
        if (
            parsed.scheme != "https"
            or parsed.hostname != "dl.google.com"
            or parsed.port is not None
            or parsed.username is not None
            or parsed.password is not None
            or parsed.query
            or parsed.fragment
            or parsed.path != f"/android/repository/{archive_filename}"
        ):
            fail(f"{label} archiveUrl must be the exact official Google HTTPS path")

        for field, maximum in (
            ("archiveSize", 1024 * 1024 * 1024),
            ("archiveEntryCount", 100_000),
            ("archiveUncompressedSize", 4 * 1024 * 1024 * 1024),
        ):
            value = package[field]
            if isinstance(value, bool) or not isinstance(value, int) or not (0 < value <= maximum):
                fail(f"{label} {field} is outside the fail-closed limit")
        if not isinstance(package["archiveSha1"], str) or not LOWER_SHA1.fullmatch(
            package["archiveSha1"]
        ):
            fail(f"{label} archiveSha1 must be lowercase SHA-1")
        for field in ("archiveSha256", "sourcePropertiesSha256"):
            if not isinstance(package[field], str) or not LOWER_SHA256.fullmatch(package[field]):
                fail(f"{label} {field} must be lowercase SHA-256")

        validate_relative_path(package["archiveRoot"], f"{label} archiveRoot", 1)
        install_path = validate_relative_path(package["installPath"], f"{label} installPath", 2)
        expected_install_prefix = "platforms/" if package_type == "platform" else "build-tools/"
        if not install_path.startswith(expected_install_prefix):
            fail(f"{label} installPath does not match packageType")
        if install_path in seen_installs:
            fail("SDK install paths must be unique")
        seen_installs.add(install_path)

        source_properties = package["sourceProperties"]
        if not isinstance(source_properties, dict) or not source_properties:
            fail(f"{label} sourceProperties must be a non-empty object")
        for key, value in source_properties.items():
            if (
                not isinstance(key, str)
                or not key
                or not key.isascii()
                or any(char in key for char in "=\r\n\x00")
                or not isinstance(value, str)
                or not value.isascii()
                or any(char in value for char in "\r\n\x00")
            ):
                fail(f"{label} sourceProperties contains an unsafe key or value")
        if source_properties.get("Pkg.Revision") != revision:
            fail(f"{label} Pkg.Revision does not match revision")

        required_files = package["requiredFiles"]
        if not isinstance(required_files, list) or not required_files:
            fail(f"{label} requiredFiles must be a non-empty array")
        normalized_required = [
            validate_relative_path(value, f"{label} requiredFiles entry")
            for value in required_files
        ]
        if len(set(normalized_required)) != len(normalized_required):
            fail(f"{label} requiredFiles contains a duplicate")
        if "source.properties" not in normalized_required:
            fail(f"{label} requiredFiles must include source.properties")
    if seen_types != {"platform", "build-tools"}:
        fail("SDK lock must contain one platform and one build-tools package")
    return lock, raw, hashlib.sha256(raw).hexdigest()


def current_host() -> dict[str, str]:
    os_name = platform.system().lower()
    machine = platform.machine().lower()
    architecture = {"amd64": "x86_64", "aarch64": "arm64"}.get(machine, machine)
    return {"os": os_name, "arch": architecture}


def require_host(lock: dict[str, Any]) -> None:
    actual = current_host()
    if actual != lock["host"]:
        fail(
            "SDK lock host mismatch: "
            f"expected {lock['host']['os']}/{lock['host']['arch']}, "
            f"got {actual['os']}/{actual['arch']}"
        )


def safe_zip_name(name: str, archive_root: str) -> str:
    if not name or not name.isascii() or len(name) > 4096:
        fail("SDK archive contains an empty, non-ASCII, or oversized entry name")
    if "\\" in name or "\x00" in name or name.startswith("/"):
        fail(f"SDK archive contains a forbidden entry path: {name!r}")
    path = PurePosixPath(name)
    parts = path.parts
    if (
        len(parts) < 2
        or parts[0] != archive_root
        or any(part in ("", ".", "..") for part in parts)
        or any(not SAFE_PATH_COMPONENT.fullmatch(part) for part in parts)
    ):
        fail(f"SDK archive entry escapes or differs from root {archive_root}: {name!r}")
    for part in parts:
        if len(part.encode("ascii")) > 255:
            fail(f"SDK archive entry has an oversized path component: {name!r}")
    return "/".join(parts)


def capture_and_validate_archive(
    package: dict[str, Any], archive_path: Path
) -> tuple[bytes, list[zipfile.ZipInfo]]:
    limit = package["archiveSize"]
    data = read_regular_file(archive_path, limit, f"SDK archive {archive_path.name}")
    if len(data) != package["archiveSize"]:
        fail(f"SDK archive {archive_path.name} size mismatch")
    if hashlib.sha1(data).hexdigest() != package["archiveSha1"]:  # noqa: S324 - official metadata correlation
        fail(f"SDK archive {archive_path.name} SHA-1 mismatch")
    if hashlib.sha256(data).hexdigest() != package["archiveSha256"]:
        fail(f"SDK archive {archive_path.name} SHA-256 mismatch")
    try:
        archive = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile as error:
        fail(f"SDK archive {archive_path.name} is not a valid ZIP: {error}")
    with archive:
        if archive.comment:
            fail(f"SDK archive {archive_path.name} must not contain a ZIP comment")
        infos = archive.infolist()
        if len(infos) != package["archiveEntryCount"]:
            fail(f"SDK archive {archive_path.name} entry count mismatch")
        if sum(info.file_size for info in infos) != package["archiveUncompressedSize"]:
            fail(f"SDK archive {archive_path.name} uncompressed size mismatch")
        seen: set[str] = set()
        casefolded: set[str] = set()
        for info in infos:
            normalized = safe_zip_name(info.filename, package["archiveRoot"])
            if normalized in seen:
                fail(f"SDK archive contains a duplicate entry: {normalized}")
            seen.add(normalized)
            folded = normalized.casefold()
            if folded in casefolded:
                fail(f"SDK archive contains a case-colliding entry: {normalized}")
            casefolded.add(folded)
            if info.flag_bits & 0x1:
                fail(f"SDK archive contains an encrypted entry: {normalized}")
            if info.compress_type not in {zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED}:
                fail(f"SDK archive contains an unsupported compression method: {normalized}")
            mode = (info.external_attr >> 16) & 0xFFFF
            if stat.S_IFMT(mode) != stat.S_IFREG or stat.S_IMODE(mode) not in {0o644, 0o755}:
                fail(f"SDK archive contains a non-regular or unsafe-mode entry: {normalized}")
    return data, infos


def extract_package(package: dict[str, Any], archive_path: Path, sdk_stage: Path) -> None:
    data, infos = capture_and_validate_archive(package, archive_path)
    destination = sdk_stage / package["installPath"]
    destination.mkdir(parents=True, mode=0o700)
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        for info in infos:
            normalized = safe_zip_name(info.filename, package["archiveRoot"])
            relative_parts = PurePosixPath(normalized).parts[1:]
            output = destination.joinpath(*relative_parts)
            output.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            mode = stat.S_IMODE((info.external_attr >> 16) & 0xFFFF)
            flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL
            if hasattr(os, "O_NOFOLLOW"):
                flags |= os.O_NOFOLLOW
            descriptor = os.open(output, flags, mode)
            try:
                with archive.open(info, "r") as source:
                    while True:
                        chunk = source.read(1024 * 1024)
                        if not chunk:
                            break
                        remaining = memoryview(chunk)
                        while remaining:
                            written = os.write(descriptor, remaining)
                            if written <= 0:
                                fail(f"Could not fully extract SDK entry: {normalized}")
                            remaining = remaining[written:]
                os.fchmod(descriptor, mode)
            finally:
                os.close(descriptor)


def parse_source_properties(data: bytes, label: str) -> dict[str, str]:
    try:
        text = data.decode("utf-8")
    except UnicodeDecodeError as error:
        fail(f"{label} source.properties is not UTF-8: {error}")
    result: dict[str, str] = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            fail(f"{label} source.properties contains a malformed line")
        key, value = line.split("=", 1)
        if not key or key in result:
            fail(f"{label} source.properties contains a duplicate or empty key")
        result[key] = value
    return result


def verify_package_metadata(package: dict[str, Any], sdk_root: Path) -> None:
    package_root = sdk_root / package["installPath"]
    if not package_root.is_dir() or package_root.is_symlink():
        fail(f"Installed SDK package is missing or unsafe: {package['packageId']}")
    source_path = package_root / "source.properties"
    source_bytes = read_regular_file(
        source_path, 1024 * 1024, f"{package['packageId']} source.properties"
    )
    if hashlib.sha256(source_bytes).hexdigest() != package["sourcePropertiesSha256"]:
        fail(f"{package['packageId']} source.properties digest mismatch")
    if parse_source_properties(source_bytes, package["packageId"]) != package["sourceProperties"]:
        fail(f"{package['packageId']} source.properties identity mismatch")
    for relative in package["requiredFiles"]:
        required = package_root.joinpath(*PurePosixPath(relative).parts)
        try:
            metadata = required.lstat()
        except FileNotFoundError:
            fail(f"{package['packageId']} is missing required file: {relative}")
        if not stat.S_ISREG(metadata.st_mode) or required.is_symlink() or metadata.st_size <= 0:
            fail(f"{package['packageId']} required file is empty or unsafe: {relative}")


def canonical_tree(root: Path) -> tuple[list[dict[str, Any]], str]:
    if not root.is_dir() or root.is_symlink():
        fail(f"Installed SDK tree is missing or unsafe: {root}")
    root_metadata = root.lstat()
    records: list[dict[str, Any]] = [
        {
            "path": ".",
            "type": "directory",
            "mode": f"{stat.S_IMODE(root_metadata.st_mode):04o}",
        }
    ]
    pending = [root]
    while pending:
        directory = pending.pop()
        with os.scandir(directory) as iterator:
            entries = sorted(iterator, key=lambda entry: entry.name)
        for entry in entries:
            relative = Path(entry.path).relative_to(root).as_posix()
            validate_relative_path(relative, "installed SDK path")
            metadata = entry.stat(follow_symlinks=False)
            if stat.S_ISLNK(metadata.st_mode):
                fail(f"Installed SDK tree contains a symlink: {relative}")
            mode = f"{stat.S_IMODE(metadata.st_mode):04o}"
            if stat.S_ISDIR(metadata.st_mode):
                records.append({"path": relative, "type": "directory", "mode": mode})
                pending.append(Path(entry.path))
            elif stat.S_ISREG(metadata.st_mode):
                data = read_regular_file(
                    Path(entry.path), 4 * 1024 * 1024 * 1024, f"installed SDK file {relative}"
                )
                records.append(
                    {
                        "path": relative,
                        "type": "file",
                        "mode": mode,
                        "size": len(data),
                        "sha256": hashlib.sha256(data).hexdigest(),
                    }
                )
            else:
                fail(f"Installed SDK tree contains a special file: {relative}")
    records.sort(key=lambda record: record["path"])
    canonical = json.dumps(records, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return records, hashlib.sha256(canonical).hexdigest()


def make_read_only(root: Path) -> None:
    directories: list[Path] = []
    for directory, child_directories, filenames in os.walk(root, topdown=True, followlinks=False):
        current = Path(directory)
        directories.append(current)
        for name in child_directories:
            child = current / name
            if child.is_symlink():
                fail(f"Cannot make symlinked SDK directory read-only: {child}")
        for name in filenames:
            child = current / name
            metadata = child.lstat()
            if not stat.S_ISREG(metadata.st_mode) or child.is_symlink():
                fail(f"Cannot make unsafe SDK file read-only: {child}")
            executable = bool(stat.S_IMODE(metadata.st_mode) & 0o111)
            child.chmod(0o555 if executable else 0o444)
    for directory in reversed(directories):
        directory.chmod(0o555)


def open_directory(path: Path) -> int:
    flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        flags |= os.O_DIRECTORY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    return os.open(path, flags)


def remove_tree_at(parent_descriptor: int, name: str) -> None:
    """Remove one child tree through an anchored parent descriptor."""
    if not name or name in {".", ".."} or "/" in name or "\\" in name:
        fail("Refusing to clean an unsafe SDK temporary-tree name")

    flags = os.O_RDONLY
    if hasattr(os, "O_DIRECTORY"):
        flags |= os.O_DIRECTORY
    if hasattr(os, "O_NOFOLLOW"):
        flags |= os.O_NOFOLLOW
    try:
        descriptor = os.open(name, flags, dir_fd=parent_descriptor)
    except FileNotFoundError:
        return
    except OSError as error:
        if error.errno not in {errno.ELOOP, errno.ENOTDIR}:
            raise
        try:
            os.unlink(name, dir_fd=parent_descriptor)
        except FileNotFoundError:
            pass
        return

    try:
        os.fchmod(descriptor, 0o700)
        for child_name in os.listdir(descriptor):
            remove_tree_at(descriptor, child_name)
    finally:
        os.close(descriptor)
    try:
        os.rmdir(name, dir_fd=parent_descriptor)
    except FileNotFoundError:
        pass


def require_anchored_parent(path: Path, descriptor: int, label: str) -> None:
    try:
        current = path.resolve(strict=True)
        current_metadata = current.stat()
    except (FileNotFoundError, OSError) as error:
        fail(f"{label} changed during SDK installation: {error}")
    anchored_metadata = os.fstat(descriptor)
    if current != path or (
        current_metadata.st_dev,
        current_metadata.st_ino,
    ) != (
        anchored_metadata.st_dev,
        anchored_metadata.st_ino,
    ):
        fail(f"{label} changed during SDK installation")


def package_tree_digest(package: dict[str, Any], sdk_root: Path) -> str:
    _, digest = canonical_tree(sdk_root / package["installPath"])
    return digest


def expected_evidence(
    lock: dict[str, Any], lock_sha256: str, sdk_root: Path, manifest_sha256: str
) -> dict[str, Any]:
    packages = []
    for package in lock["packages"]:
        record = dict(package)
        record["installedTreeSha256"] = package_tree_digest(package, sdk_root)
        packages.append(record)
    _, sdk_tree_sha256 = canonical_tree(sdk_root)
    return {
        "schemaVersion": 1,
        "lockSha256": lock_sha256,
        "host": lock["host"],
        "license": lock["license"],
        "isolatedRoot": True,
        "sdkTreeSha256": sdk_tree_sha256,
        "installedTreeManifestSha256": manifest_sha256,
        "packages": packages,
    }


def canonical_json_file(value: Any) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode("utf-8")


def validate_evidence_structure(
    lock: dict[str, Any], lock_sha256: str, evidence: dict[str, Any]
) -> None:
    exact_keys(evidence, EVIDENCE_KEYS, "SDK evidence")
    if evidence["schemaVersion"] != 1:
        fail("SDK evidence schemaVersion must be exactly 1")
    if evidence["lockSha256"] != lock_sha256:
        fail("SDK evidence is not bound to the checked-in lock")
    if evidence["host"] != lock["host"] or evidence["license"] != lock["license"]:
        fail("SDK evidence host or license differs from the lock")
    if evidence["isolatedRoot"] is not True:
        fail("SDK evidence must record an isolated root")
    for field in ("sdkTreeSha256", "installedTreeManifestSha256"):
        if not isinstance(evidence[field], str) or not LOWER_SHA256.fullmatch(evidence[field]):
            fail(f"SDK evidence {field} must be lowercase SHA-256")
    packages = evidence["packages"]
    if not isinstance(packages, list) or len(packages) != len(lock["packages"]):
        fail("SDK evidence package count differs from the lock")
    for index, (actual, expected) in enumerate(zip(packages, lock["packages"])):
        exact_keys(actual, EVIDENCE_PACKAGE_KEYS, f"SDK evidence package {index}")
        installed_digest = actual.get("installedTreeSha256")
        if not isinstance(installed_digest, str) or not LOWER_SHA256.fullmatch(installed_digest):
            fail(f"SDK evidence package {index} installedTreeSha256 is malformed")
        without_digest = dict(actual)
        del without_digest["installedTreeSha256"]
        if without_digest != expected:
            fail(f"SDK evidence package {index} differs from the lock")


def command_download_plan(lock_path: Path) -> None:
    lock, _, _ = validate_lock(lock_path)
    require_host(lock)
    for package in lock["packages"]:
        print(
            "\t".join(
                [
                    package["archiveFilename"],
                    package["archiveUrl"],
                    str(package["archiveSize"]),
                    package["archiveSha1"],
                    package["archiveSha256"],
                ]
            )
        )


def command_install(lock_path: Path, sdk_root: Path, archive_dir: Path, evidence_root: Path) -> None:
    lock, _, lock_sha256 = validate_lock(lock_path)
    require_host(lock)
    if not sdk_root.is_absolute() or not evidence_root.is_absolute() or not archive_dir.is_absolute():
        fail("SDK, archive, and evidence paths must be absolute")
    if sdk_root.exists() or sdk_root.is_symlink():
        fail("SDK root must not exist before isolated installation")
    if evidence_root.exists() or evidence_root.is_symlink():
        fail("SDK evidence root must not exist before installation")
    if not archive_dir.is_dir() or archive_dir.is_symlink():
        fail("SDK archive directory must be an existing non-symlink directory")
    sdk_parent = sdk_root.parent.resolve(strict=True)
    evidence_parent = evidence_root.parent.resolve(strict=True)
    archive_parent = archive_dir.parent.resolve(strict=True)
    if sdk_root.parent != sdk_parent or evidence_root.parent != evidence_parent:
        fail("SDK and evidence parent directories must be canonical and non-symlinked")
    if archive_dir.parent != archive_parent:
        fail("SDK archive parent directory must be canonical and non-symlinked")
    sdk_parent_descriptor = open_directory(sdk_parent)
    try:
        evidence_parent_descriptor = open_directory(evidence_parent)
    except BaseException:
        os.close(sdk_parent_descriptor)
        raise
    sdk_stage_parent: Path | None = None
    evidence_stage_parent: Path | None = None
    sdk_stage: Path | None = None
    evidence_stage: Path | None = None
    sdk_published = False
    evidence_published = False
    success = False
    interrupted_signals = tuple(
        candidate
        for candidate in (signal.SIGHUP, signal.SIGINT, signal.SIGTERM)
        if candidate is not None
    )
    previous_signal_handlers: dict[signal.Signals, Any] = {}

    def interrupt_installation(signum: int, _frame: Any) -> None:
        try:
            signal_name = signal.Signals(signum).name
        except ValueError:
            signal_name = str(signum)
        fail(f"SDK installation interrupted by {signal_name}")

    try:
        for candidate in interrupted_signals:
            previous_signal_handlers[candidate] = signal.signal(
                candidate, interrupt_installation
            )
        for _ in range(128):
            sdk_stage_parent = sdk_parent / (
                ".fearless-sdk-stage-" + secrets.token_hex(12)
            )
            try:
                os.mkdir(
                    sdk_stage_parent.name,
                    mode=0o700,
                    dir_fd=sdk_parent_descriptor,
                )
                break
            except FileExistsError:
                continue
        else:
            fail("Unable to reserve a private SDK staging directory")
        for _ in range(128):
            evidence_stage_parent = evidence_parent / (
                ".fearless-sdk-evidence-stage-" + secrets.token_hex(12)
            )
            try:
                os.mkdir(
                    evidence_stage_parent.name,
                    mode=0o700,
                    dir_fd=evidence_parent_descriptor,
                )
                break
            except FileExistsError:
                continue
        else:
            fail("Unable to reserve a private SDK evidence staging directory")
        sdk_stage = sdk_stage_parent / "sdk"
        evidence_stage = evidence_stage_parent / "evidence"
        sdk_stage.mkdir(mode=0o700)
        evidence_stage.mkdir(mode=0o700)
        for package in lock["packages"]:
            extract_package(package, archive_dir / package["archiveFilename"], sdk_stage)
            verify_package_metadata(package, sdk_stage)
        make_read_only(sdk_stage)
        for package in lock["packages"]:
            verify_package_metadata(package, sdk_stage)
        tree_records, _ = canonical_tree(sdk_stage)
        tree_manifest_bytes = canonical_json_file(tree_records)
        tree_manifest_sha256 = hashlib.sha256(tree_manifest_bytes).hexdigest()
        evidence = expected_evidence(lock, lock_sha256, sdk_stage, tree_manifest_sha256)
        evidence_bytes = canonical_json_file(evidence)
        (evidence_stage / "installed-tree.json").write_bytes(tree_manifest_bytes)
        (evidence_stage / "evidence.json").write_bytes(evidence_bytes)
        (evidence_stage / "installed-tree.json").chmod(0o444)
        (evidence_stage / "evidence.json").chmod(0o444)
        # macOS requires write permission on the root of a non-empty directory
        # while it is renamed. The contents stay read-only, and the root is
        # returned to its evidence-bound 0555 mode immediately afterward.
        sdk_stage.chmod(0o755)
        require_anchored_parent(sdk_parent, sdk_parent_descriptor, "SDK parent")
        require_anchored_parent(
            evidence_parent,
            evidence_parent_descriptor,
            "SDK evidence parent",
        )
        previous_mask = signal.pthread_sigmask(
            signal.SIG_BLOCK,
            interrupted_signals,
        )
        try:
            os.rename(
                f"{sdk_stage_parent.name}/{sdk_stage.name}",
                sdk_root.name,
                src_dir_fd=sdk_parent_descriptor,
                dst_dir_fd=sdk_parent_descriptor,
            )
            sdk_published = True
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
        sdk_root.chmod(0o555)
        previous_mask = signal.pthread_sigmask(
            signal.SIG_BLOCK,
            interrupted_signals,
        )
        try:
            os.rename(
                f"{evidence_stage_parent.name}/{evidence_stage.name}",
                evidence_root.name,
                src_dir_fd=evidence_parent_descriptor,
                dst_dir_fd=evidence_parent_descriptor,
            )
            evidence_published = True
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, previous_mask)
        evidence_root.chmod(0o555)
        require_anchored_parent(sdk_parent, sdk_parent_descriptor, "SDK parent")
        require_anchored_parent(
            evidence_parent,
            evidence_parent_descriptor,
            "SDK evidence parent",
        )
        success = True
    finally:
        for candidate in interrupted_signals:
            if candidate in previous_signal_handlers:
                signal.signal(candidate, signal.SIG_IGN)
        try:
            if not success:
                if evidence_published:
                    remove_tree_at(
                        evidence_parent_descriptor,
                        evidence_root.name,
                    )
                if sdk_published:
                    remove_tree_at(sdk_parent_descriptor, sdk_root.name)
            if sdk_stage_parent is not None:
                remove_tree_at(
                    sdk_parent_descriptor,
                    sdk_stage_parent.name,
                )
            if evidence_stage_parent is not None:
                remove_tree_at(
                    evidence_parent_descriptor,
                    evidence_stage_parent.name,
                )
        finally:
            for candidate, previous in previous_signal_handlers.items():
                signal.signal(candidate, previous)
            os.close(evidence_parent_descriptor)
            os.close(sdk_parent_descriptor)


def command_validate_evidence(lock_path: Path, evidence_path: Path) -> None:
    lock, _, lock_sha256 = validate_lock(lock_path)
    require_host(lock)
    raw = read_regular_file(evidence_path, MAX_EVIDENCE_BYTES, "SDK evidence")
    evidence = parse_json_bytes(raw, "SDK evidence")
    validate_evidence_structure(lock, lock_sha256, evidence)


def command_verify(lock_path: Path, sdk_root: Path, evidence_root: Path) -> None:
    lock, _, lock_sha256 = validate_lock(lock_path)
    require_host(lock)
    if not evidence_root.is_dir() or evidence_root.is_symlink():
        fail("SDK evidence root must be an existing non-symlink directory")
    evidence_path = evidence_root / "evidence.json"
    manifest_path = evidence_root / "installed-tree.json"
    evidence_raw = read_regular_file(evidence_path, MAX_EVIDENCE_BYTES, "SDK evidence")
    manifest_raw = read_regular_file(
        manifest_path, 64 * 1024 * 1024, "SDK installed-tree manifest"
    )
    evidence = parse_json_bytes(evidence_raw, "SDK evidence")
    validate_evidence_structure(lock, lock_sha256, evidence)
    for package in lock["packages"]:
        verify_package_metadata(package, sdk_root)
    actual_records, _ = canonical_tree(sdk_root)
    expected_manifest = parse_json_bytes(b'{"records":' + manifest_raw.rstrip() + b'}', "SDK tree wrapper")[
        "records"
    ]
    if not isinstance(expected_manifest, list) or actual_records != expected_manifest:
        fail("Installed SDK tree differs from its captured manifest")
    manifest_sha256 = hashlib.sha256(manifest_raw).hexdigest()
    if manifest_sha256 != evidence["installedTreeManifestSha256"]:
        fail("Installed SDK tree-manifest digest differs from evidence")
    expected = expected_evidence(lock, lock_sha256, sdk_root, manifest_sha256)
    if evidence != expected:
        fail("Installed SDK tree or package digest differs from evidence")


def parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)
    for command in ("validate-lock", "download-plan"):
        subparser = subparsers.add_parser(command)
        subparser.add_argument("lock", type=Path)
    install = subparsers.add_parser("install")
    install.add_argument("lock", type=Path)
    install.add_argument("sdk_root", type=Path)
    install.add_argument("archive_dir", type=Path)
    install.add_argument("evidence_root", type=Path)
    evidence = subparsers.add_parser("validate-evidence")
    evidence.add_argument("lock", type=Path)
    evidence.add_argument("evidence", type=Path)
    verify = subparsers.add_parser("verify")
    verify.add_argument("lock", type=Path)
    verify.add_argument("sdk_root", type=Path)
    verify.add_argument("evidence_root", type=Path)
    return parser.parse_args()


def main() -> int:
    arguments = parse_arguments()
    if arguments.command == "validate-lock":
        _, _, digest = validate_lock(arguments.lock)
        print(digest)
    elif arguments.command == "download-plan":
        command_download_plan(arguments.lock)
    elif arguments.command == "install":
        command_install(
            arguments.lock,
            arguments.sdk_root,
            arguments.archive_dir,
            arguments.evidence_root,
        )
    elif arguments.command == "validate-evidence":
        command_validate_evidence(arguments.lock, arguments.evidence)
    elif arguments.command == "verify":
        command_verify(arguments.lock, arguments.sdk_root, arguments.evidence_root)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except ReleaseSdkError as error:
        print(f"[android-release-sdk][error] {error}", file=sys.stderr)
        raise SystemExit(1) from None
