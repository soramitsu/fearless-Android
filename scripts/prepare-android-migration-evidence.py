#!/usr/bin/env python3
from __future__ import annotations

import argparse
import hashlib
import json
import os
import secrets
import stat
import unicodedata
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable


OUTPUT_RELATIVE = "build/reports/android-migration-upload"
DEFAULT_MODULE_SOURCES = (
    ("build/outputs/androidTest-results/connected", "android-test-results"),
    ("build/reports/androidTests/connected", "android-test-reports"),
)
DEFAULT_FIXED_SOURCES = (
    ("build/reports/android-migration-compatibility", "compatibility"),
    ("build/reports/android-emulator-lifecycle", "emulator-lifecycle"),
)
WINDOWS_FORBIDDEN = frozenset('"*/:<>?\\|')
WINDOWS_RESERVED = frozenset(
    {"CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$"}
    | {f"COM{number}" for number in range(1, 10)}
    | {f"LPT{number}" for number in range(1, 10)}
    | {f"COM{number}" for number in ("¹", "²", "³")}
    | {f"LPT{number}" for number in ("¹", "²", "³")}
)
MAX_COMPONENT_BYTES = 108
MAX_RELATIVE_PATH_BYTES = 220
MANIFEST_NAME = "manifest.json"
MANIFEST_SHA256_NAME = "manifest.sha256"
COPY_CHUNK_BYTES = 1024 * 1024


class EvidenceError(RuntimeError):
    pass


@dataclass(frozen=True)
class SourceRoot:
    source: Path
    destination_prefix: tuple[str, ...]


@dataclass(frozen=True)
class FileIdentity:
    device: int
    inode: int
    mode: int
    size: int
    modified_ns: int
    changed_ns: int

    @classmethod
    def from_stat(cls, metadata: os.stat_result) -> "FileIdentity":
        return cls(
            device=metadata.st_dev,
            inode=metadata.st_ino,
            mode=metadata.st_mode,
            size=metadata.st_size,
            modified_ns=metadata.st_mtime_ns,
            changed_ns=metadata.st_ctime_ns,
        )


@dataclass
class SourceHandle:
    source: Path
    source_relative: Path
    destination_prefix: tuple[str, ...]
    descriptor: int
    identity: FileIdentity


@dataclass(frozen=True)
class PlannedFile:
    source_handle: SourceHandle
    source_parts: tuple[str, ...]
    source_relative: str
    staged_relative: Path
    identity: FileIdentity


@dataclass
class CanonicalPathNode:
    spelling: str | None = None
    file_source: str | None = None
    children: dict[str, "CanonicalPathNode"] = field(default_factory=dict)


def _encode_bytes(value: str) -> str:
    return "".join(f"~{byte:02X}" for byte in value.encode("utf-8"))


def _utf8_prefix(value: str, byte_limit: int) -> str:
    result: list[str] = []
    used = 0
    for character in value:
        width = len(character.encode("utf-8"))
        if used + width > byte_limit:
            break
        result.append(character)
        used += width
    return "".join(result)


def portable_component(component: str) -> str:
    if not component:
        raise EvidenceError("empty path components are forbidden")

    trailing_start = len(component.rstrip(" ."))
    encoded_parts: list[str] = []
    for index, character in enumerate(component):
        codepoint = ord(character)
        unsafe = (
            character == "~"
            or character in WINDOWS_FORBIDDEN
            or codepoint < 32
            or codepoint == 127
            or (index == 0 and character == ".")
            or (index >= trailing_start and character in " .")
        )
        encoded_parts.append(_encode_bytes(character) if unsafe else character)

    encoded = "".join(encoded_parts)
    stem = component.split(".", 1)[0].rstrip(" .").upper()
    if stem in WINDOWS_RESERVED:
        encoded = _encode_bytes(component[0]) + encoded[1:]

    if len(encoded.encode("utf-8")) > MAX_COMPONENT_BYTES:
        digest_suffix = "~H" + hashlib.sha256(component.encode("utf-8")).hexdigest()
        prefix_limit = MAX_COMPONENT_BYTES - len(digest_suffix)
        encoded = _utf8_prefix(encoded, prefix_limit) + digest_suffix

    _validate_portable_component(encoded)
    return encoded


