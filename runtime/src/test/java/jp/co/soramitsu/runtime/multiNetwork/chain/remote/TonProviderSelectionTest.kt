package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.io.File
import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import jp.co.soramitsu.common.data.network.coingecko.CoingeckoApi
import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.network.ton.TonNetworkMismatchException
import jp.co.soramitsu.common.data.network.ton.TonProviderUnavailableException
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.toChain
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory

/**
 * AL-11. TonRemoteSource against a fake TON network at the HTTP layer, through the same Retrofit, Gson and scalars
 * stack the app builds in NetworkModule.provideTonApi. TI answers as deployed on 2026-10-11 (testnet, no
 * service-info; captured in ton/ti-deployed-20261011.json), as mainnet (the service-info TI's source returns), with
 * 404s, or with garbage. Chains come from the TON entries of the registry 4.2.0 shipped, and from this build's own
 * bundled registry.
 */
class TonProviderSelectionTest {

    private val tonNetwork = FakeTonNetwork()
    private val logs = Collections.synchronizedList(mutableListOf<String>())
    private var clock = 1_800_000_000_000L

    private val captured: JsonObject = JsonParser.parseString(resource("/ton/ti-deployed-20261011.json")).asJsonObject
    private val deployed: JsonObject = captured.getAsJsonObject("deployed")
    private val address: String = captured["address"].asString
    private val shippedMainnet: Chain = shippedChain("-239")
    private val shippedTestnet: Chain = shippedChain("-3")

    private fun source(registryNodeFallback: Boolean = true, tonIndexerUrl: String = TI) = TonRemoteSource(
        tonApi = productionTonApi(tonNetwork),
        availableFiatCurrencies = GetAvailableFiatCurrencies(EmptyCoingeckoApi()),
        gson = Gson(),
        tonIndexerUrl = tonIndexerUrl,
        registryNodeFallback = registryNodeFallback,
        now = { clock },
        log = { logs += it }
    )

