package jp.co.soramitsu.wallet.impl.presentation.cross_chain.confirm

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.accountId
import jp.co.soramitsu.account.api.domain.model.address
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
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionJournal
import jp.co.soramitsu.wallet.impl.data.repository.CrossChainSubmissionReconciler
import jp.co.soramitsu.wallet.impl.domain.CrossChainSubmissions
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.ReviewedPolkaswapBridgeInteractor
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.exactXcmAmountInPlanks
import jp.co.soramitsu.wallet.impl.domain.exactXcmOriginFeeInPlanks
import jp.co.soramitsu.wallet.impl.domain.interfaces.NotValidTransferStatus
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.CrossChainTransfer
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityLevel
import jp.co.soramitsu.wallet.impl.domain.model.TransferValidityStatus
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import jp.co.soramitsu.xcm.ReviewedBridgeQuote
import kotlinx.coroutines.CancellationException
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
    private val xcmInteractor: XcmInteractor,
    private val reviewedBridgeInteractor: ReviewedPolkaswapBridgeInteractor,
    private val accountInteractor: AccountInteractor,
    private val crossChainSubmissions: CrossChainSubmissions
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

    // The wallet validated at setup, which alone may sign; shown so the user sees which wallet pays.
    private val senderFlow = flowOf {
        val wallet = accountInteractor.getMetaAccount(transferDraft.metaAccountId)
        wallet.address(interactor.getChain(transferDraft.originChainId))?.let { address ->
            getAddressModel(address, wallet.name)
        }
    }

    private val walletUnchangedFlow = interactor.selectedMetaAccountFlow()
        .map { it.id == transferDraft.metaAccountId }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = false)

    private val transferSubmittingFlow = MutableStateFlow(false)
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    // An unsettled earlier transfer of this wallet, origin network and account; no new one is signed until it settles.
    private val pendingFlow = MutableStateFlow<PendingTransfer?>(null)

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_confirm),
        true
    )

    private val buttonStateFlow = kotlinx.coroutines.flow.combine(
        transferSubmittingFlow,
        walletUnchangedFlow,
        pendingFlow
    ) { submitting, walletUnchanged, pending ->
        val text = when {
            pending == null || pending.status == PendingStatus.Expired -> R.string.common_confirm
            pending.status == PendingStatus.Included -> if (pending.isThisTransfer) R.string.common_done else R.string.common_confirm
            else -> R.string.send_check_status
        }
        ButtonViewState(
            text = resourceManager.getString(text),
            enabled = !submitting && walletUnchanged && pending?.status != PendingStatus.Checking
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

    val transferableAssetFlow = interactor
        .assetFlow(transferDraft.originChainId, transferDraft.chainAssetId)
        .map(::mapAssetToAssetModel)

    private val contentStateFlow: StateFlow<CrossChainConfirmViewState> = combine(
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
        val fromInfoItem = sender?.let {
            val isWalletNameSpecified = !it.name.isNullOrEmpty()
            TitleValueViewState(
                title = resourceManager.getString(R.string.transaction_details_from),
                value = if (isWalletNameSpecified) it.name else it.address.shortenAddress(),
                additionalValue = if (isWalletNameSpecified) it.address.shortenAddress() else null
            )
        }
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

        val destinationFeeInfoItem = if (transferDraft.destinationFeeIncluded) {
            // Not "0": the destination deducts its fee from the amount.
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_destination_network_fee),
                value = resourceManager.getString(R.string.cross_chain_destination_fee_included)
            )
        } else {
            TitleValueViewState(
                title = resourceManager.getString(R.string.common_destination_network_fee),
                value = transferDraft.destinationFee.formatCryptoDetail(transferableAsset.token.configuration.symbol),
                additionalValue = transferableAsset.getAsFiatWithCurrency(transferDraft.destinationFee)
            )
        }

        CrossChainConfirmViewState(
            originChainIcon = GradientIconData(
                url = originNetwork?.icon,
                color = originNetwork?.utilityAsset?.color
            ),
            destinationChainIcon = GradientIconData(
                url = destinationNetwork?.icon,
                color = destinationNetwork?.utilityAsset?.color
            ),
            fromInfoItem = fromInfoItem,
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

    val state: StateFlow<CrossChainConfirmViewState> = kotlinx.coroutines.flow.combine(
        contentStateFlow,
        pendingFlow
    ) { content, pending ->
        content.copy(
            pendingMessage = pending?.let { resourceManager.getString(it.status.message) },
            pendingTransactionHash = pending?.submission?.transactionHash?.shortenAddress()
        )
    }.stateIn(this, SharingStarted.Eagerly, CrossChainConfirmViewState.default)

    init {
        // Settle an earlier transfer first: claim the in-flight flag so that a tap cannot submit meanwhile.
        if (transferSubmittingFlow.compareAndSet(expect = false, update = true)) {
            launch {
                try {
                    if (loadPending()) pendingFlow.value?.let { checkPending(it) }
                } finally {
                    transferSubmittingFlow.value = false
                }
            }
        }
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun copyRecipientAddressClicked() {
        launch {
            val chain = destinationNetworkFlow.value ?: return@launch
            val supportedExplorers = chain.explorers.getSupportedAddressExplorers(transferDraft.recipientAddress)
            val externalActionsPayload = ExternalAccountActions.Payload(
                value = transferDraft.recipientAddress,
                explorers = supportedExplorers
            )

            externalAccountActions.showExternalActions(externalActionsPayload)
        }
    }

    override fun onNextClick() {
        // Claim the in-flight flag before the first suspension point. Validation and submission both
        // suspend, so a second tap (or a re-entry through warningConfirmed) arriving in that window
        // must not start another transfer: each submission would sign with a fresh nonce and move funds again.
        if (!transferSubmittingFlow.compareAndSet(expect = false, update = true)) return

        launch {
            try {
                val pending = pendingFlow.value
                when {
                    pending == null || pending.status == PendingStatus.Expired -> validateAndSubmit()
                    // A retry of a transfer that was sent would send it a second time.
                    pending.status == PendingStatus.Included ->
                        if (pending.isThisTransfer) router.finishSendFlow() else validateAndSubmit()
                    else -> checkPending(pending)
                }
            } finally {
                transferSubmittingFlow.value = false
            }
        }
    }

    /** Reads only finalized blocks; it never signs or resends the earlier transfer. */
    private suspend fun checkPending(pending: PendingTransfer) {
        val scope = submissionScope() ?: return
        pendingFlow.value = pending.copy(status = PendingStatus.Checking)
        pendingFlow.value = try {
            when (crossChainSubmissions.check(scope, pending.submission.attemptId)) {
                CrossChainSubmissionReconciler.Outcome.Settled -> null
                is CrossChainSubmissionReconciler.Outcome.Included -> pending.copy(status = PendingStatus.Included)
                is CrossChainSubmissionReconciler.Outcome.Expired -> pending.copy(status = PendingStatus.Expired)
                is CrossChainSubmissionReconciler.Outcome.Unsettled -> pending.copy(status = PendingStatus.NotFound)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            pending.copy(status = PendingStatus.CheckFailed)
        }
    }

    /** Whether an unsettled transfer is kept for this scope; an unreadable record blocks like a kept one. */
    private suspend fun loadPending(): Boolean {
        val scope = submissionScope() ?: return false
        val submission = try {
            crossChainSubmissions.pending(scope)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            showError(resourceManager.getString(R.string.send_storage_unavailable))
            return true
        } ?: return false
        pendingFlow.value = PendingTransfer(submission, PendingStatus.Unknown, isThisTransfer = submission.isRetryOfDraft())
        return true
    }

    private suspend fun submissionScope(): CrossChainSubmissionJournal.Scope? {
        val wallet = accountInteractor.getMetaAccount(transferDraft.metaAccountId)
        val originChain = interactor.getChain(transferDraft.originChainId)
        val accountId = wallet.accountId(originChain) ?: return null
        return crossChainSubmissions.scope(wallet.id, originChain, accountId)
    }

    private fun CrossChainSubmissionJournal.Submission.isRetryOfDraft(): Boolean {
        // The XCM record keeps the amount with the destination fee added on top; the bridge keeps the amount.
        val sent = if (transferDraft.isReviewedBridge) transferDraft.amount else transferDraft.amount + transferDraft.destinationFee
        val sentInPlanks = runCatching { sent.scaleByPowerOfTen(details.precision).toBigIntegerExact() }.getOrNull()
        return details.destinationChainId == transferDraft.destinationChainId &&
            details.assetId == transferDraft.chainAssetId &&
            details.recipient == transferDraft.recipientAddress &&
            details.amountInPlanks == sentInPlanks
    }

    private suspend fun validateAndSubmit() {
        // Balances were validated for the draft's wallet; another selected wallet must not sign this transfer.
        if (interactor.getSelectedMetaAccount().id != transferDraft.metaAccountId) {
            showError(resourceManager.getString(R.string.send_review_changed))
            return
        }
        // The reviewed bridge executor repeats its own exact minimum, fee, balance, account,
        // runtime and recipient checks immediately before submission.
        if (transferDraft.isReviewedBridge) {
            performTransfer()
            return
        }
        val destinationChain = destinationNetworkFlow.value ?: return
        val originChain = originNetworkFlow.value ?: return
        val asset = originAssetFlow.firstOrNull() ?: return
        val token = asset.token.configuration
        val utilityAsset = utilityAssetFlow.firstOrNull() ?: return

        val amountAndDestinationFeeInPlanks: BigInteger
        val destinationFeeInPlanks: BigInteger
        val originFee: BigInteger
        try {
            amountAndDestinationFeeInPlanks = exactXcmAmountInPlanks(createTransfer(token))
            destinationFeeInPlanks = transferDraft.destinationFee.scaleByPowerOfTen(token.precision).toBigIntegerExact()
            val feeAsset = utilityAsset.token.configuration
            originFee = exactXcmOriginFeeInPlanks(
                originChainId = transferDraft.originChainId,
                expectedUtilityAssetId = checkNotNull(originChain.utilityAsset?.id) {
                    "XCM origin utility asset is unavailable"
                },
                utilityAsset = feeAsset,
                quotedFee = transferDraft.originFee
            )
        } catch (error: RuntimeException) {
            showError(error)
            return
        }
        val recipientAddress = transferDraft.recipientAddress
        val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return

        val validationProcessResult = validateTransferUseCase.validateExistentialDeposit(
            amountInPlanks = amountAndDestinationFeeInPlanks,
            originAsset = asset,
            destinationChainId = destinationChain.id,
            destinationAddress = recipientAddress,
            originAddress = selfAddress,
            originFee = originFee,
            confirmedValidations = confirmedValidations,
            destinationFeeInPlanks = destinationFeeInPlanks
        )

        // error occurred inside validation
        validationProcessResult.exceptionOrNull()?.let {
            showError(it)
            return
        }
        val validationResult = validationProcessResult.requireValue()

        ValidationException.fromValidationResult(validationResult, resourceManager)?.let {
            if (it is ValidationWarning) {
                _openValidationWarningEvent.value = Event(validationResult to it)
            } else {
                showError(it)
            }
            return
        }

        performTransfer()
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

    // Runs inside the coroutine of onNextClick, which owns transferSubmittingFlow for its whole duration.
    private suspend fun performTransfer() {
        val originAsset = originAssetFlow.value ?: return
        val token = originAsset.token.configuration

        val result = withContext(Dispatchers.Default) {
            if (transferDraft.isReviewedBridge) {
                reviewedBridgeInteractor.submit(
                    walletId = transferDraft.metaAccountId,
                    providerId = requireNotNull(transferDraft.providerId),
                    routeId = requireNotNull(transferDraft.routeId),
                    originChainId = transferDraft.originChainId,
                    destinationChainId = transferDraft.destinationChainId,
                    asset = token,
                    recipientAddress = transferDraft.recipientAddress,
                    amount = transferDraft.amount,
                    confirmedQuote = transferDraft.reviewedBridgeQuote()
                )
            } else {
                xcmInteractor.performCrossChainTransfer(createTransfer(token), transferDraft.metaAccountId)
            }
        }
        if (result.isSuccess) {
            val operationHash = result.getOrNull()
            router.finishSendFlow()
            router.openOperationSuccess(operationHash, token.chainId)
        } else {
            val error = result.requireException()

            if (error is NotValidTransferStatus) {
                processInvalidStatus(error.status)
            } else if (!loadPending()) {
                // Nothing was retained, so nothing was sent: the user may try again.
                showError(error)
            }
            // Otherwise the retained transfer may have reached the network: it is shown as unsettled instead.
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

    private enum class PendingStatus(val message: Int) {
        Unknown(R.string.cross_chain_pending_message),
        Checking(R.string.pool_join_recovery_checking),
        NotFound(R.string.cross_chain_pending_not_found),
        CheckFailed(R.string.cross_chain_pending_unavailable),
        Included(R.string.cross_chain_pending_included),
        Expired(R.string.cross_chain_pending_expired)
    }

    private data class PendingTransfer(
        val submission: CrossChainSubmissionJournal.Submission,
        val status: PendingStatus,
        /** The kept transfer is this draft, so after it was included it must not be submitted again. */
        val isThisTransfer: Boolean
    )

    private fun CrossChainTransferDraft.reviewedBridgeQuote() = ReviewedBridgeQuote(
        providerId = requireNotNull(providerId),
        routeId = requireNotNull(routeId),
        runtimeFingerprint = requireNotNull(runtimeFingerprint),
        executionFingerprint = requireNotNull(executionFingerprint),
        originFeeInPlanks = BigInteger(requireNotNull(originFeeInPlanks)),
        destinationFeeInPlanks = BigInteger(requireNotNull(destinationFeeInPlanks)),
        effectiveMinimumInPlanks = BigInteger(requireNotNull(effectiveMinimumInPlanks))
    )
}
