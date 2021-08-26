package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.historical

import android.util.Log
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.GlobalScopeUpdater
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.interfaces.currentNetworkType
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.api.historicalEras
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.fetchValuesToCache
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.observeActiveEraIndex
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

interface HistoricalUpdater {

    fun constructHistoricalKey(runtime: RuntimeSnapshot, era: BigInteger): String
}

class HistoricalUpdateMediator(
    private val historicalUpdaters: List<HistoricalUpdater>,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val bulkRetriever: BulkRetriever,
    private val stakingRepository: StakingRepository,
    private val accountRepository: AccountRepository,
    private val storageCache: StorageCache,
) : GlobalScopeUpdater {

    override val requiredModules: List<String> = listOf(Modules.STAKING)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()

        val networkType = accountRepository.currentNetworkType()

        Log.d("RX", "FFFetching historical updates for $networkType")
        return storageCache.observeActiveEraIndex(runtime, networkType)
            .map {
                val allKeysNeeded = constructHistoricalKeys(runtime)
                val keysInDataBase = storageCache.filterKeysInCache(allKeysNeeded).toSet()

                val missingKeys = allKeysNeeded.filter { it !in keysInDataBase }

                missingKeys
            }.filter { it.isNotEmpty() }
            .onEach {
                bulkRetriever.fetchValuesToCache(it, storageCache)
            }
            .noSideAffects()
    }

    private suspend fun constructHistoricalKeys(runtime: RuntimeSnapshot): List<String> {
        val historicalRange = stakingRepository.historicalEras()

        return historicalUpdaters.map { updater ->
            historicalRange.map { updater.constructHistoricalKey(runtime, it) }
        }.flatten()
    }
}
