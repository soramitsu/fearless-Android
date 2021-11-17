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
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.feature_crowdloan_impl.data.network.api.parachain.FLOW_BONUS_URL
import jp.co.soramitsu.feature_crowdloan_impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.feature_crowdloan_impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.feature_crowdloan_impl.presentation.CrowdloanRouter
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.additionalOnChainSubmission
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.model.LeasePeriodModel
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.acala.AcalaContributionType.LcDOT
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.custom.astar.AstarBonusPayload
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.feature_crowdloan_impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.feature_wallet_api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.feature_wallet_api.domain.AssetUseCase
import jp.co.soramitsu.feature_wallet_api.domain.model.Token
import jp.co.soramitsu.feature_wallet_api.domain.model.Transfer
import jp.co.soramitsu.feature_wallet_api.domain.model.TransferValidityLevel
import jp.co.soramitsu.feature_wallet_api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.FeeStatus
import jp.co.soramitsu.feature_wallet_api.presentation.mixin.TransferValidityChecks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
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
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    ) : BaseViewModel(),
    Validatable by validationExecutor,
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks {

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
    private val crowdloneName = payload.metadata?.name ?: payload.paraId.toString()
    val title = resourceManager.getString(R.string.crowdloan_confirmation_name, crowdloneName)

    private val crowdloanFlow = contributionInteractor.crowdloanStateFlow(
        parachainId = payload.paraId,
        parachainMetadata = payload.metadata?.let { mapParachainMetadataFromParcel(it) }
    )
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

    val bonusNumberFlow = flow {
        if (payload.metadata?.isAcala != true) {
            emit(payload.bonusPayload?.calculateBonus(payload.amount))
        }
    }
        .inBackground()
        .share()

    val bonusFlow = bonusNumberFlow.map { bonus ->
        bonus?.formatTokenAmount(payload.metadata!!.token)
    }
        .inBackground()
        .share()

    val ethAddress = payload.enteredEtheriumAddress
    val privateCrowdloanSignature = payload.signature

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
        val isAcalaLcDot = payload.metadata?.isAcala == true && payload.contributionType == LcDOT
        val customMinAmount = when {
            isAcalaLcDot -> 1.toBigDecimal()
            else -> null
        }
        val validationPayload = ContributeValidationPayload(
            crowdloan = crowdloanFlow.first(),
            fee = payload.fee,
            asset = assetFlow.first(),
            contributionAmount = payload.amount,
            customMinContribution = customMinAmount
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

    private fun sendTransaction(suppressWarnings: Boolean = false) {
        launch {
            val customSubmissionResult = if (payload.bonusPayload != null) {
                val flowName = payload.metadata?.flow?.name!!
                customContributeManager.getSubmitter(flowName)
                    .submitOffChain(payload.bonusPayload, payload.amount, payload.metadata)
            } else {
                Result.success(Unit)
            }

            customSubmissionResult.mapCatching {
                val additionalSubmission = payload.bonusPayload?.let {
                    val flowName = payload.metadata?.flow?.name!!

                    when {
                        payload.metadata.isAstar && (it as? AstarBonusPayload)?.referralCode.isNullOrEmpty().not() -> {
                            additionalOnChainSubmission(it, flowName, payload.amount, customContributeManager)
                        }
                        payload.metadata.isMoonbeam && ethAddress?.second == true -> {
                            additionalOnChainSubmission(it, flowName, payload.amount, customContributeManager)
                        }
                        payload.metadata.isAcala && payload.contributionType == LcDOT -> {
                            additionalOnChainSubmission(it, flowName, payload.amount, customContributeManager)
                        }
                        else -> {
                            null
                        }
                    }
                }

                val isLcDotAcala = payload.metadata?.isAcala == true && payload.contributionType == LcDOT
                if (isLcDotAcala) {
                    val apiUrl = payload.metadata?.flow?.data?.getString(FLOW_API_URL)!!
                    val recipient = contributionInteractor.getAcalaStatement(apiUrl).proxyAddress
                    val maxAllowedStatusLevel = if (suppressWarnings) TransferValidityLevel.Warning else TransferValidityLevel.Ok
                    val fee = feeFlow.firstOrNull()?.feeModel?.fee ?: return@launch
                    contributionInteractor.performTransfer(
                        transfer = Transfer(
                            recipient = recipient,
                            amount = payload.amount,
                            tokenType = Token.Type.DOT
                        ),
                        fee = fee,
                        maxAllowedLevel = maxAllowedStatusLevel,
                        additional = additionalSubmission
                    )
                } else {
                    contributionInteractor.contribute(
                        originAddress = selectedAddressModelFlow.first().address,
                        parachainId = payload.paraId,
                        contribution = payload.amount,
                        token = assetFlow.first().token,
                        additionalSubmission,
                        privateCrowdloanSignature
                    )
                }
            }
                .onFailure(::showError)
                .onSuccess {
                    showMessage(resourceManager.getString(R.string.common_transaction_submitted))

                    saveMoonbeamEtheriumAddress()

                    router.returnToMain()
                }

            _showNextProgress.value = false
        }
    }

    fun warningConfirmed() {
        sendTransaction(suppressWarnings = true)
    }

    private suspend fun saveMoonbeamEtheriumAddress() = when {
        payload.metadata?.isMoonbeam != true || ethAddress == null -> Unit
        else -> contributionInteractor.saveEthAddress(
            paraId = payload.paraId,
            address = selectedAddressModelFlow.first().address,
            etheriumAddress = ethAddress.first
        )
    }


    fun bonusClicked() = when (payload.metadata?.isAcala) {
        true -> {
            val bonusUrl = payload.metadata.flow?.data?.getString(FLOW_BONUS_URL) ?: payload.metadata.website
            openBrowserEvent.postValue(Event(bonusUrl))
        }
        else -> Unit
    }
}
