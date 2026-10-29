package jp.co.soramitsu.tonconnect.impl.data

import jp.co.soramitsu.common.data.storage.Preferences

/** Keeps the ledger in the app preferences; values hold only event and request ids. */
class PreferencesBridgeEventLedgerStorage(
    private val preferences: Preferences
) : TonConnectBridgeEventLedger.Storage {

    override fun read(): String? = preferences.getString(KEY)

    override fun write(value: String): Boolean {
        return preferences.replaceStringsDurably(mapOf(KEY to value), emptySet()) &&
            preferences.getString(KEY) == value
    }

    companion object {
        const val KEY = "tonconnect.bridge.ledger.v1"
    }
}
