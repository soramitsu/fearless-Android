#!/usr/bin/env python3
"""Build libsr25519java.so from the app-owned Rust source, reproducibly.

The crate in runtime-utils/native/sr25519 holds every sr25519 key operation of
the app (Polkadot, Kusama and SORA accounts). This script is the only recipe
that turns it into Android binaries; the Gradle task
:runtime-utils:buildSr25519AndroidLibraries runs it, and nothing under src/
holds a prebuilt copy.

Reproducibility comes from:
- the Rust toolchain pinned in rust-toolchain.toml (its rustc version string is
  also checked against the manifest),
- the cargo-ndk version and NDK revision pinned in the manifest,
- the locked dependency graph (cargo --locked),
- one codegen unit, fat LTO and stripped symbols (Cargo.toml release profile),
- --remap-path-prefix for the cargo home, the Rust toolchain, the crate and the
  cargo target directory, so no machine path reaches the binaries,
- a fixed SOURCE_DATE_EPOCH and a cleaned build environment.

Every built library must have 16 KB-aligned LOAD segments, export exactly the
JNI symbols listed in the manifest, and match the manifest's SHA-256 for its
ABI. Modes:

  build (default)        build all four ABIs into --output and verify them
  --record               build and write the new SHA-256 values to the manifest
  --reproducibility-check  build twice from clean target directories and
                         require byte-identical binaries that match the manifest
  --host-output DIR      build the host (JVM test) library instead
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CRATE = ROOT / "runtime-utils" / "native" / "sr25519"
MANIFEST = CRATE / "reproducible-build.json"
# Makes cargo's crate metadata independent of the build machine, so every host builds the same bytes.
RUSTC_WRAPPER = CRATE / "stable-crate-metadata.py"
LIBRARY = "libsr25519java.so"
REQUIRED_PAGE_ALIGNMENT = 16 * 1024
PAGE_SIZE_FLAGS = (
    "-Clink-arg=-Wl,-z,common-page-size=4096",
    "-Clink-arg=-Wl,-z,max-page-size=16384",
)
RUST_TARGETS = {
    "arm64-v8a": "aarch64-linux-android",
    "armeabi-v7a": "armv7-linux-androideabi",
    "x86": "i686-linux-android",
    "x86_64": "x86_64-linux-android",
}
# Variables that could silently change the compiler, its flags or the toolchain.
DROPPED_VARIABLES = {
    "RUSTFLAGS", "RUSTDOCFLAGS", "CARGO_ENCODED_RUSTFLAGS", "CARGO_BUILD_RUSTFLAGS",
    "RUSTC", "RUSTC_WRAPPER", "RUSTC_WORKSPACE_WRAPPER", "CARGO_BUILD_RUSTC",
    "CARGO_BUILD_RUSTC_WRAPPER", "CARGO_BUILD_RUSTC_WORKSPACE_WRAPPER", "RUSTUP_TOOLCHAIN",
    "CARGO_TARGET_DIR", "CARGO_BUILD_TARGET_DIR", "CARGO_BUILD_TARGET", "CARGO_INCREMENTAL",
    "CARGO_BUILD_INCREMENTAL", "SOURCE_DATE_EPOCH", "ANDROID_NDK_ROOT", "NDK_HOME",
    "RUSTC_BOOTSTRAP", "CARGO_ENCODED_RUSTDOCFLAGS",
}
DROPPED_PREFIXES = ("CARGO_PROFILE_", "CARGO_TARGET_", "CARGO_NDK_", "CARGO_UNSTABLE_", "__CARGO")


class BuildError(Exception):
    """A build or verification step failed."""


def log(message):
    print("[sr25519-build] " + message, flush=True)


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def load_manifest(path=MANIFEST):
    """Reads and strictly validates the reproducible-build manifest."""
    manifest = json.loads(Path(path).read_text(encoding="utf-8"))
    expected_keys = {
        "schemaVersion", "crate", "library", "rustc", "cargoNdk", "ndkVersion",
        "androidApiLevel", "sourceDateEpoch", "jniExports", "abis",
    }
    if not isinstance(manifest, dict) or set(manifest) != expected_keys:
        raise BuildError("Manifest fields differ from the reviewed schema: " + str(path))
    if manifest["schemaVersion"] != 1 or manifest["library"] != LIBRARY:
        raise BuildError("Unsupported manifest schema or library name")
    if manifest["crate"] != "runtime-utils/native/sr25519":
        raise BuildError("Manifest names a different crate")
    if not re.fullmatch(r"rustc \d+\.\d+\.\d+ \([0-9a-f]{9} \d{4}-\d{2}-\d{2}\)", manifest["rustc"]):
        raise BuildError("Manifest rustc version is malformed")
    if not re.fullmatch(r"cargo-ndk \d+\.\d+\.\d+", manifest["cargoNdk"]):
        raise BuildError("Manifest cargo-ndk version is malformed")
    if not re.fullmatch(r"\d+\.\d+\.\d+", manifest["ndkVersion"]):
        raise BuildError("Manifest NDK version is malformed")
    if manifest["androidApiLevel"] != 26:
        raise BuildError("The app's minSdk is 26; the manifest must build for API 26")
    if not isinstance(manifest["sourceDateEpoch"], int) or manifest["sourceDateEpoch"] <= 0:
        raise BuildError("Manifest SOURCE_DATE_EPOCH is malformed")
    exports = manifest["jniExports"]
    if (not isinstance(exports, list) or len(exports) != len(set(exports)) or
            not all(re.fullmatch(r"Java_[A-Za-z0-9_]+", name) for name in exports)):
        raise BuildError("Manifest JNI export list is malformed")
    if not isinstance(manifest["abis"], dict) or set(manifest["abis"]) != set(RUST_TARGETS):
        raise BuildError("Manifest must list exactly the four Android ABIs")
    for abi, entry in manifest["abis"].items():
        if set(entry) != {"rustTarget", "sha256"} or entry["rustTarget"] != RUST_TARGETS[abi]:
            raise BuildError("Manifest entry is malformed for " + abi)
        if not re.fullmatch(r"[0-9a-f]{64}", entry["sha256"]):
            raise BuildError("Manifest SHA-256 is malformed for " + abi)
    return manifest


def clean_environment():
    """The caller's environment without variables that steer the compiler."""
    environment = {
        key: value for key, value in os.environ.items()
        if key not in DROPPED_VARIABLES and not key.startswith(DROPPED_PREFIXES)
    }
    cargo_home = Path(environment.get("CARGO_HOME", Path.home() / ".cargo"))
    environment["CARGO_HOME"] = str(cargo_home)
    search_path = environment.get("PATH", "")
    cargo_bin = str(cargo_home / "bin")
    if cargo_bin not in search_path.split(os.pathsep):
        environment["PATH"] = search_path + os.pathsep + cargo_bin if search_path else cargo_bin
    environment["CARGO_TERM_COLOR"] = "never"
    return environment


