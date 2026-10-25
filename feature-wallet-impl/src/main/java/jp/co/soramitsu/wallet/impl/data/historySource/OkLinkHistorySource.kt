package jp.co.soramitsu.wallet.impl.data.historySource

import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class OkLinkHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
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
        if (pageSize <= 0 || TransactionFilter.TRANSFER !in filters) return CursorPage(null, emptyList())
        val isNativeAsset = when (chainAsset.type) {
            ChainAssetType.Normal -> true
            ChainAssetType.ERC20, ChainAssetType.BEP20 -> false
            else -> throw HistoryNotSupportedException()
        }
        val response = walletOperationsApi.getOkLinkOperationsHistory(
            url = historyUrl,
            address = accountId.toHexString(true),
            symbol = chainAsset.symbol.lowercase()
        )
        check(response.code == 0 && response.data.size == 1) {
            // A provider message may contain account details. Keep it out of UI and logs.
            "OKLink history provider rejected or malformed the request"
        }
        val firstPage = response.data.single()
        val operations = firstPage.transactionLists.filter { element ->
            if (isNativeAsset) {
                element.tokenContractAddress.isBlank() &&
                    element.transactionSymbol.equals(chainAsset.symbol, ignoreCase = true)
            } else {
                element.tokenContractAddress.equals(chainAsset.id, ignoreCase = true)
            }
        }.map { element ->
            val status = when (element.state) {
                "success" -> Operation.Status.COMPLETED
                "fail" -> Operation.Status.FAILED
                "pending" -> Operation.Status.PENDING
                else -> throw IllegalArgumentException("Unknown OKLink transaction status")
            }
            val feeAsset = if (isNativeAsset) chainAsset else chain.utilityAsset
            val fee = feeAsset?.let { element.txFee.scaleByPowerOfTen(it.precision).toBigIntegerExact() }
            val amount = element.amount.scaleByPowerOfTen(chainAsset.precision).toBigIntegerExact()
            Operation(
                id = element.txId,
                address = accountAddress,
                time = element.transactionTime,
                chainAsset = chainAsset,
                type = Operation.Type.Transfer(
                    hash = element.txId,
                    myAddress = accountAddress,
                    amount = amount,
                    receiver = element.to.lowercase(),
                    sender = element.from.lowercase(),
                    status = status,
                    fee = fee
                )
            )
        }
        return CursorPage(null, operations)
    }
}
