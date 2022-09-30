package jp.co.soramitsu.staking.impl.domain.validations.reedeem

import jp.co.soramitsu.wallet.impl.domain.model.Asset
import java.math.BigDecimal

class RedeemValidationPayload(
    val fee: BigDecimal,
    val asset: Asset,
    val collatorAddress: String? = null
)
