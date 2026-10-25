package jp.co.soramitsu.wallet.impl.data.repository.tranfser

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinSendPlan
import jp.co.soramitsu.common.data.network.solana.SolanaPaymentQuote
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** Process-only reviewed plans contain public transaction data, never retained signing material. */
@Singleton
class NativeSendQuoteStore @Inject constructor() {
    private val mutex = Mutex()
    private val quotes = ConcurrentHashMap<String, Quote>()
    private val mutableReviews = MutableStateFlow<Map<String, NativeSendReview>>(emptyMap())
    val reviews: StateFlow<Map<String, NativeSendReview>> = mutableReviews

    /** Repeated fee subscribers reuse one quote; a changed wallet or intent cannot overwrite it. */
    suspend fun getOrCreate(id: String, binding: NativeSendBinding, create: suspend () -> Quote): Quote = mutex.withLock {
        require(UUID.fromString(id).toString() == id)
        quotes[id]?.let { return@withLock requireBinding(it, binding) }
        val quote = requireBinding(create(), binding)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(binding.selectionEpoch)) {
            throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        }
        if (quotes.size >= 64) {
            val oldest = quotes.keys.first()
            quotes.remove(oldest)
            mutableReviews.update { it - oldest }
        }
        quotes[id] = quote
        mutableReviews.update { it + (id to quote.review) }
        quote
    }

    /** Sending requires the quote already displayed. It never re-plans on a missing process cache. */
    fun requireQuote(id: String?, binding: NativeSendBinding): Quote =
        quotes[id ?: throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)]
            ?.let { requireBinding(it, binding) } ?: throw NativeSendException(NativeSendException.Code.REVIEW_REQUIRED)

    private fun requireBinding(quote: Quote, binding: NativeSendBinding): Quote {
        if (quote.binding.fingerprint != binding.fingerprint || quote.binding.selectionEpoch != binding.selectionEpoch) throw NativeSendException(NativeSendException.Code.WALLET_CHANGED)
        return quote
    }

    sealed interface Quote {
        val binding: NativeSendBinding
        val review: NativeSendReview

        data class Bitcoin(override val binding: NativeSendBinding, override val review: NativeSendReview,
            val plan: BitcoinSendPlan, val changePath: String, val baseUrl: String?) : Quote

        data class Solana(override val binding: NativeSendBinding, override val review: NativeSendReview,
            val plan: SolanaPaymentQuote, val rpcUrls: List<String>) : Quote
    }
}
