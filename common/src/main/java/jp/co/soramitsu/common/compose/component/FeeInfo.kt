package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.black2

data class FeeInfoViewState(
    val caption: String? = null,
    val feeAmount: String?,
    val feeAmountFiat: String?
) {
    companion object{
        val default = FeeInfoViewState(null, null, null)
    }
}

@Composable
fun FeeInfo(state: FeeInfoViewState) {
    val verticalPadding = if (state.feeAmount.isNullOrEmpty()) {
        16.dp
    } else 8.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = verticalPadding)
    ) {
        B2(
            text = state.caption ?: stringResource(id = R.string.network_fee),
            color = black2,
            modifier = Modifier
                .weight(1f)
                .align(CenterVertically)
        )
        Column(modifier = Modifier.weight(1f)) {
            state.feeAmountFiat?.let {
                B2(text = it, color = black2, textAlign = TextAlign.End, modifier = Modifier.align(Alignment.End))
            }
            state.feeAmount?.let {
                CapsTitle(text = it, textAlign = TextAlign.End, modifier = Modifier.align(Alignment.End))
            } ?: Shimmer(
                modifier = Modifier
                    .height(16.dp)
                    .width(100.dp)
                    .align(Alignment.End)
            )
        }
    }
}

@Preview
@Composable
private fun FeeInfo() {
    val state = FeeInfoViewState(
        feeAmount = "0.0051 KSM",
        feeAmountFiat = "$0.0009"
    )
    FearlessTheme {
        Column(Modifier.padding(16.dp)) {
            FeeInfo(state = state)
            FeeInfo(
                state = FeeInfoViewState(
                    feeAmount = null,
                    feeAmountFiat = null
                )
            )
        }
    }
}
