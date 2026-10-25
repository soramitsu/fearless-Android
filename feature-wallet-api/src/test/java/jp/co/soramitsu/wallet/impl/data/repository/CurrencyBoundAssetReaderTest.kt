package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.*
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.cache.*
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.data.mappers.*
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.a
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.b
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.meta
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.config
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.chain
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures.raw
import jp.co.soramitsu.wallet.impl.domain.model.AssetAmountState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

/** Reader boundary tests. DAO batch/decode correctness is separately exercised by the QD suite. */
@OptIn(InternalCoroutinesApi::class)
class CurrencyBoundAssetReaderTest {
    private suspend fun <T> Channel<T>.next(): T = withTimeout(5000) { receive() }
    private suspend fun Channel<SelectedAccountAssetSnapshot>.loading() = next() as SelectedAccountAssetSnapshot.Loading
    private suspend fun Channel<SelectedAccountAssetSnapshot>.ready() = (next() as SelectedAccountAssetSnapshot.Ready).snapshot
    private fun quote(id: String = "provider-price", currency: String = "usd", rate: String = "2.50") =
        CachedTokenPriceRead(id, "UNTRUSTED SYMBOL", CachedNumericState.Known(BigDecimal(rate)),
            CachedNumericState.Known(BigDecimal("-0.125")), CachedFiatCurrencyIdentity.Known(currency))

    private class Fixture {
        var selection = "usd"
        var account = meta()
        val selected = Channel<MetaAccount?>(Channel.UNLIMITED)
        val fiatEvents = Channel<String>(Channel.UNLIMITED)
        val catalog = MutableStateFlow(mapOf("chain" to chain()))
        val balances = MutableStateFlow(listOf(raw().decode().asset))
        val gets = mutableListOf<List<String>>()
        val observes = mutableListOf<List<String>>()
        var getter: suspend (List<String>) -> List<CachedTokenPriceRead> = { emptyList() }
        var observer: (List<String>) -> Flow<List<CachedTokenPriceRead>> = { emptyFlow() }
        var catalogObserver: () -> Flow<Map<String, Chain>> = { catalog }
        val accounts = mockk<AccountRepository> {
            coEvery { getMetaAccount(7) } answers { account }
            every { selectedMetaAccountStateFlow() } answers { selected.receiveAsFlow() }
        }
        val fiat = mockk<SelectedFiat> {
            every { get() } answers { selection }
            every { flow() } answers { flow { emit(selection); emitAll(fiatEvents.receiveAsFlow()) } }
        }
        val chains = mockk<ChainsRepository> {
            coEvery { getChainsById() } answers { catalog.value }
            every { chainsByIdFlow() } answers { catalogObserver() }
        }
        val assets = mockk<AssetDao> {
            coEvery { getPortfolioBalances(7) } answers { balances.value }
            every { observePortfolioBalances(7) } returns balances
        }
        val prices = mockk<TokenPriceDao> {
            coEvery { getTokenPricesByIds(any()) } coAnswers {
                val ids = firstArg<List<String>>().toList(); gets += ids; getter(ids)
            }
            every { observeTokenPricesByIds(any()) } answers {
                val ids = firstArg<List<String>>().toList(); observes += ids; observer(ids)
            }
        }
        val reader get() = CurrentAccountAssetReader(assets, chains, accounts, prices, fiat)
        suspend fun selectFiat(value: String) { selection = value; fiatEvents.send(value) }
    }

    @Test fun oneShotUsesConfiguredUsdProviderAndIgnoresStoredBalancePointerAndSymbol() = runBlocking {
        val f = Fixture(); f.balances.value = listOf(raw(storedPriceId = "stale-pointer").decode().asset)
        f.getter = { listOf(quote()) }
        val snapshot = f.reader.getSnapshot(7); val asset = snapshot.toDomainAssets { true }.single()
        assertEquals(listOf(listOf("provider-price")), f.gets)
        assertTrue(snapshot.quotesReady); assertEquals("usd", snapshot.context.fiat.currencyId)
        assertEquals("usd", asset.token.fiatSymbol); assertEquals("provider-price", asset.token.quotePriceId)
        assertEquals(BigDecimal("12.30"), asset.total); assertEquals(BigDecimal("30.7500"), asset.fiatAmount)
        assertArrayEquals(a, asset.accountId); assertEquals(false, asset.enabled)
        assertEquals(17, asset.sortIndex); assertEquals("retained-name", asset.chainAccountName)
    }

