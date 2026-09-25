package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.Gson
import com.google.gson.JsonParser
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.security.MessageDigest

class PolkamarktContractTest {
    private val fixtureBytes = requireNotNull(javaClass.classLoader?.getResourceAsStream("contracts/polkamarkt-v1.json"))
        .use { it.readBytes() }
    private val fixture = JsonParser.parseString(fixtureBytes.toString(Charsets.UTF_8)).asJsonObject

    @Test
    fun `shared fixture checksum and asset identities stay exact`() {
        val digest = MessageDigest.getInstance("SHA-256").digest(fixtureBytes).joinToString("") { "%02x".format(it) }
        assertEquals("e0eb0fba87e580ecd15c8722ce0876c5e10a994cc3c497d95b16bcc34ee021b4", digest)
        val network = fixture.getAsJsonObject("network")
        assertEquals(KUSD_ASSET_ID, network.getAsJsonObject("collateral").get("assetId").asString)
        assertEquals(XOR_ASSET_ID, network.getAsJsonObject("feeAsset").get("assetId").asString)
    }

    @Test
    fun `legacy GraphQL market response is current-block derived and precision safe`() {
        val catalog = fixture.getAsJsonObject("catalog")
        val response = catalog.getAsJsonObject("legacyQueryResponse").getAsJsonObject("data")
        val markets = parseIndexedMarkets(response, catalog.get("currentBlock").asString)
        assertEquals(listOf("7", "8"), markets.map { it.id })
        assertEquals(PolkamarktDisplayStatus.Open, markets.first().displayStatus)
        assertEquals(PolkamarktDisplayStatus.Closed, markets.last().displayStatus)
        assertEquals("1000000000000000000.000000000000000001", markets.first().liquidityUsd)
    }

    @Test
    fun `runtime-only market merges without discarding indexed detail`() {
        val indexed = parseIndexedMarkets(
            fixture.getAsJsonObject("catalog").getAsJsonObject("legacyQueryResponse").getAsJsonObject("data"),
            "100"
        )
        val runtime = listOf(
            indexed.first().copy(runtimeStatus = "Open", runtimeOnly = true),
            PolkamarktMarket(
                id = "9",
                conditionId = "6",
                creator = "cnRuntimeOnly",
                title = "Runtime-only market while the indexer catches up",
                description = "Read directly from SORA runtime storage.",
                category = "AI",
                closeBlock = "150",
                runtimeStatus = "Open",
                mechanism = "DynamicPariMutuel",
                displayStatus = PolkamarktDisplayStatus.Open,
                runtimeOnly = true
            )
        )
        val merged = mergeMarkets(indexed, runtime)
        assertEquals(listOf("7", "9", "8"), merged.map { it.id })
        assertTrue(merged.first { it.id == "7" }.runtimeOnly)
    }

    @Test
    fun `arbitrary precision quote amount remains an unquoted RPC number`() {
        val quote = fixture.getAsJsonArray("quotes").first().asJsonObject
        val amount = quote.getAsJsonObject("domainParams").get("collateralIn").asString
        val request = RuntimeRequest(
            quote.get("rpcMethod").asString,
            listOf(BigInteger("7"), "Yes", BigInteger(amount))
        )
        val json = Gson().toJson(request)
        assertTrue(json.contains(amount))
        assertFalse(json.contains("\"$amount\""))
        assertEquals(BigInteger(amount), toCodecAmount("1.000000000000000001"))
    }

    @Test
    fun `activity and trader plus creator claims preserve codec precision`() {
        val activityFixture = fixture.getAsJsonObject("accountActivity")
        val activity = parseActivity(activityFixture.getAsJsonObject("indexerResponse"))
        assertEquals("1800000000000000002", activity.positions.single().shares)
        assertEquals("1000000000000000001", activity.trades.single().collateral)

        val account = activityFixture.get("account").asString
        val claim = parseClaimable(activityFixture.getAsJsonObject("runtimeClaimable"), account, "7")
        assertEquals("2200000000000000000", claim?.claimablePayout)
        assertEquals("5000000000000000", claim?.creatorFees)
        assertTrue(claim?.isCreator == true)
    }

