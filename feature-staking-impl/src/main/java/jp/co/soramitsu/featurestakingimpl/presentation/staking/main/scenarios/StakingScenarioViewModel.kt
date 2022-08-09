package jp.co.soramitsu.featurestakingimpl.presentation.staking.main.scenarios

import java.math.BigDecimal
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.featurestakingapi.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.featurestakingimpl.domain.rewards.RewardCalculator
import jp.co.soramitsu.featurestakingimpl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.featurestakingimpl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.featurestakingimpl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.featurestakingimpl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.featurewalletapi.domain.model.Token
import jp.co.soramitsu.featurewalletapi.presentation.formatters.formatTokenAmount
import kotlinx.coroutines.flow.Flow

const val PERIOD_MONTH = 30
const val PERIOD_YEAR = 365

interface StakingScenarioViewModel {
    companion object {
        val WARNING_ICON = R.drawable.ic_warning_filled
        val WAITING_ICON = R.drawable.ic_time_24
    }

    val stakingStateFlow: Flow<StakingState>
    suspend fun getStakingViewStateFlow(): Flow<StakingViewState>

    suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>>
    suspend fun getRewardCalculator(): RewardCalculator
    suspend fun alerts(): Flow<LoadingState<List<AlertModel>>>

    suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
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
