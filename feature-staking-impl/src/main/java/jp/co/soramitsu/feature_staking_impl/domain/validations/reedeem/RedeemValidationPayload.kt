package jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem

import jp.co.soramitsu.core.model.Node
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal

class RedeemValidationPayload(
    val fee: BigDecimal,
    val asset: Asset,
    val networkType: Node.NetworkType
)
