package jp.co.soramitsu.feature_staking_impl.presentation.staking.bond.select

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.TitleAndMessage
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationFailure
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.bond.BondMoreValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.math.BigDecimal
import kotlin.time.ExperimentalTime
import kotlin.time.milliseconds

private const val DEFAULT_AMOUNT = 1
private const val DEBOUNCE_DURATION_MILLIS = 500

class SelectBondMoreViewModel(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
    private val bondMoreInteractor: BondMoreInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: BondMoreValidationSystem,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = interactor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    private val assetFlow = accountStakingFlow
        .flatMapLatest { interactor.assetFlow(it.stashAddress) }
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency()
    }
        .inBackground()
        .asLiveData()

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
                val amountInPlanks = asset.token.planksFromAmount(amount)

                val feeInPlanks = bondMoreInteractor.estimateFee(controllerAddress(), amountInPlanks)

                asset.token.amountFromPlanks(feeInPlanks)
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
            val payload = BondMoreValidationPayload(
                stashState = accountStakingFlow.first(),
                fee = fee,
                amount = parsedAmountFlow.first(),
                tokenType = assetFlow.first().token.type
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = payload,
                validationFailureTransformer = ::bondMoreValidationFailure,
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                _showNextProgress.value = false

                showMessage("Ready to go to confirm")
            }
        }
    }

    private suspend fun controllerAddress() = accountStakingFlow.first().controllerAddress

    private fun bondMoreValidationFailure(reason: BondMoreValidationFailure): TitleAndMessage {
        return when (reason) {
            BondMoreValidationFailure.NOT_ENOUGH_TO_PAY_FEES -> {
                resourceManager.getString(R.string.common_not_enough_funds_title) to
                    resourceManager.getString(R.string.common_not_enough_funds_message)
            }

            BondMoreValidationFailure.ZERO_BOND -> {
                resourceManager.getString(R.string.common_error_general_title) to
                    resourceManager.getString(R.string.staking_zero_bond_error)
            }

            BondMoreValidationFailure.ELECTION_IS_OPEN -> {
                resourceManager.getString(R.string.staking_nominator_status_election) to
                    resourceManager.getString(R.string.staking_nominator_status_alert_election_message)
            }
        }
    }
}
