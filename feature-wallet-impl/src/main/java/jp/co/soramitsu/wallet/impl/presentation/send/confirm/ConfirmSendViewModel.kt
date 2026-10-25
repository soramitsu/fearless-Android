package jp.co.soramitsu.wallet.impl.presentation.send.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireException
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.Asset
import jp.co.soramitsu.core.models.Ecosystem
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
import jp.co.soramitsu.wallet.api.presentation.mixin.TransferValidityChecks
import jp.co.soramitsu.wallet.impl.data.mappers.mapAssetToAssetModel
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.CBDCTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.SubstrateTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.TonTransferParams
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendFormat
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendError
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendException
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NativeSendQuoteStore
import java.util.UUID
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

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
    private val validateTransferUseCase: ValidateTransferUseCase,
    private val nativeQuotes: NativeSendQuoteStore = NativeSendQuoteStore()
) : BaseViewModel(),
    ExternalAccountActions by externalAccountActions,
    TransferValidityChecks by transferValidityChecks,
    ConfirmSendScreenInterface {

    private val transferDraft = savedStateHandle.get<TransferDraft>(ConfirmSendFragment.KEY_DRAFT)
        ?: error("Required data not provided for send confirmation")
    private val quoteId = savedStateHandle.get<String>("native_send_quote_id") ?: UUID.randomUUID().toString().also {
        savedStateHandle["native_send_quote_id"] = it
    }
    private val feeReady = MutableStateFlow(false)

    private val phishingType =
        savedStateHandle.get<PhishingType>(ConfirmSendFragment.KEY_PHISHING_TYPE)
    private val overrides =
        savedStateHandle.get<Map<String, Any?>>(ConfirmSendFragment.KEY_OVERRIDES).orEmpty()
    private val transferComment =
        savedStateHandle.get<String>(ConfirmSendFragment.KEY_TRANSFER_COMMENT)
    private val skipEdValidation =
        savedStateHandle.get<Boolean>(ConfirmSendFragment.KEY_SKIP_ED_VALIDATION) == true

    private val _openValidationWarningEvent =
        MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> =
        _openValidationWarningEvent

    private val recipientFlow =
        interactor.observeAddressBook(transferDraft.assetPayload.chainId).map { contacts ->
            val contactName = contacts.firstOrNull {
                it.address.equals(
                    transferDraft.recipientAddress,
                    ignoreCase = true
                )
            }?.name
            getAddressModel(transferDraft.recipientAddress, contactName)
        }

    private val senderFlow = flowOf {
        currentAccountAddress(transferDraft.assetPayload.chainId)?.let { address ->
            val walletName = interactor.getSelectedMetaAccount().name
            getAddressModel(address, walletName)
        }
    }

    private enum class SubmissionState { Ready, Pending, Complete }

    private val submissionState = MutableStateFlow(SubmissionState.Ready)
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_confirm),
        false
    )

    private val assetFlow = interactor.assetFlow(
        transferDraft.assetPayload.chainId,
        transferDraft.assetPayload.chainAssetId
    ).share()

    private val chainFlow = flowOf {
        interactor.getChain(transferDraft.assetPayload.chainId)
    }.share()

    @OptIn(ExperimentalCoroutinesApi::class)
    val utilityAssetFlow = chainFlow
        .mapNotNull { it.utilityAsset?.id }
        .flatMapLatest { assetId ->
            interactor.assetFlow(transferDraft.assetPayload.chainId, assetId)
        }

    private val buttonStateFlow = combine(submissionState, feeReady, assetFlow, utilityAssetFlow) { submission, ready, asset, utility ->
        ButtonViewState(
            text = resourceManager.getString(R.string.common_confirm),
            enabled = submission == SubmissionState.Ready && ready && asset.transferable != null && utility.transferable != null
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val feeFlow = combine(assetFlow, utilityAssetFlow) { asset, utility -> asset to utility }
        .flatMapLatest { (asset, utility) ->
            kotlinx.coroutines.flow.flow<BigDecimal?> {
                feeReady.value = false
                emit(null)
                if (asset.transferable != null && utility.transferable != null) {
                    val transfer = createTransfer(asset.token.configuration)
                    interactor.observeTransferFee(transfer).collect { emit(it) }
                }
            }.catch {
                if (it is CancellationException) throw it
                feeReady.value = false
                showError(NativeSendError.display(it, resourceManager))
                emit(null)
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val state = MutableStateFlow(ConfirmSendViewState.default)

    init {
        viewModelScope.launch {
            val iconOverrideResId = overrides[ConfirmSendFragment.KEY_OVERRIDE_ICON_RES_ID] as? Int
            state.update { prev -> prev.copy(iconOverrideResId = iconOverrideResId) }

            chainFlow.onEach { chain ->
                state.update { it.copy(nativeReview = chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana(), networkInfoItem = TitleValueViewState(
                    title = resourceManager.getString(R.string.common_network), value = chain.name)) }
            }.launchIn(viewModelScope)

            recipientFlow.onEach { recipient ->
                val native = chainFlow.first().let { it.isUniversalWalletBitcoin() || it.isUniversalWalletSolana() }
                val isRecipientNameSpecified = !recipient.name.isNullOrEmpty()
                val hasOverriddenToValue =
                    overrides.containsKey(ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE)

                val toValue = when {
                    native -> if (isRecipientNameSpecified) recipient.name else recipient.address
                    hasOverriddenToValue -> overrides[ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE] as String
                    isRecipientNameSpecified -> recipient.name
                    else -> recipient.address.shortenAddress()
                }
                val toAdditionalValue = when {
                    native -> if (isRecipientNameSpecified) recipient.address else null
                    hasOverriddenToValue -> null
                    isRecipientNameSpecified -> recipient.address.shortenAddress()
                    else -> null
                }
                val toInfoItem = TitleValueViewState(
                    title = resourceManager.getString(R.string.choose_amount_to),
                    value = toValue,
                    additionalValue = toAdditionalValue,
                    clickState = phishingType?.let {
                        TitleValueViewState.ClickState.Value(
                            R.drawable.ic_alert_16,
                            ConfirmSendViewState.CODE_WARNING_CLICK
                        )
                    }
                )
                state.update { prevState -> prevState.copy(toInfoItem = toInfoItem) }
            }.launchIn(viewModelScope)

            senderFlow.onEach { sender ->
                val native = chainFlow.first().let { it.isUniversalWalletBitcoin() || it.isUniversalWalletSolana() }
                val isSenderNameSpecified = !sender?.name.isNullOrEmpty()
                val fromInfoItem = TitleValueViewState(
                    title = resourceManager.getString(R.string.transaction_details_from),
                    value = if (isSenderNameSpecified) sender?.name else if (native) sender?.address else sender?.address?.shortenAddress(),
                    additionalValue = if (isSenderNameSpecified) { if (native) sender?.address else sender?.address?.shortenAddress() } else null
                )
                state.update { prevState -> prevState.copy(fromInfoItem = fromInfoItem) }
            }.launchIn(viewModelScope)

            combine(assetFlow, nativeQuotes.reviews) { asset, reviews -> asset to reviews[quoteId] }.onEach { (asset, review) ->
                val assetModel = mapAssetToAssetModel(asset)
                val amountInfoItem = TitleValueViewState(
                    title = resourceManager.getString(R.string.common_amount),
                    value = review?.let { NativeSendFormat.amount(it.amount, assetModel.token.configuration.symbol) }
                        ?: transferDraft.amount.formatCryptoDetail(assetModel.token.configuration.symbol),
                    additionalValue = assetModel.getAsFiatWithCurrency(review?.amount ?: transferDraft.amount)
                )
                state.update { prevState ->
                    prevState.copy(
                        amountInfoItem = amountInfoItem,
                        chainIconUrl = asset.token.configuration.chainIcon
                            ?: asset.token.configuration.iconUrl
                    )
                }
            }.launchIn(viewModelScope)

            utilityAssetFlow.onEach {
                state.update { prevState -> prevState.copy() }
            }.launchIn(viewModelScope)

            buttonStateFlow.onEach {
                state.update { prevState -> prevState.copy(buttonState = it) }
            }.launchIn(viewModelScope)

            submissionState.onEach { submission ->
                state.update { prevState -> prevState.copy(isLoading = submission == SubmissionState.Pending) }
            }.launchIn(viewModelScope)

            combine(assetFlow, utilityAssetFlow, feeFlow, nativeQuotes.reviews) { asset, utilityAsset, fee, reviews ->
                val utilityAvailable = utilityAsset.transferable
                if (utilityAvailable == null || asset.transferable == null || fee == null) {
                    feeReady.value = false
                    state.update { it.copy(feeInfoItem = TitleValueViewState(
                        resourceManager.getString(R.string.common_network_fee),
                        resourceManager.getString(R.string.portfolio_balance_unavailable)
                    )) }
                    return@combine
                }
                val review = reviews[quoteId]
                val tipInfoItem = transferDraft.tip?.let { tip ->
                    TitleValueViewState(
                        title = resourceManager.getString(R.string.choose_amount_tip),
                        value = tip.formatCryptoDetail(utilityAsset.token.configuration.symbol),
                        additionalValue = utilityAsset.getAsFiatWithCurrency(transferDraft.tip)
                    )
                }

                val isSendBokoloCash = asset.token.configuration.currencyId == bokoloCashTokenId

                val showFeeAsset = if (isSendBokoloCash && utilityAvailable < fee) {
                    asset
                } else {
                    utilityAsset
                }

                val assetFeeAmount = if (isSendBokoloCash && utilityAvailable < fee) {
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
                    review?.networkFee ?: fee
                }

                val feeFormatted =
                    assetFeeAmount?.let { if (review != null) NativeSendFormat.amount(it, showFeeAsset.token.configuration.symbol)
                        else it.formatCryptoDetail(showFeeAsset.token.configuration.symbol) }
                val feeFiat = assetFeeAmount?.applyFiatRate(showFeeAsset.token.fiatRate)
                    ?.formatFiat(showFeeAsset.token.fiatSymbol)

                val feeInfoItem = TitleValueViewState(
                    title = resourceManager.getString(R.string.common_network_fee),
                    value = feeFormatted,
                    additionalValue = feeFiat
                )

                state.update { prevState ->
                    prevState.copy(
                        feeInfoItem = feeInfoItem,
                        amountInfoItem = review?.let { TitleValueViewState(resourceManager.getString(R.string.common_amount),
                            NativeSendFormat.amount(it.amount, asset.token.configuration.symbol), asset.getAsFiatWithCurrency(it.amount)) } ?: prevState.amountInfoItem,
                        tipInfoItem = tipInfoItem,
                        accountCreationInfoItem = review?.accountCreation?.takeIf { it.signum() > 0 }?.let { rent ->
                            TitleValueViewState(resourceManager.getString(R.string.send_account_creation),
                                NativeSendFormat.amount(rent, utilityAsset.token.configuration.symbol),
                                utilityAsset.getAsFiatWithCurrency(rent))
                        },
                        feeRateInfoItem = review?.satPerVbyte?.let { rate ->
                            TitleValueViewState(resourceManager.getString(R.string.send_fee_rate),
                                resourceManager.getString(R.string.send_sat_per_vbyte, BigDecimal.valueOf(rate).stripTrailingZeros().toPlainString()))
                        }
                    )
                }
                feeReady.value = true
            }.launchIn(viewModelScope)
        }
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun copyRecipientAddressClicked() {
        launch {
            val chainId = transferDraft.assetPayload.chainId
            val chain = chainRegistry.getChain(chainId)
            val supportedExplorers =
                chain.explorers.getSupportedAddressExplorers(transferDraft.recipientAddress)
            val externalActionsPayload = ExternalAccountActions.Payload(
                value = transferDraft.recipientAddress,
                explorers = supportedExplorers
            )
            externalAccountActions.showExternalActions(externalActionsPayload)
        }
    }

    override fun onNextClick() {
        if (!feeReady.value || !submissionState.compareAndSet(SubmissionState.Ready, SubmissionState.Pending)) return
        launch {
            try {
                val asset = assetFlow.firstOrNull() ?: return@launch
                val token = asset.token.configuration
                val chain = chainFlow.first()
                if (chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()) {
                    performTransfer()
                    return@launch
                }

                val inPlanks = token.planksFromAmount(transferDraft.amount)
                val fee = token.planksFromAmount(transferDraft.fee)
                val recipientAddress = transferDraft.recipientAddress
                val selfAddress =
                    currentAccountAddress(asset.token.configuration.chainId) ?: return@launch

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
                        if (it is CancellationException) throw it
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
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                showError(NativeSendError.display(error, resourceManager))
            }
        }.invokeOnCompletion {
            submissionState.compareAndSet(SubmissionState.Pending, SubmissionState.Ready)
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
        if (submissionState.value != SubmissionState.Ready) return
        confirmedValidations.add(validationResult)
        onNextClick()
    }

    private suspend fun performTransfer() {
        val token = assetFlow.firstOrNull()?.token?.configuration ?: return
        val fee = feeFlow.value ?: return
        val result = withContext(Dispatchers.Default) {
            interactor.performTransfer(createTransfer(token, fee)).also {
                // Preserve known success even if cancellation discards the dispatch-back result.
                if (it.isSuccess) submissionState.value = SubmissionState.Complete
            }
        }
        if (result.isSuccess) {
            val chain = chainFlow.first()
            router.finishSendFlow()
            if (chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()) {
                router.openOperationSuccess(result.getOrNull(), token.chainId,
                    resourceManager.getString(R.string.send_submitted_message), resourceManager.getString(R.string.send_submitted_title))
            } else router.openOperationSuccess(result.getOrNull(), token.chainId)
        } else {
            val error = result.requireException()
            if (error is CancellationException) throw error
            when {
                error is NotValidTransferStatus -> processInvalidStatus(error.status)
                error is NativeSendException && error.code == NativeSendException.Code.PAYMENT_PENDING ->
                    showError(resourceManager.getString(R.string.send_existing_pending))
                else -> if (nativeQuotes.reviews.value.containsKey(quoteId)) showError(NativeSendError.display(error, resourceManager)) else showError(error)
            }
        }
    }

    private fun processInvalidStatus(status: TransferValidityStatus) {
        when (status) {
            is TransferValidityLevel.Warning.Status -> transferValidityChecks.showTransferWarning(
                status
            )

            is TransferValidityLevel.Error.Status -> transferValidityChecks.showTransferError(status)
        }
    }

    private suspend fun getAddressModel(
        address: String,
        accountName: String? = null
    ): AddressModel {
        return addressIconGenerator.createAddressModel(address, ICON_IN_DP, accountName)
    }

    private suspend fun requireDraftWallet() {
        jp.co.soramitsu.wallet.impl.presentation.send.NativeSendDraftGuard.requireCurrent(transferDraft, interactor.getSelectedMetaAccount().id)
    }

    private suspend fun createTransfer(
        token: Asset,
        fee: BigDecimal = transferDraft.fee
    ): Transfer {
        requireDraftWallet()
        val currentAddress = currentAccountAddress(transferDraft.assetPayload.chainId)
        requireNotNull(currentAddress)

        val isSendBokoloCash = token.currencyId == bokoloCashTokenId
        val utilityAsset = utilityAssetFlow.firstOrNull() ?: error("Utility asset not configured")
        val asset = assetFlow.firstOrNull() ?: error("Asset not configured")
        val chain = chainFlow.first()

        val utilityAvailable = requireNotNull(utilityAsset.transferable) {
            resourceManager.getString(R.string.portfolio_balance_unavailable)
        }
        requireNotNull(asset.transferable) { resourceManager.getString(R.string.portfolio_balance_unavailable) }
        val feeRequiredTokens = if (isSendBokoloCash && utilityAvailable < fee) {
            val swapDetails = polkaswapInteractor.calcDetails(
                availableDexPaths = listOf(0),
                tokenFrom = asset,
                tokenTo = utilityAsset,
                amount = fee,
                desired = WithDesired.OUTPUT,
                slippageTolerance = 1.5,
                market = Market.SMART
            )
            requireNotNull(swapDetails.getOrThrow()) { "Fee conversion quote is unavailable" }.amount
        } else {
            null
        }
        val tipInPlanks = transferDraft.tip?.let { token.planksFromAmount(it) }

        val usesAppId = chainFlow.firstOrNull()?.isUsesAppId == true
        val appId = if (usesAppId) BigInteger.ZERO else null
        val maxAmountInPlanks =
            feeRequiredTokens?.let { token.planksFromAmount(it * FEE_RESERVE_TOLERANCE) }
        val additionalParams = when {
            isSendBokoloCash -> CBDCTransferParams(
                transferComment,
                maxAmountInPlanks,
                tipInPlanks,
                appId
            )

            chain.ecosystem == Ecosystem.Substrate || chain.ecosystem == Ecosystem.EthereumBased -> {
                SubstrateTransferParams(tipInPlanks, appId)
            }

            chain.ecosystem == Ecosystem.Ton -> {
                TonTransferParams(transferDraft.message)
            }

            else -> null
        }

        requireDraftWallet()
        return with(transferDraft) {
            Transfer(
                recipient = recipientAddress,
                sender = currentAddress,
                amount = nativeQuotes.reviews.value[quoteId]?.amount ?: amount,
                chainAsset = token,
                estimateFee = if (chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()) fee else fee + FEE_CORRECTION,
                additionalParams = additionalParams,
                quoteId = quoteId.takeIf { chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana() },
                sendMax = sendMax
            )
        }
    }
}
