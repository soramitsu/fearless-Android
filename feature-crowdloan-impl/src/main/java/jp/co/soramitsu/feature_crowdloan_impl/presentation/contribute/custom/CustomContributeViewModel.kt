package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.common.validation.CompositeValidation
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_KEY
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_BONUS_URL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_CROWDLOAN_INFO_URL
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.CrowdloanNotEndedValidation
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.additionalOnChainSubmission
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.CONTRIBUTE
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.CONTRIBUTE_CONFIRM
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS_CONFIRM
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamCrowdloanStep.TERMS_CONFIRM_SUCCESS
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_api.domain.validation.EnoughToPayFeesValidation
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class CustomContributeViewModel @Inject constructor(
    private val customContributeManager: CustomContributeManager,
    private val router: CrowdloanRouter,
    accountUseCase: SelectedAccountUseCase,
    addressModelGenerator: AddressIconGenerator,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    @Named("CrowdloanAssetUseCase") assetUseCase: AssetUseCase,
    @Named("CrowdloanFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: ContributeValidationSystem,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    Browserable,
    FeeLoaderMixin by feeLoaderMixin {

    val payload = savedStateHandle.get<CustomContributePayload>(KEY_PAYLOAD)!!

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    val customFlowType = payload.parachainMetadata.flow?.name!!

    private val _viewStateFlow = MutableStateFlow(customContributeManager.createNewState(customFlowType, viewModelScope, payload))
    val viewStateFlow: Flow<CustomContributeViewState> = _viewStateFlow

    val selectedAddressModelFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == TERMS_CONFIRM }
        .flatMapLatest { accountUseCase.selectedAccountFlow() }
        .map { addressModelGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .share()

    private val _validationProgress = MutableLiveData(false)
    private val _applyingInProgress = MutableLiveData(false)
    private val applyButtonStateLD = _viewStateFlow
        .flatMapLatest { it.applyActionState }
        .asLiveData()

    private val parachainMetadata = mapParachainMetadataFromParcel(payload.parachainMetadata)

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val assetModelFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == CONTRIBUTE }
        .flatMapLatest { assetFlow }
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .share()

    val unlockHintFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == CONTRIBUTE }
        .flatMapLatest { assetFlow }
        .map {
            resourceManager.getString(R.string.crowdloan_unlock_hint, it.token.configuration.symbol)
        }
        .inBackground()
        .share()

    private val crowdloanFlow = contributionInteractor.crowdloanStateFlow(payload.paraId, parachainMetadata)
        .inBackground()
        .share()

    val crowdloanDetailModelFlow = crowdloanFlow.combine(assetFlow) { crowdloan, asset ->
        val token = asset.token

        val raisedDisplay = token.amountFromPlanks(crowdloan.fundInfo.raised).format()
        val capDisplay = token.amountFromPlanks(crowdloan.fundInfo.cap).formatTokenAmount(token.configuration)

        val timeLeft = when (val state = crowdloan.state) {
            Crowdloan.State.Finished -> resourceManager.getString(R.string.transaction_status_completed)
            is Crowdloan.State.Active -> resourceManager.formatDuration(state.remainingTimeInMillis)
        }

        CrowdloanDetailsModel(
            leasePeriod = resourceManager.formatDuration(crowdloan.leasePeriodInMillis),
            leasedUntil = resourceManager.formatDate(crowdloan.leasedUntilInMillis),
            raised = resourceManager.getString(R.string.crowdloan_raised_amount, raisedDisplay, capDisplay),
            timeLeft = timeLeft,
            raisedPercentage = crowdloan.raisedFraction.fractionToPercentage().formatAsPercentage()
        )
    }
        .inBackground()
        .share()

    val enteredAmountFlow = MutableStateFlow("")

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }

    val estimatedRewardFlow = parsedAmountFlow.map { amount ->
        payload.parachainMetadata.let { metadata ->
            val estimatedReward = metadata.rewardRate?.let { amount * it }

            estimatedReward?.formatTokenAmount(metadata.token)
        }
    }.share()

    val enteredEtheriumAddress = _viewStateFlow
        .filterIsInstance<MoonbeamContributeViewState>()
        .flatMapLatest {
            it.enteredEtheriumAddressFlow
        }

    val feeLive = feeLiveData.switchMap { fee ->
        _viewStateFlow
            .filter {
                (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step in listOf(
                    TERMS_CONFIRM, TERMS_CONFIRM_SUCCESS
                )
            }
            .asLiveData()
            .map {
                fee
            }
    }

    val healthFlow = _viewStateFlow
        .filter {
            val currentStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step
            val startStep = payload.step
            currentStep == startStep
        }
        .mapLatest {
            val apiUrl = payload.parachainMetadata.flow?.data?.getString(FLOW_API_URL)
            val apiKey = payload.parachainMetadata.flow?.data?.getString(FLOW_API_KEY)
            apiUrl != null && apiKey != null && contributionInteractor.getHealth(apiUrl, apiKey)
        }
        .inBackground()
        .share()

    val learnCrowdloanModel = _viewStateFlow
        .filter {
            (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == CONTRIBUTE
        }
        .mapLatest {
            payload.parachainMetadata.let {
                LearnMoreModel(
                    text = resourceManager.getString(R.string.crowdloan_learn, it.name),
                    iconLink = it.iconLink
                )
            }
        }

    val learnCrowdloanBonusModel = _viewStateFlow
        .filter {
            _viewStateFlow.value !is MoonbeamContributeViewState
        }
        .mapLatest {
            payload.parachainMetadata.let {
                LearnMoreModel(
                    text = resourceManager.getString(R.string.crowdloan_learn_bonuses, it.name),
                    iconLink = it.iconLink
                )
            }
        }

    fun learnMoreClicked() {
        val parachainLink = (payload.parachainMetadata.flow?.data)?.run {
            getString(FLOW_BONUS_URL) ?: getString(FLOW_CROWDLOAN_INFO_URL)
        } ?: parachainMetadata.website
        openBrowserEvent.value = Event(parachainLink)
    }

    fun signedHashClicked(transactionHash: String) {
        openBrowserEvent.value = Event("https://polkascan.io/polkadot/transaction/$transactionHash")
    }

    fun backClicked() {
        if (payload.parachainMetadata.isMoonbeam) {
            val currentStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step
            // TERMS - starting screen -> go out
            // TERMS_CONFIRM_SUCCESS - signed remark completed, term signed - no need move back -> go out
            // CONTRIBUTE - starting step for user with signed terms - no need to go back -> go out
            val shouldGoOut = currentStep in listOf(TERMS, TERMS_CONFIRM_SUCCESS, CONTRIBUTE)
            if (shouldGoOut) {
                router.back()
            } else {
                launch {
                    val nextStep = currentStep?.previous() ?: TERMS
                    handleMoonbeamFlow(nextStep)
                }
            }
        } else {
            router.back()
        }
    }

    fun applyClicked() {
        launch {
            _applyingInProgress.value = true

            if (payload.parachainMetadata.isMoonbeam) {
                val customContributePayload = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload!!
                val nextStep = customContributePayload.step.next()
                handleMoonbeamFlow(nextStep)
            } else {
                _viewStateFlow.first().generatePayload()
                    .onSuccess {
                        router.setCustomBonus(it)
                        router.back()
                    }
                    .onFailure(::showError)
                _applyingInProgress.value = false
            }
        }
    }

    val applyButtonState = MediatorLiveData<Pair<ApplyActionState, Boolean>>().apply {
        var isValidation = false
        var isApplying = false
        var state: ApplyActionState? = null
        var feeStatus: FeeStatus? = null

        fun handleUpdates() {
            state?.let {
                val inProgress = isValidation || isApplying || feeStatus == FeeStatus.Loading
                value = Pair(it, inProgress)
            }
        }

        addSource(applyButtonStateLD) {
            state = it
            handleUpdates()
        }

        addSource(_validationProgress) {
            isValidation = it
            if (!it) { // called false only in error case -> need to stop applying too
                isApplying = false
            }
            handleUpdates()
        }

        addSource(_applyingInProgress) {
            isApplying = it
            handleUpdates()
        }

        addSource(feeLive) {
            feeStatus = it
            handleUpdates()
        }
    }

    private fun maybeGoToNext(fee: BigDecimal, bonusPayload: BonusPayload? = null, signature: String? = null) {
        launch {
            val contributionAmount = parsedAmountFlow.firstOrNull() ?: return@launch

            val validationPayload = ContributeValidationPayload(
                crowdloan = crowdloanFlow.first(),
                fee = fee,
                asset = assetFlow.first(),
                contributionAmount = contributionAmount
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = validationPayload,
                validationFailureTransformer = { contributeValidationFailure(it, resourceManager) },
                progressConsumer = _validationProgress.progressConsumer()
            ) {
                openConfirmScreen(it, bonusPayload, signature)
            }
        }
    }

    private fun openConfirmScreen(
        validationPayload: ContributeValidationPayload,
        bonusPayload: BonusPayload?,
        signature: String?
    ) = launch {
        val isCorrectAndOld = (_viewStateFlow.value as? MoonbeamContributeViewState)?.isEtheriumAddressCorrectAndOld()
        val confirmContributePayload = ConfirmContributePayload(
            paraId = payload.paraId,
            fee = validationPayload.fee,
            amount = validationPayload.contributionAmount,
            estimatedRewardDisplay = estimatedRewardFlow.firstOrNull(),
            bonusPayload = bonusPayload,
            metadata = payload.parachainMetadata,
            enteredEtheriumAddress = enteredEtheriumAddress.firstOrNull()?.let { it to (isCorrectAndOld?.second?.not() ?: false) },
            signature = signature
        )

        router.openMoonbeamConfirmContribute(confirmContributePayload)
    }

    private suspend fun handleMoonbeamFlow(nextStep: MoonbeamCrowdloanStep = TERMS) {
        val isPrivacyAccepted = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.isPrivacyAccepted ?: (nextStep.step > 0)

        val nextStepPayload = CustomContributePayload(
            payload.chainId,
            payload.paraId,
            payload.parachainMetadata,
            payload.amount,
            payload.previousBonusPayload,
            nextStep,
            isPrivacyAccepted
        )

        if (nextStep == CONTRIBUTE_CONFIRM) {
            val isCorrectAndOld = (_viewStateFlow.value as? MoonbeamContributeViewState)?.isEtheriumAddressCorrectAndOld()

            if (isCorrectAndOld?.first != true) {
                showError(resourceManager.getString(R.string.moonbeam_ethereum_address_incorrect))
                _applyingInProgress.value = false
            } else {
                val amount = parsedAmountFlow.firstOrNull() ?: BigDecimal.ZERO
                val amountPlanks = assetFlow.first().token.planksFromAmount(amount)
                checkBeforePrivateFlow {
                    launch {
                        val signature = (_viewStateFlow.value as? MoonbeamContributeViewState)?.getContributionSignature(amountPlanks)
                        val payloadMoonbeam = (_viewStateFlow.value as? MoonbeamContributeViewState)?.generatePayload()?.getOrNull()
                        feeLoaderMixin.loadFee(
                            coroutineScope = viewModelScope,
                            feeConstructor = { asset ->
                                val additional = if (isCorrectAndOld.second.not()) payloadMoonbeam?.let {
                                    additionalOnChainSubmission(it, customFlowType, BigDecimal.ZERO, customContributeManager)
                                } else null
                                val useBatchAll = additional != null
                                contributionInteractor.estimateFee(payload.paraId, amount, additional, useBatchAll, signature)
                            },
                            onRetryCancelled = ::backClicked,
                            onComplete = {
                                if (it is FeeStatus.Loaded) {
                                    maybeGoToNext(it.feeModel.fee, payloadMoonbeam, signature)
                                } else {
                                    showError(
                                        resourceManager.getString(R.string.fee_not_yet_loaded_title),
                                        resourceManager.getString(R.string.fee_not_yet_loaded_message)
                                    )
                                }
                            }
                        )
                    }
                }
            }
            return
        }

        if (nextStep == TERMS_CONFIRM_SUCCESS) {
            checkBeforeRemarkSignFlow {
                launch {
                    val remark = (_viewStateFlow.value as? MoonbeamContributeViewState)?.doSystemRemark() ?: false
                    if (remark) {
                        showMessage(resourceManager.getString(R.string.common_transaction_submitted))
                        _viewStateFlow.emit(customContributeManager.createNewState(customFlowType, viewModelScope, nextStepPayload))
                    } else {
                        showMessage(resourceManager.getString(R.string.transaction_status_failed))
                    }
                }
            }
        } else {
            _viewStateFlow.emit(customContributeManager.createNewState(customFlowType, viewModelScope, nextStepPayload))
        }

        if (nextStep == TERMS_CONFIRM) {
            (_viewStateFlow.value as? MoonbeamContributeViewState)?.let { viewState ->
                feeLoaderMixin.loadFee(
                    coroutineScope = viewModelScope,
                    feeConstructor = { viewState.getSystemRemarkFee() },
                    onRetryCancelled = ::backClicked
                )
            }
        }
    }

    private suspend fun checkBeforePrivateFlow(block: () -> Unit) {
        val contributionAmount = parsedAmountFlow.firstOrNull() ?: return

        val validationPayload = ContributeValidationPayload(
            crowdloan = crowdloanFlow.first(),
            fee = BigDecimal.ZERO,
            asset = assetFlow.first(),
            contributionAmount = contributionAmount
        )

        validationExecutor.requireValid(
            validationSystem = validationSystem,
            payload = validationPayload,
            validationFailureTransformer = { contributeValidationFailure(it, resourceManager) },
            progressConsumer = _validationProgress.progressConsumer()
        ) {
            block()
        }
    }

    private suspend fun checkBeforeRemarkSignFlow(block: () -> Unit) {
        val fee = (feeLive.value as? FeeStatus.Loaded)?.feeModel?.fee ?: return
        val validationPayload = ContributeValidationPayload(
            crowdloan = crowdloanFlow.first(),
            fee = fee,
            asset = assetFlow.first(),
            contributionAmount = BigDecimal.ZERO
        )

        // todo rework with dagger separate validate systems
        val contributeValidations = (validationSystem.value as CompositeValidation).validations
            .filter {
                it is CrowdloanNotEndedValidation || it is EnoughToPayFeesValidation
            }
        val feeRemarkSystem = ContributeValidationSystem(
            validation = CompositeValidation(contributeValidations)
        )

        validationExecutor.requireValid(
            validationSystem = feeRemarkSystem,
            payload = validationPayload,
            validationFailureTransformer = { contributeValidationFailure(it, resourceManager) },
            progressConsumer = _validationProgress.progressConsumer()
        ) {
            block()
        }
    }

    fun resetProgress() {
        _validationProgress.postValue(false)
        _applyingInProgress.postValue(false)
    }
}
