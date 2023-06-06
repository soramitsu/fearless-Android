package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.model.StorageChange
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.bind9420AccountInfo
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

class PaymentUpdaterFactory(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    private val scope: AccountUpdateScope,
    private val updatesMixin: UpdatesMixin
) {

    fun create(chain: Chain, metaAccount: MetaAccount): Updater {
        return PaymentUpdater(
            substrateSource,
            assetCache,
            operationDao,
            chainRegistry,
            scope,
            chain,
            updatesMixin,
            metaAccount
        )
    }
}

class PaymentUpdater(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    override val scope: AccountUpdateScope,
    private val chain: Chain,
    private val updatesMixin: UpdatesMixin,
    private val metaAccount: MetaAccount
) : Updater, UpdatesProviderUi by updatesMixin {

    override val requiredModules: List<String> = listOf(Modules.SYSTEM)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chainId = chain.id
        val runtimeResult = runCatching { chainRegistry.getRuntime(chainId) }
        val runtime = runtimeResult.onFailure {
            Log.e("PaymentUpdater", "Failed to get runtime for chain ${chain.name} (${chain.id}) $it")
            return emptyFlow()
        }.getOrNull() ?: return emptyFlow()

        val chainAccount = metaAccount.chainAccounts[chainId]

        val accountIdsToCheck = listOfNotNull(
            metaAccount.accountId(chain),
            chainAccount?.accountId
        )

        if (accountIdsToCheck.isEmpty()) return emptyFlow()

        return accountIdsToCheck.map { accountId ->
            chain.assets.sortedByDescending { it.isUtility }.mapNotNull { asset ->
                updatesMixin.startUpdateAsset(metaAccount.id, chainId, accountId, asset.id)
                val keyResult = runCatching {
                    constructKey(runtime, asset, accountId)
                }.onFailure {
                    Log.d("PaymentUpdater", "Failed to construct storage key for asset ${asset.symbolToShow} (${asset.id}) $it ")
                }

                keyResult.getOrNull()?.let { key ->
                    storageSubscriptionBuilder.subscribe(key)
                        .map { change ->
                            handleResponse(metaAccount.id, runtime, accountId, asset, change)

                            if (asset.isUtility) {
                                fetchTransfers(change.block, chain, accountId)
                            }
                        }
                }
            }.merge()
        }.merge().noSideAffects()
    }

    private suspend fun handleResponse(
        metaId: Long,
        runtime: RuntimeSnapshot,
        accountId: ByteArray,
        asset: Asset,
        change: StorageChange
    ) {
        runCatching {
            when (asset.typeExtra) {
                null, ChainAssetType.Normal,
                ChainAssetType.SoraUtilityAsset -> {
                    val runtimeVersion = chainRegistry.getRemoteRuntimeVersion(chain.id) ?: 0
                    val newAccountInfo = if (runtimeVersion >= 9420) {
                        bind9420AccountInfo(change.value, runtime)
                    } else {
                        bindAccountInfoOrDefault(change.value, runtime)
                    }
                    assetCache.updateAsset(metaId, accountId, asset, newAccountInfo)
                }

                ChainAssetType.OrmlChain,
                ChainAssetType.OrmlAsset,
                ChainAssetType.ForeignAsset,
                ChainAssetType.StableAssetPoolToken,
                ChainAssetType.LiquidCrowdloan,
                ChainAssetType.VToken,
                ChainAssetType.SoraAsset,
                ChainAssetType.VSToken,
                ChainAssetType.Stable -> {
                    val ormlTokensAccountData = bindOrmlTokensAccountDataOrDefault(change.value, runtime)

                    assetCache.updateAsset(metaId, accountId, asset) {
                        it.copy(
                            accountId = accountId,
                            freeInPlanks = ormlTokensAccountData.free,
                            miscFrozenInPlanks = ormlTokensAccountData.frozen,
                            reservedInPlanks = ormlTokensAccountData.reserved
                        )
                    }
                }

                ChainAssetType.Equilibrium -> {
                    val eqAccountInfo = bindEquilibriumAccountData(change.value, runtime)
                    assetCache.updateAsset(metaId, accountId, asset) {
                        it.copy(
                            accountId = accountId,
                            freeInPlanks = eqAccountInfo?.data?.balances?.get(asset.currency).orZero()
                        )
                    }
                }

                ChainAssetType.Unknown -> Unit
            }
        }.onFailure { Log.d("PaymentUpdater", "Failed to handle response for asset ${asset.symbolToShow} (${asset.id}) $it ") }
    }

    private fun constructKey(
        runtime: RuntimeSnapshot,
        asset: Asset,
        accountId: ByteArray
    ): String? {
        val keyConstructionResult = runCatching {
            val currency = asset.currency
            if (currency == null) {
                runtime.metadata.system().storage("Account").storageKey(runtime, accountId)
            } else {
                when (asset.typeExtra) {
                    null, ChainAssetType.Normal,
                    ChainAssetType.Equilibrium,
                    ChainAssetType.SoraUtilityAsset -> runtime.metadata.system().storage("Account").storageKey(runtime, accountId)

                    ChainAssetType.OrmlChain,
                    ChainAssetType.OrmlAsset,
                    ChainAssetType.VToken,
                    ChainAssetType.VSToken,
                    ChainAssetType.Stable,
                    ChainAssetType.ForeignAsset,
                    ChainAssetType.StableAssetPoolToken,
                    ChainAssetType.SoraAsset,
                    ChainAssetType.LiquidCrowdloan -> runtime.metadata.tokens().storage("Accounts").storageKey(runtime, accountId, currency)

                    ChainAssetType.Unknown -> error("Not supported type for token ${asset.symbolToShow} in ${chain.name}")
                }
            }
        }
        return keyConstructionResult.getOrNull()
    }

    private suspend fun fetchTransfers(blockHash: String, chain: Chain, accountId: AccountId) {
        runCatching {
            val result = substrateSource.fetchAccountTransfersInBlock(chain.id, blockHash, accountId)

            val blockTransfers = result.getOrNull() ?: return

            val local = blockTransfers.map {
                val localStatus = when (it.statusEvent) {
                    ExtrinsicStatusEvent.SUCCESS -> Operation.Status.COMPLETED
                    ExtrinsicStatusEvent.FAILURE -> Operation.Status.FAILED
                    null -> Operation.Status.PENDING
                }

                createTransferOperationLocal(it.extrinsic, localStatus, accountId, chain)
            }

            operationDao.insertAll(local)
        }.onFailure { Log.d("PaymentUpdater", "Failed to fetch transfers for chain ${chain.name} (${chain.id}) $it ") }
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
            chainAssetId = chain.utilityAsset.id, // TODO do not hardcode chain asset id
            amount = extrinsic.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = recipientAddress,
            fee = fee,
            status = mapOperationStatusToOperationLocalStatus(status),
            source = OperationLocal.Source.BLOCKCHAIN
        )
    }
}
