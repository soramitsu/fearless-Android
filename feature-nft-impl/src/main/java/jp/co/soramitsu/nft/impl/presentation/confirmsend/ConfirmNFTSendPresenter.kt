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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
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
        return screenArgsFlow.zipWithPrevious().flatMapLatest { (prevScreenArgs, currentScreenArgs) ->
            privateCreateScreenStateFlow(prevScreenArgs, currentScreenArgs).catch {
                internalNFTRouter.openErrorsScreen(
                    title = resourceManager.getString(R.string.common_error_general_title),
                    message = resourceManager.getString(R.string.common_error_network)
                )
            }
        }.stateIn(coroutineScope, SharingStarted.Lazily, ConfirmNFTSendScreenState.default)
    }

    private fun privateCreateScreenStateFlow(
        prevScreenArgs: NFTNavGraphRoute.ConfirmNFTSendScreen?,
        currentScreenArgs: NFTNavGraphRoute.ConfirmNFTSendScreen
    ): Flow<ConfirmNFTSendScreenState> {
        return flow {
            val chain = chainsRepository.getChain(currentScreenArgs.token.chainId)
            val (chainId, utilityAssetId) = chain.run { id to requireNotNull(utilityAsset?.id) }

            val isLoadingHelperFlow = isLoadingFlow.map {
                if (prevScreenArgs?.token == null || prevScreenArgs.token.tokenId == currentScreenArgs.token.tokenId) {
                    it
                } else {
                    false
                }
            }

            combine(
                accountInteractor.selectedMetaAccountFlow(),
                nftTransferInteractor.networkFeeFlow(
                    currentScreenArgs.token,
                    currentScreenArgs.receiver,
                    currentScreenArgs.isReceiverKnown
                ),
                walletInteractor.assetFlow(chainId, utilityAssetId),
                isLoadingHelperFlow
            ) { metaAccount, networkFeeResult, utilityAsset, isLoading ->
                val tokenSymbol = utilityAsset.token.configuration.symbol
                val tokenFiatRate = utilityAsset.token.fiatRate
                val tokenFiatSymbol = utilityAsset.token.fiatSymbol

                val networkFee = networkFeeResult.getOrNull()

                ConfirmNFTSendScreenState(
                    thumbnailImageModel = Loadable.ReadyToRender(
                        ImageModel.UrlWithFallbackOption(
                            currentScreenArgs.token.thumbnail,
                            ImageModel.ResId(R.drawable.drawable_fearless_bird)
                        )
                    ),
                    fromInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.transaction_details_from),
                        value = metaAccount.name,
                        additionalValue = metaAccount.address(chain)
                            ?.shortenAddress(ADDRESS_SHORTEN_COUNT),
                    ),
                    toInfoItem = TitleValueViewState(
                        title = resourceManager.getString(R.string.polkaswap_to),
                        value = currentScreenArgs.receiver
                            .shortenAddress(ADDRESS_SHORTEN_COUNT)
                    ),
                    collectionInfoItem = currentScreenArgs.token.collectionName.let {
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
            }.collect(this)
        }
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
