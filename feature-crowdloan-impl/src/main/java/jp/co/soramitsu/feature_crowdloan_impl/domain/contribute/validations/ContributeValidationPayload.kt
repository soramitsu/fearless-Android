package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import java.math.BigDecimal
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset

class ContributeValidationPayload(
    val crowdloan: Crowdloan,
    val asset: Asset,
    val fee: BigDecimal,
    val contributionAmount: BigDecimal,
    val customMinContribution: BigDecimal? = null,
)
