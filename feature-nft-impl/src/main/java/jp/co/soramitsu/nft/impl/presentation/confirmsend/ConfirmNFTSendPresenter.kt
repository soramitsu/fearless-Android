package jp.co.soramitsu.nft.impl.presentation.confirmsend

import jp.co.soramitsu.account.api.domain.interfaces.AccountInteractor
import jp.co.soramitsu.account.api.domain.model.address
import jp.co.soramitsu.common.base.errors.TitledException
import jp.co.soramitsu.common.base.errors.ValidationException
import jp.co.soramitsu.common.compose.component.ButtonViewState
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.models.ImageModel
import jp.co.soramitsu.common.compose.models.Loadable
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.applyFiatRate
import jp.co.soramitsu.common.utils.combine
import jp.co.soramitsu.common.utils.formatCryptoDetail
import jp.co.soramitsu.common.utils.formatFiat
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.common.utils.requireValue
import jp.co.soramitsu.common.utils.zipWithPrevious
import jp.co.soramitsu.core.utils.utilityAsset
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTTransferInteractor
import jp.co.soramitsu.nft.impl.domain.usecase.transfer.ValidateNFTTransferUseCase
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendCallback
import jp.co.soramitsu.nft.impl.presentation.confirmsend.contract.ConfirmNFTSendScreenState
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.runtime.multiNetwork.chain.ChainsRepository
import jp.co.soramitsu.wallet.api.domain.fromValidationResult
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject

class ConfirmNFTSendPresenter @Inject constructor(
    private val accountInteractor: AccountInteractor,
    private val resourceManager: ResourceManager,
    private val coroutinesStore: CoroutinesStore,
    private val walletInteractor: WalletInteractor,
    private val chainsRepository: ChainsRepository,
    private val nftTransferInteractor: NFTTransferInteractor,
    private val validateNFTTransferUseCase: ValidateNFTTransferUseCase,
    private val internalNFTRouter: InternalNFTRouter
) : ConfirmNFTSendCallback {

    private val screenArgsFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NFTNavGraphRoute.ConfirmNFTSendScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val isLoadingFlow = MutableStateFlow(false)

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<ConfirmNFTSendScreenState> {
        return channelFlow {
            val networkFeeHelperFlow = screenArgsFlow.flatMapLatest { destinationArgs ->
                nftTransferInteractor.networkFeeFlow(
                    destinationArgs.token,
                    destinationArgs.receiver,
                    destinationArgs.isReceiverKnown
                )
            }.onEach { result ->
                result.onFailure { throwable ->
                    throwable.message?.let {
                        internalNFTRouter.openErrorsScreen(message = it)
                    }
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

            val isLoadingHelperFlow =
                screenArgsFlow.zipWithPrevious().flatMapLatest { (prevArgs, currentArgs) ->
                    isLoadingFlow.map {
                        if (prevArgs?.token == null || prevArgs.token.tokenId == currentArgs.token.tokenId) {
                            it
                        } else {
                            false
                        }
                    }
                }

            combine(
                screenArgsFlow,
                tokenChainFlow,
                utilityAssetFlow,
                networkFeeHelperFlow,
                accountInteractor.selectedMetaAccountFlow(),
                isLoadingHelperFlow
            ) { screenArgs, selectedChain, utilityAsset, networkFeeResult, metaAccount, isLoading ->
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
                        enabled = networkFeeResult.isSuccess && !isLoading
                    ),
                    isLoading = isLoading
                )
            }.onEach {
                send(it)
            }.launchIn(this)
        }.stateIn(coroutineScope, SharingStarted.Lazily, ConfirmNFTSendScreenState.default)
    }

    override fun onConfirmClick() {
        val destination = screenArgsFlow.replayCache.lastOrNull() ?: return

        isLoadingFlow.value = true

        coroutinesStore.uiScope.launch {
            val chain = chainsRepository.getChain(destination.token.chainId)

            val utilityAssetId = chain.utilityAsset?.id ?: return@launch
            val utilityAsset = walletInteractor.getCurrentAsset(chain.id, utilityAssetId)

            val selectedAccountAddress =
                accountInteractor.selectedMetaAccount().address(chain) ?: return@launch

            val tokenBalance = nftTransferInteractor.balance(destination.token).getOrElse { BigInteger.ZERO }

            val fee = nftTransferInteractor.networkFeeFlow(
                destination.token,
                destination.receiver,
                destination.isReceiverKnown
            ).first().getOrElse { BigDecimal.ZERO }

            val validationProcessResult = validateNFTTransferUseCase(
                chain = chain,
                recipient = destination.receiver,
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

            nftTransferInteractor.send(
                token = destination.token,
                receiver = destination.receiver,
                canReceiverAcceptToken = destination.isReceiverKnown
            ).fold(
                onSuccess = {
                    internalNFTRouter.openSuccessScreen(it, destination.token.chainId)
                },
                onFailure = {
                    it.message?.let { internalNFTRouter.openErrorsScreen(message = it) }
                }
            )
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

    override fun onItemClick(code: Int) = Unit

    private companion object {
        const val ADDRESS_SHORTEN_COUNT = 5
    }
}
