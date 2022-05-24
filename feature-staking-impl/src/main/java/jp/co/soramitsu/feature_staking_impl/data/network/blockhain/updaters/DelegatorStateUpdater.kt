package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.identity
import jp.co.soramitsu.common.utils.parachainStaking
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.network.updaters.insert
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach

//class DelegatorStateUpdater(
//    scope: AccountStakingScope,
//    storageCache: StorageCache,
//    stakingSharedState: StakingSharedState,
//    chainRegistry: ChainRegistry,
//) : SingleStorageKeyUpdater<AccountStakingScope>(scope, stakingSharedState, chainRegistry, storageCache), ParachainStakingUpdater {
//
//    override suspend fun storageKey(runtime: RuntimeSnapshot): String? {
//        val accountId = scope.getAccountId() ?: return null
//        return runtime.metadata.parachainStaking().storage("DelegatorState").storageKey(runtime, accountId)
//    }
//
//    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
//        return super.listenForUpdates(storageSubscriptionBuilder)
//    }
//}

class DelegatorStateUpdater(
    override val scope: AccountStakingScope,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val storageCache: StorageCache,
) : Updater {
    override val requiredModules = listOf(Modules.PARACHAIN_STAKING, Modules.IDENTITY)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chainId = stakingSharedState.chainId()
        val runtime = chainRegistry.getRuntime(chainId)
        val accountId = scope.getAccountId() ?: return emptyFlow()

        val storageKey = runtime.metadata.parachainStaking().storage("DelegatorState").storageKey(runtime, accountId)

        return storageSubscriptionBuilder.subscribe(storageKey)
            .onEach { storageCache.insert(it, chainId) }
            .flatMapLatest { change ->
                change.
            }
            .noSideAffects()
    }

    private fun subscribeToIdentity(runtime: RuntimeSnapshot) {
        val key = runtime.metadata.identity().storage("DelegatorState").storageKey(runtime, accountId)
    }
}
