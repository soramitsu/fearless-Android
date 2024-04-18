package jp.co.soramitsu.staking.impl.presentation.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.account.api.presentation.account.AddressDisplayUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Retriable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.ext.addressOf
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.staking.api.domain.model.RewardDestination
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.api.domain.model.Validator
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.getSelectedChain
import jp.co.soramitsu.staking.impl.domain.setup.BondPayload
import jp.co.soramitsu.staking.impl.domain.setup.SetupStakingInteractor
import jp.co.soramitsu.staking.impl.domain.validations.setup.SetupStakingPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingProcess.ReadyToSubmit.Payload
import jp.co.soramitsu.staking.impl.presentation.common.SetupStakingSharedState
import jp.co.soramitsu.staking.impl.presentation.common.rewardDestination.RewardDestinationModel
import jp.co.soramitsu.staking.impl.presentation.common.validation.stakingValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.staking.impl.scenarios.relaychain.HOURS_IN_DAY
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch

@HiltViewModel
class ConfirmStakingViewModel @Inject constructor(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val scenarioInteractor: StakingScenarioInteractor,
    private val addressDisplayUseCase: AddressDisplayUseCase,
    private val resourceManager: ResourceManager,
    private val setupStakingSharedState: SetupStakingSharedState,
    private val setupStakingInteractor: SetupStakingInteractor,
    private val chainRegistry: ChainRegistry,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val validationExecutor: ValidationExecutor,
    private val clipboardManager: ClipboardManager
) : BaseViewModel(),
    Retriable,
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    init {
        loadFee()
    }

    private val currentProcessState = setupStakingSharedState.get<SetupStakingProcess.ReadyToSubmit<*>>()

    private val payload = currentProcessState.payload

    private val bondPayload = when (payload) {
        is Payload.RelayChain -> BondPayload(payload.amount, payload.rewardDestination)
        is Payload.Parachain -> BondPayload(payload.amount, RewardDestination.Restake)
        is Payload.Full<*> -> BondPayload(payload.amount, payload.rewardDestination)
        else -> null
    }
    private val stateFlow = scenarioInteractor.stakingStateFlow()
        .share()

    private val controllerAddressFlow = flowOf(payload)
        .mapNotNull {
            when (it) {
                is Payload.Parachain -> it.currentAccountAddress
                is Payload.RelayChain -> it.controllerAddress
                is Payload.Full -> it.currentAccountAddress
                else -> {
                    (stateFlow.first() as? StakingState.Stash)?.controllerAddress
                }
            }
        }
        .share()

    private val controllerAssetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelLiveData = controllerAssetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .flowOn(Dispatchers.Default)
        .asLiveData()

    val currentAccountModelLiveData = controllerAddressFlow.map {
        generateDestinationModel(it, addressDisplayUseCase(it))
    }.asLiveData()

    val nominationsLiveData = liveData(Dispatchers.Default) {
        val selectedCount = payload.blockProducers.size
        val maxStakersPerBlockProducer = scenarioInteractor.maxStakersPerBlockProducer()

        emit(resourceManager.getString(R.string.staking_confirm_nominations, selectedCount, maxStakersPerBlockProducer))
    }

    val displayAmountLiveData = flowOf(payload)
        .transform { payload ->
            when (payload) {
                is Payload.Full -> emit(payload.amount)
                is Payload.ExistingStash -> emitAll(controllerAssetFlow.map { it.bonded })
                else -> emit(null)
            }
        }
        .asLiveData()

    val unstakingTime = flow {
        val lockupPeriodInHours = scenarioInteractor.unstakingPeriod()
        val lockupPeriod = if (lockupPeriodInHours > HOURS_IN_DAY) {
            val inDays = lockupPeriodInHours / HOURS_IN_DAY
            resourceManager.getQuantityString(R.plurals.common_days_format, inDays, inDays)
        } else {
            resourceManager.getQuantityString(R.plurals.common_hours_format, lockupPeriodInHours, lockupPeriodInHours)
        }
        emit(
            resourceManager.getString(
                R.string.staking_hint_unstake_format,
                lockupPeriod
            )
        )
    }.inBackground()
        .share()

    val eraHoursLength = flow {
        val hours = scenarioInteractor.stakePeriodInHours()
        emit(resourceManager.getString(R.string.staking_hint_rewards_format, resourceManager.getQuantityString(R.plurals.common_hours_format, hours, hours)))
    }.inBackground()
        .share()

    val rewardDestinationLiveData = flowOf(currentProcessState)
        .map {
            val rewardDestination = when {
                it is SetupStakingProcess.ReadyToSubmit.Parachain -> null
                it.payload is Payload.Full -> it.payload.rewardDestination
                it.payload is Payload.ExistingStash -> runCatching {
                    scenarioInteractor.getRewardDestination(stateFlow.last())
                }.getOrNull()

                else -> null
            }

            rewardDestination?.let { mapRewardDestinationToRewardDestinationModel(it) }
        }
        .inBackground()
        .asLiveData()

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    val selectedCollatorLiveData: LiveData<AddressModel?> = liveData {
        val collators = (currentProcessState as? SetupStakingProcess.ReadyToSubmit.Parachain)?.payload?.blockProducers ?: return@liveData emit(null)
        require(collators.size <= 1)
        val collator = collators.first()
        val addressModel = generateDestinationModel(collator.address, collator.identity?.display)
        emit(addressModel)
    }

    fun confirmClicked() {
        sendTransactionIfValid()
    }

    fun backClicked() {
        setupStakingSharedState.set(currentProcessState.previous())
        router.back()
    }

    fun originAccountClicked() {
        viewModelScope.launch {
            interactor.getSelectedAccountProjection()?.let { account ->
                val chainId = controllerAssetFlow.first().token.configuration.chainId
                val chain = chainRegistry.getChain(chainId)
                val supportedExplorers = chain.explorers.getSupportedAddressExplorers(account.address)
                val externalActionsPayload = ExternalAccountActions.Payload(
                    value = account.address,
                    chainId = chainId,
                    chainName = chain.name,
                    explorers = supportedExplorers
                )

                externalAccountActions.showExternalActions(externalActionsPayload)
            }
        }
    }

    fun payoutAccountClicked() = launch {
        val payoutDestination = rewardDestinationLiveData.value as? RewardDestinationModel.Payout ?: return@launch
        val chainId = controllerAssetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedAddressExplorers(payoutDestination.destination.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = payoutDestination.destination.address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    fun nominationsClicked() {
        router.openConfirmNominations()
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            viewModelScope,
            feeConstructor = {
                when (currentProcessState) {
                    is SetupStakingProcess.ReadyToSubmit.Stash -> {
                        setupStakingInteractor.calculateSetupStakingFee(
                            controllerAddress = controllerAddressFlow.first(),
                            validatorAccountIds = currentProcessState.payload.blockProducers.map(Validator::accountIdHex),
                            bondPayload = bondPayload
                        )
                    }
                    is SetupStakingProcess.ReadyToSubmit.Parachain -> {
                        val collator = currentProcessState.payload.blockProducers.first()
                        val amount = bondPayload?.amount ?: error("Amount cant be null")
                        val delegationCount = when (val state = scenarioInteractor.stakingStateFlow().first()) {
                            is StakingState.Parachain.Delegator -> state.delegations.size
                            is StakingState.Parachain.Collator -> 0 // todo add collators support
                            is StakingState.Parachain.None -> 0
                            else -> 0
                        }
                        setupStakingInteractor.estimateFinalParachainFee(collator, it.planksFromAmount(amount), delegationCount)
                    }
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    private suspend fun mapRewardDestinationToRewardDestinationModel(
        rewardDestination: RewardDestination
    ): RewardDestinationModel {
        return when (rewardDestination) {
            is RewardDestination.Restake -> RewardDestinationModel.Restake
            is RewardDestination.Payout -> {
                val chain = interactor.getSelectedChain()
                val address = chain.addressOf(rewardDestination.targetAccountId)
                val name = addressDisplayUseCase(address)

                val addressModel = generateDestinationModel(address, name)

                RewardDestinationModel.Payout(addressModel)
            }
        }
    }

    private fun sendTransactionIfValid() = requireFee { fee ->
        launch {
            val payload = SetupStakingPayload(
                maxFee = fee,
                controllerAddress = controllerAddressFlow.first(),
                bondAmount = bondPayload?.amount,
                asset = controllerAssetFlow.first(),
                isAlreadyNominating = payload !is Payload.Full // not full flow => already nominating
            )

            validationExecutor.requireValid(
                validationSystem = scenarioInteractor.getSetupStakingValidationSystem(),
                payload = payload,
                validationFailureTransformer = { stakingValidationFailure(payload, it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(payload)
            }
        }
    }

    private fun sendTransaction(setupStakingPayload: SetupStakingPayload) = launch {
        val setupResult = when (currentProcessState) {
            is SetupStakingProcess.ReadyToSubmit.Stash -> {
                setupStakingInteractor.setupStaking(
                    controllerAddress = setupStakingPayload.controllerAddress,
                    validatorAccountIds = currentProcessState.payload.blockProducers.map(Validator::accountIdHex),
                    bondPayload = bondPayload
                )
            }
            is SetupStakingProcess.ReadyToSubmit.Parachain -> {
                val token = controllerAssetFlow.first().token
                val collator = currentProcessState.payload.blockProducers.first()
                val amount = bondPayload?.amount ?: error("Amount cant be null")
                val delegationCount = when (val state = scenarioInteractor.stakingStateFlow().first()) {
                    is StakingState.Parachain.Delegator -> state.delegations.size
                    is StakingState.Parachain.Collator -> 0 // todo add collators support
                    is StakingState.Parachain.None -> 0
                    else -> 0
                }
                val accountAddress = (scenarioInteractor.stakingStateFlow().first() as StakingState.Parachain).accountAddress
                setupStakingInteractor.setupStaking(collator, token.planksFromAmount(amount), delegationCount, accountAddress)
            }
        }

        _showNextProgress.value = false

        if (setupResult.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            setupStakingSharedState.set(currentProcessState.finish())

            router.returnToMain()
        } else {
            showError(setupResult.requireException())
        }
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private suspend fun generateDestinationModel(address: String, name: String?): AddressModel {
        return interactor.getAddressModel(address, AddressIconGenerator.SIZE_MEDIUM, name)
    }

    fun onSelectedCollatorCopyClicked() {
        selectedCollatorLiveData.value?.address?.let {
            clipboardManager.addToClipboard(it)
            showMessage(resourceManager.getString(R.string.common_copied))
        }
    }
}
