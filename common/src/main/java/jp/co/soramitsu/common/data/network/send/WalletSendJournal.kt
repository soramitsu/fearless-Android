package jp.co.soramitsu.common.data.network.send

import jp.co.soramitsu.common.data.network.bitcoin.BitcoinIndexerRoutes
import jp.co.soramitsu.common.data.network.solana.SolanaPublicKeyCodec
import jp.co.soramitsu.common.data.network.solana.SolanaRpcRoutes
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Exact signed-transaction retention and compare/read-back protection across process restarts. */
class WalletSendJournal(private val preferences: Preferences) {
    @Volatile private var durabilityFailed = false

    /** Serializes quote validation, signing, durable retention and submission for one wallet/network. */
    suspend fun <T> withWalletLock(scope: String, action: suspend () -> T): T =
        locks.getOrPut(scopeHash(scope)) { Mutex() }.withLock { action() }

    /** Reads the last payment; unknown or malformed durable state must never mean no payment. */
    fun read(scope: String): PendingWalletSend? {
        if (durabilityFailed) fail(WalletSendJournalException.Code.STORAGE_UNAVAILABLE)
        val hash = scopeHash(scope)
        val value = try { preferences.getString(PREFIX + hash) } catch (_: Exception) {
            fail(WalletSendJournalException.Code.CORRUPT_RECORD)
        } ?: return null
        return try { decode(value).also { require(it.scopeHash == hash) } } catch (_: Exception) {
            fail(WalletSendJournalException.Code.CORRUPT_RECORD)
        }
    }

    /** Retains bytes before network I/O. A retry can only use the same already-retained payment. */
    fun retain(scope: String, payment: PendingWalletSend): PendingWalletSend = synchronized(storageLock) {
        require(payment.scopeHash == scopeHash(scope) && payment.state == PendingWalletSend.State.Pending)
        require(payment.kind != PendingWalletSend.Kind.Solana || payment.solanaNetwork != null)
        val encoded = encode(payment)
        val prior = read(scope)
        if (prior?.kind == PendingWalletSend.Kind.Solana && prior.solanaNetwork == null) {
            throw WalletSendJournalException(WalletSendJournalException.Code.PENDING_PAYMENT, prior.transactionId)
        }
        if (prior != null && (prior.quoteId == payment.quoteId || prior.state == PendingWalletSend.State.Pending)) {
            if (prior.copy(state = payment.state) != payment) {
                throw WalletSendJournalException(WalletSendJournalException.Code.PENDING_PAYMENT, prior.transactionId)
            }
            return prior
        }
        persist(scope, prior, encoded)
        return payment
    }

    /** Records only a definitive chain result while preserving the exact signed bytes. */
    fun settle(scope: String, expected: PendingWalletSend, state: PendingWalletSend.State): PendingWalletSend = synchronized(storageLock) {
        require(state != PendingWalletSend.State.Pending)
        require(expected.kind != PendingWalletSend.Kind.Solana || expected.solanaNetwork != null)
        val current = read(scope)
        if (current != expected || (current.state != PendingWalletSend.State.Pending && current.state != state)) {
            fail(WalletSendJournalException.Code.CHANGED_RECORD)
        }
        val updated = current.copy(state = state)
        persist(scope, current, encode(updated))
        return updated
    }

    /**
     * Removes a pending payment proven never to land: no node received it, a node refused it before relaying it,
     * or its blockhash expired with no trace on a node final past that point. Nothing else may remove one.
     */
    fun discard(scope: String, expected: PendingWalletSend) = synchronized(storageLock) {
        require(expected.state == PendingWalletSend.State.Pending)
        if (read(scope) != expected) fail(WalletSendJournalException.Code.CHANGED_RECORD)
        val key = PREFIX + scopeHash(scope)
        val durable = try {
            preferences.replaceStringsDurably(emptyMap(), setOf(key)) && preferences.getString(key) == null
        } catch (_: Exception) { false }
        if (!durable) {
            durabilityFailed = true
            fail(WalletSendJournalException.Code.STORAGE_UNAVAILABLE)
        }
    }

    private fun persist(scope: String, expected: PendingWalletSend?, encoded: String) {
        if (read(scope) != expected) fail(WalletSendJournalException.Code.CHANGED_RECORD)
        val key = PREFIX + scopeHash(scope)
        val durable = try {
            preferences.replaceStringsDurably(mapOf(key to encoded), emptySet()) && preferences.getString(key) == encoded
        } catch (_: Exception) { false }
        if (!durable) {
            durabilityFailed = true
            fail(WalletSendJournalException.Code.STORAGE_UNAVAILABLE)
        }
    }

