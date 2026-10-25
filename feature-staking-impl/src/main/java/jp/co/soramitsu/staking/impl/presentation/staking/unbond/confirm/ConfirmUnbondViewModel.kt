package jp.co.soramitsu.staking.impl.presentation.staking.unbond.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
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
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.staking.impl.presentation.staking.unbond.UnbondRequestAuthority
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.api.presentation.model.AssetModel
import jp.co.soramitsu.common.data.storage.WalletSelectionEpoch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ConfirmUnbondViewModel @Inject constructor(
    private val router: StakingRouter,
    private val interactor: StakingInteractor,
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

    private val review = payload.review?.takeIf {
        payload.amount == it.payload.amount && payload.fee == it.payload.fee &&
            payload.collatorAddress == it.payload.collatorAddress && it.attach(viewModelScope.coroutineContext.job)
    }

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val reviewChanges = review?.let {
        merge(it.context.changes.map { Unit }, WalletSelectionEpoch.changes.map { Unit },
            it.context.selectionChanges.map { Unit })
            .shareIn(CoroutineScope(viewModelScope.coroutineContext + Dispatchers.Main), SharingStarted.Eagerly, replay = 1)
    } ?: flowOf(Unit)

    private val assetFlow = reviewChanges.map {
        review?.let { current ->
            val asset = current.context.asset
            if (current.isCurrent()) asset else Asset.createEmpty(asset.token.configuration, asset.metaId,
                asset.accountId, minSupportedVersion = asset.minSupportedVersion)
        }
    }.share()

    val assetModelFlow = assetFlow
        .map { asset -> asset?.let { mapAssetToAssetModel(it, resourceManager, { it.bonded }, R.string.staking_bonded_format) }
            ?: AssetModel("", "", "", "", resourceManager.getString(jp.co.soramitsu.common.R.string.portfolio_balance_unavailable)) }
        .inBackground()
        .asLiveData()

    val amountFiatFLow = assetFlow.map { asset ->
        asset?.let { it.token.fiatAmount(payload.amount)?.formatFiat(it.token.fiatSymbol) }
    }
        .inBackground()
        .asLiveData()

    val amount = payload.amount.toString()

    val feeStatusLiveData = assetFlow.map { asset ->
        if (review?.isCurrent() == true && asset != null) FeeStatus.Loaded(mapFeeToFeeModel(payload.fee, asset.token))
        else FeeStatus.Error
    }
        .inBackground()
        .asLiveData()

    private val mutableOriginAddress = MutableLiveData<AddressModel?>()
    val originAddressModelLiveData: LiveData<AddressModel?> = mutableOriginAddress

    init {
        // Clear before joining old icon work; failed or revoked reviews have no actionable origin card.
        viewModelScope.launch(Dispatchers.Main) {
            reviewChanges.onEach { mutableOriginAddress.value = null; _showNextProgress.value = false }
                .collectLatest {
                    val current = review?.takeIf { it.isCurrent() } ?: return@collectLatest
                    try {
                        val address = current.context.stash.executionAddress
                        val account = interactor.getProjectedAccount(address)
                        if (!current.isCurrent()) return@collectLatest
                        val model = iconGenerator.createAddressModel(address, AddressIconGenerator.SIZE_SMALL, account.name)
                        if (current.isCurrent()) mutableOriginAddress.value = model
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Throwable) { if (current.isCurrent()) showError(error) }
                }
        }
    }

    fun confirmClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        review?.revoke()
        router.back()
    }

    fun originAccountClicked() = launch {
        val current = review?.takeIf { it.isCurrent() } ?: return@launch
        val originAddressModel = originAddressModelLiveData.value ?: return@launch
        val chainId = current.context.asset.token.configuration.chainId
        val chain = chainRegistry.getChain(chainId)
        val supportedExplorers = chain.explorers.getSupportedAddressExplorers(originAddressModel.address)
        val externalActionsPayload = ExternalAccountActions.Payload(
            value = originAddressModel.address,
            explorers = supportedExplorers
        )

        if (review?.isCurrent() == true) externalAccountActions.showExternalActions(externalActionsPayload)
    }

    private fun maybeGoToNext() {
        val current = review?.takeIf { it.isCurrent() } ?: run {
            showError(message = resourceManager.getString(R.string.staking_unbond_review_changed),
                positiveButtonText = resourceManager.getString(R.string.staking_unbond_return_to_review), positiveClick = ::backClicked)
            return
        }
        val attempt = current.context.claim(viewModelScope.coroutineContext.job, current) ?: return
        launch {
            try {
                validationExecutor.requireValid(
                    validationSystem = stakingScenarioInteractor.getUnbondValidationSystem(),
                    payload = current.payload,
                    errorDisplayer = ::showError,
                    validationFailureTransformer = { unbondValidationFailure(it, resourceManager) },
                    autoFixPayload = ::unbondPayloadAutoFix,
                    progressConsumer = _showNextProgress.progressConsumer(),
                    isCurrent = attempt::isCurrent
                ) { valid ->
                    if (attempt.isCurrent()) {
                        if (valid.amount.compareTo(current.context.amount) != 0) {
                            val changed = attempt.requestAmountReview(valid.amount)
                            _showNextProgress.value = false
                            attempt.release()
                            if (changed) backClicked()
                            else showError(message = resourceManager.getString(R.string.staking_unbond_review_changed),
                                positiveButtonText = resourceManager.getString(R.string.staking_unbond_return_to_review),
                                positiveClick = ::backClicked)
                        } else sendTransaction(attempt, valid)
                    }
                }
            } catch (cancelled: CancellationException) { attempt.release(); throw cancelled }
            catch (error: Throwable) { if (attempt.isCurrent()) showError(error); attempt.release() }
        }
    }

    private fun sendTransaction(attempt: UnbondRequestAuthority.Attempt, valid: UnbondValidationPayload) = launch {
        try {
            attempt.requireCurrent()
            val amountInPlanks = valid.asset.token.planksFromAmount(valid.amount)
            val execution = currentCoroutineContext().job
            val result = unbondInteractor.unbond(valid.stash, valid.asset.metaId, attempt::isCurrent,
                { intent -> attempt.authorize(intent, execution) }) {
                attempt.requireCurrent()
                stakingScenarioInteractor.stakeLess(this, amountInPlanks, valid.stash,
                    valid.asset.bondedInPlanks, valid.collatorAddress, attempt::isCurrent)
                attempt.requireCurrent()
            }
            (result.exceptionOrNull() as? CancellationException)?.let { throw it }
            if (attempt.isCurrent()) {
                _showNextProgress.value = false
                if (result.isSuccess) {
                    showMessage(resourceManager.getString(R.string.common_transaction_submitted))
                    router.returnToStakingBalance()
                    result.getOrNull()?.let { router.openOperationSuccess(it, valid.asset.token.configuration.chainId) }
                } else showError(result.requireException())
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { if (attempt.isCurrent()) showError(error) }
        finally { if (attempt.isCurrent()) _showNextProgress.value = false; attempt.release() }
    }

    override fun onCleared() {
        review?.revoke()
        super.onCleared()
    }
}
