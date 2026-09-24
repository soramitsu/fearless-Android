package jp.co.soramitsu.wallet.impl.data.historySource

import com.google.gson.Gson
import java.net.URI
import jp.co.soramitsu.common.BuildConfig
import jp.co.soramitsu.common.data.model.CursorPage
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.ChainAssetType
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.BSCTestnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.ethereumChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.goerliChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.optimismChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.polygonTestnetChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.sepoliaChainId
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.runtime.AccountId
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryElement
import jp.co.soramitsu.wallet.impl.data.network.model.response.EtherscanHistoryResponse
import jp.co.soramitsu.wallet.impl.data.network.subquery.HistoryNotSupportedException
import jp.co.soramitsu.wallet.impl.data.network.subquery.OperationsHistoryApi
import jp.co.soramitsu.wallet.impl.domain.interfaces.TransactionFilter
import jp.co.soramitsu.wallet.impl.domain.model.Operation
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val ETHERSCAN_V2_URL = "https://api.etherscan.io/v2/api"
private const val MAX_PAGE_SIZE = 1000

// These chain IDs are present in Etherscan's V2 chain list. Goerli and Mumbai
// are retired and must not fall back to their bundled V1 endpoints.
private val etherscanV2LegacyUrls = mapOf(
    ethereumChainId to "https://api.etherscan.io/api",
    BSCChainId to "https://api.bscscan.com/api",
    BSCTestnetChainId to "https://api-testnet.bscscan.com/api",
    sepoliaChainId to "https://api-sepolia.etherscan.io/api",
    polygonChainId to "https://api.polygonscan.com/api",
    optimismChainId to "https://api-optimistic.etherscan.io/api"
)
private val retiredEtherscanChainIds = setOf(goerliChainId, polygonTestnetChainId)
private val officialEtherscanHosts = setOf(
    "api.etherscan.io",
    "api-goerli.etherscan.io",
    "api-sepolia.etherscan.io",
    "api.bscscan.com",
    "api-testnet.bscscan.com",
    "api.polygonscan.com",
    "api-testnet.polygonscan.com",
    "api-optimistic.etherscan.io"
)

class EtherscanHistorySource(
    private val walletOperationsApi: OperationsHistoryApi,
    private val historyUrl: String,
    private val etherscanV2ApiKey: String = BuildConfig.ETHERSCAN_API_KEY
) : HistorySource {
    private val gson = Gson()

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

        val endpoint = endpointFor(chain.id)
        val isNativeAsset = when (chainAsset.type) {
            ChainAssetType.Normal -> true
            ChainAssetType.BEP20, ChainAssetType.ERC20 -> false
            else -> throw HistoryNotSupportedException()
        }
        // Keep the existing 1,000-record window so high-volume accounts do not
        // lose older transfers when a shorter UI page contains only zero-value calls.
        val offset = MAX_PAGE_SIZE
        val page = if (endpoint.chainId == null) 1 else cursor?.toIntOrNull() ?: 1
        require(cursor == null || (endpoint.chainId != null && cursor.toIntOrNull() != null && page > 0)) {
            "Invalid Etherscan history cursor"
        }

        val response = walletOperationsApi.getEtherscanOperationsHistory(
            url = endpoint.url,
            chainId = endpoint.chainId,
            action = if (isNativeAsset) "txlist" else "tokentx",
            contractAddress = if (isNativeAsset) null else chainAsset.id,
            address = accountId.toHexString(true),
            page = page,
            offset = offset,
            apiKey = endpoint.apiKey
        )
        val rawTransactions = response.transactions(requireFailureFlag = isNativeAsset)
        val transactions = rawTransactions.filter { element ->
            element.value.isNotZero() && if (isNativeAsset) {
                element.contractAddress.isEmpty()
            } else {
                element.contractAddress.equals(chainAsset.id, ignoreCase = true)
            }
        }

        val operations = transactions.map { element ->
            val status = if (element.isError == 0) Operation.Status.COMPLETED else Operation.Status.FAILED
            val fee = element.gasUsed.multiply(element.gasPrice)
            Operation(
                id = element.hash,
                address = accountAddress,
                time = element.timeStamp.toDuration(DurationUnit.SECONDS).inWholeMilliseconds,
                chainAsset = chainAsset,
                type = Operation.Type.Transfer(
                    hash = element.hash,
                    myAddress = accountAddress,
                    amount = element.value,
                    receiver = element.to.lowercase(),
                    sender = element.from.lowercase(),
                    status = status,
                    fee = fee
                )
            )
        }
        val nextCursor = if (endpoint.chainId != null && rawTransactions.size == offset && page < Int.MAX_VALUE) {
            (page + 1).toString()
        } else {
            null
        }
        return CursorPage(nextCursor, operations)
    }

    private fun endpointFor(chainId: String): Endpoint = when (chainId) {
        in etherscanV2LegacyUrls -> {
            if (historyUrl == ETHERSCAN_V2_URL || historyUrl == etherscanV2LegacyUrls[chainId]) {
                check(etherscanV2ApiKey.isNotBlank()) { "Etherscan V2 history API key is unavailable" }
                Endpoint(ETHERSCAN_V2_URL, chainId, etherscanV2ApiKey)
            } else {
                independentEndpoint()
            }
        }
        in retiredEtherscanChainIds -> throw HistoryNotSupportedException()
        // Other networks use independent Etherscan-compatible explorers. Never
        // route an unreviewed chain ID to an official host without chain binding.
        else -> independentEndpoint()
    }

    private fun independentEndpoint(): Endpoint {
        val uri = URI(historyUrl)
        val host = uri.host
        if (uri.scheme != "https" || host.isNullOrBlank() || uri.userInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null || host in officialEtherscanHosts
        ) {
            throw HistoryNotSupportedException()
        }
        return Endpoint(historyUrl, null, null)
    }

    private fun EtherscanHistoryResponse.transactions(requireFailureFlag: Boolean): List<EtherscanHistoryElement> {
        if (status == "0" && message == "No transactions found") {
            val isEmptyArray = result?.isJsonArray == true && result.asJsonArray.size() == 0
            val isNoTransactionsString = result?.isJsonPrimitive == true && result.asString == "No transactions found"
            if (isEmptyArray || isNoTransactionsString) return emptyList()
        }
        check(status == "1" && message == "OK" && result?.isJsonArray == true) {
            // Provider error strings may contain account details; do not include them in UI/logs.
            "Etherscan history provider rejected or malformed the request"
        }
        return result!!.asJsonArray.map { json ->
            check(json.isJsonObject) { "Etherscan history record is malformed" }
            val record = json.asJsonObject
            val requiredFields = listOf("timeStamp", "hash", "from", "to", "contractAddress", "value", "gasPrice", "gasUsed")
            check(requiredFields.all { field -> record.has(field) && !record.get(field).isJsonNull }) {
                "Etherscan history record is incomplete"
            }
            check(!requireFailureFlag || (record.has("isError") && !record.get("isError").isJsonNull)) {
                "Etherscan history status is missing"
            }
            val transaction = gson.fromJson(record, EtherscanHistoryElement::class.java)
            check(transaction.timeStamp > 0 && transaction.hash.isNotBlank() &&
                transaction.from.isNotBlank() && transaction.to.isNotBlank() &&
                transaction.value.signum() >= 0 && transaction.gasPrice.signum() >= 0 &&
                transaction.gasUsed.signum() >= 0
            ) { "Etherscan history record is invalid" }
            transaction
        }
    }

    private data class Endpoint(val url: String, val chainId: String?, val apiKey: String?)
}
