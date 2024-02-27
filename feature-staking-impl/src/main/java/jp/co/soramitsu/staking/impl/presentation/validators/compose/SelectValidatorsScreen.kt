package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AccentButton
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.CapsTitle2
import jp.co.soramitsu.common.compose.component.CorneredInput
import jp.co.soramitsu.common.compose.component.EmptyMessage
import jp.co.soramitsu.common.compose.component.FullScreenLoading
import jp.co.soramitsu.common.compose.component.H4Bold
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.MenuIconItem
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItem
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState

data class SelectValidatorsScreenViewState(
    val toolbarTitle: String,
    val isCustom: Boolean,
    val searchQuery: String = "",
    val listState: LoadingState<ValidatorsListViewState>
)

data class ValidatorsListViewState(
    val segments: List<ListSegmentState>,
    val selectedItems: List<SelectableListItemState<String>>
) {
    val isEmpty: Boolean
        get() {
            return segments.isEmpty() || segments.all { it.items.isEmpty() }
        }
}

data class ListSegmentState(
    val title: String? = null,
    @DrawableRes val iconRes: Int? = null,
    val sortingValue: String? = null,
    val description: String? = null,
    val items: List<SelectableListItemState<String>>
)

data class MultiSelectListViewState<ItemIdType>(
    val items: List<SelectableListItemState<ItemIdType>>,
    val selectedItems: List<SelectableListItemState<ItemIdType>>
) {
    companion object {
        fun <T> empty() = MultiSelectListViewState<T>(emptyList(), emptyList())
    }

    val isEmpty = items.isEmpty()
}

interface SelectValidatorsScreenInterface {
    fun onNavigationClick()
    fun onSelected(item: SelectableListItemState<String>)
    fun onInfoClick(item: SelectableListItemState<String>)
    fun onChooseClick()
    fun onOptionsClick()
    fun onSearchQueryInput(query: String)
}

