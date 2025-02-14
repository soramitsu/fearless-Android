package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.wallet.impl.data.mappers.combineAssetWithPrices
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

val supportedPriceProviders = listOf(Asset.PriceProviderType.Chainlink)
val Asset.PriceProvider.isSupported
    get() = this.type in supportedPriceProviders

class TokenRepositoryImpl(
    private val tokenPriceDao: TokenPriceDao,
    private val selectedFiat: SelectedFiat
) : TokenRepository {

    override suspend fun getToken(chainAsset: Asset): Token = withContext(Dispatchers.Default) {
        val priceId = if (selectedFiat.isUsd() && chainAsset.priceProvider?.isSupported == true) {
            chainAsset.priceProvider?.id
        } else {
            chainAsset.priceId
        }
        val tokenPriceLocal = priceId?.let { tokenPriceDao.getTokenPrice(it) }

        combineAssetWithPrices(chainAsset, tokenPriceLocal)
    }

    override fun observeToken(chainAsset: Asset): Flow<Token> {
        return selectedFiat.flow().map {
            if (it == "usd" && chainAsset.priceProvider?.isSupported == true) {
                chainAsset.priceProvider?.id
            } else {
                chainAsset.priceId
            }
        }.flatMapLatest { priceId ->
            when (priceId) {
                null -> flowOf {
                    combineAssetWithPrices(chainAsset, null)
                }
                else -> tokenPriceDao.observeTokenPrice(priceId).map {
                    combineAssetWithPrices(chainAsset, it)
                }
            }
        }
    }
}
