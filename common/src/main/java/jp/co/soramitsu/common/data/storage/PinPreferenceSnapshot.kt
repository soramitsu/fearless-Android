package jp.co.soramitsu.common.data.storage

import jp.co.soramitsu.common.data.storage.encrypt.WalletSecretArchive

/** Exact typed value, including malformed non-string PINs. Never stringify it. */
class PinPreferenceSnapshot internal constructor(value: Any?) {
    internal val rawValue: Any? = if (value is Set<*>) value.toSet() else value
    val exists: Boolean get() = rawValue != null
    val ciphertext: String? get() = rawValue as? String

    fun matches(other: PinPreferenceSnapshot): Boolean = when {
        rawValue is Float && other.rawValue is Float -> rawValue.toRawBits() == other.rawValue.toRawBits()
        else -> rawValue == other.rawValue
    }

    companion object {
        const val PIN_KEY = "pin_code"

        internal fun requirePinKey(key: String) {
            require(key == PIN_KEY || (0 until WalletSecretArchive.MAX_SLOTS).any {
                key == WalletSecretArchive.keyFor(PIN_KEY, it)
            }) { "Only the PIN and its exact retention slots are allowed" }
        }
    }
}
