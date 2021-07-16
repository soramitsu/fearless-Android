package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.utils.Modules
import jp.co.soramitsu.common.utils.SuspendableProperty
import jp.co.soramitsu.common.utils.networkType
import jp.co.soramitsu.common.utils.system
import jp.co.soramitsu.common.utils.toAddress
import jp.co.soramitsu.core.updater.SubscriptionBuilder
import jp.co.soramitsu.core.updater.Updater
import jp.co.soramitsu.core_db.dao.SubqueryHistoryDao
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.SubqueryHistoryModel
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.fearless_utils.runtime.RuntimeSnapshot
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder.toAccountId
import jp.co.soramitsu.feature_account_api.domain.updaters.AccountUpdateScope
import jp.co.soramitsu.feature_wallet_api.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_api.data.cache.bindAccountInfoOrDefault
import jp.co.soramitsu.feature_wallet_api.data.cache.updateAsset
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapTokenTypeToTokenTypeLocal
import jp.co.soramitsu.feature_wallet_api.domain.model.SubqueryElement
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.mappers.mapSubqueryElementStatusToSubqueryHistoryModelStatus
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.bindings.TransferExtrinsic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class PaymentUpdater(
    private val substrateSource: SubstrateRemoteSource,
    private val assetCache: AssetCache,
    private val subqueryHistoryDao: SubqueryHistoryDao,
    private val runtimeProperty: SuspendableProperty<RuntimeSnapshot>,
    override val scope: AccountUpdateScope,
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
                ExtrinsicStatusEvent.SUCCESS -> SubqueryElement.Status.COMPLETED
                ExtrinsicStatusEvent.FAILURE -> SubqueryElement.Status.FAILED
                null -> SubqueryElement.Status.PENDING
            }

            createSubqueryHistoryElement(it.extrinsic, localStatus, address)
        }

        subqueryHistoryDao.insertAll(local)
    }

    private suspend fun createSubqueryHistoryElement(
        extrinsic: TransferExtrinsic,
        status: SubqueryElement.Status,
        accountAddress: String,
    ): SubqueryHistoryModel {
        val localCopy = subqueryHistoryDao.getTransaction(extrinsic.hash)

        val fee = localCopy?.fee

        val networkType = accountAddress.networkType()
        val tokenType = Token.Type.fromNetworkType(networkType)

        val senderAddress = extrinsic.senderId.toAddress(networkType)
        val recipientAddress = extrinsic.recipientId.toAddress(networkType)

        return SubqueryHistoryModel(
            hash = extrinsic.hash,
            address = accountAddress,
            time = System.currentTimeMillis(),
            tokenType = mapTokenTypeToTokenTypeLocal(tokenType),
            call = "Transfer",
            amount = extrinsic.amountInPlanks,
            sender = senderAddress,
            receiver = recipientAddress,
            fee = fee,
            status = mapSubqueryElementStatusToSubqueryHistoryModelStatus(status),
            source = SubqueryHistoryModel.Source.BLOCKCHAIN
        )
    }
}
