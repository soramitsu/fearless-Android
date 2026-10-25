#!/usr/bin/env python3
"""Extract the Room schema that an official Fearless Android APK creates.

Room generates `AppDatabase_Impl$1.createAllTables`, which runs one
`execSQL` per table and index, then writes the identity hash into
`room_master_table`. `AppDatabase_Impl.createOpenHelper` passes the schema
version to that delegate. Both classes ship un-minified in 1.x/2.x and are kept
by `-keep class jp.co.soramitsu.coredb.**` in 3.x and later, so the exact DDL a
release put on user devices can be read back from its dex files with
`dexdump -d`.

Subcommands:

  extract   Print the schema of one APK as JSON (optionally write the SQL).
  generate  Write every fixture listed in releases.json from the APK cache.
  check     Regenerate into memory and fail if any committed fixture differs.

The APKs are treated as data: they are unzipped into a private temporary
directory and only read by dexdump. Nothing from an APK is executed.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tempfile
import zipfile
from concurrent.futures import ProcessPoolExecutor
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Iterator


ROOM_IMPL_CLASSES = (
    "jp.co.soramitsu.core_db.AppDatabase_Impl",
    "jp.co.soramitsu.coredb.AppDatabase_Impl",
)
CREATE_ALL_TABLES = "createAllTables"
CREATE_OPEN_HELPER = "createOpenHelper"
EXEC_SQL = "execSQL"
ROOM_MASTER_INSERT = re.compile(
    r"^INSERT OR REPLACE INTO room_master_table \(id,identity_hash\) "
    r"VALUES\(42, '(?P<hash>[0-9a-f]{32})'\)$"
)
IDENTITY_HASH = re.compile(r"^[0-9a-f]{32}$")
DEFAULT_BUILD_TOOLS = "36.0.0"

FIXTURE_FORMAT = 2
SQL_NAME = "app-database-v{version}.sql"
MANIFEST_NAME = "fixture-manifest.json"
REPO_ROOT = Path(__file__).resolve().parents[3]
DEFAULT_RELEASES = Path(__file__).resolve().parent / "releases.json"
DEFAULT_ASSETS = REPO_ROOT / "core-db/src/androidTest/assets/migrations"
DEFAULT_SCHEMAS = REPO_ROOT / "core-db/schemas"

METHOD_HEADER = re.compile(
    r"^[0-9a-f]+:\s+\|\[[0-9a-f]+\] (?P<owner>\S+)\.(?P<name>[^.:]+):(?P<signature>\S+)$"
)
INSTRUCTION = re.compile(r"^[0-9a-f]+: [0-9a-f ]+\|[0-9a-f]{4}: (?P<op>.*)$")
CONST_STRING = re.compile(
    r'^const-string(?:/jumbo)? (?P<register>v\d+), "(?P<value>.*)" // string@[0-9a-f]+$'
)
CONST_INT = re.compile(
    r"^const(?:/4|/16|/high16)? (?P<register>v\d+), #int (?P<value>-?\d+)"
)
INVOKE = re.compile(
    r"^invoke-[a-z]+(?:/range)? \{(?P<registers>[^}]*)\}, "
    r"L(?P<owner>[^;]+);\.(?P<name>[^:]+):(?P<signature>\S+)"
)
MOVE = re.compile(r"^move(?:-object)?(?:/from16|/16)? (?P<target>v\d+), (?P<source>v\d+)$")


class ExtractionError(RuntimeError):
    pass


@dataclass
class Method:
    owner: str
    name: str
    signature: str
    instructions: list[str] = field(default_factory=list)


@dataclass(frozen=True)
class RoomSchema:
    apk_sha256: str
    apk_entry: str
    apk_entry_sha256: str
    room_class: str
    method: str
    room_version: int
    identity_hash: str
    legacy_identity_hash: str | None
    statements: tuple[str, ...]

    @property
    def statements_sha256(self) -> str:
        return sha256_bytes("\n".join(self.statements).encode("utf-8"))

    def to_json(self) -> dict:
        tables = [s for s in self.statements if s.startswith("CREATE TABLE")]
        indexes = [s for s in self.statements if s.startswith(("CREATE INDEX", "CREATE UNIQUE INDEX"))]
        views = [s for s in self.statements if s.startswith("CREATE VIEW")]
        return {
            "apk_sha256": self.apk_sha256,
            "apk_entry": self.apk_entry,
            "apk_entry_sha256": self.apk_entry_sha256,
            "class": self.room_class,
            "method": self.method,
            "room_version": self.room_version,
            "room_identity_hash": self.identity_hash,
            "room_legacy_identity_hash": self.legacy_identity_hash,
            "explicit_table_count_including_room_master": len(tables),
            "explicit_index_count": len(indexes),
            "explicit_view_count": len(views),
            "statements_sha256": self.statements_sha256,
            "statements": list(self.statements),
        }


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def unescape_dexdump(value: str) -> str:
    """Undo the escaping dexdump applies to string constants."""
    out: list[str] = []
    index = 0
    while index < len(value):
        char = value[index]
        if char != "\\":
            out.append(char)
            index += 1
            continue
        if index + 1 >= len(value):
            raise ExtractionError(f"dangling escape in dexdump string: {value!r}")
        code = value[index + 1]
        if code in "\\\"'":
            out.append(code)
            index += 2
        elif code == "n":
            out.append("\n")
            index += 2
        elif code == "r":
            out.append("\r")
            index += 2
        elif code == "t":
            out.append("\t")
            index += 2
        elif code == "u" and re.fullmatch(r"[0-9a-fA-F]{4}", value[index + 2:index + 6] or ""):
            out.append(chr(int(value[index + 2:index + 6], 16)))
            index += 6
        else:
            raise ExtractionError(f"unknown dexdump escape \\{code} in {value!r}")
    return "".join(out)


def dexdump_path(build_tools: str | None) -> Path:
    explicit = os.environ.get("DEXDUMP")
    if explicit:
        return Path(explicit)
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if not sdk:
        sdk = str(Path.home() / "Library/Android/sdk")
    candidate = Path(sdk) / "build-tools" / (build_tools or DEFAULT_BUILD_TOOLS) / "dexdump"
    if not candidate.is_file():
        raise ExtractionError(f"dexdump not found at {candidate}; set DEXDUMP or ANDROID_HOME")
    return candidate


def parse_room_methods(lines: Iterable[str]) -> Iterator[Method]:
    """Yield the disassembled methods of the generated Room classes from dexdump -d output."""
    current: Method | None = None
    for raw in lines:
        line = raw.rstrip("\n")
        header = METHOD_HEADER.match(line)
        if header:
            if current is not None:
                yield current
            owner = header.group("owner")
            current = None
            if owner.startswith(ROOM_IMPL_CLASSES):
                current = Method(owner, header.group("name"), header.group("signature"))
            continue
        if current is None:
            continue
        instruction = INSTRUCTION.match(line)
        if instruction:
            current.instructions.append(instruction.group("op"))
        elif line.strip().startswith("catches"):
            yield current
            current = None
    if current is not None:
        yield current


def iter_room_methods(dexdump: Path, dex: Path) -> Iterator[Method]:
    """Run dexdump -d on one dex and yield its generated Room methods."""
    process = subprocess.Popen(
        [str(dexdump), "-d", str(dex)],
        stdout=subprocess.PIPE,
        stderr=subprocess.DEVNULL,
        text=True,
        encoding="utf-8",
        errors="surrogateescape",
    )
    assert process.stdout is not None
    try:
        yield from parse_room_methods(process.stdout)
    finally:
        process.stdout.close()
        if process.wait() != 0:
            raise ExtractionError(f"dexdump failed for {dex.name}")


def registers_of(invoke: re.Match) -> list[str]:
    text = invoke.group("registers").strip()
    if not text:
        return []
    if ".." in text:
        start, end = (part.strip() for part in text.split(".."))
        prefix = start[0]
        return [f"{prefix}{number}" for number in range(int(start[1:]), int(end[1:]) + 1)]
    return [part.strip() for part in text.split(",")]


def parameter_types(signature: str) -> list[str]:
    """Split a dex method signature such as (Lr3/i;I)V into parameter types."""
    inside = signature[1:signature.index(")")]
    types = re.findall(r"\[*(?:L[^;]+;|[ZBSCIJFD])", inside)
    return [item[1:-1] if item.startswith("L") else item for item in types]


def is_exec_sql(invoke: re.Match, connection_type: str) -> bool:
    """Recognise execSQL even where R8 renamed the androidx.sqlite interfaces.

    Room up to 2.6 calls SupportSQLiteDatabase.execSQL(String) on the
    createAllTables parameter; 2.1.0-3.1.2 ship that interface renamed (for
    example Lr3/i;.u). Room 2.7 calls the static SQLite.execSQL(connection, sql).
    """
    if invoke.group("name") == EXEC_SQL:
        return True
    signature = invoke.group("signature")
    if invoke.group("owner") == connection_type and signature == "(Ljava/lang/String;)V":
        return True
    return signature == f"(L{connection_type};Ljava/lang/String;)V"


def executed_sql(method: Method) -> list[str]:
    """Return the string constants passed to execSQL, in execution order."""
    connection_type = parameter_types(method.signature)[0]
    strings: dict[str, str] = {}
    statements: list[str] = []
    for op in method.instructions:
        constant = CONST_STRING.match(op)
        if constant:
            strings[constant.group("register")] = unescape_dexdump(constant.group("value"))
            continue
        move = MOVE.match(op)
        if move and move.group("source") in strings:
            strings[move.group("target")] = strings[move.group("source")]
            continue
        invoke = INVOKE.match(op)
        if invoke and is_exec_sql(invoke, connection_type):
            argument = registers_of(invoke)[-1]
            if argument not in strings:
                raise ExtractionError(
                    f"{method.owner}.{method.name} calls execSQL with a non-constant argument"
                )
            statements.append(strings[argument])
    return statements


def open_helper_identity(method: Method, delegate: str) -> tuple[int, str, str | None]:
    """Read the version passed to the delegate and the hashes passed to Room."""
    ints: dict[str, int] = {}
    strings: list[str] = []
    version: int | None = None
    for op in method.instructions:
        constant = CONST_INT.match(op)
        if constant:
            ints[constant.group("register")] = int(constant.group("value"))
            continue
        string = CONST_STRING.match(op)
        if string:
            value = unescape_dexdump(string.group("value"))
            if version is not None and IDENTITY_HASH.match(value):
                strings.append(value)
            continue
        invoke = INVOKE.match(op)
        if invoke and invoke.group("owner").replace("/", ".") == delegate and invoke.group("name") == "<init>":
            argument = registers_of(invoke)[-1]
            if argument not in ints:
                raise ExtractionError(f"{delegate}.<init> receives a non-constant schema version")
            version = ints[argument]
    if version is None or not strings:
        raise ExtractionError(f"{method.owner}.{method.name} does not construct {delegate}")
    return version, strings[0], strings[1] if len(strings) > 1 else None


def open_delegate_identity(method: Method) -> tuple[int, str, str | None]:
    """Room 2.7: the delegate constructor passes (version, identity, legacy) to super."""
    ints: dict[str, int] = {}
    strings: dict[str, str] = {}
    for op in method.instructions:
        constant = CONST_INT.match(op)
        if constant:
            ints[constant.group("register")] = int(constant.group("value"))
            continue
        string = CONST_STRING.match(op)
        if string:
            strings[string.group("register")] = unescape_dexdump(string.group("value"))
            continue
        invoke = INVOKE.match(op)
        if (
            invoke
            and invoke.group("name") == "<init>"
            and invoke.group("signature") == "(ILjava/lang/String;Ljava/lang/String;)V"
        ):
            registers = registers_of(invoke)
            if registers[1] not in ints or registers[2] not in strings:
                raise ExtractionError(f"{method.owner}.<init> passes non-constant schema identity")
            return ints[registers[1]], strings[registers[2]], strings.get(registers[3])
    raise ExtractionError(f"{method.owner}.<init> does not pass a schema version")


def extract_from_apk(apk: Path, dexdump: Path) -> RoomSchema:
    apk_sha256 = sha256_file(apk)
    with tempfile.TemporaryDirectory(prefix="room-schema-") as work:
        workdir = Path(work)
        with zipfile.ZipFile(apk) as archive:
            entries = sorted(
                (name for name in archive.namelist() if re.fullmatch(r"classes\d*\.dex", name)),
                key=lambda name: int(name[7:-4] or "1"),
            )
            if not entries:
                raise ExtractionError(f"{apk.name} has no classes.dex")
            for entry in entries:
                target = workdir / entry
                with archive.open(entry) as source, target.open("wb") as destination:
                    shutil.copyfileobj(source, destination)
        found: list[tuple[str, Method]] = []
        helpers: list[Method] = []
        constructors: dict[str, Method] = {}
        for entry in entries:
            for method in iter_room_methods(dexdump, workdir / entry):
                if method.name == CREATE_ALL_TABLES:
                    found.append((entry, method))
                elif method.name == CREATE_OPEN_HELPER and method.owner in ROOM_IMPL_CLASSES:
                    helpers.append(method)
                elif method.name == "<init>":
                    constructors[method.owner] = method
        if len(found) != 1:
            owners = [method.owner for _, method in found]
            raise ExtractionError(f"{apk.name}: expected one createAllTables, found {owners}")
        entry, method = found[0]
        entry_sha256 = sha256_file(workdir / entry)
        if len(helpers) == 1:
            version, identity, legacy = open_helper_identity(helpers[0], method.owner)
        elif not helpers and method.owner in constructors:
            version, identity, legacy = open_delegate_identity(constructors[method.owner])
        else:
            raise ExtractionError(f"{apk.name}: cannot locate the Room schema version")

    statements = executed_sql(method)
    if not statements:
        raise ExtractionError(f"{apk.name}: createAllTables executes no SQL")
    for statement in statements:
        if "\n" in statement or "\r" in statement or statement.strip() != statement:
            raise ExtractionError(f"{apk.name}: unexpected whitespace in {statement!r}")
    master = ROOM_MASTER_INSERT.match(statements[-1])
    if not master:
        raise ExtractionError(f"{apk.name}: createAllTables does not end with the identity insert")
    if identity != master.group("hash"):
        raise ExtractionError(
            f"{apk.name}: identity hash {identity} passed to Room differs from "
            f"{master.group('hash')} written by createAllTables"
        )
    return RoomSchema(
        apk_sha256=apk_sha256,
        apk_entry=entry,
        apk_entry_sha256=entry_sha256,
        room_class=method.owner,
        method=CREATE_ALL_TABLES,
        room_version=version,
        identity_hash=identity,
        legacy_identity_hash=legacy,
        statements=tuple(statements),
    )


def exported_schema_statements(schema_json: Path) -> tuple[int, str, tuple[str, ...]]:
    """Rebuild createAllTables from a Room exported schema, in Room's order."""
    document = json.loads(schema_json.read_text(encoding="utf-8"))
    database = document["database"]
    statements: list[str] = []
    for entity in database["entities"]:
        table = entity["tableName"]
        statements.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            statements.append(index["createSql"].replace("${TABLE_NAME}", table))
    for view in database.get("views", []):
        statements.append(view["createSql"].replace("${VIEW_NAME}", view["viewName"]))
    statements.extend(database["setupQueries"])
    identity = database["identityHash"]
    statements = [
        statement.replace("${IDENTITY_HASH}", identity) if "room_master_table" in statement else statement
        for statement in statements
    ]
    return int(database["version"]), identity, tuple(statements)


