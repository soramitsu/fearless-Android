package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.account.api.presentation.actions.ExternalAccountActions
import jp.co.soramitsu.common.AlertViewState
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICON_IN_DP = 24

@HiltViewModel
class CrossChainConfirmViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val router: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase,
    private val xcmInteractor: XcmInteractor
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks,
    CrossChainConfirmScreenInterface {

    private val transferDraft = savedStateHandle.get<CrossChainTransferDraft>(CrossChainConfirmFragment.KEY_DRAFT)
        ?: error("Required data not provided for send confirmation")
    private val phishingType = savedStateHandle.get<PhishingType>(CrossChainConfirmFragment.KEY_PHISHING_TYPE)

    private val _openValidationWarningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> = _openValidationWarningEvent

    private val recipientFlow = interactor.observeAddressBook(transferDraft.destinationChainId).map { contacts ->
        val contactName = contacts.firstOrNull { it.address.equals(transferDraft.recipientAddress, ignoreCase = true) }?.name
        getAddressModel(transferDraft.recipientAddress, contactName)
    }

    private val originNetworkFlow = flowOf { interactor.getChain(transferDraft.originChainId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val destinationNetworkFlow = flowOf { interactor.getChain(transferDraft.destinationChainId) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val senderFlow = flowOf {
        currentAccountAddress(transferDraft.originChainId)?.let { address ->
            val walletName = interactor.getSelectedMetaAccount().name
            getAddressModel(address, walletName)
        }
    }

    private val transferSubmittingFlow = MutableStateFlow(false)
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_confirm),
        true
    )

    private val buttonStateFlow = transferSubmittingFlow.map { submitting ->
        ButtonViewState(
            text = resourceManager.getString(R.string.common_confirm),
            enabled = !submitting
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    private val originAssetFlow = interactor.assetFlow(transferDraft.originChainId, transferDraft.chainAssetId)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val utilityAssetFlow = originNetworkFlow.mapNotNull {
        it?.utilityAsset?.id
    }.flatMapLatest { assetId ->
        interactor.assetFlow(transferDraft.originChainId, assetId)
            .map(::mapAssetToAssetModel)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val transferableAssetFlow = originNetworkFlow.mapNotNull {
        it?.assets?.firstOrNull { it.symbol == transferDraft.transferableTokenSymbol }?.id
    }.flatMapLatest { assetId ->
        interactor.assetFlow(transferDraft.originChainId, assetId)
            .map(::mapAssetToAssetModel)
    }

    val state: StateFlow<CrossChainConfirmViewState> = combine(
        recipientFlow,
        originNetworkFlow,
        destinationNetworkFlow,
        senderFlow,
        originAssetFlow,
        utilityAssetFlow,
        transferableAssetFlow,
        buttonStateFlow,
        transferSubmittingFlow
    ) { recipient, originNetwork, destinationNetwork, sender, originAsset, utilityAsset, transferableAsset, buttonState, isSubmitting ->
        val isRecipientNameSpecified = !recipient.name.isNullOrEmpty()
        val toInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.send_to),
            value = if (isRecipientNameSpecified) recipient.name else recipient.address.shortenAddress(),
            additionalValue = if (isRecipientNameSpecified) recipient.address.shortenAddress() else null,
            clickState = phishingType?.let { TitleValueViewState.ClickState.Value(R.drawable.ic_alert_16, CrossChainConfirmViewState.CODE_WARNING_CLICK) }
        )

        val originNetworkName = originNetwork?.name
        val originNetworkItem = if (originNetworkName != null) {
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_origin_network),
                value = originNetworkName
            )
        } else {
            null
        }

        val destinationNetworkName = destinationNetwork?.name
        val destinationNetworkItem = if (destinationNetworkName != null) {
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_destination_network),
                value = destinationNetworkName
            )
        } else {
            null
        }

        val amountInfoItem = if (originAsset != null) {
            val assetModel = mapAssetToAssetModel(originAsset)
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_amount),
                value = transferDraft.amount.formatCryptoDetail(assetModel.token.configuration.symbol),
                additionalValue = assetModel.getAsFiatWithCurrency(transferDraft.amount)
            )
        } else {
            null
        }

        val tipInfoItem = transferDraft.tip?.let {
            TitleValueViewState(
                title = resourceManager.getString(R.string.choose_amount_tip),
                value = transferDraft.tip.formatCryptoDetail(utilityAsset.token.configuration.symbol),
                additionalValue = utilityAsset.getAsFiatWithCurrency(transferDraft.tip)
            )
        }

        val originFeeInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.common_origin_network_fee),
            value = transferDraft.originFee.formatCryptoDetail(utilityAsset.token.configuration.symbol),
            additionalValue = utilityAsset.getAsFiatWithCurrency(transferDraft.originFee)
        )

        val destinationFeeInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.common_destination_network_fee),
            value = transferDraft.destinationFee.formatCryptoDetail(transferableAsset.token.configuration.symbol),
            additionalValue = transferableAsset.getAsFiatWithCurrency(transferDraft.destinationFee)
        )

        CrossChainConfirmViewState(
            originChainIcon = GradientIconData(
                url = originNetwork?.icon,
                color = originNetwork?.utilityAsset?.color
            ),
            destinationChainIcon = GradientIconData(
                url = destinationNetwork?.icon,
                color = destinationNetwork?.utilityAsset?.color
            ),
            toInfoItem = toInfoItem,
            originNetworkItem = originNetworkItem,
            destinationNetworkItem = destinationNetworkItem,
            amountInfoItem = amountInfoItem,
            tipInfoItem = tipInfoItem,
            originFeeInfoItem = originFeeInfoItem,
            destinationFeeInfoItem = destinationFeeInfoItem,
            buttonState = buttonState,
            isLoading = isSubmitting
        )
    }.stateIn(this, SharingStarted.Eagerly, CrossChainConfirmViewState.default)

    override fun onNavigationClick() {
        router.back()
    }

    override fun copyRecipientAddressClicked() {
        launch {
            val chain = destinationNetworkFlow.value ?: return@launch
            val supportedExplorers = chain.explorers.getSupportedAddressExplorers(transferDraft.recipientAddress)
            val externalActionsPayload = ExternalAccountActions.Payload(
                value = transferDraft.recipientAddress,
                chainId = chain.id,
                chainName = chain.name,
                explorers = supportedExplorers
            )

            externalAccountActions.showExternalActions(externalActionsPayload)
        }
    }

    override fun onNextClick() {
        launch {
            val destinationChain = destinationNetworkFlow.value ?: return@launch
            val asset = originAssetFlow.firstOrNull() ?: return@launch
            val token = asset.token.configuration

            val rawAmountInPlanks = token.planksFromAmount(transferDraft.amount)
            val destinationFeeInPlanks = token.planksFromAmount(transferDraft.destinationFee)
            val originFee = token.planksFromAmount(transferDraft.originFee)
            val recipientAddress = transferDraft.recipientAddress
            val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@launch

            val validationProcessResult = validateTransferUseCase.validateExistentialDeposit(
                amountInPlanks = rawAmountInPlanks + destinationFeeInPlanks,
                originAsset = asset,
                destinationChainId = destinationChain.id,
                destinationAddress = recipientAddress,
                originAddress = selfAddress,
                originFee = originFee,
                confirmedValidations = confirmedValidations
            )

            // error occurred inside validation
            validationProcessResult.exceptionOrNull()?.let {
                showError(it)
                return@launch
            }
            val validationResult = validationProcessResult.requireValue()

            ValidationException.fromValidationResult(validationResult, resourceManager)?.let {
                if (it is ValidationWarning) {
                    _openValidationWarningEvent.value = Event(validationResult to it)
                } else {
                    showError(it)
                }
                return@launch
            }

            performTransfer()
        }
    }

    override fun onItemClick(code: Int) {
        when (code) {
            CrossChainConfirmViewState.CODE_WARNING_CLICK -> openWarningAlert()
        }
    }

    private fun openWarningAlert() {
        launch {
            val originAsset = originAssetFlow.value ?: return@launch
            val symbol = originAsset.token.configuration.symbol

            val payload = AlertViewState(
                title = getPhishingTitle(phishingType),
                message = getPhishingMessage(phishingType, symbol),
                buttonText = resourceManager.getString(R.string.top_up),
                iconRes = R.drawable.ic_alert_16
            )
            router.openAlert(payload)
        }
    }

    private fun getPhishingTitle(phishingType: PhishingType?): String {
        return when (phishingType) {
            PhishingType.SCAM -> resourceManager.getString(R.string.scam_alert_title)
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_alert_title)
            PhishingType.DONATION -> resourceManager.getString(R.string.donation_alert_title)
            PhishingType.SANCTIONS -> resourceManager.getString(R.string.sanction_alert_title)
            else -> resourceManager.getString(R.string.donation_alert_title)
        }
    }

    private fun getPhishingMessage(phishingType: PhishingType?, symbol: String): String {
        return when (phishingType) {
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_alert_message)
            else -> resourceManager.getString(R.string.scam_alert_message_format, symbol)
        }
    }

    fun warningConfirmed(validationResult: TransferValidationResult) {
        confirmedValidations.add(validationResult)
        onNextClick()
    }

    private fun performTransfer() {
        launch {
            val originAsset = originAssetFlow.value ?: return@launch
            val token = originAsset.token.configuration

            transferSubmittingFlow.value = true

            val result = withContext(Dispatchers.Default) {
                xcmInteractor.performCrossChainTransfer(createTransfer(token))
            }
            if (result.isSuccess) {
                val operationHash = result.getOrNull()
                router.finishSendFlow()
                router.openOperationSuccess(operationHash, token.chainId)
            } else {
                val error = result.requireException()

                if (error is NotValidTransferStatus) {
                    processInvalidStatus(error.status)
                } else {
                    showError(error)
                }
            }

            transferSubmittingFlow.value = false
        }
    }

    private fun processInvalidStatus(status: TransferValidityStatus) {
        when (status) {
            is TransferValidityLevel.Warning.Status -> transferValidityChecks.showTransferWarning(status)
            is TransferValidityLevel.Error.Status -> transferValidityChecks.showTransferError(status)
        }
    }

    private suspend fun getAddressModel(address: String, accountName: String? = null): AddressModel {
        return addressIconGenerator.createAddressModel(address, ICON_IN_DP, accountName)
    }

    private fun createTransfer(token: Asset): CrossChainTransfer {
        return with(transferDraft) {
            CrossChainTransfer(
                recipient = recipientAddress,
                amount = amount,
                chainAsset = token,
                originChainId = originChainId,
                destinationChainId = destinationChainId,
                destinationFee = destinationFee
            )
        }
    }
}
