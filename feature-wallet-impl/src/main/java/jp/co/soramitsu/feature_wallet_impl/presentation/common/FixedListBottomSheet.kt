package jp.co.soramitsu.feature_wallet_impl.presentation.common

import androidx.annotation.StringRes
import androidx.core.view.isGone
import jp.co.soramitsu.common.view.bottomSheet.list.fixed.FixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.R
import kotlinx.android.synthetic.main.item_sheet_currency.view.itemCurrencyLabel
import kotlinx.android.synthetic.main.item_sheet_currency.view.itemCurrencyValue
import kotlinx.android.synthetic.main.item_sheet_currency.view.itemFiatValue

fun FixedListBottomSheet.currencyItem(@StringRes label: Int, valueText: String, fiatValueText: String?) {
    item(R.layout.item_sheet_currency) { view ->
        view.itemCurrencyLabel.setText(label)

        view.itemCurrencyValue.text = valueText
        view.itemFiatValue.text = fiatValueText.orEmpty()
        view.itemFiatValue.isGone = fiatValueText.isNullOrBlank()
    }
}
