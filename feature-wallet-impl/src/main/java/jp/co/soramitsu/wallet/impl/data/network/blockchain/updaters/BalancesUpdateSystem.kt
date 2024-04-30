package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.annotation.SuppressLint
import android.util.Log
import it.airgap.beaconsdk.core.internal.utils.failure
import it.airgap.beaconsdk.core.internal.utils.onEachFailure
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.impl.data.mappers.mapMetaAccountLocalToMetaAccount
import jp.co.soramitsu.account.impl.domain.buildStorageKeys
import jp.co.soramitsu.account.impl.domain.handleBalanceResponse
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.mixin.api.networkStateService
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.MetaAccountDao
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.getSocket
import jp.co.soramitsu.runtime.multiNetwork.getSocketOrNull
import jp.co.soramitsu.runtime.multiNetwork.toSyncIssue
import jp.co.soramitsu.runtime.network.subscriptionFlowCatching
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val RUNTIME_AWAITING_TIMEOUT = 10_000L

@SuppressLint("LogNotTimber")
class BalancesUpdateSystem(
    private val chainRegistry: ChainRegistry,
    private val metaAccountDao: MetaAccountDao,
    private val assetCache: AssetCache,
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val networkStateService: networkStateService,
    private val ethereumRemoteSource: EthereumRemoteSource
) : UpdateSystem {

    private val scope =
        CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            Log.e("BalancesUpdateSystem", "BalancesUpdateSystem got error: $throwable")
        })

    private val trigger = BalanceUpdateTrigger.observe()

    private fun subscribeFlow(): Flow<Updater.SideEffect> {
        return combine(
            chainRegistry.syncedChains,
            metaAccountDao.selectedMetaAccountInfoFlow().filterNotNull()
        ) { chains, accountInfo ->
            scope.coroutineContext.cancelChildren()
            val metaAccount =
                mapMetaAccountLocalToMetaAccount(chains.associateBy { it.id }, accountInfo)
            chains to metaAccount
        }
            .map { (chains, metaAccount) ->
                coroutineScope {
                    chains.map { chain ->
                        launch {
                            if (chain.isEthereumChain) {
                                listenEthereumBalancesByTrigger(chain, metaAccount).launchIn(scope)
                            } else {
                                if (!chain.isEthereumBased || metaAccount.ethereumPublicKey != null) {
                                    subscribeChainBalances(chain, metaAccount).onEachFailure {
                                        logError(
                                            chain,
                                            it
                                        )
                                    }.launchIn(scope)
                                }
                            }
                        }
                    }
                }

            }.transform { }
    }

    private fun listenEthereumBalancesByTrigger(
        chain: Chain,
        metaAccount: MetaAccount
    ): Flow<Result<Any>> {
        return trigger.map { triggeredChainId ->
            val specificChainTriggered = triggeredChainId != null
            val currentChainTriggered = triggeredChainId == chain.id

            if (specificChainTriggered && currentChainTriggered.not()) return@map Result.failure()

            kotlin.runCatching { fetchEthereumBalances(chain, listOf(metaAccount)) }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun subscribeChainBalances(
        chain: Chain,
        metaAccount: MetaAccount
    ): Flow<Result<Any>> {
        val chainUpdateFlow =
            chainRegistry.getRuntimeProviderOrNull(chain.id)
                ?.observeWithTimeout(RUNTIME_AWAITING_TIMEOUT)
                ?.flatMapLatest { runtimeResult ->
                    if (runtimeResult.isFailure) {
                        networkStateService.notifyChainSyncProblem(chain.toSyncIssue())
                        return@flatMapLatest flowOf(runtimeResult)
                    }
                    val runtime = runtimeResult.requireValue()
                    networkStateService.notifyChainSyncSuccess(chain.id)

                    val storageKeys =
                        buildStorageKeys(
                            chain,
                            metaAccount,
                            runtime
                        ).onFailure { return@flatMapLatest flowOf(Result.failure<Any>(it)) }
                            .getOrNull()
                            ?: return@flatMapLatest flowOf(Result.failure<Any>(RuntimeException("Can't build storage keys for meta account ${metaAccount.name}, chain: ${chain.name}")))

                    val socketService = runCatching { chainRegistry.getSocketOrNull(chain.id) }
                        .onFailure { return@flatMapLatest flowOf(Result.failure<Any>(it)) }
                        .getOrNull()
                        ?: return@flatMapLatest flowOf(Result.failure<Any>(RuntimeException("Error getting socket for chain ${chain.name}")))

                    val request = SubscribeBalanceRequest(storageKeys.mapNotNull { it.key })

                    combine(socketService.subscriptionFlowCatching(request)) { subscriptionsChangeResults ->
                        subscriptionsChangeResults.forEach { subscriptionChangeResult ->
                            subscriptionChangeResult.onFailure { logError(chain, it) }

                            val subscriptionChange =
                                subscriptionChangeResult.getOrNull()
                                    ?: return@combine Result.failure(subscriptionChangeResult.requireException())

                            val storageChange = subscriptionChange.storageChange()
                            val storageKeyToHex = storageChange.changes.map { it[0]!! to it[1] }

                            if (chain.utilityAsset != null && chain.utilityAsset!!.typeExtra == ChainAssetType.Equilibrium) {
                                val storageKeyToHexRaw =
                                    storageKeyToHex.singleOrNull() ?: return@combine Result.success(
                                        Unit
                                    )
                                val eqHexRaw = storageKeyToHexRaw.second
                                val balanceData = bindEquilibriumAccountData(eqHexRaw, runtime)
                                val balances = balanceData?.data?.balances.orEmpty()
                                chain.assets.forEach { asset ->
                                    val balance = balances.getOrDefault(
                                        asset.currencyId?.toBigInteger().orZero(), null
                                    ).orZero()
                                    val metadata =
                                        storageKeys.firstOrNull { it.key == storageKeyToHexRaw.first }
                                            ?: return@forEach
                                    assetCache.updateAsset(
                                        metadata.metaAccountId,
                                        metadata.accountId,
                                        asset,
                                        SimpleBalanceData(balance)
                                    )
                                }
                                return@combine Result.success(Unit)
                            }

                            storageKeyToHex.mapNotNull { (key, hexRaw) ->
                                val metadata = storageKeys.firstOrNull { it.key == key }
                                    ?: return@mapNotNull null
                                val balanceData = handleBalanceResponse(
                                    runtime,
                                    metadata.asset,
                                    hexRaw
                                ).onFailure { logError(chain, it) }

                                assetCache.updateAsset(
                                    metadata.metaAccountId,
                                    metadata.accountId,
                                    metadata.asset,
                                    balanceData.getOrNull()
                                )

                                fetchTransfers(
                                    storageChange.block,
                                    chain,
                                    metadata.accountId
                                )
                            }
                        }
                        Result.success(Unit)
                    }
                } ?: flowOf(Result.failure("Can't find RuntimeProvider for chain: ${chain.name}"))
        return chainUpdateFlow
    }

    private suspend fun fetchEthereumBalances(
        chain: Chain,
        accounts: List<MetaAccount>
    ) {
        accounts.forEach { account ->
            val address = account.address(chain) ?: return@forEach
            val accountId = account.accountId(chain) ?: return@forEach
            chain.assets.forEach { asset ->
                val balance =
                    kotlin.runCatching { ethereumRemoteSource.fetchEthBalance(asset, address) }
                        .onFailure {
                            Log.d(
                                "BalanceUpdateSystem",
                                "fetchEthBalance error ${it.message} ${it.localizedMessage} $it"
                            )
                        }
                        .getOrNull() ?: return@forEach
                val balanceData = SimpleBalanceData(balance)
                assetCache.updateAsset(
                    metaId = account.id,
                    accountId = accountId,
                    asset = asset,
                    balanceData = balanceData
                )
            }
        }
    }

    private fun logError(chain: Chain, error: Throwable) {
        Log.e(
            "BalancesUpdateSystem",
            "Failed to subscribe to balances in ${chain.name}: ${error.message}",
            error
        )
    }


    private suspend fun fetchTransfers(blockHash: String, chain: Chain, accountId: AccountId) {
        runCatching {
            val result =
                substrateSource.fetchAccountTransfersInBlock(chain.id, blockHash, accountId)

            val blockTransfers = result.getOrNull() ?: return

            val local = blockTransfers.map {
                val localStatus = when (it.statusEvent) {
                    ExtrinsicStatusEvent.SUCCESS -> Operation.Status.COMPLETED
                    ExtrinsicStatusEvent.FAILURE -> Operation.Status.FAILED
                    null -> Operation.Status.PENDING
                }

                createTransferOperationLocal(it.extrinsic, localStatus, accountId, chain)
            }

            withContext(Dispatchers.IO) { operationDao.insertAll(local) }
        }.onFailure {
            Log.d(
                "BalancesUpdateSystem",
                "Failed to fetch transfers for chain ${chain.name} (${chain.id}) $it "
            )
        }
    }

    private suspend fun createTransferOperationLocal(
        extrinsic: TransferExtrinsic,
        status: Operation.Status,
        accountId: ByteArray,
        chain: Chain
    ): OperationLocal {
        val localCopy = operationDao.getOperation(extrinsic.hash)

        val fee = localCopy?.fee

        val senderAddress = chain.addressOf(extrinsic.senderId)
        val recipientAddress = chain.addressOf(extrinsic.recipientId)

        return OperationLocal.manualTransfer(
            hash = extrinsic.hash,
            chainId = chain.id,
            address = chain.addressOf(accountId),
            chainAssetId = chain.utilityAsset?.id.orEmpty(), // TODO do not hardcode chain asset id
            amount = extrinsic.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = recipientAddress,
            fee = fee,
            status = mapOperationStatusToOperationLocalStatus(status),
            source = OperationLocal.Source.BLOCKCHAIN
        )
    }

    override fun start(): Flow<Updater.SideEffect> {
        return emptyFlow()//subscribeFlow()
    }
}

// Request with id = 0 helps to indicate balances subscriptions in logs
class SubscribeBalanceRequest(storageKeys: List<String>) : RuntimeRequest(
    "state_subscribeStorage",
    listOf(storageKeys),
    0
)