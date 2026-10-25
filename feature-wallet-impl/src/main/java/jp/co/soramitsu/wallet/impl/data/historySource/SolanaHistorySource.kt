package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.solana.SolanaIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistoryException
import jp.co.soramitsu.common.data.network.solana.SolanaTransactionHistorySync
import jp.co.soramitsu.common.model.UniversalWalletIndexedOperationType
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransaction
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionDirection
import jp.co.soramitsu.common.model.UniversalWalletIndexedTransactionStatus
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.universalWalletSolanaIndexerNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class SolanaHistorySource(
    private val historySync: SolanaTransactionHistorySync,
    private val historyUrl: String
) : HistorySource {

    override suspend fun getOperations(
        pageSize: Int,
        cursor: String?,
        filters: Set<TransactionFilter>,
        accountId: AccountId,
        chain: Chain,
        chainAsset: Asset,
        accountAddress: String
    ): CursorPage<Operation> {
        if (pageSize <= 0 || TransactionFilter.TRANSFER !in filters) {
            return CursorPage(null, emptyList())
        }

        val network = chain.universalWalletSolanaIndexerNetwork() ?: return CursorPage(null, emptyList())
        val historyAsset = chainAsset.solanaHistoryAsset(network) ?: return CursorPage(null, emptyList())
        val limit = pageSize.coerceAtMost(SolanaIndexerRoutes.MAX_LIMIT)

        return runCatching {
            val page = historySync.history(
                wallet = accountAddress,
                assetId = historyAsset.assetId,
                isNative = historyAsset.isNative,
                network = network,
                baseUrl = historyUrl,
                before = cursor,
                limit = limit
            )
            val operations = page.transactions.mapNotNull { transaction ->
                transaction.toTransferOperation(accountAddress, chainAsset)
            }

            CursorPage(
                nextCursor = page.pageInfo.nextCursor,
                items = operations
            )
        }.getOrElse { error ->
            when (error) {
                is SolanaTransactionHistoryException,
                is SolanaIndexerRoutes.SolanaIndexerRouteException -> CursorPage(null, emptyList())
                else -> CursorPage(null, emptyList())
            }
        }
    }

    private fun UniversalWalletIndexedTransaction.toTransferOperation(
        accountAddress: String,
        chainAsset: Asset
    ): Operation? {
        if (operationType != UniversalWalletIndexedOperationType.Transfer) return null

        val amount = amount?.toUnsignedBigIntegerOrNull() ?: return null
        val fee = feeAmount?.toUnsignedBigIntegerOrNull()?.takeIf { chainAsset.isNativeSolanaAsset() }
        val time = timestampMillis ?: return null
        val status = when (status) {
            UniversalWalletIndexedTransactionStatus.Pending -> Operation.Status.PENDING
            UniversalWalletIndexedTransactionStatus.Confirmed -> Operation.Status.COMPLETED
            UniversalWalletIndexedTransactionStatus.Failed -> Operation.Status.FAILED
        }
        val counterparty = counterpartyAddress.orEmpty()
        val (sender, receiver) = when (direction) {
            UniversalWalletIndexedTransactionDirection.Outgoing -> accountAddress to counterparty
            UniversalWalletIndexedTransactionDirection.Incoming -> counterparty to accountAddress
            UniversalWalletIndexedTransactionDirection.Self,
            UniversalWalletIndexedTransactionDirection.Unknown -> return null
        }

        return Operation(
            id = transactionId,
            address = accountAddress,
            time = time,
            chainAsset = chainAsset,
            type = Operation.Type.Transfer(
                hash = transactionId,
                myAddress = accountAddress,
                amount = amount,
                receiver = receiver,
                sender = sender,
                status = status,
                fee = fee
            )
        )
    }

    private fun Asset.solanaHistoryAsset(network: UniversalWalletRegistry.SolanaNetwork): SolanaHistoryAsset? {
        if (isNativeSolanaAsset()) {
            return SolanaHistoryAsset(network.nativeAsset.id, isNative = true)
        }

        val mint = listOfNotNull(id, currencyId)
            .firstOrNull { it.isNotBlank() && it != network.nativeAsset.id }
            ?: return null

        return SolanaHistoryAsset(mint, isNative = false)
    }

    private fun Asset.isNativeSolanaAsset(): Boolean {
        return isNative == true && symbol.equals(SOLANA_SYMBOL, ignoreCase = true)
    }

    private fun String.toUnsignedBigIntegerOrNull(): BigInteger? {
        return runCatching { BigInteger(this) }
            .getOrNull()
            ?.takeIf { it.signum() >= 0 }
    }

    private companion object {
        const val SOLANA_SYMBOL = "SOL"
    }

    private data class SolanaHistoryAsset(
        val assetId: String,
        val isNative: Boolean
    )
}
