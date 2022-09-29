package jp.co.soramitsu.staking.impl.domain.validations.balance

import jp.co.soramitsu.staking.api.domain.model.StakingState

class ManageStakingValidationPayload(
    val stashState: StakingState.Stash?
)
