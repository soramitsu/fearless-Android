package jp.co.soramitsu.feature_wallet_impl.data.storage

import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private const val TRANSACTIONS_CURSOR_KEY = "TRANSACTIONS_CURSOR_KEY"

// to distinguish between no cursor and null cursor introduce a separate value for `null` cursor.
// Null value in preferences will correspond to `no cursor` state
private const val NULL_CURSOR = "NULL_CURSOR"

class TransferCursorStorage(
    private val preferences: Preferences
) {

    fun saveCursor(address: String, cursor: String?) {
        val toSave = cursor ?: NULL_CURSOR

        preferences.putString(cursorKey(address), toSave)
    }

    suspend fun awaitCursor(address: String) = preferences.stringFlow(cursorKey(address))
        .filterNotNull() // suspends until cursor is inserted
        .map {
            if (it == NULL_CURSOR) {
                null
            } else {
                it
            }
        }.first()

    private fun cursorKey(address: String) = TRANSACTIONS_CURSOR_KEY + address
}
