package jp.co.soramitsu.feature_wallet_impl.data.storage

import jp.co.soramitsu.common.data.storage.Preferences

private const val TRANSACTIONS_CURSOR_KEY = "TRANSACTIONS_CURSOR_KEY"

class TransferCursorStorage(
    private val preferences: Preferences
) {

    fun saveFirstPageNextCursor(address: String, cursor: String?) {
        preferences.putString(cursorKey(address), cursor)
    }

    fun getFirstPageNextCursor(address: String): String? = preferences.getString(cursorKey(address))

    private fun cursorKey(address: String) = TRANSACTIONS_CURSOR_KEY + address
}
