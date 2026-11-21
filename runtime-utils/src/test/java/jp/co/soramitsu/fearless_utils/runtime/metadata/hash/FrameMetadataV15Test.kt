package jp.co.soramitsu.fearless_utils.runtime.metadata.hash

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameMetadataV15Test {

    private val polkadot = MetadataHashFixtures.chain("Polkadot")
    private val bytes = MetadataHashFixtures.metadataBytes(polkadot)

    @Test
    fun `decodes the extrinsic format and pallets of real metadata`() {
        val metadata = FrameMetadataV15.decode(bytes)
        assertEquals(4, metadata.extrinsic.version)
        assertEquals(polkadot.signedExtensions, metadata.extrinsic.signedExtensions.map { it.identifier })
        assertTrue(metadata.pallets.any { it.name == "Balances" })
        assertTrue(metadata.types.isNotEmpty())
    }

    @Test
    fun `rejects other versions, other magic, truncation and trailing bytes`() {
        val otherVersion = bytes.copyOf().also { it[4] = 14 }
        val otherMagic = bytes.copyOf().also { it[0] = 0 }
        listOf(otherVersion, otherMagic, bytes.copyOf(bytes.size - 1), bytes + byteArrayOf(0)).forEach { input ->
            assertThrows(MetadataDecodeException::class.java) { FrameMetadataV15.decode(input) }
        }
    }

    @Test
    fun `unwraps the metadata_at_version runtime call result`() {
        val some = ScaleOutput().writeU8(1).writeCompactU32(bytes.size.toLong()).writeBytes(bytes).toByteArray()
        assertArrayEquals(bytes, FrameMetadataV15.unwrapVersionedResult(some))
        assertEquals(polkadot.signedExtensions, FrameMetadataV15.decodeVersionedResult(some)!!.extrinsic.signedExtensions.map { it.identifier })
        assertNull(FrameMetadataV15.unwrapVersionedResult(byteArrayOf(0)))
        assertThrows(MetadataDecodeException::class.java) { FrameMetadataV15.unwrapVersionedResult(some + byteArrayOf(0)) }
        assertThrows(MetadataDecodeException::class.java) { FrameMetadataV15.unwrapVersionedResult(byteArrayOf(2)) }
    }

    @Test
    fun `reads pallet constants`() {
        val metadata = FrameMetadataV15.decode(bytes)
        assertArrayEquals(byteArrayOf(0, 0), metadata.constant("System", "SS58Prefix"))
        assertNull(metadata.constant("System", "Missing"))
        assertNull(metadata.constant("Missing", "SS58Prefix"))
    }
}
