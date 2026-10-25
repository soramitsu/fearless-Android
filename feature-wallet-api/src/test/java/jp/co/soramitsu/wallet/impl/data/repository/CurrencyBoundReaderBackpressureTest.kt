package jp.co.soramitsu.wallet.impl.data.repository

import io.mockk.every
import io.mockk.mockk
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.cache.CachedFiatCurrencyIdentity
import jp.co.soramitsu.coredb.model.cache.CachedNumericState
import jp.co.soramitsu.coredb.model.cache.CachedTokenPriceRead
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.data.mappers.CachedMapperFixtures
import jp.co.soramitsu.wallet.impl.data.mappers.toDomainAssets
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class CurrencyBoundReaderBackpressureTest {
    private suspend fun <T> Channel<T>.next(): T = withTimeout(5000) { receive() }

    /**
     * A single runBlocking event loop and explicit handshakes control the interleaving; no sleeps.
     * The quote coroutine completes beforeOldEmit without suspension, then runs until its emit
     * suspends (or returns under the old two-channel implementation). Cancellation acknowledgement
     * proves the new catalog was processed before the public collector is released.
     */
    @Test fun catalogReplacementCancelsOldPricedPublicSendWhileConsumerIsPaused() = runBlocking {
        val meta = CachedMapperFixtures.meta()
        val originalChain = CachedMapperFixtures.chain(version = "catalog-A")
        val replacementConfig = CachedMapperFixtures.config(
            precision = 3,
            provider = Asset.PriceProvider("replacement-price", Asset.PriceProviderType.Chainlink)
        )
        val replacementChain = CachedMapperFixtures.chain(configs = listOf(replacementConfig), version = "catalog-B")
        val catalog = MutableStateFlow(mapOf("chain" to originalChain))
        val balances = flowOf(listOf(CachedMapperFixtures.raw().decode().asset))
        val beforeOldEmit = CompletableDeferred<Unit>()
        val oldCancelled = CompletableDeferred<Unit>()
        val consumerPaused = CompletableDeferred<Unit>()
        val consumerRelease = CompletableDeferred<Unit>()
        val requestedIds = mutableListOf<List<String>>()
        val accounts = mockk<AccountRepository>()
        val selectedFiat = mockk<SelectedFiat> {
            every { flow() } returns flowOf("usd")
        }
        val chains = mockk<ChainsRepository> {
            every { chainsByIdFlow() } returns catalog
        }
        val assetDao = mockk<AssetDao> {
            every { observePortfolioBalances(meta.id) } returns balances
        }
        val oldQuote = CachedTokenPriceRead(
            "provider-price", "ignored", CachedNumericState.Known(BigDecimal("900")),
            CachedNumericState.Missing, CachedFiatCurrencyIdentity.Known("usd")
        )
        val newQuote = oldQuote.copy(priceId = "replacement-price", fiatRate = CachedNumericState.Known(BigDecimal("4")))
        val priceDao = mockk<TokenPriceDao> {
            every { observeTokenPricesByIds(any()) } answers {
                val ids = firstArg<List<String>>().toList()
                requestedIds += ids
                when (ids) {
                    listOf("provider-price") -> flow {
                        try {
                            beforeOldEmit.complete(Unit)
                            emit(listOf(oldQuote))
                            awaitCancellation()
                        } finally {
                            oldCancelled.complete(Unit)
                        }
                    }
                    listOf("replacement-price") -> flowOf(listOf(newQuote))
                    else -> error("Unexpected quote keys: $ids")
                }
            }
        }
        val reader = CurrentAccountAssetReader(assetDao, chains, accounts, priceDao, selectedFiat)
        val output = Channel<SelectedAccountAssetSnapshot>(Channel.UNLIMITED)
        var heldFirstCrypto = false
        val job = launch {
            reader.observeSnapshots(meta).collect { state ->
                output.send(state)
                if (!heldFirstCrypto && state is SelectedAccountAssetSnapshot.Ready && !state.snapshot.quotesReady) {
                    heldFirstCrypto = true
                    consumerPaused.complete(Unit)
                    consumerRelease.await()
                }
            }
        }
        try {
            assertSame(SelectedAccountAssetSnapshot.AwaitingSelection, output.next())
            assertTrue(output.next() is SelectedAccountAssetSnapshot.Loading)
            val first = (output.next() as SelectedAccountAssetSnapshot.Ready).snapshot
            assertFalse(first.quotesReady)
            assertSame(originalChain, first.chainsById["chain"])
            withTimeout(5000) { consumerPaused.await(); beforeOldEmit.await() }

            catalog.value = mapOf("chain" to replacementChain)
            withTimeout(5000) { oldCancelled.await() }
            assertFalse(consumerRelease.isCompleted)
            assertTrue(output.tryReceive().isFailure)
            consumerRelease.complete(Unit)

            val pending = (output.next() as SelectedAccountAssetSnapshot.Ready).snapshot
            assertSame(replacementChain, pending.chainsById["chain"])
            assertFalse(pending.quotesReady)
            assertNull(pending.toDomainAssets { true }.single().fiatAmount)
            val priced = (output.next() as SelectedAccountAssetSnapshot.Ready).snapshot
            assertSame(replacementChain, priced.chainsById["chain"])
            assertTrue(priced.quotesReady)
            assertEquals(BigDecimal("4"), priced.toDomainAssets { true }.single().token.fiatRate)
            assertEquals(BigDecimal("1.230"), priced.toDomainAssets { true }.single().total)
            assertEquals(listOf(listOf("provider-price"), listOf("replacement-price")), requestedIds)
            assertTrue(output.tryReceive().isFailure)
        } finally {
            consumerRelease.complete(Unit)
            job.cancelAndJoin()
            output.close()
        }
    }
}
