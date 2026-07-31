package jp.co.soramitsu.coredb.migrations.fixtures

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Immutable version-71 Room database synthesized from DDL and a Room identity
 * extracted from the officially released Fearless Wallet 3.7.4 (209) APK.
 * This is not a copy of any released user's database.
 *
 * The generated fixture is intentionally copied as a frozen binary database.
 * It must never be rebuilt from current Room entities because doing so would
 * allow a schema regression to redefine the historical migration input.
 */
object ReleasedV374DatabaseFixture {

    const val DATABASE_ASSET = "migrations/3.7.4/app-database-v71.db"
    const val SCHEMA_ASSET = "migrations/3.7.4/app-database-v71.sql"
    const val MANIFEST_ASSET = "migrations/3.7.4/fixture-manifest.json"
    const val LEGACY_SECRET_ASSET =
        "migrations/3.7.4/meta-account-secrets-v69-default.hex"
    const val DATABASE_SHA256 =
        "ca0c5efa69b56e46bba3cd218194e443133c83dd03ff044b972af65657d075a9"
    const val SCHEMA_SHA256 =
        "edc3771e5a61981e18674a206d2b38f1be38595de0f77af455a2f899630beef4"
    const val LEGACY_SECRET_ASSET_SHA256 =
        "fb30cf2e9c4918c22f0a269afc715802555c5b3ef97c794c82c375725773e51c"
    const val LEGACY_SECRET_SCALE_SHA256 =
        "291eb865d03d31f20ee6fbb5cfac9722784ad5ea20dc7a6add8b3ded402c7d19"
    const val OFFICIAL_APK_SHA256 =
        "4a1cb6377c5779b697610aa5806965615e79ab1bae92e207f9ee6eeb1bb56ba2"
    const val APK_DEX_SHA256 =
        "e8c76e3b597ebb68c5afce5fc4fee80da536dfabe89db108876b5f4e8a4c9497"
    const val RELEASE_TAG = "3.7.4"
    const val RELEASE_TAG_COMMIT = "2da66a2cdc5a892270a294a12bcfacbc4b585feb"
    const val APP_DATABASE_SOURCE_SHA256 =
        "973ca000ea40241afc435605b5ff4f36691a9489d1d2ce4ff579ee03abd1881d"
    const val LEGACY_SECRET_SCHEMA_SOURCE_SHA256 =
        "321bbd1b88f72b1921d25d2a80673b918ac16ad9ea6f1976dbca93f44e8412d5"
    const val APK_ROOM_CLASS =
        "jp.co.soramitsu.coredb.AppDatabase_Impl\$1"
    const val APK_ROOM_METHOD = "createAllTables"
    const val LEGACY_SECRET_SCHEMA_SOURCE =
        "common/src/main/java/jp/co/soramitsu/common/data/secrets/v2/MetaAccountSecrets.kt"
    const val LEGACY_SECRET_SCHEMA =
        "jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets"
    const val LEGACY_SECRET_VECTOR_SOURCE =
        "core-db/src/androidTest/java/jp/co/soramitsu/coredb/migrations/TonMigrationSafetyTest.kt"
    const val LEGACY_SECRET_VECTOR_FUNCTION =
        "encodedVersion69Secrets(default arguments)"
    const val ROOM_IDENTITY_HASH = "64c235aeb5a511d5cb4df675040540b4"
    const val DATABASE_VERSION = 71
    const val EXPECTED_ROOM_TABLE_COUNT = 23
    const val EXPECTED_EXPLICIT_INDEX_COUNT = 13
    const val LEGACY_SECRET_SCALE_BYTE_COUNT = 257

    fun install(context: Context, databaseName: String) {
        require(databaseName.isNotBlank()) { "A test database name is required" }
        verifyProvenance(context)
        val target = context.getDatabasePath(databaseName)
        context.deleteDatabase(databaseName)
        check(!target.exists()) {
            "Could not remove stale test database $databaseName"
        }
        val databaseDirectory = checkNotNull(target.parentFile) {
            "Fixture database has no parent directory"
        }
        check(
            databaseDirectory.isDirectory ||
                databaseDirectory.mkdirs()
        ) {
            "Could not create fixture database directory"
        }
        val temporary = File.createTempFile(
            "${target.name}.fixture-",
            ".tmp",
            databaseDirectory
        )

        try {
            context.assets.open(DATABASE_ASSET).use { source ->
                temporary.outputStream().buffered().use { destination ->
                    source.copyTo(destination)
                }
            }
            check(temporary.sha256() == DATABASE_SHA256) {
                "Frozen 3.7.4 database fixture hash mismatch"
            }
            check(temporary.renameTo(target)) {
                "Could not atomically install frozen 3.7.4 database fixture"
            }
            check(target.sha256() == DATABASE_SHA256) {
                "Installed 3.7.4 database fixture hash mismatch"
            }
            verifyInstalled(target)
        } finally {
            if (temporary.exists()) {
                check(temporary.delete()) {
                    "Could not remove temporary 3.7.4 database fixture"
                }
            }
        }
    }

