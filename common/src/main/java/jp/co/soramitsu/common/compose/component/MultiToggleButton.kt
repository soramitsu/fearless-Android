package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.customColors
import jp.co.soramitsu.common.compose.theme.customTypography

@Composable
fun MultiToggleButton(
    currentSelection: String,
    toggleStates: List<String>,
    onToggleChange: (String) -> Unit
) {
    val selectedTint = MaterialTheme.customColors.white16
    val unselectedTint = Color.Unspecified

    BackgroundCornered(backgroundColor = MaterialTheme.customColors.white04) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .border(BorderStroke(1.dp, Color.LightGray))
                .fillMaxWidth()
        ) {
            toggleStates.forEachIndexed { index, toggleState ->
                val isSelected = currentSelection.lowercase() == toggleState.lowercase()
                val backgroundTint = if (isSelected) selectedTint else unselectedTint

                val style = if (isSelected) {
                    MaterialTheme.customTypography.header5
                } else {
                    MaterialTheme.customTypography.body1
                }

                BackgroundCornered(
                    modifier = Modifier.weight(1f),
                    backgroundColor = backgroundTint,
                ) {
                    Text(
                        text = toggleState,
                        style = style,
                        color = Color.White,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .padding(horizontal = 1.dp)
//                            .fillMaxSize()
                            .toggleable(
                                value = isSelected,
                                enabled = true,
                                onValueChange = { selected ->
                                    println("!!! onValueChange selected: $selected")
                                    if (selected) {
                                        onToggleChange(toggleState)
                                    }
                                }
                            )
                    )
                }
            }
        }
    }
}

data class MultiToggleButtonState(
    val currentSelection: String,
    val toggleStates: List<String>
)

@Preview
@Composable
fun PreviewMultiToggleButton() {
    FearlessTheme {
        Surface(Modifier.background(Color.Black)) {
            MultiToggleButton(
                currentSelection = "Currencies",
                toggleStates = listOf("Currencies", "NFTs"),
                onToggleChange = {})
        }
    }
}
