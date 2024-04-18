package jp.co.soramitsu.staking.impl.presentation.payouts.list

import androidx.lifecycle.MutableLiveData
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.singleReplaySharedFlow
import jp.co.soramitsu.common.utils.withLoading
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.api.data.SyntheticStakingType
import jp.co.soramitsu.staking.api.data.syntheticStakingType
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.model.PendingPayout
import jp.co.soramitsu.staking.impl.domain.model.PendingPayoutsStatistics
import jp.co.soramitsu.staking.impl.domain.rewards.SoraStakingRewardsScenario
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.payouts.confirm.model.ConfirmPayoutPayload
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutModel
import jp.co.soramitsu.staking.impl.presentation.payouts.list.model.PendingPayoutsStatisticsModel
import jp.co.soramitsu.staking.impl.presentation.payouts.model.PendingPayoutParcelable
import jp.co.soramitsu.staking.impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.wallet.api.presentation.formatters.formatSigned
import jp.co.soramitsu.wallet.impl.domain.model.Token
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class PayoutsListViewModel @Inject constructor(
    private val router: StakingRouter,
    private val resourceManager: ResourceManager,
    private val interactor: StakingInteractor,
    private val relayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val soraRewardScenario: SoraStakingRewardsScenario
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
            val result = relayChainScenarioInteractor.calculatePendingPayouts()

            if (result.isSuccess) {
                payoutsStatisticsFlow.emit(result.requireValue())
            } else {
                val throwable = result.requireException()
                val title = if (throwable is TitledException) {
                    throwable.title
                } else {
                    resourceManager.getString(R.string.common_error_general_title)
                }
                val errorMessage = throwable.message ?: resourceManager.getString(R.string.common_undefined_error_message)

                retryEvent.value = Event(
                    RetryPayload(
                        title = title,
                        message = errorMessage,
                        onRetry = ::loadPayouts,
                        onCancel = ::backClicked
                    )
                )
            }
        }
    }

    private suspend fun convertToUiModel(
        statistics: PendingPayoutsStatistics
    ): PendingPayoutsStatisticsModel {
        val currentAsset = interactor.currentAssetFlow().first()
        val syntheticStakingType = currentAsset.token.configuration.syntheticStakingType()

        val token = if(syntheticStakingType == SyntheticStakingType.SORA) {
            soraRewardScenario.getRewardAsset()
        } else {
            currentAsset.token
        }
        val totalAmount = token.amountFromPlanks(statistics.totalAmountInPlanks).formatCryptoDetail(token.configuration.symbol)

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
                amount = amount.formatCryptoDetail(token.configuration.symbol).formatSigned(true),
                amountFiat = token.fiatAmount(amount)?.formatFiat(token.fiatSymbol)
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
