package jp.co.soramitsu.staking.impl.presentation.staking.rebond.custom

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondPayload
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.rebondValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.requireFee
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Named
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEFAULT_AMOUNT = 1
private const val DEBOUNCE_DURATION_MILLIS = 500

@HiltViewModel
class CustomRebondViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val rebondInteractor: RebondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation
) : BaseViewModel(),
    FeeLoaderMixin by feeLoaderMixin,
    Validatable by validationExecutor {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager, Asset::unbonding, R.string.staking_unbonding_format) }
        .inBackground()
        .asLiveData()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val amountFiatFLow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatFiat(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    init {
        listenFee()
    }

    fun confirmClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    @OptIn(FlowPreview::class)
    private fun listenFee() {
        parsedAmountFlow
            .debounce(DEBOUNCE_DURATION_MILLIS.toDuration(DurationUnit.MILLISECONDS))
            .onEach { loadFee(it) }
            .launchIn(viewModelScope)
    }

    private fun loadFee(amount: BigDecimal) {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { token ->
                val amountInPlanks = token.planksFromAmount(amount)
                rebondInteractor.estimateFee {
                    stakingScenarioInteractor.rebond(
                        this,
                        amountInPlanks,
                        null
                    )
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun maybeGoToNext() = feeLoaderMixin.requireFee(this) { fee ->
        launch {
            val payload = RebondValidationPayload(
                fee = fee,
                rebondAmount = parsedAmountFlow.first(),
                controllerAsset = assetFlow.first()
            )

            validationExecutor.requireValid(
                validationSystem = stakingScenarioInteractor.getRebondValidationSystem(),
                payload = payload,
                validationFailureTransformer = { rebondValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer(),
                block = ::openConfirm
            )
        }
    }

    private fun openConfirm(validPayload: RebondValidationPayload) {
        _showNextProgress.value = false

        val confirmPayload = ConfirmRebondPayload(validPayload.rebondAmount, null)

        router.openConfirmRebond(confirmPayload)
    }
}
