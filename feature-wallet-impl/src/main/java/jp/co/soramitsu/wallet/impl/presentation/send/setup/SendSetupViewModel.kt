package jp.co.soramitsu.wallet.impl.presentation.send.setup

import android.net.Uri
import androidx.compose.ui.focus.FocusState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.common.utils.format
import jp.co.soramitsu.common.utils.formatAsCurrency
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.validation.AddressNotValidException
import jp.co.soramitsu.common.validation.InsufficientBalanceException
import jp.co.soramitsu.fearless_utils.extensions.fromHex
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.runtime.ext.utilityAsset
import jp.co.soramitsu.wallet.api.presentation.Validation
import jp.co.soramitsu.wallet.api.presentation.formatters.formatTokenAmount
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletConstants
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import jp.co.soramitsu.wallet.impl.domain.model.Asset
import jp.co.soramitsu.wallet.impl.domain.model.Transfer
import jp.co.soramitsu.wallet.impl.domain.model.amountFromPlanks
import jp.co.soramitsu.wallet.impl.domain.model.planksFromAmount
import jp.co.soramitsu.wallet.impl.presentation.AssetPayload
import jp.co.soramitsu.wallet.impl.presentation.WalletRouter
import jp.co.soramitsu.wallet.impl.presentation.balance.chainselector.ChainItemState
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.send.recipient.QrBitmapDecoder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val RETRY_TIMES = 3L

