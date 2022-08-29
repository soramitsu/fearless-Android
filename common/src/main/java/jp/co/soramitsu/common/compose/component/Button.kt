package jp.co.soramitsu.common.compose.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.ButtonColors
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import jp.co.soramitsu.common.compose.theme.FearlessTheme
import jp.co.soramitsu.common.compose.theme.accentButtonColors

@Composable
fun AccentButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FearlessButton(text, enabled, accentButtonColors, modifier, onClick)
}

@Composable
fun FearlessButton(text: String, enabled: Boolean, colors: ButtonColors, modifier: Modifier = Modifier, onClick: () -> Unit) {
    TextButton(modifier = modifier, onClick = onClick, shape = FearlessCorneredShape(), colors = colors, enabled = enabled) {
        H3(text = text)
    }
}

@Composable
@Preview
private fun ButtonPreview() {
    FearlessTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            AccentButton(
                "Start staking",
                modifier = Modifier
                    .width(200.dp)
                    .height(52.dp)
            ) {}
        }
    }
}