def require_tool(name, environment, manifest):
    tool = shutil.which(name, path=environment["PATH"])
    if tool is None:
        raise BuildError(
            name + " is required. Install rustup (https://rustup.rs); the toolchain in "
            "runtime-utils/native/sr25519/rust-toolchain.toml installs itself. Then run: "
            "cargo install cargo-ndk --version " + manifest["cargoNdk"].split()[1] + " --locked"
        )
    return tool


def run(command, environment, cwd=CRATE, capture=False):
    result = subprocess.run(
        [str(part) for part in command],
        cwd=str(cwd),
        env=environment,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        text=True,
        check=False,
    )
    if result.returncode != 0:
        detail = (result.stderr or "").strip() if capture else ""
        raise BuildError("Command failed (" + str(result.returncode) + "): " +
                         " ".join(str(part) for part in command[:3]) +
                         (": " + detail if detail else ""))
    return result.stdout if capture else ""


def check_toolchain(environment, manifest, needs_cargo_ndk):
    """Requires the exact rustc (from rust-toolchain.toml) and cargo-ndk versions."""
    require_tool("cargo", environment, manifest)
    require_tool("rustc", environment, manifest)
    rustc = run(["rustc", "--version"], environment, capture=True).strip()
    if rustc != manifest["rustc"]:
        raise BuildError("Expected " + manifest["rustc"] + " (rust-toolchain.toml), found " + rustc)
    if needs_cargo_ndk:
        require_tool("cargo-ndk", environment, manifest)
        cargo_ndk = run(["cargo", "ndk", "--version"], environment, capture=True).strip()
        if cargo_ndk != manifest["cargoNdk"]:
            raise BuildError("Expected " + manifest["cargoNdk"] + ", found " + cargo_ndk +
                             ". Run: cargo install cargo-ndk --version " +
                             manifest["cargoNdk"].split()[1] + " --locked")
    return Path(run(["rustc", "--print", "sysroot"], environment, capture=True).strip())