def render_sql(release: dict, version: int, room_class: str, statements: Iterable[str], source: str) -> str:
    header = [
        f"-- Frozen Room schema version {version} of Fearless Wallet {release['version']} "
        f"({release['versionCode']}).",
        f"-- Source: {source}",
        f"-- Room class: {room_class}.createAllTables",
        "-- Generated by tools/upgrade-fixtures/android/extract_room_schema.py. Do not",
        "-- edit by hand or regenerate from current entities; provenance and hashes",
        "-- live in fixture-manifest.json.",
        "",
    ]
    body = [f"{statement};" for statement in statements]
    footer = ["", f"PRAGMA user_version = {version};", ""]
    return "\n".join(header + body + footer)


def validate_with_sqlite(sql: str, version: int, identity: str | None) -> dict:
    """Execute the DDL in an in-memory SQLite database and describe the result."""
    connection = sqlite3.connect(":memory:")
    try:
        connection.execute("PRAGMA foreign_keys = ON")
        connection.executescript(sql)
        actual_version = connection.execute("PRAGMA user_version").fetchone()[0]
        if actual_version != version:
            raise ExtractionError(f"user_version {actual_version} != {version}")
        if identity is not None:
            stored = connection.execute(
                "SELECT identity_hash FROM room_master_table WHERE id = 42"
            ).fetchone()
            if stored is None or stored[0] != identity:
                raise ExtractionError("room_master_table does not hold the identity hash")
        if connection.execute("PRAGMA integrity_check").fetchone()[0] != "ok":
            raise ExtractionError("integrity_check failed on the generated schema")
        tables = sorted(
            row[0]
            for row in connection.execute(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'"
            )
        )
        indexes = connection.execute(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND sql IS NOT NULL"
        ).fetchone()[0]
        return {"tables": tables, "explicit_index_count": indexes}
    finally:
        connection.close()


