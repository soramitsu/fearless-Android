package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class RemoteAssetsSyncServiceProvider(
    //private val okxApiService: OkxApiService,
    private val chainDao: ChainDao
){
    fun provide(chain: Chain): RemoteAssetsSyncService? {
        return when {
            chain.isEthereumChain && chain.remoteAssetsSource == Chain.RemoteAssetsSource.OKX -> OkxRemoteAssetsSyncService(chain, chainDao)
            else -> null
        }
    }
}

interface RemoteAssetsSyncService {
    suspend fun sync()
}

class OkxRemoteAssetsSyncService(
    //private val okxApiService: OkxApiService,
    private val chain: Chain,
    private val chainDao: ChainDao
): RemoteAssetsSyncService {

    override suspend fun sync() {
        //val assets = okxApiService.getAvailableAssets()
        //val chainAssets = assets.map { ChainAssetLocal(id = "", name = "", chainId = chain.id, ...) }
        //chainDao.insertChainAssets(chainAssets)
    }

}