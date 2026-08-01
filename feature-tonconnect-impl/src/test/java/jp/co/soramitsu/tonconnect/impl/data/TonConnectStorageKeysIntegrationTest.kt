package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.storage.encrypt.TonConnectStorageKeys
import jp.co.soramitsu.coredb.model.ConnectionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TonConnectStorageKeysIntegrationTest {

    @Test
    fun scopedKeyUsesStableDomainSeparatedLengthPrefixedDigest() {
        val key = scoped(
            metaId = 1L,
            url = "https://example.com",
            source = ConnectionSource.QR
        )

        assertEquals(
            "TON_CONNECT_SCOPED_V1_1_" +
                "7ddc74a48256ebd1526f57a948b253de3e745cb2526e64ff409b768a65eb1ba8",
            key
        )
        assertTrue(TonConnectStorageKeys.isScopedKey(key))
        assertFalse(key.contains("https://example.com"))
    }

    @Test
    fun everyPrimaryKeyFieldChangesScopedKey() {
        val base = scoped(1L, "https://example.com/a", ConnectionSource.QR)
        val variants = listOf(
            scoped(2L, "https://example.com/a", ConnectionSource.QR),
            scoped(1L, "https://example.com/b", ConnectionSource.QR),
            scoped(1L, "https://example.com/a", ConnectionSource.WEB)
        )

        variants.forEach { variant ->
            assertNotEquals(base, variant)
        }
    }

    @Test
    fun lengthPrefixesPreventDelimiterStyleFieldAmbiguity() {
        assertNotEquals(
            scoped(12L, "https://example.com/QR:WEB", ConnectionSource.QR),
            scoped(1L, "2:https://example.com/QR", ConnectionSource.WEB)
        )
    }

    @Test
    fun invalidIdentityAndClientIdsAreRejectedBeforeHashing() {
        assertThrows(IllegalArgumentException::class.java) {
            scoped(0L, "https://example.com", ConnectionSource.QR)
        }
        assertThrows(IllegalArgumentException::class.java) {
            scoped(1L, "x".repeat(4_097), ConnectionSource.QR)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonConnectStorageKeys.legacy("g".repeat(32))
        }
        assertThrows(IllegalArgumentException::class.java) {
            TonConnectStorageKeys.legacy("a".repeat(1_000_000))
        }
    }

    private fun scoped(
        metaId: Long,
        url: String,
        source: ConnectionSource
    ): String {
        return TonConnectStorageKeys.scoped(metaId, url, source.name)
    }
}
