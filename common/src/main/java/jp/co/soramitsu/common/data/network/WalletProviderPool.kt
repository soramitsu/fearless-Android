package jp.co.soramitsu.common.data.network

import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

/** Bounded endpoint failover with a sticky successful provider and per-provider cooldowns. */
class WalletProviderPool(private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }) {
    private val lock = Any()
    private var preferred: String? = null
    private val blockedUntil = LinkedHashMap<String, Long>()

    /**
     * Tries each eligible endpoint once. Cancellation and deterministic request failures never retry.
     * Broadcast callers must pass exactly the same signed bytes to every attempt.
     */
    suspend fun <T> execute(
        endpoints: List<String>,
        isTransient: (Throwable) -> Boolean = { false },
        operation: suspend (String) -> T
    ): T {
        require(endpoints.isNotEmpty() && endpoints.size <= MAX_ENDPOINTS)
        val unique = endpoints.distinct()
        val candidates = synchronized(lock) {
            val now = nowMillis()
            unique.sortedBy { if (it == preferred) 0 else 1 }.filter { blockedUntil[it]?.let { deadline -> deadline <= now } ?: true }
        }
        if (candidates.isEmpty()) throw WalletProviderUnavailableException()
        var lastFailure: Throwable? = null
        for (endpoint in candidates) {
            try {
                val result = operation(endpoint)
                synchronized(lock) {
                    preferred = endpoint
                    blockedUntil.remove(endpoint)
                }
                return result
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val http = error as? HttpException
                val transient = error is IOException || http?.code() == 408 || http?.code() == 429 ||
                    http?.code() in 500..599 || isTransient(error)
                if (!transient) throw error
                val cooldown = if (http?.code() == 429) {
                    http.response()?.headers()?.get("Retry-After")?.toLongOrNull()
                        ?.coerceIn(1, MAX_RATE_LIMIT_SECONDS)?.times(1_000) ?: DEFAULT_RATE_LIMIT_MILLIS
                } else {
                    TRANSIENT_COOLDOWN_MILLIS
                }
                synchronized(lock) {
                    // Only bounded runtime routing state is retained; no requests or transaction data.
                    if (blockedUntil.size >= MAX_TRACKED_ENDPOINTS) blockedUntil.remove(blockedUntil.keys.first())
                    blockedUntil[endpoint] = nowMillis() + cooldown
                }
                lastFailure = error
            }
        }
        throw lastFailure ?: WalletProviderUnavailableException()
    }

    private companion object {
        const val MAX_ENDPOINTS = 8
        const val MAX_TRACKED_ENDPOINTS = 64
        const val MAX_RATE_LIMIT_SECONDS = 300L
        const val DEFAULT_RATE_LIMIT_MILLIS = 30_000L
        const val TRANSIENT_COOLDOWN_MILLIS = 1_000L
    }
}
