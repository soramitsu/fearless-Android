package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.coredb.dao.AssetDao
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.model.AssetLocal
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.ChainFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChainSyncService(
    private val dao: ChainDao,
    private val chainFetcher: ChainFetcher,
    private val metaAccountDao: MetaAccountDao,
    private val assetsDao: AssetDao
) {

    suspend fun syncUp() = withContext(Dispatchers.Default) {
        runCatching {
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

            coroutineScope {
                launch {
                    val removed = localChainsJoinedInfo.filter { it.chain.id !in remoteMapping }
                        .map(JoinedChainInfo::chain)
                    dao.update(removed, newOrUpdated)
                }
                launch {
                    val metaAccounts = metaAccountDao.getMetaAccounts()
                    if(metaAccounts.isEmpty()) return@launch
                    val newAssets =
                        newOrUpdated.filter { it.chain.id !in localMapping.keys }.map { it.assets }
                            .flatten()

                    val newLocalAssets = metaAccounts.map { metaAccount ->
                        newAssets.map {
                            AssetLocal(
                                accountId = metaAccount.substrateAccountId,
                                id = it.id,
                                chainId = it.chainId,
                                metaId = metaAccount.id,
                                tokenPriceId = it.priceId,
                                enabled = remoteMapping[it.chainId]?.rank != null && it.isUtility == true
                            )
                        }
                    }.flatten()
                    assetsDao.insertAssets(newLocalAssets)
                }
            }.join()
        }
    }
}
