package jp.co.soramitsu.wallet.impl.presentation.cross_chain.setup

import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressModel
import jp.co.soramitsu.common.address.createAddressIcon
import jp.co.soramitsu.common.address.createAddressModel
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
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.models.ChainId
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.api.domain.model.XcmChainType
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.XcmInteractor
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.WalletAccount
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.walletselector.light.WalletSelectionMode
import jp.co.soramitsu.wallet.impl.presentation.cross_chain.CrossChainTransferDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
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
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val SLIPPAGE_TOLERANCE = 1.35
private const val CURRENT_ICON_SIZE = 16

@HiltViewModel
class CrossChainSetupViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val walletConstants: WalletConstants,
    private val router: WalletRouter,
    private val clipboardManager: ClipboardManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase,
    private val chainAssetsManager: ChainAssetsManager,
    private val xcmInteractor: XcmInteractor
) : BaseViewModel(), CrossChainSetupScreenInterface {

    private val _openScannerEvent = MutableSharedFlow<Unit>()
    val openScannerEvent = _openScannerEvent.asSharedFlow()

    private val _openValidationWarningEvent =
        MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> = _openValidationWarningEvent

    private val payload: AssetPayload? = savedStateHandle[CrossChainSetupFragment.KEY_PAYLOAD]
    private val selectedWalletIdFlow = MutableStateFlow<Long?>(null)

    private val initialAmount = BigDecimal.ZERO
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val assetIdFlow: StateFlow<String?> = chainAssetsManager.assetIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val assetFlow: StateFlow<Asset?> = chainAssetsManager.assetFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)
    private val originChainIdFlow: StateFlow<ChainId?> = chainAssetsManager.originChainIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    @OptIn(FlowPreview::class)
    private val walletIconFlow = originChainIdFlow.flatMapConcat { chainId ->
        if (chainId == null) return@flatMapConcat flowOf(null)

        walletInteractor.selectedAccountFlow(chainId)
            .map { generateAddressModel(it, CURRENT_ICON_SIZE) }
    }
        .map { it?.image }
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val assetId: String? get() = assetIdFlow.value
    private val originChainId: String? get() = originChainIdFlow.value
    private val destinationChainId: String? get() = chainAssetsManager.destinationChainIdFlow.value

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
        allowAssetChoose = false,
        initial = initialAmount.takeIf { it.isNotZero() }
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
        defaultButtonState,
        walletIcon = null
    )

    private val amountInputFocusFlow = MutableStateFlow(false)
    private val addressInputFlow = MutableStateFlow("")
    private val isInputAddressValidFlow = combine(
        addressInputFlow,
        chainAssetsManager.originChainIdFlow
    ) { addressInput, chainId ->
        when (chainId) {
            null -> false
            else -> walletInteractor.validateSendAddress(chainId, addressInput)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val enteredAmountBigDecimalFlow = MutableStateFlow(initialAmount)
    private val visibleAmountFlow = MutableStateFlow(initialAmount)
    private val initialAmountFlow = MutableStateFlow(initialAmount.takeIf { it.isNotZero() })

    private val amountInputViewState: Flow<AmountInputViewState> = combine(
        visibleAmountFlow,
        initialAmountFlow,
        assetFlow,
        amountInputFocusFlow
    ) { amount, initialAmount, asset, isAmountInputFocused ->
        if (asset == null) {
            defaultAmountInputState
        } else {
            val tokenBalance = asset.transferable.formatCrypto(asset.token.configuration.symbol)
            val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

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
                allowAssetChoose = true,
                precision = asset.token.configuration.precision,
                initial = initialAmount
            )
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultAmountInputState)

    private val hasDestinationFeeAmountFlow = MutableStateFlow(false)
    private val destinationFeeAmountFlow: StateFlow<BigDecimal?> = combine(
        chainAssetsManager.destinationChainIdFlow,
        assetFlow
    ) { _destinationChainId, _asset ->
        hasDestinationFeeAmountFlow.value = false
        val destinationChainId = _destinationChainId ?: return@combine null
        val tokenConfiguration = _asset?.token?.configuration ?: return@combine null
        val fee = xcmInteractor.getDestinationFee(
            destinationChainId = destinationChainId,
            tokenConfiguration = tokenConfiguration
        )
        fee
    }
        .catch {
            println("Error: $it")
            emit(null)
        }
        .onEach { fee -> hasDestinationFeeAmountFlow.value = fee != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val hasOriginFeeAmountFlow = MutableStateFlow(false)
    private val originFeeAmountFlow = combine(
        chainAssetsManager.originChainIdFlow,
        chainAssetsManager.destinationChainIdFlow,
        destinationFeeAmountFlow,
        enteredAmountBigDecimalFlow,
        assetFlow.mapNotNull { it }
    ) { nullableOriginChainId, nullableDestinationChainId, nullableDestinationFeeAmount, amount, asset ->
        hasOriginFeeAmountFlow.value = false
        val originChainId = nullableOriginChainId ?: return@combine null
        val destinationChainId = nullableDestinationChainId ?: return@combine null
        val destinationAmount = nullableDestinationFeeAmount ?: BigDecimal.ZERO

        xcmInteractor.getOriginFee(
            originNetworkId = originChainId,
            destinationNetworkId = destinationChainId,
            asset = asset.token.configuration,
            amount = amount + destinationAmount
        )
    }
        .catch {
            println("Error: $it")
            emit(null)
        }
        .onEach { fee -> hasOriginFeeAmountFlow.value = fee != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val utilityAssetFlow = assetFlow.filterNotNull()
        .flatMapLatest { asset ->
            val chain = walletInteractor.getChain(asset.token.configuration.chainId)
            val utilityAsset = chain.utilityAsset
            if (utilityAsset == null) {
                flowOf(null)
            } else {
                walletInteractor.assetFlow(chain.id, utilityAsset.id).mapNotNull {
                    it
                }
            }
        }

    private val originFeeInPlanksFlow = combine(originFeeAmountFlow, utilityAssetFlow) { fee, asset ->
        fee ?: return@combine null
        asset ?: return@combine null
        asset.token.planksFromAmount(fee)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val originFeeInfoViewStateFlow: Flow<FeeInfoViewState> = combine(
        hasOriginFeeAmountFlow,
        originFeeAmountFlow,
        utilityAssetFlow
    ) { hasOriginFeeAmount, feeAmount, utilityAsset ->
        val feeFormatted = feeAmount?.formatCryptoDetail(utilityAsset?.token?.configuration?.symbol)
            ?.takeIf { hasOriginFeeAmount }
        val feeFiat = feeAmount?.applyFiatRate(utilityAsset?.token?.fiatRate)
            ?.formatFiat(utilityAsset?.token?.fiatSymbol)
            ?.takeIf { hasOriginFeeAmount }

        FeeInfoViewState(
            caption = resourceManager.getString(R.string.common_origin_network_fee),
            feeAmount = feeFormatted,
            feeAmountFiat = feeFiat
        )
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val destinationFeeInfoViewStateFlow: Flow<FeeInfoViewState?> = combine(
        hasDestinationFeeAmountFlow,
        destinationFeeAmountFlow,
        assetFlow
    ) { hasDestinationFeeAmount, feeAmount, asset ->
        if (asset == null) return@combine null

        val feeFormatted = feeAmount?.formatCryptoDetail(asset.token.configuration.symbol)
            ?.takeIf { hasDestinationFeeAmount }
        val feeFiat = feeAmount?.applyFiatRate(asset.token.fiatRate)
            ?.formatFiat(asset.token.fiatSymbol)
            ?.takeIf { hasDestinationFeeAmount }

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
                    phishing.subtype?.let { resourceManager.getString(R.string.scam_additional_stub) to it }
                ).mapNotNull { it },
                isExpanded = isExpanded,
                color = phishing.color
            )
        }
    }

    private val buttonStateFlow = combine(
        visibleAmountFlow,
        assetFlow,
        chainAssetsManager.originChainIdFlow,
        chainAssetsManager.destinationChainIdFlow,
        hasOriginFeeAmountFlow,
        hasDestinationFeeAmountFlow,
        addressInputFlow
    ) { amount, asset, originChainId, destinationChainId, hasOriginFeeAmount, hasDestinationFeeAmount, addressInput ->
        val amountInPlanks = asset?.token?.planksFromAmount(amount).orZero()
        val isAllChainsSelected = originChainId != null && destinationChainId != null
        val isAllFeesCalculated = hasOriginFeeAmount && hasDestinationFeeAmount
        val isAddressExists = addressInput.isNotBlank()
        ButtonViewState(
            text = resourceManager.getString(R.string.common_continue),
            enabled = amountInPlanks.isNotZero() && isAllChainsSelected && isAllFeesCalculated && isAddressExists
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val state = combine(
        chainAssetsManager.originSelectedChain,
        chainAssetsManager.destinationSelectedChainFlow,
        addressInputFlow,
        chainAssetsManager.originChainSelectorStateFlow,
        chainAssetsManager.destinationChainSelectorStateFlow,
        amountInputViewState,
        originFeeInfoViewStateFlow,
        destinationFeeInfoViewStateFlow,
        warningInfoStateFlow,
        buttonStateFlow,
        walletIconFlow
    ) { originSelectedChain, destinationSelectedChain, address, originChainSelectorState,
        destinationChainSelectorState, amountInputState,
        originFeeInfoState, destinationFeeInfoState,
        warningInfoState, buttonState, walletIcon ->
        val isAddressValid = if (destinationSelectedChain == null) {
            false
        } else {
            walletInteractor.validateSendAddress(destinationSelectedChain.id, address)
        }

        confirmedValidations.clear()

        CrossChainSetupViewState(
            toolbarState = toolbarViewState,
            addressInputState = AddressInputState(
                title = resourceManager.getString(R.string.send_to),
                input = address,
                image = if (isAddressValid) {
                    addressIconGenerator.createAddressIcon(
                        originSelectedChain?.isEthereumBased == true,
                        address,
                        AddressIconGenerator.SIZE_BIG
                    )
                } else {
                    R.drawable.ic_address_placeholder
                },
                editable = false
            ),
            originChainSelectorState = originChainSelectorState,
            destinationChainSelectorState = destinationChainSelectorState,
            amountInputState = amountInputState,
            originFeeInfoState = originFeeInfoState,
            destinationFeeInfoState = destinationFeeInfoState,
            warningInfoState = warningInfoState,
            buttonState = buttonState,
            walletIcon = walletIcon
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    init {
        setInitialChainsAndAssetIds()
        observeDestinationChainFlow()

        chainAssetsManager.destinationChainIdFlow.filterNotNull()
            .onEach {
                xcmInteractor.prepareDataForChains(payload!!.chainId, it)
            }
            .launchIn(viewModelScope)
    }

    private fun setInitialChainsAndAssetIds() {
        if (payload == null) return
        viewModelScope.launch {
            chainAssetsManager.setInitialIds(
                chainId = payload.chainId,
                assetId = payload.chainAssetId
            )
        }
    }

    private fun observeDestinationChainFlow() {
        chainAssetsManager.destinationSelectedChainFlow
            .onEach {
                val selectedWalletId = selectedWalletIdFlow.value
                if (selectedWalletId != null) {
                    setAddressByMetaAccountId(selectedWalletId)
                } else {
                    addressInputFlow.value = ""
                }
            }
            .launchIn(viewModelScope)
    }

    private fun getPhishingMessage(type: PhishingType): String {
        return when (type) {
            PhishingType.SCAM -> resourceManager.getString(R.string.scam_warning_message, "DOT")
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_warning_message)
            PhishingType.DONATION -> resourceManager.getString(R.string.donation_warning_message_format, "DOT")
            PhishingType.SANCTIONS -> resourceManager.getString(R.string.sanction_warning_message)
            else -> resourceManager.getString(R.string.scam_warning_message, "DOT")
        }
    }

    private suspend fun generateAddressModel(account: WalletAccount, sizeInDp: Int): AddressModel {
        return addressIconGenerator.createAddressModel(account.address, sizeInDp, account.name)
    }

    override fun onAmountInput(input: BigDecimal?) {
        visibleAmountFlow.value = input.orZero()
        enteredAmountBigDecimalFlow.value = input.orZero()
    }

    override fun onAddressInput(input: String) {
        selectedWalletIdFlow.value = null
        addressInputFlow.value = input
    }

    override fun onAddressInputClear() {
        selectedWalletIdFlow.value = null
        addressInputFlow.value = ""
    }

    override fun onNextClick() {
        viewModelScope.launch {
            val asset = assetFlow.value ?: return@launch

            val amount = enteredAmountBigDecimalFlow.value
            val inPlanks = asset.token.planksFromAmount(amount).orZero()
            val recipientAddress = addressInputFlow.value
            val selfAddress = currentAccountAddress(asset.token.configuration.chainId) ?: return@launch
            val fee = originFeeInPlanksFlow.value
            val destinationChainId = chainAssetsManager.destinationChainId ?: return@launch
            val validationProcessResult = validateTransferUseCase(
                amountInPlanks = inPlanks,
                asset = asset,
                destinationChainId = destinationChainId,
                recipientAddress = recipientAddress,
                ownAddress = selfAddress,
                fee = fee,
                confirmedValidations = confirmedValidations,
                transferMyselfAvailable = true
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
        viewModelScope.launch {
            val transferDraft = buildTransferDraft() ?: return@launch
            val phishingType = phishingModelFlow.firstOrNull()?.type

            router.openCrossChainSendConfirm(transferDraft, phishingType)
            return@launch
        }
    }

    private val tipFlow = chainAssetsManager.originChainIdFlow.map { it?.let { walletConstants.tip(it) } }
    private val tipAmountFlow = combine(tipFlow, assetFlow) { tip: BigInteger?, asset: Asset? ->
        tip?.let {
            asset?.token?.amountFromPlanks(it)
        }
    }

    private suspend fun buildTransferDraft(): CrossChainTransferDraft? {
        val recipientAddress = addressInputFlow.value
        val originFeeAmount = originFeeAmountFlow.value ?: return null
        val destinationFeeAmount = destinationFeeAmountFlow.value ?: BigDecimal.ZERO

        val originChainId = originChainId ?: return null
        val destinationChainId = chainAssetsManager.destinationChainId ?: return null
        val assetId = assetId ?: return null
        val asset = assetFlow.value?.token?.configuration ?: return null

        val amount = enteredAmountBigDecimalFlow.value
        val tip = tipAmountFlow.firstOrNull()

        return CrossChainTransferDraft(
            amount,
            originChainId,
            destinationChainId,
            originFeeAmount,
            destinationFeeAmount,
            assetId,
            recipientAddress,
            tip,
            asset.symbol
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
            selectedOriginChainId = originChainId,
            xcmAssetSymbol = chainAssetsManager.assetSymbol
        )
    }

    override fun onAssetClick() {
        val assetId = assetId ?: return
        val originChainId = originChainId ?: return

        chainAssetsManager.observeChainIdAndAssetIdResult(
            scope = viewModelScope,
            chainType = ChainType.Origin,
            onError = { showError(it) }
        )
        router.openSelectAsset(
            chainId = originChainId,
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
        originChainId?.let {
            router.openAddressHistoryWithResult(it)
                .onEach { address ->
                    selectedWalletIdFlow.value = null
                    addressInputFlow.value = address
                }
                .launchIn(viewModelScope)
        }
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            selectedWalletIdFlow.value = null
            addressInputFlow.value = buffer
        }
    }

    override fun onAmountFocusChanged(focusState: FocusState) {
        amountInputFocusFlow.value = focusState.isFocused
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val result = walletInteractor.tryReadAddressFromSoraFormat(content) ?: content

            selectedWalletIdFlow.value = null
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

            val selfAddress = originChainId?.let { currentAccountAddress(it) } ?: return@launch
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

    override fun onMyWalletsClick() {
        router.openWalletSelectorForResult(
            selectedWalletId = selectedWalletIdFlow.value,
            walletSelectionMode = WalletSelectionMode.ExternalSelectedWallet
        )
            .onEach(::setAddressByMetaAccountId)
            .launchIn(viewModelScope)
    }

    private fun setAddressByMetaAccountId(metaId: Long) {
        viewModelScope.launch {
            selectedWalletIdFlow.value = metaId
            val metaAccount = accountInteractor.getMetaAccount(metaId)
            val destinationChainId = chainAssetsManager.destinationChainId ?: return@launch
            val destinationChain = walletInteractor.getChain(destinationChainId)
            val address = metaAccount.address(destinationChain) ?: return@launch
            addressInputFlow.value = address
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
