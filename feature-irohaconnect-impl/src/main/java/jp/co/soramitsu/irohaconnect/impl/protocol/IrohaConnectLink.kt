// Field sizes of an Iroha Connect invitation.
@file:Suppress("MagicNumber")

package jp.co.soramitsu.irohaconnect.impl.protocol

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Stable failure codes; only the code reaches the UI, which translates it. */
enum class IrohaConnectErrorCode {
    INVALID_LINK,
    RETIRED_LINK,
    UNKNOWN_NETWORK,
    NETWORK_UNAVAILABLE,
    WRONG_NODE,
    INVALID_FRAME
}

class IrohaConnectException(val code: IrohaConnectErrorCode, val detail: String? = null) :
    Exception(detail?.let { "${code.name}: $it" } ?: code.name)

internal fun invalidLink(detail: String): Nothing =
    throw IrohaConnectException(IrohaConnectErrorCode.INVALID_LINK, detail)

internal fun invalidFrame(detail: String): Nothing =
    throw IrohaConnectException(IrohaConnectErrorCode.INVALID_FRAME, detail)

/** A wallet invitation a dApp registered with Torii (`POST /v1/connect/session`), checked field by field. */
internal class IrohaConnectLink(
    val scheme: String,
    val profile: IrohaConnectProfile,
    val networkId: IrohaNetworkId,
    val sid: String,
    val sidBytes: ByteArray,
    val appPublicKey: ByteArray,
    val nonce: ByteArray,
    val token: String,
    val relayToken: String
) {
    val node: String get() = profile.toriiBaseUrl
    val networkIdBytes: ByteArray get() = networkId.toBytes()

    override fun toString(): String = "IrohaConnectLink(${profile.network}, sid=$sid)"

    companion object {
        const val MAX_LINK_BYTES = 4096
        private val SCHEME = Regex("^(iroha|irohaconnect)://connect\\?")
        private val FIELDS = listOf("sid", "network_id", "app_pk", "nonce", "node", "v", "role", "token", "relay")
        private val LEGACY_FIELDS = setOf("chain_id")
        private val SID_PREFIX = "iroha-connect|sid|".toByteArray(Charsets.US_ASCII)

        /** blake2b-256("iroha-connect|sid|" || NetworkId || app_pk || nonce): the sid binds the network and app key. */
        fun deriveSessionId(
            networkIdBytes: ByteArray,
            appPublicKey: ByteArray,
            nonce: ByteArray
        ): ByteArray =
            IrohaConnectCrypto.blake2b256(NoritoBytes.concat(SID_PREFIX, networkIdBytes, appPublicKey, nonce))

        @Suppress("CyclomaticComplexMethod", "ThrowsCount") // Each check mirrors one of wire.ts parseIrohaConnectUri's.
        fun parse(input: String): IrohaConnectLink {
            if (input.isEmpty() || input != input.trim()) invalidLink("link must be non-empty without surrounding whitespace")
            if (input.toByteArray(Charsets.UTF_8).size > MAX_LINK_BYTES) invalidLink("link is too long")
            // Stricter than a WHATWG URL parser, which silently drops tabs and newlines: none may appear at all.
            val hasControl = input.any { it.code <= 0x20 || it.code in 0x7f..0x9f }
            if (hasControl) invalidLink("link contains whitespace or control characters")
            val scheme = SCHEME.find(input)?.groupValues?.get(1) ?: invalidLink("link must use iroha://connect or irohaconnect://connect")
            val query = input.substring(scheme.length + "://connect?".length)
            if ('#' in query) invalidLink("link must not carry a fragment")

            val values = LinkedHashMap<String, String>()
            query.split('&').filter(String::isNotEmpty).forEach { pair ->
                val separator = pair.indexOf('=')
                val name = decodeComponent(if (separator < 0) pair else pair.substring(0, separator))
                val value = if (separator < 0) "" else decodeComponent(pair.substring(separator + 1))
                if (name in LEGACY_FIELDS) throw IrohaConnectException(IrohaConnectErrorCode.RETIRED_LINK)
                if (name !in FIELDS) invalidLink("unexpected link field $name")
                if (name in values) invalidLink("duplicate link field $name")
                if (value.isEmpty()) invalidLink("empty link field $name")
                values[name] = value
            }
            FIELDS.forEach { if (it !in values) invalidLink("link is missing $it") }
            if (values["v"] != "1") invalidLink("v must be exactly 1")
            if (values["role"] != "wallet") invalidLink("role must be exactly wallet")

            val networkId = IrohaNetworkId.parse(values.getValue("network_id"))
                ?: invalidLink("network_id is not a canonical checksummed NetworkId")
            val node = values.getValue("node")
            val profile = IrohaConnectProfiles.forNetworkId(networkId)
            if (profile == null) {
                val nexus = IrohaConnectProfiles.nexus
                if (nexus.networkId == null && node.trimEnd('/') == nexus.toriiBaseUrl) {
                    throw IrohaConnectException(IrohaConnectErrorCode.NETWORK_UNAVAILABLE)
                }
                throw IrohaConnectException(IrohaConnectErrorCode.UNKNOWN_NETWORK)
            }
            if (node != profile.toriiBaseUrl) throw IrohaConnectException(IrohaConnectErrorCode.WRONG_NODE)

            val sid = values.getValue("sid")
            val sidBytes = canonicalBytes(sid, "sid", 32)
            val appPublicKey = canonicalBytes(values.getValue("app_pk"), "app_pk", 32)
            val nonce = canonicalBytes(values.getValue("nonce"), "nonce", 16)
            val token = values.getValue("token")
            val relayToken = values.getValue("relay")
            canonicalBytes(token, "token", 32)
            canonicalBytes(relayToken, "relay", 32)
            if (!NoritoBytes.constantTimeEquals(deriveSessionId(networkId.toBytes(), appPublicKey, nonce), sidBytes)) {
                invalidLink("sid is not bound to network_id, app_pk, and nonce")
            }
            return IrohaConnectLink(scheme, profile, networkId, sid, sidBytes, appPublicKey, nonce, token, relayToken)
        }

        private fun canonicalBytes(
            value: String,
            label: String,
            length: Int
        ): ByteArray {
            val decoded = IrohaConnectEncoding.fromCanonicalBase64Url(value)
                ?: invalidLink("$label must be canonical unpadded base64url")
            if (decoded.size != length) invalidLink("$label must be $length bytes")
            if (decoded.all { it.toInt() == 0 }) invalidLink("$label must not be all zeroes")
            return decoded
        }

        @Suppress("NestedBlockDepth", "CommentOverPrivateFunction")
        /** `application/x-www-form-urlencoded` decoding, refusing malformed escapes and invalid UTF-8. */
        private fun decodeComponent(value: String): String {
            val output = ByteArrayOutputStream(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                when {
                    character == '+' -> output.write(' '.code)
                    character == '%' -> {
                        if (index + 2 >= value.length) invalidLink("link has a malformed escape")
                        val hex = value.substring(index + 1, index + 3)
                        if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                            invalidLink("link has a malformed escape")
                        }
                        output.write(hex.toInt(16))
                        index += 2
                    }
                    character.code < 0x80 -> output.write(character.code)
                    else -> output.write(character.toString().toByteArray(Charsets.UTF_8))
                }
                index += 1
            }
            return try {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(output.toByteArray()))
                    .toString()
            } catch (_: CharacterCodingException) {
                invalidLink("link is not UTF-8")
            }
        }
    }
}

/** The relay endpoint and the token subprotocol a wallet socket must offer. */
internal object IrohaConnectRelay {
    private const val TOKEN_PROTOCOL_PREFIX = "iroha-connect.token.v1."

    fun tokenProtocol(token: String): String =
        TOKEN_PROTOCOL_PREFIX + IrohaConnectEncoding.toBase64Url(token.toByteArray(Charsets.UTF_8))

    fun webSocketUrl(link: IrohaConnectLink): String {
        val node = link.node
        require(node.startsWith("https://")) { "IrohaConnect relays must use HTTPS Torii roots" }
        return "wss://" + node.removePrefix("https://").trimEnd('/') + "/v1/connect/ws?sid=${link.sid}&role=wallet"
    }
}
