package jp.co.soramitsu.common.mixin.impl

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.distinctUntilChanged
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.model.AssetKey
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class UpdatesProvider : UpdatesMixin {
    private val _assets = MutableLiveData<Set<AssetKey>>()
    override val assets: LiveData<Set<AssetKey>> = _assets.distinctUntilChanged()

    private val _tokenRates = MutableLiveData(emptySet<String>())
    override val tokenRates: LiveData<Set<String>> = _tokenRates.distinctUntilChanged()

    private val _chains = MutableLiveData<Set<String>>()
    override val chains: LiveData<Set<String>> = _chains.distinctUntilChanged()

    private val assetsCache = mutableSetOf<AssetKey>()
    private val tokensCache = mutableSetOf<String>()
    private val chainsCache = mutableSetOf<String>()
    private val assetMutex = Mutex()
    private val tokensMutex = Mutex()
    private val chainsMutex = Mutex()

    override suspend fun startUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, tokenSymbol: String) {
        assetMutex.withLock {
            assetsCache.add(AssetKey(metaId, chainId, accountId, tokenSymbol))
            _assets.postValue(assetsCache)
        }
    }

    override suspend fun finishUpdateAsset(metaId: Long, chainId: String, accountId: AccountId, tokenSymbol: String) {
        assetMutex.withLock {
            assetsCache.remove(AssetKey(metaId, chainId, accountId, tokenSymbol))
            // update chain here too - assume chain updated if we got balance; need logic research: somehow not applied in finishChainSyncUp
            chainsCache.remove(chainId)
            _chains.postValue(chainsCache)
            _assets.postValue(assetsCache)
        }
    }

    override suspend fun startUpdateToken(symbol: String) {
        tokensMutex.withLock {
            tokensCache.add(symbol)
            _tokenRates.postValue(tokensCache)
        }
    }

    override suspend fun startUpdateTokens(symbols: List<String>) {
        if (symbols.isEmpty()) return
        tokensMutex.withLock {
            tokensCache.addAll(symbols)
            _tokenRates.postValue(tokensCache)
        }
    }

    override suspend fun finishUpdateToken(symbol: String) {
        tokensMutex.withLock {
            tokensCache.remove(symbol)
            _tokenRates.postValue(tokensCache)
        }
    }

    override suspend fun startChainSyncUp(chainId: String) {
        chainsMutex.withLock {
            chainsCache.add(chainId)
            _chains.postValue(chainsCache)
        }
    }

    override suspend fun startChainsSyncUp(chainIds: List<String>) {
        if (chainIds.isEmpty()) return
        chainsMutex.withLock {
            chainsCache.addAll(chainIds)
            _chains.postValue(chainsCache)
        }
    }

    override suspend fun finishChainSyncUp(chainId: String) {
        chainsMutex.withLock {
            chainsCache.remove(chainId)
            _chains.postValue(chainsCache)
        }
    }
}
