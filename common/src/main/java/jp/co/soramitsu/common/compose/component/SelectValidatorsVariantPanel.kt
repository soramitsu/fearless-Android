package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.selectedGreen

data class SelectValidatorsVariantPanelViewState<T>(
    val title: String,
    val description: String,
    val buttonText: String,
    val additionalInfo: T? = null
)

@Composable
fun <T> SelectValidatorsVariantPanel(
    state: SelectValidatorsVariantPanelViewState<T>,
    AdditionalInfo: @Composable (T) -> Unit = {},
    onButtonClick: () -> Unit
) {
    BackgroundCorneredWithBorder(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            H4(text = state.title)
            MarginVertical(margin = 12.dp)
            B2(text = state.description)
            MarginVertical(margin = 12.dp)
            state.additionalInfo?.let {
                AdditionalInfo(it)
            }
            MarginVertical(margin = 24.dp)
            AccentButton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                text = state.buttonText,
                onClick = onButtonClick
            )
        }
    }
}

@Composable
@Preview
private fun SelectValidatorsVariantPanelPreview() {
    val manualState = SelectValidatorsVariantPanelViewState<Nothing>(
        title = "Stake with your validators",
        description = "You should trust your nominations to act competently and honest, basing your decision purely on their current profitability could lead to reduced profits or even loss of funds.",
        buttonText = "Select manual"
    )
    val recommendedState = SelectValidatorsVariantPanelViewState(
        title = "Stake with suggested  validators",
        description = "Fearless algorithm has select a list of recommended validators based on the criteria:",
        buttonText = "Select suggested",
        additionalInfo = listOf("Most profitable", "Not oversubscribed", "Having onchain identity", "Not slashed", "Limit of 2 validators per identity")
    )
    FearlessTheme {
        Column {
            SelectValidatorsVariantPanel(recommendedState, AdditionalInfo = {
                RecommendedValidatorsAdditionalInfo(it)
            }, {})
            MarginVertical(margin = 16.dp)
            SelectValidatorsVariantPanel(manualState, onButtonClick = {})
        }
    }
}

@Composable
fun RecommendedValidatorsAdditionalInfo(state: List<String>) {
    Column {
        state.forEach { item ->
            Row {
                Image(res = R.drawable.ic_selected, tint = selectedGreen, modifier = Modifier
                    .size(12.dp)
                    .align(Alignment.CenterVertically))
                MarginHorizontal(margin = 8.dp)
                B2(text = item)
            }
        }
    }
}