def load_releases(path: Path) -> dict:
    document = json.loads(path.read_text(encoding="utf-8"))
    if document.get("format") != 1:
        raise ExtractionError(f"{path} has an unknown format")
    ids = [release["id"] for release in document["releases"]]
    if len(ids) != len(set(ids)):
        raise ExtractionError(f"{path} lists a release id twice")
    return document


def release_by_id(releases: dict, release_id: str) -> dict:
    for release in releases["releases"]:
        if release["id"] == release_id:
            return release
    raise ExtractionError(f"releases.json has no release {release_id}")


def verified_apk(release: dict, apk_root: Path) -> Path:
    binary = release["binary"]
    if binary.get("kind") != "github-apk":
        raise ExtractionError(f"{release['id']} has no GitHub APK")
    apk = apk_root / binary["cache_path"]
    if not apk.is_file():
        raise ExtractionError(f"missing APK {apk}; run fetch_release_apks.sh first")
    actual = sha256_file(apk)
    if actual != binary["sha256"]:
        raise ExtractionError(f"{apk.name}: sha256 {actual} != pinned {binary['sha256']}")
    return apk


def _extract_release(task: tuple[str, str, str]) -> tuple[str, RoomSchema]:
    release_id, apk, dexdump = task
    return release_id, extract_from_apk(Path(apk), Path(dexdump))


