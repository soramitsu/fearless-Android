package jp.co.soramitsu.feature_staking_impl.presentation.staking.redeem

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
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
import jp.co.soramitsu.feature_account_api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_staking_api.domain.model.StakingState
import jp.co.soramitsu.feature_staking_impl.domain.StakingInteractor
import jp.co.soramitsu.feature_staking_impl.domain.staking.redeem.RedeemInteractor
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationPayload
import jp.co.soramitsu.feature_staking_impl.domain.validations.reedeem.RedeemValidationSystem
import jp.co.soramitsu.feature_staking_impl.presentation.StakingRouter
import jp.co.soramitsu.feature_staking_impl.scenarios.relaychain.StakingRelayChainScenarioInteractor
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.domain.model.Asset
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeLoaderMixin
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class RedeemViewModel(
    private val router: StakingRouter,
    private val stakingRelayChainScenarioInteractor: StakingRelayChainScenarioInteractor,
    private val interactor: StakingInteractor,
    private val redeemInteractor: RedeemInteractor,
    private val resourceManager: ResourceManager,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: RedeemValidationSystem,
    private val iconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val feeLoaderMixin: FeeLoaderMixin.Presentation,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val payload: RedeemPayload
) : BaseViewModel(),
    Validatable by validationExecutor,
    FeeLoaderMixin by feeLoaderMixin,
    ExternalAccountActions by externalAccountActions {

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val accountStakingFlow = stakingRelayChainScenarioInteractor.selectedAccountStakingStateFlow()
        .filterIsInstance<StakingState.Stash>()
        .share()

    private val assetFlow = accountStakingFlow
        .flatMapLatest { interactor.assetFlow(it.controllerAddress) }
        .share()

    val amountLiveData = assetFlow.map { asset ->
        val redeemable = asset.redeemable

        redeemable.format() to asset.token.fiatAmount(redeemable)?.formatAsCurrency(asset.token.fiatSymbol)
    }
        .inBackground()
        .asLiveData()

    val assetModelLiveData = assetFlow.map { asset ->
        mapAssetToAssetModel(asset, resourceManager, Asset::redeemable, R.string.staking_redeemable_format)
    }

    val originAddressModelLiveData = accountStakingFlow.map {
        val address = it.controllerAddress
        val account = interactor.getProjectedAccount(address)

        iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)
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

    private fun loadFee() {
        feeLoaderMixin.loadFee(
            coroutineScope = viewModelScope,
            feeConstructor = { redeemInteractor.estimateFee(accountStakingFlow.first()) },
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
                asset = asset
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
        val result = redeemInteractor.redeem(accountStakingFlow.first(), redeemValidationPayload.asset)

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
