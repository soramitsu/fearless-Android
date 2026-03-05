package jp.co.soramitsu.runtime.multiNetwork.chain.remote

import com.google.gson.JsonElement
import com.google.gson.Gson
import java.math.BigInteger
import java.net.URLEncoder
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.network.ton.AccountAddress
import jp.co.soramitsu.common.data.network.ton.AccountEvent
import jp.co.soramitsu.common.data.network.ton.AccountEventAction
import jp.co.soramitsu.common.data.network.ton.AccountEvents
import jp.co.soramitsu.common.data.network.ton.AccountStatus
import jp.co.soramitsu.common.data.network.ton.ActionSimplePreview
import jp.co.soramitsu.common.data.network.ton.EmulateMessageToWalletRequest
import jp.co.soramitsu.common.data.network.ton.JettonBalance
import jp.co.soramitsu.common.data.network.ton.JettonPreview
import jp.co.soramitsu.common.data.network.ton.JettonTransferAction
import jp.co.soramitsu.common.data.network.ton.JettonTransferPayloadRemote
import jp.co.soramitsu.common.data.network.ton.JettonsBalances
import jp.co.soramitsu.common.data.network.ton.MessageConsequences
import jp.co.soramitsu.common.data.network.ton.PublicKeyResponse
import jp.co.soramitsu.common.data.network.ton.RawTime
import jp.co.soramitsu.common.data.network.ton.Risk
import jp.co.soramitsu.common.data.network.ton.SendBlockchainMessageRequest
import jp.co.soramitsu.common.data.network.ton.Seqno
import jp.co.soramitsu.common.data.network.ton.TokenRate
import jp.co.soramitsu.common.data.network.ton.TonAccountData
import jp.co.soramitsu.common.data.network.ton.TonApi
import jp.co.soramitsu.common.data.network.ton.TonIndexerBalancesResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerJsonRpcRequest
import jp.co.soramitsu.common.data.network.ton.TonIndexerMessage
import jp.co.soramitsu.common.data.network.ton.TonIndexerRunGetMethodRequest
import jp.co.soramitsu.common.data.network.ton.TonIndexerRunGetMethodResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerStateResponse
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransaction
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransactionDetail
import jp.co.soramitsu.common.data.network.ton.TonIndexerTransactionsResponse
import jp.co.soramitsu.common.data.network.ton.TonTransferAction
import jp.co.soramitsu.common.data.network.ton.Trace
import jp.co.soramitsu.common.data.network.ton.Transaction
import jp.co.soramitsu.common.domain.GetAvailableFiatCurrencies
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.ton.model.JettonTransferPayload
import kotlinx.coroutines.CancellationException

