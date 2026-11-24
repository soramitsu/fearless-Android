package jp.co.soramitsu.wallet.impl.data.network.blockchain

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.reactivex.Flowable
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import jp.co.soramitsu.wallet.impl.WalletTestModels
import jp.co.soramitsu.wallet.impl.data.network.model.response.NewHeadsNotificationExtended
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.crypto.SignedRawTransaction
import org.web3j.crypto.TransactionDecoder
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.protocol.ObjectMapperFactory
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.protocol.core.methods.request.Transaction
import org.web3j.protocol.core.methods.response.EthBlock
import org.web3j.protocol.core.methods.response.EthCall
import org.web3j.protocol.core.methods.response.EthEstimateGas
import org.web3j.protocol.core.methods.response.EthGasPrice
import org.web3j.protocol.core.methods.response.EthGetBalance
import org.web3j.protocol.core.methods.response.EthGetTransactionCount
import org.web3j.protocol.core.methods.response.EthSendTransaction
import org.web3j.protocol.http.HttpService
import org.web3j.protocol.websocket.WebSocketService

/** Balance, price feed, signing and submission against a scripted EVM node; nothing leaves the test. */
class EthereumRemoteSourceCoverageTest {

    // Public web3 documentation key; it holds nothing on any network.
    private val credentials = Credentials.create(PRIVATE_KEY)
    private val web3j = mockk<Web3j>()
    private val service = mockk<HttpService>()
    private val connection = mockk<EthereumChainConnection>()
    private val pool = mockk<EthereumConnectionPool>()
    private val source = EthereumRemoteSource(pool)

    init {
        every { connection.web3j } returns web3j
        every { connection.service } returns service
        coEvery { pool.await(CHAIN_ID) } returns connection
    }

    @Test
    fun `native total balance is the account balance of the latest block`(): Unit = runBlocking {
        every { web3j.ethGetBalance(SENDER, DefaultBlockParameterName.LATEST) } returns request(EthGetBalance().apply { result = "0x2a" })

        val balance = source.getTotalBalance(nativeAsset, chain, ByteArray(20) { 0x11 })

        assertEquals(BigInteger.valueOf(42), balance.getOrThrow())
    }

    @Test
    fun `token total balance is the balanceOf call of the token contract`(): Unit = runBlocking {
        val call = slot<Transaction>()
        every { web3j.ethCall(capture(call), DefaultBlockParameterName.LATEST) } returns request(EthCall().apply { result = "0x64" })

        val balance = source.getTotalBalance(tokenAsset, chain, ByteArray(20) { 0x11 })

        assertEquals(BigInteger.valueOf(100), balance.getOrThrow())
        assertEquals(TOKEN_CONTRACT, call.captured.to)
        assertEquals("0x70a08231" + SENDER.removePrefix("0x").padStart(64, '0'), call.captured.data)
    }

    @Test
    fun `price feed answer is the second value of latestRoundData`(): Unit = runBlocking {
        val call = slot<Transaction>()
        val roundData = listOf(7L, 612_000_000L, 1_700_000_000L, 1_700_000_100L, 7L).joinToString("") { "%064x".format(it) }
        every { web3j.ethCall(capture(call), DefaultBlockParameterName.LATEST) } returns request(EthCall().apply { result = "0x$roundData" })

        val price = source.fetchPriceFeed(CHAIN_ID, FEED)

        assertEquals(BigInteger.valueOf(612_000_000), price)
        assertEquals(FEED, call.captured.to)
        assertEquals("0xfeaf968c", call.captured.data)
    }

    @Test
    fun `price feed needs a connected client`(): Unit = runBlocking {
        val offline = mockk<EthereumChainConnection>()
        every { offline.web3j } returns null
        coEvery { pool.await("56") } returns offline

        val failure = runCatching { source.fetchPriceFeed("56", FEED) }.exceptionOrNull()

        assertEquals("There is no connection created for chain 56", failure?.message)
    }

    @Test
    fun `a complete raw transaction is signed for its chain without asking the node`(): Unit = runBlocking {
        val raw = RawTransaction.createEtherTransaction(
            BigInteger.valueOf(5),
            BigInteger.valueOf(20),
            BigInteger.valueOf(21_000),
            RECIPIENT,
            BigInteger.TEN
        )

        val signed = decode(source.signRawTransaction(CHAIN_ID, raw, PRIVATE_KEY).getOrThrow())

        assertEquals(BigInteger.valueOf(5), signed.nonce)
        assertEquals(BigInteger.valueOf(20), signed.gasPrice)
        assertEquals(BigInteger.valueOf(21_000), signed.gasLimit)
        assertEquals(RECIPIENT, signed.to)
        assertEquals(BigInteger.TEN, signed.value)
        assertEquals(1L, signed.chainId)
        assertEquals(credentials.address, signed.from)
        verify(exactly = 0) { web3j.ethGetTransactionCount(any(), any()) }
    }

