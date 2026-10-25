package jp.co.soramitsu.common.data.network.ton

import com.google.gson.annotations.SerializedName

/**
 * The TON network a request is for, named the way TI's service-info (`network`, `chainId`) and every TON
 * block header (`global_id`, config param 19) name it.
 */
enum class TonNetwork(val id: String, val chainId: String, val globalId: Int) {
    Mainnet("mainnet", "ton:mainnet", -239),
    Testnet("testnet", "ton:testnet", -3)
}

/** No TON endpoint has proven that it serves the requested network, so nothing is read or sent. */
class TonProviderUnavailableException(message: String) : IllegalStateException(message)

/** An answer named another TON network (or none) than the one requested. Its data is never shown. */
class TonNetworkMismatchException(
    val requested: TonNetwork,
    val served: String?,
    source: String
) : IllegalStateException(
    "ton_provider_network_mismatch: asked $source for TON ${requested.id}, it answered ${served ?: "without a network"}"
)

/**
 * TI's `/api/indexer/v1/service-info` (ton-indexer `registerRoutes`). Every field is nullable so that an absent
 * field reads as absent: Gson would turn a missing `readOnly` into `false`, which means "accepts transactions".
 */
data class TiServiceInfo(
    @SerializedName("schemaVersion")
    val schemaVersion: Int? = null,
    @SerializedName("serviceId")
    val serviceId: String? = null,
    @SerializedName("ecosystem")
    val ecosystem: String? = null,
    @SerializedName("chainId")
    val chainId: String? = null,
    @SerializedName("network")
    val network: String? = null,
    @SerializedName("publicBaseUrl")
    val publicBaseUrl: String? = null,
    @SerializedName("readOnly")
    val readOnly: Boolean? = null,
    @SerializedName("capabilities")
    val capabilities: List<String>? = null
)

/** The masterchain block a TonAPI server (the registry's TON node) serves as its head. */
data class TonMasterchainHead(
    @SerializedName("global_id")
    val globalId: Int? = null,
    @SerializedName("seqno")
    val seqno: Long? = null
)

/** What TI must list in its service-info before the wallet reads anything from it. */
val TI_REQUIRED_CAPABILITIES = listOf("account-balances", "account-state", "account-transactions", "run-get-method")

/** Why this service-info does not prove that the TI at [baseUrl] serves TON [network], or null when it does. */
fun TiServiceInfo.problemFor(network: TonNetwork, baseUrl: String): String? {
    val expectedBaseUrl = baseUrl.trim().removeSuffix("/")
    val missingCapabilities = TI_REQUIRED_CAPABILITIES - capabilities.orEmpty().toSet()

    return when {
        schemaVersion != 1 -> "service-info schemaVersion $schemaVersion is not 1"
        serviceId != TI_SERVICE_ID -> "service-info names service $serviceId, not $TI_SERVICE_ID"
        ecosystem != "ton" -> "service-info names ecosystem $ecosystem, not ton"
        chainId != network.chainId -> "service-info names chain $chainId, not ${network.chainId}"
        this.network != network.id -> "service-info names network ${this.network}, not ${network.id}"
        publicBaseUrl?.trim()?.removeSuffix("/") != expectedBaseUrl ->
            "service-info names base URL $publicBaseUrl, not $expectedBaseUrl"
        readOnly == null -> "service-info does not say whether it accepts transactions"
        missingCapabilities.isNotEmpty() -> "service-info lacks capabilities $missingCapabilities"
        else -> null
    }
}

private const val TI_SERVICE_ID = "ti.soramitsu.io"
