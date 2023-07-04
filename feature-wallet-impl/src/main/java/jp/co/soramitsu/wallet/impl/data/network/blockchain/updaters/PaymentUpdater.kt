package jp.co.soramitsu.wallet.impl.data.network.blockchain.updaters

import android.util.Log
import jp.co.soramitsu.account.api.domain.model.MetaAccount
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.common.data.network.runtime.binding.AssetBalanceData
import jp.co.soramitsu.common.data.network.runtime.binding.EmptyBalance
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.mixin.api.UpdatesMixin
import jp.co.soramitsu.common.mixin.api.UpdatesProviderUi
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.tokens
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.coredb.dao.OperationDao
import jp.co.soramitsu.coredb.model.OperationLocal
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.shared_utils.runtime.AccountId
import jp.co.soramitsu.shared_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.shared_utils.runtime.metadata.module
import jp.co.soramitsu.shared_utils.runtime.metadata.storage
import jp.co.soramitsu.shared_utils.runtime.metadata.storageKey
import jp.co.soramitsu.wallet.api.data.cache.AssetCache
import jp.co.soramitsu.wallet.api.data.cache.bind9420AccountInfo
import jp.co.soramitsu.wallet.api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.wallet.api.data.cache.bindAssetsAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindEquilibriumAccountData
import jp.co.soramitsu.wallet.api.data.cache.bindOrmlTokensAccountDataOrDefault
import jp.co.soramitsu.wallet.api.data.cache.updateAsset
import jp.co.soramitsu.wallet.impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.wallet.impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.wallet.impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext

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

        val runtimeVersion = withContext(Dispatchers.IO) { chainRegistry.getRemoteRuntimeVersion(chain.id) ?: 0 }
        val runtime = runCatching { chainRegistry.getRuntime(chainId) }
            .onFailure {
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
            withContext(Dispatchers.Default) {
                chain.assets.sortedByDescending { it.isUtility }.mapNotNull { asset ->
                    updatesMixin.startUpdateAsset(metaAccount.id, chainId, accountId, asset.id)

                    val key = constructBalanceKey(runtime, asset, accountId) ?: return@mapNotNull null

                    storageSubscriptionBuilder.subscribe(key)
                        .map { change ->
                            val balanceData = handleBalanceResponse(runtime, asset.typeExtra, change.value, runtimeVersion)
                                .onFailure { Log.d("PaymentUpdater", "Failed to handle response for asset ${asset.symbol} (${asset.id}) $it") }

                            assetCache.updateAsset(metaAccount.id, accountId, asset, balanceData.getOrNull())

                            if (asset.isUtility) {
                                fetchTransfers(change.block, chain, accountId)
                            }
                        }
                }.merge()
            }
        }.merge().noSideAffects()
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

            withContext(Dispatchers.IO) { operationDao.insertAll(local) }
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

fun constructBalanceKey(
    runtime: RuntimeSnapshot,
    asset: Asset,
    accountId: ByteArray
): String? {
    val keyConstructionResult = runCatching {
        val currency = asset.currency ?: return@runCatching runtime.metadata.system().storage("Account").storageKey(runtime, accountId)
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
            ChainAssetType.AssetId,
            ChainAssetType.Token2,
            ChainAssetType.LiquidCrowdloan -> runtime.metadata.tokens().storage("Accounts").storageKey(runtime, accountId, currency)

            ChainAssetType.Assets -> runtime.metadata.module(Modules.ASSETS).storage("Account").storageKey(runtime, currency, accountId)

            ChainAssetType.Unknown -> error("Not supported type for token ${asset.symbol} in ${asset.chainName}")
        }
    }
    return keyConstructionResult
        .onFailure { Log.d("PaymentUpdater", "Failed to construct storage key for asset ${asset.symbol} (${asset.id}) $it ") }
        .getOrNull()
}

fun handleBalanceResponse(
    runtime: RuntimeSnapshot,
    assetType: ChainAssetType?,
    scale: String?,
    runtimeVersion: Int
): Result<AssetBalanceData> {
    return runCatching {
        when (assetType) {
            null,
            ChainAssetType.Normal,
            ChainAssetType.SoraUtilityAsset -> {
                if (runtimeVersion >= 9420) {
                    bind9420AccountInfo(scale, runtime)
                } else {
                    bindAccountInfoOrDefault(scale, runtime)
                }
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
            ChainAssetType.Stable -> {
                bindOrmlTokensAccountDataOrDefault(scale, runtime)
            }

            ChainAssetType.Equilibrium -> {
                bindEquilibriumAccountData(scale, runtime) ?: EmptyBalance
            }

            ChainAssetType.Assets -> {
                bindAssetsAccountData(scale, runtime) ?: EmptyBalance
            }

            ChainAssetType.Unknown -> EmptyBalance
        }
    }
}
