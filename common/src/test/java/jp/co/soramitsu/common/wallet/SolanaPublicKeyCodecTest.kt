package jp.co.soramitsu.common.wallet

import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SolanaPublicKeyCodecTest {
    @Test
    fun `exact keys preserve leading zero bytes and canonical base58 representation`() {
        for (bytes in listOf(ByteArray(32), ByteArray(32) { it.toByte() }, ByteArray(32) { 0xff.toByte() })) {
            val encoded = SolanaPublicKeyCodec.encode(bytes)
            assertArrayEquals(bytes, SolanaPublicKeyCodec.decode(encoded))
        }
        assertEquals("1".repeat(32), SolanaPublicKeyCodec.encode(ByteArray(32)))
    }

    @Test
    fun `character valid but wrong byte length keys and signatures are rejected`() {
        for (value in listOf("1".repeat(31), "1".repeat(33), "z".repeat(44), "0".repeat(32), "l".repeat(32))) {
            assertThrows(IllegalArgumentException::class.java) { SolanaPublicKeyCodec.decode(value) }
        }
        assertThrows(IllegalArgumentException::class.java) { SolanaPublicKeyCodec.encode(ByteArray(31)) }
        assertArrayEquals(ByteArray(64), SolanaPublicKeyCodec.decodeFixedSize("1".repeat(64), 64))
        assertThrows(IllegalArgumentException::class.java) { SolanaPublicKeyCodec.decodeFixedSize("1".repeat(65), 64) }
    }
}
