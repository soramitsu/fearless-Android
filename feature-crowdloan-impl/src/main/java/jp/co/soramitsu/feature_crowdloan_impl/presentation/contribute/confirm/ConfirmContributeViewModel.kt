package jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_account_api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.feature_account_api.presenatation.actions.ExternalAccountActions
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.additionalOnChainSubmission
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.model.LeasePeriodModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.fee.FeeStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConfirmContributeViewModel(
    private val router: CrowdloanRouter,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    assetUseCase: AssetUseCase,
    accountUseCase: SelectedAccountUseCase,
    addressModelGenerator: AddressIconGenerator,
    private val validationExecutor: ValidationExecutor,
    private val payload: ConfirmContributePayload,
    private val validationSystem: ContributeValidationSystem,
    private val customContributeManager: CustomContributeManager,
    private val externalAccountActions: ExternalAccountActions.Presentation
) : BaseViewModel(),
    Validatable by validationExecutor,
    ExternalAccountActions by externalAccountActions {

    override val openBrowserEvent = MutableLiveData<Event<String>>()

    private val _showNextProgress = MutableLiveData(false)
    val showNextProgress: LiveData<Boolean> = _showNextProgress

    private val assetFlow = assetUseCase.currentAssetFlow()
        .share()

    val assetModelFlow = assetFlow
        .map { mapAssetToAssetModel(it, resourceManager) }
        .inBackground()
        .share()

    val selectedAddressModelFlow = accountUseCase.selectedAccountFlow()
        .map {
            addressModelGenerator.createAddressModel(it.address, AddressIconGenerator.SIZE_SMALL, it.name)
        }

    val selectedAmount = payload.amount.toString()

    val feeFlow = assetFlow.map { asset ->
        val feeModel = mapFeeToFeeModel(payload.fee, asset.token)

        FeeStatus.Loaded(feeModel)
    }
        .inBackground()
        .share()

    val enteredFiatAmountFlow = assetFlow.map { asset ->
        asset.token.fiatAmount(payload.amount)?.formatAsCurrency()
    }
        .inBackground()
        .share()

    val estimatedReward = payload.estimatedRewardDisplay

    private val crowdloanFlow = contributionInteractor.crowdloanStateFlow(payload.paraId)
        .inBackground()
        .share()

    val crowdloanInfoFlow = crowdloanFlow.map { crowdloan ->
        LeasePeriodModel(
            leasePeriod = resourceManager.formatDuration(crowdloan.leasePeriodInMillis),
            leasedUntil = resourceManager.formatDate(crowdloan.leasedUntilInMillis)
        )
    }
        .inBackground()
        .share()

    val bonusFlow = flow {
        val bonusDisplay = payload.bonusPayload?.let {
            val bonus = it.calculateBonus(payload.amount)

            bonus?.formatTokenAmount(payload.metadata!!.token)
        }

        emit(bonusDisplay)
    }
        .inBackground()
        .share()

    fun nextClicked() {
        maybeGoToNext()
    }

    fun backClicked() {
        router.back()
    }

    fun originAccountClicked() {
        launch {
            val accountAddress = selectedAddressModelFlow.first().address

            externalAccountActions.showExternalActions(ExternalAccountActions.Payload.fromAddress(accountAddress))
        }
    }

    private fun maybeGoToNext() = launch {
        val validationPayload = ContributeValidationPayload(
            crowdloan = crowdloanFlow.first(),
            fee = payload.fee,
            asset = assetFlow.first(),
            contributionAmount = payload.amount
        )

        validationExecutor.requireValid(
            validationSystem = validationSystem,
            payload = validationPayload,
            progressConsumer = _showNextProgress.progressConsumer(),
            validationFailureTransformer = { contributeValidationFailure(it, resourceManager) }
        ) {
            sendTransaction()
        }
    }

    private fun sendTransaction() {
        launch {
            val customSubmissionResult = if (payload.bonusPayload != null) {
                val metadata = payload.metadata!!

                customContributeManager.getSubmitter(metadata.customFlow!!)
                    .submitOffChain(payload.bonusPayload, payload.amount)
            } else {
                Result.success(Unit)
            }

            customSubmissionResult.mapCatching {
                val additionalSubmission = payload.bonusPayload?.let {
                    additionalOnChainSubmission(it, payload.metadata!!.customFlow!!, payload.amount, customContributeManager)
                }

                contributionInteractor.contribute(
                    parachainId = payload.paraId,
                    contribution = payload.amount,
                    additional = additionalSubmission
                )
            }
                .onFailure(::showError)
                .onSuccess {
                    showMessage(resourceManager.getString(R.string.common_transaction_submitted))

                    router.returnToMain()
                }

            _showNextProgress.value = false
        }
    }
}
