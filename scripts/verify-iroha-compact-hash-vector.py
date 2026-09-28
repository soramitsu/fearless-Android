#!/usr/bin/env python3
"""Verify the staged Iroha transaction hash against an independent compact vector."""

from __future__ import annotations

import argparse
import base64
import binascii
import hashlib
import os
import stat
import struct
import tempfile
from pathlib import Path
from typing import Callable


MAX_VECTOR_BYTES = 32 * 1024
EXPECTED = {
    "schema.version": "1",
    "source.tag": "v2.0.0-rc.2.1-fearless-mobile-sdk.3",
    "source.commit": "4f8cfbdd17aa6a3b049e619f23ec02501e5297b6",
    "reference": "release-tag-rust-framing-plus-current-local-native-host-diagnostic",
    "versioned.bytes": "565",
    "versioned.sha256": "73dd9a04a34c0acb5c4b44021389bd06910a60eeab05936c3afe425aa5374c7e",
    "bare.bytes": "564",
    "compact.length.hex": "b404",
    "canonical.prefix.hex": "00000000b404",
    "canonical.hash": "2332d0004eb24d97fd965fe68f6f31b0e51339764b4dd80f3ea50a3b6f7e5003",
    "pinned.sdk.defective.hash": "2b5e69a0a3d333756f4ac2a54bf7eaff88a2c87484d89e6e7da98abea659662d",
}
REQUIRED_KEYS = frozenset(EXPECTED) | {"versioned.base64"}


class VectorError(ValueError):
    pass


def fail(message: str) -> "None":
    raise VectorError(message)


def read_regular_snapshot(path: Path) -> bytes:
    flags = os.O_RDONLY
    flags |= getattr(os, "O_CLOEXEC", 0)
    flags |= getattr(os, "O_NOFOLLOW", 0)
    try:
        descriptor = os.open(path, flags)
    except OSError as error:
        fail(f"cannot open vector as a no-follow regular file: {error}")
    try:
        before = os.fstat(descriptor)
        if not stat.S_ISREG(before.st_mode):
            fail("vector is not a regular file")
        if before.st_size <= 0 or before.st_size > MAX_VECTOR_BYTES:
            fail("vector size is outside the bounded range")
        chunks: list[bytes] = []
        remaining = MAX_VECTOR_BYTES + 1
        while remaining > 0:
            chunk = os.read(descriptor, min(8192, remaining))
            if not chunk:
                break
            chunks.append(chunk)
            remaining -= len(chunk)
        data = b"".join(chunks)
        after = os.fstat(descriptor)
        identity_before = (
            before.st_dev,
            before.st_ino,
            before.st_size,
            before.st_mtime_ns,
            before.st_ctime_ns,
        )
        identity_after = (
            after.st_dev,
            after.st_ino,
            after.st_size,
            after.st_mtime_ns,
            after.st_ctime_ns,
        )
        if identity_before != identity_after or len(data) != before.st_size:
            fail("vector changed while it was being read")
        return data
    finally:
        os.close(descriptor)


def parse_properties(data: bytes) -> dict[str, str]:
    if b"\r" in data:
        fail("carriage returns are forbidden")
    try:
        text = data.decode("ascii")
    except UnicodeDecodeError as error:
        fail(f"vector must be ASCII: {error}")
    if not text.endswith("\n"):
        fail("vector must end with one newline")

    values: dict[str, str] = {}
    for line_number, line in enumerate(text[:-1].split("\n"), start=1):
        if not line or line.startswith("#") or "=" not in line:
            fail(f"malformed vector line {line_number}")
        key, value = line.split("=", 1)
        if not key or not all(character.islower() or character.isdigit() or character == "." for character in key):
            fail(f"invalid key on vector line {line_number}")
        if not value or value != value.strip() or any(ord(character) < 0x21 for character in value):
            fail(f"invalid value on vector line {line_number}")
        if key in values:
            fail(f"duplicate vector key: {key}")
        values[key] = value

    missing = REQUIRED_KEYS - values.keys()
    unknown = values.keys() - REQUIRED_KEYS
    if missing:
        fail(f"missing vector keys: {sorted(missing)}")
    if unknown:
        fail(f"unknown vector keys: {sorted(unknown)}")
    return values