    fun legacySecretHex(context: Context): String {
        verifyProvenance(context)
        return context.readCanonicalLegacySecretHex()
    }

    fun verifyProvenance(context: Context) {
        check(context.assetSha256(SCHEMA_ASSET) == SCHEMA_SHA256) {
            "Frozen 3.7.4 schema SQL hash mismatch"
        }
        check(context.assetSha256(DATABASE_ASSET) == DATABASE_SHA256) {
            "Frozen 3.7.4 database asset hash mismatch"
        }
        check(context.assetSha256(LEGACY_SECRET_ASSET) == LEGACY_SECRET_ASSET_SHA256) {
            "Frozen 3.7.4 legacy secret asset hash mismatch"
        }

        val manifest = context.assets.open(MANIFEST_ASSET).use { input ->
            JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
        }
        val release = manifest.getJSONObject("release")
        val extraction = manifest.getJSONObject("schema_extraction")
        val construction = manifest.getJSONObject("database_construction")
        val sources = manifest.getJSONObject("tag_sources")
        val secretExtraction = manifest.getJSONObject("secret_fixture_extraction")
        val assets = manifest.getJSONObject("frozen_assets")

        check(release.getString("tag") == RELEASE_TAG)
        check(release.getString("tag_commit") == RELEASE_TAG_COMMIT)
        check(release.getString("apk_sha256") == OFFICIAL_APK_SHA256)
        check(extraction.getString("apk_entry_sha256") == APK_DEX_SHA256)
        check(extraction.getInt("room_version") == DATABASE_VERSION)
        check(extraction.getString("room_identity_hash") == ROOM_IDENTITY_HASH)
        check(extraction.getString("class") == APK_ROOM_CLASS)
        check(extraction.getString("method") == APK_ROOM_METHOD)
        check(construction.getString("source") == SCHEMA_ASSET.substringAfterLast('/'))
        check(!construction.getBoolean("source_is_official_user_database"))
        check(!construction.getBoolean("contains_released_user_rows"))
        check(construction.getInt("database_header_sqlite_version_number") == 3_051_000)
        check(construction.getBoolean("reproducible_with_pinned_tool"))
        check(
            sources.getString("core_db_app_database_sha256") ==
                APP_DATABASE_SOURCE_SHA256
        )
        check(
            sources.getString("legacy_meta_account_secrets_sha256") ==
                LEGACY_SECRET_SCHEMA_SOURCE_SHA256
        )
        check(assets.getString("schema_sql_sha256") == SCHEMA_SHA256)
        check(assets.getString("database_sha256") == DATABASE_SHA256)
        check(
            secretExtraction.getString("source_path_at_tag") ==
                LEGACY_SECRET_SCHEMA_SOURCE
        )
        check(secretExtraction.getString("schema") == LEGACY_SECRET_SCHEMA)
        check(secretExtraction.getString("release_tag") == RELEASE_TAG)
        check(
            secretExtraction.getString("tag_commit") ==
                RELEASE_TAG_COMMIT
        )
        check(
            secretExtraction.getString("source_sha256") ==
                LEGACY_SECRET_SCHEMA_SOURCE_SHA256
        )
        check(secretExtraction.getInt("schema_field_count") == 6)
        check(secretExtraction.getString("encoding") == "SCALE")
        check(
            secretExtraction.getString("vector_source") ==
                LEGACY_SECRET_VECTOR_SOURCE
        )
        check(
            secretExtraction.getString("vector_function") ==
                LEGACY_SECRET_VECTOR_FUNCTION
        )
        check(secretExtraction.getBoolean("synthetic_only"))
        check(!secretExtraction.getBoolean("contains_user_material"))
        check(!secretExtraction.getBoolean("current_ton_field_schema_used"))
        check(
            secretExtraction.getInt("decoded_byte_count") ==
                LEGACY_SECRET_SCALE_BYTE_COUNT
        )
        check(
            assets.getString("legacy_secret_v69") ==
                LEGACY_SECRET_ASSET.substringAfterLast('/')
        )
        check(
            assets.getString("legacy_secret_v69_sha256") ==
                LEGACY_SECRET_ASSET_SHA256
        )
        check(
            assets.getString("legacy_secret_v69_scale_sha256") ==
                LEGACY_SECRET_SCALE_SHA256
        )

        val legacySecretHex = context.readCanonicalLegacySecretHex()
        val legacySecretBytes = legacySecretHex.decodeCanonicalHex()
        check(legacySecretBytes.size == LEGACY_SECRET_SCALE_BYTE_COUNT) {
            "Frozen 3.7.4 legacy secret byte count mismatch"
        }
        check(legacySecretBytes.sha256() == LEGACY_SECRET_SCALE_SHA256) {
            "Frozen 3.7.4 legacy secret SCALE hash mismatch"
        }
    }

