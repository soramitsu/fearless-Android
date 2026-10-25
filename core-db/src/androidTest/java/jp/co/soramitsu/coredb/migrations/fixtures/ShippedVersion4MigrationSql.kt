package jp.co.soramitsu.coredb.migrations.fixtures

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import java.security.MessageDigest
import org.json.JSONObject

/**
 * The SQL shipped 4.x builds ran from database 71 to 75, frozen by
 * `scripts/extract-shipped-ton-migration-sql.py` from the release refs.
 *
 * The release candidate rewrote TonMigration (71 -> 72): it keeps the chain
 * registry, while every shipped 4.x build dropped and recreated it in another
 * column order. Tests of users who already run 4.x must therefore replay this
 * copy, never the current migrations, to build their starting database.
 */
object ShippedVersion4MigrationSql {

    const val SQL_ASSET = "migrations/4.x-shipped/shipped-71-to-75.sql"
    const val MANIFEST_ASSET = "migrations/4.x-shipped/migration-manifest.json"
    const val SQL_SHA256 =
        "37ef46bab7a1e1e5f776c5a1f5cfbfc685cf4e05ee6bb967ca1fdc20475d0884"
    const val TON_MIGRATION_BLOB = "da5238cf4c8ea8ff6557a7dba9a63e638010d6ba"

    /** 4.0.1 and 4.0.2 leave database 73. */
    const val RELEASE_4_0_1 = "4.0.1"

    /** 4.1.1 (and the v4.2.0 tag) leave database 74 with a sora_card table. */
    const val RELEASE_4_1_1 = "4.1.1"

    /** The Play 4.2.0 (229) source leaves database 75. */
    const val RELEASE_4_2_0 = "b5e22d49d"

    data class Edge(
        val from: Int,
        val to: Int,
        val releases: List<String>,
        val statements: List<String>
    )

    fun load(context: Context): List<Edge> {
        val bytes = context.assets.open(SQL_ASSET).use { it.readBytes() }
        check(bytes.sha256() == SQL_SHA256) { "Frozen shipped 4.x SQL hash mismatch" }
        val manifest = context.assets.open(MANIFEST_ASSET).use { input ->
            JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
        }
        check(
            manifest.getJSONObject("frozen_assets").getString("sql_sha256") == SQL_SHA256
        )
        val blobs = manifest.getJSONObject("source_blobs")
        blobs.keys().forEach { release ->
            check(
                blobs.getJSONObject(release).getString("TonMigration.kt") ==
                    TON_MIGRATION_BLOB
            ) { "Shipped TonMigration of $release is not the frozen blob" }
        }

        val edges = mutableListOf<Edge>()
        var statements: MutableList<String>? = null
        var statement: StringBuilder? = null
        fun closeStatement() {
            statement?.let { checkNotNull(statements) += it.toString() }
            statement = null
        }
        bytes.toString(Charsets.UTF_8).removeSuffix("\n").split("\n").forEach { line ->
            when {
                line.startsWith(MIGRATION_MARKER) -> {
                    closeStatement()
                    val parts = line.removePrefix(MIGRATION_MARKER).split(" ")
                    statements = mutableListOf()
                    edges += Edge(
                        from = parts[0].toInt(),
                        to = parts[1].toInt(),
                        releases = parts[2].split(","),
                        statements = checkNotNull(statements)
                    )
                }

                line == STATEMENT_MARKER -> {
                    closeStatement()
                    statement = StringBuilder()
                }

                statement != null -> statement!!.apply {
                    if (isNotEmpty()) append('\n')
                    append(line)
                }

                else -> check(line.startsWith("--")) { "Unexpected SQL outside a statement" }
            }
        }
        closeStatement()
        check(edges.isNotEmpty() && edges.all { it.statements.isNotEmpty() })
        return edges
    }

    /**
     * Upgrades a version 71 database through one shipped release per edge
     * ([releasesByEdge] starts at 71 -> 72), as Room's open helper ran it: in
     * one transaction with foreign-key enforcement off. Room then wrote the
     * identity of the version it was built for.
     */
    fun replay(
        context: Context,
        database: SupportSQLiteDatabase,
        releasesByEdge: List<String>,
        afterEdge: (from: Int, to: Int) -> Unit = { _, _ -> }
    ) {
        val edges = load(context)
        val manifest = context.assets.open(MANIFEST_ASSET).use { input ->
            JSONObject(input.bufferedReader(Charsets.UTF_8).readText())
        }
        check(database.version == 71)
        database.setForeignKeyConstraintsEnabled(false)
        database.beginTransaction()
        try {
            var version = 71
            releasesByEdge.forEach { release ->
                val edge = edges.single { it.from == version && release in it.releases }
                edge.statements.forEach(database::execSQL)
                afterEdge(edge.from, edge.to)
                version = edge.to
            }
            database.version = version
            database.execSQL(
                "UPDATE room_master_table SET identity_hash = ? WHERE id = 42",
                arrayOf(
                    manifest.getJSONObject("shipped_room_identity_hashes")
                        .getString(version.toString())
                )
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        database.setForeignKeyConstraintsEnabled(true)
    }

    private fun ByteArray.sha256(): String =
        MessageDigest.getInstance("SHA-256").digest(this)
            .joinToString(separator = "") { byte ->
                (byte.toInt() and 0xff).toString(16).padStart(2, '0')
            }

    private const val MIGRATION_MARKER = "-- @migration "
    private const val STATEMENT_MARKER = "-- @statement"
}
