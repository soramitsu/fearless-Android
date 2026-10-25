package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** Executes real temporary SQLite DDL and DML on a dedicated test database. */
abstract class EndpointRoomProjectionSchemaContract {
    interface Database : AutoCloseable { val db: SupportSQLiteDatabase }
    protected abstract fun openDatabase(): Database

    @Test fun standaloneIdleAndActiveRoomMetadataAreReadOnly() = fixture { db ->
        admit(db)
        tracking(db)
        admit(db)
        hooks(db)
        val before = boundedExactImage(db)
        admit(db)
        assertEquals(before, boundedExactImage(db))
        db.execSQL("INSERT INTO main.chain_nodes VALUES(1,'one')")
        assertEquals(1L, scalar(db, "SELECT invalidated FROM temp.room_table_modification_log WHERE table_id=9"))
        admit(db)
    }

    @Test fun everyMissingHookAndWrongTableIdIsRejected() {
        for (operation in listOf("DELETE", "INSERT", "UPDATE")) fixture { db ->
            tracking(db)
            hooks(db)
            db.execSQL("DROP TRIGGER temp.room_table_modification_trigger_chain_nodes_$operation")
            reject(db)
        }
        fixture { db ->
            tracking(db)
            hooks(db, tableId = 8)
            reject(db)
        }
    }

    @Test fun missingTrackingRowAndNonBooleanTrackingFlagAreRejected() {
        for (change in listOf(
            "DELETE FROM temp.room_table_modification_log WHERE table_id=9",
            "UPDATE temp.room_table_modification_log SET invalidated=2 WHERE table_id=9",
            "UPDATE temp.room_table_modification_log SET invalidated='text' WHERE table_id=9",
            "UPDATE temp.room_table_modification_log SET invalidated=X'01' WHERE table_id=9"
        )) fixture { db ->
            tracking(db)
            hooks(db)
            db.execSQL(change)
            reject(db)
        }
    }

    @Test fun counterfeitReservedHookOnAnotherTableAndExtraHookAreRejected() {
        for (change in listOf(
            "CREATE TEMP TRIGGER extra AFTER INSERT ON chain_nodes BEGIN SELECT 1; END",
            "DROP TRIGGER temp.room_table_modification_trigger_chain_nodes_INSERT"
        )) fixture { db ->
            tracking(db)
            hooks(db)
            db.execSQL(change)
            if (change.startsWith("DROP")) {
                db.execSQL(
                    "CREATE TEMP TRIGGER room_table_modification_trigger_chain_nodes_INSERT " +
                    "AFTER INSERT ON other_table BEGIN SELECT 1; END"
                )
            }
            reject(db)
        }
    }

    @Test fun trackingTableTriggersAndAlteredTrackingSchemaAreRejected() {
        fixture { db ->
            tracking(db)
            hooks(db)
            db.execSQL(
                "CREATE TEMP TRIGGER unexpected_tracking_effect AFTER UPDATE ON room_table_modification_log " +
                "BEGIN DELETE FROM chain_nodes; END"
            )
            reject(db)
        }
        fixture { db ->
            tracking(db, default = 1)
            hooks(db)
            reject(db)
        }
    }

    @Test fun projectionShadowsAndReservedNameViewsRemainRejected() {
        for (change in listOf(
            "CREATE TEMP TABLE chain_nodes(id)",
            "CREATE TEMP VIEW room_table_modification_log AS SELECT 9 AS table_id,0 AS invalidated"
        )) fixture { db ->
            db.execSQL(change)
            reject(db)
        }
    }

    @Test fun oversizedHookDefinitionIsBoundedAndRejected() = fixture { db ->
        tracking(db)
        hooks(db)
        db.execSQL("CREATE TEMP TRIGGER oversized AFTER INSERT ON chain_nodes BEGIN SELECT '" + "x".repeat(131072) + "'; END")
        reject(db)
    }

    @Test fun cancellationRejectsAndEndsTheReadSnapshot() = fixture { db ->
        tracking(db)
        hooks(db)
        val before = boundedExactImage(db)
        assertThrows(android.os.OperationCanceledException::class.java) {
            admit(db, CancellationSignal().apply { cancel() })
        }
        assertEquals(false, db.inTransaction())
        assertEquals(before, boundedExactImage(db))
    }

