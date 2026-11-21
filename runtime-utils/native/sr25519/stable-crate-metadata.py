#!/usr/bin/env python3
"""rustc wrapper of the libsr25519java.so build (scripts/build-sr25519.py sets it as RUSTC_WRAPPER).

Cargo's `-C metadata` value hashes the output of `rustc -vV`, whose `host:` line names the platform of the
build machine. The value seeds every crate's StableCrateId, which reaches the library through symbol hashes
and type ids, so the same source, Cargo.lock and toolchain gave different bytes on macOS (arm64 or x86_64)
and on Linux. This wrapper replaces each `-C metadata` value with a hash of what identifies the compilation
without the build machine: package name and version, crate name and types, edition, enabled cfgs, target
and the source path inside the package. The rest of the rustc command line is passed through unchanged.
"""
import hashlib
import json
import os
import sys

sys.dont_write_bytecode = True

IDENTITY_OPTIONS = ("--crate-name", "--crate-type", "--edition", "--cfg", "--target")


def unit_identity(arguments):
    """The parts of a rustc command line that identify a compilation unit on every build host."""
    values = {option: [] for option in IDENTITY_OPTIONS}
    source = None
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument in values and index + 1 < len(arguments):
            values[argument].append(arguments[index + 1])
            index += 2
            continue
        for option in IDENTITY_OPTIONS:
            if argument.startswith(option + "="):
                values[option].append(argument[len(option) + 1:])
        if argument.endswith(".rs") and not argument.startswith("-"):
            source = argument
        index += 1
    package_root = os.environ.get("CARGO_MANIFEST_DIR", "")
    if source is not None and package_root:
        # Resolved paths: a symlinked cargo home or checkout must not leak into the identity.
        source = os.path.relpath(os.path.realpath(source), os.path.realpath(package_root))
    return json.dumps({
        "package": os.environ.get("CARGO_PKG_NAME", ""),
        "version": os.environ.get("CARGO_PKG_VERSION", ""),
        "crate": values["--crate-name"],
        "types": sorted(values["--crate-type"]),
        "edition": values["--edition"],
        "cfg": sorted(values["--cfg"]),
        "target": values["--target"],
        "source": source,
    }, sort_keys=True)


def with_stable_metadata(arguments):
    """The arguments with every `-C metadata` value replaced; unchanged when there is none."""
    metadata = hashlib.sha256(unit_identity(arguments).encode("utf-8")).hexdigest()[:16]
    rewritten = []
    index = 0
    while index < len(arguments):
        argument = arguments[index]
        if argument == "-C" and index + 1 < len(arguments) and arguments[index + 1].startswith("metadata="):
            rewritten += ["-C", "metadata=" + metadata]
            index += 2
            continue
        if argument.startswith("-Cmetadata="):
            rewritten.append("-Cmetadata=" + metadata)
        else:
            rewritten.append(argument)
        index += 1
    return rewritten


def main():
    if len(sys.argv) < 2:
        sys.exit("usage: stable-crate-metadata.py <rustc> [rustc arguments]")
    rustc, arguments = sys.argv[1], sys.argv[2:]
    os.execvp(rustc, [rustc] + with_stable_metadata(arguments))


if __name__ == "__main__":
    main()
