package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.account.api.domain.model.TotalBalance

fun TotalBalance.format(): String {
    return balance.formatAsCurrency(fiatSymbol)
}
