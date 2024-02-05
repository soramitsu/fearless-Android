package jp.co.soramitsu.nft.impl.presentation.confirmsend

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.NftRouter
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import jp.co.soramitsu.runtime.multiNetwork.ChainRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

class ConfirmNFTSendPresenter @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val chainRegistry: ChainRegistry,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val nftRouter: NftRouter
): ConfirmNFTSendCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = nftRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.ConfirmNFTSendScreen>()
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    fun createScreenStateFlow(): Flow<ConfirmNFTSendScreenState> {
        return channelFlow {
            val networkFeeFlow = createNetworkFeeFlow(screenArgsFlow)
                .shareIn(this, SharingStarted.Eagerly, 1)

            val buttonStateFlow = createButtonState(networkFeeFlow)

            combine(
                screenArgsFlow,
                networkFeeFlow,
                buttonStateFlow
            ) { screenArgs, networkFee, buttonState ->
                val chain = screenArgs.token.chainId.let { chainRegistry.getChain(it) }

                ConfirmNFTSendScreenState(
                    tokenThumbnailIconUrl = screenArgs.token.thumbnail,
                    fromInfoItem = TitleValueViewState(
                        title = "From",
                        value = accountInteractor.selectedMetaAccount().address(chain)
                    ),
                    toInfoItem = TitleValueViewState(
                        title = "From",
                        value = screenArgs.receiver
                    ),
                    collectionInfoItem = screenArgs.token.collectionName?.let {
                        TitleValueViewState(
                            title = "Collection",
                            value = it
                        )
                    },
                    feeInfoItem = networkFee.getOrNull()?.let {
                        TitleValueViewState(
                            title = "NetworkFee",
                            value = it.toString()
                        )
                    },
                    buttonState = buttonState,
                    isLoading = false
                )
            }.onEach {
                send(it)
            }.launchIn(this)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun createNetworkFeeFlow(
        screenArgsFlow: Flow<Destination.NestedNavGraphRoute.ConfirmNFTSendScreen>
    ): Flow<Result<BigDecimal>> {
        return screenArgsFlow.flatMapLatest {
            nftTransferInteractor.networkFeeFlow(
                it.token,
                it.receiver,
                it.isReceiverKnown
            )
        }
    }

    private fun createButtonState(networkFeeFlow: Flow<Result<BigDecimal>>): Flow<ButtonViewState> {
        return networkFeeFlow.map {
            ButtonViewState(
                text = "Confirm",
                enabled = it.isSuccess
            )
        }
    }

    override fun onConfirmClick() {
        val destination = screenArgsFlow.replayCache.lastOrNull() ?: return

        coroutineScope.launch {
            nftTransferInteractor.send(
                token = destination.token,
                receiver = destination.receiver,
                canReceiverAcceptToken = destination.isReceiverKnown
            ).fold(
                onSuccess = {
                    nftRouter.openSuccessScreen(it, destination.token.chainId)
                },
                onFailure = {
                    nftRouter.openErrorsScreen("")
                }
            )
        }
    }

    override fun onItemClick(code: Int) = Unit
}