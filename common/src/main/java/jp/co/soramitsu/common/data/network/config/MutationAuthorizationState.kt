package jp.co.soramitsu.common.data.network.config

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import jp.co.soramitsu.core.extrinsic.MutationExecutionGuard

interface MutationAuthorizationPersistence {
    fun read(): String?
    fun write(value: String): Boolean
}

interface MutationAuthorizationClock {
    fun wallSeconds(): Long
    fun elapsedMillis(): Long
}

/** Process-local grants; only replay/clock high-water metadata is persisted. */
class MutationAuthorizationState(
    private val context: MutationAuthorizationContext?,
    private val persistence: MutationAuthorizationPersistence,
    private val clock: MutationAuthorizationClock,
    private val verifier: MutationAuthorizationVerifier = MutationAuthorizationVerifier()
) {
    private val lock = Any()
    private var highWater = try { HighWater.decode(persistence.read()) } catch (_: Exception) { null }
    private var active: VerifiedMutationAuthorization? = null
    private var generation = 0L
    private var seenDigest: String? = null
    private var seenDeadline = 0L
    private var seenStarted = 0L

    fun acceptFresh(token: String?): Boolean = synchronized(lock) {
        val sample = checkedClock() ?: return@synchronized false
        val now = maxOf(sample.wall, requireNotNull(highWater).wallSeconds)
        val old = highWater
        val verified = verifier.verify(token, context, now)
        if (old == null || verified == null || now < old.wallSeconds - 60 || verified.revision < old.revision ||
            (verified.revision == old.revision && verified.payloadSha256 != old.digest)) {
            invalidateLocked()
            return@synchronized false
        }
        val elapsed = sample.elapsed
        if (elapsed !in 0..Long.MAX_VALUE - 900_000 || (seenDigest == verified.payloadSha256 && elapsed < seenStarted)) {
            invalidateLocked()
            return@synchronized false
        }
        val remainingMillis = minOf(verified.expiresAt - now, MutationAuthorizationVerifier.MAX_LIFETIME_SECONDS) * 1000
        val candidateDeadline = Math.addExact(elapsed, remainingMillis)
        val deadline = if (seenDigest == verified.payloadSha256) minOf(seenDeadline, candidateDeadline) else candidateDeadline
        if (elapsed < 0 || elapsed >= deadline) {
            invalidateLocked()
            return@synchronized false
        }
        val next = HighWater(verified.revision, verified.payloadSha256, maxOf(old.wallSeconds, now))
        val persisted = try { persistence.write(next.encode()) } catch (_: Exception) { false }
        if (!persisted) {
            // A commit can reach disk before reporting failure. Never trust the older in-memory revision again.
            highWater = null
            invalidateLocked()
            return@synchronized false
        }
        highWater = next
        if (active?.payloadSha256 != verified.payloadSha256) generation++
        if (seenDigest != verified.payloadSha256) seenStarted = elapsed
        seenDigest = verified.payloadSha256
        seenDeadline = deadline
        active = verified
        // The durable revision save may have blocked across expiry.
        currentGrant() != null
    }

    fun invalidate() = synchronized(lock) { invalidateLocked() }

    fun permits(capability: MutationCapability): Boolean = synchronized(lock) { currentAllows(capability) }

    fun acquire(capability: MutationCapability, intentSha256: String): MutationExecutionGuard = synchronized(lock) {
        require(intentSha256.matches(Regex("[0-9a-f]{64}"))) { "Mutation intent digest is invalid" }
        check(currentAllows(capability)) { "Mutation authorization is unavailable" }
        val leaseIntent = intentSha256
        val leaseGeneration = generation
        val leaseDigest = requireNotNull(active).payloadSha256
        object : MutationExecutionGuard {
            override fun <T> runIfAuthorized(intentSha256: String, operation: () -> T): T = synchronized(lock) {
                check(intentSha256 == leaseIntent) { "Mutation intent changed" }
                check(generation == leaseGeneration && active?.payloadSha256 == leaseDigest && currentAllows(capability)) {
                    "Mutation authorization changed or expired"
                }
                operation()
            }
        }
    }

    private fun currentAllows(capability: MutationCapability): Boolean = capability in (currentGrant()?.capabilities ?: emptySet())

    private fun currentGrant(): VerifiedMutationAuthorization? {
        val sample = checkedClock() ?: return null
        val grant = active ?: return null
        val observedWall = maxOf(sample.wall, requireNotNull(highWater).wallSeconds)
        if (observedWall >= grant.expiresAt || sample.elapsed < seenStarted || sample.elapsed >= seenDeadline) {
            invalidateLocked()
            return null
        }
        return grant
    }

    private data class ClockSample(val wall: Long, val elapsed: Long)

    /** Never hand off with a time sample taken before potentially blocking durable I/O. */
    private fun checkedClock(): ClockSample? {
        repeat(3) {
            val stored = highWater ?: return null
            val now = clock.wallSeconds()
            val elapsed = clock.elapsedMillis()
            if (now < stored.wallSeconds - 60 || elapsed < 0) {
                invalidateLocked()
                return null
            }
            if (stored.revision > 0 && now > stored.wallSeconds) {
                val next = stored.copy(wallSeconds = now)
                if (!runCatching { persistence.write(next.encode()) }.getOrDefault(false)) {
                    highWater = null
                    invalidateLocked()
                    return null
                }
                highWater = next
                // Re-sample after the save, including expiry observed during its latency.
            } else {
                return ClockSample(now, elapsed)
            }
        }
        // A clock/store that cannot stabilize must not keep the mutation path waiting.
        invalidateLocked()
        return null
    }

    private fun invalidateLocked() {
        active = null
        generation++
    }

    private data class HighWater(val revision: Long, val digest: String, val wallSeconds: Long) {
        fun encode(): String = JsonObject().apply {
            addProperty("revision", revision.toString())
            addProperty("digest", digest)
            addProperty("wallSeconds", wallSeconds.toString())
        }.toString()

        companion object {
            fun decode(value: String?): HighWater {
                if (value == null) return HighWater(0, "", 0)
                require(value.length <= 256)
                val obj = JsonParser.parseString(value).asJsonObject
                require(obj.keySet() == setOf("revision", "digest", "wallSeconds"))
                val result = HighWater(
                    canonicalDecimal(obj.get("revision").asString), obj.get("digest").asString,
                    canonicalDecimal(obj.get("wallSeconds").asString)
                )
                require(result.revision > 0 && result.digest.matches(Regex("[0-9a-f]{64}")))
                require(result.encode() == value)
                return result
            }
        }
    }
}
