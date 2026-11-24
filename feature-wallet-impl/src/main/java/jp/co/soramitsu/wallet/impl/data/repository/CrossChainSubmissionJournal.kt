package jp.co.soramitsu.wallet.impl.data.repository

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.extrinsic.mortality.ExtrinsicValidity
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * At most one unsettled cross-chain or bridge transfer per wallet, origin network and sender account, kept on disk
 * with its exact signed bytes from before they are sent until their outcome is proven. While a record exists no new
 * cross-chain transfer is signed for that scope, so an ambiguous failure can never turn into a second transfer.
 *
 * A record is released only when [CrossChainSubmissionReconciler] proves the outcome: the exact bytes are in a
 * finalized block of the origin network, or the whole validity window of the mortal transaction is finalized without
 * them, after which it can never be included.
 *
 * A transfer proven included is kept as the scope's included record, written in the same durable write that releases
 * it, until the user dismisses it or the next transfer of the scope is retained. A confirm screen restored after
 * another process death then still knows that its transfer was sent, instead of offering to send it again.
 */
class CrossChainSubmissionJournal(private val preferences: Preferences) {
    private val failedScopes = synchronized(storageLock) {
        failedStores.getOrPut(preferences.durableStorageIdentity) { mutableSetOf() }
    }

    suspend fun <T> withScope(scope: Scope, action: suspend () -> T): T =
        locks.getOrPut(scope.key) { Mutex() }.withLock { action() }

    fun requireAvailable(scope: Scope) {
        read(scope)?.let { throw CrossChainSubmissionPendingException(it) }
    }

    fun read(scope: Scope): Submission? = synchronized(storageLock) {
        check(scope.key !in failedScopes) { "Cross-chain transfer storage is unavailable" }
        val encoded = preferences.getString(scope.key) ?: return@synchronized null
        decode(encoded).also { check(it.scope == scope) { "Cross-chain transfer scope changed" } }
    }

    fun retain(
        scope: Scope,
        details: Details,
        signed: String,
        intent: String,
        anchor: FinalizedExtrinsicReader.Anchor,
        validity: ExtrinsicValidity?
    ): Submission = synchronized(storageLock) {
        requireAvailable(scope)
        val submission = Submission(
            scope = scope,
            details = details,
            signedExtrinsic = signed,
            intent = intent,
            transactionHash = signed.fromHex().blake2b256().toHexString(withPrefix = true),
            attemptId = UUID.randomUUID().toString(),
            acknowledged = false,
            anchor = anchor,
            validity = validity
        )
        // A new transfer of the scope supersedes the record of the last included one.
        persist(scope, null, submission, clearIncluded = true)
        submission
    }

    fun acknowledge(expected: Submission) = synchronized(storageLock) {
        persist(expected.scope, expected, expected.copy(acknowledged = true))
    }

    /** Only the live attempt's sealed output gate can prove that the bytes were never handed to the network. */
    fun discardUnsubmitted(expected: Submission) = synchronized(storageLock) {
        persist(expected.scope, expected, null)
    }

    /** Frees the scope once the reconciler has proven that exactly this attempt can never be included. */
    internal fun release(expected: Submission) = synchronized(storageLock) {
        persist(expected.scope, expected, null)
    }

    /** Frees the scope once the reconciler has found exactly this attempt in a finalized block, and records it. */
    internal fun settleIncluded(expected: Submission) = synchronized(storageLock) {
        persist(expected.scope, expected, null, included = expected)
    }

    /** The last transfer of [scope] proven included, until the user dismisses it or the scope sends another. */
    fun readIncluded(scope: Scope): Submission? = synchronized(storageLock) {
        check(scope.key !in failedScopes) { "Cross-chain transfer storage is unavailable" }
        val encoded = preferences.getString(scope.includedKey) ?: return@synchronized null
        decode(encoded).also { check(it.scope == scope) { "Cross-chain transfer scope changed" } }
    }

