package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import android.graphics.drawable.PictureDrawable
import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createAddressModel
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ChooseNFTRecipientPresenter @Inject constructor(
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val chainsRepository: ChainsRepository,
    private val accountInteractor: AccountInteractor,
    private val walletInteractor: WalletInteractor,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
) : ChooseNFTRecipientCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tokenFlow = internalNFTRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.ChooseNFTRecipientScreen>()
        .map { destinationArgs -> destinationArgs.token }
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    private val addressInputFlow = MutableStateFlow("")

    private val selectedWalletIdFlow = MutableStateFlow<Long?>(null)

    private val currentToken: NFT.Full?
        get() = tokenFlow.replayCache.lastOrNull()

    fun handleQRCodeResult(qrCodeContent: String) {
        val result = walletInteractor.tryReadAddressFromSoraFormat(qrCodeContent) ?: qrCodeContent

        selectedWalletIdFlow.value = null
        addressInputFlow.value = result
    }

    @OptIn(FlowPreview::class)
    fun createScreenStateFlow(): Flow<ChooseNFTRecipientScreenState> {
        val addressInputHelperFlow =
            addressInputFlow.debounce(DEFAULT_DEBOUNCE_TIMEOUT).map { it.trim() }

        val isHistoryAvailableFlow =
            tokenFlow.distinctUntilChanged().map { token ->
                chainsRepository.getChain(
                    chainId = token.chainId
                ).externalApi?.history != null
            }

        return combine(
            addressInputHelperFlow,
            createAddressIconFlow(tokenFlow, addressInputHelperFlow, DEFAULT_ADDRESS_ICON_SIZE_IN_DP),
            createSelectedAccountIconFlow(tokenFlow, DEFAULT_ADDRESS_ICON_SIZE_IN_DP),
            createButtonState(tokenFlow, addressInputHelperFlow),
            isHistoryAvailableFlow
        ) { addressInput, addressIcon, selectedWalletIcon, buttonState, isHistoryAvailable ->
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
                isLoading = false
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createSelectedAccountIconFlow(tokenFlow: Flow<NFT.Full>, sizeInDp: Int): Flow<PictureDrawable> {
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
        tokenFlow: Flow<NFT.Full>,
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
            )
        }
    }

    private fun createButtonState(tokenFlow: Flow<NFT.Full>, receiverAddressFlow: Flow<String>): Flow<ButtonViewState> {
        return combine(tokenFlow, receiverAddressFlow) { token, addressInput ->
            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = addressInput
            )

            return@combine ButtonViewState(
                text = resourceManager.getString(R.string.common_preview),
                enabled = isReceiverAddressValid && token.isUserOwnedToken
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

        coroutineScope.launch {
            val isReceiverAddressValid = walletInteractor.validateSendAddress(
                chainId = token.chainId,
                address = receiver
            )

            if (!isReceiverAddressValid || !token.isUserOwnedToken) {
                return@launch
            }

            internalNFTRouter.openNFTSendScreen(token, addressInputFlow.value.trim())
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
        }.launchIn(coroutineScope)
    }

    override fun onWalletsClick() {
        val chainId = currentToken?.chainId ?: return

        internalNFTRouter.openWalletSelectionScreen(selectedWalletIdFlow.value).onEach { metaAccountId ->
            coroutineScope.launch(Dispatchers.IO) {
                selectedWalletIdFlow.value = metaAccountId

                val metaAccount = accountInteractor.getMetaAccount(metaAccountId)
                val chain = chainsRepository.getChain(chainId)
                val address = metaAccount.address(chain) ?: return@launch

                addressInputFlow.value = address
            }
        }.launchIn(coroutineScope)
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            selectedWalletIdFlow.value = null
            addressInputFlow.value = buffer
        }
    }

    private companion object {
        const val DEFAULT_ADDRESS_ICON_SIZE_IN_DP = 40
        const val DEFAULT_DEBOUNCE_TIMEOUT = 300L
    }
}
