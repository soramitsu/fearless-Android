package jp.co.soramitsu.tonconnect.api.model

import java.net.URLDecoder

/**
 * A dApp's TON Connect connect link (protocol v2): what its QR code shows and what it opens on the same device.
 * It has two forms, `tc://?v=2&id=…&r=…` and Fearless's universal link from the TON Connect wallets list,
 * `https://fearlesswallet.io/ton-connect/?v=2&id=…&r=…`.
 *
 * [clientId] is the dApp's bridge client id (its session public key, 64 hex characters) and [request] the
 * ConnectRequest JSON from `r`. The optional `ret` return strategy is ignored: the wallet does not switch back
 * to the dApp's app after it replies.
 */
data class TonConnectLink(
    val clientId: String,
    val request: ConnectRequest
) {
    companion object {
        const val UNIVERSAL_LINK = "https://fearlesswallet.io/ton-connect/"
        const val TC_LINK = "tc://"

        private const val TC_SCHEME_PREFIX = "tc:"
        private const val UNIVERSAL_LINK_PREFIX = "https://fearlesswallet.io/ton-connect"
        private const val PROTOCOL_VERSION = "2"
        private const val PARAM_VERSION = "v"
        private const val PARAM_CLIENT_ID = "id"
        private const val PARAM_REQUEST = "r"
        private const val UNKNOWN_VERSION = -1
        private val clientIdRegex = Regex("^[0-9a-fA-F]{64}$")

        /**
         * Whether [content] is meant as a TON Connect link, so that a scanner or a link handler should report it
         * as an invalid TON Connect link rather than read it as something else. [parse] decides if it is valid.
         */
        fun isCandidate(content: String): Boolean {
            val value = content.trim()
            return value.startsWith(TC_SCHEME_PREFIX, ignoreCase = true) ||
                value.startsWith(UNIVERSAL_LINK_PREFIX, ignoreCase = true)
        }

        /**
         * Parses a connect link strictly: [TC_LINK] or [UNIVERSAL_LINK] followed by the query, protocol version 2,
         * one hex client id and one ConnectRequest naming at least one item. Other parameters are ignored.
         *
         * @throws TonConnectException for any other content.
         */
        fun parse(content: String): TonConnectLink {
            val params = linkParams(content.trim())
            requireProtocolVersion(params.onlyValueOrNullIfAbsent(PARAM_VERSION))
            val clientId = requireClientId(params.onlyValueOrNullIfAbsent(PARAM_CLIENT_ID))
            val request = requireConnectRequest(params.onlyValueOrNullIfAbsent(PARAM_REQUEST))
            return TonConnectLink(clientId, request)
        }

        private fun linkParams(link: String): Map<String, List<String>> {
            val queryStart = link.indexOf('?')
            if (queryStart < 0 || !isLinkBase(link.substring(0, queryStart))) {
                throw TonConnectException.RequestParsingError(null)
            }
            return queryParams(link.substring(queryStart + 1).substringBefore('#'))
        }

        // Scheme and host compare case-insensitively, as URIs do; nothing else may come before the query.
        private fun isLinkBase(base: String): Boolean {
            return base.equals(TC_LINK, ignoreCase = true) || base.equals(UNIVERSAL_LINK, ignoreCase = true)
        }

        private fun requireProtocolVersion(version: String?) {
            if (version != PROTOCOL_VERSION) {
                throw TonConnectException.UnsupportedVersion(version?.toIntOrNull() ?: UNKNOWN_VERSION)
            }
        }

        private fun requireClientId(clientId: String?): String {
            if (clientId == null || !clientIdRegex.matches(clientId)) {
                throw TonConnectException.WrongClientId(clientId)
            }
            return clientId
        }

        private fun requireConnectRequest(json: String?): ConnectRequest {
            val request = ConnectRequest.parse(json)
            if (request.items.isEmpty()) {
                throw TonConnectException.RequestParsingError(null)
            }
            return request
        }

        private fun queryParams(query: String): Map<String, List<String>> {
            return query.split('&')
                .filter { it.isNotEmpty() }
                .map { pair ->
                    val name = pair.substringBefore('=')
                    val value = pair.substringAfter('=', missingDelimiterValue = "")
                    decode(name) to decode(value)
                }
                .groupBy({ it.first }, { it.second })
        }

        // dApps build the query with URLSearchParams (application/x-www-form-urlencoded), so `+` is a space.
        private fun decode(value: String): String {
            return try {
                URLDecoder.decode(value, Charsets.UTF_8.name())
            } catch (malformed: IllegalArgumentException) {
                throw TonConnectException.RequestParsingError(null).apply { initCause(malformed) }
            }
        }

        private fun Map<String, List<String>>.onlyValueOrNullIfAbsent(name: String): String? {
            val values = get(name) ?: return null
            // A parameter given twice is ambiguous.
            if (values.size != 1) throw TonConnectException.RequestParsingError(null)
            return values.single()
        }
    }
}
