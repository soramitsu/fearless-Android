package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.annotation.SuppressLint
import android.util.Log
import it.airgap.beaconsdk.core.internal.utils.failure
import it.airgap.beaconsdk.core.internal.utils.onEachFailure
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.failure
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.updater.UpdateSystem
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core.utils.utilityAsset
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
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.SubscribeStorageRequest
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.data.network.model.constructBalanceKey
import jp.co.soramitsu.wallet.impl.data.network.model.handleBalanceResponse
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapMerge
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
    private val accountRepository: AccountRepository,
    private val bulkRetriever: BulkRetriever,
    private val assetCache: AssetCache,
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
    private val networkStateMixin: NetworkStateMixin,
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
            accountRepository.selectedMetaAccountFlow()
        ) { chains, metaAccount ->
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
                ?.flatMapMerge { runtimeResult ->
                    if (runtimeResult.isFailure) {
                        networkStateMixin.notifyChainSyncProblem(chain.toSyncIssue())
                        return@flatMapMerge flowOf(runtimeResult)
                    }
                    val runtime = runtimeResult.requireValue()
                    networkStateMixin.notifyChainSyncSuccess(chain.id)

                    val storageKeys =
                        buildStorageKeys(
                            chain,
                            metaAccount,
                            runtime
                        ).onFailure { return@flatMapMerge flowOf(Result.failure<Any>(it)) }
                            .getOrNull()
                            ?: return@flatMapMerge flowOf(Result.failure<Any>(RuntimeException("Can't build storage keys for meta account ${metaAccount.name}, chain: ${chain.name}")))

                    val socketService = runCatching { chainRegistry.getSocketOrNull(chain.id) }
                        .onFailure { return@flatMapMerge flowOf(Result.failure<Any>(it)) }
                        .getOrNull()
                        ?: return@flatMapMerge flowOf(Result.failure<Any>(RuntimeException("Error getting socket for chain ${chain.name}")))

                    val request = SubscribeStorageRequest(storageKeys.map { it.key })

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

    private val ethBalancesFlow = combine(chainRegistry.syncedChains,
        accountRepository.allMetaAccountsFlow().filterNot { it.isEmpty() }) { chains, accounts ->
        val filtered = chains.filter { it.isEthereumChain }
        coroutineScope {
            filtered.forEach { chain ->
                launch {
                    fetchEthereumBalances(chain, accounts)
                }
            }
        }
    }

    private val substrateBalancesFlow = combine(chainRegistry.syncedChains,
        accountRepository.allMetaAccountsFlow().filterNot { it.isEmpty() }) { chains, accounts ->
        coroutineScope {
            val filtered = chains.filterNot { it.isEthereumChain }
            filtered.forEach { chain ->
                launch {
                    runCatching {
                        val runtime =
                            runCatching { chainRegistry.getRuntimeOrNull(chain.id) }.getOrNull()
                                ?: return@launch
                        val socketService =
                            runCatching { chainRegistry.getSocket(chain.id) }.getOrNull()
                                ?: return@launch
                        val storageKeys =
                            accounts.mapNotNull { metaAccount ->
                                buildStorageKeys(chain, metaAccount, runtime)
                                    .onFailure { }
                                    .getOrNull()?.toList()
                            }.flatten()
                        val queryResults = withContext(Dispatchers.IO) {
                            bulkRetriever.queryKeys(
                                socketService,
                                storageKeys.map { it.key }
                            )
                        }

                        storageKeys.map { keyWithMetadata ->
                            val hexRaw =
                                queryResults[keyWithMetadata.key]

                            val balanceData = handleBalanceResponse(
                                runtime,
                                keyWithMetadata.asset,
                                hexRaw
                            ).onFailure { logError(chain, it) }

                            assetCache.updateAsset(
                                keyWithMetadata.metaAccountId,
                                keyWithMetadata.accountId,
                                keyWithMetadata.asset,
                                balanceData.getOrNull()
                            )
                        }
                    }
                        .onFailure {
                            logError(chain, it)
                            return@launch
                        }
                }
            }
        }
    }

    private fun singleUpdateFlow(): Flow<Unit> {
        return combine(
            ethBalancesFlow,
            substrateBalancesFlow
        ) { _, _ ->
        }.onStart { emit(Unit) }.flowOn(Dispatchers.Default)
    }

    private fun buildStorageKeys(
        chain: Chain,
        metaAccount: MetaAccount,
        runtime: RuntimeSnapshot
    ): Result<List<StorageKeyWithMetadata>> {
        val accountId = metaAccount.accountId(chain)
            ?: return Result.failure(RuntimeException("Can't get account id for meta account ${metaAccount.name}, chain: ${chain.name}"))

        if (chain.utilityAsset != null && chain.utilityAsset?.typeExtra == ChainAssetType.Equilibrium) {
            val equilibriumStorageKeys = listOf(
                constructBalanceKey(
                    runtime,
                    requireNotNull(chain.utilityAsset),
                    accountId
                )?.let {
                    StorageKeyWithMetadata(
                        requireNotNull(chain.utilityAsset),
                        metaAccount.id,
                        accountId,
                        it
                    )
                })
            return Result.success(equilibriumStorageKeys.filterNotNull())
        }

        val storageKeys = chain.assets.map { asset ->
            constructBalanceKey(runtime, asset, accountId)?.let {
                StorageKeyWithMetadata(asset, metaAccount.id, accountId, it)
            }
        }
        return Result.success(storageKeys.filterNotNull())
    }

    data class StorageKeyWithMetadata(
        val asset: Asset,
        val metaAccountId: Long,
        val accountId: AccountId,
        val key: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as StorageKeyWithMetadata

            if (asset != other.asset) return false
            if (metaAccountId != other.metaAccountId) return false
            if (!accountId.contentEquals(other.accountId)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = asset.hashCode()
            result = 31 * result + metaAccountId.hashCode()
            result = 31 * result + accountId.contentHashCode()
            return result
        }

        override fun toString(): String {
            return "StorageKeyWithMetadata(asset=${asset.name}, metaAccountId=$metaAccountId, key='$key')"
        }
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

    override fun start(): Flow<Updater.SideEffect> {
        return combine(subscribeFlow(), singleUpdateFlow()) { sideEffect, _ -> sideEffect }
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
}
