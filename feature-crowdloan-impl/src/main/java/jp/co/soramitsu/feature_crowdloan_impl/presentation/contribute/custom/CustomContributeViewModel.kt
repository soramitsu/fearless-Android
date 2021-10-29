package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.*
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.*

class CustomContributeViewModel(
    private val customContributeManager: CustomContributeManager,
    val payload: CustomContributePayload,
    private val router: CrowdloanRouter,
    accountUseCase: SelectedAccountUseCase,
    addressModelGenerator: AddressIconGenerator,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    assetUseCase: AssetUseCase,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
) : BaseViewModel(),
    FeeLoaderMixin by feeLoaderMixin {

//    val customFlowType = payload.parachainMetadata.customFlow!!
    val customFlowType = payload.parachainMetadata.flow?.name ?: payload.parachainMetadata.customFlow!!

    private val _viewStateFlow = MutableStateFlow(customContributeManager.createNewState(customFlowType, viewModelScope, payload))
    val viewStateFlow: Flow<CustomContributeViewState> = _viewStateFlow

    val selectedAddressModelFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 1 }
        .flatMapLatest { accountUseCase.selectedAccountFlow() }
        .map { addressModelGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name) }
        .share()

    val applyButtonState = _viewStateFlow
        .flatMapLatest { it.applyActionState }
        .share()

    private val _applyingInProgress = MutableStateFlow(false)
    val applyingInProgress: Flow<Boolean> = _applyingInProgress

    private val parachainMetadata = mapParachainMetadataFromParcel(payload.parachainMetadata)

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val assetModelFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 3 }
        .flatMapLatest { assetFlow }
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .share()

    val unlockHintFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 3 }
        .flatMapLatest { assetFlow }
        .map {
            resourceManager.getString(R.string.crowdloan_unlock_hint, it.token.type.displayName)
        }
        .inBackground()
        .share()

    private val crowdloanFlow = _viewStateFlow
        .filter { (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 3 }
        .flatMapLatest {
            contributionInteractor.crowdloanStateFlow(payload.paraId, parachainMetadata)
                .inBackground()
        }
        .share()

    val crowdloanDetailModelFlow = crowdloanFlow.combine(assetFlow) { crowdloan, asset ->
        val token = asset.token

        val raisedDisplay = token.amountFromPlanks(crowdloan.fundInfo.raised).format()
        val capDisplay = token.amountFromPlanks(crowdloan.fundInfo.cap).formatTokenAmount(token.type)

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

    val feeLive = feeLiveData.switchMap { fee ->
        _viewStateFlow
            .filter {
                (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 1
            }
            .asLiveData()
            .map {
                fee
            }
    }

    init {
        loadFee()
    }


    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { asset ->
                contributionInteractor.estimateFee(payload.paraId, BigDecimal.ZERO, asset.token, null)
            },
            onRetryCancelled = ::backClicked
        )
    }

    fun backClicked() {
        if (payload.isMoonbeam) {
            val currentStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step
            if (currentStep == 0) {
                router.back()
            } else {
                launch {
                    val nextStep = currentStep?.dec() ?: 0
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

            if (payload.isMoonbeam) {
                val nextStep = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step?.inc() ?: 0
                handleMoonbeamFlow(nextStep)
            } else {
                //идём на след стейт
                _viewStateFlow.first().generatePayload()
                    .onSuccess {
                        router.setCustomBonus(it)
                        router.back()
                    }
                    .onFailure(::showError)
            }

            _applyingInProgress.value = false
        }
    }

    private suspend fun handleMoonbeamFlow(nextStep: Int = 0) {
        val isPrivacyAccepted = (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.isPrivacyAccepted ?: (nextStep > 0)

        val nextStepPayload = CustomContributePayload(
            payload.paraId,
            payload.parachainMetadata,
            payload.amount,
            payload.previousBonusPayload,
            nextStep,
            isPrivacyAccepted
        )
        _viewStateFlow.emit(customContributeManager.createNewState(customFlowType, viewModelScope, nextStepPayload))
    }
}
