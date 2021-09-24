package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import android.util.Log
import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.core.model.chainId
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.feature_account_api.domain.model.accountIdIn
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.feature_wallet_api.data.cache.updateAsset
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.TransferExtrinsic
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ChainId
import jp.co.soramitsu.runtime.multiNetwork.getRuntime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onEach

class PaymentUpdaterFactory(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    private val scope: AccountUpdateScope,
) {

    fun create(chainId: ChainId): PaymentUpdater {
        return PaymentUpdater(
            substrateSource,
            assetCache,
            operationDao,
            chainRegistry,
            scope,
            chainId
        )
    }
}

class PaymentUpdater(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val chainRegistry: ChainRegistry,
    override val scope: AccountUpdateScope,
    private val chainId: ChainId = Node.NetworkType.POLKADOT.chainId,
) : Updater {

    override val requiredModules: List<String> = listOf(Modules.SYSTEM)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val chain = chainRegistry.getChain(chainId)

        Log.d("RX", "Starting balance updater for $chainId")

        val accountId = scope.getAccount().accountIdIn(chain) ?: return emptyFlow()
        val runtime = chainRegistry.getRuntime(chainId)

        val key = runtime.metadata.system().storage("Account").storageKey(runtime, accountId)

        Log.d("RX", "Subscribing for balance in $chainId")

        return storageSubscriptionBuilder.subscribe(key)
            .onEach { change ->
                val newAccountInfo = bindAccountInfoOrDefault(change.value, runtime)

                Log.d("RX", "Received new balance info in $chainId")

                assetCache.updateAsset(accountId, chain.utilityAsset, newAccountInfo)

                fetchTransfers(change.block, chain, accountId)
            }
            .noSideAffects()
    }

    private suspend fun fetchTransfers(blockHash: String, chain: Chain, accountId: AccountId) {
        val result = substrateSource.fetchAccountTransfersInBlock(chainId, blockHash, accountId)

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
    }

    private suspend fun createTransferOperationLocal(
        extrinsic: TransferExtrinsic,
        status: Operation.Status,
        accountId: ByteArray,
        chain: Chain,
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
            source = OperationLocal.Source.BLOCKCHAIN,
        )
    }
}
