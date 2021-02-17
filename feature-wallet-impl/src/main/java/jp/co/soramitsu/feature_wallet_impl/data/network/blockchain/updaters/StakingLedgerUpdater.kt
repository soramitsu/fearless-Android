package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.runtime.calls.SubstrateCalls
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.Module
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.scale.invoke
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_impl.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.StakingLedger
import jp.co.soramitsu.feature_wallet_impl.data.repository.sumStaking
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
    private val substrateCalls: SubstrateCalls,
    private val assetCache: AssetCache
) : AccountUpdater {

    override fun listenAccountUpdates(accountSubscriptionBuilder: SubscriptionBuilder, account: Account): Flow<Updater.SideEffect> {
        val stashAddress = account.address
        val key = Module.Staking.Bonded.storageKey(stashAddress.toAccountId())

        return accountSubscriptionBuilder.subscribe(key)
            .flatMapLatest { change ->
                val controllerId = change.value

                if (controllerId != null) {
                    subscribeToLedger(stashAddress, controllerId)
                } else {
                    flowOf(createEmptyLedger(stashAddress))
                }
            }.onEach { stakingLedger ->
                val era = substrateCalls.getActiveEra()

                updateAssetStaking(account, stakingLedger, era)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private fun subscribeToLedger(stashAddress: String, controllerId: String): Flow<EncodableStruct<StakingLedger>> {
        val controllerIdBytes = controllerId.fromHex()

        val key = Module.Staking.Ledger.storageKey(controllerIdBytes)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange().getSingleChange() }
            .map { change ->
                if (change != null) {
                    StakingLedger.read(change)
                } else {
                    createEmptyLedger(stashAddress)
                }
            }
    }

    private fun createEmptyLedger(address: String): EncodableStruct<StakingLedger> {
        return StakingLedger { ledger ->
            ledger[StakingLedger.stash] = address.toAccountId()
            ledger[StakingLedger.active] = BigInteger.ZERO
            ledger[StakingLedger.claimedRewards] = emptyList()
            ledger[StakingLedger.total] = BigInteger.ZERO
            ledger[StakingLedger.unlocking] = emptyList()
        }
    }

    private suspend fun updateAssetStaking(
        account: Account,
        stakingLedger: EncodableStruct<StakingLedger>,
        era: Long
    ) {
        return assetCache.updateAsset(account) { cached ->

            val redeemable = stakingLedger.sumStaking { it <= era }
            val unbonding = stakingLedger.sumStaking { it > era }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger[StakingLedger.active]
            )
        }
    }
}