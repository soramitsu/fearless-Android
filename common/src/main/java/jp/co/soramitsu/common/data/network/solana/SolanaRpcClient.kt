package jp.co.soramitsu.common.data.network.solana

import java.net.MalformedURLException
import java.net.URL

interface SolanaRpcClient {
    /** Binds all requests to a caller-selected cluster, including after endpoint changes. */
    fun withGenesisHash(expectedGenesisHash: String): SolanaRpcClient

    /** Returns a client scoped to known endpoints of the same cluster; adapters may keep their own routing. */
    fun withEndpoints(rpcUrls: List<String>): SolanaRpcClient = this

    /** Reads native spendable balance directly from the configured cluster. */
    suspend fun balance(address: String, commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed, rpcUrl: String? = null): Long =
        error("RPC balance lookup is unavailable")

    /** Reads owner, executable state and a bounded data slice; null means an authoritative missing account. */
    suspend fun accountInfo(address: String, commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed, rpcUrl: String? = null): SolanaRpcAccountInfo? =
        error("RPC account validation is unavailable")

    /** Reads complete token accounts for one exact owner and token program, without indexer metadata. */
    suspend fun tokenAccounts(address: String, programAddress: String, commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null): List<SolanaRpcTokenAccount> = error("RPC token balances are unavailable")

    /** Returns the chain height used to evaluate a reviewed blockhash's validity. */
    suspend fun blockHeight(commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed, rpcUrl: String? = null): Long =
        error("RPC block height is unavailable")

    /** Searches signature history; an absent result alone cannot prove a payment never landed. */
    suspend fun signatureStatus(signature: String, rpcUrl: String? = null): SolanaRpcSignatureStatus? =
        error("RPC signature status is unavailable")

    /**
     * The signature's status, or proof that the transaction can never land: an endpoint finalized past
     * [lastValidBlockHeight] finds, from a lookup at least as recent, no trace of it, and no other endpoint
     * disagrees. Adapters without that proof never report [SolanaSignatureOutcome.Expired].
     */
    suspend fun signatureOutcome(signature: String, lastValidBlockHeight: Long, rpcUrl: String? = null): SolanaSignatureOutcome =
        signatureStatus(signature, rpcUrl)?.let { SolanaSignatureOutcome.Seen(it) } ?: SolanaSignatureOutcome.Unknown

    /** Returns bounded recent micro-lamport per compute-unit samples for fee policy. */
    suspend fun recentPrioritizationFees(rpcUrl: String? = null): List<Long> = error("RPC priority fee samples are unavailable")

    suspend fun latestBlockhash(
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): SolanaLatestBlockhashResponse

    suspend fun feeForMessage(
        messageBase64: String,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): SolanaFeeForMessageResponse

    suspend fun minimumBalanceForRentExemption(
        dataLength: Int,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): Long

    suspend fun accountExists(
        address: String,
        commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
        rpcUrl: String? = null
    ): Boolean

    suspend fun simulateTransaction(
        transactionBase64: String,
        options: SolanaSimulationOptions = SolanaSimulationOptions(),
        rpcUrl: String? = null
    ): SolanaSimulationResponse

    suspend fun sendRawTransaction(
        transactionBase64: String,
        options: SolanaBroadcastOptions = SolanaBroadcastOptions(),
        rpcUrl: String? = null
    ): String
}


object SolanaRpcRoutes {
    private const val MAX_TRANSACTION_BASE64_LENGTH = 1_000_000
    private const val MAX_RENT_DATA_LENGTH = 10_000_000

    fun normalizeRpcUrl(rpcUrl: String): String {
        val parsed = try {
            require(rpcUrl.length <= 8192 && rpcUrl.all { it.code in 33..126 })
            val uri = java.net.URI(rpcUrl)
            require(uri.scheme in setOf("http", "https") && uri.rawFragment == null && uri.rawUserInfo == null)
            URL(rpcUrl).also { require(it.toURI().host != null && (it.port == -1 || it.port in 1..65535)) }
        } catch (_: Exception) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_URL)
        }
        val isLocal = parsed.host == "localhost" || parsed.host == "127.0.0.1"
        if ((parsed.protocol != "https" && !(parsed.protocol == "http" && isLocal)) ||
            parsed.host.isBlank() || parsed.userInfo != null
        ) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_RPC_URL)
        }
        // The complete path and query are transport identity. Validation never rewrites them.
        return rpcUrl
    }

    fun normalizeTransactionBase64(transactionBase64: String): String {
        if (transactionBase64.isEmpty() ||
            transactionBase64.length > MAX_TRANSACTION_BASE64_LENGTH ||
            transactionBase64.length % 4 != 0 ||
            !BASE64_TRANSACTION.matches(transactionBase64)
        ) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_TRANSACTION)
        }

        return transactionBase64
    }

    fun normalizeDataLength(dataLength: Int): Int {
        if (dataLength !in 0..MAX_RENT_DATA_LENGTH) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_DATA_LENGTH)
        }

        return dataLength
    }

    fun normalizePublicKey(publicKey: String): String {
        val trimmed = publicKey.trim()
        if (runCatching { SolanaPublicKeyCodec.decode(trimmed) }.isFailure) {
            throw SolanaRpcException(SolanaRpcException.Code.INVALID_ACCOUNT_ADDRESS)
        }

        return trimmed
    }

    private val BASE64_TRANSACTION = Regex("^(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?$")
}

