package jp.co.soramitsu.polkaswap.api.models

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_polkaswap_api.R

enum class Market(
    val marketName: String,
    @StringRes
    val descriptionId: Int
) {
    SMART(
        marketName = "SMART",
        descriptionId = R.string.polkaswap_market_smart_description
    ),
    TBC(
        marketName = "TBC",
        descriptionId = R.string.polkaswap_market_tbc_description
    ),
    XYK(
        marketName = "XYK",
        descriptionId = R.string.polkaswap_market_xyk_description
    )
}

fun List<String>.toMarkets(): List<Market> = Market.values().filter { it.marketName in this }

fun List<Market>.names(): List<String> =
    filter {
        it != Market.SMART
    }.map {
        it.marketName
    }

fun List<Market>.toFilters(): String =
    if (isEmpty() || this[0] == Market.SMART) "Disabled" else "AllowSelected"
