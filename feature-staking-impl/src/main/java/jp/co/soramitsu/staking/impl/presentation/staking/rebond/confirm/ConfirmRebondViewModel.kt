package jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import javax.inject.Named
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatCryptoFull
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.rebond.RebondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.confirm.ConfirmRebondFragment.Companion.PAYLOAD_KEY
import jp.co.soramitsu.staking.impl.presentation.staking.rebond.rebondValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.requireFee
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@HiltViewModel
class ConfirmRebondViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val rebondInteractor: RebondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val iconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    @Named("StakingFeeLoader") private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    FeeLoaderMixin by feeLoaderMixin,
    Validatable by validationExecutor {

    private val payload = savedStateHandle.get<ConfirmRebondPayload>(PAYLOAD_KEY)!!

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = stakingScenarioInteractor.selectedAccountStakingStateFlow()
        .share()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map {
            val retrieveAmount =
                stakingScenarioInteractor.getRebondAvailableAmount(it, payload.amount)
            mapAssetToAssetModel(
                it,
                resourceManager,
                { retrieveAmount },
                R.string.staking_unbonding_format
            )
        }
        .inBackground()
        .asLiveData()

    val amountFiatFLow = assetFlow.map { asset ->
        asset.token.fiatAmount(payload.amount)?.formatFiat(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val amount = payload.amount.formatCryptoFull()

    val originAddressModelLiveData = accountStakingFlow.map {
        val address = it.executionAddress
        val account = interactor.getProjectedAccount(address)

        val addressModel =
            iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)

        addressModel
    }
        .inBackground()
        .asLiveData()

    init {
        loadFee()
    }

    fun confirmClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    fun originAccountClicked() = launch {
        val originAddressModel = originAddressModelLiveData.value ?: return@launch
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedAddressExplorers(originAddressModel.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = originAddressModel.address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { token ->
                val amountInPlanks = token.planksFromAmount(payload.amount)
                rebondInteractor.estimateFee {
                    stakingScenarioInteractor.rebond(
                        this,
                        amountInPlanks,
                        payload.collatorAddress
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
                rebondAmount = payload.amount,
                controllerAsset = assetFlow.first()
            )

            validationExecutor.requireValid(
                validationSystem = stakingScenarioInteractor.getRebondValidationSystem(),
                payload = payload,
                validationFailureTransformer = { rebondValidationFailure(it, resourceManager) },
                progressConsumer = _showNextProgress.progressConsumer(),
                block = ::sendTransaction
            )
        }
    }

    private fun sendTransaction(validPayload: RebondValidationPayload) = launch {
        val amountInPlanks = validPayload.controllerAsset.token.planksFromAmount(payload.amount)
        val stashState = accountStakingFlow.first()

        rebondInteractor.rebond(stashState) {
            stakingScenarioInteractor.rebond(this, amountInPlanks, payload.collatorAddress)
        }
            .onSuccess {
                showMessage(resourceManager.getString(R.string.common_transaction_submitted))

                router.returnToStakingBalance()
                router.openOperationSuccess(
                    it,
                    validPayload.controllerAsset.token.configuration.chainId
                )
            }
            .onFailure(::showError)

        _showNextProgress.value = false
    }
}
