package jp.co.soramitsu.wallet.impl.data.network.blockchain

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.CopyOnWriteArrayList
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.http.HttpService

/** The fee shown for a wallet EVM transfer, read from a scripted node over HTTP; nothing is sent. */
class EthereumRemoteSourceTest {

    private val web3j = mockk<Web3j>()
    private val service = mockk<HttpService>()
    private val estimatedAmounts = CopyOnWriteArrayList<BigInteger>()

    /** Gas the node reports for a token transfer of the given amount; null when the node refuses it. */
    private var gasForAmount: (BigInteger) -> Long? = { TOKEN_TRANSFER_GAS }

    private val source: EthereumRemoteSource by lazy {
        val connection = mockk<EthereumChainConnection>()
        every { connection.web3j } returns web3j
        every { connection.service } returns service
        every { web3j.ethGetBlockByNumber(any(), any()) } returns mockk {
            every { send() } answers { EthBlock().apply { result = EthBlock.Block().apply { baseFeePerGas = "0xa" } } }
        }
        every { service.send(any(), MaxPriorityFeePerGas::class.java) } answers { MaxPriorityFeePerGas().apply { result = "0x2" } }
        every { web3j.ethEstimateGas(any()) } answers {
            val amount = BigInteger(firstArg<Transaction>().data.takeLast(UINT256_HEX_LENGTH), HEX_RADIX)
            estimatedAmounts.add(amount)
            val gas = gasForAmount(amount)
            mockk {
                every { send() } returns EthEstimateGas().apply {
                    if (gas == null) {
                        error = Response.Error(EXECUTION_ERROR_CODE, "execution reverted: transfer amount exceeds balance")
                    } else {
                        result = "0x" + gas.toString(HEX_RADIX)
                    }
                }
            }
        }
        val pool = mockk<EthereumConnectionPool>()
        every { pool.getOrNull(CHAIN_ID) } returns connection
        EthereumRemoteSource(pool)
    }

    @Test
    fun `shown token fee is the gas of the reviewed amount`() = runBlocking<Unit> {
        // Moving a real amount writes both balances, so it costs more gas than a zero-amount call.
        gasForAmount = { amount -> if (amount.signum() == 0) ZERO_AMOUNT_GAS else TOKEN_TRANSFER_GAS }

        val fee = source.listenGas(tokenTransfer(), chain).first()

        assertEquals(listOf(BigInteger.valueOf(1234)), estimatedAmounts)
        assertEquals(BigInteger.valueOf(TOKEN_TRANSFER_GAS * FEE_PER_GAS), fee)
    }

    @Test
    fun `shown fee falls back to the amount-free estimate while the node refuses the entered amount`() = runBlocking<Unit> {
        gasForAmount = { amount -> if (amount.signum() == 0) ZERO_AMOUNT_GAS else null }

        val fee = source.listenGas(tokenTransfer(), chain).first()

        assertEquals(BigInteger.valueOf(ZERO_AMOUNT_GAS * FEE_PER_GAS), fee)
    }

    private val tokenAsset = Asset(
        id = TOKEN_CONTRACT,
        name = "Token",
        symbol = "TKN",
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
        isUtility = false,
        type = ChainAssetType.ERC20,
        currencyId = null,
        existentialDeposit = null,
        color = null,
        isNative = false
    )

    private val chain = Chain(
        id = CHAIN_ID,
        paraId = null,
        rank = null,
        name = "Ethereum",
        minSupportedVersion = null,
        assets = listOf(tokenAsset),
        nodes = emptyList(),
        explorers = emptyList(),
        externalApi = Chain.ExternalApi(staking = null, history = null, crowdloans = null),
        icon = "",
        addressPrefix = 0,
        isEthereumBased = true,
        isTestNet = false,
        hasCrowdloans = false,
        parentId = null,
        supportStakingPool = false,
        isEthereumChain = true,
        chainlinkProvider = false,
        supportNft = false,
        isUsesAppId = false,
        identityChain = null,
        ecosystem = Ecosystem.Ethereum,
        remoteAssetsSource = null
    )

    private fun tokenTransfer() = Transfer(
        sender = "0x1111111111111111111111111111111111111111",
        recipient = "0x3333333333333333333333333333333333333333",
        amount = BigDecimal("12.34"),
        chainAsset = tokenAsset
    )

    private companion object {
        const val CHAIN_ID = "1"
        const val TOKEN_CONTRACT = "0x2222222222222222222222222222222222222222"
        const val ZERO_AMOUNT_GAS = 30_000L
        const val TOKEN_TRANSFER_GAS = 52_000L

        // Base fee 0xa plus priority fee 0x2.
        const val FEE_PER_GAS = 12L
        const val UINT256_HEX_LENGTH = 64
        const val HEX_RADIX = 16
        const val EXECUTION_ERROR_CODE = -32_000
    }
}
