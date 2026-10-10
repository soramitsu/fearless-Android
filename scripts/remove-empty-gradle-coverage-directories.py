#!/usr/bin/env python3
"""Remove only the known empty Gradle coverage output directories."""

from __future__ import annotations

import contextlib
import os
import stat
import sys
from typing import List, NoReturn, Optional, Tuple


MODULES = (
    "feature-account-impl",
    "feature-crowdloan-impl",
    "feature-onboarding-impl",
    "feature-staking-impl",
    "feature-wallet-impl",
)


class CoverageCleanupError(RuntimeError):
    """The cleanup target failed a no-follow safety check."""


def fail(message: str) -> NoReturn:
    raise CoverageCleanupError(message)


def open_directory_nofollow(
    path: str,
    *,
    dir_fd: Optional[int] = None,
    label: str,
) -> int:
    try:
        directory_flag = os.O_DIRECTORY
        nofollow_flag = os.O_NOFOLLOW
    except AttributeError:
        fail("this platform does not provide O_DIRECTORY and O_NOFOLLOW")

    flags = os.O_RDONLY | directory_flag | nofollow_flag
    if hasattr(os, "O_CLOEXEC"):
        flags |= os.O_CLOEXEC
    try:
        descriptor = os.open(path, flags, dir_fd=dir_fd)
    except OSError as error:
        fail(f"{label} must be a readable real directory: {error}")
    if not stat.S_ISDIR(os.fstat(descriptor).st_mode):
        os.close(descriptor)
        fail(f"{label} must be a real directory")
    return descriptor


def same_identity(left: os.stat_result, right: os.stat_result) -> bool:
    return left.st_dev == right.st_dev and left.st_ino == right.st_ino


def open_absolute_directory_nofollow(path: str) -> int:
    if os.sep != "/" or not path.startswith(os.sep):
        fail("source root requires POSIX absolute-path semantics")

    descriptor = open_directory_nofollow(
        os.sep,
        label="filesystem root",
    )
    try:
        for component in path.split(os.sep)[1:]:
            if not component:
                continue
            next_descriptor = open_directory_nofollow(
                component,
                dir_fd=descriptor,
                label=f"source-root component {component}",
            )
            os.close(descriptor)
            descriptor = next_descriptor
    except BaseException:
        os.close(descriptor)
        raise
    return descriptor


def remove_empty_gradle_coverage_directories(source_root: str) -> None:
    if not os.path.isabs(source_root):
        fail("source root must be an absolute path")

    normalized_root = os.path.normpath(source_root)
    physical_root = os.path.realpath(source_root)
    if normalized_root != source_root or physical_root != source_root:
        fail("source root must be a normalized physical path without symlinks")

    with contextlib.ExitStack() as stack:
        root_fd = open_absolute_directory_nofollow(source_root)
        stack.callback(os.close, root_fd)

        opened_coverage: List[Tuple[str, int, int, os.stat_result]] = []
        for module in MODULES:
            module_fd = open_directory_nofollow(
                module,
                dir_fd=root_fd,
                label=f"module {module}",
            )
            stack.callback(os.close, module_fd)

            try:
                coverage_fd = open_directory_nofollow(
                    "coverage",
                    dir_fd=module_fd,
                    label=f"{module}/coverage",
                )
            except CoverageCleanupError as error:
                try:
                    os.stat("coverage", dir_fd=module_fd, follow_symlinks=False)
                except FileNotFoundError:
                    continue
                except OSError as stat_error:
                    fail(f"unable to inspect {module}/coverage: {stat_error}")
                raise error

            stack.callback(os.close, coverage_fd)
            try:
                entries = os.listdir(coverage_fd)
            except OSError as error:
                fail(f"unable to inspect {module}/coverage: {error}")
            if entries:
                fail(f"{module}/coverage must be empty before removal")

            opened_coverage.append(
                (module, module_fd, coverage_fd, os.fstat(coverage_fd))
            )

        for module, module_fd, _coverage_fd, opened_identity in opened_coverage:
            try:
                live_identity = os.stat(
                    "coverage",
                    dir_fd=module_fd,
                    follow_symlinks=False,
                )
            except OSError as error:
                fail(f"{module}/coverage changed before removal: {error}")
            if (
                not stat.S_ISDIR(live_identity.st_mode)
                or not same_identity(opened_identity, live_identity)
            ):
                fail(f"{module}/coverage changed before removal")
            try:
                os.rmdir("coverage", dir_fd=module_fd)
            except OSError as error:
                fail(f"unable to remove empty {module}/coverage: {error}")


def main(argv: list[str]) -> int:
    if len(argv) != 2:
        print(
            "Usage: remove-empty-gradle-coverage-directories.py "
            "<absolute-physical-source-root>",
            file=sys.stderr,
        )
        return 2
    try:
        remove_empty_gradle_coverage_directories(argv[1])
    except CoverageCleanupError as error:
        print(f"[gradle-coverage-cleanup][error] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
