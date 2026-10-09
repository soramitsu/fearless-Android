package jp.co.soramitsu.wallet.impl.data.historySource

import java.math.BigInteger
import java.time.Instant
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.data.network.iroha.IrohaAssetDefinitionListItem
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcRequest
import jp.co.soramitsu.common.data.network.iroha.IrohaMcpJsonRpcResponse
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiClient
import jp.co.soramitsu.common.data.network.iroha.IrohaToriiRoutes
import jp.co.soramitsu.common.model.UniversalWalletRegistry
import jp.co.soramitsu.common.utils.IrohaAddressCodec
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

        val network = chain.universalWalletIrohaNetwork()
            ?: throw IrohaHistoryReadException("Wallet selected Iroha history for an unknown chain identity")
        val normalizedAddress = chain.normalizedIrohaAddress(accountAddress) ?: return CursorPage(null, emptyList())
        val cursorState = parseHistoryCursor(cursor)
        val page = cursorState.page
        val limit = pageSize.coerceAtMost(IROHA_HISTORY_MAX_PER_PAGE)

        val canonicalAssetId = try {
            IrohaToriiRoutes.normalizeAssetDefinitionId(chainAsset.id)
        } catch (_: IllegalArgumentException) {
            throw IrohaHistoryReadException(
                "Wallet asset ${chainAsset.id} is not a canonical Iroha asset definition id"
            )
        }
        val definitions = toriiClient.assetDefinitions(
            baseUrl = historyUrl.takeIf(String::isNotBlank),
            limit = IrohaToriiRoutes.MAX_LIMIT,
            offset = 0,
            countMode = IrohaToriiRoutes.CountMode.Bounded,
            network = network
        )
        if (
            definitions.hasMore != false ||
            definitions.countMode != IrohaToriiRoutes.CountMode.Bounded.apiValue
        ) {
            throw IrohaHistoryReadException(
                "Torii did not prove a complete asset-definition snapshot"
            )
        }
        val definitionsById = linkedMapOf<String, IrohaAssetDefinitionListItem>()
        definitions.items.forEach { candidate ->
            val id = try {
                IrohaToriiRoutes.normalizeAssetDefinitionId(candidate.id)
            } catch (_: IllegalArgumentException) {
                throw IrohaHistoryReadException(
                    "Torii returned a non-canonical asset definition id: ${candidate.id}"
                )
            }
            if (definitionsById.put(id, candidate) != null) {
                throw IrohaHistoryReadException("Torii returned duplicate asset definition $id")
            }
        }
        val definition = definitionsById[canonicalAssetId]
            ?: throw IrohaHistoryReadException(
                "Torii did not return a canonical definition for $canonicalAssetId"
            )
        val profilePrecision = network.validatedWalletPrecision(chainAsset, canonicalAssetId)
        val chainScale = definition.spec?.scale
            ?: throw IrohaHistoryReadException(
                "Torii omitted the fixed scale for $canonicalAssetId"
            )
        if (chainScale != profilePrecision) {
            throw IrohaHistoryReadException(
                "Wallet precision $profilePrecision does not match Torii scale $chainScale"
            )
        }
        val precision = chainScale

        val response = toriiClient.mcpJsonRpc(
                request = IrohaMcpJsonRpcRequest(
                    id = "history-$page",
                    method = "tools/call",
                    params = mapOf(
                        "name" to "iroha.instructions.list",
                        "arguments" to mapOf(
                            "account" to normalizedAddress,
                            "asset_id" to canonicalAssetId,
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

        val instructionPage = response.extractInstructionPage(
            "history-$page",
            page,
            limit,
            cursorState.totalPages,
            cursorState.totalItems
        )
        val operations = instructionPage.items.flatMap { item ->
            item.toTransferOperations(
                accountAddress = normalizedAddress,
                chainAsset = chainAsset,
                precision = precision,
                chainDiscriminant = network.chainDiscriminant
            )
        }
        if (operations.map(Operation::id).toSet().size != operations.size) {
            throw IrohaHistoryReadException("Torii MCP history returned duplicate operation identities")
        }

        return CursorPage(
            nextCursor = if (page.toLong() < instructionPage.totalPages) {
                "${page + 1}:${instructionPage.totalPages}:${instructionPage.totalItems}"
            } else {
                null
            },
            items = operations
        )
    }

    private fun IrohaMcpJsonRpcResponse.extractInstructionPage(
        expectedId: String,
        requestedPage: Int,
        requestedPerPage: Int,
        expectedTotalPages: Long?,
        expectedTotalItems: Long?
    ): IrohaInstructionPage {
        if (jsonrpc != "2.0" || id != expectedId) {
            throw IrohaHistoryReadException("Torii MCP history response has an invalid JSON-RPC envelope")
        }
        error?.let { failure ->
            throw IrohaHistoryReadException("Torii MCP history query failed: ${failure.message}")
        }
        val toolResult = result as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history response omitted the tool result")
        if (toolResult["isError"] != false) {
            throw IrohaHistoryReadException("Torii MCP history tool returned an error")
        }
        val route = toolResult["structuredContent"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history tool omitted structuredContent")
        val status = route["status"].exactLong()
            ?: throw IrohaHistoryReadException("Torii MCP history route omitted an exact HTTP status")
        if (status !in 200L..299L) {
            throw IrohaHistoryReadException("Torii MCP history route returned HTTP $status")
        }
        val rawHeaders = route["headers"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history route omitted headers")
        val headers = linkedMapOf<String, String>()
        rawHeaders.forEach { (rawName, rawValue) ->
            val name = (rawName as? String)?.lowercase()
                ?: throw IrohaHistoryReadException("Torii MCP history returned a non-string header name")
            val value = rawValue as? String
                ?: throw IrohaHistoryReadException("Torii MCP history returned a non-string header value")
            if (headers.put(name, value) != null) {
                throw IrohaHistoryReadException("Torii MCP history returned duplicate route headers")
            }
        }
        validateCompleteFanout(headers)
        val contentType = (route["content_type"] as? String)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        if (contentType != "application/json") {
            throw IrohaHistoryReadException("Torii MCP history route is not JSON")
        }
        val body = route["body"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history route omitted its object body")
        if (!body.hasExactKeys(HISTORY_PAGE_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical page body")
        }
        val items = body["items"] as? List<*>
            ?: throw IrohaHistoryReadException("Torii MCP history route omitted its item array")
        val pagination = body["pagination"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history route omitted pagination metadata")
        if (!pagination.hasExactKeys(PAGINATION_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned non-canonical pagination metadata")
        }
        val page = pagination["page"].exactLong()
        val perPage = pagination["per_page"].exactLong()
        val totalPages = pagination["total_pages"].exactLong()
            ?: throw IrohaHistoryReadException("Torii MCP history omitted total pages")
        val totalItems = pagination["total_items"].exactLong()
            ?: throw IrohaHistoryReadException("Torii MCP history omitted total items")
        if (totalItems < 0) {
            throw IrohaHistoryReadException("Torii MCP history returned negative total items")
        }
        val calculatedTotalPages = if (totalItems == 0L) {
            0L
        } else {
            ((totalItems - 1L) / requestedPerPage) + 1L
        }
        if (
            page != requestedPage.toLong() ||
            perPage != requestedPerPage.toLong() ||
            totalPages < 0 ||
            totalPages > Int.MAX_VALUE.toLong() ||
            totalPages != calculatedTotalPages
        ) {
            throw IrohaHistoryReadException("Torii MCP history returned incoherent pagination metadata")
        }
        if (
            (expectedTotalPages != null && totalPages != expectedTotalPages) ||
            (expectedTotalItems != null && totalItems != expectedTotalItems)
        ) {
            throw IrohaHistoryReadException("Torii MCP history pagination snapshot changed")
        }
        val start = runCatching {
            Math.multiplyExact(requestedPage.toLong() - 1L, requestedPerPage.toLong())
        }.getOrElse {
            throw IrohaHistoryReadException("Torii MCP history pagination overflowed")
        }
        val expectedItemCount = if (start >= totalItems) {
            0
        } else {
            minOf(requestedPerPage.toLong(), totalItems - start).toInt()
        }
        if (items.size != expectedItemCount) {
            throw IrohaHistoryReadException("Torii MCP history returned a truncated page")
        }

        val mappedItems = items.map { item ->
            item as? Map<*, *>
                ?: throw IrohaHistoryReadException("Torii MCP history returned a non-object item")
        }
        return IrohaInstructionPage(mappedItems, totalPages, totalItems)
    }

    private fun parseHistoryCursor(cursor: String?): IrohaHistoryCursor {
        if (cursor == null) return IrohaHistoryCursor(page = 1, totalPages = null, totalItems = null)
        val match = HISTORY_CURSOR.matchEntire(cursor)
            ?: throw IrohaHistoryReadException("Wallet supplied an invalid Iroha history cursor")
        val page = match.groupValues[1].toIntOrNull()
        val totalPages = match.groupValues[2].toLongOrNull()
        val totalItems = match.groupValues[3].toLongOrNull()
        if (
            page == null || totalPages == null || totalPages > Int.MAX_VALUE.toLong() ||
            totalItems == null || page.toLong() > totalPages
        ) {
            throw IrohaHistoryReadException("Wallet supplied an invalid Iroha history cursor")
        }
        return IrohaHistoryCursor(page, totalPages, totalItems)
    }

    private fun validateCompleteFanout(headers: Map<String, String>) {
        val rawValues = FANOUT_HEADER_NAMES.map(headers::get)
        val values = rawValues.map { rawValue ->
            rawValue?.takeIf(FANOUT_COUNT::matches)?.toIntOrNull()
                ?: throw IrohaHistoryReadException("Torii MCP history returned malformed fanout headers")
        }
        val attempted = values[0]
        val succeeded = values[1]
        val failed = values[2]
        val denied = values[3]
        val unavailable = values[4]
        val notFound = values[5]
        val complete = attempted > 0 && succeeded == attempted && failed == 0 &&
            denied == 0 && unavailable == 0 && notFound == 0
        if (!complete) {
            throw IrohaHistoryReadException("Torii MCP history returned incomplete fanout evidence")
        }
    }

    private fun Any?.exactLong(): Long? {
        val number = this as? Number ?: return null
        return when (number) {
            is Byte, is Short, is Int, is Long -> number.toLong()
            is BigInteger -> runCatching { number.longValueExact() }.getOrNull()
            is Float, is Double -> {
                val doubleValue = number.toDouble()
                val longValue = number.toLong()
                longValue.takeIf {
                    doubleValue.isFinite() &&
                        kotlin.math.abs(doubleValue) <= MAX_SAFE_JSON_INTEGER &&
                        doubleValue == longValue.toDouble()
                }
            }
            else -> null
        }
    }

    private fun Map<*, *>.toTransferOperations(
        accountAddress: String,
        chainAsset: Asset,
        precision: Int,
        chainDiscriminant: Int
    ): List<Operation> {
        if (!hasExactKeys(HISTORY_ITEM_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical item shape")
        }
        if (canonicalString("authority")?.let { IrohaAddressCodec.isValid(it, chainDiscriminant) } != true) {
            throw IrohaHistoryReadException("Torii MCP history returned an invalid authority")
        }
        val hash = canonicalHistoryString("transaction_hash", "transactionHash", "hash")
            ?.takeIf(CANONICAL_TRANSACTION_HASH::matches)
            ?: throw IrohaHistoryReadException("Torii MCP history returned an invalid transaction hash")
        val timestamp = timestampMillis(
            canonicalHistoryString("created_at", "createdAt", "timestamp")
        ) ?: throw IrohaHistoryReadException("Torii MCP history returned an invalid creation time")
        if (this["block"].exactLong()?.let { it > 0 } != true) {
            throw IrohaHistoryReadException("Torii MCP history returned an invalid block height")
        }
        val instructionIndex = this["index"].exactLong()
            ?.takeIf { it in 0..UInt.MAX_VALUE.toLong() }
            ?: throw IrohaHistoryReadException("Torii MCP history returned an invalid instruction index")
        if (canonicalString("kind") != "Transfer") {
            throw IrohaHistoryReadException("Torii MCP history returned a non-transfer instruction")
        }
        val transactionStatus = canonicalHistoryString(
            "transaction_status",
            "transactionStatus",
            "status"
        )
        if (transactionStatus != "Committed") {
            throw IrohaHistoryReadException("Torii MCP history returned a non-committed transaction")
        }
        val box = this["box"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history omitted its instruction box")
        if (!box.hasExactKeys(INSTRUCTION_BOX_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical instruction box")
        }
        val json = box["json"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history omitted structured instruction JSON")
        if (!json.hasExactKeys(INSTRUCTION_JSON_KEYS) || json.canonicalString("kind") != "Transfer") {
            throw IrohaHistoryReadException("Torii MCP history returned non-canonical instruction JSON")
        }
        val encoded = json.canonicalString("encoded")
            ?.takeIf(LOWER_HEX_BYTES::matches)
            ?: throw IrohaHistoryReadException("Torii MCP history returned invalid instruction bytes")
        if (box["encoded"] != "0x$encoded") {
            throw IrohaHistoryReadException("Torii MCP history returned contradictory instruction bytes")
        }
        val framedSha = box["framed_sha256"] as? String
        if (framedSha == null || !FRAMED_SHA256.matches(framedSha)) {
            throw IrohaHistoryReadException("Torii MCP history returned an invalid framed digest")
        }
        val payload = json["payload"] as? Map<*, *>
            ?: throw IrohaHistoryReadException("Torii MCP history omitted its transfer payload")
        if (!payload.hasExactKeys(TRANSFER_PAYLOAD_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical transfer payload")
        }
        val variant = payload.canonicalString("variant")
        val expectedWireId = when (variant) {
            "Asset" -> "iroha.transfer"
            "AssetBatch" -> "iroha.transfer_batch"
            else -> throw IrohaHistoryReadException("Torii MCP history returned a non-asset transfer")
        }
        if (json.canonicalString("wire_id") != expectedWireId) {
            throw IrohaHistoryReadException("Torii MCP history returned a contradictory transfer wire id")
        }
        val transfers = payload.transferPayloads(
            accountAddress,
            chainAsset.id,
            precision,
            chainDiscriminant
        )

        return transfers.map { transfer ->
            val id = transfer.legId?.let { "$hash:$instructionIndex:$it" } ?: "$hash:$instructionIndex"
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
                    status = Operation.Status.COMPLETED,
                    fee = null
                )
            )
        }
    }

    private fun Map<*, *>.transferPayloads(
        accountAddress: String,
        assetId: String,
        precision: Int,
        chainDiscriminant: Int
    ): List<IrohaTransferPayload> {
        val variant = canonicalString("variant")
        val value = this["value"]

        return when (variant) {
            "Asset" -> {
                val result = (value as? Map<*, *>)
                    ?.parseAssetTransfer(accountAddress, assetId, precision, chainDiscriminant)
                    ?: IrohaTransferParseResult.invalid()
                if (!result.canonical) {
                    throw IrohaHistoryReadException("Torii MCP history returned an invalid asset transfer")
                }
                listOfNotNull(result.transfer)
            }
            "AssetBatch" -> (value as? Map<*, *>)
                ?.parseAssetBatch(accountAddress, assetId, precision, chainDiscriminant)
                ?: throw IrohaHistoryReadException("Torii MCP history returned an invalid asset batch")
            else -> throw IrohaHistoryReadException("Torii MCP history returned an unsupported transfer variant")
        }
    }

    private fun Map<*, *>.parseAssetTransfer(
        accountAddress: String,
        assetId: String,
        precision: Int,
        chainDiscriminant: Int
    ): IrohaTransferParseResult {
        if (!hasExactKeys(SINGLE_TRANSFER_KEYS)) return IrohaTransferParseResult.invalid()

        val source = canonicalString("source") ?: return IrohaTransferParseResult.invalid()
        val destination = canonicalString("destination") ?: return IrohaTransferParseResult.invalid()
        val sourceParts = source.split('#')
        val sourceAccount = sourceParts.takeIf { it.size == 2 }?.get(1)
            ?: return IrohaTransferParseResult.invalid()
        val sourceDefinition = sourceParts[0]
        val amount = this["object"]
            ?.let { normalizeIrohaAmount(it, precision) }
            ?: return IrohaTransferParseResult.invalid()

        if (
            runCatching {
                IrohaToriiRoutes.normalizeAssetDefinitionId(sourceDefinition)
            }.getOrNull() != sourceDefinition ||
            !IrohaAddressCodec.isValid(sourceAccount, chainDiscriminant) ||
            !IrohaAddressCodec.isValid(destination, chainDiscriminant)
        ) {
            return IrohaTransferParseResult.invalid()
        }
        if (sourceDefinition != assetId) return IrohaTransferParseResult.excluded()

        val incoming = destination == accountAddress
        val outgoing = sourceAccount == accountAddress

        if (!incoming && !outgoing) return IrohaTransferParseResult.excluded()

        return IrohaTransferParseResult.included(
            IrohaTransferPayload(
                amount = amount,
                from = if (outgoing) accountAddress else sourceAccount,
                legId = null,
                to = if (incoming) accountAddress else destination
            )
        )
    }

    private fun Map<*, *>.parseAssetBatch(
        accountAddress: String,
        assetId: String,
        precision: Int,
        chainDiscriminant: Int
    ): List<IrohaTransferPayload> {
        if (!hasExactKeys(BATCH_CONTAINER_KEYS)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical asset batch")
        }
        val mode = this["mode"] as? Map<*, *>
        if (
            mode == null ||
            !mode.hasExactKeys(BATCH_MODE_KEYS) ||
            mode.canonicalString("mode") != "Atomic" ||
            mode["value"] != null
        ) {
            throw IrohaHistoryReadException("Torii MCP history returned an invalid asset batch mode")
        }
        val entries = this["entries"] as? List<*>
            ?: throw IrohaHistoryReadException("Torii MCP history omitted asset batch entries")
        if (entries.size > MAX_BATCH_ENTRIES) {
            throw IrohaHistoryReadException("Torii MCP history returned an oversized asset batch")
        }
        val results = entries.map { entry ->
            (entry as? Map<*, *>)?.parseAssetBatchEntry(
                accountAddress,
                assetId,
                precision,
                chainDiscriminant
            )
                ?: IrohaTransferParseResult.invalid()
        }
        if (!results.all(IrohaTransferParseResult::canonical)) {
            throw IrohaHistoryReadException("Torii MCP history returned an invalid asset batch entry")
        }
        val legIds = results.mapNotNull(IrohaTransferParseResult::legId)
        if (legIds.size != entries.size || legIds.toSet().size != legIds.size) {
            throw IrohaHistoryReadException("Torii MCP history returned duplicate asset batch leg ids")
        }

        return results.mapNotNull(IrohaTransferParseResult::transfer)
    }

    private fun Map<*, *>.parseAssetBatchEntry(
        accountAddress: String,
        assetId: String,
        precision: Int,
        chainDiscriminant: Int
    ): IrohaTransferParseResult {
        if (!hasExactKeys(BATCH_ENTRY_KEYS)) return IrohaTransferParseResult.invalid()

        val legId = canonicalString("leg_id")
            ?.takeIf { it.toByteArray(Charsets.UTF_8).size <= MAX_BATCH_LEG_ID_LENGTH }
            ?: return IrohaTransferParseResult.invalid()
        val from = canonicalString("from") ?: return IrohaTransferParseResult.invalid()
        val to = canonicalString("to") ?: return IrohaTransferParseResult.invalid()
        val definition = canonicalString("asset_definition") ?: return IrohaTransferParseResult.invalid()
        val amount = this["amount"]
            ?.let { normalizeIrohaAmount(it, precision) }
            ?: return IrohaTransferParseResult.invalid()

        if (
            runCatching {
                IrohaToriiRoutes.normalizeAssetDefinitionId(definition)
            }.getOrNull() != definition ||
            !IrohaAddressCodec.isValid(from, chainDiscriminant) ||
            !IrohaAddressCodec.isValid(to, chainDiscriminant)
        ) {
            return IrohaTransferParseResult.invalid()
        }

        if (definition != assetId) return IrohaTransferParseResult.excluded(legId)

        val incoming = to == accountAddress
        val outgoing = from == accountAddress

        if (!incoming && !outgoing) return IrohaTransferParseResult.excluded(legId)

        return IrohaTransferParseResult.included(
            IrohaTransferPayload(
                amount = amount,
                from = if (outgoing) accountAddress else from,
                legId = legId,
                to = if (incoming) accountAddress else to
            )
        )
    }

    private fun normalizeIrohaAmount(value: Any?, precision: Int): BigInteger? {
        if (precision !in 0..MAX_SUPPORTED_PRECISION) return null
        val quantity = value as? String ?: return null
        if (quantity.length > MAX_QUANTITY_WIRE_LENGTH || !CANONICAL_QUANTITY.matches(quantity)) return null
        val parts = quantity.split('.', limit = 2)
        val fraction = parts.getOrElse(1) { "" }
        val digits = parts[0] + fraction
        if (fraction.length > MAX_SUPPORTED_PRECISION || digits.length > MAX_QUANTITY_DIGITS) return null
        val mantissa = runCatching { BigInteger(digits) }.getOrNull()
            ?.takeIf { it.signum() > 0 && it <= MAX_QUANTITY }
            ?: return null
        val scaleDelta = precision - fraction.length

        return if (scaleDelta >= 0) {
            mantissa.multiply(BigInteger.TEN.pow(scaleDelta))
        } else {
            val divisor = BigInteger.TEN.pow(-scaleDelta)
            mantissa.divideAndRemainder(divisor)
                .takeIf { it[1] == BigInteger.ZERO }
                ?.get(0)
        }
    }

    private fun UniversalWalletRegistry.IrohaNetwork.validatedWalletPrecision(
        asset: Asset,
        canonicalAssetId: String
    ): Int {
        val profileAsset = nativeAsset?.takeIf { it.id == canonicalAssetId } ?: return asset.precision
        if (asset.symbol != profileAsset.symbol || asset.precision != profileAsset.decimals) {
            throw IrohaHistoryReadException(
                "Wallet native profile ${asset.symbol}/${asset.precision} does not match " +
                    "${profileAsset.symbol}/${profileAsset.decimals} for $canonicalAssetId"
            )
        }

        return profileAsset.decimals
    }

    private fun timestampMillis(value: String?): Long? {
        val timestamp = value?.takeIf(CANONICAL_RFC3339::matches) ?: return null
        return runCatching { Instant.parse(timestamp).toEpochMilli() }
            .getOrNull()
            ?.takeIf { it >= 0 }
    }

    private fun Map<*, *>.canonicalHistoryString(
        canonicalKey: String,
        vararg aliases: String
    ): String? {
        if (aliases.any(::containsKey)) {
            throw IrohaHistoryReadException("Torii MCP history returned a non-canonical field alias")
        }

        return (this[canonicalKey] as? String)
            ?.takeIf { it.isNotEmpty() && it == it.trim() }
    }

    private fun Map<*, *>.canonicalString(key: String): String? {
        return (this[key] as? String)
            ?.takeIf { it.isNotEmpty() && it == it.trim() }
    }

    private fun Map<*, *>.hasExactKeys(expected: Set<String>): Boolean {
        return keys.all { it is String } && keys == expected
    }

    private data class IrohaTransferPayload(
        val amount: BigInteger,
        val from: String,
        val legId: String?,
        val to: String
    )

    private data class IrohaTransferParseResult(
        val canonical: Boolean,
        val legId: String?,
        val transfer: IrohaTransferPayload?
    ) {
        companion object {
            fun invalid() = IrohaTransferParseResult(canonical = false, legId = null, transfer = null)
            fun excluded(legId: String? = null) = IrohaTransferParseResult(
                canonical = true,
                legId = legId,
                transfer = null
            )
            fun included(transfer: IrohaTransferPayload) = IrohaTransferParseResult(
                canonical = true,
                legId = transfer.legId,
                transfer = transfer
            )
        }
    }

    private data class IrohaInstructionPage(
        val items: List<Map<*, *>>,
        val totalPages: Long,
        val totalItems: Long
    )

    private data class IrohaHistoryCursor(
        val page: Int,
        val totalPages: Long?,
        val totalItems: Long?
    )

    private companion object {
        const val MAX_SUPPORTED_PRECISION = 28
        const val MAX_QUANTITY_DIGITS = 154
        const val MAX_QUANTITY_WIRE_LENGTH = MAX_QUANTITY_DIGITS + 1
        const val MAX_BATCH_ENTRIES = 1_000
        const val MAX_BATCH_LEG_ID_LENGTH = 256
        const val IROHA_HISTORY_MAX_PER_PAGE = 100
        const val MAX_SAFE_JSON_INTEGER = 9_007_199_254_740_991.0
        val MAX_QUANTITY = BigInteger.ONE.shiftLeft(511).subtract(BigInteger.ONE)
        val FANOUT_COUNT = Regex("^(0|[1-9][0-9]*)$")
        val FANOUT_HEADER_NAMES = listOf(
            "x-iroha-fanout-routes-attempted",
            "x-iroha-fanout-routes-succeeded",
            "x-iroha-fanout-routes-failed",
            "x-iroha-fanout-routes-denied",
            "x-iroha-fanout-routes-unavailable",
            "x-iroha-fanout-routes-not-found"
        )
        val CANONICAL_TRANSACTION_HASH = Regex("^[0-9a-f]{63}[13579bdf]$")
        val CANONICAL_RFC3339 = Regex(
            "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]{0,8}[1-9])?Z$"
        )
        val CANONICAL_QUANTITY = Regex("^(0|[1-9][0-9]*)(\\.[0-9]*[1-9])?$")
        val LOWER_HEX_BYTES = Regex("^([0-9a-f]{2})+$")
        val FRAMED_SHA256 = Regex("^0x[0-9a-f]{64}$")
        val HISTORY_ITEM_KEYS = setOf(
            "authority",
            "created_at",
            "kind",
            "box",
            "transaction_hash",
            "transaction_status",
            "block",
            "index"
        )
        val INSTRUCTION_BOX_KEYS = setOf("encoded", "framed_sha256", "json")
        val INSTRUCTION_JSON_KEYS = setOf("kind", "payload", "wire_id", "encoded")
        val TRANSFER_PAYLOAD_KEYS = setOf("variant", "value")
        val SINGLE_TRANSFER_KEYS = setOf("source", "object", "destination")
        val BATCH_CONTAINER_KEYS = setOf("mode", "entries")
        val BATCH_MODE_KEYS = setOf("mode", "value")
        val BATCH_ENTRY_KEYS = setOf("leg_id", "from", "to", "asset_definition", "amount")
        val HISTORY_PAGE_KEYS = setOf("pagination", "items")
        val PAGINATION_KEYS = setOf("page", "per_page", "total_pages", "total_items")
        val HISTORY_CURSOR = Regex("^([1-9][0-9]*):([1-9][0-9]*):([1-9][0-9]*)$")
    }
}

class IrohaHistoryReadException(message: String) : IllegalStateException(message)