def _validate_portable_component(component: str) -> None:
    if not component or component in {".", ".."}:
        raise EvidenceError(f"unsafe staged component: {component!r}")
    if component.startswith("."):
        raise EvidenceError(f"staged components must not be hidden: {component!r}")
    if component.endswith((" ", ".")):
        raise EvidenceError(f"staged component has a forbidden suffix: {component!r}")
    if any(
        character in WINDOWS_FORBIDDEN
        or ord(character) < 32
        or ord(character) == 127
        for character in component
    ):
        raise EvidenceError(f"staged component contains a forbidden character: {component!r}")
    if component.split(".", 1)[0].rstrip(" .").upper() in WINDOWS_RESERVED:
        raise EvidenceError(f"staged component is a reserved Windows name: {component!r}")
    if len(component.encode("utf-8")) > MAX_COMPONENT_BYTES:
        raise EvidenceError(f"staged component exceeds {MAX_COMPONENT_BYTES} bytes")


def portable_relative_path(parts: Iterable[str]) -> Path:
    encoded = tuple(portable_component(part) for part in parts)
    if not encoded:
        raise EvidenceError("empty relative paths are forbidden")
    result = Path(*encoded)
    if result.is_absolute() or ".." in result.parts:
        raise EvidenceError(f"portable path escaped staging root: {result}")
    return result


def _canonical_component(component: str) -> str:
    return unicodedata.normalize("NFC", component).casefold()


def _register_portable_path(
    root: CanonicalPathNode,
    path: Path,
    source_relative: str,
) -> None:
    node = root
    for component in path.parts:
        if node.file_source is not None:
            raise EvidenceError(
                "portable evidence file/directory prefix collision: "
                f"{node.file_source!r} conflicts with {source_relative!r}"
            )
        canonical = _canonical_component(component)
        child = node.children.get(canonical)
        if child is None:
            child = CanonicalPathNode(spelling=component)
            node.children[canonical] = child
        elif child.spelling != component:
            raise EvidenceError(
                "case/Unicode-normalized evidence path collision: "
                f"{child.spelling!r} and {component!r} while staging "
                f"{source_relative!r}"
            )
        node = child
    if node.file_source is not None:
        raise EvidenceError(
            "portable evidence path collision: "
            f"{node.file_source!r} and {source_relative!r} -> {path.as_posix()!r}"
        )
    if node.children:
        raise EvidenceError(
            "portable evidence file/directory prefix collision: "
            f"{source_relative!r} conflicts with an existing staged child"
        )
    node.file_source = source_relative


def _lexical_absolute(path: Path) -> Path:
    return Path(os.path.abspath(os.fspath(path)))


def _relative_to_project(path: Path, project_root: Path, label: str) -> Path:
    try:
        return path.relative_to(project_root)
    except ValueError as error:
        raise EvidenceError(f"{label} escapes project root: {path}") from error


def _canonical_project_root(path: Path) -> tuple[Path, Path, tuple[int, int]]:
    lexical = _lexical_absolute(path)
    try:
        lexical_metadata = lexical.lstat()
    except OSError as error:
        raise EvidenceError(f"project root is not accessible: {lexical}: {error}") from error
    if stat.S_ISLNK(lexical_metadata.st_mode):
        raise EvidenceError(f"project root must not be a symlink: {lexical}")
    try:
        canonical = lexical.resolve(strict=True)
    except OSError as error:
        raise EvidenceError(f"project root cannot be resolved: {lexical}: {error}") from error
    if not canonical.is_dir():
        raise EvidenceError(f"project root must be a directory: {canonical}")
    canonical_metadata = canonical.stat()
    if (
        lexical_metadata.st_dev,
        lexical_metadata.st_ino,
    ) != (
        canonical_metadata.st_dev,
        canonical_metadata.st_ino,
    ):
        raise EvidenceError("project root changed during canonicalization")
    return (
        lexical,
        canonical,
        (canonical_metadata.st_dev, canonical_metadata.st_ino),
    )


def _canonicalize_source_contracts(
    sources: list[SourceRoot],
    lexical_project_root: Path,
    canonical_project_root: Path,
) -> list[SourceRoot]:
    canonical_sources: list[SourceRoot] = []
    for source in sources:
        absolute = _lexical_absolute(source.source)
        try:
            relative = absolute.relative_to(lexical_project_root)
        except ValueError:
            try:
                relative = absolute.relative_to(canonical_project_root)
            except ValueError as error:
                raise EvidenceError(
                    f"evidence source escapes project root: {absolute}"
                ) from error
        canonical_sources.append(
            SourceRoot(
                source=canonical_project_root / relative,
                destination_prefix=source.destination_prefix,
            )
        )
    return canonical_sources


