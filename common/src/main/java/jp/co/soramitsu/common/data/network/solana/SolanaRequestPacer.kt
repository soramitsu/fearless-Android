package jp.co.soramitsu.common.data.network.solana

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Spaces the requests sent to an endpoint that refuses bursts with HTTP 429: each request to its host starts at
 * least [SolanaRpcEndpoints.requestSpacingMillis] after the previous one, across every client in the process.
 * Requests to other endpoints are never delayed. Waiting requests are not bounded in number here.
 */
class SolanaRequestPacer(
    private val spacingMillis: (String) -> Long = SolanaRpcEndpoints::requestSpacingMillis,
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private class Gate {
        val lock = Mutex()
        var lastStartMillis: Long? = null
    }

    private val gates = ConcurrentHashMap<String, Gate>()

    suspend fun <T> paced(endpoint: String, request: suspend () -> T): T {
        val spacing = spacingMillis(endpoint)
        if (spacing > 0) {
            // Every spelling of a rate-limited node shares one gate.
            val gate = gates.getOrPut(SolanaRpcEndpoints.rateLimitedHost(endpoint) ?: endpoint) { Gate() }
            gate.lock.withLock {
                val wait = gate.lastStartMillis?.let { it + spacing - nowMillis() } ?: 0
                if (wait > 0) delay(wait)
                gate.lastStartMillis = nowMillis()
            }
        }
        return request()
    }

    companion object {
        /** An endpoint's limit applies to the device's address, so every client shares one pacer. */
        val Shared = SolanaRequestPacer()
    }
}
