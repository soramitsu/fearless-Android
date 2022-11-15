package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.presentation.LoadingState
import jp.co.soramitsu.feature_staking_impl.R
import jp.co.soramitsu.staking.impl.presentation.pools.compose.SelectableListItemState

data class SelectedValidatorsScreenViewState(
    val listState: MultiSelectListViewState<String>,
    val canChangeValidators: Boolean
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
        if (state is LoadingState.Loaded) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (state.data.listState.items.isEmpty()) {
                    EmptyMessage(
                        modifier = Modifier.align(Alignment.Center),
                        message = R.string.staking_set_validators_message
                    )
                } else {
                    ValidatorsList(
                        listState = state.data.listState,
                        paddingValues = PaddingValues(bottom = 106.dp),
                        onSelected = {},
                        onInfoClick = screenInterface::onInfoClick
                    )
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
    val state = SelectedValidatorsScreenViewState(
        MultiSelectListViewState(
            items = emptyList(),
            selectedItems = listOf(items.first())
        ),
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
