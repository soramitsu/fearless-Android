package jp.co.soramitsu.featurewalletimpl.presentation.common

import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isGone
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R

fun FixedListBottomSheet.currencyItem(@StringRes label: Int, valueText: String, fiatValueText: String?) {
    item(R.layout.item_sheet_currency) { view ->
        view.findViewById<TextView>(R.id.itemCurrencyLabel).setText(label)

        view.findViewById<TextView>(R.id.itemCurrencyValue).text = valueText
        view.findViewById<TextView>(R.id.itemFiatValue).text = fiatValueText.orEmpty()
        view.findViewById<TextView>(R.id.itemFiatValue).isGone = fiatValueText.isNullOrBlank()
    }
}
