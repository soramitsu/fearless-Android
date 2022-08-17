package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Browserable
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.formatAsPercentage
import jp.co.soramitsu.common.utils.fractionToPercentage
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_CROWDLOAN_INFO_URL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_TERMS_URL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_TOTAL_REWARD
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.additionalOnChainSubmission
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.ApplyActionState
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType.DirectDOT
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType.LcDOT
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.interlay.InterlayBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

private const val DEBOUNCE_DURATION_MILLIS = 500

sealed class CustomContributionState {

    object NotSupported : CustomContributionState()

    class Active(val customFlow: String, val payload: BonusPayload, val tokenName: String)

    object Inactive : CustomContributionState()
}

@HiltViewModel
class CrowdloanContributeViewModel @Inject constructor(
    private val router: CrowdloanRouter,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    @Named("CrowdloanAssetUseCase") assetUseCase: AssetUseCase,
    private val validationExecutor: ValidationExecutor,
    @Named("CrowdloanFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val validationSystem: ContributeValidationSystem,
    private val customContributeManager: CustomContributeManager,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    Browserable,
    FeeLoaderMixin by feeLoaderMixin {

    private val payload = savedStateHandle.get<ContributePayload>(KEY_PAYLOAD)!!

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val parachainMetadata = payload.parachainMetadata?.let(::mapParachainMetadataFromParcel)

    private val _showNextProgress = MutableLiveData(false)

    val privacyAcceptedFlow = MutableStateFlow(payload.parachainMetadata?.isAcala != true)
    val contributionTypeFlow = MutableStateFlow(DirectDOT.ordinal)

    val applyButtonState = MediatorLiveData<Pair<ApplyActionState, Boolean>>().apply {
        var isPrivacyAccepted = false
        var isProgress = false

        fun handleUpdates() {
            val state: ApplyActionState = when {
                !isPrivacyAccepted -> ApplyActionState.Unavailable(reason = resourceManager.getString(R.string.crowdloan_agreement_required))
                else -> ApplyActionState.Available
            }
            value = state to isProgress
        }

        addSource(privacyAcceptedFlow.asLiveData()) { isPrivacyAccepted = it; handleUpdates() }
        addSource(_showNextProgress) { isProgress = it; handleUpdates() }
    }

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .share()

    val enteredAmountFlow = MutableStateFlow("")

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }

    private val customContributionFlow = flow {
        val customFlow = payload.parachainMetadata?.flow?.name

        if (
            customFlow != null &&
            customContributeManager.isCustomFlowSupported(customFlow)
        ) {
            emit(CustomContributionState.Inactive)

            val source = router.customBonusFlow.map {
                if (it != null) {
                    CustomContributionState.Active(customFlow, it, parachainMetadata!!.token)
                } else {
                    CustomContributionState.Inactive
                }
            }

            emitAll(source)
        } else {
            emit(CustomContributionState.NotSupported)
        }
    }
        .share()

    val bonusDisplayFlow = combine(
        customContributionFlow,
        parsedAmountFlow
    ) { contributionState, amount ->
        when (contributionState) {
            is CustomContributionState.Active -> {
                when (contributionState.payload) {
                    is InterlayBonusPayload,
                    is AstarBonusPayload,
                    is AcalaBonusPayload -> ""
                    else -> {
                        val bonus = contributionState.payload.calculateBonus(amount)

                        bonus?.formatTokenAmount(contributionState.tokenName)
                    }
                }
            }

            is CustomContributionState.Inactive -> resourceManager.getString(R.string.crowdloan_empty_bonus_title)

            else -> null
        }
    }
        .inBackground()
        .share()

    val unlockHintFlow = assetFlow.map {
        resourceManager.getString(R.string.crowdloan_unlock_hint, it.token.configuration.symbol)
    }
        .inBackground()
        .share()

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val title = payload.parachainMetadata?.let {
        "${it.name} (${it.token})"
    } ?: payload.paraId.toString()

    val learnCrowdloanModel = payload.parachainMetadata?.let {
        LearnMoreModel(
            text = resourceManager.getString(R.string.crowdloan_learn, it.name),
            iconLink = it.iconLink
        )
    }

    private val crowdloanFlow = contributionInteractor.crowdloanStateFlow(payload.paraId, parachainMetadata)
        .inBackground()
        .share()

    private val rewardRateFlow: Flow<BigDecimal?> = when {
        payload.parachainMetadata?.isAcala == true -> {
            crowdloanFlow.distinctUntilChanged().map { crowdloan ->
                val totalDotContributed = assetFlow.firstOrNull()?.token?.amountFromPlanks(crowdloan.fundInfo.raised)
                val totalReward = payload.parachainMetadata.flow?.data?.getString(FLOW_TOTAL_REWARD)?.toBigDecimalOrNull() ?: 170_000_000.toBigDecimal()
                totalReward.divide(totalDotContributed, 10, RoundingMode.HALF_UP)
            }
        }

        else -> flow {
            emit(payload.parachainMetadata?.rewardRate)
        }
    }

    val estimatedRewardFlow = rewardRateFlow.combine(parsedAmountFlow) { rewardRate, amount ->
        payload.parachainMetadata?.let { metadata ->
            when {
                metadata.isAcala -> null
                else -> {
                    val estimatedReward = rewardRate?.let { amount * it }
                    estimatedReward?.formatTokenAmount(metadata.token)
                }
            }
        }
    }
        .onStart { emit(null) }
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

    init {
        listenFee()
    }

    fun nextClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    fun bonusClicked() {
        launch {
            val customContributePayload = CustomContributePayload(
                chainId = payload.chainId,
                paraId = payload.paraId,
                parachainMetadata = payload.parachainMetadata!!,
                amount = parsedAmountFlow.first(),
                previousBonusPayload = router.latestCustomBonus
            )

            router.openCustomContribute(customContributePayload)
        }
    }

    @OptIn(ExperimentalTime::class)
    private fun listenFee() {
        combine(
            parsedAmountFlow.debounce(DEBOUNCE_DURATION_MILLIS.milliseconds),
            customContributionFlow,
            ::Pair
        )
            .onEach { (amount, bonusState) ->
                loadFee(amount, bonusState as? CustomContributionState.Active)
            }
            .launchIn(viewModelScope)
    }

    private fun loadFee(amount: BigDecimal, bonusActiveState: CustomContributionState.Active?) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = {
                val additionalSubmission = bonusActiveState?.let {
                    additionalOnChainSubmission(it.payload, it.customFlow, amount, customContributeManager)
                }
                val useBatchAll = additionalSubmission != null && payload.parachainMetadata?.isInterlay != true
                contributionInteractor.estimateFee(payload.paraId, amount, additionalSubmission, useBatchAll)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun requireFee(block: (BigDecimal) -> Unit) = feeLoaderMixin.requireFee(
        block,
        onError = { title, message -> showError(title, message) }
    )

    private fun maybeGoToNext() = requireFee { fee ->
        launch {
            val contributionAmount = parsedAmountFlow.firstOrNull() ?: return@launch
            val customMinContribution = when {
                parachainMetadata?.isAcala == true && contributionTypeFlow.firstOrNull() == LcDOT.ordinal -> {
                    1.toBigDecimal()
                }
                else -> null
            }

            val validationPayload = ContributeValidationPayload(
                crowdloan = crowdloanFlow.first(),
                fee = fee,
                asset = assetFlow.first(),
                contributionAmount = contributionAmount,
                customMinContribution = customMinContribution
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = validationPayload,
                validationFailureTransformer = { contributeValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                _showNextProgress.value = false

                openConfirmScreen(it)
            }
        }
    }

    private fun openConfirmScreen(
        validationPayload: ContributeValidationPayload
    ) = launch {
        val isAcala = payload.parachainMetadata?.isAcala == true
        val contributionType = when {
            isAcala -> contributionTypeFlow.map { index -> AcalaContributionType.values().find { it.ordinal == index } }.firstOrNull() ?: return@launch
            else -> DirectDOT
        }
        val bonusPayload = when {
            isAcala -> (router.latestCustomBonus as? AcalaBonusPayload)?.apply { this.contributionType = contributionType }
            else -> router.latestCustomBonus
        }

        val confirmContributePayload = ConfirmContributePayload(
            paraId = payload.paraId,
            fee = validationPayload.fee,
            amount = validationPayload.contributionAmount,
            estimatedRewardDisplay = estimatedRewardFlow.first(),
            bonusPayload = bonusPayload,
            metadata = payload.parachainMetadata,
            enteredEtheriumAddress = null,
            signature = null,
            contributionType = contributionType
        )

        router.openConfirmContribute(confirmContributePayload)
    }

    fun learnMoreClicked() {
        val parachainLink = payload.parachainMetadata?.flow?.data?.getString(FLOW_CROWDLOAN_INFO_URL) ?: parachainMetadata?.website ?: return
        openBrowserEvent.value = Event(parachainLink)
    }

    fun termsClicked() {
        val termsLink = parachainMetadata?.flow?.data?.getString(FLOW_TERMS_URL) ?: return
        openBrowserEvent.value = Event(termsLink)
    }
}
