package jp.co.soramitsu.polkamarkt.impl.data

import com.google.gson.Gson
import com.google.gson.JsonObject
import jp.co.soramitsu.account.api.domain.interfaces.AccountRepository
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.data.network.config.ProductFeatureToggleStore
import jp.co.soramitsu.common.data.network.config.MutationAuthorizationStore
import jp.co.soramitsu.common.data.network.config.MutationCapability
import jp.co.soramitsu.common.data.network.rpc.BulkRetriever
import jp.co.soramitsu.common.data.network.rpc.retrieveAllValues
import jp.co.soramitsu.common.utils.u32ArgumentFromStorageKey
import jp.co.soramitsu.core.extrinsic.ExtrinsicService
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.DictEnum
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.composite.Struct
import jp.co.soramitsu.fearless_utils.runtime.definitions.types.fromHex
import jp.co.soramitsu.fearless_utils.runtime.extrinsic.ExtrinsicBuilder
import jp.co.soramitsu.fearless_utils.runtime.metadata.moduleOrNull
import jp.co.soramitsu.fearless_utils.runtime.metadata.storage
import jp.co.soramitsu.fearless_utils.runtime.metadata.storageKey
import jp.co.soramitsu.fearless_utils.wsrpc.executeAsync
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.nonNull
import jp.co.soramitsu.fearless_utils.wsrpc.mappers.pojo
import jp.co.soramitsu.fearless_utils.wsrpc.request.runtime.RuntimeRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktAccountCapability
import jp.co.soramitsu.polkamarkt.api.PolkamarktClaimable
import jp.co.soramitsu.polkamarkt.api.PolkamarktMarket
import jp.co.soramitsu.polkamarkt.api.PolkamarktMutation
import jp.co.soramitsu.polkamarkt.api.PolkamarktOutcome
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuote
import jp.co.soramitsu.polkamarkt.api.PolkamarktQuoteRequest
import jp.co.soramitsu.polkamarkt.api.PolkamarktRuntimeCapabilities
import jp.co.soramitsu.polkamarkt.api.PolkamarktSnapshot
import jp.co.soramitsu.polkamarkt.api.PolkamarktTradeMode
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

private const val PALLET = "Polkamarkt"
private const val MAX_MARKETS = 96
private const val MAX_ACTIVITY = 50
private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

private const val LATEST_MARKETS_QUERY = """
query PolkamarktLatestMarkets(${'$'}limit: Int = 96) {
  markets(first: ${'$'}limit, orderBy: [VOLUME_USD_DESC]) { edges { node {
    id marketId conditionId creator title description category oracle resolutionSource closeBlock status mechanism
    collateralAsset liquidityUsd: liquidityUSD volumeUsd: volumeUSD probability priceYes dpmCollateral
  } } }
}
"""

private const val LEGACY_MARKETS_QUERY = """
query PolkamarktLegacyMarkets(${'$'}limit: Int = 96) {
  markets(first: ${'$'}limit) { edges { node {
    id marketId conditionId creator title description category oracle resolutionSource closeBlock status mechanism
    collateralAsset liquidityUsd: liquidityUSD volumeUsd: volumeUSD probability priceYes
  } } }
}
"""

private const val HISTORY_QUERY = """
query PolkamarktMarketHistory(${'$'}marketId: Int!, ${'$'}limit: Int = 96) {
  marketSnapshots(first: ${'$'}limit, orderBy: [TIMESTAMP_ASC], filter: { marketId: { equalTo: ${'$'}marketId } }) {
    edges { node { id marketId timestamp blockHeight probability priceYes priceNo liquidityUsd: liquidityUSD volumeUsd: volumeUSD status } }
  }
}
"""

