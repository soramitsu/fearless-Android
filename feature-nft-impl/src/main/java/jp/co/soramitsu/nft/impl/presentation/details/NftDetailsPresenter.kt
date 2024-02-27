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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
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

    private var ownerAddress: String? = null

    init {
        coroutinesStore.uiScope.launch {
            ownerAddress = screenArgsFlow.firstOrNull()?.token?.let { token ->
                if (!token.isUserOwnedToken) {
                    return@let null
                }

                nftInteractor.getOwnersForNFT(
                    token = screenArgsFlow.first().token
                ).getOrNull()?.firstOrNull()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(coroutineScope: CoroutineScope): StateFlow<NftDetailsScreenState> {
        return screenArgsFlow.map { it.token }.transformLatest { token ->
            NftDetailsScreenState(
                name = token.title,
                isTokenUserOwned = token.isUserOwnedToken,
                imageUrl = token.thumbnail,
                description = token.description,
                collectionName = token.collectionName,
                owner = ownerAddress?.shortenAddress(ADDRESS_SHORTEN_COUNT) ?: "",
                ownerIcon = ownerAddress?.let {
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
            ).also { emit(it) }
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
            copyToClipboardWithMessage(it)
        }
    }

    override fun tokenIdClicked() {
        val tokenId = screenArgsFlow.replayCache.lastOrNull()?.token?.tokenId ?: return

        if (tokenId < BigInteger.ZERO) {
            copyToClipboardWithMessage(tokenId.toString())
        }
    }

    override fun ownerClicked() {
        ownerAddress?.let {
            copyToClipboardWithMessage(it)
        }
    }

    private fun copyToClipboardWithMessage(text: String) {
        coroutinesStore.uiScope.launch {
            clipboardManager.addToClipboard(text)
            val message = resourceManager.getString(R.string.common_copied)
            internalNFTRouter.showToast(message)
        }
    }

    companion object {
        const val ADDRESS_SHORTEN_COUNT = 4
        const val HEX_RADIX = 16
    }
}
