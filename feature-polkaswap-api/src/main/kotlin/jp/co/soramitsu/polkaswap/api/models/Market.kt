package jp.co.soramitsu.polkaswap.api.models

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_polkaswap_api.R

enum class Market(
    val marketName: String,
    @StringRes
    val descriptionId: Int,
    val backString: String
) {
    SMART(
        marketName = "SMART",
        descriptionId = R.string.polkaswap_market_smart_description,
        backString = ""
    ),
    TBC(
        marketName = "TBC",
        descriptionId = R.string.polkaswap_market_tbc_description,
        backString = "MulticollateralBondingCurvePool"
    ),
    XYK(
        marketName = "XYK",
        descriptionId = R.string.polkaswap_market_xyk_description,
        backString = "XYKPool"
    )
}

fun List<String>.toMarkets(): List<Market> = Market.values().filter { it.backString in this }

fun List<Market>.backStrings(): List<String> =
    filter {
        it != Market.SMART
    }.map {
        it.backString
    }

fun List<Market>.toFilters(): String =
    if (isEmpty() || this[0] == Market.SMART) "Disabled" else "AllowSelected"
