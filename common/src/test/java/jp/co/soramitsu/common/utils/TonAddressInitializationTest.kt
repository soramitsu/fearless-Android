package jp.co.soramitsu.common.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TonAddressInitializationTest {
    @Test(timeout = 10000)
    fun `startup initialization allows concurrent TON address reads with unchanged identity`() {
        initializeTonAddressCode()
        val publicKey = ByteArray(32)
        // Public zero-key V4R2 fixture, independently checked with @ton/ton WalletContractV4.
        val expected = "UQDwxMHGmoIn936giNrdit8UYQyN42F3lBHFxDxFqic35mIP"
        val addresses = runBlocking {
            (1..32).map {
                async(Dispatchers.Default) { publicKey.v4r2tonAddress(isTestnet = false) }
            }.awaitAll()
        }
        assertEquals(List(32) { expected }, addresses)
        assertEquals(
            publicKey.tonAccountId(isTestnet = false),
            org.ton.block.AddrStd(expected).toString(userFriendly = false, testOnly = false).lowercase()
        )
    }
}
