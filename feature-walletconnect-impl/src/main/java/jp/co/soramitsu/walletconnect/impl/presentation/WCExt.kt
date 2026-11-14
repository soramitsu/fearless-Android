package jp.co.soramitsu.walletconnect.impl.presentation

import co.jp.soramitsu.walletconnect.model.WalletConnectMetadata
import co.jp.soramitsu.walletconnect.model.WalletConnectRpcRequest
import co.jp.soramitsu.walletconnect.model.WalletConnectValidation
import co.jp.soramitsu.walletconnect.model.WalletConnectVerifyContext
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

enum class Caip2Namespace(val value: String) {
    EIP155("eip155"),
    POLKADOT("polkadot")
}

// https://github.com/ChainAgnostic/CAIPs/blob/master/CAIPs/caip-2.md#syntax
@Suppress("MagicNumber")
val Chain.caip2id: String
    get() {
        val namespace = if (isEthereumChain) Caip2Namespace.EIP155 else Caip2Namespace.POLKADOT
        val chainId = id.substring(0, Integer.min(id.length, 32))
        return namespace.value + ":" + chainId
    }

/** The dApp's self-reported URL without its scheme, as the screens showed it. Not verified. */
val WalletConnectMetadata?.dappUrl: String?
    get() = this?.url?.let(::hostOf)

/** `https://app.example/path` → `app.example/path`: the scheme and a trailing slash dropped. */
internal fun hostOf(url: String): String = url.substringAfter("://", url).removeSuffix("/")

/** Whether WalletConnect Verify reports the dApp's origin as a known scam. */
val WalletConnectVerifyContext?.isFlaggedAsScam: Boolean
    get() = this?.isScam == true

/**
 * The site to show for a dApp: the origin Verify attested when there is one, otherwise the URL the
 * dApp claims, which [WalletConnectVerifyContext.validation] then marks as unverified.
 */
val WalletConnectVerifyContext.displayedHost: String?
    get() = when (validation) {
        WalletConnectValidation.VALID, WalletConnectValidation.INVALID -> verifiedOrigin?.let(::hostOf)
        WalletConnectValidation.UNKNOWN -> claimedUrl?.let(::hostOf)
    }

/**
 * What the request asks to sign. Null when the dApp sent malformed params (for example a
 * `personal_sign` message that is not hex), so a bad request is refused instead of throwing.
 */
val WalletConnectRpcRequest.message: String?
    get() = runCatching { requestMessage(method, params) }.getOrNull()

/** The account the request names, or null when it names none or its params are malformed. */
val WalletConnectRpcRequest.address: String?
    get() = runCatching { requestAddress(method, params) }.getOrNull()

internal fun String.toJsonObject(): JsonObject = Json.parseToJsonElement(this).jsonObject

internal fun String.toJsonArray(): JsonArray = Json.parseToJsonElement(this).jsonArray

/** A string value as is, any other value as its JSON text. */
internal fun JsonElement?.text(): String {
    val element = requireNotNull(this) { "Missing request parameter" }
    return if (element is JsonPrimitive) element.content else element.toString()
}

internal fun JsonElement?.stringValue(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun requestMessage(method: String, params: String): String = when (method) {
    WalletConnectMethod.PolkadotSignMessage.method -> params.toJsonObject()["message"].text()
    WalletConnectMethod.PolkadotSignTransaction.method -> params.toJsonObject()["transactionPayload"].text()
    WalletConnectMethod.EthereumSendTransaction.method,
    WalletConnectMethod.EthereumSignTransaction.method -> params.toJsonArray()[0].text()
    WalletConnectMethod.EthereumSignTypedData.method,
    WalletConnectMethod.EthereumSignTypedDataV4.method -> params.toJsonArray()[1].text()
    WalletConnectMethod.EthereumPersonalSign.method -> params.toJsonArray()[0].text().fromHex().toString(Charsets.UTF_8)
    WalletConnectMethod.EthereumSign.method -> params.toJsonArray()[1].text().fromHex().toString(Charsets.UTF_8)
    else -> "$method's params: $params"
}

private fun requestAddress(method: String, params: String): String? = when (method) {
    WalletConnectMethod.PolkadotSignMessage.method,
    WalletConnectMethod.PolkadotSignTransaction.method -> params.toJsonObject()["address"]
    WalletConnectMethod.EthereumPersonalSign.method -> params.toJsonArray()[1]
    WalletConnectMethod.EthereumSignTransaction.method,
    WalletConnectMethod.EthereumSendTransaction.method -> (params.toJsonArray()[0] as? JsonObject)?.get("from")
    WalletConnectMethod.EthereumSignTypedData.method,
    WalletConnectMethod.EthereumSignTypedDataV4.method,
    WalletConnectMethod.EthereumSign.method -> params.toJsonArray()[0]
    else -> null
}.stringValue()
