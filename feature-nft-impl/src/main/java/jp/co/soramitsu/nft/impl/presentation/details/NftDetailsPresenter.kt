package jp.co.soramitsu.nft.impl.presentation.details

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.domain.utils.convertToShareMessage
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.nft.impl.presentation.CoroutinesStore
import jp.co.soramitsu.nft.navigation.NFTNavGraphRoute
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

class NftDetailsPresenter @Inject constructor(
    private val nftInteractor: NFTInteractor,
    private val coroutinesStore: CoroutinesStore,
    private val walletInteractor: WalletInteractor,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter,
    private val iconGenerator: AddressIconGenerator,
) : NftDetailsScreenInterface {

    private val screenArgsFlow = internalNFTRouter.createNavGraphRoutesFlow()
        .filterIsInstance<NFTNavGraphRoute.DetailsNFTScreen>()
        .shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    private val mutableClipboardCopyRequestFlow = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_LATEST
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val ownersAddressFlow = screenArgsFlow.mapLatest { screenArg ->
        nftInteractor.getOwnersForNFT(
            token = screenArg.token
        ).getOrNull()
    }.shareIn(coroutinesStore.uiScope, SharingStarted.Eagerly, 1)

    init {
        mutableClipboardCopyRequestFlow.onEach { text ->
            clipboardManager.addToClipboard(text)
        }.launchIn(coroutinesStore.uiScope)
    }

    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<NftDetailsScreenState> {
        return screenArgsFlow.map { it.token }.combine(ownersAddressFlow) { token, ownersAddress ->
            val ownerAddress = ownersAddress?.run {
                val shortedFirstUserOwnerAddress =
                    firstOrNull()?.shortenAddress(ADDRESS_SHORTEN_COUNT) ?: return@run null

                if (size <= 1) {
                    return@run shortedFirstUserOwnerAddress
                }

                resourceManager.getString(
                    R.string.common_and_others_placeholder,
                    shortedFirstUserOwnerAddress
                )
            }

            NftDetailsScreenState(
                name = token.title,
                isTokenUserOwned = token.isUserOwnedToken,
                imageUrl = token.thumbnail,
                description = token.description,
                collectionName = token.collectionName,
                owner = ownerAddress ?: "",
                ownerIcon = ownersAddress?.firstOrNull()?.let {
                    iconGenerator.createEthereumAddressModel(it, SIZE_MEDIUM).image
                },
                creator = token.creatorAddress?.shortenAddress(ADDRESS_SHORTEN_COUNT) ?: "",
                creatorIcon = token.creatorAddress?.let {
                    iconGenerator.createEthereumAddressModel(it, SIZE_MEDIUM).image
                },
                network = token.chainName,
                tokenType = token.tokenType ?: "",
                tokenId = token.tokenId.toString(),
                dateTime = token.date ?: "",
                price = token.price,
                priceFiat = token.price
            )
        }.stateIn(coroutineScope, SharingStarted.Lazily, NftDetailsScreenState())
    }

    override fun sendClicked() {
        val token = screenArgsFlow.replayCache.lastOrNull()?.token ?: return
        internalNFTRouter.openChooseRecipientScreen(token)
    }

    override fun shareClicked() {
        coroutinesStore.uiScope.launch {
            val address = walletInteractor.getSelectedMetaAccount().ethereumAddress?.toHexString(true)
            internalNFTRouter.shareText(generateShareMessage(address))
        }
    }

    private fun generateShareMessage(address: String?): String {
        val ownerAddress = ownersAddressFlow.replayCache.lastOrNull()?.firstOrNull() ?: ""

        return screenArgsFlow.replayCache.lastOrNull()?.token?.run {
            convertToShareMessage(
                resourceManager,
                ownerAddress,
                address
            )
        }.orEmpty()
    }

    override fun creatorClicked() {
        screenArgsFlow.replayCache.lastOrNull()?.token?.creatorAddress?.let {
            mutableClipboardCopyRequestFlow.tryEmit(it)
        }
    }

    override fun tokenIdClicked() {
        val tokenId = screenArgsFlow.replayCache.lastOrNull()?.token?.tokenId ?: return

        if (tokenId >= BigInteger.ZERO) {
            mutableClipboardCopyRequestFlow.tryEmit(tokenId.toString())
        }
    }

    override fun ownerClicked() {
        val ownerAddress = ownersAddressFlow.replayCache.lastOrNull()?.firstOrNull() ?: ""
        mutableClipboardCopyRequestFlow.tryEmit(ownerAddress)
    }

    companion object {
        const val ADDRESS_SHORTEN_COUNT = 4
    }
}