    @Test fun nonUsdChoosesCatalogEvenWhenStoredPointerNamesProvider() = runBlocking {
        val f = Fixture(); f.selection = "eur"; f.getter = { listOf(quote("catalog-price", "eur")) }
        val asset = f.reader.getSnapshot(7).toDomainAssets { true }.single()
        assertEquals(listOf(listOf("catalog-price")), f.gets)
        assertEquals("eur", asset.token.fiatSymbol); assertEquals(BigDecimal("2.50"), asset.token.fiatRate)
    }

    @Test fun capturedOneShotKeepsHistoricalUnitAcrossSelectionChangeDuringIo() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        coEvery { f.accounts.getMetaAccount(7) } coAnswers { entered.complete(Unit); release.await(); f.account }
        f.getter = { listOf(quote()) }
        val result = async { f.reader.getSnapshot(7) }
        try {
            withTimeout(5000) { entered.await() }; f.selection = "eur"; release.complete(Unit)
            val snapshot = withTimeout(5000) { result.await() }
            assertEquals("usd", snapshot.context.fiat.currencyId); assertEquals("eur", f.fiat.get())
            assertEquals("usd", snapshot.toDomainAssets { true }.single().token.fiatSymbol)
            assertEquals(listOf(listOf("provider-price")), f.gets)
        } finally { release.complete(Unit); result.cancelAndJoin() }
    }

    @Test fun damagedExactRowWinsOverFallbackAndKeepsHealthySiblingAndPreferences() = runBlocking {
        val f = Fixture(); f.balances.value = listOf(
            raw(account = byteArrayOf(), free = "9999", enabled = true), raw(free = "broken"),
            raw(id = "sibling"), raw(account = b), raw(meta = 8), raw(chain = "unknown")
        ).map { it.decode().asset }
        val assets = f.reader.getSnapshot(7).toDomainAssets { true }
        assertEquals(listOf("asset", "sibling"), assets.map { it.id })
        assertTrue(assets[0].freeInPlanksState is AssetAmountState.Unavailable)
        assertNull(assets[0].total); assertEquals(false, assets[0].enabled)
        assertEquals(BigDecimal("12.30"), assets[1].total)
        assertEquals(listOf(listOf("provider-price")), f.gets)
    }

    @Test fun missingAccountCannotBorrowForeignExactRowsOrMissingCatalogAssets() = runBlocking {
        val f = Fixture(); f.account = meta(substrate = null)
        f.catalog.value = mapOf("chain" to chain(configs = listOf(config())))
        f.balances.value = listOf(raw(account = b), raw(account = byteArrayOf()), raw(id = "sibling"))
            .map { it.decode().asset }
        val asset = f.reader.getSnapshot(7).toDomainAssets { true }.single()
        assertTrue(asset.accountId.isEmpty()); assertEquals("asset", asset.id)
    }

    @Test fun catalogIsCapturedBeforeQuoteIoAndMappingDoesNotReadItAgain() = runBlocking {
        val f = Fixture(); val old = f.catalog.value.getValue("chain")
        val mutable = f.catalog.value.toMutableMap()
        coEvery { f.chains.getChainsById() } returns mutable
        f.getter = { mutable.clear(); listOf(quote()) }
        val snapshot = f.reader.getSnapshot(7)
        assertSame(old, snapshot.chainsById["chain"])
        assertEquals(BigDecimal("12.30"), snapshot.toDomainAssets { true }.single().total)
        coVerify(exactly = 1) { f.chains.getChainsById() }
        verify(exactly = 0) { f.chains.chainsByIdFlow() }
    }

    @Test fun emptyCompletedPortfolioDelegatesEmptyBoundedQuoteRequest() = runBlocking {
        val f = Fixture(); f.balances.value = emptyList()
        val snapshot = f.reader.getSnapshot(7)
        assertTrue(snapshot.quotesReady); assertTrue(snapshot.assets.isEmpty())
        assertEquals(listOf(emptyList<String>()), f.gets)
        assertEquals("usd", snapshot.context.fiat.currencyId)
    }

    @Test fun foreignCurrencyCannotBorrowSameSymbolAndUntrustedQuoteStaysUnavailable() = runBlocking {
        val f = Fixture(); f.selection = "cad"; f.getter = { listOf(quote("catalog-price", "usd")) }
        val snapshot = f.reader.getSnapshot(7)
        val asset = snapshot.toDomainAssets { true }.single()
        assertEquals(BigDecimal("12.30"), asset.total); assertNull(asset.fiatAmount)
        assertEquals("cad", asset.token.fiatSymbol)
        assertEquals(AssetAmountState.Unavailable("cached-price-currency-mismatch"), asset.token.fiatRateState)
        assertSame(AssetAmountState.Missing, snapshot.toDomainAssets { false }.single().token.fiatRateState)
    }

    @Test fun initialNoAccountAndColdCatalogRemainAwaitingOrLoadingNotEmptyReady() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>()
        f.catalogObserver = { flow { entered.complete(Unit); awaitCancellation() } }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSelectedSnapshots().collect { output.send(it) } }
        try {
            assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, output.next())
            f.selected.send(null); assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, output.next())
            f.selected.send(f.account); assertSame(f.account, output.loading().context.meta)
            withTimeout(5000) { entered.await() }
            assertTrue(output.tryReceive().isFailure); assertTrue(f.observes.isEmpty())
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun fixedAccountPublishesCryptoBeforeHeldQuotesThenBindsExactCurrency() = runBlocking {
        val f = Fixture(); val quotes = Channel<List<CachedTokenPriceRead>>(Channel.UNLIMITED)
        f.observer = { quotes.receiveAsFlow() }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, output.next()); val loading = output.loading()
            val crypto = output.ready(); assertSame(loading.context, crypto.context); assertFalse(crypto.quotesReady)
            val asset = crypto.toDomainAssets { true }.single()
            assertEquals(BigDecimal("12.30"), asset.total); assertNull(asset.fiatAmount)
            quotes.send(listOf(quote())); val priced = output.ready()
            assertTrue(priced.quotesReady); assertSame(crypto.context, priced.context)
            assertEquals(BigDecimal("30.7500"), priced.toDomainAssets { true }.single().fiatAmount)
        } finally { job.cancelAndJoin(); quotes.close(); output.close() }
    }

    @Test fun accountRemovalRevokesReadyAndClosesQuoteSubscription() = runBlocking {
        val f = Fixture(); val closed = CompletableDeferred<Unit>()
        f.observer = { flow { try { emit(listOf(quote())); awaitCancellation() } finally { closed.complete(Unit) } } }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSelectedSnapshots().collect { output.send(it) } }
        try {
            output.next(); f.selected.send(f.account); output.loading(); output.ready(); assertTrue(output.ready().quotesReady)
            f.selected.send(null); assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, output.next())
            withTimeout(5000) { closed.await() }; assertTrue(output.tryReceive().isFailure)
            assertEquals(1, f.observes.size)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun equalTonAccountReplacementGetsNewContextAndExactOwnedBalance() = runBlocking {
        val f = Fixture(); val first = meta(ton = a); val second = meta(ton = b)
        assertEquals(first, second)
        f.catalog.value = mapOf("chain" to chain(ecosystem = Ecosystem.Ton))
        f.balances.value = listOf(raw(account = a), raw(account = b, free = "2300"), raw(account = byteArrayOf()))
            .map { it.decode().asset }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSelectedSnapshots().collect { output.send(it) } }
        try {
            output.next(); f.selected.send(first); val old = output.loading(); val oldReady = output.ready()
            assertArrayEquals(a, oldReady.toDomainAssets { true }.single().accountId)
            f.selected.send(second); val next = output.loading(); val ready = output.ready()
            assertNotSame(old.context, next.context); assertSame(second, ready.context.meta)
            assertArrayEquals(b, ready.toDomainAssets { true }.single().accountId)
            assertEquals(BigDecimal("23.30"), ready.toDomainAssets { true }.single().total)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun deliveredUsdEurUsdSelectionsNeverReuseContextIdentityOrQuoteKey() = runBlocking {
        val f = Fixture(); val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            output.next(); val first = output.loading(); output.ready()
            withTimeout(5000) { while (f.observes.size < 1) yield() }
            f.selectFiat("eur"); val second = output.loading(); output.ready()
            withTimeout(5000) { while (f.observes.size < 2) yield() }
            f.selectFiat("usd"); val third = output.loading(); output.ready()
            assertNotSame(first.context, second.context); assertNotSame(first.context, third.context)
            assertEquals(listOf("usd", "eur", "usd"), listOf(first, second, third).map { it.context.fiat.currencyId })
            // Request creation may follow the rendezvous; wait for the third observer entry explicitly.
            withTimeout(5000) { while (f.observes.size < 3) yield() }
            assertEquals(listOf(listOf("provider-price"), listOf("catalog-price"), listOf("provider-price")), f.observes)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun currencyRevocationPrecedesBlockedCleanupAndRejectsOldCleanupEmission() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val attempted = CompletableDeferred<Unit>(); var count = 0
        f.observer = {
            if (count++ == 0) object : Flow<List<CachedTokenPriceRead>> {
                override suspend fun collect(collector: FlowCollector<List<CachedTokenPriceRead>>) {
                    collector.emit(listOf(quote()))
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) {
                            entered.complete(Unit); release.await()
                            collector.emit(listOf(quote(rate = "900"))); attempted.complete(Unit)
                        }
                    }
                }
            } else flowOf(listOf(quote("catalog-price", "eur", "3")))
        }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            output.next(); output.loading(); output.ready(); assertTrue(output.ready().quotesReady)
            f.selectFiat("eur"); assertEquals("eur", output.loading().context.fiat.currencyId)
            withTimeout(5000) { entered.await() }; assertFalse(release.isCompleted)
            assertTrue(output.tryReceive().isFailure); assertEquals(1, f.observes.size)
            release.complete(Unit); withTimeout(5000) { attempted.await() }
            val unpriced = output.ready(); assertFalse(unpriced.quotesReady)
            val ready = output.ready(); assertEquals("eur", ready.context.fiat.currencyId)
            assertEquals(BigDecimal("3"), ready.toDomainAssets { true }.single().token.fiatRate)
            assertTrue(output.tryReceive().isFailure)
        } finally { release.complete(Unit); job.cancelAndJoin(); output.close() }
    }

    @Test fun catalogReplacementRevokesQuotesBeforeCleanupAndUsesCapturedNewPrecision() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val release = CompletableDeferred<Unit>()
        val attempted = CompletableDeferred<Unit>(); var count = 0
        f.observer = {
            if (count++ == 0) object : Flow<List<CachedTokenPriceRead>> {
                override suspend fun collect(collector: FlowCollector<List<CachedTokenPriceRead>>) {
                    collector.emit(listOf(quote()))
                    try { awaitCancellation() } finally {
                        withContext(NonCancellable) {
                            entered.complete(Unit); release.await()
                            collector.emit(listOf(quote(rate = "900"))); attempted.complete(Unit)
                        }
                    }
                }
            } else flowOf(listOf(quote("new-provider", rate = "4")))
        }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            output.next(); output.loading(); output.ready(); val old = output.ready()
            val newConfig = config(precision = 3, provider = jp.co.soramitsu.core.models.Asset.PriceProvider(
                "new-provider", jp.co.soramitsu.core.models.Asset.PriceProviderType.Chainlink))
            f.catalog.value = mapOf("chain" to chain(configs = listOf(newConfig), version = "new"))
            val pending = output.ready(); assertFalse(pending.quotesReady); assertSame(old.context, pending.context)
            assertEquals(BigDecimal("1.230"), pending.toDomainAssets { true }.single().total)
            assertNull(pending.toDomainAssets { true }.single().fiatAmount)
            withTimeout(5000) { entered.await() }; assertEquals(1, f.observes.size)
            release.complete(Unit); withTimeout(5000) { attempted.await() }
            val new = output.ready(); assertTrue(new.quotesReady)
            assertEquals(BigDecimal("4"), new.toDomainAssets { true }.single().token.fiatRate)
            assertEquals(listOf("new-provider"), f.observes.last())
            assertEquals(BigDecimal("12.30"), old.toDomainAssets { true }.single().total)
            assertTrue(output.tryReceive().isFailure)
        } finally { release.complete(Unit); job.cancelAndJoin(); output.close() }
    }

    @Test fun oneShotStorageFailuresPropagateWithoutFabricatingEmptySuccess() = runBlocking {
        val f = Fixture(); val failure = IllegalStateException("controlled storage failure")
        coEvery { f.assets.getPortfolioBalances(7) } throws failure
        assertSame(failure, runCatching { f.reader.getSnapshot(7) }.exceptionOrNull())
        assertTrue(f.gets.isEmpty())
    }

    @Test fun observedQuoteFailureTerminatesWithoutPricedOrEmptySuccess() = runBlocking {
        val f = Fixture(); val failure = QuoteFailure(Any())
        f.observer = { flow { throw failure } }
        val output = mutableListOf<SelectedAccountAssetSnapshot>()
        val actual = runCatching { withTimeout(5000) { f.reader.observeSnapshots(f.account).collect { output += it } } }.exceptionOrNull()
        assertSame(failure, actual)
        assertTrue(output.first() === SelectedAccountAssetSnapshot.AwaitingSelection)
        assertTrue(output[1] is SelectedAccountAssetSnapshot.Loading)
        assertEquals(1, output.filterIsInstance<SelectedAccountAssetSnapshot.Ready>().size)
        assertTrue(output.filterIsInstance<SelectedAccountAssetSnapshot.Ready>().all {
            !it.snapshot.quotesReady && it.snapshot.assets.isNotEmpty() && it.snapshot.toDomainAssets { true }.single().fiatAmount == null
        })
    }

    @Test fun cancellationClosesActiveQuoteSubscriptionWithoutSuccessAfterCancel() = runBlocking {
        val f = Fixture(); val entered = CompletableDeferred<Unit>(); val closed = CompletableDeferred<Unit>()
        f.observer = { flow { try { entered.complete(Unit); awaitCancellation() } finally { closed.complete(Unit) } } }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            output.next(); output.loading(); output.ready(); withTimeout(5000) { entered.await() }
            job.cancelAndJoin(); withTimeout(5000) { closed.await() }
            assertTrue(job.isCancelled); assertTrue(output.tryReceive().isFailure)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun blankConfiguredKeysAndInvalidCurrencyCannotAuthorizeStoredQuote() = runBlocking {
        for ((currency, key) in listOf("usd" to " ", " " to "catalog-price")) {
            val f = Fixture(); f.selection = currency
            f.catalog.value = mapOf("chain" to chain(configs = listOf(config(priceId = key, provider = null))))
            f.getter = { listOf(quote(key, currency)) }
            val asset = f.reader.getSnapshot(7).toDomainAssets { true }.single()
            assertEquals(listOf(emptyList<String>()), f.gets)
            assertNull(asset.token.fiatRate); assertEquals(BigDecimal("12.30"), asset.total)
        }
    }

    @Test fun sameWalletChainOverrideRevokesAndSelectsExactNewAccount() = runBlocking {
        val f = Fixture(); val c = f.catalog.value.getValue("chain")
        val changed = meta(overrides = mapOf(c.id to MetaAccount.ChainAccount(
            7, c, byteArrayOf(1), b, jp.co.soramitsu.core.models.CryptoType.SR25519, "override")))
        f.balances.value = listOf(raw(), raw(account = b, enabled = true), raw(account = byteArrayOf()))
            .map { it.decode().asset }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSelectedSnapshots().collect { output.send(it) } }
        try {
            output.next(); f.selected.send(f.account); val old = output.loading(); output.ready()
            f.selected.send(changed); val next = output.loading(); val snapshot = output.ready()
            assertNotSame(old.context, next.context); assertSame(next.context, snapshot.context)
            val asset = snapshot.toDomainAssets { true }.single()
            assertArrayEquals(b, asset.accountId); assertEquals(true, asset.enabled)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun catalogWithdrawalAndRecoveryKeepDamagedExactRowWithoutFallbackPromotion() = runBlocking {
        val f = Fixture(); val c = f.catalog.value
        f.balances.value = listOf(raw(free = "broken"), raw(account = byteArrayOf(), free = "9999"))
            .map { it.decode().asset }
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        val job = launch { f.reader.observeSnapshots(f.account).collect { output.send(it) } }
        try {
            output.next(); output.loading(); val old = output.ready()
            assertTrue(old.toDomainAssets { true }.single().freeInPlanksState is AssetAmountState.Unavailable)
            f.catalog.value = emptyMap(); val empty = output.ready()
            assertSame(old.context, empty.context); assertTrue(empty.assets.isEmpty())
            f.catalog.value = c; val restored = output.ready()
            assertSame(old.context, restored.context)
            assertTrue(restored.toDomainAssets { true }.single().freeInPlanksState is AssetAmountState.Unavailable)
            assertArrayEquals(a, restored.toDomainAssets { true }.single().accountId)
        } finally { job.cancelAndJoin(); output.close() }
    }

    @Test fun wrongWalletResponseCannotRelabelRequestedRows() = runBlocking {
        val f = Fixture(); f.account = meta(id = 8)
        val snapshot = f.reader.getSnapshot(7)
        assertTrue(snapshot.assets.isEmpty()); assertTrue(snapshot.toDomainAssets { true }.isEmpty())
        assertEquals(listOf(emptyList<String>()), f.gets)
    }

    /** Instance state prevents coroutine stack-recovery reconstruction of the identity probe. */
    private class QuoteFailure(val marker: Any) : RuntimeException("controlled quote failure")

}
