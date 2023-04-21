package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
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
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

private const val RETRY_TIMES = 3L
private const val SLIPPAGE_TOLERANCE = 1.35

@HiltViewModel
class CrossChainSetupViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val walletConstants: WalletConstants,
    private val router: WalletRouter,
    private val clipboardManager: ClipboardManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase,
    private val chainAssetsManager: ChainAssetsManager
) : BaseViewModel(), CrossChainSetupScreenInterface {

    private val _openScannerEvent = MutableSharedFlow<Unit>()
    val openScannerEvent = _openScannerEvent.asSharedFlow()

    private val _openValidationWarningEvent =
        MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> = _openValidationWarningEvent

    private val payload: AssetPayload? = savedStateHandle[CrossChainSetupFragment.KEY_PAYLOAD]

    private val addressFlow: MutableStateFlow<String?> = MutableStateFlow(null)

    private val initialAmount = BigDecimal.ZERO
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val assetIdFlow: StateFlow<String?> = chainAssetsManager.assetIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val assetFlow: StateFlow<Asset?> = chainAssetsManager.assetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val originalChainIdFlow: StateFlow<ChainId?> = chainAssetsManager.originalChainIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val assetId: String? get() = assetIdFlow.value
    private val originalChainId: String? get() = originalChainIdFlow.value

    private val defaultAddressInputState = AddressInputState(
        title = resourceManager.getString(R.string.send_fund),
        "",
        R.drawable.ic_address_placeholder
    )

    private val defaultAmountInputState = AmountInputViewState(
        tokenName = "...",
        tokenImage = "",
        totalBalance = resourceManager.getString(R.string.common_available_format, "..."),
        fiatAmount = "",
        tokenAmount = initialAmount,
        allowAssetChoose = false,
        initial = initialAmount
    )

    private val defaultButtonState = ButtonViewState(
        resourceManager.getString(R.string.common_continue),
        true
    )

    private val toolbarViewState = ToolbarViewState(
        resourceManager.getString(R.string.send_fund),
        R.drawable.ic_arrow_left_24
    )

    private val defaultState = CrossChainSetupViewState(
        toolbarViewState,
        defaultAddressInputState,
        defaultAmountInputState,
        SelectorState.default,
        SelectorState.default,
        FeeInfoViewState.default,
        destinationFeeInfoState = null,
        warningInfoState = null,
        defaultButtonState
    )

    private val amountInputFocusFlow = MutableStateFlow(false)
    private val addressInputFlow = MutableStateFlow("")
    private val isInputAddressValidFlow = combine(
        addressInputFlow,
        chainAssetsManager.originalChainIdFlow
    ) { addressInput, chainId ->
        when (chainId) {
            null -> false
            else -> walletInteractor.validateSendAddress(chainId, addressInput)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val enteredAmountBigDecimalFlow = MutableStateFlow(initialAmount)
    private val visibleAmountFlow = MutableStateFlow(initialAmount)
    private val initialAmountFlow = MutableStateFlow(initialAmount)

    private val amountInputViewState: Flow<AmountInputViewState> = combine(
        visibleAmountFlow,
        initialAmountFlow,
        assetFlow,
        amountInputFocusFlow
    ) { amount, initialAmount, asset, isAmountInputFocused ->
        if (asset == null) {
            defaultAmountInputState
        } else {
            val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration)
            val fiatAmount =
                amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

            AmountInputViewState(
                tokenName = asset.token.configuration.symbolToShow,
                tokenImage = asset.token.configuration.iconUrl,
                totalBalance = resourceManager.getString(
                    R.string.common_available_format,
                    tokenBalance
                ),
                fiatAmount = fiatAmount,
                tokenAmount = amount,
                isActive = true,
                isFocused = isAmountInputFocused,
                allowAssetChoose = true,
                precision = asset.token.configuration.precision,
                initial = initialAmount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val originalFeeAmountFlow = combine(
        addressInputFlow,
        isInputAddressValidFlow,
        enteredAmountBigDecimalFlow,
        assetFlow.mapNotNull { it }
    ) { address, isAddressValid, enteredAmount, asset ->

        val feeRequestAddress = when {
            isAddressValid -> address
            else -> currentAccountAddress(asset.token.configuration.chainId) ?: return@combine null
        }

        val transfer = Transfer(
            recipient = feeRequestAddress,
            amount = enteredAmount,
            chainAsset = asset.token.configuration
        )
        val fee = walletInteractor.getTransferFee(transfer)
        fee.feeAmount
    }
        .retry(RETRY_TIMES)
        .catch {
            println("Error: $it")
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val destinationFeeAmountFlow = flowOf(BigDecimal.valueOf(0))

    private val feeInPlanksFlow = combine(originalFeeAmountFlow, assetFlow) { fee, asset ->
        fee ?: return@combine null
        asset ?: return@combine null
        asset.token.planksFromAmount(fee)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val utilityAssetFlow = assetFlow.filterNotNull()
        .flatMapLatest { asset ->
            val chain = walletInteractor.getChain(asset.token.configuration.chainId)
            walletInteractor.assetFlow(chain.id, chain.utilityAsset.id)
        }

    private val originalFeeInfoViewStateFlow: Flow<FeeInfoViewState> = combine(
        originalFeeAmountFlow,
        utilityAssetFlow
    ) { feeAmount, utilityAsset ->
        val feeFormatted = feeAmount?.formatTokenAmount(utilityAsset.token.configuration)
        val feeFiat = feeAmount?.applyFiatRate(utilityAsset.token.fiatRate)
            ?.formatAsCurrency(utilityAsset.token.fiatSymbol)

        FeeInfoViewState(
            caption = resourceManager.getString(R.string.common_origin_network_fee),
            feeAmount = feeFormatted,
            feeAmountFiat = feeFiat
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val destinationFeeInfoViewStateFlow: Flow<FeeInfoViewState?> = combine(
        destinationFeeAmountFlow,
        assetFlow
    ) { feeAmount, asset ->
        if (asset == null) return@combine null

        val feeFormatted = feeAmount?.formatTokenAmount(asset.token.configuration)
        val feeFiat = feeAmount?.applyFiatRate(asset.token.fiatRate)
            ?.formatAsCurrency(asset.token.fiatSymbol)

        FeeInfoViewState(
            caption = resourceManager.getString(R.string.common_destination_network_fee),
            feeAmount = feeFormatted,
            feeAmountFiat = feeFiat
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val isWarningExpanded = MutableStateFlow(false)
    private val phishingModelFlow = addressInputFlow.map {
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
                    phishing.type.let { resourceManager.getString(R.string.reason) to it.capitalizedName },
                    phishing.subtype?.let { resourceManager.getString(R.string.additional) to it }
                ).mapNotNull { it },
                isExpanded = isExpanded,
                color = phishing.color
            )
        }
    }

    private val buttonStateFlow = combine(
        visibleAmountFlow,
        assetFlow,
        chainAssetsManager.originalChainIdFlow,
        chainAssetsManager.destinationChainIdFlow
    ) { amount, asset, originalChainId, destinationChainId ->
        val amountInPlanks = asset?.token?.planksFromAmount(amount).orZero()
        val isAllChainsSelected = originalChainId != null && destinationChainId != null
        ButtonViewState(
            text = resourceManager.getString(R.string.common_continue),
            enabled = amountInPlanks.compareTo(BigInteger.ZERO) != 0 && isAllChainsSelected
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val state = combine(
        chainAssetsManager.originalSelectedChain,
        addressInputFlow,
        chainAssetsManager.originalChainSelectorStateFlow,
        chainAssetsManager.destinationChainSelectorStateFlow,
        amountInputViewState,
        originalFeeInfoViewStateFlow,
        destinationFeeInfoViewStateFlow,
        warningInfoStateFlow,
        buttonStateFlow
    ) { chain, address, originalChainSelectorState,
        destinationChainSelectorState, amountInputState,
        originalFeeInfoState, destinationFeeInfoState,
        warningInfoState, buttonState ->
        val isAddressValid = when (chain) {
            null -> false
            else -> walletInteractor.validateSendAddress(chain.id, address)
        }

        confirmedValidations.clear()

        CrossChainSetupViewState(
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
                }
            ),
            originalChainSelectorState = originalChainSelectorState,
            destinationChainSelectorState = destinationChainSelectorState,
            amountInputState = amountInputState,
            originalFeeInfoState = originalFeeInfoState,
            destinationFeeInfoState = destinationFeeInfoState,
            warningInfoState = warningInfoState,
            buttonState = buttonState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    init {
        setInitialChainsAndAssetIds()
        observeAddressFlow()
    }

    private fun setInitialChainsAndAssetIds() {
        if (payload == null) return
        viewModelScope.launch {
            chainAssetsManager.setInitialChainsAndAssetIds(
                chainId = payload.chainId,
                assetId = payload.chainAssetId
            )
        }
    }

    private fun observeAddressFlow() {
        addressFlow
            .onEach { addressInputFlow.value = it.orEmpty() }
            .launchIn(viewModelScope)
    }

    private fun getPhishingMessage(type: PhishingType): String {
        return when (type) {
            PhishingType.SCAM -> resourceManager.getString(R.string.scam_warning_message)
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_warning_message)
            PhishingType.DONATION -> resourceManager.getString(R.string.donation_warning_message)
            PhishingType.SANCTIONS -> resourceManager.getString(R.string.sanction_warning_message)
            else -> resourceManager.getString(R.string.scam_warning_message)
        }
    }

    override fun onAmountInput(input: BigDecimal?) {
        visibleAmountFlow.value = input.orZero()
        enteredAmountBigDecimalFlow.value = input.orZero()
    }

    override fun onAddressInput(input: String) {
        addressInputFlow.value = input
    }

    override fun onAddressInputClear() {
        addressFlow.value = null
    }

    override fun onNextClick() {
        viewModelScope.launch {
            val asset = assetFlow.value ?: return@launch

            val amount = enteredAmountBigDecimalFlow.value
            val inPlanks = asset.token.planksFromAmount(amount).orZero()
            val recipientAddress = addressInputFlow.value
            val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@launch
            val fee = feeInPlanksFlow.value
            val destinationChainId = chainAssetsManager.destinationChainId ?: return@launch
            val validationProcessResult = validateTransferUseCase(inPlanks, asset, destinationChainId, recipientAddress, selfAddress, fee, confirmedValidations)

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
        viewModelScope.launch {
            val transferDraft = buildTransferDraft() ?: return@launch
            val phishingType = phishingModelFlow.firstOrNull()?.type

            router.openCrossChainSendConfirm(transferDraft, phishingType)
            return@launch
        }
    }

    private val tipFlow = chainAssetsManager.originalChainIdFlow.map { it?.let { walletConstants.tip(it) } }
    private val tipAmountFlow = combine(tipFlow, assetFlow) { tip: BigInteger?, asset: Asset? ->
        tip?.let {
            asset?.token?.amountFromPlanks(it)
        }
    }

    private suspend fun buildTransferDraft(): CrossChainTransferDraft? {
        val recipientAddress = addressInputFlow.firstOrNull() ?: return null
        val originalFeeAmount = originalFeeAmountFlow.firstOrNull() ?: return null
        val destinationFeeAmount = destinationFeeAmountFlow.firstOrNull() ?: return null

        val originalChainId = originalChainId
        val destinationChainId = chainAssetsManager.destinationChainId
        val assetId = assetId
        if (
            originalChainId == null ||
            destinationChainId == null ||
            assetId == null
        ) {
            return null
        }

        val amount = enteredAmountBigDecimalFlow.value
        val tip = tipAmountFlow.firstOrNull()

        return CrossChainTransferDraft(
            amount,
            originalChainId,
            destinationChainId,
            originalFeeAmount,
            destinationFeeAmount,
            assetId,
            recipientAddress,
            tip
        )
    }

    override fun onDestinationChainClick() {
        chainAssetsManager.observeChainIdAndAssetIdResult(
            scope = viewModelScope,
            chainType = ChainType.Destination,
            onError = { showError(it) }
        )

        router.openSelectChainForXcm(
            selectedChainId = chainAssetsManager.destinationChainId,
            xcmChainType = XcmChainType.Destination,
            selectedOriginalChainId = originalChainId
        )
    }

    override fun onAssetClick() {
        val assetId = assetId ?: return
        val originalChainId = originalChainId ?: return

        chainAssetsManager.observeChainIdAndAssetIdResult(
            scope = viewModelScope,
            chainType = ChainType.Original,
            onError = { showError(it) }
        )
        router.openSelectAsset(
            chainId = originalChainId,
            selectedAssetId = assetId,
            isFilterXcmAssets = true
        )
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onQrClick() {
        viewModelScope.launch {
            _openScannerEvent.emit(Unit)
        }
    }

    override fun onHistoryClick() {
        originalChainId?.let {
            router.openAddressHistoryWithResult(it)
                .onEach { address ->
                    addressInputFlow.value = address
                }
                .launchIn(viewModelScope)
        }
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            addressInputFlow.value = buffer
        }
    }

    override fun onAmountFocusChanged(focusState: FocusState) {
        amountInputFocusFlow.value = focusState.isFocused
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            println("qrCodeScanned: $content")
            val result = walletInteractor.tryReadAddressFromSoraFormat(content) ?: content

            addressInputFlow.value = result
        }
    }

    override fun onQuickAmountInput(input: Double) {
        viewModelScope.launch {
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

            val selfAddress = originalChainId?.let { currentAccountAddress(it) } ?: return@launch
            val transfer = Transfer(
                recipient = selfAddress,
                amount = amountToTransfer,
                chainAsset = asset.token.configuration
            )

            val utilityFeeReserve = when {
                asset.token.configuration.isUtility.not() -> BigDecimal.ZERO
                else -> walletInteractor.getTransferFee(transfer).feeAmount
            }

            val quickAmountWithoutExtraPays = amountToTransfer - utilityFeeReserve * SLIPPAGE_TOLERANCE.toBigDecimal()

            if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
                return@launch
            }
            val scaled = quickAmountWithoutExtraPays.setScale(
                5,
                RoundingMode.HALF_DOWN
            )
            visibleAmountFlow.value = scaled
            initialAmountFlow.value = scaled
            enteredAmountBigDecimalFlow.value = quickAmountWithoutExtraPays
        }
    }

    override fun onWarningInfoClick() {
        isWarningExpanded.value = !isWarningExpanded.value
    }

    fun warningConfirmed(validationResult: TransferValidationResult) {
        confirmedValidations.add(validationResult)
        onNextClick()
    }
}
