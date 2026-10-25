#!/usr/bin/env python3
"""Freeze the SQL that shipped 4.x builds ran from database 71 to 75.

Every 4.x release (4.0.1, 4.0.2, 4.1.1, 4.2.0) upgraded 3.x databases with the
same TonMigration (71 -> 72) and 72 -> 73 SQL; 4.1.1 and the v4.2.0 tag stop
at database 74 with their own 73 -> 74, and the Play 4.2.0 (229) source
b5e22d49d has a different 73 -> 74 and adds 74 -> 75.
The release candidate rewrote TonMigration, so upgrade tests that must start
from what shipped builds left on devices replay this frozen copy instead.

The statements are read with `git show` from each release ref, Kotlin raw
strings are reproduced with the same trimIndent/trimMargin rules the compiler
applied, and each distinct statement list is kept with the refs that ran it.
Any string template or unrecognised call fails the extraction.

    scripts/extract-shipped-ton-migration-sql.py           # rewrite the asset
    scripts/extract-shipped-ton-migration-sql.py --check   # verify it
"""

import argparse
import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path

REFS = ["4.0.1", "4.0.2", "4.1.1", "v4.2.0", "b5e22d49d"]
MIGRATIONS_DIR = "core-db/src/main/java/jp/co/soramitsu/coredb/migrations"
TON_MIGRATION = f"{MIGRATIONS_DIR}/TonMigration.kt"
MIGRATIONS = f"{MIGRATIONS_DIR}/Migrations.kt"
SCHEMAS = "core-db/schemas/jp.co.soramitsu.coredb.AppDatabase"
APP_DATABASE = "core-db/src/main/java/jp/co/soramitsu/coredb/AppDatabase.kt"
ASSET_DIR = Path("core-db/src/androidTest/assets/migrations/4.x-shipped")
ASSET = ASSET_DIR / "shipped-71-to-75.sql"
MANIFEST = ASSET_DIR / "migration-manifest.json"

TON_FUNCTIONS = ["migrateMetaAccounts", "recreateChainsAndAssets", "recreateTokenPrice"]
ADJACENT_MIGRATIONS = [("Migration_72_73", 72, 73), ("Migration_73_74", 73, 74), ("Migration_74_75", 74, 75)]


def git_show(root, ref, path):
    return subprocess.run(
        ["git", "-C", str(root), "show", f"{ref}:{path}"],
        check=True, capture_output=True, text=True
    ).stdout


def git_blob(root, ref, path):
    return subprocess.run(
        ["git", "-C", str(root), "rev-parse", f"{ref}:{path}"],
        check=True, capture_output=True, text=True
    ).stdout.strip()


def trim_indent(text):
    lines = text.split("\n")
    indents = [len(line) - len(line.lstrip()) for line in lines if line.strip()]
    common = min(indents) if indents else 0
    result = []
    for index, line in enumerate(lines):
        if (index == 0 or index == len(lines) - 1) and not line.strip():
            continue
        result.append(line[common:])
    return "\n".join(result)


def trim_margin(text, prefix="|"):
    lines = text.split("\n")
    result = []
    for index, line in enumerate(lines):
        if (index == 0 or index == len(lines) - 1) and not line.strip():
            continue
        stripped = line.lstrip()
        if stripped.startswith(prefix):
            result.append(stripped[len(prefix):])
        else:
            result.append(line)
    return "\n".join(result)


EXEC_SQL = re.compile(r'execSQL\(\s*(?:"""(.*?)"""\s*\.(trimIndent|trimMargin)\(\)|"((?:[^"\\]|\\.)*)")\s*\)', re.S)


def statements_in(body, context):
    statements = []
    for match in EXEC_SQL.finditer(body):
        raw, trim, plain = match.groups()
        if raw is not None:
            if "$" in raw:
                raise SystemExit(f"{context}: string template in raw SQL")
            statements.append(trim_indent(raw) if trim == "trimIndent" else trim_margin(raw))
        else:
            if "$" in plain or "\\" in plain:
                raise SystemExit(f"{context}: template or escape in SQL literal")
            statements.append(plain)
    if body.count("execSQL(") != len(statements):
        raise SystemExit(f"{context}: an execSQL call was not recognised")
    return statements


def block_after(source, header, context):
    start = source.find(header)
    if start < 0:
        raise SystemExit(f"{context}: {header!r} not found")
    open_brace = source.index("{", start)
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace:index + 1]
    raise SystemExit(f"{context}: unterminated {header!r}")


