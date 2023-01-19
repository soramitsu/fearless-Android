package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.H3Bold
import jp.co.soramitsu.common.compose.component.H6
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.black3
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItem
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState

data class SelectedValidatorsScreenViewState(
    val groups: List<GroupViewState>,
    val canChangeValidators: Boolean
)

data class GroupViewState(
    val title: String?,
    @DrawableRes val titleIcon: Int? = null,
    val description: String? = null,
    val listState: MultiSelectListViewState<String> = MultiSelectListViewState.empty()
)

interface SelectedValidatorsInterface {
    fun onBackClick()
    fun onChangeValidatorsClick()
    fun onInfoClick(item: SelectableListItemState<String>)
}

@Composable
fun SelectedValidatorsScreen(
    state: LoadingState<SelectedValidatorsScreenViewState>,
    screenInterface: SelectedValidatorsInterface
) {
    BottomSheetScreen(modifier = Modifier.fillMaxHeight()) {
        Toolbar(
            state = ToolbarViewState(
                stringResource(id = R.string.pool_staking_selected_validators_title),
                navigationIcon = R.drawable.ic_arrow_back_24dp
            ),
            onNavigationClick = screenInterface::onBackClick
        )
        FullScreenLoading(isLoading = state is LoadingState.Loading) {
            if (state is LoadingState.Loaded) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (state.data.groups.isEmpty() || state.data.groups.any { it.listState.items.isEmpty() }) {
                        EmptyMessage(
                            modifier = Modifier.align(Alignment.Center),
                            message = R.string.staking_set_validators_message
                        )
                    } else {
                        GroupedValidators(state.data.groups, screenInterface::onInfoClick)
                    }
                    if (state.data.canChangeValidators) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 46.dp, start = 16.dp, end = 16.dp)
                        ) {
                            AccentButton(
                                text = stringResource(id = R.string.pool_staking_change_validators_button),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                onClick = screenInterface::onChangeValidatorsClick
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupedValidators(
    groups: List<GroupViewState>,
    onInfoClick: (SelectableListItemState<String>) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        groups.forEachIndexed { index, group ->
            val isLast = index == groups.size - 1

            item {
                MarginVertical(margin = 16.dp)
                ValidatorsGroupTitle(state = group)
                MarginVertical(margin = 8.dp)
            }
            items(group.listState.items) {
                SelectableListItem(
                    state = it,
                    onSelected = {},
                    onInfoClick = onInfoClick
                )
            }
            if (isLast.not()) {
                // it's divider
                item {
                    MarginVertical(margin = 16.dp)
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .background(black3)
                            .fillMaxWidth()
                            .height(1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ValidatorsGroupTitle(state: GroupViewState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        state.title?.let { title ->
            Row {
                state.titleIcon?.let {
                    Image(
                        res = it,
                        modifier = Modifier
                            .align(Alignment.CenterVertically)
                            .size(14.dp)
                    )
                    MarginHorizontal(margin = 8.dp)
                }
                H3Bold(text = title, modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        MarginVertical(margin = 8.dp)
        state.description?.let { H6(text = it, color = black2) }
    }
}

@Composable
@Preview
private fun SelectedValidatorsScreenScreenPreview() {
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
    val groups = listOf(
        GroupViewState(
            title = "Elected",
            titleIcon = R.drawable.ic_status_success_16,
            description = "Your stake is allocated to the following validators",
            listState = MultiSelectListViewState(items, items)
        )
    )
    val state = SelectedValidatorsScreenViewState(
        groups,
        true
    )
    val emptyInterface = object : SelectedValidatorsInterface {
        override fun onBackClick() = Unit
        override fun onChangeValidatorsClick() = Unit

        override fun onInfoClick(item: SelectableListItemState<String>) = Unit
    }
    FearlessTheme {
        SelectedValidatorsScreen(state = LoadingState.Loaded(state), screenInterface = emptyInterface)
    }
}
