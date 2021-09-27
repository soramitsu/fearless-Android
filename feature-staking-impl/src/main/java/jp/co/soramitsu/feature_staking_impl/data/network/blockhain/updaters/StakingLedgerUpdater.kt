package jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters

import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.model.StorageChange
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core_db.dao.AccountStakingDao
import jp.co.soramitsu.core_db.model.AccountStakingLocal
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.SocketService
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.fearless_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_staking_api.domain.api.StakingRepository
import jp.co.soramitsu.feature_staking_api.domain.model.StakingLedger
import jp.co.soramitsu.feature_staking_api.domain.model.isRedeemableIn
import jp.co.soramitsu.feature_staking_api.domain.model.isUnbondingIn
import jp.co.soramitsu.feature_staking_api.domain.model.sumStaking
import jp.co.soramitsu.feature_staking_impl.data.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.feature_staking_impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import jp.co.soramitsu.runtime.network.updaters.insert
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import java.math.BigInteger

class LedgerWithController(
    val ledger: StakingLedger,
    val controllerId: AccountId,
)

class StakingLedgerUpdater(
    private val stakingRepository: StakingRepository,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val accountStakingDao: AccountStakingDao,
    private val storageCache: StorageCache,
    private val assetCache: AssetCache,
    override val scope: AccountUpdateScope,
) : StakingUpdater {

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {

        val (chain, chainAsset) = stakingSharedState.selectedAssetWithChain.first()
        val runtime = chainRegistry.getRuntime(chain.id)

        val currentAccountId = scope.getAccount().accountIdIn(chain)!! // TODO ethereum

        val key = runtime.metadata.staking().storage("Bonded").storageKey(runtime, currentAccountId)

        return storageSubscriptionBuilder.subscribe(key)
            .flatMapLatest { change ->
                // assume we're controller, if no controller found
                val controllerId = change.value?.fromHex() ?: currentAccountId

                subscribeToLedger(storageSubscriptionBuilder.socketService, runtime, chain.id, controllerId)
            }.onEach { ledgerWithController ->
                updateAccountStaking(chain.id, chainAsset.id, currentAccountId, ledgerWithController)

                ledgerWithController?.let {
                    val era = stakingRepository.getActiveEraIndex(chain.id)

                    val stashId = it.ledger.stashId
                    val controllerId = it.controllerId

                    updateAssetStaking(it.ledger.stashId, chainAsset, it.ledger, era)

                    if (!stashId.contentEquals(controllerId)) {
                        updateAssetStaking(controllerId, chainAsset, it.ledger, era)
                    }
                } ?: updateAssetStakingForEmptyLedger(currentAccountId, chainAsset)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private suspend fun updateAccountStaking(
        chainId: String,
        chainAssetId: Int,
        accountId: AccountId,
        ledgerWithController: LedgerWithController?,
    ) {

        val accountStaking = AccountStakingLocal(
            chainId = chainId,
            chainAssetId = chainAssetId,
            accountId = accountId,
            stakingAccessInfo = ledgerWithController?.let {
                AccountStakingLocal.AccessInfo(
                    stashId = it.ledger.stashId,
                    controllerId = it.controllerId,
                )
            }
        )

        accountStakingDao.insert(accountStaking)
    }

    private suspend fun subscribeToLedger(
        socketService: SocketService,
        runtime: RuntimeSnapshot,
        chainId: String,
        controllerId: AccountId,
    ): Flow<LedgerWithController?> {
        val key = runtime.metadata.staking().storage("Ledger").storageKey(runtime, controllerId)
        val request = SubscribeStorageRequest(key)

        return socketService.subscriptionFlow(request)
            .map { it.storageChange() }
            .onEach {
                val storageChange = StorageChange(it.block, key, it.getSingleChange())

                storageCache.insert(storageChange, chainId)
            }
            .map {
                val change = it.getSingleChange()

                if (change != null) {
                    val ledger = bindStakingLedger(change, runtime)

                    LedgerWithController(ledger, controllerId)
                } else {
                    null
                }
            }
    }

    private suspend fun updateAssetStaking(
        accountId: AccountId,
        chainAsset: Chain.Asset,
        stakingLedger: StakingLedger,
        era: BigInteger,
    ) {
        assetCache.updateAsset(accountId, chainAsset) { cached ->

            val redeemable = stakingLedger.sumStaking { it.isRedeemableIn(era) }
            val unbonding = stakingLedger.sumStaking { it.isUnbondingIn(era) }

            cached.copy(
                redeemableInPlanks = redeemable,
                unbondingInPlanks = unbonding,
                bondedInPlanks = stakingLedger.active
            )
        }
    }

    private suspend fun updateAssetStakingForEmptyLedger(
        accountId: AccountId,
        chainAsset: Chain.Asset,
    ) {
        assetCache.updateAsset(accountId, chainAsset) { cached ->
            cached.copy(
                redeemableInPlanks = BigInteger.ZERO,
                unbondingInPlanks = BigInteger.ZERO,
                bondedInPlanks = BigInteger.ZERO
            )
        }
    }
}
