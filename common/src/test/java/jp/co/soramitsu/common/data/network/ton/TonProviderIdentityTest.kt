package jp.co.soramitsu.common.data.network.ton

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TonProviderIdentityTest {
    private val valid = TiServiceInfo(1, "ti.soramitsu.io", "ton", "ton:mainnet", "mainnet", "https://ti.soramitsu.io", true, TI_REQUIRED_CAPABILITIES)

    @Test
    fun shouldAcceptOnlyCompleteIdentityAndRequiredCapabilities() {
        assertNull(valid.problemFor(TonNetwork.Mainnet, " https://ti.soramitsu.io/ "))
        assertNull(valid.copy(readOnly = false).problemFor(TonNetwork.Mainnet, "https://ti.soramitsu.io"))
        val failures = listOf(valid.copy(schemaVersion = 2), valid.copy(serviceId = "other"), valid.copy(ecosystem = "evm"),
            valid.copy(chainId = "ton:testnet"), valid.copy(network = "testnet"), valid.copy(publicBaseUrl = null),
            valid.copy(readOnly = null), valid.copy(capabilities = listOf("account-balances")))
        failures.forEach { assertNotNull(it.problemFor(TonNetwork.Mainnet, "https://ti.soramitsu.io")) }
        assertTrue(valid.copy(capabilities = null).problemFor(TonNetwork.Mainnet, "https://ti.soramitsu.io")!!.contains("run-get-method"))
    }

    @Test
    fun shouldRequireRequestedNetworkAndPreserveMissingWireFields() {
        val testnet = valid.copy(chainId = TonNetwork.Testnet.chainId, network = TonNetwork.Testnet.id)
        assertNull(testnet.problemFor(TonNetwork.Testnet, "https://ti.soramitsu.io"))
        assertNotNull(testnet.problemFor(TonNetwork.Mainnet, "https://ti.soramitsu.io"))
        val gson = Gson()
        assertEquals(valid, gson.fromJson(gson.toJson(valid), TiServiceInfo::class.java))
        assertEquals(TiServiceInfo(), gson.fromJson("{}", TiServiceInfo::class.java))
        val head = TonMasterchainHead(TonNetwork.Mainnet.globalId, 1234)
        assertEquals(-239, head.globalId)
        assertEquals(-3, TonNetwork.Testnet.globalId)
        assertEquals(head, gson.fromJson(gson.toJson(head), TonMasterchainHead::class.java))
        assertEquals(TonMasterchainHead(), gson.fromJson("{}", TonMasterchainHead::class.java))
    }

    @Test
    fun shouldExplainNetworkMismatchesWithoutInventingAnAbsentNetwork() {
        val missing = TonNetworkMismatchException(TonNetwork.Mainnet, null, "node")
        assertEquals(TonNetwork.Mainnet, missing.requested)
        assertNull(missing.served)
        assertTrue(missing.message!!.contains("without a network"))
        val mismatch = TonNetworkMismatchException(TonNetwork.Mainnet, "testnet", "indexer")
        assertTrue(mismatch.message!!.contains("testnet"))
        assertTrue(mismatch.message!!.contains("indexer"))
        assertEquals("offline", TonProviderUnavailableException("offline").message)
    }
}
