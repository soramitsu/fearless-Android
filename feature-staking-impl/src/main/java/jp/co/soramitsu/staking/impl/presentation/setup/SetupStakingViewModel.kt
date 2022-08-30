package jp.co.soramitsu.staking.impl.presentation.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.staking.api.data.StakingSharedState
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.impl.data.mappers.mapRewardDestinationModelToRewardDestination
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.rewards.RewardCalculatorFactory
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationMixin
import jp.co.soramitsu.staking.impl.presentation.common.validation.stakingValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

@HiltViewModel
class SetupStakingViewModel @Inject constructor(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val rewardCalculatorFactory: RewardCalculatorFactory,
    private val resourceManager: ResourceManager,
    private val setupStakingInteractor: SetupStakingInteractor,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val rewardDestinationMixin: RewardDestinationMixin.Presentation,
    private val addressIconGenerator: AddressIconGenerator,
    private val stakingSharedState: StakingSharedState
) : BaseViewModel(),
    Retriable,
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    RewardDestinationMixin by rewardDestinationMixin {

    private val currentProcessState = setupStakingSharedState.get<SetupStakingProcess.SetupStep>()

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val _showMinimumStakeAlert = MutableLiveData<Event<String>>()
    val showMinimumStakeAlert: LiveData<Event<String>> = _showMinimumStakeAlert

    private var minimumStake = BigInteger.ZERO

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelsFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .flowOn(Dispatchers.Default)

    val currentStakingType = assetFlow.map { it.token.configuration.staking }

    val enteredAmountFlow: MutableStateFlow<String> = MutableStateFlow("")

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->

        asset.token.fiatAmount(amount)?.formatAsCurrency(asset.token.fiatSymbol)
    }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    private val rewardCalculator = viewModelScope.async {
        val asset = stakingSharedState.assetWithChain.first().asset
        rewardCalculatorFactory.create(asset.staking, asset.chainId)
    }

    val currentAccountAddressModel = liveData {
        interactor.getSelectedAccountProjection()?.let { projection ->
            val addressModel = addressIconGenerator.createEthereumAddressModel(projection.address, AddressIconGenerator.SIZE_MEDIUM, projection.name)
            this.emit(addressModel)
        }
    }

    init {
        loadFee()

        startUpdatingReturns()

        launch {
            val chainId = assetFlow.first().token.configuration.chainId
            minimumStake = stakingScenarioInteractor.getMinimumStake(chainId)

            setupStakingSharedState.setupStakingProcess.filterIsInstance<SetupStakingProcess.SetupStep>()
                .collect {
                    enteredAmountFlow.value = it.amount.toString()
                }
        }
    }

    fun nextClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        viewModelScope.launch {
            setupStakingSharedState.set(currentProcessState.previous())

            router.back()
        }
    }

    private fun startUpdatingReturns() {
        assetFlow.combine(parsedAmountFlow, ::Pair)
            .onEach { (asset, amount) -> rewardDestinationMixin.updateReturns(rewardCalculator(), asset, amount) }
            .launchIn(viewModelScope)
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = {
                when (currentProcessState) {
                    is SetupStakingProcess.SetupStep.Stash -> {
                        interactor.getSelectedAccountProjection()?.address?.let { address ->
                            setupStakingInteractor.estimateMaxSetupStakingFee(address)
                        }.orZero()
                    }
                    is SetupStakingProcess.SetupStep.Parachain -> {
                        setupStakingInteractor.estimateParachainFee()
                    }
                    is SetupStakingProcess.SetupStep.Pool -> BigInteger.ZERO
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    fun minimumStakeConfirmed() {
        launch {
            val asset = assetFlow.first()
            val amount = parsedAmountFlow.first()
            val rewardDestinationModel = rewardDestinationMixin.rewardDestinationModelFlow.first()
            val rewardDestination = mapRewardDestinationModelToRewardDestination(rewardDestinationModel)
            interactor.getSelectedAccountProjection()?.address?.let { currentAccountAddress ->
                goToNextStep(amount, rewardDestination, currentAccountAddress, asset.token.configuration.staking)
            }
        }
    }

    private fun maybeGoToNext() = requireFee { fee ->
        launch {
            val rewardDestinationModel = rewardDestinationMixin.rewardDestinationModelFlow.first()
            val rewardDestination = mapRewardDestinationModelToRewardDestination(rewardDestinationModel)
            val amount = parsedAmountFlow.first()
            val currentAccountAddress = interactor.getSelectedAccountProjection()?.address ?: return@launch
            val asset = assetFlow.first()
            val payload = SetupStakingPayload(
                bondAmount = amount,
                controllerAddress = currentAccountAddress,
                maxFee = fee,
                asset = asset,
                isAlreadyNominating = false // on setup staking screen => not nominator
            )

            validationExecutor.requireValid(
                validationSystem = stakingScenarioInteractor.getSetupStakingValidationSystem(),
                payload = payload,
                validationFailureTransformer = { stakingValidationFailure(payload, it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                _showNextProgress.value = false

                val minimumStakeAmount = payload.asset.token.configuration.amountFromPlanks(minimumStake)
                if (amount < minimumStakeAmount) {
                    _showMinimumStakeAlert.value = Event(minimumStakeAmount.formatTokenAmount(payload.asset.token.configuration.symbol))
                } else {
                    goToNextStep(amount, rewardDestination, currentAccountAddress, asset.token.configuration.staking)
                }
            }
        }
    }

    private fun goToNextStep(
        newAmount: BigDecimal,
        rewardDestination: RewardDestination,
        currentAccountAddress: String,
        stakingType: Chain.Asset.StakingType
    ) {
        viewModelScope.launch {
            val payload = when (stakingType) {
                Chain.Asset.StakingType.PARACHAIN -> SetupStakingProcess.SetupStep.Payload.Parachain(newAmount, currentAccountAddress)
                Chain.Asset.StakingType.RELAYCHAIN -> SetupStakingProcess.SetupStep.Payload.RelayChain(newAmount, rewardDestination, currentAccountAddress)
                else -> SetupStakingProcess.SetupStep.Payload.Pool(newAmount, currentAccountAddress)
            }
            setupStakingSharedState.set(currentProcessState.next(payload))

            when (stakingType) {
                Chain.Asset.StakingType.PARACHAIN -> router.openStartChangeCollators()
                Chain.Asset.StakingType.RELAYCHAIN -> router.openStartChangeValidators()
                else -> Unit
            }
        }
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private suspend fun rewardCalculator() = rewardCalculator.await()
}