private const val ACTIVITY_QUERY = """
query PolkamarktAccountActivity(${'$'}account: String!, ${'$'}limit: Int = 50) {
  accountPositions(first: ${'$'}limit, orderBy: [UPDATED_AT_DESC], filter: { account: { equalTo: ${'$'}account } }) {
    edges { node { id marketId outcome shares yesShares noShares netCollateralPaid claimablePayout isCreator status updatedAt market { id marketId title status } } }
  }
  accountTrades(first: ${'$'}limit, orderBy: [TIMESTAMP_DESC], filter: { account: { equalTo: ${'$'}account } }) {
    edges { node { id marketId side outcome collateralAmount sharesIn sharesOut feeAmount timestamp blockNumber extrinsicHash } }
  }
}
"""

private const val LEGACY_ACTIVITY_QUERY = """
query PolkamarktLegacyAccountActivity(${'$'}account: String!, ${'$'}limit: Int = 50) {
  accountPositions(first: ${'$'}limit, filter: { account: { equalTo: ${'$'}account } }) {
    edges { node { id marketId outcome shares yesShares noShares netCollateralPaid isCreator status } }
  }
  accountTrades(first: ${'$'}limit, filter: { account: { equalTo: ${'$'}account } }) {
    edges { node { id marketId side outcome sharesIn sharesOut blockNumber extrinsicHash } }
  }
}
"""

class PolkamarktIndexer @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    suspend fun markets(endpoint: String, currentBlock: String): List<PolkamarktMarket> {
        val latest = runCatching { query(endpoint, LATEST_MARKETS_QUERY, mapOf("limit" to MAX_MARKETS)) }
        val root = latest.getOrNull()
            ?.takeIf { parseIndexedMarkets(it, currentBlock).isNotEmpty() }
            ?: query(endpoint, LEGACY_MARKETS_QUERY, mapOf("limit" to MAX_MARKETS))
        return parseIndexedMarkets(root, currentBlock)
    }

    suspend fun history(endpoint: String, marketId: String?) = if (marketId == null) {
        emptyList()
    } else {
        runCatching {
            query(endpoint, HISTORY_QUERY, mapOf("marketId" to marketId.toInt(), "limit" to MAX_MARKETS))
        }.map(::parseHistory).getOrDefault(emptyList())
    }

    internal suspend fun activity(endpoint: String, account: String?): ParsedActivity {
        if (account == null) return ParsedActivity(emptyList(), emptyList())
        return runCatching {
            query(endpoint, ACTIVITY_QUERY, mapOf("account" to account, "limit" to MAX_ACTIVITY))
        }.recoverCatching {
            query(endpoint, LEGACY_ACTIVITY_QUERY, mapOf("account" to account, "limit" to MAX_ACTIVITY))
        }.map(::parseActivity).getOrDefault(ParsedActivity(emptyList(), emptyList()))
    }

    private suspend fun query(endpoint: String, query: String, variables: Map<String, Any>): JsonObject =
        withContext(Dispatchers.IO) {
            val body = gson.toJson(mapOf("query" to query, "variables" to variables)).toRequestBody(JSON_MEDIA_TYPE)
            val request = Request.Builder().url(endpoint).post(body).build()
            client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Polkamarkt indexer returned HTTP ${response.code}." }
                val payload = gson.fromJson(response.body?.charStream(), JsonObject::class.java)
                val errors = payload?.getAsJsonArray("errors")
                check(errors == null || errors.size() == 0) {
                    errors?.firstOrNull()?.asJsonObject?.get("message")?.asString
                        ?: "Polkamarkt indexer query failed."
                }
                payload?.getAsJsonObject("data") ?: JsonObject()
            }
        }
}

internal data class RpcMethodsResponse(val methods: List<String> = emptyList())
internal data class ChainHeader(val number: String? = null)

