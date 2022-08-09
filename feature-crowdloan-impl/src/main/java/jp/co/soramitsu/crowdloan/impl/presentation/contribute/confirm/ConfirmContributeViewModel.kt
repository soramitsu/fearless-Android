package jp.co.soramitsu.crowdloan.impl.presentation.contribute.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.data.network.BlockExplorerUrlBuilder
import jp.co.soramitsu.common.mixin.api.Validatable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.inBackground
import jp.co.soramitsu.common.validation.ValidationExecutor
import jp.co.soramitsu.common.validation.progressConsumer
import jp.co.soramitsu.feature_crowdloan_impl.R
import jp.co.soramitsu.account.api.domain.interfaces.SelectedAccountUseCase
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_API_URL
import jp.co.soramitsu.crowdloan.impl.data.network.api.parachain.FLOW_BONUS_URL
import jp.co.soramitsu.crowdloan.impl.di.customCrowdloan.CustomContributeManager
import jp.co.soramitsu.crowdloan.impl.domain.contribute.CrowdloanContributeInteractor
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidationPayload
import jp.co.soramitsu.crowdloan.impl.domain.contribute.validations.ContributeValidationSystem
import jp.co.soramitsu.crowdloan.impl.presentation.CrowdloanRouter
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.additionalOnChainSubmission
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.confirm.model.LeasePeriodModel
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.confirm.parcel.ConfirmContributePayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.contributeValidationFailure
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.acala.AcalaContributionType.LcDOT
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.astar.AstarBonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.custom.interlay.InterlayBonusPayload
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.getString
import jp.co.soramitsu.crowdloan.impl.presentation.contribute.select.parcel.mapParachainMetadataFromParcel
import jp.co.soramitsu.wallet.api.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.api.data.mappers.mapFeeToFeeModel
import jp.co.soramitsu.wallet.impl.domain.AssetUseCase
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.api.presentation.mixin.fee.FeeStatus
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedExplorers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Named

@HiltViewModel
class ConfirmContributeViewModel @Inject constructor(
    private val router: CrowdloanRouter,
    private val contributionInteractor: CrowdloanContributeInteractor,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    @Named("CrowdloanAssetUseCase") assetUseCase: AssetUseCase,
    accountUseCase: SelectedAccountUseCase,
    addressModelGenerator: AddressIconGenerator,
    private val validationExecutor: ValidationExecutor,
    private val validationSystem: ContributeValidationSystem,
    private val customContributeManager: CustomContributeManager,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    private val savedStateHandle: SavedStateHandle
) : BaseViewModel(),
    Validatable by validationExecutor,
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks {

    private val payload = savedStateHandle.get<ConfirmContributePayload>(KEY_PAYLOAD)!!

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
        asset.token.fiatAmount(payload.amount)?.formatAsCurrency(asset.token.fiatSymbol)
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
            val chainId = assetFlow.first().token.configuration.chainId
            val chain = chainRegistry.getChain(chainId)
            val supportedExplorers = chain.explorers.getSupportedExplorers(BlockExplorerUrlBuilder.Type.ACCOUNT, accountAddress)
            val payload = ExternalAccountActions.Payload(accountAddress, chainId, chain.name, supportedExplorers)
            externalAccountActions.showExternalActions(payload)
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
                        payload.metadata.isInterlay && (it as? InterlayBonusPayload)?.referralCode.isNullOrEmpty().not() -> {
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
                            chainAsset = assetFlow.first().token.configuration
                        ),
                        fee = fee,
                        maxAllowedLevel = maxAllowedStatusLevel,
                        additional = additionalSubmission
                    )
                } else {
                    val useBatchAll = additionalSubmission != null && payload.metadata?.isInterlay != true
                    contributionInteractor.contribute(
                        parachainId = payload.paraId,
                        contribution = payload.amount,
                        additional = additionalSubmission,
                        batchAll = useBatchAll,
                        signature = privateCrowdloanSignature
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
