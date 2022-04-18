package jp.co.soramitsu.runtime.multiNetwork.chain

import com.google.gson.Gson
import jp.co.soramitsu.commonnetworking.fearless.FearlessChainsBuilder
import jp.co.soramitsu.commonnetworking.networkclient.SoraNetworkClient
import jp.co.soramitsu.commonnetworking.networkclient.createJsonRequest
import jp.co.soramitsu.core_db.dao.ChainDao
import jp.co.soramitsu.core_db.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.BuildConfig
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.updateNodesActive
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.AssetRemote
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.model.ChainRemote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ChainSyncService(
    private val dao: ChainDao,
    private val networkClient: SoraNetworkClient,
    private val chainBuilder: FearlessChainsBuilder,
    private val gson: Gson,
) {

    suspend fun syncUp() = withContext(Dispatchers.Default) {
        val localChainsJoinedInfo = dao.getJoinChainInfo()

        val chains = chainBuilder.getChains(
            BuildConfig.APP_VERSION_NAME,
            localChainsJoinedInfo.map {
                it.chain.id to it.chain.md5Hash
            }
        )

        val assets = networkClient.createJsonRequest<List<AssetRemote>>(BuildConfig.ASSETS_URL)

        val remoteNewChains = mapChainRemoteToChain(chains.newChains.map { mapToList(it.content) to it.hash }, assets)
        val remoteUpdatedChains = mapChainRemoteToChain(chains.updatedChains.map { mapToList(it.content) to it.hash }, assets)

        val localChains = localChainsJoinedInfo.map(::mapChainLocalToChain)

        val localMapping = localChains.associateBy(Chain::id)

        val updatedMapped = remoteUpdatedChains.mapNotNull { remoteChain ->
            localMapping[remoteChain.id]?.let {
                remoteChain.updateNodesActive(it)
            }
        }.map(::mapChainToChainLocal)

        val newMapped = remoteNewChains.map(::mapChainToChainLocal)

        val removed = localChainsJoinedInfo.filter { it.chain.id in chains.removedChains }
            .map(JoinedChainInfo::chain)

        dao.update(removed, newMapped + updatedMapped)
    }

    private fun mapToList(json: String) = gson.fromJson(json, ChainRemote::class.java)
}
