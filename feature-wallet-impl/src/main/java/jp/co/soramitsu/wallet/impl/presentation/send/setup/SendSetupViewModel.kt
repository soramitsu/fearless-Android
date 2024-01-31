package jp.co.soramitsu.wallet.impl.presentation.send.setup

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.greaterThen
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedException
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.QrContentSora
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainSelectScreenContract
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SendSetupViewModel @Inject constructor(
    private val sharedState: SendSharedState,
    val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val router: WalletRouter,
    private val clipboardManager: ClipboardManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase
) : BaseViewModel(), SendSetupScreenInterface {
    companion object {
        const val SLIPPAGE_TOLERANCE = 1.35
        const val RETRY_TIMES = 3L
    }

    enum class ToggleState {
         INITIAL, CHECKED, CONFIRMED
    }

    private val _openScannerEvent = MutableLiveData<Event<Unit>>()
    val openScannerEvent: LiveData<Event<Unit>> = _openScannerEvent

    private val _openValidationWarningEvent =
        MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> =
        _openValidationWarningEvent

    val payload: AssetPayload? = savedStateHandle[SendSetupFragment.KEY_PAYLOAD]
    private val initSendToAddress: String? = savedStateHandle[SendSetupFragment.KEY_INITIAL_ADDRESS]
    private val tokenCurrencyId: String? = savedStateHandle[SendSetupFragment.KEY_TOKEN_ID]
    private val initSendToAmount: BigDecimal? = savedStateHandle[SendSetupFragment.KEY_INITIAL_AMOUNT]
    private val lockSendToAmount: Boolean = savedStateHandle.get<Boolean>(SendSetupFragment.KEY_LOCK_AMOUNT) == true

    val isInitConditionsCorrect = if (initSendToAddress.isNullOrEmpty() && payload == null) {
        error("Required data (asset or address) not specified")
    } else {
        true
    }

    private val initialAmount = initSendToAmount.orZero()
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val chainIdFlow = sharedState.chainIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val selectedChain = chainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }

    private val selectedChainItem = selectedChain.map { chain ->
        chain?.let {
            ChainSelectScreenContract.State.ItemState.Impl(
                id = chain.id,
                imageUrl = chain.icon,
                title = chain.name,
                isSelected = false,
                tokenSymbols = chain.assets.associate { it.id to it.symbol }
            )
        }
    }

    private val defaultAddressInputState = AddressInputState(
        title = resourceManager.getString(R.string.send_fund),
        "",
        R.drawable.ic_address_placeholder
    )

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_transferable_format, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount,
        allowAssetChoose = false
    )

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_continue),
        true
    )

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.send_fund),
        R.drawable.ic_arrow_left_24
    )

    private val defaultState = SendSetupViewState(
        toolbarViewState,
        defaultAddressInputState,
        defaultAmountInputState,
        SelectorState.default,
        FeeInfoViewState.default,
        warningInfoState = null,
        defaultButtonState,
        isSoftKeyboardOpen = false,
        isInputLocked = false,
        isHistoryAvailable = false,
        sendAllChecked = false,
        sendAllAllowed = false
    )

    private val assetFlow: StateFlow<Asset?> = sharedState.assetIdToChainIdFlow.map {
        it?.let { (assetId, chainId) ->
            walletInteractor.getCurrentAsset(chainId, assetId)
        }
    }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val amountInputFocusFlow = MutableStateFlow(false)

    private val addressInputFlow = MutableStateFlow(initSendToAddress.orEmpty())
    private val addressInputTrimmedFlow = addressInputFlow.map { it.trim() }

    val isSoftKeyboardOpenFlow = MutableStateFlow(lockSendToAmount && initialAmount.isZero())

