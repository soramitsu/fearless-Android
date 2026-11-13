package jp.co.soramitsu.wallet.impl.presentation.send.setup

import jp.co.soramitsu.runtime.ext.isUniversalWalletBitcoin
import jp.co.soramitsu.runtime.ext.isUniversalWalletSolana
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.account.api.domain.interfaces.NomisScoreInteractor
import jp.co.soramitsu.account.api.domain.model.NomisScoreData
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.account.api.domain.model.hasEthereum
import jp.co.soramitsu.account.api.domain.model.hasSubstrate
import jp.co.soramitsu.account.api.domain.model.hasTon
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.base.errors.ValidationWarning
import jp.co.soramitsu.common.compose.component.AddressInputWithScore
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.compose.component.QuickAmountInput
import jp.co.soramitsu.common.compose.component.SelectorState
import jp.co.soramitsu.common.compose.component.TextInputViewState
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.component.WarningInfoState
import jp.co.soramitsu.common.compose.component.emptyClick
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.data.network.runtime.binding.cast
import jp.co.soramitsu.common.model.WalletEcosystem
import jp.co.soramitsu.common.model.toAccountType
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.common.presentation.dataOrNull
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.Event
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCrypto
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.greaterThen
import jp.co.soramitsu.common.utils.isNotZero
import jp.co.soramitsu.common.utils.isZero
import jp.co.soramitsu.common.utils.orZero
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.validation.ExistentialDepositCrossedWarning
import jp.co.soramitsu.core.models.Ecosystem
import jp.co.soramitsu.core.utils.isValidAddress
import jp.co.soramitsu.runtime.ext.isValidRecipientAddress
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_wallet_impl.BuildConfig
import jp.co.soramitsu.feature_wallet_impl.R
import jp.co.soramitsu.polkaswap.api.domain.PolkaswapInteractor
import jp.co.soramitsu.polkaswap.api.models.Market
import jp.co.soramitsu.polkaswap.api.models.WithDesired
import jp.co.soramitsu.runtime.multiNetwork.chain.model.Chain
import jp.co.soramitsu.runtime.multiNetwork.chain.model.bokoloCashTokenId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraMainChainId
import jp.co.soramitsu.runtime.multiNetwork.chain.model.soraTestChainId
import jp.co.soramitsu.wallet.api.domain.ExistentialDepositUseCase
import jp.co.soramitsu.wallet.api.domain.TransferValidationResult
import jp.co.soramitsu.wallet.api.domain.ValidateTransferUseCase
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.data.repository.tranfser.NotInitializedTonAccountException
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.SendPausedException
import jp.co.soramitsu.wallet.impl.domain.interfaces.QuickInputsUseCase
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
import jp.co.soramitsu.wallet.impl.presentation.send.NativeSendError
import jp.co.soramitsu.wallet.impl.presentation.send.SendSharedState
import jp.co.soramitsu.wallet.impl.presentation.send.TransferDraft
import jp.co.soramitsu.wallet.impl.presentation.send.showSendPausedSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.retry
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