def _source_contract_keys(
    sources: list[SourceRoot],
    project_root: Path,
) -> list[tuple[str, tuple[str, ...]]]:
    return sorted(
        (
            _relative_to_project(
                source.source,
                project_root,
                "evidence source",
            ).as_posix(),
            source.destination_prefix,
        )
        for source in sources
    )


def _directory_flags() -> int:
    required = ("O_DIRECTORY", "O_NOFOLLOW")
    missing = [name for name in required if not hasattr(os, name)]
    if missing:
        raise EvidenceError(
            "platform lacks required no-follow directory flags: "
            + ", ".join(missing)
        )
    return (
        os.O_RDONLY
        | os.O_DIRECTORY
        | os.O_NOFOLLOW
        | getattr(os, "O_CLOEXEC", 0)
    )


def _file_read_flags() -> int:
    if not hasattr(os, "O_NOFOLLOW"):
        raise EvidenceError("platform lacks required O_NOFOLLOW support")
    return os.O_RDONLY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)


def _file_write_flags() -> int:
    if not hasattr(os, "O_NOFOLLOW"):
        raise EvidenceError("platform lacks required O_NOFOLLOW support")
    return (
        os.O_WRONLY
        | os.O_CREAT
        | os.O_EXCL
        | os.O_NOFOLLOW
        | getattr(os, "O_CLOEXEC", 0)
    )


def _open_absolute_directory_no_follow(path: Path) -> int:
    path = _lexical_absolute(path)
    descriptor = os.open("/", _directory_flags())
    try:
        for component in path.parts[1:]:
            next_descriptor = os.open(
                component,
                _directory_flags(),
                dir_fd=descriptor,
            )
            os.close(descriptor)
            descriptor = next_descriptor
        if not stat.S_ISDIR(os.fstat(descriptor).st_mode):
            raise EvidenceError(f"path is not a directory: {path}")
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def _open_directory_chain(
    base_descriptor: int,
    parts: Iterable[str],
    *,
    create: bool,
) -> int:
    descriptor = os.dup(base_descriptor)
    try:
        for component in parts:
            if not component or component in {".", ".."} or "/" in component:
                raise EvidenceError(f"unsafe directory component: {component!r}")
            try:
                next_descriptor = os.open(
                    component,
                    _directory_flags(),
                    dir_fd=descriptor,
                )
            except FileNotFoundError:
                if not create:
                    raise
                os.mkdir(component, mode=0o700, dir_fd=descriptor)
                next_descriptor = os.open(
                    component,
                    _directory_flags(),
                    dir_fd=descriptor,
                )
            try:
                if create:
                    os.fchmod(next_descriptor, 0o700)
            except BaseException:
                os.close(next_descriptor)
                raise
            os.close(descriptor)
            descriptor = next_descriptor
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def discover_default_sources(
    project_root: Path,
    project_descriptor: int,
) -> list[SourceRoot]:
    sources: list[SourceRoot] = []
    categories = {
        Path(suffix).relative_to("build"): category
        for suffix, category in DEFAULT_MODULE_SOURCES
    }

    def open_candidate(
        base_descriptor: int,
        parts: tuple[str, ...],
        location: str,
    ) -> bool:
        try:
            candidate_descriptor = _open_directory_chain(
                base_descriptor,
                parts,
                create=False,
            )
        except FileNotFoundError:
            return False
        except OSError as error:
            raise EvidenceError(
                f"failed to inspect evidence root {location}: {error}"
            ) from error
        os.close(candidate_descriptor)
        return True

    def discover_directory(
        directory_descriptor: int,
        owner_parts: tuple[str, ...],
    ) -> None:
        before = FileIdentity.from_stat(os.fstat(directory_descriptor))
        try:
            directory_names = sorted(os.listdir(directory_descriptor))
        except OSError as error:
            location = "/".join(owner_parts) or "."
            raise EvidenceError(
                f"failed to discover evidence under {location}: {error}"
            ) from error
        for name in directory_names:
            if name == ".git":
                continue
            try:
                metadata = os.stat(
                    name,
                    dir_fd=directory_descriptor,
                    follow_symlinks=False,
                )
            except OSError as error:
                raise EvidenceError(
                    f"failed to inspect discovery entry {'/'.join((*owner_parts, name))}: "
                    f"{error}"
                ) from error
            if stat.S_ISLNK(metadata.st_mode):
                if name == "build":
                    raise EvidenceError(
                        "evidence discovery build root must not be a symlink: "
                        + "/".join((*owner_parts, name))
                    )
                continue
            if not stat.S_ISDIR(metadata.st_mode):
                continue
            child_descriptor = os.open(
                name,
                _directory_flags(),
                dir_fd=directory_descriptor,
            )
            try:
                child_identity = FileIdentity.from_stat(
                    os.fstat(child_descriptor)
                )
                if child_identity != FileIdentity.from_stat(metadata):
                    raise EvidenceError(
                        "discovery directory changed while opening: "
                        + "/".join((*owner_parts, name))
                    )
                if name == "build":
                    for suffix, category in sorted(
                        categories.items(),
                        key=lambda item: os.fspath(item[0]),
                    ):
                        candidate_parts = suffix.parts
                        location_parts = (*owner_parts, "build", *candidate_parts)
                        if open_candidate(
                            child_descriptor,
                            candidate_parts,
                            "/".join(location_parts),
                        ):
                            sources.append(
                                SourceRoot(
                                    source=project_root / Path(*location_parts),
                                    destination_prefix=(category, *owner_parts),
                                )
                            )
                else:
                    discover_directory(
                        child_descriptor,
                        (*owner_parts, name),
                    )
                if (
                    FileIdentity.from_stat(os.fstat(child_descriptor))
                    != child_identity
                ):
                    raise EvidenceError(
                        "evidence discovery directory changed while probing: "
                        + "/".join((*owner_parts, name))
                    )
            finally:
                os.close(child_descriptor)
        after = FileIdentity.from_stat(os.fstat(directory_descriptor))
        if after != before:
            location = "/".join(owner_parts) or "."
            raise EvidenceError(
                f"evidence discovery directory changed while traversing: {location}"
            )

    discover_directory(project_descriptor, ())
    for relative, category in DEFAULT_FIXED_SOURCES:
        relative_parts = Path(relative).parts
        if open_candidate(
            project_descriptor,
            relative_parts,
            relative,
        ):
            sources.append(
                SourceRoot(
                    source=project_root / relative,
                    destination_prefix=(category,),
                )
            )
    return sorted(
        sources,
        key=lambda source: (
            os.fspath(source.source),
            source.destination_prefix,
        ),
    )


