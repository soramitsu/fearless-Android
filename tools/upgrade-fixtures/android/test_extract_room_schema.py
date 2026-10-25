#!/usr/bin/env python3
"""Tests for extract_room_schema.py that need neither APKs nor dexdump.

Run: python3 tools/upgrade-fixtures/android/test_extract_room_schema.py

The full extraction (APK -> fixtures) is checked separately by
`extract_room_schema.py check --apk-cache <dir>`, which needs the APK cache.
"""
from __future__ import annotations

import hashlib
import importlib.util
import json
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("extract_room_schema", HERE / "extract_room_schema.py")
ers = importlib.util.module_from_spec(SPEC)
sys.modules["extract_room_schema"] = ers
SPEC.loader.exec_module(ers)

# Room 2.6, kept but R8-renamed androidx.sqlite (2.1.0-3.1.2 shape).
MINIFIED_ROOM_26 = """\
2d85d0:                                        |[2d85d0] jp.co.soramitsu.coredb.AppDatabase_Impl$1.createAllTables:(Lr3/i;)V
2d85e0: 1a00 dd2a                              |0000: const-string v0, "CREATE TABLE IF NOT EXISTS `users` (`address` TEXT NOT NULL, PRIMARY KEY(`address`))" // string@2add
2d85e4: 7220 7fd7 0200                         |0002: invoke-interface {v2, v0}, Lr3/i;.u:(Ljava/lang/String;)V // method@d77f
2d85ea: 1a00 b12a                              |0005: const-string v0, "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)" // string@2ab1
2d85ee: 7220 7fd7 0200                         |0007: invoke-interface {v2, v0}, Lr3/i;.u:(Ljava/lang/String;)V // method@d77f
2d85f4: 1a00 b92a                              |000a: const-string v0, "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3bb5c8d34b0a8d8b9010b57406b31b39')" // string@2ab9
2d85f8: 7220 7fd7 0200                         |000c: invoke-interface {v2, v0}, Lr3/i;.u:(Ljava/lang/String;)V // method@d77f
2d85fe: 0e00                                   |000f: return-void
      catches       : (none)
      positions     :
2d8f10:                                        |[2d8f10] jp.co.soramitsu.coredb.AppDatabase_Impl.createOpenHelper:(Landroidx/room/q;)Lr3/j;
2d8f20: 2200 c001                              |0000: new-instance v0, Landroidx/room/w0; // type@01c0
2d8f24: 2201 0411                              |0002: new-instance v1, Ljp/co/soramitsu/coredb/AppDatabase_Impl$1; // type@1104
2d8f28: 1302 2d00                              |0004: const/16 v2, #int 45 // #2d
2d8f2c: 7030 594d 4102                         |0006: invoke-direct {v1, v4, v2}, Ljp/co/soramitsu/coredb/AppDatabase_Impl$1;.<init>:(Ljp/co/soramitsu/coredb/AppDatabase_Impl;I)V // method@4d59
2d8f32: 1a02 b024                              |0009: const-string v2, "3bb5c8d34b0a8d8b9010b57406b31b39" // string@24b0
2d8f36: 1a03 bf24                              |000b: const-string v3, "9b2864ad5ba5ef320f233a067e8a7682" // string@24bf
2d8f3a: 7053 f404 5021                         |000d: invoke-direct {v0, v5, v1, v2, v3}, Landroidx/room/w0;.<init>:(Landroidx/room/q;Landroidx/room/w0$a;Ljava/lang/String;Ljava/lang/String;)V // method@04f4
2d8f40: 1105                                   |0010: return-object v5
      catches       : (none)
"""