def extract(root, ref):
    ton = git_show(root, ref, TON_MIGRATION)
    migrate = block_after(ton, "override fun migrate(db: SupportSQLiteDatabase)", f"{ref} TonMigration")
    called = re.findall(r"^\s*(\w+)\(db\)\s*$", migrate, re.M)
    if called != TON_FUNCTIONS:
        raise SystemExit(f"{ref}: TonMigration.migrate calls {called}, expected {TON_FUNCTIONS}")
    groups = [(71, 72, [
        statement
        for name in TON_FUNCTIONS
        for statement in statements_in(
            block_after(ton, f"private fun {name}(", f"{ref} {name}"),
            f"{ref} {name}"
        )
    ])]
    migrations = git_show(root, ref, MIGRATIONS)
    for name, start, end in ADJACENT_MIGRATIONS:
        if end > database_version(root, ref):
            break
        header = f"val {name} = object : Migration({start}, {end})"
        groups.append((start, end, statements_in(block_after(migrations, header, f"{ref} {name}"), f"{ref} {name}")))
    return groups


def database_version(root, ref):
    match = re.search(r"^\s*version = (\d+),", git_show(root, ref, APP_DATABASE), re.M)
    if match is None:
        raise SystemExit(f"{ref}: AppDatabase version not found")
    return int(match.group(1))


def identity_hashes(root):
    """Room identity each shipped build wrote after upgrading to 73, 74 and 75."""
    hashes = {}
    for version in (73, 74, 75):
        found = set()
        for ref in REFS:
            try:
                schema = json.loads(git_show(root, ref, f"{SCHEMAS}/{version}.json"))
            except subprocess.CalledProcessError:
                continue
            found.add(schema["database"]["identityHash"])
        if len(found) != 1:
            raise SystemExit(f"Room identity of database {version} differs between refs: {found}")
        hashes[str(version)] = found.pop()
    return hashes


def variants(extracted):
    """One entry per distinct statement list of each edge, with the refs that ran it."""
    result = []
    for start, end in [(71, 72)] + [(a, b) for _, a, b in ADJACENT_MIGRATIONS]:
        by_statements = {}
        for ref in REFS:
            for group_start, group_end, statements in extracted[ref]:
                if (group_start, group_end) == (start, end):
                    by_statements.setdefault(tuple(statements), []).append(ref)
        for statements, refs in by_statements.items():
            result.append((start, end, refs, list(statements)))
    return result


def render(root, edges):
    blobs = {ref: {"TonMigration.kt": git_blob(root, ref, TON_MIGRATION), "Migrations.kt": git_blob(root, ref, MIGRATIONS)} for ref in REFS}
    lines = [
        "-- SQL that shipped Fearless Wallet 4.x builds ran from database 71 to 75.",
        "-- Generated by scripts/extract-shipped-ton-migration-sql.py. Every",
        "-- migration block names the releases that ran exactly its statements.",
        "-- Room ran every statement inside its upgrade transaction with foreign-key",
        "-- enforcement off, so the PRAGMA statements below change nothing.",
    ]
    for start, end, refs, statements in edges:
        source = "TonMigration.kt" if start == 71 else "Migrations.kt"
        lines.append(f"-- @migration {start} {end} {','.join(refs)} {source}")
        for statement in statements:
            lines.append("-- @statement")
            lines.append(statement)
    return "\n".join(lines) + "\n", blobs


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    root = Path(subprocess.run(["git", "rev-parse", "--show-toplevel"], check=True, capture_output=True, text=True).stdout.strip())

    extracted = {ref: extract(root, ref) for ref in REFS}
    edges = variants(extracted)
    if [(start, end) for start, end, _, _ in edges if start == 71 or start == 72] != [(71, 72), (72, 73)]:
        raise SystemExit("The shipped 71 -> 72 or 72 -> 73 SQL differs between releases")

    text, blobs = render(root, edges)
    digest = hashlib.sha256(text.encode("utf-8")).hexdigest()
    manifest = {
        "fixture_format": 1,
        "purpose": "Verbatim SQL of the shipped 4.x 71 -> 72 TonMigration and 72 -> 73, 73 -> 74, 74 -> 75 migrations, replayed by upgrade tests instead of the release candidate's rewritten migrations",
        "refs": REFS,
        "database_version_by_ref": {ref: database_version(root, ref) for ref in REFS},
        "source_blobs": blobs,
        "migrations": [
            {"from": start, "to": end, "refs": refs, "statement_count": len(statements)}
            for start, end, refs, statements in edges
        ],
        "shipped_room_identity_hashes": identity_hashes(root),
        "frozen_assets": {"sql": ASSET.name, "sql_sha256": digest},
    }
    manifest_text = json.dumps(manifest, indent=2) + "\n"

    asset = root / ASSET
    manifest_path = root / MANIFEST
    if args.check:
        if asset.read_text(encoding="utf-8") != text or manifest_path.read_text(encoding="utf-8") != manifest_text:
            raise SystemExit("Frozen shipped SQL is stale; rerun without --check")
        print(f"ok {digest}")
        return
    asset.parent.mkdir(parents=True, exist_ok=True)
    asset.write_text(text, encoding="utf-8")
    manifest_path.write_text(manifest_text, encoding="utf-8")
    print(f"wrote {ASSET} sha256 {digest}")


if __name__ == "__main__":
    sys.exit(main())
