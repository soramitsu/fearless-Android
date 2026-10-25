package jp.co.soramitsu.coredb.recovery.decision

import android.os.CancellationSignal
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions

class EndpointRoomProjectionSchemaTest {
    private val log = EndpointRoomProjectionSchema.Object(
        "table", "room_table_modification_log", "room_table_modification_log",
        "CREATE TABLE room_table_modification_log (table_id INTEGER PRIMARY KEY, invalidated INTEGER NOT NULL DEFAULT 0)"
    )

    // Independently transcribed from the actual API26/34/36 Room observer diagnostic.
    private val hooks = listOf("DELETE", "INSERT", "UPDATE").map {
        EndpointRoomProjectionSchema.Object(
            "trigger", "room_table_modification_trigger_chain_nodes_$it", "chain_nodes",
            "CREATE TRIGGER `room_table_modification_trigger_chain_nodes_$it` AFTER $it ON `chain_nodes` " +
                "BEGIN UPDATE room_table_modification_log SET invalidated = 1 WHERE table_id = 9 AND invalidated = 0; END"
        )
    }

    @Test fun schemaReadCannotRunOutsideTheGuardedWriterTransaction() {
        val database = mock<SupportSQLiteDatabase>()
        val signal = mock<CancellationSignal>()
        assertThrows(IllegalStateException::class.java) { EndpointRoomProjectionSchema.requireSchema(database, signal, "1") }
        verifyNoInteractions(signal)
    }

    @Test fun standaloneIdleAndObservedRoomHaveExactAdmission() {
        assertFalse(EndpointRoomProjectionSchema.validateObjects(emptyList()))
        assertFalse(EndpointRoomProjectionSchema.validateObjects(listOf(log)))
        assertTrue(EndpointRoomProjectionSchema.validateObjects(hooks.reversed() + log))
    }

    @Test fun missingDuplicateOrExtraObjectsAreRejected() {
        for (objects in listOf(
            hooks, hooks.take(2) + log, hooks + log + log,
            hooks + log + hooks[0].copy(name = "another_hook")
        )) {
            assertThrows(IllegalStateException::class.java) { EndpointRoomProjectionSchema.validateObjects(objects) }
        }
    }

    @Test fun counterfeitsWrongTargetsWrongIdsAndExtraEffectsAreRejected() {
        val original = hooks.first()
        for (wrong in listOf(
            original.copy(type = "table"), original.copy(name = original.name.uppercase()),
            original.copy(table = "custom_endpoint_records"), original.copy(sql = original.sql.replace("= 9", "= 8")),
            original.copy(sql = original.sql.replace(" END", " DELETE FROM chain_nodes; END")),
            original.copy(sql = original.sql + " "), original.copy(sql = "x".repeat(4097))
        )) {
            assertThrows(IllegalStateException::class.java) {
                EndpointRoomProjectionSchema.validateObjects(listOf(log, wrong) + hooks.drop(1))
            }
        }
    }

    @Test fun alteredTrackingTableAndShadowsAreRejectedEvenWithoutHooks() {
        for (wrong in listOf(
            log.copy(type = "view"), log.copy(name = "chain_nodes"),
            log.copy(table = "chain_nodes"), log.copy(sql = log.sql.replace("DEFAULT 0", "DEFAULT 1"))
        )) {
            assertThrows(IllegalStateException::class.java) { EndpointRoomProjectionSchema.validateObjects(listOf(wrong)) }
        }
    }
}
