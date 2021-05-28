package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
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
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.BonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.model.CustomContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnMoreModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.ContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeLoaderMixin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

private const val DEBOUNCE_DURATION_MILLIS = 500

sealed class CustomContributionState {

    object NotSupported : CustomContributionState()

    class Active(val payload: BonusPayload, val tokenName: String)

    object Inactive : CustomContributionState()
}

class CrowdloanContributeViewModel(
    private val router: CrowdloanRouter,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    assetUseCase: AssetUseCase,
    private val validationExecutor: ValidationExecutor,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val payload: ContributePayload,
    private val validationSystem: ContributeValidationSystem,
    private val customContributeManager: CustomContributeManager
) : BaseViewModel(),
    Validatable by validationExecutor,
    Browserable,
    FeeLoaderMixin by feeLoaderMixin {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val parachainMetadata = payload.parachainMetadata?.let(::mapParachainMetadataFromParcel)

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .share()

    val enteredAmountFlow = MutableStateFlow("")

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }

    private val customContributionFlow = flow {
        val customFlow = payload.parachainMetadata?.customFlow

        if (
            customFlow != null &&
            customContributeManager.isCustomFlowSupported(customFlow)
        ) {
            emit(CustomContributionState.Inactive)

            val source = router.customBonusFlow.map {
                if (it != null) CustomContributionState.Active(it, parachainMetadata!!.token) else CustomContributionState.Inactive
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
                val bonus = contributionState.payload.calculateBonus(amount)

                bonus.formatTokenAmount(contributionState.tokenName)
            }

            is CustomContributionState.Inactive -> resourceManager.getString(R.string.crowdloan_bonus_action)

            else -> null
        }
    }
        .inBackground()
        .share()

    val unlockHintFlow = assetFlow.map {
        resourceManager.getString(R.string.crowdloan_unlock_hint, it.token.type.displayName)
    }
        .inBackground()
        .share()

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency()
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

    val estimatedRewardFlow = parsedAmountFlow.map { amount ->
        payload.parachainMetadata?.let { metadata ->
            val estimatedReward = amount * metadata.rewardRate

            estimatedReward.formatTokenAmount(metadata.token)
        }
    }.share()

    private val crowdloanFlow = contributionInteractor.crowdloanStateFlow(payload.paraId, parachainMetadata)
        .inBackground()
        .share()

    val crowdloanDetailModelFlow = crowdloanFlow.combine(assetFlow) { crowdloan, asset ->
        val token = asset.token

        val raisedDisplay = token.amountFromPlanks(crowdloan.fundInfo.raised).format()
        val capDisplay = token.amountFromPlanks(crowdloan.fundInfo.cap).formatTokenAmount(token.type)

        val timeLeft = when (val state = crowdloan.state) {
            Crowdloan.State.Finished -> resourceManager.getString(R.string.common_completed)
            is Crowdloan.State.Active -> resourceManager.formatDuration(state.remainingTimeInMillis)
        }

        CrowdloanDetailsModel(
            leasePeriod = resourceManager.formatDuration(crowdloan.leasePeriodInMillis),
            leasedUntil = resourceManager.formatDate(crowdloan.leasedUntilInMillis),
            raised = resourceManager.getString(R.string.crownloans_raised_format, raisedDisplay, capDisplay),
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
        parsedAmountFlow
            .debounce(DEBOUNCE_DURATION_MILLIS.milliseconds)
            .onEach { loadFee(it) }
            .launchIn(viewModelScope)
    }

    private fun loadFee(amount: BigDecimal) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { asset ->
                contributionInteractor.estimateFee(payload.paraId, amount, asset.token)
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
        val confirmContributePayload = ConfirmContributePayload(
            paraId = payload.paraId,
            fee = validationPayload.fee,
            amount = validationPayload.contributionAmount,
            estimatedRewardDisplay = estimatedRewardFlow.first(),
            bonusPayload = router.latestCustomBonus,
            metadata = payload.parachainMetadata
        )

        router.openConfirmContribute(confirmContributePayload)
    }

    fun learnMoreClicked() {
        val parachainLink = parachainMetadata?.website ?: return

        openBrowserEvent.value = Event(parachainLink)
    }
}
