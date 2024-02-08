package jp.co.soramitsu.nft.impl.presentation.confirmsend

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import jp.co.soramitsu.nft.navigation.NestedNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import javax.inject.Inject

class ConfirmNFTSendPresenter @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val internalNFTRouter: InternalNFTRouter
) : ConfirmNFTSendCallback {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NestedNavGraphRoute.ConfirmNFTSendScreen>()
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
                result.onFailure {
                    it.message?.let(internalNFTRouter::openErrorsScreen)
                }
            }

            val tokenChainFlow = screenArgsFlow.map { destination ->
                chainsRepository.getChain(destination.token.chainId)
            }.distinctUntilChanged().shareIn(this, SharingStarted.Eagerly, 1)

            val utilityAssetFlow = tokenChainFlow.mapNotNull { chain ->
                val utilityAssetId = chain.utilityAsset?.id ?: return@mapNotNull null
                return@mapNotNull chain.id to utilityAssetId
            }.flatMapLatest { (chainId, utilityAssetId) ->
                walletInteractor.assetFlow(chainId, utilityAssetId)
            }.distinctUntilChanged()

            combine(
                screenArgsFlow,
                tokenChainFlow,
                utilityAssetFlow,
                networkFeeHelperFlow,
                accountInteractor.selectedMetaAccountFlow()
            ) { screenArgs, selectedChain, utilityAsset, networkFeeResult, metaAccount ->
                val tokenSymbol = utilityAsset.token.configuration.symbol
                val tokenFiatRate = utilityAsset.token.fiatRate
                val tokenFiatSymbol = utilityAsset.token.fiatSymbol

                val networkFee = networkFeeResult.getOrNull()

                ConfirmNFTSendScreenState(
                    thumbnailImageModel = Loadable.ReadyToRender(
                        ImageModel.UrlWithFallbackOption(
                            screenArgs.token.thumbnail,
                            ImageModel.ResId(R.drawable.drawable_fearless_bird)
                        )
                    ),
                    fromInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.transaction_details_from),
                        value = metaAccount.name,
                        additionalValue = metaAccount.address(selectedChain)
                            ?.shortenAddress(ADDRESS_SHORTEN_COUNT),
                    ),
                    toInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.polkaswap_to),
                        value = screenArgs.receiver
                            .shortenAddress(ADDRESS_SHORTEN_COUNT)
                    ),
                    collectionInfoItem = screenArgs.token.collectionName.let {
                        TitleValueViewState(
                            title = resourceManager.getString(R.string.nft_collection_title),
                            value = it
                        )
                    },
                    feeInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.common_network_fee),
                        value = networkFee?.formatCryptoDetail(tokenSymbol),
                        additionalValue = networkFee?.applyFiatRate(tokenFiatRate)?.formatFiat(tokenFiatSymbol)
                    ),
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
                    it.message?.let(internalNFTRouter::openErrorsScreen)
                }
            )
        }
    }

    override fun onItemClick(code: Int) = Unit

    private companion object {
        const val ADDRESS_SHORTEN_COUNT = 5
    }
}
