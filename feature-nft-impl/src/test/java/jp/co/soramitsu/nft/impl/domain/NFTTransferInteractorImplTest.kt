package jp.co.soramitsu.nft.impl.domain

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.common.data.secrets.v3.EthereumSecrets
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumConnectionPool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.web3j.crypto.TransactionDecoder
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

class NFTTransferInteractorImplTest {
    private val requests = mutableListOf<JsonObject>()
    private var callResult = "0x" + "2a".padStart(64, '0')
    private val contract = "0x" + "22".repeat(20)
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val buffer = Buffer()
        chain.request().body!!.writeTo(buffer)
        val request = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        synchronized(requests) { requests += request }
        val result = when (request.get("method").asString) {
            "eth_getTransactionCount" -> "\"0x7\""
            "eth_estimateGas" -> "\"0x5208\""
            "eth_getBlockByNumber" -> "{\"baseFeePerGas\":\"0x3b9aca00\"}"
            "eth_maxPriorityFeePerGas" -> "\"0x77359400\""
            "eth_call" -> "\"$callResult\""
            "eth_sendRawTransaction" -> "\"0xtransaction\""
            else -> error("Unexpected RPC method: ${request.get("method")}")
        }
        Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            .body("{\"jsonrpc\":\"2.0\",\"id\":${request.get("id")},\"result\":$result}".toResponseBody("application/json".toMediaType()))
            .build()
    }.build()
    private val service = HttpService("https://rpc.invalid", client, false)
    private val web3 = Web3j.build(service)
    private val config = mockk<ChainAsset> {
        every { isUtility } returns true
        every { precision } returns 18
    }
    private val chain = mockk<Chain> {
        every { id } returns "1"
        every { assets } returns listOf(config)
        every { ecosystem } returns Ecosystem.Ethereum
        every { externalApi } returns null
    }
    private val connection = mockk<EthereumChainConnection> {
        every { this@mockk.chain } returns this@NFTTransferInteractorImplTest.chain
        every { web3j } returns web3
        every { this@mockk.service } returns this@NFTTransferInteractorImplTest.service
    }
    private val receiver = "0x" + "33".repeat(20)
    private val wallet = mockk<MetaAccount> {
        every { id } returns 7
        every { chainAccounts } returns emptyMap()
        every { ethereumAddress } returns ByteArray(20) { 0x11 }
    }
    private val accounts = mockk<AccountRepository> {
        coEvery { getSelectedMetaAccount() } returns wallet
        coEvery {
            getEthereumSecrets(7)
        } returns EthereumSecrets(ethereumKeypair = Keypair(ByteArray(64), ByteArray(32).apply { this[31] = 1 }))
    }
    private val chains = mockk<ChainsRepository> { coEvery { getChain("1") } returns chain }
    private val pool = mockk<EthereumConnectionPool> { every { getOrNull("1") } returns connection }
    private val token = mockk<NFT> {
        every { chainId } returns "1"
        every { tokenType } returns "ERC721"
        every { tokenId } returns BigInteger.ONE
        every { contractAddress } returns contract
    }
    private val interactor = NFTTransferInteractorImpl(accounts, chains, pool, Dispatchers.Unconfined)

    @After
    fun closeRpcClient() {
        web3.shutdown()
        service.close()
    }

    @Test
    fun balanceDecodesTheSelectedWalletContractResult(): Unit = runBlocking {
        assertEquals(BigInteger.valueOf(42), interactor.balance(token).getOrThrow())
        val transaction = requests.single().getAsJsonArray("params")[0].asJsonObject
        assertTrue(transaction.get("data").asString.endsWith("11".repeat(20)))
        assertEquals(contract, transaction.get("to").asString)
    }

    @Test
    fun transferSignsAndBroadcastsSelectedWalletTransaction(): Unit = runBlocking {
        assertEquals("0xtransaction", interactor.send(token, receiver, true).getOrThrow())
        val raw = requests.last().getAsJsonArray("params")[0].asString
        val decoded = TransactionDecoder.decode(raw)
        assertEquals(contract, decoded.to)
        assertEquals(BigInteger.valueOf(7), decoded.nonce)
        assertTrue(decoded.data.startsWith("23b872dd"))
        assertTrue(decoded.data.endsWith("1".padStart(64, '0')))
    }

    @Test
    fun networkFeeCombinesBaseFeePriorityAndEstimatedGas(): Unit = runBlocking {
        val result = withTimeout(10_000) { interactor.networkFeeFlow(token, receiver, false).first() }
        assertEquals(0, BigDecimal("0.000063").compareTo(result.getOrThrow()))
        assertTrue(requests.map { it.get("method").asString }.contains("eth_getTransactionCount"))
    }

    @Test
    fun missingSecretsReturnsFailureBeforeSigning(): Unit = runBlocking {
        coEvery { accounts.getEthereumSecrets(7) } returns null
        val result = interactor.send(token, receiver, true)
        assertEquals("Public or Private key is not available.", result.exceptionOrNull()?.message)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun missingWalletAddressFailsBalanceTransferAndFee(): Unit = runBlocking {
        every { wallet.ethereumAddress } returns null
        assertEquals("Currently selected account is unavailable now.", interactor.balance(token).exceptionOrNull()?.message)
        assertEquals("Currently selected account is unavailable now.", interactor.send(token, receiver, true).exceptionOrNull()?.message)
        val fee = withTimeout(10_000) { interactor.networkFeeFlow(token, receiver, true).first() }
        assertEquals("Currently selected account is unavailable now.", fee.exceptionOrNull()?.message)
    }

    @Test
    fun missingConnectionFailsWithChainIdentifier() {
        every { pool.getOrNull("1") } returns null
        val error = assertThrows(IllegalStateException::class.java) { runBlocking { interactor.balance(token) } }
        assertEquals("EthereumConnection to chain with id - 1 - could not have been established.", error.message)
    }
}
