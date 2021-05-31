package jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations

import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal

class ContributeValidationPayload(
    val crowdloan: Crowdloan,
    val asset: Asset,
    val fee: BigDecimal,
    val contributionAmount: BigDecimal,
)
