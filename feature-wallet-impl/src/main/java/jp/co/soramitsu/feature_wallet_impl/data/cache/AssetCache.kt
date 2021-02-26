package jp.co.soramitsu.feature_wallet_impl.data.cache

import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.AssetReadOnlyCache
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.model.AssetLocal
import jp.co.soramitsu.core_db.model.TokenLocal
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.WalletAccount
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
        account: Account,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            if (!tokenDao.isTokenExists(tokenType)) {
                tokenDao.insertToken(TokenLocal.createEmpty(tokenType))
            }

            val cachedAsset = assetDao.getAsset(account.address, tokenType)?.asset ?: AssetLocal.createEmpty(tokenType, account.address)

            val newAsset = builder.invoke(cachedAsset)

            assetDao.insertAsset(newAsset)
        }
    }

    suspend fun updateToken(
        account: WalletAccount,
        builder: (local: TokenLocal) -> TokenLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenType = Token.Type.fromNetworkType(account.network.type)

            val tokenLocal = tokenDao.getToken(tokenType) ?: TokenLocal.createEmpty(tokenType)

            val newToken = builder.invoke(tokenLocal)

            tokenDao.insertToken(newToken)
        }
    }
}