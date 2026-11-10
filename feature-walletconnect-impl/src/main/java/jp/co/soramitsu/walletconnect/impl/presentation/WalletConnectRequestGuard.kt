package jp.co.soramitsu.walletconnect.impl.presentation

import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.math.BigInteger

/**
 * The wallet refuses to sign a request: its params are malformed or it is not bound to what the
 * user approved. The dApp gets an error response and retrying the same request cannot succeed.
 */
internal open class WalletConnectRequestRejectedException(reason: String) : IllegalStateException(reason)

internal fun rejectRequest(reason: String): Nothing = throw WalletConnectRequestRejectedException(reason)

internal inline fun requireRequest(condition: Boolean, reason: () -> String) {
    if (!condition) rejectRequest(reason())
}

private const val GENESIS_HASH_SIZE = 32

/**
 * A chain id as dApps write it: a JSON number, a decimal string or a `0x` hex string. Null when
 * absent; anything else is refused rather than guessed.
 */
internal fun evmChainIdOf(value: JsonElement?): BigInteger? {
    if (value == null || value is JsonNull) return null
    val text = (value as? JsonPrimitive)?.content ?: rejectRequest("The chain id is malformed")
    val chainId = if (text.startsWith("0x", ignoreCase = true)) {
        text.drop(2).toBigIntegerOrNull(radix = 16)
    } else {
        text.toBigIntegerOrNull()
    }
    return chainId ?: rejectRequest("The chain id is malformed")
}

/**
 * The `domain.chainId` that EIP-712 typed data commits to, or null when it names none. Typed data
 * this strict parser cannot read is refused, so no other parser decides what the check skipped.
 */
internal fun typedDataChainId(typedData: String): BigInteger? {
    val root = runCatching { typedData.toJsonObject() }.getOrNull() ?: rejectRequest("The typed data is malformed")
    val domain = root["domain"]
    if (domain == null || domain is JsonNull) return null
    val domainObject = domain as? JsonObject ?: rejectRequest("The typed data domain is malformed")
    return evmChainIdOf(domainObject["chainId"])
}

/**
 * Fearless names Substrate networks by their full genesis hash, so a payload for any other genesis is for another
 * network. The CAIP-2 id a session uses is only the first half of it.
 */
internal fun isGenesisHashOf(payloadGenesisHash: ByteArray, chain: Chain): Boolean {
    val chainGenesisHash = runCatching { chain.id.fromHex() }.getOrNull()
    return chainGenesisHash?.size == GENESIS_HASH_SIZE && chainGenesisHash.contentEquals(payloadGenesisHash)
}
