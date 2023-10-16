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
        val items = listOf(
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            ),
            NftListItem(
                "1",
                "https://public.nftstatic.com/static/nft/res/nft-cex/S3/1681135249863_5vfn4v8dfmche8vzqlhcotiwj2z8vn2g.png",
                "BNB Chain",
                "BORED MARIO v2 #120",
                1,
                290
            )
        )
        val statess = NftScreenState.ListState.Content(items)
        val previewState =
            NftScreenState(true, listState = statess)
        viewModelScope.launch { nftInteractor.getNfts() }
        state.value = previewState
    }

    override fun filtersClicked() {
        TODO("Not yet implemented")
    }
}