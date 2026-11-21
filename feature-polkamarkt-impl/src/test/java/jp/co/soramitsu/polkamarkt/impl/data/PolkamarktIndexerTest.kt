package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.polkamarkt.api.PolkamarktDisplayStatus
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PolkamarktIndexerTest {
    private val requests = mutableListOf<JsonObject>()
    private val marketResponse = """{"data":{"markets":[{"marketId":"7","title":"Will it happen?","closeBlock":"100","probability":"0.75"}]}}"""

    private fun indexer(vararg responses: Pair<Int, String>): PolkamarktIndexer {
        val queue = ArrayDeque(responses.toList())
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
            requests += JsonParser.parseString(body).asJsonObject
            val (code, response) = queue.removeFirst()
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(code).message("fixture").body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return PolkamarktIndexer(client, Gson())
    }

    @Test
    fun latestMarketsUseCurrentBlockAndBoundTheQuery() = runTest {
        val markets = indexer(200 to marketResponse).markets("https://indexer.example/graphql", "100")
        assertEquals("7", markets.single().id)
        assertEquals("75", markets.single().probabilityPercent)
        assertEquals(PolkamarktDisplayStatus.Closed, markets.single().displayStatus)
        assertEquals(96, requests.single().getAsJsonObject("variables").get("limit").asInt)
        assertTrue(requests.single().get("query").asString.contains("PolkamarktLatestMarkets"))
    }

    @Test
    fun emptyLatestCatalogRetriesTheLegacySchema() = runTest {
        val markets = indexer(200 to "{}", 200 to marketResponse).markets("https://indexer.example/graphql", "99")
        assertEquals(PolkamarktDisplayStatus.Open, markets.single().displayStatus)
        assertEquals(2, requests.size)
        assertTrue(requests.last().get("query").asString.contains("PolkamarktLegacyMarkets"))
    }

    @Test
    fun graphqlSchemaErrorsRetryTheLegacySchema() = runTest {
        val markets = indexer(200 to """{"errors":[{"message":"Unknown dpmCollateral"}]}""", 200 to marketResponse)
            .markets("https://indexer.example/graphql", "90")
        assertEquals("Will it happen?", markets.single().title)
        assertEquals(2, requests.size)
    }

    @Test
    fun failedLegacyRequestReportsItsHttpStatus() = runTest {
        val result = runCatching {
            indexer(503 to "offline", 502 to "offline").markets("https://indexer.example/graphql", "0")
        }
        assertEquals("Polkamarkt indexer returned HTTP 502.", result.exceptionOrNull()?.message)
        assertEquals(2, requests.size)
    }

    @Test
    fun historySkipsMissingOrInvalidMarketIds() = runTest {
        val indexer = indexer()
        assertTrue(indexer.history("https://indexer.example/graphql", null).isEmpty())
        assertTrue(indexer.history("https://indexer.example/graphql", "invalid").isEmpty())
        assertTrue(requests.isEmpty())
    }

    @Test
    fun historyUsesAnIntegerIdAndParsesTheReturnedProbability() = runTest {
        val response = """{"data":{"marketSnapshots":[{"id":"point","marketId":"7","probability":"0.25","blockHeight":"80"}]}}"""
        val history = indexer(200 to response).history("https://indexer.example/graphql", "7")
        assertEquals("25", history.single().probabilityPercent)
        assertEquals("80", history.single().blockHeight)
        assertEquals(7, requests.single().getAsJsonObject("variables").get("marketId").asInt)
    }

    @Test
    fun accountActivityFallsBackAndPreservesTheRequestedAccount() = runTest {
        val response = """{"data":{"accountPositions":[{"id":"p","marketId":"7","shares":"100"}],"accountTrades":[{"id":"t","marketId":"7","side":"Buy"}]}}"""
        val activity = indexer(503 to "offline", 200 to response).activity("https://indexer.example/graphql", "alice")
        assertEquals("100", activity.positions.single().shares)
        assertEquals("buy", activity.trades.single().side)
        assertEquals("alice", requests.last().getAsJsonObject("variables").get("account").asString)
        assertTrue(requests.last().get("query").asString.contains("PolkamarktLegacyAccountActivity"))
    }

    @Test
    fun absentAccountAndUnavailableOptionalDataReturnEmptyLists() = runTest {
        val indexer = indexer(500 to "offline", 500 to "offline", 500 to "offline")
        assertTrue(indexer.activity("https://indexer.example/graphql", null).positions.isEmpty())
        assertTrue(requests.isEmpty())
        assertTrue(indexer.history("https://indexer.example/graphql", "7").isEmpty())
        val activity = indexer.activity("https://indexer.example/graphql", "alice")
        assertTrue(activity.positions.isEmpty())
        assertTrue(activity.trades.isEmpty())
        assertEquals(3, requests.size)
    }
}
