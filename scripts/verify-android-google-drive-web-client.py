#!/usr/bin/env python3
"""Fail closed unless an Android App Bundle or APK carries the pinned Google Drive web client.

Google Drive keeps every wallet backup in the appDataFolder of the Google Cloud project that owns
the app's OAuth clients. Releases 2.4.1 through 4.2.0 all compiled the same web client id
(`WEB_CLIENT_ID_RELEASE`), so a release built with a different, blank or malformed value would sign
upgrading users in against another project, or not at all, and their existing backups would look
gone. The expected id is pinned only by its SHA-256 in
`.github/release/android-google-drive-web-client-sha256.txt`; this script never prints the id.
"""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import stat
import struct
import sys
import zipfile


MAX_ARTIFACT_BYTES = 250 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 10_000
MAX_DEX_FILES = 64
MAX_DEX_BYTES = 64 * 1024 * 1024
MAX_TOTAL_DEX_BYTES = 512 * 1024 * 1024
MAX_DEX_STRINGS = 1_000_000
MAX_PIN_BYTES = 4096
DEX_HEADER_SIZE = 0x70
DEX_ENDIAN_TAG = 0x12345678
DEX_MAGIC = re.compile(rb"dex\n(0[0-9]{2})\x00")
SUPPORTED_DEX_VERSIONS = {b"035", b"037", b"038", b"039", b"040"}
APK_DEX_ENTRY = re.compile(r"^classes[0-9]*\.dex$")
AAB_DEX_ENTRY = re.compile(r"^[A-Za-z0-9_.-]+/dex/classes[0-9]*\.dex$")
CLIENT_ID = re.compile(rb"([0-9]{1,20})-[0-9a-z]{1,64}\.apps\.googleusercontent\.com")
CLIENT_ID_SUFFIX = b".apps.googleusercontent.com"
PIN_PATH = (
    Path(__file__).resolve().parent.parent
    / ".github"
    / "release"
    / "android-google-drive-web-client-sha256.txt"
)


class VerificationError(Exception):
    pass


