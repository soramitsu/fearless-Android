package jp.co.soramitsu.wallet.impl.data.network.blockchain

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.crypto.transaction.type.TransactionType
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.core.methods.response.EthGetTransactionCount

/** Builds wallet EVM transfers against a scripted node; nothing is signed or sent. */
class EthereumTransactionBuilderTest {

    private val web3j = mockk<Web3j>()
    private val service = mockk<Web3jService>()
    private val estimates = CopyOnWriteArrayList<Transaction>()

    // Deliberately distinct, so each fee field shows which node answer it came from.
    private var nodeBaseFee: String? = "0xa"
    private val nodePriorityFee = "0x2"
    private val nodeGasPrice = "0x64"

    private val builder: EthereumTransactionBuilder by lazy {
        val connection = mockk<EthereumChainConnection>()
        every { connection.web3j } returns web3j
        every { connection.service } returns service
        every { web3j.ethGetBlockByNumber(any(), any()) } returns mockk {
            every { send() } answers { EthBlock().apply { result = EthBlock.Block().apply { baseFeePerGas = nodeBaseFee } } }
        }
        every { service.send(any(), MaxPriorityFeePerGas::class.java) } answers { MaxPriorityFeePerGas().apply { result = nodePriorityFee } }
        every { web3j.ethGetTransactionCount(any(), any()) } returns mockk {
            every { send() } returns EthGetTransactionCount().apply { result = "0x9" }
        }
        every { web3j.ethEstimateGas(any()) } answers {
            estimates.add(firstArg())
            mockk { every { send() } returns EthEstimateGas().apply { result = "0x5208" } }
        }
        every { web3j.ethGasPrice() } returns mockk {
            every { send() } returns EthGasPrice().apply { result = nodeGasPrice }
        }
        EthereumTransactionBuilder(connection)
    }

    @Test
    fun `eip1559 transfer tips the node priority fee and caps the fee at base fee plus that tip`() {
        val raw = builder.build(transfer(nativeAsset)).getOrThrow()

        assertEquals(TransactionType.EIP1559, raw.type)
        val transaction = raw.transaction as Transaction1559
        assertEquals(BigInteger.valueOf(2), transaction.maxPriorityFeePerGas)
        assertEquals(BigInteger.valueOf(12), transaction.maxFeePerGas)
        assertEquals(ETHEREUM_CHAIN_ID, transaction.chainId)
        assertEquals(RECIPIENT, raw.to)
        assertEquals(BigInteger.valueOf(1234), raw.value)
        assertEquals(BigInteger.valueOf(9), raw.nonce)
        assertEquals(BigInteger.valueOf(21_000), raw.gasLimit)
    }

    @Test
    fun `legacy transfer keeps the node gas price when the node reports no base fee`() {
        nodeBaseFee = null

        val raw = builder.build(transfer(nativeAsset)).getOrThrow()

        assertEquals(TransactionType.LEGACY, raw.type)
        assertEquals(BigInteger.valueOf(100), raw.gasPrice)
        assertEquals(BigInteger.valueOf(21_000), raw.gasLimit)
        assertEquals(BigInteger.valueOf(1234), raw.value)
    }

    @Test
    fun `token send is estimated and built with the reviewed amount`() {
        val raw = builder.build(transfer(tokenAsset)).getOrThrow()

        assertEquals(tokenTransferData(1234), estimates.single().data)
        assertEquals(TOKEN_CONTRACT, raw.to)
        assertEquals(BigInteger.ZERO, raw.value)
        // web3j keeps raw transaction data without the hex prefix.
        assertEquals(tokenTransferData(1234).removePrefix("0x"), raw.data)
    }

    private fun transfer(asset: Asset) = Transfer(
        sender = SENDER,
        recipient = RECIPIENT,
        amount = BigDecimal("12.34"),
        chainAsset = asset
    )

    private val nativeAsset = evmAsset(id = "ETH", isUtility = true, type = ChainAssetType.Normal)
    private val tokenAsset = evmAsset(id = TOKEN_CONTRACT, isUtility = false, type = ChainAssetType.ERC20)

    private fun evmAsset(id: String, isUtility: Boolean, type: ChainAssetType) = Asset(
        id = id,
        name = id,
        symbol = id,
        iconUrl = "",
        chainId = CHAIN_ID,
        chainName = "Ethereum",
        chainIcon = null,
        isTestNet = false,
        priceId = null,
        precision = 2,
        staking = Asset.StakingType.UNSUPPORTED,
        purchaseProviders = null,
        supportStakingPool = false,
        isUtility = isUtility,
        type = type,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = isUtility
    )

    /** ABI encoding of ERC-20 `transfer(RECIPIENT, amount)`. */
    private fun tokenTransferData(amount: Long) =
        "0xa9059cbb" + RECIPIENT.removePrefix("0x").padStart(64, '0') + amount.toString(16).padStart(64, '0')

    private companion object {
        const val CHAIN_ID = "1"
        const val ETHEREUM_CHAIN_ID = 1L
        const val SENDER = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x3333333333333333333333333333333333333333"
        const val TOKEN_CONTRACT = "0x2222222222222222222222222222222222222222"
    }
}
