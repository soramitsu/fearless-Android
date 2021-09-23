package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.controller

import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.feature_wallet_api.data.cache.updateAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.state.chainAndAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class AccountControllerBalanceUpdater(
    override val scope: AccountStakingScope,
    private val sharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val assetCache: AssetCache,
) : Updater {

    override val requiredModules: List<String> = listOf(Modules.SYSTEM, Modules.STAKING)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val (chain, chainAsset) = sharedState.chainAndAsset()
        val runtime = chainRegistry.getRuntime(chain.id)

        val accountStaking = scope.getAccountStaking()
        val stakingAccessInfo = accountStaking.stakingAccessInfo ?: return emptyFlow()

        val controllerId = stakingAccessInfo.controllerId
        val stashId = stakingAccessInfo.stashId
        val accountId = accountStaking.accountId

        if (controllerId.contentEquals(stashId)) {
            // balance is already observed, no need to do it twice
            return emptyFlow()
        }

        val companionAccountId = when {
            accountId.contentEquals(controllerId) -> stashId
            accountId.contentEquals(stashId) -> controllerId
            else -> throw IllegalArgumentException()
        }

        val key = runtime.metadata.system().storage("Account").storageKey(runtime, companionAccountId)

        return storageSubscriptionBuilder.subscribe(key)
            .onEach { change ->
                val newAccountInfo = bindAccountInfoOrDefault(change.value, runtime)

                assetCache.updateAsset(companionAccountId, chainAsset, newAccountInfo)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }
}
