package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.coredb.model.cache.CachedNumericCell
import jp.co.soramitsu.coredb.model.cache.RawCachedTokenPriceRow
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.Collections
import java.util.concurrent.atomic.AtomicReference

/** Actual repository, SelectedFiat and batch DAO wrappers; only preferences/raw storage are controlled. */
@OptIn(InternalCoroutinesApi::class)
class TokenRepositoryCurrencyTest {
    private fun configuration(
        priceId: String? = "catalog",
        provider: Asset.PriceProvider? = null
    ): Asset = mockk {
        every { this@mockk.priceId } returns priceId
        every { this@mockk.priceProvider } returns provider
    }

    private fun cell(value: String?) = value?.toByteArray(Charsets.UTF_8)?.let {
        CachedNumericCell("text", it.size.toLong(), it)
    } ?: CachedNumericCell("null", 0, null)

    private fun row(
        currency: String? = "usd",
        priceId: String = "catalog",
        rate: String? = "2.50",
        change: String? = "-0.125",
        symbol: String? = "$"
    ) = RawCachedTokenPriceRow(priceId, symbol, cell(rate), cell(change), cell(currency))

    private fun repository(dao: RawDao, selection: Selection, trusted: Boolean = true) = TokenRepositoryImpl(
        dao,
        selection.selected,
        mockk<PortfolioVisibility> { every { hasTrustedPrice(any<Asset>()) } returns trusted }
    )

    private suspend fun <T> Channel<T>.next(): T = withTimeout(5000) { receive() }

    private fun assertUnpriced(token: Token, currency: String) {
        assertEquals(currency, token.fiatContext?.currencyId)
        assertNull(token.fiatRate)
        assertNull(token.recentRateChange)
        assertNull(token.fiatAmount(BigDecimal.ONE))
        assertFalse(token.fiatRateState is AssetAmountState.Known)
    }

