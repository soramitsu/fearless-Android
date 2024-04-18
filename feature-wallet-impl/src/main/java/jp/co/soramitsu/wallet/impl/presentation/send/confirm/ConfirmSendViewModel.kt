package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
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
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.getSupportedAddressExplorers
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
//import jp.co.soramitsu.wallet.api.presentation.WalletRouter
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ICON_IN_DP = 24
val FEE_CORRECTION = BigDecimal("0.01")
val FEE_RESERVE_TOLERANCE = BigDecimal("1.5")

@HiltViewModel
class ConfirmSendViewModel @Inject constructor(
    private val interactor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val router: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val chainRegistry: ChainRegistry,
    private val externalAccountActions: ExternalAccountActions.Presentation,
    private val transferValidityChecks: TransferValidityChecks.Presentation,
    savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks,
    ConfirmSendScreenInterface {

    private val transferDraft = savedStateHandle.get<TransferDraft>(ConfirmSendFragment.KEY_DRAFT) ?: error("Required data not provided for send confirmation")
    private val phishingType = savedStateHandle.get<PhishingType>(ConfirmSendFragment.KEY_PHISHING_TYPE)
    private val overrides = savedStateHandle.get<Map<String, Any?>>(ConfirmSendFragment.KEY_OVERRIDES).orEmpty()
    private val transferComment = savedStateHandle.get<String>(ConfirmSendFragment.KEY_TRANSFER_COMMENT)
    private val skipEdValidation = savedStateHandle.get<Boolean>(ConfirmSendFragment.KEY_SKIP_ED_VALIDATION) == true

    private val _openValidationWarningEvent = MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> = _openValidationWarningEvent

    private val recipientFlow = interactor.observeAddressBook(transferDraft.assetPayload.chainId).map { contacts ->
        val contactName = contacts.firstOrNull { it.address.equals(transferDraft.recipientAddress, ignoreCase = true) }?.name
        getAddressModel(transferDraft.recipientAddress, contactName)
    }

    private val senderFlow = flowOf {
        currentAccountAddress(transferDraft.assetPayload.chainId)?.let { address ->
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

    private val assetFlow = interactor.assetFlow(transferDraft.assetPayload.chainId, transferDraft.assetPayload.chainAssetId).share()

    @OptIn(ExperimentalCoroutinesApi::class)
    val utilityAssetFlow = flowOf {
        val assetChain = interactor.getChain(transferDraft.assetPayload.chainId)
        assetChain.utilityAsset?.id
    }
        .mapNotNull { it }
        .flatMapLatest { assetId ->
            interactor.assetFlow(transferDraft.assetPayload.chainId, assetId)
        }

    private val feeFlow = assetFlow.map { createTransfer(it.token.configuration) }
        .flatMapLatest { interactor.observeTransferFee(it) }
        .map { it.feeAmount }
        .onStart { transferDraft.fee }

    val state: StateFlow<ConfirmSendViewState> = combine(
        recipientFlow,
        senderFlow,
        assetFlow,
        utilityAssetFlow,
        buttonStateFlow,
        transferSubmittingFlow,
        feeFlow
    ) { recipient, sender, asset, utilityAsset, buttonState, isSubmitting, fee ->
        val isSenderNameSpecified = !sender?.name.isNullOrEmpty()
        val fromInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.transaction_details_from),
            value = if (isSenderNameSpecified) sender?.name else sender?.address?.shortenAddress(),
            additionalValue = if (isSenderNameSpecified) sender?.address?.shortenAddress() else null
        )

        val isRecipientNameSpecified = !recipient.name.isNullOrEmpty()
        val hasOverriddenToValue = overrides.containsKey(ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE)

        val toValue = when {
            hasOverriddenToValue -> overrides[ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE] as String
            isRecipientNameSpecified -> recipient.name
            else -> recipient.address.shortenAddress()
        }
        val toAdditionalValue = when {
            hasOverriddenToValue -> null
            isRecipientNameSpecified -> recipient.address.shortenAddress()
            else -> null
        }
        val toInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.choose_amount_to),
            value = toValue,
            additionalValue = toAdditionalValue,
            clickState = phishingType?.let { TitleValueViewState.ClickState.Value(R.drawable.ic_alert_16, ConfirmSendViewState.CODE_WARNING_CLICK) }
        )

        val assetModel = mapAssetToAssetModel(asset)
        val amountInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.common_amount),
            value = transferDraft.amount.formatCryptoDetail(assetModel.token.configuration.symbol),
            additionalValue = assetModel.getAsFiatWithCurrency(transferDraft.amount)
        )

        val tipInfoItem = transferDraft.tip?.let { tip ->
            TitleValueViewState(
                title = resourceManager.getString(R.string.choose_amount_tip),
                value = tip.formatCryptoDetail(utilityAsset.token.configuration.symbol),
                additionalValue = utilityAsset.getAsFiatWithCurrency(tip)
            )
        }

        val isSendBokoloCash = asset.token.configuration.currencyId == bokoloCashTokenId

        val showFeeAsset = if (isSendBokoloCash && utilityAsset.transferable < fee) {
            asset
        } else {
            utilityAsset
        }

        val assetFeeAmount = if (isSendBokoloCash && utilityAsset.transferable < fee) {
            val swapDetails = polkaswapInteractor.calcDetails(
                availableDexPaths = listOf(0),
                tokenFrom = asset,
                tokenTo = utilityAsset,
                amount = fee,
                desired = WithDesired.OUTPUT,
                slippageTolerance = 1.5,
                market = Market.SMART
            )
            swapDetails.getOrNull()?.amount
        } else {
            fee
        }

        val feeFormatted = assetFeeAmount?.formatCryptoDetail(showFeeAsset.token.configuration.symbol)
        val feeFiat = assetFeeAmount?.applyFiatRate(showFeeAsset.token.fiatRate)?.formatFiat(showFeeAsset.token.fiatSymbol)

        val feeInfoItem = TitleValueViewState(
            title = resourceManager.getString(R.string.common_network_fee),
            value = feeFormatted,
            additionalValue = feeFiat
        )

        val iconOverrideResId = overrides[ConfirmSendFragment.KEY_OVERRIDE_ICON_RES_ID] as? Int

        ConfirmSendViewState(
            iconOverrideResId = iconOverrideResId,
            chainIconUrl = asset.token.configuration.chainIcon ?: asset.token.configuration.iconUrl,
            fromInfoItem = fromInfoItem,
            toInfoItem = toInfoItem,
            amountInfoItem = amountInfoItem,
            tipInfoItem = tipInfoItem,
            feeInfoItem = feeInfoItem,
            buttonState = buttonState,
            isLoading = isSubmitting
        )
    }.stateIn(this, SharingStarted.Eagerly, ConfirmSendViewState.default)

    override fun onNavigationClick() {
        router.back()
    }

    override fun copyRecipientAddressClicked() {
        launch {
            val chainId = transferDraft.assetPayload.chainId
            val chain = chainRegistry.getChain(chainId)
            val supportedExplorers = chain.explorers.getSupportedAddressExplorers(transferDraft.recipientAddress)
            val externalActionsPayload = ExternalAccountActions.Payload(
                value = transferDraft.recipientAddress,
                chainId = chainId,
                chainName = chain.name,
                explorers = supportedExplorers
            )

            externalAccountActions.showExternalActions(externalActionsPayload)
        }
    }

    override fun onNextClick() {
        launch {
            val asset = assetFlow.firstOrNull() ?: return@launch
            val token = asset.token.configuration

            val inPlanks = token.planksFromAmount(transferDraft.amount)
            val fee = token.planksFromAmount(transferDraft.fee)
            val recipientAddress = transferDraft.recipientAddress
            val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@launch

            if (!skipEdValidation) {
                val validationProcessResult = validateTransferUseCase.validateExistentialDeposit(
                    amountInPlanks = inPlanks,
                    originAsset = asset,
                    destinationChainId = asset.token.configuration.chainId,
                    destinationAddress = recipientAddress,
                    originAddress = selfAddress,
                    originFee = fee,
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
            }
            performTransfer()
        }
    }

    override fun onItemClick(code: Int) {
        when (code) {
            ConfirmSendViewState.CODE_WARNING_CLICK -> openWarningAlert()
        }
    }

    private fun openWarningAlert() {
        launch {
            val symbol = assetFlow.first().token.configuration.symbol

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
            val token = assetFlow.firstOrNull()?.token?.configuration ?: return@launch
            val fee = feeFlow.firstOrNull() ?: return@launch

            transferSubmittingFlow.value = true

            val tipInPlanks = transferDraft.tip?.let { token.planksFromAmount(it) }
            val result = withContext(Dispatchers.Default) {

                interactor.performTransfer(createTransfer(token, fee), fee, tipInPlanks)
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

    private suspend fun createTransfer(token: Asset, fee: BigDecimal = transferDraft.fee): Transfer {
        val currentAddress = currentAccountAddress(transferDraft.assetPayload.chainId)
        requireNotNull(currentAddress)

        val isSendBokoloCash = token.currencyId == bokoloCashTokenId
        val utilityAsset = utilityAssetFlow.firstOrNull() ?: error("Utility asset not configured")
        val asset = assetFlow.firstOrNull() ?: error("Asset not configured")

        val feeRequiredTokens = if (isSendBokoloCash && utilityAsset.transferable < fee.orZero()) {
            val swapDetails = polkaswapInteractor.calcDetails(
                availableDexPaths = listOf(0),
                tokenFrom = asset,
                tokenTo = utilityAsset,
                amount = fee,
                desired = WithDesired.OUTPUT,
                slippageTolerance = 1.5,
                market = Market.SMART
            )
            swapDetails.getOrNull()?.amount
        } else {
            null
        }

        return with(transferDraft) {
            Transfer(
                recipient = recipientAddress,
                sender = currentAddress,
                amount = amount,
                chainAsset = token,
                comment = transferComment,
                estimateFee = fee + FEE_CORRECTION,
                maxAmountIn = feeRequiredTokens?.let { it * FEE_RESERVE_TOLERANCE }
            )
        }
    }
}