    /** Forgets the included record once the user has seen it; a record that is already gone is fine. */
    fun dismissIncluded(expected: Submission) = synchronized(storageLock) {
        val current = readIncluded(expected.scope) ?: return@synchronized
        check(current == expected) { "Cross-chain transfer record changed" }
        // Only the included record goes; an unsettled transfer of the scope, if any, stays exactly as it is.
        val pending = read(expected.scope)
        persist(expected.scope, pending, pending, clearIncluded = true)
    }

    private fun persist(
        scope: Scope,
        expected: Submission?,
        replacement: Submission?,
        included: Submission? = null,
        clearIncluded: Boolean = false
    ) {
        check(read(scope) == expected) { "Cross-chain transfer record changed" }
        val encoded = replacement?.let(::encode)
        val encodedIncluded = included?.let(::encode)
        val puts = listOfNotNull(encoded?.let { scope.key to it }, encodedIncluded?.let { scope.includedKey to it }).toMap()
        val removes = setOfNotNull(scope.key.takeIf { encoded == null }, scope.includedKey.takeIf { clearIncluded })
        val durable = try {
            preferences.replaceStringsDurably(puts, removes) &&
                preferences.getString(scope.key) == encoded &&
                (!clearIncluded || preferences.getString(scope.includedKey) == null) &&
                (encodedIncluded == null || preferences.getString(scope.includedKey) == encodedIncluded)
        } catch (_: Exception) {
            false
        }
        if (!durable) {
            // A record that may or may not be on disk blocks the scope instead of being guessed.
            failedScopes += scope.key
            error("Cross-chain transfer storage is unavailable")
        }
    }

    private fun encode(submission: Submission): String {
        validate(submission)
        val details = submission.details
        return listOf(
            VERSION,
            submission.scope.walletId.toString(),
            submission.scope.chainId,
            submission.scope.accountId,
            details.destinationChainId.text(),
            details.assetId.text(),
            details.symbol.text(),
            details.precision.toString(),
            details.recipient.text(),
            details.amountInPlanks.toString(),
            submission.signedExtrinsic,
            submission.intent,
            submission.transactionHash,
            submission.attemptId,
            submission.acknowledged.toString(),
            submission.anchor.number.toString(),
            submission.anchor.blockHash,
            submission.validity?.birthBlock?.toString().orEmpty(),
            submission.validity?.period?.toString().orEmpty()
        ).joinToString("\n")
    }

    private fun decode(encoded: String): Submission {
        require(encoded.length <= MAX_RECORD_LENGTH) { "Cross-chain transfer record is too large" }
        val fields = encoded.split('\n')
        require(fields.size == FIELD_COUNT && fields[0] == VERSION) { "Unknown cross-chain transfer record" }
        require(fields[14] == "true" || fields[14] == "false") { "Invalid cross-chain transfer record" }
        val scope = Scope(fields[1].toLong(), fields[2], fields[3])
        val validity = if (fields[17].isEmpty() && fields[18].isEmpty()) {
            null
        } else {
            ExtrinsicValidity(fields[17].toBigInteger(), fields[18].toInt())
        }
        return Submission(
            scope = scope,
            details = Details(
                destinationChainId = fields[4].fromText(),
                assetId = fields[5].fromText(),
                symbol = fields[6].fromText(),
                precision = fields[7].toInt(),
                recipient = fields[8].fromText(),
                amountInPlanks = fields[9].toBigInteger()
            ),
            signedExtrinsic = fields[10],
            intent = fields[11],
            transactionHash = fields[12],
            attemptId = fields[13],
            acknowledged = fields[14].toBooleanStrict(),
            anchor = FinalizedExtrinsicReader.Anchor("0x" + scope.chainId, fields[15].toBigInteger(), fields[16]),
            validity = validity
        ).also(::validate)
    }