enum class SolanaRpcCommitment(val value: String) {
    Processed("processed"),
    Confirmed("confirmed"),
    Finalized("finalized")
}

data class SolanaBroadcastOptions(
    val maxRetries: Int? = null,
    val preflightCommitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val skipPreflight: Boolean = false
)

data class SolanaSimulationOptions(
    val commitment: SolanaRpcCommitment = SolanaRpcCommitment.Confirmed,
    val replaceRecentBlockhash: Boolean = true,
    val sigVerify: Boolean = false
)

data class SolanaRpcContext(
    val apiVersion: String? = null,
    val slot: Long
)

data class SolanaLatestBlockhash(
    val blockhash: String,
    val lastValidBlockHeight: Long
)

data class SolanaLatestBlockhashResponse(
    val context: SolanaRpcContext,
    val value: SolanaLatestBlockhash
)

data class SolanaFeeForMessageResponse(
    val context: SolanaRpcContext,
    val value: Long?
)

data class SolanaSimulationValue(
    val errorJson: String?,
    val logs: List<String>?,
    val replacementBlockhash: SolanaLatestBlockhash?,
    val unitsConsumed: Long?
)

data class SolanaSimulationResponse(
    val context: SolanaRpcContext,
    val value: SolanaSimulationValue
)

sealed interface SolanaSignatureOutcome {
    data class Seen(val status: SolanaRpcSignatureStatus) : SolanaSignatureOutcome
    /**
     * Final past the blockhash expiry with no trace, on every endpoint that answered: it can never land.
     * [provingHosts] distinct hosts proved it on their own; spellings of one host count once. One node's empty
     * lookup can come from a backend that lacks that slot's history, so only [corroborated] expiry shows that the
     * payment never left the wallet.
     */
    data class Expired(val provingHosts: Int) : SolanaSignatureOutcome {
        init { require(provingHosts >= 1) }
        val corroborated: Boolean get() = provingHosts >= MIN_CORROBORATING_HOSTS

        companion object {
            const val MIN_CORROBORATING_HOSTS = 2
        }
    }
    object Unknown : SolanaSignatureOutcome
}

/**
 * No endpoint accepted a broadcast. [outcome] says what that proves: [Outcome.NOT_SENT], no node received the
 * transaction; [Outcome.REFUSED], a node judged it and refused it before relaying it (preflight, signature or
 * decoding verdict); [Outcome.AMBIGUOUS], an attempt may have reached a node (a timeout, a dropped connection,
 * throttling, a server error, an unreadable reply or any other JSON-RPC error), so the transaction may still land.
 */
class SolanaBroadcastException(val outcome: Outcome, cause: Throwable? = null) : IllegalStateException(outcome.name, cause) {
    enum class Outcome { NOT_SENT, REFUSED, AMBIGUOUS }
}

data class SolanaJsonRpcErrorPayload(
    val code: Int,
    val message: String,
    val dataJson: String? = null
)

class SolanaRpcException(
    val code: Code,
    val rpcError: SolanaJsonRpcErrorPayload? = null
) : IllegalArgumentException(code.name) {
    enum class Code {
        INVALID_RPC_URL,
        INVALID_ACCOUNT_ADDRESS,
        INVALID_TRANSACTION,
        INVALID_MAX_RETRIES,
        INVALID_DATA_LENGTH,
        INVALID_SIMULATION_OPTIONS,
        INVALID_RPC_RESPONSE,
        INVALID_GENESIS_RESPONSE,
        GENESIS_MISMATCH,
        RPC_ERROR,
        INVALID_BLOCKHASH_RESPONSE,
        INVALID_FEE_RESPONSE,
        INVALID_RENT_RESPONSE,
        INVALID_ACCOUNT_INFO_RESPONSE,
        INVALID_SIMULATION_RESPONSE,
        INVALID_SIGNATURE_RESPONSE,
        INVALID_BALANCE_RESPONSE,
        INVALID_TOKEN_ACCOUNTS_RESPONSE,
        INVALID_BLOCK_HEIGHT_RESPONSE,
        INVALID_EPOCH_INFO_RESPONSE,
        INVALID_SIGNATURE_STATUS_RESPONSE,
        INVALID_PRIORITY_FEE_RESPONSE
    }
}
