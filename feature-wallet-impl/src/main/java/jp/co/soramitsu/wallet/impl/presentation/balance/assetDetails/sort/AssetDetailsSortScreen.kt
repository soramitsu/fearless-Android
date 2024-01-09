package jp.co.soramitsu.wallet.impl.presentation.balance.assetDetails.sort

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.black4
import jp.co.soramitsu.common.compose.theme.colorAccent
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_wallet_impl.R

@Composable
fun AssetDetailsSortContent(
    state: AssetDetailsSortState,
    callback: AssetDetailsSortCallback
) {
    val items = remember(state.items.size, state.selectedSorting) {
        SnapshotStateList<AssetDetailsSortState.Sorting>().apply {
            addAll(state.items)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight()
    ) {
        state.toolbarState?.let {
            Toolbar(
                state = it,
                onNavigationClick = callback::onNavigationClose
            )
        }

        for (item in items) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickableWithNoIndication { callback.onSortingSelected(item) },
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(id = item.nameResId),
                    style = MaterialTheme.customTypography.header4,
                )

                if (item == state.selectedSorting) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_selected),
                        tint = colorAccent,
                        contentDescription = null
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun AssetDetailsSortContentPreview() {
    val items = listOf(
        AssetDetailsSortState.Sorting(R.string.common_assets_filters_fiat),
        AssetDetailsSortState.Sorting(R.string.common_assets_filters_name),
        AssetDetailsSortState.Sorting(R.string.common_assets_filters_popularity)
    )

    Column(
        Modifier.background(black4)
    ) {
        AssetDetailsSortContent(
            state = AssetDetailsSortViewState(
                toolbarState = ToolbarViewState(
                    "Sort by",
                    null,
                    listOf(
                        MenuIconItem(icon = R.drawable.ic_cross_24, {})
                    )
                ),
                items = items,
                selectedSorting = items[0]
            ),
            callback = emptyAssetDetailsSortState()
        )
    }
}

private fun emptyAssetDetailsSortState() = object : AssetDetailsSortCallback {
    override fun onNavigationClose() {
        /* DO NOTHING */
    }

    override fun onSortingSelected(sorting: AssetDetailsSortState.Sorting) {
        /* DO NOTHING */
    }
}