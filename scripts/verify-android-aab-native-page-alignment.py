#!/usr/bin/env python3
"""Fail closed unless every native library in an Android App Bundle is 16 KiB safe."""

from __future__ import annotations

import hashlib
import os
from pathlib import Path
import re
import stat
import struct
import sys
import zipfile


MAX_AAB_BYTES = 250 * 1024 * 1024
MAX_ARCHIVE_ENTRIES = 10_000
MAX_NATIVE_LIBRARIES = 512
MAX_NATIVE_LIBRARY_BYTES = 128 * 1024 * 1024
MAX_TOTAL_NATIVE_BYTES = 512 * 1024 * 1024
REQUIRED_PAGE_ALIGNMENT = 16 * 1024
EXPECTED_MACHINES = {
    "armeabi-v7a": 40,
    "arm64-v8a": 183,
    "x86": 3,
    "x86_64": 62,
}
NATIVE_ENTRY = re.compile(r"^[A-Za-z0-9_.-]+/lib/([^/]+)/([^/]+\.so)$")


class VerificationError(Exception):
    pass


def fail(message: str) -> None:
    print(f"[android-aab-native-alignment][error] {message}", file=sys.stderr)
    raise SystemExit(1)


def require_safe_regular_file(raw_path: str) -> Path:
    path = Path(os.path.abspath(raw_path))
    current = Path(path.anchor)
    for component in path.parts[1:]:
        current /= component
        if os.path.lexists(current) and stat.S_ISLNK(os.lstat(current).st_mode):
            raise VerificationError("AAB path must not traverse a symlink component.")
    try:
        metadata = path.stat()
    except OSError as error:
        raise VerificationError(f"AAB could not be opened: {error}") from error
    if not stat.S_ISREG(metadata.st_mode) or metadata.st_size <= 0:
        raise VerificationError("AAB must be a non-empty regular file.")
    if metadata.st_size > MAX_AAB_BYTES:
        raise VerificationError(f"AAB exceeds the {MAX_AAB_BYTES}-byte limit.")
    return path


def unpack_from(fmt: str, data: bytes, offset: int, label: str) -> tuple[int, ...]:
    size = struct.calcsize(fmt)
    if offset < 0 or offset + size > len(data):
        raise VerificationError(f"{label} is truncated.")
    return struct.unpack_from(fmt, data, offset)


def verify_elf(data: bytes, entry_name: str, abi: str) -> None:
    if len(data) < 16 or data[:4] != b"\x7fELF":
        raise VerificationError(f"{entry_name} is not an ELF shared library.")
    elf_class = data[4]
    elf_data = data[5]
    if data[6] != 1:
        raise VerificationError(f"{entry_name} has an unsupported ELF identification version.")
    if elf_data == 1:
        endian = "<"
    elif elf_data == 2:
        endian = ">"
    else:
        raise VerificationError(f"{entry_name} has an unsupported ELF byte order.")

    elf_type, machine = unpack_from(endian + "HH", data, 16, f"{entry_name} ELF header")
    if elf_type != 3:
        raise VerificationError(f"{entry_name} is not an ET_DYN shared object.")
    expected_machine = EXPECTED_MACHINES[abi]
    if machine != expected_machine:
        raise VerificationError(
            f"{entry_name} machine {machine} does not match {abi} ({expected_machine})."
        )

    if elf_class == 1:
        minimum_header_size = 52
        minimum_program_header_size = 32
        program_offset = unpack_from(endian + "I", data, 28, f"{entry_name} ELF header")[0]
        header_size, program_entry_size, program_count = unpack_from(
            endian + "HHH", data, 40, f"{entry_name} ELF header"
        )

        def program_values(offset: int) -> tuple[int, int, int, int, int, int]:
            program_type = unpack_from(endian + "I", data, offset, entry_name)[0]
            file_offset, virtual_address = unpack_from(
                endian + "II", data, offset + 4, entry_name
            )
            file_size, memory_size = unpack_from(
                endian + "II", data, offset + 16, entry_name
            )
            alignment = unpack_from(endian + "I", data, offset + 28, entry_name)[0]
            return program_type, file_offset, virtual_address, file_size, memory_size, alignment

    elif elf_class == 2:
        minimum_header_size = 64
        minimum_program_header_size = 56
        program_offset = unpack_from(endian + "Q", data, 32, f"{entry_name} ELF header")[0]
        header_size, program_entry_size, program_count = unpack_from(
            endian + "HHH", data, 52, f"{entry_name} ELF header"
        )

        def program_values(offset: int) -> tuple[int, int, int, int, int, int]:
            program_type = unpack_from(endian + "I", data, offset, entry_name)[0]
            file_offset, virtual_address = unpack_from(
                endian + "QQ", data, offset + 8, entry_name
            )
            file_size, memory_size, alignment = unpack_from(
                endian + "QQQ", data, offset + 32, entry_name
            )
            return program_type, file_offset, virtual_address, file_size, memory_size, alignment

    else:
        raise VerificationError(f"{entry_name} has an unsupported ELF class.")

    if header_size < minimum_header_size or header_size > len(data):
        raise VerificationError(f"{entry_name} has an invalid ELF header size.")
    if program_count == 0 or program_count == 0xFFFF or program_count > 1024:
        raise VerificationError(f"{entry_name} has an unsupported program-header count.")
    if program_entry_size < minimum_program_header_size:
        raise VerificationError(f"{entry_name} has a truncated program-header entry size.")
    program_table_end = program_offset + program_entry_size * program_count
    if program_offset < header_size or program_table_end > len(data):
        raise VerificationError(f"{entry_name} program-header table is out of bounds.")

    load_count = 0
    for index in range(program_count):
        offset = program_offset + index * program_entry_size
        (
            program_type,
            file_offset,
            virtual_address,
            file_size,
            memory_size,
            alignment,
        ) = program_values(offset)
        if program_type != 1:
            continue
        load_count += 1
        if file_size > memory_size or file_offset + file_size > len(data):
            raise VerificationError(f"{entry_name} has an out-of-bounds PT_LOAD segment.")
        if alignment < REQUIRED_PAGE_ALIGNMENT or alignment & (alignment - 1):
            raise VerificationError(
                f"{entry_name} PT_LOAD alignment {alignment} is below or incompatible with "
                f"{REQUIRED_PAGE_ALIGNMENT}."
            )
        if (virtual_address - file_offset) % alignment != 0:
            raise VerificationError(f"{entry_name} has a misaligned PT_LOAD offset/address pair.")
    if load_count == 0:
        raise VerificationError(f"{entry_name} has no PT_LOAD segment.")


