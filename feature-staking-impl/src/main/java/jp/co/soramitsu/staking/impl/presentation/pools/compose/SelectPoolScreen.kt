package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetLayout
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.ListDialog
import jp.co.soramitsu.common.compose.component.ListDialogState
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.feature_staking_impl.R
import kotlinx.coroutines.launch

enum class PoolSorting : ListDialogState.Item {
    TotalStake {
        override val titleRes = R.string.staking_validator_total_stake
    },
    NumberOfMembers {
        override val titleRes = R.string.pool_staking_number_of_members
    }
}

data class SingleSelectListItemViewState<ItemIdType>(
    val items: List<SelectableListItemState<ItemIdType>>,
    val selectedItem: SelectableListItemState<ItemIdType>?
)

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun SelectPoolScreen(
    state: SingleSelectListItemViewState<Int>,
    onNavigationClick: () -> Unit,
    onPoolSelected: (SelectableListItemState<Int>) -> Unit,
    onInfoClick: (SelectableListItemState<Int>) -> Unit,
    onChooseClick: () -> Unit,
    onSortingSelected: (PoolSorting) -> Unit
) {
    val scope = rememberCoroutineScope()

    BottomSheetLayout(
        sheetContent = { sheetState ->
            ListDialog(
                state = ListDialogState(R.string.common_sort_by, PoolSorting.values().toList()),
                onSelected = {
                    scope.launch { sheetState.hide() }
                    onSortingSelected(it)
                }
            )
        },
        content = { sheetState ->
            BottomSheetScreen {
                Toolbar(
                    state = ToolbarViewState(
                        title = stringResource(R.string.pool_staking_choosepool_title),
                        navigationIcon = R.drawable.ic_arrow_back_24dp,
                        menuItems = listOf(
                            MenuIconItem(
                                R.drawable.ic_dots_horizontal_24,
                                onClick = {
                                    scope.launch { sheetState.show() }
                                }
                            )
                        )
                    ),
                    onNavigationClick = onNavigationClick
                )
                MarginVertical(margin = 8.dp)
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(items = state.items.map { it.copy(isSelected = it.id == state.selectedItem?.id) }) { pool ->
                        SelectableListItem(
                            state = pool,
                            onSelected = onPoolSelected,
                            onInfoClick = { onInfoClick(pool) }
                        )
                    }
                }
                AccentButton(
                    text = stringResource(id = R.string.pool_staking_choosepool_button_title),
                    onClick = onChooseClick,
                    enabled = state.selectedItem != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .padding(horizontal = 16.dp)
                )
                MarginVertical(margin = 16.dp)
            }
        }
    )
}

@Preview
@Composable
private fun SelectPoolScreenPreview() {
    val stakedText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("${stringResource(R.string.pool_staking_choosepool_staked_title)} ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append("20k KSM")
        }
    }
    val subtitle = stringResource(R.string.pool_staking_choosepool_members_count_title, 15)

    val items = listOf(
        SelectableListItemState(
            id = 1,
            title = "Polkadot js plus",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = true
        ),
        SelectableListItemState(
            id = 2,
            title = "POOL NUMBER ONE",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = false
        )
    )
    val state = SingleSelectListItemViewState(
        items = items,
        selectedItem = items.first()
    )
    FearlessTheme {
        Column {
            SelectPoolScreen(state, {}, {}, {}, {}, {})
        }
    }
}
