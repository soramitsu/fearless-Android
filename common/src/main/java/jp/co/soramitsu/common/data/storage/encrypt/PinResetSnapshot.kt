package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot

/**
 * Opaque proof-time state. It contains no new PIN and is never persisted.
 * [keyringPinRecord] is the exact stored PIN wrap, so a PIN set elsewhere
 * while a recovery was open invalidates that recovery.
 */
class PinResetSnapshot internal constructor(
    internal val typed: PinPreferenceSnapshot? = null,
    internal val unencrypted: EncryptedPreferenceSnapshot? = null,
    internal val keyringPinRecord: String? = null
) {
    fun matches(other: PinResetSnapshot): Boolean = keyringPinRecord == other.keyringPinRecord && when {
        typed != null -> other.typed != null && typed.matches(other.typed)
        other.typed != null -> false
        unencrypted == null -> other.unencrypted == null
        else -> other.unencrypted != null && unencrypted.matchesExactSnapshot(other.unencrypted)
    }

    internal fun withKeyringPinRecord(record: String?) = PinResetSnapshot(typed, unencrypted, record)
}
