package jp.co.soramitsu.wallet.impl.data.historySource

import java.io.IOException
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.iroha.IrohaAccountHistoryItem
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.runtime.ext.normalizedIrohaAddress
import jp.co.soramitsu.runtime.ext.universalWalletIrohaNetwork
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation

class IrohaHistorySource(
    private val toriiClient: IrohaToriiClient,
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

        val network = chain.universalWalletIrohaNetwork() ?: return CursorPage(null, emptyList())
        val normalizedAddress = chain.normalizedIrohaAddress(accountAddress) ?: return CursorPage(null, emptyList())
        val assetDefinitionIds = listOfNotNull(chainAsset.id, chainAsset.currencyId).filter(String::isNotBlank).toSet()
        val limit = pageSize.coerceAtMost(IrohaToriiRoutes.MAX_LIMIT)

        val filter = IrohaToriiRoutes.transferHistoryFilter(assetDefinitionIds)
        var nextCursor = cursor
        var requests = 0
        val seenCursors = mutableSetOf<String?>()
        var operations: List<Operation>

        // Torii scans a fixed window of blocks per request, so a filtered page can be empty while
        // older history remains. Follow those continuations up to MAX_PAGE_REQUESTS, then return
        // what was found with the cursor, as the web wallet does: an account without recent
        // transfers shows no history rather than an error, and paging can resume from the cursor.
        // The repository keeps the rows it already has when a page comes back empty.
        do {
            seenCursors.add(nextCursor)
            val page = toriiClient.accountHistory(
                accountId = normalizedAddress,
                baseUrl = historyUrl.takeIf(String::isNotBlank),
                limit = limit,
                cursor = nextCursor,
                filter = filter,
                network = network
            )
            operations = page.items.mapNotNull { item ->
                item.toTransferOperation(chain, normalizedAddress, chainAsset, assetDefinitionIds)
            }
            nextCursor = page.nextCursor?.takeIf(String::isNotEmpty)
            requests += 1
            if (nextCursor != null && nextCursor in seenCursors) {
                throw IOException("Iroha history cursor did not advance")
            }
        } while (operations.isEmpty() && nextCursor != null && requests < MAX_PAGE_REQUESTS)

        // Let network errors and cancellation reach the caller's retry state. Returning an
        // empty page here would replace the repository's cached operations and cursor.
        return CursorPage(nextCursor, operations)
    }

    // Torii reports each movement once, already resolved to this account's side of the transfer.
    private fun IrohaAccountHistoryItem.toTransferOperation(
        chain: Chain,
        accountAddress: String,
        chainAsset: Asset,
        assetDefinitionIds: Set<String>
    ): Operation? {
        if (type != TRANSFER_TYPE || assetDefinitionId !in assetDefinitionIds) return null
        if (accountId?.let(chain::normalizedIrohaAddress) != accountAddress) return null

        val hash = txHash?.lowercase()?.takeIf(TX_HASH::matches) ?: return null
        val index = movementIndex?.takeIf { it >= 0 } ?: return null
        val time = timestampMs?.takeIf { it > 0 } ?: return null
        val planks = amount?.toPlanksOrNull(chainAsset.precision) ?: return null
        val counterparty = counterpartyAccountId?.let(chain::normalizedIrohaAddress)
        val (sender, receiver) = when (direction) {
            INCOMING -> (counterparty ?: return null) to accountAddress
            OUTGOING -> accountAddress to (counterparty ?: return null)
            SELF -> accountAddress to accountAddress
            else -> return null
        }

        return Operation(
            id = "$hash:$index",
            address = accountAddress,
            time = time,
            chainAsset = chainAsset,
            type = Operation.Type.Transfer(
                hash = hash,
                myAddress = accountAddress,
                amount = planks,
                receiver = receiver,
                sender = sender,
                status = Operation.Status.fromSuccess(resultOk == true && status == SUCCESS_STATUS),
                fee = BigInteger.ZERO
            )
        )
    }

    // Torii amounts are decimal quantities in the asset's units.
    private fun String.toPlanksOrNull(precision: Int): BigInteger? {
        if (length > MAX_AMOUNT_LENGTH || !DECIMAL_AMOUNT.matches(this)) return null

        return runCatching { BigDecimal(this).movePointRight(precision).toBigIntegerExact() }
            .getOrNull()
            ?.takeIf { it.signum() > 0 }
    }

    private companion object {
        const val MAX_PAGE_REQUESTS = 10
        const val MAX_AMOUNT_LENGTH = 160
        const val TRANSFER_TYPE = "TRANSFER"
        const val SUCCESS_STATUS = "SUCCESS"
        const val INCOMING = "incoming"
        const val OUTGOING = "outgoing"
        const val SELF = "self"
        val DECIMAL_AMOUNT = Regex("^(0|[1-9][0-9]*)(\\.[0-9]+)?$")
        val TX_HASH = Regex("^[0-9a-f]{64}$")
    }
}