    @Test
    fun `TI as deployed today never shows its testnet balance for mainnet and the verified 4_2_0 node answers`() = runBlocking {
        tiAsDeployedToday()
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 5_000_000_000))

        val account = source().loadAccountData(shippedMainnet, address)

        assertEquals(5_000_000_000L, account.balance)
        assertNotEquals(deployed.body("balances")["ton_raw"].asString.toLong(), account.balance)
        // TI could not prove mainnet, so none of its account routes were even asked.
        assertEquals(listOf("$TI/api/indexer/v1/service-info"), tonNetwork.urlsAt(TI))
        assertTrue(logs.any { "using the registry node $KEEPER instead of TI" in it && "404" in it })
    }

    @Test
    fun `the bundled registry of this build lists only TI, so the registry node is TonAPI's public host`() = runBlocking {
        val bundled = bundledChain("-239")
        assertEquals(listOf(TI), bundled.nodes.map { it.url })
        tiAsDeployedToday()
        registryNodeServes(TON_API, globalId = -239)
        tonNetwork.get("$TON_API/v2/accounts/$address", body = tonApiAccount(balance = 42))

        assertEquals(42L, source().loadAccountData(bundled, address).balance)
        // TI is never addressed as a TonAPI server.
        assertTrue(tonNetwork.urlsAt(TI).none { "/v2/" in it })
    }

    @Test
    fun `TI that proves mainnet answers and TonAPI is not asked`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances").withNetwork("mainnet"))
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state").withNetwork("mainnet"))

        val account = source().loadAccountData(shippedMainnet, address)

        assertEquals(218_928_460_686L, account.balance)
        assertEquals(emptyList<String>(), tonNetwork.urlsAt(KEEPER) + tonNetwork.urlsAt(TON_API))
    }

    @Test
    fun `TI whose service-info says mainnet but whose balances say testnet is rejected and passed over`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances"))
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 7))
        val source = source()

        assertEquals(7L, source.loadAccountData(shippedMainnet, address).balance)
        assertTrue(logs.any { "ton_provider_network_mismatch" in it && "testnet" in it })

        // Passed over for a while: the next refresh does not ask TI at all.
        val tiCalls = tonNetwork.urlsAt(TI).size
        clock += TonProviderSelector.INDEXER_COOLDOWN_MS - 1
        assertEquals(7L, source.loadAccountData(shippedMainnet, address).balance)
        assertEquals(tiCalls, tonNetwork.urlsAt(TI).size)

        // Then TI is checked again from its service-info.
        clock += 2
        source.loadAccountData(shippedMainnet, address)
        assertEquals("$TI/api/indexer/v1/service-info", tonNetwork.urlsAt(TI)[tiCalls])
    }

    @Test
    fun `with the fallback off, a TI answer for another network fails and shows nothing`() {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances"))

        val error = assertThrows(TonNetworkMismatchException::class.java) {
            runBlocking { source(registryNodeFallback = false).loadAccountData(shippedMainnet, address) }
        }

        assertEquals("testnet", error.served)
        assertEquals(emptyList<String>(), tonNetwork.urlsAt(KEEPER) + tonNetwork.urlsAt(TON_API))
    }

    @Test
    fun `TI answers without a network are rejected for balances, state and history`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances").withoutNetwork())
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/txs", body = deployed.body("txsPage1").withoutNetwork())
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address/jettons", body = """{"balances":[]}""")
        tonNetwork.get("$KEEPER/v2/accounts/$address/events", body = """{"events":[]}""")

        val jettons = source().loadJettonBalances(shippedMainnet, address)
        val history = source().getAccountEvents(shippedMainnet, KEEPER, address, beforeLt = null)

        assertFalse(jettons.partial)
        assertEquals(0, history.events.size)
        assertTrue(tonNetwork.urlsAt(KEEPER).any { it.endsWith("/v2/accounts/$address/events") })
    }

    @Test
    fun `TI history that says testnet is never shown for mainnet`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/txs", body = deployed.body("txsPage1"))
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address/events", body = """{"events":[]}""")

        val history = source().getAccountEvents(shippedMainnet, KEEPER, address, beforeLt = null)

        assertEquals(0, history.events.size)
    }

    @Test
    fun `TI that answers 404 everywhere is not used`() = runBlocking {
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 1))

        assertEquals(1L, source().loadAccountData(shippedMainnet, address).balance)
        assertEquals(listOf("$TI/api/indexer/v1/service-info"), tonNetwork.urlsAt(TI))
    }

    @Test
    fun `TI garbage is never read as a balance`() = runBlocking {
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 3))

        // Not JSON at all.
        tonNetwork.get("$TI/api/indexer/v1/service-info", body = "<html><body>502 Bad Gateway</body></html>")
        assertEquals(3L, source().loadAccountData(shippedMainnet, address).balance)

        // JSON, but not a service-info.
        tonNetwork.get("$TI/api/indexer/v1/service-info", body = """{"status":"ok"}""")
        assertEquals(3L, source().loadAccountData(shippedMainnet, address).balance)

        // A proven TI whose balances carry no amount, or a nonsense one: a failure, never 0.
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state").withNetwork("mainnet"))
        val noAmount = deployed.body("balances").withNetwork("mainnet").apply { remove("ton_raw") }
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = noAmount)
        assertEquals(3L, source().loadAccountData(shippedMainnet, address).balance)
        tonNetwork.get(
            "$TI/api/indexer/v1/accounts/$address/balances",
            body = deployed.body("balances").withNetwork("mainnet").apply { addProperty("ton_raw", "-5") }
        )
        assertEquals(3L, source().loadAccountData(shippedMainnet, address).balance)
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = "{\"ton_raw\":")
        assertEquals(3L, source().loadAccountData(shippedMainnet, address).balance)
    }

    @Test
    fun `a service-info that does not prove the requested network is refused`() = runBlocking {
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 9))
        val proof = mainnetServiceInfo()
        val forgeries = listOf(
            proof.copyWith { addProperty("chainId", "ton:testnet") },
            proof.copyWith { addProperty("network", "testnet") },
            proof.copyWith { addProperty("serviceId", "si.soramitsu.io") },
            proof.copyWith { addProperty("publicBaseUrl", "https://indexer.invalid") },
            proof.copyWith { addProperty("schemaVersion", 2) },
            proof.copyWith { remove("readOnly") },
            proof.copyWith { add("capabilities", JsonParser.parseString("""["account-balance"]""")) }
        )

        forgeries.forEach { forged ->
            tonNetwork.get("$TI/api/indexer/v1/service-info", body = forged.toString())
            tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances").withNetwork("mainnet"))

            assertEquals(forged.toString(), 9L, source().loadAccountData(shippedMainnet, address).balance)
            assertTrue(tonNetwork.urlsAt(TI).none { it.endsWith("/balances") })
        }
    }

    @Test
    fun `a registry node that does not prove mainnet is never used, so nothing is shown`() {
        tiAsDeployedToday()
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 11))

        listOf(
            200 to masterchainHead(globalId = -3),
            200 to """{"seqno":1}""",
            404 to deployed.body("tonApiMasterchainHead").toString(),
            200 to "not json"
        ).forEach { (status, head) ->
            tonNetwork.get("$KEEPER/v2/blockchain/masterchain-head", status = status, body = head)
            clock += TonProviderSelector.VERIFIED_TTL_MS

            assertThrows(head, TonProviderUnavailableException::class.java) {
                runBlocking { source().loadAccountData(shippedMainnet, address) }
            }
        }
        assertTrue(tonNetwork.urlsAt(KEEPER).none { it.endsWith("/v2/accounts/$address") })
    }

    @Test
    fun `with the fallback off and TI unproven, nothing is read from anywhere`() {
        tiAsDeployedToday()
        registryNodeServes(KEEPER, globalId = -239)

        val error = assertThrows(TonProviderUnavailableException::class.java) {
            runBlocking { source(registryNodeFallback = false).loadAccountData(shippedMainnet, address) }
        }

        assertTrue(error.message!!, "fallback is off" in error.message!!)
        assertEquals(emptyList<String>(), tonNetwork.urlsAt(KEEPER) + tonNetwork.urlsAt(TON_API))
        assertEquals(listOf("$TI/api/indexer/v1/service-info"), tonNetwork.urlsAt(TI))
    }

    @Test
    fun `the wallet moves to TI once TI proves mainnet`() = runBlocking {
        tiAsDeployedToday()
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address", body = tonApiAccount(balance = 1))
        val source = source()
        assertEquals(1L, source.loadAccountData(shippedMainnet, address).balance)

        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances").withNetwork("mainnet"))
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state").withNetwork("mainnet"))

        clock += TonProviderSelector.FAILED_TTL_MS - 1
        assertEquals(1L, source.loadAccountData(shippedMainnet, address).balance)
        clock += 2
        assertEquals(218_928_460_686L, source.loadAccountData(shippedMainnet, address).balance)
        assertTrue(logs.any { "using $TI (service-info proves ton:mainnet)" in it })
    }

    @Test
    fun `seqno comes from the wallet contract, never from TI's masterchain seqno`() = runBlocking {
        tiProves(network = "mainnet")
        val state = deployed.body("state").withNetwork("mainnet").apply { addProperty("account_state", "active") }
        assertEquals(90_569_612, state["last_confirmed_seqno"].asInt)
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = state)
        tonNetwork.post("$TI/api/indexer/v1/runGetMethod", body = """{"exit_code":0,"gas_used":512,"stack":[["num","0x7"]]}""")

        assertEquals(7, source().getSeqno(shippedMainnet, address))
        assertTrue(tonNetwork.bodiesTo("$TI/api/indexer/v1/runGetMethod").single().contains("\"method\":\"seqno\""))
    }

    @Test
    fun `an uninitialized wallet on a proven TI has seqno 0`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state").withNetwork("mainnet"))

        assertEquals(0, source().getSeqno(shippedMainnet, address))
    }

    @Test
    fun `a transfer is broadcast only through a verified endpoint`() = runBlocking {
        val request = SendBlockchainMessageRequest(boc = "te6cckEBAQEAAgAAAEysuc0=")

        // TI as deployed: the verified 4.2.0 node broadcasts.
        tiAsDeployedToday()
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.post("$KEEPER/v2/blockchain/message", body = "")
        source().sendBlockchainMessage(shippedMainnet, request)
        val broadcast = JsonParser.parseString(tonNetwork.bodiesTo("$KEEPER/v2/blockchain/message").single()).asJsonObject
        assertEquals(request.boc, broadcast["boc"].asString)

        // TI proves mainnet but is read-only: still the verified node, never TI.
        tiProves(network = "mainnet", readOnly = true)
        source().sendBlockchainMessage(shippedMainnet, request)
        assertEquals(2, tonNetwork.bodiesTo("$KEEPER/v2/blockchain/message").size)
        assertEquals(emptyList<String>(), tonNetwork.bodiesTo("$TI/jsonRPC"))

        // TI proves mainnet and accepts transactions: TI.
        tiProves(network = "mainnet", readOnly = false)
        tonNetwork.post("$TI/jsonRPC", body = """{"ok":true,"result":{"@type":"ok"},"jsonrpc":"2.0","id":1}""")
        source().sendBlockchainMessage(shippedMainnet, request)
        assertTrue(tonNetwork.bodiesTo("$TI/jsonRPC").single().contains("\"sendBoc\""))
        assertEquals(2, tonNetwork.bodiesTo("$KEEPER/v2/blockchain/message").size)
    }

    @Test
    fun `nothing is broadcast when no endpoint proves the network`() {
        val request = SendBlockchainMessageRequest(boc = "te6cckEBAQEAAgAAAEysuc0=")
        tiAsDeployedToday()
        tonNetwork.get("$KEEPER/v2/blockchain/masterchain-head", body = masterchainHead(globalId = -3))
        tonNetwork.post("$KEEPER/v2/blockchain/message", body = "")

        assertThrows(TonProviderUnavailableException::class.java) {
            runBlocking { source().sendBlockchainMessage(shippedMainnet, request) }
        }
        // A read-only TI with the fallback off cannot broadcast either.
        tiProves(network = "mainnet", readOnly = true)
        assertThrows(TonProviderUnavailableException::class.java) {
            runBlocking { source(registryNodeFallback = false).sendBlockchainMessage(shippedMainnet, request) }
        }

        assertTrue(tonNetwork.calls.none { it.method == "POST" })
    }

    @Test
    fun `TON testnet reads TI when TI proves testnet, and only then`() = runBlocking {
        tiAsDeployedToday()
        registryNodeServes(TESTNET_TON_API, globalId = -3)
        tonNetwork.get("$TESTNET_TON_API/v2/accounts/$address", body = tonApiAccount(balance = 5))
        // As deployed (no service-info), testnet also goes to the verified testnet node, never to mainnet TonAPI.
        assertEquals(5L, source().loadAccountData(shippedTestnet, address).balance)
        assertEquals(emptyList<String>(), tonNetwork.urlsAt(TON_API) + tonNetwork.urlsAt(KEEPER))

        // A TI that proves mainnet is not a testnet indexer.
        tiProves(network = "mainnet")
        assertEquals(5L, source().loadAccountData(shippedTestnet, address).balance)

        // A TI that proves testnet serves its testnet answers to the testnet chain.
        tiProves(network = "testnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances"))
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state"))
        assertEquals(218_928_460_686L, source().loadAccountData(shippedTestnet, address).balance)
    }

    @Test
    fun `TI jetton lists are partial, a TonAPI list is complete`() = runBlocking {
        tiProves(network = "mainnet")
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances").withNetwork("mainnet"))
        assertTrue(source().loadJettonBalances(shippedMainnet, address).partial)

        tiAsDeployedToday()
        registryNodeServes(KEEPER, globalId = -239)
        tonNetwork.get("$KEEPER/v2/accounts/$address/jettons", body = """{"balances":[]}""")
        assertFalse(source().loadJettonBalances(shippedMainnet, address).partial)
    }

    @Test
    fun `TI jetton entries that are not fully stated are left out, never guessed`() = runBlocking {
        tiProves(network = "mainnet")
        val usdt = "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs"
        val balances = deployed.body("balances").withNetwork("mainnet")
        val jettons = JsonParser.parseString(
            """[
              {"kind":"jetton","symbol":"USDT","address":"$usdt","wallet":"EQAwallet","balance_raw":"1500000","balance":"1.5","decimals":6},
              {"kind":"jetton","symbol":"NOWALLET","address":"EQBmaster1","balance_raw":"5","balance":"5","decimals":9},
              {"kind":"jetton","symbol":"NODECIMALS","address":"EQBmaster2","wallet":"EQBwallet2","balance_raw":"5","balance":"5"},
              {"kind":"jetton","symbol":"NOAMOUNT","address":"EQBmaster3","wallet":"EQBwallet3","balance":"5","decimals":9}
            ]"""
        ).asJsonArray
        balances.getAsJsonArray("assets").addAll(jettons)
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = balances)

        val list = source().loadJettonBalances(shippedMainnet, address)

        assertTrue(list.partial)
        val held = list.balances.single()
        assertEquals(usdt, held.jetton.address)
        assertEquals("EQAwallet", held.walletAddress.address)
        assertEquals(6, held.jetton.decimals)
        assertEquals("1500000", held.balance)
    }

    private fun tiAsDeployedToday() {
        tonNetwork.get("$TI/api/indexer/v1/service-info", status = 404, body = deployed.body("serviceInfo").toString())
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/balances", body = deployed.body("balances"))
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/state", body = deployed.body("state"))
        tonNetwork.get("$TI/api/indexer/v1/accounts/$address/txs", body = deployed.body("txsPage1"))
    }

    private fun tiProves(network: String, readOnly: Boolean = true) {
        val info = mainnetServiceInfo().copyWith {
            addProperty("network", network)
            addProperty("chainId", "ton:$network")
            addProperty("readOnly", readOnly)
        }
        tonNetwork.get("$TI/api/indexer/v1/service-info", body = info.toString())
    }

    private fun registryNodeServes(baseUrl: String, globalId: Int) {
        tonNetwork.get("$baseUrl/v2/blockchain/masterchain-head", body = masterchainHead(globalId))
    }

    private fun mainnetServiceInfo(): JsonObject = captured.getAsJsonObject("sourceMainnetServiceInfo")

    private fun JsonObject.body(name: String): JsonObject = getAsJsonObject(name).getAsJsonObject("body").deepCopy()

    private fun JsonObject.withNetwork(network: String): JsonObject = deepCopy().apply { addProperty("network", network) }

    private fun JsonObject.withoutNetwork(): JsonObject = deepCopy().apply { remove("network") }

    private fun JsonObject.copyWith(change: JsonObject.() -> Unit): JsonObject = deepCopy().apply(change)

    private fun shippedChain(chainId: String): Chain {
        val chains = JsonParser.parseString(resource("/ton/shipped-4.2.0-ton-chains.json")).asJsonObject["chains"]
        val type = object : TypeToken<List<ChainRemote>>() {}.type
        return Gson().fromJson<List<ChainRemote>>(chains, type).single { it.chainId == chainId }.toChain()
    }

    private fun bundledChain(chainId: String): Chain {
        val file = listOf(File("src/main/assets/local_chains.json"), File("runtime/src/main/assets/local_chains.json"))
            .first(File::exists)
        val type = object : TypeToken<List<ChainRemote>>() {}.type
        return Gson().fromJson<List<ChainRemote>>(file.readText(), type).single { it.chainId == chainId }.toChain()
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResourceAsStream(path)) { "missing $path" }.bufferedReader().use { it.readText() }

    private companion object {
        const val TI = "https://ti.soramitsu.io"
        const val KEEPER = "https://keeper.tonapi.io"
        const val TON_API = "https://tonapi.io"
        const val TESTNET_TON_API = "https://testnet.tonapi.io"

        fun tonApiAccount(balance: Long) =
            """{"address":"0:83dfd552e63729b472fcbcc8c45ebcc6691702558b68ec7527e1ba403a0f31a8","balance":$balance,""" +
                """"last_activity":1791533127,"status":"active","get_methods":["seqno"],"is_wallet":true}"""

        fun masterchainHead(globalId: Int) =
            """{"workchain_id":-1,"shard":"8000000000000000","seqno":51234567,"global_id":$globalId,"version":0,""" +
                """"gen_utime":1791728944,"start_lt":"60800000000000","end_lt":"60800000000004"}"""

        // The app's TonApi exactly as NetworkModule.provideTonApi builds it, on a fake network.
        fun productionTonApi(network: FakeTonNetwork): TonApi = Retrofit.Builder()
            .client(OkHttpClient.Builder().addInterceptor(network).build())
            .baseUrl("https://tonapi.io/")
            .addConverterFactory(ScalarsConverterFactory.create())
            .addConverterFactory(GsonConverterFactory.create(Gson()))
            .build()
            .create(TonApi::class.java)
    }

    /** TI and TonAPI as a test sets them up. Anything not set up answers like TI's router: 404 "Route not found". */
    private class FakeTonNetwork : Interceptor {
        data class Call(val method: String, val url: String, val body: String?)

        val calls: MutableList<Call> = Collections.synchronizedList(mutableListOf())
        private val answers = ConcurrentHashMap<String, Pair<Int, String>>()

        fun get(url: String, status: Int = 200, body: Any) {
            answers["GET $url"] = status to body.toString()
        }

        fun post(url: String, status: Int = 200, body: String) {
            answers["POST $url"] = status to body
        }

        fun urlsAt(baseUrl: String): List<String> = calls.filter { it.url.startsWith("$baseUrl/") }.map { it.url }

        fun bodiesTo(url: String): List<String> = calls.filter { it.url == url }.mapNotNull { it.body }

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            val url = request.url.newBuilder().query(null).build().toString()
            val body = request.body?.let { Buffer().also(it::writeTo).readUtf8() }
            calls += Call(request.method, url, body)

            val (status, answer) = answers["${request.method} $url"] ?: (
                404 to """{"message":"Route ${request.method}:${request.url.encodedPath} not found",""" +
                    """"error":"Not Found","statusCode":404}"""
                )
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(status)
                .message(if (status == 200) "OK" else "Not Found")
                .body(answer.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    private class EmptyCoingeckoApi : CoingeckoApi {
        override suspend fun getAssetPrice(
            priceIds: String,
            currency: String,
            includeRateChange: Boolean
        ): Map<String, Map<String, BigDecimal>> = emptyMap()

        override suspend fun getSingleAssetPrice(priceIds: String, currency: String): Map<String, Map<String, Double>> =
            emptyMap()

        override suspend fun getSupportedCurrencies(): List<String> = emptyList()

        override suspend fun getFiatConfig(): List<FiatCurrency> = emptyList()
    }
}