def fail(message: str) -> None:
    print(f"[android-google-drive-web-client][error] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_safe_regular_file(raw_path: str, label: str, maximum_bytes: int) -> Path:
    path = Path(os.path.abspath(raw_path))
    current = Path(path.anchor)
    for component in path.parts[1:]:
        current /= component
        if os.path.lexists(current) and stat.S_ISLNK(os.lstat(current).st_mode):
            raise VerificationError(f"{label} path must not traverse a symlink component.")
    try:
        metadata = path.stat()
    except OSError as error:
        raise VerificationError(f"{label} could not be opened: {error}") from error
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_size <= 0:
        raise VerificationError(f"{label} must be a non-empty regular file.")
    if metadata.st_size > maximum_bytes:
        raise VerificationError(f"{label} exceeds the {maximum_bytes}-byte limit.")
    return path


def read_pinned_digest() -> str:
    path = require_safe_regular_file(str(PIN_PATH), "Google Drive web client pin file", MAX_PIN_BYTES)
    try:
        text = path.read_bytes().decode("ascii")
    except UnicodeDecodeError as error:
        raise VerificationError("Google Drive web client pin is malformed: not ASCII.") from error
    values = [
        line.strip()
        for line in text.splitlines()
        if line.strip() and not line.lstrip().startswith("#")
    ]
    if len(values) != 1 or not re.fullmatch(r"[0-9a-f]{64}", values[0]):
        raise VerificationError(
            "Google Drive web client pin is malformed: expected exactly one lowercase SHA-256."
        )
    return values[0]


def skip_uleb128(data: bytes, offset: int, label: str) -> int:
    for index in range(5):
        if offset + index >= len(data):
            raise VerificationError(f"{label} has a string length past the end of the file.")
        if data[offset + index] & 0x80 == 0:
            return offset + index + 1
    raise VerificationError(f"{label} has an oversized string length.")


def dex_strings(data: bytes, label: str) -> list[bytes]:
    magic = DEX_MAGIC.fullmatch(data[:8])
    if len(data) < DEX_HEADER_SIZE or not magic:
        raise VerificationError(f"{label} is not a dex file.")
    if magic.group(1) not in SUPPORTED_DEX_VERSIONS:
        raise VerificationError(f"{label} has an unsupported dex version {magic.group(1).decode()}.")
    file_size, header_size, endian_tag = struct.unpack_from("<III", data, 0x20)
    if file_size != len(data):
        raise VerificationError(f"{label} declared size does not match its entry size.")
    if header_size != DEX_HEADER_SIZE or endian_tag != DEX_ENDIAN_TAG:
        raise VerificationError(f"{label} has an unsupported dex header.")
    string_count, string_ids_offset = struct.unpack_from("<II", data, 0x38)
    if string_count > MAX_DEX_STRINGS:
        raise VerificationError(f"{label} declares too many strings.")
    if string_count and (
        string_ids_offset < DEX_HEADER_SIZE or string_ids_offset + 4 * string_count > len(data)
    ):
        raise VerificationError(f"{label} string table is out of bounds.")

    strings = []
    for index in range(string_count):
        data_offset = struct.unpack_from("<I", data, string_ids_offset + 4 * index)[0]
        if data_offset < DEX_HEADER_SIZE or data_offset >= len(data):
            raise VerificationError(f"{label} string {index} is out of bounds.")
        start = skip_uleb128(data, data_offset, label)
        end = data.find(b"\x00", start)
        if end < 0:
            raise VerificationError(f"{label} string {index} is unterminated.")
        strings.append(data[start:end])
    return strings


def dex_entries(archive: zipfile.ZipFile) -> list[zipfile.ZipInfo]:
    infos = archive.infolist()
    if not infos or len(infos) > MAX_ARCHIVE_ENTRIES:
        raise VerificationError("Artifact archive entry count is empty or exceeds the limit.")
    names = [info.filename for info in infos]
    if len(names) != len(set(names)):
        raise VerificationError("Artifact contains duplicate archive entry names.")
    for name in names:
        components = name.split("/")
        if name.startswith("/") or "\\" in name or "" in components[:-1] or any(
            component in {".", ".."} for component in components
        ):
            raise VerificationError(f"Artifact contains an unsafe archive path: {name}")

    apk_entries = [info for info in infos if APK_DEX_ENTRY.fullmatch(info.filename)]
    aab_entries = [info for info in infos if AAB_DEX_ENTRY.fullmatch(info.filename)]
    if apk_entries and aab_entries:
        raise VerificationError("Artifact mixes APK and App Bundle dex layouts.")
    entries = apk_entries or aab_entries
    if not entries:
        raise VerificationError("Artifact contains no dex files.")
    if len(entries) > MAX_DEX_FILES:
        raise VerificationError("Artifact contains too many dex files.")
    total_bytes = 0
    for info in entries:
        if info.flag_bits & 0x1:
            raise VerificationError(f"Encrypted dex entry is not allowed: {info.filename}")
        if info.file_size > MAX_DEX_BYTES:
            raise VerificationError(f"{info.filename} exceeds the {MAX_DEX_BYTES}-byte limit.")
        total_bytes += info.file_size
    if total_bytes > MAX_TOTAL_DEX_BYTES:
        raise VerificationError("Artifact dex files exceed the aggregate size limit.")
    return entries


def verify_artifact(path: Path, pinned_digest: str) -> tuple[str, str, list[str]]:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for chunk in iter(lambda: artifact.read(1024 * 1024), b""):
            digest.update(chunk)

    client_ids: dict[bytes, list[str]] = {}
    near_misses = 0
    try:
        with zipfile.ZipFile(path) as archive:
            for info in dex_entries(archive):
                data = archive.read(info)
                if len(data) != info.file_size:
                    raise VerificationError(f"{info.filename} size changed while it was read.")
                for value in dex_strings(data, info.filename):
                    if CLIENT_ID.fullmatch(value):
                        client_ids.setdefault(value, []).append(info.filename)
                    elif CLIENT_ID_SUFFIX in value:
                        near_misses += 1
    except zipfile.BadZipFile as error:
        raise VerificationError(f"Artifact is not a valid zip archive: {error}") from error

    if not client_ids:
        raise VerificationError(
            "No Google OAuth web client id is compiled into the artifact's dex files "
            f"({near_misses} other string(s) mention {CLIENT_ID_SUFFIX.decode()}). "
            "WEB_CLIENT_ID_RELEASE was missing, blank or malformed at build time."
        )
    if len(client_ids) != 1:
        raise VerificationError(
            f"Artifact contains {len(client_ids)} distinct Google OAuth client ids; "
            "expected only the pinned Google Drive web client."
        )
    value, locations = next(iter(client_ids.items()))
    project = CLIENT_ID.fullmatch(value).group(1).decode()
    value_digest = hashlib.sha256(value).hexdigest()
    if value_digest != pinned_digest:
        raise VerificationError(
            f"The compiled Google OAuth web client (project {project}, sha256 {value_digest}) "
            "is not the pinned client that 2.4.1 through 4.2.0 shipped; "
            "set WEB_CLIENT_ID_RELEASE to that client."
        )
    return digest.hexdigest(), project, sorted(set(locations))


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(
            "Usage: python3 scripts/verify-android-google-drive-web-client.py <release.aab|release.apk>",
            file=sys.stderr,
        )
        return 2
    try:
        pinned_digest = read_pinned_digest()
        path = require_safe_regular_file(argv[1], "Artifact", MAX_ARTIFACT_BYTES)
        artifact_digest, project, locations = verify_artifact(path, pinned_digest)
    except VerificationError as error:
        fail(str(error))
    print(
        "[android-google-drive-web-client] artifact sha256="
        + artifact_digest
        + " carries the pinned Google Drive web client (project "
        + project
        + ", sha256 "
        + pinned_digest
        + ") in "
        + ", ".join(locations)
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
