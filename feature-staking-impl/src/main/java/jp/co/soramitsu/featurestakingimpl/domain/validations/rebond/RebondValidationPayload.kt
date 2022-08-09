package jp.co.soramitsu.featurestakingimpl.domain.validations.rebond

import jp.co.soramitsu.featurewalletapi.domain.model.Asset
import java.math.BigDecimal

class RebondValidationPayload(
    val controllerAsset: Asset,
    val fee: BigDecimal,
    val rebondAmount: BigDecimal
)
