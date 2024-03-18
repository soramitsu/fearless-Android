package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.flow.filterNotNull

private const val SELECTED_FIAT_KEY = "selectedFiat"
private const val DEFAULT_SELECTED_FIAT = "usd"

class SelectedFiat(private val preferences: Preferences) {
    private var previous = get()
    fun flow() = preferences.stringFlow(SELECTED_FIAT_KEY) { get() }.filterNotNull()
    fun get() = preferences.getString(SELECTED_FIAT_KEY, DEFAULT_SELECTED_FIAT)
    fun isUsd() = get() == DEFAULT_SELECTED_FIAT

    fun set(value: String) {
        previous = get()
        preferences.putString(SELECTED_FIAT_KEY, value)
    }

    fun notifySyncFailed() {
        preferences.putString(SELECTED_FIAT_KEY, previous)
    }
}
