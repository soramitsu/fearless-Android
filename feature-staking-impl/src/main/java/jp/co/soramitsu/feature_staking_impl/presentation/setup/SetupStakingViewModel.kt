package jp.co.soramitsu.feature_staking_impl.presentation.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.account.AddressIconGenerator
import jp.co.soramitsu.common.account.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.AppLinksProvider
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.mixin.api.DefaultFailure
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.RetryPayload
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.multipleSourceLiveData
import jp.co.soramitsu.common.validation.DefaultFailureLevel
import jp.co.soramitsu.common.validation.ValidationSystem
import jp.co.soramitsu.common.validation.unwrap
import jp.co.soramitsu.common.view.bottomSheet.list.dynamic.DynamicListBottomSheet.Payload
import jp.co.soramitsu.feature_staking_api.domain.model.StakingAccount
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.data.mappers.mapRewardDestinationModelToRewardDestination
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.model.RewardDestination
import jp.co.soramitsu.feature_staking_impl.domain.model.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.rewards.PeriodReturns
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.domain.setup.validations.StakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.StakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.common.validation.stakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.math.BigDecimal

private const val DESTINATION_SIZE_DP = 24

private const val PERIOD_YEAR = 365

sealed class RewardDestinationModel {

    object Restake : RewardDestinationModel()

    class Payout(val destination: AddressModel) : RewardDestinationModel()
}

class PayoutEstimations(
    val restake: RewardEstimation,
    val payout: RewardEstimation
)

class SetupStakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val maxFeeEstimator: MaxFeeEstimator,
    private val validationSystem: ValidationSystem<SetupStakingPayload, StakingValidationFailure>,
    private val appLinksProvider: AppLinksProvider,
    private val stakingSharedState: StakingSharedState,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation
) : BaseViewModel(),
    Retriable, Validatable, Browserable, FeeLoaderMixin by feeLoaderMixin {

    private val _rewardDestinationLiveData = MutableLiveData<RewardDestinationModel>(RewardDestinationModel.Restake)
    val rewardDestinationLiveData: LiveData<RewardDestinationModel> = _rewardDestinationLiveData

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    override val retryEvent: MutableLiveData<Event<RetryPayload>> = multipleSourceLiveData(
        feeLoaderMixin.retryEvent
    )

    override val validationFailureEvent = MutableLiveData<Event<DefaultFailure>>()
    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelsFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .flowOn(Dispatchers.Default)

    val enteredAmountFlow = MutableStateFlow(stakingSharedState.amount.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency()
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private val rewardCalculator = viewModelScope.async { rewardCalculatorFactory.create() }

    val returnsLiveData = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        val restakeReturns = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, true)
        val payoutReturns = rewardCalculator().calculateReturns(amount, PERIOD_YEAR, false)

        val restakeEstimations = mapReturnsToEstimation(restakeReturns, asset.token)
        val payoutEstimations = mapReturnsToEstimation(payoutReturns, asset.token)

        PayoutEstimations(restakeEstimations, payoutEstimations)
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private val _showDestinationChooserEvent = MutableLiveData<Event<Payload<AddressModel>>>()

    val showDestinationChooserEvent: LiveData<Event<Payload<AddressModel>>> = _showDestinationChooserEvent

    init {
        loadFee()
    }

    override fun validationWarningConfirmed() {
        maybeGoToNext(ignoreWarnings = true)
    }

    fun nextClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    fun restakeClicked() {
        _rewardDestinationLiveData.value = RewardDestinationModel.Restake
    }

    fun learnMoreClicked() {
        openBrowserEvent.value = Event(appLinksProvider.payoutsLearnMore)
    }

    fun payoutDestinationClicked() {
        val selectedDestination = _rewardDestinationLiveData.value as? RewardDestinationModel.Payout ?: return

        viewModelScope.launch {
            val accountsInNetwork = accountsInCurrentNetwork()

            _showDestinationChooserEvent.value = Event(Payload(accountsInNetwork, selectedDestination.destination))
        }
    }

    fun payoutDestinationChanged(newDestination: AddressModel) {
        _rewardDestinationLiveData.value = RewardDestinationModel.Payout(newDestination)
    }

    fun payoutClicked() {
        viewModelScope.launch {
            val currentAccount = interactor.getSelectedAccount()

            _rewardDestinationLiveData.value = RewardDestinationModel.Payout(generateDestinationModel(currentAccount))
        }
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { account, asset ->
                maxFeeEstimator.estimateMaxSetupStakingFee(account.address, asset.token.type)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun maybeGoToNext(
        ignoreWarnings: Boolean = false
    ) = requireFee { fee ->
        _showNextProgress.value = true

        viewModelScope.launch {
            val rewardDestination = mapRewardDestinationModelToRewardDestination(rewardDestinationLiveData.value!!)
            val amount = parsedAmountFlow.first()
            val tokenType = assetFlow.first().token.type

            val payload = SetupStakingPayload(
                amount = amount,
                tokenType = tokenType,
                originAddress = interactor.getSelectedAccount().address,
                maxFee = fee,
                rewardDestination = rewardDestination
            )

            val ignoreLevel = if (ignoreWarnings) DefaultFailureLevel.WARNING else null

            val validationResult = validationSystem.validate(payload, ignoreLevel)

            _showNextProgress.value = false

            validationResult.unwrap(
                onValid = { goToNextStep(rewardDestination, amount, fee) },
                onInvalid = {
                    validationFailureEvent.value = Event(stakingValidationFailure(payload, it, resourceManager))
                },
                onFailure = { showValidationFailedToComplete() }
            )
        }
    }

    private fun goToNextStep(
        rewardDestination: RewardDestination,
        amount: BigDecimal,
        fee: BigDecimal
    ) {
        stakingSharedState.rewardDestination = rewardDestination
        stakingSharedState.amount = amount
        stakingSharedState.fee = fee

        router.openRecommendedValidators()
    }

    private fun showValidationFailedToComplete() {
        retryEvent.value = Event(RetryPayload(
            title = resourceManager.getString(R.string.choose_amount_network_error),
            message = resourceManager.getString(R.string.choose_amount_error_balance),
            onRetry = ::nextClicked
        ))
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private suspend fun rewardCalculator() = rewardCalculator.await()

    private suspend fun accountsInCurrentNetwork(): List<AddressModel> {
        return interactor.getAccountsInCurrentNetwork()
            .map { generateDestinationModel(it) }
    }

    private fun mapReturnsToEstimation(returns: PeriodReturns, token: Token): RewardEstimation {
        return RewardEstimation(returns.gainAmount, returns.gainPercentage, token)
    }

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, DESTINATION_SIZE_DP, account.name)
    }
}