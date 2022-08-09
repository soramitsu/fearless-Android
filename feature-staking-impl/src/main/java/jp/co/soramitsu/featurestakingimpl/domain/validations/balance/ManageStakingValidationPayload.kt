package jp.co.soramitsu.featurestakingimpl.domain.validations.balance

import jp.co.soramitsu.featurestakingapi.domain.model.StakingState

class ManageStakingValidationPayload(
    val stashState: StakingState.Stash?
)
