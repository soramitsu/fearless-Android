package jp.co.soramitsu.account.impl.domain

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Ethereum
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthGetBalance

class BalancesUtilsTest {

    private val address = "0x" + "ab".repeat(20)
    private val token = "0x" + "cd".repeat(20)

    private fun asset(id: String, utility: Boolean, name: String = "Ether") = Asset(
        id = id, name = name, symbol = "ETH", iconUrl = "", chainId = "1", chainName = "Ethereum", chainIcon = null,
        isTestNet = false, priceId = null, precision = 18, staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null, supportStakingPool = false, isUtility = utility, type = null, currencyId = null,
        existentialDeposit = null, color = null, isNative = utility
    )

    @Test
    fun storageKeysMatchOnAssetWalletAndAccountBytesButNotOnTheKeyText() {
        val ether = asset("eth", utility = true)
        val first = StorageKeyWithMetadata(ether, 1L, byteArrayOf(1, 2, 3), "0xaa")
        val sameBytes = StorageKeyWithMetadata(ether, 1L, byteArrayOf(1, 2, 3), null)

        assertEquals(first, sameBytes)
        assertEquals(first.hashCode(), sameBytes.hashCode())
        assertEquals(first, first)
        assertNotEquals(first, StorageKeyWithMetadata(ether, 2L, byteArrayOf(1, 2, 3), "0xaa"))
        assertNotEquals(first, StorageKeyWithMetadata(ether, 1L, byteArrayOf(9), "0xaa"))
        assertNotEquals(first, StorageKeyWithMetadata(asset("usdt", utility = false), 1L, byteArrayOf(1, 2, 3), "0xaa"))
        assertNotEquals(first, "0xaa")
        assertNotEquals(first, null)
        assertEquals("StorageKeyWithMetadata(asset=Ether, metaAccountId=1, key='0xaa')", first.toString())
    }

    @Test
    fun nativeBalanceIsReadAtTheLatestBlock(): Unit = runBlocking {
        val ethereum = mockk<Ethereum>()
        val request = mockk<Request<*, EthGetBalance>> {
            every { send() } returns EthGetBalance().apply { result = "0x2a" }
        }
        every { ethereum.ethGetBalance(address, DefaultBlockParameterName.LATEST) } returns request

        assertEquals(BigInteger.valueOf(42), ethereum.fetchEthBalance(asset("eth", utility = true), address))
    }

    @Test
    fun tokenBalanceCallsBalanceOfOnTheTokenContract(): Unit = runBlocking {
        val ethereum = mockk<Ethereum>()
        val transaction = slot<Transaction>()
        val request = mockk<Request<*, EthCall>> {
            every { send() } returns EthCall().apply { result = "0x0de0b6b3a7640000" }
        }
        every { ethereum.ethCall(capture(transaction), DefaultBlockParameterName.LATEST) } returns request

        val balance = ethereum.fetchEthBalance(asset(token, utility = false, name = "Tether"), address)

        assertEquals(BigInteger.TEN.pow(18), balance)
        assertEquals(token, transaction.captured.to)
        assertTrue(transaction.captured.data.startsWith("0x70a08231"))
        assertTrue(transaction.captured.data.endsWith("ab".repeat(20)))
        verify(exactly = 0) { ethereum.ethGetBalance(any(), any()) }
    }
}
