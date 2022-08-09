package jp.co.soramitsu.featurestakingimpl.domain.validations.reedeem

import jp.co.soramitsu.featurewalletapi.domain.model.Asset
import java.math.BigDecimal

class RedeemValidationPayload(
    val fee: BigDecimal,
    val asset: Asset,
    val collatorAddress: String? = null
)
