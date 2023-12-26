package jp.co.soramitsu.nft.impl.presentation.collection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.common.utils.flowOf
import jp.co.soramitsu.nft.impl.domain.NftInteractor
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class NftCollectionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val nftInteractor: NftInteractor
) : BaseViewModel(), NftCollectionScreenInterface {

    companion object {
        const val COLLECTION_CONTRACT_ADDRESS_KEY = "contractAddress"
    }

    private val contractAddress =
        savedStateHandle.get<String>(COLLECTION_CONTRACT_ADDRESS_KEY) ?: throw IllegalStateException("Can't find $COLLECTION_CONTRACT_ADDRESS_KEY in arguments")

    private val collection = nftInteractor.getCollection(contractAddress)

    private val defaultScreenState = NftCollectionScreenState(
        collectionName = collection.name,
        collectionImageUrl = collection.image,
        collectionDescription = collection.description,
        myNFTs = collection.nfts.map {
            NftItem(
                it.thumbnail,
                it.title,
                it.description,
                it.hashCode()
            )
        },
        availableNFTs = emptyList()
    )

    val state: StateFlow<NftCollectionScreenState> = flowOf {
        defaultScreenState
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        defaultScreenState
    )

    override fun close() {
    }

    override fun onItemClick(item: NftItem) {
    }

    override fun onSendClick(item: NftItem) {
    }

    override fun onShareClick(item: NftItem) {
    }
}