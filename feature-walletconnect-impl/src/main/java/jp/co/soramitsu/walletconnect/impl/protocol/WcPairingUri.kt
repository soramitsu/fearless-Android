package jp.co.soramitsu.walletconnect.impl.protocol

import co.jp.soramitsu.walletconnect.model.WalletConnectPairingExpiredException
import co.jp.soramitsu.walletconnect.model.WalletConnectUriException
import java.net.URLDecoder

/** A WalletConnect v2 pairing URI: `wc:{topic}@2?relay-protocol=irn&symKey={key}&expiryTimestamp={seconds}`. */
internal class WcPairingUri(
    val topic: String,
    val symKey: ByteArray,
    val relayProtocol: String,
    val relayData: String?,
    val expiryTimestamp: Long?
) {
    companion object {
        const val RELAY_PROTOCOL = "irn"
        private const val MAX_URI_LENGTH = 2048
        private const val MAX_RELAY_DATA_LENGTH = 512
        private const val VERSION_2 = "2"
        private val SCHEMES = listOf("wc://", "wc:")

        /**
         * Reads a pairing URI. A v1 URI (`…@1?bridge=…&key=…`) or any malformed one throws
         * [WalletConnectUriException]; a v2 URI whose expiry has passed throws
         * [WalletConnectPairingExpiredException].
         */
        fun parse(uri: String, nowSeconds: Long): WcPairingUri {
            val text = uri.trim()
            if (text.length > MAX_URI_LENGTH) malformed("The link is too long")
            val scheme = SCHEMES.firstOrNull { text.startsWith(it, ignoreCase = true) } ?: malformed("Not a WalletConnect link")
            val body = text.substring(scheme.length)
            val queryStart = body.indexOf('?')
            val path = if (queryStart >= 0) body.substring(0, queryStart) else body
            val query = if (queryStart >= 0) body.substring(queryStart + 1) else ""
            val at = path.lastIndexOf('@')
            if (at <= 0) malformed("Not a WalletConnect link")
            val topic = path.substring(0, at).removePrefix("//")
            if (path.substring(at + 1) != VERSION_2) malformed("Only WalletConnect v2 links are supported")
            if (!WcEncoding.isHex(topic, WcCrypto.KEY_LENGTH)) malformed("The link has no valid topic")

            val params = parseQuery(query)
            val symKeyHex = params["symKey"]?.takeIf { WcEncoding.isHex(it, WcCrypto.KEY_LENGTH) } ?: malformed("The link has no valid key")
            val relayProtocol = params["relay-protocol"] ?: RELAY_PROTOCOL
            if (relayProtocol != RELAY_PROTOCOL) malformed("Unsupported relay protocol")
            val relayData = params["relay-data"]
            if (relayData != null && relayData.length > MAX_RELAY_DATA_LENGTH) malformed("Invalid relay data")
            val expiryTimestamp = params["expiryTimestamp"]?.let { it.toLongOrNull()?.takeIf { value -> value > 0 } ?: malformed("Invalid expiry") }
            if (expiryTimestamp != null && expiryTimestamp <= nowSeconds) expired()

            return WcPairingUri(topic, WcEncoding.unhex(symKeyHex), relayProtocol, relayData, expiryTimestamp)
        }

        private fun malformed(message: String): Nothing = throw WalletConnectUriException(message)

        private fun expired(): Nothing = throw WalletConnectPairingExpiredException()

        private fun parseQuery(query: String): Map<String, String> {
            val params = LinkedHashMap<String, String>()
            if (query.isEmpty()) return params
            for (part in query.split('&')) {
                if (part.isEmpty()) continue
                val separator = part.indexOf('=')
                val key = decode(if (separator >= 0) part.substring(0, separator) else part)
                val value = decode(if (separator >= 0) part.substring(separator + 1) else "")
                // A repeated parameter is ambiguous: refuse rather than pick one.
                if (params.put(key, value) != null) malformed("Repeated link parameter")
            }
            return params
        }

        private fun decode(text: String): String = try {
            URLDecoder.decode(text, Charsets.UTF_8.name())
        } catch (error: IllegalArgumentException) {
            throw WalletConnectUriException("Malformed link encoding").apply { initCause(error) }
        }
    }
}
