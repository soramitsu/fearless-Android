package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import java.time.Instant
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
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
        val page = cursor?.toIntOrNull()?.takeIf { it >= 0 } ?: 0
        val limit = pageSize.coerceAtMost(IrohaToriiRoutes.MAX_LIMIT)

        return runCatching {
            val response = toriiClient.mcpJsonRpc(
                request = IrohaMcpJsonRpcRequest(
                    id = "history-$page",
                    method = "tools/call",
                    params = mapOf(
                        "name" to "iroha.instructions.list",
                        "arguments" to mapOf(
                            "account" to normalizedAddress,
                            "asset_id" to chainAsset.id,
                            "kind" to "Transfer",
                            "page" to page,
                            "per_page" to limit,
                            "transaction_status" to "committed",
                            "accept" to "application/json"
                        )
                    )
                ),
                network = network,
                baseUrl = historyUrl.takeIf(String::isNotBlank)
            )

            if (response.error != null) return@runCatching CursorPage(null, emptyList())

            val items = response.result.extractInstructionItems()
            val operations = items.flatMapIndexed { index, item ->
                item.toTransferOperations(
                    index = index,
                    accountAddress = normalizedAddress,
                    chainAsset = chainAsset
                )
            }

            CursorPage(
                nextCursor = if (items.size >= limit) (page + 1).toString() else null,
                items = operations
            )
        }.getOrDefault(CursorPage(null, emptyList()))
    }

    private fun Any?.extractInstructionItems(): List<Map<*, *>> {
        val result = this as? Map<*, *> ?: return emptyList()
        val body = result["body"] as? Map<*, *> ?: result
        val items = body["items"] as? List<*> ?: return emptyList()

        return items.mapNotNull { it as? Map<*, *> }
    }

    private fun Map<*, *>.toTransferOperations(
        index: Int,
        accountAddress: String,
        chainAsset: Asset
    ): List<Operation> {
        val hash = stringValue("transaction_hash", "transactionHash", "hash") ?: return emptyList()
        val timestamp = timestampMillis(stringValue("created_at", "createdAt", "timestamp")) ?: return emptyList()
        val payload = (((this["box"] as? Map<*, *>)?.get("json") as? Map<*, *>)?.get("payload") as? Map<*, *>)
            ?: return emptyList()
        val success = !stringValue("transaction_status", "transactionStatus", "status").equals("Rejected", ignoreCase = true)
        val transfers = payload.transferPayloads(accountAddress, chainAsset.id)

        return transfers.mapIndexed { transferIndex, transfer ->
            val id = if (transfers.size == 1) hash else "$hash:${index + transferIndex}"
            Operation(
                id = id,
                address = accountAddress,
                time = timestamp,
                chainAsset = chainAsset,
                type = Operation.Type.Transfer(
                    hash = hash,
                    myAddress = accountAddress,
                    amount = transfer.amount,
                    receiver = transfer.to,
                    sender = transfer.from,
                    status = Operation.Status.fromSuccess(success),
                    fee = BigInteger.ZERO
                )
            )
        }
    }

    private fun Map<*, *>.transferPayloads(accountAddress: String, assetId: String): List<IrohaTransferPayload> {
        val variant = stringValue("variant")
        val value = this["value"]

        return when (variant) {
            "Asset" -> listOfNotNull((value as? Map<*, *>)?.assetTransfer(accountAddress, assetId))
            "AssetBatch" -> (value as? Map<*, *>)
                ?.firstList("entries", "transfers", "items")
                ?.mapNotNull { (it as? Map<*, *>)?.assetTransfer(accountAddress, assetId) }
                .orEmpty()
            else -> emptyList()
        }
    }

    private fun Map<*, *>.assetTransfer(accountAddress: String, assetId: String): IrohaTransferPayload? {
        val source = stringValue("source", "source_id", "asset", "asset_id")
        val destination = stringValue("destination", "destination_id", "to", "account_id") ?: return null
        val amount = normalizeIrohaAmount(this["object"] ?: this["amount"] ?: this["quantity"] ?: this["value"]) ?: return null

        if (source != null && !source.irohaAssetMatches(assetId)) return null

        val sourceAccount = stringValue("source_account", "from", "account") ?: source.extractIrohaAssetAccount()
        val incoming = destination.equals(accountAddress, ignoreCase = true)
        val outgoing = sourceAccount?.equals(accountAddress, ignoreCase = true) == true || source?.contains(accountAddress) == true

        if (!incoming && !outgoing) return null

        return IrohaTransferPayload(
            amount = amount,
            from = if (outgoing) accountAddress else sourceAccount.orEmpty(),
            to = if (incoming) accountAddress else destination
        )
    }

    private fun normalizeIrohaAmount(value: Any?): BigInteger? {
        return when (value) {
            is String -> value.trim().takeIf { UNSIGNED_INTEGER.matches(it) }?.let(::BigInteger)
            is Number -> value.toLong().takeIf { value.toDouble() == it.toDouble() && it > 0 }?.let(BigInteger::valueOf)
            is Map<*, *> -> {
                val scale = value["scale"]
                if (scale != null && scale.toString() != "0") return null
                normalizeIrohaAmount(value["value"] ?: value["amount"] ?: value["mantissa"])
            }
            else -> null
        }?.takeIf { it.signum() > 0 }
    }

    private fun timestampMillis(value: String?): Long? {
        val timestamp = value?.trim()?.takeIf(String::isNotEmpty) ?: return null
        if (timestamp.all(Char::isDigit)) {
            val raw = timestamp.toLongOrNull() ?: return null
            return if (raw < MILLIS_THRESHOLD) raw * 1000 else raw
        }

        return runCatching { Instant.parse(timestamp).toEpochMilli() }.getOrNull()
    }

    private fun Map<*, *>.stringValue(vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key ->
            (this[key] as? String)?.trim()?.takeIf(String::isNotEmpty)
        }
    }

    private fun Map<*, *>.firstList(vararg keys: String): List<*>? {
        return keys.firstNotNullOfOrNull { key -> this[key] as? List<*> }
    }

    private fun String?.extractIrohaAssetAccount(): String? {
        val source = this ?: return null
        val separatorIndex = source.lastIndexOf('#')

        return source.takeIf { separatorIndex >= 0 && separatorIndex < source.lastIndex }
            ?.substring(separatorIndex + 1)
    }

    private fun String.irohaAssetMatches(assetId: String): Boolean {
        return this == assetId || startsWith("$assetId#")
    }

    private data class IrohaTransferPayload(
        val amount: BigInteger,
        val from: String,
        val to: String
    )

    private companion object {
        const val MILLIS_THRESHOLD = 10_000_000_000L
        val UNSIGNED_INTEGER = Regex("^[1-9][0-9]*$")
    }
}
