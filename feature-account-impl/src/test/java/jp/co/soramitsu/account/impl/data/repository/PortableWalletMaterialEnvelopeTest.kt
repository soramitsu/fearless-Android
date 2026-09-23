package jp.co.soramitsu.account.impl.data.repository

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableWalletMaterialEnvelopeTest {
    private val codec = PortableWalletMaterialEnvelope
    private val source = PortableWalletMaterialEnvelope.SourceFormat.ANDROID_DRAFT_V2

    @Test
    fun `synthetic golden vectors match iOS codec in both source directions`() {
        val vectors = listOf(
            "4650574d4c453031010101000000000400010203" to
                Triple(
                    PortableWalletMaterialEnvelope.Origin.ANDROID,
                    source,
                    byteArrayOf(0, 1, 2, 3)
                ),
            "4650574d4c453031010202000000000210fe" to
                Triple(
                    PortableWalletMaterialEnvelope.Origin.IOS,
                    PortableWalletMaterialEnvelope.SourceFormat.IOS_KEYCHAIN_V2_INVENTORY,
                    byteArrayOf(0x10, 0xfe.toByte())
                )
        )
        vectors.forEach { (hex, expected) ->
            val encoded = hex.hexBytes()
            val decoded = codec.decode(encoded)
            try {
                assertEquals(expected.first, decoded.origin)
                assertEquals(expected.second, decoded.sourceFormat)
                assertEquals(
                    PortableWalletMaterialEnvelope.DerivationMode.LOCAL_OPAQUE,
                    decoded.derivationMode
                )
                assertArrayEquals(expected.third, decoded.payload)
                assertArrayEquals(encoded, codec.encode(decoded))
                assertEquals("PortableWalletMaterialEnvelope.Record(redacted)", decoded.toString())
            } finally {
                decoded.clearPayload()
                encoded.fill(0)
                expected.third.fill(0)
            }
        }
    }

    @Test
    fun `decoder rejects unknown source derivation length and trailing material`() {
        val valid = "4650574d4c453031010101000000000400010203".hexBytes()
        val invalid = listOf(
            valid.copyOf(valid.size - 1),
            valid + byteArrayOf(0),
            valid.copyOf().also { it[0] = 0 },
            valid.copyOf().also { it[8] = 2 },
            valid.copyOf().also { it[9] = 2 }, // iOS origin with Android-local format
            valid.copyOf().also { it[10] = 3 },
            valid.copyOf().also { it[11] = 1 }, // an unreviewed derivation mode
            valid.copyOf().also { it[12] = 0x7f },
            valid.copyOf().also { it[15] = 0 }
        )
        try {
            invalid.forEach { bytes ->
                assertThrows(
                    IllegalArgumentException::class.java
                ) { codec.decode(bytes).clearPayload() }
                bytes.fill(0)
            }
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(
                    PortableWalletMaterialEnvelope.Record(
                        PortableWalletMaterialEnvelope.Origin.ANDROID,
                        PortableWalletMaterialEnvelope.SourceFormat.IOS_KEYCHAIN_V2_INVENTORY,
                        PortableWalletMaterialEnvelope.DerivationMode.LOCAL_OPAQUE,
                        byteArrayOf(1)
                    )
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(
                    PortableWalletMaterialEnvelope.Record(
                        PortableWalletMaterialEnvelope.Origin.ANDROID,
                        source,
                        PortableWalletMaterialEnvelope.DerivationMode.LOCAL_OPAQUE,
                        ByteArray(256 * 1024)
                    )
                )
            }
            assertThrows(IllegalArgumentException::class.java) {
                codec.decode(ByteArray(256 * 1024))
            }
        } finally {
            valid.fill(0)
        }
    }

    private fun String.hexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
