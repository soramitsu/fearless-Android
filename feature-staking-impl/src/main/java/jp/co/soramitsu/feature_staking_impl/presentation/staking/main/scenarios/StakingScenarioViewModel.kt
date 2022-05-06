package jp.co.soramitsu.feature_staking_impl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculator
import jp.co.soramitsu.feature_staking_impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.feature_staking_impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow

const val PERIOD_MONTH = 30
const val PERIOD_YEAR = 365

interface StakingScenarioViewModel {
    companion object {
        val WARNING_ICON = R.drawable.ic_warning_filled
        val WAITING_ICON = R.drawable.ic_time_24
    }

    suspend fun stakingState(): Flow<LoadingState<StakingState>>
    suspend fun getStakingViewStateFlow(): Flow<LoadingState<StakingViewState>>

    suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>>
    suspend fun getRewardCalculator(): RewardCalculator
    suspend fun alerts(): Flow<LoadingState<List<AlertModel>>>

}

fun formatAlertTokenAmount(amount: BigDecimal, token: Token): String {
    val formattedFiat = token.fiatAmount(amount)?.formatAsCurrency(token.fiatSymbol)
    val formattedAmount = amount.formatTokenAmount(token.configuration)

    return buildString {
        append(formattedAmount)

        formattedFiat?.let {
            append(" ($it)")
        }
    }
}
