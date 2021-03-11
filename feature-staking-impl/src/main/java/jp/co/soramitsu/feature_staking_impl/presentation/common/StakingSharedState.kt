package jp.co.soramitsu.feature_staking_impl.presentation.common

import jp.co.soramitsu.feature_staking_api.domain.model.Validator
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.math.BigDecimal

private val DEFAULT_AMOUNT = 10.toBigDecimal()

class StakingSharedState {

    val selectedValidators = MutableSharedFlow<List<Validator>>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    var amount: BigDecimal = DEFAULT_AMOUNT
    var fee: BigDecimal? = null

    var rewardDestination: RewardDestination = RewardDestination.Restake
}