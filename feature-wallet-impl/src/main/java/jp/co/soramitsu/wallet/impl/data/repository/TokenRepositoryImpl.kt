package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.model.TokenPriceLocal
import jp.co.soramitsu.wallet.impl.data.mappers.combineAssetWithPrices
import jp.co.soramitsu.wallet.impl.domain.interfaces.TokenRepository
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TokenRepositoryImpl(
    private val tokenPriceDao: TokenPriceDao
) : TokenRepository {

    override suspend fun getToken(chainAsset: Asset): Token = withContext(Dispatchers.Default) {
        val tokenPriceLocal = chainAsset.priceId?.let { tokenPriceDao.getTokenPrice(it) ?: TokenPriceLocal.createEmpty(it) }

        combineAssetWithPrices(chainAsset, tokenPriceLocal)
    }

    override fun observeToken(chainAsset: Asset): Flow<Token> {
        return when (val priceId = chainAsset.priceId) {
            null -> flowOf {
                combineAssetWithPrices(chainAsset, null)
            }
            else -> tokenPriceDao.observeTokenPrice(priceId).map {
                combineAssetWithPrices(chainAsset, it)
            }
        }
    }
}
