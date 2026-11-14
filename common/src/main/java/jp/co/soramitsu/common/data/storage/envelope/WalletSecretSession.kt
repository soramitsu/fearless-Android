package jp.co.soramitsu.common.data.storage.envelope

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The wallet secret key SK of this process, held only between a PIN (or
 * backup-proof) unlock and the next
 * [jp.co.soramitsu.common.data.storage.pin.WalletSessionLock.lock], which
 * zeroes it. A new process starts without it.
 *
 * Callers get a copy for one operation and zero it afterwards, so a lock that
 * lands during an operation never changes the bytes that operation uses.
 */
object WalletSecretSession {

    /** How SK reached this session. */
    enum class Origin {
        /** The stored PIN wrap opened with the typed or biometric-unsealed PIN. */
        PIN_WRAP,

        /** A new keyring was created with the PIN just verified or chosen. */
        NEW_KEYRING,

        /** A backup proof opened a backup wrap, then a new PIN was set. */
        BACKUP_PROOF
    }

    private val lock = Any()
    private var key: ByteArray? = null
    private var keyId: ByteArray? = null
    private var origin: Origin? = null

    private val mutableOpenings = MutableStateFlow(0L)

    /** Increments every time SK is (re)opened; the unlock maintenance runs on each change. */
    val openings: StateFlow<Long> = mutableOpenings.asStateFlow()

    fun open(keyId: ByteArray, key: ByteArray, origin: Origin) {
        require(keyId.size == WalletSecretEnvelope.KEY_ID_BYTES && key.size == WalletSecretEnvelope.KEY_BYTES)
        synchronized(lock) {
            this.key?.fill(0)
            this.key = key.copyOf()
            this.keyId = keyId.copyOf()
            this.origin = origin
        }
        mutableOpenings.value = mutableOpenings.value + 1
    }

    /** Zeroes SK. Called by every lock and by tests. */
    fun clear() {
        synchronized(lock) {
            key?.fill(0)
            key = null
            keyId = null
            origin = null
        }
    }

    fun isOpen(): Boolean = synchronized(lock) { key != null }

    fun origin(): Origin? = synchronized(lock) { origin }

    fun keyId(): ByteArray? = synchronized(lock) { keyId?.copyOf() }

    /**
     * Runs [block] with copies of the SK id and SK, zeroed when it returns.
     * Returns null without calling [block] while no SK is held.
     */
    fun <T> withKey(block: (keyId: ByteArray, key: ByteArray) -> T): T? {
        val (id, copy) = synchronized(lock) {
            val current = key ?: return null
            checkNotNull(keyId).copyOf() to current.copyOf()
        }
        return try {
            block(id, copy)
        } finally {
            copy.fill(0)
        }
    }
}