def encode_minimal_uleb128(value: int) -> bytes:
    if value < 0 or value > 0x7FFFFFFF:
        fail("compact length is outside the signed Java byte-array range")
    encoded = bytearray()
    while True:
        current = value & 0x7F
        value >>= 7
        if value:
            current |= 0x80
        encoded.append(current)
        if not value:
            return bytes(encoded)


def decode_minimal_uleb128(encoded: bytes) -> int:
    if not encoded or len(encoded) > 5:
        fail("invalid compact length size")
    value = 0
    terminated = False
    for index, current in enumerate(encoded):
        payload = current & 0x7F
        if index == 4 and payload > 0x07:
            fail("compact length exceeds the signed Java byte-array range")
        value |= payload << (index * 7)
        if not current & 0x80:
            if index != len(encoded) - 1:
                fail("compact length has trailing bytes")
            terminated = True
            break
    if not terminated:
        fail("compact length is unterminated")
    if encode_minimal_uleb128(value) != encoded:
        fail("compact length is overlong")
    return value


def iroha_prehash(message: bytes) -> str:
    digest = bytearray(hashlib.blake2b(message, digest_size=32).digest())
    digest[-1] |= 1
    return digest.hex()


def verify_values(values: dict[str, str]) -> None:
    for key, expected in EXPECTED.items():
        if values.get(key) != expected:
            fail(f"pinned vector value drifted for {key}")

    encoded_base64 = values["versioned.base64"]
    try:
        versioned = base64.b64decode(encoded_base64, validate=True)
    except (binascii.Error, ValueError) as error:
        fail(f"versioned transaction is not strict Base64: {error}")
    if base64.b64encode(versioned).decode("ascii") != encoded_base64:
        fail("versioned transaction Base64 is non-canonical")
    if len(versioned) != int(values["versioned.bytes"]):
        fail("versioned transaction byte length drifted")
    if hashlib.sha256(versioned).hexdigest() != values["versioned.sha256"]:
        fail("versioned transaction SHA-256 drifted")
    if len(versioned) <= 1 or versioned[0] != 1:
        fail("versioned transaction must use exact version byte 1")

    bare = versioned[1:]
    if len(bare) != int(values["bare.bytes"]):
        fail("bare transaction byte length drifted")
    compact = encode_minimal_uleb128(len(bare))
    if decode_minimal_uleb128(compact) != len(bare):
        fail("minimal compact length failed to round-trip")
    if compact.hex() != values["compact.length.hex"]:
        fail("compact transaction length drifted")

    prefix = b"\x00\x00\x00\x00" + compact
    if prefix.hex() != values["canonical.prefix.hex"]:
        fail("canonical transaction entrypoint prefix drifted")
    canonical_hash = iroha_prehash(prefix + bare)
    if canonical_hash != values["canonical.hash"]:
        fail("canonical compact transaction hash drifted")

    fixed_u64_hash = iroha_prehash(b"\x00\x00\x00\x00" + struct.pack("<Q", len(bare)) + bare)
    if fixed_u64_hash != values["pinned.sdk.defective.hash"]:
        fail("pinned SDK fixed-u64 defect vector drifted")
    if fixed_u64_hash == canonical_hash:
        fail("defective fixed-u64 and canonical compact hashes unexpectedly match")

    overlong = compact[:-1] + bytes((compact[-1] | 0x80, 0x00))
    try:
        decode_minimal_uleb128(overlong)
    except VectorError as error:
        if "overlong" not in str(error):
            fail(f"overlong compact length failed for the wrong reason: {error}")
    else:
        fail("overlong compact transaction length was accepted")
    if iroha_prehash(b"\x00\x00\x00\x00" + overlong + bare) == canonical_hash:
        fail("overlong and canonical compact hashes unexpectedly match")


def verify_path(path: Path) -> None:
    verify_values(parse_properties(read_regular_snapshot(path)))


def expect_rejected(label: str, operation: Callable[[], object]) -> None:
    try:
        operation()
    except (VectorError, OSError):
        return
    fail(f"adversarial self-test unexpectedly accepted {label}")


