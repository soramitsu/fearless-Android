package jp.co.soramitsu.runtime.multiNetwork.chain

import android.util.Log
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ChainsRepository(private val chainDao: ChainDao) {
    fun chainsByIdFlow(): Flow<Map<ChainId, Chain>> {
        return chainDao.joinChainInfoFlow().map { localChainsJoinedInfo ->
            localChainsJoinedInfo.map(::mapChainLocalToChain).associateBy { it.id }
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getChainsById(): Map<ChainId, Chain> = withContext(Dispatchers.IO) {
        val local = chainDao.getJoinChainInfo()
        local.map { mapChainLocalToChain(it) }.associateBy { it.id }
    }

    fun chainsFlow(): Flow<List<Chain>> {
        return chainDao.joinChainInfoFlow().map { localChainsJoinedInfo ->
            localChainsJoinedInfo.map(::mapChainLocalToChain)
        }.flowOn(Dispatchers.IO)
    }

    suspend fun getChains(): List<Chain> = withContext(Dispatchers.IO) {
        val local = chainDao.getJoinChainInfo()
        local.map { mapChainLocalToChain(it) }
    }

    suspend fun chainWithAsset(chainId: ChainId, assetId: String): Pair<Chain, Asset> {
        val chain = getChain(chainId)

        return chain to chain.assetsById.getValue(assetId)
    }

    suspend fun getChain(chainId: ChainId): Chain = withContext(Dispatchers.IO) {
        val chainLocal = chainDao.getJoinChainInfo(chainId).mapNotNull { it }.first()
        mapChainLocalToChain(chainLocal)
    }

    fun observeChainsPerAssetFlow(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<JoinedChainInfo, AssetWithToken>> {
        return chainDao.observeChainsWithBalance(accountMetaId, assetId).flowOn(Dispatchers.IO)
    }

    suspend fun notifyNodeSwitched(chainId: ChainId, nodeUrl: String) {
        chainDao.selectNode(chainId, nodeUrl)
    }
}