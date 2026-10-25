package jp.co.soramitsu.coredb.migrations.fixtures

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.security.MessageDigest
import org.json.JSONObject

/**
 * The Room schema of one released build, read from
 * `assets/migrations/<release>/fixture-manifest.json` and the frozen SQL next
 * to it.
 *
 * Format 1 is the original hand-frozen layout (2.0.0, 3.7.4). Format 2 is
 * written by `tools/upgrade-fixtures/android/extract_room_schema.py` from the
 * official APKs listed in `tools/upgrade-fixtures/android/releases.json`.
 * Neither may be regenerated from current entities.
 */
data class ReleasedSchemaFixture(
    val directory: String,
    val fixtureFormat: Int,
    val databaseVersion: Int,
    val sourceRelease: String,
    val roomIdentityHash: String?,
    val tables: List<String>,
    val statements: List<String>
) {
    override fun toString(): String = "$directory (Room $databaseVersion)"
}

internal object ReleasedSchemaFixtures {

    private const val ROOT = "migrations"
    private const val MANIFEST = "fixture-manifest.json"
    private val USER_VERSION = Regex("""^PRAGMA user_version = (\d+);$""")

    /**
     * Connection settings in format-1 files. The test opens its own
     * connection, so they carry no schema information.
     */
    private val CONNECTION_PRAGMAS = listOf(
        "PRAGMA page_size",
        "PRAGMA journal_mode",
        "PRAGMA foreign_keys"
    )

    fun all(context: Context): List<ReleasedSchemaFixture> {
        val directories = checkNotNull(context.assets.list(ROOT)) {
            "No released schema fixtures are packaged"
        }
        return directories
            .filter { directory ->
                context.assets.list("$ROOT/$directory").orEmpty().contains(MANIFEST)
            }
            .map { directory -> load(context, directory) }
            .sortedWith(compareBy(ReleasedSchemaFixture::databaseVersion, ReleasedSchemaFixture::directory))
    }

    fun load(context: Context, directory: String): ReleasedSchemaFixture {
        val manifest = JSONObject(context.readAsset("$ROOT/$directory/$MANIFEST"))
        val format = manifest.getInt("fixture_format")
        val assets = manifest.getJSONObject("frozen_assets")
        val extraction = manifest.getJSONObject("schema_extraction")
        val sqlName = assets.getString("schema_sql")
        val sqlBytes = context.readAssetBytes("$ROOT/$directory/$sqlName")
        check(sqlBytes.sha256() == assets.getString("schema_sql_sha256")) {
            "Released schema fixture $directory: SQL hash differs from its manifest"
        }

        val (databaseVersion, sourceRelease, tables) = when (format) {
            1 -> Triple(
                extraction.getInt("room_version"),
                manifest.getJSONObject("release").getString("tag"),
                emptyList()
            )

            2 -> Triple(
                manifest.getInt("db_version"),
                manifest.getJSONObject("source_release").getString("id"),
                manifest.getJSONObject("schema_check").getJSONArray("tables").let { array ->
                    List(array.length(), array::getString)
                }
            )

            else -> error("Released schema fixture $directory has unknown format $format")
        }
        val identityHash = if (extraction.isNull("room_identity_hash")) {
            null
        } else {
            extraction.getString("room_identity_hash")
        }

        var declaredVersion: Int? = null
        val statements = mutableListOf<String>()
        String(sqlBytes, Charsets.UTF_8).lineSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("--") }
            .forEach { line ->
                val userVersion = USER_VERSION.matchEntire(line)
                when {
                    userVersion != null -> declaredVersion = userVersion.groupValues[1].toInt()
                    CONNECTION_PRAGMAS.any(line::startsWith) -> Unit
                    else -> {
                        check(line.endsWith(";")) {
                            "Released schema fixture $directory: statement does not end its line: $line"
                        }
                        statements += line.removeSuffix(";")
                    }
                }
            }
        check(declaredVersion == databaseVersion) {
            "Released schema fixture $directory declares user_version $declaredVersion, " +
                "manifest says $databaseVersion"
        }
        if (identityHash != null) {
            check(statements.last().contains("'$identityHash'")) {
                "Released schema fixture $directory does not write its Room identity hash"
            }
        }
        return ReleasedSchemaFixture(
            directory = directory,
            fixtureFormat = format,
            databaseVersion = databaseVersion,
            sourceRelease = sourceRelease,
            roomIdentityHash = identityHash,
            tables = tables,
            statements = statements
        )
    }

    /**
     * Creates [databaseName] exactly as a fresh install of the release left
     * it: its own DDL, its identity row and its user_version, no rows.
     */
    fun createDatabase(
        context: Context,
        databaseName: String,
        fixture: ReleasedSchemaFixture
    ) {
        context.deleteDatabase(databaseName)
        val databaseFile = context.getDatabasePath(databaseName)
        val databaseDirectory = checkNotNull(databaseFile.parentFile) {
            "Released schema fixture database has no parent directory"
        }
        check(databaseDirectory.isDirectory || databaseDirectory.mkdirs()) {
            "Could not create the released schema fixture database directory"
        }
        SQLiteDatabase.openOrCreateDatabase(databaseFile, null).use { database ->
            database.beginTransaction()
            try {
                fixture.statements.forEach(database::execSQL)
                database.version = fixture.databaseVersion
                database.setTransactionSuccessful()
            } finally {
                database.endTransaction()
            }
        }
    }

    private fun Context.readAsset(path: String): String =
        String(readAssetBytes(path), Charsets.UTF_8)

    private fun Context.readAssetBytes(path: String): ByteArray =
        assets.open(path).use { input -> input.readBytes() }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
