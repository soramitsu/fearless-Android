package jp.co.soramitsu.account.impl.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PortableWalletAssetRowPresentationTest {
    private val codec = PortableWalletAssetRowPresentation

    @Test
    fun `versioned asset rows retain generic and named account distinctions`() {
        val rows = listOf(
            row(emptyList(), enabled = 1, sortIndex = -2, marked = true, name = ""),
            row(listOf(1, 0x80.toByte()), enabled = 0, name = "Main")
        )
        val encoded = codec.encode(rows)
        try {
            assertEquals(VECTOR, encoded.hex())
            assertEquals(rows, codec.decode(encoded))
            assertEquals(
                listOf(row(emptyList(), enabled = null, marked = true)),
                codec.decode(codec.encode(listOf(row(emptyList(), enabled = null, marked = true))))
            )
        } finally {
            encoded.fill(0)
        }
    }

    @Test
    fun `rejects duplicate unordered default and malformed source rows`() {
        val generic = row(emptyList(), enabled = 1)
        val account = row(listOf(1), enabled = 0)
        val invalid = listOf(
            listOf(generic, generic),
            listOf(account, generic),
            listOf(row(emptyList(), enabled = null)),
            listOf(row(emptyList(), enabled = 2)),
            listOf(row(emptyList(), enabled = 1).copy(chainId = "")),
            listOf(row(emptyList(), enabled = 1).copy(assetId = "\uD800")),
            listOf(row(List(129) { 1 }, enabled = 1)),
            listOf(row(emptyList(), enabled = 1, name = "x".repeat(2_049)))
        )
        invalid.forEach { rows ->
            assertThrows(IllegalArgumentException::class.java) { codec.encode(rows).fill(0) }
        }
    }

    @Test
    fun `orders distinct composed and decomposed chain ids by exact UTF-8 bytes`() {
        val decomposed = row(emptyList(), enabled = 1).copy(chainId = "e\u0301")
        val composed = row(emptyList(), enabled = 1).copy(chainId = "\u00e9")
        val ordered = listOf(decomposed, composed)
        val encoded = codec.encode(ordered)
        try {
            assertEquals(ordered, codec.decode(encoded))
            assertThrows(IllegalArgumentException::class.java) {
                codec.encode(ordered.reversed()).fill(0)
            }
        } finally {
            encoded.fill(0)
        }
    }

    @Test
    fun `rejects noncanonical wire booleans counts ordering and truncation`() {
        val valid = codec.encode(listOf(row(emptyList(), enabled = 1, sortIndex = -2, marked = true, name = "")))
        try {
            listOf(
                valid.copyOfRange(0, valid.size - 1),
                valid.copyOf().also { it[0] = 2 },
                valid.copyOf().also { it[2] = 0 },
                valid.copyOf().also { it[16] = 3 },
                valid.copyOf().also { it[21] = 2 },
                valid.copyOf().also { it[22] = 2 },
                valid + byteArrayOf(0)
            ).forEach { malformed ->
                assertThrows(IllegalArgumentException::class.java) { codec.decode(malformed) }
                malformed.fill(0)
            }
        } finally {
            valid.fill(0)
        }
    }

    @Test
    fun `rejects presentation value exceeding the envelope metadata bound`() {
        val rows = (0..100).map { index ->
            row(emptyList(), enabled = 1).copy(assetId = "a${index.toString().padStart(4, '0')}" + "x".repeat(500))
        }
        assertThrows(IllegalArgumentException::class.java) { codec.encode(rows).fill(0) }
    }

    private fun row(
        accountId: List<Byte>,
        enabled: Int?,
        sortIndex: Int = Int.MAX_VALUE,
        marked: Boolean = false,
        name: String? = null
    ) = PortableWalletAssetRowPresentation.Row(
        "sora", "dot", accountId, enabled, sortIndex, marked, name
    )

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        private const val VECTOR =
            "0100020004736f72610003646f74000002fffffffe010100000004736f7261" +
                "0003646f7400020180017fffffff000100044d61696e"
    }
}
