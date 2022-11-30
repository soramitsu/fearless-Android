package jp.co.soramitsu.common.mixin.impl

import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdatesProvider : UpdatesMixin {
    private val _assets = MutableStateFlow(emptySet<AssetKey>())
    override val assetsUpdate: Flow<Set<AssetKey>> = _assets

    private val _tokenRates = MutableStateFlow(emptySet<String>())
    override val tokenRatesUpdate: Flow<Set<String>> = _tokenRates

    private val _chains = MutableStateFlow(emptySet<String>())
    override val chainsUpdate: Flow<Set<String>> = _chains

    private val assetsCache = mutableSetOf<AssetKey>()
    private val tokensCache = mutableSetOf<String>()
    private val chainsCache = mutableSetOf<String>()
    private val assetMutex = Mutex()
    private val tokensMutex = Mutex()
    private val chainsMutex = Mutex()

    override suspend fun startUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String) {
        assetMutex.withLock {
            assetsCache.add(AssetKey(metaId, chainId, accountId, assetId))
            _assets.value = assetsCache
        }
    }

    override suspend fun finishUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, assetId: String) {
        assetMutex.withLock {
            assetsCache.remove(AssetKey(metaId, chainId, accountId, assetId))
            // update chain here too - assume chain updated if we got balance; need logic research: somehow not applied in finishChainSyncUp
            chainsCache.remove(chainId)
            _chains.value = chainsCache
            _assets.value = assetsCache
        }
    }

    override suspend fun startUpdateToken(priceId: String) {
        tokensMutex.withLock {
            tokensCache.add(priceId)
            _tokenRates.value = tokensCache
        }
    }

    override suspend fun startUpdateTokens(priceIds: Set<String>) {
        if (priceIds.isEmpty()) return
        tokensMutex.withLock {
            tokensCache.addAll(priceIds)
            _tokenRates.value = tokensCache
        }
    }

    override suspend fun finishUpdateTokens(priceIds: Set<String>) {
        if (priceIds.isEmpty()) return
        tokensMutex.withLock {
            tokensCache.removeAll(priceIds)
            _tokenRates.value = tokensCache
        }
    }

    override suspend fun finishUpdateToken(priceId: String) {
        tokensMutex.withLock {
            tokensCache.remove(priceId)
            _tokenRates.value = tokensCache
        }
    }

    override suspend fun startChainSyncUp(chainId: String) {
        chainsMutex.withLock {
            chainsCache.add(chainId)
            _chains.value = chainsCache
        }
    }

    override suspend fun startChainsSyncUp(chainIds: List<String>) {
        if (chainIds.isEmpty()) return
        chainsMutex.withLock {
            chainsCache.addAll(chainIds)
            _chains.value = chainsCache
        }
    }

    override suspend fun finishChainSyncUp(chainId: String) {
        chainsMutex.withLock {
            chainsCache.remove(chainId)
            _chains.value = chainsCache
        }
    }
}