def extract_releases(
    releases: dict,
    release_ids: Iterable[str],
    apk_root: Path,
    dexdump: Path,
    jobs: int,
    log,
) -> dict[str, RoomSchema]:
    """Extract and check the APK of every listed release, in parallel processes."""
    wanted = [release_by_id(releases, release_id) for release_id in sorted(set(release_ids))]
    tasks = [(release["id"], str(verified_apk(release, apk_root)), str(dexdump)) for release in wanted]
    with ProcessPoolExecutor(max_workers=max(1, jobs)) as pool:
        extracted = dict(pool.map(_extract_release, tasks))
    for release in wanted:
        schema = extracted[release["id"]]
        log(f"[extract] {release['id']}: Room {schema.room_version} {schema.identity_hash}")
        if schema.room_version != release["db_version"]:
            raise ExtractionError(
                f"{release['id']}: APK creates Room {schema.room_version}, "
                f"releases.json says {release['db_version']}"
            )
        if release.get("room_identity_hash") != schema.identity_hash:
            raise ExtractionError(
                f"{release['id']}: identity hash {schema.identity_hash} != pinned "
                f"{release.get('room_identity_hash')}"
            )
    return extracted


def fixture_apk_releases(releases: dict, fixture: dict) -> list[str]:
    """Releases whose APK must be extracted to build or cross-check a fixture."""
    source = fixture["source"]
    needed = [
        member for member in fixture["releases"]
        if release_by_id(releases, member)["binary"].get("kind") == "github-apk"
    ]
    if "apk_release" in source:
        needed.append(source["apk_release"])
    if "unchanged_tables_match_apk" in source:
        needed.append(source["unchanged_tables_match_apk"]["apk_release"])
    return needed


