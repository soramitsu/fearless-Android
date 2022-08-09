package jp.co.soramitsu.featureaccountimpl.presentation.account.model

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.featureaccountapi.domain.model.TotalBalance

fun TotalBalance.format(): String {
    return balance.formatAsCurrency(fiatSymbol)
}