class TonRemoteSource(
    private val tonApi: TonApi,
    private val availableFiatCurrencies: GetAvailableFiatCurrencies,
    private val gson: Gson,
    private val tonIndexerUrl: String = BuildConfig.TON_INDEXER_URL
) {
    companion object {
        private const val TON_API_HOST = "tonapi.io"
        private const val TON_API_BASE_URL = "https://tonapi.io"
    }

    suspend fun loadAccountData(chain: Chain, accountId: String): TonAccountData {
        return withIndexerFallback(
            block = { indexerBase ->
                val encodedAccountId = encodePathSegment(accountId)
                val balances = tonApi.getIndexerBalances("$indexerBase/api/indexer/v1/accounts/$encodedAccountId/balances")
                val state = tonApi.getIndexerState("$indexerBase/api/indexer/v1/accounts/$encodedAccountId/state")
                balances.toTonAccountData(state, accountId)
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                tonApi.getAccountData("$baseUrl/v2/accounts/$accountId")
            }
        )
    }

    suspend fun loadJettonBalances(chain: Chain, accountId: String): JettonsBalances {
        return withIndexerFallback(
            block = { indexerBase ->
                val encodedAccountId = encodePathSegment(accountId)
                val balances = tonApi.getIndexerBalances("$indexerBase/api/indexer/v1/accounts/$encodedAccountId/balances")
                balances.toJettonsBalances()
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                val currencies = availableFiatCurrencies.invoke().joinToString(",") { it.id.uppercase() }
                tonApi.getJettonBalances("$baseUrl/v2/accounts/$accountId/jettons", listOf(currencies))
            }
        )
    }

    suspend fun getSeqno(chain: Chain, accountId: String): Int {
        return withIndexerFallback(
            block = { indexerBase ->
                val encodedAccountId = encodePathSegment(accountId)
                val state = tonApi.getIndexerState("$indexerBase/api/indexer/v1/accounts/$encodedAccountId/state")
                state.lastConfirmedSeqno?.let { return@withIndexerFallback it }
                if (state.accountState == "uninitialized") return@withIndexerFallback 0

                val response = tonApi.runIndexerGetMethod(
                    "$indexerBase/api/indexer/v1/runGetMethod",
                    TonIndexerRunGetMethodRequest(address = accountId, method = "seqno")
                )
                parseFirstStackNumber(response).toInt()
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                val response = tonApi.getRequest("$baseUrl/v2/wallet/$accountId/seqno")
                val seqnoKeyResponse = gson.fromJson(response, Seqno::class.java)
                seqnoKeyResponse.seqno
            }
        )
    }

    suspend fun getRawTime(chain: Chain, accountId: String? = null): Int {
        return withIndexerFallback(
            block = { indexerBase ->
                val resolvedAccountId = accountId.takeIfNotBlank()
                    ?: error("Account id is required for indexer time lookup")
                val result = callIndexerJsonRpc(
                    indexerBase = indexerBase,
                    method = "getAddressInformation",
                    params = mapOf("address" to resolvedAccountId)
                )
                parseJsonLong(result.objectField("sync_utime"))
                    ?.coerceAtMost(Int.MAX_VALUE.toLong())
                    ?.toInt()
                    ?: error("Indexer getAddressInformation response does not contain sync_utime")
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                val response = tonApi.getRequest("$baseUrl/v2/liteserver/get_time")
                val timeKeyResponse = gson.fromJson(response, RawTime::class.java)
                timeKeyResponse.time
            }
        )
    }

    private fun getTonApiBaseUrl(chain: Chain): String {
        return chain.nodes
            .firstOrNull { it.url.isTonApiUrl() }
            ?.url
            ?.trim()
            ?.removeSuffix("/")
            ?: TON_API_BASE_URL
    }

    suspend fun getPublicKey(chain: Chain, accountId: String): String {
        return withIndexerFallback(
            block = { indexerBase ->
                val response = tonApi.runIndexerGetMethod(
                    "$indexerBase/api/indexer/v1/runGetMethod",
                    TonIndexerRunGetMethodRequest(address = accountId, method = "get_public_key")
                )
                parseFirstStackNumber(response).toString(16).padStart(64, '0')
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                val response = tonApi.getRequest("$baseUrl/v2/accounts/$accountId/publickey")

                val publicKeyResponse = gson.fromJson(response, PublicKeyResponse::class.java)

                publicKeyResponse.publicKey
            }
        )
    }

    suspend fun getJettonTransferPayload(chain: Chain, accountId: String, jettonId: String): JettonTransferPayload {
        return withIndexerFallback(
            block = { indexerBase ->
                val encodedJettonId = encodePathSegment(jettonId)
                val encodedAccountId = encodePathSegment(accountId)
                val jettonPayload = tonApi.getIndexerJettonTransferPayload(
                    "$indexerBase/api/indexer/v1/jettons/$encodedJettonId/transfer/$encodedAccountId/payload"
                )
                JettonTransferPayload(jettonId, jettonPayload)
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                val response = tonApi.getRequest("$baseUrl/v2/jettons/$jettonId/transfer/$accountId/payload")
                val jettonPayload = gson.fromJson(response, JettonTransferPayloadRemote::class.java)
                JettonTransferPayload(jettonId, jettonPayload)
            }
        )
    }

    suspend fun sendBlockchainMessage(chain: Chain, request: SendBlockchainMessageRequest): String {
        return withIndexerFallback(
            block = { indexerBase ->
                val bocs = request.batch.orEmpty().mapNotNull { it.takeIfNotBlank() }
                    .ifEmpty { listOfNotNull(request.boc.takeIfNotBlank()) }
                if (bocs.isEmpty()) error("sendBlockchainMessage request does not contain boc payload")

                bocs.forEach { boc ->
                    callIndexerJsonRpc(
                        indexerBase = indexerBase,
                        method = "sendBoc",
                        params = mapOf("boc" to boc)
                    )
                }
                "{\"ok\":true}"
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                tonApi.sendBlockchainMessage("$baseUrl/v2/blockchain/message", request)
            }
        )
    }

    suspend fun emulateBlockchainMessageRequest(chain: Chain, request: EmulateMessageToWalletRequest): MessageConsequences {
        return withIndexerFallback(
            block = { indexerBase ->
                val estimate = estimateMessageFeeViaIndexer(indexerBase, request)
                buildSyntheticMessageConsequences(
                    totalFees = estimate.totalFees,
                    accountId = estimate.address
                )
            },
            fallback = {
                val baseUrl = getTonApiBaseUrl(chain)
                tonApi.emulateBlockchainMessage("$baseUrl/v2/wallet/emulate", request)
            }
        )
    }

    suspend fun getAccountEvents(
        chain: Chain,
        historyUrl: String,
        accountId: String,
        beforeLt: Long?,
        beforeHash: String? = null,
        limit: Int = 100
    ): AccountEvents {
        return withIndexerFallback(
            block = { indexerBase ->
                val transactions = loadIndexerTransactions(
                    indexerBase = indexerBase,
                    accountId = accountId,
                    beforeLt = beforeLt,
                    beforeHash = beforeHash,
                    limit = limit
                )
                AccountEvents(transactions.map { it.toAccountEvent(accountId) })
            },
            fallback = {
                val fallbackBaseUrl = historyUrl.normalizedTonApiUrlOrNull() ?: getTonApiBaseUrl(chain)
                tonApi.getAccountEvents("$fallbackBaseUrl/v2/accounts/$accountId/events", limit = limit, beforeLt = beforeLt)
            }
        )
    }

    suspend fun getTonCoinPrices(): TokenRate {
        val currencies = availableFiatCurrencies.invoke().joinToString(",") { it.id.uppercase() }
        return tonApi.getTonCoinPrice(listOf(currencies)).rates["TON"]!!
    }

    private data class IndexerFeeEstimate(
        val address: String,
        val totalFees: BigInteger
    )

    private suspend fun estimateMessageFeeViaIndexer(
        indexerBase: String,
        request: EmulateMessageToWalletRequest
    ): IndexerFeeEstimate {
        val address = request.params?.firstOrNull()?.address.takeIfNotBlank()
            ?: error("estimateFee requires sender address in request params")
        val boc = request.boc.takeIfNotBlank()
            ?: error("estimateFee requires non-empty boc")
        val estimateResult = callIndexerJsonRpc(
            indexerBase = indexerBase,
            method = "estimateFee",
            params = mapOf(
                "address" to address,
                "body" to boc,
                "ignore_chksig" to true
            )
        )

        val totalFees = parseEstimateTotalFees(estimateResult)
            ?: error("Indexer estimateFee response does not contain source_fees")

        return IndexerFeeEstimate(
            address = address,
            totalFees = totalFees
        )
    }

    private suspend fun callIndexerJsonRpc(
        indexerBase: String,
        method: String,
        params: Map<String, Any?>
    ): JsonElement {
        val response = tonApi.callIndexerJsonRpc(
            "$indexerBase/jsonRPC",
            TonIndexerJsonRpcRequest(method = method, params = params)
        )

        val error = response.error
        if (error != null) {
            val message = error.message?.takeIf { it.isNotBlank() } ?: "Unknown jsonRPC error"
            error("Indexer jsonRPC method '$method' failed: $message")
        }

        return response.result ?: error("Indexer jsonRPC method '$method' returned empty result")
    }

    private fun parseEstimateTotalFees(result: JsonElement): BigInteger? {
        val sourceFees = result.objectField("source_fees") ?: return null
        val total = listOf("in_fwd_fee", "storage_fee", "gas_fee", "fwd_fee")
            .mapNotNull { key -> parseJsonBigInteger(sourceFees.objectField(key)) }
            .fold(BigInteger.ZERO, BigInteger::add)

        return total.takeIf { it > BigInteger.ZERO }
    }

    private fun buildSyntheticMessageConsequences(totalFees: BigInteger, accountId: String): MessageConsequences {
        val now = System.currentTimeMillis() / 1000L
        val accountAddress = accountId.toAccountAddress()
        val normalizedTotalFees = totalFees.coerceAtLeast(BigInteger.ZERO)
        val clampedTonRisk = normalizedTotalFees
            .coerceAtMost(BigInteger.valueOf(Long.MAX_VALUE))
            .toLong()

        return MessageConsequences(
            trace = Trace(
                transaction = Transaction(
                    hash = null,
                    lt = 0L,
                    account = accountAddress,
                    success = true,
                    utime = now,
                    origStatus = AccountStatus.active,
                    endStatus = AccountStatus.active,
                    totalFees = normalizedTotalFees,
                    endBalance = 0L,
                    transactionType = "EstimateFee",
                    stateUpdateOld = "",
                    stateUpdateNew = "",
                    outMsgs = emptyList(),
                    block = "",
                    aborted = false,
                    destroyed = false
                ),
                interfaces = emptyList(),
                children = emptyList(),
                emulated = true
            ),
            risk = Risk(
                transferAllRemainingBalance = false,
                ton = clampedTonRisk,
                jettons = emptyList()
            ),
            event = AccountEvent(
                eventId = "indexer_estimate_fee",
                account = accountAddress,
                timestamp = now,
                actions = emptyList(),
                isScam = false,
                lt = 0L,
                inProgress = false,
                extra = 0L
            )
        )
    }

    private suspend fun loadIndexerTransactions(
        indexerBase: String,
        accountId: String,
        beforeLt: Long?,
        beforeHash: String?,
        limit: Int
    ): List<TonIndexerTransaction> {
        val encodedAccountId = encodePathSegment(accountId)
        val requestLimit = limit.coerceAtLeast(1)
        val normalizedBeforeHash = beforeHash?.trim()?.takeIf { it.isNotEmpty() }

        if (beforeLt != null && normalizedBeforeHash != null) {
            val response = tonApi.getIndexerTransactions(
                "$indexerBase/api/indexer/v1/accounts/$encodedAccountId/txs",
                cursorLt = beforeLt.toString(),
                cursorHash = normalizedBeforeHash
            )
            return response.txs
                .filterNot { entry ->
                    entry.ltAsLongOrNull() == beforeLt && entry.hash == normalizedBeforeHash
                }
                .take(requestLimit)
        }

        val transactions = mutableListOf<TonIndexerTransaction>()
        val seenIds = mutableSetOf<String>()
        var page = 1
        var stagnantPageCount = 0
        var previousFirstKey: String? = null
        val pageSafetyLimit = if (beforeLt != null) 30 else maxOf(3, minOf(12, requestLimit / 10 + 3))

        while (transactions.size < requestLimit && page <= pageSafetyLimit) {
            val response = tonApi.getIndexerTransactions(
                "$indexerBase/api/indexer/v1/accounts/$encodedAccountId/txs",
                page = page
            )

            val entries = response.txs
            if (entries.isEmpty()) break

            val filteredEntries = entries.filter { entry ->
                beforeLt == null || entry.ltAsLongOrNull() < beforeLt
            }

            val beforeCount = transactions.size
            filteredEntries.forEach { entry ->
                val key = entry.uniqueKey()
                if (key in seenIds) return@forEach

                seenIds += key
                transactions += entry
                if (transactions.size >= requestLimit) return@forEach
            }

            val firstKey = entries.firstOrNull()?.uniqueKey()
            stagnantPageCount = if (transactions.size == beforeCount || firstKey == previousFirstKey) {
                stagnantPageCount + 1
            } else {
                0
            }
            previousFirstKey = firstKey

            if (stagnantPageCount >= 2) break
            if (response.shouldStopPaging(entries, page, transactions.size)) break

            page += 1
        }

        return transactions.take(requestLimit)
    }

    private fun TonIndexerBalancesResponse.toTonAccountData(
        state: TonIndexerStateResponse,
        fallbackAddress: String
    ): TonAccountData {
        return TonAccountData(
            address = state.address.ifBlank { address.ifBlank { fallbackAddress } },
            balance = parseLongAmount(tonRaw),
            lastActivity = state.lastSeenUtime ?: updatedAt,
            status = state.accountState.toAccountStatus(),
            getMethods = emptyList(),
            isWallet = true,
            currenciesBalance = null,
            interfaces = null,
            name = null,
            isScam = null,
            icon = null,
            memoRequired = null,
            isSuspended = null
        )
    }

    private fun TonIndexerBalancesResponse.toJettonsBalances(): JettonsBalances {
        val balances = assets.mapNotNull { asset ->
            val jettonAddress = asset.address?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val jettonSymbol = asset.symbol?.takeIf { it.isNotBlank() } ?: jettonAddress.take(6)
            val walletAddress = asset.wallet?.takeIf { it.isNotBlank() } ?: jettonAddress

            if (asset.kind != "jetton") return@mapNotNull null

            JettonBalance(
                balance = asset.balanceRaw,
                walletAddress = AccountAddress(
                    address = walletAddress,
                    isScam = false,
                    isWallet = true,
                    name = null,
                    icon = null
                ),
                jetton = JettonPreview(
                    address = jettonAddress,
                    name = jettonSymbol,
                    symbol = jettonSymbol,
                    decimals = asset.decimals.coerceAtLeast(0),
                    image = "",
                    verification = "none"
                ),
                price = null,
                extensions = null,
                lock = null
            )
        }

        return JettonsBalances(balances = balances)
    }

    private fun TonIndexerTransactionsResponse.shouldStopPaging(
        entries: List<TonIndexerTransaction>,
        page: Int,
        loadedCount: Int
    ): Boolean {
        val resolvedPageSize = if (this.pageSize > 0) this.pageSize else entries.size
        val resolvedTotalPages = totalPages
        if (resolvedPageSize <= 0 || entries.size < resolvedPageSize) return true
        if (resolvedTotalPages != null && page >= resolvedTotalPages) return true
        if (historyComplete && totalTxs > 0 && loadedCount >= totalTxs) return true

        return false
    }

    private fun TonIndexerTransaction.toAccountEvent(fallbackAccountId: String): AccountEvent {
        val transferAction = toTonTransferAction(fallbackAccountId)
        val jettonAction = toJettonTransferAction(fallbackAccountId)
        val resolvedTonTransfer = if (jettonAction != null) null else transferAction
        val actionType = when {
            jettonAction != null -> AccountEventAction.Type.jettonTransfer
            resolvedTonTransfer != null -> AccountEventAction.Type.tonTransfer
            else -> AccountEventAction.Type.unknown
        }
        val actionStatus = if (status.equals("failed", ignoreCase = true)) {
            AccountEventAction.Status.failed
        } else {
            AccountEventAction.Status.ok
        }
        val title = txType.takeIfNotBlank() ?: "Transaction"
        val sender = resolvedTonTransfer?.sender ?: jettonAction?.sender
        val recipient = resolvedTonTransfer?.recipient ?: jettonAction?.recipient
        val previewAccounts = listOfNotNull(sender, recipient).distinctBy { it.address }

        return AccountEvent(
            eventId = uniqueKey(),
            account = fallbackAccountId.toAccountAddress(),
            timestamp = utime,
            actions = listOf(
                AccountEventAction(
                    type = actionType,
                    status = actionStatus,
                    simplePreview = ActionSimplePreview(
                        name = title,
                        description = reason.takeIfNotBlank() ?: (status.takeIfNotBlank() ?: "Indexer"),
                        accounts = previewAccounts
                    ),
                    baseTransactions = listOfNotNull(hash.takeIfNotBlank()),
                    tonTransfer = resolvedTonTransfer,
                    jettonTransfer = jettonAction
                )
            ),
            isScam = false,
            lt = ltAsLongOrNull(),
            inProgress = status.equals("pending", ignoreCase = true),
            extra = 0
        )
    }

    private fun TonIndexerTransaction.toTonTransferAction(fallbackAccountId: String): TonTransferAction? {
        val message = primaryMessage() ?: return null
        val sender = message.source.orEmpty().ifBlank { inSource.orEmpty().ifBlank { fallbackAccountId } }
        val recipient = message.destination.orEmpty().ifBlank { fallbackAccountId }
        val amountRaw = message.value.takeIfNotBlank()
            ?: inValue.takeIfNotBlank()
            ?: outMessages?.firstOrNull()?.value.takeIfNotBlank()
            ?: "0"
        val amount = parseLongAmount(amountRaw)

        if (sender.isBlank() && recipient.isBlank() && amount == 0L) return null

        return TonTransferAction(
            sender = sender.toAccountAddress(),
            recipient = recipient.toAccountAddress(),
            amount = amount
        )
    }

    private fun TonIndexerTransaction.toJettonTransferAction(fallbackAccountId: String): JettonTransferAction? {
        val transferDetail = detail ?: return null
        if (!transferDetail.isJettonTransferDetail()) return null

        val message = primaryMessage()
        val sender = message?.source.orEmpty().ifBlank { inSource.orEmpty().ifBlank { fallbackAccountId } }
        val recipient = message?.destination.orEmpty().ifBlank { fallbackAccountId }
        val amount = transferDetail.amount.takeIfNotBlank() ?: "0"
        val jettonAddress = transferDetail.asset!!.trim()
        val symbol = jettonAddress.take(6)

        return JettonTransferAction(
            sendersWallet = sender,
            recipientsWallet = recipient,
            amount = amount,
            jetton = JettonPreview(
                address = jettonAddress,
                name = symbol,
                symbol = symbol,
                decimals = 0,
                image = "",
                verification = "none"
            ),
            sender = sender.toAccountAddress(),
            recipient = recipient.toAccountAddress()
        )
    }

    private fun TonIndexerTransaction.primaryMessage(): TonIndexerMessage? {
        return inMessage ?: outMessages?.firstOrNull()
    }

    private fun TonIndexerTransaction.ltAsLongOrNull(): Long {
        return lt.toLongOrNull() ?: txId?.substringBefore(':')?.toLongOrNull() ?: 0L
    }

    private fun TonIndexerTransaction.uniqueKey(): String {
        return txId.takeIfNotBlank() ?: "${ltAsLongOrNull()}:${hash.takeIfNotBlank().orEmpty()}"
    }

    private fun TonIndexerTransactionDetail.isJettonTransferDetail(): Boolean {
        return kind.equals("transfer", ignoreCase = true) &&
            asset.takeIfNotBlank()?.isTonAssetSymbol() == false
    }

    private fun String.toAccountAddress(): AccountAddress {
        return AccountAddress(
            address = this,
            isScam = false,
            isWallet = true,
            name = null,
            icon = null
        )
    }

    private fun String?.takeIfNotBlank(): String? {
        val value = this?.trim() ?: return null
        return value.takeIf { it.isNotEmpty() }
    }

    private fun String.isTonAssetSymbol(): Boolean {
        return uppercase() in setOf("TON", "NATIVE", "TONCOIN")
    }

    private fun String?.toAccountStatus(): AccountStatus {
        return when (this) {
            "active" -> AccountStatus.active
            "frozen" -> AccountStatus.frozen
            "uninitialized" -> AccountStatus.uninit
            else -> AccountStatus.nonexist
        }
    }

    private fun JsonElement?.objectField(key: String): JsonElement? {
        if (this == null || !this.isJsonObject) return null
        return this.asJsonObject.get(key)
    }

    private fun parseJsonLong(value: JsonElement?): Long? {
        if (value == null || !value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isNumber -> primitive.asLong
            primitive.isString -> primitive.asString.toLongOrNull()
            else -> null
        }
    }

    private fun parseJsonBigInteger(value: JsonElement?): BigInteger? {
        if (value == null || !value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isNumber -> runCatching { BigInteger(primitive.asString) }.getOrNull()
            primitive.isString -> parseNumberString(primitive.asString)
            else -> null
        }
    }

    private fun parseFirstStackNumber(response: TonIndexerRunGetMethodResponse): BigInteger {
        if (response.exitCode != 0) {
            error("Indexer get method failed with exit code ${response.exitCode}")
        }

        val value = response.stack.firstOrNull()?.getOrNull(1)
            ?: error("Indexer get method stack is empty")

        return parseNumber(value) ?: error("Indexer get method returned unsupported stack value: $value")
    }

    private fun parseNumber(value: Any?): BigInteger? {
        return when (value) {
            is Number -> BigInteger.valueOf(value.toLong())
            is String -> parseNumberString(value)
            else -> null
        }
    }

    private fun parseNumberString(value: String): BigInteger? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null

        return runCatching {
            when {
                trimmed.startsWith("-0x", ignoreCase = true) -> {
                    BigInteger(trimmed.drop(3), 16).negate()
                }

                trimmed.startsWith("0x", ignoreCase = true) -> {
                    BigInteger(trimmed.drop(2), 16)
                }

                else -> {
                    BigInteger(trimmed)
                }
            }
        }.getOrNull()
    }

    private fun parseLongAmount(value: String): Long {
        return runCatching {
            BigInteger(value)
                .coerceAtLeast(BigInteger.ZERO)
                .coerceAtMost(BigInteger.valueOf(Long.MAX_VALUE))
                .toLong()
        }.getOrDefault(0L)
    }

    private fun encodePathSegment(value: String): String {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    }

    private fun indexerBaseUrlOrNull(): String? {
        val normalized = tonIndexerUrl.trim().removeSuffix("/")
        return normalized.takeIf { it.isNotEmpty() }
    }

    private fun String.normalizedTonApiUrlOrNull(): String? {
        val parsed = runCatching { URI(this.trim()) }.getOrNull() ?: return null
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return null
        if (host != TON_API_HOST && !host.endsWith(".$TON_API_HOST")) return null
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        if (scheme != null && scheme != "https") return null
        val resolvedScheme = scheme ?: "https"
        val resolvedPort = if (parsed.port == -1) 443 else parsed.port
        if (resolvedPort != 443) return null
        return "$resolvedScheme://$host"
    }

    private fun String.isTonApiUrl(): Boolean {
        return normalizedTonApiUrlOrNull() != null
    }

    private suspend fun <T> withIndexerFallback(
        block: suspend (indexerBaseUrl: String) -> T,
        fallback: suspend () -> T
    ): T {
        val indexerBaseUrl = indexerBaseUrlOrNull()

        if (indexerBaseUrl != null) {
            val indexerResult = try {
                Result.success(block(indexerBaseUrl))
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Result.failure(exception)
            }
            if (indexerResult.isSuccess) {
                return indexerResult.getOrThrow()
            }
        }

        return fallback()
    }
}
