package jp.co.soramitsu.featurecrowdloanimpl.domain.contribute.validations

import java.math.BigDecimal
import jp.co.soramitsu.featurecrowdloanimpl.domain.main.Crowdloan
import jp.co.soramitsu.featurewalletapi.domain.model.Asset

class ContributeValidationPayload(
    val crowdloan: Crowdloan,
    val asset: Asset,
    val fee: BigDecimal,
    val contributionAmount: BigDecimal,
    val customMinContribution: BigDecimal? = null
)
