package jp.co.soramitsu.staking.impl.domain.validations.unbond

import java.math.BigDecimal
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.wallet.impl.domain.model.Asset

data class UnbondValidationPayload(
    val stash: StakingState,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val asset: Asset,
    val collatorAddress: String? = null,
    val shouldChillBeforeUnbond: Boolean = false
)
