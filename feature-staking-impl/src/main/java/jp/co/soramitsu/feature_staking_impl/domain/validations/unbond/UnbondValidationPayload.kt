package jp.co.soramitsu.feature_staking_impl.domain.validations.unbond

import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import java.math.BigDecimal

data class UnbondValidationPayload(
    val stash: StakingState,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val asset: Asset,
    val collatorAddress: String? = null
)
