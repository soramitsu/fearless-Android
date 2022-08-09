package jp.co.soramitsu.featurestakingimpl.domain.validations.rewardDestination

import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import java.math.BigDecimal

class RewardDestinationValidationPayload(
    val availableControllerBalance: BigDecimal,
    val fee: BigDecimal,
    val stashState: StakingState.Stash
)