# Room 2.7 open delegate (4.2.0 shape), including Kotlin's parameter null check.
ROOM_27 = """\
3e3170:                                        |[3e3170] jp.co.soramitsu.coredb.AppDatabase_Impl$createOpenDelegate$_openDelegate$1.<init>:(Ljp/co/soramitsu/coredb/AppDatabase_Impl;)V
3e3184: 1a03 a719                              |0002: const-string v3, "3909ad504e3e6dfada04d2bfca064870" // string@19a7
3e3188: 1a00 b419                              |0004: const-string v0, "5e04b3232d1bc1b4a93ea19faaef01e8" // string@19b4
3e318c: 1301 4b00                              |0006: const/16 v1, #int 75 // #4b
3e3190: 7040 8d50 1203                         |0008: invoke-direct {v2, v1, v3, v0}, Landroidx/room/L;.<init>:(ILjava/lang/String;Ljava/lang/String;)V // method@508d
3e3196: 0e00                                   |000b: return-void
      catches       : (none)
      positions     :
3e3198:                                        |[3e3198] jp.co.soramitsu.coredb.AppDatabase_Impl$createOpenDelegate$_openDelegate$1.createAllTables:(Lm2/b;)V
3e31a8: 1a00 fb88                              |0000: const-string v0, "connection" // string@88fb
3e31ac: 7120 0cd5 0200                         |0002: invoke-static {v2, v0}, Lkotlin/jvm/internal/v;.g:(Ljava/lang/Object;Ljava/lang/String;)V // method@d50c
3e31b2: 1a00 ae1e                              |0005: const-string v0, "CREATE TABLE IF NOT EXISTS `address_book` (`address` TEXT NOT NULL, `name` TEXT DEFAULT 'a\\"b', PRIMARY KEY(`address`))" // string@1eae
3e31b6: 7120 66d7 0200                         |0007: invoke-static {v2, v0}, Lm2/a;.a:(Lm2/b;Ljava/lang/String;)V // method@d766
3e31bc: 1a00 f71e                              |000a: const-string v0, "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '3909ad504e3e6dfada04d2bfca064870')" // string@1ef7
3e31c0: 7120 66d7 0200                         |000c: invoke-static {v2, v0}, Lm2/a;.a:(Lm2/b;Ljava/lang/String;)V // method@d766
3e31c6: 0e00                                   |000f: return-void
      catches       : (none)
3e4000:                                        |[3e4000] jp.co.soramitsu.other.Unrelated.createAllTables:(Lm2/b;)V
3e4010: 1a00 0000                              |0000: const-string v0, "CREATE TABLE should_not_be_read (x)" // string@0000
"""


def methods(text: str) -> dict[str, ers.Method]:
    return {f"{m.owner}.{m.name}": m for m in ers.parse_room_methods(text.splitlines(keepends=True))}


class DexdumpParsingTest(unittest.TestCase):

    def test_unescape_dexdump_strings(self):
        self.assertEqual('a"b\\c\n\té', ers.unescape_dexdump('a\\"b\\\\c\\n\\t\\u00e9'))
        with self.assertRaises(ers.ExtractionError):
            ers.unescape_dexdump("bad\\q")

    def test_parameter_types(self):
        self.assertEqual(["r3/i", "I", "[B"], ers.parameter_types("(Lr3/i;I[B)V"))

    def test_minified_room_26_statements_and_identity(self):
        parsed = methods(MINIFIED_ROOM_26)
        create = parsed["jp.co.soramitsu.coredb.AppDatabase_Impl$1.createAllTables"]
        statements = ers.executed_sql(create)
        self.assertEqual(3, len(statements))
        self.assertTrue(statements[0].startswith("CREATE TABLE IF NOT EXISTS `users`"))
        self.assertTrue(ers.ROOM_MASTER_INSERT.match(statements[-1]))
        version, identity, legacy = ers.open_helper_identity(
            parsed["jp.co.soramitsu.coredb.AppDatabase_Impl.createOpenHelper"],
            "jp.co.soramitsu.coredb.AppDatabase_Impl$1",
        )
        self.assertEqual((45, "3bb5c8d34b0a8d8b9010b57406b31b39", "9b2864ad5ba5ef320f233a067e8a7682"),
                         (version, identity, legacy))

    def test_room_27_skips_null_check_and_reads_delegate_identity(self):
        parsed = methods(ROOM_27)
        owner = "jp.co.soramitsu.coredb.AppDatabase_Impl$createOpenDelegate$_openDelegate$1"
        statements = ers.executed_sql(parsed[f"{owner}.createAllTables"])
        self.assertEqual(
            [
                "CREATE TABLE IF NOT EXISTS `address_book` (`address` TEXT NOT NULL, "
                "`name` TEXT DEFAULT 'a\"b', PRIMARY KEY(`address`))",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) "
                "VALUES(42, '3909ad504e3e6dfada04d2bfca064870')",
            ],
            statements,
        )
        self.assertEqual(
            (75, "3909ad504e3e6dfada04d2bfca064870", "5e04b3232d1bc1b4a93ea19faaef01e8"),
            ers.open_delegate_identity(parsed[f"{owner}.<init>"]),
        )
        self.assertNotIn("jp.co.soramitsu.other.Unrelated.createAllTables", parsed)

    def test_non_constant_sql_is_rejected(self):
        method = ers.Method(
            "jp.co.soramitsu.coredb.AppDatabase_Impl$1",
            "createAllTables",
            "(Lr3/i;)V",
            ["invoke-interface {v2, v0}, Lr3/i;.u:(Ljava/lang/String;)V // method@d77f"],
        )
        with self.assertRaises(ers.ExtractionError):
            ers.executed_sql(method)


