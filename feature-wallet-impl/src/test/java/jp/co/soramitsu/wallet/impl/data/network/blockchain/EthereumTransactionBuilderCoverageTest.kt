package jp.co.soramitsu.wallet.impl.data.network.blockchain

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.web3j.crypto.transaction.type.TransactionType
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.core.methods.response.EthGetTransactionCount

class EthereumTransactionBuilderCoverageTest {

    private val web3j = mockk<Web3j>()
    private val service = mockk<Web3jService>()
    private val chain = WalletTestModels.chain(id = CHAIN_ID, name = "Ethereum", ecosystem = Ecosystem.Ethereum)
    private val token = WalletTestModels.asset(id = TOKEN_CONTRACT, chainId = CHAIN_ID, precision = 2, isUtility = false, type = ChainAssetType.ERC20)
        .copy(chainName = "Ethereum")

    @Test
    fun `a connection without a node service or client cannot build transfers`() {
        val noService = connection(service = null, web3j = web3j)
        val noClient = connection(service = service, web3j = null)

        val serviceError = assertThrows(IllegalStateException::class.java) { EthereumTransactionBuilder(noService) }
        val clientError = assertThrows(IllegalStateException::class.java) { EthereumTransactionBuilder(noClient) }

        assertEquals("There is no connection established for chain Ethereum, 1", serviceError.message)
        assertEquals("There is no connection established for chain Ethereum, 1", clientError.message)
    }

    @Test
    fun `legacy token transfer calls the token contract with the gas price of the node`() {
        stubNode(gasPrice = "0x64")

        val raw = EthereumTransactionBuilder(connection(service, web3j)).build(transfer()).getOrThrow()

        assertEquals(TransactionType.LEGACY, raw.type)
        assertEquals(TOKEN_CONTRACT, raw.to)
        assertEquals(BigInteger.ZERO, raw.value)
        assertEquals(BigInteger.valueOf(100), raw.gasPrice)
        assertEquals(BigInteger.valueOf(52_000), raw.gasLimit)
        assertEquals(BigInteger.valueOf(9), raw.nonce)
        assertEquals(
            "a9059cbb" + RECIPIENT.removePrefix("0x").padStart(64, '0') + 1234L.toString(16).padStart(64, '0'),
            raw.data
        )
    }

    @Test
    fun `a node without a gas price fails the transfer build`() {
        stubNode(gasPrice = null)

        val error = assertThrows(GasServiceException::class.java) {
            EthereumTransactionBuilder(connection(service, web3j)).build(transfer())
        }

        assertEquals("Failed to get ethGasPrice on chain Ethereum. Response is null", error.message)
    }

    /** A node that reports no EIP-1559 fees, so transfers are built as legacy transactions. */
    private fun stubNode(gasPrice: String?) {
        every { web3j.ethGetBlockByNumber(any(), any()) } returns request(EthBlock().apply { result = EthBlock.Block() })
        every { service.send(any(), MaxPriorityFeePerGas::class.java) } throws java.io.IOException("not supported")
        every { web3j.ethGetTransactionCount(any(), any()) } returns request(EthGetTransactionCount().apply { result = "0x9" })
        every { web3j.ethEstimateGas(any()) } returns request(EthEstimateGas().apply { result = "0xcb20" })
        every { web3j.ethGasPrice() } returns request(EthGasPrice().apply { result = gasPrice })
    }

    private fun connection(service: Web3jService?, web3j: Web3j?): EthereumChainConnection = mockk {
        every { this@mockk.service } returns service
        every { this@mockk.web3j } returns web3j
        every { this@mockk.chain } returns this@EthereumTransactionBuilderCoverageTest.chain
    }

    private fun transfer() = Transfer(
        sender = SENDER,
        recipient = RECIPIENT,
        amount = BigDecimal("12.34"),
        chainAsset = token
    )

    private inline fun <reified T : Response<*>> request(response: T): Request<*, T> = mockk {
        every { send() } returns response
    }

    private companion object {
        const val CHAIN_ID = "1"
        const val SENDER = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x3333333333333333333333333333333333333333"
        const val TOKEN_CONTRACT = "0x2222222222222222222222222222222222222222"
    }
}
