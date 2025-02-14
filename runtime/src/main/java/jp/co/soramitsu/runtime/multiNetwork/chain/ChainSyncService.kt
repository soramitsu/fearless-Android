package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.resources.ContextManager
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.accountId
import jp.co.soramitsu.coredb.model.chain.ChainAssetLocal
import jp.co.soramitsu.coredb.model.chain.ChainExplorerLocal
import jp.co.soramitsu.coredb.model.chain.ChainLocal
import jp.co.soramitsu.coredb.model.chain.ChainNodeLocal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

class RemoteAssetsInitializer(
    private val dao: ChainDao,
    private val remoteAssetsSyncServiceProvider: RemoteAssetsSyncServiceProvider,
) {
    suspend fun invoke() {
        val syncedChains = dao.getJoinChainInfo().map { mapChainLocalToChain(it) }
        val chainsWithRemoteAssets = syncedChains.filter { it.remoteAssetsSource != null }

        supervisorScope {
            chainsWithRemoteAssets.forEach { chain ->
                launch {
                    val service = remoteAssetsSyncServiceProvider.provide(chain)
                    service?.sync()
                }
            }
        }
    }
}

class ChainSyncService(
    private val dao: ChainDao,
    private val chainFetcher: ChainFetcher,
    private val metaAccountDao: MetaAccountDao,
    private val assetsDao: AssetDao,

    private val contextManager: ContextManager
) {

    suspend fun syncUp() = withContext(Dispatchers.Default) {
        kotlin.runCatching { configChainsSyncUp() }.onFailure { it.printStackTrace() }.getOrNull() ?: return@withContext
    }

    private suspend fun configChainsSyncUp(): List<Chain> = supervisorScope {
        val localChainsJoinedInfo = dao.getJoinChainInfo()
        val localChainsJoinedInfoMap = localChainsJoinedInfo.associateBy { it.chain.id }

        val remoteChains = chainFetcher.getChains()
            .filter {
                !it.disabled && (it.assets?.isNotEmpty() == true)
            }
            .map {
                it.toChain()
            }

        val remoteMapping = remoteChains.associateBy(Chain::id)

        val mappedRemoteChains = remoteChains.map { mapChainToChainLocal(it) }
        val chainsSyncDeferred = async {
            val chainsToUpdate: MutableList<ChainLocal> = mutableListOf()
            val chainsToAdd: MutableList<ChainLocal> = mutableListOf()
            val chainsToRemove =
                localChainsJoinedInfo.filter { it.chain.id !in remoteMapping.keys }
                    .map { it.chain }

            mappedRemoteChains.forEach { remoteChainInfo ->
                val remoteChain = remoteChainInfo.chain
                val localChain = localChainsJoinedInfoMap[remoteChain.id]?.chain

                when {
                    localChain == null -> chainsToAdd.add(remoteChain) // new
                    localChain != remoteChain -> chainsToUpdate.add(remoteChain) // updated
                }
            }
            dao.updateChains(chainsToAdd, chainsToUpdate)
            chainsToRemove
        }

        val chainsToDelete = chainsSyncDeferred.await()
        coroutineScope {
            chainsSyncDeferred.join()
            launch {
                val localAssets =
                    localChainsJoinedInfo.map { it.assets }.flatten()
                val remoteAssets =
                    mappedRemoteChains.map { it.assets }.flatten()

                val chainsWithRemoteAssetsIds = remoteChains.filter { it.remoteAssetsSource != null }.map { it.id }.toSet()

                val assetsToAdd: MutableList<ChainAssetLocal> = mutableListOf()
                val assetsToUpdate: MutableList<ChainAssetLocal> = mutableListOf()
                val assetsToRemove = localAssets.asSequence()
                    .filter { it.chainId !in chainsWithRemoteAssetsIds }.filter { local ->
                        val remoteAssetsIds = remoteAssets.map { it.id to it.chainId }

                        local.id to local.chainId !in remoteAssetsIds
                    }.toList()

                remoteAssets.forEach { remoteAsset ->
                    val localAsset = localAssets.find { it.id == remoteAsset.id && it.chainId == remoteAsset.chainId }

                    when {
                        localAsset == null -> {
                            assetsToAdd.add(remoteAsset)
                        } // new
                        localAsset != remoteAsset -> {
                            assetsToUpdate.add(remoteAsset)
                        } // updated
                    }
                }
                dao.updateAssets(assetsToAdd, assetsToUpdate, assetsToRemove)

                val metaAccounts = metaAccountDao.getMetaAccounts()
                if(metaAccounts.isEmpty()) return@launch
                val newLocalAssets = metaAccounts.map { metaAccount ->
                    assetsToAdd.mapNotNull {
                        val chain = remoteMapping[it.chainId] ?: return@mapNotNull null
                        val accountId = metaAccount.accountId(chain) ?: return@mapNotNull null

                        AssetLocal(
                            accountId = accountId,
                            id = it.id,
                            chainId = it.chainId,
                            metaId = metaAccount.id,
                            tokenPriceId = it.priceId,
                            enabled = false
                        )
                    }
                }.flatten()

                assetsDao.insertAssets(newLocalAssets)
                assetsDao.deleteAssets(assetsToRemove.map { it.id})
            }
            launch {
                val remoteNodes = mappedRemoteChains.map { it.nodes }.flatten()
                val localNodes = localChainsJoinedInfo.map { it.nodes }.flatten()
                val nodesToUpdate: MutableList<ChainNodeLocal> = mutableListOf()
                val nodesToAdd: MutableList<ChainNodeLocal> = mutableListOf()
                val nodesToRemove =
                    localNodes.filter { local -> local.url !in remoteNodes.map { it.url } }.filter { it.isDefault }

                remoteNodes.forEach { remoteNode ->
                    val localNode = localNodes.find { it.url == remoteNode.url }

                    when {
                        localNode == null -> nodesToAdd.add(remoteNode) // new
                        localNode != remoteNode -> nodesToUpdate.add(remoteNode) // updated
                    }
                }
                dao.updateNodes(nodesToAdd, nodesToUpdate, nodesToRemove)
            }
            launch {
                val remoteExplorers = mappedRemoteChains.map { it.explorers }.flatten()
                val localExplorers = localChainsJoinedInfo.map { it.explorers }.flatten()
                val explorersToUpdate: MutableList<ChainExplorerLocal> = mutableListOf()
                val explorersToAdd: MutableList<ChainExplorerLocal> = mutableListOf()
                val explorersToRemove =
                    localExplorers.filter { local -> local.type to local.chainId !in remoteExplorers.map { it.type to it.chainId } }

                remoteExplorers.forEach { remoteExplorer ->
                    val localExplorer = localExplorers.find { it.chainId == remoteExplorer.chainId && it.type == remoteExplorer.type }

                    when {
                        localExplorer == null -> explorersToAdd.add(remoteExplorer) // new
                        localExplorer != remoteExplorer -> explorersToUpdate.add(remoteExplorer) // updated
                    }
                }
                dao.updateExplorers(explorersToAdd, explorersToUpdate, explorersToRemove)
            }
            coroutineContext.job
        }.join()

        dao.deleteChains(chainsToDelete)

        remoteChains
    }
}
