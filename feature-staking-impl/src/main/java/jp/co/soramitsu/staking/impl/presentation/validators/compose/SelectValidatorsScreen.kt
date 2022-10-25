package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItem
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState

data class SelectValidatorsScreenViewState(
    val toolbarTitle: String,
    val listState: MultiSelectListItemViewState<String>
)

data class MultiSelectListItemViewState<ItemIdType>(
    val items: List<SelectableListItemState<ItemIdType>>,
    val selectedItems: List<SelectableListItemState<ItemIdType>>
)

@Composable
fun SelectValidatorsScreen(
    state: SelectValidatorsScreenViewState,
    onNavigationClick: () -> Unit,
    onSelected: (SelectableListItemState<String>) -> Unit,
    onInfoClick: (SelectableListItemState<String>) -> Unit,
    onChooseClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    BottomSheetScreen {
        Toolbar(
            state = ToolbarViewState(
                title = state.toolbarTitle,
                navigationIcon = R.drawable.ic_arrow_back_24dp,
                menuItems = listOf(
                    MenuIconItem(
                        R.drawable.ic_dots_horizontal_24,
                        onClick = onOptionsClick
                    )
                )
            ),
            onNavigationClick = onNavigationClick
        )
        MarginVertical(margin = 8.dp)
        LazyColumn(modifier = Modifier.weight(1f)) {
            val selectedIds = state.listState.selectedItems.map { it.id }
            val items = state.listState.items.map { it.copy(isSelected = it.id in selectedIds) }
            items(items = items) { pool ->
                SelectableListItem(
                    state = pool,
                    onSelected = onSelected,
                    onInfoClick = { onInfoClick(pool) }
                )
            }
        }
        AccentButton(
            text = stringResource(id = R.string.pool_staking_choosepool_button_title),
            onClick = onChooseClick,
            enabled = state.listState.selectedItems.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
        )
        MarginVertical(margin = 16.dp)
    }
}

@Preview
@Composable
private fun SelectValidatorsScreenPreview() {
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
            id = "1",
            title = "Polkadot js plus",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = true
        ),
        SelectableListItemState(
            id = "2",
            title = "POOL NUMBER ONE",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = false,
            additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
        )
    )
    val state = MultiSelectListItemViewState(
        items = items,
        selectedItems = listOf(items.first())
    )
    FearlessTheme {
        Column {
            SelectValidatorsScreen(
                SelectValidatorsScreenViewState("Select suggested", state),
                {},
                {},
                {},
                {},
                {}
            )
        }
    }
}