//    private val maxAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredAmountBigDecimalFlow = MutableStateFlow(initialAmount)
    private val visibleAmountFlow = MutableStateFlow(initialAmount)
    private val initialAmountFlow = MutableStateFlow(initialAmount.takeIf { it.isNotZero() })
    private val lockAmountInputFlow = MutableStateFlow(initialAmount.isNotZero())
    private val lockInputFlow = MutableStateFlow(lockSendToAmount)

    private val isInputAddressValidFlow =
        combine(addressInputTrimmedFlow, chainIdFlow) { addressInput, chainId ->
            when (chainId) {
                null -> false
                else -> walletInteractor.validateSendAddress(chainId, addressInput)
            }
        }.stateIn(this, SharingStarted.Eagerly, false)

    private val chainSelectorStateFlow =
        combine(selectedChainItem, lockInputFlow) { it: ChainSelectScreenContract.State.ItemState?, isLock: Boolean ->
            SelectorState(
                title = resourceManager.getString(R.string.common_network),
                subTitle = it?.title,
                iconUrl = it?.imageUrl,
                clickable = isLock.not(),
                actionIcon = if (isLock) null else R.drawable.ic_arrow_down
            )
        }.stateIn(this, SharingStarted.Eagerly, SelectorState.default)


    private val amountInputViewState: Flow<AmountInputViewState> = combine(
        visibleAmountFlow,
        initialAmountFlow,
        assetFlow,
        amountInputFocusFlow,
        lockAmountInputFlow,
        lockInputFlow
    ) { amount, initialAmount, asset, isAmountInputFocused, isLockAmountInput, isLockInput ->
        if (asset == null) {
            defaultAmountInputState
        } else {
            val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
            val fiatAmount =
                amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

            AmountInputViewState(
                tokenName = asset.token.configuration.symbol,
                tokenImage = asset.token.configuration.iconUrl,
                totalBalance = resourceManager.getString(
                    R.string.common_transferable_format,
                    tokenBalance
                ),
                fiatAmount = fiatAmount,
                tokenAmount = amount,
                isActive = true,
                isFocused = isAmountInputFocused,
                allowAssetChoose = isLockInput.not(),
                precision = asset.token.configuration.precision,
                inputEnabled = isLockInput.not() || isLockAmountInput.not()
            )
        }
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeAmountFlow = combine(
        addressInputTrimmedFlow,
        isInputAddressValidFlow,
        enteredAmountBigDecimalFlow,
        assetFlow.mapNotNull { it }
    ) { address, isAddressValid, enteredAmount, asset ->

        val feeRequestAddress = when {
            isAddressValid -> address
            else -> currentAccountAddress(asset.token.configuration.chainId) ?: return@combine null
        }

        Transfer(
            recipient = feeRequestAddress,
            sender = requireNotNull(currentAccountAddress.invoke(asset.token.configuration.chainId)),
            amount = enteredAmount,
            chainAsset = asset.token.configuration
        )
    }.flatMapLatest {
        it?.let { transfer -> walletInteractor.observeTransferFee(transfer).map { it.feeAmount } }
            ?: flowOf(null)
    }
        .retry(RETRY_TIMES)
        .catch {
            println("Error: $it")
            it.printStackTrace()
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val feeInPlanksFlow = combine(feeAmountFlow, assetFlow) { fee, asset ->
        fee ?: return@combine null
        asset ?: return@combine null
        asset.token.planksFromAmount(fee)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val utilityAssetFlow = assetFlow.mapNotNull { it }.flatMapLatest { asset ->
        val chain = walletInteractor.getChain(asset.token.configuration.chainId)
        walletInteractor.assetFlow(chain.id, chain.utilityAsset?.id.orEmpty())
    }.share()

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = combine(
        feeAmountFlow,
        utilityAssetFlow,
        assetFlow
    ) { feeAmount, utilityAsset, asset ->
        asset ?: return@combine FeeInfoViewState.default
        feeAmount ?: return@combine FeeInfoViewState.default
        val isSendBokoloCash = asset.token.configuration.currencyId == bokoloCashTokenId
        val showFeeAsset = if (isSendBokoloCash && utilityAsset.transferable < feeAmount) {
            asset
        } else {
            utilityAsset
        }

        val assetFeeAmount = if (isSendBokoloCash && utilityAsset.transferable < feeAmount) {
            val swapDetails = polkaswapInteractor.calcDetails(
                availableDexPaths = listOf(0),
                tokenFrom = asset,
                tokenTo = utilityAsset,
                amount = feeAmount,
                desired = WithDesired.OUTPUT,
                slippageTolerance = 1.5,
                market = Market.SMART
            )
            swapDetails.getOrNull()?.amount
        } else {
            feeAmount
        }

        val feeFormatted = assetFeeAmount?.formatCryptoDetail(showFeeAsset.token.configuration.symbol)
        val feeFiat = assetFeeAmount?.applyFiatRate(showFeeAsset.token.fiatRate)?.formatFiat(showFeeAsset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val isWarningExpanded = MutableStateFlow(false)
    private val phishingModelFlow = addressInputTrimmedFlow.map {
        walletInteractor.getPhishingInfo(it)
    }
    private val warningInfoStateFlow = combine(
        phishingModelFlow,
        isWarningExpanded
    ) { phishing, isExpanded ->
        phishing?.let {
            WarningInfoState(
                message = getPhishingMessage(phishing.type),
                extras = listOf(
                    phishing.name?.let { resourceManager.getString(R.string.username_setup_choose_title) to it },
                    phishing.type?.let { resourceManager.getString(R.string.reason) to it.capitalizedName },
                    phishing.subtype?.let { resourceManager.getString(R.string.scam_additional_stub) to it }
                ).mapNotNull { it },
                isExpanded = isExpanded,
                color = phishing.color
            )
        }
    }

    private fun getPhishingMessage(type: PhishingType): String {
        return when (type) {
            PhishingType.SCAM -> resourceManager.getString(R.string.scam_warning_message, "DOT")
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_warning_message)
            PhishingType.DONATION -> resourceManager.getString(
                R.string.donation_warning_message_format,
                "DOT"
            )

            PhishingType.SANCTIONS -> resourceManager.getString(R.string.sanction_warning_message)
            else -> resourceManager.getString(R.string.scam_warning_message, "DOT")
        }
    }

    private val buttonStateFlow = combine(
        visibleAmountFlow,
        assetFlow
    ) { amount, asset ->
        val amountInPlanks = asset?.token?.planksFromAmount(amount).orZero()
        ButtonViewState(
            text = resourceManager.getString(R.string.common_continue),
            enabled = amountInPlanks.isNotZero()
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    private val sendAllToggleState: MutableStateFlow<ToggleState> = MutableStateFlow(ToggleState.INITIAL)
    private var existentialDepositCheckJob: Job? = null

    val state = combine(
        selectedChain,
        addressInputTrimmedFlow,
        chainSelectorStateFlow,
        amountInputViewState,
        feeInfoViewStateFlow,
        warningInfoStateFlow,
        buttonStateFlow,
        isSoftKeyboardOpenFlow,
        lockInputFlow,
        assetFlow,
        sendAllToggleState
    ) { chain, address, chainSelectorState, amountInputState, feeInfoState, warningInfoState, buttonState, isSoftKeyboardOpen, isInputLocked, asset, sendAllState ->
        val isAddressValid = when (chain) {
            null -> false
            else -> walletInteractor.validateSendAddress(chain.id, address)
        }

        confirmedValidations.clear()

        val quickAmountInputValues = if (asset?.token?.configuration?.currencyId == bokoloCashTokenId) {
            emptyList()
        } else {
            QuickAmountInput.values().toList()
        }

        val isHistorySupportedByChain = chain?.externalApi?.history != null

        val existentialDeposit = asset?.token?.configuration?.let { existentialDepositUseCase(it) }.orZero()
        val sendAllAllowed = existentialDeposit > BigInteger.ZERO

        SendSetupViewState(
            toolbarState = toolbarViewState,
            addressInputState = AddressInputState(
                title = resourceManager.getString(R.string.send_to),
                input = address,
                image = when {
                    isAddressValid.not() -> R.drawable.ic_address_placeholder
                    else -> addressIconGenerator.createAddressIcon(
                        chain?.isEthereumBased == true,
                        address,
                        AddressIconGenerator.SIZE_BIG
                    )
                },
                editable = false,
                showClear = isInputLocked.not()
            ),
            chainSelectorState = chainSelectorState,
            amountInputState = amountInputState,
            feeInfoState = feeInfoState,
            warningInfoState = warningInfoState,
            buttonState = buttonState,
            isSoftKeyboardOpen = isSoftKeyboardOpen,
            isInputLocked = isInputLocked,
            quickAmountInputValues = quickAmountInputValues,
            isHistoryAvailable = isHistorySupportedByChain,
            sendAllChecked = sendAllState in listOf(ToggleState.CHECKED, ToggleState.CONFIRMED),
            sendAllAllowed = sendAllAllowed
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    init {
        sharedState.clear()
        if (payload == null) {
            if (!initSendToAddress.isNullOrEmpty()) {
                findChainsForAddress(initSendToAddress)
            }
        } else {
            sharedState.update(payload.chainId, payload.chainAssetId)
        }
        initSendToAddress?.let { sharedState.updateAddress(it) }
        sharedState.addressFlow.onEach {
            it?.let { addressInputFlow.value = it }
        }.launchIn(this)
    }

    private fun observeExistentialDeposit(showMaxInput: Boolean) {
        existentialDepositCheckJob?.cancel()

        existentialDepositCheckJob = combine(
            assetFlow.mapNotNull { it },
            visibleAmountFlow,
            feeInPlanksFlow.mapNotNull { it },
            isInputAddressValidFlow,
            addressInputTrimmedFlow
        ) { asset, amount, fee, isAddressValid, address ->

            if (amount.isZero()) {
                sendAllToggleState.value = ToggleState.INITIAL
                return@combine null
            }
            val ownAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@combine null

            val recipientAddress = when {
                isAddressValid -> address
                else -> ownAddress
            }

            validateTransferUseCase.validateExistentialDeposit(
                amountInPlanks = asset.token.planksFromAmount(amount.orZero()),
                asset = asset,
                destinationChainId = asset.token.configuration.chainId,
                recipientAddress = recipientAddress,
                ownAddress = ownAddress,
                fee = fee,
            )
        }
            .onEach { it ->
                val sendAllState = sendAllToggleState.value
                it?.fold(
                    onSuccess = { validationResult ->
                        if (validationResult == TransferValidationResult.Valid && sendAllState == ToggleState.CONFIRMED) {
                            sendAllToggleState.value = ToggleState.INITIAL
                        }

                        if (validationResult.isExistentialDepositWarning && sendAllState != ToggleState.CONFIRMED) {
                            ValidationException.fromValidationResult(validationResult, resourceManager)?.let {
                                if (it is ExistentialDepositCrossedException) {
                                    val warning = ValidationWarning(
                                        it.message,
                                        it.explanation,
                                        it.positiveButtonText,
                                        it.negativeButtonText,
                                        if (showMaxInput) it.secondPositiveButtonText else null
                                    )
                                    _openValidationWarningEvent.value = Event(validationResult to warning)
                                }
                            }
                        }
                    },
                    onFailure = {
                        it.printStackTrace()
                    }
                )
            }
            .onEach { existentialDepositCheckJob?.cancel() }
            .launchIn(viewModelScope)
    }

    private fun findChainsForAddress(address: String) {
        launch {
            val chains = walletInteractor.getChains().first()
            val addressChains = chains.filter {
                it.isValidAddress(address)
            }
            when {
                addressChains.size == 1 -> {
                    val chain = addressChains[0]
                    when {
                        chain.assets.size == 1 -> sharedState.update(chain.id, chain.assets[0].id)
                        else -> router.openSelectChainAsset(chain.id)
                    }
                }

                else -> router.openSelectChain(
                    filterChainIds = addressChains.map { it.id },
                    chooserMode = false,
                    currencyId = tokenCurrencyId,
                    showAllChains = false
                )
            }
        }
    }

    override fun onAmountInput(input: BigDecimal?) {
        visibleAmountFlow.value = input.orZero()
        enteredAmountBigDecimalFlow.value = input.orZero()

        if (sendAllToggleState.value == ToggleState.CONFIRMED || sendAllToggleState.value == ToggleState.INITIAL && input?.greaterThen(BigDecimal.ZERO) == true) {
            observeExistentialDeposit(true)
        }
    }

    override fun onAddressInput(input: String) {
        addressInputFlow.value = input
    }

    override fun onAddressInputClear() {
        addressInputFlow.value = ""
        sharedState.clearAddress()
    }

    override fun onNextClick() {
        viewModelScope.launch {
            val asset = assetFlow.value ?: return@launch

            val amount = enteredAmountBigDecimalFlow.value
            val inPlanks = asset.token.planksFromAmount(amount).orZero()
            val recipientAddress = addressInputTrimmedFlow.firstOrNull() ?: return@launch
            val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@launch
            val fee = feeInPlanksFlow.value
            val destinationChainId = asset.token.configuration.chainId
            val validationProcessResult = validateTransferUseCase(
                amountInPlanks = inPlanks,
                asset = asset,
                destinationChainId = destinationChainId,
                recipientAddress = recipientAddress,
                ownAddress = selfAddress,
                fee = fee,
                confirmedValidations = confirmedValidations,
                transferMyselfAvailable = false,
                skipEdValidation = sendAllToggleState.value == ToggleState.CONFIRMED
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
            // all checks have passed - go to next step

            onNextStep()
        }
    }

    private fun onNextStep() {
        launch {
            val transferDraft = buildTransferDraft() ?: return@launch
            val phishingType = phishingModelFlow.firstOrNull()?.type
            val skipEdValidation = sendAllToggleState.value == ToggleState.CONFIRMED

            router.openSendConfirm(
                transferDraft = transferDraft,
                phishingType = phishingType,
                skipEdValidation = skipEdValidation
            )
        }
    }

    private val tipFlow = chainIdFlow.map { it?.let { walletConstants.tip(it) } }.share()
    private val tipAmountFlow = combine(tipFlow, assetFlow) { tip: BigInteger?, asset: Asset? ->
        tip?.let {
            asset?.token?.amountFromPlanks(it)
        }
    }

    private suspend fun buildTransferDraft(): TransferDraft? {
        val recipientAddress = addressInputTrimmedFlow.firstOrNull() ?: return null
        val feeAmount = feeAmountFlow.firstOrNull() ?: return null
        val tip = tipAmountFlow.firstOrNull()

        val amount = enteredAmountBigDecimalFlow.value

        val chainId = sharedState.chainId ?: return null
        val assetId = sharedState.assetId ?: return null

        val payload = AssetPayload(chainId, assetId)

        return TransferDraft(amount, feeAmount, payload, recipientAddress, tip)
    }

    override fun onChainClick() {
        sharedState.assetId?.let { assetId ->
            router.openSelectChain(
                assetId = assetId,
                chainId = sharedState.chainId,
                chooserMode = false
            )
        }
    }

    override fun onTokenClick() {
        sharedState.assetId?.let { assetId ->
            router.openSelectAsset(assetId)
        }
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onQrClick() {
        _openScannerEvent.value = Event(Unit)
    }

    override fun onHistoryClick() {
        sharedState.chainId?.let {
            router.openAddressHistory(it)
        }
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            addressInputFlow.value = buffer
        }
    }

    override fun onAmountFocusChanged(isFocused: Boolean) {
        amountInputFocusFlow.value = isFocused
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val cbdcQrContent = walletInteractor.tryReadCBDCAddressFormat(content)
            if (cbdcQrContent != null) {
                router.back()
                router.openCBDCSend(cbdcQrInfo = cbdcQrContent)
            } else {
                val soraQrContent = walletInteractor.tryReadSoraFormat(content)
                if (soraQrContent != null) {
                    handleSoraQr(soraQrContent)
                } else {

                    // 3 fill QR content

                    addressInputFlow.value = content
                    lockAmountInputFlow.value = false
                    lockInputFlow.value = false
                }
            }
        }
    }

    private suspend fun handleSoraQr(qrContentSora: QrContentSora) {
        val soraTokenId = qrContentSora.tokenId
        val soraAmount = qrContentSora.amount

        val soraChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        val soraChain = walletInteractor.getChain(soraChainId)
        soraChain.assets.firstOrNull { it.currencyId == soraTokenId }?.let { asset ->
            sharedState.update(soraChainId, asset.id)
        }

        addressInputFlow.value = qrContentSora.address
        lockInputFlow.value = true

        val amount = runCatching { BigDecimal(soraAmount) }.getOrNull().orZero()
        if (amount.greaterThen(BigDecimal.ZERO)) {
            initialAmountFlow.value = null
            lockAmountInputFlow.value = true

            delay(300) // need for 'initialAmountFlow.value = null' being applied to UI before next
            initialAmountFlow.value = amount
            onAmountInput(amount)
        }
    }

    fun setSoftKeyboardOpen(isOpen: Boolean) {
        isSoftKeyboardOpenFlow.value = isOpen
    }

    override fun onQuickAmountInput(input: Double) {
        launch {
            val utilityAsset = utilityAssetFlow.firstOrNull() ?: return@launch
            val asset = assetFlow.firstOrNull() ?: return@launch
            val tip = tipFlow.firstOrNull()
            val tipAmount = utilityAsset.token.amountFromPlanks(tip.orZero())

            val utilityTipReserve = when {
                asset.token.configuration.isUtility -> tipAmount
                else -> BigDecimal.ZERO
            }

            val allAmount = asset.transferable
            val amountToTransfer = (allAmount * input.toBigDecimal()) - utilityTipReserve

            val selfAddress =
                sharedState.chainId?.let { currentAccountAddress(it) } ?: return@launch
            val transfer = Transfer(
                recipient = selfAddress,
                sender = selfAddress,
                amount = amountToTransfer,
                chainAsset = asset.token.configuration
            )

            val utilityFeeReserve = when {
                asset.token.configuration.isUtility.not() -> BigDecimal.ZERO
                else -> walletInteractor.getTransferFee(transfer).feeAmount
            }

            val quickAmountWithoutExtraPays =
                amountToTransfer - utilityFeeReserve * SLIPPAGE_TOLERANCE.toBigDecimal()

            if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
                if (sendAllToggleState.value == ToggleState.CHECKED) {
                    sendAllToggleState.value = ToggleState.CONFIRMED
                }
                return@launch
            }
            val scaledAmount = quickAmountWithoutExtraPays.setScale(5, RoundingMode.HALF_DOWN)
            if (initialAmountFlow.value == scaledAmount) {
                initialAmountFlow.value = null
                delay(70)
            }
            visibleAmountFlow.value = scaledAmount
            initialAmountFlow.value = scaledAmount
            enteredAmountBigDecimalFlow.value = quickAmountWithoutExtraPays
            observeExistentialDeposit(input < QuickAmountInput.MAX.value)
        }
    }

    override fun onWarningInfoClick() {
        isWarningExpanded.value = !isWarningExpanded.value
    }

    fun warningCancelled(validationResult: TransferValidationResult) {
        if (validationResult.isExistentialDepositWarning) {
            onAmountInput(BigDecimal.ZERO)
            sendAllToggleState.value = ToggleState.INITIAL
        }
    }

    fun warningConfirmed(validationResult: TransferValidationResult) {
        confirmedValidations.add(validationResult)
        if (validationResult.isExistentialDepositWarning) {
            sendAllToggleState.value = ToggleState.CONFIRMED
        }
    }

    fun warningConfirmedSecond(validationResult: TransferValidationResult) {
        warningConfirmed(validationResult)
        onQuickAmountInput(1.0)
    }

    override fun onSendAllChecked(checked: Boolean) {
        val currentState = sendAllToggleState.value
        if (checked) {
            sendAllToggleState.value = ToggleState.CHECKED
        } else if (currentState == ToggleState.CONFIRMED) {
            sendAllToggleState.value = ToggleState.INITIAL
        }

        if (checked) {
            onQuickAmountInput(1.0)
        }
    }
}
