package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.controller

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.scope.AccountStakingScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.feature_wallet_api.data.cache.updateAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class AccountControllerBalanceUpdater(
    override val scope: AccountStakingScope,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val assetCache: AssetCache,
) : Updater {
    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val runtime = runtimeProperty.get()

        val accountStaking = scope.getAccountStaking()
        val stakingAccessInfo = accountStaking.stakingAccessInfo ?: return emptyFlow()

        val controllerId = stakingAccessInfo.controllerId

        val networkType = accountStaking.address.networkType()

        val controllerAddress = controllerId.toAddress(networkType)
        val stashAddress = stakingAccessInfo.stashId.toAddress(networkType)

        if (controllerAddress == stashAddress) {
            // balance is already observed, no need to do it twice
            return emptyFlow()
        }

        val companionAddress = when (accountStaking.address) {
            controllerAddress -> stashAddress
            stashAddress -> controllerAddress
            else -> throw IllegalArgumentException()
        }

        val key = runtime.metadata.system().storage("Account").storageKey(runtime, companionAddress.toAccountId())

        return storageSubscriptionBuilder.subscribe(key)
            .onEach { change ->
                val newAccountInfo = bindAccountInfoOrDefault(change.value, runtime)

                assetCache.updateAsset(companionAddress, newAccountInfo)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }
}