    private fun validate(submission: Submission) {
        require(HASH.matches(submission.intent)) { "Invalid cross-chain transfer intent" }
        require(submission.signedExtrinsic.length in 4..MAX_EXTRINSIC_LENGTH && EXTRINSIC.matches(submission.signedExtrinsic)) {
            "Invalid cross-chain transfer bytes"
        }
        require(submission.signedExtrinsic.fromHex().blake2b256().toHexString(withPrefix = true) == submission.transactionHash) {
            "Cross-chain transfer hash does not match its bytes"
        }
        require(UUID.fromString(submission.attemptId).toString() == submission.attemptId) { "Invalid cross-chain attempt" }
        require(submission.anchor.genesisHash == "0x" + submission.scope.chainId) { "Cross-chain anchor is for another network" }
        submission.validity?.let {
            require(it.birthBlock > submission.anchor.number - it.period.toBigInteger()) {
                "Cross-chain validity window precedes its anchor"
            }
        }
    }

    private fun String.text(): String = toByteArray(Charsets.UTF_8).toHexString(withPrefix = false)

    private fun String.fromText(): String {
        require(isEmpty() || TEXT.matches(this)) { "Invalid cross-chain transfer text" }
        return fromHex().decodeToString(throwOnInvalidSequence = true)
    }

    data class Scope(val walletId: Long, val chainId: String, val accountId: String) {
        init {
            require(walletId > 0 && HASH.matches(chainId) && ACCOUNT.matches(accountId)) { "Invalid cross-chain scope" }
        }

        private val digest: String = MessageDigest.getInstance("SHA-256")
            .digest("$walletId\n$chainId\n$accountId".toByteArray(Charsets.UTF_8)).toHexString(withPrefix = false)

        val key: String = PREFIX + digest

        /** Where the last transfer of this scope that was proven included is kept. */
        val includedKey: String = INCLUDED_PREFIX + digest
    }

    /** What the user confirmed, kept to present the unsettled transfer and to recognise a retry of it. */
    data class Details(
        val destinationChainId: String,
        val assetId: String,
        val symbol: String,
        val precision: Int,
        val recipient: String,
        val amountInPlanks: BigInteger
    ) {
        init {
            require(destinationChainId.isNotBlank() && assetId.isNotBlank() && symbol.isNotBlank() && recipient.isNotBlank()) {
                "Cross-chain transfer details are incomplete"
            }
            require(precision >= 0 && amountInPlanks.signum() > 0) { "Cross-chain transfer amount is invalid" }
        }
    }

    data class Submission(
        val scope: Scope,
        val details: Details,
        val signedExtrinsic: String,
        val intent: String,
        val transactionHash: String,
        val attemptId: String,
        val acknowledged: Boolean,
        val anchor: FinalizedExtrinsicReader.Anchor,
        val validity: ExtrinsicValidity?
    )

    private companion object {
        const val VERSION = "1"
        const val FIELD_COUNT = 19
        const val PREFIX = "wallet.crosschain.pending.v1."
        const val INCLUDED_PREFIX = "wallet.crosschain.included.v1."
        const val MAX_EXTRINSIC_LENGTH = 1_000_000
        const val MAX_RECORD_LENGTH = 1_010_000
        val locks = ConcurrentHashMap<String, Mutex>()
        val storageLock = Any()
        val failedStores = java.util.IdentityHashMap<Any, MutableSet<String>>()
        val HASH = Regex("[0-9a-f]{64}")
        val ACCOUNT = Regex("(?:[0-9a-f]{2}){20,32}")
        val TEXT = Regex("(?:[0-9a-f]{2})+")
        val EXTRINSIC = Regex("0x(?:[0-9a-f]{2})+")
    }
}

/** Carries the unsettled transfer for the "may have been sent" presentation; it never permits signing again. */
class CrossChainSubmissionPendingException(val submission: CrossChainSubmissionJournal.Submission) :
    IllegalStateException("A previous cross-chain transfer still needs a status check: ${submission.transactionHash}")
