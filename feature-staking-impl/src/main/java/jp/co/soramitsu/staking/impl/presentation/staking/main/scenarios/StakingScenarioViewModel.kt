package jp.co.soramitsu.staking.impl.presentation.staking.main.scenarios

import jp.co.soramitsu.common.domain.model.StoryGroup
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationFailure
import jp.co.soramitsu.staking.impl.domain.validations.balance.ManageStakingValidationPayload
import jp.co.soramitsu.staking.impl.presentation.staking.alerts.model.AlertModel
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewState
import jp.co.soramitsu.staking.impl.presentation.staking.main.StakingViewStateOld
import jp.co.soramitsu.staking.impl.presentation.staking.main.model.StakingNetworkInfoModel
import jp.co.soramitsu.wallet.impl.domain.model.Token
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.math.BigDecimal

const val PERIOD_MONTH = 30
const val PERIOD_YEAR = 365

interface StakingScenarioViewModel {
    companion object {
        val WARNING_ICON = R.drawable.ic_warning_filled
        val WAITING_ICON = R.drawable.ic_time_24
    }

    val stakingStateFlow: Flow<StakingState>

    @Deprecated("Don't use this method, use the getStakingViewStateFlow instead")
    suspend fun getStakingViewStateFlowOld(): Flow<StakingViewStateOld>

    suspend fun getStakingViewStateFlow(): Flow<StakingViewState>

    suspend fun networkInfo(): Flow<LoadingState<StakingNetworkInfoModel>>
    suspend fun alerts(): Flow<LoadingState<List<AlertModel>>>

    suspend fun getRedeemValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    suspend fun getBondMoreValidationSystem(): ValidationSystem<ManageStakingValidationPayload, ManageStakingValidationFailure>
    fun stakingStoriesFlow(): Flow<List<StoryGroup.Staking>>

    val enteredAmountFlow: MutableStateFlow<BigDecimal?>
}

fun formatAlertTokenAmount(amount: BigDecimal, token: Token): String {
    val formattedFiat = token.fiatAmount(amount)?.formatFiat(token.fiatSymbol)
    val formattedAmount = amount.formatCryptoDetail(token.configuration.symbolToShow)

    return buildString {
        append(formattedAmount)

        formattedFiat?.let {
            append(" ($it)")
        }
    }
}
