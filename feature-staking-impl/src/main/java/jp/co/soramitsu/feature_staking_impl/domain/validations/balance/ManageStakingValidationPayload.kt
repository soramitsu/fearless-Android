package jp.co.soramitsu.feature_staking_impl.domain.validations.balance

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState

class ManageStakingValidationPayload(
    val stashState: StakingState.Stash
)
