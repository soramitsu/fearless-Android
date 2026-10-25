package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlan
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.async
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.util.UUID

class NativeSendQuoteStoreTest {
    @Test fun `same quote reuses one plan but changed wallet or amount cannot replace reviewed intent`() = runTest {
        val store = NativeSendQuoteStore(); val id = UUID.randomUUID().toString(); var calls = 0
        val quote = quote()
        assertEquals(quote, store.getOrCreate(id, binding) { calls++; quote })
        assertSame(quote, store.getOrCreate(id, binding) { error("replan") })
        assertSame(quote, store.requireQuote(id, binding)); assertEquals(1, calls)
        assertEquals(quote.review, store.reviews.value[id])
        for (changed in listOf(binding.copy(metaId = 2), binding.copy(requestedRawAmount = "2"), binding.copy(chainId = "testnet"))) {
            assertTrue(runCatching { store.getOrCreate(id, changed) { error("replace") } }.exceptionOrNull() is NativeSendException)
            assertThrows(NativeSendException::class.java) { store.requireQuote(id, changed) }
        }
    }

    @Test fun `restart missing quote and failed creation never produce a sendable review`() = runTest {
        val store = NativeSendQuoteStore(); val id = UUID.randomUUID().toString()
        assertThrows(NativeSendException::class.java) { store.requireQuote(id, binding) }
        assertTrue(runCatching { store.getOrCreate(id, binding) { throw java.io.IOException() } }.isFailure)
        assertTrue(store.reviews.value.isEmpty())
        assertThrows(NativeSendException::class.java) { store.requireQuote(null, binding) }
    }

    @Test fun `wallet A to B to A while quote is suspended rejects old revision despite identical identity`() = runTest {
        val store = NativeSendQuoteStore(); val id = UUID.randomUUID().toString()
        val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        val old = async { runCatching { store.getOrCreate(id, binding) { entered.complete(Unit); release.await(); quote() } } }
        entered.await()
        repeat(2) { WalletSelectionEpoch.beginChange(); WalletSelectionEpoch.endChange() }
        release.complete(Unit)
        assertEquals(NativeSendException.Code.WALLET_CHANGED, (old.await().exceptionOrNull() as NativeSendException).code)
        assertTrue(store.reviews.value.isEmpty())
        val current = binding.copy(selectionEpoch = WalletSelectionEpoch.snapshot())
        assertEquals(current, store.getOrCreate(id, current) { quote().copy(binding = current) }.binding)
    }

    @Test fun `amount changes away and returns before canceled request completes cannot publish stale quote`() = runTest {
        val store = NativeSendQuoteStore(); val id = UUID.randomUUID().toString()
        val release = CompletableDeferred<Unit>(); val entered = CompletableDeferred<Unit>()
        val old = async { store.getOrCreate(id, binding) {
            withContext(NonCancellable) { entered.complete(Unit); release.await() }
            quote()
        } }
        entered.await(); old.cancel()
        // Simulates the user entering another amount, then the original amount while old I/O ignores cancellation.
        release.complete(Unit); old.join()
        assertTrue(store.reviews.value.isEmpty())
        val refreshed = quote().copy(review = NativeSendReview(BigDecimal.ONE, BigDecimal("20")))
        assertEquals(refreshed, store.getOrCreate(id, binding) { refreshed })
        assertEquals(BigDecimal("20"), store.reviews.value[id]?.networkFee)
    }

    @Test fun `both original registry and explicit execution genesis bind reviewed quote identity`() = runTest {
        val network = jp.co.soramitsu.common.data.network.send.SolanaSendNetworkIdentity(
            jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH,
            jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_MAINNET_GENESIS_HASH)
        val solana = binding.copy(solanaNetwork = network)
        val store = NativeSendQuoteStore(); val id = UUID.randomUUID().toString()
        store.getOrCreate(id, solana) { quote().copy(binding = solana) }
        for (changed in listOf(solana.copy(solanaNetwork = null),
            solana.copy(solanaNetwork = network.copy(registryGenesisHash = jp.co.soramitsu.common.model.UniversalWalletRegistry.SOLANA_DEVNET_GENESIS_HASH)),
            solana.copy(solanaNetwork = network.copy(executionGenesisHash = "EdP1HZdRNKaMDpiNM4suXQb4RsGZK5sCeZoPQYoJvRTL")))) {
            assertNotEquals(solana.fingerprint, changed.fingerprint)
            assertThrows(NativeSendException::class.java) { store.requireQuote(id, changed) }
        }
    }

    private fun quote() = NativeSendQuoteStore.Quote.Bitcoin(binding, NativeSendReview(BigDecimal.ONE, BigDecimal.TEN),
        BitcoinSendPlan(1, "recipient", "change", 2.0, 2, false, BitcoinIndexerRoutes.Network.Mainnet,
            listOf("sender"), emptyList(), 100, 10, 89, 0), "m/84'/0'/0'/1/0", null)
    private val binding = NativeSendBinding(1, "bitcoin", "btc", null, 8, "sender", "recipient", "1", false)
}
