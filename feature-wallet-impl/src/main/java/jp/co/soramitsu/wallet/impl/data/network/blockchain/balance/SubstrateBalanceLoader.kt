package jp.co.soramitsu.wallet.impl.data.network.blockchain.balance

import android.annotation.SuppressLint
import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.impl.domain.StorageKeyWithMetadata
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalance
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.EmptyBalance
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.data.network.runtime.binding.toAssetBalance
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.AssetBalanceUpdateItem
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.storage.source.RemoteStorageSource
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.shared_utils.wsrpc.request.runtime.storage.storageChange
import jp.co.soramitsu.shared_utils.wsrpc.subscriptionFlow
import jp.co.soramitsu.wallet.api.data.BalanceLoader
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindAssetsAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters.SubscribeBalanceRequest
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigInteger

class SubstrateBalanceLoader(
    chain: Chain,
    private val chainRegistry: ChainRegistry,
    private val remoteStorageSource: RemoteStorageSource,
    private val substrateSource: SubstrateRemoteSource,
    private val operationDao: OperationDao,
) : BalanceLoader(chain) {

    companion object {
        private const val CHAIN_SYNC_TIMEOUT_MILLIS: Long = 15_000L
    }

    private val tag = "SubstrateBalanceLoader (${chain.name})"

    override suspend fun loadBalance(metaAccounts: Set<MetaAccount>): List<AssetBalanceUpdateItem> {
        return supervisorScope {
            val metaAccountsWithSubstrate = metaAccounts.filter { it.substratePublicKey != null || it.ethereumPublicKey != null }
            if(metaAccountsWithSubstrate.isEmpty()) {
                return@supervisorScope emptyList()
            }
            val emptyAssets: MutableList<AssetBalanceUpdateItem> = mutableListOf()
            val runtime = withTimeoutOrNull(CHAIN_SYNC_TIMEOUT_MILLIS) {
                if (chainRegistry.checkChainSyncedUp(chain).not()) {
                    chainRegistry.setupChain(chain)
                }

                // awaiting runtime snapshot
                chainRegistry.awaitRuntimeProvider(chain.id).get()
            }

            val allAccountsStorageKeys =
                metaAccountsWithSubstrate.mapNotNull { metaAccount ->
                    val accountId = metaAccount.accountId(chain) ?: return@mapNotNull null

                    buildSubstrateStorageKeys(
                        chain,
                        runtime,
                        metaAccount.id,
                        accountId
                    )
                }.flatten()

            val keysToQuery =
                allAccountsStorageKeys.mapNotNull { metadata ->
                    // if storage key build is failed - we put the empty assets
                    if (metadata.key == null) {
                        emptyAssets.add(
                            AssetBalanceUpdateItem(
                                accountId = metadata.accountId,
                                id = metadata.asset.id,
                                chainId = metadata.asset.chainId,
                                metaId = metadata.metaAccountId,
                                freeInPlanks = BigInteger.valueOf(-1)
                            )
                        )
                    }
                    metadata.key
                }.toList()

            val storageKeyToResult = remoteStorageSource.queryKeys(
                keysToQuery,
                chain.id,
                null
            )

            allAccountsStorageKeys.map { metadata ->
                val hexRaw =
                    storageKeyToResult.getOrDefault(
                        metadata.key,
                        null
                    )

                val assetBalance =
                    runtime?.let {
                        handleBalanceResponse(
                            it,
                            metadata.asset,
                            hexRaw
                        ).getOrNull().toAssetBalance()
                    } ?: AssetBalance()

                AssetBalanceUpdateItem(
                    id = metadata.asset.id,
                    chainId = chain.id,
                    accountId = metadata.accountId,
                    metaId = metadata.metaAccountId,
                    freeInPlanks = assetBalance.freeInPlanks,
                    reservedInPlanks = assetBalance.reservedInPlanks,
                    miscFrozenInPlanks = assetBalance.miscFrozenInPlanks,
                    feeFrozenInPlanks = assetBalance.feeFrozenInPlanks
                )
            } + emptyAssets

        }
    }

    override fun subscribeBalance(metaAccount: MetaAccount): Flow<BalanceLoaderAction> {
        return flow { emit(chainRegistry.awaitRuntimeProvider(chain.id).get()) }
            .combine(flow { emit(chainRegistry.awaitConnection(chain.id).socketService) }) { runtime, socketService ->
            runtime to socketService
        }.flatMapLatest { (runtime, socketService) ->
            channelFlow {
                val storageKeys = buildStorageKeys(chain, metaAccount, runtime).onFailure {
                    logError("Error build storage keys for chain ${chain.name}: $it")
                }
                    .getOrNull()
                    ?: return@channelFlow

                val request = SubscribeBalanceRequest(storageKeys.mapNotNull { it.key })

                socketService.subscriptionFlow(request).collect { subscriptionChange ->
                    val storageChange = subscriptionChange.storageChange()
                    val storageKeyToHex = storageChange.changes.map { it[0]!! to it[1] }

                    storageKeyToHex.onEach { (key, hexRaw) ->
                        val metadata = storageKeys.firstOrNull { it.key == key }
                            ?: return@onEach

                        val balanceData = handleBalanceResponse(runtime, metadata.asset, hexRaw)
                            .onFailure {
                                logError("Failed to handle balance response chain ${chain.name}, asset: ${metadata.asset.name}: $it")
                            }.getOrNull()?.toAssetBalance() ?: return@onEach

                        trySend(
                            BalanceLoaderAction.UpdateBalance(
                                AssetBalanceUpdateItem(
                                    id = metadata.asset.id,
                                    chainId = chain.id,
                                    accountId = metadata.accountId,
                                    metaId = metadata.metaAccountId,
                                    freeInPlanks = balanceData.freeInPlanks,
                                    reservedInPlanks = balanceData.reservedInPlanks,
                                    miscFrozenInPlanks = balanceData.miscFrozenInPlanks,
                                    feeFrozenInPlanks = balanceData.feeFrozenInPlanks
                                )
                            )
                        )
                    }
                }
            }
        }.catch { logError("error: $it") }
    }

    private fun buildStorageKeys(
        chain: Chain,
        metaAccount: MetaAccount,
        runtime: RuntimeSnapshot
    ): Result<List<StorageKeyWithMetadata>> {
        val accountId = metaAccount.accountId(chain)
            ?: return Result.failure(RuntimeException("Can't get account id for meta account ${metaAccount.name}, chain: ${chain.name}"))

        return Result.success(buildStorageKeys(chain, runtime, metaAccount.id, accountId))
    }

    private fun buildStorageKeys(
        chain: Chain,
        runtime: RuntimeSnapshot?,
        metaAccountId: Long,
        accountId: ByteArray
    ): List<StorageKeyWithMetadata> {
        return buildSubstrateStorageKeys(chain, runtime, metaAccountId, accountId)
    }

    private fun buildSubstrateStorageKeys(
        chain: Chain,
        runtime: RuntimeSnapshot?,
        metaAccountId: Long,
        accountId: ByteArray
    ): List<StorageKeyWithMetadata> {
        return chain.assets.map { asset ->
            StorageKeyWithMetadata(
                asset, metaAccountId, accountId,
                runtime?.let { constructBalanceKey(it, asset, accountId) }
            )
        }
    }

    private fun constructBalanceKey(
        runtime: RuntimeSnapshot,
        asset: jp.co.soramitsu.core.models.Asset,
        accountId: ByteArray
    ): String? {
        val keyConstructionResult = runCatching {
            val currency =
                asset.currency ?: return@runCatching runtime.metadata.system().storage("Account")
                    .storageKey(runtime, accountId)
            when (asset.typeExtra) {
                null, ChainAssetType.Normal,
                ChainAssetType.Equilibrium,
                ChainAssetType.SoraUtilityAsset -> runtime.metadata.system().storage("Account")
                    .storageKey(runtime, accountId)

                ChainAssetType.OrmlChain,
                ChainAssetType.OrmlAsset,
                ChainAssetType.VToken,
                ChainAssetType.VSToken,
                ChainAssetType.Stable,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.SoraAsset,
                ChainAssetType.AssetId,
                ChainAssetType.Token2,
                ChainAssetType.Xcm,
                ChainAssetType.LiquidCrowdloan -> runtime.metadata.tokens().storage("Accounts")
                    .storageKey(runtime, accountId, currency)

                ChainAssetType.Assets -> runtime.metadata.module(Modules.ASSETS).storage("Account")
                    .storageKey(runtime, currency, accountId)

                else -> error("Not supported type for token ${asset.symbol} in ${asset.chainName}")
            }
        }
        return keyConstructionResult
            .onFailure {
                logError("Failed to construct storage key for asset ${asset.symbol} (${asset.id}) $it ")
            }
            .getOrNull()
    }

    private fun handleBalanceResponse(
        runtime: RuntimeSnapshot,
        asset: jp.co.soramitsu.core.models.Asset,
        scale: String?
    ): Result<AssetBalanceData> {
        return runCatching {
            when (asset.typeExtra) {
                null,
                ChainAssetType.Normal,
                ChainAssetType.SoraUtilityAsset -> {
                    bindAccountInfoOrDefault(scale, runtime)
                }

                ChainAssetType.OrmlChain,
                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.SoraAsset,
                ChainAssetType.VSToken,
                ChainAssetType.AssetId,
                ChainAssetType.Token2,
                ChainAssetType.Xcm,
                ChainAssetType.Stable -> {
                    bindOrmlTokensAccountDataOrDefault(scale, runtime)
                }

                ChainAssetType.Equilibrium -> {
                    bindEquilibriumAccountData(scale, runtime) ?: EmptyBalance
                }

                ChainAssetType.Assets -> {
                    bindAssetsAccountData(scale, runtime) ?: EmptyBalance
                }

                else -> EmptyBalance
            }
        }
    }

    @SuppressLint("LogNotTimber")
    private fun logError(text: String) {
        Log.d(tag, text)
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
            logError("Failed to fetch transfers for chain ${chain.name} (${chain.id}) $it ")
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