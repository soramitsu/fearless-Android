package jp.co.soramitsu.irohaconnect.impl.review

/** Why a `SignRequestTx` was refused; mirrors the browser wallet's `IrohaConnectTransactionErrorCode`. */
enum class IrohaConnectTransactionErrorCode(val resultCode: String) {
    UNSUPPORTED_TRANSACTION("UNSUPPORTED_TRANSACTION"),
    INVALID_TRANSACTION("INVALID_TRANSACTION"),
    WRONG_NETWORK("WRONG_NETWORK"),
    WRONG_AUTHORITY("WRONG_ACCOUNT")
}

class IrohaConnectTransactionException(val code: IrohaConnectTransactionErrorCode, val detail: String) :
    Exception("${code.name}: $detail")

data class IrohaConnectReviewAsset(val definitionId: String, val symbol: String? = null)

data class IrohaConnectReviewFeeLimit(
    /** `nexus` or `pipelineGas`. */
    val kind: String,
    val asset: IrohaConnectReviewAsset,
    val maxAmount: String
)

data class IrohaConnectReviewFee(
    /** `authority` or `sponsor`. */
    val payer: String,
    val program: String? = null,
    val programRevision: String? = null,
    val chargeLimits: List<IrohaConnectReviewFeeLimit>,
    val gasLimit: String?
)

data class IrohaConnectReviewTransfer(
    val asset: IrohaConnectReviewAsset,
    val scope: String?,
    val quantity: String,
    val destination: String
)

data class IrohaConnectReviewContractCall(
    val contractAddress: String,
    val entrypoint: String,
    val codeHashHex: String,
    val argumentBytes: Int?,
    val argumentsSha256: String?
)

/** Every signature-bound field of one reviewed payload, decoded for display. */
data class IrohaConnectTransactionReview(
    val kind: Kind,
    val authority: String,
    val networkId: String,
    val payloadBytes: Int,
    val payloadHashHex: String,
    val creationTimeMs: String,
    val ttlMs: String?,
    val nonce: String?,
    val fee: IrohaConnectReviewFee,
    /** Metadata JSON with control and bidirectional characters escaped, or null when empty. */
    val metadata: String?,
    val transfer: IrohaConnectReviewTransfer? = null,
    val calls: List<IrohaConnectReviewContractCall>? = null
) {
    enum class Kind(val wireName: String) { TRANSFER("transfer"), CONTRACT_CALLS("contract-calls") }
}

class ReviewedIrohaConnectTransaction(
    val review: IrohaConnectTransactionReview,
    /** The marked Blake2b-256 payload hash the account key signs. */
    val payloadHash: ByteArray
)

data class IrohaConnectTransactionExpectation(
    val networkIdLiteral: String,
    val networkPrefix: Int,
    val authority: String,
    val signingPublicKeyHex: String
)
