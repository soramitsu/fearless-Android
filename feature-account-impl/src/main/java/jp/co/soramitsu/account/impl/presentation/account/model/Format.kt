package jp.co.soramitsu.account.impl.presentation.account.model

import jp.co.soramitsu.account.api.domain.model.TotalBalance
import jp.co.soramitsu.account.api.presentation.account.PortfolioValuationStatus
import jp.co.soramitsu.account.api.presentation.account.presentTotalBalance

/** A bare number has no place for a partial-value caption, so only complete values are returned. */
fun TotalBalance.format(): String? {
    val presentation = presentTotalBalance(this, includeHidden = false)
    return presentation.value.takeIf { presentation.status == PortfolioValuationStatus.Complete }
}
