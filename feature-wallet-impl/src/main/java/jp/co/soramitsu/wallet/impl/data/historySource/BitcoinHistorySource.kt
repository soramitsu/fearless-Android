package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistoryException
import jp.co.soramitsu.common.data.network.bitcoin.BitcoinTransactionHistorySync
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlin.time.DurationUnit
import kotlin.time.toDuration

class BitcoinHistorySource(
    private val historySync: BitcoinTransactionHistorySync,
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
        if (pageSize <= 0 || TransactionFilter.TRANSFER !in filters || !chainAsset.symbol.equals(BITCOIN_SYMBOL, ignoreCase = true)) {
            return CursorPage(null, emptyList())
        }

        return runCatching {
            val network = if (chain.isTestNet) {
                BitcoinIndexerRoutes.Network.Testnet
            } else {
                BitcoinIndexerRoutes.Network.Mainnet
            }
            val page = if (cursor.isNullOrBlank()) {
                historySync.historyWindow(
                    address = accountAddress,
                    network = network,
                    baseUrl = historyUrl
                )
            } else {
                historySync.history(
                    address = accountAddress,
                    network = network,
                    baseUrl = historyUrl,
                    lastSeenTxid = cursor
                )
            }
            val visibleEntries = page.entries.take(pageSize)
            val operations = visibleEntries.map { entry ->
                Operation(
                    id = entry.txid,
                    address = accountAddress,
                    time = entry.timestamp.toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
                    chainAsset = chainAsset,
                    type = Operation.Type.Transfer(
                        hash = entry.txid,
                        myAddress = accountAddress,
                        amount = BigInteger.valueOf(entry.amountSats),
                        receiver = entry.to,
                        sender = entry.from,
                        status = if (entry.confirmed) Operation.Status.COMPLETED else Operation.Status.PENDING,
                        fee = BigInteger.valueOf(entry.feeSats)
                    )
                )
            }

            CursorPage(
                nextCursor = when {
                    visibleEntries.isEmpty() -> null
                    visibleEntries.size < page.entries.size -> visibleEntries.last().txid
                    else -> page.nextLastSeenTxid
                },
                items = operations
            )
        }.getOrElse { error ->
            when (error) {
                is BitcoinTransactionHistoryException -> CursorPage(null, emptyList())
                else -> CursorPage(null, emptyList())
            }
        }
    }

    private companion object {
        const val BITCOIN_SYMBOL = "BTC"
    }
}
