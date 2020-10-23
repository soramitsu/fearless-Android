package jp.co.soramitsu.feature_wallet_impl.presentation.common

import androidx.annotation.StringRes
import jp.co.soramitsu.common.view.bottomSheet.BaseFixedListBottomSheet
import jp.co.soramitsu.feature_wallet_impl.util.format
import java.math.BigDecimal

fun BaseFixedListBottomSheet.addCurrencyItem(@StringRes label: Int, value: BigDecimal) {
    addItem(label, value.format())
}