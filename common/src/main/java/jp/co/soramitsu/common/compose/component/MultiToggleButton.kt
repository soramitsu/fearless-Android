package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography
import jp.co.soramitsu.common.utils.toggleableWithNoIndication

@Composable
fun <T : MultiToggleItem> MultiToggleButton(
    state: MultiToggleButtonState<T>,
    onToggleChange: (T) -> Unit
) {
    val selectedTint = MaterialTheme.customColors.white16
    val unselectedTint = Color.Unspecified

    BackgroundCornered(backgroundColor = MaterialTheme.customColors.white04) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .fillMaxWidth()
        ) {
            state.toggleStates.forEach { toggleState ->
                val isSelected = state.currentSelection == toggleState
                val backgroundTint = if (isSelected) selectedTint else unselectedTint

                val style = if (isSelected) {
                    MaterialTheme.customTypography.header5
                } else {
                    MaterialTheme.customTypography.body1
                }

                BackgroundCornered(
                    modifier = Modifier
                        .testTag("MultiToggleButton_${toggleState.title}")
                        .weight(1f)
                        .toggleableWithNoIndication(
                            value = isSelected,
                            role = Role.Tab,
                            onValueChange = { selected ->
                                if (selected) {
                                    onToggleChange(toggleState)
                                }
                            }
                        ),
                    backgroundColor = backgroundTint
                ) {
                    Text(
                        text = toggleState.title,
                        style = style,
                        color = Color.White,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 1.dp)
                    )
                }
            }
        }
    }
}

data class MultiToggleButtonState<T : MultiToggleItem>(
    val currentSelection: T,
    val toggleStates: List<T>
)

interface MultiToggleItem {
    val title: String
}

@Preview
@Composable
private fun PreviewMultiToggleButton() {
    val currencies = object : MultiToggleItem {
        override val title = "Currencies"
    }
    val nfts = object : MultiToggleItem {
        override val title = "NFTs"
    }
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            MultiToggleButton(
                MultiToggleButtonState(currencies, listOf(currencies, nfts)),
                onToggleChange = {}
            )
        }
    }
}
