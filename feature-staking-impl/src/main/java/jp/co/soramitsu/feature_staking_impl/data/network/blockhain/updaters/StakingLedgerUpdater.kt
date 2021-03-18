package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.common.utils.sumByBigInteger
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.StakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.UnlockChunk
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class LedgerWithController(
    val ledger: StakingLedger,
    val controllerId: AccountId
)

class StakingLedgerUpdater(
    private val socketService: SocketService,
    private val stakingRepository: StakingRepository,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    private val accountStakingDao: AccountStakingDao,
    private val assetCache: AssetCache,
    override val scope: AccountUpdateScope
) : Updater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val accountAddress = scope.getAccount().address
        val currentAccountId = accountAddress.toAccountId()
        val runtime = runtimeProperty.get()

        val key = runtime.metadata.staking().storage("Bonded").storageKey(runtime, currentAccountId)

        return storageSubscriptionBuilder.subscribe(key)
            .flatMapLatest { change ->
                // assume we're controller, if no controller found
                val controllerId = change.value?.fromHex() ?: currentAccountId

                subscribeToLedger(controllerId)
            }.onEach { ledgerWithController ->
                updateAccountStaking(accountAddress, ledgerWithController)

                ledgerWithController?.ledger?.let {
                    val era = stakingRepository.getActiveEraIndex()
                    updateAssetStaking(accountAddress, it, era)
                }
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private suspend fun updateAccountStaking(
        accountAddress: String,
        ledgerWithController: LedgerWithController?
    ) {

        val accountStaking = AccountStakingLocal(
            address = accountAddress,
            stakingAccessInfo = ledgerWithController?.let {
                AccountStakingLocal.AccessInfo(
                    stashId = it.ledger.stashId,
                    controllerId = it.controllerId
                )
            }
        )

        accountStakingDao.insert(accountStaking)
    }

    private suspend fun subscribeToLedger(controllerId: AccountId): Flow<LedgerWithController?> {
        val runtime = runtimeProperty.get()

        val key = runtime.metadata.staking().storage("Ledger").storageKey(runtime, controllerId)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange().getSingleChange() }
            .map { change ->
                if (change != null) {
                    val ledger = bindStakingLedger(change, runtimeProperty.get())

                    LedgerWithController(ledger, controllerId)
                } else {
                    null
                }
            }
    }

    private suspend fun updateAssetStaking(
        accountAddress: String,
        stakingLedger: StakingLedger,
        era: BigInteger
    ) {
        return assetCache.updateAsset(accountAddress) { cached ->

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
            .sumByBigInteger(UnlockChunk::amount)
    }
}