def _walk_regular_files_fd(
    directory_descriptor: int,
    relative_parts: tuple[str, ...] = (),
) -> list[tuple[tuple[str, ...], FileIdentity]]:
    before = FileIdentity.from_stat(os.fstat(directory_descriptor))
    try:
        names = sorted(os.listdir(directory_descriptor))
    except OSError as error:
        location = "/".join(relative_parts) or "."
        raise EvidenceError(f"failed to enumerate evidence directory {location}: {error}") from error

    files: list[tuple[tuple[str, ...], FileIdentity]] = []
    for name in names:
        child_parts = (*relative_parts, name)
        location = "/".join(child_parts)
        try:
            metadata = os.stat(
                name,
                dir_fd=directory_descriptor,
                follow_symlinks=False,
            )
        except OSError as error:
            raise EvidenceError(f"failed to inspect evidence entry {location}: {error}") from error
        mode = metadata.st_mode
        if stat.S_ISLNK(mode):
            raise EvidenceError(f"evidence entries must not be symlinks: {location}")
        if stat.S_ISDIR(mode):
            try:
                child_descriptor = os.open(
                    name,
                    _directory_flags(),
                    dir_fd=directory_descriptor,
                )
            except OSError as error:
                raise EvidenceError(
                    f"failed to open evidence directory {location}: {error}"
                ) from error
            try:
                if FileIdentity.from_stat(os.fstat(child_descriptor)) != FileIdentity.from_stat(metadata):
                    raise EvidenceError(
                        f"evidence directory changed while opening: {location}"
                    )
                files.extend(
                    _walk_regular_files_fd(child_descriptor, child_parts)
                )
            finally:
                os.close(child_descriptor)
        elif stat.S_ISREG(mode):
            files.append((child_parts, FileIdentity.from_stat(metadata)))
        else:
            raise EvidenceError(f"evidence files must be regular files: {location}")

    after = FileIdentity.from_stat(os.fstat(directory_descriptor))
    if after != before:
        location = "/".join(relative_parts) or "."
        raise EvidenceError(f"evidence directory changed while traversing: {location}")
    return files


