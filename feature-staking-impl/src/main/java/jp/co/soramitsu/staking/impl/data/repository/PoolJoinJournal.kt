package jp.co.soramitsu.staking.impl.data.repository

import jp.co.soramitsu.common.data.storage.Preferences
import jp.co.soramitsu.core.rpc.FinalizedExtrinsicReader
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.fearless_utils.extensions.toHexString
import jp.co.soramitsu.fearless_utils.hash.Hasher.blake2b256
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/** One unresolved pool payment per original wallet/network/account, independent of UI lifetime. */
class PoolJoinJournal(private val preferences: Preferences) {
    private val failedScopes = synchronized(storageLock) {
        failedStores.getOrPut(preferences.durableStorageIdentity) { mutableSetOf() }
    }

    suspend fun <T> withScope(scope: Scope, action: suspend () -> T): T =
        locks.getOrPut(scope.key) { Mutex() }.withLock { action() }

    fun requireAvailable(scope: Scope) {
        read(scope)?.let { throw PoolJoinPendingException(it.transactionHash, it.acknowledged) }
    }

    fun read(scope: Scope): Payment? = synchronized(storageLock) {
        check(scope.key !in failedScopes) { "Pool transaction storage is unavailable" }
        val encoded = preferences.getString(scope.key) ?: return@synchronized null
        decode(encoded).also { check(it.scope == scope) { "Pool transaction scope changed" } }
    }

    fun retain(scope: Scope, amount: BigInteger, poolId: BigInteger, signed: String, intent: String,
        anchor: FinalizedExtrinsicReader.Anchor? = null, create: CreateDetails? = null): Payment = synchronized(storageLock) {
        requireAvailable(scope)
        val payment = Payment(scope, amount.toString(), poolId.toString(), signed, intent,
            signed.fromHex().blake2b256().toHexString(withPrefix = true), UUID.randomUUID().toString(), false, anchor, create)
        persist(scope, null, payment)
        payment
    }

    fun acknowledge(expected: Payment) = synchronized(storageLock) {
        persist(expected.scope, expected, expected.copy(acknowledged = true))
    }

    /** Only the live attempt's sealed final-writer gate can prove that output was never admitted. */
    fun discardUnsubmitted(expected: Payment) = synchronized(storageLock) {
        persist(expected.scope, expected, null)
    }

    /** Atomically preserves the original bytes and inclusion while freeing only this exact pending record. */
    internal fun finalize(expected: Payment, inclusion: FinalizedExtrinsicReader.Inclusion) = synchronized(storageLock) {
        validateInclusion(expected, inclusion)
        check(read(expected.scope) == expected) { "Pool transaction record changed" }
        val archiveKey = archiveKey(expected.scope, expected.attemptId)
        check(preferences.getString(archiveKey) == null) { "Pool transaction archive already exists" }
        val encoded = listOf("finalized-1", encode(expected), inclusion.genesisHash, inclusion.blockHash,
            inclusion.blockNumber.toString(), inclusion.extrinsicIndex.toString(), inclusion.finalizedHeadHash,
            inclusion.finalizedHeadNumber.toString()).joinToString("\n")
        write(expected.scope, mapOf(archiveKey to encoded), setOf(expected.scope.key))
    }

    /** Retained public receipt; inclusion is deliberately not presented as dispatch success. */
    fun readFinalized(scope: Scope, attemptId: String): FinalizedPayment? = synchronized(storageLock) {
        check(scope.key !in failedScopes) { "Pool transaction storage is unavailable" }
        val raw = preferences.getString(archiveKey(scope, attemptId)) ?: return@synchronized null
        require(raw.length <= 1_006_000)
        val fields = raw.split('\n')
        require(fields[0] == "finalized-1" &&
            ((fields.size == 20 && fields[1] == "2") || (fields.size == 23 && fields[1] == "3"))) {
            "Unknown finalized pool transaction"
        }
        val receiptStart = fields.size - 6
        val payment = decode(fields.subList(1, receiptStart).joinToString("\n"))
        check(payment.scope == scope && payment.attemptId == attemptId) { "Pool transaction archive changed" }
        val inclusion = FinalizedExtrinsicReader.Inclusion(fields[receiptStart], payment.transactionHash, fields[receiptStart + 1],
            fields[receiptStart + 2].toBigInteger(), fields[receiptStart + 3].toInt(), fields[receiptStart + 4],
            fields[receiptStart + 5].toBigInteger())
        validateInclusion(payment, inclusion)
        FinalizedPayment(payment, inclusion)
    }

    private fun archiveKey(scope: Scope, attemptId: String): String {
        require(UUID.fromString(attemptId).toString() == attemptId)
        return scope.key + ".finalized." + attemptId
    }

    private fun validateInclusion(payment: Payment, inclusion: FinalizedExtrinsicReader.Inclusion) {
        validate(payment)
        val anchor = checkNotNull(payment.anchor) { "Original finalized anchor is unavailable" }
        require(inclusion.genesisHash == "0x" + payment.scope.chainId && inclusion.transactionHash == payment.transactionHash)
        require(inclusion.blockHash.startsWith("0x") && HASH.matches(inclusion.blockHash.removePrefix("0x")))
        require(inclusion.finalizedHeadHash.startsWith("0x") && HASH.matches(inclusion.finalizedHeadHash.removePrefix("0x")))
        require(inclusion.blockNumber > anchor.number && inclusion.finalizedHeadNumber >= inclusion.blockNumber && inclusion.extrinsicIndex >= 0)
    }