def create_table_statements(statements: Iterable[str]) -> dict[str, str]:
    tables: dict[str, str] = {}
    for statement in statements:
        match = re.match(r"CREATE TABLE IF NOT EXISTS `([^`]+)`", statement)
        if match:
            tables[match.group(1)] = statement
    return tables


def fixture_statements(
    releases: dict,
    fixture: dict,
    extracted: dict[str, RoomSchema],
    schemas_root: Path,
) -> tuple[tuple[str, ...], str | None, str, dict, str, str]:
    """Return (statements, identity, room class, extraction record, generator method, source text)."""
    source = fixture["source"]
    kind = source["kind"]
    version = fixture["db_version"]
    if kind == "github-apk":
        schema = extracted[source["apk_release"]]
        extraction = schema.to_json()
        extraction.pop("statements")
        release = release_by_id(releases, source["apk_release"])
        text = f"official APK {release['binary']['asset_name']} ({schema.apk_entry})"
        return (
            schema.statements, schema.identity_hash, schema.room_class, extraction,
            "dexdump-createAllTables", text,
        )
    if kind == "derived-from-apk":
        schema = extracted[source["apk_release"]]
        removed = source["remove_statements"]
        missing = [statement for statement in removed if statement not in schema.statements]
        if missing:
            raise ExtractionError(f"fixture {fixture['directory']}: {missing} not in {source['apk_release']}")
        statements = tuple(
            statement for statement in schema.statements
            if statement not in removed and not ROOM_MASTER_INSERT.match(statement)
        )
        release = release_by_id(releases, source["apk_release"])
        extraction = {
            "derived_from_release": source["apk_release"],
            "derived_from_apk_sha256": schema.apk_sha256,
            "derived_from_apk_entry": schema.apk_entry,
            "derived_from_apk_entry_sha256": schema.apk_entry_sha256,
            "derived_from_room_version": schema.room_version,
            "derived_from_room_identity_hash": schema.identity_hash,
            "removed_statements": removed,
            "identity_insert_removed": True,
            "class": schema.room_class,
            "method": CREATE_ALL_TABLES,
            "room_version": version,
            "room_identity_hash": None,
            "statements_sha256": sha256_bytes("\n".join(statements).encode("utf-8")),
        }
        text = (
            f"official APK {release['binary']['asset_name']} ({schema.apk_entry}) "
            f"without the statements Migration_{version}_{schema.room_version} adds"
        )
        return statements, None, schema.room_class, extraction, "dexdump-createAllTables-derived", text
    if kind == "exported-schema-json":
        schema_path = schemas_root / source["exported_schema"]
        json_version, identity, statements = exported_schema_statements(schema_path)
        if json_version != version:
            raise ExtractionError(f"{schema_path} is version {json_version}, not {version}")
        check = source.get("unchanged_tables_match_apk")
        matched: list[str] = []
        if check:
            apk_tables = create_table_statements(extracted[check["apk_release"]].statements)
            json_tables = create_table_statements(statements)
            for table in check["tables"]:
                if apk_tables.get(table) is None or apk_tables.get(table) != json_tables.get(table):
                    raise ExtractionError(
                        f"fixture {fixture['directory']}: table {table} differs from {check['apk_release']}"
                    )
                matched.append(table)
        extraction = {
            "exported_schema": f"core-db/schemas/{source['exported_schema']}",
            "exported_schema_sha256": sha256_file(schema_path),
            "class": source["room_class"],
            "method": CREATE_ALL_TABLES,
            "room_version": version,
            "room_identity_hash": identity,
            "statements_sha256": sha256_bytes("\n".join(statements).encode("utf-8")),
        }
        if check:
            extraction["tables_byte_equal_to_apk"] = {
                "release": check["apk_release"],
                "apk_sha256": extracted[check["apk_release"]].apk_sha256,
                "tables": matched,
            }
        text = f"Room exported schema core-db/schemas/{source['exported_schema']}"
        return statements, identity, source["room_class"], extraction, "room-exported-schema", text
    raise ExtractionError(f"fixture {fixture['directory']}: unsupported source kind {kind}")


