package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.FearlessRadioButton
import jp.co.soramitsu.common.compose.component.H6
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentRadioButtonColors
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.compose.theme.warningOrange
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_staking_impl.R

data class SelectableListItemState<T>(
    val id: T,
    val title: String,
    val subtitle: String,
    val caption: AnnotatedString,
    val isSelected: Boolean,
    val additionalStatuses: List<SelectableListItemAdditionalStatus> = listOf()
) {
    enum class SelectableListItemAdditionalStatus(@DrawableRes val iconRes: Int, val iconTintColor: Color) {
        WARNING(R.drawable.ic_screen_warning, warningOrange)
    }
}

@Composable
fun <T> SelectableListItem(state: SelectableListItemState<T>, onSelected: (SelectableListItemState<T>) -> Unit, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickableWithNoIndication { onSelected(state) }
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        FearlessRadioButton(
            selected = state.isSelected,
            onClick = { onSelected(state) },
            modifier = Modifier.align(CenterVertically),
            colors = accentRadioButtonColors
        )
        MarginHorizontal(margin = 14.dp)
        Column(modifier = Modifier.weight(1f)) {
            H6(text = state.title)
            B2(text = state.subtitle, color = black1)
            B2(text = state.caption)
        }
        state.additionalStatuses.takeIf { it.isNotEmpty() }?.let { items ->
            items.forEach {
                Image(
                    modifier = Modifier
                        .size(12.dp)
                        .align(CenterVertically),
                    res = it.iconRes,
                    tint = it.iconTintColor
                )
            }
        }
        Box(
            modifier = Modifier
                .clickableWithNoIndication(onClick = onInfoClick)
                .align(CenterVertically)
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Image(res = R.drawable.ic_info_14, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
@Preview
fun PoolItemPreview() {
    FearlessTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            val stakedText = buildAnnotatedString {
                withStyle(style = SpanStyle(color = black1)) {
                    append("${stringResource(R.string.pool_staking_choosepool_staked_title)} ")
                }
                withStyle(style = SpanStyle(color = greenText)) {
                    append("20k KSM")
                }
            }
            val subtitle = stringResource(R.string.pool_staking_choosepool_members_count_title, 15)
            SelectableListItem(
                SelectableListItemState(
                    id = 1,
                    title = "Polkadot js plus",
                    subtitle = subtitle,
                    caption = stakedText,
                    isSelected = true,
                    additionalStatuses = listOf(SelectableListItemState.SelectableListItemAdditionalStatus.WARNING)
                ),
                {},
                {}
            )
            SelectableListItem(
                SelectableListItemState(
                    id = 2,
                    title = "FIRST POOL",
                    subtitle = subtitle,
                    caption = stakedText,
                    isSelected = false
                ),
                {},
                {}
            )
        }
    }
}