def verify_bundle(path: Path) -> tuple[int, str]:
    digest = hashlib.sha256()
    with path.open("rb") as artifact:
        for chunk in iter(lambda: artifact.read(1024 * 1024), b""):
            digest.update(chunk)

    try:
        with zipfile.ZipFile(path) as archive:
            infos = archive.infolist()
            if not infos or len(infos) > MAX_ARCHIVE_ENTRIES:
                raise VerificationError("AAB archive entry count is empty or exceeds the limit.")
            names = [info.filename for info in infos]
            if len(names) != len(set(names)):
                raise VerificationError("AAB contains duplicate archive entry names.")
            for name in names:
                components = name.split("/")
                if name.startswith("/") or "" in components[:-1] or any(
                    component in {".", ".."} for component in components
                ):
                    raise VerificationError(f"AAB contains an unsafe archive path: {name}")

            corrupt_entry = archive.testzip()
            if corrupt_entry is not None:
                raise VerificationError(f"AAB CRC validation failed for {corrupt_entry}.")

            native_entries: list[tuple[zipfile.ZipInfo, str, str]] = []
            total_native_bytes = 0
            for info in infos:
                match = NATIVE_ENTRY.fullmatch(info.filename)
                if not match:
                    continue
                abi, library_name = match.groups()
                if abi not in EXPECTED_MACHINES:
                    raise VerificationError(f"Unsupported native ABI in AAB: {abi}")
                if info.flag_bits & 0x1:
                    raise VerificationError(f"Encrypted native library is not allowed: {info.filename}")
                if info.file_size <= 0 or info.file_size > MAX_NATIVE_LIBRARY_BYTES:
                    raise VerificationError(f"Native library size is invalid: {info.filename}")
                total_native_bytes += info.file_size
                native_entries.append((info, abi, library_name))

            if not native_entries or len(native_entries) > MAX_NATIVE_LIBRARIES:
                raise VerificationError("AAB native-library count is empty or exceeds the limit.")
            if total_native_bytes > MAX_TOTAL_NATIVE_BYTES:
                raise VerificationError("AAB native libraries exceed the total size limit.")

            libraries_by_abi: dict[str, set[str]] = {abi: set() for abi in EXPECTED_MACHINES}
            for info, abi, library_name in native_entries:
                libraries_by_abi[abi].add(library_name)
                verify_elf(archive.read(info), info.filename, abi)

            missing_abis = sorted(abi for abi, libraries in libraries_by_abi.items() if not libraries)
            if missing_abis:
                raise VerificationError(f"AAB is missing required native ABIs: {', '.join(missing_abis)}")
            reference_abi = sorted(libraries_by_abi)[0]
            reference_libraries = libraries_by_abi[reference_abi]
            for abi, libraries in libraries_by_abi.items():
                if libraries != reference_libraries:
                    raise VerificationError(
                        f"Native library set for {abi} differs from {reference_abi}."
                    )
    except (OSError, zipfile.BadZipFile, zipfile.LargeZipFile) as error:
        raise VerificationError(f"AAB is not a readable bounded ZIP archive: {error}") from error

    return len(native_entries), digest.hexdigest()


def main() -> None:
    if len(sys.argv) != 2:
        print(
            "Usage: verify-android-aab-native-page-alignment.py <release.aab>",
            file=sys.stderr,
        )
        raise SystemExit(2)
    try:
        path = require_safe_regular_file(sys.argv[1])
        library_count, digest = verify_bundle(path)
    except VerificationError as error:
        fail(str(error))
    print(
        "[android-aab-native-alignment] passed "
        f"sha256={digest} libraries={library_count} "
        f"abis={','.join(sorted(EXPECTED_MACHINES))} alignment={REQUIRED_PAGE_ALIGNMENT}"
    )


if __name__ == "__main__":
    main()
