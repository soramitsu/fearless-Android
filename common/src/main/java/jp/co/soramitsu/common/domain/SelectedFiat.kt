package jp.co.soramitsu.common.domain

import jp.co.soramitsu.common.data.network.coingecko.FiatCurrency
import jp.co.soramitsu.common.data.storage.Preferences
import kotlinx.coroutines.flow.filterNotNull

private const val SELECTED_FIAT_KEY = "selectedFiat"
private const val DEFAULT_SELECTED_FIAT = "usd"

class SelectedFiat(
    private val preferences: Preferences,
    private val currencyMetadata: (String) -> FiatCurrency? = { null }
) {
    private var previous = get()
    fun flow() = preferences.stringFlow(SELECTED_FIAT_KEY) { get() }.filterNotNull()
    fun get() = preferences.getString(SELECTED_FIAT_KEY, DEFAULT_SELECTED_FIAT)
    fun isUsd() = get() == DEFAULT_SELECTED_FIAT

    /** Display data of a currency: its symbol is what money is shown with. Null for a currency the app does not know. */
    fun metadata(currencyId: String): FiatCurrency? = currencyMetadata(currencyId)

    fun set(value: String) {
        previous = get()
        preferences.putString(SELECTED_FIAT_KEY, value)
    }

    fun notifySyncFailed() {
        preferences.putString(SELECTED_FIAT_KEY, previous)
    }
}
