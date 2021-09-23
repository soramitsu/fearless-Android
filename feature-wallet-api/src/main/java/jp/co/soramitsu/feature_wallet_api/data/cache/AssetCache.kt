package jp.co.soramitsu.feature_wallet_api.data.cache

import android.util.Log
import jp.co.soramitsu.core_db.dao.AssetDao
import jp.co.soramitsu.core_db.dao.AssetReadOnlyCache
import jp.co.soramitsu.core_db.dao.TokenDao
import jp.co.soramitsu.core_db.model.AssetLocal
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
    private val assetDao: AssetDao
) : AssetReadOnlyCache by assetDao {

    private val assetUpdateMutex = Mutex()

    suspend fun updateAsset(
        accountId: AccountId,
        chainAsset: Chain.Asset,
        builder: (local: AssetLocal) -> AssetLocal
    ) = withContext(Dispatchers.IO) {
        Log.d("RX", "Fetching meta account")
        val findMetaAccount = accountRepository.findMetaAccount(accountId)
        Log.d("RX", "Fetched meta account: ${findMetaAccount?.id}")

        findMetaAccount?.let {
            Log.d("RX", "Found meta account")
            val symbol = chainAsset.symbol
            val chainId = chainAsset.chainId

            assetUpdateMutex.withLock {
                val cachedAsset = assetDao.getAsset(accountId, chainId, symbol)?.asset ?: AssetLocal.createEmpty(accountId, symbol, chainId, metaId = it.id)

                val newAsset = builder.invoke(cachedAsset)

                assetDao.insertAsset(newAsset)
            }
        }
    }

    suspend fun updateToken(
        symbol: String,
        builder: (local: TokenLocal) -> TokenLocal
    ) = withContext(Dispatchers.IO) {
        assetUpdateMutex.withLock {
            val tokenLocal = tokenDao.getToken(symbol) ?: TokenLocal.createEmpty(symbol)

            val newToken = builder.invoke(tokenLocal)

            tokenDao.insertToken(newToken)
        }
    }
}
