package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.coredb.dao.ChainDao
import jp.co.soramitsu.coredb.model.AssetWithToken
import jp.co.soramitsu.coredb.model.chain.JoinedChainInfo
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ChainsRepository @JvmOverloads constructor(
    private val chainDao: ChainDao,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private fun freshCatalogFlow(): Flow<List<Chain>> = chainDao.joinChainInfoFlow()
        .map { local -> local.mapNotNull(::mapChainLocalToChainOrNull) }
        .flowOn(Dispatchers.IO)

    private val catalog = ObservedChainCatalog(::freshCatalogFlow, scope)

    fun chainsByIdFlow(): Flow<Map<ChainId, Chain>> = chainsFlow().map { chains -> chains.associateBy { it.id } }

    suspend fun getChainsById(): Map<ChainId, Chain> = withContext(Dispatchers.IO) {
        val local = chainDao.getJoinChainInfo()
        local.mapNotNull(::mapChainLocalToChainOrNull).associateBy { it.id }
    }

    fun chainsFlow(): Flow<List<Chain>> = catalog.observe()

    suspend fun getChains(): List<Chain> = withContext(Dispatchers.IO) {
        val local = chainDao.getJoinChainInfo()
        local.mapNotNull(::mapChainLocalToChainOrNull)
    }

    suspend fun chainWithAsset(chainId: ChainId, assetId: String): Pair<Chain, Asset> {
        val chain = getChain(chainId)

        return chain to chain.assetsById.getValue(assetId)
    }

    suspend fun getChain(chainId: ChainId): Chain = withContext(Dispatchers.IO) {
        // Prefer flow-based wait for the chain to appear over polling
        val map = withTimeoutOrNull(60_000) { // 60s timeout
            freshCatalogFlow().map { chains -> chains.associateBy { it.id } }.firstOrNull { it.containsKey(chainId) }
        } ?: throw IllegalStateException("Chain $chainId not available locally yet; sync pending or failed")

        map.getValue(chainId)
    }

    fun observeChainsPerAssetFlow(
        accountMetaId: Long,
        assetId: String
    ): Flow<Map<JoinedChainInfo, AssetWithToken>> {
        return chainDao.observeChainsWithBalance(accountMetaId, assetId).flowOn(Dispatchers.IO)
    }

}
