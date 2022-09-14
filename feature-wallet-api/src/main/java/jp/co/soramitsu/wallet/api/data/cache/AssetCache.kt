package jp.co.soramitsu.wallet.api.data.cache

import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.AssetReadOnlyCache
import jp.co.soramitsu.coredb.dao.TokenDao
import jp.co.soramitsu.coredb.dao.emptyAccountIdValue
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.AssetUpdateItem
import jp.co.soramitsu.coredb.model.TokenLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AssetCache(
    private val tokenDao: TokenDao,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
    private val updatesMixin: UpdatesMixin
) : AssetReadOnlyCache by assetDao,
    UpdatesProviderUi by updatesMixin {

    private val assetUpdateMutex = Mutex()

    suspend fun updateAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: Chain.Asset,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        val chainId = chainAsset.chainId
        val assetId = chainAsset.id

        assetUpdateMutex.withLock {
            tokenDao.ensureToken(assetId)

            val cachedAsset = assetDao.getAsset(metaId, accountId, chainId, assetId)?.asset
            when {
                cachedAsset == null -> assetDao.insertAsset(builder.invoke(AssetLocal.createEmpty(accountId, assetId, chainId, metaId)))
                cachedAsset.accountId.contentEquals(emptyAccountIdValue) -> {
                    assetDao.deleteAsset(metaId, emptyAccountIdValue, chainId, assetId)
                    assetDao.insertAsset(builder.invoke(cachedAsset.copy(accountId = accountId)))
                }
                else -> assetDao.updateAsset(builder.invoke(cachedAsset))
            }
            updatesMixin.finishUpdateAsset(metaId, chainId, accountId, assetId)
        }
    }

    suspend fun updateAsset(
        accountId: AccountId,
        chainAsset: Chain.Asset,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        val applicableMetaAccount = accountRepository.findMetaAccount(accountId)

        applicableMetaAccount?.let {
            updateAsset(it.id, accountId, chainAsset, builder)
        }
    }

    suspend fun updateToken(
        assetId: String,
        builder: (local: TokenLocal) -> TokenLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenLocal = tokenDao.getToken(assetId) ?: TokenLocal.createEmpty(assetId)

            val newToken = builder.invoke(tokenLocal)

            tokenDao.insertToken(newToken)
        }
    }

    suspend fun updateAsset(updateModel: List<AssetUpdateItem>) {
        val onlyUpdates = mutableListOf<AssetUpdateItem>()
        updateModel.listIterator().forEach {
            val cached = assetDao.getAsset(it.metaId, it.accountId, it.chainId, it.id)?.asset
            if (cached == null) {
                val initial = AssetLocal.createEmpty(it.accountId, it.id, it.chainId, it.metaId)
                val newAsset = initial.copy(
                    sortIndex = it.sortIndex,
                    enabled = it.enabled
                )
                assetUpdateMutex.withLock {
                    assetDao.insertAsset(newAsset)
                }
            } else {
                onlyUpdates.add(it)
            }
        }
        assetUpdateMutex.withLock {
            assetDao.updateAssets(onlyUpdates)
        }
    }
}
