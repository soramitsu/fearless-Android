package jp.co.soramitsu.coredb.model.chain

import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OptionalCatalogTextTest {
    private suspend fun read(text: String): String? {
        val raw = text.toByteArray(Charsets.UTF_8)
        val requests = mutableListOf<Pair<Int, Int>>()
        val result = OptionalCatalogText.read(raw.size.toLong(), raw.takeIf { it.size <= OptionalCatalogText.CHUNK_BYTES }) { offset, count ->
            assertTrue(count in 1..OptionalCatalogText.CHUNK_BYTES)
            assertEquals(1 + requests.sumOf { it.second }, offset)
            requests += offset to count
            raw.copyOfRange(offset - 1, offset - 1 + count)
        }
        assertEquals(if (raw.size <= OptionalCatalogText.CHUNK_BYTES) 0 else raw.size, requests.sumOf { it.second })
        return result
    }

    @Test fun validOneAndThreeMiBJsonAreComplete() = runBlocking {
        for (size in listOf(1024 * 1024, 3 * 1024 * 1024)) {
            val value = "[\"" + "a".repeat(size) + "\"]"
            assertEquals(value, read(value))
        }
    }
    @Test fun multibyteTextAcrossChunkBoundaryAndNulAreExact() = runBlocking {
        val value = "a".repeat(OptionalCatalogText.CHUNK_BYTES - 1) + "雪😀\u0000é"
        assertEquals(value, read(value))
    }
    @Test fun exactLimitIsPreserved() = runBlocking {
        val value = "a".repeat(OptionalCatalogText.MAX_BYTES)
        assertEquals(value, read(value))
    }
    @Test fun nullNegativeAndOverLimitNeverReadOrReturnPrefix() = runBlocking {
        for (size in listOf(null, -1L, OptionalCatalogText.MAX_BYTES.toLong() + 1)) {
            assertNull(OptionalCatalogText.read(size, null) { _, _ -> fail("Not eligible for allocation or read"); null })
        }
    }
    @Test fun emptyValueRemainsDistinctFromAbsent() = runBlocking {
        assertEquals("", OptionalCatalogText.read(0, ByteArray(0)) { _, _ -> fail("Empty requires no query"); null })
    }
    @Test fun malformedUtf8DoesNotReturnPartialText() = runBlocking {
        assertNull(OptionalCatalogText.read(3, byteArrayOf(0x61, 0xc3.toByte(), 0x28)) { _, _ -> fail("Inline read queried"); null })
    }
    @Test fun missingShortAndLongChunkReturnNoPrefix() = runBlocking {
        for (data in listOf(null, byteArrayOf(), byteArrayOf(0x61, 0x62))) {
            assertNull(OptionalCatalogText.read(OptionalCatalogText.CHUNK_BYTES.toLong() + 1, null) { offset, count ->
                if (offset == 1) ByteArray(count) { 0x61 } else data
            })
        }
    }
    @Test fun shortInlineValueNeedsNoChunkQueries() = runBlocking {
        val raw = "[\"provider\"]".toByteArray()
        assertEquals(String(raw), OptionalCatalogText.read(raw.size.toLong(), raw) { _, _ -> fail("Short value queried"); null })
    }
    @Test fun missingMismatchedAndWideInlineAreAbsentWithoutQuery() = runBlocking {
        for ((length, inline) in listOf(2L to null, 2L to byteArrayOf(1), (OptionalCatalogText.CHUNK_BYTES.toLong() + 1) to byteArrayOf(1))) {
            assertNull(OptionalCatalogText.read(length, inline) { _, _ -> fail("Inconsistent projection queried"); null })
        }
    }
    @Test fun cancellationFromChunkPropagatesByIdentity() = runBlocking {
        val failure = CancellationException("cancel")
        try { OptionalCatalogText.read(OptionalCatalogText.CHUNK_BYTES.toLong() + 1, null) { _, _ -> throw failure }; fail("Cancellation swallowed") }
        catch (actual: CancellationException) { assertSame(failure, actual) }
    }
    @Test fun cancelledContextDoesNotAllocateOrRead() {
        val job = Job().apply { cancel() }
        try { runBlocking(job) { OptionalCatalogText.read(OptionalCatalogText.CHUNK_BYTES.toLong() + 1, null) { _, _ -> fail("Cancelled read"); null } }; fail("Cancellation swallowed") }
        catch (_: CancellationException) { }
    }
    @Test fun unrelatedStateFailurePropagatesByIdentity() = runBlocking {
        val failure = IllegalStateException("SQL unavailable")
        try { OptionalCatalogText.read(OptionalCatalogText.CHUNK_BYTES.toLong() + 1, null) { _, _ -> throw failure }; fail("State failure swallowed") }
        catch (actual: IllegalStateException) { assertSame(failure, actual) }
    }
    @Test fun fatalFailurePropagatesByIdentity() = runBlocking {
        val failure = OutOfMemoryError("synthetic propagation control")
        try { OptionalCatalogText.read(OptionalCatalogText.CHUNK_BYTES.toLong() + 1, null) { _, _ -> throw failure }; fail("Fatal failure swallowed") }
        catch (actual: OutOfMemoryError) { assertSame(failure, actual) }
    }
}
