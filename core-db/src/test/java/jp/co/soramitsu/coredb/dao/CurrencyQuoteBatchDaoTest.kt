package jp.co.soramitsu.coredb.dao

import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.cache.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CurrencyQuoteBatchDaoTest {
    private fun cell(value: String?) = value?.toByteArray()?.let { CachedNumericCell("text", it.size.toLong(), it) }
        ?: CachedNumericCell("null", 0, null)
    private fun row(id: String, rate: String? = "12.500", currency: String? = "usd") =
        RawCachedTokenPriceRow(id, null, cell(rate), cell("-1.25"), cell(currency))
    private fun ids(size: Int) = (0 until size).map { "id-%04d".format(it) }

    @Test fun emptySnapshotDoesNotCallAnyRawQuery() = runBlocking {
        val dao = Fake()
        assertTrue(dao.getTokenPricesByIds(emptyList()).isEmpty())
        assertTrue(dao.gets.isEmpty())
    }
    @Test fun emptyObservationEmitsCompletedEmptyWithoutQuery() = runBlocking {
        val dao = Fake()
        assertEquals(listOf(emptyList<CachedTokenPriceRead>()), dao.observeTokenPricesByIds(emptyList()).toList())
        assertTrue(dao.observes.isEmpty())
    }
    @Test fun snapshot512UsesOneExactBatch() = runBlocking {
        val keys = ids(512); val dao = Fake()
        assertEquals(keys, dao.getTokenPricesByIds(keys).map { it.priceId })
        assertEquals(listOf(keys), dao.gets)
        assertEquals(512, TokenPriceDao.QUOTE_ID_BATCH_SIZE)
    }
    @Test fun snapshot513DeduplicatesAndCapturesInputBeforeSuspension() = runBlocking {
        val keys = ids(513).toMutableList(); keys += keys.first()
        val dao = Fake()
        dao.getter = { batch -> keys.clear(); batch.map { row(it) } }
        assertEquals(ids(513), dao.getTokenPricesByIds(keys).map { it.priceId })
        assertEquals(listOf(512, 1), dao.gets.map { it.size })
    }
    @Test fun observation512UsesOneBatchAndRetainsEveryFiniteEmission() = runBlocking {
        val keys = ids(512); val dao = Fake()
        dao.observer = { batch -> flowOf(batch.map { row(it) }, emptyList(), batch.take(1).map { row(it) }) }
        assertEquals(listOf(512, 0, 1), dao.observeTokenPricesByIds(keys).toList().map { it.size })
        assertEquals(listOf(keys), dao.observes)
    }
    @Test fun observation513CapturesKeysBeforeCallerMutation() = runBlocking {
        val keys = ids(513).toMutableList(); val dao = Fake()
        keys += keys.first()
        val flow = dao.observeTokenPricesByIds(keys)
        keys.clear()
        assertEquals(ids(513), flow.first().map { it.priceId })
        assertEquals(listOf(512, 1), dao.observes.map { it.size })
    }
    @Test fun snapshotPreservesUnknownNumericAndCurrencyStatesWithoutSymbols() = runBlocking {
        val dao = Fake()
        dao.getter = { listOf(row("healthy"), row("malformed", "broken"), row("legacy", null, null),
            row("bad-currency").copy(fiatCurrencyId=CachedNumericCell("blob", 3, null))) }
        val result = dao.getTokenPricesByIds(listOf("healthy", "malformed", "legacy", "bad-currency"))
        assertEquals(CachedNumericState.Known(BigDecimal("12.500")), result[0].fiatRate)
        assertEquals(CachedNumericState.Known(BigDecimal("-1.25")), result[0].recentRateChange)
        assertEquals(CachedFiatCurrencyIdentity.Known("usd"), result[0].fiatCurrencyIdentity)
        assertTrue(result[1].fiatRate is CachedNumericState.Unavailable)
        assertSame(CachedNumericState.Missing, result[2].fiatRate)
        assertSame(CachedFiatCurrencyIdentity.Unverified, result[2].fiatCurrencyIdentity)
        assertTrue(result[3].fiatCurrencyIdentity is CachedFiatCurrencyIdentity.Unavailable)
        assertTrue(result.all { it.fiatSymbol == null })
    }
    @Test fun absentRequestedQuotesAreNotFabricated() = runBlocking {
        val dao = Fake(); dao.getter = { emptyList() }
        assertTrue(dao.getTokenPricesByIds(listOf("missing")).isEmpty())
    }
    @Test fun laterBatchSnapshotFailureDoesNotReturnPartialSuccess() = runBlocking {
        val failure = StorageFailure(Any()); val dao = Fake()
        dao.getter = { batch -> if (batch.size == 1) throw failure else batch.map { row(it) } }
        assertSame(failure, runCatching { dao.getTokenPricesByIds(ids(513)) }.exceptionOrNull())
        assertEquals(listOf(512, 1), dao.gets.map { it.size })
    }
    @Test fun observationStorageFailureDoesNotBecomeEmpty() = runBlocking {
        val failure = StorageFailure(Any()); val dao = Fake()
        dao.observer = { flow { throw failure } }
        assertSame(failure, runCatching { dao.observeTokenPricesByIds(listOf("id")).toList() }.exceptionOrNull())
    }
    @Test fun allBatchesMustEmitAndCancellationClosesBothSubscriptions() = runBlocking {
        val first = Channel<List<RawCachedTokenPriceRow>>(Channel.UNLIMITED)
        val second = Channel<List<RawCachedTokenPriceRow>>(Channel.UNLIMITED)
        val outputs = Channel<List<CachedTokenPriceRead>>(Channel.UNLIMITED)
        var closed = 0
        val dao = Fake()
        dao.observer = { batch -> flow {
            try { emitAll((if (batch.size == 512) first else second).receiveAsFlow()) }
            finally { closed++ }
        } }
        val job = launch(start = CoroutineStart.UNDISPATCHED) { dao.observeTokenPricesByIds(ids(513)).collect { outputs.send(it) } }
        try {
            first.send(listOf(row("id-0000"))); yield()
            assertTrue(outputs.tryReceive().isFailure)
            second.send(listOf(row("id-0512")))
            assertEquals(listOf("id-0000", "id-0512"), withTimeout(2000) { outputs.receive() }.map { it.priceId })
            first.send(emptyList())
            assertEquals(listOf("id-0512"), withTimeout(2000) { outputs.receive() }.map { it.priceId })
        } finally { job.cancelAndJoin(); first.close(); second.close(); outputs.close() }
        assertEquals(2, closed)
    }

    private inner class Fake : TokenPriceDao() {
        val gets = mutableListOf<List<String>>()
        val observes = mutableListOf<List<String>>()
        var getter: suspend (List<String>) -> List<RawCachedTokenPriceRow> = { keys -> keys.map { row(it) } }
        var observer: (List<String>) -> Flow<List<RawCachedTokenPriceRow>> = { keys -> flowOf(keys.map { row(it) }) }
        override suspend fun getTokenPriceRowsByIds(priceIds: List<String>): List<RawCachedTokenPriceRow> {
            gets += priceIds.toList(); return getter(priceIds)
        }
        override fun observeTokenPriceRowsByIds(priceIds: List<String>): Flow<List<RawCachedTokenPriceRow>> {
            observes += priceIds.toList(); return observer(priceIds)
        }
        override suspend fun getTokenPriceRow(priceId: String): RawCachedTokenPriceRow? = error("legacy query called")
        override fun observeTokenPriceRow(priceId: String): Flow<RawCachedTokenPriceRow?> = error("legacy query called")
        override fun observePriceRows(symbol: String): Flow<List<RawCachedTokenPriceRow>> = error("legacy query called")
        override suspend fun insertTokenPrice(token: TokenPriceLocal): Unit = error("writer called")
        override suspend fun insertTokensPrice(tokens: List<TokenPriceLocal>): Unit = error("writer called")
        override suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal): Unit = error("writer called")
        override suspend fun updatePrices(priceId: String, fiatSymbol: String, fiatRate: BigDecimal): Unit = error("writer called")
    }
    private class StorageFailure(val marker: Any) : RuntimeException("public storage failure")
}
