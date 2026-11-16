package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.math.BigInteger
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.core.runtime.ChainConnection
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.Type
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.primitives.u32
import jp.co.soramitsu.fearless_utils.runtime.metadata.RuntimeMetadata
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageEntryModifier
import jp.co.soramitsu.fearless_utils.runtime.metadata.StorageHasher
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.MetadataFunction
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Module
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.Storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntry
import jp.co.soramitsu.fearless_utils.runtime.metadata.module.StorageEntryType
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.fearless_utils.wsrpc.response.RpcResponse
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuoteRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PolkamarktRuntimeTest {
    private val socket = mockk<SocketService>()
    private val connection = mockk<ChainConnection>()
    private val chainRegistry = mockk<ChainRegistry>()
    private val bulk = mockk<BulkRetriever>()
    private val runtimeSnapshot = mockk<RuntimeSnapshot>()
    private val requests = mutableListOf<RuntimeRequest>()
    private val rpc = mutableMapOf<String, (List<Any>) -> Any?>()
    private val polkamarkt = PolkamarktRuntime(chainRegistry, bulk)

    private val allMethods = listOf("polkamarkt_marketState", "polkamarkt_quoteBuy", "polkamarkt_quote_sell", "polkamarkt_claimable")

    @Before fun setUp() {
        coEvery { chainRegistry.awaitConnection(soraMainChainId) } returns connection
        every { connection.socketService } returns socket
        every { socket.jsonMapper } returns Gson()
        val request = slot<RuntimeRequest>()
        val listener = slot<SocketService.ResponseListener<RpcResponse>>()
        every { socket.executeRequest(capture(request), any(), capture(listener)) } answers {
            val sent = request.captured
            requests += sent
            val result = runCatching { rpc.getValue(sent.method)(sent.params) }
            result.fold(
                { listener.captured.onNext(RpcResponse("2.0", it, sent.id, null)) },
                { listener.captured.onError(it) }
            )
            mockk(relaxed = true)
        }
    }

    private fun runtimeWith(module: Module?) {
        val metadata = RuntimeMetadata(BigInteger.ONE, listOfNotNull(module).associateBy { it.name }, mockk())
        every { runtimeSnapshot.metadata } returns metadata
        coEvery { chainRegistry.getRuntimeOrNull(soraMainChainId) } returns runtimeSnapshot
    }

    private fun entry(name: String, value: Type<*>?) = StorageEntry(
        name, StorageEntryModifier.Optional,
        StorageEntryType.NMap(listOf(u32), listOf(StorageHasher.Twox64Concat), value),
        ByteArray(0), emptyList(), "Polkamarkt"
    )

    private fun module(entries: List<StorageEntry>, calls: List<String> = emptyList()) = Module(
        "Polkamarkt", Storage("Polkamarkt", entries.associateBy { it.name }),
        calls.associateWith { mockk<MetadataFunction>() }, null, emptyMap(), emptyMap(), BigInteger.ZERO
    )

    private fun decoding(vararg values: Any?): Type<Any?> = mockk {
        every { decode(any(), any()) } returnsMany values.toList()
    }

    @Test fun currentBlockNormalizesTheHeaderAndFallsBackToGenesis() = runTest {
        rpc["chain_getHeader"] = { mapOf("number" to "0x64") }
        assertEquals("100", polkamarkt.currentBlock())

        rpc["chain_getHeader"] = { mapOf("parentHash" to "0x00") }
        assertEquals("0", polkamarkt.currentBlock())

        rpc.remove("chain_getHeader")
        assertEquals("0", polkamarkt.currentBlock())
    }

    @Test fun capabilitiesReflectRuntimeStorageCallsAndRpcMethods() = runTest {
        runtimeWith(module(listOf(entry("Markets", null), entry("Conditions", null)),
            calls = listOf("buy", "sell", "claim_market", "claim_creator_fees")))
        rpc["rpc_methods"] = { mapOf("methods" to allMethods) }

        assertEquals(
            PolkamarktRuntimeCapabilities(browse = true, quoteBuy = true, quoteSell = true, marketState = true, buy = true,
                sell = true, claimMarket = true, claimCreatorFees = true, reasons = emptyList()),
            polkamarkt.capabilities()
        )
    }

    @Test fun missingRuntimeAndRpcExplainEveryDisabledCapability() = runTest {
        coEvery { chainRegistry.getRuntimeOrNull(soraMainChainId) } returns null

        val capabilities = polkamarkt.capabilities()

        assertEquals(
            listOf(
                "Connected SORA runtime does not expose Polkamarkt storage.",
                "Live market state is unavailable; trading is disabled.",
                "Authoritative runtime quotes are unavailable.",
                "Creator claims are unavailable on this runtime."
            ),
            capabilities.reasons
        )
        assertTrue(listOf(capabilities.browse, capabilities.buy, capabilities.sell, capabilities.quoteBuy).none { it })
    }

    @Test fun quotesCallTheModeSpecificRpcWithCodecAmounts() = runTest {
        rpc["polkamarkt_quoteBuy"] = { mapOf("sharesOut" to "5") }
        rpc["polkamarkt_quoteSell"] = { mapOf("collateralOut" to "6") }

        val buy = polkamarkt.quote(PolkamarktQuoteRequest("7", PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, "1.5"))
        val sell = polkamarkt.quote(PolkamarktQuoteRequest("0x09", PolkamarktTradeMode.Sell, PolkamarktOutcome.No, "2"))

        assertEquals("5", buy.get("sharesOut").asString)
        assertEquals("6", sell.get("collateralOut").asString)
        assertEquals(listOf(BigInteger("7"), "Yes", BigInteger("1500000000000000000")), requests[0].params)
        assertEquals("polkamarkt_quoteSell", requests[1].method)
        assertEquals(listOf(BigInteger("9"), "No", BigInteger("2000000000000000000")), requests[1].params)
    }

    @Test fun quotesRejectZeroAmountsAndMarketIdsOutsideU32() = runTest {
        suspend fun failure(id: String, amount: String) = runCatching {
            polkamarkt.quote(PolkamarktQuoteRequest(id, PolkamarktTradeMode.Buy, PolkamarktOutcome.Yes, amount))
        }.exceptionOrNull()?.message

        assertEquals("Amount must be greater than zero.", failure("7", "0"))
        assertEquals("Invalid Polkamarkt market id.", failure("seven", "1"))
        assertEquals("Polkamarkt market id is outside u32.", failure("4294967296", "1"))
        assertTrue(requests.isEmpty())
    }

    @Test fun claimableIsSkippedWhenTheRuntimeLacksTheRpc() = runTest {
        rpc["rpc_methods"] = { mapOf("methods" to listOf("polkamarkt_marketState")) }

        assertTrue(polkamarkt.claimable("cnAlice", listOf("8")).isEmpty())
        assertEquals(listOf("rpc_methods"), requests.map { it.method })
    }

    @Test fun claimableQueriesEachDistinctMarketAndDropsEmptyOrFailedAnswers() = runTest {
        rpc["rpc_methods"] = { mapOf("methods" to allMethods) }
        rpc["polkamarkt_claimable"] = { params ->
            when (params[1]) {
                BigInteger("8") -> mapOf("status" to "Resolved", "traderPayout" to "0x05", "isCreator" to true, "creatorFees" to "4")
                BigInteger("9") -> emptyMap<String, Any>()
                else -> error("node error")
            }
        }

        val claims = polkamarkt.claimable("cnAlice", listOf("8", "8", "9", "10"))

        assertEquals(
            listOf(PolkamarktClaimable("8", "cnAlice", "Resolved", traderPayout = "5", creatorFees = "4", isCreator = true)),
            claims
        )
        assertEquals(3, requests.count { it.method == "polkamarkt_claimable" })
    }

    @Test fun marketsAreReadFromRuntimeStorageWithLiveStateAndVolume() = runTest {
        val prefix = "0x" + "ab".repeat(32)
        val nullValueKey = prefix + "0a000000"
        val keys = listOf(prefix + "07000000", "0xzz", nullValueKey, prefix + "09000000", prefix + "0b000000")
        coEvery { bulk.retrieveAllKeys(socket, any(), any()) } returns keys
        coEvery { bulk.queryKeys(socket, any(), any()) } answers {
            secondArg<List<String>>().associateWith { if (it == nullValueKey) null else "0x00" }
        }
        val market7 = Struct.Instance(mapOf("condition_id" to BigInteger("3"), "closeBlock" to BigInteger("200"),
            "status" to DictEnum.Entry("Open", null), "creator" to "cnCreator".toByteArray()))
        val market9 = Struct.Instance(mapOf("conditionId" to BigInteger("4"), "closeBlock" to BigInteger("50"),
            "mechanism" to DictEnum.Entry("Amm", null)))
        val untitled = Struct.Instance(mapOf("closeBlock" to BigInteger("1")))
        val condition7 = Struct.Instance(mapOf("question" to "Will it rain?".toByteArray().map { it.toInt() },
            "oracle" to "Oracle DAO\u0000\u0000".toByteArray(), "resolution_source" to " https://weather.example "))
        val condition9 = Struct.Instance(mapOf("question" to "Second?".toByteArray()))
        runtimeWith(module(listOf(
            entry("Markets", decoding(market7, market9, untitled)),
            entry("Conditions", decoding(condition7, condition9)),
            entry("ConditionDetails", decoding(Struct.Instance(mapOf("category" to "Weather".toByteArray())), Struct.Instance(emptyMap()))),
            entry("MarketVolume", decoding(BigInteger("5000000000000000000"), "not-a-number"))
        )))
        rpc["polkamarkt_marketState"] = { params ->
            check(params.single() == BigInteger("7")) { "no state" }
            mapOf("impliedYesProbabilityBps" to "4250", "mechanism" to "Dpm", "dpmCollateral" to "0x0de0b6b3a7640000")
        }

        val markets = polkamarkt.markets("100")

        assertEquals(
            listOf(
                PolkamarktMarket(
                    id = "7", conditionId = "3", creator = "cnCreator", title = "Will it rain?",
                    description = "Resolved by Oracle DAO. Source: https://weather.example", category = "Weather",
                    oracle = "Oracle DAO", resolutionSource = "https://weather.example", closeBlock = "200",
                    runtimeStatus = "Open", mechanism = "Dpm", liquidityUsd = "1", volumeUsd = "5",
                    probabilityPercent = "42.5", displayStatus = PolkamarktDisplayStatus.Open, runtimeOnly = true
                ),
                PolkamarktMarket(
                    id = "9", conditionId = "4", title = "Second?", description = "Read directly from SORA runtime storage.",
                    category = "Other", closeBlock = "50", mechanism = "Amm", displayStatus = PolkamarktDisplayStatus.Closed,
                    runtimeOnly = true
                )
            ),
            markets
        )
    }

    @Test fun marketsAreEmptyWithoutRuntimeModuleStorageOrValueType() = runTest {
        coEvery { chainRegistry.getRuntimeOrNull(soraMainChainId) } returns null
        assertTrue(polkamarkt.markets("1").isEmpty())

        runtimeWith(null)
        assertTrue(polkamarkt.markets("1").isEmpty())

        runtimeWith(module(listOf(entry("Conditions", null))))
        assertTrue(polkamarkt.markets("1").isEmpty())

        runtimeWith(module(listOf(entry("Markets", null))))
        assertTrue(polkamarkt.markets("1").isEmpty())
        assertTrue(requests.isEmpty())
    }
}
