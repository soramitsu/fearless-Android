package jp.co.soramitsu.feature_staking_impl.domain.validations.controller

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import java.math.BigDecimal

class SetControllerValidationPayload(
    val stash: StakingState.Stash,
    val controllerAddress: String,
    val fee: BigDecimal,
    val transferable: BigDecimal
)