@HiltViewModel
class SendSetupViewModel @Inject constructor(
    private val sharedState: SendSharedState,
    val savedStateHandle: SavedStateHandle,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val nomisScoreInteractor: NomisScoreInteractor,
    private val polkaswapInteractor: PolkaswapInteractor,
    private val existentialDepositUseCase: ExistentialDepositUseCase,
    private val walletConstants: WalletConstants,
    private val router: WalletRouter,
    private val clipboardManager: ClipboardManager,
    private val addressIconGenerator: AddressIconGenerator,
    private val currentAccountAddress: CurrentAccountAddressUseCase,
    private val validateTransferUseCase: ValidateTransferUseCase,
    private val quickInputsUseCase: QuickInputsUseCase
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
    private val initSendToAmount: BigDecimal? =
        savedStateHandle[SendSetupFragment.KEY_INITIAL_AMOUNT]
    private val lockSendToAmount: Boolean =
        savedStateHandle.get<Boolean>(SendSetupFragment.KEY_LOCK_AMOUNT) == true

    private val initialAmount = initSendToAmount.orZero()
    private val confirmedValidations = mutableListOf<TransferValidationResult>()

    private val chainIdFlow = sharedState.chainIdFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

    private val selectedChain = chainIdFlow.map { chainId ->
        chainId?.let { walletInteractor.getChain(it) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialValue = null)

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

    private val defaultAddressInputState = AddressInputWithScore.Empty(
        resourceManager.getString(R.string.send_to),
        resourceManager.getString(R.string.search_textfield_placeholder)
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
        sendAllAllowed = false,
        commentState = null
    )

    private val assetFlow: MutableStateFlow<Asset?> = MutableStateFlow(null)

    private val amountInputFocusFlow = MutableStateFlow(false)

    private val addressInputFlow = MutableStateFlow(initSendToAddress.orEmpty())
    private val addressInputTrimmedFlow = addressInputFlow.map { it.trim() }

    override val isSoftKeyboardOpenFlow =
        MutableStateFlow(lockSendToAmount && initialAmount.isZero())

    private val quickInputsStateFlow = MutableStateFlow<Map<Double, BigDecimal>?>(null)

    //    private val maxAmountFlow = MutableStateFlow(BigDecimal.ZERO)
    private val enteredAmountBigDecimalFlow = MutableStateFlow(initialAmount)
    private val visibleAmountFlow = MutableStateFlow(initialAmount)
    private val initialAmountFlow = MutableStateFlow(initialAmount.takeIf { it.isNotZero() })
    private val lockAmountInputFlow = MutableStateFlow(initialAmount.isNotZero())
    private val lockInputFlow = MutableStateFlow(lockSendToAmount)

    private val isInputAddressValidFlow =
        combine(addressInputTrimmedFlow, selectedChain.filterNotNull()) { addressInput, chain ->
            chain.isValidRecipientAddress(addressInput)
        }.stateIn(this, SharingStarted.Eagerly, false)

    private val chainSelectorStateFlow =
        combine(
            selectedChainItem,
            lockInputFlow
        ) { it: ChainSelectScreenContract.State.ItemState?, isLock: Boolean ->
            SelectorState(
                title = resourceManager.getString(R.string.common_network),
                subTitle = it?.title,
                iconUrl = it?.imageUrl,
                clickable = isLock.not(),
                actionIcon = if (isLock) null else R.drawable.ic_arrow_down
            )
        }.stateIn(this, SharingStarted.Eagerly, SelectorState.default)


    private val quickAmountGate = SendAmountRequestGate()
    private var quickAmountJob: Job? = null

    private var maxSelection: Pair<String, BigDecimal>? = null

    private val amountInputViewState = MutableStateFlow(defaultAmountInputState)

    // Declared before every flow and the init block that await it: properties initialize in
    // order, and the fee flow can run on Dispatchers.Default before the constructor finishes.
    private val currentMetaAccountDeffered = viewModelScope.async { withContext(Dispatchers.Default) { walletInteractor.getSelectedMetaAccount() } }

    private val currentAccountAddressFlow = chainIdFlow.map {
        it ?: return@map null
        currentAccountAddress(it)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    private val feeRequests = combine(
        addressInputTrimmedFlow,
        isInputAddressValidFlow,
        enteredAmountBigDecimalFlow,
        assetFlow.mapNotNull { it }.distinctUntilChanged(),
        selectedChain.filterNotNull()
    ) { address, isAddressValid, enteredAmount, asset, chain ->

        val currentAddress = currentMetaAccountDeffered.await().address(chain)
        val feeRequestAddress = when {
            isAddressValid -> address
            else -> currentMetaAccountDeffered.await().address(chain) ?: return@combine null
        }

        Transfer(
            recipient = feeRequestAddress,
            sender = requireNotNull(currentAddress),
            amount = enteredAmount,
            chainAsset = asset.token.configuration
        )
    }
        .distinctUntilChanged()

    private val feeAmountFlow = SendFeeRequestFlow.observe(feeRequests, walletInteractor::observeTransferFee) { error ->
        withContext(Dispatchers.Main) {
            when (error) {
                is SendPausedException -> showSendPaused(error)
                is NotInitializedTonAccountException -> showError(resourceManager.getString(R.string.fee_calculation_error_inactive_account))
                else -> showError(resourceManager.getString(NativeSendError.message(error)))
            }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val feeInPlanksFlow = combine(feeAmountFlow, assetFlow) { fee, asset ->
        fee ?: return@combine null
        asset ?: return@combine null
        val scaledFee = fee.setScale(asset.token.configuration.precision, RoundingMode.DOWN)
        asset.token.planksFromAmount(scaledFee)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    @OptIn(ExperimentalCoroutinesApi::class)
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
        val utilityAvailable = utilityAsset.transferable ?: return@combine FeeInfoViewState.default
        val isSendBokoloCash = asset.token.configuration.currencyId == bokoloCashTokenId
        val showFeeAsset = if (isSendBokoloCash && utilityAvailable < feeAmount) {
            asset
        } else {
            utilityAsset
        }

        val assetFeeAmount = if (isSendBokoloCash && utilityAvailable < feeAmount) {
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

        val native = walletInteractor.getChain(asset.token.configuration.chainId).let { it.isUniversalWalletBitcoin() || it.isUniversalWalletSolana() }
        val feeFormatted = assetFeeAmount?.let {
            if (native) jp.co.soramitsu.wallet.impl.presentation.send.NativeSendFormat.amount(it, showFeeAsset.token.configuration.symbol)
            else it.formatCryptoDetail(showFeeAsset.token.configuration.symbol)
        }
        val feeFiat = assetFeeAmount?.applyFiatRate(showFeeAsset.token.fiatRate)
            ?.formatFiat(showFeeAsset.token.fiatSymbol)

        FeeInfoViewState(caption = if (native) resourceManager.getString(R.string.choose_amount_fee) else null, feeAmount = feeFormatted, feeAmountFiat = feeFiat)
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, FeeInfoViewState.default)

    private val isWarningExpanded = MutableStateFlow(false)
    private val phishingModelFlow = addressInputTrimmedFlow.map {
        walletInteractor.getPhishingInfo(it)
    }
    private val nomisScore = addressInputTrimmedFlow.transform {
        if (it.isEmpty()) {
            return@transform
        }
        emit(LoadingState.Loading())
        val score = nomisScoreInteractor.getNomisScore(it)
        emit(LoadingState.Loaded(score))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LoadingState.Loading())

    private val warningInfoStateFlow = combine(
        phishingModelFlow,
        isWarningExpanded,
        nomisScore,
        assetFlow.filter { it != null }
    ) { phishing, isExpanded, nomisScoreLoadingState, asset ->
        phishing?.let {
            WarningInfoState(
                message = getPhishingMessage(phishing.type, asset ?: return@combine null),
                extras = listOf(
                    phishing.name?.let { resourceManager.getString(R.string.username_setup_choose_title) to it },
                    phishing.type.let { resourceManager.getString(R.string.reason) to it.capitalizedName },
                    phishing.subtype?.let { resourceManager.getString(R.string.scam_additional_stub) to it }
                ).mapNotNull { it },
                isExpanded = isExpanded,
                color = phishing.color
            )
        } ?: nomisScoreLoadingState.dataOrNull()?.takeIf { it.score in 0..25 }?.let {
            WarningInfoState(
                message = resourceManager.getString(R.string.scam_description_lowscore_text),
                extras = listOf(
                    resourceManager.getString(R.string.username_setup_choose_title) to resourceManager.getString(
                        R.string.scam_info_nomis_name
                    ),
                    resourceManager.getString(R.string.reason) to resourceManager.getString(R.string.scam_info_nomis_reason_text),
                    resourceManager.getString(R.string.scam_additional_stub) to resourceManager.getString(
                        R.string.scam_info_nomis_subtype_text
                    )
                ),
                isExpanded = isExpanded,
                color = warningOrange
            )
        }
    }

    private fun getPhishingMessage(type: PhishingType, asset: Asset): String {
        val assetSymbol = asset.token.configuration.symbol.uppercase()
        return when (type) {
            PhishingType.SCAM -> resourceManager.getString(R.string.scam_warning_message, assetSymbol)
            PhishingType.EXCHANGE -> resourceManager.getString(R.string.exchange_warning_message)
            PhishingType.DONATION -> resourceManager.getString(
                R.string.donation_warning_message_format,
                assetSymbol
            )

            PhishingType.SANCTIONS -> resourceManager.getString(R.string.sanction_warning_message, assetSymbol)
            else -> resourceManager.getString(R.string.scam_warning_message, assetSymbol)
        }
    }

    private val buttonStateFlow = MutableStateFlow(defaultButtonState)

    private val sendAllToggleState: MutableStateFlow<ToggleState> =
        MutableStateFlow(ToggleState.INITIAL)
    private var existentialDepositCheckJob: Job? = null

    private val messageInputState = MutableStateFlow<TextInputViewState?>(null)

    val state = MutableStateFlow<SendSetupViewState>(defaultState)

    init {
        sharedState.clear()
        subscribeAsset()
        if (payload == null) {
            if (!initSendToAddress.isNullOrEmpty()) {
                findChainsForAddress(initSendToAddress)
            }
        } else {
            sharedState.update(payload.chainId, payload.chainAssetId)
        }
        initSendToAddress?.let { sharedState.updateAddress(it) }

        state.onEach {
            confirmedValidations.clear()
        }.launchIn(this)

        sharedState.addressFlow.onEach {
            it?.let(::setRecipientInput)
        }.launchIn(this)

        subscribeScreenState()
        subscribeAmountInputState()

        combine(
            visibleAmountFlow,
            assetFlow
        ) { amount, asset ->
            val amountInPlanks = asset?.token?.planksFromAmount(amount).orZero()
            ButtonViewState(
                text = resourceManager.getString(R.string.common_continue),
                enabled = amountInPlanks.isNotZero(),
                loading = false
            )
        }.onEach { newState ->
            buttonStateFlow.update { newState }
        }.launchIn(viewModelScope)

    }

    private fun subscribeAmountInputState() {
        assetFlow.filterNotNull().combine(visibleAmountFlow) { asset, amount ->
            val tokenBalance = asset.sendAvailable?.formatCrypto(asset.token.configuration.symbol)
                ?: resourceManager.getString(R.string.portfolio_balance_unavailable)
            val fiatAmount =
                amount.applyFiatRate(asset.token.fiatRate)?.formatFiat(asset.token.fiatSymbol)

            amountInputViewState.update { prev ->
                prev.copy(
                    tokenName = asset.token.configuration.symbol,
                    tokenImage = asset.token.configuration.iconUrl,
                    totalBalance = resourceManager.getString(
                        R.string.common_transferable_format,
                        tokenBalance
                    ),
                    fiatAmount = fiatAmount,
                    tokenAmount = amount,
                    precision = asset.token.configuration.precision,
                )
            }
        }.launchIn(viewModelScope)

        amountInputFocusFlow.onEach { isFocused ->
            amountInputViewState.update { prev ->
                prev.copy(isFocused = isFocused)
            }
        }.launchIn(viewModelScope)

        lockAmountInputFlow.combine(lockInputFlow) { lockAmountInput, lockInput ->
            amountInputViewState.update { prev ->
                prev.copy(inputEnabled = lockInput.not() || lockAmountInput.not())
            }
        }.launchIn(viewModelScope)

        lockInputFlow.onEach { isLocked ->
            amountInputViewState.update { prev ->
                prev.copy(allowAssetChoose = isLocked.not())
            }
        }.launchIn(viewModelScope)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun subscribeAsset() {
        var previous: Pair<String, String>? = null
        sharedState.assetIdToChainIdFlow.onEach { identity ->
            if (previous != null && identity != previous) {
                // Clear before loading the new asset; an amount never changes denomination silently.
                setEnteredAmount(BigDecimal.ZERO)
                visibleAmountFlow.value = BigDecimal.ZERO
                initialAmountFlow.value = null
                lockAmountInputFlow.value = false
                quickInputsStateFlow.value = null
                sendAllToggleState.value = ToggleState.INITIAL
                amountInputViewState.update { it.copy(tokenAmount = BigDecimal.ZERO, fiatAmount = null) }
            }
            previous = identity
            assetFlow.value = null
        }.mapLatest { identity ->
            identity?.let { (assetId, chainId) ->
                withContext(Dispatchers.Default) { walletInteractor.getCurrentAsset(chainId, assetId) }
            }
        }.onEach { assetFlow.value = it }.launchIn(viewModelScope)
    }

    private fun subscribeScreenState() {
        chainSelectorStateFlow.onEach {
            state.value = state.value.copy(chainSelectorState = it)
        }.launchIn(this)

        amountInputViewState.onEach {
            state.value = state.value.copy(amountInputState = it)
        }.launchIn(this)

        feeInfoViewStateFlow.onEach {
            state.value = state.value.copy(feeInfoState = it)
        }.launchIn(this)

        warningInfoStateFlow.onEach {
            state.value = state.value.copy(warningInfoState = it)
        }.launchIn(this)

        buttonStateFlow.onEach {
            state.value = state.value.copy(buttonState = it)
        }.launchIn(this)

        isSoftKeyboardOpenFlow.onEach {
            state.value = state.value.copy(isSoftKeyboardOpen = it)
        }.launchIn(this)

        sendAllToggleState.onEach {
            state.value = state.value.copy(
                sendAllChecked = it in listOf(
                    ToggleState.CHECKED,
                    ToggleState.CONFIRMED
                )
            )
        }.launchIn(this)

        lockInputFlow.onEach { isInputLocked ->
            state.value = state.value.copy(
                isInputLocked = isInputLocked
            )
        }.launchIn(this)

        messageInputState.onEach { messageInputState ->
            state.value = state.value.copy(
                commentState = messageInputState
            )
        }.launchIn(this)

        assetFlow.onEach { asset ->
            val quickAmountInputValues = when {
                asset?.token?.configuration?.currencyId == bokoloCashTokenId -> {
                    emptyList()
                }

                else -> {
                    val chain = asset?.token?.configuration?.chainId?.let { walletInteractor.getChain(it) }
                    if (chain?.isUniversalWalletBitcoin() == true || chain?.isUniversalWalletSolana() == true) {
                        listOf(object : jp.co.soramitsu.common.compose.component.QuickInput {
                            override val label = resourceManager.getString(R.string.send_max)
                            override val value = 1.0
                        }) + QuickAmountInput.entries
                    } else QuickAmountInput.entries
                }
            }

            // "Send all" closes the account on purpose; it needs a known minimum to explain that.
            val existentialDeposit =
                asset?.token?.configuration?.let { existentialDepositUseCase(it).getOrNull() }.orZero()
            val sendAllAllowed = existentialDeposit > BigInteger.ZERO

            state.value = state.value.copy(
                quickAmountInputValues = quickAmountInputValues,
                sendAllAllowed = sendAllAllowed
            )
        }.launchIn(this)

        combine(
            selectedChain,
            addressInputTrimmedFlow
        ) { chain, address ->
            val isAddressValid = when (chain) {
                null -> false
                else -> walletInteractor.validateSendAddress(chain.id, address)
            }

            val image: Any = if (isAddressValid.not()) {
                R.drawable.ic_address_placeholder
            } else {
                addressIconGenerator.createWalletIcon(
                    chain?.ecosystem?.toAccountType() ?: WalletEcosystem.Substrate,
                    AddressIconGenerator.SIZE_BIG
                )
            }
            val addressState = if (address.isNotEmpty()) {
                (state.value.addressInputState as? AddressInputWithScore.Filled)?.copy(
                    address = address.shortenAddress(),
                    image = image
                ) ?: AddressInputWithScore.Filled(
                    defaultAddressInputState.title,
                    address.shortenAddress(),
                    image,
                    nomisScore.value.dataOrNull()?.score
                        ?: nomisScoreInteractor.getNomisScoreFromMemoryCache(address)?.score
                        ?: NomisScoreData.LOADING_CODE
                )
            } else {
                defaultAddressInputState
            }

            state.value = state.value.copy(
                addressInputState = addressState,
                isHistoryAvailable = chain?.externalApi?.history != null
            )
        }.launchIn(this)

        chainIdFlow.combine(assetFlow) { chainId, asset ->
            if (chainId == null || asset == null) {
                return@combine
            }
            val quickInputs = quickInputsUseCase.calculateTransfersQuickInputs(
                chainId,
                asset.token.configuration.id
            )
            quickInputsStateFlow.update { quickInputs }
        }.launchIn(this)

        nomisScore.onEach {
            val score = if (it is LoadingState.Loading) {
                NomisScoreData.LOADING_CODE
            } else {
                it.dataOrNull()?.score
            }
            state.update { prevState ->
                if (prevState.addressInputState is AddressInputWithScore.Filled) {
                    prevState.copy(
                        addressInputState = prevState.addressInputState.copy(score = score)
                    )
                } else prevState
            }
        }.launchIn(viewModelScope)

        selectedChain.filterNotNull().distinctUntilChanged().onEach { chain ->
            state.update { it.copy(nativeReview = chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana(),
                crossChainAvailable = !chain.isUniversalWalletBitcoin() && !chain.isUniversalWalletSolana(), recipientHint = if (chain.isUniversalWalletBitcoin()) resourceManager.getString(R.string.send_bitcoin_address_help) else null) }
            messageInputState.update { prevState ->
                if (chain.ecosystem == Ecosystem.Ton) {
                    prevState ?: TextInputViewState(
                        "",
                        resourceManager.getString(R.string.common_message)
                    )
                } else {
                    null
                }
            }
        }.launchIn(viewModelScope)
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
            val ecosystem = selectedChain.first { it != null }.cast<Chain>().ecosystem
            if (ecosystem != Ecosystem.Substrate && ecosystem != Ecosystem.EthereumBased) {
                return@combine Result.success(TransferValidationResult.Valid)
            }

            if (amount.isZero()) {
                sendAllToggleState.value = ToggleState.INITIAL
                return@combine null
            }
            val ownAddress =
                currentAccountAddressFlow.value ?: return@combine null

            val recipientAddress = when {
                isAddressValid -> address
                else -> ownAddress
            }

            validateTransferUseCase.validateExistentialDeposit(
                amountInPlanks = asset.token.planksFromAmount(amount.orZero()),
                originAsset = asset,
                destinationChainId = asset.token.configuration.chainId,
                destinationAddress = recipientAddress,
                originAddress = ownAddress,
                originFee = fee,
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
                            ValidationException.fromValidationResult(
                                validationResult,
                                resourceManager
                            )?.let {
                                if (it is ExistentialDepositCrossedWarning) {
                                    val warning = ValidationWarning(
                                        it.message,
                                        it.explanation,
                                        it.positiveButtonText,
                                        it.negativeButtonText,
                                        if (showMaxInput) it.secondPositiveButtonText else null
                                    )
                                    _openValidationWarningEvent.value =
                                        Event(validationResult to warning)
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
            val addressChains = getAccountSupportedChains(address)
            when {
                addressChains.size == 1 -> {
                    val chain = addressChains[0]
                    when {
                        chain.assets.size == 1 -> sharedState.update(chain.id, chain.assets[0].id)
                        else -> router.openSelectChainAsset(chain.id)
                    }
                }

                addressChains.size > 1 -> router.openSelectChain(
                    filterChainIds = addressChains.map { it.id },
                    chooserMode = false,
                    currencyId = tokenCurrencyId,
                    showAllChains = false
                )

                else -> showInvalidAddressError(onConfirm = router::back)
            }
        }
    }

    // Reached from entry points without their own check (QR scan, search, repeat, asset switch).
    // This screen cannot send on a vetoed network, so both choices leave it.
    private fun showSendPaused(error: SendPausedException) {
        val receivePayload = assetFlow.value?.token?.configuration?.let { AssetPayload(it.chainId, it.id) }
        showSendPausedSheet(
            resourceManager = resourceManager,
            error = error,
            onReceive = {
                router.back()
                receivePayload?.let(router::openReceive)
            },
            onClose = router::back
        )
    }

    private fun showInvalidAddressError(onConfirm: () -> Unit = emptyClick) {
        showError(
            title = resourceManager.getString(R.string.common_warning),
            message = resourceManager.getString(R.string.error_invalid_address),
            negativeButtonText = resourceManager.getString(R.string.common_close),
            negativeClick = onConfirm
        )
    }

    override fun onAmountInput(input: BigDecimal?) {
        if (maxSelection?.second?.compareTo(input.orZero()) != 0) maxSelection = null
        visibleAmountFlow.value = input.orZero()
        setEnteredAmount(input.orZero())

        if (sendAllToggleState.value == ToggleState.CONFIRMED || sendAllToggleState.value == ToggleState.INITIAL && input?.greaterThen(
                BigDecimal.ZERO
            ) == true
        ) {
            observeExistentialDeposit(true)
        }
    }

    override fun onAddressInput(input: String) {
        setRecipientInput(input)
    }

    override fun onAddressInputClear() {
        setRecipientInput("")
        sharedState.clearAddress()
    }

    override fun onNextClick() {
        viewModelScope.launch {
            buttonStateFlow.update { prevState -> prevState.copy(loading = true, enabled = false) }
            val asset = assetFlow.value ?: return@launch

            val amount = enteredAmountBigDecimalFlow.value
            val inPlanks = asset.token.planksFromAmount(
                amount.setScale(
                    asset.token.configuration.precision,
                    RoundingMode.HALF_DOWN
                )
            ).orZero()
            val recipientAddress = addressInputTrimmedFlow.firstOrNull() ?: return@launch
            val selfAddress = currentAccountAddressFlow.value ?: return@launch
            val fee = feeInPlanksFlow.value
            val validationProcessResult = validateTransferUseCase.validateTransfer(
                amountInPlanks = inPlanks,
                asset = asset,
                destinationAddress = recipientAddress,
                senderAddress = selfAddress,
                fee = fee,
                confirmedValidations = confirmedValidations,
                transferMyselfAvailable = false,
                skipEdValidation = sendAllToggleState.value == ToggleState.CONFIRMED
            )

            // error occurred inside validation
            validationProcessResult.exceptionOrNull()?.let {
                showError(it)
                buttonStateFlow.update { prevState -> prevState.copy(loading = false, enabled = true) }
                return@launch
            }
            val validationResult = validationProcessResult.requireValue()

            ValidationException.fromValidationResult(validationResult, resourceManager)?.let {
                if (it is ValidationWarning) {
                    _openValidationWarningEvent.value = Event(validationResult to it)
                } else {
                    showError(it)
                }
                buttonStateFlow.update { prevState -> prevState.copy(loading = false, enabled = true) }
                return@launch
            }
            // all checks have passed - go to next step
            buttonStateFlow.update { prevState -> prevState.copy(loading = false, enabled = true) }
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
        val epoch = jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.snapshot()
        val sharedRevision = sharedState.revision
        val wallet = walletInteractor.getSelectedMetaAccount()
        val recipientAddress = addressInputTrimmedFlow.firstOrNull() ?: return null
        val feeAmount = feeAmountFlow.firstOrNull() ?: return null
        val tip = tipAmountFlow.firstOrNull()

        val amount = enteredAmountBigDecimalFlow.value

        val chainId = sharedState.chainId ?: return null
        val assetId = sharedState.assetId ?: return null

        val payload = AssetPayload(chainId, assetId)

        if (!jp.co.soramitsu.common.data.storage.WalletSelectionEpoch.isCurrent(epoch) || sharedState.revision != sharedRevision) return null
        return TransferDraft(
            amount,
            feeAmount,
            payload,
            recipientAddress,
            tip,
            messageInputState.value?.text,
            sendMax = maxSelection?.let { it.first == "$chainId:$assetId" && it.second.compareTo(amount) == 0 } == true,
            walletId = wallet.id, walletSelectionEpoch = epoch
        )
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
            router.openSelectAsset(selectedAssetId = assetId, selectedChainId = sharedState.chainId)
        }
    }

    override val onCrossChainClick: () -> Unit = crossChain@{
        val chain = selectedChain.value ?: return@crossChain
        if (chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana() || lockInputFlow.value) return@crossChain
        val selectedAsset = assetFlow.value?.token?.configuration ?: return@crossChain
        if (selectedAsset.chainId != chain.id || sharedState.chainId != chain.id) return@crossChain
        router.openCrossChainSend(AssetPayload(selectedAsset.chainId, selectedAsset.id))
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
            setRecipientInput(buffer)
        }
    }

    override fun onAmountFocusChanged(isFocused: Boolean) {
        amountInputFocusFlow.value = isFocused
    }

    fun qrCodeScanned(content: String) {
        viewModelScope.launch {
            val chain = sharedState.chainId?.let {
                walletInteractor.getChain(it)
            }

            if (chain?.ecosystem == Ecosystem.Ton) {

                if (chain.isValidAddress(content)) {
                    fillQrContentToAddressField(content)
                    return@launch
                }

                val extractedAddress = walletInteractor.extractTonAddress(content)
                if (extractedAddress != null && chain.isValidAddress(extractedAddress)) {
                    fillQrContentToAddressField(extractedAddress)
                    return@launch
                }

                showInvalidAddressError()

                return@launch
            }

            val soraQrContent = walletInteractor.tryReadSoraFormat(content)
            if (soraQrContent != null) {
                handleSoraQr(soraQrContent)
            } else {
                val addressChains = getAccountSupportedChains(content)
                if (addressChains.isNotEmpty()) {
                    fillQrContentToAddressField(content)
                } else {
                    showInvalidAddressError()
                }
            }
        }
    }

    private suspend fun getAccountSupportedChains(address: String): List<Chain> {
        val chains = walletInteractor.getChains().first()
        val meta = currentMetaAccountDeffered.await()
        val accountSupportedChains = chains.filter {
            when (it.ecosystem) {
                Ecosystem.Substrate -> meta.hasSubstrate
                Ecosystem.Ton -> meta.hasTon
                Ecosystem.EthereumBased,
                Ecosystem.Ethereum -> meta.hasEthereum
            }
        }
        val addressChains = accountSupportedChains.filter {
            runCatching { it.isValidRecipientAddress(address) }.getOrNull() == true
        }
        return addressChains
    }

    private fun fillQrContentToAddressField(content: String) {
        setRecipientInput(content)
        lockAmountInputFlow.value = false
        lockInputFlow.value = false
    }

    private suspend fun handleSoraQr(qrContentSora: QrContentSora) {
        val soraTokenId = qrContentSora.tokenId
        val soraAmount = qrContentSora.amount

        val soraChainId = if (BuildConfig.DEBUG) soraTestChainId else soraMainChainId
        val soraChain = walletInteractor.getChain(soraChainId)
        soraChain.assets.firstOrNull { it.currencyId == soraTokenId }?.let { asset ->
            sharedState.update(soraChainId, asset.id)
        }

        setRecipientInput(qrContentSora.address)
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
        quickAmountJob?.cancel()
        val request = quickAmountGate.begin(sharedState.revision)
        quickAmountJob = launch {
            val asset = assetFlow.firstOrNull() ?: return@launch
            val chain = selectedChain.firstOrNull() ?: return@launch
            if (!quickAmountGate.isCurrent(request, sharedState.revision)) return@launch
            val native = chain.isUniversalWalletBitcoin() || chain.isUniversalWalletSolana()
            val useMax = input == 1.0 && native
            val amount = if (useMax) {
                val wallet = walletInteractor.getSelectedMetaAccount()
                val sender = wallet.address(chain) ?: return@launch
                val recipient = addressInputTrimmedFlow.first().takeIf { walletInteractor.validateSendAddress(chain.id, it) } ?: sender
                try {
                    val result = walletInteractor.getTransferMaxAmount(Transfer(sender, recipient, BigDecimal.ONE,
                        asset.token.configuration, sendMax = true))
                    if (walletInteractor.getSelectedMetaAccount().id != wallet.id) return@launch
                    result
                } catch (error: kotlinx.coroutines.CancellationException) { throw error } catch (error: Exception) {
                    if (quickAmountGate.isCurrent(request, sharedState.revision)) showError(resourceManager.getString(NativeSendError.message(error)))
                    return@launch
                }
            } else {
                quickInputsStateFlow.first { !it.isNullOrEmpty() }?.get(input) ?: return@launch
            }
            if (!quickAmountGate.isCurrent(request, sharedState.revision) || sharedState.chainId != chain.id ||
                sharedState.assetId != asset.token.configuration.id) return@launch
            if (initialAmountFlow.value == amount) {
                initialAmountFlow.value = null
                delay(70)
            }
            if (!quickAmountGate.isCurrent(request, sharedState.revision)) return@launch
            maxSelection = if (useMax) "${chain.id}:${asset.token.configuration.id}" to amount else null
            val scale = if (native) asset.token.configuration.precision else 5
            visibleAmountFlow.value = amount.setScale(scale, RoundingMode.DOWN).stripTrailingZeros()
            initialAmountFlow.value = amount.setScale(scale, RoundingMode.DOWN)
            setEnteredAmount(amount)
            observeExistentialDeposit(true)
        }
    }

    private fun setRecipientInput(value: String) {
        quickAmountGate.invalidate()
        addressInputFlow.value = value
    }

    private fun setEnteredAmount(value: BigDecimal) {
        quickAmountGate.invalidate()
        enteredAmountBigDecimalFlow.value = value
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
            return
        }
        onNextStep()
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
        } else {
            visibleAmountFlow.value = BigDecimal.ZERO
            initialAmountFlow.value = BigDecimal.ZERO
        }
    }

    override fun onCommentInput(value: String) {
        messageInputState.update { prevState ->
            prevState?.copy(text = value) ?: prevState
        }
    }
}
