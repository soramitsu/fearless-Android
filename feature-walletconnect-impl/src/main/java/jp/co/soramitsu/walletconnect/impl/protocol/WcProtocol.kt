package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

/** A protocol error to answer a dApp with: the codes the WalletConnect SDKs use. */
internal class WcProtocolException(val code: Long, message: String) : Exception(message)

/** Refuses a dApp's call with [code]. */
internal fun refuse(code: Long, message: String): Nothing = throw WcProtocolException(code, message)

internal object WcErrors {
    const val INVALID_METHOD = 1001L
    const val INVALID_EVENT = 1002L
    const val UNAUTHORIZED_METHOD = 3001L
    const val UNAUTHORIZED_UPDATE_REQUEST = 3003L
    const val UNAUTHORIZED_EXTEND_REQUEST = 3004L
    const val USER_REJECTED = 5000L
    const val UNSUPPORTED_CHAINS = 5100L
    const val UNSUPPORTED_METHODS = 5101L
    const val UNSUPPORTED_ACCOUNTS = 5103L
    const val UNSUPPORTED_NAMESPACE_KEY = 5104L
    const val USER_DISCONNECTED = 6000L
    const val SESSION_REQUEST_EXPIRED = 8000L
    const val WC_METHOD_UNSUPPORTED = 10_001L
    const val INVALID_REQUEST = -32_600L
    const val LIMIT_EXCEEDED = -32_005L
}

/** One side of a call: the relay tag and how long the relay keeps the message (seconds). */
internal class WcRpcOptions(val tag: Int, val ttlSeconds: Long, val prompt: Boolean = false)

/** Tags and TTLs per method, as the official SDKs publish them (the relay and push server read them). */
@Suppress("MagicNumber")
internal object WcRpcOptionsTable {
    private const val FIVE_MINUTES = 300L
    private const val ONE_DAY = 86_400L
    private const val THIRTY_SECONDS = 30L

    val proposeResponse = WcRpcOptions(1101, FIVE_MINUTES)
    val proposeReject = WcRpcOptions(1120, FIVE_MINUTES)
    val proposeAutoReject = WcRpcOptions(1121, FIVE_MINUTES)
    val settleRequest = WcRpcOptions(1102, FIVE_MINUTES)
    val updateRequest = WcRpcOptions(1104, ONE_DAY)
    val updateResponse = WcRpcOptions(1105, ONE_DAY)
    val extendRequest = WcRpcOptions(1106, ONE_DAY)
    val extendResponse = WcRpcOptions(1107, ONE_DAY)
    val sessionResponse = WcRpcOptions(1109, FIVE_MINUTES)
    val eventRequest = WcRpcOptions(1110, FIVE_MINUTES, prompt = true)
    val deleteRequest = WcRpcOptions(1112, ONE_DAY)
    val deleteResponse = WcRpcOptions(1113, ONE_DAY)
    val sessionPingRequest = WcRpcOptions(1114, ONE_DAY)
    val sessionPingResponse = WcRpcOptions(1115, ONE_DAY)
    val authenticateReject = WcRpcOptions(1118, FIVE_MINUTES)
    val pairingDeleteRequest = WcRpcOptions(1000, ONE_DAY)
    val pairingDeleteResponse = WcRpcOptions(1001, ONE_DAY)
    val pairingPingRequest = WcRpcOptions(1002, THIRTY_SECONDS)
    val pairingPingResponse = WcRpcOptions(1003, THIRTY_SECONDS)
    val unregistered = WcRpcOptions(0, ONE_DAY)

    const val PROPOSAL_TTL_SECONDS = FIVE_MINUTES
    const val REQUEST_TTL_SECONDS = FIVE_MINUTES
    const val SESSION_TTL_SECONDS = 7 * ONE_DAY
    const val PAIRING_INACTIVE_TTL_SECONDS = FIVE_MINUTES
    const val PAIRING_ACTIVE_TTL_SECONDS = 30 * ONE_DAY
    const val REQUEST_MAX_EXPIRY_SECONDS = 7 * ONE_DAY
}

internal object WcMethods {
    const val SESSION_PROPOSE = "wc_sessionPropose"
    const val SESSION_SETTLE = "wc_sessionSettle"
    const val SESSION_REQUEST = "wc_sessionRequest"
    const val SESSION_EVENT = "wc_sessionEvent"
    const val SESSION_UPDATE = "wc_sessionUpdate"
    const val SESSION_EXTEND = "wc_sessionExtend"
    const val SESSION_DELETE = "wc_sessionDelete"
    const val SESSION_PING = "wc_sessionPing"
    const val SESSION_AUTHENTICATE = "wc_sessionAuthenticate"
    const val PAIRING_DELETE = "wc_pairingDelete"
    const val PAIRING_PING = "wc_pairingPing"
}

/**
 * What the wallet accepts from a dApp: sizes, counts and the shape of CAIP ids. Anything outside
 * is refused before it reaches the user.
 */
internal object WcLimits {
    const val MAX_PAYLOAD_LENGTH = 1024 * 1024
    const val MAX_REQUEST_PARAMS_LENGTH = 256 * 1024
    const val MAX_NAMESPACES = 16
    const val MAX_CHAINS_PER_NAMESPACE = 256
    const val MAX_METHODS_PER_NAMESPACE = 128
    const val MAX_EVENTS_PER_NAMESPACE = 64
    const val MAX_NAME_LENGTH = 128
    const val MAX_NAME_DISPLAY_LENGTH = 256
    const val MAX_DESCRIPTION_LENGTH = 1024
    const val MAX_URL_LENGTH = 2048
    const val MAX_ICONS = 8
    const val MAX_PENDING_PROPOSALS = 10
    const val MAX_PENDING_REQUESTS_PER_SESSION = 20
    const val MAX_PAIRINGS = 64
    const val MAX_SESSIONS = 64
    const val MAX_HANDLED_ENTRIES = 2048

    private val CAIP2 = Regex("[-a-z0-9]{3,8}:[-_a-zA-Z0-9]{1,32}")
    private val CAIP10_ADDRESS = Regex("[-.%a-zA-Z0-9]{1,128}")
    private val NAMESPACE = Regex("[-a-z0-9]{3,8}")
    private val NAME = Regex("[\\x21-\\x7e]{1,$MAX_NAME_LENGTH}")

    fun isCaip2(value: String): Boolean = CAIP2.matches(value)

    fun isNamespaceKey(value: String): Boolean = NAMESPACE.matches(value)

    /** `namespace:reference:address` on a valid CAIP-2 chain. */
    fun isCaip10(value: String): Boolean {
        val chain = value.substringBeforeLast(':', missingDelimiterValue = "")
        return isCaip2(chain) && CAIP10_ADDRESS.matches(value.substringAfterLast(':'))
    }

    /** Method and event names: printable ASCII without spaces. */
    fun isName(value: String): Boolean = NAME.matches(value)

    /** Display text without control characters, cut to [maxLength]. */
    fun displayText(value: String, maxLength: Int): String = value.filterNot { it.isISOControl() }.take(maxLength)
}

internal fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal fun JsonElement?.stringList(maxSize: Int): List<String>? {
    if (this == null || this is JsonNull) return emptyList()
    val array = (this as? JsonArray)?.takeIf { it.size <= maxSize } ?: return null
    val strings = array.mapNotNull { (it as? JsonPrimitive)?.takeIf { item -> item.isString }?.content }
    return strings.takeIf { it.size == array.size }
}
