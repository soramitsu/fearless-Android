package jp.co.soramitsu.feature_staking_impl.presentation.staking.rebond.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.rebond.RebondInteractor
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.FeeLoaderMixin
import jp.co.soramitsu.feature_staking_impl.presentation.common.fee.requireFee
import jp.co.soramitsu.feature_staking_impl.presentation.common.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.domain.model.amountFromPlanks
import jp.co.soramitsu.feature_wallet_api.domain.model.planksFromAmount
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConfirmRebondViewModel(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val rebondInteractor: RebondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val iconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val payload: ConfirmRebondPayload,
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    FeeLoaderMixin by feeLoaderMixin,
    Validatable by validationExecutor {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = interactor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    private val assetFlow = accountStakingFlow.flatMapLatest {
        interactor.assetFlow(it.controllerAddress)
    }
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager, Asset::unbonding, R.string.staking_unbonding_format) }
        .inBackground()
        .asLiveData()

    val amountFiatFLow = assetFlow.map { asset ->
        asset.token.fiatAmount(payload.amount)?.formatAsCurrency()
    }
        .inBackground()
        .asLiveData()

    val amount = payload.amount.format()

    val originAddressModelLiveData = accountStakingFlow.map {
        val address = it.controllerAddress
        val account = interactor.getAccount(address)

        val addressModel = iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)

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

    fun originAccountClicked() {
        val originAddressModel = originAddressModelLiveData.value ?: return

        val externalActionsPayload = ExternalAccountActions.Payload.fromAddress(originAddressModel.address)

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { asset ->
                val amountInPlanks = asset.token.planksFromAmount(payload.amount)

                val feeInPlanks = rebondInteractor.estimateFee(controllerAddress(), amountInPlanks)

                asset.token.amountFromPlanks(feeInPlanks)
            },
            onRetryCancelled = ::backClicked
        )
    }

    private fun maybeGoToNext() = feeLoaderMixin.requireFee(this) { fee ->
        launch {
//            val asset = assetFlow.first()
//
//            val payload = UnbondValidationPayload(
//                bonded = asset.bonded,
//                available = asset.transferable,
//                stash = accountStakingFlow.first(),
//                fee = payload.fee,
//                amount = payload.amount,
//                tokenType = asset.token.type
//            )
//
//            validationExecutor.requireValid(
//                validationSystem = validationSystem,
//                payload = payload,
//                validationFailureTransformer = { unbondValidationFailure(it, resourceManager) },
//                autoFixPayload = ::unbondPayloadAutoFix,
//                progressConsumer = _showNextProgress.progressConsumer()
//            ) { validPayload ->
//                sendTransaction(validPayload)
//            }
        }
    }

    private fun sendTransaction() = launch {
//        val amountInPlanks = validPayload.tokenType.planksFromAmount(payload.amount)
//
//        val result = rebondInteractor.rebond(validPayload.stash, amountInPlanks)
//
//        _showNextProgress.value = false
//
//        if (result.isSuccess) {
//            showMessage(resourceManager.getString(R.string.common_transaction_submitted))
//
//            router.returnToStakingBalance()
//        } else {
//            showError(result.requireException())
//        }
    }

    private suspend fun controllerAddress() = accountStakingFlow.first().controllerAddress
}
