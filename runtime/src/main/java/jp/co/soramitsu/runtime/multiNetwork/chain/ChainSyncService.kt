package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polkadotChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.reefChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChainSyncService(
    private val dao: ChainDao,
    private val chainFetcher: ChainFetcher
) {

    suspend fun syncUp() = withContext(Dispatchers.Default) {
        val localChainsJoinedInfo = dao.getJoinChainInfo()

        val remoteChains = chainFetcher.getChains()
            .filter {
                !it.disabled && (it.assets?.isNotEmpty() == true)
            }
            .map {
                it.toChain()
            }

        val localChains = localChainsJoinedInfo.map(::mapChainLocalToChain)

        val remoteMapping = remoteChains.associateBy(Chain::id)
        val localMapping = localChains.associateBy(Chain::id)

        val newOrUpdated = remoteChains.mapNotNull { remoteChain ->
            val localVersion = localMapping[remoteChain.id]

            when {
                localVersion == null -> remoteChain // new
                localVersion != remoteChain -> remoteChain // updated
                else -> null // same
            }
        }.map(::mapChainToChainLocal)

        val removed = localChainsJoinedInfo.filter { it.chain.id !in remoteMapping }
            .map(JoinedChainInfo::chain)

        dao.update(removed, newOrUpdated)
    }
}
