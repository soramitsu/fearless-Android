package jp.co.soramitsu.nft.impl.presentation.chooserecipient

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.createEthereumAddressIcon
import jp.co.soramitsu.common.compose.component.AddressInputState
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.domain.models.NFT
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientCallback
import jp.co.soramitsu.nft.impl.presentation.chooserecipient.contract.ChooseNFTRecipientScreenState
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

class ChooseNFTRecipientPresenter @Inject constructor(
    private val addressIconGenerator: AddressIconGenerator,
    private val clipboardManager: ClipboardManager,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val chainsRepository: ChainsRepository,
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter
): ChooseNFTRecipientCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tokenFlow = internalNFTRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.ChooseNFTRecipientScreen>()
        .map { destinationArgs -> destinationArgs.token }
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    private val selectedWalletIdFlow = MutableStateFlow<Long?>(null)

    private val addressInputFlow = MutableStateFlow("")

    fun setNewReceiverAddress(address: String) {
        selectedWalletIdFlow.value = null
        addressInputFlow.value = address
    }

    @OptIn(FlowPreview::class)
    fun createScreenStateFlow(): Flow<ChooseNFTRecipientScreenState> {
        return channelFlow {
            val addressInputHelperFlow =
                addressInputFlow.debounce(300).map {
                    it.trim()
                }.shareIn(this, SharingStarted.Eagerly, 1)

            val addressIconFlow = createAddressIconFlow(tokenFlow, addressInputHelperFlow)

            val buttonStateFlow = createButtonState(tokenFlow, addressInputHelperFlow)

            val isHistoryAvailableFlow =
                tokenFlow.distinctUntilChanged().map { token ->
                    chainsRepository.getChain(
                        chainId = token.chainId
                    ).externalApi?.history != null
                }

            combine(
                addressInputHelperFlow,
                addressIconFlow,
                buttonStateFlow,
                isHistoryAvailableFlow
            ) { addressInput, addressIcon, buttonState, isHistoryAvailable ->
                val screenState = ChooseNFTRecipientScreenState(
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

                send(screenState)
            }.launchIn(this)
        }
    }

    private fun createAddressIconFlow(
        tokenFlow: Flow<NFT.Full>,
        receiverAddressFlow: Flow<String>
    ): Flow<Any> {
        return combine(tokenFlow, receiverAddressFlow) { token, receiverAddress ->
            val isReceiverAddressValid = nftTransferInteractor.isReceiverAddressCorrect(
                chainId = token.chainId,
                receiver = receiverAddress
            ).getOrNull() == true

            return@combine if (isReceiverAddressValid) {
                addressIconGenerator.createEthereumAddressIcon(receiverAddress, 40)
            } else {
                R.drawable.ic_address_placeholder
            }
        }
    }

    private fun createButtonState(
        tokenFlow: Flow<NFT.Full>,
        receiverAddressFlow: Flow<String>
    ): Flow<ButtonViewState> {
        return channelFlow {
            val isVerificationCompleted = AtomicBoolean(true)

            val addressInputHelperFlow =
                receiverAddressFlow.onEach {
                    isVerificationCompleted.compareAndSet(
                        true,
                        false
                    )
                }.shareIn(this, SharingStarted.Eagerly, 1)

            combine(tokenFlow, addressInputHelperFlow) { token, addressInput ->
                val isReceiverAddressValid = nftTransferInteractor.isReceiverAddressCorrect(
                    chainId = token.chainId,
                    receiver = addressInput
                ).getOrNull() ?: false

                isVerificationCompleted.set(true)

                return@combine isReceiverAddressValid && token.isUserOwnedToken
            }.combine(addressInputHelperFlow) { isButtonEnabled, _ ->
                val buttonViewState = ButtonViewState(
                    text = resourceManager.getString(R.string.common_preview),
                    enabled = isVerificationCompleted.get() && isButtonEnabled
                )

                send(buttonViewState)
            }.launchIn(this)
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
        val token = tokenFlow.replayCache.lastOrNull() ?: return
        val receiver = addressInputFlow.value.trim()

        coroutineScope.launch {
            runCatching {
                val isReceiverAddressValid =
                    nftTransferInteractor.isReceiverAddressCorrect(
                        chainId = token.chainId,
                        receiver = receiver
                    ).getOrThrow()

                if (isReceiverAddressValid && token.isUserOwnedToken) {
                    internalNFTRouter.openNFTSendScreen(token, addressInputFlow.value.trim())
                } else {
                    error(
                        """
                            Can not send token.
                        """.trimIndent()
                    )
                }
            }.getOrElse {
                internalNFTRouter.openErrorsScreen(it.message ?: "Something went wrong.")
            }
        }
    }

    override fun onQrClick() {
        internalNFTRouter.openQRCodeScanner()
    }

    override fun onHistoryClick() {
        tokenFlow.replayCache.lastOrNull()?.chainId?.let { chainId ->
            internalNFTRouter.openAddressHistory(chainId).onEach {
                selectedWalletIdFlow.value = null
                addressInputFlow.value = it
            }.launchIn(coroutineScope)
        }
    }

    override fun onWalletsClick() {
        val chainId = tokenFlow.replayCache.lastOrNull()?.chainId ?: return

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

}