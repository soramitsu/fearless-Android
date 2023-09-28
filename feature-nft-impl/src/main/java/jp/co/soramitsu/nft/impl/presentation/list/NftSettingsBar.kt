package jp.co.soramitsu.nft.impl.presentation.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.theme.colorAccentDark
import jp.co.soramitsu.common.compose.theme.white
import jp.co.soramitsu.common.compose.theme.white08
import jp.co.soramitsu.common.compose.theme.white30
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_nft_impl.R


enum class NftAppearanceType {
    Grid, List
}

data class NftSettingsState(
    val collectionAppearanceType: NftAppearanceType,
    val filtersSelected: Boolean
)

@Composable
internal fun NftSettingsBar(
    state: NftSettingsState,
    modifier: Modifier = Modifier,
    appearanceSelected: (NftAppearanceType) -> Unit,
    filtersClicked: () -> Unit
) {
    val cellsColor =
        if (state.collectionAppearanceType == NftAppearanceType.Grid) white else white30
    val listColor = if (state.collectionAppearanceType == NftAppearanceType.List) white else white30

    Row(modifier = modifier) {
        Image(
            res = R.drawable.ic_cells,
            tint = cellsColor,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clickableWithNoIndication { appearanceSelected(NftAppearanceType.Grid) }
        )
        MarginHorizontal(margin = 8.dp)
        Image(
            res = R.drawable.ic_list,
            tint = listColor,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .clickableWithNoIndication { appearanceSelected(NftAppearanceType.List) }
        )
        MarginHorizontal(margin = 13.dp)
        Divider(
            modifier = Modifier
                .size(width = 1.dp, height = 28.dp),
            color = white08
        )
        MarginHorizontal(margin = 13.dp)
        Box(
            modifier = Modifier
                .width(27.dp)
                .align(Alignment.CenterVertically)
                .clickableWithNoIndication(filtersClicked)
        ) {
            Image(
                res = R.drawable.ic_sort,
                modifier = Modifier
                    .height(20.dp)
                    .align(Alignment.Center)
            )
            if (state.filtersSelected) {
                Box(
                    modifier = modifier
                        .size(5.dp)
                        .background(colorAccentDark, shape = CircleShape)
                        .align(Alignment.TopEnd)
                )
            }
        }
    }
}