package jp.co.soramitsu.polkaswap.impl.domain.models

import androidx.annotation.StringRes
import jp.co.soramitsu.feature_polkaswap_impl.R

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