    private fun encode(payment: PendingWalletSend): String {
        validate(payment)
        val fields = mutableListOf(if (payment.solanaNetwork != null) "3" else if (payment.reviewed == null) "1" else "2", payment.scopeHash, payment.quoteId,
            payment.fingerprint, payment.kind.name, payment.transactionId, payment.feeUnits, payment.accountRentUnits,
            payment.lastValidBlockHeight?.toString().orEmpty(), payment.createdAtMillis.toString(), payment.state.name, payment.signedPayload)
        payment.solanaNetwork?.let { fields += listOf(it.registryGenesisHash, it.executionGenesisHash) }
        payment.reviewed?.let { review ->
            fields += listOf(review.chainId, review.networkName, review.assetId, review.currencyId.orEmpty(), review.symbol,
                review.precision.toString(), review.sender, review.recipient, review.amountUnits).map {
                java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(it.encodeToByteArray())
            }
        }
        return fields.joinToString("\n")
    }

    private fun decode(value: String): PendingWalletSend {
        require(value.length <= MAX_RECORD_LENGTH)
        val fields = value.split('\n', limit = 24)
        require(fields.size == 12 && fields[0] == "1" || fields.size == 21 && fields[0] == "2" ||
            fields[0] == "3" && fields.size in listOf(14, 23))
        val network = if (fields[0] == "3") SolanaSendNetworkIdentity(fields[12], fields[13]) else null
        val reviewed = if (fields[0] == "2" || fields.size == 23) {
            val detail = fields.drop(if (network == null) 12 else 14).map { String(java.util.Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
            ReviewedWalletSend(detail[0], detail[1], detail[2], detail[3].takeIf { it.isNotEmpty() }, detail[4], detail[5].toInt(),
                detail[6], detail[7], detail[8])
        } else null
        return PendingWalletSend(fields[1], fields[2], fields[3], PendingWalletSend.Kind.valueOf(fields[4]), fields[5], fields[11],
            fields[6], fields[7], fields[8].takeIf { it.isNotEmpty() }?.toLong(), fields[9].toLong(),
            PendingWalletSend.State.valueOf(fields[10]), reviewed, network).also(::validate)
    }

    private fun validate(payment: PendingWalletSend) {
        require(HEX_HASH.matches(payment.scopeHash) && HEX_HASH.matches(payment.fingerprint))
        require(UUID.fromString(payment.quoteId).toString() == payment.quoteId)
        require(UNITS.matches(payment.feeUnits) && UNITS.matches(payment.accountRentUnits) && payment.createdAtMillis > 0)
        require(payment.signedPayload.length <= 1_000_000)
        payment.reviewed?.let { detail ->
            require(detail.precision in 0..30 && UNITS.matches(detail.amountUnits) && detail.amountUnits != "0")
            require(listOf(detail.chainId, detail.networkName, detail.assetId, detail.symbol, detail.sender, detail.recipient)
                .all { it.isNotBlank() && it.length <= 256 && it.none(Char::isISOControl) })
            require(detail.currencyId == null || detail.currencyId.length <= 256 && detail.currencyId.none(Char::isISOControl))
        }
        when (payment.kind) {
            PendingWalletSend.Kind.Bitcoin -> {
                require(payment.solanaNetwork == null)
                require(HEX_HASH.matches(payment.transactionId) && payment.lastValidBlockHeight == null && payment.accountRentUnits == "0")
                require(BitcoinIndexerRoutes.normalizeBroadcastTransactionBody(payment.signedPayload) == payment.signedPayload)
            }
            PendingWalletSend.Kind.Solana -> {
                SolanaPublicKeyCodec.decodeFixedSize(payment.transactionId, 64)
                require(payment.lastValidBlockHeight != null && payment.lastValidBlockHeight >= 0)
                require(SolanaRpcRoutes.normalizeTransactionBase64(payment.signedPayload) == payment.signedPayload)
            }
        }
    }

    companion object {
        private const val PREFIX = "wallet.send.pending.v1."
        private const val MAX_RECORD_LENGTH = 1_005_000
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val storageLock = Any()
        private val HEX_HASH = Regex("^[0-9a-f]{64}$")
        private val UNITS = Regex("^(0|[1-9][0-9]{0,19})$")

        /** Hashes a canonical wallet/network scope; preference keys do not expose account addresses. */
        fun scopeHash(scope: String): String {
            require(scope.isNotEmpty() && scope.length <= 2048)
            return MessageDigest.getInstance("SHA-256").digest(scope.encodeToByteArray()).joinToString("") { "%02x".format(it) }
        }

        private fun fail(code: WalletSendJournalException.Code): Nothing = throw WalletSendJournalException(code)
    }
}
