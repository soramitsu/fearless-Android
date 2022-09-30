package jp.co.soramitsu.crowdloan.impl.domain.contribute.validations

import java.math.BigDecimal
import jp.co.soramitsu.crowdloan.impl.domain.main.Crowdloan
import jp.co.soramitsu.wallet.impl.domain.model.Asset

class ContributeValidationPayload(
    val crowdloan: Crowdloan,
    val asset: Asset,
    val fee: BigDecimal,
    val contributionAmount: BigDecimal,
    val customMinContribution: BigDecimal? = null
)