def render_fixture(
    releases: dict,
    fixture: dict,
    extracted: dict[str, RoomSchema],
    schemas_root: Path,
) -> dict[str, str]:
    version = fixture["db_version"]
    source_release = release_by_id(releases, fixture["source_release"])
    statements, identity, room_class, extraction, method, source_text = fixture_statements(
        releases, fixture, extracted, schemas_root
    )
    if fixture.get("room_identity_hash") != identity:
        raise ExtractionError(
            f"fixture {fixture['directory']}: identity hash {identity} != pinned {fixture.get('room_identity_hash')}"
        )
    members = [release_by_id(releases, member) for member in fixture["releases"]]
    for member in members:
        schema = extracted.get(member["id"])
        if schema is not None and schema.statements != statements:
            raise ExtractionError(
                f"{member['id']} shares fixture {fixture['directory']} but its APK creates a different schema"
            )

    sql_name = SQL_NAME.format(version=version)
    sql = render_sql(source_release, version, room_class, statements, source_text)
    description = validate_with_sqlite(sql, version, identity)
    manifest = {
        "fixture_format": FIXTURE_FORMAT,
        "platform": "android",
        "purpose": (
            f"Room schema version {version} exactly as a fresh install of the released app created it. "
            "AllReleasedSchemasUpgradeTest builds a database from it, inserts synthetic rows and "
            "upgrades it with the current migration graph."
        ),
        "db_version": version,
        "source_release": release_provenance(source_release),
        "releases_with_this_schema": [
            {
                "id": member["id"],
                "tag": member["tag"],
                "binary_kind": member["binary"]["kind"],
                "schema_evidence": member["schema_evidence"],
            }
            for member in members
        ],
        "generator": {
            "tier": "T1",
            "method": method,
            "tool": "tools/upgrade-fixtures/android/extract_room_schema.py",
            "inventory": "tools/upgrade-fixtures/android/releases.json",
        },
        "schema_extraction": extraction,
        "schema_check": {
            "engine": "sqlite3 (host, in-memory)",
            "integrity_check": "ok",
            "tables": description["tables"],
            "explicit_index_count": description["explicit_index_count"],
        },
        "frozen_assets": {
            "schema_sql": sql_name,
            "schema_sql_sha256": sha256_bytes(sql.encode("utf-8")),
        },
        "synthetic_only": True,
        "contains_user_material": False,
    }
    return {
        sql_name: sql,
        MANIFEST_NAME: json.dumps(manifest, indent=2, ensure_ascii=False) + "\n",
    }