    @Test fun getRetainsHistoricalCurrencyCapturedBeforeDelayedRead() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        dao.getter = { entered.complete(Unit); release.await(); listOf(row(currency = "usd")) }
        val result = async { repository(dao, selection).getToken(configuration()) }
        try {
            withTimeout(5000) { entered.await() }
            selection.selected.set("eur")
            release.complete(Unit)
            val token = withTimeout(5000) { result.await() }
            assertEquals("eur", selection.selected.get())
            assertEquals("usd", token.fiatContext?.currencyId)
            assertEquals("usd", token.quoteCurrencyId)
            assertEquals("usd", token.fiatSymbol)
            assertEquals(BigDecimal("2.50"), token.fiatRate)
        } finally { release.complete(Unit); result.cancelAndJoin() }
    }

    @Test fun getAtoBtoARemainsAHistoricalResultWithoutClaimingCurrentGeneration() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        dao.getter = { entered.complete(Unit); release.await(); listOf(row(rate = "7")) }
        val result = async { repository(dao, selection).getToken(configuration()) }
        try {
            withTimeout(5000) { entered.await() }
            selection.selected.set("eur"); selection.selected.set("usd")
            release.complete(Unit)
            val token = withTimeout(5000) { result.await() }
            assertEquals("usd", token.fiatContext?.currencyId)
            assertEquals(BigDecimal("7"), token.fiatRate)
            assertEquals(listOf(listOf("catalog")), dao.gets)
        } finally { release.complete(Unit); result.cancelAndJoin() }
    }

    @Test fun getCapturesExactCaseSensitiveSelectionAndUsesOnlyItsConfiguredKey() = runBlocking {
        val supported = Asset.PriceProvider("chainlink", Asset.PriceProviderType.Chainlink)
        val unsupported = Asset.PriceProvider("foreign", Asset.PriceProviderType.Unknown)
        for ((currency, provider, expected) in listOf(
            Triple("usd", supported, "chainlink"), Triple("eur", supported, "catalog"),
            Triple("USD", supported, "catalog"), Triple("usd", unsupported, "catalog"),
            Triple("usd", null, "catalog")
        )) {
            val dao = RawDao(); dao.getter = { ids -> listOf(row(currency, ids.single())) }
            val token = repository(dao, Selection(currency)).getToken(configuration(provider = provider))
            assertEquals(listOf(listOf(expected)), dao.gets)
            assertEquals(currency, token.fiatContext?.currencyId)
            assertEquals(currency, token.quoteCurrencyId)
            assertEquals(expected, token.quotePriceId)
            assertEquals(BigDecimal("2.50"), token.fiatRate)
        }
        assertTrue(supported.isSupported)
        assertFalse(unsupported.isSupported)
        assertEquals(listOf(Asset.PriceProviderType.Chainlink), supportedPriceProviders)
    }

    @Test fun getAndObserveMissingCatalogKeyDoNotQueryOrFabricateZero() = runBlocking {
        val dao = RawDao(); val repo = repository(dao, Selection("cad"))
        for (key in listOf(null, "", " ")) {
            val asset = configuration(priceId = key)
            assertUnpriced(repo.getToken(asset), "cad")
            assertUnpriced(withTimeout(5000) { repo.observeToken(asset).first() }, "cad")
        }
        assertTrue(dao.gets.isEmpty()); assertTrue(dao.observes.isEmpty())
    }

    @Test fun invalidSelectionDoesNotQueryAndCannotAuthorizeFiat() = runBlocking {
        val dao = RawDao(); val repo = repository(dao, Selection(" "))
        val token = repo.getToken(configuration())
        assertEquals(AssetAmountState.Unavailable("invalid-selected-fiat"), token.fiatRateState)
        assertNull(token.fiatSymbol)
        assertUnpriced(withTimeout(5000) { repo.observeToken(configuration()).first() }, " ")
        assertTrue(dao.gets.isEmpty()); assertTrue(dao.observes.isEmpty())
    }

    @Test fun getRejectsForeignCurrencyEvenWithSharedDollarAndPreservesMissingAuthority() = runBlocking {
        for (currency in listOf("usd", null, "cad ")) {
            val dao = RawDao(); dao.getter = { listOf(row(currency = currency)) }
            val token = repository(dao, Selection("cad")).getToken(configuration())
            assertUnpriced(token, "cad")
            assertEquals(AssetAmountState.Unavailable("cached-price-currency-mismatch"), token.fiatRateState)
            assertEquals("cad", token.fiatSymbol)
        }
    }

    @Test fun getRejectsForeignKeyAndDoesNotFallBackFromSupportedChainlink() = runBlocking {
        val dao = RawDao(); dao.getter = { listOf(row()) }
        val asset = configuration(provider = Asset.PriceProvider("chainlink", Asset.PriceProviderType.Chainlink))
        val token = repository(dao, Selection("usd")).getToken(asset)
        assertEquals(listOf(listOf("chainlink")), dao.gets)
        assertEquals(AssetAmountState.Unavailable("cached-price-identity-mismatch"), token.fiatRateState)
        assertUnpriced(token, "usd")
    }

    @Test fun getKeepsMalformedAndMissingNumericDistinctAndNegativeChangeKnown() = runBlocking {
        val dao = RawDao(); val repo = repository(dao, Selection("usd"))
        dao.getter = { listOf(row(rate = "not-a-number")) }
        val damaged = repo.getToken(configuration())
        assertTrue(damaged.fiatRateState is AssetAmountState.Unavailable)
        assertNull(damaged.fiatAmount(BigDecimal.ONE))
        assertEquals(BigDecimal("-0.125"), damaged.recentRateChange)
        dao.getter = { listOf(row(rate = null)) }
        assertSame(AssetAmountState.Missing, repo.getToken(configuration()).fiatRateState)
        dao.getter = { listOf(row(rate = "0")) }
        assertEquals(BigDecimal.ZERO, repo.getToken(configuration()).fiatRate)
    }

    @Test fun getIgnoresPersistedSymbolAndUsesMetadataLessSelectedId() = runBlocking {
        val dao = RawDao(); dao.getter = { listOf(row(currency = "cad", symbol = "x".repeat(2 * 1024 * 1024))) }
        val token = repository(dao, Selection("cad")).getToken(configuration())
        assertEquals("cad", token.fiatSymbol)
        assertEquals(BigDecimal("2.50"), token.fiatRate)
    }

    @Test fun getAndObserveUntrustedPriceStayUnpriced() = runBlocking {
        val dao = RawDao(); val repo = repository(dao, Selection("usd"), trusted = false)
        assertUnpriced(repo.getToken(configuration()), "usd")
        val results = withTimeout(5000) { repo.observeToken(configuration()).take(2).toList() }
        assertEquals(2, results.size)
        results.forEach { assertUnpriced(it, "usd") }
        assertTrue(results.all { it.quoteCurrencyId == null })
    }

    @Test fun getAbsentOrAmbiguousRowsRemainUnpriced() = runBlocking {
        val dao = RawDao(); val repo = repository(dao, Selection("usd"))
        for (rows in listOf(emptyList(), listOf(row(), row(rate = "99")))) {
            dao.getter = { rows }
            assertUnpriced(repo.getToken(configuration()), "usd")
        }
    }

    @Test fun getStorageFailurePropagatesWithoutZeroOrEmptySuccess() = runBlocking {
        val failure = StorageFailure(Any())
        val dao = RawDao(); dao.getter = { throw failure }
        assertSame(failure, runCatching { repository(dao, Selection("usd")).getToken(configuration()) }.exceptionOrNull())
    }

    @Test fun getCancellationDoesNotReturnQuote() = runBlocking {
        val dao = RawDao(); val entered = CompletableDeferred<Unit>(); val closed = CompletableDeferred<Unit>()
        dao.getter = { try { entered.complete(Unit); awaitCancellation() } finally { closed.complete(Unit) } }
        val result = async { repository(dao, Selection("usd")).getToken(configuration()) }
        try {
            withTimeout(5000) { entered.await() }
            result.cancelAndJoin()
            withTimeout(5000) { closed.await() }
            assertTrue(result.isCancelled)
        } finally { result.cancelAndJoin() }
    }

    @Test fun observeEmitsPendingBeforeDelayedFirstQuoteAndUsesSymbolFreeBatchWrapper() = runBlocking {
        val dao = RawDao(); val input = Channel<List<RawCachedTokenPriceRow>>(Channel.UNLIMITED)
        dao.observer = { input.receiveAsFlow() }
        val output = Channel<Token>(Channel.UNLIMITED)
        val job = launch { repository(dao, Selection("cad")).observeToken(configuration()).collect { output.send(it) } }
        try {
            assertUnpriced(output.next(), "cad")
            input.send(listOf(row(currency = "cad")))
            val token = output.next()
            assertEquals(BigDecimal("2.50"), token.fiatRate)
            assertEquals("cad", token.fiatSymbol)
            assertEquals(listOf(listOf("catalog")), dao.observes)
        } finally { job.cancelAndJoin(); input.close(); output.close() }
    }

    @Test fun observeSameCatalogKeyCurrencyReplacementRevokesThenRejectsOldCurrency() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        val inputs = listOf(Channel<List<RawCachedTokenPriceRow>>(Channel.UNLIMITED), Channel<List<RawCachedTokenPriceRow>>(Channel.UNLIMITED))
        var subscription = 0; dao.observer = { inputs[subscription++].receiveAsFlow() }
        val output = Channel<Token>(Channel.UNLIMITED)
        val job = launch { repository(dao, selection).observeToken(configuration()).collect { output.send(it) } }
        try {
            assertUnpriced(output.next(), "usd")
            inputs[0].send(listOf(row())); assertEquals(BigDecimal("2.50"), output.next().fiatRate)
            selection.selected.set("cad")
            assertUnpriced(output.next(), "cad")
            inputs[1].send(listOf(row(currency = "usd", rate = "90")))
            assertUnpriced(output.next(), "cad")
            inputs[1].send(listOf(row(currency = "cad", rate = "3")))
            assertEquals(BigDecimal("3"), output.next().fiatRate)
            assertEquals(listOf(listOf("catalog"), listOf("catalog")), dao.observes)
        } finally { job.cancelAndJoin(); inputs.forEach { it.close() }; output.close() }
    }

    @Test fun observeSupportedProviderKeyChangesOnlyForExactUsd() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        dao.observer = { ids -> flowOf(listOf(row(currency = selection.selected.get(), priceId = ids.single()))) }
        val output = Channel<Token>(Channel.UNLIMITED)
        val asset = configuration(provider = Asset.PriceProvider("chainlink", Asset.PriceProviderType.Chainlink))
        val job = launch { repository(dao, selection).observeToken(asset).collect { output.send(it) } }
        try {
            assertUnpriced(output.next(), "usd"); assertEquals("chainlink", output.next().quotePriceId)
            selection.selected.set("eur")
            assertUnpriced(output.next(), "eur"); assertEquals("catalog", output.next().quotePriceId)
            assertEquals(listOf(listOf("chainlink"), listOf("catalog")), dao.observes)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun observeRevokesBeforeNonCancellableCleanupAndRejectsCleanupPublicationAfterAtoBtoA() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        val cleanupEntered = CompletableDeferred<Unit>(); val cleanupRelease = CompletableDeferred<Unit>()
        val staleAttempted = CompletableDeferred<Unit>(); var subscription = 0
        dao.observer = {
            when (subscription++) {
                0 -> object : Flow<List<RawCachedTokenPriceRow>> {
                    override suspend fun collect(collector: FlowCollector<List<RawCachedTokenPriceRow>>) {
                        collector.emit(listOf(row(rate = "2")))
                        try { awaitCancellation() } finally {
                            withContext(NonCancellable) {
                                cleanupEntered.complete(Unit)
                                cleanupRelease.await()
                                collector.emit(listOf(row(rate = "900")))
                                staleAttempted.complete(Unit)
                            }
                        }
                    }
                }
                else -> flowOf(listOf(row(currency = selection.selected.get(), rate = "4")))
            }
        }
        val output = Channel<Token>(Channel.UNLIMITED)
        val job = launch { repository(dao, selection).observeToken(configuration()).collect { output.send(it) } }
        try {
            assertUnpriced(output.next(), "usd"); assertEquals(BigDecimal("2"), output.next().fiatRate)
            selection.selected.set("eur")
            assertUnpriced(output.next(), "eur")
            withTimeout(5000) { cleanupEntered.await() }
            assertFalse(cleanupRelease.isCompleted)
            assertEquals(1, dao.observes.size)
            selection.selected.set("usd")
            cleanupRelease.complete(Unit)
            withTimeout(5000) { staleAttempted.await() }
            val afterCleanup = mutableListOf<Token>()
            withTimeout(5000) {
                while (afterCleanup.none { it.fiatRate == BigDecimal("4") }) afterCleanup += output.receive()
            }
            assertTrue(afterCleanup.none { it.fiatRate == BigDecimal("900") })
            assertTrue(afterCleanup.dropLast(1).any { it.fiatContext?.currencyId == "usd" && it.fiatRate == null })
            assertEquals("usd", afterCleanup.last().fiatContext?.currencyId)
            assertEquals(BigDecimal("4"), afterCleanup.last().fiatRate)
            assertTrue(output.tryReceive().isFailure)
        } finally { cleanupRelease.complete(Unit); job.cancelAndJoin(); output.close() }
    }

    @Test fun notifySyncFailedRevokesAndRestoresPreviousCapturedCurrency() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        dao.observer = { flowOf(listOf(row(currency = selection.selected.get()))) }
        val output = Channel<Token>(Channel.UNLIMITED)
        val job = launch { repository(dao, selection).observeToken(configuration()).collect { output.send(it) } }
        try {
            assertUnpriced(output.next(), "usd"); assertEquals("usd", output.next().quoteCurrencyId)
            selection.selected.set("eur")
            assertUnpriced(output.next(), "eur"); assertEquals("eur", output.next().quoteCurrencyId)
            selection.selected.notifySyncFailed()
            assertUnpriced(output.next(), "usd"); assertEquals("usd", output.next().quoteCurrencyId)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun observeLaterEmptyAndDamagedRowsWithdrawPreviouslyKnownQuote() = runBlocking {
        val dao = RawDao(); dao.observer = { flowOf(listOf(row()), emptyList(), listOf(row(rate = "broken", change = null))) }
        val results = withTimeout(5000) { repository(dao, Selection("usd")).observeToken(configuration()).take(4).toList() }
        assertEquals(BigDecimal("2.50"), results[1].fiatRate)
        assertUnpriced(results[2], "usd"); assertSame(AssetAmountState.Missing, results[2].fiatRateState)
        assertUnpriced(results[3], "usd"); assertTrue(results[3].fiatRateState is AssetAmountState.Unavailable)
    }

    @Test fun observeStorageFailurePropagatesAfterExplicitPendingWithoutSuccessValue() = runBlocking {
        val failure = StorageFailure(Any())
        val dao = RawDao(); dao.observer = { flow { throw failure } }
        val results = mutableListOf<Token>()
        val thrown = runCatching {
            withTimeout(5000) { repository(dao, Selection("usd")).observeToken(configuration()).collect { results += it } }
        }.exceptionOrNull()
        assertSame(failure, thrown)
        assertEquals(1, results.size)
        assertUnpriced(results.single(), "usd")
    }

    @Test fun observeCancellationClosesRawSubscription() = runBlocking {
        val dao = RawDao(); val entered = CompletableDeferred<Unit>(); val closed = CompletableDeferred<Unit>()
        dao.observer = { flow { try { entered.complete(Unit); awaitCancellation() } finally { closed.complete(Unit) } } }
        val job = launch { repository(dao, Selection("usd")).observeToken(configuration()).collect() }
        try {
            withTimeout(5000) { entered.await() }
            job.cancelAndJoin()
            withTimeout(5000) { closed.await() }
            assertTrue(job.isCancelled)
        } finally { job.cancelAndJoin() }
    }

    @Test fun replacementCannotPublishPastUnreceivedRevocation() = runBlocking {
        val selection = Selection("usd"); val dao = RawDao()
        dao.observer = { flowOf(listOf(row(currency = selection.selected.get()))) }
        val output = Channel<Token>(Channel.UNLIMITED); val hold = CompletableDeferred<Unit>()
        val blocked = CompletableDeferred<Unit>()
        val job = launch {
            repository(dao, selection).observeToken(configuration()).collect { token ->
                output.send(token)
                if (token.fiatRate != null && token.fiatContext?.currencyId == "usd") {
                    blocked.complete(Unit); hold.await()
                }
            }
        }
        try {
            assertUnpriced(output.next(), "usd"); assertEquals(BigDecimal("2.50"), output.next().fiatRate)
            withTimeout(5000) { blocked.await() }
            selection.selected.set("eur")
            yield()
            assertEquals(1, dao.observes.size)
            assertTrue(output.tryReceive().isFailure)
            hold.complete(Unit)
            assertUnpriced(output.next(), "eur"); assertEquals("eur", output.next().quoteCurrencyId)
        } finally { hold.complete(Unit); job.cancelAndJoin(); output.close() }
    }

    /** Extra instance state prevents coroutine stack-recovery copies from weakening identity checks. */
    private class StorageFailure(val marker: Any) : RuntimeException("controlled storage failure")

    private class Selection(initial: String) {
        private val selectedFiatValue = AtomicReference(initial)
        private val changes = Channel<String?>(Channel.UNLIMITED)
        private val preferences = mockk<Preferences> {
            every { getString("selectedFiat", "usd") } answers { selectedFiatValue.get() }
            every { putString("selectedFiat", any()) } answers {
                val selected = secondArg<String?>()
                requireNotNull(selected)
                selectedFiatValue.set(selected)
                changes.trySend(selected).getOrThrow()
                Unit
            }
            every { stringFlow("selectedFiat", any()) } answers {
                flow<String?> { emit(selectedFiatValue.get()); emitAll(changes.receiveAsFlow()) }
            }
        }
        val selected = SelectedFiat(preferences)
    }

    /** Legacy and writer entries fail; each test exercises the real bounded get/observe wrappers. */
    private inner class RawDao : TokenPriceDao() {
        val gets: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
        val observes: MutableList<List<String>> = Collections.synchronizedList(mutableListOf())
        var getter: suspend (List<String>) -> List<RawCachedTokenPriceRow> = { ids -> listOf(row(priceId = ids.single())) }
        var observer: (List<String>) -> Flow<List<RawCachedTokenPriceRow>> = { ids -> flowOf(listOf(row(priceId = ids.single()))) }
        override suspend fun getTokenPriceRowsByIds(priceIds: List<String>): List<RawCachedTokenPriceRow> {
            gets += priceIds.toList(); return getter(priceIds)
        }
        override fun observeTokenPriceRowsByIds(priceIds: List<String>): Flow<List<RawCachedTokenPriceRow>> {
            observes += priceIds.toList(); return observer(priceIds)
        }
        override suspend fun getTokenPriceRow(priceId: String): RawCachedTokenPriceRow? = error("legacy query called")
        override fun observeTokenPriceRow(priceId: String): Flow<RawCachedTokenPriceRow?> = error("legacy query called")
        override fun observePriceRows(symbol: String): Flow<List<RawCachedTokenPriceRow>> = error("symbol query called")
        override suspend fun insertTokenPrice(token: TokenPriceLocal): Unit = error("writer called")
        override suspend fun insertTokensPrice(tokens: List<TokenPriceLocal>): Unit = error("writer called")
        override suspend fun insertTokenPriceOrIgnore(token: TokenPriceLocal): Unit = error("writer called")
        override suspend fun updatePrices(priceId: String, fiatSymbol: String, fiatRate: BigDecimal): Unit = error("writer called")
    }
}
