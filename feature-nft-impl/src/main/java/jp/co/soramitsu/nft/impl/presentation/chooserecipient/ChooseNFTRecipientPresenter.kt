package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.FeeInfoViewState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.zipWithPrevious
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.ValidateNFTTransferUseCase
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.CurrentAccountAddressUseCase
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class ChooseNFTRecipientPresenter @Inject constructor(
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val chainsRepository: ChainsRepository,
    private val coroutinesStore: CoroutinesStore,
    private val accountInteractor: AccountInteractor,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val validateNFTTransferUseCase: ValidateNFTTransferUseCase,
    private val currentAccountAddressUseCase: CurrentAccountAddressUseCase,
    private val internalNFTRouter: InternalNFTRouter
) : ChooseNFTRecipientCallback {

    private val tokenFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NFTNavGraphRoute.ChooseNFTRecipientScreen>()
        .map { destinationArgs -> destinationArgs.token }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val addressInputFlow = MutableStateFlow("")

    private val selectedWalletIdFlow = MutableStateFlow<Long?>(null)

    private val isLoadingFlow = MutableStateFlow(false)

    private val currentToken: NFT?
        get() = tokenFlow.replayCache.lastOrNull()

    fun handleQRCodeResult(qrCodeContent: String) {
        val result = walletInteractor.tryReadAddressFromSoraFormat(qrCodeContent) ?: qrCodeContent

        selectedWalletIdFlow.value = null
        addressInputFlow.value = result
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<ChooseNFTRecipientScreenState> {
        val addressInputHelperFlow =
            addressInputFlow.debounce(DEFAULT_DEBOUNCE_TIMEOUT).map { it.trim() }

        val isHistoryAvailableFlow =
            tokenFlow.distinctUntilChanged().map { token ->
                chainsRepository.getChain(
                    chainId = token.chainId
                ).externalApi?.history != null
            }

        val isLoadingHelperFlow =
            tokenFlow.zipWithPrevious().flatMapLatest { (prevToken, currentToken) ->
                isLoadingFlow.map {
                    if (prevToken?.tokenId == currentToken.tokenId) {
                        it
                    } else {
                        false
                    }
                }
            }

        return combine(
            addressInputHelperFlow,
            createAddressIconFlow(tokenFlow, addressInputHelperFlow, AddressIconGenerator.SIZE_MEDIUM),
            createSelectedAccountIconFlow(tokenFlow, AddressIconGenerator.SIZE_SMALL),
            createButtonState(tokenFlow, addressInputHelperFlow, isLoadingHelperFlow),
            createFeeInfoViewState(addressInputHelperFlow),
            isHistoryAvailableFlow,
            isLoadingHelperFlow
        ) {
            addressInput,
            addressIcon,
            selectedWalletIcon,
            buttonState,
            feeInfoViewState,
            isHistoryAvailable,
            isLoading ->

            return@combine ChooseNFTRecipientScreenState(
                selectedWalletIcon = selectedWalletIcon,
                addressInputState = AddressInputState(
                    title = resourceManager.getString(R.string.send_to),
                    input = addressInput,
                    image = addressIcon,
                    editable = false,
                    showClear = false
                ),
                buttonState = buttonState,
                isHistoryAvailable = isHistoryAvailable,
                feeInfoState = feeInfoViewState,
                isLoading = isLoading
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, ChooseNFTRecipientScreenState.default)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createSelectedAccountIconFlow(tokenFlow: Flow<NFT>, sizeInDp: Int): Flow<PictureDrawable> {
        return tokenFlow.mapLatest {
            chainsRepository.getChain(it.chainId)
        }.flatMapLatest { chain ->
            walletInteractor.selectedAccountFlow(chain.id).map { walletAccount ->
                addressIconGenerator.createAddressModel(
                    isEthereumBased = chain.isEthereumBased,
                    accountAddress = walletAccount.address,
                    sizeInDp = sizeInDp
                ).image
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createAddressIconFlow(
        tokenFlow: Flow<NFT>,
        receiverAddressFlow: Flow<String>,
        sizeInDp: Int
    ): Flow<Any> {
        return tokenFlow.mapLatest {
            chainsRepository.getChain(it.chainId)
        }.combine(receiverAddressFlow) { chain, receiverAddress ->
            if (!walletInteractor.validateSendAddress(chain.id, receiverAddress)) {
                return@combine R.drawable.ic_address_placeholder
            }

            return@combine addressIconGenerator.createAddressModel(
                isEthereumBased = chain.isEthereumBased,
                accountAddress = receiverAddress,
                sizeInDp = sizeInDp
            ).image
        }
    }

    private fun createButtonState(
        tokenFlow: Flow<NFT>,
        receiverAddressFlow: Flow<String>,
        isLoadingFlow: Flow<Boolean>
    ): Flow<ButtonViewState> {
        return combine(tokenFlow, receiverAddressFlow, isLoadingFlow) { token, addressInput, isLoading ->
            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = addressInput
            )

            return@combine ButtonViewState(
                text = resourceManager.getString(R.string.common_preview),
                enabled = isReceiverAddressValid && token.isUserOwnedToken && !isLoading
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createFeeInfoViewState(addressInputFlow: Flow<String>): Flow<FeeInfoViewState> {
        val networkFeeHelperFlow = combine(
            tokenFlow,
            addressInputFlow
        ) { token, receiver ->
            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = receiver
            )

            return@combine if (isReceiverAddressValid) {
                token to receiver
            } else {
                token to (currentAccountAddressUseCase(token.chainId) ?: "")
            }
        }.flatMapLatest { (token, receiver) ->
            nftTransferInteractor.networkFeeFlow(
                token = token,
                receiver = receiver,
                canReceiverAcceptToken = false
            )
        }

        val tokenChainFlow = tokenFlow.map { token ->
            chainsRepository.getChain(token.chainId)
        }.distinctUntilChanged()

        val utilityAssetFlow = tokenChainFlow.mapNotNull { chain ->
            val utilityAssetId = chain.utilityAsset?.id ?: return@mapNotNull null
            return@mapNotNull chain.id to utilityAssetId
        }.flatMapLatest { (chainId, utilityAssetId) ->
            walletInteractor.assetFlow(chainId, utilityAssetId)
        }.distinctUntilChanged()

        return combine(networkFeeHelperFlow, utilityAssetFlow) { networkFeeResult, utilityAsset ->
            val tokenSymbol = utilityAsset.token.configuration.symbol
            val tokenFiatRate = utilityAsset.token.fiatRate
            val tokenFiatSymbol = utilityAsset.token.fiatSymbol

            val networkFee = networkFeeResult.getOrNull() ?: return@combine FeeInfoViewState.default

            return@combine FeeInfoViewState(
                feeAmount = networkFee.formatCryptoDetail(tokenSymbol),
                feeAmountFiat = networkFee.applyFiatRate(tokenFiatRate)?.formatFiat(tokenFiatSymbol),
            )
        }
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
        val token = currentToken ?: return
        val receiver = addressInputFlow.value.trim()

        isLoadingFlow.value = true

        coroutinesStore.uiScope.launch {
            val chain = chainsRepository.getChain(token.chainId)

            val utilityAssetId = chain.utilityAsset?.id ?: return@launch
            val utilityAsset = walletInteractor.getCurrentAsset(chain.id, utilityAssetId)

            val selectedAccountAddress =
                accountInteractor.selectedMetaAccount().address(chain) ?: return@launch

            val tokenBalance = nftTransferInteractor.balance(token).getOrElse { BigInteger.ZERO }

            val fee = nftTransferInteractor.networkFeeFlow(
                token,
                receiver,
                false
            ).first().getOrElse { BigDecimal.ZERO }

            val validationProcessResult = validateNFTTransferUseCase(
                chain = chain,
                recipient = receiver,
                ownAddress = selectedAccountAddress,
                utilityAsset = utilityAsset,
                fee = fee.toBigInteger(),
                skipEdValidation = false,
                balance = tokenBalance,
                confirmedValidations = emptyList(),
            )

            // error occurred inside validation
            validationProcessResult.exceptionOrNull()?.let {
                showError(it)
                return@launch
            }
            val validationResult = validationProcessResult.requireValue()

            ValidationException.fromValidationResult(validationResult, resourceManager)?.let {
                showError(it)
                return@launch
            }
            // all checks have passed - go to next step

            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = receiver
            )

            if (!isReceiverAddressValid || !token.isUserOwnedToken) {
                return@launch
            }

            internalNFTRouter.openNFTSendScreen(token, addressInputFlow.value.trim())
        }.invokeOnCompletion {
            isLoadingFlow.value = false
        }
    }

    private fun showError(throwable: Throwable) {
        when (throwable) {
            is ValidationException -> {
                val (title, text) = throwable
                internalNFTRouter.openErrorsScreen(title, text)
            }

            is TitledException -> {
                internalNFTRouter.openErrorsScreen(throwable.title, throwable.message.orEmpty())
            }

            else -> {
                throwable.message?.let { internalNFTRouter.openErrorsScreen(message = it) }
            }
        }
    }

    override fun onQrClick() {
        internalNFTRouter.openQRCodeScanner()
    }

    override fun onHistoryClick() {
        val chainId = currentToken?.chainId ?: return

        internalNFTRouter.openAddressHistory(chainId).onEach {
            selectedWalletIdFlow.value = null
            addressInputFlow.value = it
        }.launchIn(coroutinesStore.uiScope)
    }

    override fun onWalletsClick() {
        val chainId = currentToken?.chainId ?: return

        internalNFTRouter.openWalletSelectionScreen(selectedWalletIdFlow.value).onEach { metaAccountId ->
            coroutinesStore.uiScope.launch(Dispatchers.IO) {
                selectedWalletIdFlow.value = metaAccountId

                val metaAccount = accountInteractor.getMetaAccount(metaAccountId)
                val chain = chainsRepository.getChain(chainId)
                val address = metaAccount.address(chain) ?: return@launch

                addressInputFlow.value = address
            }
        }.launchIn(coroutinesStore.uiScope)
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            selectedWalletIdFlow.value = null
            addressInputFlow.value = buffer
        }
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_TIMEOUT = 300L
    }
}
