package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.common.utils.formatFiat

fun TotalBalance.format(): String {
    return balance.formatFiat(fiatSymbol)
}
