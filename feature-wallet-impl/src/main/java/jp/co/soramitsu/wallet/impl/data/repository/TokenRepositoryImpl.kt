package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.runtime.multiNetwork.tokengroups.PortfolioVisibility
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.FiatValuationContext
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.expectedFiatPriceId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

val supportedPriceProviders = listOf(Asset.PriceProviderType.Chainlink)
val Asset.PriceProvider.isSupported
    get() = this.type in supportedPriceProviders

class TokenRepositoryImpl(
    private val tokenPriceDao: TokenPriceDao,
    private val selectedFiat: SelectedFiat,
    private val portfolioVisibility: PortfolioVisibility
) : TokenRepository {

    /** This one-shot result retains the selection captured before IO, even if selection changes. */
    override suspend fun getToken(chainAsset: Asset): Token {
        val context = FiatValuationContext(selectedFiat.get())
        return withContext(Dispatchers.Default) {
            val priceId = expectedFiatPriceId(chainAsset, context.currencyId).takeIf { context.isValid && !it.isNullOrBlank() }
            val price = priceId?.let { tokenPriceDao.getTokenPricesByIds(listOf(it)).singleOrNull() }
            configuredFiatToken(chainAsset, price, hasTrustedPrice(chainAsset), context)
        }
    }

    /**
     * Revoke the prior quote on each delivered selection before waiting for its cancellation
     * cleanup. The zero-capacity handoff admits no replacement until revocation is received.
     * Keep the original publisher Job: a DAO's NonCancellable cleanup must not regain authority.
     * Downstream buffers and selection events not delivered by SelectedFiat remain caller concerns.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeToken(chainAsset: Asset): Flow<Token> = channelFlow {
        val generation = AtomicLong()
        var observation: Job? = null
        selectedFiat.flow().collect { currencyId ->
            val revision = generation.incrementAndGet()
            val context = FiatValuationContext(currencyId)
            val previous = observation
            previous?.cancel()
            send(configuredFiatToken(chainAsset, null, hasTrustedPrice(chainAsset), context))
            previous?.join()
            val priceId = expectedFiatPriceId(chainAsset, currencyId).takeIf { context.isValid && !it.isNullOrBlank() }
            observation = if (priceId == null) null else launch {
                val publisherJob = currentCoroutineContext().job
                tokenPriceDao.observeTokenPricesByIds(listOf(priceId)).collect { prices ->
                    val token = configuredFiatToken(chainAsset, prices.singleOrNull(), hasTrustedPrice(chainAsset), context)
                    if (generation.get() == revision && publisherJob.isActive) {
                        send(token)
                    }
                }
            }
        }
    }.buffer(0)

    private fun hasTrustedPrice(chainAsset: Asset): Boolean = portfolioVisibility.hasTrustedPrice(chainAsset)
}
