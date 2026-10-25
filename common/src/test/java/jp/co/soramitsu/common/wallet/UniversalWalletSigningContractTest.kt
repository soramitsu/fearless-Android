package jp.co.soramitsu.common.wallet

import com.google.gson.Gson
import jp.co.soramitsu.common.model.UniversalWalletSigningDisplay
import jp.co.soramitsu.common.model.UniversalWalletSigningMethod
import jp.co.soramitsu.common.model.UniversalWalletSigningPayload
import jp.co.soramitsu.common.model.UniversalWalletSigningPayloadEncoding
import jp.co.soramitsu.common.model.UniversalWalletSigningRequest
import jp.co.soramitsu.common.model.UniversalWalletSigningResult
import jp.co.soramitsu.common.model.UniversalWalletSigningResultStatus
import jp.co.soramitsu.common.model.UniversalWalletSigningValidationError
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalWalletSigningContractTest {

    @Test
    fun `validates and serializes message signing requests and results`() {
        val request = messageRequest()
        val result = approvedMessageResult()

        assertTrue(request.validationErrors().isEmpty())
        assertTrue(result.validationErrors().isEmpty())

        val requestJson = Gson().toJson(request)
        val resultJson = Gson().toJson(result)
        assertTrue(requestJson.contains("\"method\":\"sign-message\""))
        assertTrue(requestJson.contains("\"encoding\":\"base64\""))
        assertTrue(resultJson.contains("\"status\":\"approved\""))
        assertTrue(resultJson.contains("\"signatureHex\":\"${"ab".repeat(64)}\""))
    }

    @Test
    fun `rejects malformed message signing requests`() {
        val request = messageRequest().copy(
            requestId = "bad",
            accountId = "../bad",
            ecosystem = "unknown",
            chainId = "bad chain",
            origin = " https://dapp.example ",
            message = UniversalWalletSigningPayload(
                encoding = UniversalWalletSigningPayloadEncoding.Base64,
                value = "*not-base64*",
                display = UniversalWalletSigningDisplay.Utf8
            ),
            transactionBase64 = "AQID",
            createdAtMillis = 10,
            expiresAtMillis = 9
        )

        val errors = request.validationErrors()

        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidRequestId))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidAccountId))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidEcosystem))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidChainId))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidOrigin))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidBase64))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.PayloadNotAllowed))
        assertTrue(errors.contains(UniversalWalletSigningValidationError.InvalidTimestamp))
    }

    @Test
    fun `validates transaction and batch signing requests`() {
        val transaction = messageRequest().copy(
            method = UniversalWalletSigningMethod.SignTransaction,
            message = null,
            transactionBase64 = "AQIDBA=="
        )
        val signAndSend = transaction.copy(method = UniversalWalletSigningMethod.SignAndSendTransaction)
        val batch = transaction.copy(
            method = UniversalWalletSigningMethod.SignAllTransactions,
            transactionBase64 = null,
            transactionsBase64 = listOf("AQIDBA==", "BQYHCA==")
        )

        assertTrue(transaction.validationErrors().isEmpty())
        assertTrue(signAndSend.validationErrors().isEmpty())
        assertTrue(batch.validationErrors().isEmpty())
    }

    @Test
    fun `rejects malformed transaction and batch signing requests`() {
        val transaction = messageRequest().copy(
            method = UniversalWalletSigningMethod.SignTransaction,
            message = null,
            transactionBase64 = "*bad*"
        )
        val batch = transaction.copy(
            method = UniversalWalletSigningMethod.SignAllTransactions,
            transactionBase64 = null,
            transactionsBase64 = List(17) { "AQIDBA==" }
        )

        assertTrue(transaction.validationErrors().contains(UniversalWalletSigningValidationError.TransactionRequired))
        assertTrue(batch.validationErrors().contains(UniversalWalletSigningValidationError.InvalidBatch))
    }

    @Test
    fun `validates approved transaction and rejected signing results`() {
        val transaction = approvedMessageResult().copy(
            method = UniversalWalletSigningMethod.SignAndSendTransaction,
            signatureHex = null,
            signedTransactionBase64 = "AQIDBA==",
            transactionHash = "5NfHnqDyzT9qyfxZDq2sSskAMGuFZ3VRqW4EQxghKqrKYdKq6cZNW1J34w7qE6nGx1eDQe5s2eKxB2ZtE1xU9qgN"
        )
        val rejected = approvedMessageResult().copy(
            status = UniversalWalletSigningResultStatus.Rejected,
            publicKey = null,
            signatureHex = null,
            errorCode = "user_rejected"
        )

        assertTrue(transaction.validationErrors().isEmpty())
        assertTrue(rejected.validationErrors().isEmpty())
    }

    @Test
    fun `rejects inconsistent signing results`() {
        val approved = approvedMessageResult().copy(
            publicKey = null,
            signatureHex = "ABC",
            errorCode = "not_allowed"
        )
        val failed = approvedMessageResult().copy(
            status = UniversalWalletSigningResultStatus.Failed,
            errorCode = null
        )

        val approvedErrors = approved.validationErrors()
        val failedErrors = failed.validationErrors()

        assertTrue(approvedErrors.contains(UniversalWalletSigningValidationError.PublicKeyRequired))
        assertTrue(approvedErrors.contains(UniversalWalletSigningValidationError.InvalidSignature))
        assertTrue(approvedErrors.contains(UniversalWalletSigningValidationError.ErrorNotAllowed))
        assertTrue(failedErrors.contains(UniversalWalletSigningValidationError.ErrorCodeRequired))
        assertTrue(failedErrors.contains(UniversalWalletSigningValidationError.SignatureNotAllowed))
    }

    private fun messageRequest() = UniversalWalletSigningRequest(
        requestId = "sign_1234567890abcdef",
        accountId = "solana-mainnet",
        ecosystem = "solana",
        chainId = "solana:mainnet",
        origin = "https://dapp.example",
        method = UniversalWalletSigningMethod.SignMessage,
        message = UniversalWalletSigningPayload(
            encoding = UniversalWalletSigningPayloadEncoding.Base64,
            value = "ZmVhcmxlc3M=",
            display = UniversalWalletSigningDisplay.Utf8
        ),
        createdAtMillis = 1_710_000_000_000,
        expiresAtMillis = 1_710_000_060_000
    )

    private fun approvedMessageResult() = UniversalWalletSigningResult(
        requestId = "sign_1234567890abcdef",
        accountId = "solana-mainnet",
        ecosystem = "solana",
        chainId = "solana:mainnet",
        method = UniversalWalletSigningMethod.SignMessage,
        status = UniversalWalletSigningResultStatus.Approved,
        publicKey = "HAgk14JpMQLgt6rVgv7cBQFJWFto5Dqxi472uT3DKpqk",
        signatureHex = "ab".repeat(64),
        signedAtMillis = 1_710_000_000_100
    )
}
