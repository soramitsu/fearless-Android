#!/usr/bin/env python3
"""Verify app-owned Android runtime sources without fetching another repository."""
import hashlib
import json
from pathlib import Path
import re
import sys

GENERATED = {"build", ".gradle", ".kotlin"}
MODULES = {"utils": "runtime-utils", "websocket": "runtime-websocket"}
FORBIDDEN = (
    "includeBuild(", "sourceControl {", "FEARLESS_UTILS_PATH", "FEARLESS_NV_WEBSOCKET_PATH",
    "USE_REMOTE_UTILS", "SHARED_FEATURES_VERSION_OVERRIDE", "sharedFeaturesCoreDep",
    "sharedFeaturesXcmDep", "sharedFeaturesBackupDep", "libs.nv.websocket.client",
    "jp.co.soramitsu.fearless-utils:", "jp.co.soramitsu.shared_features:",
    "jp.co.soramitsu:ui-core:", "jp.co.soramitsu:android-foundation:",
    "com.neovisionaries:nv-websocket-client:",
)


def regular_file(root, relative):
    path = Path(relative)
    if path.is_absolute() or not path.parts or any(p in {".", ".."} for p in path.parts):
        raise ValueError("Invalid source path: " + relative)
    target = root
    for part in path.parts:
        target = target / part
        if target.is_symlink():
            raise ValueError("Source paths cannot contain symlinks: " + relative)
    if not target.is_file():
        raise ValueError("Missing regular source file: " + relative)
    return target


def source_files(directory):
    files = set()
    for path in directory.rglob("*"):
        relative = path.relative_to(directory)
        if path.is_symlink():
            raise ValueError("Source/generated paths cannot be symlinks: " + str(path))
        if any(part in GENERATED for part in relative.parts):
            continue
        if ".git" in relative.parts:
            raise ValueError("Nested Git repositories are forbidden: " + str(path))
        if path.is_file():
            files.add(relative.as_posix())
    return files


def verify(root):
    manifest = json.loads(regular_file(root, "config/android-runtime-source-pins.json").read_text())
    if set(manifest) != {"schemaVersion", "utils", "websocket"} or manifest["schemaVersion"] != 2:
        raise ValueError("Invalid app-owned runtime manifest schema")
    for name, module in MODULES.items():
        entry = manifest[name]
        if set(entry) != {"repository", "commit", "tree", "module", "files"} or entry["module"] != module:
            raise ValueError("Invalid runtime provenance fields: " + name)
        if not re.fullmatch(r"soramitsu/[A-Za-z0-9_.-]+", entry["repository"]):
            raise ValueError("Invalid upstream provenance repository")
        if not all(re.fullmatch(r"[0-9a-f]{40}", entry[k]) for k in ("commit", "tree")):
            raise ValueError("Invalid upstream provenance object")
        directory = root / module
        if directory.is_symlink() or not directory.is_dir():
            raise ValueError("Missing app-owned module: " + module)
        if not entry["files"] or source_files(directory) != set(entry["files"]):
            raise ValueError("Runtime source file inventory changed: " + module)
        for relative, expected in entry["files"].items():
            if not re.fullmatch(r"[0-9a-f]{64}", expected):
                raise ValueError("Invalid source SHA-256")
            actual = hashlib.sha256(regular_file(root, module + "/" + relative).read_bytes()).hexdigest()
            if actual != expected:
                raise ValueError("Runtime source digest changed: " + module + "/" + relative)
    build_files = {"settings.gradle", "build.gradle", "gradle/libs.versions.toml"}
    build_files.update(path.relative_to(root).as_posix() for pattern in ("*/build.gradle", "*/build.gradle.kts") for path in root.glob(pattern))
    for relative in sorted(build_files):
        source = regular_file(root, relative).read_text()
        for forbidden in FORBIDDEN:
            if forbidden in source:
                raise ValueError("External internal-runtime dependency in " + relative + ": " + forbidden)
    return manifest


def main():
    try:
        manifest = verify(Path(__file__).resolve().parents[1])
    except (ValueError, KeyError, TypeError, OSError) as error:
        print("[android-runtime-source][error] " + str(error), file=sys.stderr)
        return 1
    for name, module in MODULES.items():
        print("[android-runtime-source] " + module + ": " + str(len(manifest[name]["files"])) + " app-owned source files verified")
    return 0


if __name__ == "__main__":
    sys.exit(main())
