package jp.co.soramitsu.feature_account_impl.presentation.account.model

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_account_api.domain.model.TotalBalance

fun TotalBalance.format(): String {
    return balance.formatAsCurrency(fiatSymbol)
}