def release_provenance(release: dict) -> dict:
    binary = release["binary"]
    out = {
        "id": release["id"],
        "version": release["version"],
        "versionCode": release["versionCode"],
        "tag": release["tag"],
        "commit": release["commit"],
        "binary": {"kind": binary["kind"]},
    }
    for key in ("asset_name", "url", "sha256", "signer_sha256", "size_bytes"):
        if key in binary:
            out["binary"][key] = binary[key]
    for key in ("source_commit_estimate", "tag_source_db_version"):
        if key in release:
            out[key] = release[key]
    return out


def verify_format1(fixture: dict, extracted: dict[str, RoomSchema], assets_root: Path) -> None:
    """The frozen format-1 fixtures must hold exactly the DDL of their APK."""
    schema = extracted[fixture["source"]["apk_release"]]
    sql_path = assets_root / fixture["directory"] / SQL_NAME.format(version=fixture["db_version"])
    frozen = tuple(
        line[:-1]
        for line in sql_path.read_text(encoding="utf-8").splitlines()
        if line.endswith(";") and not line.startswith(("--", "PRAGMA"))
    )
    if frozen != schema.statements:
        raise ExtractionError(f"frozen fixture {fixture['directory']} differs from its APK's createAllTables")
    if schema.identity_hash != fixture["room_identity_hash"]:
        raise ExtractionError(f"frozen fixture {fixture['directory']}: identity hash mismatch")


