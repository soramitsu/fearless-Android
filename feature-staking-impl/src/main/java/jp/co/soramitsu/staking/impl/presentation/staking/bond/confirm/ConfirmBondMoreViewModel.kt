package jp.co.soramitsu.staking.impl.presentation.staking.bond.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.liveData
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.bond.BondMoreInteractor
import jp.co.soramitsu.staking.impl.domain.validations.bond.BondMoreValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.bond.bondMoreValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmBondMoreViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val bondMoreInteractor: BondMoreInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val iconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    Validatable by validationExecutor {

    private val payload = savedStateHandle.get<ConfirmBondMorePayload>(PAYLOAD_KEY)!!

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .asLiveData()

    val amountFiatFLow = assetFlow.map { asset ->
        asset.token.fiatAmount(payload.amount)?.formatFiat(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val amount = payload.amount.toString()

    val feeStatusLiveData = assetFlow.map { asset ->
        val feeModel = mapFeeToFeeModel(payload.fee, asset.token)

        FeeStatus.Loaded(feeModel)
    }
        .inBackground()
        .asLiveData()

    val stakeCreatorBalanceFlow = flowOf {
        val balance = stakingScenarioInteractor.getAvailableForBondMoreBalance()
        val asset = assetFlow.first()
        balance.formatCryptoDetail(asset.token.configuration.symbolToShow)
    }.inBackground().share()

    val originAddressModelLiveData = liveData {
        val address = payload.stashAddress
        val account = interactor.getProjectedAccount(address)

        val addressModel = iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)

        emit(addressModel)
    }

    fun confirmClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    fun originAccountClicked() = launch {
        val chainId = assetFlow.first().token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, payload.stashAddress)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = payload.stashAddress,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )
        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private fun maybeGoToNext() = launch {
        val payload = BondMoreValidationPayload(
            stashAddress = payload.stashAddress,
            fee = payload.fee,
            amount = payload.amount,
            chainAsset = assetFlow.first().token.configuration
        )

        validationExecutor.requireValid(
            validationSystem = stakingScenarioInteractor.provideBondMoreValidationSystem(),
            payload = payload,
            validationFailureTransformer = { bondMoreValidationFailure(it, resourceManager) },
            progressConsumer = _showNextProgress.progressConsumer()
        ) {
            sendTransaction()
        }
    }

    private fun sendTransaction() = launch {
        val token = assetFlow.first().token
        val amountInPlanks = token.planksFromAmount(payload.amount)

        val result = bondMoreInteractor.bondMore(payload.stashAddress) {
            stakingScenarioInteractor.stakeMore(this, amountInPlanks, payload.collatorAddress)
        }

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            finishFlow()
        } else {
            showError(result.requireException())
        }
    }

    private fun finishFlow() = when {
        payload.overrideFinishAction != null -> payload.overrideFinishAction.invoke(router)
        else -> router.returnToStakingBalance()
    }
}
