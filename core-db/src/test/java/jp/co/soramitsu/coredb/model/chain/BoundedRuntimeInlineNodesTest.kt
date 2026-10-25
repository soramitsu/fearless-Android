package jp.co.soramitsu.coredb.model.chain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoundedRuntimeInlineNodesTest {
    private suspend fun read(row: RuntimeNodeMetadata, chunk: suspend (Long, BoundedRuntimeNodes.Field, Int, Int) -> ByteArray? =
        { _, _, _, _ -> error("Unexpected payload query") }) =
        BoundedRuntimeNodes.read("a", { if (it == null) listOf(row) else emptyList() }, chunk)

    private fun row(url: ByteArray = "wss://public.invalid".toByteArray(), name: ByteArray = "東京".toByteArray()) =
        RuntimeNodeMetadata(1, 1, url.size.toLong(), name.size.toLong(), 1, 0, url, name)

    @Test fun smallInlineFieldsDecodeExactlyWithoutPayloadQueries() = runBlocking<Unit> {
        val result = read(row())
        assertEquals(BoundedRuntimeNodes.Selection.AVAILABLE, result.selection)
        assertEquals("wss://public.invalid", result.nodes.single().url)
        assertEquals("東京", result.nodes.single().name)
        assertFalse(result.nodes.single().isDefault)
    }
    @Test fun emptyInlineNameRemainsEmptyWithoutPayloadQuery() = runBlocking<Unit> {
        assertEquals("", read(row(name=byteArrayOf())).nodes.single().name)
    }
    @Test fun exact4096ByteInlineBoundaryRemainsReadable() = runBlocking<Unit> {
        assertEquals("x".repeat(4096), read(row(name="x".repeat(4096).toByteArray())).nodes.single().name)
    }
    @Test fun largerOmittedFieldUsesExactBoundedChunks() = runBlocking<Unit> {
        val text = "x".repeat(65535) + "😀"
        val bytes = text.toByteArray()
        val queried = mutableListOf<Pair<Int,Int>>()
        val result = read(row().copy(nameBytes=bytes.size.toLong(), nameInline=null)) { _, field, offset, count ->
            assertEquals(BoundedRuntimeNodes.Field.NAME, field)
            queried += offset to count
            bytes.copyOfRange(offset-1,offset-1+count)
        }
        assertEquals(text,result.nodes.single().name)
        assertEquals(listOf(1 to 65536,65537 to 3),queried)
    }
    @Test fun contradictoryInlineLengthCannotFallbackToDifferentBytes() = runBlocking<Unit> {
        for (r in listOf(row().copy(urlBytes=1),row().copy(nameBytes=1))) {
            val result=read(r)
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE,result.selection)
            assertTrue(result.nodes.isEmpty())
        }
    }
    @Test fun unexpectedlyOversizedInlineProjectionIsRejected() = runBlocking<Unit> {
        val result=read(row(name="x".repeat(4097).toByteArray()))
        assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE,result.selection)
        assertTrue(result.nodes.isEmpty())
    }
    @Test fun invalidUtf8IsNeverReplacementDecoded() = runBlocking<Unit> {
        for (r in listOf(row(url=byteArrayOf(0x80.toByte())),row(name=byteArrayOf(0xc0.toByte(),0xaf.toByte())))) {
            val result=read(r)
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE,result.selection)
            assertTrue(result.nodes.isEmpty())
        }
    }
    @Test fun invalidFlagsAndAggregateBoundsStillRejectInlinePayload() = runBlocking<Unit> {
        for(r in listOf(row().copy(active=null),row().copy(default=null),row().copy(chainBytes=1_048_576),row().copy(chainBytes=2))) {
            val result=read(r)
            assertEquals(BoundedRuntimeNodes.Selection.UNREADABLE,result.selection)
            assertTrue(result.nodes.isEmpty())
        }
    }
}
