package jp.co.soramitsu.coredb.recovery.decision

import android.database.Cursor
import android.os.CancellationSignal
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import jp.co.soramitsu.coredb.recovery.endpointInteger
import jp.co.soramitsu.coredb.recovery.requireOwnedEndpointSchema

/** Exact scalar occupancy; opaque adopted endpoint values never enter a CursorWindow. */
internal object EndpointAttachmentSql {
    data class Occupancy(
        val owner: EndpointAttachmentPolicy.Owner?,
        val originRowId: Long?,
        val projection: EndpointAttachmentPolicy.Projection?
    )

    private const val PROJECTION_CREATE = "CREATE TABLE `chain_nodes` (`chainId` TEXT NOT NULL, " +
        "`url` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `isDefault` INTEGER NOT NULL, " +
        "PRIMARY KEY(`chainId`, `url`), FOREIGN KEY(`chainId`) REFERENCES `chains`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
    // Room's fresh83 serialized CREATE differs from the shipped82 migration only at this space.
    private val FRESH_PROJECTION_CREATE = PROJECTION_CREATE.dropLast(1) + " )"
    private const val PROJECTION_INDEX = "CREATE INDEX `index_chain_nodes_chainId` ON `chain_nodes` (`chainId`)"
    private const val NAMESPACE = "name='chain_nodes' COLLATE NOCASE OR tbl_name='chain_nodes' COLLATE NOCASE OR " +
        "name='index_chain_nodes_chainId' COLLATE NOCASE OR tbl_name='index_chain_nodes_chainId' COLLATE NOCASE OR " +
        "name='sqlite_autoindex_chain_nodes_1' COLLATE NOCASE OR tbl_name='sqlite_autoindex_chain_nodes_1' COLLATE NOCASE"

    fun requireSchema(db: SupportSQLiteDatabase, signal: CancellationSignal) {
        check(db.inTransaction()) { "Endpoint attachment requires one SQL snapshot" }
        signal.throwIfCanceled()
        db.query(SimpleSQLiteQuery("PRAGMA main.encoding"), signal).use {
            check(it.columnCount == 1 && it.moveToFirst() && it.getType(0) == Cursor.FIELD_TYPE_STRING &&
                it.getString(0) == "UTF-8" && !it.moveToNext()) { "Endpoint attachment requires UTF-8" }
        }
        db.query(SimpleSQLiteQuery("PRAGMA foreign_keys"), signal).use {
            check(it.columnCount == 1 && it.moveToFirst() && it.endpointInteger(0) == 1L && !it.moveToNext()) {
                "Endpoint attachment requires enforced foreign keys"
            }
        }
        val cancellable = object : SupportSQLiteDatabase by db {
            override fun query(query: String): Cursor = this.query(SimpleSQLiteQuery(query))
            override fun query(query: String, bindArgs: Array<out Any?>): Cursor = this.query(SimpleSQLiteQuery(query, bindArgs))
            override fun query(query: SupportSQLiteQuery): Cursor {
                signal.throwIfCanceled()
                return db.query(query, signal)
            }
        }
        requireDecisionSchema(cancellable, signal)
        check(requireOwnedEndpointSchema(cancellable)) { "Endpoint ownership schema is absent" }
        EndpointRoomProjectionSchema.requireSchema(db, signal, NAMESPACE)
        val found = mutableSetOf<String>()
        db.query(SimpleSQLiteQuery(
            "SELECT ${decisionTextProjection("type", 64)},${decisionTextProjection("name", 128)}," +
                "${decisionTextProjection("tbl_name", 128)},${decisionTextProjection("sql", 4096)},typeof(sql) " +
                "FROM main.sqlite_master WHERE $NAMESPACE LIMIT 4"
        ), signal).use { cursor ->
            while (cursor.moveToNext()) {
                signal.throwIfCanceled()
                check(cursor.columnCount == 5 && found.size < 3)
                val type = cursor.decisionText(0, 64)
                val name = cursor.decisionText(1, 128)
                check(found.add(name) && cursor.decisionText(2, 128) == "chain_nodes") {
                    "Endpoint projection namespace differs"
                }
                if (name == "sqlite_autoindex_chain_nodes_1") {
                    check(type == "index" && cursor.isNull(3) && cursor.getType(4) == Cursor.FIELD_TYPE_STRING &&
                        cursor.getString(4) == "null") { "Endpoint projection primary key differs" }
                } else {
                    val sql = cursor.decisionText(3, 4096)
                    check((name == "chain_nodes" && type == "table" &&
                        sql in setOf(PROJECTION_CREATE, FRESH_PROJECTION_CREATE)) ||
                        (name == "index_chain_nodes_chainId" && type == "index" && sql == PROJECTION_INDEX)) {
                        "Endpoint projection schema differs"
                    }
                }
            }
        }
        check(found == setOf("chain_nodes", "index_chain_nodes_chainId", "sqlite_autoindex_chain_nodes_1"))
        signal.throwIfCanceled()
    }

