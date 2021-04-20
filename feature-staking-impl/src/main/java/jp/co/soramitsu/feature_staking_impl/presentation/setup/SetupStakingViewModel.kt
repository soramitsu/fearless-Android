package jp.co.soramitsu.feature_staking_impl.presentation.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
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
import jp.co.soramitsu.feature_staking_impl.domain.model.StashSetup
import jp.co.soramitsu.feature_staking_impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.feature_staking_impl.domain.setup.MaxFeeEstimator
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.setup.SetupStakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.feature_staking_impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_staking_impl.presentation.common.validation.stakingValidationFailure
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.RewardSuffix
import jp.co.soramitsu.feature_staking_impl.presentation.mappers.mapPeriodReturnsToRewardEstimation
import jp.co.soramitsu.feature_staking_impl.presentation.staking.model.RewardEstimation
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
    val payout: RewardEstimation,
)

class SetupStakingViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val addressIconGenerator: AddressIconGenerator,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val maxFeeEstimator: MaxFeeEstimator,
    private val validationSystem: ValidationSystem<SetupStakingPayload, SetupStakingValidationFailure>,
    private val appLinksProvider: AppLinksProvider,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
) : BaseViewModel(),
    Retriable,
    Validatable,
    Browserable,
    FeeLoaderMixin by feeLoaderMixin {

    private val currentProcessState = setupStakingSharedState.get<SetupStakingProcess.Stash>()

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

    val enteredAmountFlow = MutableStateFlow(currentProcessState.amount.toString())

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

        val restakeEstimations = mapPeriodReturnsToRewardEstimation(restakeReturns, asset.token, resourceManager, RewardSuffix.APY)
        val payoutEstimations = mapPeriodReturnsToRewardEstimation(payoutReturns, asset.token, resourceManager, RewardSuffix.APR)

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
        setupStakingSharedState.set(currentProcessState.previous())

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
            feeConstructor = { asset ->
                val address = interactor.getSelectedAccount().address

                maxFeeEstimator.estimateMaxSetupStakingFee(asset.token.type, address)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun maybeGoToNext(
        ignoreWarnings: Boolean = false,
    ) = requireFee { fee ->
        _showNextProgress.value = true

        viewModelScope.launch {
            val rewardDestination = mapRewardDestinationModelToRewardDestination(rewardDestinationLiveData.value!!)
            val amount = parsedAmountFlow.first()
            val tokenType = assetFlow.first().token.type
            val controllerAddress = interactor.getSelectedAccount().address

            val stashSetup = StashSetup(rewardDestination, controllerAddress, alreadyHasStash = false)

            val payload = SetupStakingPayload(
                tokenType = tokenType,
                amount = amount,
                stashSetup = stashSetup,
                maxFee = fee
            )

            val ignoreLevel = if (ignoreWarnings) DefaultFailureLevel.WARNING else null

            val validationResult = validationSystem.validate(payload, ignoreLevel)

            _showNextProgress.value = false

            validationResult.unwrap(
                onValid = { goToNextStep(amount, stashSetup) },
                onInvalid = {
                    validationFailureEvent.value = Event(stakingValidationFailure(payload, it, resourceManager))
                },
                onFailure = { showValidationFailedToComplete() }
            )
        }
    }

    private fun goToNextStep(newAmount: BigDecimal, stashSetup: StashSetup) {
        setupStakingSharedState.set(currentProcessState.next(newAmount, stashSetup))

        router.openRecommendedValidators()
    }

    private fun showValidationFailedToComplete() {
        retryEvent.value = Event(
            RetryPayload(
                title = resourceManager.getString(R.string.choose_amount_network_error),
                message = resourceManager.getString(R.string.choose_amount_error_balance),
                onRetry = ::nextClicked
            )
        )
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

    private suspend fun generateDestinationModel(account: StakingAccount): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, DESTINATION_SIZE_DP, account.name)
    }
}