def _open_source_handles_and_plan(
    project_root: Path,
    output: Path,
    sources: list[SourceRoot],
    project_descriptor: int,
) -> tuple[list[PlannedFile], list[SourceHandle]]:
    if not sources:
        raise EvidenceError("no Android migration evidence roots were found")

    handles: list[SourceHandle] = []
    planned: list[PlannedFile] = []
    canonical_root = CanonicalPathNode()
    _register_portable_path(canonical_root, Path(MANIFEST_NAME), "<generated manifest>")
    _register_portable_path(
        canonical_root,
        Path(MANIFEST_SHA256_NAME),
        "<generated manifest checksum>",
    )
    seen_source_contracts: set[tuple[str, tuple[str, ...]]] = set()
    try:
        for source_contract in sources:
            source = _lexical_absolute(source_contract.source)
            source_relative_path = _relative_to_project(
                source,
                project_root,
                "evidence source",
            )
            contract_key = (
                source_relative_path.as_posix(),
                source_contract.destination_prefix,
            )
            if contract_key in seen_source_contracts:
                raise EvidenceError(
                    f"duplicate evidence source contract: {source_relative_path}"
                )
            seen_source_contracts.add(contract_key)
            if (
                source == output
                or output.is_relative_to(source)
                or source.is_relative_to(output)
            ):
                raise EvidenceError(f"staging output overlaps evidence source: {source}")
            try:
                source_descriptor = _open_directory_chain(
                    project_descriptor,
                    source_relative_path.parts,
                    create=False,
                )
            except OSError as error:
                raise EvidenceError(
                    "evidence source must be a directory that is readable and non-symlink: "
                    f"{source_relative_path}: {error}"
                ) from error
            handle = SourceHandle(
                source=source,
                source_relative=source_relative_path,
                destination_prefix=source_contract.destination_prefix,
                descriptor=source_descriptor,
                identity=FileIdentity.from_stat(os.fstat(source_descriptor)),
            )
            handles.append(handle)

            encoded_prefix = portable_relative_path(handle.destination_prefix)
            for source_parts, identity in _walk_regular_files_fd(source_descriptor):
                staged_relative = encoded_prefix / portable_relative_path(source_parts)
                if staged_relative.is_absolute() or ".." in staged_relative.parts:
                    raise EvidenceError(
                        f"portable evidence path escaped staging root: {staged_relative}"
                    )
                if len(staged_relative.as_posix().encode("utf-8")) > MAX_RELATIVE_PATH_BYTES:
                    raise EvidenceError(
                        f"portable evidence path exceeds {MAX_RELATIVE_PATH_BYTES} bytes: "
                        f"{staged_relative}"
                    )
                source_relative = (
                    handle.source_relative / Path(*source_parts)
                ).as_posix()
                _register_portable_path(
                    canonical_root,
                    staged_relative,
                    source_relative,
                )
                planned.append(
                    PlannedFile(
                        source_handle=handle,
                        source_parts=source_parts,
                        source_relative=source_relative,
                        staged_relative=staged_relative,
                        identity=identity,
                    )
                )

        if not planned:
            raise EvidenceError("Android migration evidence roots contain zero files")
        return (
            sorted(planned, key=lambda item: item.staged_relative.as_posix()),
            handles,
        )
    except BaseException:
        for handle in handles:
            os.close(handle.descriptor)
        raise


def _write_all(descriptor: int, payload: bytes) -> None:
    offset = 0
    while offset < len(payload):
        written = os.write(descriptor, payload[offset:])
        if written <= 0:
            raise EvidenceError("short write while staging evidence")
        offset += written


