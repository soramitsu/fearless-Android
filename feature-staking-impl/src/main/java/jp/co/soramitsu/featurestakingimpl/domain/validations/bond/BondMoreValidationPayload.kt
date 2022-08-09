package jp.co.soramitsu.featurestakingimpl.domain.validations.bond

import java.math.BigDecimal
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain

class BondMoreValidationPayload(
    val stashAddress: String,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val chainAsset: Chain.Asset
)
