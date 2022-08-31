package jp.co.soramitsu.staking.impl.presentation.pools.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.math.BigInteger
import jp.co.soramitsu.common.compose.component.B2
import jp.co.soramitsu.common.compose.component.CapsTitle
import jp.co.soramitsu.common.compose.component.FearlessRadioButton
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginHorizontal
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentRadioButtonColors
import jp.co.soramitsu.common.compose.theme.black1
import jp.co.soramitsu.common.compose.theme.greenText
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_staking_impl.R

data class PoolItemState(
    val id: Int,
    val name: String,
    val membersCount: Int,
    val stakedAmount: BigDecimal,
    val staked: String,
    val isSelected: Boolean
)

@Composable
fun PoolItem(state: PoolItemState, onSelected: (PoolItemState) -> Unit, onInfoClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clickableWithNoIndication { onSelected(state) }
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val stakedText = buildAnnotatedString {
            withStyle(style = SpanStyle(color = black1)) {
                append("${stringResource(id = R.string.pool_staking_choosepool_staked_title)} ")
            }
            withStyle(style = SpanStyle(color = greenText)) {
                append(state.staked)
            }
        }

        FearlessRadioButton(
            selected = state.isSelected,
            onClick = { onSelected(state) },
            modifier = Modifier.align(CenterVertically),
            colors = accentRadioButtonColors
        )
        MarginHorizontal(margin = 14.dp)
        Column(modifier = Modifier.weight(1f)) {
            CapsTitle(text = state.name)
            B2(text = stringResource(id = R.string.pool_staking_choosepool_members_count_title, state.membersCount), color = black1)
            B2(text = stakedText)
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
            PoolItem(
                PoolItemState(
                    id = 1,
                    name = "Polkadot js plus",
                    membersCount = 15,
                    staked = "20k KSM",
                    stakedAmount = BigDecimal.ZERO,
                    isSelected = true
                ), {}, {}
            )
            PoolItem(
                PoolItemState(
                    id = 2,
                    name = "FIRST POOL",
                    membersCount = 7,
                    staked = "10k KSM",
                    stakedAmount = BigDecimal.ZERO,
                    isSelected = false
                ), {}, {}
            )
        }
    }
}
