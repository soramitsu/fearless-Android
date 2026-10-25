package jp.co.soramitsu.wallet.api.data.cache

import jp.co.soramitsu.common.domain.SelectedFiat
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.model.cache.FreshAssetCacheUpdate
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Deprecated("Don't use it for update assets, use DAO instead")
class AssetCache(
    private val assetDao: AssetDao,
    private val selectedFiat: SelectedFiat,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) : AssetReadOnlyCache by assetDao {

    suspend fun updateAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: Asset,
        fresh: FreshAssetCacheUpdate
    ) = withContext(dispatcher) {
        val chainId = chainAsset.chainId
        val assetId = chainAsset.id
        // todo make better way to check that we support price provider
        val shouldUseChainlinkForRates =
            selectedFiat.isUsd() && chainAsset.priceProvider?.type == Asset.PriceProviderType.Chainlink
        val priceId = if (shouldUseChainlinkForRates) {
            chainAsset.priceProvider?.id
        } else {
            chainAsset.priceId
        }
        assetDao.updateFreshAssetCache(metaId, accountId, chainId, assetId, priceId, fresh)
    }
}