    private fun persist(scope: Scope, expected: Payment?, replacement: Payment?) {
        check(read(scope) == expected) { "Pool transaction record changed" }
        val encoded = replacement?.let(::encode)
        write(scope, encoded?.let { mapOf(scope.key to it) }.orEmpty(), if (encoded == null) setOf(scope.key) else emptySet())
    }

    private fun write(scope: Scope, values: Map<String, String>, removed: Set<String>) {
        val durable = try {
            preferences.replaceStringsDurably(values, removed) &&
                values.all { (key, value) -> preferences.getString(key) == value } && removed.all { preferences.getString(it) == null }
        } catch (_: Exception) { false }
        if (!durable) {
            failedScopes += scope.key
            error("Pool transaction storage is unavailable")
        }
    }

    private fun encode(payment: Payment): String {
        validate(payment)
        val fields = listOf(if (payment.create != null) "3" else if (payment.anchor == null) "1" else "2", payment.scope.walletId.toString(), payment.scope.chainId, payment.scope.accountId,
            payment.amount, payment.poolId, payment.signedExtrinsic, payment.intent, payment.transactionHash,
            payment.attemptId, payment.acknowledged.toString())
        val creation = payment.create?.let {
            listOf(it.poolName, it.nominatorAddress, it.stateTogglerAddress).map { value ->
                value.toByteArray(Charsets.UTF_8).toHexString(withPrefix = false)
            }
        }.orEmpty()
        return (fields + payment.anchor?.let { listOf(it.number.toString(), it.blockHash) }.orEmpty() + creation).joinToString("\n")
    }

    private fun decode(encoded: String): Payment {
        require(encoded.length <= 1_005_000) { "Pool transaction record is too large" }
        val fields = encoded.split('\n')
        require((fields.size == 11 && fields[0] == "1") || (fields.size == 13 && fields[0] == "2") ||
            (fields.size == 16 && fields[0] == "3")) { "Unknown pool transaction record" }
        require(fields[10] == "true" || fields[10] == "false")
        return Payment(Scope(fields[1].toLong(), fields[2], fields[3]), fields[4], fields[5], fields[6], fields[7],
            fields[8], fields[9], fields[10].toBooleanStrict(), if (fields[0] != "1")
                FinalizedExtrinsicReader.Anchor("0x" + fields[2], fields[11].toBigInteger(), fields[12]) else null,
            if (fields[0] == "3") {
                val values = fields.subList(13, 16).map { value ->
                    require(ENCODED_TEXT.matches(value)) { "Invalid pool creation details" }
                    value.fromHex().decodeToString(throwOnInvalidSequence = true)
                }
                CreateDetails(values[0], values[1], values[2])
            } else null).also(::validate)
    }

    private fun validate(payment: Payment) {
        require(UNITS.matches(payment.amount) && payment.amount.toBigInteger().signum() > 0)
        require(UNITS.matches(payment.poolId) && payment.poolId.toBigInteger().signum() > 0)
        require(HASH.matches(payment.intent))
        require(payment.signedExtrinsic.length in 4..1_000_000 && EXTRINSIC.matches(payment.signedExtrinsic))
        require(payment.signedExtrinsic.fromHex().blake2b256().toHexString(withPrefix = true) == payment.transactionHash)
        require(UUID.fromString(payment.attemptId).toString() == payment.attemptId)
        payment.anchor?.let { require(it.genesisHash == "0x" + payment.scope.chainId) }
        if (payment.create != null) require(payment.anchor != null) { "Pool creation requires its original block reference" }
    }

    data class Scope(val walletId: Long, val chainId: String, val accountId: String) {
        init { require(walletId >= 0 && HASH.matches(chainId) && HASH.matches(accountId)) }
        val key: String = PREFIX + MessageDigest.getInstance("SHA-256")
            .digest("$walletId\n$chainId\n$accountId".toByteArray(Charsets.UTF_8)).toHexString(withPrefix = false)
    }

    data class Payment(
        val scope: Scope,
        val amount: String,
        val poolId: String,
        val signedExtrinsic: String,
        val intent: String,
        val transactionHash: String,
        val attemptId: String,
        val acknowledged: Boolean,
        val anchor: FinalizedExtrinsicReader.Anchor? = null,
        val create: CreateDetails? = null
    )

    /** Original user-visible create fields; exact text is encoded separately from line delimiters. */
    data class CreateDetails(val poolName: String, val nominatorAddress: String, val stateTogglerAddress: String) {
        init {
            require(poolName.toByteArray(Charsets.UTF_8).size in 1..512)
            require(nominatorAddress.toByteArray(Charsets.UTF_8).size in 1..256)
            require(stateTogglerAddress.toByteArray(Charsets.UTF_8).size in 1..256)
        }
    }

    data class FinalizedPayment(val payment: Payment, val inclusion: FinalizedExtrinsicReader.Inclusion)

    companion object {
        private const val PREFIX = "wallet.pool.join.pending.v1."
        private val locks = ConcurrentHashMap<String, Mutex>()
        private val storageLock = Any()
        private val failedStores = IdentityHashMap<Any, MutableSet<String>>()
        private val HASH = Regex("[0-9a-f]{64}")
        private val UNITS = Regex("[0-9]{1,39}")
        private val ENCODED_TEXT = Regex("(?:[0-9a-f]{2})+")
        private val EXTRINSIC = Regex("0x(?:[0-9a-f]{2})+")
    }
}

/** Carries public transaction identity for the recovery presentation; never permits fresh signing. */
class PoolJoinPendingException(val transactionHash: String, val acknowledged: Boolean) :
    IllegalStateException("A previous pool transaction still requires reconciliation: $transactionHash")