def _copy_and_hash(
    item: PlannedFile,
    staging_descriptor: int,
) -> tuple[int, str]:
    parent_descriptor = _open_directory_chain(
        item.source_handle.descriptor,
        item.source_parts[:-1],
        create=False,
    )
    try:
        try:
            source_descriptor = os.open(
                item.source_parts[-1],
                _file_read_flags(),
                dir_fd=parent_descriptor,
            )
        except OSError as error:
            raise EvidenceError(
                f"failed to open planned evidence file {item.source_relative}: {error}"
            ) from error
    finally:
        os.close(parent_descriptor)

    try:
        destination_parent = _open_directory_chain(
            staging_descriptor,
            item.staged_relative.parts[:-1],
            create=True,
        )
    except BaseException:
        os.close(source_descriptor)
        raise
    destination_descriptor: int | None = None
    try:
        before = FileIdentity.from_stat(os.fstat(source_descriptor))
        if not stat.S_ISREG(before.mode):
            raise EvidenceError(
                f"evidence file changed type before copy: {item.source_relative}"
            )
        if before != item.identity:
            raise EvidenceError(
                f"evidence file changed after planning: {item.source_relative}"
            )
        destination_descriptor = os.open(
            item.staged_relative.parts[-1],
            _file_write_flags(),
            mode=0o400,
            dir_fd=destination_parent,
        )
        digest = hashlib.sha256()
        size = 0
        while True:
            chunk = os.read(source_descriptor, COPY_CHUNK_BYTES)
            if not chunk:
                break
            _write_all(destination_descriptor, chunk)
            digest.update(chunk)
            size += len(chunk)
        os.fsync(destination_descriptor)
        os.fchmod(destination_descriptor, 0o400)
        after = FileIdentity.from_stat(os.fstat(source_descriptor))
        if after != before or size != before.size:
            raise EvidenceError(
                f"evidence file changed while copying: {item.source_relative}"
            )
        return size, digest.hexdigest()
    finally:
        if destination_descriptor is not None:
            os.close(destination_descriptor)
        os.close(destination_parent)
        os.close(source_descriptor)


def _write_staged_file(
    staging_descriptor: int,
    name: str,
    payload: bytes,
) -> None:
    descriptor = os.open(
        name,
        _file_write_flags(),
        mode=0o400,
        dir_fd=staging_descriptor,
    )
    try:
        _write_all(descriptor, payload)
        os.fsync(descriptor)
        os.fchmod(descriptor, 0o400)
    finally:
        os.close(descriptor)


def _remove_entry_at(parent_descriptor: int, name: str) -> str | None:
    try:
        metadata = os.stat(name, dir_fd=parent_descriptor, follow_symlinks=False)
    except FileNotFoundError:
        return None

    mode = metadata.st_mode
    if not stat.S_ISDIR(mode) or stat.S_ISLNK(mode):
        os.unlink(name, dir_fd=parent_descriptor)
        return "symlink" if stat.S_ISLNK(mode) else "file"

    directory_descriptor = os.open(
        name,
        _directory_flags(),
        dir_fd=parent_descriptor,
    )
    opened = os.fstat(directory_descriptor)
    parent_metadata = os.fstat(parent_descriptor)
    if opened.st_dev != parent_metadata.st_dev:
        os.close(directory_descriptor)
        raise EvidenceError(f"cross-device staging cleanup is forbidden: {name}")
    if (opened.st_dev, opened.st_ino) != (metadata.st_dev, metadata.st_ino):
        os.close(directory_descriptor)
        raise EvidenceError(f"staging directory changed while opening: {name}")
    try:
        for child in sorted(os.listdir(directory_descriptor)):
            _remove_entry_at(directory_descriptor, child)
        current = os.stat(
            name,
            dir_fd=parent_descriptor,
            follow_symlinks=False,
        )
        if (current.st_dev, current.st_ino) != (opened.st_dev, opened.st_ino):
            raise EvidenceError(f"staging directory changed during cleanup: {name}")
    finally:
        os.close(directory_descriptor)
    os.rmdir(name, dir_fd=parent_descriptor)
    return "directory"


def _create_temporary_directory(parent_descriptor: int) -> tuple[str, int]:
    for _ in range(128):
        name = f".android-migration-upload.{secrets.token_hex(12)}"
        try:
            os.mkdir(name, mode=0o700, dir_fd=parent_descriptor)
        except FileExistsError:
            continue
        try:
            descriptor = os.open(
                name,
                _directory_flags(),
                dir_fd=parent_descriptor,
            )
        except BaseException:
            _remove_entry_at(parent_descriptor, name)
            raise
        os.fchmod(descriptor, 0o700)
        return name, descriptor
    raise EvidenceError("could not allocate a unique evidence staging directory")


