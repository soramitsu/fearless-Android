package jp.co.soramitsu.common.data.storage.encrypt

import jp.co.soramitsu.common.data.storage.PinPreferenceSnapshot

/** Opaque proof-time state. It contains no new PIN and is never persisted. */
class PinResetSnapshot internal constructor(
    internal val typed: PinPreferenceSnapshot? = null,
    internal val unencrypted: EncryptedPreferenceSnapshot? = null
) {
    fun matches(other: PinResetSnapshot): Boolean = when {
        typed != null -> other.typed != null && typed.matches(other.typed)
        other.typed != null -> false
        unencrypted == null -> other.unencrypted == null
        else -> other.unencrypted != null && unencrypted.matchesExactSnapshot(other.unencrypted)
    }
}
