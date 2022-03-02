package jp.co.soramitsu.feature_wallet_api.data.cache

import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.AssetReadOnlyCache
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.dao.emptyAccountIdValue
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.AssetUpdateItem
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AssetCache(
    private val tokenDao: TokenDao,
    private val accountRepository: AccountRepository,
    private val assetDao: AssetDao,
) : AssetReadOnlyCache by assetDao {

    private val assetUpdateMutex = Mutex()

    suspend fun updateAsset(
        metaId: Long,
        accountId: AccountId,
        chainAsset: Chain.Asset,
        builder: (local: AssetLocal) -> AssetLocal,
    ) = withContext(Dispatchers.IO) {
        val symbol = chainAsset.symbol
        val chainId = chainAsset.chainId

        assetUpdateMutex.withLock {
            tokenDao.ensureToken(symbol)

            when (val cachedAsset = assetDao.getAsset(metaId, accountId, chainId, symbol)?.asset) {
                null -> assetDao.insertAsset(builder.invoke(AssetLocal.createEmpty(accountId, symbol, chainId, metaId)))
                else -> when (cachedAsset.accountId) {
                    emptyAccountIdValue -> {
                        assetDao.deleteAsset(metaId, emptyAccountIdValue, chainId, symbol)
                        assetDao.insertAsset(builder.invoke(cachedAsset))
                    }
                    else -> assetDao.updateAsset(builder.invoke(cachedAsset))
                }
            }
        }
    }

    suspend fun updateAsset(
        accountId: AccountId,
        chainAsset: Chain.Asset,
        builder: (local: AssetLocal) -> AssetLocal,
    ) = withContext(Dispatchers.IO) {
        val applicableMetaAccount = accountRepository.findMetaAccount(accountId)

        applicableMetaAccount?.let {
            updateAsset(it.id, accountId, chainAsset, builder)
        }
    }

    suspend fun updateToken(
        symbol: String,
        builder: (local: TokenLocal) -> TokenLocal,
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenLocal = tokenDao.getToken(symbol) ?: TokenLocal.createEmpty(symbol)

            val newToken = builder.invoke(tokenLocal)

            tokenDao.insertToken(newToken)
        }
    }

    suspend fun updateAsset(updateModel: List<AssetUpdateItem>) {
        val onlyUpdates = mutableListOf<AssetUpdateItem>()
        updateModel.listIterator().forEach {
            val cached = assetDao.getAsset(it.metaId, it.accountId, it.chainId, it.tokenSymbol)?.asset
            if (cached == null) {
                val initial = AssetLocal.createEmpty(it.accountId, it.tokenSymbol, it.chainId, it.metaId)
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