class FixtureRenderingTest(unittest.TestCase):

    def test_exported_schema_75_rebuilds_create_all_tables(self):
        version, identity, statements = ers.exported_schema_statements(
            ers.DEFAULT_SCHEMAS / "jp.co.soramitsu.coredb.AppDatabase/75.json"
        )
        self.assertEqual(75, version)
        self.assertEqual("3909ad504e3e6dfada04d2bfca064870", identity)
        self.assertTrue(statements[0].startswith("CREATE TABLE IF NOT EXISTS `address_book`"))
        self.assertEqual(
            f"INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '{identity}')",
            statements[-1],
        )
        self.assertFalse(any("${" in statement for statement in statements))

    def test_rendered_sql_creates_the_declared_version(self):
        release = {"version": "9.9.9", "versionCode": 1}
        statements = (
            "CREATE TABLE IF NOT EXISTS `t` (`a` TEXT NOT NULL, PRIMARY KEY(`a`))",
            "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
            "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '0123456789abcdef0123456789abcdef')",
        )
        sql = ers.render_sql(release, 12, "x.AppDatabase_Impl$1", statements, "test")
        self.assertEqual(sql, ers.render_sql(release, 12, "x.AppDatabase_Impl$1", statements, "test"))
        description = ers.validate_with_sqlite(sql, 12, "0123456789abcdef0123456789abcdef")
        self.assertEqual(["room_master_table", "t"], description["tables"])
        with self.assertRaises(ers.ExtractionError):
            ers.validate_with_sqlite(sql, 13, None)


class CommittedInventoryTest(unittest.TestCase):
    """Checks the committed inventory and fixtures without any APK."""

    @classmethod
    def setUpClass(cls):
        cls.releases = ers.load_releases(ers.DEFAULT_RELEASES)

    def test_every_release_is_covered_by_exactly_one_fixture(self):
        ers.check_inventory(self.releases)

    def test_every_github_apk_is_pinned(self):
        for release in self.releases["releases"]:
            binaries = [release["binary"], *release.get("other_binaries", [])]
            for binary in binaries:
                if binary["kind"] != "github-apk":
                    continue
                self.assertRegex(binary["sha256"], r"^[0-9a-f]{64}$", release["id"])
                self.assertRegex(binary["signer_sha256"], r"^[0-9a-f]{64}$", release["id"])
                self.assertTrue(binary["url"].startswith(
                    "https://github.com/soramitsu/fearless-Android/releases/download/"
                ), release["id"])
                self.assertIn(binary["signer_sha256"], self.releases["signers"], release["id"])
            if release["binary"]["kind"] == "github-apk":
                self.assertRegex(release["room_identity_hash"], r"^[0-9a-f]{32}$", release["id"])
            self.assertTrue(release["schema_evidence"], release["id"])

    def test_committed_fixtures_match_their_manifests(self):
        for fixture in self.releases["fixtures"]:
            directory = fixture["directory"]
            if directory is None:
                self.assertEqual("existing-source-fixture", fixture["source"]["kind"])
                self.assertTrue((ers.REPO_ROOT / fixture["source"]["covered_by"]).is_file())
                continue
            folder = ers.DEFAULT_ASSETS / directory
            manifest = json.loads((folder / ers.MANIFEST_NAME).read_text(encoding="utf-8"))
            sql_path = folder / ers.SQL_NAME.format(version=fixture["db_version"])
            sql = sql_path.read_bytes()
            self.assertEqual(manifest["frozen_assets"]["schema_sql_sha256"], hashlib.sha256(sql).hexdigest(), directory)
            self.assertIn(f"PRAGMA user_version = {fixture['db_version']};", sql.decode("utf-8"), directory)
            if fixture["fixture_format"] == 1:
                self.assertEqual(fixture["db_version"], manifest["schema_extraction"]["room_version"], directory)
                continue
            self.assertEqual(ers.FIXTURE_FORMAT, manifest["fixture_format"], directory)
            self.assertEqual(fixture["db_version"], manifest["db_version"], directory)
            self.assertEqual(fixture["source_release"], manifest["source_release"]["id"], directory)
            self.assertEqual(fixture["room_identity_hash"], manifest["schema_extraction"]["room_identity_hash"], directory)
            self.assertEqual(
                fixture["releases"],
                [member["id"] for member in manifest["releases_with_this_schema"]],
                directory,
            )
            description = ers.validate_with_sqlite(sql.decode("utf-8"), fixture["db_version"], fixture["room_identity_hash"])
            self.assertEqual(manifest["schema_check"]["tables"], description["tables"], directory)


if __name__ == "__main__":
    unittest.main()
