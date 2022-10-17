package jp.co.soramitsu.staking.impl.presentation.validators.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.component.BottomSheetScreen
import jp.co.soramitsu.common.compose.component.MarginVertical
import jp.co.soramitsu.common.compose.component.RecommendedValidatorsAdditionalInfo
import jp.co.soramitsu.common.compose.component.SelectValidatorsVariantPanel
import jp.co.soramitsu.common.compose.component.SelectValidatorsVariantPanelViewState
import jp.co.soramitsu.common.compose.component.Toolbar
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.feature_staking_impl.R

data class StartSelectValidatorsViewState(
    val recommendedPanelState: SelectValidatorsVariantPanelViewState<List<String>>,
    val manualPanelState: SelectValidatorsVariantPanelViewState<Nothing>
)

@Composable
fun StartSelectValidatorsScreen(
    state: StartSelectValidatorsViewState,
    onRecommendedClick: () -> Unit,
    onManualClick: () -> Unit,
    onBackClick: () -> Unit
) {
    BottomSheetScreen(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Column {
            Toolbar(
                state = ToolbarViewState(
                    stringResource(id = R.string.staking_recommended_title),
                    navigationIcon = R.drawable.ic_arrow_back_24dp
                ),
                onNavigationClick = onBackClick
            )
            MarginVertical(margin = 24.dp)
            Column(Modifier.padding(horizontal = 16.dp)) {
                SelectValidatorsVariantPanel(
                    state.recommendedPanelState,
                    AdditionalInfo = {
                        RecommendedValidatorsAdditionalInfo(it)
                    },
                    onButtonClick = onRecommendedClick
                )
                MarginVertical(margin = 20.dp)
                SelectValidatorsVariantPanel(state.manualPanelState, onButtonClick = onManualClick)
                MarginVertical(margin = 20.dp)
            }
        }
    }
}

@Composable
@Preview
private fun StartSelectValidatorsScreenPreview() {
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
    val state = StartSelectValidatorsViewState(recommendedState, manualState)
    FearlessTheme {
        StartSelectValidatorsScreen(state, {}, {}, {})
    }
}