    @Test
    fun `missing nonce, gas price and gas limit are read from the node before signing`(): Unit = runBlocking {
        stubNode()
        val estimate = slot<Transaction>()
        every { web3j.ethEstimateGas(capture(estimate)) } returns request(EthEstimateGas().apply { result = "0xc350" })
        val raw = RawTransaction.createTransaction(null, null, null, TOKEN_CONTRACT, BigInteger.ZERO, "0xa9059cbb")

        val signed = decode(source.signRawTransaction(CHAIN_ID, raw, PRIVATE_KEY).getOrThrow())

        assertEquals(BigInteger.valueOf(9), signed.nonce)
        assertEquals(BigInteger.valueOf(12), signed.gasPrice)
        assertEquals(BigInteger.valueOf(50_000), signed.gasLimit)
        assertEquals(TOKEN_CONTRACT, signed.to)
        assertEquals(credentials.address, estimate.captured.from)
        assertEquals(TOKEN_CONTRACT, estimate.captured.to)
    }

    @Test
    fun `signing fails without a node service or when the node cannot give a nonce`(): Unit = runBlocking {
        val noService = mockk<EthereumChainConnection>()
        every { noService.web3j } returns web3j
        every { noService.service } returns null
        coEvery { pool.await("56") } returns noService
        every { web3j.ethGetTransactionCount(any(), any()) } returns request(
            EthGetTransactionCount().apply { error = Response.Error(-32_000, "busy") }
        )
        val raw = RawTransaction.createTransaction(null, BigInteger.ONE, BigInteger.ONE, RECIPIENT, BigInteger.ONE, "")

        val missingService = source.signRawTransaction("56", raw, PRIVATE_KEY).exceptionOrNull()
        val missingNonce = source.signRawTransaction(CHAIN_ID, raw, PRIVATE_KEY).exceptionOrNull()

        assertEquals("There is no connection established for chain with id = 56", missingService?.message)
        assertTrue(missingNonce?.message.orEmpty().startsWith("Error ethGetTransactionCount for chain with id = 1"))
    }

    @Test
    fun `a signed raw transaction is sent and node rejections are reported`(): Unit = runBlocking {
        val raw = RawTransaction.createEtherTransaction(BigInteger.ONE, BigInteger.ONE, BigInteger.valueOf(21_000), RECIPIENT, BigInteger.ONE)
        val sent = slot<String>()
        every { web3j.ethSendRawTransaction(capture(sent)) } returns request(EthSendTransaction().apply { result = "0xhash" })

        assertEquals("0xhash", source.sendRawTransaction(CHAIN_ID, raw, PRIVATE_KEY).getOrThrow())
        assertEquals(credentials.address, decode(sent.captured).from)

        every { web3j.ethSendRawTransaction(any()) } returns request(
            EthSendTransaction().apply { error = Response.Error(-32_000, "nonce too low") }
        )
        val rejection = source.sendRawTransaction(CHAIN_ID, raw, PRIVATE_KEY).exceptionOrNull()

        assertEquals("Error ethSendRawTransaction for chain with id = 1, error: nonce too low", rejection?.message)
    }

    @Test
    fun `a wallet transfer is built, signed for its chain and sent`(): Unit = runBlocking {
        stubNode()
        every { web3j.ethEstimateGas(any()) } returns request(EthEstimateGas().apply { result = "0x5208" })
        every { web3j.ethGasPrice() } returns request(EthGasPrice().apply { result = "0x64" })
        val sent = slot<String>()
        every { web3j.ethSendRawTransaction(capture(sent)) } returns request(EthSendTransaction().apply { result = "0xsent" })
        val transfer = Transfer(credentials.address, RECIPIENT, BigDecimal("12.34"), nativeAsset)

        val hash = source.performTransfer(chain, transfer, PRIVATE_KEY).getOrThrow()

        assertEquals("0xsent", hash)
        val signed = decode(sent.captured)
        val payload = signed.transaction as Transaction1559
        assertEquals(1L, payload.chainId)
        assertEquals(BigInteger.valueOf(12), payload.maxFeePerGas)
        assertEquals(BigInteger.valueOf(1234), signed.value)
        assertEquals(RECIPIENT, signed.to)
    }

