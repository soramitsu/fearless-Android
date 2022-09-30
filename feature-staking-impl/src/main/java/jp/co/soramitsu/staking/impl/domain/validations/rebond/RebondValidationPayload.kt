package jp.co.soramitsu.staking.impl.domain.validations.rebond

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal

class RebondValidationPayload(
    val controllerAsset: Asset,
    val fee: BigDecimal,
    val rebondAmount: BigDecimal
)
