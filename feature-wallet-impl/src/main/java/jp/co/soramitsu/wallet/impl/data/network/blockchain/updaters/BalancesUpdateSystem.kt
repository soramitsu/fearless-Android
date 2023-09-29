package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import it.airgap.beaconsdk.core.internal.utils.failure
import it.airgap.beaconsdk.core.internal.utils.onEachFailure
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.SimpleBalanceData
import jp.co.soramitsu.common.mixin.api.NetworkStateMixin
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
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
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.EthereumRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.data.network.model.constructBalanceKey
import jp.co.soramitsu.wallet.impl.data.network.model.handleBalanceResponse
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flatMapMerge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withContext
import org.web3j.utils.Numeric

private const val RUNTIME_AWAITING_TIMEOUT = 10_000L

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

    private val trigger = BalanceUpdateTrigger.observe()

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun subscribeFlow(): Flow<Updater.SideEffect> {
        return combine(
            chainRegistry.syncedChains,
            accountRepository.selectedMetaAccountFlow()
        ) { chains, metaAccount ->
            chains to metaAccount
        }.flatMapLatest { (chains, metaAccount) ->
            val chainsFlow = chains.map { chain ->
                if (chain.isEthereumChain) {
                    listenEthereumBalancesByTrigger(chain, metaAccount)
                } else {
                    subscribeChainBalances(chain, metaAccount).onEachFailure { logError(chain, it) }
                }
            }

            combine(chainsFlow) { }.transform { }
        }
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
        val runtimeVersion =
            kotlin.runCatching {
                chainRegistry.getRemoteRuntimeVersion(
                    chain.id
                )
            }
                .getOrNull() ?: 0
        val chainUpdateFlow =
            chainRegistry.getRuntimeProvider(chain.id).observeWithTimeout(RUNTIME_AWAITING_TIMEOUT)
                .flatMapMerge { runtimeResult ->
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

                            storageKeys.map { keyWithMetadata ->
                                val hexRaw =
                                    storageKeyToHex.firstOrNull { it.first == keyWithMetadata.key }

                                val balanceData = handleBalanceResponse(
                                    runtime,
                                    keyWithMetadata.asset.typeExtra,
                                    hexRaw?.second,
                                    runtimeVersion
                                ).onFailure { logError(chain, it) }

                                assetCache.updateAsset(
                                    keyWithMetadata.metaAccountId,
                                    keyWithMetadata.accountId,
                                    keyWithMetadata.asset,
                                    balanceData.getOrNull()
                                )

                                fetchTransfers(
                                    storageChange.block,
                                    chain,
                                    keyWithMetadata.accountId
                                )
                            }
                        }
                        Result.success(Unit)
                    }
                }
        return chainUpdateFlow
    }

    private fun singleUpdateFlow(): Flow<Unit> {
        return combine(
            chainRegistry.syncedChains,
            accountRepository.allMetaAccountsFlow().filterNot { it.isEmpty() }
        ) { chains, accounts ->
            chains.sortedByDescending { it.isEthereumChain }.forEach { chain ->

                if (chain.isEthereumChain) {
                    kotlin.runCatching {
                        fetchEthereumBalances(chain, accounts)
                    }
                    return@forEach
                }
                runCatching {
                    val runtime =
                        runCatching { chainRegistry.getRuntimeOrNull(chain.id) }.getOrNull()
                            ?: return@forEach
                    val runtimeVersion = chainRegistry.getRemoteRuntimeVersion(chain.id) ?: 0
                    val socketService =
                        runCatching { chainRegistry.getSocket(chain.id) }.getOrNull()
                            ?: return@forEach
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
                            keyWithMetadata.asset.typeExtra,
                            hexRaw,
                            runtimeVersion
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
                        return@forEach
                    }
            }
        }.onStart { emit(Unit) }.flowOn(Dispatchers.Default)
    }

    private fun buildStorageKeys(
        chain: Chain,
        metaAccount: MetaAccount,
        runtime: RuntimeSnapshot
    ): Result<List<StorageKeyWithMetadata>> {
        val accountId = metaAccount.accountId(chain)
            ?: return Result.failure(RuntimeException("Can't get account id for meta account ${metaAccount.name}, chain: ${chain.name}"))

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
    }

    private suspend fun fetchEthereumBalances(
        chain: Chain,
        accounts: List<MetaAccount>
    ) {
        val responses = ethereumRemoteSource.fetchEthBalances(chain, accounts)
        responses.forEach { (response, assetId, metaAccount) ->
            val asset = chain.assetsById.getOrDefault(assetId, null) ?: return@forEach
            val accountId = metaAccount.accountId(chain) ?: return@forEach
            (response.result as? String)?.let {
                val balance = Numeric.decodeQuantity(it)

                assetCache.updateAsset(
                    metaId = metaAccount.id,
                    accountId = accountId,
                    asset = asset,
                    balanceData = SimpleBalanceData(balance)
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
