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
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.main.Crowdloan
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.CrowdloanDetailsModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.model.LearnCrowdloanModel
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
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

private const val DEBOUNCE_DURATION_MILLIS = 500

class CrowdloanContributeViewModel(
    private val router: CrowdloanRouter,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    assetUseCase: AssetUseCase,
    private val validationExecutor: ValidationExecutor,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val payload: ContributePayload,
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
        .asLiveData()

    val enteredAmountFlow = MutableStateFlow("")

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() ?: BigDecimal.ZERO }

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
        LearnCrowdloanModel(
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

    val crowdloanDetailModelFlow = contributionInteractor.crowdloanStateFlow(payload.paraId, parachainMetadata)
        .combine(assetFlow) { crowdloan, asset ->
            val token = asset.token

            val raisedDisplay = token.amountFromPlanks(crowdloan.raised).format()
            val capDisplay = token.amountFromPlanks(crowdloan.cap).formatTokenAmount(token.type)

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
        showMessage("Ready to show confirm")
    }

    fun learnMoreClicked() {
        val parachainLink = parachainMetadata?.website ?: return

        openBrowserEvent.value = Event(parachainLink)
    }
}