    @Test
    fun `a wallet transfer the node cannot build is not signed`(): Unit = runBlocking {
        stubNode()
        every { web3j.ethGetTransactionCount(any(), any()) } returns request(
            EthGetTransactionCount().apply { error = Response.Error(-32_000, "busy") }
        )
        val transfer = Transfer(credentials.address, RECIPIENT, BigDecimal("12.34"), nativeAsset)

        val failure = source.performTransfer(chain, transfer, PRIVATE_KEY).exceptionOrNull()

        assertTrue(failure?.message.orEmpty().startsWith("Error ethGetTransactionCount for chain Ethereum, 1"))
        verify(exactly = 0) { web3j.ethSendRawTransaction(any()) }
    }

    @Test
    fun `websocket base fee starts with the latest block and follows new heads`(): Unit = runBlocking {
        val socket = mockk<WebSocketService>()
        val wsConnection = mockk<EthereumChainConnection>()
        every { wsConnection.service } returns socket
        every { wsConnection.web3j } returns web3j
        every { web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false) } returns request(block("0xa"))
        val head = ObjectMapperFactory.getObjectMapper().readValue(
            """{"jsonrpc":"2.0","method":"eth_subscription","params":{"result":{"number":"0x1","baseFeePerGas":"0x3b9aca00"}}}""",
            NewHeadsNotificationExtended::class.java
        )
        every { socket.subscribe(any(), "eth_unsubscribe", NewHeadsNotificationExtended::class.java) } returns Flowable.just(head)

        val fees = wsConnection.subscribeBaseFeePerGas().toList()

        assertEquals(listOf(BigInteger.TEN, BigInteger.valueOf(1_000_000_000)), fees)
    }

    @Test
    fun `http base fee is polled from the pending block`() = runTest {
        val httpConnection = mockk<EthereumChainConnection>()
        every { httpConnection.service } returns service
        every { httpConnection.web3j } returns web3j
        every { web3j.ethGetBlockByNumber(DefaultBlockParameterName.PENDING, false) } returns request(block("0x7"))

        val fees = httpConnection.subscribeBaseFeePerGas().take(2).toList()

        assertEquals(listOf(BigInteger.valueOf(7), BigInteger.valueOf(7)), fees)
    }

    private fun stubNode() {
        every { web3j.ethGetBlockByNumber(DefaultBlockParameterName.LATEST, false) } returns request(block("0xa"))
        every { service.send(any(), MaxPriorityFeePerGas::class.java) } answers { MaxPriorityFeePerGas().apply { result = "0x2" } }
        every { web3j.ethGetTransactionCount(credentials.address, DefaultBlockParameterName.PENDING) } returns request(
            EthGetTransactionCount().apply { result = "0x9" }
        )
    }

    private fun block(baseFee: String) = EthBlock().apply { result = EthBlock.Block().apply { baseFeePerGas = baseFee } }

    private inline fun <reified T : Response<*>> request(response: T): Request<*, T> = mockk {
        every { send() } returns response
    }

    private fun decode(hex: String) = TransactionDecoder.decode(hex) as SignedRawTransaction

    private val chain: Chain = WalletTestModels.chain(id = CHAIN_ID, name = "Ethereum", ecosystem = Ecosystem.Ethereum)

    private val nativeAsset: Asset = evmAsset(id = "ETH", isUtility = true, type = ChainAssetType.Normal)

    private val tokenAsset: Asset = evmAsset(id = TOKEN_CONTRACT, isUtility = false, type = ChainAssetType.ERC20)

    private fun evmAsset(id: String, isUtility: Boolean, type: ChainAssetType) =
        WalletTestModels.asset(id = id, chainId = CHAIN_ID, precision = 2, isUtility = isUtility, type = type).copy(chainName = "Ethereum")

    private companion object {
        const val CHAIN_ID = "1"
        const val PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318"
        const val SENDER = "0x1111111111111111111111111111111111111111"
        const val RECIPIENT = "0x3333333333333333333333333333333333333333"
        const val TOKEN_CONTRACT = "0x2222222222222222222222222222222222222222"
        const val FEED = "0x5f4ec3df9cbd43714fe2740f5e3616155c5b8419"
    }
}
