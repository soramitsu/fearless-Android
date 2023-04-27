package jp.co.soramitsu.staking.impl.presentation.staking.redeem

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.shared_utils.extensions.fromHex
import jp.co.soramitsu.staking.api.domain.model.StakingState
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.staking.impl.domain.validations.reedeem.RedeemValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
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

private const val DEBOUNCE_DURATION_MILLIS = 500

@HiltViewModel
class RedeemViewModel @Inject constructor(
    private val router: StakingRouter,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val interactor: StakingInteractor,
    private val redeemInteractor: RedeemInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val iconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    private val payload = savedStateHandle.get<RedeemPayload>(PAYLOAD_KEY)!!

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    val stakingUnlockAmount = MutableSharedFlow<String>().apply {
        launch {
            stakingScenarioInteractor.getStakingBalanceFlow(payload.collatorAddress?.fromHex()).onEach {
                emit(it.redeemable.amount.format())
            }.share()
        }
    }

    private val accountStakingFlow = stakingScenarioInteractor.stakingStateFlow
        .share()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    private val parsedAmountFlow = stakingUnlockAmount.mapNotNull {
        it.toBigDecimalOrNull()
    }

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

    private val originAddressModelLiveData = accountStakingFlow.filterIsInstance<StakingState.Stash>().map {
        val address = it.controllerAddress
        val account = interactor.getProjectedAccount(address)

        iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)
    }.asLiveData()

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
                val stashState = accountStakingFlow.first()

                redeemInteractor.estimateFee(stashState) {
                    stakingScenarioInteractor.confirmRevoke(
                        this,
                        candidate = payload.collatorAddress,
                        stashState = stashState
                    )
                }
            },
            onRetryCancelled = ::backClicked
        )
    }

    fun originAccountClicked() = launch {
        val address = originAddressModelLiveData.value?.address ?: return@launch
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
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
                validationSystem = stakingScenarioInteractor.provideRedeemValidationSystem(),
                payload = validationPayload,
                validationFailureTransformer = { redeemValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer()
            ) {
                sendTransaction(it)
            }
        }
    }

    private fun sendTransaction(redeemValidationPayload: RedeemValidationPayload) = launch {
        val result = redeemInteractor.redeem(accountStakingFlow.first(), redeemValidationPayload.asset) {
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
                result.requireValue().willKillStash -> router.returnToMain()
                else -> router.returnToStakingBalance()
            }
        } else {
            showError(result.requireException())
        }
    }
}
