package jp.co.soramitsu.staking.impl.presentation.staking.main.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.AmountInput
import jp.co.soramitsu.common.compose.component.AmountInputViewState
import jp.co.soramitsu.common.compose.component.B1
import jp.co.soramitsu.common.compose.component.BackgroundCornered
import jp.co.soramitsu.common.compose.component.ChangeToValue
import jp.co.soramitsu.common.compose.component.Image
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.TitleValueViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2
import jp.co.soramitsu.common.compose.theme.blurColorLight
import jp.co.soramitsu.common.utils.clickableWithNoIndication
import jp.co.soramitsu.feature_staking_impl.R
import java.math.BigDecimal

data class EstimatedEarningsViewState(
    val monthlyChange: TitleValueViewState?,
    val yearlyChange: TitleValueViewState?,
    val amountInputViewState: AmountInputViewState
)

@Composable
fun EstimatedEarnings(
    state: EstimatedEarningsViewState,
    onInfoClick: () -> Unit,
    onAmountInput: (BigDecimal?) -> Unit
) {
    BackgroundCornered(
        backgroundColor = blurColorLight,
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier
                    .testTag("EstimatedEarningsTitle")
                    .padding(end = 16.dp)
                    .clickableWithNoIndication(onInfoClick)
            ) {
                B1(
                    text = stringResource(id = R.string.staking_estimate_earning_title_v1_9_0),
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                )
                Image(res = R.drawable.ic_info_16, modifier = Modifier.align(CenterVertically))
            }
            MarginVertical(margin = 8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            ) {
                ChangeToValue(state.monthlyChange, modifier = Modifier.weight(1f), testTag = "MonthlyChange")
                ChangeToValue(state.yearlyChange, modifier = Modifier.weight(1f), testTag = "YearlyChange")
            }
            MarginVertical(margin = 24.dp)
            AmountInput(
                state = state.amountInputViewState,
                modifier = Modifier.padding(horizontal = 16.dp),
                backgroundColor = blurColorLight,
                borderColor = black2,
                onInput = onAmountInput
            )
            MarginVertical(margin = 24.dp)
        }
    }
}

@Preview
@Composable
private fun EstimatedEarningsPreview() {
    val state = EstimatedEarningsViewState(
        TitleValueViewState("1.43% monthly", "0.164 KSM", "$24.92"),
        null,
        AmountInputViewState("KSM", "", "44.32334", "$12000", BigDecimal.TEN, null, initial = null)
    )
    FearlessTheme {
        EstimatedEarnings(state, {}, {})
    }
}