def _verify_directory_binding(
    base_descriptor: int,
    parts: Iterable[str],
    expected_device_inode: tuple[int, int],
    label: str,
) -> None:
    try:
        descriptor = _open_directory_chain(
            base_descriptor,
            parts,
            create=False,
        )
    except OSError as error:
        raise EvidenceError(f"{label} path binding changed: {error}") from error
    try:
        metadata = os.fstat(descriptor)
        if (metadata.st_dev, metadata.st_ino) != expected_device_inode:
            raise EvidenceError(f"{label} path binding changed")
    finally:
        os.close(descriptor)


def _verify_absolute_directory_binding(
    path: Path,
    expected_device_inode: tuple[int, int],
    label: str,
) -> None:
    try:
        descriptor = _open_absolute_directory_no_follow(path)
    except OSError as error:
        raise EvidenceError(f"{label} path binding changed: {error}") from error
    try:
        metadata = os.fstat(descriptor)
        if (metadata.st_dev, metadata.st_ino) != expected_device_inode:
            raise EvidenceError(f"{label} path binding changed")
    finally:
        os.close(descriptor)


def stage_evidence(
    project_root: Path,
    output: Path,
    sources: list[SourceRoot] | None = None,
) -> dict[str, object]:
    using_default_sources = sources is None
    (
        lexical_project_root,
        project_root,
        expected_project_binding,
    ) = _canonical_project_root(project_root)
    lexical_output = _lexical_absolute(output)
    try:
        output_relative = lexical_output.relative_to(lexical_project_root)
    except ValueError:
        output_relative = _relative_to_project(
            lexical_output,
            project_root,
            "staging output",
        )
    output = project_root / output_relative
    if output == project_root or not output_relative.parts:
        raise EvidenceError("staging output must not be the project root")

    project_descriptor = _open_absolute_directory_no_follow(project_root)
    project_metadata = os.fstat(project_descriptor)
    project_binding = (project_metadata.st_dev, project_metadata.st_ino)
    if project_binding != expected_project_binding:
        os.close(project_descriptor)
        raise EvidenceError("project root changed before descriptor binding")
    output_parent_parts = output_relative.parts[:-1]
    output_name = output_relative.parts[-1]
    output_parent_descriptor: int | None = None
    temporary_name: str | None = None
    temporary_descriptor: int | None = None
    handles: list[SourceHandle] = []
    renamed = False
    success = False
    try:
        output_parent_descriptor = _open_directory_chain(
            project_descriptor,
            output_parent_parts,
            create=True,
        )
        output_parent_metadata = os.fstat(output_parent_descriptor)
        output_parent_binding = (
            output_parent_metadata.st_dev,
            output_parent_metadata.st_ino,
        )
        removed_kind = _remove_entry_at(output_parent_descriptor, output_name)
        if removed_kind in {"symlink", "file"}:
            raise EvidenceError(
                f"unsafe pre-existing {removed_kind} staging output was removed: {output}"
            )

        selected_sources = (
            discover_default_sources(project_root, project_descriptor)
            if sources is None
            else sources
        )
        selected_sources = _canonicalize_source_contracts(
            selected_sources,
            lexical_project_root,
            project_root,
        )
        plan, handles = _open_source_handles_and_plan(
            project_root,
            output,
            selected_sources,
            project_descriptor,
        )
        temporary_name, temporary_descriptor = _create_temporary_directory(
            output_parent_descriptor
        )

        manifest_files: list[dict[str, object]] = []
        for item in plan:
            size, sha256 = _copy_and_hash(item, temporary_descriptor)
            manifest_files.append(
                {
                    "source": item.source_relative,
                    "staged": item.staged_relative.as_posix(),
                    "size": size,
                    "sha256": sha256,
                }
            )

        for handle in handles:
            if FileIdentity.from_stat(os.fstat(handle.descriptor)) != handle.identity:
                raise EvidenceError(
                    f"evidence source changed while staging: {handle.source_relative}"
                )
            expected_files = sorted(
                (item.source_parts, item.identity)
                for item in plan
                if item.source_handle is handle
            )
            actual_files = sorted(_walk_regular_files_fd(handle.descriptor))
            if actual_files != expected_files:
                raise EvidenceError(
                    f"evidence source tree changed while staging: {handle.source_relative}"
                )
            _verify_directory_binding(
                project_descriptor,
                handle.source_relative.parts,
                (handle.identity.device, handle.identity.inode),
                f"evidence source {handle.source_relative}",
            )

        if using_default_sources:
            final_sources = _canonicalize_source_contracts(
                discover_default_sources(project_root, project_descriptor),
                lexical_project_root,
                project_root,
            )
            if _source_contract_keys(
                final_sources,
                project_root,
            ) != _source_contract_keys(selected_sources, project_root):
                raise EvidenceError(
                    "Android migration evidence root set changed while staging"
                )

        source_roots = sorted(
            {handle.source_relative.as_posix() for handle in handles}
        )
        manifest: dict[str, object] = {
            "format_version": 2,
            "filename_encoding": (
                "Unsafe UTF-8 bytes use ~HH; long names use SHA-256; "
                "NFC/casefold collisions are rejected"
            ),
            "source_roots": source_roots,
            "file_count": len(manifest_files),
            "files": manifest_files,
        }
        manifest_bytes = (
            json.dumps(
                manifest,
                ensure_ascii=False,
                indent=2,
                sort_keys=True,
            )
            + "\n"
        ).encode("utf-8")
        _write_staged_file(
            temporary_descriptor,
            MANIFEST_NAME,
            manifest_bytes,
        )
        manifest_sha256 = hashlib.sha256(manifest_bytes).hexdigest()
        _write_staged_file(
            temporary_descriptor,
            MANIFEST_SHA256_NAME,
            f"{manifest_sha256}  {MANIFEST_NAME}\n".encode("ascii"),
        )
        os.fsync(temporary_descriptor)

        _verify_absolute_directory_binding(
            project_root,
            project_binding,
            "project root",
        )
        _verify_directory_binding(
            project_descriptor,
            output_parent_parts,
            output_parent_binding,
            "staging output parent",
        )
        os.rename(
            temporary_name,
            output_name,
            src_dir_fd=output_parent_descriptor,
            dst_dir_fd=output_parent_descriptor,
        )
        renamed = True
        temporary_name = None
        output_metadata = os.fstat(temporary_descriptor)
        _verify_directory_binding(
            project_descriptor,
            output_parent_parts,
            output_parent_binding,
            "post-rename staging output parent",
        )
        _verify_directory_binding(
            output_parent_descriptor,
            (output_name,),
            (output_metadata.st_dev, output_metadata.st_ino),
            "final staging output",
        )
        _verify_absolute_directory_binding(
            project_root,
            project_binding,
            "final project root",
        )
        success = True
        return manifest
    except OSError as error:
        raise EvidenceError(f"evidence staging failed closed: {error}") from error
    finally:
        for handle in handles:
            try:
                os.close(handle.descriptor)
            except OSError:
                pass
        if temporary_descriptor is not None:
            try:
                os.close(temporary_descriptor)
            except OSError:
                pass
        if (
            not renamed
            and temporary_name is not None
            and output_parent_descriptor is not None
        ):
            try:
                _remove_entry_at(output_parent_descriptor, temporary_name)
            except (EvidenceError, OSError):
                pass
        if (
            renamed
            and not success
            and output_parent_descriptor is not None
        ):
            try:
                _remove_entry_at(output_parent_descriptor, output_name)
            except (EvidenceError, OSError):
                pass
        if output_parent_descriptor is not None:
            os.close(output_parent_descriptor)
        os.close(project_descriptor)


def _parse_arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Stage Android migration evidence under portable, hash-manifested names"
        )
    )
    default_root = Path(__file__).resolve().parent.parent
    parser.add_argument(
        "--project-root",
        type=Path,
        default=default_root,
        help="repository root (default: script parent repository)",
    )
    parser.add_argument(
        "--output",
        type=Path,
        help=f"staging directory (default: {OUTPUT_RELATIVE})",
    )
    return parser.parse_args()


def main() -> int:
    arguments = _parse_arguments()
    project_root = _lexical_absolute(arguments.project_root)
    output = (
        _lexical_absolute(arguments.output)
        if arguments.output is not None
        else project_root / OUTPUT_RELATIVE
    )
    try:
        manifest = stage_evidence(project_root, output)
    except (EvidenceError, OSError) as error:
        print(f"[android-migration-evidence][error] {error}", file=os.sys.stderr)
        return 1
    print(
        "[android-migration-evidence] "
        f"staged {manifest['file_count']} files in {output}"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
