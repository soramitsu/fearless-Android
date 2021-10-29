package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.map
import jp.co.soramitsu.common.utils.switchMap
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_crowdloan_impl.BuildConfig
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.moonbeam.MoonbeamContributeViewState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import java.math.BigDecimal

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
    Browserable,
    FeeLoaderMixin by feeLoaderMixin {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    //    val customFlowType = payload.parachainMetadata.customFlow!!
    val customFlowType = payload.parachainMetadata.flow?.name ?: payload.parachainMetadata.customFlow!!

//    val apiKey = payload.parachainMetadata.flow?.data?.apiKey!!

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

    val healthFlow = _viewStateFlow
        .filter {
            (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 0
        }
        .mapLatest {
            (_viewStateFlow.value as? MoonbeamContributeViewState)?.getHealth() ?: false
        }
        .inBackground()
        .share()

    val learnCrowdloanModel = _viewStateFlow
        .filter {
            (_viewStateFlow.value as? MoonbeamContributeViewState)?.customContributePayload?.step == 3
        }
        .mapLatest {
            payload.parachainMetadata.let {
                LearnMoreModel(
                    text = resourceManager.getString(R.string.crowdloan_learn, it.name),
                    iconLink = it.iconLink
                )
            }
        }

    init {
        loadFee()
    }

    fun learnMoreClicked() {
        val parachainLink = when (payload.isMoonbeam) {
            true -> BuildConfig.MOONBEAM_CROWDLOAN_INFO_LINK
            else -> parachainMetadata.website
        }

        openBrowserEvent.value = Event(parachainLink)
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
        if (nextStep == 1) {
            feeLoaderMixin.loadFee(
                coroutineScope = viewModelScope,
                feeConstructor = { asset ->
                    val value = (_viewStateFlow.value as? MoonbeamContributeViewState)?.getSystemRemarkFee()!!
                    asset.token.amountFromPlanks(value)
                },
                onRetryCancelled = ::backClicked
            )
        }
    }
}
