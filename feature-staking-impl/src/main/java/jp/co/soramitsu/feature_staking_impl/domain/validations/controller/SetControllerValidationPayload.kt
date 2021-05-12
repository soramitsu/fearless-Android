package jp.co.soramitsu.feature_staking_impl.domain.validations.controller

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import java.math.BigDecimal

class SetControllerValidationPayload(
    val stash: StakingState.Stash,
    val controllerAddress: String,
    val fee: BigDecimal,
    val asset: BigDecimal
)
