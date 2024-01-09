package jp.co.soramitsu.staking.impl.domain.validations.bond

import jp.co.soramitsu.core.models.Asset
import java.math.BigDecimal

class BondMoreValidationPayload(
    val stashAddress: String,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val chainAsset: Asset
)
