package jp.co.soramitsu.feature_staking_impl.domain.validations.setup

import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class SetupStakingPayload(
    val bondAmount: BigDecimal?,
    val tokenType: Token.Type,
    val maxFee: BigDecimal,
    val controllerAddress: String,
)
