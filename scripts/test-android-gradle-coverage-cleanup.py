#!/usr/bin/env python3
"""Adversarial tests for the bounded Gradle coverage cleanup helper."""

from __future__ import annotations

import argparse
import importlib.util
import os
import pathlib
import subprocess
import sys
import tempfile
from types import ModuleType
from typing import Callable, Optional


sys.dont_write_bytecode = True

MODULES = (
    "feature-account-impl",
    "feature-crowdloan-impl",
    "feature-onboarding-impl",
    "feature-staking-impl",
    "feature-wallet-impl",
)


def fail(message: str) -> None:
    raise AssertionError(message)


def load_cleaner(path: pathlib.Path) -> ModuleType:
    spec = importlib.util.spec_from_file_location(
        "fearless_gradle_coverage_cleaner",
        path,
    )
    if spec is None or spec.loader is None:
        fail("could not load coverage cleanup helper")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def create_source_root(parent: pathlib.Path, name: str) -> pathlib.Path:
    root = parent / name
    root.mkdir()
    for module in MODULES:
        (root / module).mkdir()
    return root


def run_cleaner(
    helper: pathlib.Path,
    source_root: str,
) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        [sys.executable, str(helper), source_root],
        check=False,
        capture_output=True,
        text=True,
    )


def expect_success(
    helper: pathlib.Path,
    source_root: pathlib.Path,
    label: str,
) -> None:
    result = run_cleaner(helper, str(source_root))
    if result.returncode != 0:
        fail(
            f"{label} failed with {result.returncode}: "
            f"{result.stdout}{result.stderr}"
        )


def expect_failure(
    helper: pathlib.Path,
    source_root: str,
    label: str,
) -> None:
    result = run_cleaner(helper, source_root)
    if result.returncode == 0:
        fail(f"{label} unexpectedly passed")
    if "[gradle-coverage-cleanup][error]" not in result.stderr:
        fail(f"{label} omitted the fail-closed diagnostic: {result.stderr}")


def expect_direct_failure(
    cleaner: ModuleType,
    operation: Callable[[], None],
    label: str,
) -> None:
    try:
        operation()
    except cleaner.CoverageCleanupError:
        return
    fail(f"{label} unexpectedly passed")


def snapshot_symlink(path: pathlib.Path) -> tuple[int, int, int, str]:
    metadata = path.lstat()
    return (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_mode,
        os.readlink(path),
    )


