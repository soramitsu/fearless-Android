package jp.co.soramitsu.staking.impl.presentation.confirm.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import jp.co.soramitsu.common.compose.component.ToolbarViewState
import jp.co.soramitsu.common.compose.theme.FearlessTheme

data class ConfirmJoinPoolScreenViewState(
    val toolbarViewState: ToolbarViewState,
    val amount: String,
    val accountAddress: String,
)

@Composable
fun ConfirmJoinPoolScreen() {
}

@Preview
@Composable
fun ConfirmJoinPoolScreenPreview() {
    FearlessTheme {
        ConfirmJoinPoolScreen()
    }
}
