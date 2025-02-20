package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.coredb.dao.ChainDao
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.yield

class RemoteAssetsInitializer(
    private val dao: ChainDao,
    private val remoteAssetsSyncServiceProvider: RemoteAssetsSyncServiceProvider,
) {
    suspend fun invoke() {
        supervisorScope {
            val syncedChains = dao.getJoinChainInfo().map { mapChainLocalToChain(it) }

            val chainsWithRemoteAssets = syncedChains.filter { it.remoteAssetsSource != null }

            chainsWithRemoteAssets.forEach { chain ->
                launch {
                    val service = remoteAssetsSyncServiceProvider.provide(chain)
                    service?.sync()
                    yield()
                }
            }
        }
    }
}
