package jp.co.soramitsu.common.model

import com.google.gson.annotations.SerializedName

data class UniversalWalletSigningRequest(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("origin")
    val origin: String,
    @SerializedName("method")
    val method: UniversalWalletSigningMethod,
    @SerializedName("message")
    val message: UniversalWalletSigningPayload? = null,
    @SerializedName("transactionBase64")
    val transactionBase64: String? = null,
    @SerializedName("transactionsBase64")
    val transactionsBase64: List<String> = emptyList(),
    @SerializedName("createdAtMillis")
    val createdAtMillis: Long,
    @SerializedName("expiresAtMillis")
    val expiresAtMillis: Long? = null
) {
    fun validationErrors(): Set<UniversalWalletSigningValidationError> {
        val errors = linkedSetOf<UniversalWalletSigningValidationError>()

        UniversalWalletSigningContractValidator.validateEnvelope(
            requestId = requestId,
            accountId = accountId,
            ecosystem = ecosystem,
            chainId = chainId,
            origin = origin,
            createdAtMillis = createdAtMillis,
            expiresAtMillis = expiresAtMillis,
            errors = errors
        )

        when (method) {
            UniversalWalletSigningMethod.SignMessage -> {
                if (message?.validationErrors()?.also(errors::addAll).isNullOrEmpty()) {
                    if (message == null) {
                        errors += UniversalWalletSigningValidationError.MessageRequired
                    }
                }
                if (transactionBase64 != null || transactionsBase64.isNotEmpty()) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
            UniversalWalletSigningMethod.SignTransaction,
            UniversalWalletSigningMethod.SignAndSendTransaction -> {
                if (!UniversalWalletSigningContractValidator.isBase64Payload(transactionBase64, maxLength = UniversalWalletSigningContractValidator.MAX_TRANSACTION_BASE64_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.TransactionRequired
                }
                if (message != null || transactionsBase64.isNotEmpty()) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
            UniversalWalletSigningMethod.SignAllTransactions -> {
                if (transactionsBase64.isEmpty() || transactionsBase64.size > UniversalWalletSigningContractValidator.MAX_TRANSACTION_BATCH) {
                    errors += UniversalWalletSigningValidationError.InvalidBatch
                }
                if (transactionsBase64.any { !UniversalWalletSigningContractValidator.isBase64Payload(it, maxLength = UniversalWalletSigningContractValidator.MAX_TRANSACTION_BASE64_LENGTH) }) {
                    errors += UniversalWalletSigningValidationError.InvalidBase64
                }
                if (message != null || transactionBase64 != null) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
        }

        return errors
    }
}

data class UniversalWalletSigningPayload(
    @SerializedName("encoding")
    val encoding: UniversalWalletSigningPayloadEncoding,
    @SerializedName("value")
    val value: String,
    @SerializedName("display")
    val display: UniversalWalletSigningDisplay = UniversalWalletSigningDisplay.Raw
) {
    fun validationErrors(): Set<UniversalWalletSigningValidationError> {
        val errors = linkedSetOf<UniversalWalletSigningValidationError>()

        when (encoding) {
            UniversalWalletSigningPayloadEncoding.Base64 -> {
                if (!UniversalWalletSigningContractValidator.isBase64Payload(value, maxLength = UniversalWalletSigningContractValidator.MAX_MESSAGE_BASE64_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.InvalidBase64
                }
            }
            UniversalWalletSigningPayloadEncoding.Hex -> {
                if (!UniversalWalletSigningContractValidator.isHexPayload(value, maxLength = UniversalWalletSigningContractValidator.MAX_MESSAGE_HEX_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.InvalidHex
                }
            }
            UniversalWalletSigningPayloadEncoding.Utf8 -> {
                if (!UniversalWalletSigningContractValidator.isHumanText(value, maxLength = UniversalWalletSigningContractValidator.MAX_MESSAGE_TEXT_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.InvalidMessage
                }
            }
        }

        return errors
    }
}

data class UniversalWalletSigningResult(
    @SerializedName("requestId")
    val requestId: String,
    @SerializedName("accountId")
    val accountId: String,
    @SerializedName("ecosystem")
    val ecosystem: String,
    @SerializedName("chainId")
    val chainId: String,
    @SerializedName("method")
    val method: UniversalWalletSigningMethod,
    @SerializedName("status")
    val status: UniversalWalletSigningResultStatus,
    @SerializedName("publicKey")
    val publicKey: String? = null,
    @SerializedName("signatureHex")
    val signatureHex: String? = null,
    @SerializedName("signatureBase64")
    val signatureBase64: String? = null,
    @SerializedName("signatureBase58")
    val signatureBase58: String? = null,
    @SerializedName("signedTransactionBase64")
    val signedTransactionBase64: String? = null,
    @SerializedName("signedTransactionsBase64")
    val signedTransactionsBase64: List<String> = emptyList(),
    @SerializedName("transactionHash")
    val transactionHash: String? = null,
    @SerializedName("errorCode")
    val errorCode: String? = null,
    @SerializedName("signedAtMillis")
    val signedAtMillis: Long
) {
    fun validationErrors(): Set<UniversalWalletSigningValidationError> {
        val errors = linkedSetOf<UniversalWalletSigningValidationError>()

        UniversalWalletSigningContractValidator.validateEnvelope(
            requestId = requestId,
            accountId = accountId,
            ecosystem = ecosystem,
            chainId = chainId,
            origin = "result",
            createdAtMillis = signedAtMillis,
            expiresAtMillis = null,
            errors = errors
        )

        if (status == UniversalWalletSigningResultStatus.Approved) {
            if (publicKey != null && !UniversalWalletSigningContractValidator.isMachineText(publicKey, maxLength = 256)) {
                errors += UniversalWalletSigningValidationError.InvalidPublicKey
            }
            if (publicKey == null) {
                errors += UniversalWalletSigningValidationError.PublicKeyRequired
            }

            validateApprovedPayload(errors)
            if (errorCode != null) {
                errors += UniversalWalletSigningValidationError.ErrorNotAllowed
            }
        } else {
            if (errorCode.isNullOrBlank() || !UniversalWalletSigningContractValidator.ERROR_CODE.matches(errorCode)) {
                errors += UniversalWalletSigningValidationError.ErrorCodeRequired
            }
            if (publicKey != null || signatureHex != null || signatureBase64 != null || signatureBase58 != null ||
                signedTransactionBase64 != null || signedTransactionsBase64.isNotEmpty() || transactionHash != null
            ) {
                errors += UniversalWalletSigningValidationError.SignatureNotAllowed
            }
        }

        return errors
    }

    private fun validateApprovedPayload(errors: MutableSet<UniversalWalletSigningValidationError>) {
        val hasSignature = signatureHex != null || signatureBase64 != null || signatureBase58 != null
        if (signatureHex != null && !UniversalWalletSigningContractValidator.SIGNATURE_HEX.matches(signatureHex)) {
            errors += UniversalWalletSigningValidationError.InvalidSignature
        }
        if (signatureBase64 != null && !UniversalWalletSigningContractValidator.isBase64Payload(signatureBase64, maxLength = 512)) {
            errors += UniversalWalletSigningValidationError.InvalidSignature
        }
        if (signatureBase58 != null && !UniversalWalletSigningContractValidator.BASE58_SIGNATURE.matches(signatureBase58)) {
            errors += UniversalWalletSigningValidationError.InvalidSignature
        }

        when (method) {
            UniversalWalletSigningMethod.SignMessage -> {
                if (!hasSignature) {
                    errors += UniversalWalletSigningValidationError.SignatureRequired
                }
                if (signedTransactionBase64 != null || signedTransactionsBase64.isNotEmpty() || transactionHash != null) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
            UniversalWalletSigningMethod.SignTransaction -> {
                if (!UniversalWalletSigningContractValidator.isBase64Payload(signedTransactionBase64, maxLength = UniversalWalletSigningContractValidator.MAX_TRANSACTION_BASE64_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.SignedTransactionRequired
                }
                if (signedTransactionsBase64.isNotEmpty() || transactionHash != null) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
            UniversalWalletSigningMethod.SignAndSendTransaction -> {
                if (!UniversalWalletSigningContractValidator.isBase64Payload(signedTransactionBase64, maxLength = UniversalWalletSigningContractValidator.MAX_TRANSACTION_BASE64_LENGTH)) {
                    errors += UniversalWalletSigningValidationError.SignedTransactionRequired
                }
                if (!UniversalWalletSigningContractValidator.isMachineText(transactionHash.orEmpty(), maxLength = 256)) {
                    errors += UniversalWalletSigningValidationError.TransactionHashRequired
                }
                if (signedTransactionsBase64.isNotEmpty()) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
            UniversalWalletSigningMethod.SignAllTransactions -> {
                if (signedTransactionsBase64.isEmpty() || signedTransactionsBase64.size > UniversalWalletSigningContractValidator.MAX_TRANSACTION_BATCH) {
                    errors += UniversalWalletSigningValidationError.SignedTransactionRequired
                }
                if (signedTransactionsBase64.any { !UniversalWalletSigningContractValidator.isBase64Payload(it, maxLength = UniversalWalletSigningContractValidator.MAX_TRANSACTION_BASE64_LENGTH) }) {
                    errors += UniversalWalletSigningValidationError.InvalidBase64
                }
                if (signedTransactionBase64 != null || transactionHash != null) {
                    errors += UniversalWalletSigningValidationError.PayloadNotAllowed
                }
            }
        }
    }
}

enum class UniversalWalletSigningMethod {
    @SerializedName("sign-message")
    SignMessage,

    @SerializedName("sign-transaction")
    SignTransaction,

    @SerializedName("sign-and-send-transaction")
    SignAndSendTransaction,

    @SerializedName("sign-all-transactions")
    SignAllTransactions
}

enum class UniversalWalletSigningPayloadEncoding {
    @SerializedName("base64")
    Base64,

    @SerializedName("hex")
    Hex,

    @SerializedName("utf8")
    Utf8
}

enum class UniversalWalletSigningDisplay {
    @SerializedName("raw")
    Raw,

    @SerializedName("utf8")
    Utf8,

    @SerializedName("hex")
    Hex
}

enum class UniversalWalletSigningResultStatus {
    @SerializedName("approved")
    Approved,

    @SerializedName("rejected")
    Rejected,

    @SerializedName("failed")
    Failed
}

enum class UniversalWalletSigningValidationError {
    InvalidRequestId,
    InvalidAccountId,
    InvalidEcosystem,
    InvalidChainId,
    InvalidOrigin,
    InvalidTimestamp,
    MessageRequired,
    TransactionRequired,
    InvalidBatch,
    InvalidBase64,
    InvalidHex,
    InvalidMessage,
    PayloadNotAllowed,
    PublicKeyRequired,
    InvalidPublicKey,
    SignatureRequired,
    InvalidSignature,
    SignedTransactionRequired,
    TransactionHashRequired,
    ErrorCodeRequired,
    ErrorNotAllowed,
    SignatureNotAllowed
}

object UniversalWalletSigningContractValidator {
    const val MAX_MESSAGE_TEXT_LENGTH = 64 * 1024
    const val MAX_MESSAGE_BASE64_LENGTH = 88 * 1024
    const val MAX_MESSAGE_HEX_LENGTH = 128 * 1024
    const val MAX_TRANSACTION_BASE64_LENGTH = 352 * 1024
    const val MAX_TRANSACTION_BATCH = 16

    val REQUEST_ID = Regex("^sign_[A-Za-z0-9_-]{16,64}$")
    val ACCOUNT_ID = Regex("^[a-z0-9][a-z0-9._:-]{1,63}$")
    val CHAIN_ID = Regex("^[A-Za-z0-9._:-]{2,128}$")
    val BASE64 = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
    val HEX = Regex("^(?:[0-9a-f]{2})+$")
    val SIGNATURE_HEX = Regex("^[0-9a-f]{64,260}$")
    val BASE58_SIGNATURE = Regex("^[1-9A-HJ-NP-Za-km-z]{64,128}$")
    val ERROR_CODE = Regex("^[a-z][a-z0-9_:-]{1,63}$")

    fun validateEnvelope(
        requestId: String,
        accountId: String,
        ecosystem: String,
        chainId: String,
        origin: String,
        createdAtMillis: Long,
        expiresAtMillis: Long?,
        errors: MutableSet<UniversalWalletSigningValidationError>
    ) {
        if (!REQUEST_ID.matches(requestId)) {
            errors += UniversalWalletSigningValidationError.InvalidRequestId
        }
        if (!ACCOUNT_ID.matches(accountId)) {
            errors += UniversalWalletSigningValidationError.InvalidAccountId
        }
        if (UniversalWalletEcosystem.fromId(ecosystem) == null) {
            errors += UniversalWalletSigningValidationError.InvalidEcosystem
        }
        if (!CHAIN_ID.matches(chainId)) {
            errors += UniversalWalletSigningValidationError.InvalidChainId
        }
        if (!isMachineText(origin, maxLength = 512)) {
            errors += UniversalWalletSigningValidationError.InvalidOrigin
        }
        if (createdAtMillis <= 0 || expiresAtMillis?.let { it <= createdAtMillis } == true) {
            errors += UniversalWalletSigningValidationError.InvalidTimestamp
        }
    }

    fun isBase64Payload(value: String?, maxLength: Int): Boolean {
        return value != null && value.isNotEmpty() && value.length <= maxLength && BASE64.matches(value)
    }

    fun isHexPayload(value: String, maxLength: Int): Boolean {
        return value.isNotEmpty() && value.length <= maxLength && HEX.matches(value)
    }

    fun isHumanText(value: String, maxLength: Int): Boolean {
        val normalized = value.trim()
        return normalized.isNotEmpty() &&
            normalized.length <= maxLength &&
            normalized.none { it.isISOControl() }
    }

    fun isMachineText(value: String, maxLength: Int): Boolean {
        return value.isNotEmpty() &&
            value.length <= maxLength &&
            value == value.trim() &&
            value.none { it <= ' ' || it.isISOControl() }
    }
}