    private fun fixture(action: (SupportSQLiteDatabase) -> Unit) {
        openDatabase().use { handle ->
            val db = handle.db
            db.execSQL("CREATE TABLE chain_nodes(id INTEGER PRIMARY KEY,name TEXT)")
            db.execSQL("CREATE TABLE other_table(id INTEGER PRIMARY KEY)")
            action(db)
        }
    }

    private fun tracking(db: SupportSQLiteDatabase, default: Int = 0) {
        db.execSQL("CREATE TEMP TABLE room_table_modification_log (table_id INTEGER PRIMARY KEY, invalidated INTEGER NOT NULL DEFAULT $default)")
        db.execSQL("INSERT INTO temp.room_table_modification_log VALUES(9,0)")
    }

    private fun hooks(db: SupportSQLiteDatabase, tableId: Int = 9) {
        for (operation in listOf("DELETE", "INSERT", "UPDATE")) db.execSQL(
            "CREATE TEMP TRIGGER `room_table_modification_trigger_chain_nodes_$operation` AFTER $operation ON `chain_nodes` " +
                "BEGIN UPDATE room_table_modification_log SET invalidated = 1 WHERE table_id = $tableId AND invalidated = 0; END"
        )
    }

    private fun admit(db: SupportSQLiteDatabase, signal: CancellationSignal = CancellationSignal()) {
        db.beginTransactionNonExclusive()
        try {
            EndpointRoomProjectionSchema.requireSchema(
                db, signal,
                "name='chain_nodes' COLLATE NOCASE OR tbl_name='chain_nodes' COLLATE NOCASE OR " +
                    "name='index_chain_nodes_chainId' COLLATE NOCASE OR name='sqlite_autoindex_chain_nodes_1' COLLATE NOCASE"
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun reject(db: SupportSQLiteDatabase) {
        val before = boundedExactImage(db)
        assertThrows(IllegalStateException::class.java) { admit(db) }
        assertEquals(before, boundedExactImage(db))
        assertEquals(false, db.inTransaction())
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long = db.query(sql).use {
        check(it.moveToFirst())
        it.getLong(0)
    }

    private fun boundedExactImage(db: SupportSQLiteDatabase): List<String> = buildList {
        for (schema in listOf("main", "temp")) db.query(
            "SELECT type,name,tbl_name,length(CAST(sql AS BLOB)) FROM $schema.sqlite_master ORDER BY name"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val metadata = (0..3).joinToString("/") { cursor.getString(it) }
                val name = cursor.getString(1)
                val size = cursor.getLong(3)
                val digest = java.security.MessageDigest.getInstance("SHA-256")
                var offset = 0L
                while (offset < size) {
                    db.query(
                        "SELECT substr(CAST(sql AS BLOB),?,?) FROM $schema.sqlite_master WHERE name=?",
                        arrayOf<Any?>(offset + 1, minOf(4096L, size - offset), name)
                    ).use { part ->
                        check(part.moveToFirst())
                        digest.update(part.getBlob(0))
                        check(!part.moveToNext())
                    }
                    offset += 4096
                }
                add(schema + "/" + metadata + "/" + digest.digest().contentToString())
            }
        }
        db.query("SELECT id,name FROM main.chain_nodes ORDER BY id").use { cursor ->
            while (cursor.moveToNext()) add("row/" + cursor.getLong(0) + "/" + cursor.getString(1))
        }
        val tracking = db.query("SELECT 1 FROM temp.sqlite_master WHERE name='room_table_modification_log'").use {
            it.moveToFirst()
        }
        if (tracking) {
            db.query(
                "SELECT table_id,typeof(invalidated),hex(CAST(invalidated AS BLOB)) " +
                    "FROM temp.room_table_modification_log ORDER BY table_id"
            ).use { cursor ->
                while (cursor.moveToNext()) add("tracking/" + (0..2).joinToString("/") { cursor.getString(it) })
            }
        }
    }
}