    @Test
    fun `v1 exposes no unsafe creation or early-resolution mutation`() {
        val deferred = fixture.getAsJsonArray("deferredActions").map { it.asString }.toSet()
        assertEquals(setOf("createMarket", "reportEarlyResolution"), deferred)
        val mutationTypes = jp.co.soramitsu.polkamarkt.api.PolkamarktMutation::class.java.declaredClasses.map {
            it.simpleName
        }.toSet()
        assertEquals(setOf("Trade", "ClaimMarket", "ClaimCreatorFees"), mutationTypes)
    }

    @Test
    fun `trade boundary rechecks runtime market exact quote KUSD XOR and shares`() {
        val buy = trade(PolkamarktTradeMode.Buy)
        val quote = quote(PolkamarktTradeMode.Buy)
        val market = runtimeMarket()
        val balances = PolkamarktExecutionBalances(
            kusd = "1.2",
            xor = "0.01",
            yesShares = "2",
            noShares = "2"
        )

        validatePolkamarktTradeExecution(buy, market, capabilities(), quote, balances)

        assertRejected("KUSD") {
            validatePolkamarktTradeExecution(buy, market, capabilities(), quote, balances.copy(kusd = "1"))
        }
        assertRejected("XOR") {
            validatePolkamarktTradeExecution(buy, market, capabilities(), quote, balances.copy(xor = "0.0009"))
        }
        assertRejected("quote changed") {
            validatePolkamarktTradeExecution(
                buy.copy(minimumResult = "1.7"),
                market,
                capabilities(),
                quote,
                balances
            )
        }
        assertRejected("no longer open") {
            validatePolkamarktTradeExecution(
                buy,
                market.copy(displayStatus = PolkamarktDisplayStatus.Closed),
                capabilities(),
                quote,
                balances
            )
        }
        assertRejected("not authoritative") {
            validatePolkamarktTradeExecution(
                buy,
                market.copy(runtimeOnly = false),
                capabilities(),
                quote,
                balances
            )
        }

        val sell = trade(PolkamarktTradeMode.Sell, amount = "2", minimum = "1.8")
        val sellQuote = quote(
            mode = PolkamarktTradeMode.Sell,
            amount = "2",
            minimum = "1.8",
            result = "2"
        )
        assertRejected("available market shares") {
            validatePolkamarktTradeExecution(
                sell,
                market,
                capabilities(),
                sellQuote,
                balances.copy(yesShares = "1.999")
            )
        }
    }

    @Test
    fun `claim boundary requires exact authoritative payout and fresh XOR fee`() {
        val trader = PolkamarktMutation.ClaimMarket("7")
        val creator = PolkamarktMutation.ClaimCreatorFees("7")
        val claim = PolkamarktClaimable(
            marketId = "7",
            account = "cnAccount",
            status = "Resolved",
            traderPayout = "2200000000000000000",
            creatorFees = "5000000000000000",
            isCreator = true
        )

        validatePolkamarktClaimExecution(trader, capabilities(), claim, "0.01", "0.001")
        validatePolkamarktClaimExecution(creator, capabilities(), claim, "0.01", "0.001")

        assertRejected("no trader payout") {
            validatePolkamarktClaimExecution(
                trader,
                capabilities(),
                claim.copy(traderPayout = "0", claimablePayout = "0"),
                "0.01",
                "0.001"
            )
        }
        assertRejected("no creator fees") {
            validatePolkamarktClaimExecution(
                creator,
                capabilities(),
                claim.copy(creatorFees = "0"),
                "0.01",
                "0.001"
            )
        }
        assertRejected("XOR") {
            validatePolkamarktClaimExecution(trader, capabilities(), claim, "0.0001", "0.001")
        }
    }

