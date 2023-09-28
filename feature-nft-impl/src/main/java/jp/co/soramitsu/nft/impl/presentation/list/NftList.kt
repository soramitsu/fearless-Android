package jp.co.soramitsu.nft.impl.presentation.list

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview


@Composable
fun NftList(state: NftScreenState.ListState, appearanceType: NftAppearanceType) {
    when (state) {
        is NftScreenState.ListState.Content -> NftList(state.items, appearanceType)
        NftScreenState.ListState.Empty -> TODO()
        NftScreenState.ListState.Loading -> TODO()
    }
}

@Composable
fun NftList(items: List<NftListItem>, appearanceType: NftAppearanceType) {
    when (appearanceType) {
        NftAppearanceType.Grid -> {
            LazyVerticalGrid(columns = GridCells.Fixed(2)){
                items(items) {
                    GridItem(it)
                }
            }
        }

        NftAppearanceType.List -> {
            LazyColumn {
                items(items) {
                    ListItem(it)
                }
            }
        }
    }
}

@Composable
private fun GridItem(item: NftListItem) {

}

@Composable
private fun ListItem(item: NftListItem) {

}

@Preview
@Composable
fun NftListPreview() {
    val state = NftScreenState.ListState.Content(emptyList())
    NftList(state, appearanceType = NftAppearanceType.Grid)
}