class PolkamarktRuntime @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val bulkRetriever: BulkRetriever
) {
    suspend fun currentBlock(): String = runCatching {
        val socket = chainRegistry.awaitConnection(soraMainChainId).socketService
        val header = socket.executeAsync(
            RuntimeRequest("chain_getHeader", emptyList<Any>()),
            mapper = pojo<ChainHeader>().nonNull()
        )
        normalizeInteger(header.number) ?: "0"
    }.getOrDefault("0")

    suspend fun capabilities(): PolkamarktRuntimeCapabilities {
        val runtime = chainRegistry.getRuntimeOrNull(soraMainChainId)
        val module = runtime?.metadata?.moduleOrNull(PALLET)
        val storages = listOf("Markets", "Conditions").associateWith { name ->
            runCatching { module?.storage(name) }.getOrNull() != null
        }
        val calls = module?.calls.orEmpty().keys.map(::normalizeName).toSet()
        val methods = rpcMethods().map(::normalizeName).toSet()
        val browse = storages.values.all { it }
        val marketState = "polkamarktmarketstate" in methods
        val quoteBuy = "polkamarktquotebuy" in methods
        val quoteSell = "polkamarktquotesell" in methods
        val reasons = buildList {
            if (!browse) add("Connected SORA runtime does not expose Polkamarkt storage.")
            if (!marketState) add("Live market state is unavailable; trading is disabled.")
            if (!quoteBuy || !quoteSell) add("Authoritative runtime quotes are unavailable.")
            if ("claimcreatorfees" !in calls) add("Creator claims are unavailable on this runtime.")
        }
        return PolkamarktRuntimeCapabilities(
            browse = browse,
            quoteBuy = quoteBuy,
            quoteSell = quoteSell,
            marketState = marketState,
            buy = "buy" in calls,
            sell = "sell" in calls,
            claimMarket = "claimmarket" in calls,
            claimCreatorFees = "claimcreatorfees" in calls,
            reasons = reasons
        )
    }

    suspend fun markets(currentBlock: String): List<PolkamarktMarket> {
        val runtime = chainRegistry.getRuntimeOrNull(soraMainChainId) ?: return emptyList()
        val module = runtime.metadata.moduleOrNull(PALLET) ?: return emptyList()
        val storage = runCatching { module.storage("Markets") }.getOrNull() ?: return emptyList()
        val valueType = storage.type.value ?: return emptyList()
        val socket = chainRegistry.awaitConnection(soraMainChainId).socketService
        val prefix = storage.storageKey(runtime)
        return bulkRetriever.retrieveAllValues(socket, prefix).entries.take(MAX_MARKETS).mapNotNull { (key, hex) ->
            val marketId = runCatching { key.u32ArgumentFromStorageKey().toString() }.getOrNull() ?: return@mapNotNull null
            val market = hex?.let { runCatching { valueType.fromHex(runtime, it) as? Struct.Instance }.getOrNull() }
                ?: return@mapNotNull null
            val conditionId = market.integer("conditionId", "condition_id")
            val condition = conditionId?.let { queryStruct("Conditions", it.toBigInteger()) }
            val details = conditionId?.let { queryStruct("ConditionDetails", it.toBigInteger()) }
            val state = runCatching { rpcJson("polkamarkt_marketState", listOf(marketId.toBigInteger())) }.getOrNull()
            val title = condition?.text("question") ?: return@mapNotNull null
            val closeBlock = market.integer("closeBlock", "close_block")
            val status = market.text("status")
            val oracle = condition?.text("oracle")
            val resolutionSource = condition?.text("resolutionSource", "resolution_source")
            val probabilityBps = state?.firstString("impliedYesProbabilityBps", "implied_yes_probability_bps")
            PolkamarktMarket(
                id = marketId,
                conditionId = conditionId,
                creator = market.text("creator"),
                title = title,
                description = listOfNotNull(
                    oracle?.let { "Resolved by $it" },
                    resolutionSource?.let { "Source: $it" }
                ).joinToString(". ").ifEmpty { "Read directly from SORA runtime storage." },
                category = details?.text("category") ?: "Other",
                oracle = oracle,
                resolutionSource = resolutionSource,
                closeBlock = closeBlock,
                runtimeStatus = status,
                mechanism = state?.firstString("mechanism") ?: market.text("mechanism"),
                liquidityUsd = state?.firstString("dpmCollateral", "dpm_collateral")
                    ?.let(::normalizeInteger)?.let(::fromCodecAmount) ?: "0",
                volumeUsd = queryInteger("MarketVolume", marketId.toBigInteger())?.let(::fromCodecAmount) ?: "0",
                probabilityPercent = probabilityBps?.let(::normalizeInteger)?.toBigIntegerOrNull()
                    ?.let { bps -> bps.toBigDecimal().movePointLeft(2).stripTrailingZeros().toPlainString() },
                displayStatus = deriveStatus(status, closeBlock, currentBlock),
                runtimeOnly = true
            )
        }
    }

    suspend fun quote(request: PolkamarktQuoteRequest): JsonObject {
        val amount = toCodecAmount(request.amount)
        require(amount > BigInteger.ZERO) { "Amount must be greater than zero." }
        val method = if (request.mode == PolkamarktTradeMode.Buy) "polkamarkt_quoteBuy" else "polkamarkt_quoteSell"
        return rpcJson(method, listOf(request.marketId.toU32(), request.outcome.name, amount))
    }

    suspend fun claimable(account: String, marketIds: List<String>): List<PolkamarktClaimable> {
        if ("polkamarkt_claimable" !in rpcMethods()) return emptyList()
        return coroutineScope {
            marketIds.distinct().take(MAX_ACTIVITY).map { marketId ->
                async {
                    runCatching {
                        parseClaimable(
                            rpcJson("polkamarkt_claimable", listOf(account, marketId.toU32())),
                            account,
                            marketId
                        )
                    }.getOrNull()
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun rpcMethods(): Set<String> = runCatching {
        chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
            RuntimeRequest("rpc_methods", emptyList<Any>()),
            mapper = pojo<RpcMethodsResponse>().nonNull()
        ).methods.toSet()
    }.getOrDefault(emptySet())

    private suspend fun rpcJson(method: String, params: List<Any>): JsonObject =
        chainRegistry.awaitConnection(soraMainChainId).socketService.executeAsync(
            RuntimeRequest(method, params),
            mapper = pojo<JsonObject>().nonNull()
        )

    private suspend fun queryStruct(storageName: String, key: BigInteger): Struct.Instance? {
        val runtime = chainRegistry.getRuntimeOrNull(soraMainChainId) ?: return null
        val storage = runCatching { runtime.metadata.moduleOrNull(PALLET)?.storage(storageName) }.getOrNull() ?: return null
        val type = storage.type.value ?: return null
        val storageKey = runCatching { storage.storageKey(runtime, key) }.getOrNull() ?: return null
        val hex = bulkRetriever.queryKeys(
            chainRegistry.awaitConnection(soraMainChainId).socketService,
            listOf(storageKey)
        )[storageKey] ?: return null
        return runCatching { type.fromHex(runtime, hex) as? Struct.Instance }.getOrNull()
    }

    private suspend fun queryInteger(storageName: String, key: BigInteger): String? {
        val runtime = chainRegistry.getRuntimeOrNull(soraMainChainId) ?: return null
        val storage = runCatching { runtime.metadata.moduleOrNull(PALLET)?.storage(storageName) }.getOrNull() ?: return null
        val type = storage.type.value ?: return null
        val storageKey = runCatching { storage.storageKey(runtime, key) }.getOrNull() ?: return null
        val hex = bulkRetriever.queryKeys(
            chainRegistry.awaitConnection(soraMainChainId).socketService,
            listOf(storageKey)
        )[storageKey] ?: return null
        return runCatching { normalizeInteger(type.fromHex(runtime, hex).toString()) }.getOrNull()
    }
}

class PolkamarktInteractorImpl @Inject constructor(
    private val chainRegistry: ChainRegistry,
    private val indexer: PolkamarktIndexer,
    private val runtime: PolkamarktRuntime,
    private val accountRepository: AccountRepository,
    private val walletRepository: WalletRepository,
    private val extrinsicService: ExtrinsicService,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val toggles: ProductFeatureToggleStore,
    private val mutationAuthorization: MutationAuthorizationStore
) : jp.co.soramitsu.polkamarkt.api.PolkamarktInteractor {

    override suspend fun snapshot(marketId: String?): PolkamarktSnapshot = coroutineScope {
        val chain = chainRegistry.getChain(soraMainChainId)
        val currentBlock = runtime.currentBlock()
        val wallet = accountRepository.getSelectedMetaAccount()
        val address = wallet.address(chain)
        val indexerEndpoint = chain.externalApi?.history?.url
        val warnings = mutableListOf<String>()

        val indexed = async {
            if (indexerEndpoint == null) {
                warnings += "SORA Polkamarkt indexer is not configured."
                emptyList()
            } else runCatching { indexer.markets(indexerEndpoint, currentBlock) }
                .onFailure { warnings += it.message ?: "Polkamarkt indexer is unavailable." }
                .getOrDefault(emptyList())
        }
        val onChain = async {
            runCatching { runtime.markets(currentBlock) }
                .onFailure { warnings += it.message ?: "SORA runtime market catalog is unavailable." }
                .getOrDefault(emptyList())
        }
        val history = async { indexerEndpoint?.let { indexer.history(it, marketId) }.orEmpty() }
        val activity = async { indexerEndpoint?.let { indexer.activity(it, address) } ?: ParsedActivity(emptyList(), emptyList()) }
        val capabilities = async { runtime.capabilities() }
        val assets = async { walletRepository.getAssets(wallet.id).filter { it.chainId == chain.id } }

        val indexedMarkets = indexed.await()
        val runtimeMarkets = onChain.await()
        val accountActivity = activity.await()
        val capability = capabilities.await()
        val soraAssets = assets.await()
        val claims = address?.let { account ->
            val ids = accountActivity.positions.map { it.marketId } +
                mergeMarkets(indexedMarkets, runtimeMarkets)
                    .filter { it.displayStatus.name in setOf("Resolved", "Cancelled") }
                    .map { it.id }
            runtime.claimable(account, ids)
        }.orEmpty()
        val signable = wallet.accountId(chain) != null && isLocallySignable(wallet.id, chain)
        val hasKusd = soraAssets.any { it.token.configuration.currencyId.equals(KUSD_ASSET_ID, ignoreCase = true) && it.transferable.signum() > 0 }
        val hasXor = soraAssets.any { it.token.configuration.currencyId.equals(XOR_ASSET_ID, ignoreCase = true) && it.transferable.signum() > 0 }
        val accountCapability = PolkamarktAccountCapability(
            address = address,
            signable = signable,
            hasKusd = hasKusd,
            hasXorForFees = hasXor,
            reason = when {
                address == null -> "Add a SORA account to trade or claim."
                !signable -> "This SORA account is watch-only or requires an unsupported external signer."
                !hasXor -> "Add XOR to pay SORA network fees."
                !hasKusd -> "Add KUSD collateral to buy market shares."
                else -> null
            }
        )
        warnings += capability.reasons
        PolkamarktSnapshot(
            currentBlock = currentBlock,
            markets = mergeMarkets(indexedMarkets, runtimeMarkets),
            history = history.await(),
            positions = accountActivity.positions,
            trades = accountActivity.trades,
            claimable = claims,
            capabilities = capability,
            account = accountCapability,
            indexerStale = indexerEndpoint == null || indexedMarkets.isEmpty(),
            warnings = warnings.distinct()
        )
    }

    override suspend fun quote(request: PolkamarktQuoteRequest): PolkamarktQuote {
        val chain = chainRegistry.getChain(soraMainChainId)
        val response = runtime.quote(request)
        val resultCodec = if (request.mode == PolkamarktTradeMode.Buy) {
            response.firstString("sharesOut", "shares_out")
        } else response.firstString("collateralOut", "collateral_out")
        val result = normalizeInteger(resultCodec) ?: error("SORA runtime returned no quote.")
        val amount = normalizeInteger(
            if (request.mode == PolkamarktTradeMode.Buy) response.firstString("collateralIn", "collateral_in")
            else response.firstString("sharesIn", "shares_in")
        ) ?: toCodecAmount(request.amount).toString()
        val minimum = minimumAfterSlippage(result.toBigInteger())
        val networkFee = extrinsicService.estimateFee(chain) {
            polkamarktTrade(request.marketId, request.mode, request.outcome, amount.toBigInteger(), minimum)
        }
        return PolkamarktQuote(
            marketId = normalizeInteger(response.firstString("marketId", "market_id")) ?: request.marketId,
            mode = request.mode,
            outcome = response.firstString("outcome")?.let { if (it.equals("No", true)) PolkamarktOutcome.No else PolkamarktOutcome.Yes }
                ?: request.outcome,
            amount = fromCodecAmount(amount),
            feeAmount = fromCodecAmount(normalizeInteger(response.firstString("feeAmount", "fee_amount")) ?: "0"),
            networkFee = fromCodecAmount(networkFee.toString()),
            resultAmount = fromCodecAmount(result),
            minimumResult = fromCodecAmount(minimum.toString())
        )
    }

    override suspend fun mutate(request: PolkamarktMutation): Result<String> = runCatching {
        check(toggles.polkamarktMutationsEnabled) { "Polkamarkt actions are temporarily disabled." }
        check(polkaswapInteractor.hasReadDisclaimer) { "Accept the Polkaswap and SORA risk disclaimer first." }
        val authorization = authorizeMutation(request)

        // The submitter binds immutable SCALE intent and rechecks authorization at key/sign/actual-send boundaries.
        check(toggles.polkamarktMutationsEnabled) { "Polkamarkt actions are temporarily disabled." }
        check(polkaswapInteractor.hasReadDisclaimer) { "Accept the Polkaswap and SORA risk disclaimer first." }
        extrinsicService.submitAuthorizedExtrinsic(
            chain = authorization.chain,
            accountId = authorization.accountId,
            authorize = { intent -> mutationAuthorization.acquire(MutationCapability.POLKAMARKT, intent) },
            formExtrinsic = authorization.submission
        ).getOrThrow()
    }

    private suspend fun authorizeMutation(request: PolkamarktMutation): AuthorizedPolkamarktSubmission {
        val chain = chainRegistry.getChain(soraMainChainId)
        val wallet = accountRepository.getSelectedMetaAccount()
        val accountId = wallet.accountId(chain) ?: error("Add a SORA account.")
        check(isLocallySignable(wallet.id, chain)) { "This SORA account cannot sign in this wallet." }
        val snapshot = snapshot(request.marketId)
        check(snapshot.account.address == wallet.address(chain)) {
            "The selected SORA account changed. Refresh before confirming."
        }

        suspend fun exactBalances(): Pair<String, String> {
            val soraAssets = walletRepository.getAssets(wallet.id).filter { it.chainId == chain.id }
            fun exactBalance(assetId: String): BigDecimal {
                val matches = soraAssets.filter {
                    it.token.configuration.currencyId.equals(assetId, ignoreCase = true)
                }
                check(matches.size <= 1) { "The exact SORA asset balance is ambiguous." }
                return matches.singleOrNull()?.transferable ?: BigDecimal.ZERO
            }
            return exactBalance(XOR_ASSET_ID).stripTrailingZeros().toPlainString() to
                exactBalance(KUSD_ASSET_ID).stripTrailingZeros().toPlainString()
        }

        val submission: suspend ExtrinsicBuilder.() -> Unit
        when (request) {
            is PolkamarktMutation.Trade -> {
                val freshQuote = quote(
                    PolkamarktQuoteRequest(
                        marketId = request.marketId,
                        mode = request.mode,
                        outcome = request.outcome,
                        amount = request.amount
                    )
                )
                val capabilities = runtime.capabilities()
                val finalBlock = runtime.currentBlock()
                val currentMarket = runtime.markets(finalBlock).singleOrNull { it.id == request.marketId }
                val (xorBalance, kusdBalance) = exactBalances()
                validatePolkamarktTradeExecution(
                    request = request,
                    market = currentMarket,
                    capabilities = capabilities,
                    freshQuote = freshQuote,
                    balances = PolkamarktExecutionBalances(
                        kusd = kusdBalance,
                        xor = xorBalance,
                        yesShares = availableShares(snapshot.positions, request.marketId, PolkamarktOutcome.Yes),
                        noShares = availableShares(snapshot.positions, request.marketId, PolkamarktOutcome.No)
                    )
                )
                submission = {
                    polkamarktTrade(
                        freshQuote.marketId,
                        freshQuote.mode,
                        freshQuote.outcome,
                        toCodecAmount(freshQuote.amount),
                        toCodecAmount(freshQuote.minimumResult)
                    )
                }
            }
            is PolkamarktMutation.ClaimMarket, is PolkamarktMutation.ClaimCreatorFees -> {
                val callName = when (request) {
                    is PolkamarktMutation.ClaimMarket -> "claim_market"
                    is PolkamarktMutation.ClaimCreatorFees -> "claim_creator_fees"
                    is PolkamarktMutation.Trade -> error("Trade handled above")
                }
                val networkFee = extrinsicService.estimateFee(chain) {
                    polkamarktClaim(callName, request.marketId)
                }.toString().let(::fromCodecAmount)
                val capabilities = runtime.capabilities()
                val currentClaim = runtime.claimable(
                    requireNotNull(wallet.address(chain)),
                    listOf(request.marketId)
                ).singleOrNull { it.marketId == request.marketId }
                val (xorBalance, _) = exactBalances()
                validatePolkamarktClaimExecution(
                    request = request,
                    capabilities = capabilities,
                    claimable = currentClaim,
                    xorBalance = xorBalance,
                    networkFee = networkFee
                )
                submission = { polkamarktClaim(callName, request.marketId) }
            }
        }

        // Account/key ownership is the last asynchronous authorization check. The
        // synchronous policy checks in mutate run immediately after this returns.
        val finalWallet = accountRepository.getSelectedMetaAccount()
        val finalAccountId = finalWallet.accountId(chain)
        check(finalWallet.id == wallet.id && finalAccountId?.contentEquals(accountId) == true) {
            "The selected wallet changed. Review the transaction again."
        }
        check(finalWallet.address(chain) == snapshot.account.address) {
            "The selected SORA account changed. Refresh before confirming."
        }
        check(isLocallySignable(finalWallet.id, chain)) {
            "This SORA account can no longer sign in this wallet."
        }
        check(toggles.polkamarktMutationsEnabled) { "Polkamarkt actions are temporarily disabled." }
        check(polkaswapInteractor.hasReadDisclaimer) { "Accept the Polkaswap and SORA risk disclaimer first." }

        return AuthorizedPolkamarktSubmission(
            chain = chain,
            accountId = accountId,
            submission = submission
        )
    }

    private suspend fun isLocallySignable(metaId: Long, chain: Chain): Boolean = runCatching {
        val wallet = accountRepository.getMetaAccount(metaId)
        val account = wallet.accountId(chain) ?: return@runCatching false
        extrinsicService.hasLocalSubstrateKey(chain, account)
    }.getOrDefault(false)
}

private data class AuthorizedPolkamarktSubmission(
    val chain: Chain,
    val accountId: ByteArray,
    val submission: suspend ExtrinsicBuilder.() -> Unit
)

private fun availableShares(
    positions: List<jp.co.soramitsu.polkamarkt.api.PolkamarktPosition>,
    marketId: String,
    outcome: PolkamarktOutcome
): String = positions.asSequence()
    .filter { it.marketId == marketId }
    .mapNotNull { position ->
        val exactOutcomeValue = when (outcome) {
            PolkamarktOutcome.Yes -> position.yesShares
            PolkamarktOutcome.No -> position.noShares
        }
        exactOutcomeValue?.asIndexedShareAmount()
            ?: position.shares
                ?.takeIf { position.outcome.equals(outcome.name, ignoreCase = true) }
                ?.asIndexedShareAmount()
    }
    .fold(BigDecimal.ZERO, BigDecimal::add)
    .stripTrailingZeros()
    .toPlainString()

/** Indexer integer share fields are codec amounts; decimal fields are already display amounts. */
private fun String.asIndexedShareAmount(): BigDecimal? {
    val normalized = normalizeDecimal(this, "")
    if (normalized.isEmpty()) return null
    return if ('.' in normalized) {
        normalized.toBigDecimalOrNull()
    } else {
        fromCodecAmount(normalized).toBigDecimalOrNull()
    }
}

private fun ExtrinsicBuilder.polkamarktTrade(
    marketId: String,
    mode: PolkamarktTradeMode,
    outcome: PolkamarktOutcome,
    amount: BigInteger,
    minimum: BigInteger
) = call(
    PALLET,
    if (mode == PolkamarktTradeMode.Buy) "buy" else "sell",
    if (mode == PolkamarktTradeMode.Buy) mapOf(
        "market_id" to marketId.toU32(),
        "outcome" to DictEnum.Entry(outcome.name, null),
        "collateral_in" to amount,
        "min_shares_out" to minimum
    ) else mapOf(
        "market_id" to marketId.toU32(),
        "outcome" to DictEnum.Entry(outcome.name, null),
        "shares_in" to amount,
        "min_collateral_out" to minimum
    )
)

private fun ExtrinsicBuilder.polkamarktClaim(callName: String, marketId: String) = call(
    PALLET,
    callName,
    mapOf("market_id" to marketId.toU32())
)

private fun String.toU32(): BigInteger {
    val value = normalizeInteger(this)?.toBigIntegerOrNull() ?: error("Invalid Polkamarkt market id.")
    require(value in BigInteger.ZERO..BigInteger("4294967295")) { "Polkamarkt market id is outside u32." }
    return value
}

private fun normalizeName(value: String) = value.replace("_", "").lowercase()

private fun Struct.Instance.value(vararg keys: String): Any? = keys.firstNotNullOfOrNull { key ->
    runCatching { get<Any>(key) }.getOrNull()
        ?: runCatching { get<Any>(key.replace(Regex("_([a-z])")) { it.groupValues[1].uppercase() }) }.getOrNull()
}

private fun Struct.Instance.integer(vararg keys: String): String? = value(*keys)?.toString()?.let(::normalizeInteger)

private fun Struct.Instance.text(vararg keys: String): String? = decodeRuntimeText(value(*keys))

private fun decodeRuntimeText(value: Any?): String? = when (value) {
    null -> null
    is ByteArray -> value.toString(Charsets.UTF_8).trim('\u0000').trim().takeIf(String::isNotEmpty)
    is List<*> -> value.mapNotNull { (it as? Number)?.toInt()?.toByte() }.toByteArray()
        .toString(Charsets.UTF_8).trim('\u0000').trim().takeIf(String::isNotEmpty)
    is DictEnum.Entry<*> -> value.name
    else -> value.toString().trim().takeIf(String::isNotEmpty)
}