def resolve_ndk(argument, manifest):
    """The pinned, installed, non-preview NDK."""
    if argument:
        ndk = Path(argument)
    else:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise BuildError("Set ANDROID_HOME or pass --ndk")
        ndk = Path(sdk) / "ndk" / manifest["ndkVersion"]
    properties = ndk / "source.properties"
    if not properties.is_file():
        raise BuildError("NDK " + manifest["ndkVersion"] + " is not installed at " + str(ndk) +
                         ". Install it with: sdkmanager \"ndk;" + manifest["ndkVersion"] + "\"")
    values = dict(
        line.split("=", 1) for line in properties.read_text(encoding="utf-8").splitlines()
        if "=" in line
    )
    values = {key.strip(): value.strip() for key, value in values.items()}
    if values.get("Pkg.Revision") != manifest["ndkVersion"]:
        raise BuildError("NDK at " + str(ndk) + " is revision " + str(values.get("Pkg.Revision")) +
                         ", expected " + manifest["ndkVersion"] + " (preview releases are refused)")
    return ndk.resolve()


def readelf_path(ndk):
    tags = {"Darwin": "darwin-x86_64", "Linux": "linux-x86_64", "Windows": "windows-x86_64"}
    tool = ndk / "toolchains" / "llvm" / "prebuilt" / tags.get(platform.system(), "") / "bin" / "llvm-readelf"
    if not tool.is_file():
        raise BuildError("llvm-readelf is missing from the NDK: " + str(tool))
    return tool


def encoded_rustflags(environment, sysroot, target_dir):
    """Page-size link flags plus path remapping, in CARGO_ENCODED_RUSTFLAGS form."""
    remaps = [
        (Path(environment["CARGO_HOME"]).resolve(), "/cargo-home"),
        (sysroot.resolve(), "/rust-toolchain"),
        (CRATE.resolve(), "/sr25519"),
        (Path(target_dir).resolve(), "/cargo-target"),
    ]
    flags = list(PAGE_SIZE_FLAGS) + ["-Ccodegen-units=1"]
    flags += ["--remap-path-prefix=" + str(source) + "=" + mapped for source, mapped in remaps]
    return "\x1f".join(flags)


