package jp.co.soramitsu.nft.impl.presentation.list

import android.util.Log
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.base.BaseViewModel
import jp.co.soramitsu.nft.impl.domain.NftInteractor
import jp.co.soramitsu.nft.impl.presentation.NftRouter
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilter
import jp.co.soramitsu.nft.impl.presentation.filters.NftFilterModel
import jp.co.soramitsu.nft.impl.presentation.filters.NftFiltersFragment
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn

// todo
//  1. Add loader to the list when reloading
//  2.
@HiltViewModel
class NftListViewModel @Inject constructor(
    private val nftInteractor: NftInteractor,
    private val nftRouter: NftRouter
) : BaseViewModel(), NftListScreenInterface {

    private val defaultFiltersState =
        NftFilterModel(mapOf(NftFilter.Spam to true, NftFilter.Airdrops to false))

    private val filtersFlow = nftRouter.nftFiltersResultFlow(NftFiltersFragment.KEY_RESULT)
        .stateIn(viewModelScope, SharingStarted.Eagerly, defaultFiltersState)

    private val swipeToRefreshTrigger = MutableSharedFlow<Unit>().onStart { emit(Unit) }

    val state = combine(filtersFlow, swipeToRefreshTrigger) { filtersModel, _ ->
        val filters = filtersModel.items.entries.filter { it.value }.map { it.key }
        val collections = nftInteractor.getNfts(filters)
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
        val hasAnyFiltersChecked = filters.isNotEmpty()
        NftScreenState(hasAnyFiltersChecked, NftScreenState.ListState.Content(models))
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        NftScreenState(true, NftScreenState.ListState.Loading)
    )

    override fun filtersClicked() {
        nftRouter.openNftFilters(filtersFlow.value)
    }
}