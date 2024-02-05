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
import jp.co.soramitsu.nft.impl.navigation.NftRouter
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
    private val nftRouter: NftRouter
): ChooseNFTRecipientCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val tokenFlow = nftRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.ChooseNFTRecipientScreen>()
        .map { destinationArgs -> destinationArgs.token }
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    private val addressInputFlow = MutableStateFlow("")

    fun setNewReceiverAddress(address: String) {
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

                val isTokenSendable = nftTransferInteractor.isTokenSendable(
                    token = token,
                    receiver = addressInput,
                    canReceiverAcceptToken = false
                ).getOrNull() ?: false

                isVerificationCompleted.set(true)

                return@combine isReceiverAddressValid && isTokenSendable
            }.combine(addressInputHelperFlow) { isButtonEnabled, _ ->
                val buttonViewState = ButtonViewState(
                    text = resourceManager.getString(R.string.common_continue),
                    enabled = isVerificationCompleted.get() && isButtonEnabled
                )

                send(buttonViewState)
            }.launchIn(this)
        }
    }

    override fun onAddressInput(input: String) {
        addressInputFlow.value = input
    }

    override fun onAddressInputClear() {
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

                val isTokenSendable = nftTransferInteractor.isTokenSendable(
                    token = token,
                    receiver = receiver,
                    canReceiverAcceptToken = false
                ).getOrNull() ?: false

                if (!isReceiverAddressValid || !isTokenSendable) {
                    nftRouter.openNFTSendScreen(token, addressInputFlow.value.trim())
                } else {
                    error(
                        """
                            Can not send token.
                        """.trimIndent()
                    )
                }
            }.getOrElse {
                nftRouter.openErrorsScreen(it.message ?: "Something went wrong.")
            }
        }
    }

    override fun onQrClick() {
        nftRouter.openQRCodeScanner()
    }

    override fun onHistoryClick() {
        tokenFlow.replayCache.lastOrNull()?.chainId?.let { chainId ->
            nftRouter.openAddressHistory(chainId)
        }
    }

    override fun onWalletsClick() {
        nftRouter.openWalletSelectionScreen { metaAccountId ->
            coroutineScope.launch {
                val metaAccount = accountInteractor.getMetaAccount(metaAccountId)
                val chainId = tokenFlow.replayCache.first().chainId
                val address = metaAccount.address(chainsRepository.getChain(chainId)) ?: return@launch
                addressInputFlow.value = address
            }
        }
    }

    override fun onPasteClick() {
        clipboardManager.getFromClipboard()?.let { buffer ->
            addressInputFlow.value = buffer
        }
    }

}