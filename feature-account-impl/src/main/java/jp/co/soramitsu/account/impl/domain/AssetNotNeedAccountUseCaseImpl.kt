package jp.co.soramitsu.account.impl.domain

import jp.co.soramitsu.account.api.domain.interfaces.AssetNotNeedAccountUseCase
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.TokenPriceDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AssetNotNeedAccountUseCaseImpl(
    private val assetDao: AssetDao,
    private val tokenPriceDao: TokenPriceDao
) : AssetNotNeedAccountUseCase {

    override suspend fun markNotNeed(chainId: ChainId, metaId: Long, assetId: String, priceId: String?) {
        updateAssetNotNeed(metaId, chainId, assetId, priceId)
    }

    private suspend fun updateAssetNotNeed(
        metaId: Long,
        chainId: ChainId,
        assetId: String,
        priceId: String?
    ) {
        val cached = assetDao.getAsset(metaId, emptyAccountIdValue, chainId, assetId)?.asset
        if (cached == null) {
            val initial = AssetLocal.createEmpty(emptyAccountIdValue, assetId, chainId, metaId, priceId)
            val newAsset = initial.copy(markedNotNeed = true)
            priceId?.let { tokenPriceDao.ensureTokenPrice(it) }
            assetDao.insertAsset(newAsset)
        } else {
            val updatedAsset = cached.copy(markedNotNeed = true)
            assetDao.updateAsset(updatedAsset)
        }
    }

    override fun getAssetsMarkedNotNeedFlow(metaId: Long): Flow<List<AssetKey>> {
        return assetDao.observeAssets(metaId).map {
            it.filter { it.asset.markedNotNeed }.map {
                AssetKey(
                    metaId = metaId,
                    chainId = it.asset.chainId,
                    accountId = it.asset.accountId,
                    assetId = it.asset.id
                )
            }
        }
    }
}