def self_test(path: Path) -> int:
    data = read_regular_snapshot(path)
    values = parse_properties(data)
    verify_values(values)
    count = 0

    def mutated(key: str, value: str) -> dict[str, str]:
        changed = dict(values)
        changed[key] = value
        return changed

    mutations = {
        "schema drift": ("schema.version", "2"),
        "tag drift": ("source.tag", "v0"),
        "commit drift": ("source.commit", "0" * 40),
        "reference drift": ("reference", "unreviewed"),
        "versioned length drift": ("versioned.bytes", "564"),
        "versioned digest drift": ("versioned.sha256", "0" * 64),
        "bare length drift": ("bare.bytes", "563"),
        "fixed-u64 framing": ("compact.length.hex", "3402000000000000"),
        "overlong framing": ("compact.length.hex", "b48400"),
        "entrypoint prefix drift": ("canonical.prefix.hex", "00000000b48400"),
        "canonical hash drift": ("canonical.hash", "0" * 64),
        "defective hash drift": ("pinned.sdk.defective.hash", "0" * 64),
        "malformed Base64": ("versioned.base64", "!!!="),
    }
    for label, (key, value) in mutations.items():
        expect_rejected(label, lambda key=key, value=value: verify_values(mutated(key, value)))
        count += 1

    changed_transaction = bytearray(base64.b64decode(values["versioned.base64"], validate=True))
    changed_transaction[-1] ^= 1
    expect_rejected(
        "transaction-byte drift",
        lambda: verify_values(mutated("versioned.base64", base64.b64encode(changed_transaction).decode("ascii"))),
    )
    count += 1

    malformed_property_inputs = {
        "duplicate key": data + b"schema.version=1\n",
        "unknown key": data + b"unexpected.key=value\n",
        "missing newline": data.rstrip(b"\n"),
        "carriage return": data.replace(b"\n", b"\r\n", 1),
        "non-ASCII": data + b"\xff=value\n",
        "blank line": b"\n" + data,
    }
    for label, malformed in malformed_property_inputs.items():
        expect_rejected(label, lambda malformed=malformed: parse_properties(malformed))
        count += 1

    malformed_uleb = {
        "zero overlong": b"\x80\x00",
        "one overlong": b"\x81\x00",
        "unterminated": b"\x80",
        "trailing byte": b"\x00\x00",
        "overflow": b"\x80\x80\x80\x80\x08",
        "too long": b"\x80\x80\x80\x80\x80\x00",
        "empty": b"",
    }
    for label, encoded in malformed_uleb.items():
        expect_rejected(label, lambda encoded=encoded: decode_minimal_uleb128(encoded))
        count += 1

    boundaries = (0, 1, 127, 128, 16_383, 16_384, 2_097_151, 2_097_152, 268_435_455, 268_435_456, 0x7FFFFFFF)
    for value in boundaries:
        if decode_minimal_uleb128(encode_minimal_uleb128(value)) != value:
            fail(f"compact boundary failed to round-trip: {value}")

    with tempfile.TemporaryDirectory(prefix="iroha-vector-self-test.") as temporary:
        symlink = Path(temporary) / "vector.properties"
        symlink.symlink_to(path.resolve())
        expect_rejected("symlink vector", lambda: verify_path(symlink))
        count += 1

        oversized = Path(temporary) / "oversized.properties"
        oversized.write_bytes(b"x" * (MAX_VECTOR_BYTES + 1))
        expect_rejected("oversized vector", lambda: verify_path(oversized))
        count += 1

    return count


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("vector", type=Path)
    parser.add_argument("--self-test", action="store_true")
    arguments = parser.parse_args()
    try:
        if arguments.self_test:
            count = self_test(arguments.vector)
            print(f"[iroha-compact-hash-vector] {count} adversarial scenarios and 11 boundaries passed")
        else:
            verify_path(arguments.vector)
        print("[iroha-compact-hash-vector] canonical Rust/native diagnostic vector passed")
        return 0
    except VectorError as error:
        print(f"[iroha-compact-hash-vector][error] {error}", file=__import__("sys").stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
