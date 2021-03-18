package jp.co.soramitsu.feature_staking_impl.domain.model

import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class SetupStakingPayload(
    val amount: BigDecimal,
    val tokenType: Token.Type,
    val originAddress: String,
    val maxFee: BigDecimal,
    val rewardDestination: RewardDestination,
)
