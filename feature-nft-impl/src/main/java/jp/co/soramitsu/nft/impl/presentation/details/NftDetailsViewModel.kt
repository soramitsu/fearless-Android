package jp.co.soramitsu.nft.impl.presentation.details

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import jp.co.soramitsu.common.address.AddressIconGenerator
import jp.co.soramitsu.common.address.AddressIconGenerator.Companion.SIZE_MEDIUM
import jp.co.soramitsu.common.address.createEthereumAddressModel
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.resources.ClipboardManager
import jp.co.soramitsu.common.resources.ResourceManager
import jp.co.soramitsu.common.utils.formatting.shortenAddress
import jp.co.soramitsu.feature_nft_impl.R
import jp.co.soramitsu.nft.domain.NFTInteractor
import jp.co.soramitsu.nft.domain.models.NFTCollection
import jp.co.soramitsu.nft.impl.presentation.collection.NftCollectionViewModel.Companion.COLLECTION_CONTRACT_ADDRESS_KEY
import jp.co.soramitsu.shared_utils.extensions.toHexString
import jp.co.soramitsu.wallet.impl.domain.interfaces.WalletInteractor
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.math.BigInteger
import javax.inject.Inject

@HiltViewModel
class NftDetailsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nftInteractor: NFTInteractor,
    private val walletInteractor: WalletInteractor,
    private val clipboardManager: ClipboardManager,
    private val resourceManager: ResourceManager,
    iconGenerator: AddressIconGenerator,
) : BaseViewModel(), NftDetailsScreenInterface {

    private val colletionsContractAddress =
            requireNotNull(savedStateHandle.get<String>(COLLECTION_CONTRACT_ADDRESS_KEY)) {
                "Can't find $COLLECTION_CONTRACT_ADDRESS_KEY in arguments"
            }

    private val chainId =
            requireNotNull(savedStateHandle.get<String>(CHAIN_ID)) { "Can't find $CHAIN_ID in arguments" }

    private val tokenId =
            requireNotNull(savedStateHandle.get<String>(TOKEN_ID)) { "Can't find $TOKEN_ID in arguments" }

    private var nft: NFTCollection.NFT.Full? = null
    private var ownerAddress: String? = null

    private val _state = MutableStateFlow(NftDetailsScreenState())
    val state = _state.asStateFlow()

    private val _shareState = MutableSharedFlow<String>()
    val shareState = _shareState.asSharedFlow()

    init {
        viewModelScope.launch {
            nft = nftInteractor.getNFTDetails(chainId, colletionsContractAddress, tokenId).getOrNull()
            nft?.let {
                ownerAddress = nftInteractor.getOwnersForNFT(it).getOrNull()?.firstOrNull()

                _state.value = NftDetailsScreenState(
                    name = it.title ?: "",
                    imageUrl = it.thumbnail,
                    description = it.description ?: "",
                    collectionName = it.collectionName ?: "",
                    owner = ownerAddress?.shortenAddress(ADDRESS_SHORTEN_COUNT) ?: "",
                    ownerIcon = ownerAddress?.let {
                        iconGenerator.createEthereumAddressModel(it, SIZE_MEDIUM).image
                    },
                    creator = it.creatorAddress?.shortenAddress(ADDRESS_SHORTEN_COUNT) ?: "",
                    creatorIcon = it.creatorAddress?.let {
                        iconGenerator.createEthereumAddressModel(it, SIZE_MEDIUM).image
                    },
                    network = it.chainName,
                    tokenType = it.tokenType ?: "",
                    tokenId = it.tokenId?.let { BigInteger(it.removePrefix("0x"), HEX_RADIX).toString() } ?: "",
                    dateTime = it.date ?: "",
                    price = it.price,
                    priceFiat = it.price
                )
            }
        }
    }

    override fun close() {
    }

    override fun shareClicked() {
        viewModelScope.launch {
            val address = walletInteractor.getSelectedMetaAccount().ethereumAddress?.toHexString(true)
            _shareState.emit(generateShareMessage(address))
        }
    }

    private fun generateShareMessage(address: String?) = buildString {
        nft?.run {
            appendLine(thumbnail)
            ownerAddress?.let {
                appendLine("${resourceManager.getString(R.string.nft_owner_title)}: $ownerAddress")
            }
            appendLine("${resourceManager.getString(R.string.common_network)}: $chainName")
            appendLine("${resourceManager.getString(R.string.nft_creator_title)}: $creatorAddress")
            appendLine("${resourceManager.getString(R.string.nft_collection_title)}: $collectionName")
            appendLine("${resourceManager.getString(R.string.nft_token_type_title)}: $tokenType")
            appendLine("${resourceManager.getString(R.string.nft_tokenid_title)}: ${state.value.tokenId}")
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
        nft?.creatorAddress?.let {
            copyToClipboardWithMessage(it)
        }
    }

    override fun tokenIdClicked() {
        if (state.value.tokenId.isNotEmpty()) {
            copyToClipboardWithMessage(state.value.tokenId)
        }
    }

    override fun ownerClicked() {
        ownerAddress?.let {
            copyToClipboardWithMessage(it)
        }
    }

    private fun copyToClipboardWithMessage(text: String) {
        clipboardManager.addToClipboard(text)
        val message = resourceManager.getString(R.string.common_copied)
        showMessage(message)
    }

    companion object {
        const val CHAIN_ID = "chainId"
        const val TOKEN_ID = "tokenId"
        const val ADDRESS_SHORTEN_COUNT = 4
        const val HEX_RADIX = 16
    }
}
