package jp.co.soramitsu.nft.impl.domain.usecase.eth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.Keypair
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.models.transfer.NFTCall
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.NFTAccountBalanceAdapter
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.connection.EthereumChainConnection
import kotlinx.coroutines.runBlocking
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
import org.web3j.crypto.transaction.type.Transaction1559
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.core.models.Asset as ChainAsset

class EthereumNFTTransactionsTest {
    private val requests = mutableListOf<JsonObject>()
    private var callResult = "0x" + "2a".padStart(64, '0')
    private val sender = "0x" + "11".repeat(20)
    private val contract = "0x" + "22".repeat(20)
    private val client = OkHttpClient.Builder().addInterceptor { chain ->
        val buffer = Buffer()
        chain.request().body!!.writeTo(buffer)
        val request = JsonParser.parseString(buffer.readUtf8()).asJsonObject
        synchronized(requests) { requests += request }
        val result = when (request.get("method").asString) {
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
    }
    private val connection = mockk<EthereumChainConnection> {
        every { this@mockk.chain } returns this@EthereumNFTTransactionsTest.chain
        every { web3j } returns web3
        every { this@mockk.service } returns this@EthereumNFTTransactionsTest.service
    }
    private val call = NFTCall.Transfer(BigInteger.valueOf(7), sender, "recipient", contract, "0x12345678", emptyList())

    @After
    fun closeRpcClient() {
        web3.shutdown()
        service.close()
    }

    @Test
    fun gasEstimationUsesSenderContractNonceAndCalldata(): Unit = runBlocking {
        assertEquals(BigInteger.valueOf(21_000), connection.estimateEthTransactionGas(call))
        val transaction = requests.single().getAsJsonArray("params")[0].asJsonObject
        assertEquals(sender, transaction.get("from").asString)
        assertEquals(contract, transaction.get("to").asString)
        assertEquals("0x7", transaction.get("nonce").asString)
        assertEquals("0x12345678", transaction.get("data").asString)
    }

    @Test
    fun rawTransactionBindsChainContractNonceAndDynamicFees(): Unit = runBlocking {
        val raw = connection.CreateRawEthTransaction(call)
        assertEquals(BigInteger.valueOf(7), raw.nonce)
        assertEquals(BigInteger.valueOf(21_000), raw.gasLimit)
        assertEquals(contract, raw.to)
        assertEquals(BigInteger.ZERO, raw.value)
        assertEquals("12345678", raw.data.removePrefix("0x"))
        val dynamic = raw.transaction as Transaction1559
        assertEquals(1L, dynamic.chainId)
        assertEquals(BigInteger("2000000000"), dynamic.maxPriorityFeePerGas)
        assertEquals(BigInteger("3000000000"), dynamic.maxFeePerGas)
    }

    @Test
    fun networkFeeUsesGasTimesBaseAndPriorityFeeInAssetUnits(): Unit = runBlocking {
        val fee = connection.estimateEthTransactionNetworkFee(call, BigInteger("1000000000"))
        assertEquals(0, BigDecimal("0.000063").compareTo(fee))
        assertEquals(listOf("eth_estimateGas", "eth_maxPriorityFeePerGas"), requests.map { it.get("method").asString })
    }

    @Test
    fun balanceCallDecodesContractResultAndUsesLatestBlock(): Unit = runBlocking {
        val result = connection.ExecuteEthFunction(balanceCall()) { it!!.value as BigInteger }
        assertEquals(BigInteger.valueOf(42), result)
        val params = requests.single().getAsJsonArray("params")
        assertEquals(contract, params[0].asJsonObject.get("to").asString)
        assertEquals("latest", params[1].asString)
    }

    @Test
    fun undecodableContractResultFailsWithClearDiagnostic() {
        callResult = "0x"
        val failure = assertThrows(IllegalStateException::class.java) {
            runBlocking { connection.ExecuteEthFunction(balanceCall()) { it } }
        }
        assertEquals("Could not transform web3 response due to Result obtained is not decodable.", failure.message)
    }

    @Test
    fun signedBroadcastPreservesTransactionPayload(): Unit = runBlocking {
        val raw = connection.CreateRawEthTransaction(call)
        val privateKey = ByteArray(32).apply { this[31] = 1 }
        val keypair = Keypair(publicKey = ByteArray(64), privateKey = privateKey)
        assertEquals("0xtransaction", connection.SendRawEthTransaction(keypair, raw))
        val payload = requests.last().getAsJsonArray("params")[0].asString
        assertTrue(payload.startsWith("0x02"))
        val decoded = TransactionDecoder.decode(payload)
        assertEquals(contract, decoded.to)
        assertEquals(raw.nonce, decoded.nonce)
        assertEquals(raw.data, decoded.data)
        assertEquals(1, privateKey.last().toInt())
    }

    private fun balanceCall(): NFTCall.AccountBalance = NFTAccountBalanceAdapter(
        sender,
        mockk<NFT> {
            every { tokenType } returns "ERC721"
            every { tokenId } returns BigInteger.ONE
            every { contractAddress } returns contract
        }
    )
}