def verify_tags(releases: dict, repository: Path) -> None:
    """Check each tag's commit and the Room version its sources declare."""
    for release in releases["releases"]:
        commit = subprocess.run(
            ["git", "-C", str(repository), "rev-parse", f"{release['tag']}^{{commit}}"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()
        if commit != release["commit"]:
            raise ExtractionError(f"{release['id']}: tag {release['tag']} is {commit}, not {release['commit']}")
        files = subprocess.run(
            ["git", "-C", str(repository), "ls-tree", "-r", "--name-only", commit, "--", "core-db/src/main"],
            check=True, capture_output=True, text=True,
        ).stdout.splitlines()
        database = next(name for name in files if name.endswith("/AppDatabase.kt"))
        text = subprocess.run(
            ["git", "-C", str(repository), "show", f"{commit}:{database}"],
            check=True, capture_output=True, text=True,
        ).stdout
        declared = int(re.search(r"version = (\d+)", text).group(1))
        expected = release.get("tag_source_db_version", release["db_version"])
        if declared != expected:
            raise ExtractionError(f"{release['id']}: tag source declares Room {declared}, expected {expected}")


def check_inventory(releases: dict) -> None:
    covered: dict[str, str] = {}
    for fixture in releases["fixtures"]:
        for member in fixture["releases"]:
            release = release_by_id(releases, member)
            if release["db_version"] != fixture["db_version"]:
                raise ExtractionError(f"{member} is Room {release['db_version']}, fixture is {fixture['db_version']}")
            if member in covered:
                raise ExtractionError(f"{member} is in fixtures {covered[member]} and {fixture['directory']}")
            covered[member] = str(fixture["directory"])
    missing = [release["id"] for release in releases["releases"] if release["id"] not in covered]
    if missing:
        raise ExtractionError(f"releases without a fixture: {missing}")


def command_extract(args: argparse.Namespace) -> int:
    dexdump = dexdump_path(args.build_tools)
    schema = extract_from_apk(Path(args.apk), dexdump)
    print(json.dumps(schema.to_json(), indent=2, ensure_ascii=False))
    return 0


def command_generate(args: argparse.Namespace, check_only: bool) -> int:
    releases = load_releases(Path(args.releases))
    check_inventory(releases)
    if args.git:
        verify_tags(releases, Path(args.git))
    dexdump = dexdump_path(args.build_tools)
    apk_root = Path(args.apk_cache)
    assets_root = Path(args.assets)
    log = (lambda message: print(message, file=sys.stderr))

    fixtures = [fixture for fixture in releases["fixtures"] if fixture.get("directory")]
    if args.all_apks:
        wanted = [r["id"] for r in releases["releases"] if r["binary"].get("kind") == "github-apk"]
    else:
        wanted = [release_id for fixture in fixtures for release_id in fixture_apk_releases(releases, fixture)]
    extracted = extract_releases(releases, wanted, apk_root, dexdump, args.jobs, log)

    outputs: dict[str, dict[str, str]] = {}
    for fixture in fixtures:
        if fixture["fixture_format"] == 1:
            verify_format1(fixture, extracted, assets_root)
        else:
            outputs[fixture["directory"]] = render_fixture(releases, fixture, extracted, Path(args.schemas))

    differences: list[str] = []
    for directory, files in sorted(outputs.items()):
        target = assets_root / directory
        for name, text in sorted(files.items()):
            path = target / name
            current = path.read_text(encoding="utf-8") if path.is_file() else None
            if current == text:
                continue
            if check_only:
                differences.append(str(path))
            else:
                target.mkdir(parents=True, exist_ok=True)
                path.write_text(text, encoding="utf-8")
                log(f"[write] {path}")
    if differences:
        for difference in differences:
            print(f"[check] stale or missing: {difference}", file=sys.stderr)
        return 1
    log(
        f"[{'check' if check_only else 'generate'}] {len(outputs)} format-2 fixtures and "
        f"{len(fixtures) - len(outputs)} frozen format-1 fixtures match {len(extracted)} APKs"
    )
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--build-tools", default=None, help=f"build-tools version (default {DEFAULT_BUILD_TOOLS})")
    sub = parser.add_subparsers(dest="command", required=True)

    extract = sub.add_parser("extract", help="print the Room schema of one APK")
    extract.add_argument("apk")

    for name in ("generate", "check"):
        command = sub.add_parser(name)
        command.add_argument("--releases", default=str(DEFAULT_RELEASES))
        command.add_argument("--apk-cache", required=True, help="directory written by fetch_release_apks.sh")
        command.add_argument("--assets", default=str(DEFAULT_ASSETS))
        command.add_argument("--schemas", default=str(DEFAULT_SCHEMAS))
        command.add_argument("--git", default=None, help="fearless-Android checkout holding the release tags")
        command.add_argument("--all-apks", action="store_true", help="extract every APK, not only fixture sources")
        command.add_argument("--jobs", type=int, default=os.cpu_count() or 1)

    args = parser.parse_args(argv)
    try:
        if args.command == "extract":
            return command_extract(args)
        return command_generate(args, check_only=args.command == "check")
    except ExtractionError as error:
        print(f"[extract_room_schema][error] {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
