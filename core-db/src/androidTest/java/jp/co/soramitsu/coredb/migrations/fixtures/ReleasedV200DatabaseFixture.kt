package jp.co.soramitsu.coredb.migrations.fixtures

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import org.json.JSONObject

/**
 * Immutable version-30 Room database synthesized from DDL and a Room identity
 * extracted from the officially released Fearless Wallet v2.0.0 (51) Google
 * APK. This is not a copy of any released user's database.
 *
 * The generated binary database and synthetic six-field SCALE secret are
 * frozen test inputs. They must never be rebuilt from current Room entities or
 * the current seven-field MetaAccountSecrets schema.
 */
object ReleasedV200DatabaseFixture {

    const val DATABASE_ASSET = "migrations/2.0.0/app-database-v30.db"
    const val SCHEMA_ASSET = "migrations/2.0.0/app-database-v30.sql"
    const val MANIFEST_ASSET = "migrations/2.0.0/fixture-manifest.json"
    const val LEGACY_SECRET_ASSET =
        "migrations/2.0.0/meta-account-secrets-v30-six-field.hex"
    const val DATABASE_SHA256 =
        "c9b071ca0037caec64236de73ecb2bed69321743379f8020ebf8f824319b5cf2"
    const val SCHEMA_SHA256 =
        "50c81ef39681051d50d0d170e0526df64378351056783d5087475c78da443af7"
    const val LEGACY_SECRET_ASSET_SHA256 =
        "fb30cf2e9c4918c22f0a269afc715802555c5b3ef97c794c82c375725773e51c"
    const val LEGACY_SECRET_SCALE_SHA256 =
        "291eb865d03d31f20ee6fbb5cfac9722784ad5ea20dc7a6add8b3ded402c7d19"
    const val OFFICIAL_APK_SHA256 =
        "c0f889119a416dc904c0033a9f8d596dd9c76da255ae5cb3ea14e361353a04a7"
    const val APK_DEX_SHA256 =
        "8ff143194a93ac9a6569e922485b766e82153e33c6a424fd6660746040787db4"
    const val RELEASE_TAG = "release/prod/v2.0.0_51"
    const val RELEASE_TAG_COMMIT = "e43832fab8f59bbf57c8ca97d7384c0ad77fd90e"
    const val APP_DATABASE_SOURCE_SHA256 =
        "8a2e54490b3971d6017c13fbea76d8e560569e69a335d73df2a16f6b61d5ec8a"
    const val APK_ROOM_CLASS =
        "jp.co.soramitsu.core_db.AppDatabase_Impl\$1"
    const val APK_ROOM_METHOD = "createAllTables"
    const val LEGACY_SECRET_SCHEMA_SOURCE_SHA256 =
        "b13ae4b101c883665d33f9d593c95f4a7d5cdbd900e0d5c90aaa7269f1f4a133"
    const val LEGACY_SECRET_SCHEMA_SOURCE =
        "common/src/main/java/jp/co/soramitsu/common/data/secrets/v2/MetaAccountSecrets.kt"
    const val LEGACY_SECRET_SCHEMA =
        "jp.co.soramitsu.common.data.secrets.v2.MetaAccountSecrets"
    const val LEGACY_SECRET_VECTOR_SOURCE =
        "core-db/src/androidTest/java/jp/co/soramitsu/coredb/migrations/" +
            "ReleasedV200MigrationSafetyTest.kt"
    const val LEGACY_SECRET_VECTOR_FUNCTION =
        "frozen six-field deterministic constants"
    const val ROOM_IDENTITY_HASH = "11b78abe23c541401fca8e23fc427a86"
    const val DATABASE_VERSION = 30
    const val EXPECTED_ROOM_TABLE_COUNT = 16
    const val EXPECTED_EXPLICIT_INDEX_COUNT = 9
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
        check(databaseDirectory.isDirectory || databaseDirectory.mkdirs()) {
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
                "Frozen v2.0.0 database fixture hash mismatch"
            }
            check(temporary.renameTo(target)) {
                "Could not atomically install frozen v2.0.0 database fixture"
            }
            check(target.sha256() == DATABASE_SHA256) {
                "Installed v2.0.0 database fixture hash mismatch"
            }
            verifyInstalled(target)
        } finally {
            if (temporary.exists()) {
                check(temporary.delete()) {
                    "Could not remove temporary v2.0.0 database fixture"
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
            "Frozen v2.0.0 schema SQL hash mismatch"
        }
        check(context.assetSha256(DATABASE_ASSET) == DATABASE_SHA256) {
            "Frozen v2.0.0 database asset hash mismatch"
        }
        check(context.assetSha256(LEGACY_SECRET_ASSET) == LEGACY_SECRET_ASSET_SHA256) {
            "Frozen v2.0.0 legacy secret asset hash mismatch"
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
        check(extraction.getString("class") == APK_ROOM_CLASS)
        check(extraction.getString("method") == APK_ROOM_METHOD)
        check(extraction.getInt("room_version") == DATABASE_VERSION)
        check(extraction.getString("room_identity_hash") == ROOM_IDENTITY_HASH)
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
        check(secretExtraction.getString("tag_commit") == RELEASE_TAG_COMMIT)
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
            assets.getString("legacy_secret_v30") ==
                LEGACY_SECRET_ASSET.substringAfterLast('/')
        )
        check(
            assets.getString("legacy_secret_v30_sha256") ==
                LEGACY_SECRET_ASSET_SHA256
        )
        check(
            assets.getString("legacy_secret_v30_scale_sha256") ==
                LEGACY_SECRET_SCALE_SHA256
        )

        val legacySecretBytes =
            context.readCanonicalLegacySecretHex().decodeCanonicalHex()
        check(legacySecretBytes.size == LEGACY_SECRET_SCALE_BYTE_COUNT) {
            "Frozen v2.0.0 legacy secret byte count mismatch"
        }
        check(legacySecretBytes.sha256() == LEGACY_SECRET_SCALE_SHA256) {
            "Frozen v2.0.0 legacy secret SCALE hash mismatch"
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
                "Frozen v2.0.0 Room identity hash mismatch"
            }
            check(database.singleString("PRAGMA integrity_check") == "ok") {
                "Frozen v2.0.0 database failed SQLite integrity_check"
            }
            check(database.singleInt("SELECT COUNT(*) FROM pragma_foreign_key_check") == 0) {
                "Frozen v2.0.0 database failed foreign_key_check"
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
                "Frozen v2.0.0 database table count mismatch"
            }
            check(
                database.singleInt(
                    """
                    SELECT COUNT(*) FROM sqlite_master
                    WHERE type = 'index' AND sql IS NOT NULL
                    """.trimIndent()
                ) == EXPECTED_EXPLICIT_INDEX_COUNT
            ) {
                "Frozen v2.0.0 database index count mismatch"
            }
        }
    }

    private fun File.sha256(): String =
        inputStream().buffered().use { input -> input.sha256() }

    private fun Context.assetSha256(assetName: String): String =
        assets.open(assetName).buffered().use { input -> input.sha256() }

    private fun Context.readCanonicalLegacySecretHex(): String {
        val encoded = assets.open(LEGACY_SECRET_ASSET).use { input ->
            input.readBytes()
        }.toString(Charsets.UTF_8)
        check(encoded.endsWith('\n')) {
            "Frozen v2.0.0 legacy secret asset must end with LF"
        }
        check('\r' !in encoded && encoded.count { it == '\n' } == 1) {
            "Frozen v2.0.0 legacy secret asset must contain one canonical line"
        }
        return encoded.dropLast(1).also { canonicalHex ->
            canonicalHex.decodeCanonicalHex()
        }
    }

    private fun String.decodeCanonicalHex(): ByteArray {
        check(startsWith("0x")) {
            "Frozen v2.0.0 legacy secret asset must use a 0x prefix"
        }
        val body = substring(2)
        check(body.isNotEmpty() && body.length % 2 == 0) {
            "Frozen v2.0.0 legacy secret asset has invalid hex length"
        }
        check(body.all { it in '0'..'9' || it in 'a'..'f' }) {
            "Frozen v2.0.0 legacy secret asset must use lowercase canonical hex"
        }
        return ByteArray(body.length / 2) { byteIndex ->
            body.substring(byteIndex * 2, byteIndex * 2 + 2)
                .toInt(radix = 16)
                .toByte()
        }
    }

    private fun ByteArray.sha256(): String =
        inputStream().buffered().use { input -> input.sha256() }

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