    fun occupancy(
        db: SupportSQLiteDatabase,
        signal: CancellationSignal,
        decision: EndpointRecoveryDecision
    ): Occupancy {
        check(db.inTransaction())
        val args = arrayOf<Any?>(decision.target.chainId, decision.candidate.url, decision.candidate.name)
        val matches = listOf(
            exactText("chainId", 1, EndpointDecisionLimits.TARGET_ID_BYTES),
            exactText("url", 2, EndpointDecisionLimits.URL_BYTES),
            exactText("name", 3, EndpointDecisionLimits.LABEL_BYTES)
        ).joinToString(" AND ")
        signal.throwIfCanceled()
        val owner = db.query(SimpleSQLiteQuery(
            "SELECT CASE WHEN typeof(id)='integer' THEN id END," +
                "CASE WHEN typeof(revision)='integer' THEN revision END," +
                "CASE WHEN typeof(attached)='integer' THEN attached END," +
                "CASE WHEN typeof(deleted)='integer' THEN deleted END," +
                "CASE WHEN $matches THEN 1 ELSE 0 END,typeof(originalSnapshotRowId)," +
                "CASE WHEN typeof(originalSnapshotRowId)='integer' THEN originalSnapshotRowId END " +
                "FROM main.custom_endpoint_records WHERE chainId=?1 AND url=?2 LIMIT 2", args
        ), signal).use { cursor ->
            signal.throwIfCanceled()
            if (!cursor.moveToFirst()) return@use null
            check(cursor.columnCount == 7)
            val value = EndpointAttachmentPolicy.Owner(
                cursor.endpointInteger(0), cursor.endpointInteger(1), cursor.endpointInteger(2),
                cursor.endpointInteger(3), cursor.endpointInteger(4) == 1L
            )
            check(cursor.getType(5) == Cursor.FIELD_TYPE_STRING)
            val origin = when (cursor.getString(5)) {
                "null" -> { check(cursor.isNull(6)); null }
                "integer" -> cursor.endpointInteger(6)
                else -> error("Endpoint ownership origin differs")
            }
            check(!cursor.moveToNext()) { "Endpoint ownership key is ambiguous" }
            value to origin
        }
        val projection = db.query(SimpleSQLiteQuery(
            "SELECT CASE WHEN typeof(isDefault)='integer' THEN isDefault END," +
                "CASE WHEN typeof(isActive)='integer' THEN isActive END,CASE WHEN $matches THEN 1 ELSE 0 END " +
                "FROM main.chain_nodes WHERE chainId=?1 AND url=?2 LIMIT 2", args
        ), signal).use { cursor ->
            signal.throwIfCanceled()
            if (!cursor.moveToFirst()) return@use null
            check(cursor.columnCount == 3)
            val value = EndpointAttachmentPolicy.Projection(
                cursor.endpointInteger(0), cursor.endpointInteger(1), cursor.endpointInteger(2) == 1L
            )
            check(!cursor.moveToNext()) { "Endpoint projection key is ambiguous" }
            value
        }
        signal.throwIfCanceled()
        return Occupancy(owner?.first, owner?.second, projection)
    }

    private fun exactText(column: String, parameter: Int, maximum: Int): String =
        "(typeof($column)='text' AND length(CAST($column AS BLOB))<=$maximum " +
            "AND CAST($column AS BLOB)=CAST(?$parameter AS BLOB))"
}
