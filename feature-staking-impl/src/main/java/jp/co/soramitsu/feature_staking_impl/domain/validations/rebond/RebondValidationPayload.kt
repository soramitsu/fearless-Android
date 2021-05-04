package jp.co.soramitsu.feature_staking_impl.domain.validations.rebond

import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal

class RebondValidationPayload(
    val controllerAsset: Asset,
    val fee: BigDecimal,
    val rebondAmount: BigDecimal
)
