package jp.co.soramitsu.staking.impl.data.network.blockhain.updaters

import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.utils.staking
import jp.co.soramitsu.core.model.StorageChange
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.storage.StorageCache
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.coredb.dao.AccountStakingDao
import jp.co.soramitsu.coredb.model.AccountStakingLocal
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.network.updaters.insert
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.wsrpc.SocketService
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.shared_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.StakingLedger
import jp.co.soramitsu.staking.api.domain.model.isRedeemableIn
import jp.co.soramitsu.staking.api.domain.model.isUnbondingIn
import jp.co.soramitsu.staking.api.domain.model.sumStaking
import jp.co.soramitsu.staking.impl.data.network.blockhain.bindings.bindStakingLedger
import jp.co.soramitsu.staking.impl.data.network.blockhain.updaters.base.StakingUpdater
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioRepository
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
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
    private val stakingRepository: StakingRelayChainScenarioRepository,
    private val stakingSharedState: StakingSharedState,
    private val chainRegistry: ChainRegistry,
    private val accountStakingDao: AccountStakingDao,
    private val storageCache: StorageCache,
    private val assetCache: AssetCache,
    override val scope: AccountUpdateScope
) : StakingUpdater {

    data class Metadata(
        val metaId: Long,
        val chain: jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain,
        val chainAsset: Asset,
        val currentAccountId: AccountId,
        val runtime: RuntimeSnapshot,
        val stakingBondedStorageKey: String,
        val bondedStorageChange: StorageChange? = null,
        val ledgerWithController: LedgerWithController? = null
    )

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        return combine(
            stakingSharedState.assetWithChain.distinctUntilChangedBy { it.asset.id },
            scope.invalidationFlow().distinctUntilChangedBy { it.id }
        ) { assetWithChain, account ->
            val (chain, chainAsset) = assetWithChain

            val runtime = chainRegistry.getRuntime(chain.id)

            val currentAccountId =
                account.accountId(chain) ?: return@combine null

            val key = runtime.metadata.staking().storage("Bonded")
                .storageKey(runtime, currentAccountId)

            Metadata(account.id, chain, chainAsset, currentAccountId, runtime, key)
        }.filterNotNull()
            .flatMapLatest { metadata ->
                subscribeToLedger(
                    storageSubscriptionBuilder.socketService,
                    metadata.runtime,
                    metadata.chain.id,
                    metadata.currentAccountId//controllerId
                ).map { metadata.copy(ledgerWithController = it) }
            }
            .onEach { metadata ->
                updateAccountStaking(
                    metadata.chain.id,
                    metadata.chainAsset.id,
                    metadata.currentAccountId,
                    metadata.ledgerWithController
                )

                metadata.ledgerWithController?.let {
                    val era = stakingRepository.getActiveEraIndex(metadata.chain.id)

                    val stashId = it.ledger.stashId
                    val controllerId = it.controllerId

                    updateAssetStaking(
                        metadata.metaId,
                        stashId,
                        metadata.chainAsset,
                        it.ledger,
                        era
                    )

                    if (!stashId.contentEquals(controllerId)) {
                        updateAssetStaking(
                            metadata.metaId,
                            controllerId,
                            metadata.chainAsset,
                            it.ledger,
                            era
                        )
                    }
                } ?: updateAssetStakingForEmptyLedger(
                    metadata.metaId,
                    metadata.currentAccountId,
                    metadata.chainAsset
                )
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private suspend fun updateAccountStaking(
        chainId: String,
        chainAssetId: String,
        accountId: AccountId,
        ledgerWithController: LedgerWithController?
    ) {
        val accountStaking = AccountStakingLocal(
            chainId = chainId,
            chainAssetId = chainAssetId,
            accountId = accountId,
            stakingAccessInfo = ledgerWithController?.let {
                AccountStakingLocal.AccessInfo(
                    stashId = it.ledger.stashId,
                    controllerId = it.controllerId
                )
            }
        )

        accountStakingDao.insert(accountStaking)
    }

    private fun subscribeToLedger(
        socketService: SocketService,
        runtime: RuntimeSnapshot,
        chainId: String,
        controllerId: AccountId
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
        metaId: Long,
        accountId: AccountId,
        chainAsset: Asset,
        stakingLedger: StakingLedger,
        era: BigInteger
    ) {
        assetCache.updateAsset(metaId, accountId, chainAsset) { cached ->
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
        metaId: Long,
        accountId: AccountId,
        chainAsset: Asset
    ) {
        assetCache.updateAsset(metaId, accountId, chainAsset) { cached ->
            cached.copy(
                redeemableInPlanks = BigInteger.ZERO,
                unbondingInPlanks = BigInteger.ZERO,
                bondedInPlanks = BigInteger.ZERO
            )
        }
    }
}
