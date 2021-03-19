package jp.co.soramitsu.feature_staking_impl.presentation.common

import jp.co.soramitsu.feature_staking_api.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.math.BigDecimal

class StashSetup(
    val alreadyHasStash: Boolean,
    val amount: BigDecimal,
    val rewardDestination: RewardDestination,
)

class SetupStakingSharedState {

    val DEFAULT_AMOUNT = 10.toBigDecimal()

    val selectedValidators = MutableSharedFlow<List<Validator>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var stashSetup: StashSetup = StashSetup(alreadyHasStash = false, DEFAULT_AMOUNT, RewardDestination.Restake)
}
