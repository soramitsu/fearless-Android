package jp.co.soramitsu.nft.impl.presentation

import jp.co.soramitsu.nft.impl.presentation.collection.NftCollectionViewModel
import jp.co.soramitsu.nft.impl.presentation.details.NftDetailsViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

object NftNavigator {

    const val COLLECTION_DETAILS_ROUTE = "collectionDetails/{${NftCollectionViewModel.COLLECTION_CHAIN_ID}}/{${NftCollectionViewModel.COLLECTION_CONTRACT_ADDRESS_KEY}}"
    const val NFT_DETAILS_ROUTE_ROOT = "nftDetails"
    const val NFT_DETAILS_ROUTE = "$NFT_DETAILS_ROUTE_ROOT/{${NftCollectionViewModel.COLLECTION_CONTRACT_ADDRESS_KEY}}/{${NftDetailsViewModel.CHAIN_ID}}/{${NftDetailsViewModel.TOKEN_ID}}"

    private val _sharedFlow =
        MutableSharedFlow<String>(extraBufferCapacity = 1)
    val sharedFlow = _sharedFlow.asSharedFlow()

    fun navigateTo(destination: String) {
        _sharedFlow.tryEmit(destination)
    }
}