def inspect_library(readelf, path, manifest):
    """Requires 16 KB-aligned LOAD segments and exactly the manifest's JNI exports."""
    environment = clean_environment()
    headers = run([readelf, "--program-headers", "--wide", path], environment, cwd=ROOT, capture=True)
    loads = [line.split() for line in headers.splitlines() if line.strip().startswith("LOAD ")]
    if not loads:
        raise BuildError(str(path) + " has no LOAD segments")
    for fields in loads:
        offset, virtual_address, alignment = int(fields[1], 16), int(fields[2], 16), int(fields[-1], 16)
        if alignment < REQUIRED_PAGE_ALIGNMENT or alignment & (alignment - 1):
            raise BuildError(str(path) + " has a LOAD segment aligned to " + hex(alignment))
        if offset % alignment != virtual_address % alignment:
            raise BuildError(str(path) + " has a LOAD segment whose offset and address disagree")
    symbols = run([readelf, "--dyn-syms", "--wide", path], environment, cwd=ROOT, capture=True)
    exported = set()
    for line in symbols.splitlines():
        fields = line.split()
        if len(fields) >= 8 and fields[0].endswith(":") and fields[4] in {"GLOBAL", "WEAK"}:
            if fields[6] != "UND":
                exported.add(fields[7].split("@", 1)[0])
    if exported != set(manifest["jniExports"]):
        raise BuildError(str(path) + " exports " + str(sorted(exported ^ set(manifest["jniExports"]))) +
                         " outside or missing from the reviewed JNI list")
    return len(loads), len(exported)


def build_android(output, target_dir, ndk, manifest):
    """Builds all four ABIs into a staging directory and returns {abi: path}."""
    environment = clean_environment()
    sysroot = check_toolchain(environment, manifest, needs_cargo_ndk=True)
    target_dir = Path(target_dir).resolve()
    staging = target_dir / "jniLibs-staging"
    if staging.exists():
        shutil.rmtree(staging)
    environment.update({
        "ANDROID_NDK_HOME": str(ndk),
        "CARGO_TARGET_DIR": str(target_dir),
        "CARGO_ENCODED_RUSTFLAGS": encoded_rustflags(environment, sysroot, target_dir),
        "SOURCE_DATE_EPOCH": str(manifest["sourceDateEpoch"]),
        "CARGO_INCREMENTAL": "0",
        "RUSTC_WRAPPER": str(RUSTC_WRAPPER),
    })
    command = ["cargo", "ndk"]
    for abi in RUST_TARGETS:
        command += ["-t", abi]
    command += ["--platform", str(manifest["androidApiLevel"]), "-o", staging,
                "build", "--release", "--locked"]
    log("cargo ndk for " + ", ".join(RUST_TARGETS) + " at API " + str(manifest["androidApiLevel"]) +
        " with NDK " + manifest["ndkVersion"] + " and " + manifest["rustc"])
    run(command, environment)
    built = {}
    for abi in RUST_TARGETS:
        library = staging / abi / LIBRARY
        if not library.is_file() or library.is_symlink():
            raise BuildError("cargo-ndk produced no " + LIBRARY + " for " + abi)
        built[abi] = library
    return built


def verify(built, manifest, readelf, allow_mismatch=False):
    mismatches = []
    for abi, library in built.items():
        loads, exports = inspect_library(readelf, library, manifest)
        digest = sha256(library)
        expected = manifest["abis"][abi]["sha256"]
        log(abi + " " + digest + " (" + str(loads) + " LOAD segments aligned to 16 KB, " +
            str(exports) + " JNI exports)")
        if digest != expected:
            mismatches.append(abi + ": built " + digest + ", manifest " + expected)
    if mismatches:
        message = ("Built libraries differ from runtime-utils/native/sr25519/reproducible-build.json:\n  " +
                   "\n  ".join(mismatches) +
                   "\nIf the Rust source, Cargo.lock or toolchain changed on purpose, rerun with --record "
                   "(or ./gradlew :runtime-utils:recordSr25519NativeHashes) and commit the manifest.")
        if not allow_mismatch:
            raise BuildError(message)
        log("WARNING: " + message)


def inside_source_tree(path):
    """True for paths under a src/ directory of this repository."""
    try:
        relative = Path(path).resolve().relative_to(ROOT)
    except ValueError:
        return False
    return "src" in relative.parts


