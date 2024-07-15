package jp.co.soramitsu.wallet.impl.presentation.send.setupcbdc

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
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
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.PhishingType
import jp.co.soramitsu.wallet.impl.domain.model.QrContentCBDC
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.send.confirm.ConfirmSendFragment
import jp.co.soramitsu.wallet.impl.presentation.send.setup.SendSetupViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class CBDCSendSetupViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val router: WalletRouter,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase
) : BaseViewModel(), CBDCSendSetupScreenInterface {
    companion object {
        const val CBDC_BRIDGE = "cnRW3S6XXtQJtYDRdL7sF6o1PMWihqRrZ9R5KiXsNyJRsqBVW"
    }

    private val _openValidationWarningEvent =
        MutableLiveData<Event<Pair<TransferValidationResult, ValidationWarning>>>()
    val openValidationWarningEvent: LiveData<Event<Pair<TransferValidationResult, ValidationWarning>>> =
        _openValidationWarningEvent

    val cbdcQrInfo: QrContentCBDC = savedStateHandle[CBDCSendSetupFragment.KEY_CBDC_INFO] ?: error("Required recipient info not specified")

    private val initialAmount = cbdcQrInfo.transactionAmount.orZero()
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val chainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId

    private val defaultAddressInputState = AddressInputState(
        title = resourceManager.getString(R.string.send_fund),
        cbdcQrInfo.recipientId,
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

    private val defaultState = CBDCSendSetupViewState(
        toolbarState = toolbarViewState,
        addressInputState = defaultAddressInputState,
        amountInputState = defaultAmountInputState,
        chainSelectorState = SelectorState.default,
        feeInfoState = FeeInfoViewState.default,
        warningInfoState = null,
        buttonState = defaultButtonState,
        isSoftKeyboardOpen = false
    )

    private val assetFlow: StateFlow<Asset?> = flowOf {
        val chainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        val chain = walletInteractor.getChain(chainId)
        chain.assets.firstOrNull {
            it.currencyId == bokoloCashTokenId
        }?.let {
            walletInteractor.getCurrentAsset(it.chainId, it.id)
        }
    }
        .stateIn(this, SharingStarted.Eagerly, null)

    private val amountInputFocusFlow = MutableStateFlow(false)

    private val isSoftKeyboardOpenFlow = MutableStateFlow(initialAmount.isZero())

    private val enteredAmountBigDecimalFlow = MutableStateFlow(initialAmount)
    private val visibleAmountFlow = MutableStateFlow(initialAmount)
    private val initialAmountFlow = MutableStateFlow(initialAmount.takeIf { it.isNotZero() })
    private val lockAmountInputFlow = MutableStateFlow(initialAmount.isNotZero())

    private val chainSelectorState = SelectorState(
        title = resourceManager.getString(R.string.common_network),
        subTitle = "Bokolo Cash",
        iconUrl = null,
        iconOverrideResId = R.drawable.ic_bokolocash,
        clickable = false,
        actionIcon = null
    )

    private val amountInputViewState: Flow<AmountInputViewState> = combine(
        visibleAmountFlow,
        initialAmountFlow,
        assetFlow,
        amountInputFocusFlow,
        lockAmountInputFlow
    ) { amount, initialAmount, asset, isAmountInputFocused, isLockAmountInput ->
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
                allowAssetChoose = false,
                precision = asset.token.configuration.precision,
                inputEnabled = isLockAmountInput.not()
            )
        }
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeAmountFlow: StateFlow<BigDecimal?> = assetFlow.map { asset ->
        asset?.token?.configuration?.let { chainAsset ->
            Transfer(
                recipient = CBDC_BRIDGE,
                sender = requireNotNull(currentAccountAddress.invoke(chainAsset.chainId)),
                amount = cbdcQrInfo.transactionAmount,
                chainAsset = chainAsset,
                comment = cbdcQrInfo.recipientId
            )
        }
    }.flatMapLatest { transfer ->
        transfer?.let { walletInteractor.observeTransferFee(transfer).map { it.feeAmount } }
            ?: flowOf(null)
    }
        .retry(SendSetupViewModel.RETRY_TIMES)
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
        val showFeeAsset = if (utilityAsset.transferable > feeAmount) {
            utilityAsset
        } else {
            asset
        }

        val assetFeeAmount = if (utilityAsset.transferable > feeAmount) {
            feeAmount
        } else {
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
        }

        val feeFormatted = assetFeeAmount?.formatCryptoDetail(showFeeAsset.token.configuration.symbol)
        val feeFiat = assetFeeAmount?.applyFiatRate(showFeeAsset.token.fiatRate)?.formatFiat(showFeeAsset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val isWarningExpanded = MutableStateFlow(false)
    private val phishingModelFlow = flowOf { walletInteractor.getPhishingInfo(CBDC_BRIDGE) }

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

    private val addressInputStateFlow = flowOf {
        AddressInputState(
            title = resourceManager.getString(R.string.send_to),
            input = cbdcQrInfo.recipientId,
            image = addressIconGenerator.createAddressIcon(
                isEthereumBased = false,
                accountAddress = CBDC_BRIDGE,
                sizeInDp = AddressIconGenerator.SIZE_BIG
            ),
            editable = false,
            showClear = false
        )
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

    val state = combine(
        addressInputStateFlow,
        amountInputViewState,
        feeInfoViewStateFlow,
        warningInfoStateFlow,
        buttonStateFlow,
        isSoftKeyboardOpenFlow
    ) { addressInputState, amountInputState, feeInfoState, warningInfoState, buttonState, isSoftKeyboardOpen ->

        confirmedValidations.clear()

        CBDCSendSetupViewState(
            toolbarState = toolbarViewState,
            addressInputState = addressInputState,
            chainSelectorState = chainSelectorState,
            amountInputState = amountInputState,
            feeInfoState = feeInfoState,
            warningInfoState = warningInfoState,
            buttonState = buttonState,
            isSoftKeyboardOpen = isSoftKeyboardOpen
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    override fun onAmountInput(input: BigDecimal?) {
        visibleAmountFlow.value = input.orZero()
        enteredAmountBigDecimalFlow.value = input.orZero()
    }

    override fun onNextClick() {
        viewModelScope.launch {
            val asset = assetFlow.value ?: return@launch

            val amount = enteredAmountBigDecimalFlow.value
            val inPlanks = asset.token.planksFromAmount(amount).orZero()
            val recipientAddress = CBDC_BRIDGE
            val selfAddress = currentAccountAddress(chainId) ?: return@launch
            val fee = feeInPlanksFlow.value
            val validationProcessResult = validateTransferUseCase(
                amountInPlanks = inPlanks,
                originAsset = asset,
                destinationChainId = chainId,
                destinationAddress = recipientAddress,
                originAddress = selfAddress,
                originFee = fee,
                confirmedValidations = confirmedValidations,
                transferMyselfAvailable = false
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
            val overrides = mapOf(
                ConfirmSendFragment.KEY_OVERRIDE_ICON_RES_ID to R.drawable.ic_bokolocash,
                ConfirmSendFragment.KEY_OVERRIDE_TO_VALUE to cbdcQrInfo.recipientId
            )
            val additionalRemark = cbdcQrInfo.recipientId

            router.openSendConfirm(transferDraft, phishingType, overrides, additionalRemark)
        }
    }

    private suspend fun buildTransferDraft(): TransferDraft? {
        val feeAmount = feeAmountFlow.firstOrNull() ?: return null
        val payload: AssetPayload = assetFlow.mapNotNull { it?.token?.configuration }.map {
            AssetPayload(it.chainId, it.id)
        }.firstOrNull() ?: return null

        return TransferDraft(
            amount = enteredAmountBigDecimalFlow.value,
            fee = feeAmount,
            assetPayload = payload,
            recipientAddress = CBDC_BRIDGE,
            tip = null
        )
    }

    override fun onNavigationClick() {
        router.back()
    }

    override fun onAmountFocusChanged(isFocused: Boolean) {
        amountInputFocusFlow.value = isFocused
    }

    fun setSoftKeyboardOpen(isOpen: Boolean) {
        isSoftKeyboardOpenFlow.value = isOpen
    }

    override fun onWarningInfoClick() {
        isWarningExpanded.value = !isWarningExpanded.value
    }

    fun warningConfirmed(validationResult: TransferValidationResult) {
        confirmedValidations.add(validationResult)
        onNextClick()
    }
}