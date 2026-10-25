package jp.co.soramitsu.coredb.model.chain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class BoundedRuntimeNodesTest {
    private class Fixture {
        val rows = mutableListOf<RuntimeNodeMetadata>()
        val values = mutableMapOf<Pair<Long, BoundedRuntimeNodes.Field>, ByteArray>()
        val chunks = mutableListOf<Int>()
        val after = mutableListOf<Long?>()
        fun add(
            id: Long,
            active: Long? = 0,
            default: Long? = 1,
            url: ByteArray = "wss://node-$id.invalid/".toByteArray(),
            name: ByteArray = "Node".toByteArray()
        ) {
            rows += RuntimeNodeMetadata(id, 1, url.size.toLong(), name.size.toLong(), active, default)
            values[id to BoundedRuntimeNodes.Field.URL] = url
            values[id to BoundedRuntimeNodes.Field.NAME] = name
        }
        suspend fun read() = BoundedRuntimeNodes.read("a", { cursor ->
            after += cursor
            rows.sortedBy { it.rowId }.filter { cursor == null || it.rowId > cursor }.take(64)
        }) { id, field, offset, count ->
            chunks += count
            values.getValue(id to field).copyOfRange(offset - 1, offset - 1 + count)
        }
    }

    @Test fun healthyRowsRetainExactValuesAndOnlyActualActiveSelection() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1)
        f.add(2, 1, 0)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, result.selection)
        assertEquals(listOf(false, true), result.nodes.map { it.isActive })
        assertEquals("wss://node-2.invalid/", result.nodes[1].url)
        assertFalse(result.nodes[1].isDefault)
    }

    @Test fun aggregateLegacyBoundaryRemainsReadableThroughBoundedChunks() = runBlocking<Unit> {
        val f = Fixture()
        val name = "x".repeat((EndpointProjectionLimits.MAX_TEXT_BYTES - 2).toInt())
        f.add(1, 1, url = byteArrayOf(65), name = name.toByteArray())
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, result.selection)
        assertEquals(name, result.nodes.single().name)
        assertTrue(f.chunks.size > 16)
        assertTrue(f.chunks.all { it <= 64 * 1024 })
    }

    @Test fun oversizedActiveMetadataDoesNotReadPayloadOrChooseFallback() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, 1)
        f.add(2)
        f.rows[0] = f.rows[0].copy(nameBytes = 3 * 1024 * 1024)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, result.selection)
        assertEquals(listOf("wss://node-2.invalid/"), result.nodes.map { it.url })
        assertTrue(result.nodes.none { it.isActive })
    }

    @Test fun unreadableInactiveTextDoesNotBlockHealthyActiveRow() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, name = byteArrayOf(0x80.toByte()))
        f.add(2, 1)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, result.selection)
        assertEquals(1, result.nodes.size)
        assertTrue(result.nodes.single().isActive)
    }

    @Test fun malformedActiveUtf8IsNeverReplacementDecoded() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, 1, url = byteArrayOf(0xc0.toByte(), 0xaf.toByte()))
        f.add(2)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, result.selection)
        assertTrue(result.nodes.none { it.isActive })
        assertEquals(1, result.nodes.size)
    }

    @Test fun unreadableActiveCannotHideAmbiguousActiveSelection() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, 1, name = byteArrayOf(0x80.toByte()))
        f.add(2, 1)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, result.selection)
        assertTrue(result.nodes.none { it.isActive })
    }

    @Test fun multipleReadableActiveRowsStayAmbiguous() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, 1)
        f.add(2, 1)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.AMBIGUOUS, result.selection)
        assertTrue(result.nodes.none { it.isActive })
    }

    @Test fun absentActiveRowNeverActivatesFirstDefault() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1)
        f.add(2, default = 0)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.MISSING, result.selection)
        assertTrue(result.nodes.none { it.isActive })
    }

    @Test fun malformedFlagsCannotBeFilteredIntoPlausibleSelection() = runBlocking<Unit> {
        val f = Fixture()
        f.add(1, 1)
        f.add(2, active = null)
        val result = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, result.selection)
        assertTrue(result.nodes.none { it.isActive })
    }

    @Test fun scalarPagesIncludeMinimumSignedRowIdAndAdvancePastNegativeValues() = runBlocking<Unit> {
        val f = Fixture()
        f.add(Long.MIN_VALUE, 1)
        for (id in -70L..70L) f.add(id)
        val result = f.read()
        assertEquals(142, result.nodes.size)
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, result.selection)
        assertEquals(null, f.after.first())
        assertEquals(4, f.after.size)
    }

    @Test fun utf8SequenceCrossingChunkBoundaryRemainsByteExact() = runBlocking<Unit> {
        val f = Fixture()
        val name = "x".repeat(65_535) + "😀"
        f.add(1, 1, name = name.toByteArray(StandardCharsets.UTF_8))
        assertEquals(name, f.read().nodes.single().name)
    }

    @Test fun missingOrShortChunkCannotCreatePartialNode() = runBlocking<Unit> {
        val row = RuntimeNodeMetadata(1, 1, 10, 0, 1, 1)
        for (chunk in listOf<ByteArray?>(null, byteArrayOf(1))) {
            val result = BoundedRuntimeNodes.read("a", { if (it == null) listOf(row) else emptyList() }) { _, _, _, _ -> chunk }
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, result.selection)
            assertTrue(result.nodes.isEmpty())
        }
    }

    @Test fun databaseFailuresPropagateInsteadOfPretendingRowWasAbsent() = runBlocking<Unit> {
        val row = RuntimeNodeMetadata(1, 1, 10, 0, 1, 1)
        assertTrue(
            runCatching {
            BoundedRuntimeNodes.read("a", { listOf(row) }) { _, _, _, _ -> error("public database failure") }
        }.isFailure
        )
    }

    @Test fun repeatedMetadataPageCannotLoopOrDuplicateRows() = runBlocking<Unit> {
        val row = RuntimeNodeMetadata(1, 1, 0, 0, 0, 1)
        assertTrue(runCatching { BoundedRuntimeNodes.read("a", { listOf(row) }) { _, _, _, _ -> null } }.isFailure)
    }

    @Test fun cancellationIsNotReclassifiedAsUnreadableEndpoint() {
        assertThrows(CancellationException::class.java) {
            runBlocking<Unit> {
                val row = RuntimeNodeMetadata(1, 1, 10, 0, 1, 1)
                BoundedRuntimeNodes.read("a", { if (it == null) listOf(row) else emptyList() }) { _, _, _, count ->
                    currentCoroutineContext()[Job]!!.cancel()
                    ByteArray(count)
                }
            }
        }
    }

    @Test fun inactiveMalformedDefaultDoesNotSuppressUniqueReadableActive() = runBlocking<Unit> {
        for (invalid in listOf<Long?>(null, -1, 2)) {
            val f = Fixture()
            f.add(1, active = 0, default = invalid)
            f.add(2, active = 1)
            val observed = f.read()
            assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, observed.selection)
            assertEquals(listOf("wss://node-2.invalid/"), observed.nodes.map { it.url })
            assertTrue(observed.nodes.single().isActive)
        }
    }

    @Test fun inactiveMalformedDefaultAcrossPagesDoesNotChangeSelectedIdentity() = runBlocking<Unit> {
        val f = Fixture()
        for (id in -70L..70L) f.add(id, active = 0, default = null)
        f.add(Long.MIN_VALUE, active = 1, default = 0)
        val observed = f.read()
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, observed.selection)
        assertEquals("wss://node-${Long.MIN_VALUE}.invalid/", observed.nodes.single().url)
        assertTrue(observed.nodes.single().isActive)
        assertFalse(observed.nodes.single().isDefault)
    }

    @Test fun activeMalformedDefaultStillRejectsWithoutPromotingFallback() = runBlocking<Unit> {
        for (invalid in listOf<Long?>(null, -1, 2)) {
            val f = Fixture()
            f.add(1, active = 1, default = invalid)
            f.add(2)
            val observed = f.read()
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, observed.selection)
            assertTrue(observed.nodes.none { it.isActive })
        }
    }

    @Test fun unknownActiveWithMalformedDefaultCannotHideCompetingSelection() = runBlocking<Unit> {
        for (invalid in listOf<Long?>(null, -1, 2)) {
            val f = Fixture()
            f.add(1, active = invalid, default = null)
            f.add(2, active = 1)
            val observed = f.read()
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE, observed.selection)
            assertTrue(observed.nodes.none { it.isActive })
        }
    }
}
