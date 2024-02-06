package jp.co.soramitsu.nft.impl.presentation.confirmsend

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
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
    private val internalNFTRouter: InternalNFTRouter
): ConfirmNFTSendCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = internalNFTRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.ConfirmNFTSendScreen>()
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(): Flow<ConfirmNFTSendScreenState> {
        return channelFlow {
            val networkFeeHelperFlow = screenArgsFlow.flatMapLatest { destinationArgs ->
                nftTransferInteractor.networkFeeFlow(
                    destinationArgs.token,
                    destinationArgs.receiver,
                    destinationArgs.isReceiverKnown
                )
            }.onEach { result ->
                result.getOrElse {
                    internalNFTRouter.openErrorsScreen(it.message ?: "Something went wrong.")
                }
            }

            combine(
                screenArgsFlow,
                networkFeeHelperFlow
            ) { screenArgs, networkFeeResult ->
                val chain = screenArgs.token.chainId.let { chainRegistry.getChain(it) }

                ConfirmNFTSendScreenState(
                    tokenThumbnailIconUrl = screenArgs.token.thumbnail,
                    fromInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.transaction_details_from),
                        value = accountInteractor.selectedMetaAccount().address(chain)
                    ),
                    toInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.polkaswap_to),
                        value = screenArgs.receiver
                    ),
                    collectionInfoItem = screenArgs.token.collectionName?.let {
                        TitleValueViewState(
                            title = resourceManager.getString(R.string.nft_collection_title),
                            value = it
                        )
                    },
                    feeInfoItem = networkFeeResult.getOrNull()?.let {
                        TitleValueViewState(
                            title = resourceManager.getString(R.string.common_network_fee),
                            value = it.toString()
                        )
                    },
                    buttonState = ButtonViewState(
                        text = resourceManager.getString(R.string.common_confirm),
                        enabled = networkFeeResult.isSuccess
                    ),
                    isLoading = false
                )
            }.onEach {
                send(it)
            }.launchIn(this)
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
                    internalNFTRouter.openSuccessScreen(it, destination.token.chainId)
                },
                onFailure = {
                    internalNFTRouter.openErrorsScreen("")
                }
            )
        }
    }

    override fun onItemClick(code: Int) = Unit
}