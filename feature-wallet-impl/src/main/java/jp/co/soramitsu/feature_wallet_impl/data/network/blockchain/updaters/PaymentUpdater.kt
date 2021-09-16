package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core_db.dao.OperationDao
import jp.co.soramitsu.core_db.model.OperationLocal
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.feature_wallet_api.data.cache.updateAsset
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.Operation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapOperationStatusToOperationLocalStatus
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.TransferExtrinsic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class PaymentUpdater(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val operationDao: OperationDao,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    override val scope: AccountUpdateScope
) : Updater {

    override val requiredModules: List<String> = listOf(Modules.SYSTEM)

    override suspend fun listenForUpdates(storageSubscriptionBuilder: SubscriptionBuilder): Flow<Updater.SideEffect> {
        val address = scope.getAccount().address

        val runtime = runtimeProperty.get()
        val key = runtime.metadata.system().storage("Account").storageKey(runtime, address.toAccountId())

        return storageSubscriptionBuilder.subscribe(key)
            .onEach { change ->
                val newAccountInfo = bindAccountInfoOrDefault(change.value, runtime)

                assetCache.updateAsset(address, newAccountInfo)

                fetchTransfers(address, change.block)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private suspend fun fetchTransfers(address: String, blockHash: String) {
        val result = substrateSource.fetchAccountTransfersInBlock(blockHash, address)

        val blockTransfers = result.getOrNull() ?: return

        val local = blockTransfers.map {
            val localStatus = when (it.statusEvent) {
                ExtrinsicStatusEvent.SUCCESS -> Operation.Status.COMPLETED
                ExtrinsicStatusEvent.FAILURE -> Operation.Status.FAILED
                null -> Operation.Status.PENDING
            }

            createTransferOperationLocal(it.extrinsic, localStatus, address)
        }

        operationDao.insertAll(local)
    }

    private suspend fun createTransferOperationLocal(
        extrinsic: TransferExtrinsic,
        status: Operation.Status,
        accountAddress: String,
    ): OperationLocal {
        val localCopy = operationDao.getOperation(extrinsic.hash)

        val fee = localCopy?.fee

        val networkType = accountAddress.networkType()
        val tokenType = Token.Type.fromNetworkType(networkType)

        val senderAddress = extrinsic.senderId.toAddress(networkType)
        val recipientAddress = extrinsic.recipientId.toAddress(networkType)

        return OperationLocal.manualTransfer(
            hash = extrinsic.hash,
            accountAddress = accountAddress,
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            amount = extrinsic.amountInPlanks,
            senderAddress = senderAddress,
            receiverAddress = recipientAddress,
            fee = fee,
            status = mapOperationStatusToOperationLocalStatus(status),
            source = OperationLocal.Source.BLOCKCHAIN,
        )
    }
}
