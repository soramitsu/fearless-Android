package jp.co.soramitsu.walletconnect.impl.protocol

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** The storage could not be read now (for example the device key store is unavailable). */
internal class WcStorageUnavailableException(
    cause: Throwable?
) : Exception("WalletConnect storage is unavailable", cause)

/**
 * The client's state in memory, written through to [storage] on every change. Readers get a
 * consistent snapshot without waiting; writers are serialized. A change that cannot be stored is
 * not applied, so memory never runs ahead of what survives a restart.
 */
internal class WcStateStore(
    private val storage: WalletConnectStateStorage,
    private val clock: WcClock
) {
    private val mutex = Mutex()

    @Volatile
    var state: WcStoredState = WcStoredState()
        private set

    @Volatile
    var isAvailable: Boolean = false
        private set

    /** Reads the stored state. False when it exists but cannot be read now; it is then left untouched. */
    suspend fun load(): Boolean = mutex.withLock {
        val json = try {
            storage.read()
        } catch (ignored: Exception) {
            isAvailable = false
            return@withLock false
        }
        state = json?.let(::decodeOrNull) ?: WcStoredState()
        isAvailable = true
        true
    }

    /** Applies [change] and stores the result; returns what [change] returned. */
    suspend fun <T> update(change: (WcStoredState) -> Pair<WcStoredState, T>): T = mutex.withLock {
        if (!isAvailable) throw WcStorageUnavailableException(null)
        val (next, result) = change(state)
        val pruned = prune(next)
        if (pruned != state) {
            if (pruned.isEmpty()) storage.clear() else storage.write(JSON.encodeToString(WcStoredState.serializer(), pruned))
            state = pruned
        }
        result
    }

    suspend fun mutate(change: (WcStoredState) -> WcStoredState) = update { change(it) to Unit }

    private fun prune(state: WcStoredState): WcStoredState {
        val now = clock.nowSeconds()
        val handled = state.handled.filterValues { it > now }
        val bounded = if (handled.size <= WcLimits.MAX_HANDLED_ENTRIES) {
            handled
        } else {
            handled.entries.sortedByDescending { it.value }.take(WcLimits.MAX_HANDLED_ENTRIES).associate { it.key to it.value }
        }
        return if (bounded.size == state.handled.size) state else state.copy(handled = bounded)
    }

    private fun WcStoredState.isEmpty() =
        !hasTopics && proposals.isEmpty() && requests.isEmpty() && handled.isEmpty() && clientSeed == null

    private fun decodeOrNull(json: String): WcStoredState? = try {
        JSON.decodeFromString(WcStoredState.serializer(), json).takeIf { it.version == WcStoredState.CURRENT_VERSION }
    } catch (ignored: SerializationException) {
        // Unreadable leftovers only cost a reconnect; they never block the wallet.
        null
    } catch (ignored: IllegalArgumentException) {
        null
    }

    private companion object {
        val JSON = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
