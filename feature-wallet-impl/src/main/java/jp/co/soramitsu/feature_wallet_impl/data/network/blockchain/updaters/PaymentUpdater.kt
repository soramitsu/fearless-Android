package jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.updaters

import jp.co.soramitsu.common.data.network.runtime.binding.ExtrinsicStatusEvent
import jp.co.soramitsu.common.utils.encode
import jp.co.soramitsu.core_api.data.network.Updater
import jp.co.soramitsu.core_db.dao.TransactionDao
import jp.co.soramitsu.core_db.model.TransactionLocal
import jp.co.soramitsu.core_db.model.TransactionSource
import jp.co.soramitsu.fearless_utils.scale.EncodableStruct
import jp.co.soramitsu.fearless_utils.ss58.SS58Encoder
import jp.co.soramitsu.feature_account_api.domain.interfaces.AccountRepository
import jp.co.soramitsu.feature_account_api.domain.model.Account
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transaction
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_impl.data.cache.AssetCache
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.SubstrateRemoteSource
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountData
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.account.AccountInfoSchema
import jp.co.soramitsu.feature_wallet_impl.data.network.blockchain.struct.extrinsic.TransferExtrinsic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach

class PaymentUpdater(
    accountRepository: AccountRepository,
    private val substrateSource: SubstrateRemoteSource,
    private val sS58Encoder: SS58Encoder,
    private val assetCache: AssetCache,
    private val transactionsDao: TransactionDao
) : AccountUpdater(accountRepository) {

    override suspend fun listenForUpdates(account: Account): Flow<Updater.SideEffect> {
        return substrateSource.listenForAccountUpdates(account.address)
            .onEach { change ->
                updateAssetBalance(account, change.newAccountInfo)

                fetchTransfers(account, change.block)
            }
            .flowOn(Dispatchers.IO)
            .noSideAffects()
    }

    private suspend fun updateAssetBalance(
        account: Account,
        accountInfo: EncodableStruct<AccountInfoSchema>
    ) = assetCache.updateAsset(account) { cachedAsset ->
        val data = accountInfo[accountInfo.schema.data]

        cachedAsset.copy(
            freeInPlanks = data[AccountData.free],
            reservedInPlanks = data[AccountData.reserved],
            miscFrozenInPlanks = data[AccountData.miscFrozen],
            feeFrozenInPlanks = data[AccountData.feeFrozen]
        )
    }

    private suspend fun fetchTransfers(account: Account, blockHash: String) {
        val result = substrateSource.fetchAccountTransfersInBlock(blockHash, account)

        val blockTransfers = result.getOrNull() ?: return

        val local = blockTransfers.map {
            val localStatus = when (it.statusEvent) {
                ExtrinsicStatusEvent.SUCCESS -> Transaction.Status.COMPLETED
                ExtrinsicStatusEvent.FAILURE -> Transaction.Status.FAILED
            }

            createTransactionLocal(it.extrinsic, localStatus, account)
        }

        transactionsDao.insert(local)
    }

    private suspend fun createTransactionLocal(
        extrinsic: TransferExtrinsic,
        status: Transaction.Status,
        account: Account
    ): TransactionLocal {
        val localCopy = transactionsDao.getTransaction(extrinsic.hash)

        val fee = localCopy?.feeInPlanks

        val networkType = account.network.type
        val token = Token.Type.fromNetworkType(networkType)

        val senderAddress = sS58Encoder.encode(extrinsic.senderId, networkType)
        val recipientAddress = sS58Encoder.encode(extrinsic.recipientId, networkType)

        return TransactionLocal(
            hash = extrinsic.hash,
            accountAddress = account.address,
            senderAddress = senderAddress,
            recipientAddress = recipientAddress,
            source = TransactionSource.BLOCKCHAIN,
            status = status,
            feeInPlanks = fee,
            token = token,
            amount = token.amountFromPlanks(extrinsic.amountInPlanks),
            date = System.currentTimeMillis(),
            networkType = networkType
        )
    }
}