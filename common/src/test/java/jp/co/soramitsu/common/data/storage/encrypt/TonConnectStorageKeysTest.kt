package jp.co.soramitsu.common.data.storage.encrypt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TonConnectStorageKeysTest {

    @Test
    fun scopedKeyIsBoundToCanonicalWalletPrefix() {
        val key = TonConnectStorageKeys.scoped(
            metaId = 42,
            url = "https://wallet.example/connect",
            source = "WEB"
        )

        assertTrue(TonConnectStorageKeys.isScopedKey(key))
        assertTrue(TonConnectStorageKeys.isScopedKeyForMeta(key, 42))
        assertFalse(TonConnectStorageKeys.isScopedKeyForMeta(key, 43))
    }

    @Test
    fun malformedUtf16UrlsAreRejectedInsteadOfHashColliding() {
        listOf(
            "https://wallet.example/\uD800",
            "https://wallet.example/\uDC00",
            "https://wallet.example/\uD800x",
            "https://wallet.example/x\uDC00"
        ).forEach { malformedUrl ->
            assertThrows(IllegalArgumentException::class.java) {
                TonConnectStorageKeys.scoped(
                    metaId = 42,
                    url = malformedUrl,
                    source = "WEB"
                )
            }
        }
    }
}
