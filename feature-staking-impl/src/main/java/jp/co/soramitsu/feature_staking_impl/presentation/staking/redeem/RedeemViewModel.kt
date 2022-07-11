package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private const val DEFAULT_AMOUNT = 1
private const val DEBOUNCE_DURATION_MILLIS = 500

class RedeemViewModel(
    private val router: StakingRouter,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val interactor: StakingInteractor,
    private val redeemInteractor: RedeemInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: RedeemValidationSystem,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val payload: RedeemPayload
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = stakingScenarioInteractor.getStakingStateFlow()
        .share()

    private val assetFlow = accountStakingFlow
        .flatMapLatest { interactor.assetFlow(it.rewardsAddress) }
        .share()

    val enteredAmountFlow = MutableStateFlow(DEFAULT_AMOUNT.toString())

    private val parsedAmountFlow = enteredAmountFlow.mapNotNull { it.toBigDecimalOrNull() }

    val enteredFiatAmountFlow = assetFlow.combine(parsedAmountFlow) { asset, amount ->
        asset.token.fiatAmount(amount)?.formatAsCurrency(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val assetModelFlow = assetFlow
        .map {
            val patternId = stakingScenarioInteractor.overrideUnbondAvailableLabel() ?: R.string.common_available_format
            val retrieveAmount = stakingScenarioInteractor.getUnstakeAvailableAmount(it, payload.collatorAddress?.fromHex())
            mapAssetToAssetModel(
                asset = it,
                resourceManager = resourceManager,
                retrieveAmount = { retrieveAmount },
                patternId = patternId
            )
        }
        .inBackground()
        .asLiveData()

    val accountLiveData = stakingScenarioInteractor.getSelectedAccountAddress()
        .inBackground()
        .asLiveData()

    val collatorLiveData = stakingScenarioInteractor.getCollatorAddress(payload.collatorAddress)
        .inBackground()
        .asLiveData()

    val unbondHint: LiveData<String> = MutableLiveData<String>().apply {
        launch {
            stakingScenarioInteractor.overrideUnbondHint()?.let { postValue(it) }
        }
    }

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
                val asset = assetFlow.first()

                val stashState = accountStakingFlow.first()

                redeemInteractor.estimateFee(stashState) {
                    stakingScenarioInteractor.stakeLess(
                        this,
                        amountInPlanks,
                        stashState,
                        asset.bondedInPlanks.orZero(),
                        payload.collatorAddress
                    )
                }
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
            val asset = assetFlow.first()

            val validationPayload = RedeemValidationPayload(
                fee = fee,
                asset = asset,
                collatorAddress = payload.collatorAddress
            )

            validationExecutor.requireValid(
                validationSystem = validationSystem,
                payload = validationPayload,
                validationFailureTransformer = { redeemValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(it)
            }
        }
    }

    private fun sendTransaction(redeemValidationPayload: RedeemValidationPayload) = launch {
        val result = redeemInteractor.redeem(accountStakingFlow.first()) {
            stakingScenarioInteractor.confirmRevoke(
                this,
                candidate = redeemValidationPayload.collatorAddress,
                stashState = accountStakingFlow.first()
            )
        }

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            when {
                payload.overrideFinishAction != null -> payload.overrideFinishAction.invoke(router)
                else -> router.returnToStakingBalance()
            }
        } else {
            showError(result.requireException())
        }
    }
}
