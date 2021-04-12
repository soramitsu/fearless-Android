package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenChange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PayoutsListViewModel(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
) : BaseViewModel(), Retriable {

    override val retryEvent: MutableLiveData<Event<RetryPayload>> = MutableLiveData()

    private val _payoutsStatisticsState: MutableLiveData<LoadingState<PendingPayoutsStatisticsModel>> = MutableLiveData(LoadingState.Loading())
    val payoutsStatisticsState: LiveData<LoadingState<PendingPayoutsStatisticsModel>> = _payoutsStatisticsState

    init {
        loadPayouts()
    }

    private fun loadPayouts() {
        launch(Dispatchers.Default) {
            val result = interactor.calculatePendingPayouts()

            if (result.isSuccess) {
                val statisticsModels = convertToUiModel(result.requireValue())

                _payoutsStatisticsState.postValue(LoadingState.Loaded(statisticsModels))
            } else {
                retryEvent.value = Event(
                    RetryPayload(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = resourceManager.getString(R.string.common_error_general_message),
                        onRetry = ::loadPayouts,
                        onCancel = ::backClicked
                    )
                )
            }
        }
    }

    fun backClicked() {
        router.back()
    }

    fun payoutAllClicked() {
        // TODO
    }

    private suspend fun convertToUiModel(
        statistics: PendingPayoutsStatistics
    ): PendingPayoutsStatisticsModel {
        val token = interactor.currentAssetFlow().first().token
        val totalAmount = token.amountFromPlanks(statistics.totalAmountInPlanks).formatTokenAmount(token.type, 6)

        val payouts = statistics.payouts.map { mapPayoutToPayoutModel(token, it) }

        return PendingPayoutsStatisticsModel(
            payouts = payouts,
            payoutAllTitle = resourceManager.getString(R.string.staking_payout_all, totalAmount)
        )
    }

    private fun mapPayoutToPayoutModel(token: Token, payout: PendingPayout): PendingPayoutModel {
        return with(payout) {
            val amount = token.amountFromPlanks(amountInPlanks)

            PendingPayoutModel(
                validatorTitle = validatorInfo.nameOrAddress,
                daysLeft = resourceManager.getQuantityString(R.plurals.staking_payouts_days_left, daysLeft, daysLeft),
                daysLeftColor = if (closeToExpire) R.color.error_red else R.color.white_64,
                // TODO decide on precision
                amount = amount.formatTokenChange(token.type, isIncome = true, precision = 6),
                amountFiat = token.fiatAmount(amount)?.formatAsCurrency()
            )
        }
    }

    fun payoutClicked(index: Int) {
        // TODO
    }
}
