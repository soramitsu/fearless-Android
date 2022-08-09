package jp.co.soramitsu.staking.impl.domain.validations.rewardDestination

import jp.co.soramitsu.staking.api.domain.model.StakingState
import java.math.BigDecimal

class RewardDestinationValidationPayload(
    val availableControllerBalance: BigDecimal,
    val fee: BigDecimal,
    val stashState: StakingState.Stash
)