    private fun verifyInstalled(databaseFile: File) {
        SQLiteDatabase.openDatabase(
            databaseFile.path,
            null,
            SQLiteDatabase.OPEN_READONLY
        ).use { database ->
            check(database.version == DATABASE_VERSION) {
                "Expected Room version $DATABASE_VERSION, found ${database.version}"
            }
            check(
                database.singleString(
                    "SELECT identity_hash FROM room_master_table WHERE id = 42"
                ) == ROOM_IDENTITY_HASH
            ) {
                "Frozen 3.7.4 Room identity hash mismatch"
            }
            check(database.singleString("PRAGMA integrity_check") == "ok") {
                "Frozen 3.7.4 database failed SQLite integrity_check"
            }
            check(database.singleInt("SELECT COUNT(*) FROM pragma_foreign_key_check") == 0) {
                "Frozen 3.7.4 database failed foreign_key_check"
            }
            check(
                database.singleInt(
                    """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'table'
                      AND name NOT IN ('android_metadata', 'sqlite_sequence')
                    """.trimIndent()
                ) == EXPECTED_ROOM_TABLE_COUNT
            ) {
                "Frozen 3.7.4 database table count mismatch"
            }
            check(
                database.singleInt(
                    """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'index' AND sql IS NOT NULL
                    """.trimIndent()
                ) == EXPECTED_EXPLICIT_INDEX_COUNT
            ) {
                "Frozen 3.7.4 database index count mismatch"
            }
        }
    }

    private fun File.sha256(): String {
        return inputStream().buffered().use { input -> input.sha256() }
    }

    private fun Context.assetSha256(assetName: String): String {
        return assets.open(assetName).buffered().use { input -> input.sha256() }
    }

    private fun Context.readCanonicalLegacySecretHex(): String {
        val encoded = assets.open(LEGACY_SECRET_ASSET).use { input -> input.readBytes() }
            .toString(Charsets.UTF_8)
        check(encoded.endsWith('\n')) {
            "Frozen 3.7.4 legacy secret asset must end with LF"
        }
        check('\r' !in encoded && encoded.count { it == '\n' } == 1) {
            "Frozen 3.7.4 legacy secret asset must contain one canonical line"
        }
        return encoded.dropLast(1).also { canonicalHex ->
            canonicalHex.decodeCanonicalHex()
        }
    }

    private fun String.decodeCanonicalHex(): ByteArray {
        check(startsWith("0x")) {
            "Frozen 3.7.4 legacy secret asset must use a 0x prefix"
        }
        val body = substring(2)
        check(body.isNotEmpty() && body.length % 2 == 0) {
            "Frozen 3.7.4 legacy secret asset has invalid hex length"
        }
        check(body.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Frozen 3.7.4 legacy secret asset must use lowercase canonical hex"
        }
        return ByteArray(body.length / 2) { byteIndex ->
            body.substring(byteIndex * 2, byteIndex * 2 + 2)
                .toInt(radix = 16)
                .toByte()
        }
    }

    private fun ByteArray.sha256(): String {
        return inputStream().buffered().use { input -> input.sha256() }
    }

    private fun InputStream.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
    }

    private fun SQLiteDatabase.singleString(sql: String): String =
        rawQuery(sql, emptyArray()).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getString(0)
        }

    private fun SQLiteDatabase.singleInt(sql: String): Int =
        rawQuery(sql, emptyArray()).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no rows: $sql" }
            cursor.getInt(0)
        }
}
