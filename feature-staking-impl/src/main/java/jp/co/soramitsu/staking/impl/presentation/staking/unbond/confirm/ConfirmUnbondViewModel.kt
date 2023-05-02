package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import jp.co.soramitsu.staking.impl.domain.StakingInteractor
import jp.co.soramitsu.staking.impl.domain.staking.unbond.UnbondInteractor
import jp.co.soramitsu.staking.impl.domain.validations.unbond.UnbondValidationPayload
import jp.co.soramitsu.staking.impl.presentation.StakingRouter
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.unbondPayloadAutoFix
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.unbondValidationFailure
import jp.co.soramitsu.staking.impl.scenarios.StakingScenarioInteractor
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmUnbondViewModel @Inject constructor(
    private val router: StakingRouter,
    interactor: StakingInteractor,
    private val stakingScenarioInteractor: StakingScenarioInteractor,
    private val unbondInteractor: UnbondInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val iconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    Validatable by validationExecutor {

    private val payload = savedStateHandle.get<ConfirmUnbondPayload>(PAYLOAD_KEY)!!

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = stakingScenarioInteractor.stakingStateFlow
        .share()

    private val assetFlow = interactor.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager, Asset::bonded, R.string.staking_bonded_format) }
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

    val originAddressModelLiveData = accountStakingFlow.map {
        val address = it.executionAddress
        val account = interactor.getProjectedAccount(address)

        val addressModel = iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)

        addressModel
    }
        .inBackground()
        .asLiveData()

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
        val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, originAddressModel.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = originAddressModel.address,
            chainId = chainId,
            chainName = chain.name,
            explorers = supportedExplorers
        )

        externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private fun maybeGoToNext() = launch {
        val asset = assetFlow.first()

        val payload = UnbondValidationPayload(
            asset = asset,
            stash = accountStakingFlow.first(),
            fee = payload.fee,
            amount = payload.amount,
            collatorAddress = payload.collatorAddress
        )

        validationExecutor.requireValid(
            validationSystem = stakingScenarioInteractor.getUnbondValidationSystem(),
            payload = payload,
            validationFailureTransformer = { unbondValidationFailure(it, resourceManager) },
            autoFixPayload = ::unbondPayloadAutoFix,
            progressConsumer = _showNextProgress.progressConsumer()
        ) { validPayload ->
            sendTransaction(validPayload)
        }
    }

    private fun sendTransaction(validPayload: UnbondValidationPayload) = launch {
        val amountInPlanks = validPayload.asset.token.configuration.planksFromAmount(payload.amount)

        val result = unbondInteractor.unbond(validPayload.stash) {
            stakingScenarioInteractor.stakeLess(this, amountInPlanks, validPayload.stash, validPayload.asset.bondedInPlanks.orZero())
        }

        _showNextProgress.value = false

        if (result.isSuccess) {
            showMessage(resourceManager.getString(R.string.common_transaction_submitted))

            router.returnToStakingBalance()
        } else {
            showError(result.requireException())
        }
    }
}