@Composable
fun SelectValidatorsScreen(
    state: SelectValidatorsScreenViewState,
    callbacks: SelectValidatorsScreenInterface
) {
    BottomSheetScreen {
        val toolbarOptions = if (state.isCustom) {
            listOf(
                MenuIconItem(
                    R.drawable.ic_dots_horizontal_24,
                    onClick = callbacks::onOptionsClick
                )
            )
        } else {
            emptyList()
        }
        Toolbar(
            state = ToolbarViewState(
                title = state.toolbarTitle,
                navigationIcon = R.drawable.ic_arrow_back_24dp,
                menuItems = toolbarOptions
            ),
            onNavigationClick = callbacks::onNavigationClick
        )
        MarginVertical(margin = 8.dp)
        FullScreenLoading(isLoading = state.listState is LoadingState.Loading) {
            Column {
                if (state.isCustom) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        CorneredInput(
                            state = state.searchQuery,
                            onInput = callbacks::onSearchQueryInput
                        )
                    }
                }
                when {
                    state.listState is LoadingState.Loaded && state.listState.data.isEmpty -> {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .fillMaxSize()
                        ) {
                            EmptyMessage(
                                message = R.string.validators_list_empty_message,
                                modifier = Modifier.align(BiasAlignment(0f, -0.3f))
                            )
                        }
                    }

                    state.listState is LoadingState.Loaded && state.listState.data.isEmpty.not() -> {
                        ValidatorsList(
                            modifier = Modifier.weight(1f),
                            listState = state.listState.data,
                            onSelected = callbacks::onSelected,
                            onInfoClick = callbacks::onInfoClick
                        )
                        AccentButton(
                            text = stringResource(id = R.string.pool_staking_choosepool_button_title),
                            onClick = callbacks::onChooseClick,
                            enabled = state.listState.data.selectedItems.isNotEmpty(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 16.dp)
                        )
                        MarginVertical(margin = 16.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun ValidatorsList(
    modifier: Modifier = Modifier,
    listState: MultiSelectListViewState<String>,
    paddingValues: PaddingValues = PaddingValues(),
    onSelected: (SelectableListItemState<String>) -> Unit,
    onInfoClick: (SelectableListItemState<String>) -> Unit
) {
    LazyColumn(modifier = modifier, contentPadding = paddingValues) {
        val selectedIds = listState.selectedItems.map { it.id }
        val items = listState.items.map { it.copy(isSelected = it.id in selectedIds) }
        items(items = items) { pool ->
            SelectableListItem(
                state = pool,
                onSelected = onSelected,
                onInfoClick = onInfoClick
            )
        }
    }
}

@Composable
fun ValidatorsList(
    modifier: Modifier = Modifier,
    listState: ValidatorsListViewState,
    paddingValues: PaddingValues = PaddingValues(),
    onSelected: (SelectableListItemState<String>) -> Unit,
    onInfoClick: (SelectableListItemState<String>) -> Unit
) {
    LazyColumn(modifier = modifier, contentPadding = paddingValues) {
        val selectedIds = listState.selectedItems.map { it.id }
        val items = listState.segments.map { segment ->
            segment.copy(items = segment.items.map { it.copy(isSelected = it.id in selectedIds) })
        }
        items.forEach { segment ->
            item { SegmentHeader(segment) }
            items(items = segment.items) { item ->
                SelectableListItem(
                    state = item,
                    onSelected = onSelected,
                    onInfoClick = onInfoClick
                )
            }

        }
    }
}

@Composable
fun SegmentHeader(state: ListSegmentState) {
    Column {
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            state.iconRes?.let {
                Image(
                    res = it,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            MarginHorizontal(margin = 8.dp)
            state.title?.let {
                H4Bold(
                    text = it,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
            Spacer(
                modifier = Modifier
                    .height(1.dp)
                    .weight(1f)
            )
            state.sortingValue?.let {
                CapsTitle2(
                    text = it,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
            }
        }
        state.description?.let {
            MarginVertical(margin = 16.dp)
            B2(text = it)
        }
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
    val comissionText = buildAnnotatedString {
        withStyle(style = SpanStyle(color = black1)) {
            append("commission ")
        }
        withStyle(style = SpanStyle(color = greenText)) {
            append("5%")
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
        ),
        SelectableListItemState(
            id = "3",
            title = "POOL NUMBER 3",
            subtitle = subtitle,
            caption = stakedText,
            isSelected = false,
            additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
        )
    )
    val itemsNotElected = listOf(
        SelectableListItemState(
            id = "5",
            title = "Validator",
            caption = comissionText,
            isSelected = true
        ),
        SelectableListItemState(
            id = "28",
            title = "0x7458451r3ufb2749g6482h3f45g4t45gt54g",
            caption = comissionText,
            isSelected = false,
        ),
        SelectableListItemState(
            id = "32",
            title = "POOL NUMBER 3",
            caption = comissionText,
            isSelected = false,
            additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
        )
    )

    val listState = ValidatorsListViewState(
        listOf(
            ListSegmentState(
                title = "Elected",
                iconRes = R.drawable.ic_elected_validator,
                sortingValue = "apy",
                items = items
            ),
            ListSegmentState(
                title = "Not Elected",
                iconRes = R.drawable.ic_validator_waiting,
                items = itemsNotElected
            )
        ),
        listOf(items.first(), itemsNotElected.last())
    )

    val callbacks = object : SelectValidatorsScreenInterface {
        override fun onNavigationClick() = Unit
        override fun onSelected(item: SelectableListItemState<String>) = Unit
        override fun onInfoClick(item: SelectableListItemState<String>) = Unit
        override fun onChooseClick() = Unit
        override fun onOptionsClick() = Unit
        override fun onSearchQueryInput(query: String) = Unit
    }

    FearlessTheme {
        Column {
            SelectValidatorsScreen(
                state = SelectValidatorsScreenViewState(
                    "Select suggested",
                    true,
                    listState = LoadingState.Loaded(listState)
                ),
                callbacks
            )
        }
    }
}
