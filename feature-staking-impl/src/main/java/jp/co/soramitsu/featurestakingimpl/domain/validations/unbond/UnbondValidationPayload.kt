package jp.co.soramitsu.featurestakingimpl.domain.validations.unbond

import java.math.BigDecimal
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.featurewalletapi.domain.model.Asset

data class UnbondValidationPayload(
    val stash: StakingState,
    val fee: BigDecimal,
    val amount: BigDecimal,
    val asset: Asset,
    val collatorAddress: String? = null,
    val shouldChillBeforeUnbond: Boolean = false
)
