package jp.co.soramitsu.irohaconnect.impl.domain

import jp.co.soramitsu.irohaconnect.impl.review.IrohaConnectTransactionReview

enum class IrohaConnectPhase { IDLE, CONNECTING, SESSION_APPROVAL, CONNECTED, REQUEST_APPROVAL, ERROR }

/** One locally signable Iroha account the dApp may be connected to. */
data class IrohaConnectAccount(
    val metaId: Long,
    val address: String,
    val name: String,
    val publicKeyHex: String
)

data class IrohaConnectSessionPermissions(val methods: List<String>, val events: List<String>, val resources: List<String>?)

data class IrohaConnectSessionInfo(
    val appName: String,
    /** The dApp's HTTPS origin (or loopback HTTP), or null when it sent none or an unsafe one. */
    val appUrl: String?,
    val chainId: String,
    val networkName: String,
    val networkId: String,
    /** What the dApp asked to use, or null when its Open frame declared no restriction. */
    val permissions: IrohaConnectSessionPermissions?,
    val toriiBaseUrl: String,
    val connectedAt: Long? = null
)

data class IrohaConnectSigningRequest(
    val requestId: String,
    val accountId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val review: IrohaConnectTransactionReview
)

/** Why the wallet answered a dApp request on its own, without asking. */
enum class IrohaConnectNoticeCode {
    RAW_SIGNING_UNAVAILABLE,
    TRANSACTION_UNSUPPORTED,
    TRANSACTION_INVALID,
    TRANSACTION_WRONG_ACCOUNT,
    PERMISSION_DENIED,
    REQUEST_IN_PROGRESS,
    REQUEST_EXPIRED,
    DAPP_CLOSED
}

data class IrohaConnectNotice(val at: Long, val code: IrohaConnectNoticeCode)

/** Why a session ended in the error phase. */
enum class IrohaConnectFailure {
    INVALID_LINK,
    RETIRED_LINK,
    UNKNOWN_NETWORK,
    NETWORK_UNAVAILABLE,
    WRONG_NODE,
    RELAY_UNREACHABLE,
    OPEN_TIMEOUT,
    SESSION_ENDED,
    PROTOCOL_ERROR,
    KEEPALIVE_FAILED
}

data class IrohaConnectSnapshot(
    val phase: IrohaConnectPhase = IrohaConnectPhase.IDLE,
    val accounts: List<IrohaConnectAccount> = emptyList(),
    val session: IrohaConnectSessionInfo? = null,
    val request: IrohaConnectSigningRequest? = null,
    val selectedAccountId: String? = null,
    val failure: IrohaConnectFailure? = null,
    val notice: IrohaConnectNotice? = null
) {
    val isActive: Boolean get() = phase != IrohaConnectPhase.IDLE && phase != IrohaConnectPhase.ERROR
}

/** Why a person's action could not be carried out; the session itself stays as it was unless stated. */
enum class IrohaConnectActionError {
    NOT_AVAILABLE,
    ACCOUNT_UNAVAILABLE,
    WALLET_LOCKED,
    CANNOT_SIGN,
    KEY_MISMATCH,
    REQUEST_EXPIRED,
    RELAY_DISCONNECTED
}

class IrohaConnectActionException(val error: IrohaConnectActionError, cause: Throwable? = null) :
    Exception(error.name, cause)

/** The signature of one message by an account's Iroha key. */
class IrohaConnectSignature(val publicKeyHex: String, val signature: ByteArray)

/** Lists and signs with the wallet's locally held Iroha keys. */
interface IrohaConnectAccountProvider {
    suspend fun accounts(networkPrefix: Int): List<IrohaConnectAccount>

    /** Signs [message] with the account's Ed25519 key; throws [IrohaConnectActionException]. */
    suspend fun sign(
        account: IrohaConnectAccount,
        networkPrefix: Int,
        message: ByteArray
    ): IrohaConnectSignature
}

/** One relay WebSocket. Only binary frames are ever sent: Torii closes a Connect socket on any text frame. */
interface IrohaConnectSocket {
    /** Returns false when the socket can no longer send. */
    fun send(frame: ByteArray): Boolean

    fun close(code: Int, reason: String)
}

interface IrohaConnectSocketListener {
    fun onOpen()
    fun onBinary(bytes: ByteArray)
    fun onText()
    fun onFailure(error: Throwable)
    fun onClosed(code: Int)
}

fun interface IrohaConnectSocketFactory {
    fun open(
        url: String,
        protocol: String,
        listener: IrohaConnectSocketListener
    ): IrohaConnectSocket
}