    @Test
    fun `mutation handler cannot submit before final validators`() {
        val source = java.io.File(
            requireNotNull(System.getProperty("user.dir")),
            "src/main/kotlin/jp/co/soramitsu/polkamarkt/impl/data/PolkamarktNetwork.kt"
        ).takeIf { it.isFile } ?: java.io.File(
            requireNotNull(System.getProperty("user.dir")),
            "feature-polkamarkt-impl/src/main/kotlin/jp/co/soramitsu/polkamarkt/impl/data/PolkamarktNetwork.kt"
        )
        val implementation = source.readText()
        val mutation = implementation.substringAfter("override suspend fun mutate")
            .substringBefore("private suspend fun authorizeMutation")
        val authorizationCall = mutation.indexOf("authorizeMutation(request)")
        val finalKillSwitch = mutation.lastIndexOf("toggles.polkamarktMutationsEnabled")
        val finalDisclaimer = mutation.lastIndexOf("polkaswapInteractor.hasReadDisclaimer")
        val submit = mutation.indexOf("extrinsicService.submitAuthorizedExtrinsic(")

        assertTrue(authorizationCall >= 0)
        assertTrue(finalKillSwitch > authorizationCall)
        assertTrue(finalDisclaimer > authorizationCall)
        assertTrue(submit > finalKillSwitch)
        assertTrue(submit > finalDisclaimer)

        val authorization = implementation.substringAfter("private suspend fun authorizeMutation")
            .substringBefore("private suspend fun isLocallySignable")
        val tradeGuard = authorization.indexOf("validatePolkamarktTradeExecution(")
        val claimGuard = authorization.indexOf("validatePolkamarktClaimExecution(")
        val finalWallet = authorization.indexOf("val finalWallet = accountRepository.getSelectedMetaAccount()")

        assertTrue(tradeGuard >= 0)
        assertTrue(claimGuard > tradeGuard)
        assertTrue(finalWallet > claimGuard)
        assertTrue(authorization.contains("runtime.capabilities()"))
        assertTrue(authorization.contains("runtime.markets(finalBlock)"))
        assertTrue(authorization.contains("runtime.claimable("))
        assertTrue(authorization.contains("exactBalances()"))
        assertTrue(authorization.contains("isLocallySignable(finalWallet.id, chain)"))
    }

    private fun runtimeMarket() = PolkamarktMarket(
        id = "7",
        title = "Runtime market",
        description = "Runtime-backed",
        category = "Other",
        displayStatus = PolkamarktDisplayStatus.Open,
        runtimeOnly = true
    )

    private fun capabilities() = PolkamarktRuntimeCapabilities(
        browse = true,
        quoteBuy = true,
        quoteSell = true,
        marketState = true,
        buy = true,
        sell = true,
        claimMarket = true,
        claimCreatorFees = true
    )

    private fun trade(
        mode: PolkamarktTradeMode,
        amount: String = "1",
        minimum: String = "1.8"
    ) = PolkamarktMutation.Trade(
        marketId = "7",
        mode = mode,
        outcome = PolkamarktOutcome.Yes,
        amount = amount,
        minimumResult = minimum
    )

    private fun quote(
        mode: PolkamarktTradeMode,
        amount: String = "1",
        minimum: String = "1.8",
        result: String = "2"
    ) = PolkamarktQuote(
        marketId = "7",
        mode = mode,
        outcome = PolkamarktOutcome.Yes,
        amount = amount,
        feeAmount = "0.1",
        networkFee = "0.001",
        resultAmount = result,
        minimumResult = minimum
    )

    private fun assertRejected(messagePart: String, block: () -> Unit) {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected rejection containing '$messagePart'", error != null)
        assertTrue(
            "Expected '${error?.message}' to contain '$messagePart'",
            error?.message.orEmpty().contains(messagePart, ignoreCase = true)
        )
    }
}
