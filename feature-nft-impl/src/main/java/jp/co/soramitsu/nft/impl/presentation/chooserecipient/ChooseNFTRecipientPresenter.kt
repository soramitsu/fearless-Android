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
import jp.co.soramitsu.nft.impl.navigation.NavAction
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
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
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
    private val navGraphRoutesFlow = internalNFTRouter.createNavGraphRoutesFlow().shareIn(coroutinesStore.ioScope, SharingStarted.Eagerly, 1)

    private val tokenFlow = navGraphRoutesFlow
        .filterIsInstance<NFTNavGraphRoute.ChooseNFTRecipientScreen>()
        .map { destinationArgs -> destinationArgs.token }
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val addressInputFlow = MutableStateFlow("")

    private val selectedWalletIdFlow = MutableStateFlow<Long?>(null)

    private val isLoadingFlow = MutableStateFlow(false)

    private val currentToken: NFT?
        get() = tokenFlow.replayCache.lastOrNull()

    init {
        subscribeClearInputOnBackClick()
    }

    private fun subscribeClearInputOnBackClick() {
        internalNFTRouter.createNavGraphActionsFlow()
            .onEach {
                val currentRoute = navGraphRoutesFlow.replayCache.lastOrNull()
                if (it is NavAction.BackPressed && currentRoute is NFTNavGraphRoute.ChooseNFTRecipientScreen) {
                    clearInputAddress()
                }
            }
            .launchIn(coroutinesStore.uiScope)
    }

    fun handleQRCodeResult(qrCodeContent: String) {
        val result = walletInteractor.tryReadAddressFromSoraFormat(qrCodeContent) ?: qrCodeContent

        selectedWalletIdFlow.value = null
        addressInputFlow.value = result
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<ChooseNFTRecipientScreenState> {
        return tokenFlow.zipWithPrevious().flatMapLatest { (prevToken, currentToken) ->
            privateCreateScreenStateFlow(prevToken, currentToken).catch {
                internalNFTRouter.openErrorsScreen(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.common_error_network)
                )
            }
        }.stateIn(coroutineScope, SharingStarted.Lazily, ChooseNFTRecipientScreenState.default)
    }

    @OptIn(FlowPreview::class)
    @Suppress("ChainWrapping")
    private fun privateCreateScreenStateFlow(prevToken: NFT?, currentToken: NFT): Flow<ChooseNFTRecipientScreenState> {
        return flow {
            val addressInputHelperFlow =
                addressInputFlow.debounce(DEFAULT_DEBOUNCE_TIMEOUT).map { it.trim() }

            val isLoadingHelperFlow =
                isLoadingFlow.map {
                    if (prevToken == null || prevToken.tokenId == currentToken.tokenId) {
                        it
                    } else {
                        false
                    }
                }

            combine(
                addressInputHelperFlow,
                createAddressIconFlow(currentToken, addressInputHelperFlow, AddressIconGenerator.SIZE_MEDIUM),
                createSelectedAccountIconFlow(currentToken, AddressIconGenerator.SIZE_SMALL),
                createFeeInfoViewState(currentToken, addressInputHelperFlow),
                isLoadingHelperFlow
            ) {
                    addressInput,
                    addressIcon,
                    selectedWalletIcon,
                    feeInfoViewState,
                    isLoading ->

                val isPreviewButtonEnabled = addressInput.isNotBlank() &&
                        currentToken.isUserOwnedToken &&
                        feeInfoViewState.feeAmount != null &&
                        !isLoading

                return@combine ChooseNFTRecipientScreenState(
                    selectedWalletIcon = selectedWalletIcon,
                    addressInputState = AddressInputState(
                        title = resourceManager.getString(R.string.send_to),
                        input = addressInput,
                        image = addressIcon,
                        editable = false,
                        showClear = true
                    ),
                    buttonState = ButtonViewState(
                        text = resourceManager.getString(R.string.common_preview),
                        enabled = isPreviewButtonEnabled
                    ),
                    feeInfoState = feeInfoViewState,
                    isLoading = isLoading
                )
            }.collect(this)
        }
    }

    private suspend fun createAddressIconFlow(
        token: NFT,
        receiverAddressFlow: Flow<String>,
        sizeInDp: Int
    ): Flow<Any> {
        val chain = chainsRepository.getChain(token.chainId)

        return receiverAddressFlow.map { receiverAddress ->
            if (!walletInteractor.validateSendAddress(chain.id, receiverAddress)) {
                return@map R.drawable.ic_address_placeholder
            }

            return@map addressIconGenerator.createAddressModel(
                isEthereumBased = chain.isEthereumBased,
                accountAddress = receiverAddress,
                sizeInDp = sizeInDp
            ).image
        }
    }

    private suspend fun createSelectedAccountIconFlow(token: NFT, sizeInDp: Int): Flow<PictureDrawable> {
        val chain = chainsRepository.getChain(token.chainId)

        return walletInteractor.selectedAccountFlow(chain.id).map { walletAccount ->
            addressIconGenerator.createAddressModel(
                isEthereumBased = chain.isEthereumBased,
                accountAddress = walletAccount.address,
                sizeInDp = sizeInDp
            ).image
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun createFeeInfoViewState(token: NFT, addressInputFlow: Flow<String>): Flow<FeeInfoViewState> {
        val (chainId, utilityAssetId) = chainsRepository.getChain(token.chainId)
            .run { id to requireNotNull(utilityAsset?.id) }

        val networkFeeHelperFlow = addressInputFlow.map { receiver ->
            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = receiver
            )

            return@map if (isReceiverAddressValid) {
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

        return combine(
            networkFeeHelperFlow,
            walletInteractor.assetFlow(chainId, utilityAssetId)
        ) { networkFeeResult, utilityAsset ->
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
        clearInputAddress()
    }

    private fun clearInputAddress() {
        addressInputFlow.value = ""
        selectedWalletIdFlow.value = null
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

    override fun onContactsClick() {
        val chainId = currentToken?.chainId ?: return

        internalNFTRouter.openContacts(chainId).onEach {
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
