package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.sumBy
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdater
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.StakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.UnlockChunk
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class StakingLedgerUpdater(
    private val socketService: SocketService,
    private val stakingRepository: StakingRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val assetCache: AssetCache
) : AccountUpdater {

    override fun listenAccountUpdates(accountSubscriptionBuilder: SubscriptionBuilder, account: Account): Flow<Updater.SideEffect> {
        val stashId = account.address.toAccountId()
        val key = Module.Staking.Bonded.storageKey(stashId)

        return accountSubscriptionBuilder.subscribe(key)
            .flatMapLatest { change ->
                val controllerId = change.value

                if (controllerId != null) {
                    subscribeToLedger(stashId, controllerId)
                } else {
                    flowOf(StakingLedger.empty(stashId))
                }
            }.onEach { stakingLedger ->
                val era = stakingRepository.getActiveEraIndex()

                updateAssetStaking(account, stakingLedger, era)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun subscribeToLedger(stashId: AccountId, controllerId: String): Flow<StakingLedger> {
        val controllerIdBytes = controllerId.fromHex()

        val key = Module.Staking.Ledger.storageKey(controllerIdBytes)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange().getSingleChange() }
            .map { change ->
                if (change != null) {
                    bindStakingLedger(change, runtimeProperty.get())
                } else {
                    StakingLedger.empty(stashId)
                }
            }
    }

    private suspend fun updateAssetStaking(
        account: Account,
        stakingLedger: StakingLedger,
        era: BigInteger
    ) {
        return assetCache.updateAsset(account) { cached ->

            val redeemable = stakingLedger.sumStaking { it <= era }
            val unbonding = stakingLedger.sumStaking { it > era }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger.active
            )
        }
    }

    private fun StakingLedger.sumStaking(
        condition: (chunkEra: BigInteger) -> Boolean
    ): BigInteger {
        return unlocking
            .filter { condition(it.era) }
            .sumBy(UnlockChunk::amount)
    }
}