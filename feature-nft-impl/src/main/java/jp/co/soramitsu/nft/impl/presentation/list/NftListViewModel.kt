package jp.co.soramitsu.nft.impl.presentation.list

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.nft.impl.domain.NftInteractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class NftListViewModel @Inject constructor(private val nftInteractor: NftInteractor) :
    BaseViewModel(), NftListScreenInterface {

    val state: MutableStateFlow<NftScreenState> =
        MutableStateFlow(NftScreenState(false, NftScreenState.ListState.Loading))

    init {
        viewModelScope.launch {
            val collections = nftInteractor.getNfts()
            val models = collections.map {
                NftCollectionListItem(
                    id = it.name,
                    image = it.image,
                    chain = it.chainName,
                    title = it.name,
                    it.nfts.size,
                    collectionSize = it.collectionSize
                )
            }.sortedBy { it.title }
            state.value = state.value.copy(listState = NftScreenState.ListState.Content(models))
        }
    }

    override fun filtersClicked() {
    }
}