def run_tests(helper: pathlib.Path, temp_parent: pathlib.Path) -> None:
    cleaner = load_cleaner(helper)
    positive_count = 0
    negative_count = 0

    if tuple(cleaner.MODULES) != MODULES:
        fail("coverage cleanup allowlist is not the exact five Gradle modules")
    positive_count += 1

    no_outputs = create_source_root(temp_parent, "no-outputs")
    expect_success(helper, no_outputs, "missing generated outputs")
    positive_count += 1

    all_empty = create_source_root(temp_parent, "all-empty")
    for module in MODULES:
        (all_empty / module / "coverage").mkdir()
    decoy = all_empty / "feature-decoy-impl" / "coverage"
    decoy.mkdir(parents=True)
    decoy_marker = decoy / "must-not-change"
    decoy_marker.write_bytes(b"decoy")
    expect_success(helper, all_empty, "exact empty output cleanup")
    for module in MODULES:
        if (all_empty / module / "coverage").exists():
            fail(f"empty coverage output was not removed for {module}")
    if decoy_marker.read_bytes() != b"decoy":
        fail("cleanup changed a directory outside the exact allowlist")
    positive_count += 1

    subset = create_source_root(temp_parent, "subset")
    (subset / MODULES[0] / "coverage").mkdir()
    (subset / MODULES[-1] / "coverage").mkdir()
    expect_success(helper, subset, "partial empty output cleanup")
    if (subset / MODULES[0] / "coverage").exists():
        fail("first partial coverage output was not removed")
    if (subset / MODULES[-1] / "coverage").exists():
        fail("last partial coverage output was not removed")
    positive_count += 1

    nonempty = create_source_root(temp_parent, "nonempty")
    for module in MODULES:
        (nonempty / module / "coverage").mkdir()
    nonempty_marker = nonempty / MODULES[-1] / "coverage" / "marker"
    nonempty_marker.write_bytes(b"preserve")
    expect_failure(helper, str(nonempty), "nonempty coverage output")
    if nonempty_marker.read_bytes() != b"preserve":
        fail("nonempty cleanup failure changed marker bytes")
    if any(not (nonempty / module / "coverage").is_dir() for module in MODULES):
        fail("nonempty preflight failure partially removed coverage outputs")
    negative_count += 1

    leaf_symlink = create_source_root(temp_parent, "leaf-symlink")
    leaf_target = temp_parent / "leaf-target"
    leaf_target.mkdir()
    leaf_marker = leaf_target / "marker"
    leaf_marker.write_bytes(b"preserve")
    (leaf_symlink / MODULES[0] / "coverage").symlink_to(
        leaf_target,
        target_is_directory=True,
    )
    leaf_link = leaf_symlink / MODULES[0] / "coverage"
    leaf_link_before = snapshot_symlink(leaf_link)
    expect_failure(helper, str(leaf_symlink), "coverage leaf symlink")
    if snapshot_symlink(leaf_link) != leaf_link_before:
        fail("leaf symlink failure changed the attacked link")
    if leaf_marker.read_bytes() != b"preserve":
        fail("leaf symlink failure changed external target")
    negative_count += 1

    parent_symlink = create_source_root(temp_parent, "parent-symlink")
    (parent_symlink / MODULES[0]).rmdir()
    parent_target = temp_parent / "parent-target"
    (parent_target / "coverage").mkdir(parents=True)
    parent_marker = parent_target / "must-not-change"
    parent_marker.write_bytes(b"preserve")
    (parent_symlink / MODULES[0]).symlink_to(
        parent_target,
        target_is_directory=True,
    )
    parent_link = parent_symlink / MODULES[0]
    parent_link_before = snapshot_symlink(parent_link)
    expect_failure(helper, str(parent_symlink), "module parent symlink")
    if snapshot_symlink(parent_link) != parent_link_before:
        fail("parent symlink failure changed the attacked link")
    if parent_marker.read_bytes() != b"preserve":
        fail("parent symlink failure changed external target")
    if not (parent_target / "coverage").is_dir():
        fail("parent symlink failure removed external coverage target")
    negative_count += 1

    missing_module = create_source_root(temp_parent, "missing-module")
    for module in MODULES:
        (missing_module / module / "coverage").mkdir()
    (missing_module / MODULES[-1] / "coverage").rmdir()
    (missing_module / MODULES[-1]).rmdir()
    expect_failure(helper, str(missing_module), "missing allowlisted module")
    if any(
        not (missing_module / module / "coverage").is_dir()
        for module in MODULES[:-1]
    ):
        fail("missing-module preflight failure partially removed outputs")
    negative_count += 1

    regular_file = create_source_root(temp_parent, "regular-file")
    coverage_file = regular_file / MODULES[0] / "coverage"
    coverage_file.write_bytes(b"preserve")
    expect_failure(helper, str(regular_file), "coverage regular file")
    if coverage_file.read_bytes() != b"preserve":
        fail("regular-file failure changed coverage bytes")
    negative_count += 1

    real_root = create_source_root(temp_parent, "real-root")
    root_symlink = temp_parent / "root-symlink"
    root_symlink.symlink_to(real_root, target_is_directory=True)
    root_link_before = snapshot_symlink(root_symlink)
    expect_failure(helper, str(root_symlink), "source-root symlink")
    if snapshot_symlink(root_symlink) != root_link_before:
        fail("source-root symlink failure changed the attacked link")
    negative_count += 1

    normalized_root = create_source_root(temp_parent, "normalized-root")
    noncanonical_root = str(
        normalized_root / ".." / normalized_root.name
    )
    expect_failure(helper, noncanonical_root, "noncanonical source root")
    negative_count += 1

    inspection_failure = create_source_root(temp_parent, "inspection-failure")
    inspection_coverage = inspection_failure / MODULES[0] / "coverage"
    inspection_coverage.mkdir()
    original_listdir = cleaner.os.listdir

    def fail_listdir(_descriptor: int) -> list[str]:
        raise PermissionError("injected inspection failure")

    cleaner.os.listdir = fail_listdir
    try:
        expect_direct_failure(
            cleaner,
            lambda: cleaner.remove_empty_gradle_coverage_directories(
                str(inspection_failure)
            ),
            "coverage inspection failure",
        )
    finally:
        cleaner.os.listdir = original_listdir
    if not inspection_coverage.is_dir():
        fail("inspection failure removed the coverage directory")
    negative_count += 1

    race_root = create_source_root(temp_parent, "leaf-race")
    race_coverage = race_root / MODULES[0] / "coverage"
    race_coverage.mkdir()
    race_target = temp_parent / "race-target"
    race_target.mkdir()
    race_marker = race_target / "marker"
    race_marker.write_bytes(b"preserve")
    original_rmdir = cleaner.os.rmdir
    race_injected = False

    def replace_before_rmdir(
        path: str,
        *,
        dir_fd: Optional[int] = None,
    ) -> None:
        nonlocal race_injected
        if path == "coverage" and not race_injected:
            race_injected = True
            original_rmdir(race_coverage)
            race_coverage.symlink_to(race_target, target_is_directory=True)
        original_rmdir(path, dir_fd=dir_fd)

    cleaner.os.rmdir = replace_before_rmdir
    try:
        expect_direct_failure(
            cleaner,
            lambda: cleaner.remove_empty_gradle_coverage_directories(
                str(race_root)
            ),
            "coverage replacement race",
        )
    finally:
        cleaner.os.rmdir = original_rmdir
    if not race_coverage.is_symlink():
        fail("replacement-race fixture was not injected")
    if race_marker.read_bytes() != b"preserve":
        fail("replacement race changed the external target")
    negative_count += 1

    first_rmdir_failure = create_source_root(
        temp_parent,
        "first-rmdir-failure",
    )
    for module in MODULES:
        (first_rmdir_failure / module / "coverage").mkdir()
    original_rmdir = cleaner.os.rmdir

    def fail_first_rmdir(path: str, *, dir_fd: Optional[int] = None) -> None:
        if path == "coverage":
            raise PermissionError("injected first rmdir failure")
        original_rmdir(path, dir_fd=dir_fd)

    cleaner.os.rmdir = fail_first_rmdir
    try:
        expect_direct_failure(
            cleaner,
            lambda: cleaner.remove_empty_gradle_coverage_directories(
                str(first_rmdir_failure)
            ),
            "first coverage rmdir failure",
        )
    finally:
        cleaner.os.rmdir = original_rmdir
    if any(
        not (first_rmdir_failure / module / "coverage").is_dir()
        for module in MODULES
    ):
        fail("first rmdir failure partially removed coverage outputs")
    negative_count += 1

    later_rmdir_failure = create_source_root(
        temp_parent,
        "later-rmdir-failure",
    )
    for module in MODULES:
        (later_rmdir_failure / module / "coverage").mkdir()
    rmdir_calls = 0

    def fail_second_rmdir(path: str, *, dir_fd: Optional[int] = None) -> None:
        nonlocal rmdir_calls
        if path == "coverage":
            rmdir_calls += 1
            if rmdir_calls == 2:
                raise PermissionError("injected later rmdir failure")
        original_rmdir(path, dir_fd=dir_fd)

    cleaner.os.rmdir = fail_second_rmdir
    try:
        expect_direct_failure(
            cleaner,
            lambda: cleaner.remove_empty_gradle_coverage_directories(
                str(later_rmdir_failure)
            ),
            "later coverage rmdir failure",
        )
    finally:
        cleaner.os.rmdir = original_rmdir
    if (later_rmdir_failure / MODULES[0] / "coverage").exists():
        fail("later rmdir failure did not preserve completed idempotent cleanup")
    if any(
        not (later_rmdir_failure / module / "coverage").is_dir()
        for module in MODULES[1:]
    ):
        fail("later rmdir failure removed a target after the injected error")
    cleaner.remove_empty_gradle_coverage_directories(
        str(later_rmdir_failure)
    )
    if any(
        (later_rmdir_failure / module / "coverage").exists()
        for module in MODULES
    ):
        fail("retry did not complete idempotent coverage cleanup")
    negative_count += 1

    ancestor_race_parent = temp_parent / "ancestor-race-parent"
    ancestor_race_parent.mkdir()
    ancestor_race_root = create_source_root(
        ancestor_race_parent,
        "checkout",
    )
    displaced_parent = temp_parent / "ancestor-race-original"
    external_parent = temp_parent / "ancestor-race-external"
    external_parent.mkdir()
    external_root = create_source_root(external_parent, "checkout")
    external_coverage = external_root / MODULES[0] / "coverage"
    external_coverage.mkdir()
    original_open = cleaner.os.open
    race_parent_name = ancestor_race_parent.name
    ancestor_race_injected = False

    def replace_ancestor_before_open(
        path: str,
        flags: int,
        mode: int = 0o777,
        *,
        dir_fd: Optional[int] = None,
    ) -> int:
        nonlocal ancestor_race_injected
        if path == race_parent_name and not ancestor_race_injected:
            ancestor_race_injected = True
            ancestor_race_parent.rename(displaced_parent)
            ancestor_race_parent.symlink_to(
                external_parent,
                target_is_directory=True,
            )
        return original_open(path, flags, mode, dir_fd=dir_fd)

    cleaner.os.open = replace_ancestor_before_open
    try:
        expect_direct_failure(
            cleaner,
            lambda: cleaner.remove_empty_gradle_coverage_directories(
                str(ancestor_race_root)
            ),
            "source-root ancestor replacement race",
        )
    finally:
        cleaner.os.open = original_open
    if not ancestor_race_injected:
        fail("ancestor replacement-race fixture was not injected")
    if not ancestor_race_parent.is_symlink():
        fail("ancestor replacement-race symlink was unexpectedly changed")
    if not external_coverage.is_dir():
        fail("ancestor replacement race removed an external coverage target")
    negative_count += 1

    if positive_count != 4:
        fail(f"expected 4 positive cases; got {positive_count}")
    if negative_count != 12:
        fail(f"expected 12 negative/adversarial cases; got {negative_count}")
    print(
        "[gradle-coverage-cleanup-test] "
        f"{positive_count} positive + {negative_count} "
        "negative/adversarial cases passed"
    )


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("helper", type=pathlib.Path)
    parser.add_argument("--temp-parent", type=pathlib.Path)
    arguments = parser.parse_args(argv[1:])

    helper_input = arguments.helper
    if helper_input.is_symlink() or not helper_input.is_file():
        parser.error("helper must be a real regular file")
    helper = helper_input.resolve(strict=True)

    temp_parent = arguments.temp_parent
    if temp_parent is not None:
        temp_parent.mkdir(parents=True, exist_ok=True)
        temp_parent = temp_parent.resolve(strict=True)

    try:
        with tempfile.TemporaryDirectory(
            prefix="gradle-coverage-cleanup.",
            dir=str(temp_parent) if temp_parent is not None else None,
        ) as temporary_directory:
            run_tests(
                helper,
                pathlib.Path(temporary_directory).resolve(strict=True),
            )
    except AssertionError as error:
        print(f"[gradle-coverage-cleanup-test][error] {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
