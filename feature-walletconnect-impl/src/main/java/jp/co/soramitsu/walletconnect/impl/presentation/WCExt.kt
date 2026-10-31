package jp.co.soramitsu.walletconnect.impl.presentation

import androidx.core.net.toUri
import com.reown.android.Core
import com.reown.walletkit.client.Wallet
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

val Core.Model.AppMetaData?.dappUrl: String?
    get() = runCatching {
        this?.url?.toUri()?.schemeSpecificPart?.removePrefix("//")?.removeSuffix("/")
    }.getOrNull() ?: this?.url

/** Whether Reown Verify reports the dApp's origin as a known scam. */
val Wallet.Model.VerifyContext?.isFlaggedAsScam: Boolean
    get() = this?.isScam == true

/**
 * What the request asks to sign. Null when the dApp sent malformed params (for example a
 * `personal_sign` message that is not hex), so a bad request is refused instead of throwing.
 */
val Wallet.Model.SessionRequest.JSONRPCRequest.message: String?
    get() = runCatching { requestMessage(method, params) }.getOrNull()

/** The account the request names, or null when it names none or its params are malformed. */
val Wallet.Model.SessionRequest.JSONRPCRequest.address: String?
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
