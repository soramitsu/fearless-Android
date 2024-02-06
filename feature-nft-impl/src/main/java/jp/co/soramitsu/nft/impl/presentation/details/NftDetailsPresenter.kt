package jp.co.soramitsu.nft.impl.presentation.details

import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.impl.navigation.Destination
import jp.co.soramitsu.nft.impl.navigation.InternalNFTRouter
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

class NftDetailsPresenter @Inject constructor(
    private val nftInteractor: NFTInteractor,
    private val walletInteractor: WalletInteractor,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    private val internalNFTRouter: InternalNFTRouter,
    private val iconGenerator: AddressIconGenerator,
) : NftDetailsScreenInterface {

    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val screenArgsFlow = internalNFTRouter.destinationsFlow
        .filterIsInstance<Destination.NestedNavGraphRoute.DetailsNFTScreen>()
        .shareIn(coroutineScope, SharingStarted.Eagerly, 1)

    private var ownerAddress: String? = null

    init {
        coroutineScope.launch {
            ownerAddress = nftInteractor.getOwnersForNFT(
                token = screenArgsFlow.first().token
            ).getOrNull()?.firstOrNull()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun createScreenStateFlow(): Flow<NftDetailsScreenState> {
        return screenArgsFlow.map { it.token }.transformLatest { token ->
            NftDetailsScreenState(
                name = token.title ?: "",
                imageUrl = token.thumbnail,
                description = token.description ?: "",
                collectionName = token.collectionName ?: "",
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
                tokenId = token.tokenId?.let { BigInteger(it.removePrefix("0x"), HEX_RADIX).toString() } ?: "",
                dateTime = token.date ?: "",
                price = token.price,
                priceFiat = token.price
            ).also { emit(it) }
        }
    }

    override fun sendClicked() {
        val token = screenArgsFlow.replayCache.lastOrNull()?.token ?: return
        internalNFTRouter.openChooseRecipientScreen(token)
    }

    override fun shareClicked() {
        coroutineScope.launch {
            val address = walletInteractor.getSelectedMetaAccount().ethereumAddress?.toHexString(true)
            internalNFTRouter.shareText(generateShareMessage(address))
        }
    }

    private fun generateShareMessage(address: String?) = buildString {
        val token = screenArgsFlow.replayCache.lastOrNull()?.token

        token?.run {
            appendLine(thumbnail)
            ownerAddress?.let {
                appendLine("${resourceManager.getString(R.string.nft_owner_title)}: $ownerAddress")
            }
            appendLine("${resourceManager.getString(R.string.common_network)}: $chainName")
            appendLine("${resourceManager.getString(R.string.nft_creator_title)}: $creatorAddress")
            appendLine("${resourceManager.getString(R.string.nft_collection_title)}: $collectionName")
            appendLine("${resourceManager.getString(R.string.nft_token_type_title)}: $tokenType")
            appendLine("${resourceManager.getString(R.string.nft_tokenid_title)}: $tokenId")
            address?.let {
                val string = resourceManager.getString(R.string.wallet_receive_share_message).format(
                    "Ethereum",
                    it
                )
                appendLine(string)
            }
        }
    }

    override fun creatorClicked() {
        screenArgsFlow.replayCache.lastOrNull()?.token?.creatorAddress?.let {
            copyToClipboardWithMessage(it)
        }
    }

    override fun tokenIdClicked() {
        val token = screenArgsFlow.replayCache.lastOrNull()?.token

        if (!token?.tokenId.isNullOrBlank()) {
            copyToClipboardWithMessage(token?.tokenId.orEmpty())
        }
    }

    override fun ownerClicked() {
        ownerAddress?.let {
            copyToClipboardWithMessage(it)
        }
    }

    private fun copyToClipboardWithMessage(text: String) {
        coroutineScope.launch {
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
