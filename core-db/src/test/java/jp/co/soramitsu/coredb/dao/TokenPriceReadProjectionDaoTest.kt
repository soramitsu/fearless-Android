package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.cache.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class TokenPriceReadProjectionDaoTest {
    private fun cell(value: String?): CachedNumericCell = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    private fun row(id: String = "price", rate: String? = "123.4500", change: String? = "-1.250") =
        RawCachedTokenPriceRow(id, "USD", cell(rate), cell(change))

    @Test fun snapshotPreservesExactDecimalScaleAndNegativeRateChange() = runBlocking {
        val dao = Fake(listOf(row()))
        val actual = dao.getTokenPrice("price")!!
        assertEquals("price", actual.priceId)
        assertEquals("USD", actual.fiatSymbol)
        assertEquals(CachedNumericState.Known(BigDecimal("123.4500")), actual.fiatRate)
        assertEquals(CachedNumericState.Known(BigDecimal("-1.250")), actual.recentRateChange)
        assertEquals(listOf("get:price"), dao.reads)
    }

    @Test fun absentPriceRemainsAbsentAndIsNotAnAllZeroQuote() = runBlocking {
        assertNull(Fake(emptyList()).getTokenPrice("missing"))
    }

    @Test fun missingNumericCellRemainsDistinctFromMalformedSibling() = runBlocking {
        val actual = Fake(listOf(row(rate = null, change = "not-a-number"))).getTokenPrice("price")!!
        assertSame(CachedNumericState.Missing, actual.fiatRate)
        assertTrue(actual.recentRateChange is CachedNumericState.Unavailable)
    }

    @Test fun malformedRateDoesNotDiscardValidChangeOrPriceIdentity() = runBlocking {
        val actual = Fake(listOf(row(rate = "bad", change = "-2.5"))).getTokenPrice("price")!!
        assertEquals("price", actual.priceId)
        assertTrue(actual.fiatRate is CachedNumericState.Unavailable)
        assertEquals(CachedNumericState.Known(BigDecimal("-2.5")), actual.recentRateChange)
    }

    @Test fun observationRetainsRemovalAndSubsequentRecovery() = runBlocking {
        val dao = Fake(emptyList())
        dao.single = listOf(null, row(rate = "bad"), null, row(rate = "5.00")).asFlow()
        val actual = dao.observeTokenPrice("price").toList()
        assertEquals(4, actual.size)
        assertNull(actual[0])
        assertTrue(actual[1]!!.fiatRate is CachedNumericState.Unavailable)
        assertNull(actual[2])
        assertEquals(CachedNumericState.Known(BigDecimal("5.00")), actual[3]!!.fiatRate)
        assertEquals(listOf("observe:price"), dao.reads)
    }

    @Test fun currencyListRetainsHealthyAndDamagedRowsInProviderOrder() = runBlocking {
        val dao = Fake(listOf(row("healthy"), row("bad", rate = "broken"), row("missing", rate = null)))
        val actual = dao.observePrices("USD").first()
        assertEquals(listOf("healthy", "bad", "missing"), actual.map { it.priceId })
        assertEquals(CachedNumericState.Known(BigDecimal("123.4500")), actual[0].fiatRate)
        assertTrue(actual[1].fiatRate is CachedNumericState.Unavailable)
        assertSame(CachedNumericState.Missing, actual[2].fiatRate)
        assertEquals(listOf("currency:USD"), dao.reads)
    }

    @Test fun currencyObservationCanRecoverOnlyDamagedPriceWithoutLosingSibling() = runBlocking {
        val healthy = row("healthy")
        val dao = Fake(emptyList())
        dao.lists = listOf(listOf(healthy, row("bad", "broken")), listOf(healthy, row("bad", "7.0"))).asFlow()
        val actual = dao.observePrices("USD").toList()
        assertEquals(actual[0][0], actual[1][0])
        assertTrue(actual[0][1].fiatRate is CachedNumericState.Unavailable)
        assertEquals(CachedNumericState.Known(BigDecimal("7.0")), actual[1][1].fiatRate)
    }

    @Test fun withheldOversizedPriceAndBlobChangeDoNotEscapeAsValues() = runBlocking {
        val raw = row().copy(fiatRate = CachedNumericCell("text", 2097152, null),
            recentRateChange = CachedNumericCell("blob", 3, null))
        val actual = Fake(listOf(raw)).getTokenPrice("price")!!
        val rate = actual.fiatRate as CachedNumericState.Unavailable
        val change = actual.recentRateChange as CachedNumericState.Unavailable
        assertEquals(CachedNumericIssue.Reason.PAYLOAD_BOUND, rate.issue.reason)
        assertEquals(CachedNumericIssue.Reason.STORAGE_CLASS, change.issue.reason)
    }

    @Test fun databaseFailureIsPropagatedRatherThanConvertedToAnEmptyCurrencyList() = runBlocking {
        val failure = IllegalStateException("public test storage failure")
        val dao = Fake(emptyList())
        dao.lists = flow { throw failure }
        val actual = runCatching { dao.observePrices("USD").toList() }.exceptionOrNull()
        assertSame(failure, actual)
    }

    private class Fake(private val rows: List<RawCachedTokenPriceRow>) : TokenPriceDao() {
        val reads = mutableListOf<String>()
        var single: Flow<RawCachedTokenPriceRow?> = flowOf(rows.firstOrNull())
        var lists: Flow<List<RawCachedTokenPriceRow>> = flowOf(rows)
        override suspend fun getTokenPriceRowsByIds(priceIds: List<String>): List<RawCachedTokenPriceRow> = error("new batch seam not exercised by old tests")
        override fun observeTokenPriceRowsByIds(priceIds: List<String>): Flow<List<RawCachedTokenPriceRow>> = error("new batch seam not exercised by old tests")
        override suspend fun getTokenPriceRow(priceId: String): RawCachedTokenPriceRow? {
            reads += "get:$priceId"
            return rows.firstOrNull { it.priceId == priceId }
        }
        override fun observeTokenPriceRow(priceId: String): Flow<RawCachedTokenPriceRow?> {
            reads += "observe:$priceId"
            return single
        }
        override fun observePriceRows(symbol: String): Flow<List<RawCachedTokenPriceRow>> {
            reads += "currency:$symbol"
            return lists
        }
        override suspend fun insertTokenPrice(token: TokenPriceLocal): Unit = error("read called writer")
        override suspend fun insertTokensPrice(tokens: List<TokenPriceLocal>): Unit = error("read called writer")
        override suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal): Unit = error("read called writer")
        override suspend fun updatePrices(priceId: String, fiatSymbol: String, fiatRate: BigDecimal): Unit = error("read called writer")
    }
}
