package jp.co.soramitsu.feature_wallet_api.data.cache

import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.AssetReadOnlyCache
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_wallet_api.data.mappers.tokenTypeLocalFromNetworkType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AssetCache(
    private val tokenDao: TokenDao,
    private val assetDao: AssetDao
) : AssetReadOnlyCache by assetDao {

    private val assetUpdateMutex = Mutex()

    suspend fun updateAsset(
        address: String,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenType = tokenTypeLocalFromNetworkType(address.networkType())

            if (!tokenDao.isTokenExists(tokenType)) {
                tokenDao.insertToken(TokenLocal.createEmpty(tokenType))
            }

            val cachedAsset = assetDao.getAsset(address, tokenType)?.asset ?: AssetLocal.createEmpty(tokenType, address)

            val newAsset = builder.invoke(cachedAsset)

            assetDao.insertAsset(newAsset)
        }
    }

    suspend fun updateToken(
        networkType: Node.NetworkType,
        builder: (local: TokenLocal) -> TokenLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenOrdinal = tokenTypeLocalFromNetworkType(networkType)

            val tokenLocal = tokenDao.getToken(tokenOrdinal) ?: TokenLocal.createEmpty(tokenOrdinal)

            val newToken = builder.invoke(tokenLocal)

            tokenDao.insertToken(newToken)
        }
    }
}