def publish(built, output):
    """Copies the verified libraries to output/<abi>/, never into a source tree."""
    output = Path(output)
    if inside_source_tree(output):
        raise BuildError("Refusing to write native libraries into a source tree: " + str(output))
    for abi, library in built.items():
        destination = output / abi
        destination.mkdir(parents=True, exist_ok=True)
        target = destination / LIBRARY
        if target.is_symlink() or target.exists():
            target.unlink()
        shutil.copyfile(library, target)


def record(built, manifest_path=MANIFEST):
    manifest = json.loads(Path(manifest_path).read_text(encoding="utf-8"))
    for abi, library in built.items():
        manifest["abis"][abi]["sha256"] = sha256(library)
    Path(manifest_path).write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    log("Recorded new SHA-256 values in " + str(manifest_path))


def reproducibility_check(ndk, manifest, work_root):
    """Two clean builds in different target directories must be byte-identical."""
    work_root = Path(work_root)
    work_root.mkdir(parents=True, exist_ok=True)
    readelf = readelf_path(ndk)
    results = []
    for attempt in (1, 2):
        target_dir = Path(tempfile.mkdtemp(prefix="sr25519-clean-" + str(attempt) + "-", dir=work_root))
        built = build_android(None, target_dir, ndk, manifest)
        results.append({abi: sha256(path) for abi, path in built.items()})
        verify(built, manifest, readelf)
    if results[0] != results[1]:
        raise BuildError("Two clean builds differ: " + json.dumps(results))
    log("Two clean builds are byte-identical and match the manifest")


def build_host(output, target_dir, manifest):
    """Builds the host library that the JVM unit tests load."""
    environment = clean_environment()
    check_toolchain(environment, manifest, needs_cargo_ndk=False)
    environment["CARGO_TARGET_DIR"] = str(Path(target_dir).resolve())
    run(["cargo", "build", "--release", "--locked"], environment)
    names = {"Darwin": "libsr25519java.dylib", "Linux": "libsr25519java.so", "Windows": "sr25519java.dll"}
    name = names.get(platform.system())
    if name is None:
        raise BuildError("Unsupported host system: " + platform.system())
    library = Path(target_dir) / "release" / name
    if not library.is_file():
        raise BuildError("cargo produced no host library at " + str(library))
    output = Path(output)
    if inside_source_tree(output):
        raise BuildError("Refusing to write the host library into a source tree: " + str(output))
    output.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(library, output / name)
    log("Host library " + str(output / name))


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--output", default=str(ROOT / "build" / "native" / "sr25519" / "jniLibs"))
    parser.add_argument("--target-dir", default=str(ROOT / "build" / "native" / "sr25519" / "target"))
    parser.add_argument("--ndk", help="NDK directory (default: $ANDROID_HOME/ndk/<manifest ndkVersion>)")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--record", action="store_true", help="write the built SHA-256 values to the manifest")
    mode.add_argument("--allow-hash-mismatch", action="store_true",
                      help="warn instead of failing on a SHA-256 mismatch (never for release builds)")
    mode.add_argument("--reproducibility-check", action="store_true")
    mode.add_argument("--host-output", help="build the host library for JVM tests into this directory")
    args = parser.parse_args(argv)
    try:
        for destination in (args.output, args.host_output):
            if destination and inside_source_tree(destination):
                raise BuildError("Refusing to write native libraries into a source tree: " + destination)
        manifest = load_manifest()
        if args.host_output:
            build_host(args.host_output, args.target_dir, manifest)
            return 0
        ndk = resolve_ndk(args.ndk, manifest)
        if args.reproducibility_check:
            reproducibility_check(ndk, manifest, Path(args.target_dir))
            return 0
        built = build_android(args.output, args.target_dir, ndk, manifest)
        if args.record:
            record(built)
            manifest = load_manifest()
        verify(built, manifest, readelf_path(ndk), allow_mismatch=args.allow_hash_mismatch)
        publish(built, args.output)
        log("Published " + LIBRARY + " for " + ", ".join(built) + " to " + args.output)
        return 0
    except (BuildError, OSError, ValueError, KeyError) as error:
        print("[sr25519-build][error] " + str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
