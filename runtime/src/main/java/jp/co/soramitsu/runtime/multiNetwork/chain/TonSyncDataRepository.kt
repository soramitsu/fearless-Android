package jp.co.soramitsu.runtime.multiNetwork.chain

import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.TokenRate
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.remote.TonRemoteSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TonSyncDataRepository(private val tonRemoteSource: TonRemoteSource) {

    // cache to be used between all sync services (remoteAssets, balances, prices)
    private val jettonCache: ConcurrentHashMap<Pair<ChainId, String>, Deferred<JettonsBalances>> =
        ConcurrentHashMap()
    private val accountDataCache: ConcurrentHashMap<Pair<ChainId, String>, Deferred<TonAccountData>> =
        ConcurrentHashMap()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    init {
        // clear expired data every 5 minutes
        scope.launch {
            while (isActive) {
                delay(5 * 60 * 1000)
                jettonCache.clear()
                accountDataCache.clear()
            }
        }
    }

    suspend fun getAccountData(chain: Chain, accountId: String): TonAccountData {
        val key = chain.id to accountId
        return accountDataCache.computeIfAbsent(key) {
            scope.async {
                retry { tonRemoteSource.loadAccountData(chain, accountId) }
            }
        }.await()
    }

    suspend fun getJettonBalances(chain: Chain, accountId: String): JettonsBalances {
        val key = chain.id to accountId
        return jettonCache.computeIfAbsent(key) {
            scope.async {
                retry { tonRemoteSource.loadJettonBalances(chain, accountId) }
            }
        }.await()
    }

    suspend fun getTonCoinPrices(): TokenRate {
        return tonRemoteSource.getTonCoinPrices()
    }

    private suspend fun <T> retry(block: suspend () -> T): T {
        var lastException: Exception? = null
        repeat(3) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
                delay(1000)
            }
        }
        throw lastException ?: IllegalStateException("Retry failed")
    }
}