@HiltViewModel
class SendSetupViewModel @Inject constructor(
    val sharedState: SendSharedState,
    val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val accountInteractor: AccountInteractor,
    private val walletInteractor: WalletInteractor,
    private val walletConstants: WalletConstants,
    private val router: WalletRouter,
    private val qrBitmapDecoder: QrBitmapDecoder,
    private val clipboardManager: ClipboardManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase
) : BaseViewModel(), SendSetupScreenInterface {

    private val _showChooserEvent = MutableLiveData<Event<Unit>>()
    val showChooserEvent: LiveData<Event<Unit>> = _showChooserEvent

    val payload: AssetPayload = savedStateHandle[SendSetupFragment.KEY_PAYLOAD] ?: error("Asset not specified")

    private val initialAmount = "0"

    private val selectedChainItem = sharedState.chainIdFlow
        .map { chainId ->
            chainId?.let {
                val chain = walletInteractor.getChain(it)
                ChainItemState(
                    id = chain.id,
                    imageUrl = chain.icon,
                    title = chain.name,
                    isSelected = false,
                    tokenSymbols = chain.assets.map { it.id to it.symbol }
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
        totalBalance = resourceManager.getString(R.string.common_balance_format, "..."),
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
        defaultButtonState
    )

    private val assetFlow: StateFlow<Asset?> =
        combine(sharedState.chainIdFlow, sharedState.assetIdFlow) { chainId, assetId ->
            chainId to assetId
        }
            .mapNotNull { (chainId, assetId) ->
                when {
                    chainId == null -> null
                    assetId == null -> null
                    else -> chainId to assetId
                }
            }
            .distinctUntilChanged()
            .flatMapLatest { (chainId, assetId) ->
                walletInteractor.assetFlow(chainId, assetId)
            }
            .stateIn(this, SharingStarted.Eagerly, null)

    private val amountInputFocusFlow = MutableStateFlow(false)

    private val addressInputFlow = MutableStateFlow("")

    private val isInputAddressValidFlow = combine(addressInputFlow, sharedState.chainIdFlow) { addressInput, chainId ->
        when (chainId) {
            null -> false
            else -> walletInteractor.validateSendAddress(chainId, addressInput)
        }
    }.stateIn(this, SharingStarted.Eagerly, false)

    private val chainSelectorStateFlow = selectedChainItem.map {
        SelectorState(
            title = resourceManager.getString(R.string.common_network),
            subTitle = it?.title,
            iconUrl = it?.imageUrl
        )
    }.stateIn(this, SharingStarted.Eagerly, SelectorState.default)

    private val enteredAmountFlow = MutableStateFlow(initialAmount)

    private val amountInputViewState: Flow<AmountInputViewState> = combine(
        enteredAmountFlow,
        assetFlow,
        amountInputFocusFlow
    ) { enteredAmount, asset, isAmountInputFocused ->
        if (asset == null) {
            defaultAmountInputState
        } else {
            val tokenBalance = asset.transferable.formatTokenAmount(asset.token.configuration)
            val amount = enteredAmount.toBigDecimalOrNull().orZero()
            val fiatAmount = amount.applyFiatRate(asset.token.fiatRate)?.formatAsCurrency(asset.token.fiatSymbol)

            AmountInputViewState(
                tokenName = asset.token.configuration.symbolToShow,
                tokenImage = asset.token.configuration.iconUrl,
                totalBalance = resourceManager.getString(R.string.common_balance_format, tokenBalance),
                fiatAmount = fiatAmount,
                tokenAmount = enteredAmount,
                isActive = true,
                isFocused = isAmountInputFocused,
                allowAssetChoose = true // chain.assets.size > 1
            )
        }
    }.stateIn(this, SharingStarted.Eagerly, defaultAmountInputState)

    private val feeAmountFlow = combine(
        addressInputFlow,
        isInputAddressValidFlow,
        enteredAmountFlow,
        assetFlow.mapNotNull { it }
    ) { address, isAddressValid, enteredAmount, asset ->

        val feeRequestAddress = when {
            isAddressValid -> address
            else -> currentAccountAddress(asset.token.configuration.chainId) ?: return@combine null
        }

        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val transfer = Transfer(
            recipient = feeRequestAddress,
            amount = amount,
            chainAsset = asset.token.configuration
        )
        val fee = walletInteractor.getTransferFee(transfer)
        fee.feeAmount
    }
        .retry(RETRY_TIMES)
        .catch {
            emit(null)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val utilityAssetFlow = assetFlow.mapNotNull { it }.flatMapLatest { asset ->
        val chain = walletInteractor.getChain(asset.token.configuration.chainId)
        walletInteractor.assetFlow(chain.id, chain.utilityAsset.id)
    }

    private val feeInfoViewStateFlow: Flow<FeeInfoViewState> = combine(
        feeAmountFlow,
        utilityAssetFlow
    ) { feeAmount, utilityAsset ->
        val feeFormatted = feeAmount?.formatTokenAmount(utilityAsset.token.configuration)
        val feeFiat = feeAmount?.applyFiatRate(utilityAsset.token.fiatRate)?.formatAsCurrency(utilityAsset.token.fiatSymbol)

        FeeInfoViewState(feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val buttonStateFlow = combine(
        enteredAmountFlow,
        assetFlow
    ) { enteredAmount, asset ->
        val amount = enteredAmount.toBigDecimalOrNull().orZero()
        val amountInPlanks = asset?.token?.planksFromAmount(amount).orZero()
        ButtonViewState(
            text = resourceManager.getString(R.string.common_continue),
            enabled = amountInPlanks.compareTo(BigInteger.ZERO) != 0
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultButtonState)

    val state = combine(
        addressInputFlow,
        chainSelectorStateFlow,
        amountInputViewState,
        feeInfoViewStateFlow,
        buttonStateFlow
    ) { address, chainSelectorState, amountInputState, feeInfoState, buttonState ->

        val placeholder = resourceManager.getDrawable(R.drawable.ic_address_placeholder)
        val accountImage = if (address.isNotEmpty()) {
            runCatching { address.fromHex() }.getOrNull()?.let { accountId ->
                addressIconGenerator.createAddressIcon(accountId, AddressIconGenerator.SIZE_BIG)
            }
        } else {
            null
        }

        SendSetupViewState(
            toolbarState = toolbarViewState,
            addressInputState = AddressInputState(
                title = resourceManager.getString(R.string.send_to),
                input = address,
                image = accountImage ?: placeholder
            ),
            chainSelectorState = chainSelectorState,
            amountInputState = amountInputState,
            feeInfoState = feeInfoState,
            buttonState = buttonState
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, defaultState)

    init {
        sharedState.update(payload.chainId, payload.chainAssetId)
    }

    override fun onAmountInput(input: String) {
        enteredAmountFlow.value = input.replace(',', '.')
    }

    override fun onAddressInput(input: String) {
        addressInputFlow.value = input
    }

    override fun onAddressInputClear() {
        addressInputFlow.value = ""
    }

    override fun onNextClick() {
        assetFlow.value?.let { asset ->
            val amount = enteredAmountFlow.value.toBigDecimalOrNull().orZero()
            val inPlanks = asset.token.planksFromAmount(amount)
            isValid(amount).fold({

                onNextStep(inPlanks)


            }, {
                showError(it)
            })
        }
    }

    private val validations = listOf(
        Validation(
            condition = {
                isInputAddressValidFlow.value
            },
            error = AddressNotValidException(resourceManager)
        ),
        Validation(
            condition = {
                val asset = assetFlow.value
                val transferableInPlanks = asset?.token?.planksFromAmount(asset.transferable).orZero()
                it < transferableInPlanks
            },
            error = InsufficientBalanceException(resourceManager)
        )
    )

    private fun isValid(amount: BigDecimal): Result<Any> {
        val amountInPlanks = assetFlow.value?.token?.planksFromAmount(amount).orZero()
        val allValidations = validations
        val firstError = allValidations.mapNotNull {
            if (it.condition(amountInPlanks)) null else it.error
        }.firstOrNull()

        return firstError?.let { Result.failure(it) } ?: Result.success(Unit)
    }

    fun onNextStep(amoutInPlanks: BigInteger) {
        launch {
            val transferDraft = buildTransferDraft() ?: return@launch

            router.openSendConfirm(transferDraft)
        }
//        router.openConfirmTransfer()
    }

    private val tipFlow = flowOf { walletConstants.tip(payload.chainId) }
    private val tipAmountFlow = combine(tipFlow, assetFlow) { tip: BigInteger?, asset: Asset? ->
        tip?.let {
            asset?.token?.amountFromPlanks(it)
        }
    }

    private suspend fun buildTransferDraft(): TransferDraft? {
        val recipientAddress = addressInputFlow.firstOrNull() ?: return null
        val feeAmount = feeAmountFlow.firstOrNull() ?: return null
        val tip = tipAmountFlow.firstOrNull()

        val amount = enteredAmountFlow.firstOrNull()?.toBigDecimalOrNull().orZero()

        return TransferDraft(amount, feeAmount, payload, recipientAddress, tip)
    }


    override fun onChainClick() {
        sharedState.assetId?.let { assetId ->
            router.openSelectChain(assetId)
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

    override fun onScanClick() {
        _showChooserEvent.value = Event(Unit)
    }

    override fun onHistoryClick() {
        showMessage("On history clicked")
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
            val result = walletInteractor.getRecipientFromQrCodeContent(content).getOrDefault(content)

            addressInputFlow.value = result
        }
    }

    fun qrFileChosen(uri: Uri) {
        viewModelScope.launch {
            val result = qrBitmapDecoder.decodeQrCodeFromUri(uri)

            if (result.isSuccess) {
                qrCodeScanned(result.requireValue())
            } else {
                showError(resourceManager.getString(R.string.invoice_scan_error_no_info))
            }
        }
    }

    override fun onQuickAmountInput(input: Double) {
        launch {
            combine(assetFlow, tipFlow) { asset, tip ->
                asset to tip
            }.collect { (asset, tip) ->
                val allAmount = asset?.transferable ?: return@collect

                val tipAmount = asset.token.amountFromPlanks(tip.orZero())

                val amountToTransfer = (allAmount * input.toBigDecimal()) - tipAmount

                val selfAddress = currentAccountAddress(payload.chainId) ?: return@collect

                val transfer = Transfer(
                    recipient = selfAddress,
                    amount = input.toBigDecimal(),
                    chainAsset = asset.token.configuration
                )
                val fee = walletInteractor.getTransferFee(transfer)

                val quickAmountWithoutExtraPays = amountToTransfer - fee.feeAmount

                if (quickAmountWithoutExtraPays < BigDecimal.ZERO) {
                    return@collect
                }

                val newAmount = quickAmountWithoutExtraPays.format()
                enteredAmountFlow.value = newAmount.replace(',', '.')
            }
        }
    }
}
