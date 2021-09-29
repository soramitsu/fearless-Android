package jp.co.soramitsu.feature_staking_impl.presentation.payouts.list

import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.singleReplaySharedFlow
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayout
import jp.co.soramitsu.feature_staking_impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import jp.co.soramitsu.feature_staking_impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenChange
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class PayoutsListViewModel(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
) : BaseViewModel(), Retriable {

    override val retryEvent: MutableLiveData<Event<RetryPayload>> = MutableLiveData()

    private val payoutsStatisticsFlow = singleReplaySharedFlow<PendingPayoutsStatistics>()

    val payoutsStatisticsState = payoutsStatisticsFlow
        .map(::convertToUiModel)
        .withLoading()
        .inBackground()

    init {
        loadPayouts()
    }

    fun backClicked() {
        router.back()
    }

    fun payoutAllClicked() {
        launch {
            val payoutStatistics = payoutsStatisticsFlow.first()

            val payload = ConfirmPayoutPayload(
                totalRewardInPlanks = payoutStatistics.totalAmountInPlanks,
                payouts = payoutStatistics.payouts.map { mapPayoutToParcelable(it) }
            )

            router.openConfirmPayout(payload)
        }
    }

    fun payoutClicked(index: Int) {
        launch {
            val payouts = payoutsStatisticsFlow.first().payouts
            val payout = payouts[index]

            val payoutParcelable = mapPayoutToParcelable(payout)

            router.openPayoutDetails(payoutParcelable)
        }
    }

    private fun loadPayouts() {
        launch {
            val result = interactor.calculatePendingPayouts()

            if (result.isSuccess) {
                payoutsStatisticsFlow.emit(result.requireValue())
            } else {
                val errorMessage = result.requireException().message ?: resourceManager.getString(R.string.common_undefined_error_message)

                retryEvent.value = Event(
                    RetryPayload(
                        title = resourceManager.getString(R.string.common_error_general_title),
                        message = errorMessage,
                        onRetry = ::loadPayouts,
                        onCancel = ::backClicked
                    )
                )
            }
        }
    }

    private suspend fun convertToUiModel(
        statistics: PendingPayoutsStatistics,
    ): PendingPayoutsStatisticsModel {
        val token = interactor.currentAssetFlow().first().token
        val totalAmount = token.amountFromPlanks(statistics.totalAmountInPlanks).formatTokenAmount(token.configuration)

        val payouts = statistics.payouts.map { mapPayoutToPayoutModel(token, it) }

        return PendingPayoutsStatisticsModel(
            payouts = payouts,
            payoutAllTitle = resourceManager.getString(R.string.staking_reward_payouts_payout_all, totalAmount),
            placeholderVisible = payouts.isEmpty()
        )
    }

    private fun mapPayoutToPayoutModel(token: Token, payout: PendingPayout): PendingPayoutModel {
        return with(payout) {
            val amount = token.amountFromPlanks(amountInPlanks)

            PendingPayoutModel(
                validatorTitle = validatorInfo.identityName ?: validatorInfo.address,
                timeLeft = timeLeft,
                createdAt = createdAt,
                daysLeftColor = if (closeToExpire) R.color.error_red else R.color.white_64,
                amount = amount.formatTokenChange(token.configuration, isIncome = true),
                amountFiat = token.fiatAmount(amount)?.formatAsCurrency()
            )
        }
    }

    private fun mapPayoutToParcelable(payout: PendingPayout): PendingPayoutParcelable {
        return with(payout) {
            PendingPayoutParcelable(
                validatorInfo = PendingPayoutParcelable.ValidatorInfoParcelable(
                    address = validatorInfo.address,
                    identityName = validatorInfo.identityName
                ),
                era = era,
                amountInPlanks = amountInPlanks,
                createdAt = createdAt,
                timeLeft = timeLeft,
                closeToExpire = closeToExpire
            )
        }
    }
}
